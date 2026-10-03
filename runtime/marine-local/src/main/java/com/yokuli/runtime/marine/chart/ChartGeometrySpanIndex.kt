package com.yokuli.runtime.marine.chart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import com.yokuli.runtime.contract.chart.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.math.*

/**
 * v8 的唯一坐标存储。每块至多 256 条原始边，邻块仅共享一个端点；没有量化和边界简化。
 * feature RTree 先筛对象，持久纬度索引再筛实际穿过射线的边块，冷点查不解压全国大环。
 * 目录和坐标都属于不可变事实版本，随原生资料包迁移；不是页面的内存缓存。
 */
internal object ChartGeometrySpanIndex {
    private const val EDGES=256
    private const val MAX_VERTICES=500_000
    private const val RAW_LIMIT=(EDGES+1)*25
    class ReadLimit:IllegalStateException("CHART_POSITION_READ_LIMIT")
    /** 单次准星冷读取上限；耗尽明确 incomplete，不回退整环或制造海水结论。 */
    class Budget(private var blocks:Int=256,private var bytes:Int=2*1024*1024) {
        fun consume(size:Int){if(--blocks<0||size>bytes)throw ReadLimit();bytes-=size}
    }
    private data class Part(val number:Int,val hole:Boolean,val count:Int,val west:Double,val east:Double,val south:Double,val north:Double)
    private data class Span(val start:Int,val count:Int,val anchor:Double,val rawSize:Int,val crc:Long,val bytes:ByteArray,val decoded:List<ChartPoint>?=null)

    fun create(db:SQLiteDatabase)=ChartGeometryWriter.create(AndroidChartSql(db))
    fun insert(db:SQLiteDatabase,row:Long,geometry:ChartGeometry,check:()->Unit):ByteArray =
        ChartGeometryWriter.insert(AndroidChartSql(db),row,geometry,check)
    fun appendIdentity(db:SQLiteDatabase,row:Long,metadata:ByteArray,geometryHash:ByteArray)=
        ChartGeometryWriter.appendIdentity(AndroidChartSql(db),row,metadata,geometryHash)

    /** 目标已 ATTACH 原生库，保留压缩块直接拷贝；只对小属性重算组合身份。 */
    fun copyFrom(target:SQLiteDatabase,alias:String,offset:Long,check:()->Unit) {
        require(alias.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))){"CHART_NATIVE_ALIAS_INVALID"}
        check()
        val last=target.rawQuery("SELECT COALESCE(MAX(rowid),0) FROM $alias.features",null).use{it.moveToFirst();it.getLong(0)}
        var first=1L
        while(first<=last) {
            check();val end=minOf(first+31,last);val args=arrayOf<Any>(offset,first,end)
            target.execSQL("INSERT INTO geometry_part SELECT feature_row+?,part_no,hole,point_count,closing_added,min_x,max_x,min_y,max_y FROM $alias.geometry_part WHERE feature_row BETWEEN ? AND ?",args)
            target.execSQL("INSERT INTO geometry_span SELECT feature_row+?,part_no,span_no,point_start,point_count,anchor_x,min_x,max_x,min_y,max_y,raw_size,crc,payload FROM $alias.geometry_span WHERE feature_row BETWEEN ? AND ?",args)
            target.execSQL("INSERT INTO geometry_fact SELECT feature_row+?,geometry_hash,part_count,point_count FROM $alias.geometry_fact WHERE feature_row BETWEEN ? AND ?",args)
            first=end+1
        }
        target.rawQuery("SELECT f.rowid,length(f.payload),g.geometry_hash FROM $alias.features f JOIN $alias.geometry_fact g ON g.feature_row=f.rowid ORDER BY f.rowid",null).use {rows->
            while(rows.moveToNext()) {
                check();val row=rows.getLong(0);val length=rows.getInt(1)
                require(length in 1..ChartFeaturePayload.MAX_STORED_BYTES){"CHART_FEATURE_PAYLOAD_INVALID"}
                val previous=contentHash(target);val digest=MessageDigest.getInstance("SHA-256")
                digest.update(previous.toByteArray());digest.update((row+offset).toString().toByteArray())
                var position=0
                while(position<length) {
                    check();val count=min(128*1024,length-position)
                    target.rawQuery("SELECT substr(payload,?,?) FROM $alias.features WHERE rowid=?",arrayOf((position+1).toString(),count.toString(),row.toString())).use {part->
                        require(part.moveToFirst());val bytes=part.getBlob(0);require(bytes.size==count){"CHART_FEATURE_PAYLOAD_TRUNCATED"};digest.update(bytes)
                    };position+=count
                }
                digest.update(unhex(rows.getString(2)))
                target.execSQL("UPDATE native_content SET content_hash=?",arrayOf(hex(digest.digest())))
            }
        }
    }

    fun contentHash(db:SQLiteDatabase):String=db.rawQuery("SELECT content_hash FROM native_content",null).use {
        require(it.moveToFirst()){ "CHART_NATIVE_IDENTITY_MISSING" };it.getString(0).also {value->require(value.matches(Regex("[0-9a-f]{64}"))){"CHART_NATIVE_IDENTITY_INVALID"}}
    }

    fun validate(db:SQLiteDatabase) {
        require(db.version==8){"CHART_NATIVE_VERSION"};contentHash(db)
        val requiredIndexes=setOf("geometry_part_latitude","geometry_span_latitude","spatial_feature_row","spatial_bucket_key")
        val indexes=db.rawQuery("SELECT name FROM sqlite_master WHERE type='index'",null).use {rows->buildSet{while(rows.moveToNext())add(rows.getString(0))}}
        require(indexes.containsAll(requiredIndexes)){"CHART_NATIVE_SPATIAL_INDEX_MISSING"}
        db.rawQuery("SELECT count(*),min(length(identity)),max(length(identity)) FROM native_content",null).use {require(it.moveToFirst()&&it.getInt(0)==1&&it.getInt(1) in 1..128&&it.getInt(2)<=128){"CHART_NATIVE_IDENTITY_INVALID"}}
        for(table in listOf("geometry_part","geometry_span","geometry_fact")) db.rawQuery("SELECT 1 FROM $table LIMIT 1",null).use {it.moveToFirst()}
        db.rawQuery("SELECT 1 FROM features f LEFT JOIN geometry_fact g ON g.feature_row=f.rowid WHERE g.feature_row IS NULL LIMIT 1",null).use {require(!it.moveToFirst()){"CHART_NATIVE_GEOMETRY_MISSING"}}
        db.rawQuery("SELECT 1 FROM geometry_fact g LEFT JOIN geometry_part p ON p.feature_row=g.feature_row GROUP BY g.feature_row HAVING count(p.part_no)<>g.part_count OR COALESCE(sum(p.point_count),0)<>g.point_count OR g.point_count<0 OR g.point_count>500000 OR g.part_count<0 OR g.part_count>500000 OR (g.part_count>0 AND (min(p.part_no)<>0 OR max(p.part_no)<>g.part_count-1)) LIMIT 1",null).use {require(!it.moveToFirst()){"CHART_GEOMETRY_SPAN_TRUNCATED"}}
        db.rawQuery("SELECT 1 FROM geometry_part p LEFT JOIN features f ON f.rowid=p.feature_row WHERE f.rowid IS NULL OR p.point_count<0 OR p.point_count>500000 OR p.closing_added NOT IN (0,1) OR p.min_x>p.max_x OR p.min_y>p.max_y OR p.min_y< -90 OR p.max_y>90 LIMIT 1",null).use {require(!it.moveToFirst()){"CHART_NATIVE_GEOMETRY_INVALID"}}
        db.rawQuery("SELECT 1 FROM geometry_span s LEFT JOIN geometry_part p ON p.feature_row=s.feature_row AND p.part_no=s.part_no WHERE p.feature_row IS NULL OR s.point_count<1 OR s.point_count>257 OR s.raw_size<17 OR s.raw_size>6425 OR length(s.payload)>65536 OR s.point_start<0 OR s.point_start>=p.point_count OR s.point_start+s.point_count>p.point_count+1 OR s.min_x>s.max_x OR s.min_y>s.max_y OR s.min_y< -90 OR s.max_y>90 LIMIT 1",null).use {require(!it.moveToFirst()){ "CHART_NATIVE_GEOMETRY_INVALID" }}
        // 正数部件必须恰好覆盖原始顶点与（必要时）闭合边；任何缺块/重叠都拒绝安装，不能当面外。
        db.rawQuery("SELECT 1 FROM geometry_part p LEFT JOIN geometry_span s ON s.feature_row=p.feature_row AND s.part_no=p.part_no GROUP BY p.feature_row,p.part_no HAVING (p.point_count=0 AND count(s.span_no)<>0) OR (p.point_count>0 AND (count(s.span_no)=0 OR min(s.span_no)<>0 OR max(s.span_no)<>count(s.span_no)-1 OR max(s.point_start+s.point_count)<>p.point_count+p.closing_added OR sum(s.point_count)-count(s.span_no)+1<>p.point_count+p.closing_added)) LIMIT 1",null).use {require(!it.moveToFirst()){"CHART_GEOMETRY_SPAN_TRUNCATED"}}
        db.rawQuery("SELECT 1 FROM geometry_span s JOIN geometry_part p ON p.feature_row=s.feature_row AND p.part_no=s.part_no WHERE s.point_start<>s.span_no*256 OR (s.point_start+s.point_count<p.point_count+p.closing_added AND s.point_count<>257) OR s.point_start+s.point_count>p.point_count+p.closing_added LIMIT 1",null).use {require(!it.moveToFirst()){"CHART_GEOMETRY_SPAN_INVALID"}}
        db.rawQuery("SELECT 1 FROM geometry_part p WHERE p.point_count>0 AND NOT EXISTS(SELECT 1 FROM spatial_feature sf JOIN spatial s ON s.id=sf.id WHERE sf.feature_row=p.feature_row) LIMIT 1",null).use {require(!it.moveToFirst()){"CHART_NATIVE_SPATIAL_MISSING"}}
        db.rawQuery("SELECT 1 FROM spatial_feature sf LEFT JOIN features f ON f.rowid=sf.feature_row LEFT JOIN spatial s ON s.id=sf.id WHERE f.rowid IS NULL OR s.id IS NULL LIMIT 1",null).use {require(!it.moveToFirst()){"CHART_NATIVE_SPATIAL_INVALID"}}
    }

    fun readGeometry(db:SQLiteDatabase,row:Long,kind:ChartGeometryKind,signal:CancellationSignal,check:()->Unit,bounds:ChartBounds?=null):ChartGeometry {
        val result=ArrayList<ChartGeometryPart>();var vertexCount=0
        for(part in parts(db,row,signal)) {
            check();require(part.count in 0..MAX_VERTICES-vertexCount){"CHART_FEATURE_GEOMETRY_LIMIT"};vertexCount+=part.count
            if(bounds!=null&&(part.count==0||!intersects(part,bounds)))continue
            val points=ArrayList<ChartPoint>(part.count)
            spans(db,row,part.number,"",emptyList(),signal) {span->
                check();val decoded=decode(span,check)
                val first=if(span.start==0)0 else 1
                for(i in first until decoded.size)if(span.start+i<part.count)points+=decoded[i]
            }
            require(points.size==part.count){"CHART_GEOMETRY_SPAN_TRUNCATED"}
            result+=ChartGeometryPart(points,part.hole)
        }
        return ChartGeometry(kind,result)
    }

    /** 解码缓存只属于一个不可变只读连接；不跨版本复用，不改变每次几何判断的预算。 */
    class ReadCache {
        private data class Key(val row:Long,val part:Int,val span:Int,val crc:Long)
        private val points=LinkedHashMap<Key,List<ChartPoint>>(256,.75f,true)
        private var bytes=0L
        fun read(row:Long,part:Int,span:Int,crc:Long):List<ChartPoint>?=points[Key(row,part,span,crc)]
        fun save(row:Long,part:Int,span:Int,crc:Long,value:List<ChartPoint>) {
            val key=Key(row,part,span,crc)
            points.remove(key)?.let{bytes-=it.size*64L+96}
            points[key]=value;bytes+=value.size*64L+96
            while(points.size>2048||bytes>4*1024*1024) {
                val first=points.entries.iterator().next();bytes-=first.value.size*64L+96;points.remove(first.key)
            }
        }
        fun clear(){points.clear();bytes=0L}
    }

    fun contains(db:SQLiteDatabase,row:Long,point:ChartPoint,signal:CancellationSignal,check:()->Unit,budget:Budget,cache:ReadCache?=null):Boolean {
        var winding=0
        for(part in parts(db,row,signal,point.latitude)) {
            check();if(part.count<3)continue
            val x=point.longitude+360.0*round(((part.west+part.east)*.5-point.longitude)/360.0)
            if(x<part.west-1e-10||x>part.east+1e-10)continue
            var inside=false;var boundary=false
            spans(db,row,part.number,"AND min_y<=? AND max_y>=? AND max_x>=?",listOf(point.latitude+1e-10,point.latitude-1e-10,x-1e-10),signal,cache) {span->
                budget.consume(span.rawSize);val points=decode(span,check);var ax=span.anchor
                for(i in 1 until points.size) {
                    val a=points[i-1];val b=points[i];val bx=ax+normalize(b.longitude-a.longitude)
                    val cross=(x-ax)*(b.latitude-a.latitude)-(point.latitude-a.latitude)*(bx-ax)
                    if(abs(cross)<=1e-10*(abs(bx-ax)+abs(b.latitude-a.latitude)).coerceAtLeast(1e-10)&&x>=min(ax,bx)-1e-10&&x<=max(ax,bx)+1e-10&&point.latitude>=min(a.latitude,b.latitude)-1e-10&&point.latitude<=max(a.latitude,b.latitude)+1e-10)boundary=true
                    if((a.latitude>point.latitude)!=(b.latitude>point.latitude)&&x<(bx-ax)*(point.latitude-a.latitude)/(b.latitude-a.latitude)+ax)inside=!inside
                    ax=bx
                }
            }
            if(boundary||inside)winding+=if(part.hole)-1 else 1
        }
        return winding>0
    }

    fun hit(db:SQLiteDatabase,row:Long,feature:NauticalFeature,point:ChartPoint,radius:Double,signal:CancellationSignal,check:()->Unit,budget:Budget,cache:ReadCache?=null):ChartPositionHit? {
        val kind=feature.geometry.kind
        if(feature.kind==NauticalFeatureKind.COVERAGE)return null
        if(kind==ChartGeometryKind.POLYGON)return if(contains(db,row,point,signal,check,budget,cache))
            ChartPositionHit(feature.copy(geometry=ChartGeometry(ChartGeometryKind.NONE,emptyList())),0.0,point)else null
        if(kind !in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT,ChartGeometryKind.LINE))return null
        val scale=111_320.0*cos(Math.toRadians(point.latitude)).coerceAtLeast(.001)
        val dx=radius/scale;val dy=radius/111_320.0
        var nearest:ChartPoint?=null;var distance=Double.POSITIVE_INFINITY
        for(part in parts(db,row,signal,point.latitude,dy)) {
            check();if(part.north<point.latitude-dy||part.south>point.latitude+dy)continue
            val x=point.longitude+360.0*round(((part.west+part.east)*.5-point.longitude)/360.0)
            val xs=if(kind==ChartGeometryKind.LINE)listOf(x)else listOf(point.longitude-360.0,point.longitude,point.longitude+360.0)
            val branches=xs.filter{part.east>=it-dx&&part.west<=it+dx}
            if(branches.isEmpty())continue
            val longitudePredicate=branches.joinToString(" OR "){"(min_x<=? AND max_x>=?)"}
            val arguments=listOf(point.latitude+dy,point.latitude-dy)+branches.flatMap{listOf(it+dx,it-dx)}
            spans(db,row,part.number,"AND min_y<=? AND max_y>=? AND ($longitudePredicate)",arguments,signal,cache) {span->
                budget.consume(span.rawSize);val points=decode(span,check)
                if(kind!=ChartGeometryKind.LINE)for(p in points) {
                    val d=hypot(normalize(p.longitude-point.longitude)*scale,(p.latitude-point.latitude)*111_320.0)
                    if(d<distance){distance=d;nearest=p}
                }else for(i in 1 until points.size) {
                    val a=points[i-1];val b=points[i]
                    val start=normalize(a.longitude-point.longitude)*scale;val end=start+normalize(b.longitude-a.longitude)*scale
                    val shift=round((start+end)/(720.0*scale))*360.0*scale
                    val ax=start-shift;val ay=(a.latitude-point.latitude)*111_320.0;val bx=end-shift;val by=(b.latitude-point.latitude)*111_320.0
                    val vx=bx-ax;val vy=by-ay
                    val t=(-(ax*vx+ay*vy)/(vx*vx+vy*vy).coerceAtLeast(.000001)).coerceIn(0.0,1.0)
                    val px=ax+t*vx;val py=ay+t*vy;val d=hypot(px,py)
                    if(d<distance){distance=d;nearest=ChartPoint(point.latitude+py/111_320.0,normalize(point.longitude+px/scale))}
                }
            }
        }
        val p=nearest?:return null;if(distance>radius)return null
        val compact=if(kind==ChartGeometryKind.LINE)feature.copy(geometry=ChartGeometry(ChartGeometryKind.NONE,emptyList()))
            else feature.copy(geometry=ChartGeometry(ChartGeometryKind.POINT,listOf(ChartGeometryPart(listOf(p)))),
                depth=if(feature.kind==NauticalFeatureKind.SOUNDING)feature.depth?.copy(pointMeters=p.depthMeters?:feature.depth?.pointMeters?.takeIf{kind==ChartGeometryKind.POINT})else feature.depth)
        return ChartPositionHit(compact,distance,p)
    }

    private fun parts(db:SQLiteDatabase,row:Long,signal:CancellationSignal,latitude:Double?=null,tolerance:Double=1e-10):List<Part> {
        val predicate=if(latitude==null)""else " AND min_y<=? AND max_y>=?"
        val args=if(latitude==null)arrayOf(row.toString())else arrayOf(row.toString(),(latitude+tolerance).toString(),(latitude-tolerance).toString())
        val index=if(latitude==null)""else "INDEXED BY geometry_part_latitude"
        return db.rawQuery("SELECT part_no,hole,point_count,min_x,max_x,min_y,max_y FROM geometry_part $index WHERE feature_row=?$predicate ORDER BY part_no",args,signal).use {cursor->
            buildList {while(cursor.moveToNext())add(Part(cursor.getInt(0),cursor.getInt(1)!=0,cursor.getInt(2),cursor.getDouble(3),cursor.getDouble(4),cursor.getDouble(5),cursor.getDouble(6)))}
        }
    }
    private inline fun spans(db:SQLiteDatabase,row:Long,part:Int,predicate:String,args:List<Double>,signal:CancellationSignal,cache:ReadCache?=null,read:(Span)->Unit) {
        val index=if(predicate.isBlank())""else "INDEXED BY geometry_span_latitude"
        // 邻接点通常命中同一边块：暖读取只取几十字节的块目录，不再搬 BLOB、解压和分配顶点。
        val payloadColumn=if(cache==null)",payload"else ""
        db.rawQuery("SELECT point_start,point_count,anchor_x,raw_size,crc,span_no$payloadColumn FROM geometry_span $index WHERE feature_row=? AND part_no=? $predicate ORDER BY span_no",(listOf(row.toString(),part.toString())+args.map(Double::toString)).toTypedArray(),signal).use {cursor->
            while(cursor.moveToNext()) {
                signal.throwIfCanceled()
                val number=cursor.getInt(5);val crc=cursor.getLong(4)
                val cached=cache?.read(row,part,number,crc)
                val bytes=when {
                    cached!=null->ByteArray(0)
                    cache==null->cursor.getBlob(6)
                    else->db.rawQuery("SELECT payload FROM geometry_span WHERE feature_row=? AND part_no=? AND span_no=?",arrayOf(row.toString(),part.toString(),number.toString()),signal).use {blob->
                        require(blob.moveToFirst()){"CHART_GEOMETRY_SPAN_TRUNCATED"};blob.getBlob(0)
                    }
                }
                val span=Span(cursor.getInt(0),cursor.getInt(1),cursor.getDouble(2),cursor.getInt(3),crc,bytes,cached)
                if(cache!=null&&cached==null) {
                    val decoded=decode(span){signal.throwIfCanceled()}
                    cache.save(row,part,number,crc,decoded)
                    read(span.copy(bytes=ByteArray(0),decoded=decoded))
                }else read(span)
            }
        }
    }
    private fun decode(span:Span,check:()->Unit):List<ChartPoint> {
        check();return span.decoded?:ChartGeometryBinary.decode(span.count,span.rawSize,span.crc,span.bytes,check)
    }
    private fun compress(raw:ByteArray):ByteArray {
        val output=ByteArrayOutputStream(raw.size);val deflater=Deflater(Deflater.BEST_SPEED,true)
        try {DeflaterOutputStream(output,deflater).use{it.write(raw)}}finally{deflater.end()};return output.toByteArray()
    }
    private fun intersects(part:Part,bounds:ChartBounds):Boolean {
        if(part.north<bounds.south||part.south>bounds.north)return false
        return bounds.split().any {box->val shift=360.0*round(((part.west+part.east)-(box.west+box.east))/720.0);part.east>=box.west+shift&&part.west<=box.east+shift}
    }
    private fun normalize(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    private fun crc(bytes:ByteArray)=CRC32().apply{update(bytes)}.value
    private fun unhex(value:String):ByteArray {require(value.matches(Regex("[0-9a-f]{64}"))){"CHART_NATIVE_IDENTITY_INVALID"};return ByteArray(32){value.substring(it*2,it*2+2).toInt(16).toByte()}}
    private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
}

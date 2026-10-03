package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*
import kotlin.math.*

/** v8 全精度 span 唯一写入实现：手机 SQLite 与桌面 JDBC 产生同一格式。 */
internal object ChartGeometryWriter {
    private const val EDGES=256
    private const val MAX_VERTICES=500_000
    fun create(db:ChartSql) {
        db.execSQL("CREATE TABLE geometry_part(feature_row INTEGER NOT NULL,part_no INTEGER NOT NULL,hole INTEGER NOT NULL,point_count INTEGER NOT NULL,closing_added INTEGER NOT NULL,min_x REAL NOT NULL,max_x REAL NOT NULL,min_y REAL NOT NULL,max_y REAL NOT NULL,PRIMARY KEY(feature_row,part_no)) WITHOUT ROWID")
        db.execSQL("CREATE INDEX geometry_part_latitude ON geometry_part(feature_row,min_y,max_y)")
        db.execSQL("CREATE TABLE geometry_span(feature_row INTEGER NOT NULL,part_no INTEGER NOT NULL,span_no INTEGER NOT NULL,point_start INTEGER NOT NULL,point_count INTEGER NOT NULL,anchor_x REAL NOT NULL,min_x REAL NOT NULL,max_x REAL NOT NULL,min_y REAL NOT NULL,max_y REAL NOT NULL,raw_size INTEGER NOT NULL,crc INTEGER NOT NULL,payload BLOB NOT NULL,PRIMARY KEY(feature_row,part_no,span_no)) WITHOUT ROWID")
        db.execSQL("CREATE INDEX geometry_span_latitude ON geometry_span(feature_row,part_no,min_y,max_y)")
        db.execSQL("CREATE TABLE geometry_fact(feature_row INTEGER PRIMARY KEY,geometry_hash TEXT NOT NULL,part_count INTEGER NOT NULL,point_count INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE native_content(identity TEXT NOT NULL,content_hash TEXT NOT NULL)")
        db.execSQL("INSERT INTO native_content VALUES (?,?)",arrayOf(UUID.randomUUID().toString(),hex(MessageDigest.getInstance("SHA-256").digest("yokuli-native-maritime-v8".toByteArray()))))
    }

    /** 返回规范坐标摘要，由 FeatureIndex 与 metadata 一起加入内容链。 */
    fun insert(db:ChartSql,row:Long,geometry:ChartGeometry,check:()->Unit):ByteArray {
        require(geometry.parts.size<=500_000){"CHART_FEATURE_GEOMETRY_LIMIT"}
        val digest=MessageDigest.getInstance("SHA-256")
        var total=0
        geometry.parts.forEachIndexed {partNo,part->
            check();total+=part.points.size;require(total<=MAX_VERTICES){"CHART_FEATURE_GEOMETRY_LIMIT"}
            val original=part.points
            if(original.isEmpty()) {
                db.execSQL("INSERT INTO geometry_part VALUES (?,?,?,?,?,?,?,?,?)",arrayOf<Any>(row,partNo,if(part.hole)1 else 0,0,0,0.0,0.0,0.0,0.0))
                digest.update(partNo.toString().toByteArray());digest.update(if(part.hole)1.toByte()else 0.toByte());digest.update("0".toByteArray())
                return@forEachIndexed
            }
            val close=geometry.kind==ChartGeometryKind.POLYGON&&original.size>=3&&
                (original.first().latitude!=original.last().latitude||original.first().longitude!=original.last().longitude)
            val count=original.size+if(close)1 else 0
            fun point(index:Int)=if(index<original.size)original[index]else original.first()
            val xs=DoubleArray(count)
            var south=90.0;var north=-90.0;var west=Double.POSITIVE_INFINITY;var east=Double.NEGATIVE_INFINITY
            for(i in 0 until count) {
                if(i%256==0)check();val p=point(i)
                require(p.latitude in -90.0..90.0&&p.longitude in -180.0..180.0&&p.depthMeters?.isFinite()!=false){"CHART_FEATURE_COORDINATE_INVALID"}
                xs[i]=if(i==0||geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))p.longitude else xs[i-1]+normalize(p.longitude-point(i-1).longitude)
                south=min(south,p.latitude);north=max(north,p.latitude);west=min(west,xs[i]);east=max(east,xs[i])
            }
            db.execSQL("INSERT INTO geometry_part VALUES (?,?,?,?,?,?,?,?,?)",arrayOf<Any>(row,partNo,if(part.hole)1 else 0,original.size,if(close)1 else 0,west,east,south,north))
            digest.update(partNo.toString().toByteArray());digest.update(if(part.hole)1.toByte()else 0.toByte())
            digest.update(original.size.toString().toByteArray())
            var start=0;var spanNo=0
            while(start<count) {
                check();val end=min(start+EDGES+1,count)
                val stream=ByteArrayOutputStream((end-start)*17)
                var minX=Double.POSITIVE_INFINITY;var maxX=Double.NEGATIVE_INFINITY;var minY=90.0;var maxY=-90.0
                DataOutputStream(stream).use {out->
                    for(i in start until end) {
                        val p=point(i);out.writeDouble(p.latitude);out.writeDouble(p.longitude);out.writeBoolean(p.depthMeters!=null);p.depthMeters?.let(out::writeDouble)
                        minX=min(minX,xs[i]);maxX=max(maxX,xs[i]);minY=min(minY,p.latitude);maxY=max(maxY,p.latitude)
                    }
                }
                val raw=stream.toByteArray();val compressed=compress(raw)
                db.insert("geometry_span",linkedMapOf<String,Any?>().apply {
                    put("feature_row",row);put("part_no",partNo);put("span_no",spanNo);put("point_start",start);put("point_count",end-start)
                    put("anchor_x",xs[start]);put("min_x",minX);put("max_x",maxX);put("min_y",minY);put("max_y",maxY)
                    put("raw_size",raw.size);put("crc",crc(raw));put("payload",compressed)
                })
                digest.update(raw)
                if(end==count)break
                start=end-1;spanNo++
            }
        }
        return digest.digest().also {db.execSQL("INSERT INTO geometry_fact VALUES (?,?,?,?)",arrayOf<Any>(row,hex(it),geometry.parts.size,total))}
    }

    fun appendIdentity(db:ChartSql,row:Long,metadata:ByteArray,geometryHash:ByteArray) {
        val previous=db.rawQuery("SELECT content_hash FROM native_content",null).use {require(it.moveToFirst());it.getString(0)}
        val digest=MessageDigest.getInstance("SHA-256")
        digest.update(previous.toByteArray());digest.update(row.toString().toByteArray());digest.update(metadata);digest.update(geometryHash)
        db.execSQL("UPDATE native_content SET content_hash=?",arrayOf(hex(digest.digest())))
    }

    private fun compress(raw:ByteArray):ByteArray {
        val output=ByteArrayOutputStream(raw.size);val deflater=Deflater(Deflater.BEST_SPEED,true)
        try {DeflaterOutputStream(output,deflater).use{it.write(raw)}}finally{deflater.end()};return output.toByteArray()
    }
    private fun crc(bytes:ByteArray)=CRC32().apply{update(bytes)}.value
    private fun normalize(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
}

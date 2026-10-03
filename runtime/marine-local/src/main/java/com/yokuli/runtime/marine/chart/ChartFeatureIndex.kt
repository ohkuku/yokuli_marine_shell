package com.yokuli.runtime.marine.chart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor

/** S-57 与 GeoPackage 共用安装索引；仅向尚未发布的版本写入，事务由导入所有者控制。 */
internal object ChartFeatureIndex {
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE features (rowid INTEGER PRIMARY KEY,feature_id TEXT NOT NULL UNIQUE,cell TEXT NOT NULL,kind TEXT NOT NULL,detail_scale INTEGER,detail_tier INTEGER,name TEXT NOT NULL,search TEXT NOT NULL,payload BLOB NOT NULL)")
        // Android vendors are not required to ship SQLite's optional RTree module. Keep the same
        // spatial table contract and fall back to ordinary indexed bounds instead of rejecting an
        // otherwise valid S-57 / GeoPackage / raster-only dataset.
        try {
            db.execSQL("CREATE VIRTUAL TABLE spatial USING rtree(id,min_x,max_x,min_y,max_y)")
        } catch (error: SQLiteException) {
            val message=error.message.orEmpty()
            if(!message.contains("no such module",ignoreCase=true)||!message.contains("rtree",ignoreCase=true))throw error
            db.execSQL("DROP TABLE IF EXISTS spatial")
            db.execSQL("CREATE TABLE spatial (id INTEGER PRIMARY KEY,min_x REAL NOT NULL,max_x REAL NOT NULL,min_y REAL NOT NULL,max_y REAL NOT NULL)")
            db.execSQL("CREATE INDEX spatial_min_x ON spatial(min_x)")
            db.execSQL("CREATE INDEX spatial_max_x ON spatial(max_x)")
            db.execSQL("CREATE INDEX spatial_min_y ON spatial(min_y)")
            db.execSQL("CREATE INDEX spatial_max_y ON spatial(max_y)")
        }
        db.execSQL("CREATE TABLE spatial_feature (id INTEGER PRIMARY KEY,feature_row INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX spatial_feature_row ON spatial_feature(feature_row)")
        // Portable hierarchical bucket index for vendors without SQLite RTree. Every bbox is stored
        // exactly once at the smallest grid level that can contain it; queries probe a one-cell ring
        // at every level, avoiding a single global sentinel scan for ordinary large polygons.
        db.execSQL("CREATE TABLE spatial_bucket (spatial_id INTEGER PRIMARY KEY,bucket INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX spatial_bucket_key ON spatial_bucket(bucket,spatial_id)")
        db.execSQL("CREATE INDEX feature_cell ON features(cell,feature_id)")
        db.execSQL("CREATE INDEX feature_kind ON features(kind,feature_id)")
        db.execSQL("CREATE INDEX feature_cell_kind ON features(cell,kind,feature_id)")
        db.execSQL("CREATE INDEX feature_detail_scale ON features(detail_scale,feature_id)")
        db.execSQL("CREATE INDEX feature_detail_tier ON features(detail_tier,feature_id)")
        db.execSQL("CREATE INDEX feature_cell_tier_kind ON features(cell,detail_tier,kind,feature_id)")
        db.execSQL("CREATE INDEX feature_cell_scale_kind ON features(cell,detail_scale,kind,feature_id)")
        // 浏览按稳定 feature_id 分页、搜索走 search；未使用的 name 索引不再重复保存长对象 ID。
        ChartGeometrySpanIndex.create(db)
        db.execSQL("PRAGMA user_version=8")
    }

    fun insert(db:SQLiteDatabase,rowId:Long,feature:NauticalFeature,gson:Gson=Gson(),check:()->Unit={}):List<ChartBounds> {
        require(rowId in 1..2_000_000) {"CHART_FEATURE_LIMIT"}
        val payload=ChartFeaturePayload.encode(feature.copy(geometry=feature.geometry.copy(parts=emptyList())),gson,check)
        db.insertOrThrow("features",null,ContentValues().apply {
            put("rowid",rowId);put("feature_id",feature.id);put("cell",feature.cellId)
            put("kind",feature.kind.name);feature.detailScaleDenominator()?.let{put("detail_scale",it)}
            feature.detailTier()?.let{put("detail_tier",it)}
            put("name",names(feature).joinToString(" · "))
            put("search",searchableText(feature));put("payload",payload)
        })
        val geometryHash=ChartGeometrySpanIndex.insert(db,rowId,feature.geometry,check)
        ChartGeometrySpanIndex.appendIdentity(db,rowId,payload,geometryHash)
        return geometryBounds(feature.geometry).also {bounds->
            require(bounds.size<=2&&bounds.all {it.valid}) {"CHART_FEATURE_BOUNDS_INVALID:${feature.id}"}
            bounds.forEachIndexed {index,bound->
                val spatialId=rowId*2+index
                db.execSQL("INSERT INTO spatial VALUES (?,?,?,?,?)",arrayOf<Any>(spatialId,bound.west,bound.east,bound.south,bound.north))
                db.execSQL("INSERT INTO spatial_feature VALUES (?,?)",arrayOf(spatialId,rowId))
                db.execSQL("INSERT INTO spatial_bucket VALUES (?,?)",arrayOf(spatialId,spatialBucket(bound)))
            }
        }
    }

    /**
     * 已安装的 JSON 索引在旁路重建，最后才由所有者持锁原子替换。不能 UPDATE 后留下数 GB 空闲页，
     * 也不能持查询锁重建全国资料。失败、取消、空间不足保留旧索引；原始资料从不修改。
     * publish 必须在确认版本仍有效后用原子 rename 发布；旧只读 SQLite 连接可自然读完旧 inode。
     */
    fun compact(file:File,gson:Gson,check:()->Unit,publish:(File)->Unit):Boolean {
        if(!file.isFile)return false
        val temporary=File(file.parentFile,".${file.name}.${UUID.randomUUID()}.compact")
        try {
            check()
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {source->
                if(source.version>=8)return false
                require(source.version in 6..7){"CHART_FEATURE_INDEX_VERSION"}
                // 只需要紧凑副本和事务页的空间；不再额外复制整个旧数据库作备份。
                require(file.parentFile!!.usableSpace>32L*1024*1024){"CHART_STORAGE_FULL"}
                SQLiteDatabase.openOrCreateDatabase(temporary,null).use {target->
                    target.rawQuery("PRAGMA journal_mode=DELETE",null).use {rows->while(rows.moveToNext())Unit}
                    create(target)
                    val signal=CancellationSignal()
                    target.execSQL("ATTACH DATABASE ? AS compact_source",arrayOf<Any>(file.path))
                    // 附加的旧库仍需供前台读取，禁止 EXCLUSIVE 事务封锁旧库。
                    target.beginTransactionNonExclusive()
                    try {
                        var previous=0L
                        while(true) {
                            check()
                            val rows=ArrayList<Pair<Long,Int>>(128)
                            source.rawQuery("SELECT rowid,length(payload) FROM features WHERE rowid>? ORDER BY rowid LIMIT 128",arrayOf(previous.toString()),signal).use {cursor->
                                while(cursor.moveToNext())rows+=cursor.getLong(0) to cursor.getInt(1)
                            }
                            if(rows.isEmpty())break
                            for((row,length) in rows) {
                                check()
                                require(row in 1..2_000_000){"CHART_FEATURE_LIMIT"}
                                val feature=ChartFeaturePayload.read(source,row,length,gson,signal,check)
                                val encoded=ChartFeaturePayload.encode(feature.copy(geometry=feature.geometry.copy(parts=emptyList())),gson,check)
                                // 目录字段已经由原导入器判定；只替换几何编码，不重新推断来源/语义层级。
                                target.execSQL("INSERT INTO features(rowid,feature_id,cell,kind,detail_scale,detail_tier,name,search,payload) SELECT rowid,feature_id,cell,kind,detail_scale,detail_tier,name,search,? FROM compact_source.features WHERE rowid=?",arrayOf<Any>(encoded,row))
                                val geometryHash=ChartGeometrySpanIndex.insert(target,row,feature.geometry,check)
                                ChartGeometrySpanIndex.appendIdentity(target,row,encoded,geometryHash)
                                previous=row
                            }
                            require(file.parentFile!!.usableSpace>8L*1024*1024){"CHART_STORAGE_FULL"}
                        }
                        // 空间索引沿用已经确认的范围；按主键分批复制，不再排序全国每个多边形的顶点。
                        var first=1L
                        while(first<=previous*2+1) {
                            check();val end=minOf(first+4095,previous*2+1)
                            target.execSQL("INSERT INTO spatial SELECT id,min_x,max_x,min_y,max_y FROM compact_source.spatial WHERE id BETWEEN ? AND ?",arrayOf<Any>(first,end))
                            target.execSQL("INSERT INTO spatial_feature SELECT id,feature_row FROM compact_source.spatial_feature WHERE id BETWEEN ? AND ?",arrayOf<Any>(first,end))
                            target.execSQL("INSERT INTO spatial_bucket SELECT spatial_id,bucket FROM compact_source.spatial_bucket WHERE spatial_id BETWEEN ? AND ?",arrayOf<Any>(first,end))
                            first=end+1
                        }
                        check();target.setTransactionSuccessful()
                    }finally {
                        try{target.endTransaction()}finally{target.execSQL("DETACH DATABASE compact_source")}
                    }
                    ChartGeometrySpanIndex.validate(target)
                    target.rawQuery("PRAGMA quick_check(1)",null).use {rows->require(rows.moveToFirst()&&rows.getString(0)=="ok"){"CHART_FEATURE_INDEX_INVALID"}}
                }
            }
            check()
            // SQLite FULL 同步后仍显式同步新文件；发布前文件完整且没有 WAL/未提交事务。
            FileOutputStream(temporary,true).use{it.fd.sync()}
            check();publish(temporary)
            return true
        }finally {
            temporary.delete()
            File(temporary.path+"-journal").delete()
            File(temporary.path+"-wal").delete()
            File(temporary.path+"-shm").delete()
        }
    }

    private val BUCKET_LEVELS=doubleArrayOf(.25,1.0,4.0,16.0,64.0,360.0)
    private const val BUCKET_LEVEL_SHIFT=24

    private fun bucketCode(level:Int,lon:Int=0,lat:Int=0):Int {
        if(level==BUCKET_LEVELS.lastIndex)return level shl BUCKET_LEVEL_SHIFT
        val size=BUCKET_LEVELS[level]
        val lonCount=kotlin.math.ceil(360.0/size).toInt()
        return (level shl BUCKET_LEVEL_SHIFT) or (lat*lonCount+lon)
    }

    private fun spatialBucket(bound:ChartBounds):Int {
        val width=bound.east-bound.west
        val height=bound.north-bound.south
        val level=BUCKET_LEVELS.indices.firstOrNull {i->
            val size=BUCKET_LEVELS[i]
            width<=size+1e-12&&height<=size+1e-12
        } ?: BUCKET_LEVELS.lastIndex
        if(level==BUCKET_LEVELS.lastIndex)return bucketCode(level)
        val size=BUCKET_LEVELS[level]
        val lonCount=kotlin.math.ceil(360.0/size).toInt()
        val latCount=kotlin.math.ceil(180.0/size).toInt()
        val lon=floor(((bound.west+bound.east)/2+180.0)/size).toInt().coerceIn(0,lonCount-1)
        val lat=floor(((bound.south+bound.north)/2+90.0)/size).toInt().coerceIn(0,latCount-1)
        return bucketCode(level,lon,lat)
    }

    /**
     * Probe a one-cell ring at every hierarchy level. If a stored bbox at that level intersects
     * the query, its centre must fall within this ring. Broad queries fall back to native bbox/RTree.
     */
    fun queryBuckets(bounds:ChartBounds):List<Int>? {
        val result=linkedSetOf(bucketCode(BUCKET_LEVELS.lastIndex))
        for(level in 0 until BUCKET_LEVELS.lastIndex) {
            val size=BUCKET_LEVELS[level]
            val lonCount=kotlin.math.ceil(360.0/size).toInt()
            val latCount=kotlin.math.ceil(180.0/size).toInt()
            for(part in bounds.split()) {
                val minLon=floor((part.west+180.0)/size).toInt().coerceIn(0,lonCount-1)
                val maxLon=floor((part.east+180.0)/size).toInt().coerceIn(0,lonCount-1)
                val minLat=floor((part.south+90.0)/size).toInt().coerceIn(0,latCount-1)
                val maxLat=floor((part.north+90.0)/size).toInt().coerceIn(0,latCount-1)
                if((maxLon-minLon+3L)*(maxLat-minLat+3L)>256)return null
                for(lat in (minLat-1).coerceAtLeast(0)..(maxLat+1).coerceAtMost(latCount-1))
                    for(lon in (minLon-1).coerceAtLeast(0)..(maxLon+1).coerceAtMost(lonCount-1))
                        result+=bucketCode(level,lon,lat)
            }
        }
        return result.toList()
    }

    /** Unicode 规范化也用于旧索引的逐行回退，保证升级前后搜索语义一致。 */
    fun normalized(text:String):String=Normalizer.normalize(text,Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

    fun matches(feature:NauticalFeature,filter:ChartFeatureFilter,normalizedQuery:String):Boolean =
        (filter.cellId==null||feature.cellId==filter.cellId)&&
            (filter.kinds.isEmpty()||feature.kind in filter.kinds)&&
            (normalizedQuery.isEmpty()||searchableText(feature).contains(normalizedQuery))

    private fun names(feature:NauticalFeature):List<String> = feature.attributes.entries
        .filter {it.key.uppercase(Locale.ROOT) in setOf("OBJNAM","NOBJNM","NAME","NAME_EN","NAME_ZH","TITLE")}
        .map {it.value.trim()}.filter {it.isNotEmpty()}.distinct()

    private fun searchableText(feature:NauticalFeature):String=normalized((names(feature)+listOf(
        feature.acronym,feature.kind.name,feature.objectClass.toString(),feature.cellId,feature.id,
        feature.attributes["GPKG_TABLE"].orEmpty(),categoryTerms(feature.kind),
    )).joinToString("\n"))

    private fun categoryTerms(kind:NauticalFeatureKind):String=when(kind) {
        NauticalFeatureKind.LAND->"陆地 岸线 land coast"
        NauticalFeatureKind.SOUNDING->"水深 测深 sounding depth"
        NauticalFeatureKind.DEPTH_AREA->"水深 深度区 depth area"
        NauticalFeatureKind.DEPTH_CONTOUR->"水深 等深线 depth contour"
        NauticalFeatureKind.DRYING_AREA->"干出区 滩涂 drying tidal"
        NauticalFeatureKind.DREDGED_AREA->"疏浚区 航道 dredged area"
        NauticalFeatureKind.OBSTRUCTION->"障碍物 obstruction hazard"
        NauticalFeatureKind.WRECK->"沉船 wreck hazard"
        NauticalFeatureKind.ROCK->"礁石 岩礁 rock hazard"
        NauticalFeatureKind.BEACON->"航标 浮标 beacon buoy mark"
        NauticalFeatureKind.LIGHT->"灯 灯塔 灯标 light lighthouse"
        NauticalFeatureKind.TRAFFIC->"通航 航道 分道 traffic fairway"
        NauticalFeatureKind.RESTRICTED->"限制 禁区 restricted prohibition"
        NauticalFeatureKind.BRIDGE->"桥梁 桥 净空 bridge clearance"
        NauticalFeatureKind.OVERHEAD->"架空线 缆线 净空 overhead cable clearance"
        NauticalFeatureKind.COVERAGE->"覆盖 范围 coverage"
        NauticalFeatureKind.QUALITY->"质量 测量 quality survey"
        NauticalFeatureKind.OTHER->"其他 other"
    }
}

package com.yokuli.runtime.marine.chart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor

/** S-57 与 GeoPackage 共用安装索引；仅向尚未发布的版本写入，事务由导入所有者控制。 */
internal object ChartFeatureIndex {
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE features (rowid INTEGER PRIMARY KEY,feature_id TEXT NOT NULL UNIQUE,cell TEXT NOT NULL,kind TEXT NOT NULL,detail_scale INTEGER,detail_tier INTEGER,name TEXT NOT NULL,search TEXT NOT NULL,payload TEXT NOT NULL)")
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
        db.execSQL("CREATE INDEX feature_name ON features(name COLLATE NOCASE,feature_id)")
        db.execSQL("PRAGMA user_version=5")
    }

    fun insert(db:SQLiteDatabase,rowId:Long,feature:NauticalFeature,gson:Gson=Gson()):List<ChartBounds> {
        require(rowId in 1..2_000_000) {"CHART_FEATURE_LIMIT"}
        val payload=gson.toJson(feature)
        require(payload.length<=8_000_000) {"CHART_FEATURE_GEOMETRY_LIMIT:${feature.id}"}
        db.insertOrThrow("features",null,ContentValues().apply {
            put("rowid",rowId);put("feature_id",feature.id);put("cell",feature.cellId)
            put("kind",feature.kind.name);feature.detailScaleDenominator()?.let{put("detail_scale",it)}
            feature.detailTier()?.let{put("detail_tier",it)}
            put("name",names(feature).joinToString(" · "))
            put("search",searchableText(feature));put("payload",payload)
        })
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

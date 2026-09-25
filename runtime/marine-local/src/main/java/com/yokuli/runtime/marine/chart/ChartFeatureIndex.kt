package com.yokuli.runtime.marine.chart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import java.text.Normalizer
import java.util.Locale

/** S-57 与 GeoPackage 共用安装索引；仅向尚未发布的版本写入，事务由导入所有者控制。 */
internal object ChartFeatureIndex {
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE features (rowid INTEGER PRIMARY KEY,feature_id TEXT NOT NULL UNIQUE,cell TEXT NOT NULL,kind TEXT NOT NULL,name TEXT NOT NULL,search TEXT NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE VIRTUAL TABLE spatial USING rtree(id,min_x,max_x,min_y,max_y)")
        db.execSQL("CREATE TABLE spatial_feature (id INTEGER PRIMARY KEY,feature_row INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX spatial_feature_row ON spatial_feature(feature_row)")
        db.execSQL("CREATE INDEX feature_cell ON features(cell,feature_id)")
        db.execSQL("CREATE INDEX feature_kind ON features(kind,feature_id)")
        db.execSQL("CREATE INDEX feature_cell_kind ON features(cell,kind,feature_id)")
        db.execSQL("CREATE INDEX feature_name ON features(name COLLATE NOCASE,feature_id)")
        db.execSQL("PRAGMA user_version=2")
    }

    fun insert(db:SQLiteDatabase,rowId:Long,feature:NauticalFeature,gson:Gson=Gson()):List<ChartBounds> {
        require(rowId in 1..2_000_000) {"CHART_FEATURE_LIMIT"}
        val payload=gson.toJson(feature)
        require(payload.length<=8_000_000) {"CHART_FEATURE_GEOMETRY_LIMIT:${feature.id}"}
        db.insertOrThrow("features",null,ContentValues().apply {
            put("rowid",rowId);put("feature_id",feature.id);put("cell",feature.cellId)
            put("kind",feature.kind.name);put("name",names(feature).joinToString(" · "))
            put("search",searchableText(feature));put("payload",payload)
        })
        return geometryBounds(feature.geometry).also {bounds->
            require(bounds.size<=2&&bounds.all {it.valid}) {"CHART_FEATURE_BOUNDS_INVALID:${feature.id}"}
            bounds.forEachIndexed {index,bound->
                val spatialId=rowId*2+index
                db.execSQL("INSERT INTO spatial VALUES (?,?,?,?,?)",arrayOf<Any>(spatialId,bound.west,bound.east,bound.south,bound.north))
                db.execSQL("INSERT INTO spatial_feature VALUES (?,?)",arrayOf(spatialId,rowId))
            }
        }
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

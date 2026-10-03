package com.yokuli.runtime.marine.chart

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * 桌面可用 RTree 不代表每台 Android 都编入该 SQLite 模块。
 * 无模块时只把已校验包的空间目录旁路转成现有 portable 表；事实属性、全精度坐标块、内容身份不重算。
 * 从 RTree 原有叶节点原样复制向外取整的 bounds，不用局部几何猜覆盖或重新解析全国原件。
 * 节点格式依据 SQLite 官方 ext/rtree/rtree.c 的 Database Format of R-Tree Tables。
 */
internal object ChartNativeCompatibility {
    fun prepare(file: File, check: () -> Unit, progress: (Long, Long) -> Unit = { _, _ -> }): Boolean {
        val portable = SQLiteDatabase.openDatabase(file.path, null, FLAGS).use { source ->
            require(source.version == 8) { "CHART_NATIVE_VERSION_UNSUPPORTED" }
            source.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('trigger','view')", null).use {
                require(!it.moveToFirst()) { "CHART_NATIVE_SCHEMA_INVALID" }
            }
            val isRtree = source.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='spatial'", null).use {
                require(it.moveToFirst()) { "CHART_NATIVE_SPATIAL_INDEX_MISSING" }
                Regex("USING\\s+rtree\\s*\\(", RegexOption.IGNORE_CASE).containsMatchIn(it.getString(0).orEmpty())
            }
            if (!isRtree) false else try {
                source.rawQuery("SELECT id FROM spatial LIMIT 1", null).use { it.moveToFirst() }
                false
            } catch (error: SQLiteException) {
                if (!error.message.orEmpty().contains("no such module", ignoreCase = true) ||
                    !error.message.orEmpty().contains("rtree", ignoreCase = true)) throw error
                true
            }
        }
        if (!portable) return false
        val stage = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.portable")
        try {
            check()
            require(file.parentFile!!.usableSpace > file.length() + 32L * 1024 * 1024) { "CHART_STORAGE_FULL" }
            SQLiteDatabase.openOrCreateDatabase(stage, null).use { target ->
                target.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
                ChartFeatureIndex.create(target) // 唯一 writer 的既有无 RTree 分支。
                target.execSQL("ATTACH DATABASE ? AS native_source", arrayOf<Any>(file.path))
                try {
                    val total = target.rawQuery("SELECT count(*),COALESCE(MAX(rowid),0) FROM native_source.features", null).use {
                        require(it.moveToFirst()); val count = it.getLong(0); val last = it.getLong(1)
                        require(count in 0..2_000_000 && last in 0..2_000_000) { "CHART_FEATURE_LIMIT" }; last
                    }
                    target.beginTransactionNonExclusive()
                    try {
                        var first = 1L
                        while (first <= total) {
                            check()
                            val last = minOf(total, first + 63)
                            val args = arrayOf<Any>(first, last)
                            target.execSQL("INSERT INTO features(rowid,feature_id,cell,kind,detail_scale,detail_tier,name,search,payload) SELECT rowid,feature_id,cell,kind,detail_scale,detail_tier,name,search,payload FROM native_source.features WHERE rowid BETWEEN ? AND ?", args)
                            target.execSQL("INSERT INTO geometry_fact SELECT feature_row,geometry_hash,part_count,point_count FROM native_source.geometry_fact WHERE feature_row BETWEEN ? AND ?", args)
                            target.execSQL("INSERT INTO geometry_part SELECT feature_row,part_no,hole,point_count,closing_added,min_x,max_x,min_y,max_y FROM native_source.geometry_part WHERE feature_row BETWEEN ? AND ?", args)
                            target.execSQL("INSERT INTO geometry_span SELECT feature_row,part_no,span_no,point_start,point_count,anchor_x,min_x,max_x,min_y,max_y,raw_size,crc,payload FROM native_source.geometry_span WHERE feature_row BETWEEN ? AND ?", args)
                            first = last + 1
                            progress(last, total)
                            require(file.parentFile!!.usableSpace > 16L * 1024 * 1024) { "CHART_STORAGE_FULL" }
                        }
                        target.execSQL("DELETE FROM native_content")
                        target.execSQL("INSERT INTO native_content(identity,content_hash) SELECT identity,content_hash FROM native_source.native_content")
                        // 临时表只用于完整对账 leaf node -> feature，发布后的格式仍是统一 v8。
                        target.execSQL("CREATE TEMP TABLE imported_spatial_nodes(id INTEGER PRIMARY KEY,node INTEGER NOT NULL)")
                        target.execSQL("CREATE TEMP TABLE imported_leaf_nodes(node INTEGER PRIMARY KEY)")
                        target.execSQL("INSERT INTO imported_leaf_nodes SELECT DISTINCT nodeno FROM native_source.spatial_rowid")
                        var nodeAfter = 0L
                        var spatialCount = 0L
                        while (true) {
                            check()
                            val nodes = ArrayList<Pair<Long, ByteArray>>(64)
                            target.rawQuery("SELECT n.node,r.data FROM imported_leaf_nodes n CROSS JOIN native_source.spatial_node r ON r.nodeno=n.node WHERE n.node>? ORDER BY n.node LIMIT 64", arrayOf(nodeAfter.toString())).use { rows ->
                                while (rows.moveToNext()) nodes += rows.getLong(0) to rows.getBlob(1)
                            }
                            if (nodes.isEmpty()) break
                            for ((node, bytes) in nodes) {
                                check()
                                require(bytes.size in 4..65536) { "CHART_NATIVE_SPATIAL_INVALID" }
                                val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                                input.short // root depth；是否叶节点由 rowid 表的 nodeno 引用确定。
                                val count = input.short.toInt() and 0xffff
                                require(count > 0 && count <= (bytes.size - 4) / 24) { "CHART_NATIVE_SPATIAL_INVALID" }
                                repeat(count) {
                                    val id = input.long
                                    val west = input.float.toDouble(); val east = input.float.toDouble()
                                    val south = input.float.toDouble(); val north = input.float.toDouble()
                                    require(id in 1..4_000_001 && listOf(west,east,south,north).all { it.isFinite() } && west <= east && south <= north && south >= -90.01 && north <= 90.01) { "CHART_NATIVE_SPATIAL_INVALID" }
                                    target.execSQL("INSERT INTO spatial(id,min_x,max_x,min_y,max_y) VALUES(?,?,?,?,?)", arrayOf<Any>(id,west,east,south,north))
                                    target.execSQL("INSERT INTO imported_spatial_nodes(id,node) VALUES(?,?)", arrayOf<Any>(id,node))
                                    require(++spatialCount <= 4_000_000) { "CHART_FEATURE_LIMIT" }
                                }
                                nodeAfter = node
                            }
                        }
                        target.rawQuery("SELECT count(*) FROM native_source.spatial_rowid", null).use { require(it.moveToFirst() && it.getLong(0) == spatialCount) { "CHART_NATIVE_SPATIAL_INVALID" } }
                        target.rawQuery("SELECT 1 FROM imported_spatial_nodes n LEFT JOIN native_source.spatial_rowid r ON r.rowid=n.id WHERE r.rowid IS NULL OR r.nodeno<>n.node LIMIT 1", null).use { require(!it.moveToFirst()) { "CHART_NATIVE_SPATIAL_INVALID" } }
                        target.execSQL("INSERT INTO spatial_feature(id,feature_row) SELECT id,feature_row FROM native_source.spatial_feature")
                        target.execSQL("INSERT INTO spatial_bucket(spatial_id,bucket) SELECT spatial_id,bucket FROM native_source.spatial_bucket")
                        target.execSQL("DROP TABLE imported_spatial_nodes")
                        target.execSQL("DROP TABLE imported_leaf_nodes")
                        check(); target.setTransactionSuccessful()
                    } finally { target.endTransaction() }
                } finally { target.execSQL("DETACH DATABASE native_source") }
                check()
                ChartGeometrySpanIndex.validate(target)
                target.rawQuery("PRAGMA quick_check(1)", null).use { require(it.moveToFirst() && it.getString(0) == "ok") { "CHART_NATIVE_INDEX_INVALID" } }
            }
            check()
            FileOutputStream(stage, true).use { it.fd.sync() }
            Files.move(stage.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            stage.delete()
            File(stage.path + "-journal").delete()
            File(stage.path + "-wal").delete()
            File(stage.path + "-shm").delete()
        }
    }
    private const val FLAGS = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
}

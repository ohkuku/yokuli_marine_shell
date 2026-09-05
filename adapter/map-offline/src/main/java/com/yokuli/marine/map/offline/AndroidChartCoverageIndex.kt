package com.yokuli.marine.map.offline

import android.database.sqlite.SQLiteDatabase
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.map.domain.LocalChartTileIndex
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.SlippyTileKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reads the validated local MBTiles index. Package bounds are never used as tile evidence. */
class AndroidChartCoverageIndex(
    private val acquireLease: (ChartPackageId) -> ChartPackageLease = { ChartPackageLease {} },
) : LocalChartTileIndex {
    override suspend fun availableKeys(
        chartPackage: ChartPackage,
        requiredKeys: Set<SlippyTileKey>,
    ): Set<SlippyTileKey> = withContext(Dispatchers.IO) {
        if (requiredKeys.isEmpty()) return@withContext emptySet()
        require(chartPackage.tileScheme == MapTileScheme.MBTILES_TMS)
        require(requiredKeys.all { it.zoom in chartPackage.minZoom..chartPackage.maxZoom })
        val path = chartPackage.localUri.removePrefix(MBTILES_URI_PREFIX)
        require(path != chartPackage.localUri) { "Only app-private MBTiles URIs can be indexed" }
        val file = File(path)
        require(file.isFile) { "Installed MBTiles file is missing" }
        acquireLease(chartPackage.id).use {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                val available = hashSetOf<SlippyTileKey>()
                requiredKeys.groupBy(SlippyTileKey::zoom).forEach { (zoom, zoomKeys) ->
                    val dimension = 1 shl zoom
                    zoomKeys.chunked(KEYS_PER_QUERY).forEach { keys ->
                        currentCoroutineContext().ensureActive()
                        val predicates = keys.joinToString(" OR ") { "(tile_column = ? AND tile_row = ?)" }
                        val arguments = ArrayList<String>(1 + keys.size * 2).apply {
                            add(zoom.toString())
                            keys.forEach { key ->
                                add(key.x.toString())
                                add((dimension - 1 - key.y).toString())
                            }
                        }
                        database.rawQuery(
                            "SELECT tile_column, tile_row FROM tiles WHERE zoom_level = ? AND ($predicates)",
                            arguments.toTypedArray(),
                        ).use { cursor ->
                            while (cursor.moveToNext()) {
                                currentCoroutineContext().ensureActive()
                                val x = cursor.getInt(0)
                                val tmsY = cursor.getInt(1)
                                val xyzY = dimension - 1 - tmsY
                                runCatching { SlippyTileKey(zoom, x, xyzY) }.getOrNull()?.let(available::add)
                            }
                        }
                    }
                }
                available
            }
        }
    }

    private companion object {
        const val MBTILES_URI_PREFIX = "mbtiles://"
        // 1 zoom argument + 2 per key remains below Android SQLite's common 999-variable limit.
        const val KEYS_PER_QUERY = 400
    }
}

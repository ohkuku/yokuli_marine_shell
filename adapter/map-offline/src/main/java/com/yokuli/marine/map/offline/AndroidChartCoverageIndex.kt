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
                val remaining = requiredKeys.toHashSet()
                val available = hashSetOf<SlippyTileKey>()
                requiredKeys.map(SlippyTileKey::zoom).distinct().forEach { zoom ->
                    database.rawQuery(
                        "SELECT tile_column, tile_row FROM tiles WHERE zoom_level = ?",
                        arrayOf(zoom.toString()),
                    ).use { cursor ->
                        val dimension = 1 shl zoom
                        var rows = 0
                        while (cursor.moveToNext() && remaining.isNotEmpty()) {
                            if (++rows % CANCELLATION_CHECK_ROWS == 0) currentCoroutineContext().ensureActive()
                            val x = cursor.getInt(0)
                            val tmsY = cursor.getInt(1)
                            val xyzY = dimension - 1 - tmsY
                            val key = runCatching { SlippyTileKey(zoom, x, xyzY) }.getOrNull() ?: continue
                            if (remaining.remove(key)) available += key
                        }
                    }
                }
                available
            }
        }
    }

    private companion object {
        const val MBTILES_URI_PREFIX = "mbtiles://"
        const val CANCELLATION_CHECK_ROWS = 512
    }
}

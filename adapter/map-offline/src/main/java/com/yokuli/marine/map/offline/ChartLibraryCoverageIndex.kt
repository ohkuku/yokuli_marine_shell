package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.SlippyTileKey
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Coverage uses the exact same read session and coordinate mapper as renderer tile requests. */
class ChartLibraryCoverageIndex(private val access: ChartResourceAccessPort) {
    suspend fun availableKeys(
        request: ChartReadRequest,
        scheme: MapTileScheme,
        requiredKeys: Set<SlippyTileKey>,
    ): Set<SlippyTileKey> = withContext(Dispatchers.IO) {
        if (requiredKeys.isEmpty()) return@withContext emptySet()
        val opened = access.open(request.copy(purpose = com.yokuli.marine.map.domain.chartlibrary.ChartReadPurpose.COVERAGE))
        if (opened !is ChartOpenResult.Opened) return@withContext emptySet()
        opened.session.use { session ->
            buildSet {
                requiredKeys.chunked(KEYS_PER_BATCH).forEach { batch ->
                    currentCoroutineContext().ensureActive()
                    batch.forEach { key ->
                        val query = runCatching { ChartTileKey(key.zoom, key.x.toLong(), key.y.toLong()) }.getOrNull()
                        if (query != null && session.hasTile(query, scheme)) add(key)
                    }
                }
            }
        }
    }

    private companion object { const val KEYS_PER_BATCH = 400 }
}

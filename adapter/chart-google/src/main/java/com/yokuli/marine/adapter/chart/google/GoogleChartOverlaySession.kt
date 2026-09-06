package com.yokuli.marine.adapter.chart.google

import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartReadSession
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class GoogleChartTilePayload(
    val widthPx: Int,
    val heightPx: Int,
    val bytes: ByteArray,
)

/**
 * Thread-safe synchronous reader used by Google Maps' worker-owned TileProvider callbacks.
 * Every reader owns one revision-pinned read session and never mutates the user's source.
 */
class GoogleChartTileReader internal constructor(
    private val layer: ChartDisplayLayer,
    private val session: ChartReadSession,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    @Synchronized
    fun read(column: Int, row: Int, zoom: Int): GoogleChartTilePayload? {
        if (closed.get() || zoom !in layer.minZoom..layer.maxZoom || column < 0 || row < 0) return null
        val key = runCatching { ChartTileKey(zoom, column.toLong(), row.toLong()) }.getOrNull() ?: return null
        val tile = runCatching { session.readTile(key, layer.tileScheme) }.getOrNull() ?: return null
        return GoogleChartTilePayload(tile.widthPx, tile.heightPx, tile.bytes)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) session.close()
    }
}

data class PreparedGoogleChartLayer(
    val planLayer: ChartDisplayLayer,
    val reader: GoogleChartTileReader,
)

class PreparedGoogleChartDisplay internal constructor(
    val planFingerprint: String,
    val layers: List<PreparedGoogleChartLayer>,
    val rejectedLayerCount: Int,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) layers.forEach { runCatching { it.reader.close() } }
    }
}

class GoogleChartOverlayPreparer(private val access: ChartResourceAccessPort) {
    suspend fun prepare(plan: ChartDisplayPlan): PreparedGoogleChartDisplay {
        val prepared = mutableListOf<PreparedGoogleChartLayer>()
        var rejected = plan.omittedLayerCount
        try {
            plan.layers.forEach { layer ->
                currentCoroutineContext().ensureActive()
                when (val opened = access.open(layer.request)) {
                    is ChartOpenResult.Opened -> prepared += PreparedGoogleChartLayer(
                        layer,
                        GoogleChartTileReader(layer, opened.session),
                    )
                    is ChartOpenResult.Rejected -> rejected += 1
                }
            }
            return PreparedGoogleChartDisplay(plan.fingerprint, prepared, rejected)
        } catch (error: Throwable) {
            prepared.forEach { runCatching { it.reader.close() } }
            throw error
        }
    }
}

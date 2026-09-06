package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartReadPurpose
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PreparedChartLayer(
    val planLayer: ChartDisplayLayer,
    val registration: ChartRasterRegistration,
)

class PreparedChartDisplay internal constructor(
    val planFingerprint: String,
    val layers: List<PreparedChartLayer>,
    val rejectedLayerCount: Int,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    override fun close() {
        if (closed.compareAndSet(false, true)) layers.forEach { runCatching { it.registration.close() } }
    }
}

class ChartDisplayPreparer(
    private val access: ChartResourceAccessPort,
    private val gateway: ChartLoopbackTileGateway,
) {
    suspend fun prepare(plan: ChartDisplayPlan): PreparedChartDisplay {
        val prepared = mutableListOf<PreparedChartLayer>()
        var rejected = plan.omittedLayerCount
        try {
            plan.layers.forEach { layer ->
                currentCoroutineContext().ensureActive()
                val opened = access.open(layer.request.copy(purpose = ChartReadPurpose.RENDER))
                if (opened !is ChartOpenResult.Opened) {
                    rejected += 1
                    return@forEach
                }
                val registration = try {
                    gateway.register(
                        opened.session,
                        layer.tileScheme,
                        layer.tileSize,
                        layer.minZoom,
                        layer.maxZoom,
                    )
                } catch (error: Throwable) {
                    opened.session.close()
                    throw error
                }
                prepared += PreparedChartLayer(layer, registration)
            }
            return PreparedChartDisplay(plan.fingerprint, prepared, rejected)
        } catch (error: Throwable) {
            prepared.forEach { runCatching { it.registration.close() } }
            throw error
        }
    }
}

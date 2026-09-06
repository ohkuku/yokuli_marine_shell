package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.SlippyTileKey
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayCoverageEvaluator
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayCoverageResult
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayCoveragePort
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Coverage uses the exact same read session and coordinate mapper as renderer tile requests. */
class ChartLibraryCoverageIndex(private val access: ChartResourceAccessPort) {
    suspend fun probe(
        request: ChartReadRequest,
        scheme: MapTileScheme,
        requiredKeys: Set<SlippyTileKey>,
    ): ChartCoverageProbeResult = withContext(Dispatchers.IO) {
        if (requiredKeys.isEmpty()) return@withContext ChartCoverageProbeResult.Readable(emptySet())
        val opened = access.open(request.copy(purpose = com.yokuli.marine.map.domain.chartlibrary.ChartReadPurpose.COVERAGE))
        if (opened !is ChartOpenResult.Opened) return@withContext ChartCoverageProbeResult.Unreadable
        opened.session.use { session ->
            ChartCoverageProbeResult.Readable(
                buildSet {
                    requiredKeys.chunked(KEYS_PER_BATCH).forEach { batch ->
                        currentCoroutineContext().ensureActive()
                        batch.forEach { key ->
                            val query = runCatching { ChartTileKey(key.zoom, key.x.toLong(), key.y.toLong()) }.getOrNull()
                            if (query != null && session.hasTile(query, scheme)) add(key)
                        }
                    }
                },
            )
        }
    }

    suspend fun availableKeys(
        request: ChartReadRequest,
        scheme: MapTileScheme,
        requiredKeys: Set<SlippyTileKey>,
    ): Set<SlippyTileKey> = when (val result = probe(request, scheme, requiredKeys)) {
        is ChartCoverageProbeResult.Readable -> result.availableKeys
        ChartCoverageProbeResult.Unreadable -> emptySet()
    }

    private companion object { const val KEYS_PER_BATCH = 400 }
}

sealed interface ChartCoverageProbeResult {
    data class Readable(val availableKeys: Set<SlippyTileKey>) : ChartCoverageProbeResult
    data object Unreadable : ChartCoverageProbeResult
}

/** Evaluates a display plan with the exact read port and TMS/XYZ mapping used by rendering. */
class ChartDisplayCoverageIndex(access: ChartResourceAccessPort) : ChartDisplayCoveragePort {
    private val index = ChartLibraryCoverageIndex(access)

    override suspend fun evaluate(
        plan: ChartDisplayPlan,
        targetZoom: Int,
        requiredKeys: Set<SlippyTileKey>,
        expectedFingerprint: String,
    ): ChartDisplayCoverageResult {
        if (expectedFingerprint != plan.fingerprint) return ChartDisplayCoverageEvaluator.evaluate(
            plan, targetZoom, requiredKeys, emptyMap(), expectedFingerprint,
        )
        val available = linkedMapOf<com.yokuli.marine.map.domain.chartlibrary.ChartAssetId, Set<SlippyTileKey>>()
        plan.layers.asSequence()
            .filter { it.role == ChartAssetRole.BASE && targetZoom in it.minZoom..it.maxZoom }
            .forEach { layer ->
                when (val result = index.probe(layer.request, layer.tileScheme, requiredKeys)) {
                    is ChartCoverageProbeResult.Readable -> available[layer.assetId] = result.availableKeys
                    ChartCoverageProbeResult.Unreadable -> Unit
                }
            }
        return ChartDisplayCoverageEvaluator.evaluate(plan, targetZoom, requiredKeys, available, expectedFingerprint)
    }
}

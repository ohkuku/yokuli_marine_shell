package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.LocalChartTileIndex
import com.yokuli.marine.map.domain.OfflineCoverageArea
import com.yokuli.marine.map.domain.OfflineCoverageEvaluator
import com.yokuli.marine.map.domain.OfflineCoverageFingerprint
import com.yokuli.marine.map.domain.OfflineCoveragePlan
import com.yokuli.marine.map.domain.OfflineCoveragePlanner
import com.yokuli.marine.map.domain.OfflineCoverageRequest
import com.yokuli.marine.map.domain.OfflineCoverageResult
import com.yokuli.marine.map.domain.OfflineCoverageTooLargeException
import com.yokuli.marine.map.domain.SavedRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class OfflineCoverageFailure { NO_ELIGIBLE_PACKAGE, INDEX_UNAVAILABLE }

sealed interface OfflineCoverageUiState {
    data object Idle : OfflineCoverageUiState
    data class Planning(
        val routeId: String,
        val fingerprint: OfflineCoverageFingerprint,
    ) : OfflineCoverageUiState
    data class Checking(
        val routeId: String,
        val fingerprint: OfflineCoverageFingerprint,
        val requiredKeyCount: Int,
    ) : OfflineCoverageUiState

    data class Ready(
        val request: OfflineCoverageRequest,
        val result: OfflineCoverageResult,
    ) : OfflineCoverageUiState

    data class TooLarge(val routeId: String, val maximumKeys: Int) : OfflineCoverageUiState
    data class Cancelled(val routeId: String) : OfflineCoverageUiState
    data class Stale(val routeId: String, val previous: OfflineCoverageResult?) : OfflineCoverageUiState
    data class Failed(val routeId: String, val reason: OfflineCoverageFailure) : OfflineCoverageUiState
}

class OfflineCoverageCoordinator(
    private val tileIndex: LocalChartTileIndex,
    private val scope: CoroutineScope,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maximumKeys: Int = OfflineCoveragePlanner.MAX_REQUIRED_TILE_KEYS,
    private val incidentLogger: (Throwable) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<OfflineCoverageUiState>(OfflineCoverageUiState.Idle)
    val state: StateFlow<OfflineCoverageUiState> = mutableState

    private var activeJob: Job? = null
    private var generation: Long = 0L
    private var activeRequest: OfflineCoverageRequest? = null
    private var activeLogicalPackageIds: Set<String> = emptySet()

    @Synchronized
    fun start(
        route: SavedRoute,
        packages: List<ChartPackage>,
        targetZoom: Int,
        halfWidthNauticalMiles: Double,
        alternateAreas: List<OfflineCoverageArea> = emptyList(),
    ) {
        val eligible = packages.filter { targetZoom in it.minZoom..it.maxZoom }.distinctBy { it.versionId }
        val currentGeneration = ++generation
        activeJob?.cancel()
        if (eligible.isEmpty()) {
            activeRequest = null
            activeLogicalPackageIds = emptySet()
            mutableState.value = OfflineCoverageUiState.Failed(route.id, OfflineCoverageFailure.NO_ELIGIBLE_PACKAGE)
            return
        }
        val request = OfflineCoverageRequest(
            routeId = route.id,
            routeRevision = route.revision,
            routePoints = route.waypoints,
            packageVersionIds = eligible.map(ChartPackage::versionId),
            targetZoom = targetZoom,
            halfWidthNauticalMiles = halfWidthNauticalMiles,
            alternateAreas = alternateAreas,
            maxRequiredKeys = maximumKeys,
        )
        activeRequest = request
        activeLogicalPackageIds = eligible.map { it.logicalId.value }.toSet()
        mutableState.value = OfflineCoverageUiState.Planning(route.id, OfflineCoverageFingerprint.of(request))
        activeJob = scope.launch(workerDispatcher) {
            try {
                val plan = OfflineCoveragePlanner.plan(request)
                publish(currentGeneration, OfflineCoverageUiState.Checking(route.id, plan.fingerprint, plan.requiredKeys.size))
                val available = eligible.associate { chartPackage ->
                    ensureActive()
                    chartPackage.versionId to tileIndex.availableKeys(chartPackage, plan.requiredKeys)
                }
                ensureActive()
                publish(
                    currentGeneration,
                    OfflineCoverageUiState.Ready(request, OfflineCoverageEvaluator.evaluate(plan, available)),
                )
            } catch (_: CancellationException) {
                // cancel()/invalidate() own their visible state; a late cancelled job cannot overwrite it.
            } catch (tooLarge: OfflineCoverageTooLargeException) {
                publish(currentGeneration, OfflineCoverageUiState.TooLarge(route.id, tooLarge.maximumKeys))
            } catch (error: Throwable) {
                incidentLogger(error)
                publish(currentGeneration, OfflineCoverageUiState.Failed(route.id, OfflineCoverageFailure.INDEX_UNAVAILABLE))
            }
        }
    }

    @Synchronized
    fun cancel() {
        val routeId = activeRequest?.routeId ?: return
        generation += 1L
        activeJob?.cancel()
        activeJob = null
        activeRequest = null
        activeLogicalPackageIds = emptySet()
        mutableState.value = OfflineCoverageUiState.Cancelled(routeId)
    }

    @Synchronized
    fun invalidateIfInputsChanged(routes: List<SavedRoute>, packages: List<ChartPackage>) {
        val previous = activeRequest ?: return
        val route = routes.firstOrNull { it.id == previous.routeId }
        val currentVersions = packages
            .filter { it.logicalId.value in activeLogicalPackageIds && previous.targetZoom in it.minZoom..it.maxZoom }
            .map(ChartPackage::versionId)
        val changed = route == null || runCatching {
            OfflineCoverageFingerprint.of(
                previous.copy(
                    routeRevision = route.revision,
                    routePoints = route.waypoints,
                    packageVersionIds = currentVersions,
                ),
            ) != OfflineCoverageFingerprint.of(previous)
        }.getOrDefault(true)
        if (!changed) return
        generation += 1L
        activeJob?.cancel()
        activeJob = null
        val previousResult = (mutableState.value as? OfflineCoverageUiState.Ready)?.result
        mutableState.value = OfflineCoverageUiState.Stale(previous.routeId, previousResult)
    }

    @Synchronized
    private fun publish(expectedGeneration: Long, value: OfflineCoverageUiState) {
        if (generation == expectedGeneration) mutableState.value = value
    }
}

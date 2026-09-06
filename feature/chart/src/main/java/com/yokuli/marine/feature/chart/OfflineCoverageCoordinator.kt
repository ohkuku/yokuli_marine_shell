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
import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.ContentFootprint
import com.yokuli.marine.map.domain.NavigationSuitability
import com.yokuli.marine.map.domain.TileAvailability
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayCoveragePort
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayCoverageStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
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
    private val displayCoverage: ChartDisplayCoveragePort? = null,
) {
    private val mutableState = MutableStateFlow<OfflineCoverageUiState>(OfflineCoverageUiState.Idle)
    val state: StateFlow<OfflineCoverageUiState> = mutableState

    private var activeJob: Job? = null
    private var generation: Long = 0L
    private var activeRequest: OfflineCoverageRequest? = null
    private var activeLogicalPackageIds: Set<String> = emptySet()
    private var activeDisplayPlanFingerprint: String? = null

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
            activeDisplayPlanFingerprint = null
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
        activeDisplayPlanFingerprint = null
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
    fun start(
        route: SavedRoute,
        displayPlan: ChartDisplayPlan,
        targetZoom: Int,
        halfWidthNauticalMiles: Double,
        alternateAreas: List<OfflineCoverageArea> = emptyList(),
    ) {
        val coverage = displayCoverage
        val eligibleBase = displayPlan.layers.any { it.role == com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole.BASE && targetZoom in it.minZoom..it.maxZoom }
        val currentGeneration = ++generation
        activeJob?.cancel()
        if (coverage == null || !eligibleBase) {
            activeRequest = null
            activeLogicalPackageIds = emptySet()
            activeDisplayPlanFingerprint = null
            mutableState.value = OfflineCoverageUiState.Failed(route.id, OfflineCoverageFailure.NO_ELIGIBLE_PACKAGE)
            return
        }
        val request = OfflineCoverageRequest(
            routeId = route.id,
            routeRevision = route.revision,
            routePoints = route.waypoints,
            packageVersionIds = listOf(ChartPackageVersionId(displayPlan.fingerprint)),
            targetZoom = targetZoom,
            halfWidthNauticalMiles = halfWidthNauticalMiles,
            alternateAreas = alternateAreas,
            maxRequiredKeys = maximumKeys,
        )
        activeRequest = request
        activeLogicalPackageIds = emptySet()
        activeDisplayPlanFingerprint = displayPlan.fingerprint
        mutableState.value = OfflineCoverageUiState.Planning(route.id, OfflineCoverageFingerprint.of(request))
        activeJob = scope.launch(workerDispatcher) {
            try {
                val routePlan = OfflineCoveragePlanner.plan(request)
                publish(currentGeneration, OfflineCoverageUiState.Checking(route.id, routePlan.fingerprint, routePlan.requiredKeys.size))
                val result = coverage.evaluate(
                    displayPlan,
                    targetZoom,
                    routePlan.requiredKeys,
                    displayPlan.fingerprint,
                )
                ensureActive()
                if (result.status == ChartDisplayCoverageStatus.STALE) {
                    publish(currentGeneration, OfflineCoverageUiState.Stale(route.id, null))
                } else {
                    publish(
                        currentGeneration,
                        OfflineCoverageUiState.Ready(
                            request,
                            OfflineCoverageResult(
                                fingerprint = routePlan.fingerprint,
                                tileAvailability = when (result.status) {
                                    ChartDisplayCoverageStatus.COMPLETE -> TileAvailability.AVAILABLE
                                    ChartDisplayCoverageStatus.PARTIAL -> TileAvailability.MISSING
                                    ChartDisplayCoverageStatus.NOT_CHECKED,
                                    ChartDisplayCoverageStatus.UNREADABLE,
                                    ChartDisplayCoverageStatus.STALE,
                                    -> TileAvailability.UNKNOWN
                                },
                                contentFootprint = ContentFootprint.NOT_VERIFIED,
                                navigationSuitability = NavigationSuitability.NOT_ASSESSED,
                                requiredKeyCount = result.requiredKeyCount,
                                missingKeys = result.missingKeys,
                            ),
                        ),
                    )
                }
            } catch (_: CancellationException) {
                // cancel()/invalidate() own their visible state.
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
        activeDisplayPlanFingerprint = null
        mutableState.value = OfflineCoverageUiState.Cancelled(routeId)
    }

    @Synchronized
    fun invalidateIfInputsChanged(routes: List<SavedRoute>, packages: List<ChartPackage>) {
        if (activeDisplayPlanFingerprint != null) return
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
    fun invalidateIfDisplayInputsChanged(routes: List<SavedRoute>, displayPlan: ChartDisplayPlan) {
        val activeFingerprint = activeDisplayPlanFingerprint ?: return
        val previous = activeRequest ?: return
        val route = routes.firstOrNull { it.id == previous.routeId }
        if (route != null && route.revision == previous.routeRevision && displayPlan.fingerprint == activeFingerprint) return
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

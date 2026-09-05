package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.ContentFootprint
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.MapTransient
import com.yokuli.marine.map.domain.PlaceSearch
import com.yokuli.marine.map.domain.TileAvailability
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.MarineTileSize
import java.text.Normalizer
import java.util.LinkedHashMap
import java.util.Locale

enum class ChartLauncherPriority { WRITE_FAILURE, UNSAVED, EDITING_DRAFT, SELECTED_PLAN, LAST_VIEW, ENTRY }

enum class ChartLauncherStatus {
    WRITE_FAILED,
    SAVING,
    EDITING_DRAFT,
    PLAN_SELECTED,
    COVERAGE_CHECKING,
    COVERAGE_STALE,
    COVERAGE_UNAVAILABLE,
    COVERAGE_TOO_LARGE,
    TILES_AVAILABLE_CONTENT_UNVERIFIED,
    TILES_AVAILABLE_CONTENT_OBSERVED,
    TILES_MISSING,
    TILES_UNKNOWN,
    LOCAL_CHART_SELECTED,
    NO_LOCAL_CHART,
    READY_TO_BROWSE,
}

enum class ChartPreviewLabel { DRAFT, PLAN }

data class ChartRoutePreview(
    val points: List<GeoPoint>,
    val revision: Long,
    val label: ChartPreviewLabel,
) {
    init {
        require(revision >= 0L)
    }
}

data class ChartLauncherSnapshot(
    val priority: ChartLauncherPriority,
    val status: ChartLauncherStatus,
    val subjectName: String? = null,
    val routePreview: ChartRoutePreview? = null,
    val camera: MapCamera? = null,
    /** Failures and in-flight unsaved writes may update even while Start edit chrome is active. */
    val critical: Boolean = false,
)

object ChartLauncherProjection {
    fun project(state: MapState, coverage: OfflineCoverageUiState): ChartLauncherSnapshot {
        val draft = state.routeDraft
        val plan = state.activeRoutePlanId?.let { id -> state.savedRoutes.firstOrNull { it.id == id } }
        val subject = draft?.name?.takeIf(String::isNotBlank) ?: plan?.name
        val preview = draft?.let {
            ChartRoutePreview(it.waypoints.toList(), it.revision, ChartPreviewLabel.DRAFT)
        } ?: plan?.let {
            ChartRoutePreview(it.waypoints.toList(), it.revision, ChartPreviewLabel.PLAN)
        }
        val writeFailed = state.saveState == MapSaveState.FAILED ||
            state.placeSaveStatus?.state == MapSaveState.FAILED ||
            state.routeSaveStatus?.state == MapSaveState.FAILED ||
            state.persistenceFailure != null ||
            state.libraryLoadState in setOf(MapLibraryLoadState.READ_FAILED, MapLibraryLoadState.CORRUPT)
        if (writeFailed) return ChartLauncherSnapshot(
            ChartLauncherPriority.WRITE_FAILURE,
            ChartLauncherStatus.WRITE_FAILED,
            subject,
            preview,
            critical = true,
        )

        val unsaved = state.saveState == MapSaveState.PENDING ||
            state.placeSaveStatus?.state == MapSaveState.PENDING ||
            state.routeSaveStatus?.state == MapSaveState.PENDING
        if (unsaved) return ChartLauncherSnapshot(
            ChartLauncherPriority.UNSAVED,
            ChartLauncherStatus.SAVING,
            subject,
            preview,
            critical = true,
        )

        if (draft != null) return ChartLauncherSnapshot(
            ChartLauncherPriority.EDITING_DRAFT,
            ChartLauncherStatus.EDITING_DRAFT,
            draft.name.takeIf(String::isNotBlank),
            preview,
        )

        if (plan != null) return ChartLauncherSnapshot(
            ChartLauncherPriority.SELECTED_PLAN,
            selectedPlanStatus(state, plan.id, plan.revision, plan.waypoints, coverage),
            plan.name,
            preview,
        )

        if (state.renderer.generation != null) return ChartLauncherSnapshot(
            ChartLauncherPriority.LAST_VIEW,
            if (state.activeChartPackageId != null && state.chartPackages.any { it.id == state.activeChartPackageId }) {
                ChartLauncherStatus.LOCAL_CHART_SELECTED
            } else {
                ChartLauncherStatus.NO_LOCAL_CHART
            },
            camera = state.camera,
        )

        return ChartLauncherSnapshot(ChartLauncherPriority.ENTRY, ChartLauncherStatus.READY_TO_BROWSE)
    }

    private fun selectedPlanStatus(
        state: MapState,
        routeId: String,
        routeRevision: Long,
        routePoints: List<GeoPoint>,
        coverage: OfflineCoverageUiState,
    ): ChartLauncherStatus = when (coverage) {
        is OfflineCoverageUiState.Planning -> if (coverage.routeId == routeId) ChartLauncherStatus.COVERAGE_CHECKING else ChartLauncherStatus.PLAN_SELECTED
        is OfflineCoverageUiState.Checking -> if (coverage.routeId == routeId) ChartLauncherStatus.COVERAGE_CHECKING else ChartLauncherStatus.PLAN_SELECTED
        is OfflineCoverageUiState.Stale -> if (coverage.routeId == routeId) ChartLauncherStatus.COVERAGE_STALE else ChartLauncherStatus.PLAN_SELECTED
        is OfflineCoverageUiState.Failed -> if (coverage.routeId == routeId) ChartLauncherStatus.COVERAGE_UNAVAILABLE else ChartLauncherStatus.PLAN_SELECTED
        is OfflineCoverageUiState.TooLarge -> if (coverage.routeId == routeId) ChartLauncherStatus.COVERAGE_TOO_LARGE else ChartLauncherStatus.PLAN_SELECTED
        is OfflineCoverageUiState.Cancelled -> if (coverage.routeId == routeId) ChartLauncherStatus.PLAN_SELECTED else ChartLauncherStatus.PLAN_SELECTED
        is OfflineCoverageUiState.Ready -> {
            val request = coverage.request
            val currentVersions = state.chartPackages
                .filter { request.targetZoom in it.minZoom..it.maxZoom }
                .map { it.versionId }
                .toSet()
            val current = request.routeId == routeId &&
                request.routeRevision == routeRevision &&
                request.routePoints == routePoints &&
                request.packageVersionIds.toSet() == currentVersions &&
                coverage.result.fingerprint == com.yokuli.marine.map.domain.OfflineCoverageFingerprint.of(request)
            if (!current) {
                ChartLauncherStatus.COVERAGE_STALE
            } else when (coverage.result.tileAvailability) {
                TileAvailability.AVAILABLE -> if (coverage.result.contentFootprint == ContentFootprint.VERIFIED_VISIBLE) {
                    ChartLauncherStatus.TILES_AVAILABLE_CONTENT_OBSERVED
                } else {
                    ChartLauncherStatus.TILES_AVAILABLE_CONTENT_UNVERIFIED
                }
                TileAvailability.MISSING -> ChartLauncherStatus.TILES_MISSING
                TileAvailability.UNKNOWN -> ChartLauncherStatus.TILES_UNKNOWN
            }
        }
        OfflineCoverageUiState.Idle -> ChartLauncherStatus.PLAN_SELECTED
    }
}

sealed interface ChartLaunchTarget {
    data object Browse : ChartLaunchTarget
    data class Place(val id: String) : ChartLaunchTarget
    data class Route(val id: String) : ChartLaunchTarget
}

object ChartLaunchProjector {
    fun action(target: ChartLaunchTarget, state: MapState): MapAction? = when (target) {
        ChartLaunchTarget.Browse -> null
        is ChartLaunchTarget.Place -> MapAction.OpenSurface(MapSurface.PlaceDetail(target.id))
        is ChartLaunchTarget.Route -> if (state.savedRoutes.any { it.id == target.id }) {
            MapAction.PreviewRoutePlan(target.id)
        } else {
            MapAction.OpenSurface(MapSurface.RouteDetail(target.id))
        }
    }

    fun isSettled(target: ChartLaunchTarget, state: MapState): Boolean = when (target) {
        ChartLaunchTarget.Browse -> true
        is ChartLaunchTarget.Place -> state.surface == MapSurface.PlaceDetail(target.id) ||
            (state.transient as? MapTransient.UnavailableObject)?.objectId == target.id
        is ChartLaunchTarget.Route -> state.surface == MapSurface.RouteDetail(target.id) ||
            (state.transient as? MapTransient.UnavailableObject)?.objectId == target.id
    }
}

enum class ChartSearchKind { PLACE, ROUTE }

data class ChartSearchItem(
    val kind: ChartSearchKind,
    val sourceId: String,
    val title: String,
    val token: LaunchToken,
)

object ChartSearchProjection {
    fun search(state: MapState, query: String, maximumResults: Int = 24): List<ChartSearchItem> {
        require(maximumResults > 0)
        if (query.isBlank()) return emptyList()
        val placeItems = PlaceSearch.filterAndSort(state.places, query).mapNotNull { place ->
            ChartDestinations.placeOrNull(place.id)?.let { ChartSearchItem(ChartSearchKind.PLACE, place.id, place.name, it) }
        }
        val normalized = query.searchNormalized()
        val routeItems = state.savedRoutes.asSequence()
            .filter { route ->
                normalized in route.name.searchNormalized() || normalized in route.notes.searchNormalized()
            }
            .sortedBy { it.name.searchNormalized() }
            .mapNotNull { route ->
                ChartDestinations.routeOrNull(route.id)?.let { ChartSearchItem(ChartSearchKind.ROUTE, route.id, route.name, it) }
            }
            .toList()
        return (placeItems + routeItems).take(maximumResults)
    }

    private fun String.searchNormalized(): String = Normalizer.normalize(trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}

/**
 * Optional foreground snapshots may use this boundary. C11 production currently uses only pure
 * route geometry, but this prevents a future late callback or stale source/style key from winning.
 */
data class ChartTilePreviewKey(
    val camera: MapCamera,
    val sourceVersionId: ChartPackageVersionId?,
    val styleRevision: String,
    val routeRevision: Long?,
    val size: MarineTileSize,
) {
    init {
        require(styleRevision.isNotBlank())
        require(routeRevision == null || routeRevision >= 0L)
    }
}

class ChartTilePreviewCache<Value : Any>(private val maximumEntries: Int) {
    init { require(maximumEntries > 0) }

    private val entries = LinkedHashMap<ChartTilePreviewKey, Value>(maximumEntries, .75f, true)
    private var generation = 0L
    private var currentRequest: Pair<Long, ChartTilePreviewKey>? = null

    val size: Int @Synchronized get() = entries.size

    @Synchronized
    fun begin(key: ChartTilePreviewKey): Long {
        generation += 1L
        currentRequest = generation to key
        return generation
    }

    @Synchronized
    fun complete(expectedGeneration: Long, key: ChartTilePreviewKey, value: Value): Boolean {
        if (currentRequest != expectedGeneration to key) return false
        entries[key] = value
        while (entries.size > maximumEntries) entries.remove(entries.entries.first().key)
        return true
    }

    @Synchronized
    operator fun get(key: ChartTilePreviewKey): Value? = entries[key]

    @Synchronized
    fun current(): Value? = currentRequest?.second?.let(entries::get)
}

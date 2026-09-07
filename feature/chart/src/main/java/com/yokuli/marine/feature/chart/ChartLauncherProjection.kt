package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.MapTransient
import com.yokuli.marine.map.domain.PlaceSearch
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.MarineTileSize
import java.text.Normalizer
import java.util.LinkedHashMap
import java.util.Locale

enum class ChartLauncherPriority { LAST_VIEW, ENTRY }

enum class ChartLauncherStatus { LAST_VIEW, READY_TO_BROWSE }

data class ChartLauncherSnapshot(
    val priority: ChartLauncherPriority,
    val status: ChartLauncherStatus,
    val camera: MapCamera? = null,
)

object ChartLauncherProjection {
    fun project(state: MapState): ChartLauncherSnapshot {
        if (state.renderer.generation != null) return ChartLauncherSnapshot(
            ChartLauncherPriority.LAST_VIEW,
            ChartLauncherStatus.LAST_VIEW,
            camera = state.camera,
        )

        return ChartLauncherSnapshot(ChartLauncherPriority.ENTRY, ChartLauncherStatus.READY_TO_BROWSE)
    }
}

sealed interface ChartDestination {
    data object Browse : ChartDestination
    data class Place(val id: String) : ChartDestination
    data class Route(val id: String) : ChartDestination
    data class ChartAsset(val id: ChartAssetId) : ChartDestination
}

object ChartLaunchProjector {
    fun action(target: ChartDestination, state: MapState): MapAction? = when (target) {
        ChartDestination.Browse -> null
        is ChartDestination.Place -> MapAction.FocusSavedPlace(target.id)
        is ChartDestination.Route -> if (state.savedRoutes.any { it.id == target.id }) {
            MapAction.PreviewRoutePlan(target.id)
        } else {
            MapAction.OpenSurface(MapSurface.RouteDetail(target.id))
        }
        is ChartDestination.ChartAsset -> MapAction.OpenSurface(MapSurface.Root)
    }

    fun isSettled(target: ChartDestination, state: MapState): Boolean = when (target) {
        ChartDestination.Browse -> true
        is ChartDestination.Place ->
            (state.transient as? MapTransient.SelectedObject)?.hit?.objectId == "place:${target.id}" ||
            (state.transient as? MapTransient.UnavailableObject)?.objectId == target.id
        is ChartDestination.Route -> (state.surface == MapSurface.Root && state.activeRoutePlanId == target.id) ||
            (state.transient as? MapTransient.UnavailableObject)?.objectId == target.id
        is ChartDestination.ChartAsset -> state.surface == MapSurface.Root
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

/** Stable per-renderer slot: live decoration freezes while Start edit chrome is active. */
class ChartLauncherDisplaySlot(initial: ChartLauncherSnapshot) {
    var shown: ChartLauncherSnapshot = initial
        private set

    fun resolve(incoming: ChartLauncherSnapshot, liveContentEnabled: Boolean): ChartLauncherSnapshot {
        if (liveContentEnabled) shown = incoming
        return shown
    }
}

package com.yokuli.marine.map.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.UUID

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude out of range" }
    }
}

data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south <= north) { "South must not exceed north" }
        require(west <= east) { "West must not exceed east" }
        GeoPoint(south, west)
        GeoPoint(north, east)
    }
}

data class MapCamera(
    val center: GeoPoint = GeoPoint(-36.8485, 174.7633),
    val zoom: Double = 11.0,
    val bearing: Double = 0.0,
) {
    init {
        require(zoom.isFinite() && zoom in 0.0..24.0) { "Zoom out of range" }
        require(bearing.isFinite()) { "Bearing must be finite" }
    }
}

enum class PositionAvailability { UNAVAILABLE, STALE, FRESH }

data class PositionObservation(
    val observationId: String,
    val point: GeoPoint,
    val observedAtMillis: Long,
    val source: String,
) {
    init {
        require(observationId.isNotBlank()) { "Position observation ID is required" }
        require(observedAtMillis >= 0L) { "Observation time must be non-negative" }
        require(source.isNotBlank()) { "Position source is required" }
    }
}

data class PositionState(
    val availability: PositionAvailability = PositionAvailability.UNAVAILABLE,
    val observation: PositionObservation? = null,
)

enum class MapTool { BROWSE, PLACES, MEASURE, MANUAL_ROUTE, CHARTS }

data class MapSelection(val point: GeoPoint)
data class SavedPlace(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val revision: Long = 1L,
) {
    init {
        require(id.isNotBlank())
        require(revision > 0L)
    }
}
data class MeasurementDraft(val points: List<GeoPoint> = emptyList())
enum class RoutePurpose { MANUAL_PLANNING }

data class ManualRouteDraft(
    val id: String = "",
    val revision: Long = 0L,
    val name: String = "",
    val waypoints: List<GeoPoint> = emptyList(),
    val plannedSpeedKnots: Double = 5.0,
    val purpose: RoutePurpose = RoutePurpose.MANUAL_PLANNING,
    val undo: List<List<GeoPoint>> = emptyList(),
    val redo: List<List<GeoPoint>> = emptyList(),
) {
    init {
        require(revision >= 0L)
        require(id.isNotBlank() || revision == 0L) { "A persisted draft requires a stable ID" }
    }
}

data class SavedRoute(
    val id: String,
    val name: String,
    val waypoints: List<GeoPoint>,
    val plannedSpeedKnots: Double,
    val purpose: RoutePurpose = RoutePurpose.MANUAL_PLANNING,
    val revision: Long = 1L,
    val sourceDraftId: String? = null,
    val sourceDraftRevision: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(revision > 0L)
        require(sourceDraftRevision == null || sourceDraftRevision > 0L)
    }
}

data class RouteSummary(
    val distanceNauticalMiles: Double,
    val estimatedDurationMillis: Long,
)

@JvmInline
value class ChartPackageId(val value: String) {
    init {
        require(value.isNotBlank()) { "Chart package ID is required" }
        require(value.length <= 128 && value.matches(Regex("[A-Za-z0-9._-]+"))) {
            "Chart package ID must be a bounded filesystem-safe token"
        }
    }
}

enum class MapTileScheme { MBTILES_TMS }

data class ChartPackage(
    val id: ChartPackageId,
    val displayName: String,
    val source: String,
    val license: String,
    val attribution: String,
    val sha256: String,
    val localUri: String,
    val coverage: GeoBounds,
    val minZoom: Int,
    val maxZoom: Int,
    val version: String,
    val rasterFormat: String = "png",
    val tileSize: Int = 256,
    val tileScheme: MapTileScheme = MapTileScheme.MBTILES_TMS,
) {
    init {
        require(displayName.isNotBlank())
        require(source.isNotBlank())
        require(license.isNotBlank())
        require(attribution.isNotBlank())
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hex characters" }
        require(localUri.isNotBlank())
        require(minZoom in 0..24 && maxZoom in minZoom..24)
        require(version.isNotBlank())
        require(rasterFormat in setOf("png", "jpg", "jpeg", "webp"))
        require(tileSize in setOf(128, 256, 512, 1024))
    }
}

data class MapPersistedState(
    val camera: MapCamera,
    val places: List<SavedPlace>,
    val measurementDraft: MeasurementDraft?,
    val routeDraft: ManualRouteDraft?,
    val savedRoutes: List<SavedRoute>,
    val chartPackages: List<ChartPackage>,
    val activeChartPackageId: ChartPackageId? = null,
    val navigationActive: Boolean = false,
    val positionObservation: PositionObservation? = null,
)

enum class MapLibraryLoadState {
    NOT_LOADED,
    LOADING,
    READY_EMPTY,
    READY,
    READ_FAILED,
    CORRUPT,
}

enum class MapSaveState { SAVED, PENDING, FAILED }
enum class MapReadFailure { IO, CORRUPT, FUTURE_SCHEMA, UNKNOWN }

data class MapSessionSnapshot(
    val camera: MapCamera = MapCamera(),
    val measurementDraft: MeasurementDraft? = null,
    val activeRouteDraftId: String? = null,
    val activeChartPackageId: ChartPackageId? = null,
)

data class MapLibrarySnapshot(
    val revision: Long = 0L,
    val places: List<SavedPlace> = emptyList(),
    val routeDrafts: List<ManualRouteDraft> = emptyList(),
    val savedRoutes: List<SavedRoute> = emptyList(),
) {
    init {
        require(revision >= 0L)
        require(places.map { it.id }.distinct().size == places.size) { "Duplicate place ID" }
        require(routeDrafts.map { it.id }.distinct().size == routeDrafts.size) { "Duplicate draft ID" }
        require(savedRoutes.map { it.id }.distinct().size == savedRoutes.size) { "Duplicate route ID" }
    }

    val isEmpty: Boolean get() = places.isEmpty() && routeDrafts.isEmpty() && savedRoutes.isEmpty()
}

data class MapPersistenceAck(val revision: Long)

sealed interface MapLoadResult {
    data class Ready(
        val session: MapSessionSnapshot,
        val library: MapLibrarySnapshot,
        val quarantinedRecordCount: Int = 0,
    ) : MapLoadResult

    data class ReadFailed(val failure: MapReadFailure) : MapLoadResult
    data class Corrupt(val failure: MapReadFailure = MapReadFailure.CORRUPT) : MapLoadResult
}

fun interface MapIdGenerator {
    fun nextId(namespace: String): String
}

object RandomMapIdGenerator : MapIdGenerator {
    override fun nextId(namespace: String): String = "$namespace-${UUID.randomUUID()}"
}

data class MapState(
    val camera: MapCamera = MapCamera(),
    val tool: MapTool = MapTool.BROWSE,
    val selection: MapSelection? = null,
    val places: List<SavedPlace> = emptyList(),
    val measurementDraft: MeasurementDraft? = null,
    val routeDrafts: List<ManualRouteDraft> = emptyList(),
    val activeRouteDraftId: String? = null,
    val savedRoutes: List<SavedRoute> = emptyList(),
    val chartPackages: List<ChartPackage> = emptyList(),
    val activeChartPackageId: ChartPackageId? = null,
    val position: PositionState = PositionState(),
    val navigationActive: Boolean = false,
    val libraryLoadState: MapLibraryLoadState = MapLibraryLoadState.READY_EMPTY,
    val libraryRevision: Long = 0L,
    val durableLibraryRevision: Long = 0L,
    val saveState: MapSaveState = MapSaveState.SAVED,
    val persistenceFailure: MapReadFailure? = null,
    val renderer: MapRendererState = MapRendererState(),
) {
    val routeDraft: ManualRouteDraft?
        get() = activeRouteDraftId?.let { active -> routeDrafts.firstOrNull { it.id == active } }

    val routeSummary: RouteSummary?
        get() = routeDraft?.takeIf { it.waypoints.size >= 2 && it.plannedSpeedKnots > 0.0 }?.let { draft ->
            val distance = draft.waypoints.zipWithNext().sumOf { (from, to) ->
                greatCircleNauticalMiles(from, to)
            }
            RouteSummary(
                distanceNauticalMiles = distance,
                estimatedDurationMillis = ((distance / draft.plannedSpeedKnots) * 3_600_000.0).toLong(),
            )
        }

    fun persisted(): MapPersistedState = MapPersistedState(
        camera = camera,
        places = places,
        measurementDraft = measurementDraft,
        routeDraft = routeDraft,
        savedRoutes = savedRoutes,
        chartPackages = chartPackages,
        activeChartPackageId = activeChartPackageId,
        navigationActive = false,
        positionObservation = null,
    )

    fun sessionSnapshot(): MapSessionSnapshot = MapSessionSnapshot(
        camera = camera,
        measurementDraft = measurementDraft,
        activeRouteDraftId = activeRouteDraftId,
        activeChartPackageId = activeChartPackageId,
    )

    fun librarySnapshot(): MapLibrarySnapshot = MapLibrarySnapshot(
        revision = libraryRevision,
        places = places,
        routeDrafts = routeDrafts.map { it.copy(undo = emptyList(), redo = emptyList()) },
        savedRoutes = savedRoutes,
    )
}

internal fun greatCircleNauticalMiles(from: GeoPoint, to: GeoPoint): Double {
    val radiusNm = 3_440.065
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val deltaLat = lat2 - lat1
    val deltaLon = Math.toRadians(to.longitude - from.longitude)
    val a = sin(deltaLat / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2).let { it * it }
    return radiusNm * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
}

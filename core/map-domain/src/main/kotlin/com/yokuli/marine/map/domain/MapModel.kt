package com.yokuli.marine.map.domain

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
        GeoPoint(south, west)
        GeoPoint(north, east)
    }

    val crossesAntimeridian: Boolean get() = west > east
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

data class MapSelection(val point: GeoPoint)

enum class PlaceCategory(val wireValue: String, val searchAliases: Set<String>) {
    ANCHORAGE("anchorage", setOf("锚地", "泊地")),
    MARINA("marina", setOf("码头", "游艇港")),
    LANDING("landing", setOf("上岸", "上岸点", "登陆点")),
    WATER("water", setOf("补水", "取水")),
    PERSONAL_MARKER("personal", setOf("个人标记", "标记")),
    ;

    companion object {
        fun fromWireValue(value: String): PlaceCategory? = entries.firstOrNull { it.wireValue == value }
    }
}

data class SavedPlace(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val revision: Long = 1L,
    val notes: String = "",
    val category: PlaceCategory = PlaceCategory.PERSONAL_MARKER,
    val tags: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = createdAtMillis,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(revision > 0L)
        require(createdAtMillis >= 0L)
        require(updatedAtMillis >= createdAtMillis)
        require(tags.none { it.isBlank() })
        require(tags.distinct().size == tags.size)
    }
}

fun interface MapClock {
    fun nowMillis(): Long
}

object SystemMapClock : MapClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

data class PlaceSaveStatus(
    val placeId: String,
    val revision: Long,
    val state: MapSaveState,
) {
    init {
        require(placeId.isNotBlank())
        require(revision > 0L)
    }
}

data class PlaceMoveDraft(
    val placeId: String,
    val expectedRevision: Long,
    val originalPoint: GeoPoint,
    val candidatePoint: GeoPoint = originalPoint,
)

data class PlaceDeleteRequest(
    val placeId: String,
    val expectedRevision: Long,
    val name: String,
    val referencingRouteCount: Int,
)

data class PlaceDeleteUndo(
    val place: SavedPlace,
    val compatibleLibraryRevision: Long,
)
data class MeasurementDraft(
    val points: List<GeoPoint> = emptyList(),
    val undo: List<List<GeoPoint>> = emptyList(),
    val redo: List<List<GeoPoint>> = emptyList(),
)

enum class MeasurementPrompt { PLACE_START, PLACE_END, RESULTS }

data class MeasurementSegment(
    val fromIndex: Int,
    val toIndex: Int,
    val distanceMeters: Double,
    val initialBearingTrueDegrees: Double?,
    val azimuthAmbiguous: Boolean,
)

data class MeasurementSummary(
    val prompt: MeasurementPrompt,
    val segments: List<MeasurementSegment>,
    val totalDistanceMeters: Double,
)

object MeasurementMath {
    fun summarize(draft: MeasurementDraft): MeasurementSummary {
        val segments = draft.points.zipWithNext().mapIndexed { index, (from, to) ->
            val inverse = Wgs84Geodesic.inverse(from, to)
            MeasurementSegment(
                fromIndex = index,
                toIndex = index + 1,
                distanceMeters = inverse.distanceMeters,
                initialBearingTrueDegrees = inverse.initialBearingTrueDegrees,
                azimuthAmbiguous = inverse.azimuthAmbiguous,
            )
        }
        return MeasurementSummary(
            prompt = when (draft.points.size) {
                0 -> MeasurementPrompt.PLACE_START
                1 -> MeasurementPrompt.PLACE_END
                else -> MeasurementPrompt.RESULTS
            },
            segments = segments,
            totalDistanceMeters = segments.sumOf { it.distanceMeters },
        )
    }
}
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
    val waypointPlaceReferences: Map<Int, PlaceRevisionReference> = emptyMap(),
) {
    init {
        require(id.isNotBlank())
        require(revision > 0L)
        require(sourceDraftRevision == null || sourceDraftRevision > 0L)
        require(waypointPlaceReferences.keys.all { it in waypoints.indices })
    }

    fun placeSourceState(index: Int, places: List<SavedPlace>): RoutePlaceSourceState {
        val reference = waypointPlaceReferences[index] ?: return RoutePlaceSourceState.NONE
        val place = places.firstOrNull { it.id == reference.placeId } ?: return RoutePlaceSourceState.MISSING
        return if (place.revision == reference.revision && place.point == waypoints[index]) {
            RoutePlaceSourceState.CURRENT
        } else {
            RoutePlaceSourceState.CHANGED
        }
    }
}

data class PlaceRevisionReference(val placeId: String, val revision: Long) {
    init {
        require(placeId.isNotBlank())
        require(revision > 0L)
    }
}

enum class RoutePlaceSourceState { NONE, CURRENT, CHANGED, MISSING }

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
    val surface: MapSurface = MapSurface.Root,
    val surfaceHistory: List<MapSurface> = emptyList(),
    val tool: MapTool = MapTool.BROWSE,
    val transient: MapTransient? = null,
    val selection: MapSelection? = null,
    val editGesture: MapEditGesture? = null,
    val precisePointEdit: MapPrecisePointEdit? = null,
    val viewport: MapViewport? = null,
    val crosshairEnabled: Boolean = false,
    val places: List<SavedPlace> = emptyList(),
    val placeMove: PlaceMoveDraft? = null,
    val placeDeleteRequest: PlaceDeleteRequest? = null,
    val placeDeleteUndo: PlaceDeleteUndo? = null,
    val placeSaveStatus: PlaceSaveStatus? = null,
    val placeQuery: String = "",
    val placeSort: PlaceSort = PlaceSort.NAME,
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
                Wgs84Geodesic.inverse(from, to).distanceMeters / METERS_PER_NAUTICAL_MILE
            }
            RouteSummary(
                distanceNauticalMiles = distance,
                estimatedDurationMillis = ((distance / draft.plannedSpeedKnots) * 3_600_000.0).toLong(),
            )
        }

    fun persisted(): MapPersistedState = MapPersistedState(
        camera = camera,
        places = places,
        measurementDraft = measurementDraft?.copy(undo = emptyList(), redo = emptyList()),
        routeDraft = routeDraft,
        savedRoutes = savedRoutes,
        chartPackages = chartPackages,
        activeChartPackageId = activeChartPackageId,
        navigationActive = false,
        positionObservation = null,
    )

    fun sessionSnapshot(): MapSessionSnapshot = MapSessionSnapshot(
        camera = camera,
        measurementDraft = measurementDraft?.copy(undo = emptyList(), redo = emptyList()),
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

const val METERS_PER_NAUTICAL_MILE = 1_852.0

package com.yokuli.marine.map.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
data class SavedPlace(val id: String, val name: String, val point: GeoPoint)
data class MeasurementDraft(val points: List<GeoPoint> = emptyList())
enum class RoutePurpose { MANUAL_PLANNING }

data class ManualRouteDraft(
    val name: String = "",
    val waypoints: List<GeoPoint> = emptyList(),
    val plannedSpeedKnots: Double = 5.0,
    val purpose: RoutePurpose = RoutePurpose.MANUAL_PLANNING,
    val undo: List<List<GeoPoint>> = emptyList(),
    val redo: List<List<GeoPoint>> = emptyList(),
)

data class SavedRoute(
    val id: String,
    val name: String,
    val waypoints: List<GeoPoint>,
    val plannedSpeedKnots: Double,
    val purpose: RoutePurpose = RoutePurpose.MANUAL_PLANNING,
)

data class RouteSummary(
    val distanceNauticalMiles: Double,
    val estimatedDurationMillis: Long,
)

@JvmInline
value class ChartPackageId(val value: String) {
    init { require(value.isNotBlank()) { "Chart package ID is required" } }
}

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
    }
}

data class MapPersistedState(
    val camera: MapCamera,
    val places: List<SavedPlace>,
    val measurementDraft: MeasurementDraft?,
    val routeDraft: ManualRouteDraft?,
    val savedRoutes: List<SavedRoute>,
    val chartPackages: List<ChartPackage>,
    val navigationActive: Boolean = false,
    val positionObservation: PositionObservation? = null,
)

data class MapState(
    val camera: MapCamera = MapCamera(),
    val tool: MapTool = MapTool.BROWSE,
    val selection: MapSelection? = null,
    val places: List<SavedPlace> = emptyList(),
    val measurementDraft: MeasurementDraft? = null,
    val routeDraft: ManualRouteDraft? = null,
    val savedRoutes: List<SavedRoute> = emptyList(),
    val chartPackages: List<ChartPackage> = emptyList(),
    val position: PositionState = PositionState(),
    val navigationActive: Boolean = false,
) {
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
        navigationActive = false,
        positionObservation = null,
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

package com.yokuli.marine.navigation.domain

data class NavigationPosition(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

enum class WaypointCategory { ANCHORAGE, MARINA, LANDING, WATER, PERSONAL_MARKER }

data class Waypoint(
    val id: String,
    val revision: Long,
    val name: String,
    val position: NavigationPosition,
    val notes: String = "",
    val category: WaypointCategory = WaypointCategory.PERSONAL_MARKER,
    val tags: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = createdAtMillis,
) {
    init {
        require(id.isNotBlank() && revision > 0L && name.isNotBlank())
        require(createdAtMillis >= 0L && updatedAtMillis >= createdAtMillis)
        require(tags.none(String::isBlank) && tags.distinct().size == tags.size)
    }
}

data class WaypointRevisionReference(val waypointId: String, val revision: Long) {
    init { require(waypointId.isNotBlank() && revision > 0L) }
}

data class RoutePoint(
    val id: String,
    val position: NavigationPosition,
    val sourceWaypoint: WaypointRevisionReference? = null,
) {
    init { require(id.isNotBlank()) }
}

data class RouteDraft(
    val id: String,
    val revision: Long,
    val name: String,
    val points: List<RoutePoint>,
    val plannedSpeedKnots: Double? = null,
    val notes: String = "",
    val baseRouteId: String? = null,
    val baseRouteRevision: Long? = null,
    val nextPointOrdinal: Int = points.size + 1,
) {
    init {
        require(id.isNotBlank() && revision > 0L)
        require(points.map(RoutePoint::id).distinct().size == points.size)
        require(plannedSpeedKnots == null || plannedSpeedKnots.isFinite() && plannedSpeedKnots > 0.0)
        require((baseRouteId == null) == (baseRouteRevision == null))
        require(baseRouteRevision == null || baseRouteRevision > 0L)
        require(nextPointOrdinal > points.size)
    }
}

data class RoutePlan(
    val id: String,
    val revision: Long,
    val name: String,
    val points: List<RoutePoint>,
    val plannedSpeedKnots: Double? = null,
    val notes: String = "",
    val sourceDraftId: String? = null,
    val sourceDraftRevision: Long? = null,
) {
    init {
        require(id.isNotBlank() && revision > 0L && name.isNotBlank())
        require(points.map(RoutePoint::id).distinct().size == points.size)
        require(plannedSpeedKnots == null || plannedSpeedKnots.isFinite() && plannedSpeedKnots > 0.0)
        require((sourceDraftId == null) == (sourceDraftRevision == null))
        require(sourceDraftRevision == null || sourceDraftRevision > 0L)
    }
}

data class NavigationTrackPoint(
    val position: NavigationPosition,
    val elevationMeters: Double? = null,
    val time: String? = null,
) {
    init { require(elevationMeters == null || elevationMeters.isFinite()) }
}

data class NavigationTrackSegment(val points: List<NavigationTrackPoint>) {
    init { require(points.isNotEmpty()) }
}

data class NavigationTrack(
    val id: String,
    val revision: Long,
    val name: String,
    val description: String = "",
    val segments: List<NavigationTrackSegment>,
    val sourceDigest: String,
    val importedAtMillis: Long,
) {
    init {
        require(id.isNotBlank() && revision > 0L && name.isNotBlank())
        require(segments.isNotEmpty())
        require(sourceDigest.matches(Regex("[0-9a-f]{64}")))
        require(importedAtMillis >= 0L)
    }
}

data class GpxImportReceipt(val id: String, val sha256: String, val importedAtMillis: Long) {
    init {
        require(id.isNotBlank())
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(importedAtMillis >= 0L)
    }
}

data class NavigationLibrary(
    val revision: Long = 0L,
    val waypoints: List<Waypoint> = emptyList(),
    val routeDrafts: List<RouteDraft> = emptyList(),
    val routePlans: List<RoutePlan> = emptyList(),
    val importedTracks: List<NavigationTrack> = emptyList(),
    val gpxImports: List<GpxImportReceipt> = emptyList(),
) {
    init {
        require(revision >= 0L)
        require(waypoints.map(Waypoint::id).distinct().size == waypoints.size)
        require(routeDrafts.map(RouteDraft::id).distinct().size == routeDrafts.size)
        require(routePlans.map(RoutePlan::id).distinct().size == routePlans.size)
        require(importedTracks.map(NavigationTrack::id).distinct().size == importedTracks.size)
        require(gpxImports.map(GpxImportReceipt::id).distinct().size == gpxImports.size)
    }
}

data class OfflineCoveragePlan(
    val routeId: String,
    val routeRevision: Long,
    val targetZoom: Int,
    val corridorHalfWidthNauticalMiles: Double,
) {
    init {
        require(routeId.isNotBlank() && routeRevision > 0L)
        require(targetZoom in 0..24)
        require(corridorHalfWidthNauticalMiles.isFinite() && corridorHalfWidthNauticalMiles > 0.0)
    }
}

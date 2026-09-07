package com.yokuli.marine.map.storage

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.GpxImportRecord
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.ImportedTrackPoint
import com.yokuli.marine.map.domain.ImportedTrackSegment
import com.yokuli.marine.map.domain.TrackOrigin
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapLibrarySnapshot
import com.yokuli.marine.map.domain.PlaceCategory
import com.yokuli.marine.map.domain.PlaceRevisionReference
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import com.yokuli.marine.navigation.domain.GpxImportReceipt
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.NavigationTrack
import com.yokuli.marine.navigation.domain.NavigationTrackPoint
import com.yokuli.marine.navigation.domain.NavigationTrackSegment
import com.yokuli.marine.navigation.domain.NavigationTrackOrigin
import com.yokuli.marine.navigation.domain.RouteDraft
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.Waypoint
import com.yokuli.marine.navigation.domain.WaypointCategory
import com.yokuli.marine.navigation.domain.WaypointRevisionReference

/** Transitional schema bridge: ownership changes without rewriting or copying the Room library. */
internal object NavigationLegacyMapper {
    fun toNavigation(source: MapLibrarySnapshot): NavigationLibrary = NavigationLibrary(
        revision = source.revision,
        waypoints = source.places.map { it.asNavigation() },
        routeDrafts = source.routeDrafts.map { it.asNavigation() },
        routePlans = source.savedRoutes.map { it.asNavigation() },
        importedTracks = source.importedTracks.map { it.asNavigation() },
        gpxImports = source.gpxImportRecords.map { it.asNavigation() },
    )

    fun toLegacy(source: NavigationLibrary): MapLibrarySnapshot = MapLibrarySnapshot(
        revision = source.revision,
        places = source.waypoints.map { it.asLegacy() },
        routeDrafts = source.routeDrafts.map { it.asLegacy() },
        savedRoutes = source.routePlans.map { it.asLegacy() },
        importedTracks = source.importedTracks.map { it.asLegacy() },
        gpxImportRecords = source.gpxImports.map { it.asLegacy() },
    )

    private fun SavedPlace.asNavigation() = Waypoint(
        id = id,
        revision = revision,
        name = name,
        position = point.asNavigation(),
        notes = notes,
        category = WaypointCategory.valueOf(category.name),
        tags = tags,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun Waypoint.asLegacy() = SavedPlace(
        id = id,
        revision = revision,
        name = name,
        point = position.asLegacy(),
        notes = notes,
        category = PlaceCategory.valueOf(category.name),
        tags = tags,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun ManualRouteDraft.asNavigation() = RouteDraft(
        id = id,
        revision = revision,
        name = name,
        points = waypoints.mapIndexed { index, point ->
            RoutePoint(
                id = waypointIds[index],
                position = point.asNavigation(),
                sourceWaypoint = waypointPlaceReferences[index]?.asNavigation(),
            )
        },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        baseRouteId = basePlanId,
        baseRouteRevision = basePlanRevision,
        nextPointOrdinal = nextWaypointOrdinal,
    )

    private fun RouteDraft.asLegacy() = ManualRouteDraft(
        id = id,
        revision = revision,
        name = name,
        waypoints = points.map { it.position.asLegacy() },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        waypointIds = points.map(RoutePoint::id),
        waypointPlaceReferences = points.mapIndexedNotNull { index, point ->
            point.sourceWaypoint?.let { index to it.asLegacy() }
        }.toMap(),
        basePlanId = baseRouteId,
        basePlanRevision = baseRouteRevision,
        nextWaypointOrdinal = nextPointOrdinal,
    )

    private fun SavedRoute.asNavigation() = RoutePlan(
        id = id,
        revision = revision,
        name = name,
        points = waypoints.mapIndexed { index, point ->
            RoutePoint(
                id = waypointIds[index],
                position = point.asNavigation(),
                sourceWaypoint = waypointPlaceReferences[index]?.asNavigation(),
            )
        },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        sourceDraftId = sourceDraftId,
        sourceDraftRevision = sourceDraftRevision,
    )

    private fun RoutePlan.asLegacy() = SavedRoute(
        id = id,
        revision = revision,
        name = name,
        waypoints = points.map { it.position.asLegacy() },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        sourceDraftId = sourceDraftId,
        sourceDraftRevision = sourceDraftRevision,
        waypointIds = points.map(RoutePoint::id),
        waypointPlaceReferences = points.mapIndexedNotNull { index, point ->
            point.sourceWaypoint?.let { index to it.asLegacy() }
        }.toMap(),
    )

    private fun ImportedTrack.asNavigation() = NavigationTrack(
        id = id,
        revision = revision,
        name = name,
        description = description,
        segments = segments.map { segment -> NavigationTrackSegment(segment.points.map { it.asNavigation() }) },
        sourceDigest = sourceDigest,
        importedAtMillis = importedAtMillis,
        origin = NavigationTrackOrigin.valueOf(origin.name),
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        durationMillis = durationMillis,
        distanceNauticalMiles = distanceNauticalMiles,
        navigationSessionId = navigationSessionId,
        routeId = routeId,
        routeRevision = routeRevision,
    )

    private fun NavigationTrack.asLegacy() = ImportedTrack(
        id = id,
        revision = revision,
        name = name,
        description = description,
        segments = segments.map { segment -> ImportedTrackSegment(segment.points.map { it.asLegacy() }) },
        sourceDigest = sourceDigest,
        importedAtMillis = importedAtMillis,
        origin = TrackOrigin.valueOf(origin.name),
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        durationMillis = durationMillis,
        distanceNauticalMiles = distanceNauticalMiles,
        navigationSessionId = navigationSessionId,
        routeId = routeId,
        routeRevision = routeRevision,
    )

    private fun ImportedTrackPoint.asNavigation() = NavigationTrackPoint(
        position = point.asNavigation(),
        elevationMeters = elevationMeters,
        time = time,
        recordedAtEpochMillis = recordedAtEpochMillis,
        sourceId = sourceId,
        speedOverGroundKnots = speedOverGroundKnots,
        courseOverGroundTrueDegrees = courseOverGroundTrueDegrees,
    )

    private fun NavigationTrackPoint.asLegacy() = ImportedTrackPoint(
        point = position.asLegacy(),
        elevationMeters = elevationMeters,
        time = time,
        recordedAtEpochMillis = recordedAtEpochMillis,
        sourceId = sourceId,
        speedOverGroundKnots = speedOverGroundKnots,
        courseOverGroundTrueDegrees = courseOverGroundTrueDegrees,
    )

    private fun GpxImportRecord.asNavigation() = GpxImportReceipt(id, sha256, importedAtMillis)
    private fun GpxImportReceipt.asLegacy() = GpxImportRecord(id, sha256, importedAtMillis)
    private fun PlaceRevisionReference.asNavigation() = WaypointRevisionReference(placeId, revision)
    private fun WaypointRevisionReference.asLegacy() = PlaceRevisionReference(waypointId, revision)
    private fun GeoPoint.asNavigation() = NavigationPosition(latitude, longitude)
    private fun NavigationPosition.asLegacy() = GeoPoint(latitude, longitude)
}

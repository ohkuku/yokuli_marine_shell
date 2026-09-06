package com.yokuli.marine.map.storage

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.GpxImportRecord
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.ImportedTrackPoint
import com.yokuli.marine.map.domain.ImportedTrackSegment
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
        waypoints = source.places.map(SavedPlace::toNavigation),
        routeDrafts = source.routeDrafts.map(ManualRouteDraft::toNavigation),
        routePlans = source.savedRoutes.map(SavedRoute::toNavigation),
        importedTracks = source.importedTracks.map(ImportedTrack::toNavigation),
        gpxImports = source.gpxImportRecords.map(GpxImportRecord::toNavigation),
    )

    fun toLegacy(source: NavigationLibrary): MapLibrarySnapshot = MapLibrarySnapshot(
        revision = source.revision,
        places = source.waypoints.map(Waypoint::toLegacy),
        routeDrafts = source.routeDrafts.map(RouteDraft::toLegacy),
        savedRoutes = source.routePlans.map(RoutePlan::toLegacy),
        importedTracks = source.importedTracks.map(NavigationTrack::toLegacy),
        gpxImportRecords = source.gpxImports.map(GpxImportReceipt::toLegacy),
    )

    private fun SavedPlace.toNavigation() = Waypoint(
        id = id,
        revision = revision,
        name = name,
        position = point.toNavigation(),
        notes = notes,
        category = WaypointCategory.valueOf(category.name),
        tags = tags,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun Waypoint.toLegacy() = SavedPlace(
        id = id,
        revision = revision,
        name = name,
        point = position.toLegacy(),
        notes = notes,
        category = PlaceCategory.valueOf(category.name),
        tags = tags,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun ManualRouteDraft.toNavigation() = RouteDraft(
        id = id,
        revision = revision,
        name = name,
        points = waypoints.mapIndexed { index, point ->
            RoutePoint(
                id = waypointIds[index],
                position = point.toNavigation(),
                sourceWaypoint = waypointPlaceReferences[index]?.toNavigation(),
            )
        },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        baseRouteId = basePlanId,
        baseRouteRevision = basePlanRevision,
        nextPointOrdinal = nextWaypointOrdinal,
    )

    private fun RouteDraft.toLegacy() = ManualRouteDraft(
        id = id,
        revision = revision,
        name = name,
        waypoints = points.map { it.position.toLegacy() },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        waypointIds = points.map(RoutePoint::id),
        waypointPlaceReferences = points.mapIndexedNotNull { index, point ->
            point.sourceWaypoint?.let { index to it.toLegacy() }
        }.toMap(),
        basePlanId = baseRouteId,
        basePlanRevision = baseRouteRevision,
        nextWaypointOrdinal = nextPointOrdinal,
    )

    private fun SavedRoute.toNavigation() = RoutePlan(
        id = id,
        revision = revision,
        name = name,
        points = waypoints.mapIndexed { index, point ->
            RoutePoint(
                id = waypointIds[index],
                position = point.toNavigation(),
                sourceWaypoint = waypointPlaceReferences[index]?.toNavigation(),
            )
        },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        sourceDraftId = sourceDraftId,
        sourceDraftRevision = sourceDraftRevision,
    )

    private fun RoutePlan.toLegacy() = SavedRoute(
        id = id,
        revision = revision,
        name = name,
        waypoints = points.map { it.position.toLegacy() },
        plannedSpeedKnots = plannedSpeedKnots,
        notes = notes,
        sourceDraftId = sourceDraftId,
        sourceDraftRevision = sourceDraftRevision,
        waypointIds = points.map(RoutePoint::id),
        waypointPlaceReferences = points.mapIndexedNotNull { index, point ->
            point.sourceWaypoint?.let { index to it.toLegacy() }
        }.toMap(),
    )

    private fun ImportedTrack.toNavigation() = NavigationTrack(
        id = id,
        revision = revision,
        name = name,
        description = description,
        segments = segments.map { segment -> NavigationTrackSegment(segment.points.map(ImportedTrackPoint::toNavigation)) },
        sourceDigest = sourceDigest,
        importedAtMillis = importedAtMillis,
    )

    private fun NavigationTrack.toLegacy() = ImportedTrack(
        id = id,
        revision = revision,
        name = name,
        description = description,
        segments = segments.map { segment -> ImportedTrackSegment(segment.points.map(NavigationTrackPoint::toLegacy)) },
        sourceDigest = sourceDigest,
        importedAtMillis = importedAtMillis,
    )

    private fun ImportedTrackPoint.toNavigation() = NavigationTrackPoint(
        position = point.toNavigation(),
        elevationMeters = elevationMeters,
        time = time,
    )

    private fun NavigationTrackPoint.toLegacy() = ImportedTrackPoint(
        point = position.toLegacy(),
        elevationMeters = elevationMeters,
        time = time,
    )

    private fun GpxImportRecord.toNavigation() = GpxImportReceipt(id, sha256, importedAtMillis)
    private fun GpxImportReceipt.toLegacy() = GpxImportRecord(id, sha256, importedAtMillis)
    private fun PlaceRevisionReference.toNavigation() = WaypointRevisionReference(placeId, revision)
    private fun WaypointRevisionReference.toLegacy() = PlaceRevisionReference(waypointId, revision)
    private fun GeoPoint.toNavigation() = NavigationPosition(latitude, longitude)
    private fun NavigationPosition.toLegacy() = GeoPoint(latitude, longitude)
}

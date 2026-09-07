package com.yokuli.marine.map.storage

import com.yokuli.marine.map.storage.proto.ActiveNavigationSessionProto
import com.yokuli.marine.map.storage.proto.ActiveNavigationEmbeddedRouteProto
import com.yokuli.marine.map.storage.proto.ActiveNavigationRoutePointProto
import com.yokuli.marine.navigation.domain.ActiveNavigationSession
import com.yokuli.marine.navigation.domain.NavigationAdvancePolicy
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.WaypointRevisionReference

internal object ActiveNavigationSessionProtoMapper {
    const val SCHEMA_VERSION = 4

    fun encode(session: ActiveNavigationSession?): ActiveNavigationSessionProto =
        ActiveNavigationSessionProto.newBuilder()
            .setSchemaVersion(SCHEMA_VERSION)
            .setHasSession(session != null)
            .apply {
                session?.let {
                    routeId = it.routeId
                    routeRevision = it.routeRevision
                    startedAtEpochMillis = it.startedAtEpochMillis
                    activeLegIndex = it.activeLegIndex
                    arrivalRadiusMeters = it.arrivalRadiusMeters
                    advancePolicy = it.advancePolicy.name
                    state = it.state.name
                    sessionId = it.sessionId
                    hasCompletedAt = it.completedAtEpochMillis != null
                    completedAtEpochMillis = it.completedAtEpochMillis ?: 0L
                    it.embeddedRoute?.let { embedded ->
                        hasEmbeddedRoute = true
                        embeddedRoute = encodeEmbeddedRoute(embedded)
                    }
                }
            }
            .build()

    fun decode(proto: ActiveNavigationSessionProto): ActiveNavigationSession? {
        require(proto.schemaVersion in 0..SCHEMA_VERSION) { "Unsupported active navigation schema ${proto.schemaVersion}" }
        if (!proto.hasSession) return null
        return ActiveNavigationSession(
            routeId = proto.routeId,
            routeRevision = proto.routeRevision,
            startedAtEpochMillis = proto.startedAtEpochMillis,
            activeLegIndex = proto.activeLegIndex,
            arrivalRadiusMeters = proto.arrivalRadiusMeters,
            advancePolicy = enumValueOf<NavigationAdvancePolicy>(proto.advancePolicy),
            state = enumValueOf<NavigationSessionState>(proto.state),
            embeddedRoute = if (proto.hasEmbeddedRoute) decodeEmbeddedRoute(proto.embeddedRoute) else null,
            sessionId = proto.sessionId.takeIf(String::isNotBlank)
                ?: "${proto.routeId}:${proto.routeRevision}:${proto.startedAtEpochMillis}",
            completedAtEpochMillis = proto.completedAtEpochMillis.takeIf { proto.hasCompletedAt },
        )
    }

    private fun encodeEmbeddedRoute(route: RoutePlan): ActiveNavigationEmbeddedRouteProto =
        ActiveNavigationEmbeddedRouteProto.newBuilder()
            .setId(route.id)
            .setRevision(route.revision)
            .setName(route.name)
            .addAllPoints(route.points.map(::encodePoint))
            .setHasPlannedSpeedKnots(route.plannedSpeedKnots != null)
            .setPlannedSpeedKnots(route.plannedSpeedKnots ?: 0.0)
            .setNotes(route.notes)
            .setSourceDraftId(route.sourceDraftId.orEmpty())
            .setSourceDraftRevision(route.sourceDraftRevision ?: 0L)
            .build()

    private fun encodePoint(point: RoutePoint): ActiveNavigationRoutePointProto =
        ActiveNavigationRoutePointProto.newBuilder()
            .setId(point.id)
            .setLatitude(point.position.latitude)
            .setLongitude(point.position.longitude)
            .setSourceWaypointId(point.sourceWaypoint?.waypointId.orEmpty())
            .setSourceWaypointRevision(point.sourceWaypoint?.revision ?: 0L)
            .build()

    private fun decodeEmbeddedRoute(route: ActiveNavigationEmbeddedRouteProto): RoutePlan = RoutePlan(
        id = route.id,
        revision = route.revision,
        name = route.name,
        points = route.pointsList.map { point ->
            RoutePoint(
                id = point.id,
                position = NavigationPosition(point.latitude, point.longitude),
                sourceWaypoint = point.sourceWaypointId.takeIf(String::isNotBlank)?.let { id ->
                    WaypointRevisionReference(id, point.sourceWaypointRevision)
                },
            )
        },
        plannedSpeedKnots = route.plannedSpeedKnots.takeIf { route.hasPlannedSpeedKnots },
        notes = route.notes,
        sourceDraftId = route.sourceDraftId.takeIf(String::isNotBlank),
        sourceDraftRevision = route.sourceDraftRevision.takeIf { route.sourceDraftId.isNotBlank() },
    )
}

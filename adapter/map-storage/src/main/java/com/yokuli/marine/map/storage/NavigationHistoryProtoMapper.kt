package com.yokuli.marine.map.storage

import com.yokuli.marine.map.storage.proto.NavigationHistoryStateProto
import com.yokuli.marine.map.storage.proto.NavigationPassageProto
import com.yokuli.marine.map.storage.proto.NavigationPassageWaypointProto
import com.yokuli.marine.navigation.domain.NavigationAdvanceReason
import com.yokuli.marine.navigation.domain.NavigationHistorySnapshot
import com.yokuli.marine.navigation.domain.NavigationPassage
import com.yokuli.marine.navigation.domain.NavigationPassageEvidence
import com.yokuli.marine.navigation.domain.NavigationPassageOutcome
import com.yokuli.marine.navigation.domain.NavigationPassageWaypoint
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.NavigationWaypointPassageEvent

internal object NavigationHistoryProtoMapper {
    const val SCHEMA_VERSION = 1
    private const val MAX_PASSAGES = 1_000
    private const val MAX_WAYPOINTS_PER_PASSAGE = 2_000
    private const val MAX_TOTAL_WAYPOINTS = 200_000

    fun encode(snapshot: NavigationHistorySnapshot): NavigationHistoryStateProto {
        require(snapshot.passages.size <= MAX_PASSAGES) { "Navigation history exceeds passage capacity" }
        require(snapshot.passages.all { it.waypoints.size <= MAX_WAYPOINTS_PER_PASSAGE }) {
            "Navigation passage exceeds waypoint capacity"
        }
        require(snapshot.passages.sumOf { it.waypoints.size } <= MAX_TOTAL_WAYPOINTS) {
            "Navigation history exceeds waypoint capacity"
        }
        return NavigationHistoryStateProto.newBuilder()
            .setSchemaVersion(SCHEMA_VERSION)
            .setRevision(snapshot.revision)
            .addAllPassages(snapshot.passages.map(::encodePassage))
            .build()
    }

    fun decode(proto: NavigationHistoryStateProto): NavigationHistorySnapshot {
        require(proto.schemaVersion in 0..SCHEMA_VERSION) { "Unsupported navigation history schema ${proto.schemaVersion}" }
        require(proto.passagesCount <= MAX_PASSAGES) { "Navigation history exceeds passage capacity" }
        require(proto.passagesList.all { it.waypointsCount <= MAX_WAYPOINTS_PER_PASSAGE }) {
            "Navigation passage exceeds waypoint capacity"
        }
        require(proto.passagesList.sumOf { it.waypointsCount } <= MAX_TOTAL_WAYPOINTS) {
            "Navigation history exceeds waypoint capacity"
        }
        return NavigationHistorySnapshot(
            revision = proto.revision,
            passages = proto.passagesList.map(::decodePassage),
        )
    }

    private fun encodePassage(passage: NavigationPassage): NavigationPassageProto =
        NavigationPassageProto.newBuilder()
            .setSessionId(passage.sessionId)
            .setRevision(passage.revision)
            .setRouteId(passage.routeId)
            .setRouteRevision(passage.routeRevision)
            .setRouteName(passage.routeName)
            .setStartedAtEpochMillis(passage.startedAtEpochMillis)
            .setHasEndedAt(passage.endedAtEpochMillis != null)
            .setEndedAtEpochMillis(passage.endedAtEpochMillis ?: 0L)
            .setOutcome(passage.outcome.name)
            .addAllWaypoints(passage.waypoints.map(::encodeWaypoint))
            .setFurthestAdvancedWaypointIndex(passage.furthestAdvancedWaypointIndex)
            .build()

    private fun decodePassage(proto: NavigationPassageProto): NavigationPassage = NavigationPassage(
        sessionId = proto.sessionId,
        revision = proto.revision,
        routeId = proto.routeId,
        routeRevision = proto.routeRevision,
        routeName = proto.routeName,
        startedAtEpochMillis = proto.startedAtEpochMillis,
        endedAtEpochMillis = proto.endedAtEpochMillis.takeIf { proto.hasEndedAt },
        outcome = enumValueOf<NavigationPassageOutcome>(proto.outcome),
        waypoints = proto.waypointsList.map(::decodeWaypoint),
        furthestAdvancedWaypointIndex = proto.furthestAdvancedWaypointIndex,
    )

    private fun encodeWaypoint(waypoint: NavigationPassageWaypoint): NavigationPassageWaypointProto =
        NavigationPassageWaypointProto.newBuilder()
            .setOrdinal(waypoint.ordinal)
            .setRoutePointId(waypoint.routePointId)
            .setPlannedLatitude(waypoint.plannedPosition.latitude)
            .setPlannedLongitude(waypoint.plannedPosition.longitude)
            .apply {
                waypoint.event?.let { event ->
                    hasEvent = true
                    eventAtEpochMillis = event.occurredAtEpochMillis
                    advanceReason = event.reason.name
                    evidence = event.evidence.name
                    hasActualPosition = event.actualPosition != null
                    actualLatitude = event.actualPosition?.latitude ?: 0.0
                    actualLongitude = event.actualPosition?.longitude ?: 0.0
                    positionSourceId = event.positionSourceId.orEmpty()
                }
            }
            .build()

    private fun decodeWaypoint(proto: NavigationPassageWaypointProto): NavigationPassageWaypoint =
        NavigationPassageWaypoint(
            ordinal = proto.ordinal,
            routePointId = proto.routePointId,
            plannedPosition = NavigationPosition(proto.plannedLatitude, proto.plannedLongitude),
            event = if (!proto.hasEvent) null else NavigationWaypointPassageEvent(
                occurredAtEpochMillis = proto.eventAtEpochMillis,
                reason = enumValueOf<NavigationAdvanceReason>(proto.advanceReason),
                evidence = enumValueOf<NavigationPassageEvidence>(proto.evidence),
                actualPosition = if (proto.hasActualPosition) {
                    NavigationPosition(proto.actualLatitude, proto.actualLongitude)
                } else null,
                positionSourceId = proto.positionSourceId.takeIf(String::isNotBlank),
            ),
        )
}

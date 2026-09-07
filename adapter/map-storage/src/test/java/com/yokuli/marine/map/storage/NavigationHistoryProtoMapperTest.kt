package com.yokuli.marine.map.storage

import com.yokuli.marine.navigation.domain.NavigationAdvanceReason
import com.yokuli.marine.navigation.domain.NavigationHistorySnapshot
import com.yokuli.marine.navigation.domain.NavigationPassage
import com.yokuli.marine.navigation.domain.NavigationPassageEvidence
import com.yokuli.marine.navigation.domain.NavigationPassageOutcome
import com.yokuli.marine.navigation.domain.NavigationPassageWaypoint
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.NavigationWaypointPassageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavigationHistoryProtoMapperTest {
    @Test
    fun `history round trip preserves positioned and unavailable passage evidence`() {
        val snapshot = NavigationHistorySnapshot(
            revision = 4,
            passages = listOf(
                NavigationPassage(
                    sessionId = "route:3:1000",
                    revision = 3,
                    routeId = "route",
                    routeRevision = 3,
                    routeName = "Harbour",
                    startedAtEpochMillis = 1_000,
                    endedAtEpochMillis = 3_000,
                    outcome = NavigationPassageOutcome.COMPLETED,
                    waypoints = listOf(
                        NavigationPassageWaypoint(0, "A", NavigationPosition(-36.85, 174.76)),
                        NavigationPassageWaypoint(
                            1, "B", NavigationPosition(-36.80, 174.86),
                            NavigationWaypointPassageEvent(
                                2_000,
                                NavigationAdvanceReason.ARRIVAL_RADIUS,
                                NavigationPassageEvidence.POSITION_RECORDED,
                                NavigationPosition(-36.801, 174.861),
                                "selected-position",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(snapshot, NavigationHistoryProtoMapper.decode(NavigationHistoryProtoMapper.encode(snapshot)))
    }

    @Test
    fun `encoder rejects an over-capacity history before allocating a wire payload`() {
        val waypointA = NavigationPassageWaypoint(0, "A", NavigationPosition(-36.85, 174.76))
        val waypointB = NavigationPassageWaypoint(1, "B", NavigationPosition(-36.80, 174.86))
        val snapshot = NavigationHistorySnapshot(
            revision = 1,
            passages = List(1_001) { index ->
                NavigationPassage(
                    sessionId = "session-$index",
                    revision = 1,
                    routeId = "route-$index",
                    routeRevision = 1,
                    routeName = "Route $index",
                    startedAtEpochMillis = 1_000,
                    endedAtEpochMillis = 2_000,
                    outcome = NavigationPassageOutcome.STOPPED,
                    waypoints = listOf(waypointA, waypointB),
                )
            },
        )

        assertThrows(IllegalArgumentException::class.java) {
            NavigationHistoryProtoMapper.encode(snapshot)
        }
    }
}

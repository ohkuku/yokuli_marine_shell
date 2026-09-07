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
}

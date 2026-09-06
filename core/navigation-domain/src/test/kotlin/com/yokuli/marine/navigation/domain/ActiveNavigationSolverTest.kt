package com.yokuli.marine.navigation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveNavigationSolverTest {
    @Test
    fun `solution exposes WGS84 true bearing signed XTE ETA and bounded progress`() {
        val solution = requireNotNull(
            ActiveNavigationSolver.solve(
                route(),
                session(),
                NavigationFix(
                    revision = 1,
                    position = NavigationPosition(-36.84, 174.79),
                    positionStatus = NavigationInputStatus.LIVE,
                    positionSourceId = "position-source",
                    motionSourceId = "speed-source",
                    receivedAtMonotonicMillis = 100,
                    ageMillis = 0,
                    speedOverGroundKnots = 6.0,
                ),
            ),
        )

        assertTrue(solution.distanceToWaypointNauticalMiles > 0.0)
        assertTrue(requireNotNull(solution.bearingToWaypointTrueDegrees) in 0.0..<360.0)
        assertTrue(solution.crossTrackErrorNauticalMiles.isFinite())
        assertTrue(requireNotNull(solution.estimatedTimeToWaypointMillis) > 0)
        assertTrue(solution.legProgress in 0.0..1.0)
        assertTrue(solution.routeProgress in 0.0..1.0)
        assertEquals("position-source", solution.positionSourceId)
    }

    @Test
    fun `stale invalid and paused inputs never produce a live solution`() {
        val stale = NavigationFix(
            revision = 1,
            position = NavigationPosition(-36.84, 174.79),
            positionStatus = NavigationInputStatus.STALE,
        )
        assertNull(ActiveNavigationSolver.solve(route(), session(), stale))
        assertNull(ActiveNavigationSolver.solve(route(), session().copy(state = NavigationSessionState.PAUSED), usableFix()))
    }

    private fun route() = RoutePlan(
        id = "route",
        revision = 4,
        name = "Harbour",
        points = listOf(
            RoutePoint("A", NavigationPosition(-36.85, 174.76)),
            RoutePoint("B", NavigationPosition(-36.80, 174.86)),
            RoutePoint("C", NavigationPosition(-36.70, 174.95)),
        ),
    )

    private fun session() = ActiveNavigationSession(
        "route", 4, 1_000, 0, 50.0, NavigationAdvancePolicy.MANUAL, NavigationSessionState.ACTIVE,
    )

    private fun usableFix() = NavigationFix(
        revision = 1,
        position = NavigationPosition(-36.84, 174.79),
        positionStatus = NavigationInputStatus.LIVE,
        positionSourceId = "source",
        receivedAtMonotonicMillis = 100,
    )
}

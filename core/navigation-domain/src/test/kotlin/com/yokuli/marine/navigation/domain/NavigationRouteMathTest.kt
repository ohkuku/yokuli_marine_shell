package com.yokuli.marine.navigation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteMathTest {
    @Test
    fun `passage uses WGS84 legs true bearings and optional planned speed`() {
        val route = RoutePlan(
            id = "route",
            revision = 1L,
            name = "Passage",
            plannedSpeedKnots = 6.0,
            points = listOf(
                RoutePoint("a", NavigationPosition(-36.8485, 174.7633)),
                RoutePoint("b", NavigationPosition(-36.7867, 174.8600)),
                RoutePoint("c", NavigationPosition(-36.7867, 174.8600)),
            ),
        )

        val summary = NavigationRouteMath.summarize(route)

        assertEquals(2, summary.legs.size)
        assertTrue(summary.distanceNauticalMiles > 5.0)
        assertTrue(summary.legs.first().initialBearingTrueDegrees in 0.0..360.0)
        assertEquals(0.0, summary.legs.last().distanceMeters, 0.0)
        assertEquals(null, summary.legs.last().initialBearingTrueDegrees)
        assertTrue(requireNotNull(summary.estimatedDurationMillis) > 0L)
    }
}

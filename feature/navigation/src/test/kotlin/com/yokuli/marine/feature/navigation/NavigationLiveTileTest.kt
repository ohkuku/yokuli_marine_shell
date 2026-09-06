package com.yokuli.marine.feature.navigation

import com.yokuli.marine.navigation.domain.ActiveNavigationSession
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.navigation.domain.NavigationAdvancePolicy
import com.yokuli.marine.navigation.domain.NavigationInputStatus
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.marine.navigation.domain.NavigationSolution
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLiveTileTest {
    @Test fun `active navigation projects next waypoint dtw btw and xte without route draft UI`() {
        val route = route()
        val session = ActiveNavigationSession(
            routeId = route.id,
            routeRevision = route.revision,
            startedAtEpochMillis = 1L,
            activeLegIndex = 0,
            arrivalRadiusMeters = 50.0,
            advancePolicy = NavigationAdvancePolicy.MANUAL,
            state = NavigationSessionState.ACTIVE,
        )
        val solution = NavigationSolution(
            routeId = route.id,
            routeRevision = route.revision,
            activeLegIndex = 0,
            previousWaypointId = "point-a",
            nextWaypointId = "point-b",
            inputStatus = NavigationInputStatus.LIVE,
            distanceToWaypointNauticalMiles = 2.5,
            bearingToWaypointTrueDegrees = 42.0,
            crossTrackErrorNauticalMiles = -0.1,
            estimatedTimeToWaypointMillis = null,
            legProgress = .2,
            routeProgress = .1,
            positionSourceId = "gateway",
            motionSourceId = null,
            receivedAtMonotonicMillis = 10L,
        )
        val projected = NavigationLauncherProjector.project(
            NavigationUiState(
                library = NavigationLibrary(routePlans = listOf(route)),
                active = ActiveNavigationSnapshot(1L, session, route, solution = solution),
            ),
        )

        assertEquals(NavigationTileMode.ACTIVE, projected.mode)
        assertEquals("2", projected.nextWaypointLabel)
        assertEquals(2.5, projected.distanceToWaypointNauticalMiles!!, 0.0)
        assertEquals(42.0, projected.bearingToWaypointTrueDegrees!!, 0.0)
        assertEquals(-0.1, projected.crossTrackErrorNauticalMiles!!, 0.0)
    }

    @Test fun `inactive tile describes the most recent saved route and edit freeze yields to alerts`() {
        val first = route("first", "First")
        val recent = route("recent", "Recent")
        val projected = NavigationLauncherProjector.project(
            NavigationUiState(library = NavigationLibrary(routePlans = listOf(first, recent))),
        )
        assertEquals(NavigationTileMode.RECENT_ROUTE, projected.mode)
        assertEquals("Recent", projected.recentRouteName)
        assertTrue(projected.recentRouteDistanceNauticalMiles!! > 0.0)

        val slot = NavigationTileDisplaySlot(projected)
        val ordinary = projected.copy(recentRouteName = "Hidden while editing")
        assertEquals("Recent", slot.resolve(ordinary, liveContentEnabled = false).recentRouteName)
        val alert = ordinary.copy(notice = NavigationNotice.STORAGE_FAILED)
        assertTrue(alert.critical)
        assertEquals("Hidden while editing", slot.resolve(alert, liveContentEnabled = false).recentRouteName)
        assertFalse(projected.critical)
    }

    private fun route(id: String = "route", name: String = "Route") = RoutePlan(
        id = id,
        revision = 1L,
        name = name,
        points = listOf(
            RoutePoint("point-a", NavigationPosition(-36.80, 174.70)),
            RoutePoint("point-b", NavigationPosition(-36.81, 174.72)),
        ),
    )
}

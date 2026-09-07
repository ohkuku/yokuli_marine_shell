package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartNavigationCommitPlannerTest {
    @Test fun quickMarkNumbersFromCanonicalNavigationWaypoints() {
        val library = NavigationLibrary(
            revision = 3,
            waypoints = listOf(
                Waypoint("a", 1, "WP 004", NavigationPosition(-36.8, 174.7)),
                Waypoint("b", 1, "Harbour", NavigationPosition(-36.9, 174.8)),
            ),
        )

        val change = ChartNavigationCommitPlanner.quickMark(
            library, GeoPoint(-36.7, 174.9), id = "wp-new", nowMillis = 42L,
        )

        assertEquals("wp-new", change.waypoint.id)
        assertEquals("WP 005", change.waypoint.name)
        assertEquals(42L, change.waypoint.createdAtMillis)
    }

    @Test fun routeSaveUsesExactSpatialDraftAndCanonicalRevision() {
        val existing = RoutePlan(
            "route-a", 4, "Old",
            listOf(
                RoutePoint("old-a", NavigationPosition(-36.8, 174.7)),
                RoutePoint("old-b", NavigationPosition(-36.9, 174.8)),
            ),
        )
        val draft = ManualRouteDraft(
            id = "draft-a",
            revision = 7,
            name = "Edited",
            waypoints = listOf(GeoPoint(-36.81, 174.71), GeoPoint(-36.72, 174.92)),
            waypointIds = listOf("point-a", "point-b"),
            basePlanId = existing.id,
            basePlanRevision = existing.revision,
            nextWaypointOrdinal = 3,
        )

        val change = ChartNavigationCommitPlanner.saveRoute(
            NavigationLibrary(revision = 8, routePlans = listOf(existing)), draft,
        )

        val saved = requireNotNull(change).route
        assertEquals(existing.id, saved.id)
        assertEquals(5L, saved.revision)
        assertEquals(listOf("point-a", "point-b"), saved.points.map { it.id })
        assertEquals(draft.waypoints.map { it.latitude to it.longitude }, saved.points.map { it.position.latitude to it.position.longitude })
    }

    @Test fun staleBaseRouteRevisionCannotOverwriteCanonicalRoute() {
        val existing = RoutePlan(
            "route-a", 5, "Current",
            listOf(
                RoutePoint("a", NavigationPosition(-36.8, 174.7)),
                RoutePoint("b", NavigationPosition(-36.9, 174.8)),
            ),
        )
        val stale = ManualRouteDraft(
            id = "draft-a", revision = 2, name = "Stale",
            waypoints = listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.7, 174.9)),
            waypointIds = listOf("a", "b"),
            basePlanId = existing.id, basePlanRevision = 4, nextWaypointOrdinal = 3,
        )

        assertNull(ChartNavigationCommitPlanner.saveRoute(NavigationLibrary(routePlans = listOf(existing)), stale))
    }
}

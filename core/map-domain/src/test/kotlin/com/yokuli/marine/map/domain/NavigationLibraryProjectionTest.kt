package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLibraryProjectionTest {
    private val reducer = DefaultMapReducer()

    @Test fun navigationProjectionReplacesDurableObjectsButPreservesSpatialDraft() {
        val draft = ManualRouteDraft(
            id = "scratch", revision = 1, name = "Scratch",
            waypoints = listOf(GeoPoint(-36.8, 174.7)), waypointIds = listOf("p1"), nextWaypointOrdinal = 2,
        )
        val waypoint = SavedPlace("canonical", "WP 001", GeoPoint(-36.7, 174.8))
        val initial = MapState(
            routeDrafts = listOf(draft), activeRouteDraftId = draft.id,
            places = listOf(SavedPlace("legacy", "Old", GeoPoint(-36.9, 174.6))),
        )

        val reduction = reducer.reduce(
            initial,
            MapAction.NavigationLibraryProjected(MapLibrarySnapshot(revision = 9, places = listOf(waypoint))),
        )

        assertEquals(listOf(waypoint), reduction.state.places)
        assertEquals(draft, reduction.state.routeDraft)
        assertEquals(9L, reduction.state.durableLibraryRevision)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test fun committedCanonicalRouteClosesScratchWithoutAnotherLibraryWrite() {
        val draft = ManualRouteDraft(
            id = "scratch", revision = 2, name = "Passage",
            waypoints = listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.7, 174.9)),
            waypointIds = listOf("p1", "p2"), nextWaypointOrdinal = 3,
        )
        val reduction = reducer.reduce(
            MapState(tool = MapTool.MANUAL_ROUTE, routeDrafts = listOf(draft), activeRouteDraftId = draft.id),
            MapAction.NavigationRouteCommitted("route", 3),
        )

        assertEquals(MapTool.BROWSE, reduction.state.tool)
        assertEquals("route", reduction.state.activeRoutePlanId)
        assertTrue(reduction.state.routeDrafts.isEmpty())
        assertTrue(reduction.effects.none { it is MapEffect.PersistLibrary })
    }
}

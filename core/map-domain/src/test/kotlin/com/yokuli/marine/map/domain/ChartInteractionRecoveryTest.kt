package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartInteractionRecoveryTest {
    private val reducer = DefaultMapReducer(
        idGenerator = MapIdGenerator { namespace -> "$namespace-fixed" },
        clock = MapClock { 1_000L },
    )
    private val vessel = GeoPoint(-36.8485, 174.7633)
    private val target = GeoPoint(-36.8200, 174.8100)
    private val moved = GeoPoint(-36.7900, 174.8600)

    @Test
    fun `measurement preview is the single display truth for pins line distance and bearing`() {
        val measuring = reduce(MapState(), MapAction.BeginMeasurement(vessel, target))
        val dragging = reduce(
            measuring,
            MapAction.BeginPointDrag(MapGestureId("move-b"), MapEditTarget.MeasurementPoint(1)),
        )
        val preview = reduce(dragging, MapAction.PreviewPointDrag(MapGestureId("move-b"), moved))

        assertEquals(listOf(vessel, target), preview.measurementDraft?.points)
        assertEquals(listOf(vessel, moved), preview.visibleMeasurementPoints)
        val expected = Wgs84Geodesic.inverse(vessel, moved)
        val summary = requireNotNull(preview.visibleMeasurementSummary)
        assertEquals(expected.distanceMeters, summary.totalDistanceMeters, 0.01)
        assertEquals(expected.initialBearingTrueDegrees, summary.segments.single().initialBearingTrueDegrees)

        val cancelled = reduce(preview, MapAction.CancelPointDrag(MapGestureId("move-b")))
        assertEquals(listOf(vessel, target), cancelled.visibleMeasurementPoints)
    }

    @Test
    fun `measure is a transient two point ruler and exit leaves no route or hidden geometry`() {
        var state = reduce(MapState(), MapAction.BeginMeasurement(vessel, target))
        state = reduce(state, MapAction.MapTapped(moved, emptyList()))
        assertEquals(listOf(vessel, moved), state.measurementDraft?.points)
        assertTrue(state.routeDrafts.isEmpty())
        assertTrue(state.savedRoutes.isEmpty())

        state = reduce(state, MapAction.SelectTool(MapTool.BROWSE))
        assertNull(state.measurementDraft)
        assertNull(state.visibleMeasurementSummary)
        assertTrue(state.routeDrafts.isEmpty())
        assertFalse(state.navigationActive)
    }

    @Test
    fun `quick mark is one action with an automatic name and no mandatory metadata form`() {
        val reduction = reducer.reduce(MapState(), MapAction.QuickMark(target))

        val waypoint = reduction.state.places.single()
        assertEquals("WP 001", waypoint.name)
        assertEquals(target, waypoint.point)
        assertEquals("", waypoint.notes)
        assertTrue(waypoint.tags.isEmpty())
        assertEquals(PlaceCategory.PERSONAL_MARKER, waypoint.category)
        assertEquals(MapSurface.Root, reduction.state.surface)
        assertEquals(target, reduction.state.selection?.point)
        assertTrue(reduction.effects.any { it is MapEffect.PersistLibrary })
    }

    @Test
    fun `plain map tap immediately creates a visible target without opening a form`() {
        val state = reduce(MapState(), MapAction.MapTapped(target, emptyList()))

        assertEquals(target, state.selection?.point)
        assertEquals(target, (state.transient as MapTransient.PointCandidate).point)
        assertEquals(MapSurface.Root, state.surface)
    }

    @Test
    fun `active navigation exposes the current leg without replacing the complete route`() {
        val route = listOf(vessel, target, moved)
        val state = reduce(
            MapState(),
            MapAction.ActiveNavigationGeometryChanged(route, listOf(target, moved)),
        )

        assertEquals(route, state.activeNavigationRoute)
        assertEquals(listOf(target, moved), state.activeNavigationLeg)
        assertTrue(state.navigationActive)
    }

    private fun reduce(state: MapState, action: MapAction): MapState = reducer.reduce(state, action).state
}

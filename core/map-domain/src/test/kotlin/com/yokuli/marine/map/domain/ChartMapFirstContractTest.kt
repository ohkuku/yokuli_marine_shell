package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMapFirstContractTest {
    private val vessel = GeoPoint(-36.8485, 174.7633)
    private val target = GeoPoint(-36.82, 174.81)
    private val movedTarget = GeoPoint(-36.79, 174.86)

    @Test
    fun `browse tap always exposes a visible target`() {
        val state = reduce(MapState(), MapAction.MapTapped(target, emptyList())).state

        assertEquals(target, state.selection?.point)
        assertEquals(target, (state.transient as MapTransient.PointCandidate).point)
    }

    @Test
    fun `quick mark saves a minimal waypoint without opening a form`() {
        val ids = ArrayDeque(listOf("place-1", "place-2"))
        val reducer = DefaultMapReducer(MapIdGenerator { ids.removeFirst() }, clock = MapClock { 10L })

        var state = reducer.reduce(MapState(), MapAction.QuickMark(target)).state
        state = reducer.reduce(state, MapAction.QuickMark(movedTarget)).state

        assertEquals(listOf("WP 001", "WP 002"), state.places.map { it.name })
        assertTrue(state.places.all { it.notes.isEmpty() && it.tags.isEmpty() })
        assertEquals(MapSurface.Root, state.surface)
        assertEquals(movedTarget, state.selection?.point)
        assertEquals("place:place-2", (state.transient as MapTransient.SelectedObject).hit.objectId)
    }

    @Test
    fun `measure is an immediate two handle ruler and never grows into a route`() {
        var state = reduce(MapState(), MapAction.BeginMeasurement(vessel, target)).state
        assertEquals(listOf(vessel, target), state.measurementDraft?.points)
        assertTrue(state.routeDrafts.isEmpty())

        state = reduce(state, MapAction.MapTapped(movedTarget, emptyList())).state
        assertEquals(listOf(vessel, movedTarget), state.measurementDraft?.points)
        assertTrue(state.routeDrafts.isEmpty())

        state = reduce(state, MapAction.SelectTool(MapTool.BROWSE)).state
        assertNull(state.measurementDraft)
    }

    @Test
    fun `route tap commits immediately and back requests an explicit draft decision`() {
        var state = reduce(MapState(), MapAction.SelectTool(MapTool.MANUAL_ROUTE)).state
        state = reduce(state, MapAction.MapTapped(vessel, emptyList())).state
        state = reduce(state, MapAction.MapTapped(target, emptyList())).state

        assertEquals(listOf(vessel, target), state.routeDraft?.waypoints)
        assertNull(state.transient)
        assertEquals(MapAction.RequestCloseRouteDraft, MapFeatureBackPolicy.actionFor(state))

        state = reduce(state, MapAction.RequestCloseRouteDraft).state
        assertEquals(state.routeDraft?.id, (state.transient as MapTransient.UnsavedRoute).draftId)
    }

    @Test
    fun `tapping a route leg inserts a directly editable point on the nearest leg`() {
        val third = GeoPoint(-36.77, 174.92)
        val insertion = GeoPoint(-36.805, 174.855)
        var state = reduce(MapState(), MapAction.SelectTool(MapTool.MANUAL_ROUTE)).state
        state = reduce(state, MapAction.MapTapped(vessel, emptyList())).state
        state = reduce(state, MapAction.MapTapped(target, emptyList())).state
        state = reduce(state, MapAction.MapTapped(third, emptyList())).state

        state = reduce(
            state,
            MapAction.MapTapped(
                insertion,
                listOf(MapHitResult(MapOverlayId.MANUAL_ROUTE, "route:${state.routeDraft?.id}:0")),
            ),
        ).state

        assertEquals(listOf(vessel, target, insertion, third), state.routeDraft?.waypoints)
        assertNull(state.transient)
    }

    @Test
    fun `map view is the session preference while measurement is not`() {
        val changed = reduce(MapState(), MapAction.SetMapViewMode(MapViewMode.STANDARD))
        val session = (changed.effects.single() as MapEffect.PersistSession).snapshot

        assertEquals(MapViewMode.STANDARD, session.mapViewMode)
        assertNull(MapState(measurementDraft = MeasurementDraft(listOf(vessel, target))).sessionSnapshot().measurementDraft)
    }

    @Test
    fun `direct to geometry is visible without becoming a saved route`() {
        val state = reduce(
            MapState(),
            MapAction.ActiveNavigationGeometryChanged(listOf(vessel, target)),
        ).state

        assertEquals(listOf(vessel, target), state.visibleRoutePoints)
        assertTrue(state.savedRoutes.isEmpty())
        assertNull(state.activeRoutePlanId)
        assertTrue(state.persisted().savedRoutes.isEmpty())
    }

    private fun reduce(state: MapState, action: MapAction): MapReduction = DefaultMapReducer().reduce(state, action)
}

package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapInteractionContractTest {
    private val first = GeoPoint(-36.8485, 174.7633)
    private val second = GeoPoint(-36.82, 174.81)
    private val final = GeoPoint(-36.79, 174.86)

    @Test
    fun `feature Back consumes exactly one transient gesture tool and surface plane`() {
        val gestureId = MapGestureId("gesture-1")
        var state = MapState(
            surface = MapSurface.ChartPackages,
            tool = MapTool.MEASURE,
            transient = MapTransient.PointCandidate(first, PointCandidateOrigin.MAP_TAP),
            measurementDraft = MeasurementDraft(listOf(first)),
            editGesture = MapEditGesture(
                gestureId,
                MapEditTarget.MeasurementPoint(0),
                originalPoint = first,
                previewPoint = second,
            ),
        )

        state = consumeBack(state)
        assertNull(state.transient)
        assertTrue(state.editGesture is MapEditGesture)
        assertEquals(MapTool.MEASURE, state.tool)
        assertEquals(MapSurface.ChartPackages, state.surface)

        state = consumeBack(state)
        assertNull(state.editGesture)
        assertEquals(listOf(first), state.measurementDraft?.points)

        state = consumeBack(state)
        assertEquals(MapTool.BROWSE, state.tool)
        assertEquals(MapSurface.ChartPackages, state.surface)

        state = consumeBack(state)
        assertEquals(MapSurface.Root, state.surface)
        assertNull(MapFeatureBackPolicy.actionFor(state))
    }

    @Test
    fun `map tap creates a candidate and only explicit confirmation appends one point`() {
        var state = reduce(MapState(), MapAction.SelectTool(MapTool.MEASURE)).state

        state = reduce(state, MapAction.MapTapped(first, emptyList())).state
        assertEquals(first, (state.transient as MapTransient.PointCandidate).point)
        assertTrue(state.measurementDraft?.points.orEmpty().isEmpty())

        state = reduce(state, MapAction.CameraChanged(MapCamera(second, 12.0))).state
        assertTrue(state.measurementDraft?.points.orEmpty().isEmpty())

        state = reduce(state, MapAction.ConfirmPointCandidate).state
        assertEquals(listOf(first), state.measurementDraft?.points)
        assertNull(state.transient)

        state = reduce(state, MapAction.ConfirmPointCandidate).state
        assertEquals(listOf(first), state.measurementDraft?.points)
    }

    @Test
    fun `object hits are selected deterministically and overlaps require a candidate choice`() {
        val place = MapHitResult(MapOverlayId.SAVED_PLACES, "place:a")
        val routePoint = MapHitResult(MapOverlayId.MANUAL_ROUTE_POINTS, "route-point:r:0")

        val selected = reduce(MapState(), MapAction.MapTapped(first, listOf(place, place))).state
        assertEquals(place, (selected.transient as MapTransient.SelectedObject).hit)

        val overlap = reduce(
            MapState(),
            MapAction.MapTapped(first, listOf(routePoint, place, routePoint)),
        ).state
        assertEquals(
            listOf(routePoint, place),
            (overlap.transient as MapTransient.ObjectCandidates).hits,
        )
    }

    @Test
    fun `gesture preview is non durable final frame commits once and viewport change cancels`() {
        val gestureId = MapGestureId("gesture-2")
        val initial = MapState(measurementDraft = MeasurementDraft(listOf(first)))
        val dragging = reduce(
            initial,
            MapAction.BeginPointDrag(gestureId, MapEditTarget.MeasurementPoint(0)),
        ).state
        val preview = reduce(dragging, MapAction.PreviewPointDrag(gestureId, second)).state

        assertEquals(listOf(first), preview.measurementDraft?.points)
        assertEquals(second, preview.editGesture?.previewPoint)

        val committed = reduce(preview, MapAction.CommitPointDrag(gestureId, final))
        assertEquals(listOf(final), committed.state.measurementDraft?.points)
        assertNull(committed.state.editGesture)
        assertEquals(1, committed.effects.filterIsInstance<MapEffect.PersistSession>().size)

        val nextGesture = reduce(
            committed.state,
            MapAction.BeginPointDrag(MapGestureId("gesture-3"), MapEditTarget.MeasurementPoint(0)),
        ).state
        val changedViewport = reduce(
            nextGesture,
            MapAction.ViewportChanged(MapViewport(320, 320, MapViewportInsets(bottomPx = 48), revision = 2)),
        ).state
        assertNull(changedViewport.editGesture)
        assertEquals(listOf(final), changedViewport.measurementDraft?.points)
    }

    @Test
    fun `stale gesture frames cannot mutate the active edit`() {
        val activeId = MapGestureId("active")
        val staleId = MapGestureId("stale")
        val state = reduce(
            MapState(measurementDraft = MeasurementDraft(listOf(first))),
            MapAction.BeginPointDrag(activeId, MapEditTarget.MeasurementPoint(0)),
        ).state

        val stalePreview = reduce(state, MapAction.PreviewPointDrag(staleId, second)).state
        val staleCommit = reduce(stalePreview, MapAction.CommitPointDrag(staleId, final)).state

        assertEquals(state, stalePreview)
        assertEquals(state, staleCommit)
    }

    private fun consumeBack(state: MapState): MapState {
        val action = requireNotNull(MapFeatureBackPolicy.actionFor(state))
        return reduce(state, action).state
    }

    private fun reduce(state: MapState, action: MapAction): MapReduction = MapReducer.reduce(state, action)
}

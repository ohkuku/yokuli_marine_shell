package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementEditingTest {
    private val a = GeoPoint(-36.8485, 174.7633)
    private val b = GeoPoint(-36.82, 174.81)
    private val c = GeoPoint(-36.79, 174.86)
    private val moved = GeoPoint(-36.80, 174.83)

    @Test
    fun `zero one and multi point summaries expose real segment and total facts`() {
        assertEquals(MeasurementPrompt.PLACE_START, MeasurementMath.summarize(MeasurementDraft()).prompt)
        assertEquals(MeasurementPrompt.PLACE_END, MeasurementMath.summarize(MeasurementDraft(listOf(a))).prompt)

        val summary = MeasurementMath.summarize(MeasurementDraft(listOf(a, b, c)))
        assertEquals(MeasurementPrompt.RESULTS, summary.prompt)
        assertEquals(2, summary.segments.size)
        assertEquals(summary.segments.sumOf { it.distanceMeters }, summary.totalDistanceMeters, 1e-9)
        assertTrue(summary.segments.all { it.initialBearingTrueDegrees != null })
    }

    @Test
    fun `insert delete move clear and undo redo are reversible confirmed edits`() {
        var state = MapState(tool = MapTool.MEASURE, measurementDraft = MeasurementDraft(listOf(a, c)))
        state = reduce(state, MapAction.InsertMeasurementPoint(1, b)).state
        assertEquals(listOf(a, b, c), state.measurementDraft?.points)

        state = reduce(state, MapAction.DeleteMeasurementPoint(0)).state
        assertEquals(listOf(b, c), state.measurementDraft?.points)
        state = reduce(state, MapAction.UndoMeasurementEdit).state
        assertEquals(listOf(a, b, c), state.measurementDraft?.points)
        state = reduce(state, MapAction.RedoMeasurementEdit).state
        assertEquals(listOf(b, c), state.measurementDraft?.points)

        val dragging = reduce(
            state,
            MapAction.BeginPointDrag(MapGestureId("final-drop"), MapEditTarget.MeasurementPoint(0)),
        ).state
        val committed = reduce(dragging, MapAction.CommitPointDrag(MapGestureId("final-drop"), moved))
        assertEquals(listOf(moved, c), committed.state.measurementDraft?.points)
        assertEquals(1, committed.effects.filterIsInstance<MapEffect.PersistSession>().size)

        val cleared = reduce(committed.state, MapAction.ClearMeasurement).state
        assertTrue(cleared.measurementDraft?.points.orEmpty().isEmpty())
        val restored = reduce(cleared, MapAction.UndoMeasurementEdit).state
        assertEquals(listOf(moved, c), restored.measurementDraft?.points)
    }

    @Test
    fun `precise edit accepts crosshair or coordinate result without mutating other collections`() {
        val place = SavedPlace("place", "saved", a)
        var state = MapState(
            tool = MapTool.MEASURE,
            places = listOf(place),
            measurementDraft = MeasurementDraft(listOf(a, b)),
        )
        state = reduce(
            state,
            MapAction.BeginPrecisePointEdit(MapPrecisePointEdit.Move(MapEditTarget.MeasurementPoint(1))),
        ).state
        state = reduce(state, MapAction.ConfirmPrecisePoint(c)).state

        assertEquals(listOf(a, c), state.measurementDraft?.points)
        assertEquals(listOf(place), state.places)
        assertNull(state.precisePointEdit)
    }

    @Test
    fun `measurement conversion copies coordinates into a distinct route draft`() {
        val reducer = DefaultMapReducer(MapIdGenerator { "draft-copy" })
        val measured = MapState(tool = MapTool.MEASURE, measurementDraft = MeasurementDraft(listOf(a, b, c)))
        val converted = reducer.reduce(measured, MapAction.ConvertMeasurementToManualRoute("copy")).state

        assertEquals(measured.measurementDraft?.points, converted.routeDraft?.waypoints)
        assertNotSame(measured.measurementDraft?.points, converted.routeDraft?.waypoints)
        assertEquals(listOf(a, b, c), measured.measurementDraft?.points)
    }

    private fun reduce(state: MapState, action: MapAction): MapReduction = MapReducer.reduce(state, action)
}

package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapReducerTest {
    private val auckland = GeoPoint(-36.8485, 174.7633)
    private val rangitoto = GeoPoint(-36.7867, 174.8600)
    private val waiheke = GeoPoint(-36.7950, 175.0900)

    @Test
    fun `no position still permits browse selection and saved place`() {
        val selected = reduce(MapState(), MapAction.LongPressMap(auckland)).state
        val saved = reduce(selected, MapAction.SaveSelectionAsPlace("锚地候选")).state

        assertEquals(PositionAvailability.UNAVAILABLE, saved.position.availability)
        assertEquals(MapTool.BROWSE, saved.tool)
        assertEquals(auckland, saved.places.single().point)
        assertNull(saved.selection)
    }

    @Test
    fun `position freshness is based on observation identity and observed time`() {
        val first = PositionObservation("fix-1", auckland, observedAtMillis = 1_000L, source = "phone")
        val fresh = reduce(MapState(), MapAction.ObservePosition(first, nowMillis = 5_000L)).state
        val duplicate = reduce(
            fresh,
            MapAction.ObservePosition(first.copy(point = rangitoto, observedAtMillis = 20_000L), nowMillis = 20_000L),
        ).state
        val stale = reduce(duplicate, MapAction.ClockTick(nowMillis = 40_001L)).state

        assertEquals(PositionAvailability.FRESH, fresh.position.availability)
        assertEquals(auckland, duplicate.position.observation?.point)
        assertEquals(1_000L, duplicate.position.observation?.observedAtMillis)
        assertEquals(PositionAvailability.STALE, stale.position.availability)
    }

    @Test
    fun `measurement and manual route remain different domain objects`() {
        val measuring = reduce(MapState(), MapAction.SelectTool(MapTool.MEASURE)).state
        val measured = listOf(auckland, rangitoto).fold(measuring) { state, point ->
            reduce(state, MapAction.AddPoint(point)).state
        }
        val routed = reduce(measured, MapAction.ConvertMeasurementToManualRoute("harbour plan")).state

        assertEquals(2, routed.measurementDraft?.points?.size)
        assertEquals(2, routed.routeDraft?.waypoints?.size)
        assertEquals(RoutePurpose.MANUAL_PLANNING, routed.routeDraft?.purpose)
        assertFalse(routed.navigationActive)
    }

    @Test
    fun `manual route editing supports undo redo reverse copy and planned eta`() {
        var state = reduce(MapState(), MapAction.SelectTool(MapTool.MANUAL_ROUTE)).state
        state = reduce(state, MapAction.AddPoint(auckland)).state
        state = reduce(state, MapAction.AddPoint(rangitoto)).state
        state = reduce(state, MapAction.AddPoint(waiheke)).state
        val original = requireNotNull(state.routeDraft)

        val undone = reduce(state, MapAction.UndoRouteEdit).state
        val redone = reduce(undone, MapAction.RedoRouteEdit).state
        val reversed = reduce(redone, MapAction.ReverseRoute).state
        val planned = reduce(reversed, MapAction.SetPlannedSpeedKnots(6.5)).state
        val copied = reduce(planned, MapAction.SaveRouteCopy("返航计划")).state

        assertEquals(2, undone.routeDraft?.waypoints?.size)
        assertEquals(original.waypoints, redone.routeDraft?.waypoints)
        assertEquals(original.waypoints.reversed(), reversed.routeDraft?.waypoints)
        assertTrue(requireNotNull(planned.routeSummary).estimatedDurationMillis > 0L)
        assertEquals("返航计划", copied.savedRoutes.single().name)
        assertFalse(copied.navigationActive)
    }

    @Test
    fun `invalid planned speed is rejected without corrupting draft`() {
        val state = reduce(
            reduce(MapState(), MapAction.SelectTool(MapTool.MANUAL_ROUTE)).state,
            MapAction.AddPoint(auckland),
        ).state
        val result = reduce(state, MapAction.SetPlannedSpeedKnots(0.0))

        assertEquals(state, result.state)
        assertTrue(result.effects.single() is MapEffect.LogIncident)
    }

    @Test
    fun `chart package removal preserves user content and camera`() {
        val packageInfo = ChartPackage(
            id = ChartPackageId("nz-demo"),
            displayName = "NZ demo raster",
            source = "owner supplied",
            license = "test fixture",
            attribution = "fixture only",
            sha256 = "a".repeat(64),
            localUri = "file:///charts/nz-demo.mbtiles",
            coverage = GeoBounds(-37.0, 174.0, -36.0, 176.0),
            minZoom = 4,
            maxZoom = 14,
            version = "1",
        )
        val camera = MapCamera(rangitoto, 12.0)
        val content = MapState(
            camera = camera,
            places = listOf(SavedPlace("place-1", "码头", auckland)),
            savedRoutes = listOf(SavedRoute("route-1", "计划", listOf(auckland, rangitoto), 5.0)),
            chartPackages = listOf(packageInfo),
        )
        val removed = reduce(content, MapAction.ChartPackagesChanged(emptyList())).state

        assertEquals(camera, removed.camera)
        assertEquals(content.places, removed.places)
        assertEquals(content.savedRoutes, removed.savedRoutes)
        assertTrue(removed.chartPackages.isEmpty())
        assertNull(removed.activeChartPackageId)
    }

    @Test
    fun `active chart package is explicit and rejects an uninstalled id`() {
        fun packageInfo(id: String) = ChartPackage(
            ChartPackageId(id), id, "source", "license", "attribution", id.first().toString().repeat(64),
            "mbtiles:///charts/$id.mbtiles", GeoBounds(-37.0, 174.0, -36.0, 176.0), 4, 14, "1",
        )
        val first = packageInfo("aaaa")
        val second = packageInfo("bbbb")
        val installed = reduce(MapState(), MapAction.ChartPackagesChanged(listOf(first, second))).state
        val selected = reduce(installed, MapAction.SelectChartPackage(first.id)).state
        val rejected = reduce(selected, MapAction.SelectChartPackage(ChartPackageId("missing")))

        assertEquals(second.id, installed.activeChartPackageId)
        assertEquals(first.id, selected.activeChartPackageId)
        assertEquals(selected, rejected.state)
        assertTrue((rejected.effects.single() as MapEffect.LogIncident).incident is MapIncident.UnknownChartPackage)
    }

    @Test
    fun `persist effect contains only durable map facts`() {
        val changed = reduce(MapState(), MapAction.CameraChanged(MapCamera(rangitoto, 9.5)))
        val persisted = (changed.effects.single() as MapEffect.Persist).snapshot

        assertEquals(rangitoto, persisted.camera.center)
        assertFalse(persisted.navigationActive)
        assertNull(persisted.positionObservation)
    }

    private fun reduce(state: MapState, action: MapAction): MapReduction = MapReducer.reduce(state, action)
}

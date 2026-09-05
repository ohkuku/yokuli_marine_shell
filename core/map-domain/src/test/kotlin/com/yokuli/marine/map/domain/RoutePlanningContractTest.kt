package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlanningContractTest {
    private val a = GeoPoint(-36.8485, 174.7633)
    private val b = GeoPoint(-36.7867, 174.8600)
    private val c = GeoPoint(-36.7950, 175.0900)
    private val d = GeoPoint(-36.7000, 175.2000)

    @Test
    fun `new route saves without invented speed then previews the acknowledged plan`() {
        val reducer = reducerWithIds("draft-a", "route-a")
        var state = reducer.reduce(MapState(), MapAction.CreateRouteDraft("Harbour", "Day plan", a)).state
        state = reducer.reduce(state, MapAction.AddRouteWaypoint(b)).state

        assertNull(requireNotNull(state.routeDraft).plannedSpeedKnots)
        assertNull(state.routeSummary?.estimatedDurationMillis)
        val saving = reducer.reduce(state, MapAction.SaveRoutePlan).state
        val targetRevision = saving.libraryRevision

        assertEquals(listOf("route-a"), saving.savedRoutes.map { it.id })
        assertEquals("draft-a", saving.routeSaveTransaction?.draft?.id)
        assertEquals(MapSaveState.PENDING, saving.routeSaveStatus?.state)

        val acknowledged = reducer.reduce(saving, MapAction.PersistenceAck(targetRevision)).state
        assertTrue(acknowledged.routeDrafts.isEmpty())
        assertEquals("route-a", acknowledged.activeRoutePlanId)
        assertEquals(MapSaveState.SAVED, acknowledged.routeSaveStatus?.state)
        assertEquals(MapSurface.RouteDetail("route-a"), acknowledged.surface)
        assertFalse(acknowledged.navigationActive)
    }

    @Test
    fun `preview is read only and editing saves the same plan with optimistic revision check`() {
        val plan = routePlan("route-a", revision = 4L)
        val reducer = reducerWithIds("draft-edit")
        val preview = reducer.reduce(
            MapState(savedRoutes = listOf(plan), libraryLoadState = MapLibraryLoadState.READY),
            MapAction.PreviewRoutePlan(plan.id),
        ).state

        assertEquals(plan.id, preview.activeRoutePlanId)
        assertTrue(preview.routeDrafts.isEmpty())
        assertEquals(plan.waypoints, preview.visibleRoutePoints)

        var editing = reducer.reduce(preview, MapAction.BeginRoutePlanEdit(plan.id)).state
        val draft = requireNotNull(editing.routeDraft)
        assertEquals(plan.id, draft.basePlanId)
        assertEquals(4L, draft.basePlanRevision)
        assertEquals(plan.waypointIds, draft.waypointIds)
        editing = reducer.reduce(editing, MapAction.MoveRouteWaypoint(draft.waypointIds.last(), c)).state
        val saving = reducer.reduce(editing, MapAction.SaveRoutePlan).state
        val acknowledged = reducer.reduce(saving, MapAction.PersistenceAck(saving.libraryRevision)).state

        assertEquals(listOf(plan.id), acknowledged.savedRoutes.map { it.id })
        assertEquals(5L, acknowledged.savedRoutes.single().revision)
        assertEquals(c, acknowledged.savedRoutes.single().waypoints.last())
    }

    @Test
    fun `multiple drafts survive activation and measurement conversion`() {
        val reducer = reducerWithIds("draft-a", "draft-b", "draft-c")
        var state = reducer.reduce(MapState(), MapAction.CreateRouteDraft("A", "", a)).state
        state = reducer.reduce(state, MapAction.CreateRouteDraft("B", "", b)).state
        state = reducer.reduce(state, MapAction.ActivateRouteDraft("draft-a")).state
        state = reducer.reduce(state, MapAction.SelectTool(MapTool.MEASURE)).state
        state = reducer.reduce(state, MapAction.AddPoint(c)).state
        state = reducer.reduce(state, MapAction.AddPoint(d)).state
        state = reducer.reduce(state, MapAction.ConvertMeasurementToManualRoute("C")).state

        assertEquals(listOf("draft-a", "draft-b", "draft-c"), state.routeDrafts.map { it.id })
        assertEquals("draft-c", state.activeRouteDraftId)
        assertEquals(listOf(a), state.routeDrafts[0].waypoints)
        assertEquals(listOf(b), state.routeDrafts[1].waypoints)
        assertEquals(listOf(c, d), state.routeDrafts[2].waypoints)
    }

    @Test
    fun `waypoint identity survives insert move reorder delete reverse undo and redo`() {
        val reducer = reducerWithIds("draft-a")
        var state = reducer.reduce(MapState(), MapAction.CreateRouteDraft("A", "", a)).state
        state = reducer.reduce(state, MapAction.AddRouteWaypoint(c)).state
        val draft = requireNotNull(state.routeDraft)
        val firstId = draft.waypointIds.first()
        val lastId = draft.waypointIds.last()
        state = reducer.reduce(state, MapAction.InsertRouteWaypoint(lastId, b)).state
        val insertedId = requireNotNull(state.routeDraft).waypointIds[1]
        state = reducer.reduce(state, MapAction.MoveRouteWaypoint(insertedId, d)).state
        state = reducer.reduce(state, MapAction.ReorderRouteWaypoint(insertedId, 0)).state
        state = reducer.reduce(state, MapAction.DeleteRouteWaypoint(lastId)).state
        val edited = requireNotNull(state.routeDraft)

        assertEquals(listOf(insertedId, firstId), edited.waypointIds)
        assertEquals(listOf(d, a), edited.waypoints)
        val undone = reducer.reduce(state, MapAction.UndoRouteEdit).state
        val redone = reducer.reduce(undone, MapAction.RedoRouteEdit).state
        val reversed = reducer.reduce(redone, MapAction.ReverseRoute).state
        assertEquals(edited.waypointIds, redone.routeDraft?.waypointIds)
        assertEquals(edited.waypoints, redone.routeDraft?.waypoints)
        assertEquals(edited.waypointIds.reversed(), reversed.routeDraft?.waypointIds)
    }

    @Test
    fun `adjacent duplicate and invalid speed are rejected while valid speed estimates only planning time`() {
        val reducer = reducerWithIds("draft-a")
        var state = reducer.reduce(MapState(), MapAction.CreateRouteDraft("A", "", a)).state
        val duplicate = reducer.reduce(state, MapAction.AddRouteWaypoint(a))
        assertEquals(state, duplicate.state)
        assertTrue((duplicate.effects.single() as MapEffect.LogIncident).incident is MapIncident.AdjacentDuplicateWaypoint)

        state = reducer.reduce(state, MapAction.AddRouteWaypoint(b)).state
        assertNull(state.routeSummary?.estimatedDurationMillis)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertEquals(state, reducer.reduce(state, MapAction.SetPlannedSpeedKnots(invalid)).state)
        }
        val planned = reducer.reduce(state, MapAction.SetPlannedSpeedKnots(6.5)).state
        assertTrue(requireNotNull(planned.routeSummary?.estimatedDurationMillis) > 0L)
        assertEquals(RouteSpeedNotice.EXTREME, reducer.reduce(planned, MapAction.SetPlannedSpeedKnots(120.0)).state.routeSpeedNotice)
    }

    @Test
    fun `copy reverse and save as copy create new plans without mutating the source`() {
        val source = routePlan("route-a", revision = 2L)
        val reducer = reducerWithIds("route-copy", "draft-edit", "route-save-as")
        var state = MapState(savedRoutes = listOf(source), libraryLoadState = MapLibraryLoadState.READY)
        state = reducer.reduce(state, MapAction.DuplicateRoutePlan(source.id, reverse = true)).state
        assertEquals(listOf("route-a", "route-copy"), state.savedRoutes.map { it.id })
        assertEquals(source.waypoints.reversed(), state.savedRoutes.last().waypoints)
        assertEquals(source.waypoints, state.savedRoutes.first().waypoints)

        state = reducer.reduce(state, MapAction.BeginRoutePlanEdit(source.id)).state
        state = reducer.reduce(state, MapAction.ReverseRoute).state
        state = reducer.reduce(state, MapAction.SaveRoutePlanAsCopy("Return copy")).state
        assertEquals(listOf("route-a", "route-copy", "route-save-as"), state.savedRoutes.map { it.id })
        assertEquals(source.revision, state.savedRoutes.first().revision)
    }

    @Test
    fun `revision conflict and write failure keep the draft and never silently overwrite the plan`() {
        val source = routePlan("route-a", revision = 2L)
        val reducer = reducerWithIds("draft-edit")
        var state = reducer.reduce(
            MapState(savedRoutes = listOf(source), libraryLoadState = MapLibraryLoadState.READY),
            MapAction.BeginRoutePlanEdit(source.id),
        ).state
        val changedElsewhere = source.copy(revision = 3L, name = "Other writer")
        state = state.copy(savedRoutes = listOf(changedElsewhere))
        val conflict = reducer.reduce(state, MapAction.SaveRoutePlan)
        assertEquals(state, conflict.state)
        assertTrue((conflict.effects.single() as MapEffect.LogIncident).incident is MapIncident.RouteRevisionConflict)

        val retryable = state.copy(savedRoutes = listOf(source))
        val saving = reducer.reduce(retryable, MapAction.SaveRoutePlan).state
        val failed = reducer.reduce(
            saving,
            MapAction.PersistenceFailed(saving.libraryRevision, MapReadFailure.IO),
        ).state
        assertEquals(source, failed.savedRoutes.single())
        assertEquals("draft-edit", failed.routeDraft?.id)
        assertEquals(MapSaveState.FAILED, failed.routeSaveStatus?.state)
    }

    @Test
    fun `discard delete and revision safe undo affect only the selected route objects`() {
        val first = routePlan("route-a", revision = 2L)
        val second = routePlan("route-b", revision = 8L).copy(name = "Keep")
        val reducer = reducerWithIds("draft-edit")
        var state = MapState(savedRoutes = listOf(first, second), libraryLoadState = MapLibraryLoadState.READY)
        state = reducer.reduce(state, MapAction.BeginRoutePlanEdit(first.id)).state
        state = reducer.reduce(state, MapAction.DiscardRouteDraft("draft-edit")).state
        assertTrue(state.routeDrafts.isEmpty())
        assertEquals(listOf(first, second), state.savedRoutes)

        state = reducer.reduce(state, MapAction.RequestDeleteRoutePlan(first.id)).state
        state = reducer.reduce(state, MapAction.ConfirmDeleteRoutePlan).state
        assertEquals(listOf(second), state.savedRoutes)
        state = reducer.reduce(state, MapAction.UndoDeleteRoutePlan).state
        assertEquals(setOf("route-a", "route-b"), state.savedRoutes.map { it.id }.toSet())
    }

    @Test
    fun `chart source and position changes cannot mutate a preview and never emit navigation`() {
        val plan = routePlan("route-a", revision = 2L)
        val reducer = reducerWithIds()
        var state = reducer.reduce(
            MapState(savedRoutes = listOf(plan), libraryLoadState = MapLibraryLoadState.READY),
            MapAction.PreviewRoutePlan(plan.id),
        ).state
        val before = state.savedRoutes
        state = reducer.reduce(state, MapAction.PositionUnavailable).state
        state = reducer.reduce(state, MapAction.ChartPackagesChanged(emptyList())).state

        assertEquals(before, state.savedRoutes)
        assertEquals(plan.waypoints, state.visibleRoutePoints)
        assertFalse(state.navigationActive)
    }

    @Test
    fun `route legs expose WGS84 distance and initial true bearing without calling it steering`() {
        val reducer = reducerWithIds("draft-a")
        var state = reducer.reduce(MapState(), MapAction.CreateRouteDraft("A", "", a)).state
        state = reducer.reduce(state, MapAction.AddRouteWaypoint(b)).state
        state = reducer.reduce(state, MapAction.AddRouteWaypoint(c)).state

        assertEquals(2, state.routeLegs.size)
        assertTrue(state.routeLegs.all { it.distanceMeters > 0.0 })
        assertTrue(state.routeLegs.all { it.initialBearingTrueDegrees != null })
        assertEquals(
            state.routeLegs.sumOf { it.distanceMeters } / METERS_PER_NAUTICAL_MILE,
            state.routeSummary?.distanceNauticalMiles ?: 0.0,
            1e-9,
        )
    }

    @Test
    fun `route save ack finalizes its transaction while a newer unrelated library write remains pending`() {
        val reducer = reducerWithIds("draft-a", "route-a", "place-a")
        var state = reducer.reduce(MapState(), MapAction.CreateRouteDraft("A", "", a)).state
        state = reducer.reduce(state, MapAction.AddRouteWaypoint(b)).state
        state = reducer.reduce(state, MapAction.SaveRoutePlan).state
        val routeRevision = state.libraryRevision
        state = reducer.reduce(
            state,
            MapAction.CreatePlace(c, "Place", "", PlaceCategory.PERSONAL_MARKER, emptyList()),
        ).state
        assertTrue(state.libraryRevision > routeRevision)

        val acknowledged = reducer.reduce(state, MapAction.PersistenceAck(routeRevision)).state

        assertNull(acknowledged.routeSaveTransaction)
        assertEquals(MapSaveState.SAVED, acknowledged.routeSaveStatus?.state)
        assertEquals(MapSaveState.PENDING, acknowledged.saveState)
    }

    private fun routePlan(id: String, revision: Long) = RoutePlan(
        id = id,
        name = "Plan",
        waypoints = listOf(a, b),
        plannedSpeedKnots = null,
        revision = revision,
        notes = "Notes",
        waypointIds = listOf("$id-a", "$id-b"),
    )

    private fun reducerWithIds(vararg ids: String): DefaultMapReducer {
        val queue = ArrayDeque(ids.toList())
        return DefaultMapReducer(MapIdGenerator { namespace -> queue.removeFirstOrNull() ?: "$namespace-fallback" })
    }
}

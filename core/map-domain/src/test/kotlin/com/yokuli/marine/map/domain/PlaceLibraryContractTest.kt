package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceLibraryContractTest {
    private val first = GeoPoint(-36.8485, 174.7633)
    private val moved = GeoPoint(-36.8400, 174.7700)
    private val clock = object : MapClock {
        var now = 1_700_000_000_000L
        override fun nowMillis(): Long = now
    }
    private val reducer = DefaultMapReducer(
        idGenerator = MapIdGenerator { "place-stable" },
        clock = clock,
    )

    @Test
    fun `create and update preserve complete place identity metadata and object save truth`() {
        val created = reducer.reduce(
            MapState(),
            MapAction.CreatePlace(
                point = first,
                name = "  Westhaven  ",
                notes = "Gate B · 夜间",
                category = PlaceCategory.MARINA,
                tags = listOf("Fuel", " 补水 ", "Fuel", ""),
            ),
        )

        val place = created.state.places.single()
        assertEquals("place-stable", place.id)
        assertEquals("Westhaven", place.name)
        assertEquals("Gate B · 夜间", place.notes)
        assertEquals(PlaceCategory.MARINA, place.category)
        assertEquals(listOf("Fuel", "补水"), place.tags)
        assertEquals(clock.now, place.createdAtMillis)
        assertEquals(clock.now, place.updatedAtMillis)
        assertEquals(1L, place.revision)
        assertEquals(PlaceSaveStatus("place-stable", 1L, MapSaveState.PENDING), created.state.placeSaveStatus)
        assertEquals(place, (created.effects.single() as MapEffect.PersistLibrary).snapshot.places.single())

        val acked = reducer.reduce(created.state, MapAction.PersistenceAck(created.state.libraryRevision)).state
        assertEquals(PlaceSaveStatus("place-stable", 1L, MapSaveState.SAVED), acked.placeSaveStatus)

        clock.now += 10_000L
        val updated = reducer.reduce(
            acked,
            MapAction.UpdatePlace(
                placeId = place.id,
                expectedRevision = place.revision,
                name = "Westhaven Marina",
                notes = "Fuel pontoon",
                category = PlaceCategory.WATER,
                tags = listOf("water"),
            ),
        ).state
        assertEquals(place.createdAtMillis, updated.places.single().createdAtMillis)
        assertEquals(clock.now, updated.places.single().updatedAtMillis)
        assertEquals(2L, updated.places.single().revision)
        assertEquals(PlaceSaveStatus("place-stable", 2L, MapSaveState.PENDING), updated.placeSaveStatus)
    }

    @Test
    fun `ordinary camera changes cannot move a place and explicit move can cancel or confirm once`() {
        val place = savedPlace()
        val base = MapState(places = listOf(place), libraryLoadState = MapLibraryLoadState.READY)
        val panned = reducer.reduce(base, MapAction.CameraChanged(MapCamera(moved, 12.0))).state
        assertEquals(first, panned.places.single().point)

        val editing = reducer.reduce(panned, MapAction.BeginPlaceMove(place.id)).state
        val preview = reducer.reduce(editing, MapAction.PreviewPlaceMove(moved)).state
        assertEquals(first, preview.places.single().point)
        assertEquals(moved, preview.placeMove?.candidatePoint)
        val cancelled = reducer.reduce(preview, MapAction.CancelPlaceMove).state
        assertEquals(first, cancelled.places.single().point)
        assertNull(cancelled.placeMove)

        val confirmed = reducer.reduce(
            reducer.reduce(cancelled, MapAction.BeginPlaceMove(place.id)).state,
            MapAction.PreviewPlaceMove(moved),
        ).let { reducer.reduce(it.state, MapAction.ConfirmPlaceMove) }
        assertEquals(moved, confirmed.state.places.single().point)
        assertEquals(4L, confirmed.state.places.single().revision)
        assertEquals(1, confirmed.effects.filterIsInstance<MapEffect.PersistLibrary>().size)
    }

    @Test
    fun `deleting a referenced place preserves route snapshots and reports missing provenance`() {
        val place = savedPlace()
        val route = SavedRoute(
            id = "route-a",
            name = "harbour",
            waypoints = listOf(first, moved),
            plannedSpeedKnots = 5.0,
            waypointPlaceReferences = mapOf(0 to PlaceRevisionReference(place.id, place.revision)),
        )
        val state = MapState(
            places = listOf(place),
            savedRoutes = listOf(route),
            libraryLoadState = MapLibraryLoadState.READY,
        )

        val requested = reducer.reduce(state, MapAction.RequestDeletePlace(place.id)).state
        assertEquals(1, requested.placeDeleteRequest?.referencingRouteCount)
        val deleted = reducer.reduce(requested, MapAction.ConfirmDeletePlace).state

        assertTrue(deleted.places.isEmpty())
        assertEquals(route.waypoints, deleted.savedRoutes.single().waypoints)
        assertEquals(RoutePlaceSourceState.MISSING, deleted.savedRoutes.single().placeSourceState(0, deleted.places))
        assertEquals(place.id, deleted.placeDeleteUndo?.place?.id)
    }

    @Test
    fun `delete undo restores the same id only before a conflicting library revision`() {
        val place = savedPlace()
        val base = MapState(places = listOf(place), libraryLoadState = MapLibraryLoadState.READY)
        val deleted = reducer.reduce(
            reducer.reduce(base, MapAction.RequestDeletePlace(place.id)).state,
            MapAction.ConfirmDeletePlace,
        ).state
        val restored = reducer.reduce(deleted, MapAction.UndoDeletePlace).state
        assertEquals(place.id, restored.places.single().id)
        assertEquals(place.revision + 1L, restored.places.single().revision)

        val deletedAgain = reducer.reduce(
            reducer.reduce(restored, MapAction.RequestDeletePlace(place.id)).state,
            MapAction.ConfirmDeletePlace,
        ).state
        val conflicting = deletedAgain.copy(libraryRevision = deletedAgain.libraryRevision + 1L)
        val rejected = reducer.reduce(conflicting, MapAction.UndoDeletePlace)
        assertTrue(rejected.state.places.isEmpty())
        assertFalse(rejected.effects.isEmpty())
    }

    @Test
    fun `stale place revision cannot overwrite a newer edit`() {
        val place = savedPlace()
        val state = MapState(places = listOf(place), libraryLoadState = MapLibraryLoadState.READY)
        val rejected = reducer.reduce(
            state,
            MapAction.UpdatePlace(place.id, place.revision - 1L, "stale", "", PlaceCategory.PERSONAL_MARKER, emptyList()),
        )
        assertEquals(place, rejected.state.places.single())
        assertTrue(rejected.effects.single() is MapEffect.LogIncident)
    }

    private fun savedPlace() = SavedPlace(
        id = "place-a",
        name = "锚地 A",
        point = first,
        notes = "sheltered",
        category = PlaceCategory.ANCHORAGE,
        tags = listOf("夜间"),
        createdAtMillis = clock.now - 1_000L,
        updatedAtMillis = clock.now,
        revision = 3L,
    )
}

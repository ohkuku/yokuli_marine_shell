package com.yokuli.marine.navigation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLibraryEditorTest {
    @Test
    fun `crud requires exact entity revisions and advances library once`() {
        val original = waypoint("harbour", 1L)
        val library = NavigationLibrary(revision = 4L, waypoints = listOf(original))

        val stale = NavigationLibraryEditor.apply(
            library,
            NavigationLibraryChange.PutWaypoint(original.copy(name = "stale", revision = 1L)),
        )
        val updated = NavigationLibraryEditor.apply(
            library,
            NavigationLibraryChange.PutWaypoint(original.copy(name = "Harbour", revision = 2L)),
        ) as NavigationChangeResult.Applied
        val deleted = NavigationLibraryEditor.apply(
            updated.library,
            NavigationLibraryChange.RemoveWaypoint(original.id, 2L),
        ) as NavigationChangeResult.Applied

        assertEquals(NavigationChangeRejection.ITEM_CONFLICT, (stale as NavigationChangeResult.Rejected).reason)
        assertEquals(5L, updated.library.revision)
        assertEquals("Harbour", updated.library.waypoints.single().name)
        assertEquals(6L, deleted.library.revision)
        assertTrue(deleted.library.waypoints.isEmpty())
    }

    @Test
    fun `GPX import is atomic and duplicate digest cannot partially append`() {
        val receipt = GpxImportReceipt("import-1", "a".repeat(64), 100L)
        val change = NavigationLibraryChange.ImportGpx(
            waypoints = listOf(waypoint("gpx-point", 1L)),
            routePlans = listOf(route("gpx-route", 1L)),
            tracks = listOf(track("gpx-track", 1L, receipt.sha256)),
            receipt = receipt,
        )
        val first = NavigationLibraryEditor.apply(NavigationLibrary(revision = 9L), change) as NavigationChangeResult.Applied
        val duplicate = NavigationLibraryEditor.apply(first.library, change)
        val copy = NavigationLibraryEditor.apply(
            first.library,
            change.copy(
                waypoints = listOf(waypoint("gpx-point-copy", 1L)),
                routePlans = listOf(route("gpx-route-copy", 1L)),
                tracks = listOf(track("gpx-track-copy", 1L, receipt.sha256)),
                receipt = receipt.copy(id = "import-copy"),
                mode = GpxImportMode.IMPORT_AS_COPY,
            ),
        ) as NavigationChangeResult.Applied

        assertEquals(10L, first.library.revision)
        assertEquals(1, first.library.waypoints.size)
        assertEquals(1, first.library.routePlans.size)
        assertEquals(1, first.library.importedTracks.size)
        assertEquals(NavigationChangeRejection.DUPLICATE_IMPORT, (duplicate as NavigationChangeResult.Rejected).reason)
        assertEquals(10L, first.library.revision)
        assertEquals(11L, copy.library.revision)
        assertEquals(2, copy.library.gpxImports.size)
    }

    private fun waypoint(id: String, revision: Long) = Waypoint(
        id,
        revision,
        id,
        NavigationPosition(-36.8, 174.7),
    )

    private fun route(id: String, revision: Long) = RoutePlan(
        id,
        revision,
        id,
        listOf(
            RoutePoint("$id-a", NavigationPosition(-36.8, 174.7)),
            RoutePoint("$id-b", NavigationPosition(-36.7, 174.8)),
        ),
    )

    private fun track(id: String, revision: Long, digest: String) = NavigationTrack(
        id,
        revision,
        id,
        segments = listOf(NavigationTrackSegment(listOf(NavigationTrackPoint(NavigationPosition(-36.8, 174.7))))),
        sourceDigest = digest,
        importedAtMillis = 100L,
    )
}

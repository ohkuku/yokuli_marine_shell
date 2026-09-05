package com.yokuli.marine.map.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRecoveryExportTest {
    @Test
    fun `recovery document keeps ids revisions coordinates and escapes private text`() {
        val snapshot = MapLibrarySnapshot(
            revision = 12L,
            places = listOf(
                SavedPlace("place-stable", "Harbour \"A\"\nprivate", GeoPoint(-36.8, 174.7), revision = 4L),
            ),
            routeDrafts = listOf(
                ManualRouteDraft(
                    id = "draft-stable",
                    revision = 7L,
                    name = "draft",
                    waypoints = listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.9, 174.8)),
                    undo = listOf(listOf(GeoPoint(0.0, 0.0))),
                ),
            ),
            savedRoutes = listOf(
                SavedRoute(
                    id = "route-stable",
                    name = "route",
                    waypoints = listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.9, 174.8)),
                    plannedSpeedKnots = 5.0,
                    revision = 3L,
                    sourceDraftId = "draft-stable",
                    sourceDraftRevision = 7L,
                ),
            ),
        )

        val document = MapRecoveryExport.encode(snapshot).toString(Charsets.UTF_8)

        assertTrue(document.startsWith("{\n"))
        assertTrue(document.endsWith("}\n"))
        assertTrue(document.contains("\"libraryRevision\": 12"))
        assertTrue(document.contains("\"id\":\"place-stable\""))
        assertTrue(document.contains("Harbour \\\"A\\\"\\nprivate"))
        assertTrue(document.contains("\"sourceDraftRevision\":7"))
        assertTrue(document.contains("\"latitude\":-36.8,\"longitude\":174.7"))
        assertFalse(document.contains("\"undo\""))
        assertFalse(document.contains("\"redo\""))
    }
}

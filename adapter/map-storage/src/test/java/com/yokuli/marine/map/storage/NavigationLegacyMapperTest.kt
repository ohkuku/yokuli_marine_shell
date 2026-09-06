package com.yokuli.marine.map.storage

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.GpxDuplicateDecision
import com.yokuli.marine.map.domain.GpxImportPlanner
import com.yokuli.marine.map.domain.GpxReader
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapIdGenerator
import com.yokuli.marine.map.domain.MapLibrarySnapshot
import com.yokuli.marine.map.domain.PlaceCategory
import com.yokuli.marine.map.domain.PlaceRevisionReference
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLegacyMapperTest {
    @Test
    fun `legacy place draft plan references and metadata round trip without schema rewrite`() {
        val point = GeoPoint(-36.8, 174.7)
        val original = MapLibrarySnapshot(
            revision = 7L,
            places = listOf(
                SavedPlace(
                    "place",
                    "码头",
                    point,
                    revision = 3L,
                    notes = "night entrance",
                    category = PlaceCategory.MARINA,
                    tags = listOf("fuel"),
                    createdAtMillis = 10L,
                    updatedAtMillis = 20L,
                ),
            ),
            routeDrafts = listOf(
                ManualRouteDraft(
                    id = "draft",
                    revision = 2L,
                    name = "draft",
                    waypoints = listOf(point),
                    waypointIds = listOf("draft-point"),
                    waypointPlaceReferences = mapOf(0 to PlaceRevisionReference("place", 3L)),
                ),
            ),
            savedRoutes = listOf(
                SavedRoute(
                    id = "route",
                    revision = 4L,
                    name = "route",
                    waypoints = listOf(point),
                    waypointIds = listOf("route-point"),
                    waypointPlaceReferences = mapOf(0 to PlaceRevisionReference("place", 3L)),
                ),
            ),
        )

        assertEquals(original, NavigationLegacyMapper.toLegacy(NavigationLegacyMapper.toNavigation(original)))
    }

    @Test
    fun `existing GPX parser output is readable by Navigation without changing its digest or segments`() {
        val preview = GpxReader().inspect(
            """<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><wpt lat="-36.8" lon="174.7"><name>泊位</name></wpt><rte><name>route</name><rtept lat="-36.8" lon="174.7"/><rtept lat="-36.7" lon="174.8"/></rte><trk><name>track</name><trkseg><trkpt lat="-36.8" lon="174.7"/></trkseg></trk></gpx>""".byteInputStream(),
        )
        var ordinal = 0
        val batch = GpxImportPlanner.materialize(
            preview,
            GpxDuplicateDecision.NEW_IMPORT,
            MapIdGenerator { namespace -> "$namespace-${++ordinal}" },
            100L,
        )
        val navigation = NavigationLegacyMapper.toNavigation(
            MapLibrarySnapshot(
                revision = 1L,
                places = batch.places,
                savedRoutes = batch.routes,
                importedTracks = batch.tracks,
                gpxImportRecords = listOf(batch.importRecord),
            ),
        )

        assertEquals("泊位", navigation.waypoints.single().name)
        assertEquals(2, navigation.routePlans.single().points.size)
        assertEquals(1, navigation.importedTracks.single().segments.size)
        assertEquals(preview.sha256, navigation.gpxImports.single().sha256)
    }
}

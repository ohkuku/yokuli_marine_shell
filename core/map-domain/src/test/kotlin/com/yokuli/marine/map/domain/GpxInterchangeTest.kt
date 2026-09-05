package com.yokuli.marine.map.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class GpxInterchangeTest {
    @Test
    fun `mixed GPX maps waypoints routes and separated read-only track segments`() {
        val preview = GpxReader().inspect(mixedGpx().byteInputStream())

        assertEquals("泊位 α", preview.waypoints.single().name)
        assertEquals(2, preview.routes.single().points.size)
        assertEquals(2, preview.tracks.single().segments.size)
        assertEquals(2, preview.tracks.single().segments.first().points.size)
        assertEquals("2026-01-02T03:04:05Z", preview.tracks.single().segments.first().points.first().time)
        assertEquals(7, preview.totalPointCount)
        assertFalse(preview.duplicate)
    }

    @Test
    fun `preview is non mutating and duplicate requires explicit import as copy`() {
        val preview = GpxReader().inspect(mixedGpx().byteInputStream())
        val ids = object : MapIdGenerator {
            var ordinal = 0
            override fun nextId(namespace: String) = "$namespace-${++ordinal}"
        }
        val first = GpxImportPlanner.materialize(preview, GpxDuplicateDecision.NEW_IMPORT, ids, 100L)
        val duplicate = GpxReader().inspect(mixedGpx().byteInputStream(), setOf(preview.sha256))

        assertTrue(duplicate.duplicate)
        assertTrue(runCatching {
            GpxImportPlanner.materialize(duplicate, GpxDuplicateDecision.NEW_IMPORT, ids, 200L)
        }.isFailure)
        val copy = GpxImportPlanner.materialize(duplicate, GpxDuplicateDecision.IMPORT_AS_COPY, ids, 200L)
        assertTrue(first.places.single().id != copy.places.single().id)
        assertTrue(first.routes.single().id != copy.routes.single().id)
        assertTrue(first.tracks.single().id != copy.tracks.single().id)
    }

    @Test
    fun `single point route is warned and never materialized as a runnable plan`() {
        val preview = GpxReader().inspect(
            gpx("<rte><name>one</name><rtept lat=\"-36\" lon=\"174\"/></rte>").byteInputStream(),
        )
        val batch = GpxImportPlanner.materialize(
            preview,
            GpxDuplicateDecision.NEW_IMPORT,
            MapIdGenerator { "id-$it" },
            1L,
        )

        assertTrue(GpxWarning.ROUTE_HAS_FEWER_THAN_TWO_POINTS in preview.warnings)
        assertTrue(batch.routes.isEmpty())
    }

    @Test
    fun `DTD depth text point budget and illegal coordinates fail without truncation`() {
        val reader = GpxReader(GpxLimits(maxTotalPoints = 2, maxDepth = 5, maxTextChars = 8))
        listOf(
            "<!DOCTYPE gpx [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\"><wpt lat=\"0\" lon=\"0\"><name>&xxe;</name></wpt></gpx>",
            gpx("<metadata><extensions><a><b><c><d/></c></b></a></extensions></metadata>"),
            gpx("<wpt lat=\"0\" lon=\"0\"><name>123456789</name></wpt>"),
            gpx("<wpt lat=\"0\" lon=\"0\"/><wpt lat=\"1\" lon=\"1\"/><wpt lat=\"2\" lon=\"2\"/>"),
            gpx("<wpt lat=\"NaN\" lon=\"0\"/>"),
            gpx("<wpt lat=\"91\" lon=\"0\"/>"),
        ).forEach { document -> assertTrue(runCatching { reader.inspect(document.byteInputStream()) }.isFailure) }
    }

    @Test
    fun `track export remains trk trkseg and independently round trips supported facts`() {
        val original = GpxReader().inspect(mixedGpx().byteInputStream()).tracks.single()
        val output = ByteArrayOutputStream()
        GpxWriter.writeTrack(original, output)
        val text = output.toString(Charsets.UTF_8.name())
        val reparsed = GpxReader().inspect(ByteArrayInputStream(output.toByteArray())).tracks.single()

        assertTrue(text.contains("<trk>"))
        assertTrue(text.contains("<trkseg>"))
        assertFalse(text.contains("<rte>"))
        assertEquals(original.segments, reparsed.segments)
        assertEquals(original.name, reparsed.name)
    }

    @Test
    fun `display LOD preserves segment identities and never mutates imported source points`() {
        val source = (0..100_000).map { index ->
            ImportedTrackPoint(GeoPoint(-40.0 + index / 1_000_000.0, 174.0))
        }
        val track = ImportedTrack(
            "track", "large", segments = listOf(
                ImportedTrackSegment(source),
                ImportedTrackSegment(listOf(ImportedTrackPoint(GeoPoint(-30.0, 170.0)))),
            ),
            sourceDigest = "a".repeat(64), importedAtMillis = 1L,
        )

        val sampled = ImportedTrackDisplayLod.sample(track, zoom = 5.0)

        assertEquals(2, sampled.size)
        assertTrue(sampled.first().points.size < source.size)
        assertEquals(source.first(), sampled.first().points.first())
        assertEquals(source.last(), sampled.first().points.last())
        assertEquals(100_001, track.segments.first().points.size)
    }

    @Test
    fun `track output is independently readable XML with the GPX 1_1 namespace`() {
        val original = GpxReader().inspect(mixedGpx().byteInputStream()).tracks.single()
        val output = ByteArrayOutputStream()
        GpxWriter.writeTrack(original, output)
        val parser = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()

        val document = parser.parse(ByteArrayInputStream(output.toByteArray()))

        assertEquals("http://www.topografix.com/GPX/1/1", document.documentElement.namespaceURI)
        assertEquals("1.1", document.documentElement.getAttribute("version"))
        assertEquals(1, document.getElementsByTagNameNS("*", "trk").length)
        assertEquals(2, document.getElementsByTagNameNS("*", "trkseg").length)
        assertEquals(4, document.getElementsByTagNameNS("*", "trkpt").length)
        assertEquals(0, document.getElementsByTagNameNS("*", "rte").length)
    }

    @Test
    fun `confirmed GPX batch is one persistence effect and duplicate IDs cannot partially append`() {
        val preview = GpxReader().inspect(mixedGpx().byteInputStream())
        var ordinal = 0
        val batch = GpxImportPlanner.materialize(
            preview,
            GpxDuplicateDecision.NEW_IMPORT,
            MapIdGenerator { namespace -> "$namespace-${++ordinal}" },
            100L,
        )
        val reducer = DefaultMapReducer()
        val initial = MapState(libraryLoadState = MapLibraryLoadState.READY_EMPTY, libraryRevision = 4L)

        val imported = reducer.reduce(initial, MapAction.ImportGpxBatch(batch))

        assertEquals(5L, imported.state.libraryRevision)
        assertEquals(MapSaveState.PENDING, imported.state.saveState)
        assertEquals(batch.places, imported.state.places)
        assertEquals(batch.routes, imported.state.savedRoutes)
        assertEquals(batch.tracks, imported.state.importedTracks)
        assertEquals(batch.importRecord, imported.state.gpxImportRecords.single())
        assertEquals(1, imported.effects.filterIsInstance<MapEffect.PersistLibrary>().size)

        val collision = reducer.reduce(imported.state, MapAction.ImportGpxBatch(batch))
        assertEquals(imported.state, collision.state)
        assertTrue(collision.effects.single() is MapEffect.LogIncident)
    }

    private fun mixedGpx() = gpx(
        """
        <wpt lat="-36.8" lon="174.7"><name>泊位 α</name><desc>备注</desc></wpt>
        <rte><name>Harbour</name><rtept lat="-36.8" lon="174.7"/><rtept lat="-36.9" lon="174.8"/></rte>
        <trk><name>Track</name><trkseg>
          <trkpt lat="-36.8" lon="174.7"><ele>2.5</ele><time>2026-01-02T03:04:05Z</time></trkpt>
          <trkpt lat="-36.9" lon="174.8"/>
        </trkseg><trkseg><trkpt lat="-40" lon="178"/><trkpt lat="-41" lon="179"/></trkseg></trk>
        """.trimIndent(),
    )

    private fun gpx(body: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"test\" xmlns=\"http://www.topografix.com/GPX/1/1\">$body</gpx>"
}

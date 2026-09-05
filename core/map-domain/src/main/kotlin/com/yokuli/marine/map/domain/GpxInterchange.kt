package com.yokuli.marine.map.domain

import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.stream.XMLOutputFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

data class GpxLimits(
    val maxFileBytes: Long = MAX_FILE_BYTES,
    val maxTotalPoints: Int = MAX_TOTAL_POINTS,
    val maxRoutePoints: Int = MAX_ROUTE_POINTS,
    val maxTextChars: Int = MAX_TEXT_CHARS,
    val maxDepth: Int = MAX_XML_DEPTH,
) {
    init {
        require(maxFileBytes > 0L)
        require(maxTotalPoints > 0)
        require(maxRoutePoints > 0)
        require(maxTextChars > 0)
        require(maxDepth > 0)
    }

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024L * 1024L
        const val MAX_TOTAL_POINTS = 200_000
        const val MAX_ROUTE_POINTS = 2_000
        const val MAX_TEXT_CHARS = 8 * 1024
        const val MAX_XML_DEPTH = 32
    }
}

data class GpxWaypoint(
    val point: GeoPoint,
    val name: String = "",
    val description: String = "",
    val time: String? = null,
)

data class ImportedTrackPoint(
    val point: GeoPoint,
    val elevationMeters: Double? = null,
    val time: String? = null,
)

data class ImportedTrackSegment(val points: List<ImportedTrackPoint>)

data class GpxRoute(
    val name: String = "",
    val description: String = "",
    val points: List<GpxWaypoint>,
)

data class GpxTrack(
    val name: String = "",
    val description: String = "",
    val segments: List<ImportedTrackSegment>,
)

enum class ImportedTrackEditability { READ_ONLY }

data class ImportedTrack(
    val id: String,
    val name: String,
    val description: String = "",
    val segments: List<ImportedTrackSegment>,
    val sourceDigest: String,
    val importedAtMillis: Long,
    val revision: Long = 1L,
    val editability: ImportedTrackEditability = ImportedTrackEditability.READ_ONLY,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(segments.isNotEmpty())
        require(segments.all { it.points.isNotEmpty() })
        require(sourceDigest.matches(Regex("[0-9a-f]{64}")))
        require(importedAtMillis >= 0L)
        require(revision > 0L)
    }
}

data class GpxImportRecord(
    val id: String,
    val sha256: String,
    val importedAtMillis: Long,
) {
    init {
        require(id.isNotBlank())
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(importedAtMillis >= 0L)
    }
}

enum class GpxWarning {
    ROUTE_HAS_FEWER_THAN_TWO_POINTS,
    UNKNOWN_EXTENSIONS_NOT_PRESERVED,
    INVALID_OPTIONAL_TIME_OMITTED,
    EMPTY_TRACK_OMITTED,
}

data class GpxImportPreview(
    val sha256: String,
    val waypoints: List<GpxWaypoint>,
    val routes: List<GpxRoute>,
    val tracks: List<GpxTrack>,
    val warnings: Set<GpxWarning>,
    val duplicate: Boolean,
    val totalPointCount: Int,
    val bounds: GeoBounds?,
)

enum class GpxDuplicateDecision { NEW_IMPORT, IMPORT_AS_COPY }

data class GpxImportSelection(
    val waypointIndices: Set<Int>,
    val routeIndices: Set<Int>,
    val trackIndices: Set<Int>,
) {
    companion object {
        fun all(preview: GpxImportPreview) = GpxImportSelection(
            waypointIndices = preview.waypoints.indices.toSet(),
            routeIndices = preview.routes.indices.toSet(),
            trackIndices = preview.tracks.indices.toSet(),
        )
    }
}

data class GpxImportBatch(
    val places: List<SavedPlace>,
    val routes: List<SavedRoute>,
    val tracks: List<ImportedTrack>,
    val importRecord: GpxImportRecord,
)

class GpxReadException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class GpxReader(private val limits: GpxLimits = GpxLimits()) {
    fun inspect(input: InputStream, existingDigests: Set<String> = emptySet()): GpxImportPreview {
        val digest = MessageDigest.getInstance("SHA-256")
        val bounded = BoundedInputStream(input, limits.maxFileBytes)
        val digested = DigestInputStream(bounded, digest)
        val declarationChecked = ForbiddenXmlDeclarationInputStream(digested)
        val handler = GpxHandler(limits)
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
                runCatching { isXIncludeAware = false }
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            }
            factory.newSAXParser().xmlReader.apply {
                // Android and desktop parsers expose different subsets. The streaming declaration
                // guard below is mandatory; these parser flags are defence in depth.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                entityResolver = org.xml.sax.EntityResolver { _, _ ->
                    throw SAXException("External XML entities are disabled")
                }
                contentHandler = handler
                errorHandler = handler
            }.parse(InputSource(declarationChecked))
        } catch (error: Exception) {
            if (error is GpxReadException) throw error
            throw GpxReadException(error.message ?: "Invalid GPX document", error)
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        return handler.preview(sha256, sha256 in existingDigests)
    }
}

/** Mandatory streaming guard independent of optional SAX feature support on Android. */
private class ForbiddenXmlDeclarationInputStream(input: InputStream) : FilterInputStream(input) {
    private val recent = ArrayDeque<Byte>()

    override fun read(): Int = super.read().also { value -> if (value >= 0) inspect(value.toByte()) }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        super.read(bytes, offset, length).also { count ->
            if (count > 0) for (index in offset until offset + count) inspect(bytes[index])
        }

    private fun inspect(byte: Byte) {
        recent.addLast(byte)
        if (recent.size > FORBIDDEN.size) recent.removeFirst()
        if (recent.size == FORBIDDEN.size && recent.indices.all { recent[it] == FORBIDDEN[it] }) {
            throw GpxReadException("DTD declarations are disabled")
        }
    }

    private companion object {
        val FORBIDDEN = "<!DOCTYPE".encodeToByteArray()
    }
}

private class BoundedInputStream(input: InputStream, private val maximumBytes: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1L) }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        super.read(bytes, offset, length).also { if (it > 0) add(it.toLong()) }

    private fun add(amount: Long) {
        count += amount
        if (count > maximumBytes) throw GpxReadException("GPX exceeds the file-size limit")
    }
}

private class GpxHandler(private val limits: GpxLimits) : DefaultHandler() {
    private data class MutableWaypoint(
        val point: GeoPoint,
        var name: String = "",
        var description: String = "",
        var elevationMeters: Double? = null,
        var time: String? = null,
    )

    private data class MutableRoute(
        var name: String = "",
        var description: String = "",
        val points: MutableList<GpxWaypoint> = mutableListOf(),
    )

    private data class MutableTrack(
        var name: String = "",
        var description: String = "",
        val segments: MutableList<ImportedTrackSegment> = mutableListOf(),
    )

    private val path = mutableListOf<String>()
    private val waypoints = mutableListOf<GpxWaypoint>()
    private val routes = mutableListOf<GpxRoute>()
    private val tracks = mutableListOf<GpxTrack>()
    private val warnings = linkedSetOf<GpxWarning>()
    private val allPoints = mutableListOf<GeoPoint>()
    private var text: StringBuilder? = null
    private var waypoint: MutableWaypoint? = null
    private var route: MutableRoute? = null
    private var routePoint: MutableWaypoint? = null
    private var track: MutableTrack? = null
    private var trackSegment: MutableList<ImportedTrackPoint>? = null
    private var trackPoint: MutableWaypoint? = null
    private var pointCount = 0
    private var sawRoot = false

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        val element = localName.ifBlank { qName.substringAfter(':') }
        path += element
        if (path.size > limits.maxDepth) fail("GPX XML nesting is too deep")
        if (!sawRoot) {
            sawRoot = true
            if (element != "gpx" || uri != GPX_NAMESPACE || attributes.getValue("version") != "1.1") {
                fail("Only GPX 1.1 documents are supported")
            }
        } else if (uri.isNotEmpty() && uri != GPX_NAMESPACE) {
            if (element == "extensions" || "extensions" in path) {
                warnings += GpxWarning.UNKNOWN_EXTENSIONS_NOT_PRESERVED
            } else {
                fail("Unexpected XML namespace")
            }
        }
        when (element) {
            "wpt" -> waypoint = mutablePoint(attributes)
            "rte" -> route = MutableRoute()
            "rtept" -> {
                if (route == null) fail("Route point is outside a route")
                if (route!!.points.size >= limits.maxRoutePoints) fail("Route exceeds the point limit")
                routePoint = mutablePoint(attributes)
            }
            "trk" -> track = MutableTrack()
            "trkseg" -> {
                if (track == null) fail("Track segment is outside a track")
                trackSegment = mutableListOf()
            }
            "trkpt" -> {
                if (trackSegment == null) fail("Track point is outside a track segment")
                trackPoint = mutablePoint(attributes)
            }
            "extensions" -> warnings += GpxWarning.UNKNOWN_EXTENSIONS_NOT_PRESERVED
        }
        if (element in TEXT_ELEMENTS) text = StringBuilder()
    }

    override fun characters(chars: CharArray, start: Int, length: Int) {
        text?.let { buffer ->
            if (buffer.length + length > limits.maxTextChars) fail("GPX text exceeds the character limit")
            buffer.append(chars, start, length)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        val element = localName.ifBlank { qName.substringAfter(':') }
        val value = if (element in TEXT_ELEMENTS) text?.toString()?.trim().orEmpty() else ""
        val parent = path.dropLast(1).lastOrNull()
        when (element) {
            "name" -> when (parent) {
                "wpt" -> waypoint?.name = value
                "rte" -> route?.name = value
                "rtept" -> routePoint?.name = value
                "trk" -> track?.name = value
                "trkpt" -> trackPoint?.name = value
            }
            "desc", "cmt" -> when (parent) {
                "wpt" -> if (waypoint?.description.isNullOrBlank()) waypoint?.description = value
                "rte" -> if (route?.description.isNullOrBlank()) route?.description = value
                "rtept" -> if (routePoint?.description.isNullOrBlank()) routePoint?.description = value
                "trk" -> if (track?.description.isNullOrBlank()) track?.description = value
                "trkpt" -> if (trackPoint?.description.isNullOrBlank()) trackPoint?.description = value
            }
            "ele" -> currentPoint(parent)?.elevationMeters = value.toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: fail("Invalid elevation")
            "time" -> currentPoint(parent)?.time = value.takeIf(::validTime).also {
                if (value.isNotEmpty() && it == null) warnings += GpxWarning.INVALID_OPTIONAL_TIME_OMITTED
            }
            "wpt" -> waypoint?.let { waypoints += it.toWaypoint() }.also { waypoint = null }
            "rtept" -> routePoint?.let { route?.points?.add(it.toWaypoint()) }.also { routePoint = null }
            "rte" -> route?.let {
                if (it.points.size < 2) warnings += GpxWarning.ROUTE_HAS_FEWER_THAN_TWO_POINTS
                routes += GpxRoute(it.name, it.description, it.points.toList())
            }.also { route = null }
            "trkpt" -> trackPoint?.let {
                trackSegment?.add(ImportedTrackPoint(it.point, it.elevationMeters, it.time))
            }.also { trackPoint = null }
            "trkseg" -> trackSegment?.let { points ->
                if (points.isNotEmpty()) track?.segments?.add(ImportedTrackSegment(points.toList()))
            }.also { trackSegment = null }
            "trk" -> track?.let {
                if (it.segments.isEmpty()) {
                    warnings += GpxWarning.EMPTY_TRACK_OMITTED
                } else {
                    tracks += GpxTrack(it.name, it.description, it.segments.toList())
                }
            }.also { track = null }
        }
        if (element in TEXT_ELEMENTS) text = null
        if (path.lastOrNull() != element) fail("Malformed GPX element nesting")
        path.removeAt(path.lastIndex)
    }

    override fun endDocument() {
        if (!sawRoot || path.isNotEmpty()) fail("Incomplete GPX document")
    }

    fun preview(sha256: String, duplicate: Boolean): GpxImportPreview {
        val bounds = allPoints.takeIf(List<GeoPoint>::isNotEmpty)?.let(::minimalBounds)
        return GpxImportPreview(
            sha256 = sha256,
            waypoints = waypoints.toList(),
            routes = routes.toList(),
            tracks = tracks.toList(),
            warnings = warnings.toSet(),
            duplicate = duplicate,
            totalPointCount = pointCount,
            bounds = bounds,
        )
    }

    private fun mutablePoint(attributes: Attributes): MutableWaypoint {
        pointCount += 1
        if (pointCount > limits.maxTotalPoints) fail("GPX exceeds the total point limit")
        val latitude = attributes.getValue("lat")?.toDoubleOrNull() ?: fail("Missing or invalid latitude")
        val longitude = attributes.getValue("lon")?.toDoubleOrNull() ?: fail("Missing or invalid longitude")
        val point = try {
            GeoPoint(latitude, longitude)
        } catch (error: IllegalArgumentException) {
            throw GpxReadException("GPX coordinate is out of range", error)
        }
        allPoints += point
        return MutableWaypoint(point)
    }

    private fun MutableWaypoint.toWaypoint() = GpxWaypoint(point, name, description, time)

    private fun currentPoint(parent: String?): MutableWaypoint? = when (parent) {
        "wpt" -> waypoint
        "rtept" -> routePoint
        "trkpt" -> trackPoint
        else -> null
    }

    private fun validTime(value: String): Boolean = value.isNotEmpty() && runCatching { Instant.parse(value) }.isSuccess

    private fun fail(message: String): Nothing = throw GpxReadException(message)

    companion object {
        private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        private val TEXT_ELEMENTS = setOf("name", "desc", "cmt", "ele", "time")
    }
}

object GpxImportPlanner {
    fun materialize(
        preview: GpxImportPreview,
        decision: GpxDuplicateDecision,
        idGenerator: MapIdGenerator,
        nowMillis: Long,
        selection: GpxImportSelection = GpxImportSelection.all(preview),
    ): GpxImportBatch {
        require(nowMillis >= 0L)
        require(!preview.duplicate || decision == GpxDuplicateDecision.IMPORT_AS_COPY) {
            "Duplicate GPX requires explicit import-as-copy confirmation"
        }
        validateSelection(preview, selection)
        val places = selection.waypointIndices.sorted().map { index ->
            val item = preview.waypoints[index]
            SavedPlace(
                id = idGenerator.nextId("gpx-place"),
                name = item.name.ifBlank { "GPX WPT ${index + 1}" },
                point = item.point,
                notes = item.description,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
            )
        }
        val routes = selection.routeIndices.sorted().mapNotNull { index ->
            val item = preview.routes[index]
            item.takeIf { it.points.size >= 2 }?.let {
                SavedRoute(
                    id = idGenerator.nextId("gpx-route"),
                    name = it.name.ifBlank { "GPX RTE ${index + 1}" },
                    waypoints = it.points.map(GpxWaypoint::point),
                    notes = it.description,
                )
            }
        }
        val tracks = selection.trackIndices.sorted().map { index ->
            val item = preview.tracks[index]
            ImportedTrack(
                id = idGenerator.nextId("gpx-track"),
                name = item.name.ifBlank { "GPX TRK ${index + 1}" },
                description = item.description,
                segments = item.segments,
                sourceDigest = preview.sha256,
                importedAtMillis = nowMillis,
            )
        }
        return GpxImportBatch(
            places = places,
            routes = routes,
            tracks = tracks,
            importRecord = GpxImportRecord(
                id = idGenerator.nextId("gpx-import"),
                sha256 = preview.sha256,
                importedAtMillis = nowMillis,
            ),
        )
    }

    private fun validateSelection(preview: GpxImportPreview, selection: GpxImportSelection) {
        require(selection.waypointIndices.all { it in preview.waypoints.indices })
        require(selection.routeIndices.all { it in preview.routes.indices })
        require(selection.trackIndices.all { it in preview.tracks.indices })
    }
}

/** Display-only decimation. Stored source points and segment boundaries are never rewritten. */
object ImportedTrackDisplayLod {
    fun sample(track: ImportedTrack, zoom: Double): List<ImportedTrackSegment> {
        val pointBudget = when {
            zoom < 6.0 -> 2_000
            zoom < 10.0 -> 8_000
            zoom < 14.0 -> 24_000
            else -> 60_000
        }
        val total = track.segments.sumOf { it.points.size }
        if (total <= pointBudget) return track.segments
        val stride = kotlin.math.ceil(total.toDouble() / pointBudget).toInt().coerceAtLeast(1)
        return track.segments.map { segment ->
            if (segment.points.size <= 2) {
                segment
            } else {
                val sampled = buildList {
                    add(segment.points.first())
                    var index = stride
                    while (index < segment.points.lastIndex) {
                        add(segment.points[index])
                        index += stride
                    }
                    add(segment.points.last())
                }
                ImportedTrackSegment(sampled)
            }
        }
    }
}

object GpxWriter {
    const val MIME_TYPE = "application/gpx+xml"
    // Literal tags make the track/segment contract human-auditable: <trk> and <trkseg>.
    fun writeTrack(track: GpxTrack, output: OutputStream) = writeDocument(output) { writer ->
        writer.writeStartElement("trk")
        writer.writeOptionalText("name", track.name)
        writer.writeOptionalText("desc", track.description)
        track.segments.forEach { segment ->
            writer.writeStartElement("trkseg")
            segment.points.forEach { point -> writer.writeTrackPoint(point) }
            writer.writeEndElement()
        }
        writer.writeEndElement()
    }

    fun writeTrack(track: ImportedTrack, output: OutputStream) = writeTrack(
        GpxTrack(track.name, track.description, track.segments),
        output,
    )

    fun writePlace(place: SavedPlace, output: OutputStream) = writeDocument(output) { writer ->
        writer.writeStartElement("wpt")
        writer.writeAttribute("lat", place.point.latitude.toString())
        writer.writeAttribute("lon", place.point.longitude.toString())
        writer.writeOptionalText("name", place.name)
        writer.writeOptionalText("desc", place.notes)
        writer.writeEndElement()
    }

    fun writeRoute(route: SavedRoute, output: OutputStream) = writeDocument(output) { writer ->
        writer.writeStartElement("rte")
        writer.writeOptionalText("name", route.name)
        writer.writeOptionalText("desc", route.notes)
        route.waypoints.forEach { point ->
            writer.writeStartElement("rtept")
            writer.writeAttribute("lat", point.latitude.toString())
            writer.writeAttribute("lon", point.longitude.toString())
            writer.writeEndElement()
        }
        writer.writeEndElement()
    }

    private inline fun writeDocument(output: OutputStream, body: (javax.xml.stream.XMLStreamWriter) -> Unit) {
        val writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, Charsets.UTF_8.name())
        try {
            writer.writeStartDocument(Charsets.UTF_8.name(), "1.0")
            writer.writeStartElement("gpx")
            writer.writeDefaultNamespace(GPX_NAMESPACE)
            writer.writeAttribute("version", "1.1")
            writer.writeAttribute("creator", "Yokuli OS")
            body(writer)
            writer.writeEndElement()
            writer.writeEndDocument()
            writer.flush()
        } finally {
            writer.close()
        }
    }

    private fun javax.xml.stream.XMLStreamWriter.writeOptionalText(element: String, value: String) {
        if (value.isBlank()) return
        writeStartElement(element)
        writeCharacters(value)
        writeEndElement()
    }

    private fun javax.xml.stream.XMLStreamWriter.writeTrackPoint(point: ImportedTrackPoint) {
        writeStartElement("trkpt")
        writeAttribute("lat", point.point.latitude.toString())
        writeAttribute("lon", point.point.longitude.toString())
        point.elevationMeters?.let { elevation -> writeOptionalText("ele", elevation.toString()) }
        point.time?.let { timestamp -> writeOptionalText("time", timestamp) }
        writeEndElement()
    }

    private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
}

package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

enum class ChartValidationIssue {
    OPEN_FAILED,
    INVALID_METADATA,
    UNKNOWN_SCHEME,
    EMPTY_TILESET,
    INVALID_COORDINATE,
    MIXED_TILE_SIZE,
    MIXED_RASTER_ENCODING,
    DUPLICATE_COORDINATE,
    READ_FAILED,
    REVISION_CHANGED,
    PERMISSION_LOST,
    DIRECT_READ_UNSUPPORTED,
    INVALID_SCHEMA,
    CANCELLED,
}

data class ChartBasicInspection(
    val facts: ChartAssetFacts,
    val sampledTileCount: Int,
    val statistics: ChartReadStatistics,
    val accessMode: ChartReadAccessMode,
    val warnings: Set<ChartCompatibilityWarning> = emptySet(),
)

sealed interface ChartBasicInspectionResult {
    data class Readable(val inspection: ChartBasicInspection) : ChartBasicInspectionResult
    data class Rejected(val issue: ChartValidationIssue, val detail: String) : ChartBasicInspectionResult
}

data class ChartVerificationProgress(
    val tilesVerified: Long,
    val sourceBytesHashed: Long,
    val totalSourceBytes: Long,
) {
    init { require(tilesVerified >= 0L && sourceBytesHashed in 0..totalSourceBytes) }
}

sealed interface ChartFullVerificationResult {
    data class Verified(
        val revision: ChartContentRevision,
        val facts: ChartAssetFacts,
        val statistics: ChartReadStatistics,
        val tileCount: Long,
        val accessMode: ChartReadAccessMode,
        val warnings: Set<ChartCompatibilityWarning> = emptySet(),
    ) : ChartFullVerificationResult
    data class Rejected(val issue: ChartValidationIssue, val detail: String) : ChartFullVerificationResult
    data object Cancelled : ChartFullVerificationResult
}

enum class ChartValidationJobKind { BASIC, FULL }
enum class ChartValidationJobStatus { RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

data class ChartValidationJob(
    val assetId: ChartAssetId,
    val kind: ChartValidationJobKind,
    val status: ChartValidationJobStatus,
    val progress: ChartVerificationProgress? = null,
    val issue: ChartValidationIssue? = null,
)

data class ChartValidationSnapshot(val jobs: Map<ChartAssetId, ChartValidationJob> = emptyMap())

sealed interface ChartValidationCommandResult {
    data class Published(val asset: ChartAsset) : ChartValidationCommandResult
    data class Rejected(val issue: ChartValidationIssue) : ChartValidationCommandResult
    data object Cancelled : ChartValidationCommandResult
}

interface ChartValidationCommandPort {
    val state: StateFlow<ChartValidationSnapshot>
    suspend fun inspectBasic(assetId: ChartAssetId): ChartValidationCommandResult
    suspend fun verifyFull(assetId: ChartAssetId): ChartValidationCommandResult
    fun cancel(assetId: ChartAssetId)
}

fun interface ChartRevisionProbe {
    suspend fun currentRevision(asset: ChartAsset): ChartContentRevision?
}

/** One mapping contract is shared by rendering, coverage and validation. */
object ChartTileCoordinateMapper {
    fun storageRow(external: ChartTileKey, scheme: MapTileScheme): Long = when (scheme) {
        MapTileScheme.MBTILES_TMS -> (1L shl external.zoom) - 1L - external.row
        MapTileScheme.XYZ -> external.row
    }

    fun externalKey(stored: ChartStoredTileKey, scheme: MapTileScheme): ChartTileKey? {
        if (stored.zoom !in MIN_ZOOM.toLong()..MAX_ZOOM.toLong()) return null
        val z = stored.zoom.toInt()
        val axis = 1L shl z
        if (stored.column !in 0 until axis || stored.storageRow !in 0 until axis) return null
        val externalRow = when (scheme) {
            MapTileScheme.MBTILES_TMS -> axis - 1L - stored.storageRow
            MapTileScheme.XYZ -> stored.storageRow
        }
        return ChartTileKey(z, stored.column, externalRow)
    }
}

/** Coarse coverage from tile truth. Highest valid zoom gives the least inflated rectangular footprint. */
object ChartTileCoverageDeriver {
    fun derive(extents: List<ChartStoredTileExtent>, scheme: MapTileScheme): GeoBounds? = extents
        .asSequence()
        .mapNotNull { it.toExternalBounds(scheme) }
        .maxByOrNull { it.first }
        ?.second

    private fun ChartStoredTileExtent.toExternalBounds(scheme: MapTileScheme): Pair<Int, GeoBounds>? {
        if (zoom !in MIN_ZOOM.toLong()..MAX_ZOOM.toLong()) return null
        val z = zoom.toInt()
        val axis = 1L shl z
        if (minColumn !in 0 until axis || maxColumn !in 0 until axis) return null
        if (minStorageRow !in 0 until axis || maxStorageRow !in 0 until axis) return null
        val minExternalRow: Long
        val maxExternalRow: Long
        when (scheme) {
            MapTileScheme.MBTILES_TMS -> {
                minExternalRow = axis - 1L - maxStorageRow
                maxExternalRow = axis - 1L - minStorageRow
            }
            MapTileScheme.XYZ -> {
                minExternalRow = minStorageRow
                maxExternalRow = maxStorageRow
            }
        }
        val west = minColumn.toDouble() / axis * 360.0 - 180.0
        val east = (maxColumn + 1L).toDouble() / axis * 360.0 - 180.0
        val north = latitudeAtRow(minExternalRow.toDouble(), axis.toDouble())
        val south = latitudeAtRow((maxExternalRow + 1L).toDouble(), axis.toDouble())
        return z to GeoBounds(south = south, west = west, north = north, east = east)
    }

    private fun latitudeAtRow(row: Double, axis: Double): Double =
        Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * row / axis))))
}

class ChartBasicInspector(private val access: ChartResourceAccessPort) {
    suspend fun inspect(asset: ChartAsset, sourceGeneration: Long): ChartBasicInspectionResult {
        val request = ChartReadRequest(asset.id, asset.locator, asset.revision, sourceGeneration, ChartReadPurpose.VALIDATION)
        val opened = access.open(request)
        if (opened is ChartOpenResult.Rejected) {
            return ChartBasicInspectionResult.Rejected(opened.failure.validationIssue(), opened.detail)
        }
        val session = (opened as ChartOpenResult.Opened).session
        return session.use {
            try {
                val metadata = it.readMetadata()
                val scheme = parseScheme(metadata)
                    ?: return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.UNKNOWN_SCHEME, "Unknown tile scheme")
                val samples = it.readSampleTiles(BASIC_SAMPLE_LIMIT)
                if (samples.isEmpty()) {
                    return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.EMPTY_TILESET, "No raster tiles")
                }
                val warnings = linkedSetOf<ChartCompatibilityWarning>()
                if (!it.metadataPresent || metadata.isEmpty()) warnings += ChartCompatibilityWarning.METADATA_MISSING
                val validSamples = samples.filter { sample ->
                    val valid = ChartTileCoordinateMapper.externalKey(sample.key, scheme) != null
                    if (!valid) warnings += ChartCompatibilityWarning.INVALID_SAMPLE_COORDINATE
                    valid
                }
                if (validSamples.isEmpty()) {
                    return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.INVALID_COORDINATE, "No renderable tile coordinate")
                }
                val tileSizes = validSamples.map { sample -> sample.payload.widthPx }.distinct()
                if (tileSizes.size > 1) warnings += ChartCompatibilityWarning.MIXED_TILE_SIZE
                val encodings = validSamples.map { sample -> sample.payload.mimeType }.distinct()
                if (encodings.size > 1) warnings += ChartCompatibilityWarning.MIXED_RASTER_ENCODING
                val declaredEncoding = declaredRasterMime(metadata)
                if (metadata.containsKey("format") && declaredEncoding == null || declaredEncoding != null && declaredEncoding != encodings.first()) {
                    warnings += ChartCompatibilityWarning.FORMAT_MISMATCH
                }
                val metadataZoom = parseZoomRange(metadata, warnings)
                val derivedZoom = it.readZoomRange() ?: validSamples.map { sample -> sample.key.zoom.toInt() }
                    .let { zooms -> zooms.min()..zooms.max() }
                val embeddedBounds = parseBounds(metadata, warnings)
                val bounds = embeddedBounds ?: ChartTileCoverageDeriver.derive(it.readTileExtents(), scheme)
                val facts = factsFrom(
                    metadata = metadata,
                    tileSize = tileSizes.first(),
                    rasterMimeType = encodings.first(),
                    scheme = scheme,
                    tileCount = null,
                    bounds = bounds,
                    zoomRange = metadataZoom ?: derivedZoom,
                )
                ChartBasicInspectionResult.Readable(
                    ChartBasicInspection(facts, samples.size, it.statistics(), it.accessMode, warnings),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                ChartBasicInspectionResult.Rejected(ChartValidationIssue.READ_FAILED, error.javaClass.simpleName)
            }
        }
    }
}

class ChartFullVerifier(
    private val access: ChartResourceAccessPort,
    private val revisionProbe: ChartRevisionProbe,
) {
    suspend fun verify(
        asset: ChartAsset,
        sourceGeneration: Long,
        shouldCancel: () -> Boolean,
        onProgress: (ChartVerificationProgress) -> Unit = {},
    ): ChartFullVerificationResult {
        if (revisionProbe.currentRevision(asset)?.cacheKey != asset.revision.cacheKey) {
            return ChartFullVerificationResult.Rejected(ChartValidationIssue.REVISION_CHANGED, "Revision changed before validation")
        }
        val opened = access.open(ChartReadRequest(asset.id, asset.locator, asset.revision, sourceGeneration, ChartReadPurpose.VALIDATION))
        if (opened is ChartOpenResult.Rejected) {
            return ChartFullVerificationResult.Rejected(opened.failure.validationIssue(), opened.detail)
        }
        val session = (opened as ChartOpenResult.Opened).session
        return session.use {
            try {
                val metadata = it.readMetadata()
                val scheme = parseScheme(metadata)
                    ?: return@use ChartFullVerificationResult.Rejected(ChartValidationIssue.UNKNOWN_SCHEME, "Unknown tile scheme")
                var offset = 0L
                var tileSize: Int? = null
                var rasterMimeType: String? = null
                var minZoom: Int? = null
                var maxZoom: Int? = null
                var previous: ChartStoredTileKey? = null
                while (true) {
                    if (shouldCancel()) return@use ChartFullVerificationResult.Cancelled
                    val page = it.readStoredTiles(offset)
                    if (page.isEmpty()) break
                    for (tile in page) {
                        if (shouldCancel()) return@use ChartFullVerificationResult.Cancelled
                        if (previous == tile.key) return@use ChartFullVerificationResult.Rejected(
                            ChartValidationIssue.DUPLICATE_COORDINATE, "Duplicate tile coordinate",
                        )
                        previous = tile.key
                        val key = ChartTileCoordinateMapper.externalKey(tile.key, scheme)
                            ?: return@use ChartFullVerificationResult.Rejected(
                                ChartValidationIssue.INVALID_COORDINATE, "Invalid tile coordinate",
                            )
                        if (tileSize != null && tileSize != tile.payload.widthPx) {
                            return@use ChartFullVerificationResult.Rejected(
                                ChartValidationIssue.MIXED_TILE_SIZE, "Tiles use different sizes",
                            )
                        }
                        tileSize = tile.payload.widthPx
                        if (rasterMimeType != null && rasterMimeType != tile.payload.mimeType) {
                            return@use ChartFullVerificationResult.Rejected(
                                ChartValidationIssue.MIXED_RASTER_ENCODING, "Tiles use different encodings",
                            )
                        }
                        rasterMimeType = tile.payload.mimeType
                        minZoom = minOf(minZoom ?: key.zoom, key.zoom)
                        maxZoom = maxOf(maxZoom ?: key.zoom, key.zoom)
                        offset++
                    }
                    onProgress(ChartVerificationProgress(offset, 0L, it.sourceSizeBytes))
                }
                if (offset == 0L || tileSize == null) return@use ChartFullVerificationResult.Rejected(
                    ChartValidationIssue.EMPTY_TILESET, "No raster tiles",
                )
                val digest = MessageDigest.getInstance("SHA-256")
                var hashed = 0L
                while (hashed < it.sourceSizeBytes) {
                    if (shouldCancel()) return@use ChartFullVerificationResult.Cancelled
                    val bytes = it.readSourceRange(hashed)
                    if (bytes.isEmpty()) return@use ChartFullVerificationResult.Rejected(
                        ChartValidationIssue.READ_FAILED, "Unexpected end of source",
                    )
                    digest.update(bytes)
                    hashed += bytes.size
                    onProgress(ChartVerificationProgress(offset, hashed, it.sourceSizeBytes))
                }
                if (revisionProbe.currentRevision(asset)?.cacheKey != asset.revision.cacheKey) {
                    return@use ChartFullVerificationResult.Rejected(
                        ChartValidationIssue.REVISION_CHANGED, "Revision changed during validation",
                    )
                }
                val exactMetadata = metadata.toMutableMap().apply {
                    this["minzoom"] = requireNotNull(minZoom).toString()
                    this["maxzoom"] = requireNotNull(maxZoom).toString()
                }
                val declaredEncoding = declaredRasterMime(metadata)
                if (metadata.containsKey("format") && declaredEncoding == null || declaredEncoding != null && declaredEncoding != rasterMimeType) {
                    return@use ChartFullVerificationResult.Rejected(ChartValidationIssue.INVALID_METADATA, "Raster metadata does not match tiles")
                }
                val warnings = compatibilityWarnings(metadata, it.metadataPresent)
                val embeddedBounds = parseBounds(metadata)
                val bounds = embeddedBounds ?: ChartTileCoverageDeriver.derive(it.readTileExtents(), scheme)
                val facts = factsFrom(
                    exactMetadata,
                    tileSize,
                    requireNotNull(rasterMimeType),
                    scheme,
                    offset,
                    bounds = bounds,
                    zoomRange = requireNotNull(minZoom)..requireNotNull(maxZoom),
                )
                val sha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                ChartFullVerificationResult.Verified(
                    asset.revision.copy(contentSha256 = sha),
                    facts,
                    it.statistics(),
                    offset,
                    it.accessMode,
                    warnings,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                ChartFullVerificationResult.Rejected(ChartValidationIssue.READ_FAILED, error.javaClass.simpleName)
            }
        }
    }
}

private fun ChartReadFailure.validationIssue(): ChartValidationIssue = when (this) {
    ChartReadFailure.PERMISSION_LOST -> ChartValidationIssue.PERMISSION_LOST
    ChartReadFailure.DIRECT_READ_UNSUPPORTED,
    ChartReadFailure.SUBRANGE_UNSUPPORTED,
    -> ChartValidationIssue.DIRECT_READ_UNSUPPORTED
    ChartReadFailure.INVALID_DATABASE,
    ChartReadFailure.INVALID_SCHEMA,
    -> ChartValidationIssue.INVALID_SCHEMA
    ChartReadFailure.REVISION_CHANGED -> ChartValidationIssue.REVISION_CHANGED
    ChartReadFailure.CANCELLED -> ChartValidationIssue.CANCELLED
    else -> ChartValidationIssue.OPEN_FAILED
}

private fun parseScheme(metadata: Map<String, String>): MapTileScheme? = when (metadata["scheme"]?.trim()?.lowercase()) {
    null, "", "tms" -> MapTileScheme.MBTILES_TMS
    "xyz" -> MapTileScheme.XYZ
    else -> null
}

private fun factsFrom(
    metadata: Map<String, String>,
    tileSize: Int,
    rasterMimeType: String,
    scheme: MapTileScheme,
    tileCount: Long?,
    bounds: GeoBounds? = parseBounds(metadata),
    zoomRange: IntRange? = parseZoomRange(metadata),
): ChartAssetFacts = ChartAssetFacts(
        format = ChartAssetFormat.RASTER_MBTILES,
        bounds = bounds,
        minZoom = zoomRange?.first,
        maxZoom = zoomRange?.last,
        tileCount = tileCount,
        tileSize = tileSize,
        tileScheme = scheme,
        attribution = metadata["attribution"]?.take(2_048),
        attributionProvenance = if (metadata.containsKey("attribution")) ChartFactProvenance.EMBEDDED else ChartFactProvenance.UNKNOWN,
        rasterMimeType = rasterMimeType,
    )

private fun parseBounds(
    metadata: Map<String, String>,
    warnings: MutableSet<ChartCompatibilityWarning>? = null,
): GeoBounds? {
    val raw = metadata["bounds"]?.trim()
    if (raw.isNullOrEmpty()) {
        warnings?.add(ChartCompatibilityWarning.BOUNDS_MISSING)
        return null
    }
    val values = raw.split(',').takeIf { it.size == 4 }?.map { it.trim().toDoubleOrNull() }
    val bounds = values?.takeIf { it.all { value -> value != null } }?.map { requireNotNull(it) }
        ?.let { runCatching { GeoBounds(south = it[1], west = it[0], north = it[3], east = it[2]) }.getOrNull() }
    if (bounds == null) warnings?.add(ChartCompatibilityWarning.BOUNDS_INVALID)
    return bounds
}

private fun parseZoomRange(
    metadata: Map<String, String>,
    warnings: MutableSet<ChartCompatibilityWarning>? = null,
): IntRange? {
    val minRaw = metadata["minzoom"]?.trim()
    val maxRaw = metadata["maxzoom"]?.trim()
    if (minRaw.isNullOrEmpty() || maxRaw.isNullOrEmpty()) {
        warnings?.add(ChartCompatibilityWarning.ZOOM_RANGE_MISSING)
        return null
    }
    val min = minRaw.toIntOrNull()
    val max = maxRaw.toIntOrNull()
    if (min == null || max == null || min !in MIN_ZOOM..MAX_ZOOM || max !in min..MAX_ZOOM) {
        warnings?.add(ChartCompatibilityWarning.ZOOM_RANGE_INVALID)
        return null
    }
    return min..max
}

private fun compatibilityWarnings(
    metadata: Map<String, String>,
    metadataPresent: Boolean,
): Set<ChartCompatibilityWarning> =
    linkedSetOf<ChartCompatibilityWarning>().apply {
        if (!metadataPresent || metadata.isEmpty()) add(ChartCompatibilityWarning.METADATA_MISSING)
        parseBounds(metadata, this)
        parseZoomRange(metadata, this)
    }

private fun declaredRasterMime(metadata: Map<String, String>): String? = when (metadata["format"]?.trim()?.lowercase()) {
    null, "" -> null
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> null
}

private const val BASIC_SAMPLE_LIMIT = 3

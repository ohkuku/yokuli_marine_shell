package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import java.security.MessageDigest
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
                if (samples.any { sample -> ChartTileCoordinateMapper.externalKey(sample.key, scheme) == null }) {
                    return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.INVALID_COORDINATE, "Invalid tile coordinate")
                }
                val tileSizes = samples.map { sample -> sample.payload.widthPx }.distinct()
                if (tileSizes.size != 1) {
                    return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.MIXED_TILE_SIZE, "Sample tiles use different sizes")
                }
                val encodings = samples.map { sample -> sample.payload.mimeType }.distinct()
                if (encodings.size != 1) {
                    return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.MIXED_RASTER_ENCODING, "Sample tiles use different encodings")
                }
                val declaredEncoding = declaredRasterMime(metadata)
                if (metadata.containsKey("format") && declaredEncoding == null || declaredEncoding != null && declaredEncoding != encodings.single()) {
                    return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.INVALID_METADATA, "Raster metadata does not match sampled tile")
                }
                val facts = factsFrom(metadata, tileSizes.single(), encodings.single(), scheme, tileCount = null)
                    ?: return@use ChartBasicInspectionResult.Rejected(ChartValidationIssue.INVALID_METADATA, "Invalid MBTiles metadata")
                ChartBasicInspectionResult.Readable(
                    ChartBasicInspection(facts, samples.size, it.statistics()),
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
                val facts = factsFrom(exactMetadata, tileSize, requireNotNull(rasterMimeType), scheme, offset)
                    ?: return@use ChartFullVerificationResult.Rejected(ChartValidationIssue.INVALID_METADATA, "Invalid MBTiles metadata")
                val sha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                ChartFullVerificationResult.Verified(
                    asset.revision.copy(contentSha256 = sha),
                    facts,
                    it.statistics(),
                    offset,
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
): ChartAssetFacts? = runCatching {
    fun boundedInt(name: String): Int? = metadata[name]?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull()
    val bounds = metadata["bounds"]?.split(',')?.takeIf { it.size == 4 }?.map { it.trim().toDouble() }?.let {
        GeoBounds(south = it[1], west = it[0], north = it[3], east = it[2])
    }
    ChartAssetFacts(
        format = ChartAssetFormat.RASTER_MBTILES,
        bounds = bounds,
        minZoom = boundedInt("minzoom"),
        maxZoom = boundedInt("maxzoom"),
        tileCount = tileCount,
        tileSize = tileSize,
        tileScheme = scheme,
        attribution = metadata["attribution"]?.take(2_048),
        attributionProvenance = if (metadata.containsKey("attribution")) ChartFactProvenance.EMBEDDED else ChartFactProvenance.UNKNOWN,
        rasterMimeType = rasterMimeType,
    )
}.getOrNull()

private fun declaredRasterMime(metadata: Map<String, String>): String? = when (metadata["format"]?.trim()?.lowercase()) {
    null, "" -> null
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> null
}

private const val BASIC_SAMPLE_LIMIT = 3

package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.MapTileScheme

@JvmInline
value class ChartSourceId(val value: String) {
    init { require(value.matches(OPAQUE_ID)) }
}

@JvmInline
value class ChartAssetId(val value: String) {
    init { require(value.matches(OPAQUE_ID)) }
}

@JvmInline
value class ChartOpaqueLocator(val value: String) {
    init { require(value.isNotBlank() && value.length <= MAX_LOCATOR_LENGTH && '\u0000' !in value) }
}

data class ChartContentRevision(
    val identity: String,
    val observedSizeBytes: Long?,
    val observedModifiedAtMillis: Long?,
    val providerRevisionHint: String? = null,
    val contentSha256: String? = null,
) {
    init {
        require(identity.isNotBlank() && identity.length <= 512)
        require(observedSizeBytes == null || observedSizeBytes >= 0L)
        require(observedModifiedAtMillis == null || observedModifiedAtMillis >= 0L)
        require(providerRevisionHint == null || providerRevisionHint.length <= 512)
        require(contentSha256 == null || contentSha256.matches(SHA_256))
    }

    /** Stable inside the catalog, without claiming a full-content hash was calculated. */
    val cacheKey: String = listOf(
        identity,
        observedSizeBytes?.toString().orEmpty(),
        observedModifiedAtMillis?.toString().orEmpty(),
        providerRevisionHint.orEmpty(),
        contentSha256.orEmpty(),
    ).joinToString("|")
}

data class ChartReadRequest(
    val assetId: ChartAssetId,
    val locator: ChartOpaqueLocator,
    val revision: ChartContentRevision,
    val sourceGeneration: Long,
) {
    init { require(sourceGeneration > 0L) }
}

data class ChartTileKey(val zoom: Int, val column: Long, val row: Long) {
    init {
        require(zoom in MIN_ZOOM..MAX_ZOOM)
        val axisSize = 1L shl zoom
        require(column in 0 until axisSize)
        require(row in 0 until axisSize)
    }

    fun storageRow(scheme: MapTileScheme): Long = ChartTileCoordinateMapper.storageRow(this, scheme)
}

data class ChartTilePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(bytes.isNotEmpty() && bytes.size <= MAX_TILE_BYTES)
        require(mimeType in SUPPORTED_RASTER_MIME_TYPES)
        require(widthPx in SUPPORTED_TILE_SIZES && heightPx == widthPx)
    }

    override fun equals(other: Any?): Boolean = other is ChartTilePayload &&
        bytes.contentEquals(other.bytes) && mimeType == other.mimeType &&
        widthPx == other.widthPx && heightPx == other.heightPx

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

data class ChartReadStatistics(
    val sourceBytesRead: Long = 0L,
    val metadataRowsRead: Int = 0,
    val tileQueries: Long = 0L,
) {
    init {
        require(sourceBytesRead >= 0L)
        require(metadataRowsRead >= 0)
        require(tileQueries >= 0L)
    }
}

enum class ChartReadFailure {
    CANNOT_OPEN,
    DIRECT_READ_UNSUPPORTED,
    SUBRANGE_UNSUPPORTED,
    INVALID_DATABASE,
    INVALID_SCHEMA,
    REVISION_CHANGED,
    SHORT_READ,
    SESSION_CLOSED,
    CANCELLED,
    TILE_TOO_LARGE,
    UNSUPPORTED_RASTER,
    IO_FAILURE,
}

class ChartReadException(
    val failure: ChartReadFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

sealed interface ChartOpenResult {
    data class Opened(val session: ChartReadSession) : ChartOpenResult
    data class Rejected(val failure: ChartReadFailure, val detail: String) : ChartOpenResult
}

interface ChartReadSession : AutoCloseable {
    val request: ChartReadRequest
    val sourceSizeBytes: Long
    fun readMetadata(limit: Int = MAX_METADATA_ROWS): Map<String, String>
    fun readTile(key: ChartTileKey, scheme: MapTileScheme): ChartTilePayload?
    fun hasTile(key: ChartTileKey, scheme: MapTileScheme): Boolean
    fun readStoredTiles(offset: Long, limit: Int = MAX_VALIDATION_PAGE_SIZE): List<ChartStoredTile>
    fun readSourceRange(offset: Long, maxByteCount: Int = MAX_HASH_READ_BYTES): ByteArray
    fun statistics(): ChartReadStatistics
    override fun close()
}

data class ChartStoredTileKey(val zoom: Long, val column: Long, val storageRow: Long)

data class ChartStoredTile(val key: ChartStoredTileKey, val payload: ChartTilePayload)

fun interface ChartResourceAccessPort {
    suspend fun open(request: ChartReadRequest): ChartOpenResult
}

const val MIN_ZOOM = 0
const val MAX_ZOOM = 24
const val MAX_METADATA_ROWS = 256
const val MAX_TILE_BYTES = 16 * 1024 * 1024
const val MAX_VALIDATION_PAGE_SIZE = 32
const val MAX_HASH_READ_BYTES = 1024 * 1024
val SUPPORTED_TILE_SIZES = setOf(256, 512)
val SUPPORTED_RASTER_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")

private const val MAX_LOCATOR_LENGTH = 4096
private val OPAQUE_ID = Regex("[A-Za-z0-9._-]{1,128}")
private val SHA_256 = Regex("[0-9a-f]{64}")

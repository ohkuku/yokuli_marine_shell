package com.yokuli.marine.map.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MapTileSnapshotFormat { JPEG }

data class MapTileSnapshotRequest(
    val id: Long,
    val camera: MapCamera,
    val contentRevision: String,
)

/** Immutable, renderer-produced payload. The byte array is defensively copied at both edges. */
class MapTileSnapshot private constructor(
    val request: MapTileSnapshotRequest,
    val width: Int,
    val height: Int,
    val format: MapTileSnapshotFormat,
    private val payload: ByteArray,
) {
    val byteCount: Int get() = payload.size
    fun encodedBytes(): ByteArray = payload.copyOf()

    companion object {
        fun create(
            request: MapTileSnapshotRequest,
            width: Int,
            height: Int,
            format: MapTileSnapshotFormat,
            encodedBytes: ByteArray,
        ) = MapTileSnapshot(request, width, height, format, encodedBytes.copyOf())
    }
}

interface MapTileSnapshotSource {
    val snapshots: StateFlow<MapTileSnapshot?>
}

interface MapTileSnapshotSink {
    fun begin(camera: MapCamera, contentRevision: String): MapTileSnapshotRequest
    fun complete(
        request: MapTileSnapshotRequest,
        width: Int,
        height: Int,
        format: MapTileSnapshotFormat,
        encodedBytes: ByteArray,
    ): Boolean
}

/**
 * Process-owned single-slot cache. A superseded renderer callback cannot replace a newer request,
 * and oversized/invalid images are rejected without discarding the last valid thumbnail.
 */
class BoundedMapTileSnapshotRuntime(
    val maximumEncodedBytes: Int = DEFAULT_MAXIMUM_TILE_SNAPSHOT_BYTES,
    private val maximumDimension: Int = DEFAULT_MAXIMUM_TILE_SNAPSHOT_DIMENSION,
) : MapTileSnapshotSource, MapTileSnapshotSink {
    init {
        require(maximumEncodedBytes in 1..MAXIMUM_ALLOWED_TILE_SNAPSHOT_BYTES)
        require(maximumDimension in 1..1024)
    }

    private val mutable = MutableStateFlow<MapTileSnapshot?>(null)
    override val snapshots: StateFlow<MapTileSnapshot?> = mutable.asStateFlow()
    private var nextRequestId = 0L
    private var currentRequest: MapTileSnapshotRequest? = null

    @Synchronized
    override fun begin(camera: MapCamera, contentRevision: String): MapTileSnapshotRequest {
        require(contentRevision.isNotBlank() && contentRevision.length <= 160)
        return MapTileSnapshotRequest(++nextRequestId, camera, contentRevision).also { currentRequest = it }
    }

    @Synchronized
    override fun complete(
        request: MapTileSnapshotRequest,
        width: Int,
        height: Int,
        format: MapTileSnapshotFormat,
        encodedBytes: ByteArray,
    ): Boolean {
        if (request != currentRequest) return false
        if (width !in 1..maximumDimension || height !in 1..maximumDimension) return false
        if (encodedBytes.isEmpty() || encodedBytes.size > maximumEncodedBytes) return false
        mutable.value = MapTileSnapshot.create(request, width, height, format, encodedBytes)
        return true
    }
}

const val DEFAULT_MAXIMUM_TILE_SNAPSHOT_BYTES = 256 * 1024
const val MAXIMUM_ALLOWED_TILE_SNAPSHOT_BYTES = 512 * 1024
const val DEFAULT_MAXIMUM_TILE_SNAPSHOT_DIMENSION = 512

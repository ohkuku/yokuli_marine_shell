package com.yokuli.marine.chart.library.android

import android.content.ContentResolver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartReadAccessMode
import com.yokuli.marine.map.domain.chartlibrary.ChartReadException
import com.yokuli.marine.map.domain.chartlibrary.ChartReadFailure
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.domain.chartlibrary.ChartReadSession
import com.yokuli.marine.map.domain.chartlibrary.ChartReadStatistics
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import com.yokuli.marine.map.domain.chartlibrary.ChartTilePayload
import com.yokuli.marine.map.domain.chartlibrary.MAX_METADATA_ROWS
import com.yokuli.marine.map.domain.chartlibrary.MAX_HASH_READ_BYTES
import com.yokuli.marine.map.domain.chartlibrary.MAX_TILE_BYTES
import com.yokuli.marine.map.domain.chartlibrary.MAX_VALIDATION_PAGE_SIZE
import com.yokuli.marine.map.domain.chartlibrary.SUPPORTED_TILE_SIZES
import com.yokuli.marine.map.domain.chartlibrary.ChartStoredTile
import com.yokuli.marine.map.domain.chartlibrary.ChartStoredTileKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class AndroidChartResourceAccess(
    private val resolver: ContentResolver,
    private val managedRoot: File? = null,
    private val acquireManagedLease: ((ChartPackageId) -> ChartPackageLease)? = null,
    private val localFallbackRoot: File? = null,
) : ChartResourceAccessPort {
    private val fallbackLocks = Array(FALLBACK_LOCK_STRIPES) { Mutex() }

    override suspend fun open(request: ChartReadRequest): ChartOpenResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(request.locator.value) }.getOrNull()
            ?: return@withContext ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Invalid locator")
        if (uri.scheme == MANAGED_SCHEME) return@withContext openManaged(request, uri)
        when (val opened = AndroidSafRandomAccessReader(resolver).open(uri)) {
            is SafRandomAccessOpenResult.Rejected -> if (
                localFallbackRoot != null && opened.failure in STREAM_FALLBACK_FAILURES
            ) {
                fallbackLocks[(request.assetId.value.hashCode() and Int.MAX_VALUE) % fallbackLocks.size].withLock {
                    openLocalFallback(request, uri, localFallbackRoot)
                }
            } else {
                ChartOpenResult.Rejected(opened.failure, opened.detail)
            }
            is SafRandomAccessOpenResult.Opened -> openDatabase(request, opened.handle)
        }
    }

    private suspend fun openLocalFallback(request: ChartReadRequest, uri: Uri, root: File): ChartOpenResult {
        val canonicalRoot = runCatching { root.apply { mkdirs() }.canonicalFile }.getOrNull()
            ?.takeIf(File::isDirectory)
            ?: return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Local chart access is unavailable")
        val revisionKey = request.revision.cacheKey.sha256().take(24)
        val target = File(canonicalRoot, "${request.assetId.value}-$revisionKey.mbtiles")
        val expectedSize = request.revision.observedSizeBytes
        if (!target.isFile || expectedSize?.let { target.length() != it } == true) {
            if (expectedSize != null && expectedSize > canonicalRoot.usableSpace - MIN_FREE_AFTER_FALLBACK_BYTES) {
                return ChartOpenResult.Rejected(ChartReadFailure.INSUFFICIENT_SPACE, "Not enough local storage for chart access")
            }
            val temporary = File(canonicalRoot, ".${request.assetId.value}-$revisionKey.partial")
            temporary.delete()
            try {
                val input = resolver.openInputStream(uri)
                    ?: return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Provider returned no readable stream")
                var copied = 0L
                input.use { source ->
                    FileOutputStream(temporary).buffered().use { destination ->
                        val buffer = ByteArray(STREAM_COPY_BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (canonicalRoot.usableSpace < count.toLong() + MIN_FREE_AFTER_FALLBACK_BYTES) {
                                throw ChartReadException(
                                    ChartReadFailure.INSUFFICIENT_SPACE,
                                    "Not enough local storage for chart access",
                                )
                            }
                            destination.write(buffer, 0, count)
                        }
                    }
                }
                if (copied <= 0L || expectedSize?.let { copied != it } == true) {
                    throw ChartReadException(
                        ChartReadFailure.SHORT_READ,
                        "Provider stream size does not match the catalog revision",
                    )
                }
                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                canonicalRoot.listFiles { file ->
                    file.isFile && file.name.startsWith("${request.assetId.value}-") && file != target
                }.orEmpty().forEach(File::delete)
            } catch (error: SecurityException) {
                temporary.delete()
                return ChartOpenResult.Rejected(
                    ChartReadFailure.PERMISSION_LOST,
                    "Persisted read permission is no longer available",
                )
            } catch (error: Throwable) {
                temporary.delete()
                val failure = (error as? ChartReadException)?.failure ?: ChartReadFailure.IO_FAILURE
                return ChartOpenResult.Rejected(failure, error.message ?: error.javaClass.simpleName)
            }
        }
        val descriptor = runCatching { ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY) }
            .getOrElse { return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, it.javaClass.simpleName) }
        return openDatabase(
            request,
            SafReadOnlyHandle(descriptor, target.length(), accessMode = ChartReadAccessMode.LOCAL_FALLBACK),
        )
    }

    private fun openManaged(request: ChartReadRequest, uri: Uri): ChartOpenResult {
        val root = managedRoot ?: return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Managed store unavailable")
        val version = runCatching { ChartPackageVersionId(uri.host.orEmpty()) }.getOrNull()
            ?: return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Invalid managed chart locator")
        if (request.revision.contentSha256 != version.value) {
            return ChartOpenResult.Rejected(ChartReadFailure.REVISION_CHANGED, "Managed version does not match catalog revision")
        }
        val rootPath = runCatching { root.canonicalFile }.getOrNull()
            ?: return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Managed root unavailable")
        val file = runCatching { File(rootPath, "package-${version.value}/map.mbtiles").canonicalFile }.getOrNull()
            ?: return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Managed chart path unavailable")
        if (file.parentFile?.parentFile != rootPath || !file.isFile) {
            return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Managed chart version is missing")
        }
        val lease = acquireManagedLease?.invoke(ChartPackageId(version.value)) ?: ChartPackageLease {}
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (error: Throwable) {
            lease.close()
            return ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, error.javaClass.simpleName)
        }
        return openDatabase(
            request,
            SafReadOnlyHandle(
                descriptor,
                file.length(),
                lease::close,
                ChartReadAccessMode.MANAGED_COPY,
            ),
        )
    }

    private fun openDatabase(request: ChartReadRequest, handle: SafReadOnlyHandle): ChartOpenResult {
        if (request.revision.observedSizeBytes?.let { it != handle.sizeBytes } == true) {
            handle.close()
            return ChartOpenResult.Rejected(ChartReadFailure.REVISION_CHANGED, "Observed size changed before open")
        }
        val header = handle.readAtResult(0L, SQLITE_HEADER.size).getOrElse { error ->
            handle.close()
            return ChartOpenResult.Rejected(
                (error as? ChartReadException)?.failure ?: ChartReadFailure.INVALID_DATABASE,
                "Unable to read SQLite header",
            )
        }
        if (!header.contentEquals(SQLITE_HEADER)) {
            handle.close()
            return ChartOpenResult.Rejected(ChartReadFailure.INVALID_DATABASE, "Not a SQLite database")
        }
        val database = try {
            SQLiteDatabase.openDatabase(
                handle.procFdPath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).also { it.rawQuery("PRAGMA query_only=ON", emptyArray()).use { cursor -> cursor.moveToFirst() } }
        } catch (error: Throwable) {
            handle.close()
            return ChartOpenResult.Rejected(ChartReadFailure.INVALID_DATABASE, error.javaClass.simpleName)
        }
        return try {
            val metadataPresent = requireMbTilesSchema(database)
            ChartOpenResult.Opened(AndroidMbTilesReadSession(request, handle, database, metadataPresent))
        } catch (error: Throwable) {
            database.close()
            handle.close()
            ChartOpenResult.Rejected(
                (error as? ChartReadException)?.failure ?: ChartReadFailure.INVALID_SCHEMA,
                error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun requireMbTilesSchema(database: SQLiteDatabase): Boolean {
        val objects = linkedSetOf<String>()
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE (type='table' OR type='view') AND name IN ('metadata','tiles')",
            emptyArray(),
        ).use { cursor -> while (cursor.moveToNext()) objects += cursor.getString(0) }
        if ("tiles" !in objects) throw ChartReadException(
            ChartReadFailure.INVALID_SCHEMA,
            "Missing required MBTiles tiles table or view",
        )
        val columns = linkedSetOf<String>()
        database.rawQuery("PRAGMA table_info(tiles)", emptyArray()).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns += cursor.getString(name)
        }
        val required = setOf("zoom_level", "tile_column", "tile_row", "tile_data")
        if (!columns.containsAll(required)) throw ChartReadException(
            ChartReadFailure.INVALID_SCHEMA,
            "MBTiles tiles object is missing required columns",
        )
        return "metadata" in objects
    }

    private companion object {
        val STREAM_FALLBACK_FAILURES = setOf(
            ChartReadFailure.CANNOT_OPEN,
            ChartReadFailure.DIRECT_READ_UNSUPPORTED,
            ChartReadFailure.SUBRANGE_UNSUPPORTED,
        )
        const val STREAM_COPY_BUFFER_BYTES = 1024 * 1024
        const val MIN_FREE_AFTER_FALLBACK_BYTES = 250L * 1024L * 1024L
        const val FALLBACK_LOCK_STRIPES = 64
    }
}

private const val MANAGED_SCHEME = "yokuli-managed"

private class AndroidMbTilesReadSession(
    override val request: ChartReadRequest,
    private val handle: SafReadOnlyHandle,
    private val database: SQLiteDatabase,
    override val metadataPresent: Boolean,
) : ChartReadSession {
    override val sourceSizeBytes: Long = handle.sizeBytes
    override val accessMode: ChartReadAccessMode = handle.accessMode
    private val closed = AtomicBoolean(false)
    private val metadataRows = AtomicInteger(0)
    private val tileQueries = AtomicLong(0L)
    private val logicalBytesRead = AtomicLong(handle.observedBytesRead())

    override fun readMetadata(limit: Int): Map<String, String> = checked {
        require(limit in 1..MAX_METADATA_ROWS)
        if (!metadataPresent) return@checked emptyMap()
        val result = linkedMapOf<String, String>()
        database.rawQuery(
            "SELECT length(name),length(value),substr(name,1,?),substr(value,1,?) FROM metadata ORDER BY name LIMIT ?",
            arrayOf((MAX_METADATA_TEXT + 1).toString(), (MAX_METADATA_TEXT + 1).toString(), limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getLong(0) > MAX_METADATA_TEXT || cursor.getLong(1) > MAX_METADATA_TEXT) {
                    throw ChartReadException(ChartReadFailure.INVALID_SCHEMA, "Metadata text exceeds bounded limit")
                }
                val name = cursor.getString(2)
                val value = cursor.getString(3)
                if (name !in result) result[name] = value
                logicalBytesRead.addAndGet((name.length + value.length).toLong())
            }
        }
        metadataRows.set(result.size)
        result
    }

    override fun readZoomRange(): IntRange? = checked {
        tileQueries.incrementAndGet()
        database.rawQuery("SELECT MIN(zoom_level),MAX(zoom_level) FROM tiles", emptyArray()).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0) || cursor.isNull(1)) return@checked null
            val min = cursor.getLong(0)
            val max = cursor.getLong(1)
            if (min !in 0L..24L || max !in min..24L) return@checked null
            min.toInt()..max.toInt()
        }
    }

    override fun readTileExtents(limit: Int): List<ChartStoredTileExtent> = checked {
        require(limit in 1..MAX_TILE_EXTENT_LEVELS)
        tileQueries.incrementAndGet()
        database.rawQuery(
            "SELECT zoom_level,MIN(tile_column),MAX(tile_column),MIN(tile_row),MAX(tile_row) " +
                "FROM tiles GROUP BY zoom_level ORDER BY zoom_level DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ChartStoredTileExtent(
                            zoom = cursor.getLong(0),
                            minColumn = cursor.getLong(1),
                            maxColumn = cursor.getLong(2),
                            minStorageRow = cursor.getLong(3),
                            maxStorageRow = cursor.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    override fun readTile(key: ChartTileKey, scheme: MapTileScheme): ChartTilePayload? = checked {
        tileQueries.incrementAndGet()
        database.rawQuery(
            "SELECT length(tile_data),CASE WHEN length(tile_data)<=? THEN tile_data ELSE NULL END " +
                "FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",
            arrayOf(MAX_TILE_BYTES.toString(), key.zoom.toString(), key.column.toString(), key.storageRow(scheme).toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@checked null
            if (cursor.getLong(0) > MAX_TILE_BYTES) throw ChartReadException(
                ChartReadFailure.TILE_TOO_LARGE,
                "Tile exceeds the bounded payload limit",
            )
            val bytes = cursor.getBlob(1)
            logicalBytesRead.addAndGet(bytes.size.toLong())
            bytes.toTilePayload()
        }
    }

    override fun hasTile(key: ChartTileKey, scheme: MapTileScheme): Boolean = checked {
        tileQueries.incrementAndGet()
        database.rawQuery(
            "SELECT 1 FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",
            arrayOf(key.zoom.toString(), key.column.toString(), key.storageRow(scheme).toString()),
        ).use { it.moveToFirst() }
    }

    override fun readStoredTiles(offset: Long, limit: Int): List<ChartStoredTile> = checked {
        require(offset >= 0L)
        require(limit in 1..MAX_VALIDATION_PAGE_SIZE)
        database.rawQuery(
            "SELECT zoom_level,tile_column,tile_row,length(tile_data)," +
                "CASE WHEN length(tile_data)<=? THEN tile_data ELSE NULL END FROM tiles " +
                "ORDER BY zoom_level,tile_column,tile_row LIMIT ? OFFSET ?",
            arrayOf(MAX_TILE_BYTES.toString(), limit.toString(), offset.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getLong(3) > MAX_TILE_BYTES) throw ChartReadException(
                        ChartReadFailure.TILE_TOO_LARGE,
                        "Tile exceeds the bounded payload limit",
                    )
                    val bytes = cursor.getBlob(4)
                    logicalBytesRead.addAndGet(bytes.size.toLong())
                    add(
                        ChartStoredTile(
                            ChartStoredTileKey(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2)),
                            bytes.toTilePayload(),
                        ),
                    )
                }
            }
        }
    }

    override fun readSampleTiles(limit: Int): List<ChartStoredTile> = checked {
        require(limit in 1..MAX_VALIDATION_PAGE_SIZE)
        database.rawQuery(
            "SELECT zoom_level,tile_column,tile_row,length(tile_data)," +
                "CASE WHEN length(tile_data)<=? THEN tile_data ELSE NULL END FROM tiles LIMIT ?",
            arrayOf(MAX_TILE_BYTES.toString(), limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getLong(3) > MAX_TILE_BYTES) throw ChartReadException(
                        ChartReadFailure.TILE_TOO_LARGE,
                        "Tile exceeds the bounded payload limit",
                    )
                    val bytes = cursor.getBlob(4)
                    logicalBytesRead.addAndGet(bytes.size.toLong())
                    add(
                        ChartStoredTile(
                            ChartStoredTileKey(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2)),
                            bytes.toTilePayload(),
                        ),
                    )
                }
            }
        }
    }

    override fun readSourceRange(offset: Long, maxByteCount: Int): ByteArray = checked {
        require(maxByteCount in 1..MAX_HASH_READ_BYTES)
        handle.readAtUpTo(offset, maxByteCount)
    }

    override fun statistics(): ChartReadStatistics = ChartReadStatistics(
        sourceBytesRead = logicalBytesRead.get().coerceAtLeast(handle.observedBytesRead()),
        metadataRowsRead = metadataRows.get(),
        tileQueries = tileQueries.get(),
    )

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            database.close()
            handle.close()
        }
    }

    private inline fun <T> checked(block: () -> T): T {
        if (closed.get()) throw ChartReadException(ChartReadFailure.SESSION_CLOSED, "Read session is closed")
        return try {
            block()
        } catch (error: ChartReadException) {
            throw error
        } catch (error: SQLiteException) {
            throw ChartReadException(ChartReadFailure.IO_FAILURE, "SQLite read failed", error)
        }
    }

    companion object { private const val MAX_METADATA_TEXT = 4096 }
}

private fun ByteArray.toTilePayload(): ChartTilePayload {
    val mimeType = when {
        size >= 8 && copyOfRange(0, 8).contentEquals(PNG_HEADER) -> "image/png"
        size >= 3 && this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() && this[2] == 0xff.toByte() -> "image/jpeg"
        size >= 12 && decodeToString(0, 4) == "RIFF" && decodeToString(8, 12) == "WEBP" -> "image/webp"
        else -> throw ChartReadException(ChartReadFailure.UNSUPPORTED_RASTER, "Unknown raster encoding")
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, options)
    if (options.outWidth !in SUPPORTED_TILE_SIZES || options.outHeight != options.outWidth) {
        throw ChartReadException(ChartReadFailure.UNSUPPORTED_RASTER, "Raster tile must be 256 or 512 square pixels")
    }
    return ChartTilePayload(copyOf(), mimeType, options.outWidth, options.outHeight)
}

private val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
private val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray()).joinToString("") { byte -> "%02x".format(byte) }

package com.yokuli.marine.chart.library.android

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.yokuli.marine.map.domain.chartlibrary.ChartReadException
import com.yokuli.marine.map.domain.chartlibrary.ChartReadFailure
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed interface SafRandomAccessOpenResult {
    data class Opened(val handle: SafReadOnlyHandle) : SafRandomAccessOpenResult
    data class Rejected(val failure: ChartReadFailure, val detail: String) : SafRandomAccessOpenResult
}

/**
 * Capability probe for a provider-owned descriptor. It reports whether direct random access is
 * available; the higher-level resource gateway may provide safe local access for stream-only
 * providers without treating the chart content as invalid.
 */
class AndroidSafRandomAccessReader(private val resolver: ContentResolver) {
    fun open(uri: Uri): SafRandomAccessOpenResult {
        val descriptor = try {
            resolver.openFileDescriptor(uri, "r")
        } catch (_: SecurityException) {
            return SafRandomAccessOpenResult.Rejected(
                ChartReadFailure.PERMISSION_LOST,
                "Persisted read permission is no longer available",
            )
        } catch (error: Throwable) {
            return SafRandomAccessOpenResult.Rejected(
                ChartReadFailure.CANNOT_OPEN,
                error.javaClass.simpleName,
            )
        } ?: return SafRandomAccessOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "Provider returned no descriptor")

        return try {
            val position = Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
            Os.lseek(descriptor.fileDescriptor, position, OsConstants.SEEK_SET)
            val stat = Os.fstat(descriptor.fileDescriptor)
            SafRandomAccessOpenResult.Opened(SafReadOnlyHandle(descriptor, stat.st_size))
        } catch (error: ErrnoException) {
            descriptor.close()
            SafRandomAccessOpenResult.Rejected(
                ChartReadFailure.DIRECT_READ_UNSUPPORTED,
                "Provider descriptor is not seekable",
            )
        } catch (error: Throwable) {
            descriptor.close()
            SafRandomAccessOpenResult.Rejected(ChartReadFailure.IO_FAILURE, error.javaClass.simpleName)
        }
    }
}

class SafReadOnlyHandle internal constructor(
    private val descriptor: ParcelFileDescriptor,
    val sizeBytes: Long,
    private val onClose: () -> Unit = {},
    val accessMode: com.yokuli.marine.map.domain.chartlibrary.ChartReadAccessMode =
        com.yokuli.marine.map.domain.chartlibrary.ChartReadAccessMode.DIRECT_PROVIDER,
    /**
     * App-owned files have a stable real path and should be opened by SQLite through that path.
     * Provider-owned descriptors deliberately keep using the descriptor bridge because their
     * original URI is not a filesystem path.
     */
    private val localDatabasePath: String? = null,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val bytesRead = AtomicLong(0L)

    internal val databasePath: String get() = localDatabasePath ?: "/proc/self/fd/${descriptor.fd}"

    fun readAt(offset: Long, byteCount: Int): ByteArray = readAtResult(offset, byteCount).getOrThrow()

    fun readAtUpTo(offset: Long, maxByteCount: Int): ByteArray {
        if (closed.get()) throw ChartReadException(ChartReadFailure.SESSION_CLOSED, "Read handle is closed")
        if (offset < 0L || maxByteCount < 0 || maxByteCount > MAX_DIRECT_READ_BYTES) {
            throw ChartReadException(ChartReadFailure.IO_FAILURE, "Invalid bounded read request")
        }
        if (offset >= sizeBytes || maxByteCount == 0) return ByteArray(0)
        return readAt(offset, minOf(maxByteCount.toLong(), sizeBytes - offset).toInt())
    }

    fun readAtResult(offset: Long, byteCount: Int): Result<ByteArray> = runCatching {
        if (closed.get()) throw ChartReadException(ChartReadFailure.SESSION_CLOSED, "Read handle is closed")
        if (offset < 0L || byteCount < 0 || byteCount > MAX_DIRECT_READ_BYTES) {
            throw ChartReadException(ChartReadFailure.IO_FAILURE, "Invalid bounded read request")
        }
        val target = ByteArray(byteCount)
        var completed = 0
        while (completed < byteCount) {
            val count = try {
                Os.pread(
                    descriptor.fileDescriptor,
                    target,
                    completed,
                    byteCount - completed,
                    offset + completed,
                )
            } catch (error: ErrnoException) {
                throw ChartReadException(ChartReadFailure.IO_FAILURE, "Random read failed", error)
            }
            if (count <= 0) throw ChartReadException(ChartReadFailure.SHORT_READ, "Provider returned a short read")
            completed += count
        }
        bytesRead.addAndGet(completed.toLong())
        target
    }

    fun observedBytesRead(): Long = bytesRead.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                descriptor.close()
            } catch (_: IOException) {
                // Idempotent close; there is no recovery action after ownership is released.
            } finally {
                onClose()
            }
        }
    }

    companion object { private const val MAX_DIRECT_READ_BYTES = 16 * 1024 * 1024 }
}

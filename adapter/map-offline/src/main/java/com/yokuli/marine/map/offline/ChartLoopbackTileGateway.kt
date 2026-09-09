package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartReadSession
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Process-private raster gateway for MapLibre. It is deliberately not a file server: callers can
 * request only a registered asset revision and a bounded XYZ tile coordinate.
 */
class ChartLoopbackTileGateway : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).toHex()
    private val registrations = ConcurrentHashMap<RouteKey, RegisteredAsset>()
    private val activeWorkers = AtomicInteger(0)
    private val workerHighWater = AtomicInteger(0)
    private val workers = ThreadPoolExecutor(
        MAX_CONCURRENT_REQUESTS,
        MAX_CONCURRENT_REQUESTS,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_REQUESTS),
        { runnable -> Thread(runnable, "yokuli-chart-tile").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    // Use the IPv4 loopback literal deliberately. Apart from making the transport scope explicit,
    // this avoids emitting an invalid unbracketed IPv6 URL when Android resolves "loopback" to ::1.
    // The application network-security policy can consequently allow exactly 127.0.0.1.
    private val server = ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getByName(IPV4_LOOPBACK))
    private val acceptThread = Thread(::acceptLoop, "yokuli-chart-loopback").apply {
        isDaemon = true
        start()
    }

    val localAddress: InetAddress get() = server.inetAddress
    val localPort: Int get() = server.localPort
    val requestCount = AtomicLong(0L)
    val rejectionCount = AtomicLong(0L)

    fun metrics(): ChartLoopbackGatewayMetrics = ChartLoopbackGatewayMetrics(
        registeredAssetCount = registrations.size,
        activeRequestCount = activeWorkers.get(),
        queuedRequestCount = workers.queue.size,
        requestHighWater = workerHighWater.get(),
        servedRequestCount = requestCount.get(),
        rejectedRequestCount = rejectionCount.get(),
    )

    fun register(
        session: ChartReadSession,
        scheme: MapTileScheme,
        tileSize: Int,
        minZoom: Int = 0,
        maxZoom: Int = 24,
    ): ChartRasterRegistration {
        check(!closed.get()) { "Gateway is closed" }
        require(tileSize == 256 || tileSize == 512)
        require(minZoom in 0..24 && maxZoom in minZoom..24)
        val route = RouteKey(
            session.request.assetId.value,
            session.request.revision.routeKey() + "-" + session.request.sourceGeneration,
        )
        val proposed = RegisteredAsset(session, scheme, tileSize, minZoom, maxZoom)
        val asset = try {
            synchronized(registrations) {
                val existing = registrations[route]
                if (existing == null) {
                    check(registrations.size < MAX_REGISTERED_ASSETS) { "Chart gateway registration limit reached" }
                    registrations[route] = proposed
                    proposed
                } else {
                    check(
                        existing.scheme == scheme && existing.tileSize == tileSize &&
                            existing.minZoom == minZoom && existing.maxZoom == maxZoom,
                    ) { "Asset revision was registered with incompatible raster facts" }
                    existing.leaseCount += 1
                    existing
                }
            }
        } catch (error: Throwable) {
            session.close()
            throw error
        }
        if (asset !== proposed) session.close()
        val template = "http://${localAddress.hostAddress}:$localPort/$token/${route.assetId}/${route.revision}/{z}/{x}/{y}"
        return ChartRasterRegistration(
            assetId = route.assetId,
            revisionRouteKey = route.revision,
            tileUrlTemplate = template,
            tileSize = tileSize,
            minZoom = minZoom,
            maxZoom = maxZoom,
        ) {
            val closeSession = synchronized(registrations) {
                asset.leaseCount -= 1
                check(asset.leaseCount >= 0) { "Chart gateway lease count underflow" }
                asset.leaseCount == 0 && registrations.remove(route, asset)
            }
            if (closeSession) asset.session.close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        registrations.values.toSet().forEach { runCatching { it.session.close() } }
        registrations.clear()
        workers.shutdownNow()
        acceptThread.interrupt()
    }

    private fun acceptLoop() {
        while (!closed.get()) {
            val socket = try {
                server.accept()
            } catch (_: SocketException) {
                break
            } catch (_: Throwable) {
                if (closed.get()) break else continue
            }
            if (!socket.inetAddress.isLoopbackAddress) {
                rejectionCount.incrementAndGet()
                socket.close()
                continue
            }
            try {
                workers.execute {
                    val active = activeWorkers.incrementAndGet()
                    workerHighWater.accumulateAndGet(active) { left, right -> maxOf(left, right) }
                    try {
                        socket.use(::serve)
                    } finally {
                        activeWorkers.decrementAndGet()
                    }
                }
            } catch (_: RejectedExecutionException) {
                rejectionCount.incrementAndGet()
                socket.close()
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val requestLine = input.readBoundedAsciiLine(MAX_REQUEST_LINE_BYTES)
            ?: return output.respond(400, "text/plain", "bad request".encodeToByteArray())
        var headerBytes = requestLine.length
        while (true) {
            val line = input.readBoundedAsciiLine(MAX_HEADER_LINE_BYTES)
                ?: return output.respond(400, "text/plain", "bad headers".encodeToByteArray())
            headerBytes += line.length
            if (headerBytes > MAX_HEADER_BYTES) return output.respond(431, "text/plain", byteArrayOf())
            if (line.isEmpty()) break
        }
        val parts = requestLine.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) {
            rejectionCount.incrementAndGet()
            return output.respond(400, "text/plain", byteArrayOf())
        }
        if (parts[0] != "GET") {
            rejectionCount.incrementAndGet()
            return output.respond(405, "text/plain", byteArrayOf())
        }
        val path = parts[1].substringBefore('?')
        if (path.length > MAX_PATH_BYTES || "%" in path || ".." in path || '\u0000' in path) {
            rejectionCount.incrementAndGet()
            return output.respond(400, "text/plain", byteArrayOf())
        }
        val segments = path.removePrefix("/").split('/')
        if (segments.size != 6 || segments[0] != token) {
            rejectionCount.incrementAndGet()
            return output.respond(404, "text/plain", byteArrayOf())
        }
        val asset = registrations[RouteKey(segments[1], segments[2])]
            ?: return output.respond(404, "text/plain", byteArrayOf())
        val key = try {
            val zoom = segments[3].toInt()
            if (zoom !in asset.minZoom..asset.maxZoom) throw IllegalArgumentException()
            ChartTileKey(zoom, segments[4].toLong(), segments[5].toLong())
        } catch (_: Throwable) {
            null
        }
        if (key == null) {
            rejectionCount.incrementAndGet()
            return output.respond(400, "text/plain", byteArrayOf())
        }
        requestCount.incrementAndGet()
        val tile = try {
            asset.session.readTile(key, asset.scheme)
        } catch (_: Throwable) {
            return output.respond(503, "text/plain", byteArrayOf())
        } ?: return output.respond(404, "text/plain", byteArrayOf())
        output.respond(200, tile.mimeType, tile.bytes)
    }

    private data class RouteKey(val assetId: String, val revision: String)
    private data class RegisteredAsset(
        val session: ChartReadSession,
        val scheme: MapTileScheme,
        val tileSize: Int,
        val minZoom: Int,
        val maxZoom: Int,
        var leaseCount: Int = 1,
    )

    companion object {
        private const val TOKEN_BYTES = 16
        private const val IPV4_LOOPBACK = "127.0.0.1"
        private const val MAX_CONCURRENT_REQUESTS = 8
        private const val MAX_QUEUED_REQUESTS = 16
        private const val MAX_REGISTERED_ASSETS = 8
        private const val ACCEPT_BACKLOG = 16
        private const val SOCKET_TIMEOUT_MILLIS = 5_000
        private const val MAX_REQUEST_LINE_BYTES = 1_024
        private const val MAX_HEADER_LINE_BYTES = 2_048
        private const val MAX_HEADER_BYTES = 8_192
        private const val MAX_PATH_BYTES = 768
    }
}

data class ChartLoopbackGatewayMetrics(
    val registeredAssetCount: Int,
    val activeRequestCount: Int,
    val queuedRequestCount: Int,
    val requestHighWater: Int,
    val servedRequestCount: Long,
    val rejectedRequestCount: Long,
)

class ChartRasterRegistration internal constructor(
    val assetId: String,
    val revisionRouteKey: String,
    val tileUrlTemplate: String,
    val tileSize: Int,
    val minZoom: Int,
    val maxZoom: Int,
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) release() }

    fun toRasterSource(sourceId: String): RasterSource {
        val tiles = TileSet("2.2.0", tileUrlTemplate).apply {
            setMinZoom(this@ChartRasterRegistration.minZoom.toFloat())
            setMaxZoom(this@ChartRasterRegistration.maxZoom.toFloat())
            scheme = "xyz"
        }
        return RasterSource(sourceId, tiles, tileSize)
    }
}

private fun com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision.routeKey(): String =
    MessageDigest.getInstance("SHA-256").digest(cacheKey.encodeToByteArray()).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun BufferedInputStream.readBoundedAsciiLine(maxBytes: Int): String? {
    val bytes = ByteArray(maxBytes)
    var count = 0
    while (count < maxBytes) {
        val value = read()
        if (value == -1) return null
        if (value == '\n'.code) {
            val end = if (count > 0 && bytes[count - 1] == '\r'.code.toByte()) count - 1 else count
            return String(bytes, 0, end, StandardCharsets.US_ASCII)
        }
        bytes[count++] = value.toByte()
    }
    return null
}

private fun BufferedOutputStream.respond(code: Int, contentType: String, body: ByteArray) {
    val reason = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        431 -> "Request Header Fields Too Large"
        else -> "Service Unavailable"
    }
    write(
        (
            "HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
            ).encodeToByteArray(),
    )
    write(body)
    flush()
}

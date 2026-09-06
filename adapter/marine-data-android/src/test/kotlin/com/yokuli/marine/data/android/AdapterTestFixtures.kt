package com.yokuli.marine.data.android

import com.yokuli.marine.data.android.persistence.ProtoDataStoreConnectionRepository
import com.yokuli.marine.data.android.runtime.AndroidNmeaInputRuntime
import com.yokuli.marine.data.android.runtime.NetworkAvailabilityPort
import com.yokuli.marine.data.android.runtime.SocketNmeaTransportFactory
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.nmea.NmeaChecksum
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.ConnectionRuntimeSnapshot
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.time.MonotonicClock
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal fun tcpConfig(
    id: String = "tcp",
    host: String = "127.0.0.1",
    port: Int,
    name: String = "Boat gateway",
): NmeaConnectionConfig = NmeaConnectionConfig(
    id = ConnectionId(id),
    displayName = name,
    endpoint = NmeaEndpoint.TcpClient(host = host, port = port),
    checksumPolicy = ChecksumPolicy.STRICT,
)

internal fun udpConfig(
    id: String = "udp",
    port: Int,
    name: String = "Boat listener",
    allowedSenderHost: String? = null,
): NmeaConnectionConfig = NmeaConnectionConfig(
    id = ConnectionId(id),
    displayName = name,
    endpoint = NmeaEndpoint.UdpListener(
        localPort = port,
        senderHostFilter = allowedSenderHost,
    ),
    checksumPolicy = ChecksumPolicy.STRICT,
)

internal fun nmea(body: String): String = NmeaChecksum.append(body) + "\r\n"

internal fun badChecksum(body: String): String = NmeaChecksum.append(body).let { valid ->
    valid.dropLast(2) + if (valid.endsWith("00")) "FF" else "00"
} + "\r\n"

internal class MutableNetworkAvailability(initiallyAvailable: Boolean) : NetworkAvailabilityPort {
    private val mutable = MutableStateFlow(initiallyAvailable)
    override val available: StateFlow<Boolean> = mutable

    fun setAvailable(value: Boolean) {
        mutable.value = value
    }
}

internal class RuntimeFixture(
    storageFile: File,
    networkAvailability: NetworkAvailabilityPort = MutableNetworkAvailability(true),
    transportFactory: com.yokuli.marine.data.android.transport.NmeaTransportFactory =
        SocketNmeaTransportFactory(),
    reconnectDelay: com.yokuli.marine.data.android.runtime.ReconnectDelayPort =
        com.yokuli.marine.data.android.runtime.ReconnectDelayPort.SYSTEM,
) : Closeable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = ProtoDataStoreConnectionRepository.create(
        storageFile = storageFile,
        scope = scope,
    )
    val runtime: NmeaInputRuntimePort = AndroidNmeaInputRuntime(
        repository = repository,
        transportFactory = transportFactory,
        networkAvailability = networkAvailability,
        clock = MonotonicClock { System.nanoTime() / 1_000_000L },
        reconnectDelay = reconnectDelay,
        applicationScope = scope,
    )

    suspend fun start(config: NmeaConnectionConfig) {
        runtime.execute(NmeaRuntimeCommand.SaveAndStart(config))
    }

    suspend fun stop(id: ConnectionId) {
        runtime.execute(NmeaRuntimeCommand.Stop(id))
    }

    suspend fun await(
        timeoutMillis: Long = 5_000L,
        predicate: (NmeaRuntimeSnapshot) -> Boolean,
    ): NmeaRuntimeSnapshot = withTimeout(timeoutMillis) {
        runtime.snapshots.first(predicate)
    }

    override fun close() {
        scope.cancel()
    }
}

internal class LoopbackTcpServer(
    private val script: suspend (Socket) -> Unit,
) : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val acceptJob: Job
    val acceptedCount = AtomicInteger(0)
    val accepted = CompletableDeferred<Unit>()
    val port: Int get() = server.localPort

    init {
        acceptJob = scope.launch {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                acceptedCount.incrementAndGet()
                accepted.complete(Unit)
                launch {
                    socket.use { script(it) }
                }
            }
        }
    }

    override fun close() {
        server.close()
        acceptJob.cancel()
        scope.cancel()
    }
}

internal suspend fun awaitCondition(
    timeoutMillis: Long = 5_000L,
    predicate: () -> Boolean,
) {
    withTimeout(timeoutMillis) {
        while (!predicate()) {
            kotlinx.coroutines.yield()
        }
    }
}

internal fun ConnectionRunIntent.isStopped(): Boolean = this == ConnectionRunIntent.STOPPED_BY_USER

internal operator fun List<ConnectionRuntimeSnapshot>.get(
    connectionId: ConnectionId,
): ConnectionRuntimeSnapshot? = singleOrNull { it.stored.config.id == connectionId }

internal fun List<ConnectionRuntimeSnapshot>.getValue(
    connectionId: ConnectionId,
): ConnectionRuntimeSnapshot = requireNotNull(this[connectionId]) {
    "No runtime snapshot for ${connectionId.value}"
}

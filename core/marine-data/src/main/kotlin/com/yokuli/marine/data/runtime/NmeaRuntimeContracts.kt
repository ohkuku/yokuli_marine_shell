package com.yokuli.marine.data.runtime

import com.yokuli.marine.data.catalog.ObservationCatalogSnapshot
import com.yokuli.marine.data.catalog.RawPreviewSnapshot
import com.yokuli.marine.data.catalog.SentenceCatalogSnapshot
import com.yokuli.marine.data.connection.ConnectionConfigIssue
import com.yokuli.marine.data.connection.MAX_NMEA_CONNECTIONS
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.nmea.MAX_FRAME_BYTES
import kotlinx.coroutines.flow.StateFlow

const val MAX_INGRESS_FRAMES_PER_CONNECTION: Int = 64
const val MAX_STRUCTURED_INCIDENTS: Int = 128
const val MAX_OBSERVED_UDP_SENDERS_PER_CONNECTION: Int = 32

data class SessionToken(
    val connectionId: ConnectionId,
    val configRevision: Long,
    val generation: SessionGeneration,
) {
    init {
        require(configRevision >= 0L) { "Session config revision must be non-negative" }
    }
}

sealed interface ConnectionTransportState {
    data object Stopped : ConnectionTransportState
    data object Starting : ConnectionTransportState
    data object TcpConnected : ConnectionTransportState
    data object UdpListening : ConnectionTransportState
    data object WaitingForNetwork : ConnectionTransportState

    data class ReconnectWaiting(
        val attempt: Int,
        val retryAtMillis: Long,
    ) : ConnectionTransportState {
        init {
            require(attempt >= 1) { "Reconnect attempt is one-based" }
            require(retryAtMillis >= 0L) { "Retry time must be monotonic and non-negative" }
        }
    }

    data object PlatformStartRequired : ConnectionTransportState
    data object Failed : ConnectionTransportState
}

enum class ConnectionInputState {
    NO_BYTES,
    BYTES_WITHOUT_VALID_FRAME,
    RECEIVING_VALID_FRAMES,
    INTERRUPTED,
    OVERLOADED,
}

data class FiveSecondFrameRate(
    val framesPerSecond: Double,
    val retainedBucketCount: Int,
) {
    init {
        require(framesPerSecond.isFinite() && framesPerSecond >= 0.0) {
            "Frame rate must be finite and non-negative"
        }
        require(retainedBucketCount in 0..MAX_BUCKETS) { "Five-second rate retains at most five buckets" }
    }

    companion object {
        const val WINDOW_MILLIS: Long = 5_000L
        const val BUCKET_MILLIS: Long = 1_000L
        const val MAX_BUCKETS: Int = 5
        val EMPTY = FiveSecondFrameRate(framesPerSecond = 0.0, retainedBucketCount = 0)
    }
}

/** Bounded counters and gauges for one saved connection. */
data class NmeaConnectionDiagnostics(
    val byteCount: Long = 0L,
    val datagramCount: Long = 0L,
    val frameCount: Long = 0L,
    val legalFrameCount: Long = 0L,
    val parsedFrameCount: Long = 0L,
    val parsedObservationCount: Long = 0L,
    val positionFrameCount: Long = 0L,
    val explicitInvalidFrameCount: Long = 0L,
    val unsupportedFrameCount: Long = 0L,
    val checksumFailureCount: Long = 0L,
    val malformedFrameCount: Long = 0L,
    val staleGenerationDropCount: Long = 0L,
    val ingressDropCount: Long = 0L,
    val rawPreviewDropCount: Long = 0L,
    val frameOverflowCount: Long = 0L,
    val datagramOverflowCount: Long = 0L,
    val tcpBufferedByteCount: Int = 0,
    val pendingIngressFrameCount: Int = 0,
    val observedUdpSenders: Set<SenderIdentity> = emptySet(),
    val validRate: FiveSecondFrameRate = FiveSecondFrameRate.EMPTY,
    val lastByteAtMillis: Long? = null,
    val lastValidFrameAtMillis: Long? = null,
    val inputInterruptedAtMillis: Long? = null,
) {
    init {
        val counters = listOf(
            byteCount,
            datagramCount,
            frameCount,
            legalFrameCount,
            parsedFrameCount,
            parsedObservationCount,
            positionFrameCount,
            explicitInvalidFrameCount,
            unsupportedFrameCount,
            checksumFailureCount,
            malformedFrameCount,
            staleGenerationDropCount,
            ingressDropCount,
            rawPreviewDropCount,
            frameOverflowCount,
            datagramOverflowCount,
        )
        require(counters.all { it >= 0L }) { "Diagnostic counters must be non-negative" }
        require(tcpBufferedByteCount in 0..MAX_FRAME_BYTES) { "TCP framing buffer is outside its bound" }
        require(pendingIngressFrameCount in 0..MAX_INGRESS_FRAMES_PER_CONNECTION) {
            "Pending ingress count is outside its bound"
        }
        require(observedUdpSenders.size <= MAX_OBSERVED_UDP_SENDERS_PER_CONNECTION) {
            "Observed UDP sender diagnostics are outside their bound"
        }
        require(listOfNotNull(lastByteAtMillis, lastValidFrameAtMillis, inputInterruptedAtMillis).all { it >= 0L }) {
            "Diagnostic timestamps must be monotonic and non-negative"
        }
    }

    /** Legal includes Parsed, ExplicitInvalid, and Unsupported parser results. */
    val lastLegalFrameAtMillis: Long?
        get() = lastValidFrameAtMillis

    val staleSessionDropCount: Long
        get() = staleGenerationDropCount

    companion object {
        val EMPTY = NmeaConnectionDiagnostics()
    }
}

data class ConnectionRuntimeSnapshot(
    val stored: StoredNmeaConnection,
    val token: SessionToken?,
    val transport: ConnectionTransportState,
    val input: ConnectionInputState,
    val metrics: NmeaConnectionDiagnostics,
    val failure: NmeaRuntimeFailure? = null,
) {
    init {
        token?.let {
            require(it.connectionId == stored.config.id) { "Session token belongs to another connection" }
            require(it.configRevision == stored.revision) { "Session token belongs to another config revision" }
        }
        require(transport != ConnectionTransportState.Failed || failure != null) {
            "A failed transport requires a typed failure"
        }
    }
}

enum class TransportFailureKind {
    DNS,
    TIMEOUT,
    CONNECTION_REFUSED,
    IO,
    UDP_BIND,
    PLATFORM,
    UNKNOWN,
}

sealed interface NmeaRuntimeFailure {
    data class InvalidConfiguration(val issues: Set<ConnectionConfigIssue>) : NmeaRuntimeFailure {
        init {
            require(issues.isNotEmpty()) { "Invalid configuration requires at least one issue" }
        }
    }
    data class DuplicateTcpEndpoint(val existingId: ConnectionId) : NmeaRuntimeFailure
    data object ActiveConnectionCapacityExceeded : NmeaRuntimeFailure
    data object ConnectionNotFound : NmeaRuntimeFailure
    data object StaleSession : NmeaRuntimeFailure
    data object PersistenceFailed : NmeaRuntimeFailure
    data class TransportFailed(val kind: TransportFailureKind) : NmeaRuntimeFailure
    data class UdpAddressInUse(val port: Int) : NmeaRuntimeFailure {
        init {
            require(port in 1..65_535) { "UDP conflict port is outside the valid range" }
        }
    }
    data object PlatformRestricted : NmeaRuntimeFailure
    data object IngressOverloaded : NmeaRuntimeFailure
    data object InternalError : NmeaRuntimeFailure
}

data class NmeaRuntimeIncident(
    val connectionId: ConnectionId?,
    val sessionToken: SessionToken?,
    val failure: NmeaRuntimeFailure,
    val occurredAtMillis: Long,
) {
    init {
        require(occurredAtMillis >= 0L) { "Incident time must be monotonic and non-negative" }
        require(sessionToken == null || connectionId == sessionToken.connectionId) {
            "Incident connection and session token must agree"
        }
    }
}

data class NmeaRuntimeSnapshot(
    val connections: List<ConnectionRuntimeSnapshot>,
    val sentenceCatalog: SentenceCatalogSnapshot,
    val observationCatalog: ObservationCatalogSnapshot,
    val rawPreview: RawPreviewSnapshot,
    val incidents: List<NmeaRuntimeIncident>,
    val activeSocketCount: Int,
    val activeTaskCount: Int,
    val revision: Long,
) {
    init {
        require(connections.size <= MAX_NMEA_CONNECTIONS) { "Runtime connection list exceeds its bound" }
        require(connections.map { it.stored.config.id }.distinct().size == connections.size) {
            "Runtime connection identities must be unique"
        }
        require(incidents.size <= MAX_STRUCTURED_INCIDENTS) { "Runtime incident list exceeds its bound" }
        require(activeSocketCount >= 0 && activeTaskCount >= 0) { "Runtime ownership counts must be non-negative" }
        require(activeSocketCount <= connections.size && activeTaskCount <= connections.size) {
            "A connection may own at most one socket and one runtime task"
        }
        require(revision >= 0L) { "Runtime revision must be non-negative" }
    }

    companion object {
        val EMPTY = NmeaRuntimeSnapshot(
            connections = emptyList(),
            sentenceCatalog = SentenceCatalogSnapshot(
                entries = emptyList(),
                capacityEvictionCount = 0L,
                capacityRejectionCount = 0L,
                staleSessionDropCount = 0L,
                outOfOrderDropCount = 0L,
            ),
            observationCatalog = ObservationCatalogSnapshot(
                candidates = emptyList(),
                evaluatedAtMillis = 0L,
                capacityEvictionCount = 0L,
                capacityRejectionCount = 0L,
                staleSessionDropCount = 0L,
                outOfOrderDropCount = 0L,
                streamCapacityEvictionCount = 0L,
                streamCapacityRejectionCount = 0L,
                streamHighWaterMark = 0,
            ),
            rawPreview = RawPreviewSnapshot(
                entries = emptyList(),
                retainedBytes = 0,
                overflowEntryCount = 0L,
                overflowByteCount = 0L,
                oversizeRejectionCount = 0L,
                staleSessionDropCount = 0L,
            ),
            incidents = emptyList(),
            activeSocketCount = 0,
            activeTaskCount = 0,
            revision = 0L,
        )
    }
}

sealed interface NmeaRuntimeCommand {
    data class Save(
        val config: NmeaConnectionConfig,
        val expectedRevision: Long? = null,
        val duplicateTcpConfirmed: Boolean = false,
    ) : NmeaRuntimeCommand {
        init {
            require(expectedRevision == null || expectedRevision >= 0L)
        }
    }

    data class SaveAndStart(
        val config: NmeaConnectionConfig,
        val expectedRevision: Long? = null,
        val duplicateTcpConfirmed: Boolean = false,
    ) : NmeaRuntimeCommand {
        init {
            require(expectedRevision == null || expectedRevision >= 0L)
        }
    }

    data class Start(val connectionId: ConnectionId) : NmeaRuntimeCommand
    data class Retry(val connectionId: ConnectionId) : NmeaRuntimeCommand
    data class Stop(val connectionId: ConnectionId) : NmeaRuntimeCommand
    data class Delete(val connectionId: ConnectionId, val expectedRevision: Long? = null) : NmeaRuntimeCommand {
        init {
            require(expectedRevision == null || expectedRevision >= 0L)
        }
    }
}

sealed interface NmeaRuntimeCommandResult {
    data class Success(
        val connectionId: ConnectionId?,
        val revision: Long,
    ) : NmeaRuntimeCommandResult {
        init {
            require(revision >= 0L) { "Result revision must be non-negative" }
        }
    }

    data class Rejected(val failure: NmeaRuntimeFailure) : NmeaRuntimeCommandResult
}

/** The only Feature-facing connection runtime port: one StateFlow truth and serialized commands. */
interface NmeaInputRuntimePort {
    val state: StateFlow<NmeaRuntimeSnapshot>

    suspend fun execute(command: NmeaRuntimeCommand): NmeaRuntimeCommandResult
}

package com.yokuli.marine.data.android.runtime

import com.yokuli.marine.data.android.transport.NetworkTransportEvent
import com.yokuli.marine.data.android.transport.NmeaTransportFactory
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaConnectionProbePort
import com.yokuli.marine.data.connection.NmeaConnectionProbeResult
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.nmea.Nmea0183Parser
import com.yokuli.marine.data.nmea.NmeaFrameEvent
import com.yokuli.marine.data.nmea.NmeaParseContext
import com.yokuli.marine.data.nmea.NmeaParseResult
import com.yokuli.marine.data.nmea.TcpNmeaFramer
import com.yokuli.marine.data.nmea.UdpNmeaDatagramFramer
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.SessionToken
import com.yokuli.marine.data.runtime.TransportFailureKind
import com.yokuli.marine.data.time.MonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one disposable transport session. It never touches the connection repository, foreground
 * service, runtime catalogs or durable run intent.
 */
class AndroidNmeaConnectionProbe(
    private val transportFactory: NmeaTransportFactory,
    private val clock: MonotonicClock,
) : NmeaConnectionProbePort {
    override suspend fun probe(
        config: NmeaConnectionConfig,
        timeoutMillis: Long,
    ): NmeaConnectionProbeResult = coroutineScope {
        require(timeoutMillis > 0L)
        val result = CompletableDeferred<NmeaConnectionProbeResult>()
        val parser = Nmea0183Parser(config.checksumPolicy)
        val tcp = TcpNmeaFramer()
        val udp = UdpNmeaDatagramFramer()
        val token = SessionToken(config.id, 0L, SessionGeneration(1L))
        val lock = Any()
        var transportReady = false
        var legalFrames = 0L
        var sequence = 0L
        val keys = linkedSetOf<DataKey>()

        fun parse(frame: NmeaFrameEvent, sender: com.yokuli.marine.data.model.SenderIdentity?) {
            if (frame !is NmeaFrameEvent.Frame || result.isCompleted) return
            sequence += 1L
            val source = when (val endpoint = config.endpoint) {
                is NmeaEndpoint.TcpClient -> SourceIdentity(config.id)
                is NmeaEndpoint.UdpListener -> endpoint.originIdentityPolicy.sourceIdentity(
                    config.id,
                    requireNotNull(sender) { "A UDP datagram requires observed sender provenance" },
                )
            }
            when (val parsed = parser.parse(
                frame.raw,
                NmeaParseContext(
                    source = source,
                    sessionGeneration = token.generation,
                    receivedAtMonotonicMillis = clock.nowMillis(),
                    observationGroupId = ObservationGroupId(sequence),
                    sender = sender,
                ),
            )) {
                is NmeaParseResult.Parsed -> synchronized(lock) {
                    legalFrames += 1L
                    keys += parsed.observations.map { it.key }
                }
                is NmeaParseResult.ExplicitInvalid -> synchronized(lock) {
                    legalFrames += 1L
                    keys += parsed.observations.map { it.key }
                }
                is NmeaParseResult.Unsupported -> synchronized(lock) { legalFrames += 1L }
                is NmeaParseResult.ChecksumFailure, is NmeaParseResult.Malformed -> Unit
            }
            val detected = synchronized(lock) {
                keys.takeIf { it.isNotEmpty() }?.toSet()?.let { NmeaConnectionProbeResult.Detected(it, legalFrames) }
            }
            if (detected != null) result.complete(detected)
        }

        val transport = launch {
            try {
                transportFactory.run(config, token) { event ->
                    when (event) {
                        NetworkTransportEvent.TcpConnected,
                        is NetworkTransportEvent.UdpListening,
                        -> synchronized(lock) { transportReady = true }
                        is NetworkTransportEvent.TcpBytes -> tcp.accept(event.bytes).forEach { parse(it, null) }
                        is NetworkTransportEvent.UdpDatagram -> udp.frame(event.bytes, event.sender).events.forEach { parse(it, event.sender) }
                        is NetworkTransportEvent.Failed -> result.complete(NmeaConnectionProbeResult.Failed(event.failure))
                    }
                }
                if (!result.isCompleted) {
                    result.complete(
                        synchronized(lock) { NmeaConnectionProbeResult.NoSemanticData(transportReady, legalFrames) },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                result.complete(
                    NmeaConnectionProbeResult.Failed(
                        NmeaRuntimeFailure.TransportFailed(TransportFailureKind.UNKNOWN),
                    ),
                )
            }
        }
        val outcome = withTimeoutOrNull(timeoutMillis) { result.await() }
            ?: synchronized(lock) { NmeaConnectionProbeResult.NoSemanticData(transportReady, legalFrames) }
        transport.cancel()
        transport.join()
        outcome
    }
}

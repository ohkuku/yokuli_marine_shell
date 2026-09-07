package com.yokuli.marine.data.android

import com.yokuli.marine.data.android.runtime.AndroidNmeaConnectionProbe
import com.yokuli.marine.data.android.transport.NetworkTransportEvent
import com.yokuli.marine.data.android.transport.NmeaTransportFactory
import com.yokuli.marine.data.connection.NmeaConnectionProbeResult
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.time.MonotonicClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNmeaConnectionProbeTest {
    @Test
    fun realParserEvidenceDetectsSemanticCapabilities() = runBlocking {
        val probe = probe { emit ->
            emit(NetworkTransportEvent.TcpConnected)
            emit(
                NetworkTransportEvent.TcpBytes(
                    nmea(
                        "GPRMC,120000.00,A,3650.0000,S,17445.0000,E,4.2,183.0,010126,,,A",
                    ).toByteArray(Charsets.US_ASCII),
                ),
            )
        }

        val result = probe.probe(tcpConfig(port = 10_110), timeoutMillis = 1_000L)

        assertTrue(result is NmeaConnectionProbeResult.Detected)
        result as NmeaConnectionProbeResult.Detected
        assertTrue(DataKey.Position in result.dataKeys)
        assertTrue(DataKey.SpeedOverGround in result.dataKeys)
        assertEquals(1L, result.legalFrameCount)
    }

    @Test
    fun transportAndUnknownLegalSentenceDoNotMasqueradeAsSemanticSuccess() = runBlocking {
        val probe = probe { emit ->
            emit(NetworkTransportEvent.TcpConnected)
            emit(
                NetworkTransportEvent.TcpBytes(
                    nmea("GPXYZ,1").toByteArray(Charsets.US_ASCII),
                ),
            )
        }

        val result = probe.probe(tcpConfig(port = 10_110), timeoutMillis = 1_000L)

        assertEquals(
            NmeaConnectionProbeResult.NoSemanticData(
                transportReady = true,
                legalFrameCount = 1L,
            ),
            result,
        )
    }

    @Test
    fun udpProbePreservesObservedSenderWhileDetectingCapabilities() = runBlocking {
        val sender = SenderIdentity("192.168.4.20", 41_100)
        val probe = AndroidNmeaConnectionProbe(
            transportFactory = NmeaTransportFactory { _, _, emit ->
                emit(NetworkTransportEvent.UdpListening(10_110))
                emit(
                    NetworkTransportEvent.UdpDatagram(
                        nmea("SDDPT,12.4,0.0").toByteArray(Charsets.US_ASCII),
                        sender,
                    ),
                )
            },
            clock = MonotonicClock { 100L },
        )

        val result = probe.probe(udpConfig(port = 10_110), timeoutMillis = 1_000L)

        assertTrue(result is NmeaConnectionProbeResult.Detected)
        result as NmeaConnectionProbeResult.Detected
        assertTrue(result.dataKeys.any { it is DataKey.Depth })
    }

    @Test
    fun timedOutProbeCancelsItsDisposableTransport() = runBlocking {
        val cancelled = AtomicBoolean(false)
        val probe = AndroidNmeaConnectionProbe(
            transportFactory = NmeaTransportFactory { _, _, emit ->
                emit(NetworkTransportEvent.TcpConnected)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.set(true)
                }
            },
            clock = MonotonicClock { 100L },
        )

        val result = probe.probe(tcpConfig(port = 10_110), timeoutMillis = 20L)

        assertEquals(NmeaConnectionProbeResult.NoSemanticData(true, 0L), result)
        assertTrue(cancelled.get())
    }

    private fun probe(
        events: suspend (emit: suspend (NetworkTransportEvent) -> Unit) -> Unit,
    ): AndroidNmeaConnectionProbe = AndroidNmeaConnectionProbe(
        transportFactory = NmeaTransportFactory { _, _, emit -> events(emit) },
        clock = MonotonicClock { 100L },
    )
}

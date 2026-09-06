package com.yokuli.marine.data.runtime

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.nmea.ChecksumPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun reconnectDelayUsesOneTwoFourEightSixteenThenThirtySecondCap() {
        val policy = RetryPolicy(jitter = RetryJitter.NONE)

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L),
            (1..8).map(policy::delayMillis),
        )
    }

    @Test
    fun jitterIsInjectedAfterTheBoundedBaseDelayIsChosen() {
        val calls = mutableListOf<Pair<Int, Long>>()
        val policy = RetryPolicy(
            jitter = RetryJitter { attempt, baseDelayMillis ->
                calls += attempt to baseDelayMillis
                baseDelayMillis + 7L
            },
        )

        assertEquals(30_007L, policy.delayMillis(attempt = 7))
        assertEquals(listOf(7 to 30_000L), calls)
    }

    @Test
    fun desiredTransportAndInputHealthAreOrthogonalFacts() {
        val connectionId = ConnectionId("tcp")
        val stored = StoredNmeaConnection(
            config = NmeaConnectionConfig(
                id = connectionId,
                displayName = "Boat gateway",
                endpoint = NmeaEndpoint.TcpClient("127.0.0.1", 10_111),
                checksumPolicy = ChecksumPolicy.STRICT,
            ),
            runIntent = ConnectionRunIntent.ENABLED,
            revision = 3L,
        )
        val connectedWithoutData = ConnectionRuntimeSnapshot(
            stored = stored,
            token = SessionToken(
                connectionId = connectionId,
                configRevision = 3L,
                generation = SessionGeneration(4),
            ),
            transport = ConnectionTransportState.TcpConnected,
            input = ConnectionInputState.NO_BYTES,
            metrics = NmeaConnectionDiagnostics.EMPTY,
        )

        assertEquals(ConnectionRunIntent.ENABLED, connectedWithoutData.stored.runIntent)
        assertSame(ConnectionTransportState.TcpConnected, connectedWithoutData.transport)
        assertSame(ConnectionInputState.NO_BYTES, connectedWithoutData.input)

        val interrupted = connectedWithoutData.copy(
            input = ConnectionInputState.INTERRUPTED,
            metrics = connectedWithoutData.metrics.copy(lastLegalFrameAtMillis = 1_000L),
        )
        assertEquals(ConnectionRunIntent.ENABLED, interrupted.stored.runIntent)
        assertEquals(connectedWithoutData.transport, interrupted.transport)
        assertSame(ConnectionInputState.INTERRUPTED, interrupted.input)

        val udpListening = connectedWithoutData.copy(
            stored = stored.copy(
                config = stored.config.copy(
                    id = ConnectionId("udp"),
                    endpoint = NmeaEndpoint.UdpListener(localPort = 10_112),
                ),
            ),
            token = SessionToken(ConnectionId("udp"), 3L, SessionGeneration(4)),
            transport = ConnectionTransportState.UdpListening,
            input = ConnectionInputState.NO_BYTES,
        )
        assertSame(ConnectionTransportState.UdpListening, udpListening.transport)
        assertSame(ConnectionInputState.NO_BYTES, udpListening.input)
    }
}

package com.yokuli.marine.data.runtime

import com.yokuli.marine.data.catalog.MAX_RAW_BYTES
import com.yokuli.marine.data.catalog.MAX_RAW_ENTRIES
import com.yokuli.marine.data.catalog.MAX_SENTENCE_KEYS
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.nmea.NmeaChecksum
import com.yokuli.marine.data.session.ActiveSessionRegistry
import com.yokuli.marine.data.time.MonotonicClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaInboundPipelineSoakTest {
    @Test
    fun virtualThirtyMinuteHundredHertzFourConnectionSoakRemainsBounded() {
        val clock = SoakClock()
        val sessions = ActiveSessionRegistry()
        val connections = (0 until CONNECTIONS).map { index ->
            val id = ConnectionId("soak-$index")
            sessions.beginSession(id, SessionGeneration(1))
            connection(id, index)
        }
        val pipeline = NmeaInboundPipeline(clock, sessions, connections)
        val expectedFrames = IntArray(CONNECTIONS)
        var badFrames = 0

        repeat(TOTAL_FRAMES) { sequence ->
            val receivedAtMillis = sequence.toLong() * FRAME_INTERVAL_MILLIS
            val connectionIndex = sequence % CONNECTIONS
            if (connectionIndex == SILENT_CONNECTION && receivedAtMillis in SILENT_WINDOW) return@repeat
            val connection = connections[connectionIndex]
            val bad = sequence % BAD_FRAME_INTERVAL == 0
            val raw = if (bad) corruptChecksum(RMC) else RMC
            expectedFrames[connectionIndex]++
            badFrames += if (bad) 1 else 0
            clock.now = receivedAtMillis
            assertTrue(
                pipeline.accept(
                    NmeaInboundFrame(
                        source = SourceIdentity(connection.stored.config.id),
                        sessionToken = requireNotNull(connection.token),
                        receivedAtMillis = receivedAtMillis,
                        groupId = ObservationGroupId(sequence.toLong() + 1L),
                        raw = raw,
                    ),
                ),
            )
        }

        clock.now = DURATION_MILLIS.toLong()
        val snapshot = pipeline.snapshot()
        assertEquals(TOTAL_FRAMES - SILENT_FRAME_COUNT, snapshot.connections.sumOf { it.metrics.frameCount }.toInt())
        assertEquals(badFrames.toLong(), snapshot.connections.sumOf { it.metrics.checksumFailureCount })
        snapshot.connections.forEachIndexed { index, connection ->
            assertEquals(expectedFrames[index].toLong(), connection.metrics.frameCount)
            assertTrue(connection.metrics.validRate.retainedBucketCount <= 5)
            assertTrue(connection.metrics.observedUdpSenders.size <= MAX_OBSERVED_UDP_SENDERS_PER_CONNECTION)
        }
        assertTrue(snapshot.rawPreview.entries.size <= MAX_RAW_ENTRIES)
        assertTrue(snapshot.rawPreview.retainedBytes <= MAX_RAW_BYTES)
        assertTrue(snapshot.sentenceCatalog.entries.size <= MAX_SENTENCE_KEYS)
        assertTrue(snapshot.observationCatalog.candidates.size <= MAX_SENTENCE_KEYS)
        assertTrue(snapshot.observationCatalog.streamHighWaterMark <= 32)
        assertTrue(snapshot.incidents.size <= MAX_STRUCTURED_INCIDENTS)
    }

    private fun connection(id: ConnectionId, index: Int): ConnectionRuntimeSnapshot {
        val stored = StoredNmeaConnection(
            config = NmeaConnectionConfig(
                id = id,
                displayName = "Soak $index",
                endpoint = NmeaEndpoint.TcpClient("127.0.0.1", 12_110 + index),
                checksumPolicy = ChecksumPolicy.STRICT,
            ),
            runIntent = ConnectionRunIntent.ENABLED,
            revision = 1L,
        )
        return ConnectionRuntimeSnapshot(
            stored = stored,
            token = SessionToken(id, stored.revision, SessionGeneration(1)),
            transport = ConnectionTransportState.TcpConnected,
            input = ConnectionInputState.NO_BYTES,
            metrics = NmeaConnectionDiagnostics.EMPTY,
        )
    }

    private fun corruptChecksum(raw: String): String = raw.dropLast(2) + if (raw.endsWith("00")) "FF" else "00"

    private class SoakClock(var now: Long = 0L) : MonotonicClock {
        override fun nowMillis(): Long = now
    }

    companion object {
        private const val DURATION_MILLIS = 30 * 60 * 1_000
        private const val TOTAL_RATE_HZ = 100
        private const val CONNECTIONS = 4
        private const val FRAME_INTERVAL_MILLIS = 1_000L / TOTAL_RATE_HZ
        private const val TOTAL_FRAMES = 30 * 60 * TOTAL_RATE_HZ
        private const val SILENT_CONNECTION = 0
        private val SILENT_WINDOW = 300_000L until 312_000L
        private const val SILENT_FRAME_COUNT = 300
        private const val BAD_FRAME_INTERVAL = 97
        private val RMC = NmeaChecksum.append(
            "GPRMC,120000.00,A,3650.0000,S,17445.0000,E,4.2,183.0,010126,,,A",
        )
    }
}

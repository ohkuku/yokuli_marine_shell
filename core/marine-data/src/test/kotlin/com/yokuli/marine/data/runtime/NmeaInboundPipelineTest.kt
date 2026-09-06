package com.yokuli.marine.data.runtime

import com.yokuli.marine.data.catalog.Freshness
import com.yokuli.marine.data.catalog.SentenceParseStatus
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.nmea.NmeaChecksum
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.session.ActiveSessionRegistry
import com.yokuli.marine.data.time.MonotonicClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaInboundPipelineTest {
    private val clock = PipelineClock(0L)
    private val sessions = ActiveSessionRegistry()
    private val connectionId = ConnectionId("gateway")
    private val source = SourceIdentity(connectionId)

    @Test
    fun parsedExplicitInvalidAndUnsupportedAreLegalInputAndUpdateCatalogTruth() {
        sessions.beginSession(connectionId, SessionGeneration(1))
        val pipeline = pipeline()

        pipeline.accept(frame(rmc(), at = 100L, group = 1L))
        pipeline.accept(frame(invalidRmc(), at = 200L, group = 2L))
        pipeline.accept(frame(NmeaChecksum.append("GPXYZ,one,two"), at = 300L, group = 3L))
        clock.now = 300L

        val snapshot = pipeline.snapshot()
        val input = snapshot.connections.getValue(connectionId)
        assertEquals(ConnectionInputState.RECEIVING_VALID_FRAMES, input.input)
        assertEquals(300L, input.metrics.lastLegalFrameAtMillis)
        assertEquals(3L, input.metrics.legalFrameCount)
        assertEquals(1L, input.metrics.parsedFrameCount)
        assertEquals(1L, input.metrics.explicitInvalidFrameCount)
        assertEquals(1L, input.metrics.unsupportedFrameCount)
        assertEquals(0L, input.metrics.checksumFailureCount)
        assertEquals(0L, input.metrics.malformedFrameCount)

        assertEquals(3, snapshot.rawPreview.entries.size)
        assertEquals(2, snapshot.sentenceCatalog.entries.size)
        val rmcEntry = snapshot.sentenceCatalog.entries.single { it.key.formatter == "RMC" }
        assertEquals(2L, rmcEntry.receivedCount)
        assertEquals(SentenceParseStatus.EXPLICIT_INVALID, rmcEntry.lastParseStatus)
        assertEquals(
            SentenceParseStatus.UNSUPPORTED,
            snapshot.sentenceCatalog.entries.single { it.key.formatter == "XYZ" }.lastParseStatus,
        )
        val position = snapshot.observationCatalog.candidates.single { it.id.key == DataKey.Position }
        assertEquals(ObservationValidity.EXPLICIT_INVALID, position.selectedObservation.validity)
        assertEquals(Freshness.INVALID, position.freshness.state)
    }

    @Test
    fun checksumFailureAndMalformedSentenceAreDiagnosticOnlyAndNeverTurnInputGreen() {
        sessions.beginSession(connectionId, SessionGeneration(1))
        val pipeline = pipeline()
        val correct = rmc()
        val wrongChecksum = correct.dropLast(2) + if (correct.endsWith("00")) "FF" else "00"

        pipeline.accept(frame(wrongChecksum, at = 100L, group = 1L))
        pipeline.accept(frame(NmeaChecksum.append("GP,not-an-identifier"), at = 200L, group = 2L))
        clock.now = 200L

        val snapshot = pipeline.snapshot()
        val input = snapshot.connections.getValue(connectionId)
        assertEquals(ConnectionInputState.BYTES_WITHOUT_VALID_FRAME, input.input)
        assertEquals(200L, input.metrics.lastByteAtMillis)
        assertEquals(2L, input.metrics.frameCount)
        assertEquals(0L, input.metrics.legalFrameCount)
        assertEquals(1L, input.metrics.checksumFailureCount)
        assertEquals(1L, input.metrics.malformedFrameCount)
        assertTrue(snapshot.sentenceCatalog.entries.isEmpty())
        assertTrue(snapshot.observationCatalog.candidates.isEmpty())
        assertEquals(2, snapshot.rawPreview.entries.size)
    }

    @Test
    fun validRateUsesOnlyFiveBoundedOneSecondBuckets() {
        sessions.beginSession(connectionId, SessionGeneration(1))
        val pipeline = pipeline()
        listOf(0L, 1_000L, 2_000L, 3_000L, 4_000L).forEachIndexed { index, at ->
            pipeline.accept(frame(NmeaChecksum.append("GPXYZ,$index"), at, group = index.toLong()))
        }

        clock.now = 4_000L
        var rate = pipeline.snapshot().connections.getValue(connectionId).metrics.validRate
        assertEquals(1.0, rate.framesPerSecond, 0.0)
        assertEquals(5, rate.retainedBucketCount)
        assertTrue(rate.retainedBucketCount <= 5)

        clock.now = 5_000L
        rate = pipeline.snapshot().connections.getValue(connectionId).metrics.validRate
        assertEquals(0.8, rate.framesPerSecond, 0.0)
        assertTrue(rate.retainedBucketCount <= 5)

        clock.now = 10_000L
        rate = pipeline.snapshot().connections.getValue(connectionId).metrics.validRate
        assertEquals(0.0, rate.framesPerSecond, 0.0)
        assertEquals(0, rate.retainedBucketCount)
    }

    @Test
    fun inputInterruptsAtTenSecondsWithoutMutatingTransportTruth() {
        sessions.beginSession(connectionId, SessionGeneration(1))
        val pipeline = pipeline()
        pipeline.accept(frame(NmeaChecksum.append("GPXYZ,alive"), at = 100L, group = 1L))

        clock.now = 10_099L
        assertEquals(
            ConnectionInputState.RECEIVING_VALID_FRAMES,
            pipeline.snapshot().connections.getValue(connectionId).input,
        )

        clock.now = 10_100L
        val interrupted = pipeline.snapshot().connections.getValue(connectionId)
        assertEquals(ConnectionInputState.INTERRUPTED, interrupted.input)
        assertEquals(100L, interrupted.metrics.lastLegalFrameAtMillis)
        assertEquals(10_100L, interrupted.metrics.inputInterruptedAtMillis)
    }

    @Test
    fun staleGenerationIsRejectedBeforeItCanTouchCountersRawOrCatalogs() {
        sessions.beginSession(connectionId, SessionGeneration(1))
        val pipeline = pipeline()
        pipeline.accept(frame(rmc(latitude = "3650.9100"), at = 100L, group = 1L, generation = 1L))
        sessions.beginSession(connectionId, SessionGeneration(2))

        pipeline.accept(frame(rmc(latitude = "3750.9100"), at = 200L, group = 2L, generation = 1L))
        pipeline.accept(frame(rmc(latitude = "3850.9100"), at = 300L, group = 3L, generation = 2L))
        clock.now = 300L

        val snapshot = pipeline.snapshot()
        val input = snapshot.connections.getValue(connectionId)
        assertEquals(token(2L), input.token)
        assertEquals(2L, input.metrics.legalFrameCount)
        assertEquals(1L, input.metrics.staleSessionDropCount)
        assertEquals(listOf(100L, 300L), snapshot.rawPreview.entries.map { it.receivedAtMillis })
        assertFalse(snapshot.rawPreview.entries.first().isCurrentSession)
        assertTrue(snapshot.rawPreview.entries.last().isCurrentSession)

        val sentence = snapshot.sentenceCatalog.entries.single { it.key.formatter == "RMC" }
        assertEquals(SessionGeneration(2), sentence.sessionGeneration)
        assertEquals(1L, sentence.receivedCount)
        val position = snapshot.observationCatalog.candidates.single { it.id.key == DataKey.Position }
        assertEquals(SessionGeneration(2), position.sessionGeneration)
        assertEquals(-38.8485, (position.selectedObservation.value as MarineValue.Position).latitudeDegrees, 0.000_001)
    }

    private fun pipeline() = NmeaInboundPipeline(
        clock = clock,
        sessionRegistry = sessions,
        initialConnections = mapOf(connectionId to connectionSnapshot()),
        inputHealthPolicy = InputHealthPolicy(interruptionTimeoutMillis = 10_000L),
    )

    private fun frame(
        raw: String,
        at: Long,
        group: Long,
        generation: Long = 1L,
    ) = NmeaInboundFrame(
        source = source,
        sessionToken = token(generation),
        receivedAtMillis = at,
        groupId = ObservationGroupId(group),
        raw = raw,
    )

    private fun token(generation: Long = 1L) = SessionToken(
        connectionId = connectionId,
        configRevision = 1L,
        generation = SessionGeneration(generation),
    )

    private fun connectionSnapshot() = ConnectionRuntimeSnapshot(
        stored = StoredNmeaConnection(
            config = NmeaConnectionConfig(
                id = connectionId,
                displayName = "Boat gateway",
                endpoint = NmeaEndpoint.TcpClient("127.0.0.1", 10_111),
                checksumPolicy = ChecksumPolicy.STRICT,
            ),
            runIntent = ConnectionRunIntent.ENABLED,
            revision = 1L,
        ),
        token = token(),
        transport = ConnectionTransportState.TcpConnected,
        input = ConnectionInputState.NO_BYTES,
        metrics = NmeaConnectionDiagnostics.EMPTY,
    )

    private fun rmc(latitude: String = "3650.9100"): String = NmeaChecksum.append(
        "GPRMC,120000,A,$latitude,S,17445.8000,E,4.5,84.4,260826,12.0,E,A",
    )

    private fun invalidRmc(): String = NmeaChecksum.append(
        "GPRMC,120001,V,,,,,,,260826,,,N",
    )
}

private class PipelineClock(var now: Long) : MonotonicClock {
    override fun nowMillis(): Long = now
}

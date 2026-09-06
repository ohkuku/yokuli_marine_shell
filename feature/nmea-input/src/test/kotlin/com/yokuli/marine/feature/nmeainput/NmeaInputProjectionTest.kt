package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionRuntimeSnapshot
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.FiveSecondFrameRate
import com.yokuli.marine.data.runtime.NmeaConnectionDiagnostics
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.runtime.SessionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Product-truth Red contract for the NMEA Input feature.
 *
 * These tests deliberately consume the core immutable runtime snapshot. The feature must not own
 * sockets, infer health from raw preview rows, or introduce a parallel mutable runtime model.
 */
class NmeaInputProjectionTest {
    private val now = 20_000L

    @Test
    fun tcpHandshakeWithoutDataIsNotReceiving() {
        val connection = tcpFacts(
            id = "tcp-waiting",
            transport = ConnectionTransportState.TcpConnected,
            input = ConnectionInputState.NO_BYTES,
        )

        val projected = project(connection)
        val row = projected.overview().connections.single()

        assertEquals(1, projected.summary.enabledCount)
        assertEquals(0, projected.summary.receivingCount)
        assertEquals(ConnectionHeadlineUi.TCP_CONNECTED_WAITING_FOR_DATA, row.headline)
        assertEquals(ConnectionTransportUi.TCP_CONNECTED, row.transport)
        assertEquals(ConnectionInputUi.NO_BYTES, row.input)
    }

    @Test
    fun udpBindWithoutDatagramIsListeningNotConnected() {
        val connection = udpFacts(
            id = "udp-waiting",
            transport = ConnectionTransportState.UdpListening,
            input = ConnectionInputState.NO_BYTES,
        )

        val row = project(connection).overview().connections.single()

        assertEquals(ConnectionHeadlineUi.UDP_LISTENING_WAITING_FOR_DATAGRAM, row.headline)
        assertEquals(ConnectionTransportUi.UDP_LISTENING, row.transport)
        assertEquals(ConnectionInputUi.NO_BYTES, row.input)
    }

    @Test
    fun badChecksumInputNeverBecomesReceiving() {
        val connection = tcpFacts(
            id = "bad-only",
            transport = ConnectionTransportState.TcpConnected,
            input = ConnectionInputState.BYTES_WITHOUT_VALID_FRAME,
            diagnostics = diagnostics(
                byteCount = 480,
                frameCount = 8,
                checksumFailureCount = 8,
                legalFrameCount = 0,
            ),
        )

        val projected = project(connection)
        val row = projected.overview().connections.single()

        assertEquals(0, projected.summary.receivingCount)
        assertEquals(ConnectionHeadlineUi.INPUT_WITHOUT_VALID_NMEA, row.headline)
        assertEquals(8L, row.metrics.checksumFailureCount)
        assertEquals(0.0, row.validFramesPerSecond5s, 0.0)
    }

    @Test
    fun windOnlyValidInputIsReceivingAndUnknownLegalFramesAlsoCount() {
        val unknown = tcpFacts(
            id = "unknown-legal",
            transport = ConnectionTransportState.TcpConnected,
            input = ConnectionInputState.RECEIVING_VALID_FRAMES,
            diagnostics = diagnostics(
                frameCount = 3,
                legalFrameCount = 3,
                unsupportedFrameCount = 3,
                parsedFrameCount = 0,
                validFramesPerSecond5s = 0.6,
                lastValidFrameAtMillis = 19_900L,
            ),
        )
        val windOnly = udpFacts(
            id = "wind-only",
            transport = ConnectionTransportState.UdpListening,
            input = ConnectionInputState.RECEIVING_VALID_FRAMES,
            diagnostics = diagnostics(
                frameCount = 5,
                legalFrameCount = 5,
                parsedFrameCount = 5,
                validFramesPerSecond5s = 1.0,
                lastValidFrameAtMillis = 19_950L,
            ),
        )
        val stopped = tcpFacts(
            id = "stopped",
            runIntent = ConnectionRunIntent.STOPPED_BY_USER,
            transport = ConnectionTransportState.Stopped,
            input = ConnectionInputState.NO_BYTES,
        )

        val state = NmeaInputProjector.project(
            snapshot(unknown, windOnly, stopped),
            NmeaInputLocalState.Overview,
            now,
        )

        assertEquals(2, state.summary.enabledCount)
        assertEquals(2, state.summary.receivingCount)
        assertEquals(
            listOf(ConnectionHeadlineUi.RECEIVING_VALID_NMEA, ConnectionHeadlineUi.RECEIVING_VALID_NMEA),
            state.overview().connections.filter { it.enabled }.map { it.headline },
        )
        assertEquals(0L, state.overview().connections.first().metrics.parsedFrameCount)
    }

    @Test
    fun typedRuntimeFailureRemainsTypedAndDoesNotLeakExceptionCopy() {
        val connection = udpFacts(
            id = "conflict",
            transport = ConnectionTransportState.Failed,
            input = ConnectionInputState.NO_BYTES,
            failure = NmeaRuntimeFailure.UdpAddressInUse(port = 10_112),
        )

        val row = project(connection).overview().connections.single()

        assertEquals(ConnectionHeadlineUi.FAILED, row.headline)
        assertEquals(NmeaInputFailureUi.UdpAddressInUse(port = 10_112), row.failure)
        assertNull(row.technicalFailureText)
    }

    @Test
    fun detailAndEditorBackToOverviewWhileOverviewReturnsControlToShell() {
        val id = ConnectionId("back-policy")
        assertEquals(
            NmeaInputUiAction.BackToOverview,
            NmeaInputBackPolicy.actionFor(NmeaInputLocalState.Detail(id)),
        )
        assertEquals(
            NmeaInputUiAction.BackToOverview,
            NmeaInputBackPolicy.actionFor(
                NmeaInputLocalState.Editor(NmeaConnectionDraft.newTcp(id)),
            ),
        )
        assertNull(NmeaInputBackPolicy.actionFor(NmeaInputLocalState.Overview))
    }

    private fun project(connection: ConnectionRuntimeSnapshot): NmeaInputUiState =
        NmeaInputProjector.project(snapshot(connection), NmeaInputLocalState.Overview, now)

    private fun snapshot(vararg connections: ConnectionRuntimeSnapshot) = NmeaRuntimeSnapshot.EMPTY.copy(
        revision = 7L,
        connections = connections.toList(),
    )

    private fun tcpFacts(
        id: String,
        runIntent: ConnectionRunIntent = ConnectionRunIntent.ENABLED,
        transport: ConnectionTransportState,
        input: ConnectionInputState,
        diagnostics: NmeaConnectionDiagnostics = diagnostics(),
        failure: NmeaRuntimeFailure? = null,
    ) = facts(
        config = NmeaConnectionConfig(
            ConnectionId(id),
            id,
            NmeaEndpoint.TcpClient("boat-gateway.local", 10_111),
            ChecksumPolicy.STRICT,
        ),
        runIntent = runIntent,
        transport = transport,
        input = input,
        diagnostics = diagnostics,
        failure = failure,
    )

    private fun udpFacts(
        id: String,
        runIntent: ConnectionRunIntent = ConnectionRunIntent.ENABLED,
        transport: ConnectionTransportState,
        input: ConnectionInputState,
        diagnostics: NmeaConnectionDiagnostics = diagnostics(),
        failure: NmeaRuntimeFailure? = null,
    ) = facts(
        config = NmeaConnectionConfig(
            ConnectionId(id),
            id,
            NmeaEndpoint.UdpListener(localPort = 10_112),
            ChecksumPolicy.STRICT,
        ),
        runIntent = runIntent,
        transport = transport,
        input = input,
        diagnostics = diagnostics,
        failure = failure,
    )

    private fun facts(
        config: NmeaConnectionConfig,
        runIntent: ConnectionRunIntent,
        transport: ConnectionTransportState,
        input: ConnectionInputState,
        diagnostics: NmeaConnectionDiagnostics,
        failure: NmeaRuntimeFailure? = null,
    ) = ConnectionRuntimeSnapshot(
        stored = StoredNmeaConnection(config, runIntent, revision = 1L),
        token = if (transport == ConnectionTransportState.Stopped) null else session(config.id.value, 1L),
        transport = transport,
        input = input,
        metrics = diagnostics,
        failure = failure,
    )

    private fun session(id: String, generation: Long) =
        SessionToken(
            connectionId = ConnectionId(id),
            configRevision = 1L,
            generation = SessionGeneration(generation),
        )

    private fun diagnostics(
        byteCount: Long = 0,
        frameCount: Long = 0,
        legalFrameCount: Long = 0,
        checksumFailureCount: Long = 0,
        unsupportedFrameCount: Long = 0,
        parsedFrameCount: Long = 0,
        validFramesPerSecond5s: Double = 0.0,
        lastValidFrameAtMillis: Long? = null,
    ) = NmeaConnectionDiagnostics(
        byteCount = byteCount,
        datagramCount = 0,
        frameCount = frameCount,
        legalFrameCount = legalFrameCount,
        checksumFailureCount = checksumFailureCount,
        malformedFrameCount = 0,
        unsupportedFrameCount = unsupportedFrameCount,
        explicitInvalidFrameCount = 0,
        staleGenerationDropCount = 0,
        ingressDropCount = 0,
        rawPreviewDropCount = 0,
        parsedFrameCount = parsedFrameCount,
        parsedObservationCount = parsedFrameCount,
        validRate = FiveSecondFrameRate(validFramesPerSecond5s, retainedBucketCount = 1),
        lastByteAtMillis = if (byteCount > 0) 19_900L else null,
        lastValidFrameAtMillis = lastValidFrameAtMillis,
    )

    private fun NmeaInputUiState.overview(): NmeaInputPageUi.Overview =
        page as NmeaInputPageUi.Overview
}

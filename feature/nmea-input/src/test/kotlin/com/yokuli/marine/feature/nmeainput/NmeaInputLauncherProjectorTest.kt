package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionRuntimeSnapshot
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.FiveSecondFrameRate
import com.yokuli.marine.data.runtime.NmeaConnectionDiagnostics
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.runtime.TransportFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaInputLauncherProjectorTest {
    @Test
    fun noConfigurationIsNotAnErrorAndHidesTheStatusEntry() {
        val state = NmeaInputLauncherProjector.project(NmeaRuntimeSnapshot.EMPTY)

        assertEquals(NmeaInputTilePriority.UNCONFIGURED, state.tile.priority)
        assertEquals(0, state.tile.attentionCount)
        assertFalse(state.status.visible)
        assertNull(state.status.preferredConnectionId)
    }

    @Test
    fun configuredButUserStoppedConnectionsAreNotReportedAsWaitingOrBroken() {
        val state = NmeaInputLauncherProjector.project(
            snapshot(
                row("primary", ConnectionTransportState.Stopped, ConnectionInputState.NO_BYTES, enabled = false),
                row("backup", ConnectionTransportState.Stopped, ConnectionInputState.NO_BYTES, enabled = false),
            ),
        )

        assertEquals(NmeaInputTilePriority.STOPPED, state.tile.priority)
        assertEquals(0, state.tile.enabledCount)
        assertEquals(0, state.tile.attentionCount)
        assertFalse(state.status.visible)
    }

    @Test
    fun udpListeningWithoutDatagramsIsWaitingNotConnectedOrReceiving() {
        val state = NmeaInputLauncherProjector.project(
            snapshot(row("udp", ConnectionTransportState.UdpListening, ConnectionInputState.NO_BYTES)),
        )

        assertEquals(NmeaInputTilePriority.WAITING, state.tile.priority)
        assertEquals(1, state.tile.enabledCount)
        assertEquals(0, state.tile.receivingCount)
        assertEquals(NmeaInputConnectionTileState.UDP_LISTENING, state.tile.connectionRows.single().state)
        assertTrue(state.status.visible)
    }

    @Test
    fun anyLegalNmeaCanBeReceivingWithoutClaimingPositionOrSystemSafety() {
        val metrics = NmeaConnectionDiagnostics(
            legalFrameCount = 10L,
            unsupportedFrameCount = 10L,
            validRate = FiveSecondFrameRate(2.0, 5),
        )
        val state = NmeaInputLauncherProjector.project(
            snapshot(row("wind", ConnectionTransportState.TcpConnected, ConnectionInputState.RECEIVING_VALID_FRAMES, metrics)),
        )

        assertEquals(NmeaInputTilePriority.RECEIVING, state.tile.priority)
        assertEquals(1, state.tile.receivingCount)
        assertEquals(2.0, state.tile.connectionRows.single().validFramesPerSecond, 0.0)
    }

    @Test
    fun interruptionOutranksASecondReceivingConnectionAndTargetsTheProblem() {
        val healthy = row("primary", ConnectionTransportState.TcpConnected, ConnectionInputState.RECEIVING_VALID_FRAMES)
        val interrupted = row("backup", ConnectionTransportState.TcpConnected, ConnectionInputState.INTERRUPTED)

        val state = NmeaInputLauncherProjector.project(snapshot(healthy, interrupted))

        assertEquals(NmeaInputTilePriority.ATTENTION, state.tile.priority)
        assertEquals(1, state.tile.receivingCount)
        assertEquals(1, state.tile.attentionCount)
        assertEquals(ConnectionId("backup"), state.status.preferredConnectionId)
    }

    @Test
    fun waitingConnectionOutranksASecondReceivingConnectionWithoutClaimingAllInputsHealthy() {
        val receiving = row("primary", ConnectionTransportState.TcpConnected, ConnectionInputState.RECEIVING_VALID_FRAMES)
        val waiting = row("backup", ConnectionTransportState.TcpConnected, ConnectionInputState.NO_BYTES)

        val state = NmeaInputLauncherProjector.project(snapshot(receiving, waiting))

        assertEquals(NmeaInputTilePriority.WAITING, state.tile.priority)
        assertEquals(1, state.tile.receivingCount)
        assertEquals(0, state.tile.attentionCount)
    }

    @Test
    fun launcherDisplayFreezesDecorativeChangesDuringEditButNeverHidesAttention() {
        val receiving = NmeaInputLauncherProjector.project(
            snapshot(row("one", ConnectionTransportState.TcpConnected, ConnectionInputState.RECEIVING_VALID_FRAMES)),
        ).tile
        val waiting = NmeaInputLauncherProjector.project(
            snapshot(row("one", ConnectionTransportState.TcpConnected, ConnectionInputState.NO_BYTES)),
        ).tile
        val failed = NmeaInputLauncherProjector.project(
            snapshot(
                row(
                    "one",
                    ConnectionTransportState.Failed,
                    ConnectionInputState.INTERRUPTED,
                    failure = NmeaRuntimeFailure.TransportFailed(TransportFailureKind.IO),
                ),
            ),
        ).tile
        val slot = NmeaInputTileDisplaySlot(receiving)

        assertEquals(receiving, slot.resolve(waiting, liveContentEnabled = false))
        assertEquals(failed, slot.resolve(failed, liveContentEnabled = false))
    }

    private fun snapshot(vararg rows: ConnectionRuntimeSnapshot) = NmeaRuntimeSnapshot.EMPTY.copy(
        connections = rows.toList(),
        revision = 1L,
    )

    private fun row(
        id: String,
        transport: ConnectionTransportState,
        input: ConnectionInputState,
        metrics: NmeaConnectionDiagnostics = NmeaConnectionDiagnostics.EMPTY,
        failure: NmeaRuntimeFailure? = null,
        enabled: Boolean = true,
    ): ConnectionRuntimeSnapshot {
        val connectionId = ConnectionId(id)
        return ConnectionRuntimeSnapshot(
            stored = StoredNmeaConnection(
                NmeaConnectionConfig(
                    connectionId,
                    "Gateway $id",
                    if (id == "udp") NmeaEndpoint.UdpListener(10_111)
                    else NmeaEndpoint.TcpClient("127.0.0.1", 10_111),
                    ChecksumPolicy.STRICT,
                ),
                if (enabled) ConnectionRunIntent.ENABLED else ConnectionRunIntent.STOPPED_BY_USER,
                1L,
            ),
            token = null,
            transport = transport,
            input = input,
            metrics = metrics,
            failure = failure,
        )
    }
}

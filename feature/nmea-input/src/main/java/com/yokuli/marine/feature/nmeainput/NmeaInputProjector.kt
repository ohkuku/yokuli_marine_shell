package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionRuntimeSnapshot
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot

/** Pure projection of immutable runtime facts. No UI state is promoted into runtime truth. */
object NmeaInputProjector {
    fun project(
        snapshot: NmeaRuntimeSnapshot,
        localState: NmeaInputLocalState,
        nowMillis: Long,
    ): NmeaInputUiState {
        require(nowMillis >= 0L)
        val rows = snapshot.connections.map { it.toRow(nowMillis) }
        val summary = NmeaInputSummaryUi(
            enabledCount = rows.count(NmeaConnectionRowUi::enabled),
            receivingCount = rows.count { it.enabled && it.isActuallyReceiving() },
        )
        val page = when (localState) {
            NmeaInputLocalState.Overview -> NmeaInputPageUi.Overview(rows)
            is NmeaInputLocalState.Detail -> {
                rows.firstOrNull { it.id == localState.connectionId }?.let { row ->
                    NmeaInputPageUi.Detail(
                        NmeaConnectionDetailUi(
                            row = row,
                            rawPreview = snapshot.rawPreview.entries
                                .asSequence()
                                .filter { it.source.connectionId == localState.connectionId }
                                .map { entry ->
                                    NmeaRawPreviewUi(
                                        receivedAtMillis = entry.receivedAtMillis,
                                        raw = entry.raw,
                                        sender = entry.sender?.let { "${it.hostAddress}:${it.port}" },
                                        isCurrentSession = entry.isCurrentSession,
                                    )
                                }
                                .toList(),
                            rawPreviewDropCount = row.metrics.rawPreviewDropCount,
                        ),
                    )
                } ?: NmeaInputPageUi.Overview(rows)
            }

            is NmeaInputLocalState.Editor -> NmeaInputPageUi.Editor(localState.draft)
            is NmeaInputLocalState.ReplacementConfirmation -> {
                NmeaInputPageUi.ReplacementConfirmation(
                    draft = localState.draft,
                    startAfterSave = localState.startAfterSave,
                )
            }

            is NmeaInputLocalState.DuplicateTcpConfirmation -> {
                NmeaInputPageUi.DuplicateTcpConfirmation(
                    draft = localState.draft,
                    startAfterSave = localState.startAfterSave,
                    existingConnectionId = localState.existingConnectionId,
                )
            }

            is NmeaInputLocalState.DeleteConfirmation -> {
                rows.firstOrNull { it.id == localState.connectionId }?.let {
                    NmeaInputPageUi.DeleteConfirmation(it)
                } ?: NmeaInputPageUi.Overview(rows)
            }
        }
        return NmeaInputUiState(summary = summary, page = page)
    }
}

private fun ConnectionRuntimeSnapshot.toRow(nowMillis: Long): NmeaConnectionRowUi {
    val isEnabled = stored.runIntent == ConnectionRunIntent.ENABLED
    return NmeaConnectionRowUi(
        id = stored.config.id,
        name = stored.config.displayName,
        endpoint = stored.config.endpoint.toUi(),
        enabled = isEnabled,
        transport = transport.toUi(),
        input = input.toUi(),
        headline = headline(isEnabled),
        metrics = metrics,
        validFramesPerSecond5s = metrics.validRate.framesPerSecond,
        lastValidFrameAgeMillis = metrics.lastLegalFrameAtMillis?.let { last ->
            if (nowMillis >= last) nowMillis - last else 0L
        },
        failure = failure?.toUiFailure(),
        technicalFailureText = null,
    )
}

private fun ConnectionRuntimeSnapshot.headline(enabled: Boolean): ConnectionHeadlineUi = when {
    !enabled || transport is ConnectionTransportState.Stopped -> ConnectionHeadlineUi.STOPPED
    failure != null || transport is ConnectionTransportState.Failed -> ConnectionHeadlineUi.FAILED
    transport is ConnectionTransportState.PlatformStartRequired -> {
        ConnectionHeadlineUi.PLATFORM_START_REQUIRED
    }
    transport is ConnectionTransportState.WaitingForNetwork -> ConnectionHeadlineUi.WAITING_FOR_NETWORK
    transport is ConnectionTransportState.ReconnectWaiting -> ConnectionHeadlineUi.RECONNECT_WAITING
    transport is ConnectionTransportState.Starting -> ConnectionHeadlineUi.STARTING
    input == ConnectionInputState.OVERLOADED -> ConnectionHeadlineUi.INPUT_OVERLOADED
    input == ConnectionInputState.INTERRUPTED -> ConnectionHeadlineUi.INPUT_INTERRUPTED
    input == ConnectionInputState.BYTES_WITHOUT_VALID_FRAME -> {
        ConnectionHeadlineUi.INPUT_WITHOUT_VALID_NMEA
    }
    input == ConnectionInputState.RECEIVING_VALID_FRAMES && transport.isActiveTransport() -> {
        ConnectionHeadlineUi.RECEIVING_VALID_NMEA
    }
    input == ConnectionInputState.NO_BYTES && transport is ConnectionTransportState.TcpConnected -> {
        ConnectionHeadlineUi.TCP_CONNECTED_WAITING_FOR_DATA
    }
    input == ConnectionInputState.NO_BYTES && transport is ConnectionTransportState.UdpListening -> {
        ConnectionHeadlineUi.UDP_LISTENING_WAITING_FOR_DATAGRAM
    }
    else -> ConnectionHeadlineUi.STARTING
}

private fun NmeaConnectionRowUi.isActuallyReceiving(): Boolean =
    input == ConnectionInputUi.RECEIVING_VALID_FRAMES &&
        (transport == ConnectionTransportUi.TCP_CONNECTED || transport == ConnectionTransportUi.UDP_LISTENING)

private fun ConnectionTransportState.isActiveTransport(): Boolean =
    this is ConnectionTransportState.TcpConnected || this is ConnectionTransportState.UdpListening

private fun NmeaEndpoint.toUi(): NmeaEndpointUi = when (this) {
    is NmeaEndpoint.TcpClient -> NmeaEndpointUi.TcpClient(host, port)
    is NmeaEndpoint.UdpListener -> NmeaEndpointUi.UdpListener(localPort, senderHostFilter)
}

private fun ConnectionTransportState.toUi(): ConnectionTransportUi = when (this) {
    is ConnectionTransportState.Stopped -> ConnectionTransportUi.STOPPED
    is ConnectionTransportState.Starting -> ConnectionTransportUi.STARTING
    is ConnectionTransportState.TcpConnected -> ConnectionTransportUi.TCP_CONNECTED
    is ConnectionTransportState.UdpListening -> ConnectionTransportUi.UDP_LISTENING
    is ConnectionTransportState.WaitingForNetwork -> ConnectionTransportUi.WAITING_FOR_NETWORK
    is ConnectionTransportState.ReconnectWaiting -> ConnectionTransportUi.RECONNECT_WAITING
    is ConnectionTransportState.PlatformStartRequired -> ConnectionTransportUi.PLATFORM_START_REQUIRED
    is ConnectionTransportState.Failed -> ConnectionTransportUi.FAILED
}

private fun ConnectionInputState.toUi(): ConnectionInputUi = when (this) {
    ConnectionInputState.NO_BYTES -> ConnectionInputUi.NO_BYTES
    ConnectionInputState.BYTES_WITHOUT_VALID_FRAME -> ConnectionInputUi.BYTES_WITHOUT_VALID_FRAME
    ConnectionInputState.RECEIVING_VALID_FRAMES -> ConnectionInputUi.RECEIVING_VALID_FRAMES
    ConnectionInputState.INTERRUPTED -> ConnectionInputUi.INTERRUPTED
    ConnectionInputState.OVERLOADED -> ConnectionInputUi.OVERLOADED
}

internal fun NmeaRuntimeFailure.toUiFailure(): NmeaInputFailureUi = when (this) {
    is NmeaRuntimeFailure.UdpAddressInUse -> NmeaInputFailureUi.UdpAddressInUse(port)
    NmeaRuntimeFailure.PersistenceFailed -> NmeaInputFailureUi.PersistenceFailed
    is NmeaRuntimeFailure.InvalidConfiguration -> NmeaInputFailureUi.InvalidConfiguration
    is NmeaRuntimeFailure.DuplicateTcpEndpoint -> NmeaInputFailureUi.DuplicateTcpEndpoint
    NmeaRuntimeFailure.ActiveConnectionCapacityExceeded -> NmeaInputFailureUi.ConnectionLimitReached
    NmeaRuntimeFailure.ConnectionNotFound -> NmeaInputFailureUi.ConnectionNotFound
    NmeaRuntimeFailure.StaleSession -> NmeaInputFailureUi.StaleSession
    NmeaRuntimeFailure.PlatformRestricted -> NmeaInputFailureUi.PlatformRestricted
    NmeaRuntimeFailure.IngressOverloaded -> NmeaInputFailureUi.IngressOverloaded
    is NmeaRuntimeFailure.TransportFailed -> NmeaInputFailureUi.TransportFailed
    NmeaRuntimeFailure.InternalError -> NmeaInputFailureUi.InternalError
}

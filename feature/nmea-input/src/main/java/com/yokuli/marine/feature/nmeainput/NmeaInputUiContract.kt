package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.runtime.NmeaConnectionDiagnostics

data class NmeaInputUiState(
    val summary: NmeaInputSummaryUi = NmeaInputSummaryUi(),
    val page: NmeaInputPageUi = NmeaInputPageUi.Overview(emptyList()),
    val notice: NmeaInputNoticeUi? = null,
)

data class NmeaInputSummaryUi(
    val enabledCount: Int = 0,
    val receivingCount: Int = 0,
) {
    init {
        require(enabledCount >= 0)
        require(receivingCount in 0..enabledCount)
    }
}

sealed interface NmeaInputPageUi {
    data class Overview(val connections: List<NmeaConnectionRowUi>) : NmeaInputPageUi
    data class Detail(val connection: NmeaConnectionDetailUi) : NmeaInputPageUi
    data class Editor(val draft: NmeaConnectionDraft) : NmeaInputPageUi
    data class ReplacementConfirmation(
        val draft: NmeaConnectionDraft,
        val startAfterSave: Boolean,
    ) : NmeaInputPageUi

    data class DuplicateTcpConfirmation(
        val draft: NmeaConnectionDraft,
        val startAfterSave: Boolean,
        val existingConnectionId: ConnectionId,
    ) : NmeaInputPageUi

    data class DeleteConfirmation(val connection: NmeaConnectionRowUi) : NmeaInputPageUi
}

sealed interface NmeaInputLocalState {
    data object Overview : NmeaInputLocalState
    data class Detail(val connectionId: ConnectionId) : NmeaInputLocalState
    data class Editor(val draft: NmeaConnectionDraft) : NmeaInputLocalState
    data class ReplacementConfirmation(
        val draft: NmeaConnectionDraft,
        val startAfterSave: Boolean,
    ) : NmeaInputLocalState

    data class DuplicateTcpConfirmation(
        val draft: NmeaConnectionDraft,
        val startAfterSave: Boolean,
        val existingConnectionId: ConnectionId,
    ) : NmeaInputLocalState

    data class DeleteConfirmation(val connectionId: ConnectionId) : NmeaInputLocalState
}

enum class NmeaInputTransportKind { TCP_CLIENT, UDP_LISTENER }

enum class NmeaInputField { NAME, TCP_HOST, PORT, UDP_SENDER_ADDRESS }

enum class NmeaInputValidationError { REQUIRED, ASCII_DIGITS_ONLY, PORT_OUT_OF_RANGE }

data class NmeaConnectionDraft(
    val id: ConnectionId,
    val name: String,
    val transport: NmeaInputTransportKind,
    val tcpHost: String,
    val portText: String,
    val restrictUdpSender: Boolean,
    val udpSenderAddress: String,
    val checksumPolicy: ChecksumPolicy,
    val expectedRevision: Long? = null,
    val originallyEnabled: Boolean = false,
    val originalEndpoint: NmeaEndpoint? = null,
    val errors: Map<NmeaInputField, NmeaInputValidationError> = emptyMap(),
    val submitting: Boolean = false,
) {
    init {
        require(expectedRevision == null || expectedRevision >= 0L)
    }

    val endpointChanged: Boolean
        get() = originalEndpoint != null && endpointOrNull() != originalEndpoint

    fun endpointOrNull(): NmeaEndpoint? {
        val port = portText.toAsciiPortOrNull() ?: return null
        return when (transport) {
            NmeaInputTransportKind.TCP_CLIENT -> tcpHost.trim().takeIf(String::isNotEmpty)?.let {
                NmeaEndpoint.TcpClient(it, port)
            }
            NmeaInputTransportKind.UDP_LISTENER -> NmeaEndpoint.UdpListener(
                localPort = port,
                senderHostFilter = udpSenderAddress.trim().takeIf { restrictUdpSender && it.isNotEmpty() },
            )
        }
    }

    fun configOrNull(): NmeaConnectionConfig? {
        if (
            errors.isNotEmpty() ||
            name.isBlank() ||
            (transport == NmeaInputTransportKind.UDP_LISTENER &&
                restrictUdpSender && udpSenderAddress.isBlank())
        ) return null
        val endpoint = endpointOrNull() ?: return null
        return NmeaConnectionConfig(id, name.trim(), endpoint, checksumPolicy)
    }

    companion object {
        fun newTcp(id: ConnectionId) = NmeaConnectionDraft(
            id = id,
            name = "",
            transport = NmeaInputTransportKind.TCP_CLIENT,
            tcpHost = "",
            portText = "10111",
            restrictUdpSender = false,
            udpSenderAddress = "",
            checksumPolicy = ChecksumPolicy.STRICT,
        )

        fun from(stored: StoredNmeaConnection): NmeaConnectionDraft {
            val endpoint = stored.config.endpoint
            return NmeaConnectionDraft(
                id = stored.config.id,
                name = stored.config.displayName,
                transport = if (endpoint is NmeaEndpoint.TcpClient) {
                    NmeaInputTransportKind.TCP_CLIENT
                } else {
                    NmeaInputTransportKind.UDP_LISTENER
                },
                tcpHost = (endpoint as? NmeaEndpoint.TcpClient)?.host.orEmpty(),
                portText = when (endpoint) {
                    is NmeaEndpoint.TcpClient -> endpoint.port
                    is NmeaEndpoint.UdpListener -> endpoint.localPort
                }.toString(),
                restrictUdpSender = (endpoint as? NmeaEndpoint.UdpListener)?.senderHostFilter != null,
                udpSenderAddress = (endpoint as? NmeaEndpoint.UdpListener)?.senderHostFilter.orEmpty(),
                checksumPolicy = stored.config.checksumPolicy,
                expectedRevision = stored.revision,
                originallyEnabled = stored.runIntent == ConnectionRunIntent.ENABLED,
                originalEndpoint = endpoint,
            )
        }
    }
}

sealed interface NmeaEndpointUi {
    data class TcpClient(val host: String, val port: Int) : NmeaEndpointUi
    data class UdpListener(val localPort: Int, val senderAddress: String? = null) : NmeaEndpointUi
}

enum class ConnectionTransportUi {
    STOPPED,
    STARTING,
    TCP_CONNECTED,
    UDP_LISTENING,
    WAITING_FOR_NETWORK,
    RECONNECT_WAITING,
    PLATFORM_START_REQUIRED,
    FAILED,
}

enum class ConnectionInputUi {
    NO_BYTES,
    BYTES_WITHOUT_VALID_FRAME,
    RECEIVING_VALID_FRAMES,
    INTERRUPTED,
    OVERLOADED,
}

enum class ConnectionHeadlineUi {
    STOPPED,
    STARTING,
    TCP_CONNECTED_WAITING_FOR_DATA,
    UDP_LISTENING_WAITING_FOR_DATAGRAM,
    INPUT_WITHOUT_VALID_NMEA,
    RECEIVING_VALID_NMEA,
    INPUT_INTERRUPTED,
    INPUT_OVERLOADED,
    WAITING_FOR_NETWORK,
    RECONNECT_WAITING,
    PLATFORM_START_REQUIRED,
    FAILED,
}

sealed interface NmeaInputFailureUi {
    data class UdpAddressInUse(val port: Int) : NmeaInputFailureUi
    data object PersistenceFailed : NmeaInputFailureUi
    data object InvalidConfiguration : NmeaInputFailureUi
    data object DuplicateTcpEndpoint : NmeaInputFailureUi
    data object ConnectionLimitReached : NmeaInputFailureUi
    data object ConnectionNotFound : NmeaInputFailureUi
    data object StaleSession : NmeaInputFailureUi
    data object PlatformRestricted : NmeaInputFailureUi
    data object IngressOverloaded : NmeaInputFailureUi
    data object TransportFailed : NmeaInputFailureUi
    data object InternalError : NmeaInputFailureUi
}

data class NmeaConnectionRowUi(
    val id: ConnectionId,
    val name: String,
    val endpoint: NmeaEndpointUi,
    val enabled: Boolean,
    val transport: ConnectionTransportUi,
    val input: ConnectionInputUi,
    val headline: ConnectionHeadlineUi,
    val metrics: NmeaConnectionDiagnostics,
    val validFramesPerSecond5s: Double,
    val lastValidFrameAgeMillis: Long?,
    val failure: NmeaInputFailureUi?,
    /** Runtime exception messages are intentionally never part of user-facing state. */
    val technicalFailureText: String? = null,
)

data class NmeaRawPreviewUi(
    val receivedAtMillis: Long,
    val raw: String,
    val sender: String?,
    val isCurrentSession: Boolean,
)

data class NmeaConnectionDetailUi(
    val row: NmeaConnectionRowUi,
    val rawPreview: List<NmeaRawPreviewUi>,
    val rawPreviewDropCount: Long,
)

sealed interface NmeaInputNoticeUi {
    data class OperationFailed(val failure: NmeaInputFailureUi) : NmeaInputNoticeUi
    data object Saved : NmeaInputNoticeUi
    data object Started : NmeaInputNoticeUi
    data object Stopped : NmeaInputNoticeUi
    data object Deleted : NmeaInputNoticeUi
    data object ActionQueueFull : NmeaInputNoticeUi
}

sealed interface NmeaInputUiAction {
    data object AddConnection : NmeaInputUiAction
    data class OpenConnection(val id: ConnectionId) : NmeaInputUiAction
    data class EditConnection(val id: ConnectionId) : NmeaInputUiAction
    data object BackToOverview : NmeaInputUiAction
    data class ChangeName(val value: String) : NmeaInputUiAction
    data class ChangeTransport(val value: NmeaInputTransportKind) : NmeaInputUiAction
    data class ChangeTcpHost(val value: String) : NmeaInputUiAction
    data class ChangePort(val value: String) : NmeaInputUiAction
    data class ChangeUdpSenderRestriction(val enabled: Boolean) : NmeaInputUiAction
    data class ChangeUdpSenderAddress(val value: String) : NmeaInputUiAction
    data class ChangeChecksumPolicy(val value: ChecksumPolicy) : NmeaInputUiAction
    data object Save : NmeaInputUiAction
    data object SaveAndEnable : NmeaInputUiAction
    data object ConfirmRunningReplacement : NmeaInputUiAction
    data object ConfirmDuplicateTcp : NmeaInputUiAction
    data class Start(val id: ConnectionId) : NmeaInputUiAction
    data class Stop(val id: ConnectionId) : NmeaInputUiAction
    data class Retry(val id: ConnectionId) : NmeaInputUiAction
    data class RequestDelete(val id: ConnectionId) : NmeaInputUiAction
    data object ConfirmDelete : NmeaInputUiAction
    data object DismissNotice : NmeaInputUiAction
}

object NmeaInputBackPolicy {
    fun actionFor(localState: NmeaInputLocalState): NmeaInputUiAction? = when (localState) {
        NmeaInputLocalState.Overview -> null
        is NmeaInputLocalState.Detail,
        is NmeaInputLocalState.Editor,
        is NmeaInputLocalState.ReplacementConfirmation,
        is NmeaInputLocalState.DuplicateTcpConfirmation,
        is NmeaInputLocalState.DeleteConfirmation,
        -> NmeaInputUiAction.BackToOverview
    }

    fun actionFor(page: NmeaInputPageUi): NmeaInputUiAction? = when (page) {
        is NmeaInputPageUi.Overview -> null
        else -> NmeaInputUiAction.BackToOverview
    }
}

internal fun String.toAsciiPortOrNull(): Int? =
    takeIf { it.isNotEmpty() && it.all { character -> character in '0'..'9' } }
        ?.toIntOrNull()
        ?.takeIf { it in 1..65_535 }

package com.yokuli.marine.data.android.transport

import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.SessionToken

/** One transport session. The runtime supplies and validates the immutable session token. */
fun interface NmeaTransportFactory {
    suspend fun run(
        config: NmeaConnectionConfig,
        sessionToken: SessionToken,
        emit: suspend (NetworkTransportEvent) -> Unit,
    )
}

/** Transport facts only. Framing, parsing and input-health projection remain in the core runtime. */
sealed interface NetworkTransportEvent {
    data object TcpConnected : NetworkTransportEvent

    data class UdpListening(val localPort: Int) : NetworkTransportEvent

    data class TcpBytes(val bytes: ByteArray) : NetworkTransportEvent {
        override fun equals(other: Any?): Boolean = other is TcpBytes && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data class UdpDatagram(
        val bytes: ByteArray,
        val sender: SenderIdentity,
    ) : NetworkTransportEvent {
        override fun equals(other: Any?): Boolean =
            other is UdpDatagram && sender == other.sender && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + sender.hashCode()
    }

    data class Failed(
        val failure: NmeaRuntimeFailure,
        val recoverable: Boolean,
    ) : NetworkTransportEvent
}

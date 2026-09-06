package com.yokuli.marine.data.connection

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.UdpOriginIdentityPolicy
import com.yokuli.marine.data.nmea.ChecksumPolicy

const val MAX_NMEA_CONNECTIONS: Int = 32
const val MAX_CONNECTION_DISPLAY_NAME_LENGTH: Int = 128
const val MAX_CONNECTION_HOST_LENGTH: Int = 253

/** User-owned transport configuration. Validation is deliberately performed by the reducer. */
data class NmeaConnectionConfig(
    val id: ConnectionId,
    val displayName: String,
    val endpoint: NmeaEndpoint,
    val checksumPolicy: ChecksumPolicy = ChecksumPolicy.STRICT,
)

sealed interface NmeaEndpoint {
    /** A remote TCP endpoint. A successful socket handshake is transport truth, not input truth. */
    data class TcpClient(
        val host: String,
        val port: Int,
    ) : NmeaEndpoint

    /** A local UDP bind. It never claims that a remote sender is connected. */
    data class UdpListener(
        val localPort: Int,
        val senderHostFilter: String? = null,
        val originIdentityPolicy: UdpOriginIdentityPolicy = UdpOriginIdentityPolicy.HOST_ADDRESS,
    ) : NmeaEndpoint
}

enum class ConnectionRunIntent {
    STOPPED_BY_USER,
    ENABLED,
}

/** Durable connection fact. Runtime transport and input state are intentionally stored elsewhere. */
data class StoredNmeaConnection(
    val config: NmeaConnectionConfig,
    val runIntent: ConnectionRunIntent,
    val revision: Long = 0L,
) {
    init {
        require(revision >= 0L) { "Connection revision must be non-negative" }
    }
}

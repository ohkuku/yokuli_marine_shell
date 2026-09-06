package com.yokuli.marine.data.android.persistence

import com.yokuli.marine.data.android.proto.PersistedChecksumPolicy
import com.yokuli.marine.data.android.proto.PersistedEndpoint
import com.yokuli.marine.data.android.proto.PersistedNmeaConnection
import com.yokuli.marine.data.android.proto.PersistedNmeaConnectionStore
import com.yokuli.marine.data.android.proto.PersistedRunIntent
import com.yokuli.marine.data.android.proto.PersistedTcpClient
import com.yokuli.marine.data.android.proto.PersistedUdpListener
import com.yokuli.marine.data.android.proto.PersistedUdpOriginIdentityPolicy
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.ConnectionConfigAction
import com.yokuli.marine.data.connection.ConnectionConfigEffect
import com.yokuli.marine.data.connection.ConnectionConfigReducer
import com.yokuli.marine.data.connection.ConnectionConfigState
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.UdpOriginIdentityPolicy
import com.yokuli.marine.data.nmea.ChecksumPolicy

internal data class DecodedConnectionStore(
    val connections: List<StoredNmeaConnection>,
    val quarantinedRecordCount: Int,
)

internal object NmeaConnectionProtoMapper {
    fun decode(store: PersistedNmeaConnectionStore): DecodedConnectionStore {
        var quarantined = 0
        val decoded = store.connectionsList.mapNotNull { persisted ->
            runCatching { persisted.toDomain() }.getOrElse {
                quarantined += 1
                null
            }
        }
        return DecodedConnectionStore(
            connections = decoded.distinctBy { it.config.id }.sortedBy { it.config.id.value },
            quarantinedRecordCount = quarantined + (decoded.size - decoded.distinctBy { it.config.id }.size),
        )
    }

    fun encode(connection: StoredNmeaConnection): PersistedNmeaConnection {
        val endpoint = when (val value = connection.config.endpoint) {
            is NmeaEndpoint.TcpClient -> PersistedEndpoint.newBuilder()
                .setTcpClient(
                    PersistedTcpClient.newBuilder()
                        .setHost(value.host)
                        .setPort(value.port),
                )
                .build()
            is NmeaEndpoint.UdpListener -> PersistedEndpoint.newBuilder()
                .setUdpListener(
                    PersistedUdpListener.newBuilder()
                        .setLocalPort(value.localPort)
                        .setOriginIdentityPolicy(value.originIdentityPolicy.toProto())
                        .apply {
                            value.senderHostFilter?.let(::setSenderHostFilter)
                        },
                )
                .build()
        }
        return PersistedNmeaConnection.newBuilder()
            .setConnectionId(connection.config.id.value)
            .setDisplayName(connection.config.displayName)
            .setEndpoint(endpoint)
            .setChecksumPolicy(connection.config.checksumPolicy.toProto())
            .setRunIntent(connection.runIntent.toProto())
            .setRevision(connection.revision)
            .build()
    }

    private fun PersistedNmeaConnection.toDomain(): StoredNmeaConnection {
        require(revision >= 0L)
        val domainEndpoint = when (endpoint.kindCase) {
            PersistedEndpoint.KindCase.TCP_CLIENT -> NmeaEndpoint.TcpClient(
                host = endpoint.tcpClient.host,
                port = endpoint.tcpClient.port,
            )
            PersistedEndpoint.KindCase.UDP_LISTENER -> NmeaEndpoint.UdpListener(
                localPort = endpoint.udpListener.localPort,
                senderHostFilter = endpoint.udpListener.senderHostFilter.takeIf {
                    endpoint.udpListener.hasSenderHostFilter()
                },
                originIdentityPolicy = endpoint.udpListener.originIdentityPolicy.toDomain(),
            )
            PersistedEndpoint.KindCase.KIND_NOT_SET,
            null,
            -> error("Connection endpoint is missing")
        }
        val config = NmeaConnectionConfig(
                id = ConnectionId(connectionId),
                displayName = displayName,
                endpoint = domainEndpoint,
                checksumPolicy = checksumPolicy.toDomain(),
            )
        val validation = ConnectionConfigReducer().reduce(
            ConnectionConfigState(),
            ConnectionConfigAction.Save(config),
        )
        require(validation.effects.singleOrNull() is ConnectionConfigEffect.Persist) {
            "Persisted NMEA connection configuration is invalid"
        }
        return StoredNmeaConnection(
            config = config,
            runIntent = runIntent.toDomain(),
            revision = revision,
        )
    }

    private fun ChecksumPolicy.toProto(): PersistedChecksumPolicy = when (this) {
        ChecksumPolicy.STRICT -> PersistedChecksumPolicy.PERSISTED_CHECKSUM_POLICY_STRICT
        ChecksumPolicy.ALLOW_MISSING -> PersistedChecksumPolicy.PERSISTED_CHECKSUM_POLICY_ALLOW_MISSING
    }

    private fun PersistedChecksumPolicy.toDomain(): ChecksumPolicy = when (this) {
        PersistedChecksumPolicy.PERSISTED_CHECKSUM_POLICY_STRICT -> ChecksumPolicy.STRICT
        PersistedChecksumPolicy.PERSISTED_CHECKSUM_POLICY_ALLOW_MISSING -> ChecksumPolicy.ALLOW_MISSING
        PersistedChecksumPolicy.PERSISTED_CHECKSUM_POLICY_UNSPECIFIED,
        PersistedChecksumPolicy.UNRECOGNIZED,
        -> error("Unknown checksum policy")
    }

    private fun ConnectionRunIntent.toProto(): PersistedRunIntent = when (this) {
        ConnectionRunIntent.STOPPED_BY_USER -> PersistedRunIntent.PERSISTED_RUN_INTENT_STOPPED_BY_USER
        ConnectionRunIntent.ENABLED -> PersistedRunIntent.PERSISTED_RUN_INTENT_ENABLED
    }

    private fun PersistedRunIntent.toDomain(): ConnectionRunIntent = when (this) {
        PersistedRunIntent.PERSISTED_RUN_INTENT_STOPPED_BY_USER -> ConnectionRunIntent.STOPPED_BY_USER
        PersistedRunIntent.PERSISTED_RUN_INTENT_ENABLED -> ConnectionRunIntent.ENABLED
        PersistedRunIntent.PERSISTED_RUN_INTENT_UNSPECIFIED,
        PersistedRunIntent.UNRECOGNIZED,
        -> error("Unknown connection run intent")
    }

    private fun UdpOriginIdentityPolicy.toProto(): PersistedUdpOriginIdentityPolicy = when (this) {
        UdpOriginIdentityPolicy.HOST_ADDRESS ->
            PersistedUdpOriginIdentityPolicy.PERSISTED_UDP_ORIGIN_IDENTITY_POLICY_HOST_ADDRESS
        UdpOriginIdentityPolicy.HOST_AND_PORT ->
            PersistedUdpOriginIdentityPolicy.PERSISTED_UDP_ORIGIN_IDENTITY_POLICY_HOST_AND_PORT
    }

    private fun PersistedUdpOriginIdentityPolicy.toDomain(): UdpOriginIdentityPolicy = when (this) {
        PersistedUdpOriginIdentityPolicy.PERSISTED_UDP_ORIGIN_IDENTITY_POLICY_HOST_ADDRESS ->
            UdpOriginIdentityPolicy.HOST_ADDRESS
        PersistedUdpOriginIdentityPolicy.PERSISTED_UDP_ORIGIN_IDENTITY_POLICY_HOST_AND_PORT ->
            UdpOriginIdentityPolicy.HOST_AND_PORT
        PersistedUdpOriginIdentityPolicy.PERSISTED_UDP_ORIGIN_IDENTITY_POLICY_UNSPECIFIED,
        PersistedUdpOriginIdentityPolicy.UNRECOGNIZED,
        -> error("Unknown UDP origin identity policy")
    }
}

package com.yokuli.marine.data.connection

import com.yokuli.marine.data.model.ConnectionId

data class ConnectionConfigState(
    val connections: Map<ConnectionId, NmeaConnectionConfig> = emptyMap(),
) {
    init {
        require(connections.size <= MAX_NMEA_CONNECTIONS) { "Connection state exceeds its bound" }
        require(connections.all { (id, config) -> id == config.id }) {
            "Connection map keys must match their configs"
        }
    }
}

sealed interface ConnectionConfigAction {
    data class Save(
        val candidate: NmeaConnectionConfig,
        val duplicateTcpConfirmed: Boolean = false,
    ) : ConnectionConfigAction
}

enum class ConnectionConfigIssue {
    BLANK_DISPLAY_NAME,
    DISPLAY_NAME_TOO_LONG,
    BLANK_TCP_HOST,
    TCP_HOST_TOO_LONG,
    BLANK_UDP_SENDER_HOST_FILTER,
    UDP_SENDER_HOST_FILTER_TOO_LONG,
    PORT_OUT_OF_RANGE,
    CONNECTION_LIMIT_REACHED,
}

sealed interface ConnectionConfigEffect {
    data class Invalid(val issues: Set<ConnectionConfigIssue>) : ConnectionConfigEffect

    data class ConfirmDuplicateTcp(
        val candidate: NmeaConnectionConfig,
        val existingConnectionIds: Set<ConnectionId>,
    ) : ConnectionConfigEffect

    data class Persist(val config: NmeaConnectionConfig) : ConnectionConfigEffect
}

data class ConnectionConfigResult(
    val state: ConnectionConfigState,
    val effects: List<ConnectionConfigEffect>,
)

/** Pure validation and duplicate-warning policy used before durable persistence. */
class ConnectionConfigReducer(
    private val maxConnections: Int = MAX_NMEA_CONNECTIONS,
) {
    init {
        require(maxConnections > 0) { "Connection capacity must be positive" }
        require(maxConnections <= MAX_NMEA_CONNECTIONS) {
            "A reducer cannot exceed the runtime connection bound"
        }
    }

    fun reduce(
        state: ConnectionConfigState,
        action: ConnectionConfigAction,
    ): ConnectionConfigResult = when (action) {
        is ConnectionConfigAction.Save -> save(state, action)
    }

    private fun save(
        state: ConnectionConfigState,
        action: ConnectionConfigAction.Save,
    ): ConnectionConfigResult {
        val issues = validate(state, action.candidate)
        if (issues.isNotEmpty()) {
            return ConnectionConfigResult(
                state = state,
                effects = listOf(ConnectionConfigEffect.Invalid(issues)),
            )
        }

        val duplicates = duplicateTcpIds(state, action.candidate)
        if (duplicates.isNotEmpty() && !action.duplicateTcpConfirmed) {
            return ConnectionConfigResult(
                state = state,
                effects = listOf(
                    ConnectionConfigEffect.ConfirmDuplicateTcp(
                        candidate = action.candidate,
                        existingConnectionIds = duplicates,
                    ),
                ),
            )
        }

        return ConnectionConfigResult(
            state = state.copy(
                connections = state.connections + (action.candidate.id to action.candidate),
            ),
            effects = listOf(ConnectionConfigEffect.Persist(action.candidate)),
        )
    }

    private fun validate(
        state: ConnectionConfigState,
        candidate: NmeaConnectionConfig,
    ): Set<ConnectionConfigIssue> = buildSet {
        if (candidate.displayName.isBlank()) add(ConnectionConfigIssue.BLANK_DISPLAY_NAME)
        if (candidate.displayName.length > MAX_CONNECTION_DISPLAY_NAME_LENGTH) {
            add(ConnectionConfigIssue.DISPLAY_NAME_TOO_LONG)
        }
        when (val endpoint = candidate.endpoint) {
            is NmeaEndpoint.TcpClient -> {
                if (endpoint.host.trim().trimEnd('.').isEmpty()) add(ConnectionConfigIssue.BLANK_TCP_HOST)
                if (endpoint.host.length > MAX_CONNECTION_HOST_LENGTH) {
                    add(ConnectionConfigIssue.TCP_HOST_TOO_LONG)
                }
                if (endpoint.port !in VALID_PORTS) add(ConnectionConfigIssue.PORT_OUT_OF_RANGE)
            }
            is NmeaEndpoint.UdpListener -> {
                if (endpoint.localPort !in VALID_PORTS) add(ConnectionConfigIssue.PORT_OUT_OF_RANGE)
                if (endpoint.senderHostFilter != null && endpoint.senderHostFilter.isBlank()) {
                    add(ConnectionConfigIssue.BLANK_UDP_SENDER_HOST_FILTER)
                }
                if ((endpoint.senderHostFilter?.length ?: 0) > MAX_CONNECTION_HOST_LENGTH) {
                    add(ConnectionConfigIssue.UDP_SENDER_HOST_FILTER_TOO_LONG)
                }
            }
        }
        if (candidate.id !in state.connections && state.connections.size >= maxConnections) {
            add(ConnectionConfigIssue.CONNECTION_LIMIT_REACHED)
        }
    }

    private fun duplicateTcpIds(
        state: ConnectionConfigState,
        candidate: NmeaConnectionConfig,
    ): Set<ConnectionId> {
        val endpoint = candidate.endpoint as? NmeaEndpoint.TcpClient ?: return emptySet()
        val normalizedCandidate = endpoint.normalizedIdentity()
        return state.connections.values
            .asSequence()
            .filter { it.id != candidate.id }
            .filter { existing ->
                (existing.endpoint as? NmeaEndpoint.TcpClient)?.normalizedIdentity() == normalizedCandidate
            }
            .mapTo(linkedSetOf()) { it.id }
    }

    private fun NmeaEndpoint.TcpClient.normalizedIdentity(): Pair<String, Int> =
        host.trim().trimEnd('.').lowercase() to port

    private companion object {
        val VALID_PORTS = 1..65_535
    }
}

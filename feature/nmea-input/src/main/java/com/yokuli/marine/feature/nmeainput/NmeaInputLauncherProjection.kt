package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionRuntimeSnapshot
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot

enum class NmeaInputTilePriority { UNCONFIGURED, STOPPED, WAITING, RECEIVING, ATTENTION }

enum class NmeaInputConnectionTileState {
    STOPPED,
    STARTING,
    TCP_WAITING,
    UDP_LISTENING,
    WAITING_FOR_NETWORK,
    RECONNECTING,
    INPUT_WITHOUT_VALID_NMEA,
    RECEIVING,
    INTERRUPTED,
    OVERLOADED,
    PLATFORM_START_REQUIRED,
    FAILED,
}

data class NmeaInputConnectionTileRow(
    val id: ConnectionId,
    val name: String,
    val state: NmeaInputConnectionTileState,
    val validFramesPerSecond: Double,
) {
    init {
        require(name.isNotBlank())
        require(validFramesPerSecond.isFinite() && validFramesPerSecond >= 0.0)
    }
}

data class NmeaInputTileState(
    val priority: NmeaInputTilePriority,
    val configuredCount: Int,
    val enabledCount: Int,
    val receivingCount: Int,
    val attentionCount: Int,
    val connectionRows: List<NmeaInputConnectionTileRow>,
) {
    init {
        require(configuredCount >= 0)
        require(enabledCount in 0..configuredCount)
        require(receivingCount in 0..enabledCount)
        require(attentionCount in 0..configuredCount)
        require(connectionRows.size == configuredCount)
    }

    val critical: Boolean get() = priority == NmeaInputTilePriority.ATTENTION
}

data class NmeaInputStatusState(
    val visible: Boolean,
    val receivingCount: Int,
    val enabledCount: Int,
    val attentionCount: Int,
    val preferredConnectionId: ConnectionId?,
)

data class NmeaInputLauncherState(
    val tile: NmeaInputTileState,
    val status: NmeaInputStatusState,
)

object NmeaInputLauncherProjector {
    fun project(snapshot: NmeaRuntimeSnapshot): NmeaInputLauncherState {
        val rows = snapshot.connections.map { connection -> connection.toTileRow() }
            .sortedWith(
                compareBy<NmeaInputConnectionTileRow> { it.state.sortOrder }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id.value },
            )
        val enabledCount = snapshot.connections.count { it.stored.runIntent == ConnectionRunIntent.ENABLED }
        val receivingCount = rows.count { it.state == NmeaInputConnectionTileState.RECEIVING }
        val attentionRows = rows.filter { it.state.isAttention }
        val priority = when {
            rows.isEmpty() -> NmeaInputTilePriority.UNCONFIGURED
            attentionRows.isNotEmpty() -> NmeaInputTilePriority.ATTENTION
            enabledCount == 0 -> NmeaInputTilePriority.STOPPED
            receivingCount > 0 -> NmeaInputTilePriority.RECEIVING
            else -> NmeaInputTilePriority.WAITING
        }
        val tile = NmeaInputTileState(
            priority = priority,
            configuredCount = rows.size,
            enabledCount = enabledCount,
            receivingCount = receivingCount,
            attentionCount = attentionRows.size,
            connectionRows = rows,
        )
        return NmeaInputLauncherState(
            tile = tile,
            status = NmeaInputStatusState(
                visible = enabledCount > 0,
                receivingCount = receivingCount,
                enabledCount = enabledCount,
                attentionCount = attentionRows.size,
                preferredConnectionId = attentionRows.firstOrNull()?.id,
            ),
        )
    }
}

class NmeaInputTileDisplaySlot(initial: NmeaInputTileState) {
    var shown: NmeaInputTileState = initial
        private set

    fun resolve(incoming: NmeaInputTileState, liveContentEnabled: Boolean): NmeaInputTileState {
        if (liveContentEnabled || incoming.critical || shown.critical != incoming.critical) shown = incoming
        return shown
    }
}

private fun ConnectionRuntimeSnapshot.toTileRow(): NmeaInputConnectionTileRow {
    val enabled = stored.runIntent == ConnectionRunIntent.ENABLED
    val tileState = when {
        !enabled -> NmeaInputConnectionTileState.STOPPED
        transport == ConnectionTransportState.Failed || failure != null -> NmeaInputConnectionTileState.FAILED
        transport == ConnectionTransportState.PlatformStartRequired -> NmeaInputConnectionTileState.PLATFORM_START_REQUIRED
        input == ConnectionInputState.OVERLOADED -> NmeaInputConnectionTileState.OVERLOADED
        input == ConnectionInputState.INTERRUPTED -> NmeaInputConnectionTileState.INTERRUPTED
        input == ConnectionInputState.RECEIVING_VALID_FRAMES -> NmeaInputConnectionTileState.RECEIVING
        input == ConnectionInputState.BYTES_WITHOUT_VALID_FRAME -> NmeaInputConnectionTileState.INPUT_WITHOUT_VALID_NMEA
        transport == ConnectionTransportState.UdpListening -> NmeaInputConnectionTileState.UDP_LISTENING
        transport == ConnectionTransportState.TcpConnected -> NmeaInputConnectionTileState.TCP_WAITING
        transport == ConnectionTransportState.WaitingForNetwork -> NmeaInputConnectionTileState.WAITING_FOR_NETWORK
        transport is ConnectionTransportState.ReconnectWaiting -> NmeaInputConnectionTileState.RECONNECTING
        transport == ConnectionTransportState.Starting -> NmeaInputConnectionTileState.STARTING
        else -> NmeaInputConnectionTileState.STARTING
    }
    return NmeaInputConnectionTileRow(
        id = stored.config.id,
        name = stored.config.displayName,
        state = tileState,
        validFramesPerSecond = metrics.validRate.framesPerSecond,
    )
}

private val NmeaInputConnectionTileState.isAttention: Boolean
    get() = this in setOf(
        NmeaInputConnectionTileState.INTERRUPTED,
        NmeaInputConnectionTileState.OVERLOADED,
        NmeaInputConnectionTileState.PLATFORM_START_REQUIRED,
        NmeaInputConnectionTileState.FAILED,
    )

private val NmeaInputConnectionTileState.sortOrder: Int
    get() = when {
        isAttention -> 0
        this == NmeaInputConnectionTileState.RECEIVING -> 2
        this == NmeaInputConnectionTileState.STOPPED -> 3
        else -> 1
    }

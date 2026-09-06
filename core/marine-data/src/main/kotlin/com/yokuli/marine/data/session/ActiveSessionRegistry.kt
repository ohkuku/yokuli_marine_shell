package com.yokuli.marine.data.session

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SessionGeneration

const val MAX_ACTIVE_CONNECTIONS = 32

data class ActiveSessionRegistrySnapshot(
    val activeSessions: Map<ConnectionId, SessionGeneration>,
    val knownConnectionCount: Int,
    val highWaterMark: Int,
    val activatedSessionCount: Long,
    val idempotentBeginCount: Long,
    val replacedSessionCount: Long,
    val endedSessionCount: Long,
    val capacityRejectionCount: Long,
    val staleBeginRejectionCount: Long,
    val endRejectionCount: Long,
    val forgottenConnectionCount: Long,
    val forgetRejectionCount: Long,
    val acceptedInboundCount: Long,
    val inboundRejectionCount: Long,
)

/**
 * The sole bounded runtime authority for active connection generations.
 *
 * Catalogs are deliberately not allowed to infer a session from the first packet. A runtime must
 * first call [beginSession], then every inbound path must pass [acceptInbound]. Replacing a
 * generation takes effect before its first frame, so a late callback from the prior socket can
 * never revive old data. The registry tracks connection sessions rather than UDP senders: every
 * sender observed through one connection therefore shares the same generation boundary.
 */
class ActiveSessionRegistry(
    private val maxActiveConnections: Int = MAX_ACTIVE_CONNECTIONS,
) {
    init {
        require(maxActiveConnections > 0) { "Active session capacity must be positive" }
    }

    enum class BeginResult {
        ACTIVATED,
        ALREADY_ACTIVE,
        REPLACED,
        REJECTED_CAPACITY,
        REJECTED_STALE_GENERATION,
    }

    private data class SessionSlot(
        val highestGeneration: SessionGeneration,
        val active: Boolean,
    )

    /** Includes inactive tombstones so a delayed older begin cannot revive an ended session. */
    private val knownSessions = linkedMapOf<ConnectionId, SessionSlot>()
    private var highWaterMark = 0
    private var activatedSessionCount = 0L
    private var idempotentBeginCount = 0L
    private var replacedSessionCount = 0L
    private var endedSessionCount = 0L
    private var capacityRejectionCount = 0L
    private var staleBeginRejectionCount = 0L
    private var endRejectionCount = 0L
    private var forgottenConnectionCount = 0L
    private var forgetRejectionCount = 0L
    private var acceptedInboundCount = 0L
    private var inboundRejectionCount = 0L

    @Synchronized
    fun beginSession(
        connectionId: ConnectionId,
        generation: SessionGeneration,
    ): BeginResult {
        val current = knownSessions[connectionId]
        if (current != null) {
            if (generation.value < current.highestGeneration.value) {
                staleBeginRejectionCount++
                return BeginResult.REJECTED_STALE_GENERATION
            }
            if (generation == current.highestGeneration) {
                if (current.active) {
                    idempotentBeginCount++
                    return BeginResult.ALREADY_ACTIVE
                }
                staleBeginRejectionCount++
                return BeginResult.REJECTED_STALE_GENERATION
            }
            knownSessions[connectionId] = SessionSlot(generation, active = true)
            replacedSessionCount++
            updateHighWaterMark()
            return BeginResult.REPLACED
        }
        if (knownSessions.size >= maxActiveConnections) {
            capacityRejectionCount++
            return BeginResult.REJECTED_CAPACITY
        }
        knownSessions[connectionId] = SessionSlot(generation, active = true)
        activatedSessionCount++
        updateHighWaterMark()
        return BeginResult.ACTIVATED
    }

    /** Ends only the exact generation, preventing a delayed old close callback from ending a replacement. */
    @Synchronized
    fun endSession(
        connectionId: ConnectionId,
        generation: SessionGeneration,
    ): Boolean {
        val current = knownSessions[connectionId]
        if (current?.highestGeneration != generation || !current.active) {
            endRejectionCount++
            return false
        }
        knownSessions[connectionId] = current.copy(active = false)
        endedSessionCount++
        return true
    }

    /** Releases one inactive tombstone when its persisted connection is deliberately deleted. */
    @Synchronized
    fun forgetConnection(connectionId: ConnectionId): Boolean {
        val current = knownSessions[connectionId]
        if (current == null || current.active) {
            forgetRejectionCount++
            return false
        }
        knownSessions.remove(connectionId)
        forgottenConnectionCount++
        return true
    }

    /** Authorizes and accounts for one inbound item without ever learning a generation from data. */
    @Synchronized
    fun acceptInbound(
        connectionId: ConnectionId,
        generation: SessionGeneration,
    ): Boolean {
        val current = knownSessions[connectionId]
        val accepted = current?.active == true && current.highestGeneration == generation
        if (accepted) {
            acceptedInboundCount++
        } else {
            inboundRejectionCount++
        }
        return accepted
    }

    /** Read-only status used to project retained catalog entries as current or historical. */
    @Synchronized
    fun isCurrent(
        connectionId: ConnectionId,
        generation: SessionGeneration,
    ): Boolean {
        val current = knownSessions[connectionId]
        return current?.active == true && current.highestGeneration == generation
    }

    @Synchronized
    fun snapshot(): ActiveSessionRegistrySnapshot = ActiveSessionRegistrySnapshot(
        activeSessions = knownSessions
            .filterValues { it.active }
            .mapValues { it.value.highestGeneration },
        knownConnectionCount = knownSessions.size,
        highWaterMark = highWaterMark,
        activatedSessionCount = activatedSessionCount,
        idempotentBeginCount = idempotentBeginCount,
        replacedSessionCount = replacedSessionCount,
        endedSessionCount = endedSessionCount,
        capacityRejectionCount = capacityRejectionCount,
        staleBeginRejectionCount = staleBeginRejectionCount,
        endRejectionCount = endRejectionCount,
        forgottenConnectionCount = forgottenConnectionCount,
        forgetRejectionCount = forgetRejectionCount,
        acceptedInboundCount = acceptedInboundCount,
        inboundRejectionCount = inboundRejectionCount,
    )

    private fun updateHighWaterMark() {
        highWaterMark = maxOf(highWaterMark, knownSessions.values.count { it.active })
    }
}

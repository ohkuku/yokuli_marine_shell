package com.yokuli.marine.data.session

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SessionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionRegistryTest {
    @Test
    fun activeSessionRegistryRemainsBoundedUnderRejectedOrigins() {
        val registry = ActiveSessionRegistry(maxActiveConnections = 4)

        repeat(1_000) { index ->
            registry.beginSession(ConnectionId("connection-$index"), SessionGeneration(1))
        }

        val snapshot = registry.snapshot()
        assertEquals(4, snapshot.activeSessions.size)
        assertEquals(4, snapshot.highWaterMark)
        assertEquals(996L, snapshot.capacityRejectionCount)
        assertEquals(ActiveSessionRegistry.BeginResult.REJECTED_CAPACITY, registry.beginSession(
            ConnectionId("still-full"),
            SessionGeneration(1),
        ))
    }

    @Test
    fun beginIsIdempotentAndOnlyANewerGenerationReplacesCurrent() {
        val registry = ActiveSessionRegistry(maxActiveConnections = 1)
        val connection = ConnectionId("gateway")

        assertEquals(
            ActiveSessionRegistry.BeginResult.ACTIVATED,
            registry.beginSession(connection, SessionGeneration(4)),
        )
        assertEquals(
            ActiveSessionRegistry.BeginResult.ALREADY_ACTIVE,
            registry.beginSession(connection, SessionGeneration(4)),
        )
        assertEquals(
            ActiveSessionRegistry.BeginResult.REJECTED_STALE_GENERATION,
            registry.beginSession(connection, SessionGeneration(3)),
        )
        assertEquals(
            ActiveSessionRegistry.BeginResult.REPLACED,
            registry.beginSession(connection, SessionGeneration(5)),
        )

        val snapshot = registry.snapshot()
        assertEquals(SessionGeneration(5), snapshot.activeSessions.getValue(connection))
        assertEquals(1L, snapshot.replacedSessionCount)
        assertEquals(1L, snapshot.staleBeginRejectionCount)
    }

    @Test
    fun endRequiresTheExactCurrentGeneration() {
        val registry = ActiveSessionRegistry()
        val connection = ConnectionId("gateway")
        registry.beginSession(connection, SessionGeneration(2))

        assertFalse(registry.endSession(connection, SessionGeneration(1)))
        assertTrue(registry.isCurrent(connection, SessionGeneration(2)))
        assertTrue(registry.endSession(connection, SessionGeneration(2)))
        assertFalse(registry.isCurrent(connection, SessionGeneration(2)))
        assertEquals(1L, registry.snapshot().endRejectionCount)
    }

    @Test
    fun endedSessionCannotBeReactivatedByOlderGeneration() {
        val registry = ActiveSessionRegistry(maxActiveConnections = 1)
        val connection = ConnectionId("gateway")
        registry.beginSession(connection, SessionGeneration(5))
        registry.endSession(connection, SessionGeneration(5))

        assertEquals(
            ActiveSessionRegistry.BeginResult.REJECTED_STALE_GENERATION,
            registry.beginSession(connection, SessionGeneration(4)),
        )
        assertEquals(
            ActiveSessionRegistry.BeginResult.REJECTED_STALE_GENERATION,
            registry.beginSession(connection, SessionGeneration(5)),
        )
        assertFalse(registry.isCurrent(connection, SessionGeneration(4)))
        assertEquals(1, registry.snapshot().knownConnectionCount)

        assertTrue(registry.forgetConnection(connection))
        assertEquals(0, registry.snapshot().knownConnectionCount)
        assertEquals(
            ActiveSessionRegistry.BeginResult.ACTIVATED,
            registry.beginSession(ConnectionId("replacement-config"), SessionGeneration(1)),
        )
    }

    @Test
    fun activeConnectionCannotBeForgottenUntilItIsEnded() {
        val registry = ActiveSessionRegistry(maxActiveConnections = 1)
        val connection = ConnectionId("gateway")
        registry.beginSession(connection, SessionGeneration(1))

        assertFalse(registry.forgetConnection(connection))
        assertEquals(1L, registry.snapshot().forgetRejectionCount)
        assertTrue(registry.endSession(connection, SessionGeneration(1)))
        assertTrue(registry.forgetConnection(connection))
        assertEquals(1L, registry.snapshot().forgottenConnectionCount)
    }

    @Test
    fun rejectedInboundIsObservableWithoutGrowingRegistryState() {
        val registry = ActiveSessionRegistry(maxActiveConnections = 2)
        val connection = ConnectionId("gateway")
        registry.beginSession(connection, SessionGeneration(8))

        repeat(10_000) { index ->
            assertFalse(
                registry.acceptInbound(
                    ConnectionId("unregistered-$index"),
                    SessionGeneration(1),
                ),
            )
        }
        assertFalse(registry.acceptInbound(connection, SessionGeneration(7)))
        assertTrue(registry.acceptInbound(connection, SessionGeneration(8)))

        val snapshot = registry.snapshot()
        assertEquals(1, snapshot.activeSessions.size)
        assertEquals(10_001L, snapshot.inboundRejectionCount)
        assertEquals(1L, snapshot.acceptedInboundCount)
    }
}

package com.yokuli.marine.data.session

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SessionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ActiveSessionRegistryTest {
    @Test
    fun inboundCommitIsLinearizedBeforeSessionReplacementReturns() {
        val registry = ActiveSessionRegistry()
        val connection = ConnectionId("gateway")
        registry.beginSession(connection, SessionGeneration(1))
        val commitEntered = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val commitFinished = CountDownLatch(1)
        val replacementStarted = CountDownLatch(1)
        val replacementFinished = CountDownLatch(1)
        val replacementResult = AtomicReference<ActiveSessionRegistry.BeginResult>()

        val inboundThread = thread(name = "p1-inbound-commit") {
            assertTrue(
                registry.commitInbound(connection, SessionGeneration(1)) {
                    commitEntered.countDown()
                    assertTrue(releaseCommit.await(5, TimeUnit.SECONDS))
                },
            )
            commitFinished.countDown()
        }
        assertTrue(commitEntered.await(5, TimeUnit.SECONDS))

        val replacementThread = thread(name = "p1-session-replacement") {
            replacementStarted.countDown()
            replacementResult.set(registry.beginSession(connection, SessionGeneration(2)))
            replacementFinished.countDown()
        }
        assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
        assertFalse(
            "beginSession must not return while an admitted catalog commit is unfinished",
            replacementFinished.await(100, TimeUnit.MILLISECONDS),
        )

        releaseCommit.countDown()
        assertTrue(commitFinished.await(5, TimeUnit.SECONDS))
        assertTrue(replacementFinished.await(5, TimeUnit.SECONDS))
        inboundThread.join(5_000)
        replacementThread.join(5_000)

        assertEquals(ActiveSessionRegistry.BeginResult.REPLACED, replacementResult.get())
        assertTrue(registry.isCurrent(connection, SessionGeneration(2)))
    }

    @Test
    fun inactiveTombstonesDoNotConsumeActiveSlotsButKnownConnectionsStayBounded() {
        val registry = ActiveSessionRegistry(
            maxActiveConnections = 1,
            maxKnownConnections = 2,
        )
        val first = ConnectionId("first")
        val second = ConnectionId("second")
        val third = ConnectionId("third")

        assertEquals(
            ActiveSessionRegistry.BeginResult.ACTIVATED,
            registry.beginSession(first, SessionGeneration(1)),
        )
        assertTrue(registry.endSession(first, SessionGeneration(1)))
        assertEquals(
            ActiveSessionRegistry.BeginResult.ACTIVATED,
            registry.beginSession(second, SessionGeneration(1)),
        )
        assertEquals(
            ActiveSessionRegistry.BeginResult.REJECTED_ACTIVE_CAPACITY,
            registry.beginSession(first, SessionGeneration(2)),
        )
        assertFalse(registry.isCurrent(first, SessionGeneration(2)))
        assertEquals(
            ActiveSessionRegistry.BeginResult.REJECTED_ACTIVE_CAPACITY,
            registry.beginSession(third, SessionGeneration(1)),
        )

        assertTrue(registry.endSession(second, SessionGeneration(1)))
        assertEquals(
            ActiveSessionRegistry.BeginResult.REJECTED_KNOWN_CAPACITY,
            registry.beginSession(third, SessionGeneration(1)),
        )
        assertEquals(2, registry.snapshot().knownConnectionCount)
        assertEquals(1, registry.snapshot().activeHighWaterMark)
        assertEquals(2, registry.snapshot().knownHighWaterMark)
        assertEquals(2L, registry.snapshot().activeCapacityRejectionCount)
        assertEquals(1L, registry.snapshot().knownCapacityRejectionCount)

        assertTrue(registry.forgetConnection(first))
        assertEquals(
            ActiveSessionRegistry.BeginResult.ACTIVATED,
            registry.beginSession(third, SessionGeneration(1)),
        )
        assertEquals(2, registry.snapshot().knownConnectionCount)
    }

    @Test
    fun activeSessionRegistryRemainsBoundedUnderRejectedOrigins() {
        val registry = ActiveSessionRegistry(maxActiveConnections = 4)

        repeat(1_000) { index ->
            registry.beginSession(ConnectionId("connection-$index"), SessionGeneration(1))
        }

        val snapshot = registry.snapshot()
        assertEquals(4, snapshot.activeSessions.size)
        assertEquals(4, snapshot.highWaterMark)
        assertEquals(4, snapshot.activeHighWaterMark)
        assertEquals(4, snapshot.knownHighWaterMark)
        assertEquals(996L, snapshot.capacityRejectionCount)
        assertEquals(ActiveSessionRegistry.BeginResult.REJECTED_ACTIVE_CAPACITY, registry.beginSession(
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
                registry.commitInbound(
                    ConnectionId("unregistered-$index"),
                    SessionGeneration(1),
                ) {},
            )
        }
        assertFalse(registry.commitInbound(connection, SessionGeneration(7)) {})
        assertTrue(registry.commitInbound(connection, SessionGeneration(8)) {})

        val snapshot = registry.snapshot()
        assertEquals(1, snapshot.activeSessions.size)
        assertEquals(10_001L, snapshot.inboundRejectionCount)
        assertEquals(1L, snapshot.acceptedInboundCount)
    }
}

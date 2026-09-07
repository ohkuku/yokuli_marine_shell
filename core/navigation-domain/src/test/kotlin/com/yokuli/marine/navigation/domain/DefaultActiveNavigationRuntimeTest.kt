package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultActiveNavigationRuntimeTest {
    @Test
    fun `start and controls are serialized against an exact saved route revision`() = runTest {
        val fixture = Fixture(this)
        fixture.runtime.initialize()

        assertTrue(fixture.runtime.execute(ActiveNavigationCommand.Start("route", 3)) is ActiveNavigationCommandResult.Accepted)
        assertEquals(NavigationSessionState.ACTIVE, fixture.runtime.state.value.sessionState)
        assertTrue(fixture.runtime.execute(ActiveNavigationCommand.Start("route", 2)) is ActiveNavigationCommandResult.Rejected)
        assertEquals(NavigationSessionState.ACTIVE, fixture.runtime.state.value.sessionState)
        fixture.runtime.execute(ActiveNavigationCommand.NextWaypoint)
        assertEquals(1, fixture.runtime.state.value.session?.activeLegIndex)
        fixture.runtime.execute(ActiveNavigationCommand.Stop)
        assertNull(fixture.runtime.state.value.session)
        assertNull(fixture.store.value)
    }

    @Test
    fun `process restore pauses an active session and waits for a fresh process input`() = runTest {
        val stored = session()
        val fixture = Fixture(this, stored)

        fixture.runtime.initialize()

        assertEquals(NavigationSessionState.PAUSED, fixture.runtime.state.value.sessionState)
        assertNull(fixture.runtime.state.value.solution)
        assertEquals(NavigationSessionState.PAUSED, fixture.store.value?.state)
        fixture.runtime.execute(ActiveNavigationCommand.Resume)
        assertNull(fixture.runtime.state.value.solution)
        assertEquals(ActiveNavigationIssue.INPUT_UNAVAILABLE, fixture.runtime.state.value.issue)
        fixture.input.value = usableFix(2)
        advanceUntilIdle()
        assertTrue(fixture.runtime.state.value.solution != null)
    }

    @Test
    fun `arrival policy advances once and stale revisions cannot move the active leg`() = runTest {
        val fixture = Fixture(this)
        fixture.runtime.initialize()
        fixture.runtime.execute(
            ActiveNavigationCommand.Start("route", 3, arrivalRadiusMeters = 200.0, advancePolicy = NavigationAdvancePolicy.ARRIVAL_RADIUS),
        )
        fixture.input.value = NavigationFix(
            revision = 2,
            position = NavigationPosition(-36.80, 174.86),
            positionStatus = NavigationInputStatus.LIVE,
            positionSourceId = "source",
            receivedAtMonotonicMillis = 200,
        )
        advanceUntilIdle()
        assertEquals(1, fixture.runtime.state.value.session?.activeLegIndex)

        fixture.input.value = usableFix(1)
        advanceUntilIdle()
        assertEquals(1, fixture.runtime.state.value.session?.activeLegIndex)
    }

    @Test
    fun `missing or changed restored route is rejected without inventing a session`() = runTest {
        val fixture = Fixture(this, session().copy(routeRevision = 2))
        fixture.runtime.initialize()
        assertNull(fixture.runtime.state.value.session)
        assertEquals(ActiveNavigationIssue.ROUTE_REVISION_CHANGED, fixture.runtime.state.value.issue)
        assertNull(fixture.store.value)
    }

    @Test
    fun `direct to uses current fix without creating a durable library route`() = runTest {
        val fixture = Fixture(this)
        fixture.input.value = usableFix(1)
        fixture.runtime.initialize()

        val result = fixture.runtime.execute(
            ActiveNavigationCommand.DirectTo(
                destination = NavigationPosition(-36.80, 174.86),
                destinationId = "waypoint-14",
                destinationName = "WP 014",
            ),
        )

        assertTrue(result is ActiveNavigationCommandResult.Accepted)
        assertEquals(listOf("direct-to-origin", "waypoint-14"), fixture.runtime.state.value.route?.points?.map { it.id })
        assertEquals(fixture.runtime.state.value.route, fixture.store.value?.embeddedRoute)
        assertTrue(fixture.runtime.state.value.solution != null)
    }

    @Test
    fun `direct to rejects unavailable position and embedded session restores without route library`() = runTest {
        val first = Fixture(this)
        first.runtime.initialize()
        assertEquals(
            ActiveNavigationIssue.INPUT_UNAVAILABLE,
            (first.runtime.execute(
                ActiveNavigationCommand.DirectTo(NavigationPosition(-36.80, 174.86), "target", "Target"),
            ) as ActiveNavigationCommandResult.Rejected).issue,
        )

        first.input.value = usableFix(1)
        advanceUntilIdle()
        first.runtime.execute(
            ActiveNavigationCommand.DirectTo(NavigationPosition(-36.80, 174.86), "target", "Target"),
        )
        val restored = Fixture(this, requireNotNull(first.store.value))
        restored.runtime.initialize()
        assertEquals(NavigationSessionState.PAUSED, restored.runtime.state.value.sessionState)
        assertEquals("target", restored.runtime.state.value.route?.points?.last()?.id)
    }

    @Test
    fun `active navigation cannot be replaced until the pending proposal is explicitly confirmed`() = runTest {
        val fixture = Fixture(this)
        fixture.input.value = usableFix(1)
        fixture.runtime.initialize()
        fixture.runtime.execute(ActiveNavigationCommand.Start("route", 3))
        val activeBefore = requireNotNull(fixture.store.value)

        val requested = fixture.runtime.execute(
            ActiveNavigationCommand.DirectTo(
                destination = NavigationPosition(-36.80, 174.86),
                destinationId = "target",
                destinationName = "Target B",
            ),
        )

        assertTrue(requested is ActiveNavigationCommandResult.ReplacementRequired)
        assertEquals("route", fixture.runtime.state.value.session?.routeId)
        assertEquals(activeBefore, fixture.store.value)
        assertEquals("Target B", fixture.runtime.state.value.pendingReplacement?.displayName)

        fixture.runtime.execute(ActiveNavigationCommand.CancelReplacement)
        assertNull(fixture.runtime.state.value.pendingReplacement)
        assertEquals(activeBefore, fixture.store.value)

        fixture.runtime.execute(
            ActiveNavigationCommand.DirectTo(
                destination = NavigationPosition(-36.80, 174.86),
                destinationId = "target",
                destinationName = "Target B",
            ),
        )
        val confirmed = fixture.runtime.execute(ActiveNavigationCommand.ConfirmReplacement)

        assertTrue(confirmed is ActiveNavigationCommandResult.Accepted)
        assertEquals("target", fixture.runtime.state.value.route?.points?.last()?.id)
        assertEquals(fixture.runtime.state.value.session, fixture.store.value)
        assertNull(fixture.runtime.state.value.pendingReplacement)
    }

    @Test
    fun `replacement persistence failure leaves the original session intact`() = runTest {
        val fixture = Fixture(this)
        fixture.input.value = usableFix(1)
        fixture.runtime.initialize()
        fixture.runtime.execute(ActiveNavigationCommand.Start("route", 3))
        val original = requireNotNull(fixture.store.value)
        fixture.runtime.execute(
            ActiveNavigationCommand.DirectTo(
                destination = NavigationPosition(-36.80, 174.86),
                destinationId = "target",
                destinationName = "Target B",
            ),
        )
        fixture.store.failSaves = true

        val result = fixture.runtime.execute(ActiveNavigationCommand.ConfirmReplacement)

        assertEquals(
            ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED,
            (result as ActiveNavigationCommandResult.Rejected).issue,
        )
        assertEquals("route", fixture.runtime.state.value.session?.routeId)
        assertEquals(original, fixture.store.value)
        assertTrue(fixture.runtime.state.value.pendingReplacement != null)
    }

    private class Fixture(
        testScope: TestScope,
        stored: ActiveNavigationSession? = null,
    ) {
        private val runtimeScope = CoroutineScope(
            testScope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScope.testScheduler),
        )
        val input = MutableStateFlow(NavigationFix())
        val store = FakeStore(stored)
        private val route = route()
        val runtime = DefaultActiveNavigationRuntime(
            routes = NavigationRouteReadPort { if (it == route.id) route else null },
            input = object : NavigationInputPort { override val state = input },
            sessionStore = store,
            clock = NavigationRuntimeClock { 5_000 },
            scope = runtimeScope,
        )
    }

    private class FakeStore(initial: ActiveNavigationSession?) : ActiveNavigationSessionStore {
        var value = initial
        var failSaves = false
        override suspend fun loadActiveNavigationSession(): NavigationSessionLoadResult =
            value?.let(NavigationSessionLoadResult::Loaded) ?: NavigationSessionLoadResult.Empty
        override suspend fun saveActiveNavigationSession(session: ActiveNavigationSession?): NavigationSessionSaveResult {
            if (failSaves) return NavigationSessionSaveResult.Failed(NavigationSessionStoreFailure.IO)
            value = session
            return NavigationSessionSaveResult.Saved
        }
    }

    companion object {
        private fun route() = RoutePlan(
            "route", 3, "Harbour",
            listOf(
                RoutePoint("A", NavigationPosition(-36.85, 174.76)),
                RoutePoint("B", NavigationPosition(-36.80, 174.86)),
                RoutePoint("C", NavigationPosition(-36.70, 174.95)),
            ),
        )
        private fun session() = ActiveNavigationSession(
            "route", 3, 1_000, 0, 50.0, NavigationAdvancePolicy.MANUAL, NavigationSessionState.ACTIVE,
        )
        private fun usableFix(revision: Long) = NavigationFix(
            revision,
            NavigationPosition(-36.84, 174.79),
            NavigationInputStatus.LIVE,
            "source",
            null,
            100,
            0,
        )
    }
}

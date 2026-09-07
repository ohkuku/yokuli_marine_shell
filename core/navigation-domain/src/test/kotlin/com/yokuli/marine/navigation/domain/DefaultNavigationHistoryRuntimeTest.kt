package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNavigationHistoryRuntimeTest {
    @Test
    fun `passage captures route snapshot and truthful waypoint evidence`() = runTest {
        val store = FakeHistoryStore()
        val runtime = DefaultNavigationHistoryRuntime(store)
        val session = session()
        val route = route()

        runtime.begin(session, route)
        runtime.recordAdvance(
            session = session,
            route = route,
            waypointIndex = 1,
            eventAtEpochMillis = 2_000,
            actualPosition = NavigationPosition(-36.801, 174.861),
            positionSourceId = "selected-position",
            reason = NavigationAdvanceReason.ARRIVAL_RADIUS,
            terminalOutcome = null,
        )
        runtime.recordAdvance(
            session = session.copy(activeLegIndex = 1),
            route = route,
            waypointIndex = 2,
            eventAtEpochMillis = 3_000,
            actualPosition = null,
            positionSourceId = null,
            reason = NavigationAdvanceReason.MANUAL,
            terminalOutcome = NavigationPassageOutcome.COMPLETED,
        )

        val passage = runtime.state.value.passages.single()
        assertEquals(route.name, passage.routeName)
        assertEquals(NavigationPassageOutcome.COMPLETED, passage.outcome)
        assertEquals(NavigationPassageEvidence.POSITION_RECORDED, passage.waypoints[1].event?.evidence)
        assertEquals(NavigationPassageEvidence.NO_POSITION_EVIDENCE, passage.waypoints[2].event?.evidence)
        assertNull(passage.waypoints[2].event?.actualPosition)
        assertEquals(store.value, runtime.state.value)
    }

    @Test
    fun `finish and duplicate callbacks are idempotent`() = runTest {
        val runtime = DefaultNavigationHistoryRuntime(FakeHistoryStore())
        val session = session()
        runtime.begin(session, route())
        runtime.begin(session, route())
        runtime.finish(session.sessionId, NavigationPassageOutcome.STOPPED, 4_000)
        val revision = runtime.state.value.revision
        runtime.finish(session.sessionId, NavigationPassageOutcome.STOPPED, 4_000)

        assertEquals(revision, runtime.state.value.revision)
        assertEquals(1, runtime.state.value.passages.size)
    }

    @Test
    fun `restored history remains durable and bounded without evicting an active passage`() = runTest {
        val old = passage("old", NavigationPassageOutcome.STOPPED, 2_000)
        val active = passage("active", NavigationPassageOutcome.ACTIVE, null)
        val store = FakeHistoryStore(NavigationHistorySnapshot(2, listOf(old, active)))
        val runtime = DefaultNavigationHistoryRuntime(store, maxPassages = 2)
        runtime.initialize()

        val result = runtime.begin(
            session().copy(
                sessionId = "new-passage",
                startedAtEpochMillis = 5_000,
            ),
            route(),
        )

        assertTrue(result is NavigationHistoryWriteResult.Saved)
        assertEquals(listOf("active", "new-passage"), runtime.state.value.passages.map { it.sessionId })
    }

    @Test
    fun `restore reconciles progression without fabricating a passage event`() = runTest {
        val store = FakeHistoryStore()
        val runtime = DefaultNavigationHistoryRuntime(store)
        runtime.begin(session().copy(activeLegIndex = 1), route())

        val passage = runtime.state.value.passages.single()
        assertEquals(1, passage.furthestAdvancedWaypointIndex)
        assertNull(passage.waypoints[1].event)
    }

    @Test
    fun `orphaned active passage becomes interrupted instead of staying active forever`() = runTest {
        val store = FakeHistoryStore(NavigationHistorySnapshot(1, listOf(passage("orphan", NavigationPassageOutcome.ACTIVE, null))))
        val runtime = DefaultNavigationHistoryRuntime(store)

        runtime.reconcileActiveSession(activeSessionId = null, observedAtEpochMillis = 8_000)

        assertEquals(NavigationPassageOutcome.INTERRUPTED, runtime.state.value.passages.single().outcome)
        assertEquals(8_000L, runtime.state.value.passages.single().endedAtEpochMillis)
    }

    @Test
    fun `invalid wall clock transition is rejected without corrupting active history`() = runTest {
        val runtime = DefaultNavigationHistoryRuntime(FakeHistoryStore())
        val session = session()
        runtime.begin(session, route())

        val result = runtime.finish(session.sessionId, NavigationPassageOutcome.STOPPED, 500)

        assertEquals(NavigationHistoryIssue.CONFLICT, (result as NavigationHistoryWriteResult.Failed).issue)
        assertEquals(NavigationPassageOutcome.ACTIVE, runtime.state.value.passages.single().outcome)
    }

    private class FakeHistoryStore(initial: NavigationHistorySnapshot = NavigationHistorySnapshot.EMPTY) : NavigationHistoryStore {
        var value = initial
        override suspend fun loadNavigationHistory() = NavigationHistoryLoadResult.Loaded(value)
        override suspend fun saveNavigationHistory(snapshot: NavigationHistorySnapshot): NavigationHistorySaveResult {
            value = snapshot
            return NavigationHistorySaveResult.Saved
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

        private fun passage(id: String, outcome: NavigationPassageOutcome, endedAt: Long?) = NavigationPassage(
            sessionId = id,
            revision = 1,
            routeId = "route-$id",
            routeRevision = 1,
            routeName = id,
            startedAtEpochMillis = 1_000,
            endedAtEpochMillis = endedAt,
            outcome = outcome,
            waypoints = listOf(
                NavigationPassageWaypoint(0, "A", NavigationPosition(-36.85, 174.76)),
                NavigationPassageWaypoint(1, "B", NavigationPosition(-36.80, 174.86)),
            ),
        )
    }
}

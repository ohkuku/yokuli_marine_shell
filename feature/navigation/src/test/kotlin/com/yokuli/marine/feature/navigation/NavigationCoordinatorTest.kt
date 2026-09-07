package com.yokuli.marine.feature.navigation

import com.yokuli.marine.navigation.domain.ActiveNavigationCommand
import com.yokuli.marine.navigation.domain.ActiveNavigationCommandResult
import com.yokuli.marine.navigation.domain.ActiveNavigationRuntimePort
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.navigation.domain.NavigationChangeResult
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationLibraryChange
import com.yokuli.marine.navigation.domain.NavigationLibraryCommitResult
import com.yokuli.marine.navigation.domain.NavigationLibraryEditor
import com.yokuli.marine.navigation.domain.NavigationLibraryLoadResult
import com.yokuli.marine.navigation.domain.NavigationLibraryPort
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCoordinatorTest {
    @Test
    fun `waypoint and route editing commit through the one revision checked library`() = runTest {
        val library = FakeLibrary()
        val coordinator = coordinator(library)
        runCurrent()

        coordinator.dispatch(NavigationUiAction.CreateWaypoint)
        coordinator.dispatch(NavigationUiAction.UpdateWaypointDraft(name = "Westhaven", latitude = "-36.84", longitude = "174.75"))
        coordinator.dispatch(NavigationUiAction.SaveWaypoint)
        runCurrent()
        val first = coordinator.state.value.library.waypoints.single()

        coordinator.dispatch(NavigationUiAction.CreateWaypoint)
        coordinator.dispatch(NavigationUiAction.UpdateWaypointDraft(name = "Rangitoto", latitude = "-36.79", longitude = "174.89"))
        coordinator.dispatch(NavigationUiAction.SaveWaypoint)
        runCurrent()
        val second = coordinator.state.value.library.waypoints.last()

        coordinator.dispatch(NavigationUiAction.CreateRoute)
        coordinator.dispatch(NavigationUiAction.UpdateRouteDraft(name = "Harbour passage", plannedSpeedKnots = "6.5"))
        coordinator.dispatch(NavigationUiAction.AddWaypointToRoute(first.id))
        coordinator.dispatch(NavigationUiAction.AddWaypointToRoute(second.id))
        coordinator.dispatch(NavigationUiAction.SaveRoute)
        runCurrent()

        val route = coordinator.state.value.library.routePlans.single()
        assertEquals("Harbour passage", route.name)
        assertEquals(6.5, route.plannedSpeedKnots!!, 0.0)
        assertEquals(listOf(first.id, second.id), route.points.map { it.sourceWaypoint?.waypointId })
        assertTrue(coordinator.state.value.page is NavigationPage.RouteDetail)
        assertEquals(3L, library.value.revision)
    }

    @Test
    fun `stale write remains visible as conflict and never overwrites the newer library`() = runTest {
        val point = waypoint("point", 1)
        val library = FakeLibrary(NavigationLibrary(revision = 4, waypoints = listOf(point)))
        val coordinator = coordinator(library)
        runCurrent()
        coordinator.dispatch(NavigationUiAction.EditWaypoint(point.id))
        coordinator.dispatch(NavigationUiAction.UpdateWaypointDraft(name = "stale name"))
        library.forceExternalRevision()

        coordinator.dispatch(NavigationUiAction.SaveWaypoint)
        runCurrent()

        assertEquals(NavigationNotice.REVISION_CONFLICT, coordinator.state.value.notice)
        assertEquals("point", library.value.waypoints.single().name)
    }

    @Test
    fun `active route deletion is blocked while typed start stop and Chart handoff stay explicit`() = runTest {
        val route = route()
        val active = FakeActive()
        val coordinator = coordinator(FakeLibrary(NavigationLibrary(revision = 2, routePlans = listOf(route))), active)
        runCurrent()

        coordinator.dispatch(NavigationUiAction.StartRoute(route.id, route.revision))
        runCurrent()
        assertEquals(ActiveNavigationCommand.Start(route.id, route.revision), active.commands.single())

        active.mutable.value = activeSnapshot(route)
        runCurrent()
        coordinator.dispatch(NavigationUiAction.DeleteRoute(route.id, route.revision))
        assertEquals(NavigationNotice.ACTIVE_ROUTE_LOCKED, coordinator.state.value.notice)
        assertEquals(1, coordinator.state.value.library.routePlans.size)

        coordinator.dispatch(NavigationUiAction.ShowRouteInChart(route.id))
        assertEquals(NavigationEffect.ShowRouteInChart(route.id), coordinator.effects.first())
        coordinator.dispatch(NavigationUiAction.ActiveCommand(ActiveNavigationCommand.Stop))
        runCurrent()
        assertEquals(ActiveNavigationCommand.Stop, active.commands.last())
    }

    @Test
    fun `Navigation owns its back stack before Shell returns from overview root`() = runTest {
        val coordinator = coordinator(FakeLibrary())
        runCurrent()
        assertFalse(coordinator.handleBack())
        coordinator.dispatch(NavigationUiAction.Navigate(NavigationSection.ROUTES))
        coordinator.dispatch(NavigationUiAction.CreateRoute)
        assertTrue(coordinator.handleBack())
        assertEquals(NavigationPage.Root, coordinator.state.value.page)
        assertTrue(coordinator.handleBack())
        assertEquals(NavigationSection.OVERVIEW, coordinator.state.value.section)
        assertFalse(coordinator.handleBack())
    }

    @Test
    fun `new route is auto named and map geometry replaces the draft without a metadata form`() = runTest {
        val coordinator = coordinator(FakeLibrary())
        runCurrent()

        coordinator.dispatch(NavigationUiAction.CreateRoute)
        val created = (coordinator.state.value.page as NavigationPage.RouteEditor).draft
        assertEquals("Route 001", created.name)

        val points = listOf(
            RoutePoint("map-1", NavigationPosition(-36.84, 174.75)),
            RoutePoint("map-2", NavigationPosition(-36.78, 174.88)),
            RoutePoint("map-3", NavigationPosition(-36.74, 174.91)),
        )
        coordinator.dispatch(NavigationUiAction.ReplaceRouteGeometry(points))

        assertEquals(points, (coordinator.state.value.page as NavigationPage.RouteEditor).draft.points)
        assertTrue(coordinator.state.value.library.routePlans.isEmpty())
    }

    @Test
    fun `save and start commits the exact map geometry before starting navigation`() = runTest {
        val library = FakeLibrary()
        val active = FakeActive()
        val coordinator = coordinator(library, active)
        runCurrent()
        coordinator.dispatch(NavigationUiAction.CreateRoute)
        coordinator.dispatch(
            NavigationUiAction.ReplaceRouteGeometry(
                listOf(
                    RoutePoint("map-1", NavigationPosition(-36.84, 174.75)),
                    RoutePoint("map-2", NavigationPosition(-36.78, 174.88)),
                ),
            ),
        )

        coordinator.dispatch(NavigationUiAction.SaveAndStartRoute)
        runCurrent()

        val saved = library.value.routePlans.single()
        assertEquals(listOf("map-1", "map-2"), saved.points.map(RoutePoint::id))
        assertEquals(ActiveNavigationCommand.Start(saved.id, saved.revision), active.commands.single())
        assertEquals(NavigationSection.ACTIVE, coordinator.state.value.section)
        assertEquals(NavigationPage.Root, coordinator.state.value.page)
    }

    @Test
    fun `back from nonempty map draft requires an explicit decision and discard cannot resurrect it`() = runTest {
        val coordinator = coordinator(FakeLibrary())
        runCurrent()
        coordinator.dispatch(NavigationUiAction.CreateRoute)
        coordinator.dispatch(
            NavigationUiAction.ReplaceRouteGeometry(
                listOf(RoutePoint("map-1", NavigationPosition(-36.84, 174.75))),
            ),
        )

        assertTrue(coordinator.handleBack())
        assertTrue(coordinator.state.value.page is NavigationPage.RouteCloseConfirmation)

        coordinator.dispatch(NavigationUiAction.DiscardRouteDraft)
        assertEquals(NavigationPage.Root, coordinator.state.value.page)
        coordinator.dispatch(NavigationUiAction.CreateRoute)
        assertTrue((coordinator.state.value.page as NavigationPage.RouteEditor).draft.points.isEmpty())
        assertTrue(coordinator.state.value.library.routeDrafts.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        library: FakeLibrary,
        active: FakeActive = FakeActive(),
    ) = NavigationCoordinator(library, active, nowMillis = { 1_000 }, newId = Ids::next, scope = backgroundScope)

    private class FakeLibrary(initial: NavigationLibrary = NavigationLibrary()) : NavigationLibraryPort {
        var value = initial
            private set

        override suspend fun loadNavigationLibrary() = NavigationLibraryLoadResult.Ready(value)

        override suspend fun commitNavigationChange(
            expectedLibraryRevision: Long,
            change: NavigationLibraryChange,
        ): NavigationLibraryCommitResult {
            if (expectedLibraryRevision != value.revision) return NavigationLibraryCommitResult.Conflict(value.revision)
            return when (val result = NavigationLibraryEditor.apply(value, change)) {
                is NavigationChangeResult.Applied -> {
                    value = result.library
                    NavigationLibraryCommitResult.Committed(value.revision)
                }
                is NavigationChangeResult.Rejected -> NavigationLibraryCommitResult.Rejected(result.reason)
            }
        }

        fun forceExternalRevision() { value = value.copy(revision = value.revision + 1) }
    }

    private class FakeActive : ActiveNavigationRuntimePort {
        val mutable = MutableStateFlow(ActiveNavigationSnapshot.EMPTY)
        override val state: StateFlow<ActiveNavigationSnapshot> = mutable
        val commands = mutableListOf<ActiveNavigationCommand>()
        override suspend fun initialize() = mutable.value
        override suspend fun execute(command: ActiveNavigationCommand): ActiveNavigationCommandResult {
            commands += command
            return ActiveNavigationCommandResult.Accepted(commands.size.toLong())
        }
    }

    private object Ids {
        private var sequence = 0
        fun next() = "test-${++sequence}"
    }

    private fun waypoint(id: String, revision: Long) = Waypoint(id, revision, id, NavigationPosition(-36.84, 174.75))

    private fun route() = RoutePlan(
        "route", 3, "Passage",
        listOf(
            RoutePoint("a", NavigationPosition(-36.84, 174.75)),
            RoutePoint("b", NavigationPosition(-36.78, 174.88)),
        ),
    )

    private fun activeSnapshot(route: RoutePlan): ActiveNavigationSnapshot {
        val session = com.yokuli.marine.navigation.domain.ActiveNavigationSession(
            route.id, route.revision, 1_000, 0, 50.0,
            com.yokuli.marine.navigation.domain.NavigationAdvancePolicy.MANUAL,
            com.yokuli.marine.navigation.domain.NavigationSessionState.ACTIVE,
        )
        return ActiveNavigationSnapshot(revision = 1, session = session, route = route)
    }
}

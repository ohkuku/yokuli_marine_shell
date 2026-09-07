package com.yokuli.marine.navigation.domain

import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ActiveNavigationRuntimePort {
    val state: StateFlow<ActiveNavigationSnapshot>
    suspend fun initialize(): ActiveNavigationSnapshot
    suspend fun execute(command: ActiveNavigationCommand): ActiveNavigationCommandResult
}

class DefaultActiveNavigationRuntime(
    private val routes: NavigationRouteReadPort,
    private val input: NavigationInputPort,
    private val sessionStore: ActiveNavigationSessionStore,
    private val clock: NavigationRuntimeClock,
    scope: CoroutineScope,
) : ActiveNavigationRuntimePort, Closeable {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ActiveNavigationSnapshot.EMPTY.copy(fix = input.state.value))
    override val state: StateFlow<ActiveNavigationSnapshot> = mutableState.asStateFlow()
    private var initialized = false
    private var revision = 0L
    private var currentFix = input.state.value
    private var session: ActiveNavigationSession? = null
    private var route: RoutePlan? = null
    private var issue: ActiveNavigationIssue? = null
    private var pendingReplacement: NavigationStartProposal? = null
    private val inputJob: Job = scope.launch {
        input.state.collect { incoming ->
            mutex.withLock {
                if (incoming.revision < currentFix.revision) return@withLock
                currentFix = incoming
                if (initialized) reconcileInputLocked()
            }
        }
    }

    override suspend fun initialize(): ActiveNavigationSnapshot = mutex.withLock {
        ensureInitializedLocked()
        mutableState.value
    }

    override suspend fun execute(command: ActiveNavigationCommand): ActiveNavigationCommandResult = mutex.withLock {
        ensureInitializedLocked()
        when (command) {
            is ActiveNavigationCommand.Start -> startLocked(command)
            is ActiveNavigationCommand.DirectTo -> directToLocked(command)
            ActiveNavigationCommand.Pause -> changeStateLocked(NavigationSessionState.PAUSED)
            ActiveNavigationCommand.Resume -> changeStateLocked(NavigationSessionState.ACTIVE)
            ActiveNavigationCommand.Stop -> stopLocked()
            ActiveNavigationCommand.NextWaypoint -> moveLegLocked(1)
            ActiveNavigationCommand.PreviousWaypoint -> moveLegLocked(-1)
            ActiveNavigationCommand.ConfirmReplacement -> confirmReplacementLocked()
            ActiveNavigationCommand.CancelReplacement -> cancelReplacementLocked()
        }
    }

    override fun close() { inputJob.cancel() }

    private suspend fun ensureInitializedLocked() {
        if (initialized) return
        when (val loaded = sessionStore.loadActiveNavigationSession()) {
            NavigationSessionLoadResult.Empty -> Unit
            is NavigationSessionLoadResult.Failed -> issue = ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED
            is NavigationSessionLoadResult.Loaded -> restoreLocked(loaded.session)
        }
        initialized = true
        publishLocked()
    }

    private suspend fun restoreLocked(stored: ActiveNavigationSession) {
        val loadedRoute = stored.embeddedRoute ?: routes.route(stored.routeId)
        when {
            loadedRoute == null -> {
                issue = ActiveNavigationIssue.ROUTE_NOT_FOUND
                sessionStore.saveActiveNavigationSession(null)
            }
            loadedRoute.revision != stored.routeRevision -> {
                issue = ActiveNavigationIssue.ROUTE_REVISION_CHANGED
                sessionStore.saveActiveNavigationSession(null)
            }
            loadedRoute.points.size < 2 || stored.activeLegIndex !in 0 until loadedRoute.points.lastIndex -> {
                issue = ActiveNavigationIssue.ROUTE_TOO_SHORT
                sessionStore.saveActiveNavigationSession(null)
            }
            else -> {
                route = loadedRoute
                session = if (stored.state == NavigationSessionState.ACTIVE) {
                    stored.copy(state = NavigationSessionState.PAUSED).also {
                        if (sessionStore.saveActiveNavigationSession(it) is NavigationSessionSaveResult.Failed) {
                            issue = ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED
                        }
                    }
                } else stored
            }
        }
    }

    private suspend fun startLocked(command: ActiveNavigationCommand.Start): ActiveNavigationCommandResult {
        if (command.routeId.isBlank() || command.routeRevision <= 0L ||
            !command.arrivalRadiusMeters.isFinite() || command.arrivalRadiusMeters <= 0.0
        ) return rejectLocked(ActiveNavigationIssue.INVALID_COMMAND)
        val loaded = routes.route(command.routeId) ?: return rejectLocked(ActiveNavigationIssue.ROUTE_NOT_FOUND)
        if (loaded.revision != command.routeRevision) return rejectLocked(ActiveNavigationIssue.ROUTE_REVISION_CHANGED)
        if (loaded.points.size < 2) return rejectLocked(ActiveNavigationIssue.ROUTE_TOO_SHORT)
        if (requiresReplacementLocked(loaded.id, loaded.revision)) {
            return requestReplacementLocked(
                NavigationStartProposal.SavedRoute(
                    routeId = loaded.id,
                    routeRevision = loaded.revision,
                    displayName = loaded.name,
                    arrivalRadiusMeters = command.arrivalRadiusMeters,
                    advancePolicy = command.advancePolicy,
                ),
            )
        }
        if (session?.routeId == loaded.id && session?.routeRevision == loaded.revision &&
            session?.state != NavigationSessionState.COMPLETE
        ) return ActiveNavigationCommandResult.Accepted(revision)
        return startRouteLocked(loaded, command.arrivalRadiusMeters, command.advancePolicy)
    }

    private suspend fun startRouteLocked(
        loaded: RoutePlan,
        arrivalRadiusMeters: Double,
        advancePolicy: NavigationAdvancePolicy,
    ): ActiveNavigationCommandResult {
        val next = ActiveNavigationSession(
            routeId = loaded.id,
            routeRevision = loaded.revision,
            startedAtEpochMillis = clock.wallTimeMillis(),
            activeLegIndex = 0,
            arrivalRadiusMeters = arrivalRadiusMeters,
            advancePolicy = advancePolicy,
            state = NavigationSessionState.ACTIVE,
        )
        if (!persistLocked(next)) return rejectLocked(ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED)
        route = loaded
        session = next
        issue = null
        pendingReplacement = null
        publishLocked()
        return ActiveNavigationCommandResult.Accepted(revision)
    }

    private suspend fun directToLocked(command: ActiveNavigationCommand.DirectTo): ActiveNavigationCommandResult {
        if (
            command.destinationId.isBlank() || command.destinationName.isBlank() ||
            !command.arrivalRadiusMeters.isFinite() || command.arrivalRadiusMeters <= 0.0
        ) return rejectLocked(ActiveNavigationIssue.INVALID_COMMAND)
        val origin = currentFix.position?.takeIf { currentFix.usablePosition }
            ?: return rejectLocked(ActiveNavigationIssue.INPUT_UNAVAILABLE)
        if (session?.state != null && session?.state != NavigationSessionState.COMPLETE) {
            return requestReplacementLocked(
                NavigationStartProposal.DirectTo(
                    destination = command.destination,
                    destinationId = command.destinationId,
                    displayName = command.destinationName,
                    arrivalRadiusMeters = command.arrivalRadiusMeters,
                ),
            )
        }
        return startDirectToLocked(
            origin = origin,
            destination = command.destination,
            destinationId = command.destinationId,
            destinationName = command.destinationName,
            arrivalRadiusMeters = command.arrivalRadiusMeters,
        )
    }

    private suspend fun startDirectToLocked(
        origin: NavigationPosition,
        destination: NavigationPosition,
        destinationId: String,
        destinationName: String,
        arrivalRadiusMeters: Double,
    ): ActiveNavigationCommandResult {
        val startedAt = clock.wallTimeMillis()
        val routeId = "direct-to-$startedAt-${destinationId.hashCode().toUInt()}"
        val embedded = RoutePlan(
            id = routeId,
            revision = 1L,
            name = destinationName,
            points = listOf(
                RoutePoint("direct-to-origin", origin),
                RoutePoint(destinationId, destination),
            ),
        )
        val next = ActiveNavigationSession(
            routeId = routeId,
            routeRevision = embedded.revision,
            startedAtEpochMillis = startedAt,
            activeLegIndex = 0,
            arrivalRadiusMeters = arrivalRadiusMeters,
            advancePolicy = NavigationAdvancePolicy.MANUAL,
            state = NavigationSessionState.ACTIVE,
            embeddedRoute = embedded,
        )
        if (!persistLocked(next)) return rejectLocked(ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED)
        route = embedded
        session = next
        issue = null
        pendingReplacement = null
        publishLocked()
        return ActiveNavigationCommandResult.Accepted(revision)
    }

    private suspend fun confirmReplacementLocked(): ActiveNavigationCommandResult {
        return when (val proposal = pendingReplacement) {
            null -> rejectLocked(ActiveNavigationIssue.INVALID_COMMAND)
            is NavigationStartProposal.SavedRoute -> {
                val loaded = routes.route(proposal.routeId) ?: return rejectLocked(ActiveNavigationIssue.ROUTE_NOT_FOUND)
                if (loaded.revision != proposal.routeRevision) return rejectLocked(ActiveNavigationIssue.ROUTE_REVISION_CHANGED)
                if (loaded.points.size < 2) return rejectLocked(ActiveNavigationIssue.ROUTE_TOO_SHORT)
                startRouteLocked(loaded, proposal.arrivalRadiusMeters, proposal.advancePolicy)
            }
            is NavigationStartProposal.DirectTo -> {
                val origin = currentFix.position?.takeIf { currentFix.usablePosition }
                    ?: return rejectLocked(ActiveNavigationIssue.INPUT_UNAVAILABLE)
                startDirectToLocked(
                    origin = origin,
                    destination = proposal.destination,
                    destinationId = proposal.destinationId,
                    destinationName = proposal.displayName,
                    arrivalRadiusMeters = proposal.arrivalRadiusMeters,
                )
            }
        }
    }

    private fun cancelReplacementLocked(): ActiveNavigationCommandResult {
        if (pendingReplacement == null) return ActiveNavigationCommandResult.Accepted(revision)
        pendingReplacement = null
        issue = null
        publishLocked()
        return ActiveNavigationCommandResult.Accepted(revision)
    }

    private fun requiresReplacementLocked(routeId: String, routeRevision: Long): Boolean {
        val current = session ?: return false
        if (current.state == NavigationSessionState.COMPLETE) return false
        return current.routeId != routeId || current.routeRevision != routeRevision
    }

    private fun requestReplacementLocked(proposal: NavigationStartProposal): ActiveNavigationCommandResult {
        pendingReplacement = proposal
        issue = null
        publishLocked()
        return ActiveNavigationCommandResult.ReplacementRequired(
            revision = revision,
            activeDisplayName = route?.name ?: requireNotNull(session).routeId,
            proposedDisplayName = proposal.displayName,
        )
    }

    private suspend fun changeStateLocked(target: NavigationSessionState): ActiveNavigationCommandResult {
        val current = session ?: return rejectLocked(ActiveNavigationIssue.NOT_ACTIVE)
        if (current.state == NavigationSessionState.COMPLETE) return rejectLocked(ActiveNavigationIssue.NOT_ACTIVE)
        if (current.state == target) return ActiveNavigationCommandResult.Accepted(revision)
        val next = current.copy(state = target)
        if (!persistLocked(next)) return rejectLocked(ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED)
        session = next
        issue = null
        publishLocked()
        return ActiveNavigationCommandResult.Accepted(revision)
    }

    private suspend fun stopLocked(): ActiveNavigationCommandResult {
        if (session == null) return ActiveNavigationCommandResult.Accepted(revision)
        if (sessionStore.saveActiveNavigationSession(null) is NavigationSessionSaveResult.Failed) {
            return rejectLocked(ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED)
        }
        session = null
        route = null
        issue = null
        pendingReplacement = null
        publishLocked()
        return ActiveNavigationCommandResult.Accepted(revision)
    }

    private suspend fun moveLegLocked(delta: Int): ActiveNavigationCommandResult {
        val current = session ?: return rejectLocked(ActiveNavigationIssue.NOT_ACTIVE)
        val activeRoute = route ?: return rejectLocked(ActiveNavigationIssue.ROUTE_NOT_FOUND)
        val target = current.activeLegIndex + delta
        val next = when {
            target < 0 -> return rejectLocked(ActiveNavigationIssue.NOT_ACTIVE)
            target >= activeRoute.points.lastIndex -> current.copy(state = NavigationSessionState.COMPLETE)
            else -> current.copy(
                activeLegIndex = target,
                state = if (current.state == NavigationSessionState.COMPLETE) NavigationSessionState.ACTIVE else current.state,
            )
        }
        if (!persistLocked(next)) return rejectLocked(ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED)
        session = next
        issue = null
        publishLocked()
        return ActiveNavigationCommandResult.Accepted(revision)
    }

    private suspend fun reconcileInputLocked() {
        val current = session
        val activeRoute = route
        if (current == null || activeRoute == null) {
            publishLocked()
            return
        }
        var nextSession = current
        var solution = ActiveNavigationSolver.solve(activeRoute, nextSession, currentFix)
        if (
            solution != null && nextSession.advancePolicy == NavigationAdvancePolicy.ARRIVAL_RADIUS &&
            solution.distanceToWaypointNauticalMiles * 1852.0 <= nextSession.arrivalRadiusMeters
        ) {
            nextSession = if (nextSession.activeLegIndex >= activeRoute.points.lastIndex - 1) {
                nextSession.copy(state = NavigationSessionState.COMPLETE)
            } else {
                nextSession.copy(activeLegIndex = nextSession.activeLegIndex + 1)
            }
            if (persistLocked(nextSession)) {
                session = nextSession
                solution = ActiveNavigationSolver.solve(activeRoute, nextSession, currentFix)
            } else {
                issue = ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED
            }
        }
        issue = when {
            issue == ActiveNavigationIssue.SESSION_PERSISTENCE_FAILED -> issue
            nextSession.state == NavigationSessionState.ACTIVE && solution == null -> ActiveNavigationIssue.INPUT_UNAVAILABLE
            else -> null
        }
        publishLocked(solution)
    }

    private suspend fun persistLocked(value: ActiveNavigationSession): Boolean =
        sessionStore.saveActiveNavigationSession(value) == NavigationSessionSaveResult.Saved

    private fun rejectLocked(value: ActiveNavigationIssue): ActiveNavigationCommandResult.Rejected {
        issue = value
        publishLocked()
        return ActiveNavigationCommandResult.Rejected(value)
    }

    private fun publishLocked(solutionOverride: NavigationSolution? = null) {
        revision += 1L
        val current = session
        val solution = solutionOverride ?: if (current?.state == NavigationSessionState.ACTIVE && route != null) {
            ActiveNavigationSolver.solve(requireNotNull(route), current, currentFix)
        } else null
        if (current?.state == NavigationSessionState.ACTIVE && solution == null && issue == null) {
            issue = ActiveNavigationIssue.INPUT_UNAVAILABLE
        }
        mutableState.value = ActiveNavigationSnapshot(
            revision = revision,
            session = current,
            route = route,
            fix = currentFix,
            solution = solution,
            issue = issue,
            pendingReplacement = pendingReplacement,
        )
    }

}

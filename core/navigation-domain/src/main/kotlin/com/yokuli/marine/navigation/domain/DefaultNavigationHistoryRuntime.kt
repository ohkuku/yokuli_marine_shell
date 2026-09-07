package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultNavigationHistoryRuntime(
    private val store: NavigationHistoryStore,
    private val maxPassages: Int = 1_000,
) : NavigationHistoryRuntimePort {
    init { require(maxPassages > 0) }

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(NavigationHistorySnapshot.EMPTY)
    override val state: StateFlow<NavigationHistorySnapshot> = mutableState.asStateFlow()
    private var initialized = false

    override suspend fun initialize(): NavigationHistorySnapshot = mutex.withLock {
        initializeLocked()
        mutableState.value
    }

    override suspend fun begin(
        session: ActiveNavigationSession,
        route: RoutePlan,
    ): NavigationHistoryWriteResult = mutex.withLock {
        initializeLocked()
        val current = mutableState.value
        val existing = current.passages.firstOrNull { it.sessionId == session.sessionId }
        if (existing != null) {
            if (
                existing.routeId == route.id && existing.routeRevision == route.revision &&
                existing.startedAtEpochMillis == session.startedAtEpochMillis
            ) {
                val checkpoint = session.historyCheckpoint(route)
                if (existing.outcome == NavigationPassageOutcome.ACTIVE &&
                    checkpoint > existing.furthestAdvancedWaypointIndex
                ) {
                    val reconciled = existing.copy(
                        revision = existing.revision + 1L,
                        furthestAdvancedWaypointIndex = checkpoint,
                    )
                    return@withLock saveLocked(
                        current.copy(
                            revision = current.revision + 1L,
                            passages = current.passages.map { if (it.sessionId == existing.sessionId) reconciled else it },
                        ),
                    )
                }
                return@withLock NavigationHistoryWriteResult.Saved(current.revision)
            }
            return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        val passage = NavigationPassage(
            sessionId = session.sessionId,
            revision = 1L,
            routeId = route.id,
            routeRevision = route.revision,
            routeName = route.name,
            startedAtEpochMillis = session.startedAtEpochMillis,
            waypoints = route.points.mapIndexed { index, point ->
                NavigationPassageWaypoint(index, point.id, point.position)
            },
            furthestAdvancedWaypointIndex = session.historyCheckpoint(route),
        )
        val retained = if (current.passages.size < maxPassages) current.passages else {
            val removable = current.passages.firstOrNull { it.outcome != NavigationPassageOutcome.ACTIVE }
                ?: return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CAPACITY_REACHED)
            current.passages.filterNot { it.sessionId == removable.sessionId }
        }
        saveLocked(current.copy(revision = current.revision + 1L, passages = retained + passage))
    }

    override suspend fun recordAdvance(
        session: ActiveNavigationSession,
        route: RoutePlan,
        waypointIndex: Int,
        eventAtEpochMillis: Long,
        actualPosition: NavigationPosition?,
        positionSourceId: String?,
        reason: NavigationAdvanceReason,
        terminalOutcome: NavigationPassageOutcome?,
    ): NavigationHistoryWriteResult = mutex.withLock {
        initializeLocked()
        if (terminalOutcome != null && terminalOutcome != NavigationPassageOutcome.COMPLETED) {
            return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        if ((actualPosition == null) != (positionSourceId == null)) {
            return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        var current = mutableState.value
        if (current.passages.none { it.sessionId == session.sessionId }) {
            val begun = beginWithoutLock(session, route)
            if (begun is NavigationHistoryWriteResult.Failed) return@withLock begun
            current = mutableState.value
        }
        val existing = current.passages.first { it.sessionId == session.sessionId }
        if (existing.outcome != NavigationPassageOutcome.ACTIVE || waypointIndex !in existing.waypoints.indices) {
            return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        if (eventAtEpochMillis < existing.startedAtEpochMillis) {
            return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        val evidence = if (actualPosition != null) {
            NavigationPassageEvidence.POSITION_RECORDED
        } else {
            NavigationPassageEvidence.NO_POSITION_EVIDENCE
        }
        val event = NavigationWaypointPassageEvent(
            occurredAtEpochMillis = eventAtEpochMillis,
            reason = reason,
            evidence = evidence,
            actualPosition = actualPosition,
            positionSourceId = positionSourceId,
        )
        val oldWaypoint = existing.waypoints[waypointIndex]
        if (oldWaypoint.event != null) {
            return@withLock NavigationHistoryWriteResult.Saved(current.revision)
        }
        val nextPassage = existing.copy(
            revision = existing.revision + 1L,
            endedAtEpochMillis = eventAtEpochMillis.takeIf { terminalOutcome != null },
            outcome = terminalOutcome ?: NavigationPassageOutcome.ACTIVE,
            waypoints = existing.waypoints.mapIndexed { index, waypoint ->
                if (index == waypointIndex) waypoint.copy(event = event) else waypoint
            },
            furthestAdvancedWaypointIndex = maxOf(existing.furthestAdvancedWaypointIndex, waypointIndex),
        )
        saveLocked(
            current.copy(
                revision = current.revision + 1L,
                passages = current.passages.map { if (it.sessionId == nextPassage.sessionId) nextPassage else it },
            ),
        )
    }

    override suspend fun finish(
        sessionId: String,
        outcome: NavigationPassageOutcome,
        endedAtEpochMillis: Long,
    ): NavigationHistoryWriteResult = mutex.withLock {
        initializeLocked()
        if (sessionId.isBlank() || outcome == NavigationPassageOutcome.ACTIVE) {
            return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        val current = mutableState.value
        val existing = current.passages.firstOrNull { it.sessionId == sessionId }
            ?: return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.NOT_FOUND)
        if (endedAtEpochMillis < existing.startedAtEpochMillis) {
            return@withLock NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        if (existing.outcome != NavigationPassageOutcome.ACTIVE) {
            return@withLock if (existing.outcome == outcome && existing.endedAtEpochMillis == endedAtEpochMillis) {
                NavigationHistoryWriteResult.Saved(current.revision)
            } else NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CONFLICT)
        }
        val nextPassage = existing.copy(
            revision = existing.revision + 1L,
            endedAtEpochMillis = endedAtEpochMillis,
            outcome = outcome,
        )
        saveLocked(
            current.copy(
                revision = current.revision + 1L,
                passages = current.passages.map { if (it.sessionId == sessionId) nextPassage else it },
            ),
        )
    }

    override suspend fun reconcileActiveSession(
        activeSessionId: String?,
        observedAtEpochMillis: Long,
    ): NavigationHistoryWriteResult = mutex.withLock {
        initializeLocked()
        val current = mutableState.value
        val orphaned = current.passages.filter {
            it.outcome == NavigationPassageOutcome.ACTIVE && it.sessionId != activeSessionId
        }
        if (orphaned.isEmpty()) return@withLock NavigationHistoryWriteResult.Saved(current.revision)
        val orphanIds = orphaned.mapTo(hashSetOf(), NavigationPassage::sessionId)
        saveLocked(
            current.copy(
                revision = current.revision + 1L,
                passages = current.passages.map { passage ->
                    if (passage.sessionId !in orphanIds) passage else passage.copy(
                        revision = passage.revision + 1L,
                        endedAtEpochMillis = maxOf(observedAtEpochMillis, passage.startedAtEpochMillis),
                        outcome = NavigationPassageOutcome.INTERRUPTED,
                    )
                },
            ),
        )
    }

    private suspend fun beginWithoutLock(
        session: ActiveNavigationSession,
        route: RoutePlan,
    ): NavigationHistoryWriteResult {
        val current = mutableState.value
        val passage = NavigationPassage(
            sessionId = session.sessionId,
            revision = 1L,
            routeId = route.id,
            routeRevision = route.revision,
            routeName = route.name,
            startedAtEpochMillis = session.startedAtEpochMillis,
            waypoints = route.points.mapIndexed { index, point ->
                NavigationPassageWaypoint(index, point.id, point.position)
            },
            furthestAdvancedWaypointIndex = session.historyCheckpoint(route),
        )
        val retained = if (current.passages.size < maxPassages) current.passages else {
            val removable = current.passages.firstOrNull { it.outcome != NavigationPassageOutcome.ACTIVE }
                ?: return NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.CAPACITY_REACHED)
            current.passages.filterNot { it.sessionId == removable.sessionId }
        }
        return saveLocked(current.copy(revision = current.revision + 1L, passages = retained + passage))
    }

    private suspend fun initializeLocked() {
        if (initialized) return
        mutableState.value = when (val loaded = store.loadNavigationHistory()) {
            is NavigationHistoryLoadResult.Loaded -> loaded.snapshot
            is NavigationHistoryLoadResult.Failed -> NavigationHistorySnapshot.EMPTY
        }
        initialized = true
    }

    private suspend fun saveLocked(next: NavigationHistorySnapshot): NavigationHistoryWriteResult {
        return if (store.saveNavigationHistory(next) == NavigationHistorySaveResult.Saved) {
            mutableState.value = next
            NavigationHistoryWriteResult.Saved(next.revision)
        } else {
            NavigationHistoryWriteResult.Failed(NavigationHistoryIssue.PERSISTENCE_FAILED)
        }
    }
}

private fun ActiveNavigationSession.historyCheckpoint(route: RoutePlan): Int =
    if (state == NavigationSessionState.COMPLETE) route.points.lastIndex else activeLegIndex

object NoOpNavigationHistoryRuntime : NavigationHistoryRuntimePort {
    override val state: StateFlow<NavigationHistorySnapshot> = MutableStateFlow(NavigationHistorySnapshot.EMPTY)
    override suspend fun initialize() = state.value
    override suspend fun begin(session: ActiveNavigationSession, route: RoutePlan) =
        NavigationHistoryWriteResult.Saved(state.value.revision)
    override suspend fun recordAdvance(
        session: ActiveNavigationSession,
        route: RoutePlan,
        waypointIndex: Int,
        eventAtEpochMillis: Long,
        actualPosition: NavigationPosition?,
        positionSourceId: String?,
        reason: NavigationAdvanceReason,
        terminalOutcome: NavigationPassageOutcome?,
    ) = NavigationHistoryWriteResult.Saved(state.value.revision)
    override suspend fun finish(sessionId: String, outcome: NavigationPassageOutcome, endedAtEpochMillis: Long) =
        NavigationHistoryWriteResult.Saved(state.value.revision)
    override suspend fun reconcileActiveSession(activeSessionId: String?, observedAtEpochMillis: Long) =
        NavigationHistoryWriteResult.Saved(state.value.revision)
}

package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.flow.StateFlow

enum class NavigationPassageOutcome { ACTIVE, COMPLETED, STOPPED, REPLACED, INTERRUPTED }
enum class NavigationAdvanceReason { ARRIVAL_RADIUS, MANUAL }
enum class NavigationPassageEvidence { POSITION_RECORDED, NO_POSITION_EVIDENCE }

data class NavigationWaypointPassageEvent(
    val occurredAtEpochMillis: Long,
    val reason: NavigationAdvanceReason,
    val evidence: NavigationPassageEvidence,
    val actualPosition: NavigationPosition? = null,
    val positionSourceId: String? = null,
) {
    init {
        require(occurredAtEpochMillis >= 0L)
        require((actualPosition == null) == (positionSourceId == null))
        require(positionSourceId == null || positionSourceId.isNotBlank())
        require((evidence == NavigationPassageEvidence.POSITION_RECORDED) == (actualPosition != null))
    }
}

data class NavigationPassageWaypoint(
    val ordinal: Int,
    val routePointId: String,
    val plannedPosition: NavigationPosition,
    val event: NavigationWaypointPassageEvent? = null,
) {
    init { require(ordinal >= 0 && routePointId.isNotBlank()) }
}

data class NavigationPassage(
    val sessionId: String,
    val revision: Long,
    val routeId: String,
    val routeRevision: Long,
    val routeName: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val outcome: NavigationPassageOutcome = NavigationPassageOutcome.ACTIVE,
    val waypoints: List<NavigationPassageWaypoint>,
    /** Route checkpoint truth; a missing event means timing/position evidence was not recovered. */
    val furthestAdvancedWaypointIndex: Int = 0,
) {
    init {
        require(sessionId.isNotBlank() && revision > 0L)
        require(routeId.isNotBlank() && routeRevision > 0L && routeName.isNotBlank())
        require(startedAtEpochMillis >= 0L)
        require((outcome == NavigationPassageOutcome.ACTIVE) == (endedAtEpochMillis == null))
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis)
        require(waypoints.size >= 2)
        require(waypoints.map(NavigationPassageWaypoint::ordinal) == waypoints.indices.toList())
        require(waypoints.map(NavigationPassageWaypoint::routePointId).distinct().size == waypoints.size)
        require(furthestAdvancedWaypointIndex in waypoints.indices)
    }
}

data class NavigationHistorySnapshot(
    val revision: Long = 0L,
    val passages: List<NavigationPassage> = emptyList(),
) {
    init {
        require(revision >= 0L)
        require(passages.map(NavigationPassage::sessionId).distinct().size == passages.size)
    }

    companion object { val EMPTY = NavigationHistorySnapshot() }
}

enum class NavigationHistoryStoreFailure { IO, CORRUPT, FUTURE_SCHEMA, UNKNOWN }

sealed interface NavigationHistoryLoadResult {
    data class Loaded(val snapshot: NavigationHistorySnapshot) : NavigationHistoryLoadResult
    data class Failed(val failure: NavigationHistoryStoreFailure) : NavigationHistoryLoadResult
}

sealed interface NavigationHistorySaveResult {
    data object Saved : NavigationHistorySaveResult
    data class Failed(val failure: NavigationHistoryStoreFailure) : NavigationHistorySaveResult
}

interface NavigationHistoryStore {
    suspend fun loadNavigationHistory(): NavigationHistoryLoadResult
    suspend fun saveNavigationHistory(snapshot: NavigationHistorySnapshot): NavigationHistorySaveResult
}

enum class NavigationHistoryIssue { NOT_FOUND, CONFLICT, CAPACITY_REACHED, PERSISTENCE_FAILED }

sealed interface NavigationHistoryWriteResult {
    data class Saved(val revision: Long) : NavigationHistoryWriteResult
    data class Failed(val issue: NavigationHistoryIssue) : NavigationHistoryWriteResult
}

interface NavigationHistoryRuntimePort {
    val state: StateFlow<NavigationHistorySnapshot>
    suspend fun initialize(): NavigationHistorySnapshot
    suspend fun begin(session: ActiveNavigationSession, route: RoutePlan): NavigationHistoryWriteResult
    suspend fun recordAdvance(
        session: ActiveNavigationSession,
        route: RoutePlan,
        waypointIndex: Int,
        eventAtEpochMillis: Long,
        actualPosition: NavigationPosition?,
        positionSourceId: String?,
        reason: NavigationAdvanceReason,
        terminalOutcome: NavigationPassageOutcome?,
    ): NavigationHistoryWriteResult
    suspend fun finish(
        sessionId: String,
        outcome: NavigationPassageOutcome,
        endedAtEpochMillis: Long,
    ): NavigationHistoryWriteResult
    suspend fun reconcileActiveSession(
        activeSessionId: String?,
        observedAtEpochMillis: Long,
    ): NavigationHistoryWriteResult
}

package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.flow.StateFlow

enum class NavigationSessionState { ACTIVE, PAUSED, COMPLETE, STOPPED }
enum class NavigationAdvancePolicy { MANUAL, ARRIVAL_RADIUS }

data class ActiveNavigationSession(
    val routeId: String,
    val routeRevision: Long,
    val startedAtEpochMillis: Long,
    val activeLegIndex: Int,
    val arrivalRadiusMeters: Double,
    val advancePolicy: NavigationAdvancePolicy,
    val state: NavigationSessionState,
    /**
     * A route owned by this UI-independent navigation session rather than the durable route library.
     * Direct-To uses this so it can survive process recreation without inventing a saved route.
     */
    val embeddedRoute: RoutePlan? = null,
) {
    init {
        require(routeId.isNotBlank() && routeRevision > 0L)
        require(startedAtEpochMillis >= 0L && activeLegIndex >= 0)
        require(arrivalRadiusMeters.isFinite() && arrivalRadiusMeters > 0.0)
        require(state != NavigationSessionState.STOPPED) { "Stopped navigation is represented by no stored session" }
        require(embeddedRoute == null || embeddedRoute.id == routeId && embeddedRoute.revision == routeRevision)
    }

    /** Stable identity for this passage attempt; restarting the same route creates a different session. */
    val sessionId: String
        get() = "$routeId:$routeRevision:$startedAtEpochMillis"
}

enum class NavigationInputStatus { LIVE, HELD, STALE, INVALID, UNAVAILABLE }

data class NavigationFix(
    val revision: Long = 0L,
    val position: NavigationPosition? = null,
    val positionStatus: NavigationInputStatus = NavigationInputStatus.UNAVAILABLE,
    val positionSourceId: String? = null,
    val motionSourceId: String? = null,
    val receivedAtMonotonicMillis: Long? = null,
    val ageMillis: Long? = null,
    val speedOverGroundKnots: Double? = null,
    val courseOverGroundTrueDegrees: Double? = null,
) {
    init {
        require(revision >= 0L)
        require(positionStatus !in setOf(NavigationInputStatus.LIVE, NavigationInputStatus.HELD) || position != null)
        require(!usablePosition || positionSourceId != null && receivedAtMonotonicMillis != null)
        require(positionSourceId == null || positionSourceId.isNotBlank())
        require(motionSourceId == null || motionSourceId.isNotBlank())
        require(receivedAtMonotonicMillis == null || receivedAtMonotonicMillis >= 0L)
        require(ageMillis == null || ageMillis >= 0L)
        require(speedOverGroundKnots == null || speedOverGroundKnots.isFinite() && speedOverGroundKnots >= 0.0)
        require(speedOverGroundKnots == null && courseOverGroundTrueDegrees == null || motionSourceId != null)
        require(
            courseOverGroundTrueDegrees == null ||
                courseOverGroundTrueDegrees.isFinite() && courseOverGroundTrueDegrees in 0.0..<360.0,
        )
    }

    val usablePosition: Boolean
        get() = position != null && positionStatus in setOf(NavigationInputStatus.LIVE, NavigationInputStatus.HELD)
}

fun interface NavigationRouteReadPort {
    suspend fun route(routeId: String): RoutePlan?
}

interface NavigationInputPort {
    val state: StateFlow<NavigationFix>
}

enum class NavigationSessionStoreFailure { IO, CORRUPT, FUTURE_SCHEMA, UNKNOWN }

sealed interface NavigationSessionLoadResult {
    data object Empty : NavigationSessionLoadResult
    data class Loaded(val session: ActiveNavigationSession) : NavigationSessionLoadResult
    data class Failed(val failure: NavigationSessionStoreFailure) : NavigationSessionLoadResult
}

sealed interface NavigationSessionSaveResult {
    data object Saved : NavigationSessionSaveResult
    data class Failed(val failure: NavigationSessionStoreFailure) : NavigationSessionSaveResult
}

interface ActiveNavigationSessionStore {
    suspend fun loadActiveNavigationSession(): NavigationSessionLoadResult
    suspend fun saveActiveNavigationSession(session: ActiveNavigationSession?): NavigationSessionSaveResult
}

data class NavigationSolution(
    val routeId: String,
    val routeRevision: Long,
    val activeLegIndex: Int,
    val previousWaypointId: String,
    val nextWaypointId: String,
    val inputStatus: NavigationInputStatus,
    val distanceToWaypointNauticalMiles: Double,
    val bearingToWaypointTrueDegrees: Double?,
    /** Signed cross-track error: positive is starboard/right of the intended leg. */
    val crossTrackErrorNauticalMiles: Double,
    val estimatedTimeToWaypointMillis: Long?,
    val legProgress: Double,
    val routeProgress: Double,
    val positionSourceId: String,
    val motionSourceId: String?,
    val receivedAtMonotonicMillis: Long,
) {
    init {
        require(routeId.isNotBlank() && routeRevision > 0L && activeLegIndex >= 0)
        require(previousWaypointId.isNotBlank() && nextWaypointId.isNotBlank())
        require(inputStatus in setOf(NavigationInputStatus.LIVE, NavigationInputStatus.HELD))
        require(distanceToWaypointNauticalMiles.isFinite() && distanceToWaypointNauticalMiles >= 0.0)
        require(bearingToWaypointTrueDegrees == null || bearingToWaypointTrueDegrees in 0.0..<360.0)
        require(crossTrackErrorNauticalMiles.isFinite())
        require(estimatedTimeToWaypointMillis == null || estimatedTimeToWaypointMillis >= 0L)
        require(legProgress in 0.0..1.0 && routeProgress in 0.0..1.0)
        require(positionSourceId.isNotBlank() && receivedAtMonotonicMillis >= 0L)
    }
}

enum class ActiveNavigationIssue {
    ROUTE_NOT_FOUND,
    ROUTE_REVISION_CHANGED,
    ROUTE_TOO_SHORT,
    INPUT_UNAVAILABLE,
    SESSION_PERSISTENCE_FAILED,
    NOT_ACTIVE,
    INVALID_COMMAND,
}

data class ActiveNavigationSnapshot(
    val revision: Long = 0L,
    val session: ActiveNavigationSession? = null,
    val route: RoutePlan? = null,
    val fix: NavigationFix = NavigationFix(),
    val solution: NavigationSolution? = null,
    val issue: ActiveNavigationIssue? = null,
    /** Ephemeral, process-local user decision. The active session remains authoritative until confirmed. */
    val pendingReplacement: NavigationStartProposal? = null,
) {
    init {
        require(revision >= 0L)
        require((session == null) == (route == null))
        require(session == null || route?.id == session.routeId && route.revision == session.routeRevision)
        require(solution == null || session?.state == NavigationSessionState.ACTIVE)
    }

    val sessionState: NavigationSessionState get() = session?.state ?: NavigationSessionState.STOPPED

    companion object { val EMPTY = ActiveNavigationSnapshot() }
}

sealed interface NavigationStartProposal {
    val displayName: String

    data class SavedRoute(
        val routeId: String,
        val routeRevision: Long,
        override val displayName: String,
        val arrivalRadiusMeters: Double,
        val advancePolicy: NavigationAdvancePolicy,
    ) : NavigationStartProposal

    data class DirectTo(
        val destination: NavigationPosition,
        val destinationId: String,
        override val displayName: String,
        val arrivalRadiusMeters: Double,
    ) : NavigationStartProposal
}

sealed interface ActiveNavigationCommand {
    data class Start(
        val routeId: String,
        val routeRevision: Long,
        val arrivalRadiusMeters: Double = 50.0,
        val advancePolicy: NavigationAdvancePolicy = NavigationAdvancePolicy.MANUAL,
    ) : ActiveNavigationCommand
    data class DirectTo(
        val destination: NavigationPosition,
        val destinationId: String,
        val destinationName: String,
        val arrivalRadiusMeters: Double = 50.0,
    ) : ActiveNavigationCommand
    data object Pause : ActiveNavigationCommand
    data object Resume : ActiveNavigationCommand
    data object Stop : ActiveNavigationCommand
    data object NextWaypoint : ActiveNavigationCommand
    data object PreviousWaypoint : ActiveNavigationCommand
    /** Applies the exact proposal currently exposed by [ActiveNavigationSnapshot.pendingReplacement]. */
    data object ConfirmReplacement : ActiveNavigationCommand
    data object CancelReplacement : ActiveNavigationCommand
}

sealed interface ActiveNavigationCommandResult {
    data class Accepted(val revision: Long) : ActiveNavigationCommandResult
    data class ReplacementRequired(
        val revision: Long,
        val activeDisplayName: String,
        val proposedDisplayName: String,
    ) : ActiveNavigationCommandResult
    data class Rejected(val issue: ActiveNavigationIssue) : ActiveNavigationCommandResult
}

fun interface NavigationRuntimeClock {
    fun wallTimeMillis(): Long
}

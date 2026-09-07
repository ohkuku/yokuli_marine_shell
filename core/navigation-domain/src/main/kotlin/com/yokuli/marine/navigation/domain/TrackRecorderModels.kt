package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.flow.StateFlow

enum class TrackRecorderStatus { IDLE, RECORDING, PAUSED, STOPPED_AWAITING_SAVE }

data class RecordedTrackPoint(
    val position: NavigationPosition,
    val recordedAtEpochMillis: Long,
    val sourceId: String,
    val speedOverGroundKnots: Double? = null,
    val courseOverGroundTrueDegrees: Double? = null,
) {
    init {
        require(recordedAtEpochMillis >= 0L)
        require(sourceId.isNotBlank())
        require(speedOverGroundKnots == null || speedOverGroundKnots.isFinite() && speedOverGroundKnots >= 0.0)
        require(courseOverGroundTrueDegrees == null || courseOverGroundTrueDegrees in 0.0..<360.0)
    }
}

data class RecordedTrackSegment(val points: List<RecordedTrackPoint>) {
    init { require(points.isNotEmpty()) }
}

data class TrackRecordingSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val status: TrackRecorderStatus,
    val segments: List<RecordedTrackSegment> = emptyList(),
    val startNewSegment: Boolean = true,
    val activeSinceEpochMillis: Long? = null,
    val accumulatedDurationMillis: Long = 0L,
    val stoppedAtEpochMillis: Long? = null,
    val navigationSessionId: String? = null,
    val routeId: String? = null,
    val routeRevision: Long? = null,
) {
    init {
        require(id.isNotBlank() && startedAtEpochMillis >= 0L)
        require(status != TrackRecorderStatus.IDLE)
        require(accumulatedDurationMillis >= 0L)
        require((routeId == null) == (routeRevision == null))
        require(routeRevision == null || routeRevision > 0L)
        require(navigationSessionId == null || navigationSessionId.isNotBlank())
        require((status == TrackRecorderStatus.RECORDING) == (activeSinceEpochMillis != null))
        require(activeSinceEpochMillis == null || activeSinceEpochMillis >= startedAtEpochMillis)
        require((status == TrackRecorderStatus.STOPPED_AWAITING_SAVE) == (stoppedAtEpochMillis != null))
        require(stoppedAtEpochMillis == null || stoppedAtEpochMillis >= startedAtEpochMillis)
    }
}

enum class TrackRecorderIssue {
    NOT_RECORDING,
    ALREADY_RUNNING,
    EMPTY_TRACK,
    CAPACITY_REACHED,
    PERSISTENCE_FAILED,
    ARCHIVE_FAILED,
}

data class TrackRecorderSnapshot(
    val revision: Long = 0L,
    val session: TrackRecordingSession? = null,
    val distanceNauticalMiles: Double = 0.0,
    val durationMillis: Long = 0L,
    val issue: TrackRecorderIssue? = null,
    val lastSavedTrackId: String? = null,
) {
    init {
        require(revision >= 0L)
        require(distanceNauticalMiles.isFinite() && distanceNauticalMiles >= 0.0)
        require(durationMillis >= 0L)
    }

    val status: TrackRecorderStatus get() = session?.status ?: TrackRecorderStatus.IDLE
    val pointCount: Int get() = session?.segments?.sumOf { it.points.size } ?: 0
    val liveTail: List<NavigationPosition>
        get() = session?.segments?.flatMap { segment -> segment.points.map(RecordedTrackPoint::position) }.orEmpty()

    companion object { val EMPTY = TrackRecorderSnapshot() }
}

sealed interface TrackRecorderCommand {
    data object Start : TrackRecorderCommand
    data object Pause : TrackRecorderCommand
    data object Resume : TrackRecorderCommand
    data object Stop : TrackRecorderCommand
    data class Save(val name: String = "") : TrackRecorderCommand
    data object Discard : TrackRecorderCommand
}

sealed interface TrackRecorderCommandResult {
    data class Accepted(val revision: Long) : TrackRecorderCommandResult
    data class Rejected(val issue: TrackRecorderIssue) : TrackRecorderCommandResult
}

enum class TrackRecordingStoreFailure { IO, CORRUPT, FUTURE_SCHEMA, UNKNOWN }

sealed interface TrackRecordingLoadResult {
    data object Empty : TrackRecordingLoadResult
    data class Loaded(val session: TrackRecordingSession) : TrackRecordingLoadResult
    data class Failed(val failure: TrackRecordingStoreFailure) : TrackRecordingLoadResult
}

sealed interface TrackRecordingSaveResult {
    data object Saved : TrackRecordingSaveResult
    data class Failed(val failure: TrackRecordingStoreFailure) : TrackRecordingSaveResult
}

interface TrackRecordingStore {
    suspend fun loadTrackRecording(): TrackRecordingLoadResult
    suspend fun saveTrackRecording(session: TrackRecordingSession?): TrackRecordingSaveResult
}

interface TrackRecorderRuntimePort {
    val state: StateFlow<TrackRecorderSnapshot>
    suspend fun initialize(): TrackRecorderSnapshot
    suspend fun execute(command: TrackRecorderCommand): TrackRecorderCommandResult
}

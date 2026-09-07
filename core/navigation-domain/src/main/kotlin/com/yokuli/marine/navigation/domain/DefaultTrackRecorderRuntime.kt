package com.yokuli.marine.navigation.domain

import java.io.Closeable
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sf.geographiclib.Geodesic

class DefaultTrackRecorderRuntime(
    private val input: NavigationInputPort,
    private val activeNavigation: StateFlow<ActiveNavigationSnapshot>,
    private val recordingStore: TrackRecordingStore,
    private val library: NavigationLibraryPort,
    private val clock: NavigationRuntimeClock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val maxPoints: Int = 200_000,
    scope: CoroutineScope,
) : TrackRecorderRuntimePort, Closeable {
    init { require(maxPoints > 0) }

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(TrackRecorderSnapshot.EMPTY)
    override val state: StateFlow<TrackRecorderSnapshot> = mutableState.asStateFlow()
    private var initialized = false
    private var revision = 0L
    private var session: TrackRecordingSession? = null
    private var issue: TrackRecorderIssue? = null
    private var lastFixRevision = input.state.value.revision
    private var lastSavedTrackId: String? = null
    private val inputJob: Job = scope.launch {
        input.state.collect { fix ->
            mutex.withLock {
                if (!initialized || fix.revision <= lastFixRevision) return@withLock
                lastFixRevision = fix.revision
                appendFixLocked(fix)
            }
        }
    }

    override suspend fun initialize(): TrackRecorderSnapshot = mutex.withLock {
        if (!initialized) {
            restoreLocked()
        }
        mutableState.value
    }

    override suspend fun execute(command: TrackRecorderCommand): TrackRecorderCommandResult = mutex.withLock {
        if (!initialized) initializeLocked()
        when (command) {
            TrackRecorderCommand.Start -> startLocked()
            TrackRecorderCommand.Pause -> pauseLocked()
            TrackRecorderCommand.Resume -> resumeLocked()
            TrackRecorderCommand.Stop -> stopLocked()
            is TrackRecorderCommand.Save -> saveLocked(command.name)
            TrackRecorderCommand.Discard -> discardLocked()
        }
    }

    override fun close() { inputJob.cancel() }

    private suspend fun initializeLocked() {
        restoreLocked()
    }

    private suspend fun restoreLocked() {
        when (val loaded = recordingStore.loadTrackRecording()) {
            TrackRecordingLoadResult.Empty -> Unit
            is TrackRecordingLoadResult.Loaded -> {
                val stored = loaded.session
                session = if (stored.status == TrackRecorderStatus.RECORDING) {
                    stored.copy(
                        status = TrackRecorderStatus.PAUSED,
                        startNewSegment = true,
                        activeSinceEpochMillis = null,
                    ).also { safe ->
                        if (recordingStore.saveTrackRecording(safe) is TrackRecordingSaveResult.Failed) {
                            issue = TrackRecorderIssue.PERSISTENCE_FAILED
                        }
                    }
                } else stored
            }
            is TrackRecordingLoadResult.Failed -> issue = TrackRecorderIssue.PERSISTENCE_FAILED
        }
        lastFixRevision = input.state.value.revision
        initialized = true
        publishLocked()
    }

    private suspend fun startLocked(): TrackRecorderCommandResult {
        if (session != null) return rejectLocked(TrackRecorderIssue.ALREADY_RUNNING)
        val now = clock.wallTimeMillis()
        val active = activeNavigation.value.session
        val next = TrackRecordingSession(
            id = newId(),
            startedAtEpochMillis = now,
            status = TrackRecorderStatus.RECORDING,
            activeSinceEpochMillis = now,
            navigationSessionId = active?.sessionId,
            routeId = active?.routeId,
            routeRevision = active?.routeRevision,
        )
        val result = acceptSessionLocked(next)
        if (result is TrackRecorderCommandResult.Rejected) return result
        appendFixLocked(input.state.value)
        return TrackRecorderCommandResult.Accepted(revision)
    }

    private suspend fun pauseLocked(): TrackRecorderCommandResult {
        val current = session?.takeIf { it.status == TrackRecorderStatus.RECORDING }
            ?: return rejectLocked(TrackRecorderIssue.NOT_RECORDING)
        val now = clock.wallTimeMillis()
        return acceptSessionLocked(
            current.copy(
                status = TrackRecorderStatus.PAUSED,
                startNewSegment = true,
                accumulatedDurationMillis = current.accumulatedDurationMillis +
                    (now - requireNotNull(current.activeSinceEpochMillis)).coerceAtLeast(0L),
                activeSinceEpochMillis = null,
            ),
        )
    }

    private suspend fun resumeLocked(): TrackRecorderCommandResult {
        val current = session?.takeIf { it.status == TrackRecorderStatus.PAUSED }
            ?: return rejectLocked(TrackRecorderIssue.NOT_RECORDING)
        return acceptSessionLocked(
            current.copy(
                status = TrackRecorderStatus.RECORDING,
                startNewSegment = true,
                activeSinceEpochMillis = clock.wallTimeMillis(),
            ),
        )
    }

    private suspend fun stopLocked(): TrackRecorderCommandResult {
        val current = session ?: return rejectLocked(TrackRecorderIssue.NOT_RECORDING)
        if (current.status == TrackRecorderStatus.STOPPED_AWAITING_SAVE) {
            return TrackRecorderCommandResult.Accepted(revision)
        }
        val now = clock.wallTimeMillis()
        val duration = current.accumulatedDurationMillis + if (current.status == TrackRecorderStatus.RECORDING) {
            (now - requireNotNull(current.activeSinceEpochMillis)).coerceAtLeast(0L)
        } else 0L
        return acceptSessionLocked(
            current.copy(
                status = TrackRecorderStatus.STOPPED_AWAITING_SAVE,
                activeSinceEpochMillis = null,
                accumulatedDurationMillis = duration,
                stoppedAtEpochMillis = now,
            ),
        )
    }

    private suspend fun saveLocked(rawName: String): TrackRecorderCommandResult {
        val current = session?.takeIf { it.status == TrackRecorderStatus.STOPPED_AWAITING_SAVE }
            ?: return rejectLocked(TrackRecorderIssue.NOT_RECORDING)
        if (current.segments.isEmpty()) return rejectLocked(TrackRecorderIssue.EMPTY_TRACK)
        val loaded = library.loadNavigationLibrary()
        val snapshot = (loaded as? NavigationLibraryLoadResult.Ready)?.library
            ?: return rejectLocked(TrackRecorderIssue.ARCHIVE_FAILED)
        val track = current.toNavigationTrack(
            name = rawName.trim().ifEmpty { "Track %03d".format(snapshot.importedTracks.size + 1) },
            savedAtEpochMillis = clock.wallTimeMillis(),
        )
        val existing = snapshot.importedTracks.firstOrNull { it.id == track.id }
        if (existing != null && existing.sourceDigest == track.sourceDigest) {
            return clearSavedLocked(track.id)
        }
        val result = library.commitNavigationChange(snapshot.revision, NavigationLibraryChange.PutTrack(track))
        if (result !is NavigationLibraryCommitResult.Committed) return rejectLocked(TrackRecorderIssue.ARCHIVE_FAILED)
        return clearSavedLocked(track.id)
    }

    private suspend fun clearSavedLocked(trackId: String): TrackRecorderCommandResult {
        if (recordingStore.saveTrackRecording(null) is TrackRecordingSaveResult.Failed) {
            return rejectLocked(TrackRecorderIssue.PERSISTENCE_FAILED)
        }
        session = null
        issue = null
        lastSavedTrackId = trackId
        publishLocked()
        return TrackRecorderCommandResult.Accepted(revision)
    }

    private suspend fun discardLocked(): TrackRecorderCommandResult {
        if (session == null) return TrackRecorderCommandResult.Accepted(revision)
        if (recordingStore.saveTrackRecording(null) is TrackRecordingSaveResult.Failed) {
            return rejectLocked(TrackRecorderIssue.PERSISTENCE_FAILED)
        }
        session = null
        issue = null
        publishLocked()
        return TrackRecorderCommandResult.Accepted(revision)
    }

    private suspend fun appendFixLocked(fix: NavigationFix) {
        val current = session?.takeIf { it.status == TrackRecorderStatus.RECORDING } ?: return
        val position = fix.position?.takeIf { fix.usablePosition } ?: return
        if (current.segments.sumOf { it.points.size } >= maxPoints) {
            pauseForSafetyLocked(current, TrackRecorderIssue.CAPACITY_REACHED)
            return
        }
        val point = RecordedTrackPoint(
            position = position,
            recordedAtEpochMillis = clock.wallTimeMillis(),
            sourceId = requireNotNull(fix.positionSourceId),
            speedOverGroundKnots = fix.speedOverGroundKnots,
            courseOverGroundTrueDegrees = fix.courseOverGroundTrueDegrees,
        )
        val segments = if (current.startNewSegment || current.segments.isEmpty()) {
            current.segments + RecordedTrackSegment(listOf(point))
        } else {
            current.segments.dropLast(1) + current.segments.last().copy(
                points = current.segments.last().points + point,
            )
        }
        val next = current.copy(segments = segments, startNewSegment = false)
        if (recordingStore.saveTrackRecording(next) is TrackRecordingSaveResult.Failed) {
            pauseForSafetyLocked(current, TrackRecorderIssue.PERSISTENCE_FAILED)
            return
        }
        session = next
        issue = null
        publishLocked()
    }

    private suspend fun pauseForSafetyLocked(current: TrackRecordingSession, cause: TrackRecorderIssue) {
        val now = clock.wallTimeMillis()
        val safe = current.copy(
            status = TrackRecorderStatus.PAUSED,
            activeSinceEpochMillis = null,
            accumulatedDurationMillis = current.accumulatedDurationMillis +
                (now - requireNotNull(current.activeSinceEpochMillis)).coerceAtLeast(0L),
            startNewSegment = true,
        )
        val persisted = recordingStore.saveTrackRecording(safe)
        session = safe
        issue = if (persisted is TrackRecordingSaveResult.Failed) TrackRecorderIssue.PERSISTENCE_FAILED else cause
        publishLocked()
    }

    private suspend fun acceptSessionLocked(next: TrackRecordingSession): TrackRecorderCommandResult {
        if (recordingStore.saveTrackRecording(next) is TrackRecordingSaveResult.Failed) {
            return rejectLocked(TrackRecorderIssue.PERSISTENCE_FAILED)
        }
        session = next
        issue = null
        publishLocked()
        return TrackRecorderCommandResult.Accepted(revision)
    }

    private fun rejectLocked(value: TrackRecorderIssue): TrackRecorderCommandResult.Rejected {
        issue = value
        publishLocked()
        return TrackRecorderCommandResult.Rejected(value)
    }

    private fun publishLocked() {
        revision += 1L
        val current = session
        mutableState.value = TrackRecorderSnapshot(
            revision = revision,
            session = current,
            distanceNauticalMiles = current?.distanceNauticalMiles() ?: 0.0,
            durationMillis = current?.durationAt(clock.wallTimeMillis()) ?: 0L,
            issue = issue,
            lastSavedTrackId = lastSavedTrackId,
        )
    }

    private fun TrackRecordingSession.durationAt(now: Long): Long = accumulatedDurationMillis +
        if (status == TrackRecorderStatus.RECORDING) {
            (now - requireNotNull(activeSinceEpochMillis)).coerceAtLeast(0L)
        } else 0L

    private fun TrackRecordingSession.distanceNauticalMiles(): Double = segments.sumOf { segment ->
        segment.points.zipWithNext().sumOf { (from, to) ->
            Geodesic.WGS84.Inverse(
                from.position.latitude,
                from.position.longitude,
                to.position.latitude,
                to.position.longitude,
            ).s12 / METERS_PER_NAUTICAL_MILE
        }
    }

    private fun TrackRecordingSession.toNavigationTrack(name: String, savedAtEpochMillis: Long): NavigationTrack {
        val navigationSegments = segments.map { segment ->
            NavigationTrackSegment(
                segment.points.map { point ->
                    NavigationTrackPoint(
                        position = point.position,
                        recordedAtEpochMillis = point.recordedAtEpochMillis,
                        sourceId = point.sourceId,
                        speedOverGroundKnots = point.speedOverGroundKnots,
                        courseOverGroundTrueDegrees = point.courseOverGroundTrueDegrees,
                    )
                },
            )
        }
        val digestInput = segments.flatMap(RecordedTrackSegment::points).joinToString("|") { point ->
            "${point.position.latitude},${point.position.longitude},${point.recordedAtEpochMillis},${point.sourceId}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(digestInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return NavigationTrack(
            id = id,
            revision = 1L,
            name = name,
            segments = navigationSegments,
            sourceDigest = digest,
            importedAtMillis = savedAtEpochMillis,
            origin = NavigationTrackOrigin.RECORDED,
            startedAtEpochMillis = startedAtEpochMillis,
            endedAtEpochMillis = requireNotNull(stoppedAtEpochMillis),
            durationMillis = accumulatedDurationMillis,
            distanceNauticalMiles = distanceNauticalMiles(),
            navigationSessionId = navigationSessionId,
            routeId = routeId,
            routeRevision = routeRevision,
        )
    }

    private companion object { const val METERS_PER_NAUTICAL_MILE = 1852.0 }
}

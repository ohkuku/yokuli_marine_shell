package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DefaultTrackRecorderRuntimeTest {
    @Test
    fun `process runtime records fixes while no feature UI owner exists`() = runTest {
        val fixture = Fixture(this)
        fixture.runtime.initialize()
        fixture.runtime.execute(TrackRecorderCommand.Start)

        fixture.emitFix(1, -36.85, 174.76)
        fixture.emitFix(2, -36.84, 174.78)

        assertEquals(TrackRecorderStatus.RECORDING, fixture.runtime.state.value.status)
        assertEquals(2, fixture.runtime.state.value.session?.segments?.single()?.points?.size)
        assertTrue(fixture.runtime.state.value.distanceNauticalMiles > 0.0)
        assertEquals(fixture.store.value, fixture.runtime.state.value.session)
    }

    @Test
    fun `pause ignores fixes and resume starts a new actual-track segment`() = runTest {
        val fixture = Fixture(this)
        fixture.runtime.initialize()
        fixture.runtime.execute(TrackRecorderCommand.Start)
        fixture.emitFix(1, -36.85, 174.76)
        fixture.clock.now = 2_000
        fixture.runtime.execute(TrackRecorderCommand.Pause)
        fixture.emitFix(2, -36.84, 174.78)
        assertEquals(1, fixture.runtime.state.value.pointCount)

        fixture.clock.now = 3_000
        fixture.runtime.execute(TrackRecorderCommand.Resume)
        fixture.emitFix(3, -36.83, 174.80)

        assertEquals(2, fixture.runtime.state.value.session?.segments?.size)
        assertEquals(listOf(1, 1), fixture.runtime.state.value.session?.segments?.map { it.points.size })
    }

    @Test
    fun `process restore pauses recording and never invents live continuation`() = runTest {
        val first = Fixture(this)
        first.runtime.initialize()
        first.runtime.execute(TrackRecorderCommand.Start)
        first.emitFix(1, -36.85, 174.76)

        val restored = Fixture(this, requireNotNull(first.store.value))
        restored.runtime.initialize()

        assertEquals(TrackRecorderStatus.PAUSED, restored.runtime.state.value.status)
        restored.emitFix(2, -36.84, 174.78)
        assertEquals(1, restored.runtime.state.value.pointCount)
    }

    @Test
    fun `stop and save create a durable recorded track associated with navigation`() = runTest {
        val fixture = Fixture(this, active = activeSnapshot())
        fixture.runtime.initialize()
        fixture.runtime.execute(TrackRecorderCommand.Start)
        fixture.emitFix(1, -36.85, 174.76)
        fixture.clock.now = 5_000
        fixture.emitFix(2, -36.84, 174.78)
        fixture.clock.now = 8_000
        fixture.runtime.execute(TrackRecorderCommand.Stop)

        assertEquals(TrackRecorderStatus.STOPPED_AWAITING_SAVE, fixture.runtime.state.value.status)
        val saved = fixture.runtime.execute(TrackRecorderCommand.Save("Passage track"))

        assertTrue(saved is TrackRecorderCommandResult.Accepted)
        val track = fixture.library.value.importedTracks.single()
        assertEquals(NavigationTrackOrigin.RECORDED, track.origin)
        assertEquals(activeSnapshot().session?.sessionId, track.navigationSessionId)
        assertEquals(2, track.segments.single().points.size)
        assertTrue(requireNotNull(track.distanceNauticalMiles) > 0.0)
        assertNull(fixture.runtime.state.value.session)
        assertNull(fixture.store.value)
    }

    @Test
    fun `failed archive commit retains the stopped recording for retry or discard`() = runTest {
        val fixture = Fixture(this)
        fixture.runtime.initialize()
        fixture.runtime.execute(TrackRecorderCommand.Start)
        fixture.emitFix(1, -36.85, 174.76)
        fixture.runtime.execute(TrackRecorderCommand.Stop)
        fixture.library.failCommit = true

        val result = fixture.runtime.execute(TrackRecorderCommand.Save("Track 001"))

        assertEquals(
            TrackRecorderIssue.ARCHIVE_FAILED,
            (result as TrackRecorderCommandResult.Rejected).issue,
        )
        assertEquals(TrackRecorderStatus.STOPPED_AWAITING_SAVE, fixture.runtime.state.value.status)
        assertTrue(fixture.store.value != null)
    }

    @Test
    fun `first command preserves a stopped recording instead of resurrecting a paused session`() = runTest {
        val stopped = TrackRecordingSession(
            id = "track-stopped",
            startedAtEpochMillis = 100,
            status = TrackRecorderStatus.STOPPED_AWAITING_SAVE,
            segments = listOf(
                RecordedTrackSegment(
                    listOf(RecordedTrackPoint(NavigationPosition(-36.85, 174.76), 120, "source")),
                ),
            ),
            accumulatedDurationMillis = 20,
            stoppedAtEpochMillis = 200,
        )
        val fixture = Fixture(this, stored = stopped)

        val result = fixture.runtime.execute(TrackRecorderCommand.Save("Recovered track"))

        assertTrue(result is TrackRecorderCommandResult.Accepted)
        assertEquals("Recovered track", fixture.library.value.importedTracks.single().name)
        assertNull(fixture.runtime.state.value.session)
    }

    @Test
    fun `capacity pauses recording and preserves every accepted point`() = runTest {
        val fixture = Fixture(this, maxPoints = 1)
        fixture.runtime.initialize()
        fixture.runtime.execute(TrackRecorderCommand.Start)
        fixture.emitFix(1, -36.85, 174.76)
        fixture.emitFix(2, -36.84, 174.78)

        assertEquals(TrackRecorderStatus.PAUSED, fixture.runtime.state.value.status)
        assertEquals(TrackRecorderIssue.CAPACITY_REACHED, fixture.runtime.state.value.issue)
        assertEquals(1, fixture.runtime.state.value.pointCount)
        assertEquals(fixture.store.value, fixture.runtime.state.value.session)
    }

    @Test
    fun `start captures the already available usable position once`() = runTest {
        val fixture = Fixture(this)
        fixture.input.value = NavigationFix(
            revision = 7,
            position = NavigationPosition(-36.85, 174.76),
            positionStatus = NavigationInputStatus.LIVE,
            positionSourceId = "selected-source",
            motionSourceId = "selected-source",
            receivedAtMonotonicMillis = 7_000,
            ageMillis = 0,
        )
        fixture.runtime.initialize()

        fixture.runtime.execute(TrackRecorderCommand.Start)

        assertEquals(1, fixture.runtime.state.value.pointCount)
        fixture.emitFix(7, -36.85, 174.76)
        assertEquals(1, fixture.runtime.state.value.pointCount)
    }

    @Test
    fun `wall clock rollback cannot crash stop or create negative timing`() = runTest {
        val fixture = Fixture(this)
        fixture.clock.now = 1_000
        fixture.runtime.initialize()
        fixture.runtime.execute(TrackRecorderCommand.Start)
        fixture.clock.now = 500

        fixture.runtime.execute(TrackRecorderCommand.Stop)

        assertEquals(1_000L, fixture.runtime.state.value.session?.stoppedAtEpochMillis)
        assertEquals(0L, fixture.runtime.state.value.durationMillis)
    }

    @Test
    fun `failed recording load never gets overwritten by start`() = runTest {
        val fixture = Fixture(this, storeFailure = TrackRecordingStoreFailure.FUTURE_SCHEMA)

        val result = fixture.runtime.execute(TrackRecorderCommand.Start)

        assertEquals(TrackRecorderIssue.PERSISTENCE_FAILED, (result as TrackRecorderCommandResult.Rejected).issue)
        assertEquals(0, fixture.store.saveCalls)
        assertNull(fixture.runtime.state.value.session)
    }

    @Test
    fun `wall clock rollback keeps recorded and archived timestamps monotonic`() = runTest {
        val fixture = Fixture(this)
        fixture.clock.now = 1_000
        fixture.runtime.execute(TrackRecorderCommand.Start)
        fixture.clock.now = 500
        fixture.emitFix(1, -36.85, 174.76)
        fixture.runtime.execute(TrackRecorderCommand.Stop)
        fixture.clock.now = 400

        fixture.runtime.execute(TrackRecorderCommand.Save("Clock-safe track"))

        val track = fixture.library.value.tracks.single()
        assertEquals(1_000L, track.segments.single().points.single().recordedAtEpochMillis)
        assertEquals(1_000L, track.endedAtEpochMillis)
        assertEquals(1_000L, track.importedAtMillis)
    }

    private class Fixture(
        testScope: TestScope,
        stored: TrackRecordingSession? = null,
        active: ActiveNavigationSnapshot = ActiveNavigationSnapshot.EMPTY,
        maxPoints: Int = 200_000,
        storeFailure: TrackRecordingStoreFailure? = null,
    ) {
        private val testScope = testScope
        val input = MutableStateFlow(NavigationFix())
        val activeNavigation = MutableStateFlow(active)
        val clock = MutableClock()
        val store = FakeTrackStore(stored, storeFailure)
        val library = FakeLibrary()
        val runtime = DefaultTrackRecorderRuntime(
            input = object : NavigationInputPort { override val state = input },
            activeNavigation = activeNavigation,
            recordingStore = store,
            library = library,
            clock = clock,
            newId = { "recording-1" },
            maxPoints = maxPoints,
            scope = CoroutineScope(
                testScope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScope.testScheduler),
            ),
        )

        suspend fun emitFix(revision: Long, latitude: Double, longitude: Double) {
            input.value = NavigationFix(
                revision = revision,
                position = NavigationPosition(latitude, longitude),
                positionStatus = NavigationInputStatus.LIVE,
                positionSourceId = "selected-source",
                motionSourceId = "selected-source",
                receivedAtMonotonicMillis = revision * 1_000,
                ageMillis = 0,
                speedOverGroundKnots = 6.0,
                courseOverGroundTrueDegrees = 70.0,
            )
            testScope.advanceUntilIdle()
        }
    }

    private class MutableClock(var now: Long = 1_000) : NavigationRuntimeClock {
        override fun wallTimeMillis() = now
    }

    private class FakeTrackStore(
        initial: TrackRecordingSession?,
        private val loadFailure: TrackRecordingStoreFailure? = null,
    ) : TrackRecordingStore {
        var value = initial
        var saveCalls = 0
        override suspend fun loadTrackRecording(): TrackRecordingLoadResult = loadFailure?.let {
            TrackRecordingLoadResult.Failed(it)
        } ?: value?.let(TrackRecordingLoadResult::Loaded) ?: TrackRecordingLoadResult.Empty
        override suspend fun saveTrackRecording(session: TrackRecordingSession?): TrackRecordingSaveResult {
            saveCalls += 1
            value = session
            return TrackRecordingSaveResult.Saved
        }
    }

    private class FakeLibrary : NavigationLibraryPort {
        var value = NavigationLibrary()
        var failCommit = false
        override suspend fun loadNavigationLibrary() = NavigationLibraryLoadResult.Ready(value)
        override suspend fun commitNavigationChange(
            expectedLibraryRevision: Long,
            change: NavigationLibraryChange,
        ): NavigationLibraryCommitResult {
            if (failCommit) return NavigationLibraryCommitResult.Failed(NavigationLibraryFailure.IO)
            if (expectedLibraryRevision != value.revision) return NavigationLibraryCommitResult.Conflict(value.revision)
            val applied = NavigationLibraryEditor.apply(value, change) as NavigationChangeResult.Applied
            value = applied.library
            return NavigationLibraryCommitResult.Committed(value.revision)
        }
    }

    companion object {
        private fun activeSnapshot(): ActiveNavigationSnapshot {
            val route = RoutePlan(
                "route", 3, "Passage",
                listOf(
                    RoutePoint("A", NavigationPosition(-36.85, 174.76)),
                    RoutePoint("B", NavigationPosition(-36.80, 174.86)),
                ),
            )
            val session = ActiveNavigationSession(
                route.id, route.revision, 900, 0, 50.0,
                NavigationAdvancePolicy.MANUAL, NavigationSessionState.ACTIVE,
            )
            return ActiveNavigationSnapshot(revision = 1, session = session, route = route)
        }
    }
}

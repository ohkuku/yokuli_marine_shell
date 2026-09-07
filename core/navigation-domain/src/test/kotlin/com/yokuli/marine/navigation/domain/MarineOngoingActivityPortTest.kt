package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MarineOngoingActivityPortTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `consumer observes the exact process snapshots and commands the owning runtimes`() = runTest {
        val navigation = FakeNavigationRuntime(ActiveNavigationSnapshot(revision = 3L))
        val track = FakeTrackRuntime(TrackRecorderSnapshot(revision = 5L))
        val port = DefaultMarineOngoingActivityPort(navigation, track, backgroundScope)
        runCurrent()

        assertSame(navigation.state.value, port.state.value.navigation)
        assertSame(track.state.value, port.state.value.trackRecorder)

        val nextNavigation = ActiveNavigationSnapshot(revision = 4L)
        val nextTrack = TrackRecorderSnapshot(revision = 6L)
        navigation.mutableState.value = nextNavigation
        track.mutableState.value = nextTrack
        runCurrent()
        assertSame(nextNavigation, port.state.value.navigation)
        assertSame(nextTrack, port.state.value.trackRecorder)

        val navigationResult = port.execute(
            MarineOngoingActivityCommand.Navigation(ActiveNavigationCommand.Pause),
        )
        val trackResult = port.execute(
            MarineOngoingActivityCommand.Track(TrackRecorderCommand.Stop),
        )

        assertEquals(ActiveNavigationCommand.Pause, navigation.lastCommand)
        assertEquals(TrackRecorderCommand.Stop, track.lastCommand)
        assertEquals(
            MarineOngoingActivityCommandResult.Navigation(ActiveNavigationCommandResult.Accepted(4L)),
            navigationResult,
        )
        assertEquals(
            MarineOngoingActivityCommandResult.Track(TrackRecorderCommandResult.Accepted(6L)),
            trackResult,
        )
    }

    private class FakeNavigationRuntime(initial: ActiveNavigationSnapshot) : ActiveNavigationRuntimePort {
        val mutableState = MutableStateFlow(initial)
        override val state = mutableState
        var lastCommand: ActiveNavigationCommand? = null
        override suspend fun initialize() = state.value
        override suspend fun execute(command: ActiveNavigationCommand): ActiveNavigationCommandResult {
            lastCommand = command
            return ActiveNavigationCommandResult.Accepted(state.value.revision)
        }
    }

    private class FakeTrackRuntime(initial: TrackRecorderSnapshot) : TrackRecorderRuntimePort {
        val mutableState = MutableStateFlow(initial)
        override val state = mutableState
        var lastCommand: TrackRecorderCommand? = null
        override suspend fun initialize() = state.value
        override suspend fun execute(command: TrackRecorderCommand): TrackRecorderCommandResult {
            lastCommand = command
            return TrackRecorderCommandResult.Accepted(state.value.revision)
        }
    }
}

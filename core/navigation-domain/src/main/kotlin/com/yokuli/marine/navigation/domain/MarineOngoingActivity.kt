package com.yokuli.marine.navigation.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * One read-only process view for Shell surfaces and future consumers such as Cockpit.
 * It deliberately carries the original runtime snapshots: consumers must not recalculate
 * navigation or own an independent recording/navigation lifecycle.
 */
data class MarineOngoingActivitySnapshot(
    val navigation: ActiveNavigationSnapshot = ActiveNavigationSnapshot.EMPTY,
    val trackRecorder: TrackRecorderSnapshot = TrackRecorderSnapshot.EMPTY,
) {
    val navigationSessionId: String? get() = navigation.session?.sessionId
    val trackRecordingId: String? get() = trackRecorder.session?.id

    companion object { val EMPTY = MarineOngoingActivitySnapshot() }
}

sealed interface MarineOngoingActivityCommand {
    data class Navigation(val command: ActiveNavigationCommand) : MarineOngoingActivityCommand
    data class Track(val command: TrackRecorderCommand) : MarineOngoingActivityCommand
}

sealed interface MarineOngoingActivityCommandResult {
    data class Navigation(val result: ActiveNavigationCommandResult) : MarineOngoingActivityCommandResult
    data class Track(val result: TrackRecorderCommandResult) : MarineOngoingActivityCommandResult
}

interface MarineOngoingActivityPort {
    val state: StateFlow<MarineOngoingActivitySnapshot>
    suspend fun execute(command: MarineOngoingActivityCommand): MarineOngoingActivityCommandResult
}

class DefaultMarineOngoingActivityPort(
    private val activeNavigation: ActiveNavigationRuntimePort,
    private val trackRecorder: TrackRecorderRuntimePort,
    scope: CoroutineScope,
) : MarineOngoingActivityPort {
    override val state: StateFlow<MarineOngoingActivitySnapshot> = combine(
        activeNavigation.state,
        trackRecorder.state,
        ::MarineOngoingActivitySnapshot,
    ).stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = MarineOngoingActivitySnapshot(
            navigation = activeNavigation.state.value,
            trackRecorder = trackRecorder.state.value,
        ),
    )

    override suspend fun execute(command: MarineOngoingActivityCommand): MarineOngoingActivityCommandResult =
        when (command) {
            is MarineOngoingActivityCommand.Navigation -> MarineOngoingActivityCommandResult.Navigation(
                activeNavigation.execute(command.command),
            )
            is MarineOngoingActivityCommand.Track -> MarineOngoingActivityCommandResult.Track(
                trackRecorder.execute(command.command),
            )
        }
}

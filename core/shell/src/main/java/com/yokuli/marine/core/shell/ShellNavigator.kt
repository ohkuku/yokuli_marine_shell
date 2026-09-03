package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.*

class ShellNavigator {
    fun reduce(state: ShellNavigationState, command: ShellCommand): ShellNavigationState = when (command) {
        ShellCommand.Home -> state.copy(surface = ShellSurface.Start)
        ShellCommand.Back -> state.copy(surface = ShellSurface.Start)
        ShellCommand.ShowAllApps -> state.copy(surface = ShellSurface.AllApps)
        is ShellCommand.Open -> open(state, command.target)
    }

    private fun open(state: ShellNavigationState, target: LaunchTarget): ShellNavigationState = when (target) {
        LaunchTarget.Desktop -> state.copy(surface = ShellSurface.Start)
        LaunchTarget.AllApps -> state.copy(surface = ShellSurface.AllApps)
        else -> {
            val appId = target.coreAppId()
            val taskId = ShellTaskId(appId.name.lowercase())
            val task = ShellTask(taskId, appId, target)
            state.copy(
                surface = ShellSurface.App(taskId),
                tasks = state.tasks.filterNot { it.appId == appId } + task,
            )
        }
    }

    private fun LaunchTarget.coreAppId(): MarineAppId = when (this) {
        is LaunchTarget.Chart -> MarineAppId.CHART
        is LaunchTarget.Cockpit -> MarineAppId.COCKPIT
        is LaunchTarget.Library -> MarineAppId.LIBRARY
        is LaunchTarget.System -> MarineAppId.SYSTEM
        LaunchTarget.AllApps, LaunchTarget.Desktop -> error("Desktop surfaces do not own app tasks")
    }
}

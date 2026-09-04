package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.ShellCommand
import com.yokuli.marine.core.model.ShellNavigationState
import com.yokuli.marine.core.model.ShellSurface
import com.yokuli.marine.core.model.ShellTask
import com.yokuli.marine.core.model.ShellTaskId

class ShellNavigator(private val registry: LauncherRegistry) {
    fun reduce(state: ShellNavigationState, command: ShellCommand): ShellNavigationState = when (command) {
        ShellCommand.Back -> state.copy(surface = ShellSurface.Start)
        ShellCommand.ShowAllApps -> state.copy(surface = ShellSurface.AllApps)
        is ShellCommand.Open -> open(state, command.target)
    }

    private fun open(state: ShellNavigationState, target: LaunchTarget): ShellNavigationState {
        requireNotNull(registry.app(target.appId)) { "Target app is not installed: ${target.appId.value}" }
        val taskId = ShellTaskId(target.appId.value)
        val task = ShellTask(taskId, target.appId, target)
        return state.copy(
            surface = ShellSurface.App(taskId),
            tasks = state.tasks.filterNot { it.appId == target.appId } + task,
        )
    }
}

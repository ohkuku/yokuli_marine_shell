package com.yokuli.shell.engine.navigation

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherHostPort

@JvmInline
value class ShellTaskId(val value: String)

sealed interface ShellSurface {
    data object Start : ShellSurface
    data object AllApps : ShellSurface
    data class App(val taskId: ShellTaskId) : ShellSurface
}

data class ShellTask(
    val id: ShellTaskId,
    val appId: LauncherAppId,
    val token: LaunchToken,
)

data class ShellNavigationState(
    val surface: ShellSurface = ShellSurface.Start,
    val tasks: List<ShellTask> = emptyList(),
)

sealed interface ShellCommand {
    data class Open(val token: LaunchToken) : ShellCommand
    data object Back : ShellCommand
    data object ShowAllApps : ShellCommand
}

class ShellNavigator(private val hostPort: LauncherHostPort) {
    suspend fun reduce(state: ShellNavigationState, command: ShellCommand): ShellNavigationState = when (command) {
        ShellCommand.Back -> state.copy(surface = ShellSurface.Start)
        ShellCommand.ShowAllApps -> state.copy(surface = ShellSurface.AllApps)
        is ShellCommand.Open -> open(state, command.token)
    }

    private suspend fun open(state: ShellNavigationState, token: LaunchToken): ShellNavigationState {
        val resolution = hostPort.resolveLaunch(token)
        require(resolution is LaunchResolution.Internal) { "Launch token is not installed: ${token.value}" }
        val taskId = ShellTaskId(resolution.appId.value)
        val task = ShellTask(taskId, resolution.appId, resolution.token)
        return state.copy(
            surface = ShellSurface.App(taskId),
            tasks = state.tasks.filterNot { it.appId == resolution.appId } + task,
        )
    }
}

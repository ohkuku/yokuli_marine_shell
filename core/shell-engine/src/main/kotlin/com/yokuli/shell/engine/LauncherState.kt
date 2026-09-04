package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.LayoutTransaction
import com.yokuli.shell.engine.layout.StartDocument

@JvmInline
value class InternalAppTaskId(val value: String)

sealed interface LauncherSurface {
    data object Start : LauncherSurface
    data object AllApps : LauncherSurface
    data class InternalApp(val taskId: InternalAppTaskId) : LauncherSurface
    data object Recents : LauncherSurface
}

enum class LauncherTransitionIntent {
    NONE,
    SIBLING_FORWARD,
    SIBLING_BACK,
    DEEPER_FORWARD,
    DEEPER_BACK,
}

data class StartScreenState(
    val document: StartDocument,
    val interaction: StartInteractionState = StartInteractionState.Idle,
    val activeTransaction: LayoutTransaction? = null,
    val undoStack: List<LayoutTransaction> = emptyList(),
)

data class AllAppsState(val catalogRevision: Long)

data class InternalAppTask(
    val taskId: InternalAppTaskId,
    val appId: LauncherAppId,
    val lastLaunchToken: LaunchToken,
    val savedUiStateKey: String? = null,
)

data class InternalTaskState(val tasks: List<InternalAppTask> = emptyList()) {
    fun task(id: InternalAppTaskId): InternalAppTask? = tasks.firstOrNull { it.taskId == id }
}

sealed interface LauncherTransient {
    data class ContextMenu(val key: String) : LauncherTransient
}

sealed interface LauncherSystemOverlay

data class LauncherEngineState(
    val surface: LauncherSurface,
    val start: StartScreenState,
    val allApps: AllAppsState,
    val tasks: InternalTaskState,
    val catalog: LauncherCatalogSnapshot,
    val transient: LauncherTransient? = null,
    val systemOverlay: LauncherSystemOverlay? = null,
    val transitionIntent: LauncherTransitionIntent = LauncherTransitionIntent.NONE,
    val nextTransactionId: Long = 1,
)

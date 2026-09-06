package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.LayoutChangeReason
import com.yokuli.shell.engine.layout.LayoutTransaction
import com.yokuli.shell.engine.layout.StartDocument

@JvmInline
value class InternalAppTaskId(val value: String)

sealed interface ShellVisualSurface {
    data object Desktop : ShellVisualSurface
    data object ModuleList : ShellVisualSurface
    data class Search(
        val query: String = "",
        val returnSurface: ShellVisualSurface = Desktop,
    ) : ShellVisualSurface
    data object Recents : ShellVisualSurface
    data class Module(val taskId: InternalAppTaskId) : ShellVisualSurface
}

enum class LauncherRecoveryMode { NORMAL, RESTORING, SAFE_MODE }

data class StartScreenState(
    val document: StartDocument,
    val interaction: StartInteractionState = StartInteractionState.Idle,
    val activeTransaction: LayoutTransaction? = null,
    val undoStack: List<LayoutTransaction> = emptyList(),
    val reveal: StartReveal? = null,
)

data class StartReveal(val tileId: TileInstanceId, val transactionId: String)

data class AllAppsState(val catalogRevision: Long)

data class InternalAppTask(
    val taskId: InternalAppTaskId,
    val appId: LauncherAppId,
    val lastLaunchToken: LaunchToken,
    val backStack: List<LaunchToken> = emptyList(),
    val savedUiStateKey: String? = null,
)

/**
 * A linked return marks an explicit cross-app hand-off. The target depth is the route depth
 * created by that hand-off, so routes opened afterwards still pop inside the target first.
 */
data class LinkedTaskReturn(
    val callerTaskId: InternalAppTaskId,
    val targetTaskId: InternalAppTaskId,
    val targetBackStackDepth: Int,
) {
    init { require(targetBackStackDepth >= 0) }
}

data class InternalTaskState(
    val tasks: List<InternalAppTask> = emptyList(),
    val linkedReturns: List<LinkedTaskReturn> = emptyList(),
) {
    fun task(id: InternalAppTaskId): InternalAppTask? = tasks.firstOrNull { it.taskId == id }
}

sealed interface LauncherTransient {
    data class ContextMenu(val entryId: LauncherEntryId) : LauncherTransient
    data object AlphabetJump : LauncherTransient
    data class UndoLayout(
        val transactionId: String,
        val reason: LayoutChangeReason,
        val entryId: LauncherEntryId,
    ) : LauncherTransient
    data class Notice(val notice: LauncherNotice) : LauncherTransient
}

enum class LauncherNotice { ALREADY_PINNED, PIN_UNAVAILABLE, LAYOUT_UNAVAILABLE }

sealed interface LauncherSystemOverlay

data class LauncherEngineState(
    val surface: ShellVisualSurface,
    val start: StartScreenState,
    val allApps: AllAppsState,
    val tasks: InternalTaskState,
    val catalog: LauncherCatalogSnapshot,
    val transient: LauncherTransient? = null,
    val systemOverlay: LauncherSystemOverlay? = null,
    val recentsReturnSurface: ShellVisualSurface? = null,
    val recoveryMode: LauncherRecoveryMode = LauncherRecoveryMode.NORMAL,
    val incidentLog: List<LauncherIncident> = emptyList(),
    val transitionRequest: ShellTransitionRequest? = null,
    val nextTransactionId: Long = 1,
)

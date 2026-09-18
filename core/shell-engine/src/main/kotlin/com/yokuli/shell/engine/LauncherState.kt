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
    /** 与 backStack 一一对应的页面实例；同一个地址再次打开也不能覆盖调用者的草稿。 */
    val backStackUiStateKeys: List<String> = emptyList(),
)

/** 页面地址描述业务对象，实例标识描述这一次访问；两者不可混用。 */
val InternalAppTask.currentUiStateKey: String
    get() = savedUiStateKey ?: "${taskId.value}:${lastLaunchToken.value}"

val InternalAppTask.retainedUiStateKeys: Set<String>
    get() = backStack.mapIndexed { index, token ->
        backStackUiStateKeys.getOrNull(index) ?: "${taskId.value}:${token.value}"
    }.toSet() + currentUiStateKey

/**
 * A linked return marks an explicit cross-app hand-off. The target depth is the route depth
 * created by that hand-off, so routes opened afterwards still pop inside the target first.
 */
data class LinkedTaskReturn(
    val callerTaskId: InternalAppTaskId,
    val targetTaskId: InternalAppTaskId,
    val targetBackStackDepth: Int,
    /** 跨应用调用发生时的完整调用者页面，允许 A → B → A 后逐层回到原位置。 */
    val callerSnapshot: InternalAppTask? = null,
    /** 被调用应用原来已经打开的会话；调用结束后恢复，避免污染最近任务。 */
    val targetPreviousTask: InternalAppTask? = null,
) {
    init { require(targetBackStackDepth >= 0) }
}

data class InternalTaskState(
    val tasks: List<InternalAppTask> = emptyList(),
    val linkedReturns: List<LinkedTaskReturn> = emptyList(),
    val nextRouteInstance: Long = 1,
) {
    fun task(id: InternalAppTaskId): InternalAppTask? = tasks.firstOrNull { it.taskId == id }

    val retainedUiStateKeys: Set<String>
        get() = (tasks + linkedReturns.flatMap { listOfNotNull(it.callerSnapshot, it.targetPreviousTask) })
            .flatMap { it.retainedUiStateKeys }.toSet()
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

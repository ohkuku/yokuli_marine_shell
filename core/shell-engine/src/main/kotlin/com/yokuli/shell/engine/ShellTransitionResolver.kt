package com.yokuli.shell.engine

enum class ShellTransitionTrigger {
    PAGE_SETTLED,
    TILE,
    MODULE_LIST_ENTRY,
    SEARCH_RESULT,
    BACK,
    BRIDGE,
    SEARCH_KEY,
    RECENTS_KEY,
    RECENT_TASK,
    RESTORE,
}

enum class ShellTransitionKind {
    NONE,
    PAGER_FORWARD,
    PAGER_BACK,
    DESKTOP_TO_MODULE,
    MODULE_LIST_TO_MODULE,
    SEARCH_TO_MODULE,
    MODULE_TO_DESKTOP,
    SEARCH_PRESENT,
    SEARCH_DISMISS,
    RECENTS_PRESENT,
    RECENTS_DISMISS,
    TASK_ACTIVATE,
}

data class ShellTransitionRequest(
    val from: ShellVisualSurface,
    val to: ShellVisualSurface,
    val trigger: ShellTransitionTrigger,
    val kind: ShellTransitionKind,
)

/** One deterministic matrix owns every Shell-level visual transition decision. */
object ShellTransitionResolver {
    fun resolve(
        from: ShellVisualSurface,
        to: ShellVisualSurface,
        trigger: ShellTransitionTrigger,
    ): ShellTransitionRequest = ShellTransitionRequest(from, to, trigger, resolveKind(from, to, trigger))

    private fun resolveKind(
        from: ShellVisualSurface,
        to: ShellVisualSurface,
        trigger: ShellTransitionTrigger,
    ): ShellTransitionKind = when {
        from == to -> ShellTransitionKind.NONE
        from == ShellVisualSurface.Desktop && to == ShellVisualSurface.ModuleList ->
            ShellTransitionKind.PAGER_FORWARD
        from == ShellVisualSurface.ModuleList && to == ShellVisualSurface.Desktop ->
            ShellTransitionKind.PAGER_BACK
        from is ShellVisualSurface.Search && to is ShellVisualSurface.Module ->
            ShellTransitionKind.SEARCH_TO_MODULE
        from is ShellVisualSurface.Search && to == from.returnSurface ->
            ShellTransitionKind.SEARCH_DISMISS
        from is ShellVisualSurface.Module && to == ShellVisualSurface.Desktop ->
            ShellTransitionKind.MODULE_TO_DESKTOP
        from == ShellVisualSurface.Desktop && to is ShellVisualSurface.Module ->
            ShellTransitionKind.DESKTOP_TO_MODULE
        from == ShellVisualSurface.ModuleList && to is ShellVisualSurface.Module ->
            ShellTransitionKind.MODULE_LIST_TO_MODULE
        to is ShellVisualSurface.Search -> ShellTransitionKind.SEARCH_PRESENT
        to == ShellVisualSurface.Recents -> ShellTransitionKind.RECENTS_PRESENT
        from == ShellVisualSurface.Recents && trigger == ShellTransitionTrigger.RECENT_TASK ->
            ShellTransitionKind.TASK_ACTIVATE
        from == ShellVisualSurface.Recents -> ShellTransitionKind.RECENTS_DISMISS
        trigger == ShellTransitionTrigger.BRIDGE && to == ShellVisualSurface.Desktop ->
            ShellTransitionKind.MODULE_TO_DESKTOP
        else -> ShellTransitionKind.NONE
    }
}

fun ShellTransitionRequest.toLegacyIntent(): LauncherTransitionIntent = when (kind) {
    ShellTransitionKind.PAGER_FORWARD -> LauncherTransitionIntent.SIBLING_FORWARD
    ShellTransitionKind.PAGER_BACK -> LauncherTransitionIntent.SIBLING_BACK
    ShellTransitionKind.DESKTOP_TO_MODULE,
    ShellTransitionKind.MODULE_LIST_TO_MODULE,
    ShellTransitionKind.SEARCH_TO_MODULE,
    ShellTransitionKind.TASK_ACTIVATE -> LauncherTransitionIntent.DEEPER_FORWARD
    ShellTransitionKind.MODULE_TO_DESKTOP -> LauncherTransitionIntent.DEEPER_BACK
    ShellTransitionKind.SEARCH_PRESENT,
    ShellTransitionKind.SEARCH_DISMISS,
    ShellTransitionKind.RECENTS_PRESENT,
    ShellTransitionKind.RECENTS_DISMISS -> LauncherTransitionIntent.TRANSIENT
    ShellTransitionKind.NONE -> LauncherTransitionIntent.NONE
}

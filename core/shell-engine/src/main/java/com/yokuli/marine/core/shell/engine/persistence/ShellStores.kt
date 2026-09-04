package com.yokuli.marine.core.shell.engine.persistence

import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.shell.engine.interaction.LauncherPage
import com.yokuli.marine.core.shell.engine.interaction.StartInteractionState
import com.yokuli.marine.core.shell.engine.layout.DesktopDocument
import com.yokuli.marine.core.shell.engine.layout.LayoutTransaction
import kotlinx.coroutines.flow.StateFlow

data class ShellPreferences(
    val themeName: String,
    val accentName: String,
    val language: AppLanguage,
    val layoutLocked: Boolean,
    val lastLauncherPage: LauncherPage,
    val lastForegroundApp: MarineAppId?,
)

data class ShellState(
    val desktop: DesktopDocument,
    val interaction: StartInteractionState,
    val lastCommittedTransaction: LayoutTransaction?,
)

sealed interface ShellAction {
    data class CommitLayout(val transaction: LayoutTransaction) : ShellAction
    data object UndoLastLayout : ShellAction
    data object CancelInteraction : ShellAction
}

interface ShellStore {
    val state: StateFlow<ShellState>
    fun dispatch(action: ShellAction)
}

interface DesktopLayoutStore {
    val document: StateFlow<DesktopDocument?>
    suspend fun save(document: DesktopDocument)
}

interface ShellPreferencesStore {
    val preferences: StateFlow<ShellPreferences>
    suspend fun update(transform: (ShellPreferences) -> ShellPreferences)
}

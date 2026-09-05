package com.yokuli.marine.feature.desktop

import androidx.compose.runtime.Composable
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.LayoutProposal
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.LauncherTransient
import com.yokuli.shell.engine.StartReveal
import com.yokuli.shell.engine.InternalAppTaskId
import com.yokuli.shell.compose.LauncherEntryUiState
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherPresentationValidator

enum class MarineIconKind { APPS, CANCEL, UNPIN, RESIZE, PIN, INFO, GENERIC }

data class LauncherUiState(
    val document: StartDocument,
    val entries: List<LauncherEntryUiState>,
    val interaction: StartInteractionState = StartInteractionState.Idle,
    val transient: LauncherTransient? = null,
    val reveal: StartReveal? = null,
) {
    val pinnedEntries: Set<LauncherEntryId> = document.placements.map { it.entryId }.toSet()
}

sealed interface LauncherUiAction {
    data class Open(val token: LaunchToken) : LauncherUiAction
    data object ShowAllApps : LauncherUiAction
    data class ProposeLayout(val proposal: LayoutProposal) : LauncherUiAction
    data class EnterStartEdit(val tileId: TileInstanceId) : LauncherUiAction
    data class SelectStartTile(val tileId: TileInstanceId) : LauncherUiAction
    data object ExitStartEdit : LauncherUiAction
    data class BeginTileDrag(val tileId: TileInstanceId, val pointerId: Long, val grabOffset: ShellOffset) : LauncherUiAction
    data class InsertionTargetChanged(val tileId: TileInstanceId, val insertionIndex: Int) : LauncherUiAction
    data class DropTile(val tileId: TileInstanceId) : LauncherUiAction
    data object CancelTileOperation : LauncherUiAction
    data class ResizeTile(val tileId: TileInstanceId) : LauncherUiAction
    data class MoveTileBy(val tileId: TileInstanceId, val columns: Int, val rows: Int) : LauncherUiAction
    data class OpenEntryContextMenu(val entryId: LauncherEntryId) : LauncherUiAction
    data object OpenAlphabetJump : LauncherUiAction
    data object DismissTransient : LauncherUiAction
    data class PinEntry(val entryId: LauncherEntryId) : LauncherUiAction
    data class UnpinTile(val tileId: TileInstanceId) : LauncherUiAction
    data class AcknowledgeStartReveal(val tileId: TileInstanceId) : LauncherUiAction
    data object UndoLayout : LauncherUiAction
    data class UpdateSearchQuery(val query: String) : LauncherUiAction
    data class ActivateTask(val taskId: InternalAppTaskId) : LauncherUiAction
    data class ShowAppInfo(val entryId: LauncherEntryId) : LauncherUiAction
}

/**
 * 中文：这里只把真实配置翻译成 UI 状态，不制造任何船舶事实。
 * English: This maps real configuration to UI state and never invents vessel facts.
 */
@Composable
fun productionLauncherUiState(
    catalog: LauncherCatalogSnapshot,
    document: StartDocument,
    interaction: StartInteractionState = StartInteractionState.Idle,
    transient: LauncherTransient? = null,
    reveal: StartReveal? = null,
    visualContributions: List<LauncherEntryVisualContribution>,
): LauncherUiState {
    LauncherPresentationValidator.validate(catalog, visualContributions)
    val visualsByEntry = visualContributions.associateBy { it.entryId }
    return LauncherUiState(
        document = document,
        interaction = interaction,
        transient = transient,
        reveal = reveal,
        entries = catalog.entries.map { descriptor ->
            LauncherEntryUiState(
                descriptor = descriptor,
                visual = requireNotNull(visualsByEntry[descriptor.entryId]),
            )
        },
    )
}

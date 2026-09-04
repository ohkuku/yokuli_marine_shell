package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.UiText
import com.yokuli.shell.engine.geometry.WpReferenceProfile
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.LayoutChangeReason
import com.yokuli.shell.engine.layout.LayoutProposal
import com.yokuli.shell.engine.layout.LayoutTransaction
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartDocumentRepair
import com.yokuli.shell.engine.layout.StartDocumentValidator
import com.yokuli.shell.engine.layout.StartLayoutEditor
import com.yokuli.shell.engine.layout.StartRepairIncident
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.StartInteractionState

data class LauncherReducerContext(
    val defaultDocument: StartDocument,
    val profile: WpReferenceProfile,
    val launchResolution: LaunchResolution? = null,
)

data class LauncherReduction(
    val state: LauncherEngineState,
    val effects: List<LauncherEffect> = emptyList(),
)

sealed interface LauncherAction {
    data object ShowStart : LauncherAction
    data object ShowAllApps : LauncherAction
    data object Back : LauncherAction
    data object Home : LauncherAction
    data class Open(val token: LaunchToken) : LauncherAction
    data class CatalogChanged(val catalog: LauncherCatalogSnapshot) : LauncherAction
    data class ApplyLayoutProposal(val proposal: LayoutProposal) : LauncherAction
    data class BeginLayoutTransaction(val proposal: LayoutProposal) : LauncherAction
    data object CommitLayoutTransaction : LauncherAction
    data object CancelLayoutTransaction : LauncherAction
    data object UndoLayout : LauncherAction
    data class EnterStartEdit(val tileId: TileInstanceId) : LauncherAction
    data class SelectStartTile(val tileId: TileInstanceId) : LauncherAction
    data object ExitStartEdit : LauncherAction
    data class BeginTileDrag(
        val tileId: TileInstanceId,
        val pointerId: Long,
        val grabOffsetPx: ShellOffset,
    ) : LauncherAction
    data class UpdateTileDrag(
        val tileId: TileInstanceId,
        val visualOffsetPx: ShellOffset,
        val targetCell: GridCell,
        val autoScrollPxPerSecond: Float,
    ) : LauncherAction
    data class AutoScrollTileDrag(
        val tileId: TileInstanceId,
        val consumedScrollPx: Float,
        val targetCell: GridCell,
    ) : LauncherAction
    data class DropTile(val tileId: TileInstanceId) : LauncherAction
    data object CancelTileOperation : LauncherAction
    data class ResizeTile(val tileId: TileInstanceId) : LauncherAction
    data object CommitTileResize : LauncherAction
    data class MoveTileBy(val tileId: TileInstanceId, val columns: Int, val rows: Int) : LauncherAction
    data class TogglePin(val entryId: LauncherEntryId) : LauncherAction
    data object ResetStartDocument : LauncherAction
}

enum class LauncherHaptic { SELECTION, LONG_PRESS, DROP }

sealed interface LauncherIncident {
    data class UnresolvedLaunchToken(val token: LaunchToken) : LauncherIncident
    data class InvalidLayoutProposal(val reason: String) : LauncherIncident
    data class CatalogRepair(val incident: StartRepairIncident) : LauncherIncident
    data class PersistenceFailure(val message: String) : LauncherIncident
}

sealed interface LauncherEffect {
    data class Launch(val token: LaunchToken) : LauncherEffect
    data class PersistDocument(val document: StartDocument) : LauncherEffect
    data class Haptic(val kind: LauncherHaptic) : LauncherEffect
    data class AccessibilityAnnouncement(val text: UiText) : LauncherEffect
    data class LogIncident(val incident: LauncherIncident) : LauncherEffect
    data class ScrollStartToReveal(val tileId: TileInstanceId) : LauncherEffect
    data object OpenAndroidSettings : LauncherEffect
}

interface LauncherReducer {
    fun reduce(
        state: LauncherEngineState,
        action: LauncherAction,
        context: LauncherReducerContext,
    ): LauncherReduction
}

class DefaultLauncherReducer : LauncherReducer {
    override fun reduce(
        state: LauncherEngineState,
        action: LauncherAction,
        context: LauncherReducerContext,
    ): LauncherReduction = when (action) {
        LauncherAction.ShowStart,
        LauncherAction.Home -> LauncherReduction(
            state.copy(
                surface = LauncherSurface.Start,
                transient = null,
                transitionIntent = LauncherTransitionIntent.DEEPER_BACK,
            ),
        )

        LauncherAction.ShowAllApps -> LauncherReduction(
            state.copy(
                surface = LauncherSurface.AllApps,
                transient = null,
                transitionIntent = LauncherTransitionIntent.SIBLING_FORWARD,
            ),
        )

        LauncherAction.Back -> back(state)
        is LauncherAction.Open -> open(state, action, context.launchResolution)
        is LauncherAction.CatalogChanged -> reconcileCatalog(state, action.catalog, context.defaultDocument)
        is LauncherAction.ApplyLayoutProposal -> applyCommitted(state, action.proposal)
        is LauncherAction.BeginLayoutTransaction -> begin(state, action.proposal)
        LauncherAction.CommitLayoutTransaction -> commit(state)
        LauncherAction.CancelLayoutTransaction -> cancel(state)
        LauncherAction.UndoLayout -> undo(state)
        is LauncherAction.EnterStartEdit -> enterEdit(state, action.tileId)
        is LauncherAction.SelectStartTile -> selectTile(state, action.tileId)
        LauncherAction.ExitStartEdit -> exitEdit(state)
        is LauncherAction.BeginTileDrag -> beginDrag(state, action)
        is LauncherAction.UpdateTileDrag -> updateDrag(state, action)
        is LauncherAction.AutoScrollTileDrag -> autoScrollDrag(state, action)
        is LauncherAction.DropTile -> dropTile(state, action.tileId)
        LauncherAction.CancelTileOperation -> cancelTileOperation(state)
        is LauncherAction.ResizeTile -> resizeTile(state, action.tileId)
        LauncherAction.CommitTileResize -> commitTileResize(state)
        is LauncherAction.MoveTileBy -> moveTileBy(state, action)
        is LauncherAction.TogglePin -> togglePin(state, action.entryId)
        LauncherAction.ResetStartDocument -> applyCommitted(
            state,
            LayoutProposal(state.start.document, context.defaultDocument, LayoutChangeReason.RESET),
        )
    }

    private fun back(state: LauncherEngineState): LauncherReduction {
        state.transient?.let { return LauncherReduction(state.copy(transient = null)) }
        when (state.start.interaction) {
            is StartInteractionState.Dragging,
            is StartInteractionState.Resizing,
            is StartInteractionState.Settling -> return cancelTileOperation(state)
            is StartInteractionState.EditIdle -> return exitEdit(state)
            else -> Unit
        }
        state.start.activeTransaction?.let { return cancel(state) }
        val intent = if (state.surface == LauncherSurface.AllApps) {
            LauncherTransitionIntent.SIBLING_BACK
        } else {
            LauncherTransitionIntent.DEEPER_BACK
        }
        return LauncherReduction(state.copy(surface = LauncherSurface.Start, transitionIntent = intent))
    }

    private fun open(
        state: LauncherEngineState,
        action: LauncherAction.Open,
        resolution: LaunchResolution?,
    ): LauncherReduction = when (resolution) {
        is LaunchResolution.Internal -> {
            val taskId = InternalAppTaskId(resolution.appId.value)
            val task = InternalAppTask(taskId, resolution.appId, resolution.token)
            LauncherReduction(
                state = state.copy(
                    surface = LauncherSurface.InternalApp(taskId),
                    tasks = InternalTaskState(
                        state.tasks.tasks.filterNot { it.appId == resolution.appId } + task,
                    ),
                    transient = null,
                    transitionIntent = LauncherTransitionIntent.DEEPER_FORWARD,
                ),
                effects = listOf(LauncherEffect.Launch(resolution.token)),
            )
        }

        is LaunchResolution.Unresolved,
        null -> LauncherReduction(
            state = state,
            effects = listOf(LauncherEffect.LogIncident(LauncherIncident.UnresolvedLaunchToken(action.token))),
        )
    }

    private fun reconcileCatalog(
        state: LauncherEngineState,
        catalog: LauncherCatalogSnapshot,
        defaultDocument: StartDocument,
    ): LauncherReduction {
        val profile = runCatching { WpReferenceProfiles.require(state.start.document.profileId) }
            .getOrElse { WpReferenceProfiles.require(defaultDocument.profileId) }
        val fallback = defaultDocument.copy(profileId = profile.id)
        val repair = StartDocumentRepair.repair(state.start.document, catalog.entries, fallback, profile)
        val installedApps = catalog.apps.map { it.appId }.toSet()
        val tasks = state.tasks.tasks.filter { it.appId in installedApps }
        val currentTaskInstalled = (state.surface as? LauncherSurface.InternalApp)
            ?.let { current -> tasks.any { it.taskId == current.taskId } } ?: true
        val repairedState = state.copy(
            surface = if (currentTaskInstalled) state.surface else LauncherSurface.Start,
            start = state.start.copy(
                document = repair.document,
                interaction = if (state.start.interaction is StartInteractionState.Dragging) {
                    StartInteractionState.Idle
                } else {
                    state.start.interaction
                },
            ),
            allApps = AllAppsState(catalog.revision),
            tasks = InternalTaskState(tasks),
            catalog = catalog,
        )
        val effects = buildList {
            repair.incidents.forEach { add(LauncherEffect.LogIncident(LauncherIncident.CatalogRepair(it))) }
            if (repair.document != state.start.document) add(LauncherEffect.PersistDocument(repair.document))
        }
        return LauncherReduction(repairedState, effects)
    }

    private fun enterEdit(state: LauncherEngineState, tileId: TileInstanceId): LauncherReduction {
        if (state.start.document.placements.none { it.tileId == tileId }) return LauncherReduction(state)
        return LauncherReduction(
            state.copy(start = state.start.copy(interaction = StartInteractionState.EditIdle(tileId))),
            listOf(LauncherEffect.Haptic(LauncherHaptic.LONG_PRESS)),
        )
    }

    private fun selectTile(state: LauncherEngineState, tileId: TileInstanceId): LauncherReduction {
        if (state.start.interaction !is StartInteractionState.EditIdle) return LauncherReduction(state)
        if (state.start.document.placements.none { it.tileId == tileId }) return LauncherReduction(state)
        return LauncherReduction(
            state.copy(start = state.start.copy(interaction = StartInteractionState.EditIdle(tileId))),
        )
    }

    private fun exitEdit(state: LauncherEngineState): LauncherReduction {
        val cancelled = if (state.start.activeTransaction != null) cancel(state).state else state
        return LauncherReduction(cancelled.copy(start = cancelled.start.copy(interaction = StartInteractionState.Idle)))
    }

    private fun beginDrag(state: LauncherEngineState, action: LauncherAction.BeginTileDrag): LauncherReduction {
        val placement = state.start.document.placements.firstOrNull { it.tileId == action.tileId }
            ?: return LauncherReduction(state)
        return LauncherReduction(
            state.copy(
                start = state.start.copy(
                    interaction = StartInteractionState.Dragging(
                        tileId = action.tileId,
                        pointerId = action.pointerId,
                        grabOffsetPx = action.grabOffsetPx,
                        visualOffsetPx = ShellOffset(0f, 0f),
                        targetCell = placement.cell,
                        proposedLayout = state.start.document,
                        autoScrollPxPerSecond = 0f,
                    ),
                ),
            ),
        )
    }

    private fun updateDrag(state: LauncherEngineState, action: LauncherAction.UpdateTileDrag): LauncherReduction {
        val dragging = state.start.interaction as? StartInteractionState.Dragging ?: return LauncherReduction(state)
        if (dragging.tileId != action.tileId) return LauncherReduction(state)
        val proposed = StartLayoutEditor.move(
            state.start.document,
            action.tileId,
            action.targetCell,
            state.catalog.entries,
        )?.after ?: state.start.document
        return LauncherReduction(
            state.copy(
                start = state.start.copy(
                    interaction = dragging.copy(
                        visualOffsetPx = action.visualOffsetPx,
                        targetCell = action.targetCell,
                        proposedLayout = proposed,
                        autoScrollPxPerSecond = action.autoScrollPxPerSecond,
                    ),
                ),
            ),
        )
    }

    private fun autoScrollDrag(
        state: LauncherEngineState,
        action: LauncherAction.AutoScrollTileDrag,
    ): LauncherReduction {
        val dragging = state.start.interaction as? StartInteractionState.Dragging ?: return LauncherReduction(state)
        if (dragging.tileId != action.tileId) return LauncherReduction(state)
        return updateDrag(
            state,
            LauncherAction.UpdateTileDrag(
                tileId = action.tileId,
                visualOffsetPx = dragging.visualOffsetPx.copy(
                    y = dragging.visualOffsetPx.y + action.consumedScrollPx,
                ),
                targetCell = action.targetCell,
                autoScrollPxPerSecond = dragging.autoScrollPxPerSecond,
            ),
        )
    }

    private fun dropTile(state: LauncherEngineState, tileId: TileInstanceId): LauncherReduction {
        val dragging = state.start.interaction as? StartInteractionState.Dragging ?: return LauncherReduction(state)
        if (dragging.tileId != tileId) return LauncherReduction(state)
        if (dragging.proposedLayout == state.start.document) {
            return LauncherReduction(
                state.copy(start = state.start.copy(interaction = StartInteractionState.EditIdle(tileId))),
            )
        }
        val applied = applyCommitted(
            state,
            LayoutProposal(state.start.document, dragging.proposedLayout, LayoutChangeReason.MOVE),
        )
        return applied.copy(
            state = applied.state.copy(
                start = applied.state.start.copy(interaction = StartInteractionState.EditIdle(tileId)),
            ),
            effects = applied.effects + LauncherEffect.Haptic(LauncherHaptic.DROP),
        )
    }

    private fun cancelTileOperation(state: LauncherEngineState): LauncherReduction {
        val selected = when (val interaction = state.start.interaction) {
            is StartInteractionState.Dragging -> interaction.tileId
            is StartInteractionState.Resizing -> interaction.tileId
            is StartInteractionState.Settling -> null
            else -> null
        }
        if (
            state.start.interaction !is StartInteractionState.Dragging &&
            state.start.interaction !is StartInteractionState.Resizing &&
            state.start.interaction !is StartInteractionState.Settling
        ) {
            return LauncherReduction(state)
        }
        val cancelled = if (state.start.activeTransaction != null) cancel(state).state else state
        return LauncherReduction(
            cancelled.copy(
                start = cancelled.start.copy(interaction = StartInteractionState.EditIdle(selected)),
            ),
        )
    }

    private fun resizeTile(state: LauncherEngineState, tileId: TileInstanceId): LauncherReduction {
        val proposal = StartLayoutEditor.resize(state.start.document, tileId, state.catalog.entries)
            ?: return LauncherReduction(state)
        validateProposal(state, proposal)?.let { return it }
        val transaction = LayoutTransaction(
            id = "layout-${state.nextTransactionId}",
            before = proposal.before,
            after = proposal.after,
            reason = LayoutChangeReason.RESIZE,
        )
        return LauncherReduction(
            state = state.copy(
                start = state.start.copy(
                    activeTransaction = transaction,
                    interaction = StartInteractionState.Resizing(
                        tileId,
                        proposal.after.placements.single { it.tileId == tileId }.size,
                        proposal.after,
                    ),
                ),
                nextTransactionId = state.nextTransactionId + 1,
            ),
            effects = listOf(LauncherEffect.Haptic(LauncherHaptic.SELECTION)),
        )
    }

    private fun commitTileResize(state: LauncherEngineState): LauncherReduction {
        val resizing = state.start.interaction as? StartInteractionState.Resizing ?: return LauncherReduction(state)
        val committed = commit(state)
        return committed.copy(
            state = committed.state.copy(
                start = committed.state.start.copy(interaction = StartInteractionState.EditIdle(resizing.tileId)),
            ),
        )
    }

    private fun moveTileBy(state: LauncherEngineState, action: LauncherAction.MoveTileBy): LauncherReduction {
        val placement = state.start.document.placements.firstOrNull { it.tileId == action.tileId }
            ?: return LauncherReduction(state)
        val proposal = StartLayoutEditor.move(
            state.start.document,
            action.tileId,
            GridCell(placement.cell.column + action.columns, placement.cell.row + action.rows),
            state.catalog.entries,
        ) ?: return LauncherReduction(state)
        val applied = applyCommitted(state, proposal)
        return applied.copy(
            state = applied.state.copy(
                start = applied.state.start.copy(interaction = StartInteractionState.EditIdle(action.tileId)),
            ),
            effects = applied.effects + LauncherEffect.Haptic(LauncherHaptic.SELECTION),
        )
    }

    private fun begin(state: LauncherEngineState, proposal: LayoutProposal): LauncherReduction {
        validateProposal(state, proposal)?.let { return it }
        val transaction = LayoutTransaction(
            id = "layout-${state.nextTransactionId}",
            before = proposal.before,
            after = proposal.after,
            reason = proposal.reason,
        )
        return LauncherReduction(
            state.copy(
                start = state.start.copy(document = proposal.after, activeTransaction = transaction),
                nextTransactionId = state.nextTransactionId + 1,
            ),
        )
    }

    private fun commit(state: LauncherEngineState): LauncherReduction {
        val transaction = state.start.activeTransaction ?: return LauncherReduction(state)
        return LauncherReduction(
            state.copy(
                start = state.start.copy(
                    document = transaction.after,
                    activeTransaction = null,
                    undoStack = state.start.undoStack + transaction,
                ),
            ),
            listOf(LauncherEffect.PersistDocument(transaction.after)),
        )
    }

    private fun cancel(state: LauncherEngineState): LauncherReduction {
        val transaction = state.start.activeTransaction ?: return LauncherReduction(state)
        return LauncherReduction(
            state.copy(start = state.start.copy(document = transaction.before, activeTransaction = null)),
        )
    }

    private fun applyCommitted(state: LauncherEngineState, proposal: LayoutProposal): LauncherReduction {
        val begun = begin(state, proposal)
        if (begun.state == state) return begun
        val committed = commit(begun.state)
        return committed.copy(effects = begun.effects + committed.effects)
    }

    private fun undo(state: LauncherEngineState): LauncherReduction {
        val transaction = state.start.undoStack.lastOrNull() ?: return LauncherReduction(state)
        val document = transaction.before
        return LauncherReduction(
            state.copy(
                start = state.start.copy(document = document, undoStack = state.start.undoStack.dropLast(1)),
            ),
            listOf(LauncherEffect.PersistDocument(document)),
        )
    }

    private fun togglePin(state: LauncherEngineState, entryId: LauncherEntryId): LauncherReduction {
        val pinned = state.start.document.placements.firstOrNull { it.entryId == entryId }
        val proposal = if (pinned == null) {
            StartLayoutEditor.pin(state.start.document, entryId, state.catalog.entries)
        } else {
            StartLayoutEditor.unpin(state.start.document, pinned.tileId)
        } ?: return LauncherReduction(state)
        return applyCommitted(state, proposal)
    }

    private fun validateProposal(
        state: LauncherEngineState,
        proposal: LayoutProposal,
    ): LauncherReduction? {
        if (proposal.before != state.start.document) {
            return invalidProposal(state, "proposal.before does not match current document")
        }
        val profile = runCatching { WpReferenceProfiles.require(proposal.after.profileId) }.getOrNull()
            ?: return invalidProposal(state, "proposal profile is unknown")
        if (!StartDocumentValidator.isValid(proposal.after, state.catalog.entries, profile)) {
            return invalidProposal(state, "proposal document is invalid")
        }
        return null
    }

    private fun invalidProposal(state: LauncherEngineState, reason: String) = LauncherReduction(
        state,
        listOf(LauncherEffect.LogIncident(LauncherIncident.InvalidLayoutProposal(reason))),
    )
}

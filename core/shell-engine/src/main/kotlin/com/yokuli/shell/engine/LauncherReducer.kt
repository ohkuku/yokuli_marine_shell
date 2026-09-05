package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.contract.PinPolicy
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
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
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
    data object ShowDesktop : LauncherAction
    data object OpenSearch : LauncherAction
    data class UpdateSearchQuery(val query: String) : LauncherAction
    data object ShowRecents : LauncherAction
    data class ActivateTask(val taskId: InternalAppTaskId) : LauncherAction
    data class RestorePersistedDocument(val document: StartDocument?) : LauncherAction
    data object EnterSafeMode : LauncherAction
    data object ExitSafeMode : LauncherAction
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
    data class InsertionTargetChanged(val tileId: TileInstanceId, val insertionIndex: Int) : LauncherAction
    data class DropTile(val tileId: TileInstanceId) : LauncherAction
    data object CancelTileOperation : LauncherAction
    data class ResizeTile(val tileId: TileInstanceId) : LauncherAction
    data class MoveTileBy(val tileId: TileInstanceId, val columns: Int, val rows: Int) : LauncherAction
    data class OpenEntryContextMenu(val entryId: LauncherEntryId) : LauncherAction
    data object OpenAlphabetJump : LauncherAction
    data object DismissTransient : LauncherAction
    data class PinEntry(val entryId: LauncherEntryId) : LauncherAction
    data class UnpinTile(val tileId: TileInstanceId) : LauncherAction
    data class AcknowledgeStartReveal(val tileId: TileInstanceId) : LauncherAction
    data class TogglePin(val entryId: LauncherEntryId) : LauncherAction
    data object ResetStartDocument : LauncherAction
    data class PersistenceIncidentObserved(val incident: LauncherPersistenceIncident) : LauncherAction
}

enum class LauncherHaptic { SELECTION, LONG_PRESS, DROP }

sealed interface LauncherIncident {
    data class UnresolvedLaunchToken(val token: LaunchToken) : LauncherIncident
    data class InvalidLayoutProposal(val reason: String) : LauncherIncident
    data class CatalogRepair(val incident: StartRepairIncident) : LauncherIncident
    data class PersistenceMigration(val incident: LauncherPersistenceIncident) : LauncherIncident
    data class PersistenceFailure(val message: String) : LauncherIncident
}

sealed interface LauncherEffect {
    data class Launch(val token: LaunchToken) : LauncherEffect
    data class PersistDocument(val document: StartDocument) : LauncherEffect
    data class Haptic(val kind: LauncherHaptic) : LauncherEffect
    data class AccessibilityAnnouncement(val text: UiText) : LauncherEffect
    data class LogIncident(val incident: LauncherIncident) : LauncherEffect
    data class ScrollStartToReveal(val tileId: TileInstanceId) : LauncherEffect
}

fun ShellInput.toShellAction(): LauncherAction = when (this) {
    ShellInput.BACK -> LauncherAction.Back
    ShellInput.DESKTOP -> LauncherAction.ShowDesktop
    ShellInput.SEARCH -> LauncherAction.OpenSearch
    ShellInput.RECENTS -> LauncherAction.ShowRecents
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
    ): LauncherReduction {
        if (
            state.recoveryMode == LauncherRecoveryMode.RESTORING &&
            action !is LauncherAction.RestorePersistedDocument &&
            action !is LauncherAction.CatalogChanged &&
            action !is LauncherAction.PersistenceIncidentObserved &&
            action != LauncherAction.EnterSafeMode
        ) {
            return LauncherReduction(state)
        }
        if (
            state.recoveryMode == LauncherRecoveryMode.SAFE_MODE &&
            action !is LauncherAction.Open &&
            action !is LauncherAction.CatalogChanged &&
            action !is LauncherAction.RestorePersistedDocument &&
            action !is LauncherAction.Back &&
            action != LauncherAction.ShowDesktop &&
            action != LauncherAction.EnterSafeMode &&
            action != LauncherAction.ExitSafeMode &&
            action != LauncherAction.ResetStartDocument
        ) {
            return LauncherReduction(state)
        }
        return when (action) {
        LauncherAction.ShowStart -> LauncherReduction(
            state.copy(transient = null).navigateTo(
                ShellVisualSurface.Desktop,
                ShellTransitionTrigger.PAGE_SETTLED,
            ),
        )

        LauncherAction.ShowDesktop -> showDesktop(state)
        LauncherAction.OpenSearch -> openSearch(state)
        is LauncherAction.UpdateSearchQuery -> updateSearchQuery(state, action.query)
        LauncherAction.ShowRecents -> showRecents(state)
        is LauncherAction.ActivateTask -> activateTask(state, action.taskId)
        is LauncherAction.RestorePersistedDocument -> restorePersistedDocument(state, action.document, context)
        LauncherAction.EnterSafeMode -> enterSafeMode(state, context)
        LauncherAction.ExitSafeMode -> LauncherReduction(
            state.copy(recoveryMode = LauncherRecoveryMode.NORMAL, transitionRequest = null),
        )
        is LauncherAction.PersistenceIncidentObserved -> LauncherReduction(
            state,
            listOf(LauncherEffect.LogIncident(LauncherIncident.PersistenceMigration(action.incident))),
        )

        LauncherAction.ShowAllApps -> LauncherReduction(
            state.copy(transient = null).navigateTo(
                ShellVisualSurface.ModuleList,
                ShellTransitionTrigger.PAGE_SETTLED,
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
        is LauncherAction.InsertionTargetChanged -> updateInsertionTarget(state, action)
        is LauncherAction.DropTile -> dropTile(state, action.tileId)
        LauncherAction.CancelTileOperation -> cancelTileOperation(state)
        is LauncherAction.ResizeTile -> resizeTile(state, action.tileId)
        is LauncherAction.MoveTileBy -> moveTileBy(state, action)
        is LauncherAction.OpenEntryContextMenu -> openEntryContextMenu(state, action.entryId)
        LauncherAction.OpenAlphabetJump -> LauncherReduction(
            state.copy(transient = LauncherTransient.AlphabetJump),
        )
        LauncherAction.DismissTransient -> LauncherReduction(state.copy(transient = null))
        is LauncherAction.PinEntry -> pinEntry(state, action.entryId)
        is LauncherAction.UnpinTile -> unpinTile(state, action.tileId)
        is LauncherAction.AcknowledgeStartReveal -> acknowledgeReveal(state, action.tileId)
        is LauncherAction.TogglePin -> togglePin(state, action.entryId)
        LauncherAction.ResetStartDocument -> applyCommitted(
            state,
            LayoutProposal(state.start.document, context.defaultDocument, LayoutChangeReason.RESET),
        )
        }
    }

    private fun restorePersistedDocument(
        state: LauncherEngineState,
        source: StartDocument?,
        context: LauncherReducerContext,
    ): LauncherReduction {
        val candidate = source ?: context.defaultDocument
        val repaired = StartDocumentRepair.repair(
            candidate,
            state.catalog.entries,
            context.defaultDocument,
            context.profile,
        )
        val effects = buildList {
            repaired.incidents.forEach { incident ->
                add(LauncherEffect.LogIncident(LauncherIncident.CatalogRepair(incident)))
            }
            if (candidate != repaired.document) add(LauncherEffect.PersistDocument(repaired.document))
        }
        return LauncherReduction(
            state.copy(
                surface = ShellVisualSurface.Desktop,
                start = StartScreenState(repaired.document),
                transient = null,
                tasks = InternalTaskState(),
                recoveryMode = LauncherRecoveryMode.NORMAL,
                transitionRequest = null,
            ),
            effects,
        )
    }

    private fun enterSafeMode(
        state: LauncherEngineState,
        context: LauncherReducerContext,
    ): LauncherReduction = LauncherReduction(
        state.copy(
            surface = ShellVisualSurface.Desktop,
            start = StartScreenState(context.defaultDocument),
            tasks = InternalTaskState(),
            transient = null,
            recentsReturnSurface = null,
            recoveryMode = LauncherRecoveryMode.SAFE_MODE,
            transitionRequest = null,
        ),
    )

    private fun back(state: LauncherEngineState): LauncherReduction {
        state.transient?.let { return LauncherReduction(state.copy(transient = null)) }
        when (state.start.interaction) {
            is StartInteractionState.Dragging,
            is StartInteractionState.Settling -> return cancelTileOperation(state)
            is StartInteractionState.EditIdle -> return exitEdit(state)
            else -> Unit
        }
        state.start.activeTransaction?.let { return cancel(state) }
        return when (val surface = state.surface) {
            ShellVisualSurface.Desktop -> LauncherReduction(state)
            ShellVisualSurface.ModuleList -> LauncherReduction(
                state.navigateTo(ShellVisualSurface.Desktop, ShellTransitionTrigger.BACK),
            )
            is ShellVisualSurface.Search -> LauncherReduction(
                state.navigateTo(surface.returnSurface, ShellTransitionTrigger.BACK),
            )
            ShellVisualSurface.Recents -> LauncherReduction(
                state.navigateTo(
                    state.recentsReturnSurface ?: ShellVisualSurface.Desktop,
                    ShellTransitionTrigger.BACK,
                ).copy(
                    recentsReturnSurface = null,
                ),
            )
            is ShellVisualSurface.Module -> backWithinTask(state, surface.taskId)
        }
    }

    private fun backWithinTask(state: LauncherEngineState, taskId: InternalAppTaskId): LauncherReduction {
        val task = state.tasks.task(taskId)
            ?: return LauncherReduction(
                state.navigateTo(ShellVisualSurface.Desktop, ShellTransitionTrigger.BACK),
            )
        val previous = task.backStack.lastOrNull()
        if (previous == null) {
            return LauncherReduction(
                state.navigateTo(ShellVisualSurface.Desktop, ShellTransitionTrigger.BACK),
            )
        }
        val restored = task.copy(lastLaunchToken = previous, backStack = task.backStack.dropLast(1))
        val request = ShellTransitionResolver.resolve(
            state.surface,
            state.surface,
            ShellTransitionTrigger.MODULE_ROUTE_BACK,
        )
        return LauncherReduction(
            state.copy(
                tasks = InternalTaskState(state.tasks.tasks.map { if (it.taskId == taskId) restored else it }),
                transitionRequest = request,
            ),
        )
    }

    private fun showDesktop(state: LauncherEngineState): LauncherReduction {
        val cancelled = cancelForShellNavigation(state)
        return LauncherReduction(
            cancelled.navigateTo(
                ShellVisualSurface.Desktop,
                ShellTransitionTrigger.BRIDGE,
            ).copy(
                transient = null,
                recentsReturnSurface = null,
            ),
        )
    }

    private fun openSearch(state: LauncherEngineState): LauncherReduction {
        if (state.surface is ShellVisualSurface.Search) return LauncherReduction(state)
        val cancelled = cancelForShellNavigation(state)
        val search = ShellVisualSurface.Search(returnSurface = cancelled.surface)
        return LauncherReduction(
            cancelled.copy(transient = null).navigateTo(search, ShellTransitionTrigger.SEARCH_KEY),
        )
    }

    private fun updateSearchQuery(state: LauncherEngineState, query: String): LauncherReduction {
        val search = state.surface as? ShellVisualSurface.Search ?: return LauncherReduction(state)
        return LauncherReduction(state.copy(surface = search.copy(query = query)))
    }

    private fun showRecents(state: LauncherEngineState): LauncherReduction {
        if (state.surface == ShellVisualSurface.Recents) return LauncherReduction(state)
        val cancelled = cancelForShellNavigation(state)
        return LauncherReduction(
            cancelled.navigateTo(
                ShellVisualSurface.Recents,
                ShellTransitionTrigger.RECENTS_KEY,
            ).copy(
                transient = null,
                recentsReturnSurface = state.surface,
            ),
        )
    }

    private fun cancelForShellNavigation(state: LauncherEngineState): LauncherEngineState {
        val cancelled = when {
            state.start.interaction is StartInteractionState.Dragging ||
                state.start.interaction is StartInteractionState.Settling -> cancelTileOperation(state).state
            state.start.activeTransaction != null -> cancel(state).state
            else -> state
        }
        return cancelled.copy(
            start = cancelled.start.copy(interaction = StartInteractionState.Idle, reveal = null),
        )
    }

    private fun activateTask(state: LauncherEngineState, taskId: InternalAppTaskId): LauncherReduction {
        if (state.tasks.task(taskId) == null) return LauncherReduction(state)
        return LauncherReduction(
            state.navigateTo(
                ShellVisualSurface.Module(taskId),
                ShellTransitionTrigger.RECENT_TASK,
            ).copy(
                transient = null,
                recentsReturnSurface = null,
            ),
        )
    }

    private fun open(
        state: LauncherEngineState,
        action: LauncherAction.Open,
        resolution: LaunchResolution?,
    ): LauncherReduction = when (resolution) {
        is LaunchResolution.Internal -> {
            val taskId = InternalAppTaskId(resolution.appId.value)
            val existing = state.tasks.tasks.firstOrNull { it.appId == resolution.appId }
            val task = when {
                existing == null -> InternalAppTask(taskId, resolution.appId, resolution.token)
                existing.lastLaunchToken == resolution.token -> existing
                else -> existing.copy(
                    lastLaunchToken = resolution.token,
                    backStack = existing.backStack + existing.lastLaunchToken,
                )
            }
            val target = ShellVisualSurface.Module(taskId)
            if (state.surface == target && existing?.lastLaunchToken == resolution.token) {
                LauncherReduction(state)
            } else {
                val trigger = when (state.surface) {
                    is ShellVisualSurface.Search -> ShellTransitionTrigger.SEARCH_RESULT
                    ShellVisualSurface.ModuleList -> ShellTransitionTrigger.MODULE_LIST_ENTRY
                    ShellVisualSurface.Recents -> ShellTransitionTrigger.RECENT_TASK
                    target -> ShellTransitionTrigger.MODULE_ROUTE_FORWARD
                    else -> ShellTransitionTrigger.TILE
                }
                LauncherReduction(
                    state = state.navigateTo(target, trigger).copy(
                        tasks = InternalTaskState(
                            state.tasks.tasks.filterNot { it.appId == resolution.appId } + task,
                        ),
                        transient = null,
                    ),
                    effects = listOf(LauncherEffect.Launch(resolution.token)),
                )
            }
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
        val catalogChanged = catalog.revision != state.catalog.revision
        val sourceDocument = if (catalogChanged) {
            state.start.activeTransaction?.before ?: state.start.document
        } else state.start.document
        val profile = runCatching { WpReferenceProfiles.require(sourceDocument.profileId) }
            .getOrElse { WpReferenceProfiles.require(defaultDocument.profileId) }
        val fallback = defaultDocument.copy(profileId = profile.id)
        val repair = StartDocumentRepair.repair(sourceDocument, catalog.entries, fallback, profile)
        val installedApps = catalog.apps.map { it.appId }.toSet()
        val tasks = state.tasks.tasks.filter { it.appId in installedApps }
        val currentTaskInstalled = (state.surface as? ShellVisualSurface.Module)
            ?.let { current -> tasks.any { it.taskId == current.taskId } } ?: true
        val recentsReturnSurface = state.recentsReturnSurface?.let { returnSurface ->
            if (returnSurface is ShellVisualSurface.Module && tasks.none { it.taskId == returnSurface.taskId }) {
                ShellVisualSurface.Desktop
            } else returnSurface
        }
        val repairedState = state.copy(
            surface = if (currentTaskInstalled) state.surface else ShellVisualSurface.Desktop,
            start = state.start.copy(
                document = repair.document,
                interaction = if (catalogChanged) {
                    reconcileInteraction(state.start.interaction, repair.document)
                } else state.start.interaction,
                activeTransaction = if (catalogChanged) null else state.start.activeTransaction,
                undoStack = if (catalogChanged) emptyList() else state.start.undoStack,
                reveal = state.start.reveal?.takeIf { reveal ->
                    repair.document.placements.any { it.tileId == reveal.tileId }
                },
            ),
            allApps = AllAppsState(catalog.revision),
            tasks = InternalTaskState(tasks),
            catalog = catalog,
            transient = if (catalogChanged) null else state.transient,
            recentsReturnSurface = recentsReturnSurface,
        )
        val effects = buildList {
            repair.incidents.forEach { add(LauncherEffect.LogIncident(LauncherIncident.CatalogRepair(it))) }
            if (repair.document != state.start.document) add(LauncherEffect.PersistDocument(repair.document))
        }
        return LauncherReduction(repairedState, effects)
    }

    private fun reconcileInteraction(
        interaction: StartInteractionState,
        document: StartDocument,
    ): StartInteractionState = when (interaction) {
        is StartInteractionState.Dragging,
        is StartInteractionState.Settling -> StartInteractionState.Idle
        is StartInteractionState.EditIdle -> interaction.takeIf { selected ->
            selected.selectedTile == null || document.placements.any { it.tileId == selected.selectedTile }
        } ?: StartInteractionState.Idle
        else -> interaction
    }

    private fun enterEdit(state: LauncherEngineState, tileId: TileInstanceId): LauncherReduction {
        if (state.start.document.placements.none { it.tileId == tileId }) return LauncherReduction(state)
        return LauncherReduction(
            // A prior pin/reveal animation must not keep scrolling underneath editing input.
            state.copy(start = state.start.copy(interaction = StartInteractionState.EditIdle(tileId), reveal = null)),
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
        if (state.start.document.placements.none { it.tileId == action.tileId }) return LauncherReduction(state)
        val insertionIndex = AdaptiveTilePacker.insertionIndexOf(state.start.document, action.tileId)
        return LauncherReduction(
            state.copy(
                // Keep undo history, but dismiss its old notice so Back cancels this drag immediately.
                transient = null,
                start = state.start.copy(
                    reveal = null,
                    interaction = StartInteractionState.Dragging(
                        tileId = action.tileId,
                        pointerId = action.pointerId,
                        grabOffsetPx = action.grabOffsetPx,
                        insertionIndex = insertionIndex,
                        proposedLayout = state.start.document,
                    ),
                ),
            ),
        )
    }

    private fun updateInsertionTarget(
        state: LauncherEngineState,
        action: LauncherAction.InsertionTargetChanged,
    ): LauncherReduction {
        val dragging = state.start.interaction as? StartInteractionState.Dragging ?: return LauncherReduction(state)
        if (dragging.tileId != action.tileId) return LauncherReduction(state)
        val maximumIndex = (state.start.document.placements.size + state.start.document.spacers.size - 1).coerceAtLeast(0)
        val insertionIndex = action.insertionIndex.coerceIn(0, maximumIndex)
        if (dragging.insertionIndex == insertionIndex) return LauncherReduction(state)
        val proposed = AdaptiveTilePacker.insert(state.start.document, action.tileId, insertionIndex)
        return LauncherReduction(
            state.copy(
                start = state.start.copy(
                    interaction = dragging.copy(
                        insertionIndex = insertionIndex,
                        proposedLayout = proposed,
                    ),
                ),
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
            is StartInteractionState.Settling -> null
            else -> null
        }
        if (
            state.start.interaction !is StartInteractionState.Dragging &&
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
        val entryId = state.start.document.placements.firstOrNull { it.tileId == tileId }?.entryId
            ?: return LauncherReduction(state)
        val entry = state.catalog.entries.firstOrNull { it.entryId == entryId }
            ?: return LauncherReduction(state)
        if (entry.supportedSizes.size < 2) return LauncherReduction(state)
        val proposal = StartLayoutEditor.resize(state.start.document, tileId, state.catalog.entries)
            ?: return LauncherReduction(state)
        validateProposal(state, proposal)?.let { return it }
        val committed = applyCommitted(state, proposal)
        return committed.copy(
            state = committed.state.copy(
                start = committed.state.start.copy(interaction = StartInteractionState.EditIdle(tileId)),
            ),
            effects = committed.effects + LauncherEffect.Haptic(LauncherHaptic.SELECTION),
        )
    }

    private fun moveTileBy(state: LauncherEngineState, action: LauncherAction.MoveTileBy): LauncherReduction {
        if (state.start.document.placements.none { it.tileId == action.tileId }) return LauncherReduction(state)
        // Accessibility and touch insertion must index the same durable sequence, including spacers.
        val currentIndex = AdaptiveTilePacker.insertionIndexOf(state.start.document, action.tileId)
        val delta = when {
            action.columns < 0 || action.rows < 0 -> -1
            action.columns > 0 || action.rows > 0 -> 1
            else -> 0
        }
        if (delta == 0) return LauncherReduction(state)
        val maximumIndex = (state.start.document.placements.size + state.start.document.spacers.size - 1).coerceAtLeast(0)
        val after = AdaptiveTilePacker.insert(
            state.start.document,
            action.tileId,
            (currentIndex + delta).coerceIn(0, maximumIndex),
        )
        if (after == state.start.document) return LauncherReduction(state)
        val proposal = LayoutProposal(state.start.document, after, LayoutChangeReason.MOVE)
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
        val restoredTile = if (transaction.reason == LayoutChangeReason.UNPIN) {
            transaction.before.placements.firstOrNull { before ->
                transaction.after.placements.none { it.tileId == before.tileId }
            }?.tileId
        } else null
        return LauncherReduction(
            state.copy(
                start = state.start.copy(
                    document = document,
                    undoStack = state.start.undoStack.dropLast(1),
                    reveal = restoredTile?.let { StartReveal(it, transaction.id) },
                ),
                transient = null,
            ),
            buildList {
                add(LauncherEffect.PersistDocument(document))
                restoredTile?.let { add(LauncherEffect.ScrollStartToReveal(it)) }
            },
        )
    }

    private fun openEntryContextMenu(
        state: LauncherEngineState,
        entryId: LauncherEntryId,
    ): LauncherReduction {
        if (state.catalog.entries.none { it.entryId == entryId }) return LauncherReduction(state)
        return LauncherReduction(state.copy(transient = LauncherTransient.ContextMenu(entryId)))
    }

    private fun pinEntry(state: LauncherEngineState, entryId: LauncherEntryId): LauncherReduction {
        val entry = state.catalog.entries.firstOrNull { it.entryId == entryId }
            ?: return LauncherReduction(state.copy(transient = LauncherTransient.Notice(LauncherNotice.PIN_UNAVAILABLE)))
        if (entry.pinPolicy != PinPolicy.PINNABLE) {
            return LauncherReduction(state.copy(transient = LauncherTransient.Notice(LauncherNotice.PIN_UNAVAILABLE)))
        }
        if (state.start.document.placements.any { it.entryId == entryId }) {
            return LauncherReduction(state.copy(transient = LauncherTransient.Notice(LauncherNotice.ALREADY_PINNED)))
        }
        val proposal = StartLayoutEditor.pin(state.start.document, entryId, state.catalog.entries)
            ?: return LauncherReduction(state.copy(transient = LauncherTransient.Notice(LauncherNotice.LAYOUT_UNAVAILABLE)))
        val committed = applyCommitted(state, proposal)
        val transaction = committed.state.start.undoStack.last()
        val tileId = transaction.after.placements.single { placement ->
            transaction.before.placements.none { it.tileId == placement.tileId }
        }.tileId
        return committed.copy(
            state = committed.state.navigateTo(
                ShellVisualSurface.Desktop,
                ShellTransitionTrigger.PAGE_SETTLED,
            ).copy(
                start = committed.state.start.copy(
                    interaction = StartInteractionState.Idle,
                    reveal = StartReveal(tileId, transaction.id),
                ),
                transient = LauncherTransient.UndoLayout(transaction.id, LayoutChangeReason.PIN, entryId),
            ),
            effects = committed.effects + LauncherEffect.ScrollStartToReveal(tileId),
        )
    }

    private fun unpinTile(state: LauncherEngineState, tileId: TileInstanceId): LauncherReduction {
        val placement = state.start.document.placements.firstOrNull { it.tileId == tileId }
            ?: return LauncherReduction(state.copy(transient = LauncherTransient.Notice(LauncherNotice.LAYOUT_UNAVAILABLE)))
        val entry = state.catalog.entries.firstOrNull { it.entryId == placement.entryId }
        if (entry?.pinPolicy != PinPolicy.PINNABLE) {
            return LauncherReduction(state.copy(transient = LauncherTransient.Notice(LauncherNotice.PIN_UNAVAILABLE)))
        }
        val proposal = StartLayoutEditor.unpin(state.start.document, tileId)
            ?: return LauncherReduction(state.copy(transient = LauncherTransient.Notice(LauncherNotice.LAYOUT_UNAVAILABLE)))
        val committed = applyCommitted(state, proposal)
        val transaction = committed.state.start.undoStack.last()
        return committed.copy(
            state = committed.state.copy(
                start = committed.state.start.copy(interaction = StartInteractionState.Idle, reveal = null),
                transient = LauncherTransient.UndoLayout(
                    transaction.id,
                    LayoutChangeReason.UNPIN,
                    placement.entryId,
                ),
            ),
        )
    }

    private fun acknowledgeReveal(state: LauncherEngineState, tileId: TileInstanceId): LauncherReduction {
        if (state.start.reveal?.tileId != tileId) return LauncherReduction(state)
        return LauncherReduction(state.copy(start = state.start.copy(reveal = null)))
    }

    private fun togglePin(state: LauncherEngineState, entryId: LauncherEntryId): LauncherReduction {
        val pinned = state.start.document.placements.firstOrNull { it.entryId == entryId }
        return if (pinned == null) pinEntry(state, entryId) else unpinTile(state, pinned.tileId)
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

private fun LauncherEngineState.navigateTo(
    target: ShellVisualSurface,
    trigger: ShellTransitionTrigger,
): LauncherEngineState {
    val request = ShellTransitionResolver.resolve(surface, target, trigger)
    return copy(
        surface = target,
        transitionRequest = request,
    )
}

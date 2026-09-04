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
        is LauncherAction.TogglePin -> togglePin(state, action.entryId)
        LauncherAction.ResetStartDocument -> applyCommitted(
            state,
            LayoutProposal(state.start.document, context.defaultDocument, LayoutChangeReason.RESET),
        )
    }

    private fun back(state: LauncherEngineState): LauncherReduction {
        state.transient?.let { return LauncherReduction(state.copy(transient = null)) }
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
            start = state.start.copy(document = repair.document),
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

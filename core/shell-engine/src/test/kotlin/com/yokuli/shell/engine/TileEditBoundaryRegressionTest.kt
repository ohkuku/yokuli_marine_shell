package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.LayoutChangeReason
import com.yokuli.shell.engine.layout.Spacer
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TileEditBoundaryRegressionTest {
    private val profile = WpReferenceProfiles.PHONE_PORTRAIT_4COL
    private val reducer = DefaultLauncherReducer()
    private val entries = listOf("a", "b", "c").map { id ->
        LauncherEntryDescriptor(
            entryId = LauncherEntryId(id), appId = LauncherAppId(id), launchToken = LaunchToken("$id.root"),
            defaultSize = MarineTileSize.ICON_1X1, supportedSizes = MarineTileSize.entries, pinPolicy = PinPolicy.PINNABLE,
        )
    }
    private val catalog = LauncherCatalogSnapshot(1, entries.map { LauncherAppDescriptor(it.appId, it.entryId) }, entries)
    private val document = StartDocument(
        schemaVersion = 2, profileId = profile.id, defaultLayoutVersion = 2,
        placements = listOf("a", "b", "c").mapIndexed { index, id ->
            TilePlacement(TileInstanceId("tile-$id"), LauncherEntryId(id), MarineTileSize.ICON_1X1, index * 2048L)
        },
        spacers = listOf(Spacer(TileInstanceId("gap"), MarineTileSize.ICON_1X1, 1024L)),
    )
    private val context = LauncherReducerContext(document, profile)
    private val tileB = TileInstanceId("tile-b")
    private fun initial() = LauncherEngineState(
        ShellVisualSurface.Desktop, StartScreenState(document, StartInteractionState.EditIdle(tileB)),
        AllAppsState(catalog.revision), InternalTaskState(), catalog,
    )
    private fun apply(state: LauncherEngineState, action: LauncherAction) = reducer.reduce(state, action, context)
    private fun order(doc: StartDocument): List<String> =
        (doc.placements.map { it.rank to it.tileId.value } + doc.spacers.map { it.rank to it.spacerId.value })
            .sortedBy { it.first }.map { it.second }

    @Test fun accessibleForwardMoveUsesTheCombinedRankSequenceAndCanUndo() {
        val moved = apply(initial(), LauncherAction.MoveTileBy(tileB, 1, 0))
        assertEquals(listOf("tile-a", "gap", "tile-c", "tile-b"), order(moved.state.start.document))
        assertEquals(1, moved.state.start.undoStack.size)
        assertEquals(StartInteractionState.EditIdle(tileB), moved.state.start.interaction)
        assertTrue(moved.effects.any { it is LauncherEffect.PersistDocument })
        val undone = apply(moved.state, LauncherAction.UndoLayout)
        assertEquals(document, undone.state.start.document)
        assertTrue(undone.state.start.undoStack.isEmpty())
    }

    @Test fun accessibleBackwardMoveCrossesOneSpacerInsteadOfJumpingTwoItems() {
        val moved = apply(initial(), LauncherAction.MoveTileBy(tileB, 0, -1))
        assertEquals(listOf("tile-a", "tile-b", "gap", "tile-c"), order(moved.state.start.document))
        assertEquals(document.spacers.single().size, moved.state.start.document.spacers.single().size)
    }

    @Test fun zeroAndBoundaryMovesHaveNoPersistenceOrUndoEffects() {
        val state = initial()
        for (action in listOf(
            LauncherAction.MoveTileBy(tileB, 0, 0),
            LauncherAction.MoveTileBy(TileInstanceId("tile-a"), -1, 0),
            LauncherAction.MoveTileBy(TileInstanceId("tile-c"), 1, 0),
        )) {
            val noOp = apply(state, action)
            assertSame(state, noOp.state)
            assertTrue(noOp.effects.isEmpty())
        }
    }

    @Test fun acceptedDragDismissesOldFeedbackAndRevealSoOneBackReallyCancels() {
        val state = initial().let {
            it.copy(
                transient = LauncherTransient.UndoLayout("prior-pin", LayoutChangeReason.PIN, LauncherEntryId("b")),
                start = it.start.copy(reveal = StartReveal(tileB, "prior-pin")),
            )
        }
        val started = apply(state, LauncherAction.BeginTileDrag(tileB, 7, ShellOffset(10f, 12f))).state
        assertNull(started.transient)
        assertNull(started.start.reveal)
        assertEquals(state.start.undoStack, started.start.undoStack)
        val preview = apply(started, LauncherAction.InsertionTargetChanged(tileB, 0)).state
        val cancelled = apply(preview, LauncherAction.Back).state
        assertEquals(document, cancelled.start.document)
        assertEquals(StartInteractionState.EditIdle(tileB), cancelled.start.interaction)
        val lateDrop = apply(cancelled, LauncherAction.DropTile(tileB))
        assertSame(cancelled, lateDrop.state)
        assertTrue(lateDrop.effects.isEmpty())
    }

    @Test fun enteringEditStopsARevealWithoutChangingTheCommittedDocument() {
        val state = initial().let { it.copy(start = it.start.copy(reveal = StartReveal(tileB, "reveal"))) }
        val edited = apply(state, LauncherAction.EnterStartEdit(tileB)).state
        assertNull(edited.start.reveal)
        assertEquals(document, edited.start.document)
        assertEquals(StartInteractionState.EditIdle(tileB), edited.start.interaction)
    }
}

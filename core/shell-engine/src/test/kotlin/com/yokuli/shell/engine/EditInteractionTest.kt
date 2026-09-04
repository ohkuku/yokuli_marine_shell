package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.interaction.DragCellHysteresis
import com.yokuli.shell.engine.interaction.EdgeAutoScrollPolicy
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditInteractionTest {
    private val profile = WpReferenceProfiles.PHONE_PORTRAIT_4COL
    private val reducer = DefaultLauncherReducer()
    private val entries = listOf(descriptor("a"), descriptor("b"), descriptor("c"))
    private val catalog = LauncherCatalogSnapshot(
        revision = 1,
        apps = entries.map { LauncherAppDescriptor(it.appId, it.entryId) },
        entries = entries,
    )
    private val document = StartDocument(
        schemaVersion = 1,
        profileId = profile.id,
        defaultLayoutVersion = 1,
        placements = listOf(
            placement("a", GridCell(0, 0)),
            placement("b", GridCell(1, 0)),
            placement("c", GridCell(3, 4)),
        ),
    )
    private val context = LauncherReducerContext(document, profile)

    @Test
    fun grabOffsetIsPreserved() {
        val grab = ShellOffset(17.5f, 33f)
        val started = reduce(
            initial(),
            LauncherAction.BeginTileDrag(TileInstanceId("tile-a"), pointerId = 42, grabOffsetPx = grab),
        )

        val dragging = started.start.interaction as StartInteractionState.Dragging
        assertEquals(42, dragging.pointerId)
        assertEquals(grab, dragging.grabOffsetPx)
        assertEquals(0, dragging.insertionIndex)
    }

    @Test
    fun neighborMovesBeforeDrop() {
        val started = startDrag()
        val preview = reduce(
            started,
            LauncherAction.InsertionTargetChanged(TileInstanceId("tile-a"), insertionIndex = 1),
        )

        val dragging = preview.start.interaction as StartInteractionState.Dragging
        assertEquals(document, preview.start.document)
        assertEquals(listOf("b", "a", "c"), dragging.proposedLayout.placements.map { it.entryId.value })
        assertEquals(GridCell(1, 0), dragging.proposedLayout.cell("a"))
    }

    @Test
    fun unchangedInsertionTargetDoesNotPublishNewEngineState() {
        val started = startDrag()
        val dragging = started.start.interaction as StartInteractionState.Dragging

        val unchanged = reduce(started, LauncherAction.InsertionTargetChanged(dragging.tileId, dragging.insertionIndex))

        assertTrue(unchanged === started)
    }

    @Test
    fun cellHysteresisPreventsThrash() {
        val policy = DragCellHysteresis()
        val origin = GridCell(0, 0)

        assertEquals(origin, policy.resolve(origin, ShellOffset(59f, 0f), 100f, origin))
        val next = policy.resolve(origin, ShellOffset(61f, 0f), 100f, origin)
        assertEquals(GridCell(1, 0), next)
        assertEquals(next, policy.resolve(origin, ShellOffset(45f, 0f), 100f, next))
        assertEquals(origin, policy.resolve(origin, ShellOffset(39f, 0f), 100f, next))
    }

    @Test
    fun edgeAutoScrollPolicyRemainsRendererInputOnly() {
        val policy = EdgeAutoScrollPolicy(activationZonePx = 48f, maximumSpeedPxPerSecond = 300f)
        assertEquals(0f, policy.velocity(200f, 400f))
        assertTrue(policy.velocity(390f, 400f) > 0f)
    }

    @Test
    fun invalidDropReturnsOrigin() {
        val preview = reduce(
            startDrag(),
            LauncherAction.InsertionTargetChanged(TileInstanceId("tile-a"), insertionIndex = 0),
        )
        val dropped = reduce(preview, LauncherAction.DropTile(TileInstanceId("tile-a")))

        assertEquals(document, dropped.start.document)
        assertTrue(dropped.start.interaction is StartInteractionState.EditIdle)
        assertTrue(dropped.start.activeTransaction == null)
    }

    @Test
    fun pointerCancelRestoresCommittedDocument() {
        val preview = reduce(
            startDrag(),
            LauncherAction.InsertionTargetChanged(TileInstanceId("tile-a"), insertionIndex = 1),
        )
        val cancelled = reduce(preview, LauncherAction.CancelTileOperation)

        assertEquals(document, cancelled.start.document)
        assertTrue(cancelled.start.interaction is StartInteractionState.EditIdle)
    }

    @Test
    fun catalogChangeCancelsDragSafely() {
        val changed = reduce(startDrag(), LauncherAction.CatalogChanged(catalog.copy(revision = 2)))

        assertEquals(document, changed.start.document)
        assertEquals(StartInteractionState.Idle, changed.start.interaction)
    }

    @Test
    fun sixSizeResizeCycleIsExact() {
        val first = resizeAndCommit(initial())
        val second = resizeAndCommit(first)
        val third = resizeAndCommit(second)
        val fourth = resizeAndCommit(third)
        val fifth = resizeAndCommit(fourth)
        val sixth = resizeAndCommit(fifth)

        assertEquals(MarineTileSize.COMPACT_2X1, first.start.document.size("a"))
        assertEquals(MarineTileSize.STANDARD_2X2, second.start.document.size("a"))
        assertEquals(MarineTileSize.WIDE_4X2, third.start.document.size("a"))
        assertEquals(MarineTileSize.TALL_2X4, fourth.start.document.size("a"))
        assertEquals(MarineTileSize.LARGE_4X4, fifth.start.document.size("a"))
        assertEquals(MarineTileSize.ICON_1X1, sixth.start.document.size("a"))
    }

    @Test
    fun resizeCanBeCancelledBeforeCommit() {
        val resizing = reduce(initial(), LauncherAction.ResizeTile(TileInstanceId("tile-a")))
        assertTrue(resizing.start.interaction is StartInteractionState.Resizing)
        assertEquals(document, resizing.start.document)

        val cancelled = reduce(resizing, LauncherAction.CancelTileOperation)
        assertEquals(document, cancelled.start.document)
        assertEquals(StartInteractionState.EditIdle(TileInstanceId("tile-a")), cancelled.start.interaction)
    }

    private fun startDrag() = reduce(
        initial(),
        LauncherAction.BeginTileDrag(TileInstanceId("tile-a"), 7, ShellOffset(10f, 12f)),
    )

    private fun resizeAndCommit(state: LauncherEngineState): LauncherEngineState = reduce(
        reduce(state, LauncherAction.ResizeTile(TileInstanceId("tile-a"))),
        LauncherAction.CommitTileResize,
    )

    private fun reduce(state: LauncherEngineState, action: LauncherAction): LauncherEngineState =
        reducer.reduce(state, action, context).state

    private fun initial() = LauncherEngineState(
        surface = ShellVisualSurface.Desktop,
        start = StartScreenState(document, interaction = StartInteractionState.EditIdle(TileInstanceId("tile-a"))),
        allApps = AllAppsState(catalog.revision),
        tasks = InternalTaskState(),
        catalog = catalog,
    )

    private fun descriptor(id: String): LauncherEntryDescriptor {
        val appId = LauncherAppId(id)
        return LauncherEntryDescriptor(
            entryId = LauncherEntryId(id),
            appId = appId,
            launchToken = LaunchToken("$id.root"),
            defaultSize = MarineTileSize.ICON_1X1,
            supportedSizes = MarineTileSize.entries,
            pinPolicy = PinPolicy.PINNABLE,
        )
    }

    private fun placement(id: String, cell: GridCell) = TilePlacement(
        TileInstanceId("tile-$id"), LauncherEntryId(id), MarineTileSize.ICON_1X1,
        (cell.row * 4L + cell.column) * 1024L,
    )

    private fun StartDocument.cell(id: String) = AdaptiveTilePacker.pack(this, 4).tiles.single { it.entry.entryId.value == id }.cell
    private fun StartDocument.size(id: String) = placements.single { it.entryId.value == id }.size
}

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
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.LayoutChangeReason
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinUnpinInteractionTest {
    private val chart = descriptor("chart", MarineTileSize.WIDE_4X2)
    private val settings = descriptor("settings", MarineTileSize.ICON_1X1)
    private val extra = descriptor("extra", MarineTileSize.STANDARD_2X2)
    private val catalog = snapshot(1, listOf(chart, settings, extra))
    private val document = StartDocument(
        schemaVersion = 1,
        profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
        defaultLayoutVersion = 1,
        placements = listOf(
            TilePlacement(TileInstanceId("tile-chart"), chart.entryId, chart.defaultSize, GridCell(0, 0)),
            TilePlacement(TileInstanceId("tile-settings"), settings.entryId, settings.defaultSize, GridCell(0, 2)),
        ),
    )
    private val reducer = DefaultLauncherReducer()

    @Test
    fun pinOpensContextMenuFirst() {
        val initial = state(surface = ShellVisualSurface.ModuleList)
        val result = reduce(initial, LauncherAction.OpenEntryContextMenu(extra.entryId))

        assertEquals(document, result.state.start.document)
        assertEquals(LauncherTransient.ContextMenu(extra.entryId), result.state.transient)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun pinReturnsToStartAndRequestsReveal() {
        val result = reduce(state(surface = ShellVisualSurface.ModuleList), LauncherAction.PinEntry(extra.entryId))
        val tile = result.state.start.document.placements.single { it.entryId == extra.entryId }

        assertEquals(ShellVisualSurface.Desktop, result.state.surface)
        assertEquals(GridCell(0, 3), tile.cell)
        assertEquals(tile.tileId, result.state.start.reveal?.tileId)
        assertTrue(result.effects.any { it == LauncherEffect.ScrollStartToReveal(tile.tileId) })
        assertEquals(LayoutChangeReason.PIN, (result.state.transient as LauncherTransient.UndoLayout).reason)
    }

    @Test
    fun pinDoesNotDuplicateEntry() {
        val result = reduce(state(), LauncherAction.PinEntry(chart.entryId))

        assertEquals(document, result.state.start.document)
        assertEquals(LauncherNotice.ALREADY_PINNED, (result.state.transient as LauncherTransient.Notice).notice)
    }

    @Test
    fun unpinDoesNotDeleteApp() {
        val result = reduce(state(), LauncherAction.UnpinTile(TileInstanceId("tile-settings")))

        assertFalse(result.state.start.document.placements.any { it.entryId == settings.entryId })
        assertTrue(result.state.catalog.entries.any { it.entryId == settings.entryId })
        assertEquals(LayoutChangeReason.UNPIN, (result.state.transient as LauncherTransient.UndoLayout).reason)
    }

    @Test
    fun undoPinRestoresDocument() {
        val pinned = reduce(state(), LauncherAction.PinEntry(extra.entryId)).state
        val undone = reduce(pinned, LauncherAction.UndoLayout).state

        assertEquals(document, undone.start.document)
        assertNull(undone.transient)
    }

    @Test
    fun undoUnpinRestoresDocument() {
        val unpinned = reduce(state(), LauncherAction.UnpinTile(TileInstanceId("tile-settings"))).state
        val undone = reduce(unpinned, LauncherAction.UndoLayout).state

        assertEquals(document, undone.start.document)
        assertEquals(TileInstanceId("tile-settings"), undone.start.reveal?.tileId)
    }

    @Test
    fun catalogAdditionDoesNotAutoPin() {
        val original = state(catalog = snapshot(1, listOf(chart, settings)))
        val expanded = snapshot(2, listOf(chart, settings, extra))
        val result = reduce(original, LauncherAction.CatalogChanged(expanded))

        assertEquals(document, result.state.start.document)
        assertFalse(result.state.start.document.placements.any { it.entryId == extra.entryId })
    }

    @Test
    fun catalogRemovalPreservesUnrelatedCoordinates() {
        val removed = snapshot(2, listOf(settings, extra))
        val result = reduce(state(), LauncherAction.CatalogChanged(removed))

        assertFalse(result.state.start.document.placements.any { it.entryId == chart.entryId })
        assertEquals(GridCell(0, 2), result.state.start.document.placements.single().cell)
    }

    @Test
    fun catalogChangeCancelsPendingTransactionBeforeReconciliation() {
        val proposal = com.yokuli.shell.engine.layout.StartLayoutEditor.unpin(
            document,
            TileInstanceId("tile-settings"),
        )!!
        val pending = reduce(state(), LauncherAction.BeginLayoutTransaction(proposal)).state

        val changed = reduce(pending, LauncherAction.CatalogChanged(catalog.copy(revision = 2))).state

        assertEquals(document, changed.start.document)
        assertNull(changed.start.activeTransaction)
        assertTrue(changed.start.undoStack.isEmpty())
    }

    private fun reduce(state: LauncherEngineState, action: LauncherAction) = reducer.reduce(
        state,
        action,
        LauncherReducerContext(document, WpReferenceProfiles.PHONE_PORTRAIT_4COL),
    )

    private fun state(
        surface: ShellVisualSurface = ShellVisualSurface.Desktop,
        catalog: LauncherCatalogSnapshot = this.catalog,
    ) = LauncherEngineState(
        surface = surface,
        start = StartScreenState(document),
        allApps = AllAppsState(catalog.revision),
        tasks = InternalTaskState(),
        catalog = catalog,
    )

    private fun descriptor(id: String, size: MarineTileSize) = LauncherEntryDescriptor(
        entryId = LauncherEntryId(id),
        appId = LauncherAppId(id),
        launchToken = LaunchToken("$id.root"),
        defaultSize = size,
        supportedSizes = MarineTileSize.entries,
        pinPolicy = PinPolicy.PINNABLE,
    )

    private fun snapshot(revision: Long, entries: List<LauncherEntryDescriptor>) = LauncherCatalogSnapshot(
        revision = revision,
        apps = entries.map { LauncherAppDescriptor(it.appId, it.entryId) },
        entries = entries,
    )
}

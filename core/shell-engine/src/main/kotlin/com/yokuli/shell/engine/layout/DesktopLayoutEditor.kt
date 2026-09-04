package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import java.util.UUID

object DesktopLayoutEditor {
    fun resize(
        document: DesktopDocument,
        tileId: TileInstanceId,
        entries: Collection<LauncherEntryDescriptor>,
    ): LayoutTransaction? {
        val current = document.placements.firstOrNull { it.tileId == tileId } ?: return null
        val entry = entries.firstOrNull { it.entryId == current.entryId } ?: return null
        val cycle = entry.supportedSizes
        val next = cycle[(cycle.indexOf(current.size) + 1).mod(cycle.size)]
        val proposed = document.copy(
            placements = document.placements.map { if (it.tileId == tileId) it.copy(size = next) else it },
        )
        val repaired = DesktopDocumentRepair.repair(proposed, entries, document).document
        return transaction(document, repaired, LayoutChangeReason.RESIZE)
    }

    fun unpin(document: DesktopDocument, tileId: TileInstanceId): LayoutTransaction? {
        if (document.placements.none { it.tileId == tileId }) return null
        return transaction(
            document,
            document.copy(placements = document.placements.filterNot { it.tileId == tileId }),
            LayoutChangeReason.UNPIN,
        )
    }

    fun pin(
        document: DesktopDocument,
        entryId: LauncherEntryId,
        entries: Collection<LauncherEntryDescriptor>,
    ): LayoutTransaction? {
        if (document.placements.any { it.entryId == entryId }) return null
        val entry = entries.firstOrNull { it.entryId == entryId } ?: return null
        val candidate = TilePlacement(
            tileId = TileInstanceId("tile-${entryId.value}"),
            entryId = entryId,
            size = entry.defaultSize,
            cell = GridCell(0, document.placements.maxOfOrNull { it.cell.row + it.size.rows } ?: 0),
        )
        val repaired = DesktopDocumentRepair.repair(
            document.copy(placements = document.placements + candidate),
            entries,
            document,
        ).document
        return transaction(document, repaired, LayoutChangeReason.PIN)
    }

    fun move(
        document: DesktopDocument,
        tileId: TileInstanceId,
        target: GridCell,
        entries: Collection<LauncherEntryDescriptor>,
    ): LayoutTransaction? {
        val moving = document.placements.firstOrNull { it.tileId == tileId } ?: return null
        if (target.column < 0 || target.row < 0 || target.column + moving.size.columns > document.columns) return null
        val proposed = document.copy(
            placements = listOf(moving.copy(cell = target)) + document.placements.filterNot { it.tileId == tileId },
        )
        val repaired = DesktopDocumentRepair.repair(proposed, entries, document).document
        val ordered = document.placements.mapNotNull { original ->
            repaired.placements.firstOrNull { it.tileId == original.tileId }
        }
        return transaction(document, repaired.copy(placements = ordered), LayoutChangeReason.MOVE)
    }

    private fun transaction(before: DesktopDocument, after: DesktopDocument, reason: LayoutChangeReason) =
        LayoutTransaction(UUID.randomUUID().toString(), before, after, reason)
}

package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import java.util.UUID

/**
 * 中文：这是 Stage 3 对旧 UI 的兼容编辑入口；Stage 4 会把提交、取消和 Undo 收入 Reducer。
 * English: This keeps the existing UI working in Stage 3; Stage 4 moves commit, cancel, and undo into the reducer.
 */
object StartLayoutEditor {
    fun resize(
        document: StartDocument,
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
        val profile = WpReferenceProfiles.require(document.profileId)
        val repaired = StartDocumentRepair.repair(proposed, entries, document, profile).document
        return transaction(document, repaired, LayoutChangeReason.RESIZE)
    }

    fun unpin(document: StartDocument, tileId: TileInstanceId): LayoutTransaction? {
        if (document.placements.none { it.tileId == tileId }) return null
        return transaction(
            document,
            document.copy(placements = document.placements.filterNot { it.tileId == tileId }),
            LayoutChangeReason.UNPIN,
        )
    }

    fun pin(
        document: StartDocument,
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
        val profile = WpReferenceProfiles.require(document.profileId)
        val repaired = StartDocumentRepair.repair(
            document.copy(placements = document.placements + candidate),
            entries,
            document,
            profile,
        ).document
        return transaction(document, repaired, LayoutChangeReason.PIN)
    }

    fun move(
        document: StartDocument,
        tileId: TileInstanceId,
        target: GridCell,
        entries: Collection<LauncherEntryDescriptor>,
    ): LayoutTransaction? {
        val moving = document.placements.firstOrNull { it.tileId == tileId } ?: return null
        val profile = WpReferenceProfiles.require(document.profileId)
        if (target.column < 0 || target.row < 0 || target.column + moving.size.columns > profile.columnCount) {
            return null
        }
        val proposed = document.copy(
            placements = listOf(moving.copy(cell = target)) + document.placements.filterNot { it.tileId == tileId },
        )
        val repaired = StartDocumentRepair.repair(proposed, entries, document, profile).document
        val ordered = document.placements.mapNotNull { original ->
            repaired.placements.firstOrNull { it.tileId == original.tileId }
        }
        return transaction(document, repaired.copy(placements = ordered), LayoutChangeReason.MOVE)
    }

    private fun transaction(before: StartDocument, after: StartDocument, reason: LayoutChangeReason) =
        LayoutTransaction(UUID.randomUUID().toString(), before, after, reason)
}

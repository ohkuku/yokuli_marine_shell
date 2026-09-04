package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.WpReferenceProfiles

/**
 * 中文：这是 Stage 3 对旧 UI 的兼容编辑入口；Stage 4 会把提交、取消和 Undo 收入 Reducer。
 * English: This keeps the existing UI working in Stage 3; Stage 4 moves commit, cancel, and undo into the reducer.
 */
object StartLayoutEditor {
    fun resize(
        document: StartDocument,
        tileId: TileInstanceId,
        entries: Collection<LauncherEntryDescriptor>,
    ): LayoutProposal? {
        val current = document.placements.firstOrNull { it.tileId == tileId } ?: return null
        val entry = entries.firstOrNull { it.entryId == current.entryId } ?: return null
        val cycle = entry.supportedSizes
        val next = cycle[(cycle.indexOf(current.size) + 1).mod(cycle.size)]
        return transaction(
            document,
            document.copy(placements = document.placements.map { if (it.tileId == tileId) it.copy(size = next) else it }),
            LayoutChangeReason.RESIZE,
        )
    }

    fun unpin(document: StartDocument, tileId: TileInstanceId): LayoutProposal? {
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
    ): LayoutProposal? {
        if (document.placements.any { it.entryId == entryId }) return null
        val entry = entries.firstOrNull { it.entryId == entryId } ?: return null
        val candidate = TilePlacement(
            tileId = TileInstanceId("tile-${entryId.value}"),
            entryId = entryId,
            size = entry.defaultSize,
            rank = (document.placements.map { it.rank } + document.spacers.map { it.rank }).maxOrNull()?.plus(1024L) ?: 0L,
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
    ): LayoutProposal? {
        document.placements.firstOrNull { it.tileId == tileId } ?: return null
        val profile = WpReferenceProfiles.require(document.profileId)
        if (target.column < 0 || target.row < 0 || target.column >= profile.columnCount) return null
        val insertionIndex = AdaptiveTilePacker.insertionIndexForCell(document, profile.columnCount, target)
        val after = AdaptiveTilePacker.insert(document, tileId, insertionIndex)
        return transaction(document, after, LayoutChangeReason.MOVE)
    }

    private fun transaction(before: StartDocument, after: StartDocument, reason: LayoutChangeReason) =
        LayoutProposal(before, after, reason)
}

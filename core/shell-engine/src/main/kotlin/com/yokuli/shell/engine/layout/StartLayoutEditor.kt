package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
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
        val profile = WpReferenceProfiles.require(document.profileId)
        val resized = TileLayoutPreview.place(document, current.copy(size = next))
        return transaction(document, resized, LayoutChangeReason.RESIZE)
    }

    fun unpin(document: StartDocument, tileId: TileInstanceId): LayoutProposal? {
        if (document.placements.none { it.tileId == tileId }) return null
        return transaction(
            document,
            TileLayoutPreview.remove(document, tileId),
            LayoutChangeReason.UNPIN,
        )
    }

    fun pin(
        document: StartDocument,
        entryId: LauncherEntryId,
        entries: Collection<LauncherEntryDescriptor>,
        size: MarineTileSize? = null,
    ): LayoutProposal? {
        val entry = entries.firstOrNull { it.entryId == entryId } ?: return null
        // 同一内容只固定一次；同一应用的其他内容互不影响。
        val existing = document.placements.firstOrNull { it.entryId == entryId }
        val requestedSize = size ?: existing?.size?.takeIf { it in entry.supportedSizes } ?: entry.defaultSize
        if (requestedSize !in entry.supportedSizes) return null
        val profile = WpReferenceProfiles.require(document.profileId)
        val candidate = existing?.copy(
            entryId = entryId,
            size = requestedSize,
            preferredCell = existing.preferredCell?.let { it.copy(column = it.column.coerceIn(0, profile.columnCount - requestedSize.columns)) },
        ) ?: TilePlacement(
            tileId = TileInstanceId("tile-${entryId.value}"),
            entryId = entryId,
            size = requestedSize,
            binding = com.yokuli.shell.contract.TileBinding("yokuli", com.yokuli.shell.contract.TileBindingKind.APP, entryId.value),
            rank = (document.placements.map { it.rank } + document.spacers.map { it.rank }).maxOrNull()?.plus(1024L) ?: 0L,
        )
        val repaired = StartDocumentRepair.repair(
            document.copy(placements = document.placements.filterNot { it.tileId == candidate.tileId } + candidate),
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
        val after = AdaptiveTilePacker.place(document, tileId, target, profile.columnCount)
        return transaction(document, after, LayoutChangeReason.MOVE)
    }

    private fun transaction(before: StartDocument, after: StartDocument, reason: LayoutChangeReason) =
        LayoutProposal(before, after, reason)
}

package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import kotlin.math.abs

/** 中文：由引擎串行队列在同一次 DataStore transform 中调用。 */
internal object TileCommitPolicy {
    private const val MAX_RECEIPTS = 32
    data class Decision(val document: StartDocument, val result: TileCommitResult)

    fun save(document: StartDocument, request: TileCommitRequest, entries: Collection<LauncherEntryDescriptor>): Decision {
        receipt(document, request.requestId)?.let { return Decision(document, it) }
        if (request.requestId.isBlank() || request.requestId.length > 128 || !request.binding.isStructurallyValid) return failed(document, "Invalid tile identity")
        val existing = document.placements.firstOrNull { it.tileId == request.tileId }
        if (request.expectedRevision == null && existing != null || request.expectedRevision != null && existing?.revision != request.expectedRevision) {
            return Decision(document, TileCommitResult.Conflict(existing))
        }
        document.placements.firstOrNull { it.tileId != request.tileId && it.effectiveContentKey == request.binding.contentKey }
            ?.let { return Decision(document, TileCommitResult.AlreadyPinned(it.tileId)) }
        val descriptor = entries.firstOrNull { it.entryId == request.binding.startEntryId }
        val preservedUnknown = existing?.binding == request.binding
        if (!preservedUnknown && request.binding.kind == TileBindingKind.APP && descriptor == null) return failed(document, "Unknown application")
        if (!preservedUnknown && !supportedBinding(request.binding)) return failed(document, "Unsupported tile content")
        val allowedSizes = if (request.binding.kind == TileBindingKind.APP) descriptor?.supportedSizes
            ?: listOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2)
            else listOf(MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2)
        if (request.size !in allowedSizes && request.size != existing?.size) return failed(document, "Unsupported tile size")
        val allowedStyles = if (request.binding.kind == TileBindingKind.APP) setOf("default", "summary", "static") else setOf("default", "simple", "detail")
        if (request.presentation.style !in allowedStyles && request.presentation != existing?.presentation) return failed(document, "Unsupported tile presentation")
        if ((request.presentation.legacyMode?.length ?: 0) > 64 || request.presentation.intervalSeconds?.let { it !in 1..120 } == true) return failed(document, "Invalid tile presentation")
        val columns = WpReferenceProfiles.require(document.profileId).columnCount
        val updated = existing?.copy(
            entryId = request.binding.startEntryId, binding = request.binding, presentation = request.presentation,
            size = request.size, preferredCell = existing.preferredCell,
        ) ?: TileDocumentEntry(
            request.tileId, request.binding.startEntryId, request.size,
            (document.placements.map { it.rank } + document.spacers.map { it.rank }).maxOrNull()?.plus(1024) ?: 0,
            binding = request.binding, presentation = request.presentation,
        )
        val revision = if (updated == existing) existing?.revision ?: 0 else document.revision + 1
        val geometryChanged = existing != null && existing.size != updated.size
        val positioned = TileLayoutPreview.place(document, updated)
        val result = positioned.placements.first { it.tileId == updated.tileId }.copy(revision = revision)
        val after = positioned.copy(placements = positioned.placements.map { if (it.tileId == result.tileId) result else it },
            revision = if (updated == existing) document.revision else document.revision + 1)
        val previousCell = existing?.let { AdaptiveTilePacker.pack(document, columns).tile(it.tileId)?.cell }
        return committed(after, request.requestId, result.tileId, revision, "save",
            geometryChanged && result.preferredCell != previousCell)
    }

    fun remove(document: StartDocument, requestId: String, tileId: TileInstanceId, expectedRevision: Long): Decision {
        if (requestId.isBlank() || requestId.length > 128) return failed(document, "Invalid request identity")
        receipt(document, requestId)?.let { return Decision(document, it) }
        val entry = document.placements.firstOrNull { it.tileId == tileId }
        if (entry == null || entry.revision != expectedRevision) return Decision(document, TileCommitResult.Conflict(entry))
        val cell = AdaptiveTilePacker.pack(document, WpReferenceProfiles.require(document.profileId).columnCount).tile(tileId)?.cell
        val after = TileLayoutPreview.remove(document, tileId).copy(revision = document.revision + 1,
            removedTiles = (document.removedTiles + TileRemovalRecord(requestId, entry.copy(preferredCell = cell ?: entry.preferredCell), document.revision + 1)).takeLast(MAX_RECEIPTS))
        return committed(after, requestId, tileId, after.revision, "remove")
    }

    fun undo(document: StartDocument, requestId: String, removalRequestId: String): Decision {
        if (requestId.isBlank() || requestId.length > 128) return failed(document, "Invalid request identity")
        receipt(document, requestId)?.let { return Decision(document, it) }
        val removed = document.removedTiles.firstOrNull { it.requestId == removalRequestId }
            ?: return failed(document, "This removal is no longer available to undo")
        document.placements.firstOrNull { it.effectiveContentKey == removed.entry.effectiveContentKey }
            ?.let { return Decision(document, TileCommitResult.AlreadyPinned(it.tileId)) }
        document.placements.firstOrNull { it.tileId == removed.entry.tileId }
            ?.let { return Decision(document, TileCommitResult.Conflict(it)) }
        val restored = restoreEntry(document, removed.entry)
        val revision = document.revision + 1
        val after = restored.copy(revision = revision, placements = restored.placements.map {
            if (it.tileId == removed.entry.tileId) it.copy(revision = revision) else it
        }, removedTiles = document.removedTiles.filterNot { it.requestId == removalRequestId })
        return committed(after, requestId, removed.entry.tileId, revision, "undo",
            after.placements.first { it.tileId == removed.entry.tileId }.preferredCell != removed.entry.preferredCell)
    }

    /** 不覆盖后来的布局。保存尺寸和撤销共用，原位置有冲突时找最近空位。 */
    fun restoreEntry(document: StartDocument, entry: TileDocumentEntry): StartDocument {
        val columns = WpReferenceProfiles.require(document.profileId).columnCount
        val packed = AdaptiveTilePacker.pack(document, columns)
        val occupied = packed.occupiedCellsByItem.filterKeys { it != entry.tileId }.values.flatten().toSet()
        val target = entry.preferredCell ?: packed.tile(entry.tileId)?.cell ?: GridCell(0, 0)
        fun cells(at: GridCell) = (at.row until at.row + entry.size.rows).flatMap { row ->
            (at.column until at.column + entry.size.columns).map { col -> GridCell(col, row) }
        }
        // 只考察目标行和障碍边界，不扫描异常持久位置之间的数百万空行。
        val candidateRows = (listOf(0, target.row.coerceAtLeast(0), packed.documentHeightRows) +
            occupied.flatMap { listOf(it.row + 1, (it.row - entry.size.rows).coerceAtLeast(0)) }).distinct()
        val free = candidateRows.asSequence().flatMap { row -> (0..columns - entry.size.columns).asSequence().map { GridCell(it, row) } }
            .filter { cells(it).none(occupied::contains) }
            .minWithOrNull(compareBy<GridCell> { abs(it.column.toLong() - target.column) + abs(it.row.toLong() - target.row) }.thenBy { it.row }.thenBy { it.column })
            ?: GridCell(0, packed.documentHeightRows)
        val ranks = (document.placements.filterNot { it.tileId == entry.tileId }.map { it.rank } + document.spacers.map { it.rank }).toSet()
        var rank = entry.rank
        while (rank in ranks) rank++
        // 只显式记录原本已解析的位置，其他实例 revision 不变。
        val stable = document.placements.filterNot { it.tileId == entry.tileId }.map { current ->
            current.copy(preferredCell = packed.tile(current.tileId)?.cell ?: current.preferredCell)
        }
        val spacerCells = packed.spacers.associate { it.spacer.spacerId to it.cell }
        return document.copy(placements = stable + entry.copy(preferredCell = free, rank = rank),
            spacers = document.spacers.map { it.copy(preferredCell = spacerCells[it.spacerId] ?: it.preferredCell) })
    }

    fun receipt(document: StartDocument, requestId: String): TileCommitResult.Saved? = document.receipts.lastOrNull { it.requestId == requestId }
        ?.let { TileCommitResult.Saved(it.tileId, it.revision, it.documentRevision, it.relocated) }

    private fun committed(document: StartDocument, requestId: String, tileId: TileInstanceId, revision: Long, operation: String, relocated: Boolean = false): Decision {
        val receipt = TileCommitReceipt(requestId, tileId, revision, document.revision, operation, relocated)
        return Decision(document.copy(receipts = (document.receipts + receipt).takeLast(MAX_RECEIPTS)),
            TileCommitResult.Saved(tileId, revision, document.revision, relocated))
    }
    private fun failed(document: StartDocument, reason: String) = Decision(document, TileCommitResult.Failed(reason))
    private fun supportedBinding(binding: TileBinding): Boolean = binding.providerId == "yokuli" && when (binding.kind) {
        TileBindingKind.APP -> true
        TileBindingKind.READING -> binding.contentId in setOf("SOG", "HEADING_TRUE", "DEPTH", "TRUE_WIND_SPEED", "APPARENT_WIND_SPEED", "PRESSURE")
        TileBindingKind.CURRENT_TASK -> binding.contentId in setOf("navigation", "anchorWatch", "recording")
        TileBindingKind.OVERVIEW -> binding.contentId == "aisTraffic"
        TileBindingKind.SAVED_PLACE, TileBindingKind.SAVED_ROUTE -> true
        TileBindingKind.UNKNOWN -> false
    }
}

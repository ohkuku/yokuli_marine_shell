package com.yokuli.shell.engine.layout

/** 中文：实际保存与唯一编辑预览共用的纯排布入口；不写入、不递增版本。 */
object TileLayoutPreview {
    /** 移除内容保留剩余实际格位；撤销无需依赖整张旧桌面。 */
    fun remove(document: StartDocument, tileId: com.yokuli.shell.contract.TileInstanceId): StartDocument {
        val columns = com.yokuli.shell.engine.geometry.WpReferenceProfiles.require(document.profileId).columnCount
        val packed = AdaptiveTilePacker.pack(document, columns)
        val spacerCells = packed.spacers.associate { it.spacer.spacerId to it.cell }
        return document.copy(
            placements = document.placements.filterNot { it.tileId == tileId }.map { entry ->
                entry.copy(preferredCell = packed.tile(entry.tileId)?.cell ?: entry.preferredCell)
            },
            spacers = document.spacers.map { it.copy(preferredCell = spacerCells[it.spacerId] ?: it.preferredCell) },
        )
    }

    fun place(document: StartDocument, candidate: TileDocumentEntry): StartDocument {
        val current = document.placements.firstOrNull { it.tileId == candidate.tileId }
        return if (current != null && current.size != candidate.size) TileCommitPolicy.restoreEntry(document, candidate)
        else document.copy(placements = document.placements.filterNot { it.tileId == candidate.tileId } + candidate)
    }
}

package com.yokuli.shell.engine.layout

import com.yokuli.shell.engine.geometry.WpReferenceProfile
import com.yokuli.shell.engine.geometry.WpReferenceProfiles

/** 中文：切换列数是一次布局操作，始终用桌面相同排布器，不能清空后重新固定。 */
object StartColumnLayout {
    fun change(document: StartDocument, profile: WpReferenceProfile): StartDocument {
        if (document.profileId == profile.id) return document
        val before = AdaptiveTilePacker.pack(document, WpReferenceProfiles.require(document.profileId).columnCount)
        // 按用户眼前的顺序重排，而非继续保留四列的坐标后空着新增两列。
        // 整体列数切换与普通拖拽不同：切换重排填入新网格，拖拽仍保留有意的横向空位。
        val visualOrder = (before.tiles.map { Triple(it.entry.tileId, it.cell, it.entry.rank) } +
            before.spacers.map { Triple(it.spacer.spacerId, it.cell, it.spacer.rank) })
            .sortedWith(compareBy({ it.second.row }, { it.second.column }, { it.third }))
        val ranks = visualOrder.mapIndexed { index, item -> item.first to index * 1024L }.toMap()
        var trailingRank = (ranks.values.maxOrNull() ?: -1024L) + 1024L
        val candidate = document.copy(
            profileId = profile.id,
            placements = document.placements.map { entry ->
                entry.copy(preferredCell = null, rank = ranks[entry.tileId] ?: trailingRank.also { trailingRank += 1024L })
            },
            spacers = document.spacers.map { spacer ->
                spacer.copy(preferredCell = null, rank = ranks[spacer.spacerId] ?: trailingRank.also { trailingRank += 1024L })
            },
        )
        val packed = AdaptiveTilePacker.pack(candidate, profile.columnCount)
        val cells = packed.tiles.associate { it.entry.tileId to it.cell }
        val spacerCells = packed.spacers.associate { it.spacer.spacerId to it.cell }
        return candidate.copy(
            placements = candidate.placements.map { it.copy(preferredCell = cells.getValue(it.tileId)) },
            spacers = candidate.spacers.map { it.copy(preferredCell = spacerCells[it.spacerId] ?: it.preferredCell) },
        )
    }
}

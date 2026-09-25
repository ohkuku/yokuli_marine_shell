package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileBinding
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.TilePresentation

/** 中文：提交仅包含目标实例可编辑字段，绝不覆盖打开编辑器时的整张桌面。 */
data class TileCommitRequest(
    val requestId: String,
    val tileId: TileInstanceId,
    val binding: TileBinding,
    val presentation: TilePresentation,
    val size: MarineTileSize,
    /** null 是新建；编辑必须带打开时的实例版本。 */
    val expectedRevision: Long? = null,
)

sealed interface TileCommitResult {
    data class Saved(val tileId: TileInstanceId, val revision: Long, val documentRevision: Long, val relocated: Boolean = false) : TileCommitResult
    data class AlreadyPinned(val tileId: TileInstanceId) : TileCommitResult
    data class Conflict(val current: TileDocumentEntry?) : TileCommitResult
    data class Failed(val reason: String) : TileCommitResult
}

/** 中文：与实例同一次落盘的有限回执，支持同 requestId 重试。 */
data class TileCommitReceipt(
    val requestId: String,
    val tileId: TileInstanceId,
    val revision: Long,
    val documentRevision: Long,
    val operation: String,
    val relocated: Boolean = false,
)

/** 中文：撤销仅恢复被移除的实例，不把整张旧桌面写回。 */
data class TileRemovalRecord(val requestId: String, val entry: TileDocumentEntry, val documentRevision: Long)

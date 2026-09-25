package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.TileBinding
import com.yokuli.shell.contract.TileBindingKind
import com.yokuli.shell.contract.TilePresentation
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.ProfileId

data class GridCell(val column: Int, val row: Int)

data class TileDocumentEntry(
    val tileId: TileInstanceId,
    val entryId: LauncherEntryId,
    val size: MarineTileSize,
    val rank: Long,
    val groupId: String? = null,
    /**
     * An optional user-authored grid location. A null value keeps the adaptive rank-first
     * behaviour used by migrated documents; a value preserves the actual two-dimensional
     * placement chosen on Start instead of collapsing it back into a one-dimensional list.
     */
    val preferredCell: GridCell? = null,
    val binding: TileBinding? = null,
    val presentation: TilePresentation = TilePresentation(),
    /** 此实例的内容、表现或位置最后一次改变的版本。 */
    val revision: Long = 0,
    /** 存储适配器携带未知 protobuf 字段，不解释或丢弃未来载荷。 */
    val preservedProto: String = "",
) {
    val effectiveContentKey: String get() = (binding ?: TileBinding("yokuli", TileBindingKind.APP, entryId.value)).contentKey
}

typealias TilePlacement = TileDocumentEntry

data class Spacer(
    val spacerId: TileInstanceId,
    val size: MarineTileSize,
    val rank: Long,
    val groupId: String? = null,
    /** 在只改一个实例时固定空白块，不让其他内容因填空而漂移。 */
    val preferredCell: GridCell? = null,
)

data class TileDocument(
    val entries: List<TileDocumentEntry>,
    val spacers: List<Spacer> = emptyList(),
)

data class StartDocument(
    val schemaVersion: Int,
    val profileId: ProfileId,
    val defaultLayoutVersion: Int,
    val placements: List<TileDocumentEntry>,
    val spacers: List<Spacer> = emptyList(),
    val revision: Long = 0,
    val receipts: List<TileCommitReceipt> = emptyList(),
    val removedTiles: List<TileRemovalRecord> = emptyList(),
    /** 恢复可解释问题，保留重复内容而不是默默丢弃。 */
    val recoveryNotes: List<String> = emptyList(),
    val preservedProto: String = "",
)

val StartDocument.tileDocument: TileDocument
    get() = TileDocument(placements, spacers)

enum class LayoutChangeReason { MOVE, RESIZE, PIN, UNPIN, RESET, REPAIR }

data class LayoutTransaction(
    val id: String,
    val before: StartDocument,
    val after: StartDocument,
    val reason: LayoutChangeReason,
)

data class LayoutProposal(
    val before: StartDocument,
    val after: StartDocument,
    val reason: LayoutChangeReason,
)

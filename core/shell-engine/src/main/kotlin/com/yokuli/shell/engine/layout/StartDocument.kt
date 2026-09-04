package com.yokuli.shell.engine.layout

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
)

typealias TilePlacement = TileDocumentEntry

data class Spacer(
    val spacerId: TileInstanceId,
    val size: MarineTileSize,
    val rank: Long,
    val groupId: String? = null,
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

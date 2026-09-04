package com.yokuli.shell.engine.interaction

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.WpTileSize
import com.yokuli.shell.engine.layout.DesktopDocument
import com.yokuli.shell.engine.layout.LayoutTransaction

enum class LauncherPage { START, ALL_APPS }
data class ShellOffset(val x: Float, val y: Float)

sealed interface StartInteractionState {
    data object Idle : StartInteractionState
    data class Paging(
        val fromPage: LauncherPage,
        val progress: Float,
        val velocityPxPerSecond: Float,
    ) : StartInteractionState
    data class EditIdle(val selectedTile: TileInstanceId?) : StartInteractionState
    data class Dragging(
        val tileId: TileInstanceId,
        val pointerId: Long,
        val grabOffsetPx: ShellOffset,
        val visualOffsetPx: ShellOffset,
        val proposedLayout: DesktopDocument,
        val autoScrollPxPerSecond: Float,
    ) : StartInteractionState
    data class Resizing(
        val tileId: TileInstanceId,
        val proposedSize: WpTileSize,
        val proposedLayout: DesktopDocument,
    ) : StartInteractionState
    data class Settling(val transaction: LayoutTransaction) : StartInteractionState
    data class Launching(
        val tileId: TileInstanceId,
        val token: LaunchToken,
        val progress: Float,
    ) : StartInteractionState
}

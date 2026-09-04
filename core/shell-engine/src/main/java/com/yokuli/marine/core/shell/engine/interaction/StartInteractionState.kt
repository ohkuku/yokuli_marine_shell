package com.yokuli.marine.core.shell.engine.interaction

import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.TileId
import com.yokuli.marine.core.model.TileSize
import com.yokuli.marine.core.shell.engine.layout.DesktopDocument
import com.yokuli.marine.core.shell.engine.layout.LayoutTransaction

enum class LauncherPage { START, ALL_APPS }
data class ShellOffset(val x: Float, val y: Float)

sealed interface StartInteractionState {
    data object Idle : StartInteractionState
    data class Paging(
        val fromPage: LauncherPage,
        val progress: Float,
        val velocityPxPerSecond: Float,
    ) : StartInteractionState
    data class EditIdle(val selectedTile: TileId?) : StartInteractionState
    data class Dragging(
        val tileId: TileId,
        val pointerId: Long,
        val grabOffsetPx: ShellOffset,
        val visualOffsetPx: ShellOffset,
        val proposedLayout: DesktopDocument,
        val autoScrollPxPerSecond: Float,
    ) : StartInteractionState
    data class Resizing(
        val tileId: TileId,
        val proposedSize: TileSize,
        val proposedLayout: DesktopDocument,
    ) : StartInteractionState
    data class Settling(val transaction: LayoutTransaction) : StartInteractionState
    data class Launching(
        val tileId: TileId,
        val target: LaunchTarget,
        val progress: Float,
    ) : StartInteractionState
}

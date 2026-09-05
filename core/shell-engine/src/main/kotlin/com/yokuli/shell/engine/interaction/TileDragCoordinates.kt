package com.yokuli.shell.engine.interaction

/**
 * Pointer coordinates always belong to the stationary Start viewport, never the moving tile.
 * Scrolling is an independent term: later pointer events cannot overwrite its compensation.
 */
data class TileDragCoordinates(
    val startPointer: ShellOffset,
    val pointer: ShellOffset = startPointer,
    val startScrollPx: Float,
) {
    fun movedTo(position: ShellOffset): TileDragCoordinates = copy(pointer = position)

    fun contentOffset(scrollPx: Float): ShellOffset = ShellOffset(
        x = pointer.x - startPointer.x,
        y = pointer.y - startPointer.y + scrollPx - startScrollPx,
    )
}

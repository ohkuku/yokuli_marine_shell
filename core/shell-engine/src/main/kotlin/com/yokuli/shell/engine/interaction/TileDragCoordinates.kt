package com.yokuli.shell.engine.interaction

/** Stable viewport coordinates; later pointer events cannot discard independent scroll compensation. */
data class TileDragCoordinates(
    val startPointer: ShellOffset,
    val pointer: ShellOffset = startPointer,
    val startScrollPx: Float,
) {
    fun movedTo(position: ShellOffset): TileDragCoordinates = copy(pointer = position)

    /** A stationary long press, or touch jitter, is not permission to start edge scrolling. */
    fun hasMovedBeyond(touchSlopPx: Float): Boolean {
        require(touchSlopPx.isFinite() && touchSlopPx >= 0f)
        val dx = pointer.x - startPointer.x
        val dy = pointer.y - startPointer.y
        return dx * dx + dy * dy > touchSlopPx * touchSlopPx
    }

    fun contentOffset(scrollPx: Float): ShellOffset = ShellOffset(
        pointer.x - startPointer.x,
        pointer.y - startPointer.y + scrollPx - startScrollPx,
    )
}

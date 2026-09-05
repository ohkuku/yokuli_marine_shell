package com.yokuli.shell.engine

import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.TileDragCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileDragCoordinatesRegressionTest {
    @Test fun stationaryLongPressAndTouchJitterDoNotBecomeScrollingDrags() {
        val held = TileDragCoordinates(ShellOffset(30f, 205f), startScrollPx = 100f)
        assertFalse(held.hasMovedBeyond(8f))
        assertFalse(held.movedTo(ShellOffset(33f, 209f)).hasMovedBeyond(8f))
        assertFalse(held.movedTo(ShellOffset(38f, 205f)).hasMovedBeyond(8f))
        assertTrue(held.movedTo(ShellOffset(39f, 205f)).hasMovedBeyond(8f))
        assertEquals(ShellOffset(0f, 200f), held.contentOffset(300f))
        assertFalse(held.hasMovedBeyond(8f))
    }

    @Test fun subsequentPointerEventsCannotDiscardAccumulatedScroll() {
        val held = TileDragCoordinates(ShellOffset(40f, 100f), startScrollPx = 20f)
        val atEdge = held.movedTo(ShellOffset(44f, 200f))
        assertEquals(ShellOffset(4f, 180f), atEdge.contentOffset(100f))
        val next = atEdge.movedTo(ShellOffset(47f, 198f))
        assertEquals(ShellOffset(7f, 178f), next.contentOffset(100f))
        assertEquals(ShellOffset(7f, 183f), next.contentOffset(105f))
    }

    @Test fun coordinateIdentityHoldsAcrossAlternatingPointerAndScrollEvents() {
        val start = ShellOffset(70f, 110f)
        var drag = TileDragCoordinates(start, startScrollPx = 45f)
        repeat(1_000) { index ->
            val pointer = ShellOffset(70f + index % 17, 110f - index % 31)
            drag = drag.movedTo(pointer)
            val scroll = (index % 113).toFloat()
            val offset = drag.contentOffset(scroll)
            assertEquals(pointer.x, start.x + offset.x, .001f)
            assertEquals(pointer.y, start.y + offset.y - scroll + 45f, .001f)
        }
    }
}

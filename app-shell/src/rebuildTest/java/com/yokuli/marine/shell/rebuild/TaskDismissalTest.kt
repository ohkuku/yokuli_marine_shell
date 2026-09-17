package com.yokuli.marine.shell.rebuild

import org.junit.Assert.*
import org.junit.Test

class TaskDismissalTest {
    @Test fun shortGestureReturnsToCard() {
        assertFalse(shouldDismissTask(-18f, -1500f, 500f))
        assertFalse(shouldDismissTask(-70f, -50f, 500f))
    }
    @Test fun deliberateThrowAndLongDragClose() {
        assertTrue(shouldDismissTask(-50f, -1000f, 500f))
        assertTrue(shouldDismissTask(-150f, 0f, 500f))
        assertFalse(shouldDismissTask(100f, 1000f, 500f))
    }
}

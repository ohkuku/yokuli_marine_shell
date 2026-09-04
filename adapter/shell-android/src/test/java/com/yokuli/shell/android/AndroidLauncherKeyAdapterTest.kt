package com.yokuli.shell.android

import android.view.KeyEvent
import com.yokuli.shell.contract.ShellInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidShellKeyAdapterTest {
    @Test
    fun mapsOnlyDeliverableNavigationKeys() {
        assertEquals(ShellInput.BACK, AndroidShellKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_BACK))
        assertEquals(ShellInput.BACK, AndroidShellKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(ShellInput.DESKTOP, AndroidShellKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_HOME))
        assertEquals(ShellInput.SEARCH, AndroidShellKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_SEARCH))
        assertNull(AndroidShellKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
    }
}

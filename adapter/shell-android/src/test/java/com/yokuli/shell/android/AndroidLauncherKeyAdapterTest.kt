package com.yokuli.shell.android

import android.view.KeyEvent
import com.yokuli.shell.contract.LauncherInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidLauncherKeyAdapterTest {
    @Test
    fun mapsOnlyDeliverableNavigationKeys() {
        assertEquals(LauncherInput.BACK, AndroidLauncherKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_BACK))
        assertEquals(LauncherInput.BACK, AndroidLauncherKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(LauncherInput.START, AndroidLauncherKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_HOME))
        assertEquals(LauncherInput.SEARCH, AndroidLauncherKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_SEARCH))
        assertNull(AndroidLauncherKeyAdapter.mapKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
    }
}

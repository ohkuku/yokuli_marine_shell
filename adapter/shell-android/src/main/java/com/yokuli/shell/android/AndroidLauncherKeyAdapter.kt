package com.yokuli.shell.android

import android.view.KeyEvent
import com.yokuli.shell.contract.LauncherInput

/**
 * Maps only navigation keys Android actually delivers to the Activity.
 *
 * Android normally reserves a device HOME key before app dispatch. KEYCODE_HOME is
 * still mapped for keyboards/test harnesses which do deliver it; the HOME launcher
 * intent remains the production path for a physical system Home press.
 */
object AndroidLauncherKeyAdapter {
    fun mapKeyCode(keyCode: Int): LauncherInput? = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE -> LauncherInput.BACK
        KeyEvent.KEYCODE_HOME -> LauncherInput.START
        KeyEvent.KEYCODE_SEARCH -> LauncherInput.SEARCH
        else -> null
    }
}

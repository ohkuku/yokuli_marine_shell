package com.yokuli.shell.android

import android.view.KeyEvent
import com.yokuli.shell.contract.ShellInput

/**
 * Maps only navigation keys Android actually delivers to the Activity.
 *
 * Android reserves a phone's physical Home key for the operating system. A
 * KEYCODE_HOME delivered by a keyboard or test device is treated only as the
 * Yokuli in-app Bridge/Desktop command; it never changes Android's launcher.
 */
object AndroidShellKeyAdapter {
    fun mapKeyCode(keyCode: Int): ShellInput? = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE -> ShellInput.BACK
        KeyEvent.KEYCODE_HOME -> ShellInput.DESKTOP
        KeyEvent.KEYCODE_SEARCH -> ShellInput.SEARCH
        else -> null
    }
}

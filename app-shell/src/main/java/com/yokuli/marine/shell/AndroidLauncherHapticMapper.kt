package com.yokuli.marine.shell

import android.view.HapticFeedbackConstants
import com.yokuli.shell.engine.LauncherHaptic

/**
 * Android product adaptation. Stage 2.5 did not observe WP8 haptic behavior.
 * Virtual keys perform their own platform haptic in the control and therefore do not also emit an Engine effect.
 */
internal object AndroidLauncherHapticMapper {
    fun constantFor(kind: LauncherHaptic): Int = when (kind) {
        LauncherHaptic.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
        LauncherHaptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        LauncherHaptic.DROP -> HapticFeedbackConstants.CONTEXT_CLICK
    }
}

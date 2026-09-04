package com.yokuli.shell.android

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yokuli.shell.contract.ShellInsets
import com.yokuli.shell.contract.ShellRect
import com.yokuli.shell.contract.ShellRoundedCorner
import com.yokuli.shell.contract.ShellRoundedCorners
import com.yokuli.shell.contract.ShellWindowMetrics

/** Converts the current Android window into a platform-neutral Shell contract. */
object AndroidShellWindowMetrics {
    fun read(
        view: View,
        windowInsets: WindowInsetsCompat = ViewCompat.getRootWindowInsets(view)
            ?: WindowInsetsCompat.Builder().build(),
    ): ShellWindowMetrics {
        val display = view.resources.displayMetrics
        return ShellWindowMetrics(
            widthPx = view.width.takeIf { it > 0 } ?: display.widthPixels,
            heightPx = view.height.takeIf { it > 0 } ?: display.heightPixels,
            density = display.density,
            safeInsets = windowInsets.getInsets(safeDrawingTypes()).toShellInsets(),
            displayCutoutRects = windowInsets.displayCutout?.boundingRects.orEmpty().map { rect ->
                ShellRect(rect.left, rect.top, rect.right, rect.bottom)
            },
            roundedCorners = readRoundedCorners(view),
            imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).toShellInsets(),
            systemGestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures()).toShellInsets(),
        )
    }

    private fun readRoundedCorners(view: View): ShellRoundedCorners {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ShellRoundedCorners()
        val insets = view.rootWindowInsets ?: return ShellRoundedCorners()
        fun corner(position: Int): ShellRoundedCorner? = insets.getRoundedCorner(position)?.let {
            ShellRoundedCorner(it.center.x, it.center.y, it.radius)
        }
        return ShellRoundedCorners(
            topLeft = corner(RoundedCorner.POSITION_TOP_LEFT),
            topRight = corner(RoundedCorner.POSITION_TOP_RIGHT),
            bottomLeft = corner(RoundedCorner.POSITION_BOTTOM_LEFT),
            bottomRight = corner(RoundedCorner.POSITION_BOTTOM_RIGHT),
        )
    }
}

/** AndroidX's safe-drawing union expressed for WindowInsetsCompat. */
private fun safeDrawingTypes(): Int =
    WindowInsetsCompat.Type.systemBars() or
        WindowInsetsCompat.Type.displayCutout() or
        WindowInsetsCompat.Type.mandatorySystemGestures()

private fun Insets.toShellInsets() = ShellInsets(left, top, right, bottom)

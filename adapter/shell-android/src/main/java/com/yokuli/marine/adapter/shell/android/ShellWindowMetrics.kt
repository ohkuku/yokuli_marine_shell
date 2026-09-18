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
        val location = IntArray(2).also(view::getLocationInWindow)
        val width = view.width.takeIf { it > 0 } ?: display.widthPixels
        val height = view.height.takeIf { it > 0 } ?: display.heightPixels
        return ShellWindowMetrics(
            widthPx = width,
            heightPx = height,
            density = display.density,
            // 全屏背景延伸到物理屏幕边缘。刘海与圆角由具体边缘控件横向避让，
            // 不把隐藏的系统栏/手势区域当作整页上下 padding。
            safeInsets = windowInsets.displayCutout?.let { cutout ->
                ShellInsets(left=(cutout.safeInsetLeft-location[0]).coerceAtLeast(0),
                    right=(cutout.safeInsetRight-(view.rootView.width-width-location[0]).coerceAtLeast(0)).coerceAtLeast(0))
            } ?: ShellInsets(),
            displayCutoutRects = windowInsets.displayCutout?.boundingRects.orEmpty().map { rect ->
                ShellRect(rect.left-location[0], rect.top-location[1], rect.right-location[0], rect.bottom-location[1])
            }.filter { it.right > 0 && it.bottom > 0 && it.left < width && it.top < height },
            roundedCorners = readRoundedCorners(view,windowInsets,location),
            imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).toShellInsets(),
            systemGestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures()).toShellInsets(),
        )
    }

    private fun readRoundedCorners(view: View, windowInsets: WindowInsetsCompat, location: IntArray): ShellRoundedCorners {
        // 部分旧系统仅在系统资源中声明圆角；不猜机型、不把一个固定半径冒充真实设备值。
        fun declaredRadius(name: String): Int {
            val id = view.resources.getIdentifier(name,"dimen","android")
            return if(id == 0) 0 else runCatching { view.resources.getDimensionPixelSize(id) }.getOrDefault(0).coerceAtLeast(0)
        }
        val common = declaredRadius("rounded_corner_radius")
        val top = declaredRadius("rounded_corner_radius_top").takeIf { it > 0 } ?: common
        val bottom = declaredRadius("rounded_corner_radius_bottom").takeIf { it > 0 } ?: common
        val width = view.rootView.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
        val height = view.rootView.height.takeIf { it > 0 } ?: view.resources.displayMetrics.heightPixels
        fun fallback(radius: Int, right: Boolean, lower: Boolean): ShellRoundedCorner? = radius.takeIf {it > 0}?.let {
            ShellRoundedCorner((if(right)width-radius else radius)-location[0],
                (if(lower)height-radius else radius)-location[1],radius)
        }
        val declared = ShellRoundedCorners(fallback(top,false,false),fallback(top,true,false),
            fallback(bottom,false,true),fallback(bottom,true,true))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return declared
        val insets = windowInsets.toWindowInsets() ?: view.rootWindowInsets ?: return declared
        fun corner(position: Int): ShellRoundedCorner? = insets.getRoundedCorner(position)?.takeIf { it.radius > 0 }?.let {
            // Android 返回窗口坐标；Compose 宿主不是根视图时必须转换为当前测量区域坐标。
            ShellRoundedCorner(it.center.x-location[0], it.center.y-location[1], it.radius)
        }
        // API 31+ 明确返回 null 表示当前窗口不经过该圆角，不能用全屏资源覆盖它。
        return ShellRoundedCorners(corner(RoundedCorner.POSITION_TOP_LEFT),
            corner(RoundedCorner.POSITION_TOP_RIGHT),corner(RoundedCorner.POSITION_BOTTOM_LEFT),corner(RoundedCorner.POSITION_BOTTOM_RIGHT))
    }
}

private fun Insets.toShellInsets() = ShellInsets(left, top, right, bottom)

package com.yokuli.marine.baselineprofile.shell

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MILLIS = 10_000L
private const val PROFILE_MAX_ITERATIONS = 3
private const val PROFILE_STABLE_ITERATIONS = 2

@RunWith(AndroidJUnit4::class)
@LargeTest
class ShellBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() = baselineProfileRule.collect(
        packageName = targetPackage(),
        maxIterations = PROFILE_MAX_ITERATIONS,
        stableIterations = PROFILE_STABLE_ITERATIONS,
        includeInStartupProfile = true,
        filterPredicate = ::isYokuliRule,
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.configureForAnimatedShell()
        device.resetTargetData()
        startShellAndWait()
        device.awaitTargetPackage()
        device.awaitTag("start-screen")
    }

    @Test
    fun generateCriticalJourneys() = baselineProfileRule.collect(
        packageName = targetPackage(),
        maxIterations = PROFILE_MAX_ITERATIONS,
        stableIterations = PROFILE_STABLE_ITERATIONS,
        includeInStartupProfile = false,
        filterPredicate = ::isYokuliRule,
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.configureForAnimatedShell()
        device.resetTargetData()
        startShellAndWait()
        device.awaitTag("virtual-key-start").click()
        device.awaitTag("start-screen")
        device.ensureSettingsPinned()
        device.awaitTag("tile-settings").longPress(device)
        device.awaitTag("unpin-selected-tile").click()
        require(device.findObject(UiSelector().resourceId("tile-settings")).waitUntilGone(WAIT_MILLIS)) {
            "Settings tile did not leave Start after Unpin"
        }
        device.showAllApps()
        device.awaitTag("launcher-entry-settings").longPress(device)
        device.awaitTag("launcher-context-menu")
        device.clickSemantically("launcher-context-pin")
        device.awaitTag("start-screen")
        device.awaitTag("tile-settings")
        device.awaitTag("virtual-key-start").click()
        device.waitForIdle()
        Thread.sleep(1_000)
        device.awaitTag("start-screen")
        device.awaitTag("tile-chart").click()
        device.awaitTag("chart-workspace-browse")
        device.pressBack()
        device.awaitTag("start-screen")
    }

    private fun UiDevice.awaitTag(tag: String): UiObject {
        val tagged = findObject(UiSelector().resourceId(tag))
        require(tagged.waitForExists(WAIT_MILLIS)) { "Timed out waiting for Compose tag: $tag" }
        return tagged
    }

    private fun UiDevice.configureForAnimatedShell() {
        Configurator.getInstance()
            .setWaitForIdleTimeout(1_000L)
            .setActionAcknowledgmentTimeout(1_000L)
        waitForIdle()
    }

    private fun UiDevice.awaitTargetPackage() {
        require(wait(Until.hasObject(By.pkg(targetPackage())), WAIT_MILLIS)) {
            "Timed out waiting for target package: ${targetPackage()}"
        }
    }

    private fun UiDevice.clickSemantically(tag: String) {
        awaitTag(tag)
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?: error("No active accessibility root for $tag")
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.viewIdResourceName == tag) {
                require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    "Unable to perform semantic click for $tag"
                }
                return
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::add) }
        }
        error("Unable to find accessibility node for $tag")
    }

    private fun UiDevice.resetTargetData() {
        require(executeShellCommand("pm clear ${targetPackage()}").trim() == "Success") {
            "Unable to reset deterministic profile state for ${targetPackage()}"
        }
    }

    private fun UiDevice.ensureSettingsPinned() {
        if (findObject(UiSelector().resourceId("tile-settings")).exists()) return
        showAllApps()
        awaitTag("launcher-entry-settings").longPress(this)
        awaitTag("launcher-context-menu")
        awaitTag("launcher-context-pin").click()
        awaitTag("start-screen")
        awaitTag("tile-settings")
    }

    private fun UiDevice.showAllApps() {
        swipe(displayWidth - 24, displayHeight / 2, 24, displayHeight / 2, 24)
        awaitTag("all-apps-list")
        waitForIdle()
        SystemClock.sleep(1_000L)
    }

    private fun UiObject.longPress(device: UiDevice) {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            centerX.toFloat(),
            centerY.toFloat(),
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        require(automation.injectInputEvent(down, true)) { "Unable to inject long-press DOWN event" }
        down.recycle()
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 250L)
        val upTime = SystemClock.uptimeMillis()
        val up = MotionEvent.obtain(
            downTime,
            upTime,
            MotionEvent.ACTION_UP,
            centerX.toFloat(),
            centerY.toFloat(),
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        require(automation.injectInputEvent(up, true)) { "Unable to inject long-press UP event" }
        up.recycle()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.startShellAndWait() {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(targetPackage(), "com.yokuli.marine.shell.ShellActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
    }

    private fun targetPackage(): String = BuildConfig.TARGET_APP_ID

    private fun isYokuliRule(rule: String): Boolean = rule.contains("Lcom/yokuli/")
}

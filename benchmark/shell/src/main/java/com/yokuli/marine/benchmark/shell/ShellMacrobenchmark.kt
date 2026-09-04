package com.yokuli.marine.benchmark.shell

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.yokuli.marine"
private const val SHELL_ACTIVITY = "com.yokuli.marine.shell.ShellActivity"
private const val SHELL_LAB_ACTIVITY = "com.yokuli.marine.feature.shell.lab.ShellLabActivity"
private const val WAIT_MILLIS = 10_000L

/**
 * 中文：CI 模拟器结果只用于发现趋势；真机 60/90/120 Hz 与三星方屏仍需人工验证。
 * English: Emulator metrics detect regressions only. Device frame gates remain unverified.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ShellMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun coldStartToStart() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { normalizeStartAndPressHome() },
    ) {
        startActivityAndWait()
        device.awaitApp()
    }

    @Test
    fun warmStartToStart() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { normalizeStartAndPressHome() },
    ) {
        startActivityAndWait()
        device.awaitApp()
    }

    @Test
    fun startToAllApps() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(shellIntent())
            device.awaitTag("start-screen")
        },
    ) {
        device.swipe(device.displayWidth - 24, device.displayHeight / 2, 24, device.displayHeight / 2, 24)
        device.awaitTag("all-apps-list")
    }

    @Test
    fun openChartAndReturn() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(shellIntent())
            device.awaitTag("start-screen")
        },
    ) {
        device.awaitTag("tile-chart").click()
        device.awaitTag("chart-workspace-browse")
        device.pressBack()
        device.awaitTag("start-screen")
    }

    @Test
    fun startVerticalScroll60Tiles() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(TARGET_PACKAGE, SHELL_LAB_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            device.awaitTag("start-screen")
        },
    ) {
        repeat(3) {
            device.swipe(device.displayWidth / 2, device.displayHeight - 80, device.displayWidth / 2, 120, 20)
            device.waitForIdle()
        }
    }

    private fun UiDevice.awaitApp() {
        require(wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), WAIT_MILLIS)) {
            "Timed out waiting for target package: $TARGET_PACKAGE"
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.normalizeStartAndPressHome() {
        pressHome()
        startActivityAndWait(shellIntent())
        device.awaitTag("start-screen")
        pressHome()
        device.waitForIdle()
    }

    private fun shellIntent() = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(TARGET_PACKAGE, SHELL_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun UiDevice.awaitTag(tag: String): UiObject {
        val tagged = findObject(UiSelector().resourceId(tag))
        require(tagged.waitForExists(WAIT_MILLIS)) { "Timed out waiting for Compose tag: $tag" }
        return tagged
    }
}

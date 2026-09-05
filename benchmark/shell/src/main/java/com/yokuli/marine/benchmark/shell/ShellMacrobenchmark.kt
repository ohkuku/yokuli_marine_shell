package com.yokuli.marine.benchmark.shell

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.yokuli.marine"
private const val SHELL_ACTIVITY = "com.yokuli.marine.shell.ShellActivity"
private const val SHELL_LAB_ACTIVITY = "com.yokuli.marine.feature.shell.lab.ShellLabActivity"
private const val EXTRA_TILE_COUNT = "com.yokuli.marine.shell.lab.TILE_COUNT"
private const val EXTRA_VIEWPORT_DP = "com.yokuli.marine.shell.lab.VIEWPORT_DP"
private const val EXTRA_PREPARE_BENCHMARK_START = "com.yokuli.marine.shell.PREPARE_BENCHMARK_START"
private const val WAIT_MILLIS = 20_000L
private val DIAGNOSTIC_TAGS = listOf(
    "shell-host",
    "start-screen",
    "launcher-restoring",
    "launcher-recovery",
    "all-apps-list",
    "shell-search-surface",
    "launcher-recents",
    "chart-workspace-browse",
    "settings-overview-list",
)

/**
 * 中文：CI 模拟器结果只用于发现趋势；真机 60/90/120 Hz 与三星方屏仍需人工验证。
 * English: Emulator metrics detect regressions only. Device frame gates remain unverified.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@OptIn(ExperimentalMetricApi::class)
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
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait(shellIntent())
        device.awaitTag("start-screen")
    }

    @Test
    fun warmStartToStart() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait(shellIntent())
        device.awaitTag("start-screen")
    }

    @Test
    fun startToAllApps() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = interactionFrameMetrics(),
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
        metrics = interactionFrameMetrics(),
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
        metrics = interactionFrameMetrics(),
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

    @Test
    fun desktopModuleListRoundTrip() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = interactionFrameMetrics(),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = { openStart() },
    ) {
        device.swipe(device.displayWidth - 24, device.displayHeight / 2, 24, device.displayHeight / 2, 24)
        device.awaitTag("all-apps-list")
        device.awaitTag("virtual-key-bridge").click()
        device.awaitTag("start-screen")
    }

    @Test
    fun searchToChart() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = interactionFrameMetrics(),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = { openStart() },
    ) {
        device.awaitTag("virtual-key-search").click()
        device.awaitTag("launcher-search-field").setText("chart")
        device.awaitTag("search-result-chart").click()
        device.awaitTag("chart-workspace-browse")
    }

    @Test
    fun dragAcrossThirtyMixedTiles() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = interactionFrameMetrics(),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = { openLab(tileCount = 30) },
    ) {
        val tile = device.awaitTag("tile-demo-1")
        tile.longPress()
        device.awaitTag("resize-selected-tile")
        device.awaitTag("tile-demo-1").dragTo(
            device.displayWidth / 2,
            device.displayHeight - 140,
            40,
        )
        device.awaitTag("tile-demo-1")
    }

    @Test
    fun resizeStandardTileToLarge() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = interactionFrameMetrics(),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = { openLab(tileCount = 30) },
    ) {
        device.awaitTag("tile-demo-1").longPress()
        device.awaitTag("resize-selected-tile")
        repeat(3) {
            device.awaitTag("resize-selected-tile").click()
            device.awaitTag("commit-tile-resize").click()
        }
        device.awaitTag("shell-lab-demo-1-size-large_4x4")
    }

    @Test
    fun rounded320Viewport() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = interactionFrameMetrics(),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = { openLab(tileCount = 30, viewportDp = 320) },
    ) {
        val viewport = device.awaitTag("shell-lab-rounded-viewport-320").bounds
        device.swipe(viewport.centerX(), viewport.bottom - 24, viewport.centerX(), viewport.top + 24, 24)
        device.waitForIdle()
        device.awaitTag("shell-lab-rounded-viewport-320")
    }

    @Test
    fun settingsScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = interactionFrameMetrics(),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = { openStart() },
    ) {
        // Include the Settings surface entrance in the measured window. The compact
        // overview can fit tall emulators, where a swipe alone legitimately produces
        // zero target frames and would otherwise be a false-positive benchmark.
        device.awaitTag("tile-settings").click()
        device.awaitTag("settings-overview-list")
        val list = device.awaitTag("settings-overview-list").bounds
        repeat(2) {
            device.swipe(list.centerX(), list.bottom - 20, list.centerX(), list.top + 20, 20)
        }
        device.waitForIdle()
        device.awaitTag("settings-overview-list")
    }

    private fun shellIntent() = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(TARGET_PACKAGE, SHELL_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        putExtra(EXTRA_PREPARE_BENCHMARK_START, true)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openStart() {
        pressHome()
        startActivityAndWait(shellIntent())
        device.awaitTag("start-screen")
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openLab(
        tileCount: Int,
        viewportDp: Int? = null,
    ) {
        pressHome()
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(TARGET_PACKAGE, SHELL_LAB_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_TILE_COUNT, tileCount)
                viewportDp?.let { putExtra(EXTRA_VIEWPORT_DP, it) }
            },
        )
        device.awaitTag("start-screen")
    }

    private fun interactionFrameMetrics(): List<Metric> = if (isEmulator()) {
        // Hosted software renderers do not always publish Perfetto RenderThread slices.
        // gfxinfo still samples actual target frames; physical devices retain precise traces.
        listOf(FrameTimingGfxInfoMetric())
    } else {
        listOf(FrameTimingMetric())
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("emulator", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)

    private fun UiDevice.awaitTag(tag: String): UiObject {
        val tagged = findObject(UiSelector().resourceId(tag))
        if (!tagged.waitForExists(WAIT_MILLIS)) {
            val visibleTags = DIAGNOSTIC_TAGS.filter { candidate ->
                findObject(UiSelector().resourceId(candidate)).exists()
            }
            val resumed = executeShellCommand("dumpsys activity activities")
                .lineSequence()
                .filter { line ->
                    line.contains("mResumedActivity") || line.contains("topResumedActivity")
                }
                .take(3)
                .joinToString(" | ")
                .compactEvidence(500)
            val targetPid = executeShellCommand("pidof $TARGET_PACKAGE").trim().ifEmpty { "none" }
            val androidRuntime = executeShellCommand("logcat -d -t 80 AndroidRuntime:E '*:S'")
                .lineSequence()
                .toList()
                .takeLast(12)
                .joinToString(" | ")
                .compactEvidence(900)
            throw IllegalArgumentException(
                "Timed out waiting for Compose tag: $tag; " +
                    "currentPackage=$currentPackageName; targetPid=$targetPid; " +
                    "visibleTags=$visibleTags; resumed=$resumed; androidRuntime=$androidRuntime",
            )
        }
        return tagged
    }

    private fun String.compactEvidence(limit: Int): String =
        replace(Regex("\\s+"), " ").trim().take(limit).ifEmpty { "none" }

    private fun UiObject.longPress() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            bounds.centerX().toFloat(),
            bounds.centerY().toFloat(),
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        require(automation.injectInputEvent(down, true)) { "Unable to inject long-press DOWN" }
        down.recycle()
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 250L)
        val up = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            bounds.centerX().toFloat(),
            bounds.centerY().toFloat(),
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        require(automation.injectInputEvent(up, true)) { "Unable to inject long-press UP" }
        up.recycle()
        device.waitForIdle()
    }
}

package com.yokuli.marine.shell

import android.graphics.Color
import android.content.Intent
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import com.yokuli.marine.core.design.WpThemeModeNameKey
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.WpTileAccentNameKey
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.feature.desktop.productionLauncherUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellActivityStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Before
    fun resetDurableLauncherState() {
        compose.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[ShellViewModel::class.java].resetLauncher()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("tile-chart").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun chartTileOpensBrowseOnlySurfaceAndSystemBackReturnsToStart() {
        compose.onNodeWithTag("tile-chart").assertIsDisplayed().performClick()

        compose.onNodeWithTag("chart-workspace-browse").assertIsDisplayed()
        compose.onNodeWithTag("wp-page-title-chart").assertIsDisplayed()
        compose.onNodeWithTag(
            if (BuildConfig.GOOGLE_MAPS_CONFIGURED) "chart-surface-google" else "chart-surface-demo",
        ).assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun productionShellExposesOnlyChartAndSettingsWithReusableLargeTitles() {
        compose.onNodeWithTag("tile-chart").performClick()
        compose.onNodeWithTag("wp-page-title-chart").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("tile-settings").performClick()
        compose.onNodeWithTag("wp-page-title-settings").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("all-apps-entry").performClick()
        compose.onAllNodes(
            SemanticsMatcher("exact production launcher entries") { node ->
                node.config.contains(SemanticsProperties.TestTag) &&
                    node.config[SemanticsProperties.TestTag].startsWith("launcher-entry-")
            },
        ).assertCountEquals(2)
        compose.onNodeWithTag("launcher-entry-chart").assertIsDisplayed()
        compose.onNodeWithTag("launcher-entry-settings").assertIsDisplayed()
        compose.onNodeWithTag("launcher-entry-anchor").assertDoesNotExist()
        compose.onNodeWithTag("launcher-entry-cockpit").assertDoesNotExist()
    }

    @Test
    fun pageTracksFingerOneToOneAndLongDragCompletes() {
        compose.onNodeWithTag("interactive-launcher-pager").performTouchInput { swipeLeft(durationMillis = 700) }
        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()
    }

    @Test
    fun shortSlowDragCancels() {
        compose.onNodeWithTag("interactive-launcher-pager").performTouchInput {
            swipe(
                start = center,
                end = center.copy(x = center.x * .76f),
                durationMillis = 900,
            )
        }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("all-apps-list").assertIsNotDisplayed()
    }

    @Test
    fun verticalIntentDoesNotPage() {
        compose.onNodeWithTag("start-grid").performTouchInput { swipeUp(durationMillis = 700) }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("all-apps-list").assertIsNotDisplayed()
    }

    @Test
    fun editModeDisablesPageSwipe() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("interactive-launcher-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("all-apps-list").assertIsNotDisplayed()
    }

    @Test
    fun systemBackExitsEditModeBeforeLeavingStart() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("unpin-selected-tile").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("unpin-selected-tile").assertDoesNotExist()
    }

    @Test
    fun chartResizeCyclesWideSmallMediumWide() {
        val wideWidth = compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("tile-chart").performTouchInput { longClick() }

        compose.onNodeWithTag("resize-selected-tile").performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width < wideWidth
        }
        val smallWidth = compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width

        compose.onNodeWithTag("resize-selected-tile").performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width > smallWidth
        }
        val mediumWidth = compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width

        compose.onNodeWithTag("resize-selected-tile").performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width > mediumWidth
        }
        compose.onNodeWithTag("all-apps-entry").performClick()
        compose.waitUntil(5_000) {
            abs(compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width - wideWidth) < 1f
        }
        assertEquals(wideWidth, compose.onNodeWithTag("tile-chart").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun systemBackFromAllAppsReturnsToStart() {
        compose.onNodeWithTag("interactive-launcher-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun appearanceUsesOneAccentAndCorrectBlackWhitePageForegroundPolicy() {
        compose.onNodeWithTag("tile-settings").performClick()
        compose.onNodeWithTag("settings-section-appearance").performClick()
        compose.onNodeWithTag("theme-accent-magenta").performClick()
        compose.onNodeWithTag("theme-mode-light").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag("start-screen").assert(
            SemanticsMatcher.expectValue(WpThemeModeNameKey, "light"),
        )
        listOf("chart", "settings").forEach { id ->
            compose.onNodeWithTag("tile-$id").assert(
                SemanticsMatcher.expectValue(WpTileAccentNameKey, "magenta"),
            )
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun lightThemeUpdatesHostWindowChromeOutsideCompose() {
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(Color.BLACK, activity.window.statusBarColor)
            assertEquals(Color.BLACK, activity.window.navigationBarColor)
            assertEquals(
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
                activity.window.attributes.layoutInDisplayCutoutMode,
            )
        }

        compose.onNodeWithTag("tile-settings").performClick()
        compose.onNodeWithTag("settings-section-appearance").performClick()
        compose.onNodeWithTag("theme-mode-light").performClick()
        compose.waitForIdle()

        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(Color.WHITE, activity.window.statusBarColor)
            assertEquals(Color.WHITE, activity.window.navigationBarColor)
            assertTrue(
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars,
            )
        }
    }

    @Test
    fun allAppsLongPressOpensContextWithoutChangingStart() {
        compose.onNodeWithTag("all-apps-entry").performClick()
        compose.onNodeWithTag("launcher-entry-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("launcher-context-menu").assertIsDisplayed()
        compose.onNodeWithTag("launcher-context-app-info").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
    }

    @Test
    fun pinReturnsToStartRevealsTileAndCanUndo() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tile-settings").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("all-apps-entry").performClick()
        compose.onNodeWithTag("launcher-entry-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("launcher-context-pin").performClick()

        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
        compose.onNodeWithTag("launcher-undo").assertIsDisplayed()
        compose.onNodeWithTag("launcher-undo-action").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tile-settings").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun unpinKeepsEntryInstalledAndCanUndo() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tile-settings").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("launcher-undo").assertIsDisplayed()

        compose.onNodeWithTag("launcher-undo-action").performClick()
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
        compose.onNodeWithTag("all-apps-entry").performClick()
        compose.onNodeWithTag("launcher-entry-settings").assertIsDisplayed()
    }

    @Test
    fun virtualStartReturnsFromSettingsWithoutDestroyingItsTask() {
        compose.onNodeWithTag("tile-settings").performClick()
        compose.onNodeWithTag("settings-workspace").assertIsDisplayed()

        compose.onNodeWithTag("virtual-key-start").performClick()
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("virtual-key-back").performTouchInput { longClick() }

        compose.onNodeWithTag("launcher-recents").assertIsDisplayed()
        compose.onNodeWithTag("recent-task-settings").assertIsDisplayed()
    }

    @Test
    fun virtualSearchFindsAndLaunchesInstalledEntry() {
        compose.onNodeWithTag("virtual-key-search").performClick()
        compose.onNodeWithTag("launcher-search-overlay").assertIsDisplayed()
        compose.onNodeWithTag("launcher-search-field").assertIsDisplayed()

        compose.onNodeWithTag("search-result-chart").performClick()
        compose.onNodeWithTag("chart-workspace-browse").assertIsDisplayed()
    }

    @Test
    fun virtualBackLongPressOpensRecents() {
        compose.onNodeWithTag("tile-chart").performClick()
        compose.onNodeWithTag("virtual-key-start").performClick()
        compose.onNodeWithTag("virtual-key-back").performTouchInput { longClick() }

        compose.onNodeWithTag("launcher-recents").assertIsDisplayed()
        compose.onNodeWithTag("recent-task-chart").performClick()
        compose.onNodeWithTag("chart-workspace-browse").assertIsDisplayed()
    }

    @Test
    fun androidBackAndDeliveredHardwareKeysUseTheUnifiedInputPath() {
        compose.onNodeWithTag("all-apps-entry").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()

        dispatchHardwareKey(KeyEvent.KEYCODE_SEARCH)
        compose.onNodeWithTag("launcher-search-overlay").assertIsDisplayed()
        dispatchHardwareKey(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("launcher-search-overlay").assertDoesNotExist()

        compose.onNodeWithTag("tile-settings").performClick()
        dispatchHardwareKey(KeyEvent.KEYCODE_HOME)
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun homeIntentReturnsToStartWithoutRecreatingActivity() {
        compose.onNodeWithTag("tile-settings").performClick()
        var before = 0
        compose.activityRule.scenario.onActivity { activity ->
            before = System.identityHashCode(activity)
            activity.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClass(activity, ShellActivity::class.java)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(before, System.identityHashCode(activity))
        }
    }

    @Test
    fun homeRecoverySurfaceCanOpenAndroidSettings() {
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[ShellViewModel::class.java].engine.dispatch(
                com.yokuli.shell.engine.LauncherAction.EnterSafeMode,
            )
        }
        compose.onNodeWithTag("launcher-recovery").assertIsDisplayed()

        var launchedAction: String? = null
        compose.activityRule.scenario.onActivity { activity ->
            activity.platformIntentLauncher = { intent -> launchedAction = intent.action }
        }
        compose.onNodeWithTag("recovery-open-android-settings").performClick()
        compose.waitForIdle()
        assertEquals(android.provider.Settings.ACTION_HOME_SETTINGS, launchedAction)
    }

    @Test
    fun square320UsesTheSameExplicitSpatialDocument() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    Box(Modifier.requiredSize(320.dp)) {
                        YokuliStartScreen(
                            state = productionLauncherUiState(
                                productionCatalog.snapshot,
                                defaultStartDocument,
                                mapConfigured = false,
                                theme = WpThemeSpec(),
                                visualContributions = productionVisualContributions,
                            ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
    }

    @Test
    fun activityRecreationRetainsTheEngineDocument() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("resize-selected-tile").performClick()
        compose.onNodeWithTag("wp-page-title-settings").assertDoesNotExist()
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        compose.onNodeWithTag("tile-settings").assertDoesNotExist()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-settings").assertDoesNotExist()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
    }

    @Test
    fun languageSelectionRecreatesTheRealActivityInEnglishAndChinese() {
        selectLanguage("en")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("app language").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings-section-language").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()

        selectLanguage("zh-CN")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("应用语言").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings-section-language").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
    }

    private fun selectLanguage(tag: String) {
        compose.onNodeWithTag("tile-settings").performClick()
        compose.onNodeWithTag("settings-section-language").performClick()
        compose.onNodeWithTag("language-$tag").performClick()
        compose.waitForIdle()
    }

    private fun dispatchHardwareKey(keyCode: Int) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        compose.waitForIdle()
    }
}

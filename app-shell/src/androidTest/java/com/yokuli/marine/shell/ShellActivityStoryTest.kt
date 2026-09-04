package com.yokuli.marine.shell

import android.graphics.Color
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.core.design.WpThemeModeNameKey
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.WpTileAccentNameKey
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.feature.desktop.productionLauncherUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellActivityStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

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
    fun allAppsLongPressUsesVisibleContextMenuToUnpin() {
        compose.onNodeWithTag("all-apps-entry").performClick()
        compose.onNodeWithTag("launcher-entry-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("launcher-context-menu").assertIsDisplayed()
        compose.onNodeWithTag("launcher-context-app-info").assertIsDisplayed()
        compose.onNodeWithTag("launcher-context-pin").performClick()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-settings").assertDoesNotExist()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
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
                                defaultDesktopDocument,
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
    fun editModeResizesAndUnpinsWithoutOpeningSettings() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        compose.onNodeWithTag("resize-selected-tile").performClick()
        compose.onNodeWithTag("wp-page-title-settings").assertDoesNotExist()
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        compose.onNodeWithTag("tile-settings").assertDoesNotExist()
    }

    @Test
    fun languageSelectionRecreatesTheRealActivityInEnglishAndChinese() {
        selectLanguage("en")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Chart").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Chart").assertIsDisplayed()

        selectLanguage("zh-CN")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("海图").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("海图").assertIsDisplayed()
    }

    private fun selectLanguage(tag: String) {
        compose.onNodeWithTag("tile-settings").performClick()
        compose.onNodeWithTag("settings-section-language").performClick()
        compose.onNodeWithTag("language-$tag").performClick()
        compose.waitForIdle()
    }
}

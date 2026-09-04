package com.yokuli.marine.shell

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.core.design.WpThemeModeNameKey
import com.yokuli.marine.core.design.WpTileAccentNameKey
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellActivityStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun anchorTileOpensSharedChartInAnchorModeAndHomeReturnsToStart() {
        compose.onNodeWithTag("tile-anchor").assertIsDisplayed().performClick()

        compose.onNodeWithTag("chart-workspace-anchor").assertIsDisplayed()
        compose.onNodeWithTag("wp-page-title-chart").assertIsDisplayed()
        compose.onNodeWithTag("chart-primary-state").assertIsDisplayed()
        compose.onNodeWithTag(
            if (BuildConfig.GOOGLE_MAPS_CONFIGURED) "chart-surface-google" else "chart-surface-fixture",
        ).assertIsDisplayed()

        compose.onNodeWithTag("chart-home").performClick()
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun everyCoreAppUsesTheReusableLargeTopLeftTitleContract() {
        listOf(
            Triple("tile-cockpit", "wp-page-title-cockpit", "cockpit-home"),
            Triple("tile-library", "wp-page-title-library", "library-home"),
            Triple("tile-system", "wp-page-title-system", "system-home"),
        ).forEach { (tile, title, home) ->
            compose.onNodeWithTag(tile).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag(title).assertIsDisplayed()
            compose.onNodeWithTag(home).performClick()
            compose.onNodeWithTag("start-screen").assertIsDisplayed()
        }
    }

    @Test
    fun systemDisplayThemePropagatesOneAccentToEveryDefaultTile() {
        compose.onNodeWithTag("tile-system").performScrollTo().performClick()
        compose.onNodeWithTag("system-section-display").performClick()
        compose.onNodeWithTag("theme-accent-magenta").performClick()
        compose.onNodeWithTag("theme-mode-light").performClick()
        compose.onNodeWithTag("system-home").performClick()

        compose.onNodeWithTag("start-screen").assert(
            SemanticsMatcher.expectValue(WpThemeModeNameKey, "light"),
        )
        listOf("chart", "anchor", "cockpit", "library", "system").forEach { id ->
            compose.onNodeWithTag("tile-$id").performScrollTo().assert(
                SemanticsMatcher.expectValue(WpTileAccentNameKey, "magenta"),
            )
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun lightThemeUpdatesHostWindowChromeOutsideTheComposeCanvas() {
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(android.graphics.Color.BLACK, activity.window.statusBarColor)
            assertEquals(android.graphics.Color.BLACK, activity.window.navigationBarColor)
            assertEquals(
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
                activity.window.attributes.layoutInDisplayCutoutMode,
            )
        }

        compose.onNodeWithTag("tile-system").performScrollTo().performClick()
        compose.onNodeWithTag("system-section-display").performClick()
        compose.onNodeWithTag("theme-mode-light").performClick()
        compose.waitForIdle()

        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(android.graphics.Color.WHITE, activity.window.statusBarColor)
            assertEquals(android.graphics.Color.WHITE, activity.window.navigationBarColor)
            assertTrue(
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars,
            )
        }
    }

    @Test
    fun allAppsEntryOpensAlphabeticalCoreAppsAndPinsShortcuts() {
        compose.onNodeWithTag("all-apps-entry").assertIsDisplayed().performClick()

        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()
        compose.onNodeWithTag("launcher-entry-chart").assertIsDisplayed()
        compose.onNodeWithTag("launcher-entry-anchorages").assertIsDisplayed().performTouchInput { longClick() }

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tile-anchorages").assertExists()
    }

    @Test
    fun square320StartScreenKeepsChartVisibleAndSystemReachable() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                Box(Modifier.requiredSize(320.dp)) {
                    YokuliStartScreen()
                }
            }
        }

        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
        compose.onNodeWithTag("start-grid").performTouchInput { swipeUp() }
        compose.onNodeWithTag("tile-system").assertIsDisplayed()
    }

    @Test
    fun editModeMovesResizesAndUnpinsWithoutOpeningTheApp() {
        val system = compose.onNodeWithTag("tile-system").assertIsDisplayed()
        val originalLeft = system.fetchSemanticsNode().boundsInRoot.left

        system.performTouchInput { longClick() }
        system.performTouchInput { swipeLeft() }
        val movedLeft = compose.onNodeWithTag("tile-system").fetchSemanticsNode().boundsInRoot.left
        assertTrue(movedLeft < originalLeft)

        compose.onNodeWithTag("resize-selected-tile").performClick()
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        compose.onNodeWithTag("tile-system").assertDoesNotExist()
    }

    @Test
    fun languageSelectionRecreatesTheRealActivityInEnglishAndChinese() {
        selectLanguage("en")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Chart").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Chart").assertIsDisplayed()

        selectLanguage("zh")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("海图").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("海图").assertIsDisplayed()
    }

    private fun selectLanguage(tag: String) {
        compose.onNodeWithTag("tile-system").performScrollTo().performClick()
        compose.onNodeWithTag("system-section-display").performClick()
        compose.onNodeWithTag("language-$tag").performClick()
        compose.waitForIdle()
    }
}

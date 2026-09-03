package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import org.junit.Rule
import org.junit.Test
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
        compose.onNodeWithText("NOT ARMED").assertIsDisplayed()

        compose.onNodeWithTag("chart-home").performClick()
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun allAppsEntryOpensAlphabeticalCoreAppsAndPinsShortcuts() {
        compose.onNodeWithTag("all-apps-entry").assertIsDisplayed().performClick()

        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()
        compose.onNodeWithText("Chart").assertIsDisplayed()
        compose.onNodeWithText("Anchorages").assertIsDisplayed().performTouchInput { longClick() }

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tile-anchorages").assertExists()
    }

    @Test
    fun square320StartScreenKeepsChartVisibleAndSystemReachable() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                Box(Modifier.requiredSize(320.dp)) {
                    YokuliStartScreen(onOpen = {}, onAllApps = {})
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
}

package com.yokuli.marine.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEngine
import com.yokuli.shell.engine.LauncherRecoveryMode
import com.yokuli.shell.engine.ShellVisualSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NmeaSourcesShellStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Before
    fun resetDurableLauncherState() {
        lateinit var resetJob: Job
        lateinit var engine: LauncherEngine
        compose.activityRule.scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[ShellViewModel::class.java]
            engine = viewModel.engine
            resetJob = viewModel.resetLauncher()
        }
        runBlocking { resetJob.join() }
        engine.dispatch(LauncherAction.EnterSafeMode)
        compose.waitUntil(10_000) { engine.state.value.recoveryMode == LauncherRecoveryMode.SAFE_MODE }
        engine.dispatch(LauncherAction.ExitSafeMode)
        engine.dispatch(LauncherAction.ShowDesktop)
        compose.waitUntil(10_000) {
            engine.state.value.recoveryMode == LauncherRecoveryMode.NORMAL &&
                engine.state.value.surface == ShellVisualSurface.Desktop
        }
        await("start-screen")
    }

    @Test
    fun bothAppsAreDiscoverableAndRootBackReturnsToTheInAppStart() {
        openAllApps()
        compose.onNodeWithTag("launcher-entry-nmea-input").performScrollTo().assertIsDisplayed().performClick()
        await("nmea-input-workspace")

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("start-screen")

        openAllApps()
        compose.onNodeWithTag("launcher-entry-data-sources").performScrollTo().assertIsDisplayed().performClick()
        await("data-sources-root")

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun nmeaAppOwnsPinningAndItsDeclaredThreeSizeCycle() {
        openAllApps()
        compose.onNodeWithTag("launcher-entry-nmea-input").performScrollTo().performTouchInput { longClick() }
        await("launcher-context-pin")
        compose.onNodeWithTag("launcher-context-pin").performClick()

        await("tile-nmea-input")
        await("nmea-tile-medium", unmerged = true)
        compose.onNodeWithTag("tile-nmea-input").performTouchInput { longClick() }
        await("resize-selected-tile")
        compose.onNodeWithTag("resize-selected-tile").performClick()
        await("nmea-tile-wide", unmerged = true)

        compose.onNodeWithTag("resize-selected-tile").performClick()
        await("nmea-tile-small", unmerged = true)
    }

    private fun openAllApps() {
        compose.onNodeWithTag("all-apps-entry").performClick()
        await("all-apps-list")
    }

    private fun await(tag: String, unmerged: Boolean = false) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

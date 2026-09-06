package com.yokuli.marine.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class ChartLibraryShellStoryTest {
    @get:Rule val compose = createAndroidComposeRule<ShellActivity>()

    @Before fun resetStart() {
        lateinit var reset: Job
        lateinit var engine: LauncherEngine
        compose.activityRule.scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[ShellViewModel::class.java]
            engine = viewModel.engine
            reset = viewModel.resetLauncher()
        }
        runBlocking { reset.join() }
        engine.dispatch(LauncherAction.EnterSafeMode)
        compose.waitUntil(10_000) { engine.state.value.recoveryMode == LauncherRecoveryMode.SAFE_MODE }
        engine.dispatch(LauncherAction.ExitSafeMode)
        engine.dispatch(LauncherAction.ShowDesktop)
        compose.waitUntil(10_000) { engine.state.value.surface == ShellVisualSurface.Desktop }
        await("start-screen")
    }

    @Test fun libraryIsDiscoverableButNotAutoPinnedAndRootBackStopsAtStart() {
        compose.onNodeWithTag("tile-chart_library").assertDoesNotExist()
        compose.onNodeWithTag("all-apps-entry").performClick()
        await("all-apps-list")
        compose.onNodeWithTag("launcher-entry-chart_library").performScrollTo().performClick()
        await("chart-library-root")

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    private fun await(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}

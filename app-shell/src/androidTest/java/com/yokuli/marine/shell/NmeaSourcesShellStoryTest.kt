package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.feature.data.dataLauncherVisualContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.contract.MarineTileSize
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
    fun dataJourneyMovesInputsToSourcesToOverviewAndRootBackReturnsToStart() {
        openAllApps()
        compose.onNodeWithTag("launcher-entry-data").performScrollTo().assertIsDisplayed().performClick()
        await("data-root")
        compose.onNodeWithTag("data-section-inputs").performClick()
        await("data-inputs")
        compose.onNodeWithTag("data-section-sources").performClick()
        await("data-sources")
        compose.onNodeWithTag("data-section-overview").performClick()
        await("data-overview")

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun dataAppOwnsPinningAndItsDeclaredThreeSizeCycle() {
        openAllApps()
        compose.onNodeWithTag("launcher-entry-data").performScrollTo().performTouchInput { longClick() }
        await("launcher-context-pin")
        compose.onNodeWithTag("launcher-context-pin").performClick()

        await("tile-data")
        await("data-tile-medium", unmerged = true)
        compose.onNodeWithTag("tile-data").performTouchInput { longClick() }
        await("resize-selected-tile")
        compose.onNodeWithTag("resize-selected-tile").performClick()
        await("data-tile-wide", unmerged = true)

        compose.onNodeWithTag("resize-selected-tile").performClick()
        await("data-tile-small", unmerged = true)
    }

    @Test
    fun dataRendererSurvivesThreeSizesTwoThemesAndLargeType() {
        val cases: List<Pair<String, @Composable () -> LauncherEntryVisualContribution>> = listOf(
            "data" to { dataLauncherVisualContribution(NmeaRuntimeSnapshot.EMPTY, MarineSourceSnapshot.EMPTY) },
        )
        val tagSuffix = mapOf(
            MarineTileSize.ICON_1X1 to "small",
            MarineTileSize.STANDARD_2X2 to "medium",
            MarineTileSize.WIDE_4X2 to "wide",
        )
        cases.forEach { (app, contribution) ->
            WpThemeMode.entries.forEach { mode ->
                MarineTileSize.entries.forEach { size ->
                    compose.activityRule.scenario.onActivity { activity ->
                        activity.setContent {
                            val platformDensity = LocalDensity.current
                            CompositionLocalProvider(
                                LocalDensity provides Density(platformDensity.density, 1.6f),
                            ) {
                                YokuliTheme(WpThemeSpec(mode = mode)) {
                                    val colors = LocalWpTheme.current
                                    val visual = contribution()
                                    Box(
                                        Modifier.requiredSize(
                                            width = if (size == MarineTileSize.WIDE_4X2) 320.dp else 152.dp,
                                            height = if (size == MarineTileSize.ICON_1X1) 76.dp else 152.dp,
                                        ),
                                    ) {
                                        visual.tileRenderers.getValue(size).Render(
                                            LauncherTileRenderContext(
                                                size = size,
                                                contentColor = colors.onAccent,
                                                modifier = Modifier.fillMaxSize(),
                                                liveContentEnabled = true,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    await("$app-tile-${tagSuffix.getValue(size)}", unmerged = true)
                }
            }
        }
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

package com.yokuli.marine.shell

import android.graphics.Color
import android.content.Intent
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.yokuli.marine.feature.chart.ChartImportUiState
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.MapRecoveryExportUiState
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.offline.OfflineMapInstanceMetrics
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherRecoveryMode
import com.yokuli.shell.engine.ShellVisualSurface
import com.yokuli.shell.engine.LauncherEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
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
        lateinit var resetJob: Job
        lateinit var engine: LauncherEngine
        compose.activityRule.scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[ShellViewModel::class.java]
            engine = viewModel.engine
            resetJob = viewModel.resetLauncher()
        }
        runBlocking { resetJob.join() }
        engine.dispatch(LauncherAction.EnterSafeMode)
        compose.waitUntil(10_000) {
            engine.state.value.recoveryMode == LauncherRecoveryMode.SAFE_MODE
        }
        engine.dispatch(LauncherAction.ExitSafeMode)
        engine.dispatch(LauncherAction.ShowDesktop)
        compose.waitUntil(10_000) {
            engine.state.value.recoveryMode == LauncherRecoveryMode.NORMAL &&
                engine.state.value.surface == ShellVisualSurface.Desktop
        }
        awaitDisplayed("start-screen")
        awaitDisplayed("tile-chart")
        awaitDisplayed("tile-settings")
    }

    @Test
    fun chartTileOpensBrowseOnlySurfaceAndSystemBackReturnsToStart() {
        compose.onNodeWithTag("tile-chart").assertIsDisplayed().performClick()

        awaitDisplayed("chart-workspace-browse")
        compose.onNodeWithTag("chart-workspace-browse").assertIsDisplayed()
        compose.onNodeWithTag("wp-page-title-chart").assertIsDisplayed()
        compose.onNodeWithTag("chart-surface-maplibre").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun fiftyMapSearchBridgeTransitionsDoNotAccumulateNativeMapViews() {
        compose.waitUntil(timeoutMillis = 10_000) { OfflineMapInstanceMetrics.liveCount == 0 }
        OfflineMapInstanceMetrics.resetForTest()

        repeat(25) {
            compose.onNodeWithTag("tile-chart").performClick()
            awaitDisplayed("chart-surface-maplibre")
            compose.waitUntil(timeoutMillis = 10_000) { OfflineMapInstanceMetrics.liveCount == 1 }

            compose.onNodeWithTag("virtual-key-search").performClick()
            awaitDisplayed("shell-search-surface")
            compose.waitUntil(timeoutMillis = 10_000) { OfflineMapInstanceMetrics.liveCount == 0 }
            compose.onNodeWithTag("search-result-chart").performClick()
            awaitDisplayed("chart-surface-maplibre")
            compose.waitUntil(timeoutMillis = 10_000) { OfflineMapInstanceMetrics.liveCount == 1 }

            compose.onNodeWithTag("virtual-key-bridge").performClick()
            awaitDisplayed("start-screen")
            compose.waitUntil(timeoutMillis = 10_000) { OfflineMapInstanceMetrics.liveCount == 0 }
        }

        assertEquals(0, OfflineMapInstanceMetrics.liveCount)
        assertEquals(1, OfflineMapInstanceMetrics.peakLiveCount)
        assertEquals(50, OfflineMapInstanceMetrics.createdCount)
    }

    @Test
    fun failedMapSaveExposesRetryAndExplicitRecoveryExportWithoutClaimingSaved() {
        var retryRequested = false
        var exportRequested = false
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    ChartWorkspace(
                        state = MapState(
                            libraryLoadState = MapLibraryLoadState.READY,
                            libraryRevision = 3L,
                            durableLibraryRevision = 2L,
                            saveState = MapSaveState.FAILED,
                        ),
                        onAction = { if (it == MapAction.RetryPersistence) retryRequested = true },
                        importState = ChartImportUiState.Idle,
                        onImportAction = {},
                        recoveryExportState = MapRecoveryExportUiState.SUCCEEDED,
                        onExportRecovery = { exportRequested = true },
                        chartSurface = { _, _, modifier -> Box(modifier) },
                    )
                }
            }
        }

        compose.onNodeWithTag("map-persistence-truth").assertIsDisplayed()
        compose.onNodeWithTag("map-library-retry-save").assertIsDisplayed().performClick()
        compose.onNodeWithTag("map-library-export-recovery").assertIsDisplayed().performClick()
        compose.onNodeWithTag("map-recovery-export-state").assertIsDisplayed()

        compose.runOnIdle {
            assertTrue(retryRequested)
            assertTrue(exportRequested)
        }
    }

    @Test
    fun mapAppKeepsPlanningToolsInternalAndPositionTruthExplicit() {
        compose.onNodeWithTag("tile-chart").performClick()
        awaitDisplayed("map-tool-bar")
        compose.onNodeWithTag("map-position-truth").assertIsDisplayed()

        compose.onNodeWithTag("map-tool-manual_route").performClick()
        compose.activityRule.scenario.onActivity { activity ->
            val mapState = ViewModelProvider(activity)[ShellViewModel::class.java].mapStore.state.value
            assertEquals(com.yokuli.marine.map.domain.MapTool.MANUAL_ROUTE, mapState.tool)
            assertEquals(com.yokuli.marine.map.domain.PositionAvailability.UNAVAILABLE, mapState.position.availability)
        }
        compose.onNodeWithTag("map-tool-charts").performClick()
        compose.activityRule.scenario.onActivity { activity ->
            val mapState = ViewModelProvider(activity)[ShellViewModel::class.java].mapStore.state.value
            assertEquals(com.yokuli.marine.map.domain.MapTool.CHARTS, mapState.tool)
        }
        compose.onNodeWithTag("launcher-entry-routes").assertDoesNotExist()
        compose.onNodeWithTag("launcher-entry-charts").assertDoesNotExist()
    }

    @Test
    fun productionShellExposesOnlyChartAndSettingsWithReusableLargeTitles() {
        compose.onNodeWithTag("tile-chart").performClick()
        awaitDisplayed("wp-page-title-chart")
        compose.onNodeWithTag("wp-page-title-chart").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("tile-settings")

        compose.onNodeWithTag("tile-settings").performClick()
        awaitDisplayed("wp-page-title-settings")
        compose.onNodeWithTag("wp-page-title-settings").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("all-apps-entry")

        compose.onNodeWithTag("all-apps-entry").performClick()
        awaitDisplayed("all-apps-list")
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
        awaitDisplayed("all-apps-list")
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
        awaitDisplayed("unpin-selected-tile")
        compose.onNodeWithTag("interactive-launcher-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("all-apps-list").assertIsNotDisplayed()
    }

    @Test
    fun sameLongPressGestureCanLiftReorderAndDropATile() {
        compose.activityRule.scenario.onActivity { activity ->
            val order = ViewModelProvider(activity)[ShellViewModel::class.java].engine.state.value
                .start.document.placements.sortedBy { it.rank }.map { it.entryId.value }
            assertEquals(listOf("chart", "settings"), order)
        }

        compose.onNodeWithTag("tile-settings").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveTo(Offset(center.x, center.y - height), delayMillis = 250)
            moveTo(Offset(center.x, -height * 3f), delayMillis = 500)
            up()
        }

        compose.waitUntil(5_000) {
            var reordered = false
            compose.activityRule.scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[ShellViewModel::class.java].engine.state.value
                val selected = state.start.interaction as?
                    com.yokuli.shell.engine.interaction.StartInteractionState.EditIdle
                reordered = state.start.document.placements.sortedBy { it.rank }
                    .map { it.entryId.value } == listOf("settings", "chart") &&
                    selected?.selectedTile?.value == "tile-settings"
            }
            reordered
        }
        awaitExists("resize-selected-tile")
    }

    @Test
    fun systemBackExitsEditModeBeforeLeavingStart() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        awaitDisplayed("unpin-selected-tile")
        compose.onNodeWithTag("unpin-selected-tile").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        awaitGone("unpin-selected-tile")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("unpin-selected-tile").assertDoesNotExist()
    }

    @Test
    fun chartResizeCommitsOnOneTapWithoutConfirmationUi() {
        compose.onNodeWithTag("tile-chart").performTouchInput { longClick() }
        awaitExists("resize-selected-tile")
        val sizes = listOf(
            com.yokuli.shell.contract.MarineTileSize.ICON_1X1,
            com.yokuli.shell.contract.MarineTileSize.STANDARD_2X2,
            com.yokuli.shell.contract.MarineTileSize.WIDE_4X2,
        )
        sizes.forEach { expected ->
            compose.onNodeWithTag("resize-selected-tile").performTouchInput { click(center) }
            compose.waitUntil(5_000) {
                var matches = false
                compose.activityRule.scenario.onActivity { activity ->
                    val state = ViewModelProvider(activity)[ShellViewModel::class.java].engine.state.value
                    matches = state.start.document.placements.single { it.entryId.value == "chart" }.size == expected &&
                        state.start.activeTransaction == null
                }
                matches
            }
            compose.onNodeWithTag("resize-selected-tile").assertIsDisplayed()
            compose.onNodeWithTag("commit-tile-resize").assertDoesNotExist()
            compose.onNodeWithTag("cancel-tile-resize").assertDoesNotExist()
        }
    }

    @Test
    fun smallTileEditControlsAreVisiblyUsableAndHave48DpHitTargets() {
        compose.activityRule.scenario.onActivity { activity ->
            val engine = ViewModelProvider(activity)[ShellViewModel::class.java].engine
            val tileId = engine.state.value.start.document.placements
                .single { it.entryId.value == "settings" }.tileId
            engine.dispatch(LauncherAction.EnterStartEdit(tileId))
        }
        compose.waitUntil(5_000) {
            var selected = false
            compose.activityRule.scenario.onActivity { activity ->
                val interaction = ViewModelProvider(activity)[ShellViewModel::class.java]
                    .engine.state.value.start.interaction as?
                    com.yokuli.shell.engine.interaction.StartInteractionState.EditIdle
                selected = interaction?.selectedTile?.value == "tile-settings"
            }
            selected
        }
        awaitExists("resize-selected-tile")
        var density = 1f
        compose.activityRule.scenario.onActivity { density = it.resources.displayMetrics.density }

        val unpin = compose.onNodeWithTag("unpin-selected-tile").fetchSemanticsNode().boundsInRoot
        val resize = compose.onNodeWithTag("resize-selected-tile").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(unpin.width >= 48f * density && unpin.height >= 48f * density)
        assertTrue(resize.width >= 48f * density && resize.height >= 48f * density)
        assertTrue(root.contains(unpin.center))
        assertTrue(root.contains(resize.center))
        val disc = compose.onNodeWithTag("resize-affordance-disc", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val glyph = compose.onNodeWithTag("resize-affordance-glyph", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(disc.width >= 28f * density && disc.height >= 28f * density)
        assertTrue(glyph.width >= 19f * density && glyph.height >= 19f * density)

        compose.onNodeWithTag("resize-selected-tile").performClick()
        compose.activityRule.scenario.onActivity { activity ->
            val state = ViewModelProvider(activity)[ShellViewModel::class.java].engine.state.value
            assertEquals(
                com.yokuli.shell.contract.MarineTileSize.STANDARD_2X2,
                state.start.document.placements.single { it.entryId.value == "settings" }.size,
            )
            assertTrue(state.start.interaction is com.yokuli.shell.engine.interaction.StartInteractionState.EditIdle)
        }
        compose.waitUntil(5_000) {
            val tileBounds = compose.onNodeWithTag("tile-settings").fetchSemanticsNode().boundsInRoot
            val unpinBounds = compose.onNodeWithTag("unpin-selected-tile").fetchSemanticsNode().boundsInRoot
            abs(tileBounds.width - tileBounds.height) <= 1f && unpinBounds.center.x > tileBounds.center.x
        }
        compose.onNodeWithTag("unpin-selected-tile", useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tile-settings").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun systemBackFromAllAppsReturnsToStart() {
        compose.onNodeWithTag("interactive-launcher-pager").performTouchInput { swipeLeft() }
        awaitDisplayed("all-apps-list")
        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun appearanceUsesOneAccentAndCorrectBlackWhitePageForegroundPolicy() {
        compose.onNodeWithTag("tile-settings").performClick()
        awaitDisplayed("settings-section-appearance")
        compose.onNodeWithTag("settings-section-appearance").performClick()
        awaitDisplayed("theme-accent-magenta")
        compose.onNodeWithTag("theme-accent-magenta").performClick()
        compose.onNodeWithTag("theme-mode-light").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("settings-section-appearance")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("start-screen")

        compose.onNodeWithTag("start-screen").assert(
            SemanticsMatcher.expectValue(WpThemeModeNameKey, "light"),
        )
        listOf("chart", "settings").forEach { id ->
            compose.onNodeWithTag("tile-$id").assert(
                SemanticsMatcher.expectValue(WpTileAccentNameKey, "magenta"),
            )
        }
    }

    @Test
    fun settingsUsesTypographicOverviewAndCompactFourColumnAccentSwatches() {
        compose.onNodeWithTag("tile-settings").performClick()
        awaitDisplayed("settings-overview-list")
        compose.onNodeWithTag("settings-overview-list").assertIsDisplayed()
        compose.onNodeWithTag("settings-accent-bullet").assertDoesNotExist()

        compose.onNodeWithTag("settings-section-appearance").performClick()
        awaitDisplayed("settings-accent-grid")
        val swatches = compose.onAllNodes(
            SemanticsMatcher("accent swatch") { node ->
                node.config.contains(SemanticsProperties.TestTag) &&
                    node.config[SemanticsProperties.TestTag].startsWith("theme-accent-")
            },
        ).fetchSemanticsNodes()
        assertTrue(swatches.size >= 4)

        var density = 1f
        compose.activityRule.scenario.onActivity { density = it.resources.displayMetrics.density }
        swatches.forEach { swatch ->
            assertTrue(swatch.boundsInRoot.width in 44f * density..48f * density + 1f)
            assertTrue(swatch.boundsInRoot.height in 44f * density..48f * density + 1f)
        }
        val firstRowTop = swatches.take(4).map { it.boundsInRoot.top }
        assertTrue(firstRowTop.all { abs(it - firstRowTop.first()) <= 1f })
        compose.onAllNodes(
            SemanticsMatcher("selected accent swatch") { node ->
                node.config.contains(SemanticsProperties.TestTag) &&
                    node.config[SemanticsProperties.TestTag].startsWith("theme-accent-") &&
                    node.config.contains(SemanticsProperties.Selected) &&
                    node.config[SemanticsProperties.Selected]
            },
        ).assertCountEquals(1)
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
        awaitDisplayed("settings-section-appearance")
        compose.onNodeWithTag("settings-section-appearance").performClick()
        awaitDisplayed("theme-mode-light")
        compose.onNodeWithTag("theme-mode-light").performClick()
        awaitHostWindowChrome(Color.WHITE)

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
        awaitDisplayed("all-apps-list")
        compose.onNodeWithTag("launcher-entry-settings").performTouchInput { longClick() }
        awaitDisplayed("launcher-context-menu")
        compose.onNodeWithTag("launcher-context-menu").assertIsDisplayed()
        compose.onNodeWithTag("launcher-context-app-info").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitGone("launcher-context-menu")
        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
    }

    @Test
    fun pinReturnsToStartRevealsTileAndCanUndo() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        awaitDisplayed("unpin-selected-tile")
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            val state = ViewModelProvider(activity)[ShellViewModel::class.java].engine.state.value
            assertTrue(
                "unpin click did not leave the serialized Engine document: ${state.start.interaction}",
                state.start.document.placements.none { it.entryId.value == "settings" },
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tile-settings").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("all-apps-entry").performClick()
        awaitDisplayed("all-apps-list")
        compose.onNodeWithTag("launcher-entry-settings").performTouchInput { longClick() }
        awaitDisplayed("launcher-context-pin")
        compose.onNodeWithTag("launcher-context-pin").performClick()

        awaitDisplayed("tile-settings")
        awaitDisplayed("launcher-undo")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
        compose.onNodeWithTag("launcher-undo").assertIsDisplayed()
        compose.onNodeWithTag("launcher-undo-action").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tile-settings").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun unpinKeepsEntryInstalledAndCanUndo() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        awaitDisplayed("unpin-selected-tile")
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tile-settings").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("launcher-undo").assertIsDisplayed()

        compose.onNodeWithTag("launcher-undo-action").performClick()
        awaitDisplayed("tile-settings")
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
        compose.onNodeWithTag("all-apps-entry").performClick()
        awaitDisplayed("launcher-entry-settings")
        compose.onNodeWithTag("launcher-entry-settings").assertIsDisplayed()
    }

    @Test
    fun virtualBridgeReturnsFromSettingsWithoutDestroyingItsTask() {
        compose.onNodeWithTag("tile-settings").performClick()
        awaitDisplayed("settings-workspace")
        compose.onNodeWithTag("settings-workspace").assertIsDisplayed()

        compose.onNodeWithTag("virtual-key-bridge").performClick()
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("virtual-key-back").performTouchInput { longClick() }

        awaitDisplayed("launcher-recents")
        compose.onNodeWithTag("launcher-recents").assertIsDisplayed()
        compose.onNodeWithTag("recent-task-settings").assertIsDisplayed()
    }

    @Test
    fun searchResultLaunchHasNoIntermediateSurface() {
        compose.onNodeWithTag("virtual-key-search").performClick()
        awaitDisplayed("shell-search-surface")
        compose.onNodeWithTag("shell-search-surface").assertIsDisplayed()
        compose.onNodeWithTag("launcher-search-field").assertIsDisplayed()

        compose.onNodeWithTag("search-result-chart").performClick()
        compose.onNodeWithTag("start-screen").assertDoesNotExist()
        compose.onNodeWithTag("all-apps-list").assertDoesNotExist()
        awaitDisplayed("chart-workspace-browse")
        compose.onNodeWithTag("chart-workspace-browse").assertIsDisplayed()
    }

    @Test
    fun focusedSearchKeepsVirtualBackAndBridgeEscapePathsReachable() {
        compose.onNodeWithTag("virtual-key-search").performClick()
        awaitDisplayed("launcher-search-field")
        compose.onNodeWithTag("launcher-search-field").performTextInput("chart")
        compose.onNodeWithTag("wp-system-key-bar").assertIsDisplayed()
        compose.onNodeWithTag("virtual-key-back").assertIsDisplayed().performClick()
        awaitDisplayed("start-screen")

        compose.onNodeWithTag("virtual-key-search").performClick()
        awaitDisplayed("launcher-search-field")
        compose.onNodeWithTag("launcher-search-field").performTextInput("settings")
        compose.onNodeWithTag("virtual-key-bridge").assertIsDisplayed().performClick()
        awaitDisplayed("start-screen")
    }

    @Test
    fun virtualBackLongPressOpensRecents() {
        compose.onNodeWithTag("tile-chart").performClick()
        awaitDisplayed("chart-workspace-browse")
        compose.onNodeWithTag("virtual-key-bridge").performClick()
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("virtual-key-back").performTouchInput { longClick() }

        awaitDisplayed("launcher-recents")
        compose.onNodeWithTag("launcher-recents").assertIsDisplayed()
        compose.onNodeWithTag("recent-task-chart").performClick()
        awaitDisplayed("chart-workspace-browse")
        compose.onNodeWithTag("chart-workspace-browse").assertIsDisplayed()
    }

    @Test
    fun androidBackAndDeliveredHardwareKeysUseTheUnifiedInputPath() {
        compose.onNodeWithTag("all-apps-entry").performClick()
        awaitDisplayed("all-apps-list")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()

        dispatchHardwareKey(KeyEvent.KEYCODE_SEARCH)
        awaitDisplayed("shell-search-surface")
        compose.onNodeWithTag("shell-search-surface").assertIsDisplayed()
        dispatchHardwareKey(KeyEvent.KEYCODE_BACK)
        awaitGone("shell-search-surface")
        compose.onNodeWithTag("shell-search-surface").assertDoesNotExist()

        compose.onNodeWithTag("tile-settings").performClick()
        awaitDisplayed("settings-workspace")
        dispatchHardwareKey(KeyEvent.KEYCODE_HOME)
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @Test
    fun appRelaunchDoesNotForceDesktopOrRecreateActivity() {
        compose.onNodeWithTag("tile-settings").performClick()
        awaitDisplayed("settings-workspace")
        var before = 0
        compose.activityRule.scenario.onActivity { activity ->
            before = System.identityHashCode(activity)
            activity.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClass(activity, ShellActivity::class.java)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        awaitDisplayed("settings-workspace")
        compose.onNodeWithTag("settings-workspace").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(before, System.identityHashCode(activity))
        }
    }

    @Test
    fun recoverySurfaceCanOpenAndroidSettings() {
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[ShellViewModel::class.java].engine.dispatch(
                LauncherAction.EnterSafeMode,
            )
        }
        awaitDisplayed("launcher-recovery")
        compose.onNodeWithTag("launcher-recovery").assertIsDisplayed()

        var launchedAction: String? = null
        compose.activityRule.scenario.onActivity { activity ->
            activity.platformIntentLauncher = { intent -> launchedAction = intent.action }
        }
        compose.onNodeWithTag("recovery-open-android-settings").performClick()
        compose.waitForIdle()
        assertEquals(android.provider.Settings.ACTION_SETTINGS, launchedAction)
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
                                visualContributions = productionVisualContributions(WpThemeSpec()),
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
    fun square360UsesTheSameExplicitSpatialDocument() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    Box(Modifier.requiredSize(360.dp)) {
                        YokuliStartScreen(
                            state = productionLauncherUiState(
                                productionCatalog.snapshot,
                                defaultStartDocument,
                                visualContributions = productionVisualContributions(WpThemeSpec()),
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
    fun virtualSystemKeysRemainAvailableOnAllApps() {
        compose.onNodeWithTag("all-apps-entry").performClick()

        awaitDisplayed("all-apps-list")
        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()
        compose.onNodeWithTag("wp-system-key-bar").assertIsDisplayed()
        compose.onNodeWithTag("virtual-key-back").assertIsDisplayed()
        compose.onNodeWithTag("virtual-key-bridge").assertIsDisplayed()
        compose.onNodeWithTag("virtual-key-search").assertIsDisplayed()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        listOf(
            "back" to "virtual-key-back",
            "bridge" to "virtual-key-bridge",
            "search" to "virtual-key-search",
        ).forEach { (key, tag) ->
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val left = bounds.left.toInt().coerceIn(0, pixels.width)
            val right = bounds.right.toInt().coerceIn(left, pixels.width)
            val top = bounds.top.toInt().coerceIn(0, pixels.height)
            val bottom = bounds.bottom.toInt().coerceIn(top, pixels.height)
            val visibleGlyphPixels = (left until right).sumOf { x ->
                (top until bottom).count { y ->
                    pixels[x, y].let { color ->
                        color.red > .8f && color.green > .8f && color.blue > .8f
                    }
                }
            }
            assertTrue("virtual $key has no visible white glyph pixels on All Apps", visibleGlyphPixels > 8)
        }
    }

    @Test
    fun virtualBackDismissesAlphabetJumpBeforeLeavingAllApps() {
        compose.onNodeWithTag("all-apps-entry").performClick()
        awaitDisplayed("all-apps-list")
        compose.onAllNodes(
            SemanticsMatcher("alphabet group") { node ->
                node.config.contains(SemanticsProperties.TestTag) &&
                    node.config[SemanticsProperties.TestTag].startsWith("alphabet-group-")
            },
        )[0].performClick()

        awaitDisplayed("alphabet-jump-overlay")
        compose.onNodeWithTag("alphabet-jump-overlay").assertIsDisplayed()
        compose.onNodeWithTag("virtual-key-back").performClick()

        awaitGone("alphabet-jump-overlay")
        compose.onNodeWithTag("alphabet-jump-overlay").assertDoesNotExist()
        compose.onNodeWithTag("all-apps-list").assertIsDisplayed()
    }

    @Test
    fun activityRecreationRetainsTheEngineDocument() {
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        awaitDisplayed("resize-selected-tile")
        compose.onNodeWithTag("wp-page-title-settings").assertDoesNotExist()
        compose.onNodeWithTag("unpin-selected-tile").performClick()
        awaitGone("tile-settings")
        compose.onNodeWithTag("tile-settings").assertDoesNotExist()

        compose.activityRule.scenario.recreate()

        awaitDisplayed("start-screen")
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
        awaitDisplayed("settings-section-language")
        compose.onNodeWithTag("settings-section-language").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()

        selectLanguage("zh-CN")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("应用语言").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("settings-section-language")
        compose.onNodeWithTag("settings-section-language").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
        compose.onNodeWithTag("tile-chart").assertIsDisplayed()
    }

    private fun selectLanguage(tag: String) {
        compose.onNodeWithTag("tile-settings").performClick()
        awaitDisplayed("settings-section-language")
        compose.onNodeWithTag("settings-section-language").performClick()
        awaitDisplayed("language-$tag")
        compose.onNodeWithTag("language-$tag").performClick()
        compose.waitForIdle()
    }

    private fun awaitDisplayed(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
        }
    }

    private fun awaitExists(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    @Suppress("DEPRECATION")
    private fun awaitHostWindowChrome(color: Int) {
        compose.waitUntil(timeoutMillis = 10_000) {
            var matches = false
            compose.activityRule.scenario.onActivity { activity ->
                matches = activity.window.statusBarColor == color &&
                    activity.window.navigationBarColor == color
            }
            matches
        }
    }

    private fun dispatchHardwareKey(keyCode: Int) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        compose.waitForIdle()
    }
}

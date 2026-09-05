package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.core.design.WpThemePolicy
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.desktop.LauncherUiAction
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.feature.desktop.productionLauncherUiState
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEngine
import com.yokuli.shell.engine.LauncherRecoveryMode
import com.yokuli.shell.engine.ShellVisualSurface
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.StartDocument
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Physical input on the production Start renderer and real Activity/Engine, not a user-facing Lab. */
@RunWith(AndroidJUnit4::class)
class ShellEditRegressionStoryTest {
    @get:Rule val compose = createAndroidComposeRule<ShellActivity>()
    private lateinit var engine: LauncherEngine
    private var viewportHeight by mutableStateOf(320)
    private var density = 1f
    private val settingsId = TileInstanceId("tile-settings")

    @Before fun resetProductionState() {
        lateinit var reset: Job
        compose.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[ShellViewModel::class.java]
            engine = model.engine
            density = activity.resources.displayMetrics.density
            reset = model.resetLauncher()
        }
        runBlocking { reset.join() }
        engine.dispatch(LauncherAction.EnterSafeMode)
        await { engine.state.value.recoveryMode == LauncherRecoveryMode.SAFE_MODE }
        engine.dispatch(LauncherAction.ExitSafeMode)
        engine.dispatch(LauncherAction.ShowDesktop)
        await { engine.state.value.recoveryMode == LauncherRecoveryMode.NORMAL && engine.state.value.surface == ShellVisualSurface.Desktop }
        compose.onNodeWithTag("start-screen").assertIsDisplayed()
    }

    @After fun releaseHarnessState() {
        compose.mainClock.autoAdvance = true
        if (::engine.isInitialized) engine.dispatch(LauncherAction.CancelTileOperation)
    }

    @Test fun smallestTileNativeResizeCycleKeepsDistinctTargetsAndCanDragFromItsCenter() {
        installProductionStartViewport()
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        await { engine.state.value.start.interaction is StartInteractionState.EditIdle }
        compose.waitForIdle()
        val sizes = engine.state.value.catalog.entries.single { it.entryId.value == "settings" }.supportedSizes
        val initial = document().placements.single { it.tileId == settingsId }.size
        repeat(sizes.size) { index ->
            assertPhysicalControls()
            compose.onNodeWithTag("resize-selected-tile").performTouchInput { click(center) }
            val expected = sizes[(sizes.indexOf(initial) + index + 1) % sizes.size]
            await { document().placements.single { it.tileId == settingsId }.size == expected }
            compose.waitForIdle()
            compose.onNodeWithTag("commit-tile-resize").assertDoesNotExist()
            compose.onNodeWithTag("cancel-tile-resize").assertDoesNotExist()
        }
        assertEquals(initial, document().placements.single { it.tileId == settingsId }.size)
        assertPhysicalControls()
        val before = document()
        val start = localCenter("tile-settings")
        val end = Offset(start.x, 80f * density)
        compose.onNodeWithTag("start-screen").performTouchInput {
            down(start)
            moveTo(Offset(start.x, start.y - 24f * density), delayMillis = 100)
            moveTo(end, delayMillis = 200)
        }
        awaitPreviewBeforeChart()
        compose.waitForIdle()
        assertEquals(before, document()) // Preview is not a durable commit.
        assertNear(end, localCenter("tile-settings"))
        assertFloatingTileOnTop()
        compose.onNodeWithTag("start-screen").performTouchInput { up() }
        await { order(document()) == listOf("settings", "chart") && engine.state.value.start.interaction is StartInteractionState.EditIdle }
        val committed = document()
        await {
            var saved = false
            compose.activityRule.scenario.onActivity { activity ->
                saved = (activity.application as ShellApplication).launcherPersistence.document.value == committed
            }
            saved
        }
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { engine = ViewModelProvider(it)[ShellViewModel::class.java].engine }
        await { engine.state.value.recoveryMode == LauncherRecoveryMode.NORMAL && document() == committed }
        compose.onNodeWithTag("tile-settings").assertIsDisplayed()
    }

    @Test fun stationaryLongPressNearBottomDoesNotScrollReorderOrCreateUndo() {
        installProductionStartViewport(height = 220)
        val before = document()
        val undoCount = engine.state.value.start.undoStack.size
        val scrollBefore = scrollValue()
        val start = localCenter("tile-settings")
        assertTrue(start.y > 172f * density && start.y < 220f * density)
        compose.onNodeWithTag("start-screen").performTouchInput {
            down(start)
            moveTo(start, delayMillis = 700)
        }
        await { engine.state.value.start.interaction is StartInteractionState.Dragging }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(scrollBefore, scrollValue(), .5f)
        assertEquals(before, document())
        compose.onNodeWithTag("start-screen").performTouchInput { up() }
        await { engine.state.value.start.interaction is StartInteractionState.EditIdle }
        compose.waitForIdle()
        assertEquals(before, document())
        assertEquals(undoCount, engine.state.value.start.undoStack.size)
        compose.onNodeWithTag("resize-selected-tile").assertIsDisplayed()
    }

    @Test fun edgeScrollPreservesFingerPositionAndBackMakesLateUpHarmless() {
        installProductionStartViewport(height = 220)
        val before = document()
        compose.onNodeWithTag("tile-settings").performTouchInput { longClick() }
        await { engine.state.value.start.interaction is StartInteractionState.EditIdle }
        compose.waitForIdle()
        val start = localCenter("tile-settings")
        val end = Offset(start.x + 24f * density, 185f * density)
        val scrollBefore = scrollValue()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("start-screen").performTouchInput {
                down(start)
                moveTo(end, delayMillis = 150)
            }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            assertTrue("Fixture must actually scroll", scrollValue() > scrollBefore + 1f)
            assertNear(end, localCenter("tile-settings"))
            val next = end + Offset(4f * density, -3f * density)
            compose.onNodeWithTag("start-screen").performTouchInput { moveTo(next, delayMillis = 100) }
            compose.mainClock.advanceTimeBy(120)
            compose.waitForIdle()
            assertNear(next, localCenter("tile-settings"))
            compose.runOnIdle { engine.dispatch(LauncherAction.Back) }
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("start-screen").performTouchInput { up() }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertEquals(before, document())
        assertEquals(ShellVisualSurface.Desktop, engine.state.value.surface)
        assertTrue(engine.state.value.start.interaction is StartInteractionState.EditIdle)
    }

    @Test fun viewportChangeCancelsAnUncommittedPreviewAndLateUpDoesNotLaunch() {
        installProductionStartViewport()
        val before = document()
        holdThenPreviewBeforeChart()
        compose.runOnIdle { viewportHeight = 280 }
        await { engine.state.value.start.interaction !is StartInteractionState.Dragging }
        compose.onNodeWithTag("start-screen").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(before, document())
        assertEquals(ShellVisualSurface.Desktop, engine.state.value.surface)
    }

    @Test fun catalogChangeCancelsTheAcceptedFingerWithoutASecondCommit() {
        installProductionStartViewport()
        val before = document()
        holdThenPreviewBeforeChart()
        engine.dispatch(LauncherAction.CatalogChanged(engine.state.value.catalog.let { it.copy(revision = it.revision + 1) }))
        await { engine.state.value.start.interaction !is StartInteractionState.Dragging }
        compose.onNodeWithTag("start-screen").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(before, document())
        assertEquals(ShellVisualSurface.Desktop, engine.state.value.surface)
    }

    @Test fun dailyDebugSettingsDoesNotExposeALabEntry() {
        compose.onNodeWithTag("tile-settings").performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("settings-section-start_screen").performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("settings-reset-start").assertIsDisplayed()
        compose.onNodeWithTag("settings-open-shell-lab").assertDoesNotExist()
    }

    private fun installProductionStartViewport(height: Int = 320) {
        viewportHeight = height
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val current by engine.state.collectAsState()
                val theme = WpThemeSpec()
                YokuliTheme(theme) {
                    Box(Modifier.requiredSize(320.dp, viewportHeight.dp)) {
                        YokuliStartScreen(
                            productionLauncherUiState(
                                catalog = current.catalog, document = current.start.document,
                                interaction = current.start.interaction, transient = current.transient,
                                reveal = current.start.reveal,
                                visualContributions = productionVisualContributions(theme),
                            ), ::dispatch,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun dispatch(action: LauncherUiAction) {
        val mapped = when (action) {
            is LauncherUiAction.Open -> LauncherAction.Open(action.token)
            LauncherUiAction.ShowAllApps -> LauncherAction.ShowAllApps
            is LauncherUiAction.ProposeLayout -> LauncherAction.ApplyLayoutProposal(action.proposal)
            is LauncherUiAction.EnterStartEdit -> LauncherAction.EnterStartEdit(action.tileId)
            is LauncherUiAction.SelectStartTile -> LauncherAction.SelectStartTile(action.tileId)
            LauncherUiAction.ExitStartEdit -> LauncherAction.ExitStartEdit
            is LauncherUiAction.BeginTileDrag -> LauncherAction.BeginTileDrag(action.tileId, action.pointerId, action.grabOffset)
            is LauncherUiAction.InsertionTargetChanged -> LauncherAction.InsertionTargetChanged(action.tileId, action.insertionIndex)
            is LauncherUiAction.DropTile -> LauncherAction.DropTile(action.tileId)
            LauncherUiAction.CancelTileOperation -> LauncherAction.CancelTileOperation
            is LauncherUiAction.ResizeTile -> LauncherAction.ResizeTile(action.tileId)
            is LauncherUiAction.MoveTileBy -> LauncherAction.MoveTileBy(action.tileId, action.columns, action.rows)
            is LauncherUiAction.UnpinTile -> LauncherAction.UnpinTile(action.tileId)
            is LauncherUiAction.PinEntry -> LauncherAction.PinEntry(action.entryId)
            is LauncherUiAction.AcknowledgeStartReveal -> LauncherAction.AcknowledgeStartReveal(action.tileId)
            LauncherUiAction.UndoLayout -> LauncherAction.UndoLayout
            LauncherUiAction.DismissTransient -> LauncherAction.DismissTransient
            else -> return
        }
        engine.dispatch(mapped)
    }

    private fun holdThenPreviewBeforeChart() {
        val start = localCenter("tile-settings")
        compose.onNodeWithTag("start-screen").performTouchInput {
            down(start)
            moveTo(start, delayMillis = 700)
            moveTo(Offset(start.x, 80f * density), delayMillis = 250)
        }
        awaitPreviewBeforeChart()
        compose.waitForIdle()
    }
    private fun awaitPreviewBeforeChart() = await {
        (engine.state.value.start.interaction as? StartInteractionState.Dragging)?.proposedLayout?.let(::order) == listOf("settings", "chart")
    }
    private fun assertPhysicalControls() {
        val root = bounds("start-screen")
        val tile = bounds("tile-settings")
        val unpin = bounds("unpin-selected-tile")
        val resize = bounds("resize-selected-tile")
        assertFalse(unpin.overlaps(resize))
        listOf(unpin, resize).forEach { rect ->
            assertTrue(rect.width >= 48f * density - .5f && rect.height >= 48f * density - .5f)
            assertTrue(rect.left >= root.left - .5f && rect.top >= root.top - .5f)
            assertTrue(rect.right <= root.right + .5f && rect.bottom <= root.bottom + .5f)
            assertFalse("Selected tile center remains a physical drag target", rect.contains(tile.center))
        }
    }
    private fun assertFloatingTileOnTop() {
        val root = bounds("start-screen")
        val tile = bounds("tile-settings")
        val image = compose.onNodeWithTag("start-screen").captureToImage().toPixelMap()
        val x = (tile.left - root.left + 6f * density).toInt().coerceIn(0, image.width - 1)
        val y = (tile.top - root.top + 6f * density).toInt().coerceIn(0, image.height - 1)
        val expected = WpThemePolicy.resolve(WpThemeSpec()).accent
        val pixel = image[x, y]
        assertEquals(expected.red, pixel.red, .05f)
        assertEquals(expected.green, pixel.green, .05f)
        assertEquals(expected.blue, pixel.blue, .05f)
    }
    private fun scrollValue(): Float {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "Production Start has no scroll semantics" }
        return nodes.first().config[SemanticsProperties.VerticalScrollAxisRange].value()
    }
    private fun bounds(tag: String): Rect {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        return Rect(node.positionInRoot, androidx.compose.ui.geometry.Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }
    private fun localCenter(tag: String): Offset = bounds(tag).center - bounds("start-screen").topLeft
    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 3f * density)
        assertEquals(expected.y, actual.y, 3f * density)
    }
    private fun document(): StartDocument = engine.state.value.start.document
    private fun order(document: StartDocument): List<String> = document.placements.sortedBy { it.rank }.map { it.entryId.value }
    private fun await(condition: () -> Boolean) = compose.waitUntil(10_000, condition)
}

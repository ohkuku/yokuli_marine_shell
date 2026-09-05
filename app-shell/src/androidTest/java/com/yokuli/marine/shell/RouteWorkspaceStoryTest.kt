package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.chart.ChartImportUiState
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.MapRecoveryExportUiState
import com.yokuli.marine.map.domain.DefaultMapReducer
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapIdGenerator
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RouteWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun drawSaveFindPreviewEditAndSaveSamePlan() {
        val harness = setRouteContent()

        compose.onNodeWithTag("map-route-create").performClick()
        compose.onNodeWithTag("map-route-editor-draft-ui").assertIsDisplayed()
        compose.runOnIdle {
            harness.dispatch(MapAction.AddRouteWaypoint(GeoPoint(-36.8485, 174.7633)))
            harness.dispatch(MapAction.AddRouteWaypoint(GeoPoint(-36.7867, 174.8600)))
        }
        compose.onNodeWithTag("map-route-name-field").performTextClearance()
        compose.onNodeWithTag("map-route-name-field").performTextInput("港湾计划")
        compose.onNodeWithTag("map-route-notes-field").performTextInput("白天目视规划")
        compose.onNodeWithTag("map-route-no-speed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("map-route-save").performScrollTo().performClick()
        compose.runOnIdle { harness.dispatch(MapAction.PersistenceAck(harness.state().libraryRevision)) }

        compose.onNodeWithTag("map-route-preview-route-ui").assertIsDisplayed()
        compose.runOnIdle {
            val plan = harness.state().savedRoutes.single()
            assertEquals("route-ui", plan.id)
            assertEquals("港湾计划", plan.name)
            assertEquals("白天目视规划", plan.notes)
            assertNull(plan.plannedSpeedKnots)
            assertFalse(harness.state().navigationActive)
        }

        compose.runOnIdle { harness.dispatch(MapAction.OpenSurface(MapSurface.Routes)) }
        compose.onNodeWithTag("map-route-plan-route-ui").performScrollTo().performClick()
        compose.onNodeWithTag("map-route-preview-route-ui").assertIsDisplayed()
        compose.onNodeWithTag("map-route-edit-route-ui").performScrollTo().performClick()
        compose.onNodeWithTag("map-route-editor-draft-edit").assertIsDisplayed()
        compose.onNodeWithTag("map-route-notes-field").performTextInput(" / revised")
        compose.onNodeWithTag("map-route-speed-field").performTextInput("6.5")
        compose.onNodeWithTag("map-route-save").performScrollTo().performClick()
        compose.runOnIdle { harness.dispatch(MapAction.PersistenceAck(harness.state().libraryRevision)) }

        compose.onNodeWithTag("map-route-preview-route-ui").assertIsDisplayed()
        compose.onNodeWithTag("map-route-name").assertTextContains("港湾计划")
        compose.runOnIdle {
            assertEquals(listOf("route-ui"), harness.state().savedRoutes.map { it.id })
            assertEquals(2L, harness.state().savedRoutes.single().revision)
            assertEquals(6.5, harness.state().savedRoutes.single().plannedSpeedKnots)
        }
    }

    private fun setRouteContent(): Harness {
        val ids = ArrayDeque(listOf("draft-ui", "route-ui", "draft-edit"))
        val reducer = DefaultMapReducer(MapIdGenerator { namespace -> ids.removeFirstOrNull() ?: "$namespace-fallback" })
        val initial = MapState(surface = MapSurface.Routes, libraryLoadState = MapLibraryLoadState.READY_EMPTY)
        var stateAccessor: () -> MapState = { initial }
        var dispatcher: (MapAction) -> Unit = {}
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    var state by remember(initial) { mutableStateOf(initial) }
                    stateAccessor = { state }
                    dispatcher = { action -> state = reducer.reduce(state, action).state }
                    ChartWorkspace(
                        state = state,
                        currentState = { state },
                        onAction = dispatcher,
                        importState = ChartImportUiState.Idle,
                        onImportAction = {},
                        recoveryExportState = MapRecoveryExportUiState.IDLE,
                        onExportRecovery = {},
                        onExportPlace = {},
                        chartSurface = { _, _, _, modifier -> Box(modifier) },
                    )
                }
            }
        }
        compose.waitForIdle()
        return Harness({ stateAccessor() }, { dispatcher(it) })
    }

    private data class Harness(val state: () -> MapState, val dispatch: (MapAction) -> Unit)
}

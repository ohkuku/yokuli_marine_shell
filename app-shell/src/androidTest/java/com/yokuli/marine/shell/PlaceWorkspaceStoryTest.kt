package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
import com.yokuli.marine.map.domain.MapClock
import com.yokuli.marine.map.domain.MapIdGenerator
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.MapTransient
import com.yokuli.marine.map.domain.PlaceCategory
import com.yokuli.marine.map.domain.PlaceRevisionReference
import com.yokuli.marine.map.domain.PointCandidateOrigin
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaceWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun candidateCollectsCompleteFieldsBeforeSaveThenListAndDetailCanFindIt() {
        val point = GeoPoint(-36.8485, 174.7633)
        val harness = setPlaceContent(
            MapState(
                transient = MapTransient.PointCandidate(point, PointCandidateOrigin.MAP_LONG_PRESS),
                libraryLoadState = MapLibraryLoadState.READY_EMPTY,
            ),
        )

        compose.onNodeWithTag("map-candidate-save").performClick()
        compose.onNodeWithTag("map-place-editor").assertIsDisplayed()
        compose.onNodeWithTag("map-place-name-field").performTextClearance()
        compose.onNodeWithTag("map-place-name-field").performTextInput("西港 Marina")
        compose.onNodeWithTag("map-place-notes-field").performTextInput("Gate B 夜间")
        compose.onNodeWithTag("map-place-tags-field").performTextInput("fuel, 补水")
        compose.onNodeWithTag("map-place-category-MARINA").performScrollTo().performClick()
        compose.onNodeWithTag("map-place-save").performScrollTo().performClick()

        compose.onNodeWithTag("map-place-detail-place-ui").assertIsDisplayed()
        compose.runOnIdle {
            val place = harness.state().places.single()
            assertEquals("西港 Marina", place.name)
            assertEquals("Gate B 夜间", place.notes)
            assertEquals(PlaceCategory.MARINA, place.category)
            assertEquals(listOf("fuel", "补水"), place.tags)
            assertEquals(point, place.point)
        }

        compose.runOnIdle { harness.dispatch(MapAction.CloseSurface) }
        compose.onNodeWithTag("map-places-page").assertIsDisplayed()
        compose.onNodeWithTag("map-places-search-field").performTextInput("补水")
        compose.onNodeWithTag("map-place-row-place-ui").assertIsDisplayed().performClick()
        compose.onNodeWithTag("map-place-detail-place-ui").assertIsDisplayed()
    }

    @Test
    fun explicitMoveDeleteAndUndoPreserveRouteSnapshotAndStablePlaceId() {
        val original = GeoPoint(-36.8485, 174.7633)
        val routePoint = GeoPoint(-36.80, 174.82)
        val place = SavedPlace(
            id = "place-a",
            name = "锚地 A",
            point = original,
            revision = 3L,
            notes = "避风",
            category = PlaceCategory.ANCHORAGE,
            createdAtMillis = 10L,
            updatedAtMillis = 20L,
        )
        val route = SavedRoute(
            id = "route-a",
            name = "计划",
            waypoints = listOf(original, routePoint),
            plannedSpeedKnots = 5.0,
            waypointPlaceReferences = mapOf(0 to PlaceRevisionReference(place.id, place.revision)),
        )
        val harness = setPlaceContent(
            MapState(
                surface = MapSurface.PlaceDetail(place.id),
                surfaceHistory = listOf(MapSurface.Root, MapSurface.Places),
                places = listOf(place),
                savedRoutes = listOf(route),
                libraryLoadState = MapLibraryLoadState.READY,
            ),
        )

        compose.onNodeWithTag("map-place-move-place-a").performScrollTo().performClick()
        compose.onNodeWithTag("map-place-move-editor").assertIsDisplayed()
        compose.onNodeWithTag("map-place-move-latitude").performTextClearance()
        compose.onNodeWithTag("map-place-move-latitude").performTextInput("36.84 S")
        compose.onNodeWithTag("map-place-move-longitude").performTextClearance()
        compose.onNodeWithTag("map-place-move-longitude").performTextInput("174.77 E")
        compose.onNodeWithTag("map-place-move-preview").performClick()
        compose.onNodeWithTag("map-place-move-confirm").performClick()
        compose.runOnIdle {
            assertEquals(GeoPoint(-36.84, 174.77), harness.state().places.single().point)
            assertEquals(listOf(original, routePoint), harness.state().savedRoutes.single().waypoints)
        }

        compose.onNodeWithTag("map-place-delete-place-a").performScrollTo().performClick()
        compose.onNodeWithTag("map-place-delete-confirmation").assertIsDisplayed()
        compose.onNodeWithTag("map-place-delete-confirm").performClick()
        compose.onNodeWithTag("map-place-delete-undo-strip").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(emptyList<SavedPlace>(), harness.state().places)
            assertEquals(listOf(original, routePoint), harness.state().savedRoutes.single().waypoints)
        }
        compose.onNodeWithTag("map-place-delete-undo").performClick()
        compose.onNodeWithTag("map-place-row-place-a").assertIsDisplayed()
        compose.onNodeWithTag("map-place-delete-undo-strip").assertDoesNotExist()
        compose.runOnIdle { assertEquals("place-a", harness.state().places.single().id) }
    }

    private fun setPlaceContent(initial: MapState): Harness {
        val reducer = DefaultMapReducer(
            idGenerator = MapIdGenerator { "place-ui" },
            clock = MapClock { 1_700_000_000_000L },
        )
        var stateAccessor: () -> MapState = { initial }
        var dispatcher: (MapAction) -> Unit = {}
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    var state by remember { mutableStateOf(initial) }
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
                        chartSurface = { _, _, _, modifier -> Box(modifier) },
                    )
                }
            }
        }
        compose.waitForIdle()
        return Harness({ stateAccessor() }, { dispatcher(it) })
    }

    private data class Harness(
        val state: () -> MapState,
        val dispatch: (MapAction) -> Unit,
    )
}

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
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.chart.ChartImportUiAction
import com.yokuli.marine.feature.chart.ChartImportUiState
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.MapRecoveryExportUiState
import com.yokuli.marine.feature.chart.OfflineCoverageUiState
import com.yokuli.marine.map.domain.ContentFootprint
import com.yokuli.marine.map.domain.DefaultMapReducer
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.NavigationSuitability
import com.yokuli.marine.map.domain.OfflineCoverageFingerprint
import com.yokuli.marine.map.domain.OfflineCoverageRequest
import com.yokuli.marine.map.domain.OfflineCoverageResult
import com.yokuli.marine.map.domain.SavedRoute
import com.yokuli.marine.map.domain.SlippyTileKey
import com.yokuli.marine.map.domain.TileAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OfflineCoverageWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun savedRouteOpensTruthfulCoverageFactsAndImportPath() {
        val route = SavedRoute(
            id = "route-coverage",
            name = "Harbour plan",
            waypoints = listOf(GeoPoint(-36.85, 174.76), GeoPoint(-36.79, 174.86)),
        )
        val request = OfflineCoverageRequest(
            routeId = route.id,
            routeRevision = route.revision,
            routePoints = route.waypoints,
            packageVersionIds = listOf(com.yokuli.marine.map.domain.ChartPackageVersionId("a".repeat(64))),
            targetZoom = 12,
        )
        val missing = SlippyTileKey(12, 4035, 2568)
        val coverage = OfflineCoverageUiState.Ready(
            request,
            OfflineCoverageResult(
                fingerprint = OfflineCoverageFingerprint.of(request),
                tileAvailability = TileAvailability.MISSING,
                contentFootprint = ContentFootprint.NOT_VERIFIED,
                navigationSuitability = NavigationSuitability.NOT_ASSESSED,
                requiredKeyCount = 3,
                missingKeys = setOf(missing),
            ),
        )
        val imports = mutableListOf<ChartImportUiAction>()
        val reducer = DefaultMapReducer()
        val initial = MapState(surface = MapSurface.RouteDetail(route.id), savedRoutes = listOf(route))
        var currentState: () -> MapState = { initial }

        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    var state by remember { mutableStateOf(initial) }
                    currentState = { state }
                    ChartWorkspace(
                        state = state,
                        currentState = { state },
                        onAction = { state = reducer.reduce(state, it).state },
                        importState = ChartImportUiState.Idle,
                        onImportAction = imports::add,
                        recoveryExportState = MapRecoveryExportUiState.IDLE,
                        onExportRecovery = {},
                        offlineCoverageState = coverage,
                        chartSurface = { _, _, _, modifier -> Box(modifier) },
                    )
                }
            }
        }

        compose.onNodeWithTag("map-route-offline-coverage-${route.id}").performScrollTo().performClick()
        compose.onNodeWithTag("map-offline-coverage-${route.id}").assertIsDisplayed()
        compose.onNodeWithTag("map-coverage-tile-availability").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("map-coverage-content-footprint").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("map-coverage-navigation-suitability").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("map-coverage-missing-12-4035-2568").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("map-coverage-import").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(MapSurface.OfflineCoverage(route.id), currentState().surface)
            assertTrue(imports.contains(ChartImportUiAction.ChooseDocument))
        }
    }
}

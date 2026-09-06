package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.chart.ChartDisplayTestTags
import com.yokuli.marine.feature.chart.ChartDisplayUiAction
import com.yokuli.marine.feature.chart.ChartDisplayUiState
import com.yokuli.marine.feature.chart.ChartQuickLayerUi
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.MapRecoveryExportUiState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChartDisplayWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun quickLayersControlDisplayOnlyAndNeverExposeLibraryOrNavigationManagement() {
        val assetId = ChartAssetId("10000000-0000-0000-0000-000000000001")
        val actions = mutableListOf<ChartDisplayUiAction>()
        val display = ChartDisplayUiState(
            busy = false,
            quickLayers = listOf(ChartQuickLayerUi(assetId, "Auckland.mbtiles", ChartAssetRole.BASE, true, true, .8f)),
        )

        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    ChartWorkspace(
                        state = MapState(surface = MapSurface.ChartPackages),
                        onAction = {},
                        chartDisplayState = display,
                        onChartDisplayAction = actions::add,
                        recoveryExportState = MapRecoveryExportUiState.IDLE,
                        onExportRecovery = {},
                        chartSurface = { _, _, _, modifier -> Box(modifier) },
                    )
                }
            }
        }

        compose.onNodeWithTag(ChartDisplayTestTags.ROOT).assertIsDisplayed()
        compose.onNodeWithTag(ChartDisplayTestTags.quickLayerVisibility(assetId.value.take(8))).performScrollTo().performClick()
        compose.onNodeWithTag(ChartDisplayTestTags.opacityDown(assetId.value.take(8))).performScrollTo().performClick()
        compose.onNodeWithTag("map-open-chart-library").assertDoesNotExist()
        compose.onNodeWithTag(ChartDisplayTestTags.REFRESH).assertDoesNotExist()
        compose.onNodeWithTag("map-open-gpx").assertDoesNotExist()
        compose.onNodeWithTag("map-open-imported-tracks").assertDoesNotExist()
        compose.onNodeWithTag("map-import-chart").assertDoesNotExist()
        compose.onNodeWithTag("map-coverage-import").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(actions.contains(ChartDisplayUiAction.SetLayerVisible(assetId, false)))
            assertTrue(actions.contains(ChartDisplayUiAction.SetOpacity(assetId, .7f)))
        }
    }

    @Test
    fun chartRootAcceptsRealNavigationContentWithoutInventingNavigationState() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    ChartWorkspace(
                        state = MapState(),
                        onAction = {},
                        recoveryExportState = MapRecoveryExportUiState.IDLE,
                        onExportRecovery = {},
                        activeNavigationStrip = { WpText("NAV", 12, modifier = Modifier.testTag("real-navigation-content")) },
                        chartSurface = { _, _, _, modifier -> Box(modifier) },
                    )
                }
            }
        }

        compose.onNodeWithTag("map-active-navigation-strip").assertIsDisplayed()
        compose.onNodeWithTag("real-navigation-content").assertIsDisplayed()
    }
}

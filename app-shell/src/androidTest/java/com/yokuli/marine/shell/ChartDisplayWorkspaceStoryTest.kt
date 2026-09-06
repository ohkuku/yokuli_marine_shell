package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.chart.ChartDisplayAssetUi
import com.yokuli.marine.feature.chart.ChartDisplaySourceUi
import com.yokuli.marine.feature.chart.ChartDisplayTestTags
import com.yokuli.marine.feature.chart.ChartDisplayUiAction
import com.yokuli.marine.feature.chart.ChartDisplayUiState
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.MapRecoveryExportUiState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChartDisplayWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun chartControlsDisplayOnlyAndNeverOwnsFileImportOrScanning() {
        val sourceId = ChartSourceId("00000000-0000-0000-0000-000000000001")
        val assetId = ChartAssetId("10000000-0000-0000-0000-000000000001")
        val actions = mutableListOf<ChartDisplayUiAction>()
        val display = ChartDisplayUiState(
            busy = false,
            sources = listOf(ChartDisplaySourceUi(sourceId, "NZ Hydro", false, true, 1)),
            assets = listOf(ChartDisplayAssetUi(assetId, "Auckland.mbtiles", ChartAssetRole.BASE, false, false, true, 1f)),
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
        compose.onNodeWithTag(ChartDisplayTestTags.source(sourceId.value.take(8))).performScrollTo().performClick()
        compose.onNodeWithTag(ChartDisplayTestTags.asset(assetId.value.take(8))).performScrollTo().performClick()
        compose.onNodeWithTag("map-import-chart").assertDoesNotExist()
        compose.onNodeWithTag("map-coverage-import").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(actions.contains(ChartDisplayUiAction.ToggleSource(sourceId)))
            assertTrue(actions.contains(ChartDisplayUiAction.PinAsset(assetId)))
        }
    }
}

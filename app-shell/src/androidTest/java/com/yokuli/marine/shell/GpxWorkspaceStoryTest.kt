package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.chart.ChartImportUiState
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.GpxExportTarget
import com.yokuli.marine.feature.chart.GpxExportUiState
import com.yokuli.marine.feature.chart.GpxImportUiAction
import com.yokuli.marine.feature.chart.GpxImportUiState
import com.yokuli.marine.feature.chart.MapRecoveryExportUiState
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.GpxReader
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.ImportedTrackPoint
import com.yokuli.marine.map.domain.ImportedTrackSegment
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GpxWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun previewRequiresExplicitSelectionAndDuplicateUsesImportAsCopy() {
        val preview = GpxReader().inspect(GPX.byteInputStream()).copy(duplicate = true)
        val actions = mutableListOf<GpxImportUiAction>()
        setContent(
            state = MapState(surface = MapSurface.GpxExchange),
            importState = GpxImportUiState.Preview("fixture", 1L, preview),
            onImportAction = actions::add,
        )

        compose.onNodeWithTag("map-gpx-preview").assertIsDisplayed()
        compose.onNodeWithTag("map-gpx-duplicate").assertIsDisplayed()
        compose.onNodeWithTag("map-gpx-track-0").performScrollTo().performClick()
        compose.onNodeWithTag("map-gpx-import-copy").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(actions.contains(GpxImportUiAction.ToggleTrack(0)))
            assertTrue(actions.contains(GpxImportUiAction.ImportAsCopy))
            assertTrue(actions.none { it == GpxImportUiAction.ConfirmImport })
        }
    }

    @Test
    fun trackDetailOffersItsOriginalTypeAndShowsCancelledVersusFailedExportTruth() {
        val track = ImportedTrack(
            id = "track-story",
            name = "Two legs",
            segments = listOf(
                ImportedTrackSegment(
                    listOf(
                        ImportedTrackPoint(GeoPoint(-36.8, 174.7)),
                        ImportedTrackPoint(GeoPoint(-36.9, 174.8)),
                    ),
                ),
                ImportedTrackSegment(
                    listOf(
                        ImportedTrackPoint(GeoPoint(40.0, -20.0)),
                        ImportedTrackPoint(GeoPoint(41.0, -21.0)),
                    ),
                ),
            ),
            sourceDigest = "a".repeat(64),
            importedAtMillis = 1L,
        )
        var saved: GpxExportTarget? = null
        var shared: GpxExportTarget? = null
        val state = MapState(surface = MapSurface.ImportedTrackDetail(track.id), importedTracks = listOf(track))
        setContent(
            state,
            exportState = GpxExportUiState.TargetCancelled(track.id),
            onSave = { saved = it },
            onShare = { shared = it },
        )

        compose.onNodeWithTag("map-track-name").assertIsDisplayed()
        compose.onNodeWithTag("map-gpx-export-state").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("map-gpx-export-track-story").performScrollTo().performClick()
        compose.onNodeWithTag("map-gpx-share-track-story").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(track, (saved as GpxExportTarget.Track).value)
            assertEquals(track, (shared as GpxExportTarget.Track).value)
        }

        setContent(state, exportState = GpxExportUiState.WriteFailed(track.id))
        compose.onNodeWithTag("map-gpx-export-state").performScrollTo().assertIsDisplayed()
    }

    private fun setContent(
        state: MapState,
        importState: GpxImportUiState = GpxImportUiState.Idle,
        exportState: GpxExportUiState = GpxExportUiState.Idle,
        onImportAction: (GpxImportUiAction) -> Unit = {},
        onSave: (GpxExportTarget) -> Unit = {},
        onShare: (GpxExportTarget) -> Unit = {},
    ) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YokuliTheme(WpThemeSpec()) {
                    ChartWorkspace(
                        state = state,
                        onAction = {},
                        importState = ChartImportUiState.Idle,
                        onImportAction = {},
                        recoveryExportState = MapRecoveryExportUiState.IDLE,
                        onExportRecovery = {},
                        gpxImportState = importState,
                        onGpxImportAction = onImportAction,
                        gpxExportState = exportState,
                        onSaveGpx = onSave,
                        onShareGpx = onShare,
                        chartSurface = { _, _, _, modifier -> Box(modifier) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val GPX = """<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
            <wpt lat="-36.8" lon="174.7"><name>泊位</name></wpt>
            <rte><name>Route</name><rtept lat="-36.8" lon="174.7"/><rtept lat="-36.9" lon="174.8"/></rte>
            <trk><name>Track</name><trkseg><trkpt lat="-36.8" lon="174.7"/><trkpt lat="-36.9" lon="174.8"/></trkseg></trk>
        </gpx>"""
    }
}

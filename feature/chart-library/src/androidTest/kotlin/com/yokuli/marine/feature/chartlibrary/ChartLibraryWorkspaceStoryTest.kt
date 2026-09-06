package com.yokuli.marine.feature.chartlibrary

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartFactProvenance
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceScanState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChartLibraryWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStandaloneHostCanAddSourcesWithoutChart() {
        var action: ChartLibraryUiAction? = null
        render(ChartLibraryUiState()) { action = it }

        compose.onNodeWithTag(ChartLibraryTestTags.ROOT).assertIsDisplayed()
        compose.onNodeWithTag(ChartLibraryTestTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(ChartLibraryTestTags.ADD_FOLDER).performClick()
        assertEquals(ChartLibraryUiAction.AddFolder, action)
    }

    @Test
    fun permissionLostSourceOffersExplicitRepair() {
        val source = sourceRow(grant = ChartGrantState.REVOKED)
        var action: ChartLibraryUiAction? = null
        render(ChartLibraryUiState(page = ChartLibraryPageUi.SourceDetail(source, emptyList()))) { action = it }

        compose.onNodeWithTag(ChartLibraryTestTags.repair(source.id.value)).performClick()
        assertEquals(ChartLibraryUiAction.RepairPermission(source.id), action)
    }

    @Test
    fun assetRoleAndPriorityAreFeatureActionsNotChartMutations() {
        val asset = assetRow()
        var action: ChartLibraryUiAction? = null
        render(ChartLibraryUiState(page = ChartLibraryPageUi.AssetDetail(asset))) { action = it }

        compose.onNodeWithTag(ChartLibraryTestTags.role(asset.id.value)).performClick()
        assertEquals(ChartLibraryUiAction.SetAssetRole(asset.id, ChartAssetRole.OVERLAY), action)
        compose.onNodeWithTag(ChartLibraryTestTags.priorityUp(asset.id.value)).performClick()
        assertEquals(ChartLibraryUiAction.MoveAssetPriority(asset.id, 1), action)
    }

    @Test
    fun rootBackIsOwnedByShellAndNeverExitsAndroid() {
        assertEquals(null, ChartLibraryBackPolicy.actionFor(ChartLibraryPageUi.Overview(emptyList(), emptyList())))
    }

    private fun render(state: ChartLibraryUiState, action: (ChartLibraryUiAction) -> Unit) {
        compose.setContent { YokuliTheme(WpThemeSpec()) { ChartLibraryWorkspace(state, action) } }
    }

    private fun sourceRow(grant: ChartGrantState) = ChartLibrarySourceRowUi(
        id = SOURCE,
        name = "MarineCharts",
        kind = ChartLibrarySourceKind.TREE,
        provider = "com.android.externalstorage.documents",
        enabled = true,
        recursive = true,
        defaultRole = ChartAssetRole.BASE,
        grantState = grant,
        scan = ChartSourceScanState(generation = 1L, status = ChartScanStatus.FAILED, issueCount = 1),
        assetCount = 0,
        availableAssetCount = 0,
        attentionCount = 1,
        referencedBytes = null,
        unknownSizeCount = 0,
    )

    private fun assetRow() = ChartLibraryAssetRowUi(
        id = ASSET,
        title = "harbour.mbtiles",
        displayPath = "charts/harbour.mbtiles",
        sourceNames = listOf("MarineCharts"),
        enabled = true,
        role = ChartAssetRole.BASE,
        priority = 0,
        access = ChartAssetAccessState.READABLE,
        validation = ChartAssetValidationState.BASIC_READABLE,
        sizeBytes = 1024L,
        format = ChartAssetFormat.RASTER_MBTILES,
        rasterMimeType = "image/png",
        tileSize = 256,
        tileScheme = null,
        minZoom = null,
        maxZoom = null,
        tileCount = null,
        bounds = null,
        attribution = null,
        attributionProvenance = ChartFactProvenance.UNKNOWN,
        revisionSummary = null,
        selected = false,
        managedCopyAvailable = false,
        validationJob = null,
        copyJob = null,
    )

    private companion object {
        val SOURCE = ChartSourceId("00000000-0000-0000-0000-000000000001")
        val ASSET = ChartAssetId("10000000-0000-0000-0000-000000000001")
    }
}

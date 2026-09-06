package com.yokuli.marine.feature.chartlibrary

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.contract.MarineTileSize
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

    @Test
    fun managedCopyRequiresAVisibleWholeFileConfirmation() {
        val asset = assetRow(managedCopyAvailable = true)
        var action: ChartLibraryUiAction? = null
        render(
            ChartLibraryUiState(
                page = ChartLibraryPageUi.SaveManagedCopyConfirmation(asset, availableCopyBytes = 8_000L),
            ),
        ) { action = it }

        compose.onNodeWithTag("chart-library-confirm-save-copy").performClick()
        assertEquals(ChartLibraryUiAction.ConfirmManagedCopy, action)
    }

    @Test
    fun threeAppOwnedTileSizesRenderInBothThemesAndLargeType() {
        val suffix = mapOf(
            MarineTileSize.ICON_1X1 to "small",
            MarineTileSize.STANDARD_2X2 to "medium",
            MarineTileSize.WIDE_4X2 to "wide",
        )
        WpThemeMode.entries.forEach { mode ->
            MarineTileSize.entries.forEach { size ->
                compose.setContent {
                    val platformDensity = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(platformDensity.density, 1.6f)) {
                        YokuliTheme(WpThemeSpec(mode = mode)) {
                            val colors = LocalWpTheme.current
                            val visual = chartLibraryLauncherVisualContribution(
                                ChartLibraryUiState(
                                    summary = ChartLibrarySummaryUi(2, 42, 40, 2, 1),
                                ),
                            )
                            Box(
                                Modifier.requiredSize(
                                    width = if (size == MarineTileSize.WIDE_4X2) 320.dp else 152.dp,
                                    height = if (size == MarineTileSize.ICON_1X1) 76.dp else 152.dp,
                                ),
                            ) {
                                visual.tileRenderers.getValue(size).Render(
                                    LauncherTileRenderContext(
                                        size,
                                        colors.onAccent,
                                        Modifier.fillMaxSize(),
                                        liveContentEnabled = true,
                                    ),
                                )
                            }
                        }
                    }
                }
                compose.onNodeWithTag("chart-library-tile-${suffix.getValue(size)}", useUnmergedTree = true)
                    .assertIsDisplayed()
            }
        }
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

    private fun assetRow(managedCopyAvailable: Boolean = false) = ChartLibraryAssetRowUi(
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
        managedCopyAvailable = managedCopyAvailable,
        validationJob = null,
        copyJob = null,
    )

    private companion object {
        val SOURCE = ChartSourceId("00000000-0000-0000-0000-000000000001")
        val ASSET = ChartAssetId("10000000-0000-0000-0000-000000000001")
    }
}

package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.SlippyTileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDisplayPlannerTest {
    @Test
    fun pinnedAssetIsNotStolenByNewCatalogAssetAndCameraIsNotPartOfTheMutation() {
        val pinned = asset(ASSET_A, bounds = null)
        val plan = plan(
            listOf(pinned, asset(ASSET_B, priority = 999)),
            ChartDisplayPreferences(ChartDisplaySelection.PinnedAsset(ASSET_A)),
        )

        assertEquals(listOf(ASSET_A), plan.layers.map { it.assetId })
        assertFalse(plan.issues.contains(ChartDisplayIssue.UNKNOWN_BOUNDS_EXCLUDED))
    }

    @Test
    fun sourceSetOrdersBaseThenOverlayByPriorityAndStableId() {
        val assets = listOf(
            asset(ASSET_B, role = ChartAssetRole.BASE, priority = 5),
            asset(ASSET_A, role = ChartAssetRole.BASE, priority = 5),
            asset(ASSET_C, role = ChartAssetRole.OVERLAY, priority = -9),
        )
        val plan = plan(assets, ChartDisplayPreferences(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID))))

        assertEquals(listOf(ASSET_A, ASSET_B, ASSET_C), plan.layers.map { it.assetId })
        assertEquals(.85f, plan.layers.last().opacity)
    }

    @Test
    fun hiddenQuickLayerRemainsSelectedButIsExcludedDeterministically() {
        val preferences = ChartDisplayPreferences(
            selection = ChartDisplaySelection.SourceSet(setOf(SOURCE_ID)),
            hiddenAssetIds = setOf(ASSET_B),
        )

        val plan = plan(listOf(asset(ASSET_A), asset(ASSET_B)), preferences)

        assertEquals(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID)), plan.selection)
        assertEquals(listOf(ASSET_A), plan.layers.map { it.assetId })
    }

    @Test
    fun viewportFiltersDistantRegionsWithoutOpeningEveryCatalogAsset() {
        val visible = asset(ASSET_A, bounds = GeoBounds(-37.0, 174.0, -36.0, 175.0))
        val distant = asset(ASSET_B, bounds = GeoBounds(40.0, -75.0, 41.0, -74.0))
        val plan = plan(
            listOf(visible, distant),
            ChartDisplayPreferences(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID))),
            ChartDisplayViewport(GeoBounds(-37.2, 173.8, -35.8, 175.2), 8),
        )

        assertEquals(listOf(ASSET_A), plan.layers.map { it.assetId })
    }

    @Test
    fun datelineIntersectionUsesTwoLongitudeSegments() {
        val crossing = asset(ASSET_A, bounds = GeoBounds(-20.0, 175.0, 20.0, -175.0))
        val plan = plan(
            listOf(crossing),
            ChartDisplayPreferences(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID))),
            ChartDisplayViewport(GeoBounds(-5.0, 178.0, 5.0, -178.0), 8),
        )
        assertEquals(listOf(ASSET_A), plan.layers.map { it.assetId })
    }

    @Test
    fun unknownBoundsAreExcludedFromAutomaticSetButRetainedWhenPinned() {
        val unknown = asset(ASSET_A, bounds = null)
        val viewport = ChartDisplayViewport(GeoBounds(-1.0, -1.0, 1.0, 1.0), 8)
        val automatic = plan(
            listOf(unknown),
            ChartDisplayPreferences(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID))),
            viewport,
        )
        val pinned = plan(
            listOf(unknown),
            ChartDisplayPreferences(ChartDisplaySelection.PinnedAsset(ASSET_A)),
            viewport,
        )

        assertTrue(automatic.layers.isEmpty())
        assertTrue(automatic.issues.contains(ChartDisplayIssue.UNKNOWN_BOUNDS_EXCLUDED))
        assertEquals(listOf(ASSET_A), pinned.layers.map { it.assetId })
    }

    @Test
    fun overzoomNeverClaimsNativeSourceSetCoverage() {
        val asset = asset(ASSET_A, minZoom = 4, maxZoom = 10)
        val preferences = ChartDisplayPreferences(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID)))
        val plan = plan(listOf(asset), preferences, ChartDisplayViewport(GeoBounds(-90.0, -180.0, 90.0, 180.0), 11))

        assertTrue(plan.layers.isEmpty())
        assertTrue(plan.issues.contains(ChartDisplayIssue.NO_NATIVE_ZOOM))
    }

    @Test
    fun layerLimitIsExplicitAndDeterministic() {
        val assets = (0 until 10).map { index ->
            asset(ChartAssetId("10000000-0000-0000-0000-${index.toString().padStart(12, '0')}"), priority = index)
        }
        val plan = plan(assets, ChartDisplayPreferences(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID))))

        assertEquals(MAX_ACTIVE_CHART_LAYERS, plan.layers.size)
        assertEquals(2, plan.omittedLayerCount)
        assertTrue(plan.issues.contains(ChartDisplayIssue.LAYER_LIMIT_REACHED))
    }

    @Test
    fun overlayTilesCannotFillBaseCoverageGapAndStaleRevisionIsRejected() {
        val base = asset(ASSET_A, role = ChartAssetRole.BASE)
        val overlay = asset(ASSET_B, role = ChartAssetRole.OVERLAY)
        val plan = plan(listOf(base, overlay), ChartDisplayPreferences(ChartDisplaySelection.SourceSet(setOf(SOURCE_ID))))
        val key = SlippyTileKey(8, 1, 1)
        val partial = ChartDisplayCoverageEvaluator.evaluate(plan, 8, setOf(key), mapOf(ASSET_A to emptySet(), ASSET_B to setOf(key)))
        val stale = ChartDisplayCoverageEvaluator.evaluate(plan, 8, setOf(key), emptyMap(), expectedFingerprint = "f".repeat(64))

        assertEquals(ChartDisplayCoverageStatus.PARTIAL, partial.status)
        assertEquals(setOf(key), partial.missingKeys)
        assertEquals(ChartDisplayCoverageStatus.STALE, stale.status)
    }

    private fun plan(
        assets: List<ChartAsset>,
        preferences: ChartDisplayPreferences,
        viewport: ChartDisplayViewport? = null,
    ) = ChartDisplayPlanner.plan(
        generation = 1L,
        catalog = ChartCatalogSnapshot(revision = 7L, sourceCount = 1, assetCount = assets.size),
        sources = listOf(source()),
        assets = assets,
        preferences = preferences,
        viewport = viewport,
    )

    private fun source() = ChartLibrarySource(
        SOURCE_ID,
        ChartLibrarySourceKind.TREE,
        ChartOpaqueLocator("content://provider/tree/charts"),
        "charts",
        grantState = ChartGrantState.GRANTED,
        scan = ChartSourceScanState(2L, ChartScanStatus.COMPLETE, 2L),
    )

    private fun asset(
        id: ChartAssetId,
        bounds: GeoBounds? = GeoBounds(-80.0, -170.0, 80.0, 170.0),
        role: ChartAssetRole = ChartAssetRole.BASE,
        priority: Int = 0,
        minZoom: Int = 0,
        maxZoom: Int = 24,
    ) = ChartAsset(
        id,
        ChartDocumentIdentity("provider", id.value),
        ChartOpaqueLocator("content://provider/document/${id.value}"),
        setOf(SOURCE_ID),
        "${id.value}.mbtiles",
        ChartContentRevision(id.value, 1024L, 1L),
        facts = ChartAssetFacts(
            ChartAssetFormat.RASTER_MBTILES,
            1024L,
            bounds,
            minZoom,
            maxZoom,
            1L,
            256,
            MapTileScheme.MBTILES_TMS,
        ),
        role = role,
        priority = priority,
        access = ChartAssetAccessState.READABLE,
        validation = ChartAssetValidationState.BASIC_READABLE,
    )

    private companion object {
        val SOURCE_ID = ChartSourceId("00000000-0000-0000-0000-000000000001")
        val ASSET_A = ChartAssetId("10000000-0000-0000-0000-000000000001")
        val ASSET_B = ChartAssetId("10000000-0000-0000-0000-000000000002")
        val ASSET_C = ChartAssetId("10000000-0000-0000-0000-000000000003")
    }
}

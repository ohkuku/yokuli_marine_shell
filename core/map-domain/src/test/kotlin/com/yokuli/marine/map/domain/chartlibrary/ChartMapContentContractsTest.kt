package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMapContentContractsTest {
    @Test fun oneLogicalLayerAggregatesManyFilesAndTheirHealth() {
        val source = source("NZ Hydro")
        val layer = ChartLayer(ChartLayerId("nz-hydro"), "LINZ 官方海图", setOf(source.id))
        val assets = listOf(
            asset(source.id, "north.mbtiles", GeoBounds(-36.0, 173.0, -34.0, 175.0), warning = true),
            asset(source.id, "south.mbtiles", GeoBounds(-42.0, 171.0, -37.0, 176.0)),
        )

        val summary = ChartLayerSummaryProjector.project(layer, listOf(source), assets)

        assertEquals(2, summary.assetCount)
        assertEquals(2, summary.readyAssetCount)
        assertEquals(ChartLayerHealth.WARNING, summary.health)
        assertEquals(7, summary.minZoom)
        assertEquals(16, summary.maxZoom)
        assertEquals(1, summary.warningCount)
        assertTrue(requireNotNull(summary.coverage).south <= -42.0)
        assertTrue(requireNotNull(summary.coverage).north >= -34.0)
    }

    @Test fun viewPlannerExposesLogicalNameAndUsesDeterministicFileOrder() {
        val source = source("NZ Hydro")
        val layer = ChartLayer(ChartLayerId("nz-hydro"), "LINZ 官方海图", setOf(source.id), opacity = .9f)
        val view = ChartMapView(
            ChartViewId("sailing"),
            "Sailing",
            ChartBuiltInBaseStyle.SATELLITE,
            listOf(ChartViewLayer(layer.id, opacity = .7f)),
        )
        val assets = listOf(
            asset(source.id, "z-south.mbtiles", GeoBounds(-42.0, 171.0, -37.0, 176.0)),
            asset(source.id, "a-north.mbtiles", GeoBounds(-36.0, 173.0, -34.0, 175.0)),
        )
        val catalog = ChartCatalogSnapshot(
            revision = 4,
            sourceCount = 1,
            assetCount = 2,
            layerCount = 1,
            viewCount = 1,
            activeViewId = view.id,
        )

        val plan = ChartViewDisplayPlanner.plan(1, catalog, listOf(source), assets, listOf(layer), view, null)

        assertEquals(view.id, plan.activeViewId)
        assertEquals("Sailing", plan.activeViewName)
        assertEquals(ChartBuiltInBaseStyle.SATELLITE, plan.builtInBaseStyle)
        assertEquals(listOf("a-north.mbtiles", "z-south.mbtiles"), plan.layers.map { it.request.locator.value.substringAfterLast('/') })
        assertTrue(plan.layers.all { it.displayName == "LINZ 官方海图" && it.logicalLayerId == layer.id })
        assertEquals(.7f, plan.layers.first().opacity)
        assertEquals(listOf(layer.id), plan.logicalLayers.map(ChartLogicalDisplayLayer::id))
    }

    @Test fun unavailableLayerRemainsVisibleAsTruthButNeverCreatesFakeRenderLayer() {
        val source = source("lost").copy(grantState = ChartGrantState.REVOKED)
        val layer = ChartLayer(ChartLayerId("lost"), "Lost charts", setOf(source.id))
        val view = ChartMapView(
            ChartViewId("chart"), "Chart", ChartBuiltInBaseStyle.NONE, listOf(ChartViewLayer(layer.id)),
        )

        val plan = ChartViewDisplayPlanner.plan(
            1,
            ChartCatalogSnapshot(revision = 1, sourceCount = 1, layerCount = 1, viewCount = 1, activeViewId = view.id),
            listOf(source),
            emptyList(),
            listOf(layer),
            view,
            null,
        )

        assertTrue(plan.layers.isEmpty())
        assertEquals(ChartLayerHealth.PERMISSION_LOST, plan.logicalLayers.single().health)
        assertTrue(ChartDisplayIssue.LOGICAL_LAYER_UNAVAILABLE in plan.issues)
    }

    private fun source(name: String) = ChartLibrarySource(
        id = ChartSourceId(UUID.nameUUIDFromBytes(name.encodeToByteArray()).toString()),
        kind = ChartLibrarySourceKind.TREE,
        locator = ChartOpaqueLocator("content://provider/$name"),
        displayName = name,
        grantState = ChartGrantState.GRANTED,
        scan = ChartSourceScanState(1, ChartScanStatus.COMPLETE, 1, 2, 0),
    )

    private fun asset(sourceId: ChartSourceId, name: String, bounds: GeoBounds, warning: Boolean = false) = ChartAsset(
        id = ChartAssetId(UUID.nameUUIDFromBytes(name.encodeToByteArray()).toString()),
        documentIdentity = ChartDocumentIdentity("provider", name),
        locator = ChartOpaqueLocator("content://provider/$name"),
        memberships = setOf(sourceId),
        displayPath = name,
        revision = ChartContentRevision(name, 1024, 1),
        facts = ChartAssetFacts(
            format = ChartAssetFormat.RASTER_MBTILES,
            sizeBytes = 1024,
            bounds = bounds,
            minZoom = 7,
            maxZoom = 16,
            tileSize = 256,
            tileScheme = MapTileScheme.MBTILES_TMS,
            rasterMimeType = "image/png",
        ),
        access = ChartAssetAccessState.READABLE,
        validation = ChartAssetValidationState.BASIC_READABLE,
        compatibilityWarnings = if (warning) setOf(ChartCompatibilityWarning.METADATA_MISSING) else emptySet(),
    )
}

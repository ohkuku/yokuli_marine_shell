package com.yokuli.marine.feature.chartlibrary

import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.shell.contract.MarineTileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLibraryShellContributionTest {
    @Test fun oneEntryOwnsExactlyTheThreeClassicTileSizes() {
        val entry = ChartLibraryShellContribution.entries.single()
        assertEquals(ChartLibraryDestinations.EntryId, entry.entryId)
        assertEquals(MarineTileSize.entries.toSet(), entry.supportedSizes.toSet())
    }

    @Test fun dynamicRoutesCarryOnlyBoundedOpaqueIds() {
        val source = ChartLibraryDestinations.source(ChartSourceId("source-42"))
        val asset = ChartLibraryDestinations.asset(ChartAssetId("asset-77"))
        assertEquals(ChartLibraryDestination.Source(ChartSourceId("source-42")), ChartLibraryDestinations.parse(source))
        assertEquals(ChartLibraryDestination.Asset(ChartAssetId("asset-77")), ChartLibraryDestinations.parse(asset))
        assertFalse(source.value.contains("content://"))
        assertFalse(asset.value.contains('/'))
        assertNull(ChartLibraryDestinations.parse(com.yokuli.shell.contract.LaunchToken("chart_library.asset.zz")))
    }

    @Test fun currentLibraryStateSearchesNamesButNeverPlacesPathsInLaunchTokens() {
        val state = ChartLibraryUiState(
            summary = ChartLibrarySummaryUi(sourceCount = 1, assetCount = 1, availableAssetCount = 1),
            searchItems = listOf(
                ChartLibrarySearchItem.Source(ChartSourceId("source-42"), "NZ Hydro"),
                ChartLibrarySearchItem.Asset(ChartAssetId("asset-77"), "Cook Strait", "NZ Hydro"),
            ),
        )
        val results = ChartLibrarySearchProjector.search(state, "hydro")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { "content://" !in it.launchToken.value })
    }

    @Test fun editFreezeHoldsDecorationButStillShowsAndClearsAttention() {
        fun tile(available: Int, attention: Int) = ChartLibraryTileState(1, 2, available, attention, 0)
        val initial = tile(1, 0)
        val slot = ChartLibraryTileDisplaySlot(initial)

        assertEquals(initial, slot.resolve(tile(2, 0), liveContentEnabled = false))
        assertEquals(tile(2, 1), slot.resolve(tile(2, 1), liveContentEnabled = false))
        assertEquals(tile(2, 0), slot.resolve(tile(2, 0), liveContentEnabled = false))
        assertEquals(tile(2, 0), slot.resolve(tile(2, 0), liveContentEnabled = true))
    }
}

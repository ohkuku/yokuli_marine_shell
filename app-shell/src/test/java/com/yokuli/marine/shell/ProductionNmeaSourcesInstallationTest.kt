package com.yokuli.marine.shell

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.feature.data.DataDestinations
import com.yokuli.marine.feature.chartlibrary.ChartLibraryDestinations
import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.MarineTileSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionNmeaSourcesInstallationTest {
    @Test
    fun productionRegistryInstallsOneDataAppInsteadOfTwoProtocolSurfaces() {
        assertEquals(4, productionInstalledApps.size)
        assertEquals(
            setOf("chart", "settings", "data", "chart_library"),
            productionInstalledApps.map { it.catalogContribution.app.appId.value }.toSet(),
        )
        assertEquals(4, productionInstalledAppRegistry.internalAppHosts.size)
    }

    @Test
    fun dataOwnsExactlyTheThreeWpTileSizes() {
        val entries = productionCatalog.snapshot.entries.associateBy { it.entryId }
        assertEquals(
            MarineTileSize.entries.toSet(),
            entries.getValue(DataDestinations.EntryId).supportedSizes.toSet(),
        )
    }

    @Test
    fun installingAppsNeverAddsThemToTheDefaultOrExistingStartDocument() {
        val pinnedEntries = defaultStartDocument.placements.map { it.entryId }.toSet()
        assertFalse(DataDestinations.EntryId in pinnedEntries)
        assertFalse(ChartLibraryDestinations.EntryId in pinnedEntries)
        assertEquals(setOf("chart", "settings"), pinnedEntries.map { it.value }.toSet())
    }

    @Test
    fun currentAndLegacyConnectionRoutesResolveToTheOneInstalledDataApp() = runBlocking {
        val input = DataDestinations.input(ConnectionId("gateway"))
        val legacyAttention = com.yokuli.shell.contract.LaunchToken("sources.attention")

        assertTrue(productionHostPort.resolveLaunch(input) is LaunchResolution.Internal)
        assertTrue(productionHostPort.resolveLaunch(legacyAttention) is LaunchResolution.Internal)
    }
}

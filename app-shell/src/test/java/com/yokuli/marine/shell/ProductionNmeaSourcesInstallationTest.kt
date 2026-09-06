package com.yokuli.marine.shell

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.feature.datasources.DataSourcesDestinations
import com.yokuli.marine.feature.nmeainput.NmeaInputDestinations
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
    fun productionRegistryKeepsBothMarineDataAppsWhenChartLibraryIsInstalled() {
        assertEquals(5, productionInstalledApps.size)
        assertEquals(
            setOf("chart", "settings", "nmea-input", "data-sources", "chart_library"),
            productionInstalledApps.map { it.catalogContribution.app.appId.value }.toSet(),
        )
        assertEquals(5, productionInstalledAppRegistry.internalAppHosts.size)
    }

    @Test
    fun bothNewAppsOwnExactlyTheThreeWpTileSizes() {
        val entries = productionCatalog.snapshot.entries.associateBy { it.entryId }
        assertEquals(
            MarineTileSize.entries.toSet(),
            entries.getValue(NmeaInputDestinations.EntryId).supportedSizes.toSet(),
        )
        assertEquals(
            MarineTileSize.entries.toSet(),
            entries.getValue(DataSourcesDestinations.EntryId).supportedSizes.toSet(),
        )
    }

    @Test
    fun installingAppsNeverAddsThemToTheDefaultOrExistingStartDocument() {
        val pinnedEntries = defaultStartDocument.placements.map { it.entryId }.toSet()
        assertFalse(NmeaInputDestinations.EntryId in pinnedEntries)
        assertFalse(DataSourcesDestinations.EntryId in pinnedEntries)
        assertFalse(ChartLibraryDestinations.EntryId in pinnedEntries)
        assertEquals(setOf("chart", "settings"), pinnedEntries.map { it.value }.toSet())
    }

    @Test
    fun boundedConnectionAndAttentionRoutesResolveToTheCorrectInstalledApp() = runBlocking {
        val nmea = NmeaInputDestinations.connection(ConnectionId("gateway"))
        val attention = DataSourcesDestinations.Attention

        assertTrue(productionHostPort.resolveLaunch(nmea) is LaunchResolution.Internal)
        assertTrue(productionHostPort.resolveLaunch(attention) is LaunchResolution.Internal)
    }
}

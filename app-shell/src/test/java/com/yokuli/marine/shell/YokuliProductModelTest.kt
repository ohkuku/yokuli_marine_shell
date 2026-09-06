package com.yokuli.marine.shell

import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.engine.geometry.ProfileId
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YokuliProductModelTest {
    @Test fun finalProductIdentitiesAreStableAndOnlyCompletedAppsAreInstalled() {
        assertEquals(
            listOf("chart", "chart_library", "data", "navigation", "preferences"),
            YokuliProductModel.finalApps.map { it.appId.value },
        )
        val installed = productionCatalog.entries.mapTo(linkedSetOf()) { it.entryId.value }
        assertEquals(setOf("chart", "preferences", "data", "chart_library", "navigation"), installed)
        assertTrue("navigation" in installed)
        assertTrue("preferences" in installed)
        assertFalse("settings" in installed)
    }

    @Test fun currentCompositionRootMigratesLegacyMarineTilesOnlyAfterDataHasARealHost() {
        val legacy = LauncherPersistedState(
            schemaVersion = 2,
            document = StartDocument(
                schemaVersion = 2,
                profileId = ProfileId("phone-portrait-4col"),
                defaultLayoutVersion = 2,
                placements = listOf(
                    TilePlacement(
                        TileInstanceId("tile-settings"), LauncherEntryId("settings"),
                        MarineTileSize.ICON_1X1, 100,
                    ),
                    TilePlacement(
                        TileInstanceId("tile-nmea"), LauncherEntryId("nmea-input"),
                        MarineTileSize.WIDE_4X2, 200,
                    ),
                ),
            ),
        )

        val result = YokuliProductModel.migrationPlan.migrate(
            legacy,
            productionCatalog.entries.mapTo(linkedSetOf()) { it.entryId },
        )

        assertEquals(2, result.state.productModelVersion)
        assertEquals(listOf(1, 2), result.appliedVersions)
        assertEquals(
            listOf("preferences", "data"),
            result.state.document!!.placements.map { it.entryId.value },
        )
        assertEquals(listOf("tile-settings", "tile-nmea"), result.state.document!!.placements.map { it.tileId.value })
        assertEquals(listOf(MarineTileSize.ICON_1X1, MarineTileSize.WIDE_4X2), result.state.document!!.placements.map { it.size })
    }

    @Test fun legacySettingsResourcePageFallsBackToPreferencesOverview() {
        val legacy = LauncherPersistedState(
            schemaVersion = 3,
            lastForegroundToken = "settings.map",
            productModelVersion = 1,
        )

        val result = YokuliProductModel.migrationPlan.migrate(
            legacy,
            productionCatalog.entries.mapTo(linkedSetOf()) { it.entryId },
        )

        assertEquals("preferences.overview", result.state.lastForegroundToken)
    }
}

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
    @Test fun finalProductIdentitiesAreStableButFutureAppsAreNotInstalledEarly() {
        assertEquals(
            listOf("chart", "chart_library", "data", "navigation", "preferences"),
            YokuliProductModel.finalApps.map { it.appId.value },
        )
        val installed = productionCatalog.entries.mapTo(linkedSetOf()) { it.entryId.value }
        assertEquals(setOf("chart", "settings", "nmea-input", "data-sources", "chart_library"), installed)
        assertFalse("data" in installed)
        assertFalse("navigation" in installed)
        assertFalse("preferences" in installed)
    }

    @Test fun currentCompositionRootDefersMigrationUntilTheReplacementAppHasARealHost() {
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

        assertEquals(0, result.state.productModelVersion)
        assertTrue(result.appliedVersions.isEmpty())
        assertEquals(legacy.document, result.state.document)
    }
}

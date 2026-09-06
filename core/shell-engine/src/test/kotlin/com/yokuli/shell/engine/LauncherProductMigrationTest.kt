package com.yokuli.shell.engine

import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.ProfileId
import com.yokuli.shell.engine.layout.Spacer
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherProductMigrationTest {
    private val data = LauncherEntryId("data")
    private val preferences = LauncherEntryId("preferences")
    private val plan = LauncherProductMigrationPlan(
        listOf(
            LauncherProductMigrationStep(
                version = 1,
                targetEntryId = data,
                legacyEntryIds = setOf(LauncherEntryId("nmea-input"), LauncherEntryId("data-sources")),
                tokenAliases = listOf(
                    LauncherTokenAlias("nmea.root", "data.inputs"),
                    LauncherTokenAlias("nmea.connection.", "data.input.", prefix = true),
                    LauncherTokenAlias("sources.root", "data.overview"),
                ),
            ),
            LauncherProductMigrationStep(
                version = 2,
                targetEntryId = preferences,
                legacyEntryIds = setOf(LauncherEntryId("settings")),
                tokenAliases = listOf(LauncherTokenAlias("settings.appearance", "preferences.appearance")),
            ),
        ),
    )

    @Test fun twoLegacyDataTilesCollapseToTheEarliestRankAndKeepItsIdentitySizeAndGroup() {
        val source = state(
            placement("nmea", "nmea-input", MarineTileSize.ICON_1X1, 3_000, "instruments"),
            placement("sources", "data-sources", MarineTileSize.WIDE_4X2, 1_000, "bridge"),
            placement("chart", "chart", MarineTileSize.STANDARD_2X2, 2_000, null),
        )

        val result = plan.migrate(source, setOf(data))

        assertEquals(listOf(1), result.appliedVersions)
        assertEquals(1, result.state.productModelVersion)
        val migrated = requireNotNull(result.state.document).placements.single { it.entryId == data }
        assertEquals("tile-sources", migrated.tileId.value)
        assertEquals(MarineTileSize.WIDE_4X2, migrated.size)
        assertEquals(1_000L, migrated.rank)
        assertEquals("bridge", migrated.groupId)
        assertEquals(2, result.state.document?.placements?.size)
    }

    @Test fun anExistingTargetAndLegacyAliasesStillCollapseDeterministically() {
        val source = state(
            placement("data", "data", MarineTileSize.STANDARD_2X2, 5_000, null),
            placement("old", "nmea-input", MarineTileSize.ICON_1X1, 2_000, "old-group"),
        )

        val result = plan.migrate(source, setOf(data))

        val migrated = requireNotNull(result.state.document).placements.single()
        assertEquals(data, migrated.entryId)
        assertEquals("tile-old", migrated.tileId.value)
        assertEquals("old-group", migrated.groupId)
    }

    @Test fun unavailableReplacementStopsTheVersionChainAndLeavesUserLayoutUntouched() {
        val source = state(
            placement("settings", "settings", MarineTileSize.ICON_1X1, 10, "system"),
            placement("nmea", "nmea-input", MarineTileSize.STANDARD_2X2, 20, null),
        ).copy(lastForegroundToken = "nmea.root")

        val result = plan.migrate(source, installedEntryIds = emptySet())

        assertEquals(source, result.state)
        assertTrue(result.appliedVersions.isEmpty())
    }

    @Test fun stepsActivateOnlyWhenRealTargetsArriveAndLaunchTokensFollowTheSameBoundary() {
        val source = state(
            placement("settings", "settings", MarineTileSize.ICON_1X1, 10, "system"),
            placement("nmea", "nmea-input", MarineTileSize.STANDARD_2X2, 20, null),
        ).copy(lastForegroundToken = "nmea.connection.616263")

        val dataOnly = plan.migrate(source, setOf(data))
        assertEquals(1, dataOnly.state.productModelVersion)
        assertEquals("data.input.616263", dataOnly.state.lastForegroundToken)
        assertTrue(requireNotNull(dataOnly.state.document).placements.any { it.entryId.value == "settings" })

        val completed = plan.migrate(
            dataOnly.state.copy(lastForegroundToken = "settings.appearance"),
            setOf(data, preferences),
        )
        assertEquals(listOf(2), completed.appliedVersions)
        assertEquals("preferences.appearance", completed.state.lastForegroundToken)
        assertTrue(requireNotNull(completed.state.document).placements.any { it.entryId == preferences })
    }

    @Test fun migrationIsIdempotentAndDoesNotTouchSpacersOrUnrelatedTiles() {
        val source = state(
            placement("chart", "chart", MarineTileSize.WIDE_4X2, 0, null),
            placement("sources", "data-sources", MarineTileSize.STANDARD_2X2, 1_024, "data"),
            placement("settings", "settings", MarineTileSize.ICON_1X1, 2_048, "system"),
        ).copy(lastForegroundToken = "sources.root")

        val first = plan.migrate(source, setOf(data, preferences)).state
        val second = plan.migrate(first, setOf(data, preferences))

        assertEquals(first, second.state)
        assertTrue(second.appliedVersions.isEmpty())
        assertEquals("tile-chart", requireNotNull(first.document).placements.first().tileId.value)
        assertEquals(listOf("gap"), first.document?.spacers?.map { it.spacerId.value })
    }

    @Test fun negativePersistedProductVersionIsRecoveredBeforeApplyingAvailableSteps() {
        val recovered = LauncherPersistedStateMigration.migrate(
            state(placement("nmea", "nmea-input", MarineTileSize.STANDARD_2X2, 20, null))
                .copy(productModelVersion = -4),
            LauncherPersistedState(),
            plan,
            setOf(data),
        )

        assertEquals(1, recovered.state.productModelVersion)
        assertTrue(LauncherPersistenceIncident.INVALID_PRODUCT_MODEL_VERSION_REPLACED in recovered.incidents)
        assertTrue(LauncherPersistenceIncident.PRODUCT_MODEL_MIGRATED in recovered.incidents)
    }

    private fun state(vararg placements: TilePlacement) = LauncherPersistedState(
        schemaVersion = 2,
        productModelVersion = 0,
        document = StartDocument(
            schemaVersion = 2,
            profileId = ProfileId("phone-portrait-4col"),
            defaultLayoutVersion = 2,
            placements = placements.toList(),
            spacers = listOf(
                Spacer(TileInstanceId("gap"), MarineTileSize.ICON_1X1, 4_096, "layout"),
            ),
        ),
    )

    private fun placement(
        tile: String,
        entry: String,
        size: MarineTileSize,
        rank: Long,
        group: String?,
    ) = TilePlacement(TileInstanceId("tile-$tile"), LauncherEntryId(entry), size, rank, group)
}

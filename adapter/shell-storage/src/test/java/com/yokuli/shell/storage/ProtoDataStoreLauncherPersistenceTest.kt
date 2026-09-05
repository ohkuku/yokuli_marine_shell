package com.yokuli.shell.storage

import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.engine.LauncherPersistenceIncident
import com.yokuli.shell.engine.LauncherStartupHealth
import com.yokuli.shell.engine.PersistedLauncherPage
import com.yokuli.shell.engine.geometry.ProfileId
import com.yokuli.shell.engine.layout.Spacer
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import com.yokuli.shell.storage.proto.LauncherStateProto
import com.yokuli.shell.storage.proto.SpacerProto
import com.yokuli.shell.storage.proto.StartDocumentProto
import com.yokuli.shell.storage.proto.TilePlacementProto
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtoDataStoreLauncherPersistenceTest {
    private val document = StartDocument(
        schemaVersion = 2,
        profileId = ProfileId("phone-portrait-4col"),
        defaultLayoutVersion = 2,
        placements = listOf(
            TilePlacement(
                TileInstanceId("tile-chart"),
                LauncherEntryId("chart"),
                MarineTileSize.WIDE_4X2,
                0L,
            ),
        ),
        spacers = listOf(Spacer(TileInstanceId("spacer-one"), MarineTileSize.STANDARD_2X2, 1024L, "weather")),
    )
    private val defaults = LauncherPersistedState(document = document)

    @Test
    fun retiredAndUnknownSizesDecodeWithoutDiscardingLayoutIdentityOrOrder() {
        fun placement(id: String, size: String, rank: Long, group: String) = TilePlacementProto.newBuilder()
            .setTileId("tile-$id")
            .setEntryId(id)
            .setSize(size)
            .setRank(rank)
            .setGroupId(group)
            .build()
        val proto = LauncherStateProto.newBuilder()
            .setSchemaVersion(2)
            .setStartDocument(
                StartDocumentProto.newBuilder()
                    .setSchemaVersion(2)
                    .setProfileId("phone-portrait-4col")
                    .setDefaultLayoutVersion(2)
                    .addPlacements(placement("small", "ICON_1X1", 3L, "g1"))
                    .addPlacements(placement("compact", "COMPACT_2X1", 19L, "g2"))
                    .addPlacements(placement("tall", "TALL_2X4", 47L, "g3"))
                    .addPlacements(placement("large", "LARGE_4X4", 89L, "g4"))
                    .addPlacements(placement("future", "FUTURE_9X9", 144L, "g5"))
                    .addSpacers(
                        SpacerProto.newBuilder()
                            .setSpacerId("spacer-legacy")
                            .setSize("COMPACT_2X1")
                            .setRank(233L)
                            .setGroupId("space")
                            .build(),
                    )
                    .build(),
            )
            .build()

        val decoded = requireNotNull(LauncherProtoMapper.decode(proto).document)

        assertEquals(listOf("tile-small", "tile-compact", "tile-tall", "tile-large", "tile-future"), decoded.placements.map { it.tileId.value })
        assertEquals(listOf(3L, 19L, 47L, 89L, 144L), decoded.placements.map { it.rank })
        assertEquals(listOf("g1", "g2", "g3", "g4", "g5"), decoded.placements.map { it.groupId })
        assertEquals(
            listOf(
                MarineTileSize.ICON_1X1,
                MarineTileSize.STANDARD_2X2,
                MarineTileSize.WIDE_4X2,
                MarineTileSize.WIDE_4X2,
                MarineTileSize.ICON_1X1,
            ),
            decoded.placements.map { it.size },
        )
        assertEquals(MarineTileSize.STANDARD_2X2, decoded.spacers.single().size)
        assertEquals(233L, decoded.spacers.single().rank)
        assertEquals("space", decoded.spacers.single().groupId)
        assertEquals(decoded, LauncherProtoMapper.decode(LauncherProtoMapper.encode(LauncherPersistedState(document = decoded))).document)
    }

    @Test
    fun aFreshDataStoreRestoresTheCommittedSnapshot() = runBlocking {
        val path = Files.createTempDirectory("yokuli-proto").resolve("launcher.pb")
        val firstJob = SupervisorJob()
        val first = ProtoDataStoreLauncherPersistence.create(path.toFile(), CoroutineScope(firstJob + Dispatchers.IO), defaults)
        first.loaded.first { it }
        first.save(defaults.copy(themeModeName = "LIGHT", accentName = "MAGENTA", languageTag = "en"))
        firstJob.cancelAndJoin()

        val secondJob = SupervisorJob()
        val second = ProtoDataStoreLauncherPersistence.create(path.toFile(), CoroutineScope(secondJob + Dispatchers.IO), defaults)
        val restored = second.load()

        assertEquals(document, restored?.document)
        assertEquals("LIGHT", restored?.themeModeName)
        assertEquals("MAGENTA", restored?.accentName)
        assertEquals("en", restored?.languageTag)
        secondJob.cancelAndJoin()
    }

    @Test
    fun corruptProtoFallsBackWithoutCrashing() = runBlocking {
        val path = Files.createTempDirectory("yokuli-corrupt-proto").resolve("launcher.pb")
        Files.write(path, byteArrayOf(0x80.toByte()))
        val job = SupervisorJob()
        val store = ProtoDataStoreLauncherPersistence.create(path.toFile(), CoroutineScope(job + Dispatchers.IO), defaults)

        assertEquals(defaults, store.load())
        assertEquals(
            LauncherPersistenceIncident.CORRUPT_DATA_REPLACED,
            withTimeout(2_000) { store.incidents.first() },
        )
        job.cancelAndJoin()
    }

    @Test
    fun legacySchemaIsMigratedRecordedAndCommitted() = runBlocking {
        val path = Files.createTempDirectory("yokuli-legacy-proto").resolve("launcher.pb")
        Files.write(
            path,
            LauncherStateProto.newBuilder()
                .setSchemaVersion(0)
                .setThemeMode("INVALID")
                .setAccent("MAGENTA")
                .setLanguageTag("en")
                .build()
                .toByteArray(),
        )
        val job = SupervisorJob()
        val store = ProtoDataStoreLauncherPersistence.create(path.toFile(), CoroutineScope(job + Dispatchers.IO), defaults)

        val restored = withTimeout(2_000) { store.load() }
        val incident = withTimeout(2_000) { store.incidents.first() }
        withTimeout(2_000) {
            while (LauncherStateProto.parseFrom(Files.readAllBytes(path)).schemaVersion != 2) {
                kotlinx.coroutines.delay(10)
            }
        }

        assertEquals("DARK", restored?.themeModeName)
        assertEquals("MAGENTA", restored?.accentName)
        assertEquals("en", restored?.languageTag)
        assertEquals(LauncherPersistenceIncident.LEGACY_SCHEMA_MIGRATED, incident)
        job.cancelAndJoin()
    }

    @Test
    fun atomicUpdatesPreserveTheEntireLauncherSnapshot() = runBlocking {
        val path = Files.createTempDirectory("yokuli-atomic-proto").resolve("launcher.pb")
        val job = SupervisorJob()
        val store = ProtoDataStoreLauncherPersistence.create(path.toFile(), CoroutineScope(job + Dispatchers.IO), defaults)
        store.loaded.first { it }
        val moved = defaults.copy(
            layoutLocked = true,
            lastLauncherPage = PersistedLauncherPage.ALL_APPS,
            lastForegroundToken = "chart.root",
            recovery = LauncherStartupHealth(lastLaunchEpochMillis = 7),
        )
        store.save(moved)
        store.savePreferences("LIGHT", "EMERALD", "en")
        store.saveDocument(document.copy(defaultLayoutVersion = 9))
        store.beginLaunch(10)
        store.markLaunchHealthy()

        val result = store.load()
        assertEquals(9, result?.document?.defaultLayoutVersion)
        assertEquals("LIGHT", result?.themeModeName)
        assertEquals("EMERALD", result?.accentName)
        assertEquals("en", result?.languageTag)
        assertEquals(true, result?.layoutLocked)
        assertEquals(PersistedLauncherPage.ALL_APPS, result?.lastLauncherPage)
        assertEquals("chart.root", result?.lastForegroundToken)
        assertFalse(requireNotNull(result).recovery.launchPending)
        job.cancelAndJoin()
    }
}

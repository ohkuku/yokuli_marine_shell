package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.LauncherHostPort
import com.yokuli.shell.contract.LauncherSystemStatus
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileContentSnapshot
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDurableRestoreTest {
    private val chart = descriptor("chart", MarineTileSize.WIDE_4X2)
    private val settings = descriptor("settings", MarineTileSize.ICON_1X1)
    private val catalog = LauncherCatalogSnapshot(
        revision = 1,
        apps = listOf(chart, settings).map { LauncherAppDescriptor(it.appId, it.entryId) },
        entries = listOf(chart, settings),
    )
    private val defaults = document(chart, settings)

    @Test
    fun delayedProcessRestoreRejectsMutationUntilCommittedDocumentLoads() = runBlocking {
        val persisted = document(chart)
        val storage = DelayedPersistence()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = DefaultLauncherEngine(FixedHost(catalog), storage, defaults, scope)

        engine.dispatch(LauncherAction.ShowAllApps)
        assertEquals(LauncherRecoveryMode.RESTORING, engine.state.value.recoveryMode)
        storage.complete(LauncherPersistedState(document = persisted))

        withTimeout(2_000) {
            while (engine.state.value.recoveryMode != LauncherRecoveryMode.NORMAL) delay(10)
        }
        assertEquals(ShellVisualSurface.Desktop, engine.state.value.surface)
        assertEquals(listOf(chart.entryId), engine.state.value.start.document.placements.map { it.entryId })
        scope.cancel()
    }

    @Test
    fun catalogChangesAreSerializedWhileDurableStateIsLoading() = runBlocking {
        val storage = DelayedPersistence()
        val host = FixedHost(catalog)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = DefaultLauncherEngine(host, storage, defaults, scope)
        host.catalog.value = catalog.copy(revision = 2, apps = listOf(settings).map {
            LauncherAppDescriptor(it.appId, it.entryId)
        }, entries = listOf(settings))

        withTimeout(2_000) {
            while (engine.state.value.catalog.revision != 2L) delay(10)
        }
        storage.complete(LauncherPersistedState(document = defaults))
        withTimeout(2_000) {
            while (engine.state.value.recoveryMode != LauncherRecoveryMode.NORMAL) delay(10)
        }

        assertEquals(2L, engine.state.value.catalog.revision)
        assertEquals(listOf(settings.entryId), engine.state.value.start.document.placements.map { it.entryId })
        scope.cancel()
    }

    @Test
    fun persistenceMigrationIncidentsAreRecordedByTheSerializedEngine() = runBlocking {
        val storage = DelayedPersistence(initiallyLoaded = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = DefaultLauncherEngine(FixedHost(catalog), storage, defaults, scope)
        storage.report(LauncherPersistenceIncident.LEGACY_SCHEMA_MIGRATED)

        withTimeout(2_000) {
            while (engine.state.value.incidentLog.isEmpty()) delay(10)
        }
        assertTrue(
            engine.state.value.incidentLog.single() is LauncherIncident.PersistenceMigration,
        )
        scope.cancel()
    }

    private fun descriptor(id: String, size: MarineTileSize): LauncherEntryDescriptor {
        val appId = LauncherAppId(id)
        return LauncherEntryDescriptor(
            entryId = LauncherEntryId(id),
            appId = appId,
            launchToken = LaunchToken("$id.root"),
            defaultSize = size,
            supportedSizes = MarineTileSize.entries,
            pinPolicy = PinPolicy.PINNABLE,
        )
    }

    private fun document(vararg entries: LauncherEntryDescriptor) = StartDocument(
        schemaVersion = 1,
        profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
        defaultLayoutVersion = 1,
        placements = entries.mapIndexed { index, entry ->
            TilePlacement(TileInstanceId("tile-${entry.entryId.value}"), entry.entryId, entry.defaultSize, GridCell(0, index * 2))
        },
    )
}

private class DelayedPersistence(initiallyLoaded: Boolean = false) : LauncherPersistencePort {
    override val state = MutableStateFlow<LauncherPersistedState?>(null)
    override val document = MutableStateFlow<StartDocument?>(null)
    override val loaded = MutableStateFlow(initiallyLoaded)
    private val mutableIncidents = MutableSharedFlow<LauncherPersistenceIncident>(replay = 1)
    override val incidents = mutableIncidents.asSharedFlow()

    fun complete(value: LauncherPersistedState) {
        state.value = value
        document.value = value.document
        loaded.value = true
    }

    fun report(incident: LauncherPersistenceIncident) {
        mutableIncidents.tryEmit(incident)
    }

    override suspend fun load(): LauncherPersistedState? = state.value
    override suspend fun save(state: LauncherPersistedState) = complete(state)
    override suspend fun reset() {
        state.value = null
        document.value = null
    }
}

private class FixedHost(override val catalog: MutableStateFlow<LauncherCatalogSnapshot>) : LauncherHostPort {
    constructor(catalog: LauncherCatalogSnapshot) : this(MutableStateFlow(catalog))
    override val tileContents = MutableStateFlow<Map<LauncherEntryId, TileContentSnapshot>>(emptyMap())
    override val systemStatus = MutableStateFlow(LauncherSystemStatus())
    override suspend fun resolveLaunch(token: LaunchToken): LaunchResolution = LaunchResolution.Unresolved(token)
}

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
import com.yokuli.shell.contract.WpTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartLayoutEditor
import com.yokuli.shell.engine.layout.TilePlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherEngineTest {
    private val chart = descriptor("chart", WpTileSize.WIDE_4X2)
    private val settings = descriptor("settings", WpTileSize.SMALL_1X1)
    private val catalog = snapshot(1, listOf(chart, settings))
    private val defaultDocument = StartDocument(
        schemaVersion = 1,
        profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
        defaultLayoutVersion = 1,
        placements = listOf(
            TilePlacement(TileInstanceId("tile-chart"), chart.entryId, chart.defaultSize, GridCell(0, 0)),
            TilePlacement(TileInstanceId("tile-settings"), settings.entryId, settings.defaultSize, GridCell(0, 2)),
        ),
    )

    @Test
    fun unresolvedTokenKeepsCurrentSurfaceAndLogsIncident() = runBlocking {
        val scope = testScope()
        val engine = engine(FakeHostPort(catalog), InMemoryLauncherPersistence(), scope)
        val incident = async { withTimeout(2_000) { engine.effects.first() } }

        engine.dispatch(LauncherAction.Open(LaunchToken("removed.root")))

        assertTrue(incident.await() is LauncherEffect.LogIncident)
        assertEquals(LauncherSurface.Start, engine.state.value.surface)
        scope.cancel()
    }

    @Test
    fun rapidOpenBackAndHomeActionsAreSerialized() = runBlocking {
        val scope = testScope()
        val host = FakeHostPort(catalog, slowToken = chart.launchToken)
        val engine = engine(host, InMemoryLauncherPersistence(), scope)

        engine.dispatch(LauncherAction.Open(chart.launchToken))
        engine.dispatch(LauncherAction.Back)
        engine.dispatch(LauncherAction.Open(settings.launchToken))
        engine.dispatch(LauncherAction.Home)

        await { host.resolvedTokens.size == 2 && engine.state.value.surface == LauncherSurface.Start }
        assertEquals(listOf(chart.launchToken, settings.launchToken), host.resolvedTokens)
        assertEquals(2, engine.state.value.tasks.tasks.size)
        scope.cancel()
    }

    @Test
    fun cancelAndUndoRestoreTheExactPreviousDocument() {
        val reducer = DefaultLauncherReducer()
        val initial = initialState()
        val proposal = StartLayoutEditor.unpin(defaultDocument, TileInstanceId("tile-settings"))!!
        val context = LauncherReducerContext(defaultDocument, WpReferenceProfiles.PHONE_PORTRAIT_4COL)

        val begun = reducer.reduce(initial, LauncherAction.BeginLayoutTransaction(proposal), context).state
        assertEquals("layout-1", begun.start.activeTransaction?.id)
        val cancelled = reducer.reduce(begun, LauncherAction.CancelLayoutTransaction, context).state
        assertEquals(defaultDocument, cancelled.start.document)

        val committed = reducer.reduce(initial, LauncherAction.ApplyLayoutProposal(proposal), context).state
        val undone = reducer.reduce(committed, LauncherAction.UndoLayout, context).state
        assertEquals(defaultDocument, undone.start.document)
    }

    @Test
    fun corruptPersistedDocumentUsesDeterministicFallback() {
        val corrupt = defaultDocument.copy(schemaVersion = -1, placements = emptyList())
        val first = engine(FakeHostPort(catalog), InMemoryLauncherPersistence(corrupt), testScope())
        val second = engine(FakeHostPort(catalog), InMemoryLauncherPersistence(corrupt), testScope())

        assertEquals(defaultDocument, first.state.value.start.document)
        assertEquals(first.state.value.start.document, second.state.value.start.document)
    }

    @Test
    fun hostCatalogFlowIsTheRuntimeCatalogSource() = runBlocking {
        val scope = testScope()
        val host = FakeHostPort(catalog)
        val engine = engine(host, InMemoryLauncherPersistence(), scope)

        host.catalogFlow.value = snapshot(2, listOf(chart))

        await { engine.state.value.catalog.revision == 2L }
        assertEquals(listOf(chart.entryId), engine.state.value.start.document.placements.map { it.entryId })
        scope.cancel()
    }

    @Test
    fun aNewControllerRecoversTheCommittedDocumentFromItsPersistencePort() = runBlocking {
        val persistence = InMemoryLauncherPersistence()
        val firstScope = testScope()
        val first = engine(FakeHostPort(catalog), persistence, firstScope)
        val proposal = StartLayoutEditor.unpin(defaultDocument, TileInstanceId("tile-settings"))!!
        first.dispatch(LauncherAction.ApplyLayoutProposal(proposal))
        await { persistence.document.value == proposal.after }
        firstScope.cancel()

        val secondScope = testScope()
        val restored = engine(FakeHostPort(catalog), persistence, secondScope)
        assertEquals(proposal.after, restored.state.value.start.document)
        secondScope.cancel()
    }

    private fun initialState() = LauncherEngineState(
        surface = LauncherSurface.Start,
        start = StartScreenState(defaultDocument),
        allApps = AllAppsState(catalog.revision),
        tasks = InternalTaskState(),
        catalog = catalog,
    )

    private fun engine(
        host: LauncherHostPort,
        persistence: LauncherPersistencePort,
        scope: CoroutineScope,
    ) = DefaultLauncherEngine(host, persistence, defaultDocument, scope)

    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun await(predicate: () -> Boolean) = withTimeout(2_000) {
        while (!predicate()) delay(10)
    }

    private fun descriptor(id: String, size: WpTileSize): LauncherEntryDescriptor {
        val appId = LauncherAppId(id)
        return LauncherEntryDescriptor(
            entryId = LauncherEntryId(id),
            appId = appId,
            launchToken = LaunchToken("$id.root"),
            defaultSize = size,
            supportedSizes = WpTileSize.entries,
            pinPolicy = PinPolicy.PINNABLE,
        )
    }

    private fun snapshot(revision: Long, entries: List<LauncherEntryDescriptor>) = LauncherCatalogSnapshot(
        revision = revision,
        apps = entries.map { LauncherAppDescriptor(it.appId, it.entryId) },
        entries = entries,
    )
}

private class FakeHostPort(
    catalog: LauncherCatalogSnapshot,
    private val slowToken: LaunchToken? = null,
) : LauncherHostPort {
    val catalogFlow = MutableStateFlow(catalog)
    override val catalog = catalogFlow
    override val tileContents = MutableStateFlow<Map<LauncherEntryId, TileContentSnapshot>>(emptyMap())
    override val systemStatus = MutableStateFlow(LauncherSystemStatus())
    val resolvedTokens = mutableListOf<LaunchToken>()

    override suspend fun resolveLaunch(token: LaunchToken): LaunchResolution {
        if (token == slowToken) delay(120)
        resolvedTokens += token
        val entry = catalog.value.entries.firstOrNull { it.launchToken == token }
            ?: return LaunchResolution.Unresolved(token)
        return LaunchResolution.Internal(entry.appId, token)
    }
}

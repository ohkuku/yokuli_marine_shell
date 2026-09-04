package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.WpTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import com.yokuli.shell.engine.layout.StartLayoutEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherNavigationTest {
    private val chart = entry("chart", "chart.browse", WpTileSize.WIDE_4X2)
    private val settings = entry("settings", "settings.root", WpTileSize.SMALL_1X1)
    private val catalog = LauncherCatalogSnapshot(
        revision = 1,
        apps = listOf(chart, settings).map { LauncherAppDescriptor(it.appId, it.entryId) },
        entries = listOf(chart, settings),
    )
    private val document = StartDocument(
        schemaVersion = 1,
        profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
        defaultLayoutVersion = 1,
        placements = listOf(
            TilePlacement(TileInstanceId("tile-chart"), chart.entryId, chart.defaultSize, GridCell(0, 0)),
            TilePlacement(TileInstanceId("tile-settings"), settings.entryId, settings.defaultSize, GridCell(0, 2)),
        ),
    )
    private val reducer = DefaultLauncherReducer()

    @Test
    fun internalBackPopsOpaqueRouteBeforeReturningToStart() {
        val opened = open(initial(), settings.appId, settings.launchToken)
        val detail = LaunchToken("settings.appearance")
        val drilledIn = open(opened, settings.appId, detail)

        val popped = reduce(drilledIn, LauncherAction.Back)
        assertEquals(settings.launchToken, popped.state.tasks.tasks.single().lastLaunchToken)
        assertTrue(popped.state.surface is LauncherSurface.InternalApp)

        val start = reduce(popped.state, LauncherAction.Back)
        assertEquals(LauncherSurface.Start, start.state.surface)
        assertEquals(1, start.state.tasks.tasks.size)
    }

    @Test
    fun homePreservesInternalTasks() {
        val opened = open(initial(), chart.appId, chart.launchToken)
        val result = reduce(opened, LauncherAction.Home)

        assertEquals(LauncherSurface.Start, result.state.surface)
        assertEquals(opened.tasks, result.state.tasks)
    }

    @Test
    fun searchIsATransientAndBackClosesItFirst() {
        val searched = reduce(initial(), LauncherAction.OpenSearch).state
        val queried = reduce(searched, LauncherAction.UpdateSearchQuery("set")).state

        assertEquals(LauncherSurface.Start, queried.surface)
        assertEquals("set", (queried.transient as LauncherTransient.Search).query)
        assertEquals(null, reduce(queried, LauncherAction.Back).state.transient)
    }

    @Test
    fun recentsCanResumeAnExistingTask() {
        val chartOpen = open(initial(), chart.appId, chart.launchToken)
        val settingsOpen = open(chartOpen.copy(surface = LauncherSurface.Start), settings.appId, settings.launchToken)
        val recents = reduce(settingsOpen, LauncherAction.ShowRecents).state
        val chartTask = recents.tasks.tasks.first { it.appId == chart.appId }

        val resumed = reduce(recents, LauncherAction.ActivateTask(chartTask.taskId)).state
        assertEquals(LauncherSurface.InternalApp(chartTask.taskId), resumed.surface)
        assertEquals(2, resumed.tasks.tasks.size)
    }

    @Test
    fun backAtStartRequestsHostExitWithoutMutatingState() {
        val result = reduce(initial(), LauncherAction.Back)
        assertEquals(initial(), result.state)
        assertTrue(result.effects.single() is LauncherEffect.RequestHostExit)
    }

    @Test
    fun shellKeysCancelAProvisionalLayoutBeforeLeavingStart() {
        val proposal = StartLayoutEditor.unpin(document, TileInstanceId("tile-settings"))!!
        val provisional = reduce(initial(), LauncherAction.BeginLayoutTransaction(proposal)).state

        val searched = reduce(provisional, LauncherAction.OpenSearch).state
        assertEquals(document, searched.start.document)
        assertEquals(null, searched.start.activeTransaction)

        val recents = reduce(provisional, LauncherAction.ShowRecents).state
        assertEquals(document, recents.start.document)
        assertEquals(null, recents.start.activeTransaction)
    }

    private fun open(state: LauncherEngineState, appId: LauncherAppId, token: LaunchToken): LauncherEngineState =
        reducer.reduce(
            state,
            LauncherAction.Open(token),
            context(LaunchResolution.Internal(appId, token)),
        ).state

    private fun reduce(state: LauncherEngineState, action: LauncherAction) = reducer.reduce(state, action, context())

    private fun context(resolution: LaunchResolution? = null) = LauncherReducerContext(
        defaultDocument = document,
        profile = WpReferenceProfiles.PHONE_PORTRAIT_4COL,
        launchResolution = resolution,
    )

    private fun initial() = LauncherEngineState(
        surface = LauncherSurface.Start,
        start = StartScreenState(document),
        allApps = AllAppsState(1),
        tasks = InternalTaskState(),
        catalog = catalog,
    )

    private fun entry(id: String, token: String, size: WpTileSize): LauncherEntryDescriptor {
        val appId = LauncherAppId(id)
        return LauncherEntryDescriptor(
            entryId = LauncherEntryId(id),
            appId = appId,
            launchToken = LaunchToken(token),
            defaultSize = size,
            supportedSizes = listOf(size),
            pinPolicy = PinPolicy.PINNABLE,
        )
    }
}

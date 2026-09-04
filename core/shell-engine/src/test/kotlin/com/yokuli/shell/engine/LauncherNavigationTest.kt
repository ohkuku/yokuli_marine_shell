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
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import com.yokuli.shell.engine.layout.StartLayoutEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherNavigationTest {
    private val chart = entry("chart", "chart.browse", MarineTileSize.WIDE_4X2)
    private val settings = entry("settings", "settings.root", MarineTileSize.ICON_1X1)
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
            TilePlacement(TileInstanceId("tile-chart"), chart.entryId, chart.defaultSize, 0L),
            TilePlacement(TileInstanceId("tile-settings"), settings.entryId, settings.defaultSize, 1024L),
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
        assertTrue(popped.state.surface is ShellVisualSurface.Module)

        val start = reduce(popped.state, LauncherAction.Back)
        assertEquals(ShellVisualSurface.Desktop, start.state.surface)
        assertEquals(1, start.state.tasks.tasks.size)
    }

    @Test
    fun desktopCommandPreservesInternalTasks() {
        val opened = open(initial(), chart.appId, chart.launchToken)
        val result = reduce(opened, LauncherAction.ShowDesktop)

        assertEquals(ShellVisualSurface.Desktop, result.state.surface)
        assertEquals(opened.tasks, result.state.tasks)
    }

    @Test
    fun searchIsAFirstClassSurfaceAndBackReturnsToItsSource() {
        val searched = reduce(initial(), LauncherAction.OpenSearch).state
        val queried = reduce(searched, LauncherAction.UpdateSearchQuery("set")).state

        assertEquals(
            ShellVisualSurface.Search("set", ShellVisualSurface.Desktop),
            queried.surface,
        )
        assertEquals(ShellVisualSurface.Desktop, reduce(queried, LauncherAction.Back).state.surface)
    }

    @Test
    fun searchResultLaunchTransitionsDirectlyFromSearchToModule() {
        val search = reduce(initial(), LauncherAction.OpenSearch).state
        val opened = reducer.reduce(
            search,
            LauncherAction.Open(chart.launchToken),
            context(LaunchResolution.Internal(chart.appId, chart.launchToken)),
        ).state

        assertEquals(ShellVisualSurface.Module(InternalAppTaskId("chart")), opened.surface)
        assertEquals(ShellTransitionKind.SEARCH_TO_MODULE, opened.transitionRequest?.kind)
        assertEquals(ShellVisualSurface.Search("", ShellVisualSurface.Desktop), opened.transitionRequest?.from)
    }

    @Test
    fun alphabetJumpIsAnEngineTransientAndBackClosesItBeforeLeavingAllApps() {
        val allApps = reduce(initial(), LauncherAction.ShowAllApps).state
        val jump = reduce(allApps, LauncherAction.OpenAlphabetJump).state

        assertEquals(ShellVisualSurface.ModuleList, jump.surface)
        assertEquals(LauncherTransient.AlphabetJump, jump.transient)

        val dismissed = reduce(jump, LauncherAction.Back).state
        assertEquals(ShellVisualSurface.ModuleList, dismissed.surface)
        assertEquals(null, dismissed.transient)
    }

    @Test
    fun recentsCanResumeAnExistingTask() {
        val chartOpen = open(initial(), chart.appId, chart.launchToken)
        val settingsOpen = open(chartOpen.copy(surface = ShellVisualSurface.Desktop), settings.appId, settings.launchToken)
        val recents = reduce(settingsOpen, LauncherAction.ShowRecents).state
        val chartTask = recents.tasks.tasks.first { it.appId == chart.appId }

        val resumed = reduce(recents, LauncherAction.ActivateTask(chartTask.taskId)).state
        assertEquals(ShellVisualSurface.Module(chartTask.taskId), resumed.surface)
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

    @Test
    fun safeModeUsesDefaultDocumentWithoutSilentlyOverwritingTheSavedLayout() {
        val custom = document.copy(placements = listOf(document.placements.first()))
        val state = initial().copy(start = StartScreenState(custom))

        val safe = reduce(state, LauncherAction.EnterSafeMode)

        assertEquals(LauncherRecoveryMode.SAFE_MODE, safe.state.recoveryMode)
        assertEquals(document, safe.state.start.document)
        assertTrue(safe.effects.none { it is LauncherEffect.PersistDocument })
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
        surface = ShellVisualSurface.Desktop,
        start = StartScreenState(document),
        allApps = AllAppsState(1),
        tasks = InternalTaskState(),
        catalog = catalog,
    )

    private fun entry(id: String, token: String, size: MarineTileSize): LauncherEntryDescriptor {
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

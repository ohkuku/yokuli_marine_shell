package com.yokuli.shell.engine.navigation

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.LauncherHostPort
import com.yokuli.shell.contract.LauncherSystemStatus
import com.yokuli.shell.contract.TileContentSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigatorTest {
    private val chart = LauncherAppId("chart")
    private val settings = LauncherAppId("settings")
    private val port = FakeHostPort(
        mapOf(
            LaunchToken("chart.browse") to chart,
            LaunchToken("settings.overview") to settings,
            LaunchToken("settings.map") to settings,
        ),
    )
    private val navigator = ShellNavigator(port)

    @Test
    fun opaqueTokenOpensOneResolvedInternalTask() = runBlocking {
        val token = LaunchToken("chart.browse")
        val state = navigator.reduce(ShellNavigationState(), ShellCommand.Open(token))

        assertEquals(token, state.tasks.single().token)
        assertEquals(chart, state.tasks.single().appId)
        assertEquals(ShellSurface.App(state.tasks.single().id), state.surface)
    }

    @Test
    fun anotherTokenReusesTheSameOpaqueAppTask() = runBlocking {
        val root = navigator.reduce(
            ShellNavigationState(),
            ShellCommand.Open(LaunchToken("settings.overview")),
        )
        val map = navigator.reduce(root, ShellCommand.Open(LaunchToken("settings.map")))

        assertEquals(1, map.tasks.size)
        assertEquals(LaunchToken("settings.map"), map.tasks.single().token)
    }

    @Test
    fun unresolvedTokenIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                navigator.reduce(ShellNavigationState(), ShellCommand.Open(LaunchToken("unknown")))
            }
        }
    }

    @Test
    fun allAppsAndBackPreserveCurrentShellBehavior() = runBlocking {
        val apps = navigator.reduce(ShellNavigationState(), ShellCommand.ShowAllApps)
        assertEquals(ShellSurface.AllApps, apps.surface)
        val start = navigator.reduce(apps, ShellCommand.Back)
        assertEquals(ShellSurface.Start, start.surface)
        assertTrue(start.tasks.isEmpty())
    }
}

private class FakeHostPort(private val launches: Map<LaunchToken, LauncherAppId>) : LauncherHostPort {
    override val catalog: StateFlow<LauncherCatalogSnapshot> = MutableStateFlow(
        LauncherCatalogSnapshot(0, emptyList(), emptyList()),
    )
    override val tileContents: StateFlow<Map<LauncherEntryId, TileContentSnapshot>> = MutableStateFlow(emptyMap())
    override val systemStatus: StateFlow<LauncherSystemStatus> = MutableStateFlow(LauncherSystemStatus())

    override suspend fun resolveLaunch(token: LaunchToken): LaunchResolution = launches[token]?.let { appId ->
        LaunchResolution.Internal(appId, token)
    } ?: LaunchResolution.Unresolved(token)
}

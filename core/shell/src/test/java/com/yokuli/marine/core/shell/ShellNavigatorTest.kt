package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.DestinationId
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.model.ShellCommand
import com.yokuli.marine.core.model.ShellNavigationState
import com.yokuli.marine.core.model.ShellSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigatorTest {
    private val navigator = ShellNavigator(LauncherRegistry(listOf(contribution("chart"), contribution("settings"))))

    @Test
    fun installedTargetOpensOneTask() {
        val target = LaunchTarget(MarineAppId("chart"), DestinationId("chart.browse"))
        val state = navigator.reduce(ShellNavigationState(), ShellCommand.Open(target))
        assertEquals(target, state.tasks.single().target)
        assertEquals(ShellSurface.App(state.tasks.single().id), state.surface)
    }

    @Test
    fun anotherDestinationReusesTheSameAppTask() {
        val root = navigator.reduce(
            ShellNavigationState(),
            ShellCommand.Open(LaunchTarget(MarineAppId("settings"), DestinationId("settings.root"))),
        )
        val map = navigator.reduce(
            root,
            ShellCommand.Open(LaunchTarget(MarineAppId("settings"), DestinationId("settings.map"))),
        )
        assertEquals(1, map.tasks.size)
        assertEquals("settings.map", map.tasks.single().target.destination.value)
    }

    @Test
    fun uninstalledTargetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            navigator.reduce(
                ShellNavigationState(),
                ShellCommand.Open(LaunchTarget(MarineAppId("future"), DestinationId("future.root"))),
            )
        }
    }

    @Test
    fun allAppsAndBackFollowWindowsPhoneSemantics() {
        val apps = navigator.reduce(ShellNavigationState(), ShellCommand.ShowAllApps)
        assertEquals(ShellSurface.AllApps, apps.surface)
        val start = navigator.reduce(apps, ShellCommand.Back)
        assertEquals(ShellSurface.Start, start.surface)
        assertTrue(start.tasks.isEmpty())
    }
}

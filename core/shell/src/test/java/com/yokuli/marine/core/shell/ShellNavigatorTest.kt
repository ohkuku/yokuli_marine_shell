package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigatorTest {
    private val navigator = ShellNavigator()

    @Test fun anchorShortcutOpensChartTaskInAnchorMode() {
        val state = navigator.reduce(ShellNavigationState(), ShellCommand.Open(LaunchTarget.Chart(ChartMode.ANCHOR)))
        val task = state.tasks.single()
        assertEquals(MarineAppId.CHART, task.appId)
        assertEquals(LaunchTarget.Chart(ChartMode.ANCHOR), task.target)
        assertEquals(ShellSurface.App(task.id), state.surface)
    }

    @Test fun chartModesReuseOneChartUiTask() {
        val browse = navigator.reduce(ShellNavigationState(), ShellCommand.Open(LaunchTarget.Chart()))
        val anchor = navigator.reduce(browse, ShellCommand.Open(LaunchTarget.Chart(ChartMode.ANCHOR)))
        assertEquals(1, anchor.tasks.size)
        assertEquals(ChartMode.ANCHOR, (anchor.tasks.single().target as LaunchTarget.Chart).mode)
    }

    @Test fun homeReturnsStartWithoutClosingUiTasks() {
        val opened = navigator.reduce(ShellNavigationState(), ShellCommand.Open(LaunchTarget.Cockpit()))
        val home = navigator.reduce(opened, ShellCommand.Home)
        assertEquals(ShellSurface.Start, home.surface)
        assertEquals(1, home.tasks.size)
    }

    @Test fun allAppsAndBackFollowWindowsPhoneSemantics() {
        val apps = navigator.reduce(ShellNavigationState(), ShellCommand.ShowAllApps)
        assertEquals(ShellSurface.AllApps, apps.surface)
        val start = navigator.reduce(apps, ShellCommand.Back)
        assertEquals(ShellSurface.Start, start.surface)
        assertTrue(start.tasks.isEmpty())
    }
}

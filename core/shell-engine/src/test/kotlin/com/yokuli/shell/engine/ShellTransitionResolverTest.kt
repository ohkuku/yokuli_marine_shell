package com.yokuli.shell.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellTransitionResolverTest {
    @Test
    fun moduleListBridgeUsesPagerBack() {
        val request = ShellTransitionResolver.resolve(
            from = ShellVisualSurface.ModuleList,
            to = ShellVisualSurface.Desktop,
            trigger = ShellTransitionTrigger.BRIDGE,
        )

        assertEquals(ShellTransitionKind.PAGER_BACK, request.kind)
    }

    @Test
    fun moduleBridgeUsesModuleExit() {
        val request = ShellTransitionResolver.resolve(
            from = ShellVisualSurface.Module(InternalAppTaskId("settings")),
            to = ShellVisualSurface.Desktop,
            trigger = ShellTransitionTrigger.BRIDGE,
        )

        assertEquals(ShellTransitionKind.MODULE_TO_DESKTOP, request.kind)
    }

    @Test
    fun searchResultLaunchHasNoIntermediateSurface() {
        val search = ShellVisualSurface.Search("cha", ShellVisualSurface.ModuleList)
        val module = ShellVisualSurface.Module(InternalAppTaskId("chart"))
        val request = ShellTransitionResolver.resolve(search, module, ShellTransitionTrigger.SEARCH_RESULT)

        assertEquals(search, request.from)
        assertEquals(module, request.to)
        assertEquals(ShellTransitionKind.SEARCH_TO_MODULE, request.kind)
    }

    @Test
    fun internalModuleRoutesHaveExplicitForwardAndBackMotionKinds() {
        val module = ShellVisualSurface.Module(InternalAppTaskId("settings"))

        assertEquals(
            ShellTransitionKind.MODULE_ROUTE_FORWARD,
            ShellTransitionResolver.resolve(module, module, ShellTransitionTrigger.MODULE_ROUTE_FORWARD).kind,
        )
        assertEquals(
            ShellTransitionKind.MODULE_ROUTE_BACK,
            ShellTransitionResolver.resolve(module, module, ShellTransitionTrigger.MODULE_ROUTE_BACK).kind,
        )
    }
}

package com.yokuli.marine.core.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherArchitectureTest {
    @Test
    fun registryContainsOnlyInstalledContributions() {
        val registry = LauncherRegistry(listOf(contribution("chart"), contribution("settings")))
        assertEquals(listOf("chart", "settings"), registry.apps.map { it.id.value })
        assertEquals(listOf("chart", "settings"), registry.entries.map { it.id.value })
    }

    @Test
    fun duplicateAppIdsFailAtCompositionBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherRegistry(listOf(contribution("chart"), contribution("chart", "chart-secondary")))
        }
    }

    @Test
    fun duplicateLauncherEntryIdsFailAtCompositionBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherRegistry(listOf(contribution("chart", "shared"), contribution("settings", "shared")))
        }
    }
}

package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LauncherArchitectureTest {
    @Test fun coreAppRegistryContainsExactlyFourApps() {
        val coreApps = LauncherRegistry.entries.filter { it.kind == LauncherEntryKind.CORE_APP }
        assertEquals(MarineAppId.entries.toSet(), coreApps.map { it.coreAppId }.toSet())
        assertEquals(4, coreApps.size)
    }

    @Test fun launcherEntryIdsAreUnique() {
        val ids = LauncherRegistry.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun defaultLayoutContainsFiveTiles() {
        assertEquals(5, LauncherRegistry.defaultLayout.placements.size)
        assertEquals(
            setOf("chart", "anchor", "cockpit", "library", "system"),
            LauncherRegistry.defaultLayout.placements.map { it.entryId.value }.toSet(),
        )
    }

    @Test fun shortcutsOpenTheirOwningWorkspace() {
        assertEquals(LaunchTarget.Chart(ChartMode.ANCHOR), entry("anchor").launchTarget)
        assertEquals(LaunchTarget.Chart(ChartMode.NAVIGATE), entry("navigation").launchTarget)
        assertEquals(LaunchTarget.Chart(ChartMode.SURVEY), entry("survey").launchTarget)
        assertEquals(LaunchTarget.Library(LibrarySection.TRIPS), entry("trips").launchTarget)
        assertEquals(LaunchTarget.System(SystemSection.DATA_SOURCES), entry("data_sources").launchTarget)
        assertEquals(LaunchTarget.System(SystemSection.DISPLAY), entry("settings").launchTarget)
    }

    @Test fun defaultLayoutRejectsOverlap() {
        val first = DesktopPlacement(TileId("a"), LauncherEntryId("chart"), TileSize.MEDIUM_2X2, 0, 0)
        val second = DesktopPlacement(TileId("b"), LauncherEntryId("anchor"), TileSize.WIDE_2X1, 1, 1)
        assertFalse(DesktopLayoutValidator.isValid(DesktopLayout(4, listOf(first, second))))
    }

    private fun entry(id: String) = LauncherRegistry.entries.first { it.id.value == id }
}

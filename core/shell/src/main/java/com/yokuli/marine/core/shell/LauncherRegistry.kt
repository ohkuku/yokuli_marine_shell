package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.*

object LauncherRegistry {
    val entries: List<LauncherEntryDescriptor> = listOf(
        core("chart", "Chart", "⌖", MarineAppId.CHART, LaunchTarget.Chart(), TileSize.HERO_4X2, TileSize.MEDIUM_2X2, TileSize.WIDE_2X1),
        shortcut("anchor", "Anchor", "⚓︎", MarineAppId.CHART, LaunchTarget.Chart(ChartMode.ANCHOR), TileSize.MEDIUM_2X2, TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        core("cockpit", "Cockpit", "◒", MarineAppId.COCKPIT, LaunchTarget.Cockpit(), TileSize.MEDIUM_2X2, TileSize.WIDE_2X1),
        core("library", "Library", "▤", MarineAppId.LIBRARY, LaunchTarget.Library(), TileSize.WIDE_2X1, TileSize.MEDIUM_2X2, TileSize.SMALL_1X1),
        core("system", "System", "⚙︎", MarineAppId.SYSTEM, LaunchTarget.System(), TileSize.WIDE_2X1, TileSize.MEDIUM_2X2, TileSize.SMALL_1X1),
        shortcut("navigation", "Navigation", "➤", MarineAppId.CHART, LaunchTarget.Chart(ChartMode.NAVIGATE), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        shortcut("survey", "Survey", "≋", MarineAppId.CHART, LaunchTarget.Chart(ChartMode.SURVEY), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        shortcut("trips", "Trips", "↝", MarineAppId.LIBRARY, LaunchTarget.Library(LibrarySection.TRIPS), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        shortcut("anchorages", "Anchorages", "⌂", MarineAppId.LIBRARY, LaunchTarget.Library(LibrarySection.PLACES), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        shortcut("data_sources", "Data Sources", "◎", MarineAppId.SYSTEM, LaunchTarget.System(SystemSection.DATA_SOURCES), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        shortcut("nmea_input", "NMEA Input", "⇥", MarineAppId.SYSTEM, LaunchTarget.System(SystemSection.CONNECTIONS), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        shortcut("diagnostics", "Diagnostics", "!", MarineAppId.SYSTEM, LaunchTarget.System(SystemSection.STORAGE_DIAGNOSTICS), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
        shortcut("settings", "Settings", "⋯", MarineAppId.SYSTEM, LaunchTarget.System(SystemSection.DISPLAY), TileSize.WIDE_2X1, TileSize.SMALL_1X1),
    )

    val defaultLayout = DesktopLayout(
        columns = 4,
        placements = listOf(
            placement("chart", TileSize.HERO_4X2, 0, 0),
            placement("anchor", TileSize.MEDIUM_2X2, 0, 2),
            placement("cockpit", TileSize.MEDIUM_2X2, 2, 2),
            placement("library", TileSize.WIDE_2X1, 0, 4),
            placement("system", TileSize.WIDE_2X1, 2, 4),
        ),
    )

    fun entry(id: LauncherEntryId): LauncherEntryDescriptor? = entries.firstOrNull { it.id == id }

    private fun core(
        id: String,
        title: String,
        symbol: String,
        appId: MarineAppId,
        target: LaunchTarget,
        vararg sizes: TileSize,
    ) = LauncherEntryDescriptor(LauncherEntryId(id), title, symbol, LauncherEntryKind.CORE_APP, appId, target, sizes.toSet())

    private fun shortcut(
        id: String,
        title: String,
        symbol: String,
        appId: MarineAppId,
        target: LaunchTarget,
        vararg sizes: TileSize,
    ) = LauncherEntryDescriptor(LauncherEntryId(id), title, symbol, LauncherEntryKind.SHORTCUT, appId, target, sizes.toSet())

    private fun placement(id: String, size: TileSize, column: Int, row: Int) =
        DesktopPlacement(TileId("tile-$id"), LauncherEntryId(id), size, column, row)
}

package com.yokuli.marine.core.model

enum class MarineAppId { CHART, COCKPIT, LIBRARY, SYSTEM }
enum class ChartMode { BROWSE, NAVIGATE, ANCHOR, SURVEY }
enum class CockpitPage { OVERVIEW, SAILING, NAVIGATION, MOTION, WEATHER }
enum class LibrarySection { PLACES, ROUTES, TRIPS, ANCHORS, SURVEYS }
enum class SystemSection { OVERVIEW, CONNECTIONS, DATA_SOURCES, DEVICES, DISPLAY, SAFETY, STORAGE_DIAGNOSTICS }

@JvmInline value class LauncherEntryId(val value: String)
@JvmInline value class TileId(val value: String)
@JvmInline value class ShellTaskId(val value: String)

sealed interface LaunchTarget {
    data object Desktop : LaunchTarget
    data object AllApps : LaunchTarget
    data class Chart(val mode: ChartMode = ChartMode.BROWSE) : LaunchTarget
    data class Cockpit(val page: CockpitPage = CockpitPage.OVERVIEW) : LaunchTarget
    data class Library(val section: LibrarySection = LibrarySection.PLACES) : LaunchTarget
    data class System(val section: SystemSection = SystemSection.OVERVIEW) : LaunchTarget
}

enum class LauncherEntryKind { CORE_APP, SHORTCUT }

data class LauncherEntryDescriptor(
    val id: LauncherEntryId,
    val title: String,
    val symbol: String,
    val kind: LauncherEntryKind,
    val coreAppId: MarineAppId,
    val launchTarget: LaunchTarget,
    val supportedSizes: Set<TileSize>,
)

enum class TileSize(val columns: Int, val rows: Int) {
    SMALL_1X1(1, 1),
    WIDE_2X1(2, 1),
    MEDIUM_2X2(2, 2),
    HERO_4X2(4, 2),
}

data class DesktopPlacement(
    val tileId: TileId,
    val entryId: LauncherEntryId,
    val size: TileSize,
    val column: Int,
    val row: Int,
)

data class DesktopLayout(val columns: Int, val placements: List<DesktopPlacement>)

enum class DataFreshness { FRESH, HELD, STALE, OFF }

data class TileSnapshot(
    val entryId: LauncherEntryId,
    val headline: String,
    val detail: String? = null,
    val freshness: DataFreshness = DataFreshness.OFF,
)

sealed interface ShellSurface {
    data object Start : ShellSurface
    data object AllApps : ShellSurface
    data class App(val taskId: ShellTaskId) : ShellSurface
}

data class ShellTask(
    val id: ShellTaskId,
    val appId: MarineAppId,
    val target: LaunchTarget,
)

data class ShellNavigationState(
    val surface: ShellSurface = ShellSurface.Start,
    val tasks: List<ShellTask> = emptyList(),
)

sealed interface ShellCommand {
    data class Open(val target: LaunchTarget) : ShellCommand
    data object Home : ShellCommand
    data object Back : ShellCommand
    data object ShowAllApps : ShellCommand
}

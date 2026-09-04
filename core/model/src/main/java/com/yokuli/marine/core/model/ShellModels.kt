package com.yokuli.marine.core.model

/** Stable IDs keep the production registry open for completed feature contributions. */
@JvmInline value class MarineAppId(val value: String)
@JvmInline value class DestinationId(val value: String)
@JvmInline value class LauncherEntryId(val value: String)
@JvmInline value class TileId(val value: String)
@JvmInline value class ShellTaskId(val value: String)

/**
 * 中文：应用语言是跨模块状态，不属于任何页面或视觉主题。
 * English: App language is cross-module state, not page or visual-theme state.
 */
enum class AppLanguage(val languageTag: String) {
    CHINESE("zh-CN"),
    ENGLISH("en"),
}

data class LaunchTarget(
    val appId: MarineAppId,
    val destination: DestinationId,
)

data class MarineAppDescriptor(
    val id: MarineAppId,
    val rootDestination: DestinationId,
)

enum class TileSize(val columns: Int, val rows: Int) {
    SMALL_1X1(1, 1),
    MEDIUM_2X2(2, 2),
    WIDE_4X2(4, 2),
}

data class LauncherEntryDescriptor(
    val id: LauncherEntryId,
    val appId: MarineAppId,
    val launchTarget: LaunchTarget,
    val defaultSize: TileSize,
    val supportedSizesInCycleOrder: List<TileSize>,
)

interface ShellFeatureContribution {
    val app: MarineAppDescriptor
    val launcherEntries: List<LauncherEntryDescriptor>
}

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
    data object Back : ShellCommand
    data object ShowAllApps : ShellCommand
}

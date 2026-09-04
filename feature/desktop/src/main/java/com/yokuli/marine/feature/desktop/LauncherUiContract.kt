package com.yokuli.marine.feature.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.shell.LauncherRegistry
import com.yokuli.marine.core.shell.engine.layout.DesktopDocument

enum class MarineIconKind { CHART, SETTINGS, APPS, DONE, UNPIN, RESIZE, PIN, INFO, GENERIC }

data class LauncherEntryUiState(
    val descriptor: LauncherEntryDescriptor,
    val title: String,
    val chineseIndex: Char,
    val icon: MarineIconKind,
    val headline: String,
    val detail: String,
)

data class LauncherUiState(
    val document: DesktopDocument,
    val entries: List<LauncherEntryUiState>,
) {
    val pinnedEntries: Set<LauncherEntryId> = document.placements.map { it.entryId }.toSet()
}

sealed interface LauncherUiAction {
    data class Open(val target: LaunchTarget) : LauncherUiAction
    data object ShowAllApps : LauncherUiAction
    data class ChangeDocument(val document: DesktopDocument) : LauncherUiAction
    data class TogglePin(val entryId: LauncherEntryId) : LauncherUiAction
    data class ShowAppInfo(val entryId: LauncherEntryId) : LauncherUiAction
}

/**
 * 中文：这里只把真实配置翻译成 UI 状态，不制造任何船舶事实。
 * English: This maps real configuration to UI state and never invents vessel facts.
 */
@Composable
fun productionLauncherUiState(
    registry: LauncherRegistry,
    document: DesktopDocument,
    mapConfigured: Boolean,
    theme: WpThemeSpec,
): LauncherUiState {
    val chart = registry.entries.single { it.id.value == "chart" }
    val settings = registry.entries.single { it.id.value == "settings" }
    return LauncherUiState(
        document = document,
        entries = listOf(
            LauncherEntryUiState(
                descriptor = chart,
                title = stringResource(R.string.launcher_chart),
                chineseIndex = 'H',
                icon = MarineIconKind.CHART,
                headline = stringResource(if (mapConfigured) R.string.tile_chart_ready else R.string.tile_chart_demo),
                detail = stringResource(if (mapConfigured) R.string.tile_chart_browse else R.string.tile_chart_unconfigured),
            ),
            LauncherEntryUiState(
                descriptor = settings,
                title = stringResource(R.string.launcher_settings),
                chineseIndex = 'S',
                icon = MarineIconKind.SETTINGS,
                headline = stringResource(
                    if (theme.mode == WpThemeMode.DARK) R.string.tile_settings_dark else R.string.tile_settings_light,
                ),
                detail = stringResource(R.string.tile_settings_accent, theme.accent.displayName),
            ),
        ),
    )
}

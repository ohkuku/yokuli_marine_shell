package com.yokuli.marine.feature.desktop

import androidx.compose.runtime.Composable
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.engine.layout.DesktopDocument

enum class MarineIconKind { CHART, SETTINGS, APPS, DONE, UNPIN, RESIZE, PIN, INFO, GENERIC }

data class LauncherEntryUiState(
    val descriptor: LauncherEntryDescriptor,
    val title: String,
    val chineseIndex: Char,
    val icon: MarineIconKind,
    val headline: String,
    val detail: String,
)

/**
 * 中文：视觉贡献属于 composition root；Engine 和目录都不认识产品图标或文案。
 * English: Visual contributions belong to the composition root; neither Engine nor catalog knows product copy.
 */
data class LauncherEntryVisualContribution(
    val entryId: LauncherEntryId,
    val createState: @Composable (LauncherEntryDescriptor, LauncherVisualContext) -> LauncherEntryUiState,
)

data class LauncherVisualContext(
    val mapConfigured: Boolean,
    val theme: WpThemeSpec,
)

data class LauncherUiState(
    val document: DesktopDocument,
    val entries: List<LauncherEntryUiState>,
) {
    val pinnedEntries: Set<LauncherEntryId> = document.placements.map { it.entryId }.toSet()
}

sealed interface LauncherUiAction {
    data class Open(val token: LaunchToken) : LauncherUiAction
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
    catalog: LauncherCatalogSnapshot,
    document: DesktopDocument,
    mapConfigured: Boolean,
    theme: WpThemeSpec,
    visualContributions: List<LauncherEntryVisualContribution>,
): LauncherUiState {
    val visualsByEntry = visualContributions.associateBy { it.entryId }
    require(visualsByEntry.size == visualContributions.size) { "Duplicate launcher visual contribution" }
    require(visualsByEntry.keys == catalog.entries.map { it.entryId }.toSet()) {
        "Launcher visual contributions must exactly match the runtime catalog"
    }
    val visualContext = LauncherVisualContext(mapConfigured = mapConfigured, theme = theme)
    return LauncherUiState(
        document = document,
        entries = catalog.entries.map { descriptor ->
            requireNotNull(visualsByEntry[descriptor.entryId]) {
                "Missing launcher visual contribution for ${descriptor.entryId.value}"
            }.createState(descriptor, visualContext)
        },
    )
}

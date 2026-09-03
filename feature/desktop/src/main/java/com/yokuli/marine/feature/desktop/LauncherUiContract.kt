package com.yokuli.marine.feature.desktop

import androidx.annotation.StringRes
import com.yokuli.marine.core.model.DesktopLayout
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.shell.LauncherRegistry

enum class LauncherTileTone { SAFE, WARNING, ALARM, STALE }

data class LauncherTileUiState(
    @StringRes val headlineRes: Int,
    @StringRes val detailRes: Int,
    val tone: LauncherTileTone? = null,
)

data class LauncherUiState(
    val layout: DesktopLayout,
    val tiles: Map<LauncherEntryId, LauncherTileUiState>,
    val pinnedEntries: Set<LauncherEntryId> = layout.placements.map { it.entryId }.toSet(),
)

sealed interface LauncherUiAction {
    data class Open(val target: LaunchTarget) : LauncherUiAction
    data object ShowAllApps : LauncherUiAction
    data class ChangeLayout(val layout: DesktopLayout) : LauncherUiAction
    data class TogglePin(val entryId: LauncherEntryId) : LauncherUiAction
}

data class LauncherEntryVisual(
    @StringRes val titleRes: Int,
    val glyph: String,
    val chineseIndex: Char,
)

object LauncherVisualCatalog {
    private val entries = mapOf(
        "chart" to LauncherEntryVisual(R.string.launcher_chart, "⌖", 'H'),
        "anchor" to LauncherEntryVisual(R.string.launcher_anchor, "⚓︎", 'M'),
        "cockpit" to LauncherEntryVisual(R.string.launcher_cockpit, "◒", 'Y'),
        "library" to LauncherEntryVisual(R.string.launcher_library, "▤", 'Z'),
        "system" to LauncherEntryVisual(R.string.launcher_system, "⚙︎", 'X'),
        "navigation" to LauncherEntryVisual(R.string.launcher_navigation, "➤", 'D'),
        "survey" to LauncherEntryVisual(R.string.launcher_survey, "≋", 'C'),
        "trips" to LauncherEntryVisual(R.string.launcher_trips, "↝", 'H'),
        "anchorages" to LauncherEntryVisual(R.string.launcher_anchorages, "⌂", 'M'),
        "data_sources" to LauncherEntryVisual(R.string.launcher_data_sources, "◎", 'S'),
        "nmea_input" to LauncherEntryVisual(R.string.launcher_nmea_input, "⇥", 'N'),
        "diagnostics" to LauncherEntryVisual(R.string.launcher_diagnostics, "!", 'Z'),
        "settings" to LauncherEntryVisual(R.string.launcher_settings, "⋯", 'S'),
    )

    fun get(entryId: LauncherEntryId): LauncherEntryVisual = entries.getValue(entryId.value)
}

/**
 * 中文：仅供尚未接入运行时的 UI 样例；不得把这些值解释为真实船舶数据。
 * English: UI-only fixtures; these values never represent connected vessel data.
 */
object LauncherUiFixtures {
    fun state(layout: DesktopLayout = LauncherRegistry.defaultLayout): LauncherUiState = LauncherUiState(
        layout = layout,
        tiles = mapOf(
            tile("chart", R.string.tile_chart_headline, R.string.tile_chart_detail),
            tile("anchor", R.string.tile_anchor_headline, R.string.tile_anchor_detail, LauncherTileTone.SAFE),
            tile("cockpit", R.string.tile_cockpit_headline, R.string.tile_cockpit_detail),
            tile("library", R.string.tile_library_headline, R.string.tile_library_detail),
            tile("system", R.string.tile_system_headline, R.string.tile_system_detail, LauncherTileTone.STALE),
            tile("navigation", R.string.tile_navigation_headline, R.string.tile_navigation_detail),
            tile("survey", R.string.tile_survey_headline, R.string.tile_survey_detail, LauncherTileTone.STALE),
            tile("trips", R.string.tile_trips_headline, R.string.tile_trips_detail),
            tile("anchorages", R.string.tile_anchorages_headline, R.string.tile_anchorages_detail),
            tile("data_sources", R.string.tile_data_sources_headline, R.string.tile_data_sources_detail, LauncherTileTone.SAFE),
            tile("nmea_input", R.string.tile_nmea_headline, R.string.tile_nmea_detail, LauncherTileTone.STALE),
            tile("diagnostics", R.string.tile_diagnostics_headline, R.string.tile_diagnostics_detail, LauncherTileTone.SAFE),
            tile("settings", R.string.tile_settings_headline, R.string.tile_settings_detail),
        ),
    )

    private fun tile(id: String, @StringRes headline: Int, @StringRes detail: Int, tone: LauncherTileTone? = null) =
        LauncherEntryId(id) to LauncherTileUiState(headline, detail, tone)
}

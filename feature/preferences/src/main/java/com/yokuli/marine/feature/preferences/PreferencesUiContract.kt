package com.yokuli.marine.feature.preferences

import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.shell.contract.AppPreferenceKey
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.MeasurementUnitSystem
import com.yokuli.shell.contract.MotionPreference

enum class PreferencesSection { OVERVIEW, APPEARANCE, LANGUAGE, UNITS, MOTION, START, APP_TILES, ABOUT }

data class AppTilePreferenceUi(
    val appId: LauncherAppId,
    val supportedSizes: Set<MarineTileSize>,
    val preferenceKeys: List<AppPreferenceKey> = emptyList(),
)

data class PreferencesUiState(
    val section: PreferencesSection = PreferencesSection.OVERVIEW,
    val theme: WpThemeSpec,
    val language: AppLanguage,
    val measurementUnits: MeasurementUnitSystem,
    val motionPreference: MotionPreference,
    val pinnedTileCount: Int,
    val startDocumentVersion: Int,
    val appTiles: List<AppTilePreferenceUi>,
    val versionName: String,
    val buildVariant: String,
    val gitSha: String,
)

sealed interface PreferencesUiAction {
    data class OpenSection(val section: PreferencesSection) : PreferencesUiAction
    data class ChangeTheme(val theme: WpThemeSpec) : PreferencesUiAction
    data class ChangeLanguage(val language: AppLanguage) : PreferencesUiAction
    data class ChangeUnits(val units: MeasurementUnitSystem) : PreferencesUiAction
    data class ChangeMotion(val preference: MotionPreference) : PreferencesUiAction
    data object ResetStartScreen : PreferencesUiAction
}

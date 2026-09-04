package com.yokuli.marine.feature.settings

import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.AppLanguage

enum class SettingsSection { OVERVIEW, APPEARANCE, START_SCREEN, MAP, LANGUAGE, ABOUT }

data class SettingsUiState(
    val section: SettingsSection,
    val theme: WpThemeSpec,
    val language: AppLanguage,
    val mapConfigured: Boolean,
    val pinnedTileCount: Int,
    val startDocumentVersion: Int,
    val versionName: String,
    val buildVariant: String,
    val gitSha: String,
    val debugShellLabAvailable: Boolean,
)

sealed interface SettingsUiAction {
    data class OpenSection(val section: SettingsSection) : SettingsUiAction
    data class ChangeTheme(val theme: WpThemeSpec) : SettingsUiAction
    data class ChangeLanguage(val language: AppLanguage) : SettingsUiAction
    data object ResetStartScreen : SettingsUiAction
    data object OpenAndroidSettings : SettingsUiAction
    data object OpenShellLab : SettingsUiAction
}

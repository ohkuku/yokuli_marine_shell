package com.yokuli.marine.feature.system

import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.core.model.SystemSection

data class SystemUiState(
    val section: SystemSection,
    val theme: WpThemeSpec,
    val language: AppLanguage,
    val nmeaConnected: Boolean,
    val positionSourcePhone: Boolean,
    val availableDeviceCount: Int,
    val safetyReady: Boolean,
    val criticalIssueCount: Int,
)

sealed interface SystemUiAction {
    data class OpenSection(val section: SystemSection) : SystemUiAction
    data class ChangeTheme(val theme: WpThemeSpec) : SystemUiAction
    data class ChangeLanguage(val language: AppLanguage) : SystemUiAction
    data object Home : SystemUiAction
}

/** 中文：未接入系统服务的 UI 样例。 English: UI fixture before system services are connected. */
object SystemUiFixtures {
    fun state(section: SystemSection, theme: WpThemeSpec, language: AppLanguage) = SystemUiState(
        section = section,
        theme = theme,
        language = language,
        nmeaConnected = false,
        positionSourcePhone = true,
        availableDeviceCount = 2,
        safetyReady = true,
        criticalIssueCount = 0,
    )
}

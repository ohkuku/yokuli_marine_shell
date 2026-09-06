package com.yokuli.marine.core.model

/**
 * 中文：应用语言是跨模块状态，不属于任何页面或视觉主题。
 * English: App language is cross-module state, not page or visual-theme state.
 */
enum class AppLanguage(val languageTag: String) {
    CHINESE("zh-CN"),
    ENGLISH("en"),
}

fun supportedAppLanguage(languageTag: String): AppLanguage? {
    val normalized = languageTag.trim().lowercase()
    return when {
        normalized == "zh" || normalized.startsWith("zh-") -> AppLanguage.CHINESE
        normalized == "en" || normalized.startsWith("en-") -> AppLanguage.ENGLISH
        else -> null
    }
}

/** First run follows the first supported device language; unsupported devices start in English. */
fun initialAppLanguage(deviceLanguageTags: List<String>): AppLanguage =
    deviceLanguageTags.firstNotNullOfOrNull(::supportedAppLanguage) ?: AppLanguage.ENGLISH

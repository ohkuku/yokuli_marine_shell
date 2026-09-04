package com.yokuli.marine.core.model

/**
 * 中文：应用语言是跨模块状态，不属于任何页面或视觉主题。
 * English: App language is cross-module state, not page or visual-theme state.
 */
enum class AppLanguage(val languageTag: String) {
    CHINESE("zh-CN"),
    ENGLISH("en"),
}

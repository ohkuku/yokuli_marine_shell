package com.yokuli.marine.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun supportedLanguagesUseStableBcp47Tags() {
        assertEquals(
            mapOf(AppLanguage.CHINESE to "zh-CN", AppLanguage.ENGLISH to "en"),
            AppLanguage.entries.associateWith { it.languageTag },
        )
    }

    @Test
    fun firstRunFollowsSupportedDeviceLanguageAndOtherwiseUsesEnglish() {
        assertEquals(AppLanguage.CHINESE, initialAppLanguage(listOf("zh-Hant-NZ", "en-NZ")))
        assertEquals(AppLanguage.ENGLISH, initialAppLanguage(listOf("en-NZ", "zh-CN")))
        assertEquals(AppLanguage.ENGLISH, initialAppLanguage(listOf("mi-NZ", "fr-FR")))
        assertEquals(AppLanguage.ENGLISH, initialAppLanguage(emptyList()))
    }

    @Test
    fun persistedRegionalTagsResolveToTheSingleSupportedLanguageTruth() {
        assertEquals(AppLanguage.CHINESE, supportedAppLanguage("zh-CN"))
        assertEquals(AppLanguage.ENGLISH, supportedAppLanguage("en-US"))
        assertEquals(null, supportedAppLanguage("de-DE"))
    }
}

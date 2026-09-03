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
}

package com.yokuli.marine.feature.preferences

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.MarineTileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesContractTest {
    @Test
    fun `all current sections have stable tokens and legacy Settings links remain readable`() {
        PreferencesSection.entries.forEach { section ->
            assertEquals(section, PreferencesDestinations.section(PreferencesDestinations.token(section)))
        }
        assertEquals(PreferencesSection.APPEARANCE, PreferencesDestinations.section(LaunchToken("settings.appearance")))
        assertEquals(PreferencesSection.START, PreferencesDestinations.section(LaunchToken("settings.start")))
        assertEquals(PreferencesSection.OVERVIEW, PreferencesDestinations.section(LaunchToken("settings.map")))
        assertNull(PreferencesDestinations.section(LaunchToken("preferences.unknown")))
    }

    @Test
    fun `Preferences is one static app with only supported WP tile sizes`() {
        val entry = PreferencesShellContribution.entries.single()
        assertEquals(PreferencesDestinations.AppId, entry.appId)
        assertEquals(
            listOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2),
            entry.supportedSizes,
        )
    }
}

package com.yokuli.marine.shell

import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.preferences.PreferencesDestinations
import com.yokuli.marine.feature.preferences.PreferencesSection
import com.yokuli.marine.feature.data.DataDestinations
import com.yokuli.marine.feature.chartlibrary.ChartLibraryDestinations
import com.yokuli.marine.feature.navigation.NavigationDestinations
import com.yokuli.marine.feature.navigation.NavigationShellContribution
import com.yokuli.shell.contract.LaunchToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProductionLaunchRegistryTest {
    @Test fun everyPreferencesSubpageIsRoutableThroughTheOneInstalledBinding() {
        PreferencesSection.entries.forEach { section ->
            val token = PreferencesDestinations.token(section)
            assertEquals("Missing Preferences route: ${token.value}", PreferencesDestinations.AppId, productionLaunchRegistrations[token])
            assertEquals(section, PreferencesDestinations.section(token))
        }
        assertEquals(ChartDestinations.AppId, productionLaunchRegistrations[ChartDestinations.Browse])
    }

    @Test fun internalRoutesDoNotBecomeAdditionalLauncherEntries() {
        assertEquals(
            setOf(
                ChartDestinations.EntryId,
                PreferencesDestinations.EntryId,
                DataDestinations.EntryId,
                ChartLibraryDestinations.EntryId,
                NavigationShellContribution.EntryId,
            ),
            productionContributions.flatMap { it.entries }.map { it.entryId }.toSet(),
        )
        assertEquals(5, productionContributions.flatMap { it.entries }.size)
        assertEquals(5, productionInstalledAppRegistry.internalAppHosts.size)
        assertEquals(
            NavigationShellContribution.AppId,
            productionLaunchRegistrations[NavigationDestinations.Routes],
        )
        assertFalse(productionLaunchRegistrations.containsKey(LaunchToken("settings.unknown")))
    }
}

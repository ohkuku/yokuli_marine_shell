package com.yokuli.marine.shell

import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsSection
import com.yokuli.marine.feature.data.DataDestinations
import com.yokuli.marine.feature.chartlibrary.ChartLibraryDestinations
import com.yokuli.marine.feature.navigation.NavigationDestinations
import com.yokuli.marine.feature.navigation.NavigationShellContribution
import com.yokuli.shell.contract.LaunchToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProductionLaunchRegistryTest {
    @Test fun everySettingsSubpageIsRoutableThroughTheOneInstalledBinding() {
        SettingsSection.entries.forEach { section ->
            val token = SettingsDestinations.token(section)
            assertEquals("Missing Settings route: ${token.value}", SettingsDestinations.AppId, productionLaunchRegistrations[token])
            assertEquals(section, SettingsDestinations.section(token))
        }
        assertEquals(ChartDestinations.AppId, productionLaunchRegistrations[ChartDestinations.Browse])
    }

    @Test fun internalRoutesDoNotBecomeAdditionalLauncherEntries() {
        assertEquals(
            setOf(
                ChartDestinations.EntryId,
                SettingsDestinations.EntryId,
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

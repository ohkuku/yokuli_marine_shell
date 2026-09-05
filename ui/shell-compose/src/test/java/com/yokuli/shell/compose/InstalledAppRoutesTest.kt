package com.yokuli.shell.compose

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class InstalledAppRoutesTest {
    @Test fun internalDestinationsResolveWithoutAddingLauncherEntriesOrHosts() {
        val app = binding("settings", internalTokens = listOf("settings.appearance", "settings.language"))
        val registry = InstalledAppRegistry(listOf(app))
        val expected = listOf("settings.root", "settings.appearance", "settings.language")
            .associate { LaunchToken(it) to LauncherAppId("settings") }
        assertEquals(expected, registry.launchRegistrations)
        assertEquals(1, registry.catalogContributions.sumOf { it.entries.size })
        assertEquals(1, registry.internalAppHosts.size)
        assertFalse(registry.launchRegistrations.containsKey(LaunchToken("settings.unknown")))
    }

    @Test fun aPublicEntryCannotBeRepeatedAsAnInternalDestination() {
        assertThrows(IllegalArgumentException::class.java) {
            binding("settings", internalTokens = listOf("settings.root"))
        }
    }

    @Test fun duplicateInternalDestinationsFailBeforeMapConversion() {
        assertThrows(IllegalArgumentException::class.java) {
            binding("settings", internalTokens = listOf("settings.language", "settings.language"))
        }
    }

    @Test fun duplicateEntryTokensWithinOneAppCannotBeSilentlyOverwritten() {
        assertThrows(IllegalArgumentException::class.java) {
            binding("settings", entryTokens = listOf("settings.root", "settings.root"))
        }
    }

    @Test fun anotherAppsEntryCannotBeCapturedByAnInternalDestination() {
        assertThrows(IllegalArgumentException::class.java) {
            InstalledAppRegistry(listOf(binding("settings", internalTokens = listOf("map.root")), binding("map")))
        }
    }

    @Test fun internalDestinationsCannotCollideAcrossApps() {
        assertThrows(IllegalArgumentException::class.java) {
            InstalledAppRegistry(listOf(
                binding("settings", internalTokens = listOf("shared.route")),
                binding("map", internalTokens = listOf("shared.route")),
            ))
        }
    }

    private fun binding(
        id: String,
        entryTokens: List<String> = listOf("$id.root"),
        internalTokens: List<String> = emptyList(),
    ): InstalledAppBinding<Unit> {
        val appId = LauncherAppId(id)
        val rootId = LauncherEntryId("$id-0")
        val contribution = object : LauncherCatalogContribution {
            override val app = LauncherAppDescriptor(appId, rootId)
            override val entries = entryTokens.mapIndexed { index, token ->
                LauncherEntryDescriptor(
                    LauncherEntryId("$id-$index"), appId, LaunchToken(token),
                    MarineTileSize.ICON_1X1, listOf(MarineTileSize.ICON_1X1), PinPolicy.PINNABLE,
                )
            }
            override val internalLaunchTokens = internalTokens.map(::LaunchToken)
        }
        return InstalledAppBinding(contribution, { emptyList() }, InternalAppHost(appId) { })
    }
}

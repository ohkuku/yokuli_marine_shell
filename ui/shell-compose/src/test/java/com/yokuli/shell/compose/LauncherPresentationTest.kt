package com.yokuli.shell.compose

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPresentationTest {
    private val appId = LauncherAppId("map")
    private val entryId = LauncherEntryId("map")
    private val sizes = listOf(MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2)
    private val descriptor = LauncherEntryDescriptor(
        entryId = entryId,
        appId = appId,
        launchToken = LaunchToken("map.root"),
        defaultSize = MarineTileSize.WIDE_4X2,
        supportedSizes = sizes,
        pinPolicy = PinPolicy.PINNABLE,
    )
    private val catalog = LauncherCatalogSnapshot(
        revision = 1,
        apps = listOf(LauncherAppDescriptor(appId, entryId)),
        entries = listOf(descriptor),
    )

    @Test
    fun appMustProvideOneExplicitRendererForEverySupportedSize() {
        LauncherPresentationValidator.validate(catalog, listOf(visual(sizes)))
    }

    @Test
    fun missingSizeRendererIsRejectedInsteadOfScaledByShell() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherPresentationValidator.validate(
                catalog,
                listOf(visual(listOf(MarineTileSize.WIDE_4X2))),
            )
        }
    }

    @Test
    fun undeclaredSizeRendererIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherPresentationValidator.validate(
                catalog,
                listOf(visual(sizes + MarineTileSize.LARGE_4X4)),
            )
        }
    }

    @Test
    fun oneBindingDerivesCatalogTokenAndHostOwnership() {
        val contribution = contribution(descriptor)
        val host = InternalAppHost(appId) { }
        val binding = InstalledAppBinding<Unit>(
            catalogContribution = contribution,
            visualContributions = { listOf(visual(sizes)) },
            internalAppHost = host,
        )
        val registry = InstalledAppRegistry(listOf(binding))

        assertEquals(listOf(contribution), registry.catalogContributions)
        assertEquals(mapOf(LaunchToken("map.root") to appId), registry.launchRegistrations)
        assertEquals(listOf(host), registry.internalAppHosts)
    }

    @Test
    fun hostFromAnotherAppCannotBeSilentlyBound() {
        assertThrows(IllegalArgumentException::class.java) {
            InstalledAppBinding<Unit>(
                catalogContribution = contribution(descriptor),
                visualContributions = { listOf(visual(sizes)) },
                internalAppHost = InternalAppHost(LauncherAppId("other")) { },
            )
        }
    }

    private fun contribution(entry: LauncherEntryDescriptor) = object :
        com.yokuli.shell.contract.LauncherCatalogContribution {
        override val app = LauncherAppDescriptor(appId, entryId)
        override val entries = listOf(entry)
    }

    private fun visual(rendererSizes: List<MarineTileSize>) = LauncherEntryVisualContribution(
        entryId = entryId,
        title = "map",
        chineseIndex = 'M',
        headline = "offline",
        detail = "browse",
        icon = LauncherIconRenderer { _, _ -> },
        tileRenderers = rendererSizes.associateWith { LauncherTileRenderer { } },
    )
}

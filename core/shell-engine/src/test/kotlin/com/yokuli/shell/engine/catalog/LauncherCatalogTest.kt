package com.yokuli.shell.engine.catalog

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.WpTileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherCatalogTest {
    @Test
    fun catalogIsComposedOnlyFromContributions() {
        val catalog = LauncherCatalog.compose(7, listOf(contribution("first"), contribution("second")))

        assertEquals(7, catalog.snapshot.revision)
        assertEquals(listOf("first", "second"), catalog.apps.map { it.appId.value })
        assertEquals(listOf("first", "second"), catalog.entries.map { it.entryId.value })
    }

    @Test
    fun duplicateOpaqueIdsAreRejectedAtCompositionBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherCatalog.compose(1, listOf(contribution("same"), contribution("same")))
        }
    }

    private fun contribution(id: String): LauncherCatalogContribution {
        val appId = LauncherAppId(id)
        val entryId = LauncherEntryId(id)
        return object : LauncherCatalogContribution {
            override val app = LauncherAppDescriptor(appId, entryId)
            override val entries = listOf(
                LauncherEntryDescriptor(
                    entryId = entryId,
                    appId = appId,
                    launchToken = LaunchToken("$id.root"),
                    defaultSize = WpTileSize.SMALL_1X1,
                    supportedSizes = listOf(WpTileSize.SMALL_1X1),
                    pinPolicy = PinPolicy.PINNABLE,
                ),
            )
        }
    }
}

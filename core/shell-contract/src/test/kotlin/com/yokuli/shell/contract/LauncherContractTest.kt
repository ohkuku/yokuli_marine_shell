package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherContractTest {
    @Test
    fun opaqueIdentifiersRejectBlankValues() {
        assertThrows(IllegalArgumentException::class.java) { LauncherAppId(" ") }
        assertThrows(IllegalArgumentException::class.java) { LauncherEntryId("") }
        assertThrows(IllegalArgumentException::class.java) { LaunchToken("\t") }
        assertThrows(IllegalArgumentException::class.java) { TileInstanceId("\n") }
    }

    @Test
    fun catalogDescriptorsContainNoVisualOrPlatformPayload() {
        val appId = LauncherAppId("sample")
        val entryId = LauncherEntryId("sample.root")
        val descriptor = LauncherEntryDescriptor(
            entryId = entryId,
            appId = appId,
            launchToken = LaunchToken("sample.open"),
            defaultSize = MarineTileSize.ICON_1X1,
            supportedSizes = listOf(MarineTileSize.ICON_1X1),
            pinPolicy = PinPolicy.PINNABLE,
        )
        val snapshot = LauncherCatalogSnapshot(
            revision = 1,
            apps = listOf(LauncherAppDescriptor(appId, entryId)),
            entries = listOf(descriptor),
        )

        assertEquals(entryId, snapshot.apps.single().rootEntryId)
        assertEquals(LaunchToken("sample.open"), snapshot.entries.single().launchToken)
    }
}

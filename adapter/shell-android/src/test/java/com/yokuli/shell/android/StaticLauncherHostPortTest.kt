package com.yokuli.shell.android

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.MarineTileSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StaticLauncherHostPortTest {
    @Test
    fun resolvesOnlyExplicitOpaqueTokens() = runBlocking {
        val appId = LauncherAppId("chart")
        val entryId = LauncherEntryId("chart")
        val token = LaunchToken("chart.browse")
        val catalog = LauncherCatalogSnapshot(
            revision = 1,
            apps = listOf(LauncherAppDescriptor(appId, entryId)),
            entries = listOf(
                LauncherEntryDescriptor(
                    entryId = entryId,
                    appId = appId,
                    launchToken = token,
                    defaultSize = MarineTileSize.WIDE_4X2,
                    supportedSizes = listOf(MarineTileSize.WIDE_4X2),
                    pinPolicy = PinPolicy.PINNABLE,
                ),
            ),
        )
        val port = StaticLauncherHostPort(
            catalog,
            mapOf(token to appId),
        )

        assertEquals(LaunchResolution.Internal(appId, token), port.resolveLaunch(token))
        val unknown = LaunchToken("unknown")
        assertEquals(LaunchResolution.Unresolved(unknown), port.resolveLaunch(unknown))
    }

    @Test
    fun rejectsACatalogEntryWithoutAHostMapping() {
        val appId = LauncherAppId("chart")
        val entryId = LauncherEntryId("chart")
        val token = LaunchToken("chart.browse")
        val catalog = LauncherCatalogSnapshot(
            revision = 1,
            apps = listOf(LauncherAppDescriptor(appId, entryId)),
            entries = listOf(
                LauncherEntryDescriptor(
                    entryId = entryId,
                    appId = appId,
                    launchToken = token,
                    defaultSize = MarineTileSize.WIDE_4X2,
                    supportedSizes = listOf(MarineTileSize.WIDE_4X2),
                    pinPolicy = PinPolicy.PINNABLE,
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) { StaticLauncherHostPort(catalog, emptyMap()) }
    }

    @Test
    fun dynamicTokensResolveOnlyWhenExactlyOneInstalledAppClaimsThem() = runBlocking {
        val chart = LauncherAppId("chart")
        val entry = LauncherEntryId("chart")
        val browse = LaunchToken("chart.browse")
        val catalog = LauncherCatalogSnapshot(
            1,
            listOf(LauncherAppDescriptor(chart, entry)),
            listOf(LauncherEntryDescriptor(entry, chart, browse, MarineTileSize.WIDE_4X2, listOf(MarineTileSize.WIDE_4X2), PinPolicy.PINNABLE)),
        )
        val route = LaunchToken("chart.route.abcd")
        val port = StaticLauncherHostPort(catalog, mapOf(browse to chart), listOf(chart to { it.value.startsWith("chart.route.") }))
        assertEquals(LaunchResolution.Internal(chart, route), port.resolveLaunch(route))

        val ambiguous = StaticLauncherHostPort(
            catalog,
            mapOf(browse to chart),
            listOf(chart to { true }, chart to { true }),
        )
        assertEquals(LaunchResolution.Unresolved(route), ambiguous.resolveLaunch(route))

        val throwing = StaticLauncherHostPort(catalog, mapOf(browse to chart), listOf(chart to { error("bad matcher") }))
        assertEquals(LaunchResolution.Unresolved(route), throwing.resolveLaunch(route))
    }
}

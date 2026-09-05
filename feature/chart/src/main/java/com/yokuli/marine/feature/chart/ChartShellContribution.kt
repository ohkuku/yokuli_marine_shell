package com.yokuli.marine.feature.chart

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TilePresentationKind

object ChartDestinations {
    val AppId = LauncherAppId("chart")
    val Browse = LaunchToken("chart.browse")
    val EntryId = LauncherEntryId("chart")

    fun place(id: String): LaunchToken = requireNotNull(placeOrNull(id)) { "Place ID is too large for a launch token" }
    fun route(id: String): LaunchToken = requireNotNull(routeOrNull(id)) { "Route ID is too large for a launch token" }
    fun placeOrNull(id: String): LaunchToken? = objectToken(PLACE_PREFIX, id)
    fun routeOrNull(id: String): LaunchToken? = objectToken(ROUTE_PREFIX, id)

    fun accepts(token: LaunchToken): Boolean = parse(token) != null

    fun parse(token: LaunchToken): ChartLaunchTarget? = when {
        token == Browse -> ChartLaunchTarget.Browse
        token.value.startsWith(PLACE_PREFIX) -> decodeId(token.value.removePrefix(PLACE_PREFIX))?.let(ChartLaunchTarget::Place)
        token.value.startsWith(ROUTE_PREFIX) -> decodeId(token.value.removePrefix(ROUTE_PREFIX))?.let(ChartLaunchTarget::Route)
        else -> null
    }

    private fun objectToken(prefix: String, id: String): LaunchToken? {
        val bytes = id.toByteArray(Charsets.UTF_8)
        if (id.isBlank() || bytes.size > MAX_OBJECT_ID_BYTES) return null
        return LaunchToken(prefix + bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) })
    }

    private fun decodeId(hex: String): String? {
        if (hex.isEmpty() || hex.length % 2 != 0 || hex.length > MAX_OBJECT_ID_BYTES * 2 ||
            !hex.matches(Regex("[0-9a-f]+"))
        ) return null
        return runCatching {
            val bytes = ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
            bytes.toString(Charsets.UTF_8).takeIf {
                it.isNotBlank() && it.toByteArray(Charsets.UTF_8).contentEquals(bytes)
            }
        }.getOrNull()
    }

    private const val PLACE_PREFIX = "chart.place."
    private const val ROUTE_PREFIX = "chart.route."
    private const val MAX_OBJECT_ID_BYTES = 512
}

object ChartShellContribution : LauncherCatalogContribution {
    override val app = LauncherAppDescriptor(ChartDestinations.AppId, ChartDestinations.EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            entryId = ChartDestinations.EntryId,
            appId = ChartDestinations.AppId,
            launchToken = ChartDestinations.Browse,
            defaultSize = MarineTileSize.WIDE_4X2,
            supportedSizes = listOf(
                MarineTileSize.ICON_1X1,
                MarineTileSize.STANDARD_2X2,
                MarineTileSize.WIDE_4X2,
            ),
            pinPolicy = PinPolicy.PINNABLE,
            presentationKind = TilePresentationKind.STATUS,
        ),
    )
}

package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TilePresentationKind
import com.yokuli.shell.contract.AppPreferenceContribution
import com.yokuli.shell.contract.AppPreferenceDefinition
import com.yokuli.shell.contract.AppPreferenceKey
import com.yokuli.shell.contract.AppPreferenceLabel
import com.yokuli.shell.contract.AppPreferenceValue

enum class ChartTileMode { AUTO, MAP, NAVIGATION, POSITION, STATIC }

val ChartTileModePreferenceKey = AppPreferenceKey("chart.tile.mode")

object ChartDestinations {
    val AppId = LauncherAppId("chart")
    val Browse = LaunchToken("chart.browse")
    val EntryId = LauncherEntryId("chart")

    fun place(id: String): LaunchToken = requireNotNull(placeOrNull(id)) { "Place ID is too large for a launch token" }
    fun route(id: String): LaunchToken = requireNotNull(routeOrNull(id)) { "Route ID is too large for a launch token" }
    fun chartAsset(id: ChartAssetId): LaunchToken =
        requireNotNull(objectToken(CHART_ASSET_PREFIX, id.value)) { "Chart asset ID is too large for a launch token" }
    fun placeOrNull(id: String): LaunchToken? = objectToken(PLACE_PREFIX, id)
    fun routeOrNull(id: String): LaunchToken? = objectToken(ROUTE_PREFIX, id)

    fun accepts(token: LaunchToken): Boolean = parse(token) != null

    fun parse(token: LaunchToken): ChartDestination? = when {
        token == Browse -> ChartDestination.Browse
        token.value.startsWith(PLACE_PREFIX) -> decodeId(token.value.removePrefix(PLACE_PREFIX))?.let(ChartDestination::Place)
        token.value.startsWith(ROUTE_PREFIX) -> decodeId(token.value.removePrefix(ROUTE_PREFIX))?.let(ChartDestination::Route)
        token.value.startsWith(CHART_ASSET_PREFIX) -> decodeId(token.value.removePrefix(CHART_ASSET_PREFIX))
            ?.let { runCatching { ChartAssetId(it) }.getOrNull() }
            ?.let(ChartDestination::ChartAsset)
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
    private const val CHART_ASSET_PREFIX = "chart.library_asset."
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
            presentationKind = TilePresentationKind.CYCLE,
        ),
    )
    override val appPreferences = AppPreferenceContribution(
        appId = ChartDestinations.AppId,
        definitions = listOf(
            AppPreferenceDefinition.Choice(
                key = ChartTileModePreferenceKey,
                options = ChartTileMode.entries.map { it.name },
                defaultValue = AppPreferenceValue.Choice(ChartTileMode.AUTO.name),
                label = AppPreferenceLabel("海图磁贴内容", "Chart tile content"),
                optionLabels = mapOf(
                    ChartTileMode.AUTO.name to AppPreferenceLabel("自动", "Automatic"),
                    ChartTileMode.MAP.name to AppPreferenceLabel("地图快照", "Map snapshot"),
                    ChartTileMode.NAVIGATION.name to AppPreferenceLabel("当前导航", "Active navigation"),
                    ChartTileMode.POSITION.name to AppPreferenceLabel("当前位置", "Current position"),
                    ChartTileMode.STATIC.name to AppPreferenceLabel("静态图标", "Static icon"),
                ),
            ),
        ),
    )
}

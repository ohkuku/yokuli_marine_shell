package com.yokuli.marine.feature.chartlibrary

import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TilePresentationKind

sealed interface ChartLibraryDestination {
    data object Browse : ChartLibraryDestination
    data object NeedsAttention : ChartLibraryDestination
    data class Source(val id: ChartSourceId) : ChartLibraryDestination
    data class Asset(val id: ChartAssetId) : ChartLibraryDestination
}

object ChartLibraryDestinations {
    val AppId = LauncherAppId("chart_library")
    val EntryId = LauncherEntryId("chart_library")
    val Browse = LaunchToken("chart_library.browse")
    val NeedsAttention = LaunchToken("chart_library.attention")

    fun source(id: ChartSourceId): LaunchToken = objectToken(SOURCE_PREFIX, id.value)
    fun asset(id: ChartAssetId): LaunchToken = objectToken(ASSET_PREFIX, id.value)

    fun accepts(token: LaunchToken): Boolean = parse(token) != null

    fun parse(token: LaunchToken): ChartLibraryDestination? = when {
        token == Browse -> ChartLibraryDestination.Browse
        token == NeedsAttention -> ChartLibraryDestination.NeedsAttention
        token.value.startsWith(SOURCE_PREFIX) -> decodeId(token.value.removePrefix(SOURCE_PREFIX))
            ?.let { runCatching { ChartSourceId(it) }.getOrNull() }
            ?.let(ChartLibraryDestination::Source)
        token.value.startsWith(ASSET_PREFIX) -> decodeId(token.value.removePrefix(ASSET_PREFIX))
            ?.let { runCatching { ChartAssetId(it) }.getOrNull() }
            ?.let(ChartLibraryDestination::Asset)
        else -> null
    }

    private fun objectToken(prefix: String, id: String): LaunchToken {
        val bytes = id.toByteArray(Charsets.UTF_8)
        require(id.isNotBlank() && bytes.size <= MAX_ID_BYTES) { "Chart Library ID is too large for a launch token" }
        return LaunchToken(prefix + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }

    private fun decodeId(hex: String): String? {
        if (hex.isEmpty() || hex.length % 2 != 0 || hex.length > MAX_ID_BYTES * 2 ||
            !hex.matches(Regex("[0-9a-f]+"))
        ) return null
        return runCatching {
            ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                .let { bytes -> bytes.toString(Charsets.UTF_8).takeIf { it.isNotBlank() && it.toByteArray(Charsets.UTF_8).contentEquals(bytes) } }
        }.getOrNull()
    }

    private const val SOURCE_PREFIX = "chart_library.source."
    private const val ASSET_PREFIX = "chart_library.asset."
    private const val MAX_ID_BYTES = 512
}

object ChartLibraryShellContribution : LauncherCatalogContribution {
    override val app = LauncherAppDescriptor(ChartLibraryDestinations.AppId, ChartLibraryDestinations.EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            entryId = ChartLibraryDestinations.EntryId,
            appId = ChartLibraryDestinations.AppId,
            launchToken = ChartLibraryDestinations.Browse,
            defaultSize = MarineTileSize.STANDARD_2X2,
            supportedSizes = MarineTileSize.entries,
            pinPolicy = PinPolicy.PINNABLE,
            presentationKind = TilePresentationKind.STATUS,
        ),
    )
    override val internalLaunchTokens = listOf(ChartLibraryDestinations.NeedsAttention)
}

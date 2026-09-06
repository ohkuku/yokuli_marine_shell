package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.source.MarineFeatureDestination
import com.yokuli.marine.data.source.MarineFeatureLinkToken
import com.yokuli.marine.data.source.MarineFeatureLinks
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TilePresentationKind

object DataSourcesDestinations {
    val AppId = LauncherAppId("data-sources")
    val EntryId = LauncherEntryId("data-sources")
    val Overview = LaunchToken(MarineFeatureLinks.dataSourcesRoot.value)
    val Attention = LaunchToken(MarineFeatureLinks.dataSourcesAttention.value)

    fun connection(id: ConnectionId): LaunchToken =
        LaunchToken(MarineFeatureLinks.dataSourcesForConnection(id).value)

    fun parse(token: LaunchToken): MarineFeatureDestination? = when (
        val destination = MarineFeatureLinks.parse(MarineFeatureLinkToken(token.value))
    ) {
        is MarineFeatureDestination.DataSources,
        MarineFeatureDestination.DataSourcesAttention,
        -> destination
        else -> null
    }

    fun accepts(token: LaunchToken): Boolean = parse(token) != null
}

object DataSourcesShellContribution : LauncherCatalogContribution {
    override val app = LauncherAppDescriptor(DataSourcesDestinations.AppId, DataSourcesDestinations.EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            entryId = DataSourcesDestinations.EntryId,
            appId = DataSourcesDestinations.AppId,
            launchToken = DataSourcesDestinations.Overview,
            defaultSize = MarineTileSize.STANDARD_2X2,
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

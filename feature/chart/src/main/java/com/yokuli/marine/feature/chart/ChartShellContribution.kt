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

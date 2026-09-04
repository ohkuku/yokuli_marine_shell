package com.yokuli.marine.feature.chart

import com.yokuli.marine.core.model.DestinationId
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.MarineAppDescriptor
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.model.ShellFeatureContribution
import com.yokuli.marine.core.model.TileSize

object ChartDestinations {
    val AppId = MarineAppId("chart")
    val Browse = DestinationId("chart.browse")
    val Target = LaunchTarget(AppId, Browse)
    val EntryId = LauncherEntryId("chart")
}

object ChartShellContribution : ShellFeatureContribution {
    override val app = MarineAppDescriptor(ChartDestinations.AppId, ChartDestinations.Browse)
    override val launcherEntries = listOf(
        LauncherEntryDescriptor(
            id = ChartDestinations.EntryId,
            appId = ChartDestinations.AppId,
            launchTarget = ChartDestinations.Target,
            defaultSize = TileSize.WIDE_4X2,
            supportedSizesInCycleOrder = listOf(TileSize.SMALL_1X1, TileSize.MEDIUM_2X2, TileSize.WIDE_4X2),
        ),
    )
}

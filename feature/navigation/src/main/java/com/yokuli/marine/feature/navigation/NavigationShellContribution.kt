package com.yokuli.marine.feature.navigation

import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TilePresentationKind

object NavigationShellContribution : LauncherCatalogContribution {
    val AppId = LauncherAppId("navigation")
    val EntryId = LauncherEntryId("navigation")

    override val app = LauncherAppDescriptor(AppId, EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            entryId = EntryId,
            appId = AppId,
            launchToken = NavigationDestinations.Overview,
            defaultSize = MarineTileSize.STANDARD_2X2,
            supportedSizes = listOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2),
            pinPolicy = PinPolicy.PINNABLE,
            presentationKind = TilePresentationKind.STATUS,
        ),
    )
    override val internalLaunchTokens = listOf(
        NavigationDestinations.Waypoints,
        NavigationDestinations.Routes,
        NavigationDestinations.Gpx,
        NavigationDestinations.Active,
    )
}

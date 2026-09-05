package com.yokuli.marine.feature.settings

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TilePresentationKind

object SettingsDestinations {
    val AppId = LauncherAppId("settings")
    val Overview = LaunchToken("settings.overview")
    val Appearance = LaunchToken("settings.appearance")
    val StartScreen = LaunchToken("settings.start")
    val Map = LaunchToken("settings.map")
    val Language = LaunchToken("settings.language")
    val About = LaunchToken("settings.about")
    val EntryId = LauncherEntryId("settings")

    fun token(section: SettingsSection): LaunchToken = when (section) {
        SettingsSection.OVERVIEW -> Overview
        SettingsSection.APPEARANCE -> Appearance
        SettingsSection.START_SCREEN -> StartScreen
        SettingsSection.MAP -> Map
        SettingsSection.LANGUAGE -> Language
        SettingsSection.ABOUT -> About
    }

    fun section(launchToken: LaunchToken): SettingsSection? =
        SettingsSection.entries.firstOrNull { token(it) == launchToken }
}

object SettingsShellContribution : LauncherCatalogContribution {
    override val app = LauncherAppDescriptor(SettingsDestinations.AppId, SettingsDestinations.EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            entryId = SettingsDestinations.EntryId,
            appId = SettingsDestinations.AppId,
            launchToken = SettingsDestinations.Overview,
            defaultSize = MarineTileSize.ICON_1X1,
            supportedSizes = listOf(
                MarineTileSize.ICON_1X1,
                MarineTileSize.STANDARD_2X2,
            ),
            pinPolicy = PinPolicy.PINNABLE,
            presentationKind = TilePresentationKind.STATIC,
        ),
    )

    // Subpages share the installed Settings task, without becoming extra launcher entries.
    override val internalLaunchTokens = SettingsSection.entries
        .filterNot { it == SettingsSection.OVERVIEW }
        .map(SettingsDestinations::token)
}

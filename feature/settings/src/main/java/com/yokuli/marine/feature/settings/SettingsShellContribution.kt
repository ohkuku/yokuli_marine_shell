package com.yokuli.marine.feature.settings

import com.yokuli.marine.core.model.DestinationId
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.MarineAppDescriptor
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.model.ShellFeatureContribution
import com.yokuli.marine.core.model.TileSize

object SettingsDestinations {
    val AppId = MarineAppId("settings")
    val Overview = DestinationId("settings.overview")
    val Appearance = DestinationId("settings.appearance")
    val StartScreen = DestinationId("settings.start")
    val Map = DestinationId("settings.map")
    val Language = DestinationId("settings.language")
    val About = DestinationId("settings.about")
    val Target = LaunchTarget(AppId, Overview)
    val EntryId = LauncherEntryId("settings")

    fun target(section: SettingsSection) = LaunchTarget(
        appId = AppId,
        destination = when (section) {
            SettingsSection.OVERVIEW -> Overview
            SettingsSection.APPEARANCE -> Appearance
            SettingsSection.START_SCREEN -> StartScreen
            SettingsSection.MAP -> Map
            SettingsSection.LANGUAGE -> Language
            SettingsSection.ABOUT -> About
        },
    )

    fun section(destination: DestinationId): SettingsSection = when (destination) {
        Appearance -> SettingsSection.APPEARANCE
        StartScreen -> SettingsSection.START_SCREEN
        Map -> SettingsSection.MAP
        Language -> SettingsSection.LANGUAGE
        About -> SettingsSection.ABOUT
        else -> SettingsSection.OVERVIEW
    }
}

object SettingsShellContribution : ShellFeatureContribution {
    override val app = MarineAppDescriptor(SettingsDestinations.AppId, SettingsDestinations.Overview)
    override val launcherEntries = listOf(
        LauncherEntryDescriptor(
            id = SettingsDestinations.EntryId,
            appId = SettingsDestinations.AppId,
            launchTarget = SettingsDestinations.Target,
            defaultSize = TileSize.SMALL_1X1,
            supportedSizesInCycleOrder = listOf(TileSize.SMALL_1X1, TileSize.MEDIUM_2X2),
        ),
    )
}

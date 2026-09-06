package com.yokuli.marine.feature.preferences

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TilePresentationKind

object PreferencesDestinations {
    val AppId = LauncherAppId("preferences")
    val EntryId = LauncherEntryId("preferences")
    val Overview = LaunchToken("preferences.overview")
    val Appearance = LaunchToken("preferences.appearance")
    val Language = LaunchToken("preferences.language")
    val Units = LaunchToken("preferences.units")
    val Motion = LaunchToken("preferences.motion")
    val Start = LaunchToken("preferences.start")
    val AppTiles = LaunchToken("preferences.app-tiles")
    val About = LaunchToken("preferences.about")

    private val current = mapOf(
        Overview to PreferencesSection.OVERVIEW,
        Appearance to PreferencesSection.APPEARANCE,
        Language to PreferencesSection.LANGUAGE,
        Units to PreferencesSection.UNITS,
        Motion to PreferencesSection.MOTION,
        Start to PreferencesSection.START,
        AppTiles to PreferencesSection.APP_TILES,
        About to PreferencesSection.ABOUT,
    )
    private val legacy = mapOf(
        LaunchToken("settings.overview") to PreferencesSection.OVERVIEW,
        LaunchToken("settings.appearance") to PreferencesSection.APPEARANCE,
        LaunchToken("settings.language") to PreferencesSection.LANGUAGE,
        LaunchToken("settings.start") to PreferencesSection.START,
        LaunchToken("settings.map") to PreferencesSection.OVERVIEW,
        LaunchToken("settings.about") to PreferencesSection.ABOUT,
    )

    fun token(section: PreferencesSection) = current.entries.single { it.value == section }.key
    fun section(token: LaunchToken): PreferencesSection? = current[token] ?: legacy[token]
    fun accepts(token: LaunchToken) = section(token) != null
}

object PreferencesShellContribution : LauncherCatalogContribution {
    override val app = LauncherAppDescriptor(PreferencesDestinations.AppId, PreferencesDestinations.EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            PreferencesDestinations.EntryId,
            PreferencesDestinations.AppId,
            PreferencesDestinations.Overview,
            MarineTileSize.ICON_1X1,
            listOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2),
            PinPolicy.PINNABLE,
            TilePresentationKind.STATIC,
        ),
    )
    override val internalLaunchTokens = PreferencesSection.entries
        .filterNot { it == PreferencesSection.OVERVIEW }
        .map(PreferencesDestinations::token)
}

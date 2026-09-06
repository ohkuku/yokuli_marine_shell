package com.yokuli.marine.feature.nmeainput

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

object NmeaInputDestinations {
    val AppId = LauncherAppId("nmea-input")
    val EntryId = LauncherEntryId("nmea-input")
    val Overview = LaunchToken(MarineFeatureLinks.nmeaInputRoot.value)

    fun connection(id: ConnectionId): LaunchToken =
        LaunchToken(MarineFeatureLinks.nmeaInputForConnection(id).value)

    fun parse(token: LaunchToken): MarineFeatureDestination.NmeaInput? =
        MarineFeatureLinks.parse(MarineFeatureLinkToken(token.value)) as? MarineFeatureDestination.NmeaInput

    fun accepts(token: LaunchToken): Boolean = parse(token) != null
}

object NmeaInputShellContribution : LauncherCatalogContribution {
    override val app = LauncherAppDescriptor(NmeaInputDestinations.AppId, NmeaInputDestinations.EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            entryId = NmeaInputDestinations.EntryId,
            appId = NmeaInputDestinations.AppId,
            launchToken = NmeaInputDestinations.Overview,
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

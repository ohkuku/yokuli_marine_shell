package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TilePresentationKind

sealed interface DataDestination {
    val section: DataSection

    data class Section(override val section: DataSection) : DataDestination
    data class Input(val connectionId: ConnectionId?) : DataDestination {
        override val section = DataSection.INPUTS
    }
    data class Source(val connectionId: ConnectionId?) : DataDestination {
        override val section = DataSection.SOURCES
    }
}

/**
 * Current Data tokens and read-only redirects for the two retired product surfaces.
 * Legacy tokens never create another catalog entry or task identity.
 */
object DataDestinations {
    val AppId = LauncherAppId("data")
    val EntryId = LauncherEntryId("data")
    val Overview = LaunchToken("data.overview")
    val Inputs = LaunchToken("data.inputs")
    val Sources = LaunchToken("data.sources")
    val Flow = LaunchToken("data.flow")
    val Diagnostics = LaunchToken("data.diagnostics")

    fun input(id: ConnectionId) = LaunchToken("data.input.${id.value}")
    fun source(id: ConnectionId) = LaunchToken("data.source.${id.value}")

    fun parse(token: LaunchToken): DataDestination? = when (token.value) {
        Overview.value, "sources.root" -> DataDestination.Section(DataSection.OVERVIEW)
        Inputs.value, "nmea.root" -> DataDestination.Input(null)
        Sources.value -> DataDestination.Source(null)
        Flow.value -> DataDestination.Section(DataSection.FLOW)
        Diagnostics.value, "sources.attention" -> DataDestination.Section(DataSection.DIAGNOSTICS)
        else -> when {
            token.value.startsWith("data.input.") -> token.connectionAfter("data.input.")?.let(DataDestination::Input)
            token.value.startsWith("nmea.connection.") -> token.connectionAfter("nmea.connection.")?.let(DataDestination::Input)
            token.value.startsWith("data.source.") -> token.connectionAfter("data.source.")?.let(DataDestination::Source)
            token.value.startsWith("sources.connection.") -> token.connectionAfter("sources.connection.")?.let(DataDestination::Source)
            else -> null
        }
    }

    fun accepts(token: LaunchToken): Boolean = parse(token) != null

    private fun LaunchToken.connectionAfter(prefix: String): ConnectionId? = value.removePrefix(prefix)
        .takeIf { it.isNotBlank() && it.length <= 160 }
        ?.let(::ConnectionId)
}

object DataShellContribution : LauncherCatalogContribution {
    override val app = LauncherAppDescriptor(DataDestinations.AppId, DataDestinations.EntryId)
    override val entries = listOf(
        LauncherEntryDescriptor(
            entryId = DataDestinations.EntryId,
            appId = DataDestinations.AppId,
            launchToken = DataDestinations.Overview,
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

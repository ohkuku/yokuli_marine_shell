package com.yokuli.shell.storage

import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.CURRENT_LAUNCHER_PERSISTENCE_SCHEMA
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.engine.LauncherStartupHealth
import com.yokuli.shell.engine.PersistedLauncherPage
import com.yokuli.shell.engine.geometry.ProfileId
import com.yokuli.shell.engine.layout.Spacer
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import com.yokuli.shell.storage.proto.LauncherRecoveryProto
import com.yokuli.shell.storage.proto.LauncherStateProto
import com.yokuli.shell.storage.proto.StartDocumentProto
import com.yokuli.shell.storage.proto.TilePlacementProto
import com.yokuli.shell.storage.proto.SpacerProto

object LauncherProtoMapper {
    fun encode(state: LauncherPersistedState): LauncherStateProto = LauncherStateProto.newBuilder()
        .setSchemaVersion(state.schemaVersion)
        .setThemeMode(state.themeModeName)
        .setAccent(state.accentName)
        .setLanguageTag(state.languageTag)
        .setLayoutLocked(state.layoutLocked)
        .setLastLauncherPage(state.lastLauncherPage.name)
        .setLastForegroundToken(state.lastForegroundToken.orEmpty())
        .setRecovery(
            LauncherRecoveryProto.newBuilder()
                .setStartupAttemptCount(state.recovery.startupAttemptCount)
                .setLaunchPending(state.recovery.launchPending)
                .setLastLaunchEpochMillis(state.recovery.lastLaunchEpochMillis)
                .setSafeMode(state.recovery.safeMode),
        )
        .also { builder -> state.document?.let { builder.startDocument = encodeDocument(it) } }
        .build()

    fun decode(proto: LauncherStateProto): LauncherPersistedState {
        val page = PersistedLauncherPage.entries.firstOrNull { it.name == proto.lastLauncherPage }
            ?: PersistedLauncherPage.START
        return LauncherPersistedState(
            schemaVersion = proto.schemaVersion,
            document = if (proto.hasStartDocument()) decodeDocument(proto.startDocument) else null,
            themeModeName = proto.themeMode.ifBlank { "DARK" },
            accentName = proto.accent.ifBlank { "CYAN" },
            languageTag = proto.languageTag.ifBlank { "zh-CN" },
            layoutLocked = proto.layoutLocked,
            lastLauncherPage = page,
            lastForegroundToken = proto.lastForegroundToken.ifBlank { null },
            recovery = LauncherStartupHealth(
                startupAttemptCount = proto.recovery.startupAttemptCount,
                launchPending = proto.recovery.launchPending,
                lastLaunchEpochMillis = proto.recovery.lastLaunchEpochMillis,
                safeMode = proto.recovery.safeMode,
            ),
        )
    }

    private fun encodeDocument(document: StartDocument): StartDocumentProto = StartDocumentProto.newBuilder()
        .setSchemaVersion(document.schemaVersion)
        .setProfileId(document.profileId.value)
        .setDefaultLayoutVersion(document.defaultLayoutVersion)
        .addAllPlacements(
            document.placements.map { placement ->
                TilePlacementProto.newBuilder()
                    .setTileId(placement.tileId.value)
                    .setEntryId(placement.entryId.value)
                    .setSize(placement.size.name)
                    .setRank(placement.rank)
                    .setGroupId(placement.groupId.orEmpty())
                    .build()
            },
        )
        .addAllSpacers(
            document.spacers.map { spacer ->
                SpacerProto.newBuilder()
                    .setSpacerId(spacer.spacerId.value)
                    .setSize(spacer.size.name)
                    .setRank(spacer.rank)
                    .setGroupId(spacer.groupId.orEmpty())
                    .build()
            },
        )
        .build()

    private fun decodeDocument(proto: StartDocumentProto): StartDocument {
        val placements = proto.placementsList.map { placement ->
            val size = MarineTileSize.fromPersistedName(placement.size) ?: MarineTileSize.ICON_1X1
            TilePlacement(
                tileId = TileInstanceId(placement.tileId),
                entryId = LauncherEntryId(placement.entryId),
                size = size,
                rank = placement.rank,
                groupId = placement.groupId.ifBlank { null },
            )
        }
        val spacers = proto.spacersList.map { spacer ->
            val size = MarineTileSize.fromPersistedName(spacer.size) ?: MarineTileSize.ICON_1X1
            Spacer(
                spacerId = TileInstanceId(spacer.spacerId),
                size = size,
                rank = spacer.rank,
                groupId = spacer.groupId.ifBlank { null },
            )
        }
        return StartDocument(
            schemaVersion = proto.schemaVersion,
            profileId = ProfileId(proto.profileId),
            defaultLayoutVersion = proto.defaultLayoutVersion,
            placements = placements,
            spacers = spacers,
        )
    }

    fun emptyDefaults(): LauncherStateProto = encode(
        LauncherPersistedState(schemaVersion = CURRENT_LAUNCHER_PERSISTENCE_SCHEMA),
    )
}

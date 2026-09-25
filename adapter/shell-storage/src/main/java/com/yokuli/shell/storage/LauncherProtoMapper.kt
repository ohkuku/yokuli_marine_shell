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
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import com.yokuli.shell.storage.proto.LauncherRecoveryProto
import com.yokuli.shell.storage.proto.LauncherStateProto
import com.yokuli.shell.storage.proto.StartDocumentProto
import com.yokuli.shell.storage.proto.TilePlacementProto
import com.yokuli.shell.storage.proto.SpacerProto
import com.yokuli.shell.storage.proto.TileBindingProto
import com.yokuli.shell.storage.proto.TilePresentationProto
import com.yokuli.shell.storage.proto.TileCommitReceiptProto
import com.yokuli.shell.storage.proto.TileRemovalRecordProto
import com.yokuli.shell.contract.TileBinding
import com.yokuli.shell.contract.TileBindingKind
import com.yokuli.shell.contract.TilePresentation
import com.yokuli.shell.engine.layout.TileCommitReceipt
import com.yokuli.shell.engine.layout.TileRemovalRecord
import java.util.Base64

object LauncherProtoMapper {
    fun encode(state: LauncherPersistedState): LauncherStateProto {
        val builder = state.preservedProto.takeIf { it.isNotEmpty() }
            ?.let { LauncherStateProto.parseFrom(Base64.getDecoder().decode(it)).toBuilder() }
            ?: LauncherStateProto.newBuilder()
        return builder.applyState(state)
    }

    private fun LauncherStateProto.Builder.applyState(state: LauncherPersistedState): LauncherStateProto = this
        .setSchemaVersion(state.schemaVersion)
        .setThemeMode(state.themeModeName)
        .setAccent(state.accentName)
        .setLanguageTag(state.languageTag)
        .setMeasurementUnitSystem(state.measurementUnitSystemName)
        .setMotionPreference(state.motionPreferenceName)
        .clearAppPreferences()
        .putAllAppPreferences(state.appPreferenceValues)
        .setWorkshopDraft(state.workshopDraft.orEmpty())
        .setLayoutLocked(state.layoutLocked)
        .setLastLauncherPage(state.lastLauncherPage.name)
        .setLastForegroundToken(state.lastForegroundToken.orEmpty())
        .setProductModelVersion(state.productModelVersion)
        .setRecovery(
            LauncherRecoveryProto.newBuilder()
                .setStartupAttemptCount(state.recovery.startupAttemptCount)
                .setLaunchPending(state.recovery.launchPending)
                .setLastLaunchEpochMillis(state.recovery.lastLaunchEpochMillis)
                .setSafeMode(state.recovery.safeMode),
        )
        .also { builder -> val document = state.document; if (document == null) builder.clearStartDocument() else builder.startDocument = encodeDocument(document) }
        .build()

    fun decode(proto: LauncherStateProto): LauncherPersistedState {
        val page = PersistedLauncherPage.entries.firstOrNull { it.name == proto.lastLauncherPage }
            ?: PersistedLauncherPage.START
        return LauncherPersistedState(
            schemaVersion = proto.schemaVersion,
            workshopDraft = proto.workshopDraft.ifBlank { null },
            preservedProto = Base64.getEncoder().encodeToString(proto.toByteArray()),
            document = if (proto.hasStartDocument()) decodeDocument(proto.startDocument) else null,
            themeModeName = proto.themeMode.ifBlank { "DARK" },
            accentName = proto.accent.ifBlank { "CYAN" },
            languageTag = proto.languageTag.ifBlank { "zh-CN" },
            measurementUnitSystemName = proto.measurementUnitSystem.ifBlank { "NAUTICAL" },
            motionPreferenceName = proto.motionPreference.ifBlank { "FOLLOW_SYSTEM" },
            appPreferenceValues = proto.appPreferencesMap,
            layoutLocked = proto.layoutLocked,
            lastLauncherPage = page,
            lastForegroundToken = proto.lastForegroundToken.ifBlank { null },
            productModelVersion = proto.productModelVersion,
            recovery = LauncherStartupHealth(
                startupAttemptCount = proto.recovery.startupAttemptCount,
                launchPending = proto.recovery.launchPending,
                lastLaunchEpochMillis = proto.recovery.lastLaunchEpochMillis,
                safeMode = proto.recovery.safeMode,
            ),
        )
    }

    private fun encodeDocument(document: StartDocument): StartDocumentProto {
        val builder = document.preservedProto.takeIf { it.isNotEmpty() }
            ?.let { StartDocumentProto.parseFrom(Base64.getDecoder().decode(it)).toBuilder() }
            ?: StartDocumentProto.newBuilder()
        return builder.setSchemaVersion(document.schemaVersion)
            .setProfileId(document.profileId.value).setDefaultLayoutVersion(document.defaultLayoutVersion)
            .setRevision(document.revision)
            .clearPlacements().addAllPlacements(document.placements.map(::encodePlacement))
            .clearSpacers().addAllSpacers(document.spacers.map { spacer ->
                SpacerProto.newBuilder().setSpacerId(spacer.spacerId.value).setSize(spacer.size.name)
                    .setRank(spacer.rank).setGroupId(spacer.groupId.orEmpty())
                    .setHasPreferredCell(spacer.preferredCell != null).setPreferredColumn(spacer.preferredCell?.column ?: 0)
                    .setPreferredRow(spacer.preferredCell?.row ?: 0).build()
            })
            .clearReceipts().addAllReceipts(document.receipts.map { receipt ->
                TileCommitReceiptProto.newBuilder().setRequestId(receipt.requestId).setTileId(receipt.tileId.value)
                    .setRevision(receipt.revision).setDocumentRevision(receipt.documentRevision).setOperation(receipt.operation).setRelocated(receipt.relocated).build()
            })
            .clearRemovedTiles().addAllRemovedTiles(document.removedTiles.map { removed ->
                TileRemovalRecordProto.newBuilder().setRequestId(removed.requestId).setEntry(encodePlacement(removed.entry))
                    .setDocumentRevision(removed.documentRevision).build()
            })
            .clearRecoveryNotes().addAllRecoveryNotes(document.recoveryNotes).build()
    }

    private fun encodePlacement(placement: TilePlacement): TilePlacementProto {
        val builder = placement.preservedProto.takeIf { it.isNotEmpty() }
            ?.let { TilePlacementProto.parseFrom(Base64.getDecoder().decode(it)).toBuilder() }
            ?: TilePlacementProto.newBuilder()
        val originalSize = builder.size.takeIf { it.isNotBlank() && MarineTileSize.fromPersistedName(it) == null && placement.size == MarineTileSize.ICON_1X1 }
        builder.setTileId(placement.tileId.value).setEntryId(placement.entryId.value)
            .setSize(originalSize ?: placement.size.name).setRank(placement.rank).setGroupId(placement.groupId.orEmpty())
            .setRevision(placement.revision).setHasPreferredCell(placement.preferredCell != null)
        placement.preferredCell?.let { builder.setPreferredColumn(it.column).setPreferredRow(it.row) }
        placement.binding?.let { binding ->
            builder.setBinding(builder.binding.toBuilder().setProviderId(binding.providerId)
                .setKind(binding.unknownKind ?: binding.kind.name).setContentId(binding.contentId))
        } ?: builder.clearBinding()
        val presentation = placement.presentation
        builder.setPresentation(builder.presentation.toBuilder().setStyle(presentation.style)
            .setLegacyMode(presentation.legacyMode.orEmpty()).setHasRotate(presentation.rotate != null)
            .setRotate(presentation.rotate ?: false).setIntervalSeconds(presentation.intervalSeconds ?: 0)
            .setHistoryMinutes(presentation.historyMinutes ?: 0)
            .setHasFixedRange(presentation.rangeMinimum!=null&&presentation.rangeMaximum!=null)
            .setRangeMinimum(presentation.rangeMinimum ?: 0.0).setRangeMaximum(presentation.rangeMaximum ?: 0.0)
            .setHideSource(!presentation.showSource).setHideReference(!presentation.showReference))
        return builder.build()
    }

    private fun decodePlacement(placement: TilePlacementProto): TilePlacement {
        val kind = TileBindingKind.entries.firstOrNull { it.name == placement.binding.kind }
        return TilePlacement(
            tileId = TileInstanceId(placement.tileId), entryId = LauncherEntryId(placement.entryId),
            size = MarineTileSize.fromPersistedName(placement.size) ?: MarineTileSize.ICON_1X1,
            rank = placement.rank, groupId = placement.groupId.ifBlank { null },
            preferredCell = if (placement.hasPreferredCell) GridCell(placement.preferredColumn, placement.preferredRow) else null,
            binding = if (placement.hasBinding()) TileBinding(placement.binding.providerId, kind ?: TileBindingKind.UNKNOWN,
                placement.binding.contentId, if (kind == null) placement.binding.kind else null) else null,
            presentation = TilePresentation(placement.presentation.style.ifBlank { "default" },
                placement.presentation.legacyMode.ifBlank { null },
                if (placement.presentation.hasRotate) placement.presentation.rotate else null,
                placement.presentation.intervalSeconds.takeIf { it > 0 },
                placement.presentation.historyMinutes.takeIf {it in 1..15},
                placement.presentation.rangeMinimum.takeIf {placement.presentation.hasFixedRange},
                placement.presentation.rangeMaximum.takeIf {placement.presentation.hasFixedRange},
                !placement.presentation.hideSource,!placement.presentation.hideReference),
            revision = placement.revision,
            preservedProto = Base64.getEncoder().encodeToString(placement.toByteArray()),
        )
    }

    private fun decodeDocument(proto: StartDocumentProto): StartDocument {
        val placements = proto.placementsList.map(::decodePlacement)
        val spacers = proto.spacersList.map { spacer ->
            val size = MarineTileSize.fromPersistedName(spacer.size) ?: MarineTileSize.ICON_1X1
            Spacer(
                spacerId = TileInstanceId(spacer.spacerId),
                size = size,
                rank = spacer.rank,
                groupId = spacer.groupId.ifBlank { null },
                preferredCell = if (spacer.hasPreferredCell) GridCell(spacer.preferredColumn, spacer.preferredRow) else null,
            )
        }
        return StartDocument(
            schemaVersion = proto.schemaVersion,
            profileId = ProfileId(proto.profileId),
            defaultLayoutVersion = proto.defaultLayoutVersion,
            placements = placements,
            spacers = spacers,
            revision = proto.revision,
            receipts = proto.receiptsList.map { TileCommitReceipt(it.requestId, TileInstanceId(it.tileId), it.revision, it.documentRevision, it.operation, it.relocated) },
            removedTiles = proto.removedTilesList.map { TileRemovalRecord(it.requestId, decodePlacement(it.entry), it.documentRevision) },
            recoveryNotes = proto.recoveryNotesList,
            preservedProto = Base64.getEncoder().encodeToString(proto.toByteArray()),
        )
    }

    fun emptyDefaults(): LauncherStateProto = encode(
        LauncherPersistedState(schemaVersion = CURRENT_LAUNCHER_PERSISTENCE_SCHEMA),
    )
}

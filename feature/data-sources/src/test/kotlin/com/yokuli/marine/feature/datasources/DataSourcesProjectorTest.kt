package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.catalog.RawPreviewEntry
import com.yokuli.marine.data.catalog.SentenceCatalogEntry
import com.yokuli.marine.data.catalog.SentenceCatalogKey
import com.yokuli.marine.data.catalog.SentenceParseStatus
import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.phone.PhoneLocationState
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDataSnapshot
import com.yokuli.marine.data.source.SelectionReason
import com.yokuli.marine.data.source.SourceCandidate
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceCatalogSnapshot
import com.yokuli.marine.data.source.SourceDecision
import com.yokuli.marine.data.source.SourceDecisionStatus
import com.yokuli.marine.data.source.SourceDescriptor
import com.yokuli.marine.data.source.SourceEvidence
import com.yokuli.marine.data.source.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourcesProjectorTest {
    private val sourceA = SourceIdentity(ConnectionId("gateway-a"))
    private val sourceB = SourceIdentity(ConnectionId("gateway-b"))

    @Test
    fun windAndDepthOnlyInputProducesACompleteDataCatalogWithoutPosition() {
        val wind = candidate(DataKey.WindSpeed(com.yokuli.marine.data.model.WindSpeedReference.APPARENT), sourceA)
        val depth = candidate(DataKey.Depth(com.yokuli.marine.data.model.DepthReference.BELOW_TRANSDUCER), sourceA)
        val state = sourceSnapshot(listOf(wind, depth))

        val ui = DataSourcesProjector.project(
            sources = state,
            nmea = NmeaRuntimeSnapshot.EMPTY,
            phone = PhoneLocationSnapshot.EMPTY,
            local = DataSourcesLocalState(),
            nowMillis = 1_000L,
        )

        assertEquals(2, (ui.page as DataSourcesPageUi.Overview).dataRows.size)
        assertFalse((ui.page as DataSourcesPageUi.Overview).dataRows.any { it.key == DataKey.Position })
    }

    @Test
    fun unselectedCandidatesRemainVisibleAndMultipleSourceRowNeedsSelection() {
        val candidates = listOf(candidate(DataKey.Position, sourceA), candidate(DataKey.Position, sourceB))
        val state = sourceSnapshot(
            candidates,
            decisions = listOf(
                SourceDecision(DataKey.Position, SourceDecisionStatus.NEEDS_SELECTION, null, null, 2, true),
            ),
        )
        val ui = DataSourcesProjector.project(
            state,
            NmeaRuntimeSnapshot.EMPTY,
            PhoneLocationSnapshot.EMPTY,
            DataSourcesLocalState(),
            1_000L,
        )

        val row = (ui.page as DataSourcesPageUi.Overview).dataRows.single()
        assertEquals(2, row.candidates.size)
        assertTrue(row.needsAttention)
        assertTrue(row.candidates.none { it.selected })
    }

    @Test
    fun unknownLegalSentenceAndBoundedRawEvidenceRemainVisibleInSentenceView() {
        val key = SentenceCatalogKey(sourceA, "GP", "XYZ")
        val entry = SentenceCatalogEntry(
            key = key,
            sessionGeneration = SessionGeneration(1L),
            receivedCount = 4L,
            firstSeenMillis = 10L,
            lastSeenMillis = 900L,
            lastGroupId = ObservationGroupId(4L),
            lastSentenceId = "GPXYZ",
            lastParseStatus = SentenceParseStatus.UNSUPPORTED,
            lastSender = null,
            isCurrentSession = true,
        )
        val nmea = NmeaRuntimeSnapshot.EMPTY.copy(
            sentenceCatalog = NmeaRuntimeSnapshot.EMPTY.sentenceCatalog.copy(entries = listOf(entry)),
            rawPreview = NmeaRuntimeSnapshot.EMPTY.rawPreview.copy(
                entries = listOf(
                    RawPreviewEntry(sourceA, SessionGeneration(1L), null, "GPXYZ", 900L, "\$GPXYZ,1*00", true),
                ),
                retainedBytes = 11,
            ),
        )

        val ui = DataSourcesProjector.project(
            MarineSourceSnapshot.EMPTY,
            nmea,
            PhoneLocationSnapshot.EMPTY,
            DataSourcesLocalState(viewMode = DataSourcesViewMode.SENTENCES),
            1_000L,
        )

        val row = (ui.page as DataSourcesPageUi.Overview).sentenceRows.single()
        assertEquals("GPXYZ", row.sentenceId)
        assertEquals(SentenceMeaningUi.UNSUPPORTED, row.meaning)
        assertEquals(1, row.rawEvidence.size)
    }

    @Test
    fun searchAndConnectionFilterUseTheSameCatalogWithoutCreatingAnotherTruth() {
        val candidates = listOf(candidate(DataKey.Position, sourceA), candidate(DataKey.Position, sourceB))
        val ui = DataSourcesProjector.project(
            sourceSnapshot(candidates),
            NmeaRuntimeSnapshot.EMPTY,
            PhoneLocationSnapshot.EMPTY,
            DataSourcesLocalState(
                query = "gateway-b",
                filter = DataSourcesFilter.Connection(ConnectionId("gateway-b")),
            ),
            1_000L,
        )

        val row = (ui.page as DataSourcesPageUi.Overview).dataRows.single()
        assertEquals(listOf(sourceB), row.candidates.map { it.source })
    }

    @Test
    fun zeroPermissionPhoneStateDoesNotMakeNmeaLookBroken() {
        val phone = PhoneLocationSnapshot(
            enabledByUser = true,
            permission = PhoneLocationPermission.DENIED,
            systemLocationEnabled = true,
            state = PhoneLocationState.PERMISSION_REQUIRED,
            latestFix = null,
            revision = 1L,
        )
        val ui = DataSourcesProjector.project(
            sourceSnapshot(listOf(candidate(DataKey.Position, sourceA))),
            NmeaRuntimeSnapshot.EMPTY,
            phone,
            DataSourcesLocalState(),
            1_000L,
        )

        assertEquals(PhoneSourceUi.PERMISSION_REQUIRED, ui.phone.state)
        assertEquals(1, (ui.page as DataSourcesPageUi.Overview).dataRows.size)
    }

    private fun candidate(key: DataKey, source: SourceIdentity) = SourceCandidate(
        id = CandidateId(key, source),
        descriptor = SourceDescriptor(source, SourceKind.NMEA, source.connectionId.value, "RMC"),
        value = when (key) {
            DataKey.Position -> MarineValue.Position(-36.8, 174.7)
            else -> MarineValue.Decimal(4.0, if (key is DataKey.Depth) MarineUnit.METERS else MarineUnit.KNOTS)
        },
        lastValidValue = when (key) {
            DataKey.Position -> MarineValue.Position(-36.8, 174.7)
            else -> MarineValue.Decimal(4.0, if (key is DataKey.Depth) MarineUnit.METERS else MarineUnit.KNOTS)
        },
        availability = SourceCandidateAvailability.LIVE,
        ageMillis = 100L,
        receivedAtMillis = 900L,
        groupId = ObservationGroupId(1L),
        evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
    )

    private fun sourceSnapshot(
        candidates: List<SourceCandidate>,
        decisions: List<SourceDecision> = candidates.groupBy { it.id.key }.map { (key, values) ->
            SourceDecision(
                key,
                if (values.size == 1) SourceDecisionStatus.DISCOVERING else SourceDecisionStatus.NEEDS_SELECTION,
                null,
                null,
                values.size,
                values.size > 1,
            )
        },
    ) = MarineSourceSnapshot(
        sourceCatalog = SourceCatalogSnapshot(candidates, 1_000L, 1L),
        decisions = decisions,
        resolvedData = ResolvedDataSnapshot(emptyMap(), 0L, 1_000L),
        selectionRevision = 0L,
        revision = 1L,
        lastFailure = null,
    )
}

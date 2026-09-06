package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDataSnapshot
import com.yokuli.marine.data.source.ResolvedDatum
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

class DataSourcesLauncherProjectorTest {
    private val source = SourceIdentity(ConnectionId("source"))

    @Test
    fun emptyCatalogWaitsForDataWithoutInventingAValueOrError() {
        val state = DataSourcesLauncherProjector.project(MarineSourceSnapshot.EMPTY)

        assertEquals(DataSourcesTilePriority.WAITING, state.tile.priority)
        assertEquals(0, state.tile.attentionCount)
        assertTrue(state.tile.relations.isEmpty())
        assertFalse(state.status.visible)
    }

    @Test
    fun phoneOnlySelectionIsAHealthyFirstClassSource() {
        val candidate = candidate(SourceKind.PHONE_SYSTEM_LOCATION, SourceCandidateAvailability.LIVE)
        val state = DataSourcesLauncherProjector.project(snapshot(candidate, SourceDecisionStatus.USING))

        assertEquals(DataSourcesTilePriority.USING, state.tile.priority)
        assertEquals(1, state.tile.usingCount)
        assertEquals("Phone system location", state.tile.relations.single().sourceName)
        assertEquals(0, state.tile.attentionCount)
    }

    @Test
    fun needsSelectionAndSelectedStaleAreDistinctAttentionFacts() {
        val live = candidate(SourceKind.NMEA, SourceCandidateAvailability.LIVE)
        val needs = DataSourcesLauncherProjector.project(snapshot(live, SourceDecisionStatus.NEEDS_SELECTION))
        val stale = DataSourcesLauncherProjector.project(
            snapshot(candidate(SourceKind.NMEA, SourceCandidateAvailability.STALE), SourceDecisionStatus.SELECTED_UNAVAILABLE),
        )

        assertEquals(1, needs.tile.needsSelectionCount)
        assertEquals(0, needs.tile.interruptedCount)
        assertEquals(0, stale.tile.needsSelectionCount)
        assertEquals(1, stale.tile.interruptedCount)
        assertTrue(needs.status.openNeedsAttention)
        assertTrue(stale.status.openNeedsAttention)
    }

    @Test
    fun sourceTileDisplayFreezesNormalRelationsButNeverHidesAttention() {
        val healthy = DataSourcesLauncherProjector.project(
            snapshot(candidate(SourceKind.NMEA, SourceCandidateAvailability.LIVE), SourceDecisionStatus.USING),
        ).tile
        val empty = DataSourcesLauncherProjector.project(MarineSourceSnapshot.EMPTY).tile
        val attention = DataSourcesLauncherProjector.project(
            snapshot(candidate(SourceKind.NMEA, SourceCandidateAvailability.STALE), SourceDecisionStatus.SELECTED_UNAVAILABLE),
        ).tile
        val slot = DataSourcesTileDisplaySlot(healthy)

        assertEquals(healthy, slot.resolve(empty, liveContentEnabled = false))
        assertEquals(attention, slot.resolve(attention, liveContentEnabled = false))
    }

    private fun candidate(kind: SourceKind, availability: SourceCandidateAvailability): SourceCandidate = SourceCandidate(
        CandidateId(DataKey.Position, source),
        SourceDescriptor(
            source,
            kind,
            if (kind == SourceKind.NMEA) "Boat gateway" else "Phone system location",
            "position",
        ),
        MarineValue.Position(-36.8, 174.7).takeIf { availability == SourceCandidateAvailability.LIVE },
        MarineValue.Position(-36.8, 174.7),
        availability,
        0L,
        1L,
        ObservationGroupId(1L),
        if (kind == SourceKind.NMEA) SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC"))
        else SourceEvidence.PhoneSystemLocation("fused", false),
    )

    private fun snapshot(candidate: SourceCandidate, status: SourceDecisionStatus): MarineSourceSnapshot {
        val selected = status in setOf(
            SourceDecisionStatus.USING,
            SourceDecisionStatus.SELECTED_UNAVAILABLE,
            SourceDecisionStatus.SELECTED_MISSING,
        )
        val decision = SourceDecision(
            DataKey.Position,
            status,
            source.takeIf { selected },
            SelectionReason.USER.takeIf { selected },
            1,
            status == SourceDecisionStatus.NEEDS_SELECTION,
        )
        val revision = if (selected) 1L else 0L
        val resolved = if (selected) {
            mapOf(
                DataKey.Position to ResolvedDatum(
                    DataKey.Position,
                    source,
                    candidate,
                    candidate.value,
                    candidate.availability,
                    SelectionReason.USER,
                    revision,
                ),
            )
        } else emptyMap()
        return MarineSourceSnapshot(
            SourceCatalogSnapshot(listOf(candidate), 1L, 1L),
            listOf(decision),
            ResolvedDataSnapshot(resolved, revision, 1L),
            revision,
            1L,
            null,
        )
    }
}

package com.yokuli.marine.data.source

import com.yokuli.marine.data.catalog.Freshness
import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSelectionReducerTest {
    private val reducer = SourceSelectionReducer()
    private val key = DataKey.Position
    private val sourceA = SourceIdentity(ConnectionId("gateway-a"))
    private val sourceB = SourceIdentity(ConnectionId("gateway-b"))

    @Test
    fun uniqueCandidateIsOnlyAdoptedAfterTheThreeSecondDiscoveryWindow() {
        var state = SourceSelectionState.empty()
        state = reduceCatalog(state, catalog(0L, candidate(sourceA, 1.0)), 0L).state
        assertEquals(SourceDecisionStatus.DISCOVERING, state.snapshot.decision(key).status)
        assertNull(state.snapshot.resolvedData.items[key])

        state = reduceCatalog(state, catalog(2_999L, candidate(sourceA, 1.0)), 2_999L).state
        assertEquals(SourceDecisionStatus.DISCOVERING, state.snapshot.decision(key).status)

        val adopted = reduceCatalog(state, catalog(3_000L, candidate(sourceA, 1.0)), 3_000L)
        assertTrue(adopted.persistenceRequired)
        assertEquals(sourceA, adopted.state.snapshot.resolvedData.items.getValue(key).source)
        assertEquals(SelectionReason.AUTOMATIC_UNIQUE, adopted.state.snapshot.decision(key).reason)
    }

    @Test
    fun firstMultipleCandidatesNeverUseArrivalOrderAsSelection() {
        var state = SourceSelectionState.empty()
        state = reduceCatalog(
            state,
            catalog(0L, candidate(sourceB, 2.0), candidate(sourceA, 1.0)),
            0L,
        ).state
        state = reduceCatalog(
            state,
            catalog(3_000L, candidate(sourceB, 2.0), candidate(sourceA, 1.0)),
            3_000L,
        ).state

        assertEquals(SourceDecisionStatus.NEEDS_SELECTION, state.snapshot.decision(key).status)
        assertNull(state.snapshot.resolvedData.items[key])
        assertFalse(state.preferences.containsKey(key))
    }

    @Test
    fun automaticallyAdoptedSourceIsNotStolenWhenANewCandidateArrives() {
        var state = SourceSelectionState.empty()
        state = reduceCatalog(state, catalog(0L, candidate(sourceA, 1.0)), 0L).state
        state = reduceCatalog(state, catalog(3_000L, candidate(sourceA, 1.0)), 3_000L).state

        state = reduceCatalog(
            state,
            catalog(3_100L, candidate(sourceA, 1.1), candidate(sourceB, 2.0)),
            3_100L,
        ).state

        val decision = state.snapshot.decision(key)
        assertEquals(sourceA, decision.selectedSource)
        assertEquals(sourceA, state.snapshot.resolvedData.items.getValue(key).source)
        assertTrue(decision.needsReview)
    }

    @Test
    fun explicitSelectionDoesNotNagWhenAnotherSourceAppears() {
        var state = SourceSelectionState.empty()
        state = reduceCatalog(
            state,
            catalog(0L, candidate(sourceA, 1.0), candidate(sourceB, 2.0)),
            0L,
        ).state
        state = reducer.reduce(
            state,
            SourceSelectionAction.Select(key, sourceA, selectedAtMillis = 1L),
        ).state

        val decision = state.snapshot.decision(key)
        assertEquals(SelectionReason.USER, decision.reason)
        assertEquals(sourceA, decision.selectedSource)
        assertFalse(decision.needsReview)
    }

    @Test
    fun selectedSourceStalesWithoutSilentlyFailingOverAndRecoversInPlace() {
        var state = SourceSelectionState.empty()
        state = reduceCatalog(
            state,
            catalog(0L, candidate(sourceA, 1.0), candidate(sourceB, 2.0)),
            0L,
        ).state
        state = reducer.reduce(
            state,
            SourceSelectionAction.Select(key, sourceA, selectedAtMillis = 1L),
        ).state

        state = reduceCatalog(
            state,
            catalog(
                10_000L,
                candidate(sourceA, 1.0, availability = SourceCandidateAvailability.STALE),
                candidate(sourceB, 2.2),
            ),
            10_000L,
        ).state
        assertEquals(sourceA, state.snapshot.resolvedData.items.getValue(key).source)
        assertEquals(SourceCandidateAvailability.STALE, state.snapshot.resolvedData.items.getValue(key).availability)
        assertEquals(SourceDecisionStatus.SELECTED_UNAVAILABLE, state.snapshot.decision(key).status)

        state = reduceCatalog(
            state,
            catalog(10_100L, candidate(sourceA, 1.3), candidate(sourceB, 2.2)),
            10_100L,
        ).state
        assertEquals(sourceA, state.snapshot.resolvedData.items.getValue(key).source)
        assertEquals(SourceCandidateAvailability.LIVE, state.snapshot.resolvedData.items.getValue(key).availability)
        assertEquals(SourceDecisionStatus.USING, state.snapshot.decision(key).status)
    }

    @Test
    fun disableIsPersistentPolicyAndPreventsFutureAutomaticAdoption() {
        var state = SourceSelectionState.empty()
        state = reduceCatalog(state, catalog(0L, candidate(sourceA, 1.0)), 0L).state
        val disabled = reducer.reduce(state, SourceSelectionAction.Disable(key, disabledAtMillis = 1L))
        assertTrue(disabled.persistenceRequired)
        state = disabled.state

        state = reduceCatalog(state, catalog(5_000L, candidate(sourceA, 1.5)), 5_000L).state
        assertEquals(SourceDecisionStatus.DISABLED, state.snapshot.decision(key).status)
        assertNull(state.snapshot.resolvedData.items[key])
        assertTrue(state.preferences[key] is SourcePreference.Disabled)
    }

    @Test
    fun selectingAStaleOrDisappearedCandidateIsRejectedWithoutChangingState() {
        var state = SourceSelectionState.empty()
        state = reduceCatalog(
            state,
            catalog(0L, candidate(sourceA, 1.0, availability = SourceCandidateAvailability.STALE)),
            0L,
        ).state
        val before = state
        val transition = reducer.reduce(
            state,
            SourceSelectionAction.Select(key, sourceA, selectedAtMillis = 1L),
        )

        assertEquals(SourceSelectionRejection.CANDIDATE_UNAVAILABLE, transition.rejection)
        assertEquals(before, transition.state)
        assertFalse(transition.persistenceRequired)
    }

    @Test
    fun auditIsBoundedAndNeverContainsMeasurementValues() {
        var state = SourceSelectionState.empty(maxAuditEntries = 4)
        state = reduceCatalog(state, catalog(0L, candidate(sourceA, 1.0), candidate(sourceB, 2.0)), 0L).state
        repeat(10) { index ->
            val source = if (index % 2 == 0) sourceA else sourceB
            state = reducer.reduce(
                state,
                SourceSelectionAction.Select(key, source, selectedAtMillis = index.toLong() + 1L),
            ).state
        }

        assertEquals(4, state.audit.size)
        assertTrue(state.audit.all { !it.toString().contains("latitude", ignoreCase = true) })
    }

    private fun reduceCatalog(
        state: SourceSelectionState,
        catalog: SourceCatalogSnapshot,
        now: Long,
    ) = reducer.reduce(state, SourceSelectionAction.CatalogChanged(catalog, now))

    private fun catalog(now: Long, vararg candidates: SourceCandidate) = SourceCatalogSnapshot(
        candidates = candidates.toList(),
        evaluatedAtMillis = now,
        revision = now,
    )

    private fun candidate(
        source: SourceIdentity,
        latitude: Double,
        availability: SourceCandidateAvailability = SourceCandidateAvailability.LIVE,
    ) = SourceCandidate(
        id = CandidateId(key, source),
        descriptor = SourceDescriptor(
            identity = source,
            kind = SourceKind.NMEA,
            displayName = source.connectionId.value,
            detail = "RMC/GGA",
        ),
        value = MarineValue.Position(latitude, 174.0),
        lastValidValue = MarineValue.Position(latitude, 174.0),
        availability = availability,
        ageMillis = 0L,
        receivedAtMillis = 0L,
        groupId = ObservationGroupId(1L),
        evidence = SourceEvidence.Nmea(
            sentenceIds = setOf("GPRMC", "GPGGA"),
            formatters = setOf("RMC", "GGA"),
        ),
    )
}

private fun MarineSourceSnapshot.decision(key: DataKey): SourceDecision =
    decisions.single { it.key == key }

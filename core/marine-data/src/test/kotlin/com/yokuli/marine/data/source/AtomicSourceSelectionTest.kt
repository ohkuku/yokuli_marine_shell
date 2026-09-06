package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicSourceSelectionTest {
    private val reducer = SourceSelectionReducer()
    private val source = SourceIdentity(ConnectionId("bridge"))

    @Test
    fun unavailableMemberRejectsTheWholeTransactionWithoutPartialSelection() {
        val initial = withCatalog(setOf(DataKey.Position))
        val transition = reducer.reduce(
            initial,
            SourceSelectionAction.ApplyAtomically(
                preferences = mapOf(
                    DataKey.Position to SourcePreference.Selected(source, SelectionReason.USER),
                    DataKey.SpeedOverGround to SourcePreference.Selected(source, SelectionReason.USER),
                ),
                selectedAtMillis = 10L,
            ),
        )

        assertEquals(SourceSelectionRejection.CANDIDATE_UNAVAILABLE, transition.rejection)
        assertEquals(initial, transition.state)
        assertFalse(transition.persistenceRequired)
    }

    @Test
    fun groupPreferencesAdvanceOneRevisionAndPersistAsOneTransition() {
        val initial = withCatalog(setOf(DataKey.Position, DataKey.SpeedOverGround))
        val preferences = mapOf(
            DataKey.Position to SourcePreference.Selected(source, SelectionReason.USER),
            DataKey.SpeedOverGround to SourcePreference.Selected(source, SelectionReason.USER),
            DataKey.CourseOverGround to SourcePreference.Disabled,
        )
        val transition = reducer.reduce(
            initial,
            SourceSelectionAction.ApplyAtomically(preferences, selectedAtMillis = 20L),
        )

        assertTrue(transition.persistenceRequired)
        assertEquals(initial.selectionRevision + 1L, transition.state.selectionRevision)
        assertEquals(preferences, transition.state.preferences.filterKeys(preferences::containsKey))
    }

    private fun withCatalog(keys: Set<DataKey>): SourceSelectionState {
        val candidates = keys.mapIndexed { index, key ->
            SourceCandidate(
                id = CandidateId(key, source),
                descriptor = SourceDescriptor(source, SourceKind.NMEA, "Bridge", "NMEA"),
                value = when (key) {
                    DataKey.Position -> MarineValue.Position(-36.8, 174.7)
                    else -> MarineValue.Decimal(4.0, if (key == DataKey.SpeedOverGround) MarineUnit.KNOTS else MarineUnit.DEGREES)
                },
                lastValidValue = when (key) {
                    DataKey.Position -> MarineValue.Position(-36.8, 174.7)
                    else -> MarineValue.Decimal(4.0, if (key == DataKey.SpeedOverGround) MarineUnit.KNOTS else MarineUnit.DEGREES)
                },
                availability = SourceCandidateAvailability.LIVE,
                ageMillis = 0L,
                receivedAtMillis = 0L,
                groupId = ObservationGroupId(index.toLong()),
                evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
            )
        }
        val catalog = SourceCatalogSnapshot(candidates, evaluatedAtMillis = 0L, revision = 1L)
        return reducer.reduce(
            SourceSelectionState.empty(),
            SourceSelectionAction.CatalogChanged(catalog, 0L, allowAutomaticSelection = false),
        ).state
    }
}

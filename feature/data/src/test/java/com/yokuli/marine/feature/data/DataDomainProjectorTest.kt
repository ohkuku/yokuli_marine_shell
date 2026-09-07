package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.phone.PHONE_SYSTEM_LOCATION_SOURCE
import com.yokuli.marine.data.phone.PhoneLocationDemandPolicy
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
import com.yokuli.marine.data.source.SourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataDomainProjectorTest {
    private val bridge = SourceIdentity(ConnectionId("bridge"))
    private val backup = SourceIdentity(ConnectionId("backup"))

    @Test
    fun groupCandidateProjectionKeepsSemanticCoverageAndActualEvidence() {
        val snapshot = snapshot(
            candidates = listOf(
                candidate(bridge, DataKey.Position, setOf("GPRMC", "GPGGA"), setOf("RMC", "GGA")),
                candidate(bridge, DataKey.SpeedOverGround, setOf("GPRMC"), setOf("RMC")),
            ),
        )

        val position = DataDomainProjector.project(snapshot, NmeaRuntimeSnapshot.EMPTY)
            .groups.single { it.group == SourceGroup.POSITION_AND_MOTION }
        val candidate = position.candidates.single()

        assertEquals(setOf(DataKey.Position, DataKey.SpeedOverGround), candidate.evidence.dataKeys)
        assertEquals(setOf("GPRMC", "GPGGA"), candidate.evidence.sentenceIds)
        assertEquals(setOf("RMC", "GGA"), candidate.evidence.formatters)
        assertEquals(setOf(DataKey.Position, DataKey.SpeedOverGround), candidate.selectableKeys)
    }

    @Test
    fun groupSelectionPlanIsOneAtomicCommandAndDisablesUnsupportedKeys() {
        val snapshot = snapshot(
            candidates = listOf(
                candidate(bridge, DataKey.Position),
                candidate(bridge, DataKey.SpeedOverGround),
            ),
        )

        val plan = SourceGroupSelectionAdapter.select(SourceGroup.POSITION_AND_MOTION, bridge, snapshot)
            as SourceGroupSelectionPlan.Ready
        val preferences = plan.command.preferences

        assertEquals(SourceGroup.POSITION_AND_MOTION.keys, preferences.keys)
        assertTrue(preferences[DataKey.Position] is SourcePreference.Selected)
        assertTrue(preferences[DataKey.SpeedOverGround] is SourcePreference.Selected)
        assertEquals(SourcePreference.Disabled, preferences[DataKey.CourseOverGround])
    }

    @Test
    fun selectedUnavailableSourceNeverSilentlyFailsOverToLiveBackup() {
        val decisions = listOf(
            decision(DataKey.Position, bridge, SourceDecisionStatus.SELECTED_UNAVAILABLE),
            decision(DataKey.SpeedOverGround, bridge, SourceDecisionStatus.SELECTED_UNAVAILABLE),
        )
        val snapshot = snapshot(
            candidates = listOf(
                candidate(bridge, DataKey.Position, availability = SourceCandidateAvailability.STALE),
                candidate(backup, DataKey.Position),
                candidate(backup, DataKey.SpeedOverGround),
            ),
            decisions = decisions,
        )

        val group = DataDomainProjector.project(snapshot, NmeaRuntimeSnapshot.EMPTY)
            .groups.single { it.group == SourceGroup.POSITION_AND_MOTION }

        assertEquals(SourceGroupStatus.SELECTED_UNAVAILABLE, group.status)
        assertEquals(bridge, group.selectedSource)
        assertFalse(group.flowWouldSelect(backup))
    }

    @Test
    fun mixedLegacyPerKeySelectionsRemainVisibleInsteadOfInventingOneGroupSource() {
        val snapshot = snapshot(
            candidates = listOf(candidate(bridge, DataKey.Position), candidate(backup, DataKey.SpeedOverGround)),
            decisions = listOf(
                decision(DataKey.Position, bridge, SourceDecisionStatus.USING),
                decision(DataKey.SpeedOverGround, backup, SourceDecisionStatus.USING),
            ),
        )

        val group = DataDomainProjector.project(snapshot, NmeaRuntimeSnapshot.EMPTY)
            .groups.single { it.group == SourceGroup.POSITION_AND_MOTION }

        assertEquals(SourceGroupStatus.MIXED_LEGACY_SELECTION, group.status)
        assertNull(group.selectedSource)
    }

    @Test
    fun phoneDemandExistsOnlyWhenPhoneIsActuallySelectedForOsData() {
        val demanded = snapshot(
            decisions = listOf(
                decision(DataKey.Position, PHONE_SYSTEM_LOCATION_SOURCE, SourceDecisionStatus.SELECTED_MISSING),
            ),
        )
        assertEquals(setOf(DataKey.Position), PhoneLocationDemandPolicy.resolve(demanded).requiredKeys)
        assertFalse(PhoneLocationDemandPolicy.resolve(snapshot()).required)
    }

    @Test
    fun apparentAndTrueWindRemainSeparateUserChoices() {
        assertEquals(
            setOf(
                DataKey.WindAngle(com.yokuli.marine.data.model.WindReference.APPARENT),
                DataKey.WindSpeed(com.yokuli.marine.data.model.WindSpeedReference.APPARENT),
            ),
            SourceGroup.APPARENT_WIND.keys,
        )
        assertEquals(
            setOf(
                DataKey.WindAngle(com.yokuli.marine.data.model.WindReference.TRUE_RELATIVE),
                DataKey.WindAngle(com.yokuli.marine.data.model.WindReference.TRUE_NORTH),
                DataKey.WindAngle(com.yokuli.marine.data.model.WindReference.MAGNETIC_NORTH),
                DataKey.WindSpeed(com.yokuli.marine.data.model.WindSpeedReference.TRUE),
            ),
            SourceGroup.TRUE_WIND.keys,
        )
        assertTrue(SourceGroup.APPARENT_WIND.keys.intersect(SourceGroup.TRUE_WIND.keys).isEmpty())
    }

    private fun SourceGroupState.flowWouldSelect(source: SourceIdentity): Boolean =
        selectedSource == source && status == SourceGroupStatus.USING

    private fun decision(key: DataKey, source: SourceIdentity, status: SourceDecisionStatus) = SourceDecision(
        key = key,
        status = status,
        selectedSource = source,
        reason = SelectionReason.USER,
        selectableCandidateCount = 1,
        needsReview = status != SourceDecisionStatus.USING,
    )

    private fun snapshot(
        candidates: List<SourceCandidate> = emptyList(),
        decisions: List<SourceDecision> = emptyList(),
    ) = MarineSourceSnapshot(
        sourceCatalog = SourceCatalogSnapshot(candidates, evaluatedAtMillis = 0L, revision = 1L),
        decisions = decisions,
        resolvedData = ResolvedDataSnapshot(emptyMap(), selectionRevision = 0L, evaluatedAtMillis = 0L),
        selectionRevision = 0L,
        revision = 1L,
        lastFailure = null,
    )

    private fun candidate(
        source: SourceIdentity,
        key: DataKey,
        sentences: Set<String> = setOf("GPRMC"),
        formatters: Set<String> = setOf("RMC"),
        availability: SourceCandidateAvailability = SourceCandidateAvailability.LIVE,
    ): SourceCandidate {
        val value = when (key) {
            DataKey.Position -> MarineValue.Position(-36.8, 174.7)
            DataKey.SpeedOverGround -> MarineValue.Decimal(4.2, MarineUnit.KNOTS)
            else -> error("fixture does not support $key")
        }
        return SourceCandidate(
            id = CandidateId(key, source),
            descriptor = SourceDescriptor(source, SourceKind.NMEA, source.connectionId.value, "NMEA"),
            value = value.takeIf { availability in setOf(SourceCandidateAvailability.LIVE, SourceCandidateAvailability.HELD) },
            lastValidValue = value,
            availability = availability,
            ageMillis = 0L,
            receivedAtMillis = 0L,
            groupId = ObservationGroupId(1L),
            evidence = SourceEvidence.Nmea(sentences, formatters),
        )
    }
}

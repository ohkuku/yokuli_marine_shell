package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineNervousSystemProjectionTest {
    private val gateway = SourceIdentity(ConnectionId("boat-gateway"))

    @Test
    fun boatOverviewAlwaysProjectsFourHumanSensorGroupsFromOneResolvedTruth() {
        val source = sourceSnapshot(
            resolved = listOf(
                resolved(DataKey.Position, MarineValue.Position(-36.84, 174.76)),
                resolved(DataKey.SpeedOverGround, MarineValue.Decimal(5.4, MarineUnit.KNOTS)),
            ),
        )

        val state = DataDomainProjector.project(source, NmeaRuntimeSnapshot.EMPTY)

        assertEquals(PrimaryDataArea.BOAT, state.primaryArea)
        assertEquals(
            listOf(BoatSensor.POSITION, BoatSensor.HEADING, BoatSensor.DEPTH, BoatSensor.WIND),
            state.boat.sensors.map { it.sensor },
        )
        assertEquals(SensorHealth.LIVE, state.boat.sensor(BoatSensor.POSITION).health)
        assertEquals(SensorHealth.UNAVAILABLE, state.boat.sensor(BoatSensor.DEPTH).health)
        assertSame(state.resolvedValues, state.boat.resolvedTruth)
    }

    @Test
    fun selectedUnavailableSourceBecomesAttentionWithoutBorrowingBackupEvidence() {
        val backup = SourceIdentity(ConnectionId("phone"))
        val stale = candidate(DataKey.Position, gateway, SourceCandidateAvailability.STALE)
        val liveBackup = candidate(DataKey.Position, backup, SourceCandidateAvailability.LIVE)
        val source = sourceSnapshot(
            candidates = listOf(stale, liveBackup),
            decisions = listOf(
                SourceDecision(
                    key = DataKey.Position,
                    status = SourceDecisionStatus.SELECTED_UNAVAILABLE,
                    selectedSource = gateway,
                    reason = SelectionReason.USER,
                    selectableCandidateCount = 1,
                    needsReview = true,
                ),
            ),
        )

        val position = DataDomainProjector.project(source, NmeaRuntimeSnapshot.EMPTY)
            .boat.sensor(BoatSensor.POSITION)

        assertEquals(SensorHealth.NEEDS_ATTENTION, position.health)
        assertEquals(gateway, position.selectedSource)
        assertFalse(position.resolvedValues.any { it.source == backup })
    }

    @Test
    fun consumerImpactUsesARegistryAndOnlyMarksActiveConsumers() {
        val source = sourceSnapshot(
            decisions = listOf(
                SourceDecision(
                    key = DataKey.Position,
                    status = SourceDecisionStatus.SELECTED_MISSING,
                    selectedSource = gateway,
                    reason = SelectionReason.USER,
                    selectableCandidateCount = 0,
                    needsReview = true,
                ),
            ),
        )
        val activity = MarineConsumerActivitySnapshot(
            activeConsumers = setOf(MarineConsumerId.NAVIGATION, MarineConsumerId.START_TILE),
        )

        val state = DataDomainProjector.project(source, NmeaRuntimeSnapshot.EMPTY, consumers = activity)

        assertEquals(
            setOf(MarineConsumerId.NAVIGATION, MarineConsumerId.START_TILE),
            state.consumerImpact.filter { it.active }.mapTo(linkedSetOf()) { it.consumerId },
        )
        assertTrue(
            state.consumerImpact.single { it.consumerId == MarineConsumerId.NAVIGATION }
                .affectedSensors.contains(BoatSensor.POSITION),
        )
        assertFalse(state.consumerImpact.single { it.consumerId == MarineConsumerId.CHART }.active)
    }

    @Test
    fun onlyBoatFlowAndConnectionsArePrimaryAreas() {
        assertEquals(
            listOf(PrimaryDataArea.BOAT, PrimaryDataArea.FLOW, PrimaryDataArea.CONNECTIONS),
            PrimaryDataArea.entries,
        )
        assertFalse(DataSurface.Diagnostics() is DataSurface.Primary)
        assertFalse(DataSurface.Sensor(BoatSensor.DEPTH) is DataSurface.Primary)
    }

    private fun DataBoatOverview.sensor(sensor: BoatSensor) = sensors.single { it.sensor == sensor }

    private fun sourceSnapshot(
        candidates: List<SourceCandidate> = emptyList(),
        decisions: List<SourceDecision> = emptyList(),
        resolved: List<ResolvedDatum> = emptyList(),
    ): MarineSourceSnapshot {
        val items = resolved.associateBy { it.key }
        return MarineSourceSnapshot(
            sourceCatalog = SourceCatalogSnapshot(candidates, 10L, 1L),
            decisions = decisions,
            resolvedData = ResolvedDataSnapshot(items, 1L, 10L),
            selectionRevision = 1L,
            revision = 1L,
            lastFailure = null,
        )
    }

    private fun resolved(key: DataKey, value: MarineValue): ResolvedDatum {
        val candidate = candidate(key, gateway, SourceCandidateAvailability.LIVE, value)
        return ResolvedDatum(
            key = key,
            source = gateway,
            candidate = candidate,
            value = value,
            availability = SourceCandidateAvailability.LIVE,
            selectionReason = SelectionReason.USER,
            selectionRevision = 1L,
        )
    }

    private fun candidate(
        key: DataKey,
        source: SourceIdentity,
        availability: SourceCandidateAvailability,
        value: MarineValue = MarineValue.Position(-36.84, 174.76),
    ) = SourceCandidate(
        id = CandidateId(key, source),
        descriptor = SourceDescriptor(source, SourceKind.NMEA, source.connectionId.value, "NMEA"),
        value = value.takeIf { availability == SourceCandidateAvailability.LIVE },
        lastValidValue = value,
        availability = availability,
        ageMillis = 0L,
        receivedAtMillis = 10L,
        groupId = ObservationGroupId(1L),
        evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
    )
}

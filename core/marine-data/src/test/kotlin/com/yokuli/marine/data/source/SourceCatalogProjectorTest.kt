package com.yokuli.marine.data.source

import com.yokuli.marine.data.catalog.Freshness
import com.yokuli.marine.data.catalog.FreshnessEvaluation
import com.yokuli.marine.data.catalog.ObservationCandidate
import com.yokuli.marine.data.catalog.ObservationCatalogSnapshot
import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ChecksumTrust
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineObservation
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.ObservationOrigin
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.phone.PhoneLocationFix
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.phone.PhoneLocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SourceCatalogProjectorTest {
    private val projector = SourceCatalogProjector()
    private val source = SourceIdentity(ConnectionId("gateway"))

    @Test
    fun sameSourceRmcAndGgaRemainOnePositionCandidateWithDeterministicEvidence() {
        val observation = observation(formatter = "GGA", value = MarineValue.Position(-36.8, 174.7))
        val nmeaCandidate = ObservationCandidate(
            id = CandidateId(DataKey.Position, source),
            sessionGeneration = SessionGeneration(1L),
            selectedObservation = observation,
            lastValidObservation = observation,
            freshness = FreshnessEvaluation(Freshness.LIVE, 0L, 1_000L),
            contributingFormatters = setOf("RMC", "GGA"),
            contributingSentenceIds = setOf("GPRMC", "GPGGA"),
            sourceAvailable = true,
            lastSeenMillis = 1_000L,
            retainedStreamCount = 2,
        )

        val result = projector.project(
            observationCatalog = observationSnapshot(nmeaCandidate),
            connectionNames = mapOf(ConnectionId("gateway") to "Boat gateway"),
            phone = PhoneLocationSnapshot.EMPTY,
            nowMillis = 1_000L,
        )

        val candidate = result.candidates.single()
        assertEquals(source, candidate.id.source)
        assertEquals(setOf("RMC", "GGA"), (candidate.evidence as SourceEvidence.Nmea).formatters)
        assertEquals("Boat gateway", candidate.descriptor.displayName)
        assertEquals(-36.8, (candidate.value as MarineValue.Position).latitudeDegrees, 0.0)
    }

    @Test
    fun phoneFixPublishesOnlyPlatformPresentFieldsAndNeverInventsZeroes() {
        val phone = PhoneLocationSnapshot(
            enabledByUser = true,
            permission = PhoneLocationPermission.APPROXIMATE,
            systemLocationEnabled = true,
            state = PhoneLocationState.RECEIVING,
            latestFix = PhoneLocationFix(
                latitudeDegrees = -36.8,
                longitudeDegrees = 174.7,
                speedKnots = null,
                courseOverGroundDegrees = null,
                horizontalAccuracyMeters = 850.0,
                receivedAtMillis = 2_000L,
                sourceTimeEpochMillis = 1_700_000_000_000L,
                provider = "network",
                sequence = 7L,
            ),
            revision = 2L,
        )

        val result = projector.project(
            observationCatalog = emptyObservationSnapshot(),
            connectionNames = emptyMap(),
            phone = phone,
            nowMillis = 2_000L,
        )

        assertEquals(setOf(DataKey.Position, DataKey.PositionAccuracy), result.candidates.map { it.id.key }.toSet())
        assertFalse(result.candidates.any { it.id.key == DataKey.SpeedOverGround })
        assertFalse(result.candidates.any { it.id.key == DataKey.CourseOverGround })
        assertEquals(SourceKind.PHONE_SYSTEM_LOCATION, result.candidates.first().descriptor.kind)
    }

    @Test
    fun phonePermissionFailureDoesNotRemoveNmeaCandidatesOrPublishFakePhonePosition() {
        val nmea = observation("RMC", MarineValue.Position(-36.9, 174.8))
        val phone = PhoneLocationSnapshot(
            enabledByUser = true,
            permission = PhoneLocationPermission.DENIED,
            systemLocationEnabled = true,
            state = PhoneLocationState.PERMISSION_REQUIRED,
            latestFix = null,
            revision = 1L,
        )

        val result = projector.project(
            observationCatalog = observationSnapshot(
                ObservationCandidate(
                    id = CandidateId(DataKey.Position, source),
                    sessionGeneration = SessionGeneration(1L),
                    selectedObservation = nmea,
                    lastValidObservation = nmea,
                    freshness = FreshnessEvaluation(Freshness.LIVE, 0L, 1_000L),
                    contributingFormatters = setOf("RMC"),
                    contributingSentenceIds = setOf("GPRMC"),
                    sourceAvailable = true,
                    lastSeenMillis = 1_000L,
                    retainedStreamCount = 1,
                ),
            ),
            connectionNames = mapOf(ConnectionId("gateway") to "Gateway"),
            phone = phone,
            nowMillis = 1_000L,
        )

        assertEquals(1, result.candidates.size)
        assertEquals(source, result.candidates.single().id.source)
        assertNull(result.candidates.singleOrNull { it.descriptor.kind == SourceKind.PHONE_SYSTEM_LOCATION })
    }

    private fun observation(formatter: String, value: MarineValue) = MarineObservation(
        key = DataKey.Position,
        value = value,
        validity = ObservationValidity.VALID,
        origin = ObservationOrigin(source, SessionGeneration(1L), "GP", formatter),
        measuredAtMillis = 1_000L,
        groupId = ObservationGroupId(1L),
        checksumTrust = ChecksumTrust.VERIFIED,
    )

    private fun observationSnapshot(candidate: ObservationCandidate) = ObservationCatalogSnapshot(
        candidates = listOf(candidate),
        evaluatedAtMillis = 1_000L,
        capacityEvictionCount = 0L,
        capacityRejectionCount = 0L,
        staleSessionDropCount = 0L,
        outOfOrderDropCount = 0L,
        streamCapacityEvictionCount = 0L,
        streamCapacityRejectionCount = 0L,
        streamHighWaterMark = 2,
    )

    private fun emptyObservationSnapshot() = observationSnapshotEmpty(2_000L)
}

private fun observationSnapshotEmpty(now: Long) = ObservationCatalogSnapshot(
    candidates = emptyList(),
    evaluatedAtMillis = now,
    capacityEvictionCount = 0L,
    capacityRejectionCount = 0L,
    staleSessionDropCount = 0L,
    outOfOrderDropCount = 0L,
    streamCapacityEvictionCount = 0L,
    streamCapacityRejectionCount = 0L,
    streamHighWaterMark = 0,
)

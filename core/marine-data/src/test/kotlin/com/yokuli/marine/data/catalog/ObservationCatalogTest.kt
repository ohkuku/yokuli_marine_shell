package com.yokuli.marine.data.catalog

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
import com.yokuli.marine.data.session.ActiveSessionRegistry
import com.yokuli.marine.data.time.MonotonicClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationCatalogTest {
    private val clock = CatalogClock(1_000L)

    @Test
    fun sameOriginRmcAndGgaAreNotSeparateDevices() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)

        catalog.record(position(source, "GP", "RMC", 1L, 1.0))
        catalog.record(position(source, "GP", "GGA", 2L, 2.0))
        catalog.record(position(source, "GN", "GLL", 3L, 3.0))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(CandidateId(DataKey.Position, source), candidate.id)
        assertEquals(setOf("RMC", "GGA", "GLL"), candidate.contributingFormatters)
        assertEquals(setOf("GPRMC", "GPGGA", "GNGLL"), candidate.contributingSentenceIds)
        assertEquals("GGA", candidate.selectedObservation.origin.formatter)
        assertEquals(2.0, candidate.selectedObservation.positionLatitude(), 0.0)
    }

    @Test
    fun deterministicSentencePriorityBeatsLastWriterWithinOneFreshnessTier() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)

        catalog.record(position(source, "GP", "GGA", 100L, 10.0))
        catalog.record(position(source, "GP", "RMC", 900L, 20.0))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals("GGA", candidate.selectedObservation.origin.formatter)
        assertEquals(10.0, candidate.selectedObservation.positionLatitude(), 0.0)
    }

    @Test
    fun newerExplicitInvalidBeatsOlderValidComplementaryEvidence() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(position(source, "GP", "GGA", 900L, 10.0))
        catalog.record(invalidPosition(source, "GP", "RMC", 1_000L))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.INVALID, candidate.freshness.state)
        assertEquals(ObservationValidity.EXPLICIT_INVALID, candidate.selectedObservation.validity)
        assertEquals("RMC", candidate.selectedObservation.origin.formatter)
        assertEquals(10.0, candidate.lastValidObservation.positionLatitude(), 0.0)
    }

    @Test
    fun olderInvalidDoesNotBeatNewerValidComplementaryEvidence() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(invalidPosition(source, "GP", "RMC", 800L))
        catalog.record(position(source, "GP", "GGA", 900L, 11.0))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.LIVE, candidate.freshness.state)
        assertEquals(ObservationValidity.VALID, candidate.selectedObservation.validity)
        assertEquals("GGA", candidate.selectedObservation.origin.formatter)
        assertEquals(11.0, candidate.selectedObservation.positionLatitude(), 0.0)
    }

    @Test
    fun explicitInvalidWinsWhenValidEvidenceHasTheSameMonotonicTimestamp() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(position(source, "GP", "GGA", 900L, 11.0, group = 8))
        catalog.record(invalidPosition(source, "GP", "RMC", 900L, group = 9))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.INVALID, candidate.freshness.state)
        assertEquals(ObservationValidity.EXPLICIT_INVALID, candidate.selectedObservation.validity)
        assertEquals(ObservationGroupId(9), candidate.selectedObservation.groupId)
    }

    @Test
    fun newerGroupAtSameTimestampRecoversFromEarlierInvalid() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(invalidPosition(source, "GP", "RMC", 900L, group = 8))
        catalog.record(position(source, "GP", "GGA", 900L, 11.0, group = 9))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.LIVE, candidate.freshness.state)
        assertEquals(ObservationValidity.VALID, candidate.selectedObservation.validity)
        assertEquals(ObservationGroupId(9), candidate.selectedObservation.groupId)
    }

    @Test
    fun newValidEvidenceRecoversWithoutResurrectingPreInvalidPreferredEvidence() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(position(source, "GP", "GGA", 700L, 10.0))
        catalog.record(invalidPosition(source, "GP", "RMC", 800L))
        catalog.record(position(source, "GN", "GLL", 900L, 12.0))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.LIVE, candidate.freshness.state)
        assertEquals("GLL", candidate.selectedObservation.origin.formatter)
        assertEquals(12.0, candidate.selectedObservation.positionLatitude(), 0.0)
    }

    @Test
    fun liveComplementaryEvidenceBeatsHeldPreferredEvidence() {
        clock.now = 4_000L
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(position(source, "GP", "GGA", 500L, 10.0))
        catalog.record(position(source, "GP", "RMC", 3_999L, 20.0))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.LIVE, candidate.freshness.state)
        assertEquals("RMC", candidate.selectedObservation.origin.formatter)
        assertEquals(20.0, candidate.selectedObservation.positionLatitude(), 0.0)
    }

    @Test
    fun eachDataKeyKeepsItsOwnTimestampAndFreshness() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(position(source, "GP", "RMC", 0L, 10.0))
        catalog.record(speed(source, "GP", "RMC", 999L, 4.0))

        clock.now = 3_001L
        val byKey = catalog.snapshot().candidates.associateBy { it.id.key }
        assertEquals(Freshness.HELD, byKey.getValue(DataKey.Position).freshness.state)
        assertEquals(Freshness.LIVE, byKey.getValue(DataKey.SpeedOverGround).freshness.state)
    }

    @Test
    fun quietReplacementSessionMakesOldEvidenceUnavailableAndRejectsLateCallback() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(position(source, "GP", "GGA", 800L, 1.0, generation = 1L))

        sessions.begin(source, 2)
        var candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.UNAVAILABLE, candidate.freshness.state)
        assertEquals(SessionGeneration(1), candidate.sessionGeneration)
        catalog.record(position(source, "GP", "GGA", 900L, 3.0, generation = 1L))
        assertEquals(1L, catalog.snapshot().staleSessionDropCount)

        catalog.record(position(source, "GP", "RMC", 950L, 2.0, generation = 2L))
        candidate = catalog.snapshot().candidates.single()
        assertEquals(SessionGeneration(2), candidate.sessionGeneration)
        assertEquals(setOf("RMC"), candidate.contributingFormatters)
        assertEquals(2.0, candidate.selectedObservation.positionLatitude(), 0.0)
    }

    @Test
    fun firstObservationFromNewSessionMakesUntouchedOldDataUnavailable() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(speed(source, "GP", "VTG", 800L, 4.0, generation = 1L))
        sessions.begin(source, 2)
        catalog.record(position(source, "GP", "RMC", 900L, 2.0, generation = 2L))

        val byKey = catalog.snapshot().candidates.associateBy { it.id.key }
        assertEquals(Freshness.UNAVAILABLE, byKey.getValue(DataKey.SpeedOverGround).freshness.state)
        assertEquals(Freshness.LIVE, byKey.getValue(DataKey.Position).freshness.state)
    }

    @Test
    fun outOfOrderObservationFromSameSentenceCannotRollAValueBack() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(speed(source, "GP", "VTG", 900L, 8.0, group = 9))
        catalog.record(speed(source, "GP", "VTG", 900L, 2.0, group = 8))

        val snapshot = catalog.snapshot()
        assertEquals(8.0, snapshot.candidates.single().selectedObservation.decimalValue(), 0.0)
        assertEquals(1L, snapshot.outOfOrderDropCount)
    }

    @Test
    fun endedSourceBecomesUnavailableWithoutDeletingCandidateHistory() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, 1)
        val catalog = ObservationCatalog(clock, sessions)
        catalog.record(position(source, "GP", "RMC", 999L, 1.0))

        sessions.endSession(source.connectionId, SessionGeneration(1))

        val candidate = catalog.snapshot().candidates.single()
        assertEquals(Freshness.UNAVAILABLE, candidate.freshness.state)
        assertEquals(1.0, candidate.lastValidObservation.positionLatitude(), 0.0)
    }

    @Test
    fun candidateCapacityIsBoundedAndProtectedCandidatesSurviveEviction() {
        val sessions = ActiveSessionRegistry()
        val one = SourceIdentity(ConnectionId("one"))
        val two = SourceIdentity(ConnectionId("two"))
        val three = SourceIdentity(ConnectionId("three"))
        listOf(one, two, three).forEach { sessions.begin(it, 1) }
        val catalog = ObservationCatalog(clock, sessions, maxCandidates = 2)
        val protected = CandidateId(DataKey.Position, one)
        catalog.record(position(one, "GP", "RMC", 1L, 1.0))
        catalog.record(position(two, "GP", "RMC", 2L, 2.0))
        catalog.record(position(three, "GP", "RMC", 3L, 3.0), protectedCandidates = setOf(protected))

        val snapshot = catalog.snapshot()
        assertEquals(2, snapshot.candidates.size)
        assertTrue(snapshot.candidates.any { it.id == protected })
        assertTrue(snapshot.candidates.any { it.id.source == three })
        assertEquals(1L, snapshot.capacityEvictionCount)
    }

    @Test
    fun manyEndedOriginsEvictCatalogEntriesButAllMemoryRemainsBounded() {
        val sessions = ActiveSessionRegistry(maxActiveConnections = 1)
        val catalog = ObservationCatalog(clock, sessions, maxCandidates = 4)

        repeat(1_000) { index ->
            val source = SourceIdentity(ConnectionId("source-$index"))
            sessions.begin(source, 1)
            catalog.record(position(source, "GP", "RMC", index.toLong(), index % 90 + 0.1))
            sessions.endSession(source.connectionId, SessionGeneration(1))
            sessions.forgetConnection(source.connectionId)
        }

        assertEquals(4, catalog.snapshot().candidates.size)
        assertEquals(996L, catalog.snapshot().capacityEvictionCount)
        assertTrue(sessions.snapshot().activeSessions.isEmpty())
        assertEquals(1, sessions.snapshot().highWaterMark)
    }

    private fun position(
        source: SourceIdentity,
        talker: String,
        formatter: String,
        at: Long,
        latitude: Double,
        generation: Long = 1L,
        group: Long = at,
    ) = MarineObservation(
        key = DataKey.Position,
        value = MarineValue.Position(latitude, 174.0),
        validity = ObservationValidity.VALID,
        origin = ObservationOrigin(source, SessionGeneration(generation), talker, formatter),
        measuredAtMillis = at,
        groupId = ObservationGroupId(group),
        checksumTrust = ChecksumTrust.VERIFIED,
    )

    private fun invalidPosition(
        source: SourceIdentity,
        talker: String,
        formatter: String,
        at: Long,
        group: Long = at,
    ) = MarineObservation(
        key = DataKey.Position,
        value = null,
        validity = ObservationValidity.EXPLICIT_INVALID,
        origin = ObservationOrigin(source, SessionGeneration(1), talker, formatter),
        measuredAtMillis = at,
        groupId = ObservationGroupId(group),
        checksumTrust = ChecksumTrust.VERIFIED,
    )

    private fun speed(
        source: SourceIdentity,
        talker: String,
        formatter: String,
        at: Long,
        knots: Double,
        generation: Long = 1L,
        group: Long = at,
    ) = MarineObservation(
        key = DataKey.SpeedOverGround,
        value = MarineValue.Decimal(knots, MarineUnit.KNOTS),
        validity = ObservationValidity.VALID,
        origin = ObservationOrigin(source, SessionGeneration(generation), talker, formatter),
        measuredAtMillis = at,
        groupId = ObservationGroupId(group),
        checksumTrust = ChecksumTrust.VERIFIED,
    )
}

private class CatalogClock(var now: Long) : MonotonicClock {
    override fun nowMillis(): Long = now
}

private fun ActiveSessionRegistry.begin(source: SourceIdentity, generation: Long) {
    beginSession(source.connectionId, SessionGeneration(generation))
}

private fun MarineObservation?.positionLatitude(): Double =
    ((this?.value) as MarineValue.Position).latitudeDegrees

private fun MarineObservation.decimalValue(): Double =
    (value as MarineValue.Decimal).value

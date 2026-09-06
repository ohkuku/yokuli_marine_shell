package com.yokuli.marine.shell

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
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
import com.yokuli.marine.map.domain.PositionPortEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yokuli.marine.navigation.domain.NavigationInputStatus

@OptIn(ExperimentalCoroutinesApi::class)
class MarineSourcePositionPortTest {
    private val sourceA = SourceIdentity(ConnectionId("source-a"))
    private val sourceB = SourceIdentity(ConnectionId("source-b"))

    @Test
    fun resolvedPositionAndAtomicMotionProjectToNavigationWithoutEndpointDisclosure() = runTest {
        val snapshots = MutableStateFlow(MarineSourceSnapshot.EMPTY)
        val port = MarineSourceNavigationInputPort(snapshots, this)
        val position = candidate(DataKey.Position, sourceA, MarineValue.Position(-36.8, 174.7), 120L, 7L)
        val speed = candidate(DataKey.SpeedOverGround, sourceB, MarineValue.Decimal(6.2, MarineUnit.KNOTS), 121L, 8L)
        val course = candidate(DataKey.CourseOverGround, sourceB, MarineValue.Decimal(42.0, MarineUnit.DEGREES), 121L, 8L)

        snapshots.value = snapshot(position, resolvedExtras = listOf(speed, course))
        runCurrent()

        val fix = port.state.value
        assertEquals(NavigationInputStatus.LIVE, fix.positionStatus)
        assertEquals(-36.8, requireNotNull(fix.position).latitude, 0.0)
        assertEquals(6.2, requireNotNull(fix.speedOverGroundKnots), 0.0)
        assertEquals(42.0, requireNotNull(fix.courseOverGroundTrueDegrees), 0.0)
        assertFalse(requireNotNull(fix.positionSourceId).contains("source-a"))
        assertFalse(requireNotNull(fix.motionSourceId).contains("source-b"))
    }

    @Test
    fun selectedLivePositionFlowsToChartWithMonotonicIdentityAndSameSourceAccuracy() = runTest {
        val state = MutableStateFlow(MarineSourceSnapshot.EMPTY)
        val port = MarineSourcePositionPort(state, sourceEpoch = "boot-1")
        val events = mutableListOf<PositionPortEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { port.events.collect(events::add) }

        state.value = snapshot(
            position = candidate(DataKey.Position, sourceA, MarineValue.Position(-36.8, 174.7), 120L, 7L),
            accuracy = candidate(DataKey.PositionAccuracy, sourceA, MarineValue.Decimal(8.0, MarineUnit.METERS), 120L, 7L),
        )
        runCurrent()

        assertTrue(events[0] is PositionPortEvent.SourceConnected)
        val observation = (events[1] as PositionPortEvent.Position).value
        assertEquals("boot-1", observation.identity.receivedAt.bootId)
        assertEquals(120L, observation.identity.receivedAt.elapsedRealtimeMillis)
        assertEquals(8.0, observation.horizontalAccuracyMeters ?: -1.0, 0.0)
        assertFalse(observation.identity.source.sourceId.contains("source-a"))
    }

    @Test
    fun selectedStaleSourceDisconnectsWithoutSilentlySwitchingToLiveAlternative() = runTest {
        val state = MutableStateFlow(MarineSourceSnapshot.EMPTY)
        val port = MarineSourcePositionPort(state, "boot-1")
        val events = mutableListOf<PositionPortEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { port.events.collect(events::add) }
        state.value = snapshot(candidate(DataKey.Position, sourceB, MarineValue.Position(-36.8, 174.7), 100L, 1L))
        runCurrent()
        events.clear()

        val staleB = candidate(
            DataKey.Position,
            sourceB,
            value = null,
            receivedAt = 100L,
            group = 1L,
            availability = SourceCandidateAvailability.STALE,
            lastValidValue = MarineValue.Position(-36.8, 174.7),
        )
        val liveA = candidate(DataKey.Position, sourceA, MarineValue.Position(-36.7, 174.8), 200L, 2L)
        state.value = snapshot(staleB, alternatives = listOf(liveA), revision = 2L)
        runCurrent()

        assertEquals(1, events.size)
        assertTrue(events.single() is PositionPortEvent.SourceDisconnected)
        assertFalse(events.any { it is PositionPortEvent.SourceConnected })
    }

    @Test
    fun sourceSwitchIsOrderedAndNeverMixesAccuracyFromAnotherSelectedSourceOrRepeatsAFrame() = runTest {
        val state = MutableStateFlow(MarineSourceSnapshot.EMPTY)
        val port = MarineSourcePositionPort(state, "boot-1")
        val events = mutableListOf<PositionPortEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { port.events.collect(events::add) }
        state.value = snapshot(candidate(DataKey.Position, sourceA, MarineValue.Position(-36.8, 174.7), 100L, 1L))
        runCurrent()
        events.clear()

        val positionB = candidate(DataKey.Position, sourceB, MarineValue.Position(-36.7, 174.9), 200L, 2L)
        val accuracyA = candidate(DataKey.PositionAccuracy, sourceA, MarineValue.Decimal(3.0, MarineUnit.METERS), 200L, 2L)
        state.value = snapshot(positionB, accuracyA, revision = 2L)
        runCurrent()

        assertTrue(events[0] is PositionPortEvent.SourceDisconnected)
        assertTrue(events[1] is PositionPortEvent.SourceConnected)
        val position = (events[2] as PositionPortEvent.Position).value
        assertNull(position.horizontalAccuracyMeters)
        events.clear()

        state.value = snapshot(positionB, accuracyA, revision = 3L)
        runCurrent()
        assertTrue(events.isEmpty())
    }

    private fun snapshot(
        position: SourceCandidate,
        accuracy: SourceCandidate? = null,
        alternatives: List<SourceCandidate> = emptyList(),
        resolvedExtras: List<SourceCandidate> = emptyList(),
        revision: Long = 1L,
    ): MarineSourceSnapshot {
        val selected = listOfNotNull(position, accuracy) + resolvedExtras
        val resolved = selected.associate { candidate ->
            candidate.id.key to ResolvedDatum(
                key = candidate.id.key,
                source = candidate.id.source,
                candidate = candidate,
                value = candidate.value,
                availability = candidate.availability,
                selectionReason = SelectionReason.USER,
                selectionRevision = revision,
            )
        }
        val decisions = selected.map { candidate ->
            SourceDecision(
                key = candidate.id.key,
                status = if (candidate.availability in setOf(SourceCandidateAvailability.LIVE, SourceCandidateAvailability.HELD)) {
                    SourceDecisionStatus.USING
                } else {
                    SourceDecisionStatus.SELECTED_UNAVAILABLE
                },
                selectedSource = candidate.id.source,
                reason = SelectionReason.USER,
                selectableCandidateCount = 1 + alternatives.count { it.id.key == candidate.id.key },
                needsReview = false,
            )
        }
        return MarineSourceSnapshot(
            sourceCatalog = SourceCatalogSnapshot((selected + alternatives).distinctBy { it.id }, 250L, revision),
            decisions = decisions,
            resolvedData = ResolvedDataSnapshot(resolved, revision, 250L),
            selectionRevision = revision,
            revision = revision,
            lastFailure = null,
        )
    }

    private fun candidate(
        key: DataKey,
        source: SourceIdentity,
        value: MarineValue?,
        receivedAt: Long,
        group: Long,
        availability: SourceCandidateAvailability = SourceCandidateAvailability.LIVE,
        lastValidValue: MarineValue? = value,
    ) = SourceCandidate(
        id = CandidateId(key, source),
        descriptor = SourceDescriptor(source, SourceKind.NMEA, "Gateway", "selected channel"),
        value = value,
        lastValidValue = lastValidValue,
        availability = availability,
        ageMillis = 0L,
        receivedAtMillis = receivedAt,
        groupId = ObservationGroupId(group),
        evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
    )
}

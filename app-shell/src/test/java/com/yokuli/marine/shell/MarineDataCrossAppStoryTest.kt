package com.yokuli.marine.shell

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.HeadingReference as MarineHeadingReference
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
import com.yokuli.marine.data.source.SourceDescriptor
import com.yokuli.marine.data.source.SourceEvidence
import com.yokuli.marine.data.source.SourceKind
import com.yokuli.marine.feature.chart.PositionObservationCoordinator
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.MonotonicTime
import com.yokuli.marine.map.domain.ObservationMonotonicClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarineDataCrossAppStoryTest {
    private val source = SourceIdentity(ConnectionId("selected-gateway"))

    @Test
    fun selectedPositionHeadingAndAtomicCourseSpeedReachTheExistingChartConsumer() = runTest {
        val sourceState = MutableStateFlow(MarineSourceSnapshot.EMPTY)
        val store = RecordingMapStore()
        val coordinator = PositionObservationCoordinator(
            port = MarineSourcePositionPort(sourceState, "boot-1"),
            mapStore = store,
            scope = backgroundScope,
            clock = ObservationMonotonicClock { MonotonicTime("boot-1", 500L) },
            freshnessTickMillis = Long.MAX_VALUE,
        )

        sourceState.value = snapshot(
            candidate(DataKey.Position, MarineValue.Position(-36.8, 174.7), group = 10L),
            candidate(
                DataKey.Heading(MarineHeadingReference.TRUE),
                MarineValue.Decimal(123.0, MarineUnit.DEGREES),
                group = 11L,
            ),
            candidate(DataKey.CourseOverGround, MarineValue.Decimal(140.0, MarineUnit.DEGREES), group = 12L),
            candidate(DataKey.SpeedOverGround, MarineValue.Decimal(6.5, MarineUnit.KNOTS), group = 12L),
        )
        runCurrent()

        val heading = store.actions.filterIsInstance<MapAction.ObserveHeading>().single().observation
        assertEquals(123.0, heading.degrees, 0.0)
        val course = store.actions.filterIsInstance<MapAction.ObserveCourseSpeed>().single().observation
        assertEquals(140.0, course.courseOverGroundTrueDegrees ?: -1.0, 0.0)
        assertEquals(6.5, course.speedOverGroundKnots ?: -1.0, 0.0)
        coordinator.close()
    }

    @Test
    fun chartBridgeNeverCombinesCourseAndSpeedFromDifferentFrames() = runTest {
        val sourceState = MutableStateFlow(MarineSourceSnapshot.EMPTY)
        val store = RecordingMapStore()
        val coordinator = PositionObservationCoordinator(
            port = MarineSourcePositionPort(sourceState, "boot-1"),
            mapStore = store,
            scope = backgroundScope,
            clock = ObservationMonotonicClock { MonotonicTime("boot-1", 500L) },
            freshnessTickMillis = Long.MAX_VALUE,
        )

        sourceState.value = snapshot(
            candidate(DataKey.Position, MarineValue.Position(-36.8, 174.7), group = 20L),
            candidate(DataKey.CourseOverGround, MarineValue.Decimal(140.0, MarineUnit.DEGREES), group = 21L),
            candidate(DataKey.SpeedOverGround, MarineValue.Decimal(6.5, MarineUnit.KNOTS), group = 22L),
        )
        runCurrent()

        val course = store.actions.filterIsInstance<MapAction.ObserveCourseSpeed>().single().observation
        assertNull(course.courseOverGroundTrueDegrees)
        assertEquals(6.5, course.speedOverGroundKnots ?: -1.0, 0.0)
        coordinator.close()
    }

    private fun snapshot(vararg candidates: SourceCandidate): MarineSourceSnapshot {
        val resolved = candidates.associate { candidate ->
            candidate.id.key to ResolvedDatum(
                key = candidate.id.key,
                source = candidate.id.source,
                candidate = candidate,
                value = candidate.value,
                availability = candidate.availability,
                selectionReason = SelectionReason.USER,
                selectionRevision = 1L,
            )
        }
        return MarineSourceSnapshot(
            sourceCatalog = SourceCatalogSnapshot(candidates.toList(), 500L, 1L),
            decisions = emptyList(),
            resolvedData = ResolvedDataSnapshot(resolved, 1L, 500L),
            selectionRevision = 1L,
            revision = 1L,
            lastFailure = null,
        )
    }

    private fun candidate(key: DataKey, value: MarineValue, group: Long) = SourceCandidate(
        id = CandidateId(key, source),
        descriptor = SourceDescriptor(source, SourceKind.NMEA, "Gateway", "selected channel"),
        value = value,
        lastValidValue = value,
        availability = SourceCandidateAvailability.LIVE,
        ageMillis = 0L,
        receivedAtMillis = 100L + group,
        groupId = ObservationGroupId(group),
        evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
    )

    private class RecordingMapStore : MapStore {
        override val state: StateFlow<MapState> = MutableStateFlow(MapState())
        val actions = mutableListOf<MapAction>()

        override fun dispatch(action: MapAction): MapDispatchResult {
            actions += action
            return MapDispatchResult.ACCEPTED
        }

        override fun close() = Unit
    }
}

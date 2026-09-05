package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionObservationContractTest {
    private val point = GeoPoint(-36.8485, 174.7633)
    private val source = ObservationSource("phone", "session-1")
    private val now = MonotonicTime("boot-a", 10_000L)

    @Test
    fun `default is explicit NoSource and ordinary map work remains available`() {
        val initial = MapState()
        val selected = DefaultMapReducer().reduce(initial, MapAction.LongPressMap(point)).state

        assertEquals(PositionSourceStatus.NoSource, selected.position.sourceStatus)
        assertEquals(PositionAvailability.UNAVAILABLE, selected.position.availability)
        assertEquals(point, selected.selection?.point)
    }

    @Test
    fun `identity dedupes cached delivery but a new epoch accepts the same id`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("same", 1, 9_000L), now)).state
        val duplicate = reducer.reduce(
            state,
            MapAction.ObservePosition(fix("same", 1, 9_900L, GeoPoint(-36.7, 174.9)), now),
        ).state
        assertEquals(point, duplicate.position.observation?.point)

        val restarted = ObservationSource("phone", "session-2")
        state = reducer.reduce(duplicate, MapAction.PositionSourceConnected(restarted)).state
        val accepted = reducer.reduce(
            state,
            MapAction.ObservePosition(fix("same", 1, 9_950L, GeoPoint(-36.7, 174.9), restarted), now),
        ).state
        assertEquals(GeoPoint(-36.7, 174.9), accepted.position.observation?.point)
    }

    @Test
    fun `a new identity at the same coordinate refreshes monotonic age`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("first", 1, 1_000L), MonotonicTime("boot-a", 31_001L))).state
        assertEquals(PositionAvailability.STALE, state.position.availability)

        state = reducer.reduce(state, MapAction.ObservePosition(fix("second", 2, 31_000L), MonotonicTime("boot-a", 31_001L))).state
        assertEquals(point, state.position.observation?.point)
        assertEquals("second", state.position.observation?.identity?.observationId)
        assertEquals(PositionAvailability.FRESH, state.position.availability)
    }

    @Test
    fun `monotonic age controls freshness and a different boot is never fresh`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("a", 1, 9_000L), now)).state
        state = reducer.reduce(state, MapAction.ObserveHeading(heading("h", 1, 9_000L), now)).state
        state = reducer.reduce(state, MapAction.PositionClockTick(MonotonicTime("boot-a", 20_001L))).state

        assertEquals(PositionAvailability.FRESH, state.position.availability)
        assertEquals(PositionAvailability.STALE, state.position.headingAvailability)

        val otherBoot = reducer.reduce(
            state,
            MapAction.ObservePosition(
                fix("boot-b", 2, 1L).copy(identity = identity("boot-b", 2, 1L, bootId = "boot-b")),
                MonotonicTime("boot-a", 20_002L),
            ),
        ).state
        assertEquals(PositionAvailability.STALE, otherBoot.position.availability)
    }

    @Test
    fun `future and out of order monotonic samples cannot replace the last good fix`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("good", 5, 9_000L), now)).state

        val old = reducer.reduce(state, MapAction.ObservePosition(fix("old", 4, 8_000L), now))
        val future = reducer.reduce(state, MapAction.ObservePosition(fix("future", 6, 11_000L), now))

        assertEquals("good", old.state.position.observation?.identity?.observationId)
        assertEquals("good", future.state.position.observation?.identity?.observationId)
        assertTrue(old.effects.single() is MapEffect.LogIncident)
        assertTrue(future.effects.single() is MapEffect.LogIncident)
    }

    @Test
    fun `disconnect retains the last point but renderer makes it historical and stops vectors`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("p", 1, 9_000L, accuracy = 12.0), now)).state
        state = reducer.reduce(state, MapAction.ObserveHeading(heading("h", 2, 9_100L), now)).state
        state = reducer.reduce(state, MapAction.ObserveCourseSpeed(course("c", 3, 9_200L), now)).state
        state = reducer.reduce(state, MapAction.PositionSourceDisconnected(source, now)).state

        val render = PositionRenderPolicy.resolve(state.position)
        assertEquals(point, state.position.observation?.point)
        assertEquals(VesselMarkerStyle.HISTORICAL, render.markerStyle)
        assertNull(render.trueHeadingDegrees)
        assertNull(render.courseVector)
        assertNull(render.accuracyMeters)
    }

    @Test
    fun `true heading is distinct from magnetic heading and COG`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("p", 1, 9_000L), now)).state
        state = reducer.reduce(
            state,
            MapAction.ObserveHeading(heading("mag", 2, 9_100L, HeadingReference.MAGNETIC), now),
        ).state
        state = reducer.reduce(state, MapAction.ObserveCourseSpeed(course("c", 3, 9_200L), now)).state

        val withoutVariation = PositionRenderPolicy.resolve(state.position)
        assertEquals(VesselMarkerStyle.LIVE_NEUTRAL, withoutVariation.markerStyle)
        assertNull(withoutVariation.trueHeadingDegrees)
        assertEquals(120.0, withoutVariation.courseVector?.trueDegrees)

        state = reducer.reduce(state, MapAction.ObserveHeading(heading("true", 4, 9_300L), now)).state
        val withTrue = PositionRenderPolicy.resolve(state.position)
        assertEquals(VesselMarkerStyle.LIVE_TRUE_HEADING, withTrue.markerStyle)
        assertEquals(45.0, withTrue.trueHeadingDegrees)
        assertFalse(withTrue.trueHeadingDegrees == withTrue.courseVector?.trueDegrees)
    }

    @Test
    fun `source switching never combines the new fix with old-source heading`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("old-fix", 1, 9_000L), now)).state
        state = reducer.reduce(state, MapAction.ObserveHeading(heading("old-heading", 2, 9_100L), now)).state
        val secondSource = ObservationSource("usb", "session-1")
        state = reducer.reduce(state, MapAction.PositionSourceConnected(secondSource)).state
        state = reducer.reduce(
            state,
            MapAction.ObservePosition(fix("new-fix", 1, 9_500L, source = secondSource), now),
        ).state

        val render = PositionRenderPolicy.resolve(state.position)
        assertEquals(VesselMarkerStyle.LIVE_NEUTRAL, render.markerStyle)
        assertNull(render.trueHeadingDegrees)
    }

    @Test
    fun `UTC changes do not alter monotonic freshness and historical snapshots are not persisted as live`() {
        val earlyUtc = fix("utc-a", 1, 9_000L).copy(
            identity = identity("utc-a", 1, 9_000L).copy(
                sampledAtUtcMillis = 2_000_000L,
                sampleTimeConfidence = SampleTimeConfidence.REPORTED_UTC,
            ),
        )
        val lateUtc = earlyUtc.copy(
            identity = earlyUtc.identity.copy(
                observationId = "utc-b",
                sequence = 2,
                sampledAtUtcMillis = 1_000_000L,
                receivedAt = MonotonicTime("boot-a", 9_500L),
            ),
        )
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(earlyUtc, now)).state
        state = reducer.reduce(state, MapAction.ObservePosition(lateUtc, now)).state

        assertEquals(PositionAvailability.FRESH, state.position.availability)
        assertEquals("utc-b", state.position.observation?.identity?.observationId)
        assertNull(state.persisted().positionObservation)
    }

    @Test
    fun `unknown accuracy and low speed COG never draw invented confidence`() {
        val reducer = DefaultMapReducer()
        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("p", 1, 9_000L), now)).state
        state = reducer.reduce(state, MapAction.ObserveCourseSpeed(course("slow", 2, 9_100L, speed = 0.2), now)).state

        val render = PositionRenderPolicy.resolve(state.position)
        assertNull(render.accuracyMeters)
        assertNull(render.courseVector)
    }

    @Test
    fun `follow is explicit and a user camera gesture returns to browse`() {
        val reducer = DefaultMapReducer()
        val noSource = reducer.reduce(MapState(), MapAction.SetPositionViewIntent(PositionViewIntent.FOLLOW_POSITION)).state
        assertEquals(PositionViewIntent.BROWSE, noSource.position.viewIntent)

        var state = reducer.reduce(MapState(), MapAction.PositionSourceConnected(source)).state
        state = reducer.reduce(state, MapAction.ObservePosition(fix("p", 1, 9_000L), now)).state
        state = reducer.reduce(state, MapAction.SetPositionViewIntent(PositionViewIntent.FOLLOW_POSITION)).state
        assertEquals(PositionViewIntent.FOLLOW_POSITION, state.position.viewIntent)
        assertTrue(state.renderer.pendingCameraCommand?.intent == MapCameraIntent.FOLLOW_POSITION)

        val generation = MapRendererGeneration(1)
        state = reducer.reduce(state, MapAction.RendererHostReady(generation)).state
        state = reducer.reduce(state, MapAction.RendererReady(generation)).state
        state = state.copy(renderer = state.renderer.copy(pendingCameraCommand = null, cameraInputEnabled = true))
        state = reducer.reduce(state, MapAction.RendererCameraIdle(generation, MapCamera(GeoPoint(0.0, 0.0), 8.0))).state
        assertEquals(PositionViewIntent.BROWSE, state.position.viewIntent)
    }

    private fun identity(id: String, sequence: Long, received: Long, bootId: String = "boot-a", source: ObservationSource = this.source) =
        ObservationIdentity(
            source = source,
            observationId = id,
            sequence = sequence,
            sampledAtUtcMillis = null,
            sampleTimeConfidence = SampleTimeConfidence.ARRIVAL_ONLY,
            receivedAt = MonotonicTime(bootId, received),
        )

    private fun fix(
        id: String,
        sequence: Long,
        received: Long,
        value: GeoPoint = point,
        source: ObservationSource = this.source,
        accuracy: Double? = null,
    ) = PositionObservation(identity(id, sequence, received, source = source), value, ObservationValidity.VALID, accuracy)

    private fun heading(
        id: String,
        sequence: Long,
        received: Long,
        reference: HeadingReference = HeadingReference.TRUE,
    ) = HeadingObservation(identity(id, sequence, received), 45.0, reference, validity = ObservationValidity.VALID)

    private fun course(id: String, sequence: Long, received: Long, speed: Double = 6.0) = CourseSpeedObservation(
        identity(id, sequence, received),
        courseOverGroundTrueDegrees = 120.0,
        speedOverGroundKnots = speed,
        quality = CourseQuality.USABLE,
        validity = ObservationValidity.VALID,
    )
}

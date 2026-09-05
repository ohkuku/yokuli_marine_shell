package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.CourseQuality
import com.yokuli.marine.map.domain.CourseSpeedObservation
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.HeadingObservation
import com.yokuli.marine.map.domain.HeadingReference
import com.yokuli.marine.map.domain.MonotonicTime
import com.yokuli.marine.map.domain.ObservationIdentity
import com.yokuli.marine.map.domain.ObservationSource
import com.yokuli.marine.map.domain.ObservationValidity
import com.yokuli.marine.map.domain.PositionAvailability
import com.yokuli.marine.map.domain.PositionObservation
import com.yokuli.marine.map.domain.PositionSourceStatus
import com.yokuli.marine.map.domain.PositionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionOverlayFrameTest {
    private val source = ObservationSource("read-only", "session")
    private val received = MonotonicTime("boot", 1_000L)
    private val point = GeoPoint(-36.8485, 174.7633)

    @Test
    fun `fresh fix uses live plane and independent truthful vectors`() {
        val frame = state().copy(
            heading = HeadingObservation(identity("heading", 2), 40.0, HeadingReference.TRUE, validity = ObservationValidity.VALID),
            headingAvailability = PositionAvailability.FRESH,
            courseSpeed = CourseSpeedObservation(identity("course", 3), 110.0, 6.0, CourseQuality.USABLE, ObservationValidity.VALID),
            courseSpeedAvailability = PositionAvailability.FRESH,
        ).toOverlayFrame()

        assertEquals(1, frame.livePoint.features().orEmpty().size)
        assertTrue(frame.historicalPoint.features().orEmpty().isEmpty())
        assertEquals(1, frame.trueHeading.features().orEmpty().size)
        assertEquals(1, frame.courseOverGround.features().orEmpty().size)
        assertEquals(1, frame.accuracy.features().orEmpty().size)
    }

    @Test
    fun `disconnect moves point to history and removes every live confidence plane`() {
        val frame = state().copy(
            sourceStatus = PositionSourceStatus.Disconnected(source, MonotonicTime("boot", 2_000L)),
        ).toOverlayFrame()

        assertTrue(frame.livePoint.features().orEmpty().isEmpty())
        assertEquals(1, frame.historicalPoint.features().orEmpty().size)
        assertTrue(frame.trueHeading.features().orEmpty().isEmpty())
        assertTrue(frame.courseOverGround.features().orEmpty().isEmpty())
        assertTrue(frame.accuracy.features().orEmpty().isEmpty())
    }

    private fun state() = PositionState(
        sourceStatus = PositionSourceStatus.Connected(source),
        observation = PositionObservation(identity("fix", 1), point, ObservationValidity.VALID, 8.0),
        availability = PositionAvailability.FRESH,
        evaluatedAt = MonotonicTime("boot", 1_500L),
    )

    private fun identity(id: String, sequence: Long) = ObservationIdentity(
        source = source,
        observationId = id,
        sequence = sequence,
        receivedAt = received,
    )
}

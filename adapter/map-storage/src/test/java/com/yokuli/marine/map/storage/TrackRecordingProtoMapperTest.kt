package com.yokuli.marine.map.storage

import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.RecordedTrackPoint
import com.yokuli.marine.navigation.domain.RecordedTrackSegment
import com.yokuli.marine.navigation.domain.TrackRecorderStatus
import com.yokuli.marine.navigation.domain.TrackRecordingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackRecordingProtoMapperTest {
    @Test
    fun `active paused and stopped sessions round trip without losing provenance`() {
        val session = TrackRecordingSession(
            id = "track-1",
            startedAtEpochMillis = 100,
            status = TrackRecorderStatus.PAUSED,
            segments = listOf(
                RecordedTrackSegment(
                    listOf(
                        RecordedTrackPoint(
                            NavigationPosition(-36.85, 174.76), 120, "selected-source", 6.0, 70.0,
                        ),
                    ),
                ),
            ),
            startNewSegment = true,
            accumulatedDurationMillis = 20,
            navigationSessionId = "route:2:90",
            routeId = "route",
            routeRevision = 2,
        )

        assertEquals(session, TrackRecordingProtoMapper.decode(TrackRecordingProtoMapper.encode(session)))
        assertNull(TrackRecordingProtoMapper.decode(TrackRecordingProtoMapper.encode(null)))
    }

    @Test
    fun `encoder rejects a recording above the runtime point bound`() {
        val point = RecordedTrackPoint(
            NavigationPosition(-36.85, 174.76), 120, "selected-source", 6.0, 70.0,
        )
        val session = TrackRecordingSession(
            id = "over-capacity",
            startedAtEpochMillis = 100,
            status = TrackRecorderStatus.PAUSED,
            segments = listOf(RecordedTrackSegment(List(200_001) { point })),
            startNewSegment = true,
            accumulatedDurationMillis = 20,
        )

        assertThrows(IllegalArgumentException::class.java) {
            TrackRecordingProtoMapper.encode(session)
        }
    }
}

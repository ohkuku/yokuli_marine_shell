package com.yokuli.marine.map.storage

import com.yokuli.marine.map.storage.proto.TrackRecordingPointProto
import com.yokuli.marine.map.storage.proto.TrackRecordingSegmentProto
import com.yokuli.marine.map.storage.proto.TrackRecordingStateProto
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.RecordedTrackPoint
import com.yokuli.marine.navigation.domain.RecordedTrackSegment
import com.yokuli.marine.navigation.domain.TrackRecorderStatus
import com.yokuli.marine.navigation.domain.TrackRecordingSession

internal object TrackRecordingProtoMapper {
    const val SCHEMA_VERSION = 1
    private const val MAX_POINTS = 200_000

    fun encode(session: TrackRecordingSession?): TrackRecordingStateProto {
        require((session?.segments?.sumOf { it.points.size } ?: 0) <= MAX_POINTS) {
            "Track recording exceeds capacity"
        }
        return TrackRecordingStateProto.newBuilder()
            .setSchemaVersion(SCHEMA_VERSION)
            .setHasSession(session != null)
            .apply {
                session?.let { value ->
                    id = value.id
                    startedAtEpochMillis = value.startedAtEpochMillis
                    status = value.status.name
                    addAllSegments(value.segments.map(::encodeSegment))
                    startNewSegment = value.startNewSegment
                    hasActiveSince = value.activeSinceEpochMillis != null
                    activeSinceEpochMillis = value.activeSinceEpochMillis ?: 0L
                    accumulatedDurationMillis = value.accumulatedDurationMillis
                    hasStoppedAt = value.stoppedAtEpochMillis != null
                    stoppedAtEpochMillis = value.stoppedAtEpochMillis ?: 0L
                    navigationSessionId = value.navigationSessionId.orEmpty()
                    routeId = value.routeId.orEmpty()
                    routeRevision = value.routeRevision ?: 0L
                }
            }
            .build()
    }

    fun decode(proto: TrackRecordingStateProto): TrackRecordingSession? {
        require(proto.schemaVersion in 0..SCHEMA_VERSION) { "Unsupported track recording schema ${proto.schemaVersion}" }
        if (!proto.hasSession) return null
        require(proto.segmentsList.sumOf { it.pointsCount } <= MAX_POINTS) { "Track recording exceeds capacity" }
        return TrackRecordingSession(
            id = proto.id,
            startedAtEpochMillis = proto.startedAtEpochMillis,
            status = enumValueOf<TrackRecorderStatus>(proto.status),
            segments = proto.segmentsList.map(::decodeSegment),
            startNewSegment = proto.startNewSegment,
            activeSinceEpochMillis = proto.activeSinceEpochMillis.takeIf { proto.hasActiveSince },
            accumulatedDurationMillis = proto.accumulatedDurationMillis,
            stoppedAtEpochMillis = proto.stoppedAtEpochMillis.takeIf { proto.hasStoppedAt },
            navigationSessionId = proto.navigationSessionId.takeIf(String::isNotBlank),
            routeId = proto.routeId.takeIf(String::isNotBlank),
            routeRevision = proto.routeRevision.takeIf { proto.routeId.isNotBlank() },
        )
    }

    private fun encodeSegment(segment: RecordedTrackSegment): TrackRecordingSegmentProto =
        TrackRecordingSegmentProto.newBuilder().addAllPoints(segment.points.map(::encodePoint)).build()

    private fun encodePoint(point: RecordedTrackPoint): TrackRecordingPointProto =
        TrackRecordingPointProto.newBuilder()
            .setLatitude(point.position.latitude)
            .setLongitude(point.position.longitude)
            .setRecordedAtEpochMillis(point.recordedAtEpochMillis)
            .setSourceId(point.sourceId)
            .setHasSpeed(point.speedOverGroundKnots != null)
            .setSpeedOverGroundKnots(point.speedOverGroundKnots ?: 0.0)
            .setHasCourse(point.courseOverGroundTrueDegrees != null)
            .setCourseOverGroundTrueDegrees(point.courseOverGroundTrueDegrees ?: 0.0)
            .build()

    private fun decodeSegment(segment: TrackRecordingSegmentProto): RecordedTrackSegment =
        RecordedTrackSegment(segment.pointsList.map(::decodePoint))

    private fun decodePoint(point: TrackRecordingPointProto): RecordedTrackPoint = RecordedTrackPoint(
        position = NavigationPosition(point.latitude, point.longitude),
        recordedAtEpochMillis = point.recordedAtEpochMillis,
        sourceId = point.sourceId,
        speedOverGroundKnots = point.speedOverGroundKnots.takeIf { point.hasSpeed },
        courseOverGroundTrueDegrees = point.courseOverGroundTrueDegrees.takeIf { point.hasCourse },
    )
}

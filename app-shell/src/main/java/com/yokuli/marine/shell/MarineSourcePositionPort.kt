package com.yokuli.marine.shell

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.HeadingReference as MarineHeadingReference
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.map.domain.CourseQuality
import com.yokuli.marine.map.domain.CourseSpeedObservation
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.HeadingObservation
import com.yokuli.marine.map.domain.HeadingReference as MapHeadingReference
import com.yokuli.marine.map.domain.MonotonicTime
import com.yokuli.marine.map.domain.ObservationIdentity
import com.yokuli.marine.map.domain.ObservationSource
import com.yokuli.marine.map.domain.ObservationValidity
import com.yokuli.marine.map.domain.PositionObservation
import com.yokuli.marine.map.domain.PositionPortEvent
import com.yokuli.marine.map.domain.ReadOnlyPositionPort
import com.yokuli.marine.map.domain.SampleTimeConfidence
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Composition adapter from the single OS source decision to Chart's existing read-only port.
 * It never selects, fails over, requests permission, starts a transport, or exposes endpoint data.
 */
class MarineSourcePositionPort(
    private val snapshots: StateFlow<MarineSourceSnapshot>,
    private val sourceEpoch: String,
) : ReadOnlyPositionPort {
    init {
        require(sourceEpoch.isNotBlank())
    }

    override val events: Flow<PositionPortEvent> = flow {
        var activeSource: ObservationSource? = null
        var lastPositionId: String? = null
        var lastHeadingId: String? = null
        var lastCourseSpeedId: String? = null
        snapshots.collect { snapshot ->
            val position = snapshot.projectPosition()
            if (position == null) {
                activeSource?.let { emit(PositionPortEvent.SourceDisconnected(it)) }
                activeSource = null
                lastPositionId = null
                lastHeadingId = null
                lastCourseSpeedId = null
                return@collect
            }

            if (activeSource != position.source) {
                activeSource?.let { emit(PositionPortEvent.SourceDisconnected(it)) }
                emit(PositionPortEvent.SourceConnected(position.source))
                activeSource = position.source
                lastPositionId = null
                lastHeadingId = null
                lastCourseSpeedId = null
            }
            if (lastPositionId != position.observation.identity.observationId) {
                emit(PositionPortEvent.Position(position.observation))
                lastPositionId = position.observation.identity.observationId
            }

            snapshot.projectHeading(position.source)?.let { heading ->
                if (lastHeadingId != heading.identity.observationId) {
                    emit(PositionPortEvent.Heading(heading))
                    lastHeadingId = heading.identity.observationId
                }
            }
            snapshot.projectCourseSpeed(position.source)?.let { courseSpeed ->
                if (lastCourseSpeedId != courseSpeed.identity.observationId) {
                    emit(PositionPortEvent.CourseSpeed(courseSpeed))
                    lastCourseSpeedId = courseSpeed.identity.observationId
                }
            }
        }
    }

    private fun MarineSourceSnapshot.projectPosition(): ProjectedPosition? {
        val datum = resolvedData.items[DataKey.Position] ?: return null
        if (datum.availability !in CONNECTED_AVAILABILITY) return null
        val value = datum.value as? MarineValue.Position ?: return null
        val source = datum.source.toObservationSource(sourceEpoch)
        val accuracy = resolvedData.items[DataKey.PositionAccuracy].accuracyFor(datum)
        val identity = observationIdentity("position", datum, source) ?: return null
        return ProjectedPosition(
            source,
            PositionObservation(
                identity = identity,
                point = GeoPoint(value.latitudeDegrees, value.longitudeDegrees),
                validity = ObservationValidity.VALID,
                horizontalAccuracyMeters = accuracy,
            ),
        )
    }

    private fun MarineSourceSnapshot.projectHeading(source: ObservationSource): HeadingObservation? {
        val datum = listOf(
            DataKey.Heading(MarineHeadingReference.TRUE),
            DataKey.Heading(MarineHeadingReference.MAGNETIC),
        ).asSequence()
            .mapNotNull(resolvedData.items::get)
            .firstOrNull { it.isUsableFrom(source) }
            ?: return null
        val value = datum.value as? MarineValue.Decimal ?: return null
        if (value.unit != MarineUnit.DEGREES || value.value !in 0.0..<360.0) return null
        val key = datum.key as DataKey.Heading
        val identity = observationIdentity("heading:${key.reference.name.lowercase()}", datum, source) ?: return null
        val variation = resolvedData.items[DataKey.MagneticVariation]
            ?.takeIf { it.sameFrameAs(datum) }
            ?.value
            .let { it as? MarineValue.Decimal }
            ?.takeIf { it.unit == MarineUnit.DEGREES }
            ?.value
        return HeadingObservation(
            identity = identity,
            degrees = value.value,
            reference = when (key.reference) {
                MarineHeadingReference.TRUE -> MapHeadingReference.TRUE
                MarineHeadingReference.MAGNETIC -> MapHeadingReference.MAGNETIC
            },
            magneticVariationDegrees = variation,
            validity = ObservationValidity.VALID,
        )
    }

    private fun MarineSourceSnapshot.projectCourseSpeed(source: ObservationSource): CourseSpeedObservation? {
        val course = resolvedData.items[DataKey.CourseOverGround]
            ?.takeIf {
                it.isUsableFrom(source) &&
                    it.decimal(MarineUnit.DEGREES)?.value?.let { degrees -> degrees in 0.0..<360.0 } == true
            }
        val speed = resolvedData.items[DataKey.SpeedOverGround]
            ?.takeIf { it.isUsableFrom(source) && (it.decimal(MarineUnit.KNOTS)?.value ?: -1.0) >= 0.0 }
        val anchor = listOfNotNull(course, speed).maxWithOrNull(
            compareBy<ResolvedDatum>({ it.candidate?.receivedAtMillis ?: -1L }, { it.candidate?.groupId?.frameSequence ?: -1L }),
        ) ?: return null
        val atomicCourse = course?.takeIf { it.sameFrameAs(anchor) }?.decimal(MarineUnit.DEGREES)?.value
        val atomicSpeed = speed?.takeIf { it.sameFrameAs(anchor) }?.decimal(MarineUnit.KNOTS)?.value
        if (atomicCourse == null && atomicSpeed == null) return null
        return CourseSpeedObservation(
            identity = observationIdentity("course-speed", anchor, source) ?: return null,
            courseOverGroundTrueDegrees = atomicCourse,
            speedOverGroundKnots = atomicSpeed,
            quality = if (atomicCourse != null && atomicSpeed != null) CourseQuality.USABLE else CourseQuality.UNKNOWN,
            validity = ObservationValidity.VALID,
        )
    }

    private fun MarineSourceSnapshot.observationIdentity(
        kind: String,
        datum: ResolvedDatum,
        source: ObservationSource,
    ): ObservationIdentity? {
        val candidate = datum.candidate ?: return null
        val receivedAt = candidate.receivedAtMillis ?: return null
        val group = candidate.groupId ?: return null
        val sourceTime = resolvedData.items[DataKey.SourceTime]
            ?.takeIf { it.sameFrameAs(datum) }
            ?.value
            .let { it as? MarineValue.UtcEpochMillis }
            ?.value
            ?.takeIf { it >= 0L }
        return ObservationIdentity(
            source = source,
            observationId = "$kind:$receivedAt:${group.frameSequence}",
            sequence = group.frameSequence,
            sampledAtUtcMillis = sourceTime,
            sampleTimeConfidence = if (sourceTime == null) {
                SampleTimeConfidence.ARRIVAL_ONLY
            } else {
                SampleTimeConfidence.REPORTED_UTC
            },
            receivedAt = MonotonicTime(sourceEpoch, receivedAt),
        )
    }

    private fun ResolvedDatum.isUsableFrom(source: ObservationSource): Boolean =
        availability in CONNECTED_AVAILABILITY && this.source.toObservationSource(sourceEpoch) == source

    private fun ResolvedDatum.sameFrameAs(other: ResolvedDatum): Boolean {
        val ownGroup = candidate?.groupId ?: return false
        return source == other.source &&
            availability in CONNECTED_AVAILABILITY &&
            ownGroup == other.candidate?.groupId
    }

    private fun ResolvedDatum.decimal(unit: MarineUnit): MarineValue.Decimal? =
        (value as? MarineValue.Decimal)?.takeIf { it.unit == unit }

    private fun ResolvedDatum?.accuracyFor(position: ResolvedDatum): Double? {
        if (this == null || !sameFrameAs(position)) return null
        val decimal = value as? MarineValue.Decimal ?: return null
        return decimal.value.takeIf { decimal.unit == MarineUnit.METERS && it >= 0.0 }
    }

    private data class ProjectedPosition(
        val source: ObservationSource,
        val observation: PositionObservation,
    )

    private companion object {
        val CONNECTED_AVAILABILITY = setOf(SourceCandidateAvailability.LIVE, SourceCandidateAvailability.HELD)
    }
}

private fun SourceIdentity.toObservationSource(epoch: String): ObservationSource {
    val stableInput = buildString {
        append(connectionId.value)
        udpOrigin?.let {
            append('|')
            append(it.hostAddress)
            append('|')
            append(it.port ?: "host")
        }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(stableInput.toByteArray(Charsets.UTF_8))
    val opaqueId = digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return ObservationSource(sourceId = "marine-$opaqueId", sourceEpoch = epoch)
}

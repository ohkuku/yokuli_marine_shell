package com.yokuli.marine.map.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class ObservationSource(
    val sourceId: String,
    val sourceEpoch: String,
) {
    init {
        require(sourceId.isNotBlank())
        require(sourceEpoch.isNotBlank())
    }
}

/** A process/boot-scoped monotonic reading. Values from different boot IDs are never compared as fresh. */
data class MonotonicTime(
    val bootId: String,
    val elapsedRealtimeMillis: Long,
) {
    init {
        require(bootId.isNotBlank())
        require(elapsedRealtimeMillis >= 0L)
    }
}

enum class SampleTimeConfidence { TRUSTED_UTC, REPORTED_UTC, ARRIVAL_ONLY }
enum class ObservationValidity { VALID, INVALID }

data class ObservationIdentity(
    val source: ObservationSource,
    val observationId: String,
    val sequence: Long? = null,
    val sampledAtUtcMillis: Long? = null,
    val sampleTimeConfidence: SampleTimeConfidence = SampleTimeConfidence.ARRIVAL_ONLY,
    val receivedAt: MonotonicTime,
) {
    init {
        require(observationId.isNotBlank())
        require(sequence == null || sequence >= 0L)
        require(sampledAtUtcMillis == null || sampledAtUtcMillis >= 0L)
        require((sampledAtUtcMillis == null) == (sampleTimeConfidence == SampleTimeConfidence.ARRIVAL_ONLY))
    }
}

data class PositionObservation(
    val identity: ObservationIdentity,
    val point: GeoPoint,
    val validity: ObservationValidity,
    val horizontalAccuracyMeters: Double? = null,
) {
    init {
        require(horizontalAccuracyMeters == null ||
            horizontalAccuracyMeters.isFinite() && horizontalAccuracyMeters >= 0.0)
    }
}

enum class HeadingReference { TRUE, MAGNETIC }

data class HeadingObservation(
    val identity: ObservationIdentity,
    val degrees: Double,
    val reference: HeadingReference,
    val magneticVariationDegrees: Double? = null,
    val validity: ObservationValidity,
) {
    init {
        require(degrees.isFinite() && degrees >= 0.0 && degrees < 360.0)
        require(magneticVariationDegrees == null || magneticVariationDegrees.isFinite())
    }
}

enum class CourseQuality { USABLE, LOW_CONFIDENCE, UNKNOWN }

data class CourseSpeedObservation(
    val identity: ObservationIdentity,
    val courseOverGroundTrueDegrees: Double?,
    val speedOverGroundKnots: Double?,
    val quality: CourseQuality,
    val validity: ObservationValidity,
) {
    init {
        require(courseOverGroundTrueDegrees == null ||
            courseOverGroundTrueDegrees.isFinite() &&
            courseOverGroundTrueDegrees >= 0.0 && courseOverGroundTrueDegrees < 360.0)
        require(speedOverGroundKnots == null || speedOverGroundKnots.isFinite() && speedOverGroundKnots >= 0.0)
    }
}

sealed interface PositionSourceStatus {
    data object NoSource : PositionSourceStatus
    data class Connected(val source: ObservationSource) : PositionSourceStatus
    data class Disconnected(val source: ObservationSource, val disconnectedAt: MonotonicTime) : PositionSourceStatus
}

enum class PositionAvailability { UNAVAILABLE, INVALID, STALE, FRESH }
enum class PositionViewIntent { BROWSE, FOLLOW_POSITION }
enum class ObservationRejection { SOURCE_MISMATCH, DUPLICATE, OUT_OF_ORDER, FUTURE_MONOTONIC_TIME }

data class PositionState(
    val sourceStatus: PositionSourceStatus = PositionSourceStatus.NoSource,
    val observation: PositionObservation? = null,
    val availability: PositionAvailability = PositionAvailability.UNAVAILABLE,
    val heading: HeadingObservation? = null,
    val headingAvailability: PositionAvailability = PositionAvailability.UNAVAILABLE,
    val courseSpeed: CourseSpeedObservation? = null,
    val courseSpeedAvailability: PositionAvailability = PositionAvailability.UNAVAILABLE,
    val viewIntent: PositionViewIntent = PositionViewIntent.BROWSE,
    val evaluatedAt: MonotonicTime? = null,
)

fun PositionState.positionAgeMillis(): Long? {
    val observed = observation?.identity?.receivedAt ?: return null
    val evaluated = evaluatedAt ?: return null
    if (observed.bootId != evaluated.bootId) return null
    return (evaluated.elapsedRealtimeMillis - observed.elapsedRealtimeMillis).takeIf { it >= 0L }
}

enum class VesselMarkerStyle { HIDDEN, LIVE_NEUTRAL, LIVE_TRUE_HEADING, HISTORICAL }

data class CourseVector(val trueDegrees: Double, val speedKnots: Double)

data class PositionRenderModel(
    val point: GeoPoint?,
    val markerStyle: VesselMarkerStyle,
    val trueHeadingDegrees: Double?,
    val courseVector: CourseVector?,
    val accuracyMeters: Double?,
)

object PositionRenderPolicy {
    const val MINIMUM_COG_VECTOR_SPEED_KNOTS = 1.0

    fun resolve(state: PositionState): PositionRenderModel {
        val observation = state.observation
        if (observation == null || observation.validity != ObservationValidity.VALID ||
            state.availability in setOf(PositionAvailability.UNAVAILABLE, PositionAvailability.INVALID)
        ) {
            return PositionRenderModel(null, VesselMarkerStyle.HIDDEN, null, null, null)
        }
        val connected = (state.sourceStatus as? PositionSourceStatus.Connected)?.source == observation.identity.source
        if (!connected || state.availability == PositionAvailability.STALE) {
            return PositionRenderModel(observation.point, VesselMarkerStyle.HISTORICAL, null, null, null)
        }
        val trueHeading = state.heading
            ?.takeIf {
                state.headingAvailability == PositionAvailability.FRESH &&
                    it.validity == ObservationValidity.VALID &&
                    it.identity.source == observation.identity.source
            }
            ?.toTrueDegreesOrNull()
        val courseVector = state.courseSpeed
            ?.takeIf {
                state.courseSpeedAvailability == PositionAvailability.FRESH &&
                    it.validity == ObservationValidity.VALID &&
                    it.quality == CourseQuality.USABLE &&
                    it.identity.source == observation.identity.source &&
                    it.courseOverGroundTrueDegrees != null &&
                    (it.speedOverGroundKnots ?: 0.0) >= MINIMUM_COG_VECTOR_SPEED_KNOTS
            }
            ?.let { CourseVector(checkNotNull(it.courseOverGroundTrueDegrees), checkNotNull(it.speedOverGroundKnots)) }
        return PositionRenderModel(
            point = observation.point,
            markerStyle = if (trueHeading == null) VesselMarkerStyle.LIVE_NEUTRAL else VesselMarkerStyle.LIVE_TRUE_HEADING,
            trueHeadingDegrees = trueHeading,
            courseVector = courseVector,
            accuracyMeters = observation.horizontalAccuracyMeters,
        )
    }

    private fun HeadingObservation.toTrueDegreesOrNull(): Double? = when (reference) {
        HeadingReference.TRUE -> degrees
        HeadingReference.MAGNETIC -> magneticVariationDegrees?.let { normalizeDegrees(degrees + it) }
    }
}

object PositionFreshnessPolicy {
    const val POSITION_FRESH_MILLIS = 30_000L
    const val HEADING_FRESH_MILLIS = 10_000L
    const val COURSE_SPEED_FRESH_MILLIS = 15_000L

    fun availability(
        identity: ObservationIdentity,
        validity: ObservationValidity,
        now: MonotonicTime,
        freshWindowMillis: Long,
    ): PositionAvailability {
        if (validity != ObservationValidity.VALID) return PositionAvailability.INVALID
        if (identity.receivedAt.bootId != now.bootId) return PositionAvailability.STALE
        val age = now.elapsedRealtimeMillis - identity.receivedAt.elapsedRealtimeMillis
        if (age < 0L) return PositionAvailability.INVALID
        return if (age <= freshWindowMillis) PositionAvailability.FRESH else PositionAvailability.STALE
    }
}

sealed interface PositionPortEvent {
    data class SourceConnected(val source: ObservationSource) : PositionPortEvent
    data class SourceDisconnected(val source: ObservationSource) : PositionPortEvent
    data class Position(val value: PositionObservation) : PositionPortEvent
    data class Heading(val value: HeadingObservation) : PositionPortEvent
    data class CourseSpeed(val value: CourseSpeedObservation) : PositionPortEvent
}

/** Read-only input boundary. It exposes observations and cannot send NMEA, routes, or vessel commands. */
interface ReadOnlyPositionPort {
    val events: Flow<PositionPortEvent>
}

fun interface ObservationMonotonicClock {
    fun now(): MonotonicTime
}

object NoSourcePositionPort : ReadOnlyPositionPort {
    override val events: Flow<PositionPortEvent> = emptyFlow()
}

private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

package com.yokuli.marine.data.model

/** Stable user-owned connection identity. Display names are deliberately separate. */
data class ConnectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConnectionId must not be blank" }
    }
}

/** Boot/runtime-local generation used to reject callbacks from replaced sessions. */
data class SessionGeneration(val value: Long) {
    init {
        require(value >= 0L) { "SessionGeneration must be non-negative" }
    }

    fun next(): SessionGeneration = SessionGeneration(Math.addExact(value, 1L))
}

/** Optional UDP sender identity. It is observed transport truth, not a physical-device claim. */
data class SenderIdentity(
    val hostAddress: String,
    val port: Int,
) {
    init {
        require(hostAddress.isNotBlank()) { "Sender host must not be blank" }
        require(port in 1..65_535) { "Sender port is outside the UDP range" }
    }
}

/** Stable logical source: the configured channel plus an observed UDP sender when applicable. */
data class SourceIdentity(
    val connectionId: ConnectionId,
    val sender: SenderIdentity? = null,
)

/** Full provenance of one parsed observation. Talker is metadata, never the source identity. */
data class ObservationOrigin(
    val source: SourceIdentity,
    val sessionGeneration: SessionGeneration,
    val talker: String,
    val formatter: String,
) {
    init {
        require(talker.length == 2 && talker.all(Char::isLetterOrDigit)) {
            "NMEA talker must contain two ASCII-style identifier characters"
        }
        require(formatter.length == 3 && formatter.all(Char::isLetterOrDigit)) {
            "NMEA formatter must contain three identifier characters"
        }
    }

    val sentenceId: String = (talker + formatter).uppercase()
}

enum class HeadingReference { TRUE, MAGNETIC }

enum class DepthReference { BELOW_TRANSDUCER, BELOW_SURFACE, BELOW_KEEL }

enum class WindReference { APPARENT, TRUE_RELATIVE, TRUE_NORTH, MAGNETIC_NORTH }

sealed interface DataKey {
    data object Position : DataKey
    data object SpeedOverGround : DataKey
    data object CourseOverGround : DataKey
    data class Heading(val reference: HeadingReference) : DataKey
    data class Depth(val reference: DepthReference) : DataKey
    data class WindAngle(val reference: WindReference) : DataKey
    data class WindSpeed(val reference: WindReference) : DataKey
    data object MagneticVariation : DataKey
    data object SourceTime : DataKey
    data object FixQuality : DataKey
    data object Satellites : DataKey
    data object HorizontalDilution : DataKey
    data object Altitude : DataKey
}

enum class MarineUnit {
    DEGREES,
    KNOTS,
    METERS,
    DIMENSIONLESS,
}

sealed interface MarineValue {
    data class Position(
        val latitudeDegrees: Double,
        val longitudeDegrees: Double,
    ) : MarineValue {
        init {
            require(latitudeDegrees.isFinite() && latitudeDegrees in -90.0..90.0)
            require(longitudeDegrees.isFinite() && longitudeDegrees in -180.0..180.0)
        }
    }

    data class Decimal(
        val value: Double,
        val unit: MarineUnit,
    ) : MarineValue {
        init {
            require(value.isFinite()) { "Observation values must be finite" }
        }
    }

    data class Count(val value: Int) : MarineValue

    data class UtcEpochMillis(val value: Long) : MarineValue
}

enum class ObservationValidity { VALID, EXPLICIT_INVALID }

/**
 * A parsed value or explicit negative-validity statement. Blank fields produce no observation.
 * [measuredAtMillis] is monotonic runtime time and must never be a wall-clock timestamp.
 */
data class MarineObservation(
    val key: DataKey,
    val value: MarineValue?,
    val validity: ObservationValidity,
    val origin: ObservationOrigin,
    val measuredAtMillis: Long,
    val sourceTimeEpochMillis: Long? = null,
) {
    init {
        require(measuredAtMillis >= 0L)
        require(validity != ObservationValidity.VALID || value != null) {
            "A valid observation requires a value"
        }
        require(validity != ObservationValidity.EXPLICIT_INVALID || value == null) {
            "An explicitly invalid observation must not carry a value"
        }
    }
}

data class CandidateId(
    val key: DataKey,
    val source: SourceIdentity,
)

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

/** Sequence allocated once per accepted frame inside one source session. */
data class ObservationGroupId(val frameSequence: Long) {
    init {
        require(frameSequence >= 0L) { "Observation frame sequence must be non-negative" }
    }
}

/** Whether an observation was protected by an actual checksum on its source frame. */
enum class ChecksumTrust {
    VERIFIED,
    UNVERIFIED_ALLOWED,
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

/** Normalized UDP origin key. It is deliberately not the observed transport provenance object. */
@ConsistentCopyVisibility
data class UdpOriginIdentity internal constructor(
    val hostAddress: String,
    val port: Int?,
)

/**
 * A UDP listener must explicitly choose whether a sender's ephemeral port is part of candidate
 * identity. The observed [SenderIdentity] itself remains provenance on [ObservationOrigin].
 */
enum class UdpOriginIdentityPolicy {
    HOST_ADDRESS,
    HOST_AND_PORT;

    fun sourceIdentity(connectionId: ConnectionId, sender: SenderIdentity): SourceIdentity =
        SourceIdentity.forUdp(connectionId, sender, this)
}

/** Stable logical source derived from configured identity and an explicit UDP identity policy. */
@ConsistentCopyVisibility
data class SourceIdentity internal constructor(
    val connectionId: ConnectionId,
    val udpOrigin: UdpOriginIdentity?,
) {
    constructor(connectionId: ConnectionId) : this(connectionId, null)

    companion object {
        fun forUdp(
            connectionId: ConnectionId,
            sender: SenderIdentity,
            policy: UdpOriginIdentityPolicy,
        ): SourceIdentity = SourceIdentity(
            connectionId = connectionId,
            udpOrigin = UdpOriginIdentity(
                hostAddress = sender.hostAddress,
                port = sender.port.takeIf { policy == UdpOriginIdentityPolicy.HOST_AND_PORT },
            ),
        )
    }
}

/** Full provenance of one parsed observation. Talker is metadata, never the source identity. */
data class ObservationOrigin(
    val source: SourceIdentity,
    val sessionGeneration: SessionGeneration,
    val talker: String,
    val formatter: String,
    val sender: SenderIdentity? = null,
) {
    init {
        require(talker.length == 2 && talker.all(Char::isLetterOrDigit)) {
            "NMEA talker must contain two ASCII-style identifier characters"
        }
        require(formatter.length == 3 && formatter.all(Char::isLetterOrDigit)) {
            "NMEA formatter must contain three identifier characters"
        }
        require(talker.all { it in '0'..'9' || it in 'A'..'Z' }) {
            "NMEA talker must be normalized uppercase ASCII"
        }
        require(formatter.all { it in '0'..'9' || it in 'A'..'Z' }) {
            "NMEA formatter must be normalized uppercase ASCII"
        }
        source.udpOrigin?.let { identity ->
            val observed = requireNotNull(sender) {
                "A UDP source identity requires observed sender provenance"
            }
            require(observed.hostAddress == identity.hostAddress) {
                "UDP source host and observed sender host must match"
            }
            require(identity.port == null || observed.port == identity.port) {
                "UDP endpoint identity and observed sender port must match"
            }
        }
        require(source.udpOrigin != null || sender == null) {
            "Observed UDP sender provenance requires an explicitly derived UDP source identity"
        }
    }

    val sentenceId: String = (talker + formatter).uppercase()
}

enum class HeadingReference { TRUE, MAGNETIC }

enum class DepthReference { BELOW_TRANSDUCER, BELOW_SURFACE, BELOW_KEEL }

enum class WindReference { APPARENT, TRUE_RELATIVE, TRUE_NORTH, MAGNETIC_NORTH }

/** Wind speed has no compass-frame direction; it is either apparent or true. */
enum class WindSpeedReference { APPARENT, TRUE }

sealed interface DataKey {
    data object Position : DataKey
    data object SpeedOverGround : DataKey
    data object CourseOverGround : DataKey
    data class Heading(val reference: HeadingReference) : DataKey
    data class Depth(val reference: DepthReference) : DataKey
    data class WindAngle(val reference: WindReference) : DataKey
    data class WindSpeed(val reference: WindSpeedReference) : DataKey
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

    data class Count(val value: Int) : MarineValue {
        init {
            require(value >= 0) { "Observation counts must be non-negative" }
        }
    }

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
    val groupId: ObservationGroupId,
    val checksumTrust: ChecksumTrust,
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
        if (validity == ObservationValidity.VALID) {
            require(key.accepts(requireNotNull(value))) {
                "Observation value kind, unit, or range does not match its data key"
            }
        }
        if (key == DataKey.SourceTime && value is MarineValue.UtcEpochMillis) {
            require(sourceTimeEpochMillis == null || sourceTimeEpochMillis == value.value) {
                "Source-time evidence must not contradict the SourceTime value"
            }
        }
    }
}

data class CandidateId(
    val key: DataKey,
    val source: SourceIdentity,
)

private fun DataKey.accepts(value: MarineValue): Boolean = when (this) {
    DataKey.Position -> value is MarineValue.Position
    DataKey.SpeedOverGround -> value.isDecimal(MarineUnit.KNOTS) { it >= 0.0 }
    DataKey.CourseOverGround -> value.isDecimal(MarineUnit.DEGREES, ::isCompassAngle)
    is DataKey.Heading -> value.isDecimal(MarineUnit.DEGREES, ::isCompassAngle)
    is DataKey.Depth -> value.isDecimal(MarineUnit.METERS) { it >= 0.0 }
    is DataKey.WindAngle -> value.isDecimal(MarineUnit.DEGREES, ::isCompassAngle)
    is DataKey.WindSpeed -> value.isDecimal(MarineUnit.KNOTS) { it >= 0.0 }
    DataKey.MagneticVariation -> value.isDecimal(MarineUnit.DEGREES) { it in -180.0..180.0 }
    DataKey.SourceTime -> value is MarineValue.UtcEpochMillis
    DataKey.FixQuality -> value is MarineValue.Count && value.value in 0..8
    DataKey.Satellites -> value is MarineValue.Count
    DataKey.HorizontalDilution -> value.isDecimal(MarineUnit.DIMENSIONLESS) { it >= 0.0 }
    DataKey.Altitude -> value.isDecimal(MarineUnit.METERS)
}

private inline fun MarineValue.isDecimal(
    expectedUnit: MarineUnit,
    predicate: (Double) -> Boolean = { true },
): Boolean = this is MarineValue.Decimal && unit == expectedUnit && predicate(value)

private fun isCompassAngle(value: Double): Boolean = value >= 0.0 && value < 360.0

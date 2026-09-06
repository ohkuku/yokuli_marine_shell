package com.yokuli.marine.data.nmea

import com.yokuli.marine.data.model.ChecksumTrust
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.DepthReference
import com.yokuli.marine.data.model.HeadingReference
import com.yokuli.marine.data.model.MarineObservation
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.ObservationOrigin
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.model.WindReference
import com.yokuli.marine.data.model.WindSpeedReference
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

data class NmeaParseContext(
    val source: SourceIdentity,
    val sessionGeneration: SessionGeneration,
    val receivedAtMonotonicMillis: Long,
    val observationGroupId: ObservationGroupId,
    val sender: SenderIdentity? = null,
) {
    init {
        require(receivedAtMonotonicMillis >= 0L)
    }
}

/** Stable sentence-instance discriminator for formatters with multiple semantic streams. */
data class SentenceSemanticDiscriminator(val stableKey: String) {
    init {
        require(stableKey.isNotBlank())
    }
}

data class NmeaSentence(
    val raw: String,
    val body: String,
    val prefix: Char,
    val origin: ObservationOrigin,
    val payloadFields: List<String>,
    val checksumTrust: ChecksumTrust,
    val groupId: ObservationGroupId,
    val semanticDiscriminator: SentenceSemanticDiscriminator?,
    val receivedAtMonotonicMillis: Long,
) {
    val talker: String get() = origin.talker
    val formatter: String get() = origin.formatter
    val sentenceId: String get() = origin.sentenceId
}

enum class NmeaFieldErrorReason {
    INVALID_NUMBER,
    OUT_OF_RANGE,
    INCOMPLETE_PAIR,
    INVALID_UNIT,
    INVALID_REFERENCE,
    INVALID_STATUS,
    INVALID_DATE_TIME,
}

data class NmeaFieldError(
    val fieldName: String,
    val rawValue: String?,
    val reason: NmeaFieldErrorReason,
)

enum class ExplicitInvalidReason {
    NAVIGATION_RECEIVER_WARNING,
    NO_POSITION_FIX,
    WIND_DATA_INVALID,
}

enum class MalformedReason {
    FRAME_TOO_LONG,
    SENTENCE_IDENTIFIER,
}

sealed interface NmeaParseResult {
    val raw: String

    data class Parsed(
        val sentence: NmeaSentence,
        val observations: List<MarineObservation>,
        val fieldErrors: List<NmeaFieldError> = emptyList(),
    ) : NmeaParseResult {
        override val raw: String = sentence.raw
    }

    data class ExplicitInvalid(
        val sentence: NmeaSentence,
        val observations: List<MarineObservation>,
        val reason: ExplicitInvalidReason,
        val fieldErrors: List<NmeaFieldError> = emptyList(),
    ) : NmeaParseResult {
        override val raw: String = sentence.raw
    }

    data class Unsupported(val sentence: NmeaSentence) : NmeaParseResult {
        override val raw: String = sentence.raw
    }

    data class Malformed(
        override val raw: String,
        val reason: MalformedReason,
    ) : NmeaParseResult

    data class ChecksumFailure(
        override val raw: String,
        val reason: ChecksumFailureReason,
    ) : NmeaParseResult
}

/**
 * Pure NMEA 0183 field parser. Transport framing, source selection, and freshness are separate
 * concerns. A blank field never creates an observation; explicit protocol invalidity creates a
 * value-less invalidation so a catalog can age/clear only the affected semantic key.
 */
class Nmea0183Parser(
    private val checksumPolicy: ChecksumPolicy = ChecksumPolicy.STRICT,
    private val maxFrameBytes: Int = MAX_FRAME_BYTES,
) {
    init {
        require(maxFrameBytes > 0)
    }

    fun parse(rawFrame: String, context: NmeaParseContext): NmeaParseResult {
        val raw = rawFrame.trimEnd('\r', '\n')
        if (raw.length > maxFrameBytes) {
            return NmeaParseResult.Malformed(raw, MalformedReason.FRAME_TOO_LONG)
        }
        val checksum = when (val validation = NmeaChecksum.validate(raw, checksumPolicy)) {
            is ChecksumValidation.Failure -> {
                return NmeaParseResult.ChecksumFailure(raw, validation.reason)
            }
            is ChecksumValidation.Valid -> validation
        }

        val fields = checksum.body.split(',')
        val identifier = fields.firstOrNull()?.uppercase().orEmpty()
        if (identifier.length != 5 || identifier.any { it !in IDENTIFIER_CHARACTERS }) {
            return NmeaParseResult.Malformed(raw, MalformedReason.SENTENCE_IDENTIFIER)
        }
        val origin = ObservationOrigin(
            source = context.source,
            sessionGeneration = context.sessionGeneration,
            talker = identifier.take(2),
            formatter = identifier.takeLast(3),
            sender = context.sender,
        )
        val sentence = NmeaSentence(
            raw = raw,
            body = checksum.body,
            prefix = checksum.prefix,
            origin = origin,
            payloadFields = fields.drop(1),
            checksumTrust = checksum.trust,
            groupId = context.observationGroupId,
            semanticDiscriminator = semanticDiscriminator(origin.formatter, fields),
            receivedAtMonotonicMillis = context.receivedAtMonotonicMillis,
        )
        val state = ParseState(sentence, fields)
        return when (origin.formatter) {
            "RMC" -> parseRmc(state)
            "GGA" -> parseGga(state)
            "GLL" -> parseGll(state)
            "VTG" -> parseVtg(state)
            "ZDA" -> parseZda(state)
            "HDG" -> parseHdg(state)
            "HDM" -> parseHeading(state, HeadingReference.MAGNETIC, "M")
            "HDT" -> parseHeading(state, HeadingReference.TRUE, "T")
            "DPT" -> parseDpt(state)
            "DBT" -> parseDbt(state)
            "MWD" -> parseMwd(state)
            "MWV" -> parseMwv(state)
            else -> NmeaParseResult.Unsupported(sentence)
        }
    }

    private fun parseRmc(state: ParseState): NmeaParseResult {
        val status = state.token(2)
        val mode = state.token(12)
        if (status == "V" || mode == "N") {
            state.invalidate(DataKey.Position)
            state.invalidate(DataKey.SpeedOverGround)
            state.invalidate(DataKey.CourseOverGround)
            return state.explicit(ExplicitInvalidReason.NAVIGATION_RECEIVER_WARNING)
        }
        val invalidStatus = status.isNotEmpty() && status != "A"
        val invalidMode = mode.isNotEmpty() && mode !in VALID_FAA_MODES
        if (invalidStatus) {
            state.error("status", state.field(2), NmeaFieldErrorReason.INVALID_STATUS)
        }
        if (invalidMode) {
            state.error("mode", state.field(12), NmeaFieldErrorReason.INVALID_STATUS)
        }
        if (invalidStatus || invalidMode) return state.parsed()
        if (status != "A") return state.parsed()

        val sourceTime = state.rmcEpochMillis(timeIndex = 1, dateIndex = 9)
        state.position(latitudeIndex = 3, northSouthIndex = 4, longitudeIndex = 5, eastWestIndex = 6)
            ?.let { state.valid(DataKey.Position, it, sourceTime) }
        state.nonNegative(7, "speedOverGround")
            ?.let { state.validDecimal(DataKey.SpeedOverGround, it, MarineUnit.KNOTS, sourceTime) }
        state.angle(8, "courseOverGround")
            ?.let { state.validDecimal(DataKey.CourseOverGround, it, MarineUnit.DEGREES, sourceTime) }
        state.signedVariation(10, 11, "magneticVariation")?.let {
            state.validDecimal(DataKey.MagneticVariation, it, MarineUnit.DEGREES, sourceTime)
        }
        sourceTime?.let {
            state.valid(DataKey.SourceTime, MarineValue.UtcEpochMillis(it), sourceTime = it)
        }
        return state.parsed()
    }

    private fun parseGga(state: ParseState): NmeaParseResult {
        val quality = state.boundedNonNegativeInteger(6, "fixQuality", maxInclusive = 8)
        quality?.let { state.valid(DataKey.FixQuality, MarineValue.Count(it)) }
        state.nonNegativeInteger(7, "satellites")
            ?.let { state.valid(DataKey.Satellites, MarineValue.Count(it)) }
        state.nonNegative(8, "horizontalDilution")
            ?.let { state.validDecimal(DataKey.HorizontalDilution, it, MarineUnit.DIMENSIONLESS) }
        return when {
            quality == 0 -> {
                state.invalidate(DataKey.Position)
                state.invalidate(DataKey.Altitude)
                state.explicit(ExplicitInvalidReason.NO_POSITION_FIX)
            }
            quality != null -> {
                state.position(2, 3, 4, 5)?.let { state.valid(DataKey.Position, it) }
                state.finiteNumber(9, "altitude")?.let { altitude ->
                    val unit = state.token(10)
                    if (unit == "M") {
                        state.validDecimal(DataKey.Altitude, altitude, MarineUnit.METERS)
                    } else {
                        state.error("altitudeUnit", state.field(10), NmeaFieldErrorReason.INVALID_UNIT)
                    }
                }
                state.parsed()
            }
            else -> state.parsed()
        }
    }

    private fun parseGll(state: ParseState): NmeaParseResult {
        val status = state.token(6)
        val mode = state.token(7)
        if (status == "V" || mode == "N") {
            state.invalidate(DataKey.Position)
            return state.explicit(ExplicitInvalidReason.NAVIGATION_RECEIVER_WARNING)
        }
        val invalidStatus = status.isNotEmpty() && status != "A"
        val invalidMode = mode.isNotEmpty() && mode !in VALID_FAA_MODES
        if (invalidStatus) {
            state.error("status", state.field(6), NmeaFieldErrorReason.INVALID_STATUS)
        }
        if (invalidMode) {
            state.error("mode", state.field(7), NmeaFieldErrorReason.INVALID_STATUS)
        }
        if (invalidStatus || invalidMode) return state.parsed()
        if (status == "A") {
            state.position(1, 2, 3, 4)?.let { state.valid(DataKey.Position, it) }
        }
        return state.parsed()
    }

    private fun parseVtg(state: ParseState): NmeaParseResult {
        val mode = state.token(9)
        if (mode == "N") {
            state.invalidate(DataKey.CourseOverGround)
            state.invalidate(DataKey.SpeedOverGround)
            return state.explicit(ExplicitInvalidReason.NAVIGATION_RECEIVER_WARNING)
        }
        if (mode.isNotEmpty() && mode !in VALID_FAA_MODES) {
            state.error("mode", state.field(9), NmeaFieldErrorReason.INVALID_STATUS)
            return state.parsed()
        }
        state.unitAngle(1, 2, expectedUnit = "T", fieldName = "trueCourse")
            ?.let { state.validDecimal(DataKey.CourseOverGround, it, MarineUnit.DEGREES) }

        val knots = state.unitNonNegative(5, 6, expectedUnit = "N", fieldName = "speedKnots")
        val kilometresPerHour = if (knots == null) {
            state.unitNonNegative(7, 8, expectedUnit = "K", fieldName = "speedKilometresPerHour")
        } else {
            null
        }
        (knots ?: kilometresPerHour?.times(KILOMETRES_PER_HOUR_TO_KNOTS))?.let {
            state.validDecimal(DataKey.SpeedOverGround, it, MarineUnit.KNOTS)
        }
        return state.parsed()
    }

    private fun parseZda(state: ParseState): NmeaParseResult {
        state.zdaEpochMillis()?.let {
            state.valid(DataKey.SourceTime, MarineValue.UtcEpochMillis(it), sourceTime = it)
        }
        return state.parsed()
    }

    private fun parseHdg(state: ParseState): NmeaParseResult {
        state.angle(1, "magneticHeading")?.let {
            state.validDecimal(DataKey.Heading(HeadingReference.MAGNETIC), it, MarineUnit.DEGREES)
        }
        state.signedVariation(4, 5, "magneticVariation")?.let {
            state.validDecimal(DataKey.MagneticVariation, it, MarineUnit.DEGREES)
        }
        return state.parsed()
    }

    private fun parseHeading(
        state: ParseState,
        reference: HeadingReference,
        expectedIndicator: String,
    ): NmeaParseResult {
        state.angle(1, "heading")?.let { heading ->
            val indicator = state.token(2)
            if (indicator == expectedIndicator) {
                state.validDecimal(DataKey.Heading(reference), heading, MarineUnit.DEGREES)
            } else {
                state.error("headingReference", state.field(2), NmeaFieldErrorReason.INVALID_REFERENCE)
            }
        }
        return state.parsed()
    }

    private fun parseDpt(state: ParseState): NmeaParseResult {
        val transducerDepth = state.nonNegative(1, "depth") ?: return state.parsed()
        state.validDecimal(
            DataKey.Depth(DepthReference.BELOW_TRANSDUCER),
            transducerDepth,
            MarineUnit.METERS,
        )
        state.finiteNumber(2, "transducerOffset")?.let { offset ->
            val adjusted = transducerDepth + offset
            if (!adjusted.isFinite() || adjusted < 0.0) {
                state.error("adjustedDepth", adjusted.toString(), NmeaFieldErrorReason.OUT_OF_RANGE)
            } else {
                val reference = when {
                    offset > 0.0 -> DepthReference.BELOW_SURFACE
                    offset < 0.0 -> DepthReference.BELOW_KEEL
                    else -> null
                }
                reference?.let {
                    state.validDecimal(DataKey.Depth(it), adjusted, MarineUnit.METERS)
                }
            }
        }
        return state.parsed()
    }

    private fun parseDbt(state: ParseState): NmeaParseResult {
        val metres = state.unitNonNegative(3, 4, expectedUnit = "M", fieldName = "depthMetres")
        val feet = if (metres == null) {
            state.unitNonNegative(1, 2, expectedUnit = "F", fieldName = "depthFeet")
        } else {
            null
        }
        val fathoms = if (metres == null && feet == null) {
            state.unitNonNegative(5, 6, expectedUnit = "F", fieldName = "depthFathoms")
        } else {
            null
        }
        val resolvedMetres = metres ?: feet?.times(FEET_TO_METRES) ?: fathoms?.times(FATHOMS_TO_METRES)
        resolvedMetres?.let {
            state.validDecimal(DataKey.Depth(DepthReference.BELOW_TRANSDUCER), it, MarineUnit.METERS)
        }
        return state.parsed()
    }

    private fun parseMwd(state: ParseState): NmeaParseResult {
        state.unitAngle(1, 2, expectedUnit = "T", fieldName = "trueWindDirection")?.let {
            state.validDecimal(DataKey.WindAngle(WindReference.TRUE_NORTH), it, MarineUnit.DEGREES)
        }
        state.unitAngle(3, 4, expectedUnit = "M", fieldName = "magneticWindDirection")?.let {
            state.validDecimal(DataKey.WindAngle(WindReference.MAGNETIC_NORTH), it, MarineUnit.DEGREES)
        }
        val knots = state.unitNonNegative(5, 6, expectedUnit = "N", fieldName = "trueWindSpeedKnots")
        val metresPerSecond = if (knots == null) {
            state.unitNonNegative(7, 8, expectedUnit = "M", fieldName = "trueWindSpeedMetresPerSecond")
        } else {
            null
        }
        (knots ?: metresPerSecond?.times(METRES_PER_SECOND_TO_KNOTS))?.let {
            state.validDecimal(
                DataKey.WindSpeed(WindSpeedReference.TRUE),
                it,
                MarineUnit.KNOTS,
            )
        }
        return state.parsed()
    }

    private fun parseMwv(state: ParseState): NmeaParseResult {
        val reference = when (state.token(2)) {
            "R" -> WindReference.APPARENT
            "T" -> WindReference.TRUE_RELATIVE
            else -> {
                state.error("windReference", state.field(2), NmeaFieldErrorReason.INVALID_REFERENCE)
                return state.parsed()
            }
        }
        return when (state.token(5)) {
            "V" -> {
                state.invalidate(DataKey.WindAngle(reference))
                state.invalidate(DataKey.WindSpeed(reference.toSpeedReference()))
                state.explicit(ExplicitInvalidReason.WIND_DATA_INVALID)
            }
            "A" -> {
                state.angle(1, "windAngle")?.let {
                    state.validDecimal(DataKey.WindAngle(reference), it, MarineUnit.DEGREES)
                }
                state.windSpeed(3, 4)?.let {
                    state.validDecimal(DataKey.WindSpeed(reference.toSpeedReference()), it, MarineUnit.KNOTS)
                }
                state.parsed()
            }
            "" -> state.parsed()
            else -> {
                state.error("status", state.field(5), NmeaFieldErrorReason.INVALID_STATUS)
                state.parsed()
            }
        }
    }

    private class ParseState(
        val sentence: NmeaSentence,
        private val fields: List<String>,
    ) {
        private val mutableObservations = mutableListOf<MarineObservation>()
        private val mutableErrors = mutableListOf<NmeaFieldError>()

        fun field(index: Int): String? = fields.getOrNull(index)

        fun token(index: Int): String = field(index)?.trim()?.uppercase().orEmpty()

        fun error(fieldName: String, rawValue: String?, reason: NmeaFieldErrorReason) {
            mutableErrors += NmeaFieldError(fieldName, rawValue, reason)
        }

        fun valid(
            key: DataKey,
            value: MarineValue,
            sourceTime: Long? = null,
        ) {
            mutableObservations += MarineObservation(
                key = key,
                value = value,
                validity = ObservationValidity.VALID,
                origin = sentence.origin,
                measuredAtMillis = sentence.receivedAtMonotonicMillis,
                groupId = sentence.groupId,
                checksumTrust = sentence.checksumTrust,
                sourceTimeEpochMillis = sourceTime,
            )
        }

        fun validDecimal(
            key: DataKey,
            value: Double,
            unit: MarineUnit,
            sourceTime: Long? = null,
        ) = valid(key, MarineValue.Decimal(value, unit), sourceTime)

        fun invalidate(key: DataKey) {
            mutableObservations += MarineObservation(
                key = key,
                value = null,
                validity = ObservationValidity.EXPLICIT_INVALID,
                origin = sentence.origin,
                measuredAtMillis = sentence.receivedAtMonotonicMillis,
                groupId = sentence.groupId,
                checksumTrust = sentence.checksumTrust,
            )
        }

        fun parsed(): NmeaParseResult.Parsed = NmeaParseResult.Parsed(
            sentence = sentence,
            observations = mutableObservations.toList(),
            fieldErrors = mutableErrors.toList(),
        )

        fun explicit(reason: ExplicitInvalidReason): NmeaParseResult.ExplicitInvalid =
            NmeaParseResult.ExplicitInvalid(
                sentence = sentence,
                observations = mutableObservations.toList(),
                reason = reason,
                fieldErrors = mutableErrors.toList(),
            )

        fun finiteNumber(index: Int, fieldName: String): Double? {
            val raw = field(index)?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val value = raw.toDoubleOrNull()
            if (value == null || !value.isFinite()) {
                error(fieldName, raw, NmeaFieldErrorReason.INVALID_NUMBER)
                return null
            }
            return value
        }

        fun nonNegative(
            index: Int,
            fieldName: String,
            maxInclusive: Double? = null,
        ): Double? {
            val value = finiteNumber(index, fieldName) ?: return null
            if (value < 0.0 || maxInclusive != null && value > maxInclusive) {
                error(fieldName, field(index), NmeaFieldErrorReason.OUT_OF_RANGE)
                return null
            }
            return value
        }

        fun nonNegativeInteger(index: Int, fieldName: String): Int? {
            val raw = field(index)?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val value = raw.toIntOrNull()
            if (value == null) {
                error(fieldName, raw, NmeaFieldErrorReason.INVALID_NUMBER)
                return null
            }
            if (value < 0) {
                error(fieldName, raw, NmeaFieldErrorReason.OUT_OF_RANGE)
                return null
            }
            return value
        }

        fun boundedNonNegativeInteger(
            index: Int,
            fieldName: String,
            maxInclusive: Int,
        ): Int? {
            val value = nonNegativeInteger(index, fieldName) ?: return null
            if (value > maxInclusive) {
                error(fieldName, field(index), NmeaFieldErrorReason.OUT_OF_RANGE)
                return null
            }
            return value
        }

        fun angle(index: Int, fieldName: String): Double? {
            val value = finiteNumber(index, fieldName) ?: return null
            if (value < 0.0 || value >= 360.0) {
                error(fieldName, field(index), NmeaFieldErrorReason.OUT_OF_RANGE)
                return null
            }
            return value
        }

        fun unitAngle(
            valueIndex: Int,
            unitIndex: Int,
            expectedUnit: String,
            fieldName: String,
        ): Double? {
            if (field(valueIndex).isNullOrBlank()) return null
            if (token(unitIndex) != expectedUnit) {
                error("${fieldName}Unit", field(unitIndex), NmeaFieldErrorReason.INVALID_UNIT)
                return null
            }
            return angle(valueIndex, fieldName)
        }

        fun unitNonNegative(
            valueIndex: Int,
            unitIndex: Int,
            expectedUnit: String,
            fieldName: String,
        ): Double? {
            if (field(valueIndex).isNullOrBlank()) return null
            if (token(unitIndex) != expectedUnit) {
                error("${fieldName}Unit", field(unitIndex), NmeaFieldErrorReason.INVALID_UNIT)
                return null
            }
            return nonNegative(valueIndex, fieldName)
        }

        fun windSpeed(valueIndex: Int, unitIndex: Int): Double? {
            val value = nonNegative(valueIndex, "windSpeed") ?: return null
            return when (token(unitIndex)) {
                "N" -> value
                "M" -> value * METRES_PER_SECOND_TO_KNOTS
                "K" -> value * KILOMETRES_PER_HOUR_TO_KNOTS
                else -> {
                    error("speedUnit", field(unitIndex), NmeaFieldErrorReason.INVALID_UNIT)
                    null
                }
            }
        }

        fun signedVariation(
            valueIndex: Int,
            directionIndex: Int,
            fieldName: String,
        ): Double? {
            val rawValue = field(valueIndex)?.trim().orEmpty()
            val rawDirection = field(directionIndex)?.trim().orEmpty()
            if (rawValue.isEmpty() && rawDirection.isEmpty()) return null
            if (rawValue.isEmpty() || rawDirection.isEmpty()) {
                error(fieldName, "$rawValue,$rawDirection", NmeaFieldErrorReason.INCOMPLETE_PAIR)
                return null
            }
            val variation = nonNegative(valueIndex, fieldName, maxInclusive = 180.0) ?: return null
            return when (token(directionIndex)) {
                "E" -> variation
                "W" -> -variation
                else -> {
                    error("${fieldName}Direction", field(directionIndex), NmeaFieldErrorReason.INVALID_REFERENCE)
                    null
                }
            }
        }

        fun position(
            latitudeIndex: Int,
            northSouthIndex: Int,
            longitudeIndex: Int,
            eastWestIndex: Int,
        ): MarineValue.Position? {
            val rawParts = listOf(
                field(latitudeIndex),
                field(northSouthIndex),
                field(longitudeIndex),
                field(eastWestIndex),
            )
            if (rawParts.all { it.isNullOrBlank() }) return null
            if (rawParts.any { it.isNullOrBlank() }) {
                error("position", rawParts.joinToString(",") { it.orEmpty() }, NmeaFieldErrorReason.INCOMPLETE_PAIR)
                return null
            }
            val latitude = coordinate(
                valueIndex = latitudeIndex,
                hemisphereIndex = northSouthIndex,
                latitude = true,
            ) ?: return null
            val longitude = coordinate(
                valueIndex = longitudeIndex,
                hemisphereIndex = eastWestIndex,
                latitude = false,
            ) ?: return null
            return MarineValue.Position(latitude, longitude)
        }

        private fun coordinate(
            valueIndex: Int,
            hemisphereIndex: Int,
            latitude: Boolean,
        ): Double? {
            val raw = field(valueIndex)?.trim().orEmpty()
            val hemisphere = token(hemisphereIndex)
            val permitted = if (latitude) setOf("N", "S") else setOf("E", "W")
            if (hemisphere !in permitted) {
                error(
                    if (latitude) "latitudeHemisphere" else "longitudeHemisphere",
                    field(hemisphereIndex),
                    NmeaFieldErrorReason.INVALID_REFERENCE,
                )
                return null
            }
            val packed = raw.toDoubleOrNull()
            if (packed == null || !packed.isFinite()) {
                error(
                    if (latitude) "latitude" else "longitude",
                    raw,
                    NmeaFieldErrorReason.INVALID_NUMBER,
                )
                return null
            }
            if (packed < 0.0) {
                error(
                    if (latitude) "latitude" else "longitude",
                    raw,
                    NmeaFieldErrorReason.OUT_OF_RANGE,
                )
                return null
            }
            val degrees = (packed / 100.0).toInt()
            val minutes = packed - degrees * 100.0
            val maxDegrees = if (latitude) 90 else 180
            if (minutes !in 0.0..<60.0 || degrees !in 0..maxDegrees || degrees == maxDegrees && minutes > 0.0) {
                error(
                    if (latitude) "latitude" else "longitude",
                    raw,
                    NmeaFieldErrorReason.OUT_OF_RANGE,
                )
                return null
            }
            val absolute = degrees + minutes / 60.0
            return if (hemisphere == "S" || hemisphere == "W") -absolute else absolute
        }

        fun rmcEpochMillis(timeIndex: Int, dateIndex: Int): Long? {
            val rawTime = field(timeIndex)?.trim().orEmpty()
            val rawDate = field(dateIndex)?.trim().orEmpty()
            if (rawTime.isEmpty() && rawDate.isEmpty()) return null
            if (rawTime.isEmpty() || rawDate.length != 6 || rawDate.any { !it.isDigit() }) {
                error("sourceDateTime", "$rawDate $rawTime", NmeaFieldErrorReason.INVALID_DATE_TIME)
                return null
            }
            val time = parseClock(rawTime)
            if (time == null) {
                error("sourceDateTime", "$rawDate $rawTime", NmeaFieldErrorReason.INVALID_DATE_TIME)
                return null
            }
            return try {
                val day = rawDate.substring(0, 2).toInt()
                val month = rawDate.substring(2, 4).toInt()
                val shortYear = rawDate.substring(4, 6).toInt()
                val year = if (shortYear >= RMC_YEAR_PIVOT) 1900 + shortYear else 2000 + shortYear
                LocalDateTime.of(LocalDate.of(year, month, day), time).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: DateTimeException) {
                error("sourceDateTime", "$rawDate $rawTime", NmeaFieldErrorReason.INVALID_DATE_TIME)
                null
            }
        }

        fun zdaEpochMillis(): Long? {
            val rawTime = field(1)?.trim().orEmpty()
            val rawDay = field(2)?.trim().orEmpty()
            val rawMonth = field(3)?.trim().orEmpty()
            val rawYear = field(4)?.trim().orEmpty()
            if (listOf(rawTime, rawDay, rawMonth, rawYear).all(String::isEmpty)) return null
            val time = parseClock(rawTime)
            val day = rawDay.toIntOrNull()
            val month = rawMonth.toIntOrNull()
            val year = rawYear.toIntOrNull()
            if (time == null || day == null || month == null || year == null) {
                error("sourceDateTime", "$rawYear-$rawMonth-$rawDay $rawTime", NmeaFieldErrorReason.INVALID_DATE_TIME)
                return null
            }
            return try {
                LocalDateTime.of(LocalDate.of(year, month, day), time).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: DateTimeException) {
                error("sourceDateTime", "$rawYear-$rawMonth-$rawDay $rawTime", NmeaFieldErrorReason.INVALID_DATE_TIME)
                null
            }
        }

        private fun parseClock(raw: String): LocalTime? {
            if (raw.length < 6 || raw.take(6).any { !it.isDigit() }) return null
            return try {
                val hour = raw.substring(0, 2).toInt()
                val minute = raw.substring(2, 4).toInt()
                val secondDecimal = raw.substring(4).toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
                if (secondDecimal < 0.0 || secondDecimal >= 60.0) return null
                val second = secondDecimal.toInt()
                val nanoseconds = ((secondDecimal - second) * NANOS_PER_SECOND).toInt().coerceIn(0, 999_999_999)
                LocalTime.of(hour, minute, second, nanoseconds)
            } catch (_: DateTimeException) {
                null
            } catch (_: NumberFormatException) {
                null
            }
        }
    }

    private companion object {
        const val FEET_TO_METRES = 0.3048
        const val FATHOMS_TO_METRES = 1.8288
        const val METRES_PER_SECOND_TO_KNOTS = 1.943844
        const val KILOMETRES_PER_HOUR_TO_KNOTS = 0.539956803
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val RMC_YEAR_PIVOT = 80
        val IDENTIFIER_CHARACTERS = ('A'..'Z').toSet() + ('0'..'9').toSet()
        val VALID_FAA_MODES = setOf("A", "D", "E", "F", "M", "P", "R", "S")

        fun semanticDiscriminator(
            formatter: String,
            fields: List<String>,
        ): SentenceSemanticDiscriminator? = when (formatter) {
            "MWV" -> fields.getOrNull(2)?.trim()?.uppercase()
                ?.takeIf { it == "R" || it == "T" }
                ?.let { SentenceSemanticDiscriminator("MWV:$it") }
            else -> null
        }
    }
}

private fun WindReference.toSpeedReference(): WindSpeedReference = when (this) {
    WindReference.APPARENT -> WindSpeedReference.APPARENT
    WindReference.TRUE_RELATIVE -> WindSpeedReference.TRUE
    WindReference.TRUE_NORTH,
    WindReference.MAGNETIC_NORTH,
    -> error("Compass wind-direction references do not define a wind-speed kind")
}

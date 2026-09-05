package com.yokuli.marine.map.domain

import java.util.Locale
import kotlin.math.abs

enum class CoordinateFormat { DECIMAL_DEGREES, DEGREES_DECIMAL_MINUTES }
enum class CoordinateField { LATITUDE, LONGITUDE }
enum class CoordinateError { EMPTY, INVALID_FORMAT, NON_FINITE, OUT_OF_RANGE, MINUTES_OUT_OF_RANGE, SIGN_HEMISPHERE_CONFLICT }

sealed interface CoordinateParseResult {
    data class Success(val point: GeoPoint) : CoordinateParseResult
    data class Failure(val field: CoordinateField, val error: CoordinateError) : CoordinateParseResult
}

data class FormattedCoordinate(val latitude: String, val longitude: String)

object CoordinateCodec {
    fun parse(latitude: String, longitude: String, format: CoordinateFormat): CoordinateParseResult {
        val lat = parseAxis(latitude, CoordinateField.LATITUDE, format)
        if (lat is AxisResult.Failure) return CoordinateParseResult.Failure(CoordinateField.LATITUDE, lat.error)
        val lon = parseAxis(longitude, CoordinateField.LONGITUDE, format)
        if (lon is AxisResult.Failure) return CoordinateParseResult.Failure(CoordinateField.LONGITUDE, lon.error)
        return CoordinateParseResult.Success(GeoPoint((lat as AxisResult.Value).value, (lon as AxisResult.Value).value))
    }

    fun format(point: GeoPoint, format: CoordinateFormat): FormattedCoordinate = FormattedCoordinate(
        latitude = formatAxis(point.latitude, CoordinateField.LATITUDE, format),
        longitude = formatAxis(point.longitude, CoordinateField.LONGITUDE, format),
    )

    private fun parseAxis(input: String, field: CoordinateField, format: CoordinateFormat): AxisResult {
        val text = input.trim().uppercase(Locale.ROOT)
        if (text.isEmpty()) return AxisResult.Failure(CoordinateError.EMPTY)
        val match = when (format) {
            CoordinateFormat.DECIMAL_DEGREES -> DECIMAL.matchEntire(text)
            CoordinateFormat.DEGREES_DECIMAL_MINUTES -> DMM.matchEntire(text)
        } ?: return AxisResult.Failure(CoordinateError.INVALID_FORMAT)
        val degreesToken = match.groupValues[1]
        val rawDegrees = when (degreesToken.removePrefix("+").removePrefix("-")) {
            "NAN" -> Double.NaN
            "INFINITY" -> if (degreesToken.startsWith('-')) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            else -> degreesToken.toDoubleOrNull() ?: return AxisResult.Failure(CoordinateError.INVALID_FORMAT)
        }
        if (!rawDegrees.isFinite()) return AxisResult.Failure(CoordinateError.NON_FINITE)
        val minutes = if (format == CoordinateFormat.DEGREES_DECIMAL_MINUTES) {
            match.groupValues[2].toDoubleOrNull() ?: return AxisResult.Failure(CoordinateError.INVALID_FORMAT)
        } else {
            0.0
        }
        if (!minutes.isFinite()) return AxisResult.Failure(CoordinateError.NON_FINITE)
        if (minutes !in 0.0..<60.0) return AxisResult.Failure(CoordinateError.MINUTES_OUT_OF_RANGE)
        val hemisphere = match.groupValues.last().singleOrNull()
        if (hemisphere != null && hemisphere !in field.hemispheres) {
            return AxisResult.Failure(CoordinateError.INVALID_FORMAT)
        }
        val explicitPlus = match.groupValues[1].startsWith('+')
        val explicitMinus = match.groupValues[1].startsWith('-')
        val negativeHemisphere = hemisphere == 'S' || hemisphere == 'W'
        val positiveHemisphere = hemisphere == 'N' || hemisphere == 'E'
        if ((explicitMinus && positiveHemisphere) || (explicitPlus && negativeHemisphere)) {
            return AxisResult.Failure(CoordinateError.SIGN_HEMISPHERE_CONFLICT)
        }
        val magnitude = abs(rawDegrees) + minutes / 60.0
        val limit = if (field == CoordinateField.LATITUDE) 90.0 else 180.0
        if (magnitude > limit || (magnitude == limit && minutes != 0.0)) {
            return AxisResult.Failure(CoordinateError.OUT_OF_RANGE)
        }
        val negative = explicitMinus || negativeHemisphere
        return AxisResult.Value(if (negative) -magnitude else magnitude)
    }

    private fun formatAxis(value: Double, field: CoordinateField, format: CoordinateFormat): String {
        val negative = value < 0.0 || value.toRawBits() < 0L
        val hemisphere = when (field) {
            CoordinateField.LATITUDE -> if (negative) 'S' else 'N'
            CoordinateField.LONGITUDE -> if (negative) 'W' else 'E'
        }
        return when (format) {
            CoordinateFormat.DECIMAL_DEGREES -> String.format(Locale.US, "%.6f %s", abs(value), hemisphere)
            CoordinateFormat.DEGREES_DECIMAL_MINUTES -> {
                val absolute = abs(value)
                val degrees = absolute.toInt()
                val minutes = (absolute - degrees) * 60.0
                String.format(Locale.US, "%d %.6f %s", degrees, minutes, hemisphere)
            }
        }
    }

    private val CoordinateField.hemispheres: Set<Char>
        get() = if (this == CoordinateField.LATITUDE) setOf('N', 'S') else setOf('E', 'W')

    private sealed interface AxisResult {
        data class Value(val value: Double) : AxisResult
        data class Failure(val error: CoordinateError) : AxisResult
    }

    private val DECIMAL = Regex("^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+|NAN|INFINITY))\\s*°?\\s*([NSEW])?$")
    private val DMM = Regex("^([+-]?\\d+)\\s*°?\\s+(\\d+(?:\\.\\d*)?)\\s*(?:[′']|M)?\\s*([NSEW])?$")
}

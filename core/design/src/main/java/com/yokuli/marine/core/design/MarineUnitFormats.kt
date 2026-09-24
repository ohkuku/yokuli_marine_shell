package com.yokuli.marine.core.design

import com.yokuli.shell.contract.*
import java.util.Locale

/**
 * 全局显示单位的不可变快照。前台界面与后台通知共享换算，不依赖 Activity 或 Compose。
 * 参数均为领域规范单位；逆换算只用于输入保存，不改写传感器、航迹或 NMEA 报文。
 */
class MarineUnitFormats(val preferences: MarineUnitPreferences) {
    val distanceUnit = if (preferences.navigation == MeasurementUnitSystem.NAUTICAL) "nm" else "km"
    val speedUnit = if (preferences.navigation == MeasurementUnitSystem.NAUTICAL) "kn" else "km/h"
    val lengthUnit = if (preferences.length == LengthUnit.FEET) "ft" else "m"
    val depthUnit = when (preferences.depth) { DepthUnit.METERS -> "m"; DepthUnit.FEET -> "ft"; DepthUnit.FATHOMS -> "fathom" }
    val temperatureUnit = if (preferences.temperature == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
    val pressureUnit = when (preferences.pressure) { PressureUnit.HECTOPASCALS -> "hPa"; PressureUnit.KILOPASCALS -> "kPa"; PressureUnit.INCHES_OF_MERCURY -> "inHg" }
    private val distanceFactor = if (preferences.navigation == MeasurementUnitSystem.NAUTICAL) 1852.0 else 1000.0
    private val lengthFactor = if (preferences.length == LengthUnit.FEET) .3048 else 1.0
    private val depthFactor = when (preferences.depth) { DepthUnit.METERS -> 1.0; DepthUnit.FEET -> .3048; DepthUnit.FATHOMS -> 1.8288 }
    private val pressureFactor = when (preferences.pressure) { PressureUnit.HECTOPASCALS -> 1.0; PressureUnit.KILOPASCALS -> 10.0; PressureUnit.INCHES_OF_MERCURY -> 33.8638866667 }

    fun distanceValue(meters: Double) = meters / distanceFactor
    fun distanceMeters(value: Double) = value * distanceFactor
    fun speedValue(knots: Double) = MarineDisplayUnits.speedFromKnots(knots, preferences.navigation)
    fun speedKnots(value: Double) = if (preferences.navigation == MeasurementUnitSystem.NAUTICAL) value else value / 1.852
    fun lengthValue(meters: Double) = meters / lengthFactor
    fun lengthMeters(value: Double) = value * lengthFactor
    fun depthValue(meters: Double) = meters / depthFactor
    fun depthMeters(value: Double) = value * depthFactor
    fun temperatureValue(celsius: Double) = if (preferences.temperature == TemperatureUnit.FAHRENHEIT) celsius * 1.8 + 32 else celsius
    fun temperatureCelsius(value: Double) = if (preferences.temperature == TemperatureUnit.FAHRENHEIT) (value - 32) / 1.8 else value
    fun pressureValue(hpa: Double) = hpa / pressureFactor
    fun pressureHpa(value: Double) = value * pressureFactor

    /** 航程始终显示所选 nm/km；近距与尺寸明确调用 length，不隐式切换单位。 */
    fun distance(meters: Double?): String {
        val value = meters?.takeIf { it.isFinite() && it >= 0 }?.let(::distanceValue) ?: return "—"
        val digits = when { value == 0.0 || value >= .1 -> 2; value >= .01 -> 3; value >= .001 -> 4; else -> 5 }
        return number(value, distanceUnit, digits)
    }
    fun length(meters: Double?) = number(meters?.let(::lengthValue), lengthUnit)
    fun speed(knots: Double?) = number(knots?.let(::speedValue), speedUnit)
    fun depth(meters: Double?) = number(meters?.let(::depthValue), depthUnit)
    fun temperature(celsius: Double?) = number(celsius?.let(::temperatureValue), temperatureUnit)
    fun pressure(hpa: Double?, signed: Boolean = false): String {
        val digits = when (preferences.pressure) {
            PressureUnit.HECTOPASCALS -> if (signed) 1 else 0
            PressureUnit.KILOPASCALS -> if (signed) 2 else 1
            PressureUnit.INCHES_OF_MERCURY -> if (signed) 3 else 2
        }
        return number(hpa?.let(::pressureValue), pressureUnit, digits, signed)
    }

    private fun number(value: Double?, unit: String, digits: Int = 1, signed: Boolean = false): String =
        value?.takeIf { it.isFinite() }?.let {
            String.format(Locale.US, "%${if (signed) "+" else ""}.${digits}f %s", if (it == 0.0) 0.0 else it, unit)
        } ?: "—"
}

package com.yokuli.marine.core.design

import com.yokuli.shell.contract.*
import java.util.Locale

/**
 * 全局显示单位的不可变快照。前台界面与后台通知共享换算，不依赖 Activity 或 Compose。
 * 参数均为领域规范单位；逆换算只用于输入保存，不改写传感器、航迹或 NMEA 报文。
 */
class MarineUnitFormats(val preferences: MarineUnitPreferences) {
    val distanceUnit = preferences.distance.symbol
    val speedUnit = preferences.speed.symbol
    val lengthUnit = if (preferences.length == LengthUnit.FEET) "ft" else "m"
    val depthUnit = when (preferences.depth) { DepthUnit.METERS -> "m"; DepthUnit.FEET -> "ft"; DepthUnit.FATHOMS -> "fathom" }
    val temperatureUnit = if (preferences.temperature == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
    val pressureUnit = when (preferences.pressure) { PressureUnit.HECTOPASCALS -> "hPa"; PressureUnit.KILOPASCALS -> "kPa"; PressureUnit.INCHES_OF_MERCURY -> "inHg" }
    private val distanceFactor = preferences.distance.metersPerUnit
    private val lengthFactor = if (preferences.length == LengthUnit.FEET) .3048 else 1.0
    private val depthFactor = when (preferences.depth) { DepthUnit.METERS -> 1.0; DepthUnit.FEET -> .3048; DepthUnit.FATHOMS -> 1.8288 }
    private val pressureFactor = when (preferences.pressure) { PressureUnit.HECTOPASCALS -> 1.0; PressureUnit.KILOPASCALS -> 10.0; PressureUnit.INCHES_OF_MERCURY -> 33.8638866667 }

    fun distanceValue(meters: Double) = meters / distanceFactor
    fun distanceMeters(value: Double) = value * distanceFactor
    fun speedValue(knots: Double) = knots * preferences.speed.unitsPerKnot
    fun speedKnots(value: Double) = value / preferences.speed.unitsPerKnot
    fun lengthValue(meters: Double) = meters / lengthFactor
    fun lengthMeters(value: Double) = value * lengthFactor
    fun depthValue(meters: Double) = meters / depthFactor
    fun depthMeters(value: Double) = value * depthFactor
    fun temperatureValue(celsius: Double) = if (preferences.temperature == TemperatureUnit.FAHRENHEIT) celsius * 1.8 + 32 else celsius
    fun temperatureCelsius(value: Double) = if (preferences.temperature == TemperatureUnit.FAHRENHEIT) (value - 32) / 1.8 else value
    fun pressureValue(hpa: Double) = hpa / pressureFactor
    fun pressureHpa(value: Double) = value * pressureFactor

    /** 距离始终采用用户选定的全尺度单位；尺寸明确调用 length，不隐式切换单位。 */
    fun distance(meters: Double?): String {
        val value = meters?.takeIf { it.isFinite() && it >= 0 }?.let(::distanceValue) ?: return "—"
        val digits = when { value == 0.0 || value >= .1 -> 2; value >= .01 -> 3; value >= .001 -> 4; value >= .0001 -> 5; else -> 6 }
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

    /** 比例尺在选定单位内取 1/2/5 × 10ⁿ，包括小数档；宽度由真实地理长度反算。 */
    fun scaleBar(maximumMeters: Double): DistanceScaleBar? {
        if (!maximumMeters.isFinite() || maximumMeters <= 0) return null
        val unit = preferences.effectiveScaleDistance
        val available = maximumMeters / unit.metersPerUnit
        if (!available.isFinite() || available <= 0) return null
        val exponent = kotlin.math.floor(kotlin.math.log10(available)).toInt()
        val power = Math.pow(10.0, exponent.toDouble())
        if (!power.isFinite() || power <= 0) return null
        val mantissa = available / power
        val step = if (mantissa >= 5) 5L else if (mantissa >= 2) 2L else 1L
        // 直接从 1/2/5 和十进制阶数构造标签，避免二进制误差渗入小数。
        val nice = java.math.BigDecimal.valueOf(step).scaleByPowerOfTen(exponent)
        val meters = nice.toDouble() * unit.metersPerUnit
        if (!meters.isFinite() || meters <= 0) return null
        val plain = nice.toPlainString()
        // 极端投影值也不能生成数百位小数并把比例尺白底撑出地图；单位始终不变。
        val label = if (plain.length <= 12) plain else nice.toString().replace("E+", "e").replace("E", "e")
        return DistanceScaleBar(meters, "$label ${unit.symbol}")
    }

    private fun number(value: Double?, unit: String, digits: Int = 1, signed: Boolean = false): String =
        value?.takeIf { it.isFinite() }?.let {
            String.format(Locale.US, "%${if (signed) "+" else ""}.${digits}f %s", if (it == 0.0) 0.0 else it, unit)
        } ?: "—"
}

/** 已确定标签及规范距离，原生地图只负责投影和绘制，不重新选单位。 */
data class DistanceScaleBar(val meters: Double, val label: String)

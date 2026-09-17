package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.contract.MeasurementUnitSystem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun OsStore.formatDistance(meters: Double?): String {
    val value = meters?.takeIf { it.isFinite() && it >= 0 } ?: return "—"
    val nautical = measurementUnits == MeasurementUnitSystem.NAUTICAL
    if (value < if (nautical) 185.2 else 1000.0) return "${value.roundToInt()} m"
    return String.format(Locale.US, "%.2f %s", MarineDisplayUnits.distanceFromMeters(value, measurementUnits), if (nautical) "nm" else "km")
}
fun OsStore.formatSpeed(knots: Double?): String {
    val value = knots?.takeIf { it.isFinite() } ?: return "—"
    return String.format(Locale.US, "%.1f %s", MarineDisplayUnits.speedFromKnots(value, measurementUnits), if (measurementUnits == MeasurementUnitSystem.NAUTICAL) "kn" else "km/h")
}
fun OsStore.formatDepth(meters: Double?): String = meters?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.1f m", it) } ?: "—"
/** 方向统一归一化为 0..359，避免四舍五入后出现 360°。 */
fun OsStore.formatBearing(degrees: Double?): String = degrees?.takeIf { it.isFinite() }?.let {
    "%03d°".format(Locale.US, (((it % 360 + 360) % 360).roundToInt()) % 360)
} ?: "—"
fun OsStore.formatAngle(degrees: Double?): String = degrees?.takeIf { it.isFinite() }?.let { "%.1f°".format(Locale.US, it) } ?: "—"
fun OsStore.formatTemperature(celsius: Double?): String = celsius?.takeIf { it.isFinite() }?.let { "%.1f °C".format(Locale.US, it) } ?: "—"
/** 读数的键映射内部规范单位；界面禁止把 raw unit 与转换后的值混在一起。 */
fun OsStore.formatMetric(key: String, value: Double?): String = when(key.lowercase()) {
    "sog", "aws", "tws", "bsp", "stw", "vmg", "vmc", "current_drift" -> formatSpeed(value)
    "cog", "heading", "twd", "current_set", "waypoint_bearing" -> formatBearing(value)
    "awa", "twa", "heel", "pitch", "rudder" -> formatAngle(value)
    "depth", "ukc" -> formatDepth(value)
    "water", "air", "temperature" -> formatTemperature(value)
    "pressure" -> value?.takeIf {it.isFinite()}?.let {"%.0f hPa".format(Locale.US,it)} ?: "—"
    "pressure_1h", "pressure_3h", "pressure_6h" -> value?.takeIf {it.isFinite()}?.let {"%+.1f hPa".format(Locale.US,it)} ?: "—"
    "roll_rate", "pitch_rate" -> value?.takeIf {it.isFinite()}?.let {"%.1f°/s".format(Locale.US,it)} ?: "—"
    "rot" -> value?.takeIf {it.isFinite()}?.let {"%.1f°/min".format(Locale.US,it)} ?: "—"
    "roll_period" -> value?.takeIf {it.isFinite()}?.let {"%.1f s".format(Locale.US,it)} ?: "—"
    "waypoint_distance", "total_log", "trip_log" -> formatDistance(value?.times(1852.0))
    "xte" -> value?.takeIf {it.isFinite()}?.let { (if(it<0) "−" else "") + formatDistance(abs(it)*1852.0) } ?: "—"
    "impacts" -> value?.takeIf {it.isFinite()}?.roundToInt()?.toString() ?: "—"
    else -> com.yokuli.marine.shell.rebuild.decimal(value)
}
fun OsStore.formatCoordinates(point: GeoPoint): String {
    if (!point.valid()) return "—"
    if (coordinateFormat == "DD") return String.format(Locale.US, "%.6f°, %.6f°", point.lat, point.lon)
    fun coordinate(value: Double, positive: String, negative: String): String {
        val direction = if (value < 0) negative else positive
        return if (coordinateFormat == "DMS") {
            val tenths = (abs(value) * 36_000).roundToLong()
            String.format(Locale.US, "%d° %02d′ %04.1f″ %s", tenths / 36_000, tenths / 600 % 60, tenths % 600 / 10.0, direction)
        } else {
            val millis = (abs(value) * 60_000).roundToLong()
            String.format(Locale.US, "%d° %06.3f′ %s", millis / 60_000, millis % 60_000 / 1000.0, direction)
        }
    }
    return coordinate(point.lat, "N", "S") + "  " + coordinate(point.lon, "E", "W")
}
fun OsStore.formatLatitude(value: Double): String = if(value.isFinite() && value in -90.0..90.0) {
    if(coordinateFormat=="DD") "%.6f".format(Locale.US,value) else formatCoordinates(GeoPoint(value,0.0)).substringBefore("  ")
} else ""
fun OsStore.formatLongitude(value: Double): String = if(value.isFinite() && value in -180.0..180.0) {
    if(coordinateFormat=="DD") "%.6f".format(Locale.US,value) else formatCoordinates(GeoPoint(0.0,value)).substringAfter("  ")
} else ""

/** 接受十进制度、度分或度分秒；校验范围、方向及分秒，拒绝含糊或矛盾输入。 */
fun parseCoordinate(text: String, latitude: Boolean): Double? {
    var raw=text.trim().uppercase(Locale.US)
    val direction=raw.lastOrNull()?.takeIf {it in "NSEW"} ?: raw.firstOrNull()?.takeIf {it in "NSEW"}
    if(direction!=null) {
        if(latitude && direction !in "NS" || !latitude && direction !in "EW") return null
        raw=if(raw.last()==direction)raw.dropLast(1).trim() else raw.drop(1).trim()
    }
    if(raw.isBlank() || raw.any {!it.isDigit() && it !in "+-.,°′'″\" :\t\n"}) return null
    val tokens=Regex("[+-]?\\d+(?:[.,]\\d+)?").findAll(raw).map {it.value.replace(',','.').toDoubleOrNull() ?: Double.NaN}.toList()
    if(tokens.size !in 1..3 || tokens.any {!it.isFinite()}) return null
    val residue=raw.replace(Regex("[+-]?\\d+(?:[.,]\\d+)?"),"")
    if(residue.any {it !in "°′'″\" :\t\n"})return null
    if(tokens.size>1 && tokens[0]%1.0!=0.0)return null
    if(tokens.drop(1).any {it<0 || it>=60})return null
    if(tokens.size==3 && tokens[1]%1.0!=0.0)return null
    if((tokens[0]<0 || raw.startsWith('-')) && direction in listOf('N','E'))return null
    val value=abs(tokens[0])+(tokens.getOrNull(1) ?: 0.0)/60+(tokens.getOrNull(2) ?: 0.0)/3600
    if(value>if(latitude)90.0 else 180.0)return null
    return if(tokens[0]<0 || raw.startsWith('-') || direction in listOf('S','W')) -value else value
}
internal fun durationLabel(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return "%02d:%02d".format(Locale.US, minutes / 60, minutes % 60)
}

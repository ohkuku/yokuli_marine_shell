package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 所有可见读数共用进程级单位格式器，后台通知也使用同一不可变实现。 */
val OsStore.distanceUnitLabel get() = unitFormats.distanceUnit
val OsStore.speedUnitLabel get() = unitFormats.speedUnit
val OsStore.lengthUnitLabel get() = unitFormats.lengthUnit
val OsStore.depthUnitLabel get() = unitFormats.depthUnit
val OsStore.temperatureUnitLabel get() = unitFormats.temperatureUnit
val OsStore.pressureUnitLabel get() = unitFormats.pressureUnit

fun OsStore.distanceValue(meters: Double) = unitFormats.distanceValue(meters)
fun OsStore.distanceMeters(value: Double) = unitFormats.distanceMeters(value)
fun OsStore.speedValue(knots: Double) = unitFormats.speedValue(knots)
fun OsStore.speedKnots(value: Double) = unitFormats.speedKnots(value)
fun OsStore.lengthValue(meters: Double) = unitFormats.lengthValue(meters)
fun OsStore.lengthMeters(value: Double) = unitFormats.lengthMeters(value)
fun OsStore.depthValue(meters: Double) = unitFormats.depthValue(meters)
fun OsStore.depthMeters(value: Double) = unitFormats.depthMeters(value)
fun OsStore.temperatureValue(celsius: Double) = unitFormats.temperatureValue(celsius)
fun OsStore.temperatureCelsius(value: Double) = unitFormats.temperatureCelsius(value)
fun OsStore.pressureValue(hpa: Double) = unitFormats.pressureValue(hpa)
fun OsStore.pressureHpa(value: Double) = unitFormats.pressureHpa(value)

fun OsStore.formatDistance(meters: Double?) = unitFormats.distance(meters)
fun OsStore.formatLength(meters: Double?) = unitFormats.length(meters)
fun OsStore.formatDepth(meters: Double?) = unitFormats.depth(meters)
fun OsStore.formatSpeed(knots: Double?) = unitFormats.speed(knots)
fun OsStore.formatTemperature(celsius: Double?) = unitFormats.temperature(celsius)
fun OsStore.formatPressure(hpa: Double?, signed: Boolean = false) = unitFormats.pressure(hpa, signed)
fun OsStore.formatPressureChange(hpa: Double?) = unitFormats.pressure(hpa, signed = true)
/** 方向统一归一化为 0..359，避免四舍五入后出现 360°。 */
fun OsStore.formatBearing(degrees: Double?): String = degrees?.takeIf { it.isFinite() }?.let {
    "%03d°".format(Locale.US, (((it % 360 + 360) % 360).roundToInt()) % 360)
} ?: "—"
fun OsStore.formatAngle(degrees: Double?): String = degrees?.takeIf { it.isFinite() }?.let { "%.1f°".format(Locale.US, it) } ?: "—"

/** 图表刻度先在显示空间选取整刻度；这些输入仍为 DataHub 声明的规范单位。 */
fun OsStore.displayMetricValue(key:String,raw:Double):Double=when(key.lowercase()) {
    "sog","aws","tws","bsp","stw","vmg","vmc","current_drift"->speedValue(raw)
    "depth","ukc"->depthValue(raw)
    "water","air","temperature"->temperatureValue(raw)
    "pressure","pressure_1h","pressure_3h","pressure_6h"->pressureValue(raw)
    "waypoint_distance","total_log","trip_log","xte"->distanceValue(raw*1852.0)
    else->raw
}
fun OsStore.displayMetricUnit(key:String):String=when(key.lowercase()) {
    "sog","aws","tws","bsp","stw","vmg","vmc","current_drift"->speedUnitLabel
    "depth","ukc"->depthUnitLabel
    "water","air","temperature"->temperatureUnitLabel
    "pressure","pressure_1h","pressure_3h","pressure_6h"->pressureUnitLabel
    "waypoint_distance","total_log","trip_log","xte"->distanceUnitLabel
    "cog","heading","twd","current_set","waypoint_bearing","awa","twa","heel","pitch","rudder"->"°"
    "roll_rate","pitch_rate"->"°/s"
    "rot"->"°/min"
    "roll_period"->"s"
    else->""
}
/** 读数的键映射内部规范单位；界面禁止把 raw unit 与转换后的值混在一起。 */
fun OsStore.formatMetric(key: String, value: Double?): String = when(key.lowercase()) {
    "sog", "aws", "tws", "bsp", "stw", "vmg", "vmc", "current_drift" -> formatSpeed(value)
    "cog", "heading", "twd", "current_set", "waypoint_bearing" -> formatBearing(value)
    "awa", "twa", "heel", "pitch", "rudder" -> formatAngle(value)
    "depth", "ukc" -> formatDepth(value)
    "water", "air", "temperature" -> formatTemperature(value)
    "pressure" -> formatPressure(value)
    "pressure_1h", "pressure_3h", "pressure_6h" -> formatPressureChange(value)
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

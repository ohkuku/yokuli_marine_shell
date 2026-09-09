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
internal fun durationLabel(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return "%02d:%02d".format(Locale.US, minutes / 60, minutes % 60)
}

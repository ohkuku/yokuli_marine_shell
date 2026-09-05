package com.yokuli.marine.map.domain

import kotlin.math.abs
import kotlin.math.ceil
import net.sf.geographiclib.Geodesic

data class GeodesicResult(
    val distanceMeters: Double,
    val initialBearingTrueDegrees: Double?,
    val azimuthAmbiguous: Boolean,
)

/** WGS84 ellipsoid inverse/direct calculations. Bearings are facts, never steering commands. */
object Wgs84Geodesic {
    fun inverse(from: GeoPoint, to: GeoPoint): GeodesicResult {
        if (from == to) return GeodesicResult(0.0, null, azimuthAmbiguous = true)
        val result = Geodesic.WGS84.Inverse(from.latitude, from.longitude, to.latitude, to.longitude)
        val ambiguous = hasMultipleShortestAzimuths(from, to)
        return GeodesicResult(
            distanceMeters = result.s12,
            initialBearingTrueDegrees = if (ambiguous) null else normalizeBearing(result.azi1),
            azimuthAmbiguous = ambiguous,
        )
    }

    fun densify(from: GeoPoint, to: GeoPoint, maxSegmentMeters: Double): List<GeoPoint> {
        require(maxSegmentMeters.isFinite() && maxSegmentMeters > 0.0)
        val inverse = Geodesic.WGS84.Inverse(from.latitude, from.longitude, to.latitude, to.longitude)
        if (inverse.s12 == 0.0) return listOf(from)
        val segmentCount = ceil(inverse.s12 / maxSegmentMeters).toInt().coerceAtLeast(1)
        return (0..segmentCount).map { index ->
            when (index) {
                0 -> from
                segmentCount -> to
                else -> {
                    val point = Geodesic.WGS84.Direct(
                        from.latitude,
                        from.longitude,
                        inverse.azi1,
                        inverse.s12 * index / segmentCount,
                    )
                    GeoPoint(point.lat2, normalizeLongitude(point.lon2))
                }
            }
        }
    }

    private fun hasMultipleShortestAzimuths(from: GeoPoint, to: GeoPoint): Boolean {
        val oppositePoles = abs(from.latitude) == 90.0 && abs(to.latitude) == 90.0 &&
            from.latitude == -to.latitude
        val longitudeDelta = abs(normalizeLongitude(to.longitude - from.longitude))
        val antipodalSymmetry = abs(from.latitude + to.latitude) < 1e-12 &&
            abs(longitudeDelta - 180.0) < 1e-12
        return oppositePoles || antipodalSymmetry
    }
}

data class Wgs84Polyline(
    val parts: List<List<GeoPoint>>,
    val bounds: GeoBounds,
) {
    companion object {
        fun build(points: List<GeoPoint>, maxSegmentMeters: Double = 25_000.0): Wgs84Polyline {
            require(points.isNotEmpty())
            require(maxSegmentMeters.isFinite() && maxSegmentMeters > 0.0)
            val dense = points.zipWithNext().flatMapIndexed { index, (from, to) ->
                Wgs84Geodesic.densify(from, to, maxSegmentMeters).let { segment ->
                    if (index == 0) segment else segment.drop(1)
                }
            }.ifEmpty { points }
            return Wgs84Polyline(splitAtAntimeridian(dense), minimalBounds(points))
        }
    }
}

private fun splitAtAntimeridian(points: List<GeoPoint>): List<List<GeoPoint>> {
    if (points.size < 2) return listOf(points)
    val parts = mutableListOf<MutableList<GeoPoint>>(mutableListOf(points.first()))
    points.zipWithNext().forEach { (from, to) ->
        val delta = to.longitude - from.longitude
        if (abs(delta) <= 180.0) {
            parts.last().add(to)
        } else {
            val unwrappedTo = if (delta < -180.0) to.longitude + 360.0 else to.longitude - 360.0
            val boundary = if (unwrappedTo > from.longitude) 180.0 else -180.0
            val fraction = (boundary - from.longitude) / (unwrappedTo - from.longitude)
            val latitude = from.latitude + (to.latitude - from.latitude) * fraction
            parts.last().add(GeoPoint(latitude, boundary))
            parts.add(mutableListOf(GeoPoint(latitude, -boundary), to))
        }
    }
    return parts.filter { it.isNotEmpty() }
}

fun minimalBounds(points: List<GeoPoint>): GeoBounds {
    require(points.isNotEmpty())
    val longitudes = points.map { if (it.longitude < 0.0) it.longitude + 360.0 else it.longitude }.sorted()
    var largestGap = Double.NEGATIVE_INFINITY
    var gapStartIndex = 0
    longitudes.indices.forEach { index ->
        val current = longitudes[index]
        val next = if (index == longitudes.lastIndex) longitudes.first() + 360.0 else longitudes[index + 1]
        val gap = next - current
        if (gap > largestGap) {
            largestGap = gap
            gapStartIndex = index
        }
    }
    val west = normalizeLongitude(longitudes[(gapStartIndex + 1) % longitudes.size])
    val east = normalizeLongitude(longitudes[gapStartIndex])
    return GeoBounds(
        south = points.minOf { it.latitude },
        west = west,
        north = points.maxOf { it.latitude },
        east = east,
    )
}

internal fun normalizeLongitude(value: Double): Double {
    require(value.isFinite())
    val wrapped = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    return if (wrapped == -180.0 && value > 0.0) 180.0 else wrapped
}

private fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

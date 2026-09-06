package com.yokuli.marine.navigation.domain

import net.sf.geographiclib.Geodesic

data class RouteLeg(
    val index: Int,
    val from: RoutePoint,
    val to: RoutePoint,
    val distanceMeters: Double,
    val initialBearingTrueDegrees: Double?,
)

data class PassageSummary(
    val legs: List<RouteLeg>,
    val distanceNauticalMiles: Double,
    val estimatedDurationMillis: Long?,
)

object NavigationRouteMath {
    fun summarize(route: RoutePlan): PassageSummary {
        val legs = route.points.zipWithNext().mapIndexed { index, (from, to) ->
            if (from.position == to.position) {
                RouteLeg(index, from, to, 0.0, null)
            } else {
                val inverse = Geodesic.WGS84.Inverse(
                    from.position.latitude,
                    from.position.longitude,
                    to.position.latitude,
                    to.position.longitude,
                )
                RouteLeg(index, from, to, inverse.s12, normalizeBearing(inverse.azi1))
            }
        }
        val distanceNm = legs.sumOf(RouteLeg::distanceMeters) / METERS_PER_NAUTICAL_MILE
        val duration = route.plannedSpeedKnots?.let { speed ->
            ((distanceNm / speed) * MILLIS_PER_HOUR).toLong().coerceAtLeast(0L)
        }
        return PassageSummary(legs, distanceNm, duration)
    }

    private fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    private const val METERS_PER_NAUTICAL_MILE = 1852.0
    private const val MILLIS_PER_HOUR = 3_600_000.0
}

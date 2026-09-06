package com.yokuli.marine.navigation.domain

import kotlin.math.PI
import kotlin.math.sin
import net.sf.geographiclib.Geodesic

object ActiveNavigationSolver {
    fun solve(route: RoutePlan, session: ActiveNavigationSession, fix: NavigationFix): NavigationSolution? {
        if (session.state != NavigationSessionState.ACTIVE || !fix.usablePosition) return null
        val current = requireNotNull(fix.position)
        val previous = route.points.getOrNull(session.activeLegIndex) ?: return null
        val next = route.points.getOrNull(session.activeLegIndex + 1) ?: return null
        val toNext = inverse(current, next.position)
        val leg = inverse(previous.position, next.position)
        val projection = projectOnLeg(previous.position, next.position, current, leg.distanceMeters, leg.initialBearing)
        val completeBefore = route.points.zipWithNext().take(session.activeLegIndex).sumOf { (from, to) ->
            inverse(from.position, to.position).distanceMeters
        }
        val totalDistance = route.points.zipWithNext().sumOf { (from, to) ->
            inverse(from.position, to.position).distanceMeters
        }
        val distanceNm = toNext.distanceMeters / METERS_PER_NAUTICAL_MILE
        val duration = fix.speedOverGroundKnots?.takeIf { it > 0.0 }?.let { speed ->
            ((distanceNm / speed) * MILLIS_PER_HOUR).toLong().coerceAtLeast(0L)
        }
        return NavigationSolution(
            routeId = route.id,
            routeRevision = route.revision,
            activeLegIndex = session.activeLegIndex,
            previousWaypointId = previous.id,
            nextWaypointId = next.id,
            inputStatus = fix.positionStatus,
            distanceToWaypointNauticalMiles = distanceNm,
            bearingToWaypointTrueDegrees = toNext.initialBearing,
            crossTrackErrorNauticalMiles = projection.signedCrossTrackMeters / METERS_PER_NAUTICAL_MILE,
            estimatedTimeToWaypointMillis = duration,
            legProgress = if (leg.distanceMeters == 0.0) 1.0 else (projection.alongMeters / leg.distanceMeters).coerceIn(0.0, 1.0),
            routeProgress = if (totalDistance == 0.0) 1.0 else ((completeBefore + projection.alongMeters) / totalDistance).coerceIn(0.0, 1.0),
            positionSourceId = requireNotNull(fix.positionSourceId),
            motionSourceId = fix.motionSourceId,
            receivedAtMonotonicMillis = requireNotNull(fix.receivedAtMonotonicMillis),
        )
    }

    private fun projectOnLeg(
        from: NavigationPosition,
        to: NavigationPosition,
        current: NavigationPosition,
        legDistance: Double,
        legBearing: Double?,
    ): Projection {
        if (legDistance == 0.0 || legBearing == null) return Projection(0.0, 0.0)
        var low = 0.0
        var high = legDistance
        repeat(PROJECTION_ITERATIONS) {
            val left = low + (high - low) / 3.0
            val right = high - (high - low) / 3.0
            if (distanceAt(from, current, legBearing, left) <= distanceAt(from, current, legBearing, right)) {
                high = right
            } else {
                low = left
            }
        }
        val along = ((low + high) / 2.0).coerceIn(0.0, legDistance)
        val nearest = Geodesic.WGS84.Direct(from.latitude, from.longitude, legBearing, along)
        val crossDistance = Geodesic.WGS84.Inverse(nearest.lat2, nearest.lon2, current.latitude, current.longitude).s12
        val fromCurrent = inverse(from, current)
        val deltaRadians = normalizeSignedDegrees((fromCurrent.initialBearing ?: legBearing) - legBearing) * PI / 180.0
        val sign = when {
            sin(deltaRadians) > 0.0 -> 1.0
            sin(deltaRadians) < 0.0 -> -1.0
            else -> 0.0
        }
        return Projection(along, crossDistance * sign)
    }

    private fun distanceAt(from: NavigationPosition, current: NavigationPosition, bearing: Double, meters: Double): Double {
        val point = Geodesic.WGS84.Direct(from.latitude, from.longitude, bearing, meters)
        return Geodesic.WGS84.Inverse(point.lat2, point.lon2, current.latitude, current.longitude).s12
    }

    private fun inverse(from: NavigationPosition, to: NavigationPosition): Inverse {
        if (from == to) return Inverse(0.0, null)
        val result = Geodesic.WGS84.Inverse(from.latitude, from.longitude, to.latitude, to.longitude)
        return Inverse(result.s12, normalizeBearing(result.azi1))
    }

    private fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    private fun normalizeSignedDegrees(value: Double): Double = ((value + 540.0) % 360.0) - 180.0

    private data class Inverse(val distanceMeters: Double, val initialBearing: Double?)
    private data class Projection(val alongMeters: Double, val signedCrossTrackMeters: Double)

    private const val PROJECTION_ITERATIONS = 36
    private const val METERS_PER_NAUTICAL_MILE = 1852.0
    private const val MILLIS_PER_HOUR = 3_600_000.0
}

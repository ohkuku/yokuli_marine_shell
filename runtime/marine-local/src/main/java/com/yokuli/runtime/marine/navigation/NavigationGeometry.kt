package com.yokuli.runtime.marine.navigation

import com.yokuli.runtime.contract.navigation.*
import net.sf.geographiclib.Geodesic
import kotlin.math.*

/** WGS84测地线距离/方位；不作可航行性结论。最接近航段的位置用同一测地线求解。 */
object NavigationGeometry {
    fun distance(a: NavigationPoint, b: NavigationPoint): Double = Geodesic.WGS84.Inverse(a.lat, a.lon, b.lat, b.lon).s12
    fun bearing(a: NavigationPoint, b: NavigationPoint): Double = (Geodesic.WGS84.Inverse(a.lat, a.lon, b.lat, b.lon).azi1 + 360) % 360
    private var cachedRoute: NavigationRouteSnapshot? = null
    private var remainingLegs = doubleArrayOf()
    @Synchronized private fun remaining(route: NavigationRouteSnapshot?, index: Int): Double {
        if (route == null) return 0.0
        if (cachedRoute !== route) {
            cachedRoute = route
            remainingLegs = DoubleArray(route.waypoints.size)
            for (i in route.waypoints.lastIndex - 1 downTo 0) remainingLegs[i] = remainingLegs[i + 1] + distance(route.waypoints[i].point, route.waypoints[i + 1].point)
        }
        return remainingLegs.getOrElse(index) { 0.0 }
    }
    private fun signedAngle(value: Double) = ((value + 540) % 360) - 180

    data class Segment(val along: Double, val length: Double, val signedOffset: Double, val relation: NavigationSegmentRelation)
    fun segment(start: NavigationPoint, end: NavigationPoint, at: NavigationPoint): Segment? {
        val leg = Geodesic.WGS84.Inverse(start.lat, start.lon, end.lat, end.lon)
        if (leg.s12 < 1.0 || leg.s12 > 10_000_000.0) return null
        val own = Geodesic.WGS84.Inverse(start.lat, start.lon, at.lat, at.lon)
        if (own.s12 > 10_000_000.0) return null // 接近对跖点时不声称唯一航段投影。
        val angular = own.s12 / 6_371_008.8
        val angle = Math.toRadians(signedAngle(own.azi1 - leg.azi1))
        val estimate = atan2(sin(angular) * cos(angle), cos(angular)) * 6_371_008.8
        val line = Geodesic.WGS84.InverseLine(start.lat, start.lon, end.lat, end.lon)
        val range = max(1_000.0, own.s12 * .05)
        var left = estimate - range
        var right = estimate + range
        fun separation(along: Double): Double {
            val foot = line.Position(along)
            return Geodesic.WGS84.Inverse(foot.lat2, foot.lon2, at.lat, at.lon).s12
        }
        repeat(40) {
            val first = left + (right - left) / 3
            val second = right - (right - left) / 3
            if (separation(first) < separation(second)) right = second else left = first
        }
        val along = (left + right) / 2
        val foot = line.Position(along)
        val toBoat = Geodesic.WGS84.Inverse(foot.lat2, foot.lon2, at.lat, at.lon)
        val sign = if (sin(Math.toRadians(signedAngle(toBoat.azi1 - foot.azi2))) >= 0) 1 else -1
        return Segment(along, leg.s12, toBoat.s12 * sign, when {
            along < -.5 -> NavigationSegmentRelation.BEFORE
            along > leg.s12 + .5 -> NavigationSegmentRelation.AFTER
            else -> NavigationSegmentRelation.ON
        })
    }

    fun guidance(session: NavigationSession, fix: NavigationFixSnapshot?, nowElapsed: Long, nowUtc: Long): NavigationGuidance {
        val target = session.target
        val geometryIndex=(session.geometryIndex?:session.targetIndex).coerceIn(0,session.targetIndex)
        val steering=session.route?.waypoints?.getOrNull(geometryIndex)?.point ?: target?.point
        val base = NavigationGuidance(session.id, session.revision, target?.id, target?.name, target?.point,
            positionObservedUtcMillis = fix?.observedUtcMillis, positionElapsedMillis = fix?.elapsedMillis,
            positionAccuracyMeters = fix?.accuracyMeters, positionSource = fix?.sourceName,geometryIndex=geometryIndex,steeringPosition=steering)
        if (!session.ongoing || session.phase == NavigationPhase.PAUSED) return base.copy(issue = "NAVIGATION_PAUSED")
        if (session.phase == NavigationPhase.RECOVERY_REQUIRED) return base.copy(issue = "RESUME_REQUIRED")
        if (target == null) return base.copy(issue = "TARGET_MISSING")
        val live = fix?.takeIf { it.positionAccepted && it.point.valid && nowElapsed - it.elapsedMillis in 0..10_000 }
            ?: return base.copy(issue = "POSITION_NOT_CURRENT")
        val range = distance(live.point, target.point)
        val direction = if (range >= max(.5, (live.accuracyMeters ?: 0.0) * .25)) bearing(live.point, target.point) else null
        val aim=steering?:target.point
        val steeringRange=distance(live.point,aim)
        val steerBearing=if(steeringRange>=.5)bearing(live.point,aim)else direction
        val origin = if (geometryIndex == (session.approachGeometryIndex?:session.approachTargetIndex)) session.approachOrigin else session.route?.waypoints?.getOrNull(geometryIndex - 1)?.point
        val leg = origin?.let { segment(it, aim, live.point) }
        val remaining = steeringRange + remaining(session.route, geometryIndex)
        val toBusinessTarget=steeringRange+remaining(session.route,geometryIndex)-remaining(session.route,session.targetIndex)
        val speed = live.speedMetersPerSecond?.takeIf { it.isFinite() && it >= .25 && live.speedElapsedMillis?.let { time -> nowElapsed - time in 0..5_000 } == true }
        val cog = live.courseTrueDegrees?.takeIf { it.isFinite() && live.courseElapsedMillis?.let { time -> nowElapsed - time in 0..5_000 } == true }
        val progress = if (speed != null && cog != null && steerBearing != null) speed * cos(Math.toRadians(signedAngle(cog - steerBearing))) else null
        val basis = session.settings.etaBasis
        val etaSpeed = when (basis) {
            NavigationEtaBasis.PLAN_SPEED -> session.settings.plannedSpeedMetersPerSecond?.takeIf { it.isFinite() && it >= .25 }
            NavigationEtaBasis.CURRENT_PROGRESS -> progress?.takeIf { it >= .25 }
            NavigationEtaBasis.NONE -> null
        }
        fun eta(meters: Double): Long? = etaSpeed?.let { meters / it }?.takeIf { it.isFinite() && it in 0.0..2_592_000.0 }?.let { nowUtc - (nowElapsed - live.elapsedMillis) + (it * 1000).roundToLong() }
        return base.copy(distanceMeters = range, remainingMeters = remaining, bearingTrueDegrees = direction,
            crossTrackMeters = leg?.signedOffset?.takeIf { leg.relation == NavigationSegmentRelation.ON },
            lineOffsetMeters = leg?.signedOffset, alongTrackMeters = leg?.along, segmentLengthMeters = leg?.length,
            segmentRelation = leg?.relation ?: NavigationSegmentRelation.DIRECT,
            nearTarget = geometryIndex==session.targetIndex && range <= session.settings.arrivalRadiusMeters && (live.accuracyMeters?.let { it <= session.settings.arrivalRadiusMeters } != false),
            etaTargetUtcMillis = eta(toBusinessTarget), etaRouteUtcMillis = eta(remaining), etaBasis = if (etaSpeed == null) NavigationEtaBasis.NONE else basis,
            progressMetersPerSecond = progress, live = true,steeringBearingTrueDegrees=steerBearing,distanceAlongRouteToTargetMeters=toBusinessTarget,
            bearingElapsedMillis=live.elapsedMillis,distanceElapsedMillis=live.elapsedMillis,crossTrackElapsedMillis=live.elapsedMillis,
            progressElapsedMillis=listOfNotNull(live.elapsedMillis,live.speedElapsedMillis,live.courseElapsedMillis).maxOrNull(),
            issue = if (origin != null && leg == null) "SEGMENT_PROJECTION_UNAVAILABLE" else null)
    }
}

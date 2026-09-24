package com.yokuli.runtime.contract.ais

import kotlin.math.*

/** WGS84 经纬度先以 Double 投影到局部米制坐标，渲染器才能再转 Float。 */
object AisGeometry {
    private const val EARTH_RADIUS = 6_371_008.8
    fun valid(point: AisPoint): Boolean = point.latitude.isFinite() && point.longitude.isFinite() && point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
    fun distanceMeters(a: AisPoint, b: AisPoint): Double {
        val la = Math.toRadians(a.latitude); val lb = Math.toRadians(b.latitude)
        val dLat = lb - la; val dLon = Math.toRadians(wrapped(b.longitude - a.longitude))
        val h = sin(dLat / 2).pow(2) + cos(la) * cos(lb) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun bearingDegrees(a: AisPoint, b: AisPoint): Double {
        val la = Math.toRadians(a.latitude); val lb = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(wrapped(b.longitude - a.longitude))
        return normalized(Math.toDegrees(atan2(sin(dLon) * cos(lb), cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dLon))))
    }
    fun localMeters(origin: AisPoint, point: AisPoint): Pair<Double, Double> {
        val distance = distanceMeters(origin, point)
        val bearing = Math.toRadians(bearingDegrees(origin, point))
        return distance * sin(bearing) to distance * cos(bearing)
    }
    fun offset(origin: AisPoint, eastMeters: Double, northMeters: Double): AisPoint {
        val distance = hypot(eastMeters, northMeters) / EARTH_RADIUS
        if (distance == 0.0) return origin
        val bearing = atan2(eastMeters, northMeters)
        val lat = Math.toRadians(origin.latitude); val lon = Math.toRadians(origin.longitude)
        val lat2 = asin((sin(lat) * cos(distance) + cos(lat) * sin(distance) * cos(bearing)).coerceIn(-1.0, 1.0))
        val lon2 = lon + atan2(sin(bearing) * sin(distance) * cos(lat), cos(distance) - sin(lat) * sin(lat2))
        return AisPoint(Math.toDegrees(lat2), wrapped(Math.toDegrees(lon2)))
    }
    fun normalized(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
    fun wrapped(degrees: Double): Double = ((degrees + 540.0) % 360.0 + 360.0) % 360.0 - 180.0
}

/** 老化策略独立于本船仪表的短租期；CPA 另用更短的运动可信期限。 */
object AisAgePolicy {
    fun expectedMillis(kind: AisEntityKind, report: AisDynamicReport): Long {
        val knots = (report.sogMetersPerSecond ?: Double.NaN) * 3600.0 / 1852.0
        return when {
            kind in setOf(AisEntityKind.AID_TO_NAVIGATION, AisEntityKind.VIRTUAL_AID) -> 180_000
            kind in setOf(AisEntityKind.SART, AisEntityKind.MOB, AisEntityKind.EPIRB) -> 60_000
            report.lowResolution -> 180_000
            kind == AisEntityKind.BASE_STATION || kind == AisEntityKind.SAR_AIRCRAFT -> 10_000
            kind == AisEntityKind.CLASS_B && knots.isFinite() && knots < 2 -> 180_000
            kind == AisEntityKind.CLASS_B -> 30_000
            report.navigationStatus in setOf(1, 5) && (!knots.isFinite() || knots <= 3) -> 180_000
            knots > 23 -> 2_000
            knots > 14 -> 6_000
            else -> 10_000
        }
    }
    fun currentMillis(kind: AisEntityKind, report: AisDynamicReport): Long = max(30_000, expectedMillis(kind, report) * 2)
    fun lostMillis(kind: AisEntityKind, report: AisDynamicReport): Long = max(180_000, expectedMillis(kind, report) * 4)
    fun motionMillis(report: AisDynamicReport): Long = if (report.rateOfTurnDegreesPerMinute?.let { abs(it) > 5 } == true || "rot_direction_only" in report.invalidFields) 15_000 else 60_000
}

/** 恒速对地模型；Heading 从不作为速度方向。所有返回值有限且带不可计算原因。 */
object AisRelativeMotion {
    fun calculate(target: AisTarget, ownship: AisOwnship?, now: Long): AisRelativeMetrics {
        val own = ownship
        val ownPosition = own?.position
        if (own == null || ownPosition == null || !own.positionValid || !AisGeometry.valid(ownPosition) || own.positionElapsed == null || now - own.positionElapsed !in 0..30_000) return AisRelativeMetrics(AisCpaState.OWN_POSITION_MISSING, reason = "own_position_not_current")
        val report = target.dynamic
        val point = report?.position
        if (point == null || !AisGeometry.valid(point)) return AisRelativeMetrics(AisCpaState.TARGET_POSITION_MISSING, reason = "target_has_no_position")
        val heading = own.headingDegrees?.takeIf { it.isFinite() && it in 0.0..<360.0 && own.headingElapsed?.let { time -> now - time in 0..10_000 } == true }
        val bearing = AisGeometry.bearingDegrees(ownPosition, point)
        val distance = AisGeometry.distanceMeters(ownPosition, point)
        val base = AisRelativeMetrics(AisCpaState.MOTION_MISSING, distance, bearing, heading?.let { AisGeometry.wrapped(bearing - it) }, referenceElapsed = report.receivedElapsed, reason = "motion_not_provided")
        if (target.cached || target.state == AisTargetState.LOST || target.state == AisTargetState.AGING) return base.copy(state = AisCpaState.STALE, reason = "last_observed_position")
        if (target.state == AisTargetState.CONFLICT) return base.copy(state = AisCpaState.CONFLICT, reason = "contradictory_reports")
        if (target.positionInvalidated || target.state == AisTargetState.INVALID) return base.copy(state = AisCpaState.TARGET_POSITION_MISSING, reason = "latest_position_invalid")
        if (target.kind in setOf(AisEntityKind.AID_TO_NAVIGATION, AisEntityKind.VIRTUAL_AID, AisEntityKind.BASE_STATION, AisEntityKind.SAR_AIRCRAFT, AisEntityKind.SART, AisEntityKind.MOB, AisEntityKind.EPIRB, AisEntityKind.UNKNOWN)) return base.copy(state = AisCpaState.NOT_SURFACE_VESSEL, reason = "not_surface_vessel")
        if (report.lowResolution) return base.copy(state = AisCpaState.LOW_RESOLUTION, reason = "long_range_precision")
        val age = now - report.receivedElapsed
        if (age !in 0..AisAgePolicy.motionMillis(report)) return base.copy(state = AisCpaState.STALE, reason = "motion_report_too_old")
        if (report.invalidFields.any { it in setOf("manual_position", "position_system_inoperative", "sog_lower_bound") }) return base.copy(state = AisCpaState.MOTION_MISSING, reason = "broadcast_motion_not_precise")
        val ownSpeed = own.sogMetersPerSecond
        val targetSpeed = report.sogMetersPerSecond
        if (ownSpeed == null || targetSpeed == null || !ownSpeed.isFinite() || ownSpeed < 0 || targetSpeed < 0 || !targetSpeed.isFinite() || own.sogElapsed?.let { now - it in 0..15_000 } != true) return base
        if ((own.anchored && ownSpeed < 0.5) || (ownSpeed > 0 && ownSpeed < 0.25) || (targetSpeed > 0 && targetSpeed < 0.25)) return base.copy(state = AisCpaState.LOW_SPEED_COURSE, reason = "low_speed_course_unstable")
        fun velocity(speed: Double, course: Double?): Pair<Double, Double>? {
            if (speed == 0.0) return 0.0 to 0.0
            if (course == null || !course.isFinite() || course !in 0.0..<360.0) return null
            val r = Math.toRadians(course)
            return speed * sin(r) to speed * cos(r)
        }
        val ov = velocity(ownSpeed, own.cogDegrees) ?: return base.copy(reason = "own_course_missing")
        val tv = velocity(targetSpeed, report.cogDegrees) ?: return base.copy(reason = "target_course_missing")
        if (ownSpeed > 0 && (own.cogElapsed?.let { now - it in 0..15_000 } != true || abs((own.cogElapsed ?: 0) - (own.sogElapsed ?: 0)) > 5_000)) return base.copy(reason = "own_motion_not_coherent")
        if (distance > 200_000 || abs(ownPosition.latitude) > 85) return base.copy(reason = "outside_local_prediction_domain")
        // 以当前单调时刻对齐两条观测；这只是受限推算，不能写回目标位置。
        val ownAge = now - own.positionElapsed
        val ownAligned = AisGeometry.offset(ownPosition, ov.first * ownAge / 1000.0, ov.second * ownAge / 1000.0)
        val targetAligned = AisGeometry.offset(point, tv.first * age / 1000.0, tv.second * age / 1000.0)
        val r = AisGeometry.localMeters(ownAligned, targetAligned)
        val vx = tv.first - ov.first; val vy = tv.second - ov.second
        val v2 = vx * vx + vy * vy
        val alignedBearing = AisGeometry.bearingDegrees(ownAligned, targetAligned)
        val aligned = base.copy(distanceMeters = hypot(r.first, r.second), bearingDegrees = alignedBearing, relativeBearingDegrees = heading?.let { AisGeometry.wrapped(alignedBearing - it) }, referenceElapsed = now, extrapolationMillis = max(ownAge, age))
        if (v2 < 0.01) return aligned.copy(state = AisCpaState.PARALLEL, reason = "relative_speed_near_zero")
        val t = -(r.first * vx + r.second * vy) / v2
        val d = hypot(r.first + vx * t, r.second + vy * t)
        if (!t.isFinite() || !d.isFinite()) return aligned.copy(reason = "non_finite_motion")
        val prediction = t.takeIf { it in 0.0..21_600.0 }
        return aligned.copy(
            state = if (t < 0) AisCpaState.PAST else if (age > 1_000 || ownAge > 1_000 || "estimated_position" in report.invalidFields || "rot_direction_only" in report.invalidFields || report.rateOfTurnDegreesPerMinute?.let { abs(it) > 5 } == true || report.utcSecond == null || report.utcSecond!! >= 60) AisCpaState.ESTIMATED else AisCpaState.CALCULATED,
            cpaMeters = d, tcpaSeconds = t,
            ownAtCpa = prediction?.let { AisGeometry.offset(ownAligned, ov.first * it, ov.second * it) },
            targetAtCpa = prediction?.let { AisGeometry.offset(targetAligned, tv.first * it, tv.second * it) },
            reason = if (t < 0) "closest_approach_in_past" else "constant_ground_velocity",
        )
    }
}

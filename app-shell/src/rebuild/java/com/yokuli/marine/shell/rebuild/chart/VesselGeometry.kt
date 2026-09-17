package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint

/** 一分钟对地航程。旧船位、缺失航迹向、停船抖动不产生误导性的方向线。 */
internal fun vesselCourseVector(vessel: MapVessel): List<GeoPoint>? {
    val speed=vessel.speedKnots?.takeIf {it.isFinite() && it>=0.5} ?: return null
    val course=vessel.courseDegrees?.takeIf {it.isFinite()} ?: return null
    if(!vessel.fresh || !vessel.point.valid()) return null
    return listOf(vessel.point,destination(vessel.point,speed*1852.0/60.0,course))
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.domain.sonar.DepthReference
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.shell.rebuild.OsStore
import kotlin.math.*

/**
 * 中文：仪表以同一份运行时观测解释物理关系，图形不能自行产生航向、流或导航指令。
 * 每个可操作读数都回到宿主的指标详情，避免仪表自己建立另一套跨应用导航。
 */
@Composable internal fun NavigationInstrumentPanel(
    os: OsStore, data: VesselDataSnapshot, now: Long, onMetric: (InstrumentTileId) -> Unit,
) {
    val c = LocalMetro.current
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        SpeedComparison(os, data, now, onMetric)
        NavigationDirections(os, data, now, onMetric)
        val difference = data.derived.headingCogDifferenceDegrees.liveNumber()
        if (difference != null) Label(
            os.t("船首与航迹相差 ", "heading / course separation ") + os.formatAngle(abs(difference)) +
                os.t(" · 角度差不等于水流", " · angle difference does not establish current"), 13, c.muted,
        )
        val targetPresent = data.waypointBearingTrueDegrees.displayNumber() != null ||
            data.waypointDistanceNauticalMiles.displayNumber() != null || data.crossTrackErrorNauticalMiles.displayNumber() != null
        if (targetPresent) {
            Label(data.destinationWaypoint.value?.takeIf { it.isNotBlank() } ?: os.t("当前目标", "current destination"), 22)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpatialMetric(os, os.t("目标方位 · 真北", "destination · true"), os.formatBearing(data.waypointBearingTrueDegrees.liveNumber()),
                    data.waypointBearingTrueDegrees, now, Modifier.weight(1f), onClick = { onMetric(InstrumentTileId.WAYPOINT_BEARING) })
                SpatialMetric(os, os.t("剩余距离", "distance to destination"), os.formatDistance(data.waypointDistanceNauticalMiles.displayNumber()?.times(1852)),
                    data.waypointDistanceNauticalMiles, now, Modifier.weight(1f), onClick = { onMetric(InstrumentTileId.WAYPOINT_DISTANCE) })
            }
            CrossTrackDeviation(os, data, now, onMetric)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SpatialMetric(os, os.t("测量水深", "measured depth"), os.formatDepth(data.depthMeters.displayNumber()), data.depthMeters, now,
                Modifier.weight(1f), onClick = { onMetric(InstrumentTileId.DEPTH) })
            SpatialMetric(os, os.t("龙骨下余量", "under-keel clearance"), os.formatDepth(data.derived.underKeelClearanceMeters.displayNumber()),
                data.derived.underKeelClearanceMeters, now, Modifier.weight(1f), onClick = { onMetric(InstrumentTileId.UKC) })
        }
    }
}

@Composable private fun NavigationDirections(os: OsStore, data: VesselDataSnapshot, now: Long, onMetric: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    val heading = data.headingTrueDegrees.liveNumber()
    val course = data.cogTrueDegrees.liveNumber()
    val bearing = data.waypointBearingTrueDegrees.liveNumber()
    val headingAngle = spatialBearing(data.headingTrueDegrees)
    val courseAngle = spatialBearing(data.cogTrueDegrees)
    val targetAngle = spatialBearing(data.waypointBearingTrueDegrees)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(os.t("船朝哪，往哪走", "heading & movement"), 23)
            Label(os.t("真北朝上", "true north up"), 13, c.muted)
        }
        Box(Modifier.fillMaxWidth().height(226.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = os.t("真北方向图。船体表示船首向，实线箭头表示对地航迹，虚线表示当前目标方位。", "True-north diagram. The hull shows heading, the solid arrow shows course over ground, and the dashed ray shows destination bearing.")
            }) {
                val r = min(size.width, size.height) * .42f
                drawCircle(c.muted.copy(alpha = .22f), r, center, style = Stroke(1.dp.toPx()))
                for (degree in 0 until 360 step 15) drawLine(c.muted.copy(alpha = .6f),
                    spatialRadial(center, r, degree.toFloat()), spatialRadial(center, r * if (degree % 90 == 0) .88f else .96f, degree.toFloat()), 1.dp.toPx())
                if (bearing != null) {
                    val end = spatialRadial(center, r * .94f, targetAngle)
                    drawLine(c.muted, center, end, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                    drawCircle(c.fg, 4.dp.toPx(), end, style = Stroke(1.5.dp.toPx()))
                }
                if (course != null) spatialArrow(center, spatialRadial(center, r * .83f, courseAngle), c.fg, 2.dp.toPx())
                if (heading != null) rotate(headingAngle, center) {
                    drawPath(spatialHull(center, r * .27f, r * .49f), c.accent.copy(alpha = .18f))
                    drawPath(spatialHull(center, r * .27f, r * .49f), c.accent, style = Stroke(2.dp.toPx()))
                    drawLine(c.accent, Offset(center.x, center.y - r * .5f), Offset(center.x, center.y - r * .72f), 2.dp.toPx())
                } else drawCircle(c.muted.copy(alpha = .5f), 5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            }
            Label(os.t("北", "N"), 13, c.muted, Modifier.align(Alignment.TopCenter))
            Label(os.t("南", "S"), 13, c.muted, Modifier.align(Alignment.BottomCenter))
        }
        if (heading == null) Label(os.t("船首向未更新 · 保留可用航迹，不用航迹代替船头", "Heading is not current · available course stays separate from the bow"), 13, c.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SpatialMetric(os, os.t("船头 · 船首向", "hull · heading"), os.formatBearing(heading), data.headingTrueDegrees, now,
                Modifier.weight(1f), activeColor = c.accent, onClick = { onMetric(InstrumentTileId.HEADING) })
            SpatialMetric(os, os.t("箭头 · 对地航迹", "arrow · course over ground"), os.formatBearing(course), data.cogTrueDegrees, now,
                Modifier.weight(1f), activeColor = c.fg, onClick = { onMetric(InstrumentTileId.COG) })
        }
    }
}

/** 中文：两条速度轴只做比较，不把 SOG-STW 当作测得的流速。 */
@Composable private fun SpeedComparison(os: OsStore, data: VesselDataSnapshot, now: Long, onMetric: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    val sog = data.sogKnots.displayNumber()
    val stw = data.speedThroughWaterKnots.displayNumber()
    val extent = spatialScale(max(abs(os.speedValue(sog ?: 0.0)), abs(os.speedValue(stw ?: 0.0))).coerceAtLeast(1.0))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(os.t("航速", "speed"), 23)
        SpatialSpeedRow(os, os.t("对地", "over ground"), data.sogKnots, now, extent, c.accent) { onMetric(InstrumentTileId.SOG) }
        SpatialSpeedRow(os, os.t("对水", "through water"), data.speedThroughWaterKnots, now, extent, c.fg) { onMetric(InstrumentTileId.BOAT_SPEED) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("0", 12, c.muted)
            Label(gaugeScaleNumber(extent, extent) + " " + os.speedUnitLabel, 12, c.muted)
        }
    }
}

@Composable private fun SpatialSpeedRow(os: OsStore, name: String, observation: VesselObservation<Double>, now: Long, extent: Double, color: Color, onClick: () -> Unit) {
    val c = LocalMetro.current
    val number = observation.displayNumber()
    val readingColor = if (observation.displayIsLive()) color else c.muted
    val ratio by animateFloatAsState((number?.let { abs(os.speedValue(it)) / extent } ?: 0.0).toFloat().coerceIn(0f, 1f), tween(260), label = "speed comparison")
    Column(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Label(name, 16, c.muted, Modifier.weight(1f))
            Label(os.formatSpeed(number), 31, readingColor)
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(c.muted.copy(alpha = .22f), Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx())
            if (number != null) drawLine(readingColor, Offset(0f, center.y), Offset(size.width * ratio, center.y), 4.dp.toPx())
        }
        Label(spatialObservationStatus(os, observation, now), 12, c.muted)
    }
}

/** 中文：XTE 的符号来自运行时，仅画正负偏差轴；不冒充转舵方向或船在航线哪一侧。 */
@Composable private fun CrossTrackDeviation(os: OsStore, data: VesselDataSnapshot, now: Long, onMetric: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    val observation = data.crossTrackErrorNauticalMiles
    val value = observation.displayNumber() ?: return
    val live = observation.liveNumber()
    val displayed = os.displayMetricValue("xte", value)
    val extent = spatialScale(abs(displayed).coerceAtLeast(.01))
    val fraction by animateFloatAsState(((live?.let { os.displayMetricValue("xte", it) } ?: 0.0) / extent).toFloat().coerceIn(-1f, 1f), tween(280), label = "cross track")
    Column(Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(role = Role.Button) { onMetric(InstrumentTileId.CROSS_TRACK_ERROR) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(os.t("航线偏差", "cross-track deviation"), 16, c.muted)
            Label(os.formatMetric("xte", value), 24, if (live != null) c.accent else c.muted)
        }
        Canvas(Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = os.t("以零为中心的有符号偏差，不是转舵指令", "Signed deviation centred on zero, not a steering command") }) {
            val left = 8.dp.toPx(); val right = size.width - left
            drawLine(c.muted.copy(alpha = .25f), Offset(left, center.y), Offset(right, center.y), 1.dp.toPx())
            drawLine(c.fg, Offset(center.x, 2.dp.toPx()), Offset(center.x, size.height - 2.dp.toPx()), 1.dp.toPx())
            if (live != null) {
                val x = center.x + fraction * (right - left) / 2f
                drawLine(c.accent, center, Offset(x, center.y), 3.dp.toPx())
                drawCircle(c.accent, 5.dp.toPx(), Offset(x, center.y))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("−" + gaugeScaleNumber(extent, extent * 2) + " " + os.distanceUnitLabel, 12, c.muted)
            Label("0", 12, c.muted)
            Label("+" + gaugeScaleNumber(extent, extent * 2) + " " + os.distanceUnitLabel, 12, c.muted)
        }
        Label(spatialObservationStatus(os, observation, now), 12, c.muted)
    }
}

/** 中文：相对风角即使没有船首向仍有意义；不推导极线、受力或未经观测的横倾。 */
@Composable internal fun SailingInstrumentPanel(
    os: OsStore, data: VesselDataSnapshot, now: Long, onMetric: (InstrumentTileId) -> Unit,
) {
    val c = LocalMetro.current
    val apparent = data.apparentWind.angleDegrees.liveNumber()
    val trueAngle = data.trueWind.angleDegrees.liveNumber()
    val apparentAngle = spatialBearing(data.apparentWind.angleDegrees)
    val trueAngleAnimated = spatialBearing(data.trueWind.angleDegrees)
    val apparentWind = data.apparentWind.speedKnots.liveNumber()
    val trueWind = data.trueWind.speedKnots.liveNumber()
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(os.t("风从哪来", "where the wind comes from"), 23, modifier = Modifier.weight(1f))
            Label(os.t("船艏朝上", "bow up"), 13, c.muted)
        }
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = os.t("船艏朝上的相对风角。实线为视风，虚线为真风；箭头指向船，表示风的来向。", "Bow-up relative wind angles. Solid indicates apparent wind, dashed indicates true wind. Arrows point toward the boat to show wind arriving.")
            }) {
                val r = min(size.width, size.height) * .42f
                drawCircle(c.muted.copy(alpha = .25f), r, center, style = Stroke(1.dp.toPx()))
                for (degrees in 0 until 360 step 15) drawLine(c.muted.copy(alpha = .55f), spatialRadial(center, r, degrees.toFloat()),
                    spatialRadial(center, r * if (degrees % 45 == 0) .9f else .96f, degrees.toFloat()), 1.dp.toPx())
                drawLine(c.muted.copy(alpha = .3f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())
                drawPath(spatialHull(center, r * .23f, r * .4f), c.fg.copy(alpha = .08f))
                drawPath(spatialHull(center, r * .23f, r * .4f), c.fg, style = Stroke(1.5.dp.toPx()))
                if (trueAngle != null && trueWind != 0.0) spatialWindArrow(center, r, trueAngleAnimated, c.fg, dashed = true)
                if (apparent != null && apparentWind != 0.0) spatialWindArrow(center, r * .88f, apparentAngle, c.accent, dashed = false)
            }
            Label(os.t("船艏 · 0°", "bow · 0°"), 13, c.muted, Modifier.align(Alignment.TopCenter))
            Label(os.t("船艉 · 180°", "stern · 180°"), 13, c.muted, Modifier.align(Alignment.BottomCenter))
            Label(os.t("左舷", "port"), 13, c.muted, Modifier.align(Alignment.CenterStart))
            Label(os.t("右舷", "starboard"), 13, c.muted, Modifier.align(Alignment.CenterEnd))
        }
        Label(os.t("实线：视风  ·  虚线：真风  ·  箭头朝船吹来", "solid: apparent · dashed: true · arrows blow toward the boat"), 13, c.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WindReadings(os, os.t("视风", "apparent wind"), data.apparentWind, now, c.accent, Modifier.weight(1f),
                { onMetric(InstrumentTileId.APPARENT_WIND_ANGLE) }, { onMetric(InstrumentTileId.APPARENT_WIND_SPEED) })
            WindReadings(os, os.t("真风", "true wind"), data.trueWind, now, c.fg, Modifier.weight(1f),
                { onMetric(InstrumentTileId.TRUE_WIND_ANGLE) }, { onMetric(InstrumentTileId.TRUE_WIND_SPEED) })
        }
        val reference = when (data.trueWind.speedKnots.reference) {
            VesselReference.WaterReferenced -> os.t("真风基于对水运动", "true wind uses water-referenced movement")
            VesselReference.GroundReferenced -> os.t("真风基于对地运动", "true wind uses ground-referenced movement")
            else -> os.t("真风沿用数据源的观测基准", "true wind uses the source's observation reference")
        }
        if (data.trueWind.speedKnots.value != null) Label(reference, 13, c.muted)
        if (apparent == null && trueAngle == null) Label(os.t("等待实时相对风角；历史读数保留在下方。", "Waiting for current relative wind angles; previous readings remain below."), 14, c.muted)
        SpeedComparison(os, data, now, onMetric)
        WindProgress(os, data, now, onMetric)
        SpatialMetric(os, os.t("横倾", "heel"), os.formatAngle(data.heelDegrees.liveNumber()), data.heelDegrees, now,
            onClick = { onMetric(InstrumentTileId.HEEL) })
    }
}

@Composable private fun WindReadings(os: OsStore, title: String, wind: VesselWindObservation, now: Long, color: Color, modifier: Modifier, angle: () -> Unit, speed: () -> Unit) {
    val c = LocalMetro.current
    val angleValue = wind.angleDegrees.displayNumber()
    val side = when {
        angleValue == null -> os.t("尚无风角", "no wind angle yet")
        abs(angleValue) < .5 -> os.t("正前方", "ahead")
        abs(abs(angleValue) - 180) < .5 -> os.t("正后方", "astern")
        angleValue < 0 -> os.t("左舷 ", "port ") + os.formatAngle(abs(angleValue))
        else -> os.t("右舷 ", "starboard ") + os.formatAngle(abs(angleValue))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(title, 19, color)
        SpatialMetric(os, os.t("风速", "speed"), os.formatSpeed(wind.speedKnots.displayNumber()), wind.speedKnots, now, activeColor = color, onClick = speed)
        Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = angle), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Label(side, 19, if (wind.angleDegrees.displayIsLive()) color else c.muted)
            Label(spatialObservationStatus(os, wind.angleDegrees, now), 12, c.muted)
        }
        if (wind.speedKnots.liveNumber() == 0.0) Label(os.t("当前风速为零", "wind speed is zero"), 13, c.muted)
    }
}

/** 中文：VMG 是运行时对风方向的有符号速度分量，不是建议速度或目标航点速度。 */
@Composable private fun WindProgress(os: OsStore, data: VesselDataSnapshot, now: Long, onMetric: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    val observation = data.derived.vmgToWindKnots
    val value = observation.displayNumber()
    val displayed = value?.let(os::speedValue)
    val extent = spatialScale(max(abs(displayed ?: 0.0), abs(os.speedValue(data.speedThroughWaterKnots.displayNumber() ?: 0.0))).coerceAtLeast(1.0))
    val fraction by animateFloatAsState(((displayed ?: 0.0) / extent).toFloat().coerceIn(-1f, 1f), tween(260), label = "wind progress")
    Column(Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(role = Role.Button) { onMetric(InstrumentTileId.VMG) }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Label(os.t("对风有效速度 · VMG", "windward velocity · VMG"), 18, modifier = Modifier.weight(1f))
            Label(os.formatSpeed(value), 29, if (observation.displayIsLive()) c.accent else c.muted)
        }
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val left = 4.dp.toPx(); val right = size.width - left
            drawLine(c.muted.copy(alpha = .3f), Offset(left, center.y), Offset(right, center.y), 1.dp.toPx())
            drawLine(c.fg, Offset(center.x, 1.dp.toPx()), Offset(center.x, size.height - 1.dp.toPx()), 1.dp.toPx())
            if (displayed != null) {
                val end = center.x + fraction * (right - left) / 2f
                drawLine(if (observation.displayIsLive()) c.accent else c.muted, center, Offset(end, center.y), 4.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(os.t("顺风 −", "downwind −"), 13, c.muted)
            Label("0", 13, c.muted)
            Label(os.t("迎风 +", "upwind +"), 13, c.muted)
        }
        Label(spatialObservationStatus(os, observation, now), 12, c.muted)
        Label(os.t("沿用系统计算的对风速度分量，不是目标速度。", "The system's calculated wind-axis velocity component, not a target speed."), 13, c.muted)
    }
}

/**
 * 中文：水深仅画单点测量剖面。海底只是一条局部测点标记，不是前视地形。
 * 未知基准不画水面；换能器/龙骨基准不假设安装深度；UKC 只读取运行时派生值。
 */
@Composable internal fun DepthInstrumentSection(
    os: OsStore, data: VesselDataSnapshot, now: Long, onMetric: (InstrumentTileId) -> Unit,
) {
    val c = LocalMetro.current
    val marine = os.marine
    val state = marine?.services?.state?.collectAsState()?.value
    val draft = state?.vesselSettings?.draftMeters?.takeIf { it.isFinite() && it > 0.0 }
    val depthObservation = data.depthMeters
    val datum = (depthObservation.reference as? VesselReference.Depth)?.reference
    // 龙骨基准的负读数有明确物理意义；其余基准的负测深不作为有效水深呈现。
    val rawDepth = depthObservation.displayNumber()
    val invalidNegativeDepth = rawDepth != null && rawDepth < 0.0 && datum != DepthReference.BELOW_KEEL
    val depth = rawDepth?.takeUnless { invalidNegativeDepth }
    val knownDatum = datum != null && datum != DepthReference.UNKNOWN
    val ukcObservation = data.derived.underKeelClearanceMeters
    val ukc = ukcObservation.displayNumber()
    val datumName = when (datum) {
        DepthReference.BELOW_SURFACE -> os.t("水面以下", "below surface")
        DepthReference.BELOW_TRANSDUCER -> os.t("换能器以下", "below transducer")
        DepthReference.BELOW_KEEL -> os.t("龙骨以下", "below keel")
        else -> os.t("测量基准未知", "measurement reference unknown")
    }
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Label(os.t("船下有什么", "below the boat"), 24)
        Label(datumName, 16, c.muted)
        if (depth != null && knownDatum) {
            val sectionColor = if (depthObservation.displayIsLive()) c.accent else c.muted
            Box(Modifier.fillMaxWidth().height(205.dp)) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription = os.t("当前测点的概念剖面：", "Conceptual section at the current measurement: ") + datumName + " " + os.formatDepth(depth) +
                        os.t("。船体非等比例，只标当前测点，不代表前方海底。", ". The hull is schematic. This shows one sample, not the seabed ahead.")
                }) {
                    val plotTop = 28.dp.toPx(); val plotBottom = size.height - 18.dp.toPx()
                    val centerX = size.width * .34f
                    val measureX = size.width * .73f
                    // 同一条有符号深度轴包含零、测点和已知吃水；负龙骨测深向上，不截成零。
                    val minDepth = min(0.0, depth)
                    val maxDepth = max(depth, if (datum == DepthReference.BELOW_SURFACE) draft ?: 0.0 else 0.0)
                    val range = (maxDepth - minDepth).coerceAtLeast(.1)
                    fun yFor(meters: Double): Float = plotTop + ((meters - minDepth) / range).toFloat() * (plotBottom - plotTop)
                    val top = yFor(0.0)
                    val measuredBottom = yFor(depth)
                    if (datum == DepthReference.BELOW_SURFACE) {
                        drawLine(c.muted.copy(alpha = .65f), Offset(8.dp.toPx(), top), Offset(size.width - 8.dp.toPx(), top), 1.dp.toPx())
                        // 船体仅用于识别水面与龙骨；只有配置的吃水才定位龙骨标记。
                        val hull = Path().apply {
                            moveTo(centerX - 35.dp.toPx(), top - 8.dp.toPx())
                            lineTo(centerX + 36.dp.toPx(), top - 8.dp.toPx())
                            lineTo(centerX + 25.dp.toPx(), top + 9.dp.toPx())
                            lineTo(centerX - 22.dp.toPx(), top + 9.dp.toPx()); close()
                        }
                        drawPath(hull, c.fg.copy(alpha = if (depthObservation.displayIsLive()) .9f else .4f), style = Stroke(1.5.dp.toPx()))
                        if (draft != null) {
                            val keelY = yFor(draft)
                            drawLine(c.fg.copy(alpha = .5f), Offset(centerX, top), Offset(centerX, keelY), 3.dp.toPx())
                            drawLine(c.fg, Offset(centerX - 11.dp.toPx(), keelY), Offset(centerX + 11.dp.toPx(), keelY), 2.dp.toPx())
                            if (ukc != null) {
                                val ukcX = size.width * .48f
                                spatialDimension(ukcX, keelY, measuredBottom, if (ukcObservation.displayIsLive()) c.fg else c.muted)
                            }
                        }
                    } else {
                        // 换能器/龙骨相对基准没有可靠水面位置，因此只画参考点和测量距离。
                        drawLine(c.muted, Offset(centerX - 30.dp.toPx(), top), Offset(measureX + 7.dp.toPx(), top), 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                        if (datum == DepthReference.BELOW_TRANSDUCER) drawCircle(c.fg, 6.dp.toPx(), Offset(centerX, top), style = Stroke(2.dp.toPx()))
                        else drawLine(c.fg, Offset(centerX - 18.dp.toPx(), top), Offset(centerX + 18.dp.toPx(), top), 4.dp.toPx())
                    }
                    spatialDimension(measureX, top, measuredBottom, sectionColor)
                    // 单个测点使用短横标记，不能铺成伪造的海底地形。
                    drawLine(sectionColor, Offset(centerX - 19.dp.toPx(), measuredBottom), Offset(measureX + 17.dp.toPx(), measuredBottom), 3.dp.toPx())
                    drawLine(sectionColor.copy(alpha = .35f), Offset(centerX - 19.dp.toPx(), measuredBottom + 5.dp.toPx()), Offset(measureX + 17.dp.toPx(), measuredBottom + 5.dp.toPx()), 1.dp.toPx())
                }
                // 原生热点覆盖尺寸线，保持至少 48dp 的可操作范围；不在绘图器里维护导航。
                Box(Modifier.fillMaxHeight().widthIn(min = 48.dp).fillMaxWidth(.34f).align(Alignment.CenterEnd)
                    .clickable(role = Role.Button) { onMetric(InstrumentTileId.DEPTH) }
                    .semantics { contentDescription = os.t("查看测量水深与基准", "inspect measured depth and reference") })
                if (datum == DepthReference.BELOW_SURFACE && draft != null && ukc != null) {
                    Box(Modifier.fillMaxHeight().widthIn(min = 48.dp).fillMaxWidth(.25f).align(Alignment.Center)
                        .clickable(role = Role.Button) { onMetric(InstrumentTileId.UKC) }
                        .semantics { contentDescription = os.t("查看龙骨下余量", "inspect under-keel clearance") })
                }
            }
            Label(os.t("单点测量示意 · 船体非等比例 · 不是前方地形", "single-sample section · hull not to scale · not terrain ahead"), 12, c.muted)
            if (!depthObservation.displayIsLive()) Label(os.t("这是上次测量的剖面，未作为实时水深。", "This section shows the last measurement, not a current depth."), 13, c.muted)
            if (datum == DepthReference.BELOW_KEEL && depth < 0.0) Label(
                os.t("测点高于龙骨基准 · 负值已保留，请核对水深基准。", "The measured point is above the keel reference. The negative reading is retained; review its reference."), 14, c.accent,
            )
        } else {
            Label(when {
                invalidNegativeDepth -> os.t("测深不符合当前基准", "depth does not match its reference")
                depth == null -> os.t("还没有水深测量", "no depth measurement yet")
                else -> os.t("先确认测量从哪里开始", "first establish the measurement reference")
            }, 22, c.muted)
            Label(if (invalidNegativeDepth) os.t("仅龙骨基准允许负读数。当前读数未用于剖面，点读数查看来源。", "Only keel-referenced measurements allow negative readings. This value is excluded from the section; tap to inspect its source.")
                else os.t("基准未确认时，不绘制水面、海底或龙骨余量。点读数可查看来源详情。", "Without a confirmed reference, no surface, bottom or clearance is drawn. Tap a reading to inspect its source."), 14, c.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SpatialMetric(os, datumName, os.formatDepth(depth), depthObservation, now, Modifier.weight(1f), onClick = { onMetric(InstrumentTileId.DEPTH) })
            SpatialMetric(os, os.t("龙骨下余量", "under-keel clearance"), os.formatDepth(ukc), ukcObservation, now, Modifier.weight(1f), activeColor = c.fg, onClick = { onMetric(InstrumentTileId.UKC) })
        }
        Label(os.t("船舶吃水 · ", "vessel draft · ") + (draft?.let(os::formatDepth) ?: os.t("尚未设置", "not set")), 14, c.muted)
        if (ukc == null) Label(when {
            depth == null -> os.t("等待水深测量后判断是否能够计算余量。", "Clearance depends on an available depth measurement.")
            !knownDatum -> os.t("测量基准未确认，系统没有计算余量。", "The unknown measurement reference prevents a clearance calculation.")
            datum == DepthReference.BELOW_TRANSDUCER -> os.t("当前从换能器量起；未确认安装偏移，不能当作龙骨余量。", "This depth starts at the transducer. Without a confirmed installation offset, it is not under-keel clearance.")
            datum == DepthReference.BELOW_KEEL -> os.t("本读数已经从龙骨量起；未再次扣除吃水。", "The measured depth already starts below the keel. Draft is not subtracted again.")
            draft == null -> os.t("水面基准已知，还需要船舶吃水才能计算余量。", "The surface reference is known; vessel draft is still needed for clearance.")
            else -> os.t("系统尚未提供有效余量；保留测量水深，不在界面重复计算。", "The system has not provided usable clearance. The measured depth remains available.")
        }, 14, c.muted)
        if (ukc != null && ukc <= 0.0) Label(os.t("龙骨余量不为正，请核对实测水深、基准和吃水。", "Clearance is not positive. Review depth, measurement reference and vessel draft."), 15, c.accent)
        Label(os.t("单点正余量不代表前方航段安全。", "Positive clearance at one point does not establish safe water ahead."), 12, c.muted)
    }
}

@Composable private fun SpatialMetric(os: OsStore, title: String, value: String, observation: VesselObservation<*>, now: Long,
    modifier: Modifier = Modifier, activeColor: Color = LocalMetro.current.accent, onClick: () -> Unit,
) {
    val c = LocalMetro.current
    Column(modifier.heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(title, 14, c.muted)
        Label(value, 29, if (observation.displayIsLive()) activeColor else c.muted)
        Label(spatialObservationStatus(os, observation, now), 12, c.muted)
    }
}

private fun spatialObservationStatus(os: OsStore, observation: VesselObservation<*>, now: Long): String {
    val source = observation.sourceIdentity?.displayName ?: when (observation.source) {
        VesselDataSource.NONE -> null
        VesselDataSource.BOAT_NMEA -> "NMEA"
        VesselDataSource.PHONE_GNSS -> os.t("手机 GPS", "phone GPS")
        VesselDataSource.PHONE_IMU -> os.t("手机姿态", "phone motion")
        VesselDataSource.PHONE_MAGNETOMETER -> os.t("手机航向", "phone heading")
        VesselDataSource.PHONE_BAROMETER -> os.t("手机气压", "phone pressure")
        VesselDataSource.DERIVED -> os.t("系统计算", "system calculation")
        VesselDataSource.DEMO -> os.t("演示", "demo")
    }
    return observationStatus(os, observation, now) + source?.let { " · $it" }.orEmpty()
}

/** 中文：换源、重连代次、推导输入或手机校准改变，均开始新的方向段，不能跨段插值。 */
private data class SpatialBearingSource(
    val identity: VesselSourceIdentity?,
    val source: VesselDataSource,
    val sourceClass: VesselSourceClass,
    val reference: VesselReference?,
    val provenance: VesselProvenance?,
)

@Composable private fun spatialBearing(observation: VesselObservation<Double>): Float {
    val value = observation.liveNumber()
    val sourceKey = SpatialBearingSource(observation.sourceIdentity, observation.source,
        observation.sourceClass, observation.reference, observation.provenanceDetail)
    val angle = remember(sourceKey) { Animatable(value?.toFloat() ?: 0f) }
    var hasReading by remember(sourceKey) { mutableStateOf(value != null) }
    LaunchedEffect(sourceKey, value) {
        if (value == null) {
            // 数据中断后隐藏方向并结束连续段；恢复首帧必须定位，不跨缺口转动。
            hasReading = false
            angle.stop()
        } else if (!hasReading) {
            angle.snapTo(value.toFloat())
            hasReading = true
        } else {
            val difference = ((value.toFloat() - angle.value) % 360f + 540f) % 360f - 180f
            angle.animateTo(angle.value + difference, tween(300))
        }
    }
    return if (value != null && !hasReading) value.toFloat() else angle.value
}

private fun spatialScale(value: Double): Double {
    val positive = value.coerceAtLeast(.001)
    val power = 10.0.pow(floor(log10(positive)))
    val ratio = positive / power
    return power * when { ratio <= 1 -> 1; ratio <= 2 -> 2; ratio <= 5 -> 5; else -> 10 }
}
private fun spatialRadial(origin: Offset, radius: Float, angle: Float): Offset {
    val radians = Math.toRadians(angle.toDouble() - 90)
    return origin + Offset(cos(radians).toFloat() * radius, sin(radians).toFloat() * radius)
}
private fun spatialHull(origin: Offset, width: Float, height: Float) = Path().apply {
    moveTo(origin.x, origin.y - height)
    cubicTo(origin.x + width * 1.2f, origin.y - height * .2f, origin.x + width, origin.y + height * .6f, origin.x + width * .65f, origin.y + height)
    lineTo(origin.x - width * .65f, origin.y + height)
    cubicTo(origin.x - width, origin.y + height * .6f, origin.x - width * 1.2f, origin.y - height * .2f, origin.x, origin.y - height)
    close()
}
private fun DrawScope.spatialArrow(start: Offset, end: Offset, color: Color, width: Float, dashed: Boolean = false) {
    val delta = end - start
    val length = delta.getDistance().coerceAtLeast(1f)
    val direction = delta / length
    val perpendicular = Offset(-direction.y, direction.x)
    drawLine(color, start, end, width, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null)
    val arrow = 8.dp.toPx()
    drawLine(color, end, end - direction * arrow + perpendicular * arrow * .5f, width)
    drawLine(color, end, end - direction * arrow - perpendicular * arrow * .5f, width)
}
private fun DrawScope.spatialWindArrow(origin: Offset, radius: Float, angle: Float, color: Color, dashed: Boolean) {
    spatialArrow(spatialRadial(origin, radius * .94f, angle), spatialRadial(origin, radius * .43f, angle), color, 2.dp.toPx(), dashed)
}
private fun DrawScope.spatialDimension(x: Float, top: Float, bottom: Float, color: Color) {
    drawLine(color, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx())
    drawLine(color, Offset(x - 5.dp.toPx(), top), Offset(x + 5.dp.toPx(), top), 1.5.dp.toPx())
    drawLine(color, Offset(x - 5.dp.toPx(), bottom), Offset(x + 5.dp.toPx(), bottom), 1.5.dp.toPx())
}

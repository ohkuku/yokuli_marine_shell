package com.yokuli.marine.shell.rebuild.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.yokuli.marine.core.design.R
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.shell.rebuild.OsStore
import kotlin.math.*

/** 中文：仪表只绘制有效观测；缺测时保留刻度、隐藏指针，不将缺测伪装为零。 */
internal fun VesselObservation<Double>.liveNumber(): Double? = value?.takeIf { freshness == VesselDataFreshness.FRESH && it.isFinite() }

/** 中文：跨越正北时走最短角度，避免 359° 到 1° 反向转一整圈。 */
@Composable private fun animatedBearing(value: Double?): Float {
    val angle = remember { Animatable(value?.toFloat() ?: 0f) }
    LaunchedEffect(value) {
        value?.let {
            val delta = ((it.toFloat() - angle.value) % 360f + 540f) % 360f - 180f
            angle.animateTo(angle.value + delta, tween(450))
        }
    }
    return angle.value
}

@Composable internal fun MarineCompass(os: OsStore, data: VesselDataSnapshot, modifier: Modifier = Modifier) {
    val c = LocalMetro.current
    val typeface = instrumentTypeface()
    val heading = data.headingTrueDegrees.liveNumber()
    val course = data.cogTrueDegrees.liveNumber()
    val headingAngle = animatedBearing(heading)
    val courseAngle = animatedBearing(course)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = min(size.width, size.height) * .43f
                val center = center
                compassScale(center, r, c.fg, c.muted, typeface)
                if (course != null) {
                    val end = radial(center, r * .76f, courseAngle)
                    drawLine(c.muted, center, end, 2.dp.toPx())
                    drawCircle(c.muted, 4.dp.toPx(), end)
                }
                if (heading != null) {
                    rotate(headingAngle, center) {
                        val bow = Path().apply {
                            moveTo(center.x, center.y - r * .67f)
                            lineTo(center.x + r * .16f, center.y + r * .2f)
                            lineTo(center.x, center.y + r * .08f)
                            lineTo(center.x - r * .16f, center.y + r * .2f)
                            close()
                        }
                        drawPath(bow, c.accent)
                    }
                } else drawCircle(c.muted.copy(alpha = .35f), 4.dp.toPx(), center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Label(os.t("船首向 · 真北", "heading · true"), 14, c.accent); Label(os.formatBearing(heading), 34) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Label(os.t("对地航向 · 真北", "course · true"), 14, c.muted); Label(os.formatBearing(course), 34) }
        }
    }
}

@Composable internal fun WindRose(os: OsStore, data: VesselDataSnapshot, modifier: Modifier = Modifier) {
    val c = LocalMetro.current
    val typeface = instrumentTypeface()
    val apparent = data.apparentWind.angleDegrees.liveNumber()
    val trueAngle = data.trueWind.angleDegrees.liveNumber()
    val apparentAngle = animatedBearing(apparent)
    val trueAnimatedAngle = animatedBearing(trueAngle)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(270.dp)) {
            val r = min(size.width, size.height) * .42f
            drawCircle(c.muted.copy(alpha = .25f), r, center, style = Stroke(1.dp.toPx()))
            for (degrees in 0 until 360 step 15) {
                drawLine(c.muted.copy(alpha = .65f), radial(center, r, degrees.toFloat()), radial(center, r * if (degrees % 45 == 0) .88f else .94f, degrees.toFloat()), 1.dp.toPx())
            }
            drawLine(c.muted.copy(alpha = .4f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())
            val hull = Path().apply {
                moveTo(center.x, center.y - r * .34f)
                cubicTo(center.x + r * .25f, center.y - r * .07f, center.x + r * .17f, center.y + r * .26f, center.x + r * .12f, center.y + r * .34f)
                lineTo(center.x - r * .12f, center.y + r * .34f)
                cubicTo(center.x - r * .17f, center.y + r * .26f, center.x - r * .25f, center.y - r * .07f, center.x, center.y - r * .34f)
                close()
            }
            drawPath(hull, c.fg, style = Stroke(1.5.dp.toPx()))
            if (trueAngle != null) windArrow(center, r, trueAnimatedAngle, c.fg.copy(alpha = .65f))
            if (apparent != null) windArrow(center, r, apparentAngle, c.accent)
            compassLabel("0°", Offset(center.x, center.y - r - 8.dp.toPx()), c.fg, 13.dp.toPx(), typeface)
            compassLabel("90°", Offset(center.x + r + 16.dp.toPx(), center.y + 4.dp.toPx()), c.muted, 11.dp.toPx(), typeface)
            compassLabel("90°", Offset(center.x - r - 16.dp.toPx(), center.y + 4.dp.toPx()), c.muted, 11.dp.toPx(), typeface)
            compassLabel("180°", Offset(center.x, center.y + r + 18.dp.toPx()), c.muted, 11.dp.toPx(), typeface)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Label(os.t("视风", "apparent wind"), 16, c.accent); Label(os.formatSpeed(data.apparentWind.speedKnots.liveNumber()), 30); Label(os.formatAngle(apparent), 18, c.accent) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Label(os.t("真风", "true wind"), 16, c.muted); Label(os.formatSpeed(data.trueWind.speedKnots.liveNumber()), 30); Label(os.formatAngle(trueAngle), 18, c.muted) }
        }
        Spacer(Modifier.height(10.dp))
        Label(os.t("箭头表示风从哪一侧吹来 · 船艏朝上", "arrows show where wind comes from · bow up"), 13, c.muted)
    }
}

@Composable internal fun AttitudeHorizon(os: OsStore, data: VesselDataSnapshot, modifier: Modifier = Modifier) {
    val c = LocalMetro.current
    val typeface = instrumentTypeface()
    val attitude = data.attitude.value?.takeIf { data.attitude.freshness == VesselDataFreshness.FRESH }
    val roll by animateFloatAsState(attitude?.heelDegrees?.toFloat() ?: 0f, tween(250), label = "heel")
    val pitch by animateFloatAsState(attitude?.pitchDegrees?.toFloat() ?: 0f, tween(250), label = "pitch")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).clipToBounds()) {
            val radius = min(size.width, size.height) * .44f
            drawArc(c.muted.copy(alpha = .3f), 200f, 140f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(1.dp.toPx()))
            for (mark in -60..60 step 15) drawLine(c.muted, radial(center, radius, mark.toFloat()), radial(center, radius * .92f, mark.toFloat()), 1.dp.toPx())
            if (attitude != null) rotate(-roll, center) {
                val horizonY = center.y + pitch.coerceIn(-45f, 45f) / 45f * radius
                drawRect(c.accent.copy(alpha = .08f), Offset(0f, horizonY), Size(size.width, (size.height - horizonY).coerceAtLeast(0f)))
                drawLine(c.accent, Offset(0f, horizonY), Offset(size.width, horizonY), 2.dp.toPx())
                for (degree in -30..30 step 10) if (degree != 0) {
                    val y = horizonY - degree / 45f * radius
                    val half = if (degree % 20 == 0) 34.dp.toPx() else 19.dp.toPx()
                    drawLine(c.muted.copy(alpha = .65f), Offset(center.x - half, y), Offset(center.x + half, y), 1.dp.toPx())
                }
            }
            drawLine(c.fg, Offset(center.x - radius * .6f, center.y), Offset(center.x - 12.dp.toPx(), center.y), 3.dp.toPx())
            drawLine(c.fg, Offset(center.x + 12.dp.toPx(), center.y), Offset(center.x + radius * .6f, center.y), 3.dp.toPx())
            drawCircle(c.fg, 5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Label(os.t("横倾", "heel"), 15, c.muted); Label(os.formatAngle(attitude?.heelDegrees), 39, c.accent) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Label(os.t("纵倾", "pitch"), 15, c.muted); Label(os.formatAngle(attitude?.pitchDegrees), 39) }
        }
        if (attitude == null) Label(os.t("等待已校准的船体姿态", "waiting for calibrated vessel attitude"), 16, c.muted)
    }
}

/** 中文：可比较的量使用刻度；方位使用罗盘；正负偏差使用中线，不渲染无意义的 3D 装饰。 */
@Composable internal fun InstrumentGauge(os: OsStore, data: VesselDataSnapshot, tile: InstrumentTileId, modifier: Modifier = Modifier) {
    val c = LocalMetro.current
    val typeface = instrumentTypeface()
    val heading = when (tile) {
        InstrumentTileId.HEADING -> data.headingTrueDegrees.liveNumber()
        InstrumentTileId.COG -> data.cogTrueDegrees.liveNumber()
        InstrumentTileId.TRUE_WIND_DIRECTION -> data.trueWind.directionDegrees.liveNumber()
        InstrumentTileId.CURRENT_SET -> data.currentSetTrueDegrees.liveNumber()
        InstrumentTileId.WAYPOINT_BEARING -> data.waypointBearingTrueDegrees.liveNumber()
        else -> null
    }
    val bearingType = tile in setOf(InstrumentTileId.HEADING, InstrumentTileId.COG, InstrumentTileId.TRUE_WIND_DIRECTION, InstrumentTileId.CURRENT_SET, InstrumentTileId.WAYPOINT_BEARING)
    val attitude = data.attitude.value?.takeIf { data.attitude.freshness == VesselDataFreshness.FRESH }
    val raw = when (tile) {
        InstrumentTileId.SOG -> data.sogKnots.liveNumber()
        InstrumentTileId.BOAT_SPEED -> data.speedThroughWaterKnots.liveNumber()
        InstrumentTileId.TRUE_WIND_SPEED -> data.trueWind.speedKnots.liveNumber()
        InstrumentTileId.APPARENT_WIND_SPEED -> data.apparentWind.speedKnots.liveNumber()
        InstrumentTileId.CURRENT_DRIFT -> data.currentDriftKnots.liveNumber()
        InstrumentTileId.VMG -> data.derived.vmgToWindKnots.liveNumber()
        InstrumentTileId.VMC -> data.derived.vmcToWaypointKnots.liveNumber()
        InstrumentTileId.RUDDER_ANGLE -> data.rudderAngleDegrees.liveNumber()
        InstrumentTileId.RATE_OF_TURN -> data.rateOfTurnDegreesPerMinute.liveNumber()
        InstrumentTileId.APPARENT_WIND_ANGLE -> data.apparentWind.angleDegrees.liveNumber()
        InstrumentTileId.TRUE_WIND_ANGLE -> data.trueWind.angleDegrees.liveNumber()
        InstrumentTileId.HEEL -> attitude?.heelDegrees
        InstrumentTileId.PITCH -> attitude?.pitchDegrees
        InstrumentTileId.ROLL_RATE -> attitude?.rollRateDegreesPerSecond
        InstrumentTileId.PITCH_RATE -> attitude?.pitchRateDegreesPerSecond
        InstrumentTileId.CROSS_TRACK_ERROR -> data.crossTrackErrorNauticalMiles.liveNumber()?.times(1852.0)
        InstrumentTileId.PRESSURE -> data.pressureHpa.liveNumber()
        InstrumentTileId.PRESSURE_TREND_1H -> data.derived.pressureTrend1hHpa.liveNumber()
        InstrumentTileId.PRESSURE_TREND_3H -> data.derived.pressureTrend3hHpa.liveNumber()
        InstrumentTileId.PRESSURE_TREND_6H -> data.derived.pressureTrend6hHpa.liveNumber()
        InstrumentTileId.WATER_TEMPERATURE -> data.waterTemperatureCelsius.liveNumber()
        InstrumentTileId.AIR_TEMPERATURE -> data.airTemperatureCelsius.liveNumber()
        InstrumentTileId.DEPTH -> data.depthMeters.liveNumber()
        InstrumentTileId.UKC -> data.derived.underKeelClearanceMeters.liveNumber()
        else -> null
    }
    val signed = tile in setOf(InstrumentTileId.HEEL, InstrumentTileId.PITCH, InstrumentTileId.RUDDER_ANGLE, InstrumentTileId.RATE_OF_TURN, InstrumentTileId.ROLL_RATE, InstrumentTileId.PITCH_RATE, InstrumentTileId.CROSS_TRACK_ERROR, InstrumentTileId.PRESSURE_TREND_1H, InstrumentTileId.PRESSURE_TREND_3H, InstrumentTileId.PRESSURE_TREND_6H, InstrumentTileId.VMG, InstrumentTileId.VMC)
    val supported = bearingType || raw != null || tile in setOf(InstrumentTileId.SOG, InstrumentTileId.BOAT_SPEED, InstrumentTileId.TRUE_WIND_SPEED, InstrumentTileId.APPARENT_WIND_SPEED, InstrumentTileId.RUDDER_ANGLE, InstrumentTileId.HEEL, InstrumentTileId.PITCH)
    if (!supported) return
    val angle = animatedBearing(heading)
    val range = when (tile) {
        InstrumentTileId.HEEL, InstrumentTileId.PITCH, InstrumentTileId.RUDDER_ANGLE -> 45.0
        InstrumentTileId.TRUE_WIND_ANGLE, InstrumentTileId.APPARENT_WIND_ANGLE -> 360.0
        InstrumentTileId.PRESSURE -> 80.0
        InstrumentTileId.PRESSURE_TREND_1H, InstrumentTileId.PRESSURE_TREND_3H, InstrumentTileId.PRESSURE_TREND_6H -> 10.0
        InstrumentTileId.WATER_TEMPERATURE, InstrumentTileId.AIR_TEMPERATURE -> 50.0
        else -> max(10.0, ceil(abs(raw ?: 0.0) / 10.0) * 10.0)
    }
    val normalized = ((if (tile == InstrumentTileId.PRESSURE) (raw ?: 970.0) - 970.0 else raw ?: 0.0) / range).toFloat().coerceIn(if (signed) -1f else 0f, 1f)
    val fraction by animateFloatAsState(normalized, tween(350), label = "instrument-scale")
    Canvas(modifier.fillMaxWidth().height(if (bearingType) 94.dp else 38.dp)) {
        if (bearingType) {
            val r = min(size.width, size.height) * .43f
            drawCircle(c.muted.copy(alpha = .35f), r, center, style = Stroke(1.dp.toPx()))
            for (mark in 0 until 360 step 30) drawLine(c.muted, radial(center, r, mark.toFloat()), radial(center, r * .88f, mark.toFloat()), 1.dp.toPx())
            compassLabel("N", Offset(center.x, center.y - r - 3.dp.toPx()), c.muted, 10.dp.toPx(), typeface)
            if (heading != null) drawLine(c.accent, center, radial(center, r * .8f, angle), 3.dp.toPx())
        } else {
            val y = size.height * .42f
            val left = 2.dp.toPx(); val right = size.width - left
            drawLine(c.muted.copy(alpha = .3f), Offset(left, y), Offset(right, y), 2.dp.toPx())
            for (tick in 0..10) {
                val x = left + (right - left) * tick / 10f
                drawLine(c.muted.copy(alpha = .5f), Offset(x, y - if (tick % 5 == 0) 7.dp.toPx() else 3.dp.toPx()), Offset(x, y + 3.dp.toPx()), 1.dp.toPx())
            }
            if (raw != null) {
                val origin = if (signed) center.x else left
                val endpoint = if (signed) center.x + fraction * (right - left) / 2 else left + fraction * (right - left)
                drawLine(c.accent, Offset(origin, y), Offset(endpoint, y), 4.dp.toPx())
                drawLine(c.fg, Offset(endpoint, y - 7.dp.toPx()), Offset(endpoint, y + 7.dp.toPx()), 2.dp.toPx())
            }
            fun scaleLabel(v: Double): String = when (tile) {
                InstrumentTileId.PRESSURE -> (970 + v).toInt().toString()
                InstrumentTileId.SOG, InstrumentTileId.BOAT_SPEED, InstrumentTileId.TRUE_WIND_SPEED, InstrumentTileId.APPARENT_WIND_SPEED, InstrumentTileId.CURRENT_DRIFT, InstrumentTileId.VMG, InstrumentTileId.VMC -> os.formatSpeed(v)
                InstrumentTileId.DEPTH, InstrumentTileId.UKC -> os.formatDepth(v)
                InstrumentTileId.CROSS_TRACK_ERROR -> (if (v < 0) "−" else "") + os.formatDistance(abs(v))
                InstrumentTileId.WATER_TEMPERATURE, InstrumentTileId.AIR_TEMPERATURE -> os.formatTemperature(v)
                else -> v.toInt().toString()
            }
            compassLabel(scaleLabel(if (signed) -range else 0.0), Offset(left + 25.dp.toPx(), size.height - 1.dp.toPx()), c.muted, 10.dp.toPx(), typeface)
            if (signed) compassLabel("0", Offset(center.x, size.height - 1.dp.toPx()), c.muted, 10.dp.toPx(), typeface)
            compassLabel(scaleLabel(range), Offset(right - 25.dp.toPx(), size.height - 1.dp.toPx()), c.muted, 10.dp.toPx(), typeface)
        }
    }
}

private fun radial(center: Offset, radius: Float, degrees: Float): Offset {
    val angle = Math.toRadians(degrees.toDouble() - 90)
    return Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
}
private fun DrawScope.windArrow(center: Offset, radius: Float, angle: Float, color: Color) {
    rotate(angle, center) {
        val start = Offset(center.x, center.y - radius * .9f)
        val end = Offset(center.x, center.y - radius * .4f)
        drawLine(color, start, end, 3.dp.toPx())
        drawLine(color, end, Offset(end.x - 7.dp.toPx(), end.y - 9.dp.toPx()), 2.dp.toPx())
        drawLine(color, end, Offset(end.x + 7.dp.toPx(), end.y - 9.dp.toPx()), 2.dp.toPx())
    }
}
private fun DrawScope.compassScale(center: Offset, radius: Float, fg: Color, muted: Color, typeface: Typeface?) {
    drawCircle(muted.copy(alpha = .18f), radius, center, style = Stroke(1.dp.toPx()))
    for (degree in 0 until 360 step 5) {
        val factor = if (degree % 30 == 0) .87f else if (degree % 10 == 0) .92f else .96f
        drawLine(if (degree % 30 == 0) fg else muted.copy(alpha = .6f), radial(center, radius, degree.toFloat()), radial(center, radius * factor, degree.toFloat()), 1.dp.toPx())
    }
    listOf("N", "E", "S", "W").forEachIndexed { i, letter ->
        val pos = radial(center, radius * .73f, i * 90f)
        compassLabel(letter, pos + Offset(0f, 5.dp.toPx()), fg, 17.dp.toPx(), typeface)
    }
}
private fun DrawScope.compassLabel(text: String, position: Offset, color: Color, pixels: Float, font: Typeface?) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = pixels; textAlign = Paint.Align.CENTER; typeface = font ?: Typeface.DEFAULT }
    drawContext.canvas.nativeCanvas.drawText(text, position.x, position.y, paint)
}

@Composable private fun instrumentTypeface(): Typeface? {
    val context = LocalContext.current
    return remember(context) { ResourcesCompat.getFont(context, R.font.selawik_light) }
}

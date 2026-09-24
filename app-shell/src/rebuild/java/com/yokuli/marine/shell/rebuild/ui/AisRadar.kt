package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.scene.ais.AisLocalFrame
import com.yokuli.marine.shell.rebuild.scene.ais.AisScenePosition
import com.yokuli.marine.shell.rebuild.scene.ais.AisVector3
import com.yokuli.marine.shell.rebuild.scene.ais.validAisBearing
import com.yokuli.runtime.contract.ais.*
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import kotlin.math.*

/** AIS 距离方位盘，不是实体雷达；显示范围绝不参与交通风险计算。 */
@Composable
internal fun AisRadar(
    os: OsStore,
    s: TrafficSnapshot,
    selected: Int?,
    showTracks: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetro.current
    val enabled = LocalInternalAppInputEnabled.current
    val own = s.ownship?.takeIf { it.positionValid && it.position?.radarValid() == true }
    val heading = validAisBearing(own?.headingDegrees)
    val course = validAisBearing(own?.cogDegrees)?.takeIf { (own?.sogMetersPerSecond ?: 0.0) >= .3 }
    val requested = s.preferences.orientation
    val orientation = when (requested) {
        AisOrientation.HEADING_UP -> if (heading != null) requested else AisOrientation.NORTH_UP
        AisOrientation.COURSE_UP -> if (course != null) requested else AisOrientation.NORTH_UP
        else -> AisOrientation.NORTH_UP
    }
    val bearing = when (orientation) { AisOrientation.HEADING_UP -> heading!!; AisOrientation.COURSE_UP -> course!!; else -> 0.0 }
    var fallbackFrom by rememberSaveable { mutableStateOf<AisOrientation?>(null) }
    var overlaps by remember { mutableStateOf<List<Int>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    val rangeMeters = s.preferences.rangeNauticalMiles.coerceIn(.25, 16.0) * 1852.0
    val frame = remember(own?.position) { own?.position?.let { AisLocalFrame(it.radarPosition()) } }
    val projection = remember(frame, rangeMeters, bearing, size) { frame?.let { RadarProjection(it, rangeMeters, bearing, size) } }
    val positioned = s.targets.filter { it.position?.radarValid() == true }
    val inside = remember(positioned, projection) { if (projection == null) emptyList() else positioned.filter { projection.distance(it.position!!) <= rangeMeters } }
    val chosen = selected?.let(s::target)
    val offscreen = positioned.filter { target -> (target.mmsi == selected || target.riskLevel != AisRiskLevel.NONE || target.distress == AisDistressState.ACTIVE) && projection != null && target.position != null && projection.distance(target.position!!) > rangeMeters }
        .sortedWith(compareByDescending<AisTarget> { it.mmsi == selected }.thenByDescending { it.riskLevel.ordinal }.thenBy { it.mmsi })
    val currentProjection = rememberUpdatedState(projection)
    val currentTargets = rememberUpdatedState(positioned)
    val currentSelect = rememberUpdatedState(onSelect)
    val currentEnabled = rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val hitRadius = with(density) { 26.dp.toPx() }
    val now = s.generatedElapsed

    AppBackHandler(enabled && (overlaps.isNotEmpty() || showExplanation)) { if (overlaps.isNotEmpty()) overlaps = emptyList() else showExplanation = false }
    LaunchedEffect(requested, orientation) {
        if (requested != orientation) {
            fallbackFrom = requested
            os.aisPreferences { it.copy(orientation = AisOrientation.NORTH_UP) }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Label(os.t("AIS 雷达", "AIS radar"), 18)
            Label(os.t("非雷达回波 ⓘ", "not radar echoes ⓘ"), 12, colors.muted,
                Modifier.clickable(enabled = enabled, role = Role.Button) { showExplanation = !showExplanation }.padding(10.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            AisOrientation.entries.forEach { mode ->
                val available = mode == AisOrientation.NORTH_UP || mode == AisOrientation.HEADING_UP && heading != null || mode == AisOrientation.COURSE_UP && course != null
                val title = when (mode) { AisOrientation.NORTH_UP -> os.t("北向上", "north up"); AisOrientation.HEADING_UP -> os.t("船艏向上", "heading up"); AisOrientation.COURSE_UP -> os.t("航迹向上", "course up") }
                Label(title, 15, if (!available) colors.muted.copy(alpha = .5f) else if (orientation == mode) colors.accent else colors.fg,
                    Modifier.heightIn(min = 40.dp).clickable(enabled = enabled && available, role = Role.Button) { fallbackFrom = null; os.aisPreferences { it.copy(orientation = mode) } }.padding(vertical = 9.dp))
            }
        }
        if (fallbackFrom != null) Label(when (fallbackFrom) {
            AisOrientation.HEADING_UP -> os.t("船首向未更新，已切换北向上", "Heading unavailable; switched to north up")
            else -> os.t("对地航迹方向不足以定向，已切换北向上", "Course is unavailable or too slow; switched to north up")
        }, 12, colors.muted, Modifier.padding(horizontal = 14.dp))

        Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { size = it }) {
            if (projection == null) {
                Column(Modifier.align(Alignment.Center).padding(26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(os.t("等待本船位置", "waiting for own position"), 27)
                    Label(os.t("当前没有有效本船位置，不能展示相对距离或 CPA。已收到的目标仍在列表和海图中。", "Without a valid own position, relative ranges and CPA cannot be shown. Received targets remain available in the list and chart."), 16, colors.muted)
                    s.ownship?.positionElapsed?.let { elapsed -> Label(os.t("上次本船观测 · ", "last own observation · ") + readingAge(os, elapsed, now), 14, colors.muted) }
                }
            } else {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = os.t("按真实距离和方位排列的 AIS 目标，不是雷达回波", "AIS targets arranged by true range and bearing, not radar echoes") }
                    .pointerInput(Unit) {
                        var pressedIds = emptyList<Int>()
                        detectTapGestures(onPress = { tap ->
                            val p = currentProjection.value
                            pressedIds = if (!currentEnabled.value || p == null) emptyList() else currentTargets.value.mapNotNull { target ->
                                val position = target.position ?: return@mapNotNull null
                                if (p.distance(position) > p.rangeMeters) return@mapNotNull null
                                val distance = (p.point(position) - tap).getDistance()
                                if (distance <= hitRadius) target.mmsi to distance else null
                            }.sortedBy { it.second }.map { it.first }
                        }, onTap = {
                            if (!currentEnabled.value) return@detectTapGestures
                            // 按下时固定身份，报文刷新不在抬手时把另一艘船换进点击结果。
                            val hits = pressedIds
                            if (hits.size == 1) currentSelect.value(hits.first()) else if (hits.isNotEmpty()) overlaps = hits
                        })
                    }) {
                    val center = projection.center
                    val radius = projection.radius
                    if (radius <= 0f) return@Canvas
                    val muted = colors.muted.copy(alpha = .42f)
                    listOf(.25f, .5f, .75f, 1f).forEach { drawCircle(muted, radius * it, center, style = Stroke(1.dp.toPx())) }
                    for (degrees in 0 until 360 step 10) {
                        val angle = Math.toRadians(degrees - bearing)
                        val v = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                        val tick = if (degrees % 30 == 0) 7.dp.toPx() else 3.dp.toPx()
                        drawLine(muted, center + v * (radius - tick), center + v * radius, 1.dp.toPx())
                    }
                    for (degrees in 0 until 360 step 90) {
                        val angle = Math.toRadians(degrees - bearing)
                        val v = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                        drawLine(muted.copy(alpha = .24f), center, center + v * radius, 1.dp.toPx())
                    }
                    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center - Offset(radius, radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2))) }
                    clipPath(clip) {
                    fun observedTrack(target: AisTarget, color: Color) {
                        if (!showTracks && target.mmsi != selected) return
                        val grouped = target.track.takeLast(240).groupBy { it.segment }
                        grouped.values.forEach { segment ->
                            val points = segment.filter { it.position.radarValid() }.map { projection.point(it.position) }
                            if (points.size > 1) {
                                val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                                drawPath(path, color.copy(alpha = .5f), style = Stroke(1.3.dp.toPx()))
                            }
                        }
                    }
                    positioned.filter { it.mmsi == selected && it !in inside }.forEach { observedTrack(it, radarColor(it, colors)) }
                    inside.forEach { target ->
                        val color = radarColor(target, colors)
                        observedTrack(target, color)
                        val point = projection.point(target.position!!)
                        val dynamic = target.dynamic
                        val current = target.radarCurrent()
                        if (current && dynamic?.invalidFields?.contains("sog_lower_bound") != true) drawGroundVector(projection, target.position!!, dynamic?.cogDegrees, dynamic?.sogMetersPerSecond, color.copy(alpha = .8f))
                        drawRadarTarget(target, point, validAisBearing(dynamic?.headingDegrees)?.takeIf { current }?.minus(bearing), color, target.mmsi == selected)
                    }
                    val ownPoint = own?.position
                    if (ownPoint != null) drawGroundVector(projection, ownPoint, own.cogDegrees, own.sogMetersPerSecond, colors.fg)
                    drawOwnRadar(center, heading?.minus(bearing), colors.fg)
                    // 预测只取共享服务已算出的同一时刻坐标，不在 Canvas 中另算一套 CPA。
                    chosen?.takeIf { it.relative.state in setOf(AisCpaState.CALCULATED, AisCpaState.ESTIMATED) && (it.relative.tcpaSeconds ?: -1.0) >= 0.0 }?.relative?.let { relative ->
                        val predictedOwn = relative.ownAtCpa?.takeIf { it.radarValid() }
                        val predictedTarget = relative.targetAtCpa?.takeIf { it.radarValid() }
                        if (predictedOwn != null && predictedTarget != null) {
                            val pOwn = projection.point(predictedOwn); val pTarget = projection.point(predictedTarget)
                            val predicted = colors.accent.copy(alpha = .75f)
                            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                            if (projection.distance(predictedOwn) <= rangeMeters && projection.distance(predictedTarget) <= rangeMeters) {
                                drawLine(predicted, pOwn, pTarget, 1.5.dp.toPx(), pathEffect = dash)
                                drawCircle(predicted, 5.dp.toPx(), pOwn, style = Stroke(1.4.dp.toPx(), pathEffect = dash))
                                drawCircle(predicted, 5.dp.toPx(), pTarget, style = Stroke(1.4.dp.toPx(), pathEffect = dash))
                            }
                        }
                    }
                    }
                }

                // 文字是原生平面 UI，不随盘面旋转；数字与所有应用共用单位格式。
                for (degrees in listOf(0, 90, 180, 270)) {
                    val angle = Math.toRadians(degrees - bearing)
                    val p = projection.center + Offset(sin(angle).toFloat(), -cos(angle).toFloat()) * (projection.radius + 13f * density.density)
                    val text = when (degrees) { 0 -> "N"; 90 -> "E"; 180 -> "S"; else -> "W" }
                    Label(text, 12, if (degrees == 0) colors.accent else colors.muted,
                        Modifier.offset { IntOffset((p.x - 6 * density.density).roundToInt(), (p.y - 7 * density.density).roundToInt()) })
                }
                Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 3.dp).background(colors.bg.copy(alpha = .8f))) {
                    Label(os.t("距圈 ", "rings ") + os.formatDistance(rangeMeters / 4.0), 11, colors.muted)
                    Label(os.t("盘内 ${inside.size} / 有位置 ${positioned.size}", "in view ${inside.size} / located ${positioned.size}"), 11, colors.muted)
                }
                if (inside.isEmpty()) Label(os.t("当前范围内没有可显示目标", "no located targets in this range"), 13, colors.muted,
                    Modifier.align(Alignment.BottomCenter).background(colors.bg.copy(alpha = .9f)).padding(8.dp))
                if (offscreen.isNotEmpty()) {
                    Canvas(Modifier.fillMaxSize()) {
                        offscreen.take(64).forEach { target ->
                        val position = target.position!!
                        val point = projection.point(position)
                        val vector = point - projection.center
                        val unit = vector / vector.getDistance().coerceAtLeast(1f)
                        val edge = projection.center + unit * (projection.radius - 10f * density.density)
                        val tangent = Offset(-unit.y, unit.x)
                        val arrow = Path().apply {
                            val tip = edge + unit * 6.dp.toPx(); moveTo(tip.x, tip.y)
                            val left = edge - unit * 5.dp.toPx() + tangent * 4.dp.toPx(); lineTo(left.x, left.y)
                            val right = edge - unit * 5.dp.toPx() - tangent * 4.dp.toPx(); lineTo(right.x, right.y); close()
                        }
                        drawPath(arrow, if (target.mmsi == selected) colors.accent else radarColor(target, colors))
                        }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(colors.bg.copy(alpha = .96f))
                        .clickable(enabled = enabled, role = Role.Button) { if (offscreen.size == 1) onSelect(offscreen.first().mmsi) else overlaps = offscreen.map { it.mmsi } }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        val first = offscreen.first()
                        Label(os.t("范围外 ${offscreen.size} 个需关注 · ", "${offscreen.size} targets of interest outside range · ") + first.displayName, 14, colors.accent, maxLines = 1)
                        Label(os.formatDistance(projection.distance(first.position!!)) + " · " + (first.relative.bearingDegrees?.let { os.formatBearing(it) + " T" } ?: aisState(os, first)), 12, colors.muted)
                    }
                }
                if (showExplanation) RadarInfo(os, Modifier.align(Alignment.Center).fillMaxWidth(.92f)) { showExplanation = false }
                if (overlaps.isNotEmpty()) {
                    Column(Modifier.align(Alignment.Center).fillMaxWidth(.9f).heightIn(max = 260.dp).background(colors.bg).border(1.dp, colors.muted).padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Label(os.t("选择目标", "choose target"), 23)
                            IconAction("close", os.t("关闭", "close"), { overlaps = emptyList() })
                        }
                        LazyColumn {
                            items(overlaps, key = { it }) { mmsi ->
                                val target = s.target(mmsi)
                                Column(Modifier.fillMaxWidth().clickable(enabled = enabled && target != null) { overlaps = emptyList(); onSelect(mmsi) }.padding(vertical = 10.dp)) {
                                    Label(target?.displayName ?: aisNumber(mmsi), 17)
                                    Label(target?.let { aisKind(os, it.kind) + " · " + aisState(os, it) } ?: os.t("目标已退出当前资料", "target no longer retained"), 12, colors.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showExplanation && projection == null) RadarInfo(os, Modifier.fillMaxWidth().padding(12.dp)) { showExplanation = false }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(.25, .5, 1.0, 2.0, 4.0, 8.0, 16.0).forEach { nm ->
                val value = nm * 1852.0
                Label(os.formatDistance(value), 14, if (abs(s.preferences.rangeNauticalMiles - nm) < .001) colors.accent else colors.muted,
                    Modifier.heightIn(min = 42.dp).clickable(enabled = enabled, role = Role.Button) { os.aisPreferences { it.copy(rangeNauticalMiles = nm) } }.padding(vertical = 10.dp))
            }
        }
        val hasPrediction = chosen?.relative?.let { it.state in setOf(AisCpaState.CALCULATED, AisCpaState.ESTIMATED) && (it.tcpaSeconds ?: -1.0) >= 0 && it.ownAtCpa != null && it.targetAtCpa != null } == true
        Label(if (hasPrediction) os.t("实线：观测轨迹 · 虚线：60 秒对地向量 · 空心圈：会遇推算", "solid: observations · dashed: 60 s ground vector · hollow: CPA estimate")
            else os.t("实线：观测轨迹 · 虚线：60 秒对地向量", "solid: observations · dashed: 60 s ground vector"), 11, colors.muted, Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
    }
}

private fun AisPoint.radarPosition() = AisScenePosition(latitude, longitude)
private fun AisPoint.radarValid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
private fun AisTarget.radarCurrent() = state == AisTargetState.CURRENT && !cached && !positionInvalidated

/** 米制比例只乘一个线性屏幕系数；盘外目标不压缩进距离环。 */
private class RadarProjection(val frame: AisLocalFrame, val rangeMeters: Double, bearing: Double, size: IntSize) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = (min(size.width, size.height) * .43f).coerceAtLeast(0f)
    private val angle = Math.toRadians(bearing)
    fun distance(point: AisPoint): Double { val p = frame.position(point.radarPosition()); return hypot(p.x, p.z) }
    fun point(point: AisPoint): Offset = point(frame.position(point.radarPosition()))
    fun point(world: AisVector3): Offset {
        val north = -world.z
        val right = cos(angle) * world.x - sin(angle) * north
        val forward = sin(angle) * world.x + cos(angle) * north
        return center + Offset((right / rangeMeters * radius).toFloat(), (-forward / rangeMeters * radius).toFloat())
    }
}

private fun radarColor(target: AisTarget, colors: MetroColors): Color = when {
    !target.radarCurrent() -> colors.muted.copy(alpha = .68f)
    target.distress == AisDistressState.ACTIVE || target.riskLevel in setOf(AisRiskLevel.URGENT, AisRiskLevel.WARNING) -> Color(0xfff16a51)
    target.riskLevel == AisRiskLevel.ATTENTION -> Color(0xffd79a42)
    target.watched -> colors.accent
    else -> colors.fg
}

private fun DrawScope.drawGroundVector(projection: RadarProjection, origin: AisPoint, cog: Double?, speed: Double?, color: Color) {
    val course = validAisBearing(cog) ?: return
    val velocity = speed?.takeIf { it.isFinite() && it > .05 && it < 1000 } ?: return
    val angle = Math.toRadians(course)
    val start = projection.frame.position(origin.radarPosition())
    val end = start + AisVector3(sin(angle) * velocity * 60.0, 0.0, -cos(angle) * velocity * 60.0)
    val from = projection.point(start); val to = projection.point(end)
    drawLine(color, from, to, 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    val diff = to - from
    if (diff.getDistance() > 9.dp.toPx()) {
        val direction = diff / diff.getDistance()
        val cross = Offset(-direction.y, direction.x)
        val arrow = Path().apply {
            moveTo(to.x, to.y)
            val left = to - direction * 6.dp.toPx() + cross * 3.dp.toPx(); lineTo(left.x, left.y)
            val right = to - direction * 6.dp.toPx() - cross * 3.dp.toPx(); lineTo(right.x, right.y); close()
        }
        drawPath(arrow, color)
    }
}

private fun DrawScope.drawOwnRadar(point: Offset, relativeHeading: Double?, color: Color) {
    if (relativeHeading == null) {
        drawCircle(color, 4.dp.toPx(), point, style = Stroke(2.dp.toPx()))
        drawCircle(color, 1.5.dp.toPx(), point)
    } else drawHull(point, relativeHeading, color, 9.dp.toPx(), true)
}

private fun DrawScope.drawHull(point: Offset, relativeHeading: Double, color: Color, radius: Float, own: Boolean = false) {
    val angle = Math.toRadians(relativeHeading)
    val forward = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
    val starboard = Offset(cos(angle).toFloat(), sin(angle).toFloat())
    val outline = Path().apply {
        val bow = point + forward * radius; moveTo(bow.x, bow.y)
        val right = point - forward * (radius * .5f) + starboard * (radius * .45f); lineTo(right.x, right.y)
        val stern = point - forward * (radius * .3f); lineTo(stern.x, stern.y)
        val left = point - forward * (radius * .5f) - starboard * (radius * .45f); lineTo(left.x, left.y); close()
    }
    if (own) drawPath(outline, color) else drawPath(outline, color, style = Stroke(1.6.dp.toPx()))
}

private fun DrawScope.drawRadarTarget(target: AisTarget, point: Offset, relativeHeading: Double?, color: Color, selected: Boolean) {
    val r = 5.dp.toPx()
    when (target.kind) {
        AisEntityKind.CLASS_A, AisEntityKind.CLASS_B, AisEntityKind.LONG_RANGE -> if (relativeHeading != null) drawHull(point, relativeHeading, color, 8.dp.toPx()) else drawCircle(color, r, point, style = Stroke(1.7.dp.toPx()))
        AisEntityKind.AID_TO_NAVIGATION, AisEntityKind.VIRTUAL_AID -> {
            val diamond = Path().apply { moveTo(point.x, point.y - r); lineTo(point.x + r, point.y); lineTo(point.x, point.y + r); lineTo(point.x - r, point.y); close() }
            drawPath(diamond, color, style = Stroke(1.6.dp.toPx(), pathEffect = if (target.kind == AisEntityKind.VIRTUAL_AID) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())) else null))
            if (target.dynamic?.offPosition == true) drawLine(color, point - Offset(r, r), point + Offset(r, r), 1.6.dp.toPx())
        }
        AisEntityKind.BASE_STATION -> drawRect(color, point - Offset(r, r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(1.6.dp.toPx()))
        AisEntityKind.SAR_AIRCRAFT -> {
            drawLine(color, point - Offset(r * 1.5f, 0f), point + Offset(r * 1.5f, 0f), 2.dp.toPx())
            drawLine(color, point - Offset(0f, r), point + Offset(0f, r), 2.dp.toPx())
            drawLine(color, point + Offset(-r * .55f, r), point + Offset(r * .55f, r), 1.6.dp.toPx())
        }
        AisEntityKind.SART, AisEntityKind.MOB, AisEntityKind.EPIRB -> {
            val active = target.distress == AisDistressState.ACTIVE
            drawCircle(color, r + 1.dp.toPx(), point, style = Stroke(if (active) 2.dp.toPx() else 1.dp.toPx()))
            if (active) {
                drawLine(color, point - Offset(r * .6f, 0f), point + Offset(r * .6f, 0f), 1.5.dp.toPx())
                drawLine(color, point - Offset(0f, r * .6f), point + Offset(0f, r * .6f), 1.5.dp.toPx())
            } else if (target.distress == AisDistressState.TEST) {
                drawLine(color, point - Offset(r * .5f, r * .5f), point + Offset(r * .5f, -r * .5f), 1.5.dp.toPx())
                drawLine(color, point + Offset(0f, -r * .5f), point + Offset(0f, r * .5f), 1.5.dp.toPx())
            } else drawCircle(color, 1.5.dp.toPx(), point)
        }
        AisEntityKind.UNKNOWN -> drawCircle(color, r, point, style = Stroke(1.dp.toPx()))
    }
    if (!target.radarCurrent() && (target.riskLevel != AisRiskLevel.NONE || target.distress == AisDistressState.ACTIVE)) drawCircle(Color(0xfff16a51), 9.dp.toPx(), point, style = Stroke(1.dp.toPx()))
    if (target.state == AisTargetState.LOST || target.positionInvalidated) drawLine(color, point - Offset(r + 2, r + 2), point + Offset(r + 2, r + 2), 1.dp.toPx())
    if (selected) drawCircle(color, 13.dp.toPx(), point, style = Stroke(1.8.dp.toPx()))
}

@Composable
private fun RadarInfo(os: OsStore, modifier: Modifier, close: () -> Unit) {
    val colors = LocalMetro.current
    Column(modifier.background(colors.bg).border(1.dp, colors.muted).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label(os.t("认识 AIS 雷达", "about AIS radar"), 24)
            IconAction("close", os.t("关闭", "close"), close)
        }
        Label(os.t("这是接收到的 AIS 位置与距离方位盘，没有雷达扫描。没有目标不代表周围安全。", "This is a range-and-bearing plot of received AIS positions, without radar scanning. No targets does not mean clear water."), 15)
        Label(os.t("船形仅在有效船首向已提供时指向船艏；圆形不声明船首。虚线使用 COG 表示 60 秒对地运动方向，两者独立。", "A vessel symbol asserts bow direction only with valid heading. A circle makes no heading claim. Dashed COG vectors show 60 seconds of ground motion independently."), 14, colors.muted)
        Label(os.t("实线是分段的实际观测轨迹。若出现空心会遇圈，它们是共享服务的恒速推算，不能当作当前船位。", "Solid tracks are segmented observations. Hollow encounter marks are the shared service's constant-velocity estimate, not current positions."), 14, colors.muted)
    }
}

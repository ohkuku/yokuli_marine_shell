package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    rangeMeters: Double? = null,
) {
    val colors = LocalMetro.current
    val safe = LocalShellHorizontalInsets.current
    val enabled = LocalInternalAppInputEnabled.current
    val own = s.ownship?.takeIf { it.positionValid && it.position?.radarValid() == true }
    val heading = validAisBearing(own?.headingDegrees)
    val course = validAisBearing(own?.cogDegrees)?.takeIf { (own?.sogMetersPerSecond ?: 0.0) >= .3 }
    val requested = s.preferences.orientation
    var fallbackFrom by rememberSaveable { mutableStateOf<AisOrientation?>(null) }
    val requestedAvailable = when (requested) {
        AisOrientation.HEADING_UP -> heading != null
        AisOrientation.COURSE_UP -> course != null
        AisOrientation.NORTH_UP -> true
    }
    // 期望朝向仍由用户偏好拥有。缺数据仅降级本次视图，恢复后由用户明确重新跟随。
    val restoreAvailable = fallbackFrom == requested && requestedAvailable && requested != AisOrientation.NORTH_UP
    val orientation = if (requestedAvailable && fallbackFrom != requested) requested else AisOrientation.NORTH_UP
    val bearing = when (orientation) { AisOrientation.HEADING_UP -> heading!!; AisOrientation.COURSE_UP -> course!!; else -> 0.0 }
    var overlaps by remember { mutableStateOf<List<Int>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var showOrientation by rememberSaveable { mutableStateOf(false) }
    val visibleRange = (rangeMeters?.takeIf { it.isFinite() } ?: (s.preferences.rangeNauticalMiles * 1852.0)).coerceIn(100.0, 59_264.0)
    val frame = remember(own?.position) { own?.position?.let { AisLocalFrame(it.radarPosition()) } }
    val projection = remember(frame, visibleRange, bearing, size) { frame?.let { RadarProjection(it, visibleRange, bearing, size) } }
    val positioned = remember(s.targets) { s.targets.filter { it.position?.radarValid() == true } }
    // 距离只随真实位置改变，不能在拖动倍率尺的每一帧重复做经纬度转换。
    val distances = remember(positioned, frame) { if (frame == null) emptyMap() else positioned.associate { target ->
        val local = frame.position(target.position!!.radarPosition())
        target.mmsi to hypot(local.x, local.z)
    } }
    val inside = remember(positioned, distances, visibleRange) { positioned.filter { (distances[it.mmsi] ?: Double.POSITIVE_INFINITY) <= visibleRange } }
    val chosen = selected?.let(s::target)
    val offscreen = remember(positioned, distances, visibleRange, selected) {
        positioned.filter { target -> (target.mmsi == selected || target.riskLevel != AisRiskLevel.NONE || target.distress == AisDistressState.ACTIVE) &&
            (distances[target.mmsi]?.let { it > visibleRange } == true) }
            .sortedWith(compareByDescending<AisTarget> { it.mmsi == selected }.thenByDescending { it.riskLevel.ordinal }.thenBy { it.mmsi })
    }
    val currentProjection = rememberUpdatedState(projection)
    val currentTargets = rememberUpdatedState(positioned)
    val currentSelect = rememberUpdatedState(onSelect)
    val currentEnabled = rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val hitRadius = with(density) { 26.dp.toPx() }
    val now = s.generatedElapsed

    AppBackHandler(enabled && (overlaps.isNotEmpty() || showExplanation || showOrientation)) {
        when { overlaps.isNotEmpty() -> overlaps = emptyList(); showOrientation -> showOrientation = false; else -> showExplanation = false }
    }
    LaunchedEffect(enabled) { if (!enabled) { showOrientation = false; showExplanation = false; overlaps = emptyList() } }
    LaunchedEffect(requested, requestedAvailable) {
        fallbackFrom = when {
            requested == AisOrientation.NORTH_UP -> null
            !requestedAvailable -> requested
            fallbackFrom != requested -> null
            else -> fallbackFrom
        }
    }
    Box(modifier.onSizeChanged { size = it }) {
            if (projection == null) {
                Column(Modifier.align(Alignment.Center).padding(26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(os.t("等待本船位置", "Waiting for own position"), 20)
                    Label(os.t("有了本船位置，才能显示与其他船舶的距离。已收到的船舶仍可在列表查看。", "Your position is needed to show nearby traffic. Received vessels are still available in the list."), 15, colors.muted)
                    s.ownship?.positionElapsed?.let { elapsed -> Label(os.t("上次本船观测 · ", "Last own observation · ") + readingAge(os, elapsed, now), 12, colors.muted) }
                    MetroButton(os.t("检查船位来源", "Check position source"), { os.openLinked("data_center:source/POSITION") }, enabled = enabled)
                }
            } else {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = os.t("按真实距离和方位排列的 AIS 目标，不是雷达回波", "AIS targets arranged by true range and bearing, not radar echoes") }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // 点击时固定目标身份；不消费按下或横向位移，让父级 Pivot 接管滑页。
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val p = currentProjection.value
                            val hits = if (!currentEnabled.value || p == null) emptyList() else currentTargets.value.mapNotNull { target ->
                                val position = target.position ?: return@mapNotNull null
                                if (p.distance(position) > p.rangeMeters) return@mapNotNull null
                                val distance = (p.point(position) - down.position).getDistance()
                                if (distance <= hitRadius) target.mmsi to distance else null
                            }.sortedBy { it.second }.map { it.first }
                            var isTap = true
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed || event.changes.count { it.pressed } > 1 ||
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop) isTap = false
                                if (!change.pressed) {
                                    if (isTap && currentEnabled.value && hits.isNotEmpty()) {
                                        change.consume()
                                        if (hits.size == 1) currentSelect.value(hits.first()) else overlaps = hits
                                    }
                                    break
                                }
                            }
                        }
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
                            if (projection.distance(predictedOwn) <= visibleRange && projection.distance(predictedTarget) <= visibleRange) {
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
                Label(os.t("每圈 ", "Each ring ") + os.formatDistance(visibleRange / 4.0), 12, colors.muted,
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = if (offscreen.isEmpty()) 8.dp else 66.dp)
                        .background(colors.bg.copy(alpha = .86f)).padding(horizontal = 4.dp, vertical = 2.dp))
                if (inside.isEmpty()) Column(Modifier.align(Alignment.Center).widthIn(max = 300.dp)
                    .background(colors.bg.copy(alpha = .94f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(if (positioned.isEmpty()) os.t("等待周围船舶", "Waiting for nearby traffic")
                        else os.t("此范围内尚无目标", "No targets in this range"), 15, colors.muted)
                    if (positioned.isEmpty()) MetroButton(os.t("检查 AIS 输入", "Check AIS input"), { os.openLinked("nmea") }, enabled = enabled)
                }
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
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 48.dp).background(colors.bg.copy(alpha = .96f))
                        .clickable(enabled = enabled, role = Role.Button) { if (offscreen.size == 1) onSelect(offscreen.first().mmsi) else overlaps = offscreen.map { it.mmsi } }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        val first = offscreen.first()
                        Label(os.t("范围外 ${offscreen.size} 个需关注 · ", "${offscreen.size} targets of interest outside range · ") + first.displayName, 14, colors.accent, maxLines = 1)
                        Label(os.formatDistance(projection.distance(first.position!!)) + " · " + (first.relative.bearingDegrees?.let { os.formatBearing(it) + " T" } ?: aisState(os, first)), 12, colors.muted)
                    }
                }
            }
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = safe.pageStart, end = safe.pageEnd),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f, fill = false).heightIn(min = 48.dp).background(colors.bg.copy(alpha = .9f))
                .semantics { contentDescription = orientationName(os, orientation) }
                .clickable(enabled = enabled, role = Role.Button) { showOrientation = true }
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph("helm", Modifier.size(20.dp), colors.fg)
                Label(orientationName(os, orientation), 12, maxLines = 1)
                Glyph("chevron_down", Modifier.size(12.dp), colors.muted)
                if (fallbackFrom != null && !restoreAvailable) Label("!", 12, colors.accentText)
            }
            if (restoreAvailable) Label(os.t("恢复跟随", "Resume follow"), 12, colors.accentText,
                Modifier.widthIn(max = 112.dp).heightIn(min = 48.dp).background(colors.bg.copy(alpha = .9f))
                    .clickable(enabled = enabled, role = Role.Button) { fallbackFrom = null }.padding(horizontal = 10.dp, vertical = 15.dp))
            Box(Modifier.size(48.dp).background(colors.bg.copy(alpha = .9f))
                .semantics { contentDescription = os.t("图例与说明", "Legend and guide") }
                .clickable(enabled = enabled, role = Role.Button) { showExplanation = true }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(22.dp)) {
                    val middle = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(colors.fg, size.width / 2f - 1.dp.toPx(), middle, style = Stroke(1.5.dp.toPx()))
                    drawCircle(colors.fg, 1.dp.toPx(), Offset(middle.x, middle.y - 4.dp.toPx()))
                    drawLine(colors.fg, Offset(middle.x, middle.y), Offset(middle.x, middle.y + 5.dp.toPx()), 1.5.dp.toPx())
                }
            }
        }
    }
    if (showOrientation && enabled) Dialog(onDismissRequest = { showOrientation = false }) {
        AppDialogSurface {
            AppDialogTitle(os.t("雷达朝向", "Radar orientation"))
            if (restoreAvailable) MetroButton(os.t("恢复", "Resume ") + orientationName(os, requested),
                { fallbackFrom = null; showOrientation = false }, primary = true)
            else if (fallbackFrom != null) Label(when (fallbackFrom) {
                AisOrientation.HEADING_UP -> os.t("船首向尚未更新，当前朝向正北。", "Heading has not updated; the view is facing north.")
                else -> os.t("船舶需要有可靠的运动方向，当前朝向正北。", "A reliable course is needed; the view is facing north.")
            }, 12, colors.muted)
            AisOrientation.entries.forEach { mode ->
                val available = mode == AisOrientation.NORTH_UP || mode == AisOrientation.HEADING_UP && heading != null || mode == AisOrientation.COURSE_UP && course != null
                ChoiceRow(orientationName(os, mode), requested == mode,
                    subtitle = when {
                        available -> null
                        mode == AisOrientation.HEADING_UP -> os.t("等待船首向", "Waiting for heading")
                        else -> os.t("等待可靠的运动方向", "Waiting for reliable course")
                    }, enabled = available) {
                    fallbackFrom = null
                    os.aisPreferences { it.copy(orientation = mode) }
                    showOrientation = false
                }
            }
            if (heading == null) MenuRow(os.t("检查船首向来源", "Check heading source"), icon = "next") {
                showOrientation = false
                os.openLinked("data_center:source/HEADING_TRUE")
            }
            if (course == null && requested == AisOrientation.COURSE_UP) MenuRow(os.t("检查运动方向来源", "Check course source"), icon = "next") {
                showOrientation = false
                os.openLinked("data_center:source/COG")
            }
            MetroButton(os.t("完成", "Done"), { showOrientation = false })
        }
    }
    if (showExplanation && enabled) Dialog(onDismissRequest = { showExplanation = false }) {
        RadarInfo(os) { showExplanation = false }
    }
    if (overlaps.isNotEmpty() && enabled) Dialog(onDismissRequest = { overlaps = emptyList() }) {
        AppDialogSurface {
            AppDialogTitle(os.t("选择船舶", "Choose a vessel"))
            Label(os.t("这些目标在当前范围内靠得很近。", "These targets are close together at this range."), 12, colors.muted)
            // 弹层读最新资料，但身份列表固定在按下时，不因报文刷新改变选择对象。
            overlaps.forEach { mmsi ->
                val target = s.target(mmsi)
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(enabled = target != null, role = Role.Button) { overlaps = emptyList(); onSelect(mmsi) }
                    .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(target?.displayName ?: aisNumber(mmsi), 15, if (target == null) colors.disabled else colors.fg)
                    Label(target?.let { aisKind(os, it.kind) + " · " + aisState(os, it) }
                        ?: os.t("目标已退出当前资料", "Target no longer retained"), 12, colors.muted)
                }
            }
            MetroButton(os.t("取消", "Cancel"), { overlaps = emptyList() })
        }
    }
}

private fun orientationName(os: OsStore, mode: AisOrientation): String = when (mode) {
    AisOrientation.NORTH_UP -> os.t("北向上", "North up")
    AisOrientation.HEADING_UP -> os.t("船艏向上", "Heading up")
    AisOrientation.COURSE_UP -> os.t("航迹向上", "Course up")
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
private fun RadarInfo(os: OsStore, close: () -> Unit) {
    val colors = LocalMetro.current
    AppDialogSurface {
        AppDialogTitle(os.t("认识周围船舶", "Reading the radar"))
        Label(os.t("显示接收到的 AIS 位置，不是雷达回波。没有目标不代表周围安全。", "This view shows received AIS positions, not radar echoes. No targets does not mean clear water."), 15)
        Label(os.t("船形指向已知船首，圆点表示尚无可靠船首向。红色需要立即留意，橙色需要关注；灰色是上次收到的位置。", "A vessel shape shows a known heading; a dot means no reliable heading. Red needs immediate attention, amber needs attention, and grey is the last received position."), 15)
        Label(os.t("实线是实际轨迹，虚线是未来 60 秒的对地运动方向。空心会遇圈是共享服务的恒速估算，不是当前船位。", "Solid lines are observed tracks. Dashed lines show 60 seconds of ground motion. Hollow encounter marks are constant-velocity estimates, not current positions."), 15)
        Label(os.t("显示范围只改变画面，不改变避碰计算。范围尺拖动可连续缩放，点倍率刻度可快速切换。", "Viewing range only changes the display, never collision calculations. Drag the range scale to zoom smoothly, or tap a zoom mark."), 12, colors.muted)
        MetroButton(os.t("知道了", "Got it"), close)
    }
}

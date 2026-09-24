package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.scene.*
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import kotlin.math.abs

/**
 * 仪表姿态工作面：观察船的横倾和纵倾，而非手机传感器设置页。
 * 只消费系统采用的双轴观测；视角、选中指标、3D 降级均为页面展示状态。
 */
@Composable internal fun InstrumentAttitudePanel(
    os: OsStore,
    active: Boolean,
    onMetric: (InstrumentTileId) -> Unit,
) {
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val data = state.vesselData
    val projection = remember(data.heelDegrees, data.pitchDegrees) { VesselAttitudeProjection.from(data) }
    val c = LocalMetro.current
    val enabled = active && LocalInternalAppInputEnabled.current
    val now = rememberMarineClock()
    var viewName by rememberSaveable { mutableStateOf(VesselViewPreset.OVERVIEW.name) }
    val preset = runCatching { VesselViewPreset.valueOf(viewName) }.getOrDefault(VesselViewPreset.OVERVIEW)
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) ready = false }
    val mode = when (projection.state) {
        VesselAttitudeDisplayState.LIVE -> os.t("当前姿态", "current attitude")
        VesselAttitudeDisplayState.LAST -> os.t("上次姿态", "last attitude")
        VesselAttitudeDisplayState.REFERENCE -> os.t("参考船体 · 等待完整姿态", "reference hull · waiting for both axes")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(VesselViewPreset.OVERVIEW, VesselViewPreset.FRONT, VesselViewPreset.SIDE).forEach { view ->
                val text = when (view) {
                    VesselViewPreset.FRONT -> os.t("正视", "front")
                    VesselViewPreset.SIDE -> os.t("侧视", "side")
                    else -> os.t("整体", "whole boat")
                }
                Label(text, 20, if (preset == view) c.accent else c.muted,
                    Modifier.heightIn(min = 48.dp).selectable(preset == view, enabled, Role.Tab) { viewName = view.name }
                        .padding(vertical = 11.dp))
            }
        }
        Label(mode, 15, if (projection.state == VesselAttitudeDisplayState.LIVE) c.accent else c.muted)
        Box(Modifier.fillMaxWidth().height(286.dp).semantics {
            contentDescription = mode + ", " + attitudeDirection(os, data.heelDegrees.attitudeDisplayValue(), true) + ", " +
                attitudeDirection(os, data.pitchDegrees.attitudeDisplayValue(), false)
        }) {
            AttitudeReferencePlane(preset, Modifier.fillMaxSize())
            // 尚未 ready 时保留完整二维读数与船体；不以无限 loading 挡住仪表。
            if (failed || !ready || !active) {
                AttitudeVessel2D(preset, projection.pose, projection.state, Modifier.fillMaxSize())
            }
            if (!failed && active) {
                // 只降低船体的存在感，水平参考与方向标签仍保持清晰；无动画假装恢复实时。
                VesselScene3D(preset, os.light, enabled, Modifier.fillMaxSize().graphicsLayer {
                    alpha = when (projection.state) {
                        VesselAttitudeDisplayState.LIVE -> 1f
                        VesselAttitudeDisplayState.LAST -> .52f
                        VesselAttitudeDisplayState.REFERENCE -> .34f
                    }
                },
                    onFailure = { failed = true }, onReady = { ready = true }, attitude = projection.pose)
            }
            AttitudeLandmarks(preset, projection.pose, Modifier.fillMaxSize())
            Label(os.t("水平参考", "level reference"), 12, c.muted,
                Modifier.align(Alignment.BottomStart).padding(bottom = 5.dp))
            if (failed) Label(os.t("二维姿态", "2D attitude"), 12, c.muted,
                Modifier.align(Alignment.BottomEnd).padding(bottom = 5.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Label("● " + os.t("左舷", "port"), 12, Color(0xFFE66B65))
            Label("● " + os.t("船艏", "bow"), 12, c.accent)
            Label("● " + os.t("右舷", "starboard"), 12, Color(0xFF6ABB8A))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AttitudeAxis(os, os.t("横倾", "heel"), data.heelDegrees, true, now, enabled,
                { onMetric(InstrumentTileId.HEEL) }, Modifier.weight(1f))
            AttitudeAxis(os, os.t("纵倾", "pitch"), data.pitchDegrees, false, now, enabled,
                { onMetric(InstrumentTileId.PITCH) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AttitudeMotionReading(os, os.t("横摇速度", "roll rate"), data.rollRateDegreesPerSecond,
                enabled, { onMetric(InstrumentTileId.ROLL_RATE) }, Modifier.weight(1f))
            AttitudeMotionReading(os, os.t("纵摇速度", "pitch rate"), data.pitchRateDegreesPerSecond,
                enabled, { onMetric(InstrumentTileId.PITCH_RATE) }, Modifier.weight(1f))
        }
        if (projection.state == VesselAttitudeDisplayState.REFERENCE) {
            Label(if (data.heelDegrees.value != null && data.pitchDegrees.value != null && !projection.samplesAligned)
                os.t("两轴的记录时间不同，先分别显示读数。收到同期姿态后船体会跟随变化。", "The axes were measured at different times. Their readings remain separate until a matching pair arrives.")
            else os.t("船体现在只作方向参考。收到可信的横倾和纵倾后才呈现实际姿态。", "This hull is a direction reference. Measured attitude appears when both trusted axes arrive."), 14, c.muted)
        } else if (projection.state == VesselAttitudeDisplayState.LAST) {
            Label(os.t("保留上次测得的姿态；各轴的更新时间见读数下方。", "Showing the last measured pose; each axis shows when it was updated."), 14, c.muted)
        }
        if (data.heelDegrees.quality == VesselDataQuality.DEGRADED || data.pitchDegrees.quality == VesselDataQuality.DEGRADED ||
            data.heelDegrees.conflict?.active == true || data.pitchDegrees.conflict?.active == true) {
            Label(os.t("姿态来源质量降低或存在差异，点选读数查看详情。", "Attitude quality is reduced or sources disagree. Select an axis for details."), 14, c.muted)
        }
        if (failed) Label(os.t("重新加载三维船体", "reload 3D hull"), 15, c.accent,
            Modifier.heightIn(min = 48.dp).clickable(enabled = enabled) { ready = false; failed = false }.padding(vertical = 12.dp))
        MenuRow(os.t("手机安装与校准", "phone mounting & calibration"),
            os.t("手机顶部朝船艏并固定；确认方向不会清除实际横倾。", "Secure the phone with its top towards the bow. Confirming alignment preserves actual heel.")) {
            if (enabled) os.openLinked("data_center:phone")
        }
    }
}

@Composable private fun AttitudeAxis(
    os: OsStore, title: String, observation: VesselObservation<Double>, heel: Boolean,
    now: Long, enabled: Boolean, onClick: () -> Unit, modifier: Modifier,
) {
    val c = LocalMetro.current
    val value = observation.attitudeDisplayValue()
    Column(modifier.clickable(enabled, role = Role.Button, onClick = onClick).padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Label(title, 16, c.muted)
        Label(os.formatAngle(value?.let(::abs)), 34, if (observation.displayIsLive()) c.accent else c.muted)
        Label(attitudeDirection(os, value, heel), 16)
        Label(if (observation.value != null && observation.quality == VesselDataQuality.UNKNOWN)
            os.t("读数质量未知", "reading quality unknown") else observationStatus(os, observation, now), 12, c.muted)
        val source = observation.sourceIdentity?.let { sourceDisplayName(os, it, emptyList()) }
            ?: when (observation.source) {
                VesselDataSource.PHONE_IMU -> os.t("固定手机", "mounted phone")
                VesselDataSource.BOAT_NMEA -> "NMEA"
                VesselDataSource.DEMO -> os.t("演示", "demo")
                else -> null
            }
        source?.let { Label(it, 12, c.muted) }
    }
}

@Composable private fun AttitudeMotionReading(
    os: OsStore, title: String, observation: VesselObservation<Double>, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier,
) {
    val c = LocalMetro.current
    val value = observation.attitudeDisplayValue()
    Column(modifier.clickable(enabled, role = Role.Button, onClick = onClick).padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(title, 14, c.muted)
        Label(value?.let { os.formatMetric("roll_rate", it) } ?: "—", 22,
            if (observation.displayIsLive()) c.fg else c.muted)
        if (value != null && !observation.displayIsLive()) Label(os.t("上次读数", "last reading"), 12, c.muted)
        if (observation.value != null && observation.quality == VesselDataQuality.UNKNOWN)
            Label(os.t("读数质量未知", "reading quality unknown"), 12, c.muted)
    }
}

private fun VesselObservation<Double>.attitudeDisplayValue(): Double? =
    value?.takeIf { it.isFinite() && quality != VesselDataQuality.UNKNOWN }

private fun attitudeDirection(os: OsStore, value: Double?, heel: Boolean): String {
    if (value == null || !value.isFinite()) return os.t("方向待定", "direction unknown")
    if (abs(value) < .05) return os.t("水平", "level")
    return if (heel) {
        if (value > 0) os.t("右舷下沉", "starboard down") else os.t("左舷下沉", "port down")
    } else if (value > 0) os.t("船艏抬起", "bow up") else os.t("船艏压下", "bow down")
}

/** 水平面固定在世界 Y=0，不随船体旋转。虚线表明这是参考，不是测得的海面。 */
@Composable private fun AttitudeReferencePlane(preset: VesselViewPreset, modifier: Modifier) {
    val c = LocalMetro.current
    Canvas(modifier) {
        fun p(x: Float, z: Float): Offset = vesselScenePoint(preset, size.width / size.height, x, 0f, z)
            .let { Offset(it.x * size.width, it.y * size.height) }
        val effect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
        for (x in -2..2) drawLine(c.muted.copy(alpha = .25f), p(x.toFloat(), -2.5f), p(x.toFloat(), 2.5f), 1.dp.toPx(), pathEffect = effect)
        for (z in -2..2) drawLine(c.muted.copy(alpha = .25f), p(-2.3f, z.toFloat()), p(2.3f, z.toFloat()), 1.dp.toPx(), pathEffect = effect)
        drawLine(c.muted.copy(alpha = .6f), p(-2.5f, 0f), p(2.5f, 0f), 1.dp.toPx(), pathEffect = effect)
    }
}

/** 三个语义点使用真实模型的同一变换和投影；颜色对应下方文字，标签本身保持水平。 */
@Composable private fun AttitudeLandmarks(preset: VesselViewPreset, pose: VesselAttitudePose?, modifier: Modifier) {
    val c = LocalMetro.current
    Canvas(modifier) {
        fun p(x: Float, y: Float, z: Float): Offset {
            val a = pose?.transform(x, y, z) ?: floatArrayOf(x, y, z)
            return vesselScenePoint(preset, size.width / size.height, a[0], a[1], a[2])
                .let { Offset(it.x * size.width, it.y * size.height) }
        }
        val port = p(-.64f, .4f, -.1f)
        val starboard = p(.64f, .4f, -.1f)
        drawLine(c.fg.copy(alpha = .7f), port, starboard, 1.dp.toPx())
        drawCircle(Color(0xFFE66B65), 3.dp.toPx(), port)
        drawCircle(Color(0xFF6ABB8A), 3.dp.toPx(), starboard)
        drawCircle(c.accent, 3.dp.toPx(), p(0f, .4f, 2f))
    }
}

/** 原生资源失败时使用同一坐标的投影轮廓，仍显示准确双轴、固定水平线和全部入口。 */
@Composable private fun AttitudeVessel2D(
    preset: VesselViewPreset, pose: VesselAttitudePose?, state: VesselAttitudeDisplayState, modifier: Modifier,
) {
    val c = LocalMetro.current
    Canvas(modifier) {
        val alpha = when (state) {
            VesselAttitudeDisplayState.LIVE -> 1f
            VesselAttitudeDisplayState.LAST -> .52f
            VesselAttitudeDisplayState.REFERENCE -> .34f
        }
        fun p(x: Float, y: Float, z: Float): Offset {
            val a = pose?.transform(x, y, z) ?: floatArrayOf(x, y, z)
            return vesselScenePoint(preset, size.width / size.height, a[0], a[1], a[2])
                .let { Offset(it.x * size.width, it.y * size.height) }
        }
        fun polygon(points: List<Offset>, color: Color) {
            val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, color.copy(alpha = color.alpha * alpha))
            drawPath(path, c.fg.copy(alpha = alpha), style = Stroke(1.4.dp.toPx()))
        }
        polygon(listOf(p(0f, -.1f, .25f), p(0f, -1.2f, 0f), p(0f, -1.2f, -.55f), p(0f, -.1f, -.7f)), c.muted)
        polygon(listOf(p(-.5f, .3f, -1.9f), p(-.65f, .3f, -.3f), p(-.45f, .3f, 1f), p(0f, .3f, 2f),
            p(0f, -.25f, 1.1f), p(-.38f, -.35f, -1.65f)), c.accent.copy(alpha = .65f))
        polygon(listOf(p(.5f, .3f, -1.9f), p(.65f, .3f, -.3f), p(.45f, .3f, 1f), p(0f, .3f, 2f),
            p(0f, -.25f, 1.1f), p(.38f, -.35f, -1.65f)), c.accent.copy(alpha = .65f))
        polygon(listOf(p(0f, .3f, 2f), p(-.45f, .3f, 1f), p(-.65f, .3f, -.3f), p(-.5f, .3f, -1.9f),
            p(.5f, .3f, -1.9f), p(.65f, .3f, -.3f), p(.45f, .3f, 1f)), c.panel)
        polygon(listOf(p(-.28f, .4f, .3f), p(-.28f, .4f, -.7f), p(.28f, .4f, -.7f), p(.28f, .4f, .3f)), c.muted.copy(alpha = .3f))
        polygon(listOf(p(0f, 3.65f, 0f), p(0f, .8f, -1.5f), p(0f, .8f, 0f)), c.fg.copy(alpha = .16f))
        polygon(listOf(p(0f, 3.4f, .05f), p(0f, .55f, 1.75f), p(0f, .65f, .3f)), c.fg.copy(alpha = .12f))
        drawLine(c.fg.copy(alpha = alpha), p(0f, .3f, 0f), p(0f, 3.8f, 0f), 2.dp.toPx())
    }
}

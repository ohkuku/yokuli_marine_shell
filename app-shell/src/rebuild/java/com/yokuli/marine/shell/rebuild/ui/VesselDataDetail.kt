package com.yokuli.marine.shell.rebuild.ui

import android.net.Uri
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSnapshot
import com.yokuli.anchorwatch.domain.sonar.DepthReference
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.location.PhoneLocationPhase
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.scene.*
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 只订阅已存在的运行时读模型；相机和页面销毁不会创建或停止任何业务资源。 */
@Composable internal fun rememberVesselScene(os: OsStore): VesselSceneModel? {
    val services = os.marine?.services ?: return null
    val state by services.state.collectAsState()
    val connections by services.network.connections.collectAsState()
    val phone by services.sources.phoneLocationStatus.collectAsState()
    val now = rememberMarineClock()
    return remember(state.vesselData, state.vesselSettings, connections, phone,
        state.vesselMountCalibration, state.phoneSensorCapabilities, state.settings.gpsDataSource, now) {
        VesselScenePresenter.present(state.vesselData, state.vesselSettings, connections, phone,
            state.vesselMountCalibration, state.phoneSensorCapabilities, now, state.settings.gpsDataSource)
    }
}

internal fun vesselHotspotTitle(os: OsStore, id: VesselHotspot): String = when (id) {
    VesselHotspot.WIND -> os.t("风况", "wind")
    VesselHotspot.POSITION -> os.t("位置", "position")
    VesselHotspot.HEADING -> os.t("船首向", "heading")
    VesselHotspot.DEPTH -> os.t("水深", "depth")
}

internal fun vesselMetricValue(os: OsStore, metric: VesselSceneMetric): String =
    if (!metric.hasValue) "—" else sourceValueText(os, metric.id, metric.position ?: metric.number)

internal fun vesselStatusText(os: OsStore, metric: VesselSceneMetric): String = when (metric.status) {
    VesselSceneStatus.CURRENT -> os.t("正在更新", "updating")
    VesselSceneStatus.LAST_READING -> os.t("上次读数", "last reading")
    VesselSceneStatus.NEVER_RECEIVED -> os.t("尚未收到读数", "no reading received yet")
    VesselSceneStatus.NO_ADOPTED_READING -> os.t("已有读数，尚未采用", "readings available; none in use")
    VesselSceneStatus.PERMISSION_REQUIRED -> os.t("需要定位权限", "location permission needed")
    VesselSceneStatus.PROVIDER_DISABLED -> os.t("手机定位服务已关闭", "phone location service is off")
    VesselSceneStatus.CALIBRATION_REQUIRED -> os.t("需要对齐船艏", "bow alignment needed")
    VesselSceneStatus.SENSOR_MISSING -> os.t("手机没有此传感器", "sensor not provided by this phone")
    VesselSceneStatus.SOURCE_DISABLED -> os.t("来源已关闭", "source is off")
    VesselSceneStatus.LOW_QUALITY -> os.t("读数精度较低", "lower-quality reading")
    VesselSceneStatus.CONFLICT -> os.t("来源读数不一致", "sources disagree")
    VesselSceneStatus.INVALID -> os.t("读数未通过质量检查", "reading did not pass quality checks")
}

/** 视风与真风各自保存选中的字段，返回来源检查后仍停在同一份真实历史。 */
@Composable internal fun VesselWindDetail(os: OsStore, openSource: (VesselMetricId) -> Unit) {
    val scene = rememberVesselScene(os)
    if (scene == null) {
        PageBody { Label(os.t("船况服务正在连接", "connecting to boat data"), 20) }
        return
    }
    val history by os.hub.history.collectAsState()
    val current by os.hub.state.collectAsState()
    val enabled = LocalInternalAppInputEnabled.current
    val now = rememberMarineClock()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var apparentField by rememberSaveable { mutableStateOf(VesselMetricId.APPARENT_WIND_SPEED.name) }
    var trueField by rememberSaveable { mutableStateOf(VesselMetricId.TRUE_WIND_SPEED.name) }
    Pivot(listOf(os.t("视风", "apparent"), os.t("真风", "true")), initialPage = page, onPageSelected = { page = it }) { tab ->
        val fields = if (tab == 0) listOf(VesselMetricId.APPARENT_WIND_SPEED, VesselMetricId.APPARENT_WIND_ANGLE)
            else listOf(VesselMetricId.TRUE_WIND_SPEED, VesselMetricId.TRUE_WIND_ANGLE, VesselMetricId.TRUE_WIND_DIRECTION)
        val selected = VesselMetricId.valueOf(if (tab == 0) apparentField else trueField).takeIf { it in fields } ?: fields.first()
        val metric = scene.metric(selected)
        val c = LocalMetro.current
        PageBody {
            Label(if (tab == 0) os.t("船上感受到的风", "the wind felt aboard")
                else os.t("消除船速影响后的风", "wind with boat motion accounted for"), 17, c.muted)
            // 每行都是原生控件；字号放大时自然增加高度，选中态不依赖颜色。
            fields.forEach { field ->
                val value = scene.metric(field)
                val active = selected == field
                Row(Modifier.fillMaxWidth().heightIn(min = 62.dp)
                    .selectable(active, enabled = enabled, role = Role.RadioButton, onClick = {
                        if (tab == 0) apparentField = field.name else trueField = field.name
                    }).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(3.dp).height(40.dp).background(if (active) c.accent else c.muted.copy(alpha = .25f)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Label(sourceMetricName(os, field), 17, if (active) c.fg else c.muted)
                        Label(vesselMetricValue(os, value), 27, if (value.isCurrent) c.fg else c.muted)
                        Label(vesselStatusText(os, value), 13, c.muted)
                    }
                    if (active) Glyph("check", Modifier.size(18.dp), c.accent)
                }
            }
            Label(sourceMetricName(os, selected), 25, c.accent)
            VesselReadingFacts(os, metric)
            if (selected in windDirectionMetrics) VesselWindDirection(os, metric)
            val key = windTrendKey(selected)
            val samples = history[key].orEmpty()
            Label(os.t("最近的变化", "recent changes"), 24)
            if (samples.isEmpty()) {
                Label(os.t("最近 15 分钟没有这项读数的历史。收到真实样本后会显示趋势。",
                    "No history for this reading in the last 15 minutes. A trend appears as actual samples arrive."), 16, c.muted)
            } else {
                ReadingTrace(os, samples, key, now, current = current.readings[key])
            }
            MenuRow(os.t("检查此项来源", "check this reading's source"),
                os.t("查看采用、指定与备用来源", "inspect the source in use, your selection and alternatives")) { if(enabled) openSource(selected) }
        }
    }
}

/** 四个热点的真实指标摘要；其余指标复用原有运行时观测入口。 */
@Composable internal fun ColumnScope.VesselMetricSummary(os: OsStore, metric: VesselMetricId) {
    val scene = rememberVesselScene(os)
    val item = scene?.metrics?.get(metric)
    val c = LocalMetro.current
    if (item != null) {
        Label(vesselMetricValue(os, item), if (metric == VesselMetricId.POSITION) 27 else 42,
            if (item.isCurrent) c.fg else c.muted)
        VesselReadingFacts(os, item)
        if (metric == VesselMetricId.POSITION) {
            item.position?.horizontalAccuracyMeters?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                Label(os.t("定位精度约 ", "reported accuracy about ") + os.formatLength(it), 15, c.muted)
            }
        }
        if (metric in setOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC))
            Label(os.t("船首向表示船艏朝向；对地航向表示移动方向，两者分开使用。",
                "Heading is where the bow points; course over ground is the direction of travel. They remain separate."), 16, c.muted)
        return
    }
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val connections by services.network.connections.collectAsState()
    val observation = sourceObservation(metric, state.vesselData)
    val now = rememberMarineClock()
    val displayValue = when (metric) {
        VesselMetricId.MOTION_SCORE -> state.vesselData.motion.value?.score
        VesselMetricId.ROLL_PERIOD -> state.vesselData.motion.value?.dominantRollPeriodSeconds
        else -> observation?.value
    }
    Label(if (displayValue == null) "—" else sourceValueText(os, metric, displayValue), 36)
    observation?.receivedElapsedRealtime?.let { Label(readingAge(os, it, now), 15, c.muted) }
    observation?.sourceIdentity?.let { Label(sourceDisplayName(os, it, connections), 17, c.muted) }
    observation?.reference?.let { Label(vesselReferenceText(os, it, metric), 15, c.muted) }
    if (observation?.value == null) Label(os.t("尚未收到读数", "no reading received yet"), 17, c.muted)
    else if (observation.freshness != VesselDataFreshness.FRESH) Label(os.t("上次读数", "last reading"), 17, c.muted)
    if (observation?.quality != VesselDataQuality.GOOD && observation?.value != null)
        Label(os.t("请留意读数质量", "check reading quality"), 16, c.muted)
}

@Composable private fun ColumnScope.VesselReadingFacts(os: OsStore, metric: VesselSceneMetric) {
    val services = os.marine?.services ?: return
    val connections by services.network.connections.collectAsState()
    val now = rememberMarineClock()
    val c = LocalMetro.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(vesselStatusText(os, metric), 17, if (metric.isCurrent) c.accent else c.muted, Modifier.weight(1f))
        metric.receivedElapsedRealtime?.let { Label(readingAge(os, it, now), 14, c.muted, Modifier.widthIn(max = 150.dp)) }
    }
    if (metric.freshness != VesselDataFreshness.FRESH && metric.hasValue && metric.status != VesselSceneStatus.LAST_READING)
        Label(os.t("显示上次完整读数，尚未收到新的测量。", "Showing the last complete reading; no new measurement yet."), 15, c.muted)
    Label(vesselReferenceText(os, metric.reference, metric.id), 15, c.muted)
    val displayedSource = metric.source?.let { sourceDisplayName(os, it, connections) }
    if (displayedSource != null) {
        Label((if (metric.adoptedSource != null) os.t("采用 · ", "in use · ") else os.t("上次来自 · ", "last from · ")) + displayedSource, 17)
    } else if (!metric.hasValue) Label(os.t("当前没有采用的读数", "no reading is currently in use"), 16, c.muted)
    val selected = metric.requestedSourceKey
    if (selected != null) {
        val selectedName = metric.requestedSource?.let { sourceDisplayName(os, it, connections) }
            ?: os.t("已保存的来源", "saved source")
        Label(os.t("指定 · ", "selected · ") + selectedName, 15, c.muted)
        if (metric.selectedSourceUnavailable) Label(os.t("保留你的选择，等待该来源恢复；不会在这里自动切换。",
            "Your choice is retained while that source is unavailable; this page never switches sources automatically."), 15, c.muted)
    } else Label(when (metric.preference) {
        VesselSourcePreference.PHONE -> os.t("选择范围 · 手机", "source preference · phone")
        VesselSourcePreference.BOAT -> os.t("选择范围 · 船载", "source preference · boat")
        VesselSourcePreference.DERIVED -> os.t("选择范围 · 系统计算", "source preference · calculated")
        VesselSourcePreference.AUTO -> os.t("自动采用符合条件的来源", "automatically uses an eligible source")
    }, 14, c.muted)
    if (metric.connectionContinuesWithoutMeasurement) Label(os.t("连接仍在接收其他消息，这项读数没有更新。",
        "The connection is receiving other messages, but this reading has not updated."), 16, c.muted)
    metric.conflict?.let {
        Label(os.t("多个来源提供了不同的读数，请检查设备与安装方向。", "Sources provide different readings. Check the instruments and their alignment."), 16, c.muted)
    }
    if (metric.quality != VesselDataQuality.GOOD && metric.hasValue && metric.status != VesselSceneStatus.LOW_QUALITY)
        Label(os.t("读数质量受限", "reading quality is limited"), 14, c.muted)
    val provenance = metric.provenanceDetail
    if (provenance is VesselProvenance.Derived) {
        Label(os.t("由已采用的读数计算", "calculated from the readings in use"), 15, c.muted)
        provenance.inputs.distinctBy { it.id }.forEach { input ->
            Label("· " + sourceDisplayName(os, input, connections), 14, c.muted)
        }
    }
    // UTC 来自生产者，缺失时明确说明；不拿接收的单调时钟伪造设备采样时间。
    val captured = metric.observedAtUtcMillis?.takeIf { it > 0L }?.let { stamp ->
        remember(stamp, os.chinese) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM,
                if (os.chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(stamp))
        }
    }
    Label(if (captured != null) os.t("设备采样时间 · ", "device sample time · ") + captured
        else os.t("设备未提供采样时间；更新年龄按收到完整读数的时刻计算。",
            "No device sample time provided; update age uses receipt of the complete reading."), 13, c.muted)
    if (metric.id == VesselMetricId.DEPTH && metric.reference !is VesselReference.Depth)
        Label(os.t("尚不清楚水深从水面、探头还是龙骨起算，不用它推断龙骨下余量。",
            "It is not known whether depth is measured from the surface, transducer or keel; do not infer under-keel clearance."), 15, c.muted)
}

/** 业务动作只进入真实连接或安装页；当前应用的父路径继续由 Shell 保存。 */
@Composable internal fun ColumnScope.VesselSourceActions(os: OsStore, metric: VesselMetricId, openMounting: () -> Unit) {
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val connections by services.network.connections.collectAsState()
    val scene = rememberVesselScene(os)
    val item = scene?.metrics?.get(metric)
    val observation = sourceObservation(metric, state.vesselData)
    val phoneStatus by services.sources.phoneLocationStatus.collectAsState()
    val enabled = LocalInternalAppInputEnabled.current
    if(metric == VesselMetricId.POSITION && os.positionSource == "phone" &&
        phoneStatus.phase in setOf(PhoneLocationPhase.PERMISSION_REQUIRED,PhoneLocationPhase.PROVIDER_DISABLED)) {
        val permission = phoneStatus.phase == PhoneLocationPhase.PERMISSION_REQUIRED
        MenuRow(if(permission)os.t("管理定位权限","manage location permission") else os.t("开启系统定位","turn on device location"),
            os.t("恢复已经选择的手机来源，不更改船位选择。","Restore the selected phone source without changing position ownership.")) {
            if(enabled) runCatching {
                val intent = if(permission) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",os.context.packageName,null))
                    else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                os.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { os.notify("无法打开系统定位设置，请在 Android 设置中调整。","Could not open location settings. Adjust them in Android Settings.") }
        }
    }
    val currentSource = item?.source ?: observation?.sourceIdentity
    val inputs = (item?.provenanceDetail ?: observation?.provenanceDetail) as? VesselProvenance.Derived
    val candidates = state.vesselData.candidates[metric].orEmpty()
    val allIdentities = (listOfNotNull(currentSource, item?.requestedSource) + inputs?.inputs.orEmpty() + candidates.map { it.source })
        .distinctBy { it.id }
    val relevantConnectionIds = allIdentities.mapNotNull { it.transportProfileId }.toMutableSet().apply {
        if (metric == VesselMetricId.POSITION) state.vesselSettings.metricSourcePins["POSITION_CONNECTION"]?.let(::add)
    }
    val relevant = connections.filter { it.spec.id in relevantConnectionIds }
    val phoneHeading = metric in setOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC,
        VesselMetricId.DEVICE_HEADING_TRUE, VesselMetricId.DEVICE_HEADING_MAGNETIC, VesselMetricId.HEEL, VesselMetricId.PITCH)
    if (phoneHeading) MenuRow(os.t("确认手机安装与船艏", "confirm phone mounting and bow"),
        os.t("检查固定方向和船首向对齐", "check mounting orientation and heading alignment")) { if(enabled) openMounting() }
    if (relevant.isNotEmpty()) {
        Label(os.t("检查连接", "inspect connection"), 24)
        relevant.forEach { connection -> ConnectionAction(os, connection, currentSource, inputs) }
    } else if (allIdentities.none { it.sourceType == VesselSourceType.PHONE_SENSOR } &&
        metric !in derivedSourceMetrics) {
        val receivers = connections.filter { it.spec.receive }
        if (receivers.isNotEmpty()) {
            Label(os.t("检查输入设备", "inspect an input"), 24)
            receivers.forEach { connection -> ConnectionAction(os, connection, null, null) }
        }
    }
    if (connections.none { it.spec.receive } && allIdentities.none { it.sourceType == VesselSourceType.PHONE_SENSOR } &&
        metric !in derivedSourceMetrics) MenuRow(os.t("添加船载连接", "add a boat connection"),
        os.t("连接提供此读数的设备", "connect an instrument that provides this reading")) { if(enabled) os.openLinked("nmea:create") }
    if (relevantConnectionIds.any { id -> connections.none { it.spec.id == id } })
        Label(os.t("原连接已移除，已保存的来源选择仍保留。添加设备后可重新指定。",
            "The original connection was removed. The saved source selection is retained; reconnect an instrument to choose again."), 15, LocalMetro.current.muted)
}

@Composable private fun ConnectionAction(os: OsStore, connection: NmeaConnectionSnapshot,
    currentSource: VesselSourceIdentity?, inputs: VesselProvenance.Derived?) {
    val enabled = LocalInternalAppInputEnabled.current
    val role = when {
        currentSource?.transportProfileId == connection.spec.id -> os.t("此读数的连接", "connection for this reading")
        inputs?.inputs?.any { it.transportProfileId == connection.spec.id } == true -> os.t("计算所用输入", "input used in the calculation")
        else -> os.t("候选输入连接", "candidate input connection")
    }
    MenuRow(connection.spec.name, role) { if(enabled) os.openLinked("nmea:connection:${Uri.encode(connection.spec.id)}") }
}

internal fun vesselReferenceText(os: OsStore, reference: VesselReference?, metric: VesselMetricId? = null): String = when (reference) {
    VesselReference.TrueNorth -> os.t("以真北为参考", "referenced to true north")
    VesselReference.MagneticNorth -> os.t("以磁北为参考", "referenced to magnetic north")
    VesselReference.VesselRelative -> if (metric in windDirectionMetrics)
        os.t("相对船艏 · 风从这个方向吹来", "relative to the bow · wind comes from this direction")
        else os.t("相对船体", "vessel-referenced")
    VesselReference.WaterReferenced -> os.t("对水参考", "water-referenced")
    VesselReference.GroundReferenced -> os.t("对地参考", "ground-referenced")
    is VesselReference.Depth -> when (reference.reference) {
        DepthReference.BELOW_SURFACE -> os.t("从水面起算的水深", "depth below the surface")
        DepthReference.BELOW_TRANSDUCER -> os.t("从探头起算的水深", "depth below the transducer")
        DepthReference.BELOW_KEEL -> os.t("从龙骨起算的水深", "depth below the keel")
        DepthReference.UNKNOWN -> os.t("水深起算位置未知", "depth reference is unknown")
    }
    null -> when (metric) {
        VesselMetricId.POSITION -> os.t("WGS 84 坐标", "WGS 84 coordinates")
        VesselMetricId.APPARENT_WIND_SPEED -> os.t("视风速 · 船上测得", "apparent wind speed · measured aboard")
        VesselMetricId.TRUE_WIND_SPEED -> os.t("真风速 · 来源未说明对水或对地参考", "true wind speed · water or ground reference not supplied")
        VesselMetricId.DEPTH -> os.t("水深起算位置未知", "depth reference is unknown")
        else -> os.t("来源未提供参考基准", "reference not supplied by the source")
    }
}

/** 单一角度的解释图。未知参考不绘制方向；过期值只画明确的历史虚线，不播放动画。 */
@Composable private fun VesselWindDirection(os: OsStore, metric: VesselSceneMetric) {
    val angle = metric.number ?: return
    val reference = metric.reference
    if (reference !in setOf(VesselReference.VesselRelative, VesselReference.TrueNorth, VesselReference.MagneticNorth)) return
    val c = LocalMetro.current
    val bowRelative = reference == VesselReference.VesselRelative
    val caption = if (bowRelative) os.t("船艏朝上 · 风从箭头所在方向吹来", "bow up · wind comes from the arrow's direction")
        else os.t("北方朝上 · 风从箭头所在方向吹来", "north up · wind comes from the arrow's direction")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Label(if (bowRelative) os.t("船艏", "bow") else if (reference == VesselReference.TrueNorth)
            os.t("真北", "true north") else os.t("磁北", "magnetic north"), 13, c.muted)
        Canvas(Modifier.fillMaxWidth().height(170.dp).semantics {
            contentDescription = caption + " · " + vesselMetricValue(os, metric) + " · " + vesselStatusText(os, metric)
        }) {
            val radius = min(size.width, size.height) * .43f
            val radians = Math.toRadians(angle - 90.0)
            val unit = Offset(cos(radians).toFloat(), sin(radians).toFloat())
            val from = center + unit * radius
            val to = center + unit * (radius * .35f)
            val lineColor = if (metric.isCurrent) c.accent else c.muted
            drawCircle(c.muted.copy(alpha = .35f), radius, center, style = Stroke(1.dp.toPx()))
            drawLine(c.muted.copy(alpha = .25f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
            drawLine(lineColor, from, to, 2.dp.toPx(), pathEffect = if (metric.isCurrent) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            val tangent = Offset(-unit.y, unit.x)
            drawLine(lineColor, to, to + unit * 11.dp.toPx() + tangent * 5.dp.toPx(), 2.dp.toPx())
            drawLine(lineColor, to, to + unit * 11.dp.toPx() - tangent * 5.dp.toPx(), 2.dp.toPx())
            if (bowRelative) {
                val hull = Path().apply {
                    moveTo(center.x, center.y - 17.dp.toPx())
                    lineTo(center.x + 7.dp.toPx(), center.y + 11.dp.toPx())
                    lineTo(center.x - 7.dp.toPx(), center.y + 11.dp.toPx())
                    close()
                }
                drawPath(hull, c.fg, style = Stroke(1.dp.toPx()))
            } else drawCircle(c.fg, 2.dp.toPx(), center)
        }
        Label(caption, 13, c.muted)
        if (!metric.isCurrent) Label(os.t("虚线为上次读数", "dashed arrow is the last reading"), 13, c.muted)
    }
}

private val windDirectionMetrics = setOf(VesselMetricId.APPARENT_WIND_ANGLE, VesselMetricId.TRUE_WIND_ANGLE, VesselMetricId.TRUE_WIND_DIRECTION)
private fun windTrendKey(metric: VesselMetricId): String = when (metric) {
    VesselMetricId.APPARENT_WIND_SPEED -> "aws"
    VesselMetricId.APPARENT_WIND_ANGLE -> "awa"
    VesselMetricId.TRUE_WIND_SPEED -> "tws"
    VesselMetricId.TRUE_WIND_ANGLE -> "twa"
    VesselMetricId.TRUE_WIND_DIRECTION -> "twd"
    else -> error("Not a wind metric")
}

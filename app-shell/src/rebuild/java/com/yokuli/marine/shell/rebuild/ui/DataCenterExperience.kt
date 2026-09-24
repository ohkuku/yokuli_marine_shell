package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.runtime.contract.PositionSourceRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.scene.VesselHotspot
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselSourceType
import com.yokuli.anchorwatch.location.PhoneLocationPhase
import com.yokuli.anchorwatch.location.PhoneHeadingPresentationQuality
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState
import com.yokuli.anchorwatch.location.vessel.PhoneHeadingAlignmentPolicy
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** 数据中心拥有来源选择界面，实际配置仍唯一存放于 VesselSettingsRepository。
 * initialMetric 只描述入口：跨应用进入来源子页时，返回交给 Shell 还原调用者。
 */
@Composable fun DataCenterScreen(os: OsStore, initialMetric: String? = null) {
    val entry = remember(initialMetric) { when {
        initialMetric in listOf("mount", "wind", "readings") -> initialMetric!!
        initialMetric?.startsWith("source/") == true && runCatching { VesselMetricId.valueOf(initialMetric.substringAfter('/')) }.isSuccess -> initialMetric
        initialMetric != null && runCatching { VesselMetricId.valueOf(initialMetric.uppercase()) }.isSuccess -> initialMetric.uppercase()
        else -> "overview"
    } }
    // 路径只保存本次访问的展示层级；跨应用访问的调用者和实例仍由 Shell 持有。
    var path by rememberSaveable(initialMetric) { mutableStateOf(listOf(entry)) }
    var selectedHotspot by rememberSaveable { mutableStateOf<VesselHotspot?>(null) }
    val pageStates = rememberSaveableStateHolder()
    val directPhone = initialMetric == "phone"
    var visibleTab by rememberSaveable(initialMetric) { mutableIntStateOf(if(directPhone) 1 else 0) }
    val target = path.last()
    fun navigate(page: String) { if(page != target) path = path + page }
    ReportVisibleAppRoute(os, when {
        target != "overview" -> "data_center:$target"
        visibleTab == 1 -> "data_center:phone"
        else -> "data_center"
    })
    val hasInlineDetail = target == "overview" && visibleTab == 0 && selectedHotspot != null
    val back: () -> Unit = {
        if(path.size > 1) path = path.dropLast(1)
        else if(hasInlineDetail) selectedHotspot = null
        else os.shell.popRoute()
    }
    val interceptBack = path.size > 1 || hasInlineDetail
    AppBackHandler(interceptBack) { back() }
    fun pageTitle(page: String): String = when(page) {
        "mount" -> os.t("固定手机", "mount phone")
        "wind" -> os.t("风况", "wind")
        "readings" -> os.t("全部读数", "all readings")
        "overview" -> os.title(AppId.DATA_CENTER)
        else -> sourceMetricName(os, VesselMetricId.valueOf(page.substringAfter("source/")))
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, pageTitle(target), hasLocalBack = path.size > 1 || entry != "overview" || directPhone,
            localBackLabel = when {
                path.size > 1 -> os.t("返回 ", "back to ") + pageTitle(path[path.lastIndex-1])
                hasInlineDetail -> os.t("收起详情", "close detail")
                else -> null
            })
        AnimatedContent(path, transitionSpec = {
            val forward = targetState.size > initialState.size
            (slideInHorizontally(tween(220)) { if (forward) it / 5 else -it / 8 } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally(tween(180)) { if (forward) -it / 8 else it / 5 } + fadeOut(tween(140)))
        }, label = "source-page") { visitPath ->
            val page = visitPath.last()
            CompositionLocalProvider(LocalInternalAppInputEnabled provides (LocalInternalAppInputEnabled.current && visitPath == path)) {
            pageStates.SaveableStateProvider(page) { when {
                page == "mount" -> PageBody { PhoneMountSettings(os) }
                page == "wind" -> VesselWindDetail(os, openSource={navigate("source/${it.name}")})
                page == "readings" -> PageBody { SourceOverview(os) { navigate(it.name) } }
                page != "overview" -> PageBody {
                    val metric=VesselMetricId.valueOf(page.substringAfter("source/"))
                    VesselMetricSummary(os,metric)
                    SourceMetricDetail(os,metric,showObservation=false)
                    VesselSourceActions(os,metric,openMounting={navigate("mount")})
                }
                else -> Pivot(listOf(os.t("我的船", "my boat"), os.t("手机", "phone")), initialPage = visibleTab, onPageSelected = { visibleTab = it }) { tab ->
                    if(tab == 0) PageBody {
                        MyVesselOverview(os,selectedHotspot,
                            onSelect={selectedHotspot=it},
                            onDetail={if(it==VesselHotspot.WIND)navigate("wind")else navigate(when(it){
                                VesselHotspot.POSITION->VesselMetricId.POSITION.name
                                VesselHotspot.HEADING->preferredHeadingMetric(os).name
                                else->VesselMetricId.DEPTH.name
                            })},
                            openReadings={navigate("readings")},openMounting={navigate("mount")})
                    } else PageBody {
                        PhoneSourceSettings(os, openMounting = { navigate("mount") }, openPosition = { navigate(VesselMetricId.POSITION.name) })
                    }
                }
            } } }
        }
    }
}

/** 手机采集与发布是两件事。这里控制采集与安装，分享端只决定输出内容。 */
@Composable internal fun ColumnScope.PhoneSourceSettings(os: OsStore, openMounting: () -> Unit, openPosition: () -> Unit) {
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val location by services.sources.phoneLocationStatus.collectAsState()
    val readings by os.hub.state.collectAsState()
    val now = rememberMarineClock()
    val locked = state.active?.paused == false
    Label(os.t("手机提供什么", "what this phone provides"), 28)
    Toggle(os.t("手机定位", "phone location"), os.positionSource == "phone",
        if (os.positionSource == "nmea") os.t("当前使用船载船位，先在“位置”中关闭它。", "Boat position is selected; turn it off in Position first.")
        else os.t("开启时提供船位、对地航速与对地航向。", "Provides position, speed and course over ground while enabled."),
        enabled = !locked && os.positionSource in listOf("none", "phone")) { os.requestPosition(if (it) PositionSourceRequest.ENABLE_PHONE else PositionSourceRequest.DISABLE_POSITION) }
    if (os.positionSource == "nmea") MenuRow(os.t("选择船位来源", "choose position source"), os.t("全船使用同一份位置选择", "one position choice for all apps")) { openPosition() }
    if (locked) Label(os.t("守锚正在使用船位，暂停后可以更改。", "Anchor Watch is using position; pause before changing it."), 17, LocalMetro.current.muted)
    readings.phone?.let { fix -> Label(os.formatCoordinates(fix.point), 23); Label(readingAge(os, fix.elapsed, now), 16, LocalMetro.current.muted) }
    if (os.positionSource == "phone") Label(when (location.phase) {
        PhoneLocationPhase.PERMISSION_REQUIRED -> os.t("请允许精确定位", "allow precise location")
        PhoneLocationPhase.PROVIDER_DISABLED -> os.t("请开启 Android 定位服务", "turn on Android location")
        PhoneLocationPhase.ERROR -> os.t("定位暂停更新，保留上次位置与时间", "location updates paused; last position and time retained")
        else -> if (readings.phone == null) os.t("正在等待第一次定位", "waiting for the first position") else os.t("定位已启用", "location enabled")
    }, 17, LocalMetro.current.muted)
    Label(os.t("罗盘与姿态", "compass & motion"), 28)
    MenuRow(os.t("固定手机", "mount this phone"), when {
        state.vesselMountCalibration.bowAxis != DeviceBowAxis.TOP || state.vesselMountCalibration.headingReferenceVersion != 1 && state.vesselMountCalibration.headingAlignmentCompletedAt > 0 -> os.t("旧安装需要重新确认", "reconfirm the previous mount")
        state.vesselMountCalibration.headingAligned && state.vesselMountCalibration.mountConfirmed -> os.t("船首向和姿态已确认", "heading and attitude confirmed")
        state.vesselMountCalibration.headingAligned -> os.t("船首向已确认", "heading aligned")
        else -> os.t("顶部指向船艏，固定后校准", "top edge toward bow, then calibrate")
    }) { openMounting() }
    val capabilities = state.phoneSensorCapabilities
    listOf(
        os.t("罗盘", "compass") to capabilities.magnetometerAvailable,
        os.t("姿态", "attitude") to capabilities.attitudeAvailable,
        os.t("转动", "rotation") to capabilities.gyroAvailable,
        os.t("气压", "pressure") to capabilities.pressureAvailable,
    ).forEach { (label, available) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(label, 22)
            Label(if (available) os.t("可采集", "supported") else os.t("手机未提供", "not provided"), 17, LocalMetro.current.muted)
        }
    }
    val phoneCandidates = state.vesselData.candidates.values.flatten().filter { it.source.sourceType == VesselSourceType.PHONE_SENSOR }
    if (phoneCandidates.isNotEmpty()) {
        Label(os.t("最近采集", "last observed"), 28)
        phoneCandidates.distinctBy { it.metric }.sortedBy { it.metric.ordinal }.forEach { candidate ->
            Label(sourceMetricName(os, candidate.metric), 21)
            Label(sourceCandidateText(os, candidate.metric, candidate, now), 16, LocalMetro.current.muted)
        }
    }
    Label(os.t("这些读数进入同一个数据中心。要采用哪一个，在“读数”中选择；采集不会自动向其他设备发送。", "These readings enter the same Data Center. Choose their use under Readings; collecting them never automatically sends data to another device."), 17, LocalMetro.current.muted)
}

/** 中文：安装只有一种解释。船首向修正与重力测得的横倾、纵倾互相独立。 */
@Composable private fun ColumnScope.PhoneMountSettings(os: OsStore) {
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val now = rememberMarineClock()
    val mount = state.vesselMountCalibration
    val phone = state.phoneHeading
    val fresh = phone.receivedElapsedRealtime?.let { now - it in 0L..2_000L } == true
    val heading = phone.liveTrueHeadingDegrees ?: phone.liveMagneticHeadingDegrees
    val compassReady = fresh && heading != null && phone.presentationQuality in setOf(
        PhoneHeadingPresentationQuality.GOOD, PhoneHeadingPresentationQuality.LOW_ACCURACY)
    val frameSuspect = state.phoneVesselMountState == PhoneVesselMountState.MOUNT_SUSPECT
    val legacyMount = mount.bowAxis != DeviceBowAxis.TOP || mount.headingReferenceVersion != 1 && mount.headingAlignmentCompletedAt > 0
    val installed = mount.headingAligned && !frameSuspect && !legacyMount
    val nmeaTrue = state.nmeaInstruments.headingTrue?.takeIf { now - it.second in 0L..3_000L }?.first
    val nmeaMagnetic = state.nmeaInstruments.headingMagnetic?.takeIf { now - it.second in 0L..3_000L }?.first
    val match = if (compassReady) PhoneHeadingAlignmentPolicy.matchLiveReference(
        phone.liveTrueHeadingDegrees, phone.liveMagneticHeadingDegrees, nmeaTrue, nmeaMagnetic) else null
    var pending by remember { mutableStateOf(false) }
    var interrupted by remember { mutableStateOf(false) }
    var correcting by rememberSaveable { mutableStateOf(false) }
    var correction by rememberSaveable { mutableStateOf(String.format(Locale.US, "%.1f", mount.headingAlignmentOffsetDegrees)) }
    val correctionValue = correction.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
    val scope = rememberCoroutineScope()
    val enabled = LocalInternalAppInputEnabled.current && !pending
    // 已提交的运行时操作由服务持有；页面离开只释放等待，不丢失保存与失效操作。
    fun runCommand(action: () -> Job) {
        if (pending) return
        services.sources.clearVesselCalibrationFeedback()
        pending = true
        interrupted = false
        val command = action()
        scope.launch {
            command.join()
            interrupted = command.isCancelled
            pending = false
        }
    }
    LaunchedEffect(mount.headingAlignmentOffsetDegrees, correcting) {
        if (!correcting) correction = String.format(Locale.US, "%.1f", mount.headingAlignmentOffsetDegrees)
    }
    Label(os.t("顶部朝船艏，固定后校准", "point toward the bow, then calibrate"), 24)
    PhoneMountDiagram(os)
    Label(os.t("手机顶部指向船艏，屏幕朝上，与船体基准平面平行固定。斜放或竖装的支架不能代表船体姿态；移动手机或改变支架后，需要重新校准。",
        "Point the phone's physical top edge toward the bow. Fix it face up, parallel to the boat's reference plane. A tilted or upright mount cannot represent vessel attitude. Recalibrate after moving it."), 17, LocalMetro.current.muted)
    Label(when {
        legacyMount -> os.t("旧安装需重新确认：按图固定后校准。", "Previous mounting needs confirmation. Follow the diagram and recalibrate.")
        frameSuspect -> os.t("安装需要重新确认", "mounting needs reconfirmation")
        installed -> os.t("已固定 · 全船共用这份校准", "mounted · one calibration for all apps")
        else -> os.t("尚未确认固定", "mounting not yet confirmed")
    }, 18, if (installed) LocalMetro.current.accent else LocalMetro.current.fg)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f)) {
            Label(os.t("手机顶部方向", "phone top-edge direction"), 14, LocalMetro.current.muted)
            Label(os.formatBearing(heading), 30)
        }
        Column(Modifier.weight(1f)) {
            Label(os.t("校准后的船首向", "calibrated vessel heading"), 14, LocalMetro.current.muted)
            Label(os.formatBearing(heading?.takeIf { installed }?.plus(mount.headingAlignmentOffsetDegrees)), 30, LocalMetro.current.accent)
        }
    }
    Label(if (phone.liveTrueHeadingDegrees != null) os.t("真北 · 使用位置计算磁偏角", "true north · declination from position")
        else os.t("磁北 · 尚未取得真北参考", "magnetic north · no true-north reference yet"), 14, LocalMetro.current.muted)
    phone.receivedElapsedRealtime?.let { Label(readingAge(os, it, now), 14, LocalMetro.current.muted) }
    val quality = when {
        !fresh -> os.t("等待新的罗盘读数，保留上次方向与时间。", "Waiting for a new compass reading; the last direction and time remain visible.")
        phone.presentationQuality == PhoneHeadingPresentationQuality.DISTURBED -> os.t("罗盘受到干扰。远离磁铁、电线或扬声器，读数稳定后再校准。", "Compass interference detected. Move away from magnets, wiring or speakers before calibrating.")
        phone.presentationQuality == PhoneHeadingPresentationQuality.LOW_ACCURACY -> os.t("罗盘精度较低，可与船载罗盘核对。", "Compass accuracy is limited; compare with the boat's compass.")
        phone.presentationQuality == PhoneHeadingPresentationQuality.UNAVAILABLE -> os.t("手机尚未提供可校准的罗盘读数。", "The phone has not provided a compass reading suitable for calibration.")
        else -> null
    }
    quality?.let { Label(it, 15, LocalMetro.current.muted) }
    if (pending) MetroProgress(os.t("正在保存校准", "saving calibration"))
    MetroButton(if (installed) os.t("重新校准", "recalibrate") else os.t("确认已固定", "confirm mounted"),
        { runCommand { services.sources.confirmFixedPhoneMount() } }, primary = true,
        enabled = enabled && compassReady && state.activeTrip?.paused != true)
    if (state.activeTrip?.paused == true) Label(os.t("请先继续航行，再确认新的安装。", "Resume the voyage before confirming a new mount."), 15, LocalMetro.current.muted)
    Label(os.t("重新校准会清除船首向微调，不会把当前横倾或纵倾当作零，也不会切换数据来源。",
        "Recalibrating clears the heading correction. It preserves actual heel and pitch and keeps your source choices."), 14, LocalMetro.current.muted)
    if (!state.phoneSensorCapabilities.attitudeAvailable) Label(os.t("这部手机没有姿态传感器；可校准船首向，不能提供船体横倾与纵倾。",
        "This phone has no attitude sensor. It can provide calibrated heading, but not vessel heel or pitch."), 15, LocalMetro.current.muted)
    if (installed) {
        MenuRow(os.t("船首向微调", "fine-tune heading"), os.t("当前修正 ", "current correction ") + os.formatAngle(mount.headingAlignmentOffsetDegrees)) {
            if (enabled) correcting = !correcting
        }
        if (correcting) {
            Label(os.t("以独立罗盘为准。正值向右修正，负值向左修正；下面先预览，保存后全船应用。",
                "Compare with an independent compass. Positive turns right, negative turns left. Preview below, then save for all apps."), 15, LocalMetro.current.muted)
            Field(os.t("船首向修正（°）", "heading correction (°)"), correction, { correction = it }, number = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton("−0.5°", { correction = String.format(Locale.US, "%.1f", ((correctionValue ?: 0.0) - .5).coerceAtLeast(-180.0)) }, Modifier.weight(1f), enabled = enabled)
                MetroButton("+0.5°", { correction = String.format(Locale.US, "%.1f", ((correctionValue ?: 0.0) + .5).coerceAtMost(180.0)) }, Modifier.weight(1f), enabled = enabled)
            }
            Label(os.t("修正后 ", "after correction ") + os.formatBearing(heading?.let { h -> correctionValue?.let { h + it } }), 26, LocalMetro.current.accent)
            if (correctionValue == null) Label(os.t("请输入 −180° 到 180°。", "Enter a value from −180° to 180°."), 14, LocalMetro.current.muted)
            MetroButton(os.t("保存微调", "save correction"), {
                correctionValue?.let { value -> runCommand { services.sources.setPhoneHeadingAlignment(value) } }
            }, primary = true, enabled = enabled && compassReady && correctionValue != null)
            MetroButton(os.t("收起", "done"), { correcting = false }, enabled = enabled)
        }
        if (match != null) MetroButton(os.t("按船载罗盘校准", "match boat compass"),
            { correcting = false; runCommand { services.sources.alignPhoneHeadingToNmea() } }, enabled = enabled)
        if (mount.mountConfirmed) {
            val phoneAttitude = state.vesselData.candidates.values.flatten().filter { it.source.sourceType == VesselSourceType.PHONE_SENSOR }
            val heel = phoneAttitude.firstOrNull { it.metric == VesselMetricId.HEEL }
            val pitch = phoneAttitude.firstOrNull { it.metric == VesselMetricId.PITCH }
            Label(os.t("手机测得的船体姿态", "attitude from this phone"), 22)
            Label(os.t("横倾 ", "heel ") + os.formatAngle((heel?.value as? Number)?.toDouble()) + "   ·   " +
                os.t("纵倾 ", "pitch ") + os.formatAngle((pitch?.value as? Number)?.toDouble()), 22)
            listOfNotNull(heel?.receivedElapsedRealtime, pitch?.receivedElapsedRealtime).minOrNull()?.let { Label(readingAge(os, it, now), 14, LocalMetro.current.muted) }
            Label(os.t("根据重力测量真实倾斜；船首向微调不改变这两个读数。", "Measured against gravity; heading correction does not change these readings."), 14, LocalMetro.current.muted)
        }
        MetroButton(os.t("我移动了手机", "I moved the phone"), {
            correcting = false
            runCommand { services.sources.invalidateFixedPhoneMount() }
        }, enabled = enabled)
    }
    if (interrupted) Label(os.t("校准操作被中断，请检查当前状态后重试。", "Calibration was interrupted. Check the current state and retry."), 15, LocalMetro.current.muted)
    if (!pending) state.vesselCalibrationFeedback?.let { Label(phoneCalibrationFeedback(os, it), 15, LocalMetro.current.muted) }
}

/** 安装示意不使用传感器动画：顶部箭头始终表示用户应实际对齐的船艏。 */
@Composable private fun PhoneMountDiagram(os: OsStore) {
    val c = LocalMetro.current
    val description = os.t("俯视：手机顶部与船艏朝向相同，屏幕朝上固定。", "Top view: phone top edge and bow point the same way; mount the phone face up.")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(os.t("船艏", "bow"), 14, c.muted)
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
            val mid = size.width / 2
            val width = 46.dp.toPx()
            val y0 = 9.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val hull = Path().apply {
                moveTo(mid, y0)
                cubicTo(mid - width, y0 + 30.dp.toPx(), mid - width, bottom - 28.dp.toPx(), mid - width * .75f, bottom)
                lineTo(mid + width * .75f, bottom)
                cubicTo(mid + width, bottom - 28.dp.toPx(), mid + width, y0 + 30.dp.toPx(), mid, y0)
                close()
            }
            drawPath(hull, c.muted, style = Stroke(1.4.dp.toPx()))
            val phoneWidth = 29.dp.toPx()
            val top = 48.dp.toPx()
            drawRect(c.fg, Offset(mid - phoneWidth / 2, top), androidx.compose.ui.geometry.Size(phoneWidth, 65.dp.toPx()), style = Stroke(2.dp.toPx()))
            drawLine(c.accent, Offset(mid, top + 9.dp.toPx()), Offset(mid, y0 + 11.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(c.accent, Offset(mid, y0 + 11.dp.toPx()), Offset(mid - 5.dp.toPx(), y0 + 18.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(c.accent, Offset(mid, y0 + 11.dp.toPx()), Offset(mid + 5.dp.toPx(), y0 + 18.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(c.muted, Offset(mid - 6.dp.toPx(), top + 57.dp.toPx()), Offset(mid + 6.dp.toPx(), top + 57.dp.toPx()), 1.5.dp.toPx())
        }
    }
}

internal fun phoneCalibrationFeedback(os: OsStore, feedback: String): String = when (feedback) {
    "Phone mounting and bow alignment saved." -> os.t("已保存固定方式与船首向校准。", "Mounting and heading calibration saved.")
    "Heading correction saved. Attitude is unchanged." -> os.t("船首向微调已保存，横倾与纵倾保持不变。", "Heading correction saved; heel and pitch are unchanged.")
    "Phone moved. Confirm mounting again before using its vessel heading or attitude." -> os.t("已停止采用旧安装的船首向与姿态。固定后重新校准。", "Previous heading and attitude calibration retired. Mount the phone and recalibrate.")
    "Wait for a fresh, undisturbed phone compass reading." -> os.t("请等待新的、未受干扰的手机罗盘读数。", "Wait for a fresh compass reading without interference.")
    "Confirm the mount with a fresh compass reading before adjusting heading." -> os.t("请先固定并校准手机，罗盘更新后再微调。", "Mount and calibrate the phone first; fine-tune when the compass is updating.")
    "Enter a correction between -180 and 180 degrees." -> os.t("请输入 −180° 到 180° 的修正。", "Enter a correction from −180° to 180°.")
    "No rotation-vector sample is available on this phone." -> os.t("未收到新的姿态读数。保持此页打开，待传感器更新后重试。", "No fresh attitude reading. Keep this page open and retry after the sensor updates.")
    "Resume the trip before confirming a new attitude segment." -> os.t("请先继续航行，再确认新的安装。", "Resume the voyage before confirming a new mount.")
    "Phone calibration could not be saved. Check storage and try again." -> os.t("校准操作未完成。请检查当前校准状态与存储后重试。", "Calibration did not complete. Check the current calibration and storage, then retry.")
    "Trip attitude frame confirmed." -> os.t("姿态安装已保存，真实横倾与纵倾保持不变。", "Attitude mounting saved; actual heel and pitch are preserved.")
    else -> feedback
}

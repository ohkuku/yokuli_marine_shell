package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.runtime.contract.PositionSourceRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselSourceType
import com.yokuli.anchorwatch.location.PhoneLocationPhase
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.anchorwatch.location.vessel.PhoneHeadingAlignmentPolicy
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput

/** 数据中心拥有来源选择界面，实际配置仍唯一存放于 VesselSettingsRepository。
 * initialMetric 只描述入口：跨应用进入来源子页时，返回交给 Shell 还原调用者。
 */
@Composable fun DataCenterScreen(os: OsStore, initialMetric: String? = null) {
    val entryMetric = remember(initialMetric) { initialMetric?.let { runCatching { VesselMetricId.valueOf(it.uppercase()) }.getOrNull() } }
    var selected by rememberSaveable(initialMetric) { mutableStateOf(entryMetric) }
    var mounting by rememberSaveable { mutableStateOf(false) }
    val pageStates = rememberSaveableStateHolder()
    val directPhone = initialMetric == "phone"
    var visibleTab by rememberSaveable(initialMetric) { mutableIntStateOf(if(directPhone) 1 else 0) }
    ReportVisibleAppRoute(os, when {
        mounting -> "data_center:mount"
        selected != null -> "data_center:${selected!!.name}"
        visibleTab == 1 -> "data_center:phone"
        else -> "data_center"
    })
    val internalPage = mounting || selected != null
    val back: () -> Unit = {
        when {
            mounting -> mounting = false
            selected != null && entryMetric == null -> selected = null
            else -> os.shell.popRoute()
        }
    }
    // 深链首层返回由 Shell 消费；从数据中心本身展开的子页只退回本应用。
    val interceptBack = mounting || selected != null && entryMetric == null
    BindInternalAppInputHandler { input -> if (input == ShellInput.BACK && interceptBack) { back(); true } else false }
    AppBackHandler(interceptBack) { back() }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, when {
            mounting -> os.t("固定手机", "mount phone")
            selected != null -> sourceMetricName(os, selected!!)
            else -> os.title(AppId.DATA_CENTER)
        }, onBack = if (internalPage || directPhone) back else null)
        val target = if (mounting) "mount" else selected?.name ?: "overview"
        AnimatedContent(target, transitionSpec = {
            val forward = targetState != "overview" && targetState != entryMetric?.name
            (slideInHorizontally(tween(220)) { if (forward) it / 5 else -it / 8 } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally(tween(180)) { if (forward) -it / 8 else it / 5 } + fadeOut(tween(140)))
        }, label = "source-page") { page ->
            pageStates.SaveableStateProvider(page) { when {
                page == "mount" -> PageBody { PhoneMountSettings(os) }
                page != "overview" -> PageBody { SourceMetricDetail(os, VesselMetricId.valueOf(page)) }
                else -> Pivot(listOf(os.t("读数", "readings"), os.t("手机", "phone")), initialPage = if (directPhone) 1 else 0, onPageSelected = { visibleTab = it }) { tab ->
                    PageBody {
                        if (tab == 0) SourceOverview(os) { selected = it }
                        else PhoneSourceSettings(os, openMounting = { mounting = true }, openPosition = { selected = VesselMetricId.POSITION })
                    }
                }
            } }
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
        state.vesselMountCalibration.headingAligned && state.vesselMountCalibration.mountConfirmed -> os.t("船首向和姿态已确认", "heading and attitude confirmed")
        state.vesselMountCalibration.headingAligned -> os.t("船首向已确认 · 姿态待安装", "heading aligned · attitude setup needed")
        else -> os.t("对齐船艏，确认安装方向", "align with the bow and confirm mounting direction")
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

@Composable private fun ColumnScope.PhoneMountSettings(os: OsStore) {
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val now = rememberMarineClock()
    var axis by rememberSaveable { mutableStateOf(state.vesselMountCalibration.bowAxis) }
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(state.vesselCalibrationFeedback) { if (confirming && state.vesselCalibrationFeedback != null) confirming = false }
    val phone = state.phoneHeading
    val fresh = phone.receivedElapsedRealtime?.let { now - it in 0L..2_000L } == true
    val heading = (phone.liveTrueHeadingDegrees ?: phone.liveMagneticHeadingDegrees)?.takeIf { fresh }
    val nmeaTrue = state.nmeaInstruments.headingTrue?.takeIf { now - it.second in 0L..3_000L }?.first
    val nmeaMagnetic = state.nmeaInstruments.headingMagnetic?.takeIf { now - it.second in 0L..3_000L }?.first
    val match = if (fresh) PhoneHeadingAlignmentPolicy.matchLiveReference(phone.liveTrueHeadingDegrees, phone.liveMagneticHeadingDegrees, nmeaTrue, nmeaMagnetic) else null
    Label(os.t("先让手机成为船的一部分", "make the phone part of the boat"), 28)
    Label(os.t("将手机固定，顶部朝向船艏。再次移动手机后，需要重新确认。", "Mount the phone with its top edge toward the bow. Confirm again after moving it."), 19, LocalMetro.current.muted)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) { Label(os.t("手机方向", "phone direction"), 14, LocalMetro.current.muted); Label(os.formatBearing(heading), 36) }
        Column(Modifier.weight(1f)) { Label(os.t("船首向", "vessel heading"), 14, LocalMetro.current.muted); Label(os.formatBearing(heading?.takeIf { state.vesselMountCalibration.headingAligned }?.plus(state.vesselMountCalibration.headingAlignmentOffsetDegrees)), 36, LocalMetro.current.accent) }
    }
    Label(if (phone.liveTrueHeadingDegrees != null) os.t("真北方向", "true north") else os.t("磁北方向", "magnetic north"), 16, LocalMetro.current.muted)
    MetroButton(os.t("确认顶部朝向船艏", "align top edge with bow"), { services.sources.alignPhoneHeadingToBow() }, primary = true, enabled = heading != null)
    if (match != null) MetroButton(os.t("匹配船载罗盘", "match boat compass"), { services.sources.alignPhoneHeadingToNmea() })
    Label(os.t("船体姿态", "vessel attitude"), 30)
    Label(os.t("手机平面与船体平行。选择朝向船艏的边缘，当前横倾不会被当成零。", "Keep the phone plane parallel to the boat. Choose the edge facing the bow; the boat's current heel will be preserved."), 18, LocalMetro.current.muted)
    DeviceBowAxis.entries.forEach { value ->
        ChoiceRow(when (value) { DeviceBowAxis.TOP -> os.t("顶部", "top"); DeviceBowAxis.BOTTOM -> os.t("底部", "bottom"); DeviceBowAxis.LEFT -> os.t("左侧", "left"); DeviceBowAxis.RIGHT -> os.t("右侧", "right") }, axis == value) { axis = value }
    }
    if (confirming) MetroProgress(os.t("正在读取安装方向", "reading mounting direction"))
    MetroButton(os.t("确认安装方向", "confirm mounting direction"), { services.sources.clearVesselCalibrationFeedback(); confirming = true; services.sources.confirmTripAttitudeFrame(axis) }, primary = true,
        enabled = !confirming && state.activeTrip?.paused != true && state.phoneSensorCapabilities.attitudeAvailable)
    if (state.vesselMountCalibration.mountConfirmed) Label(os.t("姿态安装已确认", "attitude mounting confirmed"), 19, LocalMetro.current.accent)
    if (!confirming && state.vesselCalibrationFeedback == "No rotation-vector sample is available on this phone.") Label(os.t("没有收到姿态读数，请检查手机是否支持姿态传感器。", "No attitude reading received. Check this phone's sensor support."), 17, LocalMetro.current.muted)
    if (state.vesselMountCalibration.mountConfirmed) MetroButton(os.t("停止采用手机姿态", "stop using phone attitude"), { services.voyages.pauseTripAttitude() })
}

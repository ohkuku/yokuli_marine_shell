package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.runtime.contract.PositionSourceRequest
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.scene.VesselHotspot
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselSourceType
import com.yokuli.anchorwatch.location.PhoneLocationPhase
import com.yokuli.anchorwatch.location.PhoneHeadingPresentationQuality
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
        AppPageTransition(path, pageKey = { it.last() }, pageDepth = { it.size }, modifier = Modifier.weight(1f)) { visitPath ->
            val page = visitPath.last()
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
    AppSection(os.t("手机提供什么", "what this phone provides"))
    Toggle(os.t("手机定位", "phone location"), os.positionSource == "phone",
        if (os.positionSource == "nmea") os.t("明确改用手机；定位就绪后替换当前船位来源。", "Use this phone instead; replace the source once a position is ready.")
        else os.t("开启时提供船位、对地航速与对地航向。", "Provides position, speed and course over ground while enabled."),
        enabled = !locked && !location.selectionPending) { os.requestPosition(if (it) PositionSourceRequest.ENABLE_PHONE else PositionSourceRequest.DISABLE_POSITION) }
    if(location.selectionPending)MetroProgress(os.t("等待手机船位 · 当前来源仍在使用","waiting for phone position · current source remains active"))
    if (os.positionSource == "nmea") MenuRow(os.t("选择船位来源", "choose position source"), os.t("全船使用同一份位置选择", "one position choice for all apps")) { openPosition() }
    if (locked) Label(os.t("守锚正在使用船位，暂停后可以更改。", "Anchor Watch is using position; pause before changing it."), 15, LocalMetro.current.muted)
    readings.phone?.let { fix -> Label(os.formatCoordinates(fix.point), 23); Label(readingAge(os, fix.elapsed, now), 16, LocalMetro.current.muted) }
    if (os.positionSource == "phone") Label(when (location.phase) {
        PhoneLocationPhase.PERMISSION_REQUIRED -> os.t("请允许精确定位", "allow precise location")
        PhoneLocationPhase.PROVIDER_DISABLED -> os.t("请开启 Android 定位服务", "turn on Android location")
        PhoneLocationPhase.ERROR -> os.t("定位暂停更新，保留上次位置与时间", "location updates paused; last position and time retained")
        else -> if (readings.phone == null) os.t("正在等待第一次定位", "waiting for the first position") else os.t("定位已启用", "location enabled")
    }, 17, LocalMetro.current.muted)
    AppSection(os.t("罗盘与姿态", "compass & motion"))
    MenuRow(os.t("固定手机", "mount this phone"), when {
        state.vesselMountCalibration.calibratedAt > 0 && state.vesselMountCalibration.attitudeFrameVersion != 3 -> os.t("旧安装需要重新确认", "reconfirm the previous mount")
        state.vesselMountCalibration.headingAligned && state.vesselMountCalibration.mountConfirmed -> os.t("船首向和姿态已确认", "heading and attitude confirmed")
        state.vesselMountCalibration.headingAligned -> os.t("船首向已确认", "heading aligned")
        else -> os.t("固定到支架，将当前位置设为零点", "secure it in its mount, then set zero")
    }) { openMounting() }
    val capabilities = state.phoneSensorCapabilities
    listOf(
        os.t("罗盘", "compass") to capabilities.magnetometerAvailable,
        os.t("姿态", "attitude") to capabilities.attitudeAvailable,
        os.t("转动", "rotation") to capabilities.gyroAvailable,
        os.t("气压", "pressure") to capabilities.pressureAvailable,
    ).forEach { (label, available) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(label, 15)
            Label(if (available) os.t("可采集", "supported") else os.t("手机未提供", "not provided"), 17, LocalMetro.current.muted)
        }
    }
    val phoneCandidates = state.vesselData.candidates.values.flatten().filter { it.source.sourceType == VesselSourceType.PHONE_SENSOR }
    if (phoneCandidates.isNotEmpty()) {
        AppSection(os.t("最近采集", "last observed"))
        phoneCandidates.distinctBy { it.metric }.sortedBy { it.metric.ordinal }.forEach { candidate ->
            Label(sourceMetricName(os, candidate.metric), 15)
            Label(sourceCandidateText(os, candidate.metric, candidate, now), 16, LocalMetro.current.muted)
        }
    }
    Label(os.t("这些读数进入同一个数据中心。要采用哪一个，在“读数”中选择；采集不会自动向其他设备发送。", "These readings enter the same Data Center. Choose their use under Readings; collecting them never automatically sends data to another device."), 15, LocalMetro.current.muted)
}

/** 中文：当前固定安装是用户确认的零点；艏向、横倾、纵倾分别可微调。 */
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
    val legacyMount = mount.calibratedAt > 0 && mount.attitudeFrameVersion != 3
    val installed = mount.headingAligned && !frameSuspect && !legacyMount
    val nmeaTrue = state.nmeaInstruments.headingTrue?.takeIf { now - it.second in 0L..3_000L }?.first
    val nmeaMagnetic = state.nmeaInstruments.headingMagnetic?.takeIf { now - it.second in 0L..3_000L }?.first
    val match = if (compassReady) PhoneHeadingAlignmentPolicy.matchLiveReference(
        phone.liveVesselTrueHeadingDegrees, phone.liveVesselMagneticHeadingDegrees, nmeaTrue, nmeaMagnetic) else null
    var pending by remember { mutableStateOf(false) }
    var interrupted by remember { mutableStateOf(false) }
    var correcting by rememberSaveable { mutableStateOf(false) }
    var correctingAttitude by rememberSaveable { mutableStateOf(false) }
    var heelCorrection by rememberSaveable { mutableStateOf(String.format(Locale.US,"%.1f",mount.heelOffsetDegrees)) }
    var pitchCorrection by rememberSaveable { mutableStateOf(String.format(Locale.US,"%.1f",mount.pitchOffsetDegrees)) }
    fun angle(text:String)=text.trim().replace(',','.').toDoubleOrNull()?.takeIf{it.isFinite()&&it in -45.0..45.0}
    val heelValue=angle(heelCorrection);val pitchValue=angle(pitchCorrection)
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
    LaunchedEffect(mount.heelOffsetDegrees,mount.pitchOffsetDegrees,correctingAttitude) {
        if(!correctingAttitude){heelCorrection=String.format(Locale.US,"%.1f",mount.heelOffsetDegrees);pitchCorrection=String.format(Locale.US,"%.1f",mount.pitchOffsetDegrees)}
    }
    AppSection(os.t("固定好，就以现在为零点", "secure the phone, then set zero"))
    Label(os.t("手机可以斜装或竖装，不必放平。固定在实际使用的支架上，确认后把现在的姿态记为横倾、纵倾零点。船本身已有倾角时，可在下面补上这个偏差。",
        "A tilted or upright mount is fine. Secure the phone where you use it, then confirm this pose as heel and pitch zero. If the boat is already inclined, add that offset below."),15,LocalMetro.current.muted)
    Label(os.t("尽量让手机顶部朝向船艏；竖装时以屏幕朝向作为初始艏向，再与船载罗盘核对并微调。移动手机或支架后重新设零点。",
        "Point the top toward the bow where possible. An upright mount uses the screen-facing direction initially; compare with the boat compass and fine-tune. Set zero again after moving the phone or mount."),14,LocalMetro.current.muted)
    Label(when {
        legacyMount -> os.t("旧安装需重新确认：重新确认当前位置为零点。", "Previous mounting needs confirmation. Confirm the current installation as zero.")
        frameSuspect -> os.t("安装需要重新确认", "mounting needs reconfirmation")
        installed -> os.t("已固定 · 全船共用这份校准", "mounted · one calibration for all apps")
        else -> os.t("尚未确认固定", "mounting not yet confirmed")
    }, 18, if (installed) LocalMetro.current.accentText else LocalMetro.current.fg)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f)) {
            Label(os.t("手机顶部方向", "phone top-edge direction"), 14, LocalMetro.current.muted)
            Label(os.formatBearing(heading), 30)
        }
        Column(Modifier.weight(1f)) {
            Label(os.t("校准后的船首向", "calibrated vessel heading"), 14, LocalMetro.current.muted)
            Label(os.formatBearing((phone.liveVesselTrueHeadingDegrees ?: phone.liveVesselMagneticHeadingDegrees)?.takeIf { installed }), 30, LocalMetro.current.accent)
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
    MetroButton(if (installed) os.t("以现在重新设零点", "set zero here again") else os.t("已固定，设为零点", "mounted · set zero"),
        { runCommand { services.sources.confirmFixedPhoneMount() } }, primary = true,
        enabled = enabled && compassReady && state.phoneSensorCapabilities.attitudeAvailable)
    Label(os.t("设零点会清除三项微调；数据来源保持不变，暂停中的记录不会被自动继续。",
        "Setting zero clears all three corrections. Source choices stay unchanged; paused recording stays paused."),14,LocalMetro.current.muted)
    if (!state.phoneSensorCapabilities.attitudeAvailable) Label(os.t("这部手机没有可用的姿态传感器，无法建立固定安装零点。",
        "This phone has no attitude sensor for a fixed installation reference."), 15, LocalMetro.current.muted)
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
            Label(os.t("修正后 ", "after correction ") + os.formatBearing((phone.liveVesselTrueHeadingDegrees ?: phone.liveVesselMagneticHeadingDegrees)?.let { h -> correctionValue?.let { h - mount.headingAlignmentOffsetDegrees + it } }), 26, LocalMetro.current.accent)
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
            AppSection(os.t("手机测得的船体姿态", "attitude from this phone"))
            Label(os.t("横倾 ", "heel ") + os.formatAngle((heel?.value as? Number)?.toDouble()) + "   ·   " +
                os.t("纵倾 ", "pitch ") + os.formatAngle((pitch?.value as? Number)?.toDouble()), 22)
            listOfNotNull(heel?.receivedElapsedRealtime, pitch?.receivedElapsedRealtime).minOrNull()?.let { Label(readingAge(os, it, now), 14, LocalMetro.current.muted) }
            Label(os.t("相对已确认安装零点的倾角，加上你保存的偏差。", "Angles relative to the confirmed installation, plus your saved corrections."),14,LocalMetro.current.muted)
            MenuRow(os.t("左右与前后倾角微调","fine-tune heel & pitch"),os.t("修正 ","correction ")+os.formatAngle(mount.heelOffsetDegrees)+" · "+os.formatAngle(mount.pitchOffsetDegrees)){if(enabled)correctingAttitude=!correctingAttitude}
            if(correctingAttitude) {
                Field(os.t("横倾修正（°）","heel correction (°)"),heelCorrection,{heelCorrection=it},number=true)
                Label(os.t("正值：右舷下沉；负值：左舷下沉。","Positive: starboard down; negative: port down."),12,LocalMetro.current.muted)
                Field(os.t("纵倾修正（°）","pitch correction (°)"),pitchCorrection,{pitchCorrection=it},number=true)
                Label(os.t("正值：船艏抬起；负值：船艏压下。","Positive: bow up; negative: bow down."),12,LocalMetro.current.muted)
                if(heelValue==null||pitchValue==null) Label(os.t("请输入 −45° 到 45°。","Enter values from −45° to 45°."),14,LocalMetro.current.muted)
                val previewHeel=(heel?.value as? Number)?.toDouble()?.let{value->heelValue?.let{value-mount.heelOffsetDegrees+it}}
                val previewPitch=(pitch?.value as? Number)?.toDouble()?.let{value->pitchValue?.let{value-mount.pitchOffsetDegrees+it}}
                Label(os.t("预览 ","preview ")+os.formatAngle(previewHeel)+" · "+os.formatAngle(previewPitch),22)
                MetroButton(os.t("保存倾角微调","save attitude corrections"),{if(heelValue!=null&&pitchValue!=null)runCommand{services.sources.setPhoneAttitudeAlignment(heelValue,pitchValue)}},primary=true,enabled=enabled&&heelValue!=null&&pitchValue!=null)
                MetroButton(os.t("收起","done"),{correctingAttitude=false},enabled=enabled)
            }
        }
        MetroButton(os.t("我移动了手机", "I moved the phone"), {
            correcting = false
            runCommand { services.sources.invalidateFixedPhoneMount() }
        }, enabled = enabled)
    }
    if (interrupted) Label(os.t("校准操作被中断，请检查当前状态后重试。", "Calibration was interrupted. Check the current state and retry."), 15, LocalMetro.current.muted)
    if (!pending) state.vesselCalibrationFeedback?.let { Label(phoneCalibrationFeedback(os, it), 15, LocalMetro.current.muted) }
}

internal fun phoneCalibrationFeedback(os: OsStore, feedback: String): String = when (feedback) {
    "Phone mounting and bow alignment saved." -> os.t("已把当前固定安装设为零点，船首向已校准。", "Current installation saved as zero; heading calibrated.")
    "Attitude correction saved." -> os.t("倾角微调已保存，全船应用。","Attitude corrections saved for all apps.")
    "Enter attitude corrections between -45 and 45 degrees." -> os.t("倾角微调须在 −45° 到 45°。","Attitude corrections must be between −45° and 45°.")
    "Confirm the fixed installation first." -> os.t("先固定手机并确认当前零点。","Secure the phone and confirm zero first.")
    "Heading correction saved. Attitude is unchanged." -> os.t("船首向微调已保存，横倾与纵倾保持不变。", "Heading correction saved; heel and pitch are unchanged.")
    "Phone moved. Confirm mounting again before using its vessel heading or attitude." -> os.t("已停止采用旧安装的船首向与姿态。固定后重新校准。", "Previous heading and attitude calibration retired. Mount the phone and recalibrate.")
    "Wait for a fresh, undisturbed phone compass reading." -> os.t("请等待新的、未受干扰的手机罗盘读数。", "Wait for a fresh compass reading without interference.")
    "Confirm the mount with a fresh compass reading before adjusting heading." -> os.t("请先固定并校准手机，罗盘更新后再微调。", "Mount and calibrate the phone first; fine-tune when the compass is updating.")
    "Enter a correction between -180 and 180 degrees." -> os.t("请输入 −180° 到 180° 的修正。", "Enter a correction from −180° to 180°.")
    "No rotation-vector sample is available on this phone." -> os.t("未收到新的姿态读数。保持此页打开，待传感器更新后重试。", "No fresh attitude reading. Keep this page open and retry after the sensor updates.")
    "Resume the trip before confirming a new attitude segment." -> os.t("请先继续航行，再确认新的安装。", "Resume the voyage before confirming a new mount.")
    "Phone calibration could not be saved. Check storage and try again." -> os.t("校准操作未完成。请检查当前校准状态与存储后重试。", "Calibration did not complete. Check the current calibration and storage, then retry.")
    "Trip attitude frame confirmed." -> os.t("姿态安装零点已保存。", "Attitude installation reference saved.")
    else -> feedback
}

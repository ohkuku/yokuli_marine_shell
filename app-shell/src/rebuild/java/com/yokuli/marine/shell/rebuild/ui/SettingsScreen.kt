package com.yokuli.marine.shell.rebuild.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.*
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.model.AlarmSound
import com.yokuli.anchorwatch.domain.model.AlarmType
import com.yokuli.anchorwatch.domain.model.AlarmState
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.data.vessel.anyEnabled
import com.yokuli.anchorwatch.location.PhoneLocationPhase

@Composable fun SettingsScreen(os: OsStore, initialSection: String = "overview") {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection.substringBefore(':')) }
    var reset by remember { mutableStateOf(false) }
    val c = LocalMetro.current
    val back = { if(initialSection!="overview") os.shell.popRoute() else section = "overview" }
    BindInternalAppInputHandler { input -> if (input == ShellInput.BACK && section != "overview") { back(); true } else false }
    fun title(key: String) = when (key) {
        "appearance" -> os.t("外观与显示", "appearance & display")
        "language" -> os.t("语言", "language")
        "units" -> os.t("单位与坐标", "units & coordinates")
        "start" -> os.t("开始屏幕", "Start screen")
        "vessel" -> os.t("我的船", "my boat")
        "permissions" -> os.t("权限与后台", "permissions & background")
        "sound" -> os.t("声音与警报", "sound & alarms")
        "backup" -> os.t("备份与恢复", "backup & restore")
        "about" -> os.t("关于", "about")
        else -> os.t("设置", "settings")
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, title(section), onBack = if(section=="overview") null else back)
        when (section) {
            "vessel" -> VesselProfileSettings(os)
            "permissions" -> SystemAccessSettings(os)
            "sound" -> SystemSoundSettings(os)
            "backup" -> SystemBackupSettings(os)
            "tiles" -> TileLibraryScreen(os, initialSection.substringAfter("tiles:", "").takeIf {it.isNotBlank()})
            else -> PageBody {
                when (section) {
                    "overview" -> {
                        Label(os.t("系统", "system"), 17, c.accent)
                        listOf("appearance", "language", "units", "sound", "permissions").forEach { key ->
                            MenuRow(title(key), when(key) {
                                "language" -> if(os.chinese) "简体中文" else "English"
                                "units" -> if(os.measurementUnits==MeasurementUnitSystem.NAUTICAL) os.t("海里 · 节 · ", "nm · kn · ")+os.coordinateFormat else "km · km/h · ${os.coordinateFormat}"
                                else -> null
                            }) {section=key}
                        }
                        Label(os.t("个人偏好", "personal"), 17, c.accent)
                        MenuRow(title("vessel")) {section="vessel"}
                        MenuRow(title("start")) {section="start"}
                        MenuRow(os.t("应用磁贴", "app tiles"), os.t("按应用预览样式与实时内容", "preview styles and live content by app")) {os.open("tiles")}
                        Label(os.t("资料与系统信息", "data & information"), 17, c.accent)
                        MenuRow(title("backup")) {section="backup"}
                        MenuRow(title("about"), BuildConfig.VERSION_NAME) {section="about"}
                    }
                    "appearance" -> {
                        Label(os.t("主题色", "accent colour"), 26)
                        WpAccent.entries.chunked(4).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            row.forEach { accent -> Box(Modifier.size(58.dp).background(androidx.compose.ui.graphics.Color(accent.argb))
                                .then(if(os.accent==accent.argb) Modifier.border(3.dp,c.fg) else Modifier)
                                .clickable {os.shell.updateSystemPreferences {it.copy(accentName=accent.name)}}) {
                                if(os.accent==accent.argb) Glyph("check",Modifier.size(25.dp).align(androidx.compose.ui.Alignment.Center),androidx.compose.ui.graphics.Color.White)
                            } }
                        } }
                        ChoiceRow(os.t("深色背景", "dark background"), !os.light) {os.shell.updateSystemPreferences {it.copy(themeModeName="DARK")}}
                        ChoiceRow(os.t("浅色背景", "light background"), os.light) {os.shell.updateSystemPreferences {it.copy(themeModeName="LIGHT")}}
                        Toggle(os.t("保持屏幕常亮", "keep screen awake"),os.keepAwake,os.t("仅在 Yokuli OS 位于前台时", "while Yokuli OS is in front")) {enabled->
                            os.shell.updateSystemPreferences {it.copy(appPreferenceValues=it.appPreferenceValues+("preferences.display.keep_awake" to AppPreferenceRegistry.encode(AppPreferenceValue.Toggle(enabled))))}
                        }
                        Label(os.t("文字大小", "text size"),26)
                        listOf("COMPACT" to os.t("紧凑", "compact"),"STANDARD" to os.t("标准", "standard"),"LARGE" to os.t("较大", "larger")).forEach {(value,label)->
                            ChoiceRow(label,os.textSize==value) {os.shell.updateSystemPreferences {it.copy(appPreferenceValues=it.appPreferenceValues+("preferences.display.text_size" to "c:$value"))}}
                        }
                        Label(os.t("转场、触控倾斜和动态磁贴使用统一动效。", "Transitions, touch tilt and live tiles share one motion language."),17,c.muted)
                    }
                    "language" -> {
                        ChoiceRow("简体中文",os.chinese) {os.shell.updateSystemPreferences {it.copy(languageTag="zh-CN")}}
                        ChoiceRow("English",!os.chinese) {os.shell.updateSystemPreferences {it.copy(languageTag="en")}}
                    }
                    "units" -> {
                        Label(os.t("距离与速度", "distance & speed"),25)
                        MeasurementUnitSystem.entries.forEach {units->
                            ChoiceRow(if(units==MeasurementUnitSystem.NAUTICAL) os.t("海里 · 节", "nautical miles · knots") else os.t("公里 · 公里/小时", "kilometres · km/h"),os.measurementUnits==units) {
                                os.shell.updateSystemPreferences {it.copy(measurementUnitSystemName=units.name)}
                            }
                        }
                        Label(os.t("坐标格式", "coordinate format"),25)
                        listOf("DMM" to os.t("度与分", "degrees & minutes"),"DD" to os.t("十进制度", "decimal degrees"),"DMS" to os.t("度、分与秒", "degrees, minutes & seconds")).forEach {(format,label)->
                            ChoiceRow(label,os.coordinateFormat==format) {os.shell.updateSystemPreferences {it.copy(appPreferenceValues=it.appPreferenceValues+("preferences.coordinate.format" to "c:$format"))}}
                        }
                        Label(os.formatCoordinates(GeoPoint(-36.84123,174.76543)),20)
                        Label(os.t("海图、日志、锚警、仪表与磁贴同时生效。原始记录及 NMEA 数据不改变。水深统一使用米，温度统一使用摄氏度。", "Applies to charts, logs, anchor watch, instruments and tiles. Raw records and NMEA remain unchanged. Depth uses metres; temperature uses Celsius."),17,c.muted)
                    }
                    "start" -> {
                        Label(os.t("长按磁贴，拖动位置或调整尺寸。每个应用保留一块磁贴，在磁贴工坊选择它的内容样式。", "Hold a tile to move or resize it. Each app has one tile; choose its content style in Tile Studio."),23)
                        MetroButton(os.t("选择磁贴样式", "choose tile styles"),{os.open("tiles")},primary=true)
                        MetroButton(os.t("恢复默认布局", "restore default layout"),{reset=true})
                    }
                    else -> {
                        Label("Yokuli OS",42,c.accent); Label(BuildConfig.VERSION_NAME,22)
                        Label(os.t("海上生活，简单一点。", "a little simpler, at sea."),24)
                        Label("Selawik · Microsoft · SIL Open Font License 1.1",14,c.muted)
                        Label("© OpenStreetMap contributors · Google Maps · MapLibre · Natural Earth",14,c.muted)
                    }
                }
            }
        }
    }
    if(reset) ConfirmDialog(os,os.t("恢复默认磁贴布局？其他资料保留。", "Restore default tiles? Other data stays."),{reset=false}) {os.shell.resetStart();reset=false}
}

@Composable private fun VesselProfileSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    var name by remember(state.vesselSettings.vesselName) { mutableStateOf(state.vesselSettings.vesselName) }
    var length by remember(state.settings.boatLengthMeters) { mutableStateOf(state.settings.boatLengthMeters.toString()) }
    var draft by remember(state.vesselSettings.draftMeters) { mutableStateOf(state.vesselSettings.draftMeters?.toString().orEmpty()) }
    var bow by remember(state.settings.bowRollerHeightMeters) { mutableStateOf(state.settings.bowRollerHeightMeters.toString()) }
    var antenna by remember(state.settings.nmeaGpsAntennaToBowMeters) { mutableStateOf(state.settings.nmeaGpsAntennaToBowMeters.toString()) }
    val valid = length.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true && (draft.isBlank() || draft.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true) && listOf(bow, antenna).all { text -> text.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true }
    val changed = name != state.vesselSettings.vesselName || length.toDoubleOrNull() != state.settings.boatLengthMeters || draft.toDoubleOrNull() != state.vesselSettings.draftMeters || bow.toDoubleOrNull() != state.settings.bowRollerHeightMeters || antenna.toDoubleOrNull() != state.settings.nmeaGpsAntennaToBowMeters
    PageBody {
        Field(os.t("船名", "boat name"), name, { name = it.take(100) })
        Field(os.t("船长 · m", "length · m"), length, { length = it }, number = true)
        Field(os.t("吃水 · m（未知可留空）", "draft · m (leave blank if unknown)"), draft, { draft = it }, number = true)
        Label(os.t("设备位置", "equipment positions"), 26)
        Field(os.t("船艏滚轮距水面高度 · m", "bow roller height above water · m"), bow, { bow = it }, number = true)
        Field(os.t("固定 GPS 天线到船艏滚轮 · m", "fixed GPS antenna to bow roller · m"), antenna, { antenna = it }, number = true)
        Label(os.t("这些资料供相关应用共用。手机定位不会假定手机固定在 GPS 天线位置。", "These details are shared by the apps that need them. Phone positioning does not assume a fixed antenna location."), 16, LocalMetro.current.muted)
        MetroButton(os.t("保存船舶资料", "save boat details"), {
            marine.vm.updateSettings(state.settings.copy(boatLengthMeters = length.toDouble(), bowRollerHeightMeters = bow.toDouble(), nmeaGpsAntennaToBowMeters = antenna.toDouble()))
            marine.vm.updateVesselDataSettings(state.vesselSettings.copy(vesselName = name.trim(), draftMeters = draft.toDoubleOrNull()))
        }, primary = true, enabled = valid && changed)
        if (!changed) Label(os.t("资料已保存", "details saved"), 15, LocalMetro.current.muted)
        MenuRow(os.t("传感器与船体安装", "sensors & vessel mounting"), os.t("在驾驶台中确认安装、查看来源和实时姿态", "confirm mounting and see sources and live attitude in Helm"), "data") { os.open("instruments") }
    }
}

@Composable private fun SystemAccessSettings(os: OsStore) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { revision++; os.marine?.vm?.onPermissionsChanged() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++; os.marine?.vm?.onPermissionsChanged() }
    val locationGranted = remember(revision) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val notifications = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val locationEnabled = remember(revision) { context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true }
    val unrestricted = remember(revision) { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { os.notify("无法打开系统设置", "Could not open Android settings") } }
    PageBody {
        MenuRow(os.t("精确定位权限", "precise location permission"), if (locationGranted) os.t("已允许", "allowed") else os.t("未允许 · 点按请求", "not allowed · tap to request"), "locate") {
            if (locationGranted) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) else request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        MenuRow(os.t("Android 定位服务", "Android location service"), if (locationEnabled) os.t("已开启", "on") else os.t("已关闭", "off")) { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        MenuRow(os.t("通知权限", "notifications"), if (notifications) os.t("已允许", "allowed") else os.t("未允许 · 点按请求", "not allowed · tap to request")) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        MenuRow(os.t("后台运行", "background operation"), if (unrestricted) os.t("电池优化未限制本应用", "not restricted by battery optimisation") else os.t("受 Android 电池优化管理", "managed by Android battery optimisation")) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        Label(os.t("值守、记录与数据连接会说明各自需要的权限。离开页面不会自动结束正在运行的任务。", "Watch, recording and connection screens explain the access they need. Leaving a screen does not automatically end a running task."), 18, LocalMetro.current.muted)
        MetroButton(os.t("打开应用系统设置", "open Android app settings"), { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
    }
}

@Composable private fun SystemSoundSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val context = LocalContext.current
    val now = rememberMarineClock()
    val audio = context.getSystemService(AudioManager::class.java)
    val volume = remember(now) { audio.getStreamVolume(AudioManager.STREAM_ALARM) }
    val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    val testing = state.alarmSnapshot.type == AlarmType.ALARM_TEST && state.alarmSnapshot.state == AlarmState.ALARM
    val customName = remember(state.settings.customAlarmSoundUri) {
        state.settings.customAlarmSoundUri?.let { raw -> runCatching { context.contentResolver.query(Uri.parse(raw), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            marine.vm.updateSettings(marine.vm.ui.value.settings.copy(alarmSound = AlarmSound.CUSTOM, customAlarmSoundUri = uri.toString()))
        }
    }
    PageBody {
        Label(os.t("警报声音", "alarm sound"), 28)
        MetroButton(os.t("内置循环警报音", "built-in looping alarm"), { marine.vm.updateSettings(state.settings.copy(alarmSound = AlarmSound.SYSTEM_ALARM)) }, primary = state.settings.alarmSound != AlarmSound.CUSTOM)
        MetroButton(os.t("选择音频文件", "choose an audio file"), { choose.launch(arrayOf("audio/*")) }, primary = state.settings.alarmSound == AlarmSound.CUSTOM)
        if (state.settings.alarmSound == AlarmSound.CUSTOM) Label(customName ?: os.t("已选择自定义声音", "custom sound selected"), 18, LocalMetro.current.muted)
        Label(os.t("自定义文件不可用时会回退到内置警报。", "Unavailable custom audio falls back to the built-in alarm."), 17, LocalMetro.current.muted)
        Label(os.t("再次提醒间隔", "remind again after"), 27)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf(5, 10, 15).forEach { minutes ->
            MetroButton(os.t("$minutes 分钟", "$minutes min"), { marine.vm.updateSettings(state.settings.copy(alarmSnoozeMinutes = minutes)) }, Modifier.weight(1f), primary = state.settings.alarmSnoozeMinutes == minutes)
        } }
        Label(os.t("稍后提醒会停止声音和振动，但监控继续；危险仍在时会再次响铃。", "Snooze stops sound and vibration while monitoring continues. A persistent danger sounds again after this interval."), 17, LocalMetro.current.muted)
        Label(os.t("Android 警报音量", "Android alarm volume") + " · $volume / $maximum", 24)
        if (volume == 0) Label(os.t("系统警报音量已静音。请先在声音设置中调高。", "System alarm volume is muted. Raise it in sound settings."), 18, LocalMetro.current.accent)
        MetroButton(if (testing) os.t("停止试听", "stop alarm test") else os.t("试听警报", "test alarm"), { if (testing) marine.vm.stopAlarmTest() else marine.vm.testAlarm() }, primary = true)
        if (testing) MetroButton(os.t("我能听见警报", "I can hear the alarm"), { marine.vm.confirmAlarmAudible(); marine.vm.stopAlarmTest() })
        MenuRow(os.t("Android 声音设置", "Android sound settings"), os.t("系统警报音量", "system alarm volume")) { marine.vm.openAlarmSoundSettings() }
        MenuRow(os.t("勿扰模式", "Do Not Disturb"), os.t("检查 Android 是否允许警报打断", "check whether Android allows alarm interruptions")) { marine.vm.openDoNotDisturbSettings() }
    }
}

@Composable private fun SystemBackupSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val connections by marine.vm.nmeaConnections.collectAsState()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) marine.vm.exportBackup(uri) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val running = state.backup.running
    // Manager rechecks its full policy before replacement; this also covers new parallel connections.
    val busy = state.active != null || state.activeTrip != null || state.activeSonarSurvey != null || connections.any { it.requested } || state.settings.mockEnabled || state.settings.nmeaSharingEnabled || state.outputSettings.anyEnabled
    PageBody {
        Label(os.t("航行记录与船舶数据", "voyage & vessel data"), 31)
        Label(os.t("备份已有航行记录、航迹、时刻、事件、船舶设置，以及原有锚泊与测深数据。", "Back up voyage recordings, tracks, moments, events, vessel settings and existing anchoring and sounding data."), 20)
        Label(os.t("这是航行数据备份：不包含开始磁贴、系统偏好、海图文件夹，或“我的航行”里的坐标与航线。坐标与航线请在“我的航行”中导出 GPX；海图原文件仍在你选择的文件夹中。", "This is a voyage-data backup. It does not include Start tiles, system preferences, chart folders or coordinates and routes in My Sailing. Export those as GPX in My Sailing; chart files remain in your chosen folders."), 17, LocalMetro.current.muted)
        Label(os.t("备份包含精确位置历史，文件未加密。", "The backup contains precise location history and is not encrypted."), 16, LocalMetro.current.muted)
        MetroButton(os.t("保存备份文件", "save backup file"), { export.launch("Yokuli-Voyage-${java.time.LocalDate.now()}.yokuli-backup") }, primary = true, enabled = !running)
        state.backup.lastBackupAt?.let { Label(os.t("上次备份：", "last backup: ") + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)), 16, LocalMetro.current.muted) }
        Label(os.t("从备份替换恢复", "replace from a backup"), 29)
        Label(os.t("恢复会先校验文件，再替换本机航行与船舶数据。恢复前请先结束所有值守、记录和调查，并关闭 NMEA 连接、输出及定位代理。", "The backup is validated before replacing local voyage and vessel data. First end watches, recordings and surveys, then stop NMEA connections, outputs and the location proxy."), 18)
        if (busy) Label(os.t("仍有任务或连接运行，暂时不能恢复。", "A task or connection is still running. Restore is unavailable."), 17, LocalMetro.current.accent)
        MetroButton(os.t("选择备份文件", "choose backup file"), { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !running && !busy)
        if (running) Label(os.t("正在处理备份，请稍候…", "processing backup…"), 22, LocalMetro.current.accent)
        state.backup.result?.let { Label(os.t("操作完成", "operation completed"), 22, LocalMetro.current.accent); MetroButton(os.t("关闭结果", "dismiss result"), { marine.vm.clearBackupResult() }) }
        state.backup.error?.let { error -> Label(os.t("操作未完成。原始错误：", "operation failed: ") + error, 18, LocalMetro.current.accent); MetroButton(os.t("关闭错误", "dismiss error"), { marine.vm.clearBackupResult() }) }
    }
    restoreUri?.let { uri -> ConfirmDialog(os, os.t("校验并替换本机航行与船舶数据？除非另有备份，否则无法撤销。", "Validate and replace local voyage and vessel data? This cannot be undone without another backup."), { restoreUri = null }) { restoreUri = null; marine.vm.restoreBackup(uri) } }
}

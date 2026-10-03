package com.yokuli.marine.shell.rebuild.ui

import kotlinx.coroutines.launch

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.core.design.YokuliBrandSignature
import com.yokuli.anchorwatch.platform.HostBuildIdentity
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
    val buildIdentity = remember(os.context) { HostBuildIdentity.read(os.context) }
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection.substringBefore(':')) }
    ReportVisibleAppRoute(os, if(section == "overview") "settings" else "settings:$section")
    val pageStates = rememberSaveableStateHolder()
    val c = LocalMetro.current
    val back = { if(section=="start_preset"&&initialSection!="start_preset")section="start" else if(initialSection!="overview") os.shell.popRoute() else section = "overview" }
    BindInternalAppInputHandler { input -> if (input == ShellInput.BACK && section != "overview") { back(); true } else false }
    fun title(key: String) = when (key) {
        "appearance" -> os.t("外观与显示", "appearance & display")
        "language" -> os.t("语言", "language")
        "units" -> os.t("单位与坐标", "units & coordinates")
        "start" -> os.t("开始屏幕", "Start")
        "start_preset" -> os.t("帆船布局", "Sailing layout")
        "vessel" -> os.t("我的船", "my boat")
        "permissions" -> os.t("权限与后台", "permissions & background")
        "sound" -> os.t("声音与警报", "sound & alarms")
        "backup" -> os.t("备份与恢复", "backup & restore")
        "linz" -> os.t("LINZ 航行资料", "LINZ chart data")
        "about" -> os.t("关于", "about")
        "exit" -> os.t("退出 Yokuli", "exit Yokuli")
        else -> os.t("设置", "settings")
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, title(section), hasLocalBack = section!="overview")
        AppPageTransition(section, pageKey = { it }, pageDepth = { if (it == "overview") 0 else if(it=="start_preset")2 else 1 },
            modifier = Modifier.weight(1f)) { page ->
        pageStates.SaveableStateProvider(page) {
        when (page) {
            "vessel" -> VesselProfileSettings(os)
            "permissions" -> SystemAccessSettings(os)
            "sound" -> SystemSoundSettings(os)
            "backup" -> SystemBackupSettings(os)
            "linz" -> LinzSettingsSection(os)
            "exit" -> RuntimeExitSettings(os)
            "start_preset" -> SailingStartPresetSettings(os)
            else -> PageBody {
                when (page) {
                    "overview" -> {
                        AppSection(os.t("系统", "system"))
                        listOf("start", "appearance", "language", "units", "sound", "permissions").forEach { key ->
                            MenuRow(title(key), when(key) {
                                "start" -> os.t("4 或 6 列、背景照片与透明磁贴", "4 or 6 columns, wallpaper and transparent tiles")
                                "language" -> if(os.chinese) "简体中文" else "English"
                                "units" -> "${os.distanceUnitLabel} · ${os.speedUnitLabel} · " + os.t("水深 ", "depth ") + os.depthUnitLabel
                                else -> null
                            }) {section=key}
                        }
                        AppSection(os.t("个人偏好", "personal"))
                        MenuRow(title("vessel")) {section="vessel"}
                        AppSection(os.t("资料与系统信息", "data & information"))
                        MenuRow(title("linz"), os.t("API 密钥与离线区域缓存", "API key and offline area cache")) {section="linz"}
                        MenuRow(title("backup")) {section="backup"}
                        MenuRow(title("about"), buildIdentity.appVersionName) {section="about"}
                        MenuRow(title("exit"), os.t("暂停任务并停止后台采集与共享", "pause tasks and stop background collection & sharing"), "close") {section="exit"}
                    }
                    "appearance" -> {
                        MenuRow(title("start"),os.t("自定义开始屏幕的照片、透明度与磁贴底色", "personalise Start’s photo, transparency and tile background")) {os.openLinked("settings:start")}
                        AccentPaletteSettings(os)
                        ChoiceRow(os.t("深色背景", "dark background"), !os.light) {os.shell.updateSystemPreferences {it.copy(themeModeName="DARK")}}
                        ChoiceRow(os.t("浅色背景", "light background"), os.light) {os.shell.updateSystemPreferences {it.copy(themeModeName="LIGHT")}}
                        Toggle(os.t("保持屏幕常亮", "keep screen awake"),os.keepAwake,os.t("仅在 Yokuli OS 位于前台时", "while Yokuli OS is in front")) {enabled->
                            os.shell.updateSystemPreferences {it.copy(appPreferenceValues=it.appPreferenceValues+("preferences.display.keep_awake" to AppPreferenceRegistry.encode(AppPreferenceValue.Toggle(enabled))))}
                        }
                        AppSection(os.t("文字大小", "text size"))
                        listOf("COMPACT" to os.t("紧凑", "compact"),"STANDARD" to os.t("标准", "standard"),"LARGE" to os.t("较大", "larger")).forEach {(value,label)->
                            ChoiceRow(label,os.textSize==value) {os.shell.updateSystemPreferences {it.copy(appPreferenceValues=it.appPreferenceValues+("preferences.display.text_size" to "c:$value"))}}
                        }
                        Label(os.t("转场、轻按反馈和动态磁贴使用统一动效。", "Transitions, touch feedback and live tiles share one motion language."),15,c.muted)
                    }
                    "language" -> {
                        ChoiceRow("简体中文",os.chinese) {os.shell.updateSystemPreferences {it.copy(languageTag="zh-CN")}}
                        ChoiceRow("English",!os.chinese) {os.shell.updateSystemPreferences {it.copy(languageTag="en")}}
                    }
                    "units" -> UnitSettings(os)
                    "start" -> {
                        MenuRow(os.t("帆船布局","Sailing layout"),os.t("预览一套为出航准备的组合磁贴","Preview a complete set of tiles for sailing"),"chart") {section="start_preset"}
                        StartColumnsSettings(os)
                        StartBackgroundSettings(os)
                        Label(os.t("长按磁贴排列位置。在工坊选择内容、组合与点击去向。", "Hold tiles to arrange them. Choose content, combinations and destinations in Tile Studio."),15)
                        MetroButton(os.t("选择磁贴样式", "choose tile styles"),{os.openLinked("tiles")},primary=true)
                    }
                    else -> {
                        YokuliBrandSignature(Modifier.size(width=208.dp,height=44.dp),color=c.fg,accent=c.accentText)
                        Label(buildIdentity.appVersionName,15,c.muted)
                        Label(os.t("海上生活，简单一点。", "a little simpler, at sea."),20)
                        Label("${buildIdentity.flavor} · ${buildIdentity.channel} · ${buildIdentity.appVersionCode}",16,c.muted)
                        Label("Git ${buildIdentity.gitSha.take(12)} · ${buildIdentity.gitState}",16,c.muted)
                        Label(buildIdentity.gitBranch,16,c.muted)
                        Label(buildIdentity.timestampUtc,14,c.muted)
                        Label("Selawik · Microsoft · SIL Open Font License 1.1",14,c.muted)
                        Label("Google Maps · MapLibre · Natural Earth",14,c.muted)
                    }
                }
            }
        }
        }
        }
    }
}

@Composable private fun SystemAccessSettings(os: OsStore) {
    val context = LocalContext.current
    val residency = os.marine?.system?.residency
    val runtime = residency?.state?.collectAsState()?.value
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { revision++; os.marine?.services?.sources?.onPermissionsChanged() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++; os.marine?.services?.sources?.onPermissionsChanged() }
    val locationGranted = remember(revision) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val notifications = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val locationEnabled = remember(revision) { context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true }
    val unrestricted = remember(revision) { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { os.notify("无法打开系统设置", "Could not open Android settings") } }
    PageBody {
        runtime?.let { status ->
            AppSection(os.t("航行核心", "Marine Core"))
            val coreConnection = os.marine?.system?.connection?.collectAsState()?.value
            Label(if(coreConnection?.readiness == com.yokuli.runtime.contract.RuntimeReadiness.READY)
                os.t("独立后台 · 已连接", "Independent background · connected") else os.t("正在恢复连接", "Restoring connection"), 17)
            status.recoveryProblem?.let { Label(os.t("恢复被阻止，原始记录仍保留：", "Recovery is blocked; saved records are retained: ") + it, 13, LocalMetro.current.accentText) }
            if(status.deviceRestarted) Label(os.t("设备重启后，守锚和记录保留原会话并暂停。核对船位后到对应应用继续。", "After device restart, watch and log sessions remain paused. Check position and resume in their apps."), 13, LocalMetro.current.muted)
            AppSection(os.t("系统采集", "system collection"))
            Label(when(status.phase) {
                com.yokuli.runtime.contract.RuntimeResidencyPhase.RUNNING -> os.t("后台运行中", "running in the background")
                com.yokuli.runtime.contract.RuntimeResidencyPhase.STARTING -> os.t("正在启动", "starting")
                com.yokuli.runtime.contract.RuntimeResidencyPhase.STOPPING -> os.t("正在保存并停止", "saving and stopping")
                com.yokuli.runtime.contract.RuntimeResidencyPhase.STOPPED -> os.t("已停止", "stopped")
                com.yokuli.runtime.contract.RuntimeResidencyPhase.BLOCKED -> os.t("后台运行受限", "background operation restricted")
            }, 22)
            val capabilities = buildList {
                if(status.phoneLocation)add(os.t("手机定位", "phone position"))
                if(status.phoneHeading)add(os.t("罗盘", "compass"))
                if(status.phoneMotion)add(os.t("姿态与运动", "attitude & motion"))
                if(status.phonePressure)add(os.t("气压", "pressure"))
            }
            Label(capabilities.joinToString(" · ").ifBlank { os.t("尚无正在采集的手机传感器", "no phone sensor currently collecting") }, 15, LocalMetro.current.muted)
            if(status.problem == "PHONE_BACKGROUND_LOCATION_NOT_ALLOWED") Label(os.t("手机后台定位尚未获准；检查精确定位权限，并在此页重试。其他采集继续。", "Phone background position is not available. Check precise location access, then retry here. Other collection continues."), 14, LocalMetro.current.muted)
            Label(os.t("输入 ${status.inputConnections} · 输出 ${status.outputConnections} · 共享${if(status.sharing)"开启" else "关闭"}", "${status.inputConnections} inputs · ${status.outputConnections} outputs · sharing ${if(status.sharing)"on" else "off"}"), 15, LocalMetro.current.muted)
            Label(os.t("仅保留你已开启的定位、连接与共享；查看磁贴或关闭页面不会切换来源。", "Only your enabled position source, connections and sharing continue. Tiles and page changes never switch sources."), 14, LocalMetro.current.muted)
            var retrying by remember { mutableStateOf(false) }
            if(status.recoveryProblem != null) MetroButton(os.t("重新读取恢复记录", "Retry recovery"), {
                if (!retrying) os.scope.launch {
                    retrying = true
                    try {
                        if(residency?.retryRecovery() == true) residency.startFromForeground()
                        else os.notify("恢复仍受阻，已保留原始记录", "Recovery is still blocked; original records are retained", app=AppId.SETTINGS)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { os.notify("核心暂未连接，请稍后重试", "Core is not connected. Please retry shortly", app=AppId.SETTINGS) }
                    finally { retrying = false }
                }
            }, enabled=!retrying)
            else if(status.phase == com.yokuli.runtime.contract.RuntimeResidencyPhase.BLOCKED || status.problem != null) MetroButton(os.t("重试后台运行", "retry background operation"), { residency?.startFromForeground() })
            AppSection(os.t("Android 访问权限", "Android access"))
        }
        MenuRow(os.t("精确定位权限", "precise location permission"), if (locationGranted) os.t("已允许", "allowed") else os.t("未允许 · 点按请求", "not allowed · tap to request"), "locate") {
            if (locationGranted) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) else request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        MenuRow(os.t("Android 定位服务", "Android location service"), if (locationEnabled) os.t("已开启", "on") else os.t("已关闭", "off")) { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        MenuRow(os.t("通知权限", "notifications"), if (notifications) os.t("已允许", "allowed") else os.t("未允许 · 点按请求", "not allowed · tap to request")) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        MenuRow(os.t("后台运行", "background operation"), if (unrestricted) os.t("电池优化未限制本应用", "not restricted by battery optimisation") else os.t("受 Android 电池优化管理", "managed by Android battery optimisation")) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        Label(os.t("值守、记录与数据连接会说明各自需要的权限。离开页面不会自动结束正在运行的任务。", "Watch, recording and connection screens explain the access they need. Leaving a screen does not automatically end a running task."), 15, LocalMetro.current.muted)
        MetroButton(os.t("打开应用系统设置", "open Android app settings"), { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
    }
}

@Composable private fun SystemSoundSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
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
            marine.services.preferences.setAlarmSound(AlarmSound.CUSTOM, uri.toString())
        }
    }
    PageBody {
        AppSection(os.t("消息提示音", "Notification sounds"))
        listOf("yokuli.messages.v1" to os.t("普通消息", "Messages"), "yokuli.attention.v1" to os.t("需要注意", "Needs attention")).forEach { (channel, label) ->
            MenuRow(label, os.t("提示音、振动与系统横幅", "Sound, vibration and system banners"), "notification") {
                runCatching { context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channel)) }
                    .onFailure { os.notify("无法打开通知设置", "Could not open notification settings", app=AppId.SETTINGS) }
            }
        }
        AppSection(os.t("警报声音", "alarm sound"))
        ChoiceRow(os.t("内置循环警报音", "built-in looping alarm"), state.settings.alarmSound != AlarmSound.CUSTOM) {
            marine.services.preferences.setAlarmSound(AlarmSound.SYSTEM_ALARM)
        }
        ChoiceRow(os.t("自定义声音", "custom sound"), state.settings.alarmSound == AlarmSound.CUSTOM,
            subtitle = customName ?: os.t("选择一个音频文件", "choose an audio file")) { choose.launch(arrayOf("audio/*")) }
        if (state.settings.alarmSound == AlarmSound.CUSTOM) MetroButton(os.t("更换音频文件", "change audio file"), { choose.launch(arrayOf("audio/*")) })
        Label(os.t("自定义文件不可用时会回退到内置警报。", "Unavailable custom audio falls back to the built-in alarm."), 15, LocalMetro.current.muted)
        AppSection(os.t("再次提醒间隔", "remind again after"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(os.t("${state.settings.alarmSnoozeMinutes} 分钟", "${state.settings.alarmSnoozeMinutes} min"), 20, modifier = Modifier.weight(1f))
            MetroButton("−", { marine.services.preferences.setAlarmSnoozeMinutes((state.settings.alarmSnoozeMinutes - 5).coerceAtLeast(5)) },
                Modifier.width(56.dp), enabled = state.settings.alarmSnoozeMinutes > 5)
            MetroButton("+", { marine.services.preferences.setAlarmSnoozeMinutes((state.settings.alarmSnoozeMinutes + 5).coerceAtMost(15)) },
                Modifier.width(56.dp), enabled = state.settings.alarmSnoozeMinutes < 15)
        }
        Label(os.t("稍后提醒会停止声音和振动，但监控继续；危险仍在时会再次响铃。", "Snooze stops sound and vibration while monitoring continues. A persistent danger sounds again after this interval."), 15, LocalMetro.current.muted)
        Label(os.t("Android 警报音量", "Android alarm volume") + " · $volume / $maximum", 20)
        if (volume == 0) Label(os.t("系统警报音量已静音。请先在声音设置中调高。", "System alarm volume is muted. Raise it in sound settings."), 15, LocalMetro.current.accentText)
        MetroButton(if (testing) os.t("停止试听", "stop alarm test") else os.t("试听警报", "test alarm"), { if (testing) marine.services.preferences.stopAlarmTest() else marine.services.preferences.testAlarm() }, primary = true)
        if (testing) MetroButton(os.t("我能听见警报", "I can hear the alarm"), { marine.services.preferences.confirmAlarmAudible(); marine.services.preferences.stopAlarmTest() })
        MenuRow(os.t("Android 声音设置", "Android sound settings"), os.t("系统警报音量", "system alarm volume")) { marine.services.preferences.openAlarmSoundSettings() }
        MenuRow(os.t("勿扰模式", "Do Not Disturb"), os.t("检查 Android 是否允许警报打断", "check whether Android allows alarm interruptions")) { marine.services.preferences.openDoNotDisturbSettings() }
    }
}

@Composable private fun SystemBackupSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
    val connections by marine.services.network.connections.collectAsState()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) marine.services.preferences.exportBackup(uri) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val running = state.backup.running
    // Manager rechecks its full policy before replacement; this also covers new parallel connections.
    val busy = state.active != null || state.activeTrip != null || state.activeSonarSurvey != null || connections.any { it.requested } || state.settings.mockEnabled || state.settings.nmeaSharingEnabled || state.outputSettings.anyEnabled
    PageBody {
        AppSection(os.t("航行记录与船舶数据", "voyage & vessel data"))
        Label(os.t("备份已有航行记录、航迹、时刻、事件、船舶设置，以及原有锚泊与测深数据。", "Back up voyage recordings, tracks, moments, events, vessel settings and existing anchoring and sounding data."), 15)
        Label(os.t("这是航行数据备份：不包含开始磁贴、系统偏好、海图文件夹，或“我的航行”里的坐标与航线。坐标与航线请在“我的航行”中导出 GPX；海图原文件仍在你选择的文件夹中。", "This is a voyage-data backup. It does not include Start tiles, system preferences, chart folders or coordinates and routes in My Sailing. Export those as GPX in My Sailing; chart files remain in your chosen folders."), 15, LocalMetro.current.muted)
        Label(os.t("备份包含精确位置历史，文件未加密。", "The backup contains precise location history and is not encrypted."), 15, LocalMetro.current.muted)
        MetroButton(os.t("保存备份文件", "save backup file"), { export.launch("Yokuli-Voyage-${java.time.LocalDate.now()}.yokuli-backup") }, primary = true, enabled = !running)
        state.backup.lastBackupAt?.let { Label(os.t("上次备份：", "last backup: ") + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)), 16, LocalMetro.current.muted) }
        AppSection(os.t("从备份替换恢复", "replace from a backup"))
        Label(os.t("恢复会先校验文件，再替换本机航行与船舶数据。恢复前请先结束所有值守、记录和调查，并关闭 NMEA 连接、输出及定位代理。", "The backup is validated before replacing local voyage and vessel data. First end watches, recordings and surveys, then stop NMEA connections, outputs and the location proxy."), 15)
        if (busy) Label(os.t("仍有任务或连接运行，暂时不能恢复。", "A task or connection is still running. Restore is unavailable."), 15, LocalMetro.current.accentText)
        MetroButton(os.t("选择备份文件", "choose backup file"), { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !running && !busy)
        if (running) Label(os.t("正在处理备份，请稍候…", "processing backup…"), 15, LocalMetro.current.accentText)
        state.backup.result?.let { Label(os.t("操作完成", "operation completed"), 15, LocalMetro.current.accentText); MetroButton(os.t("关闭结果", "dismiss result"), { marine.services.preferences.clearBackupResult() }) }
        state.backup.error?.let { error -> Label(os.t("操作未完成。原始错误：", "operation failed: ") + error, 18, LocalMetro.current.accentText); MetroButton(os.t("关闭错误", "dismiss error"), { marine.services.preferences.clearBackupResult() }) }
    }
    restoreUri?.let { uri -> ConfirmDialog(os, os.t("校验并替换本机航行与船舶数据？除非另有备份，否则无法撤销。", "Validate and replace local voyage and vessel data? This cannot be undone without another backup."), { restoreUri = null }) { restoreUri = null; marine.services.preferences.restoreBackup(uri) } }
}

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

@Composable fun SettingsScreen(os: OsStore, initialSection: String = "overview") {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    var reset by remember { mutableStateOf(false) }
    val preferences by os.shell.persistence.state.collectAsState()
    val c = LocalMetro.current
    val back = { if (section == "overview") os.back() else section = "overview" }
    BindInternalAppInputHandler { input -> if (input == ShellInput.BACK && section != "overview") { section = "overview"; true } else false }
    fun title(key: String) = when (key) {
        "appearance" -> os.t("外观与显示", "appearance & display")
        "language" -> os.t("语言", "language")
        "units" -> os.t("单位与坐标", "units & coordinates")
        "start" -> os.t("开始屏幕", "Start screen")
        "tiles" -> os.t("应用磁贴", "app tiles")
        "vessel" -> os.t("船舶资料", "your boat")
        "permissions" -> os.t("权限与后台", "permissions & background")
        "sources" -> os.t("船舶数据来源", "boat data sources")
        "sound" -> os.t("声音与警报", "sound & alarms")
        "backup" -> os.t("备份与恢复", "backup & restore")
        "about" -> os.t("关于", "about")
        else -> os.t("设置", "settings")
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, title(section), onBack = back)
        when (section) {
            "vessel" -> VesselProfileSettings(os)
            "permissions" -> SystemAccessSettings(os)
            "sources" -> VesselSourceSettings(os)
            "sound" -> SystemSoundSettings(os)
            "backup" -> SystemBackupSettings(os)
            else -> PageBody {
                when (section) {
                    "overview" -> {
                        val vesselName = os.marine?.vm?.ui?.collectAsState()?.value?.vesselSettings?.vesselName.orEmpty()
                        listOf("appearance", "language", "units", "start", "tiles", "vessel", "sources", "sound", "permissions", "backup", "about").forEach { key ->
                            MenuRow(title(key), when (key) {
                                "appearance" -> os.t("背景、主题色、动画与屏幕常亮", "background, accent, motion & screen awake")
                                "language" -> if (os.chinese) "简体中文" else "English"
                                "units" -> (if (os.measurementUnits == MeasurementUnitSystem.NAUTICAL) os.t("海里 · 节", "nautical miles · knots") else os.t("公里 · 公里/小时", "kilometres · km/h")) + " · ${os.coordinateFormat}"
                                "start" -> os.t("磁贴布局与恢复默认", "tile layout & restore defaults")
                                "tiles" -> os.t("选择各应用在开始屏幕显示什么", "choose what apps show on Start")
                                "vessel" -> vesselName.ifBlank { os.t("船名、船长、吃水与设备位置", "name, length, draft & equipment positions") }
                                "permissions" -> os.t("定位、通知与后台运行", "location, notifications & background access")
                                "sources" -> os.t("船位来源与各项读数的选择", "position source & selected measurements")
                                "sound" -> os.t("警报音、再次提醒与试听", "alarm sound, reminders & audible check")
                                "backup" -> os.t("保存航行资料与恢复旧备份", "save voyage data & restore backups")
                                else -> "Yokuli OS · ${BuildConfig.VERSION_NAME}"
                            }) { section = key }
                        }
                    }
                    "appearance" -> {
                        Label(os.t("主题色", "accent colour"), 26)
                        WpAccent.entries.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { accent -> Box(Modifier.size(58.dp).background(androidx.compose.ui.graphics.Color(accent.argb))
                                .then(if (os.accent == accent.argb) Modifier.border(3.dp, c.fg) else Modifier)
                                .clickable { os.shell.updateSystemPreferences { it.copy(accentName = accent.name) } }) }
                        } }
                        Toggle(os.t("浅色背景", "light background"), os.light) { light -> os.shell.updateSystemPreferences { it.copy(themeModeName = if (light) "LIGHT" else "DARK") } }
                        Toggle(os.t("保持屏幕常亮", "keep screen awake"), os.keepAwake, os.t("Yokuli OS 在前台时保持屏幕亮着", "keep the display on while Yokuli OS is in front")) { enabled ->
                            os.shell.updateSystemPreferences { it.copy(appPreferenceValues = it.appPreferenceValues + ("preferences.display.keep_awake" to AppPreferenceRegistry.encode(AppPreferenceValue.Toggle(enabled)))) }
                        }
                        Toggle(os.t("减少动画", "reduce motion"), os.reduceMotion, os.t("关闭时跟随 Android 的动画设置", "otherwise follow Android’s animation setting")) { reduced -> os.shell.updateSystemPreferences { it.copy(motionPreferenceName = if (reduced) "REDUCED" else "FOLLOW_SYSTEM") } }
                    }
                    "language" -> {
                        MetroButton("简体中文", { os.shell.updateSystemPreferences { it.copy(languageTag = "zh-CN") } }, primary = os.chinese)
                        MetroButton("English", { os.shell.updateSystemPreferences { it.copy(languageTag = "en") } }, primary = !os.chinese)
                        Label(os.t("所有应用使用同一种语言。", "Every app uses the same language."), 17, c.muted)
                    }
                    "units" -> {
                        Label(os.t("距离与速度", "distance & speed"), 25)
                        MeasurementUnitSystem.entries.forEach { units ->
                            MetroButton(if (units == MeasurementUnitSystem.NAUTICAL) os.t("海里 / 节", "nautical miles / knots") else os.t("公里 / 公里每小时", "kilometres / km per hour"),
                                { os.shell.updateSystemPreferences { it.copy(measurementUnitSystemName = units.name) } }, primary = os.measurementUnits == units)
                        }
                        Label(os.t("坐标格式", "coordinate format"), 25)
                        listOf("DMM" to os.t("度与分", "degrees & minutes"), "DD" to os.t("十进制度", "decimal degrees"), "DMS" to os.t("度、分与秒", "degrees, minutes & seconds")).forEach { (format, label) ->
                            MetroButton(label, { os.shell.updateSystemPreferences { it.copy(appPreferenceValues = it.appPreferenceValues + ("preferences.coordinate.format" to AppPreferenceRegistry.encode(AppPreferenceValue.Choice(format)))) } }, primary = os.coordinateFormat == format)
                        }
                        Label(os.formatCoordinates(GeoPoint(-36.84123, 174.76543)), 20)
                        Label(os.t("只改变显示方式；保存的数据与 NMEA 原始语句保持各自的原始单位。", "These choices change presentation. Saved data and raw NMEA sentences keep their original units."), 16, c.muted)
                    }
                    "start" -> {
                        val count = os.shell.engine.state.collectAsState().value.start.document.placements.size
                        Label(os.t("$count 块磁贴", "$count tiles"), 42, c.accent)
                        Label(os.t("长按磁贴可以移动、改变尺寸或取消固定。应用列表里的应用也可以长按固定。", "Hold a tile to move, resize or unpin it. Hold an app in the app list to pin it."), 20)
                        MetroButton(os.t("回到开始屏幕", "go to Start"), { os.home() }, primary = true)
                        MetroButton(os.t("恢复默认布局", "restore default layout"), { reset = true })
                        Label(os.t("只恢复磁贴布局，不清除海图、连接或航行资料。", "Only the tile layout is restored. Charts, connections and voyage data are kept."), 16, c.muted)
                    }
                    "tiles" -> {
                        val registry = os.shell.appPreferenceRegistry
                        val resolved = registry.resolve(preferences?.appPreferenceValues.orEmpty())
                        os.shell.apps.forEach { app ->
                            val definitions = registry.definitions.filterValues { it.first == app.id && it.first.value != "preferences" }
                            if (definitions.isNotEmpty()) {
                                Label(os.title(app.app), 28, c.accent)
                                definitions.forEach { (key, owned) ->
                                    val definition = owned.second
                                    Label(if (os.chinese) definition.label.chinese else definition.label.english, 21)
                                    fun change(value: AppPreferenceValue) = os.shell.updateSystemPreferences { it.copy(appPreferenceValues = it.appPreferenceValues + (key.value to AppPreferenceRegistry.encode(value))) }
                                    when (definition) {
                                        is AppPreferenceDefinition.Choice -> definition.options.forEach { option ->
                                            val label = definition.optionLabels[option]?.let { if (os.chinese) it.chinese else it.english } ?: option
                                            MetroButton(label, { change(AppPreferenceValue.Choice(option)) }, primary = (resolved[key] as? AppPreferenceValue.Choice)?.option == option)
                                        }
                                        is AppPreferenceDefinition.Toggle -> Toggle(os.t("启用", "enabled"), (resolved[key] as? AppPreferenceValue.Toggle)?.enabled == true) { change(AppPreferenceValue.Toggle(it)) }
                                    }
                                }
                            }
                        }
                        Label(os.t("这里只列出应用已提供的磁贴内容选项。", "Only tile options provided by an app appear here."), 16, c.muted)
                    }
                    else -> {
                        Label("Yokuli OS", 42, c.accent)
                        Label(BuildConfig.VERSION_NAME, 22)
                        Label(os.t("海上生活，简单一点。", "a little simpler, at sea."), 24)
                        Label(os.t("地图与海图版权归各数据提供者。", "Maps and charts belong to their respective providers."), 16, c.muted)
                        Label("© OpenStreetMap contributors · Google Maps · MapLibre", 14, c.muted)
                    }
                }
            }
        }
    }
    if (reset) ConfirmDialog(os, os.t("恢复默认磁贴布局？其他数据会保留。", "Restore the default tile layout? Other data will be kept."), { reset = false }) { os.shell.resetStart(); reset = false }
}

@Composable private fun VesselSourceSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val connections by marine.vm.nmeaConnections.collectAsState()
    val now = rememberMarineClock()
    val locked = state.active?.paused == false
    val nmeaConnected = connections.any { it.spec.receive && it.state in setOf(NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE) }
    PageBody {
        Label(os.t("船位", "position"), 28)
        Toggle(os.t("手机 GPS", "phone GPS"), os.positionSource == "phone", os.t("开启时请求权限并启动定位；关闭时停止。", "Requests access and starts location when switched on; stops when switched off."), enabled = !locked && os.positionSource in listOf("none", "phone")) { os.requestService(if (it) "gpsOn" else "gpsOff") }
        Toggle(os.t("NMEA 船位", "NMEA position"), os.positionSource == "nmea", if (nmeaConnected) os.t("使用已连接来源中的有效船位", "use a valid fix from connected sources") else os.t("先在 NMEA 中连接一个输入", "connect an input in NMEA first"), enabled = !locked && (os.positionSource == "nmea" || (os.positionSource == "none" && nmeaConnected))) { os.requestService(if (it) "sourceNmea" else "sourceOff") }
        Label(if (locked) os.t("锚警正在值守，暂停后可以更改船位来源。", "Pause the anchor watch before changing its position source.") else os.t("两项都可以关闭。船位来源不会自动切换；NMEA 的其他读数仍可继续更新。", "Both may be off. Position never changes source automatically; other NMEA readings can continue."), 16, LocalMetro.current.muted)
        MenuRow(os.t("NMEA 连接", "NMEA connections"), os.t("管理并行的输入与输出", "manage concurrent inputs and outputs"), "connect") { os.open("nmea") }
        if (os.positionSource != "phone") {
            val selectedConnection = state.vesselSettings.metricSourcePins["POSITION_CONNECTION"]
            connections.filter { it.spec.receive && it.state in setOf(NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE) }.forEach { connection ->
                MetroButton(os.t("船位来自：", "position from: ") + connection.spec.name, { marine.vm.selectNmeaPositionConnection(connection.spec.id) }, primary = os.positionSource == "nmea" && selectedConnection == connection.spec.id, enabled = !locked)
            }
            val positions = state.vesselData.candidates[VesselMetricId.POSITION].orEmpty().filter { it.source.transportProfileId == selectedConnection }
            val selectedSource = state.vesselSettings.metricSourcePins[VesselMetricId.POSITION.name]
            positions.forEach { candidate -> MetroButton(candidate.source.displayName, { marine.vm.setNmeaMetricSource(VesselMetricId.POSITION, candidate.source.persistentKey) }, primary = selectedSource == candidate.source.persistentKey, enabled = !locked && os.positionSource == "nmea") }
            if (selectedConnection != null) Label(os.t("船位固定在选中的连接与来源；信号丢失时不会自动改用另一条连接。", "Position stays on the selected connection and source. Losing its signal does not select another connection."), 16, LocalMetro.current.muted)
        }

        Label(os.t("各项读数", "measurements"), 28)
        val groups = state.vesselData.candidates.filter { it.value.isNotEmpty() || it.key.name in state.vesselSettings.metricSourcePins }.filterKeys { it != VesselMetricId.POSITION }
        if (groups.isEmpty()) Label(os.t("来源提供有效数据后，可以在这里选择每项读数的来源。", "When sources provide data, choose the source for each measurement here."), 19, LocalMetro.current.muted)
        groups.forEach { (metric, candidates) ->
            Label(sourceMetricName(os, metric), 24)
            val pinned = state.vesselSettings.metricSourcePins[metric.name]
            MetroButton(os.t("自动选择可用来源", "automatically select an available source"), { marine.vm.setNmeaMetricSource(metric, null) }, primary = pinned == null)
            candidates.distinctBy { it.source.persistentKey }.forEach { candidate ->
                val valid = candidate.validity == CandidateValidity.ELIGIBLE
                MenuRow((if (pinned == candidate.source.persistentKey) "■ " else "□ ") + candidate.source.displayName,
                    readingAge(os, candidate.receivedElapsedRealtime, now) + " · " + if (valid) os.t("可用", "available") else os.t("当前不可用", "currently unavailable")) { marine.vm.setNmeaMetricSource(metric, candidate.source.persistentKey) }
            }
            if (pinned != null && candidates.none { it.source.persistentKey == pinned }) Label(os.t("已选择的来源当前不可用；可重新选择或改为自动。", "The selected source is unavailable. Choose another source or automatic selection."), 15, LocalMetro.current.muted)
        }
    }
}

internal fun sourceMetricName(os: OsStore, metric: VesselMetricId): String = when (metric) {
    VesselMetricId.POSITION -> os.t("位置", "position")
    VesselMetricId.SOG -> os.t("对地航速", "speed over ground")
    VesselMetricId.COG -> os.t("对地航向", "course over ground")
    VesselMetricId.HEADING_TRUE -> os.t("真船首向", "true heading")
    VesselMetricId.HEADING_MAGNETIC -> os.t("磁船首向", "magnetic heading")
    VesselMetricId.DEPTH -> os.t("水深", "depth")
    VesselMetricId.UKC -> os.t("龙骨下余量", "under-keel clearance")
    VesselMetricId.SPEED_THROUGH_WATER -> os.t("对水航速", "speed through water")
    VesselMetricId.APPARENT_WIND_SPEED -> os.t("视风速", "apparent wind speed")
    VesselMetricId.APPARENT_WIND_ANGLE -> os.t("视风角", "apparent wind angle")
    VesselMetricId.TRUE_WIND_SPEED -> os.t("真风速", "true wind speed")
    VesselMetricId.TRUE_WIND_ANGLE -> os.t("真风角", "true wind angle")
    VesselMetricId.TRUE_WIND_DIRECTION -> os.t("真风向", "true wind direction")
    VesselMetricId.PRESSURE -> os.t("气压", "pressure")
    VesselMetricId.HEEL -> os.t("横倾", "heel")
    VesselMetricId.PITCH -> os.t("纵倾", "pitch")
    VesselMetricId.WATER_TEMPERATURE -> os.t("水温", "water temperature")
    VesselMetricId.AIR_TEMPERATURE -> os.t("气温", "air temperature")
    VesselMetricId.DEVICE_HEADING_TRUE -> os.t("手机真方位", "phone true heading")
    VesselMetricId.DEVICE_HEADING_MAGNETIC -> os.t("手机磁方位", "phone magnetic heading")
    VesselMetricId.RATE_OF_TURN -> os.t("转向率", "rate of turn")
    VesselMetricId.RUDDER_ANGLE -> os.t("舵角", "rudder angle")
    VesselMetricId.ROLL_RATE -> os.t("横摇角速度", "roll rate")
    VesselMetricId.PITCH_RATE -> os.t("纵摇角速度", "pitch rate")
    VesselMetricId.YAW_RATE -> os.t("艏摇角速度", "yaw rate")
    VesselMetricId.CURRENT_SET -> os.t("流向", "current set")
    VesselMetricId.CURRENT_DRIFT -> os.t("流速", "current drift")
    VesselMetricId.XTE -> os.t("横向偏差", "cross-track error")
    VesselMetricId.WAYPOINT_BEARING -> os.t("目标方位", "waypoint bearing")
    VesselMetricId.WAYPOINT_DISTANCE -> os.t("目标距离", "waypoint distance")
    VesselMetricId.DESTINATION_WAYPOINT -> os.t("目标航点", "destination waypoint")
    VesselMetricId.TOTAL_LOG -> os.t("总航程", "total log")
    VesselMetricId.TRIP_LOG -> os.t("本次航程", "trip log")
    VesselMetricId.VMG_WIND -> os.t("迎风有效速度", "VMG to wind")
    VesselMetricId.VMC_WAYPOINT -> os.t("朝目标有效速度", "VMC to waypoint")
    VesselMetricId.MOTION_SCORE -> os.t("运动强度", "motion score")
    VesselMetricId.ROLL_PERIOD -> os.t("横摇周期", "roll period")
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
        MenuRow(os.t("传感器与船体安装", "sensors & vessel mounting"), os.t("在仪表中确认安装、查看来源和实时姿态", "confirm mounting and see sources and live attitude in instruments"), "data") { os.open("instruments") }
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

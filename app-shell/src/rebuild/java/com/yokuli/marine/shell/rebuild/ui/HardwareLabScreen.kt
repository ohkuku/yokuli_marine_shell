package com.yokuli.marine.shell.rebuild.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.hardware.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val labJson = GsonBuilder().setPrettyPrinting().create()

/** 始终占用自己的布局空间，不把模拟身份盖在海图按钮上，也不生成自己的时钟。 */
@Composable fun HardwareModeBanner(os: OsStore) {
    val service = os.marine?.system?.hardwareLab ?: return
    val state by service.state.collectAsState()
    if (state.mode == HardwareMode.REAL) return
    val c = LocalMetro.current
    val insets = LocalShellHorizontalInsets.current
    Row(Modifier.fillMaxWidth().background(c.fg).clickable {
        if (os.page != "hardware_lab") os.shell.openLinked("hardware_lab")
    }.padding(start = insets.pageStart, end = insets.pageEnd, top = 5.dp, bottom = 5.dp)) {
        Label(if (state.mode == HardwareMode.REPLAY) os.t("录像回放", "REPLAY") else os.t("模拟环境", "SIMULATION"), 12, c.bg, Modifier.weight(1f))
        Label(if (state.paused) os.t("已暂停", "Paused") else "${state.rate.toInt()}×", 12, c.bg)
    }
}

/** Core 是场景、时钟、设备及文件的唯一所有者；离开此页面不会取消已提交的操作。 */
@Composable fun HardwareLabScreen(os: OsStore) {
    val service = os.marine?.system?.hardwareLab
    if (service == null) { Column { PageHeader(os, os.t("演练室", "Hardware Lab")); PageBody { Label(os.t("正在连接系统…", "Connecting to system…"), 14) } }; return }
    val state by service.state.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<HardwareLabCommand?>(null) }
    var selectedDevice by rememberSaveable { mutableStateOf<String?>(null) }
    var exportRecording by rememberSaveable { mutableStateOf<String?>(null) }
    var recordName by rememberSaveable { mutableStateOf("") }
    var scenarioText by rememberSaveable { mutableStateOf("") }
    var scenarioDirty by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.ready, state.scenario) { if (state.ready && !scenarioDirty) scenarioText = labJson.toJson(state.scenario) }
    fun submit(command: HardwareLabCommand) {
        if (busy || !state.ready) return
        busy = true; error = null; status = null
        os.scope.launch {
            try {
                val result = service.execute(command)
                if (!result.accepted) error = hardwareResultMessage(os, result)
                else {
                    status = hardwareResultMessage(os, result)
                    if (command.action in setOf(LabAction.SAVE_SCENARIO, LabAction.IMPORT_SCENARIO)) scenarioDirty = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: os.t("操作未完成，请查看系统状态", "Operation did not complete. Review system state.") }
            finally { busy = false }
        }
    }
    fun updateScenario(text: String) { scenarioText = text; scenarioDirty = true }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) { busy = true; error = null; os.scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    requireNotNull(os.context.contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
                        val out = StringBuilder(); val buffer = CharArray(4096)
                        while (true) { val count = reader.read(buffer); if (count < 0) break; out.append(buffer, 0, count); require(out.length <= 256_000) { "Scenario exceeds 256 KiB" } }
                        out.toString()
                    }
                }
                // 导入先成为草稿。用户确认保存后 Core 校验并持久化，绝不立即更改正在运行的设备。
                require(org.json.JSONObject(text).optInt("version", 1) == 1) { "Unsupported scenario version" }
                updateScenario(text); advanced = true; status = os.t("已读入草稿，请检查后保存", "Draft imported. Review and save it.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: os.t("无法读取场景", "Could not read scenario") }
            finally { busy = false }
        } }
    }
    val exportScenario = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && !busy) { val text = scenarioText; busy = true; error = null; os.scope.launch {
            try { writeDocument(os, uri) { it.write(text.toByteArray(Charsets.UTF_8)) }; status = os.t("场景已导出", "Scenario exported") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: os.t("导出失败", "Export failed") }
            finally { busy = false }
        } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        val id = exportRecording; exportRecording = null
        if (uri != null && id != null && !busy) { busy = true; error = null; os.scope.launch {
            try {
                writeDocument(os, uri) { stream ->
                    var offset = 0L
                    do { val chunk = service.readRecording(id, offset, 48_000); stream.write(chunk.text.toByteArray(Charsets.UTF_8))
                        check(chunk.end || chunk.nextOffset > offset) { "Recording cursor did not advance" }; offset = chunk.nextOffset
                    } while (!chunk.end)
                }; status = os.t("录像已导出", "Recording exported")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: os.t("录像导出失败", "Could not export recording") }
            finally { busy = false }
        } }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.t("演练室", "Hardware Lab"))
        if (busy || !state.ready) Label(os.t("系统正在处理…", "System is working…"), 13, modifier = Modifier.padding(start = LocalShellHorizontalInsets.current.pageStart, end = LocalShellHorizontalInsets.current.pageEnd))
        (error ?: state.error ?: status)?.let { Label(it, 13, modifier = Modifier.padding(start = LocalShellHorizontalInsets.current.pageStart, end = LocalShellHorizontalInsets.current.pageEnd, top = 4.dp, bottom = 4.dp)) }
        Pivot(listOf(os.t("场景", "Scenario"), os.t("设备", "Devices"), os.t("录像", "Recordings"))) { tab -> PageBody {
            when (tab) {
                0 -> {
                    AppSection(when (state.mode) { HardwareMode.REAL -> os.t("真实设备", "Real devices"); HardwareMode.SIMULATION -> os.t("模拟航行", "Simulated passage"); HardwareMode.REPLAY -> os.t("系统回放", "System replay") },
                        os.t("演练使用独立资料，真实航行会保留。切换前请结束记录、守锚和导航。", "Practice has its own data. Finish recording, anchor watch and navigation before switching."))
                    if (state.mode == HardwareMode.REAL) MetroButton(os.t("进入模拟", "Enter simulation"), { confirmation = HardwareLabCommand(LabAction.ENTER_SIMULATION) }, primary = true, enabled = state.ready && !busy)
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetroButton(if (state.paused) os.t("继续", "Resume") else os.t("暂停", "Pause"), { submit(HardwareLabCommand(if (state.paused) LabAction.RESUME else LabAction.PAUSE)) }, Modifier.weight(1f), enabled = !busy && state.ready)
                            MetroButton(os.t("前进 1 秒", "Step 1s"), { submit(HardwareLabCommand(LabAction.STEP)) }, Modifier.weight(1f), enabled = state.paused && !busy && state.ready)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1, 2, 10, 60).forEach { rate ->
                            MetroButton("${rate}×", { submit(HardwareLabCommand(LabAction.SET_RATE, rate = rate.toDouble())) }, Modifier.weight(1f), primary = state.rate == rate.toDouble(), enabled = !busy && state.ready)
                        } }
                        Label(DateFormat.getDateTimeInstance().format(Date(state.utcMillis)), 13, LocalMetro.current.muted)
                        if (state.mode == HardwareMode.SIMULATION) Label(os.t("首次使用：继续时间，在数据中心选择模拟 GNSS，再确认手机固定零点。系统不会替你改选来源。", "First run: resume time, select simulated GNSS in Data Center and confirm the fixed mounting zero. Sources are never switched for you."), 13, LocalMetro.current.muted)
                        if (state.mode == HardwareMode.REPLAY) Label("${state.replayPositionMillis / 1000}s / ${state.replayDurationMillis / 1000}s", 14)
                        MetroButton(os.t("回到真实设备", "Return to real devices"), { confirmation = HardwareLabCommand(LabAction.RETURN_REAL) }, enabled = !busy && state.ready)
                    }
                    AppSection(os.t("下次模拟的场景", "Scenario for the next simulation"), os.t("保存不会改变正在运行的航行。重新进入模拟后生效。", "Saving does not alter the current passage. Re-enter simulation to apply it."))
                    Toggle(os.t("完整 JSON 编辑", "Full JSON editor"), advanced, onChange = { advanced = it })
                    if (advanced) Field(os.t("场景、航点、AIS 与定时事件", "Scenario, waypoints, AIS & timed events"), scenarioText, ::updateScenario, multiline = true)
                    else ScenarioFields(os, scenarioText, ::updateScenario)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetroButton(os.t("保存场景", "Save scenario"), { submit(HardwareLabCommand(LabAction.IMPORT_SCENARIO, scenarioJson = scenarioText)) }, Modifier.weight(1f), primary = true, enabled = scenarioDirty && !busy && state.ready)
                        MetroButton(os.t("撤销编辑", "Revert edits"), { scenarioText = labJson.toJson(state.scenario); scenarioDirty = false }, Modifier.weight(1f), enabled = scenarioDirty && !busy)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetroButton(os.t("导入", "Import"), { import.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f), enabled = !busy)
                        MetroButton(os.t("导出", "Export"), { exportScenario.launch("yokuli-scenario.json") }, Modifier.weight(1f), enabled = !busy && scenarioText.isNotBlank())
                    }
                }
                1 -> {
                    AppSection(os.t("设备总线", "Device bus"), os.t("查看实际接入状态；在模拟中可插拔设备并施加故障，回放遵循录像中的事件。", "View connected devices. Attach, detach or inject faults in simulation; replay follows recorded events."))
                    state.devices.forEach { device ->
                        Toggle(device.name, device.attached, "${device.kind} · ${device.frames} ${os.t("帧", "frames")}", enabled = state.ready && !busy && state.mode == HardwareMode.SIMULATION) {
                            submit(HardwareLabCommand(if (it) LabAction.ATTACH else LabAction.DETACH, deviceId = device.id))
                        }
                        val faults = state.faults.filter { it.deviceId == device.id }
                        if (state.mode == HardwareMode.SIMULATION && HardwareFaultPolicy.allowed(device.id).isNotEmpty()) MenuRow(os.t("故障", "Fault"), faults.joinToString { faultTitle(os, it.type) }.ifEmpty { os.t("无", "None") }, "data") { selectedDevice = device.id }
                    }
                    if (state.devices.isEmpty()) Label(os.t("暂无已注册设备", "No registered devices"), 14)
                    PowerAndStorage(os, state, busy, ::submit)
                }
                2 -> {
                    AppSection(os.t("系统录像", "System recordings"), os.t("记录输入与系统事件，可导出或重放到独立环境。", "Record device input and system events; export or replay in an isolated environment."))
                    if (state.recordingId != null) {
                        Label(os.t("正在记录", "Recording"), 17)
                        MetroButton(os.t("完成录像", "Finish recording"), { submit(HardwareLabCommand(LabAction.STOP_RECORDING)) }, primary = true, enabled = !busy && state.ready)
                    } else {
                        Field(os.t("录像名称", "Recording name"), recordName, { recordName = it.take(80) })
                        MetroButton(os.t("开始录像", "Start recording"), { submit(HardwareLabCommand(LabAction.START_RECORDING, name = recordName)) }, primary = true, enabled = !busy && state.ready)
                    }
                    state.recordings.sortedByDescending { it.startedAtUtc }.forEach { recording ->
                        AppSection(recording.name, "${recording.durationMillis / 1000}s · ${recording.frames} ${os.t("帧", "frames")} · ${recording.bytes / 1024} KiB")
                        Label(DateFormat.getDateTimeInstance().format(Date(recording.startedAtUtc)), 12, LocalMetro.current.muted)
                        recording.error?.let { Label(it, 13) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetroButton(os.t("回放", "Replay"), { confirmation = HardwareLabCommand(LabAction.ENTER_REPLAY, recordingId = recording.id) }, Modifier.weight(1f), enabled = recording.id != state.recordingId && !busy && state.ready)
                            MetroButton(os.t("导出", "Export"), { exportRecording = recording.id; export.launch("yokuli-${recording.id}.jsonl") }, Modifier.weight(1f), enabled = recording.id != state.recordingId && !busy && state.ready)
                            MetroButton(os.t("删除", "Delete"), { confirmation = HardwareLabCommand(LabAction.DELETE_RECORDING, recordingId = recording.id) }, Modifier.weight(1f), enabled = recording.id != state.recordingId && state.replayId != recording.id && !busy && state.ready)
                        }
                    }
                }
            }
        } }
    }
    confirmation?.let { command -> AppDialog(onDismissRequest = { if (!busy) confirmation = null }) { AppDialogSurface {
        AppDialogTitle(if (command.action == LabAction.DELETE_RECORDING) os.t("删除录像？", "Delete recording?") else os.t("切换运行环境？", "Switch operating environment?"))
        Label(if (command.action == LabAction.DELETE_RECORDING) os.t("这份录像将从设备删除。", "This recording will be removed from the device.") else os.t("系统会重新连接设备。真实与演练资料分开保留；正在进行的航行任务必须先结束。", "The system reconnects devices. Real and practice data remain separate; active sailing tasks must be finished first."), 14)
        MetroButton(os.t("确认", "Confirm"), { confirmation = null; submit(command) }, primary = true, enabled = !busy)
        MetroButton(os.t("取消", "Cancel"), { confirmation = null }, enabled = !busy)
    } } }
    selectedDevice?.let { id -> FaultPicker(os, id, busy, onDismiss = { selectedDevice = null }) { command -> selectedDevice = null; submit(command) } }
}

private suspend fun writeDocument(os: OsStore, uri: Uri, write: suspend (java.io.OutputStream) -> Unit) = withContext(Dispatchers.IO) {
    try { requireNotNull(os.context.contentResolver.openOutputStream(uri, "wt")) { "Cannot open destination" }.use { write(it) } }
    catch (failure: Exception) { runCatching { android.provider.DocumentsContract.deleteDocument(os.context.contentResolver, uri) }; throw failure }
}

@Composable private fun ScenarioFields(os: OsStore, text: String, change: (String) -> Unit) {
    val parsed = remember(text) { runCatching { org.json.JSONObject(text) }.getOrNull() }
    if (parsed == null) { Label(os.t("请在 JSON 编辑中修正场景格式，或重新导入。", "Fix the scenario in the JSON editor or import it again."), 14); return }
    fun set(key: String, value: String, numeric: Boolean) {
        val updated = org.json.JSONObject(parsed.toString())
        // 中间输入允许为空或负号；Core 保存时统一验证完整场景，不偷偷改成零。
        updated.put(key, if (numeric) value.toDoubleOrNull() ?: value else value); change(updated.toString(2))
    }
    Field(os.t("名称", "Name"), parsed.optString("name"), { set("name", it.take(80), false) })
    val fields = listOf(
        Triple("latitude", "纬度 · °", "Latitude · °"), Triple("longitude", "经度 · °", "Longitude · °"),
        Triple("speedKnots", "船速 · kn", "Speed · kn"), Triple("courseDegrees", "航迹向 · °", "Course · °"),
        Triple("headingDegrees", "船首向 · °", "Heading · °"), Triple("accuracyMeters", "定位误差 · m", "Position accuracy · m"),
        Triple("heelDegrees", "横倾 · °", "Heel · °"), Triple("pitchDegrees", "纵倾 · °", "Pitch · °"),
        Triple("rollPeriodSeconds", "摇摆周期 · s", "Roll period · s"), Triple("depthMeters", "水深 · m", "Depth · m"),
        Triple("windSpeedKnots", "风速 · kn", "Wind speed · kn"), Triple("windDirectionDegrees", "风向 · °", "Wind direction · °"),
        Triple("pressureHpa", "气压 · hPa", "Pressure · hPa"), Triple("waterTemperatureC", "水温 · °C", "Water temperature · °C"))
    fields.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { (key, zh, en) ->
        Box(Modifier.weight(1f)) { Field(os.t(zh, en), parsed.optString(key), { set(key, it, true) }, number = true) }
    } } }
    Label(os.t("航点、AIS 船舶与定时事件在完整 JSON 中维护；这些参数使用注明的标准单位。", "Edit waypoints, AIS vessels and timed events in the full JSON editor. Parameters use the stated canonical units."), 13, LocalMetro.current.muted)
}

@Composable private fun FaultPicker(os: OsStore, id: String, busy: Boolean, onDismiss: () -> Unit, submit: (HardwareLabCommand) -> Unit) {
    val allowed = remember(id) { HardwareFaultPolicy.allowed(id).toList() }
    if (allowed.isEmpty()) return
    var selected by remember(id) { mutableStateOf(allowed.first()) }
    var magnitude by remember(id, selected) { mutableStateOf(HardwareFaultPolicy.defaultMagnitude(selected).toString()) }
    val range = HardwareFaultPolicy.magnitudeRange(selected)
    val value = if (range == null) 0.0 else magnitude.toDoubleOrNull()
    val valid = value != null && value.isFinite() && (range == null || value in range)
    AppDialog(onDismissRequest = onDismiss) { AppDialogSurface {
        AppDialogTitle(os.t("设备故障", "Device fault"))
        Column(Modifier.heightIn(max = 320.dp).then(Modifier)) {
            androidx.compose.foundation.lazy.LazyColumn { items(allowed.size) { index -> val fault = allowed[index]
                ChoiceRow(faultTitle(os, fault), selected == fault) { selected = fault }
            } }
        }
        if (range != null) Field(os.t("幅度", "Magnitude") + " · " + HardwareFaultPolicy.magnitudeUnit(selected), magnitude, { magnitude = it }, number = true)
        MetroButton(os.t("施加故障", "Apply fault"), { submit(HardwareLabCommand(LabAction.SET_FAULT, deviceId = id, fault = HardwareFault(id, selected, requireNotNull(value)))) }, primary = true, enabled = !busy && valid)
        MetroButton(os.t("清除故障", "Clear faults"), { submit(HardwareLabCommand(LabAction.CLEAR_FAULT, deviceId = id)) }, enabled = !busy)
    } }
}
private fun faultTitle(os: OsStore, fault: DeviceFault): String = when (fault) {
    DeviceFault.LOST -> os.t("失去信号", "Signal lost"); DeviceFault.FROZEN -> os.t("数据冻结", "Frozen data")
    DeviceFault.STALE -> os.t("过期时间戳", "Stale timestamps"); DeviceFault.INACCURATE -> os.t("定位误差", "Position inaccuracy")
    DeviceFault.TELEPORT -> os.t("船位跳变", "Position jump"); DeviceFault.MAGNETIC_INTERFERENCE -> os.t("磁场干扰", "Magnetic interference")
    DeviceFault.HEADING_JUMP -> os.t("艏向跳变", "Heading jump"); DeviceFault.MISSING -> os.t("缺少读数", "Missing reading")
    DeviceFault.ZERO -> os.t("零值", "Zero reading"); DeviceFault.INTERMITTENT -> os.t("间歇失联", "Intermittent signal")
    DeviceFault.PACKET_LOSS -> os.t("丢包", "Packet loss"); DeviceFault.LATENCY -> os.t("传输延迟", "Transport latency")
    DeviceFault.DISCONNECTED -> os.t("连接断开", "Disconnected"); DeviceFault.AIS_CONFLICT -> os.t("AIS 身份冲突", "AIS identity conflict")
    DeviceFault.AIS_FRAGMENTED -> os.t("AIS 分片", "AIS fragmentation")
}

@Composable private fun PowerAndStorage(os: OsStore, state: HardwareLabSnapshot, busy: Boolean, submit: (HardwareLabCommand) -> Unit) {
    val power = state.power
    var percent by remember(power.percent) { mutableStateOf(power.percent.toString()) }
    var thermal by remember(power.thermal) { mutableStateOf(power.thermal.toString()) }
    var external by remember(power.external) { mutableStateOf(power.external) }
    var charging by remember(power.charging) { mutableStateOf(power.charging) }
    var screen by remember(power.screenOn) { mutableStateOf(power.screenOn) }
    val enabled = state.ready && !busy && state.mode != HardwareMode.REAL
    AppSection(os.t("电源与存储", "Power & storage"), "${state.storage.freeBytes / 1_048_576} / ${state.storage.totalBytes / 1_048_576} MiB ${os.t("可用", "free")}")
    if (state.mode == HardwareMode.REAL) { Label(os.t("电量 ${power.percent}% · 真实设备状态", "Battery ${power.percent}% · Actual device state"), 14); return }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { Field(os.t("电量 · %", "Battery · %"), percent, { percent = it }, number = true) }
        Box(Modifier.weight(1f)) { Field(os.t("温控等级 · 0–6", "Thermal level · 0–6"), thermal, { thermal = it }, number = true) }
    }
    Toggle(os.t("外部电源", "External power"), external, enabled = enabled, onChange = { external = it })
    Toggle(os.t("正在充电", "Charging"), charging, enabled = enabled, onChange = { charging = it })
    Toggle(os.t("屏幕点亮", "Screen on"), screen, enabled = enabled, onChange = { screen = it })
    MetroButton(os.t("应用电源状态", "Apply power state"), { submit(HardwareLabCommand(LabAction.SET_POWER, power = HardwarePower(requireNotNull(percent.toIntOrNull()), external, charging, requireNotNull(thermal.toIntOrNull()), screen, true))) }, enabled = enabled && percent.toIntOrNull() in 0..100 && thermal.toIntOrNull() in 0..6)
    StorageFault.entries.forEach { fault -> ChoiceRow(when (fault) {
        StorageFault.NONE -> os.t("存储正常", "Storage healthy"); StorageFault.FULL -> os.t("空间耗尽", "Storage full")
        StorageFault.READ_ONLY -> os.t("只读存储", "Read-only storage"); StorageFault.CORRUPT -> os.t("数据损坏", "Corrupt data")
        StorageFault.PERMISSION_REVOKED -> os.t("访问权限收回", "Access revoked")
    }, fault == state.storage.fault, enabled = enabled) { submit(HardwareLabCommand(LabAction.SET_STORAGE, storageFault = fault)) } }
}


private fun hardwareResultMessage(os: OsStore, result: HardwareLabResult): String = when (result.message ?: result.code) {
    "OK" -> os.t("已应用", "Applied")
    "RESTARTING", "Core is changing environments" -> os.t("正在切换运行环境，系统会自动重新连接…", "Switching environments. The system will reconnect…")
    "END_ACTIVE_ANCHOR_VOYAGE_AND_NAVIGATION_FIRST" -> os.t("请先结束当前守锚、航行记录和导航，再切换环境。", "Finish current anchor watch, voyage recording and navigation before switching.")
    "STOP_RECORDING_BEFORE_SWITCH" -> os.t("请先完成正在进行的系统录像。", "Finish the current system recording first.")
    "STOP_RECORDING_BEFORE_STORAGE_RECOVERY" -> os.t("请先完成系统录像，再恢复存储。", "Finish system recording before recovering storage.")
    "CORE_RECOVERY_PENDING" -> os.t("系统正在恢复资料，请稍后再切换。", "The system is recovering data. Please wait before switching.")
    "SIMULATION_REQUIRED" -> os.t("这项操作只适用于模拟环境。", "This operation is available in simulation only.")
    "VIRTUAL_ENVIRONMENT_REQUIRED" -> os.t("请先进入模拟或回放环境。", "Enter simulation or replay first.")
    "REPLAY_FINISHED" -> os.t("录像已播放完毕，可从录像列表重新开始。", "Replay has finished. Start it again from Recordings.")
    "RECORDING_HAS_NO_INPUT" -> os.t("这份录像还没有设备输入，无法回放。", "This recording has no device input to replay.")
    "REPLAY_IN_USE" -> os.t("这份录像正在使用，请先退出回放。", "This recording is in use. Leave replay first.")
    "REQUEST_ID_CONFLICT" -> os.t("这个操作编号已用于另一项操作，请重新查看当前状态。", "This request ID belongs to a different operation. Review current state.")
    else -> result.message ?: result.code
}

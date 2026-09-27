package com.yokuli.runtime.contract.hardware

import kotlinx.coroutines.flow.StateFlow

/** 虚拟运行环境的用户入口。设备输入、时钟和故障均由 Core 执行，页面不生成数据。 */
interface HardwareLabService {
    val state: StateFlow<HardwareLabSnapshot>
    suspend fun execute(command: HardwareLabCommand): HardwareLabResult
    /** 分页读取系统录像，不把整个文件跨 Binder 放进内存。 */
    suspend fun readRecording(id: String, offset: Long, limit: Int = 48_000): RecordingChunk
}

enum class HardwareMode { REAL, SIMULATION, REPLAY }
enum class LabAction {
    ENTER_SIMULATION, ENTER_REPLAY, RETURN_REAL, PAUSE, RESUME, SET_RATE, STEP,
    SAVE_SCENARIO, IMPORT_SCENARIO, ATTACH, DETACH, SET_FAULT, CLEAR_FAULT,
    START_RECORDING, STOP_RECORDING, DELETE_RECORDING, SET_POWER, SET_STORAGE,
}

/** 命令均返回真实结果。mode 切换要求现有守锚/记录/导航已结束，不隐式停止用户任务。 */
data class HardwareLabCommand(
    val action: LabAction = LabAction.PAUSE,
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val deviceId: String = "",
    val recordingId: String = "",
    val rate: Double = 1.0,
    val stepMillis: Long = 1_000,
    val name: String = "",
    val scenario: HardwareScenario? = null,
    val scenarioJson: String? = null,
    val fault: HardwareFault? = null,
    val power: HardwarePower? = null,
    val storageFault: StorageFault = StorageFault.NONE,
)
data class HardwareLabResult(val accepted: Boolean, val code: String = "OK", val message: String? = null)

data class HardwareLabSnapshot(
    val ready: Boolean = false,
    val mode: HardwareMode = HardwareMode.REAL,
    val epoch: Long = 0,
    val utcMillis: Long = 0,
    val elapsedMillis: Long = 0,
    val capturedHostElapsedMillis: Long = 0,
    val paused: Boolean = false,
    val rate: Double = 1.0,
    val scenario: HardwareScenario = HardwareScenario(),
    val devices: List<LabDevice> = emptyList(),
    val faults: List<HardwareFault> = emptyList(),
    val recordings: List<HardwareRecording> = emptyList(),
    val recordingId: String? = null,
    val replayId: String? = null,
    val replayPositionMillis: Long = 0,
    val replayDurationMillis: Long = 0,
    val eventCursor: Int = 0,
    val power: HardwarePower = HardwarePower(),
    val storage: HardwareStorage = HardwareStorage(),
    val error: String? = null,
    val clock: com.yokuli.runtime.contract.time.ClockSnapshot = com.yokuli.runtime.contract.time.ClockSnapshot(),
)
data class LabDevice(val id: String, val name: String, val kind: String, val attached: Boolean, val frames: Long = 0)

/** 单位为度、节、米、hPa；每个事件按场景相对毫秒执行一次，时间暂停不会偷偷继续。 */
data class HardwareScenario(
    val version: Int = 1,
    val name: String = "Harbour passage",
    val latitude: Double = -36.83,
    val longitude: Double = 174.79,
    val speedKnots: Double = 6.0,
    val courseDegrees: Double = 55.0,
    val headingDegrees: Double = 55.0,
    val accuracyMeters: Double = 3.0,
    val heelDegrees: Double = 8.0,
    val pitchDegrees: Double = 2.0,
    val rollPeriodSeconds: Double = 7.5,
    val depthMeters: Double = 12.4,
    val windSpeedKnots: Double = 17.0,
    val windDirectionDegrees: Double = 230.0,
    val pressureHpa: Double = 1012.4,
    val waterTemperatureC: Double = 17.3,
    val waypoints: List<ScenarioPoint> = emptyList(),
    val targets: List<ScenarioAisTarget> = emptyList(),
    val events: List<ScenarioEvent> = emptyList(),
)
data class ScenarioPoint(val latitude: Double, val longitude: Double)
data class ScenarioAisTarget(val mmsi: Int, val name: String = "", val latitude: Double, val longitude: Double,
    val speedKnots: Double = 4.0, val courseDegrees: Double = 180.0, val headingDegrees: Double = 180.0)
enum class ScenarioAction { SET_SPEED, SET_COURSE, SET_WIND, SET_DEPTH, SET_PRESSURE, ATTACH, DETACH, FAULT, CLEAR_FAULT, AIS_SPAWN, POWER, STORAGE }
data class ScenarioEvent(val atMillis: Long, val action: ScenarioAction, val deviceId: String = "", val value: Double = 0.0,
    val fault: HardwareFault? = null, val target: ScenarioAisTarget? = null,
    val power: HardwarePower? = null, val storageFault: StorageFault = StorageFault.NONE)

enum class DeviceFault { LOST, FROZEN, STALE, INACCURATE, TELEPORT, MAGNETIC_INTERFERENCE, HEADING_JUMP, MISSING, ZERO, INTERMITTENT, PACKET_LOSS, LATENCY, DISCONNECTED, AIS_CONFLICT, AIS_FRAGMENTED }
data class HardwareFault(val deviceId: String, val type: DeviceFault, val magnitude: Double = 0.0)
data class HardwarePower(val percent: Int = 100, val external: Boolean = true, val charging: Boolean = true,
    val thermal: Int = 0, val screenOn: Boolean = true, val simulated: Boolean = false)
enum class StorageFault { NONE, FULL, READ_ONLY, CORRUPT, PERMISSION_REVOKED }
data class HardwareStorage(val freeBytes: Long = 0, val totalBytes: Long = 0, val fault: StorageFault = StorageFault.NONE)
data class HardwareRecording(val id: String, val name: String, val startedAtUtc: Long, val durationMillis: Long = 0,
    val frames: Long = 0, val bytes: Long = 0, val complete: Boolean = false, val error: String? = null)
data class RecordingChunk(val text: String, val nextOffset: Long, val end: Boolean)

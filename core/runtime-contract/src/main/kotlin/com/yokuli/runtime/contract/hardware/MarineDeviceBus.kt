package com.yokuli.runtime.contract.hardware

import com.yokuli.runtime.contract.device.DeviceBackend
import com.yokuli.runtime.contract.device.DeviceKind
import com.yokuli.runtime.contract.time.MarineTime
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/** 中文：总线帧保存测量时刻，收包/回放时刻不能把陈旧观测重新变成新数据。 */
enum class HardwareSensorType { ROTATION_QUATERNION, ACCELEROMETER, LINEAR_ACCELERATION, GYROSCOPE, MAGNETOMETER, LEGACY_ORIENTATION }

/** 中文：有界、可持久化的 HAL 数据包。kind 决定有效字段；四元数按 w/x/y/z，向量为 SI 单位。 */
data class HardwarePayload(
    val kind: DeviceKind = DeviceKind.GNSS,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sogKnots: Double? = null,
    val cogTrueDegrees: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Double? = null,
    val satellites: Int? = null,
    val sensorType: HardwareSensorType? = null,
    val values: List<Double> = emptyList(),
    val sensorAccuracy: Int = 3,
    val pressureHpa: Double? = null,
    val sentence: String? = null,
    val peer: String = "",
    val requireChecksum: Boolean = true,
) {
    fun isValid(): Boolean = when (kind) {
        DeviceKind.GNSS -> latitude?.let { it.isFinite() && it in -90.0..90.0 } == true &&
            longitude?.let { it.isFinite() && it in -180.0..180.0 } == true &&
            listOfNotNull(sogKnots, cogTrueDegrees, altitudeMeters, accuracyMeters).all { it.isFinite() } &&
            (sogKnots == null || sogKnots >= 0) && (accuracyMeters == null || accuracyMeters >= 0)
        DeviceKind.IMU -> sensorType != null && values.size == (if (sensorType == HardwareSensorType.ROTATION_QUATERNION) 4 else 3) &&
            values.all { it.isFinite() } && (sensorType != HardwareSensorType.ROTATION_QUATERNION || values.sumOf { it * it } in .5..1.5)
        DeviceKind.PRESSURE -> pressureHpa?.let { it.isFinite() && it in 300.0..1_200.0 } == true
        DeviceKind.NMEA_CONNECTION -> !sentence.isNullOrBlank() && sentence.length <= 4_096 && peer.length <= 256
    }
}

data class HardwareDeviceSpec(
    val id: String = "",
    val name: String = "",
    val kind: DeviceKind = DeviceKind.GNSS,
    val backend: DeviceBackend = DeviceBackend.REAL,
    val driver: String = "",
    val capabilities: List<String> = emptyList(),
)

data class HardwareDevice(
    val spec: HardwareDeviceSpec = HardwareDeviceSpec(),
    val generation: Long = 0,
    val attached: Boolean = true,
    val lastMeasuredElapsedMillis: Long? = null,
    val lastReceivedElapsedMillis: Long? = null,
    val reason: String? = null,
)

data class HardwareBusSnapshot(
    val backend: DeviceBackend = DeviceBackend.REAL,
    val epoch: Long = 1,
    val revision: Long = 0,
    val devices: List<HardwareDevice> = emptyList(),
)

data class HardwareFrame(
    val deviceId: String = "",
    val backend: DeviceBackend = DeviceBackend.REAL,
    val epoch: Long = 0,
    val generation: Long = 0,
    val sequence: Long = 0,
    val utcMillis: Long = 0,
    val measuredElapsedMillis: Long = 0,
    val receivedElapsedMillis: Long = 0,
    val payload: HardwarePayload = HardwarePayload(),
)

/**
 * 中文：Core 唯一设备总线。Android 驱动、模拟器、回放器使用同一入口；业务不拥有模拟副本。
 * 切换后端会增加 epoch、失效所有设备并同步通知消费者。旧驱动/旧回放任务的迟到帧不能跨代。
 * 帧订阅供生产驱动同步消费，frames 供有界记录；高频事件不重建整页领域状态。
 */
object MarineDeviceBus {
    private val lock = Any()
    private val devices = linkedMapOf<String, HardwareDevice>()
    private val _state = MutableStateFlow(HardwareBusSnapshot())
    val state = _state.asStateFlow()
    private val _frames = MutableSharedFlow<HardwareFrame>(extraBufferCapacity = 4_096, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val frames = _frames.asSharedFlow()
    private var sequence = 0L
    private var generation = 0L
    private data class Listener(val kind: DeviceKind, val frame: (HardwareFrame) -> Unit, val reset: (String?) -> Unit)
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val backendListeners = CopyOnWriteArrayList<(HardwareBusSnapshot) -> Unit>()
    private val catalogListeners = CopyOnWriteArrayList<(HardwareBusSnapshot) -> Unit>()
    private data class Recorder(val frame:(HardwareFrame)->Unit,val catalog:(HardwareBusSnapshot)->Unit)
    private val recorders=CopyOnWriteArrayList<Recorder>()

    fun subscribe(kind: DeviceKind, onFrame: (HardwareFrame) -> Unit, onReset: (String?) -> Unit): AutoCloseable {
        val listener = Listener(kind, onFrame, onReset)
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /** 中文：后端变化回调只处理资源释放，不从回调中自动恢复真实采集。 */
    fun onBackendChanged(listener: (HardwareBusSnapshot) -> Unit): AutoCloseable {
        backendListeners += listener
        return AutoCloseable { backendListeners.remove(listener) }
    }

    /**
     * 中文：录制用非合流设备边界流。注册时同步给出初始目录，之后只在 attach/detach/切换世界时调用。
     * 回调位于总线顺序锁内，只允许投递有界队列；禁止 IO、阻塞或回调总线。
     */
    fun onCatalogChanged(listener: (HardwareBusSnapshot) -> Unit): AutoCloseable {
        synchronized(lock) { catalogListeners += listener; listener(_state.value) }
        return AutoCloseable { synchronized(lock) { catalogListeners.remove(listener) } }
    }

    /**
     * 中文：录制专用同一顺序入口。初始目录与之后的帧/热插拔边界原子排序，避免异步 Flow 合流或乱序。
     * 回调只投递有界队列；不得执行文件 IO、阻塞、调用总线或领域服务。
     */
    fun subscribeRecorder(onFrame:(HardwareFrame)->Unit,onCatalog:(HardwareBusSnapshot)->Unit):AutoCloseable {
        val recorder=Recorder(onFrame,onCatalog)
        synchronized(lock){recorders+=recorder;onCatalog(_state.value)}
        return AutoCloseable{synchronized(lock){recorders.remove(recorder)}}
    }
    private fun publishCatalog(){
        val snapshot=_state.value
        catalogListeners.forEach{it(snapshot)}
        recorders.forEach{it.catalog(snapshot)}
    }

    fun enterBackend(backend: DeviceBackend): Long {
        val next = synchronized(lock) {
            devices.clear()
            _state.value.copy(backend = backend, epoch = _state.value.epoch + 1, revision = _state.value.revision + 1, devices = emptyList()).also { _state.value = it; publishCatalog() }
        }
        listeners.forEach { it.reset(null) }
        backendListeners.forEach { it(next) }
        return next.epoch
    }

    fun attach(spec: HardwareDeviceSpec): HardwareDevice {
        require(spec.id.isNotBlank() && spec.id.length <= 128 && spec.name.length <= 160)
        require(spec.capabilities.size <= 64 && spec.capabilities.all { it.length <= 80 })
        return synchronized(lock) {
            check(spec.backend == _state.value.backend) { "Device backend is not active" }
            val previous = devices[spec.id]
            require(previous!=null||devices.size<256){"Device catalog is full"}
            if (previous?.attached == true && previous.spec == spec) return@synchronized previous
            val device = HardwareDevice(spec, ++generation)
            devices[spec.id] = device
            publishState()
            publishCatalog()
            device
        }
    }

    fun detach(id: String, reason: String = "Device detached", notifyConsumers: Boolean = true) {
        val kind = synchronized(lock) {
            val previous = devices[id] ?: return
            if(!previous.attached)return
            devices[id] = previous.copy(attached = false, generation = ++generation, reason = reason.take(256))
            publishState()
            publishCatalog()
            previous.spec.kind
        }
        if(notifyConsumers)listeners.filter { it.kind == kind }.forEach { it.reset(id) }
    }

    fun isCurrent(frame: HardwareFrame): Boolean = synchronized(lock) {
        frame.epoch == _state.value.epoch && frame.backend == _state.value.backend &&
            devices[frame.deviceId]?.let { it.attached && it.generation == frame.generation } == true
    }

    fun publish(
        deviceId: String,
        payload: HardwarePayload,
        measuredElapsedMillis: Long = MarineTime.nowElapsedMillis(),
        utcMillis: Long = MarineTime.nowUtcMillis(),
        expectedEpoch: Long? = null,
        expectedGeneration: Long? = null,
    ): Boolean {
        if (!payload.isValid()) return false
        val frame = synchronized(lock) {
            val snapshot = _state.value
            val device = devices[deviceId] ?: return false
            if (!device.attached || device.spec.backend != snapshot.backend || device.spec.kind != payload.kind ||
                expectedEpoch?.let { it != snapshot.epoch } == true || expectedGeneration?.let { it != device.generation } == true) return false
            val received = MarineTime.nowElapsedMillis()
            if (measuredElapsedMillis < 0 || measuredElapsedMillis > received) return false
            val value = HardwareFrame(deviceId, snapshot.backend, snapshot.epoch, device.generation, ++sequence, utcMillis, measuredElapsedMillis, received, payload)
            devices[deviceId] = device.copy(lastMeasuredElapsedMillis = measuredElapsedMillis, lastReceivedElapsedMillis = received)
            // 目录每秒更新；每个原始 IMU 帧仍同步进入订阅者与记录流。
            val publishedAt=snapshot.devices.firstOrNull{it.spec.id==deviceId}?.lastReceivedElapsedMillis
            if(publishedAt==null||received-publishedAt>=1_000)publishState()
            recorders.forEach{it.frame(value)}
            value
        }
        listeners.filter { it.kind == payload.kind }.forEach { it.frame(frame) }
        _frames.tryEmit(frame)
        return true
    }

    private fun publishState() {
        _state.value = _state.value.copy(revision = _state.value.revision + 1, devices = devices.values.toList())
    }
}

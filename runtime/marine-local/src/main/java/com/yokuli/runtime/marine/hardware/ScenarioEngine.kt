package com.yokuli.runtime.marine.hardware

import android.hardware.GeomagneticField
import com.yokuli.runtime.contract.device.DeviceBackend
import com.yokuli.runtime.contract.device.DeviceKind
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.time.MarineTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.*

/** 场景运行位置独立落盘；进程重启不重走出发点，也不重复执行已经过的时序事件。 */
data class ScenarioCheckpoint(
    val elapsedMillis: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val nextWaypoint: Int = 0,
    val speedKnots: Double = 0.0,
    val courseDegrees: Double = 0.0,
    val headingDegrees: Double = 0.0,
    val depthMeters: Double = 0.0,
    val windSpeedKnots: Double = 0.0,
    val pressureHpa: Double = 1013.25,
    val targets: List<ScenarioAisTarget> = emptyList(),
    val eventCursor: Int = 0,
    val faults: List<HardwareFault> = emptyList(),
    val attachedDeviceIds: Set<String> = ScenarioValidation.deviceIds,
    val frozenFrames: List<ScenarioFrozenFrame> = emptyList(),
)

/** FROZEN 的原测量时刻也持久化；重启不允许把冻结的最后一帧刷新为实时。 */
data class ScenarioFrozenFrame(val key: String, val payload: HardwarePayload, val measuredElapsedMillis: Long, val utcMillis: Long)

data class ScenarioEngineState(
    val elapsedMillis: Long = 0,
    val eventCursor: Int = 0,
    val faults: List<HardwareFault> = emptyList(),
    val attachedDeviceIds: Set<String> = emptySet(),
    val frameCounts: Map<String, Long> = emptyMap(),
    val error: String? = null,
)

/**
 * 模拟器是 HAL 后端，不直接改任何仪表读数。GNSS / 原始 IMU / 气压 / NMEA 与真实设备
 * 经过同一总线、解析器、校准、来源仲裁和安全领域。时间来自 MarineTime，暂停无采样。
 */
class ScenarioEngine(
    scope: CoroutineScope,
    initialScenario: HardwareScenario,
    private val onPower: (HardwarePower) -> Unit,
    private val onStorage: (StorageFault) -> Unit,
    private val onError: (String) -> Unit = {},
    initialCheckpoint: ScenarioCheckpoint? = null,
    initialElapsedMillis: Long = 0,
) : AutoCloseable {
    private val lock = Any()
    private var scenario = initialScenario
    private var current = ScenarioCheckpoint()
    private var originElapsed = 0L
    private var closed = false
    private var nextGnss = 0L
    private var nextPressure = 0L
    private var nextNmea = 0L
    private var nextAisStatic = 0L
    private var generated = 0L
    private var fragmentSequence = 0
    private var magneticDeclination = 0.0
    private var nextMagneticEstimate = 0L
    private val counts = mutableMapOf<String, Long>()
    private val lastSamples = linkedMapOf<String, Sample>()
    private val frozen = linkedMapOf<String, Sample>()
    private data class Sample(val payload: HardwarePayload, val measured: Long, val utc: Long)
    private data class Emission(val device: String, val sample: Sample, val due: Long, val epoch: Long, val generation: Long, val order: Long)
    private val pending = PriorityQueue<Emission>(compareBy<Emission> { it.due }.thenBy { it.order })
    private val mutable = MutableStateFlow(ScenarioEngineState())
    val state = mutable.asStateFlow()
    private val job: Job

    init {
        ScenarioValidation.requireValid(initialScenario)
        require(initialElapsedMillis in 0..ScenarioValidation.MAX_DURATION_MILLIS)
        check(MarineDeviceBus.state.value.backend == DeviceBackend.SIMULATED) { "Simulation backend is not active" }
        synchronized(lock) {
            current = initialCheckpoint?.also(::validateCheckpoint) ?: fresh(initialScenario)
            current.frozenFrames.forEach { frozen[it.key] = Sample(it.payload, it.measuredElapsedMillis, it.utcMillis) }
            val target = initialCheckpoint?.elapsedMillis ?: initialElapsedMillis
            originElapsed = MarineTime.nowElapsedMillis() - target
            if (initialCheckpoint == null) advanceTo(target)
            ScenarioValidation.deviceIds.forEach(::reconcileAttachment)
            publishState()
        }
        job = scope.launch(Dispatchers.Default) {
            try {
                // 初始已暂停的恢复环境不产生第一帧；等待继续或明确单步。
                if (MarineTime.state.value.paused) MarineTime.sleep(1L)
                while (isActive) {
                    tick()
                    MarineTime.sleep(100L)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message?.take(256) ?: "Scenario processing failed"
                synchronized(lock) { mutable.value = mutable.value.copy(error = message) }
                onError(message)
            }
        }
    }

    fun checkpoint(): ScenarioCheckpoint = synchronized(lock) { current.copy(frozenFrames = frozen.map { (key, sample) ->
        ScenarioFrozenFrame(key, sample.payload, sample.measured, sample.utc)
    }) }
    fun setScenario(value: HardwareScenario) {
        ScenarioValidation.requireValid(value)
        synchronized(lock) {
            check(!closed)
            scenario = value
            current = fresh(value)
            originElapsed = MarineTime.nowElapsedMillis()
            resetBuffers()
            ScenarioValidation.deviceIds.forEach { MarineDeviceBus.detach(it, "Scenario replaced"); reconcileAttachment(it) }
            advanceTo(0)
            publishState()
        }
    }
    fun setFault(value: HardwareFault) {
        ScenarioValidation.requireValidFault(value)
        synchronized(lock) { check(!closed); changeFault(value); publishState() }
    }
    fun clearFault(deviceId: String) {
        ScenarioValidation.requireDevice(deviceId)
        synchronized(lock) { check(!closed); clearFaultLocked(deviceId); publishState() }
    }
    fun attach(deviceId: String) {
        ScenarioValidation.requireDevice(deviceId)
        synchronized(lock) { check(!closed); current = current.copy(attachedDeviceIds = current.attachedDeviceIds + deviceId); reconcileAttachment(deviceId); publishState() }
    }
    fun detach(deviceId: String) {
        ScenarioValidation.requireDevice(deviceId)
        synchronized(lock) { check(!closed); current = current.copy(attachedDeviceIds = current.attachedDeviceIds - deviceId); reconcileAttachment(deviceId); clearPending(deviceId); publishState() }
    }
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pending.clear()
            ScenarioValidation.deviceIds.forEach { MarineDeviceBus.detach(it, "Scenario stopped") }
        }
        job.cancel()
    }

    private fun tick() {
        val ready = synchronized(lock) {
            if (closed || MarineDeviceBus.state.value.backend != DeviceBackend.SIMULATED) return
            val elapsed = (MarineTime.nowElapsedMillis() - originElapsed).coerceAtLeast(current.elapsedMillis)
            check(elapsed <= ScenarioValidation.MAX_DURATION_MILLIS) { "Scenario reached its seven-day limit" }
            advanceTo(elapsed)
            produce(elapsed)
            val now = MarineTime.nowElapsedMillis()
            buildList { while (pending.peek()?.due?.let { it <= now } == true) add(pending.remove()) }
        }
        ready.forEach { emission ->
            if (MarineDeviceBus.publish(emission.device, emission.sample.payload, emission.sample.measured,
                    emission.sample.utc, emission.epoch, emission.generation)) synchronized(lock) {
                counts[emission.device] = (counts[emission.device] ?: 0) + 1
            }
        }
        synchronized(lock) { publishState() }
    }

    private fun fresh(value: HardwareScenario) = ScenarioCheckpoint(latitude = value.latitude, longitude = value.longitude,
        speedKnots = value.speedKnots, courseDegrees = normal(value.courseDegrees), headingDegrees = normal(value.headingDegrees),
        depthMeters = value.depthMeters, windSpeedKnots = value.windSpeedKnots, pressureHpa = value.pressureHpa, targets = value.targets)

    private fun validateCheckpoint(value: ScenarioCheckpoint) {
        require(value.elapsedMillis in 0..ScenarioValidation.MAX_DURATION_MILLIS && value.nextWaypoint in 0..scenario.waypoints.size)
        require(value.eventCursor in 0..scenario.events.size && value.attachedDeviceIds.all { it in ScenarioValidation.deviceIds })
        require(scenario.events.take(value.eventCursor).all { it.atMillis <= value.elapsedMillis } && scenario.events.drop(value.eventCursor).all { it.atMillis >= value.elapsedMillis })
        require(value.faults.size <= 4 && value.faults.map { it.deviceId }.distinct().size == value.faults.size)
        require(value.frozenFrames.size <= 512 && value.frozenFrames.map { it.key }.distinct().size == value.frozenFrames.size)
        value.frozenFrames.forEach { frame ->
            require(frame.key.length <= 256 && frame.key.substringBefore('|') in ScenarioValidation.deviceIds)
            require(frame.payload.isValid() && frame.measuredElapsedMillis >= 0 && frame.utcMillis > 0)
        }
        ScenarioValidation.requireValid(scenario.copy(latitude = value.latitude, longitude = value.longitude,
            speedKnots = value.speedKnots, courseDegrees = value.courseDegrees, headingDegrees = value.headingDegrees,
            depthMeters = value.depthMeters, windSpeedKnots = value.windSpeedKnots, pressureHpa = value.pressureHpa,
            targets = value.targets.take(64), events = emptyList()))
        require(value.targets.size <= 128 && value.targets.map { it.mmsi }.distinct().size == value.targets.size)
        value.targets.forEach(ScenarioValidation::target)
        value.faults.forEach(ScenarioValidation::requireValidFault)
    }

    /** 按事件边界积分；恢复一周场景不需要重放数百万个计时 tick。 */
    private fun advanceTo(elapsed: Long) {
        while (current.eventCursor < scenario.events.size && scenario.events[current.eventCursor].atMillis <= elapsed) {
            val event = scenario.events[current.eventCursor]
            moveTo(event.atMillis.coerceAtLeast(current.elapsedMillis))
            apply(event)
            current = current.copy(eventCursor = current.eventCursor + 1)
        }
        moveTo(elapsed)
    }

    private fun moveTo(elapsed: Long) {
        val seconds = (elapsed - current.elapsedMillis).coerceAtLeast(0) / 1_000.0
        var point = ScenarioPoint(current.latitude, current.longitude)
        var remaining = current.speedKnots * KNOT * seconds
        var course = current.courseDegrees
        var waypoint = current.nextWaypoint
        var speed = current.speedKnots
        if (scenario.waypoints.isEmpty()) point = destination(point, course, remaining)
        else {
            while (waypoint < scenario.waypoints.size && remaining > 0) {
                val next = scenario.waypoints[waypoint]
                val distance = distance(point, next)
                if (distance < .05) { point = next; waypoint++; continue }
                course = bearing(point, next)
                if (remaining >= distance) { point = next; remaining -= distance; waypoint++ }
                else { point = destination(point, course, remaining); remaining = 0.0 }
            }
            if (waypoint == scenario.waypoints.size) speed = 0.0
        }
        val targets = current.targets.map { target ->
            val moved = destination(ScenarioPoint(target.latitude, target.longitude), target.courseDegrees, target.speedKnots * KNOT * seconds)
            target.copy(latitude = moved.latitude, longitude = moved.longitude)
        }
        current = current.copy(elapsedMillis = elapsed, latitude = point.latitude, longitude = point.longitude,
            speedKnots = speed, courseDegrees = course, headingDegrees = normal(current.headingDegrees + shortest(current.courseDegrees, course)),
            nextWaypoint = waypoint, targets = targets)
    }

    private fun apply(event: ScenarioEvent) {
        when (event.action) {
            ScenarioAction.SET_SPEED -> current = current.copy(speedKnots = event.value)
            ScenarioAction.SET_COURSE -> current = current.copy(courseDegrees = normal(event.value), headingDegrees = normal(current.headingDegrees + shortest(current.courseDegrees, event.value)))
            ScenarioAction.SET_WIND -> current = current.copy(windSpeedKnots = event.value)
            ScenarioAction.SET_DEPTH -> current = current.copy(depthMeters = event.value)
            ScenarioAction.SET_PRESSURE -> current = current.copy(pressureHpa = event.value)
            ScenarioAction.ATTACH -> { current = current.copy(attachedDeviceIds = current.attachedDeviceIds + event.deviceId); reconcileAttachment(event.deviceId) }
            ScenarioAction.DETACH -> { current = current.copy(attachedDeviceIds = current.attachedDeviceIds - event.deviceId); reconcileAttachment(event.deviceId); clearPending(event.deviceId) }
            ScenarioAction.FAULT -> changeFault(requireNotNull(event.fault))
            ScenarioAction.CLEAR_FAULT -> clearFaultLocked(event.deviceId)
            ScenarioAction.AIS_SPAWN -> { val target = requireNotNull(event.target); current = current.copy(targets = current.targets.filterNot { it.mmsi == target.mmsi } + target); nextAisStatic = 0 }
            ScenarioAction.POWER -> onPower(requireNotNull(event.power).copy(simulated = true))
            ScenarioAction.STORAGE -> onStorage(event.storageFault)
        }
    }

    private fun changeFault(fault: HardwareFault) {
        current = current.copy(faults = current.faults.filterNot { it.deviceId == fault.deviceId } + fault)
        clearPending(fault.deviceId)
        frozen.keys.removeAll { it.startsWith(fault.deviceId + "|") }
        if (fault.type == DeviceFault.FROZEN) frozen.putAll(lastSamples.filterKeys { it.startsWith(fault.deviceId + "|") })
        reconcileAttachment(fault.deviceId)
    }
    private fun clearFaultLocked(id: String) {
        current = current.copy(faults = current.faults.filterNot { it.deviceId == id })
        clearPending(id); frozen.keys.removeAll { it.startsWith(id + "|") }; reconcileAttachment(id)
    }
    private fun clearPending(id: String) { pending.removeAll { it.device == id } }
    private fun reconcileAttachment(id: String) {
        val attached = id in current.attachedDeviceIds && current.faults.none { it.deviceId == id && it.type == DeviceFault.DISCONNECTED }
        if (!attached) { MarineDeviceBus.detach(id, "Scenario device disconnected"); return }
        val kind = when (id) { "sim.gnss" -> DeviceKind.GNSS; "sim.imu" -> DeviceKind.IMU; "sim.pressure" -> DeviceKind.PRESSURE; else -> DeviceKind.NMEA_CONNECTION }
        val capabilities = when (kind) {
            DeviceKind.GNSS -> listOf("position", "speed_over_ground", "course_over_ground")
            DeviceKind.IMU -> listOf("rotation", "acceleration", "gyroscope", "magnetometer")
            DeviceKind.PRESSURE -> listOf("pressure")
            DeviceKind.NMEA_CONNECTION -> listOf("position", "heading", "depth", "wind", "water_temperature", "ais")
        }
        MarineDeviceBus.attach(HardwareDeviceSpec(id, "Scenario ${id.substringAfter('.')}", kind, DeviceBackend.SIMULATED, "yokuli.scenario.v1", capabilities))
    }

    private fun produce(elapsed: Long) {
        if (elapsed >= nextGnss) {
            nextGnss = elapsed + 1_000
            val fault = fault("sim.gnss")
            val base = ScenarioPoint(current.latitude, current.longitude)
            val point = when (fault?.type) {
                DeviceFault.TELEPORT -> destination(base, 90.0, HardwareFaultPolicy.effectiveMagnitude(fault))
                DeviceFault.INACCURATE -> destination(base, normal(elapsed / 57.0), (HardwareFaultPolicy.effectiveMagnitude(fault)) * .6)
                else -> base
            }
            emit("sim.gnss", HardwarePayload(kind = DeviceKind.GNSS, latitude = point.latitude, longitude = point.longitude,
                sogKnots = current.speedKnots, cogTrueDegrees = current.courseDegrees, altitudeMeters = 0.0,
                accuracyMeters = if (fault?.type == DeviceFault.INACCURATE) HardwareFaultPolicy.effectiveMagnitude(fault) else scenario.accuracyMeters, satellites = 12))
        }
        produceImu(elapsed)
        if (elapsed >= nextPressure) {
            nextPressure = elapsed + 1_000
            emit("sim.pressure", HardwarePayload(kind = DeviceKind.PRESSURE, pressureHpa = current.pressureHpa))
        }
        if (elapsed >= nextNmea) {
            nextNmea = elapsed + 1_000
            produceNmea(elapsed)
        }
    }

    private fun produceImu(elapsed: Long) {
        val fault = fault("sim.imu")
        val seconds = elapsed / 1_000.0
        val frequency = 2 * PI / scenario.rollPeriodSeconds
        val heel = Math.toRadians(scenario.heelDegrees) * sin(seconds * frequency)
        val pitch = Math.toRadians(scenario.pitchDegrees) * sin(seconds * frequency * .73)
        val disturbance = if (fault?.type == DeviceFault.MAGNETIC_INTERFERENCE) (HardwareFaultPolicy.effectiveMagnitude(fault)) * sin(seconds * 8) else 0.0
        val jump = if (fault?.type == DeviceFault.HEADING_JUMP) HardwareFaultPolicy.effectiveMagnitude(fault) else 0.0
        // Android 旋转向量给磁北参考，原Heading仓库再结合GNSS磁偏角转换真北。
        if (elapsed >= nextMagneticEstimate) {
            magneticDeclination = GeomagneticField(current.latitude.toFloat(), current.longitude.toFloat(), 0f, MarineTime.nowUtcMillis()).declination.toDouble()
            nextMagneticEstimate = elapsed + 30_000L
        }
        val yaw = -Math.toRadians(current.headingDegrees - magneticDeclination + disturbance + jump)
        val quaternion = quaternion(yaw, pitch, heel)
        val accuracy = if (fault?.type == DeviceFault.MAGNETIC_INTERFERENCE) 0 else 3
        fun sensor(type: HardwareSensorType, values: List<Double>) = emit("sim.imu", HardwarePayload(kind = DeviceKind.IMU,
            sensorType = type, values = if (fault?.type == DeviceFault.ZERO) if (type == HardwareSensorType.ROTATION_QUATERNION) listOf(1.0, 0.0, 0.0, 0.0) else listOf(0.0, 0.0, 0.0) else values,
            sensorAccuracy = accuracy))
        sensor(HardwareSensorType.ROTATION_QUATERNION, quaternion)
        sensor(HardwareSensorType.GYROSCOPE, listOf(Math.toRadians(scenario.pitchDegrees) * frequency * .73 * cos(seconds * frequency * .73),
            Math.toRadians(scenario.heelDegrees) * frequency * cos(seconds * frequency), 0.0))
        val linear = listOf(.12 * sin(seconds * frequency), .04 * cos(seconds * frequency), .18 * sin(seconds * frequency * 1.3))
        sensor(HardwareSensorType.LINEAR_ACCELERATION, linear)
        val gravity = rotateInverse(quaternion, listOf(0.0, 0.0, 9.80665))
        sensor(HardwareSensorType.ACCELEROMETER, gravity.zip(linear) { a, b -> a + b })
        sensor(HardwareSensorType.MAGNETOMETER, rotateInverse(quaternion, listOf(0.0, if (accuracy == 0) 220.0 else 25.0, -42.0)))
    }

    private fun produceNmea(elapsed: Long) {
        val f = fault("sim.nmea")
        val zero = f?.type == DeviceFault.ZERO
        val missing = f?.type == DeviceFault.MISSING
        val utc = Instant.ofEpochMilli(MarineTime.nowUtcMillis()).atZone(ZoneOffset.UTC)
        val time = String.format(Locale.US, "%02d%02d%02d", utc.hour, utc.minute, utc.second)
        val date = String.format(Locale.US, "%02d%02d%02d", utc.dayOfMonth, utc.monthValue, utc.year % 100)
        val latitude = coordinate(current.latitude, true)
        val longitude = coordinate(current.longitude, false)
        sentence("GPRMC,$time.00,A,$latitude,$longitude,${decimal(current.speedKnots)},${decimal(current.courseDegrees)},$date,,,A")
        sentence("GPGGA,$time.00,$latitude,$longitude,1,12,0.8,0.0,M,0.0,M,,")
        val heading = normal(current.headingDegrees + if (f?.type == DeviceFault.HEADING_JUMP) HardwareFaultPolicy.effectiveMagnitude(f) else 0.0)
        sentence("HEHDT,${decimal(heading)},T")
        sentence("SDDPT,${if (missing) "" else decimal(if (zero) 0.0 else current.depthMeters)},0.0")
        val wind = if (missing) "" else decimal(if (zero) 0.0 else current.windSpeedKnots)
        sentence("WIMWD,${decimal(scenario.windDirectionDegrees)},T,,M,$wind,N,,M")
        sentence("WIMWV,${decimal(normal(scenario.windDirectionDegrees - current.headingDegrees))},T,$wind,N,${if (missing) "V" else "A"}")
        sentence("YXMTW,${decimal(scenario.waterTemperatureC)},C")
        sentence("YXXDR,P,${decimal(current.pressureHpa / 1000.0, 4)},B,BARO")
        current.targets.forEach { target ->
            aisDynamic(target, utc.second, f?.type == DeviceFault.AIS_FRAGMENTED)
            if (f?.type == DeviceFault.AIS_CONFLICT) {
                val displaced = destination(ScenarioPoint(target.latitude, target.longitude), 90.0, 8_000.0)
                aisDynamic(target.copy(latitude = displaced.latitude, longitude = displaced.longitude), utc.second, false, "simulator/conflict")
            }
        }
        if (elapsed >= nextAisStatic) {
            nextAisStatic = elapsed + 30_000
            current.targets.forEach(::aisStatic)
        }
    }

    /** FROZEN 重发原测量时刻；LATENCY 按原时刻排队；失联仅停流，绝不伪造新观测。 */
    private fun emit(id: String, payload: HardwarePayload, additionalDelay: Long = 0) {
        val bus = MarineDeviceBus.state.value
        val device = bus.devices.firstOrNull { it.spec.id == id && it.attached } ?: return
        if (bus.backend != DeviceBackend.SIMULATED) return
        val f = fault(id)
        if (f?.type in setOf(DeviceFault.LOST, DeviceFault.DISCONNECTED) || f?.type == DeviceFault.MISSING && id != "sim.nmea") return
        if (f?.type == DeviceFault.INTERMITTENT) {
            val period = (HardwareFaultPolicy.effectiveMagnitude(f)).toLong().coerceAtLeast(200)
            if ((current.elapsedMillis / period) % 2L == 1L) return
        }
        val order = ++generated
        if (f?.type == DeviceFault.PACKET_LOSS && ((order * 73 + 19) % 100) < (HardwareFaultPolicy.effectiveMagnitude(f))) return
        val now = MarineTime.nowElapsedMillis()
        val utc = MarineTime.nowUtcMillis()
        val key = "$id|${payload.sensorType ?: payload.sentence?.substringBefore(',').orEmpty()}|${aisFrameKey(payload.sentence)}"
        var sample = Sample(payload, now, utc)
        if (f?.type == DeviceFault.FROZEN) sample = frozen.getOrPut(key) { lastSamples[key] ?: sample }
        else lastSamples[key] = sample
        while (lastSamples.size > 512) lastSamples.remove(lastSamples.keys.first())
        while (frozen.size > 512) frozen.remove(frozen.keys.first())
        if (f?.type == DeviceFault.STALE) {
            val age = (HardwareFaultPolicy.effectiveMagnitude(f)).toLong()
            sample = sample.copy(measured = (now - age).coerceAtLeast(0), utc = (utc - age).coerceAtLeast(1))
        }
        val latency = if (f?.type == DeviceFault.LATENCY) (HardwareFaultPolicy.effectiveMagnitude(f)).toLong() else 0L
        check(pending.size < 20_000) { "Scenario delayed-frame buffer is full" }
        pending.add(Emission(id, sample, now + latency + additionalDelay, bus.epoch, device.generation, order))
    }

    private fun sentence(body: String, prefix: Char = '$', peer: String = "simulator/nmea", delay: Long = 0) {
        val checksum = body.fold(0) { sum, c -> sum xor c.code }
        emit("sim.nmea", HardwarePayload(kind = DeviceKind.NMEA_CONNECTION,
            sentence = "$prefix$body*${checksum.toString(16).uppercase().padStart(2, '0')}", peer = peer), delay)
    }

    private fun aisDynamic(target: ScenarioAisTarget, second: Int, fragmented: Boolean, peer: String = "simulator/ais") {
        val bits = Bits(168)
        bits.put(0, 6, 1); bits.put(8, 30, target.mmsi.toLong()); bits.put(38, 4, if (target.speedKnots < .1) 1 else 0)
        bits.put(42, 8, -128); bits.put(50, 10, (target.speedKnots * 10).roundToInt().toLong()); bits.put(60, 1, 1)
        bits.put(61, 28, (target.longitude * 600_000).roundToLong()); bits.put(89, 27, (target.latitude * 600_000).roundToLong())
        bits.put(116, 12, (normal(target.courseDegrees) * 10).roundToInt().coerceAtMost(3599).toLong())
        bits.put(128, 9, normal(target.headingDegrees).roundToInt().coerceAtMost(359).toLong()); bits.put(137, 6, second.toLong())
        val payload = bits.armour()
        if (!fragmented) sentence("AIVDM,1,1,,A,$payload,0", '!', peer)
        else {
            val sequence = (++fragmentSequence % 10).toString()
            sentence("AIVDM,2,1,$sequence,A,${payload.take(14)},0", '!', peer)
            // 故障真实制造残片和延迟分片，已有重组器负责超时及拒绝，不在UI伪造故障标签。
            if ((target.mmsi + current.elapsedMillis / 1_000) % 2L == 0L)
                sentence("AIVDM,2,2,$sequence,A,${payload.drop(14)},0", '!', peer, 2_500)
        }
    }
    private fun aisStatic(target: ScenarioAisTarget) {
        val bits = Bits(424)
        bits.put(0, 6, 5); bits.put(8, 30, target.mmsi.toLong()); bits.text(70, 42, "YKL${target.mmsi.toString().takeLast(4)}")
        bits.text(112, 120, target.name.ifBlank { "VESSEL ${target.mmsi}" }); bits.put(232, 8, 36)
        bits.put(240, 9, 8); bits.put(249, 9, 3); bits.put(258, 6, 2); bits.put(264, 6, 2); bits.put(270, 4, 1)
        bits.put(283, 5, 24); bits.put(288, 6, 60); bits.put(294, 8, 18); bits.text(302, 120, "YOKULI SCENARIO")
        val payload = bits.armour()
        val sequence = (++fragmentSequence % 10).toString()
        sentence("AIVDM,2,1,$sequence,A,${payload.take(56)},0", '!')
        sentence("AIVDM,2,2,$sequence,A,${payload.drop(56)},2", '!')
    }

    private class Bits(size: Int) {
        private val bits = IntArray(size)
        fun put(offset: Int, count: Int, value: Int) = put(offset, count, value.toLong())
        fun put(offset: Int, count: Int, value: Long) { repeat(count) { bit -> bits[offset + bit] = ((value shr (count - bit - 1)) and 1L).toInt() } }
        fun text(offset: Int, count: Int, value: String) {
            val chars = value.uppercase(Locale.ROOT).padEnd(count / 6, '@').take(count / 6)
            chars.forEachIndexed { index, c -> put(offset + index * 6, 6, when (c.code) { in 64..95 -> c.code - 64; in 32..63 -> c.code; else -> 32 }) }
        }
        fun armour(): String = buildString {
            for (start in bits.indices step 6) {
                var value = 0
                repeat(6) { value = (value shl 1) or bits.getOrElse(start + it) { 0 } }
                append((value + if (value < 40) 48 else 56).toChar())
            }
        }
    }

    private fun fault(id: String) = current.faults.firstOrNull { it.deviceId == id }
    private fun aisFrameKey(sentence: String?): String {
        if (sentence?.startsWith("!AIVDM") != true) return ""
        val fields = sentence.split(',')
        val data = fields.getOrNull(5).orEmpty()
        if (fields.getOrNull(2) != "1" || data.length < 7) return fields.take(4).joinToString()
        fun bit(offset: Int): Int {
            val code = data[offset / 6].code - 48
            val value = if (code > 40) code - 8 else code
            return (value shr (5 - offset % 6)) and 1
        }
        var identity = 0L
        for (index in 8 until 38) identity = (identity shl 1) or bit(index).toLong()
        return "${fields.getOrNull(1)}:1:$identity"
    }
    private fun publishState() {
        mutable.value = ScenarioEngineState(current.elapsedMillis, current.eventCursor, current.faults,
            MarineDeviceBus.state.value.devices.filter { it.attached && it.spec.id in ScenarioValidation.deviceIds }.map { it.spec.id }.toSet(), counts.toMap(), mutable.value.error)
    }
    private fun resetBuffers() { pending.clear(); frozen.clear(); lastSamples.clear(); counts.clear(); nextGnss = 0; nextPressure = 0; nextNmea = 0; nextAisStatic = 0; nextMagneticEstimate = 0; mutable.value = ScenarioEngineState() }

    private companion object {
        const val KNOT = 1852.0 / 3600.0
        const val EARTH = 6_371_008.8
        fun normal(value: Double) = ((value % 360) + 360) % 360
        fun shortest(from: Double, to: Double) = ((to - from + 540) % 360) - 180
        fun decimal(value: Double, places: Int = 2) = String.format(Locale.US, "%.${places}f", value)
        fun coordinate(value: Double, latitude: Boolean): String {
            val scaledMinutes = (abs(value) * 600_000).roundToLong()
            val degrees = (scaledMinutes / 600_000).toInt()
            val minutes = (scaledMinutes % 600_000) / 10_000.0
            return String.format(Locale.US, if (latitude) "%02d%07.4f,%s" else "%03d%07.4f,%s", degrees, minutes,
                if (latitude) if (value < 0) "S" else "N" else if (value < 0) "W" else "E")
        }
        fun distance(a: ScenarioPoint, b: ScenarioPoint): Double {
            val latA = Math.toRadians(a.latitude); val latB = Math.toRadians(b.latitude)
            val dLat = latB - latA; val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) + cos(latA) * cos(latB) * sin(dLon / 2).pow(2)
            return 2 * EARTH * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
        fun bearing(a: ScenarioPoint, b: ScenarioPoint): Double {
            val latA = Math.toRadians(a.latitude); val latB = Math.toRadians(b.latitude); val lon = Math.toRadians(b.longitude - a.longitude)
            return normal(Math.toDegrees(atan2(sin(lon) * cos(latB), cos(latA) * sin(latB) - sin(latA) * cos(latB) * cos(lon))))
        }
        fun destination(point: ScenarioPoint, course: Double, metres: Double): ScenarioPoint {
            if (metres <= 0) return point
            val lat = Math.toRadians(point.latitude); val lon = Math.toRadians(point.longitude); val bearing = Math.toRadians(course); val arc = metres / EARTH
            val nextLat = asin((sin(lat) * cos(arc) + cos(lat) * sin(arc) * cos(bearing)).coerceIn(-1.0, 1.0))
            val nextLon = lon + atan2(sin(bearing) * sin(arc) * cos(lat), cos(arc) - sin(lat) * sin(nextLat))
            return ScenarioPoint(Math.toDegrees(nextLat), ((Math.toDegrees(nextLon) + 540) % 360) - 180)
        }
        // Android设备坐标：顶部+y，右侧+x，屏幕外+z；顺时针艏向为绕z负旋转。
        fun quaternion(yaw: Double, pitch: Double, heel: Double): List<Double> {
            val cy = cos(yaw / 2); val sy = sin(yaw / 2); val cp = cos(pitch / 2); val sp = sin(pitch / 2); val cr = cos(heel / 2); val sr = sin(heel / 2)
            return listOf(cy * cp * cr + sy * sp * sr, cy * sp * cr - sy * cp * sr, cy * cp * sr + sy * sp * cr, sy * cp * cr - cy * sp * sr)
        }
        fun rotateInverse(q: List<Double>, v: List<Double>): List<Double> {
            val w = q[0]; val x = -q[1]; val y = -q[2]; val z = -q[3]
            val tx = 2 * (y * v[2] - z * v[1]); val ty = 2 * (z * v[0] - x * v[2]); val tz = 2 * (x * v[1] - y * v[0])
            return listOf(v[0] + w * tx + y * tz - z * ty, v[1] + w * ty + z * tx - x * tz, v[2] + w * tz + x * ty - y * tx)
        }
    }
}

package com.yokuli.runtime.marine.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.LocationManager
import com.yokuli.runtime.contract.time.MarineTime
import com.yokuli.runtime.contract.hardware.MarineDeviceBus
import com.yokuli.runtime.contract.hardware.HardwareDevice
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSnapshot
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.location.PhoneLocationPhase
import com.yokuli.anchorwatch.location.SystemLocationRepository
import com.yokuli.anchorwatch.location.vessel.PhonePressureRepository
import com.yokuli.anchorwatch.location.vessel.PhoneVesselAttitudeRepository
import com.yokuli.anchorwatch.runtime.RuntimeResourceManager
import com.yokuli.runtime.contract.device.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 默认进程的设备目录：读取唯一驱动与资源所有者，不注册第二套传感器或 socket。
 * 目录以 2 Hz 合并 IMU 等高频输入，既不降低真实采集频率，也不把每帧姿态推给 Shell。
 * 真实设备未采集时保留能力目录；活动设备、模拟与回放均投影同一 DeviceBus。
 */
@Singleton
class LocalDeviceRuntimeService @Inject constructor(
    @ApplicationContext context: Context,
    private val location: SystemLocationRepository,
    private val attitude: PhoneVesselAttitudeRepository,
    private val pressure: PhonePressureRepository,
    private val navigation: NavigationRepository,
    private val resources: RuntimeResourceManager,
) : DeviceRuntimeService {
    private val runtimeId = UUID.randomUUID().toString()
    private val hasGnss = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow(DeviceCatalogSnapshot(runtimeId = runtimeId))
    override val state = mutable.asStateFlow()
    private var gnssGeneration = 0L
    private var gnssActive = false
    private data class ConnectionEpoch(val generation: Long, val observedSince: Long)
    private val connectionEpochs = mutableMapOf<String, ConnectionEpoch>()

    init {
        scope.launch {
            while (isActive) {
                try {
                    val now = MarineTime.nowElapsedMillis()
                    val connections = navigation.connections.value
                    connectionEpochs.keys.retainAll(connections.map { it.spec.id }.toSet())
                    val bus=MarineDeviceBus.state.value
                    val physical=if(bus.backend==DeviceBackend.REAL)listOf(gnss(now),imu(now),barometer(now))+connections.sortedBy{it.spec.id}.map{nmea(it,now)}else emptyList()
                    val devices=(physical.associateBy{it.id}+bus.devices.associate{it.spec.id to fromBus(it,now,physical.firstOrNull{old->old.id==it.spec.id})}).values.toList()
                    mutable.value = DeviceCatalogSnapshot(true, runtimeId, mutable.value.revision + 1, now, devices)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 失败不得继续声称旧目录实时；下一次投影恢复后才恢复 ready。
                    mutable.value = mutable.value.copy(ready = false, error = "DEVICE_CATALOG_UNAVAILABLE")
                }
                delay(500)
            }
        }
    }

    private fun fromBus(device:HardwareDevice,now:Long,physical:MarineDeviceDescriptor?):MarineDeviceDescriptor {
        val spec=device.spec
        val active=device.attached&&(spec.kind!=DeviceKind.NMEA_CONNECTION||spec.backend!=DeviceBackend.REAL||physical?.active==true)
        return MarineDeviceDescriptor(
            id=spec.id,name=if(spec.backend==DeviceBackend.REAL)spec.name else "${spec.backend.name} · ${spec.name}",kind=spec.kind,backend=spec.backend,
            availability=if(active)DeviceAvailability.AVAILABLE else physical?.availability?:DeviceAvailability.DISABLED,
            health=if(spec.backend==DeviceBackend.REAL&&spec.kind==DeviceKind.NMEA_CONNECTION&&physical!=null)physical.health else if(!active)DeviceHealth.OFF else observationHealth(device.lastMeasuredElapsedMillis,now,if(spec.kind==DeviceKind.IMU)5_000 else 15_000),
            requested=if(spec.backend==DeviceBackend.REAL&&spec.kind==DeviceKind.NMEA_CONNECTION)physical?.requested?:active else active,active=active,generation=device.generation,generationOrigin="hal.bus",
            capabilities=spec.capabilities,
            lastMeasuredElapsedRealtime=device.lastMeasuredElapsedMillis,
            lastReceivedElapsedRealtime=device.lastReceivedElapsedMillis,
            lastOutputElapsedRealtime=physical?.lastOutputElapsedRealtime,
            provenance=DeviceProvenance(spec.driver,spec.id,if(spec.kind==DeviceKind.NMEA_CONNECTION)spec.id.removePrefix("nmea:")else null),
            reason=device.reason,
        )
    }

    private fun gnss(now: Long): MarineDeviceDescriptor {
        val status = location.status.value
        val permission = location.hasPermission()
        // 未申请采集时现有驱动 phase 为 OFF；仍需如实说明 Android GPS 总开关已关闭。
        // 这是只读宿主查询，不注册监听，也不取得定位租约。
        val providerEnabled = runCatching { locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrNull()
        val requested = resources.state.value.needsSystemLocation || status.selectionPending || status.phase != PhoneLocationPhase.OFF
        val active = hasGnss && permission && providerEnabled != false && status.phase == PhoneLocationPhase.LISTENING
        if (active && !gnssActive) gnssGeneration++
        gnssActive = active
        val measured = location.fix.value?.receivedElapsedRealtime?.takeIf { active && status.phase != PhoneLocationPhase.PERMISSION_REQUIRED }
        val availability = when {
            !hasGnss -> DeviceAvailability.MISSING
            !permission -> DeviceAvailability.PERMISSION_REQUIRED
            providerEnabled == false || status.phase == PhoneLocationPhase.PROVIDER_DISABLED -> DeviceAvailability.DISABLED
            else -> DeviceAvailability.AVAILABLE
        }
        return MarineDeviceDescriptor(
            id = "phone.gnss", name = "Phone GNSS", kind = DeviceKind.GNSS,
            availability = availability, requested = requested, active = active,
            health = when {
                status.phase == PhoneLocationPhase.ERROR -> DeviceHealth.ERROR
                !active -> DeviceHealth.OFF
                else -> observationHealth(measured, now, 10_000)
            },
            generation = gnssGeneration, generationOrigin = "catalog.lifecycle",
            capabilities = if (hasGnss) listOf("position", "speed.overGround", "course.overGround", "altitude") else emptyList(),
            lastMeasuredElapsedRealtime = measured,
            provenance = DeviceProvenance("android.location.gps", "phone:gnss"),
            reason = when {
                !hasGnss -> "HARDWARE_MISSING"
                availability == DeviceAvailability.PERMISSION_REQUIRED -> "PRECISE_LOCATION_PERMISSION_REQUIRED"
                availability == DeviceAvailability.DISABLED -> "ANDROID_LOCATION_DISABLED"
                status.phase == PhoneLocationPhase.ERROR -> "LOCATION_DRIVER_ERROR"
                status.selectionPending -> "POSITION_SELECTION_PENDING"
                else -> null
            },
        )
    }

    private fun imu(now: Long): MarineDeviceDescriptor {
        val sample = attitude.deviceOrientation.value
        val resource = resources.state.value
        val available = attitude.capabilities.attitudeAvailable
        val active = resource.phoneMotionActive || resource.deviceViewOrientationActive
        val measured = sample.elapsedRealtimeMillis.takeIf { active }
        val requested = resource.needsPhoneMotion || resource.needsDeviceViewOrientation
        return MarineDeviceDescriptor(
            id = "phone.imu", name = "Phone IMU", kind = DeviceKind.IMU,
            availability = if (available) DeviceAvailability.AVAILABLE else DeviceAvailability.MISSING,
            health = when {
                !available -> DeviceHealth.OFF
                requested && !active -> DeviceHealth.ERROR
                !active -> DeviceHealth.OFF
                measured != null && sample.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE -> DeviceHealth.HELD
                else -> observationHealth(measured, now, 5_000)
            }, requested = requested, active = active && available,
            generation = sample.generation,
            capabilities = buildList {
                if (available) add("orientation.device")
                if (attitude.capabilities.gyroAvailable) add("angularVelocity")
                if (attitude.capabilities.linearAccelerationAvailable) add("acceleration.linear")
                if (attitude.capabilities.magnetometerAvailable) add("magneticField")
            },
            lastMeasuredElapsedRealtime = measured,
            provenance = DeviceProvenance("android.sensor.rotationVector", "phone:vessel-imu"),
            reason = when {
                !available -> "HARDWARE_MISSING"
                requested && !active -> "SENSOR_START_FAILED"
                measured != null && sample.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE -> "SENSOR_ACCURACY_UNRELIABLE"
                else -> null
            },
        )
    }

    private fun barometer(now: Long): MarineDeviceDescriptor {
        val sample = pressure.sample.value
        val resource = resources.state.value
        val available = attitude.capabilities.pressureAvailable
        val active = resource.phonePressureActive
        return MarineDeviceDescriptor(
            id = "phone.pressure", name = "Phone barometer", kind = DeviceKind.PRESSURE,
            availability = if (available) DeviceAvailability.AVAILABLE else DeviceAvailability.MISSING,
            health = when {
                !available -> DeviceHealth.OFF
                resource.needsPhonePressure && !active -> DeviceHealth.ERROR
                !active -> DeviceHealth.OFF
                else -> observationHealth(sample.receivedElapsedRealtime, now, 30_000)
            }, requested = resource.needsPhonePressure, active = active,
            generation = sample.generation,
            capabilities = if (available) listOf("pressure.air") else emptyList(),
            lastMeasuredElapsedRealtime = sample.receivedElapsedRealtime.takeIf { active },
            provenance = DeviceProvenance("android.sensor.pressure", "phone:barometer"),
            reason = when {
                !available -> "HARDWARE_MISSING"
                resource.needsPhonePressure && !active -> "SENSOR_START_FAILED"
                else -> null
            },
        )
    }

    private fun nmea(value: NmeaConnectionSnapshot, now: Long): MarineDeviceDescriptor {
        val generation = navigation.connectionEpoch(value.spec.id) ?: value.transport.connectionGeneration
        val epoch = connectionEpochs[value.spec.id]?.takeIf { it.generation == generation }
            ?: ConnectionEpoch(generation,
                value.transport.connectedAtElapsedRealtime?.takeIf {
                    value.transport.connectionGeneration == generation && it in 0..now
                } ?: now).also { connectionEpochs[value.spec.id] = it }
        val active = navigation.isConnectionOpen(value.spec.id)
        // 旧驱动诊断可能保留上一次连接的时间；跨代次不把它重新标成实时。
        val received = value.lastLegalSentenceElapsed?.takeIf { value.requested && it >= epoch.observedSince }
        val output = value.lastWrittenElapsed?.takeIf { value.requested && it >= epoch.observedSince }
        return MarineDeviceDescriptor(
            id = "nmea:${value.spec.id}", name = value.spec.name, kind = DeviceKind.NMEA_CONNECTION,
            availability = DeviceAvailability.AVAILABLE, requested = value.requested, active = active,
            health = when {
                !value.requested -> DeviceHealth.OFF
                value.state == NmeaConnectionState.ERROR -> DeviceHealth.ERROR
                value.state in setOf(NmeaConnectionState.CONNECTING, NmeaConnectionState.RECONNECTING) -> DeviceHealth.STARTING
                !active -> DeviceHealth.WAITING
                else -> observationHealth(listOfNotNull(received, output).maxOrNull(), now, 30_000)
            },
            generation = generation,
            capabilities = buildList {
                add("nmea0183.${value.spec.protocol.name.lowercase()}")
                if (value.spec.receive) add("nmea.receive")
                if (value.spec.send) add("nmea.send")
            },
            // 原始报文有接收时刻；目录不能从报文接收时刻推断各字段的实际测量时刻。
            lastReceivedElapsedRealtime = received, lastOutputElapsedRealtime = output,
            provenance = DeviceProvenance("yokuli.nmea.${value.spec.protocol.name.lowercase()}", "nmea:${value.spec.id}", value.spec.id),
            reason = if (value.state == NmeaConnectionState.ERROR) "NMEA_TRANSPORT_ERROR" else null,
        )
    }

    private fun observationHealth(measured: Long?, now: Long, recentMillis: Long): DeviceHealth = when {
        measured == null -> DeviceHealth.WAITING
        now - measured in 0..recentMillis -> DeviceHealth.LIVE
        else -> DeviceHealth.HELD
    }
}

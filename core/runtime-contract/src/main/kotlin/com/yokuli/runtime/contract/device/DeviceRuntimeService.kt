package com.yokuli.runtime.contract.device

import kotlinx.coroutines.flow.StateFlow

/** 中文：设备后端必须贯穿目录、总线帧和数据来源，不能将模拟/回放标作真实。 */
enum class DeviceBackend { REAL, SIMULATED, REPLAY }
enum class DeviceKind { GNSS, IMU, PRESSURE, NMEA_CONNECTION }
enum class DeviceAvailability { AVAILABLE, MISSING, PERMISSION_REQUIRED, DISABLED }

/** HELD 表示保留最后一次观测；不是断开，也不为安全业务判断数据可用性。 */
enum class DeviceHealth { OFF, STARTING, WAITING, LIVE, HELD, ERROR }

/** 仅说明生产适配器来源，不包含位置、原始报文、网络地址或 Android 文件路径。 */
data class DeviceProvenance(
    val driver: String = "",
    val sourceId: String = "",
    val connectionId: String? = null,
)

/**
 * 设备目录是既有驱动的只读投影，不是另一份船舶读数。
 * generation 只在 runtimeId 内比较；generationOrigin 区分真实驱动代次与目录观察的生命周期代次。
 * 所有 elapsed 时间与 Android 开机时钟一致；没有事实时保持 null，不用到达时间伪造测量时间。
 */
data class MarineDeviceDescriptor(
    val id: String = "",
    val name: String = "",
    val kind: DeviceKind = DeviceKind.GNSS,
    val backend: DeviceBackend = DeviceBackend.REAL,
    val availability: DeviceAvailability = DeviceAvailability.MISSING,
    val health: DeviceHealth = DeviceHealth.OFF,
    val requested: Boolean = false,
    val active: Boolean = false,
    val generation: Long = 0,
    val generationOrigin: String = "driver",
    val capabilities: List<String> = emptyList(),
    val lastMeasuredElapsedRealtime: Long? = null,
    val lastReceivedElapsedRealtime: Long? = null,
    val lastOutputElapsedRealtime: Long? = null,
    val provenance: DeviceProvenance = DeviceProvenance(),
    val reason: String? = null,
)

/** ready=false 时旧 devices 仅供保留展示；不得认为它们仍在运行，重连须等新快照。 */
data class DeviceCatalogSnapshot(
    val ready: Boolean = false,
    val runtimeId: String = "",
    val revision: Long = 0,
    val capturedElapsedRealtime: Long = 0,
    val devices: List<MarineDeviceDescriptor> = emptyList(),
    val error: String? = null,
)

/** 读取目录不会开启定位、注册传感器或连接网络；控制仍通过各领域的授权端口执行。 */
interface DeviceRuntimeService {
    val state: StateFlow<DeviceCatalogSnapshot>
}

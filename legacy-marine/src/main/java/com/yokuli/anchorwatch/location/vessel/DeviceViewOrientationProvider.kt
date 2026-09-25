package com.yokuli.anchorwatch.location.vessel

import android.hardware.SensorManager
import com.yokuli.anchorwatch.api.DisplayLease
import com.yokuli.anchorwatch.api.DisplayLeaseRegistry
import com.yokuli.anchorwatch.runtime.RuntimeOwner
import com.yokuli.anchorwatch.runtime.RuntimeRequirement
import com.yokuli.anchorwatch.runtime.RuntimeResourceManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备自然方向到磁北 ENU 的原始四元数。Android +Z 朝屏幕外（用户），
 * 观察远处目标的视线为 -Z；界面旋转只改变屏幕上方向，不能改变物理视线。
 * 时间来自 SensorEvent 的开机时钟；重启监听 generation 递增，显示插值必须断开。
 */
data class DeviceViewOrientationSample(
    val deviceToMagneticWorld: SensorQuaternion? = null,
    val elapsedRealtimeMillis: Long? = null,
    val generation: Long = 0,
    val sourceName: String = "",
    val accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val headingAccuracyDegrees: Double? = null,
    val sensorAvailable: Boolean = false,
)

/** 仅持有前台显示租约；不修改手机安装身份、任何校准或全船选源。 */
@Singleton
class DeviceViewOrientationProvider @Inject constructor(
    attitude: PhoneVesselAttitudeRepository,
    resources: RuntimeResourceManager,
) {
    val orientation = attitude.deviceOrientation
    private val leases = DisplayLeaseRegistry { active ->
        resources.set(RuntimeOwner.DEVICE_VIEW_UI,
            if (active) RuntimeRequirement(needsDeviceViewOrientation = true) else null)
    }
    fun acquire(): DisplayLease = leases.acquire()
}

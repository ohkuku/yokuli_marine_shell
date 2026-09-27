package com.yokuli.runtime.contract.hardware

/** 故障能力目录由 Core 与界面共用；不为不适用的设备展示无效控制。 */
object HardwareFaultPolicy {
    val deviceIds = setOf("sim.gnss", "sim.imu", "sim.pressure", "sim.nmea")
    private val common = setOf(DeviceFault.LOST, DeviceFault.FROZEN, DeviceFault.STALE, DeviceFault.MISSING,
        DeviceFault.INTERMITTENT, DeviceFault.LATENCY, DeviceFault.DISCONNECTED)
    fun allowed(deviceId: String): Set<DeviceFault> = when (deviceId) {
        "sim.gnss" -> common + setOf(DeviceFault.INACCURATE, DeviceFault.TELEPORT)
        "sim.imu" -> common + setOf(DeviceFault.MAGNETIC_INTERFERENCE, DeviceFault.HEADING_JUMP, DeviceFault.ZERO)
        "sim.pressure" -> common
        "sim.nmea" -> common + setOf(DeviceFault.HEADING_JUMP, DeviceFault.ZERO, DeviceFault.PACKET_LOSS, DeviceFault.AIS_CONFLICT, DeviceFault.AIS_FRAGMENTED)
        else -> emptySet()
    }
    /** null 表示此故障没有幅度参数；零值对有幅度的故障表示使用默认值。 */
    fun magnitudeRange(type: DeviceFault): ClosedFloatingPointRange<Double>? = when (type) {
        DeviceFault.INACCURATE -> 0.0..10_000.0
        DeviceFault.TELEPORT -> 0.0..100_000.0
        DeviceFault.HEADING_JUMP -> -360.0..360.0
        DeviceFault.STALE -> 0.0..86_400_000.0
        DeviceFault.LATENCY -> 0.0..60_000.0
        DeviceFault.PACKET_LOSS -> 0.0..100.0
        DeviceFault.INTERMITTENT -> 0.0..120_000.0
        DeviceFault.MAGNETIC_INTERFERENCE -> 0.0..180.0
        else -> null
    }
    fun magnitudeUnit(type: DeviceFault): String = when (type) {
        DeviceFault.INACCURATE, DeviceFault.TELEPORT -> "m"
        DeviceFault.HEADING_JUMP, DeviceFault.MAGNETIC_INTERFERENCE -> "°"
        DeviceFault.STALE, DeviceFault.LATENCY, DeviceFault.INTERMITTENT -> "ms"
        DeviceFault.PACKET_LOSS -> "%"
        else -> ""
    }
    fun defaultMagnitude(type: DeviceFault): Double = when (type) {
        DeviceFault.INACCURATE -> 250.0
        DeviceFault.TELEPORT -> 2_000.0
        DeviceFault.HEADING_JUMP -> 180.0
        DeviceFault.STALE -> 30_000.0
        DeviceFault.LATENCY -> 2_000.0
        DeviceFault.PACKET_LOSS -> 35.0
        DeviceFault.INTERMITTENT -> 6_000.0
        DeviceFault.MAGNETIC_INTERFERENCE -> 65.0
        else -> 0.0
    }
    fun effectiveMagnitude(fault: HardwareFault): Double = fault.magnitude.takeUnless { it == 0.0 } ?: defaultMagnitude(fault.type)
    fun requireValid(fault: HardwareFault) {
        require(fault.deviceId in deviceIds && fault.type in allowed(fault.deviceId)) { "This fault does not apply to ${fault.deviceId}" }
        require(fault.magnitude.isFinite()) { "Invalid fault magnitude" }
        val range = magnitudeRange(fault.type)
        require(if (range == null) fault.magnitude == 0.0 else fault.magnitude in range) { "Fault magnitude is out of range" }
    }
}

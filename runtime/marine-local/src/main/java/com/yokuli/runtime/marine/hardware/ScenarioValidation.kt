package com.yokuli.runtime.marine.hardware

import com.yokuli.runtime.contract.hardware.*

/** 场景导入与交互编辑共用边界；畸形数值不能进入真实解析器、位置完整性或持久化。 */
object ScenarioValidation {
    val deviceIds = HardwareFaultPolicy.deviceIds
    const val MAX_DURATION_MILLIS = 7 * 86_400_000L

    fun requireValid(scenario: HardwareScenario) {
        require(scenario.version == 1) { "Unsupported scenario version" }
        require(scenario.name.isNotBlank() && scenario.name.length <= 128 && scenario.name.none { it.isISOControl() }) { "Invalid scenario name" }
        point(scenario.latitude, scenario.longitude)
        number(scenario.speedKnots, 0.0, 100.0, "speed")
        angle(scenario.courseDegrees); angle(scenario.headingDegrees)
        number(scenario.accuracyMeters, .1, 10_000.0, "accuracy")
        number(scenario.heelDegrees, -75.0, 75.0, "heel")
        number(scenario.pitchDegrees, -60.0, 60.0, "pitch")
        number(scenario.rollPeriodSeconds, .5, 120.0, "roll period")
        number(scenario.depthMeters, 0.0, 12_000.0, "depth")
        number(scenario.windSpeedKnots, 0.0, 200.0, "wind speed"); angle(scenario.windDirectionDegrees)
        number(scenario.pressureHpa, 300.0, 1_200.0, "pressure")
        number(scenario.waterTemperatureC, -5.0, 60.0, "water temperature")
        require(scenario.waypoints.size <= 1_024 && scenario.targets.size <= 64 && scenario.events.size <= 2_048) { "Scenario exceeds its item limit" }
        scenario.waypoints.forEach { point(it.latitude, it.longitude) }
        scenario.targets.forEach(::target)
        require(scenario.targets.map { it.mmsi }.distinct().size == scenario.targets.size) { "Duplicate target MMSI" }
        require((scenario.targets.map { it.mmsi } + scenario.events.mapNotNull { it.target?.mmsi }).distinct().size <= 128) { "Too many AIS identities" }
        var previous = -1L
        scenario.events.forEach { event ->
            require(event.atMillis in 0..MAX_DURATION_MILLIS && event.atMillis >= previous) { "Scenario events must be ordered by time" }
            require(event.value.isFinite()) { "Invalid event number" }
            previous = event.atMillis
            when (event.action) {
                ScenarioAction.SET_SPEED -> number(event.value, 0.0, 100.0, "event speed")
                ScenarioAction.SET_COURSE -> angle(event.value)
                ScenarioAction.SET_WIND -> number(event.value, 0.0, 200.0, "event wind")
                ScenarioAction.SET_DEPTH -> number(event.value, 0.0, 12_000.0, "event depth")
                ScenarioAction.SET_PRESSURE -> number(event.value, 300.0, 1_200.0, "event pressure")
                ScenarioAction.ATTACH, ScenarioAction.DETACH, ScenarioAction.CLEAR_FAULT -> requireDevice(event.deviceId)
                ScenarioAction.FAULT -> {
                    val fault = requireNotNull(event.fault) { "Fault event needs a fault" }
                    requireValidFault(fault)
                    require(event.deviceId.isEmpty() || event.deviceId == fault.deviceId) { "Fault event has conflicting device identities" }
                }
                ScenarioAction.AIS_SPAWN -> target(requireNotNull(event.target) { "AIS event needs a target" })
                ScenarioAction.POWER -> requireValidPower(requireNotNull(event.power) { "Power event needs a value" })
                ScenarioAction.STORAGE -> Unit
            }
        }
    }

    fun requireDevice(id: String) = require(id in deviceIds) { "Unknown scenario device: $id" }
    fun requireValidPower(power: HardwarePower) {
        require(power.percent in 0..100 && power.thermal in 0..6) { "Invalid power state" }
    }

    fun requireValidFault(fault: HardwareFault) = HardwareFaultPolicy.requireValid(fault)

    internal fun target(target: ScenarioAisTarget) {
        require(target.mmsi in 1..999_999_999) { "Invalid target MMSI" }
        require(target.name.length <= 80 && target.name.none { it.isISOControl() }) { "Invalid target name" }
        point(target.latitude, target.longitude)
        number(target.speedKnots, 0.0, 100.0, "target speed")
        angle(target.courseDegrees); angle(target.headingDegrees)
    }
    internal fun point(latitude: Double, longitude: Double) {
        number(latitude, -90.0, 90.0, "latitude")
        number(longitude, -180.0, 180.0, "longitude")
    }
    private fun angle(value: Double) = number(value, 0.0, 360.0, "direction")
    private fun number(value: Double, minimum: Double, maximum: Double, name: String) =
        require(value.isFinite() && value in minimum..maximum) { "Invalid $name ($minimum–$maximum)" }
}

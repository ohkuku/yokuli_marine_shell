package com.yokuli.shell.contract

/** 仅控制呈现和用户输入；传感器、记录、几何与 NMEA 保持各自规范单位。 */
enum class LengthUnit { METERS, FEET }
enum class DepthUnit { METERS, FEET, FATHOMS }
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }
enum class PressureUnit { HECTOPASCALS, KILOPASCALS, INCHES_OF_MERCURY }

/**
 * 一份系统显示偏好。航程距离与速度沿用既有航海/公制设置；
 * 近距、船体尺寸与水深分别有明确单位，禁止页面根据数值自行猜测或改换单位。
 * 值对象也用作图表、静止磁贴和编辑器的缓存版本。
 */
data class MarineUnitPreferences(
    val navigation: MeasurementUnitSystem = MeasurementUnitSystem.NAUTICAL,
    val length: LengthUnit = LengthUnit.METERS,
    val depth: DepthUnit = DepthUnit.METERS,
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val pressure: PressureUnit = PressureUnit.HECTOPASCALS,
)

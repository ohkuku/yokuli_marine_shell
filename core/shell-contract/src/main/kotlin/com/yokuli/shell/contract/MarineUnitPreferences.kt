package com.yokuli.shell.contract

/** 仅控制呈现和用户输入；传感器、记录、几何与 NMEA 保持各自规范单位。 */
enum class LengthUnit { METERS, FEET }
enum class DepthUnit { METERS, FEET, FATHOMS }
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }
enum class PressureUnit { HECTOPASCALS, KILOPASCALS, INCHES_OF_MERCURY }
/** 全尺度距离单位；不会因数值变小自行切换到另一单位。 */
enum class DistanceUnit(val symbol: String, val metersPerUnit: Double) {
    NAUTICAL_MILES("nm", 1852.0), KILOMETERS("km", 1000.0), MILES("mi", 1609.344),
    METERS("m", 1.0), FEET("ft", .3048), YARDS("yd", .9144),
}
/** 与距离独立选择；内部速度仍为节。 */
enum class SpeedUnit(val symbol: String, val unitsPerKnot: Double) {
    KNOTS("kn", 1.0), KILOMETERS_PER_HOUR("km/h", 1.852), MILES_PER_HOUR("mph", 1.1507794480235425), METERS_PER_SECOND("m/s", 1852.0 / 3600.0),
}

/**
 * 一份系统显示偏好，也作为图表、磁贴和编辑器的缓存版本。
 * navigation 保留旧预设身份；新增 distance/speed 分别采用明确选择，未配置时兼容旧预设。
 * scaleDistance=null 表示比例尺跟随距离单位，任何地图不得自行按缩放猜 m/ft。
 */
data class MarineUnitPreferences(
    val navigation: MeasurementUnitSystem = MeasurementUnitSystem.NAUTICAL,
    val length: LengthUnit = LengthUnit.METERS,
    val depth: DepthUnit = DepthUnit.METERS,
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val pressure: PressureUnit = PressureUnit.HECTOPASCALS,
    val distance: DistanceUnit = if (navigation == MeasurementUnitSystem.NAUTICAL) DistanceUnit.NAUTICAL_MILES else DistanceUnit.KILOMETERS,
    val speed: SpeedUnit = if (navigation == MeasurementUnitSystem.NAUTICAL) SpeedUnit.KNOTS else SpeedUnit.KILOMETERS_PER_HOUR,
    val scaleDistance: DistanceUnit? = null,
) {
    val effectiveScaleDistance get() = scaleDistance ?: distance
    companion object {
        /** 前台、后台通知和地图统一解码，不能由各客户端给同一键设置不同默认值。 */
        fun fromStored(navigationName: String, values: Map<String, String>): MarineUnitPreferences {
            val navigation = enumValues<MeasurementUnitSystem>().firstOrNull { it.name == navigationName } ?: MeasurementUnitSystem.NAUTICAL
            val defaults = MarineUnitPreferences(navigation = navigation)
            fun value(kind: String) = values["preferences.units.$kind"]?.removePrefix("c:")
            return defaults.copy(
                length = enumValues<LengthUnit>().firstOrNull { it.name == value("length") } ?: defaults.length,
                depth = enumValues<DepthUnit>().firstOrNull { it.name == value("depth") } ?: defaults.depth,
                temperature = enumValues<TemperatureUnit>().firstOrNull { it.name == value("temperature") } ?: defaults.temperature,
                pressure = enumValues<PressureUnit>().firstOrNull { it.name == value("pressure") } ?: defaults.pressure,
                distance = enumValues<DistanceUnit>().firstOrNull { it.name == value("distance") } ?: defaults.distance,
                speed = enumValues<SpeedUnit>().firstOrNull { it.name == value("speed") } ?: defaults.speed,
                scaleDistance = enumValues<DistanceUnit>().firstOrNull { it.name == value("scale") },
            )
        }
    }
}

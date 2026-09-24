package com.yokuli.marine.shell.rebuild.scene.ais

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/** 场景只消费运行时事实；不能作为另一份目标仓库或风险计算器。 */
internal data class AisScenePosition(val latitude: Double, val longitude: Double) {
    val valid get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/** AIS 报告点到四条船体边界的广播距离，单位米。高度仍然只是类别示意。 */
internal data class AisSceneDimensions(val toBow: Double, val toStern: Double, val toPort: Double, val toStarboard: Double) {
    val reliable get() = listOf(toBow, toStern, toPort, toStarboard).all { it.isFinite() && it > 0.0 } && toBow + toStern <= 1022 && toPort + toStarboard <= 126
}

internal enum class AisSceneKind { VESSEL, AID_TO_NAVIGATION, BASE_STATION, AIRCRAFT, DISTRESS, UNKNOWN }

internal data class AisSceneTarget(
    val id: String,
    val label: String,
    val position: AisScenePosition,
    val headingDegrees: Double? = null,
    val cogDegrees: Double? = null,
    val sogMetersPerSecond: Double? = null,
    val dimensions: AisSceneDimensions? = null,
    val kind: AisSceneKind = AisSceneKind.VESSEL,
    val ageLabel: String = "",
    val statusLabel: String = "",
    val stale: Boolean = false,
    val lost: Boolean = false,
    val risk: Boolean = false,
    val followed: Boolean = false,
    /** 只接受已观测轨迹段，段与段之间绝不补线。 */
    val track: List<List<AisScenePosition>> = emptyList(),
)

internal data class AisSceneData(
    /** 只能传入当前被系统采纳且有效的本船位置；旧位置不能冒充当前参考。 */
    val ownPosition: AisScenePosition?,
    val ownHeadingDegrees: Double? = null,
    val ownCogDegrees: Double? = null,
    val ownSogMetersPerSecond: Double? = null,
    val ownDimensions: AisSceneDimensions? = null,
    val targets: List<AisSceneTarget>,
    /** 所有视图共享的真实对地向量时长；没有运动资料就不绘制。 */
    val vectorSeconds: Double = 180.0,
    val showTracks: Boolean = true,
)

internal enum class AisScenePreset { OVERVIEW, NORTH_TOP, BOW_FORWARD, ENCOUNTER }

/** 页面拥有相机，数据包不能改写用户的浏览位置。中心为 WGS84，旋转后仍保持同一地点。 */
internal data class AisSceneCameraState(
    val preset: AisScenePreset = AisScenePreset.OVERVIEW,
    val rangeMeters: Double = 1852.0,
    val bearingDegrees: Double = 0.0,
    val elevationDegrees: Double = 52.0,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val followOwn: Boolean = true,
) {
    companion object {
        val Saver = listSaver<AisSceneCameraState, Any>(
            save = { listOf(it.preset.name, it.rangeMeters, it.bearingDegrees, it.elevationDegrees, it.centerLatitude ?: Double.NaN, it.centerLongitude ?: Double.NaN, it.followOwn) },
            restore = { AisSceneCameraState(
                preset = runCatching { AisScenePreset.valueOf(it[0] as String) }.getOrDefault(AisScenePreset.OVERVIEW),
                rangeMeters = (it[1] as Double).coerceIn(100.0, 59264.0),
                bearingDegrees = it[2] as Double, elevationDegrees = it[3] as Double,
                centerLatitude = (it[4] as Double).takeIf(Double::isFinite), centerLongitude = (it[5] as Double).takeIf(Double::isFinite), followOwn = it[6] as Boolean,
            ) },
        )
    }
}

internal data class AisVector3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(p: AisVector3) = AisVector3(x + p.x, y + p.y, z + p.z)
    operator fun minus(p: AisVector3) = AisVector3(x - p.x, y - p.y, z - p.z)
    operator fun times(s: Double) = AisVector3(x * s, y * s, z * s)
    fun dot(p: AisVector3) = x * p.x + y * p.y + z * p.z
    fun cross(p: AisVector3) = AisVector3(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x)
    fun normalized(): AisVector3 { val length = sqrt(dot(this)).coerceAtLeast(1e-9); return this * (1.0 / length) }
}

/** WGS84 椭球 ECEF -> 局部东/北，全部双精度计算完成后才交给 GPU。 */
internal class AisLocalFrame(val origin: AisScenePosition) {
    private val lat = Math.toRadians(origin.latitude)
    private val lon = Math.toRadians(origin.longitude)
    private val base = ecef(origin)
    fun position(point: AisScenePosition): AisVector3 {
        val d = ecef(point) - base
        val east = -sin(lon) * d.x + cos(lon) * d.y
        val north = -sin(lat) * cos(lon) * d.x - sin(lat) * sin(lon) * d.y + cos(lat) * d.z
        // AIS 没有可靠水面高度，统一海平面；不会把地球曲率当成船舶下沉。
        // Filament 使用右手坐标：X 东、Y 上、Z 南，避免北向视图左右镜像。
        return AisVector3(east, 0.0, -north)
    }
    companion object {
        private fun ecef(p: AisScenePosition): AisVector3 {
            val latitude = Math.toRadians(p.latitude); val longitude = Math.toRadians(p.longitude)
            val eccentricitySquared = 6.69437999014e-3
            val n = 6378137.0 / sqrt(1.0 - eccentricitySquared * sin(latitude).pow(2))
            return AisVector3(n * cos(latitude) * cos(longitude), n * cos(latitude) * sin(longitude), n * (1.0 - eccentricitySquared) * sin(latitude))
        }
    }
}

internal data class AisSceneCamera(
    val eye: AisVector3, val target: AisVector3, val up: AisVector3,
    val halfWidth: Double, val halfHeight: Double, val clipFar: Double,
) {
    val forward = (target - eye).normalized()
    val right = forward.cross(up).normalized()
    val screenUp = right.cross(forward)
    fun project(point: AisVector3): Offset {
        val p = point - target
        return Offset((0.5 + p.dot(right) / (2.0 * halfWidth)).toFloat(), (0.5 - p.dot(screenUp) / (2.0 * halfHeight)).toFloat())
    }
}

internal fun validAisBearing(value: Double?) = value?.takeIf { it.isFinite() && it >= 0.0 && it < 360.0 }

internal data class AisSceneFrame(val local: AisLocalFrame, val camera: AisSceneCamera, val targets: List<AisSceneTarget>, val referenceOnly: Boolean)

internal fun aisSceneFrame(data: AisSceneData, state: AisSceneCameraState, aspect: Double): AisSceneFrame? {
    val own = data.ownPosition?.takeIf { it.valid }
    val manualCenter = if (state.centerLatitude != null && state.centerLongitude != null) AisScenePosition(state.centerLatitude, state.centerLongitude).takeIf { it.valid } else null
    val origin = own ?: manualCenter ?: data.targets.firstOrNull { it.position.valid }?.position ?: return null
    val local = AisLocalFrame(origin)
    val center = if (state.followOwn) own ?: manualCenter ?: origin else manualCenter ?: origin
    val target = local.position(center)
    val safeAspect = aspect.takeIf { it.isFinite() && it > 0 } ?: 1.0
    val range = state.rangeMeters.takeIf { it.isFinite() }?.coerceIn(100.0, 59264.0) ?: 1852.0
    val halfWidth = range * max(1.0, safeAspect)
    val halfHeight = range * max(1.0, 1.0 / safeAspect)
    val isForward = state.preset == AisScenePreset.BOW_FORWARD && validAisBearing(data.ownHeadingDegrees) != null
    val bearing = if (isForward && state.followOwn) data.ownHeadingDegrees!! else state.bearingDegrees
    val elevation = if (state.preset == AisScenePreset.NORTH_TOP) 89.95 else if (isForward) 23.0 else state.elevationDegrees.coerceIn(28.0, 78.0)
    val angle = Math.toRadians(bearing); val pitch = Math.toRadians(elevation)
    val distance = range * 3.2
    val eye = target + AisVector3(-sin(angle) * cos(pitch) * distance, sin(pitch) * distance, cos(angle) * cos(pitch) * distance)
    return AisSceneFrame(local, AisSceneCamera(eye, target, AisVector3(0.0, 1.0, 0.0), halfWidth, halfHeight, range * 12.0), data.targets.filter { it.position.valid }.distinctBy { it.id }, own == null)
}

/** 仅用于用户镜头中心，不写回 AIS 仓库。跨日界线取短方向。 */
internal fun aisSceneMidpoint(a: AisScenePosition, b: AisScenePosition): AisScenePosition {
    val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
    val dl = Math.toRadians((b.longitude - a.longitude + 540.0) % 360.0 - 180.0)
    val bx = cos(lat2) * cos(dl); val by = cos(lat2) * sin(dl)
    val lat = atan2(sin(lat1) + sin(lat2), sqrt((cos(lat1) + bx).pow(2) + by * by))
    val lon = Math.toRadians(a.longitude) + atan2(by, cos(lat1) + bx)
    return AisScenePosition(Math.toDegrees(lat), (Math.toDegrees(lon) + 540.0) % 360.0 - 180.0)
}

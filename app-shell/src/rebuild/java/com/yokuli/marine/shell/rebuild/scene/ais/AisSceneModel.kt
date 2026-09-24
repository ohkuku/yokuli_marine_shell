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
    val elevationDegrees: Double = 48.0,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val followOwn: Boolean = true,
) {
    companion object {
        val Saver = listSaver<AisSceneCameraState, Any>(
            save = { listOf(it.preset.name, it.rangeMeters, it.bearingDegrees, it.elevationDegrees, it.centerLatitude ?: Double.NaN, it.centerLongitude ?: Double.NaN, it.followOwn) },
            restore = { AisSceneCameraState(
                preset = runCatching { AisScenePreset.valueOf(it[0] as String) }.getOrDefault(AisScenePreset.OVERVIEW),
                rangeMeters = (it[1] as Double).takeIf(Double::isFinite)?.coerceIn(100.0, 59264.0) ?: 1852.0,
                bearingDegrees = (it[2] as Double).takeIf(Double::isFinite)?.let { value -> (value % 360.0 + 360.0) % 360.0 } ?: 0.0,
                elevationDegrees = (it[3] as Double).takeIf(Double::isFinite)?.coerceIn(20.0, 78.0) ?: 48.0,
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
    private val sinLat = sin(lat)
    private val cosLat = cos(lat)
    private val sinLon = sin(lon)
    private val cosLon = cos(lon)
    private val base = ecef(origin)
    // 相机旋转不改变局部坐标。保留有界换算结果，避免每一帧为标签、命中和轨迹重复算经纬度。
    private val positions = object : LinkedHashMap<AisScenePosition, AisVector3>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AisScenePosition, AisVector3>?) = size > 8192
    }
    fun position(point: AisScenePosition): AisVector3 {
        positions[point]?.let { return it }
        val d = ecef(point) - base
        val east = -sinLon * d.x + cosLon * d.y
        val north = -sinLat * cosLon * d.x - sinLat * sinLon * d.y + cosLat * d.z
        // AIS 没有可靠水面高度，统一海平面；不会把地球曲率当成船舶下沉。
        // Filament 使用右手坐标：X 东、Y 上、Z 南，避免北向视图左右镜像。
        return AisVector3(east, 0.0, -north).also { positions[point] = it }
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
    /** 非空时为本船视点的真实透视视角；海图式概览仍用正交。 */
    val verticalFovDegrees: Double? = null,
    val aspect: Double = 1.0,
    val clipNear: Double = .1,
) {
    val forward = (target - eye).normalized()
    val right = forward.cross(up).normalized()
    val screenUp = right.cross(forward)
    fun depth(point: AisVector3) = (point - eye).dot(forward)
    fun project(point: AisVector3): Offset {
        val p = point - eye
        val depth = p.dot(forward)
        // 与 GPU 相同的前后裁面。不可见点不能再参与标签、拾取、视野计数或连线。
        if (!depth.isFinite() || depth <= clipNear || depth >= clipFar) return Offset(Float.NaN, Float.NaN)
        val height = verticalFovDegrees?.let { depth * tan(Math.toRadians(it * .5)) } ?: halfHeight
        val width = if (verticalFovDegrees != null) height * aspect else halfWidth
        return Offset((.5 + p.dot(right) / (2.0 * width)).toFloat(), (.5 - p.dot(screenUp) / (2.0 * height)).toFloat())
    }
    /** 在该深度下的屏幕像素比例，同一相机供原生船模与平面符号使用。 */
    fun metersPerPixel(point: AisVector3, viewportWidth: Int): Double {
        val width = verticalFovDegrees?.let { depth(point).coerceAtLeast(clipNear) * tan(Math.toRadians(it * .5)) * aspect } ?: halfWidth
        return 2.0 * width / viewportWidth.coerceAtLeast(1)
    }
    fun clipSegment(start: AisVector3, end: AisVector3): Pair<AisVector3, AisVector3>? {
        val from = depth(start); val to = depth(end)
        if (!from.isFinite() || !to.isFinite()) return null
        val delta = to - from
        var low = 0.0; var high = 1.0
        val near = clipNear + .001; val far = clipFar - .001
        if (abs(delta) < 1e-9) return if (from in near..far) start to end else null
        val enter = (near - from) / delta; val leave = (far - from) / delta
        low = max(low, min(enter, leave)); high = min(high, max(enter, leave))
        return if (low <= high) (start + (end - start) * low) to (start + (end - start) * high) else null
    }
}

internal fun validAisBearing(value: Double?) = value?.takeIf { it.isFinite() && it >= 0.0 && it < 360.0 }

internal data class AisSceneFrame(val local: AisLocalFrame, val camera: AisSceneCamera, val targets: List<AisSceneTarget>, val referenceOnly: Boolean) {
    /** 同一帧的原生模型、文字、拾取、视野计数共用投影，不各自遍历换算。 */
    val targetPositions = targets.associate { it.id to local.position(it.position) }
    val targetProjections = targetPositions.mapValues { camera.project(it.value) }
}

internal fun aisSceneFrame(data: AisSceneData, state: AisSceneCameraState, aspect: Double, previousLocal: AisLocalFrame? = null): AisSceneFrame? {
    val own = data.ownPosition?.takeIf { it.valid }
    val manualCenter = if (state.centerLatitude != null && state.centerLongitude != null) AisScenePosition(state.centerLatitude, state.centerLongitude).takeIf { it.valid } else null
    val origin = own ?: manualCenter ?: data.targets.firstOrNull { it.position.valid }?.position ?: return null
    val local = previousLocal?.takeIf { it.origin == origin } ?: AisLocalFrame(origin)
    val center = if (state.followOwn) own ?: manualCenter ?: origin else manualCenter ?: origin
    val target = local.position(center)
    val safeAspect = aspect.takeIf { it.isFinite() && it > 0 } ?: 1.0
    val range = state.rangeMeters.takeIf { it.isFinite() }?.coerceIn(100.0, 59264.0) ?: 1852.0
    val halfWidth = range * max(1.0, safeAspect)
    val halfHeight = range * max(1.0, 1.0 / safeAspect)
    val wantsForward = state.preset == AisScenePreset.BOW_FORWARD
    val isForward = wantsForward && own != null && validAisBearing(data.ownHeadingDegrees) != null
    val bearing = if (isForward) data.ownHeadingDegrees!! else if (wantsForward) 0.0 else state.bearingDegrees.takeIf(Double::isFinite) ?: 0.0
    val angle = Math.toRadians(bearing)
    val pose = if (isForward) {
        // 船桥视点固定在本船，不再把低角度正交概览叫作前视。视点高度为示意，
        // 不作为传感器事实；缺少实际艏向时仅当前绘制降级，不覆写用户相机偏好。
        val reference = local.position(own!!)
        val eyeHeight = data.ownDimensions?.takeIf { it.reliable }?.let { (it.toBow + it.toStern) * .12 }?.coerceIn(4.0, 18.0) ?: 6.0
        val eye = reference + AisVector3(0.0, eyeHeight, 0.0)
        val aim = eye + AisVector3(sin(angle) * range, -tan(Math.toRadians(6.0)) * range, -cos(angle) * range)
        AisSceneCamera(eye, aim, AisVector3(0.0, 1.0, 0.0), halfWidth, halfHeight, max(1000.0, range * 4.0),
            verticalFovDegrees = (58.0 * (range / 1852.0).pow(.25)).coerceIn(25.0, 85.0), aspect = safeAspect, clipNear = .5)
    } else {
        val elevation = if (state.preset == AisScenePreset.NORTH_TOP || wantsForward) 89.95 else state.elevationDegrees.takeIf(Double::isFinite)?.coerceIn(20.0, 78.0) ?: 48.0
        val pitch = Math.toRadians(elevation)
        val distance = range * 3.2
        val eye = target + AisVector3(-sin(angle) * cos(pitch) * distance, sin(pitch) * distance, cos(angle) * cos(pitch) * distance)
        AisSceneCamera(eye, target, AisVector3(0.0, 1.0, 0.0), halfWidth, halfHeight, range * 12.0)
    }
    return AisSceneFrame(local, pose, data.targets.filter { it.position.valid }.distinctBy { it.id }, own == null)
}

/** 显示用几何。GPU 模型、屏幕命中体积共用同一尺寸；不参与距离或风险计算。 */
internal data class AisDisplayGeometry(
    val isShip: Boolean,
    val position: AisVector3,
    val headingRadians: Double,
    val length: Double,
    val beam: Double,
    val heightScale: Double,
) {
    fun transform(point: AisVector3) = position + AisVector3(
        cos(headingRadians) * point.x * beam - sin(headingRadians) * point.z * length,
        point.y * heightScale,
        sin(headingRadians) * point.x * beam + cos(headingRadians) * point.z * length,
    )
    fun matrix() = floatArrayOf(
        (cos(headingRadians) * beam).toFloat(), 0f, (sin(headingRadians) * beam).toFloat(), 0f,
        0f, heightScale.toFloat(), 0f, 0f,
        (-sin(headingRadians) * length).toFloat(), 0f, (cos(headingRadians) * length).toFloat(), 0f,
        position.x.toFloat(), position.y.toFloat(), position.z.toFloat(), 1f,
    )
    /** 对应包内 GLB 的边界；包含甲板/船桥高度，而非只接受海平面上的点击。 */
    fun boundsFaces(): List<List<AisVector3>> {
        val bottom = if (isShip) -.1 else 0.0
        val top = if (isShip) .32 else .65
        val vertices = listOf(
            AisVector3(-.5, bottom, -.5), AisVector3(.5, bottom, -.5), AisVector3(.5, bottom, .5), AisVector3(-.5, bottom, .5),
            AisVector3(-.5, top, -.5), AisVector3(.5, top, -.5), AisVector3(.5, top, .5), AisVector3(-.5, top, .5),
        ).map(::transform)
        return listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6, 7), listOf(0, 1, 5, 4), listOf(1, 2, 6, 5), listOf(2, 3, 7, 6), listOf(3, 0, 4, 7))
            .map { face -> face.map(vertices::get) }
    }
}

internal fun aisDisplayGeometry(target: AisSceneTarget, frame: AisSceneFrame, viewportWidth: Int, density: Float): AisDisplayGeometry {
    val isShip = target.kind == AisSceneKind.VESSEL && validAisBearing(target.headingDegrees) != null
    val point = frame.targetPositions[target.id] ?: frame.local.position(target.position)
    val dims = target.dimensions?.takeIf { it.reliable && isShip }
    val heading = Math.toRadians(if (isShip) target.headingDegrees!! else 0.0)
    val metersPerPixel = frame.camera.metersPerPixel(point, viewportWidth).coerceAtLeast(.01)
    val forward = AisVector3(sin(heading), 0.0, -cos(heading))
    val screenLength = hypot(forward.dot(frame.camera.right), forward.dot(frame.camera.screenUp)).coerceAtLeast(.28)
    val symbolicLength = (26.0 * density.coerceAtLeast(1f) * metersPerPixel / screenLength).coerceAtLeast(3.0)
    val reportedLength = dims?.let { it.toBow + it.toStern }
    val length = max(reportedLength ?: 0.0, symbolicLength)
    val actualSize = reportedLength != null && reportedLength >= symbolicLength
    val beam = if (actualSize) dims!!.toPort + dims.toStarboard else length * if (isShip) .34 else .56
    val xOffset = dims?.takeIf { actualSize }?.let { (it.toStarboard - it.toPort) * .5 } ?: 0.0
    val zOffset = dims?.takeIf { actualSize }?.let { (it.toBow - it.toStern) * .5 } ?: 0.0
    val position = point + AisVector3(cos(heading) * xOffset + sin(heading) * zOffset, 0.0, sin(heading) * xOffset - cos(heading) * zOffset)
    val heightScale = if (isShip) if (actualSize) length.coerceAtMost(140.0) else length * .6 else symbolicLength * .45
    return AisDisplayGeometry(isShip, position, heading, length, beam, heightScale)
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

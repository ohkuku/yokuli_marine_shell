package com.yokuli.marine.shell.rebuild.scene.ais

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset
import com.yokuli.marine.shell.rebuild.scene.navigation.NavigationPositionMotion
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

/** 只按 AIS 报告类别选示意轮廓；不推断实际外形、装载或帆况。 */
internal enum class AisVesselForm(val top: Double) {
    GENERIC(.32), SAILING(.93), MOTOR(.32), FISHING(.39), TUG(.40), PASSENGER(.32), CARGO(.33), TANKER(.33);
    companion object {
        fun fromShipType(value: Int?): AisVesselForm = when(value ?: 0) {
            36 -> SAILING
            37 -> MOTOR
            30 -> FISHING
            31,32,52 -> TUG
            in 60..69 -> PASSENGER
            in 70..79 -> CARGO
            in 80..89 -> TANKER
            else -> GENERIC
        }
    }
}

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
    val form: AisVesselForm = AisVesselForm.GENERIC,
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
    /** 本船姿态来自系统已经选用、校准后的观测；缺失仍为 null，绝不套用到他船。 */
    val ownHeelDegrees: Double? = null,
    val ownPitchDegrees: Double? = null,
    /** 原始船位的单调观测时刻，仅供显示插值，不刷新 AIS 风险依据。 */
    val ownPositionElapsedMillis: Long? = null,
)

internal enum class AisScenePreset { OVERVIEW, NORTH_TOP, BOW_FORWARD, ENCOUNTER }

/** 页面拥有相机，数据包不能改写用户的浏览位置。中心为 WGS84，旋转后仍保持同一地点。 */
internal data class AisSceneCameraState(
    val preset: AisScenePreset = AisScenePreset.OVERVIEW,
    val rangeMeters: Double = 1852.0,
    val bearingDegrees: Double = 0.0,
    val elevationDegrees: Double = 32.0,
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
    /** 所有交通视角采用透视；距离尺度由相机目标平面的米制范围决定。 */
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

internal data class AisSceneFrame(
    val local: AisLocalFrame, val camera: AisSceneCamera, val targets: List<AisSceneTarget>, val referenceOnly: Boolean,
    /** 仅本次出帧的已观测位置插值；不会写回 AIS 风险、距离、年龄或历史。 */
    val displayedPositions: Map<String,AisVector3> = emptyMap(),
    val displayedHeadings: Map<String,Double> = emptyMap(),
    val displayedOwnPosition: AisVector3? = null,
    /** 观测更新时计算的局部坐标；相机出帧不重复转换整个交通目录。 */
    val observedPositions: Map<String,AisVector3> = emptyMap(),
) {
    /** 同一帧的原生模型、文字、拾取、视野计数共用投影，不各自遍历换算。 */
    val targetPositions: Map<String,AisVector3> = when {
        observedPositions.isNotEmpty() && displayedPositions.isEmpty() -> observedPositions
        observedPositions.isNotEmpty() -> LinkedHashMap(observedPositions).apply { putAll(displayedPositions) }
        else -> targets.associate { it.id to (displayedPositions[it.id] ?: local.position(it.position)) } + displayedPositions.filterKeys { it == AisTrafficRenderer3D.OWN_ID }
    }
    val targetProjections = targetPositions.mapValues { camera.project(it.value) }
}

/**
 * 仅为呈现选取有界目标；原 AIS 仓库/警报不裁剪。原始目录在观测变化时投影到本地，
 * 镜头跨出缓冲区域或明显转向才重新挑选；手势/插值帧最多处理 128 个模型及符号。
 */
internal class AisSceneTargetIndex {
    private var facts: List<AisSceneTarget>? = null
    private var frame: AisLocalFrame? = null
    private var coordinates = emptyMap<String,AisVector3>()
    private var previousCamera: AisSceneCamera? = null
    private var previousSelected: String? = null
    var targets: List<AisSceneTarget> = emptyList(); private set
    var positions: Map<String,AisVector3> = emptyMap(); private set

    fun clear() { facts=null;frame=null;coordinates=emptyMap();previousCamera=null;previousSelected=null;targets=emptyList();positions=emptyMap() }

    fun update(values: List<AisSceneTarget>, local: AisLocalFrame, camera: AisSceneCamera, selected: String?) {
        val factsChanged = facts !== values || frame !== local
        if (factsChanged) {
            facts = values; frame = local
            coordinates = values.asSequence().filter { it.position.valid }.associate { it.id to local.position(it.position) }
        }
        val previous = previousCamera
        if (!factsChanged && previousSelected == selected && previous != null &&
            (previous.target-camera.target).let { it.dot(it) } < max(20.0,camera.halfHeight*.12).pow(2) &&
            previous.forward.dot(camera.forward) > .994 &&
            abs(previous.halfHeight-camera.halfHeight) < camera.halfHeight*.12) return
        previousCamera = camera; previousSelected = selected
        targets = values.asSequence().filter { it.id in coordinates }.distinctBy { it.id }.filter { target ->
            target.id == selected || target.risk || camera.project(coordinates.getValue(target.id)).let { it.x in -.6f..1.6f && it.y in -.6f..1.6f }
        }.sortedWith(compareByDescending<AisSceneTarget> { it.id == selected }
            .thenByDescending { it.risk }.thenByDescending { it.followed }
            .thenBy { (coordinates.getValue(it.id)-camera.target).let { p -> p.dot(p) } }.thenBy { it.id })
            .take(128).toList()
        positions = targets.associate { it.id to coordinates.getValue(it.id) }
    }
}

internal fun aisSceneFrame(data: AisSceneData, state: AisSceneCameraState, aspect: Double, previousLocal: AisLocalFrame? = null,
    targetIndex: AisSceneTargetIndex? = null, selectedId: String? = null): AisSceneFrame? {
    val own = data.ownPosition?.takeIf { it.valid }
    val manualCenter = if (state.centerLatitude != null && state.centerLongitude != null) AisScenePosition(state.centerLatitude, state.centerLongitude).takeIf { it.valid } else null
    val origin = (if(state.followOwn) own ?: manualCenter else manualCenter ?: own) ?: data.targets.firstOrNull { it.position.valid }?.position ?: return null
    // 经纬度微动不能每次换局部坐标系，否则船位、地形与相机缓动都会跳基准。
    val local = previousLocal?.takeIf { it.position(origin).let { p -> hypot(p.x,p.z) < 32_000.0 } } ?: AisLocalFrame(origin)
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
        // 跟随镜头从本船后上方看向真实艏向；位置和船模同时可见，不把雷达平面倾斜。
        // 镜头高度与距离是用户视角，不代表传感器测量或船舶真实高度。
        val reference = local.position(own!!)
        val chaseDistance = (range * .34).coerceIn(70.0, 3_000.0)
        val ahead = (range * .20).coerceIn(35.0, 1_500.0)
        val eye = reference + AisVector3(-sin(angle)*chaseDistance, chaseDistance*.55, cos(angle)*chaseDistance)
        val aim = reference + AisVector3(sin(angle)*ahead, 0.0, -cos(angle)*ahead)
        AisSceneCamera(eye, aim, AisVector3(0.0, 1.0, 0.0), halfWidth, halfHeight, max(2_000.0, range * 8.0),
            verticalFovDegrees = 48.0, aspect = safeAspect, clipNear = .5)
    } else {
        val elevation = if (state.preset == AisScenePreset.NORTH_TOP || wantsForward) 52.0 else state.elevationDegrees.takeIf(Double::isFinite)?.coerceIn(20.0, 78.0) ?: 32.0
        val pitch = Math.toRadians(elevation)
        val fov = 48.0
        val distance = halfHeight / tan(Math.toRadians(fov * .5))
        val eye = target + AisVector3(-sin(angle) * cos(pitch) * distance, sin(pitch) * distance, cos(angle) * cos(pitch) * distance)
        AisSceneCamera(eye, target, AisVector3(0.0, 1.0, 0.0), halfWidth, halfHeight, range * 14.0,
            verticalFovDegrees = fov, aspect = safeAspect, clipNear = .5)
    }
    val index = targetIndex ?: AisSceneTargetIndex()
    index.update(data.targets,local,pose,selectedId)
    return AisSceneFrame(local, pose, index.targets, own == null, observedPositions=index.positions)
}

/** 显示用几何。GPU 模型、屏幕命中体积共用同一尺寸；不参与距离或风险计算。 */
internal data class AisDisplayGeometry(
    val isShip: Boolean,
    val position: AisVector3,
    val headingRadians: Double,
    val length: Double,
    val beam: Double,
    val heightScale: Double,
    val top: Double = .32,
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
        val top = if (isShip) this.top else .65
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
    val heading = Math.toRadians(if (isShip) frame.displayedHeadings[target.id] ?: target.headingDegrees!! else 0.0)
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
    return AisDisplayGeometry(isShip, position, heading, length, beam, heightScale, target.form.top)
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

/** 相机缓动只改变投影，不补算船位；原生模型、覆盖线、文字命中共用出帧相机。 */
internal class AisSceneCameraMotion {
    private var shown: AisSceneCamera? = null
    private var lastFrameNanos = 0L
    var moving = false; private set
    fun rebase(offset:AisVector3) { shown=shown?.let {it.copy(eye=it.eye+offset,target=it.target+offset)} }
    fun advance(target: AisSceneCamera, nanos: Long, immediate:Boolean=false): AisSceneCamera {
        val previous = shown
        if (previous == null || lastFrameNanos == 0L || immediate) {
            shown = target; lastFrameNanos = nanos; moving = false; return target
        }
        val dt = if (nanos - lastFrameNanos > 500_000_000L) 1.0 / 60.0 else ((nanos - lastFrameNanos) / 1e9).coerceIn(0.0, .05)
        lastFrameNanos = nanos
        val amount = 1.0 - exp(-dt / .11)
        fun mix(a: Double, b: Double) = a + (b - a) * amount
        fun vector(a: AisVector3, b: AisVector3) = a + (b - a) * amount
        val tolerance = max(.005, target.halfHeight * .00002)
        val error = (target.eye - previous.eye).let { sqrt(it.dot(it)) } +
            (target.target - previous.target).let { sqrt(it.dot(it)) } + abs(target.halfHeight - previous.halfHeight)
        moving = error > tolerance || abs((target.verticalFovDegrees ?: 48.0) - (previous.verticalFovDegrees ?: 48.0)) > .005
        val result = if (!moving) target else target.copy(
            eye = vector(previous.eye, target.eye), target = vector(previous.target, target.target),
            up = vector(previous.up, target.up).normalized(),
            halfWidth = mix(previous.halfWidth, target.halfWidth), halfHeight = mix(previous.halfHeight, target.halfHeight),
            verticalFovDegrees = mix(previous.verticalFovDegrees ?: 48.0, target.verticalFovDegrees ?: 48.0),
            clipFar = max(previous.clipFar, target.clipFar),
        )
        shown = result
        return result
    }
}

/** 视觉插值只追赶已收到的报告，不按速度外推；丢失/过期目标立即保留最后事实位置。 */
internal class AisObservedMotion {
    private data class Shown(var point:AisVector3,var heading:Double?)
    private val targets=mutableMapOf<String,Shown>()
    private val own=NavigationPositionMotion()
    private var ownHeading:Double?=null
    private var local:AisLocalFrame?=null
    var moving=false;private set
    fun reset() { targets.clear();own.clear();ownHeading=null;local=null;moving=false }
    fun advance(frame:AisSceneFrame,data:AisSceneData,dt:Double,animatedIds:Set<String>,camera:AisSceneCamera=frame.camera):AisSceneFrame {
        if(local !== frame.local){
            local?.let {old->
                val shift=frame.local.position(old.origin)
                own.rebase(shift.x,shift.z)
                targets.values.forEach {it.point+=shift}
            }
            local=frame.local
        }
        moving=false
        val amount=1-exp(-dt/.24)
        val positions=HashMap<String,AisVector3>(animatedIds.size+1)
        val headings=mutableMapOf<String,Double>()
        val ids=frame.targets.mapTo(mutableSetOf()){it.id}
        targets.keys.retainAll(ids.intersect(animatedIds))
        for(target in frame.targets){
            if(target.id !in animatedIds)continue
            val next=frame.targetPositions[target.id]?:frame.local.position(target.position)
            val heading=validAisBearing(target.headingDegrees)
            val shown=targets.getOrPut(target.id){Shown(next,heading)}
            val gap=hypot(next.x-shown.point.x,next.z-shown.point.z)
            // 大跳变不能慢慢漂过陆地；这是图形去抖阈值，不是风险判定。
            if(target.stale||target.lost||gap>max(500.0,frame.camera.halfHeight*.5))shown.point=next
            else if(gap>.005){shown.point+= (next-shown.point)*amount;moving=true}else shown.point=next
            if(heading==null)shown.heading=null
            else {
                val delta=shown.heading?.let{(heading-it+540.0)%360.0-180.0}?:0.0
                shown.heading=shown.heading?.let{(it+delta*amount+360.0)%360.0}?:heading
                if(abs(delta)>.02)moving=true
                headings[target.id]=shown.heading!!
            }
            positions[target.id]=shown.point
        }
        val actualOwn=data.ownPosition?.takeIf{it.valid}?.let(frame.local::position)
        val displayedOwn=actualOwn?.let {point->
            own.update(point.x,point.z,data.ownPositionElapsedMillis?:0L,dt,max(500.0,frame.camera.halfHeight*.5))
            if(hypot(own.x-point.x,own.z-point.z)>.005)moving=true
            AisVector3(own.x,0.0,own.z).also{positions[AisTrafficRenderer3D.OWN_ID]=it}
        }
        if(actualOwn==null)own.clear()
        validAisBearing(data.ownHeadingDegrees)?.let {heading->
            val delta=ownHeading?.let{(heading-it+540.0)%360.0-180.0}?:0.0
            ownHeading=ownHeading?.let{(it+delta*amount+360.0)%360.0}?:heading
            if(abs(delta)>.02)moving=true
            headings[AisTrafficRenderer3D.OWN_ID]=ownHeading!!
        }?:run{ownHeading=null}
        return frame.copy(camera=camera,displayedPositions=positions,displayedHeadings=headings,displayedOwnPosition=displayedOwn)
    }
}

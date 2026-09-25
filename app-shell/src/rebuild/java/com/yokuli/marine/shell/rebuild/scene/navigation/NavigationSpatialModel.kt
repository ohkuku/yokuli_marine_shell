package com.yokuli.marine.shell.rebuild.scene.navigation

import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.opengl.Matrix
import android.view.Surface
import com.yokuli.anchorwatch.location.vessel.DeviceViewOrientationSample
import com.yokuli.anchorwatch.location.vessel.SensorQuaternion
import kotlin.math.*

/** 显示专属快照；目标来自唯一导航会话，不能在画面内推进航点或选源。 */
data class SpatialNavigationTarget(
    val id: String,
    val name: String,
    val bearingTrueDegrees: Double?,
    val distanceMeters: Double?,
    val nearTarget: Boolean = false,
    /** 几何引导点不是可完成的业务目标，不能冒充目的地。 */
    val steering: Boolean = false,
)
enum class SpatialMountMode { HANDHELD, VESSEL_MOUNTED }
/** 已经解析为真北的方向，仍保留来源与原始观测年龄，不能用COG补Heading。 */
data class SpatialDirection(val trueDegrees: Double, val source: String, val ageMillis: Long, val observedElapsedMillis:Long?=null)
data class SpatialReferencePosition(
    val latitude: Double,
    val longitude: Double,
    val observedUtcMillis: Long,
    val altitudeMeters: Double? = null,
)
data class NavigationSpatialSnapshot(
    val current: SpatialNavigationTarget?,
    val next: SpatialNavigationTarget? = null,
    val vesselHeading: SpatialDirection? = null,
    val courseOverGround: SpatialDirection? = null,
    val mountMode: SpatialMountMode = SpatialMountMode.HANDHELD,
    val position: SpatialReferencePosition? = null,
    val vesselHeelDegrees: Double? = null,
    val vesselPitchDegrees: Double? = null,
    val live: Boolean = false,
    val issueText: String? = null,
    val steering: SpatialNavigationTarget? = null,
)

/**
 * Android磁场模型的证据；不使用海图/假定位推断磁差。
 * 公共API不暴露设备内置模型的版本；其文档WMM2020名义有效期至2025，
 * 之后数年仅近似适用。因此此处始终是模型估算，不声明精确测得的真北。
 */
data class SpatialNorthConversion(val declinationDegrees: Double, val position: SpatialReferencePosition, val modelTimeUtcMillis: Long) {
    companion object {
        fun from(position: SpatialReferencePosition?, nowUtc: Long): SpatialNorthConversion? {
            val p = position ?: return null
            if (!p.latitude.isFinite() || !p.longitude.isFinite() || p.latitude !in -89.5..89.5 || p.longitude !in -180.0..180.0 ||
                p.observedUtcMillis <= 0L || nowUtc - p.observedUtcMillis !in 0..86_400_000L) return null
            return runCatching {
                GeomagneticField(p.latitude.toFloat(), p.longitude.toFloat(), (p.altitudeMeters ?: 0.0).toFloat(), nowUtc)
                    .declination.toDouble().takeIf { it.isFinite() }?.let { SpatialNorthConversion(it, p, nowUtc) }
            }.getOrNull()
        }
    }
}

internal data class SpatialVector(val x: Double, val y: Double, val z: Double) {
    operator fun plus(v: SpatialVector) = SpatialVector(x + v.x, y + v.y, z + v.z)
    fun scale(v: Double) = SpatialVector(x * v, y * v, z * v)
}
internal fun SensorQuaternion.rotate(x: Double, y: Double, z: Double): SpatialVector {
    val p = this * SensorQuaternion(0.0, x, y, z) * inverse()
    return SpatialVector(p.x, p.y, p.z)
}
internal fun wrapBearing(value: Double) = ((value % 360.0) + 360.0) % 360.0
internal fun signedBearing(value: Double) = ((value + 540.0) % 360.0) - 180.0
internal fun bearingVector(degrees: Double, radius: Double = 10.0, height: Double = 1.6): SpatialVector {
    val a = Math.toRadians(degrees)
    return SpatialVector(sin(a) * radius, height, -cos(a) * radius)
}
internal fun worldVector(enu: SpatialVector, declination: Double): SpatialVector {
    val d = Math.toRadians(declination)
    return SpatialVector(enu.x * cos(d) + enu.y * sin(d), enu.z, -(enu.y * cos(d) - enu.x * sin(d)))
}
internal data class SpatialCamera(
    val forward: SpatialVector,
    val up: SpatialVector,
    val trueBearing: Double?,
    val issue: String? = null,
    val free: Boolean = false,
)
internal fun resolveSpatialCamera(
    snapshot: NavigationSpatialSnapshot,
    sample: DeviceViewOrientationSample,
    shown: SensorQuaternion?,
    rotation: Int,
    conversion: SpatialNorthConversion?,
    nowElapsed: Long,
    freeYaw: Double?,
    freePitch: Double,
): SpatialCamera {
    fun at(bearing: Double, pitch: Double, issue: String? = null, free: Boolean = false): SpatialCamera {
        val a = Math.toRadians(bearing); val p = Math.toRadians(pitch)
        return SpatialCamera(SpatialVector(sin(a) * cos(p), sin(p), -cos(a) * cos(p)), SpatialVector(0.0, 1.0, 0.0), bearing, issue, free)
    }
    if (freeYaw != null) return at(wrapBearing(freeYaw), freePitch.coerceIn(-60.0, 50.0), free = true)
    if (snapshot.mountMode == SpatialMountMode.VESSEL_MOUNTED) {
        val heading = snapshot.vesselHeading?.takeIf { it.trueDegrees.isFinite() && it.ageMillis in 0..10_000L }
        return if (heading != null) {
            val base = at(heading.trueDegrees, (snapshot.vesselPitchDegrees ?: 0.0).coerceIn(-60.0, 60.0))
            val heel = Math.toRadians((snapshot.vesselHeelDegrees ?: 0.0).coerceIn(-70.0, 70.0))
            val a = Math.toRadians(heading.trueDegrees)
            base.copy(up = SpatialVector(cos(a) * sin(heel), cos(heel), sin(a) * sin(heel)))
        } else at(0.0, -12.0, "heading")
    }
    if (!sample.sensorAvailable) return at(0.0, -12.0, "sensor")
    if (shown == null || sample.elapsedRealtimeMillis?.let { nowElapsed - it !in 0..2_000L } != false) return at(0.0, -12.0, "stale")
    if (sample.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || sample.headingAccuracyDegrees?.let { it > 35.0 } == true)
        return at(0.0, -12.0, "magnetic")
    if (conversion == null) return at(0.0, -12.0, "north")
    // -Z 是穿过屏幕朝远处的视线；物理顶部/安装船艏不能替代它。
    val forward = worldVector(shown.rotate(0.0, 0.0, -1.0), conversion.declinationDegrees)
    if (hypot(forward.x, forward.z) < .18) return at(0.0, -12.0, "vertical")
    val upAxis = when (rotation) {
        Surface.ROTATION_90 -> SpatialVector(-1.0, 0.0, 0.0)
        Surface.ROTATION_180 -> SpatialVector(0.0, -1.0, 0.0)
        Surface.ROTATION_270 -> SpatialVector(1.0, 0.0, 0.0)
        else -> SpatialVector(0.0, 1.0, 0.0)
    }
    return SpatialCamera(forward, worldVector(shown.rotate(upAxis.x, upAxis.y, upAxis.z), conversion.declinationDegrees),
        wrapBearing(Math.toDegrees(atan2(forward.x, -forward.z))))
}

/** 同一列主序PV矩阵用于GL网格、标注、边缘与点击命中。 */
internal class SpatialProjection(val width: Int, val height: Int, camera: SpatialCamera) {
    val matrix = FloatArray(16)
    init {
        val p = FloatArray(16); val v = FloatArray(16)
        Matrix.perspectiveM(p, 0, 58f, width.toFloat() / height.coerceAtLeast(1), .15f, 150f)
        Matrix.setLookAtM(v, 0, 0f, 1.6f, 0f, camera.forward.x.toFloat(), (1.6 + camera.forward.y).toFloat(), camera.forward.z.toFloat(), camera.up.x.toFloat(), camera.up.y.toFloat(), camera.up.z.toFloat())
        Matrix.multiplyMM(matrix, 0, p, 0, v, 0)
    }
    fun project(v: SpatialVector): ProjectedSpatialPoint {
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, matrix, 0, floatArrayOf(v.x.toFloat(), v.y.toFloat(), v.z.toFloat(), 1f), 0)
        val w = out[3]
        val behind = w <= .01f
        val scale = max(abs(w), .01f)
        val nx = out[0] / scale; val ny = out[1] / scale
        return ProjectedSpatialPoint((nx + 1f) * width / 2f, (1f - ny) * height / 2f, behind,
            !behind && nx in -.88f.. .88f && ny in -.78f.. .78f, nx, ny)
    }
}
internal data class ProjectedSpatialPoint(val x: Float, val y: Float, val behind: Boolean, val inside: Boolean, val nx: Float, val ny: Float)

/** 仅插值显示四元数，不刷新源时间；断流/换源/重启直接贴事实。 */
internal class DeviceViewMotion {
    var shown: SensorQuaternion? = null; private set
    private var target: SensorQuaternion? = null
    private var sample: DeviceViewOrientationSample? = null
    private var lastFrame = 0L
    var moving = false; private set
    fun update(value: DeviceViewOrientationSample) {
        if (value == sample) return
        val before = sample; sample = value; target = value.deviceToMagneticWorld
        val continuous = before?.generation == value.generation && before?.sourceName == value.sourceName &&
            before?.elapsedRealtimeMillis?.let { t -> value.elapsedRealtimeMillis?.let { it - t in 0..2_000L } } == true
        if (!continuous || shown == null || target == null) { shown = target; moving = false; lastFrame = 0L } else moving = true
    }
    fun advance(nanos: Long) {
        val a = shown ?: return; var b = target ?: return
        if (!moving) return
        val dt = if (lastFrame == 0L) 1.0 / 60.0 else ((nanos - lastFrame) / 1e9).coerceIn(0.0, .05)
        lastFrame = nanos
        var dot = a.w*b.w+a.x*b.x+a.y*b.y+a.z*b.z
        if (dot < 0.0) { b = SensorQuaternion(-b.w,-b.x,-b.y,-b.z); dot = -dot }
        if (dot > .9999999) { shown = b; moving = false; lastFrame = 0; return }
        val t = 1.0 - exp(-dt/.075)
        val angle = acos(dot.coerceIn(-1.0,1.0)); val denominator = sin(angle)
        val x = if (denominator < .0001) 1-t else sin((1-t)*angle)/denominator
        val y = if (denominator < .0001) t else sin(t*angle)/denominator
        shown = SensorQuaternion(a.w*x+b.w*y,a.x*x+b.x*y,a.y*x+b.y*y,a.z*x+b.z*y).normalized()
    }
}

/** 固定船艏与自由相机同样只在显示层缓动；同一相机输出供所有图元使用。 */
internal class SpatialCameraMotion {
    private val motion=DeviceViewMotion()
    private var previous:SpatialCamera?=null
    val moving get()=motion.moving
    fun present(camera:SpatialCamera,source:String,observed:Long,nanos:Long):SpatialCamera {
        if(camera.issue!=null){previous=null;motion.update(DeviceViewOrientationSample());return camera}
        if(previous!=camera){
            previous=camera
            val f=camera.forward
            val fl=sqrt(f.x*f.x+f.y*f.y+f.z*f.z).coerceAtLeast(.0001)
            val forward=f.scale(1/fl)
            val rawRight=SpatialVector(forward.y*camera.up.z-forward.z*camera.up.y,forward.z*camera.up.x-forward.x*camera.up.z,forward.x*camera.up.y-forward.y*camera.up.x)
            val rl=sqrt(rawRight.x*rawRight.x+rawRight.y*rawRight.y+rawRight.z*rawRight.z).coerceAtLeast(.0001)
            val r=rawRight.scale(1/rl)
            val u=SpatialVector(r.y*forward.z-r.z*forward.y,r.z*forward.x-r.x*forward.z,r.x*forward.y-r.y*forward.x)
            val q=com.yokuli.anchorwatch.location.vessel.PhoneVesselAttitudeFrame.fromMatrix(floatArrayOf(r.x.toFloat(),u.x.toFloat(),-forward.x.toFloat(),r.y.toFloat(),u.y.toFloat(),-forward.y.toFloat(),r.z.toFloat(),u.z.toFloat(),-forward.z.toFloat()))
            motion.update(DeviceViewOrientationSample(q,observed,sourceName=source,sensorAvailable=true))
        }
        motion.advance(nanos)
        val q=motion.shown?:return camera
        val f=q.rotate(0.0,0.0,-1.0)
        return camera.copy(forward=f,up=q.rotate(0.0,1.0,0.0),trueBearing=wrapBearing(Math.toDegrees(atan2(f.x,-f.z))))
    }
}

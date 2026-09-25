package com.yokuli.marine.shell.rebuild.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 中文：只属于当前姿态画面的显示旋转。原始读数、更新时间、校准、风险计算均不写回。
 * 由一个前台帧时钟推进；原生船体、方向标记与二维降级读取同一列主序矩阵。
 * 采用最短弧四元数球面插值，跨 ±180° 不绕远路；采集停止后不外推新姿态。
 */
internal class VesselAttitudeMotion {
    // 四元数顺序 w,x,y,z。数组和矩阵在此画面生命周期内复用，帧循环不分配姿态对象。
    private val shown = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
    private val target = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
    val matrix = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }
    private var previous: VesselAttitudeProjection? = null
    private var animate = false
    private var lastFrameNanos = 0L
    var isMoving = false
        private set
    var targetVersion by mutableIntStateOf(0)
        private set
    private var drawnVersion by mutableIntStateOf(0)

    /** 在 Compose 提交真实投影时设置目标；Canvas 只在 draw 阶段订阅帧版本。 */
    fun setTarget(projection: VesselAttitudeProjection, allowAnimation: Boolean) {
        if (previous == projection && animate == allowAnimation) return
        val old = previous
        previous = projection
        animate = allowAnimation
        val pose = projection.pose
        if (pose == null) {
            target[0] = 1.0; target[1] = 0.0; target[2] = 0.0; target[3] = 0.0
        } else {
            // 与 VesselAttitudePose 的 Rx(-pitch) · Rz(-heel) 一致。
            val pitch = Math.toRadians(-pose.pitchDegrees) * .5
            val heel = Math.toRadians(-pose.heelDegrees) * .5
            val cp = cos(pitch); val sp = sin(pitch); val ch = cos(heel); val sh = sin(heel)
            target[0] = cp * ch; target[1] = sp * ch; target[2] = -sp * sh; target[3] = cp * sh
        }
        val timeContinues = old?.sampleElapsedRealtime?.let { before ->
            projection.sampleElapsedRealtime?.let { it - before in 0L..2_000L }
        } == true
        val continuous = allowAnimation && old?.state == VesselAttitudeDisplayState.LIVE &&
            projection.state == VesselAttitudeDisplayState.LIVE &&
            old.continuityKey == projection.continuityKey && timeContinues
        if (!continuous) snapToTarget()
        else {
            isMoving = abs(dot()) < SETTLED_DOT
            if (!isMoving) snapToTarget()
        }
        targetVersion++
    }

    fun readDrawFrame() { drawnVersion }

    fun resetClock() { lastFrameNanos = 0L }

    /** 离场、换源、重新校准和失效边界直接呈现事实，不播放伪造的中间航行过程。 */
    fun snapToTarget() {
        target.copyInto(shown)
        isMoving = false
        lastFrameNanos = 0L
        writeMatrix()
    }

    /** 返回本帧是否改变矩阵；收敛后停止请求帧，静止读数不维持空转动画。 */
    fun advance(frameTimeNanos: Long): Boolean {
        if (!isMoving) { lastFrameNanos = 0L; return false }
        val elapsed = if (lastFrameNanos == 0L) 1.0 / 60.0
            else ((frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0).coerceAtMost(.05)
        lastFrameNanos = frameTimeNanos
        var cosine = dot()
        val sign = if (cosine < 0.0) -1.0 else 1.0
        cosine = abs(cosine).coerceIn(0.0, 1.0)
        if (cosine >= SETTLED_DOT) { snapToTarget(); return true }
        // 按真实帧间隔计算同一时间常数，在 60/90/120Hz 屏幕上有相同跟随速度。
        val amount = 1.0 - exp(-elapsed / .075)
        val a: Double
        val b: Double
        if (cosine > .9995) {
            a = 1.0 - amount; b = amount * sign
        } else {
            val angle = acos(cosine)
            val denominator = sin(angle)
            a = sin((1.0 - amount) * angle) / denominator
            b = sin(amount * angle) / denominator * sign
        }
        var length = 0.0
        for (i in 0..3) { shown[i] = shown[i] * a + target[i] * b; length += shown[i] * shown[i] }
        val scale = 1.0 / sqrt(length)
        for (i in 0..3) shown[i] *= scale
        isMoving = abs(dot()) < SETTLED_DOT
        if (!isMoving) target.copyInto(shown)
        writeMatrix()
        return true
    }

    fun transform(x: Float, y: Float, z: Float, destination: FloatArray) {
        destination[0] = matrix[0] * x + matrix[4] * y + matrix[8] * z
        destination[1] = matrix[1] * x + matrix[5] * y + matrix[9] * z
        destination[2] = matrix[2] * x + matrix[6] * y + matrix[10] * z
    }

    private fun dot() = shown[0] * target[0] + shown[1] * target[1] + shown[2] * target[2] + shown[3] * target[3]

    private fun writeMatrix() {
        val w = shown[0]; val x = shown[1]; val y = shown[2]; val z = shown[3]
        matrix[0] = (1 - 2 * (y * y + z * z)).toFloat()
        matrix[1] = (2 * (x * y + w * z)).toFloat()
        matrix[2] = (2 * (x * z - w * y)).toFloat()
        matrix[4] = (2 * (x * y - w * z)).toFloat()
        matrix[5] = (1 - 2 * (x * x + z * z)).toFloat()
        matrix[6] = (2 * (y * z + w * x)).toFloat()
        matrix[8] = (2 * (x * z + w * y)).toFloat()
        matrix[9] = (2 * (y * z - w * x)).toFloat()
        matrix[10] = (1 - 2 * (x * x + y * y)).toFloat()
        drawnVersion++
    }

    private companion object {
        // 约 0.04° 以内落到真实目标，不保留无限小尾巴或每帧分配。
        const val SETTLED_DOT = .99999994
    }
}

package com.yokuli.marine.shell.rebuild.scene

import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import com.yokuli.anchorwatch.domain.vessel.VesselDataSnapshot
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.domain.vessel.VesselProvenance
import com.yokuli.anchorwatch.domain.vessel.persistentKey
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 船体姿态的显示坐标，不拥有传感器、不修正或写回观测。
 * 领域：正横倾 = 右舷下沉；正纵倾 = 船艏抬起（PhoneVesselAttitudeFrame）。
 * 离线 GLB：X 右舷、Y 上、Z 船艏；水线中心为旋转原点。
 * 列向量先绕 Z 转 -heel，再绕固定 X 转 -pitch：Rx(-pitch) · Rz(-heel)。
 * 因此右舷点 Y = -cos(pitch) sin(heel)，船艏点 Y = sin(pitch)。
 * Heading、设备原始坐标与相机视角均不参与此变换；所有输入仍以角度为单位。
 */
internal data class VesselAttitudePose(val heelDegrees: Double, val pitchDegrees: Double) {
    fun transform(x: Float, y: Float, z: Float): FloatArray {
        val h = Math.toRadians(heelDegrees)
        val p = Math.toRadians(pitchDegrees)
        val ch = cos(h); val sh = sin(h); val cp = cos(p); val sp = sin(p)
        return floatArrayOf(
            (ch * x + sh * y).toFloat(),
            (-cp * sh * x + cp * ch * y + sp * z).toFloat(),
            (sp * sh * x - sp * ch * y + cp * z).toFloat(),
        )
    }

    /** Filament 要求列主序矩阵；同时供二维降级投影使用，避免两种画面方向相反。 */
    fun matrix(): FloatArray {
        val x = transform(1f, 0f, 0f)
        val y = transform(0f, 1f, 0f)
        val z = transform(0f, 0f, 1f)
        return floatArrayOf(x[0], x[1], x[2], 0f, y[0], y[1], y[2], 0f,
            z[0], z[1], z[2], 0f, 0f, 0f, 0f, 1f)
    }
}

internal enum class VesselAttitudeDisplayState { LIVE, LAST, REFERENCE }

/** 双轴必须来自当前实际观测。缺失不补零，不将不同时刻的旧轴拼成实时姿态。 */
internal data class VesselAttitudeProjection(
    val pose: VesselAttitudePose?,
    val state: VesselAttitudeDisplayState,
    val samplesAligned: Boolean,
    /** 中文：仅用于防止跨来源、连接代次或校准版本插值，不决定业务来源。 */
    val continuityKey: String? = null,
    val sampleElapsedRealtime: Long? = null,
) {
    companion object {
        private fun continuity(observation: VesselObservation<Double>): String {
            val source = observation.sourceIdentity
            val calibration = (observation.provenanceDetail as? VesselProvenance.PhoneSensor)?.calibrationVersion
            return "${source?.persistentKey ?: observation.source.name}:${source?.connectionGeneration}:$calibration"
        }
        fun from(data: VesselDataSnapshot): VesselAttitudeProjection {
            val heel = data.heelDegrees
            val pitch = data.pitchDegrees
            val h = heel.value?.takeIf { it.isFinite() && it in -180.0..180.0 }
            val p = pitch.value?.takeIf { it.isFinite() && it in -90.0..90.0 }
            val hAt = heel.receivedElapsedRealtime
            val pAt = pitch.receivedElapsedRealtime
            val aligned = hAt != null && pAt != null && abs(hAt - pAt) <= 2_000L
            val heelLive = heel.freshness == VesselDataFreshness.FRESH
            val pitchLive = pitch.freshness == VesselDataFreshness.FRESH
            if (h == null || p == null || !aligned ||
                heelLive != pitchLive ||
                heel.quality == VesselDataQuality.UNKNOWN || pitch.quality == VesselDataQuality.UNKNOWN) {
                return VesselAttitudeProjection(null, VesselAttitudeDisplayState.REFERENCE, aligned)
            }
            val current = heelLive && pitchLive
            return VesselAttitudeProjection(VesselAttitudePose(h, p),
                if (current) VesselAttitudeDisplayState.LIVE else VesselAttitudeDisplayState.LAST, aligned,
                continuityKey = continuity(heel) + "/" + continuity(pitch),
                sampleElapsedRealtime = maxOf(requireNotNull(hAt), requireNotNull(pAt)))
        }
    }
}

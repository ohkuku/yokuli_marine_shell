package com.yokuli.marine.shell.rebuild.scene

import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.CandidateValidity
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselPosition
import com.yokuli.anchorwatch.domain.vessel.VesselProvenance
import com.yokuli.anchorwatch.domain.vessel.VesselReference
import com.yokuli.anchorwatch.domain.vessel.VesselSourceClass
import com.yokuli.anchorwatch.domain.vessel.VesselSourceConflict
import com.yokuli.anchorwatch.domain.vessel.VesselSourceIdentity
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState

/** 场景热点属于展示导航，不控制采集、选源、告警或记录。 */
enum class VesselHotspot(val nodeName:String, val x:Float, val y:Float, val z:Float) {
    // 对应离线 GLB 的具名语义节点及根坐标，绝不依赖网格三角形编号。
    WIND("hotspot:wind",0f,3.8f,.19f),
    POSITION("hotspot:position",0f,.36f,-.1f),
    HEADING("hotspot:heading",0f,.55f,1.86f),
    DEPTH("hotspot:depth",0f,-1.2f,-.06f),
}

/** 展示状态不替代运行时 freshness；问题可以并存，由 [VesselSceneMetric.issues] 保留。 */
enum class VesselSceneStatus {
    CURRENT, LAST_READING, NEVER_RECEIVED, NO_ADOPTED_READING, PERMISSION_REQUIRED, PROVIDER_DISABLED,
    CALIBRATION_REQUIRED, SENSOR_MISSING, SOURCE_DISABLED, LOW_QUALITY, CONFLICT, INVALID,
}

/**
 * “我的船”的只读投影。距离为米、速度为节、角度为度，显示单位由全局格式器处理。
 * 只包含当前运行时提供的事实；相机、热点选择和二维/三维模式由页面保存。
 */
data class VesselSceneModel(
    val metrics: Map<VesselMetricId, VesselSceneMetric>,
    val hotspots: List<VesselSceneHotspot>,
    val mount: VesselSceneMount,
    val generatedElapsedRealtime: Long,
) {
    fun metric(id: VesselMetricId): VesselSceneMetric = metrics.getValue(id)
    fun hotspot(id: VesselHotspot): VesselSceneHotspot = hotspots.first { it.id == id }
}

/** primaryMetric 仅决定摘要，不把其他字段的缺失、时间或基准覆盖为摘要字段。 */
data class VesselSceneHotspot(
    val id: VesselHotspot,
    val primaryMetric: VesselMetricId,
    val metrics: List<VesselMetricId>,
    val status: VesselSceneStatus,
)

data class VesselSceneMount(
    val bowAxis: DeviceBowAxis,
    val mountState: PhoneVesselMountState,
    val attitudeConfirmed: Boolean,
    val headingAligned: Boolean,
)

/**
 * source 是当前所显示读数的来源，过期时仍保留；adoptedSource 才是运行时仍采用的来源。
 * requestedSourceKey/preference 是用户意图，不能根据候选状态在 UI 内偷偷改写。
 * observedAtUtcMillis 是设备给出的采样 UTC；receivedElapsedRealtime 是完整数值的接收时刻。
 * sourceHeartbeatElapsedRealtime 是同一来源的最近消息，不能冒充新的数值或新的定位。
 */
data class VesselSceneMetric(
    val id: VesselMetricId,
    val number: Double?,
    val position: VesselPosition?,
    val source: VesselSourceIdentity?,
    val adoptedSource: VesselSourceIdentity?,
    val sourceClass: VesselSourceClass,
    val preference: VesselSourcePreference,
    val requestedSourceKey: String?,
    val requestedSource: VesselSourceIdentity?,
    val selectedSourceUnavailable: Boolean,
    val freshness: VesselDataFreshness,
    val quality: VesselDataQuality,
    val status: VesselSceneStatus,
    val issues: Set<VesselSceneStatus>,
    val observedAtUtcMillis: Long?,
    val receivedElapsedRealtime: Long?,
    val sourceHeartbeatElapsedRealtime: Long?,
    val ageMillis: Long?,
    val reference: VesselReference?,
    val provenance: String?,
    val provenanceDetail: VesselProvenance?,
    val conflict: VesselSourceConflict?,
    val selectionReason: String?,
    val candidates: List<VesselSceneCandidate>,
    val connection: VesselSceneConnection?,
    /** 只指同一连接在此指标停止更新后还有新消息，绝不等价于这个指标有效。 */
    val connectionContinuesWithoutMeasurement: Boolean,
) {
    val hasValue: Boolean get() = number != null || position != null
    val isCurrent: Boolean get() = hasValue && freshness == VesselDataFreshness.FRESH &&
        status in setOf(VesselSceneStatus.CURRENT, VesselSceneStatus.LOW_QUALITY, VesselSceneStatus.CONFLICT)
}

/** 候选读数供详情解释与显式选源；永远不用于补齐当前场景缺失的值。 */
data class VesselSceneCandidate(
    val source: VesselSourceIdentity,
    val sourceClass: VesselSourceClass,
    val number: Double?,
    val position: VesselPosition?,
    val reference: VesselReference?,
    val quality: VesselDataQuality,
    val validity: CandidateValidity,
    val freshness: VesselDataFreshness,
    val observedAtUtcMillis: Long?,
    val receivedElapsedRealtime: Long,
    val sourceHeartbeatElapsedRealtime: Long,
    val ageMillis: Long?,
    val provenance: VesselProvenance?,
    val isRequested: Boolean,
    val isAdopted: Boolean,
    val connection: VesselSceneConnection?,
)

/** 连接只描述传输事实；字段新鲜度继续使用该字段自己的时间。 */
data class VesselSceneConnection(
    val id: String,
    val name: String,
    val state: NmeaConnectionState,
    val requested: Boolean,
    val generation: Long,
    val lastMessageElapsedRealtime: Long?,
    val messageAgeMillis: Long?,
    val error: String?,
)

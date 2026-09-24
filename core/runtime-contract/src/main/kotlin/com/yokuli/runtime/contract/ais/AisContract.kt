package com.yokuli.runtime.contract.ais

import kotlinx.coroutines.flow.StateFlow

/** AIS 只消费连接已接收的报文；此端口没有开启 socket 或更改全船选源的权限。 */
interface AisTrafficService {
    val snapshot: StateFlow<TrafficSnapshot>
    suspend fun command(command: AisCommand): AisCommandResult
}

data class AisPoint(val latitude: Double, val longitude: Double)
/** 来源包含连接代次及实际发送端；不同 UDP peer 的分片和报告不混合。 */
data class AisSource(val connectionId: String, val generation: Long, val peer: String?, val formatter: String, val channel: String, val tagSource: String? = null) {
    val key: String get() = "$connectionId|$generation|${peer.orEmpty()}|$formatter|$channel|${tagSource.orEmpty()}"
}
data class AisFrame(val connectionId: String, val generation: Long, val peer: String?, val sentence: String, val receivedElapsed: Long)
enum class AisEntityKind { CLASS_A, CLASS_B, LONG_RANGE, AID_TO_NAVIGATION, VIRTUAL_AID, BASE_STATION, SAR_AIRCRAFT, SART, MOB, EPIRB, UNKNOWN }
enum class AisDistressState { NONE, ACTIVE, TEST, INACTIVE, UNKNOWN }
enum class AisTargetState { WAITING_POSITION, CURRENT, AGING, LOST, CONFLICT, INVALID }
enum class AisView { RADAR, THREE_D, CHART }
enum class AisOrientation { NORTH_UP, HEADING_UP, COURSE_UP }
enum class AisInputState { DISABLED, CONNECTING, ONLINE, INTERRUPTED }
enum class AisCpaState { CALCULATED, ESTIMATED, PAST, PARALLEL, OWN_POSITION_MISSING, TARGET_POSITION_MISSING, STALE, CONFLICT, MOTION_MISSING, LOW_SPEED_COURSE, NOT_SURFACE_VESSEL, LOW_RESOLUTION }
enum class AisRiskKind { CPA, PROXIMITY, ANCHOR_PROXIMITY, DISTRESS, TARGET_LOST }
enum class AisRiskLevel { NONE, ATTENTION, WARNING, URGENT }
enum class AisApproachTrend { APPROACHING, RECEDING, STEADY, UNKNOWN }

/** 广播尺寸相对 AIS 定位天线，不把天线无条件当成船体中心。饱和值不作为实尺轮廓。 */
data class AisDimensions(val toBowMeters: Int, val toSternMeters: Int, val toPortMeters: Int, val toStarboardMeters: Int) {
    val lengthMeters: Int get() = toBowMeters + toSternMeters
    val beamMeters: Int get() = toPortMeters + toStarboardMeters
    val usable: Boolean get() = lengthMeters > 0 && beamMeters > 0 && toBowMeters < 511 && toSternMeters < 511 && toPortMeters < 63 && toStarboardMeters < 63
}
/** 每个静态字段保留自己的来源和更新时间；静态更新不会续租位置。 */
data class AisStaticValue<T>(val value: T, val source: AisSource, val receivedElapsed: Long)
data class AisStaticData(
    val name: AisStaticValue<String>? = null,
    val callSign: AisStaticValue<String>? = null,
    val shipType: AisStaticValue<Int>? = null,
    val imo: AisStaticValue<Int>? = null,
    val dimensions: AisStaticValue<AisDimensions>? = null,
    val destination: AisStaticValue<String>? = null,
    val eta: AisStaticValue<String>? = null,
    val draughtMeters: AisStaticValue<Double>? = null,
    val motherShipMmsi: AisStaticValue<Int>? = null,
    val vendor: AisStaticValue<String>? = null,
    val aidType: AisStaticValue<Int>? = null,
)
/** 同一报文的一致动态；null 是未提供/协议无效，绝不补成零。秒字段不是完整 UTC。 */
data class AisDynamicReport(
    val source: AisSource,
    val receivedElapsed: Long,
    val messageType: Int,
    val position: AisPoint?,
    val sogMetersPerSecond: Double? = null,
    val cogDegrees: Double? = null,
    val headingDegrees: Double? = null,
    val navigationStatus: Int? = null,
    val rateOfTurnDegreesPerMinute: Double? = null,
    val positionAccurate: Boolean = false,
    val raim: Boolean = false,
    val utcSecond: Int? = null,
    val sourceTimestampText: String? = null,
    val lowResolution: Boolean = false,
    val altitudeMeters: Int? = null,
    val offPosition: Boolean? = null,
    val invalidFields: Set<String> = emptySet(),
)
data class AisTrackPoint(val position: AisPoint, val receivedElapsed: Long, val segment: Long)
data class AisSafetyMessage(val text: String, val destinationMmsi: Int?, val receivedElapsed: Long, val source: AisSource, val messageType: Int)
data class AisRawMessage(val type: Int, val receivedElapsed: Long, val source: AisSource, val payload: String, val fillBits: Int)
/** 本船值由现有选源结果投影；当前有效性与各字段单调时间由原运行时负责。 */
data class AisOwnship(
    val position: AisPoint?, val positionElapsed: Long?, val positionValid: Boolean,
    val sogMetersPerSecond: Double? = null, val sogElapsed: Long? = null,
    val cogDegrees: Double? = null, val cogElapsed: Long? = null,
    val headingDegrees: Double? = null, val headingElapsed: Long? = null,
    val anchored: Boolean = false, val anchor: AisPoint? = null,
)
data class AisRelativeMetrics(
    val state: AisCpaState,
    val distanceMeters: Double? = null, val bearingDegrees: Double? = null,
    val relativeBearingDegrees: Double? = null,
    val cpaMeters: Double? = null, val tcpaSeconds: Double? = null,
    val referenceElapsed: Long? = null, val extrapolationMillis: Long = 0,
    val ownAtCpa: AisPoint? = null, val targetAtCpa: AisPoint? = null,
    val reason: String? = null, val approachTrend: AisApproachTrend = AisApproachTrend.UNKNOWN,
)
data class AisRiskEvent(
    val id: String, val mmsi: Int, val kind: AisRiskKind, val level: AisRiskLevel,
    val startedElapsed: Long, val updatedElapsed: Long,
    val active: Boolean = true, val acknowledged: Boolean = false,
    val snoozedUntilElapsed: Long = 0, val reason: String,
    val distanceMeters: Double? = null, val cpaMeters: Double? = null, val tcpaSeconds: Double? = null,
)
data class AisTarget(
    val mmsi: Int, val kind: AisEntityKind, val state: AisTargetState,
    val staticData: AisStaticData = AisStaticData(), val dynamic: AisDynamicReport? = null,
    val candidates: List<AisDynamicReport> = emptyList(),
    val lastMessageElapsed: Long, val positionAgeMillis: Long? = null,
    val positionInvalidated: Boolean = false, val conflictReason: String? = null,
    val distress: AisDistressState = AisDistressState.NONE,
    val track: List<AisTrackPoint> = emptyList(), val safetyMessages: List<AisSafetyMessage> = emptyList(),
    val recentMessages: List<AisRawMessage> = emptyList(),
    val relative: AisRelativeMetrics = AisRelativeMetrics(AisCpaState.TARGET_POSITION_MISSING),
    val riskLevel: AisRiskLevel = AisRiskLevel.NONE, val riskEventIds: List<String> = emptyList(),
    val watched: Boolean = false, val alias: String? = null, val cached: Boolean = false,
) {
    val displayName: String get() = alias?.takeIf { it.isNotBlank() } ?: staticData.name?.value?.takeIf { it.isNotBlank() } ?: mmsi.toString().padStart(9, '0')
    val position: AisPoint? get() = dynamic?.position
}
data class AisInputHealth(
    val connectionId: String, val name: String, val state: AisInputState,
    val generation: Long = 0, val lastByteElapsed: Long? = null, val lastNmeaElapsed: Long? = null,
    val lastAisElapsed: Long? = null, val legalAisMessages: Long = 0, val dynamicTargets: Int = 0,
    val rejectedMessages: Long = 0, val lastError: String? = null,
)
/** 可调整工程初值，不代表通用安全距离；三个监控开关与两个地图可见性独立。 */
data class AisPreferences(
    val chartLayer: Boolean = true, val anchorLayer: Boolean = true,
    val lastView: AisView = AisView.RADAR, val rangeNauticalMiles: Double = 2.0,
    val orientation: AisOrientation = AisOrientation.NORTH_UP,
    val ownMmsi: Int? = null, val watchedMmsis: Set<Int> = emptySet(), val aliases: Map<Int, String> = emptyMap(),
    val cpaEnabled: Boolean = false, val proximityEnabled: Boolean = false, val anchorProximityEnabled: Boolean = false,
    val cpaDistanceMeters: Double = 926.0, val cpaTimeSeconds: Double = 900.0,
    val proximityMeters: Double = 185.2, val anchorProximityMeters: Double = 185.2,
    val anchorUsesAnchorPoint: Boolean = false,
    val lostTargetAlerts: Boolean = true, val soundEnabled: Boolean = false,
) {
    val monitoringEnabled: Boolean get() = cpaEnabled || proximityEnabled || anchorProximityEnabled
}
data class TrafficSnapshot(
    val generatedElapsed: Long = 0, val targets: List<AisTarget> = emptyList(),
    val inputs: List<AisInputHealth> = emptyList(), val ownship: AisOwnship? = null,
    val preferences: AisPreferences = AisPreferences(), val events: List<AisRiskEvent> = emptyList(),
    val ownReports: List<AisOwnReport> = emptyList(), val ownIdentityConflict: Boolean = false,
    val capacityLimited: Boolean = false, val backgroundLimitations: List<String> = emptyList(),
    val runtime: AisRuntimeStatus = AisRuntimeStatus(), val notices: List<AisNotice> = emptyList(),
) { fun target(mmsi: Int): AisTarget? = targets.firstOrNull { it.mmsi == mmsi } }
data class AisOwnReport(val mmsi: Int, val source: AisSource, val receivedElapsed: Long)
/** 缓存只恢复静态和最后位置；cached=true 的历史绝不能参与实时预测。 */
data class AisCachedTarget(val mmsi: Int, val kind: AisEntityKind, val staticData: AisStaticData, val lastPosition: AisPoint?, val lastHeadingDegrees: Double? = null, val lastSource: AisSource? = null, val savedAtUtcMillis: Long = 0L)
sealed interface AisCommand {
    /** 请求与持久化结果使用同一身份；重试同一请求不会重复执行。 */
    val requestId: String
    data class UpdatePreferences(val preferences: AisPreferences, override val requestId: String = java.util.UUID.randomUUID().toString()) : AisCommand
    data class Watch(val mmsi: Int, val watched: Boolean, override val requestId: String = java.util.UUID.randomUUID().toString()) : AisCommand
    data class Alias(val mmsi: Int, val alias: String?, override val requestId: String = java.util.UUID.randomUUID().toString()) : AisCommand
    data class Acknowledge(val eventId: String, override val requestId: String = java.util.UUID.randomUUID().toString()) : AisCommand
    data class Snooze(val eventId: String, val durationMillis: Long = 300_000, override val requestId: String = java.util.UUID.randomUUID().toString()) : AisCommand
    data class RetainTarget(val mmsi: Int, val retain: Boolean, val ownerId: String = "default", override val requestId: String = java.util.UUID.randomUUID().toString()) : AisCommand
}
data class AisCommandResult(val success: Boolean, val reason: String? = null, val requestId: String = "")

/** 后台执行能力是 Android 的事实，开关保存成功不等于后台保护已经可用。 */
data class AisRuntimeStatus(
    val ready: Boolean = false, val monitoringRequested: Boolean = false,
    val foregroundActive: Boolean = false, val notificationsAllowed: Boolean = false,
    val soundAllowed: Boolean = false, val backgroundRestricted: Boolean = false,
    val inputQueueDrops: Long = 0, val persistenceError: String? = null, val foregroundError: String? = null,
)
/** 同一风险事件由运行时发出一次，Shell 和 Android 共用目标身份而不各算一次警报。 */
data class AisNotice(
    val id: String, val eventId: String, val mmsi: Int, val level: AisRiskLevel,
    val titleZh: String, val titleEn: String, val messageZh: String, val messageEn: String,
    val issuedAtUtcMillis: Long,
)

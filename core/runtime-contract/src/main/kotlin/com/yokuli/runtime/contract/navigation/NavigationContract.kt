package com.yokuli.runtime.contract.navigation

import kotlinx.coroutines.flow.StateFlow

/** 导航规范量：WGS84 经纬度、米、米/秒、真北角、UTC 毫秒。 */
data class NavigationPoint(val lat: Double, val lon: Double) {
    val valid: Boolean get() = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}
data class NavigationWaypoint(val id: String, val name: String, val point: NavigationPoint)
/** 执行时冻结几何；收藏或图集更新不能修改此副本。 */
data class NavigationRouteSnapshot(val id: String, val revision: String, val name: String, val waypoints: List<NavigationWaypoint>,
    /** 完整路径中的业务目标索引；其余为形状点。null兼容旧路线每点都是目标。 */
    val navigationTargetIndices: List<Int>? = null) {
    val targetIndices:List<Int> get()=navigationTargetIndices ?: waypoints.indices.toList()
    val geometry:List<NavigationPoint> get()=waypoints.map{it.point}
}
enum class NavigationSource { LOCAL, EXTERNAL_NMEA }
enum class NavigationPhase { ACTIVE, PAUSED, RECOVERY_REQUIRED, ARRIVED, ENDED }
enum class WaypointAdvanceMode { MANUAL, AUTOMATIC }
enum class NavigationSegmentRelation { BEFORE, ON, AFTER, DIRECT, UNKNOWN }
enum class NavigationEtaBasis { PLAN_SPEED, CURRENT_PROGRESS, NONE }
data class NavigationSettings(
    val advanceMode: WaypointAdvanceMode = WaypointAdvanceMode.MANUAL,
    val arrivalRadiusMeters: Double = 50.0,
    val maximumPositionErrorMeters: Double = 25.0,
    val plannedSpeedMetersPerSecond: Double? = null,
    val etaBasis: NavigationEtaBasis = NavigationEtaBasis.CURRENT_PROGRESS,
)
data class NavigationSession(
    val id: String,
    val revision: Long,
    val source: NavigationSource,
    val route: NavigationRouteSnapshot?,
    val targetIndex: Int,
    val phase: NavigationPhase,
    val settings: NavigationSettings = NavigationSettings(),
    val externalSourceId: String? = null,
    val analysisReference: String? = null,
    /** 开始时实际船位，用于首目标接入段；不以稍后船位悄悄重画。 */
    val approachOrigin: NavigationPoint? = null,
    /** 手动选中的接入目标；推进以后才改用上一航点为航段起点。 */
    val approachTargetIndex: Int = 0,
    val startedAtUtcMillis: Long,
    val updatedAtUtcMillis: Long,
    val lastGuidance: NavigationGuidance? = null,
    /** 当前路径引导点，不是已确认到达的业务目标；跟踪形状点不会触发ADVANCE。 */
    val geometryIndex:Int?=null,
    val approachGeometryIndex:Int?=null,
) {
    val ongoing: Boolean get() = phase !in setOf(NavigationPhase.ARRIVED, NavigationPhase.ENDED)
    val target: NavigationWaypoint? get() = route?.waypoints?.getOrNull(targetIndex)
}
/** 值和依据一并发布，UI/磁贴不各算一套目标。crossTrackMeters >0 表示规划线右侧。 */
data class NavigationGuidance(
    val sessionId: String,
    val sessionRevision: Long,
    val targetId: String?,
    val targetName: String?,
    val targetPosition: NavigationPoint?,
    val distanceMeters: Double? = null,
    val remainingMeters: Double? = null,
    val bearingTrueDegrees: Double? = null,
    val crossTrackMeters: Double? = null,
    /** 航段延长线上也可计算，仅供通过线判定；不是有效航段横偏。 */
    val lineOffsetMeters: Double? = null,
    val alongTrackMeters: Double? = null,
    val segmentLengthMeters: Double? = null,
    val segmentRelation: NavigationSegmentRelation = NavigationSegmentRelation.UNKNOWN,
    val nearTarget: Boolean = false,
    val etaTargetUtcMillis: Long? = null,
    val etaRouteUtcMillis: Long? = null,
    val etaBasis: NavigationEtaBasis = NavigationEtaBasis.NONE,
    val progressMetersPerSecond: Double? = null,
    val positionObservedUtcMillis: Long? = null,
    val positionElapsedMillis: Long? = null,
    val positionAccuracyMeters: Double? = null,
    val positionSource: String? = null,
    val live: Boolean = false,
    val issue: String? = null,
    /** 各派生读数独立保留触发它的测量时间；其他字段更新不能改写已记录历史。 */
    val bearingElapsedMillis: Long? = null,
    val distanceElapsedMillis: Long? = null,
    val crossTrackElapsedMillis: Long? = null,
    val progressElapsedMillis: Long? = null,
    /** 沿完整路径的引导与目的地信息分开，不将下一圆弧点当作用户目的地。 */
    val geometryIndex:Int?=null,
    val steeringPosition:NavigationPoint?=null,
    val steeringBearingTrueDegrees:Double?=null,
    val distanceAlongRouteToTargetMeters:Double?=null,
)
data class NavigationExternalSource(val id: String, val name: String, val current: Boolean)
data class NavigationState(
    val ready: Boolean = false,
    val session: NavigationSession? = null,
    val guidance: NavigationGuidance? = null,
    val externalSources: List<NavigationExternalSource> = emptyList(),
    val storageIssue: String? = null,
    val backgroundIssue: String? = null,
    val backgroundActive: Boolean = false,
)
enum class NavigationAction { START, SELECT_TARGET, ADVANCE, ARRIVE, PAUSE, RESUME, END, REPLAN, SELECT_EXTERNAL, SETTINGS }
data class NavigationCommand(
    val requestId: String,
    val action: NavigationAction,
    val expectedSessionId: String? = null,
    val expectedRevision: Long? = null,
    val route: NavigationRouteSnapshot? = null,
    val targetIndex: Int? = null,
    val settings: NavigationSettings? = null,
    val externalSourceId: String? = null,
    val analysisReference: String? = null,
)
enum class NavigationResult { SAVED, CONFLICT, REJECTED, FAILED }
data class NavigationReceipt(val requestId: String, val action: NavigationAction, val result: NavigationResult,
    val sessionId: String? = null, val revision: Long? = null, val reason: String? = null)
interface NavigationSessionService {
    val state: StateFlow<NavigationState>
    val receipts: StateFlow<List<NavigationReceipt>>
    /** 请求一经接受归运行时；页面离开不取消已提交命令。 */
    suspend fun execute(command: NavigationCommand): NavigationReceipt
    /** 仅当新仓储尚未迁移时接受一次旧冻结路线；恢复后不会直接自动启航。 */
    suspend fun importLegacy(route: NavigationRouteSnapshot?, targetIndex: Int = 0)
    fun retryRead()
}

/** 被全船来源仲裁采纳的观测；数值与各自时效独立，不用COG替Heading。 */
data class NavigationFixSnapshot(
    val point: NavigationPoint,
    val elapsedMillis: Long,
    val observedUtcMillis: Long?,
    val sourceId: String,
    val sourceName: String,
    val positionAccepted: Boolean,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double? = null,
    val speedElapsedMillis: Long? = null,
    val courseTrueDegrees: Double? = null,
    val courseElapsedMillis: Long? = null,
)

package com.yokuli.runtime.contract.planning

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.flow.StateFlow

/** 航线版本是几何内容的摘要；临时草稿、保存航线和导航冻结版本都必须明确传入。 */
data class PassageRoute(val id:String,val revision:String,val name:String,val points:List<ChartPoint>,val navigationTargetIndices:List<Int>?=null)
/** 米、米/秒；null 表示尚未设置，绝不当成零吃水或零净空。 */
data class PassageVessel(val draftMeters:Double?,val beamMeters:Double?,val airDraftMeters:Double?,val minimumUnderKeelMeters:Double?,val clearanceMarginMeters:Double?,val corridorHalfWidthMeters:Double?,val turnRadiusMeters:Double?,val plannedSpeedMetersPerSecond:Double?)
/** 用户明确画出的避让区，不从 AIS 短期目标或栅格颜色推断。 */
data class PassageAvoidance(val id:String,val name:String,val boundary:List<ChartPoint>)
/** 仅预留环境版本引用；当前没有潮汐、天气来源，不计算环境修正。 */
data class PassageEnvironmentReference(val provider:String,val revision:String,val validFromUtc:Long,val validUntilUtc:Long)
data class PassageRequest(val requestId:String,val route:PassageRoute,val datasetIds:List<String>,val vessel:PassageVessel,val departureUtc:Long?=null,val avoidances:List<PassageAvoidance> = emptyList(),val backgroundKey:String="offline")
enum class PassageSeverity { CONFLICT, REVIEW, INSUFFICIENT, NO_CONFLICT_FOUND }
enum class PassageIssueKind { LAND, DEPTH, OBSTACLE, CLEARANCE, RESTRICTION, TRAFFIC, COVERAGE, QUALITY, VESSEL, DATA, AVOIDANCE, GEOMETRY }
/** 每项问题可回到具体航段和证据对象；alongMeters 是整条航线的累计距离。 */
data class PassageIssue(val id:String,val severity:PassageSeverity,val kind:PassageIssueKind,val legIndex:Int,val point:ChartPoint?,val alongMeters:Double,val message:String,val featureId:String?=null,val cellId:String?=null,val evidence:DepthEvidence?=null)
/** 深度区域、独立测深点、资料空白各有不同语义，不连接成伪连续水深曲线。 */
data class PassageStripSpan(val fromMeters:Double,val toMeters:Double,val legIndex:Int,val depth:DepthEvidence?,val covered:Boolean,val featureId:String?=null)
data class PassageAnalysis(val id:String,val key:String,val request:PassageRequest,val dataRevision:Long,val datasetRevisions:Map<String,Long>,val computedAtUtc:Long,val distanceMeters:Double,val arrivalUtc:Long?,val severity:PassageSeverity,val issues:List<PassageIssue>,val strip:List<PassageStripSpan>,val rulesVersion:String="geometry-1",val complete:Boolean=true)
enum class PassageJobPhase { LOADING, ANALYZING, SEARCHING, COMPLETE, CANCELLED, FAILED, INTERRUPTED }
data class PassageJob(val requestId:String,val phase:PassageJobPhase,val progress:Float=0f,val detail:String?=null)
data class PassageCandidate(val id:String,val route:PassageRoute,val analysis:PassageAnalysis,val additionalMeters:Double,
    /** 候选完整折线中的业务目标；圆弧采样仅提供几何。 */
    val navigationTargetIndices:List<Int>?=null)
data class PassagePlan(val requestId:String,val original:PassageAnalysis,val candidates:List<PassageCandidate>,val reason:String?=null)
/** 人工核对只绑定某次分析版本与具体问题，不改变风险级别或填补未知资料。 */
data class PassageReview(val analysisKey:String,val issueId:String,val note:String,val reviewedAtUtc:Long)
/** 一个所有者保留最近结果；返回页面不重新启动、取消或重复计算已完成任务。 */
data class PassageState(val job:PassageJob?=null,val analysis:PassageAnalysis?=null,val plan:PassagePlan?=null,val avoidances:List<PassageAvoidance> = emptyList(),val storageIssue:String?=null,val ready:Boolean=false,
    /** 冻结的最近提交及种类；失败/中断后可原样重试，恢复不自动启动计算。 */
    val pendingRequest:PassageRequest?=null,val planning:Boolean=false,val detourLeg:Int?=null,
    val reviews:List<PassageReview> = emptyList())
interface RouteAnalysisService {
    val state:StateFlow<PassageState>
    fun analyze(request:PassageRequest)
    fun cancel(requestId:String)
    suspend fun saveAvoidance(value:PassageAvoidance)
    suspend fun removeAvoidance(id:String)
    /** 空内容移除；只接受当前原线或候选分析中的问题，提交成功才返回。 */
    suspend fun saveReview(analysisKey:String,issueId:String,note:String)
    fun retryRestore()
}
interface RoutePlanningService {
    val state:StateFlow<PassageState>
    /** detourLeg=null 为全线；其他值只替换该航段，所有连接仍重新检查。 */
    fun plan(request:PassageRequest,detourLeg:Int?=null)
    fun cancel(requestId:String)
}

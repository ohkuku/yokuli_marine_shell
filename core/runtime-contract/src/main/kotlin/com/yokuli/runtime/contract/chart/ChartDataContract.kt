package com.yokuli.runtime.contract.chart

import kotlinx.coroutines.flow.StateFlow

/** WGS84 位置；depthMeters 仅用于该实际测深点，不向周围插值。 */
data class ChartPoint(val latitude:Double,val longitude:Double,val depthMeters:Double?=null)
/** west > east 代表跨越日期变更线，不把它变成横跨全球的单个矩形。 */
data class ChartBounds(val west:Double,val south:Double,val east:Double,val north:Double) {
    val valid get()=listOf(west,south,east,north).all(Double::isFinite)&&west in -180.0..180.0&&east in -180.0..180.0&&south in -90.0..90.0&&north in south..90.0
    fun split():List<ChartBounds> = if(west<=east)listOf(this)else listOf(copy(east=180.0),copy(west=-180.0))
}
enum class ChartGeometryKind { POINT, MULTIPOINT, LINE, POLYGON, NONE }
/** 孔洞保留为独立环；多外环不强行合并成一个没有岛屿的面。 */
data class ChartGeometryPart(val points:List<ChartPoint>,val hole:Boolean=false)
data class ChartGeometry(val kind:ChartGeometryKind,val parts:List<ChartGeometryPart>)
enum class NauticalFeatureKind { LAND, SOUNDING, DEPTH_AREA, DEPTH_CONTOUR, DRYING_AREA, DREDGED_AREA, OBSTRUCTION, WRECK, ROCK, BEACON, LIGHT, TRAFFIC, RESTRICTED, BRIDGE, OVERHEAD, COVERAGE, QUALITY, OTHER }
enum class DepthEvidenceKind { POINT, INTERVAL, CONTOUR, UNKNOWN }
/** point 只证明该点；区间上下界可缺失，绝不用中值证明整段深度。datum 为 S-57 SDAT/VERDAT 代码。 */
data class DepthEvidence(val kind:DepthEvidenceKind,val lowerMeters:Double?=null,val upperMeters:Double?=null,val pointMeters:Double?=null,val datum:String?=null,val quality:String?=null)
enum class ChartUse { ANALYSIS_ALLOWED, REFERENCE_ONLY, UNKNOWN, EXPIRED, CANCELLED }
/** 格式可读不等于用途许可已确认。依据来自提供方/持有人明确声明，过期会退出分析。 */
data class DataEligibility(val use:ChartUse=ChartUse.UNKNOWN,val provider:String="",val licenceEvidence:String="",val validUntilUtc:Long?=null,val reason:String?=null) {
    fun allowsAnalysis(now:Long)=use==ChartUse.ANALYSIS_ALLOWED&&provider.isNotBlank()&&licenceEvidence.isNotBlank()&&(validUntilUtc==null||validUntilUtc>now)
}
data class ChartFeatureSource(val datasetId:String,val cellId:String,val edition:Int,val update:Int,val producer:Int,val compilationScale:Int?,val intendedUsage:Int,val horizontalDatum:Int?,val verticalDatum:Int?,val soundingDatum:Int?,val issueDate:String?,val sourceDate:String?=null,val sourceIndication:String?=null)
data class NauticalFeature(val id:String,val datasetId:String,val cellId:String,val objectClass:Int,val acronym:String,val kind:NauticalFeatureKind,val geometry:ChartGeometry,val attributes:Map<String,String>,val depth:DepthEvidence?,val source:ChartFeatureSource,val issues:List<String> = emptyList())
/** CATCOV=1 是有效覆盖，2 是显式无覆盖；geometry 必须保留孔洞。 */
data class CoverageEvidence(val featureId:String,val cellId:String,val geometry:ChartGeometry,val covered:Boolean,val compilationScale:Int?)
data class ChartCellRevision(val cellId:String,val edition:Int,val update:Int,val intendedUsage:Int,val compilationScale:Int?,val issueDate:String?,val cancelled:Boolean=false,val featureCount:Int=0,val bounds:List<ChartBounds> = emptyList(),val coverage:List<CoverageEvidence> = emptyList(),val quality:List<String> = emptyList(),val hasUnsupportedSemantic:Boolean=false,val issues:List<String> = emptyList())
data class ChartDataset(val id:String,val name:String,val format:String="S57",val revision:Long,val installedAtUtc:Long,val eligibility:DataEligibility,val cells:List<ChartCellRevision>,val offlineReadable:Boolean=true,val issue:String?=null)
/** 持有期间引用的是同一组不可变 SQLite 版本；更新/移除不会改变已取得的分析依据。 */
data class ChartDataSnapshot(val id:String,val revision:Long,val datasets:List<ChartDataset>,val missingDatasetIds:List<String> = emptyList()) {
    val cells get()=datasets.flatMap {it.cells}
}
data class ChartFeaturePage(val features:List<NauticalFeature>,val nextAfterId:String?,val hasMore:Boolean,val truncated:Boolean=false)
/** 图册对象筛选；空类别表示全部，text 匹配真实名称、类别、图幅与来源图层，不改变分析资格。 */
data class ChartFeatureFilter(val cellId:String?=null,val kinds:Set<NauticalFeatureKind> = emptySet(),val text:String="")
enum class ChartImportPhase { COPYING, PARSING, INDEXING, COMMITTING, COMPLETE, CANCELLED, FAILED, INTERRUPTED }
data class ChartImportJob(val requestId:String,val name:String,val phase:ChartImportPhase,val completed:Int=0,val total:Int=0,val detail:String="",val datasetId:String?=null)
data class ChartDataState(val revision:Long=0,val datasets:List<ChartDataset> = emptyList(),val activeJob:ChartImportJob?=null,val loading:Boolean=true,val error:String?=null)
data class ChartImportRequest(val requestId:String,val sourceUri:String,val name:String,val eligibility:DataEligibility,val replaceDatasetId:String?=null)
sealed interface ChartCommandResult {
    data class Accepted(val requestId:String):ChartCommandResult
    data class Saved(val datasetId:String,val revision:Long):ChartCommandResult
    data class Failed(val reason:String):ChartCommandResult
    data object Busy:ChartCommandResult
}

/** 无 Android、地图 SDK 或 DAO 的领域端口；文件引用只在平台适配层打开。 */
interface ChartDataService {
    val state:StateFlow<ChartDataState>
    suspend fun importPackage(request:ChartImportRequest):ChartCommandResult
    suspend fun retryImport(requestId:String):ChartCommandResult
    fun cancelImport(requestId:String)
    suspend fun rename(datasetId:String,name:String):ChartCommandResult
    suspend fun updateEligibility(datasetId:String,value:DataEligibility):ChartCommandResult
    suspend fun remove(datasetId:String):ChartCommandResult
    suspend fun acquireSnapshot(datasetIds:List<String>):ChartDataSnapshot
    suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int=2_000,afterId:String?=null):ChartFeaturePage
    /** 只浏览快照选定版本；按稳定对象 ID 分页，取消会释放本次读取租约，不释放调用方持有的快照。 */
    suspend fun browse(snapshotId:String,filter:ChartFeatureFilter=ChartFeatureFilter(),limit:Int=100,afterId:String?=null):ChartFeaturePage
    /** 读取同一快照中的完整对象；不存在返回 null，失效快照或损坏索引抛出错误，不伪装为空对象。 */
    suspend fun readFeature(snapshotId:String,featureId:String):NauticalFeature?
    suspend fun releaseSnapshot(snapshotId:String)
    suspend fun retryRestore()
}

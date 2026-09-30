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
/** automatic 取消手动自我认证；精度、基准、覆盖等真实分析条件仍由技术检查决定。旧状态可读取，过期及撤销仍阻止分析。 */
data class DataEligibility(val use:ChartUse=ChartUse.UNKNOWN,val provider:String="",val licenceEvidence:String="",val validUntilUtc:Long?=null,val reason:String?=null,val automatic:Boolean=false) {
    fun allowsAnalysis(now:Long)=(if(automatic)use!=ChartUse.EXPIRED&&use!=ChartUse.CANCELLED else use==ChartUse.ANALYSIS_ALLOWED&&provider.isNotBlank()&&licenceEvidence.isNotBlank())&&(validUntilUtc==null||validUntilUtc>now)
}
data class ChartFeatureSource(val datasetId:String,val cellId:String,val edition:Int,val update:Int,val producer:Int,val compilationScale:Int?,val intendedUsage:Int,val horizontalDatum:Int?,val verticalDatum:Int?,val soundingDatum:Int?,val issueDate:String?,val sourceDate:String?=null,val sourceIndication:String?=null)
data class NauticalFeature(val id:String,val datasetId:String,val cellId:String,val objectClass:Int,val acronym:String,val kind:NauticalFeatureKind,val geometry:ChartGeometry,val attributes:Map<String,String>,val depth:DepthEvidence?,val source:ChartFeatureSource,val issues:List<String> = emptyList())
/**
 * Feature-level semantic scale. A source file/cell may contain several LINZ Hydro scale bands,
 * so cell.compilationScale is not sufficient for LOD, cursor precedence, or route planning.
 * Smaller denominators are more detailed. This is source metadata only; it never upgrades
 * LINZ reference GIS into an ENC compilation scale.
 */
fun NauticalFeature.detailScaleDenominator():Int? = source.compilationScale
    ?: attributes["YOKULI_DETAIL_SCALE"]?.toIntOrNull()?.takeIf{it>0}
    ?: attributes["LINZ_LDS_LAYER"]?.let {title->
        when {
            "1:4k - 1:22k" in title->4_000
            "1:22k - 1:90k" in title->22_000
            "1:90k - 1:350k" in title->90_000
            "1:350k - 1:1,500k" in title->350_000
            "1:1.5mil and smaller" in title->1_500_000
            else->null
        }
    }

/**
 * Stable LOD tier independent of the exact denominator. Real ENCs may use 1:12k, 1:50k, etc.;
 * routing must not require equality with the LINZ band anchor values.
 */
fun detailTierForScale(scale:Int?):Int? = scale?.takeIf{it>0}?.let {
    when {
        it<=22_000->0
        it<=90_000->1
        it<=350_000->2
        it<=1_500_000->3
        else->4
    }
}
fun NauticalFeature.detailTier():Int? =
    attributes["YOKULI_DETAIL_TIER"]?.toIntOrNull()?.takeIf{it in 0..4}
        ?: detailTierForScale(detailScaleDenominator())

/** CATCOV=1 是有效覆盖，2 是显式无覆盖；geometry 必须保留孔洞。 */
data class CoverageEvidence(
    val featureId:String,
    val cellId:String,
    val geometry:ChartGeometry,
    val covered:Boolean,
    val compilationScale:Int?,
    /** Feature/source LOD tier; nullable keeps old catalogues readable. */
    val detailTier:Int?=null,
) {
    fun resolvedDetailTier():Int?=detailTier?.takeIf{it in 0..4}?:detailTierForScale(compilationScale)
}

/** priority 越小越优先；只有 priorityExplicit=true 才表示用户显式来源覆盖顺序。 */
data class ChartCellRevision(
    val cellId:String,val edition:Int,val update:Int,val intendedUsage:Int,val compilationScale:Int?,val issueDate:String?,
    val cancelled:Boolean=false,val featureCount:Int=0,val bounds:List<ChartBounds> = emptyList(),
    val coverage:List<CoverageEvidence> = emptyList(),val quality:List<String> = emptyList(),
    val hasUnsupportedSemantic:Boolean=false,val issues:List<String> = emptyList(),val referenceOnly:Boolean=false,
    val priority:Int=0,val sourceName:String?=null,val linzScaleBand:String?=null,val wholeCellIssues:List<String>?=null,
    val metadata:Map<String,String>?=null,val hasStructuredCoverage:Boolean?=null,
    val priorityExplicit:Boolean=false,
) {
    fun detailScaleDenominator():Int?=compilationScale ?: when(linzScaleBand) {
        "1:4k - 1:22k"->4_000
        "1:22k - 1:90k"->22_000
        "1:90k - 1:350k"->90_000
        "1:350k - 1:1,500k"->350_000
        "1:1.5mil and smaller"->1_500_000
        else->null
    }
    fun detailTier():Int?=detailTierForScale(detailScaleDenominator())
}
/** 一个用户文件夹是一份资料；栅格说明和矢量索引随同一不可变版本发布。旧目录没有 rasters 字段。 */
data class ChartDataset(val id:String,val name:String,val format:String="S57",val revision:Long,val installedAtUtc:Long,val eligibility:DataEligibility,val cells:List<ChartCellRevision>,val offlineReadable:Boolean=true,val issue:String?=null,val sourceUri:String?=null,val sourceIsFolder:Boolean=false,val rasters:List<RasterBathymetryGrid>?=null,val downloadBounds:ChartBounds?=null,val metadata:Map<String,String>?=null,val preparing:Boolean=false,val preparationIssue:String?=null)
/** 持有期间引用的是同一组不可变 SQLite 版本；更新/移除不会改变已取得的分析依据。 */
data class ChartDataSnapshot(val id:String,val revision:Long,val datasets:List<ChartDataset>,val missingDatasetIds:List<String> = emptyList()) {
    val cells get()=datasets.flatMap {it.cells}
}
data class ChartFeaturePage(val features:List<NauticalFeature>,val nextAfterId:String?,val hasMore:Boolean,val truncated:Boolean=false)
/** 指点命中的紧凑对象。面/线只返回属性，geometry=NONE；完整几何仍由 readFeature 读取。
 * distanceMeters 是到真实对象的距离，nearestPoint 不是该处的插值水深。 */
data class ChartPositionHit(val feature:NauticalFeature,val distanceMeters:Double,val nearestPoint:ChartPoint)
/** 单个原始网格像元；空值保留为空，不能借较低优先级资料补齐。 */
data class ChartPositionRaster(val grid:RasterBathymetryGrid,val elevationMeters:Float?)
/** Core 内持有并释放同一快照；不把全国海岸几何和覆盖面通过 IPC 发送给准星。 */
data class ChartPositionInfo(val datasetId:String,val datasetRevision:Long,val datasetName:String,val hits:List<ChartPositionHit>,val raster:ChartPositionRaster?,val incomplete:Boolean=false)
/** 图册对象筛选；空类别表示全部，text 匹配真实名称、类别、图幅与来源图层，不改变分析资格。 */
data class ChartFeatureFilter(val cellId:String?=null,val kinds:Set<NauticalFeatureKind> = emptySet(),val text:String="")
/** 显示/粗规划用的局部空间 LOD 过滤；只减少读取量，不改变原始资料或完整分析语义。 */
data class ChartSpatialFilter(
    val cellIds:Set<String> = emptySet(),
    val kinds:Set<NauticalFeatureKind> = emptySet(),
    /** Semantic LOD tiers 0..4. Empty means all tiers; unscaled features remain included. */
    val detailTiers:Set<Int> = emptySet(),
    /** Legacy exact-denominator filter kept for wire compatibility; new callers should use detailTiers. */
    val detailScales:Set<Int> = emptySet(),
)
enum class ChartImportPhase { COPYING, PARSING, INDEXING, COMMITTING, COMPLETE, CANCELLED, FAILED, INTERRUPTED }
/** completed/total 只表示当前阶段的工作量；GPKG 为当前文件全部图层的对象数，不是整包百分比。
 * fileIndex 为当前文件的 1-based 次序；0 表示尚未枚举或正在处理跨文件阶段。 */
data class ChartImportJob(val requestId:String,val name:String,val phase:ChartImportPhase,val completed:Int=0,val total:Int=0,val detail:String="",val datasetId:String?=null,val fileIndex:Int=0,val fileCount:Int=0,val fileName:String="")
enum class ChartExportPhase { PREPARING, PACKAGING, COPYING, COMPLETE, CANCELLED, FAILED, INTERRUPTED }
/** collectionId 只标识结果归属，由发起包提供；Core 不拥有图册绑定，不以同源数据猜归属。 */
data class ChartExportRequest(val requestId:String,val datasetId:String,val targetUri:String,val chart:ChartRasterization?=null,val collectionId:String?=null)
data class ChartExportJob(val requestId:String,val datasetId:String,val name:String,val phase:ChartExportPhase,val completed:Long=0,val total:Long=0,val detail:String="",val targetUri:String?=null,val chart:Boolean=false,val outputFolderUri:String?=null,val collectionId:String?=null)
/** 常驻目录是摘要：cells 保留身份、范围与优先级，不携带全国 coverage 几何；真实覆盖通过快照读取。 */
data class ChartDataState(val revision:Long=0,val datasets:List<ChartDataset> = emptyList(),val activeJob:ChartImportJob?=null,val loading:Boolean=true,val error:String?=null,val linz:LinzOnlineStatus?=null,val exportJob:ChartExportJob?=null)
data class ChartImportRequest(val requestId:String,val sourceUri:String,val name:String,val eligibility:DataEligibility=DataEligibility(automatic=true),val replaceDatasetId:String?=null,val rasterProduct:String?=null,val remoteBounds:ChartBounds?=null)
/** 来源限制和测量质量提示必须呈现为待复核；不能因此把真实缺失的语义一并忽略。 */
fun isBlockingChartIssue(code:String):Boolean = !code.startsWith("REFERENCE_ONLY_") &&
    code!="REFERENCE_COVERAGE_FROM_LINZ_DEPTH_AREAS" && code!="SURVEY_QUALITY_UNSPECIFIED"
data class ChartRasterWindow(val grid:RasterBathymetryGrid,val window:RasterBathymetryWindow)
sealed interface ChartCommandResult {
    data class Accepted(val requestId:String):ChartCommandResult
    data class Saved(val datasetId:String,val revision:Long):ChartCommandResult
    data class Failed(val reason:String):ChartCommandResult
    data object Busy:ChartCommandResult
}

/** 在线源仍落为一个原子离线数据版本；API Key 不进入状态、日志或导出文件。 */
const val LINZ_ONLINE_DATASET_ID="linz-online"
data class LinzOnlineStatus(val configured:Boolean=false,val cachedAtUtc:Long?=null,val bounds:ChartBounds?=null)

/** 无 Android、地图 SDK 或 DAO 的领域端口；文件引用只在平台适配层打开。 */
interface ChartDataService {
    val state:StateFlow<ChartDataState>
    suspend fun importPackage(request:ChartImportRequest):ChartCommandResult
    suspend fun retryImport(requestId:String):ChartCommandResult
    fun cancelImport(requestId:String)
    suspend fun rename(datasetId:String,name:String):ChartCommandResult
    /** 完整、无重复的图幅/栅格 ID 排列；修改会递增资料版本并使旧分析过期。 */
    suspend fun reorderCells(datasetId:String,cellIds:List<String>):ChartCommandResult
    suspend fun updateEligibility(datasetId:String,value:DataEligibility):ChartCommandResult
    /** 只编辑文件夹说明；不复制到单文件，不改变资料来源、精度或用途门槛。 */
    suspend fun updateMetadata(datasetId:String,metadata:Map<String,String>):ChartCommandResult
    /** 按需读取一个文件夹或文件说明，不把所有原文件 metadata 放进常驻状态；可验证页面版本。 */
    suspend fun readMetadata(datasetId:String,cellId:String?=null,revision:Long?=null):Map<String,String>
    /** 接受后由 Core 保持源版本租约；页面离开或 Binder 断开不会取消完整资料导出。 */
    suspend fun exportPackage(request:ChartExportRequest):ChartCommandResult
    fun cancelExport(requestId:String)
    suspend fun remove(datasetId:String):ChartCommandResult
    /** 单选文件夹；列表形状只为兼容既有持久化，最多含一个 ID。 */
    suspend fun acquireSnapshot(datasetIds:List<String>):ChartDataSnapshot
    /** 局部显示租约：覆盖与 query 几何在 Core 裁至 bounds 后才跨进程，原始来源、版本与孔洞不变。
     * 裁后的面边界包含窗口切边，不得把它重新解释为真实岸线；岸线用原始线对象。
     * 不用于规划或安全证据；query/rasterWindows 只能查询此窗口，readFeature 仍可读取完整原对象。
     * 与完整快照使用相同 releaseSnapshot 和客户端死亡释放，不保存第二份资料。 */
    suspend fun acquireDisplaySnapshot(datasetIds:List<String>,bounds:ChartBounds):ChartDataSnapshot = error("CHART_DISPLAY_SNAPSHOT_UNSUPPORTED")
    suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int=2_000,afterId:String?=null):ChartFeaturePage
    /** 局部空间查询可按图幅与对象类别做 LOD；默认实现保持兼容，Local 实现会在 SQLite 层提前过滤。 */
    suspend fun querySpatial(snapshotId:String,bounds:ChartBounds,filter:ChartSpatialFilter,limit:Int=2_000,afterId:String?=null):ChartFeaturePage {
        val page=query(snapshotId,bounds,limit,afterId)
        val filtered=page.features.filter {feature->
            (filter.cellIds.isEmpty()||feature.cellId in filter.cellIds)&&
                (filter.kinds.isEmpty()||feature.kind in filter.kinds)&&
                (filter.detailTiers.isEmpty()||feature.detailTier()==null||feature.detailTier() in filter.detailTiers)&&
                (filter.detailScales.isEmpty()||feature.detailScaleDenominator()==null||feature.detailScaleDenominator() in filter.detailScales)
        }
        return page.copy(features=filtered)
    }
    /** 局部只读查询，半径 2–150 米；返回资料自身证据，不参与安全分析或替代船舶传感器。 */
    suspend fun inspectPosition(datasetIds:List<String>,point:ChartPoint,radiusMeters:Double):ChartPositionInfo = error("CHART_POSITION_QUERY_UNSUPPORTED")
    /** 只浏览快照选定版本；按稳定对象 ID 分页，取消会释放本次读取租约，不释放调用方持有的快照。 */
    suspend fun browse(snapshotId:String,filter:ChartFeatureFilter=ChartFeatureFilter(),limit:Int=100,afterId:String?=null):ChartFeaturePage
    /** 读取同一快照中的完整对象；不存在返回 null，失效快照或损坏索引抛出错误，不伪装为空对象。 */
    suspend fun readFeature(snapshotId:String,featureId:String):NauticalFeature?
    /** 只读所持快照的原始像元。超过预算明确失败，不插值、不降采样、不把空值当海平面。 */
    suspend fun rasterWindows(snapshotId:String,bounds:ChartBounds,maxCells:Int=262_144):List<ChartRasterWindow>
    suspend fun releaseSnapshot(snapshotId:String)
    suspend fun retryRestore()
    /** 空字符串删除用户密钥；null 保留。只返回配置状态，不回传明文密钥。 */
    suspend fun configureLinz(apiKey:String):ChartCommandResult = ChartCommandResult.Failed("LINZ_UNSUPPORTED")
    /** 下载完整区域后原子替换 LINZ 缓存；失败保留上一完整版本。 */
    suspend fun refreshLinz(bounds:ChartBounds):ChartCommandResult = ChartCommandResult.Failed("LINZ_UNSUPPORTED")
}

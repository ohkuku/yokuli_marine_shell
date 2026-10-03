package com.yokuli.runtime.contract.chart

import kotlinx.coroutines.flow.StateFlow

/** 官方分发目录；和图册的已安装资料分离，不改变当前海图/导航数据源。 */
data class OfficialChartPackage(
    val id:String,
    val collectionId:String,
    val version:String,
    val name:String,
    val nameEn:String,
    val description:String="",
    val descriptionEn:String="",
    val country:String="",
    val countryName:String="",
    val countryNameEn:String="",
    val bytes:Long,
    val sha256:String,
    val downloadUrl:String,
    val fileName:String,
    val downloadSubdirectory:String,
    val contentTypes:List<String> = emptyList(),
    val recommended:Boolean=false,
    val status:String="published",
    val provider:String="",
    val license:String="",
    val attribution:String="",
    val sourceDate:String="",
    val createdAt:String="",
    val sourceNotice:String="",
    val sourceNoticeEn:String="",
)
data class OfficialChartRegion(val country:String,val name:String,val nameEn:String,val count:Int)
data class OfficialChartPage(val packages:List<OfficialChartPackage> = emptyList(),val total:Int=0,val hasMore:Boolean=false)
enum class ChartDownloadPhase { QUEUED, DOWNLOADING, WAITING, VERIFYING, READY, FAILED, CANCELLED, MISSING }
/** 下载任务冻结包版本与摘要；READY 表示实际文件大小和 SHA-256 都已核对。 */
data class OfficialChartDownload(
    val id:String,
    val item:OfficialChartPackage,
    val phase:ChartDownloadPhase=ChartDownloadPhase.QUEUED,
    val receivedBytes:Long=0,
    val verifiedBytes:Long=0,
    val destination:String="",
    val reason:String?=null,
    val createdAtMillis:Long=0,
    val attempt:Int=1,
    val updatedAtMillis:Long=0,
)
data class OfficialChartStoreState(
    val loading:Boolean=true,
    val refreshing:Boolean=false,
    val catalogueRevision:Long=0,
    val updatedAtMillis:Long?=null,
    val catalogueError:String?=null,
    val regions:List<OfficialChartRegion> = emptyList(),
    val downloads:List<OfficialChartDownload> = emptyList(),
    val destination:String="Documents/Yokuli OS Documents/Chart Packages",
    val storagePermissionRequired:Boolean=false,
)

/** Core 单写下载账本；Android 下载服务负责网络任务。页面退出不会取消下载。 */
interface OfficialChartStore {
    val state:StateFlow<OfficialChartStoreState>
    suspend fun refresh()
    suspend fun browse(query:String="",country:String="",offset:Int=0,limit:Int=40):OfficialChartPage
    /** requestId 重试返回同一任务；同一包摘要不重复下载。 */
    suspend fun download(packageId:String,requestId:String):String
    suspend fun cancel(downloadId:String)
    suspend fun retry(downloadId:String)
    /** 显式删除仅清理该任务拥有的下载文件，不删除已导入图册或用户其他原件。 */
    suspend fun remove(downloadId:String,deleteFile:Boolean)
    /** 导入前重新核对已完成文件可读；URI 仅属同 UID 应用，不是新的持久 SAF 授权。 */
    suspend fun readyUri(downloadId:String):String
}

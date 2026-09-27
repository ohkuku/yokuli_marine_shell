package com.yokuli.anchorwatch.api

import android.os.SystemClock
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import kotlinx.coroutines.flow.StateFlow

/** 单项读数保留内部规范单位和真实来源；显示走全局格式器，elapsed 为单调时钟毫秒。 */
data class Reading(val value: Double, val unit: String, val source: String, val elapsed: Long,
    val freshness:VesselDataFreshness=VesselDataFreshness.FRESH,
    val quality:VesselDataQuality=VesselDataQuality.GOOD,
    /** 稳定来源身份与来源显示名分开；同名设备及重连代次不连接历史曲线。 */
    val sourceKey:String=source,
    val validForMillis:Long=10_000,
    /** 历史连续段还取决于测量基准、校准和推导输入，不能仅凭同一设备连接曲线。 */
    val continuityKey:String=sourceKey,
    /** 中文：观测发生的 UTC 时间；只在采纳该观测时转换一次，刷新页面不重盖时间。 */
    val observedUtcMillis:Long?=null,
    /** 只属于历史采集进程的连续段；不替代原始来源/连续性身份，重启不接线。 */
    val historySessionKey:String?=null) {
    fun fresh(now: Long=SystemClock.elapsedRealtime()) = freshness==VesselDataFreshness.FRESH&&quality!=VesselDataQuality.UNKNOWN&&now-elapsed in 0..validForMillis
}
/** 读写故障保留旧文件；界面明确重试，不把未落盘的历史说成已保存。 */
data class HistoryStorageState(
    val loading:Boolean=false,
    val pending:Boolean=false,
    val lastSavedUtcMillis:Long?=null,
    val readIssue:String?=null,
    val writeIssue:String?=null,
)

/** Core 持有采样、时间和落盘；Shell 只缓存读取结果，不能反向续写历史。 */
data class ReadingHistorySnapshot(
    val generation:String="",
    val revision:Long=0,
    val readings:Map<String,Reading> = emptyMap(),
    val metrics:List<String> = emptyList(),
    val storage:HistoryStorageState=HistoryStorageState(),
)
data class ReadingHistorySlice(val metric:String="",val generation:String="",val revision:Long=0,val readings:List<Reading> = emptyList())
interface ReadingHistoryService {
    val state:StateFlow<ReadingHistorySnapshot>
    /** 单次最多一个指标的1800点；afterElapsed包含边界点以便同毫秒更换来源时去重。 */
    suspend fun slice(metric:String,afterElapsed:Long?=null):ReadingHistorySlice
    /** 有界批读，总计最多4000点；客户端按窗口跨度分批，不能一次跨Binder搬整份历史。 */
    suspend fun slices(afterElapsed:Map<String,Long?>):List<ReadingHistorySlice>
    fun retryStorage()
    suspend fun flush():Boolean
}

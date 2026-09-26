package com.yokuli.marine.shell.rebuild.data

import android.os.SystemClock
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.UUID
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality

/**
 * 界面使用的船位快照：point 为 WGS84，elapsed 为单调时钟毫秒，utc 为 UTC 毫秒。
 * speed 是节，course 是真北角度，accuracy 是米；速度/航向各自计时，不能借船位更新时间续命。
 * 此结构是业务引擎的读模型，不负责选源，也不启动定位或网络连接。
 */
data class Fix(val point: GeoPoint, val source: String, val elapsed: Long, val utc: Long,
    val speed: Double?=null, val course: Double?=null, val accuracy: Double?=null,
    val speedElapsed:Long=elapsed,val courseElapsed:Long=elapsed,
    val heading:Double?=null,val headingElapsed:Long?=null,
    val headingFreshness:VesselDataFreshness=VesselDataFreshness.UNAVAILABLE,
    val speedFreshness:VesselDataFreshness=VesselDataFreshness.FRESH,
    val courseFreshness:VesselDataFreshness=VesselDataFreshness.FRESH) {
    fun fresh(now: Long=SystemClock.elapsedRealtime()) = now-elapsed in 0..10000
    fun freshSpeed(now:Long=SystemClock.elapsedRealtime())=speed?.takeIf {speedFreshness==VesselDataFreshness.FRESH&&now-speedElapsed in 0..5000}
    fun freshCourse(now:Long=SystemClock.elapsedRealtime())=course?.takeIf {courseFreshness==VesselDataFreshness.FRESH&&now-courseElapsed in 0..5000}
    /** 船首向绝不使用 COG 替代，过期/空字段心跳不能继续转动船形。 */
    fun freshHeading(now:Long=SystemClock.elapsedRealtime())=heading?.takeIf {headingFreshness==VesselDataFreshness.FRESH&&headingElapsed?.let{now-it in 0..15000}==true}
}
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
/** 海图、磁贴、趋势共用的进程内快照；连接计数与读数分离，已连接不代表已有可信数据。 */
data class VesselData(
    /** 不同来源的最新船位；只有显式选中的来源才能成为当前船位。 */
    val phone: Fix?=null, val nmea: Fix?=null, val demo: Fix?=null, val readings: Map<String,Reading> = emptyMap(),
    /** 接收侧状态：gpsOn 表示手机定位被选择；connection/endpoint 是兼容旧主连接的摘要。 */
    val gpsOn: Boolean=false, val connection: String="off", val endpoint: String="",
    val received: Long=0, val rejected: Long=0, val lastRx: Long=0, val raw: List<String> = emptyList(),
    /** 本机发布服务状态；输出成功只在实际写出后计数，不能把入队当成已发送。 */
    val server: String="off", val serverPort: Int=10111, val addresses: List<String> = emptyList(),
    val clients: Int=0, val transmitted: Long=0, val sentToInput: Long=0, val upstreamPublishing:Boolean=false, val message: String?=null
) {
    fun fix(source: String) = when(source) { "nmea" -> nmea; "phone" -> phone; "demo" -> demo; else -> null }
}
/** 轻量发布订阅读模型；持久航迹归航行日志，字段采纳与来源仲裁归底层业务引擎。 */
class DataHub(context:Context?=null,scope:CoroutineScope?=null) {
    private val historySession=UUID.randomUUID().toString()
    private val cache=context?.takeIf {scope!=null}?.let {ReadingHistoryCache(it.applicationContext)}
    private val retry=Channel<CompletableDeferred<Boolean>>(Channel.UNLIMITED)
    private val storage=MutableStateFlow(HistoryStorageState(loading=cache!=null))
    val historyStorage=storage.asStateFlow()
    private val mutable=MutableStateFlow(VesselData())
    val state=mutable.asStateFlow()
    private val traces=MutableStateFlow<Map<String,List<Reading>>>(emptyMap())
    /** 最近15分钟的有限显示历史；同次设备开机可恢复磁盘缓存，进程重启明确断线。 */
    val history=traces.asStateFlow()
    init {
        if(cache!=null&&scope!=null)scope.launch(Dispatchers.IO) {
            fun restore():Boolean {
                storage.update {it.copy(loading=true)}
                return try {
                    val restored=cache.read(SystemClock.elapsedRealtime())
                    mergeRestored(restored.readings)
                    storage.update {it.copy(loading=false,readIssue=null,lastSavedUtcMillis=restored.savedAtUtc)}
                    true
                } catch(cancelled:CancellationException) {throw cancelled}
                catch(error:Exception) {storage.update {it.copy(loading=false,readIssue=error.message ?: "Could not read history")};false}
            }
            var loaded=restore()
            var saved:Map<String,List<Reading>>?=null
            while(isActive) {
                // 常规采集每30秒最多写一次；重试及明确退出可请求立即刷批并等待真实回执。
                val request=withTimeoutOrNull(30_000L) {retry.receive()}
                if(!loaded)loaded=restore()
                val snapshot=traces.value
                var persisted=loaded
                if(loaded&&snapshot!==saved) {
                    try {
                        val utc=System.currentTimeMillis()
                        cache.write(snapshot,utc)
                        saved=snapshot
                        storage.update {it.copy(writeIssue=null,lastSavedUtcMillis=utc,pending=traces.value!==snapshot)}
                    } catch(cancelled:CancellationException) {request?.cancel(cancelled);throw cancelled}
                    catch(error:Exception) {storage.update {it.copy(writeIssue=error.message ?: "Could not save history",pending=true)};persisted=false}
                }
                request?.complete(persisted)
            }
        }
    }
    fun retryHistoryStorage() {if(cache!=null)retry.trySend(CompletableDeferred())}
    /** 只刷显示历史；调用者在已停止采集后等待此回执，不能把它当成航行记录保存。 */
    suspend fun flushHistory():Boolean {
        if(cache==null)return true
        val receipt=CompletableDeferred<Boolean>()
        retry.send(receipt)
        return withTimeoutOrNull(10_000L) {receipt.await()} ?: false
    }
    @Synchronized private fun mergeRestored(restored:Map<String,List<Reading>>) {
        val now=SystemClock.elapsedRealtime()
        traces.update {current->
            (restored.keys+current.keys).take(ReadingHistoryCache.MAX_METRICS).associateWith {key->
                (restored[key].orEmpty()+current[key].orEmpty())
                    .filter {it.elapsed in (now-ReadingHistoryCache.WINDOW_MILLIS).coerceAtLeast(0)..now}
                    .associateBy {listOf(it.elapsed,it.sourceKey,it.continuityKey,it.unit,it.value)}.values
                    .sortedBy {it.elapsed}.takeLast(ReadingHistoryCache.MAX_POINTS)
            }.filterValues {it.isNotEmpty()}
        }
    }
    @Synchronized fun update(block: (VesselData)->VesselData) {
        val before=mutable.value
        val snapshot=block(before)
        mutable.value=snapshot
        // 网络计数与服务状态不会重新采样仪表历史；调用者可在后台独立投影两者。
        if(before.readings==snapshot.readings)return
        val now=SystemClock.elapsedRealtime()
        val historyBefore=traces.value
        traces.update { previous ->
            // 未变更的序列保持原引用；连接计数/别的传感器更新不再复制每条最多 1800 点的曲线。
            var changed:MutableMap<String,List<Reading>>?=null
            (previous.keys+snapshot.readings.keys).take(ReadingHistoryCache.MAX_METRICS).forEach { key ->
                val original=previous[key].orEmpty()
                var values=if(original.firstOrNull()?.let {now-it.elapsed>15*60_000}==true)
                    original.dropWhile {now-it.elapsed>15*60_000} else original
                snapshot.readings[key]?.takeIf {it.fresh(now)&&it.value.isFinite()}?.let {value->
                    val last=values.lastOrNull()
                    // 保存实际样本，每500ms最多一份，不平均/改写旧点；50Hz姿态不会挤掉15分钟历史。
                    val boundary=last!=null&&(last.sourceKey!=value.sourceKey||last.continuityKey!=value.continuityKey||last.historySessionKey!=historySession)
                    if(last==null||value.elapsed-last.elapsed>=500L||
                        (boundary&&value.elapsed>=last.elapsed&&!(value.elapsed==last.elapsed&&last.sourceKey==value.sourceKey&&last.continuityKey==value.continuityKey)))
                        values=(if(values.size>=1800)values.takeLast(1799)else values)+value.copy(
                            historySessionKey=historySession,
                            observedUtcMillis=value.observedUtcMillis ?: (System.currentTimeMillis()-(now-value.elapsed)))
                }
                if(values!==original) {
                    val result=changed ?: previous.toMutableMap().also {changed=it}
                    if(values.isEmpty())result.remove(key)else result[key]=values
                }
            }
            changed ?: previous
        }
        if(cache!=null&&traces.value!==historyBefore)storage.update {it.copy(pending=true)}
    }
    fun resetNmea() = update { it.copy(nmea=null,readings=emptyMap()) }
}

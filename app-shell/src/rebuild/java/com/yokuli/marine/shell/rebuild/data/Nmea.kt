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
typealias Reading = com.yokuli.anchorwatch.api.Reading
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
/** Shell仅缓存Core投影，不采样、不写历史文件；IPC断线保留历史并显示错误。 */
class DataHub(context:Context?=null,scope:CoroutineScope?=null) {
    private val mutable=MutableStateFlow(VesselData())
    val state=mutable.asStateFlow()
    private val traces=MutableStateFlow<Map<String,List<Reading>>>(emptyMap())
    val history=traces.asStateFlow()
    private val storage=MutableStateFlow(HistoryStorageState(loading=true))
    val historyStorage=storage.asStateFlow()
    private var service:com.yokuli.anchorwatch.api.ReadingHistoryService?=null
    private var subscription:Job?=null
    private var latestSubscription:Job?=null
    fun bind(endpoint:com.yokuli.anchorwatch.api.ReadingHistoryService,scope:CoroutineScope) {
        if(service===endpoint&&subscription?.isActive==true)return
        subscription?.cancel();latestSubscription?.cancel();service=endpoint
        // 当前数值不等待历史查询；历史每5秒合并一个批次，UI隐藏也不承担采样责任。
        latestSubscription=scope.launch {
            endpoint.state.collect {snapshot->
                update {it.copy(readings=snapshot.readings)}
                storage.value=snapshot.storage
            }
        }
        subscription=scope.launch(Dispatchers.Default){
            var generation=""
            var revision=-1L
            endpoint.state.collect {snapshot->
                if(snapshot.generation.isBlank())return@collect
                val changedGeneration=generation!=snapshot.generation
                if(snapshot.revision==revision&&!changedGeneration)return@collect
                val now=SystemClock.elapsedRealtime()
                val previous=traces.value
                val updated=previous.mapValues {(_,list)->list.filter{now-it.elapsed in 0..15*60_000L}}.toMutableMap()
                try {
                    val requests=linkedMapOf<String,Long?>()
                    (snapshot.metrics+snapshot.readings.keys+previous.keys).distinct().take(64).forEach {metric->
                        val existing=if(changedGeneration)emptyList()else updated[metric].orEmpty()
                        if(!changedGeneration&&existing.isNotEmpty()&&existing.last().elapsed==snapshot.readings[metric]?.elapsed)return@forEach
                        requests[metric]=existing.lastOrNull()?.elapsed
                    }
                    val batches=mutableListOf<Map<String,Long?>>()
                    var batch=linkedMapOf<String,Long?>();var budget=0
                    requests.forEach{(metric,after)->
                        // 源头最多每500ms一条。冷启动每批两字段，增量可把所有变化字段合成一批。
                        val cost=if(after==null)1800 else ((now-after).coerceAtLeast(0)/500L+3).coerceAtMost(1800).toInt()
                        if(budget+cost>3600&&batch.isNotEmpty()){batches+=batch;batch=linkedMapOf();budget=0}
                        batch[metric]=after;budget+=cost
                    }
                    if(batch.isNotEmpty())batches+=batch
                    for(request in batches)for(slice in endpoint.slices(request)){
                        check(slice.generation==snapshot.generation){"CORE_HISTORY_GENERATION_CHANGED"}
                        val existing=if(changedGeneration)emptyList()else updated[slice.metric].orEmpty()
                        updated[slice.metric]=(existing+slice.readings).associateBy {listOf(it.elapsed,it.sourceKey,it.continuityKey,it.unit,it.value,it.historySessionKey)}
                            .values.sortedBy{it.elapsed}.takeLast(1800)
                    }
                    traces.value=updated.filterValues{it.isNotEmpty()}
                    generation=snapshot.generation;revision=snapshot.revision
                }catch(cancelled:CancellationException){throw cancelled}
                catch(error:Exception){storage.update {it.copy(readIssue=error.message?:"CORE_HISTORY_UNAVAILABLE")}}
                delay(5_000L)
            }
        }
    }
    fun retryHistoryStorage(){service?.retryStorage()}
    suspend fun flushHistory():Boolean=service?.flush()?:false
    @Synchronized fun update(block:(VesselData)->VesselData){mutable.value=block(mutable.value)}
    fun resetNmea()=update{it.copy(nmea=null,readings=emptyMap())}
}

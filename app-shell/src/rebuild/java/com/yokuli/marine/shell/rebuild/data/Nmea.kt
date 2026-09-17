package com.yokuli.marine.shell.rebuild.data

import android.os.SystemClock
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 界面使用的船位快照：point 为 WGS84，elapsed 为单调时钟毫秒，utc 为 UTC 毫秒。
 * speed 是节，course 是真北角度，accuracy 是米；速度/航向各自计时，不能借船位更新时间续命。
 * 此结构是业务引擎的读模型，不负责选源，也不启动定位或网络连接。
 */
data class Fix(val point: GeoPoint, val source: String, val elapsed: Long, val utc: Long,
    val speed: Double?=null, val course: Double?=null, val accuracy: Double?=null,
    val speedElapsed:Long=elapsed,val courseElapsed:Long=elapsed) {
    fun fresh(now: Long=SystemClock.elapsedRealtime()) = now-elapsed in 0..10000
    fun freshSpeed(now:Long=SystemClock.elapsedRealtime())=speed?.takeIf {now-speedElapsed in 0..10000}
    fun freshCourse(now:Long=SystemClock.elapsedRealtime())=course?.takeIf {now-courseElapsed in 0..10000}
}
/** 单项读数保留内部规范单位和真实来源；显示走全局格式器，elapsed 为单调时钟毫秒。 */
data class Reading(val value: Double, val unit: String, val source: String, val elapsed: Long) {
    fun fresh(now: Long=SystemClock.elapsedRealtime()) = now-elapsed in 0..10000
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
class DataHub {
    private val mutable=MutableStateFlow(VesselData())
    val state=mutable.asStateFlow()
    private val traces=MutableStateFlow<Map<String,List<Reading>>>(emptyMap())
    /** 最近 15 分钟的有限显示历史；重启后不伪造连续数据，持久历史由记录器承担。 */
    val history=traces.asStateFlow()
    fun update(block: (VesselData)->VesselData) {
        mutable.update(block)
        val snapshot=mutable.value
        val now=SystemClock.elapsedRealtime()
        traces.update { previous ->
            buildMap {
                (previous.keys+snapshot.readings.keys).forEach { key ->
                    var values=previous[key].orEmpty().dropWhile { now-it.elapsed>15*60_000 }
                    snapshot.readings[key]?.takeIf { it.fresh(now) && it.value.isFinite() }?.let { value ->
                        if(values.lastOrNull()?.elapsed?.let { it<value.elapsed }!=false)
                            values=(values+value).takeLast(1800)
                    }
                    if(values.isNotEmpty()) put(key,values)
                }
            }
        }
    }
    fun resetNmea() = update { it.copy(nmea=null,readings=emptyMap()) }
}

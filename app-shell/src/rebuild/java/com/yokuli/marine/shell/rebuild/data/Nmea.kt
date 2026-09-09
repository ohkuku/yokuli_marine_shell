package com.yokuli.marine.shell.rebuild.data

import android.os.SystemClock
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Fix(val point: GeoPoint, val source: String, val elapsed: Long, val utc: Long,
    val speed: Double?=null, val course: Double?=null, val accuracy: Double?=null,
    val speedElapsed:Long=elapsed,val courseElapsed:Long=elapsed) {
    fun fresh(now: Long=SystemClock.elapsedRealtime()) = now-elapsed in 0..10000
    fun freshSpeed(now:Long=SystemClock.elapsedRealtime())=speed?.takeIf {now-speedElapsed in 0..10000}
    fun freshCourse(now:Long=SystemClock.elapsedRealtime())=course?.takeIf {now-courseElapsed in 0..10000}
}
data class Reading(val value: Double, val unit: String, val source: String, val elapsed: Long) {
    fun fresh(now: Long=SystemClock.elapsedRealtime()) = now-elapsed in 0..10000
}
data class VesselData(
    val phone: Fix?=null, val nmea: Fix?=null, val demo: Fix?=null, val readings: Map<String,Reading> = emptyMap(),
    val gpsOn: Boolean=false, val connection: String="off", val endpoint: String="",
    val received: Long=0, val rejected: Long=0, val lastRx: Long=0, val raw: List<String> = emptyList(),
    val server: String="off", val serverPort: Int=10111, val addresses: List<String> = emptyList(),
    val clients: Int=0, val transmitted: Long=0, val sentToInput: Long=0, val upstreamPublishing:Boolean=false, val message: String?=null
) {
    fun fix(source: String) = when(source) { "nmea" -> nmea; "phone" -> phone; "demo" -> demo; else -> null }
}
class DataHub {
    private val mutable=MutableStateFlow(VesselData())
    val state=mutable.asStateFlow()
    private val traces=MutableStateFlow<Map<String,List<Reading>>>(emptyMap())
    /** Display history for this app process; durable voyage history belongs to the recorder. */
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

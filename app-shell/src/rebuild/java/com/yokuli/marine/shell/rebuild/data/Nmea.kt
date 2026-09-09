package com.yokuli.marine.shell.rebuild.data

import android.os.SystemClock
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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
    val phone: Fix?=null, val nmea: Fix?=null, val readings: Map<String,Reading> = emptyMap(),
    val gpsOn: Boolean=false, val connection: String="off", val endpoint: String="",
    val received: Long=0, val rejected: Long=0, val lastRx: Long=0, val raw: List<String> = emptyList(),
    val server: String="off", val serverPort: Int=10111, val addresses: List<String> = emptyList(),
    val clients: Int=0, val transmitted: Long=0, val sentToInput: Long=0, val upstreamPublishing:Boolean=false, val message: String?=null
) {
    fun fix(source: String) = if(source=="nmea") nmea else phone
}
class DataHub {
    private val mutable=MutableStateFlow(VesselData())
    val state=mutable.asStateFlow()
    fun update(block: (VesselData)->VesselData) = mutable.update(block)
    fun resetNmea() = update { it.copy(nmea=null,readings=emptyMap()) }
}

/** Strict framing/checksum, explicit invalidation, and field-specific clocks from the old app's approach. */
object Nmea {
    private fun utc(clock:String?,date:String?):Long?=runCatching {
        require(clock!=null && clock.length>=6 && date!=null && date.length==6)
        val year=date.substring(4,6).toInt().let {if(it<80) 2000+it else 1900+it}
        val seconds=clock.substring(4).toDouble();val whole=seconds.toInt()
        val time=LocalTime.of(clock.substring(0,2).toInt(),clock.substring(2,4).toInt(),whole,((seconds-whole)*1e9).toInt().coerceIn(0,999999999))
        LocalDate.of(year,date.substring(2,4).toInt(),date.substring(0,2).toInt()).atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
    fun checksum(body: String): String {
        var value=0; body.forEach { value=value xor it.code }
        return "$"+body+"*"+String.format(Locale.US,"%02X",value)
    }
    fun valid(line: String): Boolean {
        if(line.length !in 7..1024 || !line.startsWith('$')) return false
        val star=line.indexOf('*')
        if(star!=line.length-3) return false
        val expected=line.substring(star+1).toIntOrNull(16) ?: return false
        var actual=0; for(i in 1 until star) actual=actual xor line[i].code
        return expected==actual && line.substring(1,star).all { it.code in 32..126 }
    }
    class Framer {
        private val buffer=StringBuilder()
        fun feed(bytes: ByteArray, count: Int): List<String> {
            val lines=mutableListOf<String>()
            for(i in 0 until count) {
                val c=bytes[i].toInt().toChar()
                if(c=='$') buffer.clear()
                if(c=='\n' || c=='\r') {
                    if(buffer.isNotEmpty()) lines.add(buffer.toString())
                    buffer.clear()
                } else if(buffer.isNotEmpty() || c=='$') {
                    if(buffer.length<1024) buffer.append(c) else buffer.clear()
                }
            }
            return lines
        }
    }
    fun accept(line: String, source: String, hub: DataHub) {
        val now=SystemClock.elapsedRealtime()
        if(!valid(line)) { hub.update { it.copy(rejected=it.rejected+1,raw=(it.raw+"× $line").takeLast(80)) }; return }
        val f=line.substring(1).substringBefore('*').split(','); val type=f[0].takeLast(3)
        fun number(i: Int): Double? = f.getOrNull(i)?.toDoubleOrNull()?.takeIf { it.isFinite() }
        fun angle(i: Int) = number(i)?.takeIf { it in 0.0..360.0 }?.rem(360)
        fun speed(i: Int) = number(i)?.takeIf { it in 0.0..300.0 }
        fun coord(i: Int,h: Int,latitude: Boolean): Double? {
            val v=number(i)?.takeIf { it>=0 } ?: return null
            val hemi=f.getOrNull(h) ?: return null
            if(hemi !in if(latitude) listOf("N","S") else listOf("E","W")) return null
            val degrees=(v/100).toInt(); val minutes=v-degrees*100
            if(minutes !in 0.0..<60.0) return null
            val out=degrees+minutes/60
            if(out > if(latitude) 90 else 180) return null
            return out*if(hemi=="S" || hemi=="W") -1 else 1
        }
        fun point(a: Int,b: Int,c: Int,d: Int): GeoPoint? {
            val lat=coord(a,b,true) ?: return null; val lon=coord(c,d,false) ?: return null
            return GeoPoint(lat,lon)
        }
        hub.update { old ->
            var fix=old.nmea; val values=old.readings.toMutableMap()
            fun put(key: String, value: Double?, unit: String) { value?.let { values[key]=Reading(it,unit,source,now) } }
            when(type) {
                "RMC" -> when(f.getOrNull(2)) {
                    "V" -> { fix=null; values.remove("sog"); values.remove("cog") }
                    "A" -> if(f.getOrNull(12)!="N") {
                        val sog=speed(7); val cog=angle(8)
                        point(3,4,5,6)?.let { fix=Fix(it,source,now,utc(f.getOrNull(1),f.getOrNull(9)) ?: System.currentTimeMillis(),sog,cog) }
                        put("sog",sog,"kn"); put("cog",cog,"°T")
                    } else { fix=null; values.remove("sog"); values.remove("cog") }
                }
                "GGA" -> {
                    val quality=f.getOrNull(6)?.toIntOrNull()
                    if(quality==0) fix=null
                    else if(quality!=null && quality>0) point(2,3,4,5)?.let { p ->
                        fix=Fix(p,source,now,System.currentTimeMillis(),
                            values["sog"]?.takeIf { it.fresh(now) }?.value,values["cog"]?.takeIf { it.fresh(now) }?.value,
                            speedElapsed=values["sog"]?.elapsed ?: now,courseElapsed=values["cog"]?.elapsed ?: now)
                    }
                    put("satellites",number(7),""); put("hdop",number(8),"")
                }
                "GLL" -> when(f.getOrNull(6)) { "V" -> fix=null; "A" -> point(1,2,3,4)?.let { fix=Fix(it,source,now,System.currentTimeMillis()) } }
                "VTG" -> if(f.getOrNull(9)!="N") { put("sog",speed(5),"kn"); put("cog",angle(1),"°T") }
                "HDT" -> put("heading",angle(1),"°T")
                "HDM" -> put("magnetic",angle(1),"°M")
                "HDG" -> {
                    val mag=angle(1); put("magnetic",mag,"°M")
                    val variation=number(4)?.times(if(f.getOrNull(5)=="W") -1 else 1)
                    if(mag!=null && variation!=null) put("heading",(mag+variation+360)%360,"°T")
                }
                "DPT" -> { put("depth",number(1)?.takeIf { it>=0 },"m"); put("depthOffset",number(2),"m") }
                "DBT" -> put("depth",number(3)?.takeIf { it>=0 },"m")
                "VHW" -> { put("bsp",speed(5),"kn"); put("heading",angle(1),"°T") }
                "MWV" -> {
                    val prefix=if(f.getOrNull(2)=="R") "aw" else "tw"
                    if(f.getOrNull(5)=="V") { values.remove(prefix+"s"); values.remove(prefix+"a") }
                    else if(f.getOrNull(5)=="A" && f.getOrNull(2) in listOf("R","T")) {
                        val knots=number(3)?.times(when(f.getOrNull(4)) { "N"->1.0; "M"->1.943844; "K"->0.539957; else->Double.NaN })?.takeIf { it.isFinite() && it>=0 }
                        put(prefix+"s",knots,"kn"); put(prefix+"a",angle(1),"°")
                    }
                }
                "MWD" -> { put("twd",angle(1),"°T"); put("tws",speed(5),"kn") }
                "MTW" -> put("water",number(1),"°C")
            }
            old.copy(nmea=fix,readings=values,received=old.received+1,lastRx=now,connection="live",raw=(old.raw+line).takeLast(80))
        }
    }
    fun output(data: VesselData, source: String): List<String> {
        val result=mutableListOf<String>(); val now=SystemClock.elapsedRealtime()
        val fix=data.fix(source)?.takeIf { it.fresh(now) }
        fun n(v: Double?)=v?.let { String.format(Locale.US,"%.2f",it) } ?: ""
        if(fix!=null) {
            fun coord(v: Double,width: Int): String = String.format(Locale.US,"%0${width}d%07.4f",abs(v).toInt(),(abs(v)%1)*60)
            val time=Instant.ofEpochMilli(fix.utc).atZone(ZoneOffset.UTC)
            val body="GPRMC,${time.format(DateTimeFormatter.ofPattern("HHmmss.SS"))},A,${coord(fix.point.lat,2)},${if(fix.point.lat>=0) "N" else "S"},${coord(fix.point.lon,3)},${if(fix.point.lon>=0) "E" else "W"},${n(fix.freshSpeed(now))},${n(fix.freshCourse(now))},${time.format(DateTimeFormatter.ofPattern("ddMMyy"))},,,A"
            result+=checksum(body)
        }
        fun value(key: String)=data.readings[key]?.takeIf { it.fresh(now) }?.value
        value("depth")?.let { result+=checksum("SDDBT,,f,${n(it)},M,,F") }
        value("heading")?.let { result+=checksum("HEHDT,${n(it)},T") }
        for((prefix,reference) in listOf("aw" to "R","tw" to "T")) {
            val angle=value(prefix+"a"); val speed=value(prefix+"s")
            if(angle!=null && speed!=null) result+=checksum("WIMWV,${n(angle)},$reference,${n(speed)},N,A")
        }
        return result
    }
}

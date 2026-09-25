package com.yokuli.anchorwatch.runtime.trip

import android.content.Context
import android.util.AtomicFile
import com.yokuli.anchorwatch.data.database.TripEventEntity
import com.yokuli.anchorwatch.domain.vessel.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 一次点击的写入回执。SAVED 只在事件事务提交后出现，失败重试沿用原请求和采样。 */
enum class TripMomentStatus { SAVING, SAVED, UPDATING, FAILED, REJECTED, NOT_RECOVERABLE }
data class TripMomentReceipt(
    val requestId: String,
    val event: TripEventEntity? = null,
    val status: TripMomentStatus = TripMomentStatus.SAVING,
    val reason: String? = null,
)

/** 用户内容与观测依据同属原事件；补笔记不改 timestamp、坐标或 data。 */
data class TripMomentContent(val requestId: String, val name: String, val note: String, val kind: String, val paused: Boolean) {
    companion object {
        fun from(event: TripEventEntity): TripMomentContent? = runCatching {
            if (event.type != "USER_MOMENT") return null
            val json = JSONObject(event.detailJson)
            TripMomentContent(json.getString("requestId"), json.optString("name"), json.optString("note"), json.optString("kind", "GENERAL"), json.optBoolean("paused"))
        }.getOrNull()
    }
}

/** 待提交内容的持久日志；只保存这次点击的数据，不另建采集器或航行会话。 */
@Singleton class TripMomentJournal @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.filesDir, "trip-moment-captures")
    private fun file(id: String): AtomicFile {
        require(runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false))
        return AtomicFile(File(directory, "$id.json"))
    }
    fun save(id: String, event: TripEventEntity) {
        check(directory.isDirectory || directory.mkdirs()) { "MOMENT_STORAGE_UNAVAILABLE" }
        val data = JSONObject().put("eventId",event.id).put("tripId", event.tripId).put("timestamp", event.timestamp)
            .put("latitude", event.latitude ?: JSONObject.NULL).put("longitude", event.longitude ?: JSONObject.NULL)
            .put("detail", JSONObject(event.detailJson)).toString().toByteArray(Charsets.UTF_8)
        val target = file(id)
        val stream = target.startWrite()
        try {
            stream.write(data)
            // finishWrite 本身会吞下部分同步/重命名错误；捕获回执必须建立在真实落盘之上。
            stream.fd.sync()
            target.finishWrite(stream)
            check(target.openRead().use {it.readBytes().contentEquals(data)}) {"MOMENT_COMMIT_MISMATCH"}
        }
        catch (error: Throwable) { target.failWrite(stream); throw error }
    }
    fun read(id: String): TripEventEntity? {
        val target = file(id)
        if (!target.baseFile.exists()&&!File(target.baseFile.path+".bak").exists()) return null
        val value = target.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        return TripEventEntity(id=value.optLong("eventId",0),tripId=value.getLong("tripId"), timestamp=value.getLong("timestamp"), type="USER_MOMENT", severity="INFO",
            latitude=if(value.isNull("latitude"))null else value.getDouble("latitude"), longitude=if(value.isNull("longitude"))null else value.getDouble("longitude"), detailJson=value.getJSONObject("detail").toString())
    }
    fun pendingIds():List<String> = directory.listFiles().orEmpty().filter {it.name.endsWith(".json")||it.name.endsWith(".json.bak")}.sortedBy {it.lastModified()}.mapNotNull {
        it.name.removeSuffix(".bak").removeSuffix(".json").takeIf {id->runCatching {java.util.UUID.fromString(id).toString()==id}.getOrDefault(false)}
    }.distinct()
    fun remove(id: String) = file(id).delete()
}

/** 在运行时入口立即取一次不可变快照；没有新鲜、合格位置时，事件位置保持 null。 */
internal fun captureTripMoment(id: String, sessionId: Long, name: String, paused: Boolean, snapshot: VesselDataSnapshot): TripEventEntity {
    val timestamp = System.currentTimeMillis()
    val observation = snapshot.position
    val position = observation.value?.takeIf { observation.freshness == VesselDataFreshness.FRESH &&
        observation.quality == VesselDataQuality.GOOD && it.latitude.isFinite() && it.longitude.isFinite() &&
        it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    fun evidence(value: VesselObservation<*>, number: Double? = null) = JSONObject()
        .put("value", number?.takeIf { it.isFinite() } ?: JSONObject.NULL)
        .put("sourceId", value.sourceIdentity?.id ?: value.source.name)
        .put("observedAt", value.observedAtUtcMillis ?: JSONObject.NULL)
        .put("receivedElapsed", value.receivedElapsedRealtime ?: JSONObject.NULL)
        .put("freshness", value.freshness.name).put("quality", value.quality.name)
        .put("reference", value.reference?.toString() ?: JSONObject.NULL)
    fun numeric(value: VesselObservation<Double>) = evidence(value, value.value?.takeIf { value.freshness in setOf(VesselDataFreshness.FRESH, VesselDataFreshness.HELD) })
    val data = JSONObject().put("position", evidence(observation))
        .put("snapshotElapsed", snapshot.generatedElapsedRealtime)
        .put("sogKnots", numeric(snapshot.sogKnots)).put("cogTrueDegrees", numeric(snapshot.cogTrueDegrees))
        .put("headingTrueDegrees", numeric(snapshot.headingTrueDegrees)).put("depthMeters", numeric(snapshot.depthMeters))
        .put("speedThroughWaterKnots", numeric(snapshot.speedThroughWaterKnots))
        .put("apparentWindSpeedKnots", numeric(snapshot.apparentWind.speedKnots)).put("apparentWindAngleDegrees", numeric(snapshot.apparentWind.angleDegrees))
        .put("windSpeedKnots", numeric(snapshot.trueWind.speedKnots)).put("windAngleDegrees", numeric(snapshot.trueWind.angleDegrees))
        .put("pressureHpa", numeric(snapshot.pressureHpa)).put("heelDegrees", numeric(snapshot.heelDegrees)).put("pitchDegrees", numeric(snapshot.pitchDegrees))
    val detail = JSONObject().put("requestId", id).put("name", name.trim().take(100).ifBlank { "Moment" })
        .put("note", "").put("kind", "GENERAL").put("paused", paused).put("data", data)
    return TripEventEntity(tripId=sessionId, timestamp=timestamp, type="USER_MOMENT", severity="INFO", latitude=position?.latitude, longitude=position?.longitude, detailJson=detail.toString())
}

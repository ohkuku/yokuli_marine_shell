package com.yokuli.marine.shell.rebuild.extensions

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSnapshot
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselSourceCandidate
import com.yokuli.anchorwatch.domain.vessel.VesselSourceClass
import com.yokuli.anchorwatch.domain.vessel.VesselSourceType
import com.yokuli.anchorwatch.domain.vessel.persistentKey
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.anchorwatch.location.GpsSourceSafety
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.ui.derivedSourceMetrics
import com.yokuli.marine.shell.rebuild.ui.sourceObservation
import com.yokuli.runtime.contract.VoyageAction
import com.yokuli.runtime.contract.VoyageCommandReceipt
import com.yokuli.runtime.contract.VoyageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * SDK2 的系统操作网关；只投递给已有 Core 所有者，不建立来源、连接、分享或航行的平行状态。
 * 会话/权限/前台/就绪检查由 ExtensionMarineBridge 统一完成。此处仍验证每个业务参数。
 * appId 来自已经安装的清单，不能由扩展请求参数覆盖；命令 ID 据此隔离到每个应用。
 */
class ExtensionSystemServices(private val os: OsStore, private val appId: String, private val authorize: () -> Unit) {
    private val system get() = os.marine?.system ?: fail("CORE_UNAVAILABLE", "Marine Core is not connected")
    private val services get() = system.services

    suspend fun request(method: String, params: JSONObject): JSONObject = withContext(Dispatchers.Main.immediate) {
        when (method) {
            "sources.snapshot" -> sourcesSnapshot()
            "sources.select" -> selectSource(params)
            "nmea.setConnectionEnabled" -> setConnectionEnabled(params)
            "sharing.snapshot" -> sharingSnapshot()
            "sharing.setEnabled" -> setSharingEnabled(params)
            "voyage.snapshot" -> voyageSnapshot()
            "voyage.command" -> voyageCommand(params)
            "voyage.receipt" -> voyageReceipt(params)
            else -> fail("METHOD_NOT_FOUND", "Unsupported system service method")
        }
    }

    private fun sourcesSnapshot(): JSONObject {
        val state = services.state.value
        val now = SystemClock.elapsedRealtime()
        val snapshotCurrent = snapshotCurrent(now)
        val connections = services.network.connections.value
        val phone = services.sources.phoneLocationStatus.value
        val locked = state.active?.paused == false
        val options = JSONArray().put(JSONObject().put("sourceId", JSONObject.NULL)
            .put("name", os.t("关闭船位", "Position off")).put("available", !locked))
            .put(JSONObject().put("sourceId", "phone").put("name", os.t("手机 GPS", "Phone GPS"))
                .put("available", !locked && hasLocationPermission() && locationEnabled() && !phone.selectionPending &&
                    !GpsSourceSafety.blocksSystemGps(state.settings.mockEnabled, state.mockGps.state)))
        connections.filter { it.spec.receive }.forEach { connection ->
            options.put(JSONObject().put("sourceId", "connection:${connection.spec.id}")
                .put("name", connection.spec.name).put("available", !locked && !phone.selectionPending && connection.acceptsSourceSelection()))
        }
        val position = JSONObject().put("mode", when (state.settings.gpsDataSource) {
            GpsDataSource.SYSTEM -> "phone"; GpsDataSource.NMEA -> "nmea"; GpsDataSource.DEMO -> "demo"; GpsDataSource.NONE -> "none"
        }).put("locked", locked).put("phoneStatus", phone.phase.name).put("selectionPending", phone.selectionPending)
            .put("selectedConnectionId", json(state.vesselSettings.metricSourcePins["POSITION_CONNECTION"]))
            .put("options", options)
        val metrics = JSONArray()
        var remainingCandidates = 128
        VesselMetricId.entries.forEach { metric ->
            val observation = sourceObservation(metric, state.vesselData)
            val candidates = state.vesselData.candidates[metric].orEmpty().distinctBy { it.source.persistentKey }
            val visibleCandidates = candidates.take(minOf(32, remainingCandidates))
            remainingCandidates -= visibleCandidates.size
            metrics.put(JSONObject().put("metric", metric.name)
                .put("pinnedSourceId", json(state.vesselSettings.metricSourcePins[metric.name]))
                .put("selectedSourceId", json(observation?.sourceIdentity?.persistentKey))
                .put("selectionReason", json(observation?.selectionReason))
                .put("freshness", if (!snapshotCurrent && observation?.value != null) "STALE" else json(observation?.freshness?.name))
                .put("selectable", metric !in derivedSourceMetrics && (metric != VesselMetricId.POSITION || !locked))
                .put("conflict", state.vesselData.conflicts[metric]?.active == true)
                .put("candidates", JSONArray().apply { visibleCandidates.forEach { put(candidateJson(metric, it, now, snapshotCurrent)) } })
                .put("candidatesTruncated", candidates.size > visibleCandidates.size))
        }
        return JSONObject().put("position", position).put("metrics", metrics).put("current", snapshotCurrent)
            .put("generatedElapsedMillis", state.vesselData.generatedElapsedRealtime)
    }

    private fun candidateJson(metric: VesselMetricId, candidate: VesselSourceCandidate<*>, now: Long, current: Boolean): JSONObject =
        JSONObject().put("sourceId", candidate.source.persistentKey).put("name", candidate.source.displayName)
            .put("type", candidate.source.sourceType.name).put("class", candidate.sourceClass.name)
            .put("connectionId", json(candidate.source.transportProfileId))
            .put("generation", json(candidate.source.connectionGeneration))
            .put("validity", if (current) MetricSourceEligibility.evaluate(metric, candidate, now).name else "STALE")
            .put("quality", candidate.quality.name).put("measuredAt", json(candidate.observedAtUtcMillis))
            .put("ageMillis", json((now - candidate.measuredElapsedRealtime).takeIf { it >= 0L }))

    private suspend fun selectSource(params: JSONObject): JSONObject {
        val metricName = text(params, "metric", 64)
        val metric = VesselMetricId.entries.firstOrNull { it.name == metricName } ?: invalid("Unknown metric")
        if (!params.has("sourceId")) invalid("sourceId is required; use null for automatic or position off")
        val sourceId = if (params.isNull("sourceId")) null else text(params, "sourceId", 512)
        val state = services.state.value
        if (metric in derivedSourceMetrics) fail("READ_ONLY_SOURCE", "This measurement is calculated from selected system readings")
        if (metric == VesselMetricId.POSITION) {
            if (state.active?.paused == false) fail("SOURCE_LOCKED", "Pause Anchor Watch explicitly before replacing its position source")
            if (sourceId != null && services.sources.phoneLocationStatus.value.selectionPending)
                fail("SOURCE_CHANGE_PENDING", "A position handover is already being checked; position off can cancel it")
            when {
                sourceId == null -> submit { services.sources.switchGpsDataSource(GpsDataSource.NONE) }
                sourceId == "phone" -> selectPhonePosition()
                sourceId.startsWith("connection:") -> {
                    val connection = inputConnection(sourceId.removePrefix("connection:"))
                    submitCall { services.sources.selectNmeaPositionConnection(connection.spec.id) }
                }
                else -> {
                    requireCurrentCandidates()
                    val candidate = state.vesselData.candidates[metric].orEmpty().firstOrNull { it.source.persistentKey == sourceId }
                        ?: fail("SOURCE_NOT_FOUND", "This source is no longer in the system catalogue; refresh Sources")
                    when {
                        candidate.sourceClass == VesselSourceClass.PHONE_GNSS -> selectPhonePosition()
                        candidate.source.sourceType == VesselSourceType.NMEA_INPUT -> {
                            inputConnection(candidate.source.transportProfileId ?: fail("SOURCE_NOT_FOUND", "This source has no input connection"))
                            submit { services.sources.setVesselMetricSource(metric, sourceId) }
                        }
                        else -> fail("SOURCE_NOT_SELECTABLE", "Open Data Center to review this position source")
                    }
                }
            }
        } else {
            if (sourceId != null) requireCurrentCandidates()
            if (sourceId != null && state.vesselData.candidates[metric].orEmpty().none { it.source.persistentKey == sourceId })
                fail("SOURCE_NOT_FOUND", "This source is no longer in the system catalogue; refresh Sources")
            submit { services.sources.setVesselMetricSource(metric, sourceId) }
        }
        return JSONObject().put("requested", true).put("outcome", "REQUESTED").put("metric", metric.name)
            .put("sourceId", json(sourceId)).put("state", sourcesSnapshot())
    }

    private suspend fun selectPhonePosition() {
        if (!hasLocationPermission()) fail("SYSTEM_PERMISSION_REQUIRED", "Open Data Center (data_center) and allow precise Android location, then choose Phone GPS again")
        if (!locationEnabled()) fail("LOCATION_DISABLED", "Open Data Center (data_center) and enable Android location, then choose Phone GPS again")
        val state = services.state.value
        if (GpsSourceSafety.blocksSystemGps(state.settings.mockEnabled, state.mockGps.state))
            fail("POSITION_PROXY_ACTIVE", "Stop the Android mock-location proxy in Data Center before choosing Phone GPS")
        // 复用已有事务：取得有效手机船位后才替换全局来源，不将权限检查当成有效定位。
        submit { services.sources.switchGpsDataSource(GpsDataSource.SYSTEM) }
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(os.context,
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(): Boolean = os.context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    private fun inputConnection(id: String): NmeaConnectionSnapshot {
        val connection = services.network.connections.value.firstOrNull { it.spec.id == id && it.spec.receive }
            ?: fail("CONNECTION_NOT_FOUND", "This NMEA input is not configured")
        if (!connection.acceptsSourceSelection()) fail("CONNECTION_NOT_CONNECTED", "Connect this input in Boat Network before selecting its position")
        return connection
    }

    private suspend fun setConnectionEnabled(params: JSONObject): JSONObject {
        val id = text(params, "id", 160)
        val enabled = boolean(params, "enabled")
        services.network.connections.value.firstOrNull { it.spec.id == id }
            ?: fail("CONNECTION_NOT_FOUND", "Only an existing configured NMEA connection can be controlled")
        // 即便宿主快照显示相同值，也将明确意图交给 Core 的幂等 setter，避免旧投影跳过 Stop。
        submit {
            if (enabled) services.network.startNmeaConnection(id) else services.network.stopNmeaConnection(id)
        }
        val current = services.network.connections.value.firstOrNull { it.spec.id == id }
        return JSONObject().put("requested", true).put("outcome", "REQUESTED").put("id", id).put("enabled", enabled)
            .put("state", current?.let(::connectionJson) ?: JSONObject.NULL)
    }

    private fun connectionJson(connection: NmeaConnectionSnapshot): JSONObject = JSONObject()
        .put("id", connection.spec.id).put("name", connection.spec.name).put("requested", connection.requested)
        .put("state", connection.state.name).put("receiveEnabled", connection.spec.receive)
        .put("sendEnabled", connection.spec.send).put("error", json(connection.error))

    private fun sharingSnapshot(): JSONObject {
        val state = services.state.value
        val settings = state.localNmeaServerSettings
        val runtime = state.localNmeaServerRuntime
        val transport = state.nmeaSharing
        val now = SystemClock.elapsedRealtime()
        return JSONObject().put("configured", settings.configured).put("requested", settings.serverRequested)
            .put("state", transport.state.name).put("port", settings.port).put("clientCount", transport.clientCount)
            .put("feed", settings.feed.name).put("capabilities", JSONArray(settings.capabilities.sorted()))
            .put("forwardFrom", JSONArray(settings.forwardFrom.sorted()))
            .put("generatedSentences", runtime.generatedSentences).put("queuedSentences", runtime.queuedSentences)
            .put("sentSentences", transport.sentSentences)
            .put("lastOutputAgeMillis", json(transport.lastOutputElapsed?.let { now - it }?.takeIf { it >= 0L }))
            .put("message", transport.message)
    }

    private suspend fun setSharingEnabled(params: JSONObject): JSONObject {
        val enabled = boolean(params, "enabled")
        val settings = services.state.value.localNmeaServerSettings
        if (enabled && (!settings.configured || settings.port !in 1024..65535))
            fail("SHARING_NOT_CONFIGURED", "Save a valid listening configuration in Data Sharing first")
        submit {
            if (enabled) services.sharing.startLocalNmeaServer() else services.sharing.stopLocalNmeaServer()
        }
        // requested 是持久租约，RUNNING 才是监听事实，sentSentences 才是真正写出。
        return JSONObject().put("requested", true).put("outcome", "REQUESTED").put("enabled", enabled).put("state", sharingSnapshot())
    }

    private suspend fun voyageSnapshot(): JSONObject {
        val state = system.voyage.snapshot()
        return JSONObject().put("sessionId", json(state.id?.toString())).put("phase", state.phase.name).put("name", state.name)
            .put("distanceMeters", state.distanceMeters.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("startedAt", json(state.startedAt)).put("pausedAt", json(state.pausedAt))
            .put("elapsedMillis", state.elapsedMillis(System.currentTimeMillis())).put("momentCount", state.momentCount)
            .put("commandPending", state.commandPending)
    }

    private suspend fun voyageCommand(params: JSONObject): JSONObject {
        val clientId = requestId(params)
        val action = when (text(params, "action", 12)) {
            "start" -> VoyageAction.START; "pause" -> VoyageAction.PAUSE
            "resume" -> VoyageAction.RESUME; "end" -> VoyageAction.FINISH
            else -> invalid("action must be start, pause, resume or end")
        }
        val sessionId = if (params.has("sessionId") && !params.isNull("sessionId")) positiveId(params, "sessionId") else null
        if (action == VoyageAction.START && sessionId != null) invalid("Starting a voyage must not specify a sessionId")
        if (action != VoyageAction.START && sessionId == null) invalid("sessionId is required for pause, resume and end")
        val name = if (params.has("name")) text(params, "name", 80, blank = true) else ""
        val motion = if (params.has("motion")) boolean(params, "motion") else false
        if (action != VoyageAction.START && (name.isNotEmpty() || motion)) invalid("name and motion apply only to start")
        val request = VoyageRequest(action, sessionId, name, motion, namespacedId(clientId))
        val known = readReceipt(request.requestId)
        if (known != null) {
            if (known.request != request) fail("REQUEST_ID_CONFLICT", "This requestId was already used with different voyage arguments")
            return receiptResponse(clientId, known).put("requested", true).put("reused", true)
        }
        val current = system.voyage.snapshot()
        if (action == VoyageAction.START && current.id != null) fail("VOYAGE_ALREADY_ACTIVE", "A voyage already exists; open Sailing Log to continue it")
        if (action != VoyageAction.START && current.id != sessionId) fail("SESSION_CHANGED", "The voyage changed; refresh its current session before acting")
        submitCall { system.voyage.request(request) }
        // 只读实际账本；不能根据请求已经返回、Job 结束或页面消失推断航行成功。
        val receipt = try { readReceipt(request.requestId) } catch (failure: ExtensionBridgeException) {
            fail("OUTCOME_UNKNOWN", "The command was submitted but its receipt is unavailable; query voyage.receipt with the same requestId")
        }
        return receiptResponse(clientId, receipt).put("requested", true).put("reused", false)
    }

    private suspend fun voyageReceipt(params: JSONObject): JSONObject {
        val clientId = requestId(params)
        val id = namespacedId(clientId)
        val recheck = if (params.has("recheck")) boolean(params, "recheck") else false
        var receipt = readReceipt(id)
        if (recheck && receipt != null && !receipt.terminal) {
            // 此命令只对账，绝不再次发送开始/暂停/继续/结束。
            submitCall { system.voyage.recheck(id) }
            receipt = readReceipt(id)
        }
        return receiptResponse(clientId, receipt).put("found", receipt != null)
    }

    private suspend fun readReceipt(id: String): VoyageCommandReceipt? = try {
        system.voyage.receipt(id)
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (_: Exception) { fail("RECEIPT_UNAVAILABLE", "The persistent voyage ledger cannot be read; do not repeat the command with a new requestId") }

    private suspend fun receiptResponse(clientId: String, receipt: VoyageCommandReceipt?): JSONObject = JSONObject()
        .put("requestId", clientId).put("receipt", receipt?.let {
            JSONObject().put("action", when (it.request.action) {
                VoyageAction.START -> "start"; VoyageAction.PAUSE -> "pause"; VoyageAction.RESUME -> "resume"; VoyageAction.FINISH -> "end"
            }).put("status", it.status.name).put("reason", json(it.reason)).put("sessionId", json(it.sessionId?.toString())).put("terminal", it.terminal)
        } ?: JSONObject.NULL).put("state", voyageSnapshot())

    private fun namespacedId(id: String): String = "ext:" + MessageDigest.getInstance("SHA-256")
        .digest((appId + '\u0000' + id).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Job 属于 Core；这里只等投递阶段，不取消后台任务，也不将完成信号解释为业务成功。 */
    private suspend fun submit(action: () -> Job) {
        authorize()
        val job = try { action() } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { unknownOutcome() }
        val completion = CompletableDeferred<Completion>()
        val handle = job.invokeOnCompletion { completion.complete(Completion(it)) }
        try {
            val result = withTimeoutOrNull(5_000L) { completion.await() } ?: unknownOutcome()
            if (result.failure != null) unknownOutcome()
        } finally { handle.dispose() }
    }

    private fun submitCall(action: () -> Any?) {
        authorize()
        try { action() } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { unknownOutcome() }
    }

    private data class Completion(val failure: Throwable?)
    private fun snapshotCurrent(now: Long = SystemClock.elapsedRealtime()): Boolean {
        val generated = services.state.value.vesselData.generatedElapsedRealtime
        return generated > 0L && now - generated in 0L..2_000L
    }
    private fun requireCurrentCandidates() {
        if (!snapshotCurrent()) fail("SOURCE_SNAPSHOT_STALE", "Wait for the current Core source catalogue before selecting a measurement")
    }
    private fun unknownOutcome(): Nothing = fail("OUTCOME_UNKNOWN", "The system could not confirm this request; refresh the actual state before trying again")
    private fun requestId(params: JSONObject): String = text(params, "requestId", 80).also {
        if (!it.matches(Regex("[A-Za-z0-9_-]{1,80}"))) invalid("requestId must contain 1–80 ASCII letters, numbers, underscores or hyphens")
    }
    private fun text(params: JSONObject, key: String, limit: Int, blank: Boolean = false): String {
        val value = params.opt(key) as? String ?: invalid("$key must be a string")
        if (value.length > limit || (!blank && value.isBlank()) || value.any { it.code < 32 }) invalid("$key is invalid")
        return value.trim()
    }
    private fun boolean(params: JSONObject, key: String): Boolean = params.opt(key) as? Boolean ?: invalid("$key must be a boolean")
    private fun positiveId(params: JSONObject, key: String): Long {
        val value = when (val number = params.opt(key)) {
            is String -> {
                if (!number.matches(Regex("[1-9][0-9]{0,18}"))) invalid("$key must be a positive decimal string")
                number.toLongOrNull() ?: invalid("$key is outside the supported range")
            }
            is Int -> number.toLong()
            is Long -> number.takeIf { it <= 9_007_199_254_740_991L }
                ?: invalid("Use a decimal string for a sessionId beyond the JavaScript safe integer range")
            else -> invalid("$key must be a decimal string")
        }
        if (value <= 0L) invalid("$key must be positive")
        return value
    }
    private fun json(value: Any?): Any = value ?: JSONObject.NULL
    private fun invalid(message: String): Nothing = fail("INVALID_ARGUMENT", message)
    private fun fail(code: String, message: String): Nothing = throw ExtensionBridgeException(code, message)

    private fun NmeaConnectionSnapshot.acceptsSourceSelection(): Boolean = requested && spec.receive && state in setOf(
        NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE)

}

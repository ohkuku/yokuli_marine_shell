package com.yokuli.runtime.marine.ais

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yokuli.anchorwatch.api.LocalMarineServices
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.model.PositionHealth
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.anchorwatch.runtime.RuntimeOwner
import com.yokuli.anchorwatch.runtime.RuntimeRequirement
import com.yokuli.anchorwatch.runtime.RuntimeResourceManager
import com.yokuli.anchorwatch.runtime.notification.NotificationCoordinator
import com.yokuli.runtime.contract.ais.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** 进程唯一 AIS 所有者：复用已获准连接，按单调时间串行维护交通；没有页面/地图依赖。 */
@Singleton
class LocalAisTrafficService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navigation: NavigationRepository,
    private val marine: LocalMarineServices,
    private val notifications: NotificationCoordinator,
    private val resources: RuntimeResourceManager,
) : AisTrafficService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val guard = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val engine = AisEngine()
    private val gson = Gson()
    private val settingsFile = AtomicFile(File(context.filesDir, "ais/preferences-v1.json"))
    private val cacheFile = AtomicFile(File(context.filesDir, "ais/last-observed-v1.json"))
    private var saved = SavedSettings()
    private val frames = Channel<AisFrame>(512)
    private val droppedFrames = AtomicLong()
    private val lastDroppedFrameElapsed=AtomicLong()
    private val foregroundRevision = AtomicLong()
    private val _snapshot = MutableStateFlow(TrafficSnapshot())
    override val snapshot = _snapshot.asStateFlow()
    private var persistenceError: String? = null
    private var foregroundError: String? = null
    private var foregroundActive = false
    private var foregroundLocation = false
    private var requestedLocationType = false
    private var lastLocationTypeAttempt=0L
    private var serviceRequested = false
    private var lastServiceAttempt=0L
    private var lastCacheWrite = 0L
    private var lastStatusText = ""
    private var lastResource: RuntimeRequirement? = null
    private val noticeLog = ArrayDeque<AisNotice>()
    private val announced = linkedMapOf<String, Long>()
    private val activeNotifications = linkedSetOf<String>()

    init {
        // Input collection starts immediately; bounded queue isolates malformed
        // or busy traffic from the shared position/wind/depth dispatchers.
        scope.launch {
            navigation.frames.collect { raw ->
                val body = raw.sentence
                if (body.substringBefore(',').takeLast(3) !in setOf("VDM", "VDO")) return@collect
                if (!frames.trySend(AisFrame(raw.connectionId, raw.generation, raw.peer, raw.originalSentence, raw.receivedElapsedRealtime)).isSuccess) recordDroppedFrame()
            }
        }
        scope.launch {
            guard.withLock {
                runCatching { withContext(Dispatchers.IO) {
                    File(context.filesDir,"ais/background-start-error.txt").takeIf{it.isFile&&it.length()<=512}?.let {
                        foregroundError=runCatching{it.readText()}.getOrNull()
                    }
                    read(settingsFile, MAX_SETTINGS_BYTES)?.let { json ->
                        runCatching { gson.fromJson(json, SavedSettings::class.java) }.onSuccess { value ->
                            if (value != null && validPreferences(value.preferences)) {
                                saved = value.copy(receipts = value.receipts.orEmpty().takeLast(64), acknowledgements = value.acknowledgements.orEmpty().take(256).toSet(), snoozes = value.snoozes.orEmpty().entries.take(256).associate { it.key to it.value })
                            } else persistenceError = "Saved AIS preferences could not be restored"
                        }.onFailure { persistenceError = "Saved AIS preferences could not be restored" }
                    }
                    read(cacheFile, MAX_CACHE_BYTES)?.let { json ->
                        runCatching {
                            val values=gson.fromJson<List<AisCachedTarget>>(json, object : TypeToken<List<AisCachedTarget>>() {}.type)
                            val safe=values.orEmpty().take(512).filter { cached -> runCatching {
                                // Gson can restore an unknown enum or a damaged
                                // required nested field as null; reject it here.
                                cached.kind.name
                                cached.staticData.hashCode()
                                cached.mmsi in 1..999999999 && cached.lastPosition?.let(::validPoint)!=false
                            }.getOrDefault(false) }
                            engine.restoreCache(safe)
                        }
                            .onFailure { persistenceError = "Last observed AIS cache could not be restored" }
                    }
                } }.onFailure { persistenceError="Saved AIS data could not be restored" }
                runCatching { notifications.createAisChannels(chinese()) }
                    .onFailure { foregroundError="Android traffic notification channels could not be created" }
                ready.complete(Unit)
                reconcileService(retry = true)
                refresh()
            }
        }
        scope.launch {
            ready.await()
            for (frame in frames) guard.withLock {
                // A queued sentence cannot resurrect a connection that the
                // user stopped, or the previous epoch after a reconnect.
                if (navigation.sourcesCurrent(mapOf(frame.connectionId to frame.generation)))
                    runCatching { engine.accept(frame) }.onFailure { recordDroppedFrame() }
            }
        }
        scope.launch {
            ready.await()
            while (isActive) {
                delay(1_000)
                guard.withLock {
                    refresh()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastCacheWrite >= 60_000) {
                        lastCacheWrite = now
                        val cache = engine.exportCache().take(512).map { it.copy(savedAtUtcMillis=System.currentTimeMillis()) }
                        withContext(Dispatchers.IO) {
                            runCatching { write(cacheFile, gson.toJson(cache), MAX_CACHE_BYTES) }
                                .onFailure { persistenceError = "Last observed AIS cache could not be saved" }
                        }
                    }
                }
            }
        }
    }

    override suspend fun command(command: AisCommand): AisCommandResult {
        ready.await()
        // Once accepted, a page disappearing cannot cancel a durable command
        // halfway through and leave its in-memory state behind its disk state.
        return withContext(NonCancellable + Dispatchers.Default) { guard.withLock {
            saved.receipts.firstOrNull { it.requestId == command.requestId }?.let { return@withLock it }
            try {
                var next = saved
                when (command) {
                    is AisCommand.UpdatePreferences -> {
                        require(validPreferences(command.preferences)) { "Invalid AIS preferences" }
                        // Focus/alias commands own these fields. A view range
                        // update created from an older snapshot must never
                        // overwrite another client's just-saved follow choice.
                        next = saved.copy(preferences = command.preferences.copy(
                            watchedMmsis=saved.preferences.watchedMmsis,aliases=saved.preferences.aliases))
                    }
                    is AisCommand.Watch -> {
                        require(command.mmsi in 1..999999999) { "Invalid MMSI" }
                        val ids = saved.preferences.watchedMmsis.toMutableSet().apply { if (command.watched) add(command.mmsi) else remove(command.mmsi) }
                        require(ids.size <= 256) { "Followed target limit reached" }
                        next = saved.copy(preferences = saved.preferences.copy(watchedMmsis = ids))
                    }
                    is AisCommand.Alias -> {
                        require(command.mmsi in 1..999999999) { "Invalid MMSI" }
                        val name = command.alias?.trim()?.takeIf { it.isNotEmpty() }
                        require(name == null || name.length <= 80 && name.none(Char::isISOControl)) { "Name must be 80 characters or fewer" }
                        val aliases = saved.preferences.aliases.toMutableMap().apply { if (name == null) remove(command.mmsi) else put(command.mmsi, name) }
                        require(aliases.size <= 256) { "Named target limit reached" }
                        next = saved.copy(preferences = saved.preferences.copy(aliases = aliases))
                    }
                    is AisCommand.Acknowledge -> {
                        require(snapshot.value.events.any { it.id == command.eventId }) { "This traffic event is no longer available" }
                        next = saved.copy(acknowledgements = (saved.acknowledgements + command.eventId).takeLastSet(256))
                    }
                    is AisCommand.Snooze -> {
                        require(snapshot.value.events.any { it.id == command.eventId && it.active }) { "This traffic event is no longer active" }
                        require(command.durationMillis in 30_000..3_600_000) { "Reminder duration must be between 30 seconds and one hour" }
                        next = saved.copy(snoozes = (saved.snoozes + (command.eventId to (System.currentTimeMillis() + command.durationMillis))).entries.toList().takeLast(256).associate { it.key to it.value })
                    }
                    is AisCommand.RetainTarget -> {
                        require(command.mmsi in 1..999999999) { "Invalid MMSI" }
                        require(command.ownerId.isNotBlank() && command.ownerId.length <= 128) { "Invalid target selection owner" }
                        engine.retain(command.mmsi, command.retain, command.ownerId)
                        refresh()
                        return@withLock AisCommandResult(true, requestId = command.requestId)
                    }
                }
                val result = AisCommandResult(true, requestId = command.requestId)
                next = next.copy(receipts = (next.receipts + result).takeLast(64))
                withContext(Dispatchers.IO) { write(settingsFile, gson.toJson(next), MAX_SETTINGS_BYTES) }
                saved = next
                persistenceError = null
                // User acknowledgement remains separate from target loss,
                // input ownership, layer display and the other alarm service.
                when (command) {
                    is AisCommand.Acknowledge -> engine.acknowledge(command.eventId)
                    is AisCommand.Snooze -> engine.snooze(command.eventId, SystemClock.elapsedRealtime() + command.durationMillis)
                    else -> Unit
                }
                reconcileService(retry = command is AisCommand.UpdatePreferences)
                refresh()
                result
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = error.message?.take(180) ?: "AIS change could not be saved"
                persistenceError = message
                refresh()
                AisCommandResult(false, message, command.requestId)
            }
        } }
    }

    /** Android Service 只反馈执行状态；不另建目标仓库、不代替用户开启输入。 */
    internal fun foregroundChanged(active: Boolean, error: String? = null, location: Boolean = false) {
        val revision=foregroundRevision.incrementAndGet()
        scope.launch {
            ready.await()
            guard.withLock {
                if(revision!=foregroundRevision.get())return@withLock
                foregroundActive = active && saved.preferences.monitoringEnabled
                foregroundLocation = active && location
                foregroundError = error
                if(foregroundActive)lastStatusText=""
                if(foregroundActive)withContext(Dispatchers.IO){runCatching{File(context.filesDir,"ais/background-start-error.txt").delete()}}
                refresh()
            }
        }
    }

    internal fun monitoringRequested(): Boolean = saved.preferences.monitoringEnabled

    internal fun wantsLocation(): Boolean = marine.state.value.settings.gpsDataSource == GpsDataSource.SYSTEM &&
        ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED

    private fun reconcileService(retry: Boolean) {
        if (!saved.preferences.monitoringEnabled) {
            serviceRequested = false
            foregroundError = null
            context.stopService(Intent(context, AisMonitoringService::class.java))
            foregroundActive = false
            return
        }
        if (foregroundActive && (!retry || foregroundLocation==wantsLocation()) || serviceRequested && !retry) return
        serviceRequested = true
        lastServiceAttempt=SystemClock.elapsedRealtime()
        requestedLocationType = wantsLocation()
        foregroundError = null
        runCatching { ContextCompat.startForegroundService(context, Intent(context, AisMonitoringService::class.java)) }
            .onFailure { foregroundError = "Android did not allow AIS background monitoring: ${it.javaClass.simpleName}" }
    }

    private fun refresh() {
        try { refreshSnapshot() } catch(error:Exception) {
            if(error is CancellationException)throw error
            // A runtime fault must freeze the last observation honestly, not
            // terminate aging forever or let one decoder stop other services.
            val old=_snapshot.value
            val message="AIS processing is interrupted: ${error.javaClass.simpleName}"
            _snapshot.value=old.copy(generatedElapsed=SystemClock.elapsedRealtime(),
                targets=old.targets.map { target -> target.copy(state=AisTargetState.LOST,
                    relative=AisRelativeMetrics(AisCpaState.STALE,reason="traffic_runtime_interrupted")) },
                events=old.events.map { event -> if(event.active)event.copy(reason="risk_cannot_be_reconfirmed") else event },
                backgroundLimitations=(old.backgroundLimitations+message).distinct().takeLast(12),
                runtime=old.runtime.copy(ready=true,foregroundError=message))
        }
    }

    private fun refreshSnapshot() {
        val now = SystemClock.elapsedRealtime()
        val state = marine.state.value
        val preferences = saved.preferences
        if(preferences.monitoringEnabled && !foregroundActive && now-lastServiceAttempt>=15_000 && applicationVisible())reconcileService(retry=true)
        val wantsLocation=wantsLocation()
        val returningToApp=foregroundActive && wantsLocation && !foregroundLocation && now-lastLocationTypeAttempt>=5_000 && applicationVisible()
        if(foregroundActive && (requestedLocationType != wantsLocation || returningToApp)) {
            lastLocationTypeAttempt=now
            requestedLocationType=wantsLocation()
            runCatching { ContextCompat.startForegroundService(context,Intent(context,AisMonitoringService::class.java)) }
                .onFailure { foregroundError="Android could not update AIS background location capability" }
        }
        val inputs = navigation.connections.value.filter { it.spec.receive }.map { connection ->
            AisInputHealth(connection.spec.id, connection.spec.name, when {
                !connection.requested -> AisInputState.DISABLED
                connection.state in setOf(NmeaConnectionState.CONNECTING, NmeaConnectionState.RECONNECTING) -> AisInputState.CONNECTING
                navigation.isConnectionOpen(connection.spec.id) -> AisInputState.ONLINE
                else -> AisInputState.INTERRUPTED
            }, connection.transport.connectionGeneration,
                connection.transport.lastByteReceivedElapsedRealtime,
                connection.lastLegalSentenceElapsed,
                lastError = connection.error ?: connection.transport.lastDisconnectReason)
        }
        val ownship = ownship(now)
        val traffic = engine.tick(now, ownship, preferences, inputs)
        // These IDs cannot acknowledge a new event after process restart:
        // cached dynamic data is never promoted to a live event by restore.
        saved.acknowledgements.forEach(engine::acknowledge)
        saved.snoozes.forEach { (id, until) ->
            val remaining = (until - System.currentTimeMillis()).coerceIn(0, 3_600_000)
            if (remaining > 0) engine.snooze(id, now + remaining)
        }
        val notificationAllowed = notifications.aisNotificationAllowed(preferences.soundEnabled)
        val soundAllowed = notifications.aisSoundAllowed()
        val restricted = context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        val limitations = buildList {
            if (preferences.monitoringEnabled && !foregroundActive) add("AIS background monitoring is not active; open AIS to restore it")
            if (preferences.monitoringEnabled && inputs.none { it.state == AisInputState.ONLINE }) add("No enabled AIS input is currently online; closed connections remain closed")
            if (preferences.monitoringEnabled && !notificationAllowed) add("Android traffic notifications are blocked")
            if (preferences.monitoringEnabled && preferences.soundEnabled && !soundAllowed) add("Traffic sound is blocked by Android channel or Do Not Disturb settings")
            if (preferences.monitoringEnabled && restricted) add("Android restricts this application's background activity")
            if (preferences.monitoringEnabled && state.settings.gpsDataSource==GpsDataSource.SYSTEM && !foregroundLocation) add("AIS cannot retain the selected phone position in the background without Android location permission")
            if (preferences.monitoringEnabled && ownship.positionValid.not()) add("Current own-vessel position is unavailable; relative traffic risk cannot be calculated")
            if (droppedFrames.get() > 0 && now-lastDroppedFrameElapsed.get()<=60_000) add("Input processing reached its bounded queue; some recent AIS reports were skipped")
            persistenceError?.let(::add)
            foregroundError?.let(::add)
        }
        val requirement = if (preferences.monitoringEnabled && foregroundActive) RuntimeRequirement(
            needsNmeaTransport = inputs.any { it.state != AisInputState.DISABLED },
            needsSystemLocation = state.settings.gpsDataSource == GpsDataSource.SYSTEM && foregroundLocation,
            needsWakeLock = true, needsWifiLock = state.settings.keepWifiAwake,
        ) else null
        if (requirement != lastResource) {
            runCatching { resources.set(RuntimeOwner.AIS_TRAFFIC, requirement) }
                .onSuccess { lastResource = requirement }
                .onFailure { foregroundError = "AIS background resources could not be acquired: ${it.javaClass.simpleName}" }
        }
        val effectiveEvents = traffic.events.map { event -> event.copy(
            acknowledged = event.acknowledged || event.id in saved.acknowledgements,
            snoozedUntilElapsed = maxOf(event.snoozedUntilElapsed, saved.snoozes[event.id]?.let { now + (it-System.currentTimeMillis()).coerceIn(0,3_600_000) } ?: 0L),
        ) }
        publishRiskNotices(traffic.copy(events = effectiveEvents), now, notificationAllowed)
        _snapshot.value = traffic.copy(
            events = effectiveEvents, backgroundLimitations = limitations,
            runtime = AisRuntimeStatus(true, preferences.monitoringEnabled, foregroundActive, notificationAllowed, soundAllowed, restricted, droppedFrames.get(), persistenceError, foregroundError),
            notices = noticeLog.toList(),
        )
        if (foregroundActive) {
            val text = if (chinese()) {
                if (limitations.isEmpty()) "${traffic.targets.count { it.state == AisTargetState.CURRENT }} 个当前目标 · 交通提醒已开启"
                else "监控受限 · ${when { !notificationAllowed -> "通知权限受限"; inputs.none { it.state == AisInputState.ONLINE } -> "等待已启用输入"; !ownship.positionValid -> "等待本船定位"; else -> "请在 AIS 查看状态" }}"
            } else if (limitations.isEmpty()) "${traffic.targets.count { it.state == AisTargetState.CURRENT }} current targets · traffic alerts enabled" else "Monitoring limited · open AIS for status"
            if (text != lastStatusText) {
                lastStatusText = text
                runCatching { notifications.publishAisForeground(notifications.aisForegroundNotification(text, chinese())) }
            }
        }
    }

    private fun publishRiskNotices(traffic: TrafficSnapshot, now: Long, canNotify: Boolean) {
        val eligible = traffic.events.filter { it.active && !it.acknowledged && it.snoozedUntilElapsed <= now }
        val current = eligible.mapTo(mutableSetOf()) { it.id }
        activeNotifications.filter { it !in current }.toList().forEach { id ->
            runCatching { notifications.cancelAisEvent(id) }; activeNotifications.remove(id)
        }
        if (!traffic.preferences.monitoringEnabled) return
        eligible.forEach { event ->
            val interval = if (event.level == AisRiskLevel.URGENT) 120_000 else 300_000
            val last = announced[event.id]
            if (last != null && now-last < interval) return@forEach
            announced[event.id] = now
            val name = traffic.target(event.mmsi)?.displayName ?: event.mmsi.toString().padStart(9, '0')
            val zh = if(event.reason=="risk_cannot_be_reconfirmed")"目标资料不足，之前的交通风险无法继续确认" else when (event.kind) { AisRiskKind.CPA -> "预计会遇距离进入提醒范围"; AisRiskKind.PROXIMITY -> "目标进入近距离提醒范围"; AisRiskKind.ANCHOR_PROXIMITY -> if(traffic.preferences.anchorUsesAnchorPoint)"目标进入锚点区域的交通警戒范围" else "目标进入本船锚泊交通警戒范围"; AisRiskKind.DISTRESS -> "收到真实激活的遇险信标"; AisRiskKind.TARGET_LOST -> "关注目标资料变旧，风险无法继续确认" }
            val en=if(event.reason=="risk_cannot_be_reconfirmed")"Target data is insufficient to reconfirm the previous traffic risk" else when(event.kind) {
                AisRiskKind.CPA->"Predicted closest approach is within your traffic alert range"
                AisRiskKind.PROXIMITY->"Target has entered your nearby-traffic alert range"
                AisRiskKind.ANCHOR_PROXIMITY->if(traffic.preferences.anchorUsesAnchorPoint)"Target has entered the configured anchor-area traffic guard" else "Target has entered the traffic guard around your anchored vessel"
                AisRiskKind.DISTRESS->"An active AIS distress beacon has been received"
                AisRiskKind.TARGET_LOST->"Target data is aging; its previous risk can no longer be confirmed"
            }
            val titleZh = "AIS · $name"
            val titleEn = "AIS · $name"
            val notice = AisNotice("${event.id}:$now",event.id,event.mmsi,event.level,titleZh,titleEn,zh,en,System.currentTimeMillis())
            noticeLog.addLast(notice)
            while (noticeLog.size > 80) noticeLog.removeFirst()
            if (canNotify) runCatching {
                notifications.publishAisEvent(event.id,event.mmsi,if(chinese())titleZh else titleEn,if(chinese())zh else en,traffic.preferences.soundEnabled)
                activeNotifications.add(event.id)
            }.onFailure { foregroundError="Android refused the AIS traffic notification: ${it.javaClass.simpleName}" }
        }
        if (announced.size > 512) announced.keys.filter { it !in current }.take(announced.size-512).forEach(announced::remove)
    }

    private fun ownship(now: Long): AisOwnship {
        val state = marine.state.value
        val accepted = state.acceptedPosition.takeIf { it.selectedSource == state.settings.gpsDataSource }
        val fix = accepted?.acceptedFix
        val point = fix?.let { AisPoint(it.latitude,it.longitude) }?.takeIf(::validPoint)
        val current = state.settings.gpsDataSource != GpsDataSource.NONE && state.settings.gpsDataSource != GpsDataSource.DEMO &&
            fix?.valid == true && !fix.isMockLocation && point != null && now-fix.receivedElapsedRealtime in 0..15_000 &&
            accepted?.health != PositionHealth.GPS_LOST && accepted?.disposition != "SEEDED_FROM_PERSISTED_ACCEPTED_FIX"
        fun numeric(value: VesselObservation<Double>, metric: VesselMetricId): Double? = value.value?.takeIf {
            it.isFinite() && value.freshness == VesselDataFreshness.FRESH && value.quality != VesselDataQuality.UNKNOWN &&
                value.conflict?.active != true && value.receivedElapsedRealtime?.let { time -> now-time in 0..MetricSourceEligibility.measurementLeaseMillis(metric) } == true
        }
        val data=state.vesselData
        val anchor=state.active?.takeIf { it.active && !it.paused }
        return AisOwnship(point,fix?.receivedElapsedRealtime,current,
            numeric(data.sogKnots,VesselMetricId.SOG)?.takeIf{it>=0.0}?.times(1852.0/3600.0),data.sogKnots.receivedElapsedRealtime,
            numeric(data.cogTrueDegrees,VesselMetricId.COG)?.takeIf{it in 0.0..<360.0},data.cogTrueDegrees.receivedElapsedRealtime,
            numeric(data.headingTrueDegrees,VesselMetricId.HEADING_TRUE)?.takeIf{it in 0.0..<360.0},data.headingTrueDegrees.receivedElapsedRealtime,
            anchor != null,anchor?.let { AisPoint(it.anchorLatitude,it.anchorLongitude) }?.takeIf(::validPoint))
    }

    private fun validPreferences(value: AisPreferences): Boolean = runCatching {
        value.lastView.name.isNotBlank() && value.orientation.name.isNotBlank() &&
            value.rangeNauticalMiles.isFinite() && value.rangeNauticalMiles in 0.25..16.0 &&
            (value.ownMmsi == null || value.ownMmsi in 1..999999999) &&
            value.watchedMmsis.size <= 256 && value.watchedMmsis.all { it in 1..999999999 } &&
            value.aliases.size <= 256 && value.aliases.all { (id,name) -> id in 1..999999999 && name.length in 1..80 && name.none(Char::isISOControl) } &&
            value.cpaDistanceMeters.isFinite() && value.cpaDistanceMeters in 10.0..18520.0 &&
            value.cpaTimeSeconds.isFinite() && value.cpaTimeSeconds in 60.0..7200.0 &&
            value.proximityMeters.isFinite() && value.proximityMeters in 10.0..18520.0 &&
            value.anchorProximityMeters.isFinite() && value.anchorProximityMeters in 10.0..18520.0
    }.getOrDefault(false)

    private fun read(file: AtomicFile, limit: Int): String? {
        if (!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        return runCatching {
            file.openRead().use { stream ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(4096)
                while(output.size()<=limit) {
                    val count=stream.read(buffer,0,minOf(buffer.size,limit+1-output.size()))
                    if(count<0)break
                    output.write(buffer,0,count)
                }
                val bytes=output.toByteArray()
                require(bytes.size <= limit) { "AIS storage exceeds its size limit" }
                String(bytes,Charsets.UTF_8)
            }
        }.onFailure { persistenceError = "Saved AIS data could not be read" }.getOrNull()
    }

    private fun write(file: AtomicFile, json: String, limit: Int) {
        val bytes=json.toByteArray(Charsets.UTF_8)
        require(bytes.size<=limit) { "AIS saved data exceeds its size limit" }
        check(file.baseFile.parentFile?.let { it.isDirectory || it.mkdirs() }==true) { "AIS storage directory could not be created" }
        val stream=file.startWrite()
        try { stream.write(bytes);file.finishWrite(stream) } catch(error:Exception) { file.failWrite(stream);throw error }
    }

    internal fun chinese()=marine.state.value.settings.appLanguage in setOf(AppLanguage.SIMPLIFIED_CHINESE,AppLanguage.TRADITIONAL_CHINESE)
    private fun applicationVisible()=runCatching {
        ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance==ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)
    private fun recordDroppedFrame() { droppedFrames.incrementAndGet();lastDroppedFrameElapsed.set(SystemClock.elapsedRealtime()) }
    private fun validPoint(point:AisPoint)=point.latitude.isFinite()&&point.longitude.isFinite()&&point.latitude in -90.0..90.0&&point.longitude in -180.0..180.0
    private fun <T> Set<T>.takeLastSet(count:Int)=toList().takeLast(count).toSet()
    private data class SavedSettings(
        val preferences:AisPreferences=AisPreferences(), val acknowledgements:Set<String> = emptySet(),
        val snoozes:Map<String,Long> = emptyMap(), val receipts:List<AisCommandResult> = emptyList(),
    )
    private companion object { const val MAX_SETTINGS_BYTES=256*1024; const val MAX_CACHE_BYTES=4*1024*1024 }
}

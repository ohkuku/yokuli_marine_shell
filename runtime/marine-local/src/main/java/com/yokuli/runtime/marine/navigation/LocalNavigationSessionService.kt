package com.yokuli.runtime.marine.navigation

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.yokuli.anchorwatch.api.LocalMarineServices
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.runtime.RuntimeOwner
import com.yokuli.anchorwatch.runtime.RuntimeRequirement
import com.yokuli.anchorwatch.runtime.RuntimeResourceManager
import com.yokuli.runtime.contract.navigation.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/** 唯一执行导航所有者；不打开socket、不修改选源，不因页面生命周期结束会话。 */
@Singleton
class LocalNavigationSessionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marine: LocalMarineServices,
    private val resources: RuntimeResourceManager,
) : NavigationSessionService {
    private data class Document(val schema: Int = 1, val session: NavigationSession? = null,
        val receipts: List<NavigationReceipt> = emptyList(), val legacyImported: Boolean = false)
    private data class Work(val command: NavigationCommand, val result: CompletableDeferred<NavigationReceipt>)
    private data class AutoApproach(val key: String, val source: String, val lastElapsed: Long,
        val armed: Boolean = false, val since: Long? = null, val samples: Int = 0, val lastAlong: Double? = null, val crossed: Boolean = false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val guard = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val commands = Channel<Work>(64)
    private val gson = Gson()
    private val file = AtomicFile(File(context.filesDir, "navigation/session-v1.json"))
    private var saved = Document()
    private var readBlocked = false
    private var auto: AutoApproach? = null
    private var lastAdvanceElapsed = 0L
    private var lastCheckpoint = 0L
    private var resource: RuntimeRequirement? = null
    private var locationAllowed = false
    private data class GuidanceKey(val id:String,val revision:Long,val geometryIndex:Int?,val fix:NavigationFixSnapshot?,val fresh:Boolean,val speedFresh:Boolean,val courseFresh:Boolean)
    private var guidanceKey:GuidanceKey?=null
    private var cachedGuidance:NavigationGuidance?=null
    private var externalTargetEpoch:Triple<String,String?,Long>?=null
    internal val locationRequested = marine.state.map {it.settings.gpsDataSource==GpsDataSource.SYSTEM}.distinctUntilChanged()
    private val _state = MutableStateFlow(NavigationState())
    override val state = _state.asStateFlow()
    private val _receipts = MutableStateFlow<List<NavigationReceipt>>(emptyList())
    override val receipts = _receipts.asStateFlow()

    init {
        scope.launch {
            guard.withLock { read() }
            loaded.complete(Unit)
            while (isActive) {
                val advance = guard.withLock {
                    publish()
                    val command = automaticAdvance()
                    val now = SystemClock.elapsedRealtime()
                    if (!readBlocked && saved.session?.phase == NavigationPhase.ACTIVE && now - lastCheckpoint >= 30_000) {
                        lastCheckpoint = now
                        try { persist(saved.copy(session = saved.session?.copy(lastGuidance = _state.value.guidance))) }
                        catch(cancelled:CancellationException){throw cancelled}
                        catch(error:Exception){_state.value = _state.value.copy(storageIssue = error.message ?: "CHECKPOINT_FAILED")}
                    }
                    command
                }
                if (advance != null) execute(advance)
                delay(250)
            }
        }
        scope.launch {
            for (work in commands) {
                loaded.await()
                val receipt = try { guard.withLock { apply(work.command) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { NavigationReceipt(work.command.requestId, work.command.action, NavigationResult.FAILED, reason = error.message ?: "NAVIGATION_COMMAND_FAILED") }
                work.result.complete(receipt)
            }
        }
    }

    override suspend fun execute(command: NavigationCommand): NavigationReceipt {
        val response = CompletableDeferred<NavigationReceipt>()
        if (commands.trySend(Work(command, response)).isFailure)
            return NavigationReceipt(command.requestId, command.action, NavigationResult.FAILED, reason = "NAVIGATION_QUEUE_FULL")
        return response.await()
    }

    override suspend fun importLegacy(route: NavigationRouteSnapshot?, targetIndex: Int) {
        val response = CompletableDeferred<Unit>()
        scope.launch {
            loaded.await()
            try {
                guard.withLock {
                    check(!readBlocked) { "NAVIGATION_READ_BLOCKED" }
                    if (saved.legacyImported) return@withLock
                    val valid = route?.takeIf(::validRoute)
                    val session = if (saved.session == null && valid != null) NavigationSession(
                        "legacy-${valid.id}", 1, NavigationSource.LOCAL, valid, targetIndex.takeIf{it in valid.targetIndices}?:valid.targetIndices.first(),
                        NavigationPhase.RECOVERY_REQUIRED, startedAtUtcMillis = System.currentTimeMillis(), updatedAtUtcMillis = System.currentTimeMillis()) else saved.session
                    persist(saved.copy(session = session, legacyImported = true))
                    publish()
                }
                response.complete(Unit)
            } catch (error: Exception) {
                _state.value = _state.value.copy(storageIssue = error.message ?: "MIGRATION_FAILED")
                response.completeExceptionally(error)
            }
        }
        response.await()
    }

    override fun retryRead() { scope.launch { guard.withLock { if (readBlocked) read() else publish() } } }

    private suspend fun read() {
        try {
            val restored = withContext(Dispatchers.IO) {
                if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) Document()
                else gson.fromJson(file.openRead().bufferedReader().use { it.readText() }, Document::class.java)
                    ?: error("NAVIGATION_DOCUMENT_EMPTY")
            }
            require(restored.schema == 1) { "NAVIGATION_SCHEMA_UNSUPPORTED" }
            restored.session?.route?.let { require(validRoute(it)) { "NAVIGATION_ROUTE_INVALID" } }
            restored.session?.let { session ->
                require(session.id.isNotBlank() && session.revision>0 && validSettings(session.settings)) { "NAVIGATION_SESSION_INVALID" }
                require(session.source==NavigationSource.EXTERNAL_NMEA && !session.externalSourceId.isNullOrBlank() ||
                    session.source==NavigationSource.LOCAL && session.targetIndex in session.route?.targetIndices.orEmpty()) { "NAVIGATION_TARGET_INVALID" }
            }
            restored.session?.takeIf{it.source==NavigationSource.LOCAL}?.let {session ->
                require(session.geometryIndex?.let{it in 0..session.targetIndex}!=false) {"NAVIGATION_GEOMETRY_INVALID"}
            }
            require(restored.receipts.size <= 32) { "NAVIGATION_RECEIPTS_INVALID" }
            saved = restored.copy(session = restored.session?.let { session ->
                if (session.ongoing) session.copy(phase = NavigationPhase.RECOVERY_REQUIRED) else session
            })
            guidanceKey = null
            cachedGuidance = null
            auto = null
            readBlocked = false
            _receipts.value = saved.receipts
            _state.value = _state.value.copy(ready = true, storageIssue = null)
            publish()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            readBlocked = true
            _state.value = _state.value.copy(ready = false, storageIssue = error.message ?: "NAVIGATION_READ_FAILED")
        }
    }

    private suspend fun persist(document: Document) {
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                file.baseFile.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "NAVIGATION_DIRECTORY_UNAVAILABLE" } }
                val bytes = gson.toJson(document).toByteArray(Charsets.UTF_8)
                val output = file.startWrite()
                try { output.write(bytes); output.fd.sync(); file.finishWrite(output) }
                catch (error: Throwable) { file.failWrite(output); throw error }
                // AtomicFile会记录而不抛出部分同步/重命名错误；确认最终文件后才发布成功回执。
                check(file.openRead().use { it.readBytes() }.contentEquals(bytes)) { "NAVIGATION_WRITE_NOT_CONFIRMED" }
            }
        } catch (error: Exception) {
            readBlocked = true
            _state.value = _state.value.copy(ready = false, storageIssue = error.message ?: "NAVIGATION_WRITE_FAILED")
            throw error
        }
        saved = document
        _receipts.value = document.receipts
        _state.value = _state.value.copy(storageIssue = null)
    }

    private suspend fun apply(command: NavigationCommand): NavigationReceipt {
        saved.receipts.firstOrNull { it.requestId == command.requestId }?.let { return it }
        fun result(value: NavigationResult, reason: String? = null, session: NavigationSession? = saved.session) =
            NavigationReceipt(command.requestId, command.action, value, session?.id, session?.revision, reason)
        if (readBlocked) return result(NavigationResult.FAILED, "NAVIGATION_READ_BLOCKED")
        if (command.requestId.isBlank() || command.requestId.length > 128) return result(NavigationResult.REJECTED, "INVALID_REQUEST_ID")
        val current = saved.session
        if (current?.id != command.expectedSessionId || current?.revision != command.expectedRevision) {
            return rememberReceipt(result(NavigationResult.CONFLICT, "NAVIGATION_CHANGED"), saved)
        }
        val settings = command.settings ?: if(command.action==NavigationAction.START)NavigationSettings(plannedSpeedMetersPerSecond=marine.state.value.vesselSettings.plannedSpeedMetersPerSecond)else current?.settings ?: NavigationSettings()
        if (!validSettings(settings)) return rememberReceipt(result(NavigationResult.REJECTED, "INVALID_NAVIGATION_SETTINGS"), saved)
        val nowUtc = System.currentTimeMillis()
        val fix = fix()
        val livePosition = fix?.takeIf { it.positionAccepted && it.point.valid && SystemClock.elapsedRealtime() - it.elapsedMillis in 0..10_000 }
        var candidate: NavigationSession? = current
        var rejection: String? = null
        when (command.action) {
            NavigationAction.START, NavigationAction.REPLAN -> {
                val route = command.route
                if (route == null || !validRoute(route)) rejection = "INVALID_ROUTE"
                else if (command.action == NavigationAction.REPLAN && current?.ongoing != true) rejection = "NAVIGATION_NOT_ACTIVE"
                else if (command.action == NavigationAction.REPLAN && current?.source != NavigationSource.LOCAL) rejection = "EXTERNAL_DEVICE_OWNS_ROUTE"
                else if (command.action == NavigationAction.REPLAN && livePosition == null) rejection = "POSITION_REQUIRED"
                else if (command.action == NavigationAction.REPLAN && livePosition != null && NavigationGeometry.distance(route.waypoints.first().point,livePosition.point)>max(10.0,livePosition.accuracyMeters?:10.0)) rejection = "REPLAN_START_MOVED"
                else if ((command.targetIndex ?: route.targetIndices.first()) !in route.targetIndices) rejection = "INVALID_TARGET"
                else candidate = NavigationSession(
                    if (command.action == NavigationAction.START) "nav-${command.requestId}" else current!!.id,
                    if (command.action == NavigationAction.START) 1 else current!!.revision + 1,
                    NavigationSource.LOCAL, route.copy(waypoints = route.waypoints.toList(),navigationTargetIndices=route.navigationTargetIndices?.toList()), command.targetIndex ?: route.targetIndices.first(),
                    NavigationPhase.ACTIVE, settings, analysisReference = command.analysisReference,
                    approachOrigin = livePosition?.point, approachTargetIndex = command.targetIndex ?: route.targetIndices.first(),
                    geometryIndex=initialGeometry(route,command.targetIndex?:route.targetIndices.first()),approachGeometryIndex=initialGeometry(route,command.targetIndex?:route.targetIndices.first()), startedAtUtcMillis = if (command.action == NavigationAction.START) nowUtc else current!!.startedAtUtcMillis,
                    updatedAtUtcMillis = nowUtc)
            }
            NavigationAction.SELECT_EXTERNAL -> {
                if (command.externalSourceId.isNullOrBlank() || _state.value.externalSources.none { it.id == command.externalSourceId }) rejection = "EXTERNAL_SOURCE_NOT_FOUND"
                else candidate = NavigationSession("nav-${command.requestId}", 1, NavigationSource.EXTERNAL_NMEA, null, 0,
                    NavigationPhase.ACTIVE, settings.copy(advanceMode = WaypointAdvanceMode.MANUAL), command.externalSourceId,
                    startedAtUtcMillis = nowUtc, updatedAtUtcMillis = nowUtc)
            }
            else -> {
                if (current == null || !current.ongoing) rejection = "NAVIGATION_NOT_ACTIVE"
                else when (command.action) {
                    NavigationAction.SELECT_TARGET -> if (current.source != NavigationSource.LOCAL || command.targetIndex == null || command.targetIndex !in current.route!!.targetIndices) rejection = "INVALID_TARGET"
                        else candidate = current.copy(targetIndex = command.targetIndex!!, approachOrigin = livePosition?.point, approachTargetIndex = command.targetIndex!!,geometryIndex=initialGeometry(current.route!!,command.targetIndex!!),approachGeometryIndex=initialGeometry(current.route!!,command.targetIndex!!))
                    NavigationAction.ADVANCE -> if (current.phase != NavigationPhase.ACTIVE) rejection = "NAVIGATION_NOT_ACTIVE"
                        else if (current.source != NavigationSource.LOCAL) rejection = "EXTERNAL_DEVICE_OWNS_ADVANCE"
                        else if (current.targetIndex >= current.route!!.targetIndices.last()) rejection = "FINAL_ARRIVAL_REQUIRES_CONFIRMATION"
                        else candidate = current.copy(targetIndex = current.route!!.targetIndices.first{it>current.targetIndex},geometryIndex=current.targetIndex+1)
                    NavigationAction.ARRIVE -> if(current.source==NavigationSource.LOCAL&&current.targetIndex!=current.route!!.targetIndices.last())rejection="FINAL_TARGET_REQUIRED" else candidate = current.copy(phase = NavigationPhase.ARRIVED)
                    NavigationAction.END -> candidate = current.copy(phase = NavigationPhase.ENDED)
                    NavigationAction.PAUSE -> candidate = current.copy(phase = NavigationPhase.PAUSED)
                    NavigationAction.RESUME -> if (livePosition == null && current.source == NavigationSource.LOCAL) rejection = "POSITION_REQUIRED"
                        else candidate = current.copy(phase = NavigationPhase.ACTIVE, approachOrigin = current.approachOrigin ?: livePosition?.point)
                    NavigationAction.SETTINGS -> candidate = current.copy(settings = if (current.source == NavigationSource.EXTERNAL_NMEA) settings.copy(advanceMode = WaypointAdvanceMode.MANUAL) else settings)
                    else -> Unit
                }
                if (candidate != null && rejection == null) candidate = candidate.copy(revision = current!!.revision + 1, updatedAtUtcMillis = nowUtc)
            }
        }
        if (rejection != null) return rememberReceipt(result(NavigationResult.REJECTED, rejection), saved)
        val receipt = result(NavigationResult.SAVED, session = candidate)
        return try {
            val next = candidate?.let { it.copy(lastGuidance = if (it.source == NavigationSource.LOCAL) NavigationGeometry.guidance(it, fix, SystemClock.elapsedRealtime(), nowUtc) else externalGuidance(it, SystemClock.elapsedRealtime())) }
            persist(saved.copy(session = next, receipts = (saved.receipts + receipt).takeLast(32), legacyImported = true))
            auto = null
            if (command.action in setOf(NavigationAction.ADVANCE, NavigationAction.SELECT_TARGET)) lastAdvanceElapsed = SystemClock.elapsedRealtime()
            publish()
            reconcileForeground()
            receipt
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            _state.value = _state.value.copy(storageIssue = error.message ?: "NAVIGATION_WRITE_FAILED")
            result(NavigationResult.FAILED, "NAVIGATION_WRITE_FAILED")
        }
    }

    private suspend fun rememberReceipt(receipt: NavigationReceipt, document: Document): NavigationReceipt = try {
        persist(document.copy(receipts = (document.receipts + receipt).takeLast(32)))
        receipt
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        _state.value = _state.value.copy(storageIssue = error.message ?: "NAVIGATION_WRITE_FAILED")
        receipt.copy(result = NavigationResult.FAILED, reason = "NAVIGATION_WRITE_FAILED")
    }

    private fun validRoute(route: NavigationRouteSnapshot) = route.id.isNotBlank() && route.id.length <= 256 && route.revision.isNotBlank() &&
        route.waypoints.size in 1..10_000 && route.waypoints.all { it.id.isNotBlank() && it.point.valid } && route.waypoints.map { it.id }.distinct().size == route.waypoints.size &&
        route.targetIndices.isNotEmpty() && route.targetIndices==route.targetIndices.distinct().sorted() && route.targetIndices.all{it in route.waypoints.indices} && route.targetIndices.last()==route.waypoints.lastIndex
    private fun initialGeometry(route:NavigationRouteSnapshot,target:Int):Int =
        if(route.navigationTargetIndices==null)target else (route.targetIndices.lastOrNull{it<target}?.plus(1)?:0).coerceAtMost(target)
    /** 形状点仅推进沿线投影；不发业务ADVANCE、不跨过当前用户目标、不增加会话修订。 */
    private fun trackGeometry(session:NavigationSession,input:NavigationFixSnapshot?,now:Long):NavigationSession {
        if(session.source!=NavigationSource.LOCAL||session.phase!=NavigationPhase.ACTIVE||session.route?.navigationTargetIndices==null||input==null||!input.positionAccepted||now-input.elapsedMillis !in 0..5_000)return session
        val route=session.route?:return session
        var index=session.geometryIndex?:initialGeometry(route,session.targetIndex)
        val capture=max(3.0,min(25.0,input.accuracyMeters?:10.0))
        var steps=0
        while(index<session.targetIndex&&steps++<128){
            val point=route.waypoints[index].point
            val distance=NavigationGeometry.distance(input.point,point)
            val origin=if(index==(session.approachGeometryIndex?:session.approachTargetIndex))session.approachOrigin else route.waypoints.getOrNull(index-1)?.point
            val segment=origin?.let{NavigationGeometry.segment(it,point,input.point)}
            val passed=segment?.let{it.relation==NavigationSegmentRelation.AFTER&&abs(it.signedOffset)<=capture&&distance<=max(100.0,capture*4)}==true
            if(distance>capture&&!passed)break
            index++
        }
        return if(index!=session.geometryIndex)session.copy(geometryIndex=index)else session
    }
    private fun validSettings(value: NavigationSettings) = value.arrivalRadiusMeters.isFinite() && value.arrivalRadiusMeters in 10.0..1_000.0 &&
        value.maximumPositionErrorMeters.isFinite() && value.maximumPositionErrorMeters in 1.0..200.0 &&
        (value.plannedSpeedMetersPerSecond?.let { it.isFinite() && it in .25..60.0 } != false)

    private fun fix(): NavigationFixSnapshot? {
        val vessel = marine.state.value.vesselData
        val position = vessel.position
        val point = position.value ?: return null
        val time = position.receivedElapsedRealtime ?: return null
        return NavigationFixSnapshot(NavigationPoint(point.latitude, point.longitude), time, position.observedAtUtcMillis,
            position.sourceIdentity?.id ?: position.source.name, position.provenance ?: position.source.name,
            position.freshness == VesselDataFreshness.FRESH && position.quality != VesselDataQuality.UNKNOWN && position.source != VesselDataSource.DEMO,
            point.horizontalAccuracyMeters?.takeIf{it.isFinite()&&it>=0},
            vessel.sogKnots.value?.times(.5144444444)?.takeIf { vessel.sogKnots.freshness == VesselDataFreshness.FRESH }, vessel.sogKnots.receivedElapsedRealtime,
            vessel.cogTrueDegrees.value?.takeIf { vessel.cogTrueDegrees.freshness == VesselDataFreshness.FRESH }, vessel.cogTrueDegrees.receivedElapsedRealtime)
    }

    private fun sourceKey(source: VesselSourceIdentity) = listOf(source.transportProfileId ?: source.stableKey, source.transportPeer.orEmpty(), source.talkerId.orEmpty()).joinToString("|")
    private fun externalCandidates() = marine.state.value.vesselData.candidates.filterKeys {
        it in setOf(VesselMetricId.WAYPOINT_BEARING, VesselMetricId.WAYPOINT_DISTANCE, VesselMetricId.DESTINATION_WAYPOINT, VesselMetricId.XTE)
    }.values.flatten().filter { it.source.sourceType == VesselSourceType.NMEA_INPUT }

    /** 只读取同一物理来源、同一测量报文提供的目标经纬度；禁止由距离/方位虚构坐标。 */
    private fun externalTarget(range:VesselSourceCandidate<*>?,name:String?):NavigationPoint? {
        range?:return null
        val source=range.source
        val record=marine.state.value.nmeaFields.firstOrNull {it.connectionId==source.transportProfileId&&it.peer==source.transportPeer&&
            it.key.talker==source.talkerId&&it.key.sentenceType==source.sentenceType&&it.receivedElapsedRealtime==range.receivedElapsedRealtime}?:return null
        val fields=record.rawSentence.trim().substringBefore('*').removePrefix("$").split(',')
        val type=fields.firstOrNull()?.takeLast(3)?:return null
        val indexes=when(type){"RMB"->listOf(6,7,8,9,5);"BWC","BWR"->listOf(2,3,4,5,12);else->return null}
        if(name!=null&&fields.getOrNull(indexes[4])!=name)return null
        fun coordinate(index:Int,side:Int,latitude:Boolean):Double? {
            val raw=fields.getOrNull(index)?.toDoubleOrNull()?.takeIf {it.isFinite()&&it>=0}?:return null
            val degrees=floor(raw/100);val minutes=raw-degrees*100
            if(minutes !in 0.0..<60.0)return null
            val sign=when(fields.getOrNull(side)?.uppercase()){if(latitude)"N"else"E"->1;if(latitude)"S"else"W"-> -1;else->return null}
            return (degrees+minutes/60)*sign
        }
        return NavigationPoint(coordinate(indexes[0],indexes[1],true)?:return null,coordinate(indexes[2],indexes[3],false)?:return null).takeIf {it.valid}
    }

    private fun externalGuidance(session: NavigationSession, now: Long): NavigationGuidance {
        val values = externalCandidates().filter { sourceKey(it.source) == session.externalSourceId }
        fun metric(id: VesselMetricId) = values.filter { it.metric == id && it.validity in setOf(CandidateValidity.ELIGIBLE, CandidateValidity.LOW_QUALITY, CandidateValidity.STALE) && it.quality != VesselDataQuality.UNKNOWN }
            .maxByOrNull { it.receivedElapsedRealtime }
        val destination=metric(VesselMetricId.DESTINATION_WAYPOINT)
        val name=destination?.value as? String
        val previousTarget=externalTargetEpoch
        val targetEpoch=when {previousTarget==null||previousTarget.first!=session.id->0L;previousTarget.second!=name->destination?.receivedElapsedRealtime?:now;else->previousTarget.third}
        externalTargetEpoch=Triple(session.id,name,targetEpoch)
        val range = metric(VesselMetricId.WAYPOINT_DISTANCE)
        val target=externalTarget(range,name)
        val direction = metric(VesselMetricId.WAYPOINT_BEARING)
        val cross = metric(VesselMetricId.XTE)
        fun number(candidate: VesselSourceCandidate<*>?, maxAge: Long = 15_000): Double? = (candidate?.value as? Double)
            ?.takeIf { it.isFinite() && candidate.receivedElapsedRealtime>=targetEpoch && now - candidate.receivedElapsedRealtime in 0..maxAge }
        val distance = number(range,Long.MAX_VALUE)?.takeIf { it >= 0 }?.times(1852)
        // BOD is the fixed origin-to-destination bearing, not boat-to-target guidance.
        val bearing = number(direction)?.takeIf { direction?.let {it.reference == VesselReference.TrueNorth && it.source.sentenceType != "BOD"}==true }
        val own=fix()
        val speed=own?.speedMetersPerSecond?.takeIf {own.speedElapsedMillis?.let {time->now-time in 0..5_000}==true&&it>=.25}
        val course=own?.courseTrueDegrees?.takeIf {own.courseElapsedMillis?.let {time->now-time in 0..5_000}==true}
        val progress=if(speed!=null&&course!=null&&bearing!=null)speed*cos(Math.toRadians(course-bearing))else null
        val etaSpeed=when(session.settings.etaBasis){NavigationEtaBasis.PLAN_SPEED->session.settings.plannedSpeedMetersPerSecond;NavigationEtaBasis.CURRENT_PROGRESS->progress;NavigationEtaBasis.NONE->null}?.takeIf {it>=.25}
        val eta=if(distance!=null&&etaSpeed!=null)(distance/etaSpeed).takeIf {it in 0.0..2_592_000.0}?.let {System.currentTimeMillis()-(now-(range?.receivedElapsedRealtime?:now))+(it*1000).roundToLong()}else null
        val running = session.phase == NavigationPhase.ACTIVE
        val live = running && distance != null && bearing != null && range?.let {now-it.receivedElapsedRealtime in 0..15_000}==true
        val observation = listOfNotNull(range?.receivedElapsedRealtime, direction?.receivedElapsedRealtime).minOrNull()
        return NavigationGuidance(session.id, session.revision, name, name, target,
            distanceMeters = distance.takeIf { running }, remainingMeters=distance.takeIf{running}, bearingTrueDegrees = bearing.takeIf { running },
            etaTargetUtcMillis=eta.takeIf{live},etaRouteUtcMillis=eta.takeIf{live},etaBasis=if(live&&eta!=null)session.settings.etaBasis else NavigationEtaBasis.NONE,progressMetersPerSecond=progress.takeIf{live},
            crossTrackMeters = number(cross)?.times(-1852)?.takeIf { running },
            positionElapsedMillis = observation, positionObservedUtcMillis = range?.observedAtUtcMillis,
            positionSource = values.firstOrNull()?.source?.displayName, live = live,
            bearingElapsedMillis=direction?.receivedElapsedRealtime,distanceElapsedMillis=range?.receivedElapsedRealtime,crossTrackElapsedMillis=cross?.receivedElapsedRealtime,
            progressElapsedMillis=listOfNotNull(direction?.receivedElapsedRealtime,own?.speedElapsedMillis,own?.courseElapsedMillis).maxOrNull(),
            issue = when { session.phase == NavigationPhase.RECOVERY_REQUIRED -> "RESUME_REQUIRED"; !running -> "NAVIGATION_PAUSED"; !live -> "EXTERNAL_TARGET_NOT_CURRENT"; target==null -> "EXTERNAL_TARGET_COORDINATES_NOT_PROVIDED"; else -> null })
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val sources = externalCandidates().groupBy { sourceKey(it.source) }.map { (key, values) ->
            NavigationExternalSource(key, values.first().source.displayName,
                values.any { now - it.receivedElapsedRealtime in 0..15_000 && it.validity == CandidateValidity.ELIGIBLE })
        }.sortedBy { it.id }
        val session = saved.session?.let {trackGeometry(it,fix(),now)}
        if(session!=saved.session)saved=saved.copy(session=session)
        val guidance = session?.takeIf { it.ongoing }?.let {
            if (it.source == NavigationSource.LOCAL) {
                val input=fix()
                val key=GuidanceKey(it.id,it.revision,it.geometryIndex,input,input?.let {v->v.positionAccepted&&now-v.elapsedMillis in 0..10_000}==true,
                    input?.speedElapsedMillis?.let {now-it in 0..5_000}==true,input?.courseElapsedMillis?.let {now-it in 0..5_000}==true)
                if(key!=guidanceKey){
                    val fresh=NavigationGeometry.guidance(it,input,now,System.currentTimeMillis())
                    val previous=cachedGuidance?.takeIf {g->g.sessionId==it.id&&g.sessionRevision==it.revision} ?: it.lastGuidance
                    cachedGuidance=if(!fresh.live&&previous!=null&&previous.targetId==fresh.targetId)fresh.copy(
                        distanceMeters=previous.distanceMeters,remainingMeters=previous.remainingMeters,
                        crossTrackMeters=previous.crossTrackMeters,segmentRelation=previous.segmentRelation,
                        positionObservedUtcMillis=previous.positionObservedUtcMillis,positionElapsedMillis=previous.positionElapsedMillis,
                        distanceElapsedMillis=previous.distanceElapsedMillis,crossTrackElapsedMillis=previous.crossTrackElapsedMillis)else fresh
                    guidanceKey=key
                }
                cachedGuidance
            } else externalGuidance(it, now)
        }
        _state.value = _state.value.copy(session = session, guidance = guidance, externalSources = sources)
        reconcileResources()
    }

    private fun automaticAdvance(): NavigationCommand? {
        if (readBlocked) { auto = null; return null }
        val session = saved.session ?: return null
        if (session.phase != NavigationPhase.ACTIVE || session.source != NavigationSource.LOCAL || session.settings.advanceMode != WaypointAdvanceMode.AUTOMATIC) { auto = null; return null }
        val guidance = _state.value.guidance ?: return null
        val fix = fix() ?: return null
        val now = SystemClock.elapsedRealtime()
        if (!guidance.live || now - fix.elapsedMillis !in 0..5_000 || fix.accuracyMeters?.let { it.isFinite() && it <= min(session.settings.maximumPositionErrorMeters, session.settings.arrivalRadiusMeters / 2) } != true) { auto = null; return null }
        val key = "${session.id}:${session.revision}:${session.targetIndex}"
        val old = auto?.takeIf { it.key == key && it.source == fix.sourceId }
        if (old?.lastElapsed == fix.elapsedMillis) return null
        val radius = session.settings.arrivalRadiusMeters
        val uncertainty=fix.accuracyMeters ?: return null
        val armed = old?.armed == true || (guidance.distanceMeters ?: 0.0) > radius * 1.5
        val along = guidance.alongTrackMeters
        val length = guidance.segmentLengthMeters
        val atBusinessTarget=(session.geometryIndex?:session.targetIndex)==session.targetIndex
        val crossed = atBusinessTarget && (old?.crossed == true || (along != null && length != null && along >= length + uncertainty && old?.lastAlong?.let { it <= length + uncertainty } == true))
        val passed = crossed && along != null && length != null && along >= length + uncertainty &&
            abs(guidance.lineOffsetMeters ?: Double.POSITIVE_INFINITY) + uncertainty <= radius && (guidance.distanceMeters ?: Double.POSITIVE_INFINITY) <= radius * 2
        val arrived=(guidance.distanceMeters ?: Double.POSITIVE_INFINITY)+uncertainty<=radius
        val qualifies = atBusinessTarget && armed && now - lastAdvanceElapsed >= 10_000 && (arrived || passed)
        val since = if (qualifies) old?.since ?: fix.elapsedMillis else null
        val count = if (qualifies) (old?.samples ?: 0) + 1 else 0
        auto = AutoApproach(key, fix.sourceId, fix.elapsedMillis, armed, since, count, guidance.alongTrackMeters.takeIf{atBusinessTarget}, crossed)
        if (since == null || count < 3 || fix.elapsedMillis - since < 3_000) return null
        return NavigationCommand("auto-$key", if (session.targetIndex == session.route!!.targetIndices.last()) NavigationAction.ARRIVE else NavigationAction.ADVANCE,
            session.id, session.revision)
    }

    private fun reconcileForeground() {
        if (saved.session?.phase != NavigationPhase.ACTIVE) {
            context.stopService(Intent(context, NavigationTrackingService::class.java)); return
        }
        runCatching { ContextCompat.startForegroundService(context, Intent(context, NavigationTrackingService::class.java)) }
            .onFailure { _state.value = _state.value.copy(backgroundIssue = "ANDROID_BACKGROUND_START_DENIED") }
    }
    internal fun foreground(active: Boolean, issue: String? = null, location: Boolean = false) { scope.launch { guard.withLock {
        locationAllowed = active && location
        _state.value = _state.value.copy(backgroundActive = active, backgroundIssue = issue)
        reconcileResources()
    } } }
    internal fun activeSession() = state.value.session?.phase == NavigationPhase.ACTIVE
    internal fun chinese() = marine.state.value.settings.appLanguage == com.yokuli.anchorwatch.domain.model.AppLanguage.SIMPLIFIED_CHINESE
    internal fun needsPhoneLocation() = marine.state.value.settings.gpsDataSource == GpsDataSource.SYSTEM
    private fun reconcileResources() {
        val requirement = if (saved.session?.phase == NavigationPhase.ACTIVE && _state.value.backgroundActive) RuntimeRequirement(
            needsSystemLocation = needsPhoneLocation() && locationAllowed, needsNmeaTransport = marine.state.value.settings.gpsDataSource == GpsDataSource.NMEA || saved.session?.source == NavigationSource.EXTERNAL_NMEA,
            needsWakeLock = true, needsWifiLock = saved.session?.source == NavigationSource.EXTERNAL_NMEA || marine.state.value.settings.gpsDataSource == GpsDataSource.NMEA) else null
        if (resource != requirement) {
            runCatching { resources.set(RuntimeOwner.NAVIGATION_SESSION, requirement) }
                .onSuccess { resource = requirement }
                .onFailure { _state.value = _state.value.copy(backgroundIssue = "NAVIGATION_RESOURCES_UNAVAILABLE") }
        }
    }
}

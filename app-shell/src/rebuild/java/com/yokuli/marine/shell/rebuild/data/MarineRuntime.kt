package com.yokuli.marine.shell.rebuild.data

import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.MainViewModel
import com.yokuli.anchorwatch.data.nmea.ConnectionProfile
import com.yokuli.anchorwatch.data.nmea.Protocol
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.NavigationFix
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.marine.shell.rebuild.NoticeSeverity
import com.yokuli.anchorwatch.runtime.RuntimeFeedbackContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** 航行是进程级系统会话；海图、日志、状态栏和磁贴读取同一个状态。 */
enum class VoyagePhase { IDLE, STARTING, RECORDING, PAUSED, SAVING }
data class VoyageSessionState(
    /** 持久化航行 ID；尚未开始时为空。 */
    val id:Long?=null,
    val phase:VoyagePhase=VoyagePhase.IDLE,
    val name:String="",
    /** 内部距离一律为米，界面统一交给系统单位格式化。 */
    val distanceMeters:Double=0.0,
    val startedAt:Long?=null,
    val pausedAt:Long?=null,
    val accumulatedPausedMillis:Long=0L,
    val momentCount:Int=0,
    /** 等待数据库确认时禁止重复发送开始/停止命令。 */
    val commandPending:Boolean=false,
) {
    val active:Boolean get()=id!=null
    fun elapsedMillis(now:Long)=startedAt?.let{((pausedAt?:now)-it-accumulatedPausedMillis).coerceAtLeast(0)}?:0L
}

/** Shell subscribes to Boat Watch's existing process-wide engine. It owns no sockets or GPS. */
class MarineRuntime(private val os: OsStore, val vm: MainViewModel) {
    private val _voyage=MutableStateFlow(VoyageSessionState())
    val voyage=_voyage.asStateFlow()
    private var pendingCommand:String?=null
    private val deliveredFeedback=linkedSetOf<Long>()
    private var previousTrack: com.yokuli.anchorwatch.data.trip.TripTrackSnapshot? = null
    private var previousTripId: Long? = null
    private val subscription: Job = os.scope.launch {
        vm.ui.collect { state ->
            if (state.settingsReady) {
                os.positionSource = when (state.settings.gpsDataSource) {
                    GpsDataSource.SYSTEM -> "phone"
                    GpsDataSource.NMEA -> "nmea"
                    GpsDataSource.DEMO -> "demo"
                    else -> "none"
                }
            }
            publish(state)
            publishFeedback(state)
        }
    }

    /** 反馈订阅与应用页面生命周期无关，短时间多个运行时事件逐条进入通知中心。 */
    private fun publishFeedback(state:MainUiState) {
        state.runtimeDiagnostics.pendingUserFeedback.sortedBy{it.id}.forEach { feedback ->
            if(deliveredFeedback.add(feedback.id)&&feedback.context!=RuntimeFeedbackContext.POSITION_STATUS) {
                val title=feedback.englishTitle.lowercase()
                val app=when {
                    feedback.context in setOf(RuntimeFeedbackContext.ARM_WATCH,RuntimeFeedbackContext.DEPTH_DATA_UNAVAILABLE,RuntimeFeedbackContext.WIND_DATA_UNAVAILABLE)->AppId.ANCHOR
                    title.startsWith("trip")||title.startsWith("recording")||title=="waypoint not saved"->AppId.VOYAGES
                    title.startsWith("nmea")||title.startsWith("phone/app")||title.startsWith("phone sensor output")||title.startsWith("phone vessel output")->AppId.NMEA
                    title.startsWith("approach")->AppId.PLACES
                    title.startsWith("anchor")||title.startsWith("alarm")||title.startsWith("safety")||title.startsWith("wind")||title.startsWith("high wind")||title.startsWith("condition")||title.startsWith("required nmea instrument")->AppId.ANCHOR
                    else->null
                }
                os.notify("${feedback.chineseTitle} · ${feedback.chineseMessage}","${feedback.englishTitle} · ${feedback.englishMessage}",app=app,
                    severity=if(feedback.highPriority)NoticeSeverity.WARNING else NoticeSeverity.INFO,
                    key="runtime:${feedback.context}:${feedback.englishTitle}")
            }
            vm.consumeRuntimeFeedback(feedback.id)
        }
        // 防止异步 UI 状态在消费确认前再次到达时重复显示；已消费旧 ID 不需无限保留。
        if(deliveredFeedback.size>512)deliveredFeedback.toList().take(256).forEach(deliveredFeedback::remove)
    }

    fun close() = subscription.cancel()

    private fun NavigationFix.asFix(source: String, selected:VesselDataSnapshot?=null): Fix? {
        if (!valid || (isMockLocation && source != "demo")) return null
        val composed=selected?.let{VesselFixProjection.compose(this,it)}?:this
        return Fix(GeoPoint(latitude, longitude), source, receivedElapsedRealtime,
            timestampUtcMillis ?: 0L, composed.sogKnots, composed.cogTrueDegrees, horizontalAccuracyMeters,
            composed.sogReceivedElapsedRealtime ?: receivedElapsedRealtime,
            composed.cogReceivedElapsedRealtime ?: receivedElapsedRealtime,
            selected?.headingTrueDegrees?.value,selected?.headingTrueDegrees?.receivedElapsedRealtime,
            selected?.headingTrueDegrees?.freshness ?: VesselDataFreshness.UNAVAILABLE,
            selected?.sogKnots?.freshness ?: VesselDataFreshness.UNAVAILABLE,
            selected?.cogTrueDegrees?.freshness ?: VesselDataFreshness.UNAVAILABLE)
    }

    private fun publish(state: MainUiState) {
        val trip=state.activeTrip
        _voyage.value=VoyageSessionState(trip?.id,when{pendingCommand=="start"->VoyagePhase.STARTING;pendingCommand=="finish"->VoyagePhase.SAVING;trip==null->VoyagePhase.IDLE;trip.paused->VoyagePhase.PAUSED;else->VoyagePhase.RECORDING},trip?.name.orEmpty(),trip?.distanceMeters?:0.0,trip?.startedAt,trip?.pausedAt,trip?.accumulatedPausedMillis?:0L,trip?.waypointCount?:0,pendingCommand!=null)
        os.recordingActive = state.activeTrip != null
        os.recordingPaused = state.activeTrip?.paused == true
        if(previousTrack !== state.tripTrack || previousTripId != state.activeTrip?.id) {
            previousTrack=state.tripTrack;previousTripId=state.activeTrip?.id
            os.recordedSegments = if (state.activeTrip != null) state.tripTrack.rendered(3000).map { segment ->
                segment.points.mapNotNull { point ->
                    if (point.hasPosition) GeoPoint(point.latitude!!, point.longitude!!) else null
                }
            } else emptyList()
        }
        val selected = state.settings.gpsDataSource
        val accepted = state.acceptedPosition.takeIf { it.selectedSource == selected }?.acceptedFix
        val readings = buildMap {
            fun add(key:String,value:VesselObservation<Double>,unit:String,metric:VesselMetricId) {
                val number=value.value?.takeIf{it.isFinite()}?:return
                val time=value.receivedElapsedRealtime?:return
                put(key,Reading(number,unit,value.provenance?:value.source.name,time,value.freshness,value.quality,
                    value.sourceIdentity?.id?:value.provenanceDetail?.toString()?:value.source.name,
                    MetricSourceEligibility.measurementLeaseMillis(metric)))
            }
            with(state.vesselData) {
                add("sog",sogKnots,"kn",VesselMetricId.SOG);add("cog",cogTrueDegrees,"°T",VesselMetricId.COG)
                add("heading",headingTrueDegrees,"°T",VesselMetricId.HEADING_TRUE)
                add("depth",depthMeters,"m",VesselMetricId.DEPTH);add("ukc",derived.underKeelClearanceMeters,"m",VesselMetricId.DEPTH)
                add("aws",apparentWind.speedKnots,"kn",VesselMetricId.APPARENT_WIND_SPEED)
                add("awa",apparentWind.angleDegrees,"°",VesselMetricId.APPARENT_WIND_ANGLE)
                add("tws",trueWind.speedKnots,"kn",VesselMetricId.TRUE_WIND_SPEED)
                add("twa",trueWind.angleDegrees,"°",VesselMetricId.TRUE_WIND_ANGLE)
                add("twd",trueWind.directionDegrees,"°T",VesselMetricId.TRUE_WIND_DIRECTION)
                add("bsp",speedThroughWaterKnots,"kn",VesselMetricId.SPEED_THROUGH_WATER)
                add("water",waterTemperatureCelsius,"°C",VesselMetricId.WATER_TEMPERATURE)
                add("air",airTemperatureCelsius,"°C",VesselMetricId.AIR_TEMPERATURE)
                add("pressure",pressureHpa,"hPa",VesselMetricId.PRESSURE)
                add("pressure_1h",derived.pressureTrend1hHpa,"hPa",VesselMetricId.PRESSURE)
                add("pressure_3h",derived.pressureTrend3hHpa,"hPa",VesselMetricId.PRESSURE)
                add("pressure_6h",derived.pressureTrend6hHpa,"hPa",VesselMetricId.PRESSURE)
                add("heel",heelDegrees,"°",VesselMetricId.HEEL);add("pitch",pitchDegrees,"°",VesselMetricId.PITCH)
                add("roll_rate",rollRateDegreesPerSecond,"°/s",VesselMetricId.ROLL_RATE)
                add("pitch_rate",pitchRateDegreesPerSecond,"°/s",VesselMetricId.PITCH_RATE)
                add("rot",rateOfTurnDegreesPerMinute,"°/min",VesselMetricId.RATE_OF_TURN)
                add("rudder",rudderAngleDegrees,"°",VesselMetricId.RUDDER_ANGLE)
                add("vmg",derived.vmgToWindKnots,"kn",VesselMetricId.VMG_WIND)
                add("vmc",derived.vmcToWaypointKnots,"kn",VesselMetricId.VMC_WAYPOINT)
                add("current_set",currentSetTrueDegrees,"°T",VesselMetricId.CURRENT_SET)
                add("current_drift",currentDriftKnots,"kn",VesselMetricId.CURRENT_DRIFT)
                add("xte",crossTrackErrorNauticalMiles,"nm",VesselMetricId.XTE)
                add("waypoint_bearing",waypointBearingTrueDegrees,"°T",VesselMetricId.WAYPOINT_BEARING)
                add("waypoint_distance",waypointDistanceNauticalMiles,"nm",VesselMetricId.WAYPOINT_DISTANCE)
                add("total_log",totalLogNauticalMiles,"nm",VesselMetricId.TOTAL_LOG)
                add("trip_log",tripLogNauticalMiles,"nm",VesselMetricId.TRIP_LOG)
                fun motionReading(value:Double?)=VesselObservation(value,motion.source,motion.observedAtUtcMillis,motion.receivedElapsedRealtime,motion.quality,motion.freshness,motion.provenance,motion.sourceIdentity,motion.sourceClass,provenanceDetail=motion.provenanceDetail)
                add("roll_period",motionReading(motion.value?.dominantRollPeriodSeconds),"s",VesselMetricId.ROLL_PERIOD)
                add("motion",motionReading(motion.value?.score),"",VesselMetricId.MOTION_SCORE)
                add("impacts",motionReading(motion.value?.impactCandidateCount?.toDouble()),"",VesselMetricId.MOTION_SCORE)
            }
        }
        os.hub.update {
            it.copy(phone = (if(selected==GpsDataSource.SYSTEM) accepted else state.systemFix)?.asFix("phone",state.vesselData.takeIf{selected==GpsDataSource.SYSTEM}),
                nmea = (if(selected==GpsDataSource.NMEA) accepted else state.nmeaFix)?.asFix("NMEA",state.vesselData.takeIf{selected==GpsDataSource.NMEA}),
                demo = if(selected==GpsDataSource.DEMO) accepted?.asFix("demo",state.vesselData) else null,
                readings = readings, gpsOn = state.settings.gpsDataSource == GpsDataSource.SYSTEM,
                connection = when(state.connection) {
                    NmeaConnectionState.DISCONNECTED -> "off"
                    NmeaConnectionState.CONNECTING -> "connecting"
                    NmeaConnectionState.RECONNECTING -> "reconnecting"
                    NmeaConnectionState.ERROR -> "error"
                    NmeaConnectionState.CONNECTED_NO_DATA -> "waiting"
                    else -> "live"
                }, endpoint = "${state.settings.profile.protocol} ${state.settings.profile.host}:${state.settings.profile.port}",
                received = state.diagnostics.validSentences, rejected = state.diagnostics.invalidSentences,
                raw = state.diagnostics.raw, lastRx = state.diagnostics.lastPacketElapsed ?: 0L,
                server = state.nmeaSharing.state.name.lowercase(), serverPort = state.nmeaSharing.port,
                clients = state.nmeaSharing.clientCount, addresses = state.nmeaSharing.addresses,
                transmitted = state.nmeaSharing.sentSentences,
                upstreamPublishing = state.outputSettings.publicationEnabled)
        }
    }

    fun syncLanguage() {
        val current = vm.ui.value
        val language = if(os.chinese) AppLanguage.SIMPLIFIED_CHINESE else AppLanguage.ENGLISH
        if(current.settingsReady && current.settings.appLanguage != language)
            vm.updateSettings(current.settings.copy(appLanguage = language))
    }

    fun action(action: String, extra: String? = null) {
        when(action) {
            "gpsOn" -> { vm.onPermissionsChanged(); vm.switchGpsDataSource(GpsDataSource.SYSTEM) }
            "gpsOff", "sourceOff" -> vm.switchGpsDataSource(GpsDataSource.NONE)
            "sourceNmea" -> vm.switchGpsDataSource(GpsDataSource.NMEA)
            "connect" -> vm.saveAndConnect(ConnectionProfile(name="Boat",
                protocol=if(os.nmeaProtocol=="UDP") Protocol.UDP else Protocol.TCP,
                host=os.nmeaHost.trim(),port=os.nmeaPort.toIntOrNull() ?: 0,autoReconnect=true))
            "disconnect" -> vm.disconnect()
            "shareOn" -> vm.setNmeaSharing(true,os.serverPort.toIntOrNull() ?: 10111)
            "shareOff" -> vm.stopLocalNmeaServer()
            "stopAll" -> vm.stopAllNmeaSharing()
            "tripPause" -> pauseRecording()
            "tripResume" -> resumeRecording()
            "tripEnd" -> finishRecording()
            else -> os.open("nmea")
        }
    }

    fun startRecording(name: String, motion: Boolean = false) {
        val state=vm.ui.value
        if(state.activeTrip!=null||pendingCommand!=null)return
        if(os.positionSource !in listOf("phone","nmea")) {
            os.notify("请先开启一个船位来源", "Enable a position source first")
            return
        }
        command("start",{vm.startTrip(name,motion,if(os.positionSource=="nmea")VesselSourcePreference.BOAT else VesselSourcePreference.PHONE)},{it.activeTrip!=null},"航行记录已开始","Voyage recording started")
    }

    fun pauseRecording(){val id=vm.ui.value.activeTrip?.takeIf{!it.paused}?.id?:return;command("pause",{vm.pauseTrip()},{it.activeTrip?.let{trip->trip.id==id&&trip.paused}==true},"航行记录已暂停","Voyage recording paused")}
    fun resumeRecording(){val id=vm.ui.value.activeTrip?.takeIf{it.paused}?.id?:return;command("resume",{vm.resumeTrip()},{it.activeTrip?.let{trip->trip.id==id&&!trip.paused}==true},"航行记录已继续","Voyage recording resumed")}
    fun finishRecording(){val id=vm.ui.value.activeTrip?.id?:return;command("finish",{vm.endTrip()},{it.tripSessions.any{trip->trip.id==id&&!trip.active&&trip.endedAt!=null}},"航行记录已保存","Voyage recording saved")}

    private fun command(kind:String,send:()->Unit,ack:(MainUiState)->Boolean,zh:String,en:String){
        if(pendingCommand!=null)return
        pendingCommand=kind;publish(vm.ui.value)
        os.scope.launch{
            try{
                send()
                val confirmed=withTimeoutOrNull(18_000){vm.ui.first(ack)}
                if(confirmed!=null)os.notify(zh,en,app=AppId.VOYAGES)
                else os.notify("操作尚未完成，请查看航行日志中的状态","Operation is not confirmed yet; check its status in Logbook",app=AppId.VOYAGES)
            }finally{pendingCommand=null;publish(vm.ui.value)}
        }
    }
}

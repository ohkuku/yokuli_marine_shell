package com.yokuli.marine.shell.rebuild.data

import com.yokuli.runtime.contract.*

import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.api.MarineServices
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.NavigationFix
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.marine.shell.rebuild.NoticeSeverity
import com.yokuli.anchorwatch.runtime.RuntimeFeedbackContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shell 的显示投影：订阅唯一运行时，格式化读数/通知；不持有设备、会话或业务执行范围。 */
class MarinePresentationBridge(private val os: OsStore, val system: com.yokuli.runtime.marine.MarineSystem) {
    val services: MarineServices = system.services
    val voyage = system.voyage.state
    private val deliveredFeedback=linkedSetOf<Long>()
    private var previousTrack: com.yokuli.anchorwatch.data.trip.TripTrackSnapshot? = null
    private var previousTripId: Long? = null
    private val subscription: Job = os.scope.launch {
        services.state.collect { state ->
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
            services.feedback.consumeRuntimeFeedback(feedback.id)
        }
        // 防止异步 UI 状态在消费确认前再次到达时重复显示；已消费旧 ID 不需无限保留。
        if(deliveredFeedback.size>512)deliveredFeedback.toList().take(256).forEach(deliveredFeedback::remove)
    }

    fun close() { subscription.cancel(); commandFeedback.cancel() }

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
                    MetricSourceEligibility.measurementLeaseMillis(metric),
                    // 基准/校准变化不改变来源计数，但须在历史中断开；吃水改变也不能伪装成海底骤变。
                    "${value.sourceIdentity?.id ?: value.source.name}|${value.reference}|${value.provenanceDetail}" +
                        if (key == "ukc") "|draft:${state.vesselSettings.draftMeters}" else ""))
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
        val current = services.state.value
        val language = if(os.chinese) AppLanguage.SIMPLIFIED_CHINESE else AppLanguage.ENGLISH
        if(current.settingsReady && current.settings.appLanguage != language)
            services.preferences.setLanguage(language)
    }

    // Commands are owned by the system coordinator, never by a page or this read projection.
    fun startRecording(name: String, motion: Boolean = false) = system.voyage.start(name, motion)
    fun pauseRecording() = system.voyage.pause()
    fun resumeRecording() = system.voyage.resume()
    fun finishRecording() = system.voyage.finish()

    private val commandFeedback = os.scope.launch {
        system.voyage.events.collect { event ->
            val message = when(event.status) {
                VoyageCommandStatus.POSITION_REQUIRED -> "请先开启一个船位来源" to "Enable a position source first"
                VoyageCommandStatus.NOT_CONFIRMED -> "操作尚未完成，请查看航行日志中的状态" to "Operation is not confirmed yet; check its status in Logbook"
                VoyageCommandStatus.FAILED -> "航行操作未完成，请查看当前状态" to "Voyage action failed; check the current state"
                VoyageCommandStatus.CONFIRMED -> when(event.action) {
                    VoyageAction.START -> "航行记录已开始" to "Voyage recording started"
                    VoyageAction.PAUSE -> "航行记录已暂停" to "Voyage recording paused"
                    VoyageAction.RESUME -> "航行记录已继续" to "Voyage recording resumed"
                    VoyageAction.FINISH -> "航行记录已保存" to "Voyage recording saved"
                }
            }
            os.notify(message.first, message.second, app = AppId.VOYAGES, destination = "voyages")
        }
    }
}

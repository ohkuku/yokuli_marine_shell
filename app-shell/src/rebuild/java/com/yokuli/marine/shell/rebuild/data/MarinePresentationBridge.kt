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
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Shell 的显示投影：订阅唯一运行时，格式化读数/通知；不持有设备、会话或业务执行范围。 */
class MarinePresentationBridge(private val os: OsStore, val system: com.yokuli.runtime.marine.MarineSystem) {
    val services: MarineServices = system.services
    val voyage = system.voyage.state
    private data class ShellProjection(val ready:Boolean,val source:GpsDataSource,val recording:Boolean,val paused:Boolean)
    init { os.hub.bind(system.readingHistory,os.scope) }
    private val subscriptions=listOf(
        os.scope.launch {
            services.state.map { ShellProjection(it.settingsReady,it.settings.gpsDataSource,it.activeTrip!=null,it.activeTrip?.paused==true) }
                .distinctUntilChanged().collect { state ->
                    if(state.ready)os.positionSource=when(state.source) {
                        GpsDataSource.SYSTEM->"phone";GpsDataSource.NMEA->"nmea";GpsDataSource.DEMO->"demo";else->"none"
                    }
                    os.recordingActive=state.recording
                    os.recordingPaused=state.paused
                }
        },
        os.scope.launch {
            // 地理轨迹整理属于读模型计算；主线程只接收结果，不随网络计数重算 3000 个点。
            services.state.map { it.activeTrip?.id to it.tripTrack }.distinctUntilChanged()
                .map { (tripId,track) ->
                    if(tripId==null)emptyList() else track.rendered(3000).map { segment ->
                        segment.points.mapNotNull { point ->
                            if(point.hasPosition)GeoPoint(point.latitude!!,point.longitude!!)else null
                        }
                    }
                }.flowOn(Dispatchers.Default).collect { os.recordedSegments=it }
        },
        os.scope.launch(Dispatchers.Default) {
            services.state.map(::projectTransport).distinctUntilChanged().collect { projection ->
                os.hub.update { projection.copy(readings=it.readings,message=it.message,sentToInput=it.sentToInput) }
            }
        },
    )

    fun close() { subscriptions.forEach { it.cancel() } }

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

    private fun projectTransport(state:MainUiState):VesselData {
        val selected=state.settings.gpsDataSource
        val accepted=state.acceptedPosition.takeIf {it.selectedSource==selected}?.acceptedFix
        return VesselData(phone = (if(selected==GpsDataSource.SYSTEM) accepted else state.systemFix)?.asFix("phone",state.vesselData.takeIf{selected==GpsDataSource.SYSTEM}),
                nmea = (if(selected==GpsDataSource.NMEA) accepted else state.nmeaFix)?.asFix("NMEA",state.vesselData.takeIf{selected==GpsDataSource.NMEA}),
                demo = if(selected==GpsDataSource.DEMO) accepted?.asFix("demo",state.vesselData) else null,
                gpsOn = state.settings.gpsDataSource == GpsDataSource.SYSTEM,
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

}

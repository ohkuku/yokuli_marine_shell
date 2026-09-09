package com.yokuli.marine.shell.rebuild.data

import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.MainViewModel
import com.yokuli.anchorwatch.data.nmea.ConnectionProfile
import com.yokuli.anchorwatch.data.nmea.Protocol
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.NavigationFix
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shell subscribes to Boat Watch's existing process-wide engine. It owns no sockets or GPS. */
class MarineRuntime(private val os: OsStore, val vm: MainViewModel) {
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
        }
    }

    fun close() = subscription.cancel()

    private fun NavigationFix.asFix(source: String): Fix? {
        if (!valid || (isMockLocation && source != "demo")) return null
        return Fix(GeoPoint(latitude, longitude), source, receivedElapsedRealtime,
            timestampUtcMillis ?: 0L, sogKnots, cogTrueDegrees, horizontalAccuracyMeters,
            sogReceivedElapsedRealtime ?: receivedElapsedRealtime,
            cogReceivedElapsedRealtime ?: receivedElapsedRealtime)
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
            fun add(key: String, value: VesselObservation<Double>, unit: String) {
                val number = value.value?.takeIf { it.isFinite() } ?: return
                val time = value.receivedElapsedRealtime ?: return
                put(key, Reading(number, unit, value.provenance ?: value.source.name, time))
            }
            with(state.vesselData) {
                add("heading", headingTrueDegrees, "°T")
                add("depth", depthMeters, "m")
                add("aws", apparentWind.speedKnots, "kn")
                add("awa", apparentWind.angleDegrees, "°")
                add("tws", trueWind.speedKnots, "kn")
                add("bsp", speedThroughWaterKnots, "kn")
                add("water", waterTemperatureCelsius, "°C")
                add("pressure", pressureHpa, "hPa")
                add("ukc", derived.underKeelClearanceMeters, "m")
            }
            accepted?.asFix(if(selected==GpsDataSource.SYSTEM) "phone GPS" else if(selected==GpsDataSource.DEMO) "DEMO" else "NMEA")?.let { fix ->
                fix.speed?.let { put("sog",Reading(it,"kn",fix.source,fix.speedElapsed)) }
                fix.course?.let { put("cog",Reading(it,"°T",fix.source,fix.courseElapsed)) }
            }
        }
        os.hub.update {
            it.copy(phone = (if(selected==GpsDataSource.SYSTEM) accepted else state.systemFix)?.asFix("phone"),
                nmea = (if(selected==GpsDataSource.NMEA) accepted else state.nmeaFix)?.asFix("NMEA"),
                demo = if(selected==GpsDataSource.DEMO) accepted?.asFix("demo") else null,
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
            "tripPause" -> vm.pauseTrip()
            "tripResume" -> vm.resumeTrip()
            "tripEnd" -> vm.endTrip()
            else -> os.open("nmea")
        }
    }

    fun startRecording(name: String, motion: Boolean = false) {
        val state=vm.ui.value
        if(os.positionSource !in listOf("phone","nmea")) {
            os.notify("请先开启一个船位来源", "Enable a position source first")
            os.open("settings:sources"); return
        }
        vm.startTrip(name, motion, if(os.positionSource=="nmea") VesselSourcePreference.BOAT else VesselSourcePreference.PHONE)
    }
}

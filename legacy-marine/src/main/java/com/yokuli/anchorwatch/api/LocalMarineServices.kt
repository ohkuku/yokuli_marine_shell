package com.yokuli.anchorwatch.api

import android.content.ComponentName
import android.net.Uri
import com.yokuli.anchorwatch.AnchorSetupDraft
import com.yokuli.anchorwatch.AnchorWatchInput
import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.data.database.AnchorSessionEntity
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import com.yokuli.anchorwatch.data.database.TripWaypointEntity
import com.yokuli.anchorwatch.data.nmea.ConnectionProfile
import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSnapshot
import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSpec
import com.yokuli.anchorwatch.data.nmea.NmeaFeed
import com.yokuli.anchorwatch.data.trip.TripMapData
import com.yokuli.anchorwatch.data.trip.TripReplayData
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.domain.model.AlarmSound
import com.yokuli.anchorwatch.domain.vessel.InstrumentTileId
import com.yokuli.anchorwatch.domain.condition.ConditionGuardConfig
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.MarineDestination
import com.yokuli.anchorwatch.domain.report.TripReport
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import com.yokuli.anchorwatch.location.PhoneLocationStatus
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.yokuli.anchorwatch.LegacyMarineController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 唯一的进程内实现：窄端口委托同一个应用级 Controller，不另建来源、会话或仓库。
 * Job/同步调用表示原操作执行通道，不伪造领域确认；运行结果仍以 state 的真实状态为准。
 * 这里是旧后端适配边界，不是 Binder 服务，也不声明 APK/ROM 已有进程隔离。
 */
@Singleton
class LocalMarineServices @Inject constructor(
    private val controller: LegacyMarineController,
    contentService: LocalMarineContentService,
) : MarineServices {
    override val state: StateFlow<MainUiState> get() = controller.ui
    override val content: MarineContentService = contentService

    override val sources: DataSourceService = object : DataSourceService {
        override val state: StateFlow<MainUiState> get() = controller.ui
        override val phoneLocationStatus: StateFlow<PhoneLocationStatus> get() = controller.phoneLocationStatus
        override fun onPermissionsChanged(): Unit = controller.onPermissionsChanged()
        override fun switchGpsDataSource(source: GpsDataSource): Job = controller.switchGpsDataSource(source)
        override fun selectNmeaPositionConnection(id: String): Unit = controller.selectNmeaPositionConnection(id)
        override fun setVesselMetricSource(metric: VesselMetricId, sourceId: String?): Job = controller.setVesselMetricSource(metric, sourceId)
        override fun confirmTripAttitudeFrame(axis: DeviceBowAxis): Job = controller.confirmTripAttitudeFrame(axis)
        override fun alignPhoneHeadingToBow(): Job = controller.alignPhoneHeadingToBow()
        override fun alignPhoneHeadingToNmea(): Job = controller.alignPhoneHeadingToNmea()
        override fun clearVesselCalibrationFeedback(): Unit = controller.clearVesselCalibrationFeedback()
    }

    override val voyages: VoyageService = object : VoyageService {
        override val state: StateFlow<MainUiState> get() = controller.ui
        override fun startTrip(name: String, phoneMotionEnabled: Boolean, positionPreference: VesselSourcePreference): Job = controller.startTrip(name, phoneMotionEnabled, positionPreference)
        override fun pauseTrip(): ComponentName? = controller.pauseTrip()
        override fun resumeTrip(): Unit = controller.resumeTrip()
        override fun pauseTripAttitude(): Job = controller.pauseTripAttitude()
        override fun endTrip(): ComponentName? = controller.endTrip()
        override fun markTripWaypoint(name: String, note: String, type: String): Unit = controller.markTripWaypoint(name, note, type)
        override fun deleteTrip(session: TripSessionEntity): Unit = controller.deleteTrip(session)
        override fun renameTrip(id: Long, name: String): Job = controller.renameTrip(id, name)
        override fun editTripMoment(value: TripWaypointEntity, name: String, note: String): Job = controller.editTripMoment(value, name, note)
        override suspend fun tripReport(sessionId: Long): TripReport? = controller.tripReport(sessionId)
        override suspend fun tripReplay(sessionId: Long): TripReplayData = controller.tripReplay(sessionId)
        override suspend fun tripMapData(sessionId: Long, pointBudget: Int): TripMapData = controller.tripMapData(sessionId, pointBudget)
        override fun exportTripCsv(session: TripSessionEntity): Job = controller.exportTripCsv(session)
        override fun exportTripGpx(session: TripSessionEntity): Job = controller.exportTripGpx(session)
        override fun exportTripKml(session: TripSessionEntity): Job = controller.exportTripKml(session)
        override fun exportTripKmz(session: TripSessionEntity): Job = controller.exportTripKmz(session)
        override fun exportTripEvents(session: TripSessionEntity): Job = controller.exportTripEvents(session)
        override fun exportTripWaypoints(session: TripSessionEntity): Job = controller.exportTripWaypoints(session)
        override fun exportTripCustomMetrics(session: TripSessionEntity): Job = controller.exportTripCustomMetrics(session)
        override fun shareTripReportSnapshot(session: TripSessionEntity): Job = controller.shareTripReportSnapshot(session)
        override fun exportTripAiSource(session: TripSessionEntity): Job = controller.exportTripAiSource(session)
    }

    override val anchor: AnchorService = object : AnchorService {
        override val state: StateFlow<MainUiState> get() = controller.ui
        override fun saveAnchorSetupDraft(value: AnchorSetupDraft): Unit = controller.saveAnchorSetupDraft(value)
        override fun clearAnchorSetupDraft(): Unit = controller.clearAnchorSetupDraft()
        override fun arm(lat: Double, lon: Double, input: AnchorWatchInput): Unit = controller.arm(lat, lon, input)
        override fun requestArm(lat: Double, lon: Double, input: AnchorWatchInput): String = controller.requestArm(lat, lon, input)
        override fun requestPauseWatch(sessionId: Long): String = controller.requestPauseWatch(sessionId)
        override fun requestResumeWatch(sessionId: Long): String = controller.requestResumeWatch(sessionId)
        override fun requestLiftAnchor(sessionId: Long): String = controller.requestLiftAnchor(sessionId)
        override fun updateAnchorSettings(input: AnchorWatchInput): Unit = controller.updateAnchorSettings(input)
        override fun updateConditionGuards(config: ConditionGuardConfig): Unit = controller.updateConditionGuards(config)
        override fun pauseWatch(): ComponentName? = controller.pauseWatch()
        override fun resumeWatch(): Unit = controller.resumeWatch()
        override fun liftAnchor(): Unit = controller.liftAnchor()
        override fun acknowledge(): ComponentName? = controller.acknowledge()
        override fun keepCurrentCenter(session: AnchorSessionEntity): Unit? = controller.keepCurrentCenter(session)
        override fun continueEstimatingCenter(session: AnchorSessionEntity): Unit? = controller.continueEstimatingCenter(session)
        override fun recalculateCentreFromTrack(session: AnchorSessionEntity): Job = controller.recalculateCentreFromTrack(session)
        override fun keepCurrentRecalculatedCentre(): Unit = controller.keepCurrentRecalculatedCentre()
        override fun acceptEstimatedCenter(session: AnchorSessionEntity): Unit? = controller.acceptEstimatedCenter(session)
        override fun applyRecalculatedCentre(): Unit = controller.applyRecalculatedCentre()
        override fun loadHistoryEvents(sessionId: Long): Job = controller.loadHistoryEvents(sessionId)
        override fun exportCsv(session: AnchorSessionEntity): Job = controller.exportCsv(session)
        override fun exportGpx(session: AnchorSessionEntity): Job = controller.exportGpx(session)
    }

    override val network: NetworkService = object : NetworkService {
        override val state: StateFlow<MainUiState> get() = controller.ui
        override val connections: StateFlow<List<NmeaConnectionSnapshot>> get() = controller.nmeaConnections
        override fun saveNmeaConnection(spec: NmeaConnectionSpec, onSaved: () -> Unit): Job = controller.saveNmeaConnection(spec, onSaved)
        override fun startNmeaConnection(id: String): Job = controller.startNmeaConnection(id)
        override fun stopNmeaConnection(id: String): Job = controller.stopNmeaConnection(id)
        override fun removeNmeaConnection(id: String): Job = controller.removeNmeaConnection(id)
        override fun saveAndConnect(profile: ConnectionProfile): Job = controller.saveAndConnect(profile)
        override fun disconnect(): Unit = controller.disconnect()
    }

    override val sharing: SharingService = object : SharingService {
        override val state: StateFlow<MainUiState> get() = controller.ui
        override fun setNmeaSharing(enabled: Boolean, port: Int): Unit = controller.setNmeaSharing(enabled, port)
        override fun saveLocalNmeaPublicationPolicy(port: Int, feed: NmeaFeed, capabilities: Set<String>, forwardFrom: Set<String>): Job = controller.saveLocalNmeaPublicationPolicy(port, feed, capabilities, forwardFrom)
        override fun startLocalNmeaServer(): Job = controller.startLocalNmeaServer()
        override fun stopLocalNmeaServer(): Job = controller.stopLocalNmeaServer()
        override fun stopAllNmeaSharing(): Job = controller.stopAllNmeaSharing()
    }

    override val preferences: VesselPreferencesService = object : VesselPreferencesService {
        override val state: StateFlow<MainUiState> get() = controller.ui
        override fun setLanguage(language: AppLanguage): Job = controller.setLanguage(language)
        override fun setVesselGeometry(lengthMeters: Double, bowRollerHeightMeters: Double, antennaToBowMeters: Double): Job =
            controller.setVesselGeometry(lengthMeters, bowRollerHeightMeters, antennaToBowMeters)
        override fun setVesselIdentity(name: String, draftMeters: Double?): Job = controller.setVesselIdentity(name, draftMeters)
        override fun setAlarmSound(sound: AlarmSound, customUri: String?): Job = controller.setAlarmSound(sound, customUri)
        override fun setAlarmSnoozeMinutes(minutes: Int): Job = controller.setAlarmSnoozeMinutes(minutes)
        override fun setInstrumentLayout(layout: List<InstrumentTileId>): Job = controller.setInstrumentLayout(layout)
        override fun exportBackup(uri: Uri): Job = controller.exportBackup(uri)
        override fun restoreBackup(uri: Uri): Job = controller.restoreBackup(uri)
        override fun clearBackupResult(): Unit = controller.clearBackupResult()
        override fun confirmAlarmAudible(): Unit = controller.confirmAlarmAudible()
        override fun testAlarm(): Unit = controller.testAlarm()
        override fun stopAlarmTest(): ComponentName? = controller.stopAlarmTest()
        override fun openAlarmSoundSettings(): Unit = controller.openAlarmSoundSettings()
        override fun openDoNotDisturbSettings(): Unit = controller.openDoNotDisturbSettings()
    }

    override val feedback: MarineFeedbackService = object : MarineFeedbackService {
        override val state: StateFlow<MainUiState> get() = controller.ui
        override val destinations: SharedFlow<MarineDestination> get() = controller.shellDestinations
        override fun consumeRuntimeFeedback(id: Long): Unit = controller.consumeRuntimeFeedback(id)
    }

    override val display: DisplayDemandService = object : DisplayDemandService {
        private val heading = DisplayLeaseRegistry(controller::setMapHeadingDisplayActive)
        private val instruments = DisplayLeaseRegistry(controller::setTripLiveDisplayActive)
        override fun acquireMapHeading(): DisplayLease = heading.acquire()
        override fun acquireInstruments(): DisplayLease = instruments.acquire()
    }
}

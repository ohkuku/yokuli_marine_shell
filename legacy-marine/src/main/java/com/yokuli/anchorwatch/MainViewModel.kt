package com.yokuli.anchorwatch

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.yokuli.anchorwatch.data.database.AnchorSessionEntity
import com.yokuli.anchorwatch.data.database.SonarSurveyEntity
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import com.yokuli.anchorwatch.data.nmea.ConnectionProfile
import com.yokuli.anchorwatch.data.preferences.AppSettings
import com.yokuli.anchorwatch.domain.sonar.TideMode
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.report.TripReport
import com.yokuli.anchorwatch.domain.report.AnchorReport
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.anchorwatch.domain.condition.ConditionGuardConfig
import com.yokuli.anchorwatch.data.vessel.VesselDataSettings
import com.yokuli.anchorwatch.data.vessel.NmeaOutputTransportMode
import com.yokuli.anchorwatch.data.vessel.NmeaOutputPreset
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import com.yokuli.anchorwatch.data.trip.TripReplayData
import com.yokuli.anchorwatch.data.trip.TripMapData
import com.yokuli.anchorwatch.data.trip.TripDashboard
import com.yokuli.anchorwatch.data.database.SavedAnchorageEntity
import com.yokuli.anchorwatch.domain.anchorage.ApproachHeadingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 旧页面的源码兼容入口。这里只转发同一个进程级 [LegacyMarineController]，不持有业务状态、
 * 协程或服务。页面销毁不能停止用户已经开启的 GPS、航行、守锚或 NMEA 会话；显示需求仍由
 * 页面原有的 setTripLiveDisplayActive / setPhoneHeadingDisplayActive 显式取得和释放。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val controller: LegacyMarineController,
) : AndroidViewModel(app) {
    val ui get() = controller.ui
    val phoneLocationStatus get() = controller.phoneLocationStatus
    val nmeaConnections get() = controller.nmeaConnections
    val nmeaConnectionSpecs get() = controller.nmeaConnectionSpecs
    val shellDestinations get() = controller.shellDestinations
    val navigationRequests get() = controller.navigationRequests

    fun saveNmeaConnection(spec:com.yokuli.anchorwatch.data.nmea.NmeaConnectionSpec,onSaved:()->Unit={}) = controller.saveNmeaConnection(spec, onSaved)
    fun startNmeaConnection(id:String) = controller.startNmeaConnection(id)
    fun stopNmeaConnection(id:String) = controller.stopNmeaConnection(id)
    fun selectNmeaPositionConnection(id:String) = controller.selectNmeaPositionConnection(id)
    fun removeNmeaConnection(id:String) = controller.removeNmeaConnection(id)
    fun setVesselMetricSource(metric:com.yokuli.anchorwatch.domain.vessel.VesselMetricId,sourceId:String?) = controller.setVesselMetricSource(metric, sourceId)
    fun setNmeaMetricSource(metric:com.yokuli.anchorwatch.domain.vessel.VesselMetricId,sourceId:String?) = controller.setNmeaMetricSource(metric, sourceId)
    fun consumeSonarGridChanges(version:Long) = controller.consumeSonarGridChanges(version)
    fun validateProfile(profile:ConnectionProfile) = controller.validateProfile(profile)
    fun saveAndConnect(profile:ConnectionProfile) = controller.saveAndConnect(profile)
    fun disconnect() = controller.disconnect()
    fun reconnectNmea() = controller.reconnectNmea()
    fun stopActiveWatchAndDisconnect() = controller.stopActiveWatchAndDisconnect()
    fun stopNmeaDependenciesAndDisconnect() = controller.stopNmeaDependenciesAndDisconnect()
    fun continueTripWithPhoneAndDisconnect() = controller.continueTripWithPhoneAndDisconnect()
    fun clearConnectionAttempt() = controller.clearConnectionAttempt()
    fun dismissRuntimeFeedback() = controller.dismissRuntimeFeedback()
    fun consumeRuntimeFeedback(id:Long) = controller.consumeRuntimeFeedback(id)
    fun updateSettings(settings:AppSettings) = controller.updateSettings(settings)
    fun completeOnboarding() = controller.completeOnboarding()
    fun setSonarLayerEnabled(enabled:Boolean,acceptDisclaimer:Boolean=false) = controller.setSonarLayerEnabled(enabled, acceptDisclaimer)
    fun exportBackup(uri:Uri) = controller.exportBackup(uri)
    fun restoreBackup(uri:Uri) = controller.restoreBackup(uri)
    fun clearBackupResult() = controller.clearBackupResult()
    fun importOfflineMap(uri:Uri) = controller.importOfflineMap(uri)
    fun removeOfflineMap() = controller.removeOfflineMap()
    fun setOfflineMapEnabled(enabled:Boolean) = controller.setOfflineMapEnabled(enabled)
    fun createOfflineMapProvider() = controller.createOfflineMapProvider()
    fun exportSupportBundle(uri:Uri) = controller.exportSupportBundle(uri)
    fun clearSupportBundleResult() = controller.clearSupportBundleResult()
    fun clearIncidentLog() = controller.clearIncidentLog()
    fun clearRebuildableCaches() = controller.clearRebuildableCaches()
    fun refreshStorage() = controller.refreshStorage()
    fun confirmAlarmAudible() = controller.confirmAlarmAudible()
    fun setNmeaSharing(enabled:Boolean,port:Int) = controller.setNmeaSharing(enabled, port)
    fun saveLocalNmeaServerConfiguration(port:Int,includePressure:Boolean,includeDerivedWind:Boolean) = controller.saveLocalNmeaServerConfiguration(port, includePressure, includeDerivedWind)
    fun startLocalNmeaServer() = controller.startLocalNmeaServer()
    fun saveLocalNmeaPublicationPolicy(port:Int,feed:com.yokuli.anchorwatch.data.nmea.NmeaFeed,capabilities:Set<String>,forwardFrom:Set<String>) = controller.saveLocalNmeaPublicationPolicy(port, feed, capabilities, forwardFrom)
    fun stopLocalNmeaServer() = controller.stopLocalNmeaServer()
    fun stopAllNmeaSharing() = controller.stopAllNmeaSharing()
    fun deleteHistorySession(session:AnchorSessionEntity) = controller.deleteHistorySession(session)
    fun setMapType(mapType:Int) = controller.setMapType(mapType)
    fun setGpsDataSource(source:GpsDataSource) = controller.setGpsDataSource(source)
    fun switchGpsDataSource(source:GpsDataSource) = controller.switchGpsDataSource(source)
    fun setDemoMode(enabled:Boolean) = controller.setDemoMode(enabled)
    fun updateVesselDataSettings(value:VesselDataSettings) = controller.updateVesselDataSettings(value)
    fun createTripDashboard(title:String) = controller.createTripDashboard(title)
    fun saveTripDashboard(value:TripDashboard) = controller.saveTripDashboard(value)
    fun deleteTripDashboard(id:String) = controller.deleteTripDashboard(id)
    fun reorderTripDashboards(ids:List<String>) = controller.reorderTripDashboards(ids)
    fun setTripLiveDisplayActive(active:Boolean) = controller.setTripLiveDisplayActive(active)
    fun confirmTripAttitudeFrame(axis:DeviceBowAxis) = controller.confirmTripAttitudeFrame(axis)
    fun calibrateVesselMount(axis:DeviceBowAxis) = controller.calibrateVesselMount(axis)
    fun setPhoneVesselMounted(mounted:Boolean) = controller.setPhoneVesselMounted(mounted)
    fun alignPhoneHeadingToBow() = controller.alignPhoneHeadingToBow()
    fun alignPhoneHeadingToNmea() = controller.alignPhoneHeadingToNmea()
    fun setPhoneHeadingAlignment(offsetDegrees:Double) = controller.setPhoneHeadingAlignment(offsetDegrees)
    fun clearVesselCalibrationFeedback() = controller.clearVesselCalibrationFeedback()
    fun setNmeaOutputEndpoint(mode:NmeaOutputTransportMode,host:String,port:Int) = controller.setNmeaOutputEndpoint(mode, host, port)
    fun setNmeaPhonePositionPublishing(enabled:Boolean) = controller.setNmeaPhonePositionPublishing(enabled)
    fun setNmeaPhoneHeadingPublishing(enabled:Boolean) = controller.setNmeaPhoneHeadingPublishing(enabled)
    fun setNmeaPhoneRateOfTurnPublishing(enabled:Boolean) = controller.setNmeaPhoneRateOfTurnPublishing(enabled)
    fun setNmeaPhoneAttitudePublishing(enabled:Boolean) = controller.setNmeaPhoneAttitudePublishing(enabled)
    fun setNmeaPhonePressurePublishing(enabled:Boolean) = controller.setNmeaPhonePressurePublishing(enabled)
    fun setNmeaDerivedWindPublishing(enabled:Boolean) = controller.setNmeaDerivedWindPublishing(enabled)
    fun setNmeaOutputPreset(preset:NmeaOutputPreset) = controller.setNmeaOutputPreset(preset)
    fun startNmeaOutput() = controller.startNmeaOutput()
    fun stopNmeaOutput() = controller.stopNmeaOutput()
    fun testNmeaDeviceOutput(result:(Boolean)->Unit) = controller.testNmeaDeviceOutput(result)
    fun testKnownGoodHdgOutput(result:(Boolean)->Unit) = controller.testKnownGoodHdgOutput(result)
    fun updateDemoConfiguration(scenario:com.yokuli.anchorwatch.domain.model.DemoScenario?=null,speed:Int?=null) = controller.updateDemoConfiguration(scenario, speed)
    fun onPermissionsChanged() = controller.onPermissionsChanged()
    fun setAnchorSetupGpsPreview(enabled:Boolean) = controller.setAnchorSetupGpsPreview(enabled)
    fun saveAnchorSetupDraft(value:AnchorSetupDraft) = controller.saveAnchorSetupDraft(value)
    fun clearAnchorSetupDraft() = controller.clearAnchorSetupDraft()
    fun clearDiagnostics() = controller.clearDiagnostics()
    fun arm(lat:Double,lon:Double,input:AnchorWatchInput) = controller.arm(lat, lon, input)
    fun updateAnchorSettings(input:AnchorWatchInput) = controller.updateAnchorSettings(input)
    fun updateConditionGuards(config:ConditionGuardConfig) = controller.updateConditionGuards(config)
    fun resetWindBaseline() = controller.resetWindBaseline()
    fun pauseWatch() = controller.pauseWatch()
    fun resumeWatch() = controller.resumeWatch()
    fun liftAnchor() = controller.liftAnchor()
    fun stop() = controller.stop()
    fun acknowledge() = controller.acknowledge()
    fun acceptEstimatedCenter(session:AnchorSessionEntity) = controller.acceptEstimatedCenter(session)
    fun keepCurrentCenter(session:AnchorSessionEntity) = controller.keepCurrentCenter(session)
    fun continueEstimatingCenter(session:AnchorSessionEntity) = controller.continueEstimatingCenter(session)
    fun resetCentreAnalysis(session:AnchorSessionEntity) = controller.resetCentreAnalysis(session)
    fun recalculateCentreFromTrack(session:AnchorSessionEntity) = controller.recalculateCentreFromTrack(session)
    fun dismissCentreRecalculation() = controller.dismissCentreRecalculation()
    fun keepCurrentRecalculatedCentre() = controller.keepCurrentRecalculatedCentre()
    fun applyRecalculatedCentre() = controller.applyRecalculatedCentre()
    fun saveRecalculatedCentreAsAnchorage() = controller.saveRecalculatedCentreAsAnchorage()
    fun testAlarm() = controller.testAlarm()
    fun stopAlarmTest() = controller.stopAlarmTest()
    fun startSonarSurvey(name:String,tideMode:TideMode,manualTideOffsetMeters:Double,tideStationId:String?=null) = controller.startSonarSurvey(name, tideMode, manualTideOffsetMeters, tideStationId)
    fun stopSonarSurvey() = controller.stopSonarSurvey()
    fun startTrip(name:String,phoneMotionEnabled:Boolean,positionPreference:VesselSourcePreference=controller.ui.value.vesselSettings.positionPreference) = controller.startTrip(name, phoneMotionEnabled, positionPreference)
    fun pauseTrip() = controller.pauseTrip()
    fun resumeTrip() = controller.resumeTrip()
    fun pauseTripAttitude() = controller.pauseTripAttitude()
    fun endTrip() = controller.endTrip()
    fun markTripWaypoint(name:String,note:String,type:String) = controller.markTripWaypoint(name, note, type)
    fun deleteTrip(session:TripSessionEntity) = controller.deleteTrip(session)
    fun renameTrip(id:Long,name:String) = controller.renameTrip(id, name)
    fun editTripMoment(value:com.yokuli.anchorwatch.data.database.TripWaypointEntity,name:String,note:String) = controller.editTripMoment(value, name, note)
    suspend fun tripReport(sessionId:Long):TripReport? = controller.tripReport(sessionId)
    suspend fun anchorReport(sessionId:Long):AnchorReport? = controller.anchorReport(sessionId)
    suspend fun tripReplay(sessionId:Long):TripReplayData = controller.tripReplay(sessionId)
    suspend fun tripMapData(sessionId:Long,pointBudget:Int):TripMapData = controller.tripMapData(sessionId, pointBudget)
    fun openLiveTripMap() = controller.openLiveTripMap()
    fun openTripMap(sessionId:Long,waypointId:Long?=null) = controller.openTripMap(sessionId, waypointId)
    fun closeTripMap() = controller.closeTripMap()
    fun exportTripCsv(session:TripSessionEntity) = controller.exportTripCsv(session)
    fun exportTripGpx(session:TripSessionEntity) = controller.exportTripGpx(session)
    fun exportTripKml(session:TripSessionEntity) = controller.exportTripKml(session)
    fun exportTripKmz(session:TripSessionEntity) = controller.exportTripKmz(session)
    fun exportTripEvents(session:TripSessionEntity) = controller.exportTripEvents(session)
    fun exportTripWaypoints(session:TripSessionEntity) = controller.exportTripWaypoints(session)
    fun exportTripCustomMetrics(session:TripSessionEntity) = controller.exportTripCustomMetrics(session)
    fun shareTripLiveSnapshot() = controller.shareTripLiveSnapshot()
    fun shareTripReportSnapshot(session:TripSessionEntity) = controller.shareTripReportSnapshot(session)
    fun exportTripAiSource(session:TripSessionEntity) = controller.exportTripAiSource(session)
    fun exportAnchorAiSource(session:AnchorSessionEntity) = controller.exportAnchorAiSource(session)
    fun renameSonarSurvey(surveyId:Long,name:String) = controller.renameSonarSurvey(surveyId, name)
    fun deleteSonarSurvey(surveyId:Long) = controller.deleteSonarSurvey(surveyId)
    fun rebuildSonarSurvey(surveyId:Long) = controller.rebuildSonarSurvey(surveyId)
    fun selectSonarSurvey(surveyId:Long) = controller.selectSonarSurvey(surveyId)
    fun selectCorrectedSonarHistory() = controller.selectCorrectedSonarHistory()
    fun exportSonarCsv(survey:SonarSurveyEntity) = controller.exportSonarCsv(survey)
    fun startGpsProxy() = controller.startGpsProxy()
    fun stopGpsProxy() = controller.stopGpsProxy()
    fun openDeveloperOptions() = controller.openDeveloperOptions()
    fun openAlarmNotificationSettings() = controller.openAlarmNotificationSettings()
    fun openAlarmSoundSettings() = controller.openAlarmSoundSettings()
    fun openDoNotDisturbSettings() = controller.openDoNotDisturbSettings()
    fun openBatteryOptimization() = controller.openBatteryOptimization()
    fun openFullScreenAlarmSettings() = controller.openFullScreenAlarmSettings()
    fun openAnchorInGoogleMaps(session:AnchorSessionEntity) = controller.openAnchorInGoogleMaps(session)
    fun openAnchorageInGoogleMaps(value:SavedAnchorageEntity) = controller.openAnchorageInGoogleMaps(value)
    fun openAnchorageCoordinates(latitude:Double,longitude:Double) = controller.openAnchorageCoordinates(latitude, longitude)
    fun approachAnchorageSpot(spotId:Long) = controller.approachAnchorageSpot(spotId)
    fun approachSavedAnchorage(savedAnchorageId:Long) = controller.approachSavedAnchorage(savedAnchorageId)
    fun approachAnchorage(clusterId:String) = controller.approachAnchorage(clusterId)
    fun confirmAnchorageApproachDisclaimer() = controller.confirmAnchorageApproachDisclaimer()
    fun dismissAnchorageApproachDisclaimer() = controller.dismissAnchorageApproachDisclaimer()
    fun setApproachHeadingMode(mode:ApproachHeadingMode) = controller.setApproachHeadingMode(mode)
    fun cancelAnchorageApproach() = controller.cancelAnchorageApproach()
    fun setPhoneHeadingDisplayActive(active:Boolean) = controller.setPhoneHeadingDisplayActive(active)
    fun setMapHeadingDisplayActive(active:Boolean) = controller.setMapHeadingDisplayActive(active)
    fun dismissNearbyAnchorage() = controller.dismissNearbyAnchorage()
    fun shareAnchorageQr(value:SavedAnchorageEntity) = controller.shareAnchorageQr(value)
    fun page(index:Int) = controller.page(index)
    fun rememberAnchorSection(index:Int) = controller.rememberAnchorSection(index)
    fun rememberSailSection(index:Int) = controller.rememberSailSection(index)
    fun openDataSection(index:Int) = controller.openDataSection(index)
    fun rememberDataSection(index:Int) = controller.rememberDataSection(index)
    fun follow(value:Boolean) = controller.follow(value)
    fun requestRangeEditor() = controller.requestRangeEditor()
    fun consumeRangeEditorRequest() = controller.consumeRangeEditorRequest()
    fun loadHistoryEvents(sessionId:Long) = controller.loadHistoryEvents(sessionId)
    fun saveAnchorage(value:SavedAnchorageEntity) = controller.saveAnchorage(value)
    fun dismissAnchorageDuplicate() = controller.dismissAnchorageDuplicate()
    fun deleteAnchorage(id:Long) = controller.deleteAnchorage(id)
    fun dismissAnchorageOperationError() = controller.dismissAnchorageOperationError()
    fun exportCsv(session:AnchorSessionEntity) = controller.exportCsv(session)
    fun exportGpx(session:AnchorSessionEntity) = controller.exportGpx(session)
}

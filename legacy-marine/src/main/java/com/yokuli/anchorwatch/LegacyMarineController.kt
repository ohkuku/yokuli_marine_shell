package com.yokuli.anchorwatch

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.NmeaInstrumentState
import com.yokuli.anchorwatch.data.AlarmUiRepository
import com.yokuli.anchorwatch.data.backup.BackupOperationState
import com.yokuli.anchorwatch.data.backup.YokuliBackupManager
import com.yokuli.anchorwatch.data.database.AnchorDao
import com.yokuli.anchorwatch.data.database.AnchorSessionEntity
import com.yokuli.anchorwatch.data.database.TrackPointEntity
import com.yokuli.anchorwatch.data.database.AlarmEventEntity
import com.yokuli.anchorwatch.data.database.DepthSampleEntity
import com.yokuli.anchorwatch.data.database.SonarDao
import com.yokuli.anchorwatch.data.database.SonarSurveyEntity
import com.yokuli.anchorwatch.data.database.IncidentLogEntity
import com.yokuli.anchorwatch.data.database.TripDao
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import com.yokuli.anchorwatch.data.export.TripExportManager
import com.yokuli.anchorwatch.data.diagnostics.IncidentLogger
import com.yokuli.anchorwatch.data.diagnostics.StorageHealth
import com.yokuli.anchorwatch.data.diagnostics.StorageHealthRepository
import com.yokuli.anchorwatch.data.diagnostics.SupportBundleManager
import com.yokuli.anchorwatch.data.diagnostics.SupportBundleState
import com.yokuli.anchorwatch.data.nmea.ConnectionProfile
import com.yokuli.anchorwatch.data.nmea.NmeaDiagnostics
import com.yokuli.anchorwatch.data.nmea.NmeaTransportDiagnostics
import com.yokuli.anchorwatch.data.nmea.NmeaEndpointPreflight
import com.yokuli.anchorwatch.data.nmea.NmeaFieldObservation
import com.yokuli.anchorwatch.data.nmea.NmeaFieldRepository
import com.yokuli.anchorwatch.data.nmea.Protocol
import com.yokuli.anchorwatch.data.nmea.output.NmeaOutputEndpointPolicy
import com.yokuli.anchorwatch.data.preferences.AppSettings
import com.yokuli.anchorwatch.data.preferences.SettingsRepository
import com.yokuli.anchorwatch.data.sharing.NmeaSharingServer
import com.yokuli.anchorwatch.data.sharing.NmeaSharingStatus
import com.yokuli.anchorwatch.data.sharing.LocalNmeaServerSettings
import com.yokuli.anchorwatch.data.sharing.LocalNmeaServerSettingsRepository
import com.yokuli.anchorwatch.data.sonar.SonarRecorderStatus
import com.yokuli.anchorwatch.data.sonar.SonarSurveyRecorder
import com.yokuli.anchorwatch.data.sonar.SonarIncrementalGridUpdater
import com.yokuli.anchorwatch.data.linz.LinzDepthReferenceRepository
import com.yokuli.anchorwatch.data.linz.LinzDepthReference
import com.yokuli.anchorwatch.data.linz.LinzDepthDiagnostics
import com.yokuli.anchorwatch.domain.sonar.TideMode
import com.yokuli.anchorwatch.domain.sonar.SonarGrid
import com.yokuli.anchorwatch.domain.sonar.DepthUiState
import com.yokuli.anchorwatch.data.sonar.SonarGridScope
import com.yokuli.anchorwatch.domain.model.AnchorCenterSource
import com.yokuli.anchorwatch.domain.model.AnchorOriginMode
import com.yokuli.anchorwatch.domain.model.AnchorPlacementMode
import com.yokuli.anchorwatch.domain.model.AnchorRangeMode
import com.yokuli.anchorwatch.domain.model.AnchorSafetyPreset
import com.yokuli.anchorwatch.domain.model.AlarmSnapshot
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.domain.model.AlarmSound
import com.yokuli.anchorwatch.domain.model.NavigationFix
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.anchor.AnchorDepthSource
import com.yokuli.anchorwatch.domain.anchor.AnchorCentreRecalculationResult
import com.yokuli.anchorwatch.domain.anchor.AnchorCentreRecalculationStatus
import com.yokuli.anchorwatch.domain.anchor.AnchorCentreRecalculator
import com.yokuli.anchorwatch.domain.report.TripReport
import com.yokuli.anchorwatch.domain.report.TripReportEngine
import com.yokuli.anchorwatch.domain.report.AnchorReport
import com.yokuli.anchorwatch.domain.report.AnchorReportEngine
import com.yokuli.anchorwatch.location.GlobalMockLocationManager
import com.yokuli.anchorwatch.location.DemoGpsStatus
import com.yokuli.anchorwatch.location.DemoLocationRepository
import com.yokuli.anchorwatch.location.GpsSourceSafety
import com.yokuli.anchorwatch.location.MockGpsState
import com.yokuli.anchorwatch.location.MockGpsStatus
import com.yokuli.anchorwatch.location.NmeaSourceAvailability
import com.yokuli.anchorwatch.location.NmeaSourceSelectionPolicy
import com.yokuli.anchorwatch.location.NewAnchorPositionSourcePolicy
import com.yokuli.anchorwatch.location.SystemLocationRepository
import com.yokuli.anchorwatch.location.AcceptedPositionRepository
import com.yokuli.anchorwatch.location.AcceptedPositionState
import com.yokuli.anchorwatch.location.PhoneHeadingRepository
import com.yokuli.anchorwatch.location.PhoneHeadingSample
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.anchorwatch.location.vessel.PhoneSensorCapabilities
import com.yokuli.anchorwatch.location.vessel.PhoneVesselAttitudeRepository
import com.yokuli.anchorwatch.location.vessel.PhoneHeadingAlignmentPolicy
import com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState
import com.yokuli.anchorwatch.location.vessel.VesselMountCalibration
import com.yokuli.anchorwatch.location.vessel.VesselMountCalibrationRepository
import com.yokuli.anchorwatch.location.vessel.PhoneVesselOutputReadinessPolicy
import com.yokuli.anchorwatch.runtime.nmea.NmeaManualDisconnectRepository
import com.yokuli.anchorwatch.runtime.sharing.LocalNmeaServerRuntime
import com.yokuli.anchorwatch.runtime.sharing.LocalNmeaServerRuntimeStatus
import com.yokuli.anchorwatch.service.AnchorForegroundService
import com.yokuli.anchorwatch.runtime.RuntimeDiagnostics
import com.yokuli.anchorwatch.runtime.RuntimeDiagnosticsRepository
import com.yokuli.anchorwatch.runtime.RuntimeOwner
import com.yokuli.anchorwatch.runtime.RuntimeRequirement
import com.yokuli.anchorwatch.runtime.RuntimeResourceManager
import com.yokuli.anchorwatch.runtime.RuntimeResourceSnapshot
import com.yokuli.anchorwatch.domain.safety.DeviceSafetyProbe
import com.yokuli.anchorwatch.domain.safety.WatchPreflightEvaluator
import com.yokuli.anchorwatch.domain.safety.WatchSafetyInput
import com.yokuli.anchorwatch.domain.safety.WatchSafetyReport
import com.yokuli.anchorwatch.map.OfflineMapInfo
import com.yokuli.anchorwatch.map.OfflineMapRepository
import com.yokuli.anchorwatch.domain.condition.ConditionGuardConfig
import com.yokuli.anchorwatch.domain.condition.ConditionRuntimeSnapshot
import com.yokuli.anchorwatch.data.condition.LiveDepthRepository
import com.yokuli.anchorwatch.data.condition.LiveDepthState
import com.yokuli.anchorwatch.data.condition.LiveWindRepository
import com.yokuli.anchorwatch.data.condition.LiveWindState
import com.yokuli.anchorwatch.data.vessel.VesselDataHub
import com.yokuli.anchorwatch.data.vessel.VesselDataSettings
import com.yokuli.anchorwatch.data.vessel.VesselSettingsRepository
import com.yokuli.anchorwatch.data.vessel.NmeaDeviceOutputSettings
import com.yokuli.anchorwatch.data.vessel.NmeaOutputTransportMode
import com.yokuli.anchorwatch.data.vessel.NmeaOutputPreset
import com.yokuli.anchorwatch.data.vessel.withPreset
import com.yokuli.anchorwatch.data.vessel.anyStreamSelected
import com.yokuli.anchorwatch.domain.vessel.NmeaOutputPurpose
import com.yokuli.anchorwatch.domain.vessel.PublicationPolicy
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import com.yokuli.anchorwatch.domain.vessel.InstrumentTileId
import com.yokuli.anchorwatch.data.vessel.OutputSettingsRepository
import com.yokuli.anchorwatch.data.trip.TripReplayLoader
import com.yokuli.anchorwatch.data.trip.TripReplayData
import com.yokuli.anchorwatch.data.trip.TripMapData
import com.yokuli.anchorwatch.data.trip.TripMapDestination
import com.yokuli.anchorwatch.data.trip.TripMapDestinationType
import com.yokuli.anchorwatch.data.trip.TripTrackRepository
import com.yokuli.anchorwatch.data.trip.TripTrackSnapshot
import com.yokuli.anchorwatch.data.trip.TripDashboardRepository
import com.yokuli.anchorwatch.data.trip.TripDashboard
import com.yokuli.anchorwatch.domain.vessel.VesselDataSnapshot
import com.yokuli.anchorwatch.runtime.output.PhonePositionNmeaOutputRuntime
import com.yokuli.anchorwatch.runtime.output.PhonePositionOutputStatus
import com.yokuli.anchorwatch.runtime.condition.ConditionRuntime
import com.yokuli.anchorwatch.data.anchorage.AnchorageApproachRepository
import com.yokuli.anchorwatch.data.anchorage.AnchorageQrImageGenerator
import com.yokuli.anchorwatch.data.anchorage.AnchorageShareContent
import com.yokuli.anchorwatch.data.anchorage.DuplicateAnchorageException
import com.yokuli.anchorwatch.data.database.SavedAnchorageEntity
import com.yokuli.anchorwatch.localization.usesChinese
import com.yokuli.anchorwatch.domain.anchorage.AnchorageApproachEngine
import com.yokuli.anchorwatch.domain.anchorage.AnchorageApproachState
import com.yokuli.anchorwatch.domain.anchorage.AnchorageCluster
import com.yokuli.anchorwatch.domain.anchorage.AnchorageClusterDistance
import com.yokuli.anchorwatch.domain.anchorage.AnchorageNearbyEpisodeTracker
import com.yokuli.anchorwatch.domain.anchorage.AnchorageNearbyPolicy
import com.yokuli.anchorwatch.domain.anchorage.ApproachDirectionPolicy
import com.yokuli.anchorwatch.domain.anchorage.ApproachHeadingMode
import com.yokuli.anchorwatch.domain.navigation.NmeaCourseTrustGate
import com.yokuli.anchorwatch.domain.navigation.TrustedNmeaCourse
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

enum class ConnectionAttemptState { IDLE, TESTING, WARNING, FAILED }
data class ConnectionAttempt(val state:ConnectionAttemptState=ConnectionAttemptState.IDLE,val message:String="")
data class CentreRecalculationUiState(val sessionId:Long?=null,val sessionActive:Boolean=false,val loading:Boolean=false,val result:AnchorCentreRecalculationResult?=null)
const val CORRECTED_SONAR_HISTORY_ID = -1L

data class AnchorSetupDraft(
    val referenceKey:String="none",
    val referenceAlarmRadiusMeters:Double?=null,
    val referenceWaterDepthMeters:Double?=null,
    val referenceRodeMeters:Double?=null,
    val estimate:Boolean=false,
    val knownMethod:String=AnchorCenterSource.CURRENT_POSITION.name,
    val manualCoordinate:String="",
    val mapLatitude:Double?=null,
    val mapLongitude:Double?=null,
    val rangeMode:String=AnchorRangeMode.BASIC.name,
    val safetyPreset:String=AnchorSafetyPreset.BALANCED.name,
    val depthSource:String=AnchorDepthSource.MANUAL.name,
    val depth:String="",
    val rode:String="40",
    val bowHeight:String="",
    val boatLength:String="",
    val alarmRadius:String="",
    val depthGuard:Boolean=false,
    val shallowDepth:String="",
    val deepGuard:Boolean=false,
    val deepDepth:String="",
    val windGuard:Boolean=false,
    val windWarning:String="",
    val windAlarm:String="",
    val windShift:Boolean=false,
    val windShiftDegrees:String="",
    /** 中文：当前下锚/已锚好是访问草稿；不代表守锚已经开始。 */
    val setupScenario:String="selected",
    val referenceCapturedAt:Long?=null,
    val referencePositionSource:String?=null,
)

data class MainUiState(
    val fix:NavigationFix?=null,
    val nmeaFix:NavigationFix?=null,
    val nmeaConnectionStartedElapsed:Long?=null,
    val systemFix:NavigationFix?=null,
    val connection:NmeaConnectionState=NmeaConnectionState.DISCONNECTED,
    val connectionAttempt:ConnectionAttempt=ConnectionAttempt(),
    val diagnostics:NmeaDiagnostics=NmeaDiagnostics(),
    val nmeaTransportDiagnostics:NmeaTransportDiagnostics=NmeaTransportDiagnostics(),
    val nmeaInstruments:NmeaInstrumentState=NmeaInstrumentState(),
    val settings:AppSettings=AppSettings(),
    val settingsReady:Boolean=false,
    val sessions:List<AnchorSessionEntity> = emptyList(),
    val active:AnchorSessionEntity?=null,
    val points:List<TrackPointEntity> = emptyList(),
    /** Full current centre-analysis count; [points] is only the bounded map trail. */
    val activeLearningPointCount:Int=0,
    val follow:Boolean=true,
    val page:Int=0,
    val anchorSection:Int=0,
    val sailSection:Int=0,
    val dataSection:Int=0,
    val mockGps:MockGpsStatus=MockGpsStatus(),
    val proxyFeedback:String?=null,
    val demoGps:DemoGpsStatus=DemoGpsStatus(),
    val alarmSnapshot:AlarmSnapshot=AlarmSnapshot(),
    val rangeEditorRequested:Boolean=false,
    val eventsBySession:Map<Long,List<AlarmEventEntity>> = emptyMap(),
    val positionHealth:com.yokuli.anchorwatch.domain.model.PositionHealth=com.yokuli.anchorwatch.domain.model.PositionHealth.GPS_LOST,
    val nmeaSharing:NmeaSharingStatus=NmeaSharingStatus(),
    val localNmeaServerSettings:LocalNmeaServerSettings=LocalNmeaServerSettings(),
    val localNmeaServerRuntime:LocalNmeaServerRuntimeStatus=LocalNmeaServerRuntimeStatus(),
    val acceptedPosition:AcceptedPositionState=AcceptedPositionState(),
    val sonarSurveys:List<SonarSurveyEntity> = emptyList(),
    val selectedSonarSurveyId:Long? = null,
    val activeSonarSurvey:SonarSurveyEntity? = null,
    val sonarSamples:List<DepthSampleEntity> = emptyList(),
    val sonarGrid:SonarGrid = SonarGrid.build(emptyList()),
    val sonarGridVersion:Long=0L,
    val sonarGridChangedCells:Set<Pair<Long,Long>> = emptySet(),
    val sonarRecorder:SonarRecorderStatus = SonarRecorderStatus(),
    val linzDepth:LinzDepthReference=LinzDepthReference(),
    val linzDepthDiagnostics:LinzDepthDiagnostics=LinzDepthDiagnostics(),
    val depthUi:DepthUiState=DepthUiState(),
    val backup:BackupOperationState=BackupOperationState(),
    val runtimeDiagnostics:RuntimeDiagnostics=RuntimeDiagnostics(),
    val dismissedRuntimeFeedbackId:Long=0L,
    val watchSafety:WatchSafetyReport=WatchSafetyReport(),
    val storageHealth:StorageHealth=StorageHealth(),
    val incidents:List<IncidentLogEntity> = emptyList(),
    val supportBundle:SupportBundleState=SupportBundleState(),
    val offlineMap:OfflineMapInfo=OfflineMapInfo(),
    /** Live orientation is intentionally separate from GPS publication cadence. */
    val phoneHeading:PhoneHeadingSample=PhoneHeadingSample(),
    /** NMEA COG accepted only after the anti-wander speed/time gate. */
    val trustedNmeaCourse:TrustedNmeaCourse?=null,
    val approachHeadingMode:ApproachHeadingMode=ApproachHeadingMode.PHONE,
    val vesselApproachHeadingAvailable:Boolean=false,
    val liveDepth:LiveDepthState=LiveDepthState(),
    val liveWind:LiveWindState=LiveWindState(),
    val conditions:ConditionRuntimeSnapshot=ConditionRuntimeSnapshot(),
    val savedAnchorages:List<SavedAnchorageEntity> = emptyList(),
    val anchorageClusters:List<AnchorageCluster> = emptyList(),
    val anchorageApproach:AnchorageApproachState = AnchorageApproachState(),
    val nearbyAnchoragePrompt:List<AnchorageClusterDistance> = emptyList(),
    val approachDisclaimerTargetId:String?=null,
    val anchorageDuplicateExisting:SavedAnchorageEntity?=null,
    val anchorageOperationError:String?=null,
    val centreRecalculation:CentreRecalculationUiState=CentreRecalculationUiState(),
    val vesselData:VesselDataSnapshot=VesselDataSnapshot(),
    val nmeaFields:List<NmeaFieldObservation> = emptyList(),
    val vesselSettings:VesselDataSettings=VesselDataSettings(),
    val outputSettings:NmeaDeviceOutputSettings=NmeaDeviceOutputSettings(),
    val phonePositionOutputStatus:PhonePositionOutputStatus=PhonePositionOutputStatus(),
    val tripSessions:List<TripSessionEntity> = emptyList(),
    val activeTrip:TripSessionEntity? = null,
    val tripTrack:TripTrackSnapshot=TripTrackSnapshot(),
    val tripMapDestination:TripMapDestination?=null,
    val tripDashboards:List<TripDashboard> = emptyList(),
    val phoneSensorCapabilities:PhoneSensorCapabilities=PhoneSensorCapabilities(),
    val vesselMountCalibration:VesselMountCalibration=VesselMountCalibration(),
    val phoneVesselMountState:PhoneVesselMountState=PhoneVesselMountState.UNCALIBRATED,
    val vesselCalibrationFeedback:String?=null,
    val runtimeResources:RuntimeResourceSnapshot=RuntimeResourceSnapshot(),
    val anchorSetupDraft:AnchorSetupDraft?=null,
    val anchorDraftSaveError:Boolean=false,
)

private data class PositionSources(val selected:NavigationFix?,val nmea:NavigationFix?,val system:NavigationFix?,val settings:AppSettings)
private data class AvailablePositions(val nmea:NavigationFix?,val system:NavigationFix?,val demo:NavigationFix?,val demoStatus:DemoGpsStatus)
private data class PositionRoutingSnapshot(
    val positions:AvailablePositions,
    val settings:AppSettings,
    val active:AnchorSessionEntity?,
    val connection:NmeaConnectionState,
    val connectionStartedElapsed:Long?,
)
data class AnchorWatchInput(val placement:AnchorPlacementMode,val rangeMode:AnchorRangeMode,val safetyPreset:AnchorSafetyPreset,val depthMeters:Double?,val rodeMeters:Double,val bowHeightMeters:Double,val boatLengthMeters:Double?,val alarmRadiusMeters:Double,val positionSource:GpsDataSource=GpsDataSource.SYSTEM,val centerSource:AnchorCenterSource=AnchorCenterSource.CURRENT_POSITION,val usePhoneHeading:Boolean=true,val depthSource:AnchorDepthSource=AnchorDepthSource.MANUAL,val conditions:ConditionGuardConfig=ConditionGuardConfig(),val originMode:AnchorOriginMode=AnchorOriginMode.CURRENT_ACCEPTED_POSITION,val anchoragePlaceId:Long?=null,val anchorageSpotId:Long?=null)

/**
 * 进程级旧业务组合根：应用端口和兼容 ViewModel 都注入同一个实例。此处仅移动所有权，
 * 航行、锚警和数据服务仍由原有 repository / foreground service 负责，不因页面重建而重建。
 * controllerScope 随应用进程存活；UI 只改变显示需求，不负责关闭全局业务。
 */
@Singleton
class LegacyMarineController @Inject constructor(
    private val app:Application,
    private val nav:NavigationRepository,
    private val multiPublisher:com.yokuli.anchorwatch.runtime.output.MultiNmeaPublisher,
    private val dao:AnchorDao,
    private val prefs:SettingsRepository,
    private val mockManager:GlobalMockLocationManager,
    private val systemLocation:SystemLocationRepository,
    private val demoLocation:DemoLocationRepository,
    private val endpointPreflight:NmeaEndpointPreflight,
    private val alarmUi:AlarmUiRepository,
    private val sharingServer:NmeaSharingServer,
    private val localNmeaServerSettingsRepository:LocalNmeaServerSettingsRepository,
    private val localNmeaServerRuntime:LocalNmeaServerRuntime,
    private val acceptedPosition:AcceptedPositionRepository,
    private val phoneHeadingRepository:PhoneHeadingRepository,
    private val sonarDao:SonarDao,
    private val sonarRecorder:SonarSurveyRecorder,
    private val sonarGridUpdater:SonarIncrementalGridUpdater,
    private val linzDepthRepository:LinzDepthReferenceRepository,
    private val backupManager:YokuliBackupManager,
    private val runtimeDiagnostics:RuntimeDiagnosticsRepository,
    private val anchorCommands:com.yokuli.anchorwatch.runtime.AnchorCommandRegistry,
    private val safetyProbe:DeviceSafetyProbe,
    private val storageHealthRepository:StorageHealthRepository,
    private val supportBundleManager:SupportBundleManager,
    private val incidentLogger:IncidentLogger,
    private val offlineMapRepository:OfflineMapRepository,
    private val liveDepthRepository:LiveDepthRepository,
    private val liveWindRepository:LiveWindRepository,
    private val conditionRuntime:ConditionRuntime,
    private val anchorageApproachRepository:AnchorageApproachRepository,
    private val anchorageSpotRepository:com.yokuli.anchorwatch.data.anchorage.AnchorageSpotRepository,
    private val anchorageQrImageGenerator:AnchorageQrImageGenerator,
    private val vesselDataHub:VesselDataHub,
    private val nmeaFieldRepository:NmeaFieldRepository,
    private val vesselSettingsRepository:VesselSettingsRepository,
    private val outputSettingsRepository:OutputSettingsRepository,
    private val phonePositionNmeaOutputRuntime:PhonePositionNmeaOutputRuntime,
    private val tripDao:TripDao,
    private val runtimeResources:RuntimeResourceManager,
    private val vesselAttitudeRepository:PhoneVesselAttitudeRepository,
    private val vesselMountCalibrationRepository:VesselMountCalibrationRepository,
    private val nmeaManualDisconnectRepository:NmeaManualDisconnectRepository,
    private val tripExportManager:TripExportManager,
    private val tripReplayLoader:TripReplayLoader,
    private val tripTrackRepository:TripTrackRepository,
    private val tripDashboardRepository:TripDashboardRepository,
    private val tripReportEngine:TripReportEngine,
    private val anchorReportEngine:AnchorReportEngine,
    private val voyageCommands:com.yokuli.anchorwatch.runtime.VoyageCommandRegistry,
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /**
     * 聚合状态以及编辑草稿只在当前进程内保存；Activity 重建继续使用同一个实例。
     * anchorSetupDraft 作为未启动草稿单独保存；tripMapDestination 仅存在本次访问，不能冒充记录。
     * 已开始的航行、锚警会话和配置仍从各自的持久化 repository 恢复。
     */
    private val anchorDraftStorage=app.getSharedPreferences("anchor_setup_draft",android.content.Context.MODE_PRIVATE)
    private val anchorDraftJson=com.google.gson.Gson()
    private val anchorDraftWrites=kotlinx.coroutines.channels.Channel<AnchorSetupDraft?>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val restoredAnchorDraft=runCatching{anchorDraftStorage.getString("draft",null)?.let{anchorDraftJson.fromJson(it,AnchorSetupDraft::class.java)}}.getOrNull()
    private val _ui = MutableStateFlow(MainUiState(anchorSetupDraft=restoredAnchorDraft))
    init { controllerScope.launch(Dispatchers.IO) {
        for(draft in anchorDraftWrites){
            val saved=runCatching{val edit=anchorDraftStorage.edit();if(draft==null)edit.remove("draft")else edit.putString("draft",anchorDraftJson.toJson(draft));edit.commit()}.getOrDefault(false)
            _ui.update{it.copy(anchorDraftSaveError=!saved)}
        }
    } }
    val ui = _ui.asStateFlow()
    val phoneLocationStatus=systemLocation.status
    val nmeaConnections=nav.connections
    val nmeaConnectionSpecs=nav.connectionSpecs
    fun saveNmeaConnection(spec:com.yokuli.anchorwatch.data.nmea.NmeaConnectionSpec,onSaved:()->Unit={})=controllerScope.launch{runCatching{nav.saveConnection(spec)}.onSuccess{_ui.update{it.copy(connectionAttempt=ConnectionAttempt())};onSaved()}.onFailure{error->_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,error.message.orEmpty()))}}}
    fun startNmeaConnection(id:String)=controllerScope.launch(kotlinx.coroutines.Dispatchers.IO){
        nmeaManualDisconnectRepository.clear()
        nav.startConnection(id)
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction("OS_NETWORK_CHANGED"))
    }
    fun stopNmeaConnection(id:String)=controllerScope.launch(kotlinx.coroutines.Dispatchers.IO){nav.stopConnection(id)}
    fun selectNmeaPositionConnection(id:String)=selectNmeaPositionSource(id,null)
    private fun selectNmeaPositionSource(id:String,sourceKey:String?){
        if(!nav.isConnectionOpen(id)){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Connect this NMEA input before selecting its position."))};return}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction("OS_NMEA_POSITION").putExtra("connectionId",id).putExtra("sourceKey",sourceKey))
    }
    fun removeNmeaConnection(id:String)=controllerScope.launch{nav.removeConnection(id)}
    /** 唯一的逐项选源写入口；手机与 NMEA 都写入同一份用户偏好，不另建应用配置。 */
    fun setVesselMetricSource(metric:com.yokuli.anchorwatch.domain.vessel.VesselMetricId,sourceId:String?)=controllerScope.launch{
        if(metric==com.yokuli.anchorwatch.domain.vessel.VesselMetricId.POSITION){
            if(sourceId==null)return@launch
            val source=ui.value.vesselData.candidates[metric]?.firstOrNull{com.yokuli.anchorwatch.domain.vessel.VesselSourcePinPolicy.matches(it.source,sourceId)}?.source?:return@launch
            source.transportProfileId?.let{selectNmeaPositionSource(it,sourceId)}
            return@launch
        }
        vesselSettingsRepository.selectMetricSource(metric, sourceId)
    }
    /** 旧入口兼容；名称保留不代表 NMEA 应用拥有数据源设置。 */
    fun setNmeaMetricSource(metric:com.yokuli.anchorwatch.domain.vessel.VesselMetricId,sourceId:String?)=setVesselMetricSource(metric,sourceId)
    private var pointsJob:Job?=null
    private var observedSessionId:Long?=null
    private var observedEstimationEpochStartedAt:Long?=null
    private var sonarSamplesJob:Job?=null
    private var observedSonarSurveyId:Long?=null
    private val anchorageNearbyTracker=AnchorageNearbyEpisodeTracker()
    private val nmeaCourseTrustGate=NmeaCourseTrustGate()
    private var selectedApproachClusterId:String?=null
    private var selectedApproachMemberIds:Set<Long> = emptySet()
    private var gisApproachTarget:AnchorageCluster?=null
    private var lastApproachHeadingRefreshElapsed=0L

    init{
        // Safety and recording repositories consume the full provider rate. Compose/map only
        // needs a bounded visual cadence; drawing every 10‑20 Hz NMEA sentence can starve taps,
        // service commands and test idling on slower phones without adding navigational value.
        val nmeaForUi=nav.fix.map{fix->delay(UI_POSITION_FRAME_MILLIS);fix}
        val diagnosticsForUi=nav.diagnostics.map{diagnostics->delay(UI_POSITION_FRAME_MILLIS);diagnostics}
        val available=combine(nmeaForUi,systemLocation.fix,demoLocation.fix,demoLocation.status){nmea,system,demo,status->AvailablePositions(nmea,system,demo,status)}
        val sources=combine(available,prefs.settings){positions,settings->
            val selected=when(settings.gpsDataSource){GpsDataSource.NONE->null;GpsDataSource.NMEA->positions.nmea;GpsDataSource.SYSTEM->positions.system;GpsDataSource.DEMO->if(positions.demoStatus.running)positions.demo else positions.system}
            PositionSources(selected,positions.nmea,positions.system,settings) to positions.demoStatus
        }
        val positionRouting=combine(available,prefs.settings,dao.sessions(),nav.connectionState,nav.connectionStartedElapsed){positions,settings,sessions,connection,connectionStarted->
            PositionRoutingSnapshot(positions,settings,sessions.firstOrNull{it.active},connection,connectionStarted)
        }
        controllerScope.launch{
            positionRouting.collect{routing->
                val positions=routing.positions;val settings=routing.settings;val active=routing.active
                val lockedSource=active?.takeUnless{it.paused&&settings.gpsDataSource==GpsDataSource.NONE}?.positionSource?.let{runCatching{GpsDataSource.valueOf(it)}.getOrNull()}
                val source=lockedSource?:NewAnchorPositionSourcePolicy.resolve(settings.gpsDataSource,settings.demoMode)
                if(active!=null)acceptedPosition.lockSource(active.id,source)else{acceptedPosition.unlockSource(null);acceptedPosition.selectSource(source)}
                val raw=when(source){GpsDataSource.NONE->null;GpsDataSource.NMEA->positions.nmea;GpsDataSource.SYSTEM->positions.system;GpsDataSource.DEMO->positions.demo.takeIf{positions.demoStatus.running}}
                raw?.let{acceptedPosition.submit(source,it,nav.connectionGeneration().takeIf{source==GpsDataSource.NMEA})}
            }
        }
        controllerScope.launch{
            positionRouting.collect{routing->
                val lockedSource=routing.active?.takeUnless{it.paused&&routing.settings.gpsDataSource==GpsDataSource.NONE}?.positionSource?.let{runCatching{GpsDataSource.valueOf(it)}.getOrNull()}
                val effectiveSource=NewAnchorPositionSourcePolicy.resolve(routing.settings.gpsDataSource,routing.settings.demoMode)
                systemLocation.setAppEnabled(effectiveSource in setOf(GpsDataSource.SYSTEM,GpsDataSource.DEMO))
                vesselDataHub.setShellPositionSource(effectiveSource)
            }
        }
        controllerScope.launch{
            combine(sources,nav.connectionState,nav.connectionStartedElapsed,diagnosticsForUi,dao.sessions()){sourceAndDemo,connection,connectionStarted,diagnostics,sessions->
                arrayOf(sourceAndDemo,connection,connectionStarted,diagnostics,sessions)
            }.collect{values->
                @Suppress("UNCHECKED_CAST") val sourceAndDemo=values[0] as Pair<PositionSources,DemoGpsStatus>
                val position=sourceAndDemo.first
                @Suppress("UNCHECKED_CAST") val sessions=values[4] as List<AnchorSessionEntity>
                val active=sessions.firstOrNull{it.active}
                val connection=values[1] as NmeaConnectionState
                val nowElapsed=android.os.SystemClock.elapsedRealtime()
                val trustedNmeaCourse=nmeaCourseTrustGate.update(position.nmea,nowElapsed)
                _ui.update{it.copy(nmeaFix=position.nmea,trustedNmeaCourse=trustedNmeaCourse,nmeaConnectionStartedElapsed=values[2] as Long?,systemFix=position.system,connection=connection,diagnostics=values[3] as NmeaDiagnostics,settings=position.settings,settingsReady=true,sessions=sessions,active=active,demoGps=sourceAndDemo.second)}
                observePoints(active)
                if(selectedApproachClusterId!=null)refreshAnchorageApproach(nowElapsed)
            }
        }
        controllerScope.launch{acceptedPosition.state.map{accepted->delay(UI_POSITION_FRAME_MILLIS);accepted}.collect{accepted->_ui.update{it.copy(fix=accepted.acceptedFix,positionHealth=accepted.health,acceptedPosition=accepted)};refreshDepthUi()}}
        // Rotation-vector sensors commonly publish much faster than Android GNSS. Keeping
        // this stream independent makes the boat symbol turn immediately while the
        // estimator still receives phone-heading evidence only with accepted positions.
        controllerScope.launch{phoneHeadingRepository.sample.collect{sample->
            _ui.update{it.copy(phoneHeading=sample)}
            val nowElapsed=android.os.SystemClock.elapsedRealtime()
            if(selectedApproachClusterId!=null&&nowElapsed-lastApproachHeadingRefreshElapsed>=100L){
                lastApproachHeadingRefreshElapsed=nowElapsed
                refreshAnchorageApproach(nowElapsed)
            }
        }}
        controllerScope.launch{liveDepthRepository.state.collect{value->_ui.update{it.copy(liveDepth=value)}}}
        controllerScope.launch{liveWindRepository.state.collect{value->_ui.update{it.copy(liveWind=value)}}}
        controllerScope.launch{nav.instruments.collect{value->_ui.update{it.copy(nmeaInstruments=value)}}}
        controllerScope.launch{nav.transportDiagnostics.collect{value->_ui.update{it.copy(nmeaTransportDiagnostics=value)}}}
        controllerScope.launch{vesselDataHub.snapshot.collect{value->_ui.update{it.copy(vesselData=value)}}}
        controllerScope.launch{nmeaFieldRepository.fields.collect{value->_ui.update{it.copy(nmeaFields=value)}}}
        controllerScope.launch{vesselSettingsRepository.settings.collect{value->_ui.update{it.copy(vesselSettings=value)}}}
        controllerScope.launch{outputSettingsRepository.settings.collect{value->_ui.update{it.copy(outputSettings=value)}}}
        controllerScope.launch{phonePositionNmeaOutputRuntime.status.collect{value->_ui.update{it.copy(phonePositionOutputStatus=value)}}}
        controllerScope.launch{tripDao.sessions().collect{value->_ui.update{it.copy(tripSessions=value,activeTrip=value.firstOrNull{trip->trip.active})}}}
        controllerScope.launch{tripTrackRepository.snapshot.collect{value->_ui.update{it.copy(tripTrack=value)}}}
        controllerScope.launch{tripDashboardRepository.decoded.collect{value->_ui.update{it.copy(tripDashboards=value.filter{dashboard->dashboard.preset==com.yokuli.anchorwatch.domain.vessel.TripInstrumentPreset.CUSTOM})}}}
        _ui.update{it.copy(phoneSensorCapabilities=vesselAttitudeRepository.capabilities)}
        controllerScope.launch{vesselMountCalibrationRepository.calibration.collect{value->_ui.update{it.copy(vesselMountCalibration=value)}}}
        controllerScope.launch{vesselAttitudeRepository.mountState.collect{value->_ui.update{it.copy(phoneVesselMountState=value)}}}
        controllerScope.launch{conditionRuntime.state.collect{value->_ui.update{it.copy(conditions=value)}}}
        controllerScope.launch{anchorageApproachRepository.anchorages.collect{value->_ui.update{it.copy(savedAnchorages=value)}}}
        controllerScope.launch{anchorageApproachRepository.clusters.collect{clusters->
            if(selectedApproachClusterId!=null&&gisApproachTarget==null){
                val resolved=com.yokuli.anchorwatch.domain.anchorage.AnchorageClusterIdentityResolver.resolve(selectedApproachClusterId,selectedApproachMemberIds,clusters)
                selectedApproachClusterId=resolved?.id
                selectedApproachMemberIds=resolved?.savedAnchorageIds?.toSet().orEmpty()
                if(resolved==null)phoneHeadingRepository.setApproachDemand(false)
            }
            _ui.update{it.copy(anchorageClusters=clusters)}
            refreshAnchorageApproach()
        }}
        controllerScope.launch{mockManager.status.collect{status->_ui.update{current->val defaultInactive=status.state==MockGpsState.INACTIVE&&status.message=="Android GPS is using the normal system source.";current.copy(mockGps=status,proxyFeedback=if(defaultInactive)current.proxyFeedback?:status.message else status.message)}}}
        controllerScope.launch{alarmUi.snapshot.collect{snapshot->_ui.update{it.copy(alarmSnapshot=snapshot)}}}
        controllerScope.launch{sharingServer.status.collect{status->_ui.update{it.copy(nmeaSharing=status)}}}
        controllerScope.launch{localNmeaServerSettingsRepository.settings.collect{value->
            _ui.update{it.copy(localNmeaServerSettings=value)}
            // START_STICKY normally recreates the foreground service itself.
            // If the user later reopens the Activity after an OEM/force-stop
            // boundary, reclaim the still-explicit same-boot lease here while
            // a visible Activity is allowed to start the service.
            if(value.serverRequested&&!localNmeaServerRuntime.enabled)ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.REFRESH_LOCAL_NMEA_SERVER))
        }}
        controllerScope.launch{localNmeaServerRuntime.status.collect{value->_ui.update{it.copy(localNmeaServerRuntime=value)}}}
        // 当前产品不加载声纳历史网格；正常 NMEA 水深仍由 VesselDataHub 提供。
        controllerScope.launch{linzDepthRepository.state.collect{value->_ui.update{it.copy(linzDepth=value)};refreshDepthUi()}}
        controllerScope.launch{linzDepthRepository.diagnostics.collect{value->_ui.update{it.copy(linzDepthDiagnostics=value)}}}
        controllerScope.launch{backupManager.state.collect{value->_ui.update{it.copy(backup=value)}}}
        controllerScope.launch{runtimeDiagnostics.state.collect{value->_ui.update{it.copy(runtimeDiagnostics=value)}}}
        controllerScope.launch{runtimeResources.state.collect{value->_ui.update{it.copy(runtimeResources=value)}}}
        controllerScope.launch{incidentLogger.recent.collect{value->_ui.update{it.copy(incidents=value)}}}
        controllerScope.launch{supportBundleManager.state.collect{value->_ui.update{it.copy(supportBundle=value)}}}
        controllerScope.launch{offlineMapRepository.state.collect{value->_ui.update{it.copy(offlineMap=value)}}}
        controllerScope.launch{combine(acceptedPosition.state.map{it.acceptedFix}.distinctUntilChanged(),prefs.settings.map{it.showLinzDepthReference}.distinctUntilChanged()){fix,enabled->fix to enabled}.conflate().collect{(fix,enabled)->delay(2_000);if(enabled&&fix?.valid==true)linzDepthRepository.refresh(fix.latitude,fix.longitude)}}
        controllerScope.launch{while(true){refreshDepthUi();refreshWatchSafety();refreshAnchorageApproach();delay(1_000)}}
        controllerScope.launch{while(true){refreshStorageHealth();delay(30_000)}}
        val buildIdentity=com.yokuli.anchorwatch.platform.HostBuildIdentity.read(app)
        incidentLogger.record(
            "app",
            "MARINE_CONTROLLER_STARTED",
            details=mapOf(
                "versionName" to buildIdentity.appVersionName,
                "versionCode" to buildIdentity.appVersionCode,
                "gitSha" to buildIdentity.gitSha,
                "gitBranch" to buildIdentity.gitBranch,
                "gitDirty" to buildIdentity.gitDirty,
                "buildTimestampUtc" to buildIdentity.timestampUtc,
                "buildChannel" to buildIdentity.channel,
                "buildFlavor" to buildIdentity.flavor,
                "buildInCi" to buildIdentity.inCi,
                "databaseSchemaVersion" to BuildConfig.DATABASE_SCHEMA_VERSION,
            ),
        )
    }

    private companion object {
        const val UI_POSITION_FRAME_MILLIS=250L
        const val MAX_ACTIVE_TRAIL_POINTS=4_800
        const val PHONE_HEADING_ALIGNMENT_FRESH_MILLIS=2_000L
        const val NMEA_HEADING_ALIGNMENT_FRESH_MILLIS=3_000L
    }

    private fun refreshWatchSafety(){
        val state=_ui.value
        val nowElapsed=android.os.SystemClock.elapsedRealtime()
        val lockedSource=state.active?.positionSource?.let{runCatching{GpsDataSource.valueOf(it)}.getOrNull()}
        val effectiveSource=lockedSource?:NewAnchorPositionSourcePolicy.resolve(state.settings.gpsDataSource,state.settings.demoMode)
        val effectiveFix=when(effectiveSource){GpsDataSource.NONE->null;GpsDataSource.NMEA->state.nmeaFix;GpsDataSource.SYSTEM->state.systemFix;GpsDataSource.DEMO->if(state.active==null)state.systemFix else state.fix}
        val report=WatchPreflightEvaluator.evaluate(WatchSafetyInput(
            nowElapsed=nowElapsed,nowWall=System.currentTimeMillis(),settings=state.settings.copy(gpsDataSource=effectiveSource),
            selectedFix=effectiveFix,nmeaConnection=state.connection,device=safetyProbe.snapshot(),sonar=state.sonarRecorder,nmeaConnectionStartedElapsedRealtime=state.nmeaConnectionStartedElapsed,
        ))
        _ui.update{it.copy(watchSafety=report)}
    }

    private fun refreshAnchorageApproach(nowElapsed:Long=android.os.SystemClock.elapsedRealtime()){
        val state=_ui.value
        if(state.active!=null&&selectedApproachClusterId!=null){selectedApproachClusterId=null;selectedApproachMemberIds=emptySet();gisApproachTarget=null;phoneHeadingRepository.setApproachDemand(false)}
        val accepted=state.fix?.takeIf{
            it.valid&&state.positionHealth!=com.yokuli.anchorwatch.domain.model.PositionHealth.GPS_LOST
        }
        // HDT/HDG/VHW are independent instrument sentences. They must update
        // approach guidance even when no GGA/RMC sentence arrives to create a
        // new NavigationFix.
        val physicalNmeaHeading=state.vesselData.headingTrueDegrees.takeIf{it.sourceClass==com.yokuli.anchorwatch.domain.vessel.VesselSourceClass.BOAT_NMEA&&it.value!=null}?.let{it.value!! to (it.receivedElapsedRealtime?:0L)}
        val physicalNmeaHeadingFresh=physicalNmeaHeading?.second?.let{
            nowElapsed-it in 0L..ApproachDirectionPolicy.FRESH_MILLIS
        }==true
        val trustedNmeaCourse=state.trustedNmeaCourse?.takeIf{it.isFresh(nowElapsed)}
        // Physical HDT/HDG is an instrument stream and remains usable when the
        // same server has no GPS fix. Trusted COG still has its own fresh-fix
        // and speed gates, so this does not turn stale position into heading.
        val vesselHeadingAvailable=ApproachDirectionPolicy.vesselModeAvailable(
            connection=state.connection,
            physicalHeadingFresh=physicalNmeaHeadingFresh,
            trustedCourseAvailable=trustedNmeaCourse!=null,
        )
        val headingMode=if(state.approachHeadingMode==ApproachHeadingMode.VESSEL&&!vesselHeadingAvailable)ApproachHeadingMode.PHONE else state.approachHeadingMode
        val approach=AnchorageApproachEngine.evaluate(
            clusters=availableApproachClusters(),
            selectedClusterId=selectedApproachClusterId,
            positionLatitude=accepted?.latitude,
            positionLongitude=accepted?.longitude,
        ){bearing->
            ApproachDirectionPolicy.resolve(
                nowElapsed=nowElapsed,
                targetBearingDegrees=bearing,
                nmeaTrueHeadingDegrees=physicalNmeaHeading?.first,
                nmeaHeadingReceivedElapsed=physicalNmeaHeading?.second,
                cogTrueDegrees=trustedNmeaCourse?.trueDegrees,
                sogKnots=trustedNmeaCourse?.sogKnots,
                cogReceivedElapsed=trustedNmeaCourse?.receivedElapsedRealtime,
                // Approach guidance is presentation, not persisted centre-learning
                // evidence. It must follow the live rotation vector while the phone
                // is moving; the integrity-gated value remains exclusive to the
                // accepted-position/estimator path.
                phoneTrueHeadingDegrees=state.phoneHeading.liveTrueHeadingDegrees,
                phoneHeadingTrusted=state.phoneHeading.receivedElapsedRealtime?.let{nowElapsed-it in 0L..1_500L}==true,
                preferredMode=headingMode,
                cogTrustedBySourcePolicy=trustedNmeaCourse!=null,
            )
        }
        val allDistances=if(accepted==null)emptyList() else AnchorageNearbyPolicy.distances(
            accepted.latitude,accepted.longitude,state.anchorageClusters,
        )
        val promptIds=anchorageNearbyTracker.update(
            allDistances,
            automaticPromptEnabled=state.active==null&&selectedApproachClusterId==null,
        )
        val prompt=allDistances.filter{it.cluster.id in promptIds}
        _ui.update{it.copy(anchorageApproach=approach,nearbyAnchoragePrompt=prompt,approachHeadingMode=headingMode,vesselApproachHeadingAvailable=vesselHeadingAvailable)}
    }

    private fun refreshStorageHealth()=controllerScope.launch{
        val value=storageHealthRepository.snapshot();_ui.update{it.copy(storageHealth=value)}
    }

    private fun observePoints(session:AnchorSessionEntity?){
        val epochStartedAt=session?.let{it.estimationEpochStartedAt?:it.startedAt}
        if(pointsJob?.isActive==true&&observedSessionId==session?.id&&observedEstimationEpochStartedAt==epochStartedAt)return
        pointsJob?.cancel()
        observedSessionId=session?.id
        observedEstimationEpochStartedAt=epochStartedAt
        if(session==null){_ui.update{it.copy(points=emptyList(),activeLearningPointCount=0)};return}
        // AnchorWatchRuntime owns centre estimation. Compose observes only the
        // bounded render trail. Seed the full count once, then extend it from
        // monotonically increasing row IDs so a long watch does not rerun COUNT
        // over its entire history for every one-second fix.
        pointsJob=controllerScope.launch{
            val baseline=dao.pointCountSnapshotSince(session.id,requireNotNull(epochStartedAt))
            var count=baseline.pointCount
            var lastCountedId=baseline.maxPointId
            dao.recentPoints(session.id,MAX_ACTIVE_TRAIL_POINTS).collect{points->
                val newPoints=points.count{it.id>lastCountedId&&it.timestamp>=epochStartedAt}
                count+=newPoints
                lastCountedId=maxOf(lastCountedId,points.maxOfOrNull{it.id}?:lastCountedId)
                _ui.update{it.copy(points=points,activeLearningPointCount=count)}
            }
        }
    }

    private fun observeSonarSamples(surveyId:Long?){
        _ui.update{it.copy(selectedSonarSurveyId=surveyId)}
        if(sonarSamplesJob?.isActive==true&&observedSonarSurveyId==surveyId)return
        sonarSamplesJob?.cancel();observedSonarSurveyId=surveyId
        if(surveyId==null){_ui.update{it.copy(sonarSamples=emptyList(),sonarGrid=SonarGrid.build(emptyList()))};return}
        val scope=if(surveyId==CORRECTED_SONAR_HISTORY_ID)SonarGridScope.CORRECTED_HISTORY else SonarGridScope.SURVEY
        val scopeId=if(surveyId==CORRECTED_SONAR_HISTORY_ID)SonarGridScope.CORRECTED_HISTORY_ID else surveyId
        sonarSamplesJob=controllerScope.launch{
            var grid=SonarGrid.fromPersisted(sonarDao.gridCellsNow(scope,scopeId))
            _ui.update{it.copy(sonarSamples=emptyList(),sonarGrid=grid,sonarGridVersion=it.sonarGridVersion+1,sonarGridChangedCells=emptySet())};refreshDepthUi()
            sonarGridUpdater.changes.collect{change->
                if(change.scopeType!=scope||change.scopeId!=scopeId)return@collect
                if(change.reload){grid=SonarGrid.fromPersisted(sonarDao.gridCellsNow(scope,scopeId));_ui.update{it.copy(sonarGrid=grid,sonarGridVersion=it.sonarGridVersion+1,sonarGridChangedCells=emptySet())}}
                else{val x=change.gridX?:return@collect;val y=change.gridY?:return@collect;grid.applyCell(x,y,change.cell);_ui.update{it.copy(sonarGrid=grid,sonarGridVersion=it.sonarGridVersion+1,sonarGridChangedCells=if(it.page==0)it.sonarGridChangedCells+(x to y)else emptySet())}}
                refreshDepthUi()
            }
        }
    }

    fun consumeSonarGridChanges(version:Long)=_ui.update{state->if(state.sonarGridVersion==version)state.copy(sonarGridChangedCells=emptySet())else state}

    private fun refreshDepthUi(nowElapsed:Long=android.os.SystemClock.elapsedRealtime()){
        _ui.update{state->
            val recorder=state.sonarRecorder;val age=recorder.lastDepthReceivedElapsedRealtime?.let{(nowElapsed-it).coerceAtLeast(0L)};val hold=com.yokuli.anchorwatch.domain.sonar.SonarDepthHoldPolicy.evaluate(recorder.lastDepthMeters!=null,age?:Long.MAX_VALUE,recorder.depthTravelledMeters).state
            val inspection=if(state.settings.showPersonalMapReference)state.fix?.let{state.sonarGrid.inspect(it.latitude,it.longitude)}else null
            val selectedSurvey=state.selectedSonarSurveyId?.takeIf{it!=CORRECTED_SONAR_HISTORY_ID}?.let{id->state.sonarSurveys.firstOrNull{it.id==id}}
            state.copy(depthUi=DepthUiState(
                liveDepthMeters=recorder.lastDepthMeters,liveDepthReference=recorder.lastDepthReference,liveDepthAgeMillis=age,liveDepthHoldState=hold,
                correctedDepthMeters=recorder.lastDepthMeters.takeIf{recorder.lastDepthIsChartDatum},linz=state.linzDepth,
                personalMapDepthMeters=inspection?.depthMeters,personalMapMeasured=inspection?.measured,personalMapSamples=inspection?.sampleCount,personalMapUncertaintyMeters=inspection?.uncertaintyMeters,
                personalSurveyName=selectedSurvey?.name,personalSurveyStartedAt=selectedSurvey?.startedAt,
            ))
        }
    }

    fun validateProfile(profile:ConnectionProfile)=endpointPreflight.validate(profile,_ui.value.settings.nmeaSharingEnabled,_ui.value.settings.nmeaSharingPort)

    fun saveAndConnect(profile:ConnectionProfile)=controllerScope.launch{
        if(_ui.value.connection !in setOf(NmeaConnectionState.DISCONNECTED,NmeaConnectionState.ERROR)){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.WARNING,"NMEA input is already running. Stop it before replacing the RX endpoint."))}
            return@launch
        }
        endpointPreflight.validate(profile,_ui.value.settings.nmeaSharingEnabled,_ui.value.settings.nmeaSharingPort)?.let{message->_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,message))};return@launch}
        // RX and TX are separate products. An incomplete or stopped output
        // route must never block, mutate or be saved by an input connection.
        // Do not preflight with a disposable socket and then reconnect. Many
        // marine gateways allow only one reader or release the previous client
        // slowly, which made the test socket consume the stream while the real
        // App socket appeared quiet. The long-lived RX transport is the only
        // endpoint connection; traffic/fix health continues asynchronously.
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.TESTING,"Connecting to NMEA input…"))}
        nmeaManualDisconnectRepository.clear()
        nav.clearUserDisconnectLatch()
        if(!nav.connect(profile)){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"The NMEA input connection could not be started. Stop the previous input generation before trying again."))}
            return@launch
        }
        val generation=nav.connectionGeneration()
        // TCP open and NMEA traffic are separate states. Persist the formal RX
        // endpoint immediately; CONNECTED_NO_DATA remains a successful live
        // connection and the normal state/health cards continue observing it.
        prefs.saveConnectionProfile(profile)
        observeNmeaConnectionOutcome(generation)
    }

    private suspend fun observeNmeaConnectionOutcome(generation:Long){
        val outcome=withTimeoutOrNull(7_000L){
            combine(nav.connectionState,nav.transportDiagnostics){connection,diagnostics->connection to diagnostics}.first{(connection,diagnostics)->
                diagnostics.connectionGeneration>=generation&&(
                    connection in setOf(NmeaConnectionState.CONNECTED,NmeaConnectionState.CONNECTED_NO_DATA,NmeaConnectionState.CONNECTED_NO_FIX,NmeaConnectionState.STALE,NmeaConnectionState.ERROR)||
                    (connection==NmeaConnectionState.RECONNECTING&&diagnostics.lastDisconnectReason!=null)
                )
            }
        }
        when{
            outcome==null->_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.WARNING,"The RX connection is still opening. Do not tap Connect again; live transport diagnostics will keep updating."))}
            outcome.first in setOf(NmeaConnectionState.CONNECTED,NmeaConnectionState.CONNECTED_NO_DATA,NmeaConnectionState.CONNECTED_NO_FIX,NmeaConnectionState.STALE)->_ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
            outcome.first==NmeaConnectionState.RECONNECTING->_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.WARNING,"${outcome.second.lastFailureCategory?:"NMEA connection failed"}: ${outcome.second.lastDisconnectReason?:"unknown transport error"}. A single protected retry is scheduled; do not reconnect repeatedly."))}
            else->_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"${outcome.second.lastFailureCategory?:"NMEA connection failed"}: ${outcome.second.lastDisconnectReason?:"unknown transport error"}"))}
        }
    }

    fun disconnect(){
        if(_ui.value.active?.paused==false&&_ui.value.active?.positionSource==GpsDataSource.NMEA.name){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"This active anchor session is locked to NMEA. Pause the watch before disconnecting, or lift the anchor to end the session."))}
            return
        }
        if(runtimeResources.snapshot().needsNmeaTransport){
            val owners=runtimeResources.snapshot().nmeaOwners.joinToString{it.name.replace('_',' ')}
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"NMEA is still required by ${owners.ifBlank{"a background feature"}}. Review and stop those features before disconnecting."))}
            return
        }
        // The dependency review above is the safety gate. Once it passes, this
        // explicit user action must clear every stale owner latch and close RX;
        // otherwise an old background claim can make Disconnect appear broken.
        controllerScope.launch{nmeaManualDisconnectRepository.suppress();nav.disconnectAll()}
    }
    fun reconnectNmea()=controllerScope.launch{
        val state=_ui.value.connection
        if(state in setOf(NmeaConnectionState.CONNECTING,NmeaConnectionState.RECONNECTING))return@launch
        nmeaManualDisconnectRepository.clear()
        nav.clearUserDisconnectLatch()
        if(!nav.reconnect(_ui.value.settings.profile)){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.WARNING,"Reconnect is already running or is inside the 15-second server-protection cooldown."))}
            return@launch
        }
        val generation=nav.connectionGeneration()
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.TESTING,"Closing the previous generation and opening one protected RX socket…"))}
        observeNmeaConnectionOutcome(generation)
    }
    fun stopActiveWatchAndDisconnect(){
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.STOP_WATCH_AND_DISCONNECT))
    }
    fun stopNmeaDependenciesAndDisconnect(){
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.STOP_NMEA_DEPENDENCIES_AND_DISCONNECT))
    }
    fun continueTripWithPhoneAndDisconnect(){
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.CONTINUE_TRIP_WITH_PHONE_AND_DISCONNECT))
    }
    fun clearConnectionAttempt()=_ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
    fun dismissRuntimeFeedback(){
        val id=_ui.value.runtimeDiagnostics.lastUserFeedback?.id?:return
        consumeRuntimeFeedback(id)
    }
    /** 按 ID 消费已保存到系统通知中心的事件，不会误删同时到达的新反馈。 */
    fun consumeRuntimeFeedback(id:Long){
        // Dismiss the event at its process-wide owner as well as immediately
        // hiding it in this UI frame. Recreating MainActivity must not bring
        // the same already-acknowledged banner back.
        runtimeDiagnostics.dismissUserFeedback(id)
        _ui.update{state->state.copy(dismissedRuntimeFeedbackId=maxOf(state.dismissedRuntimeFeedbackId,id))}
    }
    /** 新偏好入口只传本次修改的字段；由仓库在 DataStore.edit 中合并，不保存过时整份快照。 */
    fun setLanguage(language: AppLanguage) = controllerScope.launch { prefs.setLanguage(language) }
    fun setVesselGeometry(lengthMeters: Double, bowRollerHeightMeters: Double, antennaToBowMeters: Double): Job {
        require(lengthMeters.isFinite() && lengthMeters > 0.0) { "Invalid vessel length" }
        require(bowRollerHeightMeters.isFinite() && bowRollerHeightMeters >= 0.0) { "Invalid bow roller height" }
        require(antennaToBowMeters.isFinite() && antennaToBowMeters >= 0.0) { "Invalid antenna offset" }
        return controllerScope.launch { prefs.setVesselGeometry(lengthMeters, bowRollerHeightMeters, antennaToBowMeters) }
    }
    fun setVesselIdentity(name: String, draftMeters: Double?): Job {
        require(draftMeters == null || (draftMeters.isFinite() && draftMeters >= 0.0)) { "Invalid vessel draft" }
        return controllerScope.launch { vesselSettingsRepository.setVesselIdentity(name, draftMeters) }
    }
    fun setAlarmSound(sound: AlarmSound, customUri: String? = null): Job {
        require(sound == AlarmSound.SYSTEM_ALARM || sound == AlarmSound.CUSTOM) { "Unsupported alarm sound" }
        require(sound != AlarmSound.CUSTOM || !customUri.isNullOrBlank()) { "Custom alarm sound needs an audio file" }
        return controllerScope.launch { prefs.setAlarmSound(sound, customUri) }
    }
    fun setAlarmSnoozeMinutes(minutes: Int): Job {
        require(minutes in 1..30) { "Invalid alarm snooze interval" }
        return controllerScope.launch { prefs.setAlarmSnoozeMinutes(minutes) }
    }
    fun setInstrumentLayout(layout: List<InstrumentTileId>) = controllerScope.launch {
        vesselSettingsRepository.setInstrumentLayout(layout)
    }
    /** 仅供未迁移的旧页面与兼容调用；新产品端口不暴露整份设置写入。 */
    fun updateSettings(settings:AppSettings){
        val current=_ui.value
        var safe=if(current.activeSonarSurvey!=null&&settings.sounderOffsetMeters!=current.settings.sounderOffsetMeters){
            controllerScope.launch{incidentLogger.record("sonar","OFFSET_CHANGE_REJECTED_ACTIVE_SURVEY")}
            settings.copy(sounderOffsetMeters=current.settings.sounderOffsetMeters)
        }else settings
        val proxyActive=GpsSourceSafety.requiresStopAction(current.settings.mockEnabled,current.mockGps.state)
        if(proxyActive&&(safe.enhancedMock!=current.settings.enhancedMock||safe.mockHz!=current.settings.mockHz)){
            controllerScope.launch{incidentLogger.record("gps_proxy","LIVE_CONFIGURATION_CHANGE_REJECTED")}
            safe=safe.copy(enhancedMock=current.settings.enhancedMock,mockHz=current.settings.mockHz)
        }
        if(safe.keepWifiAwake!=current.settings.keepWifiAwake)runtimeResources.updateKeepWifiAwake(safe.keepWifiAwake)
        _ui.update{it.copy(settings=safe)};controllerScope.launch{prefs.save(safe)}
    }
    fun completeOnboarding()=updateSettings(_ui.value.settings.copy(onboardingCompleted=true))
    fun setSonarLayerEnabled(enabled:Boolean,acceptDisclaimer:Boolean=false){
        val state=_ui.value
        updateSettings(state.settings.copy(sonarLayerEnabled=enabled,sonarDisclaimerAccepted=state.settings.sonarDisclaimerAccepted||acceptDisclaimer))
    }
    fun exportBackup(uri:Uri)=controllerScope.launch{backupManager.export(uri)}
    fun restoreBackup(uri:Uri)=controllerScope.launch{backupManager.restore(uri)}
    fun clearBackupResult()=backupManager.clearResult()
    fun importOfflineMap(uri:Uri)=controllerScope.launch{
        val outcome=offlineMapRepository.import(uri)
        val result=outcome.getOrNull()
        if(result!=null){
            val current=_ui.value.settings
            // Import owns files, not map visibility. A first import remains
            // unselected until the user chooses it in Map -> Layers; replacing
            // an already selected chart preserves that explicit preference.
            prefs.save(current.copy(offlineMapName=result.info.name,offlineMapAttribution=result.info.attribution))
            incidentLogger.record("offline_map","IMPORTED",details=mapOf("name" to result.info.name,"tiles" to result.info.tileCount,"bytes" to result.info.sizeBytes))
            refreshStorageHealth()
        }else{val error=outcome.exceptionOrNull();_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,error?.message?:"Offline map import failed."))}}
    }
    fun removeOfflineMap()=controllerScope.launch{
        val outcome=offlineMapRepository.remove()
        if(outcome.isSuccess){val current=_ui.value.settings;prefs.save(current.copy(offlineMapEnabled=false,offlineMapName=null,offlineMapAttribution=null));incidentLogger.record("offline_map","REMOVED");refreshStorageHealth()}
        else _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,outcome.exceptionOrNull()?.message?:"Offline map removal failed."))}
    }
    fun setOfflineMapEnabled(enabled:Boolean){val current=_ui.value.settings;updateSettings(current.copy(offlineMapEnabled=enabled&&_ui.value.offlineMap.installed))}
    fun createOfflineMapProvider()=offlineMapRepository.provider()
    fun exportSupportBundle(uri:Uri)=controllerScope.launch{supportBundleManager.export(uri)}
    fun clearSupportBundleResult()=supportBundleManager.clearResult()
    fun clearIncidentLog()=controllerScope.launch{
        if(_ui.value.supportBundle.running){
            incidentLogger.record("storage","INCIDENT_LOG_CLEAR_REJECTED_SUPPORT_EXPORT")
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Wait for the support bundle export to finish before clearing its incident evidence."))}
            return@launch
        }
        storageHealthRepository.clearIncidentLog();refreshStorageHealth()
    }
    fun clearRebuildableCaches()=controllerScope.launch{
        val state=_ui.value
        if(state.activeSonarSurvey!=null||state.backup.running||state.supportBundle.running){
            incidentLogger.record("storage","REBUILDABLE_CACHE_CLEAR_REJECTED",details=mapOf("sonarActive" to (state.activeSonarSurvey!=null),"backupRunning" to state.backup.running,"supportExportRunning" to state.supportBundle.running))
            return@launch
        }
        storageHealthRepository.clearRebuildableCaches();incidentLogger.record("storage","REBUILDABLE_CACHES_CLEARED");refreshStorageHealth()
    }
    fun refreshStorage()=refreshStorageHealth()
    fun confirmAlarmAudible(){
        val confirmedAt = System.currentTimeMillis()
        controllerScope.launch {
            prefs.setAlarmAudibleConfirmedAt(confirmedAt)
            incidentLogger.record("alarm", "AUDIBLE_TEST_CONFIRMED")
        }
    }
    fun setNmeaSharing(enabled:Boolean,port:Int){
        val safePort=port.takeIf{it in 1024..65535}
        if(safePort==null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"NMEA Sharing port must be between 1024 and 65535."))};return}
        controllerScope.launch{
            val current=_ui.value.localNmeaServerSettings
            localNmeaServerSettingsRepository.saveConfiguration(current.copy(port=safePort,configured=true,serverRequested=false))
            if(enabled)localNmeaServerSettingsRepository.requestStart() else localNmeaServerSettingsRepository.requestStop()
            ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.REFRESH_LOCAL_NMEA_SERVER))
        }
    }

    fun saveLocalNmeaServerConfiguration(port:Int,includePressure:Boolean,includeDerivedWind:Boolean)=controllerScope.launch{
        if(_ui.value.localNmeaServerSettings.serverRequested){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Stop the Phone NMEA service before changing its listening port or feed."))}
            return@launch
        }
        if(port !in 1024..65535){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Phone NMEA service port must be between 1024 and 65535."))}
            return@launch
        }
        localNmeaServerSettingsRepository.saveConfiguration(_ui.value.localNmeaServerSettings.copy(port=port,includePressure=includePressure,includeDerivedWind=includeDerivedWind,configured=true,serverRequested=false))
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
    }

    fun startLocalNmeaServer()=controllerScope.launch{
        val state=_ui.value
        fun fail(message:String){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,message))}}
        if(!state.localNmeaServerSettings.configured||state.localNmeaServerSettings.port !in 1024..65535){
            fail("Save a valid Phone NMEA service listening port first.")
            return@launch
        }
        localNmeaServerSettingsRepository.requestStart()
        // Reflect the lease synchronously so one physical tap cannot enqueue
        // several foreground-service starts while DataStore/Flow catches up.
        _ui.update{it.copy(localNmeaServerSettings=it.localNmeaServerSettings.copy(serverRequested=true),connectionAttempt=ConnectionAttempt())}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.REFRESH_LOCAL_NMEA_SERVER))
    }

    /** 保存本机服务的完整发布策略；运行中不能偷偷改变发给客户的数据。 */
    fun saveLocalNmeaPublicationPolicy(port:Int,feed:com.yokuli.anchorwatch.data.nmea.NmeaFeed,capabilities:Set<String>,forwardFrom:Set<String>)=controllerScope.launch{
        if(_ui.value.localNmeaServerSettings.serverRequested||port !in 1024..65535)return@launch
        localNmeaServerSettingsRepository.saveConfiguration(_ui.value.localNmeaServerSettings.copy(port=port,configured=true,feed=feed,capabilities=capabilities,forwardFrom=forwardFrom,includePressure="pressure" in capabilities))
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
    }

    fun stopLocalNmeaServer()=controllerScope.launch{
        localNmeaServerSettingsRepository.requestStop();_ui.update{it.copy(localNmeaServerSettings=it.localNmeaServerSettings.copy(serverRequested=false),connectionAttempt=ConnectionAttempt())}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.REFRESH_LOCAL_NMEA_SERVER))
    }

    /** Emergency master stop. Product-specific Stop buttons remain independent,
     * while this action guarantees that "stop all sharing" revokes both live
     * leases before one foreground command performs both hard-stop paths. */
    fun stopAllNmeaSharing()=controllerScope.launch{
        outputSettingsRepository.requestStop()
        localNmeaServerSettingsRepository.requestStop()
        _ui.update{it.copy(
            outputSettings=it.outputSettings.copy(publicationEnabled=false),
            localNmeaServerSettings=it.localNmeaServerSettings.copy(serverRequested=false),
            connectionAttempt=ConnectionAttempt(),
        )}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.STOP_ALL_NMEA_SHARING))
    }
    fun deleteHistorySession(session:AnchorSessionEntity){if(session.active)return;controllerScope.launch{dao.deleteCompletedSession(session.id)}}
    fun setMapType(mapType:Int){val value=mapType.takeIf{it in 1..3}?:1;val updated=_ui.value.settings.copy(mapType=value);_ui.update{it.copy(settings=updated)};controllerScope.launch{prefs.save(updated)}}
    fun setGpsDataSource(source:GpsDataSource)=switchGpsDataSource(source)
    fun switchGpsDataSource(source:GpsDataSource)=controllerScope.launch{
        if(source==GpsDataSource.NMEA&&!nav.hasOpenTransport()){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Connect a NMEA input before choosing NMEA position."))};return@launch
        }
        if(source==GpsDataSource.SYSTEM&&!systemLocation.hasPermission()){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Precise location permission is required."))};return@launch
        }
        if(source==GpsDataSource.SYSTEM&&GpsSourceSafety.blocksSystemGps(_ui.value.settings.mockEnabled,mockManager.status.value.state)){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Stop the Android mock GPS proxy before using phone GNSS."))};return@launch
        }
        if(source==GpsDataSource.NMEA&&_ui.value.vesselSettings.metricSourcePins["POSITION_CONNECTION"]==null){
            val inputs=nav.connections.value.filter{it.requested&&it.spec.receive&&nav.isConnectionOpen(it.spec.id)}
            if(inputs.size!=1){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Choose the position input in NMEA connections."))};return@launch}
            selectNmeaPositionConnection(inputs.single().spec.id);return@launch
        }
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction("OS_POSITION_SOURCE").putExtra("source",source.name))
    }
    fun setDemoMode(enabled:Boolean)=controllerScope.launch{
        val current=_ui.value.settings
        if(_ui.value.active!=null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Lift the current anchor session before changing Demo mode."))};return@launch}
        if(_ui.value.activeTrip!=null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"End the current Trip Watch session before changing Demo mode."))};return@launch}
        if(_ui.value.activeSonarSurvey!=null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Stop and save the current sonar survey before changing Demo mode."))};return@launch}
        if(enabled&&GpsSourceSafety.blocksSystemGps(current.mockEnabled,mockManager.status.value.state)){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Disable the global NMEA GPS proxy before enabling Demo mode. Demo needs an independent System GPS origin."))};return@launch}
        demoLocation.stop()
        prefs.save(current.copy(demoMode=enabled,gpsDataSource=if(enabled)GpsDataSource.DEMO else GpsDataSource.SYSTEM,mockEnabled=false))
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
    }
    fun updateVesselDataSettings(value:VesselDataSettings)=controllerScope.launch{vesselSettingsRepository.save(value)}
    fun createTripDashboard(title:String)=controllerScope.launch{tripDashboardRepository.create(title)}
    fun saveTripDashboard(value:TripDashboard)=controllerScope.launch{tripDashboardRepository.save(value)}
    fun deleteTripDashboard(id:String)=controllerScope.launch{tripDashboardRepository.delete(id)}
    fun reorderTripDashboards(ids:List<String>)=controllerScope.launch{tripDashboardRepository.reorder(ids)}
    fun setTripLiveDisplayActive(active:Boolean){runtimeResources.set(RuntimeOwner.VESSEL_HUB_UI,if(active)RuntimeRequirement(needsSystemLocation=false,needsPhoneMotion=true,needsPhoneHeading=true,needsPhonePressure=true)else null)}
    fun confirmTripAttitudeFrame(axis:DeviceBowAxis)=controllerScope.launch{
        if(_ui.value.activeTrip?.paused==true){_ui.update{it.copy(vesselCalibrationFeedback="Resume the trip before confirming a new attitude segment.")};return@launch}
        runtimeResources.set(RuntimeOwner.VESSEL_HUB_UI,RuntimeRequirement(needsSystemLocation=false,needsPhoneMotion=true,needsPhoneHeading=true,needsPhonePressure=true))
        delay(500)
        val saved=vesselAttitudeRepository.calibrate(axis)
        if(saved){
            vesselAttitudeRepository.setMounted(true)
            _ui.update{it.copy(vesselCalibrationFeedback="Trip attitude frame confirmed.")}
            if(_ui.value.activeTrip!=null)ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.CONFIRM_TRIP_ATTITUDE_FRAME))
        }else _ui.update{it.copy(vesselCalibrationFeedback="No rotation-vector sample is available on this phone.")}
        // TripWatchPage owns the preview lease while visible; a running Trip
        // takes over through its own runtime owner. Do not tear down the shared
        // sensor preview between confirmation and the Start command.
    }
    /** Source-compatible entry point retained for old UI/tests. */
    fun calibrateVesselMount(axis:DeviceBowAxis)=confirmTripAttitudeFrame(axis)
    fun setPhoneVesselMounted(mounted:Boolean)=controllerScope.launch{
        if(mounted&&_ui.value.vesselMountCalibration.calibratedAt<=0L){
            _ui.update{it.copy(vesselCalibrationFeedback="Confirm the phone-to-vessel attitude frame during Trip Watch first.")}
            return@launch
        }
        if(mounted&&!_ui.value.vesselMountCalibration.attitudeFrameConfirmed){
            _ui.update{it.copy(vesselCalibrationFeedback="The previous attitude segment was invalidated. Secure the phone in its mount and confirm the current installation as zero.")}
            return@launch
        }
        vesselAttitudeRepository.setMounted(mounted)
        _ui.update{it.copy(vesselCalibrationFeedback=if(mounted)"Trip attitude capture resumed." else "Trip attitude capture paused. Heading, GPS and pressure continue.")}
    }
    fun alignPhoneHeadingToBow()=controllerScope.launch{
        val state=_ui.value
        val now=android.os.SystemClock.elapsedRealtime()
        val phoneFresh=state.phoneHeading.receivedElapsedRealtime?.let{now-it in 0L..PHONE_HEADING_ALIGNMENT_FRESH_MILLIS}==true
        val phoneAvailable=state.phoneHeading.liveTrueHeadingDegrees!=null||state.phoneHeading.liveMagneticHeadingDegrees!=null
        if(!phoneFresh||!phoneAvailable){
            _ui.update{it.copy(vesselCalibrationFeedback=com.yokuli.anchorwatch.localization.localized(it.settings.appLanguage,"No fresh phone compass reading is available. Keep this page open, move away from magnetic interference, then try again.","当前没有新鲜的手机罗盘数据。请保持此页打开、远离磁场干扰后再试。"))}
            return@launch
        }
        vesselAttitudeRepository.alignHeading(0.0)
        _ui.update{it.copy(vesselCalibrationFeedback=com.yokuli.anchorwatch.localization.localized(it.settings.appLanguage,"Aligned now. The top of the phone is the vessel bow direction; you can repeat this at any time.","已重新对齐。现在手机顶部方向就是船艏方向；以后可随时再次操作。"))}
    }
    fun alignPhoneHeadingToNmea()=controllerScope.launch{
        val state=_ui.value
        val now=android.os.SystemClock.elapsedRealtime()
        val phoneFresh=state.phoneHeading.receivedElapsedRealtime?.let{now-it in 0L..PHONE_HEADING_ALIGNMENT_FRESH_MILLIS}==true
        val nmeaTrue=state.nmeaInstruments.headingTrue?.takeIf{now-it.second in 0L..NMEA_HEADING_ALIGNMENT_FRESH_MILLIS}?.first
        val nmeaMagnetic=state.nmeaInstruments.headingMagnetic?.takeIf{now-it.second in 0L..NMEA_HEADING_ALIGNMENT_FRESH_MILLIS}?.first
        val match=if(phoneFresh && phoneCompassReadyForAlignment())PhoneHeadingAlignmentPolicy.matchLiveReference(
            phoneTrueDegrees=state.phoneHeading.liveVesselTrueHeadingDegrees?.minus(state.vesselMountCalibration.headingAlignmentOffsetDegrees)?.let{(it+360.0)%360.0},
            phoneMagneticDegrees=state.phoneHeading.liveVesselMagneticHeadingDegrees?.minus(state.vesselMountCalibration.headingAlignmentOffsetDegrees)?.let{(it+360.0)%360.0},
            vesselTrueDegrees=nmeaTrue,
            vesselMagneticDegrees=nmeaMagnetic,
        )else null
        if(match==null){
            _ui.update{it.copy(vesselCalibrationFeedback=com.yokuli.anchorwatch.localization.localized(it.settings.appLanguage,"A fresh Phone and NMEA heading with the same north reference is required. Nothing was changed.","需要同时取得采用相同北向基准的新鲜手机艏向与 NMEA 艏向；本次没有修改。"))}
            return@launch
        }
        try { vesselAttitudeRepository.alignHeading(match.offsetDegrees) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            _ui.update { it.copy(vesselCalibrationFeedback = "Phone calibration could not be saved. Check storage and try again.") }
            return@launch
        }
        val reference=if(match.reference==com.yokuli.anchorwatch.location.vessel.PhoneHeadingAlignmentReference.TRUE_NORTH)"true" else "magnetic"
        _ui.update{it.copy(vesselCalibrationFeedback=com.yokuli.anchorwatch.localization.localized(it.settings.appLanguage,"Aligned to the live NMEA $reference heading. You can realign again at any time.","已按实时 NMEA ${if(reference=="true")"真北" else "磁北"}艏向重新对齐；以后可随时再次操作。"))}
    }
    /** 中文：用户明确确认当前固定安装为姿态零点，船首向保持磁北/真北参考。 */
    fun confirmFixedPhoneMount() = controllerScope.launch {
        try {
            val state = _ui.value
            if (!phoneCompassReadyForAlignment()) {
                _ui.update { it.copy(vesselCalibrationFeedback = "Wait for a fresh, undisturbed phone compass reading.") }
                return@launch
            }
            if (!vesselAttitudeRepository.confirmFixedMount()) {
                _ui.update { it.copy(vesselCalibrationFeedback = "No rotation-vector sample is available on this phone.") }
                return@launch
            }
            _ui.update { it.copy(vesselCalibrationFeedback = "Phone mounting and bow alignment saved.") }
            if (state.activeTrip?.paused == false && state.phoneSensorCapabilities.attitudeAvailable) {
                ContextCompat.startForegroundService(app, Intent(app, AnchorForegroundService::class.java)
                    .setAction(AnchorForegroundService.CONFIRM_TRIP_ATTITUDE_FRAME))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            _ui.update { it.copy(vesselCalibrationFeedback = "Phone calibration could not be saved. Check storage and try again.") }
        }
    }
    /** 保存用户的角度修正；不修改姿态安装、COG 或当前数据来源。 */
    fun setPhoneHeadingAlignment(offsetDegrees: Double) = controllerScope.launch {
        if (!offsetDegrees.isFinite() || offsetDegrees !in -180.0..180.0) {
            _ui.update { it.copy(vesselCalibrationFeedback = "Enter a correction between -180 and 180 degrees.") }
            return@launch
        }
        if (!_ui.value.vesselMountCalibration.headingAligned || !phoneCompassReadyForAlignment()) {
            _ui.update { it.copy(vesselCalibrationFeedback = "Confirm the mount with a fresh compass reading before adjusting heading.") }
            return@launch
        }
        try {
            vesselAttitudeRepository.alignHeading(offsetDegrees)
            _ui.update { it.copy(vesselCalibrationFeedback = "Heading correction saved. Attitude is unchanged.") }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _ui.update { it.copy(vesselCalibrationFeedback = "Phone calibration could not be saved. Check storage and try again.") } }
    }
    fun setPhoneAttitudeAlignment(heelDegrees:Double,pitchDegrees:Double)=controllerScope.launch {
        if(!heelDegrees.isFinite()||!pitchDegrees.isFinite()||heelDegrees !in -45.0..45.0||pitchDegrees !in -45.0..45.0){
            _ui.update{it.copy(vesselCalibrationFeedback="Enter attitude corrections between -45 and 45 degrees.")};return@launch
        }
        if(!_ui.value.vesselMountCalibration.mountConfirmed){
            _ui.update{it.copy(vesselCalibrationFeedback="Confirm the fixed installation first.")};return@launch
        }
        try {
            vesselAttitudeRepository.alignAttitude(heelDegrees,pitchDegrees)
            _ui.update{it.copy(vesselCalibrationFeedback="Attitude correction saved.")}
            if(_ui.value.activeTrip?.paused==false) ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java)
                .setAction(AnchorForegroundService.CONFIRM_TRIP_ATTITUDE_FRAME))
        } catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){_ui.update{it.copy(vesselCalibrationFeedback="Phone calibration could not be saved. Check storage and try again.")}}
    }
    fun invalidateFixedPhoneMount() = controllerScope.launch {
        try {
            vesselAttitudeRepository.invalidateFixedMount()
            _ui.update { it.copy(vesselCalibrationFeedback = "Phone moved. Confirm mounting again before using its vessel heading or attitude.") }
            if (_ui.value.activeTrip != null) app.startService(Intent(app, AnchorForegroundService::class.java)
                .setAction(AnchorForegroundService.PAUSE_TRIP_ATTITUDE))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _ui.update { it.copy(vesselCalibrationFeedback = "Phone calibration could not be saved. Check storage and try again.") } }
    }
    private fun phoneCompassReadyForAlignment(): Boolean {
        val phone = _ui.value.phoneHeading
        return phone.receivedElapsedRealtime?.let { android.os.SystemClock.elapsedRealtime() - it in 0L..PHONE_HEADING_ALIGNMENT_FRESH_MILLIS } == true &&
            (phone.liveTrueHeadingDegrees != null || phone.liveMagneticHeadingDegrees != null) &&
            phone.presentationQuality in setOf(com.yokuli.anchorwatch.location.PhoneHeadingPresentationQuality.GOOD,
                com.yokuli.anchorwatch.location.PhoneHeadingPresentationQuality.LOW_ACCURACY)
    }
    fun clearVesselCalibrationFeedback()=_ui.update{it.copy(vesselCalibrationFeedback=null)}
    fun setNmeaOutputEndpoint(mode:NmeaOutputTransportMode,host:String,port:Int)=controllerScope.launch{
        val current=_ui.value.outputSettings
        val input=_ui.value.settings.profile
        if(current.publicationEnabled){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Stop NMEA output before changing its destination."))};return@launch}
        if(mode==NmeaOutputTransportMode.TCP_SERVER){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Use Phone NMEA service to host a TCP server. Phone/App boat output only writes locally-owned data into the boat network."))}
            return@launch
        }
        if(mode !in setOf(NmeaOutputTransportMode.SAME_AS_INPUT_CONNECTION,NmeaOutputTransportMode.TCP_SERVER)&&(host.isBlank()||port !in 1..65535)){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"This NMEA output destination needs a valid host and port from 1 to 65535."))}
            return@launch
        }
        val proposed=when(mode){
            NmeaOutputTransportMode.SAME_AS_INPUT_CONNECTION->current.copy(transportMode=mode,outputHost="",outputPort=input.port.takeIf{it in 1..65535}?:10110)
            NmeaOutputTransportMode.DEDICATED_TCP->NmeaOutputEndpointPolicy.tcpDestination(current,input,host,port)
            NmeaOutputTransportMode.TCP_SERVER->current
            NmeaOutputTransportMode.UDP_UNICAST,NmeaOutputTransportMode.UDP_BROADCAST->current.copy(transportMode=mode,outputHost=host.trim(),outputPort=port)
        }
        outputSettingsRepository.saveConfiguration(proposed.copy(transportConfigured=true,publicationEnabled=false))
        _ui.update{it.copy(connectionAttempt=ConnectionAttempt())}
    }
    fun setNmeaPhonePositionPublishing(enabled:Boolean)=controllerScope.launch{
        updateNmeaOutputStreams{current->current.copy(
            phonePositionEnabled=enabled,
            positionPolicy=if(enabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF,
        )}
    }
    fun setNmeaPhoneHeadingPublishing(enabled:Boolean)=controllerScope.launch{
        updateNmeaOutputStreams{it.copy(phoneHeadingEnabled=enabled,headingPolicy=if(enabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF)}
    }
    fun setNmeaPhoneRateOfTurnPublishing(enabled:Boolean)=controllerScope.launch{
        updateNmeaOutputStreams{it.copy(phoneMotionEnabled=enabled||it.phoneAttitudeEnabled,phoneRateOfTurnEnabled=enabled,rateOfTurnPolicy=if(enabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF,motionPolicy=if(enabled||it.phoneAttitudeEnabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF)}
    }
    fun setNmeaPhoneAttitudePublishing(enabled:Boolean)=controllerScope.launch{
        updateNmeaOutputStreams{it.copy(phoneMotionEnabled=enabled||it.phoneRateOfTurnEnabled,phoneAttitudeEnabled=enabled,attitudePolicy=if(enabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF,motionPolicy=if(enabled||it.phoneRateOfTurnEnabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF)}
    }
    fun setNmeaPhonePressurePublishing(enabled:Boolean)=controllerScope.launch{
        updateNmeaOutputStreams{it.copy(phonePressureEnabled=enabled,includePressure=enabled,pressurePolicy=if(enabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF)}
    }
    fun setNmeaDerivedWindPublishing(enabled:Boolean)=controllerScope.launch{
        updateNmeaOutputStreams{it.copy(includeDerivedWind=enabled,derivedWindPolicy=if(enabled)PublicationPolicy.ALWAYS else PublicationPolicy.OFF)}
    }
    fun setNmeaOutputPreset(preset:NmeaOutputPreset)=controllerScope.launch{
        updateNmeaOutputStreams{it.withPreset(preset)}
    }
    private suspend fun updateNmeaOutputStreams(transform:(NmeaDeviceOutputSettings)->NmeaDeviceOutputSettings){
        val current=_ui.value.outputSettings
        val updated=transform(current)
        outputSettingsRepository.saveConfiguration(updated)
        _ui.update{it.copy(outputSettings=updated.copy(publicationEnabled=current.publicationEnabled),connectionAttempt=ConnectionAttempt())}
        // A running publisher observes the same repository Flow, but an
        // explicit refresh also serializes this user action through the
        // foreground command actor before any subsequent Start/Stop action.
        if(current.publicationEnabled)ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.REFRESH_PHONE_SENSOR_OUTPUT))
    }
    fun startNmeaOutput()=controllerScope.launch{
        val state=_ui.value
        // Destination changes only transport ownership. Every route publishes
        // the same Phone/App-owned feed and can never republish Boat input.
        val value=NmeaOutputEndpointPolicy.automatic(state.outputSettings,state.settings.profile).copy(
            purpose=NmeaOutputPurpose.BOAT_BUS_INJECTION,
            autoStartOutput=false,
        )
        fun fail(message:String){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,message))}}
        if(!isOutputDestinationReady(value,state)){fail(outputDestinationError(value));return@launch}
        if(value!=state.outputSettings)outputSettingsRepository.saveConfiguration(value.copy(publicationEnabled=false))
        outputSettingsRepository.requestStart()
        _ui.update{it.copy(outputSettings=it.outputSettings.copy(publicationEnabled=true),connectionAttempt=ConnectionAttempt())}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.REFRESH_PHONE_SENSOR_OUTPUT))
    }
    fun stopNmeaOutput()=controllerScope.launch{
        outputSettingsRepository.requestStop();_ui.update{it.copy(outputSettings=it.outputSettings.copy(publicationEnabled=false),connectionAttempt=ConnectionAttempt())}
        // The foreground coordinator is the only production lifecycle owner.
        // Its command actor performs the hard stop and invalidates queued bytes.
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.REFRESH_PHONE_SENSOR_OUTPUT))
    }
    private fun isOutputDestinationReady(value:NmeaDeviceOutputSettings,state:MainUiState):Boolean{
        val effective=NmeaOutputEndpointPolicy.automatic(value,state.settings.profile)
        return effective.anyStreamSelected&&effective.transportConfigured&&when(effective.transportMode){
            NmeaOutputTransportMode.SAME_AS_INPUT_CONNECTION->NmeaOutputEndpointPolicy.isValid(effective,state.settings.profile)&&nav.hasOpenTransport()
            NmeaOutputTransportMode.TCP_SERVER->false
            NmeaOutputTransportMode.DEDICATED_TCP->effective.outputHost.isNotBlank()&&effective.outputPort in 1..65535&&!NmeaOutputEndpointPolicy.opensSecondTransportOnInputEndpoint(effective,state.settings.profile)
            NmeaOutputTransportMode.UDP_UNICAST,NmeaOutputTransportMode.UDP_BROADCAST->effective.outputHost.isNotBlank()&&effective.outputPort in 1..65535
        }
    }
    private fun outputDestinationError(value:NmeaDeviceOutputSettings):String{
        val effective=NmeaOutputEndpointPolicy.automatic(value,_ui.value.settings.profile)
        return if(!effective.anyStreamSelected)"Select at least one Phone/App output stream before starting." else if(!effective.transportConfigured)"Choose an NMEA output destination before enabling a stream." else when(effective.transportMode){
            NmeaOutputTransportMode.SAME_AS_INPUT_CONNECTION->"Connect Boat NMEA input over TCP first. This route reuses that exact full-duplex connection and never opens a second boat socket."
            NmeaOutputTransportMode.DEDICATED_TCP->if(NmeaOutputEndpointPolicy.opensSecondTransportOnInputEndpoint(effective,_ui.value.settings.profile))"This duplicates the input endpoint. Choose Reuse current Boat TCP connection, or enter a genuinely separate TX port." else "Enter a valid TCP output host and port first."
            NmeaOutputTransportMode.TCP_SERVER->"Use the separate Phone NMEA service page to host a TCP listener."
            NmeaOutputTransportMode.UDP_UNICAST,NmeaOutputTransportMode.UDP_BROADCAST->"Enter a valid UDP output host and port first."
        }
    }
    fun testNmeaDeviceOutput(result:(Boolean)->Unit)=controllerScope.launch{
        val state=_ui.value
        val profile=state.settings.profile;val settings=NmeaOutputEndpointPolicy.automatic(state.outputSettings,profile)
        if(settings.transportMode==NmeaOutputTransportMode.TCP_SERVER){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"A phone-hosted listener belongs to Phone NMEA service, not Phone/App boat output."))};result(false);return@launch
        }
        val success=withContext(Dispatchers.IO){phonePositionNmeaOutputRuntime.testOutput(settings,profile)}
        result(success)
    }
    fun testKnownGoodHdgOutput(result:(Boolean)->Unit)=controllerScope.launch{
        val state=_ui.value
        val profile=state.settings.profile;val settings=NmeaOutputEndpointPolicy.automatic(state.outputSettings,profile)
        if(settings.transportMode==NmeaOutputTransportMode.TCP_SERVER){result(false);return@launch}
        val success=withContext(Dispatchers.IO){phonePositionNmeaOutputRuntime.testKnownGoodHdg(settings,profile)}
        result(success)
    }
    fun updateDemoConfiguration(scenario:com.yokuli.anchorwatch.domain.model.DemoScenario?=null,speed:Int?=null)=controllerScope.launch{
        val current=_ui.value.settings
        if(_ui.value.active!=null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Lift the current anchor session before changing the Demo trajectory."))};return@launch}
        if(_ui.value.activeTrip!=null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"End the current Trip Watch session before changing the Demo trajectory."))};return@launch}
        if(_ui.value.activeSonarSurvey!=null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Stop and save the sonar survey before changing the Demo trajectory."))};return@launch}
        if(!current.demoMode)return@launch
        prefs.save(current.copy(demoScenario=scenario?:current.demoScenario,demoSpeedMultiplier=speed?:current.demoSpeedMultiplier))
    }
    fun onPermissionsChanged(){systemLocation.refreshPermission()}
    fun setAnchorSetupGpsPreview(enabled:Boolean)=systemLocation.setPreviewEnabled(enabled)
    fun saveAnchorSetupDraft(value:AnchorSetupDraft){
        if(value==_ui.value.anchorSetupDraft&&!_ui.value.anchorDraftSaveError)return
        _ui.update{it.copy(anchorSetupDraft=value)}
        anchorDraftWrites.trySend(value)
    }
    fun clearAnchorSetupDraft(){
        _ui.update{it.copy(anchorSetupDraft=null)}
        anchorDraftWrites.trySend(null)
    }

    fun clearDiagnostics()=nav.clearDiagnostics()
    fun arm(lat:Double,lon:Double,input:AnchorWatchInput){ requestArm(lat,lon,input) }
    fun requestArm(lat:Double,lon:Double,input:AnchorWatchInput):String{
        val intent=Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.ARM)
            .putExtra("lat",lat).putExtra("lon",lon).putExtra("rode",input.rodeMeters).putExtra("depth",input.depthMeters?:Double.NaN).putExtra("bowHeight",input.bowHeightMeters).putExtra("boatLength",input.boatLengthMeters?:Double.NaN)
            .putExtra("antennaOffset",if(input.positionSource==GpsDataSource.NMEA)_ui.value.settings.nmeaGpsAntennaToBowMeters else 0.0)
            .putExtra("warning",maxOf(input.alarmRadiusMeters*.8,input.alarmRadiusMeters-10).coerceAtMost(input.alarmRadiusMeters-.1)).putExtra("alarm",input.alarmRadiusMeters).putExtra("placement",input.placement.name).putExtra("rangeMode",input.rangeMode.name).putExtra("safetyPreset",input.safetyPreset.name).putExtra("positionSource",input.positionSource.name).putExtra("centerSource",input.centerSource.name).putExtra("usePhoneHeading",true)
            .putExtra("depthSource",input.depthSource.name)
            .putExtra("originMode",input.originMode.name)
            .putExtra("anchoragePlaceId",input.anchoragePlaceId?:-1L).putExtra("anchorageSpotId",input.anchorageSpotId?:-1L)
            .putExtra("depthGuard",input.conditions.depthGuardEnabled).putExtra("shallowDepth",input.conditions.shallowDepthAlarmMeters?:Double.NaN).putExtra("deepDepth",input.conditions.deepDepthAlarmMeters?:Double.NaN).putExtra("windGuard",input.conditions.windGuardEnabled).putExtra("windWarning",input.conditions.windWarningKnots?:Double.NaN).putExtra("windAlarm",input.conditions.windAlarmKnots?:Double.NaN).putExtra("windShift",input.conditions.windShiftEnabled).putExtra("windShiftDegrees",input.conditions.windShiftThresholdDegrees?:Double.NaN).putExtra("apparentFallback",input.conditions.windAllowApparentFallback)
        return dispatchAnchorCommand(com.yokuli.runtime.contract.AnchorCommandType.START,null,intent).first
    }
    fun updateAnchorSettings(input:AnchorWatchInput){val intent=Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.UPDATE_RADIUS).putExtra("alarm",input.alarmRadiusMeters);ContextCompat.startForegroundService(app,intent)}
    fun updateConditionGuards(config:ConditionGuardConfig){val value=config.validated();ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.UPDATE_CONDITION_GUARDS).putExtra("depthGuard",value.depthGuardEnabled).putExtra("shallowDepth",value.shallowDepthAlarmMeters?:Double.NaN).putExtra("deepDepth",value.deepDepthAlarmMeters?:Double.NaN).putExtra("windGuard",value.windGuardEnabled).putExtra("windWarning",value.windWarningKnots?:Double.NaN).putExtra("windAlarm",value.windAlarmKnots?:Double.NaN).putExtra("windShift",value.windShiftEnabled).putExtra("windShiftDegrees",value.windShiftThresholdDegrees?:Double.NaN).putExtra("apparentFallback",value.windAllowApparentFallback))}
    fun resetWindBaseline()=ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.RESET_WIND_BASELINE))
    fun pauseWatch()=app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.PAUSE_WATCH))
    fun resumeWatch()=ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.RESUME_WATCH))
    fun liftAnchor()=ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.LIFT_ANCHOR))
    fun requestPauseWatch(sessionId:Long?)=dispatchAnchorCommand(com.yokuli.runtime.contract.AnchorCommandType.PAUSE,sessionId,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.PAUSE_WATCH)).first
    fun requestResumeWatch(sessionId:Long?)=dispatchAnchorCommand(com.yokuli.runtime.contract.AnchorCommandType.RESUME,sessionId,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.RESUME_WATCH)).first
    fun requestLiftAnchor(sessionId:Long?)=dispatchAnchorCommand(com.yokuli.runtime.contract.AnchorCommandType.LIFT,sessionId,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.LIFT_ANCHOR)).first
    private fun dispatchAnchorCommand(type:com.yokuli.runtime.contract.AnchorCommandType,sessionId:Long?,intent:Intent):Pair<String,android.content.ComponentName?>{
        val request=anchorCommands.create(type,sessionId)
        intent.putExtra(com.yokuli.anchorwatch.runtime.AnchorCommandRegistry.COMMAND_ID_EXTRA,request.commandId)
        fun deliver():android.content.ComponentName? {
            val component=if(type==com.yokuli.runtime.contract.AnchorCommandType.PAUSE)app.startService(intent) else {ContextCompat.startForegroundService(app,intent);null}
            if(type==com.yokuli.runtime.contract.AnchorCommandType.PAUSE && component==null)error("ANCHOR_SERVICE_NOT_STARTED")
            return component
        }
        anchorCommands.retainDelivery(request.commandId) { deliver() }
        try {
            val component=deliver()
            if(type==com.yokuli.runtime.contract.AnchorCommandType.PAUSE && component==null)error("ANCHOR_SERVICE_NOT_STARTED")
            return request.commandId to component
        }catch(error:Exception){
            anchorCommands.finish(request.commandId,com.yokuli.runtime.contract.AnchorCommandStatus.FAILED,sessionId,"DISPATCH_FAILED")
            throw error
        }
    }
    fun stop()=pauseWatch()
    fun acknowledge()=app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.ACK))
    fun acceptEstimatedCenter(session:AnchorSessionEntity)=session.candidateId?.let{candidateId->ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.ACCEPT_ESTIMATED_CENTER).putExtra("sessionId",session.id).putExtra("candidateId",candidateId))}
    fun keepCurrentCenter(session:AnchorSessionEntity)=session.candidateId?.let{candidateId->ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.KEEP_CURRENT_CENTER).putExtra("sessionId",session.id).putExtra("candidateId",candidateId))}
    fun continueEstimatingCenter(session:AnchorSessionEntity)=session.candidateId?.let{candidateId->ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.CONTINUE_ESTIMATING_CENTER).putExtra("sessionId",session.id).putExtra("candidateId",candidateId))}
    fun resetCentreAnalysis(session:AnchorSessionEntity)=ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.RESET_CENTRE_ANALYSIS).putExtra("sessionId",session.id))
    fun recalculateCentreFromTrack(session:AnchorSessionEntity)=controllerScope.launch{
        _ui.update{it.copy(centreRecalculation=CentreRecalculationUiState(session.id,session.active,loading=true))}
        dao.insertEvent(AlarmEventEntity(sessionId=session.id,timestamp=System.currentTimeMillis(),type="ANCHOR_CENTRE_RECALCULATION_REQUESTED"))
        val points=withContext(Dispatchers.IO){dao.points(session.id).first()}
        val result=withContext(Dispatchers.Default){AnchorCentreRecalculator.analyze(session,points)}
        val eventType=if(result.status==AnchorCentreRecalculationStatus.READY)"ANCHOR_CENTRE_RECALCULATION_READY" else "ANCHOR_CENTRE_RECALCULATION_INSUFFICIENT"
        val candidate=result.candidate
        dao.insertEvent(AlarmEventEntity(sessionId=session.id,timestamp=System.currentTimeMillis(),type=eventType,detail="status=${result.status};oldLat=${session.anchorLatitude};oldLon=${session.anchorLongitude};newLat=${candidate?.latitude};newLon=${candidate?.longitude};shiftMeters=${result.shiftMeters};uncertainty=${candidate?.uncertaintyRadiusMeters};trackDiameter=${candidate?.trackDiameterMeters};fitRadius=${candidate?.fittedRadiusMeters};radialObservable=${candidate?.radialObservable};reason=${candidate?.observabilityReason}"))
        _ui.update{it.copy(centreRecalculation=CentreRecalculationUiState(session.id,session.active,result=result))}
    }
    fun dismissCentreRecalculation()=_ui.update{it.copy(centreRecalculation=CentreRecalculationUiState())}
    fun keepCurrentRecalculatedCentre(){val value=_ui.value.centreRecalculation;val id=value.sessionId?:return;controllerScope.launch{dao.insertEvent(AlarmEventEntity(sessionId=id,timestamp=System.currentTimeMillis(),type="ANCHOR_CENTRE_RECALCULATION_REJECTED",detail="USER_KEPT_CURRENT"))};dismissCentreRecalculation()}
    fun applyRecalculatedCentre(){val value=_ui.value.centreRecalculation;val result=value.result?:return;val candidate=result.candidate?:return;if(!value.sessionActive||result.status!=AnchorCentreRecalculationStatus.READY)return;ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.APPLY_RECALCULATED_CENTRE).putExtra("sessionId",value.sessionId?:-1L).putExtra("expectedCurrentLatitude",result.currentLatitude).putExtra("expectedCurrentLongitude",result.currentLongitude).putExtra("latitude",candidate.latitude).putExtra("longitude",candidate.longitude).putExtra("uncertainty",candidate.uncertaintyRadiusMeters).putExtra("trackDiameter",candidate.trackDiameterMeters).putExtra("fitRadius",candidate.fittedRadiusMeters?:Double.NaN).putExtra("shift",result.shiftMeters?:Double.NaN));dismissCentreRecalculation()}
    fun saveRecalculatedCentreAsAnchorage(){val value=_ui.value.centreRecalculation;val result=value.result?:return;val candidate=result.candidate?:return;val session=_ui.value.sessions.firstOrNull{it.id==value.sessionId}?:return;controllerScope.launch{val now=System.currentTimeMillis();try{anchorageApproachRepository.save(SavedAnchorageEntity(name="${if(_ui.value.settings.appLanguage.usesChinese())"轨迹估算" else "Track estimate"} · ${java.text.DateFormat.getDateInstance().format(java.util.Date(session.startedAt))}",latitude=candidate.latitude,longitude=candidate.longitude,createdAt=now,updatedAt=now,preferredAlarmRadiusMeters=session.alarmRadiusMeters,typicalWaterDepthMeters=session.waterDepthMeters?:session.minObservedDepthMeters,typicalRodeLengthMeters=session.rodeLengthMeters,sourceSessionId=session.id,coordinateSource=com.yokuli.anchorwatch.data.anchorage.AnchorageCoordinateSource.ESTIMATED_REGION_CENTRE.name,coordinateUncertaintyMeters=candidate.uncertaintyRadiusMeters));dismissCentreRecalculation()}catch(cancelled:CancellationException){throw cancelled}catch(duplicate:DuplicateAnchorageException){_ui.update{it.copy(centreRecalculation=CentreRecalculationUiState(),anchorageDuplicateExisting=duplicate.existing)}}catch(error:Throwable){_ui.update{it.copy(centreRecalculation=CentreRecalculationUiState(),anchorageOperationError="Could not save the recalculated anchorage. No data was changed.")}}}}
    fun testAlarm()=ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.TEST_ALARM))
    fun stopAlarmTest()=app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.STOP_ALARM_TEST))
    fun startSonarSurvey(name:String,tideMode:TideMode,manualTideOffsetMeters:Double,tideStationId:String?=null){
        val intent=Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.START_SONAR_SURVEY).putExtra("name",name).putExtra("tideMode",tideMode.name).putExtra("manualTideOffset",manualTideOffsetMeters).putExtra("tideStationId",tideStationId)
        ContextCompat.startForegroundService(app,intent)
    }
    fun stopSonarSurvey()=app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.STOP_SONAR_SURVEY))
    val voyageCommandResults get() = voyageCommands.commands
    fun recheckVoyageCommand(requestId:String) {
        if(voyageCommands.get(requestId)?.terminal != false)return
        try {
            ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java)
                .setAction(com.yokuli.anchorwatch.runtime.VoyageCommandRegistry.QUERY_ACTION)
                .putExtra(com.yokuli.anchorwatch.runtime.VoyageCommandRegistry.EXTRA_ID,requestId))
        } catch(error:Exception) { voyageCommands.unknown(requestId,"QUERY_DELIVERY_FAILED") }
    }
    fun requestVoyageCommand(request:com.yokuli.runtime.contract.VoyageRequest):String {
        if(voyageCommands.register(request)) {
            val intent=Intent(app,AnchorForegroundService::class.java)
                .setAction(com.yokuli.anchorwatch.runtime.VoyageCommandRegistry.ACTION)
                .putExtra(com.yokuli.anchorwatch.runtime.VoyageCommandRegistry.EXTRA_ID,request.requestId)
            try {
                if(request.action in setOf(com.yokuli.runtime.contract.VoyageAction.START,com.yokuli.runtime.contract.VoyageAction.RESUME))
                    ContextCompat.startForegroundService(app,intent)
                else if(app.startService(intent)==null) voyageCommands.finish(request.requestId,com.yokuli.runtime.contract.VoyageRequestStatus.FAILED,"RUNTIME_UNAVAILABLE")
            } catch(error:Exception) {
                voyageCommands.finish(request.requestId,com.yokuli.runtime.contract.VoyageRequestStatus.FAILED,"COMMAND_DELIVERY_FAILED")
            }
        }
        return request.requestId
    }
    fun startTrip(name:String,phoneMotionEnabled:Boolean,positionPreference:VesselSourcePreference=_ui.value.vesselSettings.positionPreference)=controllerScope.launch{
        val safePreference=when(_ui.value.settings.gpsDataSource){GpsDataSource.SYSTEM->VesselSourcePreference.PHONE;GpsDataSource.NMEA->VesselSourcePreference.BOAT;else->null}
        if(safePreference==null){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Choose Phone GPS or NMEA before recording a trip."))};return@launch}
        ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.START_TRIP).putExtra("name",name).putExtra("phoneMotionEnabled",phoneMotionEnabled).putExtra("positionPreference",safePreference.name))
    }
    fun pauseTrip()=app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.PAUSE_TRIP))
    fun resumeTrip()=ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.RESUME_TRIP))
    fun pauseTripAttitude()=controllerScope.launch{
        vesselAttitudeRepository.setMounted(false)
        app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.PAUSE_TRIP_ATTITUDE))
        _ui.update{it.copy(vesselCalibrationFeedback="Trip attitude capture paused. Heading, GPS and pressure continue.")}
    }
    fun endTrip()=app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.END_TRIP))
    fun markTripWaypoint(name:String,note:String,type:String)=ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.MARK_TRIP_WAYPOINT).putExtra("name",name).putExtra("note",note).putExtra("type",type))
    fun deleteTrip(session:TripSessionEntity){if(session.active)return;controllerScope.launch{tripDao.deleteCompleted(session.id)}}
    /** 已保存航行的用户名称；不改动原始航迹和事件。 */
    fun renameTrip(id:Long,name:String)=controllerScope.launch{if(name.isNotBlank())tripDao.renameCompleted(id,name.trim().take(100))}
    fun editTripMoment(value:com.yokuli.anchorwatch.data.database.TripWaypointEntity,name:String,note:String)=controllerScope.launch{if(name.isNotBlank())tripDao.updateWaypoint(value.copy(name=name.trim().take(100),note=note.trim().take(2000)))}
    suspend fun tripReport(sessionId:Long):TripReport?=tripReportEngine.generate(sessionId)
    suspend fun anchorReport(sessionId:Long):AnchorReport?=anchorReportEngine.generate(sessionId)
    suspend fun tripReplay(sessionId:Long):TripReplayData=tripReplayLoader.load(sessionId)
    suspend fun tripMapData(sessionId:Long,pointBudget:Int):TripMapData=tripTrackRepository.loadMapData(sessionId,pointBudget)
    fun openLiveTripMap(){_ui.value.activeTrip?.let{openTripMap(TripMapDestinationType.LIVE,it.id)}}
    fun openTripMap(sessionId:Long,waypointId:Long?=null)=openTripMap(TripMapDestinationType.HISTORY,sessionId,waypointId)
    private fun openTripMap(type:TripMapDestinationType,sessionId:Long,waypointId:Long?=null){
        _ui.update{it.copy(tripMapDestination=TripMapDestination(type,sessionId,waypointId))}
    }
    fun closeTripMap(){_ui.update{it.copy(tripMapDestination=null)}}
    fun exportTripCsv(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.csv(session)}.onSuccess{shareExport(it,"text/csv")}.onFailure(::exportFailed)}
    fun exportTripGpx(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.gpx(session)}.onSuccess{shareExport(it,"application/gpx+xml")}.onFailure(::exportFailed)}
    fun exportTripKml(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.kml(session)}.onSuccess{shareExport(it,"application/vnd.google-earth.kml+xml")}.onFailure(::exportFailed)}
    fun exportTripKmz(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.kmz(session)}.onSuccess{shareExport(it,"application/vnd.google-earth.kmz")}.onFailure(::exportFailed)}
    fun exportTripEvents(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.eventsCsv(session)}.onSuccess{shareExport(it,"text/csv")}.onFailure(::exportFailed)}
    fun exportTripWaypoints(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.waypointsCsv(session)}.onSuccess{shareExport(it,"text/csv")}.onFailure(::exportFailed)}
    fun exportTripCustomMetrics(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.customMetricsCsv(session)}.onSuccess{shareExport(it,"text/csv")}.onFailure(::exportFailed)}
    fun shareTripLiveSnapshot()=controllerScope.launch{runCatching{tripExportManager.liveSnapshot(_ui.value.vesselData)}.onSuccess{shareExport(it,"image/png")}.onFailure(::exportFailed)}
    fun shareTripReportSnapshot(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.reportSnapshot(session)}.onSuccess{shareExport(it,"image/png")}.onFailure(::exportFailed)}
    fun exportTripAiSource(session:TripSessionEntity)=controllerScope.launch{runCatching{tripExportManager.aiZip(session)}.onSuccess{shareExport(it,"application/zip")}.onFailure(::exportFailed)}
    fun exportAnchorAiSource(session:AnchorSessionEntity)=controllerScope.launch{runCatching{tripExportManager.anchorAiZip(session)}.onSuccess{shareExport(it,"application/zip")}.onFailure(::exportFailed)}
    fun renameSonarSurvey(surveyId:Long,name:String)=controllerScope.launch{sonarRecorder.rename(surveyId,name)}
    fun deleteSonarSurvey(surveyId:Long)=controllerScope.launch{sonarRecorder.delete(surveyId)}
    fun rebuildSonarSurvey(surveyId:Long)=controllerScope.launch{sonarRecorder.rebuild(surveyId)}
    fun selectSonarSurvey(surveyId:Long)=observeSonarSamples(surveyId)
    fun selectCorrectedSonarHistory()=observeSonarSamples(CORRECTED_SONAR_HISTORY_ID)
    fun exportSonarCsv(survey:SonarSurveyEntity)=controllerScope.launch{
        val file=withContext(Dispatchers.IO){java.io.File(app.cacheDir,"sonar-${survey.id}.csv").also{target->target.bufferedWriter().use{writer->
            writer.appendLine("timestamp,latitude,longitude,raw_depth_m,measured_depth_m,normalized_depth_m,reference,sentence,nmea_offset_m,gps_source,position_provider,position_accuracy_m,hdop,sog_knots,position_age_ms,fix_trust,disposition,usable,position_correction,base_grid_x,base_grid_y,survey_id,tide_mode,tide_height_m,tide_station_id,tide_station_distance_m,tide_year,tide_method,tide_source,tide_source_updated_at,tide_status,depth_held,depth_age_ms,depth_source_elapsed_realtime")
            var afterTimestamp=Long.MIN_VALUE;var afterId=Long.MIN_VALUE
            while(true){val page=sonarDao.samplesPage(survey.id,afterTimestamp,afterId,1_000);if(page.isEmpty())break;page.forEach{sample->writer.appendLine("${sample.timestamp},${sample.latitude},${sample.longitude},${sample.rawDepthMeters},${sample.measuredDepthMeters},${sample.normalizedDepthMeters?:""},${sample.depthReference},${sample.sentenceType},${sample.nmeaOffsetMeters?:""},${sample.gpsSource},${sample.positionProvider},${sample.horizontalAccuracyMeters?:""},${sample.hdop?:""},${sample.sogKnots?:""},${sample.positionAgeMillis},${sample.fixTrust},${sample.disposition},${sample.usable},${sample.positionCorrectionMethod},${sample.baseGridX},${sample.baseGridY},${survey.id},${sample.tideCorrectionMode},${sample.tideHeightMetersApplied?:""},${sample.tideStationId?:""},${sample.tideStationDistanceMeters?:""},${sample.tidePredictionYear?:""},${sample.tideCorrectionMethod?:""},${sample.tideSource?:""},${sample.tideSourceUpdatedAt?:""},${sample.tideCorrectionStatus},${sample.depthHeld},${sample.depthAgeMillis},${sample.depthSourceElapsedRealtime?:""}")};val last=page.last();afterTimestamp=last.timestamp;afterId=last.id}
        }}}
        shareExport(file,"text/csv")
    }
    fun startGpsProxy(){val state=_ui.value;val availability=NmeaSourceSelectionPolicy.availability(state.connection,state.nmeaFix,state.nmeaConnectionStartedElapsed,android.os.SystemClock.elapsedRealtime(),state.settings.gpsLossSeconds*1_000L);val problem=when{state.settings.gpsDataSource!=GpsDataSource.NMEA->"Select NMEA GPS before enabling the global proxy.";availability!=NmeaSourceAvailability.AVAILABLE->"Connect the NMEA server and wait for a fresh valid position before enabling the global proxy.";!NmeaSourceSelectionPolicy.isUsablePosition(state.connection,state.nmeaFix,state.nmeaConnectionStartedElapsed,android.os.SystemClock.elapsedRealtime(),state.settings.gpsLossSeconds*1_000L)->"The current NMEA position quality is not acceptable for the global proxy.";else->null};if(problem!=null){_ui.update{it.copy(proxyFeedback=problem)};return};_ui.update{it.copy(proxyFeedback="Checking Android mock-location access…")};ContextCompat.startForegroundService(app,Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.START_PROXY))}
    fun stopGpsProxy(){_ui.update{it.copy(proxyFeedback=null)};app.startService(Intent(app,AnchorForegroundService::class.java).setAction(AnchorForegroundService.STOP_PROXY))}
    fun openDeveloperOptions(){runCatching{app.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure{app.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
    fun openAlarmNotificationSettings(){val channelReady=android.os.Build.VERSION.SDK_INT>=26&&app.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(AnchorForegroundService.ALARM_CH)!=null;val intent=when{channelReady->Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,app.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,AnchorForegroundService.ALARM_CH);android.os.Build.VERSION.SDK_INT>=26->Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,app.packageName);else->Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${app.packageName}"))};app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    fun openAlarmSoundSettings(){runCatching{app.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure{app.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
    fun openDoNotDisturbSettings(){runCatching{app.startActivity(Intent(android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure{openAlarmSoundSettings()}}
    fun openBatteryOptimization(){app.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    fun openFullScreenAlarmSettings(){
        val intent=if(android.os.Build.VERSION.SDK_INT>=34)Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${app.packageName}")) else Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,app.packageName)
        runCatching{app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure{openAlarmNotificationSettings()}
    }
    fun openAnchorInGoogleMaps(session:AnchorSessionEntity){
        if(session.centerStatus!=com.yokuli.anchorwatch.domain.model.AnchorCenterStatus.RESOLVED.name)return
        openCoordinatesInGoogleMaps(session.anchorLatitude,session.anchorLongitude)
    }
    fun openAnchorageInGoogleMaps(value:SavedAnchorageEntity)=openCoordinatesInGoogleMaps(value.latitude,value.longitude)
    fun openAnchorageCoordinates(latitude:Double,longitude:Double)=openCoordinatesInGoogleMaps(latitude,longitude)
    fun approachAnchorageSpot(spotId:Long)=controllerScope.launch{
        val spot=anchorageSpotRepository.get(spotId)?:run{runtimeDiagnostics.recordUserFeedback("Approach unavailable","That saved anchoring spot no longer exists. Refresh the library or restore it from backup.",true);return@launch}
        if(_ui.value.anchorageClusters.any{spot.id in it.savedAnchorageIds}){approachSavedAnchorage(spot.id);return@launch}
        val radius=maxOf(40.0,spot.preferredAlarmRadiusMeters?.takeIf{it.isFinite()&&it>0}?:0.0,spot.coordinateUncertaintyMeters?.takeIf{it.isFinite()&&it>0}?:0.0)
        val target=AnchorageCluster("spot:${spot.id}",spot.latitude,spot.longitude,radius,emptyList(),spot.name,1,spot.typicalWaterDepthMeters,spot.typicalWaterDepthMeters,spot.typicalRodeLengthMeters,spot.typicalRodeLengthMeters,spot.preferredAlarmRadiusMeters,spot.preferredAlarmRadiusMeters,spot.lastVisitedAt,spot.preferredAlarmRadiusMeters==null,spot.coordinateSource!="CONFIRMED_ANCHOR")
        gisApproachTarget=target;approachAnchorage(target.id)
    }
    fun approachSavedAnchorage(savedAnchorageId:Long){
        val cluster=_ui.value.anchorageClusters.firstOrNull{savedAnchorageId in it.savedAnchorageIds}?:run{runtimeDiagnostics.recordUserFeedback("Approach unavailable","The selected saved anchorage is not present in the current spatial index. Open its details and verify the saved coordinates.",true);return}
        approachAnchorage(cluster.id)
    }
    fun approachAnchorage(clusterId:String){
        if(availableApproachClusters().none{it.id==clusterId}){runtimeDiagnostics.recordUserFeedback("Approach unavailable","The selected anchorage target could not be resolved. The current Anchor Watch state was not changed.",true);return}
        if(_ui.value.active!=null){
            _ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Lift anchor before starting saved-anchorage approach guidance. The active alarm session remains unchanged."))}
            return
        }
        if(_ui.value.settings.anchorageApproachDisclaimerAccepted)startAnchorageApproach(clusterId)
        else _ui.update{it.copy(page=0,approachDisclaimerTargetId=clusterId)}
    }
    fun confirmAnchorageApproachDisclaimer(){
        val target=_ui.value.approachDisclaimerTargetId?:return
        if(_ui.value.active!=null){
            _ui.update{it.copy(approachDisclaimerTargetId=null,connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Lift anchor before starting saved-anchorage approach guidance. The active alarm session remains unchanged."))}
            return
        }
        val updated=_ui.value.settings.copy(anchorageApproachDisclaimerAccepted=true)
        _ui.update{it.copy(settings=updated,approachDisclaimerTargetId=null)}
        controllerScope.launch{prefs.save(updated)}
        startAnchorageApproach(target)
    }
    fun dismissAnchorageApproachDisclaimer()=_ui.update{it.copy(approachDisclaimerTargetId=null)}
    private fun startAnchorageApproach(clusterId:String){
        if(_ui.value.active!=null){runtimeDiagnostics.recordUserFeedback("Approach not started","Lift the active anchor before starting saved-anchorage approach guidance.",true);return}
        val target=availableApproachClusters().firstOrNull{it.id==clusterId}?:run{runtimeDiagnostics.recordUserFeedback("Approach unavailable","The selected target is no longer available.",true);return}
        selectedApproachClusterId=target.id
        selectedApproachMemberIds=target.savedAnchorageIds.toSet()
        phoneHeadingRepository.setApproachDemand(true)
        anchorageNearbyTracker.dismiss(_ui.value.nearbyAnchoragePrompt.map{it.cluster.id})
        _ui.update{it.copy(page=0,anchorSection=0,approachDisclaimerTargetId=null,nearbyAnchoragePrompt=emptyList(),approachHeadingMode=if(it.vesselApproachHeadingAvailable)ApproachHeadingMode.VESSEL else ApproachHeadingMode.PHONE)}
        refreshAnchorageApproach()
    }
    fun setApproachHeadingMode(mode:ApproachHeadingMode){
        val state=_ui.value
        if(selectedApproachClusterId==null)return
        if(mode==ApproachHeadingMode.VESSEL&&!state.vesselApproachHeadingAvailable)return
        _ui.update{it.copy(approachHeadingMode=mode)}
        refreshAnchorageApproach()
    }
    fun cancelAnchorageApproach(){selectedApproachClusterId=null;selectedApproachMemberIds=emptySet();gisApproachTarget=null;phoneHeadingRepository.setApproachDemand(false);refreshAnchorageApproach()}
    private fun availableApproachClusters()=_ui.value.anchorageClusters+listOfNotNull(gisApproachTarget).filter{target->_ui.value.anchorageClusters.none{it.id==target.id}}
    fun setPhoneHeadingDisplayActive(active:Boolean){phoneHeadingRepository.setDisplayDemand(active)}
    fun setMapHeadingDisplayActive(active:Boolean)=setPhoneHeadingDisplayActive(active)
    fun dismissNearbyAnchorage(){
        anchorageNearbyTracker.dismiss(_ui.value.nearbyAnchoragePrompt.map{it.cluster.id})
        _ui.update{it.copy(nearbyAnchoragePrompt=emptyList())}
    }
    private fun openCoordinatesInGoogleMaps(latitude:Double,longitude:Double){
        val uri=android.net.Uri.parse(AnchorageShareContent.googleMapsUrl(latitude,longitude))
        val google=Intent(Intent.ACTION_VIEW,uri).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val intent=if(google.resolveActivity(app.packageManager)!=null)google else Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching{app.startActivity(intent)}.onFailure{_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"No map application or browser is available to open the anchor position."))}}
    }
    fun shareAnchorageQr(value:SavedAnchorageEntity)=controllerScope.launch{
        runCatching{
            val file=withContext(Dispatchers.IO){anchorageQrImageGenerator.generate(value,_ui.value.settings.appLanguage.usesChinese())}
            val uri=androidx.core.content.FileProvider.getUriForFile(app,"${app.packageName}.files",file)
            val send=Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM,uri)
                .putExtra(Intent.EXTRA_TEXT,AnchorageShareContent.shareText(value))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            send.clipData=android.content.ClipData.newUri(app.contentResolver,"Saved anchorage",uri)
            val title=if(_ui.value.settings.appLanguage.usesChinese())"分享收藏锚地" else "Share saved anchorage"
            app.startActivity(Intent.createChooser(send,title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure{_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"Could not create or share the anchorage QR image."))}}
    }
    private val _shellDestinations=kotlinx.coroutines.flow.MutableSharedFlow<com.yokuli.anchorwatch.domain.model.MarineDestination>(extraBufferCapacity=8)
    val shellDestinations=_shellDestinations.asSharedFlow()
    private fun emitDestination(index:Int){_shellDestinations.tryEmit(when(index){0->com.yokuli.anchorwatch.domain.model.MarineDestination.ANCHOR;1->com.yokuli.anchorwatch.domain.model.MarineDestination.INSTRUMENTS;2->when(_ui.value.dataSection){1->com.yokuli.anchorwatch.domain.model.MarineDestination.NMEA;2->com.yokuli.anchorwatch.domain.model.MarineDestination.NMEA;3->com.yokuli.anchorwatch.domain.model.MarineDestination.DEPTH;else->com.yokuli.anchorwatch.domain.model.MarineDestination.SOURCES};else->com.yokuli.anchorwatch.domain.model.MarineDestination.SETTINGS})}
    private val _navigationRequests=kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity=8)
    val navigationRequests=_navigationRequests.asSharedFlow()
    fun page(index:Int){_ui.update{it.copy(page=index,sonarGridChangedCells=emptySet())};_navigationRequests.tryEmit(index);emitDestination(index)}
    fun rememberAnchorSection(index:Int)=_ui.update{it.copy(anchorSection=index.coerceIn(0,2))}
    fun rememberSailSection(index:Int)=_ui.update{it.copy(sailSection=index.coerceIn(0,1))}
    fun openDataSection(index:Int){_ui.update{it.copy(page=2,dataSection=index.coerceIn(0,3),sonarGridChangedCells=emptySet())};_navigationRequests.tryEmit(2);emitDestination(2)}
    fun rememberDataSection(index:Int)=_ui.update{it.copy(dataSection=index.coerceIn(0,3))}
    fun follow(value:Boolean)=_ui.update{it.copy(follow=value)}
    fun requestRangeEditor(){_ui.update{it.copy(page=0,rangeEditorRequested=true)};_navigationRequests.tryEmit(0);emitDestination(0)}
    fun consumeRangeEditorRequest()=_ui.update{it.copy(rangeEditorRequested=false)}
    fun loadHistoryEvents(sessionId:Long)=controllerScope.launch{val events=dao.recentEvents(sessionId,30);_ui.update{it.copy(eventsBySession=mapOf(sessionId to events))}}
    fun saveAnchorage(value:SavedAnchorageEntity)=controllerScope.launch{
        val proposed=value.copy(updatedAt=System.currentTimeMillis())
        try{anchorageApproachRepository.save(proposed)}catch(cancelled:CancellationException){throw cancelled}catch(duplicate:DuplicateAnchorageException){
            _ui.update{it.copy(anchorageDuplicateExisting=duplicate.existing)}
        }catch(error:Throwable){
            android.util.Log.e("AnchorLibrary","Failed to save anchorage",error)
            _ui.update{it.copy(anchorageOperationError="Could not save the anchorage. No data was changed.")}
        }
    }
    fun dismissAnchorageDuplicate()=_ui.update{it.copy(anchorageDuplicateExisting=null)}
    fun deleteAnchorage(id:Long)=controllerScope.launch{
        try{
            anchorageApproachRepository.delete(id,_ui.value.active?.anchoragePlaceId)
            _ui.update{state->state.copy(anchorageDuplicateExisting=state.anchorageDuplicateExisting?.takeUnless{it.id==id})}
        }catch(cancelled:CancellationException){throw cancelled}catch(error:Throwable){
            android.util.Log.e("AnchorLibrary","Failed to delete anchorage $id",error)
            _ui.update{it.copy(anchorageOperationError="Could not delete the anchorage. It is still saved.")}
        }
    }
    fun dismissAnchorageOperationError()=_ui.update{it.copy(anchorageOperationError=null)}
    fun exportCsv(session:AnchorSessionEntity)=controllerScope.launch{
        val points=dao.points(session.id).first()
        val file=java.io.File(app.cacheDir,"anchor-${session.id}.csv")
        file.writeText(buildString{
            appendLine("timestamp,latitude,longitude,distance_from_anchor_m,gps_source,provider,hdop,horizontal_accuracy_m,fix_trust,was_quarantined,sog_knots,cog_deg,heading_deg,heading_source,heading_quality,heading_epoch,wind_direction_true,wind_speed_knots")
            points.forEach{appendLine("${it.timestamp},${it.latitude},${it.longitude},${it.distanceFromAnchor},${it.positionSource},${it.positionProvider},${it.hdop?:""},${it.horizontalAccuracyMeters?:""},${it.fixTrust},${it.wasQuarantined},${it.sog?:""},${it.cog?:""},${it.heading?:""},${it.headingSource},${it.headingQuality},${it.headingEpoch?:""},${it.windDirectionTrue?:""},${it.windSpeedKnots?:""}")}
        })
        shareExport(file,"text/csv")
    }
    fun exportGpx(session:AnchorSessionEntity)=controllerScope.launch{
        val points=dao.points(session.id).first();val events=dao.events(session.id).first();val file=java.io.File(app.cacheDir,"anchor-${session.id}.gpx")
        file.writeText(buildString{
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<gpx version=\"1.1\" creator=\"Boat Watch\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
            if(events.isNotEmpty())appendLine("<metadata><desc>${xmlEscape(events.joinToString(" | "){event->"${java.time.Instant.ofEpochMilli(event.timestamp)} ${event.type} ${event.detail}"})}</desc></metadata>")
            appendLine("<wpt lat=\"${session.anchorLatitude}\" lon=\"${session.anchorLongitude}\"><name>Active anchor</name></wpt>")
            appendLine("<trk><name>Anchor session ${session.id}</name><trkseg>")
            points.forEach{appendLine("<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"><time>${java.time.Instant.ofEpochMilli(it.timestamp)}</time></trkpt>")}
            appendLine("</trkseg></trk>");appendLine("</gpx>")
        })
        shareExport(file,"application/gpx+xml")
    }
    private fun shareExport(file:java.io.File,mime:String){val uri=androidx.core.content.FileProvider.getUriForFile(app,"${app.packageName}.files",file);val intent=Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK);runCatching{app.startActivity(Intent.createChooser(intent,"Export anchor session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure{_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,"No app is available to receive the export."))}}}
    private fun exportFailed(error:Throwable){_ui.update{it.copy(connectionAttempt=ConnectionAttempt(ConnectionAttemptState.FAILED,error.message?:"Could not create the export."))}}
    private fun xmlEscape(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
}

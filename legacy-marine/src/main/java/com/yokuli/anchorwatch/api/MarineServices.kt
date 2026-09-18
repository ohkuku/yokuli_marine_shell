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

/**
 * 进程内迁移用的只读兼容投影。MainUiState 仍含旧实体和 UI 字段，不是公开 OS IPC schema。
 * 各端口当前观察同一事实快照；不能经此接口取得 Controller、可变 Flow 或底层 repository。
 */
interface MarineStateReader {
    val state: StateFlow<MainUiState>
}

/** 只有数据中心端口可以选源、启停手机定位或确认手机安装方向。 */
interface DataSourceService : MarineStateReader {
    val phoneLocationStatus: StateFlow<PhoneLocationStatus>
    fun onPermissionsChanged()
    fun switchGpsDataSource(source: GpsDataSource): Job
    fun selectNmeaPositionConnection(id: String)
    fun setVesselMetricSource(metric: VesselMetricId, sourceId: String?): Job
    fun confirmTripAttitudeFrame(axis: DeviceBowAxis): Job
    fun alignPhoneHeadingToBow(): Job
    fun alignPhoneHeadingToNmea(): Job
    fun clearVesselCalibrationFeedback()
}

/** 所有应用控制同一个航行会话；查看、导出和编辑也由航行领域执行。 */
interface VoyageService : MarineStateReader {
    fun startTrip(name: String, phoneMotionEnabled: Boolean, positionPreference: VesselSourcePreference = state.value.vesselSettings.positionPreference): Job
    fun pauseTrip(): ComponentName?
    fun resumeTrip()
    fun pauseTripAttitude(): Job
    fun endTrip(): ComponentName?
    fun markTripWaypoint(name: String, note: String, type: String)
    fun deleteTrip(session: TripSessionEntity)
    fun renameTrip(id: Long, name: String): Job
    fun editTripMoment(value: TripWaypointEntity, name: String, note: String): Job
    suspend fun tripReport(sessionId: Long): TripReport?
    suspend fun tripReplay(sessionId: Long): TripReplayData
    suspend fun tripMapData(sessionId: Long, pointBudget: Int): TripMapData
    fun exportTripCsv(session: TripSessionEntity): Job
    fun exportTripGpx(session: TripSessionEntity): Job
    fun exportTripKml(session: TripSessionEntity): Job
    fun exportTripKmz(session: TripSessionEntity): Job
    fun exportTripEvents(session: TripSessionEntity): Job
    fun exportTripWaypoints(session: TripSessionEntity): Job
    fun exportTripCustomMetrics(session: TripSessionEntity): Job
    fun shareTripReportSnapshot(session: TripSessionEntity): Job
    fun exportTripAiSource(session: TripSessionEntity): Job
}

/** 暂停、起锚、消音和 UI 消息消费互相独立；端口不根据页面生命周期代为执行。 */
interface AnchorService : MarineStateReader {
    fun saveAnchorSetupDraft(value: AnchorSetupDraft)
    fun clearAnchorSetupDraft()
    fun arm(lat: Double, lon: Double, input: AnchorWatchInput)
    fun updateAnchorSettings(input: AnchorWatchInput)
    fun updateConditionGuards(config: ConditionGuardConfig)
    fun pauseWatch(): ComponentName?
    fun resumeWatch()
    fun liftAnchor()
    fun acknowledge(): ComponentName?
    fun keepCurrentCenter(session: AnchorSessionEntity): Unit?
    fun continueEstimatingCenter(session: AnchorSessionEntity): Unit?
    fun recalculateCentreFromTrack(session: AnchorSessionEntity): Job
    fun keepCurrentRecalculatedCentre()
    fun acceptEstimatedCenter(session: AnchorSessionEntity): Unit?
    fun applyRecalculatedCentre()
    fun loadHistoryEvents(sessionId: Long): Job
    fun exportCsv(session: AnchorSessionEntity): Job
    fun exportGpx(session: AnchorSessionEntity): Job
}

/** 连接聚合拥有地址、接收/发送及该连接的输出策略，不拥有全船字段选源。 */
interface NetworkService : MarineStateReader {
    val connections: StateFlow<List<NmeaConnectionSnapshot>>
    fun saveNmeaConnection(spec: NmeaConnectionSpec, onSaved: () -> Unit = {}): Job
    fun startNmeaConnection(id: String): Job
    fun stopNmeaConnection(id: String): Job
    fun removeNmeaConnection(id: String): Job
    fun saveAndConnect(profile: ConnectionProfile): Job
    fun disconnect()
}

/** 本机 NMEA 发布服务及明确的全部共享停止；分享内容不修改数据中心的来源。 */
interface SharingService : MarineStateReader {
    fun setNmeaSharing(enabled: Boolean, port: Int)
    fun saveLocalNmeaPublicationPolicy(port: Int, feed: NmeaFeed, capabilities: Set<String>, forwardFrom: Set<String>): Job
    fun startLocalNmeaServer(): Job
    fun stopLocalNmeaServer(): Job
    fun stopAllNmeaSharing(): Job
}

/**
 * 船舶/仪表偏好、警报声音设置与用户资料备份。只接收本次修改的字段；
 * 不接收整份旧设置，来源、连接与发布不能通过偏好命令覆盖。
 */
interface VesselPreferencesService : MarineStateReader {
    fun setLanguage(language: AppLanguage): Job
    fun setVesselGeometry(lengthMeters: Double, bowRollerHeightMeters: Double, antennaToBowMeters: Double): Job
    fun setVesselIdentity(name: String, draftMeters: Double?): Job
    fun setAlarmSound(sound: AlarmSound, customUri: String? = null): Job
    fun setAlarmSnoozeMinutes(minutes: Int): Job
    fun setInstrumentLayout(layout: List<InstrumentTileId>): Job
    fun exportBackup(uri: Uri): Job
    fun restoreBackup(uri: Uri): Job
    fun clearBackupResult()
    fun confirmAlarmAudible()
    fun testAlarm()
    fun stopAlarmTest(): ComponentName?
    fun openAlarmSoundSettings()
    fun openDoNotDisturbSettings()
}

/** 消息消费只推进反馈游标，不能代替确认守锚告警。 */
interface MarineFeedbackService : MarineStateReader {
    val destinations: SharedFlow<MarineDestination>
    fun consumeRuntimeFeedback(id: Long)
}

/** 每个 UI 消费者独立持有并幂等关闭；关闭自己的句柄不能撤销另一个画面的显示需求。 */
interface DisplayLease : AutoCloseable {
    override fun close()
}

/** UI 仅申请临时显示需求；不创建定位、连接、航行或值守的运行租约。 */
interface DisplayDemandService {
    fun acquireMapHeading(): DisplayLease
    fun acquireInstruments(): DisplayLease
}

/**
 * Android APK 与 ROM HOME 共用的进程内服务入口。当前实现是 LocalMarineServices，
 * 尚无 Binder/远程 ROM 实现；调用方不得根据预置身份假定特权或后台存活保证。
 */
interface MarineServices : MarineStateReader {
    val sources: DataSourceService
    val voyages: VoyageService
    val anchor: AnchorService
    val network: NetworkService
    val sharing: SharingService
    val preferences: VesselPreferencesService
    val feedback: MarineFeedbackService
    val display: DisplayDemandService
    val content: MarineContentService
}

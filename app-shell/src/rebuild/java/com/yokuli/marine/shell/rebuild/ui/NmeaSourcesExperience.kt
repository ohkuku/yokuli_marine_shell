package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.runtime.contract.PositionSourceRequest
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSnapshot
import com.yokuli.anchorwatch.data.vessel.VesselDataSettings
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.*

/** 来源选择只写既有全局配置；连接启停和发布策略不在此处修改。 */
@Composable internal fun VesselSourceSettings(os: OsStore) = PageBody {
    SourceOverview(os) { os.open("data_center:${it.name}") }
}

@Composable internal fun ColumnScope.SourceOverview(os: OsStore, open: (VesselMetricId) -> Unit) {
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val connections by services.network.connections.collectAsState()
    val now = rememberMarineClock()
    Label(os.t("全船共用一份数据", "one set of data aboard"), 28)
    Label(os.t("在这里选择每项读数由谁提供。海图、守锚、驾驶台与共享服务使用同一份选择。", "Choose who supplies each reading. Chart, Anchor Watch, Helm and sharing all use these choices."), 17, LocalMetro.current.muted)
    val groups = listOf(
        os.t("航行", "navigation") to listOf(VesselMetricId.POSITION, VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC, VesselMetricId.SOG, VesselMetricId.COG, VesselMetricId.SPEED_THROUGH_WATER),
        os.t("风与环境", "wind & environment") to listOf(VesselMetricId.APPARENT_WIND_SPEED, VesselMetricId.APPARENT_WIND_ANGLE, VesselMetricId.TRUE_WIND_SPEED, VesselMetricId.TRUE_WIND_ANGLE, VesselMetricId.TRUE_WIND_DIRECTION, VesselMetricId.DEPTH, VesselMetricId.PRESSURE, VesselMetricId.WATER_TEMPERATURE, VesselMetricId.AIR_TEMPERATURE),
        os.t("船体姿态", "vessel motion") to listOf(VesselMetricId.HEEL, VesselMetricId.PITCH, VesselMetricId.RATE_OF_TURN, VesselMetricId.ROLL_RATE, VesselMetricId.PITCH_RATE, VesselMetricId.YAW_RATE, VesselMetricId.RUDDER_ANGLE),
    )
    groups.forEach { (title, metrics) ->
        Label(title, 30, LocalMetro.current.accent)
        metrics.forEach { metric ->
            val observation = sourceObservation(metric, state.vesselData)
            val candidates = state.vesselData.candidates[metric].orEmpty()
            val pin = state.vesselSettings.metricSourcePins[metric.name]
            val provider = observation?.sourceIdentity?.let { sourceDisplayName(os, it, connections) }
            val value = observation?.let { sourceObservationText(os, metric, it, now) }
            val choice = when {
                metric == VesselMetricId.POSITION && os.positionSource == "none" -> os.t("已关闭", "off")
                metric == VesselMetricId.POSITION && os.positionSource == "phone" -> os.t("手机 GPS", "phone GPS")
                metric == VesselMetricId.POSITION -> os.t("指定船位来源", "selected position source")
                pin == null && hasLegacyHeadingChoice(metric, state.vesselSettings) -> os.t("旧版船首向选择", "legacy heading choice")
                pin == null -> os.t("自动", "automatic")
                else -> os.t("指定来源", "selected source")
            }
            MenuRow(sourceMetricName(os, metric), listOfNotNull(value, provider ?: choice,
                if (observation?.value == null && candidates.isEmpty()) os.t("等待来源提供读数", "waiting for a source reading") else null).joinToString(" · ")) { open(metric) }
        }
    }
    val known = groups.flatMap { it.second }.toSet()
    val more = state.vesselData.candidates.keys.filter { it !in known && it !in derivedSourceMetrics }.sortedBy { it.ordinal }
    if (more.isNotEmpty()) {
        Label(os.t("更多数据", "more measurements"), 30, LocalMetro.current.accent)
        more.forEach { metric -> MenuRow(sourceMetricName(os, metric), sourceObservation(metric, state.vesselData)?.let { sourceObservationText(os, metric, it, now) }.orEmpty()) { open(metric) } }
    }
}

@Composable internal fun ColumnScope.SourceMetricDetail(os: OsStore, metric: VesselMetricId, showObservation: Boolean = true) {
    val services = os.marine?.services ?: return
    val state by services.state.collectAsState()
    val connections by services.network.connections.collectAsState()
    val now = rememberMarineClock()
    val observation = sourceObservation(metric, state.vesselData)
    val candidates = state.vesselData.candidates[metric].orEmpty().distinctBy { it.source.persistentKey }
    val pinned = state.vesselSettings.metricSourcePins[metric.name]
    val locked = state.active?.paused == false
    val activeKey = observation?.takeIf { it.value != null && it.freshness in setOf(VesselDataFreshness.FRESH,VesselDataFreshness.HELD) }?.sourceIdentity?.persistentKey
    if(showObservation) {
        Label(observation?.let { sourceObservationText(os, metric, it, now) } ?: os.t("等待第一条读数", "waiting for the first reading"), 30)
        observation?.sourceIdentity?.let { Label(sourceDisplayName(os, it, connections), 19, LocalMetro.current.accent) }
    }
    if (metric == VesselMetricId.POSITION) {
        Label(os.t("谁提供船位", "position source"), 28)
        ChoiceRow(os.t("关闭船位", "position off"), os.positionSource == "none", os.t("停止使用船位，保留其他读数与网络连接。", "Stop using position; keep other readings and network connections."), !locked) { os.requestPosition(PositionSourceRequest.DISABLE_POSITION) }
        ChoiceRow(os.t("手机 GPS", "phone GPS"), os.positionSource == "phone", os.t("选择时启动手机定位，关闭时停止；需要精确定位权限。", "Starts phone location when selected and stops when disabled; requires precise location permission."), !locked && os.positionSource in listOf("none", "phone")) { os.requestPosition(PositionSourceRequest.ENABLE_PHONE) }
        val selectedConnection = state.vesselSettings.metricSourcePins["POSITION_CONNECTION"]
        connections.filter { it.spec.receive }.forEach { connection ->
            val online = connection.acceptsSourceSelection()
            val same = os.positionSource == "nmea" && selectedConnection == connection.spec.id
            ChoiceRow(connection.spec.name, same,
                if (online) os.t("NMEA · 已连接", "NMEA · connected") else os.t("NMEA · 尚未连接；在船联网中启动", "NMEA · stopped; start it in Boat Network"),
                !locked && online && os.positionSource != "phone") { services.sources.selectNmeaPositionConnection(connection.spec.id) }
            if (same) candidates.filter { it.source.transportProfileId == connection.spec.id }.forEach { candidate ->
                ChoiceRow(sourceDisplayName(os, candidate.source, connections), (pinned ?: activeKey) == candidate.source.persistentKey,
                    sourceCandidateText(os, metric, candidate, now), !locked) { services.sources.setVesselMetricSource(metric, candidate.source.persistentKey) }
            }
        }
        if (connections.none { it.spec.receive }) Label(os.t("还没有船载输入。在船联网中添加连接后，它会出现在这里。", "No boat input yet. Add a connection in Boat Network and it will appear here."), 18, LocalMetro.current.muted)
        Label(when {
            locked -> os.t("守锚进行中，暂停后可以更改船位来源。", "Pause Anchor Watch before changing position source.")
            os.positionSource == "phone" -> os.t("要改用船载船位，先关闭手机船位。其他 NMEA 读数会继续更新。", "Turn phone position off before choosing a boat source. Other NMEA readings keep updating.")
            os.positionSource == "nmea" -> os.t("要改用手机，先关闭船载船位。选中的连接中断时不会自动换来源。", "Turn boat position off before choosing the phone. If the selected connection stops, another source will not take over.")
            else -> os.t("选择只影响全船使用的船位，不会启动任何网络连接。", "This chooses position for all apps; it never starts a network connection.")
        }, 17, LocalMetro.current.muted)
        return
    }
    if (metric in derivedSourceMetrics) {
        Label(os.t("由系统根据已选读数计算，无需另外指定来源。", "Calculated from the selected readings; no separate source selection is needed."), 18, LocalMetro.current.muted)
        return
    }
    Label(os.t("选择来源", "choose a source"), 28)
    val legacyHeading = hasLegacyHeadingChoice(metric, state.vesselSettings)
    if (legacyHeading && pinned == null) {
        ChoiceRow(os.t("沿用旧版船首向选择", "keep legacy heading choice"), true,
            listOfNotNull(when (state.vesselSettings.headingPreference) {
                VesselSourcePreference.BOAT -> os.t("船载数据", "boat data")
                VesselSourcePreference.PHONE -> os.t("手机数据", "phone data")
                VesselSourcePreference.DERIVED -> os.t("计算数据", "derived data")
                VesselSourcePreference.AUTO -> null
            }, state.vesselSettings.boatHeadingSourceId).joinToString(" · "), enabled = false) {}
    }
    ChoiceRow(os.t("自动", "automatic"), pinned == null && !legacyHeading,
        if (legacyHeading) os.t("清除旧版共用船首向偏好。真、磁船首向将分别按各自选择运行，已有独立指定来源会保留。", "Clear the shared legacy heading preference. True and magnetic heading then use their individual choices; existing per-reading selections are kept.")
        else if (metric in setOf(VesselMetricId.SOG, VesselMetricId.COG)) os.t("默认跟随船位来源；也可以指定下面的独立读数。", "Follows the position source by default; you can select an independent reading below.")
        else os.t("从已采集的候选中选择；不会开启连接或手机定位。", "Chooses among observed candidates; does not start connections or phone location.")) { services.sources.setVesselMetricSource(metric, null) }
    candidates.sortedWith(compareBy<VesselSourceCandidate<*>> { it.source.transportProfileId != null }.thenBy { sourceDisplayName(os, it.source, connections) }).forEach { candidate ->
        val connection = candidate.source.transportProfileId?.let { id -> connections.firstOrNull { it.spec.id == id } }
        val selected = pinned == candidate.source.persistentKey
        val actual = activeKey == candidate.source.persistentKey
        val status = listOfNotNull(sourceCandidateText(os, metric, candidate, now),
            if (actual) os.t("当前采用", "in use") else null,
            if (connection != null && !connection.requested) os.t("连接已停止", "connection stopped") else null,
            if (candidate.source.sourceClassIsPhoneGps() && os.positionSource != "phone") os.t("手机定位未被选用", "phone location is not selected") else null).joinToString(" · ")
        ChoiceRow(sourceDisplayName(os, candidate.source, connections), selected, status) { services.sources.setVesselMetricSource(metric, candidate.source.persistentKey) }
    }
    if (pinned != null && candidates.none { it.source.persistentKey == pinned }) {
        Label(os.t("保留已指定的来源，等待它恢复。不会改用另一个来源。", "Keeping your selected source while it is absent. Another source will not take over."), 18, LocalMetro.current.muted)
    }
    if (candidates.isEmpty()) Label(os.t("尚未收到这项数据。手机采集或已连接的 NMEA 设备提供读数后，会在同一列表中出现。", "No reading received yet. Phone measurements and connected NMEA devices appear together when observed."), 19, LocalMetro.current.muted)
    if (metric in setOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC)) Label(os.t("手机固定并对齐船艏后，才能作为船首向来源。对地航向不能代替船首向。", "A mounted phone becomes a heading source after bow alignment. Course over ground cannot replace heading."), 17, LocalMetro.current.muted)
    state.vesselData.conflicts[metric]?.takeIf { it.active }?.let { Label(os.t("来源之间的读数有差异，请核对安装方向和设备。", "Sources disagree. Check device alignment and instruments."), 17, LocalMetro.current.accent) }
}

private fun hasLegacyHeadingChoice(metric: VesselMetricId, settings: VesselDataSettings) = metric in setOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC) && (settings.headingPreference != VesselSourcePreference.AUTO || settings.boatHeadingSourceId != null)

private fun NmeaConnectionSnapshot.acceptsSourceSelection() = requested && state in setOf(NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE)
private fun VesselSourceIdentity.sourceClassIsPhoneGps() = sourceType == VesselSourceType.PHONE_SENSOR && (phoneSensorType?.contains("GPS", true) == true || id.contains("gnss", true))
internal val derivedSourceMetrics = setOf(VesselMetricId.UKC, VesselMetricId.VMG_WIND, VesselMetricId.VMC_WAYPOINT, VesselMetricId.MOTION_SCORE, VesselMetricId.ROLL_PERIOD)

internal fun sourceDisplayName(os: OsStore, source: VesselSourceIdentity, connections: List<NmeaConnectionSnapshot>): String {
    source.transportProfileId?.let { id ->
        val name = connections.firstOrNull { it.spec.id == id }?.spec?.name ?: os.t("船载连接", "boat connection")
        return listOfNotNull(name, source.transducerName ?: source.fullSentenceId ?: source.talkerId).distinct().joinToString(" · ")
    }
    if (source.sourceType == VesselSourceType.PHONE_SENSOR) return when {
        source.id.contains("baro", true) || source.phoneSensorType?.contains("pressure", true) == true -> os.t("手机气压计", "phone barometer")
        source.id.contains("gnss", true) || source.id.contains("gps", true) -> os.t("手机 GPS", "phone GPS")
        source.id.contains("vessel-heading", true) -> os.t("手机船首向", "phone vessel heading")
        source.id.contains("heading", true) || source.id.contains("compass", true) -> os.t("手机罗盘方位", "phone compass direction")
        source.id.contains("imu", true) -> os.t("手机船体姿态", "phone vessel attitude")
        else -> os.t("手机传感器", "phone sensor")
    }
    return if (source.sourceType == VesselSourceType.APP_DERIVED) os.t("系统计算", "calculated") else source.displayName
}

internal fun sourceCandidateText(os: OsStore, metric: VesselMetricId, candidate: VesselSourceCandidate<*>, now: Long): String = listOfNotNull(
    sourceValueText(os, metric, candidate.value), readingAge(os, candidate.receivedElapsedRealtime, now),
    when (candidate.validity) { CandidateValidity.ELIGIBLE -> null; CandidateValidity.LOW_QUALITY -> os.t("精度较低", "lower accuracy"); CandidateValidity.STALE -> os.t("保留上次读数", "last reading retained"); CandidateValidity.DISABLED -> os.t("已停止", "stopped"); CandidateValidity.INVALID -> os.t("未通过质量检查", "quality check failed") }
).joinToString(" · ")

internal fun sourceObservationText(os: OsStore, metric: VesselMetricId, observation: VesselObservation<*>, now: Long): String {
    val direction = metric in setOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC, VesselMetricId.DEVICE_HEADING_TRUE, VesselMetricId.DEVICE_HEADING_MAGNETIC)
    return listOfNotNull(if (direction && observation.freshness != VesselDataFreshness.FRESH) os.t("等待新方向", "awaiting heading update") else sourceValueText(os, metric, observation.value), observation.receivedElapsedRealtime?.let { readingAge(os, it, now) }).joinToString(" · ")
}

internal fun sourceValueText(os: OsStore, metric: VesselMetricId, value: Any?): String = when (value) {
    null -> os.t("还没有读数", "no reading yet")
    is VesselPosition -> os.formatCoordinates(GeoPoint(value.latitude, value.longitude))
    is Number -> when (metric) {
        VesselMetricId.SOG, VesselMetricId.SPEED_THROUGH_WATER, VesselMetricId.APPARENT_WIND_SPEED, VesselMetricId.TRUE_WIND_SPEED, VesselMetricId.CURRENT_DRIFT, VesselMetricId.VMG_WIND, VesselMetricId.VMC_WAYPOINT -> os.formatSpeed(value.toDouble())
        VesselMetricId.DEPTH, VesselMetricId.UKC -> os.formatDepth(value.toDouble())
        VesselMetricId.COG, VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC, VesselMetricId.DEVICE_HEADING_TRUE, VesselMetricId.DEVICE_HEADING_MAGNETIC, VesselMetricId.TRUE_WIND_DIRECTION, VesselMetricId.CURRENT_SET, VesselMetricId.WAYPOINT_BEARING -> os.formatBearing(value.toDouble())
        VesselMetricId.WATER_TEMPERATURE, VesselMetricId.AIR_TEMPERATURE -> os.formatTemperature(value.toDouble())
        VesselMetricId.PRESSURE -> os.formatMetric("pressure", value.toDouble())
        VesselMetricId.WAYPOINT_DISTANCE, VesselMetricId.XTE, VesselMetricId.TOTAL_LOG, VesselMetricId.TRIP_LOG -> os.formatDistance(value.toDouble() * 1852.0)
        VesselMetricId.HEEL, VesselMetricId.PITCH, VesselMetricId.APPARENT_WIND_ANGLE, VesselMetricId.TRUE_WIND_ANGLE, VesselMetricId.RUDDER_ANGLE -> os.formatAngle(value.toDouble())
        VesselMetricId.RATE_OF_TURN -> "${decimal(value.toDouble())}°/min"
        VesselMetricId.ROLL_RATE, VesselMetricId.PITCH_RATE, VesselMetricId.YAW_RATE -> "${decimal(value.toDouble())}°/s"
        VesselMetricId.ROLL_PERIOD -> "${decimal(value.toDouble())} s"
        else -> decimal(value.toDouble())
    }
    else -> value.toString()
}

internal fun sourceObservation(metric: VesselMetricId, s: VesselDataSnapshot): VesselObservation<*>? = when (metric) {
    VesselMetricId.POSITION -> s.position
    VesselMetricId.SOG -> s.sogKnots; VesselMetricId.COG -> s.cogTrueDegrees
    VesselMetricId.HEADING_TRUE -> s.headingTrueDegrees; VesselMetricId.HEADING_MAGNETIC -> s.headingMagneticDegrees
    VesselMetricId.DEVICE_HEADING_TRUE -> s.deviceHeadingTrueDegrees; VesselMetricId.DEVICE_HEADING_MAGNETIC -> s.deviceHeadingMagneticDegrees
    VesselMetricId.DEPTH -> s.depthMeters; VesselMetricId.UKC -> s.derived.underKeelClearanceMeters
    VesselMetricId.APPARENT_WIND_SPEED -> s.apparentWind.speedKnots; VesselMetricId.APPARENT_WIND_ANGLE -> s.apparentWind.angleDegrees
    VesselMetricId.TRUE_WIND_SPEED -> s.trueWind.speedKnots; VesselMetricId.TRUE_WIND_ANGLE -> s.trueWind.angleDegrees; VesselMetricId.TRUE_WIND_DIRECTION -> s.trueWind.directionDegrees
    VesselMetricId.SPEED_THROUGH_WATER -> s.speedThroughWaterKnots; VesselMetricId.PRESSURE -> s.pressureHpa
    VesselMetricId.HEEL -> s.heelDegrees; VesselMetricId.PITCH -> s.pitchDegrees
    VesselMetricId.ROLL_RATE -> s.rollRateDegreesPerSecond; VesselMetricId.PITCH_RATE -> s.pitchRateDegreesPerSecond; VesselMetricId.YAW_RATE -> s.yawRateDegreesPerSecond
    VesselMetricId.RATE_OF_TURN -> s.rateOfTurnDegreesPerMinute; VesselMetricId.RUDDER_ANGLE -> s.rudderAngleDegrees
    VesselMetricId.WATER_TEMPERATURE -> s.waterTemperatureCelsius; VesselMetricId.AIR_TEMPERATURE -> s.airTemperatureCelsius
    VesselMetricId.CURRENT_SET -> s.currentSetTrueDegrees; VesselMetricId.CURRENT_DRIFT -> s.currentDriftKnots
    VesselMetricId.XTE -> s.crossTrackErrorNauticalMiles; VesselMetricId.WAYPOINT_BEARING -> s.waypointBearingTrueDegrees; VesselMetricId.WAYPOINT_DISTANCE -> s.waypointDistanceNauticalMiles
    VesselMetricId.DESTINATION_WAYPOINT -> s.destinationWaypoint; VesselMetricId.TOTAL_LOG -> s.totalLogNauticalMiles; VesselMetricId.TRIP_LOG -> s.tripLogNauticalMiles
    VesselMetricId.VMG_WIND -> s.derived.vmgToWindKnots; VesselMetricId.VMC_WAYPOINT -> s.derived.vmcToWaypointKnots
    VesselMetricId.MOTION_SCORE, VesselMetricId.ROLL_PERIOD -> s.motion
}

internal fun sourceMetricName(os: OsStore, metric: VesselMetricId): String = when (metric) {
    VesselMetricId.POSITION -> os.t("位置", "position")
    VesselMetricId.SOG -> os.t("对地航速", "speed over ground")
    VesselMetricId.COG -> os.t("对地航向", "course over ground")
    VesselMetricId.HEADING_TRUE -> os.t("真船首向", "true heading")
    VesselMetricId.HEADING_MAGNETIC -> os.t("磁船首向", "magnetic heading")
    VesselMetricId.DEPTH -> os.t("水深", "depth")
    VesselMetricId.UKC -> os.t("龙骨下余量", "under-keel clearance")
    VesselMetricId.SPEED_THROUGH_WATER -> os.t("对水航速", "speed through water")
    VesselMetricId.APPARENT_WIND_SPEED -> os.t("视风速", "apparent wind speed")
    VesselMetricId.APPARENT_WIND_ANGLE -> os.t("视风角", "apparent wind angle")
    VesselMetricId.TRUE_WIND_SPEED -> os.t("真风速", "true wind speed")
    VesselMetricId.TRUE_WIND_ANGLE -> os.t("真风角", "true wind angle")
    VesselMetricId.TRUE_WIND_DIRECTION -> os.t("真风向", "true wind direction")
    VesselMetricId.PRESSURE -> os.t("气压", "pressure")
    VesselMetricId.HEEL -> os.t("横倾", "heel")
    VesselMetricId.PITCH -> os.t("纵倾", "pitch")
    VesselMetricId.WATER_TEMPERATURE -> os.t("水温", "water temperature")
    VesselMetricId.AIR_TEMPERATURE -> os.t("气温", "air temperature")
    VesselMetricId.DEVICE_HEADING_TRUE -> os.t("手机真方位", "phone true heading")
    VesselMetricId.DEVICE_HEADING_MAGNETIC -> os.t("手机磁方位", "phone magnetic heading")
    VesselMetricId.RATE_OF_TURN -> os.t("转向率", "rate of turn")
    VesselMetricId.RUDDER_ANGLE -> os.t("舵角", "rudder angle")
    VesselMetricId.ROLL_RATE -> os.t("横摇角速度", "roll rate")
    VesselMetricId.PITCH_RATE -> os.t("纵摇角速度", "pitch rate")
    VesselMetricId.YAW_RATE -> os.t("艏摇角速度", "yaw rate")
    VesselMetricId.CURRENT_SET -> os.t("流向", "current set")
    VesselMetricId.CURRENT_DRIFT -> os.t("流速", "current drift")
    VesselMetricId.XTE -> os.t("横向偏差", "cross-track error")
    VesselMetricId.WAYPOINT_BEARING -> os.t("目标方位", "waypoint bearing")
    VesselMetricId.WAYPOINT_DISTANCE -> os.t("目标距离", "waypoint distance")
    VesselMetricId.DESTINATION_WAYPOINT -> os.t("目标航点", "destination waypoint")
    VesselMetricId.TOTAL_LOG -> os.t("总航程", "total log")
    VesselMetricId.TRIP_LOG -> os.t("本次航程", "trip log")
    VesselMetricId.VMG_WIND -> os.t("迎风有效速度", "VMG to wind")
    VesselMetricId.VMC_WAYPOINT -> os.t("朝目标有效速度", "VMC to waypoint")
    VesselMetricId.MOTION_SCORE -> os.t("运动强度", "motion score")
    VesselMetricId.ROLL_PERIOD -> os.t("横摇周期", "roll period")
}

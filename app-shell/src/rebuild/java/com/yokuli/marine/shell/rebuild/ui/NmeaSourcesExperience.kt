package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.location.PhoneLocationPhase

@Composable internal fun VesselSourceSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val connections by marine.vm.nmeaConnections.collectAsState()
    val phoneLocation by marine.vm.phoneLocationStatus.collectAsState()
    val now = rememberMarineClock()
    var expanded by remember { mutableStateOf<VesselMetricId?>(null) }
    val locked = state.active?.paused == false
    val nmeaConnected = connections.any { it.spec.receive && it.state in setOf(NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE) }
    PageBody {
        Label(os.t("船位", "position"), 28)
        Toggle(os.t("手机 GPS", "phone GPS"), os.positionSource == "phone", os.t("开启时请求权限并启动定位；关闭时停止。", "Requests access and starts location when switched on; stops when switched off."), enabled = !locked && os.positionSource in listOf("none", "phone")) { os.requestService(if (it) "gpsOn" else "gpsOff") }
        if (os.positionSource == "phone") Label(when (phoneLocation.phase) {
            PhoneLocationPhase.OFF -> os.t("定位服务正在准备", "preparing location service")
            PhoneLocationPhase.PERMISSION_REQUIRED -> os.t("需要精确定位权限", "precise location permission required")
            PhoneLocationPhase.PROVIDER_DISABLED -> os.t("系统定位已关闭，请开启 Android 定位", "Android location is off; enable location in system settings")
            PhoneLocationPhase.ERROR -> os.t("定位服务暂不可用", "location service is unavailable")
            PhoneLocationPhase.LISTENING -> phoneLocation.lastFixElapsedRealtime?.let { received -> if (now - received in 0..10_000) os.t("定位已更新", "position updated") else os.t("等待位置更新 · ", "waiting for position update · ") + readingAge(os, received, now) } ?: os.t("正在等待定位", "waiting for a position")
        }, 16, LocalMetro.current.muted)
        Toggle(os.t("NMEA 船位", "NMEA position"), os.positionSource == "nmea", if (nmeaConnected) os.t("使用已连接来源中的有效船位", "use a valid fix from connected sources") else os.t("先在 NMEA 中连接一个输入", "connect an input in NMEA first"), enabled = !locked && (os.positionSource == "nmea" || (os.positionSource == "none" && nmeaConnected))) { os.requestService(if (it) "sourceNmea" else "sourceOff") }
        Label(if (locked) os.t("锚警正在值守，暂停后可以更改船位来源。", "Pause the anchor watch before changing its position source.") else os.t("两项都可以关闭。船位来源不会自动切换；NMEA 的其他读数仍可继续更新。", "Both may be off. Position never changes source automatically; other NMEA readings can continue."), 16, LocalMetro.current.muted)

        if (os.positionSource != "phone") {
            val selectedConnection = state.vesselSettings.metricSourcePins["POSITION_CONNECTION"]
            val selected = connections.firstOrNull { it.spec.id == selectedConnection }
            if (selectedConnection != null) {
                Label(os.t("已选连接：", "selected connection: ") + (selected?.spec?.name ?: os.t("原连接不可用", "previous connection unavailable")), 23, LocalMetro.current.accent)
                Label(when {
                    os.positionSource != "nmea" -> os.t("船位来源已关闭，保留此选择。", "Position is off; this selection is retained.")
                    selected == null -> os.t("等待原连接恢复；不会自动改用其他连接。", "Waiting for the selected connection; another connection will not take over.")
                    !selected.requested || selected.state == NmeaConnectionState.DISCONNECTED -> os.t("该连接已停止，船位暂不可用。", "This connection is stopped; position is unavailable.")
                    selected.state in setOf(NmeaConnectionState.CONNECTING, NmeaConnectionState.RECONNECTING) -> os.t("等待该连接接通。", "Waiting for this connection.")
                    selected.state == NmeaConnectionState.ERROR -> os.t("该连接受阻，船位暂不可用。", "This connection is unavailable; position is unavailable.")
                    os.hub.state.value.fix("nmea")?.fresh(now) != true -> os.t("等待该连接的新鲜有效船位。", "Waiting for a fresh valid position from this connection.")
                    else -> os.t("正在使用此连接的船位。", "Using position from this connection.")
                }, 17, LocalMetro.current.muted)
            }
connections.filter { it.spec.receive }.forEach { connection ->
    val current = selectedConnection == connection.spec.id
    val online = connection.state in setOf(NmeaConnectionState.CONNECTED,NmeaConnectionState.CONNECTED_NO_DATA,NmeaConnectionState.CONNECTED_NO_FIX,NmeaConnectionState.STALE)
    ChoiceRow(connection.spec.name,current,if(online)os.t("已连接","connected")else os.t("连接停止或暂不可用","stopped or unavailable"),enabled=!locked&&online&&os.positionSource=="nmea") { marine.vm.selectNmeaPositionConnection(connection.spec.id) }
}
val positions = state.vesselData.candidates[VesselMetricId.POSITION].orEmpty().filter { it.source.transportProfileId == selectedConnection }
val selectedSource = state.vesselSettings.metricSourcePins[VesselMetricId.POSITION.name]?:state.vesselData.position.sourceIdentity?.persistentKey
positions.distinctBy{it.source.persistentKey}.forEach { candidate ->
    ChoiceRow(candidate.source.displayName,selectedSource==candidate.source.persistentKey,sourceCandidateText(os,VesselMetricId.POSITION,candidate,now),enabled=!locked&&os.positionSource=="nmea") { marine.vm.setNmeaMetricSource(VesselMetricId.POSITION,candidate.source.persistentKey) }
}
            if (selectedConnection != null) Label(os.t("船位固定在选中的连接与来源；信号丢失时不会自动改用另一条连接。", "Position stays on the selected connection and source. Losing its signal does not select another connection."), 16, LocalMetro.current.muted)
        }

        Label(os.t("各项读数", "measurements"), 28)
        val groups = state.vesselData.candidates.filter { it.value.isNotEmpty() || it.key.name in state.vesselSettings.metricSourcePins }.filterKeys { it != VesselMetricId.POSITION }
        if (groups.isEmpty()) Label(os.t("来源提供有效数据后，可以在这里选择每项读数的来源。", "When sources provide data, choose the source for each measurement here."), 19, LocalMetro.current.muted)
        groups.forEach { (metric,candidates) ->
            val pinned=state.vesselSettings.metricSourcePins[metric.name]
            val active=sourceObservation(metric,state.vesselData)
            val actual=candidates.firstOrNull{it.source.persistentKey==(pinned?:active?.sourceIdentity?.persistentKey)}
            MenuRow(sourceMetricName(os,metric),listOfNotNull(if(pinned==null)os.t("自动","automatic")else os.t("指定来源","selected source"),actual?.source?.displayName,actual?.let{sourceCandidateText(os,metric,it,now)}).joinToString(" · ")) { expanded=if(expanded==metric)null else metric }
            if(expanded==metric) {
                ChoiceRow(os.t("自动选择可用来源","automatically select an available source"),pinned==null) { marine.vm.setNmeaMetricSource(metric,null) }
                candidates.distinctBy{it.source.persistentKey}.forEach { candidate ->
                    ChoiceRow(candidate.source.displayName,pinned==candidate.source.persistentKey,sourceCandidateText(os,metric,candidate,now)) { marine.vm.setNmeaMetricSource(metric,candidate.source.persistentKey) }
                }
                if(pinned!=null&&candidates.none{it.source.persistentKey==pinned})Label(os.t("已选来源当前不可用，系统不会偷偷换成其他来源。","The selected source is unavailable. Another source will not silently take over."),15,LocalMetro.current.muted)
            }
        }
    }
}

private fun sourceCandidateText(os:OsStore,metric:VesselMetricId,candidate:VesselSourceCandidate<*>,now:Long):String {
    val value=when(val number=candidate.value) {
        is VesselPosition->os.formatCoordinates(GeoPoint(number.latitude,number.longitude))
        is Number->when(metric) {
            VesselMetricId.SOG,VesselMetricId.SPEED_THROUGH_WATER,VesselMetricId.APPARENT_WIND_SPEED,VesselMetricId.TRUE_WIND_SPEED,VesselMetricId.CURRENT_DRIFT,VesselMetricId.VMG_WIND,VesselMetricId.VMC_WAYPOINT->os.formatSpeed(number.toDouble())
            VesselMetricId.DEPTH,VesselMetricId.UKC->os.formatDepth(number.toDouble())
            VesselMetricId.COG,VesselMetricId.HEADING_TRUE,VesselMetricId.HEADING_MAGNETIC,VesselMetricId.DEVICE_HEADING_TRUE,VesselMetricId.DEVICE_HEADING_MAGNETIC,VesselMetricId.TRUE_WIND_DIRECTION,VesselMetricId.CURRENT_SET,VesselMetricId.WAYPOINT_BEARING->os.formatBearing(number.toDouble())
            VesselMetricId.WATER_TEMPERATURE,VesselMetricId.AIR_TEMPERATURE->os.formatTemperature(number.toDouble())
            VesselMetricId.PRESSURE->os.formatMetric("pressure",number.toDouble())
            VesselMetricId.WAYPOINT_DISTANCE,VesselMetricId.XTE,VesselMetricId.TOTAL_LOG,VesselMetricId.TRIP_LOG->os.formatDistance(number.toDouble()*1852.0)
            VesselMetricId.HEEL,VesselMetricId.PITCH,VesselMetricId.APPARENT_WIND_ANGLE,VesselMetricId.TRUE_WIND_ANGLE,VesselMetricId.RUDDER_ANGLE->os.formatAngle(number.toDouble())
            VesselMetricId.RATE_OF_TURN->"${decimal(number.toDouble())}°/min"
            VesselMetricId.ROLL_RATE,VesselMetricId.PITCH_RATE,VesselMetricId.YAW_RATE->"${decimal(number.toDouble())}°/s"
            VesselMetricId.ROLL_PERIOD->"${decimal(number.toDouble())} s"
            else->decimal(number.toDouble())
        }
        else->number.toString()
    }
    val quality=when(candidate.validity){CandidateValidity.ELIGIBLE->null;CandidateValidity.LOW_QUALITY->os.t("质量较低","lower quality");CandidateValidity.STALE->os.t("已过期","stale");CandidateValidity.DISABLED->os.t("已关闭","disabled");else->os.t("当前不可用","unavailable")}
    return listOfNotNull(value,readingAge(os,candidate.receivedElapsedRealtime,now),quality).joinToString(" · ")
}

private fun sourceObservation(metric:VesselMetricId,s:VesselDataSnapshot):VesselObservation<*>?=when(metric){
    VesselMetricId.SOG->s.sogKnots;VesselMetricId.COG->s.cogTrueDegrees
    VesselMetricId.HEADING_TRUE->s.headingTrueDegrees;VesselMetricId.HEADING_MAGNETIC->s.headingMagneticDegrees
    VesselMetricId.DEPTH->s.depthMeters;VesselMetricId.UKC->s.derived.underKeelClearanceMeters
    VesselMetricId.APPARENT_WIND_SPEED->s.apparentWind.speedKnots;VesselMetricId.APPARENT_WIND_ANGLE->s.apparentWind.angleDegrees
    VesselMetricId.TRUE_WIND_SPEED->s.trueWind.speedKnots;VesselMetricId.TRUE_WIND_ANGLE->s.trueWind.angleDegrees;VesselMetricId.TRUE_WIND_DIRECTION->s.trueWind.directionDegrees
    VesselMetricId.SPEED_THROUGH_WATER->s.speedThroughWaterKnots;VesselMetricId.PRESSURE->s.pressureHpa
    VesselMetricId.HEEL,VesselMetricId.PITCH->s.attitude;VesselMetricId.RATE_OF_TURN->s.rateOfTurnDegreesPerMinute
    VesselMetricId.WATER_TEMPERATURE->s.waterTemperatureCelsius;VesselMetricId.AIR_TEMPERATURE->s.airTemperatureCelsius
    else->null
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

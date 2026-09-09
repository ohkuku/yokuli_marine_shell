package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.anchorwatch.MarineContentTheme
import com.yokuli.anchorwatch.VesselSpatialInstrument
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.Reading
import com.yokuli.shell.contract.MeasurementUnitSystem
import java.util.Locale

/** Instruments consume the shared vessel observations; opening the app never selects a position source. */
@Composable fun InstrumentsScreen(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val history by os.hub.history.collectAsState()
    val data by os.hub.state.collectAsState()
    val now = rememberMarineClock()
    val owner = LocalLifecycleOwner.current
    var selected by remember { mutableStateOf<InstrumentTileId?>(null) }
    var mounting by remember { mutableStateOf(false) }
    var chooseTiles by remember { mutableStateOf(false) }
    var trend by rememberSaveable { mutableStateOf("sog") }
    val c = LocalMetro.current
    DisposableEffect(marine, owner) {
        fun update() = marine.vm.setTripLiveDisplayActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer); update()
        onDispose { owner.lifecycle.removeObserver(observer); marine.vm.setTripLiveDisplayActive(false) }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.t("仪表", "instruments"))
        Pivot(listOf(os.t("航行", "navigation"), os.t("帆航", "sailing"), os.t("空间", "spatial"), os.t("趋势", "trends"), os.t("我的仪表", "my instruments"))) { page ->
            PageBody {
                if (state.settings.demoMode) Label(os.t("演示 · 模拟读数", "DEMO · simulated readings"), 17, c.accent)
                when (page) {
                    0, 1 -> {
                        val tiles = if (page == 0) state.vesselSettings.navLayout else state.vesselSettings.sailingLayout
                        InstrumentGrid(os, state.vesselData, tiles, now) { selected = it }
                        MenuRow(os.t("读数从哪里来", "where readings come from"), os.t("查看各来源并选择船位或固定某项读数", "inspect sources, select position or pin a measurement"), "connect") { os.open("settings:sources") }
                    }
                    2 -> {
                        MarineContentTheme(os.chinese, c.accent, os.light) {
                            VesselSpatialInstrument(state, locked = false, confirmFrame = { mounting = true }, pauseAttitude = { marine.vm.pauseTripAttitude() })
                        }
                        InstrumentGrid(os, state.vesselData, listOf(InstrumentTileId.ROLL_RATE, InstrumentTileId.PITCH_RATE, InstrumentTileId.ROLL_PERIOD, InstrumentTileId.MOTION_SCORE), now) { selected = it }
                        Label(os.t("手机必须固定在船体上，姿态才代表船体运动。取下或移动手机后，请暂停姿态并重新确认安装。", "Mount the phone on the boat for its attitude to represent vessel motion. After moving it, pause attitude and confirm the mounting again."), 17, c.muted)
                        MetroButton(os.t("确认手机安装", "confirm phone mounting"), { mounting = true }, enabled = state.activeTrip?.paused != true)
                        MetroButton(os.t("手机顶部方向设为船首向", "use phone top direction as heading"), { marine.vm.alignPhoneHeadingToBow() })
                        MetroButton(os.t("用 NMEA 船首向对齐手机", "align phone with NMEA heading"), { marine.vm.alignPhoneHeadingToNmea() })
                        state.vesselCalibrationFeedback?.let { feedback ->
                            Label(when (feedback) {
                                "Trip attitude frame confirmed." -> os.t("姿态零点已确认。", feedback)
                                "No rotation-vector sample is available on this phone." -> os.t("当前手机没有可用的旋转向量样本。", feedback)
                                "Resume the trip before confirming a new attitude segment." -> os.t("请先继续航行记录，再确认新的姿态零点。", feedback)
                                "Trip attitude capture paused. Heading, GPS and pressure continue." -> os.t("姿态已暂停，船首向、GPS 与气压继续。", feedback)
                                "Trip attitude capture resumed." -> os.t("姿态采集已继续。", feedback)
                                else -> feedback
                            }, 18, c.accent)
                            MetroButton(os.t("关闭提示", "dismiss"), { marine.vm.clearVesselCalibrationFeedback() })
                        }
                    }
                    3 -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            listOf("sog", "depth", "aws", "tws", "pressure").forEach { metric -> Label(metricName(os, metric), 22, if (metric == trend) c.accent else c.muted, Modifier.clickable { trend = metric }.padding(vertical = 8.dp)) }
                        }
                        val current = data.readings[trend]?.takeIf { it.fresh(now) }?.displayed(os)
                        Label(current?.let { "${decimal(it.value)} ${it.unit}" } ?: "—", 48, c.accent)
                        ReadingTrace(os, history[trend].orEmpty().map { it.displayed(os) }, trend, now)
                        Label(os.t("最近 15 分钟的真实样本。断线和来源切换保留为空隙；没有收到的数据不会补成零。", "Actual samples from the last 15 minutes. Gaps and source changes stay visible; missing readings never become zero."), 17, c.muted)
                        InstrumentGrid(os, state.vesselData, state.vesselSettings.weatherLayout, now) { selected = it }
                    }
                    else -> {
                        if (state.vesselSettings.customLayout.isEmpty()) {
                            Label(os.t("只看你关心的读数", "the readings that matter to you"), 32, c.accent)
                            Label(os.t("把常用的读数放在一起。布局会保留，下次打开直接看到它们。", "Keep your favourite readings together. Your layout is saved for the next visit."), 21)
                        } else InstrumentGrid(os, state.vesselData, state.vesselSettings.customLayout, now) { selected = it }
                        MetroButton(os.t("选择仪表", "choose instruments"), { chooseTiles = true }, primary = true)
                    }
                }
            }
        }
    }
    selected?.let { tile ->
        val value = instrumentValue(os, state.vesselData, tile)
        Dialog(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().background(c.bg).border(1.dp, c.muted).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                Label(instrumentName(os, tile), 29)
                Label(value.text, if (tile == InstrumentTileId.POSITION) 23 else 46, c.accent)
                Label(observationStatus(os, value.observation, now), 18)
                Label(value.observation.sourceIdentity?.displayName ?: sourceName(os, value.observation.source), 21)
                value.observation.conflict?.let { Label(os.t("来源之间存在差异；可在设置中检查并固定来源。", "Sources disagree. Inspect them or pin a source in settings."), 17, c.muted) }
                if (tile == InstrumentTileId.UKC) Label(os.t("龙骨下余量使用共享吃水资料；未设置吃水时不计算。", "Under-keel clearance uses the shared draft. It is unavailable until draft is set."), 17, c.muted)
                MetroButton(os.t("来源设置", "source settings"), { selected = null; os.open("settings:sources") })
                MetroButton(os.t("完成", "done"), { selected = null })
            }
        }
    }
    if (mounting) {
        var axis by remember { mutableStateOf(DeviceBowAxis.TOP) }
        Dialog(onDismissRequest = { mounting = false }) {
            Column(Modifier.fillMaxWidth().background(c.bg).border(1.dp, c.muted).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                Label(os.t("确认安装", "confirm mounting"), 32)
                Label(os.t("将手机牢固固定在船上，并让船体保持你要作为零点的姿态。手机哪一边朝向船艏？", "Secure the phone to the boat, with the vessel in the attitude you want as zero. Which phone edge faces the bow?"), 20)
                DeviceBowAxis.entries.forEach { choice -> MetroButton(when (choice) { DeviceBowAxis.TOP -> os.t("顶部", "top"); DeviceBowAxis.BOTTOM -> os.t("底部", "bottom"); DeviceBowAxis.LEFT -> os.t("左侧", "left"); DeviceBowAxis.RIGHT -> os.t("右侧", "right") }, { axis = choice }, primary = axis == choice) }
                MetroButton(os.t("确认姿态零点", "confirm attitude zero"), { marine.vm.confirmTripAttitudeFrame(axis); mounting = false }, enabled = state.activeTrip?.paused != true)
                MetroButton(os.t("取消", "cancel"), { mounting = false })
            }
        }
    }
    if (chooseTiles) Dialog(onDismissRequest = { chooseTiles = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).background(c.bg).border(1.dp, c.muted).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Label(os.t("我的仪表", "my instruments"), 31)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                InstrumentTileId.entries.forEach { tile -> Toggle(instrumentName(os, tile), tile in state.vesselSettings.customLayout) { add ->
                    val previous = marine.vm.ui.value.vesselSettings
                    marine.vm.updateVesselDataSettings(previous.copy(customLayout = if (add) (previous.customLayout + tile).distinct() else previous.customLayout - tile))
                } }
            }
            MetroButton(os.t("完成", "done"), { chooseTiles = false }, primary = true)
        }
    }
}

private fun Reading.displayed(os: OsStore): Reading = if (unit == "kn" && os.measurementUnits == MeasurementUnitSystem.METRIC) copy(value = value * 1.852, unit = "km/h") else this

@Composable private fun InstrumentGrid(os: OsStore, data: VesselDataSnapshot, tiles: List<InstrumentTileId>, now: Long, select: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    tiles.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            row.forEach { tile ->
                val value = instrumentValue(os, data, tile)
                Column(Modifier.weight(1f).clickable { select(tile) }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(instrumentName(os, tile), 16, c.muted)
                    Label(value.text, if (tile == InstrumentTileId.POSITION) 18 else 34, if (value.observation.freshness == VesselDataFreshness.FRESH) c.fg else c.muted)
                    Label(observationStatus(os, value.observation, now), 12, c.muted)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private data class InstrumentValue(val text: String, val observation: VesselObservation<*>)
private fun instrumentValue(os: OsStore, d: VesselDataSnapshot, tile: InstrumentTileId): InstrumentValue {
    fun <T> value(o: VesselObservation<T>, format: (T) -> String): InstrumentValue = InstrumentValue(o.value?.takeIf { o.freshness == VesselDataFreshness.FRESH }?.let(format) ?: "—", o)
    fun number(o: VesselObservation<Double>, unit: String = "°") = value(o) { if (it.isFinite()) String.format(Locale.US, "%.1f%s", it, unit) else "—" }
    fun speed(o: VesselObservation<Double>) = value(o, os::formatSpeed)
    fun depth(o: VesselObservation<Double>) = value(o, os::formatDepth)
    fun distance(o: VesselObservation<Double>) = value(o) { os.formatDistance(it * 1852.0) }
    return when (tile) {
        InstrumentTileId.SOG -> speed(d.sogKnots)
        InstrumentTileId.COG -> number(d.cogTrueDegrees)
        InstrumentTileId.HEADING -> number(d.headingTrueDegrees)
        InstrumentTileId.DEPTH -> depth(d.depthMeters)
        InstrumentTileId.UKC -> depth(d.derived.underKeelClearanceMeters)
        InstrumentTileId.POSITION -> value(d.position) { os.formatCoordinates(GeoPoint(it.latitude, it.longitude)) }
        InstrumentTileId.BOAT_SPEED -> speed(d.speedThroughWaterKnots)
        InstrumentTileId.TRUE_WIND_SPEED -> speed(d.trueWind.speedKnots)
        InstrumentTileId.TRUE_WIND_DIRECTION -> number(d.trueWind.directionDegrees)
        InstrumentTileId.TRUE_WIND_ANGLE -> number(d.trueWind.angleDegrees)
        InstrumentTileId.APPARENT_WIND_SPEED -> speed(d.apparentWind.speedKnots)
        InstrumentTileId.APPARENT_WIND_ANGLE -> number(d.apparentWind.angleDegrees)
        InstrumentTileId.HEEL -> value(d.attitude) { "${decimal(it.heelDegrees)}°" }
        InstrumentTileId.PITCH -> value(d.attitude) { "${decimal(it.pitchDegrees)}°" }
        InstrumentTileId.ROLL_RATE -> value(d.attitude) { "${decimal(it.rollRateDegreesPerSecond)}°/s" }
        InstrumentTileId.PITCH_RATE -> value(d.attitude) { "${decimal(it.pitchRateDegreesPerSecond)}°/s" }
        InstrumentTileId.ROLL_PERIOD -> value(d.motion) { it.dominantRollPeriodSeconds?.let { p -> "${decimal(p)} s" } ?: "—" }
        InstrumentTileId.MOTION_SCORE -> value(d.motion) { decimal(it.score) }
        InstrumentTileId.IMPACT_COUNT -> value(d.motion) { it.impactCandidateCount.toString() }
        InstrumentTileId.PRESSURE -> number(d.pressureHpa, " hPa")
        InstrumentTileId.PRESSURE_TREND_1H -> number(d.derived.pressureTrend1hHpa, " hPa")
        InstrumentTileId.PRESSURE_TREND_3H -> number(d.derived.pressureTrend3hHpa, " hPa")
        InstrumentTileId.PRESSURE_TREND_6H -> number(d.derived.pressureTrend6hHpa, " hPa")
        InstrumentTileId.RATE_OF_TURN -> number(d.rateOfTurnDegreesPerMinute, "°/min")
        InstrumentTileId.RUDDER_ANGLE -> number(d.rudderAngleDegrees)
        InstrumentTileId.WATER_TEMPERATURE -> number(d.waterTemperatureCelsius, "°C")
        InstrumentTileId.AIR_TEMPERATURE -> number(d.airTemperatureCelsius, "°C")
        InstrumentTileId.CURRENT_SET -> number(d.currentSetTrueDegrees)
        InstrumentTileId.CURRENT_DRIFT -> speed(d.currentDriftKnots)
        InstrumentTileId.CROSS_TRACK_ERROR -> value(d.crossTrackErrorNauticalMiles) { val side = if (it < 0) "−" else ""; side + os.formatDistance(kotlin.math.abs(it) * 1852.0) }
        InstrumentTileId.WAYPOINT_BEARING -> number(d.waypointBearingTrueDegrees)
        InstrumentTileId.WAYPOINT_DISTANCE -> distance(d.waypointDistanceNauticalMiles)
        InstrumentTileId.TOTAL_LOG -> distance(d.totalLogNauticalMiles)
        InstrumentTileId.TRIP_LOG -> distance(d.tripLogNauticalMiles)
        InstrumentTileId.VMG -> speed(d.derived.vmgToWindKnots)
        InstrumentTileId.VMC -> speed(d.derived.vmcToWaypointKnots)
    }
}

private fun observationStatus(os: OsStore, observation: VesselObservation<*>, now: Long): String {
    val status = when (observation.freshness) { VesselDataFreshness.FRESH -> os.t("实时", "live"); VesselDataFreshness.HELD -> os.t("暂存读数", "held reading"); VesselDataFreshness.STALE -> os.t("已过期", "stale"); VesselDataFreshness.UNAVAILABLE -> os.t("不可用", "unavailable") }
    return status + (observation.receivedElapsedRealtime?.let { " · " + readingAge(os, it, now) } ?: "")
}
private fun sourceName(os: OsStore, source: VesselDataSource): String = when (source) {
    VesselDataSource.NONE -> os.t("未收到数据", "no data received")
    VesselDataSource.BOAT_NMEA -> "NMEA"
    VesselDataSource.PHONE_GNSS -> os.t("手机 GPS", "phone GPS")
    VesselDataSource.PHONE_IMU -> os.t("手机运动传感器", "phone motion sensors")
    VesselDataSource.PHONE_MAGNETOMETER -> os.t("手机磁传感器", "phone magnetometer")
    VesselDataSource.PHONE_BAROMETER -> os.t("手机气压计", "phone barometer")
    VesselDataSource.DERIVED -> os.t("根据可用观测计算", "derived from available observations")
    VesselDataSource.DEMO -> os.t("演示数据", "demo data")
}
private fun instrumentName(os: OsStore, tile: InstrumentTileId): String = when (tile) {
    InstrumentTileId.SOG -> os.t("对地航速", "speed over ground")
    InstrumentTileId.COG -> os.t("对地航向", "course over ground")
    InstrumentTileId.HEADING -> os.t("真船首向", "true heading")
    InstrumentTileId.DEPTH -> os.t("水深", "depth")
    InstrumentTileId.UKC -> os.t("龙骨下余量", "under-keel clearance")
    InstrumentTileId.POSITION -> os.t("船位", "position")
    InstrumentTileId.BOAT_SPEED -> os.t("对水航速", "speed through water")
    InstrumentTileId.TRUE_WIND_SPEED -> os.t("真风速", "true wind speed")
    InstrumentTileId.TRUE_WIND_DIRECTION -> os.t("真风向", "true wind direction")
    InstrumentTileId.TRUE_WIND_ANGLE -> os.t("真风角", "true wind angle")
    InstrumentTileId.APPARENT_WIND_SPEED -> os.t("视风速", "apparent wind speed")
    InstrumentTileId.APPARENT_WIND_ANGLE -> os.t("视风角", "apparent wind angle")
    InstrumentTileId.HEEL -> os.t("横倾", "heel")
    InstrumentTileId.PITCH -> os.t("纵倾", "pitch")
    InstrumentTileId.ROLL_RATE -> os.t("横摇角速度", "roll rate")
    InstrumentTileId.PITCH_RATE -> os.t("纵摇角速度", "pitch rate")
    InstrumentTileId.ROLL_PERIOD -> os.t("横摇周期", "roll period")
    InstrumentTileId.MOTION_SCORE -> os.t("运动强度", "motion score")
    InstrumentTileId.IMPACT_COUNT -> os.t("冲击候选次数", "possible impacts")
    InstrumentTileId.PRESSURE -> os.t("气压", "pressure")
    InstrumentTileId.PRESSURE_TREND_1H -> os.t("1 小时气压变化", "pressure change · 1 h")
    InstrumentTileId.PRESSURE_TREND_3H -> os.t("3 小时气压变化", "pressure change · 3 h")
    InstrumentTileId.PRESSURE_TREND_6H -> os.t("6 小时气压变化", "pressure change · 6 h")
    InstrumentTileId.RATE_OF_TURN -> os.t("转向率", "rate of turn")
    InstrumentTileId.RUDDER_ANGLE -> os.t("舵角", "rudder angle")
    InstrumentTileId.WATER_TEMPERATURE -> os.t("水温", "water temperature")
    InstrumentTileId.AIR_TEMPERATURE -> os.t("气温", "air temperature")
    InstrumentTileId.CURRENT_SET -> os.t("流向", "current set")
    InstrumentTileId.CURRENT_DRIFT -> os.t("流速", "current drift")
    InstrumentTileId.CROSS_TRACK_ERROR -> os.t("横向偏差", "cross-track error")
    InstrumentTileId.WAYPOINT_BEARING -> os.t("目标方位", "waypoint bearing")
    InstrumentTileId.WAYPOINT_DISTANCE -> os.t("目标距离", "waypoint distance")
    InstrumentTileId.TOTAL_LOG -> os.t("总航程", "total log")
    InstrumentTileId.TRIP_LOG -> os.t("本次航程", "trip log")
    InstrumentTileId.VMG -> os.t("迎风有效速度", "VMG to wind")
    InstrumentTileId.VMC -> os.t("朝目标有效速度", "VMC to waypoint")
}

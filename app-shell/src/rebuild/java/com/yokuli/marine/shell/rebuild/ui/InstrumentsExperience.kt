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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.marine.shell.rebuild.*
import java.util.Locale

/** 中文：仪表只消费全局观测；布局属于仪表应用，船位来源和航行会话属于系统。 */
@Composable fun InstrumentsScreen(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val history by os.hub.history.collectAsState()
    val data by os.hub.state.collectAsState()
    val now = rememberMarineClock()
    val owner = LocalLifecycleOwner.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var mounting by rememberSaveable { mutableStateOf(false) }
    var chooseTiles by rememberSaveable { mutableStateOf(false) }
    // 中文：选择弹窗独立编辑草稿；快速连续选择不依赖异步 DataStore 回流。
    var pickerTileNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var trend by rememberSaveable { mutableStateOf("sog") }
    val c = LocalMetro.current
    fun closeLayer(): Boolean = when {
        mounting -> { mounting = false; true }
        chooseTiles -> { chooseTiles = false; true }
        selected != null -> { selected = null; true }
        editing -> { editing = false; true }
        else -> false
    }
    BindInternalAppInputHandler { input -> input == ShellInput.BACK && closeLayer() }
    AppBackHandler(mounting || chooseTiles || selected != null || editing) { closeLayer() }
    DisposableEffect(marine, owner) {
        fun update() = marine.vm.setTripLiveDisplayActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer); update()
        onDispose { owner.lifecycle.removeObserver(observer); marine.vm.setTripLiveDisplayActive(false) }
    }
    fun saveLayout(layout: List<InstrumentTileId>) {
        marine.vm.updateVesselDataSettings(marine.vm.ui.value.vesselSettings.copy(customLayout = layout.distinct()))
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.t("仪表", "instruments"))
        Pivot(listOf(os.t("航行", "navigation"), os.t("帆航", "sailing"), os.t("姿态", "attitude"), os.t("趋势", "trends"), os.t("我的", "mine"))) { page ->
            if (page == 4) {
                InstrumentBoard(os, state.vesselData, state.vesselSettings.customLayout, now, editing,
                    edit = { editing = !editing }, add = {
                        pickerTileNames = marine.vm.ui.value.vesselSettings.customLayout.map { it.name }
                        chooseTiles = true
                    }, save = ::saveLayout,
                    select = { selected = it.name })
            } else PageBody {
                if (state.settings.demoMode) Label(os.t("演示 · 模拟读数", "DEMO · simulated readings"), 17, c.accent)
                when (page) {
                    0 -> {
                        MarineCompass(os, state.vesselData)
                        InstrumentGrid(os, state.vesselData, state.vesselSettings.navLayout.filterNot { it in setOf(InstrumentTileId.HEADING, InstrumentTileId.COG) }, now) { selected = it.name }
                    }
                    1 -> {
                        WindRose(os, state.vesselData)
                        InstrumentGrid(os, state.vesselData, listOf(InstrumentTileId.BOAT_SPEED, InstrumentTileId.VMG, InstrumentTileId.SOG, InstrumentTileId.HEEL), now) { selected = it.name }
                    }
                    2 -> {
                        AttitudeHorizon(os, state.vesselData)
                        InstrumentGrid(os, state.vesselData, listOf(InstrumentTileId.ROLL_RATE, InstrumentTileId.PITCH_RATE, InstrumentTileId.ROLL_PERIOD, InstrumentTileId.MOTION_SCORE), now) { selected = it.name }
                        Label(os.t("固定手机并确认零点后，横倾和纵倾才代表船体姿态。", "Secure the phone and confirm zero so heel and pitch represent your boat."), 17, c.muted)
                        MetroButton(os.t("安装与校准", "mount & calibrate"), { mounting = true })
                        state.vesselCalibrationFeedback?.let { feedback ->
                            Label(when (feedback) {
                                "Trip attitude frame confirmed." -> os.t("姿态零点已确认。", feedback)
                                "No rotation-vector sample is available on this phone." -> os.t("当前手机没有可用的姿态传感器。", feedback)
                                "Resume the trip before confirming a new attitude segment." -> os.t("请先继续航行，再确认姿态零点。", feedback)
                                "Trip attitude capture paused. Heading, GPS and pressure continue." -> os.t("姿态采集已暂停。", feedback)
                                "Trip attitude capture resumed." -> os.t("姿态采集已继续。", feedback)
                                else -> feedback
                            }, 17, c.accent)
                        }
                    }
                    3 -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            listOf("sog", "aws", "tws", "pressure").forEach { metric -> Label(metricName(os, metric), 24, if (metric == trend) c.fg else c.muted, Modifier.clickable { trend = metric }.padding(vertical = 8.dp)) }
                        }
                        val current = data.readings[trend]?.takeIf { it.fresh(now) }
                        Label(os.formatMetric(trend, current?.value), 48, c.accent)
                        ReadingTrace(os, history[trend].orEmpty(), trend, now)
                        Label(os.t("最近 15 分钟 · 拖动查看当时的读数与来源", "last 15 minutes · drag to inspect a reading and its source"), 16, c.muted)
                        InstrumentGrid(os, state.vesselData, state.vesselSettings.weatherLayout, now) { selected = it.name }
                    }
                }
            }
        }
    }
    selected?.let { name -> InstrumentTileId.entries.firstOrNull { it.name == name } }?.let { tile ->
        val value = instrumentValue(os, state.vesselData, tile)
        Dialog(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().background(c.bg).border(2.dp, c.fg).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                Label(instrumentName(os, tile), 32)
                InstrumentGauge(os, state.vesselData, tile)
                Label(value.text, if (tile == InstrumentTileId.POSITION) 23 else 48, c.accent)
                Label(observationStatus(os, value.observation, now), 18)
                Label(os.t("来源", "source"), 14, c.muted)
                Label(value.observation.sourceIdentity?.displayName ?: sourceName(os, value.observation.source), 21)
                value.observation.conflict?.let { Label(os.t("多个来源的读数有差异。可在 NMEA 的数据来源中检查。", "Sources disagree. Inspect their readings in NMEA data sources."), 17, c.muted) }
                if (tile == InstrumentTileId.UKC) Label(os.t("龙骨下余量根据水深与船舶吃水计算。", "Under-keel clearance uses measured depth and your boat's draft."), 17, c.muted)
                if (tile !in state.vesselSettings.customLayout) MetroButton(os.t("添加到我的仪表", "add to my instruments"), { saveLayout(state.vesselSettings.customLayout + tile) })
                MetroButton(os.t("完成", "done"), { selected = null }, primary = true)
            }
        }
    }
    if (mounting) {
        var axis by rememberSaveable { mutableStateOf(DeviceBowAxis.TOP) }
        Dialog(onDismissRequest = { mounting = false }) {
            Column(Modifier.fillMaxWidth().background(c.bg).border(2.dp, c.fg).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                Label(os.t("安装与校准", "mount & calibrate"), 32)
                Label(os.t("固定手机，让船保持作为零点的姿态。选择朝向船艏的手机边缘。", "Secure the phone with the boat at your chosen zero attitude. Select the phone edge facing the bow."), 20)
                DeviceBowAxis.entries.forEach { choice ->
                    InstrumentChoice(when (choice) { DeviceBowAxis.TOP -> os.t("顶部", "top"); DeviceBowAxis.BOTTOM -> os.t("底部", "bottom"); DeviceBowAxis.LEFT -> os.t("左侧", "left"); DeviceBowAxis.RIGHT -> os.t("右侧", "right") }, axis == choice) { axis = choice }
                }
                MetroButton(os.t("确认姿态零点", "confirm attitude zero"), { marine.vm.confirmTripAttitudeFrame(axis); mounting = false }, primary = true, enabled = state.activeTrip?.paused != true)
                MetroButton(os.t("手机顶部对齐船首向", "align heading with phone top"), { marine.vm.alignPhoneHeadingToBow() })
                MetroButton(os.t("用 NMEA 船首向校准", "calibrate heading from NMEA"), { marine.vm.alignPhoneHeadingToNmea() })
                MetroButton(os.t("暂停姿态采集", "pause attitude capture"), { marine.vm.pauseTripAttitude(); mounting = false })
                MetroButton(os.t("取消", "cancel"), { mounting = false })
            }
        }
    }
    if (chooseTiles) Dialog(onDismissRequest = { chooseTiles = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).background(c.bg).border(2.dp, c.fg).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Label(os.t("添加仪表", "add instruments"), 32)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                InstrumentTileId.entries.forEach { tile ->
                    InstrumentChoice(instrumentName(os, tile), tile.name in pickerTileNames) {
                        pickerTileNames = if (tile.name in pickerTileNames) pickerTileNames - tile.name else pickerTileNames + tile.name
                    }
                }
            }
            MetroButton(os.t("完成", "done"), {
                saveLayout(pickerTileNames.mapNotNull { name -> InstrumentTileId.entries.firstOrNull { it.name == name } })
                chooseTiles = false
            }, primary = true)
            MetroButton(os.t("取消", "cancel"), { chooseTiles = false })
        }
    }
}

@Composable private fun InstrumentChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalMetro.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(Modifier.size(24.dp).border(2.dp, c.fg).padding(5.dp)) { if (selected) Box(Modifier.fillMaxSize().background(c.accent)) }
        Label(title, 22)
    }
}

/** 中文：编辑时在本地重排，拖动结束一次性保存；稳定的枚举 ID 保证读数不会跟随位置串位。 */
@Composable private fun InstrumentBoard(os: OsStore, data: VesselDataSnapshot, saved: List<InstrumentTileId>, now: Long, editing: Boolean, edit: () -> Unit, add: () -> Unit, save: (List<InstrumentTileId>) -> Unit, select: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    var order by remember(saved) { mutableStateOf(saved.distinct()) }
    var dragging by remember { mutableStateOf<InstrumentTileId?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentOrder by rememberUpdatedState(order)
    val currentSave by rememberUpdatedState(save)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(os.t("添加", "add"), add, Modifier.weight(1f), primary = true)
                if (order.isNotEmpty()) MetroButton(if (editing) os.t("完成", "done") else os.t("排列", "arrange"), edit, Modifier.weight(1f))
            }
            if (editing && order.isNotEmpty()) Label(os.t("长按仪表拖动排序，也可使用上移和下移。", "hold and drag to reorder, or use move up / down"), 15, c.muted, Modifier.padding(top = 12.dp))
            if (order.isEmpty()) {
                Spacer(Modifier.height(35.dp))
                Label(os.t("你的驾驶台", "your helm"), 36)
                Label(os.t("把常看的仪表放在一起。选择、移除与顺序都会保存。", "Keep the instruments you use together. Your selection and order are saved."), 21, c.muted, Modifier.padding(top = 14.dp))
            }
        }
        items(order, key = { it.name }) { tile ->
            var rowHeight by remember { mutableIntStateOf(1) }
            val value = instrumentValue(os, data, tile)
            val isDragging = dragging == tile
            Column(Modifier.fillMaxWidth().zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer { translationY = if (isDragging) dragOffset else 0f; alpha = if (isDragging) .86f else 1f }
                .background(if (isDragging) c.panel else c.bg)
                .onSizeChanged { rowHeight = it.height }
                .then(if (editing) Modifier.pointerInput(tile, editing) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = tile; dragOffset = 0f },
                        onDragEnd = { currentSave(currentOrder); dragging = null; dragOffset = 0f },
                        onDragCancel = { currentSave(currentOrder); dragging = null; dragOffset = 0f },
                    ) { change, amount ->
                        change.consume(); dragOffset += amount.y
                        val index = currentOrder.indexOf(tile)
                        val step = rowHeight + 16.dp.toPx()
                        val direction = when { dragOffset > step * .6f -> 1; dragOffset < -step * .6f -> -1; else -> 0 }
                        val next = index + direction
                        if (direction != 0 && next in currentOrder.indices) {
                            order = currentOrder.toMutableList().apply { removeAt(index); add(next, tile) }
                            dragOffset -= step * direction
                        }
                    }
                } else Modifier.clickable { select(tile) }).padding(vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label(instrumentName(os, tile), 22, modifier = Modifier.weight(1f))
                    if (editing) Label("≡", 32, c.muted)
                }
                Label(value.text, if (tile == InstrumentTileId.POSITION) 22 else 46, if (value.observation.freshness == VesselDataFreshness.FRESH) c.accent else c.muted)
                InstrumentGauge(os, data, tile)
                Label(observationStatus(os, value.observation, now), 13, c.muted)
                if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    val index = order.indexOf(tile)
                    if (index > 0) Label(os.t("上移", "up"), 17, c.accent, Modifier.clickable { val changed = order.toMutableList().apply { removeAt(index); add(index - 1, tile) }; order = changed; save(changed) }.padding(vertical = 8.dp))
                    if (index < order.lastIndex) Label(os.t("下移", "down"), 17, c.accent, Modifier.clickable { val changed = order.toMutableList().apply { removeAt(index); add(index + 1, tile) }; order = changed; save(changed) }.padding(vertical = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Label(os.t("移除", "remove"), 17, c.muted, Modifier.clickable { val changed = order - tile; order = changed; save(changed) }.padding(vertical = 8.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.muted.copy(alpha = .2f)))
            }
        }
    }
}

@Composable private fun InstrumentGrid(os: OsStore, data: VesselDataSnapshot, tiles: List<InstrumentTileId>, now: Long, select: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    tiles.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            row.forEach { tile ->
                val value = instrumentValue(os, data, tile)
                Column(Modifier.weight(1f).clickable { select(tile) }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(instrumentName(os, tile), 16, c.muted)
                    Label(value.text, if (tile == InstrumentTileId.POSITION) 18 else 34, if (value.observation.freshness == VesselDataFreshness.FRESH) c.fg else c.muted)
                    InstrumentGauge(os, data, tile)
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
    fun bearing(o: VesselObservation<Double>) = value(o, os::formatBearing)
    fun angle(o: VesselObservation<Double>) = value(o, os::formatAngle)
    fun speed(o: VesselObservation<Double>) = value(o, os::formatSpeed)
    fun depth(o: VesselObservation<Double>) = value(o, os::formatDepth)
    fun distance(o: VesselObservation<Double>) = value(o) { os.formatDistance(it * 1852.0) }
    return when (tile) {
        InstrumentTileId.SOG -> speed(d.sogKnots)
        InstrumentTileId.COG -> bearing(d.cogTrueDegrees)
        InstrumentTileId.HEADING -> bearing(d.headingTrueDegrees)
        InstrumentTileId.DEPTH -> depth(d.depthMeters)
        InstrumentTileId.UKC -> depth(d.derived.underKeelClearanceMeters)
        InstrumentTileId.POSITION -> value(d.position) { os.formatCoordinates(GeoPoint(it.latitude, it.longitude)) }
        InstrumentTileId.BOAT_SPEED -> speed(d.speedThroughWaterKnots)
        InstrumentTileId.TRUE_WIND_SPEED -> speed(d.trueWind.speedKnots)
        InstrumentTileId.TRUE_WIND_DIRECTION -> bearing(d.trueWind.directionDegrees)
        InstrumentTileId.TRUE_WIND_ANGLE -> angle(d.trueWind.angleDegrees)
        InstrumentTileId.APPARENT_WIND_SPEED -> speed(d.apparentWind.speedKnots)
        InstrumentTileId.APPARENT_WIND_ANGLE -> angle(d.apparentWind.angleDegrees)
        InstrumentTileId.HEEL -> value(d.attitude) { os.formatAngle(it.heelDegrees) }
        InstrumentTileId.PITCH -> value(d.attitude) { os.formatAngle(it.pitchDegrees) }
        InstrumentTileId.ROLL_RATE -> value(d.attitude) { "${decimal(it.rollRateDegreesPerSecond)}°/s" }
        InstrumentTileId.PITCH_RATE -> value(d.attitude) { "${decimal(it.pitchRateDegreesPerSecond)}°/s" }
        InstrumentTileId.ROLL_PERIOD -> value(d.motion) { it.dominantRollPeriodSeconds?.let { p -> "${decimal(p)} s" } ?: "—" }
        InstrumentTileId.MOTION_SCORE -> value(d.motion) { decimal(it.score) }
        InstrumentTileId.IMPACT_COUNT -> value(d.motion) { it.impactCandidateCount.toString() }
        InstrumentTileId.PRESSURE -> value(d.pressureHpa) { os.formatMetric("pressure", it) }
        InstrumentTileId.PRESSURE_TREND_1H -> number(d.derived.pressureTrend1hHpa, " hPa")
        InstrumentTileId.PRESSURE_TREND_3H -> number(d.derived.pressureTrend3hHpa, " hPa")
        InstrumentTileId.PRESSURE_TREND_6H -> number(d.derived.pressureTrend6hHpa, " hPa")
        InstrumentTileId.RATE_OF_TURN -> number(d.rateOfTurnDegreesPerMinute, "°/min")
        InstrumentTileId.RUDDER_ANGLE -> angle(d.rudderAngleDegrees)
        InstrumentTileId.WATER_TEMPERATURE -> value(d.waterTemperatureCelsius, os::formatTemperature)
        InstrumentTileId.AIR_TEMPERATURE -> value(d.airTemperatureCelsius, os::formatTemperature)
        InstrumentTileId.CURRENT_SET -> bearing(d.currentSetTrueDegrees)
        InstrumentTileId.CURRENT_DRIFT -> speed(d.currentDriftKnots)
        InstrumentTileId.CROSS_TRACK_ERROR -> value(d.crossTrackErrorNauticalMiles) { val side = if (it < 0) "−" else ""; side + os.formatDistance(kotlin.math.abs(it) * 1852.0) }
        InstrumentTileId.WAYPOINT_BEARING -> bearing(d.waypointBearingTrueDegrees)
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

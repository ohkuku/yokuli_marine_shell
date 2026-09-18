package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import com.yokuli.marine.shell.rebuild.data.Reading
import com.yokuli.marine.shell.rebuild.*
import java.util.Locale

/** 中文：仪表只消费全局观测；布局属于仪表应用，船位来源和航行会话属于系统。 */
@Composable fun InstrumentsScreen(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
    val history by os.hub.history.collectAsState()
    val data by os.hub.state.collectAsState()
    val now = rememberMarineClock()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseTiles by rememberSaveable { mutableStateOf(false) }
    // 中文：选择弹窗独立编辑草稿；快速连续选择不依赖异步 DataStore 回流。
    var pickerTileNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var trend by rememberSaveable { mutableStateOf("sog") }
    var chooseTrend by rememberSaveable { mutableStateOf(false) }
    val availableTrends = InstrumentTrendCatalog.available(data.readings, history, now)
    val activeTrend = InstrumentTrendCatalog.selected(trend, availableTrends, data.readings, now)
    LaunchedEffect(activeTrend?.key, trend) {
        if (activeTrend != null) trend = activeTrend.key
        else if (InstrumentTrendCatalog.metrics.none { it.key == trend }) trend = "sog"
    }
    val c = LocalMetro.current
    fun closeLayer(): Boolean = when {
        chooseTrend -> { chooseTrend = false; true }
        chooseTiles -> { chooseTiles = false; true }
        selected != null -> { selected = null; true }
        editing -> { editing = false; true }
        else -> false
    }
    BindInternalAppInputHandler { input -> input == ShellInput.BACK && closeLayer() }
    AppBackHandler(chooseTrend || chooseTiles || selected != null || editing) { closeLayer() }
    fun saveLayout(layout: List<InstrumentTileId>) {
        marine.services.preferences.setInstrumentLayout(layout)
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.title(AppId.INSTRUMENTS))
        Pivot(listOf(os.t("航行", "navigation"), os.t("帆航", "sailing"), os.t("姿态", "attitude"), os.t("趋势", "trends"), os.t("我的", "mine"))) { page ->
            if (page == 4) {
                InstrumentBoard(os, state.vesselData, state.vesselSettings.customLayout, now, editing,
                    edit = { editing = !editing }, add = {
                        pickerTileNames = marine.services.state.value.vesselSettings.customLayout.map { it.name }
                        chooseTiles = true
                    }, save = ::saveLayout,
                    select = { selected = it.name })
            } else PageBody {
                if (state.settings.demoMode) Label(os.t("演示 · 模拟读数", "DEMO · simulated readings"), 17, c.accent)
                when (page) {
                    0 -> {
                        val speed = instrumentValue(os, state.vesselData, InstrumentTileId.SOG)
                        Row(Modifier.fillMaxWidth().clickable { selected = InstrumentTileId.SOG.name }, verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Label(os.t("对地航速", "speed over ground"), 16, c.muted)
                                Label(speed.text, 56, if (speed.observation.displayIsLive()) c.accent else c.muted)
                            }
                            Label(observationStatus(os, speed.observation, now), 13, c.muted, Modifier.widthIn(max = 145.dp).padding(bottom = 9.dp))
                        }
                        MarineCompass(os, state.vesselData)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label(os.t("查看船首向", "heading details"), 17, c.accent, Modifier.clickable { selected = InstrumentTileId.HEADING.name }.padding(vertical = 10.dp))
                            Label(os.t("固定手机", "mount phone"), 17, c.accent, Modifier.clickable { os.openLinked("data_center:phone") }.padding(vertical = 10.dp))
                        }
                        InstrumentGrid(os, state.vesselData, state.vesselSettings.navLayout.filterNot { it in setOf(InstrumentTileId.HEADING, InstrumentTileId.COG, InstrumentTileId.SOG) }, now) { selected = it.name }
                    }
                    1 -> {
                        WindRose(os, state.vesselData)
                        InstrumentGrid(os, state.vesselData, listOf(InstrumentTileId.BOAT_SPEED, InstrumentTileId.VMG, InstrumentTileId.SOG, InstrumentTileId.HEEL), now) { selected = it.name }
                    }
                    2 -> {
                        AttitudeHorizon(os, state.vesselData)
                        InstrumentGrid(os, state.vesselData, listOf(InstrumentTileId.ROLL_RATE, InstrumentTileId.PITCH_RATE, InstrumentTileId.ROLL_PERIOD, InstrumentTileId.MOTION_SCORE), now) { selected = it.name }
                        Label(os.t("手机与船体固定后，确认安装方向；横倾不会被当作零度清除。", "Secure the phone to the boat and confirm its mounting direction. Existing heel is preserved."), 17, c.muted)
                        MetroButton(os.t("安装与校准", "mount & calibrate"), { os.openLinked("data_center:phone") })
                        state.vesselCalibrationFeedback?.let { feedback ->
                            Label(when (feedback) {
                                "Trip attitude frame confirmed." -> os.t("船体安装方向已确认。", "Vessel mounting direction confirmed.")
                                "No rotation-vector sample is available on this phone." -> os.t("当前手机没有可用的姿态传感器。", feedback)
                                "Resume the trip before confirming a new attitude segment." -> os.t("请先继续航行，再确认安装方向。", feedback)
                                "Trip attitude capture paused. Heading, GPS and pressure continue." -> os.t("姿态采集已暂停。", feedback)
                                "Trip attitude capture resumed." -> os.t("姿态采集已继续。", feedback)
                                else -> feedback
                            }, 17, c.accent)
                        }
                    }
                    3 -> {
                        val selectedTrend = activeTrend
                        if (selectedTrend == null) {
                            Spacer(Modifier.height(26.dp))
                            Label(os.t("等一份船况", "waiting for conditions"), 32)
                            Label(os.t("收到航速、风或天气读数后，变化会出现在这里。", "Speed, wind and weather trends appear as readings arrive."), 18, c.muted)
                            Label(os.t("可在数据中心查看当前来源。", "Check your sources in Data Center."), 14, c.muted)
                        } else {
                            val key = selectedTrend.key
                            val current = data.readings[key]
                            val last = InstrumentTrendCatalog.lastReading(key, data.readings, history, now)
                            val live = current != null && current === last && current.fresh(now)
                            Row(Modifier.fillMaxWidth().clickable { chooseTrend = true }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Label(metricName(os, key), 27, modifier = Modifier.weight(1f))
                                Label(os.t("切换", "change"), 16, c.accent)
                            }
                            // 此页回看观测；过期方向仅以明确的历史读数呈现，不驱动实时罗盘。
                            Label(os.formatMetric(key, last?.value), 54, if (live) c.accent else c.muted)
                            last?.let {
                                Label((if (live) os.t("最新 · ", "latest · ") else os.t("上次记录 · ", "last recorded · ")) + readingAge(os, it.elapsed, now) + " · " + it.source, 13, c.muted)
                            }
                            val samples = history[key].orEmpty().filter { now - it.elapsed in 0..900_000 && it.value.isFinite() }
                            if (samples.isNotEmpty()) {
                                ReadingTrace(os, samples, key, now, current = current)
                                Label(os.t("最近 15 分钟 · 拖动查看", "last 15 minutes · drag to inspect"), 13, c.muted)
                            } else {
                                Label(os.t("最近 15 分钟还没有趋势样本。", "No trend samples in the last 15 minutes."), 16, c.muted)
                            }
                        }
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
                Label(value.text, if (tile == InstrumentTileId.POSITION) 23 else 48, if (value.observation.displayIsLive()) c.accent else c.muted)
                Label(observationStatus(os, value.observation, now), 18)
                Label(os.t("来源", "source"), 14, c.muted)
                Label(value.observation.sourceIdentity?.displayName ?: sourceName(os, value.observation.source), 21)
                instrumentTrendKey(tile)?.let { key -> ReadingTrace(os, history[key].orEmpty(), key, now, current = data.readings[key]) }
                value.observation.conflict?.let { Label(os.t("多个来源的读数有差异，可在数据中心中检查。", "Sources disagree. Inspect them in Data Center."), 17, c.muted) }
                if (tile == InstrumentTileId.UKC) Label(os.t("龙骨下余量根据水深与船舶吃水计算。", "Under-keel clearance uses measured depth and your boat's draft."), 17, c.muted)
                if (tile !in state.vesselSettings.customLayout) MetroButton(os.t("添加到我的仪表", "add to my instruments"), { saveLayout(state.vesselSettings.customLayout + tile) })
                MetroButton(os.t("完成", "done"), { selected = null }, primary = true)
            }
        }
    }
    if (chooseTrend) Dialog(onDismissRequest = { chooseTrend = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).background(c.bg).border(2.dp, c.fg).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Label(os.t("观察趋势", "watch a trend"), 30)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (availableTrends.isEmpty()) Label(os.t("收到航行或天气读数后，再来选择。", "Choose a trend once navigation or weather readings arrive."), 18, c.muted)
                InstrumentTrendGroup.entries.forEach { group ->
                    val choices = availableTrends.filter { it.group == group }
                    if (choices.isNotEmpty()) {
                        Label(if (group == InstrumentTrendGroup.NAVIGATION) os.t("航行", "navigation") else os.t("天气", "weather"), 20, c.accent, Modifier.padding(top = 14.dp, bottom = 4.dp))
                        choices.forEach { metric ->
                            val last = InstrumentTrendCatalog.lastReading(metric.key, data.readings, history, now)
                            ChoiceRow(metricName(os, metric.key), activeTrend?.key == metric.key,
                                subtitle = last?.let { os.formatMetric(metric.key, it.value) + " · " + readingAge(os, it.elapsed, now) }) {
                                trend = metric.key; chooseTrend = false
                            }
                        }
                    }
                }
            }
            MetroButton(os.t("完成", "done"), { chooseTrend = false })
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
    Row(Modifier.fillMaxWidth().toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(Modifier.size(26.dp).border(2.dp, c.fg), contentAlignment = Alignment.Center) { if (selected) Glyph("check", Modifier.size(18.dp), c.fg) }
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
            Column(Modifier.fillMaxWidth().animateItem(placementSpec = if (isDragging) null else spring(stiffness = 360f)).zIndex(if (isDragging) 1f else 0f)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Label(value.text, if (tile == InstrumentTileId.POSITION) 22 else 44, if (value.observation.displayIsLive()) c.accent else c.muted, Modifier.weight(1f))
                    if (tile != InstrumentTileId.POSITION) Box(Modifier.width(100.dp)) { InstrumentGauge(os, data, tile) }
                }
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
                    Label(value.text, if (tile == InstrumentTileId.POSITION) 18 else 34, if (value.observation.displayIsLive()) c.fg else c.muted)
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
    fun <T> value(o: VesselObservation<T>, format: (T) -> String): InstrumentValue = InstrumentValue(o.value?.takeIf { InstrumentReadingPolicy.displayable(tile, o.freshness, o.quality) }?.let(format) ?: "—", o)
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
        InstrumentTileId.HEEL -> angle(d.heelDegrees)
        InstrumentTileId.PITCH -> angle(d.pitchDegrees)
        InstrumentTileId.ROLL_RATE -> number(d.rollRateDegreesPerSecond, "°/s")
        InstrumentTileId.PITCH_RATE -> number(d.pitchRateDegreesPerSecond, "°/s")
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

internal fun observationStatus(os: OsStore, observation: VesselObservation<*>, now: Long): String {
    if (observation.value == null) return os.t("等待首份读数", "waiting for the first reading")
    val status = if (observation.displayIsLive()) os.t("实时", "live") else os.t("上次读数", "last reading")
    return status + (observation.receivedElapsedRealtime?.let { " · " + readingAge(os, it, now) } ?: "")
}
internal fun readingStatus(os: OsStore, reading: Reading?, now: Long): String = if (reading == null) os.t("等待首份读数", "waiting for the first reading") else (if (reading.fresh(now)) os.t("实时", "live") else os.t("上次读数", "last reading")) + " · " + readingAge(os, reading.elapsed, now)
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

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
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

/** 仪表消费同一份船况；场景、回看选择与布局只是应用内展示状态。 */
@Composable fun InstrumentsScreen(os: OsStore, initialSection: String = "") {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
    val history by os.hub.history.collectAsState()
    val data by os.hub.state.collectAsState()
    val now = rememberMarineClock()
    val pageKeys = listOf("navigation", "sailing", "attitude", "weather", "history", "mine")
    val entryMetric = remember(initialSection) {
        initialSection.removePrefix("metric:").takeIf { initialSection.startsWith("metric:") }
            ?.let { name -> InstrumentTileId.entries.firstOrNull { it.name == name } }
    }
    var selected by rememberSaveable(initialSection) { mutableStateOf(entryMetric?.name) }
    var currentPage by rememberSaveable(initialSection) { mutableIntStateOf(pageKeys.indexOf(initialSection.removePrefix("tab:")).coerceAtLeast(0)) }
    var chooseTiles by rememberSaveable { mutableStateOf(false) }
    var pickerTileNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var trend by rememberSaveable { mutableStateOf("sog") }
    var trendChosen by rememberSaveable { mutableStateOf(false) }
    var chooseTrend by rememberSaveable { mutableStateOf(false) }
    val pageStates = rememberSaveableStateHolder()
    val availableTrends = InstrumentTrendCatalog.available(data.readings, history, now)
    val activeTrend = InstrumentTrendCatalog.selected(trend, availableTrends, data.readings, now, preserveSelection=trendChosen)
    val enabled = LocalInternalAppInputEnabled.current
    val c = LocalMetro.current
    // 已开始回看某个指标后，来源离线或短期缓存到期不能把用户跳到另一个指标。
    LaunchedEffect(activeTrend?.key) { activeTrend?.let { trend = it.key; trendChosen=true } }
    ReportVisibleAppRoute(os, selected?.let { "instruments:metric:$it" } ?: "instruments:tab:${pageKeys[currentPage]}")
    fun closeLayer(): Boolean = when {
        chooseTrend -> { chooseTrend = false; true }
        chooseTiles -> { chooseTiles = false; true }
        selected != null -> { if (entryMetric != null) os.shell.popRoute() else selected = null; true }
        editing -> { editing = false; true }
        else -> false
    }
    AppBackHandler(chooseTrend || chooseTiles || selected != null || editing) { closeLayer() }
    fun saveLayout(layout: List<InstrumentTileId>) { marine.services.preferences.setInstrumentLayout(layout) }
    val select: (InstrumentTileId) -> Unit = { if (enabled) selected = it.name }
    val target = selected ?: "workspace"
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, selected?.let { name -> instrumentName(os, InstrumentTileId.valueOf(name)) } ?: os.title(AppId.INSTRUMENTS),
            hasLocalBack = selected != null, localBackLabel = if (selected != null && entryMetric == null) os.t("返回仪表", "back to instruments") else null)
        AppPageTransition(target, pageKey = { it }, pageDepth = { if (it == "workspace") 0 else 1 }, modifier = Modifier.weight(1f)) { visible ->
            val isActive = enabled && visible == target && !chooseTiles && !chooseTrend
            CompositionLocalProvider(LocalInternalAppInputEnabled provides isActive) {
                pageStates.SaveableStateProvider(visible) {
                    if (visible != "workspace") {
                        InstrumentDetailPage(os, InstrumentTileId.valueOf(visible), state.vesselData,
                            state.vesselSettings.customLayout, history, data.readings, now, isActive, ::saveLayout, select)
                    } else Pivot(listOf(os.t("航行", "navigation"), os.t("帆航", "sailing"), os.t("姿态", "attitude"),
                        os.t("天气", "weather"), os.t("回看", "history"), os.t("我的", "mine")),
                        initialPage = currentPage, onPageSelected = { currentPage = it }) { page ->
                        if (page == 5) InstrumentBoard(os, state.vesselData, state.vesselSettings.customLayout, now, editing,
                            edit = { editing = !editing }, add = {
                                pickerTileNames = marine.services.state.value.vesselSettings.customLayout.map { it.name }
                                chooseTiles = true
                            }, save = ::saveLayout, select = select)
                        else PageBody {
                            if (state.settings.demoMode) Label(os.t("演示 · 模拟读数", "DEMO · simulated readings"), 16, c.accentText)
                            when (page) {
                                0 -> NavigationInstrumentPanel(os, state.vesselData, now, select)
                                1 -> SailingInstrumentPanel(os, state.vesselData, now, select)
                                2 -> {
                                    InstrumentAttitudePanel(os, active = isActive && currentPage == 2, onMetric = select)
                                    var axis by rememberSaveable { mutableStateOf("heel") }
                                    AppSection(os.t("摆动回看", "motion history"))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        listOf("heel" to os.t("横倾", "heel"), "pitch" to os.t("纵倾", "pitch")).forEach { (key, label) ->
                                            ChoiceRow(label, axis == key, modifier = Modifier.weight(1f)) { axis = key }
                                        }
                                    }
                                    ReadingTrace(os, history[axis].orEmpty(), axis, now, data.readings[axis])
                                }
                                3 -> InstrumentWeatherPanel(os, state.vesselData, now, select, active = isActive && currentPage == 3)
                                4 -> {
                                    val selectedTrend = activeTrend
                                    if (selectedTrend == null) {
                                        Spacer(Modifier.height(26.dp))
                                        Label(os.t("等一份船况", "waiting for conditions"), 24)
                                        Label(os.t("收到航速、方向、风或天气读数后，在这里回看真实变化。", "Review speed, direction, wind and weather as real readings arrive."), 15, c.muted)
                                        MetroButton(os.t("检查数据来源", "check data sources"), { os.openLinked("data_center:readings") })
                                    } else {
                                        val key = selectedTrend.key
                                        val current = data.readings[key]
                                        val last = InstrumentTrendCatalog.lastReading(key, data.readings, history, now)
                                        val live = current != null && current === last && current.fresh(now)
                                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { chooseTrend = true }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Label(metricName(os, key), 20, modifier = Modifier.weight(1f))
                                            Label(os.t("切换", "change"), 16, c.accentText)
                                        }
                                        Label(os.formatMetric(key, last?.value), 42, if (live) c.accent else c.muted)
                                        last?.let { Label((if (live) os.t("最新 · ", "latest · ") else os.t("上次记录 · ", "last recorded · ")) + readingAge(os, it.elapsed, now) + " · " + it.source, 13, c.muted) }
                                        ReadingTrace(os, history[key].orEmpty(), key, now, current)
                                        if (key == "pressure") MetroButton(os.t("查看长时气压记录", "longer pressure history"), { selected = InstrumentTileId.PRESSURE.name })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (chooseTrend) AppDialog(onDismissRequest = { chooseTrend = false }) {
        AppDialogSurface() {
            AppDialogTitle(os.t("选择回看内容", "choose a history"))
            Column {
                InstrumentTrendGroup.entries.forEach { group ->
                    val choices = availableTrends.filter { it.group == group }
                    if (choices.isNotEmpty()) {
                        Label(if (group == InstrumentTrendGroup.NAVIGATION) os.t("航行", "navigation") else os.t("天气", "weather"), 19, c.accentText, Modifier.padding(top = 14.dp))
                        choices.forEach { metric ->
                            val last = InstrumentTrendCatalog.lastReading(metric.key, data.readings, history, now)
                            ChoiceRow(metricName(os, metric.key), activeTrend?.key == metric.key,
                                subtitle = last?.let { os.formatMetric(metric.key, it.value) + " · " + readingAge(os, it.elapsed, now) }) {
                                trend = metric.key; trendChosen=true; chooseTrend = false
                            }
                        }
                    }
                }
            }
            MetroButton(os.t("完成", "done"), { chooseTrend = false })
        }
    }
    if (chooseTiles) AppDialog(onDismissRequest = { chooseTiles = false }) {
        AppDialogSurface() {
            AppDialogTitle(os.t("添加仪表", "add instruments"))
            Column {
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

/** 详情是仪表自己的子页；修复来源后由 Shell 恢复同一指标、图形窗口和相机。 */
@Composable private fun InstrumentDetailPage(os: OsStore, tile: InstrumentTileId, data: VesselDataSnapshot,
    layout: List<InstrumentTileId>, history: Map<String, List<Reading>>, readings: Map<String, Reading>, now: Long,
    active: Boolean, save: (List<InstrumentTileId>) -> Unit, select: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    val value = instrumentValue(os, data, tile)
    PageBody {
        when (tile) {
            InstrumentTileId.HEEL, InstrumentTileId.PITCH -> InstrumentAttitudePanel(os, active, select)
            InstrumentTileId.DEPTH, InstrumentTileId.UKC -> DepthInstrumentSection(os, data, now, select)
            InstrumentTileId.PRESSURE -> InstrumentPressureHistory(os, data.pressureHpa, active)
            else -> {
                Label(value.text, if (tile == InstrumentTileId.POSITION) 24 else 42, if (value.observation.displayIsLive()) c.accent else c.muted)
                when (tile) {
                    InstrumentTileId.HEADING, InstrumentTileId.COG -> MarineCompass(os, data)
                    InstrumentTileId.TRUE_WIND_SPEED, InstrumentTileId.TRUE_WIND_ANGLE, InstrumentTileId.TRUE_WIND_DIRECTION,
                    InstrumentTileId.APPARENT_WIND_SPEED, InstrumentTileId.APPARENT_WIND_ANGLE -> WindRose(os, data)
                    else -> InstrumentGauge(os, data, tile)
                }
            }
        }
        if (tile == InstrumentTileId.BOAT_SPEED) Label(os.t("这是船相对水的速度，来自船上的计程仪，适合看帆航表现。平时看对地航速即可；二者相减不能直接当作流速。", "This is speed relative to the water, measured by a boat log and useful for sailing performance. Use speed over ground for everyday navigation; subtracting them does not directly measure current."), 15, c.muted)
        if (tile == InstrumentTileId.PRESSURE) Label(os.t("当前仪表来源", "current instrument source"), 19)
        Label(observationStatus(os, value.observation, now), 15, c.muted)
        Label(os.t("来源 · ", "source · ") + (value.observation.sourceIdentity?.displayName ?: sourceName(os, value.observation.source)), 15, c.muted)
        if (tile != InstrumentTileId.PRESSURE) instrumentTrendKey(tile)?.let { ReadingTrace(os, history[it].orEmpty(), it, now, readings[it]) }
        value.observation.conflict?.takeIf { it.active }?.let { Label(os.t("多个来源读数不一致，请检查设备与安装方向。", "Sources disagree; inspect the instruments and alignment."), 15, c.muted) }
        if (tile in setOf(InstrumentTileId.SOG, InstrumentTileId.HEADING, InstrumentTileId.DEPTH,
                InstrumentTileId.TRUE_WIND_SPEED, InstrumentTileId.APPARENT_WIND_SPEED, InstrumentTileId.PRESSURE))
            PinTileAction(os, tileReadingBinding(tile.name))
        if (tile !in layout) MetroButton(os.t("添加到我的仪表", "add to my instruments"), { save(layout + tile) })
        instrumentSourceMetric(tile)?.let { metric ->
            MetroButton(os.t("检查此项来源", "check this source"), { os.openLinked("data_center:source/${metric.name}") })
        }
    }
}

internal fun instrumentSourceMetric(tile: InstrumentTileId): VesselMetricId? = when (tile) {
    InstrumentTileId.POSITION -> VesselMetricId.POSITION
    InstrumentTileId.HEADING -> VesselMetricId.HEADING_TRUE
    InstrumentTileId.BOAT_SPEED -> VesselMetricId.SPEED_THROUGH_WATER
    InstrumentTileId.RATE_OF_TURN -> VesselMetricId.RATE_OF_TURN
    InstrumentTileId.CROSS_TRACK_ERROR -> VesselMetricId.XTE
    InstrumentTileId.VMG -> VesselMetricId.VMG_WIND
    InstrumentTileId.VMC -> VesselMetricId.VMC_WAYPOINT
    InstrumentTileId.PRESSURE_TREND_1H, InstrumentTileId.PRESSURE_TREND_3H, InstrumentTileId.PRESSURE_TREND_6H -> VesselMetricId.PRESSURE
    InstrumentTileId.MOTION_SCORE, InstrumentTileId.IMPACT_COUNT -> VesselMetricId.MOTION_SCORE
    else -> VesselMetricId.entries.firstOrNull { it.name == tile.name }
}

@Composable private fun InstrumentChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    AppCheckRow(title, selected, onClick = onClick)
}

/** 中文：编辑时在本地重排，拖动结束一次性保存；稳定的枚举 ID 保证读数不会跟随位置串位。 */
@Composable private fun InstrumentBoard(os: OsStore, data: VesselDataSnapshot, saved: List<InstrumentTileId>, now: Long, editing: Boolean, edit: () -> Unit, add: () -> Unit, save: (List<InstrumentTileId>) -> Unit, select: (InstrumentTileId) -> Unit) {
    val c = LocalMetro.current
    var order by remember(saved) { mutableStateOf(saved.distinct()) }
    var dragging by remember { mutableStateOf<InstrumentTileId?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentOrder by rememberUpdatedState(order)
    val currentSave by rememberUpdatedState(save)
    LazyColumn(Modifier.fillMaxSize().padding(start = LocalShellHorizontalInsets.current.pageStart, end = LocalShellHorizontalInsets.current.pageEnd), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(os.t("添加", "add"), add, Modifier.weight(1f), primary = true)
                if (order.isNotEmpty()) MetroButton(if (editing) os.t("完成", "done") else os.t("排列", "arrange"), edit, Modifier.weight(1f))
            }
            if (editing && order.isNotEmpty()) Label(os.t("长按仪表拖动排序，也可使用上移和下移。", "hold and drag to reorder, or use move up / down"), 15, c.muted, Modifier.padding(top = 12.dp))
            if (order.isEmpty()) {
                Spacer(Modifier.height(35.dp))
                Label(os.t("你的驾驶台", "your helm"), 24)
                Label(os.t("把常看的仪表放在一起。选择、移除与顺序都会保存。", "Keep the instruments you use together. Your selection and order are saved."), 15, c.muted, Modifier.padding(top = 14.dp))
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
                    Label(instrumentName(os, tile), 15, modifier = Modifier.weight(1f))
                    if (editing) Glyph("menu", Modifier.size(24.dp), c.muted)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Label(value.text, if (tile == InstrumentTileId.POSITION) 22 else 44, if (value.observation.displayIsLive()) c.accent else c.muted, Modifier.weight(1f))
                    if (tile != InstrumentTileId.POSITION) Box(Modifier.width(100.dp)) { InstrumentGauge(os, data, tile) }
                }
                Label(observationStatus(os, value.observation, now), 13, c.muted)
                if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    val index = order.indexOf(tile)
                    if (index > 0) Label(os.t("上移", "up"), 17, c.accentText, Modifier.clickable { val changed = order.toMutableList().apply { removeAt(index); add(index - 1, tile) }; order = changed; save(changed) }.padding(vertical = 8.dp))
                    if (index < order.lastIndex) Label(os.t("下移", "down"), 17, c.accentText, Modifier.clickable { val changed = order.toMutableList().apply { removeAt(index); add(index + 1, tile) }; order = changed; save(changed) }.padding(vertical = 8.dp))
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
        InstrumentTileId.PRESSURE_TREND_1H -> value(d.derived.pressureTrend1hHpa) { os.formatMetric("pressure_1h", it) }
        InstrumentTileId.PRESSURE_TREND_3H -> value(d.derived.pressureTrend3hHpa) { os.formatMetric("pressure_3h", it) }
        InstrumentTileId.PRESSURE_TREND_6H -> value(d.derived.pressureTrend6hHpa) { os.formatMetric("pressure_6h", it) }
        InstrumentTileId.RATE_OF_TURN -> number(d.rateOfTurnDegreesPerMinute, "°/min")
        InstrumentTileId.RUDDER_ANGLE -> angle(d.rudderAngleDegrees)
        InstrumentTileId.WATER_TEMPERATURE -> value(d.waterTemperatureCelsius, os::formatTemperature)
        InstrumentTileId.AIR_TEMPERATURE -> value(d.airTemperatureCelsius, os::formatTemperature)
        InstrumentTileId.CURRENT_SET -> bearing(d.currentSetTrueDegrees)
        InstrumentTileId.CURRENT_DRIFT -> speed(d.currentDriftKnots)
        InstrumentTileId.CROSS_TRACK_ERROR -> value(d.crossTrackErrorNauticalMiles) { os.formatMetric("xte", it) }
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
internal fun instrumentName(os: OsStore, tile: InstrumentTileId): String = when (tile) {
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

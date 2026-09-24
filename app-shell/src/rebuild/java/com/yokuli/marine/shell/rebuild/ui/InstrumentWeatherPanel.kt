package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.anchorwatch.api.MarinePressurePoint
import com.yokuli.anchorwatch.api.MarinePressureSource
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 天气页只表达实际风与天气观测，不由气压升降推断一份不存在的预报。 */
@Composable internal fun InstrumentWeatherPanel(os: OsStore, data: VesselDataSnapshot, now: Long,
    onMetric: (InstrumentTileId) -> Unit, active: Boolean = true) {
    val c = LocalMetro.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InstrumentPressureHistory(os, data.pressureHpa, active)
        AppSection(os.t("温度与风", "temperature & wind"))
        listOf(InstrumentTileId.AIR_TEMPERATURE to data.airTemperatureCelsius,
            InstrumentTileId.WATER_TEMPERATURE to data.waterTemperatureCelsius,
            InstrumentTileId.TRUE_WIND_SPEED to data.trueWind.speedKnots).forEach { (tile, observation) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onMetric(tile) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(instrumentName(os, tile), 16)
                    Label(observationStatus(os, observation, now), 12, c.muted)
                }
                Label(instrumentTrendKey(tile)?.let { os.formatMetric(it, observation.value?.takeIf { value -> value.isFinite() && observation.quality != VesselDataQuality.UNKNOWN }) } ?: "—", 28,
                    if (observation.displayIsLive()) c.fg else c.muted)
            }
        }
        Label(os.t("点选读数，查看它自己的历史与来源。", "Open a reading for its own history and source."), 14, c.muted)
    }
}

private data class PressureLoad<T>(val data: T? = null, val failed: Boolean = false)

/**
 * 复用运行时已持久化的每来源、每分钟气压样本。UTC 历史保持 UTC，不转换成假实时 Reading。
 * 选择历史来源仅影响查询；不会改变系统正在采用的气压、连接或传感器开关。
 */
@Composable internal fun InstrumentPressureHistory(os: OsStore, live: VesselObservation<Double>, active: Boolean = true) {
    val content = os.content
    val c = LocalMetro.current
    val clock = rememberMarineClock()
    val utcMinute = remember(clock) { System.currentTimeMillis() / 60_000L }
    val end = utcMinute * 60_000L + 59_999L
    var hours by rememberSaveable { mutableIntStateOf(6) }
    var pinnedSource by rememberSaveable { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var selectedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val enabled = active && LocalInternalAppInputEnabled.current
    val liveKey = live.sourceIdentity?.persistentKey
    val sources by produceState(PressureLoad<List<MarinePressureSource>>(), content, enabled, retry, utcMinute) {
        if (!enabled) return@produceState
        value = PressureLoad()
        try {
            content.observePressureSources(end - 30L * 24 * 3_600_000L, end).collect { value = PressureLoad(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = PressureLoad(failed = true) }
    }
    val known = sources.data.orEmpty().filter { it.lastObservedUtcMillis <= end }
    // 默认显示当前来源；无当前来源时允许回看最近来源，但始终标为历史查询。
    val sourceKey = pinnedSource ?: liveKey ?: known.firstOrNull()?.key
    val sourceName = known.firstOrNull { it.key == sourceKey }?.name
        ?: live.sourceIdentity?.takeIf { it.persistentKey == sourceKey }?.displayName
    val start = end - hours * 3_600_000L
    val points by produceState(PressureLoad<List<MarinePressurePoint>>(), content, sourceKey, start, enabled, retry) {
        value = PressureLoad()
        if (!enabled || sourceKey == null) return@produceState
        try {
            content.observePressureHistory(sourceKey, start, end).collect { value = PressureLoad(it.filter { sample -> sample.pressureHpa.isFinite() && sample.observedUtcMillis in start..end }) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = PressureLoad(failed = true) }
    }
    LaunchedEffect(sourceKey) { selectedAt = null }
    val samples = points.data.orEmpty()
    val selected = selectedAt?.let { at -> samples.minByOrNull { abs(it.observedUtcMillis - at) } }
    val display = selected ?: samples.lastOrNull()
    val nowUtc = System.currentTimeMillis()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppSection(os.t("气压记录", "pressure history"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(os.formatPressure(display?.pressureHpa), 38, c.accent, Modifier.weight(1f))
            Label(os.t("历史观测", "recorded observations"), 13, c.muted, Modifier.padding(bottom = 7.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1, 6, 24).forEach { value ->
                MetroButton(os.t("${value}小时", "$value h"), { hours = value; selectedAt = null }, primary = hours == value)
            }
        }
        MenuRow(os.t("历史来源", "history source"), sourceName ?: os.t("尚无气压来源", "no pressure source yet")) { choosing = true }
        if (pinnedSource != null && pinnedSource != liveKey) Label(os.t("正在回看此来源；实时来源没有改变。", "Reviewing this source; your live source is unchanged."), 13, c.muted)
        when {
            sources.failed || points.failed -> {
                Label(os.t("气压历史暂时未能读取，实时仪表继续工作。", "Could not read pressure history. Live instruments continue."), 15, c.muted)
                MetroButton(os.t("重试", "retry"), { retry++ })
            }
            sources.data == null || (sourceKey != null && points.data == null) -> Label(os.t("正在读取气压记录…", "loading pressure records…"), 15, c.muted)
            samples.isEmpty() -> Label(os.t("这个时间范围没有该来源的记录。收到气压后按分钟保留，重启也可回看。", "No records from this source in this period. Received pressure is retained by minute and survives restart."), 15, c.muted)
            else -> {
                PressureRecordGraph(os, samples, start, end, selected, onSelect = { selectedAt = it })
                display?.let { point ->
                    val time = remember(point.observedUtcMillis, os.chinese) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, if (os.chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(point.observedUtcMillis)) }
                    Label(time + " · " + os.t("记录于 ", "recorded ") + readingAge(os, point.observedUtcMillis, nowUtc), 13, c.muted)
                }
                if (samples.size > 1) {
                    val minimum = samples.minOf { it.pressureHpa }; val maximum = samples.maxOf { it.pressureHpa }
                    Label(os.t("低 ", "low ") + os.formatPressure(minimum) + " · " + os.t("高 ", "high ") + os.formatPressure(maximum), 14, c.muted)
                    Label(os.t("首末记录差 ", "first-to-last change ") + os.formatPressureChange(samples.last().pressureHpa - samples.first().pressureHpa), 16)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val index = display?.let { samples.indexOf(it) } ?: samples.lastIndex
                    MetroButton(os.t("上一条", "previous"), { selectedAt = samples[(index - 1).coerceAtLeast(0)].observedUtcMillis }, Modifier.weight(1f), enabled = index > 0)
                    MetroButton(os.t("下一条", "next"), { selectedAt = samples[(index + 1).coerceAtMost(samples.lastIndex)].observedUtcMillis }, Modifier.weight(1f), enabled = index < samples.lastIndex)
                    if (selectedAt != null) MetroButton(os.t("最新", "latest"), { selectedAt = null }, Modifier.weight(1f))
                }
                Label(os.t("触摸查看时刻；空白表示没有记录。阴影是每个时段的实际读数范围，不是天气预测。", "Touch to inspect a time. Gaps have no records; shading shows observed ranges, not a weather forecast."), 13, c.muted)
            }
        }
    }
    if (choosing) Dialog(onDismissRequest = { choosing = false }) {
        AppBackHandler { choosing = false }
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).background(c.bg).border(1.dp, c.muted).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSection(os.t("回看哪个来源", "choose history source"))
            if (liveKey != null) ChoiceRow(os.t("跟随实时来源", "follow the live source"), pinnedSource == null, live.sourceIdentity?.displayName) { pinnedSource = null; selectedAt = null; choosing = false }
            known.forEach { source -> ChoiceRow(source.name, pinnedSource == source.key) { pinnedSource = source.key; selectedAt = null; choosing = false } }
            if (known.isEmpty()) Label(os.t("还没有保存的气压来源。", "No pressure sources recorded yet."), 16, c.muted)
            MetroButton(os.t("关闭", "close"), { choosing = false })
        }
    }
}

/** 24 小时最多1441个原始点，绘制至多48个时间范围，不逐帧重算整条历史。 */
@Composable private fun PressureRecordGraph(os: OsStore, samples: List<MarinePressurePoint>, start: Long, end: Long,
    selected: MarinePressurePoint?, onSelect: (Long) -> Unit) {
    val c = LocalMetro.current
    val bounds = remember(samples, os.unitPreferences) {
        val lo = samples.minOf { os.pressureValue(it.pressureHpa) }; val hi = samples.maxOf { os.pressureValue(it.pressureHpa) }
        val margin = ((hi - lo) * .12).coerceAtLeast(os.pressureValue(.2))
        (lo - margin) to (hi + margin)
    }
    val bins = remember(samples, start, end) { samples.groupBy { (((it.observedUtcMillis - start).toDouble() / (end - start) * 48).toInt()).coerceIn(0, 47) } }
    var width by remember { mutableIntStateOf(1) }
    val select by rememberUpdatedState<(Float) -> Unit>({ x -> onSelect(start + ((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * (end - start)).toLong()) })
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(gaugeScaleNumber(bounds.second, bounds.second - bounds.first) + " " + os.pressureUnitLabel, 12, c.muted)
            Label(os.t("${samples.size} 条记录", "${samples.size} records"), 12, c.muted)
        }
        Canvas(Modifier.fillMaxWidth().height(200.dp).clipToBounds().onSizeChanged { width = it.width }
            .semantics { contentDescription = os.t("按时间显示气压实测范围，可用下方上一条和下一条按钮逐项读取。", "Pressure observations over time. Use previous and next below to inspect every record.") }
            .pointerInput(start, end) { detectTapGestures { select(it.x) } }
            .pointerInput(start, end) { detectHorizontalDragGestures(onDragStart = { select(it.x) }) { change, _ -> change.consume(); select(change.position.x) } }) {
            fun x(time: Long) = ((time - start).toDouble() / (end - start) * size.width).toFloat()
            fun y(hpa: Double) = (size.height - (os.pressureValue(hpa) - bounds.first) / (bounds.second - bounds.first) * size.height).toFloat()
            repeat(4) { i -> val yy = size.height * i / 3; drawLine(c.muted.copy(alpha = .18f), Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx()) }
            bins.forEach { (index, bin) ->
                val low = bin.minOf { it.pressureHpa }; val high = bin.maxOf { it.pressureHpa }
                drawLine(c.accent.copy(alpha = .22f), Offset((index + .5f) / 48 * size.width, y(low)), Offset((index + .5f) / 48 * size.width, y(high)), (size.width / 48 * .8f).coerceAtLeast(1f))
            }
            val path = Path(); var previous: MarinePressurePoint? = null
            samples.forEachIndexed { index, value ->
                val old = previous
                val next = samples.getOrNull(index + 1)
                if ((old == null || value.observedUtcMillis - old.observedUtcMillis > 180_000L) &&
                    (next == null || next.observedUtcMillis - value.observedUtcMillis > 180_000L)) {
                    drawCircle(c.accent, 3.dp.toPx(), Offset(x(value.observedUtcMillis), y(value.pressureHpa)))
                }
                if (old == null || value.observedUtcMillis - old.observedUtcMillis > 180_000L) path.moveTo(x(value.observedUtcMillis), y(value.pressureHpa)) else path.lineTo(x(value.observedUtcMillis), y(value.pressureHpa))
                previous = value
            }
            drawPath(path, c.accent, style = Stroke(2.dp.toPx()))
            selected?.let { point -> val xx = x(point.observedUtcMillis); drawLine(c.fg.copy(alpha = .5f), Offset(xx, 0f), Offset(xx, size.height), 1.dp.toPx()); drawCircle(c.fg, 4.dp.toPx(), Offset(xx, y(point.pressureHpa))) }
        }
        Label(gaugeScaleNumber(bounds.first, bounds.second - bounds.first) + " " + os.pressureUnitLabel, 12, c.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(os.t("${(end - start) / 3_600_000} 小时前", "${(end - start) / 3_600_000} hours ago"), 12, c.muted)
            Label(os.t("现在", "now"), 12, c.muted)
        }
    }
}

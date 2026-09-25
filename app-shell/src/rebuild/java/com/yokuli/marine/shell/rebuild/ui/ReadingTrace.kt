package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.data.Reading
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * 真实的 15 分钟进程内读数历史。方向、偏差、测深、速度和累计量使用各自的表达，
 * 不采集第二份数据、不补点。本次回看固定样本和坐标；明确更新或换窗口才重新取快照。
 */
@Composable internal fun ReadingTrace(os: OsStore, values: List<Reading>, metric: String, now: Long, current: Reading? = null) {
    val c = LocalMetro.current
    var minutes by rememberSaveable(metric) { mutableIntStateOf(5) }
    var revision by rememberSaveable(metric) { mutableIntStateOf(0) }
    var selectedId by rememberSaveable(metric) { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val captureClock=remember(metric,minutes,revision) {android.os.SystemClock.elapsedRealtime() to System.currentTimeMillis()}
    val capturedAt=captureClock.first
    val capturedUtc=captureClock.second
    // 仅缓存本次查看的不可变列表，采集和长期保留仍属于 DataHub / 日志服务。
    val captured = remember(metric, minutes, revision) { values.filter { it.elapsed in maxOf(0L,capturedAt-minutes*60_000L)..capturedAt }.toList() }
    val frame = remember(captured, metric, minutes, capturedAt, os.unitPreferences) {
        buildInstrumentHistory(os, metric, captured, capturedAt, minutes)
    }
    val newCount=remember(values,capturedAt) {values.count {it.value.isFinite()&&it.quality!=VesselDataQuality.UNKNOWN&&it.elapsed>capturedAt}}
    val clockOffset=remember(captured,capturedAt,capturedUtc) {
        captured.lastOrNull {it.observedUtcMillis!=null}?.let {it.observedUtcMillis!!-it.elapsed} ?: (capturedUtc-capturedAt)
    }
    fun clockLabel(elapsed:Long) = historyTimeLabel(clockOffset+elapsed, os.chinese)
    val samples = frame.points
    val selected = selectedId?.let { id -> samples.firstOrNull { historySampleId(it.reading)==id } }
    val presented = selected ?: samples.lastOrNull()
    val selectedIndex = presented?.let { samples.indexOf(it) } ?: -1
    val summary = remember(frame, os.unitPreferences, os.chinese) { historySummary(os, metric, frame) }
    val chartTitle = historyTitle(os, metric, frame.kind)
    val step: (Int) -> Boolean = { direction ->
        if (samples.isEmpty()) false else {
            selectedId = historySampleId(samples[(selectedIndex + direction).coerceIn(samples.indices)].reading)
            true
        }
    }
    val stepCurrent by rememberUpdatedState(step)
    val pick: (Offset) -> Unit = { point ->
        val selectedPoint = if (frame.kind == InstrumentHistoryKind.DIRECTION) {
            val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            val angle = normalizedHistoryAngle(Math.toDegrees(atan2((point.x-center.x).toDouble(), (center.y-point.y).toDouble())))
            samples.minWithOrNull(compareBy<HistoryPoint> { abs(signedHistoryAngle(it.value-angle)) }.thenByDescending { it.reading.elapsed })
        } else {
            val fraction = (point.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            samples.minByOrNull { abs(it.time-fraction) }
        }
        selectedId = selectedPoint?.reading?.let(::historySampleId)
    }
    val pickCurrent by rememberUpdatedState(pick)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Label(chartTitle, 19, c.fg, Modifier.weight(1f))
            Row(Modifier.selectableGroup(), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(1, 5, 15).forEach { span ->
                    Column(Modifier.selectable(selected=minutes==span, role=Role.RadioButton) { minutes=span; selectedId=null }
                        .padding(horizontal=9.dp, vertical=9.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                        Label(os.t("$span 分", "$span min"), 13, if(minutes==span)c.fg else c.muted)
                        Box(Modifier.padding(top=5.dp).width(16.dp).height(2.dp).background(if(minutes==span)c.accent else c.bg))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Label(os.t("固定时段 · ","fixed period · ")+clockLabel(frame.end),12,c.muted,Modifier.weight(1f))
            Label(if(newCount>0)os.t("$newCount 份新读数 · 更新","$newCount new · update")else os.t("更新至现在","update to now"),13,c.accentText,
                Modifier.clickable(role=Role.Button) {revision++;selectedId=null}.padding(horizontal=8.dp,vertical=12.dp))
        }
        if (samples.isEmpty()) {
            Label(os.t("这段时间还没有实测读数", "No actual readings in this window yet"), 20, c.muted, Modifier.padding(vertical=28.dp))
            Label(os.t("收到数据后点“更新至现在”查看；不会用零或模拟曲线填满画面。", "Choose update when readings arrive. Missing samples are left empty."), 13, c.muted)
        } else {
            Label(summary, 16, c.fg)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(frame.kind!=InstrumentHistoryKind.DIRECTION) HistoryAxis(frame)
                Box(Modifier.weight(1f).height(190.dp)) {
                    Canvas(Modifier.fillMaxSize().clipToBounds().onSizeChanged { canvasSize=it }
                        .semantics {
                            contentDescription = chartTitle + ". " + summary + ". " +
                                if(frame.kind==InstrumentHistoryKind.DIRECTION)os.t("点按方向，或长按后拖动查看真实样本", "Tap a direction, or hold then drag to inspect actual samples")
                                else os.t("点按或横向拖动查看真实样本", "Tap or drag horizontally to inspect actual samples")
                            customActions = listOf(
                                CustomAccessibilityAction(os.t("前一份样本", "Previous sample")) { stepCurrent(-1) },
                                CustomAccessibilityAction(os.t("后一份样本", "Next sample")) { stepCurrent(1) },
                                CustomAccessibilityAction(os.t("回到本段末次", "Last reading in this period")) { selectedId=null; true },
                            )
                        }
                        .pointerInput(metric) { detectTapGestures { pickCurrent(it) } }
                        .pointerInput(metric, frame.kind) {
                            // 普通纵向滑动交给 PageBody；只有明确的图表手势才占用位置变化。
                            if(frame.kind==InstrumentHistoryKind.DIRECTION) {
                                detectDragGesturesAfterLongPress(onDragStart={pickCurrent(it)}) { change, _ ->
                                    change.consume(); pickCurrent(change.position)
                                }
                            } else {
                                detectHorizontalDragGestures(onDragStart={pickCurrent(it)}) { change, _ ->
                                    change.consume(); pickCurrent(change.position)
                                }
                            }
                        }) {
                        drawInstrumentHistory(frame, c, selected, current?.fresh(now)==true && current.elapsed==samples.last().reading.elapsed && current.sourceKey==samples.last().reading.sourceKey)
                    }
                    if(frame.kind==InstrumentHistoryKind.DIRECTION) {
                        Label(os.t("北", "N"), 12, c.muted, Modifier.align(Alignment.TopCenter))
                        Label(os.t("东", "E"), 12, c.muted, Modifier.align(Alignment.CenterEnd))
                        Label(os.t("南", "S"), 12, c.muted, Modifier.align(Alignment.BottomCenter))
                        Label(os.t("西", "W"), 12, c.muted, Modifier.align(Alignment.CenterStart))
                    }
                }
            }
            if(frame.kind==InstrumentHistoryKind.DIRECTION) {
                Label(os.t("点按或长按后拖动选方向。扇区长度为样本数量；细线为最近方向，虚线为本段平均方向。", "Tap or hold then drag to select a direction. Sector length shows sample count; solid pointer is latest, dashed is the segment mean."), 12, c.muted)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Label(clockLabel(frame.start), 12, c.muted)
                    Label(os.displayMetricUnit(metric), 12, c.muted)
                    Label(clockLabel(frame.end), 12, c.muted)
                }
                Label(historyLegend(os, metric, frame.kind), 12, c.muted)
            }
            if(frame.kind==InstrumentHistoryKind.DEVIATION) DeviationShare(os, frame)
            if(frame.breaks.isNotEmpty()) Label(os.t("${frame.breaks.size} 处断点：换源或读数间隔过长，不跨段连接。", "${frame.breaks.size} breaks: source changes or missing updates. Segments are kept separate."), 12, c.muted)
            if(frame.kind==InstrumentHistoryKind.COUNTER && frame.incrementTotal==null) Label(os.t("尚无连续同源的两次读数，暂不能计算增量。", "There are no two continuous same-source readings yet; no increment can be calculated."), 12, c.muted)
            if(frame.kind==InstrumentHistoryKind.COUNTER && frame.rejectedIncrements>0) Label(os.t("已排除 ${frame.rejectedIncrements} 处换源、缺口或计数回退。", "Excluded ${frame.rejectedIncrements} source changes, gaps or counter resets."), 12, c.muted)
            presented?.let { point ->
                val reading = point.reading
                val value = if(metric=="awa" || metric=="twa") signedHistoryAngle(reading.value) else reading.value
                Column(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=12.dp, vertical=10.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label((if(selected!=null)os.t("选中样本", "selected sample")else os.t("本段末次", "last in period"))+" · "+
                                (reading.observedUtcMillis?.let {historyTimeLabel(it,os.chinese)} ?: clockLabel(reading.elapsed)), 12, c.muted)
                            Label(os.formatMetric(metric, value), 25, if(selected!=null)c.fg else c.accent)
                        }
                        Label("‹", 27, if(selectedIndex>0)c.fg else c.muted, Modifier.clickable(enabled=selectedIndex>0) { stepCurrent(-1) }
                            .semantics { contentDescription=os.t("前一份样本", "Previous sample") }.padding(horizontal=13.dp, vertical=8.dp))
                        Label("›", 27, if(selectedIndex<samples.lastIndex)c.fg else c.muted, Modifier.clickable(enabled=selectedIndex<samples.lastIndex) { stepCurrent(1) }
                            .semantics { contentDescription=os.t("后一份样本", "Next sample") }.padding(horizontal=13.dp, vertical=8.dp))
                    }
                    val quality = when(reading.quality) {
                        VesselDataQuality.GOOD -> os.t("质量正常", "good quality")
                        VesselDataQuality.DEGRADED -> os.t("质量降低", "degraded quality")
                        VesselDataQuality.UNKNOWN -> os.t("质量未知", "unknown quality")
                    }
                    Label(reading.source+" · "+quality+" · "+readingAge(os,reading.elapsed,now), 12, c.muted)
                    if(selected!=null) Label(os.t("回到本段末次", "last in this period"), 13, c.accent,
                        Modifier.clickable { selectedId=null }.padding(vertical=8.dp))
                }
            }
            val sources = frame.sourceCount
            val degraded = frame.degradedCount
            Label(os.t("实测历史 · ${samples.size} 份 · $sources 个来源", "observed history · ${samples.size} samples · $sources sources")+
                if(degraded>0)os.t(" · $degraded 份质量降低", " · $degraded degraded")else "", 12, c.muted)
        }
    }
}

@Composable private fun HistoryAxis(frame: InstrumentHistoryFrame) {
    val c=LocalMetro.current
    Column(Modifier.width(52.dp).height(190.dp), verticalArrangement=Arrangement.SpaceBetween) {
        val descending=frame.kind!=InstrumentHistoryKind.DEPTH
        val steps=if(frame.kind==InstrumentHistoryKind.COUNT&&frame.upper<=1.0)1 else 3
        (0..steps).forEach { step ->
            val fraction=step.toDouble()/steps
            val value=if(descending)frame.upper-(frame.upper-frame.lower)*fraction else frame.lower+(frame.upper-frame.lower)*fraction
            val span=frame.upper-frame.lower
            val digits=when {frame.kind==InstrumentHistoryKind.COUNT->0;span<.001 -> 6;span<.01 -> 5;span<.1 -> 4;span<1 -> 3;span<10 -> 1;else -> 0}
            Label(String.format(Locale.US,"%.${digits}f",if(value==0.0)0.0 else value), 11, c.muted)
        }
    }
}

@Composable private fun DeviationShare(os: OsStore, frame: InstrumentHistoryFrame) {
    val c=LocalMetro.current
    // 表达样本的左右分布，不把不同采样频率伪装成持续时长。
    val negative=frame.points.count { it.value < 0 }.toFloat()/frame.points.size.coerceAtLeast(1)
    val positive=frame.points.count { it.value > 0 }.toFloat()/frame.points.size.coerceAtLeast(1)
    val neutral=(1f-negative-positive).coerceAtLeast(0f)
    Row(Modifier.fillMaxWidth().height(6.dp)) {
        if(negative>0)Box(Modifier.weight(negative).fillMaxHeight().background(c.muted))
        if(neutral>0)Box(Modifier.weight(neutral).fillMaxHeight().background(c.panel))
        if(positive>0)Box(Modifier.weight(positive).fillMaxHeight().background(c.accent))
    }
    Label(os.t("样本占比  − ${(negative*100).roundToInt()}%  ·  零位 ${(neutral*100).roundToInt()}%  ·  + ${(positive*100).roundToInt()}%",
        "sample share  − ${(negative*100).roundToInt()}%  ·  zero ${(neutral*100).roundToInt()}%  ·  + ${(positive*100).roundToInt()}%"), 12, c.muted)
}

private fun historyTitle(os: OsStore, metric: String, kind: InstrumentHistoryKind): String=when(kind) {
    InstrumentHistoryKind.DIRECTION -> os.t("方位分布", "direction rose")
    InstrumentHistoryKind.BEARING -> os.t("方位随时间", "bearing over time")
    InstrumentHistoryKind.RANGE -> if(metric in setOf("sog","bsp","stw","tws","aws","current_drift"))os.t("速度与范围", "speed range")else os.t("区间变化", "range over time")
    InstrumentHistoryKind.DEPTH -> if(metric=="ukc")os.t("龙骨下余量", "keel clearance")else os.t("测深剖面", "depth profile")
    InstrumentHistoryKind.DEVIATION -> if(metric.startsWith("pressure_"))os.t("气压升降","pressure rise & fall")else os.t("两侧偏差", "signed balance")
    InstrumentHistoryKind.WEATHER -> os.t("天气变化", "weather change")
    InstrumentHistoryKind.COUNT -> os.t("近期冲击候选", "recent impact candidates")
    InstrumentHistoryKind.COUNTER -> os.t("分段增量", "recorded increments")
}

private fun historyLegend(os: OsStore, key: String, kind: InstrumentHistoryKind): String=when(kind) {
    InstrumentHistoryKind.BEARING -> os.t("点为真实方位；正北同时位于 0° 与 360°。跨北断开，避免画成反向大转弯。", "Dots are observed bearings. North is both 0° and 360°; crossings break instead of implying a full reverse turn.")
    InstrumentHistoryKind.RANGE -> os.t("细柱：实测低–高；横划：时段平均。空白处没有样本。", "Thin bars: observed low–high; ticks: period mean. Empty spans have no samples.")
    InstrumentHistoryKind.DEPTH -> if(key=="ukc")os.t("向下为龙骨下余量；细带为实测范围，非海床轮廓。", "Downwards is clearance below keel; bands are observed ranges, not the seabed.")else os.t("向下为水深；细带为实测范围，非海床轮廓。", "Depth increases downwards; bands are observed ranges, not the seabed.")
    InstrumentHistoryKind.DEVIATION -> os.t("中线是零位；上方为 +，下方为 −。柱高保留两侧极值。", "Centre is zero; + above, − below. Bars retain extrema on both sides.")
    InstrumentHistoryKind.WEATHER -> os.t("每个点保留真实时间和数值；直线只连接连续同源观测，不平滑、不补点。", "Every dot retains its observed time and value. Straight lines only connect continuous same-source readings; no smoothing or invented points.")
    InstrumentHistoryKind.COUNT -> os.t("每次观测时前 5 分钟内的冲击候选数；阶梯是观测状态，下降表示旧候选移出窗口，不能相加或当新增事件。", "Candidates in the five minutes before each observation. Steps show observed counts; falling counts age out of the window. Do not sum them or infer new events.")
    InstrumentHistoryKind.COUNTER -> os.t("柱为同源连续读数的增量；虚线柱含断点，只计已知部分。零高柱表示确实未增长。", "Bars show same-source continuous increments. Dashed bars contain a break and include only known portions. A zero bar means no recorded increase.")
    else -> ""
}

private fun historySummary(os: OsStore, key: String, frame: InstrumentHistoryFrame): String {
    if(frame.points.isEmpty())return ""
    fun number(value: Double, signed: Boolean=false): String {
        val decimals=when {abs(value)<.01 -> 4;abs(value)<1 -> 2;else -> 1}
        return String.format(Locale.US, "%${if(signed)"+" else ""}.${decimals}f", if(value==0.0)0.0 else value)+" "+os.displayMetricUnit(key)
    }
    fun actual(point: HistoryPoint?):String=point?.let { os.formatMetric(key, if(key=="awa" || key=="twa")signedHistoryAngle(it.reading.value)else it.reading.value) } ?: "—"
    val lastRun=frame.latestRun
    return when(frame.kind) {
        InstrumentHistoryKind.DIRECTION -> frame.meanDirection?.let { os.t("本段平均方位 ", "segment mean ")+os.formatBearing(it) }
            ?: os.t("方向分散，暂无稳定平均方位", "directions are scattered; no stable mean")
        InstrumentHistoryKind.BEARING -> os.t("本段起点 ","segment start ")+actual(lastRun.firstOrNull())+os.t(" · 末次 "," · last ")+actual(lastRun.lastOrNull())
        InstrumentHistoryKind.RANGE -> os.t("本段平均 ", "segment mean ")+number(lastRun.map { it.value }.average())+os.t(" · 最高 ", " · peak ")+actual(frame.maximum)+
            if(lastRun.size>=2)os.t(" · 变化 ", " · change ")+number(lastRun.last().value-lastRun.first().value, true)else ""
        InstrumentHistoryKind.DEPTH -> os.t("最小 ", "minimum ")+actual(frame.minimum)+os.t(" · 最大 ", " · maximum ")+actual(frame.maximum)
        InstrumentHistoryKind.DEVIATION -> os.t("负向最远 ", "negative extent ")+number(minOf(0.0, frame.minimum?.value ?: 0.0))+os.t(" · 正向最远 ", " · positive extent ")+number(maxOf(0.0, frame.maximum?.value ?: 0.0))
        InstrumentHistoryKind.WEATHER -> if(lastRun.size>=2) {
            val elapsed=(lastRun.last().reading.elapsed-lastRun.first().reading.elapsed)/60_000.0
            os.t("连续 ${String.format(Locale.US,"%.1f",elapsed)} 分钟变化 ", "change over ${String.format(Locale.US,"%.1f",elapsed)} continuous min ")+number(lastRun.last().value-lastRun.first().value, true)
        }else os.t("已收到首个观测，等待同源后续数据", "first observation received; waiting for the next reading")
        InstrumentHistoryKind.COUNT -> os.t("末次窗口 ","last observed window ")+actual(frame.points.lastOrNull())+os.t(" · 最高窗口 "," · highest window ")+actual(frame.maximum)
        InstrumentHistoryKind.COUNTER -> os.t("已观测增量 ", "observed increase ")+(frame.incrementTotal?.let { number(it) } ?: "—")+os.t(" · 最新总量 ", " · latest total ")+actual(frame.points.lastOrNull())
    }
}

/** 固定回看窗口的墙钟标签；不会每次重组重新把历史点映射到“现在”。 */
internal fun historyTimeLabel(utcMillis:Long,chinese:Boolean):String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM,if(chinese)Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(utcMillis))

/** 同一毫秒发生来源/校准切换时仍能逐个选中，不能只把时间当成样本身份。 */
private fun historySampleId(reading:Reading):String =
    "${reading.elapsed}:${reading.sourceKey.length}:${reading.sourceKey}:${reading.continuityKey}"

/** 按物理来源身份及该字段有效期保留中断；同名设备不会被画成连续数据。 */
internal fun readingsAreContinuous(previous: Reading, next: Reading): Boolean =
    next.elapsed>=previous.elapsed && next.elapsed-previous.elapsed<=previous.validForMillis &&
        next.sourceKey==previous.sourceKey && next.continuityKey==previous.continuityKey && next.unit==previous.unit &&
        !(next.unit.startsWith("°") && abs(next.value-previous.value)>180)

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import com.yokuli.anchorwatch.domain.vessel.InstrumentTileId
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.contract.*
import java.util.Locale

/** 仪表与磁贴共用指标身份；旧 HEADING 绑定只在入口规范化，不再维护第二份数值映射。 */
internal fun tileInstrumentId(id:String):InstrumentTileId?=InstrumentTileId.entries.firstOrNull {it.name==if(id=="HEADING_TRUE")"HEADING"else id}
internal fun tileHasHistory(id:String,style:String)=id!="POSITION"&&(style=="trend"||style=="detail"&&id !in TileReadingPresentationPolicy.windIds)
internal fun tileHistoryMinutes(id:String,presentation:TilePresentation)=presentation.historyMinutes?.coerceIn(1,15) ?: if(id.startsWith("PRESSURE"))15 else 5

/** 量程只控制表达，不是警戒阈值，也不产生实际水深或可通航性判断。 */
internal fun tileDefaultRange(id:String,value:Double?=null):Pair<Double,Double> {
    val base=when(id) {
        "PRESSURE"->970.0 to 1050.0
        "AIR_TEMPERATURE","WATER_TEMPERATURE"->0.0 to 40.0
        "DEPTH"->0.0 to 50.0
        "UKC"->-2.0 to 10.0
        "HEEL","PITCH","RUDDER_ANGLE"->-45.0 to 45.0
        "TRUE_WIND_ANGLE","APPARENT_WIND_ANGLE"->-180.0 to 180.0
        "PRESSURE_TREND_1H","PRESSURE_TREND_3H","PRESSURE_TREND_6H"->-5.0 to 5.0
        "CROSS_TRACK_ERROR"->-.1 to .1
        "ROLL_RATE","PITCH_RATE","RATE_OF_TURN"->-10.0 to 10.0
        "VMG","VMC"->-10.0 to 10.0
        "MOTION_SCORE"->0.0 to 100.0
        "TRUE_WIND_SPEED","APPARENT_WIND_SPEED"->0.0 to 40.0
        "WAYPOINT_DISTANCE"->0.0 to 10.0
        else->0.0 to 15.0
    }
    val observed=value?.takeIf(Double::isFinite) ?: return base
    val span=base.second-base.first
    return minOf(base.first,kotlin.math.floor(observed/span)*span) to maxOf(base.second,kotlin.math.ceil(observed/span)*span)
}

/** 只有当前表现实际使用的配置才出现；所有修改回到原实例草稿，提交仍由 Shell 原子落盘。 */
@Composable internal fun TileMetricConfiguration(os:OsStore,choice:TileContentChoice,presentation:TilePresentation,onChange:(TilePresentation)->Unit) {
    val binding=choice.binding
    if(binding.kind==TileBindingKind.APP) {
        if(presentation.style=="summary"&&presentation.legacyMode==null) {
            Toggle(os.t("轮换摘要","Rotate summaries"),presentation.rotate!=false,
                os.t("警报与任务状态保持可见","Alarms and task state stay visible")) {onChange(presentation.copy(rotate=it))}
            if(presentation.rotate!=false)listOf(6,10,15).forEach {seconds->
                ChoiceRow(os.t("每 $seconds 秒","Every $seconds seconds"),(presentation.intervalSeconds ?: 6)==seconds) {onChange(presentation.copy(intervalSeconds=seconds))}
            }
        }
        return
    }
    if(binding.kind!=TileBindingKind.READING)return
    val id=binding.contentId
    val tile=tileInstrumentId(id) ?: return
    val metric=instrumentTrendKey(tile)
    val hasHistory=tileHasHistory(id,presentation.style)
    if(hasHistory) {
        AppSection(os.t("回看多久","History window"))
        listOf(1,5,15).forEach {minutes->ChoiceRow(os.t("近 $minutes 分钟","Last $minutes minutes"),tileHistoryMinutes(id,presentation)==minutes) {onChange(presentation.copy(historyMinutes=minutes))}}
        Label(os.t("只显示已有观测；未采集、来源变更或中断处留空。","Shows recorded observations only; missing samples and source changes remain gaps."),13,LocalMetro.current.muted)
    }
    val canRange=metric!=null&&(presentation.style in setOf("gauge","attitude")||hasHistory&&id !in TileReadingPresentationPolicy.directionIds&&id !in TileReadingPresentationPolicy.counterIds)
    var editingRange by remember(binding.contentKey) {mutableStateOf(false)}
    if(canRange&&metric!=null) {
        AppSection(os.t("显示量程","Display range"))
        val fixed=presentation.rangeMinimum!=null&&presentation.rangeMaximum!=null
        ChoiceRow(os.t("自动适配","Automatic"),!fixed) {onChange(presentation.copy(rangeMinimum=null,rangeMaximum=null))}
        MenuRow(os.t("固定量程","Fixed range"),if(fixed)os.formatMetric(metric,presentation.rangeMinimum)+" — "+os.formatMetric(metric,presentation.rangeMaximum)else os.t("指定刻度上下限","Choose scale limits"),"edit") {editingRange=true}
        if(fixed)Label(os.t("这是显示刻度，超出时明确标注；不会设定警报阈值。","This is a display scale. Out-of-range readings are labelled; no alarm threshold is changed."),13,LocalMetro.current.muted)
    }
    AppSection(os.t("补充信息","Supporting information"))
    Toggle(os.t("显示数据来源","Show source"),presentation.showSource) {onChange(presentation.copy(showSource=it))}
    Toggle(os.t("显示测量参考","Show measurement reference"),presentation.showReference,
        os.t("例如真北、船艏或测深基准；时效与警报始终保留","For example true north, bow or depth datum; age and alarms always remain")) {onChange(presentation.copy(showReference=it))}
    if(editingRange&&metric!=null) {
        val defaults=tileDefaultRange(id)
        var low by remember {mutableStateOf(String.format(Locale.US,"%.3f",os.displayMetricValue(metric,presentation.rangeMinimum ?: defaults.first)).trimEnd('0').trimEnd('.'))}
        var high by remember {mutableStateOf(String.format(Locale.US,"%.3f",os.displayMetricValue(metric,presentation.rangeMaximum ?: defaults.second)).trimEnd('0').trimEnd('.'))}
        val minimum=low.replace(',','.').toDoubleOrNull()?.takeIf(Double::isFinite)
        val maximum=high.replace(',','.').toDoubleOrNull()?.takeIf(Double::isFinite)
        val offset=os.displayMetricValue(metric,0.0);val scale=os.displayMetricValue(metric,1.0)-offset
        val configured=if(minimum!=null&&maximum!=null&&scale.isFinite()&&scale>0)presentation.copy(rangeMinimum=(minimum-offset)/scale,rangeMaximum=(maximum-offset)/scale)else null
        val valid=configured!=null&&minimum!!<maximum!!&&TileReadingPresentationPolicy.hasValidOptions(configured)
        AppDialog(onDismissRequest={editingRange=false}) {AppDialogSurface {
            AppDialogTitle(os.t("固定显示量程","Fixed display range"))
            Label(os.displayMetricUnit(metric),14,LocalMetro.current.muted)
            Field(os.t("下限","Minimum"),low,{low=it.take(24)})
            Field(os.t("上限","Maximum"),high,{high=it.take(24)})
            if(!valid)Label(os.t("请输入有效数字，并使上限大于下限。","Enter valid numbers with the maximum above the minimum."),13,LocalMetro.current.accentText)
            MetroButton(os.t("应用量程","Apply range"),{configured?.let(onChange);editingRange=false},primary=true,enabled=valid)
            MetroButton(os.t("取消","Cancel"),{editingRange=false})
        }}
    }
}

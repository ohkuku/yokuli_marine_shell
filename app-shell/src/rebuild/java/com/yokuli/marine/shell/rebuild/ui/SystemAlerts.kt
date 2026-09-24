package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yokuli.runtime.contract.AnchorCommandStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yokuli.anchorwatch.domain.model.*
import com.yokuli.anchorwatch.domain.condition.*
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.flow.distinctUntilChanged

/** A single system presentation of domain alarms. It does not own their thresholds or lifetimes. */
@Composable fun SystemMarineAlerts(os:OsStore) {
    val services=os.marine?.services?:return
    // 系统警报仍即时订阅变化；与警报无关的高频姿态/网络字段无需反复组合覆盖层。
    val state by remember(services) {services.state.distinctUntilChanged {old,new->
        old.active==new.active&&old.alarmSnapshot==new.alarmSnapshot&&old.conditions==new.conditions&&
            old.settings.alarmSnoozeMinutes==new.settings.alarmSnoozeMinutes
    }}.collectAsState(services.state.value)
    val tick=rememberMarineClock();val now=remember(tick){System.currentTimeMillis()}
    val active=state.active;val alarm=state.alarmSnapshot;val c=LocalMetro.current
    val monitor = os.marine?.system?.anchorCommands ?: return
    val commands by monitor.commands.collectAsState()
    val pending = commands.lastOrNull { !it.terminal }
    var pauseSession by rememberSaveable { mutableStateOf<Long?>(null) }
    var pauseDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var requestId by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    // 回执独立于警报 Dialog 是否仍显示；先等运行时和值守投影确认，再导航。
    LaunchedEffect(commands, active?.id, active?.paused, requestId) {
        val result = commands.firstOrNull { it.commandId == requestId } ?: return@LaunchedEffect
        if(result.status == AnchorCommandStatus.CONFIRMED) {
            if(result.type == com.yokuli.runtime.contract.AnchorCommandType.PAUSE &&
                active?.let { it.id == result.sessionId && it.paused } != true) return@LaunchedEffect
            val destination = pauseDestination
            requestId = null
            pauseDestination = null
            if(destination != null) os.openSystemDestination(destination)
        } else if(result.terminal) {
            feedback = os.t("操作未完成，当前值守状态没有得到确认。", "The action did not complete; the current watch state was not confirmed.")
            requestId = null
            pauseDestination = null
        }
    }
    val testing=alarm.type==AlarmType.ALARM_TEST&&alarm.state==AlarmState.ALARM
    Box(Modifier.fillMaxSize()) {
        if(testing)Column(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth().background(c.bg).border(2.dp,c.accent).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            AppSection(os.t("警报测试正在响铃","Alarm test is sounding"))
            MetroButton(os.t("我能听见，停止测试","I can hear it · stop test"),{services.preferences.confirmAlarmAudible();services.preferences.stopAlarmTest()},primary=true)
            MetroButton(os.t("停止测试","stop test"),services.preferences::stopAlarmTest)
        }
    }
    if(testing||active==null||active.paused)return
    data class Alert(val title:String,val value:String,val detail:String,val source:ConditionAlarmSource,val severity:SafetyAlert.Severity,val sortKey:String)
    fun buildAlerts(chinese:Boolean):List<Alert> {
        fun tr(zh:String,en:String)=if(chinese)zh else en
        return buildList {
        if(alarm.state in setOf(AlarmState.WARNING,AlarmState.ALARM)&&(active.alarmSnoozedUntil?:0L)<=now) {
            val title=when(alarm.type){
                AlarmType.ANCHOR_RADIUS_EXCEEDED->tr("越过锚警范围","anchor range exceeded")
                AlarmType.GPS_DATA_LOST->tr("船位数据丢失","position data lost")
                AlarmType.GPS_QUALITY_BAD->tr("船位质量下降","position quality degraded")
                AlarmType.NMEA_CONNECTION_LOST->tr("NMEA 连接丢失","NMEA connection lost")
                else->tr("锚警预警","anchor warning")
            }
            val radial=alarm.state==AlarmState.WARNING||alarm.type==AlarmType.ANCHOR_RADIUS_EXCEEDED
            val source=when(active.positionSource){GpsDataSource.SYSTEM.name->tr("手机 GPS","phone GPS");GpsDataSource.NMEA.name->"NMEA";GpsDataSource.DEMO.name->tr("演示数据","demo data");else->tr("所选船位来源","selected position source")}
            val detail=when {
                alarm.state==AlarmState.WARNING->tr("已越过 ${os.formatLength(active.warningRadiusMeters)} 预警范围；正式警戒半径 ${os.formatLength(active.alarmRadiusMeters)}。","Beyond the ${os.formatLength(active.warningRadiusMeters)} warning boundary; full alarm radius ${os.formatLength(active.alarmRadiusMeters)}.")
                alarm.type==AlarmType.ANCHOR_RADIUS_EXCEEDED->tr("警戒半径 ${os.formatLength(active.alarmRadiusMeters)}。请核对船况和周围环境。","Alarm radius ${os.formatLength(active.alarmRadiusMeters)}. Check the vessel and surroundings.")
                else->tr("缺失或可疑位置不进入监控计算。恢复可信船位前，不能判断船是否仍在范围内。","Missing or suspect positions are excluded from monitoring. Whether the vessel remains inside cannot be evaluated until accepted position returns.")
            }
            add(Alert(title,if(radial)os.formatLength(alarm.distanceMeters)else "—",detail+tr(" 船位来源：$source。"," Position source: $source."),ConditionAlarmSource.ANCHOR,
                if(alarm.state==AlarmState.WARNING)SafetyAlert.Severity.WARNING else SafetyAlert.Severity.ALARM,if(radial)"anchor" else "critical source lost"))
        }
        val depth=state.conditions.depth
        if((depth.alarmActive||depth.dataUnavailable)&&(active.depthAlarmSnoozedUntil?:0L)<=now)add(Alert(
            if(depth.dataUnavailable)tr("水深数据丢失","depth data lost")else if(depth.status==DepthGuardStatus.SHALLOW_ALARM)tr("浅水警报","shallow water")else tr("深水警报","deep water"),
            if(depth.dataUnavailable)"—" else os.formatDepth(depth.filteredDepthMeters),
            if(depth.dataUnavailable)tr("水深警戒仍开启，但缺少新鲜 NMEA 水深，无法检查阈值。请恢复来源，或在锚警中明确关闭该警戒。","The depth guard remains enabled, but cannot evaluate limits without fresh NMEA depth. Restore its source or explicitly disable this guard in anchor watch.")
            else if(depth.status==DepthGuardStatus.SHALLOW_ALARM)tr("NMEA 水深低于浅水阈值 ${os.formatDepth(active.shallowDepthAlarmMeters)}。","NMEA depth is below the shallow limit ${os.formatDepth(active.shallowDepthAlarmMeters)}.")
            else tr("NMEA 水深高于深水阈值 ${os.formatDepth(active.deepDepthAlarmMeters)}。","NMEA depth is above the deep limit ${os.formatDepth(active.deepDepthAlarmMeters)}."),
            ConditionAlarmSource.DEPTH,SafetyAlert.Severity.ALARM,if(depth.dataUnavailable)"depth data lost"else if(depth.status==DepthGuardStatus.SHALLOW_ALARM)"shallow"else "deep"))
        val wind=state.conditions.windSpeed
        if((wind.warningActive||wind.alarmActive||wind.dataUnavailable)&&(active.windAlarmSnoozedUntil?:0L)<=now)add(Alert(
            if(wind.dataUnavailable)tr("风数据丢失","wind data lost")else tr("风速警戒","wind speed alert"),
            if(wind.dataUnavailable)"—" else os.formatSpeed(wind.filteredSpeedKnots),
            if(wind.dataUnavailable)tr("风速警戒仍开启，但缺少所需的新鲜 NMEA 风速，无法检查阈值。请恢复来源，或明确关闭该警戒。","The wind guard remains enabled, but cannot evaluate limits without the required fresh NMEA wind. Restore its source or explicitly disable this guard.")
            else tr("预警 ${os.formatSpeed(active.windWarningKnots)} · 告警 ${os.formatSpeed(active.windAlarmKnots)}。","Warning ${os.formatSpeed(active.windWarningKnots)} · alarm ${os.formatSpeed(active.windAlarmKnots)}.")+when(wind.source){WindSpeedSource.TRUE->tr(" 当前使用真风。"," Using true wind.");WindSpeedSource.APPARENT->tr(" 当前使用视风回退值。"," Using apparent-wind fallback.");else->tr(" 当前来源未确认。"," Current source is unconfirmed.")},
            ConditionAlarmSource.WIND_SPEED,if(wind.warningActive&&!wind.alarmActive&&!wind.dataUnavailable)SafetyAlert.Severity.WARNING else SafetyAlert.Severity.ALARM,
            if(wind.dataUnavailable)"wind data lost"else "wind speed"))
        val shift=state.conditions.windShift
        if((shift.alarmActive||shift.dataUnavailable)&&(active.windShiftAlarmSnoozedUntil?:0L)<=now)add(Alert(
            if(shift.dataUnavailable)tr("风向数据丢失","wind direction data lost")else tr("风向发生变化","wind shift"),
            if(shift.dataUnavailable)"—" else os.formatAngle(shift.shiftDegrees),
            if(shift.dataUnavailable)tr("风向警戒仍开启，但缺少新鲜真风方向，无法与基线比较。请恢复 NMEA 风向来源，或明确关闭该警戒。","The wind-direction guard remains enabled, but cannot compare with its baseline without fresh true-wind direction. Restore its NMEA source or explicitly disable this guard.")
            else tr("固定基线 ${os.formatBearing(shift.baselineDirectionDegrees)} T → 当前 ${os.formatBearing(shift.currentDirectionDegrees)} T；变化阈值 ${os.formatAngle(active.windShiftThresholdDegrees)}。","Fixed baseline ${os.formatBearing(shift.baselineDirectionDegrees)} T → current ${os.formatBearing(shift.currentDirectionDegrees)} T; shift limit ${os.formatAngle(active.windShiftThresholdDegrees)}.")+when(shift.baselineSource){TrueWindDirectionSource.MWD->" NMEA MWD";TrueWindDirectionSource.MWV_TRUE_PLUS_HDT->" NMEA MWV + HDT";else->""},
            ConditionAlarmSource.WIND_SHIFT,SafetyAlert.Severity.ALARM,if(shift.dataUnavailable)"wind direction lost"else "wind shift"))
    }
    }
    // 通知保存双语原文；语言切换仅改变显示，不重写历史，也不重新触发警报。
    val chineseAlerts=buildAlerts(true)
    val englishAlerts=buildAlerts(false)
    val alerts=if(os.chinese)chineseAlerts else englishAlerts
    // 通知历史由进程级 Room 事件订阅写入；这里仅呈现当前需要用户处理的警报。
    // The retained domain sorter consumes English semantic keys. Display language must not change alarm priority.
    val primary=SafetyAlertAggregator.sorted(alerts.map{SafetyAlert(it.source,it.severity,it.sortKey,it.detail)}).firstOrNull()?.let{sorted->alerts.first{it.source==sorted.source}}?:return
    var end by remember(active.id){mutableStateOf(false)}
    Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)) {
        AppDialogSurface {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Glyph("warning",Modifier.size(24.dp),Color(0xFFD04848))
                AppDialogTitle(primary.title,Modifier.weight(1f))
            }
            Label(primary.value,42)
            Label(primary.detail)
            alerts.filter{it!=primary}.forEach{Label("${it.title} · ${it.value}",15,c.muted)}
            MetroButton(os.t("${state.settings.alarmSnoozeMinutes} 分钟后提醒","snooze ${state.settings.alarmSnoozeMinutes} min"),services.anchor::acknowledge,primary=true)
            MetroButton(os.t("稍后提醒并查看锚警","snooze & open anchor watch"),{services.anchor.acknowledge();os.openSystemDestination("anchor")})
            MetroButton(os.t("稍后提醒并查看船位来源", "snooze & view position source"), { services.anchor.acknowledge(); os.openSystemDestination("data_center:source/POSITION") })
            MetroButton(os.t("暂停监控并处理来源", "pause monitoring & inspect source"), {
                pauseSession = active.id; pauseDestination = "data_center:source/POSITION"
            }, enabled = pending == null)
            MetroButton(os.t("暂停监控", "pause monitoring"), {
                pauseSession = active.id; pauseDestination = null
            }, enabled = pending == null)
            MetroButton(os.t("起锚并结束值守","lift anchor & end watch"),{end=true}, enabled = pending == null)
            if(pending != null) {
                Label(if(pending.status == AnchorCommandStatus.UNKNOWN)
                    os.t("原操作仍待确认，请勿重复发送。", "The original action is still awaiting confirmation; do not send it again.")
                    else os.t("正在处理守锚操作…", "Processing the watch action…"), 15, c.muted)
                if(pending.status == AnchorCommandStatus.UNKNOWN)
                    MetroButton(os.t("重新查询这次请求", "recheck this request"), { monitor.recheck(pending.commandId) })
            }
            feedback?.let { Label(it, 15, c.accentText) }
        }
    }
    pauseSession?.let { sessionId -> AnchorPauseConfirmation(os, onDismiss = { pauseSession = null; pauseDestination = null }) {
        pauseSession = null
        feedback = null
        runCatching { services.anchor.requestPauseWatch(sessionId) }.onSuccess { requestId = it }.onFailure {
            pauseDestination = null
            feedback = os.t("未发送暂停请求，请检查当前值守。", "The pause request was not sent. Check the current watch.")
        }
    } }
    if(end)ConfirmDialog(os,os.t("起锚并结束值守？结束后不再监控此锚位。", "Lift anchor and end the watch? This anchor will no longer be monitored."),{end=false}) {
        end = false
        feedback = null
        runCatching { services.anchor.requestLiftAnchor(active.id) }.onSuccess { requestId = it }.onFailure {
            feedback = os.t("未发送起锚请求，请检查当前值守。", "The lift request was not sent. Check the current watch.")
        }
    }
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yokuli.anchorwatch.domain.model.*
import com.yokuli.anchorwatch.domain.condition.*
import com.yokuli.marine.shell.rebuild.*

/** A single system presentation of domain alarms. It does not own their thresholds or lifetimes. */
@Composable fun SystemMarineAlerts(os:OsStore) {
    val vm=os.marine?.vm?:return;val state by vm.ui.collectAsState()
    val tick=rememberMarineClock();val now=remember(tick){System.currentTimeMillis()}
    val active=state.active;val alarm=state.alarmSnapshot;val c=LocalMetro.current
    val testing=alarm.type==AlarmType.ALARM_TEST&&alarm.state==AlarmState.ALARM
    Box(Modifier.fillMaxSize()) {
        if(testing)Column(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth().background(c.bg).border(2.dp,c.accent).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Label(os.t("警报测试正在响铃","alarm test is sounding"),25,c.accent)
            MetroButton(os.t("我能听见，停止测试","I can hear it · stop test"),{vm.confirmAlarmAudible();vm.stopAlarmTest()},primary=true)
            MetroButton(os.t("停止测试","stop test"),vm::stopAlarmTest)
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
                alarm.state==AlarmState.WARNING->tr("已越过 ${os.formatDistance(active.warningRadiusMeters)} 预警范围；正式警戒半径 ${os.formatDistance(active.alarmRadiusMeters)}。","Beyond the ${os.formatDistance(active.warningRadiusMeters)} warning boundary; full alarm radius ${os.formatDistance(active.alarmRadiusMeters)}.")
                alarm.type==AlarmType.ANCHOR_RADIUS_EXCEEDED->tr("警戒半径 ${os.formatDistance(active.alarmRadiusMeters)}。请核对船况和周围环境。","Alarm radius ${os.formatDistance(active.alarmRadiusMeters)}. Check the vessel and surroundings.")
                else->tr("缺失或可疑位置不进入监控计算。恢复可信船位前，不能判断船是否仍在范围内。","Missing or suspect positions are excluded from monitoring. Whether the vessel remains inside cannot be evaluated until accepted position returns.")
            }
            add(Alert(title,if(radial)os.formatDistance(alarm.distanceMeters)else "—",detail+tr(" 船位来源：$source。"," Position source: $source."),ConditionAlarmSource.ANCHOR,
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
            if(shift.dataUnavailable)"—" else "${decimal(shift.shiftDegrees,0)}°",
            if(shift.dataUnavailable)tr("风向警戒仍开启，但缺少新鲜真风方向，无法与基线比较。请恢复 NMEA 风向来源，或明确关闭该警戒。","The wind-direction guard remains enabled, but cannot compare with its baseline without fresh true-wind direction. Restore its NMEA source or explicitly disable this guard.")
            else tr("固定基线 ${decimal(shift.baselineDirectionDegrees,0)}°T → 当前 ${decimal(shift.currentDirectionDegrees,0)}°T；变化阈值 ${decimal(active.windShiftThresholdDegrees,0)}°。","Fixed baseline ${decimal(shift.baselineDirectionDegrees,0)}°T → current ${decimal(shift.currentDirectionDegrees,0)}°T; shift limit ${decimal(active.windShiftThresholdDegrees,0)}°.")+when(shift.baselineSource){TrueWindDirectionSource.MWD->" NMEA MWD";TrueWindDirectionSource.MWV_TRUE_PLUS_HDT->" NMEA MWV + HDT";else->""},
            ConditionAlarmSource.WIND_SHIFT,SafetyAlert.Severity.ALARM,if(shift.dataUnavailable)"wind direction lost"else "wind shift"))
    }
    }
    // 通知保存双语原文；语言切换仅改变显示，不重写历史，也不重新触发警报。
    val chineseAlerts=buildAlerts(true)
    val englishAlerts=buildAlerts(false)
    val alerts=if(os.chinese)chineseAlerts else englishAlerts
    LaunchedEffect(active.id,alerts.map {it.sortKey to it.severity}) {
        chineseAlerts.forEach { alert ->
            val english=englishAlerts.first { it.sortKey==alert.sortKey }
            os.notify(alert.title+" · "+alert.detail,english.title+" · "+english.detail,
                app=AppId.ANCHOR,severity=if(alert.severity==SafetyAlert.Severity.ALARM)NoticeSeverity.ALARM else NoticeSeverity.WARNING,
                destination="anchor",key="anchor:${active.id}:${alert.sortKey}:${alert.severity}")
        }
    }
    // The retained domain sorter consumes English semantic keys. Display language must not change alarm priority.
    val primary=SafetyAlertAggregator.sorted(alerts.map{SafetyAlert(it.source,it.severity,it.sortKey,it.detail)}).firstOrNull()?.let{sorted->alerts.first{it.source==sorted.source}}?:return
    var end by remember(active.id){mutableStateOf(false)}
    Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)) {
        Column(Modifier.fillMaxWidth().heightIn(max=670.dp).background(c.bg).border(2.dp,Color(0xFFD04848)).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Label(primary.title,32,Color(0xFFD04848));Label(primary.value,42);Label(primary.detail,20)
            alerts.filter{it!=primary}.forEach{Label("${it.title} · ${it.value}",18,c.muted)}
            MetroButton(os.t("${state.settings.alarmSnoozeMinutes} 分钟后提醒","snooze ${state.settings.alarmSnoozeMinutes} min"),vm::acknowledge,primary=true)
            MetroButton(os.t("稍后提醒并查看锚警","snooze & open anchor watch"),{vm.acknowledge();os.open("anchor")})
            MetroButton(os.t("暂停并处理数据来源","pause & check data sources"),{vm.pauseWatch();os.open("nmea:sources")})
            MetroButton(os.t("暂停锚警","pause watch"),vm::pauseWatch)
            MetroButton(os.t("起锚并结束值守","lift anchor & end watch"),{end=true})
        }
    }
    if(end)ConfirmDialog(os,os.t("结束这次锚警值守？","End this anchor watch?"),{end=false}){vm.liftAnchor();end=false}
}

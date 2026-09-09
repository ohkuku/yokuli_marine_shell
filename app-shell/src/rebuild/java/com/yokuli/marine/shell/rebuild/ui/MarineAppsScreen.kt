package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.anchorwatch.LegacyMarineScreen
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Each marine app gets a Shell task; the engine and its background sessions remain shared. */
@Composable fun MarineAppScreen(os: OsStore, page: String) {
    val marine=os.marine
    if(marine==null) {
        Column { PageHeader(os,os.t("正在准备","getting ready")); Label(os.t("正在打开船舶数据…","Opening boat data…"),modifier=Modifier.padding(22.dp)) }
        return
    }
    val state by marine.vm.ui.collectAsState()
    LaunchedEffect(os.chinese,state.settingsReady) { marine.syncLanguage() }
    if(page=="trip") {
        TripRecorderScreen(os)
    } else {
        var nestedBack by remember(page) { mutableStateOf<(() -> Boolean)?>(null) }
        BindInternalAppInputHandler { input -> input == ShellInput.BACK && nestedBack?.invoke() == true }
        LegacyMarineScreen(page,marine.vm,os.chinese,LocalMetro.current.accent,os.light,os::back,
            onBackHandler={nestedBack=it})
    }
}

@Composable fun ChartAppScreen(os:OsStore) {
    var controls by remember {mutableStateOf(false)}
    Box(Modifier.fillMaxSize()) {
        ChartScreen(os,recording=os.recordingActive,onRecording={controls=true})
        if(os.positionSource=="demo") Label(os.t("演示船位","DEMO POSITION"),14,
            modifier=Modifier.align(Alignment.TopCenter).padding(top=64.dp).background(LocalMetro.current.bg).padding(6.dp))
    }
    if(controls) RecordingDialog(os) {controls=false}
}

@Composable private fun RecordingDialog(os:OsStore,onDismiss:()->Unit) {
    val marine=os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val active=state.activeTrip
    var name by remember {mutableStateOf(os.t("航行 ","Trip ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date()))}
    var motion by remember {mutableStateOf(false)}
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).border(1.dp,LocalMetro.current.muted).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Label(if(active==null) os.t("记录这次航行","record this trip") else active.name,31)
            if(active==null) {
                Field(os.t("名称","name"),name,{name=it.take(100)})
                Label(os.t("记录船位，以及实际可用的风、水深和航速。完成后可回放、查看报告和导出。","Record position and the wind, depth and speed available. Replay, reports and exports follow when you finish."),16,LocalMetro.current.muted)
                Toggle(os.t("同时记录船体运动","record vessel motion"),motion,
                    os.t("需要把手机固定在船上并在仪表中校准。","Mount the phone on the boat and calibrate it in instruments.")) {motion=it}
                MetroButton(os.t("开始记录","start recording"),{
                    marine.startRecording(name.trim(),motion);onDismiss()
                },primary=true,enabled=name.isNotBlank() && state.active==null)
                if(state.active!=null) Label(os.t("锚警报正在值守，请先起锚。","Lift the anchor before starting a trip."),16)
                if(os.positionSource=="none") MetroButton(os.t("选择船位来源","choose position source"),{onDismiss();os.open("data")})
            } else {
                Label(nm(active.distanceMeters),40,LocalMetro.current.accent)
                MetroButton(if(active.paused) os.t("继续记录","resume recording") else os.t("暂停记录","pause recording"),{
                    if(active.paused) marine.vm.resumeTrip() else marine.vm.pauseTrip();onDismiss()
                },primary=true)
                MetroButton(os.t("记录此刻","mark this moment"),{
                    marine.vm.markTripWaypoint(os.t("航行标记","Trip mark"),"","GENERAL");onDismiss()
                })
                MetroButton(os.t("结束并保存","finish & save"),{marine.vm.endTrip();onDismiss();os.open("voyages")})
                MetroButton(os.t("仪表与详情","instruments & details"),{onDismiss();os.open("instruments")})
            }
            MetroButton(os.t("返回海图","back to chart"),{onDismiss();os.open("chart")})
        }
    }
}

@Composable private fun TripRecorderScreen(os:OsStore) {
    val marine=os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    var controls by remember {mutableStateOf(false)}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit) {while(true) {delay(1000);now=System.currentTimeMillis()}}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("航行记录","trip recorder"))
        PageBody {
            val trip=state.activeTrip
            if(trip==null) {
                Label(os.t("出发吧。","let’s go."),48,LocalMetro.current.accent)
                Label(os.t("把沿途的风景、航迹和船况，留在同一段航行里。","Keep your track, moments and boat conditions together in one trip."),21)
                MetroButton(os.t("开始记录","start recording"),{controls=true},primary=true)
            } else {
                Label(trip.name,28)
                Label(nm(trip.distanceMeters),52,LocalMetro.current.accent)
                val elapsed=((if(trip.paused) trip.pausedAt ?: now else now)-trip.startedAt-trip.accumulatedPausedMillis).coerceAtLeast(0)/1000
                Label("%02d:%02d:%02d".format(elapsed/3600,elapsed/60%60,elapsed%60),30)
                Label(if(trip.paused) os.t("已暂停","paused") else os.t("正在记录","recording"),17,LocalMetro.current.muted)
                MetroButton(os.t("回到海图","back to chart"),{os.open("chart")},primary=true)
                MetroButton(os.t("管理记录","recording controls"),{controls=true})
            }
            MenuRow(os.t("仪表","instruments"),os.t("航行、帆船、运动、天气与自定义","navigation, sailing, motion, weather & custom"),"data") {os.open("instruments")}
            MenuRow(os.t("航行日志","logbook"),os.t("回放、报告、标记与导出","replay, reports, moments & exports"),"logbook") {os.open("voyages")}
            MenuRow(os.t("船位与数据","position & data"),os.t("查看当前来源","see current sources"),"locate") {os.open("data")}
        }
    }
    if(controls) RecordingDialog(os) {controls=false}
}

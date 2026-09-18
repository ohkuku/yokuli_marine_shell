package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.VoyagePhase
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date

@Composable fun ChartAppScreen(os:OsStore) {
    var controls by remember {mutableStateOf(false)}
    val voyage=os.marine?.voyage?.collectAsState()?.value
    Box(Modifier.fillMaxSize()) {
        ChartScreen(os,recording=voyage?.active==true,recordingPaused=voyage?.phase==VoyagePhase.PAUSED,onRecording={controls=true})
        if(os.positionSource=="demo") Label(os.t("演示船位","DEMO POSITION"),14,
            modifier=Modifier.align(Alignment.TopCenter).padding(top=64.dp).background(LocalMetro.current.bg).padding(6.dp))
    }
    if(controls) RecordingDialog(os) {controls=false}
}

@Composable internal fun RecordingDialog(os:OsStore,initialMarking:Boolean=false,onDismiss:()->Unit) {
    val marine=os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val voyage by marine.voyage.collectAsState()
    val active=state.activeTrip
    var name by remember {mutableStateOf(os.t("航行 ","Trip ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date()))}
    var motion by remember {mutableStateOf(false)}
    var marking by remember {mutableStateOf(initialMarking)}
    var momentName by remember(active?.id) {mutableStateOf(os.t("航行标记 ","Trip mark ")+((active?.waypointCount ?: 0)+1))}
    var momentNote by remember {mutableStateOf("")}
    var momentType by remember {mutableStateOf("GENERAL")}
    val data by os.hub.state.collectAsState()
    val now=rememberMarineClock()
    val canMark=active!=null && !active.paused && data.fix(os.positionSource)?.fresh(now)==true
    val canMotion=state.phoneSensorCapabilities.attitudeAvailable && state.vesselMountCalibration.mountConfirmed && state.phoneVesselMountState==com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState.VESSEL_MOUNTED
    val dismiss={if(marking)marking=false else onDismiss()}
    Dialog(onDismissRequest=dismiss) {
        Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).border(1.dp,LocalMetro.current.muted).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Label(if(marking)os.t("记录此刻","mark this moment") else if(active==null) os.t("记录这次航行","record this trip") else active.name,31)
            if(marking && active!=null) {
                Field(os.t("时刻名称","moment name"),momentName,{momentName=it.take(100)})
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    listOf("GENERAL" to os.t("随记","moment"),"SAIL_CHANGE" to os.t("换帆","sail change"),"WEATHER" to os.t("天气","weather"),"HAZARD" to os.t("注意点","hazard")).forEach {(type,label)->
                        Label(label,22,if(momentType==type)LocalMetro.current.accent else LocalMetro.current.muted,Modifier.clickable {momentType=type}.padding(vertical=8.dp))
                    }
                }
                Field(os.t("想记住什么","what happened"),momentNote,{momentNote=it.take(2000)},multiline=true)
                Label(if(canMark)os.t("保存时记录当时的有效船位与船况。","Saving captures the valid position and conditions at that moment.") else if(active.paused)os.t("请先继续记录，再保存时刻。","Resume recording before marking a moment.") else os.t("等待有效船位后才能保存时刻。","A valid position is needed to mark this moment."),16,LocalMetro.current.muted)
                MetroButton(os.t("保存时刻","save moment"),{marine.vm.markTripWaypoint(momentName.trim(),momentNote.trim(),momentType);onDismiss()},primary=true,enabled=canMark&&momentName.isNotBlank())
            } else if(active==null) {
                Field(os.t("名称","name"),name,{name=it.take(100)})
                Label(os.t("记录船位，以及实际可用的风、水深和航速。完成后可回放、查看报告和导出。","Record position and the wind, depth and speed available. Replay, reports and exports follow when you finish."),16,LocalMetro.current.muted)
                Toggle(os.t("同时记录船体运动","record vessel motion"),motion,
                    os.t("需要把手机固定在船上并在数据中心校准。","Mount the phone on the boat and calibrate it in Data Center."),enabled=canMotion) {motion=it}
                MetroButton(if(voyage.commandPending)os.t("正在开始…","starting…")else os.t("开始记录","start recording"),{
                    marine.startRecording(name.trim(),motion&&canMotion);onDismiss()
                },primary=true,enabled=!voyage.commandPending && name.isNotBlank() && os.positionSource in listOf("phone","nmea"))
                if(os.positionSource=="none") Label(os.t("还没有选择船位。在“数据中心”开启手机定位，或选择已连接的船载来源。","No position source is selected. Enable phone location or choose a connected boat source in Data Center."),17,LocalMetro.current.muted)
            } else {
                Label(os.formatDistance(active.distanceMeters),40,LocalMetro.current.accent)
                MetroButton(if(active.paused) os.t("继续记录","resume recording") else os.t("暂停记录","pause recording"),{
                    if(active.paused) marine.resumeRecording() else marine.pauseRecording();onDismiss()
                },primary=true,enabled=!voyage.commandPending)
                MetroButton(os.t("记录此刻","mark this moment"),{marking=true},enabled=!active.paused)
                if(active.paused)Label(os.t("记录已暂停；继续后可以保存沿途时刻。","Recording is paused. Resume to mark moments."),15,LocalMetro.current.muted)
                MetroButton(if(voyage.phase==VoyagePhase.SAVING)os.t("正在保存…","saving…")else os.t("结束并保存","finish & save"),{marine.finishRecording();onDismiss()},enabled=!voyage.commandPending)
                MetroButton(os.t("查看本次航行","view this voyage"),{onDismiss();os.open("voyage:${active.id}")})
            }
            MetroButton(if(marking)os.t("返回记录控制","back to recording controls") else os.t("完成","done"),dismiss)
        }
    }
}

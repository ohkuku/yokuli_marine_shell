package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.VoyagePhase
import com.yokuli.runtime.contract.VoyageRequestStatus
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date

@Composable fun ChartAppScreen(os:OsStore,initialAisMmsi:Int?=null) {
    var controls by remember {mutableStateOf(false)}
    val voyage=os.marine?.voyage?.collectAsState()?.value
    Box(Modifier.fillMaxSize()) {
        ChartScreen(os,recording=voyage?.active==true,recordingPaused=voyage?.phase==VoyagePhase.PAUSED,onRecording={controls=true},initialAisMmsi=initialAisMmsi,interactionBlocked=controls)
        if(os.positionSource=="demo") Label(os.t("演示船位","DEMO POSITION"),14,
            modifier=Modifier.align(Alignment.TopCenter).padding(top=64.dp).background(LocalMetro.current.bg).padding(6.dp))
    }
    if(controls) RecordingDialog(os) {controls=false}
}

@Composable internal fun RecordingDialog(os:OsStore,initialMomentRequestId:String?=null,onDismiss:()->Unit) {
    val marine=os.marine ?: return
    val state by marine.services.state.collectAsState()
    val voyage by marine.voyage.collectAsState()
    val commands by marine.system.voyage.commands.collectAsState()
    val currentCommand = commands.lastOrNull { !it.terminal } ?: commands.lastOrNull()
    val active=state.activeTrip
    var name by remember {mutableStateOf(os.t("航行 ","Trip ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date()))}
    var motion by remember {mutableStateOf(false)}
    var momentRequestId by rememberSaveable {mutableStateOf(initialMomentRequestId)}
    val marking=momentRequestId!=null
    val canMotion=state.phoneSensorCapabilities.attitudeAvailable && state.vesselMountCalibration.mountConfirmed && state.phoneVesselMountState==com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState.VESSEL_MOUNTED
    val dismiss={if(marking)momentRequestId=null else onDismiss()}
    AppDialog(onDismissRequest=dismiss) {
        AppDialogSurface() {
            AppDialogTitle(if(marking)os.t("记录此刻","mark this moment") else if(active==null) os.t("记录这次航行","record this trip") else active.name)
            if(!marking) VoyageCommandFeedback(os,currentCommand)
            if(marking) {
                CapturedMomentContent(os,momentRequestId!!)
            } else if(active==null) {
                Field(os.t("名称","name"),name,{name=it.take(100)})
                Label(os.t("记录船位，以及实际可用的风、水深和航速。完成后可回放、查看报告和导出。","Record position and the wind, depth and speed available. Replay, reports and exports follow when you finish."),15,LocalMetro.current.muted)
                AppCheckRow(os.t("同时记录船体运动","record vessel motion"),motion,
                    os.t("需要把手机固定在船上并在数据中心校准。","Mount the phone on the boat and calibrate it in Data Center."),enabled=canMotion) {motion=!motion}
                MetroButton(if(currentCommand?.status==VoyageRequestStatus.UNKNOWN)os.t("结果未确认","result unconfirmed")else if(voyage.commandPending)os.t("正在开始…","starting…")else os.t("开始记录","start recording"),{
                    marine.startRecording(name.trim(),motion&&canMotion);onDismiss()
                },primary=true,enabled=!voyage.commandPending && name.isNotBlank() && os.positionSource in listOf("phone","nmea"))
                if(os.positionSource=="none") Label(os.t("还没有选择船位。在“数据中心”开启手机定位，或选择已连接的船载来源。","No position source is selected. Enable phone location or choose a connected boat source in Data Center."),15,LocalMetro.current.muted)
            } else {
                Label(os.formatDistance(active.distanceMeters),40,LocalMetro.current.accent)
                MetroButton(if(active.paused) os.t("继续记录","resume recording") else os.t("暂停记录","pause recording"),{
                    if(active.paused) marine.resumeRecording() else marine.pauseRecording();onDismiss()
                },primary=true,enabled=!voyage.commandPending)
                MetroButton(os.t("记一笔","capture a moment"),{momentRequestId=captureVoyageMoment(os,active.id)})
                if(active.paused)Label(os.t("轨迹已暂停，仍可记一笔；不会继续录制。","Track recording is paused. Capturing a moment does not resume it."),15,LocalMetro.current.muted)
                MetroButton(if(voyage.phase==VoyagePhase.SAVING)os.t("正在保存…","saving…")else os.t("结束并保存","finish & save"),{marine.finishRecording();onDismiss()},enabled=mayFinishVoyage(commands))
                MetroButton(os.t("查看本次航行","view this voyage"),{onDismiss();os.openLinked("voyage:${active.id}")})
            }
            MetroButton(if(marking)os.t("返回记录控制","back to recording controls") else os.t("完成","done"),dismiss)
        }
    }
}

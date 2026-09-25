package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.yokuli.anchorwatch.domain.model.PositionHealth
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.*
import com.yokuli.runtime.contract.ais.AisInputState
import kotlinx.coroutines.flow.distinctUntilChanged

/** 任务完全来自领域状态，不写入可清除的通知历史，也不以面板生命周期启动或停止。 */
@Composable internal fun NotificationTaskCards(os: OsStore, onOpenDestination: (String) -> Unit) {
    val marine = os.marine ?: return
    val services = marine.services
    val state by remember(services) { services.state.distinctUntilChanged { before, after ->
        before.active == after.active && before.acceptedPosition.health == after.acceptedPosition.health &&
            before.acceptedPosition.reason == after.acceptedPosition.reason &&
            before.runtimeDiagnostics.serviceReady == after.runtimeDiagnostics.serviceReady &&
            before.runtimeDiagnostics.restoreError == after.runtimeDiagnostics.restoreError &&
            before.runtimeDiagnostics.activeOwners == after.runtimeDiagnostics.activeOwners
    } }.collectAsState(services.state.value)
    val voyage by marine.system.voyage.state.collectAsState()
    val anchorCommands by marine.system.anchorCommands.commands.collectAsState()
    val voyageCommands by marine.system.voyage.commands.collectAsState()
    val traffic by remember(marine.system.ais) { marine.system.ais.snapshot.distinctUntilChanged { before, after ->
        before.preferences.monitoringEnabled == after.preferences.monitoringEnabled && before.runtime == after.runtime &&
            before.backgroundLimitations == after.backgroundLimitations && before.ownship?.positionValid == after.ownship?.positionValid &&
            before.preferences.soundEnabled == after.preferences.soundEnabled && before.events.count { it.active } == after.events.count { it.active } &&
            before.inputs.map { it.state } == after.inputs.map { it.state }
    } }.collectAsState(marine.system.ais.snapshot.value)
    val pendingAnchor = anchorCommands.lastOrNull { !it.terminal }
    val active = state.active
    val anchorResult = anchorCommands.lastOrNull { it.expectedSessionId == active?.id && it.terminal }
    val voyageResult = voyageCommands.lastOrNull { !it.terminal }
        ?: voyageCommands.lastOrNull { it.request.expectedSessionId == voyage.id }
    var confirmPauseSession by rememberSaveable { mutableStateOf<Long?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val hasAnchor = active != null || pendingAnchor != null
    val hasVoyage = voyage.active || voyage.commandPending
    val hasTraffic = traffic.preferences.monitoringEnabled
    if(!hasAnchor && !hasVoyage && !hasTraffic) return
    val c = LocalMetro.current
    val tick = rememberMarineClock()
    val nowUtc = remember(tick) { System.currentTimeMillis() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(os.t("当前任务", "current tasks"), 20)
        if(hasAnchor) {
            val recovery = active?.paused == false && (!state.runtimeDiagnostics.serviceReady ||
                com.yokuli.anchorwatch.runtime.RuntimeOwner.ANCHOR_WATCH !in state.runtimeDiagnostics.activeOwners)
            val limited = active?.paused == false && state.acceptedPosition.health != PositionHealth.GPS_OK
            val status = when {
                pendingAnchor?.status == AnchorCommandStatus.UNKNOWN -> os.t("结果未确认", "result unconfirmed")
                pendingAnchor != null -> os.t("正在处理", "processing")
                active?.paused == true -> os.t("已暂停", "paused")
                recovery -> os.t("需要恢复", "recovery needed")
                limited -> os.t("数据受限", "data limited")
                else -> os.t("监控中", "monitoring")
            }
            TaskCard(os, os.t("守锚", "anchor watch"), status, "anchor", onOpenDestination) {
                active?.let { session ->
                    Label(os.t("警戒范围 ", "watch radius ") + os.formatLength(session.alarmRadiusMeters), 15, c.muted)
                    Label(os.t("船位来源 · ", "position source · ") + when(session.positionSource) {
                        "SYSTEM" -> os.t("手机", "phone")
                        "NMEA" -> os.t("船载", "aboard")
                        "DEMO" -> os.t("演示", "demo")
                        else -> os.t("尚未确认", "not confirmed")
                    }, 15, c.muted)
                    if(recovery) Label(os.t("运行时尚未恢复这次值守，打开守锚检查恢复状态。", "The runtime has not restored this watch. Open Anchor Watch to inspect recovery."), 14, c.muted)
                    if(limited) Label(os.t("等待可信船位，当前不能确认船是否仍在警戒范围内。", "Awaiting accepted position; the vessel cannot currently be confirmed within the watch boundary."), 14, c.muted)
                    if(session.paused) Label(os.t("越界监控已暂停；继续监控前会重新检查船位。", "Boundary monitoring is paused. Position is checked again before monitoring resumes."), 14, c.muted)
                    if(pendingAnchor == null) MetroButton(if(session.paused) os.t("继续监控", "resume monitoring") else os.t("暂停监控", "pause monitoring"), {
                        feedback = null
                        if(!session.paused) confirmPauseSession = session.id
                        else runCatching { services.anchor.requestResumeWatch(session.id) }.onFailure {
                            feedback = os.t("守锚请求未发送，请查看当前值守状态。", "The watch request was not sent. Review the current watch state.")
                        }
                    })
                }
                if(pendingAnchor?.status == AnchorCommandStatus.UNKNOWN) {
                    Label(os.t("运行时仍在确认原请求。此处不会再次暂停、恢复或创建值守。", "The runtime is still confirming the original request. This does not issue another pause, resume or start."), 14, c.muted)
                    MetroButton(os.t("重新查询这次请求", "recheck this request"), { marine.system.anchorCommands.recheck(pendingAnchor.commandId) })
                }
                anchorResult?.takeIf { it.status in setOf(AnchorCommandStatus.FAILED, AnchorCommandStatus.REJECTED) && !it.resultPresented }?.let { result ->
                    Label(os.t("上次操作未完成，请查看当前值守。", "The previous action did not complete. Review the current watch."), 14, c.accent)
                    MetroButton(os.t("已看到操作结果", "dismiss action result"), { marine.system.anchorCommands.acknowledgeResult(result.commandId) })
                }
            }
        }
        if(hasVoyage) TaskCard(os, os.t("航行记录", "voyage recording"), when {
            voyageResult?.status == VoyageRequestStatus.UNKNOWN -> os.t("结果未确认", "result unconfirmed")
            voyage.phase == VoyagePhase.SAVING -> os.t("正在保存", "saving")
            voyage.commandPending -> os.t("正在处理", "processing")
            voyage.phase == VoyagePhase.PAUSED -> os.t("已暂停", "paused")
            else -> os.t("记录中", "recording")
        }, "voyages", onOpenDestination) {
            if(voyage.name.isNotBlank()) Label(voyage.name, 16)
            Label(durationLabel(voyage.elapsedMillis(nowUtc)) + " · " + os.formatDistance(voyage.distanceMeters), 15, c.muted)
            if(voyageResult?.status == VoyageRequestStatus.UNKNOWN)
                Label(os.t("尚未确认原操作，继续等待运行时结果；不重复发送。", "The original action is unconfirmed. Awaiting its runtime result; it will not be sent again."), 14, c.muted)
            if(voyageResult?.status == VoyageRequestStatus.UNKNOWN)
                MetroButton(os.t("重新查询这次请求", "recheck this request"), { marine.system.voyage.recheck(voyageResult.request.requestId) })
            if(voyageResult?.status in setOf(VoyageRequestStatus.FAILED, VoyageRequestStatus.REJECTED))
                Label(os.t("上次记录操作未完成，请核对当前状态。", "The previous recording action did not complete. Review the current state."), 14, c.accent)
            if(!voyage.commandPending && voyage.phase in setOf(VoyagePhase.RECORDING, VoyagePhase.PAUSED))
                MetroButton(if(voyage.phase == VoyagePhase.PAUSED) os.t("继续记录", "resume recording") else os.t("暂停记录", "pause recording"), {
                    marine.system.voyage.request(VoyageRequest(
                        if(voyage.phase == VoyagePhase.PAUSED) VoyageAction.RESUME else VoyageAction.PAUSE,
                        expectedSessionId = voyage.id))
                })
        }
        if(hasTraffic) TaskCard(os, os.t("AIS 交通监控", "AIS traffic watch"), when {
            !traffic.runtime.ready -> os.t("正在准备", "preparing")
            traffic.backgroundLimitations.isNotEmpty() || traffic.inputs.none { it.state == AisInputState.ONLINE } -> os.t("监控受限", "monitoring limited")
            else -> os.t("监控中", "monitoring")
        }, "ais", onOpenDestination) {
            val online = traffic.inputs.count { it.state == AisInputState.ONLINE }
            val risks = traffic.events.count { it.active }
            Label(os.t("$online 条接收在线 · $risks 项活动提醒", "$online receivers online · $risks active alerts"), 15, c.muted)
            if(traffic.inputs.isEmpty()) Label(os.t("还没有接收 AIS 的连接。", "No connection is receiving AIS yet."), 14, c.muted)
            if(!traffic.runtime.foregroundActive) Label(os.t("后台交通监控尚未运行，请进入 AIS 检查恢复状态。", "Background traffic monitoring is not running. Open AIS to check recovery."), 14, c.muted)
            if(!traffic.runtime.notificationsAllowed) Label(os.t("Android 通知受限，后台提醒可能不可见。", "Android notifications are restricted; background alerts may not be visible."), 14, c.muted)
            if(traffic.runtime.backgroundRestricted) Label(os.t("Android 正在限制后台运行。", "Android is restricting background execution."), 14, c.muted)
            if(traffic.preferences.soundEnabled && !traffic.runtime.soundAllowed) Label(os.t("提醒声音受系统设置限制。", "System settings restrict alert sounds."), 14, c.muted)
            if(traffic.ownship?.positionValid != true) Label(os.t("本船位置尚未确认，不能计算相对交通风险。", "Own position is unconfirmed; relative traffic risk cannot be calculated."), 14, c.muted)
            if(traffic.runtime.foregroundError != null) Label(os.t("后台服务启动失败，请进入监控设置处理。", "The background service failed to start. Open monitoring settings."), 14, c.muted)
            if(traffic.runtime.persistenceError != null) Label(os.t("监控设置尚未保存，请进入 AIS 重试。", "Monitoring settings were not saved. Open AIS to retry."), 14, c.muted)
            MetroButton(os.t("监控设置", "monitoring settings"), { onOpenDestination("ais:settings") })
        }
        feedback?.let { Label(it, 14, c.accent) }
    }
    confirmPauseSession?.let { sessionId ->
        AnchorPauseConfirmation(os, onDismiss = { confirmPauseSession = null }) {
            confirmPauseSession = null
            runCatching { services.anchor.requestPauseWatch(sessionId) }.onFailure {
                feedback = os.t("未发送暂停请求，请核对当前值守状态。", "The pause request was not sent. Review the current watch state.")
            }
        }
    }
}

/** 主体导航与操作按钮是相邻命中区域，避免父点击吞掉动作或重复执行。 */
@Composable private fun TaskCard(os: OsStore, title: String, status: String, destination: String, onOpenDestination: (String) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalMetro.current
    Column(Modifier.fillMaxWidth().background(c.subtle).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpenDestination(destination) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(title,15,weight=FontWeight.SemiBold)
                Label(status,12,c.accentText)
            }
            Glyph("next", Modifier.size(22.dp), c.muted)
        }
        content()
    }
}

@Composable internal fun AnchorPauseConfirmation(os: OsStore, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AppDialog(onDismissRequest = onDismiss) {
        AppDialogSurface {
            AppDialogTitle(os.t("暂停守锚监控？", "Pause anchor monitoring?"))
            Label(os.t("暂停后不再检查船舶是否越过警戒范围，守锚保护与相关监控会停止。锚点和已记录的轨迹会保留，之后可明确继续监控。", "Pausing stops boundary checks and the associated anchor protection. The anchor and recorded track are retained; you can explicitly resume monitoring later."))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroButton(os.t("暂停监控", "Pause monitoring"), onConfirm, Modifier.weight(1f), primary = true)
                MetroButton(os.t("继续值守", "Keep monitoring"), onDismiss, Modifier.weight(1f))
            }
        }
    }
}

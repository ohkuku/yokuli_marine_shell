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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.yokuli.anchorwatch.domain.model.PositionHealth
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.marine.shell.rebuild.chart.ChartBundlePhase
import com.yokuli.runtime.contract.*
import com.yokuli.runtime.contract.ais.AisInputState
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.navigation.NavigationPhase
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
    val residency by marine.system.residency.state.collectAsState()
    val charts by marine.system.charts.state.collectAsState()
    val navigation by marine.system.navigation.state.collectAsState()
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
    val bundleTask=os.maps.bundles.task?.takeIf {os.maps.bundles.busy&&it.phase in setOf(ChartBundlePhase.VERIFYING,ChartBundlePhase.IMPORTING_DATA,ChartBundlePhase.IMPORTING_CHARTS,ChartBundlePhase.EXPORTING,ChartBundlePhase.COMMITTING,ChartBundlePhase.CLEANING)}
    val importJob = charts.activeJob?.takeIf { it.phase in setOf(ChartImportPhase.COPYING, ChartImportPhase.PARSING, ChartImportPhase.INDEXING, ChartImportPhase.COMMITTING)&&it.requestId!="atlas-data-${bundleTask?.requestId}" }
    val exportJob = charts.exportJob?.takeIf { it.phase in setOf(ChartExportPhase.PREPARING, ChartExportPhase.PACKAGING, ChartExportPhase.COPYING)&&it.requestId!="atlas-export-${bundleTask?.requestId}" }
    val navigationSession = navigation.session?.takeIf { it.ongoing }
    val hasPhoneCollection = residency.phoneLocation || residency.phoneHeading || residency.phoneMotion || residency.phonePressure
    val hasConnections = residency.inputConnections > 0 || residency.outputConnections > 0
    if(!hasAnchor && !hasVoyage && !hasTraffic && bundleTask==null && importJob == null && exportJob == null && navigationSession == null &&
        !hasPhoneCollection && !hasConnections && !residency.sharing) return
    val c = LocalMetro.current
    val tick = rememberMarineClock()
    val nowUtc = remember(tick) { System.currentTimeMillis() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(os.t("当前任务", "current tasks"), 20)
        bundleTask?.let {job->
            TaskCard(os,os.title(AppId.LIBRARY)+os.t(" · 资料包"," · Collection"),when(job.phase) {
                ChartBundlePhase.VERIFYING->os.t("正在读取文件","Reading files")
                ChartBundlePhase.IMPORTING_DATA->os.t("正在准备数据","Preparing data")
                ChartBundlePhase.IMPORTING_CHARTS->os.t("正在登记海图","Adding charts")
                ChartBundlePhase.EXPORTING->os.t("正在导出","Exporting")
                else->os.t("正在保存资料","Saving collection")
            },"library",onOpenDestination) {
                Label(job.name,15)
                if(job.fileCount>0)Label(os.t("文件 ${job.fileIndex} / ${job.fileCount}","File ${job.fileIndex} / ${job.fileCount}"),13,c.muted)
                if(job.total>0)TaskProgress(os,job.completed.toLong(),job.total.toLong(),os.t("当前文件 ","Current file "))
                if(job.cancellable)MetroButton(os.t("取消","Cancel"),os.maps.bundles::cancelImport)
            }
        }
        importJob?.let { job ->
            TaskCard(os, os.title(AppId.LIBRARY) + os.t(" · 导入数据", " · Importing data"), when(job.phase) {
                ChartImportPhase.COPYING -> os.t("正在读取文件", "Reading files")
                ChartImportPhase.PARSING -> os.t("正在解析数据", "Reading geographic data")
                ChartImportPhase.INDEXING -> os.t("正在建立查询索引", "Preparing lookup index")
                else -> os.t("正在保存", "Saving")
            }, "library:data", onOpenDestination) {
                Label(job.name, 15)
                val hasCurrentFile = job.fileCount > 0 && job.fileIndex in 1..job.fileCount
                if(hasCurrentFile) Label(
                    os.t("文件 ${job.fileIndex} / ${job.fileCount}", "File ${job.fileIndex} / ${job.fileCount}") +
                        job.fileName.orEmpty().takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(), 13, c.muted)
                if(job.phase == ChartImportPhase.INDEXING && hasCurrentFile && job.total > 0) {
                    TaskProgress(os, job.completed.toLong(), job.total.toLong(), os.t("当前文件 ", "Current file "))
                }
                if(job.phase != ChartImportPhase.COMMITTING) MetroButton(os.t("取消导入", "Cancel import"), {
                    marine.system.charts.cancelImport(job.requestId)
                })
            }
        }
        exportJob?.let { job ->
            TaskCard(os, os.title(AppId.LIBRARY) + if(job.chart)os.t(" · 生成海图", " · Creating chart")else os.t(" · 导出数据", " · Exporting data"), when(job.phase) {
                ChartExportPhase.PREPARING -> if(job.chart)os.t("正在准备海图", "Preparing chart")else os.t("正在准备文件", "Preparing files")
                ChartExportPhase.PACKAGING -> if(job.chart)os.t("正在绘制图块", "Drawing tiles")else os.t("正在打包", "Packaging")
                else -> os.t("正在写入目标文件", "Writing destination file")
            }, "chartdataset:${job.datasetId}", onOpenDestination) {
                Label(job.name, 15)
                TaskProgress(os, job.completed, job.total)
                MetroButton(if(job.chart)os.t("取消生成", "Cancel creation")else os.t("取消导出", "Cancel export"), { marine.system.charts.cancelExport(job.requestId) })
            }
        }
        navigationSession?.let { session ->
            TaskCard(os, os.title(AppId.CHART) + os.t(" · 导航", " · Navigation"), when {
                session.phase == NavigationPhase.PAUSED -> os.t("已暂停", "Paused")
                session.phase == NavigationPhase.RECOVERY_REQUIRED -> os.t("等待确认恢复", "Review before resuming")
                navigation.guidance?.live != true -> os.t("等待船位更新", "Waiting for position")
                else -> os.t("引导中", "Guiding")
            }, "chart", onOpenDestination) {
                (navigation.guidance?.targetName ?: session.target?.name ?: session.route?.name)
                    ?.takeIf(String::isNotBlank)?.let { Label(it, 15) }
                navigation.guidance?.takeIf { it.live }?.remainingMeters?.let { Label(os.t("剩余 ", "Remaining ") + os.formatDistance(it), 14, c.muted) }
                if(navigation.backgroundIssue != null) Label(os.t("导航持续运行受限，请打开海图查看。", "Navigation needs attention. Open Chart for details."), 13, c.accentText)
            }
        }
        if(hasPhoneCollection) TaskCard(os, os.t("数据中心", "Data Center"), os.t("手机数据采集中", "Collecting phone data"), "data_center", onOpenDestination) {
            Label(buildList {
                if(residency.phoneLocation) add(os.t("船位", "Position"))
                if(residency.phoneHeading) add(os.t("船首向", "Heading"))
                if(residency.phoneMotion) add(os.t("姿态", "Attitude"))
                if(residency.phonePressure) add(os.t("气压", "Pressure"))
            }.joinToString(" · "), 13, c.muted)
        }
        if(hasConnections) TaskCard(os, os.t("船联网", "Boat Network"), os.t("连接已启用", "Connections enabled"), "nmea", onOpenDestination) {
            Label(buildList {
                if(residency.inputConnections > 0) add(os.t("${residency.inputConnections} 路接收", "${residency.inputConnections} inputs"))
                if(residency.outputConnections > 0) add(os.t("${residency.outputConnections} 路发送", "${residency.outputConnections} outputs"))
            }.joinToString(" · "), 13, c.muted)
        }
        if(residency.sharing) TaskCard(os, os.t("数据共享", "Data Sharing"), os.t("正在提供数据", "Publishing data"), "local_nmea", onOpenDestination) {
            Label(os.t("本机服务或数据发送已开启", "Local service or data output is enabled"), 13, c.muted)
        }
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
            TaskCard(os, os.t("守锚", "Anchor Watch"), status, "anchor", onOpenDestination) {
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
        if(hasVoyage) TaskCard(os, os.title(AppId.VOYAGES) + os.t(" · 记录", " · Recording"), when {
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

/** 进度只反映当前阶段的已处理量，不把文件、对象和字节合成为虚假的全程百分比。 */
@Composable private fun TaskProgress(os: OsStore, completed: Long, total: Long, labelPrefix: String = "") {
    val fraction = if(total > 0) (completed.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else null
    if(fraction == null) MetroProgress(os.t("处理中", "Working"))
    else {
        Box(Modifier.fillMaxWidth().height(3.dp).background(LocalMetro.current.controlStroke)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(LocalMetro.current.accent))
        }
        Label(labelPrefix + "${(fraction * 100).toInt()}%", 12, LocalMetro.current.muted)
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

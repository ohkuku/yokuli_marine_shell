package com.yokuli.marine.shell.rebuild.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.MainActivity
import com.yokuli.runtime.contract.RuntimeResidencyPhase
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Settings 与退出磁贴共用同一可审阅的关闭入口；Home/切 App 不进入此流程。 */
@Composable internal fun RuntimeExitSettings(os: OsStore) {
    val system = os.marine?.system ?: return
    val runtime by system.residency.state.collectAsState()
    val marine by system.services.state.collectAsState()
    val navigation by system.navigation.state.collectAsState()
    val traffic by system.ais.snapshot.collectAsState()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    fun saveAndExit() {
        val activity = context.exitActivity()
        (activity as? MainActivity)?.holdAutomaticResidencyForExit(true)
        stopping = true; failure = null
        os.scope.launch {
          try {
            val stopped = system.residency.state.value.let { it.explicitlyStopped && it.phase == RuntimeResidencyPhase.STOPPED }
            var result = if(stopped) com.yokuli.runtime.contract.RuntimeExitResult(true) else
                withTimeoutOrNull(30_000) { system.residency.exit() }
                    ?: com.yokuli.runtime.contract.RuntimeExitResult(false, "EXIT_CONFIRMATION_TIMEOUT")
            if (!result.completed) {
                val settled = withTimeoutOrNull(2_000) {
                    system.residency.state.first { it.phase == RuntimeResidencyPhase.STOPPED || it.phase == RuntimeResidencyPhase.BLOCKED }
                } ?: system.residency.state.value
                if (settled.explicitlyStopped && settled.phase == RuntimeResidencyPhase.STOPPED)
                    result = com.yokuli.runtime.contract.RuntimeExitResult(true)
            }
            if (result.completed) {
                // Core 已停就是退出完成；显示历史与通知仅作有界收尾，不能把用户困在退出页。
                os.save()
                withTimeoutOrNull(3_000) { os.hub.flushHistory() }
                withTimeoutOrNull(3_000) { os.notifications.resolvePositionAfterExit() }
                if (activity != null) activity.finishAndRemoveTask()
                else failure = os.t("采集已停止，但当前窗口无法关闭。请使用系统返回键。", "Collection is stopped, but this window could not close. Use the system Back control.")
            } else failure = if(result.problem == "EXIT_CONFIRMATION_TIMEOUT")
                os.t("系统停止仍在确认中。请查看当前状态后重试；不要重复开启任务。", "System stop is still awaiting confirmation. Review the current state and retry; do not restart tasks.")
            else result.problem
          } catch(cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
          } catch(error: Exception) {
            failure = os.t("退出尚未完成。已暂停的任务保持暂停，请重试。", "Exit has not finished. Tasks already paused stay paused. Please retry.")
          } finally { stopping = false }
        }
    }
    PageBody {
        Label(os.t("离开页面，数据继续。", "Leave a screen. Your data continues."), 22)
        Label(os.t("返回桌面或锁屏会继续采集。彻底退出后，手机传感器、定位、连接与共享全部停止；资料和来源设置保留。", "Home and screen lock keep collection running. Exiting stops phone sensors, position, connections and sharing. Saved data and source preferences stay."), 15, LocalMetro.current.muted)
        AppSection(os.t("退出时", "on exit"))
        if (marine.active?.paused == false) Label(os.t("守锚暂停 · 不再监测拖锚", "anchor watch pauses · no drag monitoring"), 17)
        if (marine.activeTrip?.paused == false) Label(os.t("航程先保存，再暂停记录", "recording is saved, then paused"), 17)
        if (navigation.session?.ongoing == true) Label(os.t("导航暂停 · 航线保留", "navigation pauses · route stays"), 17)
        if (traffic.preferences.monitoringEnabled) Label(os.t("AIS 警戒关闭", "AIS alerts turn off"), 17)
        if (runtime.inputConnections + runtime.outputConnections > 0) Label(os.t("断开 ${runtime.inputConnections + runtime.outputConnections} 个数据连接", "disconnect ${runtime.inputConnections + runtime.outputConnections} data connections"), 17)
        if (runtime.sharing) Label(os.t("停止数据共享", "stop data sharing"), 17)
        Label(os.t("再次打开 Yokuli 会恢复采集；任务、连接及共享由你重新开启。", "Opening Yokuli resumes collection. Restart tasks, connections and sharing when you choose."), 15, LocalMetro.current.muted)
        failure?.let { Label(os.t("尚未完全退出，已完成的暂停会保留。请重试。", "Exit is incomplete. Tasks already paused stay paused. Please retry."), 16); Label(it, 13, LocalMetro.current.muted) }
        if(runtime.explicitlyStopped && !stopping) {
            MetroButton(os.t("保存并退出", "save & exit"), ::saveAndExit, primary = true)
            MetroButton(os.t("重新开始采集", "resume collection"), {
                (context.exitActivity() as? MainActivity)?.holdAutomaticResidencyForExit(false)
                failure = null
                system.residency.startFromForeground()
            })
        }
        else MetroButton(if (stopping) os.t("正在保存并退出…", "saving and exiting…") else os.t("彻底退出", "exit completely"), { confirm = true }, primary = true, enabled = !stopping)
        if (runtime.phase == RuntimeResidencyPhase.BLOCKED && !stopping) Label(os.t("后台运行受限，请到权限与后台查看。", "Background operation is restricted. Check permissions & background."), 14, LocalMetro.current.muted)
    }
    if (confirm) ConfirmDialog(os, os.t("暂停保护任务，并停止所有采集、连接与共享？", "Pause protection and stop all collection, connections and sharing?"), { confirm = false }) {
        confirm = false
        saveAndExit()
    }
}
private tailrec fun Context.exitActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.exitActivity()
    else -> null
}

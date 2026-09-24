package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.Composable
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.*

/** 已未知的请求仍占据防重锁；只允许明确结束本次活动航行，不允许重新开始或切换暂停。 */
internal fun mayFinishVoyage(receipts: List<VoyageCommandReceipt>): Boolean = receipts.filterNot { it.terminal }.let { pending ->
    pending.isEmpty() || pending.all { it.status == VoyageRequestStatus.UNKNOWN && it.request.action != VoyageAction.FINISH }
}

@Composable internal fun VoyageCommandFeedback(os: OsStore, receipt: VoyageCommandReceipt?) {
    if(receipt == null) return
    val c=LocalMetro.current
    when(receipt.status) {
        VoyageRequestStatus.UNKNOWN -> {
            Label(os.t("上次记录操作的结果未确认，下面仍显示实际航行状态；不会自动重复发送。", "The previous recording action is unconfirmed. The actual voyage state remains below; the action will not be repeated automatically."), 16, c.accent)
            MetroButton(os.t("重新查询这次请求", "recheck this request"), { os.marine?.system?.voyage?.recheck(receipt.request.requestId) })
        }
        VoyageRequestStatus.QUEUED, VoyageRequestStatus.EXECUTING -> Label(os.t("正在处理记录操作…", "Processing the recording action…"), 16, c.muted)
        VoyageRequestStatus.REJECTED, VoyageRequestStatus.FAILED -> Label(when(receipt.reason) {
            "POSITION_REQUIRED" -> os.t("没有选用可记录的船位来源，请在数据中心选择。", "No usable position source is selected. Choose one in Data Center.")
            "SESSION_CHANGED", "SESSION_ENDED" -> os.t("这次航行的状态已改变，请以当前记录为准。", "This voyage has changed. Review the current recording.")
            "DEMO_ACTIVE" -> os.t("先关闭演示模式，再开始真实航行记录。", "Turn off demo mode before starting a real voyage recording.")
            "SUPERSEDED_BY_FINISH" -> os.t("之前未确认的操作已被本次结束记录取代。", "Finishing the recording superseded the previously unconfirmed action.")
            else -> os.t("记录操作未完成；已保存的记录仍保留，请检查当前状态后再操作。", "The recording action did not complete. Saved records are retained; review the current state before acting again.")
        }, 16, c.accent)
        VoyageRequestStatus.CONFIRMED -> Unit
    }
}

package com.yokuli.runtime.marine.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yokuli.runtime.contract.notification.*
import kotlinx.coroutines.*

/** 兼容底层恢复入口；非导出、固定发布身份和目的地，不接受任意通知文本或路由。 */
class RuntimeRecoveryNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.yokuli.RUNTIME_RECOVERY_NOTICE") return
        val failed = intent.getBooleanExtra("failed", false)
        val chinese = intent.getBooleanExtra("chinese", false)
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val record = NoticeRecord("system:boot-recovery", "SETTINGS", NoticeText(
                    if(failed) "后台任务需要检查" else "设备已重启",
                    if(failed) "Review background tasks" else "Device restarted",
                    if(failed) "后台任务恢复未完成，请查看后台运行状态。" else "值守和记录被重启中断。锚点和航程已保留，请核对船位后继续。",
                    if(failed) "Background recovery is incomplete. Review background status." else "Restart interrupted monitoring and recording. Your anchor and voyage are retained; check position before continuing.",
                    "runtime.device_restart"), System.currentTimeMillis(), level = NoticeLevel.WARNING,
                    target = NoticeTarget("settings", section = "permissions"), aggregationKey = "system:boot-recovery",
                    presentationLanguage = if(chinese) "zh-CN" else "en")
                // 客户端拥有实际事务，等待结束不会取消已经送出的持久写入。
                withTimeoutOrNull(8500) {
                    BinderNotificationClient.shared(context).execute(NoticeCommand(java.util.UUID.randomUUID().toString(),
                        NoticeOperation.PUBLISH, record, publishMode = NoticePublishMode.OCCURRENCE))
                }
            } finally { pending.finish(); scope.cancel() }
        }
    }
}

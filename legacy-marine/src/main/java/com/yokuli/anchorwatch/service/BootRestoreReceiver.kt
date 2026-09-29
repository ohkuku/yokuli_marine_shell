package com.yokuli.anchorwatch.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yokuli.anchorwatch.data.database.AnchorDao
import com.yokuli.anchorwatch.data.database.TripDao
import com.yokuli.anchorwatch.data.database.SonarDao
import com.yokuli.anchorwatch.data.preferences.SettingsRepository
import com.yokuli.anchorwatch.runtime.MarineRecoveryBarrier
import com.yokuli.anchorwatch.localization.usesChinese
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Receiver 只唤醒统一恢复屏障和呈现提示；不能自己重放开始守锚/记录/连接。 */
@AndroidEntryPoint
class BootRestoreReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: AnchorDao
    @Inject lateinit var trips: TripDao
    @Inject lateinit var sonarDao: SonarDao
    @Inject lateinit var preferences: SettingsRepository
    @Inject lateinit var recovery: MarineRecoveryBarrier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = recovery.ensureRecovered()
                val settings = preferences.settings.first()
                val sonar = sonarDao.active()
                if (sonar != null) { sonarDao.refreshSampleCount(sonar.id); sonarDao.finish(sonar.id, System.currentTimeMillis()) }
                if (settings.mockEnabled) preferences.save(settings.copy(mockEnabled = false))
                if (result.deviceRestarted && (dao.active() != null || trips.active() != null || sonar != null || settings.mockEnabled))
                    notifyRecovery(context, false, settings.appLanguage.usesChinese())
            } catch (error: Exception) {
                notifyRecovery(context, true, runCatching { preferences.settings.first().appLanguage.usesChinese() }.getOrDefault(false))
            } finally { pending.finish() }
        }
    }

    private fun notifyRecovery(context: Context, failed: Boolean, chinese: Boolean) {
        // 通知实现位于上层 runtime；显式同包接收器转入唯一持久消息服务，避免第二份 Android 卡片。
        context.sendBroadcast(Intent("com.yokuli.RUNTIME_RECOVERY_NOTICE")
            .setComponent(android.content.ComponentName(context.packageName,
                "com.yokuli.runtime.marine.notification.RuntimeRecoveryNoticeReceiver"))
            .putExtra("failed", failed).putExtra("chinese", chinese))
    }
}

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
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, if(chinese) "运行恢复" else "Runtime recovery", NotificationManager.IMPORTANCE_HIGH))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, ID, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = if(failed) {
            if(chinese) "恢复未完成，保护与记录尚未启动。打开 Yokuli 查看并处理。" else "Recovery is incomplete. Protection and recording have not restarted. Open Yokuli to resolve it."
        } else {
            if(chinese) "设备重启中断了值守或记录。锚点和航程已保留，请核对实时船位后继续。" else "Restart interrupted protection or recording. Your anchor and voyage are retained; check live position before continuing."
        }
        runCatching { manager.notify(ID, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if(chinese) "Yokuli 需要确认" else "Yokuli needs confirmation")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open)
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()) }
    }
    companion object { const val CHANNEL = "monitor_restore"; const val ID = 47 }
}

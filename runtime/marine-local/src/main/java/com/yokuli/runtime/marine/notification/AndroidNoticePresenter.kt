package com.yokuli.runtime.marine.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.yokuli.runtime.contract.notification.*
import kotlinx.coroutines.*

/** Android 是同一份已落盘消息的另一种呈现，不持有第二份消息/警报状态。 */
internal class AndroidNoticePresenter(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var previous = emptyMap<String, NoticeRecord>()
    private var initialized = false
    private var channelsCreated = false
    @Volatile var needsRetry = false
        private set
    private fun ensureChannels() {
        if(channelsCreated) return
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        listOf(
            NotificationChannel(MESSAGES, "Yokuli · Messages", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(ATTENTION, "Yokuli · Attention", NotificationManager.IMPORTANCE_HIGH),
        ).forEach { channel ->
            channel.description = "Yokuli OS notification centre"
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
            channel.enableVibration(channel.id == ATTENTION)
            // 只创建一次；系统会保留用户对频道声音、振动和重要程度的设置。
            manager.createNotificationChannel(channel)
        }
        channelsCreated = true
    }
    /** 平台呈现失败不能中断唯一历史服务的订阅；后续只重试同一快照，不重新发布消息。 */
    @Synchronized fun present(snapshot: NotificationSnapshot) {
        if(snapshot.epoch.isBlank() || snapshot.persistenceFailure != null) return
        needsRetry = runCatching { presentSnapshot(snapshot) }.isFailure
    }
    private fun presentSnapshot(snapshot: NotificationSnapshot) {
        ensureChannels()
        // 用户之后授予权限时重试呈现；历史不因通知权限被拒绝而丢失。
        check(manager.areNotificationsEnabled()) { "NOTIFICATIONS_DISABLED" }
        val now = System.currentTimeMillis()
        val records = snapshot.records.associateBy { it.id }
        manager.activeNotifications.filter { it.tag?.startsWith(TAG) == true }.forEach { active ->
            val tag = active.tag ?: return@forEach
            val record = records[tag.removePrefix(TAG)]
            if(record == null || record.read && record.dismissible) manager.cancel(active.tag, ID)
        }
        records.values.forEach { record ->
            val old = previous[record.id]
            val changed = old == null || old.updatedAtUtcMillis != record.updatedAtUtcMillis
            val fresh = now - record.updatedAtUtcMillis in 0..30_000
            if ((!record.read || !record.dismissible) && changed && (initialized || fresh || !record.dismissible)) {
                show(record, silent = !initialized || !fresh || record.level == NoticeLevel.ALARM && record.category in setOf("anchor", "traffic"))
                // 后面的平台调用失败时，也不让已经成功呈现的消息再次响铃。
                previous = previous + (record.id to record)
            }
        }
        previous = records
        initialized = true
    }
    private fun show(record: NoticeRecord, silent: Boolean) {
        val zh = record.presentationLanguage?.startsWith("zh") ?: (context.resources.configuration.locales[0]?.language == "zh")
        val title = (if(zh)record.text.titleZh else record.text.titleEn).ifBlank { "Yokuli OS" }
        val body = if(zh)record.text.bodyZh else record.text.bodyEn
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "com.yokuli.NOTICE.${record.id}"
            putExtra(NOTICE_ID, record.id)
        }
        val content = launch?.let { PendingIntent.getActivity(context, record.id.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val dismiss = Intent(context, NoticeDismissReceiver::class.java).apply {
            action="com.yokuli.DISMISS.${record.id}"
            putExtra(NOTICE_ID,record.id)
        }
        val notification = NotificationCompat.Builder(context, if(record.level==NoticeLevel.INFO)MESSAGES else ATTENTION)
            .setSmallIcon(com.yokuli.runtime.marine.R.drawable.ic_yokuli_notice)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(content).setWhen(record.updatedAtUtcMillis).setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(record.dismissible).setOngoing(!record.dismissible)
            .setOnlyAlertOnce(false).setSilent(silent)
            .setCategory(if(record.dismissible)NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_ERROR)
            .apply { if(record.dismissible)setDeleteIntent(PendingIntent.getBroadcast(context,record.id.hashCode(),dismiss,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
            .build()
        // 频道仍尊重用户静音；异常由外层隔离并重试呈现，绝不阻断内部历史。
        manager.notify(TAG+record.id,ID,notification)
    }
    companion object {
        const val NOTICE_ID="yokuli.notice.id"
        const val MESSAGES="yokuli.messages.v1"
        const val ATTENTION="yokuli.attention.v1"
        private const val TAG="yokuli.notice:"
        private const val ID=5101
    }
}

/** Android 上划走消息也通过唯一消息服务提交；不确认/停止领域警报。 */
class NoticeDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id=intent.getStringExtra(AndroidNoticePresenter.NOTICE_ID)?.takeIf { it.length in 1..256 } ?: return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try { withTimeoutOrNull(8000) {
                BinderNotificationClient.shared(context).execute(NoticeCommand(java.util.UUID.randomUUID().toString(),NoticeOperation.REMOVE,noticeId=id))
            }} finally { pending.finish(); cancel() }
        }
    }
}

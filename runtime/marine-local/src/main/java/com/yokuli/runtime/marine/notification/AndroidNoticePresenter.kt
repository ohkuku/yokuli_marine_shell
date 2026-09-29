package com.yokuli.runtime.marine.notification

import com.yokuli.runtime.contract.time.MarineTime

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
    @Volatile private var foreground = false
    private var preferredLanguage: String? = null
    private var tone: android.media.Ringtone? = null
    private var lastSoundElapsed = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Synchronized fun setForeground(value: Boolean, language: String?, snapshot: NotificationSnapshot) {
        val changed = foreground != value
        foreground = value
        if (!language.isNullOrBlank() && preferredLanguage != language) {
            preferredLanguage = language
            channelsCreated = false
        }
        if (value) manager.activeNotifications.filter { it.tag?.startsWith(TAG) == true }
            .forEach { manager.cancel(it.tag, ID) }
        else if (changed) {
            // 未解决问题离开应用后仍可见，已经看过的普通消息不重新刷回系统栏。
            snapshot.records.filter { !it.dismissible }.forEach { runCatching { show(it, silent = true) } }
        }
        if (changed) { tone?.stop(); tone = null }
    }
    @Synchronized fun close() { handler.removeCallbacksAndMessages(null); tone?.stop(); tone = null }
    private fun playSound(record: NoticeRecord) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSoundElapsed < 2000L || manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        val channel = manager.getNotificationChannel(if(record.level == NoticeLevel.INFO) MESSAGES else ATTENTION) ?: return
        if(channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        val uri = channel.sound ?: return
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        if(audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return
        lastSoundElapsed = now
        handler.post {
            if (!foreground) return@post
            runCatching {
                tone?.stop()
                tone = RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = channel.audioAttributes ?: AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                    play()
                }
                val current = tone
                handler.postDelayed({ if (tone === current) { current?.stop(); tone = null } }, 3500)
            }
        }
    }
    @Volatile var needsRetry = false
        private set
    private fun ensureChannels() {
        if(channelsCreated) return
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val zh = preferredLanguage?.startsWith("zh") ?: (context.resources.configuration.locales[0]?.language == "zh")
        listOf(
            NotificationChannel(MESSAGES, if(zh) "Yokuli · 消息" else "Yokuli · Messages", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(ATTENTION, if(zh) "Yokuli · 需要处理" else "Yokuli · Attention", NotificationManager.IMPORTANCE_HIGH),
        ).forEach { channel ->
            channel.description = if(zh) "Yokuli OS 通知中心" else "Yokuli OS notification centre"
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
            channel.enableVibration(channel.id == ATTENTION)
            // 只创建一次；系统会保留用户对频道声音、振动和重要程度的设置。
            manager.createNotificationChannel(channel)
        }
        // 清理旧的第二条通知链残留；前台服务所需的常驻通知不受影响。
        listOf(43, 44, 45, 47).forEach(manager::cancel)
        manager.activeNotifications.filter { it.id == 141 && it.tag?.startsWith("ais:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
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
        if (!foreground) check(manager.areNotificationsEnabled()) { "NOTIFICATIONS_DISABLED" }
        val now = MarineTime.nowUtcMillis()
        val records = snapshot.records.associateBy { it.id }
        manager.activeNotifications.filter { it.tag?.startsWith(TAG) == true }.forEach { active ->
            val tag = active.tag ?: return@forEach
            val record = records[tag.removePrefix(TAG)]
            if(foreground || record == null || record.read) manager.cancel(active.tag, ID)
        }
        records.values.forEach { record ->
            val old = previous[record.id]
            val changed = old == null || old.updatedAtUtcMillis != record.updatedAtUtcMillis
            val fresh = now - record.updatedAtUtcMillis in 0..30_000
            if ((!record.read || !record.dismissible) && changed && (initialized || fresh || !record.dismissible)) {
                val silent = !initialized || !fresh || record.category == "anchor" && record.level == NoticeLevel.ALARM || record.text.arguments["sound"] == "false"
                if (foreground) { if (!silent) playSound(record) }
                else show(record, silent)
                // 后面的平台调用失败时，也不让已经成功呈现的消息再次响铃。
                previous = previous + (record.id to record)
            }
        }
        previous = records
        initialized = true
    }
    private fun show(record: NoticeRecord, silent: Boolean) {
        ensureChannels()
        val zh = (preferredLanguage ?: record.presentationLanguage)?.startsWith("zh") ?: (context.resources.configuration.locales[0]?.language == "zh")
        val title = (if (com.yokuli.runtime.marine.hardware.HardwareLabBoot.current.mode != com.yokuli.runtime.contract.hardware.HardwareMode.REAL) { if (zh) "演练 · " else "Practice · " } else "") + (if(zh)record.text.titleZh else record.text.titleEn).ifBlank { "Yokuli OS" }
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

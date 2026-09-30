package com.yokuli.anchorwatch.runtime.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.yokuli.anchorwatch.service.AnchorForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns every notification channel and notification-manager mutation used by the runtime. */
@Singleton
class NotificationCoordinator @Inject constructor(@ApplicationContext private val context:Context){
    private val manager get()=context.getSystemService(NotificationManager::class.java)
    private val unitFormatsState = MutableStateFlow(NotificationUnitFormats.CANONICAL)
    val unitFormats = unitFormatsState.asStateFlow()
    // 图册等独立 Core 领域只提供展示文本；任务及其生命周期仍由原服务拥有。
    private data class ForegroundContent(val text:String,val alarm:Boolean,val silent:Boolean,val title:String,val snoozeLabel:String)
    private var lastForeground:ForegroundContent? = null
    private var taskLines:List<String> = emptyList()
    @Synchronized fun setTaskLines(lines:List<String>) {
        val next=lines.map { it.take(300) }.distinct().take(8)
        if(next==taskLines)return
        taskLines=next
        val previous=lastForeground ?: return
        // 已显式退出或服务被撤销后，异步进度不得重新创建一个常驻通知。
        if(runCatching {manager.activeNotifications.any {it.id==ONGOING_ID}}.getOrDefault(false)) {
            runCatching {manager.notify(ONGOING_ID,buildForeground(previous))}
        }
    }
    fun installUnitFormats(formats: NotificationUnitFormats) { unitFormatsState.value = formats }


    fun createChannels(statusName:String,eventName:String,alarmName:String){
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(STATUS_CHANNEL,statusName,NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(EVENT_CHANNEL,eventName,NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(ALARM_CHANNEL,alarmName,NotificationManager.IMPORTANCE_HIGH).apply{
                    setSound(null,null)
                    enableVibration(false)
                },
            ),
        )
    }

    fun foregroundNotification(
        text:String,
        alarm:Boolean,
        silent:Boolean=false,
        title:String,
        snoozeLabel:String,
    ):Notification = synchronized(this) {
        ForegroundContent(text,alarm,silent,title,snoozeLabel).also {lastForeground=it}.let(::buildForeground)
    }

    private fun buildForeground(content:ForegroundContent):Notification {
        val (text,alarm,silent,title,snoozeLabel)=content
        val body=(listOf(text)+taskLines).distinct().joinToString("\n")
        val summary=if(alarm||taskLines.isEmpty())text else taskLines.first()
        val open=PendingIntent.getActivity(
            context,
            0,
            (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setAction("com.yokuli.RUNTIME_TASKS")
                .putExtra("yokuli.runtime.destination", "notifications"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze=PendingIntent.getService(
            context,
            1,
            Intent(context,AnchorForegroundService::class.java).setAction(AnchorForegroundService.SNOOZE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context,if(alarm)ALARM_CHANNEL else STATUS_CHANNEL)
            .setSmallIcon(com.yokuli.anchorwatch.R.drawable.ic_yokuli_notice)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent || !alarm)
            .setShowWhen(false)
            .setPriority(if(alarm)NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .setCategory(if(alarm)NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_SERVICE)
            .apply{if(alarm){addAction(0,snoozeLabel,snooze)}}
            .build()
    }

    fun publishForeground(notification:Notification)=manager.notify(ONGOING_ID,notification)

    fun publishEvent(title:String,text:String,high:Boolean,notificationId:Int=EVENT_ID){
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context,if(high)EVENT_CHANNEL else STATUS_CHANNEL)
                .setSmallIcon(com.yokuli.anchorwatch.R.drawable.ic_yokuli_notice)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(if(high)NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    fun cancelEvent(notificationId:Int=EVENT_ID)=manager.cancel(notificationId)

    /** AIS 使用自己的通道与 ID；取消交通提示绝不会撤销走锚警报。 */
    fun createAisChannels(chinese:Boolean) {
        manager.createNotificationChannels(listOf(
            NotificationChannel(AIS_STATUS_CHANNEL,if(chinese)"AIS 监控" else "AIS monitoring",NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(AIS_SILENT_CHANNEL,if(chinese)"AIS 交通提示 · 静音" else "AIS traffic · quiet",NotificationManager.IMPORTANCE_HIGH).apply{setSound(null,null);enableVibration(false)},
            NotificationChannel(AIS_SOUND_CHANNEL,if(chinese)"AIS 交通提示 · 声音" else "AIS traffic · sound",NotificationManager.IMPORTANCE_HIGH).apply{
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            },
        ))
    }

    fun aisForegroundNotification(text:String,chinese:Boolean):Notification = NotificationCompat.Builder(context,AIS_STATUS_CHANNEL)
        .setSmallIcon(com.yokuli.anchorwatch.R.drawable.ic_yokuli_notice)
        .setContentTitle(if(chinese)"AIS · 周围船舶" else "AIS · nearby traffic")
        .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(aisIntent(null)).setOngoing(true).setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_LOW).build()

    fun publishAisForeground(notification:Notification)=manager.notify(AIS_ONGOING_ID,notification)

    fun publishAisEvent(eventId:String,mmsi:Int,title:String,text:String,sound:Boolean) {
        manager.notify("ais:$eventId",AIS_EVENT_ID,NotificationCompat.Builder(context,if(sound)AIS_SOUND_CHANNEL else AIS_SILENT_CHANNEL)
            .setSmallIcon(com.yokuli.anchorwatch.R.drawable.ic_yokuli_notice).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(aisIntent(mmsi))
            .setAutoCancel(true).setSilent(!sound).setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }

    fun cancelAisEvent(eventId:String)=manager.cancel("ais:$eventId",AIS_EVENT_ID)

    fun aisNotificationAllowed(sound:Boolean=false):Boolean = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        manager.getNotificationChannel(if(sound)AIS_SOUND_CHANNEL else AIS_SILENT_CHANNEL)?.importance?.let{it!=NotificationManager.IMPORTANCE_NONE}==true

    fun aisSoundAllowed():Boolean = aisNotificationAllowed(true) &&
        manager.getNotificationChannel(AIS_SOUND_CHANNEL)?.let{it.importance>=NotificationManager.IMPORTANCE_DEFAULT&&it.sound!=null}==true &&
        manager.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_ALL

    private fun aisIntent(mmsi:Int?):PendingIntent {
        val intent=(context.packageManager.getLaunchIntentForPackage(context.packageName)?:Intent()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(if(mmsi==null)"com.yokuli.AIS" else "com.yokuli.AIS.$mmsi")
            .putExtra("yokuli.ais.route",if(mmsi==null)"ais" else "ais:target:$mmsi")
        if(mmsi!=null)intent.putExtra("yokuli.ais.target",mmsi)
        return PendingIntent.getActivity(context,mmsi?:0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object{
        const val STATUS_CHANNEL="anchor_status"
        const val EVENT_CHANNEL="anchor_events_v2"
        const val ALARM_CHANNEL="anchor_alarm_selectable_v2"
        const val ONGOING_ID=42
        const val EVENT_ID=43
        const val DEPTH_DATA_EVENT_ID=44
        const val WIND_DATA_EVENT_ID=45
        const val AIS_STATUS_CHANNEL="ais_status_v1"
        const val AIS_SILENT_CHANNEL="ais_traffic_quiet_v1"
        const val AIS_SOUND_CHANNEL="ais_traffic_sound_v1"
        const val AIS_ONGOING_ID=140
        const val AIS_EVENT_ID=141
    }
}

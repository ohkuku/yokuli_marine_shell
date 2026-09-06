package com.yokuli.marine.data.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.yokuli.marine.data.android.R
import com.yokuli.marine.data.android.runtime.MarineDataRuntimeOwner
import com.yokuli.marine.data.phone.PhoneLocationCommand
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Visibility and explicit stop path for user-enabled system location. */
class PhoneLocationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runtime: PhoneLocationRuntimePort by lazy {
        (application as? MarineDataRuntimeOwner)?.phoneLocationRuntime
            ?: error("Application must implement MarineDataRuntimeOwner")
    }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.phone_location_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.phone_location_notification_channel_description)
                setShowBadge(false)
            },
        )
        startForeground(NOTIFICATION_ID, notification())
        serviceScope.launch {
            runtime.state.collectLatest { snapshot ->
                if (!snapshot.enabledByUser) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            serviceScope.launch { runtime.execute(PhoneLocationCommand.Disable) }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val disable = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, PhoneLocationForegroundService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                OPEN_REQUEST_CODE,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nmea)
            .setContentTitle(getString(R.string.phone_location_notification_title))
            .setContentText(getString(R.string.phone_location_notification_text))
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.phone_location_notification_stop),
                    disable,
                ).build(),
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "yokuli_phone_location"
        private const val NOTIFICATION_ID = 7_211
        private const val STOP_REQUEST_CODE = 7_212
        private const val OPEN_REQUEST_CODE = 7_213
        private const val ACTION_DISABLE = "com.yokuli.marine.data.android.DISABLE_PHONE_LOCATION"
    }
}

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
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Process-level visibility and stop path for user-enabled network inputs.
 *
 * The service never owns sockets. The Application-owned runtime survives feature navigation and
 * this service only reflects its durable run intent.
 */
class NmeaInputForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runtime: NmeaInputRuntimePort by lazy {
        (application as? MarineDataRuntimeOwner)?.nmeaInputRuntime
            ?: error("Application must implement MarineDataRuntimeOwner")
    }
    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }
    private var stoppedAfterAllInputsDisabled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Android requires foreground promotion immediately after startForegroundService().
        val enabledAtStart = runtime.state.value.connections.count {
            it.stored.runIntent == ConnectionRunIntent.ENABLED
        }
        startForeground(NOTIFICATION_ID, notification(enabledConnectionCount = enabledAtStart))
        serviceScope.launch {
            runtime.state.collectLatest { snapshot ->
                val enabledCount = snapshot.connections.count {
                    it.stored.runIntent == ConnectionRunIntent.ENABLED
                }
                if (enabledCount == 0) {
                    stoppedAfterAllInputsDisabled = true
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification(enabledCount))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            serviceScope.launch {
                runtime.state.value.connections
                    .filter { it.stored.runIntent == ConnectionRunIntent.ENABLED }
                    .forEach { runtime.execute(NmeaRuntimeCommand.Stop(it.stored.config.id)) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val enabledInputsRemain = runtime.state.value.connections.any {
            it.stored.runIntent == ConnectionRunIntent.ENABLED
        }
        if (!stoppedAfterAllInputsDisabled && enabledInputsRemain) {
            (runtime as? ForegroundServiceLossListener)?.onForegroundServiceLost()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.nmea_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.nmea_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun notification(enabledConnectionCount: Int): Notification {
        val stopIntent = Intent(this, NmeaInputForegroundService::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentPendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                OPEN_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nmea)
            .setContentTitle(getString(R.string.nmea_notification_title))
            .setContentText(getString(R.string.nmea_notification_enabled_count, enabledConnectionCount))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.nmea_notification_stop_all),
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "yokuli_nmea_input"
        private const val NOTIFICATION_ID = 7_201
        private const val STOP_REQUEST_CODE = 7_202
        private const val OPEN_REQUEST_CODE = 7_203
        private const val ACTION_STOP_ALL = "com.yokuli.marine.data.android.STOP_ALL_NMEA_INPUTS"
    }
}

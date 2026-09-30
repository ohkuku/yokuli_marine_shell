package com.yokuli.runtime.marine.navigation

import com.yokuli.runtime.contract.hardware.MarineDeviceBus
import com.yokuli.runtime.contract.device.DeviceBackend
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** 执行导航的Android容器；只有用户提交/继续导航后才启动，恢复磁盘不偷偷获得后台定位。 */
@AndroidEntryPoint
class NavigationTrackingService : Service() {
    @Inject lateinit var navigation: LocalNavigationSessionService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var failure: String? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!navigation.activeSession()) { stopSelf(); return START_NOT_STICKY }
        if (!start()) return START_NOT_STICKY
        if (!observing) {
            observing = true
            scope.launch {
                combine(navigation.state,navigation.locationRequested) { state,location -> Triple(state.session?.phase,location,state.session?.targetIndex) }
                    .distinctUntilChanged().collect { if (navigation.activeSession()) start() else stopSelf() }
            }
        }
        return START_NOT_STICKY
    }
    private fun start(): Boolean {
        val chinese = navigation.chinese()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, if (chinese) "导航" else "Navigation", NotificationManager.IMPORTANCE_LOW))
        val session = navigation.state.value.session
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, ID, it
                .setAction("com.yokuli.NAVIGATION_TASK")
                .putExtra("yokuli.runtime.destination", "chart")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.yokuli.runtime.marine.R.drawable.ic_yokuli_notice)
            .setContentTitle(if(chinese) "海图 · 导航中" else "Chart · Navigating")
            .setContentText(session?.route?.name?.takeIf { it.isNotBlank() } ?: if (chinese) "正在跟随船载设备目标" else "Following the onboard target")
            .setContentIntent(launch).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false).setCategory(NotificationCompat.CATEGORY_NAVIGATION).build()
        val virtual=MarineDeviceBus.state.value.backend!=DeviceBackend.REAL
        var location = navigation.needsPhoneLocation()&&!virtual
        fun promote() = ServiceCompat.startForeground(this, ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                (if (location) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) else 0)
        var result = runCatching { promote() }
        if (result.isFailure && location) { location = false; result = runCatching { promote() } }
        if (result.isFailure) {
            failure = "ANDROID_BACKGROUND_START_DENIED"
            navigation.foreground(false, failure, false)
            stopSelf(); return false
        }
        failure = if (navigation.needsPhoneLocation() && !location && !virtual) "BACKGROUND_LOCATION_NOT_ALLOWED" else null
        navigation.foreground(true, failure, location||virtual&&navigation.needsPhoneLocation())
        return true
    }
    override fun onDestroy() {
        scope.cancel()
        navigation.foreground(false, if (navigation.activeSession()) failure ?: "BACKGROUND_NAVIGATION_STOPPED" else null, false)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private companion object { const val CHANNEL = "navigation_session"; const val ID = 7193 }
}

package com.yokuli.runtime.marine.ais

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.yokuli.anchorwatch.runtime.notification.NotificationCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 系统监控的 Android 容器；关闭 AIS 页面不释放它，也不启用任何未获准的连接。 */
@AndroidEntryPoint
class AisMonitoringService : Service() {
    @Inject lateinit var traffic: LocalAisTrafficService
    @Inject lateinit var notifications: NotificationCoordinator
    private var startFailure:String?=null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var location=traffic.wantsLocation()
        fun start(locationType:Boolean) {
            notifications.createAisChannels(traffic.chinese())
            ServiceCompat.startForeground(this,NotificationCoordinator.AIS_ONGOING_ID,
                notifications.aisForegroundNotification(if(traffic.chinese())"正在恢复 AIS 监控" else "Restoring AIS monitoring",traffic.chinese()),
                if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    (if(locationType)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) else 0)
        }
        var success=runCatching { start(location) }
        if(success.isFailure && location) {
            // Android may allow a resumed network monitor but deny while-in-use
            // GPS after process restart. Keep the accepted input alive and
            // report the unavailable location capability in the shared state.
            location=false
            success=runCatching { start(false) }
        }
        if(success.isFailure) {
            startFailure="Android could not start AIS foreground monitoring: ${success.exceptionOrNull()?.javaClass?.simpleName}"
            traffic.foregroundChanged(false,startFailure)
            stopSelf()
            return START_NOT_STICKY
        }
        startFailure=null
        traffic.foregroundChanged(true,location=location)
        return START_STICKY
    }

    override fun onDestroy() {
        traffic.foregroundChanged(false,if(traffic.monitoringRequested())startFailure?:"Android stopped AIS background monitoring; open AIS to restore it" else null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder?=null
}

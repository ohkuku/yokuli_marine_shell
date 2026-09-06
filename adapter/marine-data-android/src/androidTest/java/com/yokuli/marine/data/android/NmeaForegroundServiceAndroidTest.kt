package com.yokuli.marine.data.android

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.data.android.service.NmeaInputForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NmeaForegroundServiceAndroidTest {
    @Test
    fun mergedServiceIsPrivateConnectedDeviceAndHasItsRequiredPermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, NmeaInputForegroundService::class.java),
            0,
        )
        @Suppress("DEPRECATION")
        val requestedPermissions = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toSet()

        assertFalse(service.exported)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        assertTrue("android.permission.FOREGROUND_SERVICE" in requestedPermissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" in requestedPermissions)
        assertTrue("android.permission.CHANGE_NETWORK_STATE" in requestedPermissions)
        assertFalse("android.permission.ACCESS_FINE_LOCATION" in requestedPermissions)
        assertFalse("android.permission.ACCESS_COARSE_LOCATION" in requestedPermissions)
    }
}

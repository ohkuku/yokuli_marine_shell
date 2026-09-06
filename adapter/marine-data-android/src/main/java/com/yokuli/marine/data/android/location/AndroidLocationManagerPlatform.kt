package com.yokuli.marine.data.android.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PlatformLocationFix

/** Uses Android's system providers and names their actual provider; it does not claim GNSS purity. */
class AndroidLocationManagerPlatform(context: Context) : PhoneLocationPlatform {
    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    override fun permission(): PhoneLocationPermission = when {
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED -> PhoneLocationPermission.PRECISE
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED -> PhoneLocationPermission.APPROXIMATE
        else -> PhoneLocationPermission.DENIED
    }

    override fun isSystemLocationEnabled(): Boolean = locationManager.isLocationEnabled

    override fun start(
        onFix: (PlatformLocationFix) -> Unit,
        onProviderChanged: () -> Unit,
    ) {
        stop()
        val callback = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val fix = runCatching {
                    PlatformLocationFix(
                        latitudeDegrees = location.latitude,
                        longitudeDegrees = location.longitude,
                        speedMetersPerSecond = location.speed.toDouble().takeIf { location.hasSpeed() },
                        bearingDegrees = location.bearing.toDouble().takeIf { location.hasBearing() },
                        horizontalAccuracyMeters = location.accuracy.toDouble().takeIf { location.hasAccuracy() },
                        sourceTimeEpochMillis = location.time.takeIf { it >= 0L },
                        provider = location.provider ?: "system",
                    )
                }.getOrNull() ?: return
                onFix(fix)
            }

            override fun onProviderEnabled(provider: String) = onProviderChanged()
            override fun onProviderDisabled(provider: String) = onProviderChanged()
            @Deprecated("Legacy callback retained for minSdk 26")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        val allowedProviders = locationManager.getProviders(true).filter {
            it == LocationManager.GPS_PROVIDER ||
                it == LocationManager.NETWORK_PROVIDER ||
                (android.os.Build.VERSION.SDK_INT >= 31 && it == LocationManager.FUSED_PROVIDER)
        }
        require(allowedProviders.isNotEmpty()) { "No enabled Android location provider" }
        allowedProviders.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                MIN_UPDATE_MILLIS,
                MIN_UPDATE_METERS,
                callback,
                Looper.getMainLooper(),
            )
        }
        listener = callback
    }

    override fun stop() {
        listener?.let(locationManager::removeUpdates)
        listener = null
    }

    private companion object {
        const val MIN_UPDATE_MILLIS = 1_000L
        const val MIN_UPDATE_METERS = 0f
    }
}

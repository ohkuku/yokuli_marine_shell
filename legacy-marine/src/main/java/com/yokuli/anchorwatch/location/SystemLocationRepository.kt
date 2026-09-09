package com.yokuli.anchorwatch.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.yokuli.anchorwatch.BuildConfig
import com.yokuli.anchorwatch.domain.model.NavigationFix
import com.yokuli.anchorwatch.domain.model.PositionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PhoneLocationPhase { OFF, PERMISSION_REQUIRED, PROVIDER_DISABLED, LISTENING, ERROR }
data class PhoneLocationStatus(
    val phase:PhoneLocationPhase=PhoneLocationPhase.OFF,
    val lastFixElapsedRealtime:Long?=null,
    val error:String?=null,
)

@Singleton
class SystemLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    settings:com.yokuli.anchorwatch.data.preferences.SettingsRepository,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val guard = Any()
    private val _fix = MutableStateFlow<NavigationFix?>(null)
    val fix = _fix.asStateFlow()
    private val _recentFixes = MutableStateFlow<List<NavigationFix>>(emptyList())
    val recentFixes = _recentFixes.asStateFlow()
    private var appEnabled = false
    private var previewEnabled = false
    private var backgroundEnabled = false
    @Volatile private var sourcePermitsPhone=false
    private val _sourceConsent=MutableStateFlow(false);val sourceConsent=_sourceConsent.asStateFlow()
    private var running = false
    private val _status=MutableStateFlow(PhoneLocationStatus());val status=_status.asStateFlow()
    private val listener = object:LocationListener {
        override fun onLocationChanged(location:Location){publish(location)}
        @Deprecated("Required for LocationListener compatibility on Android 9–10")
        override fun onStatusChanged(provider:String?,status:Int,extras:android.os.Bundle?)=Unit
        override fun onProviderEnabled(provider:String){if(provider==LocationManager.GPS_PROVIDER)synchronized(guard){reconcileLocked()}}
        override fun onProviderDisabled(provider:String){if(provider==LocationManager.GPS_PROVIDER)synchronized(guard){_fix.value=null;reconcileLocked()}}
    }
    init{
        ContextCompat.registerReceiver(context,object:BroadcastReceiver(){
            override fun onReceive(context:Context?,intent:Intent?){synchronized(guard){reconcileLocked()}}
        },IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),ContextCompat.RECEIVER_NOT_EXPORTED)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Default).launch{
            settings.settings.collect{value->synchronized(guard){
                sourcePermitsPhone=value.gpsDataSource in setOf(com.yokuli.anchorwatch.domain.model.GpsDataSource.SYSTEM,com.yokuli.anchorwatch.domain.model.GpsDataSource.DEMO)
                _sourceConsent.value=sourcePermitsPhone;reconcileLocked()
            }}
        }
    }

    private fun publish(location: Location) = synchronized(guard) {
        // A switch away from the NMEA proxy must be backed by a real system
        // position, never by the app's own mock location fed back to itself.
        if (!sourcePermitsPhone || !(appEnabled||backgroundEnabled) || LocationCompat.isMock(location) || location.provider!=LocationManager.GPS_PROVIDER)return@synchronized
        val now=SystemClock.elapsedRealtime()
        val received = location.elapsedRealtimeNanos.takeIf { it > 0 }?.div(1_000_000) ?: now
        // Last-known callbacks and NETWORK fixes can arrive after newer GNSS.
        // Never let arrival order rewind the selected observation's timestamp.
        if(received>now||_fix.value?.receivedElapsedRealtime?.let{received<=it}==true)return@synchronized
        val value = NavigationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            timestampUtcMillis = location.time,
            receivedElapsedRealtime = received,
            sogKnots = location.speed.takeIf { location.hasSpeed() }?.times(1.943844),
            cogTrueDegrees = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
            // Android bearing is course over ground. It is not bow heading,
            // especially at anchor where tiny GPS motion makes it unstable.
            headingTrueDegrees = null,
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            horizontalAccuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble(),
            positionProvider = if (location.provider == LocationManager.GPS_PROVIDER) {
                PositionProvider.ANDROID_GNSS
            } else {
                PositionProvider.ANDROID_NETWORK
            },
            isMockLocation = LocationCompat.isMock(location),
            hdop = null,
            sourceSentence = "SYSTEM_GPS:${location.provider}",
            valid = location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0,
        )
        _fix.value = value
        _status.value=PhoneLocationStatus(PhoneLocationPhase.LISTENING,received)
        if (value.valid) appendRecent(value)
    }

    /** Establishes the exact raw-provider precondition needed by black-box ARM
     * race tests. It still enters through [publish], so provider conversion,
     * mock rejection and every downstream integrity rule remain production
     * code. Release builds cannot call this entry point. */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun publishProviderLocationForTest(location:Location) {
        check(BuildConfig.DEBUG) { "Provider test injection is disabled in release builds" }
        publish(location)
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun setAppEnabled(enabled: Boolean) = synchronized(guard) { appEnabled = enabled; reconcileLocked() }
    fun setPreviewEnabled(enabled: Boolean) = synchronized(guard) { previewEnabled = enabled; reconcileLocked() }
    fun setBackgroundEnabled(enabled: Boolean) = synchronized(guard) { backgroundEnabled = enabled; reconcileLocked() }
    fun refreshPermission() = synchronized(guard) { reconcileLocked() }

    @SuppressLint("MissingPermission")
    private fun reconcileLocked() {
        val requested=sourcePermitsPhone&&(appEnabled||backgroundEnabled)
        val permission=hasPermission()
        val providerEnabled=runCatching{locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)}.getOrDefault(false)
        if(!requested||!permission){
            if(running)runCatching{locationManager.removeUpdates(listener)}
            running=false;_fix.value=null
            _status.value=PhoneLocationStatus(if(requested)PhoneLocationPhase.PERMISSION_REQUIRED else PhoneLocationPhase.OFF)
            return
        }
        // Register GNSS once, even while its provider is disabled. Android's
        // provider callback resumes delivery; a stale fix never restarts it.
        if(!running){
            val error=runCatching{
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1_000L,0f,listener,Looper.getMainLooper())
                running=true
                if(providerEnabled)locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::publish)
            }.exceptionOrNull()
            if(error!=null){_status.value=PhoneLocationStatus(PhoneLocationPhase.ERROR,error=error.message);return}
        }
        if(!providerEnabled)_fix.value=null
        _status.value=PhoneLocationStatus(if(providerEnabled)PhoneLocationPhase.LISTENING else PhoneLocationPhase.PROVIDER_DISABLED,_fix.value?.receivedElapsedRealtime)
    }

    private fun appendRecent(fix: NavigationFix) {
        val cutoff = fix.receivedElapsedRealtime - 10 * 60_000L
        _recentFixes.value = (_recentFixes.value + fix).filter { it.receivedElapsedRealtime >= cutoff }.takeLast(1_200)
    }
}

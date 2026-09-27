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
import com.yokuli.runtime.contract.time.MarineTime
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.device.*
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.yokuli.anchorwatch.BuildConfig
import com.yokuli.anchorwatch.domain.model.NavigationFix
import com.yokuli.anchorwatch.domain.model.PositionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
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
    val selectionPending:Boolean=false,
)

@Singleton
class SystemLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    settings:com.yokuli.anchorwatch.data.preferences.SettingsRepository,
    private val residency:com.yokuli.anchorwatch.runtime.RuntimeResidencyRepository,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val guard = Any()
    private val _fix = MutableStateFlow<NavigationFix?>(null)
    val fix = _fix.asStateFlow()
    private val _recentFixes = MutableStateFlow<List<NavigationFix>>(emptyList())
    val recentFixes = _recentFixes.asStateFlow()
    private var appEnabled = false
    /** 中文：用户明确请求替换船位后的临时采集租约；未提交前不发布为全船船位。 */
    private var preparingSelection = false
    private val preparedLocation = MutableStateFlow<NavigationFix?>(null)
    private var realDevice: HardwareDevice? = null
    private var realEpoch = MarineDeviceBus.state.value.epoch
    private var realResumeRequired = false
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
        override fun onProviderDisabled(provider:String){if(provider==LocationManager.GPS_PROVIDER)synchronized(guard){_fix.value=null;MarineDeviceBus.detach("phone.gnss", "Android GNSS disabled");realDevice=null;reconcileLocked()}}
    }
    init{
        MarineDeviceBus.subscribe(DeviceKind.GNSS, ::consumeFrame) { id -> synchronized(guard) {
            if (id == null || _fix.value?.hardwareDeviceId == id) { _fix.value=null;_recentFixes.value=emptyList();preparedLocation.value=null }
        } }
        MarineDeviceBus.onBackendChanged { snapshot -> synchronized(guard) {
            if(running)runCatching{locationManager.removeUpdates(listener)}
            running=false;realDevice=null;realEpoch=snapshot.epoch
            // 返回真实世界必须再次确认定位，不把实验室的采集意图带回 Android。
            realResumeRequired=snapshot.backend==DeviceBackend.REAL
            reconcileLocked()
        } }

        ContextCompat.registerReceiver(context,object:BroadcastReceiver(){
            override fun onReceive(context:Context?,intent:Intent?){synchronized(guard){reconcileLocked()}}
        },IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),ContextCompat.RECEIVER_NOT_EXPORTED)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Default).launch{
            kotlinx.coroutines.flow.combine(settings.settings,residency.state){settings,_->settings}.collect{value->synchronized(guard){
                sourcePermitsPhone=value.gpsDataSource in setOf(com.yokuli.anchorwatch.domain.model.GpsDataSource.SYSTEM,com.yokuli.anchorwatch.domain.model.GpsDataSource.DEMO)
                _sourceConsent.value=sourcePermitsPhone;reconcileLocked()
            }}
        }
    }

    private fun publish(location: Location) = synchronized(guard) {
        if (LocationCompat.isMock(location) || location.provider!=LocationManager.GPS_PROVIDER || MarineDeviceBus.state.value.backend!=DeviceBackend.REAL) return@synchronized
        val device=realDevice?:return@synchronized
        val measured=location.elapsedRealtimeNanos.takeIf{it>0}?.div(1_000_000)?.let(MarineTime::fromHostElapsedMillis)?:MarineTime.nowElapsedMillis()
        MarineDeviceBus.publish(device.spec.id,HardwarePayload(
            kind=DeviceKind.GNSS,latitude=location.latitude,longitude=location.longitude,
            sogKnots=location.speed.takeIf{location.hasSpeed()}?.times(1.943844),
            cogTrueDegrees=location.bearing.takeIf{location.hasBearing()}?.toDouble(),
            altitudeMeters=location.altitude.takeIf{location.hasAltitude()},
            accuracyMeters=location.accuracy.takeIf{location.hasAccuracy()}?.toDouble(),
            satellites=location.extras?.getInt("satellites")?.takeIf{it>0},
        ),measured,location.time,realEpoch,device.generation)
    }

    private fun consumeFrame(frame:HardwareFrame)=synchronized(guard) {
        if(!MarineDeviceBus.isCurrent(frame))return@synchronized
        val p=frame.payload
        val value=NavigationFix(latitude=p.latitude?:return@synchronized,longitude=p.longitude?:return@synchronized,
            timestampUtcMillis=frame.utcMillis,receivedElapsedRealtime=frame.measuredElapsedMillis,
            sogKnots=p.sogKnots,cogTrueDegrees=p.cogTrueDegrees,headingTrueDegrees=null,
            altitudeMeters=p.altitudeMeters,horizontalAccuracyMeters=p.accuracyMeters,satellites=p.satellites,
            positionProvider=PositionProvider.ANDROID_GNSS,isMockLocation=false,
            sourceSentence="${frame.backend}:GNSS:${frame.deviceId}",valid=true,
            hardwareBackend=frame.backend.name,hardwareEpoch=frame.epoch,hardwareDeviceId=frame.deviceId)
        if(preparingSelection&&MarineTime.nowElapsedMillis()-frame.measuredElapsedMillis in 0L..10_000L&&p.accuracyMeters?.let{it<=100.0}==true)preparedLocation.value=value
        if(!sourcePermitsPhone||!(appEnabled||backgroundEnabled))return@synchronized
        if(_fix.value?.let{it.hardwareEpoch==frame.epoch&&frame.measuredElapsedMillis<=it.receivedElapsedRealtime}==true)return@synchronized
        _fix.value=value
        _status.value=PhoneLocationStatus(PhoneLocationPhase.LISTENING,frame.measuredElapsedMillis,selectionPending=preparingSelection)
        appendRecent(value)
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

    suspend fun preparePositionSelection():Boolean {
        synchronized(guard){
            check(hasPermission()){ "Phone position requires precise location permission." }
            check(MarineDeviceBus.state.value.backend!=DeviceBackend.REAL||locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){ "Turn on Android location before choosing phone position." }
            preparedLocation.value=null;preparingSelection=true;realResumeRequired=false;reconcileLocked()
            check(_status.value.phase!=PhoneLocationPhase.ERROR){ "Phone location could not start." }
        }
        return withTimeoutOrNull(20_000L){preparedLocation.first{it!=null}}!=null
    }
    fun preparedPositionIsReady():Boolean=synchronized(guard) {
        preparedLocation.value?.let { MarineTime.nowElapsedMillis()-it.receivedElapsedRealtime in 0L..10_000L }==true
    }
    fun finishPositionSelection(adopted:Boolean)=synchronized(guard) {
        val position=preparedLocation.value
        if(adopted){sourcePermitsPhone=true;_sourceConsent.value=true;appEnabled=true}
        preparingSelection=false;preparedLocation.value=null
        if(adopted&&position!=null){_fix.value=position;_status.value=PhoneLocationStatus(PhoneLocationPhase.LISTENING,position.receivedElapsedRealtime);appendRecent(position)}
        reconcileLocked()
    }
    fun hasPermission(): Boolean = MarineDeviceBus.state.value.backend!=DeviceBackend.REAL || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun setAppEnabled(enabled: Boolean) = synchronized(guard) { appEnabled = enabled; reconcileLocked() }
    fun setPreviewEnabled(enabled: Boolean) = synchronized(guard) { previewEnabled = enabled; reconcileLocked() }
    fun setBackgroundEnabled(enabled: Boolean) = synchronized(guard) { backgroundEnabled = enabled; reconcileLocked() }
    fun refreshPermission() = synchronized(guard) { reconcileLocked() }

    @SuppressLint("MissingPermission")
    private fun reconcileLocked() {
        val requested=!residency.explicitlyStopped&&(preparingSelection||sourcePermitsPhone&&(appEnabled||backgroundEnabled))
        if(MarineDeviceBus.state.value.backend!=DeviceBackend.REAL){
            if(running)runCatching{locationManager.removeUpdates(listener)}
            running=false;realDevice=null
            if(!requested){_fix.value=null;_status.value=PhoneLocationStatus(PhoneLocationPhase.OFF)}
            else _status.value=PhoneLocationStatus(PhoneLocationPhase.LISTENING,_fix.value?.receivedElapsedRealtime,selectionPending=preparingSelection)
            return
        }
        val permission=hasPermission()
        val providerEnabled=runCatching{locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)}.getOrDefault(false)
        if(!requested||!permission||realResumeRequired){
            if(running)runCatching{locationManager.removeUpdates(listener)}
            running=false;_fix.value=null
            realDevice?.let{MarineDeviceBus.detach(it.spec.id,"GNSS collection stopped")};realDevice=null
            _status.value=PhoneLocationStatus(if(requested)PhoneLocationPhase.PERMISSION_REQUIRED else PhoneLocationPhase.OFF)
            return
        }
        // Register GNSS once, even while its provider is disabled. Android's
        // provider callback resumes delivery; a stale fix never restarts it.
        if(!running){
            val error=runCatching{
                realEpoch=MarineDeviceBus.state.value.epoch
                realDevice=MarineDeviceBus.attach(HardwareDeviceSpec("phone.gnss","Phone GNSS",DeviceKind.GNSS,DeviceBackend.REAL,"android.location",listOf("position","sog","cog")))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1_000L,0f,listener,Looper.getMainLooper())
                running=true
                if(providerEnabled)locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::publish)
            }.exceptionOrNull()
            if(error!=null){_status.value=PhoneLocationStatus(PhoneLocationPhase.ERROR,error=error.message,selectionPending=preparingSelection);return}
        }
        if(providerEnabled&&running&&realDevice==null)realDevice=MarineDeviceBus.attach(HardwareDeviceSpec("phone.gnss","Phone GNSS",DeviceKind.GNSS,DeviceBackend.REAL,"android.location",listOf("position","sog","cog")))
        if(!providerEnabled)_fix.value=null
        _status.value=PhoneLocationStatus(if(providerEnabled)PhoneLocationPhase.LISTENING else PhoneLocationPhase.PROVIDER_DISABLED,_fix.value?.receivedElapsedRealtime,selectionPending=preparingSelection)
    }

    private fun appendRecent(fix: NavigationFix) {
        val cutoff = fix.receivedElapsedRealtime - 10 * 60_000L
        _recentFixes.value = (_recentFixes.value + fix).filter { it.receivedElapsedRealtime >= cutoff }.takeLast(1_200)
    }
}

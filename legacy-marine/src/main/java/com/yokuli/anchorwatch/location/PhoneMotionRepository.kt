package com.yokuli.anchorwatch.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.device.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

data class PhoneMotionState(
    val available: Boolean = false,
    val moving: Boolean = false,
    val disturbed: Boolean = false,
    val accelerationDeltaMetersPerSecondSquared: Double = 0.0,
    val angularVelocityRadPerSecond: Double = 0.0,
    val updatedElapsedRealtime: Long? = null,
)

/**
 * Motion integrity is deliberately independent from phone heading. An active
 * System-GNSS watch keeps this sensor running even when the user has disabled
 * phone-heading evidence, so moving the handset cannot manufacture a trusted
 * position jump.
 */
@Singleton
class PhoneMotionRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val driver:AndroidMarineSensorDriver,
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val _state = MutableStateFlow(PhoneMotionState(available = accelerometer != null || gyroscope != null))
    val state = _state.asStateFlow()
    private var running = false
    private var acceleration = 9.81
    private var angularVelocity = 0.0
    private var accelerationMeasured:Long?=null
    private var gyroMeasured:Long?=null

    private var currentDevice:String?=null
    private val busSubscription=MarineDeviceBus.subscribe(DeviceKind.IMU,::consumeFrame){id->synchronized(this){
        if(id==null||id==currentDevice){acceleration=9.81;angularVelocity=0.0;accelerationMeasured=null;gyroMeasured=null;currentDevice=null;_state.value=PhoneMotionState(available=isAvailable())}
    }}
    private fun isAvailable()=MarineDeviceBus.state.value.backend!=DeviceBackend.REAL||accelerometer!=null||gyroscope!=null
    @Synchronized fun start():Boolean {
        if(running)return isAvailable()
        running=driver.request("phone.motion",setOf(DeviceKind.IMU))
        return running
    }
    @Synchronized fun stop(){running=false;driver.release("phone.motion");acceleration=9.81;angularVelocity=0.0;accelerationMeasured=null;gyroMeasured=null;currentDevice=null;_state.value=PhoneMotionState(available=isAvailable())}
    override fun onSensorChanged(event:SensorEvent)=Unit
    @Synchronized private fun consumeFrame(frame:HardwareFrame) {
        if(!running||!MarineDeviceBus.isCurrent(frame))return
        if(frame.payload.sensorType !in setOf(HardwareSensorType.ACCELEROMETER,HardwareSensorType.GYROSCOPE))return
        currentDevice=frame.deviceId
        val magnitude=sqrt(frame.payload.values.sumOf{it*it})
        if(frame.payload.sensorType==HardwareSensorType.ACCELEROMETER){
            if(accelerationMeasured?.let{frame.measuredElapsedMillis<=it}==true)return
            acceleration=magnitude;accelerationMeasured=frame.measuredElapsedMillis
        }else{
            if(gyroMeasured?.let{frame.measuredElapsedMillis<=it}==true)return
            angularVelocity=magnitude;gyroMeasured=frame.measuredElapsedMillis
        }
        val at=frame.measuredElapsedMillis
        val accelerationDelta=if(accelerationMeasured?.let{at-it in 0L..2_000L}==true)abs(acceleration-9.81)else 0.0
        val turnRate=if(gyroMeasured?.let{at-it in 0L..2_000L}==true)angularVelocity else 0.0
        _state.value=PhoneMotionState(true,turnRate>.7||accelerationDelta>3.0,turnRate>1.4||accelerationDelta>5.0,accelerationDelta,turnRate,at)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun magnitude(values: FloatArray): Double =
        sqrt(values.take(3).sumOf { it.toDouble() * it.toDouble() })
}

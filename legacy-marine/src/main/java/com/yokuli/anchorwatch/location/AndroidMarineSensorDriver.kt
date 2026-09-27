package com.yokuli.anchorwatch.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.yokuli.runtime.contract.device.DeviceBackend
import com.yokuli.runtime.contract.device.DeviceKind
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.time.MarineTime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 中文：Android IMU/气压唯一采集适配器。业务租约合并后仅注册一组监听，所有数据先经过设备总线。 */
@Singleton
class AndroidMarineSensorDriver @Inject constructor(@ApplicationContext context: Context) : SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val rotation=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?:manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val demands=linkedMapOf<String,Set<DeviceKind>>()
    private val guard=Any()
    private val registered=mutableSetOf<Int>()
    private val devices=mutableMapOf<DeviceKind,HardwareDevice>()
    private var epoch=MarineDeviceBus.state.value.epoch
    private var realResumeRequired=false
    init { MarineDeviceBus.onBackendChanged { state -> synchronized(guard) {
        manager.unregisterListener(this);registered.clear();devices.clear();epoch=state.epoch
        realResumeRequired=state.backend==DeviceBackend.REAL
    } } }

    fun request(owner:String,kinds:Set<DeviceKind>):Boolean=synchronized(guard) {
        demands[owner]=kinds
        if(MarineDeviceBus.state.value.backend!=DeviceBackend.REAL)return@synchronized true
        realResumeRequired=false
        reconcile()
        kinds.all { kind -> devices.containsKey(kind) }
    }
    fun release(owner:String)=synchronized(guard) {
        demands.remove(owner)
        val needed=demands.values.flatten().toSet()
        val detached=devices.filterKeys{it !in needed}.values.toList()
        if(MarineDeviceBus.state.value.backend==DeviceBackend.REAL)reconcile()
        // 释放与再次申请原子排序，避免新租约刚 attach 又被上一租约迟到的 detach 移除。
        // 各消费仓库已自行清空，不跨仓库锁同步回调；目录仍记录真实停止边界。
        detached.forEach{MarineDeviceBus.detach(it.spec.id,"Sensor collection stopped",notifyConsumers=false)}
    }
    private fun reconcile() {
        if(realResumeRequired||MarineDeviceBus.state.value.backend!=DeviceBackend.REAL)return
        val needed=demands.values.flatten().toSet()
        val types=buildSet {
            if(DeviceKind.IMU in needed){rotation?.let{add(it.type)};add(Sensor.TYPE_ACCELEROMETER);add(Sensor.TYPE_LINEAR_ACCELERATION);add(Sensor.TYPE_GYROSCOPE);add(Sensor.TYPE_MAGNETIC_FIELD);if(rotation==null)add(Sensor.TYPE_ORIENTATION)}
            if(DeviceKind.PRESSURE in needed)add(Sensor.TYPE_PRESSURE)
        }
        registered.filter{it !in types}.forEach{type->manager.getDefaultSensor(type)?.let{manager.unregisterListener(this,it)}}
        registered.retainAll(types)
        types.filter{it !in registered}.forEach { type ->
            val sensor=manager.getDefaultSensor(type)?:return@forEach
            if(manager.registerListener(this,sensor,if(type==Sensor.TYPE_PRESSURE)SensorManager.SENSOR_DELAY_NORMAL else SensorManager.SENSOR_DELAY_GAME))registered+=type
        }
        devices.keys.filter{it !in needed}.toList().forEach(devices::remove)
        if(registered.any{it!=Sensor.TYPE_PRESSURE}&&DeviceKind.IMU !in devices)devices[DeviceKind.IMU]=MarineDeviceBus.attach(HardwareDeviceSpec("phone.imu","Phone motion",DeviceKind.IMU,DeviceBackend.REAL,"android.sensor",listOf("rotation","gyro","acceleration","magnetometer")))
        if(Sensor.TYPE_PRESSURE in registered&&DeviceKind.PRESSURE !in devices)devices[DeviceKind.PRESSURE]=MarineDeviceBus.attach(HardwareDeviceSpec("phone.pressure","Phone barometer",DeviceKind.PRESSURE,DeviceBackend.REAL,"android.sensor",listOf("pressure")))
        epoch=MarineDeviceBus.state.value.epoch
    }
    override fun onSensorChanged(event:SensorEvent) {
        val kind=if(event.sensor.type==Sensor.TYPE_PRESSURE)DeviceKind.PRESSURE else DeviceKind.IMU
        val capture=synchronized(guard){if(event.sensor.type !in registered)return;devices[kind]?.let{it to epoch}}?:return
        val payload=if(kind==DeviceKind.PRESSURE)HardwarePayload(kind=kind,pressureHpa=event.values.firstOrNull()?.toDouble())else {
            val type=when(event.sensor.type){
                Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR->HardwareSensorType.ROTATION_QUATERNION
                Sensor.TYPE_ACCELEROMETER->HardwareSensorType.ACCELEROMETER
                Sensor.TYPE_LINEAR_ACCELERATION->HardwareSensorType.LINEAR_ACCELERATION
                Sensor.TYPE_GYROSCOPE->HardwareSensorType.GYROSCOPE
                Sensor.TYPE_MAGNETIC_FIELD->HardwareSensorType.MAGNETOMETER
                Sensor.TYPE_ORIENTATION->HardwareSensorType.LEGACY_ORIENTATION
                else->return
            }
            val values=if(type==HardwareSensorType.ROTATION_QUATERNION){val q=FloatArray(4);SensorManager.getQuaternionFromVector(q,event.values);q.map{it.toDouble()}}else event.values.take(3).map{it.toDouble()}
            HardwarePayload(kind=kind,sensorType=type,values=values,sensorAccuracy=event.accuracy)
        }
        MarineDeviceBus.publish(capture.first.spec.id,payload,MarineTime.fromHostElapsedMillis(event.timestamp/1_000_000L),MarineTime.nowUtcMillis(),capture.second,capture.first.generation)
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
}

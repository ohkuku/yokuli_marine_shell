package com.yokuli.anchorwatch.location.vessel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yokuli.anchorwatch.domain.vessel.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DeviceBowAxis { TOP, BOTTOM, LEFT, RIGHT }
enum class PhoneVesselMountState { HANDHELD, VESSEL_MOUNTED, MOUNT_SUSPECT, UNCALIBRATED }
data class SensorQuaternion(val w:Double,val x:Double,val y:Double,val z:Double){
    fun inverse()=SensorQuaternion(w,-x,-y,-z)
    operator fun times(other:SensorQuaternion)=SensorQuaternion(w*other.w-x*other.x-y*other.y-z*other.z,w*other.x+x*other.w+y*other.z-z*other.y,w*other.y-x*other.z+y*other.w+z*other.x,w*other.z+x*other.y-y*other.x+z*other.w)
    fun normalized():SensorQuaternion{val n=sqrt(w*w+x*x+y*y+z*z);require(n.isFinite()&&n>1e-9){"Invalid sensor rotation"};return SensorQuaternion(w/n,x/n,y/n,z/n)}
}
data class VesselMountCalibration(
    val version:Int=1,
    val bowAxis:DeviceBowAxis=DeviceBowAxis.TOP,
    val neutralQuaternion:SensorQuaternion=SensorQuaternion(1.0,0.0,0.0,0.0),
    val calibratedAt:Long=0,
    val mountState:PhoneVesselMountState=PhoneVesselMountState.UNCALIBRATED,
    val headingAlignmentOffsetDegrees:Double=0.0,
    /** Legacy backup field. Automatic recovery is deliberately disabled: an
     * invalidated Trip attitude segment always needs a new user confirmation. */
    val automaticMountRecovery:Boolean=false,
    val headingAlignmentCompletedAt:Long=0,
    val mountConfirmedVersion:Int=0,
    val headingAlignmentVersion:Int=0,
    val attitudeInvalidatedAt:Long=0,
    /** 中文：1 表示手机物理顶部；旧版本采用屏幕旋转坐标，升级后必须重新确认 Heading。 */
    val headingReferenceVersion:Int=1,
    /** 中文：3 采用当前固定安装作为零点；旧绝对姿态模型需用户重新确认。 */
    val attitudeFrameVersion:Int=3,
    /** 中文：相对安装零点的横倾和纵倾补偿，正值分别为右舷下沉和艏部抬起。 */
    val heelOffsetDegrees:Double=0.0,
    val pitchOffsetDegrees:Double=0.0,
){
    val attitudeFrameConfirmed:Boolean get()=calibratedAt>attitudeInvalidatedAt&&attitudeFrameVersion==3
    val mountConfirmed:Boolean get()=attitudeFrameConfirmed&&mountState==PhoneVesselMountState.VESSEL_MOUNTED&&mountConfirmedVersion==version
    /** Heading alignment is a separate, durable coordinate relationship. A
     * later Trip attitude-frame confirmation must not erase or stale it. */
    val headingAligned:Boolean get()=headingAlignmentCompletedAt>0L&&headingReferenceVersion==1&&attitudeFrameVersion==3
}
enum class PhoneVesselOutputBlocker{VESSEL_ZERO_REQUIRED,MOUNT_CONFIRMATION_REQUIRED,HEADING_ALIGNMENT_REQUIRED,MOUNT_SUSPECT}
data class PhoneVesselOutputReadiness(val ready:Boolean,val blockers:Set<PhoneVesselOutputBlocker>)
object PhoneVesselOutputReadinessPolicy{
    fun evaluate(calibration:VesselMountCalibration,@Suppress("UNUSED_PARAMETER") runtimeMountState:PhoneVesselMountState):PhoneVesselOutputReadiness{
        val blockers=buildSet{
            if(!calibration.headingAligned)add(PhoneVesselOutputBlocker.HEADING_ALIGNMENT_REQUIRED)
        }
        return PhoneVesselOutputReadiness(blockers.isEmpty(),blockers)
    }
}
enum class PhoneHeadingAlignmentReference{TRUE_NORTH,MAGNETIC_NORTH}
data class PhoneHeadingAlignmentMatch(val offsetDegrees:Double,val reference:PhoneHeadingAlignmentReference)

/** Keeps the persisted angular correction as an implementation detail. The
 * user either points the phone toward the bow (zero correction), or asks the
 * App to match two simultaneous headings with the same north reference. */
object PhoneHeadingAlignmentPolicy{
    fun matchLiveReference(
        phoneTrueDegrees:Double?,
        phoneMagneticDegrees:Double?,
        vesselTrueDegrees:Double?,
        vesselMagneticDegrees:Double?,
    ):PhoneHeadingAlignmentMatch?=when{
        phoneTrueDegrees.isHeading()&&vesselTrueDegrees.isHeading()->PhoneHeadingAlignmentMatch(shortestOffset(vesselTrueDegrees!!,phoneTrueDegrees!!),PhoneHeadingAlignmentReference.TRUE_NORTH)
        phoneMagneticDegrees.isHeading()&&vesselMagneticDegrees.isHeading()->PhoneHeadingAlignmentMatch(shortestOffset(vesselMagneticDegrees!!,phoneMagneticDegrees!!),PhoneHeadingAlignmentReference.MAGNETIC_NORTH)
        else->null
    }

    fun shortestOffset(referenceDegrees:Double,phoneDegrees:Double):Double{
        require(referenceDegrees.isHeading()&&phoneDegrees.isHeading())
        return ((referenceDegrees-phoneDegrees+540.0)%360.0)-180.0
    }

    private fun Double?.isHeading()=this!=null&&isFinite()&&this in 0.0..360.0
}
data class PhoneSensorCapabilities(val attitudeAvailable:Boolean=false,val gyroAvailable:Boolean=false,val magnetometerAvailable:Boolean=false,val pressureAvailable:Boolean=false,val linearAccelerationAvailable:Boolean=false)
data class PhoneVesselAttitudeSample(val attitude:VesselAttitude?=null,val dynamicAccelerationG:Double=0.0,val mountSuspect:Boolean=false,val receivedElapsedRealtime:Long?=null)
data class PhonePressureSample(val pressureHpa:Double?=null,val receivedElapsedRealtime:Long?=null,
    /** 中文：每次重新注册传感器开始新连续段，不跨停采区间连接。 */
    val generation:Long=0)

/** 中文：安装确认捕获当前手机姿态作为船体零点；之后在固定船体轴中解释转动。
 * 世界航向仍由磁北参考决定，不能把相对 yaw 当作船首向。 */
object PhoneVesselAttitudeFrame {
    private fun zRotation(angle:Double)=SensorQuaternion(cos(angle/2),0.0,0.0,sin(angle/2))
    private fun rotate(q:SensorQuaternion,v:DoubleArray):DoubleArray {
        val r=q*SensorQuaternion(0.0,v[0],v[1],v[2])*q.inverse()
        return doubleArrayOf(r.x,r.y,r.z)
    }
    /** 手机顶部近乎朝上时使用屏幕外法线，避免竖装的船艏轴退化为零向量。 */
    private fun installationHeading(q:SensorQuaternion):Double {
        val top=rotate(q,doubleArrayOf(0.0,1.0,0.0))
        val forward=if(hypot(top[0],top[1])>.2)top else rotate(q,doubleArrayOf(0.0,0.0,1.0))
        return atan2(forward[0],forward[1])
    }
    fun vesselRotation(current:SensorQuaternion,calibration:VesselMountCalibration):SensorQuaternion {
        val neutral=calibration.neutralQuaternion.normalized()
        val levelAtCapture=zRotation(-installationHeading(neutral))
        return (current.normalized()*neutral.inverse()*levelAtCapture).normalized()
    }
    fun magneticHeading(current:SensorQuaternion,calibration:VesselMountCalibration):Double? {
        val forward=rotate(vesselRotation(current,calibration),doubleArrayOf(0.0,1.0,0.0))
        if(hypot(forward[0],forward[1])<.1)return null
        return (Math.toDegrees(atan2(forward[0],forward[1]))+360.0)%360.0
    }
    /** 由 Android 旋转矩阵恢复四元数，罗盘 fallback 与 rotation-vector 使用相同安装数学。 */
    fun fromMatrix(m:FloatArray):SensorQuaternion {
        val trace=m[0]+m[4]+m[8]
        val q=when {
            trace>0f->{val s=sqrt(trace.toDouble()+1)*2;SensorQuaternion(s/4,(m[7]-m[5])/s,(m[2]-m[6])/s,(m[3]-m[1])/s)}
            m[0]>m[4]&&m[0]>m[8]->{val s=sqrt(1.0+m[0]-m[4]-m[8])*2;SensorQuaternion((m[7]-m[5])/s,s/4,(m[1]+m[3])/s,(m[2]+m[6])/s)}
            m[4]>m[8]->{val s=sqrt(1.0+m[4]-m[0]-m[8])*2;SensorQuaternion((m[2]-m[6])/s,(m[1]+m[3])/s,s/4,(m[5]+m[7])/s)}
            else->{val s=sqrt(1.0+m[8]-m[0]-m[4])*2;SensorQuaternion((m[3]-m[1])/s,(m[2]+m[6])/s,(m[5]+m[7])/s,s/4)}
        }
        return q.normalized()
    }
    fun resolve(current:SensorQuaternion,gyroValues:DoubleArray,calibration:VesselMountCalibration):VesselAttitude {
        val corrected=vesselRotation(current,calibration)
        val upAtStarboard=2*(corrected.x*corrected.z-corrected.w*corrected.y)
        val upAtBow=2*(corrected.y*corrected.z+corrected.w*corrected.x)
        val upAtScreen=1-2*(corrected.x*corrected.x+corrected.y*corrected.y)
        val heel=Math.toDegrees(atan2(-upAtStarboard,upAtScreen))+calibration.heelOffsetDegrees
        val pitch=Math.toDegrees(asin(upAtBow.coerceIn(-1.0,1.0)))+calibration.pitchOffsetDegrees
        val mountTransform=calibration.neutralQuaternion.normalized().inverse()*zRotation(-installationHeading(calibration.neutralQuaternion.normalized()))
        val rates=rotate(mountTransform.inverse(),gyroValues)
        return VesselAttitude(heel,pitch,Math.toDegrees(rates[1]),Math.toDegrees(rates[0]),-Math.toDegrees(rates[2]))
    }
    /** 旧调用只用于兼容已有轴坐标使用者；实际传感器发布总是传入持久化安装。 */
    fun resolve(current:SensorQuaternion,gyroValues:DoubleArray,axis:DeviceBowAxis):VesselAttitude {
        val angle=when(axis){DeviceBowAxis.TOP->0.0;DeviceBowAxis.RIGHT->-Math.PI/2;DeviceBowAxis.BOTTOM->Math.PI;DeviceBowAxis.LEFT->Math.PI/2}
        return resolve(current*zRotation(angle),gyroValues,VesselMountCalibration())
    }
}

private val Context.mountStore by preferencesDataStore("vessel_mount_calibration")

@Singleton
class VesselMountCalibrationRepository @Inject constructor(@ApplicationContext private val context:Context){
    private object K{val heelOffset=doublePreferencesKey("heel_offset");val pitchOffset=doublePreferencesKey("pitch_offset");val attitudeFrameVersion=intPreferencesKey("attitude_frame_version");val version=intPreferencesKey("calibration_version");val axis=stringPreferencesKey("bow_axis");val w=doublePreferencesKey("neutral_w");val x=doublePreferencesKey("neutral_x");val y=doublePreferencesKey("neutral_y");val z=doublePreferencesKey("neutral_z");val at=longPreferencesKey("calibrated_at");val mount=stringPreferencesKey("mount_state");val mountConfirmedVersion=intPreferencesKey("mount_confirmed_version");val headingReferenceVersion=intPreferencesKey("heading_reference_version");val headingOffset=doublePreferencesKey("heading_alignment_offset");val headingAlignedAt=longPreferencesKey("heading_alignment_completed_at");val headingAlignmentVersion=intPreferencesKey("heading_alignment_version");val automaticRecovery=booleanPreferencesKey("automatic_mount_recovery");val attitudeInvalidatedAt=longPreferencesKey("attitude_invalidated_at")}
    val calibration=context.mountStore.data.map{p->
        val at=p[K.at]?:0;val version=p[K.version]?:1
        val neutral=runCatching{SensorQuaternion(p[K.w]?:1.0,p[K.x]?:0.0,p[K.y]?:0.0,p[K.z]?:0.0).normalized()}.getOrNull()
        VesselMountCalibration(version=version,bowAxis=p[K.axis]?.let{runCatching{DeviceBowAxis.valueOf(it)}.getOrNull()}?:DeviceBowAxis.TOP,neutralQuaternion=neutral?:SensorQuaternion(1.0,0.0,0.0,0.0),calibratedAt=at,mountState=p[K.mount]?.let{runCatching{PhoneVesselMountState.valueOf(it)}.getOrNull()}?:if(at>0)PhoneVesselMountState.HANDHELD else PhoneVesselMountState.UNCALIBRATED,headingAlignmentOffsetDegrees=p[K.headingOffset]?:0.0,automaticMountRecovery=false,headingAlignmentCompletedAt=p[K.headingAlignedAt]?:0L,mountConfirmedVersion=p[K.mountConfirmedVersion]?:0,headingAlignmentVersion=p[K.headingAlignmentVersion]?:0,attitudeInvalidatedAt=p[K.attitudeInvalidatedAt]?:0L,headingReferenceVersion=p[K.headingReferenceVersion]?:0,attitudeFrameVersion=if(neutral==null)0 else p[K.attitudeFrameVersion]?:1,heelOffsetDegrees=p[K.heelOffset]?:0.0,pitchOffsetDegrees=p[K.pitchOffset]?:0.0)
    }
    /** 中文：保存用户确认的固定安装零点；零点只在明确操作时更新。 */
    suspend fun save(axis:DeviceBowAxis,q:SensorQuaternion){context.mountStore.edit{p->p[K.attitudeFrameVersion]=3;p[K.heelOffset]=0.0;p[K.pitchOffset]=0.0;val version=(p[K.version]?:0)+1;p[K.version]=version;p[K.axis]=axis.name;p[K.w]=q.w;p[K.x]=q.x;p[K.y]=q.y;p[K.z]=q.z;p[K.at]=System.currentTimeMillis();p[K.attitudeInvalidatedAt]=0L;p[K.mount]=PhoneVesselMountState.HANDHELD.name;p[K.mountConfirmedVersion]=0}}
    suspend fun setMountState(value:PhoneVesselMountState)=context.mountStore.edit{p->p[K.mount]=value.name;p[K.mountConfirmedVersion]=if(value==PhoneVesselMountState.VESSEL_MOUNTED)p[K.version]?:1 else 0}
    suspend fun setHeadingAlignment(offsetDegrees:Double)=context.mountStore.edit{p->require(offsetDegrees.isFinite());p[K.headingReferenceVersion]=1;p[K.headingOffset]=((offsetDegrees+540.0)%360.0)-180.0;p[K.headingAlignedAt]=System.currentTimeMillis();p[K.headingAlignmentVersion]=maxOf(p[K.headingAlignmentVersion]?:0,p[K.version]?:1)+1}
    /** 中文：固定顶部朝艏时，同一笔写入提交安装和船首向；以当前固定姿态作为零点。 */
    suspend fun confirmFixedMount(q: SensorQuaternion?) = context.mountStore.edit { p ->
        val version = (p[K.version] ?: 0) + 1
        val now = System.currentTimeMillis()
        p[K.version] = version
        p[K.axis] = DeviceBowAxis.TOP.name
        p[K.attitudeFrameVersion] = 3
        p[K.heelOffset] = 0.0; p[K.pitchOffset] = 0.0
        p[K.headingOffset] = 0.0
        p[K.headingReferenceVersion] = 1
        p[K.headingAlignedAt] = now
        p[K.headingAlignmentVersion] = maxOf(p[K.headingAlignmentVersion] ?: 0, version) + 1
        if (q != null) {
            p[K.w] = q.w; p[K.x] = q.x; p[K.y] = q.y; p[K.z] = q.z
            p[K.at] = now; p[K.attitudeInvalidatedAt] = 0L
            p[K.mount] = PhoneVesselMountState.VESSEL_MOUNTED.name
            p[K.mountConfirmedVersion] = version
        } else {
            // 有罗盘但没有姿态传感器的手机仍可提供已对齐的船首向。
            p[K.at] = 0L; p[K.mountConfirmedVersion] = 0
            p[K.mount] = PhoneVesselMountState.UNCALIBRATED.name
        }
    }
    suspend fun setAttitudeOffsets(heel:Double,pitch:Double)=context.mountStore.edit { p ->
        require(heel.isFinite()&&pitch.isFinite()&&heel in -45.0..45.0&&pitch in -45.0..45.0)
        check(p[K.attitudeFrameVersion]==3&&(p[K.at]?:0L)>(p[K.attitudeInvalidatedAt]?:0L)){"Confirm the fixed installation first."}
        p[K.heelOffset]=heel;p[K.pitchOffset]=pitch
        val version=(p[K.version]?:0)+1;p[K.version]=version;p[K.mountConfirmedVersion]=version
    }
    suspend fun invalidateFixedMount() = context.mountStore.edit { p ->
        p[K.headingAlignedAt] = 0L
        p[K.headingAlignmentVersion] = (p[K.headingAlignmentVersion] ?: 0) + 1
        p[K.attitudeInvalidatedAt] = maxOf(p[K.attitudeInvalidatedAt] ?: 0L, System.currentTimeMillis())
        p[K.mount] = PhoneVesselMountState.MOUNT_SUSPECT.name
        p[K.mountConfirmedVersion] = 0
    }
    suspend fun invalidateAttitudeSegment(atWallTime:Long)=context.mountStore.edit{p->
        p[K.attitudeInvalidatedAt]=maxOf(p[K.attitudeInvalidatedAt]?:0L,atWallTime)
        p[K.mount]=PhoneVesselMountState.MOUNT_SUSPECT.name
        p[K.mountConfirmedVersion]=0
    }
    suspend fun restore(value:VesselMountCalibration){
        val normalized=value.neutralQuaternion.normalized()
        context.mountStore.edit{p->
            p[K.version]=value.version.coerceAtLeast(1)
            p[K.axis]=value.bowAxis.name
            p[K.w]=normalized.w;p[K.x]=normalized.x;p[K.y]=normalized.y;p[K.z]=normalized.z
            p[K.at]=value.calibratedAt.coerceAtLeast(0L);p[K.mount]=value.mountState.name;p[K.mountConfirmedVersion]=value.mountConfirmedVersion;p[K.headingOffset]=value.headingAlignmentOffsetDegrees;p[K.headingAlignedAt]=value.headingAlignmentCompletedAt.coerceAtLeast(0L);p[K.headingAlignmentVersion]=value.headingAlignmentVersion;p[K.automaticRecovery]=false;p[K.attitudeInvalidatedAt]=value.attitudeInvalidatedAt.coerceAtLeast(0L);p[K.headingReferenceVersion]=value.headingReferenceVersion.coerceIn(0,1);p[K.attitudeFrameVersion]=value.attitudeFrameVersion.coerceIn(0,3);p[K.heelOffset]=value.heelOffsetDegrees.takeIf{it.isFinite()}?.coerceIn(-45.0,45.0)?:0.0;p[K.pitchOffset]=value.pitchOffsetDegrees.takeIf{it.isFinite()}?.coerceIn(-45.0,45.0)?:0.0
        }
    }
}

@Singleton
class PhoneVesselAttitudeRepository @Inject constructor(@ApplicationContext context:Context,private val calibrationRepository:VesselMountCalibrationRepository):SensorEventListener{
    private val manager=context.getSystemService(SensorManager::class.java)
    private val rotation=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?:manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val gyro=manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val linear=manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val magnetometer=manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val capabilities=PhoneSensorCapabilities(rotation!=null,gyro!=null,magnetometer!=null,manager.getDefaultSensor(Sensor.TYPE_PRESSURE)!=null,linear!=null)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val _sample=MutableStateFlow(PhoneVesselAttitudeSample());val sample=_sample.asStateFlow()
    private val _mountState=MutableStateFlow(PhoneVesselMountState.UNCALIBRATED);val mountState=_mountState.asStateFlow()
    @Volatile private var calibration=VesselMountCalibration();private var currentQuaternion:SensorQuaternion?=null;private var quaternionReceivedElapsed:Long?=null;private var gyroValues=DoubleArray(3);private var dynamicG=0.0;private var running=false
    init{scope.launch{calibrationRepository.calibration.collect{calibration=it;_mountState.value=when{it.calibratedAt<=0->PhoneVesselMountState.UNCALIBRATED;!it.attitudeFrameConfirmed->PhoneVesselMountState.MOUNT_SUSPECT;else->it.mountState}}}}
    @Synchronized fun start():Boolean{if(running)return capabilities.attitudeAvailable;val a=rotation?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}?:false;if(a){gyro?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)};linear?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}};running=a;return running}
    @Synchronized fun stop(){if(running)manager.unregisterListener(this);running=false;currentQuaternion=null;quaternionReceivedElapsed=null;gyroValues=DoubleArray(3);_sample.value=PhoneVesselAttitudeSample()}
    suspend fun calibrate(axis:DeviceBowAxis):Boolean{
        // 确認安裝方向需要正在運作的真實傳感器樣本，不能重用停止前的姿態。
        val q=synchronized(this){currentQuaternion?.takeIf{running&&quaternionReceivedElapsed?.let{SystemClock.elapsedRealtime()-it in 0L..2_000L}==true}}?:return false
        calibrationRepository.save(axis,q);return true
    }
    suspend fun confirmFixedMount(): Boolean {
        val q = synchronized(this) { currentQuaternion?.takeIf {
            running && quaternionReceivedElapsed?.let { SystemClock.elapsedRealtime() - it in 0L..2_000L } == true
        } }
        if (capabilities.attitudeAvailable && q == null) return false
        calibrationRepository.confirmFixedMount(q)
        return true
    }
    suspend fun invalidateFixedMount() = calibrationRepository.invalidateFixedMount()
    suspend fun setMounted(mounted:Boolean){calibrationRepository.setMountState(if(mounted)PhoneVesselMountState.VESSEL_MOUNTED else PhoneVesselMountState.HANDHELD);_mountState.value=if(mounted)PhoneVesselMountState.VESSEL_MOUNTED else PhoneVesselMountState.HANDHELD}
    suspend fun alignHeading(offsetDegrees:Double)=calibrationRepository.setHeadingAlignment(offsetDegrees)
    suspend fun alignAttitude(heel:Double,pitch:Double)=calibrationRepository.setAttitudeOffsets(heel,pitch)
    @Synchronized override fun onSensorChanged(event:SensorEvent){if(!running)return;when(event.sensor.type){Sensor.TYPE_GYROSCOPE->{gyroValues=doubleArrayOf(event.values[0].toDouble(),event.values[1].toDouble(),event.values[2].toDouble())};Sensor.TYPE_LINEAR_ACCELERATION->{dynamicG=sqrt(event.values.take(3).sumOf{it.toDouble()*it.toDouble()})/SensorManager.GRAVITY_EARTH};Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR->{val values=FloatArray(4);SensorManager.getQuaternionFromVector(values,event.values);val current=runCatching{SensorQuaternion(values[0].toDouble(),values[1].toDouble(),values[2].toDouble(),values[3].toDouble()).normalized()}.getOrNull()?:return;currentQuaternion=current;quaternionReceivedElapsed=event.timestamp/1_000_000L;publish(current)}}}
    private fun publish(current:SensorQuaternion){
        val now=SystemClock.elapsedRealtime()
        // Keep currentQuaternion available so the user can calibrate, but never
        // expose the Android device frame as if it were a vessel frame.
        if(calibration.calibratedAt<=0L){_mountState.value=PhoneVesselMountState.UNCALIBRATED;_sample.value=PhoneVesselAttitudeSample(receivedElapsedRealtime=now);return}
        // 当前固定位置是用户明确确认的零点；只有再次确认才能重设，传感器不会自动调平。
        val attitude=PhoneVesselAttitudeFrame.resolve(current,gyroValues,calibration)
        val configuredMounted=calibration.mountConfirmed&&calibration.mountState==PhoneVesselMountState.VESSEL_MOUNTED
        if(!calibration.attitudeFrameConfirmed||calibration.mountState==PhoneVesselMountState.MOUNT_SUSPECT)_mountState.value=PhoneVesselMountState.MOUNT_SUSPECT
        else _mountState.value=if(configuredMounted)PhoneVesselMountState.VESSEL_MOUNTED else PhoneVesselMountState.HANDHELD
        val vesselFrame=_mountState.value==PhoneVesselMountState.VESSEL_MOUNTED
        _sample.value=if(vesselFrame)PhoneVesselAttitudeSample(attitude,dynamicG,false,now)else PhoneVesselAttitudeSample(dynamicAccelerationG=dynamicG,mountSuspect=_mountState.value==PhoneVesselMountState.MOUNT_SUSPECT,receivedElapsedRealtime=now)
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
}

@Singleton
class PhonePressureRepository @Inject constructor(@ApplicationContext context:Context):SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val sensor=manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val _sample=MutableStateFlow(PhonePressureSample())
    val sample=_sample.asStateFlow()
    @Volatile private var running=false
    private var generation=0L
    private var startedElapsed=0L
    @Synchronized fun start():Boolean {
        if(running)return true
        startedElapsed=SystemClock.elapsedRealtime()
        running=sensor?.let { manager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL) }?:false
        if(running)generation++
        return running
    }
    @Synchronized fun stop() {
        running=false
        manager.unregisterListener(this)
        _sample.value=PhonePressureSample(generation=generation)
    }
    @Synchronized override fun onSensorChanged(event:SensorEvent) {
        if(!running||event.sensor.type!=Sensor.TYPE_PRESSURE)return
        // Android SensorEvent 与 elapsedRealtime 使用同一开机时基；排队回调不能刷新旧数据。
        val measured=event.timestamp/1_000_000L
        if(measured<startedElapsed||measured>SystemClock.elapsedRealtime()||
            _sample.value.receivedElapsedRealtime?.let {measured<=it}==true)return
        event.values.firstOrNull()?.takeIf { it.isFinite()&&it in 800f..1_200f }?.let {
            _sample.value=PhonePressureSample(it.toDouble(),measured,generation)
        }
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
}

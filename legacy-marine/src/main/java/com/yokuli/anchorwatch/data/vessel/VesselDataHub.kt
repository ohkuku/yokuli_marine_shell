package com.yokuli.anchorwatch.data.vessel
import com.yokuli.anchorwatch.domain.model.GpsDataSource

import android.os.SystemClock
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.condition.LiveDepthRepository
import com.yokuli.anchorwatch.data.condition.LiveWindRepository
import com.yokuli.anchorwatch.data.nmea.NmeaFieldObservation
import com.yokuli.anchorwatch.data.nmea.NmeaFieldRepository
import com.yokuli.anchorwatch.data.nmea.NmeaFieldSemantic
import com.yokuli.anchorwatch.data.nmea.NmeaMeasurementConfirmation
import com.yokuli.anchorwatch.data.nmea.NmeaSourceInvalidation
import com.yokuli.anchorwatch.data.nmea.input.NmeaFieldCandidateMapper
import com.yokuli.anchorwatch.domain.model.HeadingSource
import com.yokuli.anchorwatch.domain.sonar.DepthReference
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourcePreference
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.anchorwatch.domain.vessel.source.VesselSourceArbitrator
import com.yokuli.anchorwatch.location.PhoneHeadingRepository
import com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState
import com.yokuli.anchorwatch.location.vessel.VesselMountCalibration
import com.yokuli.anchorwatch.location.vessel.VesselMountCalibrationRepository
import com.yokuli.anchorwatch.location.vessel.PhonePressureRepository
import com.yokuli.anchorwatch.location.vessel.PhoneVesselAttitudeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private enum class ResolvedWindField{SPEED,DIRECTION,ANGLE}

/** Unified instrument/presentation truth. It never feeds Anchor safety. */
@Singleton
class VesselDataHub @Inject constructor(private val navigation:NavigationRepository,depth:LiveDepthRepository,wind:LiveWindRepository,phoneHeading:PhoneHeadingRepository,positions:VesselPositionRepository,settings:VesselSettingsRepository,attitude:PhoneVesselAttitudeRepository,pressure:PhonePressureRepository,nmeaFields:NmeaFieldRepository,private val sourceRegistry:VesselSourceRegistry,mountCalibration:VesselMountCalibrationRepository,private val pressureHistory:PressureHistoryRepository){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);private val _snapshot=MutableStateFlow(VesselDataSnapshot());val snapshot=_snapshot.asStateFlow()
    private var boatPosition=VesselObservation<VesselPosition>();private var phonePosition=VesselObservation<VesselPosition>();private var sog=VesselObservation<Double>();private var cog=VesselObservation<Double>();private var speedThroughWater=VesselObservation<Double>();private var boatHeading=VesselObservation<Double>();private var magneticHeading=VesselObservation<Double>();private var phoneHeadingValue=VesselObservation<Double>();private var phoneMagneticHeadingValue=VesselObservation<Double>();private var depthValue=VesselObservation<Double>();private var trueWindSpeed=VesselObservation<Double>();private var trueWindDirection=VesselObservation<Double>();private var trueWindAngle=VesselObservation<Double>();private var apparentWindSpeed=VesselObservation<Double>();private var apparentWindAngle=VesselObservation<Double>();private var attitudeValue=VesselObservation<VesselAttitude>();private var motionValue=VesselObservation<VesselMotion>();private var pressureValue=VesselObservation<Double>();private var rateOfTurn=VesselObservation<Double>();private var rudderAngle=VesselObservation<Double>();private var waterTemperature=VesselObservation<Double>();private var airTemperature=VesselObservation<Double>();private var currentSet=VesselObservation<Double>();private var currentDrift=VesselObservation<Double>();private var crossTrackError=VesselObservation<Double>();private var waypointBearing=VesselObservation<Double>();private var waypointDistance=VesselObservation<Double>();private var destinationWaypoint=VesselObservation<String>();private var totalLog=VesselObservation<Double>();private var tripLog=VesselObservation<Double>();private val motionAnalyzer=VesselMotionAnalyzer()
    private val connectionPriorities={navigation.connectionPriorities()}
    private val displayObservations=mutableMapOf<VesselMetricId,VesselObservation<*>>()
    private val derivedDisplay=mutableMapOf<VesselMetricId,VesselObservation<Double>>()
    private val arbitrator=VesselSourceArbitrator();private val trueWindHysteresis=TrueWindSourceHysteresis()
    @Volatile private var positionPreference=VesselSourcePreference.AUTO;@Volatile private var headingPreference=VesselSourcePreference.AUTO
    @Volatile private var metricSourcePins:Map<String,String> = emptyMap()
    @Volatile private var pinnedPositionSourceId:String?=null;@Volatile private var pinnedHeadingSourceId:String?=null;@Volatile private var allowPinnedFallback=false
    @Volatile private var vesselDraftMeters=0.0
    @Volatile private var calibration=VesselMountCalibration()
    init{
        scope.launch{positions.boat.collect{boatPosition=it}};scope.launch{positions.phone.collect{phonePosition=it}}
        scope.launch{positions.acceptedPhoneFix.collect{fix->
            if(fix==null)sourceRegistry.removeSources(setOf(PHONE_GNSS_ID.id))
            if(fix!=null){
                val candidates=buildList<VesselSourceCandidate<*>>{
                    add(VesselSourceCandidate(VesselMetricId.POSITION,VesselPosition(fix.latitude,fix.longitude,fix.altitudeMeters,fix.horizontalAccuracyMeters,fix.satellites,fix.hdop),PHONE_GNSS_ID,VesselSourceClass.PHONE_GNSS,receivedElapsedRealtime=fix.receivedElapsedRealtime,observedAtUtcMillis=fix.timestampUtcMillis,provenance=VesselProvenance.PhoneSensor("Android GNSS")))
                    fix.sogKnots?.let{add(VesselSourceCandidate(VesselMetricId.SOG,it,PHONE_GNSS_ID,VesselSourceClass.PHONE_GNSS,receivedElapsedRealtime=fix.sogReceivedElapsedRealtime?:fix.receivedElapsedRealtime,provenance=VesselProvenance.PhoneSensor("Android GNSS")))}
                    fix.cogTrueDegrees?.let{add(VesselSourceCandidate(VesselMetricId.COG,it,PHONE_GNSS_ID,VesselSourceClass.PHONE_GNSS,VesselReference.TrueNorth,fix.cogReceivedElapsedRealtime?:fix.receivedElapsedRealtime,provenance=VesselProvenance.PhoneSensor("Android GNSS")))}
                }
                sourceRegistry.publishAll(candidates)
            }
        }}
        scope.launch{navigation.transportDiagnostics.map{it.connectionGeneration}.distinctUntilChanged().drop(1).collect{clearBoatGeneration()}}
        scope.launch{navigation.sourceInvalidations.collect(::invalidateLegacyBoatFields)}
        scope.launch{navigation.instruments.collect{value->value.sogOrNull()?.let{sog=it};value.cogOrNull()?.let{cog=it};value.stwOrNull()?.let{speedThroughWater=it};value.trueHeadingOrNull()?.let{boatHeading=it};value.magneticHeadingOrNull()?.let{magneticHeading=it}}}
        scope.launch{navigation.fix.filterNotNull().collect{fix->val source=VesselDataSource.BOAT_NMEA;fix.sogKnots?.let{sog=observation(it,source,fix.sogReceivedElapsedRealtime?:fix.receivedElapsedRealtime,fix.timestampUtcMillis,"NMEA SOG")};fix.cogTrueDegrees?.let{cog=observation(it,source,fix.cogReceivedElapsedRealtime?:fix.receivedElapsedRealtime,fix.timestampUtcMillis,"NMEA COG")};fix.speedThroughWaterKnots?.let{speedThroughWater=observation(it,source,fix.speedThroughWaterReceivedElapsedRealtime?:fix.receivedElapsedRealtime,fix.timestampUtcMillis,"VHW")};if(fix.headingSource==HeadingSource.NMEA_PHYSICAL)fix.headingTrueDegrees?.let{boatHeading=observation(it,source,fix.headingReceivedElapsedRealtime?:fix.receivedElapsedRealtime,fix.timestampUtcMillis,"HDT/HDG/VHW")};fix.headingMagneticDegrees?.let{magneticHeading=observation(it,source,fix.headingMagneticReceivedElapsedRealtime?:fix.receivedElapsedRealtime,fix.timestampUtcMillis,"HDM/HDG/VHW")}}}
        scope.launch{depth.state.collect{value->value.depthMeters?.let{meters->depthValue=observation(meters,if(value.isDemo)VesselDataSource.DEMO else VesselDataSource.BOAT_NMEA,value.receivedElapsedRealtime,System.currentTimeMillis(),"${value.sentenceType?.name?:"DEPTH"}:${value.reference?.name?:"UNKNOWN"}")}}}
        scope.launch{wind.state.collect{value->value.trueSpeed?.let{trueWindSpeed=observation(it.value,VesselDataSource.BOAT_NMEA,it.receivedElapsedRealtime,null,"NMEA true wind")};value.trueDirection?.let{trueWindDirection=observation(it.value,VesselDataSource.BOAT_NMEA,it.receivedElapsedRealtime,null,value.trueDirectionSource?.name)};value.trueAngle?.let{trueWindAngle=observation(it.value,VesselDataSource.BOAT_NMEA,it.receivedElapsedRealtime,null,"NMEA true angle")};value.apparentSpeed?.let{apparentWindSpeed=observation(it.value,VesselDataSource.BOAT_NMEA,it.receivedElapsedRealtime,null,"NMEA apparent wind")};value.apparentAngle?.let{apparentWindAngle=observation(it.value,VesselDataSource.BOAT_NMEA,it.receivedElapsedRealtime,null,"NMEA apparent angle")}}}
        // Presentation follows the responsive sensor channel. The integrity-gated
        // channel is deliberately reserved for persisted anchor-centre evidence.
        scope.launch{phoneHeading.sample.collect{sample->val quality=if(sample.presentationQuality==com.yokuli.anchorwatch.location.PhoneHeadingPresentationQuality.GOOD)VesselDataQuality.GOOD else VesselDataQuality.DEGRADED;val received=sample.receivedElapsedRealtime?:return@collect
            sample.liveTrueHeadingDegrees?.let{value->
                sourceRegistry.publish(VesselSourceCandidate(VesselMetricId.DEVICE_HEADING_TRUE,value,PHONE_DEVICE_HEADING_ID,VesselSourceClass.PHONE_DEVICE_COMPASS,VesselReference.TrueNorth,received,quality=quality,provenance=VesselProvenance.PhoneSensor("Android device compass")))
                if(calibration.headingAligned&&sample.liveVesselTrueHeadingDegrees!=null){val aligned=sample.liveVesselTrueHeadingDegrees;sourceRegistry.publish(VesselSourceCandidate(VesselMetricId.HEADING_TRUE,aligned,PHONE_VESSEL_HEADING_ID,VesselSourceClass.PHONE_VESSEL_HEADING,VesselReference.TrueNorth,received,quality=quality,provenance=VesselProvenance.PhoneSensor("Heading-aligned phone compass",calibration.headingAlignmentVersion)));phoneHeadingValue=observation(aligned,VesselDataSource.PHONE_MAGNETOMETER,received,null,"heading-aligned phone vessel heading",quality)}
            }
            sample.liveMagneticHeadingDegrees?.let{value->sourceRegistry.publish(VesselSourceCandidate(VesselMetricId.DEVICE_HEADING_MAGNETIC,value,PHONE_DEVICE_HEADING_ID,VesselSourceClass.PHONE_DEVICE_COMPASS,VesselReference.MagneticNorth,received,quality=quality,provenance=VesselProvenance.PhoneSensor("Android device compass")));if(calibration.headingAligned&&sample.liveVesselMagneticHeadingDegrees!=null){val aligned=sample.liveVesselMagneticHeadingDegrees;sourceRegistry.publish(VesselSourceCandidate(VesselMetricId.HEADING_MAGNETIC,aligned,PHONE_VESSEL_HEADING_ID,VesselSourceClass.PHONE_VESSEL_HEADING,VesselReference.MagneticNorth,received,quality=quality,provenance=VesselProvenance.PhoneSensor("Heading-aligned phone compass",calibration.headingAlignmentVersion)));phoneMagneticHeadingValue=observation(aligned,VesselDataSource.PHONE_MAGNETOMETER,received,null,"heading-aligned phone vessel magnetic heading",quality)}}
        }}
        scope.launch{attitude.mountState.collect{state->
            if(state!=PhoneVesselMountState.VESSEL_MOUNTED){
                // The fixed-frame flag belongs only to Trip attitude. Heading
                // has its own durable alignment and GNSS/pressure are device-
                // frame independent, so a moved phone must not clear them.
                attitudeValue=VesselObservation();motionValue=VesselObservation()
                sourceRegistry.removeSources(setOf(PHONE_IMU_ID.id))
            }
        }}
        scope.launch{mountCalibration.calibration.collect{value->calibration=value;if(!value.headingAligned){phoneHeadingValue=VesselObservation();phoneMagneticHeadingValue=VesselObservation();sourceRegistry.removeSources(setOf(PHONE_VESSEL_HEADING_ID.id))}}}
        scope.launch{attitude.sample.collect{sample->val value=sample.attitude;if(value!=null&&sample.receivedElapsedRealtime!=null){val quality=if(sample.mountSuspect)VesselDataQuality.DEGRADED else VesselDataQuality.GOOD;attitudeValue=observation(value,VesselDataSource.PHONE_IMU,sample.receivedElapsedRealtime,null,if(sample.mountSuspect)"PHONE_MOVED_OR_MOUNT_SUSPECT" else "calibrated vessel frame",quality);sourceRegistry.publishAll(listOf(VesselSourceCandidate(VesselMetricId.HEEL,value.heelDegrees,PHONE_IMU_ID,VesselSourceClass.PHONE_IMU,receivedElapsedRealtime=sample.receivedElapsedRealtime,quality=quality,provenance=VesselProvenance.PhoneSensor("Mounted phone IMU",calibration.version)),VesselSourceCandidate(VesselMetricId.PITCH,value.pitchDegrees,PHONE_IMU_ID,VesselSourceClass.PHONE_IMU,receivedElapsedRealtime=sample.receivedElapsedRealtime,quality=quality,provenance=VesselProvenance.PhoneSensor("Mounted phone IMU",calibration.version)),VesselSourceCandidate(VesselMetricId.ROLL_RATE,value.rollRateDegreesPerSecond,PHONE_IMU_ID,VesselSourceClass.PHONE_IMU,receivedElapsedRealtime=sample.receivedElapsedRealtime,quality=quality,provenance=VesselProvenance.PhoneSensor("Mounted phone IMU",calibration.version)),VesselSourceCandidate(VesselMetricId.PITCH_RATE,value.pitchRateDegreesPerSecond,PHONE_IMU_ID,VesselSourceClass.PHONE_IMU,receivedElapsedRealtime=sample.receivedElapsedRealtime,quality=quality,provenance=VesselProvenance.PhoneSensor("Mounted phone IMU",calibration.version)),VesselSourceCandidate(VesselMetricId.YAW_RATE,value.yawRateDegreesPerSecond,PHONE_IMU_ID,VesselSourceClass.PHONE_IMU,receivedElapsedRealtime=sample.receivedElapsedRealtime,quality=quality,provenance=VesselProvenance.PhoneSensor("Mounted phone IMU",calibration.version))));if(!sample.mountSuspect){val motion=motionAnalyzer.add(VesselMotionPoint(sample.receivedElapsedRealtime,value.heelDegrees,value.pitchDegrees,value.rollRateDegreesPerSecond,value.pitchRateDegreesPerSecond,sample.dynamicAccelerationG));motionValue=observation(motion,VesselDataSource.DERIVED,sample.receivedElapsedRealtime,null,VesselMotionAnalyzer.ALGORITHM_VERSION);motion.score?.let{sourceRegistry.publish(VesselSourceCandidate(VesselMetricId.MOTION_SCORE,it,PHONE_IMU_ID,VesselSourceClass.PHONE_IMU,receivedElapsedRealtime=sample.receivedElapsedRealtime,quality=quality,provenance=VesselProvenance.PhoneSensor("Mounted phone IMU",calibration.version)))}}}}}
        scope.launch {
            var registeredSourceId:String?=null
            pressure.sample.collect { sample ->
                val value=sample.pressureHpa
                val measured=sample.receivedElapsedRealtime
                val identity=PHONE_PRESSURE_ID.copy(id="${PHONE_PRESSURE_ID.id}:${sample.generation}",stableKey=PHONE_PRESSURE_ID.persistentKey)
                if(registeredSourceId!=identity.id||value==null||measured==null) {
                    registeredSourceId?.let { sourceRegistry.removeSources(setOf(it)) }
                    registeredSourceId=null
                }
                if(value!=null&&measured!=null) {
                    pressureHistory.record(identity.persistentKey,identity.displayName,value,measured,identity.id)
                    pressureValue=observation(value,VesselDataSource.PHONE_BAROMETER,measured,null,"TYPE_PRESSURE")
                    sourceRegistry.publish(VesselSourceCandidate(VesselMetricId.PRESSURE,value,identity,VesselSourceClass.PHONE_BAROMETER,
                        receivedElapsedRealtime=measured,quality=VesselDataQuality.GOOD,provenance=VesselProvenance.PhoneSensor("Android pressure sensor")))
                    registeredSourceId=identity.id
                }
            }
        }
        scope.launch{nmeaFields.fields.collect{fields->
            val candidates=fields.mapNotNull { NmeaFieldCandidateMapper.map(it,navigation.activeProfileStableId(),navigation.connectionGeneration()) }
            navigation.publishInputCandidates(candidates)
            // 每个真实连接/发送方均独立记录。字段列表重发旧值时由原测量身份去重。
            candidates.filter { it.metric==VesselMetricId.PRESSURE }.forEach { candidate ->
                (candidate.value as? Double)?.let { value ->
                    pressureHistory.record(candidate.source.persistentKey,candidate.source.displayName,value,
                        candidate.receivedElapsedRealtime,candidate.source.id,candidate.observedAtUtcMillis)
                }
            }
            fun numeric(semantic:NmeaFieldSemantic)=fields.filter{it.key.semantic==semantic&&it.value!=null}.maxByOrNull{it.receivedElapsedRealtime}
            fun textual(semantic:NmeaFieldSemantic)=fields.filter{it.key.semantic==semantic&&!it.text.isNullOrBlank()}.maxByOrNull{it.receivedElapsedRealtime}
            fun update(current:VesselObservation<Double>,semantic:NmeaFieldSemantic)=numeric(semantic)?.let{fieldObservation(it,it.value!!)}?:current
            rateOfTurn=update(rateOfTurn,NmeaFieldSemantic.ROT);rudderAngle=update(rudderAngle,NmeaFieldSemantic.RUDDER_ANGLE);waterTemperature=update(waterTemperature,NmeaFieldSemantic.WATER_TEMPERATURE);airTemperature=update(airTemperature,NmeaFieldSemantic.AIR_TEMPERATURE)
            currentSet=update(currentSet,NmeaFieldSemantic.CURRENT_SET_TRUE);currentDrift=update(currentDrift,NmeaFieldSemantic.CURRENT_DRIFT);crossTrackError=update(crossTrackError,NmeaFieldSemantic.CROSS_TRACK_ERROR);waypointBearing=update(waypointBearing,NmeaFieldSemantic.BEARING_TO_WAYPOINT);waypointDistance=update(waypointDistance,NmeaFieldSemantic.DISTANCE_TO_WAYPOINT);totalLog=update(totalLog,NmeaFieldSemantic.TOTAL_LOG);tripLog=update(tripLog,NmeaFieldSemantic.TRIP_LOG)
            numeric(NmeaFieldSemantic.AIR_PRESSURE)?.let{field->pressureValue=fieldObservation(field,field.value!!)}
            numeric(NmeaFieldSemantic.APPARENT_WIND_ANGLE)?.let{apparentWindAngle=fieldObservation(it,it.value!!)};numeric(NmeaFieldSemantic.APPARENT_WIND_SPEED)?.let{apparentWindSpeed=fieldObservation(it,it.value!!)}
            numeric(NmeaFieldSemantic.TRUE_WIND_ANGLE)?.let{trueWindAngle=fieldObservation(it,it.value!!)};numeric(NmeaFieldSemantic.TRUE_WIND_SPEED)?.let{trueWindSpeed=fieldObservation(it,it.value!!)}
            numeric(NmeaFieldSemantic.TRUE_WIND_DIRECTION)?.let{trueWindDirection=fieldObservation(it,it.value!!)}
            textual(NmeaFieldSemantic.DESTINATION_WAYPOINT)?.let{destinationWaypoint=fieldObservation(it,it.text!!)}
        }}
        scope.launch{settings.settings.collect{value->
            synchronized(this@VesselDataHub){
                val positionPin=value.pinnedPositionSourceId?.let(VesselSourcePinPolicy::normalize)
                val headingPin=value.boatHeadingSourceId?.let(VesselSourcePinPolicy::normalize)
                if(positionPreference!=value.positionPreference||headingPreference!=value.headingPreference||metricSourcePins!=value.metricSourcePins||pinnedPositionSourceId!=positionPin||pinnedHeadingSourceId!=headingPin||allowPinnedFallback!=value.allowPinnedFallback){
                    displayObservations.clear();derivedDisplay.clear()
                }
                positionPreference=value.positionPreference;metricSourcePins=value.metricSourcePins
                headingPreference=value.headingPreference;pinnedPositionSourceId=positionPin;pinnedHeadingSourceId=headingPin
                allowPinnedFallback=value.allowPinnedFallback;vesselDraftMeters=value.draftMeters?:0.0
            }
            navigation.pinBoatHeadingSource(pinnedHeadingSourceId?.substringAfterLast(':'),allowPinnedFallback)
        }}
        scope.launch{while(isActive){publish(SystemClock.elapsedRealtime());delay(250)}}
    }
    @Synchronized fun hasFreshPhonePosition(now:Long)=classify(phonePosition,now,3_000,10_000).let{it.value!=null&&it.freshness==VesselDataFreshness.FRESH}
    @Synchronized fun setTripPositionPreference(value:VesselSourcePreference?){/* Recording never changes the system source. */}
    @Volatile private var shellPositionSource=GpsDataSource.NONE
    @Synchronized fun setShellPositionSource(source:GpsDataSource){if(shellPositionSource!=source){derivedDisplay.clear();listOf(VesselMetricId.POSITION,VesselMetricId.SOG,VesselMetricId.COG).forEach(displayObservations::remove)};shellPositionSource=source;arbitrator.reset();publish(SystemClock.elapsedRealtime())}
    @Synchronized private fun publish(now:Long){
        val effectivePositionPreference=when(shellPositionSource){GpsDataSource.NMEA->VesselSourcePreference.BOAT;GpsDataSource.SYSTEM,GpsDataSource.DEMO->VesselSourcePreference.PHONE;GpsDataSource.NONE->VesselSourcePreference.DERIVED}
        // Output reads its own accepted Phone fix directly. Starting Phone TX
        // must never mutate the Hub's Boat/Phone presentation arbitration.
        val positionSelection=registrySelection<VesselPosition>(VesselMetricId.POSITION,effectivePositionPreference,pinnedPositionSourceId,now)
        val selectedPosition=if(shellPositionSource==GpsDataSource.NONE)VesselObservation<VesselPosition>() else selectionObservation(positionSelection)?:VesselObservation<VesselPosition>()
        val headingSelection=registrySelection<Double>(VesselMetricId.HEADING_TRUE,headingPreference,pinnedHeadingSourceId,now)
        val selectedHeading=selectionObservation(headingSelection)?:VesselObservation<Double>()
        val freshCog=selectionObservation(registrySelection<Double>(VesselMetricId.COG,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>()
        val headingCog=if(selectedHeading.value!=null&&freshCog.value!=null){val difference=abs(((selectedHeading.value-freshCog.value+540.0)%360.0)-180.0);derivedReading(difference,listOf(selectedHeading,freshCog),"heading minus COG")}else VesselObservation()
        val classifiedDepth=selectionObservation(registrySelection<Double>(VesselMetricId.DEPTH,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>();val classifiedPressure=selectionObservation(registrySelection<Double>(VesselMetricId.PRESSURE,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>()
        // 中文：数值和基准必须来自同一个已选观测；旁路安全水深的基准不能替另一个来源背书。
        val selectedDepthReference=(classifiedDepth.reference as? VesselReference.Depth)?.reference
        val ukcValue=UkcCompatibilityPolicy.calculate(classifiedDepth.value,selectedDepthReference,vesselDraftMeters)
        val ukc=if(ukcValue!=null) {
            derivedReading(ukcValue,listOf(classifiedDepth),"surface-referenced depth minus configured vessel draft").copy(
                // 计算余量不更新测量时间，不把保留/过期深度变成新观测，也不丢弃来源冲突。
                observedAtUtcMillis=classifiedDepth.observedAtUtcMillis,
                quality=classifiedDepth.quality,
                freshness=classifiedDepth.freshness,
                reference=VesselReference.Depth(DepthReference.BELOW_KEEL),
                conflict=classifiedDepth.conflict,
                sourceHeartbeatElapsedRealtime=classifiedDepth.sourceHeartbeatElapsedRealtime,
                selectionReason=classifiedDepth.selectionReason,
            )
        }else VesselObservation()
        fun pressureTrend(window:Long):VesselObservation<Double>{
            val sourceKey=classifiedPressure.sourceIdentity?.persistentKey?:return VesselObservation()
            val value=pressureHistory.trend(sourceKey,window,classifiedPressure.sourceIdentity.id)?:return VesselObservation()
            val sourceName=classifiedPressure.sourceIdentity.displayName
            return VesselObservation(value.changeHpa,classifiedPressure.source,observedAtUtcMillis=value.observedAtUtcMillis,receivedElapsedRealtime=value.measuredElapsedRealtime,quality=classifiedPressure.quality,freshness=classifiedPressure.freshness,provenance="$sourceName · linear pressure trend · coverage=${"%.0f".format(value.coverage*100)}%",sourceIdentity=classifiedPressure.sourceIdentity,sourceClass=classifiedPressure.sourceClass,provenanceDetail=VesselProvenance.Derived("pressure trend ${value.continuityKey}",listOf(classifiedPressure.sourceIdentity)))
        }
        val freshSog=selectionObservation(registrySelection<Double>(VesselMetricId.SOG,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>();val freshStw=selectionObservation(registrySelection<Double>(VesselMetricId.SPEED_THROUGH_WATER,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>();val externalTws=selectionObservation(registrySelection<Double>(VesselMetricId.TRUE_WIND_SPEED,VesselSourcePreference.BOAT,null,now))?:VesselObservation<Double>();val externalTwa=selectionObservation(registrySelection<Double>(VesselMetricId.TRUE_WIND_ANGLE,VesselSourcePreference.BOAT,null,now))?:VesselObservation<Double>();val externalTwd=selectionObservation(registrySelection<Double>(VesselMetricId.TRUE_WIND_DIRECTION,VesselSourcePreference.BOAT,null,now))?:VesselObservation<Double>();val freshWaypointBearing=selectionObservation(registrySelection<Double>(VesselMetricId.WAYPOINT_BEARING,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>();val freshAws=selectionObservation(registrySelection<Double>(VesselMetricId.APPARENT_WIND_SPEED,VesselSourcePreference.BOAT,null,now))?:VesselObservation<Double>();val freshAwa=selectionObservation(registrySelection<Double>(VesselMetricId.APPARENT_WIND_ANGLE,VesselSourcePreference.BOAT,null,now))?:VesselObservation<Double>()
        fun freshValue(value:VesselObservation<Double>)=value.value.takeIf{value.freshness==VesselDataFreshness.FRESH}
        val preferredWind=TrueWindResolver.resolve(freshValue(freshAws),freshValue(freshAwa),freshValue(externalTws),freshValue(externalTwd),freshValue(externalTwa),freshValue(freshStw),freshValue(selectedHeading),freshValue(freshSog),freshValue(freshCog),freshAws.receivedElapsedRealtime,freshAwa.receivedElapsedRealtime,externalTws.receivedElapsedRealtime,externalTwd.receivedElapsedRealtime,externalTwa.receivedElapsedRealtime,freshStw.receivedElapsedRealtime,selectedHeading.receivedElapsedRealtime,freshSog.receivedElapsedRealtime,freshCog.receivedElapsedRealtime)
        val derivedWind=if(preferredWind?.reference==TrueWindReference.EXTERNAL)TrueWindResolver.resolve(freshValue(freshAws),freshValue(freshAwa),null,null,null,freshValue(freshStw),freshValue(selectedHeading),freshValue(freshSog),freshValue(freshCog),freshAws.receivedElapsedRealtime,freshAwa.receivedElapsedRealtime,null,null,null,freshStw.receivedElapsedRealtime,selectedHeading.receivedElapsedRealtime,freshSog.receivedElapsedRealtime,freshCog.receivedElapsedRealtime)else preferredWind
        val resolvedWind=trueWindHysteresis.select(preferredWind,derivedWind,now)
        fun resolvedWindField(field:ResolvedWindField,value:Double?):VesselObservation<Double>{
            if(value==null)return when(field){ResolvedWindField.SPEED->externalTws;ResolvedWindField.DIRECTION->externalTwd;ResolvedWindField.ANGLE->externalTwa}
            val sourceClass=when(resolvedWind?.reference){TrueWindReference.EXTERNAL->VesselSourceClass.BOAT_NMEA;TrueWindReference.WATER->VesselSourceClass.DERIVED_WATER;TrueWindReference.GROUND->VesselSourceClass.DERIVED_GROUND;null->VesselSourceClass.NONE}
            val externalDirect=when(field){
                ResolvedWindField.SPEED->externalTws.takeIf{resolvedWind?.speedProvenance=="external true-wind speed"}
                ResolvedWindField.DIRECTION->externalTwd.takeIf{resolvedWind?.directionProvenance=="external true-wind direction"}
                ResolvedWindField.ANGLE->externalTwa.takeIf{resolvedWind?.angleProvenance=="external true-wind angle"}
            }
            val inputs=when(resolvedWind?.reference){
                TrueWindReference.EXTERNAL->when(field){
                    ResolvedWindField.SPEED->listOf(externalTws)
                    ResolvedWindField.DIRECTION->listOf(externalTwa,selectedHeading,externalTws)
                    ResolvedWindField.ANGLE->listOf(externalTwd,selectedHeading,externalTws)
                }
                TrueWindReference.WATER->listOf(freshAws,freshAwa,freshStw,selectedHeading)
                TrueWindReference.GROUND->listOf(freshAws,freshAwa,freshSog,freshCog,selectedHeading)
                null->emptyList()
            }.filter{it.value!=null&&it.freshness==VesselDataFreshness.FRESH}
            val fieldProvenance=when(field){ResolvedWindField.SPEED->resolvedWind?.speedProvenance;ResolvedWindField.DIRECTION->resolvedWind?.directionProvenance;ResolvedWindField.ANGLE->resolvedWind?.angleProvenance}?:resolvedWind?.provenance
            val received=inputs.mapNotNull{it.receivedElapsedRealtime}.minOrNull()?:externalDirect?.receivedElapsedRealtime?:now
            val detail=externalDirect?.provenanceDetail?:VesselProvenance.Derived(fieldProvenance?:"true wind resolver",inputs.mapNotNull{it.sourceIdentity}.distinctBy{it.id})
            return VesselObservation(value,sourceClass.toLegacySource(),receivedElapsedRealtime=received,quality=if(externalDirect!=null)VesselDataQuality.GOOD else VesselDataQuality.DEGRADED,freshness=VesselDataFreshness.FRESH,provenance=fieldProvenance,sourceIdentity=externalDirect?.sourceIdentity?:DERIVED_WIND_ID,sourceClass=sourceClass,reference=when(field){ResolvedWindField.DIRECTION->VesselReference.TrueNorth;ResolvedWindField.ANGLE->VesselReference.VesselRelative;ResolvedWindField.SPEED->when(resolvedWind?.reference){TrueWindReference.WATER->VesselReference.WaterReferenced;TrueWindReference.GROUND->VesselReference.GroundReferenced;else->null}},provenanceDetail=detail)
        }
        fun selectedTrueWind(metric:VesselMetricId,field:ResolvedWindField,direct:VesselObservation<Double>,value:Double?):VesselObservation<Double> {
            // 明确固定的外部读数失效时保留它自己的时效，不能用计算值或上一个来源顶替。
            if(metricSourcePins[metric.name]!=null){derivedDisplay.remove(metric);return direct}
            return retainDerived(metric,resolvedWindField(field,value))
        }
        val freshTwa=selectedTrueWind(VesselMetricId.TRUE_WIND_ANGLE,ResolvedWindField.ANGLE,externalTwa,resolvedWind?.angleDegrees)
        val freshTwd=selectedTrueWind(VesselMetricId.TRUE_WIND_DIRECTION,ResolvedWindField.DIRECTION,externalTwd,resolvedWind?.directionTrueDegrees)
        val freshTws=selectedTrueWind(VesselMetricId.TRUE_WIND_SPEED,ResolvedWindField.SPEED,externalTws,resolvedWind?.speedKnots)
        // Never combine a water-relative angle with ground-relative speed. Prefer
        // water VMG (STW/TWA); only fall back to a fully ground-referenced vector.
        val vmg=retainDerived(VesselMetricId.VMG_WIND,VmgReferencePolicy.calculate(freshValue(freshStw),freshValue(freshTwa),freshValue(freshSog),freshValue(freshCog),freshValue(freshTwd))?.let{result->derivedReading(result.knots,if(result.provenance.startsWith("STW"))listOf(freshStw,freshTwa)else listOf(freshSog,freshCog,freshTwd),result.provenance)}?:VesselObservation())
        val vmcValue=projectedSpeed(freshValue(freshSog),freshValue(freshCog)?.let{course->freshValue(freshWaypointBearing)?.let{bearing->signedAngle(course-bearing)}},now,"SOG × cos(COG-bearing)")
        val vmc=retainDerived(VesselMetricId.VMC_WAYPOINT,vmcValue.value?.let{derivedReading(it,listOf(freshSog,freshCog,freshWaypointBearing),"SOG × cos(COG-bearing)")}?:VesselObservation())
        val currentValues=estimateCurrent(freshValue(freshSog),freshValue(freshCog),freshValue(freshStw),freshValue(selectedHeading),now)
        val currentInputs=listOf(freshSog,freshCog,freshStw,selectedHeading)
        val estimatedCurrent=retainDerived(VesselMetricId.CURRENT_SET,currentValues?.first?.value?.let{derivedReading(it,currentInputs,"ground velocity minus water velocity: set")}?:VesselObservation()) to retainDerived(VesselMetricId.CURRENT_DRIFT,currentValues?.second?.value?.let{derivedReading(it,currentInputs,"ground velocity minus water velocity: drift")}?:VesselObservation())
        val selectedMagnetic=selectionObservation(registrySelection<Double>(VesselMetricId.HEADING_MAGNETIC,headingPreference,pinnedHeadingSourceId,now))?:VesselObservation<Double>()
        val deviceTrue=selectionObservation(registrySelection<Double>(VesselMetricId.DEVICE_HEADING_TRUE,VesselSourcePreference.PHONE,null,now))?:VesselObservation();val deviceMagnetic=selectionObservation(registrySelection<Double>(VesselMetricId.DEVICE_HEADING_MAGNETIC,VesselSourcePreference.PHONE,null,now))?:VesselObservation()
        val sourceSnapshot=sourceRegistry.snapshot.value
        fun selectedMetric(metric:VesselMetricId,legacy:VesselObservation<Double>,fresh:Long,held:Long)=selectionObservation(registrySelection<Double>(metric,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>()
        val sourceSelections=sourceSnapshot.keys.associateWith{metric->
            val preference=when(metric){VesselMetricId.POSITION->effectivePositionPreference;VesselMetricId.HEADING_TRUE,VesselMetricId.HEADING_MAGNETIC->headingPreference;else->VesselSourcePreference.AUTO}
            val pin=when(metric){VesselMetricId.POSITION->pinnedPositionSourceId;VesselMetricId.HEADING_TRUE,VesselMetricId.HEADING_MAGNETIC->pinnedHeadingSourceId;else->null}
            registrySelection<Any>(metric,preference,pin,now)
        }
        // 数据中心需要看到所有提供者；“本次采用谁”和“有哪些来源可选”不能混为同一份过滤列表。
        val evaluatedCandidates=sourceSelections.mapValues{(metric,_)->
            sourceSnapshot[metric].orEmpty().map{it.copy(validity=MetricSourceEligibility.evaluate(metric,it,now))}
        }
        val conflicts=sourceSelections.mapNotNull{(metric,selection)->selection.conflict.takeIf{it.active}?.let{metric to it}}.toMap()
        fun attitudeField(metric:VesselMetricId)=selectionObservation(registrySelection<Double>(metric,VesselSourcePreference.AUTO,null,now))?:VesselObservation<Double>()
        val heel=attitudeField(VesselMetricId.HEEL);val pitch=attitudeField(VesselMetricId.PITCH);val roll=attitudeField(VesselMetricId.ROLL_RATE);val pitchRate=attitudeField(VesselMetricId.PITCH_RATE);val yaw=attitudeField(VesselMetricId.YAW_RATE)
        _snapshot.value=VesselDataSnapshot(position=selectedPosition,sogKnots=freshSog,cogTrueDegrees=freshCog,headingTrueDegrees=selectedHeading,headingMagneticDegrees=selectedMagnetic,depthMeters=classifiedDepth,speedThroughWaterKnots=freshStw,trueWind=VesselWindObservation(freshTws,freshTwd,freshTwa),apparentWind=VesselWindObservation(freshAws,VesselObservation(),freshAwa),attitude=VesselDisplayObservationPolicy.attitude(heel,pitch,roll,pitchRate,yaw),motion=classify(motionValue,now,2_000,10_000),pressureHpa=classifiedPressure,rateOfTurnDegreesPerMinute=selectedMetric(VesselMetricId.RATE_OF_TURN,rateOfTurn,5_000,30_000),rudderAngleDegrees=selectedMetric(VesselMetricId.RUDDER_ANGLE,rudderAngle,5_000,30_000),waterTemperatureCelsius=selectedMetric(VesselMetricId.WATER_TEMPERATURE,waterTemperature,30_000,5*60_000),airTemperatureCelsius=selectedMetric(VesselMetricId.AIR_TEMPERATURE,airTemperature,30_000,5*60_000),currentSetTrueDegrees=selectedMetric(VesselMetricId.CURRENT_SET,currentSet,10_000,60_000),currentDriftKnots=selectedMetric(VesselMetricId.CURRENT_DRIFT,currentDrift,10_000,60_000),crossTrackErrorNauticalMiles=selectedMetric(VesselMetricId.XTE,crossTrackError,10_000,60_000),waypointBearingTrueDegrees=selectionObservation(registrySelection(VesselMetricId.WAYPOINT_BEARING,VesselSourcePreference.AUTO,null,now))?:freshWaypointBearing,waypointDistanceNauticalMiles=selectedMetric(VesselMetricId.WAYPOINT_DISTANCE,waypointDistance,10_000,60_000),destinationWaypoint=selectionObservation(registrySelection(VesselMetricId.DESTINATION_WAYPOINT,VesselSourcePreference.AUTO,null,now))?:VesselObservation<String>(),totalLogNauticalMiles=selectedMetric(VesselMetricId.TOTAL_LOG,totalLog,30_000,5*60_000),tripLogNauticalMiles=selectedMetric(VesselMetricId.TRIP_LOG,tripLog,30_000,5*60_000),derived=VesselDerivedSnapshot(underKeelClearanceMeters=ukc,headingCogDifferenceDegrees=headingCog,pressureTrend1hHpa=pressureTrend(60*60_000L),pressureTrend3hHpa=pressureTrend(3*60*60_000L),pressureTrend6hHpa=pressureTrend(6*60*60_000L),vmgToWindKnots=vmg,vmcToWaypointKnots=vmc,estimatedCurrentSetTrueDegrees=estimatedCurrent?.first?:VesselObservation(),estimatedCurrentDriftKnots=estimatedCurrent?.second?:VesselObservation()),deviceHeadingTrueDegrees=deviceTrue,deviceHeadingMagneticDegrees=deviceMagnetic,candidates=evaluatedCandidates,conflicts=conflicts,generatedElapsedRealtime=now,heelDegrees=heel,pitchDegrees=pitch,rollRateDegreesPerSecond=roll,pitchRateDegreesPerSecond=pitchRate,yawRateDegreesPerSecond=yaw)
    }
    @Synchronized private fun clearBoatGeneration(){boatPosition=VesselObservation();sog=VesselObservation();cog=VesselObservation();speedThroughWater=VesselObservation();boatHeading=VesselObservation();magneticHeading=VesselObservation();depthValue=VesselObservation();trueWindSpeed=VesselObservation();trueWindDirection=VesselObservation();trueWindAngle=VesselObservation();apparentWindSpeed=VesselObservation();apparentWindAngle=VesselObservation();rateOfTurn=VesselObservation();rudderAngle=VesselObservation();waterTemperature=VesselObservation();airTemperature=VesselObservation();currentSet=VesselObservation();currentDrift=VesselObservation();crossTrackError=VesselObservation();waypointBearing=VesselObservation();waypointDistance=VesselObservation();destinationWaypoint=VesselObservation();totalLog=VesselObservation();tripLog=VesselObservation();arbitrator.reset();trueWindHysteresis.reset()}
    @Synchronized private fun invalidateLegacyBoatFields(event:NmeaSourceInvalidation){event.affectedMetrics.forEach{metric->when(metric){
        VesselMetricId.POSITION->boatPosition=VesselObservation();VesselMetricId.SOG->sog=VesselObservation();VesselMetricId.COG->cog=VesselObservation();VesselMetricId.SPEED_THROUGH_WATER->speedThroughWater=VesselObservation();VesselMetricId.DEPTH->depthValue=VesselObservation();
        VesselMetricId.HEADING_TRUE->boatHeading=VesselObservation();VesselMetricId.HEADING_MAGNETIC->magneticHeading=VesselObservation();VesselMetricId.TRUE_WIND_SPEED->trueWindSpeed=VesselObservation();VesselMetricId.TRUE_WIND_DIRECTION->trueWindDirection=VesselObservation();VesselMetricId.TRUE_WIND_ANGLE->trueWindAngle=VesselObservation();VesselMetricId.APPARENT_WIND_SPEED->apparentWindSpeed=VesselObservation();VesselMetricId.APPARENT_WIND_ANGLE->apparentWindAngle=VesselObservation();
        VesselMetricId.RATE_OF_TURN->rateOfTurn=VesselObservation();VesselMetricId.RUDDER_ANGLE->rudderAngle=VesselObservation();VesselMetricId.XTE->crossTrackError=VesselObservation();VesselMetricId.WAYPOINT_BEARING->waypointBearing=VesselObservation();VesselMetricId.WAYPOINT_DISTANCE->waypointDistance=VesselObservation();VesselMetricId.DESTINATION_WAYPOINT->destinationWaypoint=VesselObservation();else->Unit
    }}}
    private fun <T> registrySelection(metric:VesselMetricId,preference:VesselSourcePreference,pinnedId:String?,now:Long):VesselSourceSelection<T>{
        val all=sourceRegistry.candidates<T>(metric)
        val boatPosition=metric==VesselMetricId.POSITION&&preference==VesselSourcePreference.BOAT
        val groundMotion=metric in setOf(VesselMetricId.SOG,VesselMetricId.COG)
        val explicitMetricPin=if(metric==VesselMetricId.POSITION)null else metricSourcePins[metric.name]
        // 自动航速/COG 跟随船位；用户明确指定某项读数后，以该项来源为准，不能被旧默认偏好覆盖。
        val effectivePreference=if(explicitMetricPin!=null)VesselSourcePreference.AUTO else if(groundMotion)when(shellPositionSource){GpsDataSource.SYSTEM->VesselSourcePreference.PHONE;GpsDataSource.NMEA->VesselSourcePreference.BOAT;else->preference}else preference
        val candidates=if(boatPosition||groundMotion&&explicitMetricPin==null&&shellPositionSource==GpsDataSource.NMEA)all.filter{it.source.transportProfileId==metricSourcePins["POSITION_CONNECTION"]}else all
        // The transport repository fixes the exact NMEA position identity. A
        // second arbiter must never silently choose another GPS for Trip/UI.
        val storedPin=if(boatPosition)navigation.positionSourcePin()?:"POSITION_NOT_SELECTED" else if(metric==VesselMetricId.POSITION)null else metricSourcePins[metric.name]?:pinnedId
        val resolvedPin=storedPin?.let{stored->VesselSourcePinPolicy.resolve(candidates,stored)?:stored}
        return arbitrator.select(metric,candidates,MetricSourcePreference(effectivePreference,resolvedPin,if(metric==VesselMetricId.POSITION||explicitMetricPin!=null)false else allowPinnedFallback,connectionPriorities()),now)
    }
    @Suppress("UNCHECKED_CAST") private fun <T> selectionObservation(selection:VesselSourceSelection<T>):VesselObservation<T>? {
        val metric=selection.selected?.metric?:selection.candidates.firstOrNull()?.metric?:return null
        val pin=metricSourcePins[metric.name]
        val previous=(displayObservations[metric] as? VesselObservation<T>)?.takeIf { observation ->
            pin==null || observation.sourceIdentity?.let{VesselSourcePinPolicy.matches(it,pin)}==true
        }
        val value=VesselDisplayObservationPolicy.resolve(selection,previous,SystemClock.elapsedRealtime())
        if(value!=null)displayObservations[metric]=value else displayObservations.remove(metric)
        return value
    }
    private fun <T> classify(value:VesselObservation<T>,now:Long,fresh:Long,held:Long)=VesselFreshnessPolicy.classify(value,now,fresh,held)
    private fun retainDerived(metric:VesselMetricId,next:VesselObservation<Double>):VesselObservation<Double>{
        if(next.value!=null){derivedDisplay[metric]=next;return next}
        val previous=derivedDisplay[metric]?:return next
        val current=sourceRegistry.snapshot.value.values.flatten().filter{it.explicitValidity !in setOf(CandidateValidity.INVALID,CandidateValidity.DISABLED)}.mapTo(mutableSetOf()){it.source.id}
        val retained=VesselDisplayObservationPolicy.retainDerived(previous,next,current)
        if(retained.value==null)derivedDisplay.remove(metric)
        return retained
    }
    private fun derivedReading(value:Double,inputs:List<VesselObservation<Double>>,algorithm:String):VesselObservation<Double>{
        val identity=VesselSourceIdentity("derived:$algorithm:${inputs.mapNotNull{it.sourceIdentity?.id}.joinToString("|")}",sourceType=VesselSourceType.APP_DERIVED,displayName=algorithm)
        return VesselObservation(value,VesselDataSource.DERIVED,observedAtUtcMillis=inputs.minByOrNull { it.receivedElapsedRealtime?:Long.MAX_VALUE }?.observedAtUtcMillis,receivedElapsedRealtime=inputs.mapNotNull{it.receivedElapsedRealtime}.minOrNull(),
            quality=if(inputs.all{it.quality==VesselDataQuality.GOOD})VesselDataQuality.GOOD else VesselDataQuality.DEGRADED,
            freshness=if(inputs.all{it.freshness==VesselDataFreshness.FRESH})VesselDataFreshness.FRESH else VesselDataFreshness.STALE,
            provenance=algorithm,sourceIdentity=identity,provenanceDetail=VesselProvenance.Derived(algorithm,inputs.flatMap{(it.provenanceDetail as? VesselProvenance.Derived)?.inputs?:listOfNotNull(it.sourceIdentity)}.distinctBy{it.id}))
    }
    private fun <T> observation(value:T,source:VesselDataSource,received:Long?,observed:Long?,provenance:String?,quality:VesselDataQuality=VesselDataQuality.GOOD)=VesselObservation(value,source,observed,received,quality,VesselDataFreshness.FRESH,provenance)
    private fun <T> fieldObservation(field:NmeaFieldObservation,value:T)=observation(value,VesselDataSource.BOAT_NMEA,field.receivedElapsedRealtime,null,"${field.key.sentenceType}:${field.key.fieldIndex}${field.key.transducerName?.let{":$it"}.orEmpty()}")
    private fun projectedSpeed(speed:Double?,angle:Double?,now:Long,provenance:String):VesselObservation<Double>{if(speed==null||angle==null)return VesselObservation();return VesselObservation(speed*cos(Math.toRadians(angle)),VesselDataSource.DERIVED,receivedElapsedRealtime=now,quality=VesselDataQuality.GOOD,freshness=VesselDataFreshness.FRESH,provenance=provenance)}
    private fun signedAngle(value:Double)=((value+540.0)%360.0)-180.0
    private fun estimateCurrent(sog:Double?,cog:Double?,stw:Double?,heading:Double?,now:Long):Pair<VesselObservation<Double>,VesselObservation<Double>>?{if(sog==null||cog==null||stw==null||heading==null)return null;val groundEast=sog*sin(Math.toRadians(cog));val groundNorth=sog*cos(Math.toRadians(cog));val waterEast=stw*sin(Math.toRadians(heading));val waterNorth=stw*cos(Math.toRadians(heading));val east=groundEast-waterEast;val north=groundNorth-waterNorth;val drift=hypot(east,north);val set=(Math.toDegrees(atan2(east,north))+360.0)%360.0;val base=VesselObservation(set,VesselDataSource.DERIVED,receivedElapsedRealtime=now,quality=VesselDataQuality.DEGRADED,freshness=VesselDataFreshness.FRESH,provenance="ground velocity minus through-water velocity");return base to base.copy(value=drift)}
    private fun com.yokuli.anchorwatch.data.NmeaInstrumentState.sogOrNull()=speedOverGroundKnots?.let{observation(it.first,VesselDataSource.BOAT_NMEA,it.second,null,"NMEA SOG")}
    private fun com.yokuli.anchorwatch.data.NmeaInstrumentState.cogOrNull()=courseOverGroundTrue?.let{observation(it.first,VesselDataSource.BOAT_NMEA,it.second,null,"NMEA COG")}
    private fun com.yokuli.anchorwatch.data.NmeaInstrumentState.stwOrNull()=speedThroughWaterKnots?.let{observation(it.first,VesselDataSource.BOAT_NMEA,it.second,null,"VHW")}
    private fun com.yokuli.anchorwatch.data.NmeaInstrumentState.trueHeadingOrNull()=headingTrue?.let{observation(it.first,VesselDataSource.BOAT_NMEA,it.second,null,selectedHeadingSourceId?:"HDT/HDG/VHW",if(headingConflict)VesselDataQuality.DEGRADED else VesselDataQuality.GOOD)}
    private fun com.yokuli.anchorwatch.data.NmeaInstrumentState.magneticHeadingOrNull()=headingMagnetic?.let{observation(it.first,VesselDataSource.BOAT_NMEA,it.second,null,selectedHeadingSourceId?:"HDM/HDG/VHW",if(headingConflict)VesselDataQuality.DEGRADED else VesselDataQuality.GOOD)}
    private fun normalize(value:Double)=(value%360.0+360.0)%360.0
    private companion object{
        val PHONE_GNSS_ID=VesselSourceIdentity("phone:gnss",sourceType=VesselSourceType.PHONE_SENSOR,phoneSensorType="GNSS",displayName="Phone GNSS")
        val PHONE_DEVICE_HEADING_ID=VesselSourceIdentity("phone:device-heading",sourceType=VesselSourceType.PHONE_SENSOR,phoneSensorType="DEVICE_COMPASS",displayName="Phone device compass")
        val PHONE_VESSEL_HEADING_ID=VesselSourceIdentity("phone:vessel-heading",sourceType=VesselSourceType.PHONE_SENSOR,phoneSensorType="VESSEL_COMPASS",displayName="Phone vessel compass")
        val PHONE_IMU_ID=VesselSourceIdentity("phone:vessel-imu",sourceType=VesselSourceType.PHONE_SENSOR,phoneSensorType="VESSEL_IMU",displayName="Phone vessel IMU")
        val PHONE_PRESSURE_ID=VesselSourceIdentity("phone:barometer",sourceType=VesselSourceType.PHONE_SENSOR,phoneSensorType="PRESSURE",displayName="Phone barometer")
        val DERIVED_WIND_ID=VesselSourceIdentity("derived:true-wind",sourceType=VesselSourceType.APP_DERIVED,displayName="App true-wind resolver")
    }
}

package com.yokuli.runtime.marine.history

import android.content.Context
import android.os.SystemClock
import com.yokuli.anchorwatch.api.*
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.anchorwatch.api.withNavigation
import com.yokuli.runtime.marine.navigation.LocalNavigationSessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** 所有指标的唯一短历史作者，运行在Marine Core；无Activity订阅时继续采录。 */
@Singleton
class LocalReadingHistoryService @Inject constructor(
    @ApplicationContext context:Context,
    private val marine:LocalMarineServices,
    private val navigation:LocalNavigationSessionService,
):ReadingHistoryService {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val historySession=UUID.randomUUID().toString()
    private val cache=ReadingHistoryCache(context.applicationContext)
    private val retry=Channel<CompletableDeferred<Boolean>>(Channel.UNLIMITED)
    private val mutable=MutableStateFlow(ReadingHistorySnapshot(generation=historySession,storage=HistoryStorageState(loading=true)))
    override val state=mutable.asStateFlow()
    private val traces=MutableStateFlow<Map<String,List<Reading>>>(emptyMap())
    private var previousReadings:Map<String,Reading> = emptyMap()
    private var utcOffset=System.currentTimeMillis()-SystemClock.elapsedRealtime()
    private var clockEpoch=0L
    private data class Projection(val vessel:VesselDataSnapshot,val draft:Double?)
    init {
        scope.launch(Dispatchers.IO) {
            fun restore():Boolean {
                mutable.update {it.copy(storage=it.storage.copy(loading=true))}
                return try {
                    val restored=cache.read(SystemClock.elapsedRealtime())
                    mergeRestored(restored.readings)
                    mutable.update {it.copy(storage=it.storage.copy(loading=false,readIssue=null,lastSavedUtcMillis=restored.savedAtUtc))}
                    true
                }catch(cancelled:CancellationException){throw cancelled}
                catch(error:Exception){mutable.update {it.copy(storage=it.storage.copy(loading=false,readIssue=error.message?:"HISTORY_READ_FAILED"))};false}
            }
            var loaded=restore()
            var saved:Map<String,List<Reading>>?=null
            while(isActive){
                val request=withTimeoutOrNull(5_000L){retry.receive()}
                if(!loaded)loaded=restore()
                val snapshot=traces.value
                var persisted=loaded
                if(loaded&&snapshot!==saved){
                    try {
                        val utc=System.currentTimeMillis()
                        cache.write(snapshot,utc);saved=snapshot
                        mutable.update {it.copy(storage=it.storage.copy(writeIssue=null,lastSavedUtcMillis=utc,pending=traces.value!==snapshot))}
                    }catch(cancelled:CancellationException){request?.cancel(cancelled);throw cancelled}
                    catch(error:Exception){mutable.update{it.copy(storage=it.storage.copy(writeIssue=error.message?:"HISTORY_WRITE_FAILED",pending=true))};persisted=false}
                }
                request?.complete(persisted)
            }
        }
        scope.launch {
            // 指标投影2Hz足够记录真实500ms样本；高频姿态仍由原始VesselData流供场景插值。
            combine(marine.state,navigation.state){snapshot,guide->
                Projection(snapshot.vesselData.withNavigation(guide).copy(candidates=emptyMap(),conflicts=emptyMap(),generatedElapsedRealtime=0),snapshot.vesselSettings.draftMeters)
            }.sample(500L).distinctUntilChanged().collect { projection->
                adopt(projectReadings(projection.vessel,projection.draft))
            }
        }
    }
    override fun retryStorage(){retry.trySend(CompletableDeferred())}
    override suspend fun flush():Boolean{
        val receipt=CompletableDeferred<Boolean>()
        retry.send(receipt)
        return withTimeoutOrNull(10_000){receipt.await()}?:false
    }
    override suspend fun slice(metric:String,afterElapsed:Long?):ReadingHistorySlice {
        require(metric.length<=128){"INVALID_METRIC"}
        val now=SystemClock.elapsedRealtime()
        val readings=traces.value[metric].orEmpty().filter {it.elapsed in (now-ReadingHistoryCache.WINDOW_MILLIS).coerceAtLeast(0)..now && (afterElapsed==null||it.elapsed>=afterElapsed)}
        return ReadingHistorySlice(metric,historySession,mutable.value.revision,readings)
    }
    override suspend fun slices(afterElapsed:Map<String,Long?>):List<ReadingHistorySlice> {
        require(afterElapsed.size<=64){"HISTORY_BATCH_TOO_WIDE"}
        var points=0
        return afterElapsed.map{(metric,after)->slice(metric,after).also{points+=it.readings.size;require(points<=4000){"HISTORY_BATCH_TOO_LARGE"}}}
    }
    @Synchronized private fun mergeRestored(restored:Map<String,List<Reading>>){
        val now=SystemClock.elapsedRealtime()
        traces.update {current->
            (restored.keys+current.keys).take(ReadingHistoryCache.MAX_METRICS).associateWith {key->
                (restored[key].orEmpty()+current[key].orEmpty()).filter {it.elapsed in (now-ReadingHistoryCache.WINDOW_MILLIS).coerceAtLeast(0)..now}
                    .associateBy {listOf(it.elapsed,it.sourceKey,it.continuityKey,it.unit,it.value)}.values.sortedBy {it.elapsed}.takeLast(ReadingHistoryCache.MAX_POINTS)
            }.filterValues {it.isNotEmpty()}
        }
        mutable.update {it.copy(revision=it.revision+1,metrics=traces.value.keys.toList())}
    }
    @Synchronized private fun adopt(readings:Map<String,Reading>){
        val now=SystemClock.elapsedRealtime()
        val before=traces.value
        traces.update {previous->
            var changed:MutableMap<String,List<Reading>>?=null
            (previous.keys+readings.keys).take(ReadingHistoryCache.MAX_METRICS).forEach {key->
                val original=previous[key].orEmpty()
                var values=if(original.firstOrNull()?.let {now-it.elapsed>ReadingHistoryCache.WINDOW_MILLIS}==true)original.dropWhile {now-it.elapsed>ReadingHistoryCache.WINDOW_MILLIS}else original
                readings[key]?.takeIf {it.fresh(now)&&it.value.isFinite()}?.let {value->
                    val last=values.lastOrNull()
                    val boundary=last!=null&&(last.sourceKey!=value.sourceKey||last.continuityKey!=value.continuityKey||last.historySessionKey!=historySession)
                    if(last==null||value.elapsed-last.elapsed>=500L||(boundary&&value.elapsed>=last.elapsed&&!(value.elapsed==last.elapsed&&last.sourceKey==value.sourceKey&&last.continuityKey==value.continuityKey)))
                        values=(if(values.size>=1800)values.takeLast(1799)else values)+value.copy(historySessionKey=historySession,
                            observedUtcMillis=value.observedUtcMillis?:System.currentTimeMillis()-(now-value.elapsed))
                }
                if(values!==original){val target=changed?:previous.toMutableMap().also {changed=it};if(values.isEmpty())target.remove(key)else target[key]=values}
            }
            changed?:previous
        }
        mutable.update {it.copy(readings=readings,metrics=traces.value.keys.toList(),revision=if(traces.value!==before)it.revision+1 else it.revision,
            storage=if(traces.value!==before)it.storage.copy(pending=true)else it.storage)}
    }
    private fun projectReadings(vessel:VesselDataSnapshot,draft:Double?):Map<String,Reading> {
        val offset=System.currentTimeMillis()-SystemClock.elapsedRealtime()
        if(abs(offset-utcOffset)>2_000L) { utcOffset=offset;clockEpoch++ }
        val readings = buildMap {
            fun add(key:String,value:VesselObservation<Double>,unit:String,metric:VesselMetricId) {
                val number=value.value?.takeIf{it.isFinite()}?:return
                val time=value.receivedElapsedRealtime?:return
                val sourceKey=value.sourceIdentity?.id?:value.provenanceDetail?.toString()?:value.source.name
                val basis="$sourceKey|${value.reference}|${value.provenanceDetail}" + if(key=="ukc")"|draft:$draft" else ""
                val previous=previousReadings[key]?.takeIf {
                    it.elapsed==time&&it.sourceKey==sourceKey&&it.continuityKey.substringBeforeLast("|clock:")==basis
                }
                put(key,Reading(number,unit,value.provenance?:value.source.name,time,value.freshness,value.quality,
                    sourceKey,MetricSourceEligibility.measurementLeaseMillis(metric),
                    previous?.continuityKey?:"$basis|clock:$clockEpoch",
                    previous?.observedUtcMillis?:value.observedAtUtcMillis?:time+utcOffset))
            }
            with(vessel) {
                add("sog",sogKnots,"kn",VesselMetricId.SOG);add("cog",cogTrueDegrees,"°T",VesselMetricId.COG)
                add("heading",headingTrueDegrees,"°T",VesselMetricId.HEADING_TRUE)
                add("depth",depthMeters,"m",VesselMetricId.DEPTH);add("ukc",derived.underKeelClearanceMeters,"m",VesselMetricId.DEPTH)
                add("aws",apparentWind.speedKnots,"kn",VesselMetricId.APPARENT_WIND_SPEED)
                add("awa",apparentWind.angleDegrees,"°",VesselMetricId.APPARENT_WIND_ANGLE)
                add("tws",trueWind.speedKnots,"kn",VesselMetricId.TRUE_WIND_SPEED)
                add("twa",trueWind.angleDegrees,"°",VesselMetricId.TRUE_WIND_ANGLE)
                add("twd",trueWind.directionDegrees,"°T",VesselMetricId.TRUE_WIND_DIRECTION)
                add("bsp",speedThroughWaterKnots,"kn",VesselMetricId.SPEED_THROUGH_WATER)
                add("water",waterTemperatureCelsius,"°C",VesselMetricId.WATER_TEMPERATURE)
                add("air",airTemperatureCelsius,"°C",VesselMetricId.AIR_TEMPERATURE)
                add("pressure",pressureHpa,"hPa",VesselMetricId.PRESSURE)
                add("pressure_1h",derived.pressureTrend1hHpa,"hPa",VesselMetricId.PRESSURE)
                add("pressure_3h",derived.pressureTrend3hHpa,"hPa",VesselMetricId.PRESSURE)
                add("pressure_6h",derived.pressureTrend6hHpa,"hPa",VesselMetricId.PRESSURE)
                add("heel",heelDegrees,"°",VesselMetricId.HEEL);add("pitch",pitchDegrees,"°",VesselMetricId.PITCH)
                add("roll_rate",rollRateDegreesPerSecond,"°/s",VesselMetricId.ROLL_RATE)
                add("pitch_rate",pitchRateDegreesPerSecond,"°/s",VesselMetricId.PITCH_RATE)
                add("rot",rateOfTurnDegreesPerMinute,"°/min",VesselMetricId.RATE_OF_TURN)
                add("rudder",rudderAngleDegrees,"°",VesselMetricId.RUDDER_ANGLE)
                add("vmg",derived.vmgToWindKnots,"kn",VesselMetricId.VMG_WIND)
                add("vmc",derived.vmcToWaypointKnots,"kn",VesselMetricId.VMC_WAYPOINT)
                add("current_set",currentSetTrueDegrees,"°T",VesselMetricId.CURRENT_SET)
                add("current_drift",currentDriftKnots,"kn",VesselMetricId.CURRENT_DRIFT)
                add("xte",crossTrackErrorNauticalMiles,"nm",VesselMetricId.XTE)
                add("waypoint_bearing",waypointBearingTrueDegrees,"°T",VesselMetricId.WAYPOINT_BEARING)
                add("waypoint_distance",waypointDistanceNauticalMiles,"nm",VesselMetricId.WAYPOINT_DISTANCE)
                add("total_log",totalLogNauticalMiles,"nm",VesselMetricId.TOTAL_LOG)
                add("trip_log",tripLogNauticalMiles,"nm",VesselMetricId.TRIP_LOG)
                fun motionReading(value:Double?)=VesselObservation(value,motion.source,motion.observedAtUtcMillis,motion.receivedElapsedRealtime,motion.quality,motion.freshness,motion.provenance,motion.sourceIdentity,motion.sourceClass,provenanceDetail=motion.provenanceDetail)
                add("roll_period",motionReading(motion.value?.dominantRollPeriodSeconds),"s",VesselMetricId.ROLL_PERIOD)
                add("motion",motionReading(motion.value?.score),"",VesselMetricId.MOTION_SCORE)
                add("impacts",motionReading(motion.value?.impactCandidateCount?.toDouble()),"",VesselMetricId.MOTION_SCORE)
            }
        }
        previousReadings=readings
        return readings
    }

}

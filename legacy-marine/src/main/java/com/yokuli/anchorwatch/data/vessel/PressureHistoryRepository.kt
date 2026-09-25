package com.yokuli.anchorwatch.data.vessel

import com.yokuli.anchorwatch.data.database.PressureHistoryDao
import com.yokuli.anchorwatch.data.database.PressureHistoryEntity
import com.yokuli.anchorwatch.domain.vessel.PressureTrend
import com.yokuli.anchorwatch.domain.vessel.PressureTrendEstimator
import com.yokuli.anchorwatch.runtime.MonotonicClock
import com.yokuli.anchorwatch.runtime.SystemMonotonicClock
import com.yokuli.anchorwatch.runtime.WallClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PressureHistoryPolicy {
    const val BUCKET_MILLIS=60_000L
    const val TREND_RETENTION_MILLIS=6*60*60_000L
    const val DATABASE_RETENTION_MILLIS=30L*24*60*60_000L
    fun bucket(utcMillis:Long)=Math.floorDiv(utcMillis,BUCKET_MILLIS)
    fun validPressure(value:Double)=value.isFinite()&&value in 800.0..1_200.0
}

/** 每个真实来源每 UTC 分钟保留首个实际测量；存储和趋势共用同一份不可变观测。 */
@Singleton
class PressureHistoryRepository @Inject constructor(
    private val dao:PressureHistoryDao,
    private val wallClock:WallClock,
    private val monotonicClock:MonotonicClock=SystemMonotonicClock,
) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    // 一分钟只排一条；写入失败保留原对象重试，不给旧数据重新盖时间戳。
    private val databaseWrites=Channel<PressureHistoryEntity>(2_048)
    private val lastMeasurements=linkedMapOf<String,Long>()
    private val reservedMinutes=linkedMapOf<String,Long>()
    private val estimators=linkedMapOf<String,PressureTrendEstimator>()
    private val latestStored=linkedMapOf<String,PressureHistoryEntity>()
    private val processKey=UUID.randomUUID().toString()
    private var clockEpoch=0L
    private var utcOffset=wallClock.currentTimeMillis()-monotonicClock.elapsedRealtime()
    private val _historyLoaded=MutableStateFlow(false)
    val historyLoaded=_historyLoaded.asStateFlow()
    /** 读取/落盘失败的实际原因。读页面显示此状态，不能把等待落盘的样本称为已保存。 */
    private val _storageIssue=MutableStateFlow<String?>(null)
    val storageIssue=_storageIssue.asStateFlow()
    private var writeIssue:String?=null
    private val readIssues=mutableMapOf<String,String>()

    @Synchronized fun historyReadFailed(query:String,error:Throwable) {
        readIssues[query]=error.message?.take(180)?:error.javaClass.simpleName
        _storageIssue.value=writeIssue?:readIssues.values.firstOrNull()
    }
    @Synchronized fun historyReadRecovered(query:String) {
        readIssues.remove(query)
        _storageIssue.value=writeIssue?:readIssues.values.firstOrNull()
    }
    @Synchronized private fun writeIssue(value:String?) {
        writeIssue=value
        _storageIssue.value=value?:readIssues.values.firstOrNull()
    }

    init {
        scope.launch {
            val rows=retryStorage {
                dao.since(wallClock.currentTimeMillis()-PressureHistoryPolicy.TREND_RETENTION_MILLIS-PressureHistoryPolicy.BUCKET_MILLIS)
            }
            synchronized(this@PressureHistoryRepository) {
                rows.forEach(::addToEstimator)
                _historyLoaded.value=true
            }
            var lastPrunedAt=0L
            for(value in databaseWrites) {
                val stored=retryStorage {
                    dao.insertObservation(value)
                    // 重启后同分钟已有记录时，采用数据库首个观测，绝不改写为本次值。
                    checkNotNull(dao.observation(value.sourceStableKey,value.bucketUtcMinute)) { "Pressure observation was not saved" }
                }
                synchronized(this@PressureHistoryRepository) { addToEstimator(stored) }
                val now=wallClock.currentTimeMillis()
                if(now-lastPrunedAt>=24*60*60_000L) {
                    retryStorage { dao.prune(now-PressureHistoryPolicy.DATABASE_RETENTION_MILLIS) }
                    lastPrunedAt=now
                }
            }
        }
    }

    @Synchronized fun record(
        sourceStableKey:String,
        sourceDisplayName:String,
        pressureHpa:Double,
        measuredElapsedRealtime:Long,
        continuityKey:String=sourceStableKey,
        observedAtUtcMillis:Long?=null,
    ) {
        val key=sourceStableKey.trim()
        val nowElapsed=monotonicClock.elapsedRealtime()
        if(key.isEmpty()||!PressureHistoryPolicy.validPressure(pressureHpa)||measuredElapsedRealtime<=0||measuredElapsedRealtime>nowElapsed)return
        val measurementKey="$key|$continuityKey"
        // NMEA 字段列表因其他报文更新时会重新发布旧字段；接收心跳不是新测量。
        if(lastMeasurements[measurementKey]?.let { measuredElapsedRealtime<=it }==true)return
        val currentOffset=wallClock.currentTimeMillis()-nowElapsed
        if(abs(currentOffset-utcOffset)>2_000L) {
            utcOffset=currentOffset
            clockEpoch++ // 系统时间校正仅影响之后的观测；旧点永不重新定位。
        }
        val observedUtc=observedAtUtcMillis?:measuredElapsedRealtime+utcOffset
        if(observedUtc<=0)return
        val minute=PressureHistoryPolicy.bucket(observedUtc)
        if(reservedMinutes[key]==minute) {
            rememberMeasurement(measurementKey,measuredElapsedRealtime)
            return
        }
        val value=PressureHistoryEntity(key,minute,observedUtc,pressureHpa,sourceDisplayName.ifBlank {key},
            "$processKey|$clockEpoch|$continuityKey",measuredElapsedRealtime)
        if(databaseWrites.trySend(value).isFailure) {
            writeIssue("Pressure history storage is waiting; the pending buffer is full")
            return
        }
        rememberMeasurement(measurementKey,measuredElapsedRealtime)
        reservedMinutes[key]=minute
        while(reservedMinutes.size>512)reservedMinutes.remove(reservedMinutes.keys.first())
    }

    @Synchronized fun trend(sourceStableKey:String,windowMillis:Long,continuityKey:String?=null):PressureTrend? {
        if(!_historyLoaded.value)return null
        val point=latestStored[sourceStableKey]?:return null
        val segment=point.continuityKey?:return null
        if(!segment.startsWith("$processKey|$clockEpoch|"))return null
        if(continuityKey!=null&&!segment.endsWith("|$continuityKey"))return null
        // 以真实末点为计算截止时间；没有新观测时不在后台滑动回归窗口。
        return estimators[sourceStableKey]?.trend(point.sampledAtUtcMillis,windowMillis)?.copy(
            observedAtUtcMillis=point.sampledAtUtcMillis,
            measuredElapsedRealtime=point.measuredElapsedRealtime,
            continuityKey=segment,
        )
    }

    private fun rememberMeasurement(key:String,elapsed:Long) {
        lastMeasurements.remove(key)
        lastMeasurements[key]=elapsed
        while(lastMeasurements.size>512)lastMeasurements.remove(lastMeasurements.keys.first())
    }

    private fun addToEstimator(value:PressureHistoryEntity) {
        val previous=latestStored[value.sourceStableKey]
        if(previous==value)return
        if(previous!=null&&previous.continuityKey==value.continuityKey&&previous.sampledAtUtcMillis>=value.sampledAtUtcMillis)return
        if(previous?.continuityKey!=value.continuityKey||value.continuityKey==null)
            estimators.remove(value.sourceStableKey)
        latestStored[value.sourceStableKey]=value
        if(value.continuityKey!=null)
            estimators.getOrPut(value.sourceStableKey) { PressureTrendEstimator() }.add(value.sampledAtUtcMillis,value.pressureHpa)
        while(latestStored.size>512) {
            val key=latestStored.keys.first()
            latestStored.remove(key);estimators.remove(key)
        }
    }

    private suspend fun <T> retryStorage(block:suspend ()->T):T {
        while(true) {
            try {
                val result=block()
                writeIssue(null)
                return result
            } catch(cancelled:CancellationException) {
                throw cancelled
            } catch(error:Exception) {
                writeIssue(error.message?.take(180)?:error.javaClass.simpleName)
                delay(2_000)
            }
        }
    }
}

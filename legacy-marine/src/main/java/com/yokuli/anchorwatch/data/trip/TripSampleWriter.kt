package com.yokuli.anchorwatch.data.trip

import com.yokuli.anchorwatch.data.database.TripDao
import com.yokuli.anchorwatch.data.database.TripSampleEntity
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.yokuli.anchorwatch.runtime.DurableRuntimeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class TripWriterResult(val written:Int=0,val dropped:Long=0,val writeFailed:Boolean=false,val persistedValues:List<TripSampleEntity> = emptyList())

internal data class PendingTripBatch(val values:List<TripSampleEntity>,val dropped:Long)

/** Thread-safe, bounded queue kept separate so the failure/requeue policy is unit-testable. */
internal class TripSampleBuffer(private val capacity:Int){
    private val queue=ArrayDeque<TripSampleEntity>();private var dropped=0L

    @Synchronized fun enqueue(value:TripSampleEntity):Boolean{
        var overflow=false
        if(queue.size>=capacity){queue.removeFirst();dropped++;overflow=true}
        queue.addLast(value)
        return overflow
    }

    @Synchronized fun size()=queue.size

    @Synchronized fun take():PendingTripBatch{
        val batch=PendingTripBatch(queue.toList(),dropped)
        queue.clear();dropped=0
        return batch
    }

    @Synchronized fun restore(batch:PendingTripBatch){
        // Samples may have arrived while Room was writing. Restore the failed
        // batch in timestamp order, then keep the newest bounded window.
        val combined=ArrayDeque<TripSampleEntity>(batch.values.size+queue.size)
        batch.values.forEach(combined::addLast)
        queue.forEach(combined::addLast)
        var overflow=0L
        while(combined.size>capacity){combined.removeFirst();overflow++}
        queue.clear();combined.forEach(queue::addLast)
        dropped+=batch.dropped+overflow
    }
}

/**
 * 先写有界磁盘尾日志，再发布为实时轨迹；Room 批量提交按已有序号唯一索引去重。
 * Core 在 Room 提交后、清尾日志前被杀也只会重复确认同一批点。满盘不静默丢数据。
 */
@Singleton
class TripSampleWriter @Inject constructor(private val dao:TripDao,@ApplicationContext context:Context){
    private data class Document(val version:Int=1,val samples:List<TripSampleEntity> = emptyList(),val checkpoint:TripSessionEntity?=null)
    private val mutex=Mutex()
    private val disk=DurableRuntimeFile(File(context.filesDir,"marine-core"),"recording-tail.json",Document::class.java)
    private var readFailure:Throwable?=null
    private fun validate(value:Document) {
        require(value.version==1&&value.samples.size<=MAX_QUEUE){"RECORDING_TAIL_INVALID"}
        require(value.samples.all{it.recordingSequence>0&&it.tripId>0}){"RECORDING_TAIL_SEQUENCE_INVALID"}
        require(value.samples.map{it.tripId to it.recordingSequence}.toSet().size==value.samples.size){"RECORDING_TAIL_DUPLICATE_SEQUENCE"}
        require(value.samples.isEmpty()||value.checkpoint!=null&&value.samples.all{it.tripId==value.checkpoint.id}){"RECORDING_TAIL_SESSION_MISMATCH"}
    }
    @Volatile private var document=try {
        disk.read{Document()}.also(::validate)
    }catch(error:Exception){readFailure=error;Document()}

    suspend fun retryStorage()=mutex.withLock {
        if(readFailure==null)return@withLock
        val restored=withContext(Dispatchers.IO){disk.read{Document()}.also(::validate)}
        document=restored;readFailure=null
    }
    suspend fun enqueue(value:TripSampleEntity,checkpoint:TripSessionEntity):Boolean=mutex.withLock {
        readFailure?.let{throw IllegalStateException("RECORDING_TAIL_UNAVAILABLE",it)}
        check(document.samples.size<MAX_QUEUE){"RECORDING_STORAGE_FULL"}
        require(value.recordingSequence>0&&value.tripId==checkpoint.id){"INVALID_RECORDING_SAMPLE"}
        val next=Document(samples=document.samples+value,checkpoint=checkpoint)
        withContext(NonCancellable+Dispatchers.IO){disk.write(next)}
        document=next
        false
    }
    fun size()=document.samples.size
    suspend fun flush():TripWriterResult=mutex.withLock{flushLocked()}
    private suspend fun flushLocked():TripWriterResult {
        if(readFailure!=null)return TripWriterResult(writeFailed=true)
        val batch=document
        if(batch.samples.isEmpty())return TripWriterResult()
        return try {
            withContext(NonCancellable+Dispatchers.IO){
                dao.commitRecordingBatch(batch.samples,batch.checkpoint)
                disk.write(Document())
            }
            document=Document()
            TripWriterResult(written=batch.samples.size,persistedValues=batch.samples)
        }catch(failure:Exception){
            if(failure is CancellationException)throw failure
            TripWriterResult(writeFailed=true)
        }
    }
    companion object{const val MAX_QUEUE=120;const val FLUSH_SIZE=20;const val FLUSH_MILLIS=5_000L;const val MIN_FLUSH_RETRY_MILLIS=1_000L}
}

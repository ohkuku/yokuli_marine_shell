package com.yokuli.anchorwatch.runtime.output

import android.os.SystemClock
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import javax.inject.Singleton

/** 每个发送目标独立有界队列；慢设备不会阻塞其他连接，也不会伪造写出计数。 */
@Singleton class MultiNmeaPublisher @Inject constructor(
    private val navigation: NavigationRepository,
    private val encoder: NmeaPublicationEncoder,
    private val resources: RuntimeResourceManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Batch(val epoch: Long, val sentences: List<String>,val sourceEpochs:Map<String,Long>)
    private data class Writer(val queue: Channel<Batch>, val job: Job)
    private val writers = mutableMapOf<String, Writer>()
    private val guard = Any()
    init {
        scope.launch { navigation.connections.collect { connections ->
            val sending = connections.filter { it.requested && it.spec.send }
            val ids = sending.mapTo(mutableSetOf()) { it.spec.id }
            synchronized(guard) {
                writers.keys.toList().filter { it !in ids }.forEach { writers.remove(it)?.let { writer -> writer.queue.close(); writer.job.cancel() } }
                ids.forEach { id -> if (id !in writers) {
                    val channel = Channel<Batch>(64)
                    val job = scope.launch { for (batch in channel) {
                        if(navigation.sourcesCurrent(batch.sourceEpochs))navigation.writeConnection(id,batch.epoch,batch.sentences,batch.sourceEpochs)
                        else navigation.recordDroppedOutput(id,batch.sentences.size)
                    } }
                    writers[id] = Writer(channel, job)
                } }
            }
            val generated = sending.filter { it.spec.feed != NmeaFeed.RAW }.flatMap { NmeaPublicationPolicy.selected(it.spec) }.toSet()
            resources.set(RuntimeOwner.NMEA_CONNECTIONS, if (connections.any { it.requested }) RuntimeRequirement(needsWakeLock = true, needsWifiLock = true, needsSystemLocation = "position" in generated, needsPhoneMotion = generated.any { it in setOf("attitude", "rotation") }, needsPhoneHeading = "heading" in generated, needsPhonePressure = "pressure" in generated) else null)
        } }
        scope.launch { navigation.frames.collect { frame ->
            navigation.connections.value.filter { it.requested && it.spec.send && it.spec.feed == NmeaFeed.RAW }.forEach { target ->
                val epoch=navigation.connectionEpoch(target.spec.id)?:return@forEach
                encoder.forward(target.spec, frame)?.let { queue(target, NmeaPublicationBatch(listOf(it),mapOf(frame.connectionId to frame.generation)),epoch) }
            }
        } }
        scope.launch { while (isActive) {
            delay(1_000)
            navigation.connections.value.filter { it.requested && it.spec.send && it.spec.feed != NmeaFeed.RAW }.forEach { target ->
                val epoch=navigation.connectionEpoch(target.spec.id)?:return@forEach
                queue(target,encoder.encodeBatch(target.spec,SystemClock.elapsedRealtime()),epoch)
            }
        } }
    }
    private fun queue(target: NmeaConnectionSnapshot, batch:NmeaPublicationBatch,epoch:Long) {
        val lines=batch.sentences
        if (lines.isEmpty()) return
        if(navigation.connectionEpoch(target.spec.id)!=epoch)return
        val accepted = synchronized(guard) { writers[target.spec.id]?.queue?.trySend(Batch(epoch, lines,batch.sourceEpochs))?.isSuccess == true }
        if (!accepted) navigation.recordDroppedOutput(target.spec.id, lines.size)
    }
}

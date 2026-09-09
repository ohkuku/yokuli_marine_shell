package com.yokuli.anchorwatch.runtime.output

import android.os.SystemClock
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.data.sharing.NmeaOutputMux
import com.yokuli.anchorwatch.data.vessel.*
import com.yokuli.anchorwatch.domain.model.*
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

/** One bounded writer per destination. Slow TCP peers never block other peers. */
@Singleton class MultiNmeaPublisher @Inject constructor(
    private val navigation:NavigationRepository,
    private val hub:VesselDataHub,
    private val positions:VesselPositionRepository,
    private val mux:NmeaOutputMux,
    private val resources:RuntimeResourceManager,
    private val phoneEncoder:AnchorWatchNmeaFeedEncoder,
    private val mount:com.yokuli.anchorwatch.location.vessel.VesselMountCalibrationRepository,
    private val attitude:com.yokuli.anchorwatch.location.vessel.PhoneVesselAttitudeRepository,
){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private data class Batch(val epoch:Long,val sentences:List<String>)
    private data class Writer(val queue:Channel<Batch>,val job:Job)
    private val writers=mutableMapOf<String,Writer>()
    private val guard=Any()
    @Volatile private var calibration=com.yokuli.anchorwatch.location.vessel.VesselMountCalibration()
    init{
        scope.launch{mount.calibration.collect{calibration=it}}
        scope.launch{navigation.connections.collect{connections->
            val ids=connections.filter{it.requested&&it.spec.send}.mapTo(mutableSetOf()){it.spec.id}
            synchronized(guard){writers.keys.toList().filter{it !in ids}.forEach{writers.remove(it)?.let{writer->writer.queue.close();writer.job.cancel()}};ids.forEach{id->if(id !in writers){val channel=Channel<Batch>(64);val job=scope.launch{for(batch in channel){navigation.writeConnection(id,batch.epoch,batch.sentences)}};writers[id]=Writer(channel,job)}}}
            resources.set(RuntimeOwner.NMEA_CONNECTIONS,if(connections.any{it.requested})RuntimeRequirement(needsWakeLock=true,needsWifiLock=true,needsSystemLocation=connections.any{it.requested&&it.spec.send},needsPhoneMotion=connections.any{it.requested&&it.spec.send},needsPhoneHeading=connections.any{it.requested&&it.spec.send},needsPhonePressure=connections.any{it.requested&&it.spec.send})else null)
        }}
        scope.launch{navigation.frames.collect{frame->
            navigation.connections.value.filter{it.requested&&it.spec.send&&it.spec.feed==NmeaFeed.RAW&&frame.connectionId in it.spec.forwardFrom}.forEach{target->
                if(frame.connectionId!=target.spec.id&&!samePeer(frame.peer,target.spec)&&accepts(target.spec,frame.sentence))queue(target,listOf(frame.sentence))
            }
        }}
        scope.launch{while(isActive){delay(1000);navigation.connections.value.filter{it.requested&&it.spec.send&&it.spec.feed!=NmeaFeed.RAW}.forEach{target->queue(target,encode(target.spec,SystemClock.elapsedRealtime()))}}}
    }
    private fun queue(target:NmeaConnectionSnapshot,lines:List<String>){
        if(lines.isEmpty())return
        val epoch=navigation.connectionEpoch(target.spec.id)?:return
        val accepted=synchronized(guard){writers[target.spec.id]?.queue?.trySend(Batch(epoch,lines))?.isSuccess==true}
        if(!accepted)navigation.recordDroppedOutput(target.spec.id,lines.size)
    }
    private fun accepts(spec:NmeaConnectionSpec,line:String)=spec.sentenceTypes.isEmpty()||line.removePrefix("$").substringBefore(',').takeLast(3).uppercase() in spec.sentenceTypes
    private fun samePeer(peer:String?,spec:NmeaConnectionSpec)=peer?.removePrefix("/")?.equals("${spec.host}:${spec.port}",true)==true
    private fun ancestry(observation:VesselObservation<*>):List<VesselSourceIdentity>{
        val candidates=hub.snapshot.value.candidates.values.flatten()
        fun leaves(identity:VesselSourceIdentity,seen:Set<String>):List<VesselSourceIdentity>{
            if(identity.persistentKey in seen)return emptyList()
            if(identity.sourceType!=VesselSourceType.APP_DERIVED)return listOf(identity)
            val parent=candidates.firstOrNull{it.source.persistentKey==identity.persistentKey}?:return emptyList()
            val origin=parent.provenance as? VesselProvenance.Derived?:return emptyList()
            val parts=origin.inputs.map{leaves(it,seen+identity.persistentKey)}
            return if(parts.any{it.isEmpty()})emptyList()else parts.flatten()
        }
        val origins=when(val origin=observation.provenanceDetail){is VesselProvenance.Derived->origin.inputs;is VesselProvenance.Nmea->listOf(origin.source);else->listOfNotNull(observation.sourceIdentity)}
        val parts=origins.map{leaves(it,emptySet())};return if(parts.any{it.isEmpty()})emptyList()else parts.flatten()
    }
    private fun allowed(value:VesselObservation<*>,spec:NmeaConnectionSpec,now:Long):Boolean{
        if(value.value==null||value.source in setOf(VesselDataSource.DEMO,VesselDataSource.NONE)||value.receivedElapsedRealtime?.let{now-it<0}!=false)return false
        if(value.freshness !in setOf(VesselDataFreshness.FRESH,VesselDataFreshness.HELD))return false
        val origins=ancestry(value)
        if(origins.isEmpty()||origins.any{it.transportProfileId==spec.id||samePeer(it.transportPeer,spec)||it.sourceType in setOf(VesselSourceType.DEMO,VesselSourceType.PHONE_TX_ECHO)})return false
        return spec.feed!=NmeaFeed.PHONE||PhoneOwnedPublicationProvenancePolicy.evaluate(value,hub.snapshot.value.candidates.values.flatten()).allowed
    }
    /** Encoding is fed from the same selected observations that the apps read. */
    private fun encode(spec:NmeaConnectionSpec,now:Long):List<String>{
        val snapshot=hub.snapshot.value
        if(spec.feed==NmeaFeed.PHONE){
            val config=NmeaDeviceOutputSettings(phonePositionEnabled=true,phoneHeadingEnabled=true,phoneRateOfTurnEnabled=true,phoneAttitudeEnabled=true,phonePressureEnabled=true)
            return AnchorWatchNmeaStream.entries.flatMap{stream->phoneEncoder.encode(stream,snapshot,config,now,positions.acceptedPhoneFix.value,inputProfileId=spec.id,mountCalibration=calibration,runtimeMountState=attitude.mountState.value).sentences}.filter{accepts(spec,it)}
        }
        fun number(value:VesselObservation<Double>)=value.value?.takeIf{it.isFinite()&&allowed(value,spec,now)}
        val lines=buildList{
            val position=snapshot.position
            if(allowed(position,spec,now)&&position.freshness==VesselDataFreshness.FRESH){
                val fix=if(position.source==VesselDataSource.PHONE_GNSS)positions.acceptedPhoneFix.value else if(position.source==VesselDataSource.BOAT_NMEA)navigation.fix.value else null
                fix?.let{addAll(mux.acceptedPosition(it,now))}
            }
            number(snapshot.headingTrueDegrees)?.let{add(mux.phoneHeading(it))}
            number(snapshot.headingMagneticDegrees)?.let{add(mux.phoneMagneticHeading(it,null))}
            number(snapshot.speedThroughWaterKnots)?.let{add(mux.canonicalSpeedThroughWater(it))}
            number(snapshot.depthMeters)?.let{depth->
                // DBT means below transducer. Surface/keel-referenced values are
                // never relabelled as transducer depth merely to fill a feed.
                if((snapshot.depthMeters.reference as? VesselReference.Depth)?.reference==com.yokuli.anchorwatch.domain.sonar.DepthReference.BELOW_TRANSDUCER)add(mux.canonicalDepth(depth))
            }
            val aws=number(snapshot.apparentWind.speedKnots);val awa=number(snapshot.apparentWind.angleDegrees)
            if(aws!=null&&awa!=null)add(NmeaChecksum.append("WIMWV,${format((awa+360)%360)},R,${format(aws)},N,A")+"\r\n")
            val tws=number(snapshot.trueWind.speedKnots);val twa=number(snapshot.trueWind.angleDegrees);val twd=number(snapshot.trueWind.directionDegrees)
            if(tws!=null&&twa!=null&&twd!=null)addAll(mux.derivedTrueWind(tws,twd,twa))
            number(snapshot.rateOfTurnDegreesPerMinute)?.let{add(mux.phoneRateOfTurn(it))}
            val pressure=number(snapshot.pressureHpa)
            val attitude=snapshot.attitude.value?.takeIf{allowed(snapshot.attitude,spec,now)}
            mux.phoneXdr(attitude,pressure)?.let(::add)
        }
        return lines.filter{accepts(spec,it)}
    }
    private fun format(value:Double)=String.format(java.util.Locale.US,"%.2f",value)
}

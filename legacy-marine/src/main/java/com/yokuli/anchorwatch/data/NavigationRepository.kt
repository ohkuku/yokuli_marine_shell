package com.yokuli.anchorwatch.data

import android.os.SystemClock
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.data.nmea.input.*
import com.yokuli.anchorwatch.data.nmea.output.NmeaOutboundLoopGuard
import com.yokuli.anchorwatch.data.preferences.SettingsRepository
import com.yokuli.anchorwatch.data.vessel.*
import com.yokuli.anchorwatch.data.condition.*
import com.yokuli.anchorwatch.domain.model.*
import com.yokuli.anchorwatch.domain.sonar.DepthObservation
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.vessel.source.*
import com.yokuli.anchorwatch.location.NmeaFixQualityPolicy
import com.yokuli.anchorwatch.location.PositionIntegrityFilter
import com.yokuli.anchorwatch.location.PositionIntegrityResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

data class NmeaInstrumentState(
 val headingTrue:Pair<Double,Long>?=null,
 val headingMagnetic:Pair<Double,Long>?=null,
 val speedOverGroundKnots:Pair<Double,Long>?=null,
 val courseOverGroundTrue:Pair<Double,Long>?=null,
 val speedThroughWaterKnots:Pair<Double,Long>?=null,
 val selectedHeadingSourceId:String?=null,
 val headingCandidates:List<NmeaHeadingCandidate> = emptyList(),
 val headingConflict:Boolean=false,
 val headingConflictDegrees:Double?=null,
 val pinnedHeadingSourceUnavailable:Boolean=false,
)

/** Connection-owned ingestion; legacy properties are projections of selected observations. */
@Singleton class NavigationRepository @Inject constructor(
    private val liveDepth:LiveDepthRepository,
    private val liveWind:LiveWindRepository,
    private val outboundLoopGuard:NmeaOutboundLoopGuard,
    private val sourceRegistry:VesselSourceRegistry,
    private val settings:SettingsRepository,
    private val vesselSettings:VesselSettingsRepository,
    private val store:NmeaConnectionStore,
){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val guard=Any()
    private val sessions=linkedMapOf<String,Session>()
    // UDP listeners with the same local port share a socket. Each logical
    // connection still has its own peer filter, parser and lifecycle.
    private val udpListeners=linkedMapOf<Int,NmeaConnectionManager>()
    private val udpOwners=linkedMapOf<Int,MutableSet<String>>()
    private val processors=mutableMapOf<String,Processor>()
    private val positionFixes=mutableMapOf<String,NavigationFix>()
    private val depthFixes=mutableMapOf<String,DepthObservation>()
    private val arbiter=VesselSourceArbitrator()
    private var selectedIdentity:String?=null
    private var selectedEpoch=0L
    private var nextConnectionEpoch=0L
    private var pendingPositionPin:String?=null
    private var lastDepthKey:String?=null
    @Volatile private var positionLock:String?=null
    fun positionSourcePin():String?=positionLock
    fun positionConnectionId():String?=vessel.metricSourcePins["POSITION_CONNECTION"]
    private var positionPolicyKey:String?=null
    @Volatile private var vessel=VesselDataSettings()
    @Volatile private var loaded=false
    private val _specs=MutableStateFlow<List<NmeaConnectionSpec>>(emptyList());val connectionSpecs=_specs.asStateFlow()
    private val _connections=MutableStateFlow<List<NmeaConnectionSnapshot>>(emptyList());val connections=_connections.asStateFlow()
    private val _frames=MutableSharedFlow<NmeaRawFrame>(extraBufferCapacity=512,onBufferOverflow=kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST);val frames=_frames.asSharedFlow()
    private val _fix=MutableStateFlow<NavigationFix?>(null);val fix=_fix.asStateFlow()
    private val _connectionState=MutableStateFlow(NmeaConnectionState.DISCONNECTED);val connectionState=_connectionState.asStateFlow()
    private val _recentFixes=MutableStateFlow<List<NavigationFix>>(emptyList());val recentFixes=_recentFixes.asStateFlow()
    private val _diagnostics=MutableStateFlow(NmeaDiagnostics());val diagnostics=_diagnostics.asStateFlow()
    private val _connectionStartedElapsed=MutableStateFlow<Long?>(null);val connectionStartedElapsed=_connectionStartedElapsed.asStateFlow()
    private val _transport=MutableStateFlow(NmeaTransportDiagnostics());val transportDiagnostics=_transport.asStateFlow()
    private val _validRaw=MutableSharedFlow<String>(extraBufferCapacity=512);val validRawSentences=_validRaw.asSharedFlow()
    private val _parsed=MutableSharedFlow<ParsedNmeaEnvelope>(extraBufferCapacity=256);val parsedEnvelopes=_parsed.asSharedFlow()
    private val _invalidations=MutableSharedFlow<NmeaSourceInvalidation>(extraBufferCapacity=64);val sourceInvalidations=_invalidations.asSharedFlow()
    private val _depth=MutableSharedFlow<DepthObservation>(extraBufferCapacity=64);val depthObservations=_depth.asSharedFlow()
    private val _instruments=MutableStateFlow(NmeaInstrumentState());val instruments=_instruments.asStateFlow()
    private class Processor { val parser=Nmea0183Parser();val retained=NmeaUpdateRetainer();val positionGates=mutableMapOf<String,PositionIntegrityFilter>() }
    private data class Session(var spec:NmeaConnectionSpec,var requested:Boolean=false,var generation:Long=0,var started:Long?=null,var manager:NmeaConnectionManager?=null,var reader:Job?=null,var stateReader:Job?=null,var transportReader:Job?=null,var udpWriter:DatagramSocket?=null,var snapshot:NmeaConnectionSnapshot=NmeaConnectionSnapshot(spec),val writeGuard:Any=Any())
    init {
        scope.launch {
            val saved=store.read()
            val initial=saved?:settings.settings.first().profile.takeIf{it.host.isNotBlank()||it.port>0}?.let{listOf(NmeaConnectionSpec(id=it.stableId,name=it.name,protocol=it.protocol,host=it.host,port=it.port,localPort=it.port,requireChecksum=it.requireChecksum,autoReconnect=it.autoReconnect))}.orEmpty()
            synchronized(guard){initial.forEach{sessions[it.id]=Session(it)};loaded=true;publishConnections()}
            if(saved==null)store.save(initial)
        }
        scope.launch{vesselSettings.settings.collect{vessel=it;synchronized(guard){arbiter.reset();refreshObservations()}}}
        scope.launch{while(isActive){delay(250);synchronized(guard){refreshObservations();publishConnections()}}}
    }
    suspend fun saveConnection(spec:NmeaConnectionSpec){
        while(!loaded)delay(10)
        require(spec.id.isNotBlank()&&spec.port in 1..65535&&(!spec.receive||spec.protocol!=Protocol.UDP||spec.localPort in 1..65535)){"Invalid NMEA endpoint"}
        require(spec.protocol==Protocol.UDP||spec.host.isNotBlank()){ "A TCP host is required" }
        require(!spec.send||spec.host.isNotBlank()){ "An output host is required" }
        require(spec.id !in spec.forwardFrom){"A connection cannot forward to itself"}
        synchronized(guard){
            val previous=sessions[spec.id]
            val proposed=sessions.values.map{it.spec}.filter{it.id!=spec.id}+spec
            val edges=proposed.filter{it.send&&it.feed==NmeaFeed.RAW}.flatMap{target->target.forwardFrom.map{it to target.id}}.groupBy({it.first},{it.second})
            fun cycle(node:String,path:Set<String>):Boolean=if(node in path)true else edges[node].orEmpty().any{cycle(it,path+node)}
            require(proposed.none{cycle(it.id,emptySet())}){"This forwarding route would create a loop"}
            require(previous?.requested!=true){"Stop this connection before editing its transport or routing"}
            sessions[spec.id]=Session(spec.copy(name=spec.name.trim().ifBlank{"NMEA"}),snapshot=previous?.snapshot?.copy(spec=spec)?:NmeaConnectionSnapshot(spec))
            publishConnections()
        }
        store.save(connectionSpecs.value)
    }
    suspend fun removeConnection(id:String){stopConnection(id);synchronized(guard){sessions.remove(id);publishConnections()};store.save(connectionSpecs.value)}
    fun startConnection(id:String):Boolean=synchronized(guard){
        val session=sessions[id]?:return@synchronized false
        if(session.requested)return@synchronized false
        session.requested=true;session.generation=++nextConnectionEpoch;session.started=SystemClock.elapsedRealtime();invalidateConnection(id)
        val spec=session.spec
        if(spec.protocol==Protocol.UDP&&!spec.receive){
            try{session.udpWriter=DatagramSocket();session.snapshot=session.snapshot.copy(requested=true,state=NmeaConnectionState.CONNECTED_NO_DATA,error=null)}catch(e:Exception){session.requested=false;session.snapshot=session.snapshot.copy(error=e.message,state=NmeaConnectionState.ERROR)}
            publishConnections();return@synchronized session.requested
        }
        var startTransport=spec.protocol==Protocol.TCP
        val manager=if(spec.protocol==Protocol.UDP){udpOwners.getOrPut(spec.localPort){linkedSetOf()}.add(id);udpListeners.getOrPut(spec.localPort){startTransport=true;NmeaConnectionManager(scope)}}else NmeaConnectionManager(scope)
        if(spec.protocol==Protocol.UDP&&spec.send)try{session.udpWriter=DatagramSocket()}catch(error:Exception){
            udpOwners[spec.localPort]?.remove(id);if(udpOwners[spec.localPort].isNullOrEmpty()){udpOwners.remove(spec.localPort);udpListeners.remove(spec.localPort)?.disconnect()}
            session.requested=false;session.snapshot=session.snapshot.copy(state=NmeaConnectionState.ERROR,error=error.message);publishConnections();return@synchronized false
        }
        session.manager=manager
        session.reader=scope.launch{
            val peerFilter=if(spec.protocol==Protocol.UDP&&spec.host.isNotBlank()&&spec.host!="0.0.0.0")runCatching{InetAddress.getByName(spec.host).hostAddress}.getOrNull()?:spec.host else null
            manager.frames.collect{(line,peer)->
                val peerAddress=peer.removePrefix("/").substringBeforeLast(':')
                if(peerFilter==null||peerFilter==peerAddress)ingest(id,line,peer,spec.requireChecksum)
            }
        }
        session.stateReader=scope.launch{manager.state.collect{value->synchronized(guard){if(session.requested){session.snapshot=session.snapshot.copy(state=value);if(value in setOf(NmeaConnectionState.ERROR,NmeaConnectionState.DISCONNECTED))invalidateConnection(id);publishConnections();refreshObservations()}}}}
        session.transportReader=scope.launch{var last=-1L;manager.diagnostics.collect{value->synchronized(guard){if(session.requested){if(last!=value.connectionGeneration){last=value.connectionGeneration;session.generation=++nextConnectionEpoch;session.started=SystemClock.elapsedRealtime();invalidateConnection(id)};session.snapshot=session.snapshot.copy(transport=value.copy(connectionGeneration=session.generation));publishConnections()}}}}
        if(startTransport)manager.connect(if(spec.protocol==Protocol.UDP)spec.profile().copy(host="")else spec.profile());publishConnections();true
    }
    fun stopConnection(id:String){
        val stopped=synchronized(guard){
        val session=sessions[id]?:return
        session.requested=false;session.generation=++nextConnectionEpoch;session.reader?.cancel();session.stateReader?.cancel();session.transportReader?.cancel()
        if(session.spec.protocol==Protocol.UDP&&session.spec.receive){val owners=udpOwners[session.spec.localPort];owners?.remove(id);if(owners.isNullOrEmpty()){udpListeners.remove(session.spec.localPort)?.disconnect();udpOwners.remove(session.spec.localPort)}}else session.manager?.disconnect()
        session.manager=null;session.udpWriter?.close();session.udpWriter=null;session.started=null
        session.snapshot=session.snapshot.copy(requested=false,state=NmeaConnectionState.DISCONNECTED,transport=session.snapshot.transport.copy(connectionGeneration=session.generation,desiredConnected=false))
        invalidateConnection(id);refreshObservations();publishConnections();session
        }
        synchronized(stopped.writeGuard){ /* Every in-flight write finished or failed on the closed transport. */ }
    }
    fun reconnectConnection(id:String):Boolean{stopConnection(id);return startConnection(id)}
    fun connectionEpoch(id:String)=synchronized(guard){sessions[id]?.generation}
    fun isConnectionOpen(id:String)=synchronized(guard){sessions[id]?.let{it.requested&&(it.udpWriter!=null||it.manager?.hasOpenTransport()==true)}==true}
    fun inputConnectionIds()=connections.value.filter{it.requested&&it.spec.receive}.mapTo(linkedSetOf()){it.spec.id}
    fun anyRequested()=connections.value.any{it.requested}
    fun writeConnection(id:String,expectedGeneration:Long,sentences:List<String>):Boolean {
        val target=synchronized(guard){sessions[id]?.takeIf{it.requested&&it.generation==expectedGeneration&&it.spec.send}}?:return false
        val lines=sentences.map{it.trim()+"\r\n"}
        val address=if(target.spec.protocol==Protocol.UDP)try{InetAddress.getByName(target.spec.host)}catch(e:Exception){return false}else null
        val attempt=outboundLoopGuard.beginWrite(lines)
        val result=synchronized(target.writeGuard){
            val current=synchronized(guard){target.takeIf{it.requested&&it.generation==expectedGeneration}}
            if(current==null)false else try {
                if(target.spec.protocol==Protocol.TCP)target.manager?.writeExpected(lines,target.manager?.diagnostics?.value?.connectionGeneration)?.success==true
                else {
                    val socket=synchronized(guard){target.udpWriter}?:return@synchronized false
                    socket.broadcast=target.spec.host.endsWith(".255")||target.spec.host=="255.255.255.255"
                    lines.forEach{val bytes=it.toByteArray(Charsets.US_ASCII);socket.send(DatagramPacket(bytes,bytes.size,address,target.spec.port))};true
                }
            }catch(e:Exception){synchronized(guard){target.snapshot=target.snapshot.copy(error=e.message)};false}
        }
        outboundLoopGuard.completeWrite(attempt,result)
        synchronized(guard){if(target.requested&&target.generation==expectedGeneration){target.snapshot=target.snapshot.copy(writtenSentences=target.snapshot.writtenSentences+if(result)lines.size else 0,droppedSentences=target.snapshot.droppedSentences+if(result)0 else lines.size,lastWrittenElapsed=if(result)SystemClock.elapsedRealtime()else target.snapshot.lastWrittenElapsed,recentWritten=if(result)(target.snapshot.recentWritten+lines.map{it.trim()}).takeLast(80)else target.snapshot.recentWritten,error=if(result)null else target.snapshot.error);publishConnections()}}
        return result
    }
    /** The asynchronous generic field decoder cannot republish a stopped epoch. */
    fun publishInputCandidates(candidates:List<VesselSourceCandidate<*>>){synchronized(guard){
        sourceRegistry.publishAll(candidates.filter{candidate->sessions[candidate.source.transportProfileId]?.let{it.requested&&it.spec.receive&&it.generation==candidate.source.connectionGeneration}==true})
    }}
    fun recordDroppedOutput(id:String,count:Int){synchronized(guard){sessions[id]?.let{it.snapshot=it.snapshot.copy(droppedSentences=it.snapshot.droppedSentences+count);publishConnections()}}}
    private fun invalidateConnection(id:String){
        val sourceIds=sourceRegistry.snapshot.value.values.flatten().filter{it.source.transportProfileId==id}.mapTo(mutableSetOf()){it.source.id}
        sourceRegistry.removeSources(sourceIds);sourceIds.forEach{positionFixes.remove(it);depthFixes.remove(it)};processors.keys.filter{it.startsWith("$id|")}.forEach(processors::remove)
    }
    private fun ingest(id:String,line:String,peer:String,checksum:Boolean)=synchronized(guard){
        val session=sessions[id]?:return@synchronized
        if(!session.requested||!session.spec.receive)return@synchronized
        val now=SystemClock.elapsedRealtime();val normalized=line.trim();val old=session.snapshot.diagnostics
        val valid=NmeaChecksum.validate(normalized,checksum)
        val echo=valid&&outboundLoopGuard.isRecentExactOutboundForReceiver(normalized,"$id:${session.generation}:$peer",now)
        val raw=(old.raw+if(echo)"[echo] $normalized" else normalized).takeLast(160)
        val processor=processors.getOrPut("$id|${session.generation}|$peer"){Processor()}
        val parsed=if(valid&&!echo)processor.parser.parseEnvelope(normalized,checksum,now)else null
        session.snapshot=session.snapshot.copy(diagnostics=old.copy(bytes=old.bytes+line.length+1,validSentences=old.validSentences+if(valid)1 else 0,invalidSentences=old.invalidSentences+if(!valid)1 else 0,lastPacketElapsed=now,raw=raw,echoedAppTxSentences=old.echoedAppTxSentences+if(echo)1 else 0))
        if(!valid||echo){publishConnections();return@synchronized}
        _frames.tryEmit(NmeaRawFrame(id,session.generation,peer,normalized,now));_validRaw.tryEmit(normalized)
        if(parsed==null){publishConnections();return@synchronized}
        val update=processor.retained.accept(parsed.update,now,normalized);val envelope=parsed.copy(update=update,connectionId=id,connectionGeneration=session.generation,peer=peer);_parsed.tryEmit(envelope)
        val base=NmeaCandidateMapper.map(envelope,id,session.generation)
        fun identify(source:VesselSourceIdentity)=source.copy(id="nmea:$id:${session.generation}:$peer:${source.fullSentenceId}",stableKey="nmea:$id:$peer:${source.fullSentenceId}",displayName="${session.spec.name} · ${source.fullSentenceId}",transportPeer=peer)
        val candidates=base.map{candidate->val identity=identify(candidate.source);candidate.copy(source=identity,provenance=VesselProvenance.Nmea(identity))}
        if(!update.holdAllowed){
            val affected=NmeaInvalidationPolicy.affectedMetrics(envelope.sentenceType)
            val ids=sourceRegistry.snapshot.value.values.flatten().filter{it.metric in affected&&it.source.transportProfileId==id&&it.source.id.contains(":$peer:")&&it.source.fullSentenceId==envelope.fullSentenceId}.mapTo(mutableSetOf()){it.source.id};sourceRegistry.removeSources(ids);ids.forEach{positionFixes.remove(it);depthFixes.remove(it)}
        }
        val pos=candidates.firstOrNull{it.metric==VesselMetricId.POSITION}
        var acceptedPosition=false
        if(pos!=null&&update.position!=null){
            val rawFix=update.position.copy(sogKnots=update.sog,cogTrueDegrees=update.cog,sogReceivedElapsedRealtime=update.measuredAt(NmeaMetric.SOG),cogReceivedElapsedRealtime=update.measuredAt(NmeaMetric.COG),hdop=update.hdop?:update.position.hdop,fixQuality=update.fixQuality?:update.position.fixQuality,satellites=update.satellites?:update.position.satellites,positionProvider=PositionProvider.NMEA)
            val gate=processor.positionGates.getOrPut(envelope.fullSentenceId){PositionIntegrityFilter()}
            val result=if(NmeaFixQualityPolicy.allowsContinuation(rawFix,now))gate.evaluate(rawFix)else null
            if(result is PositionIntegrityResult.Accepted){result.fixes.lastOrNull()?.let{positionFixes[pos.source.id]=it.fix;acceptedPosition=true;session.manager?.reportValidFix();session.snapshot=session.snapshot.copy(diagnostics=session.snapshot.diagnostics.copy(lastFixElapsed=now))}}
        }
        sourceRegistry.publishAll(candidates.filter{it.metric!=VesselMetricId.POSITION||acceptedPosition})
        update.depthObservation?.let{observation->candidates.firstOrNull{it.metric==VesselMetricId.DEPTH}?.let{depthFixes[it.source.id]=observation}}
        refreshObservations();publishConnections()
    }
    @Suppress("UNCHECKED_CAST") private fun <T> selected(metric:VesselMetricId,now:Long):VesselSourceCandidate<T>?{
        val connectionPin=vessel.metricSourcePins["POSITION_CONNECTION"]
        val all=sourceRegistry.candidates<T>(metric).filter{it.sourceClass==VesselSourceClass.BOAT_NMEA}
        val raw=if(metric==VesselMetricId.POSITION)all.filter{it.source.transportProfileId==connectionPin}else all
        val pin=vessel.metricSourcePins[metric.name]?:when(metric){VesselMetricId.POSITION->vessel.pinnedPositionSourceId;VesselMetricId.HEADING_TRUE,VesselMetricId.HEADING_MAGNETIC->vessel.boatHeadingSourceId;else->null}
        val effectivePin=if(metric==VesselMetricId.POSITION){
            val policy="$connectionPin|$pin"
            if(positionPolicyKey!=policy){positionPolicyKey=policy;positionLock=null}
            if(positionLock==null){
                positionLock=pin?:raw.firstOrNull{MetricSourceEligibility.evaluate(metric,it,now)==CandidateValidity.ELIGIBLE}?.source?.persistentKey
                val chosen=positionLock
                if(pin==null&&chosen!=null&&pendingPositionPin!=chosen){
                    pendingPositionPin=chosen
                    scope.launch{
                        val current=vesselSettings.settings.first()
                        if(current.metricSourcePins["POSITION_CONNECTION"]==connectionPin&&current.metricSourcePins["POSITION"]==null)
                            vesselSettings.save(current.copy(metricSourcePins=current.metricSourcePins+("POSITION" to chosen)))
                    }
                }
            }
            positionLock
        }else pin
        val resolved=effectivePin?.let{VesselSourcePinPolicy.resolve(raw,it)?:it}
        return arbiter.select(metric,raw,MetricSourcePreference(VesselSourcePreference.BOAT,resolved,if(metric==VesselMetricId.POSITION)false else vessel.allowPinnedFallback,connectionPriorities()),now).selected
    }
    suspend fun selectPositionConnection(id:String,sourceKey:String?=null){
        require(isConnectionOpen(id)){"Connect this input before selecting its position"}
        val current=vesselSettings.settings.first()
        val pins=current.metricSourcePins.toMutableMap();pins["POSITION_CONNECTION"]=id;if(sourceKey==null)pins.remove("POSITION")else pins["POSITION"]=sourceKey
        val updated=current.copy(metricSourcePins=pins,pinnedPositionSourceId=null,allowPinnedFallback=false)
        synchronized(guard){positionLock=null;positionPolicyKey=null;vessel=updated;arbiter.reset();refreshObservations()}
        vesselSettings.save(updated)
    }
    suspend fun ensurePositionConnection(){
        val current=vesselSettings.settings.first()
        val pinned=current.metricSourcePins["POSITION_CONNECTION"]
        if(pinned==null){val id=connections.value.firstOrNull{it.requested&&it.spec.receive}?.spec?.id?:return;selectPositionConnection(id)}
    }
    fun connectionPriorities()=connectionSpecs.value.associate{it.id to it.priority}
    private fun refreshObservations(){
        val now=SystemClock.elapsedRealtime();val position=selected<VesselPosition>(VesselMetricId.POSITION,now)
        val identity=position?.source?.id
        if(identity!=selectedIdentity){selectedIdentity=identity;selectedEpoch++;_connectionStartedElapsed.value=position?.source?.transportProfileId?.let{sessions[it]?.started};_transport.value=NmeaTransportDiagnostics(connectionGeneration=selectedEpoch,connectedAtElapsedRealtime=_connectionStartedElapsed.value)}
        fun number(metric:VesselMetricId)=selected<Double>(metric,now)?.let{it.value to it.receivedElapsedRealtime}
        val heading=number(VesselMetricId.HEADING_TRUE);val magnetic=number(VesselMetricId.HEADING_MAGNETIC)
        val selectedFix=identity?.let(positionFixes::get)?.let{f->f.copy(headingTrueDegrees=heading?.first,headingReceivedElapsedRealtime=heading?.second,headingMagneticDegrees=magnetic?.first,headingMagneticReceivedElapsedRealtime=magnetic?.second,headingSource=if(heading!=null)HeadingSource.NMEA_PHYSICAL else HeadingSource.NONE,headingQuality=if(heading!=null)HeadingQuality.STABLE else HeadingQuality.UNAVAILABLE)}
        if(_fix.value!=selectedFix){_fix.value=selectedFix;if(selectedFix!=null)_recentFixes.value=(_recentFixes.value+selectedFix).filter{now-it.receivedElapsedRealtime<600_000}.takeLast(1200)}
        _instruments.value=NmeaInstrumentState(heading,magnetic,number(VesselMetricId.SOG),number(VesselMetricId.COG),number(VesselMetricId.SPEED_THROUGH_WATER),selected<Double>(VesselMetricId.HEADING_TRUE,now)?.source?.persistentKey)
        val depth=selected<Double>(VesselMetricId.DEPTH,now)
        val depthObservation=depth?.source?.id?.let(depthFixes::get)
        val depthKey=depthObservation?.let{"${depth.source.id}:${it.receivedElapsedRealtime}"}
        if(depthKey!=lastDepthKey){lastDepthKey=depthKey;if(depthObservation==null)liveDepth.clear()else{liveDepth.accept(depthObservation);_depth.tryEmit(depthObservation)}}
        fun wind(metric:VesselMetricId)=number(metric)?.let{TimedWindValue(it.first,it.second)}
        liveWind.publishSelected(LiveWindState(trueSpeed=wind(VesselMetricId.TRUE_WIND_SPEED),apparentSpeed=wind(VesselMetricId.APPARENT_WIND_SPEED),trueDirection=wind(VesselMetricId.TRUE_WIND_DIRECTION),trueDirectionSource=com.yokuli.anchorwatch.domain.condition.TrueWindDirectionSource.MWD,apparentAngle=wind(VesselMetricId.APPARENT_WIND_ANGLE),trueAngle=wind(VesselMetricId.TRUE_WIND_ANGLE)))
        val live=sessions.values.filter{it.requested&&it.spec.receive}
        _connectionState.value=if(selectedFix!=null)NmeaConnectionState.CONNECTED else when{live.any{it.manager?.hasOpenTransport()==true}->NmeaConnectionState.CONNECTED_NO_FIX;live.any{it.snapshot.state==NmeaConnectionState.RECONNECTING}->NmeaConnectionState.RECONNECTING;live.any{it.snapshot.state==NmeaConnectionState.CONNECTING}->NmeaConnectionState.CONNECTING;live.isNotEmpty()->NmeaConnectionState.ERROR;else->NmeaConnectionState.DISCONNECTED}
        if(_connectionStartedElapsed.value==null&&live.isNotEmpty())_connectionStartedElapsed.value=live.mapNotNull{it.started}.minOrNull()
        _transport.value=_transport.value.copy(desiredConnected=live.isNotEmpty(),lastByteReceivedElapsedRealtime=live.mapNotNull{it.snapshot.diagnostics.lastPacketElapsed}.maxOrNull())
    }
    private fun publishConnections(){
        _specs.value=sessions.values.map{it.spec}.sortedByDescending{it.priority}
        _connections.value=_specs.value.mapNotNull{spec->sessions[spec.id]?.let{it.snapshot.copy(spec=spec,requested=it.requested)}}
        val d=sessions.values.map{it.snapshot.diagnostics}
        _diagnostics.value=NmeaDiagnostics(bytes=d.sumOf{it.bytes},validSentences=d.sumOf{it.validSentences},invalidSentences=d.sumOf{it.invalidSentences},checksumErrors=d.sumOf{it.checksumErrors},lastPacketElapsed=d.mapNotNull{it.lastPacketElapsed}.maxOrNull(),lastFixElapsed=d.mapNotNull{it.lastFixElapsed}.maxOrNull(),raw=sessions.values.flatMap{session->session.snapshot.diagnostics.raw.takeLast(40).map{"[${session.spec.name}] $it"}}.takeLast(200))
    }
    // Compatibility commands now address one named connection. Feature readers
    // may claim existing input, but never reconnect saved endpoints implicitly.
    fun connect(p:ConnectionProfile):Boolean {synchronized(guard){if(!sessions.containsKey(p.stableId))sessions[p.stableId]=Session(NmeaConnectionSpec(id=p.stableId,name=p.name,protocol=p.protocol,host=p.host,port=p.port,localPort=p.localPort?:p.port,autoReconnect=p.autoReconnect,requireChecksum=p.requireChecksum));publishConnections()};scope.launch{store.save(connectionSpecs.value)};return startConnection(p.stableId)}
    fun reconnect(p:ConnectionProfile)=if(synchronized(guard){sessions.containsKey(p.stableId)})reconnectConnection(p.stableId)else connect(p)
    fun disconnect(){activeProfileStableId().takeIf{it.isNotBlank()}?.let(::stopConnection)}
    fun disconnectAll(){connectionSpecs.value.forEach{stopConnection(it.id)}}
    fun acquireBackgroundConnection(p:ConnectionProfile)=hasOpenTransport()
    fun claimBackgroundConnectionIfConnected()=hasOpenTransport()
    fun releaseBackgroundConnection()=Unit
    fun clearUserDisconnectLatch()=Unit
    fun setSafetyOwnedRetry(enabled:Boolean){synchronized(guard){sessions.values.filter{it.requested&&it.spec.receive}.forEach{it.manager?.setSafetyOwnedRetry(enabled)}}}
    fun isUserDisconnected()=!hasOpenTransport()
    fun hasOpenTransport()=synchronized(guard){sessions.values.any{it.requested&&it.spec.receive&&it.manager?.hasOpenTransport()==true}}
    fun activeProfileStableId()=selectedIdentity?.let{identity->sourceRegistry.snapshot.value.values.flatten().firstOrNull{it.source.id==identity}?.source?.transportProfileId}?:connectionSpecs.value.firstOrNull()?.id.orEmpty()
    fun connectionGeneration()=selectedEpoch
    fun pinBoatHeadingSource(sourceId:String?,allowFallback:Boolean=false)=Unit
    fun clearDiagnostics(){synchronized(guard){sessions.values.forEach{it.snapshot=it.snapshot.copy(diagnostics=NmeaDiagnostics())};publishConnections()}}
    fun writeToBoat(sentences:List<String>)=writeToBoatExpected(sentences,null).success
    fun writeToBoatExpected(sentences:List<String>,expectedGeneration:Long?):NmeaTransportWriteResult {
        val id=activeProfileStableId();val epoch=connectionEpoch(id)
        val accepted=expectedGeneration==null||expectedGeneration==selectedEpoch
        val result=accepted&&epoch!=null&&writeConnection(id,epoch,sentences)
        return NmeaTransportWriteResult(result,expectedGeneration,selectedEpoch,sentences.size,if(result)sentences.size else 0)
    }
    fun accept(line:String,requireChecksum:Boolean=true){val id=activeProfileStableId();ingest(id,line,"injected",requireChecksum)}
}

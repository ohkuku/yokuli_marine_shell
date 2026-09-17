package com.yokuli.anchorwatch.runtime.sharing

import android.os.SystemClock
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.data.sharing.LocalNmeaServerSettings
import com.yokuli.anchorwatch.data.sharing.NmeaSharingServer
import com.yokuli.anchorwatch.data.sharing.SharingServerState
import com.yokuli.anchorwatch.runtime.RuntimeOwner
import com.yokuli.anchorwatch.runtime.RuntimeRequirement
import com.yokuli.anchorwatch.runtime.RuntimeResourceManager
import com.yokuli.anchorwatch.runtime.output.NmeaPublicationEncoder
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 本机服务状态。生成、排队和真正写出的数量分开，监听成功不等于数据已送达。 */
data class LocalNmeaServerRuntimeStatus(
    val requested:Boolean=false,
    val generation:Long=0,
    val generatedSentences:Long=0,
    val queuedSentences:Long=0,
    val suppressedStreams:Map<String,String> = emptyMap(),
    val recentGenerated:List<String> = emptyList(),
    val message:String="Off",
)

/** 接收客户端只是传输方向不同；与 NMEA 主动发送共用真实的能力开关、来源和防回送规则。 */
@Singleton class LocalNmeaServerRuntime @Inject constructor(
    private val server:NmeaSharingServer,
    private val encoder:NmeaPublicationEncoder,
    private val navigation:NavigationRepository,
    private val resources:RuntimeResourceManager,
) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val recent=ArrayDeque<String>()
    private val _status=MutableStateFlow(LocalNmeaServerRuntimeStatus())
    val status=_status.asStateFlow()
    @Volatile private var settings=LocalNmeaServerSettings()
    @Volatile private var running=false
    val enabled:Boolean get()=running

    init {
        scope.launch { while(isActive) { delay(1_000); publishDue(SystemClock.elapsedRealtime()) } }
        scope.launch { navigation.frames.collect { frame -> forward(frame) } }
    }

    @Synchronized fun configure(value:LocalNmeaServerSettings) {
        val valid=value.configured&&value.port in 1024..65535
        val shouldRun=value.serverRequested&&valid
        if(settings==value&&running==shouldRun)return
        val changed=settings.copy(serverRequested=false)!=value.copy(serverRequested=false)
        settings=value.copy(serverRequested=shouldRun)
        if(!shouldRun) {
            running=false;server.stop();resources.release(RuntimeOwner.NMEA_SHARING)
            _status.value=_status.value.copy(requested=false,generation=_status.value.generation+1,message=if(valid)"Off" else "Invalid listening port")
            return
        }
        // 修改策略后旧队列不能继续发送已关闭的能力。
        if(changed&&running)server.stop()
        running=true
        if(changed||server.status.value.state in setOf(SharingServerState.STOPPED,SharingServerState.ERROR))server.start(value.port)
        val capabilities=if(value.feed==NmeaFeed.RAW)emptySet() else value.capabilities
        resources.set(RuntimeOwner.NMEA_SHARING,RuntimeRequirement(needsSystemLocation="position" in capabilities,needsWakeLock=true,needsWifiLock=true,needsPhoneHeading="heading" in capabilities,needsPhoneMotion=capabilities.any{it in setOf("attitude","rotation")},needsPhonePressure="pressure" in capabilities))
        _status.value=LocalNmeaServerRuntimeStatus(requested=true,generation=_status.value.generation+1,message="Starting the NMEA service")
    }

    private fun spec(peer:String)=NmeaConnectionSpec(id="local-nmea-service",host=NmeaPeerGuard.host(peer),receive=false,send=true,feed=settings.feed,forwardFrom=settings.forwardFrom,capabilities=settings.capabilities)

    @Synchronized private fun forward(frame:NmeaRawFrame) {
        if(!running||settings.feed!=NmeaFeed.RAW||server.status.value.state!=SharingServerState.RUNNING)return
        server.status.value.clients.forEach { client -> encoder.forward(spec(client.address),frame)?.let { publish(it,client.id) } }
    }

    @Synchronized private fun publishDue(now:Long) {
        if(!running||!settings.serverRequested||server.status.value.state!=SharingServerState.RUNNING)return
        if(settings.feed!=NmeaFeed.RAW)server.status.value.clients.forEach { client ->
            encoder.encode(spec(client.address),now).forEach { publish(it,client.id) }
        }
        _status.value=_status.value.copy(message=if(server.status.value.clientCount==0)"Listening; waiting for a client" else "Serving ${server.status.value.clientCount} client(s)")
    }

    private fun publish(sentence:String,clientId:Long) {
        recent.addLast(sentence.trim());while(recent.size>60)recent.removeFirst()
        val queued=server.publish(sentence,clientId)
        _status.value=_status.value.copy(generatedSentences=_status.value.generatedSentences+1,queuedSentences=_status.value.queuedSentences+if(queued>0)1 else 0,recentGenerated=recent.toList())
    }

    @Synchronized fun shutdown() {
        running=false;server.stop();resources.release(RuntimeOwner.NMEA_SHARING)
        _status.value=_status.value.copy(requested=false,generation=_status.value.generation+1,message="Off")
    }
}

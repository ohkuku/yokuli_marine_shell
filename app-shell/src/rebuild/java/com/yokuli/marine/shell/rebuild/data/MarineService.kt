package com.yokuli.marine.shell.rebuild.data

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yokuli.marine.shell.R
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class MarineService : Service(), LocationListener {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val os get()=(application as YokuliApplication).os
    private val hub get()=os.hub
    private var inputJob: Job?=null
    private var serverJob: Job?=null
    private var upstreamJob:Job?=null
    @Volatile private var tcp: Socket?=null
    @Volatile private var udp: DatagramSocket?=null
    @Volatile private var sharing: ServerSocket?=null
    private val inputLock=Any()
    private val clients=ConcurrentHashMap<Socket,Channel<String>>()
    private val sent=ConcurrentHashMap<String,Long>()
    private val inputEpoch=java.util.concurrent.atomic.AtomicLong()
    private val serverEpoch=java.util.concurrent.atomic.AtomicLong()
    private val locations by lazy { getSystemService(LocationManager::class.java) }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("marine","Yokuli OS",NotificationManager.IMPORTANCE_LOW))
    }
    private fun foreground() {
        val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,MarineService::class.java).setAction("stopAll"),PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"marine").setSmallIcon(R.drawable.ic_yokuli_os).setContentTitle("Yokuli OS")
            .setContentText(os.t("船舶数据服务运行中","Boat data service is running")).setContentIntent(intent).setOngoing(true)
            .addAction(0,os.t("停止","Stop"),stop).build()
        var type=ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if(hub.state.value.gpsOn && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) type=type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this,41,notification,if(Build.VERSION.SDK_INT>=29) type else 0)
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val action=intent?.action
        if(action=="gpsOn") hub.update { it.copy(gpsOn=true) }
        foreground()
        when(action) {
            "gpsOn" -> startGps()
            "gpsOff" -> { locations.removeUpdates(this); hub.update { it.copy(gpsOn=false,phone=null) } }
            "connect" -> connect()
            "disconnect" -> stopInput()
            "shareOn" -> startSharing()
            "shareOff" -> stopSharing()
            "send" -> sendInput(intent.getStringExtra("sentence").orEmpty())
            "upstreamOn" -> startUpstream()
            "upstreamOff" -> stopUpstream()
            "stopAll" -> { stopInput(); stopSharing(); locations.removeUpdates(this); hub.update { it.copy(gpsOn=false,phone=null) }; stopSelf() }
        }
        if(!hub.state.value.gpsOn && inputJob==null && serverJob==null) stopSelf()
        return START_NOT_STICKY // A process restart must not pretend a live connection or silently restart output.
    }
    private fun startGps() {
        try {
            val provider=if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locations.requestLocationUpdates(provider,1000,0f,this,Looper.getMainLooper())
            hub.update { it.copy(gpsOn=true,message=null) }
        } catch(_:Exception) { hub.update { it.copy(gpsOn=false,message="gps") } }
    }
    override fun onLocationChanged(location: Location) {
        if(location.isFromMockProvider) { hub.update { it.copy(phone=null,message="mock") }; return }
        val p=GeoPoint(location.latitude,location.longitude); if(!p.valid()) return
        val age=SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos
        if(age !in 0..10_000_000_000) return
        hub.update { it.copy(phone=Fix(p,"phone",location.elapsedRealtimeNanos/1_000_000,location.time,
            if(location.hasSpeed()) location.speed*1.943844 else null,
            if(location.hasBearing() && location.hasSpeed() && location.speed>=0.5f) location.bearing.toDouble() else null,
            if(location.hasAccuracy()) location.accuracy.toDouble() else null),message=null) }
    }
    override fun onProviderDisabled(provider: String) { hub.update { it.copy(phone=null,message="gps") } }
    override fun onProviderEnabled(provider: String) {}
    @Deprecated("Android callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
    private fun stopInput() {
        stopUpstream()
        inputEpoch.incrementAndGet()
        inputJob?.cancel(); inputJob=null
        runCatching { tcp?.close() }; tcp=null; udp?.close(); udp=null
        hub.resetNmea(); hub.update { it.copy(connection="off",endpoint="") }
    }
    private fun localAddress(address: InetAddress): Boolean = address.isAnyLocalAddress || address.isLoopbackAddress ||
        runCatching { NetworkInterface.getByInetAddress(address)!=null }.getOrDefault(false)
    private fun connect() {
        stopInput()
        val host=os.nmeaHost.trim(); val port=os.nmeaPort.toIntOrNull(); val protocol=os.nmeaProtocol
        if(port==null || port !in 1..65535 || (protocol=="TCP" && host.isEmpty())) { hub.update { it.copy(connection="error",message="endpoint") }; return }
        val source="$protocol ${if(protocol=="UDP") "0.0.0.0" else host}:$port"
        val epoch=inputEpoch.get()
        hub.update { it.copy(connection="connecting",endpoint=source,message=null) }
        inputJob=scope.launch {
            while(isActive) {
                var ownedTcp:Socket?=null
                var ownedUdp:DatagramSocket?=null
                try {
                    val framer=Nmea.Framer()
                    if(protocol=="TCP") {
                        val address=InetAddress.getByName(host)
                        require(!(port==sharing?.localPort && localAddress(address))) { "loop" }
                        ensureActive()
                        val socket=Socket();ownedTcp=socket;tcp=socket
                        socket.connect(InetSocketAddress(address,port),6000); socket.soTimeout=2000
                        ensureActive();if(inputEpoch.get()!=epoch) break
                        hub.update { it.copy(connection="waiting") }
                        val stream=socket.getInputStream(); val bytes=ByteArray(4096)
                        while(isActive) {
                            val count=try { stream.read(bytes) } catch(_:SocketTimeoutException) { continue }
                            if(count<0) error("closed")
                            if(inputEpoch.get()==epoch && isActive) for(line in framer.feed(bytes,count)) receive(line,source)
                        }
                    } else {
                        ensureActive()
                        val socket=DatagramSocket(null);ownedUdp=socket;udp=socket;socket.reuseAddress=true
                        socket.bind(InetSocketAddress(port)); socket.soTimeout=2000
                        hub.update { it.copy(connection="waiting") }
                        val packet=DatagramPacket(ByteArray(8192),8192)
                        while(isActive) {
                            packet.length=packet.data.size
                            try { socket.receive(packet) } catch(_:SocketTimeoutException) { continue }
                            // Datagram boundaries cannot carry partial sentences into another sender's packet.
                            val packetFramer=Nmea.Framer()
                            val bytes=packet.data.copyOfRange(packet.offset,packet.offset+packet.length)+byteArrayOf(10)
                            if(inputEpoch.get()==epoch && isActive) for(line in packetFramer.feed(bytes,bytes.size)) receive(line,"UDP ${packet.address.hostAddress}:${packet.port}")
                        }
                    }
                } catch(_:CancellationException) { break }
                catch(e:Exception) {
                    if(!isActive || inputEpoch.get()!=epoch) break
                    hub.resetNmea(); hub.update { it.copy(connection="reconnecting",message=if(e.message=="loop") "loop" else "network") }
                } finally {
                    runCatching {ownedTcp?.close()};ownedUdp?.close()
                    if(tcp===ownedTcp) tcp=null
                    if(udp===ownedUdp) udp=null
                }
                delay(3000)
            }
        }
    }
    private fun receive(line:String,source:String) {
        val now=SystemClock.elapsedRealtime()
        sent.entries.removeIf { now-it.value>15000 }
        if(sent[line]?.let { now-it<15000 } == true) return
        Nmea.accept(line,source,hub)
    }
    private fun rememberSent(line:String) {
        val now=SystemClock.elapsedRealtime();sent.entries.removeIf {now-it.value>15000}
        if(sent.size>=512) sent.clear()
        sent[line]=now
    }
    private fun stopUpstream() {upstreamJob?.cancel();upstreamJob=null;hub.update {it.copy(upstreamPublishing=false)}}
    private fun startUpstream() {
        stopUpstream()
        if(tcp?.isConnected!=true) {hub.update {it.copy(message="send")};return}
        hub.update {it.copy(upstreamPublishing=true)}
        upstreamJob=scope.launch {
            while(isActive) {
                // Publish only the phone's own position upstream. Never send a peer its own readings back.
                val data=hub.state.value
                val sentences=Nmea.output(data.copy(readings=emptyMap()),"phone")
                for(line in sentences) {
                    try {synchronized(inputLock) {
                        val socket=tcp ?: error("disconnected");rememberSent(line)
                        socket.getOutputStream().apply {write((line+"\r\n").toByteArray(Charsets.US_ASCII));flush()}
                    };hub.update {it.copy(sentToInput=it.sentToInput+1)}} catch(_:Exception) {hub.update {it.copy(message="send")}}
                }
                delay(1000)
            }
        }
    }
    private fun sendInput(text:String) {
        val clean=text.trim()
        val line=if(clean.contains('*')) clean else Nmea.checksum(clean.removePrefix("$"))
        if(!Nmea.valid(line) || clean.contains('\n') || clean.contains('\r')) { hub.update { it.copy(message="sentence") }; return }
        scope.launch { try {
            synchronized(inputLock) {
                val socket=tcp?.takeIf { it.isConnected && !it.isClosed } ?: error("disconnected")
                rememberSent(line)
                socket.getOutputStream().apply { write((line+"\r\n").toByteArray(Charsets.US_ASCII)); flush() }
            }
            hub.update { it.copy(sentToInput=it.sentToInput+1,message="sent") }
        } catch(_:Exception) { hub.update { it.copy(message="send") } } }
    }
    private fun startSharing() {
        stopSharing()
        val port=os.serverPort.toIntOrNull()
        if(port==null || port !in 1024..65535) { hub.update { it.copy(server="error",message="port") }; return }
        hub.update { it.copy(server="starting",serverPort=port,message=null) }
        val epoch=serverEpoch.get()
        serverJob=scope.launch {
            var ownedServer:ServerSocket?=null
            val ownedClients=ConcurrentHashMap<Socket,Channel<String>>()
            try {
                val address=tcp?.inetAddress
                require(!(tcp?.port==port && address!=null && localAddress(address))) { "loop" }
                ensureActive()
                val listener=ServerSocket();ownedServer=listener;sharing=listener;listener.reuseAddress=true;listener.bind(InetSocketAddress(port),8);listener.soTimeout=2000
                val addresses=Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
                    .flatMap { Collections.list(it.inetAddresses) }.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
                ensureActive();if(serverEpoch.get()!=epoch) return@launch
                hub.update { it.copy(server="running",addresses=addresses) }
                launch {
                    while(isActive) {
                        val sentences=Nmea.output(hub.state.value,os.positionSource)
                        for(line in sentences) {
                            rememberSent(line)
                            for((socket,queue) in ownedClients) if(queue.trySend(line).isFailure) { queue.close(); socket.close() }
                        }
                        delay(1000)
                    }
                }
                while(isActive) {
                    val client=try { listener.accept() } catch(_:SocketTimeoutException) { continue }
                    if(clients.size>=8) { client.close(); continue }
                    client.tcpNoDelay=true
                    val queue=Channel<String>(32);clients[client]=queue;ownedClients[client]=queue;hub.update { it.copy(clients=clients.size) }
                    launch { try {
                        val output=client.getOutputStream()
                        for(line in queue) { output.write((line+"\r\n").toByteArray(Charsets.US_ASCII)); output.flush(); hub.update { it.copy(transmitted=it.transmitted+1) } }
                    } catch(_:Exception) {} finally {clients.remove(client);ownedClients.remove(client);queue.close();client.close();hub.update {it.copy(clients=clients.size)} } }
                }
            } catch(e:Exception) { if(isActive && serverEpoch.get()==epoch) hub.update { it.copy(server="error",message=if(e.message=="loop") "loop" else "server") } }
            finally {
                runCatching {ownedServer?.close()};if(sharing===ownedServer) sharing=null
                ownedClients.forEach {(socket,queue)->runCatching {socket.close()};queue.close();clients.remove(socket)}
                hub.update {it.copy(clients=clients.size)}
            }
        }
    }
    private fun stopSharing() {
        serverEpoch.incrementAndGet()
        serverJob?.cancel(); serverJob=null
        runCatching { sharing?.close() }; sharing=null
        clients.keys.forEach { runCatching { it.close() } }; clients.values.forEach { it.close() }; clients.clear()
        hub.update { it.copy(server="off",clients=0,addresses=emptyList()) }
    }
    override fun onDestroy() {
        stopInput(); stopSharing(); locations.removeUpdates(this)
        hub.update { it.copy(gpsOn=false,phone=null) }; scope.cancel(); super.onDestroy()
    }
}

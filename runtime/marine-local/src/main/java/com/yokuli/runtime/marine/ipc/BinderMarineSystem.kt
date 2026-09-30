package com.yokuli.runtime.marine.ipc

import android.content.*
import android.os.*
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.api.*
import com.yokuli.runtime.contract.*
import com.yokuli.runtime.contract.ais.AisTrafficService
import com.yokuli.runtime.contract.chart.ChartDataService
import com.yokuli.runtime.contract.device.DeviceCatalogSnapshot
import com.yokuli.runtime.contract.device.DeviceRuntimeService
import com.yokuli.runtime.contract.navigation.NavigationSessionService
import com.yokuli.runtime.contract.planning.RouteAnalysisService
import com.yokuli.runtime.contract.planning.RoutePlanningService
import com.yokuli.runtime.marine.MarineSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shell / 通知进程共享的 Marine Core Binder 客户端；不实例化本地数据仓库或采集资源。 */
class BinderMarineSystem private constructor(context: Context) : MarineSystem, AutoCloseable {
    private val client = CoreClient(context)
    override val connection = client.connection
    override val hardwareLab = client.proxy<com.yokuli.runtime.contract.hardware.HardwareLabService>("hardwareLab")
    private val clockMirror = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope ->
        scope.launch { hardwareLab.state.collect { if (it.ready) {
            com.yokuli.runtime.contract.time.MarineTime.applyRemote(it.clock)
            if (com.yokuli.runtime.marine.notification.NotificationProcessRole.isNotificationProcess())
                com.yokuli.runtime.contract.hardware.VirtualHostServices.configure(it.mode != com.yokuli.runtime.contract.hardware.HardwareMode.REAL, it.power, it.storage.fault)
        } } }
    }
    override val devices = client.proxy<DeviceRuntimeService>("devices")
    override val residency = client.proxy<RuntimeResidencyService>("residency")
    override val charts = client.proxy<ChartDataService>("charts")
    override val navigation = client.proxy<NavigationSessionService>("navigation")
    override val analysis = client.proxy<RouteAnalysisService>("analysis")
    override val planning = client.proxy<RoutePlanningService>("planning")
    override val voyage = client.proxy<VoyageSessionService>("voyage")
    override val anchorCommands = client.proxy<AnchorCommandMonitor>("anchorCommands")
    override val ais = client.proxy<AisTrafficService>("ais")
    override val presentation = client.proxy<RuntimePresentationService>("presentation")
    override val readingHistory = client.proxy<ReadingHistoryService>("readingHistory")
    override val services = client.proxy<MarineServices>("services")
    override fun close() { clockMirror.cancel(); client.close() }
    companion object {
        @Volatile private var instance: BinderMarineSystem? = null
        fun shared(context: Context) = instance ?: synchronized(this) { instance ?: BinderMarineSystem(context.applicationContext).also { instance = it } }
    }
}

/** UNKNOWN 是传输结果未知；客户端从不换一个 ID 自动重发写命令。 */
class MarineCoreUnavailableException(val requestId: String, val outcomeUnknown: Boolean, reason: String) : IllegalStateException(reason)

private class CoreClient(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        android.util.Log.w("MarineCore", "Client operation failed: ${error.message}")
    })
    private val _connection = MutableStateFlow(RuntimeConnection("yokuli.marine", RuntimeTransport.BINDER, RuntimeReadiness.INITIALIZING, processIsolated = true))
    val connection: StateFlow<RuntimeConnection> = _connection.asStateFlow()
    private val available = MutableStateFlow<IBinder?>(null)
    private val sendLock = Mutex()
    private val slots = Semaphore(CoreWire.MAX_IN_FLIGHT)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CorePacket>>()
    private val noticeCooldown = ConcurrentHashMap<String, Long>()
    private val callbacks = ConcurrentHashMap<String, Map<Int, () -> Unit>>()
    private val streams = ConcurrentHashMap<String, Subscription>()
    private val propertyFlows = ConcurrentHashMap<String, Any>()
    private val proxies = ConcurrentHashMap<String, Any>()
    private val leases = ConcurrentHashMap<String, RemoteLease>()
    private val inbound = Channel<Pair<Int, CorePacket>>(128)
    @Volatile private var bound = false
    @Volatile private var closed = false
    @Volatile private var remote: IBinder? = null
    @Volatile private var remoteEpoch: String? = null
    @Volatile private var remoteSession: String? = null
    private var reconnect: Job? = null
    private val generations = AtomicLong()
    @Volatile private var activeService: ServiceConnection? = null
    @Volatile private var activeDeath: IBinder.DeathRecipient? = null
    private data class Subscription(val call: CoreCall, val type: Type, val accept: (Any?) -> Unit, val failure: (Throwable) -> Unit, var sequence: Long = 0, var mainSnapshot: MainUiState? = null, val unavailable: (String) -> Unit = {})

    private val listener = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code !in CoreWire.RESULT..CoreWire.ARGUMENT_CALLBACK) return super.onTransact(code, data, reply, flags)
            if (getCallingUid() != Process.myUid()) throw SecurityException("Wrong Marine Core UID")
            data.enforceInterface(CoreWire.CALLBACK)
            val packet = CoreWire.readPayload(data, CorePacket::class.java)
            if (!inbound.trySend(code to packet).isSuccess) {
                disconnect("MARINE_CLIENT_BACKPRESSURE")
                throw IllegalStateException("MARINE_CLIENT_BACKPRESSURE")
            }
            reply?.writeNoException()
            return true
        }
    }
    private fun service(generation: Long) = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed || generations.get() != generation) return
            remote = binder
            fun current() = !closed && generations.get() == generation && remote === binder
            val death = IBinder.DeathRecipient { if (current()) disconnect("MARINE_CORE_RESTARTING") }
            activeDeath = death
            scope.launch {
                try {
                    binder.linkToDeath(death, 0)
                    val hello = hello(binder)
                    require(hello.protocol == CoreWire.VERSION && hello.apkVersion == CoreWire.version(context) && hello.schema == MarineCorePorts.schema) { "MARINE_CORE_VERSION_MISMATCH" }
                    if (!current()) return@launch
                    remoteSession = UUID.randomUUID().toString()
                    sendLock.withLock {
                        if (!current()) return@withLock
                        transact(binder, CoreWire.ATTACH)
                    }
                    if (!current()) return@launch
                    remoteEpoch = hello.epoch
                    streams.values.forEach { it.sequence = 0; it.mainSnapshot = null }
                    available.value = binder
                    _connection.value = _connection.value.copy(readiness = RuntimeReadiness.INITIALIZING, reason = null)
                    streams.values.toList().forEach { subscribe(it, binder, generation) }
                    leases.values.toList().forEach { it.acquire(binder) }
                } catch (error: IllegalArgumentException) {
                    if (current()) disconnect("MARINE_CORE_VERSION_MISMATCH", retry = false)
                } catch (_: Exception) { if (current()) disconnect("MARINE_CORE_RESTARTING") }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { if (generations.get() == generation) disconnect("MARINE_CORE_RESTARTING") }
        override fun onBindingDied(name: ComponentName) { if (generations.get() == generation) disconnect("MARINE_CORE_BINDING_DIED") }
        override fun onNullBinding(name: ComponentName) { if (generations.get() == generation) disconnect("MARINE_CORE_BINDING_REFUSED") }
    }

    init {
        check(MarineCoreProcess.isShell() || com.yokuli.runtime.marine.notification.NotificationProcessRole.isNotificationProcess()) {
            "Only the Shell or notification process may create a Marine Core client"
        }
        scope.launch { for ((code, packet) in inbound) deliver(code, packet) }
        val method = MarineCorePorts.interfaces.getValue("core").getMethod("getConnection")
        val call = newCall("core", method, emptyArray())
        streams[call.id] = Subscription(call, RuntimeConnection::class.java, { value ->
            val local = value as RuntimeConnection
            _connection.value = local.copy(transport = RuntimeTransport.BINDER, processIsolated = true)
        }, { disconnect(it.message ?: "MARINE_CORE_SNAPSHOT_FAILED") })
        bind()
    }

    @Synchronized private fun bind() {
        if (closed || bound) return
        try {
            // Visible Shell grants only its current while-in-use eligibility; Core still owns all leases.
            val flags = Context.BIND_AUTO_CREATE or if (Build.VERSION.SDK_INT >= 30) Context.BIND_INCLUDE_CAPABILITIES else 0
            val next = service(generations.incrementAndGet())
            activeService = next
            bound = context.bindService(Intent(context, MarineCoreBinderService::class.java), next, flags)
            if (!bound) disconnect("MARINE_CORE_BINDING_REFUSED")
        } catch (_: Exception) { disconnect("MARINE_CORE_BINDING_REFUSED") }
    }
    @Synchronized private fun disconnect(reason: String, retry: Boolean = true) {
        if (closed) return
        generations.incrementAndGet()
        available.value = null
        val oldBinder = remote
        val oldSession = remoteSession
        val oldDeath = activeDeath
        if (oldBinder != null && oldDeath != null) runCatching { oldBinder.unlinkToDeath(oldDeath, 0) }
        activeDeath = null
        remote = null
        remoteSession = null
        remoteEpoch = null
        if (oldBinder != null && oldSession != null) scope.launch {
            runCatching { sendLock.withLock { transact(oldBinder, CoreWire.DETACH, session = oldSession) } }
        }
        _connection.value = _connection.value.copy(readiness = RuntimeReadiness.UNAVAILABLE, reason = reason)
        streams.values.forEach { it.unavailable(reason) }
        pending.forEach { (id, result) -> result.completeExceptionally(MarineCoreUnavailableException(id, true, "MARINE_COMMAND_OUTCOME_UNKNOWN")) }
        pending.clear()
        callbacks.clear()
        val oldService = activeService
        activeService = null
        if (bound && oldService != null) { runCatching { context.unbindService(oldService) }; bound = false }
        if (retry && reconnect?.isActive != true) reconnect = scope.launch {
            delay(1_000)
            reconnect = null
            withContext(Dispatchers.Main) { bind() }
        }
    }

    private suspend fun deliver(code: Int, packet: CorePacket) {
        if (packet.epoch != remoteEpoch || packet.session != remoteSession || available.value == null) return
        when (code) {
            CoreWire.ARGUMENT_CALLBACK -> packet.callbackIndex?.let { callbacks[packet.id]?.get(it) }?.let { action -> withContext(Dispatchers.Main.immediate) { runCatching(action) } }
            CoreWire.RESULT -> pending.remove(packet.id)?.complete(packet)
            CoreWire.VALUE -> streams[packet.id]?.let { stream ->
                if (packet.sequence <= stream.sequence) return
                stream.sequence = packet.sequence
                if (packet.error != null) stream.failure(IllegalStateException(packet.error))
                else try {
                    val value = if (stream.type == MainUiState::class.java) {
                        require(!packet.delta || stream.mainSnapshot != null) { "MARINE_INITIAL_SNAPSHOT_REQUIRED" }
                        MainStateWire.apply(stream.mainSnapshot ?: MainUiState(), packet.value, packet.delta).also { stream.mainSnapshot = it }
                    } else MarineCoreCodec.gson.fromJson<Any?>(packet.value, stream.type)
                    stream.accept(value)
                }
                catch (error: Exception) { stream.failure(error) }
            }
        }
    }

    @Suppress("UNCHECKED_CAST") fun <T> proxy(port: String): T = proxies.getOrPut(port) {
        val type = MarineCorePorts.interfaces.getValue(port)
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, rawArguments ->
            val args = rawArguments ?: emptyArray()
            when (method.name) {
                "toString" -> return@newProxyInstance "MarineCore[$port]"
                "hashCode" -> return@newProxyInstance System.identityHashCode(self)
                "equals" -> return@newProxyInstance self === args.firstOrNull()
            }
            if (port == "core" && method.name == "getConnection") return@newProxyInstance connection
            MarineCorePorts.nested(method.returnType)?.let { return@newProxyInstance proxy<Any>(it) }
            if (Flow::class.java.isAssignableFrom(method.returnType)) return@newProxyInstance flow(port, method, args)
            if (DisplayLease::class.java.isAssignableFrom(method.returnType)) {
                val lease = RemoteLease(newCall(port, method, args))
                leases[lease.call.id] = lease
                scope.launch { lease.acquire() }
                return@newProxyInstance lease
            }
            val realArguments = if (MarineCorePorts.isSuspend(method)) args.dropLast(1).toTypedArray() else args
            val call = newCall(port, method, realArguments)
            val actions = realArguments.mapIndexedNotNull { index, value -> (value as? Function0<*>)?.let { index to { it.invoke(); Unit } } }.toMap()
            if (MarineCorePorts.isSuspend(method)) {
                val continuation = args.last() as Continuation<Any?>
                val awaitReply: suspend () -> Any? = {
                    suspendCancellableCoroutine { waiter ->
                        val task = scope.launch {
                            try { waiter.resume(invoke(call, method, actions)) }
                            catch (error: Throwable) { waiter.resumeWithException(error) }
                        }
                        // scope 已关闭时 launch 主体不会执行，也必须结束调用方的等待。
                        task.invokeOnCompletion { failure -> if (failure != null) waiter.cancel(failure) }
                        // UI 超时/离开立即释放等待；已接受的写操作继续由 Core 完成，不因取消重复发送。
                        if (MarineCorePorts.cancellableRead(port, method)) waiter.invokeOnCancellation {
                            task.cancel()
                            // acquireSnapshot 可能已回包、但调用方尚未恢复就被取消；此时 invoke
                            // 已正常返回，不会再经过它的取消 catch。按调用 ID 幂等放弃读取，
                            // 也释放这个从未交给调用方的远端句柄。写命令不进入这条路径。
                            scope.launch { abandon(call.id) }
                        }
                    }
                }
                return@newProxyInstance awaitReply.startCoroutineUninterceptedOrReturn(continuation)
            }
            if (Job::class.java.isAssignableFrom(method.returnType)) return@newProxyInstance launchCommand(call, method, actions)
            val knownId = knownRequestId(port, method, realArguments)
            if (knownId != null) {
                launchCommand(call, method, actions)
                return@newProxyInstance knownId
            }
            if (method.returnType == Void.TYPE || method.returnType == Unit::class.java) {
                launchCommand(call, method, actions)
                return@newProxyInstance Unit
            }
            if (method.returnType == ComponentName::class.java) {
                // Compatibility return was Android's startService component, never a domain success receipt.
                launchCommand(call, method, actions)
                return@newProxyInstance null
            }
            if (port == "photos" && method.name == "file") {
                // Same UID private media path only; no Room/controller is created by this pure resolution.
                val photo = args[0] as com.yokuli.anchorwatch.data.database.entity.AnchoragePhotoEntity
                val thumbnail = args[1] as Boolean
                val relative = if (thumbnail) photo.thumbnailRelativeFileName ?: photo.relativeFileName else photo.relativeFileName
                return@newProxyInstance java.io.File(java.io.File(context.filesDir, "anchorage_media"), java.io.File(relative).name)
            }
            error("Marine synchronous result needs an explicit non-blocking adapter: $port.${method.name}")
        }
    } as T

    private fun launchCommand(call: CoreCall, method: Method, actions: Map<Int, () -> Unit>): Job = scope.launch {
        try { invoke(call, method, actions) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val description = commandNotice(call, method)
            if (description != null) {
                val unknown = error is MarineCoreUnavailableException && error.outcomeUnknown
                // Distinct accepted writes with unknown outcomes must remain individually visible.
                val key = "${description.key}:${if (unknown) "unknown:${call.id}" else "failed"}"
                val now = SystemClock.elapsedRealtime()
                var publish = unknown
                if (!unknown) noticeCooldown.compute(key) { _, previous ->
                    if (previous == null || now - previous >= 30_000L) { publish = true; now } else previous
                }
                if (noticeCooldown.size > 256) noticeCooldown.entries.removeIf { now - it.value >= 30_000L }
                if (publish) runCatching {
                    val id = "marine-action:$key"
                    val record = com.yokuli.runtime.contract.notification.NoticeRecord(
                        id = id, publisher = description.publisher,
                        text = com.yokuli.runtime.contract.notification.NoticeText(
                            titleZh = description.actionZh + if (unknown) "：结果待确认" else "：未完成",
                            titleEn = description.actionEn + if (unknown) ": awaiting confirmation" else ": could not complete",
                            bodyZh = if (unknown) "海事服务连接中断，任务可能已经执行。请打开${description.destinationZh}检查当前状态，确认前不要重复提交。"
                                else "海事服务未能完成这项请求。请打开${description.destinationZh}检查连接和当前状态，再决定是否重试。",
                            bodyEn = if (unknown) "The marine service connection was interrupted; the action may have completed. Open ${description.destinationEn} and check its state before submitting again."
                                else "The marine service could not complete this request. Open ${description.destinationEn} to check the connection and current state before retrying.",
                            code = if (unknown) "MARINE_COMMAND_OUTCOME_UNKNOWN" else "MARINE_COMMAND_NOT_COMPLETED",
                            arguments = mapOf("operation" to "${call.port}.${method.name}", "requestId" to call.id, "reason" to error.message.orEmpty().take(256)),
                        ), occurredAtUtcMillis = System.currentTimeMillis(),
                        level = com.yokuli.runtime.contract.notification.NoticeLevel.WARNING,
                        target = description.target,
                        domainEventId = "marine-transport:${call.id}", aggregationKey = id, category = "runtime",
                    )
                    com.yokuli.runtime.marine.notification.BinderNotificationClient.shared(context).execute(
                        com.yokuli.runtime.contract.notification.NoticeCommand("marine-transport:${call.id}", com.yokuli.runtime.contract.notification.NoticeOperation.PUBLISH, record))
                }
            }
            throw error
        }
    }

    private fun knownRequestId(port: String, method: Method, args: Array<out Any?>): String? = when {
        port == "anchor" && method.name in setOf("requestArm", "requestPauseWatch", "requestResumeWatch", "requestLiftAnchor") -> args.last() as String
        port == "voyage" && method.name == "request" -> (args[0] as VoyageRequest).requestId
        port == "voyages" && method.name == "requestCommand" -> (args[0] as VoyageRequest).requestId
        port == "voyages" && method.name == "captureMoment" -> args.last() as String
        else -> null
    }

    private fun newCall(port: String, method: Method, args: Array<out Any?>): CoreCall {
        val types = MarineCorePorts.argumentTypes(method)
        val values = JsonArray()
        args.forEachIndexed { index, value -> values.add(if (value is Function0<*>) JsonNull.INSTANCE else MarineCoreCodec.gson.toJsonTree(value, types[index])) }
        return CoreCall(UUID.randomUUID().toString(), port, MarineCorePorts.key(method), values)
    }

    private fun flow(port: String, method: Method, args: Array<out Any?>): Any {
        val canonicalPort = if (method.name == "getState" && MarineStateReader::class.java.isAssignableFrom(MarineCorePorts.interfaces.getValue(port))) "services" else port
        val canonicalMethod = if (canonicalPort != port) MarineCorePorts.interfaces.getValue("services").getMethod("getState") else method
        val key = "$canonicalPort:${MarineCorePorts.key(canonicalMethod)}:${args.contentToString()}"
        if (StateFlow::class.java.isAssignableFrom(method.returnType)) return propertyFlows.getOrPut(key) {
            val type = MarineCorePorts.valueType(canonicalMethod)
            val mutable = MutableStateFlow(seed(type))
            val call = newCall(canonicalPort, canonicalMethod, args)
            val subscription = Subscription(call, type, { mutable.value = it }, { disconnect("MARINE_CORE_SNAPSHOT_FAILED:${it.message}") }, unavailable = { reason ->
                // 断线后保留最后目录供展示，但必须等待重连的新 Core 快照才可恢复实时标记。
                (mutable.value as? com.yokuli.runtime.contract.hardware.HardwareLabSnapshot)?.let {
                    val clock = com.yokuli.runtime.contract.time.MarineTime.snapshot()
                    if (clock.virtual) com.yokuli.runtime.contract.time.MarineTime.applyRemote(clock.copy(paused = true, revision = clock.revision + 1))
                    mutable.value = it.copy(ready = false, error = reason)
                }
                (mutable.value as? DeviceCatalogSnapshot)?.let { mutable.value = it.copy(ready = false, error = reason) }
            })
            streams[call.id] = subscription
            scope.launch { available.value?.let { subscribe(subscription, it) } }
            mutable.asStateFlow()
        }
        val source = callbackFlow<Any?> {
            val call = newCall(canonicalPort, canonicalMethod, args)
            val subscription = Subscription(call, MarineCorePorts.valueType(canonicalMethod), { if (trySend(it).isFailure) close(IllegalStateException("MARINE_STREAM_BACKPRESSURE")) }, { close(it) })
            streams[call.id] = subscription
            available.value?.let { subscribe(subscription, it) }
            awaitClose {
                streams.remove(call.id)
                scope.launch { release(call.id) }
            }
        }
        return if (SharedFlow::class.java.isAssignableFrom(method.returnType)) propertyFlows.getOrPut(key) {
            source.shareIn(scope, SharingStarted.WhileSubscribed(0), replay = 0)
        } else source
    }

    private fun seed(type: Type): Any? {
        if (type is ParameterizedType && type.rawType == List::class.java) return emptyList<Any>()
        val raw = (if (type is ParameterizedType) type.rawType else type) as Class<*>
        if (raw == MainUiState::class.java) return MainUiState()
        return raw.getDeclaredConstructor().newInstance()
    }

    private suspend fun subscribe(subscription: Subscription, binder: IBinder, generation: Long = generations.get()) {
        if (streams[subscription.call.id] !== subscription || remote !== binder || generations.get() != generation) return
        try { sendLock.withLock { transact(binder, CoreWire.SUBSCRIBE, subscription.call) } }
        catch (_: Exception) { if (remote === binder && generations.get() == generation) disconnect("MARINE_CORE_SUBSCRIPTION_FAILED") }
    }
    private suspend fun invoke(call: CoreCall, method: Method, actions: Map<Int, () -> Unit>): Any? {
        if (closed || !slots.tryAcquire()) throw MarineCoreUnavailableException(call.id, false, "MARINE_CLIENT_QUEUE_FULL_OR_CLOSED")
        var sent = false
        try {
            val binder = withTimeoutOrNull(10_000) { available.filterNotNull().first() }
                ?: throw MarineCoreUnavailableException(call.id, false, "MARINE_CORE_NOT_CONNECTED")
            val generation = generations.get()
            val result = CompletableDeferred<CorePacket>()
            pending[call.id] = result
            if (actions.isNotEmpty()) callbacks[call.id] = actions
            sendLock.withLock {
                if (available.value !== binder || generations.get() != generation) throw MarineCoreUnavailableException(call.id, false, "MARINE_CORE_CONNECTION_CHANGED")
                try {
                    sent = true
                    transact(binder, CoreWire.CALL, call)
                } catch (error: Exception) {
                    val unattached = error is SecurityException && error.message == "MARINE_CLIENT_NOT_ATTACHED"
                    if (!unattached) throw error
                    sent = false
                    // Core rejected before accepting this CALL. Same-session ATTACH is idempotent;
                    // it must not release retained snapshots or create a new domain request ID.
                    transact(binder, CoreWire.ATTACH)
                    streams.values.toList().forEach { subscription ->
                        subscription.sequence = 0
                        subscription.mainSnapshot = null
                        transact(binder, CoreWire.SUBSCRIBE, subscription.call)
                    }
                    leases.values.toList().forEach { it.restoreDetached(binder) }
                    sent = true
                    transact(binder, CoreWire.CALL, call)
                }
            }
            val packet = withTimeoutOrNull(180_000) { result.await() }
                ?: throw MarineCoreUnavailableException(call.id, true, "MARINE_COMMAND_OUTCOME_UNKNOWN")
            packet.error?.let {
                if (it.contains("MARINE_COMMAND_OUTCOME_UNKNOWN")) throw MarineCoreUnavailableException(call.id, true, it)
                throw IllegalStateException(it)
            }
            return if (method.returnType == Void.TYPE || MarineCorePorts.valueType(method) == Unit::class.java) Unit
            else MarineCoreCodec.gson.fromJson<Any?>(packet.value, MarineCorePorts.valueType(method))
        } catch (cancelled: CancellationException) {
            if (sent && MarineCorePorts.cancellableRead(call.port, method)) withContext(NonCancellable) { abandon(call.id) }
            throw cancelled
        } catch (error: DeadObjectException) {
            disconnect("MARINE_CORE_RESTARTING")
            throw MarineCoreUnavailableException(call.id, sent, "MARINE_COMMAND_OUTCOME_UNKNOWN")
        } finally {
            pending.remove(call.id)
            callbacks.remove(call.id)
            slots.release()
        }
    }

    private inner class RemoteLease(val call: CoreCall) : DisplayLease {
        private val released = AtomicBoolean(false)
        private var acquiredSession: String? = null
        suspend fun acquire(binder: IBinder? = available.value) {
            if (released.get() || binder == null || acquiredSession == remoteSession) return
            sendLock.withLock {
                if (released.get() || remote !== binder || acquiredSession == remoteSession) return@withLock
                transact(binder, CoreWire.CALL, call)
                acquiredSession = remoteSession
            }
        }
        // Called only while sendLock is held, after Core explicitly rejected the detached session.
        fun restoreDetached(binder: IBinder) {
            if (released.get()) return
            transact(binder, CoreWire.CALL, call)
            acquiredSession = remoteSession
        }
        override fun close() {
            if (!released.compareAndSet(false, true)) return
            leases.remove(call.id)
            scope.launch { release(call.id) }
        }
    }
    private suspend fun release(id: String) {
        val binder = available.value ?: return
        runCatching { sendLock.withLock { transact(binder, CoreWire.RELEASE, id = id) } }
    }
    private suspend fun abandon(id: String) {
        val binder = available.value ?: return
        runCatching { sendLock.withLock { transact(binder, CoreWire.ABANDON_READ, id = id) } }
    }
    private fun hello(binder: IBinder): CoreHello {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(CoreWire.DESCRIPTOR); data.writeInt(CoreWire.VERSION)
            check(binder.transact(CoreWire.HELLO, data, reply, 0))
            reply.readException()
            return MarineCoreCodec.gson.fromJson(reply.readString(), CoreHello::class.java)
        } finally { data.recycle(); reply.recycle() }
    }
    private fun transact(binder: IBinder, code: Int, call: CoreCall? = null, id: String? = null, session: String? = remoteSession) {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(CoreWire.DESCRIPTOR); data.writeInt(CoreWire.VERSION); data.writeStrongBinder(listener); data.writeString(session)
            call?.let { CoreWire.writePayload(context, data, it) }
            id?.let(data::writeString)
            check(binder.transact(code, data, reply, 0)) { "UNSUPPORTED_MARINE_TRANSACTION" }
            reply.readException()
        } finally { data.recycle(); reply.recycle() }
    }
    fun close() {
        if (closed) return
        val binder = remote
        val death = activeDeath
        val service = activeService
        closed = true
        generations.incrementAndGet()
        available.value = null
        _connection.value = _connection.value.copy(readiness = RuntimeReadiness.UNAVAILABLE, reason = "MARINE_CLIENT_CLOSED")
        streams.values.forEach { it.unavailable("MARINE_CLIENT_CLOSED") }
        pending.forEach { (id, result) -> result.completeExceptionally(MarineCoreUnavailableException(id, true, "MARINE_COMMAND_OUTCOME_UNKNOWN")) }
        scope.launch {
            runCatching { binder?.let { transact(it, CoreWire.DETACH); death?.let { recipient -> it.unlinkToDeath(recipient, 0) } } }
            if (bound && service != null) { runCatching { context.unbindService(service) }; bound = false }
            inbound.close()
            scope.cancel()
        }
    }
}

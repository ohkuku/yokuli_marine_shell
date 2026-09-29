package com.yokuli.runtime.marine.ipc

import android.app.Service
import android.content.Intent
import android.os.*
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.yokuli.anchorwatch.api.DisplayLease
import com.yokuli.runtime.contract.ais.AisCommand
import com.yokuli.runtime.contract.chart.ChartDataSnapshot
import com.yokuli.runtime.marine.InProcessMarineSystem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 默认进程是唯一 Marine Core；只导出固定领域端口，不暴露 DAO、控制器、任意反射入口。
 * Binder 断开只释放这个客户端的显示/查询租约，航行、守锚与后台收发继续由原所有者管理。
 */
@AndroidEntryPoint
class MarineCoreBinderService : Service() {
    @Inject lateinit var systemProvider: javax.inject.Provider<InProcessMarineSystem>
    @Inject lateinit var recoveryProvider: javax.inject.Provider<com.yokuli.anchorwatch.runtime.MarineRecoveryBarrier>
    @Inject lateinit var hardwareProvider: javax.inject.Provider<com.yokuli.runtime.marine.hardware.LocalHardwareLabService>
    private val system get() = systemProvider.get()
    // Hilt/Room/恢复日志只能在工作线程构造，不能堵塞 Service 的启动确认或 UI 输入。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clients = ConcurrentHashMap<IBinder, Client>()
    private val epoch = UUID.randomUUID().toString()

    private inner class Client(val callback: IBinder, val session: String) {
        val subscriptions = ConcurrentHashMap<String, Job>()
        val queries = ConcurrentHashMap<String, Job>()
        val leases = ConcurrentHashMap<String, DisplayLease>()
        val snapshots = ConcurrentHashMap<String, String>()
        val retained = ConcurrentHashMap<String, AisCommand.RetainTarget>()
        val abandoned = ConcurrentHashMap.newKeySet<String>()
        val released = ConcurrentHashMap.newKeySet<String>()
        val operations = ConcurrentHashMap.newKeySet<String>()
        val callbackDeliveries = ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<Job>>()
        val sendLock = Mutex()
        @Volatile var closed = false
        val death = IBinder.DeathRecipient { disconnect(callback) }
        suspend fun send(code: Int, packet: CorePacket) = withContext(Dispatchers.IO) {
            sendLock.withLock {
                if (closed) return@withLock
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(CoreWire.CALLBACK)
                    CoreWire.writePayload(this@MarineCoreBinderService, data, packet.copy(epoch = epoch, session = session))
                    check(callback.transact(code, data, null, IBinder.FLAG_ONEWAY)) { "CLIENT_CALLBACK_UNSUPPORTED" }
                } catch (_: Exception) { disconnect(callback) }
                finally { data.recycle() }
            }
        }
    }

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(CoreWire.DESCRIPTOR); return true }
            if (code !in CoreWire.HELLO..CoreWire.ABANDON_READ) return super.onTransact(code, data, reply, flags)
            checkCaller()
            data.enforceInterface(CoreWire.DESCRIPTOR)
            require(data.readInt() == CoreWire.VERSION) { "MARINE_PROTOCOL_MISMATCH" }
            if (code == CoreWire.HELLO) {
                reply?.writeNoException()
                reply?.writeString(MarineCoreCodec.gson.toJson(CoreHello(CoreWire.VERSION, CoreWire.version(this@MarineCoreBinderService), MarineCorePorts.schema, epoch)))
                return true
            }
            val callback = requireNotNull(data.readStrongBinder())
            val session = data.readString().orEmpty()
            require(session.length in 1..80) { "INVALID_MARINE_CLIENT_SESSION" }
            if (code == CoreWire.ATTACH) {
                require(clients.size < CoreWire.MAX_CLIENTS || clients.containsKey(callback)) { "TOO_MANY_MARINE_CLIENTS" }
                disconnect(callback)
                clients.computeIfAbsent(callback) { Client(it, session).also { c -> callback.linkToDeath(c.death, 0) } }
                reply?.writeNoException()
                return true
            }
            val client = clients[callback] ?: throw SecurityException("MARINE_CLIENT_NOT_ATTACHED")
            require(client.session == session) { "STALE_MARINE_CLIENT_SESSION" }
            when (code) {
                CoreWire.CALL, CoreWire.SUBSCRIBE -> {
                    val call = CoreWire.readPayload(data, CoreCall::class.java)
                    require(call.id.length in 1..160 && call.port.length < 40 && call.method.length < 4096)
                    val method = MarineCorePorts.method(call.port, call.method)
                    require(MarineCorePorts.nested(method.returnType) == null) { "NESTED_PORT_IS_NOT_A_WIRE_VALUE" }
                    require(call.arguments.size() == MarineCorePorts.argumentTypes(method).size)
                    if (code == CoreWire.SUBSCRIBE) {
                        require(Flow::class.java.isAssignableFrom(method.returnType))
                        require(client.subscriptions.size < CoreWire.MAX_SUBSCRIPTIONS || client.subscriptions.containsKey(call.id))
                        subscribe(client, call, method)
                    } else {
                        require(!Flow::class.java.isAssignableFrom(method.returnType))
                        require(client.operations.size < CoreWire.MAX_IN_FLIGHT) { "MARINE_COMMAND_QUEUE_FULL" }
                        require(client.operations.add(call.id)) { "DUPLICATE_TRANSPORT_REQUEST" }
                        execute(client, call, method)
                    }
                }
                CoreWire.RELEASE -> {
                    val id = data.readString().orEmpty()
                    require(id.length <= 160)
                    if (id in client.operations) client.released.add(id)
                    client.subscriptions.remove(id)?.cancel()
                    CoreMutationLifetime.scope.launch { runCatching { client.leases.remove(id)?.close() } }
                }
                CoreWire.ABANDON_READ -> {
                    val id = data.readString().orEmpty()
                    require(id.length <= 160)
                    if (id in client.operations) client.abandoned.add(id)
                    client.queries.remove(id)?.cancel()
                    CoreMutationLifetime.scope.launch { runCatching { client.snapshots.remove(id)?.let { system.charts.releaseSnapshot(it) } } }
                }
                CoreWire.DETACH -> disconnect(callback)
            }
            reply?.writeNoException()
            return true
        }
    }

    override fun onCreate() {
        super.onCreate()
        check(MarineCoreProcess.isCore(this)) { "Marine Core service must live in the default process" }
        // A crash during descriptor preparation cannot leave an unbounded private wire cache.
        scope.launch(Dispatchers.IO) { java.io.File(cacheDir, "core-wire").listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 600_000) it.delete() } }
    }
    override fun onBind(intent: Intent): IBinder = binder

    private fun checkCaller() {
        if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("Marine Core only accepts its own UID")
    }

    private fun subscribe(client: Client, call: CoreCall, method: Method) {
        client.subscriptions.remove(call.id)?.cancel()
        client.released.remove(call.id)
        val sequence = AtomicLong()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                @Suppress("UNCHECKED_CAST") val flow = invoke(client, call, method) as Flow<Any?>
                val updates = if (kotlinx.coroutines.flow.SharedFlow::class.java.isAssignableFrom(method.returnType) && !kotlinx.coroutines.flow.StateFlow::class.java.isAssignableFrom(method.returnType) || method.name == "getEvents") flow else flow.conflate()
                var previousMain: com.yokuli.anchorwatch.MainUiState? = null
                updates.collect { value ->
                    val delta = value is com.yokuli.anchorwatch.MainUiState && previousMain != null
                    val tree = withContext(Dispatchers.Default) {
                        if (value is com.yokuli.anchorwatch.MainUiState) MainStateWire.difference(previousMain, value)
                        else MarineCoreCodec.gson.toJsonTree(value, MarineCorePorts.valueType(method))
                    }
                    if (delta && tree.asJsonObject.size() == 0) return@collect
                    client.send(CoreWire.VALUE, CorePacket(call.id, sequence.incrementAndGet(), delta = delta, value = tree))
                    if (value is com.yokuli.anchorwatch.MainUiState) previousMain = value
                    // High-rate sensors keep original measurement times; transport coalesces display frames only.
                    if (call.port == "services" && method.name == "getState") delay(50)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { client.send(CoreWire.VALUE, CorePacket(call.id, sequence.incrementAndGet(), error = reason(error))) }
        }
        client.subscriptions[call.id] = job
        job.start()
    }

    private fun execute(client: Client, call: CoreCall, method: Method) {
        val read = MarineCorePorts.cancellableRead(call.port, method)
        val executor = if (read) scope else CoreMutationLifetime.scope
        val job = executor.launch(start = CoroutineStart.LAZY) {
            try {
                if (!read && call.port != "hardwareLab" && call.port != "display" && !(call.port == "residency" && method.name in setOf("retryRecovery", "exit")) && !(call.port == "charts" && method.name == "releaseSnapshot") && !(call.port == "photos" && method.name == "file")) recoveryProvider.get().ensureRecovered()
                val result = if (call.port == "charts" && method.name == "acquireSnapshot") {
                    // Acquiring a domain handle must publish its ID even if the UI disappears at the await boundary.
                    withContext(NonCancellable) { invoke(client, call, method) }
                } else invoke(client, call, method)
                if (result is Job) {
                    val completion = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
                    val hook = result.invokeOnCompletion { completion.set(it) }
                    try { result.join(); completion.get()?.let { throw it } } finally { hook.dispose() }
                }
                // Success callbacks (save & close, etc.) must reach Shell before the terminal response releases them.
                client.callbackDeliveries.remove(call.id)?.joinAll()
                when {
                    result is DisplayLease -> {
                        if (client.closed || call.id in client.released) result.close()
                        else client.leases[call.id] = result
                    }
                    result is ChartDataSnapshot -> {
                        if (client.closed || call.id in client.abandoned || !currentCoroutineContext().isActive) withContext(NonCancellable) { system.charts.releaseSnapshot(result.id) }
                        else client.snapshots[call.id] = result.id
                    }
                    call.port == "charts" && method.name == "releaseSnapshot" -> {
                        val id = call.arguments[0].asString
                        client.snapshots.entries.removeAll { it.value == id }
                    }
                }
                if (call.port == "ais" && method.name == "command") {
                    val command = MarineCoreCodec.gson.fromJson(call.arguments[0], AisCommand::class.java)
                    if (command is AisCommand.RetainTarget) {
                        val key = "${command.ownerId}:${command.mmsi}"
                        if (command.retain) {
                            if (client.closed) withContext(NonCancellable) { system.ais.command(command.copy(retain = false, requestId = UUID.randomUUID().toString())) }
                            else client.retained[key] = command
                        } else client.retained.remove(key)
                    }
                }
                val value = if (result is Job || result is DisplayLease || result === Unit) JsonNull.INSTANCE
                    else withContext(Dispatchers.Default) { MarineCoreCodec.gson.toJsonTree(result, MarineCorePorts.valueType(method)) }
                client.send(CoreWire.RESULT, CorePacket(call.id, value = value))
            } catch (cancelled: CancellationException) {
                // A canceled read has no write receipt. Accepted domain mutations never use this path.
                if (!read) client.send(CoreWire.RESULT, CorePacket(call.id, error = "MARINE_COMMAND_OUTCOME_UNKNOWN"))
                throw cancelled
            } catch (error: Exception) { client.send(CoreWire.RESULT, CorePacket(call.id, error = reason(error))) }
            finally {
                client.operations.remove(call.id)
                client.callbackDeliveries.remove(call.id)
                client.queries.remove(call.id)
                client.abandoned.remove(call.id)
                client.released.remove(call.id)
            }
        }
        if (read) client.queries[call.id] = job
        job.start()
    }

    private suspend fun invoke(client: Client, call: CoreCall, method: Method): Any? {
        val argumentTypes = MarineCorePorts.argumentTypes(method)
        val args = argumentTypes.mapIndexed { index, type ->
            if (method.parameterTypes[index] == Function0::class.java) {
                {
                    val delivery = CoreMutationLifetime.scope.launch(start = CoroutineStart.LAZY) {
                        client.send(CoreWire.ARGUMENT_CALLBACK, CorePacket(call.id, callbackIndex = index))
                    }
                    client.callbackDeliveries.computeIfAbsent(call.id) { java.util.concurrent.CopyOnWriteArrayList() }.add(delivery)
                    delivery.start()
                    Unit
                }
            } else MarineCoreCodec.gson.fromJson<Any?>(call.arguments[index], type)
        }.toMutableList()
        // 故障清除、恢复时钟和退出演练不依赖已注入故障的业务存储恢复。
        val target = if (call.port == "hardwareLab") hardwareProvider.get() else MarineCorePorts.target(system, call.port)
        if (!MarineCorePorts.isSuspend(method)) return try { method.invoke(target, *args.toTypedArray()) }
            catch (error: InvocationTargetException) { throw error.targetException }
        return suspendCoroutine { continuation ->
            args.add(continuation)
            try {
                val result = method.invoke(target, *args.toTypedArray())
                if (result !== COROUTINE_SUSPENDED) continuation.resume(result)
            } catch (error: InvocationTargetException) { continuation.resumeWithException(error.targetException) }
            catch (error: Exception) { continuation.resumeWithException(error) }
        }
    }

    private fun reason(error: Throwable): String = (if (error is InvocationTargetException) error.targetException else error).let {
        "${it.javaClass.simpleName}:${it.message.orEmpty()}".take(1024)
    }
    private fun disconnect(callback: IBinder) {
        val client = clients.remove(callback) ?: return
        client.closed = true
        runCatching { callback.unlinkToDeath(client.death, 0) }
        client.subscriptions.values.forEach(Job::cancel)
        client.queries.values.forEach(Job::cancel)
        CoreMutationLifetime.scope.launch {
            client.leases.values.forEach { runCatching { it.close() } }
            client.leases.clear()
            client.snapshots.values.forEach { runCatching { system.charts.releaseSnapshot(it) } }
            client.snapshots.clear()
            client.retained.values.forEach { runCatching { system.ais.command(it.copy(retain = false, requestId = UUID.randomUUID().toString())) } }
            client.retained.clear()
        }
    }
    override fun onDestroy() {
        clients.keys.toList().forEach(::disconnect)
        // Client handle cleanup belongs to the process scope and survives this bound Service.
        scope.cancel()
        super.onDestroy()
    }
}

/** 已接受写命令归进程而非某次 bind；UI 离开导致 Service 销毁也不能截断落盘。 */
private object CoreMutationLifetime { val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }

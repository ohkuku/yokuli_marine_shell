package com.yokuli.runtime.marine.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.yokuli.runtime.contract.notification.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/** 进程级客户端复用一个连接；断线只恢复订阅和快照，不重发结果未知的业务命令。 */
class BinderNotificationClient private constructor(context: Context) : NotificationClient {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(NotificationSnapshot())
    private val _connection = MutableStateFlow(NoticeConnection.CONNECTING)
    override val snapshot = _snapshot.asStateFlow()
    override val connection = _connection.asStateFlow()
    private val _results = MutableStateFlow<Map<String, NoticeCommandResult>>(emptyMap())
    override val results = _results.asStateFlow()
    private val commandSlots = Semaphore(32)
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private val transport = Mutex()
    @Volatile private var remote: IBinder? = null
    @Volatile private var bound = false
    @Volatile private var closed = false
    private var reconnect: Job? = null
    private val death = IBinder.DeathRecipient { disconnect() }
    private val listener = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != NoticeWire.CHANGED) return super.onTransact(code, data, reply, flags)
            if (getCallingUid() != Process.myUid()) throw SecurityException("Wrong notification service UID")
            data.enforceInterface(NoticeWire.CALLBACK)
            data.readString(); data.readLong()
            refreshes.trySend(Unit)
            return true
        }
    }
    private val service = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            remote = binder
            _connection.value = NoticeConnection.CONNECTING
            scope.launch {
                try {
                    binder.linkToDeath(death, 0)
                    transport.withLock {
                        val hello = NoticeWire.gson.fromJson(call(binder, NoticeWire.HELLO), NoticeServiceInfo::class.java)
                        if (hello.protocolMajor != NotificationProtocol.MAJOR || !hello.capabilities.containsAll(NoticeCapability.entries)) {
                            _connection.value = NoticeConnection.UNSUPPORTED
                            return@withLock
                        }
                        call(binder, NoticeWire.SUBSCRIBE) { writeStrongBinder(listener) }
                        if (remote === binder) refreshes.trySend(Unit)
                    }
                } catch (_: SecurityException) { _connection.value = NoticeConnection.UNSUPPORTED }
                catch (_: IllegalArgumentException) { _connection.value = NoticeConnection.UNSUPPORTED }
                catch (_: Exception) { disconnect() }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) = disconnect()
        override fun onBindingDied(name: ComponentName) = disconnect()
        override fun onNullBinding(name: ComponentName) = disconnect()
    }
    init {
        check(!NotificationProcessRole.isNotificationProcess()) { "Notification host cannot bind another notification owner" }
        bind()
        scope.launch { for (ignored in refreshes) refresh() }
    }
    private fun bind() {
        if (closed || bound) return
        try {
            bound = context.bindService(Intent(context, NotificationBinderService::class.java), service, Context.BIND_AUTO_CREATE)
            if (!bound) disconnect()
        } catch (_: Exception) { disconnect() }
    }
    @Synchronized private fun disconnect() {
        if (closed) return
        remote?.let { runCatching { it.unlinkToDeath(death, 0) } }
        remote = null
        _connection.value = NoticeConnection.DISCONNECTED
        if (bound) { runCatching { context.unbindService(service) }; bound = false }
        if (reconnect?.isActive != true) reconnect = scope.launch {
            delay(1500)
            reconnect = null
            bind()
        }
    }
    private suspend fun refresh() = transport.withLock {
        val binder = remote ?: return@withLock
        try {
            repeat(3) {
                var first = page(binder, "", 0, 0)
                val records = first.records.toMutableList()
                var changed = false
                var offset = records.size
                while (offset < first.total) {
                    val next = page(binder, first.epoch, first.revision, offset)
                    if (next.changed) { changed = true; break }
                    require(next.records.isNotEmpty() && next.records.size <= NotificationProtocol.PAGE_SIZE)
                    records.addAll(next.records); offset = records.size
                }
                if (!changed) {
                    require(records.size <= NotificationProtocol.MAX_HISTORY && records.all(::validRecord))
                    if (remote === binder) {
                        _snapshot.value = NotificationSnapshot(first.epoch, first.revision, records, first.failure)
                        _connection.value = NoticeConnection.READY
                    }
                    return@withLock
                }
            }
            refreshes.trySend(Unit)
        } catch (_: Exception) { disconnect() }
    }
    private fun page(binder: IBinder, epoch: String, revision: Long, offset: Int): NoticePage = NoticeWire.gson.fromJson(
        call(binder, NoticeWire.PAGE) { writeString(epoch); writeLong(revision); writeInt(offset) }, NoticePage::class.java)
    private suspend fun connected(): IBinder? {
        remote?.takeIf { connection.value == NoticeConnection.READY }?.let { return it }
        withTimeoutOrNull(10_000) { connection.first { it == NoticeConnection.READY || it == NoticeConnection.CLOSED || it == NoticeConnection.UNSUPPORTED } }
        return remote?.takeIf { connection.value == NoticeConnection.READY }
    }
    override suspend fun execute(command: NoticeCommand): NoticeCommandResult {
        if (closed || !commandSlots.tryAcquire()) return NoticeCommandResult(command.requestId, NoticeCommandStatus.NOT_SENT, reason = "CLIENT_QUEUE_FULL_OR_CLOSED")
        // task 属于进程级客户端。调用方/面板离开或等待超时都不取消已进入 Binder 的事务。
        val task = scope.async {
            try { executeConnected(command).also(::rememberResult) }
            finally { commandSlots.release() }
        }
        return withTimeoutOrNull(12_000) { task.await() }
            ?: NoticeCommandResult(command.requestId, NoticeCommandStatus.UNKNOWN, reason = "COMMAND_RESULT_PENDING")
    }
    @Synchronized private fun rememberResult(result: NoticeCommandResult) {
        _results.value = (_results.value.filterKeys { it != result.requestId } + (result.requestId to result)).entries.toList().takeLast(64).associate { it.key to it.value }
    }
    private suspend fun executeConnected(command: NoticeCommand): NoticeCommandResult {
        val binder = connected() ?: return NoticeCommandResult(command.requestId, NoticeCommandStatus.NOT_SENT, reason = "SERVICE_NOT_CONNECTED")
        return withContext(Dispatchers.IO) { transport.withLock {
            if (remote !== binder) return@withLock NoticeCommandResult(command.requestId, NoticeCommandStatus.NOT_SENT, reason = "SERVICE_CHANGED")
            try {
                val raw = NoticeWire.gson.toJson(command)
                if (raw.length > NoticeWire.MAX_PAYLOAD) return@withLock NoticeCommandResult(command.requestId, NoticeCommandStatus.REJECTED, reason = "COMMAND_TOO_LARGE")
                val result = NoticeWire.gson.fromJson(call(binder, NoticeWire.COMMAND) { writeString(raw) }, NoticeCommandResult::class.java)
                refreshes.trySend(Unit)
                result
            } catch (_: SecurityException) { NoticeCommandResult(command.requestId, NoticeCommandStatus.REJECTED, reason = "CAPABILITY_DENIED") }
            catch (_: IllegalArgumentException) { NoticeCommandResult(command.requestId, NoticeCommandStatus.REJECTED, reason = "INVALID_COMMAND") }
            catch (_: Exception) { disconnect(); NoticeCommandResult(command.requestId, NoticeCommandStatus.UNKNOWN, reason = "CONNECTION_LOST_DURING_COMMAND") }
        } }
    }
    override suspend fun result(requestId: String): NoticeCommandResult {
        _results.value[requestId]?.takeIf { it.status == NoticeCommandStatus.COMPLETED || it.status == NoticeCommandStatus.REJECTED }?.let { return it }
        if (closed || !commandSlots.tryAcquire()) return NoticeCommandResult(requestId, NoticeCommandStatus.UNKNOWN, reason = "RECEIPT_QUEUE_FULL_OR_CLOSED")
        val task = scope.async {
            try { queryReceipt(requestId).also(::rememberResult) }
            finally { commandSlots.release() }
        }
        return withTimeoutOrNull(12_000) { task.await() }
            ?: NoticeCommandResult(requestId, NoticeCommandStatus.UNKNOWN, reason = "RECEIPT_PENDING")
    }
    private suspend fun queryReceipt(requestId: String): NoticeCommandResult {
        val binder = connected() ?: return NoticeCommandResult(requestId, NoticeCommandStatus.UNKNOWN, reason = "SERVICE_NOT_CONNECTED")
        return withContext(Dispatchers.IO) { transport.withLock {
            try { NoticeWire.gson.fromJson(call(binder, NoticeWire.RESULT) { writeString(requestId) }, NoticeCommandResult::class.java) }
            catch (_: Exception) { disconnect(); NoticeCommandResult(requestId, NoticeCommandStatus.UNKNOWN, reason = "RECEIPT_UNAVAILABLE") }
        } }
    }
    private fun call(binder: IBinder, code: Int, write: Parcel.() -> Unit = {}): String {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(NoticeWire.DESCRIPTOR); data.writeInt(NotificationProtocol.MAJOR); data.write()
            check(binder.transact(code, data, reply, 0)) { "UNSUPPORTED_TRANSACTION" }
            reply.readException()
            return reply.readString().orEmpty()
        } finally { data.recycle(); reply.recycle() }
    }
    override fun close() {
        if (closed) return
        closed = true
        val binder = remote
        remote = null
        _connection.value = NoticeConnection.CLOSED
        scope.launch {
            runCatching { binder?.let { call(it, NoticeWire.UNSUBSCRIBE) { writeStrongBinder(listener) }; it.unlinkToDeath(death, 0) } }
            if (bound) { runCatching { context.unbindService(service) }; bound = false }
            scope.cancel()
        }
    }
    companion object {
        @Volatile private var shared: BinderNotificationClient? = null
        fun shared(context: Context): BinderNotificationClient = shared ?: synchronized(this) {
            shared ?: BinderNotificationClient(context).also { shared = it }
        }
    }
}

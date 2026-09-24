package com.yokuli.runtime.marine.notification

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.*
import com.google.gson.Gson
import com.yokuli.runtime.contract.notification.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.concurrent.ConcurrentHashMap

/** Android 传输适配；数据页有界，订阅只发版本提示，不用 Binder 推送整个消息历史。 */
internal object NoticeWire {
    const val DESCRIPTOR = "com.yokuli.runtime.notification.v1"
    const val CALLBACK = "$DESCRIPTOR.callback"
    const val HELLO = IBinder.FIRST_CALL_TRANSACTION
    const val PAGE = HELLO + 1
    const val COMMAND = HELLO + 2
    const val RESULT = HELLO + 3
    const val SUBSCRIBE = HELLO + 4
    const val UNSUBSCRIBE = HELLO + 5
    const val CHANGED = IBinder.FIRST_CALL_TRANSACTION
    const val MAX_PAYLOAD = 96_000
    val gson = Gson()
}
internal data class NoticePage(val epoch: String, val revision: Long, val total: Int, val records: List<NoticeRecord>, val failure: String?, val changed: Boolean = false)

/** 同包子进程只启动消息服务，不创建 Shell、Room、传感器、socket 或第二份海事运行时。 */
object NotificationProcessRole {
    const val SUFFIX = ":notifications"
    fun isNotificationProcess(): Boolean = Application.getProcessName().endsWith(SUFFIX)
}

class NotificationBinderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: NotificationRepository
    private val initialized = CompletableDeferred<Unit>()
    private val callbacks = ConcurrentHashMap<IBinder, IBinder.DeathRecipient>()
    override fun onCreate() {
        super.onCreate()
        check(NotificationProcessRole.isNotificationProcess()) { "Notification writer requires its dedicated process" }
        repository = NotificationRepository(applicationContext)
        scope.launch {
            try { repository.initialize(); initialized.complete(Unit) }
            catch (failure: Throwable) { initialized.completeExceptionally(failure); throw failure }
            repository.snapshot.collect { value ->
                callbacks.keys.forEach { listener ->
                    val parcel = Parcel.obtain()
                    try {
                        parcel.writeInterfaceToken(NoticeWire.CALLBACK)
                        parcel.writeString(value.epoch); parcel.writeLong(value.revision)
                        if (!listener.transact(NoticeWire.CHANGED, parcel, null, IBinder.FLAG_ONEWAY)) remove(listener)
                    } catch (_: RemoteException) { remove(listener) }
                    finally { parcel.recycle() }
                }
            }
        }
    }
    override fun onBind(intent: Intent): IBinder = endpoint
    override fun onDestroy() {
        callbacks.keys.toList().forEach(::remove)
        if (!initialized.isCompleted) initialized.completeExceptionally(CancellationException("Notification service stopped"))
        scope.cancel()
        super.onDestroy()
    }
    private fun remove(value: IBinder) { callbacks.remove(value)?.let { runCatching { value.unlinkToDeath(it, 0) } } }
    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(NoticeWire.DESCRIPTOR); return true }
            if (code !in NoticeWire.HELLO..NoticeWire.UNSUBSCRIBE) return super.onTransact(code, data, reply, flags)
            // Binder 的实际 UID 是身份，不相信请求里的 publisher 或 appId。
            if (getCallingUid() != Process.myUid()) throw SecurityException("Notification capability is limited to the application UID")
            data.enforceInterface(NoticeWire.DESCRIPTOR)
            val version = data.readInt()
            if (version != NotificationProtocol.MAJOR) throw IllegalArgumentException("UNSUPPORTED_NOTIFICATION_PROTOCOL:$version")
            requireNotNull(reply)
            val answer: String = when (code) {
                NoticeWire.HELLO -> {
                    runBlocking { initialized.await() }
                    NoticeWire.gson.toJson(NoticeServiceInfo(NotificationProtocol.MAJOR, NotificationProtocol.MINOR, repository.snapshot.value.epoch, NoticeCapability.entries.toSet()))
                }
                NoticeWire.PAGE -> {
                    val expectedEpoch = data.readString().orEmpty(); val expectedRevision = data.readLong(); val offset = data.readInt()
                    require(offset in 0..NotificationProtocol.MAX_HISTORY)
                    val value = repository.snapshot.value
                    val changed = expectedEpoch.isNotBlank() && (expectedEpoch != value.epoch || expectedRevision != value.revision)
                    val records = mutableListOf<NoticeRecord>()
                    var characters = 512
                    if (!changed) for (record in value.records.drop(offset).take(NotificationProtocol.PAGE_SIZE)) {
                        val size = NoticeWire.gson.toJson(record).length
                        if (characters + size > NoticeWire.MAX_PAYLOAD) break
                        records.add(record); characters += size
                    }
                    NoticeWire.gson.toJson(NoticePage(value.epoch, value.revision, value.records.size, records, value.persistenceFailure, changed))
                }
                NoticeWire.COMMAND -> {
                    val raw = data.readString().orEmpty(); require(raw.length <= NoticeWire.MAX_PAYLOAD)
                    val command = NoticeWire.gson.fromJson(raw, NoticeCommand::class.java)
                    require(command != null && command.operation != null && command.requestId != null)
                    NoticeWire.gson.toJson(runBlocking { initialized.await(); repository.execute(command) })
                }
                NoticeWire.RESULT -> {
                    val requestId = data.readString().orEmpty(); require(requestId.length in 1..128)
                    NoticeWire.gson.toJson(runBlocking { initialized.await(); repository.receipt(requestId) })
                }
                NoticeWire.SUBSCRIBE -> {
                    val listener = requireNotNull(data.readStrongBinder())
                    require(callbacks.containsKey(listener) || callbacks.size < 8) { "SUBSCRIPTION_LIMIT" }
                    if (!callbacks.containsKey(listener)) {
                        val death = IBinder.DeathRecipient { remove(listener) }
                        listener.linkToDeath(death, 0)
                        callbacks[listener] = death
                    }
                    "SUBSCRIBED"
                }
                NoticeWire.UNSUBSCRIBE -> { data.readStrongBinder()?.let(::remove); "REMOVED" }
                else -> error("Unsupported command")
            }
            reply.writeNoException(); reply.writeString(answer)
            return true
        }
    }
}

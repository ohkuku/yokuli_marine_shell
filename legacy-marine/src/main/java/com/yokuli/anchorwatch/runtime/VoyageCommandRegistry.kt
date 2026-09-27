package com.yokuli.anchorwatch.runtime

import android.content.Context
import com.yokuli.runtime.contract.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 唯一持久航行账本；进程与页面离开不丢失请求，UNKNOWN 只读对账，不重新开始记录。 */
@Singleton class VoyageCommandRegistry @Inject constructor(@ApplicationContext context: Context) {
    private data class Document(val version: Int = 1, val commands: List<VoyageCommandReceipt> = emptyList())
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val disk = DurableRuntimeFile(File(context.filesDir, "marine-core"), "voyage-commands.json", Document::class.java)
    private var storageFailure: Throwable? = null
    @Volatile private var ledger = load()
    private val values = MutableStateFlow<List<VoyageCommandReceipt>>(projection(ledger))
    val commands = values.asStateFlow()
    private val executing = mutableSetOf<String>()

    private fun load(): List<VoyageCommandReceipt> = try {
        val document = disk.read { Document() }
        require(document.version == 1 && document.commands.size <= 32768) { "VOYAGE_LEDGER_INVALID" }
        require(document.commands.map { it.request.requestId }.toSet().size == document.commands.size) { "VOYAGE_LEDGER_DUPLICATE_ID" }
        document.commands.map {
            require(it.request.requestId.isNotBlank() && it.request.requestId.length <= 128) { "VOYAGE_LEDGER_INVALID_ID" }
            if (!it.terminal) it.copy(status = VoyageRequestStatus.UNKNOWN, reason = "CORE_RESTARTED_CONFIRMING") else it
        }.also { if (it != document.commands) disk.write(Document(commands = it)) }
    } catch (error: Exception) { storageFailure = error; emptyList() }

    fun requireReadable() = synchronized(lock) { storageFailure?.let { throw IllegalStateException("VOYAGE_LEDGER_UNAVAILABLE", it) } }
    /** 仅恢复入口调用；不丢失无法读取的原文件，不在执行期间重新加载。 */
    fun retryStorage() = synchronized(lock) {
        if(storageFailure==null)return@synchronized
        storageFailure=null
        val restored=load()
        requireReadable()
        ledger=restored
        values.value=projection(restored)
    }

    private fun publish(next: List<VoyageCommandReceipt>) {
        requireReadable()
        require(next.size<=32768){"COMMAND_HISTORY_FULL"}
        try { disk.write(Document(commands = next)) }
        catch (error: Exception) { storageFailure = error; throw error }
        ledger = next
        values.value = projection(next)
    }

    private fun projection(all: List<VoyageCommandReceipt>) = (all.filterNot { it.terminal } + all.filter { it.terminal }.takeLast(127)).distinctBy { it.request.requestId }

    fun register(request: VoyageRequest): Boolean = synchronized(lock) {
        requireReadable()
        require(request.requestId.isNotBlank() && request.requestId.length <= 128) { "INVALID_REQUEST_ID" }
        ledger.firstOrNull { it.request.requestId == request.requestId }?.let {
            require(it.request == request) { "REQUEST_ID_PAYLOAD_MISMATCH" }
            return@synchronized false
        }
        val pending = ledger.filterNot { it.terminal }
        val supersedingFinish = request.action == VoyageAction.FINISH && request.expectedSessionId != null &&
            pending.all { it.status == VoyageRequestStatus.UNKNOWN && it.request.action != VoyageAction.FINISH }
        val blocked = pending.isNotEmpty() && !supersedingFinish
        val result = VoyageCommandReceipt(request,
            if (blocked) VoyageRequestStatus.REJECTED else VoyageRequestStatus.QUEUED,
            if (blocked) "COMMAND_PENDING" else null)
        publish(ledger + result)
        if (!blocked) scope.launch { delay(18_000); runCatching { unknown(request.requestId, "CONFIRMATION_DELAYED") } }
        !blocked
    }
    fun isExecuting(id:String):Boolean=synchronized(lock){id in executing}
    fun get(id: String) = ledger.firstOrNull { it.request.requestId == id }
    fun associateSession(id: String, sessionId: Long) = synchronized(lock) {
        replace(id) { if (it.terminal) it else it.copy(sessionId = sessionId) }
    }
    fun begin(id: String): VoyageRequest? = synchronized(lock) {
        requireReadable()
        val receipt = get(id) ?: return@synchronized null
        if (receipt.status != VoyageRequestStatus.QUEUED || id in executing) return@synchronized null
        replace(id) { it.copy(status = VoyageRequestStatus.EXECUTING, reason = null) }
        executing.add(id)
        receipt.request
    }
    fun finish(id: String, status: VoyageRequestStatus, reason: String? = null, sessionId: Long? = null) = synchronized(lock) {
        require(status in setOf(VoyageRequestStatus.CONFIRMED, VoyageRequestStatus.REJECTED, VoyageRequestStatus.FAILED))
        val endedId = sessionId ?: get(id)?.sessionId
        val isFinish = status == VoyageRequestStatus.CONFIRMED && get(id)?.request?.action == VoyageAction.FINISH
        publish(ledger.map { earlier ->
            when {
                earlier.request.requestId == id && !earlier.terminal -> earlier.copy(status = status, reason = reason, sessionId = sessionId ?: earlier.sessionId)
                isFinish && earlier.request.requestId != id && !earlier.terminal &&
                    (earlier.sessionId == endedId || earlier.request.action == VoyageAction.START && earlier.sessionId == null) ->
                    earlier.copy(status = VoyageRequestStatus.REJECTED, reason = "SUPERSEDED_BY_FINISH")
                else -> earlier
            }
        })
        executing.retainAll(ledger.filterNot { it.terminal }.map { it.request.requestId }.toSet())
        executing.remove(id)
    }
    fun unknown(id: String, reason: String) = synchronized(lock) {
        replace(id) { if (it.terminal) it else it.copy(status = VoyageRequestStatus.UNKNOWN, reason = reason) }
    }
    fun serviceInterrupted() = synchronized(lock) {
        try { publish(ledger.map { if (!it.terminal) it.copy(status = VoyageRequestStatus.UNKNOWN, reason = "RUNTIME_INTERRUPTED") else it }) }
        finally { executing.clear() }
    }
    private fun replace(id: String, change: (VoyageCommandReceipt) -> VoyageCommandReceipt) {
        val next = ledger.map { if (it.request.requestId == id) change(it) else it }
        if (next != ledger) publish(next)
    }
    companion object {
        const val ACTION = "com.yokuli.anchorwatch.EXECUTE_VOYAGE_COMMAND"
        const val QUERY_ACTION = "com.yokuli.anchorwatch.QUERY_VOYAGE_COMMAND"
        const val EXTRA_ID = "voyageCommandId"
    }
}

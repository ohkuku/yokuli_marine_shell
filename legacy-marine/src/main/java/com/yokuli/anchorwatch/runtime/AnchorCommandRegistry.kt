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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Marine Core 持久命令账本；提交前落盘，同 ID 重试只核对实际会话，不凭超时重做下锚。 */
@Singleton
class AnchorCommandRegistry @Inject constructor(@ApplicationContext private val context: Context) : AnchorCommandMonitor {
    private data class Document(val version: Int = 1, val commands: List<AnchorCommandSnapshot> = emptyList())
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val disk = DurableRuntimeFile(File(context.filesDir, "marine-core"), "anchor-commands.json", Document::class.java)
    private var storageFailure: Throwable? = null
    @Volatile private var ledger = load()
    private val values = MutableStateFlow<List<AnchorCommandSnapshot>>(projection(ledger))
    override val commands = values.asStateFlow()
    private val executions = mutableSetOf<String>()
    private val deliveries = mutableMapOf<String, () -> Unit>()
    private var reconciler: ((String) -> Unit)? = null

    private fun load(): List<AnchorCommandSnapshot> = try {
        val document = disk.read { Document() }
        require(document.version == 1 && document.commands.size <= 32768) { "ANCHOR_LEDGER_INVALID" }
        require(document.commands.map { it.commandId }.toSet().size == document.commands.size) { "ANCHOR_LEDGER_DUPLICATE_ID" }
        document.commands.map {
            require(it.commandId.isNotBlank() && it.commandId.length <= 128) { "ANCHOR_LEDGER_INVALID_ID" }
            if (!it.terminal) it.copy(status = AnchorCommandStatus.UNKNOWN, reason = "CORE_RESTARTED_CONFIRMING") else it
        }.also { if (it != document.commands) disk.write(Document(commands = it)) }
    } catch (error: Exception) { storageFailure = error; emptyList() }

    /** 恢复屏障调用；读失败不能被当成没有历史命令。 */
    fun requireReadable() = synchronized(lock) { storageFailure?.let { throw IllegalStateException("ANCHOR_LEDGER_UNAVAILABLE", it) } }

    /** 仅恢复入口调用；不丢失无法读取的原文件，不在执行期间重新加载。 */
    fun retryStorage() = synchronized(lock) {
        if(storageFailure==null)return@synchronized
        storageFailure=null
        val restored=load()
        requireReadable()
        ledger=restored
        values.value=projection(restored)
    }

    private fun publish(next: List<AnchorCommandSnapshot>) {
        requireReadable()
        require(next.size<=32768){"COMMAND_HISTORY_FULL"}
        try { disk.write(Document(commands = next)) }
        catch (error: Exception) { storageFailure = error; throw error }
        ledger = next
        values.value = projection(next)
    }

    private fun projection(all: List<AnchorCommandSnapshot>) = (all.filterNot { it.terminal } + all.filter { it.terminal }.takeLast(127)).distinctBy { it.commandId }

    fun create(type: AnchorCommandType, expectedSessionId: Long?, requestId: String = UUID.randomUUID().toString(), payloadFingerprint: String? = null): AnchorCommandSnapshot = synchronized(lock) {
        requireReadable()
        require(requestId.isNotBlank() && requestId.length <= 128) { "INVALID_REQUEST_ID" }
        get(requestId)?.let { existing ->
            require(existing.type == type && existing.expectedSessionId == expectedSessionId && existing.payloadFingerprint == payloadFingerprint) { "REQUEST_ID_PAYLOAD_MISMATCH" }
            return@synchronized existing
        }
        val pending = ledger.filter { !it.terminal }
        check(pending.isEmpty() || type == AnchorCommandType.LIFT && expectedSessionId != null && pending.none { it.type == AnchorCommandType.LIFT }) { "ANCHOR_COMMAND_PENDING" }
        val request = AnchorCommandSnapshot(requestId, type, expectedSessionId, payloadFingerprint = payloadFingerprint)
        val retained = ledger
        publish(retained + request)
        deliveries.keys.retainAll(ledger.map { it.commandId }.toSet())
        scope.launch { delay(45_000); runCatching { unknown(request.commandId, "CONFIRMATION_DELAYED") } }
        request
    }

    fun retainDelivery(id: String, send: () -> Unit) = synchronized(lock) { deliveries[id] = send }
    /** 进程重启后原始闭包不存在，重新确认仍能在唯一 actor 中只读核对落盘事实。 */
    fun setReconciler(value: ((String) -> Unit)?) = synchronized(lock) { reconciler = value }
    override fun recheck(commandId: String) {
        val send = synchronized(lock) {
            if (get(commandId)?.terminal != false) null
            else reconciler?.let { action -> { action(commandId) } } ?: {
                androidx.core.content.ContextCompat.startForegroundService(context,
                    android.content.Intent(context, com.yokuli.anchorwatch.service.AnchorForegroundService::class.java)
                        .setAction(QUERY_ACTION).putExtra(COMMAND_ID_EXTRA, commandId))
                Unit
            }
        } ?: return
        runCatching(send).onFailure { runCatching { unknown(commandId, "CONFIRMATION_DELIVERY_FAILED") } }
    }

    fun serviceInterrupted() = synchronized(lock) {
        try { publish(ledger.map { if (!it.terminal) it.copy(status = AnchorCommandStatus.UNKNOWN, reason = "RUNTIME_INTERRUPTED") else it }) }
        finally { executions.clear(); reconciler = null }
    }

    fun isExecuting(id:String):Boolean=synchronized(lock){id in executions}
    fun get(id: String) = ledger.firstOrNull { it.commandId == id }

    /** UNKNOWN 是已受理但结果不明，绝不重复调用动作；恢复对账负责结束这类请求。 */
    fun begin(id: String, type: AnchorCommandType): Boolean = synchronized(lock) {
        requireReadable()
        val request = get(id) ?: return@synchronized false
        if (request.type != type || request.status != AnchorCommandStatus.QUEUED || id in executions) return@synchronized false
        update(id) { it.copy(status = AnchorCommandStatus.EXECUTING, reason = null) }
        executions.add(id)
        true
    }

    fun finish(id: String, status: AnchorCommandStatus, sessionId: Long?, reason: String? = null) = synchronized(lock) {
        require(status in setOf(AnchorCommandStatus.CONFIRMED, AnchorCommandStatus.REJECTED, AnchorCommandStatus.FAILED))
        val endedIndex = ledger.indexOfFirst { it.commandId == id }
        val isLift = status == AnchorCommandStatus.CONFIRMED && get(id)?.type == AnchorCommandType.LIFT
        publish(ledger.mapIndexed { index, prior ->
            when {
                prior.commandId == id && !prior.terminal -> prior.copy(status = status, sessionId = sessionId, reason = reason)
                isLift && index < endedIndex && !prior.terminal && (prior.expectedSessionId == sessionId || prior.expectedSessionId == null) ->
                    prior.copy(status = AnchorCommandStatus.REJECTED, reason = "SUPERSEDED_BY_LIFT")
                else -> prior
            }
        })
        executions.retainAll(ledger.filterNot { it.terminal }.map { it.commandId }.toSet())
        executions.remove(id)
    }

    fun unknown(id: String, reason: String) = synchronized(lock) {
        update(id) { if (it.terminal) it else it.copy(status = AnchorCommandStatus.UNKNOWN, reason = reason) }
    }
    override fun acknowledgeResult(commandId: String) = synchronized(lock) {
        update(commandId) { if (it.terminal) it.copy(resultPresented = true) else it }
    }
    private fun update(id: String, change: (AnchorCommandSnapshot) -> AnchorCommandSnapshot) {
        val next = ledger.map { if (it.commandId == id) change(it) else it }
        if (next != ledger) publish(next)
    }
    companion object { const val COMMAND_ID_EXTRA = "anchorCommandId"; const val QUERY_ACTION = "com.yokuli.anchorwatch.QUERY_ANCHOR_COMMAND" }
}

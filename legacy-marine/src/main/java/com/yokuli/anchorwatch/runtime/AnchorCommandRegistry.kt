package com.yokuli.anchorwatch.runtime

import com.yokuli.runtime.contract.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Intent 与运行时共享的进程级请求账本；不依赖 Compose、通知消息或页面是否还打开。 */
@Singleton
class AnchorCommandRegistry @Inject constructor() : AnchorCommandMonitor {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val values = MutableStateFlow<List<AnchorCommandSnapshot>>(emptyList())
    override val commands = values.asStateFlow()
    private val executions = mutableSetOf<String>()
    private val deliveries = mutableMapOf<String, () -> Unit>()

    fun create(type: AnchorCommandType, expectedSessionId: Long?): AnchorCommandSnapshot = synchronized(lock) {
        val pending = values.value.filter { !it.terminal }
        // 显式起锚可以排在正在恢复/尚未确认的操作之后；普通请求不能靠重复点击绕过锁。
        check(pending.isEmpty() || type == AnchorCommandType.LIFT && expectedSessionId != null && pending.none { it.type == AnchorCommandType.LIFT }) { "ANCHOR_COMMAND_PENDING" }
        val request = AnchorCommandSnapshot(UUID.randomUUID().toString(), type, expectedSessionId)
        val retained = (values.value.filter { !it.terminal } + values.value.filter { it.terminal }.takeLast(63)).distinctBy { it.commandId }
        values.value = retained + request
        deliveries.keys.retainAll(values.value.map { it.commandId }.toSet())
        // 恢复 NMEA 定位本身可等待 30 秒。即使超时，也继续接收该 ID 的真实执行结果。
        scope.launch { delay(45_000); unknown(request.commandId, "CONFIRMATION_DELAYED") }
        request
    }

    /** 保存原始参数，重新确认仍投递同一 ID，由运行时去重并核对会话。 */
    fun retainDelivery(id: String, send: () -> Unit) = synchronized(lock) { deliveries[id] = send }

    override fun recheck(commandId: String) {
        val send = synchronized(lock) { if(get(commandId)?.terminal != false) null else deliveries[commandId] } ?: return
        runCatching(send).onFailure { unknown(commandId, "CONFIRMATION_DELIVERY_FAILED") }
    }

    /** 服务被中断时请求仍保留，旧 actor 的执行占位不能阻止同 ID 在新服务中核对。 */
    fun serviceInterrupted() = synchronized(lock) {
        executions.forEach { id -> update(id) { if(it.terminal) it else it.copy(status = AnchorCommandStatus.UNKNOWN, reason = "RUNTIME_INTERRUPTED") } }
        executions.clear()
    }

    fun get(id: String) = values.value.firstOrNull { it.commandId == id }

    /** 重复 Intent 只复用回执，不执行第二遍。 */
    fun begin(id: String, type: AnchorCommandType): Boolean = synchronized(lock) {
        val request = get(id) ?: return@synchronized false
        if (request.type != type || request.terminal || !executions.add(id)) return@synchronized false
        update(id) { it.copy(status = AnchorCommandStatus.EXECUTING, reason = null) }
        true
    }

    fun finish(id: String, status: AnchorCommandStatus, sessionId: Long?, reason: String? = null) = synchronized(lock) {
        require(status in setOf(AnchorCommandStatus.CONFIRMED, AnchorCommandStatus.REJECTED, AnchorCommandStatus.FAILED))
        update(id) { if(it.terminal) it else it.copy(status = status, sessionId = sessionId, reason = reason) }
        executions.remove(id)
        if(status == AnchorCommandStatus.CONFIRMED && get(id)?.type == AnchorCommandType.LIFT) {
            // 起锚已按数据库确认后，迟到的旧开始/恢复命令不能再次建立或恢复值守。
            val endedIndex = values.value.indexOfFirst { it.commandId == id }
            values.value = values.value.mapIndexed { index, prior ->
                if(index < endedIndex && !prior.terminal && (prior.expectedSessionId == sessionId || prior.expectedSessionId == null)) {
                    executions.remove(prior.commandId)
                    prior.copy(status = AnchorCommandStatus.REJECTED, reason = "SUPERSEDED_BY_LIFT")
                } else prior
            }
        }
    }

    fun unknown(id: String, reason: String) = synchronized(lock) {
        update(id) { if(it.terminal) it else it.copy(status = AnchorCommandStatus.UNKNOWN, reason = reason) }
    }

    override fun acknowledgeResult(commandId: String) = synchronized(lock) {
        update(commandId) { if(it.terminal) it.copy(resultPresented = true) else it }
    }

    private fun update(id: String, change: (AnchorCommandSnapshot) -> AnchorCommandSnapshot) {
        values.value = values.value.map { if(it.commandId == id) change(it) else it }
    }

    companion object { const val COMMAND_ID_EXTRA = "anchorCommandId" }
}

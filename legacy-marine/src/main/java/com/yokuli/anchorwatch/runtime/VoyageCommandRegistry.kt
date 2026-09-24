package com.yokuli.anchorwatch.runtime

import com.yokuli.runtime.contract.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 唯一航行请求账本。由实际 trip actor 写终态，UI 和协调器均只读；当前代次仅在进程内。 */
@Singleton class VoyageCommandRegistry @Inject constructor() {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val values = MutableStateFlow<List<VoyageCommandReceipt>>(emptyList())
    val commands = values.asStateFlow()
    private val executing = mutableSetOf<String>()

    /** 返回 true 仅表示首次受理并需要投递；同 ID 同参数不重复发送。 */
    fun register(request: VoyageRequest): Boolean = synchronized(lock) {
        values.value.firstOrNull { it.request.requestId == request.requestId }?.let {
            require(it.request == request) { "REQUEST_ID_PAYLOAD_MISMATCH" }
            return@synchronized false
        }
        val pending = values.value.filterNot { it.terminal }
        // 显式结束当前会话可排在 UNKNOWN 后面；查询和关闭通知从不会触发此结束操作。
        val supersedingFinish = request.action == VoyageAction.FINISH && request.expectedSessionId != null &&
            pending.all { it.status == VoyageRequestStatus.UNKNOWN && it.request.action != VoyageAction.FINISH }
        val blocked = pending.isNotEmpty() && !supersedingFinish
        val result = VoyageCommandReceipt(request,
            if(blocked) VoyageRequestStatus.REJECTED else VoyageRequestStatus.QUEUED,
            if(blocked) "COMMAND_PENDING" else null)
        values.value = values.value.filterNot { it.terminal } + values.value.filter { it.terminal }.takeLast(30) + result
        if(!blocked) scope.launch { delay(18_000); unknown(request.requestId, "CONFIRMATION_DELAYED") }
        !blocked
    }
    fun get(id: String) = values.value.firstOrNull { it.request.requestId == id }
    fun associateSession(id: String, sessionId: Long) = synchronized(lock) {
        replace(id) { if(it.terminal) it else it.copy(sessionId = sessionId) }
    }
    fun begin(id: String): VoyageRequest? = synchronized(lock) {
        val receipt = get(id) ?: return@synchronized null
        if(receipt.terminal || !executing.add(id)) return@synchronized null
        replace(id) { it.copy(status = VoyageRequestStatus.EXECUTING, reason = null) }
        receipt.request
    }
    fun finish(id: String, status: VoyageRequestStatus, reason: String? = null, sessionId: Long? = null) = synchronized(lock) {
        require(status in setOf(VoyageRequestStatus.CONFIRMED, VoyageRequestStatus.REJECTED, VoyageRequestStatus.FAILED))
        replace(id) { if(it.terminal) it else it.copy(status = status, reason = reason, sessionId = sessionId ?: it.sessionId) }
        executing.remove(id)
        if(status == VoyageRequestStatus.CONFIRMED && get(id)?.request?.action == VoyageAction.FINISH) {
            val endedId = sessionId ?: get(id)?.sessionId
            values.value = values.value.map { earlier ->
                if(earlier.request.requestId != id && !earlier.terminal &&
                    (earlier.sessionId == endedId || earlier.request.action == VoyageAction.START && earlier.sessionId == null)) {
                    executing.remove(earlier.request.requestId)
                    earlier.copy(status = VoyageRequestStatus.REJECTED, reason = "SUPERSEDED_BY_FINISH")
                } else earlier
            }
        }
    }
    fun unknown(id: String, reason: String) = synchronized(lock) {
        replace(id) { if(it.terminal) it else it.copy(status = VoyageRequestStatus.UNKNOWN, reason = reason) }
    }
    /** 服务代次退出不猜测执行是否已落盘，保留 UNKNOWN；新服务不会自动重放这些命令。 */
    fun serviceInterrupted() = synchronized(lock) {
        executing.toList().forEach { unknown(it, "RUNTIME_INTERRUPTED") }
        values.value = values.value.map { if(it.status == VoyageRequestStatus.QUEUED)
            it.copy(status = VoyageRequestStatus.FAILED, reason = "QUEUE_INTERRUPTED_BEFORE_EXECUTION") else it }
        executing.clear()
    }
    private fun replace(id: String, change: (VoyageCommandReceipt) -> VoyageCommandReceipt) {
        values.value = values.value.map { if(it.request.requestId == id) change(it) else it }
    }
    companion object {
        const val ACTION = "com.yokuli.anchorwatch.EXECUTE_VOYAGE_COMMAND"
        const val QUERY_ACTION = "com.yokuli.anchorwatch.QUERY_VOYAGE_COMMAND"
        const val EXTRA_ID = "voyageCommandId"
    }
}

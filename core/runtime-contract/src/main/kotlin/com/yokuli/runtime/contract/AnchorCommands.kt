package com.yokuli.runtime.contract

import kotlinx.coroutines.flow.StateFlow

/** 下锚、暂停、恢复、起锚是四种独立操作；关闭页面不属于任何一种操作。 */
enum class AnchorCommandType { START, PAUSE, RESUME, LIFT }
enum class AnchorCommandStatus { QUEUED, EXECUTING, UNKNOWN, CONFIRMED, REJECTED, FAILED }

/**
 * 进程级命令回执。commandId 关联一次请求；expectedSessionId 防止旧页面操作另一场值守。
 * UNKNOWN 只表示暂时无法确认，既不是失败，也不授权客户端另建请求；重新确认须复用原 ID。
 * sessionId 是执行后实际关联的会话；终态只由执行命令的运行时写入。
 */
data class AnchorCommandSnapshot(
    val commandId: String,
    val type: AnchorCommandType,
    val expectedSessionId: Long?,
    val sessionId: Long? = expectedSessionId,
    val status: AnchorCommandStatus = AnchorCommandStatus.QUEUED,
    val reason: String? = null,
    /** 仅标记结果已经展示，不能消音、暂停、结束值守或清除执行状态。 */
    val resultPresented: Boolean = false,
) {
    val terminal: Boolean get() = status in setOf(AnchorCommandStatus.CONFIRMED, AnchorCommandStatus.REJECTED, AnchorCommandStatus.FAILED)
}

/** 多页面订阅同一份记录；订阅生命周期不控制命令生命周期。 */
interface AnchorCommandMonitor {
    val commands: StateFlow<List<AnchorCommandSnapshot>>
    /** 使用原请求 ID 重新确认；不会创建另一次操作，也不会以超时当失败。 */
    fun recheck(commandId: String)
    fun acknowledgeResult(commandId: String)
}

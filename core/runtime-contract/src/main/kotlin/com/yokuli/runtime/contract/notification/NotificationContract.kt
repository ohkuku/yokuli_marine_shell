package com.yokuli.runtime.contract.notification

import kotlinx.coroutines.flow.StateFlow

/** 普通版本化 Binder 协议；不是 Stable AIDL，不授权第三方 UID。 */
object NotificationProtocol { const val MAJOR = 1; const val MINOR = 0; const val MAX_HISTORY = 200; const val PAGE_SIZE = 20 }
enum class NoticeCapability { READ_HISTORY, UPDATE_HISTORY, PUBLISH_EVENTS, READ_RECEIPTS }
data class NoticeServiceInfo(val protocolMajor: Int, val protocolMinor: Int, val epoch: String, val capabilities: Set<NoticeCapability>)
enum class NoticeLevel { INFO, WARNING, ALARM }
/** 目的地是领域/对象描述，传输层不携带闭包或 Compose 页面实例。 */
enum class NoticeActionKind { OPEN_TARGET }
data class NoticeAction(val kind: NoticeActionKind, val target: NoticeTarget)
data class NoticeTarget(val domain: String, val objectType: String? = null, val objectId: String? = null, val section: String? = null)
data class NoticeText(val titleZh: String = "", val titleEn: String = "", val bodyZh: String = "", val bodyEn: String = "", val code: String? = null, val arguments: Map<String, String> = emptyMap())
/** UTC 时间可跨重启保存；revision 在文件内递增，epoch 标识本次服务进程。 */
data class NoticeRecord(
    val id: String, val publisher: String?, val text: NoticeText, val occurredAtUtcMillis: Long,
    val updatedAtUtcMillis: Long = occurredAtUtcMillis, val level: NoticeLevel = NoticeLevel.INFO,
    val target: NoticeTarget? = null, val domainEventId: String? = null, val aggregationKey: String? = null,
    val category: String = "message", val dismissible: Boolean = true, val read: Boolean = false, val occurrences: Int = 1,
) {
    /** 当前只支持明确的对象查看动作；不把确认警报或停止任务偷藏在通知点击里。 */
    val primaryAction: NoticeAction? get() = target?.let { NoticeAction(NoticeActionKind.OPEN_TARGET, it) }
}
enum class NoticeConnection { CONNECTING, READY, DISCONNECTED, UNSUPPORTED, CLOSED }
data class NotificationSnapshot(val epoch: String = "", val revision: Long = 0, val records: List<NoticeRecord> = emptyList(), val persistenceFailure: String? = null)
/** COMPLETED 表示已持久化；UNKNOWN 表示通信中断且服务可能已经完成，禁止自动改 requestId 重发。 */
enum class NoticeCommandStatus { COMPLETED, REJECTED, PERSISTENCE_FAILED, UNKNOWN, NOT_SENT }
data class NoticeCommandResult(val requestId: String, val status: NoticeCommandStatus, val revision: Long = 0, val reason: String? = null)
enum class NoticePublishMode { OCCURRENCE, STATE_UPDATE }
enum class NoticeOperation { PUBLISH, MARK_READ, REMOVE, CLEAR_ALL, CLEAR_READ, RESTORE, RETRY_STORAGE }
data class NoticeCommand(
    val requestId: String, val operation: NoticeOperation, val record: NoticeRecord? = null, val noticeId: String? = null,
    val expectedRevision: Long? = null, val expectedEpoch: String? = null,
    val eventStream: String? = null, val eventSequence: Long? = null, val publishMode: NoticePublishMode = NoticePublishMode.OCCURRENCE,
)
interface NotificationClient {
    val snapshot: StateFlow<NotificationSnapshot>
    val connection: StateFlow<NoticeConnection>
    /** 有界完成回执；等待超时的调用也能收到迟到的真实结果，不依赖面板存活。 */
    val results: StateFlow<Map<String, NoticeCommandResult>>
    suspend fun execute(command: NoticeCommand): NoticeCommandResult
    suspend fun result(requestId: String): NoticeCommandResult
    fun close()
}

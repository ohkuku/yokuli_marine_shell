package com.yokuli.marine.shell.rebuild

import android.content.Context
import androidx.compose.runtime.*
import com.yokuli.runtime.contract.notification.*
import com.yokuli.runtime.marine.notification.BinderNotificationClient
import com.yokuli.runtime.marine.notification.legacyNoticeTarget
import com.yokuli.runtime.marine.notification.legacyRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 历史警报等级只是消息样式；阅读和清除不操作原领域警报。 */
enum class NoticeSeverity { INFO, WARNING, ALARM }
/** Shell 兼容呈现投影。旧双语文本完整保留为正文，不按标点猜标题。 */
data class SystemNotice(
    val id: String = uid(), val app: AppId?, val chinese: String, val english: String,
    val createdAt: Long = System.currentTimeMillis(), val severity: NoticeSeverity = NoticeSeverity.INFO,
    val destination: String? = null, val read: Boolean = false, val key: String? = null, val occurrences: Int = 1,
    val titleChinese: String = "", val titleEnglish: String = "", val bodyChinese: String = chinese, val bodyEnglish: String = english,
    val dismissible: Boolean = true, val updatedAt: Long = createdAt, val domainEventId: String? = null,
) {
    fun text(os: OsStore) = if (titleChinese.isBlank() && titleEnglish.isBlank()) os.t(chinese, english) else listOf(title(os), body(os)).filter { it.isNotBlank() }.joinToString(" · ")
    fun title(os: OsStore) = os.t(titleChinese, titleEnglish).ifBlank { app?.let(os::title) ?: os.t("消息", "notification") }
    fun body(os: OsStore) = os.t(bodyChinese, bodyEnglish)
    internal fun record() = NoticeRecord(id, app?.name, NoticeText(titleChinese, titleEnglish, bodyChinese, bodyEnglish),
        createdAt, updatedAt, NoticeLevel.valueOf(severity.name), destination?.let(::legacyNoticeTarget), domainEventId, key,
        dismissible = dismissible, read = read, occurrences = occurrences)
}
private fun NoticeRecord.present() = SystemNotice(id, AppId.entries.firstOrNull { it.name == publisher }, text.bodyZh, text.bodyEn,
    occurredAtUtcMillis, NoticeSeverity.valueOf(level.name), primaryAction?.target?.legacyRoute(), read, aggregationKey, occurrences,
    text.titleZh, text.titleEn, text.bodyZh, text.bodyEn, dismissible, updatedAtUtcMillis, domainEventId)

/** 只拥有 Shell 消息投影与 toast 排队；唯一持久化写者在 :notifications 服务进程。 */
class SystemNotificationStore(context: Context, private val scope: CoroutineScope) {
    private val client = BinderNotificationClient.shared(context)
    private val userCommands = Mutex()
    private val pendingRequests = linkedMapOf<String, NoticeCommand>()
    private val unknownRequests = linkedSetOf<String>()
    var checkingPending by mutableStateOf(false); private set
    var hasUnknownCommands by mutableStateOf(false); private set
    private val recoverablePosts = linkedMapOf<String, Pair<NoticeCommand, NoticeCommandStatus>>()
    private val banners = Channel<SystemNotice>(32, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private val bannerCandidates = linkedMapOf<String, SystemNotice>()
    private val acknowledgedBanners = linkedSetOf<String>()
    private var initialized = false
    private var projectedRevision = -1L
    private var presentationVisible = false
    var items by mutableStateOf<List<SystemNotice>>(emptyList()); private set
    var banner by mutableStateOf<SystemNotice?>(null); private set
    var connection by mutableStateOf(NoticeConnection.CONNECTING); private set
    var persistenceFailure by mutableStateOf<String?>(null); private set
    var pendingCommand by mutableStateOf<NoticeCommand?>(null); private set
    var pendingIds by mutableStateOf<Set<String>>(emptySet()); private set
    var lastResult by mutableStateOf<NoticeCommandResult?>(null); private set
    val unreadCount get() = items.count { !it.read }
    init {
        scope.launch { client.connection.collect { value ->
            connection = value
            if (value == NoticeConnection.READY) {
                recheckPending()
                lastResult?.takeIf { it.status == NoticeCommandStatus.UNKNOWN && it.requestId !in unknownRequests }?.let { lastResult = client.result(it.requestId) }
            }
        } }
        scope.launch { client.results.collect { receipts ->
            unknownRequests.toList().forEach { id -> receipts[id]?.takeUnless { it.status == NoticeCommandStatus.UNKNOWN }?.let(::resolvePending) }
            lastResult?.takeIf { it.status == NoticeCommandStatus.UNKNOWN }?.let { result ->
                receipts[result.requestId]?.let { lastResult = it }
            }
        } }
        scope.launch { client.snapshot.collect { snapshot ->
            val previous = items.associateBy { it.id }
            items = snapshot.records.map { it.present() }
            persistenceFailure = snapshot.persistenceFailure
            if (snapshot.revision != projectedRevision) {
                projectedRevision = snapshot.revision
                lastResult?.takeIf { it.status == NoticeCommandStatus.UNKNOWN || it.status == NoticeCommandStatus.PERSISTENCE_FAILED }?.let { previousResult ->
                    scope.launch {
                        val recovered = client.result(previousResult.requestId)
                        if (lastResult?.requestId == previousResult.requestId) lastResult = recovered
                    }
                }
            }
            if (snapshot.epoch.isNotBlank()) {
                items.asReversed().forEach { notice ->
                    val eventFresh = notice.domainEventId != null && System.currentTimeMillis() - notice.updatedAt in 0..10_000
                    val candidate = bannerCandidates.values.firstOrNull { it.id == notice.id ||
                        it.key != null && it.key == notice.key && it.app == notice.app && it.updatedAt == notice.updatedAt }
                    val requested = candidate != null
                    candidate?.let { bannerCandidates.remove(it.id) }
                    val identity = "${notice.id}:${notice.updatedAt}"
                    if (!notice.read && (requested || initialized && eventFresh && previous[notice.id]?.updatedAt != notice.updatedAt) && acknowledgedBanners.add(identity)) {
                        if (!presentationVisible) banners.trySend(notice)
                    }
                }
                while (acknowledgedBanners.size > 256) acknowledgedBanners.remove(acknowledgedBanners.first())
                initialized = true
            }
            banner?.let { current -> if (items.none { it.id == current.id && !it.read }) banner = null }
        } }
        scope.launch { for (notice in banners) {
            if (presentationVisible || items.none { it.id == notice.id && it.updatedAt == notice.updatedAt && !it.read }) continue
            banner = notice
            delay(if (notice.severity == NoticeSeverity.INFO) 3500 else 6000)
            if (banner?.id == notice.id) banner = null
        } }
    }
    fun setPresentationVisible(visible: Boolean) { presentationVisible = visible; if (visible) banner = null }
    suspend fun awaitLoaded() { client.snapshot.first { it.epoch.isNotBlank() } }
    fun dismissBanner() { banner = null }
    fun post(notice: SystemNotice, showBanner: Boolean = true) {
        if (showBanner) {
            bannerCandidates[notice.id] = notice
            while (bannerCandidates.size > 64) bannerCandidates.remove(bannerCandidates.keys.first())
        }
        scope.launch {
            val command = NoticeCommand(uid(), NoticeOperation.PUBLISH, record = notice.record())
            var result = client.execute(command)
            repeat(2) {
                if (result.status == NoticeCommandStatus.NOT_SENT) { delay(2000); result = client.execute(command) }
            }
            if (result.status != NoticeCommandStatus.COMPLETED) {
                bannerCandidates.remove(notice.id)
                lastResult = result
                recoverablePosts[command.requestId] = command to result.status
                hasUnknownCommands = unknownRequests.isNotEmpty() || recoverablePosts.values.any { it.second == NoticeCommandStatus.UNKNOWN }
                while (recoverablePosts.size > 32) recoverablePosts.remove(recoverablePosts.keys.first())
            }
        }
    }
    fun markRead(id: String) {
        if (items.none { it.id == id && !it.read } || id in pendingIds) return
        submit(NoticeOperation.MARK_READ, id)
    }
    fun remove(id: String, onResult: (NoticeCommandResult) -> Unit = {}) {
        submit(NoticeOperation.REMOVE, id, onResult = onResult)
    }
    fun clearAll(onResult: (NoticeCommandResult) -> Unit = {}) = submit(NoticeOperation.CLEAR_ALL, onResult = onResult)
    fun clearRead(onResult: (NoticeCommandResult) -> Unit = {}) = submit(NoticeOperation.CLEAR_READ, onResult = onResult)
    fun restore(notice: SystemNotice, onResult: (NoticeCommandResult) -> Unit = {}) = submit(NoticeOperation.RESTORE, notice.id, notice.record(), onResult)
    fun retryPersistence() = submit(NoticeOperation.RETRY_STORAGE) { result ->
        if (result.status == NoticeCommandStatus.COMPLETED) scope.launch {
            recoverablePosts.values.toList().forEach { (command, status) ->
                val recovered = if (status == NoticeCommandStatus.UNKNOWN) client.result(command.requestId)
                    else client.execute(command)
                if (recovered.status == NoticeCommandStatus.COMPLETED || recovered.status == NoticeCommandStatus.REJECTED) recoverablePosts.remove(command.requestId)
                lastResult = recovered
            }
        }
    }
    /** 只查询原 requestId 的回执；不会因打开面板或重连生成一个新命令。 */
    fun recheckPending() {
        val ids = (unknownRequests + recoverablePosts.filterValues { it.second == NoticeCommandStatus.UNKNOWN }.keys).toList()
        if (checkingPending || ids.isEmpty()) return
        checkingPending = true
        scope.launch {
            try { ids.forEach { id ->
                val result = client.result(id)
                resolvePending(result)
                if (result.status != NoticeCommandStatus.UNKNOWN) recoverablePosts.remove(id)
                if (lastResult?.requestId == id) lastResult = result
            } }
            finally {
                checkingPending = false
                hasUnknownCommands = unknownRequests.isNotEmpty() || recoverablePosts.values.any { it.second == NoticeCommandStatus.UNKNOWN }
            }
        }
    }
    private fun resolvePending(result: NoticeCommandResult) {
        if (result.requestId !in unknownRequests || result.status == NoticeCommandStatus.UNKNOWN) return
        unknownRequests.remove(result.requestId)
        hasUnknownCommands = unknownRequests.isNotEmpty() || recoverablePosts.values.any { it.second == NoticeCommandStatus.UNKNOWN }
        val entry = pendingRequests.entries.firstOrNull { it.value.requestId == result.requestId }
        entry?.let { pendingRequests.remove(it.key); it.value.noticeId?.let { id -> pendingIds = pendingIds - id } }
        pendingCommand = pendingRequests.values.firstOrNull()
        lastResult = result
    }
    private fun submit(operation: NoticeOperation, id: String? = null, record: NoticeRecord? = null, onResult: (NoticeCommandResult) -> Unit = {}) {
        val operationKey = "$operation:${id.orEmpty()}"
        if (operationKey in pendingRequests || id != null && id in pendingIds) {
            val existing = pendingRequests[operationKey] ?: pendingRequests.values.firstOrNull { it.noticeId == id }
            onResult(NoticeCommandResult(existing?.requestId ?: uid(), if (existing?.requestId in unknownRequests) NoticeCommandStatus.UNKNOWN else NoticeCommandStatus.NOT_SENT, reason = "CHANGE_ALREADY_PENDING"))
            return
        }
        if (pendingRequests.size >= 32) {
            onResult(NoticeCommandResult(uid(), NoticeCommandStatus.NOT_SENT, reason = "TOO_MANY_PENDING_CHANGES"))
            return
        }
        val current = client.snapshot.value
        val protectsWholeSnapshot = operation == NoticeOperation.CLEAR_ALL || operation == NoticeOperation.CLEAR_READ
        val command = NoticeCommand(uid(), operation, record, id,
            expectedRevision = current.revision.takeIf { protectsWholeSnapshot }, expectedEpoch = current.epoch.takeIf { protectsWholeSnapshot })
        pendingRequests[operationKey] = command
        if (pendingCommand == null) pendingCommand = command
        id?.let { pendingIds = pendingIds + it }
        scope.launch { userCommands.withLock {
            pendingCommand = command
            try {
                var result = client.execute(command)
                if (result.status == NoticeCommandStatus.UNKNOWN) {
                    client.results.value[command.requestId]?.takeUnless { it.status == NoticeCommandStatus.UNKNOWN }?.let { result = it }
                    if (result.status == NoticeCommandStatus.UNKNOWN) { unknownRequests.add(command.requestId); hasUnknownCommands = true }
                }
                lastResult = result
                // 成功对应已提交 revision。先等投影追上，再放开行手势，避免旧快照重新出现。
                if (result.status == NoticeCommandStatus.COMPLETED) withTimeoutOrNull(3000) {
                    client.snapshot.first { it.revision >= result.revision || client.connection.value != NoticeConnection.READY }
                }
                onResult(result)
            } finally {
                if (command.requestId !in unknownRequests) {
                    pendingRequests.remove(operationKey)
                    id?.let { pendingIds = pendingIds - it }
                }
                pendingCommand = pendingRequests.values.firstOrNull()
            }
        } }
    }
}

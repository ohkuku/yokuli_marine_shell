package com.yokuli.runtime.marine.notification

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.runtime.contract.notification.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 唯一写者，服务进程独占此实例。先完成 AtomicFile 提交，再公布 revision 与成功结果。 */
internal class NotificationRepository(context: Context) {
    private data class Disk(val schema: Int = 1, val revision: Long = 0, val records: List<NoticeRecord> = emptyList(),
        val acceptedEvents: List<String> = emptyList(), val anchorCursor: Long = 0, val receipts: List<NoticeCommandResult> = emptyList(),
        val commandFingerprints: Map<String, String> = emptyMap())
    private val file = AtomicFile(File(context.filesDir, "notifications/history-v1.json"))
    private val legacy = AtomicFile(File(context.filesDir, "system-notifications.json"))
    private val mutex = Mutex()
    private val gson = Gson()
    private val epoch = UUID.randomUUID().toString()
    private var disk = Disk()
    private var loaded = false
    private var pending: Pair<NoticeCommand, Disk>? = null
    private val state = MutableStateFlow(NotificationSnapshot(epoch = epoch))
    val snapshot = state.asStateFlow()

    suspend fun initialize() = mutex.withLock { withContext(Dispatchers.IO) { load() } }
    private fun load() {
        try {
            val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
            if (exists) {
                val text = file.openRead().use { input -> input.readBytesLimited() }
                val saved = gson.fromJson(text, Disk::class.java) ?: error("EMPTY_HISTORY")
                require(saved.schema == 1 && saved.revision >= 0 && saved.records.size <= NotificationProtocol.MAX_HISTORY)
                require(saved.records.all(::validRecord) && saved.acceptedEvents.size <= 512 && saved.receipts.size <= 128)
                disk = saved.copy(commandFingerprints = saved.commandFingerprints.orEmpty())
            } else {
                disk = migrateLegacy()
                persist(disk)
            }
            loaded = true
            publish()
        } catch (_: Exception) {
            loaded = false
            publish("HISTORY_READ_FAILED")
        }
    }
    private fun migrateLegacy(): Disk {
        if (!legacy.baseFile.exists() && !File(legacy.baseFile.path + ".bak").exists()) return Disk()
        val raw = legacy.openRead().use { it.readBytesLimited() }
        val root = if (raw.trimStart().startsWith("{")) JSONObject(raw) else null
        val items = root?.getJSONArray("items") ?: JSONArray(raw)
        val records = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            NoticeRecord(id = item.getString("id"), publisher = item.nullableString("app"),
                text = NoticeText(bodyZh = item.getString("zh"), bodyEn = item.getString("en")),
                occurredAtUtcMillis = item.getLong("time"), level = NoticeLevel.valueOf(item.optString("severity", "INFO")),
                target = item.nullableString("destination")?.let(::legacyNoticeTarget), read = item.optBoolean("read"),
                aggregationKey = item.nullableString("key"), occurrences = item.optInt("occurrences", 1).coerceAtLeast(1))
        }.distinctBy { it.id }.sortedByDescending { it.updatedAtUtcMillis }.take(NotificationProtocol.MAX_HISTORY)
        require(records.all(::validRecord))
        val accepted = root?.optJSONArray("aisNoticeIds")?.let { array -> (0 until array.length()).map { "AIS:" + array.getString(it) }.takeLast(512) }.orEmpty()
        return Disk(records = records, acceptedEvents = accepted, anchorCursor = root?.optLong("anchorEventId") ?: 0)
    }
    private fun publish(failure: String? = null) {
        state.value = NotificationSnapshot(epoch, disk.revision, disk.records, failure)
    }
    suspend fun receipt(id: String): NoticeCommandResult = mutex.withLock {
        disk.receipts.firstOrNull { it.requestId == id }
            ?: pending?.takeIf { it.first.requestId == id }?.let { NoticeCommandResult(id, NoticeCommandStatus.PERSISTENCE_FAILED, disk.revision, "RETRY_STORAGE") }
            ?: NoticeCommandResult(id, NoticeCommandStatus.UNKNOWN, disk.revision, "NO_DURABLE_RECEIPT")
    }
    suspend fun execute(command: NoticeCommand): NoticeCommandResult = mutex.withLock { withContext(Dispatchers.IO) {
        if (command.requestId.isBlank() || command.requestId.length > 128) return@withContext rejected(command, "INVALID_REQUEST_ID")
        val requestFingerprint = fingerprint(command)
        disk.receipts.firstOrNull { it.requestId == command.requestId }?.let {
            if (disk.commandFingerprints[command.requestId] != requestFingerprint) return@withContext rejected(command, "REQUEST_ID_REUSED")
            return@withContext it
        }
        pending?.first?.takeIf { it.requestId == command.requestId }?.let {
            if (fingerprint(it) != requestFingerprint) return@withContext rejected(command, "REQUEST_ID_REUSED")
        }
        if (command.operation == NoticeOperation.RETRY_STORAGE) {
            if (!loaded) load()
            if (!loaded) return@withContext failed(command, "HISTORY_READ_FAILED")
            val retry = pending
            if (retry != null) {
                val committed = commit(retry.first, retry.second)
                if (committed.status != NoticeCommandStatus.COMPLETED) return@withContext failed(command, "HISTORY_WRITE_FAILED")
            }
            return@withContext NoticeCommandResult(command.requestId, NoticeCommandStatus.COMPLETED, disk.revision)
        }
        if (!loaded) return@withContext failed(command, "HISTORY_READ_FAILED")
        if (command.operation in setOf(NoticeOperation.MARK_READ, NoticeOperation.REMOVE) && command.noticeId.orEmpty().length !in 1..256) return@withContext rejected(command, "MISSING_NOTICE_ID")
        if (command.eventStream != null && (command.operation != NoticeOperation.PUBLISH || command.eventStream != "anchor" || (command.eventSequence ?: 0) <= 0)) return@withContext rejected(command, "INVALID_EVENT_CURSOR")
        if (command.eventSequence != null && command.eventStream == null) return@withContext rejected(command, "MISSING_EVENT_STREAM")
        if (disk.revision == Long.MAX_VALUE) return@withContext rejected(command, "REVISION_LIMIT")
        if (pending != null) return@withContext failed(command, "RETRY_STORAGE_FIRST")
        if (command.expectedEpoch != null && command.expectedEpoch != epoch) return@withContext rejected(command, "EPOCH_CHANGED")
        if (command.expectedRevision != null && command.expectedRevision != disk.revision) return@withContext rejected(command, "REVISION_CHANGED")
        val record = command.record
        val eventSequence = command.eventSequence
        if (command.operation == NoticeOperation.PUBLISH && record == null && !(command.eventStream == "anchor" && (command.eventSequence ?: 0) > 0)) return@withContext rejected(command, "MISSING_RECORD")
        if (command.operation in setOf(NoticeOperation.PUBLISH, NoticeOperation.RESTORE) && record != null && !validRecord(record)) return@withContext rejected(command, "INVALID_RECORD")
        val eventKey = record?.domainEventId?.let { "${record.publisher ?: "system"}:$it" }
        val duplicateEvent = command.operation == NoticeOperation.PUBLISH && eventKey != null && eventKey in disk.acceptedEvents
        val duplicateAnchor = command.operation == NoticeOperation.PUBLISH && command.eventStream == "anchor" && eventSequence != null && eventSequence <= disk.anchorCursor
        if (duplicateEvent || duplicateAnchor) return@withContext NoticeCommandResult(command.requestId, NoticeCommandStatus.COMPLETED, disk.revision)
        var records = disk.records
        when (command.operation) {
            NoticeOperation.PUBLISH -> {
                if (!duplicateEvent && !duplicateAnchor && record != null) {
                    if (records.any { it.id == record.id && it.publisher != record.publisher }) return@withContext rejected(command, "NOTICE_PUBLISHER_CONFLICT")
                    val previous = records.firstOrNull { it.publisher == record.publisher && (it.id == record.id || (record.aggregationKey != null && it.aggregationKey == record.aggregationKey && record.updatedAtUtcMillis - it.updatedAtUtcMillis in 0..30_000)) }
                    val next = record.copy(id = previous?.id ?: record.id, occurredAtUtcMillis = minOf(previous?.occurredAtUtcMillis ?: record.occurredAtUtcMillis, record.occurredAtUtcMillis),
                        updatedAtUtcMillis = maxOf(previous?.updatedAtUtcMillis ?: record.updatedAtUtcMillis, record.updatedAtUtcMillis),
                        read = false, occurrences = previous?.let { if (command.publishMode == NoticePublishMode.STATE_UPDATE) it.occurrences else (it.occurrences + 1).coerceAtMost(1_000_000) } ?: record.occurrences)
                    records = (listOf(next) + records.filterNot { it.id == next.id }).take(NotificationProtocol.MAX_HISTORY)
                }
            }
            NoticeOperation.MARK_READ -> records = records.map { if (it.id == command.noticeId) it.copy(read = true) else it }
            NoticeOperation.REMOVE -> records = records.filterNot { it.id == command.noticeId && it.dismissible }
            NoticeOperation.CLEAR_ALL -> records = records.filterNot { it.dismissible }
            NoticeOperation.CLEAR_READ -> records = records.filterNot { it.read && it.dismissible }
            NoticeOperation.RESTORE -> {
                if (record == null) return@withContext rejected(command, "MISSING_RECORD")
                if (records.none { it.id == record.id }) records = (records + record).sortedByDescending { it.updatedAtUtcMillis }.take(NotificationProtocol.MAX_HISTORY)
            }
            NoticeOperation.RETRY_STORAGE -> Unit
        }
        val accepted = if (command.operation == NoticeOperation.PUBLISH && !duplicateEvent && eventKey != null) (disk.acceptedEvents + eventKey).takeLast(512) else disk.acceptedEvents
        val cursor = if (command.eventStream == "anchor") maxOf(disk.anchorCursor, command.eventSequence ?: 0) else disk.anchorCursor
        val revision = disk.revision + 1
        val receipt = NoticeCommandResult(command.requestId, NoticeCommandStatus.COMPLETED, revision)
        val receipts = (disk.receipts + receipt).takeLast(128)
        val next = disk.copy(revision = revision, records = records, acceptedEvents = accepted, anchorCursor = cursor,
            receipts = receipts, commandFingerprints = (disk.commandFingerprints + (command.requestId to requestFingerprint)).filterKeys { id -> receipts.any { it.requestId == id } })
        commit(command, next)
    } }
    private fun commit(command: NoticeCommand, next: Disk): NoticeCommandResult = try {
        persist(next)
        disk = next
        pending = null
        publish()
        next.receipts.last()
    } catch (_: Exception) {
        pending = command to next
        publish("HISTORY_WRITE_FAILED")
        failed(command, "HISTORY_WRITE_FAILED")
    }
    private fun fingerprint(command: NoticeCommand): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(gson.toJson(command).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun persist(value: Disk) {
        file.baseFile.parentFile?.let { require(it.isDirectory || it.mkdirs()) }
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 32_000_000) { "HISTORY_CAPACITY" }
        val stream = file.startWrite()
        try {
            stream.write(bytes); stream.fd.sync(); file.finishWrite(stream)
            // ART/JVM 的反射字段顺序并不相同，revision 不保证在 JSON 前 256 字节。
            // 逐字节核对完整已提交内容，既不误判长历史，也不忽略静默 rename 失败。
            check(file.baseFile.length() == bytes.size.toLong()) { "HISTORY_COMMIT_INCOMPLETE" }
            file.openRead().use { input ->
                val buffer = ByteArray(8192)
                var offset = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(offset + count <= bytes.size) { "HISTORY_COMMIT_INCOMPLETE" }
                    for (index in 0 until count) check(buffer[index] == bytes[offset + index]) { "HISTORY_COMMIT_INCOMPLETE" }
                    offset += count
                }
                check(offset == bytes.size) { "HISTORY_COMMIT_INCOMPLETE" }
            }
        } catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private fun rejected(command: NoticeCommand, reason: String) = NoticeCommandResult(command.requestId, NoticeCommandStatus.REJECTED, disk.revision, reason)
    private fun failed(command: NoticeCommand, reason: String) = NoticeCommandResult(command.requestId, NoticeCommandStatus.PERSISTENCE_FAILED, disk.revision, reason)
}

internal fun validRecord(value: NoticeRecord): Boolean = runCatching {
    val target = value.target
    value.id.length in 1..256 && value.publisher.orEmpty().length <= 64 && value.text.titleZh.length <= 256 && value.text.titleEn.length <= 256 &&
        value.text.bodyZh.length <= 4000 && value.text.bodyEn.length <= 4000 && value.text.code.orEmpty().length <= 128 &&
        value.text.arguments.orEmpty().size <= 16 && value.text.arguments.orEmpty().all { (key, text) -> key.length in 1..64 && text.length <= 256 } &&
        value.level.name.isNotBlank() && value.domainEventId.orEmpty().length <= 256 && value.aggregationKey.orEmpty().length <= 256 && value.category.length <= 64 &&
        value.occurredAtUtcMillis > 0 && value.updatedAtUtcMillis >= value.occurredAtUtcMillis && value.occurrences in 1..1_000_000 &&
        (target == null || (target.domain.length in 1..64 && target.objectId.orEmpty().length <= 256 && target.objectType.orEmpty().length <= 64 && target.section.orEmpty().length <= 256))
}.getOrDefault(false)
private fun java.io.InputStream.readBytesLimited(): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= 32_000_000)
        output.write(buffer, 0, count)
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}
private fun JSONObject.nullableString(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }
/** 仅兼容旧消息路由；新领域发布者直接提交 NoticeTarget。 */
fun legacyNoticeTarget(route: String): NoticeTarget {
    val parts = route.split(':', limit = 3)
    return if (parts.size == 3) NoticeTarget(parts[0], parts[1], parts[2]) else NoticeTarget(parts[0], section = parts.getOrNull(1))
}
fun NoticeTarget.legacyRoute(): String = listOfNotNull(domain, objectType ?: section, objectId).joinToString(":")

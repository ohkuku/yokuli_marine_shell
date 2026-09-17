package com.yokuli.marine.shell.rebuild

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 通知属于发布应用；查看通知不等于确认警报，也不会停止后台会话。 */
enum class NoticeSeverity { INFO, WARNING, ALARM }

/** 保存双语内容、发生时间与目标对象，切换语言后历史通知仍一致显示。 */
data class SystemNotice(
    val id: String = uid(), val app: AppId?, val chinese: String, val english: String,
    val createdAt: Long = System.currentTimeMillis(), val severity: NoticeSeverity = NoticeSeverity.INFO,
    val destination: String? = null, val read: Boolean = false, val key: String? = null, val occurrences: Int = 1,
) {
    fun text(os: OsStore) = os.t(chinese, english)
    fun json() = JSONObject().put("id", id).put("app", app?.name).put("zh", chinese).put("en", english)
        .put("time", createdAt).put("severity", severity.name).put("destination", destination)
        .put("read", read).put("key", key).put("occurrences", occurrences)
    companion object {
        fun from(j: JSONObject) = SystemNotice(j.getString("id"), AppId.entries.find { it.name == j.optString("app") },
            j.getString("zh"), j.getString("en"), j.getLong("time"),
            NoticeSeverity.entries.find { it.name == j.optString("severity") } ?: NoticeSeverity.INFO,
            j.optString("destination").takeIf { it.isNotBlank() && it != "null" }, j.optBoolean("read"),
            j.optString("key").takeIf { it.isNotBlank() && it != "null" }, j.optInt("occurrences", 1))
    }
}

/** 全系统唯一消息中心：最多保留 200 条，磁盘串行写入；toast 排队且不改变应用视口。 */
class SystemNotificationStore(context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "system-notifications.json"))
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val banners = Channel<SystemNotice>(32, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    var items by mutableStateOf<List<SystemNotice>>(emptyList()); private set
    var banner by mutableStateOf<SystemNotice?>(null); private set
    var expanded by mutableStateOf(false); private set
    private var appCaptureBlockedUntil=0L
    private var historyLoaded=false
    private var markRestoredRead=false
    private var discardRestoredRead=false
    /** 通知中心及其收起动画覆盖应用时，最近任务保留原应用截图。 */
    val canCaptureApp get()=!expanded&&android.os.SystemClock.elapsedRealtime()>=appCaptureBlockedUntil
    val unreadCount get() = items.count { !it.read }
    init {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { JSONArray(file.openRead().bufferedReader().use { it.readText() }).objects().mapNotNull { runCatching { SystemNotice.from(it) }.getOrNull() } }.getOrDefault(emptyList())
            }
            // 冷启动读盘期间用户仍可展开或清空中心；这些操作也必须作用于迟到的历史。
            val restored=saved.map{if(markRestoredRead)it.copy(read=true)else it}.filterNot{discardRestoredRead&&it.read}
            items = (items + restored).distinctBy { it.id }.sortedByDescending { it.createdAt }.take(200)
            historyLoaded=true
            for (signal in writes) {
                val snapshot=JSONArray(items.map {it.json()}).toString()
                withContext(Dispatchers.IO) {
                runCatching {
                    val stream = file.startWrite()
                    try { stream.write(snapshot.toByteArray()); file.finishWrite(stream) }
                    catch (e: Exception) { file.failWrite(stream); throw e }
                }
            }
            }
        }
        scope.launch {
            for (notice in banners) {
                if (expanded || items.none { it.id == notice.id && !it.read }) continue
                banner = notice
                delay(if (notice.severity == NoticeSeverity.INFO) 3500 else 6000)
                if (banner?.id == notice.id) banner = null
            }
        }
    }
    fun post(notice: SystemNotice) {
        val previous = items.firstOrNull { notice.key != null && it.key == notice.key }
        // 同一事件短时间反复到达只更新次数；不让弱定位信号反复挤占界面。
        val merge = previous != null && notice.createdAt - previous.createdAt < 30_000
        val entry = if (merge) notice.copy(id = previous!!.id, occurrences = previous.occurrences + 1, read = expanded) else notice.copy(read = expanded)
        items = (listOf(entry) + items.filterNot { it.id == entry.id }).take(200)
        save()
        if (!expanded && !merge) banners.trySend(entry)
    }
    fun open() { if(!historyLoaded)markRestoredRead=true; expanded = true; banner = null; items = items.map { it.copy(read = true) }; save() }
    fun close() { if(expanded)appCaptureBlockedUntil=android.os.SystemClock.elapsedRealtime()+260L;expanded = false }
    fun toggle() { if (expanded) close() else open() }
    fun dismissBanner() { banner = null }
    fun remove(id: String) { items = items.filterNot { it.id == id }; save() }
    fun clearRead() { if(!historyLoaded)discardRestoredRead=true; items = items.filterNot { it.read }; save() }
    private fun save() { writes.trySend(Unit) }
}

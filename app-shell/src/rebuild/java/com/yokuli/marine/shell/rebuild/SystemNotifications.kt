package com.yokuli.marine.shell.rebuild

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.runtime.contract.ais.AisTrafficService
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
    private val loaded=CompletableDeferred<Unit>()
    private var lastAnchorEventId=0L
    private val acceptedAisIds=linkedSetOf<String>()
    private var aisSource:AisTrafficService?=null
    private var aisSubscription:Job?=null
    private var markRestoredRead=false
    private var discardRestoredRead=false
    private var discardRestored=false
    private val removedBeforeLoad=mutableSetOf<String>()
    /** 通知中心及其收起动画覆盖应用时，最近任务保留原应用截图。 */
    val canCaptureApp get()=!expanded&&android.os.SystemClock.elapsedRealtime()>=appCaptureBlockedUntil
    val unreadCount get() = items.count { !it.read }
    init {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val text=file.openRead().bufferedReader().use {it.readText()}
                    val root=if(text.trimStart().startsWith("{"))JSONObject(text)else null
                    val entries=root?.optJSONArray("items") ?: JSONArray(text)
                    val ids=root?.optJSONArray("aisNoticeIds")?.let {array ->
                        (maxOf(0,array.length()-512) until array.length()).mapNotNull {index->
                            array.optString(index).takeIf {it.isNotBlank()&&it.length<=256}
                        }
                    }.orEmpty()
                    Triple(root?.optLong("anchorEventId") ?: 0L,entries.objects().mapNotNull {runCatching{SystemNotice.from(it)}.getOrNull()},ids)
                }.getOrDefault(Triple(0L,emptyList(),emptyList()))
            }
            // 冷启动读盘期间用户仍可展开或清空中心；这些操作也必须作用于迟到的历史。
            val restored=if(discardRestored)emptyList() else saved.second
                .map{if(markRestoredRead)it.copy(read=true)else it}
                .filterNot{it.id in removedBeforeLoad || discardRestoredRead&&it.read}
            items = (items + restored).distinctBy { it.id }.sortedByDescending { it.createdAt }.take(200)
            lastAnchorEventId=saved.first
            acceptedAisIds.addAll(saved.third)
            historyLoaded=true
            removedBeforeLoad.clear()
            loaded.complete(Unit)
            for (signal in writes) {
                val snapshot=JSONObject().put("items",JSONArray(items.map {it.json()})).put("anchorEventId",lastAnchorEventId)
                    .put("aisNoticeIds",JSONArray(acceptedAisIds.toList())).toString()
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
    fun post(notice: SystemNotice, showBanner:Boolean=true) {
        val previous = items.firstOrNull { notice.key != null && it.key == notice.key }
        // 同一事件短时间反复到达只更新次数；不让弱定位信号反复挤占界面。
        val merge = previous != null && notice.createdAt - previous.createdAt < 30_000
        val entry = if (merge) notice.copy(id = previous!!.id, occurrences = previous.occurrences + 1, read = expanded) else notice.copy(read = expanded)
        items = (listOf(entry) + items.filterNot { it.id == entry.id }).take(200)
        save()
        if (showBanner && !expanded && !merge) banners.trySend(entry)
    }
    suspend fun awaitLoaded()=loaded.await()
    /** 单个进程级订阅；更换运行时会取消旧订阅，页面和 Activity 关闭不停止消息接收。 */
    internal fun connectAis(source:AisTrafficService) {
        if(aisSource===source)return
        aisSubscription?.cancel()
        aisSource=source
        aisSubscription=scope.launch {
            awaitLoaded()
            source.snapshot.collect {traffic ->
                traffic.notices.sortedBy {it.issuedAtUtcMillis}.forEach {notice ->
                    if(notice.mmsi in 1..999_999_999)acceptAisNotice(notice.id,notice.asNotice())
                }
            }
        }
    }
    /** 收到过的 ID 与消息在同一 AtomicFile 写入；删除/读取不会被下一秒快照复活。 */
    private fun acceptAisNotice(id:String,notice:SystemNotice) {
        if(id.isBlank()||id.length>256||!acceptedAisIds.add(id))return
        while(acceptedAisIds.size>512)acceptedAisIds.remove(acceptedAisIds.first())
        post(notice,showBanner=System.currentTimeMillis()-notice.createdAt in 0..10_000)
    }
    /** 事件游标和通知一起落盘；已删除的历史不会在下一次 Room 订阅时重新出现。 */
    fun acceptAnchorEvent(id:Long,notice:SystemNotice?) {
        if(id<=lastAnchorEventId)return
        lastAnchorEventId=id
        if(notice!=null)post(notice,showBanner=System.currentTimeMillis()-notice.createdAt in 0..10_000)
        else save()
    }
    fun open() { if(!historyLoaded)markRestoredRead=true; expanded = true; banner = null; items = items.map { it.copy(read = true) }; save() }
    fun close() { if(expanded)appCaptureBlockedUntil=android.os.SystemClock.elapsedRealtime()+260L;expanded = false }
    fun toggle() { if (expanded) close() else open() }
    fun dismissBanner() { banner = null }
    fun remove(id: String) {
        if(!historyLoaded)removedBeforeLoad+=id
        items = items.filterNot { it.id == id }
        if(banner?.id==id)banner=null
        save()
    }
    /** 只清消息，不修改守锚警报和后台会话；迟到的读盘结果也不能复活消息。 */
    fun clearAll() { if(!historyLoaded)discardRestored=true; items=emptyList(); banner=null; save() }
    fun clearRead() { if(!historyLoaded)discardRestoredRead=true; items = items.filterNot { it.read }; save() }
    private fun save() { writes.trySend(Unit) }
}

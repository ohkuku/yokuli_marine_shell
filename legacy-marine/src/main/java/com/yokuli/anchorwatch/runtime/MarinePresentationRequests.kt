package com.yokuli.anchorwatch.runtime

import android.content.Context
import com.yokuli.anchorwatch.api.MarinePresentationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Core 准备内容、Shell 执行系统 UI。队列跨进程重连保留；接手后不重复弹出分享框。 */
@Singleton class MarinePresentationRequests @Inject constructor(@ApplicationContext context: Context) {
    private data class Document(val requests: List<MarinePresentationRequest> = emptyList())
    private val disk = DurableRuntimeFile(File(context.filesDir, "marine-core"), "presentation-requests.json", Document::class.java)
    private val lock = Mutex()
    private var loadFailure: Throwable? = null
    private val mutable = MutableStateFlow(runCatching { disk.read { Document() }.requests }
        .onFailure { loadFailure = it }.getOrDefault(emptyList()))
    val state = mutable.asStateFlow()
    suspend fun enqueue(request: MarinePresentationRequest) = withContext(Dispatchers.IO) { lock.withLock {
        loadFailure?.let { throw IllegalStateException("PRESENTATION_QUEUE_UNREADABLE", it) }
        val current = mutable.value.filter { System.currentTimeMillis() - it.createdAtUtc in 0..900_000L }
        check(current.size < 16) { "PRESENTATION_QUEUE_FULL" }
        val next = current + request
        disk.write(Document(next)); mutable.value = next
    } }
    suspend fun acknowledge(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        loadFailure?.let { throw IllegalStateException("PRESENTATION_QUEUE_UNREADABLE", it) }
        val next = mutable.value.filterNot { it.id == id }
        disk.write(Document(next)); mutable.value = next
    } }
}

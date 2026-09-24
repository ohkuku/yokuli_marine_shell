package com.yokuli.marine.shell.rebuild

import android.util.AtomicFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** 一次不可变快照的落盘回执。更新内存读模型不代表 SAVED；失败重试不重新创建业务对象。 */
enum class DurableCommitResult { SAVED, FAILED }
data class DurableCommit(val revision: Long, val result: Deferred<DurableCommitResult>)

/** 进程内保存状态；revision 仅用于本次运行的写入排序，不冒充持久化业务版本。 */
data class PersistenceState(
    val requestedRevision: Long = 0,
    val durableRevision: Long = 0,
    val pendingWrites: Int = 0,
    val failedRevision: Long? = null,
    val readFailure: String? = null,
) {
    val saving get() = pendingWrites > 0
    val failed get() = readFailure != null || failedRevision != null && durableRevision < requestedRevision
}

/**
 * experience-v1.json 的唯一写入者，保留原格式与 AtomicFile 恢复机制。
 * 有回执的快照不能被 CONFLATED 吞掉：每次成功对应确实完成的那份写入。
 * 有界队列满时明确失败；页面可对当前读模型重试，不能无上限积压整份收藏。
 */
internal class DurableSnapshotStore(private val file: AtomicFile, scope: CoroutineScope, initialReadFailure: String? = null) {
    private data class Write(val revision: Long, val snapshot: String, val result: CompletableDeferred<DurableCommitResult>)
    private val sequence = AtomicLong()
    private val writes = Channel<Write>(32)
    private val mutableState = MutableStateFlow(PersistenceState(readFailure = initialReadFailure, failedRevision = initialReadFailure?.let { 0L }))
    val state = mutableState.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            for (write in writes) {
                val saved = try {
                    val stream = file.startWrite()
                    try {
                        stream.write(write.snapshot.toByteArray(Charsets.UTF_8))
                        // AtomicFile.finishWrite 会记录而不抛出部分 sync/rename 错误。
                        // 先显式 sync，再确认最终主文件，才对用户发出成功回执。
                        stream.fd.sync()
                        file.finishWrite(stream)
                    } catch (error: Exception) {
                        file.failWrite(stream)
                        throw error
                    }
                    if (file.baseFile.readText(Charsets.UTF_8) != write.snapshot) throw IOException("Snapshot commit mismatch")
                    true
                } catch (_: Exception) { false }
                mutableState.update { current -> current.copy(
                    durableRevision = if (saved) maxOf(current.durableRevision, write.revision) else current.durableRevision,
                    pendingWrites = current.pendingWrites - 1,
                    failedRevision = if (saved) current.failedRevision?.takeIf { it > write.revision }
                        else maxOf(current.failedRevision ?: 0, write.revision),
                ) }
                write.result.complete(if (saved) DurableCommitResult.SAVED else DurableCommitResult.FAILED)
            }
        }
    }

    /** 仅在原文件完整读取且 UI 合并完成后解除写保护；重试保存不能自行绕过读取失败。 */
    @Synchronized fun contentReadRestored() {
        mutableState.update { it.copy(readFailure = null, failedRevision = null) }
    }

    @Synchronized
    fun submit(snapshot: String): DurableCommit {
        val revision = sequence.incrementAndGet()
        val result = CompletableDeferred<DurableCommitResult>()
        if (mutableState.value.readFailure != null) {
            mutableState.update { it.copy(requestedRevision = revision, failedRevision = revision) }
            result.complete(DurableCommitResult.FAILED)
            return DurableCommit(revision, result)
        }
        mutableState.update { it.copy(requestedRevision = revision, pendingWrites = it.pendingWrites + 1) }
        if (writes.trySend(Write(revision, snapshot, result)).isFailure) {
            mutableState.update { it.copy(pendingWrites = it.pendingWrites - 1, failedRevision = revision) }
            result.complete(DurableCommitResult.FAILED)
        }
        return DurableCommit(revision, result)
    }
}

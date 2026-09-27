package com.yokuli.anchorwatch.runtime

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import com.yokuli.anchorwatch.data.database.AlarmEventEntity
import com.yokuli.anchorwatch.data.database.AppDatabase
import com.yokuli.anchorwatch.data.database.TripEventEntity
import com.yokuli.anchorwatch.domain.model.AnchorMonitoringPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程唯一恢复屏障。Activity/Service/Receiver 的启动先后都不能绕过它。
 * 同次开机保留用户运行意图；设备重启（或无法证明同次开机）先冻结保护/记录，等待显式继续。
 * 每代只有一次事务化缺口事件；文件失败不放行，重试仍使用同一 generation 去重。
 */
@Singleton
class MarineRecoveryBarrier @Inject constructor(
    @ApplicationContext context: Context,
    private val database: AppDatabase,
    private val residency: RuntimeResidencyRepository,
    private val anchors: AnchorCommandRegistry,
    private val voyages: VoyageCommandRegistry,
    private val recording: com.yokuli.anchorwatch.data.trip.TripSampleWriter,
) {
    data class Recovery(val generation: String, val deviceRestarted: Boolean, val restoredAtUtc: Long)
    private data class Document(val version: Int = 1, val bootCount: Int? = null, val bootEpochMillis: Long = 0,
        val generation: String = "", val recoveredAtUtc: Long = 0)
    private val lock = Mutex()
    private val disk = DurableRuntimeFile(File(context.filesDir, "marine-core"), "recovery.json", Document::class.java)
    private val bootCount = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
    private val generation = UUID.randomUUID().toString()
    @Volatile private var completed: Recovery? = null

    /** 用户明确重试：重新读原账本/尾日志，成功前不授权设备能力。 */
    suspend fun retryRecovery():Recovery {
        lock.withLock {
            if(residency.state.value.recoveryProblem!=null)completed=null
            withContext(Dispatchers.IO){anchors.retryStorage();voyages.retryStorage();recording.retryStorage()}
        }
        return ensureRecovered()
    }

    suspend fun ensureRecovered(): Recovery = completed ?: lock.withLock {
        completed?.let { return@withLock it }
        residency.recoveryStarted(generation)
        try {
            withContext(Dispatchers.IO) {
                anchors.requireReadable(); voyages.requireReadable()
                check(!recording.flush().writeFailed) { "RECORDING_TAIL_RECOVERY_FAILED" }
                val previous = disk.read { Document() }
                require(previous.version == 1) { "CORE_RECOVERY_SCHEMA_UNSUPPORTED" }
                val now = System.currentTimeMillis()
                val bootEpoch = now - SystemClock.elapsedRealtime()
                // BOOT_COUNT 是依据；不可读取时保守要求确认，不能用可调系统时钟冒充开机ID。
                val restarted = previous.generation.isBlank() || bootCount == null || previous.bootCount != bootCount
                database.withTransaction {
                    database.anchorDao().active()?.let { session ->
                        val frozen = if (restarted && !session.paused) session.copy(paused = true,
                            alarmSnoozedUntil = null, monitoringPhase = AnchorMonitoringPhase.PAUSED.name) else session
                        val marker = "generation=$generation;restart=$restarted"
                        if (!database.anchorDao().hasRecoveryMarker(session.id, marker)) {
                            database.anchorDao().updateSessionAndInsertEvent(frozen, AlarmEventEntity(
                                sessionId = session.id, timestamp = now, type = "CORE_RECOVERY_GAP", detail = marker))
                        }
                    }
                    database.tripDao().active()?.let { session ->
                        val marker = "generation=$generation;restart=$restarted"
                        if (!database.tripDao().hasRecoveryMarker(session.id, marker)) {
                            val frozen = if (restarted && !session.paused) session.copy(paused = true, pausedAt = now) else session
                            database.tripDao().updateSessionAndInsertEvent(frozen.copy(eventCount = frozen.eventCount + 1),
                                TripEventEntity(tripId = session.id, timestamp = now, type = "CORE_RECOVERY_GAP",
                                    severity = "WARNING", detailJson = marker))
                        }
                    }
                }
                disk.write(Document(bootCount = bootCount, bootEpochMillis = bootEpoch, generation = generation, recoveredAtUtc = now))
                Recovery(generation, restarted, now)
            }.also {
                completed = it
                residency.recoveryFinished(it.generation, it.deviceRestarted)
            }
        } catch (error: Exception) {
            residency.recoveryFailed(generation, error.message ?: "CORE_RECOVERY_FAILED")
            throw error
        }
    }
}

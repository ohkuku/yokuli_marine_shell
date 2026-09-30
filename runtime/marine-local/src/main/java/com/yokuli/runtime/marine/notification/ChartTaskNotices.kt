package com.yokuli.runtime.marine.notification

import android.content.Context
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.runtime.notification.NotificationCoordinator
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.notification.*
import com.yokuli.runtime.contract.time.MarineTime
import com.yokuli.runtime.marine.MarineSystem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import java.io.File

/** Android 常驻栏是 Core 任务的静默投影，不创建导入任务、不持有文件或页面，也不写通知历史。 */
internal suspend fun watchChartTaskPresentation(system: MarineSystem, context: Context, presenter: NotificationCoordinator) {
    combine(system.charts.state, system.services.state.map { it.settings.appLanguage }.distinctUntilChanged()) { charts, language ->
        val zh = when(language) {
            AppLanguage.SIMPLIFIED_CHINESE -> true
            AppLanguage.ENGLISH -> false
            else -> context.resources.configuration.locales[0].language.startsWith("zh")
        }
        fun label(chinese: String, english: String) = if(zh) chinese else english
        buildList {
            charts.activeJob?.takeIf { it.phase.running }?.let { job ->
                val phase = when(job.phase) {
                    ChartImportPhase.COPYING -> label("读取文件", "Reading files")
                    ChartImportPhase.PARSING -> label("解析数据", "Reading geographic data")
                    ChartImportPhase.INDEXING -> label("建立索引", "Preparing lookup index")
                    else -> label("保存数据", "Saving data")
                }
                val progress = if(job.total > 0) " · ${((job.completed.toDouble()/job.total)*100).toInt().coerceIn(0,100)}%" else ""
                add(label("图册 · ", "Chart Library · ") + job.name.take(120) + " · " + phase + progress)
            }
            charts.exportJob?.takeIf { it.phase.running }?.let { job ->
                val phase = when(job.phase) {
                    ChartExportPhase.PREPARING -> label("准备导出", "Preparing export")
                    ChartExportPhase.PACKAGING -> label("打包数据", "Packaging data")
                    else -> label("写入文件", "Writing file")
                }
                val progress = if(job.total > 0) " · ${((job.completed.toDouble()/job.total)*100).toInt().coerceIn(0,100)}%" else ""
                add(label("图册 · ", "Chart Library · ") + job.name.take(120) + " · " + phase + progress)
            }
        }
    }.distinctUntilChanged().collect(presenter::setTaskLines)
}

/** 唯一 Core 消息桥的交付账本；只保存消息与确认游标，不保存或控制第二份导入业务状态。 */
internal suspend fun watchChartTaskResults(system: MarineSystem, context: Context, publish: suspend (NoticeCommand) -> Boolean) = coroutineScope {
    val journal = ChartNoticeDelivery(context)
    chartNoticeStorageRetry { journal.restore() }
    val wake = Channel<Unit>(Channel.CONFLATED)
    launch {
        system.charts.state.map { Triple(it.loading, it.activeJob, it.exportJob) }.distinctUntilChanged().collect { (loading, importJob, exportJob) ->
            if(!loading) {
                val language = when(system.services.state.value.settings.appLanguage) {
                    AppLanguage.SIMPLIFIED_CHINESE -> "zh-CN"
                    AppLanguage.ENGLISH -> "en"
                    else -> context.resources.configuration.locales[0].toLanguageTag()
                }
                chartNoticeStorageRetry { journal.capture(importJob, exportJob, language) }
                wake.trySend(Unit)
            }
        }
    }
    try {
        while(currentCoroutineContext().isActive) {
            val command = journal.next()
            if(command == null) { wake.receive(); continue }
            if(publish(command)) {
                // 先拿到通知仓库的持久成功回执，再落确认；死亡窗口重送完全相同的消息与时间。
                chartNoticeStorageRetry { journal.acknowledge(command.requestId) }
            } else {
                // 不能把被拒绝的消息当成成功丢掉，也不新建 requestId 绕过去重。
                android.util.Log.w("ChartNotices", "Chart result delivery was rejected; retaining the original message")
                delay(5_000)
            }
        }
    } finally { wake.close() }
}

private suspend fun <T> chartNoticeStorageRetry(action: suspend () -> T): T {
    var announced = false
    while(true) {
        try { return action() }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            if(!announced) android.util.Log.w("ChartNotices", "Chart notice delivery storage is unavailable; retaining state for retry", error)
            announced = true
            delay(5_000)
        }
    }
}

private data class ChartNoticeDeliveryDisk(
    val schema: Int = 1,
    val initialized: Boolean = false,
    val lastImportResult: String? = null,
    val lastExportResult: String? = null,
    val pending: List<NoticeCommand> = emptyList(),
)

/** 与图册一样位于当前虚拟世界的 noBackupFilesDir，不能把演练消息交付到真实世界。 */
private class ChartNoticeDelivery(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "chart-notices/delivery-v1.json"))
    private val gson = Gson()
    private val mutex = Mutex()
    private var disk = ChartNoticeDeliveryDisk()

    suspend fun restore() = mutex.withLock {
        VirtualHostServices.beforeRead()
        val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        disk = if(!exists) ChartNoticeDeliveryDisk() else file.openRead().use { input ->
            require(input.channel.size() <= MAX_BYTES) { "CHART_NOTICE_DELIVERY_TOO_LARGE" }
            val value = gson.fromJson(input.bufferedReader(), ChartNoticeDeliveryDisk::class.java)
                ?: error("CHART_NOTICE_DELIVERY_EMPTY")
            require(value.schema == 1 && value.pending.size <= MAX_PENDING && value.pending.all {
                it.requestId.length in 1..128 && it.operation == NoticeOperation.PUBLISH && it.record?.publisher == "LIBRARY"
            }) { "CHART_NOTICE_DELIVERY_INVALID" }
            value
        }
    }

    suspend fun capture(importJob: ChartImportJob?, exportJob: ChartExportJob?, language: String) = mutex.withLock {
        val importKey = importJob?.takeUnless { it.phase.running }?.let { "import:${it.requestId}:${it.phase}" }
        val exportKey = exportJob?.takeUnless { it.phase.running }?.let { "export:${it.requestId}:${it.phase}" }
        if(!disk.initialized) {
            // 首次升级只建立当前终态基线，不重放旧版本历史。之后即使没观察到 running，恢复终态也会补交付。
            save(disk.copy(initialized = true, lastImportResult = importKey, lastExportResult = exportKey))
            return@withLock
        }
        var next = disk
        if(importKey != null && importKey != disk.lastImportResult) {
            next = next.copy(lastImportResult = importKey)
            importJob?.resultNotice()?.let { record -> next = next.copy(pending = next.pending + record.deliveryCommand(importKey, language)) }
        }
        if(exportKey != null && exportKey != disk.lastExportResult) {
            next = next.copy(lastExportResult = exportKey)
            exportJob?.resultNotice()?.let { record -> next = next.copy(pending = next.pending + record.deliveryCommand(exportKey, language)) }
        }
        if(next != disk) save(next)
    }

    suspend fun next(): NoticeCommand? = mutex.withLock { disk.pending.firstOrNull() }
    suspend fun acknowledge(requestId: String) = mutex.withLock {
        if(disk.pending.any { it.requestId == requestId }) save(disk.copy(pending = disk.pending.filterNot { it.requestId == requestId }))
    }

    private fun save(next: ChartNoticeDeliveryDisk) {
        require(next.pending.size <= MAX_PENDING) { "CHART_NOTICE_DELIVERY_FULL" }
        val bytes = gson.toJson(next).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "CHART_NOTICE_DELIVERY_TOO_LARGE" }
        VirtualHostServices.beforeWrite()
        check(file.baseFile.parentFile?.mkdirs() == true || file.baseFile.parentFile?.isDirectory == true)
        val stream = file.startWrite()
        try { stream.write(bytes); stream.fd.sync(); file.finishWrite(stream); disk = next }
        catch(error: Throwable) { file.failWrite(stream); throw error }
    }

    private fun NoticeRecord.deliveryCommand(key: String, language: String): NoticeCommand {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return NoticeCommand("chart-task:$digest", NoticeOperation.PUBLISH, copy(presentationLanguage = language),
            publishMode = NoticePublishMode.OCCURRENCE)
    }
    private companion object { const val MAX_PENDING = 128; const val MAX_BYTES = 1_048_576L }
}

private val ChartImportPhase.running get() = this in setOf(ChartImportPhase.COPYING, ChartImportPhase.PARSING, ChartImportPhase.INDEXING, ChartImportPhase.COMMITTING)
private val ChartExportPhase.running get() = this in setOf(ChartExportPhase.PREPARING, ChartExportPhase.PACKAGING, ChartExportPhase.COPYING)

private fun ChartImportJob.resultNotice(): NoticeRecord? {
    if(phase == ChartImportPhase.CANCELLED || phase.running) return null
    val success = phase == ChartImportPhase.COMPLETE
    val interrupted = phase == ChartImportPhase.INTERRUPTED
    return NoticeRecord("atlas:import:$requestId", "LIBRARY", NoticeText(
        if(success) "数据导入完成" else if(interrupted) "数据导入已中断" else "数据导入失败",
        if(success) "Data imported" else if(interrupted) "Data import interrupted" else "Data import failed",
        if(success) "${name.take(150)}已加入图册。" else "${name.take(150)}尚未全部准备完成。打开图册查看原因并继续，已就绪的数据保留。",
        if(success) "${name.take(150)} is ready in Chart Library." else "${name.take(150)} is not fully prepared. Open Chart Library for details and continue; ready data is preserved.",
        "atlas.import.${phase.name.lowercase()}", mapOf("requestId" to requestId)),
        MarineTime.nowUtcMillis(), level = if(success) NoticeLevel.INFO else NoticeLevel.WARNING,
        target = if(success && datasetId != null) NoticeTarget("chartdataset", objectType = datasetId) else NoticeTarget("library", section = "data"),
        domainEventId = "import:$requestId:$phase", aggregationKey = "atlas:import:$requestId", category = "chart-data")
}

private fun ChartExportJob.resultNotice(): NoticeRecord? {
    if(phase == ChartExportPhase.CANCELLED || phase.running) return null
    val success = phase == ChartExportPhase.COMPLETE
    val interrupted = phase == ChartExportPhase.INTERRUPTED
    return NoticeRecord("atlas:export:$requestId", "LIBRARY", NoticeText(
        if(success) "数据包已导出" else if(interrupted) "数据导出已中断" else "数据导出失败",
        if(success) "Data package exported" else if(interrupted) "Data export interrupted" else "Data export failed",
        if(success) "${name.take(150)}已保存到所选位置。" else "${name.take(150)}没有完成导出。打开图册查看原因，原数据保持不变。",
        if(success) "${name.take(150)} was saved to the selected destination." else "${name.take(150)} was not exported. Open Chart Library for details; source data is unchanged.",
        "atlas.export.${phase.name.lowercase()}", mapOf("requestId" to requestId)),
        MarineTime.nowUtcMillis(), level = if(success) NoticeLevel.INFO else NoticeLevel.WARNING,
        target = NoticeTarget("chartdataset", objectType = datasetId), domainEventId = "export:$requestId:$phase",
        aggregationKey = "atlas:export:$requestId", category = "chart-data")
}

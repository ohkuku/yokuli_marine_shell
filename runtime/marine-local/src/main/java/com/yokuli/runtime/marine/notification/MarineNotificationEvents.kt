package com.yokuli.runtime.marine.notification

import android.content.Context
import com.yokuli.anchorwatch.api.LocalMarineContentService
import com.yokuli.anchorwatch.data.database.AlarmEventEntity
import com.yokuli.runtime.contract.*
import com.yokuli.anchorwatch.runtime.RuntimeUserFeedback
import com.yokuli.anchorwatch.runtime.RuntimeFeedbackContext
import com.yokuli.runtime.marine.MarineSystem
import com.yokuli.runtime.contract.ais.AisNotice
import com.yokuli.runtime.contract.ais.AisRiskLevel
import com.yokuli.runtime.contract.notification.*
import com.yokuli.runtime.marine.ais.LocalAisTrafficService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton

/** 海事事件转消息的唯一桥，随主进程启动，不依赖通知中心、OsStore 或页面显示。 */
@Singleton
class MarineNotificationEvents @Inject constructor(
    @ApplicationContext context: Context,
    private val content: LocalMarineContentService,
    private val ais: LocalAisTrafficService,
    private val systemProvider: javax.inject.Provider<MarineSystem>,
) {
    private val client = BinderNotificationClient.shared(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private val processEpoch = java.util.UUID.randomUUID().toString()
    @Synchronized fun start() {
        if (started) return
        started = true
        scope.launch {
            val services = systemProvider.get().services
            val accepted = linkedSetOf<Long>()
            services.state.map { it.runtimeDiagnostics.pendingUserFeedback }.distinctUntilChanged().collect { feedback ->
                feedback.sortedBy { it.id }.forEach { event ->
                    // 位置时效由船位读模型表达；不把每次更新间隔转换为通知风暴。
                    if (event.id in accepted || event.context == RuntimeFeedbackContext.POSITION_STATUS || publish(NoticeCommand(
                            "runtime:$processEpoch:${event.id}", NoticeOperation.PUBLISH, event.record(processEpoch)))) {
                        accepted.add(event.id)
                        while (accepted.size > 512) accepted.remove(accepted.first())
                        services.feedback.consumeRuntimeFeedback(event.id)
                    }
                }
            }
        }
        scope.launch {
            val published = linkedMapOf<String, VoyageRequestStatus>()
            // 读取有界账本而非易丢失的UI事件队列；通知延迟不丢掉最终执行结果。
            systemProvider.get().voyage.commands.collect { receipts ->
                receipts.forEach { receipt ->
                    if (published[receipt.request.requestId] != receipt.status) {
                        receipt.record()?.let { notice ->
                            if (publish(NoticeCommand("voyage:${receipt.request.requestId}:${receipt.status}", NoticeOperation.PUBLISH,
                                    notice, publishMode = NoticePublishMode.STATE_UPDATE))) published[receipt.request.requestId] = receipt.status
                        }
                    }
                }
                published.keys.retainAll(receipts.map { it.request.requestId }.toSet())
            }
        }
        scope.launch {
            var acceptedCursor = 0L
            content.observeRecentAlarmEvents(200).retryWhen { _, _ -> delay(5000); true }.collect { events ->
                events.sortedBy { it.id }.forEach { event ->
                    if (event.id > acceptedCursor) {
                        publish(NoticeCommand("anchor-event:${event.id}", NoticeOperation.PUBLISH, event.toNoticeRecord(), eventStream = "anchor", eventSequence = event.id))
                        acceptedCursor = event.id
                    }
                }
            }
        }
        scope.launch {
            val accepted = linkedSetOf<String>()
            ais.snapshot.collect { snapshot ->
                snapshot.notices.sortedBy { it.issuedAtUtcMillis }.forEach { event ->
                    if (event.mmsi in 1..999_999_999 && event.id !in accepted) {
                        publish(NoticeCommand("ais-event:${event.id}".take(128), NoticeOperation.PUBLISH, event.record()))
                        accepted.add(event.id)
                        while (accepted.size > 512) accepted.remove(accepted.first())
                    }
                }
            }
        }
    }
    private suspend fun publish(command: NoticeCommand): Boolean {
        var delayMillis = 1000L
        while (currentCoroutineContext().isActive) {
            val result = client.execute(command)
            when (result.status) {
                NoticeCommandStatus.COMPLETED -> return true
                NoticeCommandStatus.REJECTED -> return false // 畸形领域事件不能永久阻塞后续合法事件。
                NoticeCommandStatus.PERSISTENCE_FAILED -> client.execute(NoticeCommand("retry:${command.requestId}".take(128), NoticeOperation.RETRY_STORAGE))
                NoticeCommandStatus.UNKNOWN -> {
                    if (client.result(command.requestId).status == NoticeCommandStatus.COMPLETED) return true
                    // 这里只重送明确幂等的 PUBLISH：原 requestId、事件 ID/游标不变，服务持久去重。
                    // UI 删除/阅读等结果未知的命令不走此重播路径。
                }
                NoticeCommandStatus.NOT_SENT -> Unit
            }
            delay(delayMillis)
            delayMillis = (delayMillis * 2).coerceAtMost(30_000)
        }
        return false
    }
}

private fun AisNotice.record() = NoticeRecord(
    id = "ais-notice:$id", publisher = "AIS", text = NoticeText(titleZh, titleEn, messageZh, messageEn, "ais.traffic", mapOf("mmsi" to mmsi.toString(), "level" to level.name)),
    occurredAtUtcMillis = issuedAtUtcMillis,
    level = when (level) { AisRiskLevel.NONE -> NoticeLevel.INFO; AisRiskLevel.ATTENTION -> NoticeLevel.WARNING; AisRiskLevel.WARNING, AisRiskLevel.URGENT -> NoticeLevel.ALARM },
    target = NoticeTarget("ais", "target", mmsi.toString()), domainEventId = id, aggregationKey = "ais-notice:$id", category = "traffic",
)
/** 守锚事件的唯一纯消息映射；运行时发布和旧客户端投影共用，不创建订阅或持久化写者。 */
fun AlarmEventEntity.toNoticeRecord(): NoticeRecord? {
    val title = when (type) {
        "ALARM_TRIGGERED" -> when (detail) {
            "ANCHOR_RADIUS_EXCEEDED" -> "超出守锚范围" to "anchor boundary exceeded"
            "GPS_DATA_LOST" -> "船位停止更新" to "position updates stopped"
            "GPS_QUALITY_BAD" -> "船位质量下降" to "position quality degraded"
            "NMEA_CONNECTION_LOST" -> "守锚连接中断" to "anchor watch connection lost"
            else -> "守锚警报" to "anchor alarm"
        }
        "WARNING_TRIGGERED" -> "接近守锚边界" to "approaching the anchor boundary"
        "DEPTH_SHALLOW_ALARM" -> "水深低于警戒值" to "depth below your limit"
        "DEPTH_DEEP_ALARM" -> "水深超过警戒值" to "depth above your limit"
        "DEPTH_DATA_LOST" -> "水深警戒等待新数据" to "depth guard is waiting for data"
        "WIND_WARNING" -> "风速接近警戒值" to "wind speed approaching your limit"
        "WIND_ALARM" -> "风速超过警戒值" to "wind speed exceeded your limit"
        "WIND_SHIFT_ALARM" -> "风向变化超出设定范围" to "wind direction change exceeded your limit"
        "WIND_DATA_LOST" -> "风况警戒等待新数据" to "wind guard is waiting for data"
        "ESTIMATED_CENTER_HIGH" -> "锚点估算可供查看" to "anchor estimate ready to review"
        else -> return null
    }
    val informational = type == "ESTIMATED_CENTER_HIGH"
    val warning = type in setOf("WARNING_TRIGGERED", "WIND_WARNING")
    return NoticeRecord(id = "anchor-event:$id", publisher = "ANCHOR", text = NoticeText(title.first, title.second,
        if (informational) "请查看估算结果，再决定是否应用。" else "打开守锚查看当前情况。清除这条记录不会确认或停止监控。",
        if (informational) "Review the estimate before choosing to apply it." else "Open anchor watch for the current state. Clearing this record does not acknowledge or stop monitoring.", "anchor.$type", mapOf("eventType" to type, "detail" to detail.orEmpty().take(256))),
        occurredAtUtcMillis = timestamp, level = if (informational) NoticeLevel.INFO else if (warning) NoticeLevel.WARNING else NoticeLevel.ALARM,
        target = NoticeTarget("anchor"), domainEventId = id.toString(), aggregationKey = "anchor-event:$id", category = "anchor")
}

/** 旧反馈仍缺少显式发布应用字段，此处集中兼容归属；新领域发布者不要继续复制标题推断。 */
private fun RuntimeUserFeedback.record(epoch: String): NoticeRecord {
    val title = englishTitle.lowercase()
    val publisher = when {
        context in setOf(RuntimeFeedbackContext.ARM_WATCH, RuntimeFeedbackContext.DEPTH_DATA_UNAVAILABLE, RuntimeFeedbackContext.WIND_DATA_UNAVAILABLE) -> "ANCHOR"
        title.startsWith("trip") || title.startsWith("recording") || title == "waypoint not saved" -> "VOYAGES"
        title.startsWith("nmea") || title.startsWith("phone/app") || title.startsWith("phone sensor output") || title.startsWith("phone vessel output") -> "NMEA"
        title.startsWith("approach") -> "PLACES"
        listOf("anchor", "alarm", "safety", "wind", "high wind", "condition", "required nmea instrument").any(title::startsWith) -> "ANCHOR"
        else -> null
    }
    val occurred = (System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtime() - receivedElapsedRealtime).coerceAtLeast(0)).coerceAtLeast(1)
    return NoticeRecord("runtime:$epoch:$id", publisher,
        NoticeText(chineseTitle, englishTitle, chineseMessage, englishMessage, "runtime.${context.name}", mapOf("feedbackId" to id.toString())),
        occurred, level = if (highPriority) NoticeLevel.WARNING else NoticeLevel.INFO,
        domainEventId = "$epoch:$id", aggregationKey = "runtime:$context:$englishTitle".take(256), category = "runtime")
}
private fun VoyageCommandReceipt.record(): NoticeRecord? {
    val title = when (status) {
        VoyageRequestStatus.QUEUED, VoyageRequestStatus.EXECUTING -> return null
        VoyageRequestStatus.UNKNOWN -> "航行操作等待结果" to "voyage action awaiting result"
        VoyageRequestStatus.REJECTED -> if (reason == "POSITION_REQUIRED") "需要船位数据" to "position required" else "航行操作未执行" to "voyage action not applied"
        VoyageRequestStatus.FAILED -> "航行操作未完成" to "voyage action failed"
        VoyageRequestStatus.CONFIRMED -> when (request.action) {
            VoyageAction.START -> "航行记录已开始" to "voyage recording started"
            VoyageAction.PAUSE -> "航行记录已暂停" to "voyage recording paused"
            VoyageAction.RESUME -> "航行记录已继续" to "voyage recording resumed"
            VoyageAction.FINISH -> "航行记录已保存" to "voyage recording saved"
        }
    }
    val text = when {
        status == VoyageRequestStatus.UNKNOWN -> "后台可能仍在处理，请查看航行日志中的当前状态，不要重复提交。" to "The runtime may still be working. Check Logbook before submitting again."
        reason == "POSITION_REQUIRED" -> "请在数据中心选择可用的船位来源，再开始记录。" to "Choose a usable position source in Data Center before starting."
        status == VoyageRequestStatus.CONFIRMED -> "打开航行日志查看本次记录。" to "Open Logbook to view this recording."
        else -> "打开航行日志查看当前状态和具体原因。" to "Open Logbook for the current state and details."
    }
    return NoticeRecord("voyage-command:${request.requestId}", "VOYAGES", NoticeText(title.first, title.second, text.first, text.second,
        "voyage.${request.action}.${status}", mapOf("requestId" to request.requestId, "status" to status.name)), System.currentTimeMillis(),
        level = if (status == VoyageRequestStatus.CONFIRMED) NoticeLevel.INFO else NoticeLevel.WARNING,
        target = NoticeTarget("voyages"), domainEventId = "${request.requestId}:$status", aggregationKey = "voyage-command:${request.requestId}", category = "voyage-command")
}

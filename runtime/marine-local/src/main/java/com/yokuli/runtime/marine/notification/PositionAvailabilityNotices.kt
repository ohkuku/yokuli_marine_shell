package com.yokuli.runtime.marine.notification

import com.yokuli.runtime.contract.time.MarineTime

import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.PositionHealth
import com.yokuli.runtime.contract.RuntimeResidencyPhase
import com.yokuli.runtime.contract.VoyagePhase
import com.yokuli.runtime.contract.navigation.NavigationPhase
import com.yokuli.runtime.contract.notification.*
import com.yokuli.runtime.marine.MarineSystem
import kotlinx.coroutines.*

/** 只读当前事实；在 Binder 传输锁内再次使用，排除已过时、但仍在等待连接的提醒。 */
internal fun positionNeedsAttention(system: MarineSystem): Boolean {
    val runtime = system.residency.state.value
    val data = system.services.state.value
    if(!runtime.requested || runtime.phase in setOf(RuntimeResidencyPhase.STOPPED, RuntimeResidencyPhase.STOPPING) || !data.settingsReady) return false
    val position = data.acceptedPosition
    val neededByTask = data.active?.paused == false || system.voyage.state.value.phase == VoyagePhase.RECORDING ||
        system.navigation.state.value.session?.phase == NavigationPhase.ACTIVE || system.ais.snapshot.value.preferences.monitoringEnabled
    if(position.selectedSource == GpsDataSource.NONE && !neededByTask) return false
    val age = position.lastAcceptedElapsedRealtime?.let { (MarineTime.nowElapsedMillis() - it).coerceAtLeast(0) }
    return position.acceptedFix == null || position.health == PositionHealth.GPS_LOST || age == null || age > 45_000L
}

/**
 * 船位失联是系统状态，不是每次GPS间隔的错误。只读唯一位置/任务事实，保留30秒启动宽限。
 * 同一条待处理记录不可清除；两分钟一次提醒，读过或划走横幅不代表问题已解决。
 * 明确关闭船位且没有位置任务后停止提醒。健康恢复不续写旧观测时间。
 */
internal suspend fun watchPositionAvailability(system: MarineSystem, publish: suspend (NoticeCommand) -> Boolean) {
    val id = "system:position-required"
    var missingSince: Long? = null
    var lastReminder = 0L
    var published = false
    var resolved = false
    var sequence = 0L
    val epoch = java.util.UUID.randomUUID().toString()
    while (currentCoroutineContext().isActive) {
        val runtime = system.residency.state.value
        val data = system.services.state.value
        val position = data.acceptedPosition
        val neededByTask = data.active?.paused == false || system.voyage.state.value.phase == VoyagePhase.RECORDING ||
            system.navigation.state.value.session?.phase == NavigationPhase.ACTIVE || system.ais.snapshot.value.preferences.monitoringEnabled
        val now = MarineTime.nowElapsedMillis()
        val observationAge = position.lastAcceptedElapsedRealtime?.let { (now - it).coerceAtLeast(0) }
        val usable = position.acceptedFix != null && position.health != PositionHealth.GPS_LOST && observationAge != null && observationAge <= 45_000L
        val missing = positionNeedsAttention(system)
        if (missing) {
            resolved = false
            if (missingSince == null) missingSince = now
            if (now - requireNotNull(missingSince) >= 30_000 && (!published || now - lastReminder >= 120_000)) {
                val ageZh = observationAge?.let { if (it < 60_000) "${it / 1000} 秒前" else "${it / 60_000} 分钟前" } ?: "尚未收到"
                val ageEn = observationAge?.let { if (it < 60_000) "${it / 1000}s ago" else "${it / 60_000}m ago" } ?: "not received"
                val source = when(position.selectedSource) { GpsDataSource.SYSTEM -> "手机 GPS" to "Phone GPS"; GpsDataSource.NMEA -> "船联网" to "Boat Network"; else -> "船位来源" to "Position source" }
                val time = MarineTime.nowUtcMillis()
                val record = NoticeRecord(id, "DATA_CENTER", NoticeText("需要恢复船位", "Restore vessel position",
                    "${source.first} · 上次可信船位：$ageZh。选择可用来源；仅浏览时可在数据中心关闭船位。",
                    "${source.second} · Last accepted position: $ageEn. Choose a working source, or turn position off in Data Center for browsing.",
                    "position.missing", mapOf("source" to position.selectedSource.name, "reason" to position.reason.orEmpty().take(128))),
                    time, level=NoticeLevel.WARNING, target=NoticeTarget("data_center",section="source/POSITION"),
                    domainEventId="$epoch:${++sequence}", aggregationKey=id, category="position", dismissible=false)
                if (publish(NoticeCommand("position:$epoch:$sequence", NoticeOperation.PUBLISH, record, publishMode=NoticePublishMode.STATE_UPDATE))) {
                    lastReminder = now; published = true
                }
            }
        } else {
            missingSince = null
            // 同一进程恢复、明确关闭，或上次进程留下的待处理记录，均通过领域解析动作结束。
            if (!resolved && data.settingsReady && (published || !runtime.requested || usable || position.selectedSource == GpsDataSource.NONE && !neededByTask)) {
                if(publish(NoticeCommand("position-resolve:$epoch:${++sequence}", NoticeOperation.RESOLVE, noticeId=id))) {
                    published = false; resolved = true
                }
            }
        }
        MarineTime.sleep(5000)
    }
}

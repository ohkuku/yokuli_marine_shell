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
 * 同一失联只产生一条待处理记录；持续失联不重复通知，恢复稳定10秒后收束。
 * 明确关闭船位且没有位置任务后停止提醒。健康恢复不续写旧观测时间。
 */
internal suspend fun watchPositionAvailability(system: MarineSystem, publish: suspend (NoticeCommand) -> Boolean) {
    val id = "system:position-required"
    var published = false
    var resolved = false
    var missingSince: Long? = null
    var healthySince: Long? = null
    var sequence = 0L
    var sourceKey: String? = null
    var publishedSource: String? = null
    // Display history only. Losing an integrity epoch must never make this cached time a usable fix.
    val lastObservedBySource = linkedMapOf<String, Long>()
    val epoch = java.util.UUID.randomUUID().toString()
    while (currentCoroutineContext().isActive) {
        val runtime = system.residency.state.value
        val data = system.services.state.value
        val position = data.acceptedPosition
        val neededByTask = data.active?.paused == false || system.voyage.state.value.phase == VoyagePhase.RECORDING ||
            system.navigation.state.value.session?.phase == NavigationPhase.ACTIVE || system.ais.snapshot.value.preferences.monitoringEnabled
        val now = MarineTime.nowElapsedMillis()
        val currentSource = "${MarineTime.snapshot().timeEpoch}:${position.selectedSource}:${if (position.selectedSource == GpsDataSource.NMEA) data.vesselSettings.metricSourcePins["POSITION_CONNECTION"].orEmpty() + ":" + data.vesselSettings.pinnedPositionSourceId.orEmpty() else ""}"
        if (sourceKey != currentSource) {
            sourceKey = currentSource
            missingSince = null
            healthySince = null
        }
        position.lastAcceptedElapsedRealtime?.takeIf { it in 0..now }?.let { observed ->
            lastObservedBySource[currentSource] = maxOf(lastObservedBySource[currentSource] ?: observed, observed)
            while (lastObservedBySource.size > 32) lastObservedBySource.remove(lastObservedBySource.keys.first())
        }
        val lastObserved = lastObservedBySource[currentSource]
        val observationAge = position.lastAcceptedElapsedRealtime?.let { (now - it).coerceAtLeast(0) }
        val displayAge = lastObserved?.let { (now - it).coerceAtLeast(0) }
        val usable = position.acceptedFix != null && position.health != PositionHealth.GPS_LOST && observationAge != null && observationAge <= 45_000L
        val missing = positionNeedsAttention(system)
        if (missing) {
            resolved = false
            healthySince = null
            if (missingSince == null) missingSince = now
            if ((!published || publishedSource != currentSource) && now - requireNotNull(missingSince) >= 30_000L) {
                val ageZh = displayAge?.let { if (it < 60_000) "${it / 1000} 秒前" else "${it / 60_000} 分钟前" } ?: "尚未收到"
                val ageEn = displayAge?.let { if (it < 60_000) "${it / 1000}s ago" else "${it / 60_000}m ago" } ?: "not received"
                val source = when(position.selectedSource) { GpsDataSource.SYSTEM -> "手机 GPS" to "Phone GPS"; GpsDataSource.NMEA -> "船联网" to "Boat Network"; else -> "船位来源" to "Position source" }
                val reason = when {
                    position.selectedSource == GpsDataSource.NONE -> "当前任务需要船位，请明确选择来源。" to "A current task needs position; select a source explicitly."
                    position.selectedSource == GpsDataSource.SYSTEM -> when (system.services.sources.phoneLocationStatus.value.phase) {
                        com.yokuli.anchorwatch.location.PhoneLocationPhase.PERMISSION_REQUIRED -> "尚未取得定位权限，请在数据中心检查权限。" to "Location permission is missing; review permissions in Data Center."
                        com.yokuli.anchorwatch.location.PhoneLocationPhase.PROVIDER_DISABLED -> "手机定位服务已关闭，请在数据中心检查手机定位。" to "Phone location is switched off; review phone location in Data Center."
                        else -> "正在等待所选手机 GPS 的可信定位。" to "Waiting for an accepted fix from the selected Phone GPS."
                    }
                    else -> "正在等待所选船载连接的可信船位，请检查该连接。" to "Waiting for accepted position from the selected boat connection; check that connection."
                }
                val record = NoticeRecord(id, "DATA_CENTER", NoticeText("船位暂未更新", "Position updates paused",
                    "${source.first} · ${reason.first} 提醒时上次可信船位：$ageZh。仅浏览时可在数据中心关闭船位。",
                    "${source.second} · ${reason.second} Last accepted position when reported: $ageEn. For browsing, position can be turned off in Data Center.",
                    "position.missing", mapOf("source" to position.selectedSource.name, "reason" to position.reason.orEmpty().take(128), "lastAcceptedElapsedMillis" to lastObserved?.toString().orEmpty())),
                    MarineTime.nowUtcMillis(), level=NoticeLevel.WARNING, target=NoticeTarget("data_center",section="source/POSITION"),
                    domainEventId="$epoch:${++sequence}", aggregationKey=id, category="position", dismissible=false)
                if (publish(NoticeCommand("position:$epoch:$sequence", NoticeOperation.PUBLISH, record, publishMode=NoticePublishMode.STATE_UPDATE))) {
                    published = true
                    publishedSource = currentSource
                }
            }
        } else {
            missingSince = null
            if (healthySince == null) healthySince = now
            val explicitlyOff = !runtime.requested || position.selectedSource == GpsDataSource.NONE && !neededByTask
            if (!resolved && data.settingsReady && (explicitlyOff || usable && now - requireNotNull(healthySince) >= 10_000L) && publish(NoticeCommand("position-resolve:$epoch:${++sequence}", NoticeOperation.RESOLVE, noticeId=id))) {
                published = false; publishedSource = null; resolved = true
            }
        }
        MarineTime.sleep(5000)
    }
}

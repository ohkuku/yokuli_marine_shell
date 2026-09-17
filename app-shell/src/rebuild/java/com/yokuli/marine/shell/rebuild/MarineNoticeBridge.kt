package com.yokuli.marine.shell.rebuild

import com.yokuli.anchorwatch.data.database.AlarmEventEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/** 进程级订阅：前台服务启动 Application 时即工作，不依赖 Activity 或 Compose 存活。 */
internal fun OsStore.observeMarineNotices()=scope.launch {
    notifications.awaitLoaded()
    val database=EntryPointAccessors.fromApplication(context.applicationContext,SailingDataAccess::class.java).database()
    database.anchorDao().observeRecentAlarmEvents(200).collect {events->
        events.sortedBy {it.id}.forEach {event->notifications.acceptAnchorEvent(event.id,event.asNotice())}
    }
}

internal fun AlarmEventEntity.asNotice():SystemNotice? {
    val title=when(type) {
        "ALARM_TRIGGERED" -> when(detail) {
            "ANCHOR_RADIUS_EXCEEDED" -> "超出守锚范围" to "anchor boundary exceeded"
            "GPS_DATA_LOST" -> "船位停止更新，守锚需要检查" to "position updates stopped; check anchor watch"
            "GPS_QUALITY_BAD" -> "船位质量下降" to "position quality degraded"
            "NMEA_CONNECTION_LOST" -> "守锚的 NMEA 连接中断" to "anchor watch NMEA connection lost"
            else -> "守锚警报" to "anchor alarm"
        }
        "WARNING_TRIGGERED" -> "接近守锚边界" to "approaching the anchor boundary"
        "DEPTH_SHALLOW_ALARM" -> "水深低于警戒值" to "depth below your limit"
        "DEPTH_DEEP_ALARM" -> "水深超过警戒值" to "depth above your limit"
        "DEPTH_DATA_LOST" -> "水深警戒正在等待新数据" to "depth guard is waiting for fresh data"
        "WIND_WARNING" -> "风速接近警戒值" to "wind speed is approaching your limit"
        "WIND_ALARM" -> "风速超过警戒值" to "wind speed exceeded your limit"
        "WIND_SHIFT_ALARM" -> "风向超过变化范围" to "wind direction changed beyond your limit"
        "WIND_DATA_LOST" -> "风况警戒正在等待新数据" to "wind guard is waiting for fresh data"
        "ESTIMATED_CENTER_HIGH" -> "已估计出锚点，等待你确认" to "estimated anchor position ready for review"
        else -> return null
    }
    val severity=when(type) {
        "WARNING_TRIGGERED","WIND_WARNING"->NoticeSeverity.WARNING
        "ESTIMATED_CENTER_HIGH"->NoticeSeverity.INFO
        else->NoticeSeverity.ALARM
    }
    return SystemNotice(id="anchor-event:$id",app=AppId.ANCHOR,chinese=title.first,english=title.second,
        createdAt=timestamp,severity=severity,destination="anchor",key="anchor-event:$id")
}

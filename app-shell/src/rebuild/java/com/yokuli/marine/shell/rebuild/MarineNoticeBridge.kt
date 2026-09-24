package com.yokuli.marine.shell.rebuild

import com.yokuli.anchorwatch.data.database.AlarmEventEntity
import com.yokuli.runtime.marine.notification.toNoticeRecord

/**
 * 保留旧调用方的事件投影入口，委托给运行时唯一的事件映射与 Shell 的实际消息投影。
 * 事件订阅、游标和发布仍由 MarineNotificationEvents 拥有，这里不恢复第二条通知链。
 */
internal fun AlarmEventEntity.asNotice(): SystemNotice? = toNoticeRecord()?.asNotice()

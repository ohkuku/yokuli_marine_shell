package com.yokuli.anchorwatch.runtime.notification

import java.util.Locale

/**
 * 系统偏好拥有者提供的纯显示函数，不复制偏好存储或修改警报阈值。
 * 函数只捕获不可变单位快照，后台通知不依赖 Activity、Compose 或 Shell 对象。
 */
data class NotificationUnitFormats(
    val length: (Double?) -> String,
    val depth: (Double?) -> String,
    val speed: (Double?) -> String,
) {
    companion object {
        /** 系统偏好尚未读入时保留有明确单位的规范值，加载后立即更新。 */
        val CANONICAL = NotificationUnitFormats(
            length = { canonical(it, "m") },
            depth = { canonical(it, "m") },
            speed = { canonical(it, "kn") },
        )
        private fun canonical(value: Double?, unit: String): String =
            value?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.1f %s", it, unit) } ?: "—"
    }
}

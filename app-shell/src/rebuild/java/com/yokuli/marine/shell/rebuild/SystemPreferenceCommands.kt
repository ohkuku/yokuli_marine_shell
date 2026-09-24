package com.yokuli.marine.shell.rebuild

/** Shell 偏好命令的生命周期不属于快捷卡片；SAVED 仅表示 DataStore 写入已完成。 */
enum class SystemPreferenceStatus { PENDING, SAVED, FAILED }
data class SystemPreferenceCommand(val requestId: String, val key: String,
    val status: SystemPreferenceStatus, val error: String? = null)

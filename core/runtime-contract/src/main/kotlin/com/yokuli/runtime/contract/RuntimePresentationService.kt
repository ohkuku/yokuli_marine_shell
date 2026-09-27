package com.yokuli.runtime.contract

/** Shell 偏好的后台投影。只传单位标识，不跨进程传格式化函数或 UI 对象。 */
data class RuntimeUnitPreferences(
    val navigation: String = "NAUTICAL",
    val length: String = "METERS",
    val depth: String = "METERS",
    val temperature: String = "CELSIUS",
    val pressure: String = "HECTOPASCALS",
    val distance: String = "NAUTICAL_MILES",
    val speed: String = "KNOTS",
    val scaleDistance: String? = null,
)

/** 原始设置仍归 Shell；Marine Core 持久保留最近成功的显示快照供冷启动通知使用。 */
interface RuntimePresentationService {
    /** 完成代表投影已原子落盘；失败抛出，调用者保留快照并在重连后重试。 */
    suspend fun updateUnits(preferences: RuntimeUnitPreferences)
}

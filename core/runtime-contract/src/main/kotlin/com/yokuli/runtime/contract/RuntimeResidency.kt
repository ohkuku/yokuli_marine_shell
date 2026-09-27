package com.yokuli.runtime.contract

import kotlinx.coroutines.flow.StateFlow

/** 用户明确启动/退出的系统采集会话；页面可见性不拥有它。 */
enum class RuntimeResidencyPhase { STOPPED, STARTING, RUNNING, STOPPING, BLOCKED }
data class RuntimeResidencyState(
    val requested: Boolean = false,
    val explicitlyStopped: Boolean = false,
    val phase: RuntimeResidencyPhase = RuntimeResidencyPhase.STOPPED,
    val problem: String? = null,
    /** 真实持有的能力，不把设置中选中等同于 Android 已允许采集。 */
    val phoneLocation: Boolean = false,
    val phoneMotion: Boolean = false,
    val phoneHeading: Boolean = false,
    val phonePressure: Boolean = false,
    val inputConnections: Int = 0,
    val outputConnections: Int = 0,
    val sharing: Boolean = false,
    /** 当前 Core 恢复代次；UI 重连订阅不生成新的业务会话。 */
    val recoveryGeneration: String? = null,
    val recoveryReady: Boolean = false,
    /** 设备重启使安全任务暂停，用户需要核对来源后继续。 */
    val deviceRestarted: Boolean = false,
    val recoveryProblem: String? = null,
)
data class RuntimeExitResult(val completed: Boolean, val problem: String? = null)
interface RuntimeResidencyService {
    val state: StateFlow<RuntimeResidencyState>
    /** 只由可见 Activity 的用户启动触发，不能在任意后台订阅中调用。 */
    fun startFromForeground()
    /** 重新读取原始恢复文件，不删除资料或重放安全命令；失败保留阻断原因。 */
    suspend fun retryRecovery(): Boolean
    /** 用户确认后暂停保护/航程、关闭收发并释放资源；失败时保留页面显示实际结果。 */
    suspend fun exit(): RuntimeExitResult
}

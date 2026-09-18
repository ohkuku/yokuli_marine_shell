package com.yokuli.runtime.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 描述真实执行方式；同进程实现不宣称 Binder 或崩溃隔离已经存在。 */
enum class RuntimeTransport { IN_PROCESS, BINDER }
enum class RuntimeReadiness { INITIALIZING, READY, UNAVAILABLE }
data class RuntimeConnection(
    val endpoint: String,
    val transport: RuntimeTransport,
    val readiness: RuntimeReadiness,
    val protocolMajor: Int = 1,
    val processIsolated: Boolean = false,
    val reason: String? = null,
)
interface RuntimeEndpoint { val connection: StateFlow<RuntimeConnection> }

/** 权限交互留给前台宿主；批准后才调用数据源服务，不传任意字符串动作。 */
enum class PositionSourceRequest { ENABLE_PHONE, DISABLE_POSITION, USE_NMEA }

/** 仅表示当前传输是否实现；缺少系统能力必须明确返回，不伪造成功。 */
sealed interface RuntimeBindingResult {
    data class Available(val transport: RuntimeTransport) : RuntimeBindingResult
    data class Unavailable(val reason: String) : RuntimeBindingResult
}
object RuntimeBindings {
    fun resolve(transport: RuntimeTransport): RuntimeBindingResult = when (transport) {
        RuntimeTransport.IN_PROCESS -> RuntimeBindingResult.Available(transport)
        RuntimeTransport.BINDER -> RuntimeBindingResult.Unavailable("ROM_BINDER_NOT_IMPLEMENTED")
    }
}

enum class VoyagePhase { IDLE, STARTING, RECORDING, PAUSED, SAVING }
/** 航行的共享读模型；业务事实来自运行时，界面只负责格式化。 */
data class VoyageSessionState(
    val id: Long? = null,
    val phase: VoyagePhase = VoyagePhase.IDLE,
    val name: String = "",
    val distanceMeters: Double = 0.0,
    val startedAt: Long? = null,
    val pausedAt: Long? = null,
    val accumulatedPausedMillis: Long = 0L,
    val momentCount: Int = 0,
    val commandPending: Boolean = false,
) {
    val active: Boolean get() = id != null
    fun elapsedMillis(now: Long) = startedAt?.let { ((pausedAt ?: now) - it - accumulatedPausedMillis).coerceAtLeast(0) } ?: 0L
}
enum class VoyageAction { START, PAUSE, RESUME, FINISH }
enum class VoyageCommandStatus { CONFIRMED, NOT_CONFIRMED, POSITION_REQUIRED, FAILED }
/** 命令回执只携带业务代码；双语文案由 Shell 的通知投影决定。 */
data class VoyageCommandEvent(val action: VoyageAction, val status: VoyageCommandStatus)

/**
 * 各应用共享的航行会话契约。状态可以多处订阅；events 是 Shell 通知中心的单消费队列，
 * 不作为另一份会话状态。关闭订阅不停止航行，只有明确的 finish 命令结束记录。
 */
interface VoyageSessionService {
    val state: StateFlow<VoyageSessionState>
    val events: Flow<VoyageCommandEvent>
    fun start(name: String, motion: Boolean = false)
    fun pause()
    fun resume()
    fun finish()
}

package com.yokuli.shell.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.yokuli.shell.contract.ShellInput

fun interface InternalAppInputRegistration : AutoCloseable

/**
 * Feature-first input boundary. The mounted app may consume an input; otherwise the pure Shell
 * Engine receives it. Registration identity prevents disposal of an old composition from clearing
 * a newer app handler during animated transitions.
 */
class InternalAppInputRouter {
    private data class MountedHandler(
        val owner: Any,
        val handler: (ShellInput) -> Boolean,
    )

    private val mounted = mutableListOf<MountedHandler>()

    fun register(owner: Any, handler: (ShellInput) -> Boolean): InternalAppInputRegistration {
        synchronized(this) { mounted.removeAll { it.owner === owner }; mounted += MountedHandler(owner, handler) }
        return InternalAppInputRegistration {
            synchronized(this) {
                mounted.removeAll { it.owner === owner }
            }
        }
    }

    fun dispatch(input: ShellInput): Boolean = synchronized(this) { mounted.toList() }
        .asReversed().any { it.handler(input) }
}

val LocalInternalAppInputRouter = staticCompositionLocalOf<InternalAppInputRouter> {
    InternalAppInputRouter()
}

/** 转场保留旧画面时，只有当前任务可以接收虚拟键与物理返回。 */
val LocalInternalAppInputEnabled = staticCompositionLocalOf { true }
/** 当前组合对应的页面访问；原生回调据此拒绝已经离场页面的写入。 */
val LocalInternalAppPageKey = staticCompositionLocalOf<String?> { null }

@Composable
fun BindInternalAppInputHandler(handler: (ShellInput) -> Boolean) {
    val router = LocalInternalAppInputRouter.current
    val currentHandler = rememberUpdatedState(handler)
    val enabled = LocalInternalAppInputEnabled.current
    DisposableEffect(router, enabled) {
        val owner = Any()
        val registration = if (enabled) router.register(owner) { input -> currentHandler.value(input) } else null
        onDispose { registration?.close() }
    }
}

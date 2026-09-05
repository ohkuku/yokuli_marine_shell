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

    @Volatile
    private var mounted: MountedHandler? = null

    fun register(owner: Any, handler: (ShellInput) -> Boolean): InternalAppInputRegistration {
        mounted = MountedHandler(owner, handler)
        return InternalAppInputRegistration {
            synchronized(this) {
                if (mounted?.owner === owner) mounted = null
            }
        }
    }

    fun dispatch(input: ShellInput): Boolean = mounted?.handler?.invoke(input) == true
}

val LocalInternalAppInputRouter = staticCompositionLocalOf<InternalAppInputRouter> {
    InternalAppInputRouter()
}

@Composable
fun BindInternalAppInputHandler(handler: (ShellInput) -> Boolean) {
    val router = LocalInternalAppInputRouter.current
    val currentHandler = rememberUpdatedState(handler)
    DisposableEffect(router) {
        val owner = Any()
        val registration = router.register(owner) { input -> currentHandler.value(input) }
        onDispose(registration::close)
    }
}

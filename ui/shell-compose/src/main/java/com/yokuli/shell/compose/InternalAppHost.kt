package com.yokuli.shell.compose

import androidx.compose.runtime.Composable
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId

/**
 * 中文：Compose 主机是壳与内部应用 UI 的渲染边界，Engine 不依赖它。
 * English: The Compose host is the rendering boundary between the shell and internal app UI.
 */
class InternalAppHost(
    val appId: LauncherAppId,
    private val content: @Composable (LaunchToken) -> Unit,
) {
    @Composable
    fun Render(token: LaunchToken) = content(token)
}

interface InternalAppHostResolver {
    fun hostFor(appId: LauncherAppId): InternalAppHost?
}

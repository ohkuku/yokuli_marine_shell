package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.compose.LocalInternalAppPageKey
import com.yokuli.shell.compose.LocalInternalAppInputEnabled

/** 归属、标题与返回分别定义；两种页面头部消费同一结果，不自行推断应用父目录。 */
internal data class PageNavigation(
    val appIdentity: String,
    val title: String,
    val canGoBack: Boolean,
    val backLabel: String,
    val returnDestination: String?,
    val enabled: Boolean,
    val back: () -> Unit,
)

@Composable internal fun pageNavigation(os: OsStore, title: String, hasLocalBack: Boolean): PageNavigation {
    // Shell 栈发生变化时同步头部；同一地址的不同访问拥有不同的页面 key。
    val shellState by os.shell.engine.state.collectAsState()
    val key = LocalInternalAppPageKey.current
    val destination = os.shell.backDestination(key)
    val owner = os.shell.appForPage(LocalAppPage.current ?: os.page)
    return PageNavigation(owner?.let { os.title(it.app) }.orEmpty(), title,
        hasLocalBack || destination != null, os.t("返回", "back"), if(hasLocalBack) null else destination,
        LocalInternalAppInputEnabled.current && shellState.transient == null, os::back)
}

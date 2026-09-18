package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.compose.LocalInternalAppPageKey

/** 局部状态形成的子页也有业务地址；离场后保留登记，供通知恢复已有会话。 */
@Composable internal fun ReportVisibleAppRoute(os: OsStore, destination: String) {
    val key = LocalInternalAppPageKey.current
    SideEffect { if(key != null) os.shell.reportVisibleRoute(key, destination) }
}

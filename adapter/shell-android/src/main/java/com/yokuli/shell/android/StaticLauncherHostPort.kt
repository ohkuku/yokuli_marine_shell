package com.yokuli.shell.android

import com.yokuli.shell.contract.LaunchResolution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.LauncherHostPort
import com.yokuli.shell.contract.LauncherSystemStatus
import com.yokuli.shell.contract.TileContentSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 中文：Stage 2 的宿主适配器只解析已安装的静态内部入口，不推断运行时能力。
 * English: The Stage 2 host adapter resolves only installed static internal entries.
 */
class StaticLauncherHostPort(
    catalog: LauncherCatalogSnapshot,
    private val launches: Map<LaunchToken, LauncherAppId>,
) : LauncherHostPort {
    override val catalog: StateFlow<LauncherCatalogSnapshot> = MutableStateFlow(catalog)
    override val tileContents: StateFlow<Map<LauncherEntryId, TileContentSnapshot>> = MutableStateFlow(emptyMap())
    override val systemStatus: StateFlow<LauncherSystemStatus> = MutableStateFlow(LauncherSystemStatus())

    init {
        val installedApps = catalog.apps.map { it.appId }.toSet()
        require(launches.values.all { it in installedApps }) { "Launch map contains an app outside the catalog" }
        require(catalog.entries.all { launches[it.launchToken] == it.appId }) {
            "Every catalog entry must resolve to its contributed app"
        }
    }

    override suspend fun resolveLaunch(token: LaunchToken): LaunchResolution = launches[token]?.let { appId ->
        LaunchResolution.Internal(appId, token)
    } ?: LaunchResolution.Unresolved(token)
}

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
    private val dynamicLaunches: List<Pair<LauncherAppId, (LaunchToken) -> Boolean>> = emptyList(),
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
        require(dynamicLaunches.all { it.first in installedApps }) {
            "Dynamic launch matcher belongs to an app outside the catalog"
        }
    }

    override suspend fun resolveLaunch(token: LaunchToken): LaunchResolution {
        launches[token]?.let { return LaunchResolution.Internal(it, token) }
        val matches = dynamicLaunches.filter { (_, matcher) -> runCatching { matcher(token) }.getOrDefault(false) }
        return if (matches.size == 1) {
            LaunchResolution.Internal(matches.single().first, token)
        } else {
            LaunchResolution.Unresolved(token)
        }
    }
}

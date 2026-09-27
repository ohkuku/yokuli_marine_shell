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
    private val builtInApps = catalog.apps.map { it.appId }.toSet()
    private var installedLaunches: Map<LaunchToken, LauncherAppId> = emptyMap()
    private val catalogState = MutableStateFlow(catalog)
    override val catalog: StateFlow<LauncherCatalogSnapshot> = catalogState
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

    /** 中文：宿主为持久化内容实例发布查看入口；不创建新的应用或业务状态。 */
    fun updateCatalog(snapshot: LauncherCatalogSnapshot) {
        require(snapshot.apps.map { it.appId }.toSet() == catalogState.value.apps.map { it.appId }.toSet())
        require(snapshot.entries.all { entry -> launches[entry.launchToken] == entry.appId || installedLaunches[entry.launchToken] == entry.appId ||
            dynamicLaunches.any { (owner, matches) -> owner == entry.appId && matches(entry.launchToken) } })
        catalogState.value = snapshot
    }

    /** 安装服务提交整个目录与入口表；内置身份不可卸载，包入口不能覆盖系统入口。 */
    fun updateInstalledCatalog(snapshot: LauncherCatalogSnapshot, installed: Map<LaunchToken, LauncherAppId>) {
        require(installed.keys.none { it in launches }) { "Installed entry overrides a system app" }
        require(installed.values.none { it in builtInApps }) { "Installed app overrides a system identity" }
        val ids = snapshot.apps.map { it.appId }.toSet()
        require(ids == builtInApps + installed.values.toSet())
        require(snapshot.entries.all { entry ->
            launches[entry.launchToken] == entry.appId || installed[entry.launchToken] == entry.appId ||
                dynamicLaunches.any { (owner, matches) -> owner == entry.appId && matches(entry.launchToken) }
        })
        installedLaunches = installed.toMap()
        catalogState.value = snapshot
    }

    override suspend fun resolveLaunch(token: LaunchToken): LaunchResolution {
        installedLaunches[token]?.let { return LaunchResolution.Internal(it, token) }
        launches[token]?.let { return LaunchResolution.Internal(it, token) }
        val matches = dynamicLaunches.filter { (_, matcher) -> runCatching { matcher(token) }.getOrDefault(false) }
        return if (matches.size == 1) {
            LaunchResolution.Internal(matches.single().first, token)
        } else {
            LaunchResolution.Unresolved(token)
        }
    }
}

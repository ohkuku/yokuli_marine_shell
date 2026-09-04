package com.yokuli.shell.android

import com.yokuli.shell.compose.InternalAppHost
import com.yokuli.shell.compose.InternalAppHostResolver
import com.yokuli.shell.contract.LauncherAppId

class DefaultInternalAppHostResolver(hosts: List<InternalAppHost>) : InternalAppHostResolver {
    private val hostsById = hosts.associateBy { it.appId }

    init {
        require(hostsById.size == hosts.size) { "Duplicate internal app host" }
    }

    override fun hostFor(appId: LauncherAppId): InternalAppHost? = hostsById[appId]
}

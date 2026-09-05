package com.yokuli.shell.compose

import androidx.compose.runtime.Composable
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution

/**
 * A feature is installed once. Catalog entries derive launch registrations; the same binding
 * contributes its app-owned visuals and its one internal host.
 */
class InstalledAppBinding<VisualEnvironment>(
    val catalogContribution: LauncherCatalogContribution,
    val visualContributions: @Composable (VisualEnvironment) -> List<LauncherEntryVisualContribution>,
    val internalAppHost: InternalAppHost,
) {
    init {
        val appId = catalogContribution.app.appId
        require(internalAppHost.appId == appId) { "Internal host must belong to the installed app" }
        require(catalogContribution.entries.isNotEmpty()) { "Installed app must contribute at least one entry" }
        require(catalogContribution.entries.all { it.appId == appId }) {
            "Installed app may contribute only its own entries"
        }
    }

    val launchRegistrations: Map<LaunchToken, LauncherAppId> =
        catalogContribution.entries.associate { it.launchToken to it.appId }
}

class InstalledAppRegistry<VisualEnvironment>(
    val bindings: List<InstalledAppBinding<VisualEnvironment>>,
) {
    init {
        require(bindings.isNotEmpty()) { "Installed app registry must not be empty" }
        require(bindings.map { it.catalogContribution.app.appId }.distinct().size == bindings.size) {
            "Duplicate installed app binding"
        }
        val tokens = bindings.flatMap { it.launchRegistrations.keys }
        require(tokens.distinct().size == tokens.size) { "Duplicate installed LaunchToken" }
    }

    val catalogContributions: List<LauncherCatalogContribution> =
        bindings.map { it.catalogContribution }

    val launchRegistrations: Map<LaunchToken, LauncherAppId> =
        bindings.flatMap { it.launchRegistrations.entries }.associate { it.key to it.value }

    val internalAppHosts: List<InternalAppHost> = bindings.map { it.internalAppHost }

    @Composable
    fun visualContributions(environment: VisualEnvironment): List<LauncherEntryVisualContribution> = buildList {
        bindings.forEach { binding -> addAll(binding.visualContributions(environment)) }
    }
}

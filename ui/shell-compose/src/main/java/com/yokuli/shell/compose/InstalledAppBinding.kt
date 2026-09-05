package com.yokuli.shell.compose

import androidx.compose.runtime.Composable
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution

/**
 * A feature is installed once. Its public entries and internal destinations derive all launch
 * registrations; the same binding contributes its app-owned visuals and its one internal host.
 */
class InstalledAppBinding<VisualEnvironment>(
    val catalogContribution: LauncherCatalogContribution,
    val visualContributions: @Composable (VisualEnvironment) -> List<LauncherEntryVisualContribution>,
    val internalAppHost: InternalAppHost,
    val searchContributions: @Composable (VisualEnvironment, String) -> List<LauncherSearchResultContribution> = { _, _ -> emptyList() },
    val dynamicLaunchTokenMatcher: (LaunchToken) -> Boolean = { false },
) {
    val launchRegistrations: Map<LaunchToken, LauncherAppId>

    init {
        val appId = catalogContribution.app.appId
        require(internalAppHost.appId == appId) { "Internal host must belong to the installed app" }
        require(catalogContribution.entries.isNotEmpty()) { "Installed app must contribute at least one entry" }
        require(catalogContribution.entries.all { it.appId == appId }) {
            "Installed app may contribute only its own entries"
        }
        val tokens = catalogContribution.entries.map { it.launchToken } + catalogContribution.internalLaunchTokens
        // Check the list before converting to a map: associate must never silently hide a duplicate.
        require(tokens.distinct().size == tokens.size) { "Duplicate LaunchToken within installed app" }
        launchRegistrations = tokens.associateWith { appId }
    }
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

    @Composable
    fun searchContributions(environment: VisualEnvironment, query: String): List<LauncherSearchResultContribution> = buildList {
        bindings.forEach { binding -> addAll(binding.searchContributions(environment, query)) }
    }

    val dynamicLaunchTokenMatchers: List<Pair<LauncherAppId, (LaunchToken) -> Boolean>> =
        bindings.map { it.catalogContribution.app.appId to it.dynamicLaunchTokenMatcher }
}

package com.yokuli.shell.engine.catalog

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId

class LauncherCatalog private constructor(val snapshot: LauncherCatalogSnapshot) {
    val apps: List<LauncherAppDescriptor> get() = snapshot.apps
    val entries: List<LauncherEntryDescriptor> get() = snapshot.entries

    fun entry(id: LauncherEntryId): LauncherEntryDescriptor? = entries.firstOrNull { it.entryId == id }

    fun entry(token: LaunchToken): LauncherEntryDescriptor? = entries.firstOrNull { it.launchToken == token }

    fun app(id: LauncherAppId): LauncherAppDescriptor? = apps.firstOrNull { it.appId == id }

    companion object {
        fun compose(
            revision: Long,
            contributions: List<LauncherCatalogContribution>,
        ): LauncherCatalog {
            require(revision >= 0) { "Catalog revision must not be negative" }
            val apps = contributions.map { it.app }
            val entries = contributions.flatMap { it.entries }
            require(apps.map { it.appId }.distinct().size == apps.size) { "Duplicate LauncherAppId" }
            require(entries.map { it.entryId }.distinct().size == entries.size) { "Duplicate LauncherEntryId" }
            require(entries.map { it.launchToken }.distinct().size == entries.size) { "Duplicate LaunchToken" }
            require(entries.all { entry -> apps.any { it.appId == entry.appId } }) {
                "Every launcher entry must belong to a contributed app"
            }
            require(apps.all { app -> entries.any { it.entryId == app.rootEntryId && it.appId == app.appId } }) {
                "Every contributed app must identify one of its entries as root"
            }
            require(entries.all { entry ->
                entry.supportedSizes.isNotEmpty() &&
                    entry.defaultSize in entry.supportedSizes &&
                    entry.supportedSizes.distinct().size == entry.supportedSizes.size
            }) { "Launcher entry sizes must declare a valid deterministic cycle" }
            return LauncherCatalog(LauncherCatalogSnapshot(revision, apps, entries))
        }
    }
}

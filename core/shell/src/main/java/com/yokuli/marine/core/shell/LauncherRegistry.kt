package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.MarineAppDescriptor
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.model.ShellFeatureContribution

/** Compile-time registry assembled by app-shell from installed feature contributions. */
class LauncherRegistry(contributions: List<ShellFeatureContribution>) {
    val apps: List<MarineAppDescriptor> = contributions.map { it.app }
    val entries: List<LauncherEntryDescriptor> = contributions.flatMap { it.launcherEntries }

    init {
        require(apps.map { it.id }.distinct().size == apps.size) { "Duplicate MarineAppId" }
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate LauncherEntryId" }
        require(entries.all { entry -> apps.any { it.id == entry.appId } }) {
            "Every launcher entry must belong to an installed app"
        }
        require(entries.all { entry ->
            entry.defaultSize in entry.supportedSizesInCycleOrder &&
                entry.supportedSizesInCycleOrder.isNotEmpty() &&
                entry.supportedSizesInCycleOrder.distinct().size == entry.supportedSizesInCycleOrder.size
        }) { "Launcher entry sizes must declare a valid deterministic cycle" }
    }

    fun entry(id: LauncherEntryId): LauncherEntryDescriptor? = entries.firstOrNull { it.id == id }
    fun app(id: MarineAppId): MarineAppDescriptor? = apps.firstOrNull { it.id == id }
}

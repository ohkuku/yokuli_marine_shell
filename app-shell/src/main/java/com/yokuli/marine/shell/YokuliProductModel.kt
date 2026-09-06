package com.yokuli.marine.shell

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.engine.LauncherProductMigrationPlan
import com.yokuli.shell.engine.LauncherProductMigrationStep
import com.yokuli.shell.engine.LauncherTokenAlias

/** Stable product identities do not install an app. Production registration remains explicit. */
data class YokuliProductAppIdentity(
    val appId: LauncherAppId,
    val rootEntryId: LauncherEntryId,
    val rootToken: LaunchToken,
)

object YokuliProductModel {
    const val LATEST_MIGRATION_VERSION = 2

    val Chart = identity("chart", "chart", "chart.browse")
    val ChartLibrary = identity("chart_library", "chart_library", "chart_library.browse")
    val Data = identity("data", "data", "data.overview")
    val Navigation = identity("navigation", "navigation", "navigation.overview")
    val Preferences = identity("preferences", "preferences", "preferences.overview")

    val finalApps = listOf(Chart, ChartLibrary, Data, Navigation, Preferences)

    val migrationPlan = LauncherProductMigrationPlan(
        listOf(
            LauncherProductMigrationStep(
                version = 1,
                targetEntryId = Data.rootEntryId,
                legacyEntryIds = setOf(LauncherEntryId("nmea-input"), LauncherEntryId("data-sources")),
                tokenAliases = listOf(
                    LauncherTokenAlias("sources.root", "data.overview"),
                    LauncherTokenAlias("sources.attention", "data.diagnostics"),
                    LauncherTokenAlias("sources.connection.", "data.source.", prefix = true),
                    LauncherTokenAlias("nmea.root", "data.inputs"),
                    LauncherTokenAlias("nmea.connection.", "data.input.", prefix = true),
                ),
            ),
            LauncherProductMigrationStep(
                version = 2,
                targetEntryId = Preferences.rootEntryId,
                legacyEntryIds = setOf(LauncherEntryId("settings")),
                tokenAliases = listOf(
                    LauncherTokenAlias("settings.overview", "preferences.overview"),
                    LauncherTokenAlias("settings.appearance", "preferences.appearance"),
                    LauncherTokenAlias("settings.start", "preferences.start"),
                    LauncherTokenAlias("settings.map", "preferences.chart"),
                    LauncherTokenAlias("settings.language", "preferences.language"),
                    LauncherTokenAlias("settings.about", "preferences.about"),
                ),
            ),
        ),
    )

    private fun identity(app: String, entry: String, token: String) = YokuliProductAppIdentity(
        LauncherAppId(app), LauncherEntryId(entry), LaunchToken(token),
    )
}

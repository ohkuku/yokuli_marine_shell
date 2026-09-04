package com.yokuli.marine.shell

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.yokuli.marine.adapter.chart.google.GoogleMarineChartSurface
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.chart.ChartShellContribution
import com.yokuli.marine.feature.chart.ChartSurfaceKind
import com.yokuli.marine.feature.chart.ChartUiAction
import com.yokuli.marine.feature.chart.ChartUiState
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.MarineChartDemoSurface
import com.yokuli.marine.feature.chart.MarineChartSurface
import com.yokuli.marine.feature.desktop.LauncherEntryUiState
import com.yokuli.marine.feature.desktop.LauncherEntryVisualContribution
import com.yokuli.marine.feature.desktop.MarineIconKind
import com.yokuli.marine.feature.desktop.R as DesktopR
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsSection
import com.yokuli.marine.feature.settings.SettingsShellContribution
import com.yokuli.marine.feature.settings.SettingsUiAction
import com.yokuli.marine.feature.settings.SettingsUiState
import com.yokuli.marine.feature.settings.SettingsWorkspace
import com.yokuli.shell.android.DefaultInternalAppHostResolver
import com.yokuli.shell.android.StaticLauncherHostPort
import com.yokuli.shell.compose.InternalAppHost
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.WpTileSize
import com.yokuli.shell.engine.catalog.LauncherCatalog
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement

data class InstalledAppBinding(
    val catalogContribution: LauncherCatalogContribution,
    val launchRegistrations: Map<LaunchToken, LauncherAppId>,
    val visualContributions: List<LauncherEntryVisualContribution>,
    val internalAppHost: InternalAppHost,
)

data class ProductionShellRuntime(
    val mapConfigured: Boolean,
    val theme: WpThemeSpec,
    val language: AppLanguage,
    val settingsSection: SettingsSection,
    val pinnedTileCount: Int,
    val startDocumentVersion: Int,
    val versionName: String,
    val buildVariant: String,
    val gitSha: String,
    val debugShellLabAvailable: Boolean,
    val openMapSettings: () -> Unit,
    val onSettingsAction: (SettingsUiAction) -> Unit,
)

val LocalProductionShellRuntime = staticCompositionLocalOf<ProductionShellRuntime> {
    error("Production shell runtime was not provided")
}

/**
 * 中文：生产应用只在这里注册一次，目录、LaunchToken、视觉和内部宿主均从该绑定派生。
 * English: Production apps register once here; catalog, launch tokens, visuals, and hosts derive from it.
 */
val productionInstalledApps = listOf(
    InstalledAppBinding(
        catalogContribution = ChartShellContribution,
        launchRegistrations = mapOf(ChartDestinations.Browse to ChartDestinations.AppId),
        visualContributions = listOf(
            LauncherEntryVisualContribution(ChartDestinations.EntryId) { descriptor, visual ->
                LauncherEntryUiState(
                    descriptor = descriptor,
                    title = stringResource(DesktopR.string.launcher_chart),
                    chineseIndex = 'H',
                    icon = MarineIconKind.CHART,
                    headline = stringResource(
                        if (visual.mapConfigured) DesktopR.string.tile_chart_configured
                        else DesktopR.string.tile_chart_demo,
                    ),
                    detail = stringResource(
                        if (visual.mapConfigured) DesktopR.string.tile_chart_browse_only
                        else DesktopR.string.tile_chart_unconfigured,
                    ),
                )
            },
        ),
        internalAppHost = InternalAppHost(ChartDestinations.AppId) { token ->
            check(token == ChartDestinations.Browse)
            val runtime = LocalProductionShellRuntime.current
            val chartSurface: MarineChartSurface = if (runtime.mapConfigured) {
                { modifier ->
                    GoogleMarineChartSurface(
                        darkMode = runtime.theme.mode == WpThemeMode.DARK,
                        modifier = modifier.testTag("chart-surface-google"),
                    )
                }
            } else {
                { modifier -> MarineChartDemoSurface(modifier.testTag("chart-surface-demo")) }
            }
            ChartWorkspace(
                state = ChartUiState(
                    surfaceKind = if (runtime.mapConfigured) ChartSurfaceKind.GOOGLE_MAPS else ChartSurfaceKind.DEMO,
                    mapConfigured = runtime.mapConfigured,
                ),
                onAction = { action ->
                    if (action == ChartUiAction.OpenMapSettings) runtime.openMapSettings()
                },
                chartSurface = chartSurface,
            )
        },
    ),
    InstalledAppBinding(
        catalogContribution = SettingsShellContribution,
        launchRegistrations = SettingsSection.entries.associate {
            SettingsDestinations.token(it) to SettingsDestinations.AppId
        },
        visualContributions = listOf(
            LauncherEntryVisualContribution(SettingsDestinations.EntryId) { descriptor, visual ->
                LauncherEntryUiState(
                    descriptor = descriptor,
                    title = stringResource(DesktopR.string.launcher_settings),
                    chineseIndex = 'S',
                    icon = MarineIconKind.SETTINGS,
                    headline = stringResource(
                        if (visual.theme.mode == WpThemeMode.DARK) DesktopR.string.tile_settings_dark
                        else DesktopR.string.tile_settings_light,
                    ),
                    detail = stringResource(DesktopR.string.tile_settings_accent, visual.theme.accent.displayName),
                )
            },
        ),
        internalAppHost = InternalAppHost(SettingsDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            val tokenSection = SettingsDestinations.section(token)
                ?: error("Unknown Settings launch token: ${token.value}")
            val section = runtime.settingsSection.let { remembered ->
                if (remembered == SettingsSection.OVERVIEW) tokenSection else remembered
            }
            SettingsWorkspace(
                state = SettingsUiState(
                    section = section,
                    theme = runtime.theme,
                    language = runtime.language,
                    mapConfigured = runtime.mapConfigured,
                    pinnedTileCount = runtime.pinnedTileCount,
                    startDocumentVersion = runtime.startDocumentVersion,
                    versionName = runtime.versionName,
                    buildVariant = runtime.buildVariant,
                    gitSha = runtime.gitSha,
                    debugShellLabAvailable = runtime.debugShellLabAvailable,
                ),
                onAction = runtime.onSettingsAction,
            )
        },
    ),
)

val productionContributions = productionInstalledApps.map { it.catalogContribution }
val productionCatalog = LauncherCatalog.compose(revision = 1, contributions = productionContributions)
val productionLaunchRegistrations = productionInstalledApps.flatMap { it.launchRegistrations.entries }.let { entries ->
    entries.associate { it.key to it.value }.also { registrations ->
        require(registrations.size == entries.size) { "Duplicate production LaunchToken registration" }
    }
}
val productionVisualContributions = productionInstalledApps.flatMap { it.visualContributions }
val productionInternalAppHostResolver = DefaultInternalAppHostResolver(
    productionInstalledApps.map { it.internalAppHost },
)

val productionHostPort = StaticLauncherHostPort(
    catalog = productionCatalog.snapshot,
    launches = productionLaunchRegistrations,
)

val defaultStartDocument = StartDocument(
    schemaVersion = 1,
    profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
    defaultLayoutVersion = 1,
    placements = listOf(
        TilePlacement(
            tileId = TileInstanceId("tile-chart"),
            entryId = ChartDestinations.EntryId,
            size = WpTileSize.WIDE_4X2,
            cell = GridCell(column = 0, row = 0),
        ),
        TilePlacement(
            tileId = TileInstanceId("tile-settings"),
            entryId = SettingsDestinations.EntryId,
            size = WpTileSize.SMALL_1X1,
            cell = GridCell(column = 0, row = 2),
        ),
    ),
)

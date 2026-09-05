package com.yokuli.marine.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.testTag
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
import com.yokuli.marine.feature.chart.MarineChartTransitionSurface
import com.yokuli.marine.feature.chart.chartLauncherVisualContribution
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsSection
import com.yokuli.marine.feature.settings.SettingsShellContribution
import com.yokuli.marine.feature.settings.SettingsUiAction
import com.yokuli.marine.feature.settings.SettingsUiState
import com.yokuli.marine.feature.settings.SettingsWorkspace
import com.yokuli.marine.feature.settings.settingsLauncherVisualContribution
import com.yokuli.shell.android.DefaultInternalAppHostResolver
import com.yokuli.shell.android.StaticLauncherHostPort
import com.yokuli.shell.compose.InternalAppHost
import com.yokuli.shell.compose.InstalledAppBinding
import com.yokuli.shell.compose.InstalledAppRegistry
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherCatalogContribution
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.catalog.LauncherCatalog
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement

data class ProductionShellVisualEnvironment(
    val mapConfigured: Boolean,
    val theme: WpThemeSpec,
)

data class ProductionShellRuntime(
    val mapConfigured: Boolean,
    val theme: WpThemeSpec,
    val language: AppLanguage,
    val heavyContentReady: Boolean,
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
val productionInstalledApps: List<InstalledAppBinding<ProductionShellVisualEnvironment>> = listOf(
    InstalledAppBinding(
        catalogContribution = ChartShellContribution,
        visualContributions = { environment ->
            listOf(chartLauncherVisualContribution(environment.mapConfigured))
        },
        internalAppHost = InternalAppHost(ChartDestinations.AppId) { token ->
            check(token == ChartDestinations.Browse)
            val runtime = LocalProductionShellRuntime.current
            val chartSurface: MarineChartSurface = if (runtime.mapConfigured && runtime.heavyContentReady) {
                { modifier ->
                    GoogleMarineChartSurface(
                        darkMode = runtime.theme.mode == WpThemeMode.DARK,
                        modifier = modifier.testTag("chart-surface-google"),
                    )
                }
            } else if (runtime.mapConfigured) {
                { modifier -> MarineChartTransitionSurface(modifier) }
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
        visualContributions = { environment ->
            listOf(settingsLauncherVisualContribution(environment.theme))
        },
        internalAppHost = InternalAppHost(SettingsDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            val tokenSection = SettingsDestinations.section(token)
                ?: error("Unknown Settings launch token: ${token.value}")
            SettingsWorkspace(
                state = SettingsUiState(
                    section = tokenSection,
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

val productionInstalledAppRegistry: InstalledAppRegistry<ProductionShellVisualEnvironment> =
    InstalledAppRegistry(productionInstalledApps)
val productionContributions = productionInstalledAppRegistry.catalogContributions
val productionCatalog = LauncherCatalog.compose(revision = 1, contributions = productionContributions)
val productionLaunchRegistrations = productionInstalledAppRegistry.launchRegistrations
@Composable
fun productionVisualContributions(
    mapConfigured: Boolean,
    theme: WpThemeSpec,
): List<LauncherEntryVisualContribution> {
    val environment = ProductionShellVisualEnvironment(mapConfigured, theme)
    return productionInstalledAppRegistry.visualContributions(environment)
}
val productionInternalAppHostResolver = DefaultInternalAppHostResolver(
    productionInstalledAppRegistry.internalAppHosts,
)

val productionHostPort = StaticLauncherHostPort(
    catalog = productionCatalog.snapshot,
    launches = productionLaunchRegistrations,
)

val defaultStartDocument = StartDocument(
    schemaVersion = 2,
    profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
    defaultLayoutVersion = 2,
    placements = listOf(
        TilePlacement(
            tileId = TileInstanceId("tile-chart"),
            entryId = ChartDestinations.EntryId,
            size = MarineTileSize.WIDE_4X2,
            rank = 0L,
        ),
        TilePlacement(
            tileId = TileInstanceId("tile-settings"),
            entryId = SettingsDestinations.EntryId,
            size = MarineTileSize.ICON_1X1,
            rank = 1024L,
        ),
    ),
)

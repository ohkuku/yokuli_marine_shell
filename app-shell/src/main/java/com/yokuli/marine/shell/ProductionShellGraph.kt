package com.yokuli.marine.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import com.yokuli.marine.adapter.chart.google.GoogleMarineChartSurface
import com.yokuli.marine.map.offline.OfflineMarineChartSurface
import com.yokuli.marine.map.offline.ChartLoopbackTileGateway
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapViewportInsets
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.chart.ChartShellContribution
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.ChartDisplayUiAction
import com.yokuli.marine.feature.chart.ChartDisplayUiState
import com.yokuli.marine.feature.chart.MarineChartSurface
import com.yokuli.marine.feature.chart.MarineChartTransitionSurface
import com.yokuli.marine.feature.chart.MapPlaceExportUiState
import com.yokuli.marine.feature.chart.MapRecoveryExportUiState
import com.yokuli.marine.feature.chart.OfflineCoverageUiState
import com.yokuli.marine.feature.chart.GpxExportTarget
import com.yokuli.marine.feature.chart.GpxExportUiState
import com.yokuli.marine.feature.chart.GpxImportUiAction
import com.yokuli.marine.feature.chart.GpxImportUiState
import com.yokuli.marine.feature.chart.chartLauncherVisualContribution
import com.yokuli.marine.feature.chart.chartLauncherSearchContributions
import com.yokuli.marine.feature.chart.ChartLaunchProjector
import com.yokuli.marine.feature.chart.ChartDestination
import com.yokuli.marine.feature.chartlibrary.ChartLibraryDestinations
import com.yokuli.marine.feature.chartlibrary.ChartLibraryShellContribution
import com.yokuli.marine.feature.chartlibrary.ChartLibraryUiAction
import com.yokuli.marine.feature.chartlibrary.ChartLibraryUiState
import com.yokuli.marine.feature.chartlibrary.ChartLibraryWorkspace
import com.yokuli.marine.feature.chartlibrary.chartLibraryLauncherVisualContribution
import com.yokuli.marine.feature.chartlibrary.chartLibrarySearchContributions
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsSection
import com.yokuli.marine.feature.settings.SettingsShellContribution
import com.yokuli.marine.feature.settings.SettingsUiAction
import com.yokuli.marine.feature.settings.SettingsUiState
import com.yokuli.marine.feature.settings.SettingsWorkspace
import com.yokuli.marine.feature.settings.settingsLauncherVisualContribution
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineFeatureLinkToken
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.feature.data.DataDestination
import com.yokuli.marine.feature.data.DataDestinations
import com.yokuli.marine.feature.data.DataShellContribution
import com.yokuli.marine.feature.data.DataUiAction
import com.yokuli.marine.feature.data.DataUiState
import com.yokuli.marine.feature.data.DataWorkspace
import com.yokuli.marine.feature.data.dataLauncherVisualContribution
import com.yokuli.marine.feature.nmeainput.NmeaInputUiAction
import com.yokuli.marine.feature.nmeainput.NmeaInputUiState
import com.yokuli.marine.feature.nmeainput.NmeaInputWorkspace
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
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort

data class ProductionShellVisualEnvironment(
    val theme: WpThemeSpec,
    val mapState: MapState,
    val offlineCoverageState: OfflineCoverageUiState,
    val nmeaSnapshot: NmeaRuntimeSnapshot = NmeaRuntimeSnapshot.EMPTY,
    val dataSourcesSnapshot: MarineSourceSnapshot = MarineSourceSnapshot.EMPTY,
    val chartLibraryState: ChartLibraryUiState = ChartLibraryUiState(),
)

data class ProductionShellRuntime(
    val theme: WpThemeSpec,
    val language: AppLanguage,
    val heavyContentReady: Boolean,
    val pinnedTileCount: Int,
    val startDocumentVersion: Int,
    val versionName: String,
    val buildVariant: String,
    val gitSha: String,
    val debugShellLabAvailable: Boolean,
    val mapState: MapState,
    val currentMapState: () -> MapState,
    val mapShellSafeInsets: MapViewportInsets,
    val onMapAction: (MapAction) -> Unit,
    val chartDisplayState: ChartDisplayUiState,
    val onChartDisplayAction: (ChartDisplayUiAction) -> Unit,
    val acquireChartPackageLease: (ChartPackageId) -> ChartPackageLease,
    val chartLibraryAccess: ChartResourceAccessPort,
    val chartTileGateway: ChartLoopbackTileGateway,
    val recoveryExportState: MapRecoveryExportUiState,
    val onExportMapRecovery: () -> Unit,
    val placeExportState: MapPlaceExportUiState,
    val onExportPlace: (SavedPlace) -> Unit,
    val gpxImportState: GpxImportUiState,
    val onGpxImportAction: (GpxImportUiAction) -> Unit,
    val gpxExportState: GpxExportUiState,
    val onSaveGpx: (GpxExportTarget) -> Unit,
    val onShareGpx: (GpxExportTarget) -> Unit,
    val offlineCoverageState: OfflineCoverageUiState,
    val onStartOfflineCoverage: (routeId: String, targetZoom: Int, halfWidthNauticalMiles: Double) -> Unit,
    val onCancelOfflineCoverage: () -> Unit,
    val onSettingsAction: (SettingsUiAction) -> Unit,
    val dataState: DataUiState,
    val onDataAction: (DataUiAction) -> Unit,
    val onOpenData: (LaunchToken) -> Unit,
    /** Proven connection editor retained as an internal Data subflow, not an installed App. */
    val nmeaInputState: NmeaInputUiState,
    val onNmeaInputAction: (NmeaInputUiAction) -> Unit,
    val chartLibraryState: ChartLibraryUiState,
    val onChartLibraryAction: (ChartLibraryUiAction) -> Unit,
    val onOpenChartLibrary: (LaunchToken) -> Unit,
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
            listOf(chartLauncherVisualContribution(environment.mapState, environment.offlineCoverageState))
        },
        searchContributions = { environment, query ->
            chartLauncherSearchContributions(environment.mapState, query)
        },
        dynamicLaunchTokenMatcher = ChartDestinations::accepts,
        internalAppHost = InternalAppHost(ChartDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            val target = remember(token) { requireNotNull(ChartDestinations.parse(token)) }
            LaunchedEffect(token) {
                if (target is ChartDestination.ChartAsset) {
                    runtime.onChartDisplayAction(ChartDisplayUiAction.PinAsset(target.id))
                }
                ChartLaunchProjector.action(target, runtime.currentMapState())?.let(runtime.onMapAction)
            }
            val chartSurface: MarineChartSurface = remember(runtime.heavyContentReady) {
                if (runtime.heavyContentReady) {
                    { state, onAction, onQueryPortChanged, modifier ->
                        if (
                            state.chartDisplayPlan.selection is ChartDisplaySelection.None &&
                            BuildConfig.GOOGLE_MAPS_CONFIGURED
                        ) {
                            GoogleMarineChartSurface(
                                state = state,
                                onAction = onAction,
                                onQueryPortChanged = onQueryPortChanged,
                                darkMode = runtime.theme.mode == WpThemeMode.DARK,
                                modifier = modifier.testTag("chart-surface-google"),
                            )
                        } else {
                            OfflineMarineChartSurface(
                                state = state,
                                onAction = onAction,
                                onQueryPortChanged = onQueryPortChanged,
                                acquirePackageLease = runtime.acquireChartPackageLease,
                                chartLibraryAccess = runtime.chartLibraryAccess,
                                chartTileGateway = runtime.chartTileGateway,
                                modifier = modifier.testTag("chart-surface-maplibre"),
                            )
                        }
                    }
                } else {
                    { _, _, _, modifier -> MarineChartTransitionSurface(modifier) }
                }
            }
            if (!ChartLaunchProjector.isSettled(target, runtime.mapState)) {
                MarineChartTransitionSurface(Modifier.fillMaxSize())
                return@InternalAppHost
            }
            ChartWorkspace(
                state = runtime.mapState,
                onAction = runtime.onMapAction,
                currentState = runtime.currentMapState,
                shellSafeInsets = runtime.mapShellSafeInsets,
                chartDisplayState = runtime.chartDisplayState,
                onChartDisplayAction = runtime.onChartDisplayAction,
                recoveryExportState = runtime.recoveryExportState,
                onExportRecovery = runtime.onExportMapRecovery,
                placeExportState = runtime.placeExportState,
                onExportPlace = runtime.onExportPlace,
                gpxImportState = runtime.gpxImportState,
                onGpxImportAction = runtime.onGpxImportAction,
                gpxExportState = runtime.gpxExportState,
                onSaveGpx = runtime.onSaveGpx,
                onShareGpx = runtime.onShareGpx,
                offlineCoverageState = runtime.offlineCoverageState,
                onStartOfflineCoverage = runtime.onStartOfflineCoverage,
                onCancelOfflineCoverage = runtime.onCancelOfflineCoverage,
                onOpenChartLibrary = {
                    runtime.onOpenChartLibrary(ChartLibraryDestinations.Browse)
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
                    chartPackageCount = runtime.mapState.chartPackages.size,
                    activeChartPackageName = runtime.mapState.chartPackages
                        .firstOrNull { it.id == runtime.mapState.activeChartPackageId }
                        ?.displayName,
                    pinnedTileCount = runtime.pinnedTileCount,
                    startDocumentVersion = runtime.startDocumentVersion,
                    versionName = runtime.versionName,
                    buildVariant = runtime.buildVariant,
                    gitSha = runtime.gitSha,
                    // Daily Debug APKs are product builds too. Lab is only an explicit instrumentation/tooling target.
                    debugShellLabAvailable = false,
                ),
                onAction = runtime.onSettingsAction,
            )
        },
    ),
    InstalledAppBinding(
        catalogContribution = DataShellContribution,
        visualContributions = { environment ->
            listOf(dataLauncherVisualContribution(environment.nmeaSnapshot, environment.dataSourcesSnapshot))
        },
        dynamicLaunchTokenMatcher = DataDestinations::accepts,
        internalAppHost = InternalAppHost(DataDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            LaunchedEffect(token) {
                runtime.onOpenData(token)
            }
            DataWorkspace(runtime.dataState, runtime.onDataAction) {
                NmeaInputWorkspace(
                    state = runtime.nmeaInputState,
                    onAction = runtime.onNmeaInputAction,
                    embedded = true,
                    onExitEmbedded = { runtime.onDataAction(DataUiAction.Navigate(com.yokuli.marine.feature.data.DataSection.OVERVIEW)) },
                )
            }
        },
    ),
    InstalledAppBinding(
        catalogContribution = ChartLibraryShellContribution,
        visualContributions = { environment ->
            listOf(chartLibraryLauncherVisualContribution(environment.chartLibraryState))
        },
        searchContributions = { environment, query ->
            chartLibrarySearchContributions(environment.chartLibraryState, query)
        },
        dynamicLaunchTokenMatcher = ChartLibraryDestinations::accepts,
        internalAppHost = InternalAppHost(ChartLibraryDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            val destination = remember(token) { requireNotNull(ChartLibraryDestinations.parse(token)) }
            LaunchedEffect(token) { runtime.onOpenChartLibrary(token) }
            ChartLibraryWorkspace(runtime.chartLibraryState, runtime.onChartLibraryAction)
        },
    ),
)

val productionInstalledAppRegistry: InstalledAppRegistry<ProductionShellVisualEnvironment> =
    InstalledAppRegistry(productionInstalledApps)
val productionContributions = productionInstalledAppRegistry.catalogContributions
val productionCatalog = LauncherCatalog.compose(revision = 4, contributions = productionContributions)
val productionLaunchRegistrations = productionInstalledAppRegistry.launchRegistrations
@Composable
fun productionVisualContributions(
    theme: WpThemeSpec,
    mapState: MapState = MapState(),
    offlineCoverageState: OfflineCoverageUiState = OfflineCoverageUiState.Idle,
    nmeaSnapshot: NmeaRuntimeSnapshot = NmeaRuntimeSnapshot.EMPTY,
    dataSourcesSnapshot: MarineSourceSnapshot = MarineSourceSnapshot.EMPTY,
    chartLibraryState: ChartLibraryUiState = ChartLibraryUiState(),
): List<LauncherEntryVisualContribution> {
    val environment = ProductionShellVisualEnvironment(
        theme,
        mapState,
        offlineCoverageState,
        nmeaSnapshot,
        dataSourcesSnapshot,
        chartLibraryState,
    )
    return productionInstalledAppRegistry.visualContributions(environment)
}
@Composable
fun productionSearchContributions(
    theme: WpThemeSpec,
    mapState: MapState,
    offlineCoverageState: OfflineCoverageUiState,
    nmeaSnapshot: NmeaRuntimeSnapshot = NmeaRuntimeSnapshot.EMPTY,
    dataSourcesSnapshot: MarineSourceSnapshot = MarineSourceSnapshot.EMPTY,
    chartLibraryState: ChartLibraryUiState = ChartLibraryUiState(),
    query: String,
) = productionInstalledAppRegistry.searchContributions(
    ProductionShellVisualEnvironment(
        theme,
        mapState,
        offlineCoverageState,
        nmeaSnapshot,
        dataSourcesSnapshot,
        chartLibraryState,
    ),
    query,
)
val productionInternalAppHostResolver = DefaultInternalAppHostResolver(
    productionInstalledAppRegistry.internalAppHosts,
)

val productionHostPort = StaticLauncherHostPort(
    catalog = productionCatalog.snapshot,
    launches = productionLaunchRegistrations,
    dynamicLaunches = productionInstalledAppRegistry.dynamicLaunchTokenMatchers,
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

package com.yokuli.marine.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import com.yokuli.marine.adapter.chart.google.GoogleMarineChartSurface
import com.yokuli.marine.map.offline.OfflineMarineChartSurface
import com.yokuli.marine.map.offline.ChartLoopbackTileGateway
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.BoundedMapTileSnapshotRuntime
import com.yokuli.marine.map.domain.MapReducer
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapViewMode
import com.yokuli.marine.map.domain.MapTileSnapshot
import com.yokuli.marine.map.domain.MapTileSnapshotSink
import com.yokuli.marine.map.domain.MapViewportInsets
import com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle
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
import com.yokuli.marine.feature.chart.UnsavedRouteDecision
import com.yokuli.marine.feature.chart.GpxExportTarget
import com.yokuli.marine.feature.chart.GpxExportUiState
import com.yokuli.marine.feature.chart.GpxImportUiAction
import com.yokuli.marine.feature.chart.GpxImportUiState
import com.yokuli.marine.feature.chart.chartLauncherVisualContribution
import com.yokuli.marine.feature.chart.ChartLaunchProjector
import com.yokuli.marine.feature.chart.ChartDestination
import com.yokuli.marine.feature.chartlibrary.ChartLibraryDestinations
import com.yokuli.marine.feature.chartlibrary.ChartLibraryShellContribution
import com.yokuli.marine.feature.chartlibrary.ChartLibraryUiAction
import com.yokuli.marine.feature.chartlibrary.ChartLibraryUiState
import com.yokuli.marine.feature.chartlibrary.ChartLibraryWorkspace
import com.yokuli.marine.feature.chartlibrary.chartLibraryLauncherVisualContribution
import com.yokuli.marine.feature.chartlibrary.chartLibrarySearchContributions
import com.yokuli.marine.feature.preferences.AppTilePreferenceUi
import com.yokuli.marine.feature.preferences.AppTilePreferenceItemUi
import com.yokuli.marine.feature.preferences.PreferencesDestinations
import com.yokuli.marine.feature.preferences.PreferencesShellContribution
import com.yokuli.marine.feature.preferences.PreferencesUiAction
import com.yokuli.marine.feature.preferences.PreferencesUiState
import com.yokuli.marine.feature.preferences.PreferencesWorkspace
import com.yokuli.marine.feature.preferences.preferencesLauncherVisualContribution
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
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import com.yokuli.marine.navigation.domain.ActiveNavigationCommand
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.feature.navigation.ActiveNavigationStrip
import com.yokuli.marine.feature.navigation.NavigationDestinations
import com.yokuli.marine.feature.navigation.NavigationShellContribution
import com.yokuli.marine.feature.navigation.NavigationGpxBounds
import com.yokuli.marine.feature.navigation.NavigationGpxFailure
import com.yokuli.marine.feature.navigation.NavigationGpxItem
import com.yokuli.marine.feature.navigation.NavigationGpxItemKind
import com.yokuli.marine.feature.navigation.NavigationGpxUiAction
import com.yokuli.marine.feature.navigation.NavigationGpxUiState
import com.yokuli.marine.feature.navigation.NavigationGpxWarning
import com.yokuli.marine.feature.navigation.NavigationUiAction
import com.yokuli.marine.feature.navigation.NavigationUiState
import com.yokuli.marine.feature.navigation.NavigationWorkspace
import com.yokuli.marine.feature.navigation.navigationLauncherVisualContribution
import com.yokuli.marine.feature.navigation.navigationSearchContributions

data class ProductionShellVisualEnvironment(
    val theme: WpThemeSpec,
    val mapState: MapState,
    val offlineCoverageState: OfflineCoverageUiState,
    val nmeaSnapshot: NmeaRuntimeSnapshot = NmeaRuntimeSnapshot.EMPTY,
    val dataSourcesSnapshot: MarineSourceSnapshot = MarineSourceSnapshot.EMPTY,
    val chartLibraryState: ChartLibraryUiState = ChartLibraryUiState(),
    val navigationState: NavigationUiState = NavigationUiState(),
    val mapTileSnapshot: MapTileSnapshot? = null,
    val chartTileMode: com.yokuli.marine.feature.chart.ChartTileMode = com.yokuli.marine.feature.chart.ChartTileMode.AUTO,
)

data class ProductionShellRuntime(
    val theme: WpThemeSpec,
    val heavyContentReady: Boolean,
    val mapState: MapState,
    val currentMapState: () -> MapState,
    val mapShellSafeInsets: MapViewportInsets,
    val onMapAction: (MapAction) -> Unit,
    val chartDisplayState: ChartDisplayUiState,
    val onChartDisplayAction: (ChartDisplayUiAction) -> Unit,
    val acquireChartPackageLease: (ChartPackageId) -> ChartPackageLease,
    val chartLibraryAccess: ChartResourceAccessPort,
    val chartTileGateway: ChartLoopbackTileGateway,
    val mapTileSnapshotSink: MapTileSnapshotSink,
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
    val activeNavigationState: ActiveNavigationSnapshot,
    val onActiveNavigationCommand: (ActiveNavigationCommand) -> Unit,
    val onDirectTo: (point: com.yokuli.marine.map.domain.GeoPoint, name: String) -> Unit,
    val onUnsavedRouteDecision: (UnsavedRouteDecision) -> Unit,
    val onSaveAndStartRoute: () -> Unit,
    val preferencesState: PreferencesUiState,
    val onPreferencesAction: (PreferencesUiAction) -> Unit,
    val dataState: DataUiState,
    val onDataAction: (DataUiAction) -> Unit,
    val onOpenData: (LaunchToken) -> Unit,
    /** Proven connection editor retained as an internal Data subflow, not an installed App. */
    val nmeaInputState: NmeaInputUiState,
    val onNmeaInputAction: (NmeaInputUiAction) -> Unit,
    val chartLibraryState: ChartLibraryUiState,
    val onChartLibraryAction: (ChartLibraryUiAction) -> Unit,
    val onOpenChartLibrary: (LaunchToken) -> Unit,
    val navigationState: NavigationUiState,
    val onNavigationAction: (NavigationUiAction) -> Unit,
    val onOpenNavigation: (LaunchToken) -> Unit,
)

val LocalProductionShellRuntime = staticCompositionLocalOf<ProductionShellRuntime> {
    error("Production shell runtime was not provided")
}

internal enum class ChartSurfaceKind { GOOGLE, OFFLINE }

internal fun chartSurfaceKind(mapViewMode: MapViewMode, googleMapsConfigured: Boolean): ChartSurfaceKind =
    if (mapViewMode != MapViewMode.MARINE && googleMapsConfigured) {
        ChartSurfaceKind.GOOGLE
    } else {
        ChartSurfaceKind.OFFLINE
    }

private fun chartMapMode(style: ChartBuiltInBaseStyle): MapViewMode = when (style) {
    ChartBuiltInBaseStyle.NONE -> MapViewMode.MARINE
    ChartBuiltInBaseStyle.STANDARD -> MapViewMode.STANDARD
    ChartBuiltInBaseStyle.SATELLITE -> MapViewMode.SATELLITE
}

@Composable
private fun rememberProductionChartSurface(
    runtime: ProductionShellRuntime,
    snapshotSink: MapTileSnapshotSink = runtime.mapTileSnapshotSink,
): MarineChartSurface = remember(runtime.heavyContentReady, snapshotSink) {
    if (runtime.heavyContentReady) {
        { state, onAction, onQueryPortChanged, modifier ->
            if (chartSurfaceKind(state.mapViewMode, BuildConfig.GOOGLE_MAPS_CONFIGURED) == ChartSurfaceKind.GOOGLE) {
                GoogleMarineChartSurface(
                    state = state,
                    onAction = onAction,
                    onQueryPortChanged = onQueryPortChanged,
                    darkMode = runtime.theme.mode == WpThemeMode.DARK,
                    chartLibraryAccess = runtime.chartLibraryAccess,
                    tileSnapshotSink = snapshotSink,
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
                    tileSnapshotSink = snapshotSink,
                    modifier = modifier.testTag("chart-surface-maplibre"),
                )
            }
        }
    } else {
        { _, _, _, modifier -> MarineChartTransitionSurface(modifier) }
    }
}

private fun GpxImportUiState.toNavigationGpxState(): NavigationGpxUiState = when (this) {
    GpxImportUiState.Idle -> NavigationGpxUiState.Idle
    is GpxImportUiState.Inspecting -> NavigationGpxUiState.Inspecting
    is GpxImportUiState.Preview -> NavigationGpxUiState.Preview(
        items = buildList {
            preview.waypoints.forEachIndexed { index, item ->
                add(
                    NavigationGpxItem(
                        index,
                        NavigationGpxItemKind.WAYPOINT,
                        item.name,
                        index in selection.waypointIndices,
                    ),
                )
            }
            preview.routes.forEachIndexed { index, item ->
                add(
                    NavigationGpxItem(
                        index,
                        NavigationGpxItemKind.ROUTE,
                        item.name,
                        index in selection.routeIndices,
                        pointCount = item.points.size,
                    ),
                )
            }
            preview.tracks.forEachIndexed { index, item ->
                add(
                    NavigationGpxItem(
                        index,
                        NavigationGpxItemKind.TRACK,
                        item.name,
                        index in selection.trackIndices,
                        pointCount = item.segments.sumOf { it.points.size },
                        segmentCount = item.segments.size,
                    ),
                )
            }
        },
        totalPointCount = preview.totalPointCount,
        bounds = preview.bounds?.let { NavigationGpxBounds(it.south, it.west, it.north, it.east) },
        duplicate = preview.duplicate,
        warnings = preview.warnings.mapTo(linkedSetOf()) {
            when (it) {
                com.yokuli.marine.map.domain.GpxWarning.ROUTE_HAS_FEWER_THAN_TWO_POINTS -> NavigationGpxWarning.SHORT_ROUTE
                com.yokuli.marine.map.domain.GpxWarning.UNKNOWN_EXTENSIONS_NOT_PRESERVED -> NavigationGpxWarning.UNKNOWN_EXTENSIONS
                com.yokuli.marine.map.domain.GpxWarning.INVALID_OPTIONAL_TIME_OMITTED -> NavigationGpxWarning.INVALID_OPTIONAL_TIME
                com.yokuli.marine.map.domain.GpxWarning.EMPTY_TRACK_OMITTED -> NavigationGpxWarning.EMPTY_TRACK
            }
        },
        canImport = canImport,
    )
    is GpxImportUiState.Writing -> NavigationGpxUiState.Writing
    is GpxImportUiState.Succeeded -> NavigationGpxUiState.Succeeded(placeCount, routeCount, trackCount)
    is GpxImportUiState.Cancelled -> NavigationGpxUiState.Cancelled
    is GpxImportUiState.Failed -> NavigationGpxUiState.Failed(
        when (reason) {
            com.yokuli.marine.feature.chart.GpxImportFailure.INVALID_DOCUMENT -> NavigationGpxFailure.INVALID_DOCUMENT
            com.yokuli.marine.feature.chart.GpxImportFailure.EMPTY_SELECTION -> NavigationGpxFailure.EMPTY_SELECTION
            com.yokuli.marine.feature.chart.GpxImportFailure.DISPATCH_REJECTED -> NavigationGpxFailure.QUEUE_BUSY
            com.yokuli.marine.feature.chart.GpxImportFailure.WRITE_FAILED -> NavigationGpxFailure.WRITE_FAILED
        },
    )
}

private fun NavigationGpxUiAction.toDocumentAction(): GpxImportUiAction = when (this) {
    NavigationGpxUiAction.ChooseDocument -> GpxImportUiAction.ChooseDocument
    is NavigationGpxUiAction.ToggleItem -> when (kind) {
        NavigationGpxItemKind.WAYPOINT -> GpxImportUiAction.ToggleWaypoint(index)
        NavigationGpxItemKind.ROUTE -> GpxImportUiAction.ToggleRoute(index)
        NavigationGpxItemKind.TRACK -> GpxImportUiAction.ToggleTrack(index)
    }
    NavigationGpxUiAction.ConfirmImport -> GpxImportUiAction.ConfirmImport
    NavigationGpxUiAction.ImportAsCopy -> GpxImportUiAction.ImportAsCopy
    NavigationGpxUiAction.Cancel -> GpxImportUiAction.Cancel
    NavigationGpxUiAction.DismissResult -> GpxImportUiAction.DismissResult
}

/**
 * 中文：生产应用只在这里注册一次，目录、LaunchToken、视觉和内部宿主均从该绑定派生。
 * English: Production apps register once here; catalog, launch tokens, visuals, and hosts derive from it.
 */
val productionInstalledApps: List<InstalledAppBinding<ProductionShellVisualEnvironment>> = listOf(
    InstalledAppBinding(
        catalogContribution = ChartShellContribution,
        visualContributions = { environment ->
            listOf(
                chartLauncherVisualContribution(
                    environment.mapState,
                    environment.offlineCoverageState,
                    environment.navigationState.active,
                    environment.mapTileSnapshot,
                    environment.chartTileMode,
                ),
            )
        },
        dynamicLaunchTokenMatcher = ChartDestinations::accepts,
        internalAppHost = InternalAppHost(ChartDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            val target = remember(token) { requireNotNull(ChartDestinations.parse(token)) }
            LaunchedEffect(token) {
                ChartLaunchProjector.action(target, runtime.currentMapState())?.let(runtime.onMapAction)
            }
            val chartSurface = rememberProductionChartSurface(runtime)
            if (!ChartLaunchProjector.isSettled(target, runtime.mapState)) {
                MarineChartTransitionSurface(Modifier.fillMaxSize())
                return@InternalAppHost
            }
            ChartWorkspace(
                state = runtime.mapState,
                onAction = runtime.onMapAction,
                currentState = runtime.currentMapState,
                shellSafeInsets = runtime.mapShellSafeInsets,
                connectedBaseConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
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
                activeNavigationStrip = if (runtime.activeNavigationState.session == null) null else {
                    {
                        ActiveNavigationStrip(
                            snapshot = runtime.activeNavigationState,
                            onCommand = runtime.onActiveNavigationCommand,
                        )
                    }
                },
                onDirectTo = runtime.onDirectTo,
                onUnsavedRouteDecision = runtime.onUnsavedRouteDecision,
                onStartNavigation = { routeId, routeRevision ->
                    runtime.onActiveNavigationCommand(ActiveNavigationCommand.Start(routeId, routeRevision))
                },
                onSaveAndStartRoute = runtime.onSaveAndStartRoute,
                chartSurface = chartSurface,
            )
        },
    ),
    InstalledAppBinding(
        catalogContribution = PreferencesShellContribution,
        visualContributions = { environment ->
            listOf(preferencesLauncherVisualContribution())
        },
        dynamicLaunchTokenMatcher = PreferencesDestinations::accepts,
        internalAppHost = InternalAppHost(PreferencesDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            val tokenSection = PreferencesDestinations.section(token)
                ?: error("Unknown Preferences launch token: ${token.value}")
            PreferencesWorkspace(runtime.preferencesState.copy(section = tokenSection), runtime.onPreferencesAction)
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
            listOf(chartLibraryLauncherVisualContribution(environment.chartLibraryState, environment.mapState.chartDisplayPlan))
        },
        searchContributions = { environment, query ->
            chartLibrarySearchContributions(environment.chartLibraryState, query)
        },
        dynamicLaunchTokenMatcher = ChartLibraryDestinations::accepts,
        internalAppHost = InternalAppHost(ChartLibraryDestinations.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            val destination = remember(token) { requireNotNull(ChartLibraryDestinations.parse(token)) }
            LaunchedEffect(token) { runtime.onOpenChartLibrary(token) }
            val previewSnapshotSink = remember { BoundedMapTileSnapshotRuntime() }
            val previewSurface = rememberProductionChartSurface(runtime, previewSnapshotSink)
            val displayPlan = runtime.chartDisplayState.plan
            var previewState by remember {
                mutableStateOf(
                    MapState(
                        chartDisplayPlan = displayPlan,
                        mapViewMode = chartMapMode(displayPlan.builtInBaseStyle),
                    ),
                )
            }
            LaunchedEffect(displayPlan.fingerprint) {
                previewState = previewState.copy(
                    chartDisplayPlan = displayPlan,
                    mapViewMode = chartMapMode(displayPlan.builtInBaseStyle),
                )
            }
            ChartLibraryWorkspace(
                state = runtime.chartLibraryState,
                onAction = runtime.onChartLibraryAction,
                compositePreview = { modifier ->
                    previewSurface(
                        previewState,
                        { action -> previewState = MapReducer.reduce(previewState, action).state },
                        {},
                        modifier,
                    )
                },
            )
        },
    ),
    InstalledAppBinding(
        catalogContribution = NavigationShellContribution,
        visualContributions = { environment -> listOf(navigationLauncherVisualContribution(environment.navigationState)) },
        searchContributions = { environment, query -> navigationSearchContributions(environment.navigationState, query) },
        dynamicLaunchTokenMatcher = NavigationDestinations::accepts,
        internalAppHost = InternalAppHost(NavigationShellContribution.AppId) { token ->
            val runtime = LocalProductionShellRuntime.current
            LaunchedEffect(token) { runtime.onOpenNavigation(token) }
            NavigationWorkspace(
                state = runtime.navigationState,
                onAction = runtime.onNavigationAction,
                gpxState = runtime.gpxImportState.toNavigationGpxState(),
                onGpxAction = { runtime.onGpxImportAction(it.toDocumentAction()) },
            )
        },
    ),
)

val productionInstalledAppRegistry: InstalledAppRegistry<ProductionShellVisualEnvironment> =
    InstalledAppRegistry(productionInstalledApps)
val productionContributions = productionInstalledAppRegistry.catalogContributions
val productionCatalog = LauncherCatalog.compose(revision = 5, contributions = productionContributions)
val productionLaunchRegistrations = productionInstalledAppRegistry.launchRegistrations

fun productionAppTilePreferences(persistedValues: Map<String, String>): List<AppTilePreferenceUi> {
    val registry = productionInstalledAppRegistry.appPreferenceRegistry
    val resolved = registry.resolve(persistedValues)
    return productionCatalog.snapshot.apps.map { app ->
        AppTilePreferenceUi(
            appId = app.appId,
            supportedSizes = productionCatalog.snapshot.entries
                .filter { it.appId == app.appId }
                .flatMapTo(linkedSetOf()) { it.supportedSizes },
            preferences = registry.definitions
                .filterValues { it.first == app.appId }
                .entries.sortedBy { it.key.value }
                .map { (key, owned) -> AppTilePreferenceItemUi(owned.second, resolved.getValue(key)) },
        )
    }
}
@Composable
fun productionVisualContributions(
    theme: WpThemeSpec,
    mapState: MapState = MapState(),
    offlineCoverageState: OfflineCoverageUiState = OfflineCoverageUiState.Idle,
    nmeaSnapshot: NmeaRuntimeSnapshot = NmeaRuntimeSnapshot.EMPTY,
    dataSourcesSnapshot: MarineSourceSnapshot = MarineSourceSnapshot.EMPTY,
    chartLibraryState: ChartLibraryUiState = ChartLibraryUiState(),
    navigationState: NavigationUiState = NavigationUiState(),
    mapTileSnapshot: MapTileSnapshot? = null,
    chartTileMode: com.yokuli.marine.feature.chart.ChartTileMode = com.yokuli.marine.feature.chart.ChartTileMode.AUTO,
): List<LauncherEntryVisualContribution> {
    val environment = ProductionShellVisualEnvironment(
        theme,
        mapState,
        offlineCoverageState,
        nmeaSnapshot,
        dataSourcesSnapshot,
        chartLibraryState,
        navigationState,
        mapTileSnapshot,
        chartTileMode,
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
    navigationState: NavigationUiState = NavigationUiState(),
    query: String,
) = productionInstalledAppRegistry.searchContributions(
    ProductionShellVisualEnvironment(
        theme,
        mapState,
        offlineCoverageState,
        nmeaSnapshot,
        dataSourcesSnapshot,
        chartLibraryState,
        navigationState,
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
            entryId = PreferencesDestinations.EntryId,
            size = MarineTileSize.ICON_1X1,
            rank = 1024L,
        ),
    ),
)

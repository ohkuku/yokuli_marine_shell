package com.yokuli.marine.shell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.map.domain.DefaultMapStore
import com.yokuli.marine.map.domain.MapEffect
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.feature.chart.ChartDisplayCoordinator
import com.yokuli.marine.feature.chart.ChartDisplayUiAction
import com.yokuli.marine.feature.chart.ChartDisplayUiState
import com.yokuli.marine.feature.chart.GpxDocumentSource
import com.yokuli.marine.feature.chart.GpxImportCoordinator
import com.yokuli.marine.feature.chart.GpxImportUiAction
import com.yokuli.marine.feature.chart.GpxImportUiState
import com.yokuli.marine.feature.chart.OfflineCoverageCoordinator
import com.yokuli.marine.feature.chart.OfflineCoverageUiState
import com.yokuli.marine.feature.chart.UnsavedRouteDecision
import com.yokuli.marine.feature.chart.PositionObservationCoordinator
import com.yokuli.marine.feature.chartlibrary.ChartLibraryCoordinator
import com.yokuli.marine.feature.chartlibrary.ChartLibraryDestination
import com.yokuli.marine.feature.chartlibrary.ChartLibraryEffect
import com.yokuli.marine.feature.chartlibrary.ChartLibraryUiAction
import com.yokuli.marine.feature.chartlibrary.ChartLibraryUiState
import com.yokuli.marine.map.domain.chartlibrary.ChartPickerSelection
import com.yokuli.marine.map.offline.ChartDisplayCoverageIndex
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.feature.data.DataCoordinator
import com.yokuli.marine.feature.data.DataDestination
import com.yokuli.marine.feature.data.DataEffect
import com.yokuli.marine.feature.data.DataUiAction
import com.yokuli.marine.feature.data.DataUiState
import com.yokuli.marine.feature.nmeainput.NmeaInputCoordinator
import com.yokuli.marine.feature.nmeainput.NmeaInputEffect
import com.yokuli.marine.feature.nmeainput.NmeaInputUiAction
import com.yokuli.marine.feature.nmeainput.NmeaInputUiState
import com.yokuli.marine.data.source.MarineFeatureLinkToken
import com.yokuli.marine.data.source.MarineFeatureLinks
import com.yokuli.marine.navigation.domain.ActiveNavigationCommand
import com.yokuli.marine.navigation.domain.ActiveNavigationCommandResult
import com.yokuli.marine.navigation.domain.ActiveNavigationIssue
import com.yokuli.marine.navigation.domain.ActiveNavigationRuntimePort
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.feature.navigation.NavigationCoordinator
import com.yokuli.marine.feature.navigation.NavigationDestination
import com.yokuli.marine.feature.navigation.NavigationEffect
import com.yokuli.marine.feature.navigation.NavigationUiAction
import com.yokuli.marine.feature.navigation.NavigationUiState
import android.net.Uri
import com.yokuli.shell.engine.DefaultLauncherEngine
import com.yokuli.shell.engine.InMemoryLauncherPersistence
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEngine
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.engine.LauncherRecoveryMode
import com.yokuli.shell.engine.InternalAppTaskId
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.MeasurementUnitSystem
import com.yokuli.shell.contract.MotionPreference
import com.yokuli.shell.contract.AppPreferenceKey
import com.yokuli.shell.contract.AppPreferenceRegistry
import com.yokuli.shell.contract.AppPreferenceValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 中文：ViewModel 组合平台存储与纯 Engine；Activity 只渲染 StateFlow 并执行平台 effect。
 * English: This ViewModel composes platform storage with the pure Engine; the Activity only renders flows and effects.
 */
class ShellViewModel(application: Application) : AndroidViewModel(application) {
    private sealed interface PendingRouteDecision {
        data class CloseTask(val taskId: InternalAppTaskId) : PendingRouteDecision
        data class StartNavigation(
            val command: ActiveNavigationCommand,
            val completion: CompletableDeferred<ActiveNavigationCommandResult>,
        ) : PendingRouteDecision
    }

    private val shellApplication = application as ShellApplication
    private val defaults = LauncherPersistedState(
        document = defaultStartDocument,
        languageTag = application.selectedAppLanguageTag(),
    )
    private val persistence = shellApplication.launcherPersistence
    private val recoveryTrackingEnabled = BuildConfig.BUILD_TYPE !in HARNESS_BUILD_TYPES
    private val enginePersistence = if (recoveryTrackingEnabled) {
        persistence
    } else {
        // The performance/profile process is repeatedly killed and relaunched. Its Engine
        // needs a synchronous default document; production builds retain Proto restore/recovery.
        InMemoryLauncherPersistence(defaultStartDocument)
    }
    private var healthyTimer: Job? = null
    private val routeDecisionLock = Any()
    private var pendingRouteDecision: PendingRouteDecision? = null
    private val startupJob: Job
    private val chartPackages = shellApplication.chartPackageRepository
    val nmeaRuntimeState = shellApplication.nmeaInputRuntime.state
    val marineSourceState = shellApplication.marineSourceRuntime.state
    val phoneLocationState = shellApplication.phoneLocationRuntime.state
    private val nmeaInputCoordinator = NmeaInputCoordinator(
        runtimePort = shellApplication.nmeaInputRuntime,
        scope = viewModelScope,
        nowMillis = { android.os.SystemClock.elapsedRealtime() },
    )
    val nmeaInputState: StateFlow<NmeaInputUiState> = nmeaInputCoordinator.state
    val nmeaInputEffects: Flow<NmeaInputEffect> = nmeaInputCoordinator.effects
    private val dataCoordinator = DataCoordinator(
        sourcePort = shellApplication.marineSourceRuntime,
        nmeaPort = shellApplication.nmeaInputRuntime,
        phoneDemandPort = shellApplication.dataPhoneDemandRuntime,
        scope = viewModelScope,
    )
    val dataState: StateFlow<DataUiState> = dataCoordinator.state
    val dataEffects: Flow<DataEffect> = dataCoordinator.effects
    private val chartLibraryCoordinator = ChartLibraryCoordinator(
        runtime = shellApplication.chartLibraryRuntime,
        scope = viewModelScope,
    )
    val chartLibraryState: StateFlow<ChartLibraryUiState> = chartLibraryCoordinator.state
    val chartLibraryEffects: Flow<ChartLibraryEffect> = chartLibraryCoordinator.effects
    val activeNavigationState = shellApplication.activeNavigationRuntime.state

    val persistedPreferences: StateFlow<LauncherPersistedState> = persistence.state
        .map { persisted ->
            (persisted ?: defaults).copy(languageTag = application.selectedAppLanguageTag())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaults)

    val engine: LauncherEngine = DefaultLauncherEngine(
        hostPort = productionHostPort,
        persistence = enginePersistence,
        defaultDocument = defaultStartDocument,
        scope = viewModelScope,
    )

    val mapStore: MapStore = DefaultMapStore(
        initialState = MapState(libraryLoadState = MapLibraryLoadState.NOT_LOADED),
        scope = viewModelScope,
        persistence = (application as ShellApplication).mapPersistence,
        effectHandler = { effect ->
            when (effect) {
                is MapEffect.LogIncident -> android.util.Log.w("YokuliMap", effect.incident.toString())
                is MapEffect.PersistLibrary,
                is MapEffect.PersistSession,
                MapEffect.Reload,
                -> Unit
            }
        },
    )
    private val positionObservationCoordinator = PositionObservationCoordinator(
        port = (application as ShellApplication).positionPort,
        mapStore = mapStore,
        scope = viewModelScope,
        clock = application.observationClock,
    )
    private val chartDisplayCoordinator = ChartDisplayCoordinator(
        catalog = shellApplication.chartLibraryRuntime,
        mapStore = mapStore,
        scope = viewModelScope,
    )
    val chartDisplayState: StateFlow<ChartDisplayUiState> = chartDisplayCoordinator.state
    private val gpxImportCoordinator = GpxImportCoordinator(
        documentSource = GpxDocumentSource { sourceUri ->
            checkNotNull(application.contentResolver.openInputStream(Uri.parse(sourceUri)))
        },
        mapStore = mapStore,
        scope = viewModelScope,
        incidentLogger = {
            // GPX contents, coordinates, names and source URI are private and never logged.
            android.util.Log.w("YokuliMap", "GPX workflow failed: ${it.javaClass.simpleName}")
        },
    )
    val gpxImportState: StateFlow<GpxImportUiState> = gpxImportCoordinator.state
    private val offlineCoverageCoordinator = OfflineCoverageCoordinator(
        tileIndex = (application as ShellApplication).chartCoverageIndex,
        scope = viewModelScope,
        displayCoverage = ChartDisplayCoverageIndex(application.chartLibraryRuntime),
        incidentLogger = {
            // Package paths and route geometry are private and intentionally excluded.
            android.util.Log.w("YokuliMap", "Offline coverage check failed: ${it.javaClass.simpleName}")
        },
    )
    val offlineCoverageState: StateFlow<OfflineCoverageUiState> = offlineCoverageCoordinator.state
    private val guardedActiveNavigationRuntime = object : ActiveNavigationRuntimePort {
        override val state: StateFlow<ActiveNavigationSnapshot> = activeNavigationState

        override suspend fun initialize(): ActiveNavigationSnapshot =
            shellApplication.activeNavigationRuntime.initialize()

        override suspend fun execute(command: ActiveNavigationCommand): ActiveNavigationCommandResult =
            executeNavigationWithDraftGuard(command)
    }
    private val navigationCoordinator = NavigationCoordinator(
        libraryPort = shellApplication.mapPersistence,
        activeRuntime = guardedActiveNavigationRuntime,
        nowMillis = System::currentTimeMillis,
        scope = viewModelScope,
    )
    val navigationState: StateFlow<NavigationUiState> = navigationCoordinator.state
    val navigationEffects: Flow<NavigationEffect> = navigationCoordinator.effects

    init {
        viewModelScope.launch {
            combine(activeNavigationState, mapStore.state) { navigation, map -> navigation to map }
                .collect { (navigation, map) ->
                    val session = navigation.session
                    val geometry = navigation.route?.points.orEmpty().map { point ->
                        GeoPoint(point.position.latitude, point.position.longitude)
                    }
                    if (map.activeNavigationRoute != geometry) {
                        mapStore.dispatch(MapAction.ActiveNavigationGeometryChanged(geometry))
                    }
                    if (
                        session != null &&
                        session.embeddedRoute == null &&
                        map.savedRoutes.any { it.id == session.routeId && it.revision == session.routeRevision } &&
                        map.activeRoutePlanId != session.routeId
                    ) {
                        mapStore.dispatch(MapAction.PreviewRoutePlan(session.routeId))
                    }
                }
        }
        viewModelScope.launch {
            gpxImportState.collect { state ->
                if (state is GpxImportUiState.Succeeded) navigationCoordinator.dispatch(NavigationUiAction.Refresh)
            }
        }
        viewModelScope.launch {
            mapStore.state.collect { state ->
                offlineCoverageCoordinator.invalidateIfInputsChanged(state.savedRoutes, state.chartPackages)
                offlineCoverageCoordinator.invalidateIfDisplayInputsChanged(state.savedRoutes, state.chartDisplayPlan)
            }
        }
        if (!recoveryTrackingEnabled) {
            // A performance/profile harness must render a deterministic Start immediately;
            // its repeated process control is not a production recovery event.
            engine.dispatch(LauncherAction.RestorePersistedDocument(defaultStartDocument))
            engine.dispatch(LauncherAction.ShowDesktop)
        }
        startupJob = viewModelScope.launch {
            val selectedLanguageTag = application.selectedAppLanguageTag()
            val loaded = persistence.load()
            val persisted = (loaded ?: defaults).copy(languageTag = selectedLanguageTag)
            if (loaded != null && loaded.languageTag != selectedLanguageTag) {
                persistence.save(persisted)
            }
            if (recoveryTrackingEnabled) {
                application.synchronizePersistedLanguage(selectedLanguageTag)
            }
            engine.state.first { it.recoveryMode != LauncherRecoveryMode.RESTORING }
            if (recoveryTrackingEnabled) {
                val decision = persistence.beginLaunch(System.currentTimeMillis())
                if (decision.enterSafeMode) {
                    engine.dispatch(LauncherAction.EnterSafeMode)
                }
            } else {
                // Macrobenchmark and Baseline Profile generation deliberately force-stop the target.
                // Treating harness lifecycle control as a production crash would corrupt later journeys.
                persistence.markLaunchHealthy()
                engine.dispatch(LauncherAction.ExitSafeMode)
                engine.dispatch(LauncherAction.ShowDesktop)
            }
        }
    }

    fun saveTheme(theme: WpThemeSpec) {
        viewModelScope.launch {
            val current = persistence.load() ?: defaults
            persistence.savePreferences(theme.mode.name, theme.accent.name, shellApplication.selectedAppLanguageTag())
        }
    }

    fun onChartDisplayAction(action: ChartDisplayUiAction) {
        chartDisplayCoordinator.dispatch(action)
    }

    fun inspectGpxDocument(sourceUri: String) {
        gpxImportCoordinator.inspectDocument(sourceUri)
    }

    fun onGpxImportAction(action: GpxImportUiAction) {
        gpxImportCoordinator.dispatch(action)
    }

    fun onNmeaInputAction(action: NmeaInputUiAction) {
        nmeaInputCoordinator.dispatch(action)
    }

    fun openNmeaInput(token: MarineFeatureLinkToken) {
        nmeaInputCoordinator.open(token)
    }

    fun onDataAction(action: DataUiAction) {
        dataCoordinator.dispatch(action)
    }

    fun onChartLibraryAction(action: ChartLibraryUiAction) = chartLibraryCoordinator.dispatch(action)

    fun openChartLibrary(destination: ChartLibraryDestination) = chartLibraryCoordinator.open(destination)

    fun completeChartLibraryPicker(selection: ChartPickerSelection?) =
        chartLibraryCoordinator.completePicker(selection)

    fun openData(token: com.yokuli.shell.contract.LaunchToken) {
        when (val destination = dataCoordinator.open(token)) {
            is DataDestination.Input -> nmeaInputCoordinator.open(
                destination.connectionId?.let(MarineFeatureLinks::nmeaInputForConnection)
                    ?: MarineFeatureLinks.nmeaInputRoot,
            )
            else -> Unit
        }
    }

    fun acquireChartPackageLease(packageId: ChartPackageId): ChartPackageLease =
        chartPackages.acquireLease(packageId)

    fun startOfflineCoverage(routeId: String, targetZoom: Int, halfWidthNauticalMiles: Double) {
        val state = mapStore.state.value
        val route = state.savedRoutes.firstOrNull { it.id == routeId } ?: return
        if (state.chartDisplayPlan.selection !is ChartDisplaySelection.None) {
            offlineCoverageCoordinator.start(
                route = route,
                displayPlan = state.chartDisplayPlan,
                targetZoom = targetZoom,
                halfWidthNauticalMiles = halfWidthNauticalMiles,
            )
        } else {
            offlineCoverageCoordinator.start(
                route = route,
                packages = state.chartPackages,
                targetZoom = targetZoom,
                halfWidthNauticalMiles = halfWidthNauticalMiles,
            )
        }
    }

    fun cancelOfflineCoverage() = offlineCoverageCoordinator.cancel()

    fun onActiveNavigationCommand(command: ActiveNavigationCommand): Job = viewModelScope.launch {
        val result = guardedActiveNavigationRuntime.execute(command)
        if (result is ActiveNavigationCommandResult.Accepted &&
            command is ActiveNavigationCommand.Start
        ) {
            mapStore.dispatch(MapAction.PreviewRoutePlan(command.routeId))
            mapStore.dispatch(MapAction.OpenSurface(com.yokuli.marine.map.domain.MapSurface.Root))
        } else if (result is ActiveNavigationCommandResult.Accepted && command is ActiveNavigationCommand.DirectTo) {
            mapStore.dispatch(MapAction.OpenSurface(com.yokuli.marine.map.domain.MapSurface.Root))
        }
    }

    fun startDirectTo(point: GeoPoint, name: String): Job = onActiveNavigationCommand(
        ActiveNavigationCommand.DirectTo(
            destination = NavigationPosition(point.latitude, point.longitude),
            destinationId = "target-${point.latitude}-${point.longitude}",
            destinationName = name,
        ),
    )

    fun saveAndStartActiveRoute(): Job = viewModelScope.launch {
        val status = saveActiveRouteDraft() ?: return@launch
        onActiveNavigationCommand(ActiveNavigationCommand.Start(status.routeId, status.revision)).join()
    }

    fun requestCloseTask(taskId: InternalAppTaskId) {
        val task = engine.state.value.tasks.task(taskId) ?: return
        val isChart = task.appId == com.yokuli.marine.feature.chart.ChartDestinations.AppId
        val draft = mapStore.state.value.routeDraft
        if (!isChart || draft == null || draft.waypoints.isEmpty()) {
            engine.dispatch(LauncherAction.CloseTask(taskId))
            return
        }
        val claimed = synchronized(routeDecisionLock) {
            if (pendingRouteDecision != null) false else {
                pendingRouteDecision = PendingRouteDecision.CloseTask(taskId)
                true
            }
        }
        if (!claimed) return
        mapStore.dispatch(MapAction.RequestCloseRouteDraft)
        engine.dispatch(LauncherAction.ActivateTask(taskId))
    }

    fun resolveUnsavedRoute(decision: UnsavedRouteDecision): Job = viewModelScope.launch {
        val pending = synchronized(routeDecisionLock) { pendingRouteDecision }
        when (decision) {
            UnsavedRouteDecision.CANCEL -> {
                clearPendingRouteDecision(pending)
                mapStore.dispatch(MapAction.DismissTransient)
                (pending as? PendingRouteDecision.StartNavigation)?.completion?.complete(
                    ActiveNavigationCommandResult.Rejected(ActiveNavigationIssue.INVALID_COMMAND),
                )
            }
            UnsavedRouteDecision.DISCARD -> {
                val draftId = mapStore.state.value.routeDraft?.id ?: return@launch
                mapStore.dispatch(MapAction.DiscardRouteDraft(draftId))
                finishPendingRouteDecision(pending)
            }
            UnsavedRouteDecision.SAVE -> {
                saveActiveRouteDraft() ?: return@launch
                finishPendingRouteDecision(pending)
            }
        }
    }

    private suspend fun executeNavigationWithDraftGuard(
        command: ActiveNavigationCommand,
    ): ActiveNavigationCommandResult {
        if (command !is ActiveNavigationCommand.Start && command !is ActiveNavigationCommand.DirectTo) {
            return shellApplication.activeNavigationRuntime.execute(command)
        }
        val draft = mapStore.state.value.routeDraft
        if (draft == null || draft.waypoints.isEmpty()) {
            if (draft != null) mapStore.dispatch(MapAction.DiscardRouteDraft(draft.id))
            mapStore.dispatch(MapAction.SelectTool(com.yokuli.marine.map.domain.MapTool.BROWSE))
            return shellApplication.activeNavigationRuntime.execute(command)
        }
        val completion = CompletableDeferred<ActiveNavigationCommandResult>()
        val claimed = synchronized(routeDecisionLock) {
            if (pendingRouteDecision != null) false else {
                pendingRouteDecision = PendingRouteDecision.StartNavigation(command, completion)
                true
            }
        }
        if (!claimed) return ActiveNavigationCommandResult.Rejected(ActiveNavigationIssue.INVALID_COMMAND)
        mapStore.dispatch(MapAction.RequestCloseRouteDraft)
        engine.dispatch(LauncherAction.Open(com.yokuli.marine.feature.chart.ChartDestinations.Browse, preserveCaller = true))
        return completion.await()
    }

    private suspend fun finishPendingRouteDecision(pending: PendingRouteDecision?) {
        clearPendingRouteDecision(pending)
        when (pending) {
            is PendingRouteDecision.CloseTask -> engine.dispatch(LauncherAction.CloseTask(pending.taskId))
            is PendingRouteDecision.StartNavigation -> pending.completion.complete(
                shellApplication.activeNavigationRuntime.execute(pending.command),
            )
            null -> Unit
        }
    }

    private fun clearPendingRouteDecision(expected: PendingRouteDecision?) {
        synchronized(routeDecisionLock) {
            if (pendingRouteDecision === expected) pendingRouteDecision = null
        }
    }

    private suspend fun saveActiveRouteDraft(): com.yokuli.marine.map.domain.RouteSaveStatus? {
        val before = mapStore.state.value
        before.routeDraft?.takeIf { it.waypoints.size >= 2 } ?: return null
        if (mapStore.dispatch(MapAction.SaveRoutePlan) !in setOf(MapDispatchResult.ACCEPTED, MapDispatchResult.COALESCED)) {
            return null
        }
        val submitted = mapStore.state.first { state ->
            state.libraryRevision > before.libraryRevision && state.routeSaveStatus != null
        }
        val completed = if (submitted.routeSaveStatus?.state == MapSaveState.PENDING) {
            mapStore.state.first { state ->
                val status = state.routeSaveStatus
                status != null && status.routeId == submitted.routeSaveStatus?.routeId && status.state != MapSaveState.PENDING
            }
        } else submitted
        return completed.routeSaveStatus?.takeIf { it.state == MapSaveState.SAVED }
    }

    fun onNavigationAction(action: NavigationUiAction) = navigationCoordinator.dispatch(action)

    fun openNavigation(token: com.yokuli.shell.contract.LaunchToken): NavigationDestination? =
        navigationCoordinator.open(token)

    fun saveLanguage(language: AppLanguage) {
        viewModelScope.launch {
            val current = persistence.load() ?: defaults
            persistence.savePreferences(current.themeModeName, current.accentName, language.languageTag)
        }
    }

    fun saveMeasurementUnits(units: MeasurementUnitSystem) {
        viewModelScope.launch {
            val current = persistence.load() ?: defaults
            persistence.save(
                current.copy(
                    languageTag = shellApplication.selectedAppLanguageTag(),
                    measurementUnitSystemName = units.name,
                ),
            )
        }
    }

    fun saveMotionPreference(preference: MotionPreference) {
        viewModelScope.launch {
            val current = persistence.load() ?: defaults
            persistence.save(
                current.copy(
                    languageTag = shellApplication.selectedAppLanguageTag(),
                    motionPreferenceName = preference.name,
                ),
            )
        }
    }

    fun saveAppPreference(key: AppPreferenceKey, value: AppPreferenceValue) {
        viewModelScope.launch {
            val definition = productionInstalledAppRegistry.appPreferenceRegistry.definitions[key]?.second
                ?: return@launch
            if (!definition.accepts(value)) return@launch
            val current = persistence.load() ?: defaults
            val next = current.appPreferenceValues.toMutableMap().apply {
                put(key.value, AppPreferenceRegistry.encode(value))
            }.entries.sortedBy { it.key }
                .take(AppPreferenceRegistry.MAX_REGISTERED_PREFERENCES)
                .associate { it.toPair() }
            persistence.save(
                current.copy(
                    languageTag = shellApplication.selectedAppLanguageTag(),
                    appPreferenceValues = next,
                ),
            )
        }
    }

    fun resetLauncher(): Job = viewModelScope.launch {
        persistence.reset()
        engine.dispatch(LauncherAction.RestorePersistedDocument(defaultStartDocument))
        engine.dispatch(LauncherAction.ShowDesktop)
    }

    fun resetStartDocument() {
        engine.dispatch(LauncherAction.ResetStartDocument)
    }

    /** Ends only app-owned transient UI. Saved marine data remains in its durable store. */
    fun closeAppSession(appId: LauncherAppId) {
        if (appId == com.yokuli.marine.feature.chart.ChartDestinations.AppId) {
            mapStore.dispatch(MapAction.CloseSession)
        }
    }

    /**
     * Benchmark-only rendezvous for task reuse. Production relaunch must preserve its surface.
     */
    fun prepareBenchmarkStart() {
        if (recoveryTrackingEnabled) return
        engine.dispatch(LauncherAction.RestorePersistedDocument(defaultStartDocument))
        engine.dispatch(LauncherAction.ExitSafeMode)
        engine.dispatch(LauncherAction.ShowDesktop)
    }

    fun onHostResumed() {
        if (!recoveryTrackingEnabled) return
        healthyTimer?.cancel()
        healthyTimer = viewModelScope.launch {
            startupJob.join()
            delay(HEALTHY_STARTUP_MILLIS)
            if (engine.state.value.recoveryMode != LauncherRecoveryMode.SAFE_MODE) {
                persistence.markLaunchHealthy()
            }
        }
    }

    fun onHostStopped() {
        if (!recoveryTrackingEnabled) return
        healthyTimer?.cancel()
        healthyTimer = null
        if (engine.state.value.recoveryMode != LauncherRecoveryMode.SAFE_MODE) {
            viewModelScope.launch {
                startupJob.join()
                persistence.markLaunchHealthy()
            }
        }
    }

    override fun onCleared() {
        chartDisplayCoordinator.close()
        positionObservationCoordinator.close()
        mapStore.close()
        super.onCleared()
    }

    private companion object {
        const val HEALTHY_STARTUP_MILLIS = 10_000L
        val HARNESS_BUILD_TYPES = setOf("benchmark", "nonMinifiedRelease")
    }
}

package com.yokuli.marine.shell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.map.domain.DefaultMapStore
import com.yokuli.marine.map.domain.MapEffect
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.feature.chart.ChartImportUiAction
import com.yokuli.marine.feature.chart.ChartImportUiState
import com.yokuli.marine.feature.chart.ChartPackageCoordinator
import com.yokuli.marine.feature.chart.GpxDocumentSource
import com.yokuli.marine.feature.chart.GpxImportCoordinator
import com.yokuli.marine.feature.chart.GpxImportUiAction
import com.yokuli.marine.feature.chart.GpxImportUiState
import com.yokuli.marine.feature.chart.OfflineCoverageCoordinator
import com.yokuli.marine.feature.chart.OfflineCoverageUiState
import android.net.Uri
import com.yokuli.shell.engine.DefaultLauncherEngine
import com.yokuli.shell.engine.InMemoryLauncherPersistence
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEngine
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.engine.LauncherRecoveryMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 中文：ViewModel 组合平台存储与纯 Engine；Activity 只渲染 StateFlow 并执行平台 effect。
 * English: This ViewModel composes platform storage with the pure Engine; the Activity only renders flows and effects.
 */
class ShellViewModel(application: Application) : AndroidViewModel(application) {
    private val defaults = LauncherPersistedState(document = defaultStartDocument)
    private val persistence = (application as ShellApplication).launcherPersistence
    private val recoveryTrackingEnabled = BuildConfig.BUILD_TYPE !in HARNESS_BUILD_TYPES
    private val enginePersistence = if (recoveryTrackingEnabled) {
        persistence
    } else {
        // The performance/profile process is repeatedly killed and relaunched. Its Engine
        // needs a synchronous default document; production builds retain Proto restore/recovery.
        InMemoryLauncherPersistence(defaultStartDocument)
    }
    private var healthyTimer: Job? = null
    private val startupJob: Job
    private val chartPackages = (application as ShellApplication).chartPackageRepository

    val persistedPreferences: StateFlow<LauncherPersistedState> = persistence.state
        .map { it ?: defaults }
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
    private val chartPackageCoordinator = ChartPackageCoordinator(
        repository = chartPackages,
        mapStore = mapStore,
        scope = viewModelScope,
        incidentLogger = {
            // Source URIs, package metadata and coordinates are intentionally excluded.
            android.util.Log.w("YokuliMap", "Chart package workflow failed: ${it.javaClass.simpleName}")
        },
    )
    val chartImportState: StateFlow<ChartImportUiState> = chartPackageCoordinator.state
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
        incidentLogger = {
            // Package paths and route geometry are private and intentionally excluded.
            android.util.Log.w("YokuliMap", "Offline coverage check failed: ${it.javaClass.simpleName}")
        },
    )
    val offlineCoverageState: StateFlow<OfflineCoverageUiState> = offlineCoverageCoordinator.state

    init {
        viewModelScope.launch {
            mapStore.state.collect { state ->
                offlineCoverageCoordinator.invalidateIfInputsChanged(state.savedRoutes, state.chartPackages)
            }
        }
        if (!recoveryTrackingEnabled) {
            // A performance/profile harness must render a deterministic Start immediately;
            // its repeated process control is not a production recovery event.
            engine.dispatch(LauncherAction.RestorePersistedDocument(defaultStartDocument))
            engine.dispatch(LauncherAction.ShowDesktop)
        }
        startupJob = viewModelScope.launch {
            val persisted = persistence.load() ?: defaults
            if (recoveryTrackingEnabled) {
                application.synchronizePersistedLanguage(persisted.languageTag)
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
            persistence.savePreferences(theme.mode.name, theme.accent.name, current.languageTag)
        }
    }

    fun inspectChartDocument(sourceUri: String) {
        chartPackageCoordinator.inspectDocument(sourceUri)
    }

    fun onChartImportAction(action: ChartImportUiAction) {
        chartPackageCoordinator.dispatch(action)
    }

    fun inspectGpxDocument(sourceUri: String) {
        gpxImportCoordinator.inspectDocument(sourceUri)
    }

    fun onGpxImportAction(action: GpxImportUiAction) {
        gpxImportCoordinator.dispatch(action)
    }

    fun acquireChartPackageLease(packageId: ChartPackageId): ChartPackageLease =
        chartPackages.acquireLease(packageId)

    fun startOfflineCoverage(routeId: String, targetZoom: Int, halfWidthNauticalMiles: Double) {
        val state = mapStore.state.value
        val route = state.savedRoutes.firstOrNull { it.id == routeId } ?: return
        offlineCoverageCoordinator.start(
            route = route,
            packages = state.chartPackages,
            targetZoom = targetZoom,
            halfWidthNauticalMiles = halfWidthNauticalMiles,
        )
    }

    fun cancelOfflineCoverage() = offlineCoverageCoordinator.cancel()

    fun saveLanguage(language: AppLanguage) {
        viewModelScope.launch {
            val current = persistence.load() ?: defaults
            persistence.savePreferences(current.themeModeName, current.accentName, language.languageTag)
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

    /**
     * Benchmark-only rendezvous for task reuse. Production relaunch must preserve its surface.
     */
    fun prepareBenchmarkStart() {
        if (recoveryTrackingEnabled) return
        engine.dispatch(LauncherAction.RestorePersistedDocument(defaultStartDocument))
        engine.dispatch(LauncherAction.ExitSafeMode)
        engine.dispatch(LauncherAction.ShowDesktop)
    }

    fun requestAndroidSettings() {
        engine.dispatch(LauncherAction.RequestAndroidSettings)
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
        mapStore.close()
        super.onCleared()
    }

    private companion object {
        const val HEALTHY_STARTUP_MILLIS = 10_000L
        val HARNESS_BUILD_TYPES = setOf("benchmark", "nonMinifiedRelease")
    }
}

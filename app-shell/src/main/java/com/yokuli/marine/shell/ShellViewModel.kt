package com.yokuli.marine.shell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.shell.engine.DefaultLauncherEngine
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEngine
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.engine.LauncherRecoveryMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private var healthyTimer: Job? = null
    private val startupJob: Job

    val persistedPreferences: StateFlow<LauncherPersistedState> = persistence.state
        .map { it ?: defaults }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaults)

    val engine: LauncherEngine = DefaultLauncherEngine(
        hostPort = productionHostPort,
        persistence = persistence,
        defaultDocument = defaultStartDocument,
        scope = viewModelScope,
    )

    init {
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

    private companion object {
        const val HEALTHY_STARTUP_MILLIS = 10_000L
        val HARNESS_BUILD_TYPES = setOf("benchmark", "nonMinifiedRelease")
    }
}

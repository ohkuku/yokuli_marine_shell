package com.yokuli.marine.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.core.design.WpNavigationIntent
import com.yokuli.marine.core.design.WpMotionTimings
import com.yokuli.marine.core.design.WpSurfaceTransitionHost
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.feature.desktop.LauncherUiAction
import com.yokuli.marine.feature.desktop.InteractiveLauncherPager
import com.yokuli.marine.feature.desktop.LauncherPagerPage
import com.yokuli.marine.feature.desktop.LauncherRecoverySurface
import com.yokuli.marine.feature.desktop.WpAppList
import com.yokuli.marine.feature.desktop.WpRecentsSurface
import com.yokuli.marine.feature.desktop.WpSearchSurface
import com.yokuli.marine.feature.desktop.WpStatusStrip
import com.yokuli.marine.feature.desktop.WpSystemKeyBar
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.feature.desktop.productionLauncherUiState
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsSection
import com.yokuli.marine.feature.settings.SettingsUiAction
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEffect
import com.yokuli.shell.engine.LauncherRecoveryMode
import com.yokuli.shell.engine.ShellVisualSurface
import com.yokuli.shell.engine.LauncherTransitionIntent
import com.yokuli.shell.engine.InternalAppTaskId
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.toLauncherAction
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherInput
import com.yokuli.shell.android.AndroidLauncherKeyAdapter

class ShellActivity : AppCompatActivity() {
    private val shellViewModel by viewModels<ShellViewModel>()
    private var longBackConsumed = false
    internal var platformIntentLauncher: (Intent) -> Unit = { intent -> startActivity(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bootstrapLegacyLocale()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        enterImmersiveMode()
        setContent { YokuliShell(shellViewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Bringing the existing Android task forward preserves its in-app Shell surface.
        // Only explicit deep links may request a different internal destination.
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        shellViewModel.onHostResumed()
    }

    override fun onStop() {
        shellViewModel.onHostStopped()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val input = AndroidLauncherKeyAdapter.mapKeyCode(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (input == LauncherInput.BACK && event.repeatCount > 0 && !longBackConsumed) {
                longBackConsumed = true
                dispatchInput(LauncherInput.RECENTS)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (input == LauncherInput.BACK && longBackConsumed) {
                longBackConsumed = false
            } else {
                dispatchInput(input)
            }
            return true
        }
        return true
    }

    private fun dispatchInput(input: LauncherInput) {
        shellViewModel.engine.dispatch(input.toLauncherAction())
    }

    internal fun openAndroidSettings() {
        platformIntentLauncher(Intent(Settings.ACTION_SETTINGS))
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun YokuliShell(shellViewModel: ShellViewModel = viewModel<ShellViewModel>()) {
    val context = LocalContext.current
    val engine = shellViewModel.engine
    val engineState by engine.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(engine, hapticFeedback) {
        engine.effects.collect { effect ->
            when (effect) {
                is LauncherEffect.Haptic -> hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                LauncherEffect.RequestHostExit -> (context as? Activity)?.finishAfterTransition()
                LauncherEffect.OpenAndroidSettings -> (context as? ShellActivity)?.openAndroidSettings()
                    ?: context.startActivity(Intent(Settings.ACTION_SETTINGS))
                is LauncherEffect.LogIncident -> Log.w("YokuliLauncher", effect.incident.toString())
                else -> Unit
            }
        }
    }
    val persistedPreferences by shellViewModel.persistedPreferences.collectAsState()
    val themeSpec = WpThemeSpec(
        WpThemeMode.valueOf(persistedPreferences.themeModeName),
        WpAccent.valueOf(persistedPreferences.accentName),
    )
    val language = if (persistedPreferences.languageTag == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
    val dispatch: (LauncherAction) -> Unit = engine::dispatch
    val dispatchInput: (LauncherInput) -> Unit = { input -> dispatch(input.toLauncherAction()) }
    BackHandler(enabled = true) { dispatchInput(LauncherInput.BACK) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) engine.dispatch(LauncherAction.CancelTileOperation)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    YokuliTheme(themeSpec) {
        val colors = LocalWpTheme.current
        SyncHostWindowChrome(colors.background, themeSpec.mode == WpThemeMode.LIGHT)
        val reducedMotion = rememberPlatformReducedMotion() || engineState.recoveryMode != LauncherRecoveryMode.NORMAL
        val motionProfile = WpReferenceProfiles.require(engineState.start.document.profileId).motion
        val motionTimings = remember(motionProfile) {
            WpMotionTimings(
                pageSettleMillis = motionProfile.measuredPageSettleMillis ?: 700,
                appOpenMillis = motionProfile.measuredAppOpenMillis ?: 1_000,
                backReturnMillis = motionProfile.measuredBackReturnMillis ?: 750,
            )
        }
        val runtime = ProductionShellRuntime(
            mapConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
            theme = themeSpec,
            language = language,
            heavyContentReady = true,
            pinnedTileCount = engineState.start.document.placements.size,
            startDocumentVersion = engineState.start.document.defaultLayoutVersion,
            versionName = BuildConfig.VERSION_NAME,
            buildVariant = "${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}",
            gitSha = BuildConfig.GIT_SHA,
            debugShellLabAvailable = BuildConfig.DEBUG,
            openMapSettings = {
                dispatch(LauncherAction.Open(SettingsDestinations.Overview))
                dispatch(LauncherAction.Open(SettingsDestinations.Map))
            },
            onSettingsAction = { action ->
                when (action) {
                    is SettingsUiAction.OpenSection -> dispatch(
                        LauncherAction.Open(SettingsDestinations.token(action.section)),
                    )
                    is SettingsUiAction.ChangeTheme -> {
                        shellViewModel.saveTheme(action.theme)
                    }
                    is SettingsUiAction.ChangeLanguage -> {
                        shellViewModel.saveLanguage(action.language)
                        context.persistAppLanguage(action.language)
                    }
                    SettingsUiAction.ResetStartScreen -> shellViewModel.resetStartDocument()
                    SettingsUiAction.OpenAndroidSettings -> shellViewModel.requestAndroidSettings()
                    SettingsUiAction.OpenShellLab -> if (BuildConfig.DEBUG) context.openShellLab()
                }
            },
        )
        CompositionLocalProvider(LocalProductionShellRuntime provides runtime) {
            val launcherState = productionLauncherUiState(
                catalog = engineState.catalog,
                document = engineState.start.document,
                interaction = engineState.start.interaction,
                transient = engineState.transient,
                reveal = engineState.start.reveal,
                mapConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
                theme = themeSpec,
                visualContributions = productionVisualContributions,
            )
            val startEditing = engineState.start.interaction !is StartInteractionState.Idle
            var retainedSearchQuery by remember { mutableStateOf("") }
            val activeSearchQuery = (engineState.surface as? ShellVisualSurface.Search)?.query
            LaunchedEffect(activeSearchQuery) {
                if (activeSearchQuery != null) retainedSearchQuery = activeSearchQuery
            }
            val launcherAction: (LauncherUiAction) -> Unit = { action ->
                when (action) {
                    is LauncherUiAction.Open -> dispatch(LauncherAction.Open(action.token))
                    LauncherUiAction.ShowAllApps -> dispatch(LauncherAction.ShowAllApps)
                    is LauncherUiAction.ProposeLayout -> dispatch(LauncherAction.ApplyLayoutProposal(action.proposal))
                    is LauncherUiAction.EnterStartEdit -> dispatch(LauncherAction.EnterStartEdit(action.tileId))
                    is LauncherUiAction.SelectStartTile -> dispatch(LauncherAction.SelectStartTile(action.tileId))
                    LauncherUiAction.ExitStartEdit -> dispatch(LauncherAction.ExitStartEdit)
                    is LauncherUiAction.BeginTileDrag -> dispatch(
                        LauncherAction.BeginTileDrag(action.tileId, action.pointerId, action.grabOffset),
                    )
                    is LauncherUiAction.UpdateTileDrag -> dispatch(
                        LauncherAction.UpdateTileDrag(
                            action.tileId,
                            action.visualOffset,
                            action.targetCell,
                            action.autoScrollPxPerSecond,
                        ),
                    )
                    is LauncherUiAction.AutoScrollTileDrag -> dispatch(
                        LauncherAction.AutoScrollTileDrag(action.tileId, action.consumedPx, action.targetCell),
                    )
                    is LauncherUiAction.DropTile -> dispatch(LauncherAction.DropTile(action.tileId))
                    LauncherUiAction.CancelTileOperation -> dispatch(LauncherAction.CancelTileOperation)
                    is LauncherUiAction.ResizeTile -> dispatch(LauncherAction.ResizeTile(action.tileId))
                    LauncherUiAction.CommitTileResize -> dispatch(LauncherAction.CommitTileResize)
                    is LauncherUiAction.MoveTileBy -> dispatch(
                        LauncherAction.MoveTileBy(action.tileId, action.columns, action.rows),
                    )
                    is LauncherUiAction.OpenEntryContextMenu -> dispatch(
                        LauncherAction.OpenEntryContextMenu(action.entryId),
                    )
                    LauncherUiAction.OpenAlphabetJump -> dispatch(LauncherAction.OpenAlphabetJump)
                    LauncherUiAction.DismissTransient -> dispatch(LauncherAction.DismissTransient)
                    is LauncherUiAction.PinEntry -> dispatch(LauncherAction.PinEntry(action.entryId))
                    is LauncherUiAction.UnpinTile -> dispatch(LauncherAction.UnpinTile(action.tileId))
                    is LauncherUiAction.AcknowledgeStartReveal -> dispatch(
                        LauncherAction.AcknowledgeStartReveal(action.tileId),
                    )
                    LauncherUiAction.UndoLayout -> dispatch(LauncherAction.UndoLayout)
                    is LauncherUiAction.UpdateSearchQuery -> dispatch(LauncherAction.UpdateSearchQuery(action.query))
                    is LauncherUiAction.ActivateTask -> dispatch(LauncherAction.ActivateTask(action.taskId))
                    is LauncherUiAction.ShowAppInfo -> context.openHostAppInfo()
                }
            }
            val transitionTarget = engineState.motionTarget()
            Column(
                Modifier.fillMaxSize().background(colors.background)
                    .semantics { testTagsAsResourceId = true },
            ) {
                Box(Modifier.weight(1f)) {
                    val recoveryAtStart = engineState.surface == ShellVisualSurface.Desktop &&
                        engineState.recoveryMode != LauncherRecoveryMode.NORMAL
                    if (recoveryAtStart) {
                        LauncherRecoverySurface(
                            restoring = engineState.recoveryMode == LauncherRecoveryMode.RESTORING,
                            onOpenChart = { dispatch(LauncherAction.Open(com.yokuli.marine.feature.chart.ChartDestinations.Browse)) },
                            onOpenSettings = { dispatch(LauncherAction.Open(SettingsDestinations.Overview)) },
                            onResetStart = shellViewModel::resetLauncher,
                            onOpenAndroidSettings = shellViewModel::requestAndroidSettings,
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            WpStatusStrip { dispatch(LauncherAction.Open(SettingsDestinations.Overview)) }
                            WpSurfaceTransitionHost(
                                targetState = transitionTarget,
                                intent = engineState.transitionIntent.toWpIntent(),
                                reducedMotion = reducedMotion,
                                timings = motionTimings,
                                modifier = Modifier.weight(1f),
                            ) { target, heavyContentReady ->
                                when (target) {
                                ShellMotionTarget.Launcher -> InteractiveLauncherPager(
                                    requestedPage = if (engineState.surface == ShellVisualSurface.ModuleList) {
                                        LauncherPagerPage.ALL_APPS
                                    } else {
                                        LauncherPagerPage.START
                                    },
                                    userScrollEnabled = !startEditing,
                                    onPageSettled = { page ->
                                        dispatch(
                                            if (page == LauncherPagerPage.START) LauncherAction.ShowStart
                                            else LauncherAction.ShowAllApps,
                                        )
                                    },
                                ) { page ->
                                    when (page) {
                                        LauncherPagerPage.START -> YokuliStartScreen(
                                            launcherState.copy(
                                                transient = engineState.transient.takeIf {
                                                    engineState.surface == ShellVisualSurface.Desktop
                                                },
                                            ),
                                            launcherAction,
                                        )
                                        LauncherPagerPage.ALL_APPS -> WpAppList(
                                            launcherState.copy(
                                                transient = engineState.transient.takeIf {
                                                    engineState.surface == ShellVisualSurface.ModuleList
                                                },
                                            ),
                                            launcherAction,
                                        )
                                    }
                                }
                                is ShellMotionTarget.App -> {
                                    val task = target
                                    CompositionLocalProvider(
                                        LocalProductionShellRuntime provides runtime.copy(
                                            heavyContentReady = heavyContentReady,
                                        ),
                                    ) {
                                        productionInternalAppHostResolver.hostFor(task.appId)?.Render(task.token)
                                            ?: error("No internal host for installed app: ${task.appId.value}")
                                    }
                                }
                                ShellMotionTarget.Recents -> WpRecentsSurface(
                                    tasks = engineState.tasks.tasks,
                                    entries = launcherState.entries,
                                    onActivate = { dispatch(LauncherAction.ActivateTask(it.taskId)) },
                                )
                                ShellMotionTarget.Search -> WpSearchSurface(
                                    state = launcherState,
                                    searchQuery = retainedSearchQuery,
                                    onAction = launcherAction,
                                )
                                }
                            }
                        }
                    }
                }
                // Canvas-backed launcher overlays can otherwise retain a stale draw layer above
                // the virtual hardware strip. Re-key the strip for both page and transient-plane
                // changes so Back / Start / Search remain the final visible input surface.
                key(engineState.surface, engineState.transient) {
                    WpSystemKeyBar(onInput = dispatchInput)
                }
            }
        }
    }
}

private fun LauncherTransitionIntent.toWpIntent(): WpNavigationIntent = when (this) {
    LauncherTransitionIntent.NONE -> WpNavigationIntent.SIBLING_FORWARD
    LauncherTransitionIntent.SIBLING_FORWARD -> WpNavigationIntent.SIBLING_FORWARD
    LauncherTransitionIntent.SIBLING_BACK -> WpNavigationIntent.SIBLING_BACK
    LauncherTransitionIntent.DEEPER_FORWARD -> WpNavigationIntent.DEEPER_FORWARD
    LauncherTransitionIntent.DEEPER_BACK -> WpNavigationIntent.DEEPER_BACK
    LauncherTransitionIntent.TRANSIENT -> WpNavigationIntent.TRANSIENT
}

private sealed interface ShellMotionTarget {
    data object Launcher : ShellMotionTarget
    data object Search : ShellMotionTarget
    data object Recents : ShellMotionTarget
    data class App(
        val taskId: InternalAppTaskId,
        val appId: LauncherAppId,
        val token: LaunchToken,
    ) : ShellMotionTarget
}

private fun com.yokuli.shell.engine.LauncherEngineState.motionTarget(): ShellMotionTarget = when (val current = surface) {
    ShellVisualSurface.Desktop,
    ShellVisualSurface.ModuleList -> ShellMotionTarget.Launcher
    is ShellVisualSurface.Search -> ShellMotionTarget.Search
    ShellVisualSurface.Recents -> ShellMotionTarget.Recents
    is ShellVisualSurface.Module -> {
        val task = requireNotNull(tasks.task(current.taskId))
        ShellMotionTarget.App(task.taskId, task.appId, task.lastLaunchToken)
    }
}

@Composable
private fun rememberPlatformReducedMotion(): Boolean {
    val context = LocalContext.current
    val reduced = remember {
        mutableStateOf(context.animatorDurationScale() == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = context.animatorDurationScale() == 0f
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced.value
}

private fun Context.animatorDurationScale(): Float = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}.getOrDefault(1f)

private fun Context.openHostAppInfo() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
}

private fun Context.openShellLab() {
    startActivity(Intent().setClassName(packageName, "com.yokuli.marine.feature.shell.lab.ShellLabActivity"))
}

@Composable
private fun SyncHostWindowChrome(background: Color, useDarkSystemIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
        }
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = useDarkSystemIcons
            isAppearanceLightNavigationBars = useDarkSystemIcons
        }
    }
}

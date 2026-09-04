package com.yokuli.marine.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.core.design.WpNavigationIntent
import com.yokuli.marine.core.design.WpSurfaceTransitionHost
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.feature.desktop.LauncherUiAction
import com.yokuli.marine.feature.desktop.InteractiveLauncherPager
import com.yokuli.marine.feature.desktop.LauncherPagerPage
import com.yokuli.marine.feature.desktop.WpAppList
import com.yokuli.marine.feature.desktop.WpStatusStrip
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.feature.desktop.productionLauncherUiState
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsSection
import com.yokuli.marine.feature.settings.SettingsUiAction
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEffect
import com.yokuli.shell.engine.LauncherSurface
import com.yokuli.shell.engine.LauncherTransitionIntent
import com.yokuli.shell.engine.interaction.StartInteractionState

class ShellActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bootstrapLegacyLocale()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { YokuliShell() }
    }
}

@Composable
private fun YokuliShell() {
    val context = LocalContext.current
    val shellViewModel = viewModel<ShellViewModel>()
    val engine = shellViewModel.engine
    val engineState by engine.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(engine, hapticFeedback) {
        engine.effects.collect { effect ->
            if (effect is LauncherEffect.Haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    var themeModeName by rememberSaveable { mutableStateOf(WpThemeMode.DARK.name) }
    var accentName by rememberSaveable { mutableStateOf(WpAccent.CYAN.name) }
    var settingsSectionName by rememberSaveable { mutableStateOf(SettingsSection.OVERVIEW.name) }
    val themeSpec = WpThemeSpec(WpThemeMode.valueOf(themeModeName), WpAccent.valueOf(accentName))
    val language = AppCompatDelegate.getApplicationLocales()[0]?.language.let {
        if (it == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
    }
    val dispatch: (LauncherAction) -> Unit = { action ->
        if (action is LauncherAction.Open) {
            SettingsDestinations.section(action.token)?.let { settingsSectionName = it.name }
        }
        engine.dispatch(action)
    }
    val settingsSubpageVisible = (engineState.surface as? LauncherSurface.InternalApp)?.let { surface ->
        engineState.tasks.task(surface.taskId)?.appId == SettingsDestinations.AppId &&
            SettingsSection.valueOf(settingsSectionName) != SettingsSection.OVERVIEW
    } == true
    val launcherEditing = engineState.start.interaction !is StartInteractionState.Idle
    BackHandler(engineState.surface != LauncherSurface.Start || launcherEditing) {
        if (settingsSubpageVisible) {
            settingsSectionName = SettingsSection.OVERVIEW.name
        } else {
            dispatch(LauncherAction.Back)
        }
    }
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
        val runtime = ProductionShellRuntime(
            mapConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
            theme = themeSpec,
            language = language,
            settingsSection = SettingsSection.valueOf(settingsSectionName),
            pinnedTileCount = engineState.start.document.placements.size,
            startDocumentVersion = engineState.start.document.defaultLayoutVersion,
            versionName = BuildConfig.VERSION_NAME,
            buildVariant = "${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}",
            gitSha = BuildConfig.GIT_SHA,
            debugShellLabAvailable = BuildConfig.DEBUG,
            openMapSettings = { dispatch(LauncherAction.Open(SettingsDestinations.Map)) },
            onSettingsAction = { action ->
                when (action) {
                    is SettingsUiAction.OpenSection -> settingsSectionName = action.section.name
                    is SettingsUiAction.ChangeTheme -> {
                        themeModeName = action.theme.mode.name
                        accentName = action.theme.accent.name
                    }
                    is SettingsUiAction.ChangeLanguage -> context.persistAppLanguage(action.language)
                    SettingsUiAction.ResetStartScreen -> dispatch(LauncherAction.ResetStartDocument)
                    SettingsUiAction.OpenShellLab -> if (BuildConfig.DEBUG) context.openShellLab()
                }
            },
        )
        CompositionLocalProvider(LocalProductionShellRuntime provides runtime) {
            val launcherState = productionLauncherUiState(
                catalog = engineState.catalog,
                document = engineState.start.document,
                interaction = engineState.start.interaction,
                mapConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
                theme = themeSpec,
                visualContributions = productionVisualContributions,
            )
            val startEditing = engineState.start.interaction !is StartInteractionState.Idle
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
                    is LauncherUiAction.TogglePin -> dispatch(LauncherAction.TogglePin(action.entryId))
                    is LauncherUiAction.ShowAppInfo -> context.openHostAppInfo()
                }
            }
            Column(Modifier.fillMaxSize().background(colors.background)) {
                WpStatusStrip { dispatch(LauncherAction.Open(SettingsDestinations.Overview)) }
                WpSurfaceTransitionHost(
                    targetState = engineState.surface is LauncherSurface.InternalApp,
                    intent = engineState.transitionIntent.toWpIntent(),
                    modifier = Modifier.weight(1f),
                ) { internalAppVisible ->
                    if (internalAppVisible) {
                        val surface = engineState.surface
                        if (surface is LauncherSurface.InternalApp) {
                            val task = requireNotNull(engineState.tasks.task(surface.taskId))
                            productionInternalAppHostResolver.hostFor(task.appId)?.Render(task.lastLaunchToken)
                                ?: error("No internal host for installed app: ${task.appId.value}")
                        }
                    } else {
                        InteractiveLauncherPager(
                            requestedPage = if (engineState.surface == LauncherSurface.AllApps) {
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
                                    launcherState,
                                    launcherAction,
                                )
                                LauncherPagerPage.ALL_APPS -> WpAppList(launcherState, launcherAction)
                            }
                        }
                    }
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
}

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

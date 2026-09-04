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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yokuli.marine.adapter.chart.google.GoogleMarineChartSurface
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.core.design.WpNavigationIntent
import com.yokuli.marine.core.design.WpSurfaceTransitionHost
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.chart.ChartSurfaceKind
import com.yokuli.marine.feature.chart.ChartUiAction
import com.yokuli.marine.feature.chart.ChartUiState
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.chart.MarineChartDemoSurface
import com.yokuli.marine.feature.chart.MarineChartSurface
import com.yokuli.marine.feature.desktop.LauncherUiAction
import com.yokuli.marine.feature.desktop.WpAppList
import com.yokuli.marine.feature.desktop.WpStatusStrip
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.feature.desktop.productionLauncherUiState
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsSection
import com.yokuli.marine.feature.settings.SettingsUiAction
import com.yokuli.marine.feature.settings.SettingsUiState
import com.yokuli.marine.feature.settings.SettingsWorkspace
import com.yokuli.shell.android.DefaultInternalAppHostResolver
import com.yokuli.shell.compose.InternalAppHost
import com.yokuli.shell.engine.layout.DesktopLayoutEditor
import com.yokuli.shell.engine.navigation.ShellCommand
import com.yokuli.shell.engine.navigation.ShellNavigationState
import com.yokuli.shell.engine.navigation.ShellNavigator
import com.yokuli.shell.engine.navigation.ShellSurface
import kotlinx.coroutines.launch

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
    val navigator = remember { ShellNavigator(productionHostPort) }
    val scope = rememberCoroutineScope()
    var navigation by remember { mutableStateOf(ShellNavigationState()) }
    var desktopDocument by remember { mutableStateOf(defaultDesktopDocument) }
    var transitionIntent by remember { mutableStateOf(WpNavigationIntent.SIBLING_FORWARD) }
    var themeModeName by rememberSaveable { mutableStateOf(WpThemeMode.DARK.name) }
    var accentName by rememberSaveable { mutableStateOf(WpAccent.CYAN.name) }
    var settingsSectionName by rememberSaveable { mutableStateOf(SettingsSection.OVERVIEW.name) }
    val themeSpec = WpThemeSpec(WpThemeMode.valueOf(themeModeName), WpAccent.valueOf(accentName))
    val language = AppCompatDelegate.getApplicationLocales()[0]?.language.let {
        if (it == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
    }
    val dispatch: (ShellCommand) -> Unit = { command ->
        if (command is ShellCommand.Open) {
            SettingsDestinations.section(command.token)?.let { settingsSectionName = it.name }
        }
        transitionIntent = when (command) {
            ShellCommand.ShowAllApps -> WpNavigationIntent.SIBLING_FORWARD
            ShellCommand.Back -> if (navigation.surface == ShellSurface.AllApps) {
                WpNavigationIntent.SIBLING_BACK
            } else {
                WpNavigationIntent.DEEPER_BACK
            }
            is ShellCommand.Open -> WpNavigationIntent.DEEPER_FORWARD
        }
        scope.launch { navigation = navigator.reduce(navigation, command) }
    }
    val settingsSubpageVisible = (navigation.surface as? ShellSurface.App)?.let { surface ->
        navigation.tasks.firstOrNull { it.id == surface.taskId }?.appId == SettingsDestinations.AppId &&
            SettingsSection.valueOf(settingsSectionName) != SettingsSection.OVERVIEW
    } == true
    BackHandler(navigation.surface != ShellSurface.Start) {
        if (settingsSubpageVisible) {
            settingsSectionName = SettingsSection.OVERVIEW.name
        } else {
            dispatch(ShellCommand.Back)
        }
    }

    YokuliTheme(themeSpec) {
        val colors = LocalWpTheme.current
        SyncHostWindowChrome(colors.background, themeSpec.mode == WpThemeMode.LIGHT)
        Column(Modifier.fillMaxSize().background(colors.background)) {
            WpStatusStrip { dispatch(ShellCommand.Open(SettingsDestinations.Overview)) }
            WpSurfaceTransitionHost(
                targetState = navigation.surface,
                intent = transitionIntent,
                modifier = Modifier.weight(1f),
            ) { surface ->
                val launcherState = productionLauncherUiState(
                    catalog = productionCatalog.snapshot,
                    document = desktopDocument,
                    mapConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
                    theme = themeSpec,
                )
                val launcherAction: (LauncherUiAction) -> Unit = { action ->
                    when (action) {
                        is LauncherUiAction.Open -> dispatch(ShellCommand.Open(action.token))
                        LauncherUiAction.ShowAllApps -> dispatch(ShellCommand.ShowAllApps)
                        is LauncherUiAction.ChangeDocument -> desktopDocument = action.document
                        is LauncherUiAction.TogglePin -> {
                            val pinned = desktopDocument.placements.firstOrNull { it.entryId == action.entryId }
                            val transaction = if (pinned == null) {
                                DesktopLayoutEditor.pin(desktopDocument, action.entryId, productionCatalog.entries)
                            } else {
                                DesktopLayoutEditor.unpin(desktopDocument, pinned.tileId)
                            }
                            transaction?.let { desktopDocument = it.after }
                        }
                        is LauncherUiAction.ShowAppInfo -> context.openHostAppInfo()
                    }
                }
                when (surface) {
                    ShellSurface.Start -> SwipeSurface(onSwipeLeft = { dispatch(ShellCommand.ShowAllApps) }) {
                        YokuliStartScreen(launcherState, launcherAction)
                    }
                    ShellSurface.AllApps -> SwipeSurface(onSwipeRight = { dispatch(ShellCommand.Back) }) {
                        WpAppList(launcherState, launcherAction)
                    }
                    is ShellSurface.App -> {
                        val task = navigation.tasks.first { it.id == surface.taskId }
                        val internalAppHostResolver = DefaultInternalAppHostResolver(
                            listOf(
                                InternalAppHost(ChartDestinations.AppId) { token ->
                                    check(token == ChartDestinations.Browse)
                                    val chartSurface: MarineChartSurface = if (BuildConfig.GOOGLE_MAPS_CONFIGURED) {
                                        { modifier ->
                                            GoogleMarineChartSurface(
                                                darkMode = themeSpec.mode == WpThemeMode.DARK,
                                                modifier = modifier.testTag("chart-surface-google"),
                                            )
                                        }
                                    } else {
                                        { modifier -> MarineChartDemoSurface(modifier.testTag("chart-surface-demo")) }
                                    }
                                    ChartWorkspace(
                                        state = ChartUiState(
                                            surfaceKind = if (BuildConfig.GOOGLE_MAPS_CONFIGURED) {
                                                ChartSurfaceKind.GOOGLE_MAPS
                                            } else {
                                                ChartSurfaceKind.DEMO
                                            },
                                            mapConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
                                        ),
                                        onAction = { action ->
                                            if (action == ChartUiAction.OpenMapSettings) {
                                                dispatch(ShellCommand.Open(SettingsDestinations.Map))
                                            }
                                        },
                                        chartSurface = chartSurface,
                                    )
                                },
                                InternalAppHost(SettingsDestinations.AppId) { token ->
                                    val tokenSection = SettingsDestinations.section(token)
                                        ?: error("Unknown Settings launch token: ${token.value}")
                                    val section = SettingsSection.valueOf(settingsSectionName).let { remembered ->
                                        if (remembered == SettingsSection.OVERVIEW) tokenSection else remembered
                                    }
                                    SettingsWorkspace(
                                        state = SettingsUiState(
                                            section = section,
                                            theme = themeSpec,
                                            language = language,
                                            mapConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED,
                                            pinnedTileCount = desktopDocument.placements.size,
                                            desktopDocumentVersion = desktopDocument.version,
                                            versionName = BuildConfig.VERSION_NAME,
                                            buildVariant = "${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}",
                                            gitSha = BuildConfig.GIT_SHA,
                                            debugShellLabAvailable = BuildConfig.DEBUG,
                                        ),
                                        onAction = { action ->
                                            when (action) {
                                                is SettingsUiAction.OpenSection -> settingsSectionName = action.section.name
                                                is SettingsUiAction.ChangeTheme -> {
                                                    themeModeName = action.theme.mode.name
                                                    accentName = action.theme.accent.name
                                                }
                                                is SettingsUiAction.ChangeLanguage -> context.persistAppLanguage(action.language)
                                                SettingsUiAction.ResetStartScreen -> desktopDocument = defaultDesktopDocument
                                                SettingsUiAction.OpenShellLab -> if (BuildConfig.DEBUG) context.openShellLab()
                                            }
                                        },
                                    )
                                },
                            ),
                        )
                        internalAppHostResolver.hostFor(task.appId)?.Render(task.token)
                            ?: error("No internal host for installed app: ${task.appId.value}")
                    }
                }
            }
        }
    }
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

/**
 * 中文：Phase 0A 保留基础左右入口；带进度、速度与取消规则的统一 pager 属于 S3。
 * English: Phase 0A keeps basic edge navigation; the progress/velocity/cancel pager belongs to S3.
 */
@Composable
private fun SwipeSurface(
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize().pointerInput(onSwipeLeft, onSwipeRight) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val threshold = size.width * .18f
                    if (drag < -threshold) onSwipeLeft?.invoke()
                    if (drag > threshold) onSwipeRight?.invoke()
                    drag = 0f
                },
                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
            )
        },
    ) { content() }
}

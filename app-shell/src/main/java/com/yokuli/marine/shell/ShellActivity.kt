package com.yokuli.marine.shell

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.core.design.WpNavigationIntent
import com.yokuli.marine.core.design.WpSurfaceTransitionHost
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.core.model.*
import com.yokuli.marine.core.shell.ShellNavigator
import com.yokuli.marine.core.shell.DesktopLayoutEditor
import com.yokuli.marine.core.shell.LauncherRegistry
import com.yokuli.marine.feature.chart.*
import com.yokuli.marine.feature.cockpit.*
import com.yokuli.marine.feature.desktop.*
import com.yokuli.marine.feature.library.*
import com.yokuli.marine.feature.system.*

class ShellActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bootstrapLegacyLocale()
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
    val navigator = remember { ShellNavigator() }
    var navigation by remember { mutableStateOf(ShellNavigationState()) }
    var desktopLayout by remember { mutableStateOf(LauncherRegistry.defaultLayout) }
    var transitionIntent by remember { mutableStateOf(WpNavigationIntent.SIBLING_FORWARD) }
    var themeModeName by rememberSaveable { mutableStateOf(WpThemeMode.DARK.name) }
    var accentName by rememberSaveable { mutableStateOf(WpAccent.CYAN.name) }
    val themeSpec = WpThemeSpec(
        mode = WpThemeMode.valueOf(themeModeName),
        accent = WpAccent.valueOf(accentName),
    )
    val language = AppCompatDelegate.getApplicationLocales()[0]?.language.let {
        if (it == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
    }
    val dispatch: (ShellCommand) -> Unit = { command ->
        transitionIntent = when (command) {
            ShellCommand.ShowAllApps -> WpNavigationIntent.SIBLING_FORWARD
            ShellCommand.Back -> if (navigation.surface == ShellSurface.AllApps) {
                WpNavigationIntent.SIBLING_BACK
            } else {
                WpNavigationIntent.DEEPER_BACK
            }
            ShellCommand.Home -> WpNavigationIntent.DEEPER_BACK
            is ShellCommand.Open -> when (command.target) {
                LaunchTarget.AllApps -> WpNavigationIntent.SIBLING_FORWARD
                LaunchTarget.Desktop -> WpNavigationIntent.DEEPER_BACK
                else -> WpNavigationIntent.DEEPER_FORWARD
            }
        }
        navigation = navigator.reduce(navigation, command)
    }
    BackHandler(navigation.surface != ShellSurface.Start) { dispatch(ShellCommand.Back) }

    YokuliTheme(themeSpec) {
        Column(Modifier.fillMaxSize().background(LocalWpTheme.current.background)) {
            WpStatusStrip { dispatch(ShellCommand.Open(LaunchTarget.System())) }
            WpSurfaceTransitionHost(
                targetState = navigation.surface,
                intent = transitionIntent,
                modifier = Modifier.weight(1f),
            ) { surface ->
                when (surface) {
                    ShellSurface.Start -> SwipeSurface(onSwipeLeft = { dispatch(ShellCommand.ShowAllApps) }) {
                        val launcherState = LauncherUiFixtures.state(desktopLayout)
                        YokuliStartScreen(
                            state = launcherState,
                            onAction = { action ->
                                when (action) {
                                    is LauncherUiAction.Open -> dispatch(ShellCommand.Open(action.target))
                                    LauncherUiAction.ShowAllApps -> dispatch(ShellCommand.ShowAllApps)
                                    is LauncherUiAction.ChangeLayout -> desktopLayout = action.layout
                                    is LauncherUiAction.TogglePin -> Unit
                                }
                            },
                        )
                    }
                    ShellSurface.AllApps -> SwipeSurface(onSwipeRight = { dispatch(ShellCommand.Back) }) {
                        val launcherState = LauncherUiFixtures.state(desktopLayout)
                        WpAppList(
                            state = launcherState,
                            onAction = { action ->
                                when (action) {
                                    is LauncherUiAction.Open -> dispatch(ShellCommand.Open(action.target))
                                    LauncherUiAction.ShowAllApps -> Unit
                                    is LauncherUiAction.ChangeLayout -> desktopLayout = action.layout
                                    is LauncherUiAction.TogglePin -> {
                                        val pinned = desktopLayout.placements.firstOrNull { it.entryId == action.entryId }
                                        desktopLayout = if (pinned == null) {
                                            DesktopLayoutEditor.pin(desktopLayout, action.entryId)
                                        } else {
                                            DesktopLayoutEditor.unpin(desktopLayout, pinned.tileId)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    is ShellSurface.App -> {
                        val task = navigation.tasks.first { it.id == surface.taskId }
                        val home = { dispatch(ShellCommand.Home) }
                        when (val target = task.target) {
                            is LaunchTarget.Chart -> {
                                var mode by remember(task.id) { mutableStateOf(target.mode) }
                                ChartWorkspace(ChartUiFixtures.state(mode)) { action ->
                                    when (action) {
                                        is ChartUiAction.SelectMode -> mode = action.mode
                                        ChartUiAction.Home -> home()
                                    }
                                }
                            }
                            is LaunchTarget.Cockpit -> CockpitWorkspace(CockpitUiFixtures.state(target.page)) { action ->
                                if (action == CockpitUiAction.Home) home()
                            }
                            is LaunchTarget.Library -> {
                                var section by remember(task.id) { mutableStateOf(target.section) }
                                LibraryWorkspace(LibraryUiFixtures.state(section)) { action ->
                                    when (action) {
                                        is LibraryUiAction.SelectSection -> section = action.section
                                        LibraryUiAction.Home -> home()
                                    }
                                }
                            }
                            is LaunchTarget.System -> {
                                var section by remember(task.id) { mutableStateOf(target.section) }
                                SystemWorkspace(SystemUiFixtures.state(section, themeSpec, language)) { action ->
                                    when (action) {
                                        is SystemUiAction.OpenSection -> section = action.section
                                        is SystemUiAction.ChangeTheme -> {
                                            themeModeName = action.theme.mode.name
                                            accentName = action.theme.accent.name
                                        }
                                        is SystemUiAction.ChangeLanguage -> context.persistAppLanguage(action.language)
                                        SystemUiAction.Home -> home()
                                    }
                                }
                            }
                            LaunchTarget.AllApps, LaunchTarget.Desktop -> Unit
                        }
                    }
                }
            }
        }
    }
}

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
                    if (drag < -90f) onSwipeLeft?.invoke()
                    if (drag > 90f) onSwipeRight?.invoke()
                    drag = 0f
                },
                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
            )
        },
    ) { content() }
}

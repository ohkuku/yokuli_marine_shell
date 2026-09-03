package com.yokuli.marine.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yokuli.marine.core.design.YokuliColors
import com.yokuli.marine.core.design.WpNavigationIntent
import com.yokuli.marine.core.design.WpSurfaceTransitionHost
import com.yokuli.marine.core.model.*
import com.yokuli.marine.core.shell.ShellNavigator
import com.yokuli.marine.core.shell.DesktopLayoutEditor
import com.yokuli.marine.core.shell.LauncherRegistry
import com.yokuli.marine.feature.chart.ChartWorkspace
import com.yokuli.marine.feature.cockpit.CockpitWorkspace
import com.yokuli.marine.feature.desktop.WpAppList
import com.yokuli.marine.feature.desktop.WpStatusStrip
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.marine.feature.library.LibraryWorkspace
import com.yokuli.marine.feature.system.SystemWorkspace

class ShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val navigator = remember { ShellNavigator() }
    var navigation by remember { mutableStateOf(ShellNavigationState()) }
    var desktopLayout by remember { mutableStateOf(LauncherRegistry.defaultLayout) }
    var transitionIntent by remember { mutableStateOf(WpNavigationIntent.SIBLING_FORWARD) }
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

    Column(Modifier.fillMaxSize().background(YokuliColors.Black)) {
        WpStatusStrip { dispatch(ShellCommand.Open(LaunchTarget.System())) }
        WpSurfaceTransitionHost(
            targetState = navigation.surface,
            intent = transitionIntent,
            modifier = Modifier.weight(1f),
        ) { surface ->
            when (surface) {
                ShellSurface.Start -> SwipeSurface(onSwipeLeft = { dispatch(ShellCommand.ShowAllApps) }) {
                    YokuliStartScreen(
                        onOpen = { dispatch(ShellCommand.Open(it)) },
                        onAllApps = { dispatch(ShellCommand.ShowAllApps) },
                        layout = desktopLayout,
                        onLayoutChange = { desktopLayout = it },
                    )
                }
                ShellSurface.AllApps -> SwipeSurface(onSwipeRight = { dispatch(ShellCommand.Back) }) {
                    WpAppList(
                        onOpen = { dispatch(ShellCommand.Open(it)) },
                        pinnedEntries = desktopLayout.placements.map { it.entryId }.toSet(),
                        onPinToggle = { entryId ->
                            val pinned = desktopLayout.placements.firstOrNull { it.entryId == entryId }
                            desktopLayout = if (pinned == null) {
                                DesktopLayoutEditor.pin(desktopLayout, entryId)
                            } else {
                                DesktopLayoutEditor.unpin(desktopLayout, pinned.tileId)
                            }
                        },
                    )
                }
                is ShellSurface.App -> {
                    val task = navigation.tasks.first { it.id == surface.taskId }
                    val home = { dispatch(ShellCommand.Home) }
                    when (val target = task.target) {
                        is LaunchTarget.Chart -> ChartWorkspace(target.mode, home)
                        is LaunchTarget.Cockpit -> CockpitWorkspace(target.page, home)
                        is LaunchTarget.Library -> LibraryWorkspace(target.section, home)
                        is LaunchTarget.System -> SystemWorkspace(target.section, home)
                        LaunchTarget.AllApps, LaunchTarget.Desktop -> Unit
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

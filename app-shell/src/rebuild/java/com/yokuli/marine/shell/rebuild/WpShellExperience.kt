package com.yokuli.marine.shell.rebuild

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.core.design.*
import com.yokuli.marine.feature.desktop.*
import com.yokuli.marine.shell.rebuild.ui.*
import com.yokuli.shell.android.AndroidShellWindowMetrics
import com.yokuli.shell.compose.*
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.*
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.interaction.StartInteractionState
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

/** Uses the original production Shell composables and reducer without reimplementing gestures. */
@Composable
fun OsExperience(os: OsStore, service: (String, String?) -> Unit) {
    val shell = os.shell
    val state by shell.engine.state.collectAsState()
    os.marine?.let { marine ->
        val settingsReady by remember(marine) { marine.vm.ui.map { it.settingsReady }.distinctUntilChanged() }.collectAsState(false)
        LaunchedEffect(os.chinese, marine, settingsReady) { marine.syncLanguage() }
        LaunchedEffect(marine) {
            marine.vm.navigationRequests.collect { destination ->
                // The embedded app owns its own nested pages. Notifications and alarm banners
                // can also request a destination while the user is in Start or another app.
                val embeddedPages = setOf("anchor", "anchorages", "sonar", "voyages", "instruments", "nmea", "marine-settings", "sources", "output")
                if (os.page !in embeddedPages) {
                    os.open(when (destination) {
                        0 -> "anchor"
                        1 -> "instruments"
                        2 -> when (marine.vm.ui.value.dataSection) {
                            0 -> "sources"
                            1 -> "nmea"
                            2 -> "output"
                            else -> "sonar"
                        }
                        else -> "marine-settings"
                    })
                }
            }
        }
    }
    val hostContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(baseConfiguration, os.chinese) {
        Configuration(baseConfiguration).apply { setLocale(if (os.chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH) }
    }
    val localizedContext = remember(hostContext, configuration) {
        ShellLanguageContext(hostContext, hostContext.createConfigurationContext(configuration).resources)
    }
    val theme = WpThemeSpec(
        if (os.light) WpThemeMode.LIGHT else WpThemeMode.DARK,
        WpAccent.entries.firstOrNull { it.argb == os.accent } ?: WpAccent.CYAN,
    )
    val colors = WpThemePolicy.resolve(theme).copy(accent = Color(os.accent))
    val metrics = rememberShellWindowMetrics()
    val systemMotionDisabled = rememberPlatformReducedMotion()
    val reducedMotion = os.reduceMotion || systemMotionDisabled
    val motionProfile = WpReferenceProfiles.require(state.start.document.profileId).motion
    val timings = remember(motionProfile) {
        WpMotionTimings(
            pageSettleVisibleWindowMillis = motionProfile.measuredPageSettleMillis ?: 700,
            appOpenVisibleWindowMillis = motionProfile.measuredAppOpenMillis ?: 1_000,
            backReturnVisibleWindowMillis = motionProfile.measuredBackReturnMillis ?: 750,
        )
    }
    BackHandler { shell.back() }
    val view = LocalView.current
    LaunchedEffect(shell, view) {
        shell.engine.effects.collect { effect ->
            if (effect is LauncherEffect.Haptic) view.performHapticFeedback(when (effect.kind) {
                LauncherHaptic.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
                LauncherHaptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
                LauncherHaptic.DROP -> HapticFeedbackConstants.CONTEXT_CLICK
            })
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, shell) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) shell.engine.dispatch(LauncherAction.CancelTileOperation)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(
        LocalWpTheme provides colors,
        LocalReducedMotion provides reducedMotion,
        LocalInternalAppInputRouter provides shell.inputRouter,
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
    ) {
        var retainedQuery by remember { mutableStateOf("") }
        val query = (state.surface as? ShellVisualSurface.Search)?.query
        LaunchedEffect(query) { if (query != null) retainedQuery = query }
        val launcher = productionLauncherUiState(
            catalog = state.catalog,
            document = state.start.document,
            interaction = state.start.interaction,
            transient = state.transient,
            reveal = state.start.reveal,
            visualContributions = shell.apps.map { app -> shellVisual(os, app) },
            searchResults = searchContributions(os, query ?: retainedQuery),
        )
        val dispatch = shell.engine::dispatch
        val launcherAction: (LauncherUiAction) -> Unit = { action ->
            when (action) {
                is LauncherUiAction.Open -> dispatch(LauncherAction.Open(action.token))
                LauncherUiAction.ShowAllApps -> dispatch(LauncherAction.ShowAllApps)
                is LauncherUiAction.ProposeLayout -> dispatch(LauncherAction.ApplyLayoutProposal(action.proposal))
                is LauncherUiAction.EnterStartEdit -> dispatch(LauncherAction.EnterStartEdit(action.tileId))
                is LauncherUiAction.SelectStartTile -> dispatch(LauncherAction.SelectStartTile(action.tileId))
                LauncherUiAction.ExitStartEdit -> dispatch(LauncherAction.ExitStartEdit)
                is LauncherUiAction.BeginTileDrag -> dispatch(LauncherAction.BeginTileDrag(action.tileId, action.pointerId, action.grabOffset))
                is LauncherUiAction.InsertionTargetChanged -> dispatch(LauncherAction.InsertionTargetChanged(action.tileId, action.insertionIndex))
                is LauncherUiAction.TileCellTargetChanged -> dispatch(LauncherAction.TileCellTargetChanged(action.tileId, action.targetCell, action.columns))
                is LauncherUiAction.DropTile -> dispatch(LauncherAction.DropTile(action.tileId))
                LauncherUiAction.CancelTileOperation -> dispatch(LauncherAction.CancelTileOperation)
                is LauncherUiAction.ResizeTile -> dispatch(LauncherAction.ResizeTile(action.tileId))
                is LauncherUiAction.MoveTileBy -> dispatch(LauncherAction.MoveTileBy(action.tileId, action.columns, action.rows))
                is LauncherUiAction.OpenEntryContextMenu -> dispatch(LauncherAction.OpenEntryContextMenu(action.entryId))
                LauncherUiAction.OpenAlphabetJump -> dispatch(LauncherAction.OpenAlphabetJump)
                LauncherUiAction.DismissTransient -> dispatch(LauncherAction.DismissTransient)
                is LauncherUiAction.PinEntry -> dispatch(LauncherAction.PinEntry(action.entryId))
                is LauncherUiAction.UnpinTile -> dispatch(LauncherAction.UnpinTile(action.tileId))
                is LauncherUiAction.AcknowledgeStartReveal -> dispatch(LauncherAction.AcknowledgeStartReveal(action.tileId))
                LauncherUiAction.UndoLayout -> dispatch(LauncherAction.UndoLayout)
                is LauncherUiAction.UpdateSearchQuery -> dispatch(LauncherAction.UpdateSearchQuery(action.query))
                is LauncherUiAction.ActivateTask -> dispatch(LauncherAction.ActivateTask(action.taskId))
                is LauncherUiAction.CloseTask -> dispatch(LauncherAction.CloseTask(action.taskId))
                is LauncherUiAction.ShowAppInfo -> hostContext.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${hostContext.packageName}")))
            }
        }
        Column(Modifier.fillMaxSize().background(colors.background).testTag("shell-host")
            .semantics { testTagsAsResourceId = true }) {
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    WpStatusStrip(windowMetrics = metrics)
                    WpSurfaceTransitionHost(
                        targetState = state.motionTarget(),
                        transitionKind = state.transitionRequest?.kind.toWpKind(),
                        reducedMotion = reducedMotion,
                        timings = timings,
                        modifier = Modifier.weight(1f),
                    ) { target, heavyContentReady ->
                        when (target) {
                            ShellMotionTarget.Launcher -> InteractiveLauncherPager(
                                requestedPage = if (state.surface == ShellVisualSurface.ModuleList) LauncherPagerPage.ALL_APPS else LauncherPagerPage.START,
                                userScrollEnabled = state.start.interaction is StartInteractionState.Idle,
                                programmaticSettleMillis = timings.pageSettleVisibleWindowMillis,
                                reducedMotion = reducedMotion,
                                onPageSettled = { dispatch(if (it == LauncherPagerPage.START) LauncherAction.ShowStart else LauncherAction.ShowAllApps) },
                            ) { page ->
                                if (page == LauncherPagerPage.START) YokuliStartScreen(
                                    launcher.copy(transient = state.transient.takeIf { state.surface == ShellVisualSurface.Desktop }), launcherAction,
                                ) else WpAppList(
                                    launcher.copy(transient = state.transient.takeIf { state.surface == ShellVisualSurface.ModuleList }), launcherAction,
                                )
                            }
                            is ShellMotionTarget.App -> {
                                val page = shell.pageForToken(target.token)
                                if (heavyContentReady || page != "chart") ShellAppContent(os, page, service)
                                else Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
                                    Glyph("chart", Modifier.size(60.dp), colors.accent)
                                }
                            }
                            ShellMotionTarget.Search -> WpSearchSurface(launcher, retainedQuery, launcherAction)
                            ShellMotionTarget.Recents -> WpRecentsSurface(
                                state.tasks.tasks, launcher.entries,
                                onActivate = { dispatch(LauncherAction.ActivateTask(it.taskId)) },
                                onClose = { dispatch(LauncherAction.CloseTask(it.taskId)) },
                            )
                        }
                    }
                }
                os.toast?.let { message ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth()
                        .background(colors.accent).clickable { os.toast = null }.padding(16.dp)) {
                        Label(message, 17, Color.White)
                    }
                }
                os.marine?.let { marine ->
                    com.yokuli.anchorwatch.LegacyMarineAlerts(marine.vm, os.chinese, colors.accent, os.light)
                }
            }
            if (os.storageError) Label(os.t("存储失败，改动尚未保存", "Storage error. Changes have not been saved."), 13, colors.warning, Modifier.padding(8.dp))
            key(state.surface, state.transient) {
                WpSystemKeyBar(windowMetrics = metrics, onInput = shell::input)
            }
        }
    }
}

@Composable
private fun ShellAppContent(os: OsStore, page: String, service: (String, String?) -> Unit) {
    when {
        page == "chart" -> ChartAppScreen(os)
        page == "library" -> LibraryScreen(os)
        page.startsWith("library:") -> LibraryFolderScreen(os, page.substringAfter(':'))
        page == "places" -> PlacesScreen(os)
        page.startsWith("place:") -> PlaceScreen(os, page.substringAfter(':'))
        page.startsWith("route:") -> RouteScreen(os, page.substringAfter(':'))
        page == "data" -> DataScreen(os, service)
        page == "nmea" -> NmeaScreen(os, service)
        page == "settings" -> SettingsScreen(os)
        else -> MarineAppScreen(os, page)
    }
}

@Composable
private fun shellVisual(os: OsStore, app: ShellApp): LauncherEntryVisualContribution {
    val title = os.title(app.app)
    val detail = when (app.app.name) {
        "CHART" -> os.t("船位 · 标记 · 测距", "position · marks · distance")
        "LIBRARY" -> os.t("文件夹与海图图层", "folders and chart layers")
        "PLACES" -> os.t("${os.places.size} 个标记 · ${os.routes.size} 条航线", "${os.places.size} marks · ${os.routes.size} routes")
        "SETTINGS" -> os.t("让它成为你的", "make it yours")
        else -> ""
    }
    return LauncherEntryVisualContribution(
        app.entry, title,
        when (app.app.name) {
            "CHART", "LIBRARY", "TRIP", "VOYAGES" -> 'H'
            "PLACES" -> 'W'
            "DATA" -> 'C'
            "SETTINGS" -> 'S'
            "ANCHOR", "ANCHORAGES" -> 'M'
            "SONAR" -> 'G'
            "INSTRUMENTS" -> 'Y'
            else -> app.app.en.first().uppercaseChar()
        },
        title, detail,
        LauncherIconRenderer { tint, modifier -> ShellAppIcon(app, tint, modifier) },
        app.sizes.associateWith { size -> LauncherTileRenderer { context ->
            if (app.app.name == "CHART" && size == MarineTileSize.WIDE_4X2) {
                Row(context.modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxHeight().width(116.dp), contentAlignment = Alignment.Center) {
                        ShellAppIcon(app, context.contentColor, Modifier.size(54.dp))
                    }
                    Column(Modifier.fillMaxHeight().weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        WpText(title, 12, color = context.contentColor, maxLines = 1)
                        WpText(os.t("浏览海图", "browse charts"), 23, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
                        WpText(detail, 12, color = context.contentColor.copy(alpha = .84f), maxLines = 2)
                    }
                }
            } else if (app.app.name == "CHART" && size == MarineTileSize.STANDARD_2X2) {
                Column(context.modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShellAppIcon(app, context.contentColor, Modifier.size(28.dp))
                        WpText(title, 12, color = context.contentColor, maxLines = 1, modifier = Modifier.padding(start = 7.dp))
                    }
                    WpText(os.t("浏览海图", "browse charts"), 20, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
                    WpText(detail, 11, color = context.contentColor.copy(alpha = .84f), maxLines = 2)
                }
            } else Box(context.modifier.fillMaxSize()) {
                if (size == MarineTileSize.ICON_1X1) {
                    ShellAppIcon(app, context.contentColor, Modifier.size(44.dp).align(Alignment.Center))
                } else {
                    ShellAppIcon(app, context.contentColor, Modifier.size(42.dp).align(Alignment.TopStart))
                    Column(Modifier.align(Alignment.BottomStart)) {
                        WpText(title, if (size == MarineTileSize.WIDE_4X2) 26 else 18, color = context.contentColor, weight = FontWeight.Light, maxLines = 1)
                        if (size == MarineTileSize.WIDE_4X2 && detail.isNotBlank()) WpText(detail, 12, color = context.contentColor, maxLines = 1)
                    }
                }
            }
        } },
    )
}

/** Original app icon paths, shared by tiles and alphabetic app rows. */
@Composable
private fun ShellAppIcon(app: ShellApp, color: Color, modifier: Modifier) {
    if (app.app.name !in setOf("CHART", "LIBRARY", "PLACES", "DATA", "SETTINGS")) {
        Glyph(app.app.icon, modifier, color)
        return
    }
    Canvas(modifier) {
        val unit = minOf(size.width, size.height)
        val stroke = unit * .075f
        when (app.app.name) {
            "CHART" -> {
                drawCircle(color, unit * .31f, center, style = Stroke(stroke))
                drawLine(color, Offset(center.x, center.y - unit * .42f), Offset(center.x, center.y + unit * .42f), stroke)
                drawLine(color, Offset(center.x - unit * .42f, center.y), Offset(center.x + unit * .42f, center.y), stroke)
                drawPath(Path().apply {
                    moveTo(center.x + unit * .08f, center.y - unit * .30f)
                    lineTo(center.x - unit * .04f, center.y + unit * .11f)
                    lineTo(center.x + unit * .18f, center.y - unit * .03f)
                    close()
                }, color)
            }
            "SETTINGS" -> {
                drawCircle(color, unit * .34f, style = Stroke(unit * .12f))
                drawCircle(color, unit * .09f)
            }
            "LIBRARY" -> {
                repeat(3) { index ->
                    val inset = unit * (.12f + index * .08f)
                    drawRect(color, Offset(inset, inset), androidx.compose.ui.geometry.Size(unit * .68f, unit * .58f), style = Stroke(unit * .065f))
                }
                drawLine(color, Offset(unit * .30f, unit * .42f), Offset(unit * .62f, unit * .26f), unit * .065f)
                drawLine(color, Offset(unit * .30f, unit * .42f), Offset(unit * .58f, unit * .55f), unit * .065f)
            }
            "PLACES" -> {
                drawCircle(color, unit * .4f, center, style = Stroke(unit * .06f))
                drawLine(color, Offset(center.x, unit * .10f), Offset(center.x - unit * .11f, center.y + unit * .08f), unit * .07f, StrokeCap.Round)
                drawLine(color, Offset(center.x - unit * .11f, center.y + unit * .08f), center, unit * .07f, StrokeCap.Round)
                drawCircle(color, unit * .055f, center)
            }
            "DATA" -> {
                val points = listOf(Offset(unit * .18f, unit * .25f), Offset(unit * .18f, unit * .75f), Offset(unit * .5f, unit * .5f), Offset(unit * .82f, unit * .25f), Offset(unit * .82f, unit * .75f))
                drawLine(color, points[0], points[2], stroke, StrokeCap.Round)
                drawLine(color, points[1], points[2], stroke, StrokeCap.Round)
                drawLine(color, points[2], points[3], stroke, StrokeCap.Round)
                drawLine(color, points[2], points[4], stroke, StrokeCap.Round)
                points.forEach { drawCircle(color, unit * .07f, it) }
            }
        }
    }
}

private fun searchContributions(os: OsStore, query: String): List<LauncherSearchResultContribution> {
    if (query.isBlank()) return emptyList()
    return os.places.filter { it.name.contains(query, true) }.map {
        LauncherSearchResultContribution("place-${it.id}", it.name, coordinates(it.point), LaunchToken("place:${it.id}"))
    } + os.routes.filter { it.name.contains(query, true) }.map {
        LauncherSearchResultContribution("route-${it.id}", it.name, nm(it.length), LaunchToken("route:${it.id}"))
    }
}

private class ShellLanguageContext(base: Context, private val localizedResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

@Composable
private fun rememberShellWindowMetrics(): ShellWindowMetrics {
    val view = LocalView.current
    var metrics by remember(view) { mutableStateOf(AndroidShellWindowMetrics.read(view)) }
    DisposableEffect(view) {
        var latestInsets = ViewCompat.getRootWindowInsets(view) ?: WindowInsetsCompat.Builder().build()
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            latestInsets = insets
            metrics = AndroidShellWindowMetrics.read(target, insets)
            insets
        }
        val listener = View.OnLayoutChangeListener { target, _, _, _, _, _, _, _, _ -> metrics = AndroidShellWindowMetrics.read(target, latestInsets) }
        view.addOnLayoutChangeListener(listener)
        ViewCompat.requestApplyInsets(view)
        onDispose {
            view.removeOnLayoutChangeListener(listener)
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }
    return metrics
}

@Composable
private fun rememberPlatformReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private sealed interface ShellMotionTarget {
    data object Launcher : ShellMotionTarget
    data object Search : ShellMotionTarget
    data object Recents : ShellMotionTarget
    data class App(val taskId: InternalAppTaskId, val token: LaunchToken) : ShellMotionTarget
}
private fun LauncherEngineState.motionTarget(): ShellMotionTarget = when (val current = surface) {
    ShellVisualSurface.Desktop, ShellVisualSurface.ModuleList -> ShellMotionTarget.Launcher
    is ShellVisualSurface.Search -> ShellMotionTarget.Search
    ShellVisualSurface.Recents -> ShellMotionTarget.Recents
    is ShellVisualSurface.Module -> tasks.task(current.taskId)!!.let { ShellMotionTarget.App(it.taskId, it.lastLaunchToken) }
}
private fun ShellTransitionKind?.toWpKind(): WpSurfaceTransitionKind =
    if (this == null) WpSurfaceTransitionKind.NONE else WpSurfaceTransitionKind.valueOf(name)

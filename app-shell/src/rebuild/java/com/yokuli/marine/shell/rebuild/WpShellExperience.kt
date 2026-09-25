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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.yokuli.shell.engine.interaction.StartInteractionState
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

/** Uses the original production Shell composables and reducer without reimplementing gestures. */
@Composable
fun OsExperience(os: OsStore) {
    val shell = os.shell
    val startBackdrop = rememberStartBackdrop(os)
    val state by shell.engine.state.collectAsState()
    val underlayFocus = remember { FocusRequester() }
    val shadeBlocked = os.notificationShade.blocksInput
    var wasShadeBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(shadeBlocked) {
        if (shadeBlocked && !wasShadeBlocked) underlayFocus.saveFocusedChild()
        if (!shadeBlocked && wasShadeBlocked) {
            if (!underlayFocus.restoreFocusedChild()) underlayFocus.requestFocus()
        }
        wasShadeBlocked = shadeBlocked
    }
    val savedTasks=rememberSaveableStateHolder()
    val savedKeys=remember {mutableMapOf<String,InternalAppTaskId>()}
    LaunchedEffect(state.tasks.retainedUiStateKeys) {
        os.maps.retainAisViews(state.tasks.retainedUiStateKeys.toSet())
        savedKeys.keys.toList().filter { it !in state.tasks.retainedUiStateKeys }
            .forEach {savedTasks.removeState(it);savedKeys.remove(it)}
    }
    os.marine?.let { marine ->
        val settingsReady by remember(marine) { marine.services.state.map { it.settingsReady }.distinctUntilChanged() }.collectAsState(false)
        LaunchedEffect(os.chinese, marine, settingsReady) { marine.syncLanguage() }
        LaunchedEffect(marine) {
            marine.services.feedback.destinations.collect { destination ->
                os.open(when(destination.name) {
                    "CHART" -> "chart"
                    "ANCHOR" -> "anchor"
                    "LOGBOOK" -> "voyages"
                    "INSTRUMENTS" -> "instruments"
                    "SOURCES" -> "data_center"
                    "NMEA" -> "nmea"
                    "LOCAL_NMEA" -> "local_nmea"
                    "DEPTH" -> "chart"
                    else -> "settings"
                })
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
    val colors = WpThemePolicy.resolve(theme, Color(os.accent))
    val metrics = rememberShellWindowMetrics()
    val systemMotionDisabled = rememberPlatformReducedMotion()
    val reducedMotion = systemMotionDisabled
    // 控件遵循 W10M；应用开合与 Home 保留用户喜欢的经典翻转关系。
    val timings = remember { WpMotionTimings() }
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
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val needsHeadingDisplay=lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        (!shadeBlocked && state.surface is ShellVisualSurface.Module && shell.appForPage(os.page)?.app in setOf(AppId.CHART,AppId.ANCHOR,AppId.INSTRUMENTS,AppId.DATA_CENTER,AppId.AIS))
    DisposableEffect(os.marine,needsHeadingDisplay) {
        val services=os.marine?.services
        val lease = if (needsHeadingDisplay) services?.display?.acquireMapHeading() else null
        onDispose { lease?.close() }
    }
    val needsSensorDisplay = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        (!shadeBlocked && state.surface is ShellVisualSurface.Module && shell.appForPage(os.page)?.app in setOf(AppId.INSTRUMENTS,AppId.DATA_CENTER))
    DisposableEffect(os.marine, needsSensorDisplay) {
        val services = os.marine?.services
        val lease = if (needsSensorDisplay) services?.display?.acquireInstruments() else null
        onDispose { lease?.close() }
    }
    DisposableEffect(lifecycleOwner, shell) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) shell.engine.dispatch(LauncherAction.CancelTileOperation)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(
        LocalWpTheme provides colors,
        LocalStartBackdrop provides startBackdrop,
        LocalReducedMotion provides reducedMotion,
        LocalInternalAppInputRouter provides shell.inputRouter,
        LocalShellHorizontalInsets provides shellHorizontalInsets(metrics),
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
            visualContributions = shell.apps.map { app -> tilePresentation(os,app,
                animate=!shadeBlocked && state.surface==ShellVisualSurface.Desktop && state.start.interaction is StartInteractionState.Idle && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && state.start.document.placements.any {it.entryId==app.entry}) } +
                // 旧样式仍在兼容目录中，必须提供视觉声明以通过 Shell 契约校验；
                // 应用列表/搜索另行只显示根入口，未固定样式不会启动动画。
                shell.presets.map {preset -> presetTilePresentation(os,preset,
                    animate=!shadeBlocked && state.surface==ShellVisualSurface.Desktop && state.start.interaction is StartInteractionState.Idle && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && state.start.document.placements.any {it.entryId==preset.entryId}) },
            searchResults = searchContributions(os, query ?: retainedQuery),
        )
        val dispatch = shell::dispatch
        val launcherAction: (LauncherUiAction) -> Unit = { action ->
            if (!shadeBlocked) when (action) {
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
                Column(Modifier.fillMaxSize().focusRequester(underlayFocus).focusRestorer().focusGroup()
                    .then(if (shadeBlocked) Modifier.clearAndSetSemantics { } else Modifier)) {
                    SystemStatusBar(os, metrics)
                    WpSurfaceTransitionHost(
                        targetState = state.motionTarget(),
                        transitionKind = when {
                            (state.surface as? ShellVisualSurface.Module)?.taskId?.let {it==shell.coldOpeningTask}==true -> WpSurfaceTransitionKind.DESKTOP_TO_MODULE
                            state.transitionRequest?.kind in setOf(ShellTransitionKind.DESKTOP_TO_MODULE,ShellTransitionKind.MODULE_LIST_TO_MODULE,ShellTransitionKind.SEARCH_TO_MODULE) -> WpSurfaceTransitionKind.TASK_ACTIVATE
                            else -> state.transitionRequest?.kind.toWpKind()
                        },
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
                                    onPersonalizeStart = {
                                        if(!shadeBlocked) {
                                            dispatch(LauncherAction.ExitStartEdit)
                                            os.openLinked("settings:start")
                                        }
                                    },
                                    personalizeStartLabel = os.t("背景与透明磁贴","Background & transparent tiles"),
                                ) else WpAppList(
                                    launcher.copy(entries=launcher.entries.filter {entry -> shell.apps.any {it.entry==entry.descriptor.entryId}},
                                        transient = state.transient.takeIf { state.surface == ShellVisualSurface.ModuleList }), launcherAction,
                                    onSearch = { if (!shadeBlocked) dispatch(LauncherAction.OpenSearch) },
                                )
                            }
                            is ShellMotionTarget.App -> {
                                val page = shell.pageForToken(target.token)
                                val app=shell.appForPage(page)
                                if(!heavyContentReady && target==state.motionTarget() && target.taskId==shell.coldOpeningTask && app!=null) AppLaunchCover(os,app)
                                else {
                                    val stateKey=target.instanceKey
                                    SideEffect {savedKeys[stateKey]=target.taskId}
                                    savedTasks.SaveableStateProvider(stateKey) {
                                        CompositionLocalProvider(LocalInternalAppInputEnabled provides (target==state.motionTarget() && !shadeBlocked),LocalInternalAppPageKey provides target.instanceKey,LocalAppPage provides page) {
                                        TaskCaptureHost(os,target.taskId,target.instanceKey,active=target==state.motionTarget() && heavyContentReady) {
                                            ShellAppContent(os,page)
                                        }
                                        }
                                    }
                                }
                            }
                            ShellMotionTarget.Search -> WpSearchSurface(launcher.copy(entries=launcher.entries.filter {entry -> shell.apps.any {it.entry==entry.descriptor.entryId}}), retainedQuery, launcherAction)
                            ShellMotionTarget.Recents -> TaskSwitcher(
                                os, state.tasks.tasks,
                                onActivate = { dispatch(LauncherAction.ActivateTask(it.taskId)) },
                                onClose = { dispatch(LauncherAction.CloseTask(it.taskId)) },
                            )
                        }
                    }
                }
                SystemMarineAlerts(os)
                NotificationCenter(os, Modifier.fillMaxSize(), metrics)
            }
            key(state.surface, state.transient) {
                WpSystemKeyBar(
                    windowMetrics = metrics,
                    onInput = shell::input,
                    onNotificationsClick = os.notificationShade::toggle,
                )
            }
        }
    }
}

@Composable
private fun ShellAppContent(os: OsStore, page: String) {
    when {
        page == "chart" -> ChartAppScreen(os)
        page.startsWith("chart:ais:") -> ChartAppScreen(os,page.substringAfterLast(':').toIntOrNull())
        page == "library" -> LibraryScreen(os)
        page.startsWith("library:") -> LibraryFolderScreen(os, page.substringAfter(':'))
        page == "places" || page.startsWith("places:") -> PlacesScreen(os,anchoragesOnly=page=="places:anchorages")
        page.startsWith("place:") -> PlaceScreen(os, page.substringAfter(':'))
        page.startsWith("route:") -> RouteScreen(os, page.substringAfter(':'))
        page.startsWith("anchorage:") -> SavedLocationScreen(os,page.substringAfter(':').toLongOrNull())
        page.startsWith("collection:") -> CollectionScreen(os,page.substringAfter(':').toLongOrNull())
        page == "instruments" || page.startsWith("instruments:") -> InstrumentsScreen(os, page.substringAfter(':', ""))
        page == "data_center" || page.startsWith("data_center:") -> DataCenterScreen(os, page.substringAfter(':', "").takeIf { it.isNotBlank() })
        page == "voyages" -> LogbookScreen(os)
        page.substringBefore(':') in setOf("voyage","replay","report") -> LogbookScreen(os,page.substringAfter(':').toLongOrNull())
        page == "anchor" || page.startsWith("anchor:") -> AnchorExperience(os,page.substringAfter(':',"watch"))
        page == "nmea" || page.startsWith("nmea:") -> NmeaScreen(os,page.substringAfter(':', ""))
        page == "ais" || page.startsWith("ais:") -> AisScreen(os,page.substringAfter(':', ""))
        page == "local_nmea" -> LocalNmeaScreen(os)
        page == "tiles" || page.startsWith("tiles:") -> TileLibraryScreen(os,page.substringAfter(':', "").takeIf {it.isNotBlank()})
        page == "settings" || page.startsWith("settings:") -> SettingsScreen(os,page.substringAfter(':',"overview"))
        else -> Column { PageHeader(os,os.t("页面已更新","page updated"));PageBody {
            Label(os.t("这个旧入口已不再使用。你的数据仍保留在所属应用中。","This older destination has moved. Your data remains in its app."),15)
            MetroButton(os.t("返回应用列表","open apps"),os::home,primary=true)
        } }
    }
}

/** Original app icon paths, shared by tiles and alphabetic app rows. */
@Composable
internal fun ShellAppIcon(app: ShellApp, color: Color, modifier: Modifier) {
    if (app.app.name !in setOf("CHART", "LIBRARY")) {
        Glyph(app.app.icon, modifier, color)
        return
    }
    Canvas(modifier) {
        val unit = minOf(size.width, size.height)
        val stroke = unit * .0625f
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

@Composable
private fun searchContributions(os: OsStore, query: String): List<LauncherSearchResultContribution> {
    val term=query.trim()
    if (term.isBlank()) return emptyList()
    val traffic=rememberAisTraffic(os)
    val aisApp=if(!os.title(AppId.AIS).contains(term,true)&&listOf("AIS","周围船舶","船舶交通","交通警戒","雷达","三维","CPA","TCPA","surrounding vessels","nearby traffic","radar","3D").any {it.contains(term,true)})listOf(
        LauncherSearchResultContribution("ais-application-search",os.title(AppId.AIS),os.t("周围船舶 · 雷达、三维与海图","surrounding vessels · radar, 3D and chart"),os.shell.apps.first {it.app==AppId.AIS}.rootToken),
    )else emptyList()
    val targets=traffic.targets.filter {target->
        listOf(target.displayName,target.staticData.name?.value.orEmpty(),target.staticData.callSign?.value.orEmpty(),aisNumber(target.mmsi)).any {it.contains(term,true)}
    }.take(80).map {target->
        LauncherSearchResultContribution("ais-target-${target.mmsi}",target.displayName,
            "AIS · ${aisNumber(target.mmsi)} · ${aisState(os,target)}",LaunchToken("ais:target:${target.mmsi}"))
    }
    return aisApp + targets + os.allPlaces.filter { it.name.contains(term, true) }.map {
        LauncherSearchResultContribution("place-${it.id}", it.name, os.formatCoordinates(it.point), LaunchToken("place:${it.id}"))
    } + os.routes.filter { it.name.contains(term, true) }.map {
        LauncherSearchResultContribution("route-${it.id}", it.name, os.formatDistance(it.length), LaunchToken("route:${it.id}"))
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
    data class App(val taskId: InternalAppTaskId, val token: LaunchToken, val instanceKey: String) : ShellMotionTarget
}
private fun LauncherEngineState.motionTarget(): ShellMotionTarget = when (val current = surface) {
    ShellVisualSurface.Desktop, ShellVisualSurface.ModuleList -> ShellMotionTarget.Launcher
    is ShellVisualSurface.Search -> ShellMotionTarget.Search
    ShellVisualSurface.Recents -> ShellMotionTarget.Recents
    is ShellVisualSurface.Module -> tasks.task(current.taskId)!!.let { ShellMotionTarget.App(it.taskId, it.lastLaunchToken, it.currentUiStateKey) }
}
private fun ShellTransitionKind?.toWpKind(): WpSurfaceTransitionKind =
    if (this == null) WpSurfaceTransitionKind.NONE else WpSurfaceTransitionKind.valueOf(name)

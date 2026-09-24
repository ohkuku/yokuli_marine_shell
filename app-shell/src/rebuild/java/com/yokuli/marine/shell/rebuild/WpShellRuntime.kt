package com.yokuli.marine.shell.rebuild

import com.yokuli.shell.android.StaticLauncherHostPort
import com.yokuli.shell.compose.InternalAppInputRouter
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.*
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement
import com.yokuli.shell.storage.ProtoDataStoreLauncherPersistence
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.shell.rebuild.ui.*
import androidx.compose.runtime.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.yield

/** The original Shell owns task history, Start editing and navigation. Apps only publish destinations. */
class WpShellRuntime(private val os: OsStore) {
    val inputRouter = InternalAppInputRouter()
    val apps = AppId.entries.map(::ShellApp)
    val presets = tilePresets()
    val appPreferenceRegistry = AppPreferenceRegistry.compose(apps.map {it.id}.toSet(),tilePreferenceContributions(apps))
    val snapshots = TaskSnapshotStore()
    /** 调用者与目标旧会话共享已有 Bitmap 引用；恢复页面时也恢复对应任务图。 */
    private val pageSnapshots = linkedMapOf<String, TaskSnapshot>()
    private val snapshotPageKeys = mutableMapOf<InternalAppTaskId, String>()
    private val chartPageStates = mutableMapOf<String, ChartInteractionSnapshot>()
    private var foregroundChartKey: String? = null
    private val visiblePageRoutes = mutableMapOf<String, String>()
    var coldOpeningTask by mutableStateOf<InternalAppTaskId?>(null)
        private set
    private val navigationQueue=Channel<LauncherAction>(Channel.UNLIMITED)
    val catalog = LauncherCatalogSnapshot(
        revision = 2,
        apps = apps.map { LauncherAppDescriptor(it.id, it.entry) },
        entries = apps.map { app ->
            LauncherEntryDescriptor(app.entry, app.id, app.rootToken, app.defaultSize,
                app.sizes, PinPolicy.PINNABLE)
        } + presets.map {preset ->
            val owner=apps.first {it.app==preset.app}
            LauncherEntryDescriptor(preset.entryId,owner.id,preset.launchToken,preset.defaultSize,preset.sizes,PinPolicy.PINNABLE)
        },
    )
    // These are the exact initial placements from codex/shell-map-contract. Existing original
    // launcher_state.pb documents retain their identity, position, pinning and custom sizes.
    val defaultDocument = StartDocument(
        schemaVersion = 2,
        profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
        defaultLayoutVersion = 2,
        placements = listOf(
            TilePlacement(TileInstanceId("tile-chart"), LauncherEntryId("chart"), MarineTileSize.WIDE_4X2, 0),
            TilePlacement(TileInstanceId("tile-settings"), LauncherEntryId("preferences"), MarineTileSize.ICON_1X1, 1024),
        ),
    )
    val persistence = ProtoDataStoreLauncherPersistence.create(
        os.context, os.scope, LauncherPersistedState(document = defaultDocument),
        productMigration = nineAppMigration(),
        installedEntryIds = catalog.entries.map {it.entryId}.toSet(),
    )
    private val host = StaticLauncherHostPort(
        catalog,
        apps.associate { it.rootToken to it.id },
        apps.map { app -> app.id to { token: LaunchToken -> appForPage(pageForToken(token))?.id == app.id } },
    )
    val engine: LauncherEngine = DefaultLauncherEngine(host, persistence, defaultDocument, os.scope)

    init {
        os.scope.launch {
            for(action in navigationQueue) {
                val current=(engine.state.value.surface as? ShellVisualSurface.Module)?.taskId
                // 只有离开应用才拍最近任务图；应用内进退页不等待 PixelCopy，也不切换截图。
                if(current!=null && leavesTask(action, current)) {
                    snapshots.captureCurrent(current,engine.state.value.tasks.task(current)?.currentUiStateKey){os.notifications.canCaptureApp}
                    engine.state.value.tasks.task(current)?.let { task ->
                        snapshots.images[current]?.takeIf {it.pageInstanceKey==task.currentUiStateKey}
                            ?.let { retainPageSnapshot(task.currentUiStateKey, it) }
                    }
                }
                deliver(action)
                yield()
            }
        }
        os.scope.launch {
            persistence.load()
            persistence.updatePreferences { preferences ->
                if (preferences.appPreferenceValues["preferences.rebuild.migrated"] == "b:1") preferences else {
                    val old=os.initial
                    preferences.copy(
                        languageTag=if(old.has("language")) if(old.optString("language")=="en") "en" else "zh-CN" else preferences.languageTag,
                        themeModeName=if(old.has("light")) if(old.optBoolean("light")) "LIGHT" else "DARK" else preferences.themeModeName,
                        accentName=WpAccent.entries.firstOrNull { old.has("accent") && it.argb==old.optLong("accent") }?.name ?: preferences.accentName,
                        motionPreferenceName=if(old.has("reduceMotion")) if(old.optBoolean("reduceMotion")) "REDUCED" else "FOLLOW_SYSTEM" else preferences.motionPreferenceName,
                        appPreferenceValues=preferences.appPreferenceValues + mapOf(
                            "preferences.rebuild.migrated" to "b:1",
                            "preferences.display.keep_awake" to (preferences.appPreferenceValues["preferences.display.keep_awake"] ?: if(old.optBoolean("keepAwake",true)) "b:1" else "b:0")),
                    )
                }
            }
            persistence.state.collect { preferences -> preferences?.let {
                os.chinese=it.languageTag!="en";os.light=it.themeModeName=="LIGHT"
                os.accent=WpAccent.entries.firstOrNull { a -> a.name==it.accentName }?.argb ?: WpAccent.CYAN.argb
                os.reduceMotion=false
                os.textSize=it.appPreferenceValues["preferences.display.text_size"]?.removePrefix("c:") ?: "STANDARD"
                os.keepAwake=it.appPreferenceValues["preferences.display.keep_awake"]!="b:0"
                os.measurementUnits=runCatching { MeasurementUnitSystem.valueOf(it.measurementUnitSystemName) }.getOrDefault(MeasurementUnitSystem.NAUTICAL)
                os.coordinateFormat=it.appPreferenceValues["preferences.coordinate.format"]?.removePrefix("c:") ?: "DMM"
                os.maps.chinese=os.chinese
                os.maps.distanceLabel={meters->os.formatDistance(meters)}
                os.maps.nauticalScale=os.measurementUnits==MeasurementUnitSystem.NAUTICAL
            } }
        }
        os.scope.launch {
            engine.state.collect { state ->
                os.page = when (val surface = state.surface) {
                    ShellVisualSurface.Desktop, ShellVisualSurface.ModuleList -> "start"
                    ShellVisualSurface.Recents -> "sessions"
                    is ShellVisualSurface.Search -> "search"
                    is ShellVisualSurface.Module -> state.tasks.task(surface.taskId)
                        ?.lastLaunchToken?.let(::pageForToken) ?: "start"
                }
                os.editTiles = state.start.interaction !is com.yokuli.shell.engine.interaction.StartInteractionState.Idle
                os.recent = state.tasks.tasks.map { pageForToken(it.lastLaunchToken) }
                val retainedSnapshotKeys = (state.tasks.tasks + state.tasks.linkedReturns.flatMap {
                    listOfNotNull(it.callerSnapshot, it.targetPreviousTask)
                }).map { it.currentUiStateKey }.toSet()
                pageSnapshots.keys.retainAll(retainedSnapshotKeys)
                state.tasks.tasks.forEach { task ->
                    if(snapshotPageKeys.put(task.taskId, task.currentUiStateKey) != task.currentUiStateKey) {
                        restoredPageSnapshot(task.currentUiStateKey)?.let { snapshots.images[task.taskId] = it }
                            ?: snapshots.images.remove(task.taskId)
                    }
                }
                snapshotPageKeys.keys.retainAll(state.tasks.tasks.map { it.taskId }.toSet())
                snapshots.retain(state.tasks.tasks.map {it.taskId}.toSet())
                val foregroundTask = (state.surface as? ShellVisualSurface.Module)?.let { state.tasks.task(it.taskId) }
                val chartKey = foregroundTask?.takeIf { appForPage(pageForToken(it.lastLaunchToken))?.app == AppId.CHART }?.currentUiStateKey
                if(chartKey != null && chartKey != foregroundChartKey) {
                    chartPageStates[chartKey]?.let(os::restoreChartInteraction)
                }
                foregroundChartKey = chartKey
                chartPageStates.keys.retainAll(state.tasks.retainedUiStateKeys)
                visiblePageRoutes.keys.retainAll(state.tasks.retainedUiStateKeys)
            }
        }
    }

    fun pageForToken(token: LaunchToken): String = canonicalPage(apps.firstOrNull { it.rootToken == token }?.page ?: token.value)

    private fun retainPageSnapshot(key: String, snapshot: TaskSnapshot) {
        pageSnapshots.remove(key)
        pageSnapshots[key] = snapshot
        // 历史访问最多八张、32 MiB；只移除引用，不能回收仍在当前任务卡使用的 Bitmap。
        while(pageSnapshots.size > 8 || pageSnapshots.values.sumOf { it.bitmap.allocationByteCount.toLong() } > 32L * 1024 * 1024) {
            pageSnapshots.remove(pageSnapshots.keys.first())
        }
    }

    private fun restoredPageSnapshot(key: String): TaskSnapshot? =
        pageSnapshots.remove(key)?.also { pageSnapshots[key] = it }

    fun updateSystemPreferences(transform: (LauncherPersistedState) -> LauncherPersistedState) {
        os.scope.launch { runCatching { persistence.updatePreferences(transform) }
            .onFailure { os.notify("设置未保存，请重试","Settings were not saved. Please retry.") } }
    }

    fun canonicalPage(page: String): String = when {
        page == "trip" || page == "trip.overview" -> "voyages"
        page == "anchorages" || page == "anchorages.overview" -> "places:anchorages"
        page == "data" || page == "data.overview" -> "instruments"
        page == "sources" || page.startsWith("data.sources") || page=="settings:sources" || page=="nmea:sources" -> "data_center"
        page == "marine-settings" -> "settings:vessel"
        page == "output" -> "nmea:outputs"
        page == "sonar" || page == "sonar.overview" || page=="chart:depth" -> "chart"
        page == "local_nmea" -> "local_nmea"
        else -> page
    }

    fun appForPage(page: String): ShellApp? {
        val canonical=canonicalPage(page)
        val root = when (canonical.substringBefore(':').substringBefore('/')) {
            "place", "route", "saved", "spot", "anchorage", "collection" -> "places"
            "voyage", "replay", "report" -> "voyages"
            else -> canonical.substringBefore(':').substringBefore('/')
        }
        return apps.firstOrNull { it.page == root }
    }

    /** 由业务对象发起的跨应用操作，返回时恢复调用页；普通应用入口仍调用 open。 */
    fun openLinked(destination: String) = open(destination, linked = true)

    /** 局部子页也报告可见地址，通知不能只看到根 token 就误判为另一个页面。 */
    fun reportVisibleRoute(instanceKey: String, destination: String) {
        visiblePageRoutes[instanceKey] = canonicalPage(destination)
    }

    /** 最近任务和系统跳转读取实际可见子页，不把首次启动 token 当成当前页面。 */
    fun visibleRouteForTask(task: InternalAppTask): String =
        visiblePageRoutes[task.currentUiStateKey] ?: pageForToken(task.lastLaunchToken)

    fun openSystemDestination(destination: String) {
        val page = canonicalPage(destination)
        val app = appForPage(page) ?: return
        val state = engine.state.value
        val existing = state.tasks.tasks.firstOrNull { it.appId == app.id }
        val current = (state.surface as? ShellVisualSurface.Module)?.let { state.tasks.task(it.taskId) }
        // 应用级通知恢复正在使用的应用；对象级通知定位其具体地址，不重启相同页面。
        val visibleRoute = existing?.let { visiblePageRoutes[it.currentUiStateKey] ?: pageForToken(it.lastLaunchToken) }
        val resumeVisible = existing != null && (page == app.page || page == visibleRoute)
        if(resumeVisible && existing?.taskId == current?.taskId) return
        val token = if(resumeVisible) existing!!.lastLaunchToken else if(page == app.page) app.rootToken else LaunchToken(page)
        // 不仅当前入口会切换局部标签，保存在 Back 栈中的同地址实例也可能已经显示别的内容。
        // 只有实际页面匹配才可复用，否则新建访问，并让 Back 先回到当前页面。
        val retainedIndex = existing?.backStack?.indexOfLast { it == token } ?: -1
        val candidateKey = when {
            existing?.lastLaunchToken == token -> existing.currentUiStateKey
            retainedIndex >= 0 -> existing?.backStackUiStateKeys?.getOrNull(retainedIndex)
            else -> null
        }
        val candidateVisibleRoute = candidateKey?.let { visiblePageRoutes[it] } ?: pageForToken(token)
        val resetLocalPage = !resumeVisible && candidateKey != null && candidateVisibleRoute != page
        dispatch(LauncherAction.Open(token, preserveCaller = current != null,
            reuseExistingRoute = true, reenterCurrentRoute = resetLocalPage))
    }

    fun open(destination: String, linked: Boolean = false) {
        val page=canonicalPage(destination)
        when (page) {
            "start" -> home()
            "search" -> input(ShellInput.SEARCH)
            "sessions" -> input(ShellInput.RECENTS)
            else -> {
                val app = appForPage(page) ?: return
                val token = if (page == app.page) app.rootToken else LaunchToken(page)
                dispatch(LauncherAction.Open(token, preserveCaller = (linked || page != app.page) && engine.state.value.surface is ShellVisualSurface.Module,
                    replaceTaskRoute = page == app.page && !linked))
            }
        }
    }

    fun dispatch(action:LauncherAction) {
        val navigates=action is LauncherAction.Open || action is LauncherAction.ActivateTask || action is LauncherAction.PinEntry ||
            action in listOf(LauncherAction.Back,LauncherAction.ShowDesktop,LauncherAction.ShowRecents,LauncherAction.OpenSearch,LauncherAction.ShowStart,LauncherAction.ShowAllApps)
        // 在离开海图的操作发起时保存；另一应用准备自己的海图请求以后不可反向覆盖原访问。
        val currentTask = (engine.state.value.surface as? ShellVisualSurface.Module)?.let { engine.state.value.tasks.task(it.taskId) }
        if(navigates && currentTask != null && leavesTask(action,currentTask.taskId) && appForPage(pageForToken(currentTask.lastLaunchToken))?.app == AppId.CHART) {
            chartPageStates[currentTask.currentUiStateKey] = os.captureChartInteraction()
        }
        if(navigates && engine.state.value.surface is ShellVisualSurface.Module) navigationQueue.trySend(action)
        else deliver(action)
    }
    private fun deliver(action:LauncherAction) {
        if(action is LauncherAction.Open) {
            val app=appForPage(pageForToken(action.token))
            val existing=engine.state.value.tasks.tasks.firstOrNull {it.appId==app?.id}
            coldOpeningTask=app?.takeIf {existing==null}?.let {InternalAppTaskId(it.id.value)}
            // 首页入口是新访问；引擎为它分配独立页面实例，不再清空同应用的调用者快照。
            if(existing!=null && action.token==app?.rootToken && !action.preserveCaller && !action.reuseExistingRoute) {
                engine.dispatch(action.copy(replaceTaskRoute=true,preserveCaller=false));return
            }
        } else if(action is LauncherAction.ActivateTask || action in listOf(
            LauncherAction.Back, LauncherAction.ShowDesktop, LauncherAction.ShowRecents,
            LauncherAction.OpenSearch, LauncherAction.ShowStart, LauncherAction.ShowAllApps,
        )) coldOpeningTask=null
        engine.dispatch(action)
    }

    private fun leavesTask(action: LauncherAction, current: InternalAppTaskId): Boolean = when (action) {
        is LauncherAction.Open -> appForPage(pageForToken(action.token))?.id?.value != current.value
        is LauncherAction.ActivateTask -> action.taskId != current
        LauncherAction.Back -> engine.state.value.tasks.let { tasks ->
            tasks.linkedReturns.lastOrNull()?.let { it.targetTaskId == current && (tasks.task(current)?.backStack?.size ?: 0) <= it.targetBackStackDepth } == true ||
                tasks.task(current)?.backStack?.isEmpty() == true
        }
        LauncherAction.ShowDesktop, LauncherAction.ShowRecents, LauncherAction.OpenSearch,
        LauncherAction.ShowStart, LauncherAction.ShowAllApps -> true
        else -> false
    }

    /** 返回依据本次访问栈，而不是页面归属；调用者快照保留地图和列表的原实例。 */
    fun backDestination(instanceKey: String?): String? {
        val state = engine.state.value
        val task = (state.surface as? ShellVisualSurface.Module)?.let { state.tasks.task(it.taskId) } ?: return null
        if (instanceKey != null && task.currentUiStateKey != instanceKey) return null
        val handoff = state.tasks.linkedReturns.lastOrNull()?.takeIf {
            it.targetTaskId == task.taskId && task.backStack.size <= it.targetBackStackDepth
        }
        if (handoff != null) {
            val caller = handoff.callerSnapshot ?: state.tasks.task(handoff.callerTaskId) ?: return null
            return visiblePageRoutes[caller.currentUiStateKey] ?: pageForToken(caller.lastLaunchToken)
        }
        return task.backStack.lastOrNull()?.let { token ->
            task.backStackUiStateKeys.lastOrNull()?.let { visiblePageRoutes[it] } ?: pageForToken(token)
        }
    }

    fun back() = input(ShellInput.BACK)
    private fun atUnlinkedAppRoot():Boolean {
        val state=engine.state.value
        if(state.transient!=null)return false
        val task=(state.surface as? ShellVisualSurface.Module)?.let{state.tasks.task(it.taskId)}?:return false
        return task.backStack.isEmpty()&&state.tasks.linkedReturns.lastOrNull()?.targetTaskId!=task.taskId
    }
    /** 当前 App 的对象地址返回父路径；局部页面处理器调用，避免再次递归分发 Back。 */
    fun popRoute() = dispatch(LauncherAction.Back)
    fun home() { input(ShellInput.DESKTOP); os.save() }
    fun input(input: ShellInput) {
        if (os.notifications.expanded) {
            os.notifications.close()
            if (input == ShellInput.BACK) return
        }
        if (inputRouter.dispatch(input)) return
        if (input == ShellInput.BACK && os.page == "chart" && os.showCrosshair) {
            os.showCrosshair = false
            return
        }
        if(input==ShellInput.BACK&&atUnlinkedAppRoot()) {
            dispatch(LauncherAction.ShowDesktop)
            return
        }
        dispatch(input.toShellAction())
    }
    fun resetStart() {
        os.scope.launch {
            persistence.saveDocument(defaultDocument)
            engine.dispatch(LauncherAction.RestorePersistedDocument(defaultDocument))
        }
    }
}

/** All migration targets exist in this catalog, including upgrades starting at old model 0/1/2. */
private fun nineAppMigration(): LauncherProductMigrationPlan {
    val rules=listOf(
        "instruments" to setOf("data","data_sources"),
        "nmea" to setOf("nmea_input","nmea_output"),
        "voyages" to setOf("trip"),
        "navigation" to setOf("anchorages"),
        "chart" to setOf("sonar"),
        "instruments" to setOf("data","data_sources"),
        "nmea" to setOf("nmea_input","nmea_output"),
    ) + tilePresets().groupBy { ShellApp(it.app).entry.value }.map { (entry, presets) -> entry to presets.map {it.entryId.value}.toSet() }
    return LauncherProductMigrationPlan(rules.mapIndexed { index,(target,legacy) ->
        LauncherProductMigrationStep(index+1,LauncherEntryId(target),legacy.map(::LauncherEntryId).toSet(),
            legacy.map { LauncherTokenAlias("$it.overview", if(target=="nmea") "nmea.root" else if(target=="chart") "chart.browse" else "$target.overview") })
    })
}

data class ShellApp(val app: AppId) {
    val page = app.name.lowercase()
    private val stableName = when (app.name) {
        "LIBRARY" -> "chart_library"
        "PLACES" -> "navigation"
        "SETTINGS" -> "preferences"
        "TILES" -> "tile_library"
        else -> page
    }
    val id = LauncherAppId(stableName)
    val entry = LauncherEntryId(stableName)
    val rootToken = LaunchToken(when (app.name) {
        "CHART", "LIBRARY" -> "$stableName.browse"
        "NMEA" -> "nmea.root"
        else -> "$stableName.overview"
    })
    val sizes = if (app.name == "SETTINGS") listOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2) else MarineTileSize.entries
    val defaultSize = if (app.name == "CHART") MarineTileSize.WIDE_4X2 else MarineTileSize.STANDARD_2X2
}

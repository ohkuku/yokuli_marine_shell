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
import com.yokuli.marine.shell.rebuild.ui.formatDistance

/** The original Shell owns task history, Start editing and navigation. Apps only publish destinations. */
class WpShellRuntime(private val os: OsStore) {
    val inputRouter = InternalAppInputRouter()
    val apps = AppId.entries.map(::ShellApp)
    val appPreferenceRegistry = AppPreferenceRegistry.compose(apps.map { it.id }.toSet(),
        listOf(AppPreferenceContribution(LauncherAppId("chart"),listOf(AppPreferenceDefinition.Choice(
            AppPreferenceKey("chart.tile.mode"),listOf("AUTO","MAP","NAVIGATION","POSITION","STATIC"),AppPreferenceValue.Choice("AUTO"),
            AppPreferenceLabel("海图磁贴内容","Chart tile content"),mapOf(
                "AUTO" to AppPreferenceLabel("自动","Automatic"),"MAP" to AppPreferenceLabel("地图快照","Map snapshot"),
                "NAVIGATION" to AppPreferenceLabel("当前导航","Active navigation"),"POSITION" to AppPreferenceLabel("当前位置","Current position"),
                "STATIC" to AppPreferenceLabel("静态图标","Static icon")))))) + apps.filter { it.app != AppId.CHART }.map { app ->
            AppPreferenceContribution(app.id,listOf(AppPreferenceDefinition.Choice(
                AppPreferenceKey("${app.id.value}.tile.mode"), listOf("LIVE","STATIC"), AppPreferenceValue.Choice("LIVE"),
                AppPreferenceLabel("磁贴内容","Tile content"),mapOf("LIVE" to AppPreferenceLabel("实时内容","Live content"),"STATIC" to AppPreferenceLabel("静态图标","Static icon")))))
        })
    val catalog = LauncherCatalogSnapshot(
        revision = 1,
        apps = apps.map { LauncherAppDescriptor(it.id, it.entry) },
        entries = apps.map { app ->
            LauncherEntryDescriptor(app.entry, app.id, app.rootToken, app.defaultSize,
                app.sizes, PinPolicy.PINNABLE)
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
        installedEntryIds = apps.map { it.entry }.toSet(),
    )
    private val host = StaticLauncherHostPort(
        catalog,
        apps.associate { it.rootToken to it.id },
        apps.map { app -> app.id to { token: LaunchToken -> appForPage(pageForToken(token))?.id == app.id } },
    )
    val engine: LauncherEngine = DefaultLauncherEngine(host, persistence, defaultDocument, os.scope)

    init {
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
                os.reduceMotion=it.motionPreferenceName=="REDUCED"
                os.keepAwake=it.appPreferenceValues["preferences.display.keep_awake"]!="b:0"
                os.measurementUnits=runCatching { MeasurementUnitSystem.valueOf(it.measurementUnitSystemName) }.getOrDefault(MeasurementUnitSystem.NAUTICAL)
                os.coordinateFormat=it.appPreferenceValues["preferences.coordinate.format"]?.removePrefix("c:") ?: "DMM"
                os.maps.chinese=os.chinese
                os.maps.distanceLabel={meters->os.formatDistance(meters)}
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
            }
        }
    }

    fun pageForToken(token: LaunchToken): String = canonicalPage(apps.firstOrNull { it.rootToken == token }?.page ?: token.value)

    fun updateSystemPreferences(transform: (LauncherPersistedState) -> LauncherPersistedState) {
        os.scope.launch { runCatching { persistence.updatePreferences(transform) }
            .onFailure { os.notify("设置未保存，请重试","Settings were not saved. Please retry.") } }
    }

    fun canonicalPage(page: String): String = when {
        page == "trip" || page == "trip.overview" -> "voyages"
        page == "anchorages" || page == "anchorages.overview" -> "places:anchorages"
        page == "data" || page == "data.overview" -> "instruments"
        page == "sources" || page.startsWith("data.sources") -> "settings:sources"
        page == "marine-settings" -> "settings:vessel"
        page == "output" -> "nmea:outputs"
        page == "sonar" || page == "sonar.overview" -> "chart:depth"
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

    fun open(destination: String) {
        val page=canonicalPage(destination)
        when (page) {
            "start" -> home()
            "search" -> input(ShellInput.SEARCH)
            "sessions" -> input(ShellInput.RECENTS)
            else -> {
                val app = appForPage(page) ?: return
                val token = if (page == app.page) app.rootToken else LaunchToken(page)
                engine.dispatch(LauncherAction.Open(token, preserveCaller = engine.state.value.surface is ShellVisualSurface.Module))
            }
        }
    }

    fun back() = input(ShellInput.BACK)
    fun home() { input(ShellInput.DESKTOP); os.save() }
    fun input(input: ShellInput) {
        if (inputRouter.dispatch(input)) return
        if (input == ShellInput.BACK && os.page == "chart" && os.showCrosshair) {
            os.showCrosshair = false
            return
        }
        engine.dispatch(input.toShellAction())
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
    )
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

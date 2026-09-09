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
import java.io.File

/** The original Shell owns task history, Start editing and navigation. Apps only publish destinations. */
class WpShellRuntime(private val os: OsStore) {
    private val restoreOriginalPreferences = !File(os.context.filesDir, "experience-v1.json").exists() &&
        File(os.context.filesDir, "datastore/launcher_state.pb").exists()
    val inputRouter = InternalAppInputRouter()
    val apps = AppId.entries.map(::ShellApp)
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
        installedEntryIds = apps.map { it.entry }.toSet(),
    )
    private val host = StaticLauncherHostPort(
        catalog,
        apps.associate { it.rootToken to it.id },
        apps.map { app -> app.id to { token: LaunchToken -> appForPage(pageForToken(token))?.id == app.id } },
    )
    val engine: LauncherEngine = DefaultLauncherEngine(host, persistence, defaultDocument, os.scope)

    init {
        if (restoreOriginalPreferences) os.scope.launch {
            persistence.load()?.let { preferences ->
                os.chinese = preferences.languageTag != "en"
                os.light = preferences.themeModeName == "LIGHT"
                os.accent = WpAccent.entries.firstOrNull { it.name == preferences.accentName }?.argb ?: WpAccent.CYAN.argb
                os.reduceMotion = preferences.motionPreferenceName == "REDUCED"
                os.save()
            }
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

    fun pageForToken(token: LaunchToken): String = apps.firstOrNull { it.rootToken == token }?.page ?: token.value

    fun appForPage(page: String): ShellApp? {
        val root = when (page.substringBefore(':').substringBefore('/')) {
            "place", "route" -> "places"
            "marine-settings" -> "settings"
            "sources" -> "data"
            "output" -> "nmea"
            "voyage", "replay", "report" -> "voyages"
            else -> page.substringBefore(':').substringBefore('/')
        }
        return apps.firstOrNull { it.page == root }
    }

    fun open(page: String) {
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

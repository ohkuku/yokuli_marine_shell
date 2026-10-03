package com.yokuli.marine.shell.rebuild.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.ui.*
import com.yokuli.shell.contract.TileBinding
import com.yokuli.shell.contract.TileBindingKind

/** 编译期可信组件表。包只引用逻辑组件名，不引用类名，也不能请求加载 DEX/APK。 */
internal object SystemYklApps {
    fun identity(app: AppId): YklPackageEntry {
        val route = app.name.lowercase()
        val launcherId = when (app) {
            AppId.LIBRARY -> "chart_library"; AppId.PLACES -> "navigation"
            AppId.SETTINGS -> "preferences"; AppId.TILES -> "tile_library"; else -> route
        }
        val token = when (app) { AppId.CHART, AppId.LIBRARY -> "$launcherId.browse"; AppId.NMEA -> "nmea.root"; else -> "$launcherId.overview" }
        return YklPackageEntry("com.yokuli.${route.replace('_', '-')}", app.zh, app.en, 1, YklRuntime.HOST_KOTLIN,
            YklOrigin.SYSTEM_IMAGE, route, launcherId, token, "", setOf(route), emptySet(), app, route)
    }

    /** 每个应用适配器只处理本包的内部页面；Shell 不再集中理解所有应用的业务页面。 */
    private val components: Map<AppId, @Composable (OsStore, String) -> Unit> = mapOf(
        AppId.CHART to { os, page -> when {
            page == "task:navigation" -> CurrentNavigationTileDestination(os)
            page.startsWith("chart:ais:") -> ChartAppScreen(os, page.substringAfterLast(':').toIntOrNull())
            else -> ChartAppScreen(os)
        } },
        AppId.LIBRARY to { os, page -> when {
            page == "library" -> LibraryScreen(os)
            page == "library:data" -> LibraryScreen(os)
            page == "library:packages" -> LibraryScreen(os)
            page.startsWith("library:bundle/") -> ChartBundleDetailScreen(os, page.substringAfter("library:bundle/"))
            page.startsWith("chartobjects:") -> LibraryObjectsScreen(os, page.substringAfter(':').substringBefore(':'), page.substringAfter(':').substringAfter(':', "").takeIf { it.isNotBlank() }?.let(android.net.Uri::decode))
            page.startsWith("chartdataset:") -> LibraryDatasetScreen(os, page.substringAfter(':'))
            else -> LibraryFolderScreen(os, page.substringAfter(':'))
        } },
        AppId.VOYAGES to { os, page -> if (page == "voyages" || page == "task:recording") LogbookScreen(os)
            else LogbookScreen(os, page.substringAfter(':').toLongOrNull()) },
        AppId.ANCHOR to { os, page -> AnchorExperience(os, if (page == "task:anchorWatch") "current" else page.substringAfter(':', "watch")) },
        AppId.PLACES to { os, page -> when {
            page.startsWith("tileplace:") -> SavedTileDestination(os, TileBinding("yokuli", TileBindingKind.SAVED_PLACE, page.substringAfter(':')))
            page.startsWith("tileroute:") -> SavedTileDestination(os, TileBinding("yokuli", TileBindingKind.SAVED_ROUTE, page.substringAfter(':')))
            page == "places" || page.startsWith("places:") -> PlacesScreen(os, anchoragesOnly = page == "places:anchorages")
            page.startsWith("place:") -> PlaceScreen(os, page.substringAfter(':'))
            page.startsWith("route:") -> RouteScreen(os, page.substringAfter(':'))
            page.startsWith("anchorage:") -> SavedLocationScreen(os, page.substringAfter(':').toLongOrNull())
            else -> CollectionScreen(os, page.substringAfter(':').toLongOrNull())
        } },
        AppId.INSTRUMENTS to { os, page -> InstrumentsScreen(os, page.substringAfter(':', "")) },
        AppId.DATA_CENTER to { os, page -> DataCenterScreen(os, page.substringAfter(':', "").takeIf { it.isNotBlank() }) },
        AppId.NMEA to { os, page -> NmeaScreen(os, page.substringAfter(':', "")) },
        AppId.AIS to { os, page -> AisScreen(os, page.substringAfter(':', "")) },
        AppId.LOCAL_NMEA to { os, _ -> LocalNmeaScreen(os) },
        AppId.SETTINGS to { os, page -> SettingsScreen(os, page.substringAfter(':', "overview")) },
        AppId.TILES to { os, page -> TileLibraryScreen(os, page.substringAfter(':', "").takeIf { it.isNotBlank() }) },
        AppId.APP_CENTER to { os, page -> AppCenterScreen(os, page.substringAfter(':', "")) },
        AppId.CHART_STORE to { os, page -> ChartDownloadsScreen(os, page.substringAfter(':', "")) },
        AppId.HARDWARE_LAB to { os, _ -> HardwareLabScreen(os) },
    )

    @Composable fun Render(os: OsStore, entry: YklPackageEntry, route: String) {
        val app = entry.hostApp
        val expected = app?.let(::identity)
        if (app == null || entry.error != null || expected == null || entry.id != expected.id || entry.component != expected.component ||
            entry.origin != YklOrigin.SYSTEM_IMAGE || entry.runtime != YklRuntime.HOST_KOTLIN || entry.digest.isBlank() || !entry.owns(route)) {
            PackageUnavailable(os, entry.error ?: os.t("应用入口与系统组件不匹配", "Package entry does not match the system component")); return
        }
        val component = components[app]
        if (component == null) PackageUnavailable(os, os.t("此系统组件未安装", "This system component is not installed"))
        else component(os, route)
    }
}

/** 全部系统与用户安装应用共享这一生产入口；页面实例、返回及暂停由原 Shell task graph 提供。 */
@Composable fun YklPackageScreen(os: OsStore, route: String) {
    val ready by os.packages.ready.collectAsState()
    val entries by os.packages.entries.collectAsState()
    if (!ready) { Column { PageBody { MetroProgress(os.t("正在打开应用…", "Opening app…")) } }; return }
    val entry = entries.singleOrNull { it.owns(route) }
    if (entry == null) { PackageUnavailable(os, os.t("这个入口已不再使用。资料仍保留在所属应用中。", "This destination has moved. Your data remains in its application.")); return }
    when (entry.runtime) {
        YklRuntime.HOST_KOTLIN -> SystemYklApps.Render(os, entry, route)
        YklRuntime.WEB -> {
            if (entry.origin != YklOrigin.USER_INSTALLED || entry.installed == null || entry.error != null)
                PackageUnavailable(os, entry.error ?: os.t("应用未安装", "App is not installed"))
            else ExtensionScreen(os, entry.id)
        }
    }
}

@Composable private fun PackageUnavailable(os: OsStore, message: String) {
    Column { PageHeader(os, os.t("暂时无法打开应用", "Cannot open this app")); PageBody {
        Label(message, 14)
        MetroButton(os.t("打开应用中心", "Open App Center"), { os.shell.openLinked("app_center") }, primary = true)
    } }
}

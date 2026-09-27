package com.yokuli.marine.shell.rebuild.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.ui.*
import com.yokuli.shell.compose.*
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.layout.TileDocumentEntry

private val sizes = listOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2)
private fun label(zh: String, en: String) = AppPreferenceLabel(zh, en)
fun extensionTileChoice(app: ShellApp): TileContentChoice {
    val manifest = requireNotNull(app.extension).manifest
    return TileContentChoice(TileBinding("yokuli", TileBindingKind.APP, app.entry.value),
        label(manifest.name, manifest.nameEn), label("已安装应用", "Installed app"), AppId.APP_CENTER,
        TileContentGroup.APPS, sizes, listOf(TileContentStyle("static", label("应用入口", "App shortcut"))),
        MarineTileSize.STANDARD_2X2, app.rootToken.value, label(manifest.name, manifest.nameEn))
}
fun missingExtensionTileChoice(binding: TileBinding) = TileContentChoice(binding,
    label("应用未安装", "App not installed"), label("重新安装后可继续使用；也可移除此磁贴", "Reinstall to use this shortcut, or remove the tile"),
    AppId.APP_CENTER, TileContentGroup.APPS, sizes, listOf(TileContentStyle("static", label("应用入口", "App shortcut"))),
    MarineTileSize.STANDARD_2X2, "app_center", label("应用中心", "App Center"), supported = false)

@Composable fun extensionTilePresentation(os: OsStore, app: ShellApp): LauncherEntryVisualContribution {
    val manifest = requireNotNull(app.extension).manifest
    return extensionVisual(app.entry, if(os.chinese) manifest.name else manifest.nameEn, os.t("已安装", "Installed"))
}
@Composable fun extensionInstancePresentation(os: OsStore, tile: TileDocumentEntry): LauncherEntryVisualContribution {
    val app = os.shell.apps.firstOrNull { it.entry.value == tileBinding(tile).contentId }
    val ready by os.extensions.ready.collectAsState()
    val issue by os.extensions.errors.collectAsState()
    val missingTitle = when { !ready -> os.t("正在读取应用", "Loading app"); issue != null -> os.t("安装记录暂不可读", "App records unavailable"); else -> os.t("应用未安装", "App not installed") }
    val title = tile.presentation.title ?: app?.extension?.manifest?.let { if(os.chinese) it.name else it.nameEn }
        ?: missingTitle
    return extensionVisual(tile.entryId, title, if(app == null) os.t("打开应用中心", "Open App Center") else os.t("已安装", "Installed"), sizes + tile.size)
}
@Composable private fun extensionVisual(entry: LauncherEntryId, title: String, detail: String, tileSizes: List<MarineTileSize> = sizes) =
    LauncherEntryVisualContribution(entry, title, title.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#', title, detail,
        LauncherIconRenderer { color, modifier -> Glyph("apps", modifier, color) },
        tileSizes.distinct().associateWith { size -> LauncherTileRenderer { context ->
            Box(context.modifier.fillMaxSize().padding(if(size == MarineTileSize.ICON_1X1) 8.dp else 14.dp)) {
                Glyph("apps", Modifier.size(if(size == MarineTileSize.ICON_1X1) 24.dp else 32.dp).align(Alignment.TopStart), context.contentColor)
                if(size != MarineTileSize.ICON_1X1) Column(Modifier.align(Alignment.BottomStart)) {
                    Label(title, 17, context.contentColor, maxLines = 2)
                    if(size == MarineTileSize.WIDE_4X2) Label(detail, 12, context.contentColor.copy(alpha = .65f), maxLines = 1)
                }
            }
        } })

package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.ChartFolder
import com.yokuli.marine.shell.rebuild.chart.MapSource

@Composable fun LibraryScreen(os:OsStore) {
    val library=os.library
    var naming by remember {mutableStateOf<ChartFolder?>(null)}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->
        uri?.let {library.linkFolder(it)}?.let {naming=it}
    }
    val single=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(library::importCopy) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("海图库","chart library"))
        Pivot(listOf(os.t("图层","layers"),os.t("文件夹","folders"))) { page ->
            PageBody {
                LibraryProgress(os)
                if(page==0) {
                    if(library.layers.isEmpty()) {
                        Label(os.t("一个文件夹，\n一张自己的图层。","one folder,\nyour own layer."),35)
                        Label(os.t("连接海图文件夹，为图层起个名字。重叠区域按你排好的优先级显示。","Connect a chart folder and name its layer. Overlapping charts follow the priority you choose."),18,LocalMetro.current.muted)
                    } else {
                        Label(os.t("选择一张命名图层，在所有地图中使用。","choose a named layer for all maps."),14,LocalMetro.current.muted)
                        library.folders.filter {it.layerName!=null}.forEach {entry ->
                            Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                val included=library.folderFiles(entry).count {it.enabled && it.error==null}
                                MenuRow((if(os.maps.source==MapSource.CustomLayer(entry.id)) "✓  " else "")+entry.layerName,os.t("${folderName(os,entry)} · $included 张海图","${folderName(os,entry)} · $included charts")) {viewLayer(os,entry)}
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    MetroButton(os.t("管理图层","manage layer"),{os.open("library:${entry.id}")},Modifier.weight(1f))
                                    IconAction("chart",os.t("查看","view"),{viewLayer(os,entry)})
                                }
                            }
                        }
                    }
                    MetroButton(os.t("添加文件夹图层","add folder layer"),{folder.launch(null)},primary=true,enabled=!library.busy)
                } else {
                    if(library.folders.isEmpty()) Label(os.t("带上你的海图。","bring your charts."),35)
                    library.folders.forEach {entry ->
                        MenuRow(folderName(os,entry),entry.layerName?.let {os.t("图层：$it","layer: $it")}
                            ?: os.t("已连接 · 尚未创建图层","connected · no layer yet"),"folder") {os.open("library:${entry.id}")}
                    }
                    MetroButton(os.t("连接文件夹","connect folder"),{folder.launch(null)},primary=true,enabled=!library.busy)
                    MetroButton(os.t("重新扫描所有文件夹","rescan folders"),{library.rescan()},enabled=!library.busy && library.folders.any {it.uri!="copy"})
                    MetroButton(os.t("导入到本机文件夹","import into local folder"),{single.launch(arrayOf("*/*"))},enabled=!library.busy)
                    Label(os.t("支持 PNG、JPEG、WebP 栅格 MBTiles。新增或替换文件后重新扫描；已有顺序和选择会保留。原文件始终保留。","PNG, JPEG and WebP raster MBTiles. Rescan after adding or replacing files; your order and choices are preserved. Original files are always kept."),16,LocalMetro.current.muted)
                }
            }
        }
    }
    naming?.let {entry ->TextDialog(os,os.t("命名图层","name this layer"),entry.layerName ?: folderName(os,entry),{naming=null}) {library.setLayer(entry,it)}}
}

@Composable fun LibraryFolderScreen(os:OsStore,folderId:String) {
    val library=os.library
    val folder=library.folders.firstOrNull {it.id==folderId}
    if(folder==null) {LaunchedEffect(folderId) {os.back()};return}
    val files=library.folderFiles(folder)
    val c=LocalMetro.current
    var naming by remember {mutableStateOf(false)}
    var disconnect by remember {mutableStateOf(false)}
    var removeLayer by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,folder.layerName ?: folderName(os,folder),os.t("海图库","CHART LIBRARY"))
        Pivot(listOf(os.t("海图顺序","chart order"),os.t("图层设置","layer settings"))) {page ->
            PageBody {
                LibraryProgress(os)
                if(page==0) {
                    Label(os.t("上方优先。没有覆盖的地方，\n自动显示下一张海图。","top first. where coverage ends,\nthe next chart appears."),22)
                    if(files.isEmpty() && !library.busy) {
                        Label(os.t("文件夹里还没有可用的海图","no readable charts in this folder"),25,c.muted)
                        if(folder.uri!="copy") MetroButton(os.t("重新扫描","rescan"),{library.rescan(folder)},primary=true)
                    }
                    files.forEachIndexed {index,file ->
                        Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Label("${index+1}".padStart(2,'0'),28,c.accent)
                            Toggle(file.filename,file.enabled,if(file.error==null) "${decimal(file.bytes/1_000_000.0)} MB · z${file.minZoom}–${file.maxZoom}" else "${decimal(file.bytes/1_000_000.0)} MB") {library.toggle(file)}
                            if(file.name!=file.filename) Label(file.name,14,c.muted)
                            if(file.attribution.isNotBlank()) Label(android.text.Html.fromHtml(file.attribution,0).toString(),12,c.muted)
                            file.error?.let {Label(library.errorText(it,os.chinese),15)}
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                MetroButton(os.t("上移","move up"),{library.moveFile(file,-1)},Modifier.weight(1f),enabled=index>0)
                                MetroButton(os.t("下移","move down"),{library.moveFile(file,1)},Modifier.weight(1f),enabled=index<files.lastIndex)
                            }
                            if(file.error==null) MenuRow(os.t("查看这张海图","view this chart"),coordinates(file.focus)) {
                                library.showOnly(file);os.maps.select(MapSource.CustomLayer(folder.id));os.fly(file.focus,file.previewZoom);os.open("chart");os.save()
                            }
                        }
                    }
                } else {
                    Label(folderName(os,folder),28,c.accent)
                    Label(os.t("${files.size} 张海图 · ${decimal(files.sumOf {it.bytes}/1_000_000.0)} MB","${files.size} charts · ${decimal(files.sumOf {it.bytes}/1_000_000.0)} MB"),16,c.muted)
                    if(folder.layerName==null) {
                        Label(os.t("创建图层后，这个文件夹的海图就能一起显示在地图上。","Create a layer to show this folder's charts together on the map."),19)
                        MetroButton(os.t("创建图层","create layer"),{naming=true},primary=true)
                    } else {
                        Label(if(os.maps.source==MapSource.CustomLayer(folder.id))os.t("当前使用的地图","current map source")else os.t("可从地图来源中选择","available in map sources"),16,c.accent)
                        MetroButton(os.t("查看图层","view layer"),{viewLayer(os,folder)},primary=true,enabled=files.any {it.enabled && it.error==null})
                        MetroButton(os.t("重命名图层","rename layer"),{naming=true})
                        MetroButton(os.t("移除图层","remove layer"),{removeLayer=true})
                    }
                    if(folder.uri!="copy") MetroButton(os.t("重新扫描文件夹","rescan folder"),{library.rescan(folder)},enabled=!library.busy)
                    MetroButton(os.t("断开文件夹","disconnect folder"),{disconnect=true},enabled=!library.busy)
                    Label(os.t("断开连接不会删除文件。部分文件提供器需要本机读取副本，会占用存储空间。","Disconnecting never deletes files. Some file providers need a local reading copy, which uses device storage."),16,c.muted)
                }
            }
        }
    }
    if(naming) TextDialog(os,os.t("图层名称","layer name"),folder.layerName ?: folderName(os,folder),{naming=false}) {library.setLayer(folder,it)}
    if(removeLayer) ConfirmDialog(os,os.t("移除图层？若正在使用将切回在线，文件夹和海图顺序会保留。","Remove this layer? An active layer switches to online; keep its folder and chart order."),{removeLayer=false}) {os.maps.removingLayer(folder.id);library.removeLayer(folder);removeLayer=false}
    if(disconnect) ConfirmDialog(os,os.t("断开 ${folderName(os,folder)}？原文件会保留。","Disconnect ${folderName(os,folder)}? Keep original files."),{disconnect=false}) {os.maps.removingLayer(folder.id);library.forgetFolder(folder.uri);disconnect=false;os.back()}
}

@Composable private fun LibraryProgress(os:OsStore) {
    val library=os.library
    if(library.busy) {Label(os.t("正在读取…","reading…"),25,LocalMetro.current.accent);Label(library.progress,14,LocalMetro.current.muted)}
    library.failure?.let {Label(library.errorText(it,os.chinese),17,Color(0xFFE47C4C))}
    if(library.rejected>0) Label(os.t("${library.rejected} 个文件未能读取","${library.rejected} files could not be read"),14,LocalMetro.current.muted)
}
private fun folderName(os:OsStore,folder:ChartFolder) = if(folder.uri=="copy") os.t("本机导入","local imports") else folder.name
private fun viewLayer(os:OsStore,folder:ChartFolder) {
    val file=os.library.folderFiles(folder).firstOrNull {it.enabled && it.error==null}
    os.maps.select(MapSource.CustomLayer(folder.id));file?.let {os.fly(it.focus,it.previewZoom)};os.open("chart");os.save()
}

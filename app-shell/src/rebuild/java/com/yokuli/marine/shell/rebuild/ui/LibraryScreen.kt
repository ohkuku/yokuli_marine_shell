package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*

/** 海图管理显示图层，数据管理可查询对象；两类各有导入、详情与管理流程。 */
@Composable fun LibraryScreen(os:OsStore,initialPage:Int=0) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.title(AppId.LIBRARY))
        Pivot(listOf(os.t("海图","Charts"),os.t("数据","Data")),initialPage=initialPage) {page->
            if(page==0)RasterLibraryPane(os)else StructuredChartLibraryPane(os)
        }
    }
}

@Composable private fun RasterLibraryPane(os:OsStore) {
    val library=os.library
    var naming by remember {mutableStateOf<ChartFolder?>(null)}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->uri?.let(library::linkFolder)?.let {naming=it}}
    val single=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(library::importCopy)}
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { PageBody {
            LibraryProgress(os)
            Label(os.t("显示图层","Map layers"),18)
            Label(os.t("MBTiles 海图 · 按文件夹管理","MBTiles charts · Organised by folder"),13,LocalMetro.current.muted)
            if(library.folders.isEmpty()) {
                Label(os.t("添加你的第一张海图","Add your first chart"),20)
                Label(os.t("连接文件夹，或导入 MBTiles 文件。每个文件夹对应一个命名图层，重叠时按你的排序显示。","Connect a folder or import an MBTiles file. Each folder becomes a named layer; your order controls overlapping charts."),15,LocalMetro.current.muted)
            }
            library.folders.forEach {entry ->
                val charts=library.folderFiles(entry)
                val used=os.maps.source==MapSource.CustomLayer(entry.id)
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable {os.open("library:${entry.id}")}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Glyph("folder",Modifier.size(32.dp),if(used)LocalMetro.current.accent else LocalMetro.current.fg)
                    Column(Modifier.weight(1f).padding(start=16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Label(entry.layerName ?: folderName(os,entry),20)
                        Label(os.t("${charts.count {it.enabled && it.error==null}} / ${charts.size} 张参与显示","${charts.count {it.enabled && it.error==null}} / ${charts.size} charts included"),15,LocalMetro.current.muted)
                        if(used)Label(os.t("海图正在使用","in use on chart"),14,LocalMetro.current.accentText)
                        else if(entry.layerName==null)Label(os.t("点开创建图层","open to create a layer"),14,LocalMetro.current.muted)
                    }
                    Glyph("chevron_right",Modifier.size(20.dp),LocalMetro.current.muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Label(os.t("水深、障碍等可查询内容，在“数据”页单独管理。","Manage queryable depths and hazards separately in Data."),13,LocalMetro.current.muted)
        } }
        AppCommandBar(os,listOf(
            AppCommand("link-folder","folder",os.t("连接海图文件夹","Connect chart folder"),{folder.launch(null)},enabled=!library.busy),
            AppCommand("import-chart","plus",os.t("导入海图文件","Import chart file"),{single.launch(arrayOf("*/*"))},enabled=!library.busy),
        ))
    }
    naming?.let {entry ->TextDialog(os,os.t("命名这个图层","name this layer"),entry.layerName ?: folderName(os,entry),{naming=null}) {library.setLayer(entry,it);naming=null;os.open("library:${entry.id}")}}
}

@Composable fun LibraryFolderScreen(os:OsStore,folderId:String) {
    val library=os.library
    val folder=library.folders.firstOrNull {it.id==folderId}
    if(folder==null) {LaunchedEffect(folderId) {os.back()};return}
    val files=library.folderFiles(folder)
    val c=LocalMetro.current
    var naming by remember {mutableStateOf(false)}
    var namingFolder by remember {mutableStateOf(false)}
    var namingFile by remember {mutableStateOf<ChartFile?>(null)}
    var removeFile by remember {mutableStateOf<ChartFile?>(null)}
    var disconnect by remember {mutableStateOf(false)}
    var removeLayer by remember {mutableStateOf(false)}
    var expanded by rememberSaveable(folderId) {mutableStateOf<String?>(null)}
    val included=files.count {it.enabled && it.error==null}
    val used=os.maps.source==MapSource.CustomLayer(folder.id)
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,folder.layerName ?: folderName(os,folder),os.title(AppId.LIBRARY))
        Pivot(listOf(os.t("海图","charts"),os.t("管理","manage"))) {page ->PageBody {
            LibraryProgress(os)
            if(page==0) {
                Label(os.t("$included 张正在参与图层","$included charts included in layer"),20,c.accentText)
                if(folder.layerName!=null) {
                    MetroButton(if(used)os.t("回到海图查看","view current chart")else os.t("在海图中使用此图层","use this layer on chart"),{viewLayer(os,folder)},primary=true,enabled=included>0)
                } else MetroButton(os.t("创建命名图层","create a named layer"),{naming=true},primary=true)
                Label(os.t("上方优先，空白处显示下一张。开关决定是否参与渲染；点文件名展开操作。","Top first; uncovered areas show the next chart. Switch inclusion on or off; tap a name for actions."),15,c.muted)
                if(files.isEmpty() && !library.busy)Label(os.t("文件夹里还没有海图","no charts in this folder yet"),20,c.muted)
                files.forEachIndexed {index,file ->
                    Column(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().clickable {expanded=if(expanded==file.id)null else file.id}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                            Label("${index+1}".padStart(2,'0'),20,if(file.enabled)c.accentText else c.muted,Modifier.width(42.dp))
                            Column(Modifier.weight(1f)) {
                                Label(file.displayName,15)
                                Label(if(file.error!=null)library.errorText(file.error,os.chinese)else if(file.enabled)os.t("参与显示","included")else os.t("已隐藏","hidden"),14,if(file.enabled)c.accentText else c.muted)
                            }
                            Glyph(if(expanded==file.id)"minus"else "plus",Modifier.size(22.dp),c.muted)
                        }
                        if(expanded==file.id) {
                            Toggle(os.t("参与此图层","include in this layer"),file.enabled,os.t("关闭后保留文件和顺序","keeps the file and its priority when off")) {library.toggle(file)}
                            Label(file.filename+" · ${decimal(file.bytes/1_000_000.0)} MB",13,c.muted)
                            if(file.attribution.isNotBlank())Label(android.text.Html.fromHtml(file.attribution,0).toString(),12,c.muted)
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                MetroButton(os.t("上移","move up"),{library.moveFile(file,-1)},Modifier.weight(1f),enabled=index>0)
                                MetroButton(os.t("下移","move down"),{library.moveFile(file,1)},Modifier.weight(1f),enabled=index<files.lastIndex)
                            }
                            if(file.error==null)MenuRow(os.t("在海图查看此范围","view this extent on chart"),os.formatCoordinates(file.focus)) {
                                // 查看位置不重新打开已隐藏的文件，也不悄悄创建或修改图层。
                                if(folder.layerName!=null)os.maps.select(MapSource.CustomLayer(folder.id))
                                os.fly(file.focus,file.previewZoom);os.openLinked("chart")
                            }
                            MenuRow(os.t("重命名显示名称","rename display name")) {namingFile=file}
                            MenuRow(os.t("从图册移除","remove from library"),os.t("保留原文件，可在管理中恢复","keeps the file; restore from manage")) {removeFile=file}
                        }
                    }
                }
                if(folder.uri!="copy")MetroButton(os.t("扫描新增或替换的文件","scan added or replaced files"),{library.rescan(folder)},enabled=!library.busy)
            } else {
                Label(folderName(os,folder),20,c.accentText)
                Label(os.t("${files.size} 张海图 · ${decimal(files.sumOf {it.bytes}/1_000_000.0)} MB","${files.size} charts · ${decimal(files.sumOf {it.bytes}/1_000_000.0)} MB"),15,c.muted)
                MetroButton(if(folder.layerName==null)os.t("创建图层","create layer")else os.t("重命名图层","rename layer"),{naming=true},primary=true)
                MetroButton(os.t("重命名文件夹标签","rename folder label"),{namingFolder=true})
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MetroButton(os.t("全部显示","include all"),{library.includeAll(folder,true)},Modifier.weight(1f),enabled=files.isNotEmpty())
                    MetroButton(os.t("全部隐藏","hide all"),{library.includeAll(folder,false)},Modifier.weight(1f),enabled=files.isNotEmpty())
                }
                val excluded=library.excludedFiles.filter {it.source==folder.uri}
                if(excluded.isNotEmpty()) {
                    AppSection(os.t("已移除的海图","removed charts"))
                    excluded.forEach {file ->MenuRow(file.displayName,os.t("点按恢复到图层末尾","tap to restore at the end"),"plus") {library.restore(file)}}
                }
                if(folder.layerName!=null)MetroButton(os.t("删除图层","delete layer"),{removeLayer=true})
                MetroButton(os.t("断开文件夹","disconnect folder"),{disconnect=true},enabled=!library.busy)
                Label(os.t("图册只管理引用，原文件不会被删除。重新扫描会保留你的排序、显示名称和移除记录。","The library manages references. Original files remain. Rescanning preserves priorities, display names and removed items."),15,c.muted)
            }
        }}
    }
    if(naming)TextDialog(os,os.t("图层名称","layer name"),folder.layerName ?: folderName(os,folder),{naming=false}) {library.setLayer(folder,it);naming=false}
    if(namingFolder)TextDialog(os,os.t("文件夹标签","folder label"),folderName(os,folder),{namingFolder=false}) {library.renameFolder(folder,it);namingFolder=false}
    namingFile?.let {file ->TextDialog(os,os.t("海图显示名称","chart display name"),file.displayName,{namingFile=null}) {library.renameFile(file,it);namingFile=null}}
    removeFile?.let {file ->ConfirmDialog(os,os.t("从图册移除 ${file.displayName}？原文件保留。","Remove ${file.displayName} from the library? Keep the original file."),{removeFile=null}) {library.forget(file);removeFile=null;expanded=null}}
    if(removeLayer)ConfirmDialog(os,os.t("删除图层？保留文件夹和海图，正在使用此图层的地图将切回离线底图。","Delete this layer? Keep its folder and charts; an active layer switches to the offline basemap."),{removeLayer=false}) {os.maps.removingLayer(folder.id);library.removeLayer(folder);removeLayer=false}
    if(disconnect)ConfirmDialog(os,os.t("断开 ${folderName(os,folder)}？原文件保留。","Disconnect ${folderName(os,folder)}? Keep original files."),{disconnect=false}) {os.maps.removingLayer(folder.id);library.forgetFolder(folder.uri);disconnect=false;os.back()}
}

@Composable private fun LibraryProgress(os:OsStore) {
    val library=os.library
    if(library.busy) {MetroProgress(os.t("正在读取…","reading…"));Label(library.progress,14,LocalMetro.current.muted)}
    library.failure?.let {Label(library.errorText(it,os.chinese),17,Color(0xFFE47C4C))}
    if(library.rejected>0)Label(os.t("${library.rejected} 个文件未能读取","${library.rejected} files could not be read"),14,LocalMetro.current.muted)
}
private fun folderName(os:OsStore,folder:ChartFolder)=if(folder.uri=="copy" && folder.name=="imported charts")os.t("本机导入","local imports")else folder.name
private fun viewLayer(os:OsStore,folder:ChartFolder) {
    val file=os.library.folderFiles(folder).firstOrNull {it.enabled && it.error==null}
    os.maps.select(MapSource.CustomLayer(folder.id))
    if(os.shell.backDestination(null)=="chart") {
        // 这次访问是海图交来的修复任务：恢复原海图实例，不再嵌套打开第二次海图。
        os.back()
    } else {
        file?.let {os.fly(it.focus,it.previewZoom)}
        os.openLinked("chart")
    }
}

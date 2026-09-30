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

/** 图册统一配置两类来源：海图文件夹供显示，数据目录供查询与规划。 */
@Composable fun LibraryScreen(os:OsStore,initialPage:Int=0) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.title(AppId.LIBRARY))
        Pivot(listOf(os.t("海图","Charts"),os.t("数据","Data")),initialPage=initialPage) {page->
            if(page==0)RasterLibraryPane(os)else Column(Modifier.fillMaxSize()) {
                LibraryDataSettings(os)
                Box(Modifier.weight(1f).fillMaxWidth()){StructuredChartLibraryPane(os)}
            }
        }
    }
}

@Composable private fun RasterLibraryPane(os:OsStore) {
    val library=os.library
    // 导入只建立文件夹，不增加资料用途或命名确认。
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->uri?.let(library::linkFolder)}
    val single=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(library::importCopy)}
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { PageBody {
            LibraryProgress(os)
            LibraryBackgroundSettings(os)
            Label(os.t(".yklchart 图包 / MBTiles",".yklchart packages / MBTiles"),13,LocalMetro.current.muted)
            if(library.folders.isEmpty()) {
                Label(os.t("添加你的第一张海图","Add your first chart"),20)
                Label(os.t("导入海图文件夹、.yklchart 图包或 MBTiles 文件。各文件的名称、范围和来源资料自动保留。","Import a chart folder, .yklchart package or MBTiles file. Each file keeps its own name, coverage and source details."),15,LocalMetro.current.muted)
            }
            library.folders.forEach {entry ->
                val charts=library.folderFiles(entry)
                val used=os.maps.source==MapSource.CustomLayer(entry.id)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ChoiceRow(folderName(os,entry),used,
                            os.t("${charts.count {it.enabled && it.error==null}} / ${charts.size} 张参与显示","${charts.count {it.enabled && it.error==null}} / ${charts.size} charts included")+
                                if(entry.packageId!=null)os.t(" · 离线图包"," · Offline package")else "") {
                            selectChartFolder(os,entry.id)
                        }
                    }
                    IconAction("settings",os.t("管理文件夹：","Manage folder: ")+(folderName(os,entry)),{os.open("library:${entry.id}")})
                }
            }
            Spacer(Modifier.height(8.dp))
            Label(os.t("水深、障碍和自动规划使用的数据，请在“数据”页指定。更换海图背景不更换航行数据。","Configure depths, hazards and automatic-routing data in Data. Changing the chart background does not change navigation data."),13,LocalMetro.current.muted)
        } }
        AppCommandBar(os,listOf(
            AppCommand("link-folder","folder",os.t("导入文件夹","Import folder"),{folder.launch(null)},enabled=!library.busy),
            AppCommand("import-chart","plus",os.t("导入海图文件","Import chart file"),{single.launch(arrayOf("*/*"))},enabled=!library.busy),
        ))
    }
}

@Composable fun LibraryFolderScreen(os:OsStore,folderId:String) {
    val library=os.library
    val folder=library.folders.firstOrNull {it.id==folderId}
    if(folder==null) {LaunchedEffect(folderId) {if(os.page=="library:$folderId")os.back()};return}
    val files=library.folderFiles(folder)
    val c=LocalMetro.current
    var namingFolder by remember {mutableStateOf(false)}
    var editingMetadata by remember {mutableStateOf(false)}
    var fileMetadata by remember {mutableStateOf<ChartFile?>(null)}
    var namingFile by remember {mutableStateOf<ChartFile?>(null)}
    var removeFile by remember {mutableStateOf<ChartFile?>(null)}
    var disconnect by remember {mutableStateOf(false)}
    var expanded by rememberSaveable(folderId) {mutableStateOf<String?>(null)}
    val included=files.count {it.enabled && it.error==null}
    val used=os.maps.source==MapSource.CustomLayer(folder.id)
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri ->
        uri?.let {library.exportFolder(folder,it)}
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,folderName(os,folder),os.title(AppId.LIBRARY))
        Pivot(listOf(os.t("海图","charts"),os.t("管理","manage"))) {page ->PageBody {
            LibraryProgress(os)
            if(page==0) {
                Label(os.t("$included 张参与海图显示","$included charts included in background"),20,c.accentText)
                ChoiceRow(os.t("用此文件夹显示自定义海图","Use this folder for custom charts"),used,
                    os.t("点选立即生效，无须再次确认","Applies immediately; no further confirmation")) {selectChartFolder(os,folder.id)}
                if(used)MetroButton(os.t("在海图查看","View on chart"),{viewLayer(os,folder)},enabled=included>0)
                ChartSourceSaveStatus(os)
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
                            Toggle(os.t("参与此文件夹显示","Include in this folder"),file.enabled,os.t("关闭后保留文件和顺序","keeps the file and its priority when off"),enabled=!library.busy) {library.toggle(file)}
                            Label(file.filename+" · ${decimal(file.bytes/1_000_000.0)} MB",13,c.muted)
                            if(file.attribution.isNotBlank())Label(chartDisplayText(file.attribution,700),12,c.muted)
                            MenuRow(os.t("文件资料","File metadata"),os.t("名称、范围、来源等原始字段","Original name, coverage and source fields")) {fileMetadata=file}
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                MetroButton(os.t("上移","move up"),{library.moveFile(file,-1)},Modifier.weight(1f),enabled=index>0&&!library.busy)
                                MetroButton(os.t("下移","move down"),{library.moveFile(file,1)},Modifier.weight(1f),enabled=index<files.lastIndex&&!library.busy)
                            }
                            if(file.error==null)MenuRow(os.t("在海图查看此范围","view this extent on chart"),os.formatCoordinates(file.focus)) {
                                selectChartFolder(os,folder.id)
                                os.fly(file.focus,file.previewZoom);os.openLinked("chart")
                            }
                            MenuRow(os.t("重命名显示名称","rename display name")) {namingFile=file}
                            MenuRow(os.t("从图册移除","remove from library"),os.t("保留原文件，可在管理中恢复","keeps the file; restore from manage")) {removeFile=file}
                        }
                    }
                }
                if(folder.uri!="copy")MetroButton(if(folder.packageId!=null)os.t("重新检查离线副本","Recheck offline copy")else os.t("扫描新增或替换的文件","Scan added or replaced files"),{library.rescan(folder)},enabled=!library.busy)
            } else {
                Label(folderName(os,folder),20,c.accentText)
                Label(os.t("${files.size} 张海图 · ${decimal(files.sumOf {it.bytes}/1_000_000.0)} MB","${files.size} charts · ${decimal(files.sumOf {it.bytes}/1_000_000.0)} MB"),15,c.muted)
                if(folder.packageId!=null) {
                    Label(os.t("已安装离线图包 · 原 .yklchart 文件可移走；重新导入同一包可更新。","Installed offline package · The original .yklchart file is no longer needed. Reimport the same package to update it."),14,c.muted)
                    Label(os.t("图包是资料容器，不代表官方海图认证，也不会授予自动规划用途。","Packaging does not certify an official chart or grant permission for automatic planning."),13,c.muted)
                }
                MetroButton(os.t("重命名文件夹","Rename folder"),{namingFolder=true},primary=true,enabled=!library.busy)
                library.folderMetadata(folder).filterValues {it.isNotBlank()}.forEach { (key,value) ->
                    Label(chartDisplayText(key,80),13,c.muted)
                    Label(chartDisplayText(value,8192),14)
                }
                MetroButton(os.t("编辑文件夹资料","Edit folder metadata"),{editingMetadata=true},enabled=!library.busy)
                val exportCount=library.allFolderFiles(folder).size
                MetroButton(os.t("导出整个文件夹 · $exportCount 个文件","Export entire folder · $exportCount files"),{
                    val name=folderName(os,folder).replace(Regex("[\\\\/:*?\"<>|]"),"_").take(100).ifBlank {"charts"}
                    export.launch("$name.yklchart")
                },enabled=!library.busy && exportCount>0)
                Label(os.t("导出全部已导入的文件，包含隐藏和从图册移除的项目；各文件保留原始内容与资料，按文件夹顺序打包。","Exports every imported file, including hidden and removed items, in folder order with original contents and metadata."),13,c.muted)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MetroButton(os.t("全部显示","include all"),{library.includeAll(folder,true)},Modifier.weight(1f),enabled=files.isNotEmpty()&&!library.busy)
                    MetroButton(os.t("全部隐藏","hide all"),{library.includeAll(folder,false)},Modifier.weight(1f),enabled=files.isNotEmpty()&&!library.busy)
                }
                val excluded=library.excludedFiles.filter {it.source==folder.uri}
                if(excluded.isNotEmpty()) {
                    AppSection(os.t("已移除的海图","removed charts"))
                    excluded.forEach {file ->MenuRow(file.displayName,os.t("点按恢复到文件夹末尾","Tap to restore at the end"),"plus") {library.restore(file)}}
                }
                MetroButton(if(folder.packageId!=null)os.t("移除离线图包","Remove offline package")else os.t("断开文件夹","Disconnect folder"),{disconnect=true},enabled=!library.busy)
                Label(if(folder.packageId!=null)os.t("移除图包会删除应用内副本，原 .yklchart 文件保留。重新检查会保留排序、显示名称和移除记录。","Removing this package deletes its installed copy and keeps the original .yklchart file. Rechecking preserves priorities, names and removed items.")
                    else os.t("原文件不会被删除。重新扫描会保留排序、显示名称、文件夹资料和移除记录。","Original files remain. Rescanning preserves priorities, display names, folder metadata and removed items."),15,c.muted)
            }
        }}
    }
    if(namingFolder)TextDialog(os,os.t("文件夹名称","Folder name"),folderName(os,folder),{namingFolder=false}) {library.renameFolder(folder,it);namingFolder=false}
    if(editingMetadata)MetadataEditorDialog(os,os.t("文件夹资料","Folder metadata"),library.folderMetadata(folder),
        onDismiss={editingMetadata=false},onSave={library.updateFolderMetadata(folder,it) {editingMetadata=false}},
        busy=library.busy,failure=library.failure?.let {library.errorText(it,os.chinese)})
    fileMetadata?.let {file ->AppDialog(onDismissRequest={fileMetadata=null}) {AppDialogSurface {
        AppDialogTitle(file.displayName)
        Label(file.filename,14,c.muted)
        Label(os.t("缩放 ${file.minZoom}–${file.maxZoom} · ${file.tileSize} px · ${file.scheme}","Zoom ${file.minZoom}–${file.maxZoom} · ${file.tileSize} px · ${file.scheme}"),14,c.muted)
        file.metadata.forEach {(key,value)->Label(chartDisplayText(key,80),13,c.muted);Label(chartDisplayText(value,8192),14)}
        if(file.metadataTruncated)Label(os.t("部分字段超过显示限额；完整原始资料仍保存在 MBTiles 文件中，并随文件一起导出。","Some fields exceed the display limit. Complete source metadata remains in the MBTiles file and is exported with it."),13,c.muted)
        if(file.packageMetadata.isNotEmpty()) {
            AppSection(os.t("图包中的文件资料","Package file metadata"))
            file.packageMetadata.forEach {(key,value)->Label(chartDisplayText(key,80),13,c.muted);Label(chartDisplayText(value,8192),14)}
        }
        MetroButton(os.t("关闭","Close"),{fileMetadata=null})
    }}}
    namingFile?.let {file ->TextDialog(os,os.t("海图显示名称","chart display name"),file.displayName,{namingFile=null}) {library.renameFile(file,it);namingFile=null}}
    removeFile?.let {file ->ConfirmDialog(os,os.t("从图册移除 ${file.displayName}？原文件保留。","Remove ${file.displayName} from the library? Keep the original file."),{removeFile=null}) {library.forget(file);removeFile=null;expanded=null}}
    if(disconnect)ConfirmDialog(os,if(folder.packageId!=null)os.t("移除 ${folderName(os,folder)} 的离线副本？原图包文件保留。","Remove the installed copy of ${folderName(os,folder)}? Keep the original package file.")
        else os.t("断开 ${folderName(os,folder)}？原文件保留。","Disconnect ${folderName(os,folder)}? Keep original files."),{disconnect=false}) {
        disconnect=false
        library.forgetFolder(folder.uri) {os.maps.removingLayer(folder.id);if(os.page=="library:${folder.id}")os.back()}
    }
}

@Composable private fun LibraryProgress(os:OsStore) {
    val library=os.library
    if(library.busy) {MetroProgress(if(library.exporting)os.t("正在导出…","Exporting…")else os.t("正在处理…","Working…"));Label(library.progress,14,LocalMetro.current.muted)}
    if(library.exporting)MetroButton(os.t("取消导出","Cancel export"),library::cancelExport)
    if(library.exportComplete)Label(os.t("整个文件夹已导出为 .yklchart。","The entire folder was exported as .yklchart."),14,LocalMetro.current.accentText)
    library.failure?.let {Label(library.errorText(it,os.chinese),17,Color(0xFFE47C4C))}
    if(library.rejected>0)Label(os.t("${library.rejected} 个文件未能读取","${library.rejected} files could not be read"),14,LocalMetro.current.muted)
}
private fun folderName(os:OsStore,folder:ChartFolder)=if(folder.uri=="copy" && folder.displayName=="imported charts")os.t("本机导入","Local imports")else folder.displayName
private fun viewLayer(os:OsStore,folder:ChartFolder) {
    val file=os.library.folderFiles(folder).firstOrNull {it.enabled && it.error==null}
    if(!selectChartFolder(os,folder.id))return
    if(os.shell.backDestination(null)=="chart") {
        os.back()
    } else {
        file?.let {os.fly(it.focus,it.previewZoom)}
        os.openLinked("chart")
    }
}

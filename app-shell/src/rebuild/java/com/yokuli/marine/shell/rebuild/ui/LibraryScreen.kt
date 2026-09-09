package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.ChartFile

@Composable fun LibraryScreen(os:OsStore) {
    val library=os.library
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(library::linkFolder) }
    val single=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(library::importCopy) }
    var remove by remember { mutableStateOf<ChartFile?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("海图库","chart library"))
        Pivot(listOf(os.t("海图","charts"),os.t("文件夹","folders"))) { page ->
            PageBody {
                if(library.busy) {
                    Label(os.t("正在读取…","reading…"),25,LocalMetro.current.accent)
                    Label(library.progress,14,LocalMetro.current.muted)
                }
                library.failure?.let { Label(library.errorText(it,os.chinese),17,androidx.compose.ui.graphics.Color(0xFFE47C4C)) }
                if(library.rejected>0) Label(os.t("${library.rejected} 个文件未能读取","${library.rejected} files could not be read"),14,LocalMetro.current.muted)
                if(page==0) {
                    if(library.files.isEmpty()) {
                        Label(os.t("你的海图，\n随时在手边。","your charts,\nclose at hand."),35)
                        Label(os.t("支持 PNG、JPEG、WebP 栅格 MBTiles。原文件始终保留；需要时会生成本机读取副本。","PNG, JPEG and WebP raster MBTiles. Originals are preserved; a local reading copy is created when needed."),18,LocalMetro.current.muted)
                    }
                    for(file in library.files) {
                        Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Toggle(file.name,file.enabled,"${decimal(file.bytes/1_000_000.0)} MB · z${file.minZoom}–${file.maxZoom} · ${file.scheme.uppercase()}") { library.toggle(file) }
                            if(file.error!=null) Label(library.errorText(file.error,os.chinese),15)
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                MetroButton(os.t("查看海图","view chart"),{
                                    library.showOnly(file);os.mapMode="marine";os.fly(file.focus,file.previewZoom);os.open("chart");os.save()
                                },Modifier.weight(1f),primary=true)
                                IconAction("close",os.t("移出目录","forget"),{remove=file})
                            }
                        }
                    }
                    MetroButton(os.t("连接海图文件夹","connect chart folder"),{folder.launch(null)},enabled=!library.busy)
                    MetroButton(os.t("导入离线副本","import offline copy"),{single.launch(arrayOf("*/*"))},enabled=!library.busy)
                    if(library.files.size>12) Label(os.t("最多同时显示 12 张海图。请关闭暂时不用的海图。","Up to 12 charts can be shown together. Turn off charts you don't need."),15,LocalMetro.current.muted)
                } else {
                    library.folders.forEach { uri ->
                        Label(android.net.Uri.decode(uri.substringAfterLast('/')).substringAfter(':'),25)
                        Label(os.t("只读连接","read-only connection"),14,LocalMetro.current.muted)
                        MetroButton(os.t("断开文件夹","disconnect folder"),{library.forgetFolder(uri)})
                    }
                    if(library.folders.isEmpty()) Label(os.t("还没有连接文件夹","no folders connected"),29)
                    MetroButton(os.t("连接文件夹","connect folder"),{folder.launch(null)},primary=true,enabled=!library.busy)
                    MetroButton(os.t("重新扫描","rescan"),{library.rescan()},enabled=!library.busy && library.folders.isNotEmpty())
                    Label(os.t("新增或替换文件后点重新扫描。部分文件提供器需要本机读取副本，会占用存储空间。断开连接不会删除原文件。","Rescan after adding or replacing files. Some file providers need a local reading copy, which uses device storage. Disconnecting never deletes originals."),16,LocalMetro.current.muted)
                }
            }
        }
    }
    remove?.let { file -> ConfirmDialog(os,os.t("将 ${file.name} 移出目录？文件会保留。","Forget ${file.name}? The file will be kept."),{remove=null}) {library.forget(file);remove=null} }
}

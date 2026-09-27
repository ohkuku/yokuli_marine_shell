package com.yokuli.marine.shell.rebuild.extensions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.ui.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** 应用中心只管理扩展包和授权；内置应用的生命周期、业务数据继续由各系统服务拥有。 */
@Composable fun AppCenterScreen(os: OsStore, section: String = "") {
    if(section == "developers") { DeveloperGuide(os); return }
    val manager = os.extensions
    val installed by manager.installed.collectAsState()
    val packages by os.packages.entries.collectAsState()
    val ready by manager.ready.collectAsState()
    val registryError by manager.errors.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var candidate by remember { mutableStateOf<ExtensionCandidate?>(null) }
    var manage by remember { mutableStateOf<String?>(null) }
    val pager = rememberPagerState { 2 }
    var catalog by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            catalog = withContext(Dispatchers.IO) {
                val json = os.context.assets.open("extensions/catalog.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONArray("apps")
                (0 until json.length()).map { json.getJSONObject(it) }
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { error = os.t("应用列表未能读取", "Could not load app catalog") }
    }
    fun stage(uri: Uri? = null, asset: String? = null) {
        if(busy) return
        busy = true; error = null
        scope.launch {
            var staged: ExtensionCandidate? = null
            try {
                staged = withContext(Dispatchers.IO) {
                    val stream = if(uri != null) os.context.contentResolver.openInputStream(uri)
                        else os.context.assets.open("extensions/${requireNotNull(asset)}")
                    requireNotNull(stream) { "Could not read package" }.use { manager.inspect(it) }
                }
                candidate = staged
            } catch(e: CancellationException) { staged?.let { withContext(NonCancellable) { manager.discard(it) } }; throw e }
            catch(e: Exception) { error = e.message ?: os.t("无法读取安装包", "Could not read package") }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) stage(uri = uri) }
    // 预览不是安装。离开页面或取消都回收临时包；安装流程由系统作用域完成，结果可在已安装列表恢复。
    val operationRunning by rememberUpdatedState(busy)
    DisposableEffect(candidate) { val old = candidate; onDispose { if(!operationRunning) old?.let { os.scope.launch { manager.discard(it) } } } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.t("应用中心", "App Center"))
        PivotHeaders(listOf(os.t("发现", "Discover"),os.t("已安装", "Installed")), pager)
        HorizontalPager(pager,Modifier.weight(1f),verticalAlignment=Alignment.Top) { tab ->
        PageBody {
            if(!ready || busy) Label(os.t("正在处理…", "Working…"), 14, LocalMetro.current.muted)
            (registryError ?: error)?.let { Label(it, 14); }
            if(tab==0) {
                AppSection(os.t("为航行添一点便利", "Make time aboard simpler"), os.t("离线应用 · 安装前由你决定授权", "Offline apps · You choose their access"))
                catalog.forEach { item ->
                    val current=installed.firstOrNull { it.manifest.id==item.getString("id") }
                    val update = current != null && item.getInt("version") > current.manifest.version
                    MenuRow(if(os.chinese)item.getString("name")else item.getString("nameEn"),
                        if(update)os.t("有更新 · 查看", "Update available · Review")else if(current!=null)os.t("已安装 · 打开", "Installed · Open")else if(os.chinese)item.optString("description")else item.optString("descriptionEn"),"apps") {
                        if(update && ready && registryError==null) stage(asset=item.getString("asset"))
                        else if(current!=null) os.shell.openLinked("extension:${current.manifest.id}")
                        else if(ready&&registryError==null) stage(asset=item.getString("asset"))
                    }
                }
                MetroButton(os.t("从文件安装", "Install from file"), { picker.launch(arrayOf("application/zip","application/octet-stream","*/*")) }, enabled=ready&&!busy&&registryError==null)
                Label(os.t("安装 .ykl 应用包，在 Yokuli 中运行。", "Install a .ykl app to run inside Yokuli."), 13, LocalMetro.current.muted)
            } else {
                if(installed.isEmpty()&&ready) Label(os.t("还没有安装扩展应用", "No extensions installed"), 16)
                installed.forEach { app -> MenuRow(if(os.chinese)app.manifest.name else app.manifest.nameEn,os.t("版本 ${app.manifest.version} · 授权与管理", "Version ${app.manifest.version} · Access & management"),"apps") { manage=app.manifest.id } }
                AppSection(os.t("随系统提供", "Built into Yokuli"),os.t("这些应用会一直保留", "These apps stay with the system"))
                packages.filter { it.origin == YklOrigin.SYSTEM_IMAGE }.forEach { app ->
                    MenuRow(os.t(app.name, app.nameEn), app.error ?: os.t("系统 .ykl · 版本 ${app.version}", "System .ykl · Version ${app.version}"), app.hostApp?.icon) { os.shell.openLinked(app.rootRoute) }
                }
            }
            MenuRow(os.t("为 Yokuli 开发应用", "Build for Yokuli"),os.t("SDK、界面组件与离线指南", "SDK, UI components & offline guide"),"data") { os.open("app_center:developers") }
        }
        }
    }
    candidate?.let { preview ->
        val existing=installed.firstOrNull {it.manifest.id==preview.manifest.id}
        // 更新保留用户已经收回的授权；新增系统控制能力需要明确选择，不能随更新默默获得。
        var grants by remember(preview) { mutableStateOf(existing?.grants?.intersect(preview.manifest.permissions)
            ?: preview.manifest.permissions.filterNot { it.endsWith(".control") }.toSet()) }
        AppDialog(onDismissRequest={if(!busy)candidate=null}) { AppDialogSurface {
            AppDialogTitle(if(os.chinese)preview.manifest.name else preview.manifest.nameEn)
            Label(if(os.chinese)preview.manifest.description else preview.manifest.descriptionEn,14)
            Label(os.t("版本 ${preview.manifest.version}","Version ${preview.manifest.version}"),13,LocalMetro.current.muted)
            Label(os.t("由文件提供的应用，请确认来源。授权可随时收回。", "Only install packages from a source you trust. Access can be revoked."),13,LocalMetro.current.muted)
            preview.manifest.permissions.sorted().forEach { permission ->
                Toggle(permissionTitle(os,permission), permission in grants, onChange={ checked -> grants=if(checked)grants+permission else grants-permission })
            }
            error?.let { Label(it, 14, LocalMetro.current.muted) }
            Label("SHA-256 · ${preview.digest.take(16)}…",12,LocalMetro.current.muted)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MetroButton(if(existing==null)os.t("安装", "Install")else os.t("更新", "Update"), {
                    busy=true; error=null
                    os.scope.launch {
                        try { manager.install(preview,grants); candidate=null; scope.launch { pager.animateScrollToPage(1) } }
                        catch(e:CancellationException){throw e}
                        catch(e:Exception){error=e.message}
                        finally{busy=false}
                    }
                },Modifier.weight(1f),primary=true,enabled=!busy)
                MetroButton(os.t("取消", "Cancel"), {candidate=null},Modifier.weight(1f),enabled=!busy)
            }
        } }
    }
    installed.firstOrNull {it.manifest.id==manage}?.let { app ->
        var deleteConfirmation by remember(app.manifest.id) { mutableStateOf(false) }
        AppDialog(onDismissRequest={if(!busy)manage=null}) { AppDialogSurface {
            AppDialogTitle(if(os.chinese)app.manifest.name else app.manifest.nameEn)
            if(deleteConfirmation) {
                Label(os.t("卸载将删除此应用的本地资料。桌面快捷磁贴会保留，供你重新安装或移除。", "Uninstall removes this app’s local data. Start shortcuts remain so you can reinstall or remove them."),14)
                MetroButton(os.t("卸载应用", "Uninstall app"), {
                    busy=true;os.scope.launch {
                        try{manager.uninstall(app.manifest.id);manage=null}
                        catch(e:CancellationException){throw e}
                        catch(e:Exception){error=e.message;manage=null}
                        finally{busy=false}
                    }
                },primary=true,enabled=!busy)
                MetroButton(os.t("保留", "Keep app"), {deleteConfirmation=false},enabled=!busy)
            } else {
                MetroButton(os.t("打开", "Open"), {manage=null;os.shell.openLinked("extension:${app.manifest.id}")},primary=true)
                app.manifest.permissions.sorted().forEach { permission ->
                    Toggle(permissionTitle(os,permission),permission in app.grants,onChange={checked->
                        if(!busy) { busy=true;os.scope.launch {
                            try{manager.setGrants(app.manifest.id,if(checked)app.grants+permission else app.grants-permission)}
                            catch(e:CancellationException){throw e}
                            catch(e:Exception){error=e.message;manage=null}
                            finally{busy=false}
                        } }
                    })
                }
                MetroButton(os.t("卸载…", "Uninstall…"), {deleteConfirmation=true},enabled=!busy)
                MetroButton(os.t("完成", "Done"),{manage=null},enabled=!busy)
            }
        } }
    }
}
private fun permissionTitle(os:OsStore,key:String)=when(key){
    "marine.read"->os.t("读取选用的船舶数据", "Read selected vessel data")
    "nmea.read"->os.t("查看 NMEA 连接状态", "View NMEA connection status")
    "navigation.open"->os.t("打开相关系统应用", "Open related system apps")
    "lab.read"->os.t("查看演练环境与系统录像", "Read practice environment and recordings")
    "lab.control"->os.t("切换真实/模拟环境并控制设备和时钟", "Switch real/simulated environments and control devices and clock")
    "devices.read"->os.t("查看设备与采集状态", "View devices and collection status")
    "sources.read"->os.t("查看数据来源与选用情况", "View data sources and selections")
    "sources.control"->os.t("更改全船数据来源", "Change vessel data sources")
    "nmea.control"->os.t("查看并启停已配置的连接", "View and switch configured connections")
    "sharing.read"->os.t("查看本机分享状态", "View sharing status")
    "sharing.control"->os.t("查看并启停本机分享", "View and switch sharing")
    "voyage.read"->os.t("查看当前记录与操作回执", "View recording and operation receipts")
    "voyage.control"->os.t("开始、暂停或结束航行记录", "Start, pause or finish recording")
    else->key
}

@Composable private fun DeveloperGuide(os:OsStore) {
    var error by remember {mutableStateOf<String?>(null)}
    var exporting by remember {mutableStateOf(false)}
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(uri != null && !exporting) {
            exporting=true;error=null
            os.scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        os.context.assets.open("extensions/developers/yokuli-sdk-2.zip").use { source ->
                            requireNotNull(os.context.contentResolver.openOutputStream(uri,"wt")) {"Could not open destination"}.use { destination -> source.copyTo(destination) }
                        }
                    }
                    error=os.t("SDK 已导出", "SDK exported")
                } catch(e:CancellationException){throw e}
                catch(e:Exception){
                    withContext(Dispatchers.IO) { runCatching { android.provider.DocumentsContract.deleteDocument(os.context.contentResolver,uri) } }
                    error=os.t("未能保存 SDK，请重新选择位置", "Could not save SDK. Choose another location.")
                } finally{exporting=false}
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("开发者指南", "Developer guide"),hasLocalBack=true)
        if(error!=null) Label(error!!,14)
        MetroButton(if(exporting)os.t("正在导出…", "Exporting…")else os.t("保存 SDK 与模板", "Save SDK & templates"),
            {export.launch("yokuli-sdk-2.zip")},Modifier.padding(horizontal=22.dp),primary=true,enabled=!exporting)
        MetroButton(os.t("打开 SDK 源码与模板", "Open SDK source & templates"), {
            runCatching { os.context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse("https://github.com/ohkuku/yokuli_marine_shell/tree/codex/yokuli-os-rom/sdk")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { error=os.t("没有可以打开链接的浏览器", "No browser is available") }
        },Modifier.padding(horizontal=22.dp))
        val docs=remember {
            ExtensionInstalled(ExtensionManifest("org.yokuli.guide","开发者指南","Developer guide",1,"",emptySet()),"bundled",0L,emptySet(),File(os.context.filesDir,"sdk-guide-unpacked"))
        }
        ExtensionWebView(docs,enabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current,assetLoader={ path ->
            if(path in setOf("index.html","site.css","site.js"))runCatching{os.context.assets.open("extensions/developers/$path")}.getOrNull()else null
        },onRequest={_,_->throw IllegalStateException("Guide has no system access")},modifier=Modifier.fillMaxSize(),onError={error=it})
    }
}

@Composable fun ExtensionScreen(os:OsStore,id:String) {
    val installed by os.extensions.installed.collectAsState()
    val app=installed.firstOrNull{it.manifest.id==id}
    val ready by os.extensions.ready.collectAsState()
    val registryError by os.extensions.errors.collectAsState()
    var failure by remember(id,app?.digest,app?.installedAt,app?.grants,app?.authorizationEpoch) { mutableStateOf<String?>(null) }
    var retry by remember(id) {mutableIntStateOf(0)}
    if(app==null) {
        Column { PageHeader(os,os.t("应用", "App"));PageBody {
            Label(if(!ready)os.t("正在恢复应用…", "Restoring apps…")else if(registryError!=null)os.t("安装记录暂不可读", "Installation records could not be read")else os.t("此应用尚未安装", "This app is not installed"),18)
            MetroButton(os.t("打开应用中心", "Open App Center"),{os.open("app_center")})
        } };return
    }
    // 版本或授权变化立即销毁旧 WebView；旧桥不能持有已撤销的数据能力。
    key(app.digest,app.installedAt,app.grants,app.authorizationEpoch,retry) {
        val bridge=remember { ExtensionMarineBridge(os, app) }
        Column(Modifier.fillMaxSize()) {
            PageHeader(os,if(os.chinese)app.manifest.name else app.manifest.nameEn)
            if(failure!=null) PageBody {
                Label(os.t("应用已暂停", "App paused"),18)
                Label(failure!!,14)
                MetroButton(os.t("重新打开", "Reopen"),{failure=null;retry++})
            } else ExtensionWebView(app,enabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current,assetLoader={path->
                if(path in setOf("_sdk/yokuli.js","_sdk/yokuli.css"))runCatching{os.context.assets.open("extensions/sdk/${path.substringAfterLast('/')}")}.getOrNull()else null
            },onRequest=bridge::request,modifier=Modifier.fillMaxSize(),onError={failure=it})
        }
    }
}

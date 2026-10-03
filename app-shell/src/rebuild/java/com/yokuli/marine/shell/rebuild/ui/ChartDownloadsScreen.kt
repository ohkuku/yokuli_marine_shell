package com.yokuli.marine.shell.rebuild.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.uid
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val CHART_DOWNLOAD_SITE = "https://ohkuku.github.io/yokuli_marine_shell/charts/"

/** 页面只管理筛选和访问；下载、摘要校验、续传与文件归属由 Core 唯一持有。 */
@Composable fun ChartDownloadsScreen(os: OsStore, section: String = "") {
    val service = os.marine?.system?.chartStore
    if (service == null) {
        Column { PageHeader(os, os.title(AppId.CHART_STORE)); PageBody { MetroProgress(os.t("正在连接系统…", "Connecting to system…")) } }
        return
    }
    val state by service.state.collectAsState()
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var commandPending by remember { mutableStateOf(false) }
    var pendingPermissionPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPermissionJob by rememberSaveable { mutableStateOf<String?>(null) }
    var removeDownload by remember { mutableStateOf<OfficialChartDownload?>(null) }

    fun command(action: suspend () -> Unit) {
        if (commandPending || state.loading) return
        commandPending = true; feedback = null
        // 提交动作不依赖详情页继续可见；离开页面不会中止 Core 已接受的下载或导入。
        os.scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { feedback = chartStoreProblem(os, failure.message.orEmpty()) }
            finally { commandPending = false }
        }
    }
    fun download(id: String) {
        val origin=os.page
        command { service.download(id, uid()); if(os.page==origin)os.open("chart_store:downloads") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingPermissionPackage; pendingPermissionPackage = null
        val job = pendingPermissionJob; pendingPermissionJob = null
        if (granted && job != null) command { service.retry(job) }
        else if (granted && id != null) download(id)
        else if (!granted) feedback = os.t("允许储存权限后，才能把资料包保存到 Documents。", "Allow storage access to save packages in Documents.")
    }
    fun requestDownload(id: String) {
        if (state.storagePermissionRequired) {
            pendingPermissionPackage = id
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else download(id)
    }
    fun retry(job:OfficialChartDownload) {
        if(state.storagePermissionRequired) {
            pendingPermissionJob=job.id
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else command {service.retry(job.id)}
    }
    fun importCollection(job: OfficialChartDownload) {
        val origin=os.page
        command {
            if (os.maps.bundles.busy) {
                feedback = os.t("图册正在处理其他资料，完成后再导入。", "Chart Library is processing another collection. Import this one when it finishes.")
                return@command
            }
            val uri = Uri.parse(service.readyUri(job.id))
            val existing = os.maps.bundles.bundles.firstOrNull { it.sourceUri == uri.toString() }
            if (existing != null) { if(os.page==origin)os.openLinked("library:bundle/${existing.id}") }
            else {
                os.maps.bundles.importPackage(uri, ownedDownload = true)
                if(os.page==origin)os.openLinked("library")
            }
        }
    }
    LaunchedEffect(service, state.loading) {
        if (!state.loading && (state.updatedAtMillis == null || System.currentTimeMillis() - state.updatedAtMillis!! > 6 * 60 * 60 * 1000)) {
            try { service.refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { feedback = chartStoreProblem(os, failure.message.orEmpty()) }
        }
    }
    val itemId = section.takeIf { it.startsWith("package/") }?.substringAfter("package/")?.let(Uri::decode)
    var detail by remember(itemId) { mutableStateOf<OfficialChartPackage?>(null) }
    var loadingDetail by remember(itemId) { mutableStateOf(false) }
    LaunchedEffect(itemId, state.catalogueRevision) {
        if (itemId == null) return@LaunchedEffect
        loadingDetail = true
        try {
            detail = service.browse(query = itemId).packages.firstOrNull { it.id == itemId }
                ?: state.downloads.firstOrNull { it.item.id == itemId }?.item
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { feedback = chartStoreProblem(os, failure.message.orEmpty()) }
        finally { loadingDetail = false }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, detail?.let { chartStoreName(os, it) } ?: os.title(AppId.CHART_STORE), hasLocalBack = itemId != null)
        if (itemId != null) PageBody {
            feedback?.let { Label(it, 13, LocalMetro.current.muted) }
            val item = detail
            if (loadingDetail && item == null) MetroProgress(os.t("正在读取资料", "Loading collection"))
            else if (item == null) {
                Label(os.t("这份资料包已不在当前目录中。", "This collection is no longer in the current catalogue."), 15)
                MetroButton(os.t("返回浏览", "Back to browse"), os::back)
            } else {
                val job = state.downloads.firstOrNull { it.item.sha256 == item.sha256 && it.item.id == item.id }
                Label(chartStoreRegion(os, item), 13, LocalMetro.current.muted)
                Label(os.t(item.description, item.descriptionEn).ifBlank { chartStoreContents(os, item) }, 15)
                Label(chartStoreContents(os, item), 13, LocalMetro.current.muted)
                Label("${chartStoreBytes(item.bytes)} · ${item.version}", 15)
                if (job == null) {
                    MetroButton(os.t("下载资料包", "Download collection"), { requestDownload(item.id) }, primary = true,
                        enabled = !commandPending && !state.loading && item.status == "published")
                    Label(os.t("下载完成后，由你决定何时导入图册。", "Import into Chart Library whenever you are ready."), 12, LocalMetro.current.muted)
                    if(item.status!="published") Label(os.t("资料包正在发布，完成后即可下载。","This collection is being published. Download it when publication completes."),13,LocalMetro.current.muted)
                } else ChartDownloadItem(os, job, commandPending || state.loading,
                    onImport = { importCollection(job) }, onCancel = { command { service.cancel(job.id) } },
                    onRetry = { retry(job) }, onRemove = { removeDownload = job }, showName = false)
                AppSection(os.t("保存位置", "Saved to"))
                Label(job?.destination?.takeIf(String::isNotBlank) ?: chartStoreDestination(state,item), 12, LocalMetro.current.muted)
                AppSection(os.t("资料来源", "Sources"))
                if (item.provider.isNotBlank()) Label(item.provider, 15)
                if (item.sourceDate.isNotBlank()) Label(os.t("资料获取日期 · ", "Source download date · ") + item.sourceDate.substringBefore('T'), 12, LocalMetro.current.muted)
                if (item.license.isNotBlank()) Label(item.license, 12, LocalMetro.current.muted)
                if (item.attribution.isNotBlank()) Label(item.attribution, 12, LocalMetro.current.muted)
                val notice = os.t(item.sourceNotice, item.sourceNoticeEn)
                if (notice.isNotBlank()) Label(notice, 12, LocalMetro.current.muted)
            }
        } else Pivot(listOf(os.t("浏览", "Browse"), os.t("下载", "Downloads")), initialPage = if (section == "downloads") 1 else 0) { page ->
            if (page == 0) ChartStoreBrowse(os, service, state, feedback, commandPending,
                onRefresh = { command { service.refresh() } }, onOpenWebsite = {
                    runCatching { os.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHART_DOWNLOAD_SITE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { feedback = os.t("没有可以打开网页的浏览器。", "No browser is available.") }
                })
            else PageBody {
                feedback?.let { Label(it, 13, LocalMetro.current.muted) }
                if (state.loading) MetroProgress(os.t("正在读取下载记录", "Loading downloads"))
                else if (state.downloads.isEmpty()) {
                    AppSection(os.t("为下次出航准备", "Ready for your next passage"))
                    Label(os.t("在“浏览”中选择海域。下载保存在 Documents，离线也能导入。", "Choose a region in Browse. Downloads stay in Documents and can be imported offline."), 15, LocalMetro.current.muted)
                }
                state.downloads.sortedByDescending { it.createdAtMillis }.forEach { job -> key(job.id) {
                    ChartDownloadItem(os, job, commandPending || state.loading, onImport = { importCollection(job) },
                        onCancel = { command { service.cancel(job.id) } }, onRetry = { retry(job) },
                        onRemove = { removeDownload = job },
                        onDetails = { os.open("chart_store:package/${Uri.encode(job.item.id)}") })
                } }
                if (state.downloads.isNotEmpty()) Label(state.destination, 12, LocalMetro.current.muted)
            }
        }
    }
    removeDownload?.let { job -> ConfirmDialog(os,
        os.t("删除这份下载文件？已导入图册的资料会保留。", "Delete this download? Collections already imported into Chart Library will remain."),
        onDismiss = { removeDownload = null }) {
        removeDownload = null
        command { service.remove(job.id, deleteFile = true) }
    } }
}

@Composable private fun ChartStoreBrowse(os: OsStore, service: OfficialChartStore, state: OfficialChartStoreState,
    feedback: String?, commandPending: Boolean, onRefresh: () -> Unit, onOpenWebsite: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var chooseCountry by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable(query, country) { mutableIntStateOf(0) }
    var page by remember(service, query, country, offset) { mutableStateOf(OfficialChartPage()) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(service, query, country, offset, state.catalogueRevision, state.loading) {
        if (state.loading) return@LaunchedEffect
        loading = true; failure = null
        try { if (query.isNotBlank()) delay(180); page = service.browse(query.trim(), country, offset, 40) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = chartStoreProblem(os, error.message.orEmpty()) }
        finally { loading = false }
    }
    PageBody {
        Field(os.t("搜索海域", "Search regions"), query, { query = it.take(120) })
        val selectedRegion = state.regions.firstOrNull { it.country == country }
        MenuRow(os.t("海域", "Region"), selectedRegion?.let { os.t(it.name, it.nameEn) } ?: os.t("全部", "All regions")) { chooseCountry = true }
        (failure ?: feedback ?: state.catalogueError?.let { chartStoreProblem(os, it) })?.let { Label(it, 13, LocalMetro.current.muted) }
        if (state.loading || loading && page.packages.isEmpty()) MetroProgress(os.t("正在读取目录", "Loading catalogue"))
        if (!state.loading && !loading && page.packages.isEmpty()) Label(
            if (query.isNotBlank() || country.isNotBlank()) os.t("没有找到匹配的海域。", "No matching region found.")
            else os.t("目录暂时无法读取，请稍后刷新。", "The catalogue is unavailable. Refresh when connected."), 15, LocalMetro.current.muted)
        page.packages.forEach { item ->
            val ready = state.downloads.firstOrNull { it.item.id == item.id && it.item.sha256 == item.sha256 && it.phase == ChartDownloadPhase.READY }
            MenuRow(chartStoreName(os, item), listOf(
                chartStoreRegion(os, item), chartStoreBytes(item.bytes),
                if (ready != null) os.t("已下载", "Downloaded") else if (item.recommended) os.t("推荐", "Recommended") else ""
            ).filter(String::isNotBlank).joinToString(" · "), "download") {
                os.open("chart_store:package/${Uri.encode(item.id)}")
            }
            Label(chartStoreContents(os, item), 12, LocalMetro.current.muted)
        }
        if (offset > 0 || page.hasMore) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(os.t("上一页", "Previous"), { offset = (offset - 40).coerceAtLeast(0) }, Modifier.weight(1f), enabled = offset > 0 && !loading)
            MetroButton(os.t("下一页", "Next"), { offset += 40 }, Modifier.weight(1f), enabled = page.hasMore && !loading)
        }
        if (page.total > 0) Label(os.t("共 ${page.total} 份资料包", "${page.total} collections"), 12, LocalMetro.current.muted)
        if (state.refreshing) MetroProgress(os.t("正在更新目录", "Updating catalogue"))
        else MetroButton(os.t("刷新目录", "Refresh catalogue"), onRefresh, enabled = !commandPending && !state.loading)
        state.updatedAtMillis?.let { date ->
            val formatted = DateFormat.getDateInstance(DateFormat.MEDIUM, if (os.chinese) Locale.CHINESE else Locale.ENGLISH).format(Date(date))
            Label(os.t("目录更新于 ", "Catalogue updated ") + formatted, 12, LocalMetro.current.muted)
        }
        MenuRow(os.t("在网站浏览", "Browse on the web"), "yokuli os", "export", onOpenWebsite)
    }
    if (chooseCountry) AppDialog(onDismissRequest = { chooseCountry = false }) { AppDialogSurface {
        AppDialogTitle(os.t("选择海域", "Choose a region"))
        ChoiceRow(os.t("全部海域", "All regions"), country.isBlank()) { country = ""; chooseCountry = false }
        state.regions.forEach { region ->
            ChoiceRow(os.t(region.name, region.nameEn), country == region.country,
                os.t("${region.count} 份资料包", "${region.count} collections")) { country = region.country; chooseCountry = false }
        }
        MetroButton(os.t("关闭", "Close"), { chooseCountry = false })
    } }
}

@Composable private fun ChartDownloadItem(os: OsStore, job: OfficialChartDownload, busy: Boolean,
    onImport: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit,
    onDetails: (() -> Unit)? = null, showName: Boolean = true) {
    val c = LocalMetro.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showName) {
            if (onDetails != null) MenuRow(chartStoreName(os, job.item), "${chartStoreBytes(job.item.bytes)} · ${job.item.version}", onClick = onDetails)
            else Label(chartStoreName(os, job.item), 18)
        }
        Label(chartDownloadPhase(os, job), 15)
        when (job.phase) {
            ChartDownloadPhase.DOWNLOADING, ChartDownloadPhase.WAITING, ChartDownloadPhase.QUEUED, ChartDownloadPhase.VERIFYING -> {
                val verifying = job.phase == ChartDownloadPhase.VERIFYING
                val bytes = if (verifying) job.verifiedBytes else job.receivedBytes
                ChartDownloadProgress(bytes, job.item.bytes)
                Label("${chartStoreBytes(bytes)} / ${chartStoreBytes(job.item.bytes)}", 12, c.muted)
                MetroButton(os.t("取消下载", "Cancel download"), onCancel, enabled = !busy)
            }
            ChartDownloadPhase.READY -> {
                MetroButton(os.t("在图册使用", "Use in Chart Library"), onImport, primary = true, enabled = !busy && !os.maps.bundles.busy)
                if (os.maps.bundles.busy) Label(os.t("图册正在处理资料，完成后即可导入。", "Chart Library is busy. Import when it finishes."), 12, c.muted)
                MetroButton(os.t("删除下载文件", "Delete downloaded file"), onRemove, enabled = !busy)
            }
            else -> {
                job.reason?.let { Label(chartStoreProblem(os, it), 13, c.muted) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetroButton(os.t("重新下载", "Download again"), onRetry, Modifier.weight(1f), enabled = !busy)
                    MetroButton(os.t("移除", "Remove"), onRemove, Modifier.weight(1f), enabled = !busy)
                }
            }
        }
    }
}

@Composable internal fun ChartDownloadProgress(bytes: Long, total: Long) {
    val c = LocalMetro.current
    val fraction = if (total > 0) (bytes.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
    Canvas(Modifier.fillMaxWidth().height(4.dp).semantics {
        progressBarRangeInfo = if (total > 0) ProgressBarRangeInfo(fraction, 0f..1f) else ProgressBarRangeInfo.Indeterminate
    }) {
        drawLine(c.controlFill, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
        drawLine(c.fg, Offset(0f, size.height / 2), Offset(size.width * fraction, size.height / 2), size.height)
    }
}

internal fun chartStoreName(os: OsStore, item: OfficialChartPackage) = os.t(item.name, item.nameEn).ifBlank { item.name }
internal fun chartStoreRegion(os: OsStore, item: OfficialChartPackage) = os.t(item.countryName, item.countryNameEn).ifBlank { item.country }
private fun chartStoreDestination(state:OfficialChartStoreState,item:OfficialChartPackage):String {
    val subdirectory=if(state.destination.endsWith("Chart Packages"))item.downloadSubdirectory.removePrefix("Chart Packages/")else item.downloadSubdirectory
    return listOf(state.destination,subdirectory,item.collectionId,item.fileName).filter(String::isNotBlank).joinToString("/")
}
internal fun chartStoreBytes(value: Long): String = when {
    value >= (1L shl 30) -> String.format(Locale.US, "%.2f GiB", value.toDouble() / (1L shl 30))
    value >= (1L shl 20) -> String.format(Locale.US, "%.1f MiB", value.toDouble() / (1L shl 20))
    value >= (1L shl 10) -> String.format(Locale.US, "%.0f KiB", value.toDouble() / (1L shl 10))
    else -> "$value B"
}
private fun chartStoreContents(os: OsStore, item: OfficialChartPackage) = item.contentTypes.mapNotNull { type -> when (type) {
    "charts", "chart", "yklcharts", "mbtiles" -> os.t("离线海图", "Offline charts")
    "geodata", "data", "yklgeodata" -> os.t("水深与地物数据", "Depths & features")
    "navigation", "routing", "prepared-navigation" -> os.t("离线航路", "Offline routing")
    "terrain", "3d", "prepared-terrain" -> os.t("三维地形", "3D terrain")
    "sources" -> os.t("原始资料", "Source files")
    else -> null
} }.distinct().joinToString(" · ").ifBlank { os.t("离线资料包", "Offline collection") }

internal fun chartDownloadPhase(os: OsStore, job: OfficialChartDownload): String = when (job.phase) {
    ChartDownloadPhase.QUEUED -> os.t("等待下载", "Queued")
    ChartDownloadPhase.DOWNLOADING -> os.t("正在下载", "Downloading")
    ChartDownloadPhase.WAITING -> if(job.reason.orEmpty().contains("WIFI",true)) os.t("等待 Wi-Fi，连接后继续", "Waiting for Wi-Fi; resumes automatically")
        else os.t("等待网络，连接后继续", "Waiting for a connection; resumes automatically")
    ChartDownloadPhase.VERIFYING -> os.t("正在核对文件完整性", "Checking file integrity")
    ChartDownloadPhase.READY -> os.t("已下载，可离线使用", "Downloaded; ready offline")
    ChartDownloadPhase.FAILED -> os.t("下载未完成", "Download did not finish")
    ChartDownloadPhase.CANCELLED -> os.t("已取消", "Cancelled")
    ChartDownloadPhase.MISSING -> os.t("下载文件已移走", "Downloaded file is missing")
}

internal fun chartStoreProblem(os: OsStore, raw: String): String = when {
    raw.contains("REAL_WORLD_REQUIRED", true) -> os.t("请先退出演练，再管理真实下载文件。", "Leave the simulation before managing real downloads.")
    raw.contains("CORE_RECONNECTING", true) -> os.t("正在重新连接系统，已有下载会继续。", "Reconnecting to the system. Existing downloads continue.")
    raw.contains("PERMISSION", true) || raw.contains("ACCESS", true) -> os.t("无法读取或保存文件，请检查系统储存权限后重试。", "File access is unavailable. Check storage permissions, then retry.")
    raw.contains("DOWNLOAD_LIMIT",true)||raw.contains("REQUEST_LIMIT",true)||raw.contains("STORAGE_LIMIT",true) -> os.t("下载记录已满，请移除不再需要的下载后重试。", "The download list is full. Remove downloads you no longer need, then retry.")
    raw.contains("SPACE", true) || raw.contains("STORAGE", true) -> os.t("储存空间不足或无法写入，请检查 Documents 文件夹。", "Storage is full or unavailable. Check the Documents folder.")
    raw.contains("CATALOGUE",true) -> os.t("目录暂时无法更新。已有资料和下载仍保留，可稍后刷新。", "The catalogue could not be updated. Existing collections and downloads are kept. Refresh later.")
    raw.contains("NOT_PUBLISHED",true) -> os.t("这份资料包仍在发布，请稍后刷新目录。", "This collection is still being published. Refresh the catalogue later.")
    raw.contains("FILE_EXISTS",true) -> os.t("保存位置已有同名文件。请先在文件管理器中移走它，再重新下载。", "A file with this name already exists. Move it in your file manager before downloading again.")
    raw.contains("DELETE_FAILED",true) -> os.t("未能删除下载文件，请在文件管理器中检查。", "The downloaded file could not be deleted. Check it in your file manager.")
    raw.contains("NOT_READY",true)||raw.contains("CANCEL_FIRST",true)||raw.contains("RETRY_NOT_AVAILABLE",true) -> os.t("下载状态已改变，请查看当前进度后再操作。", "The download state has changed. Review its current progress before another action.")
    raw.contains("ALREADY_READY",true) -> os.t("资料包已下载，可在图册使用。", "The collection is already downloaded and ready for Chart Library.")
    raw.contains("CANNOT_RESUME",true) -> os.t("这次下载无法续传，请重新下载。", "This download cannot be resumed. Download it again.")
    raw.contains("VERIFY_FAILED",true) -> os.t("文件校验未完成，请重新下载。", "File verification did not finish. Download it again.")
    raw.contains("LEDGER",true) -> os.t("下载记录暂时无法读取，现有文件保留。", "Download records could not be read. Existing files are kept.")
    raw.contains("SHA", true) || raw.contains("DIGEST", true) || raw.contains("HASH", true) || raw.contains("SIZE", true) || raw.contains("CHECKSUM", true) -> os.t("下载文件不完整，请重新下载。", "The downloaded file is incomplete. Download it again.")
    raw.contains("MISSING", true) || raw.contains("NOT_FOUND", true) -> os.t("文件或目录条目已变更，请刷新目录或重新下载。", "The file or catalogue entry has changed. Refresh the catalogue or download again.")
    raw.contains("UNKNOWN", true) || raw.contains("TIMEOUT", true) -> os.t("操作结果仍在确认，请先查看下载列表。", "The operation is still being confirmed. Check Downloads before retrying.")
    raw.contains("BUSY", true) -> os.t("操作正在进行，请稍候。", "An operation is in progress. Please wait.")
    else -> os.t("暂时无法完成，请检查网络后重试。已有下载会保留。", "Could not complete this action. Check your connection and retry. Existing downloads are kept.")
}

package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.yokuli.chartpackage.ChartPackageSource
import com.yokuli.chartpackage.YokuliChartPackage
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.chartpackage.YokuliAtlasPackage
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 两个现有海图册对象的绑定，不复制海图、数据或来源选择的业务状态。 */
data class ChartBundle(val id:String,val name:String,val chartFolderId:String?=null,val datasetId:String?=null,
    val sourceUri:String="",val metadata:Map<String,String> = emptyMap(),val issue:String?=null)
enum class ChartBundlePhase { VERIFYING, IMPORTING_DATA, IMPORTING_CHARTS, EXPORTING, COMMITTING, CLEANING, COMPLETE, CANCELLED, FAILED, INTERRUPTED }
/** 对象量直接来自 Core 当前文件，不能换算为整包的虚构百分比。 */
data class ChartBundleTask(val requestId:String,val name:String,val phase:ChartBundlePhase,val detail:String="",
    val fileIndex:Int=0,val fileCount:Int=0,val completed:Int=0,val total:Int=0,val cancellable:Boolean=true,val retryable:Boolean=true,
    val processedBytes:Long=0,val totalBytes:Long=0)

/**
 * Shell 的组合安装事务：持久保存阶段/回执，原资料仍由 ChartLibrary 与 Marine Core 唯一拥有。
 * 每代安装使用独立来源，两个子项均成功才原子发布组合；退出页面不会取消，进程恢复继续对账。
 */
class ChartBundleStore(context:Context,private val scope:CoroutineScope,private val library:ChartLibrary) {
    private val app=context.applicationContext
    private val root=File(app.filesDir,"chart-bundles")
    private val index=AtomicFile(File(app.filesDir,"chart-bundles.json"))
    private data class Installed(val value:ChartBundle,val generation:String,
        val ownedCharts:List<String> = emptyList(),val ownedData:Boolean=false,val directories:List<String> = emptyList())
    private data class Pending(val requestId:String,val sourceUri:String,val name:String,val generation:String,
        val phase:ChartBundlePhase=ChartBundlePhase.VERIFYING,val bundleId:String?=null,
        val chartsPath:String?=null,val dataPath:String?=null,val chartFolderId:String?=null,val datasetId:String?=null,
        val metadata:Map<String,String> = emptyMap(),val cancelled:Boolean=false,
        val operation:String="PACKAGE",val sourceIsFolder:Boolean=false,val dataOwned:Boolean=true,
        val protectedDatasetIds:List<String> = emptyList(),val failureDetail:String="")
    private var installed=emptyList<Installed>()
    /** 仅保留本模块拥有的待清理旧代；崩溃后仍可完成，不删除用户独立资料。 */
    private var retired=emptyList<Installed>()
    private var pending:Pending?=null
    private data class ExportPending(val requestId:String,val bundleId:String,val targetUri:String,val sourceFingerprint:String)
    private var pendingExport:ExportPending?=null
    private var migrated=false
    private var migratedSelection:String?=null
    private var service:ChartDataService?=null
    private var worker:Job?=null
    private var observer:Job?=null
    private var readable=true
    private val persistenceMutex=Mutex()
    private val operationMutex=Mutex()
    private var operationCount=0
    private fun reserveOperation(){operationCount++;busy=true}
    private fun releaseOperation(){operationCount--;busy=operationCount>0}
    private fun launchOperation(replacing:Job?=null,block:suspend ()->Unit):Job {
        reserveOperation()
        return scope.launch {
            try {
                // 在锁外停止旧 owner；旧协程的 finally 不能释放新操作的 busy 保留。
                replacing?.cancelAndJoin()
                operationMutex.withLock {block()}
            }finally {releaseOperation()}
        }
    }
    private suspend fun <T> exclusive(block:suspend ()->T):T=withContext(Dispatchers.Main.immediate) {
        reserveOperation()
        try {operationMutex.withLock {block()}}finally {releaseOperation()}
    }
    var bundles by mutableStateOf<List<ChartBundle>>(emptyList())
        private set
    var task by mutableStateOf<ChartBundleTask?>(null)
        private set
    var issue by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private val restored=scope.async(Dispatchers.IO) {
        runCatching {
            if(!index.baseFile.exists()&&!File(index.baseFile.path+".bak").exists())JSONObject()
            else JSONObject(index.openRead().bufferedReader().use {it.readText()})
        }
    }
    private var restoreApplied=false

    private suspend fun restore() {
        if(restoreApplied)return
        try {
            val data=restored.await().getOrThrow()
            if(restoreApplied)return
            fun entries(key:String)=data.optJSONArray(key)?.let {array->(0 until array.length()).map {n->
                val j=array.getJSONObject(n)
                Installed(ChartBundle(j.getString("id"),j.getString("name"),j.optional("chartFolderId"),j.optional("datasetId"),j.getString("sourceUri"),metadata(j)),j.getString("generation").also(::checkGeneration),j.strings("ownedCharts"),j.optBoolean("ownedData"),j.strings("directories"))
            }}.orEmpty()
            installed=entries("bundles");retired=entries("retired");migrated=data.optBoolean("migrated");migratedSelection=data.optional("migratedSelection")
            pending=data.optJSONObject("pending")?.let {j->Pending(j.getString("requestId"),j.getString("sourceUri"),j.getString("name"),
                j.getString("generation").also(::checkGeneration),ChartBundlePhase.valueOf(j.getString("phase")),j.optional("bundleId"),
                j.optional("chartsPath"),j.optional("dataPath"),j.optional("chartFolderId"),j.optional("datasetId"),metadata(j),j.optBoolean("cancelled"),j.optString("operation","PACKAGE"),j.optBoolean("sourceIsFolder"),j.optBoolean("dataOwned",true),j.strings("protectedDatasetIds"),j.optString("failureDetail"))}
            pendingExport=data.optJSONObject("export")?.let {ExportPending(it.getString("requestId"),it.getString("bundleId"),it.getString("targetUri"),it.getString("sourceFingerprint"))}
            publish()
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){readable=false;issue="ATLAS_CATALOG_UNREADABLE"}
        restoreApplied=true
    }

    fun connect(charts:ChartDataService) {
        if(service===charts)return
        service=charts
        observer?.cancel()
        observer=scope.launch {
            restore()
            combine(charts.state,snapshotFlow {library.revision}) {state,_->state}.collect {publish(it)}
        }
        // 旧 Binder 等待被取消不代表已接受的 Core 导入取消；同 requestId 重连对账。
        val previousWorker=worker
        worker=launchOperation(replacing=previousWorker) {
            try {
                restore()
                if(!readable)return@launchOperation
                val p=pending
                if(pendingExport!=null)runExport(charts,requireNotNull(pendingExport))
                else if(p!=null&&p.phase !in setOf(ChartBundlePhase.FAILED,ChartBundlePhase.CANCELLED))runImport(charts)
                else {
                    if(p!=null) {
                        // 旧版 Shell 未保存原因时，从原 Core 请求回执恢复，用户无需抓取 logcat。
                        val state=charts.state.first {!it.loading}
                        val reason=p.failureDetail.ifBlank {
                            state.activeJob?.takeIf {it.requestId==dataRequestId(p)&&it.phase==ChartImportPhase.FAILED}?.detail.orEmpty()
                        }
                        task=ChartBundleTask(p.requestId,p.name,p.phase,reason,cancellable=false)
                        if(p.phase==ChartBundlePhase.FAILED)issue=reason.ifBlank {"ATLAS_IMPORT_FAILED"}
                    }
                    cleanupRetired(charts)
                }
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_RESTORE_FAILED"}
        }
    }

    fun importPackage(uri:Uri)=beginPackageImport(uri,null)

    /** 仅由 Core readyUri 回执进入：下载通知的标题并不等于真实文件名。 */
    internal fun importDownloadedPackage(uri:Uri,item:OfficialChartPackage)=beginPackageImport(uri,item)

    private fun beginPackageImport(uri:Uri,download:OfficialChartPackage?) {
        if(busy){issue="ATLAS_CORE_BUSY";return}
        issue=null
        val requestId=UUID.randomUUID().toString()
        task=ChartBundleTask(requestId,download?.name.orEmpty(),ChartBundlePhase.VERIFYING,cancellable=false,retryable=false)
        worker=launchOperation {
            try {
                restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
                check(service!=null){"ATLAS_CORE_UNAVAILABLE"}
                val name=withContext(Dispatchers.IO) {
                    if(download!=null)require(uri.scheme=="content"&&uri.authority=="downloads"&&uri.lastPathSegment?.toLongOrNull()!=null){"ATLAS_DOWNLOAD_URI_INVALID"}
                    val managed=ownedDownloadName(uri)
                    val display=download?.fileName ?: managed ?: sourceDisplayName(uri)
                    // 某些下载提供方给出的是无后缀标题；这时读取严格容器清单判别，不猜扩展名。
                    require(display==null||'.' !in display||display.endsWith(".yklpkg",true)){"ATLAS_EXTENSION_REQUIRED"}
                    if(uri.scheme=="content"&&download==null&&managed==null) {
                        try {app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                        catch(error:SecurityException){throw IllegalStateException("ATLAS_SOURCE_PERMISSION",error)}
                    }
                    val manifest=openPackage(uri).use {YokuliAtlasPackage.readManifest(it)}
                    if(download!=null)require(manifest.id==download.id){"ATLAS_DOWNLOAD_ID_MISMATCH"}
                    chartDisplayText(manifest.name,120).ifBlank {"atlas"}
                }
                // 未完成的旧事务先按自身所有权撤销，不让新的导入吞掉清理账本。
                pending?.let {rollback(requireNotNull(service),it);pending=null;persist()}
                pending=Pending(requestId,uri.normalizeScheme().toString(),name,UUID.randomUUID().toString())
                persist();runImport(requireNotNull(service))
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_IMPORT_FAILED";task=task?.copy(phase=ChartBundlePhase.FAILED,detail=requireNotNull(issue),cancellable=false,retryable=pending?.requestId==requestId)}
        }
    }

    private fun sourceDisplayName(uri:Uri):String?=if(uri.scheme=="file")File(requireNotNull(uri.path)).name
        else app.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->
            val column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if(column>=0&&cursor.moveToFirst())cursor.getString(column)else null
        }

    /** 仅查询本 UID 的真实系统下载，COLUMN_TITLE / DISPLAY_NAME 不用于后缀判定。 */
    private fun ownedDownloadName(uri:Uri):String? {
        if(uri.scheme!="content"||uri.authority!="downloads")return null
        val id=uri.lastPathSegment?.toLongOrNull()?:return null
        return app.getSystemService(DownloadManager::class.java).query(DownloadManager.Query().setFilterById(id))?.use {cursor->
            if(!cursor.moveToFirst())return@use null
            check(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL){"ATLAS_DOWNLOAD_NOT_READY"}
            val local=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))?.let(Uri::parse)
            local?.path?.substringAfterLast('/')?.takeIf {local.scheme=="file"}
        }
    }

    private fun openPackage(uri:Uri)=if(uri.scheme=="file")File(requireNotNull(uri.path)).inputStream()
        else app.contentResolver.openInputStream(uri)?:error("ATLAS_SOURCE_UNREADABLE")

    fun create(name:String) {
        if(busy)return
        issue=null
        worker=launchOperation {
            try {
                restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
                val title=chartDisplayText(name,120);require(title.isNotBlank()){"ATLAS_NAME_REQUIRED"}
                val id=UUID.randomUUID().toString()
                val next=installed+Installed(ChartBundle(id,title),id)
                persist(next,retired,pending);installed=next;publish()
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_CREATE_FAILED"}
        }
    }

    fun importCharts(bundleId:String,uri:Uri,folder:Boolean=false)=importComponent(bundleId,uri,folder,"CHARTS")
    /** 一个资料集合是一份完整源文件夹；更新由 Core 原子发布，不能将新目录伪装为追加。 */
    fun importData(bundleId:String,uri:Uri,folder:Boolean=false)=importComponent(bundleId,uri,folder,"DATA")

    fun rename(bundleId:String,name:String)=editBundle(bundleId) {record->
        val title=chartDisplayText(name,120);require(title.isNotBlank()){"ATLAS_NAME_REQUIRED"}
        record.copy(value=record.value.copy(name=title))
    }
    fun updateMetadata(bundleId:String,metadata:Map<String,String>)=editBundle(bundleId) {record->
        record.copy(value=record.value.copy(metadata=YokuliChartPackage.validateMetadata(metadata)))
    }
    fun clearCharts(bundleId:String)=editBundle(bundleId,removeCharts=true) {it.copy(value=it.value.copy(chartFolderId=null),ownedCharts=emptyList())}
    fun clearData(bundleId:String)=editBundle(bundleId,removeData=true) {it.copy(value=it.value.copy(datasetId=null),ownedData=false)}

    private fun editBundle(bundleId:String,removeCharts:Boolean=false,removeData:Boolean=false,change:(Installed)->Installed) {
        if(busy)return
        issue=null
        worker=launchOperation {
            try {
                restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
                val old=installed.firstOrNull {it.value.id==bundleId}?:error("ATLAS_MISSING")
                val next=installed.map {if(it==old)change(old)else it}
                val discarded=if(removeCharts||removeData)old.copy(value=old.value.copy(
                    chartFolderId=old.value.chartFolderId.takeIf {removeCharts},datasetId=old.value.datasetId.takeIf {removeData}),
                    ownedCharts=if(removeCharts)old.ownedCharts else emptyList(),ownedData=removeData&&old.ownedData,directories=emptyList())else null
                val nextRetired=retired+listOfNotNull(discarded)
                persist(next,nextRetired,pending);installed=next;retired=nextRetired;publish()
                service?.let {cleanupRetired(it)}
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_SAVE_FAILED"}
        }
    }

    suspend fun attachDataSource(bundleId:String,datasetId:String) = exclusive {
        restore()

            val charts=service?:error("ATLAS_CORE_UNAVAILABLE")
            require(datasetId==LINZ_ONLINE_DATASET_ID||charts.state.value.datasets.any {it.id==datasetId}){"ATLAS_DATA_MISSING"}
            val old=installed.firstOrNull {it.value.id==bundleId}?:error("ATLAS_MISSING")
            val next=installed.map {if(it==old)old.copy(value=old.value.copy(datasetId=datasetId),ownedData=old.ownedData&&old.value.datasetId==datasetId)else it}
            val discard=old.takeIf {it.ownedData&&it.value.datasetId!=datasetId}?.copy(value=old.value.copy(chartFolderId=null),ownedCharts=emptyList(),directories=emptyList())
            val nextRetired=retired+listOfNotNull(discard)
            persist(next,nextRetired,pending);installed=next;retired=nextRetired;publish();cleanupRetired(charts)
    }

    private fun importComponent(bundleId:String,uri:Uri,folder:Boolean,operation:String) {
        if(busy)return
        issue=null
        worker=launchOperation {
            try {
                restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
                val charts=service?:error("ATLAS_CORE_UNAVAILABLE")
                pending?.let {rollback(charts,it);pending=null;persist()}
                val bundle=installed.firstOrNull {it.value.id==bundleId}?:error("ATLAS_MISSING")
                withContext(Dispatchers.IO) {if(uri.scheme=="content")app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                val isNewData=charts.state.value.datasets.none {it.sourceUri==uri.toString()}
                pending=Pending(UUID.randomUUID().toString(),uri.toString(),bundle.value.name,UUID.randomUUID().toString(),
                    bundleId=bundleId,operation=operation,sourceIsFolder=folder,dataOwned=isNewData,protectedDatasetIds=charts.state.value.datasets.map {it.id})
                persist();runImport(charts)
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_IMPORT_FAILED"}
        }
    }

    /** 只迁移一次：现有明确搭配归同一包，其余现有目录各成一包，不复制/删除旧原件。 */
    suspend fun migrateLegacy(chartFolderId:String?=null,datasetId:String?=null):String? = exclusive {
        restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
        if(migrated)return@exclusive migratedSelection?.takeIf {id->installed.any {it.value.id==id}}

            val charts=service?:error("ATLAS_CORE_UNAVAILABLE")
            charts.state.first {!it.loading}
            val knownCharts=installed.mapNotNull {it.value.chartFolderId}.toSet()
            val knownData=installed.mapNotNull {it.value.datasetId}.toSet()
            val folders=library.folders.filter {it.id !in knownCharts&&it.packageId?.startsWith("atlas-")!=true&&library.folderFiles(it).isNotEmpty()}.toMutableList()
            val datasets=charts.state.value.datasets.filter {it.id !in knownData&&it.cells.isNotEmpty()&&it.sourceUri?.startsWith(Uri.fromFile(root).toString())!=true}.toMutableList()
            val additions=mutableListOf<Installed>()
            val selectedChart=folders.firstOrNull {it.id==chartFolderId}
            val selectedData=datasets.firstOrNull {it.id==datasetId}
            var selected:String?=null
            if(selectedChart!=null||selectedData!=null) {
                selected=UUID.randomUUID().toString()
                additions+=Installed(ChartBundle(selected,selectedChart?.displayName?:requireNotNull(selectedData).name,selectedChart?.id,selectedData?.id),selected)
                selectedChart?.let {folders.remove(it)};selectedData?.let {datasets.remove(it)}
            }
            folders.forEach {folder->val id=UUID.randomUUID().toString();additions+=Installed(ChartBundle(id,folder.displayName,chartFolderId=folder.id),id)}
            datasets.forEach {data->val id=UUID.randomUUID().toString();additions+=Installed(ChartBundle(id,data.name,datasetId=data.id),id)}
            val next=installed+additions
            migrated=true;migratedSelection=selected
            try {persist(next,retired,pending)}catch(error:Exception){migrated=false;migratedSelection=null;throw error}
            installed=next;publish();selected
    }

    /** 已生成 MBTiles 追加到原包；不自动生成，不改变该包的数据来源。 */
    suspend fun attachChartFolder(bundleId:String,folderId:String,owned:Boolean=false) = exclusive {
        restore();attachChartFolderNow(bundleId,folderId,owned)
    }

    private suspend fun attachChartFolderNow(bundleId:String,folderId:String,owned:Boolean) {
        val record=installed.firstOrNull {it.value.id==bundleId}?:error("ATLAS_MISSING")
        val current=record.value.chartFolderId
        val target=if(current==null)folderId else library.appendBundleFolder(current,folderId).id
        val next=installed.map {if(it==record)record.copy(value=record.value.copy(chartFolderId=target),
            ownedCharts=(record.ownedCharts+(if(owned)listOf(folderId)else emptyList())+listOfNotNull(target.takeIf {it!=current&&it!=folderId})).distinct())else it}
        persist(next,retired,pending);installed=next;publish()
    }

    private suspend fun runComponentImport(charts:ChartDataService,original:Pending) {
        var p=original
        val record=installed.firstOrNull {it.value.id==p.bundleId}?:error("ATLAS_MISSING")
        val updated:Installed
        var discarded:Installed?=null
        if(p.operation=="CHARTS") {
            if(p.chartFolderId==null) {
                phase(ChartBundlePhase.IMPORTING_CHARTS)
                val folder=library.importBundleSource(Uri.parse(p.sourceUri),p.sourceIsFolder,ownerIdentity(p.generation))
                p=p.copy(chartFolderId=folder.id);pending=p;persist()
            }
            val addition=requireNotNull(p.chartFolderId)
            val target=record.value.chartFolderId?.let {library.appendBundleFolder(it,addition).id}?:addition
            updated=record.copy(value=record.value.copy(chartFolderId=target),ownedCharts=(record.ownedCharts+addition+target).distinct())
        }else {
            if(p.datasetId==null) {
                phase(ChartBundlePhase.IMPORTING_DATA)
                val request=ChartImportRequest(dataRequestId(p),p.sourceUri,p.name)
                charts.state.first {!it.loading}
                var before=charts.state.value.activeJob
                var result=charts.importPackage(request)
                while(result is ChartCommandResult.Busy){delay(400);before=charts.state.value.activeJob;result=charts.importPackage(request)}
                val id=when(result) {
                    is ChartCommandResult.Saved->result.datasetId
                    is ChartCommandResult.Failed->error(result.reason)
                    is ChartCommandResult.Accepted->awaitDataReceipt(charts,request,p,before)
                    ChartCommandResult.Busy->error("ATLAS_CORE_BUSY")
                }
                p=p.copy(datasetId=id,dataOwned=p.dataOwned&&id !in p.protectedDatasetIds);pending=p;persist()
            }
            updated=record.copy(value=record.value.copy(datasetId=p.datasetId),ownedData=p.dataOwned||record.value.datasetId==p.datasetId&&record.ownedData)
            if(record.value.datasetId!=p.datasetId&&record.ownedData)discarded=record.copy(value=record.value.copy(chartFolderId=null),ownedCharts=emptyList(),directories=emptyList())
        }
        phase(ChartBundlePhase.COMMITTING,false)
        val next=installed.map {if(it==record)updated else it};val nextRetired=retired+listOfNotNull(discarded)
        withContext(NonCancellable) {persist(next,nextRetired,null);installed=next;retired=nextRetired;pending=null;publish()}
        task=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.COMPLETE,cancellable=false)
        cleanupRetired(charts)
    }

    fun retryImport() {
        if(busy)return
        issue=null
        worker=launchOperation {
            try {
                restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
                val charts=service?:error("ATLAS_CORE_UNAVAILABLE")
                pendingExport?.let {runExport(charts,it);return@launchOperation}
                val previous=pending?:run {cleanupRetired(charts);return@launchOperation}
                rollback(charts,previous)
                pending=if(previous.operation=="PACKAGE")Pending(UUID.randomUUID().toString(),previous.sourceUri,previous.name,UUID.randomUUID().toString())
                    else previous.copy(requestId=UUID.randomUUID().toString(),generation=UUID.randomUUID().toString(),phase=ChartBundlePhase.VERIFYING,
                        chartFolderId=null,datasetId=null,cancelled=false,protectedDatasetIds=charts.state.value.datasets.map {it.id},failureDetail="")
                persist();runImport(charts)
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_IMPORT_FAILED"}
        }
    }

    fun cancelImport() {
        if(task?.cancellable!=true)return
        val running=worker
        worker=launchOperation(replacing=running) {
            try {
                // 旧 owner 已结束，取消标记不会再被先前阶段回执覆盖。
                pendingExport?.let {request->
                    service?.cancelExport("atlas-export-${request.requestId}")
                    withContext(Dispatchers.IO) {runCatching {DocumentsContract.deleteDocument(app.contentResolver,Uri.parse(request.targetUri))}}
                    pendingExport=null;persist();task=task?.copy(phase=ChartBundlePhase.CANCELLED,cancellable=false,retryable=false)
                    return@launchOperation
                }
                val p=pending?:return@launchOperation
                pending=p.copy(cancelled=true);persist()
                val charts=service?:error("ATLAS_CORE_UNAVAILABLE")
                rollback(charts,requireNotNull(pending))
                pending=p.copy(cancelled=true,phase=ChartBundlePhase.CANCELLED);persist()
                task=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.CANCELLED,cancellable=false)
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_CLEANUP_FAILED"}
        }
    }

    /** 先持久解除组合，再清理它独占的子资料；普通海图和数据目录不按名称猜测删除。 */
    fun remove(bundleId:String) {
        if(busy)return
        worker=launchOperation {
            try {
                restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
                val target=installed.firstOrNull {it.value.id==bundleId}?:return@launchOperation
                val nextInstalled=installed-target;val nextRetired=retired+target
                persist(nextInstalled,nextRetired,pending)
                installed=nextInstalled;retired=nextRetired;publish()
                service?.let {cleanupRetired(it)}
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_REMOVE_FAILED"}
        }
    }

    private suspend fun runImport(charts:ChartDataService) {
        issue=null
        try {
            var p=pending?:return
            if(p.cancelled){
                rollback(charts,p);pending=p.copy(phase=ChartBundlePhase.CANCELLED);persist()
                task=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.CANCELLED,cancellable=false);return
            }
            if(p.operation!="PACKAGE") {runComponentImport(charts,p);return}
            if(p.bundleId==null) {
                task=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.VERIFYING)
                val active=currentCoroutineContext()
                val directory=generationDirectory(p.generation)
                val atlas=withContext(Dispatchers.IO) {
                    if(directory.exists())check(directory.deleteRecursively()){"ATLAS_STORAGE_FAILED"}
                    check(root.isDirectory||root.mkdirs()){"ATLAS_STORAGE_FAILED"}
                    val uri=Uri.parse(p.sourceUri)
                    val input=openPackage(uri)
                    var lastUpdate=0L
                    input.use {YokuliAtlasPackage.extractWithProgress(it,directory,progress={progress->
                        val now=android.os.SystemClock.elapsedRealtime()
                        if(now-lastUpdate>=200L||progress.packageBytesRead==progress.packageBytesTotal) {
                            lastUpdate=now
                            val update=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.VERIFYING,
                                fileIndex=progress.fileIndex,fileCount=progress.fileCount,
                                processedBytes=progress.packageBytesRead,totalBytes=progress.packageBytesTotal)
                            scope.launch {if(task?.requestId==update.requestId&&task?.phase==ChartBundlePhase.VERIFYING)task=update}
                        }
                    }) {active.ensureActive();check(root.usableSpace>128_000_000L){"ATLAS_STORAGE_FULL"}}}
                }
                p=p.copy(name=chartDisplayText(atlas.name,120),bundleId=atlas.id,chartsPath=atlas.charts?.path,dataPath=atlas.geodata?.path,metadata=atlas.metadata,phase=ChartBundlePhase.IMPORTING_DATA)
                pending=p;persist()
            }
            if(p.dataPath!=null&&p.datasetId==null) {
                phase(ChartBundlePhase.IMPORTING_DATA)
                val dataUri=Uri.fromFile(childFile(p,requireNotNull(p.dataPath))).toString()
                val request=ChartImportRequest(dataRequestId(p),dataUri,p.name)
                // 可读目录尚在恢复时等待真实 Core 状态，不把恢复延迟当成导入失败。
                charts.state.first {!it.loading}
                var before=charts.state.value.activeJob
                var result=charts.importPackage(request)
                while(result is ChartCommandResult.Busy) {
                    delay(400);currentCoroutineContext().ensureActive()
                    before=charts.state.value.activeJob
                    result=charts.importPackage(request)
                }
                val datasetId=when(result) {
                    is ChartCommandResult.Saved->result.datasetId
                    is ChartCommandResult.Failed->error(result.reason)
                    is ChartCommandResult.Accepted->awaitDataReceipt(charts,request,p,before)
                    ChartCommandResult.Busy->error("ATLAS_CORE_BUSY")
                }
                p=p.copy(datasetId=datasetId);pending=p;persist()
            }
            // Core 已完整安装并持久返回收据，后续恢复只用 datasetId；组合包内的压缩传输副本不再是数据源。
            // 先保存收据再清理，崩溃在两步之间也只重做幂等清理，不重新导入全国资料。
            if(p.datasetId!=null&&p.dataPath!=null)withContext(Dispatchers.IO) {
                removeDataTransport(childFile(p,requireNotNull(p.dataPath)))
            }
            if(p.chartsPath!=null&&p.chartFolderId==null) {
                phase(ChartBundlePhase.IMPORTING_CHARTS)
                val folder=library.importBundleCharts(Uri.fromFile(childFile(p,requireNotNull(p.chartsPath))),ownerIdentity(p.generation))
                p=p.copy(chartFolderId=folder.id);pending=p;persist()
            }
            phase(ChartBundlePhase.COMMITTING,false)
            val value=ChartBundle(requireNotNull(p.bundleId),p.name,p.chartFolderId,p.datasetId,p.sourceUri,p.metadata)
            val previous=installed.filter {it.value.id==value.id}
            // 绑定和清理账本在同一 AtomicFile 事务中发布；旧完整组合在此之前始终可用。
            withContext(NonCancellable) {
                val nextInstalled=installed.filterNot {it.value.id==value.id}+Installed(value,p.generation,listOfNotNull(p.chartFolderId),p.datasetId!=null,listOf(p.generation))
                val nextRetired=retired+previous
                persist(nextInstalled,nextRetired,null)
                installed=nextInstalled;retired=nextRetired;pending=null;publish()
            }
            task=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.COMPLETE,cancellable=false)
            cleanupRetired(charts)
        }catch(cancel:CancellationException) {
            // 进程/连接取消保留阶段以便继续；只有显式 cancelImport 才回滚。
            throw cancel
        }catch(error:Exception) {
            issue=(error.message?.takeIf {it.isNotBlank()}?:error.javaClass.simpleName).take(2_000)
            android.util.Log.w("YokuliAtlas","Collection import failed",error)
            val p=pending
            if(p!=null) {
                task=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.FAILED,requireNotNull(issue),cancellable=false)
                withContext(NonCancellable) {
                    pending=p.copy(phase=ChartBundlePhase.FAILED,failureDetail=requireNotNull(issue));persist()
                    runCatching {withTimeout(10_000){rollback(charts,p)}}.onFailure {
                        // 清理不是本次安装失败的根因，不能覆盖用户需要看到的原始原因。
                        android.util.Log.w("YokuliAtlas","Failed import cleanup will be retried",it)
                    }
                }
            }
        }
    }

    /** activeJob 只保留最近任务；跨 Binder 合流或后续导入不能吞掉本请求的完成回执。 */
    private suspend fun awaitDataReceipt(charts:ChartDataService,request:ChartImportRequest,p:Pending,before:ChartImportJob?):String {
        while(true) {
            val end=withTimeoutOrNull(2_000) {
                charts.state.onEach {state->state.activeJob?.takeIf {it.requestId==request.requestId}?.let {job->
                    task=ChartBundleTask(p.requestId,p.name,ChartBundlePhase.IMPORTING_DATA,job.detail,job.fileIndex,job.fileCount,
                        job.completed,job.total,job.phase!=ChartImportPhase.COMMITTING,
                        processedBytes=job.processedBytes,totalBytes=job.totalBytes)
                }}.first {state->state.activeJob?.let {it.requestId==request.requestId&&it.phase in terminalPhases&&
                    (it!=before||it.phase==ChartImportPhase.COMPLETE)}==true}
            }
            if(end!=null) {
                val job=requireNotNull(end.activeJob)
                check(job.phase==ChartImportPhase.COMPLETE){job.detail.ifBlank {"ATLAS_DATA_IMPORT_FAILED"}}
                return requireNotNull(job.datasetId)
            }
            currentCoroutineContext().ensureActive()
            if(charts.state.value.activeJob?.requestId==request.requestId)continue
            // 原 Core 命令账本先检查 requestId，再检查 busy；成功只返回原 datasetId，不重建资料。
            when(val receipt=charts.importPackage(request)) {
                is ChartCommandResult.Saved->return receipt.datasetId
                is ChartCommandResult.Failed->error(receipt.reason)
                is ChartCommandResult.Accepted,ChartCommandResult.Busy->Unit
            }
        }
    }

    private suspend fun rollback(charts:ChartDataService,p:Pending) {
        charts.cancelImport(dataRequestId(p))
        charts.state.first {state->state.loading.not()&&(state.activeJob?.let {it.requestId!=dataRequestId(p)||it.phase in terminalPhases}!=false)}
        val source=if(p.operation=="DATA")p.sourceUri else p.dataPath?.let {Uri.fromFile(childFile(p,it)).toString()}
        val dataset=p.datasetId?:charts.state.value.datasets.firstOrNull {it.sourceUri==source}?.id
        if(dataset!=null&&p.dataOwned&&dataset !in p.protectedDatasetIds)removeData(charts,dataset)
        val owner=ownerIdentity(p.generation)
        val folder=p.chartFolderId?:library.folders.firstOrNull {it.packageId==owner}?.id
        if(folder!=null) {
            if(p.operation=="CHARTS")installed.firstOrNull {it.value.id==p.bundleId}?.value?.chartFolderId?.takeIf {it!=folder}?.let {before->
                val merged=UUID.nameUUIDFromBytes("$before/$folder".toByteArray(Charsets.UTF_8)).toString()
                library.removeBundleCharts("package-$merged","atlas-$merged")
            }
            library.removeBundleCharts(folder,owner)
        }
        withContext(Dispatchers.IO) {val directory=generationDirectory(p.generation);if(directory.exists())check(directory.deleteRecursively()){"ATLAS_CLEANUP_FAILED"}}
    }

    private suspend fun cleanupRetired(charts:ChartDataService) {
        for(old in retired.toList()) {
            old.value.datasetId?.takeIf {old.ownedData&&installed.none {current->current.value.datasetId==it}}?.let {removeData(charts,it)}
            old.ownedCharts.filter {id->installed.none {id in it.ownedCharts||id==it.value.chartFolderId}}.forEach {id->
                library.folders.firstOrNull {it.id==id}?.packageId?.takeIf {it.startsWith("atlas-")}?.let {owner->library.removeBundleCharts(id,owner)}
            }
            for(generation in old.directories.filter {item->installed.none {item in it.directories}})withContext(Dispatchers.IO) {
                val directory=generationDirectory(generation);if(directory.exists())check(directory.deleteRecursively()){"ATLAS_CLEANUP_FAILED"}
            }
            val next=retired-old;persist(installed,next,pending);retired=next
        }
        cleanupInstalledDataTransports(charts)
    }

    /** 清理旧版本留下的已安装数据子包。只删除 Shell 私有、拥有且已完成安装的传输副本。 */
    private suspend fun cleanupInstalledDataTransports(charts:ChartDataService) {
        val state=charts.state.value
        if(state.loading)return
        val readableIds=state.datasets.filter{it.offlineReadable}.mapTo(hashSetOf()){it.id}
        val generations=installed.filter{it.ownedData&&it.value.datasetId in readableIds}
            .flatMap{it.directories}.distinct().filter{it!=pending?.generation}
        try {withContext(Dispatchers.IO) {
            val owner=root.canonicalFile
            for(generation in generations) {
                currentCoroutineContext().ensureActive()
                val directory=generationDirectory(generation).canonicalFile
                if(!directory.isDirectory||!directory.path.startsWith(owner.path+File.separator))continue
                directory.walkTopDown().onEnter{it.canonicalPath.startsWith(owner.path+File.separator)}.forEach {file->
                    currentCoroutineContext().ensureActive()
                    if(file.isFile&&file.extension.equals("yklgeodata",true)&&file.canonicalPath.startsWith(directory.path+File.separator))
                        removeDataTransport(file)
                }
            }
        }}catch(cancel:CancellationException){throw cancel}
        catch(failure:Exception){android.util.Log.w("YokuliAtlas","Installed data transport cleanup will be retried",failure)}
    }

    private fun removeDataTransport(file:File) {
        // 清理失败保留安装成功的资料，下次连接再试；不能回滚已交付数据或删除用户选择的原 .yklpkg。
        try {
            if(file.isFile&&!file.delete())android.util.Log.w("YokuliAtlas","Installed data transport could not be removed: ${file.name}")
        }catch(failure:SecurityException){android.util.Log.w("YokuliAtlas","Installed data transport cleanup will be retried",failure)}
    }

    /** 只导出真实子集合；一个来源也可独立制包，空包留在本机等待用户加入内容。 */
    fun exportBundle(bundleId:String,targetUri:Uri) {
        if(busy)return
        issue=null
        worker=launchOperation {
            try {
                restore();check(readable){"ATLAS_CATALOG_UNREADABLE"}
                require(targetUri.scheme=="content"){"ATLAS_EXPORT_DOCUMENT_REQUIRED"}
                val name=withContext(Dispatchers.IO) {app.contentResolver.query(targetUri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null}}
                require(name?.endsWith(".yklpkg",true)==true){"ATLAS_EXTENSION_REQUIRED"}
                val record=installed.firstOrNull {it.value.id==bundleId}?:error("ATLAS_MISSING")
                require(record.value.chartFolderId!=null||record.value.datasetId!=null){"ATLAS_EMPTY"}
                pendingExport=ExportPending(UUID.randomUUID().toString(),bundleId,targetUri.toString(),exportFingerprint(record.value));persist()
                runExport(service?:error("ATLAS_CORE_UNAVAILABLE"),requireNotNull(pendingExport))
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){issue=error.message?:"ATLAS_EXPORT_FAILED"}
        }
    }

    private suspend fun runExport(charts:ChartDataService,request:ExportPending) {
        val directory=File(app.cacheDir,"chart-bundle-export/${request.requestId}")
        var touched=false
        try {
            charts.state.first {!it.loading}
            val bundle=installed.firstOrNull {it.value.id==request.bundleId}?.value?:error("ATLAS_MISSING")
            require(bundle.chartFolderId!=null||bundle.datasetId!=null){"ATLAS_EMPTY"}
            require(exportFingerprint(bundle)==request.sourceFingerprint){"ATLAS_EXPORT_SOURCE_CHANGED"}
            task=ChartBundleTask(request.requestId,bundle.name,ChartBundlePhase.EXPORTING)
            withContext(Dispatchers.IO) {check(directory.isDirectory||directory.mkdirs()){"ATLAS_STORAGE_FAILED"}}
            val display=bundle.chartFolderId?.let {id->
                val file=File(directory,"charts.yklcharts")
                // 每次重新构造显示子包，原文件列表由 ChartLibrary 冻结并校验。
                library.exportBundleCharts(id,FileProvider.getUriForFile(app,"${app.packageName}.chartbundle-files",file))
                file
            }
            val geodata=bundle.datasetId?.let {id->
                val file=File(directory,"data.yklgeodata")
                val uri=FileProvider.getUriForFile(app,"${app.packageName}.chartbundle-files",file)
                val export=ChartExportRequest("atlas-export-${request.requestId}",id,uri.toString())
                charts.state.first {!it.loading}
                var result=charts.exportPackage(export)
                while(result is ChartCommandResult.Busy){delay(400);result=charts.exportPackage(export)}
                when(result) {
                    is ChartCommandResult.Failed->error(result.reason)
                    is ChartCommandResult.Accepted->{
                        val state=charts.state.onEach {state->state.exportJob?.takeIf {it.requestId==export.requestId}?.let {job->
                            task=ChartBundleTask(request.requestId,bundle.name,ChartBundlePhase.EXPORTING,job.detail)
                        }}.first {it.exportJob?.let {job->job.requestId==export.requestId&&job.phase in setOf(ChartExportPhase.COMPLETE,ChartExportPhase.CANCELLED,ChartExportPhase.FAILED,ChartExportPhase.INTERRUPTED)}==true}
                        val job=requireNotNull(state.exportJob);check(job.phase==ChartExportPhase.COMPLETE){job.detail.ifBlank {"ATLAS_EXPORT_FAILED"}}
                    }
                    is ChartCommandResult.Saved->Unit
                    ChartCommandResult.Busy->error("ATLAS_CORE_BUSY")
                }
                require(file.isFile&&file.length()>0){"ATLAS_EXPORT_SOURCE_MISSING"};file
            }
            val active=currentCoroutineContext()
            withContext(Dispatchers.IO) {
                val combined=File(directory,"atlas.yklpkg")
                combined.outputStream().use {output->YokuliAtlasPackage.write(output,bundle.id,bundle.name,
                    charts=display?.let {file->ChartPackageSource("files/charts.yklcharts","charts",0){file.inputStream()}},
                    geodata=geodata?.let {file->ChartPackageSource("files/data.yklgeodata","geodata",1){file.inputStream()}},
                    metadata=bundle.metadata,check={active.ensureActive();check(directory.usableSpace>96_000_000L){"ATLAS_STORAGE_FULL"}})}
                java.io.FileOutputStream(combined,true).use {it.fd.sync()}
                active.ensureActive()
                app.contentResolver.openFileDescriptor(Uri.parse(request.targetUri),"rwt")?.use {descriptor->
                    touched=true
                    val canSync=descriptor.statSize>=0
                    coroutineScope {
                        val closer=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED) {
                            try {awaitCancellation()}finally {runCatching {descriptor.close()}}
                        }
                        try {android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use {output->combined.inputStream().use {input->
                            val buffer=ByteArray(64*1024)
                            while(true){active.ensureActive();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n)}
                            output.flush();if(canSync)output.fd.sync()
                        }}}finally {withContext(NonCancellable){closer.cancelAndJoin()}}
                    }
                }?:error("ATLAS_EXPORT_DOCUMENT_REQUIRED")
            }
            withContext(NonCancellable) {pendingExport=null;persist()}
            task=ChartBundleTask(request.requestId,bundle.name,ChartBundlePhase.COMPLETE,cancellable=false)
            withContext(Dispatchers.IO) {directory.deleteRecursively()}
        }catch(cancel:CancellationException){throw cancel}
        catch(error:Exception) {
            issue=error.message?:"ATLAS_EXPORT_FAILED"
            if(touched)withContext(Dispatchers.IO) {
                if(!runCatching {DocumentsContract.deleteDocument(app.contentResolver,Uri.parse(request.targetUri))}.getOrDefault(false))issue="ATLAS_OUTPUT_PARTIAL"
            }
            // 已清理的文档 URI 不可盲目重用；重新导出由用户选择新的保存目标。
            withContext(NonCancellable) {pendingExport=null;persist()}
            task=task?.copy(phase=ChartBundlePhase.FAILED,detail=requireNotNull(issue),cancellable=false,retryable=false)
        }
    }

    private suspend fun removeData(charts:ChartDataService,id:String) {
        if(charts.state.value.datasets.none {it.id==id})return
        when(val result=charts.remove(id)) {
            is ChartCommandResult.Saved->Unit
            is ChartCommandResult.Failed->error(result.reason)
            ChartCommandResult.Busy->error("ATLAS_CORE_BUSY")
            is ChartCommandResult.Accepted->error("ATLAS_REMOVE_NOT_CONFIRMED")
        }
    }

    private suspend fun phase(value:ChartBundlePhase,cancellable:Boolean=true) {
        val p=requireNotNull(pending).copy(phase=value);pending=p;persist()
        task=ChartBundleTask(p.requestId,p.name,value,cancellable=cancellable)
    }

    private fun publish(state:ChartDataState?=service?.state?.value) {
        bundles=installed.map {record->
            val folder=library.folders.firstOrNull {it.id==record.value.chartFolderId}
            val data=state?.datasets?.firstOrNull {it.id==record.value.datasetId}
            val unavailable=when {
                record.value.chartFolderId!=null&&folder==null->"ATLAS_CHARTS_MISSING"
                folder!=null&&library.folderFiles(folder).none {it.enabled&&it.error==null}->"ATLAS_CHARTS_UNAVAILABLE"
                record.value.datasetId!=null&&(state==null||state.loading)->"ATLAS_CORE_RESTORING"
                record.value.datasetId==LINZ_ONLINE_DATASET_ID&&data==null->"ATLAS_DATA_PENDING"
                record.value.datasetId!=null&&data==null->"ATLAS_DATA_MISSING"
                data!=null&&(!data.offlineReadable||data.issue!=null)->"ATLAS_DATA_UNAVAILABLE"
                else->null
            }
            record.value.copy(issue=unavailable)
        }
    }

    private suspend fun persist(nextInstalled:List<Installed> = installed,nextRetired:List<Installed> = retired,nextPending:Pending?=pending) {
        check(readable){"ATLAS_CATALOG_UNREADABLE"}
        fun json(record:Installed)=JSONObject().put("id",record.value.id).put("name",record.value.name)
            .put("chartFolderId",record.value.chartFolderId).put("datasetId",record.value.datasetId)
            .put("sourceUri",record.value.sourceUri).put("metadata",JSONObject(record.value.metadata)).put("generation",record.generation)
            .put("ownedCharts",JSONArray(record.ownedCharts)).put("ownedData",record.ownedData).put("directories",JSONArray(record.directories))
        val snapshot=JSONObject().put("version",1).put("migrated",migrated).put("migratedSelection",migratedSelection).put("bundles",JSONArray(nextInstalled.map(::json))).put("retired",JSONArray(nextRetired.map(::json)))
        nextPending?.let {p->snapshot.put("pending",JSONObject().put("requestId",p.requestId).put("sourceUri",p.sourceUri).put("name",p.name)
            .put("generation",p.generation).put("phase",p.phase.name).put("bundleId",p.bundleId).put("chartsPath",p.chartsPath).put("dataPath",p.dataPath)
            .put("chartFolderId",p.chartFolderId).put("datasetId",p.datasetId).put("metadata",JSONObject(p.metadata)).put("cancelled",p.cancelled)
            .put("operation",p.operation).put("sourceIsFolder",p.sourceIsFolder).put("dataOwned",p.dataOwned).put("protectedDatasetIds",JSONArray(p.protectedDatasetIds)).put("failureDetail",p.failureDetail))}
        pendingExport?.let {request->snapshot.put("export",JSONObject().put("requestId",request.requestId).put("bundleId",request.bundleId).put("targetUri",request.targetUri).put("sourceFingerprint",request.sourceFingerprint))}
        val encoded=snapshot.toString().toByteArray()
        withContext(NonCancellable+Dispatchers.IO) {persistenceMutex.withLock {
            val stream=index.startWrite()
            try {stream.write(encoded);index.finishWrite(stream)}
            catch(error:Exception){index.failWrite(stream);throw error}
        }}
    }

    private fun exportFingerprint(value:ChartBundle):String {
        val chart=library.folders.firstOrNull {it.id==value.chartFolderId}
        val members=chart?.let {library.allFolderFiles(it)}.orEmpty()
        val source=listOf(value.id,value.name,value.chartFolderId.orEmpty(),value.datasetId.orEmpty(),
            JSONArray(value.metadata.toSortedMap().map {JSONArray(listOf(it.key,it.value))}).toString(),
            JSONArray(members.map {it.json()}).toString(),
            service?.state?.value?.datasets?.firstOrNull {it.id==value.datasetId}?.revision?.toString().orEmpty()).joinToString("\u0000")
        return java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") {"%02x".format(it.toInt() and 255)}
    }
    private fun generationDirectory(generation:String):File {checkGeneration(generation);return File(root,generation)}
    private fun childFile(p:Pending,path:String):File {
        val directory=generationDirectory(p.generation).canonicalFile
        val child=File(directory,path).canonicalFile
        require(child.path.startsWith(directory.path+File.separator)){"ATLAS_PATH_INVALID"}
        return child
    }
    private fun checkGeneration(value:String){require(value.matches(Regex("[0-9a-f-]{36}"))&&UUID.fromString(value).toString()==value){"ATLAS_CATALOG_INVALID"}}
    private fun ownerIdentity(generation:String)="atlas-$generation"
    private fun dataRequestId(p:Pending)="atlas-data-${p.requestId}"
    private fun JSONObject.strings(key:String):List<String> = optJSONArray(key)?.let {v->(0 until v.length()).map {v.getString(it)}}.orEmpty()
    private fun JSONObject.optional(key:String)=optString(key).takeIf {it.isNotBlank()&&it!="null"}
    private fun metadata(j:JSONObject):Map<String,String> = j.optJSONObject("metadata")?.let {v->v.keys().asSequence().associateWith {v.getString(it)}}.orEmpty()
    private val terminalPhases=setOf(ChartImportPhase.COMPLETE,ChartImportPhase.CANCELLED,ChartImportPhase.FAILED,ChartImportPhase.INTERRUPTED)
}

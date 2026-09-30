package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.hardware.VirtualHostServices
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.chartpackage.ChartPackageManifest
import com.yokuli.chartpackage.ChartPackageSource
import com.yokuli.chartpackage.YokuliChartPackage
import com.yokuli.runtime.contract.chart.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.util.UUID
import java.util.PriorityQueue
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/** 唯一图集所有者；Android SAF/SQLite 停在此层，普通 APK 与 ROM 使用同一实现。 */
@Singleton class LocalChartDataService @Inject constructor(@ApplicationContext private val context:Context):ChartDataService {
    private data class Stored(val dataset:ChartDataset,val directory:String)
    private data class Receipt(val requestId:String,val datasetId:String,val revision:Long)
    private data class Catalogue(val revision:Long=0,val datasets:List<Stored> = emptyList(),val receipts:List<Receipt> = emptyList())
    private data class Pending(val request:ChartImportRequest,val status:ChartImportJob)
    private data class PendingExport(val request:ChartExportRequest,val status:ChartExportJob)
    private data class SourcePayload(val path:String,val format:String,val cellIds:List<String>,val metadata:Map<String,String>,val priority:Int,val rasterProduct:String?=null,val linked:ChartSourceLink?=null,val storage:String?=null)
    private data class SourceInventory(val files:List<SourcePayload>,val complete:Boolean=true,val packageManifest:ChartPackageManifest?=null)
    private data class CopiedPackage(val files:List<File>,val manifest:ChartPackageManifest?=null,val pendingCopies:Map<String,Uri> = emptyMap())
    private val root=File(context.noBackupFilesDir,"chart-datasets")
    private val manifest=AtomicFile(File(root,"catalogue.json"))
    private val jobFile=AtomicFile(File(root,"import.json"))
    private val exportJobFile=AtomicFile(File(root,"export.json"))
    private val gson=Gson()
    private val linzKeys=LinzKeyStore(context)
    private var linzConfigured=false
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(ChartDataState())
    override val state=mutable.asStateFlow()
    private var catalogue=Catalogue()
    private var storageFault:String?=null
    private val sourceIssues=mutableMapOf<String,String>()
    private val sourceChecks=mutableMapOf<String,Long>()
    private val sourceCheckMutex=Mutex()
    private val sourceLinks=java.util.concurrent.ConcurrentHashMap<String,List<ChartSourceLink>>()
    /** 准星在同一港区连续移动时会反复命中同一批大几何；只缓存已解析对象，不复制资料所有权。 */
    private data class PositionFeatureKey(val directory:String,val rowId:Long)
    private data class PositionFeatureValue(val feature:NauticalFeature,val bytes:Int)
    private val positionFeatureLock=Any()
    private val positionFeatures=LinkedHashMap<PositionFeatureKey,PositionFeatureValue>(128,.75f,true)
    private var positionFeatureBytes=0L
    private val indexUpgradeLock=Any()
    private var indexWarmup:Job?=null
    private val cleanupScheduled=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var importJob:Job?=null
    private var pending:Pending?=null
    private var exportWorker:Job?=null
    private var pendingExport:PendingExport?=null
    private val leases=mutableMapOf<String,List<Stored>>()
    /** 显示租约的只读窗口；来源和版本仍由 leases 单独拥有，不建立第二份资料状态。 */
    private val displayWindows=mutableMapOf<String,ChartBounds>()
    private val dictionaries by lazy {S57Dictionaries(context.assets.open("chartdata/s57objectclasses.csv").bufferedReader().use {it.readText()},context.assets.open("chartdata/s57attributes.csv").bufferedReader().use {it.readText()})}
    init {scope.launch {restore();scheduleIndexWarmup()}}

    private fun scheduleIndexWarmup() {
        indexWarmup?.cancel()
        indexWarmup=scope.launch {
            // Keep startup and the first map frame free. Old derived indexes are upgraded in the
            // background shortly afterward; openIndex() still performs the same guarded migration
            // if the user reaches a dataset before this warm-up does.
            delay(5_000)
            val files=mutex.withLock {catalogue.datasets.map {File(File(root,it.directory),"features.sqlite")}}
            for(file in files) {
                currentCoroutineContext().ensureActive()
                runCatching {
                    synchronized(indexUpgradeLock) {
                        if(file.isFile) {
                            val version=SQLiteDatabase.openDatabase(file.path,null,
                                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use{it.version}
                            if(version!=6)upgradeFeatureIndex(file)
                        }
                    }
                }
                delay(50)
            }
        }
    }

    override suspend fun retryRestore()=withContext(Dispatchers.IO) {restore();scheduleIndexWarmup()}
    private suspend fun restore()=mutex.withLock {
        if(importJob?.isCompleted==false||exportWorker?.isActive==true)return@withLock
        try {
            VirtualHostServices.beforeRead()
            linzConfigured=runCatching{linzKeys.read().isNotBlank()}.getOrDefault(false)
            catalogue=if(hasAtomicFile(manifest))manifest.openRead().use {input->require(input.channel.size()<=MAX_CATALOGUE_BYTES){"CHART_CATALOGUE_SIZE_LIMIT"};input.bufferedReader().use {gson.fromJson(it,Catalogue::class.java)}} else Catalogue()
            require(catalogue.datasets.size<=2_000&&catalogue.datasets.all {it.directory.matches(Regex("version-[0-9a-f-]{36}"))}) {"CHART_CATALOGUE_INVALID"}
            // Derived indexes are upgraded lazily on first use; restoring the app must not scan a
            // nationwide payload before the first map frame.
            catalogue=catalogue.copy(datasets=catalogue.datasets.map {it.copy(dataset=it.dataset.copy(eligibility=it.dataset.eligibility.copy(automatic=true)))})
            pendingExport=if(hasAtomicFile(exportJobFile))exportJobFile.openRead().bufferedReader().use {gson.fromJson(it,PendingExport::class.java)}else null
            pendingExport?.takeIf {it.status.phase in exportWorkingPhases}?.let {oldExport->
                val output=if(oldExport.request.chart!=null)oldExport.status.targetUri else oldExport.request.targetUri
                val removed=if(oldExport.status.phase!=ChartExportPhase.COPYING)true
                    else if(output!=null)cleanupExportTarget(Uri.parse(output))
                    else if(oldExport.request.chart!=null)cleanupGeneratedChart(oldExport) else true
                pendingExport=oldExport.copy(status=oldExport.status.copy(phase=ChartExportPhase.INTERRUPTED,detail=if(removed)"Export interrupted; start again to create the complete package"else "Export interrupted; the destination may contain an incomplete file. Start again."))
                saveExportJob()
            }
            pending=if(hasAtomicFile(jobFile))jobFile.openRead().bufferedReader().use {gson.fromJson(it,Pending::class.java)}else null
            // Gson 读取旧任务时不会填充新增字符串的 Kotlin 默认值；先迁移再调用 copy。
            pending=pending?.let {it.copy(status=it.status.copy(fileName=it.status.fileName.orEmpty()))}
            val old=pending
            if(old!=null) {
                val receipt=catalogue.receipts.firstOrNull {it.requestId==old.request.requestId}
                if(receipt!=null)pending=old.copy(status=old.status.copy(phase=ChartImportPhase.COMPLETE,datasetId=receipt.datasetId,detail=""))
                else if(old.status.phase in workingPhases)pending=old.copy(status=old.status.copy(phase=ChartImportPhase.INTERRUPTED,detail="Import was interrupted. The previous complete version is preserved."))
                saveJob()
            }
            if(pending?.status?.phase in setOf(ChartImportPhase.INTERRUPTED,ChartImportPhase.CANCELLED,ChartImportPhase.FAILED))finishPreparation(pending?.status?.detail?.takeIf {it.isNotBlank()} ?: "CHART_PREPARATION_INTERRUPTED")
            discardEmptyImports()
            // 恢复屏障只恢复本地清单，不持目录锁等待外部 SAF；首次实际快照再核对来源。
            sourceChecks.clear();sourceIssues.clear();sourceLinks.clear()
            synchronized(positionFeatureLock){positionFeatures.clear();positionFeatureBytes=0L}
            storageFault=null;publish()
            cleanup()
        }catch(error:Exception) {mutable.value=mutable.value.copy(loading=false,error=error.message ?: "CHART_CATALOGUE_UNREADABLE")}
    }
    /** 清理上一版在导入前写入的空占位；已有对象/原件的部分资料仍保留供用户重扫。 */
    private fun discardEmptyImports() {
        val empty=catalogue.datasets.filter {stored->
            stored.dataset.format=="PENDING"&&stored.dataset.cells.isEmpty()&&
                !File(File(root,stored.directory),"sources.json").exists()&&
                !File(File(root,stored.directory),"features.sqlite").exists()
        }.map {it.dataset.id}.toSet()
        if(empty.isEmpty())return
        val next=catalogue.copy(revision=catalogue.revision+1,datasets=catalogue.datasets.filterNot {it.dataset.id in empty})
        writeAtomic(manifest,gson.toJson(next));catalogue=next
        pending?.takeIf {it.request.replaceDatasetId in empty}?.let {
            pending=it.copy(request=it.request.copy(replaceDatasetId=null));saveJob()
        }
    }
    /** 使用提供方文档身份去重，树 URI 与其根文档 URI 表达的是同一个来源。 */
    private fun sourceKey(value:String):String=runCatching {
        val uri=Uri.parse(value)
        when {
            uri.scheme=="file"->"file:"+File(requireNotNull(uri.path)).canonicalPath
            DocumentsContract.isTreeUri(uri)->"content:${uri.authority}:"+DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.isDocumentUri(context,uri)->"content:${uri.authority}:"+DocumentsContract.getDocumentId(uri)
            else->uri.normalizeScheme().toString()
        }
    }.getOrDefault(value)
    private fun sourceName(uri:Uri):String=if(uri.scheme=="file")File(requireNotNull(uri.path)).name else
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
            if(it.moveToFirst()&&!it.isNull(0))it.getString(0)else null
        } ?: error("CHART_DATA_FORMAT_UNSUPPORTED")
    private fun isRawDataName(name:String):Boolean {
        val base=name.substringAfterLast('/')
        return base.substringAfterLast('.',"").lowercase(java.util.Locale.ROOT) in setOf("gpkg","tif","tiff","asc","ascii")||
            base.matches(Regex("[A-Za-z0-9_]+\\.[0-9]{3}"))&&!base.equals("CATALOG.031",true)
    }
    private fun requireDataName(name:String) {
        val extension=name.substringAfterLast('.',"").lowercase(java.util.Locale.ROOT)
        require(extension !in setOf("yklchart","yklcharts","mbtiles")){"CHART_DATA_EXTENSION_REQUIRED"}
        if(extension in setOf("nc","nc4","h5","hdf5"))error("GEBCO_USE_DATA_GEOTIFF_OR_ESRI_ASCII_NOT_NETCDF")
        if(name.endsWith("PERMIT.TXT",true)||extension=="pmt")error("S63_REQUIRES_LICENSED_CLIENT_AND_DEVICE_USER_PERMIT")
        require(extension in setOf("yklgeodata","zip")||isRawDataName(name)){"CHART_DATA_FORMAT_UNSUPPORTED"}
    }
    /** UI 不是信任边界；直接 IPC、重试及升级遗留请求也先检查真实文件名和清单类别。 */
    private suspend fun validateImportSource(uri:Uri) {
        if(uri.scheme=="linz"||DocumentsContract.isTreeUri(uri))return
        val name=sourceName(uri);requireDataName(name)
        if(name.endsWith(".yklgeodata",true)) {
            val active=currentCoroutineContext()
            val input=if(uri.scheme=="file")File(requireNotNull(uri.path)).inputStream()else
                context.contentResolver.openInputStream(uri) ?: error("CHART_READ_FAILED")
            input.use {YokuliChartPackage.readManifest(it,"data"){active.ensureActive()}}
        }
    }
    private fun publish() {
        mutable.value=ChartDataState(catalogue.revision,catalogue.datasets.map {stored->
            val directory=File(root,stored.directory)
            val readable=sourceIssues[stored.directory]==null&&stored.dataset.cells.isNotEmpty()&&File(directory,"features.sqlite").isFile&&(stored.dataset.rasters.isNullOrEmpty()||runCatching{RasterBathymetryStore.open(directory,context).use{it.grids==stored.dataset.rasters}}.getOrDefault(false))
            projectDataset(stored.dataset,includeCoverage=false).copy(offlineReadable=readable,issue=if(readable)null else sourceIssues[stored.directory] ?: stored.dataset.preparationIssue ?: if(stored.dataset.preparing)"CHART_PREPARING"else "INSTALLED_INDEX_MISSING")
        },pending?.status,false,storageFault,LinzOnlineStatus(linzConfigured,catalogue.datasets.firstOrNull{it.dataset.id==LINZ_ONLINE_DATASET_ID}?.dataset?.installedAtUtc,catalogue.datasets.firstOrNull{it.dataset.id==LINZ_ONLINE_DATASET_ID}?.dataset?.downloadBounds),pendingExport?.status)
    }
    override suspend fun importPackage(request:ChartImportRequest):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        if(mutable.value.loading||mutable.value.error!=null)return@withLock ChartCommandResult.Failed("Chart catalogue is not readable")
        if(request.requestId.isBlank()||request.requestId.length>128||request.name.isBlank()||request.name.length>120)return@withLock ChartCommandResult.Failed("A request ID and chart name are required")
        catalogue.receipts.firstOrNull {it.requestId==request.requestId}?.let {return@withLock ChartCommandResult.Saved(it.datasetId,it.revision)}
        if(importJob?.isCompleted==false)return@withLock if(pending?.request?.requestId==request.requestId)ChartCommandResult.Accepted(request.requestId)else ChartCommandResult.Busy
        if(request.replaceDatasetId!=null&&request.replaceDatasetId!=LINZ_ONLINE_DATASET_ID&&catalogue.datasets.none {it.dataset.id==request.replaceDatasetId})return@withLock ChartCommandResult.Failed("The dataset to update no longer exists")
        val uri=runCatching {Uri.parse(request.sourceUri)}.getOrNull() ?: return@withLock ChartCommandResult.Failed("Source is not readable")
        if(uri.scheme=="linz"&&(uri.toString()!="linz://regional"||request.remoteBounds==null||request.replaceDatasetId!=LINZ_ONLINE_DATASET_ID))return@withLock ChartCommandResult.Failed("LINZ_REQUEST_INVALID")
        if(uri.scheme !in setOf("content","file","linz"))return@withLock ChartCommandResult.Failed("Use an Android document or folder")
        try { validateImportSource(uri) }
        catch(cancel:CancellationException){throw cancel}
        catch(error:Exception){return@withLock ChartCommandResult.Failed(error.message ?: "CHART_READ_FAILED")}
        // 任务身份不等于资料身份：同一 SAF 来源重新选择时更新原集合，不因新 requestId 增加副本。
        val matching=catalogue.datasets.filter {it.dataset.sourceUri?.let(::sourceKey)==sourceKey(request.sourceUri)}
            .maxWithOrNull(compareBy<Stored>{it.dataset.preparationIssue==null&&!it.dataset.preparing}.thenBy{it.dataset.revision})
        val actual=request.copy(replaceDatasetId=request.replaceDatasetId ?: matching?.dataset?.id)
        val datasetId=actual.replaceDatasetId ?: UUID.nameUUIDFromBytes(sourceKey(actual.sourceUri).toByteArray(Charsets.UTF_8)).toString()
        val previous=pending
        pending=Pending(actual,ChartImportJob(actual.requestId,actual.name,ChartImportPhase.COPYING,datasetId=datasetId))
        try {
            if(uri.scheme=="content")runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            saveJob()
        }catch(error:Exception) {pending=previous;return@withLock ChartCommandResult.Failed(error.message ?: "Cannot save import request")}
        // 只登记后台任务；校验、索引和落盘全部成功后才将新文件夹加入资料目录。
        publish()
        importJob=scope.launch {performImport(actual,datasetId)}
        ChartCommandResult.Accepted(request.requestId)
    }}
    override suspend fun configureLinz(apiKey:String):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        try {
            VirtualHostServices.beforeWrite();linzKeys.save(apiKey.trim());linzConfigured=apiKey.isNotBlank();publish()
            ChartCommandResult.Saved(LINZ_ONLINE_DATASET_ID,catalogue.revision)
        }catch(error:Exception){ChartCommandResult.Failed(if(error.message?.startsWith("LINZ_")==true)error.message!! else "LINZ_KEY_NOT_SAVED")}
    }}
    override suspend fun refreshLinz(bounds:ChartBounds):ChartCommandResult {
        if(!linzConfigured)return ChartCommandResult.Failed("LINZ_KEY_REQUIRED")
        try {LinzOnlineDownload.validate(bounds)}catch(error:Exception){return ChartCommandResult.Failed("LINZ_AREA_TOO_LARGE")}
        return importPackage(ChartImportRequest(UUID.randomUUID().toString(),"linz://regional","LINZ",
            DataEligibility(ChartUse.REFERENCE_ONLY,"Toitū Te Whenua LINZ","LINZ Data Service — reference hydrographic GIS; not corrected for Notices to Mariners"),
            replaceDatasetId=LINZ_ONLINE_DATASET_ID,remoteBounds=bounds))
    }
    /** 规划取得冻结快照前确保整区缓存；覆盖内直接离线使用，网络失败绝不借旧区伪装新覆盖。 */
    internal suspend fun ensureLinz(bounds:ChartBounds) {
        state.first{!it.loading}
        val cached=state.value.datasets.firstOrNull{it.id==LINZ_ONLINE_DATASET_ID&&it.offlineReadable}?.downloadBounds
        if(cached!=null&&bounds.west>=cached.west&&bounds.east<=cached.east&&bounds.south>=cached.south&&bounds.north<=cached.north)return
        when(val accepted=refreshLinz(bounds)) {
            is ChartCommandResult.Accepted->{
                val finished=withTimeoutOrNull(180_000) {state.first { snapshot->
                    snapshot.activeJob?.let {it.requestId!=accepted.requestId||it.phase !in workingPhases}!=false
                }}?:error("LINZ_DOWNLOAD_TIMEOUT")
                val job=finished.activeJob
                check(job?.requestId==accepted.requestId&&job.phase==ChartImportPhase.COMPLETE){
                    if(job?.requestId==accepted.requestId)job.detail.ifBlank{"LINZ_DOWNLOAD_FAILED"}else "LINZ_DOWNLOAD_REPLACED"
                }
            }
            is ChartCommandResult.Failed->error(accepted.reason)
            ChartCommandResult.Busy->error("LINZ_IMPORT_BUSY")
            is ChartCommandResult.Saved->Unit
        }
    }
    override fun cancelImport(requestId:String) {
        scope.launch {
            val cancelled=mutex.withLock {
                if(pending?.request?.requestId==requestId&&pending?.status?.phase in workingPhases&&pending?.status?.phase!=ChartImportPhase.COMMITTING)
                    importJob?.also {it.cancel()} else null
            } ?: return@launch
            // 不持锁等待清理；也覆盖协程尚未进入 performImport 就被取消、来不及执行其 catch 的情况。
            cancelled.join()
            mutex.withLock {
                if(pending?.request?.requestId==requestId&&pending?.status?.phase in workingPhases&&cancelled.isCancelled) {
                    pending=pending?.let {it.copy(status=it.status.copy(phase=ChartImportPhase.CANCELLED,detail=""))}
                    runCatching {saveJob()};runCatching {finishPreparation("CHART_PREPARATION_CANCELLED")};publish()
                }
            }
        }
    }
    override suspend fun retryImport(requestId:String):ChartCommandResult {
        val request=mutex.withLock {pending?.takeIf {it.request.requestId==requestId}?.request}
            ?: return ChartCommandResult.Failed("The import request is no longer retained")
        return importPackage(request)
    }
    private fun finishPreparation(issue:String) {
        val datasetId=pending?.status?.datasetId ?: return
        val stored=catalogue.datasets.firstOrNull {it.dataset.id==datasetId&&it.dataset.preparing} ?: return
        val next=catalogue.copy(revision=catalogue.revision+1,datasets=catalogue.datasets.map {if(it==stored)it.copy(dataset=it.dataset.copy(revision=catalogue.revision+1,preparing=false,preparationIssue=issue))else it})
        writeAtomic(manifest,gson.toJson(next));catalogue=next
    }
    private suspend fun progress(phase:ChartImportPhase,done:Int=0,total:Int=0,detail:String="",fileIndex:Int=0,fileCount:Int=0,fileName:String="")=mutex.withLock {
        pending=pending?.let {it.copy(status=it.status.copy(phase=phase,completed=done,total=total,detail=detail,fileIndex=fileIndex,fileCount=fileCount,fileName=fileName))};publish()
    }
    private suspend fun performImport(request:ChartImportRequest,datasetId:String) {
        val stage=File(root,"stage-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val workContext=currentCoroutineContext()
            var nextStorageCheck=0L
            fun check(){
                workContext.ensureActive();VirtualHostServices.beforeWrite()
                // 解析器按属性/顶点检查取消；容量检查不随每个属性反复触发文件系统查询。
                val now=android.os.SystemClock.elapsedRealtime()
                if(now>=nextStorageCheck){require(root.usableSpace>96_000_000L) {"CHART_STORAGE_FULL"};nextStorageCheck=now+500}
            }
            val attached=mutex.withLock {catalogue.datasets.firstOrNull {it.dataset.id==datasetId}}
            val original=attached?.takeIf {it.dataset.cells.isNotEmpty()}
            val expectedDirectory=attached?.directory
            val source=File(stage,"source").apply {mkdirs()}
            val copied=if(request.sourceUri=="linz://regional") {
                val bounds=requireNotNull(request.remoteBounds){"LINZ_REGION_REQUIRED"}
                val downloaded=File(source,"linz-region.gpkg")
                LinzOnlineDownload.download(linzKeys.read(),bounds,downloaded) {done,total,detail->check();progress(ChartImportPhase.COPYING,done,total,detail)}
                CopiedPackage(listOf(downloaded))
            } else copyPackage(Uri.parse(request.sourceUri),source,::check)
            val incoming=copied.files
            var materializedBytes=0L
            val linked=mutableMapOf<String,ChartSourceLink>()
            val cached=hashSetOf<String>()
            fun cacheSource(file:File,link:ChartSourceLink) {
                LinkedChartSource.verify(context,link,::check)
                LinkedChartSource.input(context,Uri.parse(link.uri)).use {input->FileOutputStream(file).use {out->
                    val buffer=ByteArray(256*1024);var count=0L
                    while(true){check();val n=input.read(buffer);if(n<0)break;count+=n;materializedBytes+=n
                        require(count<=32_000_000_000L&&materializedBytes<=64_000_000_000L){"CHART_PACKAGE_SIZE_LIMIT"};out.write(buffer,0,n)}
                    require(count==link.size){"CHART_SOURCE_CHANGED"};out.fd.sync()
                }}
                LinkedChartSource.verify(context,link,::check,full=true)
                linked.remove(file.absolutePath);cached+=file.absolutePath
            }
            fun register(file:File):ChartSourceLink? {
                val uri=copied.pendingCopies[file.absolutePath]?:return null
                if(file.absolutePath in cached)return null
                val link=linked.getOrPut(file.absolutePath){LinkedChartSource.capture(context,uri,::check)}
                // 提供方没有任何版本/修改时间时无法廉价判断原件是否已变；只对此类来源缓存。
                // 避免每次准星/规划查询都重读整个全国资料来核对哈希。
                if(link.modified<=0){cacheSource(file,link);return null}
                return link
            }
            fun openInput(file:File):InputStream {
                val link=register(file)
                return if(link==null)file.inputStream()else LinkedChartSource.input(context,Uri.parse(link.uri))
            }
            fun sourceSize(file:File)=register(file)?.size?:file.length()
            fun openRandom(file:File):ChartSourceHandle {
                val link=register(file)?:return ChartSourceHandle(file)
                try {return LinkedChartSource.random(context,Uri.parse(link.uri),sqlite=file.extension.equals("gpkg",true))}
                catch(_:ChartRandomAccessUnavailable) {
                    // 仅文档提供方不能随机读取时缓存，资料语义/几何错误不能触发复制重试。
                    cacheSource(file,link)
                    return ChartSourceHandle(file)
                }
            }
            val rasterProducts=mutableMapOf<String,String>()
            val payloadCells=mutableMapOf<String,List<String>>()
            val suppliedMetadata=copied.manifest?.files.orEmpty().associate {File(source,it.path).absolutePath to it.metadata}
            val suppliedProducts=copied.manifest?.files.orEmpty().associate {File(source,it.path).absolutePath to it.rasterProduct}
            val filePriorities=copied.manifest?.files?.associate {File(source,it.path).absolutePath to it.priority} ?: incoming.sortedBy {it.name}.mapIndexed {index,file->file.absolutePath to index}.toMap()
            val packagePriorities=mutableMapOf<String,Int>()
            fun sourceIdentity(file:File)=if(copied.manifest==null)file.name else file.relativeTo(source).invariantSeparatorsPath
            fun sourceName(file:File)=sourceIdentity(file).removePrefix("files/").replace(Regex("^[0-9A-Fa-f-]{36}_"),"")
            require(incoming.isNotEmpty()) {"CHART_NO_SUPPORTED_DATA"}
            require(incoming.size<=YokuliChartPackage.MAX_FILES){"YKLCHART_FILE_COUNT_LIMIT"}
            val geopackages=incoming.filter {it.extension.equals("gpkg",ignoreCase=true)}
            val rasters=incoming.filter(RasterBathymetryImporter::accepts)
            val encFiles=incoming.filter {it.extension.toIntOrNull()!=null}
            val formats=listOfNotNull("GPKG".takeIf{geopackages.isNotEmpty()},"GEBCO".takeIf{rasters.isNotEmpty()},"S57".takeIf{encFiles.isNotEmpty()})
            val format=if(request.sourceUri=="linz://regional")"LINZ"else formats.singleOrNull()?:"MIXED"
            val sourceIsFolder=DocumentsContract.isTreeUri(Uri.parse(request.sourceUri))
            // 文件夹与 .yklgeodata 是完整替换；单个 S-57 增量仍继承已安装的基础单元。
            val incremental=original!=null&&!sourceIsFolder&&copied.manifest==null&&format=="S57"&&original.dataset.format=="S57"
            val reader=S57Reader(dictionaries,::check)
            val raw=File(stage,"records").apply {mkdirs()}
            if(incremental)File(File(root,requireNotNull(original).directory),"records").listFiles().orEmpty().forEach {file->check();file.copyTo(File(raw,file.name),overwrite=false)}
            val revisions=if(incremental)requireNotNull(original).dataset.cells.associateBy {it.cellId}.toMutableMap() else mutableMapOf()
            // 先读轻量 DSID，完整二进制记录只按一个图幅及当前增量持有，避免整套交换集同时占内存。
            val incomingCells=encFiles.sortedBy {it.name}.mapIndexed {index,file->
                progress(ChartImportPhase.PARSING,index,encFiles.size,file.name);check()
                val header=openInput(file).use {reader.read(it,sourceSize(file),metadataOnly=true)}.header
                require(file.extension.toIntOrNull()==header.update) {"S57_FILE_UPDATE_NUMBER_MISMATCH:${file.name}"}
                payloadCells[file.absolutePath]=listOf(header.cell)
                header to file
            }.groupBy {it.first.cell}
            for((cellId,updates) in incomingCells) {
                check();val existingFile=File(raw,"$cellId.raw.gz")
                // 同一 S-57 图幅的基础及更新归一个单元，使用它们最先声明的优先级。
                updates.mapNotNull {filePriorities[it.second.absolutePath]}.minOrNull()?.let {packagePriorities[cellId]=it}
                val old=if(existingFile.isFile)readCell(existingFile,::check)else null
                val bases=updates.filter {it.first.update==0}
                require(bases.size<=1) {"S57_DUPLICATE_BASE:$cellId"}
                var cell=bases.firstOrNull()?.let {reader.base(openInput(it.second).use {stream->reader.read(stream,sourceSize(it.second))})} ?: old ?: error("S57_BASE_MISSING:$cellId")
                require(updates.map {it.first.update}.distinct().size==updates.size) {"S57_DUPLICATE_UPDATE:$cellId"}
                updates.filter {it.first.update>0}.sortedBy {it.first.update}.forEach {(header,file)->
                    progress(ChartImportPhase.PARSING,detail=file.name)
                    if(header.update<=cell.header.update&&bases.isEmpty())error("S57_OLD_OR_DUPLICATE_UPDATE:$cellId")
                    cell=reader.apply(cell,openInput(file).use {reader.read(it,sourceSize(file))})
                }
                if(old!=null&&cell.header.edition!=0)require(cell.header.edition>old.header.edition||(cell.header.edition==old.header.edition&&cell.header.update>=old.header.update)) {"S57_REVISION_REGRESSION:$cellId"}
                writeCell(existingFile,cell,::check)
                revisions[cellId]=ChartCellRevision(cellId,cell.header.edition,cell.header.update,cell.header.usage,cell.parameters.scale.takeIf {it>0},cell.header.issue,cancelled=cell.header.edition==0,sourceName=updates.last().second.name)
            }
            require(revisions.size<=2_000) {"CHART_CELL_LIMIT"}
            val database=File(stage,"features.sqlite")
            SQLiteDatabase.openOrCreateDatabase(database,null).use {db->
                db.rawQuery("PRAGMA journal_mode=DELETE",null).use { cursor ->
                    require(cursor.moveToFirst()&&cursor.getString(0).equals("delete",true)) {"CHART_SQLITE_JOURNAL_MODE_FAILED"}
                }
                ChartFeatureIndex.create(db)
                db.beginTransaction()
                try {
                    var index=0L
                    val total=revisions.size
                    for((cellIndex,cellFile) in raw.listFiles().orEmpty().sortedBy {it.name}.withIndex()) {
                        check();val cell=readCell(cellFile,::check);var count=0
                        val coverage=mutableListOf<CoverageEvidence>();val quality=mutableSetOf<String>();val issues=mutableSetOf<String>();val bounds=mutableListOf<ChartBounds>();var allBounds=emptyList<ChartBounds>()
                        progress(ChartImportPhase.INDEXING,cellIndex,total,cell.header.cell)
                        for(feature in reader.features(cell,datasetId)) {
                            check();count++;index++;require(index<=2_000_000) {"CHART_FEATURE_LIMIT"}
                            val featureBounds=ChartFeatureIndex.insert(db,index,feature,gson);allBounds=coalesceBounds(allBounds+featureBounds)
                            if(feature.kind==NauticalFeatureKind.COVERAGE) {
                                coverage+=CoverageEvidence(feature.id,feature.cellId,feature.geometry,feature.attributes["CATCOV"]=="1",feature.source.compilationScale,feature.detailTier())
                                bounds+=featureBounds
                            }
                            if(feature.kind==NauticalFeatureKind.QUALITY)feature.attributes.filterKeys {it in setOf("CATZOC","POSACC","SOUACC","TECSOU","SURSTA","SUREND")}.forEach {(key,value)->quality+="$key=$value"}
                            issues+=feature.issues
                        }
                        if(cell.header.edition!=0&&coverage.none {it.covered})issues+="NO_EXPLICIT_ENC_COVERAGE"
                        if(cell.header.edition!=0&&quality.isEmpty())issues+="SURVEY_QUALITY_UNSPECIFIED"
                        val fallback=if(bounds.isEmpty())allBounds else bounds
                        revisions[cell.header.cell]=revisions.getValue(cell.header.cell).copy(featureCount=count,bounds=coalesceBounds(fallback),coverage=coverage,
                            quality=quality.toList(),hasUnsupportedSemantic=issues.any {it.startsWith("UNSUPPORTED")||it.startsWith("UNINTERPRETED")||it.contains("MISSING")||it.contains("NOT_CLOSED")},issues=issues.toList())
                    }
                    db.setTransactionSuccessful()
                }finally {db.endTransaction()}
                db.rawQuery("PRAGMA optimize",null).use { cursor -> while(cursor.moveToNext()) Unit }
            }
            for((position,file) in geopackages.sortedBy{filePriorities[it.absolutePath]?:Int.MAX_VALUE}.withIndex()) {
                val fileIndex=encFiles.size+position+1
                check()
                progress(ChartImportPhase.INDEXING,fileIndex=fileIndex,fileCount=incoming.size,fileName=sourceName(file))
                register(file)
                val partial=File(stage,"gpkg-$position").apply{mkdirs()}
                val cellId=if(geopackages.size==1&&original?.dataset?.cells?.any{it.cellId=="GPKG"}==true)"GPKG"
                    else "GPKG_${UUID.nameUUIDFromBytes(sourceIdentity(file).toByteArray(Charsets.UTF_8))}"
                val cells=openRandom(file).use {handle->GeoPackageChartImporter.prepare(handle.file,partial,datasetId,::check,objectClasses=dictionaries.objects,
                    cellIdOverride=cellId,displayName=sourceName(file),progress={done,total,detail->progress(ChartImportPhase.INDEXING,done,total,detail,fileIndex,incoming.size,sourceName(file))})}
                progress(ChartImportPhase.INDEXING,fileIndex=fileIndex,fileCount=incoming.size,fileName=sourceName(file))
                mergeFeatureIndex(File(partial,"features.sqlite"),database,::check)
                payloadCells[file.absolutePath]=cells.map {it.cellId}
                cells.forEach {cell->
                    require(revisions.put(cell.cellId,cell.copy(sourceName=sourceName(file),metadata=null))==null){"CHART_DUPLICATE_CELL"}
                    filePriorities[file.absolutePath]?.let {packagePriorities[cell.cellId]=it}
                }
                partial.deleteRecursively()
            }
            rasters.forEach {file->filePriorities[file.absolutePath]?.let {priority->
                packagePriorities["GEBCO_${UUID.nameUUIDFromBytes(sourceIdentity(file).toByteArray(Charsets.UTF_8))}"]=priority
            }}
            if(rasters.isNotEmpty())RasterBathymetryImporter.prepare(rasters.sortedBy{it.name},stage,datasetId,request.rasterProduct,::check,sourceIdentity=::sourceIdentity,preserveSource=true,
                openRandom=::openRandom,openInput=::openInput,sourceSize=::sourceSize,linkedSource={register(it)},
                declaredProductForSource={file->request.rasterProduct?:suppliedProducts[file.absolutePath]?:suppliedMetadata[file.absolutePath]?.get("yokuli.raster.product")?:suppliedMetadata[file.absolutePath]?.get("raster.product")}) {done,total,detail->
                progress(ChartImportPhase.INDEXING,done,total,detail)
            }.forEach {cell->
                val file=rasters.first {"GEBCO_${UUID.nameUUIDFromBytes(sourceIdentity(it).toByteArray(Charsets.UTF_8))}"==cell.cellId}
                payloadCells[file.absolutePath]=listOf(cell.cellId)
                val grid=RasterBathymetryStore.open(stage,context).use {store->store.grids.firstOrNull {it.cellId==cell.cellId}}
                if(grid!=null)rasterProducts[file.absolutePath]=grid.product
                require(revisions.put(cell.cellId,cell.copy(metadata=null))==null){"CHART_DUPLICATE_CELL"}
            }
            require(revisions.size in 1..2_000){"CHART_CELL_LIMIT"}
            // Normalize sparse declared priorities before appending files, avoiding Int overflow.
            val oldCells=original?.dataset?.cells.orEmpty()
            val oldPriorities=oldCells.sortedWith(compareBy<ChartCellRevision>{it.priority}.thenBy{it.cellId})
                .mapIndexed {index,cell->cell.cellId to index}.toMap()
            val oldExplicit=oldCells.associate {it.cellId to it.priorityExplicit}
            var nextPriority=(oldPriorities.values.maxOrNull()?:-1)+1
            // 首次导入尊重包内顺序；整包更新保留已落盘的手动顺序，新增文件按包顺序追加。
            // LDS 未给编制比例尺时仍使用原图层比例尺带从细到粗排列。
            val orderedCells=revisions.values.sortedWith(compareBy<ChartCellRevision>{packagePriorities[it.cellId]?:Int.MAX_VALUE}
                .thenBy{it.compilationScale?:LinzLdsAdapter.scaleBandSortDenominator(it.linzScaleBand)?:Int.MAX_VALUE}.thenBy{it.cellId})
                .map {cell->cell.copy(
                    priority=oldPriorities[cell.cellId]?:if(original==null&&copied.manifest!=null)packagePriorities.getValue(cell.cellId)else nextPriority++,
                    priorityExplicit=oldExplicit[cell.cellId]?:((original==null&&copied.manifest!=null))
                )}
                .sortedWith(compareBy<ChartCellRevision>{it.priority}.thenBy{it.cellId})
            val grids=if(rasters.isEmpty())emptyList()else RasterBathymetryStore.open(stage,context).use{it.grids}
            // 原生资料仅关联原件；包解压与不支持随机读取的提供方才保留本地原件。
            linked.values.forEach {LinkedChartSource.verify(context,it,::check)}
            require(incoming.sumOf {linked[it.absolutePath]?.size?:it.length()}<=64_000_000_000L){"CHART_PACKAGE_SIZE_LIMIT"}
            val incomingPayloads=incoming.mapIndexed {index,file->SourcePayload(file.relativeTo(source).invariantSeparatorsPath,
                when {file.extension.equals("gpkg",true)->"gpkg";RasterBathymetryImporter.accepts(file)->"gebco";else->"s57"},
                payloadCells.getValue(file.absolutePath),suppliedMetadata[file.absolutePath].orEmpty(),filePriorities[file.absolutePath]?:index,
                rasterProduct=rasterProducts[file.absolutePath],linked=linked[file.absolutePath],
                storage=if(file.absolutePath in linked)"linked"else if(file.absolutePath in cached)"provider-cache"else "package")}
            val oldInventory=if(incremental)readSourceInventory(requireNotNull(original))else null
            val replacedCells=incomingCells.filterValues {updates->updates.any {it.first.update==0}}.keys
            val retained=oldInventory?.files.orEmpty().filter {old->old.cellIds.none {it in replacedCells}&&incomingPayloads.none {it.path==old.path}}
            if(incremental)for(payload in retained) {
                check()
                if(payload.linked!=null)LinkedChartSource.verify(context,payload.linked,::check)
                else {val from=sourceFile(requireNotNull(original),payload.path)
                    val target=File(source,payload.path);target.parentFile?.mkdirs();copySource(from,target,::check)}
            }
            val inventory=SourceInventory(retained+incomingPayloads,!incremental||oldInventory?.complete==true,(copied.manifest?:oldInventory?.packageManifest)?.copy(files=emptyList(),metadata=emptyMap()))
            require(inventory.files.size<=YokuliChartPackage.MAX_FILES){"YKLCHART_FILE_COUNT_LIMIT"}
            val inventoryJson=gson.toJson(inventory);require(inventoryJson.toByteArray(Charsets.UTF_8).size<=16_000_000){"CHART_SOURCE_MANIFEST_SIZE_LIMIT"}
            File(stage,"sources.json").writeText(inventoryJson)
            check()
            progress(ChartImportPhase.COMMITTING,detail="Installing the complete indexed version")
            withContext(NonCancellable) {mutex.withLock {
                workContext.ensureActive()
                require(catalogue.datasets.firstOrNull {it.dataset.id==datasetId}?.directory==expectedDirectory) {"CHART_CHANGED_DURING_IMPORT"}
                val directory="version-${UUID.randomUUID()}";val target=File(root,directory)
                require(stage.renameTo(target)) {"CHART_ATOMIC_RENAME_FAILED"}
                val revision=catalogue.revision+1
                val current=catalogue.datasets.firstOrNull {it.dataset.id==datasetId}?.dataset
                val currentCells=current?.cells.orEmpty().associateBy {it.cellId}
                val finalCells=orderedCells.map {cell->
                    currentCells[cell.cellId]?.let {old->cell.copy(priority=old.priority,priorityExplicit=old.priorityExplicit)} ?: cell
                }.sortedBy {it.priority}
                val finalName=current?.name?.takeIf {it!=attached?.dataset?.name} ?: request.name.trim()
                val dataset=ChartDataset(datasetId,finalName,format=format,revision=revision,installedAtUtc=System.currentTimeMillis(),eligibility=request.eligibility.copy(automatic=true),cells=finalCells,sourceUri=request.sourceUri,sourceIsFolder=sourceIsFolder,rasters=grids,downloadBounds=request.remoteBounds,metadata=catalogue.datasets.firstOrNull {it.dataset.id==datasetId}?.dataset?.metadata ?: copied.manifest?.let {ChartSourceMetadata.folder(it)})
                val next=catalogue.copy(revision=revision,datasets=catalogue.datasets.filterNot {it.dataset.id==datasetId}+Stored(dataset,directory),receipts=(catalogue.receipts+Receipt(request.requestId,datasetId,revision)).takeLast(64))
                writeAtomic(manifest,gson.toJson(next));catalogue=next
                pending=Pending(request,ChartImportJob(request.requestId,request.name,ChartImportPhase.COMPLETE,revisions.size,revisions.size,"",datasetId))
                runCatching {saveJob()};publish();cleanup()
            }}
        }catch(cancel:CancellationException) {
            withContext(NonCancellable) {mutex.withLock {pending=pending?.copy(status=pending!!.status.copy(phase=ChartImportPhase.CANCELLED,detail="Import cancelled; the previous version is preserved"));runCatching {saveJob()};runCatching {finishPreparation("CHART_PREPARATION_CANCELLED")};publish()}}
        }catch(error:Exception) {
            val detail=if(request.sourceUri=="linz://regional")error.message?.takeIf { it.startsWith("LINZ_")||it.startsWith("GPKG_")||it.startsWith("CHART_") }?:"LINZ_DATA_INVALID"
                else error.message?:error.javaClass.simpleName
            withContext(NonCancellable) {mutex.withLock {pending=pending?.copy(status=pending!!.status.copy(phase=ChartImportPhase.FAILED,detail=detail));runCatching {saveJob()};runCatching {finishPreparation(detail)};publish()}}
        }finally {
            stage.deleteRecursively()
            // 已改名版本即使最终回执失败也保留到下次读取目录：不能删掉可能已经由原子清单引用的索引。
        }
    }
    /** 仅合并本进程生成的同版索引。保留对象与边界，只平移内部行号；不重复解析几十万个几何。 */
    private suspend fun mergeFeatureIndex(source:File,target:File,check:()->Unit) {
        SQLiteDatabase.openDatabase(target.path,null,SQLiteDatabase.OPEN_READWRITE).use {output->
            require(source.isFile){"CHART_FEATURE_INDEX_MISSING"}
            output.execSQL("ATTACH DATABASE ? AS incoming",arrayOf<Any>(source.path))
            try {
                val version=output.rawQuery("PRAGMA incoming.user_version",null).use{it.moveToFirst();it.getInt(0)}
                require(version==6){"CHART_FEATURE_INDEX_VERSION"}
                val offset=output.rawQuery("SELECT COALESCE(MAX(rowid),0) FROM features",null).use{it.moveToFirst();it.getLong(0)}
                val last=output.rawQuery("SELECT COALESCE(MAX(rowid),0) FROM incoming.features",null).use{it.moveToFirst();it.getLong(0)}
                require(offset in 0..2_000_000&&last in 0..2_000_000&&offset+last<=2_000_000){"CHART_FEATURE_LIMIT"}
                output.beginTransaction()
                try {
                    var first=1L
                    while(first<=last) {
                        check();currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                        val end=minOf(first+255,last)
                        // 数据在 SQLite 内复制，长 payload 不经过 Android CursorWindow 或 Gson。
                        output.execSQL("INSERT INTO features(rowid,feature_id,cell,kind,detail_scale,detail_tier,name,search,payload) SELECT rowid+?,feature_id,cell,kind,detail_scale,detail_tier,name,search,payload FROM incoming.features WHERE rowid BETWEEN ? AND ?",arrayOf<Any>(offset,first,end))
                        output.execSQL("INSERT INTO spatial SELECT id+?,min_x,max_x,min_y,max_y FROM incoming.spatial WHERE id BETWEEN ? AND ?",arrayOf<Any>(offset*2,first*2,end*2+1))
                        output.execSQL("INSERT INTO spatial_feature SELECT id+?,feature_row+? FROM incoming.spatial_feature WHERE feature_row BETWEEN ? AND ?",arrayOf<Any>(offset*2,offset,first,end))
                        output.execSQL("INSERT INTO spatial_bucket(spatial_id,bucket) SELECT spatial_id+?,bucket FROM incoming.spatial_bucket WHERE spatial_id BETWEEN ? AND ?",arrayOf<Any>(offset*2,first*2,end*2+1))
                        first=end+1
                    }
                    check();currentCoroutineContext().ensureActive()
                    output.setTransactionSuccessful()
                }finally{output.endTransaction()}
            }finally{output.execSQL("DETACH DATABASE incoming")}
        }
    }
    private fun readSourceInventory(stored:Stored,verifyFiles:Boolean=true):SourceInventory? {
        val file=File(File(root,stored.directory),"sources.json")
        if(!file.isFile)return null
        require(file.length()<=16_000_000){"CHART_SOURCE_MANIFEST_INVALID"}
        return file.reader().use {gson.fromJson(it,SourceInventory::class.java)}.also {inventory->
            require(inventory.files.size in 1..YokuliChartPackage.MAX_FILES&&inventory.files.map{it.path}.distinct().size==inventory.files.size){"CHART_SOURCE_MANIFEST_INVALID"}
            if(verifyFiles)inventory.files.forEach {payload->if(payload.linked!=null)LinkedChartSource.verify(context,payload.linked)else sourceFile(stored,payload.path)}
        }
    }
    private fun sourceFile(stored:Stored,path:String):File {
        require(path.isNotBlank()&&!path.startsWith('/')&&path.split('/').none{it.isBlank()||it==".."||it=="."}&&!path.contains('\\')){"CHART_SOURCE_PATH_INVALID"}
        val directory=File(File(root,stored.directory),"source").canonicalFile
        return File(directory,path).canonicalFile.also {require(it.path.startsWith(directory.path+File.separator)&&it.isFile){"CHART_EXPORT_SOURCE_MISSING:Original files are unavailable. Reimport the complete source folder before exporting."}}
    }
    private fun openOriginal(stored:Stored,payload:SourcePayload,check:()->Unit={}):InputStream {
        val link=payload.linked?:return sourceFile(stored,payload.path).inputStream()
        LinkedChartSource.verify(context,link,check)
        return LinkedChartSource.input(context,Uri.parse(link.uri))
    }
    private fun openOriginalRandom(stored:Stored,payload:SourcePayload):ChartSourceHandle {
        val link=payload.linked?:return ChartSourceHandle(sourceFile(stored,payload.path))
        LinkedChartSource.verify(context,link)
        return LinkedChartSource.random(context,Uri.parse(link.uri),sqlite=payload.format=="gpkg")
    }

    private fun copySource(from:File,to:File,check:()->Unit) {
        from.inputStream().buffered().use {input->to.outputStream().buffered().use {out->
            val buffer=ByteArray(64*1024)
            while(true){check();val count=input.read(buffer);if(count<0)break;out.write(buffer,0,count)}
        }}
    }
    override suspend fun exportPackage(request:ChartExportRequest):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        if(mutable.value.loading||mutable.value.error!=null)return@withLock ChartCommandResult.Failed("CHART_CATALOGUE_UNREADABLE")
        if(request.requestId.isBlank()||request.requestId.length>128)return@withLock ChartCommandResult.Failed("CHART_EXPORT_REQUEST_INVALID")
        pendingExport?.takeIf {it.request.requestId==request.requestId}?.let {old->
            if(old.request!=request)return@withLock ChartCommandResult.Failed("CHART_EXPORT_REQUEST_CONFLICT")
            if(old.status.phase==ChartExportPhase.COMPLETE||exportWorker?.isActive==true)return@withLock ChartCommandResult.Accepted(request.requestId)
        }
        if(exportWorker?.isActive==true)return@withLock ChartCommandResult.Busy
        val uri=runCatching{Uri.parse(request.targetUri)}.getOrNull()
        if(uri?.scheme!="content")return@withLock ChartCommandResult.Failed("CHART_EXPORT_DOCUMENT_REQUIRED")
        if(request.chart==null&&DocumentsContract.isTreeUri(uri)||request.chart!=null&&!DocumentsContract.isTreeUri(uri))return@withLock ChartCommandResult.Failed("CHART_EXPORT_DOCUMENT_REQUIRED")
        request.chart?.let {try {ChartRasterGenerator.validate(it)}catch(error:Exception){return@withLock ChartCommandResult.Failed(error.message?:"CHART_RENDER_REQUEST_INVALID")}}
        val stored=catalogue.datasets.firstOrNull {it.dataset.id==request.datasetId}?:return@withLock ChartCommandResult.Failed("Dataset no longer exists")
        if(stored.dataset.preparing)return@withLock ChartCommandResult.Failed("CHART_PREPARING")
        if(stored.dataset.preparationIssue!=null)return@withLock ChartCommandResult.Failed("CHART_PREPARATION_INCOMPLETE")
        val lease="export-${UUID.randomUUID()}";leases[lease]=listOf(stored)
        pendingExport=PendingExport(request,ChartExportJob(request.requestId,request.datasetId,stored.dataset.name,ChartExportPhase.PREPARING,targetUri=request.targetUri.takeIf {request.chart==null},chart=request.chart!=null,outputFolderUri=request.targetUri.takeIf {request.chart!=null},collectionId=request.collectionId))
        try {saveExportJob()}catch(error:Exception){leases.remove(lease);return@withLock ChartCommandResult.Failed(error.message?:"CHART_EXPORT_NOT_SAVED")}
        runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION or if(request.chart!=null)Intent.FLAG_GRANT_READ_URI_PERMISSION else 0)}
        publish();exportWorker=scope.launch {performExport(request,stored,lease)}
        ChartCommandResult.Accepted(request.requestId)
    }}
    override fun cancelExport(requestId:String) {
        scope.launch {mutex.withLock {if(pendingExport?.request?.requestId==requestId)exportWorker?.cancel()}}
    }
    private fun saveExportJob(){pendingExport?.let {writeAtomic(exportJobFile,gson.toJson(it))}}
    private suspend fun exportProgress(phase:ChartExportPhase,completed:Long=0,total:Long=0,detail:String="",persist:Boolean=false,targetUri:String?=null)=mutex.withLock {
        pendingExport=pendingExport?.let {it.copy(status=it.status.copy(phase=phase,completed=completed,total=total,detail=detail,targetUri=targetUri?:it.status.targetUri))}
        if(persist)saveExportJob()
        // Progress does not rescan every installed raster catalogue.
        mutable.value=mutable.value.copy(exportJob=pendingExport?.status)
    }
    private suspend fun performExport(request:ChartExportRequest,stored:Stored,lease:String) {
        val temporary=File(root,"export-${UUID.randomUUID()}.${if(request.chart==null)"yklgeodata"else "mbtiles"}")
        var target:Uri?=if(request.chart==null)Uri.parse(request.targetUri)else null
        var touched=false;var lastProgress=0L
        try {
            val work=currentCoroutineContext()
            fun check(){work.ensureActive();VirtualHostServices.beforeRead();require(root.usableSpace>96_000_000L){"CHART_STORAGE_FULL"}}
            val options=request.chart
            if(options!=null) {
                checkLinkedSources(listOf(stored))
                val snapshot=ChartDataSnapshot(lease,stored.dataset.revision,listOf(stored.dataset))
                val rasterStore=if(stored.dataset.rasters.isNullOrEmpty())null else RasterBathymetryStore.open(File(root,stored.directory),context)
                exportProgress(ChartExportPhase.PACKAGING,total=options.tileCount(),persist=true)
                try {ChartRasterGenerator.write(temporary,snapshot,options,this,
                    if(rasterStore==null)emptyMap()else mapOf(stored.dataset.id to rasterStore),::check) {done,total->
                    val now=System.nanoTime();if(now-lastProgress>150_000_000L||done==total){lastProgress=now;exportProgress(ChartExportPhase.PACKAGING,done,total)}
                }}finally {rasterStore?.close()}
                validateLinkedSources(stored,::check)
            }else {
            val inventory=requireNotNull(readSourceInventory(stored)){"CHART_EXPORT_SOURCE_MISSING:Original files are unavailable. Reimport the complete source folder before exporting."}
            require(inventory.complete){"CHART_EXPORT_SOURCE_MISSING:Original files are unavailable. Reimport the complete source folder before exporting."}
            val priorities=stored.dataset.cells.associate {it.cellId to it.priority}
            val sources=inventory.files.sortedWith(compareBy<SourcePayload>{it.cellIds.mapNotNull(priorities::get).minOrNull()?:Int.MAX_VALUE}.thenBy {it.priority}.thenBy {it.path})
                .mapIndexed {index,payload->
                    payload.linked?.let {LinkedChartSource.verify(context,it,::check,full=true)}
                    val rasterProduct=payload.rasterProduct?:stored.dataset.rasters.orEmpty().firstOrNull {it.cellId in payload.cellIds}?.product
                    ChartPackageSource(if(payload.path.startsWith("files/"))payload.path else "files/${payload.path}",payload.format,index,
                        YokuliChartPackage.validateMetadata(payload.metadata),rasterProduct=rasterProduct){openOriginal(stored,payload,::check)}
                }
            exportProgress(ChartExportPhase.PACKAGING,persist=true)
            withContext(Dispatchers.IO) {
                val packageSource=inventory.packageManifest
                temporary.outputStream().use {output->YokuliChartPackage.write(output,
                    id=packageSource?.id?:stored.dataset.id,name=stored.dataset.name,kind="data",files=sources,
                    metadata=stored.dataset.metadata.orEmpty(),provider=packageSource?.provider.orEmpty(),license=packageSource?.license.orEmpty(),attribution=packageSource?.attribution.orEmpty(),check=::check,
                    progress={done,total->
                        val now=System.nanoTime()
                        if(now-lastProgress>150_000_000L){lastProgress=now;runBlocking {exportProgress(ChartExportPhase.PACKAGING,done,total)}}
                    })}
            }
            check()
            inventory.files.mapNotNull {it.linked}.forEach {LinkedChartSource.verify(context,it,::check,full=true)}
            }
            FileOutputStream(temporary,true).use {it.fd.sync()}
            val total=temporary.length()
            if(options!=null) {
                val tree=Uri.parse(request.targetUri)
                val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
                // 先持久化创建意图，崩溃在 createDocument 与回执间也能凭唯一任务文件名找回清理。
                exportProgress(ChartExportPhase.COPYING,total=total,persist=true)
                target=DocumentsContract.createDocument(context.contentResolver,parent,"application/octet-stream",generatedChartName(request,stored.dataset.name))?:error("CHART_EXPORT_PERMISSION_LOST")
                touched=true
            }
            exportProgress(ChartExportPhase.COPYING,total=total,persist=true,targetUri=requireNotNull(target).toString())
            touched=true
            val descriptor=context.contentResolver.openFileDescriptor(requireNotNull(target),"rwt")?:error("CHART_EXPORT_PERMISSION_LOST")
            val canSync=descriptor.statSize>=0
            coroutineScope {
                // Closing the descriptor interrupts a provider blocked in write when cancellation arrives.
                val closer=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED) {try {awaitCancellation()}finally {runCatching {descriptor.close()}}}
                try {android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use {output->
                FileInputStream(temporary).buffered().use {input->
                    val buffer=ByteArray(64*1024);var done=0L
                    while(true){work.ensureActive();VirtualHostServices.beforeWrite();val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count);done+=count
                        val now=System.nanoTime();if(now-lastProgress>150_000_000L){lastProgress=now;exportProgress(ChartExportPhase.COPYING,done,total)}}
                    output.flush();work.ensureActive()
                    // Some document providers expose pipes rather than seekable files.
                    if(canSync)output.fd.sync()
                }
                }}finally {withContext(NonCancellable){closer.cancelAndJoin()}}
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable){exportProgress(ChartExportPhase.COMPLETE,total,total,if(options==null)"Original files exported"else "Offline chart generated",persist=true)}
        }catch(cancel:CancellationException) {
            withContext(NonCancellable){val removed=!touched||target==null||cleanupExportTarget(requireNotNull(target));exportProgress(ChartExportPhase.CANCELLED,detail=if(removed)"Export cancelled"else "Export cancelled; the destination may contain an incomplete file",persist=false);runCatching {saveExportJob()}}
        }catch(error:Exception) {
            val cancelled=!currentCoroutineContext().isActive
            withContext(NonCancellable){val removed=!touched||target==null||cleanupExportTarget(requireNotNull(target));exportProgress(if(cancelled)ChartExportPhase.CANCELLED else ChartExportPhase.FAILED,detail=(if(cancelled)"Export cancelled"else error.message?:"CHART_EXPORT_FAILED")+if(removed)""else "; the destination may contain an incomplete file",persist=false);runCatching {saveExportJob()}}
        }finally {
            withContext(NonCancellable){temporary.delete();mutex.withLock {leases.remove(lease);cleanup()}}
        }
    }
    private fun generatedChartName(request:ChartExportRequest,name:String):String =
        name.replace(Regex("[^\\p{L}\\p{N} _.-]"),"_").take(70).ifBlank {"Yokuli"}+"-${UUID.nameUUIDFromBytes(request.requestId.toByteArray(Charsets.UTF_8))}.mbtiles"
    private fun cleanupGeneratedChart(pending:PendingExport):Boolean=runCatching {
        val tree=Uri.parse(pending.request.targetUri)
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val expected=generatedChartName(pending.request,pending.status.name)
        var success=true
        context.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {cursor->
            while(cursor.moveToNext())if(cursor.getString(1)==expected) {
                success=cleanupExportTarget(DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)))&&success
            }
        }?:return@runCatching false
        success
    }.getOrDefault(false)
    private fun cleanupExportTarget(uri:Uri):Boolean=runCatching {DocumentsContract.deleteDocument(context.contentResolver,uri)}.getOrDefault(false)

    override suspend fun reorderCells(datasetId:String,cellIds:List<String>):ChartCommandResult=edit(datasetId) {data->
        require(cellIds.size==cellIds.distinct().size&&cellIds.toSet()==data.cells.map{it.cellId}.toSet()) {"CHART_CELL_ORDER_INVALID"}
        val positions=cellIds.withIndex().associate{it.value to it.index}
        data.copy(cells=data.cells.map{it.copy(priority=positions.getValue(it.cellId),priorityExplicit=true)}.sortedBy{it.priority})
    }
    override suspend fun rename(datasetId:String,name:String):ChartCommandResult {
        if(name.isBlank()||name.length>120)return ChartCommandResult.Failed("Use a name of 1–120 characters")
        return edit(datasetId){it.copy(name=name.trim())}
    }
    private fun projectDataset(dataset:ChartDataset,includeCoverage:Boolean=true)=dataset.copy(metadata=null,cells=dataset.cells.map {cell->
        cell.copy(metadata=null,coverage=if(includeCoverage)cell.coverage else emptyList(),hasStructuredCoverage=if(includeCoverage)null else cell.coverage.any {
            it.covered&&it.geometry.kind==ChartGeometryKind.POLYGON&&it.geometry.parts.any {part->!part.hole&&part.points.size>=3&&part.points.all {point->
                point.latitude.isFinite()&&point.longitude.isFinite()&&point.latitude in -90.0..90.0&&point.longitude in -180.0..180.0
            }}
        })
    })
    override suspend fun readMetadata(datasetId:String,cellId:String?,revision:Long?):Map<String,String> = withContext(Dispatchers.IO) {
        val lease="metadata-${UUID.randomUUID()}"
        val stored=mutex.withLock {
            require(!mutable.value.loading&&mutable.value.error==null){"CHART_CATALOGUE_UNREADABLE"}
            val selected=catalogue.datasets.firstOrNull {it.dataset.id==datasetId}?:error("CHART_DATASET_MISSING")
            require(revision==null||selected.dataset.revision==revision){"CHART_METADATA_REVISION_CHANGED"}
            if(cellId!=null)require(selected.dataset.cells.any {it.cellId==cellId}){"CHART_CELL_MISSING"}
            require(leases.size<32){"CHART_SNAPSHOT_LIMIT"};leases[lease]=listOf(selected);selected
        }
        try {
            currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
            if(cellId==null)return@withContext YokuliChartPackage.validateMetadata(stored.dataset.metadata.orEmpty())
            val cell=stored.dataset.cells.first {it.cellId==cellId}
            val inventory=readSourceInventory(stored,verifyFiles=false)
            val payload=inventory?.files.orEmpty().filter {cellId in it.cellIds}.maxWithOrNull(compareBy<SourcePayload>{it.path.substringAfterLast('.').toIntOrNull()?:0}.thenBy {it.priority})
            val work=currentCoroutineContext()
            fun check(){work.ensureActive();VirtualHostServices.beforeRead()}
            val automatic=linkedMapOf<String,String>()
            cell.compilationScale?.let {automatic["compilationScale"]=it.toString()};cell.issueDate?.let {automatic["issueDate"]=it}
            if(payload!=null) {
                val link=payload.linked
                link?.let {LinkedChartSource.verify(context,it,::check)}
                automatic["source.storage"]=payload.storage?:"package"
                when(payload.format) {
                    "gpkg"->openOriginalRandom(stored,payload).use {automatic.putAll(ChartSourceMetadata.geopackage(it.file,::check))}
                    "s57"->openOriginal(stored,payload,::check).use {automatic.putAll(ChartSourceMetadata.s57(S57Reader(dictionaries,::check).read(it,link?.size?:sourceFile(stored,payload.path).length(),metadataOnly=true).header))}
                    "gebco"->{
                        val logical=if(link==null)sourceFile(stored,payload.path)else File(payload.path)
                        automatic.putAll(ChartSourceMetadata.raster(logical,stored.dataset.rasters.orEmpty().firstOrNull {it.cellId==cellId}))
                        if(link!=null)automatic["source.bytes"]=link.size.toString()
                    }
                }
            }
            // 旧目录内的说明仍可读；显式包注释优先，自动内容不挤掉原注释。
            val annotations=cell.metadata.orEmpty()+payload?.metadata.orEmpty()
            ChartSourceMetadata.merge(automatic,annotations)
        }finally {withContext(NonCancellable){mutex.withLock {leases.remove(lease);cleanup()}}}
    }
    override suspend fun updateMetadata(datasetId:String,metadata:Map<String,String>):ChartCommandResult {
        val validated=try {YokuliChartPackage.validateMetadata(metadata)}catch(error:Exception){return ChartCommandResult.Failed(error.message?:"CHART_METADATA_INVALID")}
        return edit(datasetId){it.copy(metadata=validated)}
    }
    override suspend fun updateEligibility(datasetId:String,value:DataEligibility):ChartCommandResult {
        if(value.use==ChartUse.ANALYSIS_ALLOWED&&!value.allowsAnalysis(System.currentTimeMillis()))return ChartCommandResult.Failed("Provider and permitted-use evidence are required")
        return edit(datasetId){it.copy(eligibility=value.copy(automatic=true))}
    }
    private suspend fun edit(datasetId:String,block:(ChartDataset)->ChartDataset):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        if(mutable.value.loading||mutable.value.error!=null)return@withLock ChartCommandResult.Failed("CHART_CATALOGUE_UNREADABLE")
        val old=catalogue.datasets.firstOrNull {it.dataset.id==datasetId} ?: return@withLock ChartCommandResult.Failed("Dataset no longer exists")
        try {val revision=catalogue.revision+1;val next=catalogue.copy(revision=revision,datasets=catalogue.datasets.map {if(it==old)it.copy(dataset=block(it.dataset).copy(revision=revision))else it});writeAtomic(manifest,gson.toJson(next));catalogue=next;publish();ChartCommandResult.Saved(datasetId,revision)}catch(error:Exception){ChartCommandResult.Failed(error.message ?: "Could not save chart metadata")}
    }}
    override suspend fun remove(datasetId:String):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        if(mutable.value.loading||mutable.value.error!=null)return@withLock ChartCommandResult.Failed("CHART_CATALOGUE_UNREADABLE")
        if(pending?.status?.datasetId==datasetId&&importJob?.isCompleted==false)return@withLock ChartCommandResult.Busy
        try {val next=catalogue.copy(revision=catalogue.revision+1,datasets=catalogue.datasets.filterNot {it.dataset.id==datasetId});writeAtomic(manifest,gson.toJson(next));catalogue=next;publish();cleanup();ChartCommandResult.Saved(datasetId,next.revision)}catch(error:Exception){ChartCommandResult.Failed(error.message ?: "Could not remove chart")}
    }}
    override suspend fun acquireSnapshot(datasetIds:List<String>):ChartDataSnapshot=withContext(Dispatchers.IO) {
        // 先取得版本租约再检查外部来源，不持目录互斥锁访问可能缓慢的 SAF 提供方。
        val snapshot=mutex.withLock {
            VirtualHostServices.beforeRead()
            require(!mutable.value.loading&&mutable.value.error==null) {"CHART_CATALOGUE_UNREADABLE"}
            require(leases.size<32) {"CHART_SNAPSHOT_LIMIT"}
            val ids=datasetIds.distinct();require(ids.size<=1){"CHART_SELECT_ONE_FOLDER"}
            val selected=ids.mapNotNull {id->catalogue.datasets.firstOrNull {it.dataset.id==id&&it.dataset.cells.isNotEmpty()&&File(File(root,it.directory),"features.sqlite").isFile}}
            val id=UUID.randomUUID().toString();leases[id]=selected
            ChartDataSnapshot(id,catalogue.revision,selected.map {projectDataset(it.dataset)},ids.filter {wanted->selected.none {it.dataset.id==wanted}})
        }
        try {
            val selected=mutex.withLock {leases.getValue(snapshot.id).toList()}
            checkLinkedSources(selected)
            snapshot
        }catch(error:Exception){withContext(NonCancellable){mutex.withLock {leases.remove(snapshot.id);cleanup()}};throw error}
    }
    override suspend fun acquireDisplaySnapshot(datasetIds:List<String>,bounds:ChartBounds):ChartDataSnapshot {
        ChartDisplayWindow.validate(bounds)
        val snapshotId=UUID.randomUUID().toString()
        var retained=false
        try {
            return withContext(Dispatchers.IO) {
                val snapshot=mutex.withLock {
                    VirtualHostServices.beforeRead()
                    require(!mutable.value.loading&&mutable.value.error==null){"CHART_CATALOGUE_UNREADABLE"}
                    require(leases.size<32){"CHART_SNAPSHOT_LIMIT"}
                    val ids=datasetIds.distinct();require(ids.size<=1){"CHART_SELECT_ONE_FOLDER"}
                    val selected=ids.mapNotNull {id->catalogue.datasets.firstOrNull {it.dataset.id==id&&it.dataset.cells.isNotEmpty()&&File(File(root,it.directory),"features.sqlite").isFile}}
                    leases[snapshotId]=selected;displayWindows[snapshotId]=bounds;retained=true
                    ChartDataSnapshot(snapshotId,catalogue.revision,selected.map {projectDataset(it.dataset)},ids.filter {wanted->selected.none {it.dataset.id==wanted}})
                }
                checkLinkedSources(mutex.withLock {leases.getValue(snapshotId).toList()})
                val work=currentCoroutineContext()
                ChartDisplayWindow(bounds){work.ensureActive();VirtualHostServices.beforeRead()}.snapshot(snapshot)
            }
        }catch(error:Exception) {
            // catch 位于 dispatcher 返回边界之外；取消即使发生在回包前也不会遗失租约 ID。
            if(retained)releaseSnapshot(snapshotId)
            throw error
        }
    }
    override suspend fun releaseSnapshot(snapshotId:String)=withContext(NonCancellable+Dispatchers.IO) {mutex.withLock {displayWindows.remove(snapshotId);leases.remove(snapshotId);cleanup()}}
    private data class IndexedFeature(val stored:Stored,val id:String,val rowId:Long,val length:Int)

    /** 一次读取单独保留版本：调用方离开页面释放快照时，正在关闭的 SQLite 仍不能被删掉。 */
    private suspend fun <T> withSnapshotRead(snapshotId:String,block:suspend (List<Stored>,CancellationSignal)->T):T=withContext(Dispatchers.IO) {
        VirtualHostServices.beforeRead()
        val readLease=UUID.randomUUID().toString()
        val selected=mutex.withLock {(leases[snapshotId]?.toList() ?: error("CHART_SNAPSHOT_EXPIRED")).also {leases[readLease]=it}}
        try {
            checkLinkedSources(selected)
            coroutineScope {
                val signal=CancellationSignal()
                // SQLite 的排序/旧索引搜索也能被取消，不必等阻塞中的 moveToNext 返回。
                val cancellation=launch(Dispatchers.Default,start=CoroutineStart.UNDISPATCHED) {
                    try {awaitCancellation()}finally {signal.cancel()}
                }
                try {block(selected,signal)}finally {withContext(NonCancellable) {cancellation.cancelAndJoin()}}
            }
        }finally {withContext(NonCancellable) {mutex.withLock {leases.remove(readLease);cleanup()}}}
    }

    private fun sourceFailure(error:Exception)=error.message?.takeIf {it.startsWith("CHART_SOURCE_")}?:"CHART_SOURCE_UNAVAILABLE"
    private fun linkedSources(stored:Stored):List<ChartSourceLink> = sourceLinks.getOrPut(stored.directory) {
        readSourceInventory(stored,verifyFiles=false)?.files.orEmpty().mapNotNull {it.linked}
    }
    private fun validateLinkedSources(stored:Stored,check:()->Unit={}) {
        linkedSources(stored).forEach {LinkedChartSource.verify(context,it,check)}
    }
    /** 同一版本的并行图层/规划查询合并来源检查，不按每个对象打开 SAF 或重复哈希。 */
    private suspend fun checkLinkedSources(selected:List<Stored>)=sourceCheckMutex.withLock {
        val work=currentCoroutineContext()
        for(stored in selected) {
            // 压缩包/旧本地版本没有外部关联：不可变 inventory 只读一次，查询不增加源 IO。
            if(linkedSources(stored).isEmpty())continue
            val now=android.os.SystemClock.elapsedRealtime()
            val previous=mutex.withLock {sourceChecks[stored.directory] to sourceIssues[stored.directory]}
            // 原件权限/变化仍定期核对，但不能让每次准星移动都重新访问 SAF/哈希来源。
            if(previous.first?.let {now-it<60_000}==true) {
                previous.second?.let {error(it)}
                continue
            }
            var failure:String?=null
            try {validateLinkedSources(stored){work.ensureActive();VirtualHostServices.beforeRead()}}
            catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){failure=sourceFailure(error)}
            mutex.withLock {
                sourceChecks[stored.directory]=now
                val old=sourceIssues[stored.directory]
                if(failure==null)sourceIssues.remove(stored.directory)else sourceIssues[stored.directory]=failure!!
                if(old!=failure)publish()
            }
            failure?.let {error(it)}
        }
    }

    /** Upgrade the derived spatial index without touching source evidence or dataset revision. */
    private fun upgradeFeatureIndex(file:File) {
        if(!file.isFile)return
        VirtualHostServices.beforeWrite()
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {db->
            val version=db.version
            if(version==6)return
            require(version in 2..5){"CHART_FEATURE_INDEX_VERSION"}
            db.beginTransaction()
            try {
                val featureColumns=mutableSetOf<String>().also {names->
                    db.rawQuery("PRAGMA table_info(features)",null).use {rows->while(rows.moveToNext())names+=rows.getString(1)}
                }
                if("detail_scale" !in featureColumns)db.execSQL("ALTER TABLE features ADD COLUMN detail_scale INTEGER")
                // Recover both LINZ band anchors and arbitrary ENC compilation scales from the
                // persisted payload for every legacy version. Earlier v3/v4 migrations knew only
                // LINZ anchors, so S-57 rows may still have a null detail_scale.
                db.execSQL("""
                    UPDATE features SET detail_scale=CASE
                        WHEN instr(payload,'1:4k - 1:22k')>0 THEN 4000
                        WHEN instr(payload,'1:22k - 1:90k')>0 THEN 22000
                        WHEN instr(payload,'1:90k - 1:350k')>0 THEN 90000
                        WHEN instr(payload,'1:350k - 1:1,500k')>0 THEN 350000
                        WHEN instr(payload,'1:1.5mil and smaller')>0 THEN 1500000
                        WHEN instr(payload,'"compilationScale":')>0 THEN
                            NULLIF(CAST(TRIM(SUBSTR(
                                payload,
                                instr(payload,'"compilationScale":')+length('"compilationScale":'),
                                instr(substr(payload,instr(payload,'"compilationScale":')+length('"compilationScale":')),',')-1
                            )) AS INTEGER),0)
                        ELSE detail_scale END
                    WHERE detail_scale IS NULL
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS feature_detail_scale ON features(detail_scale,feature_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS feature_cell_scale_kind ON features(cell,detail_scale,kind,feature_id)")
                // v5 separates semantic LOD tier from exact source denominator.
                if("detail_tier" !in featureColumns)db.execSQL("ALTER TABLE features ADD COLUMN detail_tier INTEGER")
                db.execSQL("""
                    UPDATE features SET detail_tier=CASE
                        WHEN detail_scale IS NULL OR detail_scale<=0 THEN NULL
                        WHEN detail_scale<22000 THEN 0
                        WHEN detail_scale<90000 THEN 1
                        WHEN detail_scale<350000 THEN 2
                        WHEN detail_scale<1500000 THEN 3
                        ELSE 4 END
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS feature_detail_tier ON features(detail_tier,feature_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS feature_cell_tier_kind ON features(cell,detail_tier,kind,feature_id)")
                db.execSQL("CREATE TABLE IF NOT EXISTS spatial_bucket (spatial_id INTEGER PRIMARY KEY,bucket INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS spatial_bucket_key ON spatial_bucket(bucket,spatial_id)")
                // Hierarchical portable buckets: .25°, 1°, 4°, 16°, 64°, world. A bbox is stored
                // once at the smallest level that can contain it, eliminating the old -1 hot bucket.
                db.execSQL("""
                    INSERT OR REPLACE INTO spatial_bucket(spatial_id,bucket)
                    SELECT id,
                        CASE
                            WHEN (max_x-min_x)<=.25 AND (max_y-min_y)<=.25 THEN
                                MIN(719,MAX(0,CAST(((((min_y+max_y)/2.0)+90.0)/.25) AS INTEGER)))*1440+
                                MIN(1439,MAX(0,CAST(((((min_x+max_x)/2.0)+180.0)/.25) AS INTEGER)))
                            WHEN (max_x-min_x)<=1.0 AND (max_y-min_y)<=1.0 THEN
                                (1*16777216)+
                                MIN(179,MAX(0,CAST(((((min_y+max_y)/2.0)+90.0)/1.0) AS INTEGER)))*360+
                                MIN(359,MAX(0,CAST(((((min_x+max_x)/2.0)+180.0)/1.0) AS INTEGER)))
                            WHEN (max_x-min_x)<=4.0 AND (max_y-min_y)<=4.0 THEN
                                (2*16777216)+
                                MIN(44,MAX(0,CAST(((((min_y+max_y)/2.0)+90.0)/4.0) AS INTEGER)))*90+
                                MIN(89,MAX(0,CAST(((((min_x+max_x)/2.0)+180.0)/4.0) AS INTEGER)))
                            WHEN (max_x-min_x)<=16.0 AND (max_y-min_y)<=16.0 THEN
                                (3*16777216)+
                                MIN(11,MAX(0,CAST(((((min_y+max_y)/2.0)+90.0)/16.0) AS INTEGER)))*23+
                                MIN(22,MAX(0,CAST(((((min_x+max_x)/2.0)+180.0)/16.0) AS INTEGER)))
                            WHEN (max_x-min_x)<=64.0 AND (max_y-min_y)<=64.0 THEN
                                (4*16777216)+
                                MIN(2,MAX(0,CAST(((((min_y+max_y)/2.0)+90.0)/64.0) AS INTEGER)))*6+
                                MIN(5,MAX(0,CAST(((((min_x+max_x)/2.0)+180.0)/64.0) AS INTEGER)))
                            ELSE (5*16777216)
                        END
                    FROM spatial
                """.trimIndent())
                db.execSQL("PRAGMA user_version=6")
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
        }
    }

    private fun openIndex(stored:Stored):SQLiteDatabase {
        val file=File(File(root,stored.directory),"features.sqlite")
        require(file.isFile) {"CHART_INDEX_MISSING:${stored.dataset.id}"}
        synchronized(indexUpgradeLock) {
            val version=SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use{it.version}
            if(version!=6)upgradeFeatureIndex(file)
        }
        return SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    }

    private fun spatialUsesRtree(db:SQLiteDatabase):Boolean =
        db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='spatial'",null).use {cursor->
            cursor.moveToFirst()&&cursor.getString(0).orEmpty().contains("USING rtree",ignoreCase=true)
        }

    /** null keeps the native RTree/broad-query path; a list enables the portable equality bucket index. */
    private fun portableBuckets(db:SQLiteDatabase,bounds:ChartBounds):List<Int>? =
        if(spatialUsesRtree(db))null else ChartFeatureIndex.queryBuckets(bounds)

    /** Android CursorWindow 有单行容量上限；长几何分段读取，绝不以截断 JSON 代替对象。 */
    private suspend fun readIndexedFeature(db:SQLiteDatabase,row:IndexedFeature,signal:CancellationSignal):NauticalFeature {
        require(row.length in 1..8_000_000) {"CHART_FEATURE_PAYLOAD_INVALID"}
        val payload=StringBuilder(row.length)
        var position=1
        while(position<=row.length) {
            currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
            db.rawQuery("SELECT substr(payload,?,?) FROM features WHERE rowid=?",arrayOf(position.toString(),"128000",row.rowId.toString()),signal).use {part->
                require(part.moveToFirst()) {"CHART_FEATURE_ROW_MISSING"}
                val text=part.getString(0)
                require(!text.isNullOrEmpty()) {"CHART_FEATURE_PAYLOAD_TRUNCATED"}
                payload.append(text)
            }
            position+=128_000
        }
        currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
        val feature=requireNotNull(gson.fromJson(payload.toString(),NauticalFeature::class.java)) {"CHART_FEATURE_PAYLOAD_INVALID"}
        require(feature.id==row.id&&feature.datasetId==row.stored.dataset.id) {"CHART_FEATURE_ID_MISMATCH"}
        return feature
    }

    private suspend fun readPositionFeature(db:SQLiteDatabase,row:IndexedFeature,signal:CancellationSignal):NauticalFeature {
        val key=PositionFeatureKey(row.stored.directory,row.rowId)
        synchronized(positionFeatureLock){positionFeatures[key]}?.let{return it.feature}
        val feature=readIndexedFeature(db,row,signal)
        // 单个巨型对象不长期常驻；总热缓存约 12 MiB，足够覆盖当前港区连续准星移动。
        if(row.length<=4_000_000) synchronized(positionFeatureLock) {
            positionFeatures.remove(key)?.let{positionFeatureBytes-=it.bytes}
            positionFeatures[key]=PositionFeatureValue(feature,row.length);positionFeatureBytes+=row.length
            while(positionFeatures.size>160||positionFeatureBytes>12_000_000L) {
                val first=positionFeatures.entries.firstOrNull()?:break
                positionFeatureBytes-=first.value.bytes;positionFeatures.remove(first.key)
            }
        }
        return feature
    }

    /** 保留至多一页加一个轻量索引行，跨数据集也按实际 ID 排序，不依赖文件或目录顺序。 */
    private fun pageCandidates(limit:Int)=PriorityQueue<IndexedFeature>(minOf(limit+1,256),compareByDescending {it.id})
    private fun retainCandidate(rows:PriorityQueue<IndexedFeature>,row:IndexedFeature,limit:Int) {
        rows.add(row)
        if(rows.size>limit+1)rows.poll()
    }
    private suspend fun readPage(rows:Collection<IndexedFeature>,limit:Int,signal:CancellationSignal):ChartFeaturePage {
        val ordered=rows.sortedBy {it.id};val found=ArrayList<NauticalFeature>();var bytes=0;var more=false
        var db:SQLiteDatabase?=null;var directory:String?=null
        try {
            for(row in ordered) {
                currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                require(row.length in 1..8_000_000) {"CHART_FEATURE_PAYLOAD_INVALID"}
                if(found.size>=limit||(found.isNotEmpty()&&bytes+row.length>12_000_000)) {more=true;break}
                if(directory!=row.stored.directory) {
                    db?.close();db=null
                    db=openIndex(row.stored);directory=row.stored.directory
                }
                found+=readIndexedFeature(requireNotNull(db),row,signal);bytes+=row.length
            }
        }finally {db?.close()}
        return ChartFeaturePage(found,if(more)found.lastOrNull()?.id else null,more)
    }

    override suspend fun inspectPosition(datasetIds:List<String>,point:ChartPoint,radiusMeters:Double):ChartPositionInfo=try {withTimeout(6_500) {
        require(datasetIds.size==1&&point.latitude.isFinite()&&point.latitude in -90.0..90.0&&point.longitude.isFinite()&&point.longitude in -180.0..180.0&&radiusMeters.isFinite()&&radiusMeters in 2.0..150.0) {"CHART_POSITION_QUERY_INVALID"}
        // 一次准星读取只注册一个短租约；不再 acquire -> withSnapshotRead 再套第二层租约/来源检查。
        val readLease=UUID.randomUUID().toString()
        val stored=mutex.withLock {
            VirtualHostServices.beforeRead()
            require(!mutable.value.loading&&mutable.value.error==null) {"CHART_CATALOGUE_UNREADABLE"}
            val selected=catalogue.datasets.firstOrNull {it.dataset.id==datasetIds.single()&&it.dataset.cells.isNotEmpty()&&File(File(root,it.directory),"features.sqlite").isFile}
                ?:error("CHART_SELECTED_DATA_MISSING")
            leases[readLease]=listOf(selected)
            selected
        }
        try {
            checkLinkedSources(listOf(stored))
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val signal=CancellationSignal()
                    val cancellation=launch(Dispatchers.Default,start=CoroutineStart.UNDISPATCHED) {
                        try {awaitCancellation()}finally {signal.cancel()}
                    }
                    val directory=File(root,stored.directory)
                    val db=openIndex(stored)
                    val rasterStore=if(stored.dataset.rasters.isNullOrEmpty())null else runCatching{RasterBathymetryStore.open(directory,context)}.getOrNull()
                    try {
                        var remainingObjects=160
                        var remainingBytes=12_000_000
                        val work=currentCoroutineContext()
                        ChartPositionQuery(stored.dataset,point,radiusMeters).read(
                            readCell={cell,bounds,accept->
                                work.ensureActive();VirtualHostServices.beforeRead()
                                val modern=db.version>=2
                                val split=bounds.split()
                                val predicate=split.joinToString(" OR ") {"(s.max_x>=? AND s.min_x<=? AND s.max_y>=? AND s.min_y<=?)"}
                                val buckets=portableBuckets(db,bounds)
                                val bucketJoin=if(buckets==null)"" else " JOIN spatial_bucket sb ON sb.spatial_id=s.id"
                                val bucketClause=if(buckets==null)"" else "sb.bucket IN (${buckets.joinToString(","){ "?" }}) AND "
                                val args=mutableListOf<String>();buckets?.let{args+=it.map(Int::toString)}
                                split.forEach {args+=listOf(it.west,it.east,it.south,it.north).map(Double::toString)}
                                args+=cell.cellId;args+=(remainingObjects+1).toString()
                                val categories=if(modern)" AND f.kind!='COVERAGE'"else ""
                                // 先未知面，再水深，再设施；只读取准星附近真正需要的一小批完整 payload。
                                val order=if(modern)"CASE WHEN f.kind='OTHER' THEN 0 WHEN f.kind IN ('DEPTH_AREA','DREDGED_AREA') THEN 1 WHEN f.kind IN ('LAND','DRYING_AREA') THEN 2 WHEN f.kind IN ('SOUNDING','DEPTH_CONTOUR') THEN 3 ELSE 4 END,COALESCE(f.detail_tier,2147483647),COALESCE(f.detail_scale,2147483647),"else ""
                                var truncated=false
                                db.rawQuery("SELECT DISTINCT f.feature_id,f.rowid,length(f.payload) FROM spatial s$bucketJoin JOIN spatial_feature sf ON sf.id=s.id JOIN features f ON f.rowid=sf.feature_row WHERE $bucketClause($predicate) AND f.cell=?$categories ORDER BY $order f.feature_id LIMIT ?",args.toTypedArray(),signal).use {cursor->
                                    while(cursor.moveToNext()) {
                                        work.ensureActive();VirtualHostServices.beforeRead()
                                        val length=cursor.getInt(2)
                                        if(remainingObjects<=0||length>remainingBytes){truncated=true;break}
                                        val row=IndexedFeature(stored,cursor.getString(0),cursor.getLong(1),length)
                                        remainingObjects--;remainingBytes-=length
                                        accept(readPositionFeature(db,row,signal))
                                    }
                                }
                                truncated
                            },
                            readRaster={grid,pixel->
                                work.ensureActive();VirtualHostServices.beforeRead()
                                rasterStore?.readWindow(grid.id,pixel.first,pixel.second,1,1){work.ensureActive();VirtualHostServices.beforeRead()}?.elevationAt(0,0)
                            },
                        )
                    } finally {
                        rasterStore?.close();db.close()
                        withContext(NonCancellable){cancellation.cancelAndJoin()}
                    }
                }
            }
        }finally {
            withContext(NonCancellable+Dispatchers.IO){mutex.withLock {leases.remove(readLease);cleanup()}}
        }
    }}catch(timeout:TimeoutCancellationException){throw IllegalStateException("CHART_POSITION_QUERY_TIMEOUT",timeout)}

    override suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int,afterId:String?):ChartFeaturePage {
        require(bounds.valid) {"CHART_QUERY_BOUNDS_INVALID"};require(limit in 1..10_000) {"CHART_QUERY_LIMIT_INVALID"}
        val displayWindow=mutex.withLock {displayWindows[snapshotId]}
        require(displayWindow==null||ChartDisplayWindow.contains(displayWindow,bounds)){"CHART_DISPLAY_WINDOW_EXCEEDED"}
        return withSnapshotRead(snapshotId) {selected,signal->
            val rows=pageCandidates(limit)
            for(stored in selected) {
                currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                openIndex(stored).use {db->
                    val predicate=bounds.split().joinToString(" OR ") {"(s.max_x>=? AND s.min_x<=? AND s.max_y>=? AND s.min_y<=?)"}
                    val buckets=portableBuckets(db,bounds)
                    val bucketJoin=if(buckets==null)"" else " JOIN spatial_bucket sb ON sb.spatial_id=s.id"
                    val bucketClause=if(buckets==null)"" else "sb.bucket IN (${buckets.joinToString(","){ "?" }}) AND "
                    val args=mutableListOf<String>();buckets?.let{args+=it.map(Int::toString)}
                    bounds.split().forEach {args+=listOf(it.west,it.east,it.south,it.north).map(Double::toString)};args+=afterId.orEmpty();args+=(limit+1).toString()
                    db.rawQuery("SELECT DISTINCT f.feature_id,f.rowid,length(f.payload) FROM spatial s$bucketJoin JOIN spatial_feature sf ON sf.id=s.id JOIN features f ON f.rowid=sf.feature_row WHERE $bucketClause($predicate) AND f.feature_id>? ORDER BY f.feature_id LIMIT ?",args.toTypedArray(),signal).use {cursor->
                        while(cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                            retainCandidate(rows,IndexedFeature(stored,cursor.getString(0),cursor.getLong(1),cursor.getInt(2)),limit)
                        }
                    }
                }
            }
            val page=readPage(rows,limit,signal)
            if(displayWindow==null)page else {
                val work=currentCoroutineContext()
                val clipper=ChartDisplayWindow(bounds){work.ensureActive();VirtualHostServices.beforeRead()}
                page.copy(features=page.features.map {feature->feature.copy(geometry=clipper.clip(feature.geometry))})
            }
        }
    }


    override suspend fun querySpatial(snapshotId:String,bounds:ChartBounds,filter:ChartSpatialFilter,limit:Int,afterId:String?):ChartFeaturePage {
        require(bounds.valid) {"CHART_QUERY_BOUNDS_INVALID"}
        require(limit in 1..10_000) {"CHART_QUERY_LIMIT_INVALID"}
        require(filter.cellIds.size<=256&&filter.kinds.size<=NauticalFeatureKind.entries.size&&filter.detailTiers.size<=5&&filter.detailTiers.all{it in 0..4}&&filter.detailScales.size<=16&&filter.detailScales.all{it in 1..100_000_000}) {"CHART_SPATIAL_FILTER_TOO_LARGE"}
        val displayWindow=mutex.withLock {displayWindows[snapshotId]}
        require(displayWindow==null||ChartDisplayWindow.contains(displayWindow,bounds)){"CHART_DISPLAY_WINDOW_EXCEEDED"}
        val cells=filter.cellIds.sorted()
        val kinds=filter.kinds.map{it.name}.sorted()
        val tiers=filter.detailTiers.sorted()
        val scales=filter.detailScales.sorted()
        return withSnapshotRead(snapshotId) {selected,signal->
            val rows=pageCandidates(limit)
            for(stored in selected) {
                currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                openIndex(stored).use {db->
                    val predicate=bounds.split().joinToString(" OR ") {"(s.max_x>=? AND s.min_x<=? AND s.max_y>=? AND s.min_y<=?)"}
                    val buckets=portableBuckets(db,bounds)
                    val bucketJoin=if(buckets==null)"" else " JOIN spatial_bucket sb ON sb.spatial_id=s.id"
                    val bucketClause=if(buckets==null)"" else "sb.bucket IN (${buckets.joinToString(","){ "?" }}) AND "
                    val cellClause=if(cells.isEmpty())"" else " AND f.cell IN (${cells.joinToString(","){ "?" }})"
                    val kindClause=if(kinds.isEmpty())"" else " AND f.kind IN (${kinds.joinToString(","){ "?" }})"
                    val tierClause=if(tiers.isEmpty())"" else " AND (f.detail_tier IS NULL OR f.detail_tier IN (${tiers.joinToString(","){ "?" }}))"
                    val scaleClause=if(scales.isEmpty())"" else " AND (f.detail_scale IS NULL OR f.detail_scale IN (${scales.joinToString(","){ "?" }}))"
                    val args=mutableListOf<String>();buckets?.let{args+=it.map(Int::toString)}
                    bounds.split().forEach {args+=listOf(it.west,it.east,it.south,it.north).map(Double::toString)}
                    args+=afterId.orEmpty();args+=cells;args+=kinds;args+=tiers.map(Int::toString);args+=scales.map(Int::toString);args+=(limit+1).toString()
                    db.rawQuery("SELECT DISTINCT f.feature_id,f.rowid,length(f.payload) FROM spatial s$bucketJoin JOIN spatial_feature sf ON sf.id=s.id JOIN features f ON f.rowid=sf.feature_row WHERE $bucketClause($predicate) AND f.feature_id>?$cellClause$kindClause$tierClause$scaleClause ORDER BY f.feature_id LIMIT ?",args.toTypedArray(),signal).use {cursor->
                        while(cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                            retainCandidate(rows,IndexedFeature(stored,cursor.getString(0),cursor.getLong(1),cursor.getInt(2)),limit)
                        }
                    }
                }
            }
            val page=readPage(rows,limit,signal)
            if(displayWindow==null)page else {
                val work=currentCoroutineContext()
                val clipper=ChartDisplayWindow(bounds){work.ensureActive();VirtualHostServices.beforeRead()}
                page.copy(features=page.features.map {feature->feature.copy(geometry=clipper.clip(feature.geometry))})
            }
        }
    }

    override suspend fun rasterWindows(snapshotId:String,bounds:ChartBounds,maxCells:Int):List<ChartRasterWindow> {
        require(bounds.valid&&maxCells in 1..1_048_576){"CHART_RASTER_QUERY_INVALID"}
        val displayWindow=mutex.withLock {displayWindows[snapshotId]}
        require(displayWindow==null||ChartDisplayWindow.contains(displayWindow,bounds)){"CHART_DISPLAY_WINDOW_EXCEEDED"}
        return withSnapshotRead(snapshotId) {selected,_->
            val work=currentCoroutineContext()
            val result=mutableListOf<ChartRasterWindow>();var remaining=maxCells
            for(stored in selected) {
                work.ensureActive();VirtualHostServices.beforeRead()
                if(stored.dataset.rasters.isNullOrEmpty())continue
                RasterBathymetryStore.open(File(root,stored.directory),context).use {store->
                    for(grid in store.grids.sortedBy { grid->stored.dataset.cells.firstOrNull { it.cellId==grid.cellId }?.priority?:Int.MAX_VALUE }) {
                        val rectangles=linkedSetOf<List<Int>>()
                        for(box in bounds.split())for(shift in listOf(-360.0,0.0,360.0,720.0)) {
                            val west=max(grid.westEdge,box.west+shift);val east=min(grid.westEdge+grid.width*grid.pixelWidthDegrees,box.east+shift)
                            val north=min(grid.northEdge,box.north);val south=max(grid.northEdge-grid.height*grid.pixelHeightDegrees,box.south)
                            if(east<west||north<south)continue
                            if(east==west&&(box.west!=box.east||west<grid.westEdge||west>=grid.westEdge+grid.width*grid.pixelWidthDegrees))continue
                            if(north==south&&(box.north!=box.south||north>grid.northEdge||north<=grid.northEdge-grid.height*grid.pixelHeightDegrees))continue
                            val x=floor((west-grid.westEdge)/grid.pixelWidthDegrees).toInt().coerceIn(0,grid.width-1)
                            val y=floor((grid.northEdge-north)/grid.pixelHeightDegrees).toInt().coerceIn(0,grid.height-1)
                            val endX=(floor((east-grid.westEdge)/grid.pixelWidthDegrees).toInt()+1).coerceIn(x+1,grid.width)
                            val endY=(floor((grid.northEdge-south)/grid.pixelHeightDegrees).toInt()+1).coerceIn(y+1,grid.height)
                            rectangles+=listOf(x,y,endX-x,endY-y)
                        }
                        for((x,y,width,height) in rectangles) {
                            work.ensureActive();VirtualHostServices.beforeRead()
                            val count=width.toLong()*height
                            require(count<=remaining){"GEBCO_WINDOW_LIMIT:请缩短航段或减少重叠资料 / Shorten the passage or select fewer overlapping grids"}
                            remaining-=count.toInt()
                            result+=ChartRasterWindow(grid,store.readWindow(grid.id,x,y,width,height){work.ensureActive();VirtualHostServices.beforeRead()})
                        }
                    }
                }
            }
            result
        }
    }

    override suspend fun browse(snapshotId:String,filter:ChartFeatureFilter,limit:Int,afterId:String?):ChartFeaturePage {
        require(limit in 1..1_000) {"CHART_BROWSE_LIMIT_INVALID"}
        require(filter.text.length<=512) {"CHART_SEARCH_TOO_LONG"}
        val normalizedQuery=ChartFeatureIndex.normalized(filter.text)
        return withSnapshotRead(snapshotId) {selected,signal->
            val rows=pageCandidates(limit)
            for(stored in selected) {
                currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                if(filter.cellId!=null&&stored.dataset.cells.none {it.cellId==filter.cellId})continue
                openIndex(stored).use {db->
                    val columns=mutableSetOf<String>()
                    db.rawQuery("PRAGMA table_info(features)",null,signal).use {cursor->while(cursor.moveToNext())columns+=cursor.getString(1)}
                    val indexed=columns.containsAll(setOf("kind","search"))
                    val conditions=mutableListOf("f.feature_id>?");val args=mutableListOf(afterId.orEmpty())
                    filter.cellId?.let {cell->conditions+="f.cell=?";args+=cell}
                    if(rows.size>limit)rows.peek()?.let {last->conditions+="f.feature_id<?";args+=last.id}
                    if(indexed&&filter.kinds.isNotEmpty()) {
                        val kinds=filter.kinds.sortedBy {it.name};conditions+="f.kind IN (${kinds.joinToString(",") {"?"}})";args+=kinds.map {it.name}
                    }
                    if(indexed&&normalizedQuery.isNotEmpty()) {conditions+="instr(f.search,?)>0";args+=normalizedQuery}
                    val needsLegacyFilter=!indexed&&(filter.kinds.isNotEmpty()||normalizedQuery.isNotEmpty())
                    val limitClause=if(needsLegacyFilter)"" else " LIMIT ?".also {args+=(limit+1).toString()}
                    val sql="SELECT f.feature_id,f.rowid,length(f.payload) FROM features f WHERE ${conditions.joinToString(" AND ")} ORDER BY f.feature_id$limitClause"
                    var matches=0
                    db.rawQuery(sql,args.toTypedArray(),signal).use {cursor->
                        while(cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                            val row=IndexedFeature(stored,cursor.getString(0),cursor.getLong(1),cursor.getInt(2))
                            // 旧版本不可原地升级：按主键游标逐条解析，最多持有一个候选对象，不把全库装进内存。
                            if(needsLegacyFilter&&!ChartFeatureIndex.matches(readIndexedFeature(db,row,signal),filter,normalizedQuery))continue
                            retainCandidate(rows,row,limit)
                            if(++matches>=limit+1)break
                        }
                    }
                }
            }
            readPage(rows,limit,signal)
        }
    }

    override suspend fun readFeature(snapshotId:String,featureId:String):NauticalFeature? {
        require(featureId.isNotBlank()) {"CHART_FEATURE_ID_REQUIRED"}
        return withSnapshotRead(snapshotId) {selected,signal->
            for(stored in selected) {
                currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                openIndex(stored).use {db->
                    db.rawQuery("SELECT feature_id,rowid,length(payload) FROM features WHERE feature_id=?",arrayOf(featureId),signal).use {cursor->
                        if(cursor.moveToFirst())return@withSnapshotRead readIndexedFeature(db,IndexedFeature(stored,cursor.getString(0),cursor.getLong(1),cursor.getInt(2)),signal)
                    }
                }
            }
            null
        }
    }
    private fun copyPackage(uri:Uri,directory:File,check:()->Unit):CopiedPackage {
        val entries=mutableListOf<Pair<String,Uri>>();var visited=0
        if(uri.scheme=="content"&&DocumentsContract.isTreeUri(uri)) {
            runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            fun walk(documentId:String,depth:Int,prefix:String="") {
                check();require(depth<=12&&entries.size<=10_000) {"CHART_FOLDER_LIMIT"}
                val children=DocumentsContract.buildChildDocumentsUriUsingTree(uri,documentId)
                context.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use {cursor->
                    while(cursor.moveToNext()) {check();require(++visited<=10_000){"CHART_FOLDER_LIMIT"};val id=cursor.getString(0);val name=cursor.getString(1);if(cursor.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR)walk(id,depth+1,"$prefix$name/")else entries+=(prefix+name) to DocumentsContract.buildDocumentUriUsingTree(uri,id)}
                } ?: error("CHART_FOLDER_PERMISSION_LOST")
            }
            walk(DocumentsContract.getTreeDocumentId(uri),0)
        }else {
            if(uri.scheme=="content")runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            val name=sourceName(uri);requireDataName(name)
            entries+=name to uri
        }
        // A declared package cannot fall through to legacy ZIP sniffing, even if malformed.
        // One package is one collection; combining it with folder siblings would discard its contract.
        require(entries.none {it.first.substringAfterLast('.').lowercase(java.util.Locale.ROOT) in setOf("yklchart","yklcharts","mbtiles")}){"CHART_DATA_EXTENSION_REQUIRED"}
        val packages=entries.filter {it.first.endsWith(".yklgeodata",ignoreCase=true)}
        if(packages.isNotEmpty()) {
            require(packages.size==1&&entries.size==1){"YKLCHART_IMPORT_PACKAGE_SEPARATELY"}
            val packageUri=packages.single().second
            val input=if(packageUri.scheme=="file")File(requireNotNull(packageUri.path)).inputStream()
                else context.contentResolver.openInputStream(packageUri)?:error("CHART_DOCUMENT_PERMISSION_LOST")
            val packageManifest=input.use {YokuliChartPackage.extract(it,directory,"data",check)}
            return CopiedPackage(packageManifest.files.map {File(directory,it.path)},packageManifest)
        }
        val native=entries.filter {entry->
            val name=entry.first.substringAfterLast('/');val extension=name.substringAfterLast('.',"").lowercase()
            extension in setOf("gpkg","tif","tiff","asc","ascii")||name.matches(Regex("[A-Za-z0-9_]+\\.[0-9]{3}"))&&!name.equals("CATALOG.031",true)
        }
        if(native.isNotEmpty()&&entries.none {it.first.endsWith(".zip",true)||it.first.endsWith("PERMIT.TXT",true)||it.first.endsWith(".pmt",true)||it.first.substringAfterLast('.').lowercase() in setOf("nc","nc4","h5","hdf5")}) {
            val pending=linkedMapOf<String,Uri>();val names=hashSetOf<String>()
            val files=native.sortedBy {it.first}.map {(name,document)->
                val safe=name.substringAfterLast('/');val enc=safe.substringAfterLast('.').toIntOrNull()!=null
                val localName=if(enc||name==safe)safe else "${UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8))}_$safe"
                val file=File(directory,if(enc)localName.uppercase(java.util.Locale.ROOT)else localName)
                require(names.add(file.name)){"CHART_DUPLICATE_PACKAGE_FILENAME:$safe"};pending[file.absolutePath]=document;file
            }
            return CopiedPackage(files,pendingCopies=pending)
        }
        var total=0L;var fileCount=0
        val results=mutableListOf<File>()
        val pendingSources=linkedMapOf<String,Uri>()
        fun copy(input:InputStream,name:String) {
            check();require(++fileCount<=10_000) {"CHART_PACKAGE_FILE_LIMIT"}
            val safe=name.substringAfterLast('/').substringAfterLast('\\')
            if(safe.equals("PERMIT.TXT",true)||safe.endsWith(".pmt",true))error("S63_REQUIRES_LICENSED_CLIENT_AND_DEVICE_USER_PERMIT")
            val extension=safe.substringAfterLast('.',"").lowercase()
            require(extension !in setOf("yklchart","yklcharts","yklgeodata","mbtiles")&&!safe.equals("manifest.json",true)){"CHART_DATA_EXTENSION_REQUIRED"}
            if(extension in setOf("nc","nc4","h5","hdf5"))error("GEBCO_USE_DATA_GEOTIFF_OR_ESRI_ASCII_NOT_NETCDF")
            val enc=safe.matches(Regex("[A-Za-z0-9_]+\\.[0-9]{3}"))&&!safe.equals("CATALOG.031",true)
            val supported=enc||extension in setOf("gpkg","tif","tiff","asc","ascii")
            if(!supported) {
                val buffer=ByteArray(64*1024)
                while(true) {check();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=64_000_000_000L) {"CHART_PACKAGE_SIZE_LIMIT"}}
                return
            }
            val localName=if(enc||name==safe)safe else "${UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8))}_$safe"
            val target=File(directory,if(enc)localName.uppercase(java.util.Locale.ROOT)else localName);require(!target.exists()) {"CHART_DUPLICATE_PACKAGE_FILENAME:$safe"}
            target.outputStream().buffered().use {out->val buffer=ByteArray(64*1024);var size=0L;while(true){check();val n=input.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=32_000_000_000L&&total<=64_000_000_000L) {"CHART_PACKAGE_SIZE_LIMIT"};out.write(buffer,0,n)}}
            results+=target
        }
        for((name,source) in entries) {
            check()
            val extension=name.substringAfterLast('.',"").lowercase()
            if(extension !in setOf("zip","gpkg","tif","tiff","asc","ascii","nc","nc4","h5","hdf5","pmt")&&extension.toIntOrNull()==null&&!name.endsWith("PERMIT.TXT",true))continue
            if(name.endsWith("PERMIT.TXT",true))error("S63_REQUIRES_LICENSED_CLIENT_AND_DEVICE_USER_PERMIT")
            if(isRawDataName(name)) {
                val safe=name.substringAfterLast('/');val enc=safe.substringAfterLast('.').toIntOrNull()!=null
                val localName=if(enc||name==safe)safe else "${UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8))}_$safe"
                val logical=File(directory,if(enc)localName.uppercase(java.util.Locale.ROOT)else localName)
                require(results.none {it==logical}){"CHART_DUPLICATE_PACKAGE_FILENAME:$safe"}
                pendingSources[logical.absolutePath]=source;results+=logical;continue
            }
            val stream=if(source.scheme=="file")File(requireNotNull(source.path)).inputStream()else context.contentResolver.openInputStream(source) ?: error("CHART_DOCUMENT_PERMISSION_LOST")
            stream.buffered().use {input->
                input.mark(4);val magic=ByteArray(4);val read=input.read(magic);input.reset()
                if(read==4&&magic[0]==80.toByte()&&magic[1]==75.toByte()) {
                    require(extension=="zip"){"CHART_DATA_FORMAT_UNSUPPORTED"}
                    ZipInputStream(input).use {zip->
                    while(true){check();val entry=zip.nextEntry ?: break;if(!entry.isDirectory)copy(zip,"$name/${entry.name}");zip.closeEntry()}
                }}else {require(extension!="zip"){"CHART_DATA_FORMAT_UNSUPPORTED"};copy(input,name)}
            }
        }
        return CopiedPackage(results,pendingCopies=pendingSources)
    }
    private fun writeCell(file:File,cell:S57Reader.Cell,check:()->Unit) {
        DataOutputStream(GZIPOutputStream(file.outputStream().buffered())).use {out->
            out.writeUTF("YOKULI_S57_RECORDS_1");out.writeUTF(gson.toJson(cell.header));out.writeUTF(gson.toJson(cell.parameters));out.writeInt(cell.lexical);out.writeInt(cell.nationalLexical);out.writeInt(cell.records.size)
            cell.records.values.forEach {record->check();out.writeInt(record.fields.size);record.fields.forEach {(tag,bytes)->out.writeUTF(tag);out.writeInt(bytes.size);out.write(bytes)}}
        }
    }
    private fun readCell(file:File,check:()->Unit):S57Reader.Cell=DataInputStream(GZIPInputStream(file.inputStream().buffered())).use {input->
        require(input.readUTF()=="YOKULI_S57_RECORDS_1") {"S57_STORED_RECORD_FORMAT"}
        val header=gson.fromJson(input.readUTF(),S57Reader.Header::class.java);val parameters=gson.fromJson(input.readUTF(),S57Reader.Parameters::class.java);val lexical=input.readInt();val national=input.readInt();val count=input.readInt();require(count in 0..1_000_000) {"S57_STORED_RECORD_LIMIT"}
        val records=linkedMapOf<String,S57Reader.Record>()
        repeat(count) {check();val fields=linkedMapOf<String,ByteArray>();val number=input.readInt();require(number in 1..32) {"S57_STORED_FIELD_LIMIT"};repeat(number){val tag=input.readUTF();val size=input.readInt();require(size in 0..32_000_000) {"S57_STORED_FIELD_SIZE"};val bytes=ByteArray(size);input.readFully(bytes);fields[tag]=bytes};val record=S57Reader.Record(fields);require(records.put(record.key,record)==null) {"S57_STORED_RECORD_DUPLICATE"}}
        S57Reader.Cell(header,parameters,lexical,national,records)
    }
    private fun hasAtomicFile(file:AtomicFile)=file.baseFile.exists()||File(file.baseFile.path+".bak").exists()
    private fun saveJob(){pending?.let {writeAtomic(jobFile,gson.toJson(it))}}
    private fun writeAtomic(file:AtomicFile,text:String) {if(file.baseFile==manifest.baseFile)require(text.toByteArray(Charsets.UTF_8).size<=MAX_CATALOGUE_BYTES){"CHART_CATALOGUE_SIZE_LIMIT"};var stream:FileOutputStream?=null;try {VirtualHostServices.beforeWrite();stream=file.startWrite();stream.write(text.toByteArray(Charsets.UTF_8));stream.fd.sync();VirtualHostServices.beforeWrite();file.finishWrite(stream);stream=null;require(file.openRead().bufferedReader(Charsets.UTF_8).use {it.readText()}==text) {"CHART_STORAGE_READBACK_FAILED"}}catch(error:Exception){if(stream!=null)runCatching {file.failWrite(stream)};storageFault=error.message ?: "CHART_STORAGE_WRITE_FAILED";mutable.value=mutable.value.copy(error=storageFault);throw error}}
    private fun cleanup() {
        // 释放快照租约不依赖磁盘；只把故障期间的旧版本回收延后。
        if(runCatching{VirtualHostServices.beforeWrite()}.isFailure)return
        val keep=catalogue.datasets.map {it.directory}.toSet()+leases.values.flatten().map {it.directory}
        sourceChecks.keys.retainAll(keep);sourceIssues.keys.retainAll(keep);sourceLinks.keys.retainAll(keep)
        root.listFiles().orEmpty().filter {it.isDirectory&&it.name.startsWith("version-")&&it.name !in keep}.forEach {old->
            // 锁内只原子摘除无租约版本；耗时递归删除留在 IO 后台，不能拖住准星返回。
            val retired=File(root,"retired-${UUID.randomUUID()}")
            if(old.renameTo(retired))scheduleRemoval(retired)
        }
        root.listFiles().orEmpty().filter {it.isDirectory&&it.name.startsWith("retired-")}.forEach(::scheduleRemoval)
        if(exportWorker?.isActive!=true)root.listFiles().orEmpty().filter {it.isFile&&it.name.startsWith("export-")}.forEach {it.delete()}
        if(importJob?.isCompleted!=false)root.listFiles().orEmpty().filter {it.isDirectory&&it.name.startsWith("stage-")}.forEach {old->
            val retired=File(root,"retired-${UUID.randomUUID()}");if(old.renameTo(retired))scheduleRemoval(retired)
        }
    }
    private fun scheduleRemoval(file:File) {
        if(cleanupScheduled.add(file.name))scope.launch {
            try {file.deleteRecursively()}finally {cleanupScheduled.remove(file.name)}
        }
    }
    companion object {private const val MAX_CATALOGUE_BYTES=32L*1024*1024
        private val exportWorkingPhases=setOf(ChartExportPhase.PREPARING,ChartExportPhase.PACKAGING,ChartExportPhase.COPYING)
        private val workingPhases=setOf(ChartImportPhase.COPYING,ChartImportPhase.PARSING,ChartImportPhase.INDEXING,ChartImportPhase.COMMITTING)}
}

/** 包围盒用于召回，不代替精确几何分析；最小经度弧在日期变更线处分成两个索引矩形。 */
internal fun geometryBounds(geometry:ChartGeometry):List<ChartBounds> {
    val points=geometry.parts.flatMap {it.points};if(points.isEmpty())return emptyList()
    val longitudes=points.map {it.longitude}.distinct().sorted();val south=points.minOf {it.latitude};val north=points.maxOf {it.latitude}
    if(longitudes.size==1)return listOf(ChartBounds(longitudes[0],south,longitudes[0],north))
    var largest=-1.0;var gap=0
    for(i in longitudes.indices) {val next=if(i==longitudes.lastIndex)longitudes[0]+360 else longitudes[i+1];val size=next-longitudes[i];if(size>largest){largest=size;gap=i}}
    val west=longitudes[(gap+1)%longitudes.size];val east=longitudes[gap]
    return ChartBounds(west,south,east,north).split()
}
private fun coalesceBounds(bounds:List<ChartBounds>):List<ChartBounds> {
    if(bounds.isEmpty())return emptyList()
    val groups=if(bounds.any {it.west<=-179.999}&&bounds.any {it.east>=179.999})listOf(bounds.filter {it.west<0},bounds.filter {it.west>=0})else listOf(bounds)
    return groups.filter {it.isNotEmpty()}.map {group->ChartBounds(group.minOf {it.west},group.minOf {it.south},group.maxOf {it.east},group.maxOf {it.north})}
}

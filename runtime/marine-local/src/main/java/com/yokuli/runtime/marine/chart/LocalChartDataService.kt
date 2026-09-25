package com.yokuli.runtime.marine.chart

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val root=File(context.noBackupFilesDir,"chart-datasets").apply {mkdirs()}
    private val manifest=AtomicFile(File(root,"catalogue.json"))
    private val jobFile=AtomicFile(File(root,"import.json"))
    private val gson=Gson()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(ChartDataState())
    override val state=mutable.asStateFlow()
    private var catalogue=Catalogue()
    private var storageFault:String?=null
    private var importJob:Job?=null
    private var pending:Pending?=null
    private val leases=mutableMapOf<String,List<Stored>>()
    private val dictionaries by lazy {S57Dictionaries(context.assets.open("chartdata/s57objectclasses.csv").bufferedReader().use {it.readText()},context.assets.open("chartdata/s57attributes.csv").bufferedReader().use {it.readText()})}
    init {scope.launch {restore()}}

    override suspend fun retryRestore()=withContext(Dispatchers.IO) {restore()}
    private suspend fun restore()=mutex.withLock {
        if(importJob?.isActive==true)return@withLock
        try {
            catalogue=if(hasAtomicFile(manifest))manifest.openRead().bufferedReader().use {gson.fromJson(it,Catalogue::class.java)} else Catalogue()
            require(catalogue.datasets.size<=2_000&&catalogue.datasets.all {it.directory.matches(Regex("version-[0-9a-f-]{36}"))}) {"CHART_CATALOGUE_INVALID"}
            pending=if(hasAtomicFile(jobFile))jobFile.openRead().bufferedReader().use {gson.fromJson(it,Pending::class.java)}else null
            val old=pending
            if(old!=null) {
                val receipt=catalogue.receipts.firstOrNull {it.requestId==old.request.requestId}
                if(receipt!=null)pending=old.copy(status=old.status.copy(phase=ChartImportPhase.COMPLETE,datasetId=receipt.datasetId,detail=""))
                else if(old.status.phase in workingPhases)pending=old.copy(status=old.status.copy(phase=ChartImportPhase.INTERRUPTED,detail="Import was interrupted. The previous complete version is preserved."))
                saveJob()
            }
            storageFault=null;publish()
            cleanup()
        }catch(error:Exception) {mutable.value=mutable.value.copy(loading=false,error=error.message ?: "CHART_CATALOGUE_UNREADABLE")}
    }
    private fun publish() {
        mutable.value=ChartDataState(catalogue.revision,catalogue.datasets.map {stored->stored.dataset.copy(offlineReadable=File(File(root,stored.directory),"features.sqlite").isFile,issue=if(File(File(root,stored.directory),"features.sqlite").isFile)null else "INSTALLED_INDEX_MISSING")},pending?.status,false,storageFault)
    }
    override suspend fun importPackage(request:ChartImportRequest):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        if(mutable.value.loading||mutable.value.error!=null)return@withLock ChartCommandResult.Failed("Chart catalogue is not readable")
        if(request.requestId.isBlank()||request.requestId.length>128||request.name.isBlank()||request.name.length>120)return@withLock ChartCommandResult.Failed("A request ID and chart name are required")
        catalogue.receipts.firstOrNull {it.requestId==request.requestId}?.let {return@withLock ChartCommandResult.Saved(it.datasetId,it.revision)}
        if(importJob?.isActive==true)return@withLock if(pending?.request?.requestId==request.requestId)ChartCommandResult.Accepted(request.requestId)else ChartCommandResult.Busy
        if(request.replaceDatasetId!=null&&catalogue.datasets.none {it.dataset.id==request.replaceDatasetId})return@withLock ChartCommandResult.Failed("The dataset to update no longer exists")
        val uri=runCatching {Uri.parse(request.sourceUri)}.getOrNull() ?: return@withLock ChartCommandResult.Failed("Source is not readable")
        if(uri.scheme !in setOf("content","file"))return@withLock ChartCommandResult.Failed("Use an Android document or folder")
        if(request.eligibility.use==ChartUse.ANALYSIS_ALLOWED&&!request.eligibility.allowsAnalysis(System.currentTimeMillis()))return@withLock ChartCommandResult.Failed("Provider, permitted use and a current licence are required")
        pending=Pending(request,ChartImportJob(request.requestId,request.name,ChartImportPhase.COPYING))
        try {saveJob()}catch(error:Exception) {return@withLock ChartCommandResult.Failed(error.message ?: "Cannot save import request")}
        publish()
        importJob=scope.launch {performImport(request)}
        ChartCommandResult.Accepted(request.requestId)
    }}
    override fun cancelImport(requestId:String) {
        scope.launch {mutex.withLock {if(pending?.request?.requestId==requestId&&pending?.status?.phase!=ChartImportPhase.COMMITTING)importJob?.cancel()}}
    }
    override suspend fun retryImport(requestId:String):ChartCommandResult {
        val request=mutex.withLock {pending?.takeIf {it.request.requestId==requestId}?.request}
            ?: return ChartCommandResult.Failed("The import request is no longer retained")
        return importPackage(request)
    }
    private suspend fun progress(phase:ChartImportPhase,done:Int=0,total:Int=0,detail:String="")=mutex.withLock {
        pending=pending?.let {it.copy(status=it.status.copy(phase=phase,completed=done,total=total,detail=detail))};publish()
    }
    private suspend fun performImport(request:ChartImportRequest) {
        val stage=File(root,"stage-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val workContext=currentCoroutineContext()
            fun check(){workContext.ensureActive();require(root.usableSpace>96_000_000L) {"CHART_STORAGE_FULL"}}
            val original=mutex.withLock {catalogue.datasets.firstOrNull {it.dataset.id==request.replaceDatasetId}}
            val source=File(stage,"source").apply {mkdirs()}
            val incoming=copyPackage(Uri.parse(request.sourceUri),source,::check)
            require(incoming.isNotEmpty()) {"CHART_NO_SUPPORTED_DATA"}
            val datasetId=original?.dataset?.id ?: UUID.nameUUIDFromBytes(request.requestId.toByteArray()).toString()
            val geopackages=incoming.filter {it.extension.equals("gpkg",ignoreCase=true)}
            require(geopackages.isEmpty()||geopackages.size==1&&incoming.size==1) {"CHART_MIXED_PACKAGE"}
            val format=if(geopackages.isEmpty())"S57"else"GPKG"
            require(original==null||original.dataset.format==format) {"CHART_FORMAT_CHANGED"}
            val revisions=if(format=="GPKG") {
                // GeoPackage 由用户在原文件中维护；每次读取完整版本，失败不动已安装的资料。
                GeoPackageChartImporter.prepare(geopackages.single(),stage,datasetId,::check,objectClasses=dictionaries.objects) {done,total,detail->
                    progress(ChartImportPhase.INDEXING,done,total,detail)
                }.associateBy {it.cellId}.toMutableMap()
            } else {
            val reader=S57Reader(dictionaries,::check)
            val raw=File(stage,"records").apply {mkdirs()}
            if(original!=null)File(File(root,original.directory),"records").listFiles().orEmpty().forEach {file->check();file.copyTo(File(raw,file.name),overwrite=false)}
            val revisions=original?.dataset?.cells?.associateBy {it.cellId}?.toMutableMap() ?: mutableMapOf()
            // 先读轻量 DSID，完整二进制记录只按一个图幅及当前增量持有，避免整套交换集同时占内存。
            val incomingCells=incoming.sortedBy {it.name}.mapIndexed {index,file->
                progress(ChartImportPhase.PARSING,index,incoming.size,file.name);check()
                val header=reader.read(file,metadataOnly=true).header
                require(file.extension.toIntOrNull()==header.update) {"S57_FILE_UPDATE_NUMBER_MISMATCH:${file.name}"}
                header to file
            }.groupBy {it.first.cell}
            for((cellId,updates) in incomingCells) {
                check();val existingFile=File(raw,"$cellId.raw.gz")
                val old=if(existingFile.isFile)readCell(existingFile,::check)else null
                val bases=updates.filter {it.first.update==0}
                require(bases.size<=1) {"S57_DUPLICATE_BASE:$cellId"}
                var cell=bases.firstOrNull()?.let {reader.base(reader.read(it.second))} ?: old ?: error("S57_BASE_MISSING:$cellId")
                require(updates.map {it.first.update}.distinct().size==updates.size) {"S57_DUPLICATE_UPDATE:$cellId"}
                updates.filter {it.first.update>0}.sortedBy {it.first.update}.forEach {(header,file)->
                    progress(ChartImportPhase.PARSING,detail=file.name)
                    if(header.update<=cell.header.update&&bases.isEmpty())error("S57_OLD_OR_DUPLICATE_UPDATE:$cellId")
                    cell=reader.apply(cell,reader.read(file))
                }
                if(old!=null&&cell.header.edition!=0)require(cell.header.edition>old.header.edition||(cell.header.edition==old.header.edition&&cell.header.update>=old.header.update)) {"S57_REVISION_REGRESSION:$cellId"}
                writeCell(existingFile,cell,::check)
                revisions[cellId]=ChartCellRevision(cellId,cell.header.edition,cell.header.update,cell.header.usage,cell.parameters.scale.takeIf {it>0},cell.header.issue,cancelled=cell.header.edition==0)
            }
            require(revisions.size<=2_000) {"CHART_CELL_LIMIT"}
            val database=File(stage,"features.sqlite")
            SQLiteDatabase.openOrCreateDatabase(database,null).use {db->
                db.execSQL("PRAGMA journal_mode=DELETE")
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
                                coverage+=CoverageEvidence(feature.id,feature.cellId,feature.geometry,feature.attributes["CATCOV"]=="1",feature.source.compilationScale)
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
                db.execSQL("PRAGMA optimize")
            }
            revisions
            }
            // 原文件从不修改。只保留本应用已解析的未加密记录与只读索引。
            source.deleteRecursively()
            check()
            progress(ChartImportPhase.COMMITTING,detail="Installing the complete indexed version")
            withContext(NonCancellable) {mutex.withLock {
                if(original!=null)require(catalogue.datasets.firstOrNull {it.dataset.id==datasetId}?.dataset?.revision==original.dataset.revision) {"CHART_CHANGED_DURING_IMPORT"}
                val directory="version-${UUID.randomUUID()}";val target=File(root,directory)
                require(stage.renameTo(target)) {"CHART_ATOMIC_RENAME_FAILED"}
                val revision=catalogue.revision+1
                val dataset=ChartDataset(datasetId,request.name.trim(),format=format,revision=revision,installedAtUtc=System.currentTimeMillis(),eligibility=request.eligibility,cells=revisions.values.sortedBy {it.cellId})
                val next=catalogue.copy(revision=revision,datasets=catalogue.datasets.filterNot {it.dataset.id==datasetId}+Stored(dataset,directory),receipts=(catalogue.receipts+Receipt(request.requestId,datasetId,revision)).takeLast(64))
                writeAtomic(manifest,gson.toJson(next));catalogue=next
                pending=Pending(request,ChartImportJob(request.requestId,request.name,ChartImportPhase.COMPLETE,revisions.size,revisions.size,"",datasetId))
                runCatching {saveJob()};publish();cleanup()
            }}
        }catch(cancel:CancellationException) {
            withContext(NonCancellable) {mutex.withLock {pending=pending?.copy(status=pending!!.status.copy(phase=ChartImportPhase.CANCELLED,detail="Import cancelled; the previous version is preserved"));runCatching {saveJob()};publish()}}
        }catch(error:Exception) {
            withContext(NonCancellable) {mutex.withLock {pending=pending?.copy(status=pending!!.status.copy(phase=ChartImportPhase.FAILED,detail=error.message ?: error.javaClass.simpleName));runCatching {saveJob()};publish()}}
        }finally {
            stage.deleteRecursively()
            // 已改名版本即使最终回执失败也保留到下次读取目录：不能删掉可能已经由原子清单引用的索引。
        }
    }
    override suspend fun rename(datasetId:String,name:String):ChartCommandResult {
        if(name.isBlank()||name.length>120)return ChartCommandResult.Failed("Use a name of 1–120 characters")
        return edit(datasetId){it.copy(name=name.trim())}
    }
    override suspend fun updateEligibility(datasetId:String,value:DataEligibility):ChartCommandResult {
        if(value.use==ChartUse.ANALYSIS_ALLOWED&&!value.allowsAnalysis(System.currentTimeMillis()))return ChartCommandResult.Failed("Provider and permitted-use evidence are required")
        return edit(datasetId){it.copy(eligibility=value)}
    }
    private suspend fun edit(datasetId:String,block:(ChartDataset)->ChartDataset):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        if(mutable.value.loading||mutable.value.error!=null)return@withLock ChartCommandResult.Failed("CHART_CATALOGUE_UNREADABLE")
        val old=catalogue.datasets.firstOrNull {it.dataset.id==datasetId} ?: return@withLock ChartCommandResult.Failed("Dataset no longer exists")
        try {val revision=catalogue.revision+1;val next=catalogue.copy(revision=revision,datasets=catalogue.datasets.map {if(it==old)it.copy(dataset=block(it.dataset).copy(revision=revision))else it});writeAtomic(manifest,gson.toJson(next));catalogue=next;publish();ChartCommandResult.Saved(datasetId,revision)}catch(error:Exception){ChartCommandResult.Failed(error.message ?: "Could not save chart metadata")}
    }}
    override suspend fun remove(datasetId:String):ChartCommandResult=withContext(Dispatchers.IO) {mutex.withLock {
        if(mutable.value.loading||mutable.value.error!=null)return@withLock ChartCommandResult.Failed("CHART_CATALOGUE_UNREADABLE")
        if(pending?.request?.replaceDatasetId==datasetId&&importJob?.isActive==true)return@withLock ChartCommandResult.Busy
        try {val next=catalogue.copy(revision=catalogue.revision+1,datasets=catalogue.datasets.filterNot {it.dataset.id==datasetId});writeAtomic(manifest,gson.toJson(next));catalogue=next;publish();cleanup();ChartCommandResult.Saved(datasetId,next.revision)}catch(error:Exception){ChartCommandResult.Failed(error.message ?: "Could not remove chart")}
    }}
    override suspend fun acquireSnapshot(datasetIds:List<String>):ChartDataSnapshot=mutex.withLock {
        require(!mutable.value.loading&&mutable.value.error==null) {"CHART_CATALOGUE_UNREADABLE"}
        require(leases.size<32) {"CHART_SNAPSHOT_LIMIT"}
        val ids=datasetIds.distinct();val selected=ids.mapNotNull {id->catalogue.datasets.firstOrNull {it.dataset.id==id}}
        val id=UUID.randomUUID().toString();leases[id]=selected
        ChartDataSnapshot(id,catalogue.revision,selected.map {it.dataset},ids.filter {wanted->selected.none {it.dataset.id==wanted}})
    }
    override suspend fun releaseSnapshot(snapshotId:String)=withContext(NonCancellable+Dispatchers.IO) {mutex.withLock {leases.remove(snapshotId);cleanup()}}
    private data class IndexedFeature(val stored:Stored,val id:String,val rowId:Long,val length:Int)

    /** 一次读取单独保留版本：调用方离开页面释放快照时，正在关闭的 SQLite 仍不能被删掉。 */
    private suspend fun <T> withSnapshotRead(snapshotId:String,block:suspend (List<Stored>,CancellationSignal)->T):T=withContext(Dispatchers.IO) {
        val readLease=UUID.randomUUID().toString()
        val selected=mutex.withLock {(leases[snapshotId]?.toList() ?: error("CHART_SNAPSHOT_EXPIRED")).also {leases[readLease]=it}}
        try {
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

    private fun openIndex(stored:Stored):SQLiteDatabase {
        val file=File(File(root,stored.directory),"features.sqlite")
        require(file.isFile) {"CHART_INDEX_MISSING:${stored.dataset.id}"}
        return SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    }

    /** Android CursorWindow 有单行容量上限；长几何分段读取，绝不以截断 JSON 代替对象。 */
    private suspend fun readIndexedFeature(db:SQLiteDatabase,row:IndexedFeature,signal:CancellationSignal):NauticalFeature {
        require(row.length in 1..8_000_000) {"CHART_FEATURE_PAYLOAD_INVALID"}
        val payload=StringBuilder(row.length)
        var position=1
        while(position<=row.length) {
            currentCoroutineContext().ensureActive()
            db.rawQuery("SELECT substr(payload,?,?) FROM features WHERE rowid=?",arrayOf(position.toString(),"128000",row.rowId.toString()),signal).use {part->
                require(part.moveToFirst()) {"CHART_FEATURE_ROW_MISSING"}
                val text=part.getString(0)
                require(!text.isNullOrEmpty()) {"CHART_FEATURE_PAYLOAD_TRUNCATED"}
                payload.append(text)
            }
            position+=128_000
        }
        currentCoroutineContext().ensureActive()
        val feature=requireNotNull(gson.fromJson(payload.toString(),NauticalFeature::class.java)) {"CHART_FEATURE_PAYLOAD_INVALID"}
        require(feature.id==row.id&&feature.datasetId==row.stored.dataset.id) {"CHART_FEATURE_ID_MISMATCH"}
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
                currentCoroutineContext().ensureActive()
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

    override suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int,afterId:String?):ChartFeaturePage {
        require(bounds.valid) {"CHART_QUERY_BOUNDS_INVALID"};require(limit in 1..10_000) {"CHART_QUERY_LIMIT_INVALID"}
        return withSnapshotRead(snapshotId) {selected,signal->
            val rows=pageCandidates(limit)
            for(stored in selected) {
                currentCoroutineContext().ensureActive()
                openIndex(stored).use {db->
                    val predicate=bounds.split().joinToString(" OR ") {"(s.max_x>=? AND s.min_x<=? AND s.max_y>=? AND s.min_y<=?)"}
                    val args=mutableListOf<String>();bounds.split().forEach {args+=listOf(it.west,it.east,it.south,it.north).map(Double::toString)};args+=afterId.orEmpty();args+=(limit+1).toString()
                    db.rawQuery("SELECT DISTINCT f.feature_id,f.rowid,length(f.payload) FROM spatial s JOIN spatial_feature sf ON sf.id=s.id JOIN features f ON f.rowid=sf.feature_row WHERE ($predicate) AND f.feature_id>? ORDER BY f.feature_id LIMIT ?",args.toTypedArray(),signal).use {cursor->
                        while(cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            retainCandidate(rows,IndexedFeature(stored,cursor.getString(0),cursor.getLong(1),cursor.getInt(2)),limit)
                        }
                    }
                }
            }
            readPage(rows,limit,signal)
        }
    }

    override suspend fun browse(snapshotId:String,filter:ChartFeatureFilter,limit:Int,afterId:String?):ChartFeaturePage {
        require(limit in 1..1_000) {"CHART_BROWSE_LIMIT_INVALID"}
        require(filter.text.length<=512) {"CHART_SEARCH_TOO_LONG"}
        val normalizedQuery=ChartFeatureIndex.normalized(filter.text)
        return withSnapshotRead(snapshotId) {selected,signal->
            val rows=pageCandidates(limit)
            for(stored in selected) {
                currentCoroutineContext().ensureActive()
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
                            currentCoroutineContext().ensureActive()
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
                currentCoroutineContext().ensureActive()
                openIndex(stored).use {db->
                    db.rawQuery("SELECT feature_id,rowid,length(payload) FROM features WHERE feature_id=?",arrayOf(featureId),signal).use {cursor->
                        if(cursor.moveToFirst())return@withSnapshotRead readIndexedFeature(db,IndexedFeature(stored,cursor.getString(0),cursor.getLong(1),cursor.getInt(2)),signal)
                    }
                }
            }
            null
        }
    }
    private fun copyPackage(uri:Uri,directory:File,check:()->Unit):List<File> {
        val entries=mutableListOf<Pair<String,Uri>>()
        if(uri.scheme=="content"&&DocumentsContract.isTreeUri(uri)) {
            runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            fun walk(documentId:String,depth:Int) {
                check();require(depth<=12&&entries.size<=10_000) {"CHART_FOLDER_LIMIT"}
                val children=DocumentsContract.buildChildDocumentsUriUsingTree(uri,documentId)
                context.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use {cursor->
                    while(cursor.moveToNext()) {check();val id=cursor.getString(0);val name=cursor.getString(1);if(cursor.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR)walk(id,depth+1)else entries+=name to DocumentsContract.buildDocumentUriUsingTree(uri,id)}
                } ?: error("CHART_FOLDER_PERMISSION_LOST")
            }
            walk(DocumentsContract.getTreeDocumentId(uri),0)
        }else {
            if(uri.scheme=="content")runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            val name=if(uri.scheme=="file")File(requireNotNull(uri.path)).name else context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null} ?: "chart-package"
            entries+=name to uri
        }
        var total=0L;var fileCount=0
        val results=mutableListOf<File>()
        fun copy(input:InputStream,name:String) {
            check();require(++fileCount<=10_000) {"CHART_PACKAGE_FILE_LIMIT"}
            val safe=name.substringAfterLast('/').substringAfterLast('\\')
            if(safe.equals("PERMIT.TXT",true)||safe.endsWith(".pmt",true))error("S63_REQUIRES_LICENSED_CLIENT_AND_DEVICE_USER_PERMIT")
            if((!safe.matches(Regex("[A-Za-z0-9_]+\\.[0-9]{3}"))||safe.equals("CATALOG.031",true))&&!safe.endsWith(".gpkg",true)) {
                val buffer=ByteArray(64*1024)
                while(true) {check();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=2_000_000_000L) {"CHART_PACKAGE_SIZE_LIMIT"}}
                return
            }
            val target=File(directory,safe.uppercase());require(!target.exists()) {"S57_DUPLICATE_PACKAGE_FILENAME:$safe"}
            target.outputStream().buffered().use {out->val buffer=ByteArray(64*1024);var size=0L;while(true){check();val n=input.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=512_000_000L&&total<=2_000_000_000L) {"CHART_PACKAGE_SIZE_LIMIT"};out.write(buffer,0,n)}}
            results+=target
        }
        for((name,source) in entries) {
            check();if(name.equals("PERMIT.TXT",true))error("S63_REQUIRES_LICENSED_CLIENT_AND_DEVICE_USER_PERMIT")
            val stream=if(source.scheme=="file")File(requireNotNull(source.path)).inputStream()else context.contentResolver.openInputStream(source) ?: error("CHART_DOCUMENT_PERMISSION_LOST")
            stream.buffered().use {input->
                input.mark(4);val magic=ByteArray(4);val read=input.read(magic);input.reset()
                if(read==4&&magic[0]==80.toByte()&&magic[1]==75.toByte())ZipInputStream(input).use {zip->
                    while(true){check();val entry=zip.nextEntry ?: break;if(!entry.isDirectory)copy(zip,entry.name);zip.closeEntry()}
                }else copy(input,name)
            }
        }
        return results
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
    private fun writeAtomic(file:AtomicFile,text:String) {var stream:FileOutputStream?=null;try {stream=file.startWrite();stream.write(text.toByteArray(Charsets.UTF_8));stream.fd.sync();file.finishWrite(stream);stream=null;require(file.openRead().bufferedReader(Charsets.UTF_8).use {it.readText()}==text) {"CHART_STORAGE_READBACK_FAILED"}}catch(error:Exception){if(stream!=null)runCatching {file.failWrite(stream)};storageFault=error.message ?: "CHART_STORAGE_WRITE_FAILED";mutable.value=mutable.value.copy(error=storageFault);throw error}}
    private fun cleanup() {
        val keep=catalogue.datasets.map {it.directory}.toSet()+leases.values.flatten().map {it.directory}
        root.listFiles().orEmpty().filter {it.isDirectory&&it.name.startsWith("version-")&&it.name !in keep}.forEach {it.deleteRecursively()}
        if(importJob?.isActive!=true)root.listFiles().orEmpty().filter {it.isDirectory&&it.name.startsWith("stage-")}.forEach {it.deleteRecursively()}
    }
    companion object {private val workingPhases=setOf(ChartImportPhase.COPYING,ChartImportPhase.PARSING,ChartImportPhase.INDEXING,ChartImportPhase.COMMITTING)}
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

package com.yokuli.runtime.marine.chart.terrain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

/** Core 持有作业和不可变块。已接受的任务不随页面销毁；产品驻事实版本目录并随原生资料导出。 */
class LocalChartTerrainRuntime(
    private val context:Context,
    private val charts:ChartDataService,
    private val resolveDirectory:suspend (snapshotId:String)->File,
    private val resolveSourceIdentity:suspend (snapshotId:String)->String,
) {
    private val gson=Gson()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val pending=linkedMapOf<String,Work>()
    private val wake=Channel<Unit>(Channel.CONFLATED)
    private var running:Work?=null
    private var runningJob:Deferred<Unit>?=null
    private val compiler=ChartTerrainCompiler(charts)
    private val validated=mutableSetOf<String>()
    private val mutableState=MutableStateFlow<List<ChartTerrainProgress>>(emptyList())
    val state:StateFlow<List<ChartTerrainProgress>> = mutableState.asStateFlow()
    private data class Work(val key:String,val sourceKey:String,val request:ChartTerrainRequest) {
        val queueKey:String get()="${request.datasetId}:$key"
    }
    private data class Source(val snapshot:ChartDataSnapshot,val directory:File,val sourceKey:String)
    init {
        scope.launch {for(signal in wake)while(true){
            val task=mutex.withLock {
                val work=pending.values.minByOrNull{it.request.lod}?:return@withLock null
                pending.remove(work.queueKey);running=work
                scope.async(start=CoroutineStart.LAZY){compile(work)}.also{runningJob=it;it.start()}
            }?:break
            try{task.await()}catch(cancel:CancellationException){currentCoroutineContext().ensureActive()}
            finally{mutex.withLock{running=null;runningJob=null}}
        }}
        scope.launch{restore()}
    }

    /** 进程恢复仅重排未完成工作；不复用旧安装的资料 ID，不重复发布已完成块。 */
    private suspend fun restore() {
        charts.state.first{!it.loading}
        for(dataset in charts.state.value.datasets.filter{it.offlineReadable}) {
            currentCoroutineContext().ensureActive()
            try{withSource(ChartTerrainRequest(dataset.id,dataset.revision,ChartBounds(0.0,0.0,1.0,1.0))){source->
                if(!File(source.directory,FILE_NAME).isFile)return@withSource
                database(source.directory).use{db->
                    val jobs=ArrayList<Work>();val stale=ArrayList<String>()
                    db.rawQuery("SELECT key,source_key,request FROM jobs WHERE phase IN ('QUEUED','PREPARING') ORDER BY updated_at LIMIT 257",null).use{rows->
                        while(rows.moveToNext()) {
                            val request=gson.fromJson(rows.getString(2),ChartTerrainRequest::class.java).copy(datasetId=dataset.id,revision=dataset.revision)
                            if(rows.getString(1)==source.sourceKey&&rows.getString(0)==key(source.sourceKey,request)) {
                                validate(request);jobs+=Work(rows.getString(0),source.sourceKey,request)
                            }else stale+=rows.getString(0)
                        }
                    }
                    db.beginTransaction()
                    try {
                        for(id in stale)db.execSQL("UPDATE jobs SET phase='STALE',reason='CHART_SOURCE_CHANGED' WHERE key=?",arrayOf(id))
                        for(work in jobs)db.execSQL("UPDATE jobs SET phase='QUEUED',request=?,reason=NULL WHERE key=?",arrayOf(gson.toJson(work.request),work.key))
                        db.setTransactionSuccessful()
                    }finally{db.endTransaction()}
                    withContext(NonCancellable){mutex.withLock{jobs.forEach(::enqueueLocked)};wake.trySend(Unit)}
                    progress(dataset.id,source.sourceKey,db)
                }
            }}catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){android.util.Log.w("YokuliTerrain","Restore deferred: ${failure.message}")}
        }
    }

    suspend fun prepare(request:ChartTerrainRequest):ChartTerrainStatus=prepareRegion(listOf(request)).single()

    /** 整个区域先校验，再单事务接受。离页取消 IPC 等待也不会留下只接受半个区域的状态。 */
    suspend fun prepareRegion(requests:List<ChartTerrainRequest>):List<ChartTerrainStatus> {
        require(requests.size in 1..64){"CHART_TERRAIN_REGION_LIMIT"}
        requests.forEach(::validate)
        val first=requests.first()
        require(requests.all{it.datasetId==first.datasetId&&it.revision==first.revision}&&requests.distinct().size==requests.size){"CHART_TERRAIN_REQUEST_INVALID"}
        return withSource(first){source->
            mutex.withLock {
                database(source.directory).use {db->
                    require(pending.size+requests.count{"${it.datasetId}:${key(source.sourceKey,it)}" !in pending}<=256){"CHART_TERRAIN_QUEUE_FULL"}
                    val accepted=ArrayList<Work>()
                    val results=ArrayList<ChartTerrainStatus>()
                    currentCoroutineContext().ensureActive();VirtualHostServices.beforeWrite()
                    db.beginTransaction()
                    try {
                        for(request in requests) {
                            val key=key(source.sourceKey,request)
                            if(hasProduct(db,key))results+=status(request,key,source.sourceKey,ChartTerrainPhase.READY)
                            else {
                                val existing=job(db,key,request,source.sourceKey)
                                if(existing?.phase in setOf(ChartTerrainPhase.QUEUED,ChartTerrainPhase.PREPARING,ChartTerrainPhase.SUBDIVIDED))results+=requireNotNull(existing)
                                else {
                                    db.execSQL("INSERT OR REPLACE INTO jobs(key,source_key,request,phase,reason,updated_at) VALUES (?,?,?,'QUEUED',NULL,?)",arrayOf(key,source.sourceKey,gson.toJson(request),System.currentTimeMillis()))
                                    results+=status(request,key,source.sourceKey,ChartTerrainPhase.QUEUED)
                                }
                                if(existing?.phase!=ChartTerrainPhase.SUBDIVIDED)accepted+=Work(key,source.sourceKey,request)
                            }
                        }
                        db.setTransactionSuccessful()
                    }finally{db.endTransaction()}
                    // 不再挂起：接受的事务和内存队列之间不会因页面取消而丢任务。
                    accepted.forEach(::enqueueLocked);wake.trySend(Unit);progress(first.datasetId,source.sourceKey,db)
                    results
                }
            }
        }
    }

    suspend fun cancel(datasetId:String) {
        val dataset=charts.state.value.datasets.firstOrNull{it.id==datasetId}?:return
        var task:Deferred<Unit>?=null
        withSource(ChartTerrainRequest(datasetId,dataset.revision,ChartBounds(0.0,0.0,1.0,1.0))){source->
            mutex.withLock {
                pending.entries.removeAll{it.value.request.datasetId==datasetId}
                if(running?.request?.datasetId==datasetId){task=runningJob;task?.cancel()}
                database(source.directory).use{db->
                    db.execSQL("UPDATE jobs SET phase='CANCELLED',reason=NULL WHERE phase IN ('QUEUED','PREPARING') AND source_key=?",arrayOf(source.sourceKey))
                    progress(datasetId,source.sourceKey,db)
                }
            }
        }
        task?.join()
    }

    suspend fun status(request:ChartTerrainRequest):ChartTerrainStatus=withSource(request){source->
        val key=key(source.sourceKey,request)
        if(!File(source.directory,FILE_NAME).isFile)return@withSource status(request,key,source.sourceKey,ChartTerrainPhase.STALE,"CHART_TERRAIN_NOT_PREPARED")
        database(source.directory).use{db->
            if(hasProduct(db,key))status(request,key,source.sourceKey,ChartTerrainPhase.READY)
            else job(db,key,request,source.sourceKey)?:status(request,key,source.sourceKey,ChartTerrainPhase.STALE,"CHART_TERRAIN_NOT_PREPARED")
        }
    }

    suspend fun read(request:ChartTerrainRequest):ChartProductBlock=withSource(request){source->
        val key=key(source.sourceKey,request)
        require(File(source.directory,FILE_NAME).isFile){"CHART_TERRAIN_NOT_PREPARED"}
        database(source.directory).use {db->
            try {
                val product=readTerrainProduct(db,key)
                require(product.sourceKey==source.sourceKey){"CHART_TERRAIN_PRODUCT_IDENTITY"}
                ChartProductBlock(key,product.schema,product.bytes,product.sha256)
            }catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){
                db.execSQL("DELETE FROM products WHERE key=?",arrayOf(key))
                db.execSQL("UPDATE jobs SET phase='FAILED',reason='CHART_TERRAIN_PRODUCT_CORRUPT' WHERE key=?",arrayOf(key))
                progress(request.datasetId,source.sourceKey,db);throw failure
            }
        }
    }

    /** 单事务封存 READY 产品，不导出作业/会话，调用者持有事实版本租约。 */
    suspend fun exportPrepared(snapshotId:String,targetFile:File):Boolean=withContext(Dispatchers.IO) {
        val directory=resolveDirectory(snapshotId);val source=File(directory,FILE_NAME)
        if(!source.isFile)return@withContext false
        require(!targetFile.exists()){"CHART_TERRAIN_EXPORT_EXISTS"}
        var complete=false
        try {
            SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {input->
                validateTerrainSchema(input)
                if(input.rawQuery("SELECT 1 FROM products LIMIT 1",null).use{!it.moveToFirst()})return@withContext false
                SQLiteDatabase.openOrCreateDatabase(targetFile,null).use {output->
                    createTerrainProducts(output);output.execSQL("ATTACH DATABASE ? AS prepared",arrayOf(source.path))
                    output.beginTransactionNonExclusive()
                    try {
                        // 一个 SQLite 读快照包含全部完成块；新发布块留待下次导出。
                        output.execSQL("INSERT INTO products SELECT * FROM prepared.products")
                        currentCoroutineContext().ensureActive();VirtualHostServices.beforeWrite()
                        output.setTransactionSuccessful()
                    }finally{try{output.endTransaction()}finally{output.execSQL("DETACH DATABASE prepared")}}
                }
            }
            FileOutputStream(targetFile,true).use{it.fd.sync()};complete=true;true
        }finally{if(!complete){targetFile.delete();File(targetFile.path+"-journal").delete()}}
    }

    private fun enqueueLocked(work:Work) {if(running?.queueKey!=work.queueKey&&work.queueKey !in pending)pending[work.queueKey]=work}
    private suspend fun compile(work:Work) {
        try {
            val latest=charts.state.value.datasets.firstOrNull{it.id==work.request.datasetId}?:return
            val request=work.request.copy(revision=latest.revision)
            withSource(request){source->
                require(source.sourceKey==work.sourceKey){"CHART_SOURCE_CHANGED"}
                database(source.directory).use{db->
                    if(hasProduct(db,work.key))return@withSource
                    db.execSQL("UPDATE jobs SET phase='PREPARING',reason=NULL WHERE key=? AND phase='QUEUED'",arrayOf(work.key))
                    progress(request.datasetId,source.sourceKey,db)
                }
                val tile=withTimeout(120_000){withContext(Dispatchers.Default){compiler.compile(source.snapshot,work.key,source.sourceKey,request.bounds,request.lod)}}
                currentCoroutineContext().ensureActive()
                val bytes=ChartTerrainBlockCodec.encode(tile);val hash=ChartTerrainBlockCodec.hash(bytes)
                require(source.directory.usableSpace>bytes.size.toLong()*2+32*1024*1024){"CHART_STORAGE_FULL"}
                VirtualHostServices.beforeWrite()
                // 取消和发布共享锁：停止之后不会又冒出“正在准备”，已完成的块仍保留。
                mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    database(source.directory).use{db->
                        db.beginTransaction()
                        try {
                            db.execSQL("INSERT OR REPLACE INTO products(key,source_key,schema,sha256,payload,created_at) VALUES (?,?,?,?,?,?)",arrayOf(work.key,source.sourceKey,ChartTerrainBlockCodec.SCHEMA,hash,bytes,System.currentTimeMillis()))
                            db.execSQL("UPDATE jobs SET phase='READY',reason=NULL,updated_at=? WHERE key=?",arrayOf(System.currentTimeMillis(),work.key));db.setTransactionSuccessful()
                        }finally{db.endTransaction()}
                        progress(request.datasetId,source.sourceKey,db)
                    }
                }
            }
        }catch(cancel:CancellationException){if(cancel is TimeoutCancellationException)failure(work,"CHART_TERRAIN_PREPARATION_TIMEOUT")else throw cancel}
        catch(failure:Exception){failure(work,failure.message?:"CHART_TERRAIN_PREPARATION_FAILED")}
    }
    private suspend fun failure(work:Work,reason:String) {
        try {
            val dataset=charts.state.value.datasets.firstOrNull{it.id==work.request.datasetId}?:return
            val request=work.request.copy(revision=dataset.revision)
            val size=minOf(request.bounds.north-request.bounds.south,request.bounds.east-request.bounds.west)*111_320
            var subdivide=reason in setOf("CHART_TERRAIN_SUBDIVIDE_REQUIRED","CHART_TERRAIN_MODEL_LIMIT")&&size>400
            var outcome=reason
            // 细分也是 Core 准备工作；队列/存储错误必须落 FAILED，不能遗留永久 PREPARING。
            if(subdivide)try{prepareRegion(request.splitTerrainRequest())}
                catch(cancel:CancellationException){throw cancel}
                catch(failure:Exception){subdivide=false;outcome=failure.message?:"CHART_TERRAIN_PREPARATION_FAILED"}
            withSource(request){source->database(source.directory).use{db->
                db.execSQL("UPDATE jobs SET phase=?,reason=?,updated_at=? WHERE key=? AND phase!='CANCELLED'",arrayOf(if(subdivide)"SUBDIVIDED" else "FAILED",outcome.take(512),System.currentTimeMillis(),work.key))
                progress(dataset.id,source.sourceKey,db)
            }}
        }catch(cancel:CancellationException){throw cancel}
        catch(failure:Exception){android.util.Log.w("YokuliTerrain","Preparation failed: $reason",failure)}
    }
    private suspend fun <T> withSource(request:ChartTerrainRequest,block:suspend (Source)->T):T=withContext(Dispatchers.IO) {
        validate(request)
        val snapshot=charts.acquireSnapshot(listOf(request.datasetId))
        try {
            val dataset=snapshot.datasets.singleOrNull()
            require(dataset?.id==request.datasetId&&dataset.revision==request.revision&&dataset.offlineReadable){"CHART_SOURCE_CHANGED"}
            val identity=resolveSourceIdentity(snapshot.id)
            require(identity.isNotBlank()){"CHART_TERRAIN_FACTS_NOT_READY"}
            val sourceKey=ChartTerrainBlockCodec.hash("terrain-5:$identity".toByteArray(Charsets.UTF_8))
            block(Source(snapshot,resolveDirectory(snapshot.id),sourceKey))
        }finally{withContext(NonCancellable){charts.releaseSnapshot(snapshot.id)}}
    }
    private fun validate(request:ChartTerrainRequest) {
        require(request.datasetId.isNotBlank()&&request.revision>=0&&request.lod in 0..1&&request.bounds.valid&&request.bounds.west<request.bounds.east&&request.bounds.south<request.bounds.north&&request.bounds.north-request.bounds.south<=20&&request.bounds.east-request.bounds.west<=180){"CHART_TERRAIN_REQUEST_INVALID"}
    }
    private fun database(directory:File):SQLiteDatabase {
        require(directory.isDirectory){"CHART_SOURCE_CHANGED"}
        val file=File(directory,FILE_NAME);val existed=file.exists()
        return SQLiteDatabase.openOrCreateDatabase(file,null).also{db->
            try {
                synchronized(validated){
                    if(file.path !in validated){
                        if(existed)validateTerrainSchema(db)else createTerrainProducts(db)
                        createTerrainJobs(db);validated+=file.path
                    }
                }
            }catch(failure:Exception){db.close();throw failure}
        }
    }
    private fun progress(datasetId:String,sourceKey:String,db:SQLiteDatabase) {
        val counts=mutableMapOf<String,Int>()
        db.rawQuery("SELECT phase,count(*) FROM jobs WHERE source_key=? GROUP BY phase",arrayOf(sourceKey)).use{rows->while(rows.moveToNext())counts[rows.getString(0)]=rows.getInt(1)}
        val ready=db.rawQuery("SELECT count(*) FROM products WHERE source_key=?",arrayOf(sourceKey)).use{it.moveToFirst();it.getInt(0)}
        val value=ChartTerrainProgress(datasetId,counts["QUEUED"]?:0,counts["PREPARING"]?:0,ready,counts["FAILED"]?:0)
        mutableState.update{old->(old.filter{it.datasetId!=datasetId}+value).filter{entry->charts.state.value.datasets.any{it.id==entry.datasetId}}}
    }
    private fun hasProduct(db:SQLiteDatabase,key:String)=db.rawQuery("SELECT 1 FROM products WHERE key=?",arrayOf(key)).use{it.moveToFirst()}
    private fun job(db:SQLiteDatabase,key:String,request:ChartTerrainRequest,sourceKey:String):ChartTerrainStatus?=db.rawQuery("SELECT phase,reason FROM jobs WHERE key=?",arrayOf(key)).use{rows->
        if(!rows.moveToFirst())null else status(request,key,sourceKey,ChartTerrainPhase.valueOf(rows.getString(0)),rows.getString(1))
    }
    private fun status(request:ChartTerrainRequest,key:String,sourceKey:String,phase:ChartTerrainPhase,reason:String?=null)=ChartTerrainStatus(key,sourceKey,phase,request.bounds,request.lod,reason)
    private fun key(source:String,request:ChartTerrainRequest)=ChartTerrainBlockCodec.hash("$source:${request.bounds}:${request.lod}".toByteArray(Charsets.UTF_8))
    companion object {const val FILE_NAME="terrain-products.sqlite"}
}

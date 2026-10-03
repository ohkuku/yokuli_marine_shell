package com.yokuli.runtime.marine.chart.terrain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
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
import kotlin.math.floor
import kotlin.math.pow

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
        validateRegion(requests)
        val first=requests.first()
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

    suspend fun status(request:ChartTerrainRequest):ChartTerrainStatus=statuses(listOf(request)).single()

    /** 一帧所需区域只核验一次来源，两个有界 SQL 返回全部状态；绝不在轮询中触发建模。 */
    suspend fun statuses(requests:List<ChartTerrainRequest>):List<ChartTerrainStatus> {
        validateRegion(requests)
        return withSource(requests.first()){source->
            val keys=requests.map{key(source.sourceKey,it)}
            if(!File(source.directory,FILE_NAME).isFile)return@withSource requests.mapIndexed{index,request->
                status(request,keys[index],source.sourceKey,ChartTerrainPhase.STALE,"CHART_TERRAIN_NOT_PREPARED")
            }
            withReadSignal {signal->database(source.directory).use{db->
                val placeholders=keys.joinToString(","){"?"}
                val args=(listOf(source.sourceKey)+keys).toTypedArray()
                val ready=HashSet<String>()
                val jobs=HashMap<String,Pair<ChartTerrainPhase,String?>>()
                currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                db.rawQuery("SELECT key FROM products WHERE source_key=? AND key IN ($placeholders)",args,signal).use{rows->
                    while(rows.moveToNext())ready+=rows.getString(0)
                }
                db.rawQuery("SELECT key,phase,reason FROM jobs WHERE source_key=? AND key IN ($placeholders)",args,signal).use{rows->
                    while(rows.moveToNext())jobs[rows.getString(0)]=ChartTerrainPhase.valueOf(rows.getString(1)) to rows.getString(2)
                }
                currentCoroutineContext().ensureActive()
                requests.mapIndexed{index,request->
                    val key=keys[index];val job=jobs[key]
                    when {
                        key in ready->status(request,key,source.sourceKey,ChartTerrainPhase.READY)
                        job!=null->status(request,key,source.sourceKey,job.first,job.second)
                        else->status(request,key,source.sourceKey,ChartTerrainPhase.STALE,"CHART_TERRAIN_NOT_PREPARED")
                    }
                }
            }}
        }
    }

    /**
     * 首帧读取全国包已有的基础层，而不是要求每种相机范围都预制一遍。
     * 基础层沿 -180/-90 原点的相同网格逐级二分；这里只查已有键，不建模、不改变事实。
     * 预制包的复杂父块可以只有 READY 子块，既不伪造父块，也不要求携带运行时 jobs。
     */
    suspend fun overview(requests:List<ChartTerrainRequest>):List<ChartTerrainStatus> {
        validateRegion(requests)
        return withSource(requests.first()){source->
            if(!File(source.directory,FILE_NAME).isFile)return@withSource emptyList()
            val candidates=linkedMapOf<String,ChartTerrainRequest>()
            val paths=requests.map {request->overviewAncestors(request).map {ancestor->
                key(source.sourceKey,ancestor).also{candidates[it]=ancestor}
            }}
            if(candidates.isEmpty())return@withSource emptyList()
            // 最多64个请求，各9级；IN参数低于系统SQLite的保守999项限制。
            require(candidates.size<=576){"CHART_TERRAIN_REGION_LIMIT"}
            withReadSignal {signal->database(source.directory).use{db->
                suspend fun readReady(keys:Collection<String>):Set<String> {
                    if(keys.isEmpty())return emptySet()
                    require(keys.size<=576){"CHART_TERRAIN_REGION_LIMIT"}
                    currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
                    val ready=HashSet<String>()
                    val placeholders=keys.joinToString(","){"?"}
                    db.rawQuery("SELECT key FROM products WHERE source_key=? AND key IN ($placeholders)",
                        (listOf(source.sourceKey)+keys).toTypedArray(),signal).use{rows->
                        while(rows.moveToNext())ready+=rows.getString(0)
                    }
                    currentCoroutineContext().ensureActive()
                    return ready
                }
                val ready=readReady(candidates.keys)
                val selected=paths.mapNotNull{path->path.firstOrNull{it in ready}}.distinct()
                    .sortedByDescending {id->candidates.getValue(id).bounds.let{(it.east-it.west)*(it.north-it.south)}}
                val result=ArrayList<ChartTerrainStatus>()
                fun append(id:String,request:ChartTerrainRequest) {
                    // 父子块不能共面叠画；只返回实际 READY 的范围，不以子块冒充整个父块。
                    if(result.any{outer->containsBounds(outer.bounds,request.bounds)})return
                    result.removeAll{inner->containsBounds(request.bounds,inner.bounds)}
                    if(result.size<24)result+=status(request,id,source.sourceKey,ChartTerrainPhase.READY)
                }
                for(id in selected) {
                    currentCoroutineContext().ensureActive()
                    append(id,candidates.getValue(id))
                    if(result.size==24)break
                }
                // 已有祖先的普通近距首帧到这里结束，不增加 SQL 或生成几何。
                var frontier=requests.map{it.copy(lod=0)}.distinct().filter {request->
                    result.none{outer->containsBounds(outer.bounds,request.bounds)}
                }
                val focus=requests.first().bounds
                val focusX=(focus.west+focus.east)/2;val focusY=(focus.south+focus.north)/2
                val longitudeScale=kotlin.math.cos(Math.toRadians(focusY)).coerceAtLeast(.003)
                fun proximity(request:ChartTerrainRequest):Double {
                    val bounds=request.bounds
                    // 输入按观察中心排序，超大请求只读最近的有界子树；越限部分仍为未知。
                    val dx=(((bounds.west+bounds.east)/2-focusX+540.0)%360.0-180.0)*longitudeScale
                    val dy=(bounds.south+bounds.north)/2-focusY
                    return dx*dx+dy*dy
                }
                for(depth in 1..8) {
                    if(frontier.isEmpty()||result.size==24)break
                    currentCoroutineContext().ensureActive()
                    val children=frontier.asSequence().filter {request->
                        minOf(request.bounds.north-request.bounds.south,request.bounds.east-request.bounds.west)*111_320>400
                    }.flatMap{it.splitTerrainRequest().asSequence()}.distinct()
                        .filter{request->result.none{outer->containsBounds(outer.bounds,request.bounds)}}
                        .sortedBy(::proximity).take(576).toList()
                    val childRequests=children.associateByTo(linkedMapOf()){key(source.sourceKey,it)}
                    val childReady=readReady(childRequests.keys)
                    val next=ArrayList<ChartTerrainRequest>()
                    for((id,request) in childRequests) {
                        if(id in childReady)append(id,request)else next+=request
                        if(result.size==24)break
                    }
                    frontier=next
                }
                result
            }}
        }
    }

    private fun overviewAncestors(request:ChartTerrainRequest):List<ChartTerrainRequest> {
        val bounds=request.bounds
        val initialWidth=bounds.east-bounds.west;val initialHeight=bounds.north-bounds.south
        return (0..8).mapNotNull{level->
            val width=initialWidth*2.0.pow(level);val height=initialHeight*2.0.pow(level)
            if(width>180.0||height>20.0)return@mapNotNull null
            val west=-180.0+floor((bounds.west+180.0+1e-10)/width)*width
            val south=-90.0+floor((bounds.south+90.0+1e-10)/height)*height
            val ancestor=ChartBounds(west.coerceAtLeast(-180.0),south.coerceAtLeast(-90.0),
                (west+width).coerceAtMost(180.0),(south+height).coerceAtMost(90.0))
            if(!ancestor.valid||!containsBounds(ancestor,bounds))null else request.copy(bounds=ancestor,lod=0)
        }.distinct()
    }
    private fun containsBounds(outer:ChartBounds,inner:ChartBounds)=
        outer.west<=inner.west+1e-9&&outer.east>=inner.east-1e-9&&outer.south<=inner.south+1e-9&&outer.north>=inner.north-1e-9

    suspend fun read(request:ChartTerrainRequest):ChartProductBlock=withSource(request){source->
        val key=key(source.sourceKey,request)
        require(File(source.directory,FILE_NAME).isFile){"CHART_TERRAIN_NOT_PREPARED"}
        withReadSignal {signal->database(source.directory).use {db->
            try {
                val product=readTerrainProduct(db,key,signal)
                require(product.sourceKey==source.sourceKey){"CHART_TERRAIN_PRODUCT_IDENTITY"}
                ChartProductBlock(key,product.schema,product.bytes,product.sha256)
            }catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){
                // SQLite 的 OperationCanceledException 不是协程 CancellationException；取消、
                // 权限/存储瞬时错误都不能把已准备的完整块删成“损坏”。
                currentCoroutineContext().ensureActive()
                if(failure.message !in setOf("CHART_TERRAIN_PRODUCT_CORRUPT","CHART_TERRAIN_PRODUCT_INVALID","CHART_TERRAIN_PRODUCT_IDENTITY"))throw failure
                db.execSQL("DELETE FROM products WHERE key=?",arrayOf(key))
                db.execSQL("UPDATE jobs SET phase='FAILED',reason='CHART_TERRAIN_PRODUCT_CORRUPT' WHERE key=?",arrayOf(key))
                progress(request.datasetId,source.sourceKey,db);throw failure
            }
        }}
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
                            db.execSQL("INSERT OR REPLACE INTO products(key,source_key,schema,sha256,payload,created_at) VALUES (?,?,?,?,?,?)",arrayOf<Any>(work.key,source.sourceKey,ChartTerrainBlockCodec.SCHEMA,hash,bytes,System.currentTimeMillis()))
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
                db.execSQL("UPDATE jobs SET phase=?,reason=?,updated_at=? WHERE key=? AND phase!='CANCELLED'",arrayOf<Any>(if(subdivide)"SUBDIVIDED" else "FAILED",outcome.take(512),System.currentTimeMillis(),work.key))
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
            val sourceKey=ChartTerrainBlockCodec.hash("${CHART_TERRAIN_PRODUCT_RULES}:$identity".toByteArray(Charsets.UTF_8))
            block(Source(snapshot,resolveDirectory(snapshot.id),sourceKey))
        }finally{withContext(NonCancellable){charts.releaseSnapshot(snapshot.id)}}
    }
    /** Binder 只读请求取消时，中断 SQLite 等待并完整释放来源租约。 */
    private suspend fun <T> withReadSignal(block:suspend (CancellationSignal)->T):T=coroutineScope {
        val signal=CancellationSignal()
        val watcher=launch(Dispatchers.Default,start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{signal.cancel()}}
        try{block(signal)}finally{withContext(NonCancellable){watcher.cancelAndJoin()}}
    }
    private fun validateRegion(requests:List<ChartTerrainRequest>) {
        require(requests.size in 1..64){"CHART_TERRAIN_REGION_LIMIT"}
        requests.forEach(::validate)
        val first=requests.first()
        require(requests.all{it.datasetId==first.datasetId&&it.revision==first.revision}&&requests.distinct().size==requests.size){"CHART_TERRAIN_REQUEST_INVALID"}
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

package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.hardware.VirtualHostServices
import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.marine.chart.LocalChartDataService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import org.locationtech.jts.geom.Coordinate
import kotlin.math.*

/** 进程级离线作业所有者。界面只提交命令、订阅结果；关闭海图不终止计算。 */
@Singleton
class LocalPassagePlanningService @Inject constructor(@ApplicationContext context:Context,private val charts:LocalChartDataService):RouteAnalysisService,RoutePlanningService {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val file=AtomicFile(File(context.filesDir,"passage-workspace-v1.json"))
    private data class Pending(val request:PassageRequest,val leg:Int?,val planning:Boolean)
    private data class Document(val schema:Int=1,val state:PassageState=PassageState(),val completed:List<String> = emptyList())
    private val gson=Gson();private val writes=Mutex();private val commands=Mutex()
    private val mutable=MutableStateFlow(PassageState());override val state=mutable.asStateFlow()
    private var saved=Document();private var readBlocked=false
    private var running:Job?=null
    private val geometry=PassageGeometry(charts)
    private val ready=CompletableDeferred<Unit>()
    private val working=setOf(PassageJobPhase.LOADING,PassageJobPhase.ANALYZING,PassageJobPhase.SEARCHING)
    init {scope.launch{try{restore()}finally{ready.complete(Unit)}}}
    private suspend fun restore()=writes.withLock {
        try {
            val loaded=withContext(Dispatchers.IO){
                VirtualHostServices.beforeRead()
                if(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists())Document()
                else file.openRead().bufferedReader().use {reader->
                    val json=com.google.gson.JsonParser.parseReader(reader).asJsonObject
                    if(json.has("schema"))gson.fromJson(json,Document::class.java) else Document(state=gson.fromJson(json,PassageState::class.java))
                }
            }
            // Gson不会为旧文件缺失的非空集合执行Kotlin默认参数。
            var stored=loaded.copy(completed=loaded.completed.orEmpty(),state=loaded.state.copy(
                avoidances=loaded.state.avoidances.orEmpty(),reviews=loaded.state.reviews.orEmpty()))
            require(stored.schema==1&&stored.completed.size<=32&&stored.state.avoidances.size<=100&&stored.state.reviews.size<=128){"Unsupported passage workspace"}
            stored.state.avoidances.forEach(::validAvoidance)
            val analyses=listOfNotNull(stored.state.analysis,stored.state.plan?.original)+stored.state.plan?.candidates.orEmpty().map{it.analysis}
            if(analyses.any{it.rulesVersion != PASSAGE_RULES_VERSION}){
                // 保留用户航线/参数/避让区及批注，只失效旧计算；不得拿旧无冲突结论覆盖新数据规则。
                val job=stored.state.job?:analyses.firstOrNull()?.let{PassageJob(it.id,PassageJobPhase.INTERRUPTED)}
                stored=stored.copy(completed=emptyList(),state=stored.state.copy(analysis=null,plan=null,planningReadiness=null,
                    job=job?.copy(phase=PassageJobPhase.INTERRUPTED,detail="离线规划规则已更新，请重新计算 / Offline planning rules changed; calculate again")))
            }
            saved=stored
            readBlocked=false
            mutable.value=stored.state.copy(ready=true,storageIssue=null,job=stored.state.job?.let {
                if(it.phase in working)it.copy(phase=PassageJobPhase.INTERRUPTED,detail="计算已中断，请重新计算 / Calculation interrupted; calculate again")else it
            })
        }catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){readBlocked=true;mutable.update{it.copy(ready=false,storageIssue=error.message?:"PASSAGE_READ_FAILED")}}
    }
    override fun retryRestore(){scope.launch{ready.await();commands.withLock{if(readBlocked&&running?.isActive!=true)restore()}}}
    /** 在同一落盘事务内合并最新避让区与计算结果；成功状态只能在finishWrite之后发布。 */
    private suspend fun commit(pending:Pending?=null,completedId:String?=null,
        transform:(PassageState)->PassageState):Boolean=writes.withLock {
        if(readBlocked){mutable.update{it.copy(storageIssue="PASSAGE_READ_BLOCKED")};return@withLock false}
        currentCoroutineContext().ensureActive()
        val base=mutable.value
        val transformed=transform(base).copy(ready=true,storageIssue=null)
        val next=if(pending!=null)transformed.copy(pendingRequest=pending.request,planning=pending.planning,detourLeg=pending.leg)else transformed
        val doc=saved.copy(state=next,
            completed=if(completedId==null)saved.completed else (saved.completed+completedId).distinct().takeLast(32))
        try {
            // 已开始的原子提交不可被页面取消截断；cancel/new job等待同一写入锁。
            withContext(NonCancellable+Dispatchers.IO){
                VirtualHostServices.beforeWrite()
                val bytes=gson.toJson(doc).toByteArray(Charsets.UTF_8)
                val output=file.startWrite()
                try {output.write(bytes);output.fd.sync();VirtualHostServices.beforeWrite();file.finishWrite(output)}catch(error:Throwable){file.failWrite(output);throw error}
                // finishWrite部分IO失败只写日志；最终内容未确认时不能显示已保存或继续覆盖。
                check(file.openRead().use{it.readBytes()}.contentEquals(bytes)){"PASSAGE_WRITE_NOT_CONFIRMED"}
                saved=doc
                mutable.update {current->if(next.job==base.job&&current.job?.requestId==base.job?.requestId)next.copy(job=current.job)else next}
            }
            true
        }catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){readBlocked=true;mutable.update{it.copy(ready=false,storageIssue=error.message?:"PASSAGE_WRITE_FAILED")};false}
    }
    private fun validAvoidance(value:PassageAvoidance) {
        require(value.id.isNotBlank()&&value.boundary.size in 3..500)
        require(value.boundary.all{it.latitude.isFinite()&&it.longitude.isFinite()&&it.latitude in -89.9..89.9&&it.longitude in -180.0..180.0})
        PassageProjection(value.boundary.first()).geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(value.boundary))))
    }
    override suspend fun saveAvoidance(value:PassageAvoidance){ready.await();validAvoidance(value);commands.withLock{
        check(commit{it.copy(avoidances=(it.avoidances.filterNot{a->a.id==value.id}+value.copy(name=value.name.trim().take(100))).takeLast(100))}){"Unable to save avoidance"}
    }}
    override suspend fun removeAvoidance(id:String){ready.await();commands.withLock{check(commit{it.copy(avoidances=it.avoidances.filterNot{a->a.id==id})}){"Unable to remove avoidance"}}}
    override suspend fun saveReview(analysisKey:String,issueId:String,note:String) {
        ready.await()
        require(analysisKey.isNotBlank()&&issueId.isNotBlank()&&note.length<=2000){"Invalid review"}
        commands.withLock {
            check(commit { current ->
                val analyses=listOfNotNull(current.analysis,current.plan?.original)+current.plan?.candidates.orEmpty().map{it.analysis}
                require(analyses.any{it.key==analysisKey&&it.issues.any{issue->issue.id==issueId}}){"Analysis changed; reopen this issue"}
                val retained=current.reviews.filterNot{it.analysisKey==analysisKey&&it.issueId==issueId}
                current.copy(reviews=if(note.isBlank())retained else (retained+PassageReview(analysisKey,issueId,note.trim(),System.currentTimeMillis())).takeLast(128))
            }) {"Unable to save review"}
        }
    }
    override fun analyze(request:PassageRequest)=submit(request,null,false)
    override fun plan(request:PassageRequest,detourLeg:Int?)=submit(request,detourLeg,true)
    private fun submit(request:PassageRequest,leg:Int?,planning:Boolean){scope.launch{ready.await();commands.withLock{
        if(request.requestId.isBlank()||request.requestId.length>128){mutable.update{it.copy(storageIssue="Invalid request identifier")};return@withLock}
        if(readBlocked)return@withLock
        if(request.requestId in saved.completed||running?.isActive==true&&mutable.value.job?.requestId==request.requestId)return@withLock
        running?.cancelAndJoin()
        val frozen=request.copy(route=request.route.copy(points=request.route.points.toList(),navigationTargetIndices=request.route.navigationTargetIndices?.toList()),datasetIds=request.datasetIds.toList(),avoidances=request.avoidances.map{it.copy(boundary=it.boundary.toList())})
        if(!commit(Pending(frozen,leg,planning)){it.copy(job=PassageJob(request.requestId,PassageJobPhase.LOADING),plan=null,planningReadiness=null)})return@withLock
        running=scope.launch{run(frozen,leg,planning)}
    }}}
    override fun cancel(requestId:String){scope.launch{ready.await();commands.withLock{
        if(mutable.value.job?.let{it.requestId==requestId&&it.phase in working}==true){
            running?.cancelAndJoin()
            if(!commit{if(it.job?.requestId==requestId&&it.job?.phase in working)it.copy(job=PassageJob(requestId,PassageJobPhase.CANCELLED))else it})
                mutable.update{it.copy(job=PassageJob(requestId,PassageJobPhase.FAILED,detail="计算已停止，取消状态尚未保存 / Stopped; cancellation was not saved"))}
        }
    }}}
    private fun progress(id:String,phase:PassageJobPhase,p:Float){mutable.update{if(it.job?.requestId==id&&it.job?.phase in working)it.copy(job=PassageJob(id,phase,p.coerceIn(0f,1f)))else it}}
    private suspend fun run(request:PassageRequest,leg:Int?,planning:Boolean){
        var snapshot:ChartDataSnapshot?=null
        try{
            geometry.validateRequest(request)
            require(leg==null||leg in 0 until request.route.points.lastIndex){"Choose an existing leg"}
            snapshot=charts.acquireSnapshot(request.datasetIds.distinct())
            // 自动规划优先“先出粗航线”：只做轻量资料门槛，然后直接搜索。
            // 完整深度/净空/限制证据检查保留给“检查当前航线”，不再在出线前后重复扫同一区域。
            val readiness=if(planning)planningReadiness(snapshot,request,leg)else null
            val original=when {
                planning&&readiness?.canSearch==true->planningPreviewAnalysis(snapshot,request)
                planning->readinessAnalysis(snapshot,request,requireNotNull(readiness))
                else->geometry.analyze(snapshot,request){progress(request.requestId,PassageJobPhase.ANALYZING,it)}
            }
            val plan=if(planning){
                when {
                    readiness?.canSearch!=true->PassagePlan(request.requestId,original,emptyList(),readiness?.message)
                    else->createPlan(snapshot,request,original,leg)
                }
            }else null
            currentCoroutineContext().ensureActive()
            val completed=commit(completedId=request.requestId){
                if(it.job?.requestId!=request.requestId)it else it.copy(job=PassageJob(request.requestId,PassageJobPhase.COMPLETE,1f),analysis=original,plan=plan,planningReadiness=readiness)
            }
            if(!completed)mutable.update{if(it.job?.requestId==request.requestId)it.copy(job=PassageJob(request.requestId,PassageJobPhase.FAILED,detail="计算结果尚未保存，请重试 / Result not saved; calculate again"))else it}
        }catch(e:CancellationException){throw e}catch(e:Exception){
            commit{if(it.job?.requestId==request.requestId)it.copy(job=PassageJob(request.requestId,PassageJobPhase.FAILED,detail=e.message?.take(250)?:"Unable to calculate"))else it}
        }finally{snapshot?.let{withContext(NonCancellable){runCatching{charts.releaseSnapshot(it.id)}}}}
    }
    /** 栅格规划先用轻量元数据确认端点落在网格范围；真实像元在搜索 world 中只读取一次。 */
    private fun rasterContains(grid:RasterBathymetryGrid,point:ChartPoint):Boolean {
        val south=grid.northEdge-grid.height*grid.pixelHeightDegrees
        if(point.latitude<south-1e-9||point.latitude>grid.northEdge+1e-9)return false
        val span=grid.width*grid.pixelWidthDegrees
        if(span>=360.0-1e-9)return true
        var delta=(point.longitude-grid.westEdge)%360.0
        if(delta<0)delta+=360.0
        return delta<=span+1e-9
    }

    private fun pureNumericRaster(snapshot:ChartDataSnapshot):Boolean =
        snapshot.datasets.isNotEmpty()&&snapshot.datasets.all{dataset->
            !dataset.rasters.isNullOrEmpty()&&dataset.cells.none{it.featureCount>0}
        }

    /**
     * 纯 GEBCO / 数值高程直接在原始像元上做有界 A*，不先把几万像元 polygonize 成 JTS 面。
     * 这是“先给大概航线”的快速通道；混合/矢量资料继续走完整几何 world。
     */
    private suspend fun searchNumericRaster(snapshot:ChartDataSnapshot,request:PassageRequest,start:ChartPoint,end:ChartPoint,
        padding:Double,onProgress:(Float)->Unit):List<ChartPoint>? {
        val projection=PassageProjection(start)
        val windows=charts.rasterWindows(snapshot.id,around(listOf(start,end),padding),maxCells=262_144)
        if(windows.isEmpty())return null
        val latitude=(start.latitude+end.latitude)/2.0
        val cellMeters=windows.minOf{item->
            val ew=item.grid.pixelWidthDegrees*111_320.0*cos(Math.toRadians(latitude)).coerceAtLeast(.15)
            val ns=item.grid.pixelHeightDegrees*110_540.0
            max(25.0,min(ew,ns))
        }
        val vessel=request.vessel
        val configuredMargin=(vessel.beamMeters?.takeIf{it.isFinite()&&it>0}?:0.0)/2
        val margin=max(25.0,configuredMargin)
        val required=vessel.draftMeters?.takeIf{it.isFinite()&&it>0}

        fun elevation(point:ChartPoint):Float? {
            for(item in windows) {
                val pixel=item.grid.pixelAt(point)?:continue
                val x=pixel.first-item.window.column;val y=pixel.second-item.window.row
                if(x in 0 until item.window.width&&y in 0 until item.window.height)return item.window.elevationAt(x,y)
            }
            return null
        }
        fun terrainWater(c:Coordinate):Boolean {
            val value=elevation(projection.point(c))?:return false
            return value.isFinite()&&value<0f
        }
        fun draftPreferred(c:Coordinate):Boolean {
            val need=required?:return true
            val value=elevation(projection.point(c))?:return false
            return value.isFinite()&&value<0f&&-value.toDouble()>=need
        }
        fun depthMultiplier(c:Coordinate):Double {
            val need=required?:return 1.0
            val value=elevation(projection.point(c))?:return 20.0
            if(!value.isFinite()||value>=0f)return 20.0
            val depth=-value.toDouble()
            if(depth>=need)return 1.0
            val deficit=((need-depth)/need.coerceAtLeast(.1)).coerceIn(0.0,1.0)
            return 4.0+12.0*deficit
        }

        val avoidance=union(request.avoidances.mapNotNull{a->
            runCatching{projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(a.boundary))))}.getOrNull()
        },projection.factory)
        val avoidanceMargin=if(avoidance.isEmpty)null else avoidance.buffer(margin)
        val diagonal=margin/sqrt(2.0)
        val offsets=arrayOf(
            0.0 to 0.0,margin to 0.0,-margin to 0.0,0.0 to margin,0.0 to -margin,
            diagonal to diagonal,diagonal to -diagonal,-diagonal to diagonal,-diagonal to -diagonal
        )
        fun outsideAvoidance(c:Coordinate):Boolean =
            avoidanceMargin==null||!avoidanceMargin.covers(projection.factory.createPoint(c))
        fun safe(c:Coordinate):Boolean {
            for((dx,dy) in offsets)if(!terrainWater(Coordinate(c.x+dx,c.y+dy)))return false
            return outsideAvoidance(c)
        }
        val coastPreferenceMeters=(cellMeters*1.5).coerceIn(350.0,900.0)
        fun coastOpen(c:Coordinate,radius:Double):Boolean {
            repeat(8){i->
                val angle=2*Math.PI*i/8
                if(!terrainWater(Coordinate(c.x+cos(angle)*radius,c.y+sin(angle)*radius)))return false
            }
            return true
        }
        fun coastMultiplier(c:Coordinate):Double=when {
            coastOpen(c,coastPreferenceMeters)->1.0
            coastOpen(c,coastPreferenceMeters*.65)->2.25
            coastOpen(c,coastPreferenceMeters*.35)->5.0
            else->9.0
        }
        val sampleStep=max(25.0,min(250.0,cellMeters*.5))
        fun clear(a:Coordinate,b:Coordinate,preferDraft:Boolean=false,preferCoast:Boolean=false):Boolean {
            if(avoidanceMargin!=null&&projection.factory.createLineString(arrayOf(a,b)).intersects(avoidanceMargin))return false
            val length=a.distance(b);val slices=max(1,ceil(length/sampleStep).toInt())
            for(i in 0..slices) {
                val t=i.toDouble()/slices
                val at=Coordinate(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
                if(!safe(at)||preferDraft&&!draftPreferred(at)||preferCoast&&!coastOpen(at,coastPreferenceMeters*.65))return false
            }
            return true
        }
        // A* 用较大的软岸距来“选哪边走”；折线简化不能再拿同一个岸距当硬门槛，
        // 否则开阔水域也会保留每个栅格拐点。这里只保留一个较小近岸底线。
        val simplifyCoastMeters=(coastPreferenceMeters*.28).coerceIn(100.0,220.0)
        fun simplifyClear(a:Coordinate,b:Coordinate):Boolean {
            if(avoidanceMargin!=null&&projection.factory.createLineString(arrayOf(a,b)).intersects(avoidanceMargin))return false
            val length=a.distance(b);val slices=max(1,ceil(length/sampleStep).toInt())
            for(i in 0..slices) {
                val t=i.toDouble()/slices
                val at=Coordinate(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
                // 中心线必须仍在水里；吃水继续保守核对。岸距这里只防止简化后贴回岸边。
                if(!terrainWater(at)||!outsideAvoidance(at)||required!=null&&!draftPreferred(at))return false
                if(!coastOpen(at,simplifyCoastMeters))return false
            }
            return true
        }
        /**
         * 用户明确选择的控制点只要求“点本身在水里”；不能因为 GEBCO 粗像元附近的 25 m
         * 安全采样碰到岸就否定这个点。自动生成的中间路径仍使用 safe() 与软岸距。
         */
        suspend fun nearestWater(origin:Coordinate):Coordinate? {
            if(terrainWater(origin)&&outsideAvoidance(origin))return origin
            val radial=max(50.0,min(250.0,cellMeters*.5))
            val maxRadius=(cellMeters*3.0).coerceIn(750.0,2_000.0)
            var radius=radial
            while(radius<=maxRadius+1e-6) {
                currentCoroutineContext().ensureActive()
                val samples=max(16,ceil(2*Math.PI*radius/radial).toInt()).coerceAtMost(96)
                var best:Coordinate?=null
                repeat(samples){i->
                    val angle=2*Math.PI*i/samples
                    val candidate=Coordinate(origin.x+cos(angle)*radius,origin.y+sin(angle)*radius)
                    if(terrainWater(candidate)&&outsideAvoidance(candidate)&&(best==null||candidate.distance(origin)<best!!.distance(origin)))best=candidate
                }
                if(best!=null)return best
                radius+=radial
            }
            return null
        }
        fun preserveEndpoints(coords:List<Coordinate>):List<ChartPoint> {
            val output=mutableListOf(start)
            val snappedStart=projection.point(coords.first())
            if(distance(start,snappedStart)>1.0)output+=snappedStart
            coords.drop(1).dropLast(1).mapTo(output,projection::point)
            val snappedEnd=projection.point(coords.last())
            if(distance(output.last(),snappedEnd)>1.0)output+=snappedEnd
            if(distance(output.last(),end)>1.0)output+=end else output[output.lastIndex]=end
            return output.fold(mutableListOf()){acc,p->if(acc.lastOrNull()?.let{distance(it,p)<.5}!=true)acc+=p;acc}
        }

        val rawA=projection.xy(start);val rawB=projection.xy(end)
        val a=nearestWater(rawA)?:return null
        val b=nearestWater(rawB)?:return null
        if(clear(a,b,preferDraft=true,preferCoast=true))return preserveEndpoints(listOf(a,b))

        /**
         * 单一 GEBCO 窗口直接在原始像元邻接图上搜索。搜索拓扑和真实导入数据完全对齐，
         * 避免长距离投影网格刚好跨过一条连续水路而误报“被陆地切断”。
         */
        suspend fun searchSingleRasterPixels():List<ChartPoint>? {
            val item=windows.singleOrNull()?:return null
            val window=item.window
            val width=window.width;val height=window.height
            if(width<=1||height<=1)return null
            fun localPixel(point:ChartPoint):Pair<Int,Int>? {
                val pixel=item.grid.pixelAt(point)?:return null
                val x=pixel.first-window.column;val y=pixel.second-window.row
                return if(x in 0 until width&&y in 0 until height)x to y else null
            }
            val startPixel=localPixel(start)?:return null
            val endPixel=localPixel(end)?:return null
            fun pixelId(x:Int,y:Int)=y*width+x
            fun pixelX(id:Int)=id%width
            fun pixelY(id:Int)=id/width
            fun pixelElevation(id:Int)=window.elevationAt(pixelX(id),pixelY(id))
            fun pixelPoint(id:Int)=item.grid.centre(window.column+pixelX(id),window.row+pixelY(id))
            val traversableCache=ByteArray(width*height)
            fun traversable(id:Int):Boolean {
                if(id !in traversableCache.indices)return false
                return when(traversableCache[id].toInt()) {
                    1->false
                    2->true
                    else->{
                        val value=pixelElevation(id)
                        val water=value!=null&&value<0f
                        val allowed=water&&(avoidanceMargin==null||!avoidanceMargin.covers(
                            projection.factory.createPoint(projection.xy(pixelPoint(id)))))
                        traversableCache[id]=if(allowed)2 else 1
                        allowed
                    }
                }
            }
            val ew=max(25.0,item.grid.pixelWidthDegrees*111_320.0*cos(Math.toRadians(latitude)).coerceAtLeast(.15))
            val ns=max(25.0,item.grid.pixelHeightDegrees*110_540.0)
            val nominal=(ew+ns)/2
            val maxSnapPixels=ceil(2_000.0/nominal).toInt().coerceIn(2,8)
            fun nearestPixel(origin:Pair<Int,Int>):Int? {
                val (ox,oy)=origin
                val direct=pixelId(ox,oy)
                if(traversable(direct))return direct
                var best:Int?=null;var bestDistance=Double.POSITIVE_INFINITY
                for(radius in 1..maxSnapPixels) {
                    for(y in (oy-radius).coerceAtLeast(0)..(oy+radius).coerceAtMost(height-1))
                        for(x in (ox-radius).coerceAtLeast(0)..(ox+radius).coerceAtMost(width-1)) {
                            if(max(abs(x-ox),abs(y-oy))!=radius)continue
                            val id=pixelId(x,y)
                            if(!traversable(id))continue
                            val d=hypot((x-ox)*ew,(y-oy)*ns)
                            if(d<bestDistance){best=id;bestDistance=d}
                        }
                    if(best!=null)return best
                }
                return null
            }
            val first=nearestPixel(startPixel)?:return null
            val target=nearestPixel(endPixel)?:return null

            val coastRadius=ceil(coastPreferenceMeters/nominal).toInt().coerceIn(1,4)
            val penaltyCache=DoubleArray(width*height){Double.NaN}
            fun openRing(id:Int,radius:Int):Boolean {
                val x=pixelX(id);val y=pixelY(id)
                if(radius<=0)return traversable(id)
                for(dy in -radius..radius)for(dx in -radius..radius) {
                    if(max(abs(dx),abs(dy))!=radius)continue
                    val nx=x+dx;val ny=y+dy
                    if(nx !in 0 until width||ny !in 0 until height||!traversable(pixelId(nx,ny)))return false
                }
                return true
            }
            fun pixelPenalty(id:Int):Double {
                val cached=penaltyCache[id]
                if(!cached.isNaN())return cached
                val value=pixelElevation(id)
                val depthPenalty=when {
                    value==null||value>=0f->20.0
                    required==null||-value.toDouble()>=required->1.0
                    else->{
                        val deficit=((required+value.toDouble())/required.coerceAtLeast(.1)).coerceIn(0.0,1.0)
                        4.0+12.0*deficit
                    }
                }
                val coastPenalty=when {
                    openRing(id,coastRadius)->1.0
                    openRing(id,max(1,(coastRadius*.65).roundToInt()))->2.0
                    openRing(id,1)->4.5
                    else->7.0
                }
                return (depthPenalty*coastPenalty).also{penaltyCache[id]=it}
            }
            fun edgeOpen(from:Int,to:Int,dx:Int,dy:Int):Boolean {
                if(!traversable(to))return false
                if(avoidanceMargin!=null&&projection.factory.createLineString(
                        arrayOf(projection.xy(pixelPoint(from)),projection.xy(pixelPoint(to)))
                    ).intersects(avoidanceMargin))return false
                return true
            }
            fun squeezePenalty(from:Int,dx:Int,dy:Int):Double {
                if(dx==0||dy==0)return 1.0
                val x=pixelX(from);val y=pixelY(from)
                val sideA=traversable(pixelId(x+dx,y))
                val sideB=traversable(pixelId(x,y+dy))
                // 15″ GEBCO 的窄斜航道常只剩角接触水格。允许通过，但成本高，
                // 所以有更宽水路时仍会优先选择更宽的路线。
                return when {
                    sideA&&sideB->1.0
                    sideA||sideB->1.6
                    else->4.5
                }
            }
            fun heuristic(id:Int)=hypot((pixelX(id)-pixelX(target))*ew,(pixelY(id)-pixelY(target))*ns)
            data class PixelNode(val id:Int,val cost:Double,val score:Double)
            val scores=DoubleArray(width*height){Double.POSITIVE_INFINITY}
            val parents=IntArray(width*height){-1}
            val queue=PriorityQueue<PixelNode>(compareBy{it.score})
            scores[first]=0.0;queue.add(PixelNode(first,0.0,heuristic(first)*1.12))
            var visited=0
            while(queue.isNotEmpty()&&visited<width*height) {
                currentCoroutineContext().ensureActive()
                val node=queue.remove();if(node.cost>scores[node.id])continue
                if(node.id==target) {
                    val reverse=mutableListOf<Int>();var current=target
                    while(current>=0){reverse+=current;current=parents[current]}
                    reverse.reverse()
                    val coordinates=mutableListOf<Coordinate>(a)
                    reverse.mapTo(coordinates){projection.xy(pixelPoint(it))}
                    coordinates+=b
                    val reduced=mutableListOf(coordinates.first());var i=0
                    while(i<coordinates.lastIndex) {
                        var next=coordinates.lastIndex
                        while(next>i+1&&!simplifyClear(coordinates[i],coordinates[next]))next--
                        reduced+=coordinates[next];i=next
                    }
                    return preserveEndpoints(reduced)
                }
                visited++
                if(visited%256==0)onProgress((visited/(width*height).toFloat()).coerceAtMost(.99f))
                val x=pixelX(node.id);val y=pixelY(node.id)
                for(dy in -1..1)for(dx in -1..1) {
                    if(dx==0&&dy==0)continue
                    val nx=x+dx;val ny=y+dy
                    if(nx !in 0 until width||ny !in 0 until height)continue
                    val next=pixelId(nx,ny)
                    if(!edgeOpen(node.id,next,dx,dy))continue
                    val edge=hypot(dx*ew,dy*ns)
                    val cost=node.cost+edge*(pixelPenalty(node.id)+pixelPenalty(next))/2*squeezePenalty(node.id,dx,dy)
                    if(cost>=scores[next])continue
                    scores[next]=cost;parents[next]=node.id
                    // 粗规划允许轻微加权 A*，减少长距离搜索时无意义的横向扩展。
                    queue.add(PixelNode(next,cost,cost+heuristic(next)*1.12))
                }
            }
            return null
        }
        searchSingleRasterPixels()?.let{return it}

        val extent=padding.coerceAtLeast(2_000.0)
        val minX=min(a.x,b.x)-extent;val maxX=max(a.x,b.x)+extent
        val minY=min(a.y,b.y)-extent;val maxY=max(a.y,b.y)+extent
        val span=max(maxX-minX,maxY-minY)
        val step=max(50.0,min(span/170.0,cellMeters.coerceAtMost(600.0)))
        val cols=ceil((maxX-minX)/step).toInt()+1;val rows=ceil((maxY-minY)/step).toInt()+1
        if(cols<=1||rows<=1||cols.toLong()*rows>300_000)return null
        fun coord(id:Int)=Coordinate(minX+(id%cols)*step,minY+(id/cols)*step)
        fun id(c:Coordinate)=(((c.y-minY)/step).roundToInt().coerceIn(0,rows-1))*cols+
            ((c.x-minX)/step).roundToInt().coerceIn(0,cols-1)

        // A* 节点/半格中点按本次搜索缓存。长距离时同一个点会被许多邻边重复访问；
        // 缓存后水陆、岸距和深度代价只计算一次，不改变最终折线的逐段复核。
        val halfCols=cols*2-1
        val halfRows=rows*2-1
        val safeCache=ByteArray(halfCols*halfRows)
        fun halfCoordinate(x2:Int,y2:Int)=Coordinate(minX+x2*step*.5,minY+y2*step*.5)
        fun safeHalf(x2:Int,y2:Int):Boolean {
            if(x2 !in 0 until halfCols||y2 !in 0 until halfRows)return false
            val index=y2*halfCols+x2
            return when(safeCache[index].toInt()) {
                1->false
                2->true
                else->safe(halfCoordinate(x2,y2)).also{safeCache[index]=if(it)2 else 1}
            }
        }
        fun nodeSafe(node:Int):Boolean {
            val x=node%cols;val y=node/cols
            return safeHalf(x*2,y*2)
        }
        val penaltyCache=DoubleArray(cols*rows){Double.NaN}
        fun nodePenalty(node:Int):Double {
            val cached=penaltyCache[node]
            if(!cached.isNaN())return cached
            val at=coord(node)
            return (depthMultiplier(at)*coastMultiplier(at)).also{penaltyCache[node]=it}
        }
        fun edgePassable(from:Int,to:Int,dx:Int,dy:Int):Boolean {
            if(!nodeSafe(from)||!nodeSafe(to))return false
            val x=from%cols;val y=from/cols
            val nx=x+dx;val ny=y+dy
            // 对角移动不能从陆地格子的角上“切过去”。
            if(dx!=0&&dy!=0) {
                // 粗栅格岸线常把一个真实可通过的斜向水道切成锯齿。
                // 两个正交旁点都不可用才禁止切角；最终折线还会做密集逐段复核。
                if(!nodeSafe(y*cols+nx)&&!nodeSafe(ny*cols+x))return false
            }
            // step 不大于一个数值栅格像元；两端+半格中点足够做搜索阶段的连通判断。
            if(!safeHalf(x*2+dx,y*2+dy))return false
            if(avoidanceMargin!=null&&projection.factory.createLineString(arrayOf(coord(from),coord(to))).intersects(avoidanceMargin))return false
            return true
        }

        data class RasterNode(val id:Int,val cost:Double,val score:Double)
        fun controlConnectorClear(origin:Coordinate,target:Coordinate):Boolean {
            if(avoidanceMargin!=null&&projection.factory.createLineString(arrayOf(origin,target)).intersects(avoidanceMargin))return false
            val length=origin.distance(target);val slices=max(1,ceil(length/sampleStep).toInt())
            for(i in 0..slices) {
                val t=i.toDouble()/slices
                val at=Coordinate(origin.x+(target.x-origin.x)*t,origin.y+(target.y-origin.y)*t)
                // 接入短段只要求中心线在水中；到达搜索网格后恢复 safe() 岸距。
                if(!terrainWater(at)||!outsideAvoidance(at))return false
            }
            return true
        }
        fun nearestNode(origin:Coordinate):Int? {
            val base=id(origin);val bx=base%cols;val by=base/cols
            var best:Int?=null;var bestDistance=Double.POSITIVE_INFINITY
            // 允许控制点从粗岸线附近接入安全搜索网格；范围略大于旧 3 格，避免长距离粗 step 下误杀。
            for(radius in 0..6)for(y in by-radius..by+radius)for(x in bx-radius..bx+radius) {
                if(x !in 0 until cols||y !in 0 until rows)continue
                val candidate=y*cols+x;val at=coord(candidate);val d=origin.distance(at)
                if(d<bestDistance&&nodeSafe(candidate)&&controlConnectorClear(origin,at)) {best=candidate;bestDistance=d}
            }
            return best
        }
        val first=nearestNode(a)?:return null
        val scores=DoubleArray(cols*rows){Double.POSITIVE_INFINITY};val parents=IntArray(cols*rows){-1}
        val queue=PriorityQueue<RasterNode>(compareBy{it.score})
        scores[first]=a.distance(coord(first));queue.add(RasterNode(first,scores[first],scores[first]+coord(first).distance(b)))
        val visitBudget=min(70_000,max(16_000,cols*rows));var visited=0;var found=-1
        while(queue.isNotEmpty()&&visited<visitBudget) {
            currentCoroutineContext().ensureActive()
            val node=queue.remove();if(node.cost>scores[node.id])continue
            visited++;if(visited%100==0)onProgress((visited/visitBudget.toFloat()).coerceAtMost(.99f))
            val here=coord(node.id)
            if(here.distance(b)<=step*2&&clear(here,b)){found=node.id;break}
            val x=node.id%cols;val y=node.id/cols
            for(dy in -1..1)for(dx in -1..1) {
                if(dx==0&&dy==0)continue
                val nx=x+dx;val ny=y+dy
                if(nx !in 0 until cols||ny !in 0 until rows)continue
                val next=ny*cols+nx;val there=coord(next)
                if(!edgePassable(node.id,next,dx,dy))continue
                val edge=here.distance(there)
                val cost=node.cost+edge*(nodePenalty(node.id)+nodePenalty(next))/2
                if(cost>=scores[next])continue
                scores[next]=cost;parents[next]=node.id
                queue.add(RasterNode(next,cost,cost+there.distance(b)))
            }
        }
        if(found<0)return null
        val reverse=mutableListOf<Coordinate>(b);var current=found
        while(current>=0){reverse.add(coord(current));current=parents[current]}
        reverse.add(a);reverse.reverse()
        val reduced=mutableListOf(reverse.first());var i=0
        while(i<reverse.lastIndex) {
            var next=reverse.lastIndex
            while(next>i+1&&!simplifyClear(reverse[i],reverse[next]))next--
            reduced.add(reverse[next]);i=next
        }
        return preserveEndpoints(reduced)
    }

    /**
     * 自动规划的门槛只回答“有没有可尝试搜索的资料”。
     * GEBCO/数值栅格不再为了门槛先 polygonize 一遍；搜索本身会检查 NoData、陆地、浅水和端点可通行性。
     * 纯矢量资料仍需读取局部对象来确认区域语义，因为仅靠图幅元数据无法证明存在可搜索水域。
     */
    private suspend fun planningReadiness(snapshot:ChartDataSnapshot,request:PassageRequest,leg:Int?):PassagePlanningReadiness {
        fun evaluate(evidence:PassagePlanningEvidence?=null)=PassagePlanningEligibility.evaluate(
            request.datasetIds,snapshot.datasets,System.currentTimeMillis(),snapshot.missingDatasetIds,evidence)
        val metadata=evaluate()
        if(!metadata.canRequestPlanning)return metadata
        val legs=if(leg==null)(0 until request.route.points.lastIndex).toList()else listOf(leg)
        legs.forEach { index ->
            val length=distance(request.route.points[index],request.route.points[index+1])
            require(length<=80_000){"该航段过长，请添加中间航点 / Add intermediate waypoints to this leg"}
        }

        val rasters=snapshot.datasets.flatMap{it.rasters.orEmpty()}
        if(rasters.isNotEmpty()) {
            val endpoints=legs.flatMap{index->listOf(request.route.points[index],request.route.points[index+1])}.distinct()
            val covered=endpoints.all{point->rasters.any{grid->rasterContains(grid,point)}}
            progress(request.requestId,PassageJobPhase.LOADING,.08f)
            return evaluate(PassagePlanningEvidence(
                coverageConfirmed=covered,
                depthAreasConfirmed=covered,
                semanticsComplete=true
            ))
        }

        // 矢量-only 资料没有数值网格可做快速范围判定；只在这种情况下保留局部语义门槛。
        for((order,index) in legs.withIndex()) {
            currentCoroutineContext().ensureActive()
            val start=request.route.points[index];val end=request.route.points[index+1]
            val length=distance(start,end)
            val world=geometry.world(snapshot,request,listOf(start,end),max(2000.0,length*.75).coerceAtMost(40_000.0))
            val factory=world.projection.factory
            val endpoints=listOf(start,end).map{factory.createPoint(world.projection.xy(it))}
            val depths=world.features.filter { item ->
                val feature=item.feature;val depth=feature.depth
                feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&
                    feature.geometry.kind==ChartGeometryKind.POLYGON&&!feature.issues.any(::isBlockingChartIssue)&&
                    depth?.kind==DepthEvidenceKind.INTERVAL&&!depth.datum.isNullOrBlank()&&depth.lowerMeters?.isFinite()==true
            }.map{it.geometry}
            val searchableEvidence=union(depths,factory)
            val result=evaluate(PassagePlanningEvidence(
                endpoints.all{world.coverage.covers(it)},
                endpoints.all{searchableEvidence.covers(it)},
                world.malformed.isEmpty()
            ))
            if(!result.canSearch)return result
            progress(request.requestId,PassageJobPhase.LOADING,.08f+.12f*(order+1f)/legs.size)
        }
        return evaluate(PassagePlanningEvidence(coverageConfirmed=true,depthAreasConfirmed=true))
    }

    /** 这是门槛结果，不是全线分析；不伪造水深条带、到达时间或“未发现冲突”。 */
    private fun readinessAnalysis(snapshot:ChartDataSnapshot,request:PassageRequest,readiness:PassagePlanningReadiness):PassageAnalysis {
        val key=passageHash(listOf(PASSAGE_RULES_VERSION,request.route,request.vessel,request.datasetIds,snapshot.datasets.map{it.id to it.revision},readiness.reason))
        val kind=when(readiness.reason){
            PassageReadinessReason.NO_STRUCTURED_COVERAGE,PassageReadinessReason.REGION_NOT_COVERED->PassageIssueKind.COVERAGE
            PassageReadinessReason.DEPTH_NOT_SUPPORTED->PassageIssueKind.DEPTH
            PassageReadinessReason.UNSUPPORTED_DATA->PassageIssueKind.QUALITY
            else->PassageIssueKind.DATA
        }
        val issue=PassageIssue("$key:eligibility",PassageSeverity.INSUFFICIENT,kind,0,request.route.points.firstOrNull(),0.0,readiness.message)
        return PassageAnalysis(request.requestId,key,request,snapshot.revision,snapshot.datasets.associate{it.id to it.revision},System.currentTimeMillis(),
            request.route.points.zipWithNext().sumOf{distance(it.first,it.second)},null,PassageSeverity.INSUFFICIENT,listOf(issue),emptyList(),PASSAGE_RULES_VERSION,complete=false)
    }
    /** 自动规划的原线摘要只用于绑定当前输入版本；它不是完整安全分析。 */
    private fun planningPreviewAnalysis(snapshot:ChartDataSnapshot,request:PassageRequest):PassageAnalysis {
        val total=request.route.points.zipWithNext().sumOf{distance(it.first,it.second)}
        val key=passageHash(listOf(PASSAGE_RULES_VERSION,"route-first",request.route,request.vessel,request.datasetIds,
            snapshot.datasets.map{it.id to it.revision},request.avoidances))
        val note=PassageIssue("$key:route-first",PassageSeverity.REVIEW,PassageIssueKind.QUALITY,0,
            request.route.points.firstOrNull(),0.0,
            "自动规划会先生成可编辑的粗略航线；完整深度、净空、限制与资料质量检查可在出线后按需运行 / Auto planning generates an editable coarse route first; run the full route check afterward when needed")
        val speed=request.vessel.plannedSpeedMetersPerSecond?.takeIf{it.isFinite()&&it>.1}
        val arrival=request.departureUtc?.let{depart->speed?.let{depart+(total/it*1000).toLong()}}
        return PassageAnalysis(request.requestId,key,request,snapshot.revision,snapshot.datasets.associate{it.id to it.revision},
            System.currentTimeMillis(),total,arrival,PassageSeverity.REVIEW,listOf(note),emptyList(),PASSAGE_RULES_VERSION,complete=false)
    }

    /**
     * 搜索已经逐边限制在 world.navigable 内。这里故意不再调用 geometry.analyze() 重扫整条候选：
     * 自动规划的职责是快速给出可编辑路线，完整证据检查由独立“检查当前航线”承担。
     */
    private fun planningCandidateAnalysis(snapshot:ChartDataSnapshot,request:PassageRequest,route:PassageRoute,turnRadius:Double?):PassageAnalysis {
        val candidateRequest=request.copy(requestId=request.requestId+":candidate",route=route)
        val total=route.points.zipWithNext().sumOf{distance(it.first,it.second)}
        val key=passageHash(listOf(PASSAGE_RULES_VERSION,"route-first-candidate",route,request.vessel,request.datasetIds,
            snapshot.datasets.map{it.id to it.revision},request.avoidances))
        val issues=buildList {
            add(PassageIssue("$key:coarse",PassageSeverity.REVIEW,PassageIssueKind.QUALITY,0,route.points.firstOrNull(),0.0,
                "已在当前数据的连续水域中自动绕开陆地，并优先避开明显浅区；这是一条粗略航线，不是完整航海安全检查 / Coarse route follows connected water, automatically routing around land and preferring to avoid obvious shallows; this is not a full navigation-safety check"))
            if(request.vessel.draftMeters==null)add(PassageIssue("$key:draft",PassageSeverity.REVIEW,PassageIssueKind.VESSEL,0,
                route.points.firstOrNull(),0.0,
                "未设置吃水；当前只按水陆地形出线，不判断实际余深 / Draft is unset; this route only uses terrain and does not assess under-keel depth"))
            if(request.vessel.airDraftMeters==null)add(PassageIssue("$key:air",PassageSeverity.REVIEW,PassageIssueKind.CLEARANCE,0,
                route.points.firstOrNull(),0.0,
                "未设置船高；自动规划会保守避开已知桥梁和高空设施 / Air draft is unset; auto planning conservatively avoids known bridges and overhead structures"))
            if(route.points.size>2)add(PassageIssue("$key:shape",PassageSeverity.REVIEW,PassageIssueKind.GEOMETRY,0,
                route.points.getOrNull(1),0.0,
                "自动补出的中间点只是粗略航线形状点；请在地图上按实际操船核对 / Auto-added intermediate points only shape the coarse route; review them on the chart for actual handling"))
        }
        val speed=request.vessel.plannedSpeedMetersPerSecond?.takeIf{it.isFinite()&&it>.1}
        val arrival=request.departureUtc?.let{depart->speed?.let{depart+(total/it*1000).toLong()}}
        return PassageAnalysis(candidateRequest.requestId,key,candidateRequest,snapshot.revision,snapshot.datasets.associate{it.id to it.revision},
            System.currentTimeMillis(),total,arrival,PassageSeverity.REVIEW,issues,emptyList(),PASSAGE_RULES_VERSION,complete=false)
    }

    private suspend fun createPlan(snapshot:ChartDataSnapshot,request:PassageRequest,original:PassageAnalysis,leg:Int?):PassagePlan {
        val points=request.route.points
        require(leg==null||leg in 0 until points.lastIndex){"Choose an existing leg"}
        val result=mutableListOf(points.first())
        val fastRaster=pureNumericRaster(snapshot)
        for(index in 0 until points.lastIndex){
            currentCoroutineContext().ensureActive()
            if(leg!=null&&leg!=index){result.add(points[index+1]);continue}
            val a=points[index];val b=points[index+1];val distance=distance(a,b)
            if(distance>80_000)return PassagePlan(request.requestId,original,emptyList(),"该航段过长，请添加中间航点 / Add intermediate waypoints to this leg")
            val basePadding=max(2000.0,distance*.75).coerceAtMost(40_000.0)
            // 岛屿/半岛在直线中间时，失败不是正确答案：自动向外扩大水域搜索，自己寻找绕行侧。
            // 纯栅格快速规划多给一档扩展范围；仍保持有界，避免无限计算。
            val paddings=if(fastRaster) listOf(
                basePadding,
                max(basePadding,min(45_000.0,max(10_000.0,distance*1.5))),
                max(basePadding,min(60_000.0,max(18_000.0,distance*2.5)))
            ).distinct().sorted()
            else listOf(basePadding,max(basePadding,min(60_000.0,max(8_000.0,distance*1.25)))).distinct()
            var path:List<ChartPoint>?=null
            for((attempt,padding) in paddings.withIndex()) {
                currentCoroutineContext().ensureActive()
                progress(request.requestId,PassageJobPhase.LOADING,
                    ((index+(attempt.toFloat()/paddings.size))/points.lastIndex).coerceIn(.08f,.9f))
                path=if(fastRaster) {
                    searchNumericRaster(snapshot,request,a,b,padding){fraction->
                        progress(request.requestId,PassageJobPhase.SEARCHING,(index+(attempt+fraction)/paddings.size)/points.lastIndex)
                    }
                } else {
                    val world=geometry.world(snapshot,request,listOf(a,b),padding)
                    geometry.search(world,a,b,request.vessel.turnRadiusMeters,smoothTurns=leg!=null){fraction->
                        progress(request.requestId,PassageJobPhase.SEARCHING,(index+(attempt+fraction)/paddings.size)/points.lastIndex)
                    }
                }
                if(path!=null)break
            }
            val foundPath=path ?: return PassagePlan(request.requestId,original,emptyList(),
                "第 ${index+1} 段自动扩大绕行范围后仍未找到连续水路。控制点本身只按水域接入，不会因靠近岸线被拒绝；若这些点都在水上，通常表示当前 GEBCO 粗网格把中间水路切断或资料存在缺口 / Leg ${index+1} still has no continuous water route after expanded detour search. Control points are accepted by their water cell even near shore; if they are all in water, the selected coarse grid is likely disconnecting the waterway or has a data gap")
            result.addAll(foundPath.drop(1))
        }
        require(result.size<=2000){"Candidate is too complex"}
        val turnRadius=request.vessel.turnRadiusMeters?.takeIf {it.isFinite()&&it>0}
        // 全线自动规划不再为了转弯半径重新构造一次整区 world；先给粗略折线。
        // 高级“仅绕行某一段”仍在 search 内按已设置半径做局部平滑。
        val candidatePoints=result
        if(leg!=null&&result.size>2&&turnRadius!=null){
            // 局部绕行不可偷偷移动相邻保留航点；接头不相切时必须要求用户重做全线。
            val junctions=listOfNotNull(points.getOrNull(leg)?.takeIf{leg>0},points.getOrNull(leg+1)?.takeIf{leg+1<points.lastIndex})
            val discontinuity=junctions.any{joint->val index=result.indexOf(joint);if(index<=0||index>=result.lastIndex)false else{
                val projection=PassageProjection(joint);val a=projection.xy(result[index-1]);val b=projection.xy(result[index+1]);
                abs(atan2(a.x*b.y-a.y*b.x,-(a.x*b.x+a.y*b.y)))>Math.toRadians(2.0)
            }}
            if(discontinuity)return PassagePlan(request.requestId,original,emptyList(),"绕行接头需要重新平滑，请选择全线规划 / Detour joins need smoothing; plan the whole route")
        }
        // 原用户目标必须仍在候选上，圆弧采样不能悄悄取代一个被平滑移开的目的地。
        val logical=request.route.navigationTargetIndices ?: points.indices.drop(1)
        val targetIndices=mutableListOf<Int>()
        for(originalIndex in logical.filter{it>0}) {
            val originalPoint=points.getOrNull(originalIndex)?:return PassagePlan(request.requestId,original,emptyList(),"目标索引无效 / Invalid destination mapping")
            val previous=targetIndices.lastOrNull()?:-1
            val match=(previous+1 until candidatePoints.size).minByOrNull{distance(candidatePoints[it],originalPoint)}
            if(match==null||distance(candidatePoints[match],originalPoint)>.5)return PassagePlan(request.requestId,original,emptyList(),"转弯会移动已选航点，请调整航点后重算 / Smoothing would move a destination; adjust waypoints")
            targetIndices.add(match)
        }
        if(targetIndices.lastOrNull()!=candidatePoints.lastIndex)targetIndices.add(candidatePoints.lastIndex)
        val candidateRoute=request.route.copy(revision=passageHash(listOf(candidatePoints,targetIndices)),points=candidatePoints,navigationTargetIndices=targetIndices)
        progress(request.requestId,PassageJobPhase.ANALYZING,.96f)
        val final=planningCandidateAnalysis(snapshot,request,candidateRoute,turnRadius)
        return PassagePlan(request.requestId,original,listOf(PassageCandidate(
            passageHash(candidateRoute),candidateRoute,final,final.distanceMeters-original.distanceMeters,targetIndices)))
    }
}

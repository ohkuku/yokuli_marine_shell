package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.hardware.VirtualHostServices
import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.contract.navigation.NavigationRouteSnapshot
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
import org.locationtech.jts.geom.TopologyException
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
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
    private fun failureDetail(error:Exception):String {
        val message=error.message.orEmpty()
        return if(error is TopologyException||message.contains("non-noded intersection",true)||message.contains("side location conflict",true))
            "局部海图几何有拓扑问题；已避免把它解释成陆地。请重试规划，问题区域会按局部未知处理 / Local chart geometry has a topology issue; it is not treated as land. Retry planning; the affected area is handled as local unknown."
        else message.take(250).ifBlank{"Unable to calculate"}
    }
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
    /** 导航所有者只读取本服务已发布的候选；不信任 UI 已启用按钮或命令附带的分析字符串。 */
    internal suspend fun navigationReferenceRejection(analysisReference:String,route:NavigationRouteSnapshot,requireCandidate:Boolean):String? {
        ready.await()
        val current=state.value
        if(!current.ready||current.storageIssue!=null)return "NAVIGATION_ANALYSIS_EXPIRED"
        val plan=current.plan
        val candidate=plan?.candidates?.firstOrNull{it.analysis.key==analysisReference}
        if(candidate==null) {
            // 手动 START 可引用当前完整接入检查（仍允许人工处理其问题），不把草稿摘要冒充完整检查。
            val analysis=current.analysis
            return if(!requireCandidate&&analysis!=null&&analysis.key==analysisReference&&analysis.complete&&
                analysis.rulesVersion==PASSAGE_RULES_VERSION&&current.job?.phase==PassageJobPhase.COMPLETE) null else "NAVIGATION_ANALYSIS_EXPIRED"
        }
        if(candidate.draftOnly)return "NAVIGATION_REFERENCE_DRAFT_ONLY"
        if(candidate.analysis.severity !in setOf(PassageSeverity.REVIEW,PassageSeverity.NO_CONFLICT_FOUND))return "NAVIGATION_ANALYSIS_INSUFFICIENT"
        val data=charts.state.value
        if(current.job?.phase!=PassageJobPhase.COMPLETE||
            plan?.original?.key!=current.analysis?.key||current.planningReadiness?.canSearch!=true||
            candidate.analysis.rulesVersion!=PASSAGE_RULES_VERSION||data.loading||data.error!=null||
            candidate.analysis.datasetRevisions!=current.analysis?.datasetRevisions||
            candidate.analysis.request.avoidances!=current.avoidances||
            !PassagePlanningEligibility.evaluate(candidate.analysis.request.datasetIds,data.datasets,System.currentTimeMillis()).canRequestPlanning||
            candidate.analysis.datasetRevisions.any{(id,revision)->data.datasets.firstOrNull{it.id==id}?.revision!=revision})
            return "NAVIGATION_ANALYSIS_EXPIRED"
        if(route.id!=candidate.route.id||route.navigationTargetIndices!=candidate.navigationTargetIndices||
            route.waypoints.map{ChartPoint(it.point.lat,it.point.lon)}!=candidate.route.points)return "NAVIGATION_ANALYSIS_ROUTE_MISMATCH"
        return null
    }
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
    private fun progress(id:String,phase:PassageJobPhase,p:Float,detail:String=""){mutable.update{if(it.job?.requestId==id&&it.job?.phase in working)it.copy(job=PassageJob(id,phase,p.coerceIn(0f,1f),detail))else it}}
    private suspend fun run(request:PassageRequest,leg:Int?,planning:Boolean){
        var snapshot:ChartDataSnapshot?=null
        try{
            geometry.validateRequest(request)
            require(leg==null||leg in 0 until request.route.points.lastIndex){"Choose an existing leg"}
            if(LINZ_ONLINE_DATASET_ID in request.datasetIds) {
                val length=request.route.points.zipWithNext().maxOf {distance(it.first,it.second)}
                charts.ensureLinz(around(request.route.points,max(2000.0,length*.75).coerceAtMost(40_000.0)))
            }
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
            commit{if(it.job?.requestId==request.requestId)it.copy(job=PassageJob(request.requestId,PassageJobPhase.FAILED,detail=failureDetail(e)))else it}
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
        val job=currentCoroutineContext()
        val projection=PassageProjection(start){job.ensureActive()}
        val cells=snapshot.datasets.flatMap { data->data.cells.map { "${data.id}/${it.cellId}" to it } }.toMap()
        val manualOrder=cells.values.any{it.priorityExplicit}
        val windows=charts.rasterWindows(snapshot.id,around(listOf(start,end),padding),maxCells=262_144)
            .sortedWith(
                if(manualOrder)
                    compareBy<ChartRasterWindow>{cells["${it.grid.datasetId}/${it.grid.cellId}"]?.priority?:Int.MAX_VALUE}
                        .thenBy{max(it.grid.pixelWidthDegrees,it.grid.pixelHeightDegrees)}
                else
                    compareBy<ChartRasterWindow>{max(it.grid.pixelWidthDegrees,it.grid.pixelHeightDegrees)}
                        .thenBy{cells["${it.grid.datasetId}/${it.grid.cellId}"]?.priority?:Int.MAX_VALUE}
            )
        if(windows.isEmpty())return null
        val latitude=(start.latitude+end.latitude)/2.0
        val cellMeters=windows.minOf{item->
            val ew=item.grid.pixelWidthDegrees*111_320.0*cos(Math.toRadians(latitude)).coerceAtLeast(.15)
            val ns=item.grid.pixelHeightDegrees*110_540.0
            max(10.0,min(ew,ns))
        }
        val vessel=request.vessel
        val margin=max(vessel.corridorHalfWidthMeters?:0.0,(vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0))
        val required=vessel.draftMeters?.takeIf{it.isFinite()&&it>0}?.plus(vessel.minimumUnderKeelMeters?:0.0)

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
            return value.isFinite()&&value<0f&&(required==null||-value.toDouble()>=required)
        }

        val avoidance=union(request.avoidances.mapNotNull{a->
            runCatching{projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(a.boundary))))}.getOrNull()
        },projection.factory)
        val avoidanceMargin=if(avoidance.isEmpty)null else avoidance.buffer(margin)
        val diagonal=margin/sqrt(2.0)
        val offsets=if(margin<=0.0)arrayOf(0.0 to 0.0)else arrayOf(
            0.0 to 0.0,margin to 0.0,-margin to 0.0,0.0 to margin,0.0 to -margin,
            diagonal to diagonal,diagonal to -diagonal,-diagonal to diagonal,-diagonal to -diagonal
        )
        fun outsideAvoidance(c:Coordinate):Boolean =
            avoidanceMargin==null||!avoidanceMargin.covers(projection.factory.createPoint(c))
        fun safe(c:Coordinate):Boolean {
            for((dx,dy) in offsets)if(!terrainWater(Coordinate(c.x+dx,c.y+dy)))return false
            return outsideAvoidance(c)
        }
        // 距离是唯一优化代价；固定几百米岸距和倍数惩罚会把直水路扭成大绕行。
        // 岸线精度只作为资料警示；用户明确配置的船宽/走廊仍是硬约束。
        val sampleStep=max(10.0,min(250.0,cellMeters*.5))
        fun clear(a:Coordinate,b:Coordinate):Boolean {
            job.ensureActive()
            // 原网格逐格穿越，检查所有被线段触及的格；不能用稀疏采样或 Bresenham 漏掉角格。
            windows.singleOrNull()?.let { item ->
                val pa=projection.point(a);val pb=projection.point(b)
                val grid=item.grid
                fun x(p:ChartPoint):Double {
                    val delta=((p.longitude-grid.westEdge)%360+360)%360
                    return delta/grid.pixelWidthDegrees
                }
                val x0=x(pa);var x1=x(pb)
                val period=360/grid.pixelWidthDegrees
                if(x1-x0>period/2)x1-=period else if(x0-x1>period/2)x1+=period
                if(!RasterSupercover.clear(x0,(grid.northEdge-pa.latitude)/grid.pixelHeightDegrees,
                        x1,(grid.northEdge-pb.latitude)/grid.pixelHeightDegrees) { column,row ->
                    val xx=column-item.window.column;val yy=row-item.window.row
                    if(xx !in 0 until item.window.width||yy !in 0 until item.window.height)false
                    else item.window.elevationAt(xx,yy)?.let { v->v<0&&(required==null||-v>=required) }==true
                })return false
            }
            if(windows.size>1) {
                // 不同文件/分辨率：把线按所有原网格边界分段，再在每段查唯一优先来源。
                // 高优先 NoData 不借低优先深水补洞，也不让一次采样跨过细小陆地角格。
                val pa=projection.point(a);val pb=projection.point(b)
                val dl=((pb.longitude-pa.longitude+540)%360)-180
                val dlat=pb.latitude-pa.latitude
                val cuts=java.util.TreeSet<Double>().apply{add(0.0);add(1.0)}
                fun crossings(first:Double,last:Double) {
                    val delta=last-first;if(abs(delta)<1e-12)return
                    val lo=ceil(min(first,last)).toInt();val hi=floor(max(first,last)).toInt()
                    if(hi.toLong()-lo>100_000)error("栅格线段过大，请分段规划 / Raster segment too large; add a waypoint")
                    for(edge in lo..hi) {if(edge%128==0)job.ensureActive();val t=(edge-first)/delta;if(t>0&&t<1)cuts+=t}
                }
                for(item in windows) {
                    val grid=item.grid
                    val x0=(((pa.longitude-grid.westEdge)%360+360)%360)/grid.pixelWidthDegrees
                    crossings(x0,x0+dl/grid.pixelWidthDegrees)
                    crossings((grid.northEdge-pa.latitude)/grid.pixelHeightDegrees,(grid.northEdge-pb.latitude)/grid.pixelHeightDegrees)
                }
                fun water(t:Double,dx:Double=0.0,dy:Double=0.0):Boolean {
                    val point=ChartPoint(pa.latitude+dlat*t+dy,((pa.longitude+dl*t+dx+540)%360)-180)
                    val value=elevation(point)?:return false
                    return value<0&&(required==null||-value>=required)
                }
                val sequence=cuts.toList()
                for(i in 0 until sequence.lastIndex)if(!water((sequence[i]+sequence[i+1])/2))return false
                for(t in sequence.drop(1).dropLast(1)) {
                    if(!water(t))return false
                    // 约 0.1 mm 的边界两侧检查仅解决浮点归属，不改变地理余量。
                    for(dx in listOf(-1e-9,1e-9))for(dy in listOf(-1e-9,1e-9))if(!water(t,dx,dy))return false
                }
            }
            if(avoidanceMargin!=null&&projection.factory.createLineString(arrayOf(a,b)).intersects(avoidanceMargin))return false
            val length=a.distance(b);val slices=max(1,ceil(length/sampleStep).toInt())
            for(i in 0..slices) {
                if(i%128==0)job.ensureActive()
                val t=i.toDouble()/slices
                val at=Coordinate(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
                if(!safe(at))return false
            }
            return true
        }
        fun smoothRasterPath(points:List<ChartPoint>):List<ChartPoint>? {
            val radius=request.vessel.turnRadiusMeters?.takeIf{it.isFinite()&&it>0}?:return points
            if(points.size<=2)return points
            val reduced=points.map(projection::xy).fold(mutableListOf<Coordinate>()){list,p->
                if(list.lastOrNull()?.distance(p)?.let{it>.01}!=false)list.add(p);list
            }
            if(reduced.size<=2)return points
            val smooth=mutableListOf(reduced.first())
            for(index in 1 until reduced.lastIndex) {
                val before=reduced[index-1];val corner=reduced[index];val after=reduced[index+1]
                val inLen=before.distance(corner);val outLen=corner.distance(after)
                if(inLen<.01||outLen<.01)return null
                val ux=(corner.x-before.x)/inLen;val uy=(corner.y-before.y)/inLen
                val vx=(after.x-corner.x)/outLen;val vy=(after.y-corner.y)/outLen
                val angle=acos((ux*vx+uy*vy).coerceIn(-1.0,1.0))
                if(angle<.01){smooth+=corner;continue}
                val tangent=radius*tan(angle/2.0)
                if(!tangent.isFinite()||tangent>min(inLen,outLen)*.45)return null
                val entry=Coordinate(corner.x-ux*tangent,corner.y-uy*tangent)
                val exit=Coordinate(corner.x+vx*tangent,corner.y+vy*tangent)
                val side=if(ux*vy-uy*vx>0)1 else -1
                val centre=Coordinate(entry.x-uy*radius*side,entry.y+ux*radius*side)
                val base=atan2(entry.y-centre.y,entry.x-centre.x)
                val samples=max(4,ceil(angle/Math.toRadians(5.0)).toInt())
                smooth+=entry
                for(step in 1..samples) {
                    val theta=base+side*angle*step/samples
                    smooth+=Coordinate(centre.x+radius*cos(theta),centre.y+radius*sin(theta))
                }
                smooth[smooth.lastIndex]=exit
            }
            smooth+=reduced.last()
            if(!smooth.zipWithNext().all{(a,b)->clear(a,b)})return null
            return smooth.map(projection::point).toMutableList().also{route->
                route[0]=start;route[route.lastIndex]=end
            }
        }

        // 最远可见点的拉直只在同一水陆/吃水/避让约束下进行，不保留像元阶梯。
        fun simplifyShape(path:List<Coordinate>):List<Coordinate> {
            if(path.size<=2)return path
            val result=mutableListOf(path.first());var index=0
            while(index<path.lastIndex) {
                job.ensureActive()
                var next=path.lastIndex
                while(next>index+1&&!clear(path[index],path[next]))next--
                if(!clear(path[index],path[next]))return emptyList()
                if(result.last().distance(path[next])>.05)result+=path[next]
                index=next
            }
            return result
        }
        fun preserveEndpoints(coords:List<Coordinate>):List<ChartPoint>? {
            val reduced=simplifyShape(coords)
            if(reduced.size<2)return null
            val result=reduced.map(projection::point).toMutableList()
            result[0]=start;result[result.lastIndex]=end
            if(!result.zipWithNext().all { (a,b)->clear(projection.xy(a),projection.xy(b)) })return null
            return smoothRasterPath(result)
        }

        val rawA=projection.xy(start);val rawB=projection.xy(end)
        fun endpoint(origin:Coordinate,label:String,english:String):Coordinate {
            val value=elevation(projection.point(origin))
            require(value!=null) { "$label 无有效高程数据，请移动航点或更换资料 / $english has no elevation data; move the waypoint or choose other data" }
            require(value<0f) { "$label 在所选网格中属于陆地或零高程；不会擅自挪动航点，请移动到明确水域或使用更精细资料 / $english falls in a land or zero-elevation cell; move it into charted water or use finer data" }
            require(required==null||-value>=required) { "$label 的参考水深不足吃水和余深要求 / $english is shallower than the configured draft and under-keel clearance" }
            require(outsideAvoidance(origin)) { "$label 位于避让区内 / $english is inside an avoidance area" }
            return origin
        }
        val a=endpoint(rawA,"起点","Start")
        val b=endpoint(rawB,"终点","Destination")
        if(clear(a,b))return preserveEndpoints(listOf(a,b))

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
            val startPixel=localPixel(projection.point(a))?:return null
            val endPixel=localPixel(projection.point(b))?:return null
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
                        val water=value!=null&&value<0f&&(required==null||-value>=required)
                        val allowed=water&&(avoidanceMargin==null||!avoidanceMargin.covers(
                            projection.factory.createPoint(projection.xy(pixelPoint(id)))))
                        traversableCache[id]=if(allowed)2 else 1
                        allowed
                    }
                }
            }
            val ew=max(25.0,item.grid.pixelWidthDegrees*111_320.0*cos(Math.toRadians(latitude)).coerceAtLeast(.15))
            val ns=max(25.0,item.grid.pixelHeightDegrees*110_540.0)
            val first=pixelId(startPixel.first,startPixel.second)
            val target=pixelId(endPixel.first,endPixel.second)
            if(!traversable(first)||!traversable(target))return null
            // 用户航点和它的原像元中心之间也需要查线，禁止接出一段穿陆地/避让区的短线。
            if(!clear(a,projection.xy(pixelPoint(first)))||!clear(projection.xy(pixelPoint(target)),b))return null

            val lineCostCache=object:java.util.LinkedHashMap<Long,Double>(32_768,0.75f,true) {
                override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Long,Double>?)=size>32_768
            }
            fun lineKey(a:Int,b:Int):Long {
                val low=min(a,b);val high=max(a,b)
                return (low.toLong() shl 32) or (high.toLong() and 0xffffffffL)
            }
            /**
             * Theta* 的任意角度可见性：沿原生 GEBCO 像元用 supercover 检查整条直线。
             * 只要直线经过一个陆地/NoData/避让像元就不可见；窄航道里若连续水像元能形成直线，
             * 则不会再因为 8 邻接像素阶梯产生一串假拐点。
             */
            fun lineCost(from:Int,to:Int):Double? {
                if(from==to)return 0.0
                val key=lineKey(from,to)
                lineCostCache[key]?.let{return it.takeIf{value->value>=0.0}}
                if(!traversable(from)||!traversable(to)) {
                    lineCostCache[key]=-1.0;return null
                }
                if(avoidanceMargin!=null&&projection.factory.createLineString(
                        arrayOf(projection.xy(pixelPoint(from)),projection.xy(pixelPoint(to)))
                    ).intersects(avoidanceMargin)) {
                    lineCostCache[key]=-1.0;return null
                }
                val visible=RasterSupercover.clear(pixelX(from)+.5,pixelY(from)+.5,pixelX(to)+.5,pixelY(to)+.5) { x,y ->
                    x in 0 until width&&y in 0 until height&&traversable(pixelId(x,y))
                }
                if(!visible) {lineCostCache[key]=-1.0;return null}
                val cost=hypot((pixelX(to)-pixelX(from))*ew,(pixelY(to)-pixelY(from))*ns)
                lineCostCache[key]=cost
                return cost
            }
            fun heuristic(id:Int)=hypot((pixelX(id)-pixelX(target))*ew,(pixelY(id)-pixelY(target))*ns)
            data class PixelNode(val id:Int,val cost:Double,val score:Double)
            val scores=DoubleArray(width*height){Double.POSITIVE_INFINITY}
            val parents=IntArray(width*height){-1}
            val queue=PriorityQueue<PixelNode>(compareBy{it.score})
            scores[first]=0.0;parents[first]=first
            queue.add(PixelNode(first,0.0,heuristic(first)))
            var visited=0
            while(queue.isNotEmpty()&&visited<width*height) {
                currentCoroutineContext().ensureActive()
                val node=queue.remove();if(node.cost>scores[node.id])continue
                if(node.id==target) {
                    val reverse=mutableListOf<Int>()
                    var current=target
                    while(true) {
                        reverse+=current
                        if(current==first)break
                        current=parents[current]
                        if(current<0)return null
                    }
                    reverse.reverse()
                    val coordinates=mutableListOf<Coordinate>(a)
                    reverse.mapTo(coordinates){projection.xy(pixelPoint(it))}
                    coordinates+=b
                    return preserveEndpoints(coordinates)
                }
                visited++
                if(visited%256==0)onProgress((visited/(width*height).toFloat()).coerceAtMost(.99f))
                val x=pixelX(node.id);val y=pixelY(node.id)
                for(dy in -1..1)for(dx in -1..1) {
                    if(dx==0&&dy==0)continue
                    val nx=x+dx;val ny=y+dy
                    if(nx !in 0 until width||ny !in 0 until height)continue
                    val next=pixelId(nx,ny)
                    if(!traversable(next))continue

                    val local=lineCost(node.id,next)?:continue
                    var bestParent=node.id
                    var bestCost=scores[node.id]+local

                    // Basic Theta*: 优先尝试从当前节点的父节点直接看到 next。
                    // 这一步让搜索结果本身就是任意角度折线，而不是先制造像素阶梯再事后猜哪些点能删。
                    val parent=parents[node.id]
                    if(parent>=0&&parent!=node.id) {
                        val shortcut=lineCost(parent,next)
                        if(shortcut!=null) {
                            val candidate=scores[parent]+shortcut
                            if(candidate<bestCost) {
                                bestCost=candidate;bestParent=parent
                            }
                        }
                    }
                    if(bestCost>=scores[next])continue
                    scores[next]=bestCost;parents[next]=bestParent
                    queue.add(PixelNode(next,bestCost,bestCost+heuristic(next)))
                }
            }
            return null
        }
        searchSingleRasterPixels()?.let{return it}

        val extent=padding.coerceAtLeast(2_000.0)
        val minX=min(a.x,b.x)-extent;val maxX=max(a.x,b.x)+extent
        val minY=min(a.y,b.y)-extent;val maxY=max(a.y,b.y)+extent
        val width=maxX-minX;val height=maxY-minY
        val budgetStep=sqrt((width*height/250_000.0).coerceAtLeast(0.0)).coerceAtLeast(10.0)
        val sourceStep=cellMeters.coerceIn(10.0,250.0)
        val step=max(budgetStep,sourceStep)
        val cols=ceil(width/step).toInt()+1;val rows=ceil(height/step).toInt()+1
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
        fun edgePassable(from:Int,to:Int,dx:Int,dy:Int):Boolean {
            if(!nodeSafe(from)||!nodeSafe(to))return false
            val x=from%cols;val y=from/cols
            val nx=x+dx;val ny=y+dy
            // 对角移动不能从陆地格子的角上“切过去”。
            if(dx!=0&&dy!=0) {
                // 任一旁格不通即禁止穿角；显示上的细缝不能冒充原始数据中的水路。
                if(!nodeSafe(y*cols+nx)||!nodeSafe(ny*cols+x))return false
            }
            if(!safeHalf(x*2+dx,y*2+dy)||!clear(coord(from),coord(to)))return false
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
        val visitBudget=cols*rows;var visited=0;var found=-1
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
                val edge=here.distance(there)
                val cost=node.cost+edge
                if(cost>=scores[next])continue
                // 已经更短的节点无需再次运行整条边的原像元 supercover 和走廊检查。
                if(!edgePassable(node.id,next,dx,dy))continue
                scores[next]=cost;parents[next]=node.id
                queue.add(RasterNode(next,cost,cost+there.distance(b)))
            }
        }
        if(found<0)return null
        val reverse=mutableListOf<Coordinate>(b);var current=found
        while(current>=0){reverse.add(coord(current));current=parents[current]}
        reverse.add(a);reverse.reverse()
        return preserveEndpoints(reverse)
    }

    /** 航段越长，先用越粗的海图比例尺推断主走廊；异常时再回到完整细节。 */
    private fun planningScaleForDistance(meters:Double):Int=when {
        meters<4_000->4_000
        meters<15_000->22_000
        meters<45_000->90_000
        else->350_000
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

        val activeCells=snapshot.datasets.flatMap{it.cells}.groupBy{it.cellId}.values.map{versions->
            versions.maxWith(compareBy<ChartCellRevision>{it.edition}.thenBy{it.update})
        }.filterNot{it.cancelled}
        val endpoints=legs.flatMap{index->listOf(request.route.points[index],request.route.points[index+1])}.distinct()
        val ownershipKinds=setOf(
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA
        )
        fun metadataCovers(point:ChartPoint):Boolean=activeCells.any {cell->
            cell.bounds.any {box->point.latitude in box.south..box.north&&
                box.split().any{part->point.longitude>=part.west&&point.longitude<=part.east}}
        }

        var semanticsComplete=true
        var coverageConfirmed=true
        var depthConfirmed=true
        for(point in endpoints) {
            currentCoroutineContext().ensureActive()
            val info=try {
                charts.inspectPosition(request.datasetIds,point,2.0)
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){semanticsComplete=false;null}
            if(info==null) {
                coverageConfirmed=false;depthConfirmed=false;continue
            }
            if(info.incomplete)semanticsComplete=false
            val owners=info.hits.filter{it.distanceMeters<=.001&&it.feature.kind in ownershipKinds}
            val onLand=owners.any{it.feature.kind in setOf(NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA)}
            val vectorWater=!onLand&&owners.any{it.feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)}
            val rasterWater=!onLand&&!vectorWater&&info.raster?.elevationMeters?.let{it.isFinite()&&it<0f}==true
            val covered=owners.isNotEmpty()||info.raster!=null||metadataCovers(point)
            coverageConfirmed=coverageConfirmed&&covered
            depthConfirmed=depthConfirmed&&(vectorWater||rasterWater)
        }
        progress(request.requestId,PassageJobPhase.LOADING,.08f)
        return evaluate(PassagePlanningEvidence(
            coverageConfirmed=coverageConfirmed,
            depthAreasConfirmed=depthConfirmed,
            semanticsComplete=semanticsComplete
        ))
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
    private fun planningCandidateAnalysis(snapshot:ChartDataSnapshot,request:PassageRequest,route:PassageRoute,turnRadius:Double?,referenceDepthIssues:List<PassageIssue>):PassageAnalysis {
        val candidateRequest=request.copy(requestId=request.requestId+":candidate",route=route)
        val total=route.points.zipWithNext().sumOf{distance(it.first,it.second)}
        val key=passageHash(listOf(PASSAGE_RULES_VERSION,"route-first-candidate",route,request.vessel,request.datasetIds,
            snapshot.datasets.map{it.id to it.revision},request.avoidances))
        val hardWaypointTurns=buildList {
            val radius=turnRadius?.takeIf{it.isFinite()&&it>0}?:return@buildList
            var alongToJoint=0.0
            for(index in 1 until request.route.points.lastIndex) {
                val joint=request.route.points[index]
                val projection=PassageProjection(joint)
                val before=projection.xy(request.route.points[index-1])
                val after=projection.xy(request.route.points[index+1])
                val inLen=hypot(before.x,before.y);val outLen=hypot(after.x,after.y)
                alongToJoint+=inLen
                if(inLen<1.0||outLen<1.0)continue
                val ux=-before.x/inLen;val uy=-before.y/inLen
                val vx=after.x/outLen;val vy=after.y/outLen
                val turn=abs(atan2(ux*vy-uy*vx,ux*vx+uy*vy))
                if(turn>Math.toRadians(5.0)) {
                    val tangent=radius*tan(turn/2.0)
                    add(PassageIssue(
                        "$key:hard-turn:$index",PassageSeverity.REVIEW,PassageIssueKind.GEOMETRY,index-1,joint,alongToJoint,
                        if(tangent>min(inLen,outLen)*.45)
                            "用户航点必须精确经过，但该转角无法在相邻航段长度内满足已设置的转弯半径；请移动航点或增加过渡点 / This hard waypoint cannot satisfy the configured turn radius within the adjacent leg lengths; move it or add transition waypoints"
                        else
                            "用户航点是精确目标，自动圆弧不会跨过它；此处转角需按已设置的转弯半径人工确认 / Hard waypoints remain exact targets, so auto smoothing does not cut across this turn; review it against the configured turn radius"
                    ))
                }
            }
        }
        val issues=buildList {
            add(PassageIssue("$key:coarse",PassageSeverity.REVIEW,PassageIssueKind.QUALITY,0,route.points.firstOrNull(),0.0,
                if(referenceDepthIssues.isNotEmpty())
                    "依据 LINZ 连续参考水域生成绕陆草稿；源深度数值只作参考筛选，垂直基准未知，无法确认实际水深与余深 / Draft follows connected LINZ reference water around land. Source depth values are only a reference filter; unknown vertical datum prevents confirmation of actual depth or under-keel clearance"
                else "依据当前资料生成并用细节走廊复核的可编辑草稿；它不证明实际余深，也不能直接作为导航依据，请先运行完整航线检查 / Editable draft generated from current data and locally rechecked against full-detail corridors; it does not establish actual under-keel clearance and cannot be used as navigation evidence until a full route check is run"))
            addAll(referenceDepthIssues.mapIndexed{index,issue->issue.copy(id="$key:linz-datum:$index")})
            addAll(hardWaypointTurns)
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
            System.currentTimeMillis(),total,arrival,if(referenceDepthIssues.isEmpty())PassageSeverity.REVIEW else PassageSeverity.INSUFFICIENT,issues,emptyList(),PASSAGE_RULES_VERSION,complete=false)
    }

    private suspend fun createPlan(snapshot:ChartDataSnapshot,request:PassageRequest,original:PassageAnalysis,leg:Int?):PassagePlan {
        val points=request.route.points
        require(leg==null||leg in 0 until points.lastIndex){"Choose an existing leg"}
        val result=mutableListOf(points.first())
        val referenceDepthIssues=linkedMapOf<Pair<Int,String>,PassageIssue>()
        val fastRaster=pureNumericRaster(snapshot)
        for(index in 0 until points.lastIndex){
            currentCoroutineContext().ensureActive()
            if(leg!=null&&leg!=index){result.add(points[index+1]);continue}
            val priorDistance=result.zipWithNext().sumOf{distance(it.first,it.second)}
            val a=points[index];val b=points[index+1];val distance=distance(a,b)
            if(distance>80_000)return PassagePlan(request.requestId,original,emptyList(),"该航段过长，请添加中间航点 / Add intermediate waypoints to this leg")
            // 第一遍只给直线附近一个小走廊，先快速回答“哪边有水”；只有被岛屿/半岛挡住
            // 才扩大搜索。旧逻辑一上来就用 0.75×航段长度的巨大矩形，奥克兰十几海里的
            // 航段会先装入一大片港湾细节，用户看到的就是几十秒等待。
            val fastPadding=max(1_500.0,distance*.20).coerceAtMost(8_000.0)
            val broadPadding=max(fastPadding,min(20_000.0,max(5_000.0,distance*.55)))
            val fallbackPadding=max(broadPadding,min(35_000.0,max(8_000.0,distance)))
            data class SearchAttempt(val padding:Double,val scale:Int?)
            val attempts=if(fastRaster) listOf(
                SearchAttempt(fastPadding,null),
                SearchAttempt(broadPadding,null),
                SearchAttempt(fallbackPadding,null)
            ).distinctBy{it.padding}
            else {
                val coarse=planningScaleForDistance(distance)
                listOf(
                    // 小窗口 + 匹配航段 tier：正常情况先快速出主走廊。
                    SearchAttempt(fastPadding,coarse),
                    // 若附近被岛屿/半岛挡住，只扩大粗搜索一次。
                    SearchAttempt(broadPadding,coarse),
                    // 粗层可能根本没有表达狭窄航道；先在直线附近用完整细节尝试一次，
                    // 保留 10–20 m 局部分辨率，而不是立刻把整片区域粗化。
                    SearchAttempt(fastPadding,null),
                    // 最后才扩大完整细节范围。
                    SearchAttempt(fallbackPadding,null)
                ).distinctBy{it.padding to it.scale}
            }

            var path:List<ChartPoint>?=null
            var acceptedWorld:PassageWorld?=null
            for((attemptIndex,attempt) in attempts.withIndex()) {
                currentCoroutineContext().ensureActive()
                progress(request.requestId,PassageJobPhase.LOADING,
                    ((index+(attemptIndex.toFloat()/attempts.size))/points.lastIndex).coerceIn(.08f,.9f))

                if(fastRaster) {
                    path=searchNumericRaster(snapshot,request,a,b,attempt.padding){fraction->
                        progress(request.requestId,PassageJobPhase.SEARCHING,(index+(attemptIndex+fraction)/attempts.size)/points.lastIndex)
                    }
                    if(path!=null)break
                    continue
                }

                val world=try {
                    geometry.world(
                        snapshot,request,listOf(a,b),attempt.padding,PassageWorldPurpose.REFERENCE_DRAFT,
                        preferredScaleDenominator=attempt.scale
                    ){fraction,detail->
                        progress(request.requestId,PassageJobPhase.LOADING,
                            (index+(attemptIndex+fraction*.6f)/attempts.size)/points.lastIndex,detail)
                    }
                }catch(cancel:CancellationException){throw cancel}
                catch(error:Exception){
                    // 粗尺度推断失败不终止；直接降级到完整细节。完整细节失败才交给外层报告。
                    if(attempt.scale!=null)continue else throw error
                }

                var found=geometry.search(world,a,b,request.vessel.turnRadiusMeters,smoothTurns=true){fraction->
                    progress(request.requestId,PassageJobPhase.SEARCHING,(index+(attemptIndex+fraction)/attempts.size)/points.lastIndex)
                }
                if(found==null)continue

                if(attempt.scale!=null) {
                    // Fine validation returns and repairs only conflicting spans; a valid long detour
                    // is no longer rejected merely because it looks "suspicious" geometrically.
                    found=geometry.refineCoarseRoute(snapshot,request,found)?:continue
                }

                path=found
                acceptedWorld=world
                break
            }

            // 仅对最终采用的矢量 world 记录参考深度问题；被细化淘汰的粗候选不能污染结果。
            val finalWorld=acceptedWorld
            if(path!=null&&finalWorld!=null&&finalWorld.referenceDatumFeatures.isNotEmpty()) {
                val line=finalWorld.projection.line(requireNotNull(path))
                val corridor=line.buffer(max(1.0,finalWorld.margin))
                val linear=org.locationtech.jts.linearref.LengthIndexedLine(line)
                for((feature,shape) in finalWorld.referenceDatumFeatures) {
                    currentCoroutineContext().ensureActive()
                    val cellKey="${feature.datasetId}/${feature.cellId}";val issueKey=index to cellKey
                    if(issueKey in referenceDepthIssues)continue
                    val hit=runCatching{OverlayNGRobust.overlay(shape,corridor,OverlayNG.INTERSECTION)}
                        .onFailure{if(it is CancellationException)throw it}.getOrNull()?:continue
                    if(hit.isEmpty)continue
                    referenceDepthIssues[issueKey]=PassageIssue(
                        passageHash(listOf(request.requestId,index,feature.id)),
                        PassageSeverity.INSUFFICIENT,PassageIssueKind.DEPTH,index,finalWorld.projection.point(hit.coordinate),
                        priorDistance+linear.project(hit.coordinate),
                        "此航段经过垂直基准未知的 LINZ 参考资料，不能确认实际水深、吃水与余深；仅供编辑草稿并核对正式海图 / This leg uses LINZ reference data with an unknown vertical datum. Actual depth, draft and under-keel clearance cannot be confirmed; use only as an editable draft and review official charts",
                        feature.id,feature.cellId,feature.depth
                    )
                }
            }
            val foundPath=path ?: return PassagePlan(request.requestId,original,emptyList(),
                "第 ${index+1} 段未找到满足当前资料与吃水条件的连续水路。可能是粗网格、资料缺口或搜索范围所限，不代表实际没有海路；可增加途经点或换用更精细资料 / Leg ${index+1} has no connected route under the selected data and draft constraints. Coarse cells, coverage gaps or search limits may hide a real waterway; add a waypoint or choose finer data")
            result.addAll(foundPath.drop(1))
        }
        require(result.size<=2000){"Candidate is too complex"}
        val turnRadius=request.vessel.turnRadiusMeters?.takeIf {it.isFinite()&&it>0}
        // 每个用户航段内部的自动拐点已按 turnRadius 平滑；用户明确设置的原始航点仍作为必须命中的逻辑目标保留。
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
        val final=planningCandidateAnalysis(snapshot,request,candidateRoute,turnRadius,referenceDepthIssues.values.toList())
        return PassagePlan(request.requestId,original,listOf(PassageCandidate(
            passageHash(candidateRoute),candidateRoute,final,final.distanceMeters-original.distanceMeters,targetIndices,draftOnly=true)))
    }
}

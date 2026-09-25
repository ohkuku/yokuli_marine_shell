package com.yokuli.runtime.marine.planning

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
import javax.inject.Inject
import javax.inject.Singleton
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
                if(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists())Document()
                else file.openRead().bufferedReader().use {reader->
                    val json=com.google.gson.JsonParser.parseReader(reader).asJsonObject
                    if(json.has("schema"))gson.fromJson(json,Document::class.java) else Document(state=gson.fromJson(json,PassageState::class.java))
                }
            }
            // Gson不会为旧文件缺失的非空集合执行Kotlin默认参数。
            val stored=loaded.copy(completed=loaded.completed.orEmpty(),state=loaded.state.copy(
                avoidances=loaded.state.avoidances.orEmpty(),reviews=loaded.state.reviews.orEmpty()))
            require(stored.schema==1&&stored.completed.size<=32&&stored.state.avoidances.size<=100&&stored.state.reviews.size<=128){"Unsupported passage workspace"}
            stored.state.avoidances.forEach(::validAvoidance)
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
                val bytes=gson.toJson(doc).toByteArray(Charsets.UTF_8)
                val output=file.startWrite()
                try {output.write(bytes);output.fd.sync();file.finishWrite(output)}catch(error:Throwable){file.failWrite(output);throw error}
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
        if(!commit(Pending(frozen,leg,planning)){it.copy(job=PassageJob(request.requestId,PassageJobPhase.LOADING),plan=null)})return@withLock
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
            snapshot=charts.acquireSnapshot(request.datasetIds.distinct())
            val original=geometry.analyze(snapshot,request){progress(request.requestId,PassageJobPhase.ANALYZING,it)}
            var plan:PassagePlan?=null
            if(planning){
                val v=request.vessel
                val missing=v.draftMeters==null||v.beamMeters==null||v.minimumUnderKeelMeters==null||v.clearanceMarginMeters==null||v.corridorHalfWidthMeters==null
                val invalid=snapshot.datasets.isEmpty()||snapshot.missingDatasetIds.isNotEmpty()||snapshot.datasets.any{!it.eligibility.allowsAnalysis(System.currentTimeMillis())||!it.offlineReadable||it.issue!=null}
                plan=when{
                    missing->PassagePlan(request.requestId,original,emptyList(),"先补齐船体和避让参数 / Complete vessel and clearance settings")
                    invalid->PassagePlan(request.requestId,original,emptyList(),"选用允许分析的数据集 / Select data with analysis permission")
                    else->createPlan(snapshot,request,original,leg)
                }
            }
            currentCoroutineContext().ensureActive()
            val completed=commit(completedId=request.requestId){
                if(it.job?.requestId!=request.requestId)it else it.copy(job=PassageJob(request.requestId,PassageJobPhase.COMPLETE,1f),analysis=original,plan=plan)
            }
            if(!completed)mutable.update{if(it.job?.requestId==request.requestId)it.copy(job=PassageJob(request.requestId,PassageJobPhase.FAILED,detail="计算结果尚未保存，请重试 / Result not saved; calculate again"))else it}
        }catch(e:CancellationException){throw e}catch(e:Exception){
            commit{if(it.job?.requestId==request.requestId)it.copy(job=PassageJob(request.requestId,PassageJobPhase.FAILED,detail=e.message?.take(250)?:"Unable to calculate"))else it}
        }finally{snapshot?.let{withContext(NonCancellable){runCatching{charts.releaseSnapshot(it.id)}}}}
    }
    private suspend fun createPlan(snapshot:ChartDataSnapshot,request:PassageRequest,original:PassageAnalysis,leg:Int?):PassagePlan {
        val points=request.route.points
        require(leg==null||leg in 0 until points.lastIndex){"Choose an existing leg"}
        val result=mutableListOf(points.first())
        for(index in 0 until points.lastIndex){
            currentCoroutineContext().ensureActive()
            if(leg!=null&&leg!=index){result.add(points[index+1]);continue}
            val a=points[index];val b=points[index+1];val distance=distance(a,b)
            if(distance>80_000)return PassagePlan(request.requestId,original,emptyList(),"该航段过长，请添加中间航点 / Add intermediate waypoints to this leg")
            val world=geometry.world(snapshot,request,listOf(a,b),max(2000.0,distance*.6).coerceAtMost(20_000.0))
            val path=geometry.search(world,a,b,request.vessel.turnRadiusMeters,smoothTurns=leg!=null){progress(request.requestId,PassageJobPhase.SEARCHING,(index+it)/points.lastIndex)}
                ?:return PassagePlan(request.requestId,original,emptyList(),if(request.vessel.turnRadiusMeters==null)"设置转弯半径后可生成绕行 / Set turning radius to generate detours" else "当前资料和搜索范围内未找到完整通路；可调整航点或资料 / No complete passage found within this search")
            result.addAll(path.drop(1))
        }
        require(result.size<=2000){"Candidate is too complex"}
        val candidatePoints=if(leg==null&&result.size>2){
            val world=geometry.world(snapshot,request,result,max(250.0,request.vessel.turnRadiusMeters?.times(3)?:250.0))
            geometry.smooth(world,result,request.vessel.turnRadiusMeters)
                ?:return PassagePlan(request.requestId,original,emptyList(),if(request.vessel.turnRadiusMeters==null)"设置转弯半径后可生成绕行 / Set turning radius to generate detours" else "无法满足转弯半径，请调整中间航点 / Turning radius cannot be met; adjust intermediate waypoints")
        }else result
        if(leg!=null&&result.size>2){
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
        val final=geometry.analyze(snapshot,request.copy(requestId=request.requestId+":candidate",route=candidateRoute)){progress(request.requestId,PassageJobPhase.ANALYZING,it)}
        if(final.issues.any{it.severity==PassageSeverity.CONFLICT||it.severity==PassageSeverity.INSUFFICIENT})return PassagePlan(request.requestId,original,emptyList(),"候选航线仍有冲突或资料缺口 / Candidate still has conflicts or missing evidence")
        return PassagePlan(request.requestId,original,listOf(PassageCandidate(passageHash(candidateRoute),candidateRoute,final,final.distanceMeters-original.distanceMeters,targetIndices)))
    }
}

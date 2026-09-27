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
            // 无数据/不可用数据在这里结束；不先扫完整航线，更不进入搜索或生成候选。
            val readiness=if(planning)planningReadiness(snapshot,request,leg)else null
            val original=if(readiness?.canSearch==false)readinessAnalysis(snapshot,request,readiness)
                else geometry.analyze(snapshot,request){progress(request.requestId,PassageJobPhase.ANALYZING,it)}
            val plan=if(planning){
                val v=request.vessel
                val missingDepth=v.draftMeters?.let{it.isFinite()&&it>0}!=true||v.minimumUnderKeelMeters?.let{it.isFinite()&&it>=0}!=true
                when {
                    readiness?.canSearch!=true->PassagePlan(request.requestId,original,emptyList(),readiness?.message)
                    missingDepth->PassagePlan(request.requestId,original,emptyList(),"先设置吃水和最小富余水深；船宽与走廊参数可稍后补充，粗略建议会保留复核提示 / Set draft and minimum under-keel clearance first; lateral vessel settings may be added later and coarse suggestions remain marked for review")
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
    /** 目录检查通过后，逐个计划搜索区域读取真实对象；不把覆盖元数据当作深度支持。 */
    private suspend fun planningReadiness(snapshot:ChartDataSnapshot,request:PassageRequest,leg:Int?):PassagePlanningReadiness {
        fun evaluate(evidence:PassagePlanningEvidence?=null)=PassagePlanningEligibility.evaluate(
            request.datasetIds,snapshot.datasets,System.currentTimeMillis(),snapshot.missingDatasetIds,evidence)
        val metadata=evaluate()
        if(!metadata.canRequestPlanning)return metadata
        val legs=if(leg==null)(0 until request.route.points.lastIndex).toList()else listOf(leg)
        for((order,index) in legs.withIndex()) {
            currentCoroutineContext().ensureActive()
            val start=request.route.points[index];val end=request.route.points[index+1]
            val length=distance(start,end)
            require(length<=80_000){"该航段过长，请添加中间航点 / Add intermediate waypoints to this leg"}
            val world=geometry.world(snapshot,request,listOf(start,end),max(2000.0,length*.75).coerceAtMost(40_000.0))
            val factory=world.projection.factory
            val endpoints=listOf(start,end).map{factory.createPoint(world.projection.xy(it))}
            val depths=world.features.filter { item ->
                val feature=item.feature;val depth=feature.depth
                feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&
                    feature.geometry.kind==ChartGeometryKind.POLYGON&&!feature.issues.any(::isBlockingChartIssue)&&
                    depth?.kind==DepthEvidenceKind.INTERVAL&&!depth.datum.isNullOrBlank()&&depth.lowerMeters?.isFinite()==true
            }.map{it.geometry}
            // GEBCO 是参考高程而非海图基准“可信深度面”。粗略搜索只要求端点落在
            // 已知数值地形（海、浅水或陆地）或正式深度面；UNKNOWN / NoData 仍阻断。
            // 陆地只表示“有地形证据”，真正通行性仍由 world.navigable 排除。
            val rasterTerrain=world.rasterAreas.filter {it.kind!=RasterPassageKind.UNKNOWN}.map {it.geometry}
            val searchableEvidence=union(depths+rasterTerrain,factory)
            val result=evaluate(PassagePlanningEvidence(
                endpoints.all{world.coverage.covers(it)},
                endpoints.all{searchableEvidence.covers(it)},
                world.malformed.isEmpty()
            ))
            if(!result.canSearch)return result
            progress(request.requestId,PassageJobPhase.LOADING,(order+1f)/legs.size)
        }
        // 长作业中用途许可可能刚好过期；最终进入分析/搜索前再检查冻结元数据的时间条件。
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
    private suspend fun createPlan(snapshot:ChartDataSnapshot,request:PassageRequest,original:PassageAnalysis,leg:Int?):PassagePlan {
        val points=request.route.points
        require(leg==null||leg in 0 until points.lastIndex){"Choose an existing leg"}
        val result=mutableListOf(points.first())
        for(index in 0 until points.lastIndex){
            currentCoroutineContext().ensureActive()
            if(leg!=null&&leg!=index){result.add(points[index+1]);continue}
            val a=points[index];val b=points[index+1];val distance=distance(a,b)
            if(distance>80_000)return PassagePlan(request.requestId,original,emptyList(),"该航段过长，请添加中间航点 / Add intermediate waypoints to this leg")
            val basePadding=max(2000.0,distance*.75).coerceAtMost(40_000.0)
            val paddings=listOf(basePadding,max(basePadding,min(60_000.0,max(8_000.0,distance*1.25)))).distinct()
            var path:List<ChartPoint>?=null
            for((attempt,padding) in paddings.withIndex()) {
                currentCoroutineContext().ensureActive()
                val world=geometry.world(snapshot,request,listOf(a,b),padding)
                path=geometry.search(world,a,b,request.vessel.turnRadiusMeters,smoothTurns=leg!=null){fraction->
                    progress(request.requestId,PassageJobPhase.SEARCHING,(index+(attempt+fraction)/paddings.size)/points.lastIndex)
                }
                if(path!=null)break
            }
            val foundPath=path ?: return PassagePlan(request.requestId,original,emptyList(),"当前资料和扩展搜索范围内未找到完整通路；请检查端点是否在可通行水域，或添加中间航点 / No complete passage was found in the available data and expanded search area; check that both endpoints are in navigable water or add an intermediate waypoint")
            result.addAll(foundPath.drop(1))
        }
        require(result.size<=2000){"Candidate is too complex"}
        val turnRadius=request.vessel.turnRadiusMeters?.takeIf {it.isFinite()&&it>0}
        val candidatePoints=if(leg==null&&result.size>2&&turnRadius!=null){
            val world=geometry.world(snapshot,request,result,max(250.0,turnRadius*3))
            geometry.smooth(world,result,turnRadius)
                ?:return PassagePlan(request.requestId,original,emptyList(),"无法满足转弯半径，请调整中间航点 / Turning radius cannot be met; adjust intermediate waypoints")
        }else result
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
        val checked=geometry.analyze(snapshot,request.copy(requestId=request.requestId+":candidate",route=candidateRoute)){progress(request.requestId,PassageJobPhase.ANALYZING,it)}
        // 缺船体参数只降低粗导航可信度，不应阻止“别穿陆地”的参考建议；资料空白和真实冲突仍阻断。
        val vesselRelaxed=checked.issues.map { issue ->
            if(issue.kind==PassageIssueKind.VESSEL&&issue.severity==PassageSeverity.INSUFFICIENT)
                issue.copy(severity=PassageSeverity.REVIEW,message=
                    "船体/净空参数未完整；当前建议只按已知水陆地形与可用资料粗略避让，请人工核对 / Vessel/clearance settings are incomplete; this suggestion only uses known terrain and available chart data for coarse avoidance and requires human review")
            else issue
        }
        if(vesselRelaxed.any{it.severity==PassageSeverity.CONFLICT||it.severity==PassageSeverity.INSUFFICIENT})
            return PassagePlan(request.requestId,original,emptyList(),"候选航线仍有冲突或资料缺口 / Candidate still has conflicts or missing evidence")
        val notes=buildList {
            if(turnRadius==null&&candidatePoints.size>2)add(PassageIssue(
                passageHash(listOf(checked.key,"rough-turns")),PassageSeverity.REVIEW,PassageIssueKind.GEOMETRY,0,
                candidatePoints.getOrNull(1),0.0,
                "粗略折线路线未应用转弯半径；请在海图上人工核对并按实际操船修正 / Coarse polyline route does not apply a turning radius; review it on the chart and adjust for actual vessel handling"
            ))
            if(request.vessel.draftMeters==null||request.vessel.minimumUnderKeelMeters==null)add(PassageIssue(
                passageHash(listOf(checked.key,"rough-depth")),PassageSeverity.REVIEW,PassageIssueKind.VESSEL,0,
                candidatePoints.firstOrNull(),0.0,
                "未设置吃水或最小富余水深；当前只按水陆地形粗略绕行，不判断实际余深 / Draft or minimum under-keel clearance is unset; this route only avoids terrain coarsely and does not assess real under-keel clearance"
            ))
        }
        val finalIssues=(vesselRelaxed+notes).distinctBy{it.id}
        val finalSeverity=when {
            finalIssues.any{it.severity==PassageSeverity.CONFLICT}->PassageSeverity.CONFLICT
            finalIssues.any{it.severity==PassageSeverity.INSUFFICIENT}->PassageSeverity.INSUFFICIENT
            finalIssues.any{it.severity==PassageSeverity.REVIEW}->PassageSeverity.REVIEW
            else->PassageSeverity.NO_CONFLICT_FOUND
        }
        val final=checked.copy(severity=finalSeverity,issues=finalIssues)
        return PassagePlan(request.requestId,original,listOf(PassageCandidate(passageHash(candidateRoute),candidateRoute,final,final.distanceMeters-original.distanceMeters,targetIndices)))
    }
}

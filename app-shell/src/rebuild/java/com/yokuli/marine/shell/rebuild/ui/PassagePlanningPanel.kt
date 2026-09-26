package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.contract.navigation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

private const val LIBRARY_DATA_CONTEXT="library-navigation-data-v1"
private fun signature(value:Any)=MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()).take(16).joinToString(""){"%02x".format(it)}
private fun GeoPoint.chartPoint()=ChartPoint(lat,lon)
private fun ChartPoint.geo()=GeoPoint(latitude,longitude)
private fun OsStore.passageVessel():PassageVessel?=marine?.services?.state?.value?.vesselSettings?.let{v->PassageVessel(v.draftMeters,v.beamMeters,v.airDraftMeters,v.minimumUnderKeelMeters,v.clearanceMarginMeters,v.corridorHalfWidthMeters,v.turnRadiusMeters,v.plannedSpeedMetersPerSecond)}

/** 只有这一份航线草稿；检查面板不再拥有起终点或另外选择的路线。 */
internal fun passagePlanningContext(os:OsStore):String=signature(listOf(os.editingRouteId,os.draftRoute,os.draftNavigationTargetIndices))

/** 计算只取图册启用的数据。背景是呈现偏好，不构成计算依据，也不使结果失效。 */
internal fun OsStore.planningRequest(points:List<GeoPoint>,id:String,name:String,targetIndices:List<Int>?=null):PassageRequest? {
    val system=marine?.system?:return null
    return PassageRequest(uid(),PassageRoute(id,signature(listOf(points,targetIndices)),name,points.map{it.chartPoint()},targetIndices),
        maps.selectedDatasetIds.toList(),passageVessel()?:return null,departureUtc=System.currentTimeMillis(),
        avoidances=system.analysis.state.value.avoidances,backgroundKey=LIBRARY_DATA_CONTEXT)
}
internal fun draftMatchesAnalysis(os:OsStore,result:PassageAnalysis?,data:ChartDataState,now:Long):Boolean {
    if(result==null||data.loading||data.error!=null||!os.editingRoute)return false
    val ids=os.maps.selectedDatasetIds
    val revisions=ids.mapNotNull{id->data.datasets.firstOrNull{it.id==id}?.let{it.id to it.revision}}.toMap()
    return result.request.route.points==os.draftRoute.map{it.chartPoint()}&&
        result.request.route.navigationTargetIndices==os.draftNavigationTargetIndices&&
        result.request.vessel==os.passageVessel()&&result.request.datasetIds==ids&&
        result.request.backgroundKey==LIBRARY_DATA_CONTEXT&&result.datasetRevisions==revisions&&
        result.request.avoidances==os.marine?.system?.analysis?.state?.value?.avoidances&&
        (result.severity==PassageSeverity.INSUFFICIENT||ids.all{id->data.datasets.firstOrNull{it.id==id}?.let{
            it.offlineReadable&&it.issue==null&&it.eligibility.allowsAnalysis(now)}==true})
}
internal fun draftCalculationBusy(os:OsStore):Boolean=os.marine?.system?.analysis?.state?.value?.job?.phase in
    setOf(PassageJobPhase.LOADING,PassageJobPhase.SEARCHING,PassageJobPhase.ANALYZING)

/** 返回拒绝原因；成功只启动现有作业，不采用结果、不开始导航。 */
internal fun requestDraftCalculation(os:OsStore,plan:Boolean,leg:Int?=null):String? {
    val system=os.marine?.system?:return os.t("航行服务仍在启动","Marine services are starting")
    if(!os.editingRoute||os.draftRoute.size<2)return os.t("在地图上添加起点和终点","Add a start and destination on the map")
    if(!system.analysis.state.value.ready||draftCalculationBusy(os))return os.t("请等当前计算完成","Wait for the current calculation")
    val data=system.charts.state.value
    if(data.loading||data.error!=null)return os.t("数据目录尚未就绪，请在图册检查","The data library is not ready; check Library")
    val ready=PassagePlanningEligibility.evaluate(os.maps.selectedDatasetIds,data.datasets,System.currentTimeMillis())
    if(plan&&!ready.canRequestPlanning)return os.t("图册中的航行数据尚不满足自动生成条件；仍可手动编辑。","Navigation data in Library is not ready for automatic routing. Manual editing remains available.")
    val request=os.planningRequest(os.draftRoute.toList(),os.editingRouteId?:"draft",
        os.routes.firstOrNull{it.id==os.editingRouteId}?.name?:os.t("当前航线","Current route"),os.draftNavigationTargetIndices)
        ?:return os.t("船舶参数尚未就绪","Boat settings are not ready")
    if(plan)system.planning.plan(request,leg)else system.analysis.analyze(request)
    return null
}

/** 地图停止改线后检查同一草稿；切换背景不会触发新的深度计算。 */
@Composable internal fun DraftPassageAnalysis(os:OsStore) {
    val system=os.marine?.system?:return
    val vessel by system.services.state.collectAsState()
    val data by system.charts.state.collectAsState()
    val workspace by system.analysis.state.collectAsState()
    val points=os.draftRoute
    LaunchedEffect(os.editingRoute,points,os.draftNavigationTargetIndices,os.maps.selectedDatasetIds,data.revision,vessel.vesselSettings,workspace.avoidances) {
        if(!os.editingRoute)return@LaunchedEffect
        val view=os.maps.view("chart",os.center,os.zoom)
        view.planningLines=emptyList();view.planningPoints=emptyList()
        // 草稿恢复与正式保存分开；自动保留不清空草稿，也不显示“航线已保存”。
        delay(900)
        if(os.persistenceState.value.readFailure==null)os.save()
        if(points.size<2||os.maps.selectedDatasetIds.isEmpty()||draftCalculationBusy(os))return@LaunchedEffect
        val current=system.analysis.state.value
        val now=System.currentTimeMillis()
        if(draftMatchesAnalysis(os,current.analysis,data,now)||current.plan?.candidates?.any{draftMatchesAnalysis(os,it.analysis,data,now)}==true)return@LaunchedEffect
        requestDraftCalculation(os,false)
    }
}

private fun issueText(os:OsStore,text:String):String {
    if(text.contains(" / "))return text.split(" / ").let {if(os.chinese)it.first()else it.last()}
    if(!os.chinese)return text
    return when(text){
        "Choose at least two points"->"先在地图添加起点和终点"
        "Invalid route coordinates"->"路线中有无效坐标，请重新选点"
        "Chart query is incomplete","Chart query cursor did not advance"->"航行资料读取不完整，请在图册更新或重新导入"
        "Area contains too many chart objects; use a shorter passage"->"本区域资料过多，请分段规划"
        "Incomplete polygon","Missing outer boundary","Unattached polygon hole","Invalid chart polygon"->"资料区域边界不完整，请在图册检查"
        "Choose an existing leg"->"所选航段已变化，请重新选择"
        "Candidate is too complex"->"方案过于复杂，请分段规划"
        else->text
    }
}
private fun severityColor(level:PassageSeverity)=when(level){PassageSeverity.CONFLICT->Color(0xFFD84742);PassageSeverity.REVIEW->Color(0xFFEBA13A);PassageSeverity.INSUFFICIENT->Color(0xFF81909B);PassageSeverity.NO_CONFLICT_FOUND->Color(0xFF258A83)}
internal fun draftVerdict(os:OsStore,level:PassageSeverity)=when(level){PassageSeverity.CONFLICT->os.t("发现冲突","Conflicts found");PassageSeverity.REVIEW->os.t("需要核对","Review needed");PassageSeverity.INSUFFICIENT->os.t("资料不足","More data needed");PassageSeverity.NO_CONFLICT_FOUND->os.t("已检查条件下未发现冲突","No conflict in checked conditions")}

/** 当前编辑器的检查与建议面板，不是第二个规划器；来源只提供图册修复入口。 */
@Composable fun PassagePlanningPanel(os:OsStore,onDismiss:()->Unit) {
    val system=os.marine?.system?:return
    val state by system.analysis.state.collectAsState()
    val data by system.charts.state.collectAsState()
    val marine by system.services.state.collectAsState()
    val persistence by os.persistenceState.collectAsState()
    val tick=rememberMarineClock()
    val now=remember(tick){System.currentTimeMillis()}
    val points=os.draftRoute
    val original=state.analysis
    val current=original?.takeIf{draftMatchesAnalysis(os,it,data,now)}
        ?:state.plan?.candidates?.firstOrNull{draftMatchesAnalysis(os,it.analysis,data,now)}?.analysis
    val busy=draftCalculationBusy(os)
    val readiness=PassagePlanningEligibility.evaluate(os.maps.selectedDatasetIds,data.datasets,now)
    var leg by rememberSaveable {mutableStateOf<Int?>(null)}
    var chooseLeg by rememberSaveable {mutableStateOf(false)}
    var advanced by rememberSaveable {mutableStateOf(false)}
    var reviewing by rememberSaveable {mutableStateOf(false)}
    var issueLimit by rememberSaveable {mutableIntStateOf(5)}
    var saving by remember {mutableStateOf(false)}
    var feedback by remember {mutableStateOf<String?>(null)}
    var command by remember {mutableStateOf<NavigationCommand?>(null)}
    var choosingAvoidance by remember {mutableStateOf(false)}
    var avoidanceCenter by remember {mutableStateOf<GeoPoint?>(null)}
    var avoidanceName by rememberSaveable {mutableStateOf(os.t("避让区域","Avoidance area"))}
    val avoidanceRadius=rememberUnitNumberDraft(100.0,os.distanceUnitLabel,os::distanceValue,os::distanceMeters,"avoidance")
    val view=os.maps.view("chart",os.center,os.zoom)
    val canWrite=!saving&&!persistence.saving&&persistence.readFailure==null
    fun candidateUsable(candidate:PassageCandidate):Boolean {
        val latestData=system.charts.state.value
        val latest=system.analysis.state.value
        val time=System.currentTimeMillis()
        return !draftCalculationBusy(os)&&latest.plan?.candidates?.any{it.analysis.key==candidate.analysis.key}==true&&
            latest.plan?.original?.key==latest.analysis?.key&&draftMatchesAnalysis(os,latest.analysis,latestData,time)&&
            PassagePlanningEligibility.evaluate(os.maps.selectedDatasetIds,latestData.datasets,time).canRequestPlanning&&
            latest.planningReadiness?.canSearch!=false&&candidate.analysis.complete&&
            candidate.analysis.severity in setOf(PassageSeverity.REVIEW,PassageSeverity.NO_CONFLICT_FOUND)&&
            candidate.analysis.datasetRevisions==latest.analysis?.datasetRevisions
    }
    fun persistDraft(message:String,close:Boolean=false) {
        saving=true
        val receipt=os.save()
        os.scope.launch {
            try {
                val saved=receipt.result.await()==DurableCommitResult.SAVED
                feedback=if(saved)message else os.t("草稿尚未保存到设备，请重试。","The draft is not saved on this device. Retry.")
                if(saved&&close)onDismiss()
            } finally {saving=false}
        }
    }
    fun locate(issue:PassageIssue) {
        when(issue.kind) {
            PassageIssueKind.VESSEL->os.openLinked("settings:vessel")
            PassageIssueKind.DATA->os.openLinked("library:data")
            else->issue.point?.let {p->view.planningPoints=listOf(MapPoint("planning:issue",p.geo(),"",severityColor(issue.severity).toArgb().toLong(),10f));os.fly(p.geo(),os.zoom.coerceAtLeast(14.0));onDismiss()}
        }
    }
    LaunchedEffect(current?.key){reviewing=false}
    if(reviewing)current?.let{PassageReviewDialog(os,it){reviewing=false}}
    if(choosingAvoidance)MapPicker(os,os.center,onConfirm={avoidanceCenter=it;choosingAvoidance=false},onCancel={choosingAvoidance=false})
    command?.let {pending->ConfirmDialog(os,os.t("用这个方案替换当前导航？收藏航线不会因此改变。","Replace active navigation with this candidate? Saved routes stay unchanged."),{command=null}) {
        command=null
        val candidate=system.analysis.state.value.plan?.candidates?.firstOrNull{it.analysis.key==pending.analysisReference}
        if(candidate==null||!candidateUsable(candidate))feedback=os.t("航线或资料已变化，请重新计算。","Route or data changed. Recalculate.")
        else {saving=true;os.scope.launch {try {
            val receipt=os.commitNavigation(pending)
            feedback=if(receipt.result==NavigationResult.SAVED)os.t("当前导航已更新","Navigation updated")else navigationFailure(os,receipt)
        }finally {saving=false}}}
    }}
    AppDialog(onDismissRequest={if(!saving)onDismiss()}) {AppDialogSurface {
        AppDialogTitle(os.t("航线检查与建议","Route checks & suggestions"))
        Label(os.t("当前地图中的同一条航线 · ${points.size} 个路径点","The route on your map · ${points.size} path points"),14,LocalMetro.current.muted)
        Label(os.t("自动使用图册启用的航行数据；海图背景不参与计算。","Uses navigation data enabled in Library. The chart background is not used for calculation."),13,LocalMetro.current.muted)
        if(!readiness.canRequestPlanning||data.loading||data.error!=null) {
            Label(os.t("航行资料尚未就绪，手动编辑不受影响。","Navigation data is not ready. You can still edit manually."),14)
            MenuRow(os.t("到图册检查数据","Check data in Library"),os.t("配置一次，所有航线共用","Configure once for all routes"),"folder") {os.openLinked("library:data")}
        }
        if(points.size<2)Label(os.t("返回地图添加起点和终点。","Return to the map and add a start and destination."),15)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MetroButton(os.t("检查当前航线","Check this route"),{feedback=requestDraftCalculation(os,false)},Modifier.weight(1f),enabled=os.editingRoute&&points.size>=2&&!busy&&state.ready)
            MetroButton(os.t("自动生成建议","Suggest a route"),{feedback=requestDraftCalculation(os,true,leg)},Modifier.weight(1f),primary=true,enabled=os.editingRoute&&points.size>=2&&!busy&&state.ready&&readiness.canRequestPlanning&&!data.loading&&data.error==null)
        }
        if(busy) {MetroProgress(os.t("正在计算…","Calculating…"));MetroButton(os.t("取消计算","Cancel calculation"),{state.job?.requestId?.let(system.analysis::cancel)})}
        state.job?.takeIf{it.phase in setOf(PassageJobPhase.FAILED,PassageJobPhase.INTERRUPTED,PassageJobPhase.CANCELLED)}?.let {job->
            Label(issueText(os,job.detail?:"计算未完成 / Calculation did not finish"),14)
        }
        if(current==null&&original!=null)Label(os.t("上次结果不适用于当前航线或资料，请重新检查。","The previous result does not apply to this route or its data. Recheck."),14,LocalMetro.current.muted)
        current?.let {result->
            AppSection(draftVerdict(os,result.severity))
            Label(os.formatDistance(result.distanceMeters)+result.arrivalUtc?.let{" · "+os.t("计划抵达 ","Planned arrival ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it))}.orEmpty(),15)
            if(result.complete)PassageEvidenceStrip(os,result){along->
                val routePoints=result.request.route.points
                var remaining=along;var chosen=routePoints.lastOrNull()
                for((a,b) in routePoints.zipWithNext()){val length=distance(a.geo(),b.geo());if(remaining<=length){chosen=destination(a.geo(),remaining,bearing(a.geo(),b.geo())).chartPoint();break};remaining-=length}
                chosen?.let{p->view.planningPoints=listOf(MapPoint("planning:cursor",p.geo(),"",os.accent,8f));os.fly(p.geo(),os.zoom.coerceAtLeast(13.0));onDismiss()}
            }
            result.issues.take(issueLimit).forEach {issue->MenuRow(issueText(os,issue.message),os.t("第 ${issue.legIndex+1} 段 · ","Leg ${issue.legIndex+1} · ")+os.formatDistance(issue.alongMeters)){locate(issue)}}
            if(result.issues.size>issueLimit)MenuRow(os.t("展开其余问题","More issues")){issueLimit+=15}
            if(result.issues.isNotEmpty())MenuRow(os.t("核对记录","Review notes")){reviewing=true}
            Label(os.t("不含潮汐或天气修正；未发现冲突不代表保证可通航。","No tide or weather adjustment. No conflict found is not a guarantee of safe passage."),13,LocalMetro.current.muted)
        }
        state.plan?.let {plan->
            plan.reason?.let{Label(issueText(os,it),14)}
            plan.candidates.forEach {candidate->
                val usable=candidateUsable(candidate)
                AppSection(os.t("建议路线","Suggested route"))
                Label(os.formatDistance(candidate.analysis.distanceMeters),20,LocalMetro.current.accentText)
                Label(draftVerdict(os,candidate.analysis.severity),14,LocalMetro.current.muted)
                if(!usable)Label(os.t("需要重新计算，或该方案不满足采用条件。","Recalculate, or resolve the conditions preventing adoption."),13,LocalMetro.current.muted)
                MetroButton(os.t("在当前地图比较","Compare on this map"),{
                    view.planningLines=listOf(MapLine("planning:original",plan.original.request.route.points.map{it.geo()},0xFF7E8995,3f),MapLine("planning:candidate",candidate.route.points.map{it.geo()},os.accent,4f))
                    os.fitRequest=(plan.original.request.route.points+candidate.route.points).map{it.geo()};onDismiss()
                },enabled=usable)
                MetroButton(os.t("采用并继续编辑","Use and continue editing"),{
                    if(!candidateUsable(candidate)||!canWrite)return@MetroButton
                    val proposed=candidate.route.points.map{it.geo()}
                    os.planningDraftUndo=PlanningDraftUndo(os.draftRoute,os.editingRouteId,os.editingRoute,proposed,os.editingRouteId,os.draftNavigationTargetIndices,candidate.navigationTargetIndices)
                    os.draftRoute=proposed;os.draftNavigationTargetIndices=candidate.navigationTargetIndices;os.showCrosshair=true
                    view.planningLines=emptyList();view.planningPoints=emptyList();os.fitRequest=proposed
                    persistDraft(os.t("已采用建议，继续编辑同一条航线","Suggestion applied to the same route"),close=true)
                },primary=true,enabled=usable&&canWrite)
                if(os.editingRouteId!=null&&os.editingRouteId==os.activeRouteId&&os.navigationState.session?.source==NavigationSource.LOCAL)MetroButton(os.t("替换当前导航…","Replace active navigation…"),{
                    if(!candidateUsable(candidate)||!canWrite)return@MetroButton
                    val route=Route(candidate.route.id,candidate.route.name,candidate.route.points.map{it.geo()},candidate.navigationTargetIndices)
                    command=os.navigationCommand(NavigationAction.REPLAN,route=route.navigationSnapshot(),targetIndex=candidate.navigationTargetIndices?.firstOrNull()?:1,analysisReference=candidate.analysis.key)
                },enabled=usable&&canWrite)
            }
        }
        os.planningDraftUndo?.let {undo->
            val applicable=os.editingRoute&&os.draftRoute==undo.appliedPoints&&os.editingRouteId==undo.appliedRouteId&&os.draftNavigationTargetIndices==undo.appliedNavigationTargetIndices
            if(applicable)MetroButton(os.t("撤销采用建议","Undo suggestion"),{
                if(os.draftRoute!=undo.appliedPoints||os.editingRouteId!=undo.appliedRouteId||os.draftNavigationTargetIndices!=undo.appliedNavigationTargetIndices)return@MetroButton
                os.draftRoute=undo.points;os.editingRouteId=undo.routeId;os.draftNavigationTargetIndices=undo.previousNavigationTargetIndices;os.planningDraftUndo=null
                persistDraft(os.t("已恢复之前的航线","Previous route restored"),close=true)
            },enabled=canWrite)
        }
        if(persistence.failed||state.storageIssue!=null) {
            Label(os.t("存在未保存的草稿或计算记录，请重试。","Some draft or calculation records are not saved. Retry."),14)
            MetroButton(os.t("重试保存草稿","Retry saving draft"),{persistDraft(os.t("草稿已保留","Draft retained"))},enabled=canWrite)
        }
        if(persistence.readFailure!=null) {ContentRecoveryStatus(os,showRecoveredCopy=false)}
        MenuRow(os.t("更多规划选项","More route options"),if(advanced)os.t("收起","Hide")else null){advanced=!advanced}
        if(advanced) {
            MenuRow(os.t("船舶与余量参数","Boat and clearance settings"),marine.vesselSettings.vesselName){os.openLinked("settings:vessel")}
            if(points.size>2)MenuRow(leg?.let{os.t("仅绕行第 ${it+1} 段","Detour leg ${it+1}")}?:os.t("生成整条航线","Generate the whole route")){chooseLeg=!chooseLeg}
            if(chooseLeg) {
                ChoiceRow(os.t("整条航线","Whole route"),leg==null){leg=null;chooseLeg=false}
                (0 until points.lastIndex).take(200).forEach{index->ChoiceRow(os.t("第 ${index+1} 段","Leg ${index+1}"),leg==index){leg=index;chooseLeg=false}}
            }
            MenuRow(os.t("添加避让区","Add avoidance area")){choosingAvoidance=true}
            avoidanceCenter?.let {center->
                Field(os.t("名称","Name"),avoidanceName,{avoidanceName=it.take(100)})
                Field(os.t("范围半径","Radius")+" · "+os.distanceUnitLabel,avoidanceRadius.text,{avoidanceRadius.edit(it)},number=true)
                MetroButton(os.t("保存避让区","Save avoidance"),{
                    val radius=avoidanceRadius.value?:return@MetroButton
                    val area=PassageAvoidance(uid(),avoidanceName,(0 until 72).map{destination(center,radius,it*5.0).chartPoint()})
                    saving=true;os.scope.launch {try {system.analysis.saveAvoidance(area);avoidanceCenter=null}
                        catch(cancel:CancellationException){throw cancel}catch(_:Exception){feedback=os.t("避让区未保存","Avoidance not saved")}finally{saving=false}}
                },enabled=!saving&&avoidanceName.isNotBlank()&&avoidanceRadius.value?.let{it in 1.0..50000.0}==true)
            }
            state.avoidances.forEach {area->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MetroButton(area.name,{view.planningAreas=listOf(MapArea("avoidance:${area.id}",area.boundary.map{it.geo()},0x558C448F));os.fitRequest=area.boundary.map{it.geo()};onDismiss()},Modifier.weight(1f))
                IconAction("delete",os.t("移除避让区","Remove avoidance"),{os.scope.launch {try {system.analysis.removeAvoidance(area.id)}catch(cancel:CancellationException){throw cancel}catch(_:Exception){feedback=os.t("未能移除","Could not remove")}}})
            }}
        }
        if(saving||persistence.saving)MetroProgress(os.t("正在保存…","Saving…"))
        feedback?.let{Label(it,14)}
        MetroButton(os.t("返回航线编辑","Back to route editing"),onDismiss,enabled=!saving)
    }}
}

/** 上轨为覆盖，下轨画实际深度区间；测深点独立显示，空白不补线。 */
@Composable private fun PassageEvidenceStrip(os:OsStore,result:PassageAnalysis,onPick:(Double)->Unit) {
    val c=LocalMetro.current;val depthValues=result.strip.flatMap{listOfNotNull(it.depth?.lowerMeters,it.depth?.upperMeters,it.depth?.pointMeters)}.filter{it.isFinite()}
    val maxDepth=depthValues.maxOrNull()?.coerceAtLeast(1.0)?:1.0
    Canvas(Modifier.fillMaxWidth().height(112.dp).pointerInput(result.id){detectTapGestures{p->onPick((p.x/size.width).coerceIn(0f,1f)*result.distanceMeters)}}){
        val length=result.distanceMeters.coerceAtLeast(1.0)
        fun x(v:Double)=(v/length*size.width).toFloat()
        fun y(v:Double)=(26.dp.toPx()+v.coerceAtLeast(0.0)/maxDepth*(size.height-30.dp.toPx())).toFloat()
        drawRect(c.muted.copy(alpha=.12f));drawLine(c.muted,Offset(0f,20.dp.toPx()),Offset(size.width,20.dp.toPx()),1.dp.toPx())
        result.strip.forEach{s->
            if(s.covered)drawLine(c.accent.copy(alpha=.6f),Offset(x(s.fromMeters),9.dp.toPx()),Offset(x(s.toMeters),9.dp.toPx()),5.dp.toPx())
            val d=s.depth
            if(d?.kind==DepthEvidenceKind.POINT)d.pointMeters?.let{drawCircle(c.accent,2.5.dp.toPx(),Offset(x(s.fromMeters),y(it)))}
            else if(d?.kind==DepthEvidenceKind.INTERVAL&&d.lowerMeters!=null){val top=y(d.lowerMeters ?: return@forEach);val bottom=y(d.upperMeters?:maxDepth);drawRect(c.accent.copy(alpha=.22f),Offset(x(s.fromMeters),top),androidx.compose.ui.geometry.Size((x(s.toMeters)-x(s.fromMeters)).coerceAtLeast(1f),(bottom-top).coerceAtLeast(2f)))}
        }
        result.issues.forEach{drawCircle(severityColor(it.severity),3.dp.toPx(),Offset(x(it.alongMeters),9.dp.toPx()))}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Label(os.formatDistance(0.0),12,c.muted);Label(os.formatDistance(result.distanceMeters),12,c.muted)}
    Label(os.t("上：覆盖与问题　下：深度区间／独立测深点","Top: coverage & issues · Below: depth intervals / soundings"),12,c.muted)
    Label(os.t("深度范围 ","Depth range ")+os.formatDepth(0.0)+" – "+os.formatDepth(maxDepth),12,c.muted)
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

private val planningPointSaver=listSaver<GeoPoint?,Double>(save={it?.let { p -> listOf(p.lat,p.lon) }.orEmpty()},restore={if(it.size==2)GeoPoint(it[0],it[1])else null})
private fun signature(value:Any)=MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()).take(16).joinToString(""){"%02x".format(it)}
private fun GeoPoint.chartPoint()=ChartPoint(lat,lon)
private fun ChartPoint.geo()=GeoPoint(latitude,longitude)
private fun OsStore.passageVessel():PassageVessel?=marine?.services?.state?.value?.vesselSettings?.let{v->PassageVessel(v.draftMeters,v.beamMeters,v.airDraftMeters,v.minimumUnderKeelMeters,v.clearanceMarginMeters,v.corridorHalfWidthMeters,v.turnRadiusMeters,v.plannedSpeedMetersPerSecond)}
private fun OsStore.backgroundKey()=when(val source=maps.source){MapSource.Offline->"offline";MapSource.Satellite->"satellite";is MapSource.CustomLayer->"custom:${source.layerId}:${library.revision}"}
/** 输入属于一次海图访问；切到地图比较不丢失，外部换了草稿/航线则不套用旧输入。 */
internal fun passagePlanningContext(os:OsStore):String {
    val route=chartRoute(os)
    return signature(listOf(os.editingRoute,os.editingRouteId,if(os.editingRoute)os.draftRoute else route?.points,
        if(os.editingRoute)os.draftNavigationTargetIndices else route?.navigationTargetIndices,route?.id))
}
internal fun OsStore.planningRequest(points:List<GeoPoint>,id:String,name:String,targetIndices:List<Int>?=null):PassageRequest? {
    val system=marine?.system?:return null
    return PassageRequest(uid(),PassageRoute(id,signature(listOf(points,targetIndices)),name,points.map{it.chartPoint()},targetIndices),maps.selectedDatasetIds,passageVessel()?:return null,departureUtc=System.currentTimeMillis(),avoidances=system.analysis.state.value.avoidances,backgroundKey=backgroundKey())
}
private fun issueText(os:OsStore,text:String):String {
    if(text.contains(" / "))return text.split(" / ").let {if(os.chinese)it.first()else it.last()}
    if(!os.chinese)return text
    return when(text){
        "Choose at least two points"->"先选择起点和终点"
        "Invalid route coordinates"->"路线中有无效坐标，请重新选点"
        "Chart query is incomplete","Chart query cursor did not advance"->"海图资料读取不完整，请更新或重新导入"
        "Area contains too many chart objects; use a shorter passage"->"本区域资料过多，请分段规划"
        "Incomplete polygon","Missing outer boundary","Unattached polygon hole","Invalid chart polygon"->"海图区域边界不完整，请检查数据集"
        "Choose an existing leg"->"所选航段已变化，请重新选择"
        "Candidate is too complex"->"方案过于复杂，请分段规划"
        else->text
    }
}
private fun severityColor(level:PassageSeverity)=when(level){PassageSeverity.CONFLICT->Color(0xFFD84742);PassageSeverity.REVIEW->Color(0xFFEBA13A);PassageSeverity.INSUFFICIENT->Color(0xFF81909B);PassageSeverity.NO_CONFLICT_FOUND->Color(0xFF258A83)}
private fun verdict(os:OsStore,level:PassageSeverity)=when(level){PassageSeverity.CONFLICT->os.t("有冲突","Conflicts found");PassageSeverity.REVIEW->os.t("需要核对","Review needed");PassageSeverity.INSUFFICIENT->os.t("资料不足","More information needed");PassageSeverity.NO_CONFLICT_FOUND->os.t("已检查条件下未发现冲突","No conflict in checked conditions")}

/** 草稿停止变化后才完整检查。移动中不重复整区查询，作业与页面生命周期分离。 */
@Composable internal fun DraftPassageAnalysis(os:OsStore) {
    val system=os.marine?.system?:return
    val vessel by system.services.state.collectAsState()
    val data by system.charts.state.collectAsState()
    val points=os.draftRoute
    LaunchedEffect(os.editingRoute,points,os.draftNavigationTargetIndices,os.maps.selectedDatasetIds,data.revision,vessel.vesselSettings,os.maps.source,os.library.revision) {
        if(!os.editingRoute||points.size<2||os.maps.selectedDatasetIds.isEmpty())return@LaunchedEffect
        delay(900)
        val current=system.analysis.state.value
        if(current.job?.phase in setOf(PassageJobPhase.LOADING,PassageJobPhase.SEARCHING,PassageJobPhase.ANALYZING))return@LaunchedEffect
        // 仅保留仍与当前输入和资料版本相符的比较；手工改线不能被旧候选永久挡住。
        val previous=current.analysis
        val geometry=points.map{it.chartPoint()}
        val indices=os.draftNavigationTargetIndices
        val sameInput=previous?.request?.route?.let{it.points==geometry&&it.navigationTargetIndices==indices}==true ||
            current.plan?.candidates?.any{it.route.points==geometry&&it.navigationTargetIndices==indices}==true
        val sameConditions=previous!=null&&previous.request.vessel==os.passageVessel()&&previous.request.datasetIds==os.maps.selectedDatasetIds&&
            previous.request.backgroundKey==os.backgroundKey()&&previous.request.avoidances==current.avoidances&&
            previous.datasetRevisions.all{(id,revision)->data.datasets.firstOrNull{it.id==id}?.let{it.revision==revision&&it.offlineReadable&&it.issue==null&&it.eligibility.allowsAnalysis(System.currentTimeMillis())}==true}
        if(current.plan?.candidates?.isNotEmpty()==true&&current.job?.phase==PassageJobPhase.COMPLETE&&sameInput&&sameConditions)return@LaunchedEffect
        os.planningRequest(points,os.editingRouteId?:"draft",os.t("当前草稿","Current draft"),os.draftNavigationTargetIndices)?.let(system.analysis::analyze)
    }
}

@Composable fun PassagePlanningPanel(os:OsStore,onDismiss:()->Unit) {
    val system=os.marine?.system?:return
    val state by system.analysis.state.collectAsState()
    val chartData by system.charts.state.collectAsState()
    val marineState by system.services.state.collectAsState()
    val persistence by os.persistenceState.collectAsState()
    val tick=rememberMarineClock()
    val wallNow=remember(tick){System.currentTimeMillis()}
    val currentRoute=chartRoute(os)
    val initial=if(os.editingRoute)os.draftRoute else currentRoute?.points.orEmpty()
    var from by rememberSaveable(stateSaver=planningPointSaver) {mutableStateOf(initial.firstOrNull())}
    var to by rememberSaveable(stateSaver=planningPointSaver) {mutableStateOf(initial.lastOrNull())}
    var useExisting by rememberSaveable {mutableStateOf(initial.size>=2)}
    var activeRemainder by rememberSaveable {mutableStateOf(false)}
    var choosing by remember {mutableStateOf<String?>(null)}
    var chooseData by remember {mutableStateOf(false)}
    var chooseRoute by remember {mutableStateOf(false)}
    var pickedRouteId by rememberSaveable {mutableStateOf<String?>(null)}
    var recoveredRouteJson by rememberSaveable {mutableStateOf<String?>(null)}
    val pickedRoute=os.routes.firstOrNull { it.id==pickedRouteId } ?: recoveredRouteJson?.let {runCatching {Route.from(org.json.JSONObject(it))}.getOrNull()}
    var avoidanceName by rememberSaveable {mutableStateOf(os.t("避让区域","Avoidance area"))}
    var avoidanceCenter by rememberSaveable(stateSaver=planningPointSaver) {mutableStateOf<GeoPoint?>(null)}
    val avoidanceRadius=rememberUnitNumberDraft(100.0,os.distanceUnitLabel,os::distanceValue,os::distanceMeters,"avoidance")
    var selectedLeg by rememberSaveable {mutableStateOf<Int?>(null)}
    val undo=os.planningDraftUndo
    var replanCommand by remember {mutableStateOf<NavigationCommand?>(null)}
    var issueLimit by remember {mutableStateOf(5)}
    var reviewing by rememberSaveable {mutableStateOf(false)}
    var saving by remember {mutableStateOf(false)}
    var feedback by remember {mutableStateOf<String?>(null)}
    val view=os.maps.view("chart",os.center,os.zoom)
    val template=if(activeRemainder)listOfNotNull(from)+os.activeRoute?.points.orEmpty().drop(os.routeLeg) else if(useExisting)pickedRoute?.points?:if(os.editingRoute)os.draftRoute else currentRoute?.points.orEmpty() else listOfNotNull(from,to)
    val routeId=if(activeRemainder)os.activeRouteId?:"replan" else if(useExisting)pickedRoute?.id?:os.editingRouteId?:currentRoute?.id?:"draft" else "point-to-point"
    val routeName=if(activeRemainder)os.activeRoute?.name?:os.t("余下航程","Remaining passage") else if(useExisting)pickedRoute?.name?:currentRoute?.name?:os.t("当前草稿","Current draft")else os.t("新航线","New route")
    val targetIndices=if(activeRemainder)os.activeRoute?.targetIndices?.filter{it>=os.routeLeg}?.map{it-os.routeLeg+1}
        else if(useExisting)when{pickedRoute!=null->pickedRoute.navigationTargetIndices;os.editingRoute->os.draftNavigationTargetIndices;else->currentRoute?.navigationTargetIndices}else null
    val analysis=state.analysis
    val readiness=PassagePlanningEligibility.evaluate(os.maps.selectedDatasetIds,chartData.datasets,wallNow)
    val currentDatasetRevisions=os.maps.selectedDatasetIds.mapNotNull{id->chartData.datasets.firstOrNull{it.id==id}?.let{it.id to it.revision}}.toMap()
    val canRequestPlan=readiness.canRequestPlanning&&!chartData.loading
    val stale=analysis!=null&&(analysis.request.route.points!=template.map{it.chartPoint()}||analysis.request.route.navigationTargetIndices!=targetIndices||analysis.request.vessel!=os.passageVessel()||analysis.request.datasetIds!=os.maps.selectedDatasetIds||analysis.request.backgroundKey!=os.backgroundKey()||analysis.datasetRevisions!=currentDatasetRevisions||analysis.datasetRevisions.any{(id,revision)->
        val dataset=chartData.datasets.firstOrNull{it.id==id}
        dataset==null||dataset.revision!=revision||analysis.severity!=PassageSeverity.INSUFFICIENT&&(!dataset.offlineReadable||dataset.issue!=null||!dataset.eligibility.allowsAnalysis(wallNow))
    }||analysis.request.avoidances!=state.avoidances||(state.planning&&state.detourLeg!=selectedLeg))
    val busy=state.job?.phase in setOf(PassageJobPhase.LOADING,PassageJobPhase.ANALYZING,PassageJobPhase.SEARCHING)
    val regionReadiness=state.planningReadiness?.takeIf{state.planning&&!stale&&analysis!=null}
    fun canUseCandidate(candidate:PassageCandidate)=canRequestPlan&&!stale&&!busy&&state.plan?.original?.key==analysis?.key&&
        regionReadiness?.canSearch!=false&&candidate.analysis.complete&&candidate.analysis.severity in setOf(PassageSeverity.REVIEW,PassageSeverity.NO_CONFLICT_FOUND)&&
        candidate.analysis.request.datasetIds==os.maps.selectedDatasetIds&&candidate.analysis.datasetRevisions==currentDatasetRevisions
    LaunchedEffect(analysis?.id,stale){reviewing=false}
    if(reviewing&&!stale)analysis?.let{result->PassageReviewDialog(os,result){reviewing=false}}
    fun showRoute(points:List<ChartPoint>,candidate:List<ChartPoint>?=null){view.planningLines=listOf(MapLine("planning:original",points.map{it.geo()},0xFF7E8995,3f))+candidate?.let{listOf(MapLine("planning:candidate",it.map{p->p.geo()},os.accent,4f))}.orEmpty();os.fitRequest=(points+candidate.orEmpty()).map{it.geo()};onDismiss()}
    fun locate(issue:PassageIssue){issue.point?.let{p->view.planningPoints=listOf(MapPoint("planning:issue",p.geo(),"",severityColor(issue.severity).toArgb().toLong(),10f));os.fly(p.geo(),os.zoom.coerceAtLeast(14.0));onDismiss()}}
    fun calculate(plan:Boolean){if(template.size<2||busy||!state.ready)return;if(plan&&!canRequestPlan){feedback=os.t(readiness.messageZh,readiness.messageEn);return};os.planningRequest(template,routeId,routeName,targetIndices)?.let{request->if(plan)system.planning.plan(request,selectedLeg)else system.analysis.analyze(request)}}
    fun restoreRequest(pending:PassageRequest){
        // 显式恢复只改本次规划输入，不更改草稿、收藏或正在执行的导航。
        pickedRouteId=null
        recoveredRouteJson=Route(pending.route.id,pending.route.name,pending.route.points.map{it.geo()},pending.route.navigationTargetIndices).json().toString()
        from=pending.route.points.firstOrNull()?.geo();to=pending.route.points.lastOrNull()?.geo()
        useExisting=true;activeRemainder=false;selectedLeg=state.detourLeg
        feedback=os.t("已继续这次规划；重新计算使用当前资料和船体参数","Plan restored; recalculation uses the current charts and boat settings")
    }
    fun persistCurrentDraft(successText:String,onSaved:(()->Unit)?=null){
        saving=true
        val receipt=os.save()
        os.scope.launch{
            val saved=receipt.result.await()==DurableCommitResult.SAVED
            feedback=if(saved)successText else os.t("设备尚未保存当前草稿，可重试保存","Current draft is not saved on this device. Retry saving.")
            saving=false
            if(saved)onSaved?.invoke()
        }
    }
    if(choosing!=null){val kind=choosing!!;MapPicker(os,if(kind=="from")from else if(kind=="to")to else os.center,onCancel={choosing=null},onConfirm={p->if(kind=="from"){from=p;useExisting=false;activeRemainder=false;selectedLeg=null}else if(kind=="to"){to=p;useExisting=false;activeRemainder=false;selectedLeg=null}else avoidanceCenter=p;choosing=null});return}
    if(chooseData)MapSourcePicker(os){chooseData=false}
    replanCommand?.let { command -> ConfirmDialog(os,os.t("用此方案替换正在执行的导航路线？","Replace the active navigation route with this candidate?"),{replanCommand=null}) {
        replanCommand=null
        val candidate=state.plan?.candidates?.firstOrNull{it.analysis.key==command.analysisReference}
        if(candidate==null||!canUseCandidate(candidate)){feedback=os.t("资料或条件已变化，请重新计算后采用","Data or conditions changed. Recalculate before using this candidate.");return@ConfirmDialog}
        saving=true
        os.scope.launch { val receipt=os.commitNavigation(command);feedback=if(receipt.result==NavigationResult.SAVED)os.t("当前导航已更新","Navigation updated")else if(receipt.reason=="REPLAN_START_MOVED")os.t("船位已变化，请从船位重新规划余程","Your boat has moved. Replan the remaining passage from its current position.")else os.t("导航未更改，请重新计算或重试","Navigation unchanged; recalculate or retry");saving=false }
    }}
    AppDialog(onDismissRequest=onDismiss){AppDialogSurface{
        AppDialogTitle(os.t("航线规划","Route planning"))
        MenuRow(os.t("选择航行数据","Choose navigation data"),os.maps.selectedDatasetIds.mapNotNull{id->chartData.datasets.firstOrNull{it.id==id}?.name}.joinToString(" · ").ifBlank{os.t("尚未选择","Not selected")}){chooseData=true}
        if(!readiness.canRequestPlanning)Label(os.t(readiness.messageZh,readiness.messageEn),14,LocalMetro.current.muted)
        else regionReadiness?.takeIf{!it.canSearch}?.let{Label(os.t(it.messageZh,it.messageEn),14,LocalMetro.current.muted)}
        // 保持本次访问及弹窗输入；返回图库后继续原起终点，不重新创建规划页面。
        MenuRow(os.t("管理 / 导入数据","Manage / import data")){os.openLinked("library:data")}
        if(os.navigationState.session?.source==NavigationSource.LOCAL&&os.navigationState.session?.ongoing==true) {
            val currentFix=os.hub.state.value.fix(os.positionSource)
            MenuRow(os.t("从船位重新规划余程","Replan remaining passage from boat"),if(activeRemainder)os.t("已选择","Selected")else null) {
                if(currentFix?.fresh(android.os.SystemClock.elapsedRealtime())==true){from=currentFix.point;activeRemainder=true;useExisting=false;selectedLeg=null}
                else feedback=os.t("等待新的可信船位后重规划","Wait for a fresh boat position to replan")
            }
        }
        if(activeRemainder)Label(os.t("已保留后续航点","Remaining waypoints retained"),14)
        else if(useExisting){
            MenuRow(routeName,os.t("保留航点，检查整条路线","Keep waypoints and check the whole route")){chooseRoute=!chooseRoute}
            MenuRow(os.t("改为选择起终点","Choose start and destination")){useExisting=false;selectedLeg=null}
        }else {
            MenuRow(os.t("起点","Start"),from?.let(os::formatCoordinates)?:os.t("选择位置","Choose position")){choosing="from"}
            MenuRow(os.t("终点","Destination"),to?.let(os::formatCoordinates)?:os.t("选择位置","Choose position")){choosing="to"}
            MenuRow(os.t("从我的航线选择","Choose a saved route")){chooseRoute=!chooseRoute}
        }
        if(chooseRoute){os.routes.forEach{r->MenuRow(r.name,os.formatDistance(r.length)){pickedRouteId=r.id;recoveredRouteJson=null;useExisting=true;activeRemainder=false;chooseRoute=false;selectedLeg=null}}}
        state.pendingRequest?.takeIf{pending->!busy&&(pending.route.points!=template.map{it.chartPoint()}||pending.route.navigationTargetIndices!=targetIndices||(state.planning&&state.detourLeg!=selectedLeg))}?.let {pending->
            MenuRow(os.t("继续上次规划","Continue previous plan"),pending.route.name){restoreRequest(pending)}
        }
        if(template.size>2){MenuRow(selectedLeg?.let{os.t("只绕行第 ${it+1} 段","Detour leg ${it+1}")}?:os.t("规划全线","Plan whole route")){selectedLeg=if(selectedLeg==null)0 else if(selectedLeg!!>=template.lastIndex-1)null else selectedLeg!!+1}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            MetroButton(os.t("检查航线","Check route"),{calculate(false)},Modifier.weight(1f),enabled=template.size>=2&&!busy&&state.ready)
            MetroButton(os.t("自动规划","Find a route"),{calculate(true)},Modifier.weight(1f),primary=true,enabled=template.size>=2&&!busy&&state.ready&&canRequestPlan)
        }
        MetroButton(if(os.draftRoute.isNotEmpty())os.t("继续手动编辑","Continue manual editing")else os.t("手动绘线","Draw route manually"),{
            if(saving||persistence.saving||persistence.readFailure!=null)return@MetroButton
            // 只在没有现存草稿时使用明确选出的起终点；现有草稿永不被这个入口替换。
            if(os.draftRoute.isEmpty()&&!useExisting&&!activeRemainder&&from!=null&&to!=null){
                os.draftRoute=listOfNotNull(from,to);os.draftNavigationTargetIndices=null;os.editingRouteId=null
                os.fitRequest=os.draftRoute
            }
            resumeOrCreateRouteDraft(os)
            persistCurrentDraft(os.t("手工草稿已保存","Manual draft saved"),onDismiss)
        },enabled=!saving&&!persistence.saving&&persistence.readFailure==null)
        if(busy){MetroProgress(os.t("正在计算…","Calculating…"));MetroButton(os.t("取消计算","Cancel"),{state.job?.requestId?.let(system.analysis::cancel)})}
        state.job?.takeIf{it.phase in setOf(PassageJobPhase.FAILED,PassageJobPhase.INTERRUPTED,PassageJobPhase.CANCELLED)}?.let{job->
            Label(issueText(os,job.detail?:"计算已停止 / Calculation stopped"),14)
            MetroButton(os.t("重新计算","Retry"),{calculate(state.planning)},enabled=template.size>=2&&!busy&&state.ready&&(!state.planning||canRequestPlan))
        }
        state.storageIssue?.let{Label(os.t("结果未能保存到设备","Result could not be saved"),14);MetroButton(os.t("重新读取已保存资料","Reload saved workspace"),{system.analysis.retryRestore()},enabled=!busy)}
        analysis?.let {result->
            AppSection(if(stale)os.t("上次结果 · 条件已变化","Previous result · conditions changed")else verdict(os,result.severity))
            Label(os.formatDistance(result.distanceMeters)+result.arrivalUtc?.let{" · "+os.t("计划抵达 ","Planned arrival ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it))}.orEmpty(),15)
            if(stale)Label(os.t("重新计算后才能采用方案","Recalculate before using a candidate"),14,LocalMetro.current.muted)
            if(result.complete)PassageEvidenceStrip(os,result){along->
                val points=result.request.route.points
                var remaining=along;var chosen=points.lastOrNull()
                for((a,b) in points.zipWithNext()){val len=distance(a.geo(),b.geo());if(remaining<=len){chosen=destination(a.geo(),remaining,bearing(a.geo(),b.geo())).chartPoint();break};remaining-=len}
                chosen?.let{p->view.planningPoints=listOf(MapPoint("planning:cursor",p.geo(),"",os.accent,8f));os.fly(p.geo(),os.zoom.coerceAtLeast(13.0));onDismiss()}
            }
            result.issues.take(issueLimit).forEach{issue->MenuRow(issueText(os,issue.message),os.t("第 ${issue.legIndex+1} 段 · ","Leg ${issue.legIndex+1} · ")+os.formatDistance(issue.alongMeters)){
                when(issue.kind){PassageIssueKind.VESSEL->{os.openLinked("settings:vessel")};PassageIssueKind.DATA->{chooseData=true};else->locate(issue)}
            }}
            if(result.issues.isNotEmpty()&&!stale)MenuRow(os.t("核对记录","Review notes")){reviewing=true}
            if(result.issues.size>issueLimit)MenuRow(os.t("还有 ${result.issues.size-issueLimit} 项","${result.issues.size-issueLimit} more")){issueLimit+=15}
            if(result.issues.isEmpty())Label(os.t("依据所选海图检查；不含潮汐或天气修正","Checked against selected charts; no tide or weather adjustment"),13,LocalMetro.current.muted)
        }
        state.plan?.let{plan->
            plan.reason?.let{Label(issueText(os,it),15)}
            plan.candidates.forEach{candidate->
                AppSection(os.t("候选航线","Route candidate"))
                val difference=when {
                    candidate.additionalMeters>1->os.t("增加 ","Longer by ")+os.formatDistance(candidate.additionalMeters)
                    candidate.additionalMeters< -1->os.t("缩短 ","Shorter by ")+os.formatDistance(-candidate.additionalMeters)
                    else->os.t("长度相同","Same distance")
                }
                Label(os.formatDistance(candidate.analysis.distanceMeters)+" · "+difference,19,LocalMetro.current.accent)
                candidate.analysis.arrivalUtc?.let{Label(os.t("计划抵达 ","Planned arrival ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it)),14)}
                Label(verdict(os,candidate.analysis.severity),14,LocalMetro.current.muted)
                MetroButton(os.t("在海图比较","Compare on chart"),{showRoute(plan.original.request.route.points,candidate.route.points)})
                MetroButton(os.t("采用为草稿","Use as draft"),{
                    if(!canUseCandidate(candidate)||saving||persistence.saving||persistence.readFailure!=null)return@MetroButton
                    val points=candidate.route.points.map{it.geo()}
                    val appliedRouteId=if(routeId=="point-to-point")null else routeId.takeUnless{it=="draft"}
                    if(os.editingRoute&&os.draftRoute==points&&os.editingRouteId==appliedRouteId&&os.draftNavigationTargetIndices==candidate.navigationTargetIndices){feedback=os.t("这份方案已在草稿中","This candidate is already in the draft");return@MetroButton}
                    os.planningDraftUndo=PlanningDraftUndo(os.draftRoute,os.editingRouteId,os.editingRoute,points,appliedRouteId,
                        os.draftNavigationTargetIndices,candidate.navigationTargetIndices)
                    os.draftRoute=points;os.draftNavigationTargetIndices=candidate.navigationTargetIndices
                    os.editingRouteId=appliedRouteId;os.editingRoute=true
                    persistCurrentDraft(os.t("草稿已保存","Draft saved"))
                },primary=true,enabled=canUseCandidate(candidate)&&!saving&&!persistence.saving&&persistence.readFailure==null)
                if(activeRemainder&&os.navigationState.session?.ongoing==true)MetroButton(os.t("替换当前导航路线…","Replace active route…"),{
                    if(!canUseCandidate(candidate)||saving)return@MetroButton
                    val route=Route(id=candidate.route.id,name=candidate.route.name,points=candidate.route.points.map{it.geo()},navigationTargetIndices=candidate.navigationTargetIndices)
                    val command=os.navigationCommand(NavigationAction.REPLAN,route=route.navigationSnapshot(),targetIndex=candidate.navigationTargetIndices?.firstOrNull()?:1,analysisReference=candidate.analysis.key)
                    replanCommand=command
                },enabled=canUseCandidate(candidate)&&!saving)
            }
        }
        undo?.let{old->MetroButton(os.t("撤销采用方案","Undo candidate"),{
            if(os.draftRoute!=old.appliedPoints||os.editingRouteId!=old.appliedRouteId||!os.editingRoute||os.draftNavigationTargetIndices!=old.appliedNavigationTargetIndices){feedback=os.t("草稿已有后续修改，未覆盖它","The draft has newer edits and was kept");return@MetroButton}
            os.draftRoute=old.points;os.editingRouteId=old.routeId;os.editingRoute=old.editing
            os.draftNavigationTargetIndices=old.previousNavigationTargetIndices;os.planningDraftUndo=null
            // 失败时保留已恢复的内存草稿。重试只写当前快照，不能再采用或撤销一次。
            persistCurrentDraft(os.t("已恢复原草稿","Original draft restored"))
        },enabled=!saving&&!persistence.saving&&persistence.readFailure==null)}
        if(persistence.readFailure!=null){
            Label(os.t("已保存资料暂时无法读取，恢复前不能覆盖它","Saved content could not be read. Restore it before saving changes."),14)
            MetroButton(os.t("重新读取","Retry reading"),{os.retryContentRead()})
        }else if(persistence.failed){
            Label(os.t("当前草稿尚未保存到设备","Current draft has not been saved on this device"),14)
            MetroButton(os.t("重试保存当前草稿","Retry saving current draft"),{persistCurrentDraft(os.t("当前草稿已保存","Current draft saved"))},enabled=!saving&&!persistence.saving)
        }
        if(saving||persistence.saving)MetroProgress(os.t("正在保存…","Saving…"))
        feedback?.let{Label(it,14)}
        if(view.planningLines.isNotEmpty()||view.planningPoints.isNotEmpty()||view.planningAreas.isNotEmpty())MenuRow(os.t("清除地图上的规划预览","Clear planning preview")){view.planningLines=emptyList();view.planningPoints=emptyList();view.planningAreas=emptyList()}
        MenuRow(os.t("船体与规划参数","Boat & planning settings"),marineState.vesselSettings.vesselName){os.openLinked("settings:vessel")}
        MenuRow(os.t("添加避让区","Add avoidance area")){choosing="avoidance"}
        avoidanceCenter?.let{center->
            Field(os.t("名称","Name"),avoidanceName,{avoidanceName=it.take(100)})
            Field(os.t("范围半径","Radius")+" · "+os.distanceUnitLabel,avoidanceRadius.text,{avoidanceRadius.edit(it)},number=true)
            MetroButton(os.t("保存避让区","Save avoidance"),{
                val radius=avoidanceRadius.value?:return@MetroButton
                val avoidance=PassageAvoidance(uid(),avoidanceName,(0 until 72).map{destination(center,radius,it*5.0).chartPoint()})
                saving=true;os.scope.launch{runCatching{system.analysis.saveAvoidance(avoidance)}.onSuccess{avoidanceCenter=null}.onFailure{feedback=os.t("避让区未保存","Avoidance not saved")};saving=false}
            },enabled=!saving&&avoidanceName.isNotBlank()&&avoidanceRadius.value?.let{it in 1.0..50000.0}==true)
        }
        state.avoidances.forEach{area->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            MetroButton(area.name,{view.planningAreas=listOf(MapArea("avoidance:${area.id}",area.boundary.map{it.geo()},0x558C448F));os.fitRequest=area.boundary.map{it.geo()};onDismiss()},Modifier.weight(1f))
            IconAction("delete",os.t("移除避让区","Remove avoidance"),{os.scope.launch{runCatching{system.analysis.removeAvoidance(area.id)}.onFailure{feedback=os.t("未能移除","Could not remove")}}})
        }}
        MetroButton(os.t("返回海图","Back to chart"),onDismiss)
    }}
}

/** 上轨为覆盖，下轨画实际深度区间；测深点独立显示，空白不补线。横轴固定为航线累计距离。 */
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

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.launch

internal fun featureTitle(os:OsStore,f:NauticalFeature):String=(if(os.chinese)f.attributes["NOBJNM"] else null)?.takeIf {it.isNotBlank()} ?: f.attributes["OBJNAM"]?.takeIf{it.isNotBlank()} ?: when(f.kind){
    NauticalFeatureKind.LAND->os.t("陆地","Land");NauticalFeatureKind.SOUNDING->os.t("测深点","Sounding");NauticalFeatureKind.DEPTH_AREA->os.t("水深区域","Depth area");NauticalFeatureKind.DEPTH_CONTOUR->os.t("等深线","Depth contour");NauticalFeatureKind.DRYING_AREA->os.t("干出区域","Drying area");NauticalFeatureKind.DREDGED_AREA->os.t("疏浚区域","Dredged area");NauticalFeatureKind.ROCK->os.t("礁石","Rock");NauticalFeatureKind.WRECK->os.t("沉船","Wreck");NauticalFeatureKind.OBSTRUCTION->os.t("障碍物","Obstruction");NauticalFeatureKind.BEACON->os.t("航标","Beacon");NauticalFeatureKind.LIGHT->os.t("灯标","Light");NauticalFeatureKind.TRAFFIC->os.t("交通规则区","Traffic area");NauticalFeatureKind.RESTRICTED->os.t("限制区","Restricted area");NauticalFeatureKind.BRIDGE->os.t("桥梁","Bridge");NauticalFeatureKind.OVERHEAD->os.t("上方障碍","Overhead obstruction");NauticalFeatureKind.QUALITY->os.t("测量质量","Survey quality");NauticalFeatureKind.COVERAGE->os.t("资料覆盖范围","Data coverage");else->os.t("其他对象","Other object")
}
internal fun depthEvidenceText(os:OsStore,depth:DepthEvidence?):String=when(depth?.kind){
    DepthEvidenceKind.POINT->os.t("该点水深 ","Depth at this point ")+os.formatDepth(depth.pointMeters)
    DepthEvidenceKind.INTERVAL->os.t("深度区间 ","Depth interval ")+os.formatDepth(depth.lowerMeters)+" – "+os.formatDepth(depth.upperMeters)
    DepthEvidenceKind.CONTOUR->os.t("等深线 ","Depth contour ")+os.formatDepth(depth.pointMeters?:depth.lowerMeters)
    else->os.t("没有深度依据","No depth evidence")
}
/** 一次明确确认的追加命令；失败重试只保存同一份结果，不再次追加坐标。 */
private data class ChartObjectWaypointDraft(
    val point:GeoPoint,
    val beforePoints:List<GeoPoint>,val beforeTargets:List<Int>?,val routeId:String?,
    val points:List<GeoPoint>,val targets:List<Int>?,val applied:Boolean=false,
)

@Composable fun ChartObjectSheet(os:OsStore,view:MapViewState) {
    var selectedId by remember(view.selectedChartObjects){mutableStateOf(if(view.selectedChartObjects.size==1)view.selectedChartObjects.first().id else null)}
    val selected=view.selectedChartObjects.firstOrNull{it.id==selectedId}
    var raw by remember(selectedId){mutableStateOf(false)}
    var pending by remember(selectedId){mutableStateOf<Place?>(null)}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    // 此命令属于点按坐标，不属于某个航标对象；切换“这里的其他对象”不会重复追加同一点。
    var waypoint by remember(view.selectedChartCoordinate){mutableStateOf<ChartObjectWaypointDraft?>(null)}
    var waypointSaving by remember(view.selectedChartCoordinate){mutableStateOf(false)}
    var waypointSaved by remember(view.selectedChartCoordinate){mutableStateOf(false)}
    var waypointError by remember(view.selectedChartCoordinate){mutableStateOf<String?>(null)}
    fun prepareWaypoint() {
        val point=view.selectedChartCoordinate?.takeIf {it.valid()} ?: return
        val original=os.draftRoute.toList();val targets=os.draftNavigationTargetIndices?.toList()
        val duplicate=original.lastOrNull()==point
        waypoint=ChartObjectWaypointDraft(point,original,targets,os.editingRouteId,
            if(duplicate)original else original+point,
            if(duplicate)targets else targets?.let {(it+original.size).distinct().sorted()})
        waypointError=null
    }
    fun commitWaypoint() {
        val command=waypoint ?: return
        if(waypointSaving||waypointSaved)return
        val expected=if(command.applied)command.points else command.beforePoints
        val expectedTargets=if(command.applied)command.targets else command.beforeTargets
        if(os.draftRoute!=expected||os.draftNavigationTargetIndices!=expectedTargets||os.editingRouteId!=command.routeId) {
            waypointError=os.t("草稿已改变，请重新确认，避免覆盖其他修改。","The draft changed. Review it again to preserve your edits.")
            return
        }
        waypointSaving=true;waypointError=null
        try {
            if(!command.applied) {
                os.draftRoute=command.points
                os.draftNavigationTargetIndices=command.targets
                os.editingRoute=true
                waypoint=command.copy(applied=true)
            }
            val receipt=os.save()
            os.scope.launch {
                try {
                    if(receipt.result.await()==DurableCommitResult.SAVED)waypointSaved=true
                    else waypointError=os.t("草稿尚未保存到设备，重试不会再次添加航点。","The draft is not saved yet. Retrying will not add another waypoint.")
                }finally {waypointSaving=false}
            }
        }catch(failure:Exception) {
            waypointSaving=false
            waypointError=os.t("草稿尚未保存，请重试。","The draft is not saved yet. Retry saving.")
        }
    }
    val data by os.maps.charts.state.collectAsState()
    val dismiss={view.selectedChartObjects=emptyList();view.selectedChartCoordinate=null}
    AppDialog(onDismissRequest=dismiss){AppDialogSurface{
        AppDialogTitle(selected?.let{featureTitle(os,it)}?:os.t("这里有什么","At this position"))
        if(selected==null)view.selectedChartObjects.forEach{f->MenuRow(featureTitle(os,f),f.depth?.let{depthEvidenceText(os,it)}){selectedId=f.id}}
        else {
            selected.depth?.let{Label(depthEvidenceText(os,it),20,LocalMetro.current.accent)}
            (if(os.chinese)selected.attributes["NINFOM"] else null)?.takeIf {it.isNotBlank()}?.let {Label(it,15)}
                ?: selected.attributes["INFORM"]?.takeIf{it.isNotBlank()}?.let{Label(it,15)}
            val dataset=data.datasets.firstOrNull {it.id==selected.datasetId}
            dataset?.let {Label(it.name+" · "+chartUseLabel(os,it.eligibility),13,LocalMetro.current.muted)}
            Label("${selected.cellId} · ${os.t("版","edition")} ${selected.source.edition} · ${os.t("更新","update")} ${selected.source.update}",13,LocalMetro.current.muted)
            if(selected.issues.isNotEmpty())Label(os.t("部分对象信息不完整","Some object information is incomplete"),14)
            val reference=if(selected.geometry.kind==ChartGeometryKind.POINT)selected.geometry.parts.firstOrNull()?.points?.firstOrNull()?.let {GeoPoint(it.latitude,it.longitude)}else view.selectedChartCoordinate
            if(pending==null)MetroButton(os.t("收藏这里","Save this place"),{
                reference?.let {point->pending=Place(name=featureTitle(os,selected),point=point,note="${selected.acronym} · ${selected.cellId} · ${selected.source.edition}/${selected.source.update}",capture=PlaceCapture(System.currentTimeMillis()));error=false}
            },enabled=!saving&&reference!=null)
            if(waypoint==null)MetroButton(os.t("把此坐标加到航线草稿","Add this coordinate to route draft"),{prepareWaypoint()},enabled=!saving&&!waypointSaving&&!waypointSaved&&view.selectedChartCoordinate?.valid()==true)
            if(view.selectedChartCoordinate==null)Label(os.t("先在地图点选一个位置，才能添加航点。","Tap a position on the map before adding a waypoint."),13,LocalMetro.current.muted)
            if(view.selectedChartObjects.size>1&&!saving&&!waypointSaving)MenuRow(os.t("查看此处其他对象","Other objects here")){selectedId=null}
            MenuRow(if(raw)os.t("收起资料","Hide details")else os.t("原始资料","Source details")){raw=!raw}
            if(raw){
                selected.source.compilationScale?.let {Label(os.t("编图比例尺 ","Compilation scale ")+"1:$it",13,LocalMetro.current.muted)}
                selected.attributes.forEach{(key,value)->Label("$key · $value",13,LocalMetro.current.muted)}
                selected.depth?.datum?.let{Label(os.t("深度基准代码 ","Depth datum code ")+it,13,LocalMetro.current.muted)}
                selected.issues.forEach {Label(chartDataError(os,it),13,LocalMetro.current.muted)}
            }
        }
        waypoint?.let {command->
            AppSection(os.t("确认航点位置","Confirm waypoint position"))
            Label(os.formatCoordinates(command.point),18,LocalMetro.current.accentText)
            Label(when {
                waypointSaved->os.t("已保存到航线草稿","Saved to route draft")
                command.points==command.beforePoints->os.t("这个位置已在草稿末尾，将保存当前草稿。","This position is already at the end. Save the current draft.")
                command.beforePoints.isEmpty()->os.t("作为新草稿的第一个航点。","Use this as the first waypoint of a new draft.")
                else->os.t("追加到现有草稿末尾，已有航点保持不变。","Append to the current draft, keeping its existing waypoints.")
            },14,LocalMetro.current.muted)
            Label(os.t("请核对所选位置，航标或障碍物的位置不等于可航行目的地。","Check the selected position: a navigation aid or hazard is not a passable destination."),13,LocalMetro.current.muted)
            waypointError?.let {Label(it,14,LocalMetro.current.accentText)}
            MetroButton(when {waypointSaving->os.t("正在保存…","Saving…");waypointSaved->os.t("已保存","Saved");command.applied->os.t("重试保存草稿","Retry saving draft");else->os.t("确认并保存草稿","Confirm and save draft")},::commitWaypoint,primary=true,enabled=!waypointSaving&&!waypointSaved)
            if(!waypointSaving&&!waypointSaved) {
                if(waypointError!=null)MetroButton(os.t("按当前草稿重新确认","Review the current draft"),{prepareWaypoint()})
                if(!command.applied)MetroButton(os.t("取消添加","Cancel addition"),{waypoint=null;waypointError=null})
            }
        }
        pending?.let{place->
            Field(os.t("地点名称","Place name"),place.name,{if(!saving)pending=place.copy(name=it.take(120))})
            Label(os.formatCoordinates(place.point),15)
            MetroButton(if(saving)os.t("正在保存…","Saving…")else os.t("确认收藏","Confirm save"),{
                saving=true;error=false
                val receipt=os.sailing.put(place)
                os.scope.launch{try {if(receipt.result.await()==DurableCommitResult.SAVED){pending=null;dismiss()}else error=true}finally {saving=false}}
            },primary=true,enabled=!saving&&place.name.isNotBlank())
            if(error)Label(os.t("未能保存，请重试","Could not save; retry"),14)
            if(!saving)MetroButton(os.t("取消收藏","Cancel save"),{pending=null;error=false})
        }
        MetroButton(os.t("关闭","Close"),dismiss)
    }}
}

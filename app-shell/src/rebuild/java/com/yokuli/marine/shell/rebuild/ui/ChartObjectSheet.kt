package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.cos
import java.util.Locale

internal fun featureTitle(os:OsStore,f:NauticalFeature):String=(if(os.chinese)f.attributes["NOBJNM"] else null)?.takeIf {it.isNotBlank()} ?: f.attributes["OBJNAM"]?.takeIf{it.isNotBlank()} ?: when(f.kind){
    NauticalFeatureKind.LAND->os.t("陆地","Land");NauticalFeatureKind.SOUNDING->os.t("测深点","Sounding");NauticalFeatureKind.DEPTH_AREA->os.t("水深区域","Depth area");NauticalFeatureKind.DEPTH_CONTOUR->os.t("等深线","Depth contour");NauticalFeatureKind.DRYING_AREA->os.t("干出区域","Drying area");NauticalFeatureKind.DREDGED_AREA->os.t("疏浚区域","Dredged area");NauticalFeatureKind.ROCK->os.t("礁石","Rock");NauticalFeatureKind.WRECK->os.t("沉船","Wreck");NauticalFeatureKind.OBSTRUCTION->os.t("障碍物","Obstruction");NauticalFeatureKind.BEACON->os.t("航标","Beacon");NauticalFeatureKind.LIGHT->os.t("灯标","Light");NauticalFeatureKind.TRAFFIC->os.t("交通规则区","Traffic area");NauticalFeatureKind.RESTRICTED->os.t("限制区","Restricted area");NauticalFeatureKind.BRIDGE->os.t("桥梁","Bridge");NauticalFeatureKind.OVERHEAD->os.t("上方障碍","Overhead obstruction");NauticalFeatureKind.QUALITY->os.t("测量质量","Survey quality");NauticalFeatureKind.COVERAGE->os.t("资料覆盖范围","Data coverage");else->when(f.acronym) {
    "ACHARE","ACHBRT"->os.t("锚地","Anchorage")
    "BERTHS"->os.t("泊位","Berth")
    "HRBFAC"->os.t("港口设施","Harbour facility")
    "HRBARE"->os.t("港区","Harbour area")
    "SMCFAC"->os.t("游艇服务设施","Small craft facilities")
    "DOCARE"->os.t("船坞","Dock")
    "MORFAC"->os.t("系泊设施","Mooring facility")
    "SLCONS"->os.t("岸线设施","Shore construction")
    "COALNE"->os.t("岸线","Coastline")
    "SEAARE"->os.t("海域","Sea area")
    "PILBOP"->os.t("引航登船点","Pilot boarding point")
    "RDOSTA"->os.t("无线电台","Radio station")
    "RTPBCN"->os.t("雷达应答标","Radar beacon")
    "GPKG_UNCERTAIN"->os.t("几何不确定区域","Uncertain chart geometry")
    else->f.acronym.takeIf {it.isNotBlank()} ?: os.t("其他对象","Other object")
}
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
    var pending by remember(selectedId,view.selectedChartCoordinate){mutableStateOf<Place?>(null)}
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
    val queryPoint=view.selectedChartCoordinate
    val selectedDatasets=os.maps.selectedDatasetIds
    var raster by remember(queryPoint,selectedDatasets,data.revision){mutableStateOf<ChartRasterProbe?>(null)}
    var rasterLoading by remember(queryPoint,selectedDatasets,data.revision){mutableStateOf(false)}
    var rasterError by remember(queryPoint,selectedDatasets,data.revision){mutableStateOf(false)}
    var rasterRetry by remember(queryPoint){mutableIntStateOf(0)}
    LaunchedEffect(queryPoint,selectedDatasets,data.revision,rasterRetry) {
        raster=null;rasterError=false
        if(queryPoint==null||!hasRasterAt(data.datasets,selectedDatasets,queryPoint))return@LaunchedEffect
        rasterLoading=true
        try {raster=probeChartRaster(os.maps.charts,selectedDatasets,queryPoint)}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){rasterError=true}
        finally {rasterLoading=false}
    }
    val dismiss={view.selectedChartObjects=emptyList();view.selectedChartCoordinate=null}
    AppDialog(onDismissRequest=dismiss){AppDialogSurface{
        AppDialogTitle(selected?.let{featureTitle(os,it)}?:os.t("这里有什么","At this position"))
        if(rasterLoading)MetroProgress(os.t("读取离线水深…","Reading offline bathymetry…"))
        if(rasterError) {
            Label(os.t("所选资料无法读取，请重试或在图册更新文件夹。","The selected data could not be read. Retry or update its folder in Atlas."),14,LocalMetro.current.muted)
            MetroButton(os.t("重新读取","Read again"),{rasterRetry++})
        }
        raster?.let {reading->
            val elevation=reading.elevationMeters?.toDouble()
            Label(when {
                elevation==null->os.t("此像元没有高程数据","No elevation data in this cell")
                elevation<0->os.t("估算海底深度 ","Estimated seabed depth ")+os.formatDepth(-elevation)
                else->os.t("地表高程 ","Surface elevation ")+os.formatDepth(elevation)
            },20,LocalMetro.current.accentText)
            if(elevation!=null&&elevation<0)Label(os.t("原始高程 ","Source elevation ")+os.formatDepth(elevation),13,LocalMetro.current.muted)
            Label(reading.datasetName+" · "+reading.grid.product.removeSuffix("_Grid"),13,LocalMetro.current.muted)
            val pixel=queryPoint?.let{reading.grid.pixelAt(ChartPoint(it.lat,it.lon))}
            val latitude=pixel?.let{reading.grid.centre(it.first,it.second).latitude}?:0.0
            val horizontal=reading.grid.pixelWidthDegrees*111_320*cos(Math.toRadians(latitude)).coerceAtLeast(0.0)
            val vertical=reading.grid.pixelHeightDegrees*111_320
            val arcseconds=String.format(Locale.ROOT,"%.0f",reading.grid.pixelHeightDegrees*3600)
            Label(os.t("网格 ","Grid ")+arcseconds+"″ · "+os.t("约 ","about ")+os.formatDistance(horizontal)+" × "+os.formatDistance(vertical),13,LocalMetro.current.muted)
            Label(os.t("参考地形，非 ENC 测深；不能证明安全净空。","Reference terrain, not ENC soundings; it does not establish safe clearance."),13,LocalMetro.current.muted)
            if(elevation==null)Label(os.t("保留所选资料的空值，不用低优先级文件补齐。","The selected source has no data here. Lower-priority files are not substituted."),13,LocalMetro.current.muted)
        }
        if(selected==null&&view.selectedChartObjects.isEmpty()&&queryPoint!=null) {
            Label(os.formatCoordinates(queryPoint),14,LocalMetro.current.muted)
            if(!rasterLoading&&!rasterError&&raster==null)Label(os.t("所选资料在这里没有可显示的数据。","The selected data has no information to display here."),14,LocalMetro.current.muted)
            if(pending==null)MetroButton(os.t("收藏这里","Save this place"),{
                pending=Place(name=os.t("我的地点","My place"),point=queryPoint,note=raster?.let{it.grid.product+" · "+it.grid.sourceName}.orEmpty(),capture=PlaceCapture(System.currentTimeMillis()));error=false
            },enabled=!saving)
            if(waypoint==null)MetroButton(os.t("加入航线草稿","Add to route draft"),{prepareWaypoint()},enabled=!saving&&!waypointSaving&&!waypointSaved)
        }
        if(selected==null)view.selectedChartObjects.forEach{f->MenuRow(featureTitle(os,f),f.depth?.let{depthEvidenceText(os,it)}){selectedId=f.id}}
        else {
            selected.depth?.let{Label(depthEvidenceText(os,it),20,LocalMetro.current.accent)}
            (if(os.chinese)selected.attributes["NINFOM"] else null)?.takeIf {it.isNotBlank()}?.let {Label(it,15)}
                ?: selected.attributes["INFORM"]?.takeIf{it.isNotBlank()}?.let{Label(it,15)}
            val dataset=data.datasets.firstOrNull {it.id==selected.datasetId}
            dataset?.let {Label(it.name,13,LocalMetro.current.muted)}
            Label("${selected.cellId} · ${os.t("版","edition")} ${selected.source.edition} · ${os.t("更新","update")} ${selected.source.update}",13,LocalMetro.current.muted)
            if(selected.issues.any(::isBlockingChartIssue))Label(os.t("部分对象信息不完整","Some object information is incomplete"),14)
            else if(dataset?.cells?.any{it.cellId==selected.cellId&&it.referenceOnly}==true)
                Label(os.t("参考水文资料，不能替代正式航海图。","Reference hydrographic data; not a substitute for official charts."),13,LocalMetro.current.muted)
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

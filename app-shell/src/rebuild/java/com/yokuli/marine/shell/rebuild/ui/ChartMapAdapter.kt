package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.marine.shell.rebuild.data.Fix
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.compose.LocalInternalAppPageKey
import com.yokuli.shell.engine.ShellVisualSurface
import com.yokuli.shell.engine.currentUiStateKey
import kotlinx.coroutines.*
import com.yokuli.runtime.contract.chart.ChartGeometryKind
import com.yokuli.runtime.contract.chart.NauticalFeature
import com.yokuli.runtime.contract.chart.NauticalFeatureKind
import kotlin.math.*

/** Chart owns its route/mark editing; the renderer receives geometry and returns gestures. */
@Composable
fun NativeChart(os: OsStore, fix: Fix?, modifier: Modifier = Modifier, onHost: (ChartHost) -> Unit) {
    DraftPassageAnalysis(os)
    val now=rememberMarineClock()
    val traffic=rememberAisTraffic(os)
    val instanceKey = LocalInternalAppPageKey.current
    val shellState by os.shell.engine.state.collectAsState()
    fun isCurrent(): Boolean = os.shell.engine.state.value.let { state ->
        val task = (state.surface as? ShellVisualSurface.Module)?.let { state.tasks.task(it.taskId) }
        instanceKey == null || task?.currentUiStateKey == instanceKey
    }
    val active = (shellState.surface as? ShellVisualSurface.Module)?.let { shellState.tasks.task(it.taskId)?.currentUiStateKey == instanceKey } == true || instanceKey == null
    val sharedView = os.maps.view("chart", os.center, os.zoom)
    var selectedDraftPointIndex by rememberSaveable(instanceKey) {mutableStateOf<Int?>(null)}
    // 原生地图在转场期间仍会回调相机位置。每次访问用自己的相机状态，离场页不能写回新页。
    val view = remember(instanceKey) { MapViewState(os.center, os.zoom) }
    val chartData by os.maps.charts.state.collectAsState()
    val datasetPreview=sharedView.datasetPreview
    val datasetPreviewDataset=chartData.datasets.firstOrNull {it.id==datasetPreview?.datasetId}
    val datasetPreviewValid=datasetPreview!=null&&!chartData.loading&&chartData.error==null&&datasetPreviewDataset?.let {it.revision==datasetPreview.datasetRevision&&it.offlineReadable}==true
    var datasetPreviewScene by remember(datasetPreview?.requestId) {mutableStateOf(MapScene())}
    LaunchedEffect(active,datasetPreview?.requestId,datasetPreviewDataset?.revision,datasetPreviewDataset?.offlineReadable,chartData.loading,chartData.error,view.center,view.zoom,os.maps.unitPreferences,os.maps.portrayalPreferences,os.chinese) {
        if(!active||datasetPreview==null) {datasetPreviewScene=MapScene();return@LaunchedEffect}
        if(chartData.loading||chartData.error!=null)return@LaunchedEffect
        if(!datasetPreviewValid) {
            sharedView.datasetPreview=null;sharedView.datasetPreviewNote=null;datasetPreviewScene=MapScene();return@LaunchedEffect
        }
        delay(120)
        try {
            val drawing=datasetPreviewDrawing(os.maps.charts,datasetPreview,requireNotNull(datasetPreviewDataset),view.center,view.zoom,os.maps.unitPreferences,os.maps.portrayalPreferences,os.chinese)
            if(sharedView.datasetPreview?.requestId!=datasetPreview.requestId)return@LaunchedEffect
            datasetPreviewScene=drawing.scene
            sharedView.datasetPreviewNote=drawing.note
        } catch(cancel:CancellationException) {throw cancel}
        catch(_:Exception) {
            if(sharedView.datasetPreview?.requestId==datasetPreview.requestId) {
                datasetPreviewScene=MapScene()
                sharedView.datasetPreviewNote=os.t("数据预览暂时无法读取；原始数据仍保留在图册。","Data preview could not be read; the original data remains in Library.")
            }
        }
    }
    val libraryPreview=sharedView.libraryPreview
    val previewDataset=chartData.datasets.firstOrNull {it.id==libraryPreview?.feature?.datasetId}
    val previewValid=libraryPreview!=null&&!chartData.loading&&chartData.error==null&&previewDataset?.let {it.revision==libraryPreview.datasetRevision&&it.offlineReadable}==true
    var libraryScene by remember(libraryPreview?.requestId) {mutableStateOf(MapScene())}
    LaunchedEffect(active,libraryPreview?.requestId,previewDataset?.revision,previewDataset?.offlineReadable,chartData.loading,chartData.error) {
        if(!active||libraryPreview==null)return@LaunchedEffect
        if(chartData.loading||chartData.error!=null)return@LaunchedEffect
        if(!previewValid) {
            sharedView.libraryPreview=null;sharedView.libraryPreviewNote=null;return@LaunchedEffect
        }
        if(sharedView.libraryPreviewCameraRequestId!=libraryPreview.requestId) {
            val extent=withContext(Dispatchers.Default) {libraryObjectExtent(libraryPreview.feature)}
            if(!isCurrent()||sharedView.libraryPreview?.requestId!=libraryPreview.requestId)return@LaunchedEffect
            sharedView.libraryPreviewCameraRequestId=libraryPreview.requestId
            if(extent.size==1)os.fly(extent.first(),os.zoom.coerceAtLeast(14.0))else if(extent.isNotEmpty())os.fitRequest=extent
        }
    }
    val previewAccent=os.accent
    val previewUnits=os.maps.unitPreferences
    val previewPortrayal=os.maps.portrayalPreferences
    val soundingViewport=if(libraryPreview?.feature?.kind==NauticalFeatureKind.SOUNDING)view.center to view.zoom else null
    LaunchedEffect(active,libraryPreview?.requestId,previewValid,soundingViewport,previewAccent,previewUnits,previewPortrayal,os.chinese) {
        if(!active||libraryPreview==null||!previewValid) {libraryScene=MapScene();return@LaunchedEffect}
        delay(120)
        val feature=libraryPreview.feature
        val center=view.center;val zoom=view.zoom
        val rendered=withContext(Dispatchers.Default) {
            when {
                feature.kind==NauticalFeatureKind.SOUNDING-> {
                    val drawing=structuredScene(listOf(feature),center,zoom,previewUnits,previewPortrayal)
                    drawing.scene.copy(points=drawing.scene.points.map {it.copy(id="library-object:${it.id}",color=previewAccent)}) to drawing.soundingsSimplified
                }
                feature.geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT)-> {
                    val count=feature.geometry.parts.sumOf {it.points.size}
                    val stride=((count+159)/160).coerceAtLeast(1);var index=0
                    val markers=buildList {
                        for(part in feature.geometry.parts)for(point in part.points) {
                            if(index%256==0)currentCoroutineContext().ensureActive()
                            if(index++%stride==0)add(MapPoint("library-object:$index",GeoPoint(point.latitude,point.longitude),"",previewAccent,6f))
                        }
                    }
                    MapScene(points=markers) to (count>markers.size)
                }
                else-> {
                    val lines=feature.geometry.parts.mapIndexed {index,part->
                        currentCoroutineContext().ensureActive()
                        MapLine("library-object:$index",part.points.map {GeoPoint(it.latitude,it.longitude)},previewAccent,3f)
                    }
                    MapScene(lines=lines) to false
                }
            }
        }
        if(sharedView.libraryPreview?.requestId!=libraryPreview.requestId)return@LaunchedEffect
        libraryScene=rendered.first
        sharedView.libraryPreviewNote=when {
            !rendered.second->null
            feature.kind==NauticalFeatureKind.SOUNDING->os.t("测深点已简化 · 放大查看，完整资料保留在图册","Depth labels simplified · Zoom in; full data remains in Library")
            else->os.t("点位仅作概览 · 完整资料保留在图册","Point overview · Full data remains in Library")
        }
    }
    var nativeHost by remember(instanceKey) { mutableStateOf<ChartHost?>(null) }
    fun nearestDraftInsertion(point:GeoPoint):Pair<Int,GeoPoint>? {
        val host=nativeHost?:return null
        val camera=host.camera?:return null
        val routePoints=os.draftRoute
        if(routePoints.size<2)return null
        val q=camera.project(point)
        if(!q.x.isFinite()||!q.y.isFinite())return null
        var bestSegment=-1;var bestDistance=Float.POSITIVE_INFINITY;var bestX=0f;var bestY=0f
        for(index in 0 until routePoints.lastIndex) {
            val a=camera.project(routePoints[index]);val b=camera.project(routePoints[index+1])
            if(!a.x.isFinite()||!a.y.isFinite()||!b.x.isFinite()||!b.y.isFinite())continue
            val dx=b.x-a.x;val dy=b.y-a.y;val length2=dx*dx+dy*dy
            val t=if(length2<=.01f)0f else (((q.x-a.x)*dx+(q.y-a.y)*dy)/length2).coerceIn(0f,1f)
            val x=a.x+dx*t;val y=a.y+dy*t;val d=hypot(q.x-x,q.y-y)
            if(d<bestDistance){bestDistance=d;bestSegment=index;bestX=x;bestY=y}
        }
        val threshold=44f*host.resources.displayMetrics.density
        if(bestSegment<0||bestDistance>threshold)return null
        // 命中测试用最近线段，但真正插入用户长按的地理位置；这样长按在线旁边也能主动把航线掰到那里。
        return (bestSegment+1) to point
    }
    view.interactive = active && LocalInternalAppInputEnabled.current
    if(active) {
        view.objectPickingEnabled = !os.editingRoute&&os.ruler.isEmpty()&&datasetPreview==null
        view.datasetPreview = sharedView.datasetPreview
        view.orientationMode = sharedView.orientationMode
        view.planningLines = sharedView.planningLines
        view.planningPoints = sharedView.planningPoints
        view.planningAreas = sharedView.planningAreas
        view.follow = os.follow
        view.showCrosshair = os.showCrosshair || os.editingRoute
        view.ruler = os.ruler
        view.bottomOverlayDp = sharedView.bottomOverlayDp
        os.cameraRequest?.let { (point, zoom) -> view.fly(point, zoom); os.cameraRequest = null }
        os.fitRequest?.takeIf { it.isNotEmpty() }?.let { view.fit(it); os.fitRequest = null; os.follow = false }
    }
    val effectiveOrientation=view.effectiveOrientationMode
    val orientationIssue=view.orientationIssue
    SideEffect {
        nativeHost?.captureForTile = active
        if(active){sharedView.effectiveOrientationMode=effectiveOrientation;sharedView.orientationIssue=orientationIssue}
    }
    val route = chartRoute(os)
    val navigating = !os.editingRoute && route != null && chartIsNavigating(os)
    val points = if (os.editingRoute) os.draftRoute else route?.points.orEmpty()
    val accent = os.accent
    view.scaleTopDp=118f
    val allPlaces=remember(os.places,os.sailing.spots,os.sailing.locations){os.allPlaces}
    val livePosition=fix?.takeIf {it.fresh(now)}?.point
    val nextPoint=os.nextPoint
    val lines = remember(sharedView.previewTrack,os.recordingActive,os.recordedSegments,points,navigating,os.routeLeg,livePosition,nextPoint,accent) {buildList {
        sharedView.previewTrack.forEachIndexed { i, segment -> add(MapLine("voyage-preview:$i",segment,0xFFDE8531,3f)) }
        if(os.recordingActive) os.recordedSegments.forEachIndexed {i,segment -> add(MapLine("recording:$i",segment,0xFF008B8E,2.5f))}
        if(points.isNotEmpty()) add(MapLine("route",points,if(navigating)0xFF7D898C else accent,2.6f))
        if(navigating) add(MapLine("remaining",points.drop((os.routeLeg-1).coerceAtLeast(0)),accent))
        if(navigating && livePosition!=null) nextPoint?.let {add(MapLine("target",listOf(livePosition,it),accent,2f,true))}
    }}
    val logicalTargets=(if(os.editingRoute)os.draftNavigationTargetIndices else route?.navigationTargetIndices) ?: points.indices.toList()
    val currentTarget=os.navigationState.session?.targetIndex
    val markers = remember(points,logicalTargets,currentTarget,os.editingRoute,navigating,os.routeLeg,allPlaces,sharedView.selectedPlaceId,accent) {buildList {
        if(os.editingRoute) {
            points.forEachIndexed {i,p->add(MapPoint("route:$i",p,(i+1).toString(),accent,14f,true))}
        } else {
            val labels=logicalTargets.withIndex().associate {it.value to (it.index+1).toString()}
            points.forEachIndexed {i,p ->
                val label=labels[i]
                if(label!=null)add(MapPoint("route:$i",p,label,if(navigating && i<os.routeLeg)0xFF7D898C else accent,
                    if(navigating && i==currentTarget)16f else 10f,false))
                else if(i==0||navigating&&i==os.routeLeg)add(MapPoint("route:$i",p,"",accent,4f,false))
            }
        }
        allPlaces.forEach {add(MapPoint("place:${it.id}",it.point,"",if(sharedView.selectedPlaceId==it.id)0xFFD74A29 else accent,if(sharedView.selectedPlaceId==it.id)12f else 5f))}
    }}
    val aisTargets=remember(traffic.targets,traffic.preferences.chartLayer,sharedView.previewTrack.isEmpty(),sharedView.selectedAisMmsi) {
        if(sharedView.previewTrack.isNotEmpty())emptyList()
        else if(traffic.preferences.chartLayer)aisMapTargets(traffic,sharedView.selectedAisMmsi)
        // 显式查看单船可临时显示该对象；不擅自打开用户关闭的全局图层。
        else if(sharedView.selectedAisMmsi!=null)aisMapTargets(traffic,sharedView.selectedAisMmsi).filter {it.selected}
        else emptyList()
    }
    MarineMap(os.maps,MapScene(
        vessel=fix?.let {MapVessel(it.point,it.freshCourse(now),it.fresh(now),it.freshHeading(now),it.freshSpeed(now))},
        points=markers+libraryScene.points+datasetPreviewScene.points,
        lines=lines+libraryScene.lines+datasetPreviewScene.lines,
        areas=libraryScene.areas+datasetPreviewScene.areas,
        demo=os.positionSource=="demo" || os.marine?.services?.state?.value?.settings?.demoMode==true,
        aisTargets=aisTargets,
        aisInteractive=!os.editingRoute&&os.ruler.isEmpty()&&sharedView.previewTrack.isEmpty()&&datasetPreview==null),view,modifier,
        onHost={host -> nativeHost=host;host.captureForTile=active;onHost(host)},onEvent={event ->if(isCurrent())when(event) {
            is MapEvent.CameraChanged -> {os.center=event.center;os.zoom=event.zoom;sharedView.center=event.center;sharedView.zoom=event.zoom}
            MapEvent.GestureStarted -> {os.follow=false;os.showCrosshair=true;sharedView.selectedPlaceId=null;sharedView.selectedAisMmsi=null}
            is MapEvent.CoordinateSelected -> {
                if(event.longPress&&os.editingRoute) {
                    nearestDraftInsertion(event.point)?.let {(index,inserted)->
                        insertRouteDraftControlPoint(os,index,inserted);selectedDraftPointIndex=null
                    }
                } else {os.follow=false;os.showCrosshair=true}
            }
            is MapEvent.ItemSelected -> if(event.id.startsWith("route:")&&os.editingRoute) {
                selectedDraftPointIndex=event.id.substringAfter(':').toIntOrNull()?.takeIf{it in os.draftRoute.indices}
            }else if(event.id.startsWith("enc:")||event.id.startsWith("raster:")) {
                sharedView.selectedPlaceId=null;sharedView.selectedAisMmsi=null;os.showCrosshair=false
            }else if(event.id.startsWith("ais:")&&!os.editingRoute&&os.ruler.isEmpty()) {
                sharedView.selectedAisMmsi=event.id.substringAfter(':');sharedView.selectedPlaceId=null;os.showCrosshair=false
            } else if(event.id.startsWith("place:")) {
                val id=event.id.removePrefix("place:")
                os.allPlaces.firstOrNull {it.id==id}?.let {place ->
                    sharedView.selectedPlaceId=id;sharedView.selectedAisMmsi=null;os.showCrosshair=false
                }
            }
            is MapEvent.PointMoved -> when {
                event.id.startsWith("ruler:") -> {val index=event.id.substringAfter(':').toIntOrNull();os.ruler=os.ruler.mapIndexed {i,p ->if(i==index)event.point else p}}
                event.id.startsWith("route:") && os.editingRoute -> event.id.substringAfter(':').toIntOrNull()?.let{index->moveRouteDraftPoint(os,index,event.point)}
            }
        }})
    selectedDraftPointIndex?.let {index->
        os.draftRoute.getOrNull(index)?.let {point->
            val manual=index in routeDraftControlIndices(os.draftRoute,os.draftNavigationTargetIndices)
            AppDialog(onDismissRequest={selectedDraftPointIndex=null}) {AppDialogSurface {
                AppDialogTitle(os.t("航线点 ${index+1}","Route point ${index+1}"))
                Label(os.formatCoordinates(point),14,LocalMetro.current.muted)
                Label(if(manual)os.t("人工控制点 · 自动规划会保留这个位置","Manual control point · auto planning preserves this position")
                    else os.t("自动形状点 · 拖动后会变成人工控制点","Automatic shape point · dragging it makes it a manual control point"),13,LocalMetro.current.muted)
                MetroButton(os.t("删除这个点","Delete this point"),{
                    deleteRouteDraftPoint(os,index);selectedDraftPointIndex=null
                },primary=true)
                MetroButton(os.t("关闭","Close"),{selectedDraftPointIndex=null})
            }}
        }
    }
}

/** 相机只接收边界极值，不将十万测深点送到主线程取景；日期变更线使用最小经度弧。 */
private suspend fun libraryObjectExtent(feature:NauticalFeature):List<GeoPoint> {
    var south=90.0;var north=-90.0
    val longitudes=ArrayList<Double>()
    for(part in feature.geometry.parts)for((index,point) in part.points.withIndex()) {
        if(index%256==0)currentCoroutineContext().ensureActive()
        if(!point.latitude.isFinite()||!point.longitude.isFinite()||point.latitude !in -90.0..90.0||point.longitude !in -180.0..180.0)continue
        south=minOf(south,point.latitude);north=maxOf(north,point.latitude);longitudes+=point.longitude
    }
    if(longitudes.isEmpty())return emptyList()
    longitudes.sort()
    if(longitudes.first()==longitudes.last()&&south==north)return listOf(GeoPoint(south,longitudes.first()))
    var gapIndex=longitudes.lastIndex;var largest=longitudes.first()+360.0-longitudes.last()
    for(index in 0 until longitudes.lastIndex) {
        if(index%256==0)currentCoroutineContext().ensureActive()
        val gap=longitudes[index+1]-longitudes[index]
        if(gap>largest){largest=gap;gapIndex=index}
    }
    val west=longitudes[(gapIndex+1)%longitudes.size];val east=longitudes[gapIndex]
    return listOf(GeoPoint(south,west),GeoPoint(north,west),GeoPoint(north,east),GeoPoint(south,east))
}

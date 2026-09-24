package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.marine.shell.rebuild.data.Fix
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.compose.LocalInternalAppPageKey
import com.yokuli.shell.engine.ShellVisualSurface
import com.yokuli.shell.engine.currentUiStateKey

/** Chart owns its route/mark editing; the renderer receives geometry and returns gestures. */
@Composable
fun NativeChart(os: OsStore, fix: Fix?, modifier: Modifier = Modifier, onHost: (ChartHost) -> Unit) {
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
    // 原生地图在转场期间仍会回调相机位置。每次访问用自己的相机状态，离场页不能写回新页。
    val view = remember(instanceKey) { MapViewState(os.center, os.zoom) }
    var nativeHost by remember(instanceKey) { mutableStateOf<ChartHost?>(null) }
    view.interactive = active && LocalInternalAppInputEnabled.current
    if(active) {
        view.follow = os.follow
        view.showCrosshair = os.showCrosshair || os.editingRoute
        view.ruler = os.ruler
        view.bottomOverlayDp = sharedView.bottomOverlayDp
        os.cameraRequest?.let { (point, zoom) -> view.fly(point, zoom); os.cameraRequest = null }
        os.fitRequest?.takeIf { it.isNotEmpty() }?.let { view.fit(it); os.fitRequest = null; os.follow = false }
    }
    SideEffect { nativeHost?.captureForTile = active }
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
    val markers = remember(points,os.editingRoute,navigating,os.routeLeg,allPlaces,sharedView.selectedPlaceId,accent) {buildList {
        points.forEachIndexed {i,p -> add(MapPoint("route:$i",p,(i+1).toString(),if(navigating && i<os.routeLeg)0xFF7D898C else accent,
            if(os.editingRoute)14f else if(navigating && i==os.routeLeg)16f else 10f,os.editingRoute))}
        allPlaces.forEach {add(MapPoint("place:${it.id}",it.point,"",if(sharedView.selectedPlaceId==it.id)0xFFD74A29 else accent,if(sharedView.selectedPlaceId==it.id)12f else 5f))}
    }}
    val aisTargets=remember(traffic.targets,traffic.preferences.chartLayer,sharedView.previewTrack.isEmpty(),sharedView.selectedAisMmsi) {
        if(traffic.preferences.chartLayer&&sharedView.previewTrack.isEmpty())aisMapTargets(traffic,sharedView.selectedAisMmsi)else emptyList()
    }
    MarineMap(os.maps,MapScene(fix?.let {MapVessel(it.point,it.freshCourse(now),it.fresh(now),it.freshHeading(now),it.freshSpeed(now))},markers,lines,
        demo=os.positionSource=="demo" || os.marine?.services?.state?.value?.settings?.demoMode==true,
        aisTargets=aisTargets,
        aisInteractive=!os.editingRoute&&os.ruler.isEmpty()&&sharedView.previewTrack.isEmpty()),view,modifier,
        onHost={host -> nativeHost=host;host.captureForTile=active;onHost(host)},onEvent={event ->if(isCurrent())when(event) {
            is MapEvent.CameraChanged -> {os.center=event.center;os.zoom=event.zoom;sharedView.center=event.center;sharedView.zoom=event.zoom}
            MapEvent.GestureStarted -> {os.follow=false;os.showCrosshair=true;sharedView.selectedPlaceId=null;sharedView.selectedAisMmsi=null}
            is MapEvent.CoordinateSelected -> {os.follow=false;os.showCrosshair=true}
            is MapEvent.ItemSelected -> if(event.id.startsWith("ais:")&&!os.editingRoute&&os.ruler.isEmpty()) {
                sharedView.selectedAisMmsi=event.id.substringAfter(':');sharedView.selectedPlaceId=null;os.showCrosshair=false
            } else if(event.id.startsWith("place:")) {
                val id=event.id.removePrefix("place:")
                os.allPlaces.firstOrNull {it.id==id}?.let {place ->
                    sharedView.selectedPlaceId=id;sharedView.selectedAisMmsi=null;os.showCrosshair=false
                }
            }
            is MapEvent.PointMoved -> when {
                event.id.startsWith("ruler:") -> {val index=event.id.substringAfter(':').toIntOrNull();os.ruler=os.ruler.mapIndexed {i,p ->if(i==index)event.point else p}}
                event.id.startsWith("route:") && os.editingRoute -> {val index=event.id.substringAfter(':').toIntOrNull();os.draftRoute=os.draftRoute.mapIndexed {i,p ->if(i==index)event.point else p}}
            }
        }})
}

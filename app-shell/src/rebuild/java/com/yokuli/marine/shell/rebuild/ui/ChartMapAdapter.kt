package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.marine.shell.rebuild.data.Fix

/** Chart owns its route/mark editing; the renderer receives geometry and returns gestures. */
@Composable
fun NativeChart(os: OsStore, fix: Fix?, modifier: Modifier = Modifier, onHost: (ChartHost) -> Unit) {
    val view = os.maps.view("chart", os.center, os.zoom)
    view.follow = os.follow
    view.showCrosshair = os.showCrosshair || os.editingRoute
    view.ruler = os.ruler
    os.cameraRequest?.let { (point, zoom) -> view.fly(point, zoom); os.cameraRequest = null }
    os.fitRequest?.takeIf { it.isNotEmpty() }?.let { view.fit(it); os.fitRequest = null; os.follow = false }
    val route = if(os.displayedRouteId!=null && os.displayedRouteId!=os.activeRouteId) os.routes.firstOrNull {it.id==os.displayedRouteId} else os.activeRoute
    val navigating = !os.editingRoute && route != null && route.id == os.activeRouteId
    val points = if (os.editingRoute) os.draftRoute else route?.points.orEmpty()
    val accent = os.accent
    view.scaleTopDp=118f
    val lines = buildList {
        view.previewTrack.forEachIndexed { i, segment -> add(MapLine("voyage-preview:$i",segment,0xFFDE8531,3f)) }
        if(os.recordingActive) os.recordedSegments.forEachIndexed {i,segment -> add(MapLine("recording:$i",segment,0xFF008B8E,2.5f))}
        if(points.isNotEmpty()) add(MapLine("route",points,if(navigating)0xFF7D898C else accent,2.6f))
        if(navigating) add(MapLine("remaining",points.drop((os.routeLeg-1).coerceAtLeast(0)),accent))
        if(navigating && fix?.fresh()==true) os.nextPoint?.let {add(MapLine("target",listOf(fix.point,it),accent,2f,true))}
    }
    val markers = buildList {
        points.forEachIndexed {i,p -> add(MapPoint("route:$i",p,(i+1).toString(),if(navigating && i<os.routeLeg)0xFF7D898C else accent,
            if(os.editingRoute)14f else if(navigating && i==os.routeLeg)16f else 10f,os.editingRoute))}
        os.allPlaces.forEach {add(MapPoint("place:${it.id}",it.point,"",if(view.selectedPlaceId==it.id)0xFFD74A29 else accent,if(view.selectedPlaceId==it.id)12f else 5f))}
    }
    MarineMap(os.maps,MapScene(fix?.let {MapVessel(it.point,it.freshCourse(),it.fresh())},markers,lines,
        demo=os.positionSource=="demo" || os.marine?.vm?.ui?.value?.settings?.demoMode==true),view,modifier,
        onHost={host -> host.captureForTile=true;onHost(host)},onEvent={event ->when(event) {
            is MapEvent.CameraChanged -> {os.center=event.center;os.zoom=event.zoom}
            MapEvent.GestureStarted -> {os.follow=false;os.showCrosshair=true;view.selectedPlaceId=null}
            is MapEvent.CoordinateSelected -> {os.follow=false;os.showCrosshair=true}
            is MapEvent.ItemSelected -> if(event.id.startsWith("place:")) {
                val id=event.id.removePrefix("place:")
                os.allPlaces.firstOrNull {it.id==id}?.let {place ->
                    view.selectedPlaceId=id;os.fly(place.point);os.showCrosshair=false;os.follow=false
                }
            }
            is MapEvent.PointMoved -> when {
                event.id.startsWith("ruler:") -> {val index=event.id.substringAfter(':').toIntOrNull();os.ruler=os.ruler.mapIndexed {i,p ->if(i==index)event.point else p}}
                event.id.startsWith("route:") && os.editingRoute -> {val index=event.id.substringAfter(':').toIntOrNull();os.draftRoute=os.draftRoute.mapIndexed {i,p ->if(i==index)event.point else p}}
            }
        }})
}

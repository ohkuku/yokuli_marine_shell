package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as GoogleMapView
import com.google.android.gms.maps.CameraUpdateFactory as GoogleCamera
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.Fix
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.*

interface ChartCamera {
    fun project(point:GeoPoint):PointF
    fun unproject(x:Float,y:Float):GeoPoint
    fun move(point:GeoPoint,zoom:Double)
    fun zoom():Double
    fun fit(points:List<GeoPoint>)
}

/** Pins intercept only their own DOWN. Every other touch goes straight to the native map. */
class ChartOverlay(context:Context, private val os:OsStore) : View(context) {
    var camera:ChartCamera?=null
    var fix:Fix?=null
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var handle:Int?=null
    private var startX=0f; private var startY=0f
    private var pinOffset=PointF()
    var placeClick:((String)->Unit)?=null
    private fun project(p:GeoPoint)=camera?.project(p) ?: PointF()
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas); if(camera==null) return
        fun line(points:List<GeoPoint>,color:Int,width:Float,dashed:Boolean=false) {
            if(points.isEmpty()) return
            paint.color=color; paint.style=Paint.Style.STROKE; paint.strokeWidth=width*density
            paint.pathEffect=if(dashed) android.graphics.DashPathEffect(floatArrayOf(7*density,5*density),0f) else null
            val path=Path(); points.forEachIndexed { i,p -> val s=project(p); if(i==0) path.moveTo(s.x,s.y) else path.lineTo(s.x,s.y) }
            canvas.drawPath(path,paint); paint.pathEffect=null
        }
        val route=if(os.editingRoute) os.draftRoute else os.routes.firstOrNull { it.id==(os.displayedRouteId ?: os.activeRouteId) }?.points.orEmpty()
        line(route,android.graphics.Color.WHITE,5f)
        line(route,os.accent.toInt(),2.6f)
        fun pin(p:GeoPoint,text:String,accent:Int=os.accent.toInt(),radius:Float=14f) {
            val s=project(p); if(s.x !in -80f..width+80f || s.y !in -80f..height+80f) return
            val r=radius*density
            paint.style=Paint.Style.FILL; paint.color=android.graphics.Color.WHITE; canvas.drawCircle(s.x,s.y,r+2*density,paint)
            paint.color=accent; canvas.drawCircle(s.x,s.y,r,paint)
            paint.color=android.graphics.Color.WHITE; paint.textSize=13*density; paint.typeface=android.graphics.Typeface.create("sans-serif-medium",0); paint.textAlign=Paint.Align.CENTER
            canvas.drawText(text,s.x,s.y+4.5f*density,paint)
        }
        for((index,p) in route.withIndex()) pin(p,(index+1).toString(),radius=if(os.editingRoute) 14f else 10f)
        os.places.forEach { place ->
            val p=project(place.point)
            paint.color=android.graphics.Color.WHITE; paint.strokeWidth=4*density; canvas.drawLine(p.x,p.y,p.x,p.y-16*density,paint)
            paint.color=os.accent.toInt(); paint.strokeWidth=2*density; canvas.drawLine(p.x,p.y,p.x,p.y-16*density,paint)
            paint.style=Paint.Style.FILL; canvas.drawCircle(p.x,p.y-18*density,5*density,paint)
        }
        if(os.ruler.size==2) {
            line(os.ruler,android.graphics.Color.WHITE,5f); line(os.ruler,android.graphics.Color.rgb(215,74,41),2.6f,true)
            pin(os.ruler[0],"A",android.graphics.Color.rgb(215,74,41),18f); pin(os.ruler[1],"B",android.graphics.Color.rgb(215,74,41),18f)
        }
        fix?.let { f ->
            val p=project(f.point); val fresh=f.fresh()
            if(os.activeRouteId!=null && fresh) os.nextPoint?.let { line(listOf(f.point,it),os.accent.toInt(),2f,true) }
            paint.color=android.graphics.Color.WHITE; paint.style=Paint.Style.FILL; canvas.drawCircle(p.x,p.y,13*density,paint)
            paint.color=if(fresh) android.graphics.Color.rgb(0,122,220) else android.graphics.Color.GRAY
            if(fresh && f.freshCourse()!=null) {
                canvas.save(); canvas.rotate(f.freshCourse()!!.toFloat(),p.x,p.y)
                val path=Path(); path.moveTo(p.x,p.y-17*density); path.lineTo(p.x-9*density,p.y+11*density); path.lineTo(p.x,p.y+6*density); path.lineTo(p.x+9*density,p.y+11*density); path.close()
                canvas.drawPath(path,paint); canvas.restore()
            } else { paint.style=if(fresh) Paint.Style.FILL else Paint.Style.STROKE; paint.strokeWidth=3*density; canvas.drawCircle(p.x,p.y,9*density,paint) }
        }
        if(os.showCrosshair || os.editingRoute) {
            val x=width/2f; val y=height/2f
            for((color,w) in listOf(android.graphics.Color.WHITE to 4f,android.graphics.Color.rgb(20,34,43) to 1.5f)) {
                paint.color=color; paint.strokeWidth=w*density; paint.style=Paint.Style.STROKE
                canvas.drawCircle(x,y,10*density,paint)
                canvas.drawLine(x-22*density,y,x-5*density,y,paint); canvas.drawLine(x+5*density,y,x+22*density,y,paint)
                canvas.drawLine(x,y-22*density,x,y-5*density,paint); canvas.drawLine(x,y+5*density,x,y+22*density,paint)
            }
        }
        val cam=camera ?: return
        val baseX=18*density; val baseY=height-18*density
        val meters=distance(cam.unproject(baseX,baseY),cam.unproject(baseX+90*density,baseY))
        paint.style=Paint.Style.FILL; paint.color=0xDFFFFFFF.toInt(); canvas.drawRect(baseX-6*density,baseY-26*density,baseX+100*density,baseY+6*density,paint)
        paint.color=android.graphics.Color.rgb(25,37,43); paint.strokeWidth=2*density
        canvas.drawLine(baseX,baseY,baseX+90*density,baseY,paint); canvas.drawLine(baseX,baseY-4*density,baseX,baseY,paint); canvas.drawLine(baseX+90*density,baseY-4*density,baseX+90*density,baseY,paint)
        paint.textSize=12*density; paint.textAlign=Paint.Align.LEFT; canvas.drawText(nm(meters),baseX,baseY-8*density,paint)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        val cam=camera ?: return false
        val points=if(os.ruler.size==2) os.ruler else if(os.editingRoute) os.draftRoute else emptyList()
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX=event.x; startY=event.y
                handle=points.indices.minByOrNull { i -> val p=cam.project(points[i]); hypot(p.x-event.x,p.y-event.y) }
                    ?.takeIf { i -> val p=cam.project(points[i]); hypot(p.x-event.x,p.y-event.y)<32*density }
                if(handle!=null) {
                    val p=cam.project(points[handle!!]); pinOffset=PointF(p.x-event.x,p.y-event.y)
                    parent.requestDisallowInterceptTouchEvent(true); return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val index=handle ?: return false
                val p=cam.unproject(event.x+pinOffset.x,event.y+pinOffset.y)
                if(os.ruler.size==2) os.ruler=os.ruler.mapIndexed { i,v -> if(i==index) p else v }
                else os.draftRoute=os.draftRoute.mapIndexed { i,v -> if(i==index) p else v }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                val consumed=handle!=null; handle=null; parent.requestDisallowInterceptTouchEvent(false); return consumed
            }
        }
        return handle!=null
    }
}

class ChartHost(context:Context,val os:OsStore,private val mode:String) : FrameLayout(context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var native:MapView?=null
    private var google:GoogleMapView?=null
    private var gateway:TileGateway?=null
    private var styleRevision=""
    private var styleJob:Job?=null
    private var started=false; private var resumed=false; private var destroyed=false
    val overlay=ChartOverlay(context,os)
    var camera:ChartCamera?=null
    var error by mutableStateOf<String?>(null)
    private var libre:MapLibreMap?=null
    init {
        setBackgroundColor(android.graphics.Color.rgb(222,233,232))
        if(mode in listOf("standard","satellite") && BuildConfig.GOOGLE_MAPS_CONFIGURED) initGoogle() else initLibre()
        addView(overlay,LayoutParams(-1,-1))
    }
    private fun moved(center:GeoPoint,z:Double) { os.center=center; os.zoom=z; overlay.invalidate() }
    private fun touch() { os.follow=false; os.showCrosshair=true }
    private fun pick(point:GeoPoint) {
        val nearby=os.places.minByOrNull { distance(it.point,point) }
        if(nearby!=null && !os.editingRoute && os.ruler.isEmpty()) {
            val a=camera?.project(nearby.point); val b=camera?.project(point)
            if(a!=null && b!=null && hypot(a.x-b.x,a.y-b.y)<28*resources.displayMetrics.density) { os.open("place:${nearby.id}"); return }
        }
        os.showCrosshair=true; os.fly(point)
    }
    private fun initGoogle() {
        google=GoogleMapView(context).also { v ->
            addView(v,LayoutParams(-1,-1)); v.onCreate(Bundle())
            v.getMapAsync { map ->
                if(destroyed) return@getMapAsync
                map.mapType=if(mode=="satellite") GoogleMap.MAP_TYPE_SATELLITE else GoogleMap.MAP_TYPE_NORMAL
                map.uiSettings.apply { isMapToolbarEnabled=false; isMyLocationButtonEnabled=false; isCompassEnabled=false; isRotateGesturesEnabled=false; isTiltGesturesEnabled=false }
                val adapter=object:ChartCamera {
                    override fun project(point:GeoPoint)=map.projection.toScreenLocation(GoogleLatLng(point.lat,point.lon)).let { PointF(it.x.toFloat(),it.y.toFloat()) }
                    override fun unproject(x:Float,y:Float)=map.projection.fromScreenLocation(android.graphics.Point(x.toInt(),y.toInt())).let { GeoPoint(it.latitude,it.longitude) }
                    override fun move(point:GeoPoint,zoom:Double) { map.moveCamera(GoogleCamera.newLatLngZoom(GoogleLatLng(point.lat,point.lon),zoom.toFloat())) }
                    override fun zoom()=map.cameraPosition.zoom.toDouble()
                    override fun fit(points:List<GeoPoint>) {
                        if(points.size==1) move(points.first(),os.zoom) else {
                            val bounds=com.google.android.gms.maps.model.LatLngBounds.builder()
                            points.forEach {bounds.include(GoogleLatLng(it.lat,it.lon))}
                            map.moveCamera(GoogleCamera.newLatLngBounds(bounds.build(),(48*resources.displayMetrics.density).toInt()))
                        }
                    }
                }
                camera=adapter; overlay.camera=adapter; adapter.move(os.center,os.zoom)
                map.setOnCameraMoveListener { val p=map.cameraPosition; moved(GeoPoint(p.target.latitude,p.target.longitude),p.zoom.toDouble()) }
                map.setOnCameraMoveStartedListener { if(it==GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) touch() }
                map.setOnMapClickListener { pick(GeoPoint(it.latitude,it.longitude)) }
                map.setOnMapLongClickListener { pick(GeoPoint(it.latitude,it.longitude)) }
            }
        }
    }
    private fun initLibre() {
        MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent","YokuliOS/0.2 (+https://github.com/ohkuku/yokuli_marine_shell)").build()) }.build())
        native=MapView(context).also { v ->
            addView(v,LayoutParams(-1,-1)); v.onCreate(Bundle())
            v.getMapAsync { map ->
                if(destroyed) return@getMapAsync
                libre=map
                map.uiSettings.apply { isLogoEnabled=false; isAttributionEnabled=false; isCompassEnabled=false; isRotateGesturesEnabled=false; isTiltGesturesEnabled=false }
                map.setPrefetchZoomDelta(0); map.setPrefetchesTiles(false); map.setMaxZoomPreference(22.0); map.setMinZoomPreference(1.0)
                val adapter=object:ChartCamera {
                    override fun project(point:GeoPoint)=map.projection.toScreenLocation(LatLng(point.lat,point.lon))
                    override fun unproject(x:Float,y:Float)=map.projection.fromScreenLocation(PointF(x,y)).let { GeoPoint(it.latitude,it.longitude) }
                    override fun move(point:GeoPoint,zoom:Double) { map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat,point.lon),zoom)) }
                    override fun zoom()=map.cameraPosition.zoom
                    override fun fit(points:List<GeoPoint>) {
                        if(points.size==1) move(points.first(),os.zoom) else {
                            val bounds=org.maplibre.android.geometry.LatLngBounds.Builder()
                            points.forEach {bounds.include(LatLng(it.lat,it.lon))}
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),(48*resources.displayMetrics.density).toInt()))
                        }
                    }
                }
                camera=adapter; overlay.camera=adapter; adapter.move(os.center,os.zoom)
                map.addOnCameraMoveListener { map.cameraPosition.target?.let { moved(GeoPoint(it.latitude,it.longitude),map.cameraPosition.zoom) } }
                map.addOnCameraMoveStartedListener { if(it==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) touch() }
                map.addOnMapClickListener { pick(GeoPoint(it.latitude,it.longitude)); true }
                map.addOnMapLongClickListener { pick(GeoPoint(it.latitude,it.longitude)); true }
                updateStyle()
            }
        }
    }
    fun updateStyle() {
        val map=libre ?: return
        val selected=os.library.selected
        val key=mode+selected.joinToString { "${it.id}:${it.modified}" }
        if(styleRevision==key) return
        styleRevision=key; styleJob?.cancel(); error=null
        styleJob=scope.launch {
            var proposed:TileGateway?=null
            try {
                val sources=JSONObject(); val layers=JSONArray().put(JSONObject().put("id","water").put("type","background").put("paint",JSONObject().put("background-color","#dee9e8")))
                if(mode=="standard") {
                    sources.put("osm",JSONObject().put("type","raster").put("tileSize",256).put("maxzoom",19)
                        .put("tiles",JSONArray(listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))))
                    layers.put(JSONObject().put("id","osm").put("type","raster").put("source","osm"))
                } else {
                    val mounted=withContext(Dispatchers.IO) {
                        TileGateway(context).also { proposed=it }.let { gateway -> selected.mapNotNull { chart ->
                            runCatching { chart to gateway.register(chart) }.getOrElse { withContext(Dispatchers.Main) { error="read" }; null }
                        } }
                    }
                    for((chart,url) in mounted) {
                        sources.put(chart.id,JSONObject().put("type","raster").put("tileSize",chart.tileSize).put("minzoom",chart.minZoom).put("maxzoom",chart.maxZoom)
                            .put("tiles",JSONArray(listOf(url))))
                        layers.put(JSONObject().put("id",chart.id).put("type","raster").put("source",chart.id)
                            .put("paint",JSONObject().put("raster-fade-duration",0).put("raster-resampling","linear")))
                    }
                }
                val style=JSONObject().put("version",8).put("sources",sources).put("layers",layers)
                val old=gateway; gateway=proposed
                map.setStyle(Style.Builder().fromJson(style.toString())) { old?.close() }
            } catch(e:Exception) { proposed?.close(); if(e !is CancellationException) error="read" }
        }
    }
    fun update(fix:Fix?) {
        overlay.fix=fix; overlay.invalidate(); updateStyle()
        if(camera!=null) os.cameraRequest?.let { (p,z) -> camera?.move(p,z); os.cameraRequest=null }
        if(camera!=null && width>0 && height>0) os.fitRequest?.takeIf {it.isNotEmpty()}?.let { points ->
            camera?.fit(points);os.fitRequest=null;os.follow=false
        }
        if(os.follow && fix?.fresh()==true) camera?.move(fix.point,os.zoom)
    }
    fun lifecycle(event:Lifecycle.Event) {
        when(event) {
            Lifecycle.Event.ON_START -> if(!started) { native?.onStart(); google?.onStart(); started=true }
            Lifecycle.Event.ON_RESUME -> if(!resumed) { lifecycle(Lifecycle.Event.ON_START); native?.onResume(); google?.onResume(); resumed=true }
            Lifecycle.Event.ON_PAUSE -> if(resumed) { native?.onPause(); google?.onPause(); resumed=false }
            Lifecycle.Event.ON_STOP -> if(started) { lifecycle(Lifecycle.Event.ON_PAUSE); native?.onStop(); google?.onStop(); started=false }
            else -> Unit
        }
    }
    fun destroy() {
        if(destroyed) return
        destroyed=true; lifecycle(Lifecycle.Event.ON_STOP); scope.cancel()
        native?.onDestroy(); google?.onDestroy(); gateway?.close(); camera=null
    }
}

@Composable fun NativeChart(os:OsStore,fix:Fix?,modifier:Modifier=Modifier,onHost:(ChartHost)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val host=remember(os.mapMode) { ChartHost(context,os,os.mapMode) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(host,lifecycle) {
        onHost(host)
        val observer=LifecycleEventObserver { _,event -> host.lifecycle(event) }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) host.lifecycle(Lifecycle.Event.ON_START)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) host.lifecycle(Lifecycle.Event.ON_RESUME)
        onDispose { lifecycle.removeObserver(observer); host.destroy() }
    }
    AndroidView(factory={host},modifier=modifier,update={ it.update(fix) })
}

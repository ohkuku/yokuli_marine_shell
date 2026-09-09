package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.distance
import com.yokuli.marine.shell.rebuild.nm
import com.yokuli.marine.shell.rebuild.ui.Label
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
    fun project(point: GeoPoint): PointF
    fun unproject(x: Float, y: Float): GeoPoint
    fun move(point: GeoPoint, zoom: Double)
    fun zoom(): Double
    fun fit(points: List<GeoPoint>)
}

/** Only draggable handles intercept touches. All other gestures reach the native map. */
class ChartOverlay(context: Context, private val state: MapViewState) : View(context) {
    var camera: ChartCamera? = null
    var scene = MapScene()
    var onEvent: (MapEvent) -> Unit = {}
    var scaleBottomOffsetDp = 0f
    var distanceLabel: (Double) -> String = ::nm
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var handle: MapPoint? = null
    private var pinOffset = PointF()
    private fun project(p: GeoPoint) = camera?.project(p) ?: PointF()
    private fun line(canvas: Canvas, points: List<GeoPoint>, color: Int, width: Float, dashed: Boolean = false) {
        if (points.isEmpty()) return
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = width * density
        paint.pathEffect = if (dashed) android.graphics.DashPathEffect(floatArrayOf(7*density,5*density),0f) else null
        val path = Path()
        points.forEachIndexed { index,p -> val s=project(p); if(index==0) path.moveTo(s.x,s.y) else path.lineTo(s.x,s.y) }
        canvas.drawPath(path,paint); paint.pathEffect=null
    }
    private fun pin(canvas: Canvas, item: MapPoint) {
        val p=project(item.point); if(p.x !in -80f..width+80f || p.y !in -80f..height+80f) return
        val r=item.radiusDp*density
        paint.style=Paint.Style.FILL; paint.color=android.graphics.Color.WHITE;canvas.drawCircle(p.x,p.y,r+2*density,paint)
        paint.color=item.color.toInt();canvas.drawCircle(p.x,p.y,r,paint)
        if(item.label.isNotBlank()) {
            paint.color=android.graphics.Color.WHITE;paint.textSize=13*density;paint.typeface=android.graphics.Typeface.create("sans-serif-medium",0);paint.textAlign=Paint.Align.CENTER
            canvas.drawText(item.label,p.x,p.y+4.5f*density,paint)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);val cam=camera ?: return
        scene.circles.filter {it.radiusMeters.isFinite() && it.radiusMeters>0}.forEach { circle ->
            val ring=(0..72).map { i -> destination(circle.center,circle.radiusMeters,i*5.0) }
            val path=Path();ring.forEachIndexed {i,p -> val s=project(p);if(i==0) path.moveTo(s.x,s.y) else path.lineTo(s.x,s.y)};path.close()
            paint.style=Paint.Style.FILL;paint.color=(circle.color.toInt() and 0xFFFFFF) or 0x18000000;canvas.drawPath(path,paint)
            line(canvas,ring,android.graphics.Color.WHITE,3.5f,circle.dashed);line(canvas,ring,circle.color.toInt(),1.8f,circle.dashed)
        }
        scene.lines.forEach { item ->line(canvas,item.points,android.graphics.Color.WHITE,item.widthDp+2f,item.dashed);line(canvas,item.points,item.color.toInt(),item.widthDp,item.dashed)}
        scene.points.forEach {pin(canvas,it)}
        if(state.ruler.size==2) {
            line(canvas,state.ruler,android.graphics.Color.WHITE,5f,true);line(canvas,state.ruler,0xFFD74A29.toInt(),2.6f,true)
            state.ruler.forEachIndexed {i,p ->pin(canvas,MapPoint("ruler:$i",p,if(i==0)"A" else "B",0xFFD74A29,18f,true))}
        }
        scene.vessel?.let {f ->
            val p=project(f.point)
            paint.style=Paint.Style.FILL;paint.color=android.graphics.Color.WHITE;canvas.drawCircle(p.x,p.y,13*density,paint)
            paint.color=if(f.fresh) 0xFF007ADC.toInt() else android.graphics.Color.GRAY
            if(f.fresh && f.courseDegrees!=null) {
                canvas.save();canvas.rotate(f.courseDegrees.toFloat(),p.x,p.y)
                val path=Path();path.moveTo(p.x,p.y-17*density);path.lineTo(p.x-9*density,p.y+11*density);path.lineTo(p.x,p.y+6*density);path.lineTo(p.x+9*density,p.y+11*density);path.close();canvas.drawPath(path,paint);canvas.restore()
            } else {paint.style=if(f.fresh) Paint.Style.FILL else Paint.Style.STROKE;paint.strokeWidth=3*density;canvas.drawCircle(p.x,p.y,9*density,paint)}
        }
        if(state.showCrosshair) {
            val x=width/2f;val y=height/2f
            for((color,w) in listOf(android.graphics.Color.WHITE to 4f,0xFF14222B.toInt() to 1.5f)) {
                paint.color=color;paint.strokeWidth=w*density;paint.style=Paint.Style.STROKE;canvas.drawCircle(x,y,10*density,paint)
                canvas.drawLine(x-22*density,y,x-5*density,y,paint);canvas.drawLine(x+5*density,y,x+22*density,y,paint)
                canvas.drawLine(x,y-22*density,x,y-5*density,paint);canvas.drawLine(x,y+5*density,x,y+22*density,paint)
            }
        }
        val x=18*density;val y=height-(18+scaleBottomOffsetDp)*density
        val meters=distance(cam.unproject(x,y),cam.unproject(x+90*density,y))
        paint.style=Paint.Style.FILL;paint.color=0xDFFFFFFF.toInt();canvas.drawRect(x-6*density,y-26*density,x+100*density,y+6*density,paint)
        paint.color=0xFF19252B.toInt();paint.strokeWidth=2*density;canvas.drawLine(x,y,x+90*density,y,paint);canvas.drawLine(x,y-4*density,x,y,paint);canvas.drawLine(x+90*density,y-4*density,x+90*density,y,paint)
        paint.textSize=12*density;paint.textAlign=Paint.Align.LEFT;canvas.drawText(distanceLabel(meters),x,y-8*density,paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cam=camera ?: return false
        if(!state.interactive) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val handles=if(state.ruler.size==2) state.ruler.mapIndexed {i,p ->MapPoint("ruler:$i",p,draggable=true)} else scene.points.filter {it.draggable}
                handle=handles.minByOrNull {val p=project(it.point);hypot(p.x-event.x,p.y-event.y)}?.takeIf {val p=project(it.point);hypot(p.x-event.x,p.y-event.y)<32*density}
                handle?.let {val p=project(it.point);pinOffset=PointF(p.x-event.x,p.y-event.y);parent.requestDisallowInterceptTouchEvent(true);return true}
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val item=handle ?: return false;val point=cam.unproject(event.x+pinOffset.x,event.y+pinOffset.y)
                if(item.id.startsWith("ruler:")) {val index=item.id.substringAfter(':').toInt();state.ruler=state.ruler.mapIndexed {i,p ->if(i==index) point else p}}
                onEvent(MapEvent.PointMoved(item.id,point))
                invalidate();return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {val used=handle!=null;handle=null;parent.requestDisallowInterceptTouchEvent(false);return used}
        }
        return handle!=null
    }
}

private fun destination(p: GeoPoint, meters: Double, degrees: Double): GeoPoint {
    val d=meters/6371008.8;val b=Math.toRadians(degrees);val lat=Math.toRadians(p.lat);val lon=Math.toRadians(p.lon)
    val next=asin(sin(lat)*cos(d)+cos(lat)*sin(d)*cos(b))
    return GeoPoint(Math.toDegrees(next),((Math.toDegrees(lon+atan2(sin(b)*sin(d)*cos(lat),cos(d)-sin(lat)*sin(next)))+540)%360)-180)
}

class ChartHost(context: Context, private val maps: MapSessionStore, private val state: MapViewState, private val googleEngine: Boolean) : FrameLayout(context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var native: MapView?=null
    private var google: GoogleMapView?=null
    private var googleMap: GoogleMap?=null
    private var libre: MapLibreMap?=null
    private var gateway: TileGateway?=null
    private var styleRevision=""
    private var styleJob: Job?=null
    private var started=false;private var resumed=false;private var destroyed=false
    private var sourceGeneration=0L
    private var lastRequest=0L
    private var lastFollowPoint: GeoPoint?=null
    var captureForTile = false
    private var captureJob: Job? = null
    val overlay=ChartOverlay(context,state)
    var camera: ChartCamera?=null
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var onEvent: (MapEvent)->Unit={}
    init {
        setBackgroundColor(0xFFDEE9E8.toInt())
        if(googleEngine) {overlay.scaleBottomOffsetDp=28f;initGoogle()} else initLibre()
        addView(overlay,LayoutParams(-1,-1))
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN && state.interactive)parent?.requestDisallowInterceptTouchEvent(true)
        val handled=super.dispatchTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL)parent?.requestDisallowInterceptTouchEvent(false)
        return handled
    }
    private fun moved(center: GeoPoint,z: Double) {state.center=center;state.zoom=z;overlay.invalidate();onEvent(MapEvent.CameraChanged(center,z))}
    private fun touch() {if(!state.interactive)return;state.follow=false;state.showCrosshair=true;onEvent(MapEvent.GestureStarted)}
    private fun pick(point: GeoPoint) {
        if(!state.interactive) return
        val nearby=overlay.scene.points.filter {!it.draggable}.minByOrNull {distance(it.point,point)}
        if(nearby!=null && state.ruler.isEmpty()) {
            val a=camera?.project(nearby.point);val b=camera?.project(point)
            if(a!=null && b!=null && hypot(a.x-b.x,a.y-b.y)<28*resources.displayMetrics.density) {onEvent(MapEvent.ItemSelected(nearby.id));return}
        }
        state.showCrosshair=true;state.fly(point);onEvent(MapEvent.CoordinateSelected(point));updateCamera()
    }
    private fun initGoogle() {
        google=GoogleMapView(context).also {v ->
            addView(v,LayoutParams(-1,-1));v.onCreate(Bundle());v.getMapAsync {map ->
                if(destroyed)return@getMapAsync
                googleMap=map
                map.uiSettings.apply {isMapToolbarEnabled=false;isMyLocationButtonEnabled=false;isCompassEnabled=false;isRotateGesturesEnabled=false;isTiltGesturesEnabled=false}
                val adapter=object:ChartCamera {
                    override fun project(point:GeoPoint)=map.projection.toScreenLocation(GoogleLatLng(point.lat,point.lon)).let {PointF(it.x.toFloat(),it.y.toFloat())}
                    override fun unproject(x:Float,y:Float)=map.projection.fromScreenLocation(android.graphics.Point(x.toInt(),y.toInt())).let {GeoPoint(it.latitude,it.longitude)}
                    override fun move(point:GeoPoint,zoom:Double) {map.moveCamera(GoogleCamera.newLatLngZoom(GoogleLatLng(point.lat,point.lon),zoom.toFloat()))}
                    override fun zoom()=map.cameraPosition.zoom.toDouble()
                    override fun fit(points:List<GeoPoint>) {if(points.size==1) move(points.first(),state.zoom) else map.moveCamera(GoogleCamera.newLatLngBounds(com.google.android.gms.maps.model.LatLngBounds.builder().also {b ->points.forEach {b.include(GoogleLatLng(it.lat,it.lon))}}.build(),(48*resources.displayMetrics.density).toInt()))}
                }
                camera=adapter;overlay.camera=adapter;adapter.move(state.center,state.zoom)
                map.setOnCameraMoveListener {val p=map.cameraPosition;moved(GeoPoint(p.target.latitude,p.target.longitude),p.zoom.toDouble())}
                map.setOnCameraMoveStartedListener {if(it==GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE)touch()}
                map.setOnCameraIdleListener { captureSnapshot() }
                map.setOnMapClickListener {pick(GeoPoint(it.latitude,it.longitude))};map.setOnMapLongClickListener {pick(GeoPoint(it.latitude,it.longitude))}
                updateStyle();updateCamera()
            }
        }
    }
    private fun initLibre() {
        MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).addInterceptor {chain ->chain.proceed(chain.request().newBuilder().header("User-Agent","YokuliOS/0.4 (+https://github.com/ohkuku/yokuli_marine_shell)").build())}.build())
        native=MapView(context).also {v ->
            addView(v,LayoutParams(-1,-1));v.onCreate(Bundle());v.getMapAsync {map ->
                if(destroyed)return@getMapAsync
                libre=map
                map.uiSettings.apply {isLogoEnabled=false;isAttributionEnabled=false;isCompassEnabled=false;isRotateGesturesEnabled=false;isTiltGesturesEnabled=false}
                map.setPrefetchZoomDelta(0);map.setPrefetchesTiles(false);map.setMaxZoomPreference(22.0);map.setMinZoomPreference(1.0)
                val adapter=object:ChartCamera {
                    override fun project(point:GeoPoint)=map.projection.toScreenLocation(LatLng(point.lat,point.lon))
                    override fun unproject(x:Float,y:Float)=map.projection.fromScreenLocation(PointF(x,y)).let {GeoPoint(it.latitude,it.longitude)}
                    override fun move(point:GeoPoint,zoom:Double) {map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat,point.lon),zoom))}
                    override fun zoom()=map.cameraPosition.zoom
                    override fun fit(points:List<GeoPoint>) {if(points.size==1) move(points.first(),state.zoom) else map.moveCamera(CameraUpdateFactory.newLatLngBounds(org.maplibre.android.geometry.LatLngBounds.Builder().also {b ->points.forEach {b.include(LatLng(it.lat,it.lon))}}.build(),(48*resources.displayMetrics.density).toInt()))}
                }
                camera=adapter;overlay.camera=adapter;adapter.move(state.center,state.zoom)
                map.addOnCameraMoveListener {map.cameraPosition.target?.let {moved(GeoPoint(it.latitude,it.longitude),map.cameraPosition.zoom)}}
                map.addOnCameraMoveStartedListener {if(it==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)touch()}
                map.addOnCameraIdleListener { captureSnapshot() }
                map.addOnMapClickListener {pick(GeoPoint(it.latitude,it.longitude));true};map.addOnMapLongClickListener {pick(GeoPoint(it.latitude,it.longitude));true}
                updateStyle();updateCamera()
            }
        }
    }
    private fun retire(old:TileGateway?) {if(old!=null) maps.scope.launch(Dispatchers.IO) {runCatching {old.close()}}}
    fun updateStyle() {
        val source=maps.source
        val layer=maps.selectedLayer()
        val revision=if(source is MapSource.CustomLayer) "${source}:${maps.library.revision}" else source.toString()
        if(styleRevision==revision) return
        if(googleEngine && googleMap==null || !googleEngine && libre==null) return
        styleRevision=revision;styleJob?.cancel();val generation=++sourceGeneration;error=null;loading=true
        if(googleEngine) {
            googleMap?.apply {
                mapType=if(source==MapSource.Satellite) GoogleMap.MAP_TYPE_SATELLITE else GoogleMap.MAP_TYPE_NORMAL
                setOnMapLoadedCallback {if(!destroyed && generation==sourceGeneration){loading=false;error=null;captureSnapshot()}}
            }
            styleJob=scope.launch {delay(15000);if(generation==sourceGeneration && loading) {loading=false;error="online"}}
            return
        }
        styleJob=scope.launch {
            var proposed:TileGateway?=null
            try {
                // Remove the previous source while preparing the new one; no stale chart masquerades as the new layer.
                val sources=offlineWorldSources();val layers=offlineWorldLayers()
                libre?.setStyle(Style.Builder().fromJson(JSONObject().put("version",8).put("sources",sources).put("layers",layers).toString()))
                when(source) {
                    MapSource.Online -> {
                        sources.put("osm",JSONObject().put("type","raster").put("tileSize",256).put("maxzoom",19).put("tiles",JSONArray(listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))))
                        layers.put(JSONObject().put("id","osm").put("type","raster").put("source","osm"))
                    }
                    MapSource.Satellite -> error="online"
                    is MapSource.CustomLayer -> {
                        if(layer==null || layer.files.isEmpty()) error="empty"
                        else {
                            val url=withContext(Dispatchers.IO) {TileGateway(context).also {proposed=it}.register(layer)}
                            ensureActive()
                            sources.put(layer.id,JSONObject().put("type","raster").put("tileSize",256).put("minzoom",layer.minZoom).put("maxzoom",layer.maxZoom).put("tiles",JSONArray(listOf(url))))
                            layers.put(JSONObject().put("id",layer.id).put("type","raster").put("source",layer.id).put("paint",JSONObject().put("raster-fade-duration",0).put("raster-resampling","linear")))
                        }
                    }
                }
                ensureActive()
                if(generation!=sourceGeneration) {retire(proposed);return@launch}
                val old=gateway;gateway=proposed;retire(old)
                libre?.setStyle(Style.Builder().fromJson(JSONObject().put("version",8).put("sources",sources).put("layers",layers).toString())) {
                    if(!destroyed && generation==sourceGeneration) {loading=false;overlay.invalidate();captureSnapshot()}
                }
            } catch(e:Exception) {retire(proposed);if(e !is CancellationException && generation==sourceGeneration) {error="read";loading=false}}
        }
    }
    private fun updateCamera() {
        val cam=camera ?: return
        state.request?.takeIf {it.id!=lastRequest}?.let {request ->
            if(width<=0 || height<=0) return@let
            if(request.point!=null) cam.move(request.point,request.zoom) else if(request.points.isNotEmpty()) cam.fit(request.points)
            lastRequest=request.id
            if(state.request?.id==request.id)state.request=null
        }
        val vessel=overlay.scene.vessel
        if(state.follow && vessel?.fresh==true && (lastFollowPoint!=vessel.point || distance(state.center,vessel.point)>1)) {cam.move(vessel.point,state.zoom);lastFollowPoint=vessel.point}
    }
    fun captureSnapshot() {
        if(!captureForTile || destroyed || loading || error!=null || width<=0 || height<=0)return
        captureJob?.cancel()
        captureJob=scope.launch {
            delay(600)
            val generation=sourceGeneration
            val source=maps.source
            val center=state.center;val zoom=state.zoom
            fun save(bitmap:Bitmap?) {
                if(bitmap==null || destroyed || generation!=sourceGeneration || state.center!=center || state.zoom!=zoom)return
                val combined=bitmap.copy(Bitmap.Config.ARGB_8888,true) ?: return
                overlay.draw(Canvas(combined))
                val w=480.coerceAtMost(combined.width)
                maps.snapshot=Bitmap.createScaledBitmap(combined,w,(combined.height.toDouble()*w/combined.width).roundToInt().coerceAtLeast(1),true)
                maps.snapshotSource=source
                maps.snapshotDemo=overlay.scene.demo
                maps.snapshotCapturedAt=System.currentTimeMillis()
                if(maps.snapshot !== combined)combined.recycle()
            }
            if(googleEngine)googleMap?.snapshot {save(it)} else libre?.snapshot {save(it)}
        }
    }
    fun update(scene:MapScene,events:(MapEvent)->Unit) {
        onEvent=events;overlay.onEvent=events;overlay.scene=scene;overlay.distanceLabel=maps.distanceLabel;overlay.invalidate()
        googleMap?.uiSettings?.setAllGesturesEnabled(state.interactive)
        googleMap?.uiSettings?.apply {isRotateGesturesEnabled=false;isTiltGesturesEnabled=false}
        libre?.uiSettings?.apply {isScrollGesturesEnabled=state.interactive;isZoomGesturesEnabled=state.interactive;isRotateGesturesEnabled=false;isTiltGesturesEnabled=false}
        updateStyle();updateCamera()
        if(gateway?.lastError!=null) error="read"
    }
    fun lifecycle(event:Lifecycle.Event) {when(event) {
        Lifecycle.Event.ON_START ->if(!started){native?.onStart();google?.onStart();started=true}
        Lifecycle.Event.ON_RESUME ->if(!resumed){lifecycle(Lifecycle.Event.ON_START);native?.onResume();google?.onResume();resumed=true}
        Lifecycle.Event.ON_PAUSE ->if(resumed){native?.onPause();google?.onPause();resumed=false}
        Lifecycle.Event.ON_STOP ->if(started){lifecycle(Lifecycle.Event.ON_PAUSE);native?.onStop();google?.onStop();started=false}
        else ->Unit
    }}
    fun destroy() {if(destroyed)return;destroyed=true;lifecycle(Lifecycle.Event.ON_STOP);scope.cancel();native?.onDestroy();google?.onDestroy();retire(gateway);gateway=null;camera=null}
}

@Composable
fun MarineMap(maps:MapSessionStore,scene:MapScene,state:MapViewState,modifier:Modifier=Modifier,onEvent:(MapEvent)->Unit={},onHost:(ChartHost)->Unit={}) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val google=maps.source !is MapSource.CustomLayer && BuildConfig.GOOGLE_MAPS_CONFIGURED
    // Keep the native camera across online/satellite and custom-layer changes.
    key(google,state) {
        val host=remember {ChartHost(context,maps,state,google)}
        DisposableEffect(host,lifecycle) {
            onHost(host)
            val observer=LifecycleEventObserver {_,event ->host.lifecycle(event)};lifecycle.addObserver(observer)
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))host.lifecycle(Lifecycle.Event.ON_START)
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))host.lifecycle(Lifecycle.Event.ON_RESUME)
            onDispose {lifecycle.removeObserver(observer);host.destroy()}
        }
        // Explicit observation preserves native redraw without recreating its viewport.
        state.center;state.zoom;state.ruler;state.showCrosshair;state.follow;state.request;maps.source;maps.library.revision
        var coverage by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(maps.source,maps.library.revision,state.center,state.zoom) {
            coverage=null
            val layer=maps.selectedLayer() ?: return@LaunchedEffect
            val center=state.center;val zoom=state.zoom
            delay(600)
            coverage=withContext(Dispatchers.IO) {
                layer.files.any { chart ->
                    ensureActive()
                    runCatching {
                        val z=floor(zoom).toInt().coerceAtMost(chart.maxZoom)
                        if(z<chart.minZoom)false else {
                            val n=2.0.pow(z);val x=floor((center.lon+180)/360*n).toInt().coerceIn(0,(1 shl z)-1)
                            val lat=Math.toRadians(center.lat.coerceIn(-85.0511,85.0511));val y=floor((1-asinh(tan(lat))/PI)/2*n).toInt()
                            ChartReader(context,android.net.Uri.parse(chart.uri)).use {it.coverageZoom(x,y,z,chart)!=null}
                        }
                    }.getOrDefault(false)
                }
            }
        }
        Box(modifier) {
            AndroidView(factory={host},modifier=Modifier.fillMaxSize(),update={it.update(scene,onEvent)})
            val zh=maps.chinese
            val message=when {
                host.error=="online" ->if(zh)"在线地图暂不可用 · 检查网络或选择自定义图层" else "online map unavailable · check network or choose a custom layer"
                host.error=="empty" ->if(zh)"图层没有可用文件 · 在海图库检查" else "no available files · check chart library"
                host.error!=null ->if(zh)"图层读取失败 · 在海图库检查" else "chart read failed · check chart library"
                host.loading ->if(zh)"正在载入 ${maps.sourceName(true)}…" else "loading ${maps.sourceName(false)}…"
                coverage==false ->if(zh)"当前位置无本地图块" else "no local chart tile here"
                else ->null
            }
            message?.let {Label(it,13,Color(0xFF19252B),Modifier.align(Alignment.TopCenter).padding(top=54.dp,start=12.dp,end=12.dp).background(Color.White.copy(alpha=.95f)).padding(9.dp))}
            val credits=if(maps.source==MapSource.Online && !BuildConfig.GOOGLE_MAPS_CONFIGURED)listOf("© OpenStreetMap contributors · Natural Earth")
                else maps.selectedLayer()?.files.orEmpty().map {android.text.Html.fromHtml(it.attribution,0).toString()}.filter {it.isNotBlank()}.distinct()+if(!google)listOf("Natural Earth")else emptyList()
            if(credits.isNotEmpty())Column(Modifier.align(Alignment.BottomEnd).widthIn(max=230.dp).background(Color.White.copy(alpha=.92f)).padding(4.dp)) {
                Label(credits.joinToString(" · "),10,Color(0xFF19252B),maxLines=2)
            }
        }
    }
}

/** Packaged geometry sits below every raster. It never participates in user chart priority. */
private fun offlineWorldSources()=JSONObject().put("natural-earth-land",JSONObject()
    .put("type","geojson").put("data","asset://maps/ne_50m_land.geojson").put("maxzoom",8).put("tolerance",.375))

private fun offlineWorldLayers()=JSONArray()
    .put(JSONObject().put("id","water").put("type","background").put("paint",JSONObject().put("background-color","#dee9e8")))
    .put(JSONObject().put("id","world-land").put("type","fill").put("source","natural-earth-land")
        .put("paint",JSONObject().put("fill-color","#e9e4d7").put("fill-antialias",true)))
    .put(JSONObject().put("id","world-coast").put("type","line").put("source","natural-earth-land")
        .put("paint",JSONObject().put("line-color","#8c978e").put("line-width",.65)))

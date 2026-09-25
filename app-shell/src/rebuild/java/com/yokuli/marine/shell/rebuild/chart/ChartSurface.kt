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
import androidx.compose.foundation.clickable
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
import com.yokuli.marine.shell.rebuild.ui.Label
import com.yokuli.marine.shell.rebuild.ui.MetroProgress
import com.yokuli.marine.shell.rebuild.ui.LocalMetro
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
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
    fun orientation(): Double
    fun orient(bearingDegrees: Double, animated: Boolean)
    fun fit(points: List<GeoPoint>)
}

/** Only draggable handles intercept touches. All other gestures reach the native map. */
class ChartOverlay(context: Context, private val state: MapViewState) : View(context) {
    var camera: ChartCamera? = null
    var scene = MapScene()
    var onEvent: (MapEvent) -> Unit = {}
    var unitFormats = com.yokuli.marine.core.design.MarineUnitFormats(com.yokuli.shell.contract.MarineUnitPreferences())
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
        // Geographic geometry is rendered by the map engine, never this screen overlay.
        if(state.showCrosshair) {
            val x=width/2f;val y=height/2f
            for((color,w) in listOf(android.graphics.Color.WHITE to 4f,0xFF14222B.toInt() to 1.5f)) {
                paint.color=color;paint.strokeWidth=w*density;paint.style=Paint.Style.STROKE;canvas.drawCircle(x,y,10*density,paint)
                canvas.drawLine(x-22*density,y,x-5*density,y,paint);canvas.drawLine(x+5*density,y,x+22*density,y,paint)
                canvas.drawLine(x,y-22*density,x,y-5*density,paint);canvas.drawLine(x,y+5*density,x,y+22*density,paint)
            }
        }
        val x=18*density; val y=state.scaleTopDp*density
        val availablePixels=(width-x-12*density).coerceAtLeast(0f)
        val maximumPixels=min(110*density,availablePixels)
        if(maximumPixels<=0f)return
        val maximum=distance(cam.unproject(x,y),cam.unproject(x+maximumPixels,y))
        unitFormats.scaleBar(maximum)?.let { scale ->
            // 先求无量纲比例，防止极端投影值在乘像素时溢出；窄视口同步缩短实际采样线段。
            val pixels=(maximumPixels*(scale.meters/maximum).coerceIn(0.0,1.0)).toFloat()
            paint.textSize=12*density
            val fullLabelWidth=paint.measureText(scale.label)
            if(fullLabelWidth>availablePixels)paint.textSize*=availablePixels/fullLabelWidth
            val labelWidth=paint.measureText(scale.label)
            paint.style=Paint.Style.FILL;paint.color=0xDCFFFFFF.toInt();canvas.drawRect(x-6*density,y-25*density,x+max(pixels,labelWidth)+8*density,y+6*density,paint)
            paint.color=0xFF19252B.toInt();paint.strokeWidth=2*density
            canvas.drawLine(x,y,x+pixels,y,paint);canvas.drawLine(x,y-5*density,x,y,paint);canvas.drawLine(x+pixels,y-5*density,x+pixels,y,paint)
            paint.textAlign=Paint.Align.LEFT;canvas.drawText(scale.label,x,y-8*density,paint)
        }
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

internal fun destination(p: GeoPoint, meters: Double, degrees: Double): GeoPoint {
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
    private var lastOrientationRequest: Double?=null
    private var cameraGestureActive=false
    /** 全量事实在 overlay.scene，viewportTraffic 是预算后的候选，renderedTraffic 只含实际交给原生引擎的身份。 */
    private var viewportTraffic:List<MapAisTarget> = emptyList()
    private var renderedTraffic:List<MapAisTarget> = emptyList()
    private var renderedTrafficIds:Set<String> = emptySet()
    private var projectedTrafficSource:List<MapAisTarget>? = null
    private var trafficProjectionDirty = true
    private var projectedVisibleCount = 0
    private var trafficRenderJob:Job?=null
    private var lastTrafficRenderAt=0L
    var captureForTile = false
    private var captureJob: Job? = null
    val overlay=ChartOverlay(context,state)
    private val nativeScene=NativeSceneRenderer(context,scope) {failed->if(!destroyed){if(failed)error="depthLabels"else if(error=="depthLabels")error=null}}
    private val placeLabels=OfflineMapLabels(context,scope,
        onError={if(!destroyed){error="labels"}},
        onUpdated={if(!destroyed){if(error=="labels")error=null;captureSnapshot()}})
    var camera: ChartCamera?=null
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var onEvent: (MapEvent)->Unit={}
    init {
        setBackgroundColor(0xFFDEE9E8.toInt())
        if(googleEngine) initGoogle() else initLibre()
        addView(overlay,LayoutParams(-1,-1))
    }
    private var tapDown:PointF?=null
    private var nativeTapPoint:GeoPoint?=null
    private var nativeTapAt=0L
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN->{tapDown=PointF(event.x,event.y);nativeTapPoint=null}
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL->{tapDown=null;nativeTapPoint=null}
            MotionEvent.ACTION_UP->{
                val down=tapDown
                nativeTapPoint=if(down!=null&&event.eventTime-event.downTime<=600&&
                    hypot(event.x-down.x,event.y-down.y)<=8*resources.displayMetrics.density)
                    camera?.unproject(event.x,event.y)?.takeIf{it.valid()} else null
                nativeTapAt=android.os.SystemClock.uptimeMillis();tapDown=null
            }
        }
        if(event.actionMasked==MotionEvent.ACTION_DOWN && state.interactive)parent?.requestDisallowInterceptTouchEvent(true)
        val handled=super.dispatchTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL)parent?.requestDisallowInterceptTouchEvent(false)
        return handled
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0&&h>0)post {
            if(!destroyed){trafficProjectionDirty=true;placeLabels.onCameraIdle();updateCamera();renderViewportScene(force=true)}
        }
    }
    private fun moved(center: GeoPoint,z: Double) {if(destroyed)return;state.center=center;state.zoom=z;trafficProjectionDirty=true;placeLabels.onCameraChanged(center,z);renderViewportScene();overlay.invalidate();onEvent(MapEvent.CameraChanged(center,z))}
    private fun touch() {if(!state.interactive)return;cameraGestureActive=true;state.follow=false;state.showCrosshair=true;onEvent(MapEvent.GestureStarted)}
    private fun aisPickingEnabled() = state.interactive && overlay.scene.aisInteractive && state.ruler.isEmpty() && overlay.scene.points.none { it.draggable }
    private fun selectMarker(id:String,hitPoint:GeoPoint?=null) {
        if (destroyed || !state.interactive) return
        if(id.startsWith("enc:")&&!state.objectPickingEnabled)return
        if (id.startsWith("ais:")) {
            val mmsi = id.removePrefix("ais:")
            // 原生点击与地图空白点击走同一工具规则，不接受样式加载/裁剪后留下的旧 marker 回调。
            if (!aisPickingEnabled() || mmsi !in renderedTrafficIds || overlay.scene.aisTargets.none { it.mmsi == mmsi && it.point.valid() }) return
        } else if (state.ruler.isNotEmpty() || overlay.scene.points.none { it.id == id && !it.draggable && it.point.valid() }) return
        val touchPoint=hitPoint ?: nativeTapPoint?.takeIf{android.os.SystemClock.uptimeMillis()-nativeTapAt in 0..1000}
        onEvent(MapEvent.ItemSelected(id,touchPoint))
    }
    private fun pick(point: GeoPoint) {
        if(destroyed || !state.interactive || !point.valid()) return
        val projection = camera ?: return
        val click = projection.project(point)
        if (!click.x.isFinite() || !click.y.isFinite()) return
        val radius = 28 * resources.displayMetrics.density
        fun screenDistance(candidate:GeoPoint):Float {
            val pixel = projection.project(candidate)
            return if (pixel.x.isFinite() && pixel.y.isFinite()) hypot(pixel.x-click.x,pixel.y-click.y) else Float.POSITIVE_INFINITY
        }
        if(state.ruler.isEmpty()) {
            val nearby=overlay.scene.points.asSequence().filter {!it.draggable&&it.point.valid()&&(state.objectPickingEnabled||!it.id.startsWith("enc:"))}
                .map {it to screenDistance(it.point)}.filter {it.second<radius}.minByOrNull {it.second}?.first
            if(nearby!=null) {selectMarker(nearby.id,point);return}
        }
        if(aisPickingEnabled()) {
            val currentIds = overlay.scene.aisTargets.mapTo(hashSetOf()) { it.mmsi }
            val target=renderedTraffic.asSequence().filter {it.mmsi in currentIds}
                .map {it to screenDistance(it.point)}.filter {it.second<radius}.minByOrNull {it.second}?.first
            if(target!=null) {selectMarker("ais:${target.mmsi}",point);return}
        }
        // 点按只进入选点模式。准星固定在视口中心，镜头只由拖动、缩放或明确定位按钮移动。
        onEvent(MapEvent.CoordinateSelected(point));overlay.invalidate()
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
                    override fun move(point:GeoPoint,zoom:Double) {map.moveCamera(GoogleCamera.newCameraPosition(com.google.android.gms.maps.model.CameraPosition.builder(map.cameraPosition).target(GoogleLatLng(point.lat,point.lon)).zoom(zoom.toFloat()).build()))}
                    override fun zoom()=map.cameraPosition.zoom.toDouble()
                    override fun orientation()=map.cameraPosition.bearing.toDouble()
                    override fun orient(bearingDegrees:Double,animated:Boolean){
                        val update=GoogleCamera.newCameraPosition(com.google.android.gms.maps.model.CameraPosition.builder(map.cameraPosition).bearing(bearingDegrees.toFloat()).build())
                        if(animated)map.animateCamera(update,180,null)else map.moveCamera(update)
                    }
                    override fun fit(points:List<GeoPoint>) {if(points.size==1) move(points.first(),state.zoom) else map.moveCamera(GoogleCamera.newLatLngBounds(com.google.android.gms.maps.model.LatLngBounds.builder().also {b ->points.forEach {b.include(GoogleLatLng(it.lat,it.lon))}}.build(),(48*resources.displayMetrics.density).toInt()))}
                }
                camera=adapter;overlay.camera=adapter;trafficProjectionDirty=true;adapter.move(state.center,state.zoom)
                map.setOnCameraMoveListener {val p=map.cameraPosition;moved(GeoPoint(p.target.latitude,p.target.longitude),p.zoom.toDouble())}
                map.setOnCameraMoveStartedListener {if(it==GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE)touch()}
                map.setOnCameraIdleListener { orientationAtRest();renderViewportScene(force=true);captureSnapshot() }
                map.setOnMarkerClickListener {marker ->
                    val id=marker.tag as? String
                    if(id?.startsWith("ais:")==true && !aisPickingEnabled()) pick(GeoPoint(marker.position.latitude,marker.position.longitude))
                    else if(id!=null) selectMarker(id)
                    true
                }
                map.setOnMapClickListener {pick(GeoPoint(it.latitude,it.longitude))};map.setOnMapLongClickListener {pick(GeoPoint(it.latitude,it.longitude))}
                updateStyle();updateCamera();renderViewportScene(force=true)
            }
        }
    }
    private fun initLibre() {
        MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).addInterceptor {chain ->chain.proceed(chain.request().newBuilder().header("User-Agent","YokuliOS/0.4 (+https://github.com/ohkuku/yokuli_marine_shell)").build())}.build())
        // Shell 转场与任务卡使用整窗 PixelCopy；TextureView 参与同一窗口合成，
        // 避免独立 GLSurfaceView 在任务截图中变黑，也让缩放转场带着地图一起运动。
        native=MapView(context,MapLibreMapOptions.createFromAttributes(context).textureMode(true)).also {v ->
            v.addOnDidFailLoadingMapListener {if(!destroyed&&loading){loading=false;error="base"}}
            addView(v,LayoutParams(-1,-1));v.onCreate(Bundle());v.getMapAsync {map ->
                if(destroyed)return@getMapAsync
                libre=map
                map.uiSettings.apply {isLogoEnabled=false;isAttributionEnabled=false;isCompassEnabled=false;isRotateGesturesEnabled=false;isTiltGesturesEnabled=false}
                map.setPrefetchZoomDelta(0);map.setPrefetchesTiles(false);map.setMaxZoomPreference(22.0);map.setMinZoomPreference(1.0)
                val adapter=object:ChartCamera {
                    override fun project(point:GeoPoint)=map.projection.toScreenLocation(LatLng(point.lat,point.lon))
                    override fun unproject(x:Float,y:Float)=map.projection.fromScreenLocation(PointF(x,y)).let {GeoPoint(it.latitude,it.longitude)}
                    override fun move(point:GeoPoint,zoom:Double) {map.moveCamera(CameraUpdateFactory.newCameraPosition(org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition).target(LatLng(point.lat,point.lon)).zoom(zoom).build()))}
                    override fun zoom()=map.cameraPosition.zoom
                    override fun orientation()=map.cameraPosition.bearing
                    override fun orient(bearingDegrees:Double,animated:Boolean){
                        val update=CameraUpdateFactory.newCameraPosition(org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition).bearing(bearingDegrees).build())
                        if(animated)map.easeCamera(update,180)else map.moveCamera(update)
                    }
                    override fun fit(points:List<GeoPoint>) {if(points.size==1) move(points.first(),state.zoom) else map.moveCamera(CameraUpdateFactory.newLatLngBounds(org.maplibre.android.geometry.LatLngBounds.Builder().also {b ->points.forEach {b.include(LatLng(it.lat,it.lon))}}.build(),(48*resources.displayMetrics.density).toInt()))}
                }
                camera=adapter;overlay.camera=adapter;trafficProjectionDirty=true;adapter.move(state.center,state.zoom)
                map.addOnCameraMoveListener {map.cameraPosition.target?.let {moved(GeoPoint(it.latitude,it.longitude),map.cameraPosition.zoom)}}
                map.addOnCameraMoveStartedListener {if(it==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)touch()}
                map.addOnCameraIdleListener {placeLabels.onCameraIdle();orientationAtRest();renderViewportScene(force=true);captureSnapshot()}
                map.setOnMarkerClickListener {marker ->
                    val id=marker.title
                    if(id?.startsWith("ais:")==true && !aisPickingEnabled()) pick(GeoPoint(marker.position.latitude,marker.position.longitude))
                    else if(id!=null) selectMarker(id)
                    true
                }
                map.addOnMapClickListener {pick(GeoPoint(it.latitude,it.longitude));true};map.addOnMapLongClickListener {pick(GeoPoint(it.latitude,it.longitude));true}
                updateStyle();updateCamera();renderViewportScene(force=true)
            }
        }
    }
    private fun retire(old:TileGateway?) {if(old!=null) maps.scope.launch(Dispatchers.IO) {runCatching {old.close()}}}
    private fun applyLibreStyle(sources: JSONObject, layers: JSONArray, generation: Long, ready: Boolean = false) {
        val map = libre ?: return
        // Style 接管资源前清理自有图层、source 和图钉；加载中的参考底图也重画地理内容。
        placeLabels.clear()
        nativeScene.clear()
        renderedTraffic=emptyList();renderedTrafficIds=emptySet();state.aisVisibleCount=0
        trafficProjectionDirty=true
        map.setStyle(Style.Builder().fromJson(JSONObject().put("version",8).put("sources",sources).put("layers",layers).toString())) { loaded ->
            if (!destroyed && generation == sourceGeneration && map.style === loaded) {
                if (ready) loading = false
                if(error=="base"||error=="labels")error=null
                placeLabels.attach(map,loaded,maps.chinese,
                    beforeLayerId=(maps.source as? MapSource.CustomLayer)?.layerId)
                nativeScene.invalidate()
                renderViewportScene(force=true)
                overlay.invalidate()
                if (ready) captureSnapshot()
            }
        }
    }
    fun updateStyle() {
        val source=maps.source
        val layer=maps.selectedLayer()
        val revision=(if(source is MapSource.CustomLayer) "${source}:${maps.library.revision}" else source.toString())+":${maps.chinese}"
        if(styleRevision==revision) return
        if(googleEngine && googleMap==null || !googleEngine && libre==null) return
        styleRevision=revision;styleJob?.cancel();val generation=++sourceGeneration;error=null;loading=true
        if(googleEngine) {
            googleMap?.apply {
                // 卫星影像同时显示 SDK 的地名、道路等信息，不再只有一张无标注照片。
                mapType=GoogleMap.MAP_TYPE_HYBRID
                setOnMapLoadedCallback {if(!destroyed && generation==sourceGeneration){loading=false;error=null;renderViewportScene(force=true);captureSnapshot()}}
            }
            styleJob=scope.launch {delay(15000);if(generation==sourceGeneration && loading) {loading=false;error="online"}}
            return
        }
        styleJob=scope.launch {
            var proposed:TileGateway?=null
            try {
                // 一次提交完整离线样式，避免加载同一份全球几何两次造成切换闪动。
                val sources=OfflineWorldStyle.sources();val layers=OfflineWorldStyle.layers()
                when(source) {
                    MapSource.Offline -> Unit
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
                applyLibreStyle(sources, layers, generation, ready = true)
            } catch(e:Exception) {
                retire(proposed)
                if(e !is CancellationException && generation==sourceGeneration) {
                    val old=gateway;gateway=null;retire(old)
                    // 新图层打不开时不继续显示上一张海图，回到明确的本地背景并保留错误提示。
                    if(source is MapSource.CustomLayer)runCatching {
                        applyLibreStyle(OfflineWorldStyle.sources(),OfflineWorldStyle.layers(),generation,ready=true)
                    }
                    error=if(source==MapSource.Offline)"base" else "read";loading=false
                }
            }
        }
    }
    private fun orientationAtRest(){
        val requested=lastOrientationRequest
        val actual=camera?.orientation()
        if(requested!=null&&actual!=null&&abs(((requested-actual+540)%360)-180)>1.0)lastOrientationRequest=null
        cameraGestureActive=false
        updateCamera()
    }
    private fun updateCamera() {
        val cam=camera ?: return
        state.request?.takeIf {it.id!=lastRequest}?.let {request ->
            if(width<=0 || height<=0) return@let
            if(request.point!=null) cam.move(request.point,request.zoom) else if(request.points.isNotEmpty()) cam.fit(request.points)
            lastOrientationRequest=null
            lastRequest=request.id
            if(state.request?.id==request.id)state.request=null
        }
        val vessel=overlay.scene.vessel
        if(state.follow && vessel?.fresh==true && (lastFollowPoint!=vessel.point || distance(state.center,vessel.point)>1)) {cam.move(vessel.point,state.zoom);lastFollowPoint=vessel.point}
        val direction=when(state.orientationMode){
            MapOrientationMode.NORTH_UP->0.0
            MapOrientationMode.HEADING_UP->vessel?.headingDegrees?.takeIf{it.isFinite()}
            MapOrientationMode.COURSE_UP->vessel?.takeIf{(it.speedKnots?:0.0)>=.5}?.courseDegrees?.takeIf{it.isFinite()}
        }
        state.effectiveOrientationMode=if(direction==null)MapOrientationMode.NORTH_UP else state.orientationMode
        state.orientationIssue=if(direction!=null)null else when(state.orientationMode){MapOrientationMode.HEADING_UP->"heading";MapOrientationMode.COURSE_UP->"course";else->null}
        if(cameraGestureActive)return
        val desired=((direction?:0.0)%360+360)%360
        fun difference(a:Double,b:Double)=abs(((a-b+540)%360)-180)
        if(lastOrientationRequest?.let{difference(desired,it)<.25}!=true){
            // 先记录请求防止相机回调重入；北向降级直接到事实，不播放伪航向。
            lastOrientationRequest=desired
            if(difference(desired,cam.orientation())>.2)cam.orient(desired,animated=direction!=null&&state.orientationMode!=MapOrientationMode.NORTH_UP)
        }
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
    /** 先按实际投影与缓冲区筛选，再预算普通目标；场景事实和风险规则不被裁剪。 */
    private fun renderViewportScene(force:Boolean=false) {
        if(destroyed)return
        val source=overlay.scene
        if (source.aisTargets != projectedTrafficSource) trafficProjectionDirty=true
        val now=android.os.SystemClock.elapsedRealtime()
        if (trafficProjectionDirty) {
            val remaining = 100L - (now-lastTrafficRenderAt)
            if (!force && remaining>0L && source.aisTargets.isNotEmpty()) {
                if(trafficRenderJob?.isActive!=true)trafficRenderJob=scope.launch {
                    delay(remaining);trafficRenderJob=null;renderViewportScene(force=true)
                }
            } else {
                trafficRenderJob?.cancel();trafficRenderJob=null;lastTrafficRenderAt=now
                val projection=camera
                val validTargets=source.aisTargets.filter {it.point.valid()}
                val important=validTargets.filter {it.selected||it.watched||it.risk||it.distress=="ACTIVE"}
                val importantIds=important.mapTo(hashSetOf()){it.mmsi}
                val inViewIds=hashSetOf<String>()
                val nearby=if(projection!=null&&width>0&&height>0) {
                    val buffer=max(96f*resources.displayMetrics.density,min(width,height)*.25f)
                    // 距中心平方距离只算一次；日期变更线和地理投影由原生引擎决定。
                    data class Candidate(val target:MapAisTarget,val distanceSquared:Float)
                    validTargets.asSequence().mapNotNull { target ->
                        val point=projection.project(target.point)
                        if(!point.x.isFinite()||!point.y.isFinite())return@mapNotNull null
                        if(point.x in 0f..width.toFloat()&&point.y in 0f..height.toFloat())inViewIds+=target.mmsi
                        if(target.mmsi in importantIds||point.x !in -buffer..(width+buffer)||point.y !in -buffer..(height+buffer))return@mapNotNull null
                        val dx=point.x-width/2f;val dy=point.y-height/2f
                        Candidate(target,dx*dx+dy*dy)
                    }.sortedWith(compareBy<Candidate> {it.distanceSquared}.thenBy {it.target.mmsi})
                        .take(512).map {it.target}.toList()
                } else emptyList()
                viewportTraffic=(important+nearby).distinctBy {it.mmsi}.sortedBy {it.mmsi}
                projectedVisibleCount=viewportTraffic.count {it.mmsi in inViewIds}
                projectedTrafficSource=source.aisTargets
                trafficProjectionDirty=false
            }
        }
        // 非 AIS 的船位、测距和航线立即交给原生绘制；相机回写到 Compose 不再强制重复全量投影。
        if(nativeScene.render(googleMap,libre,source.copy(aisTargets=viewportTraffic),state.ruler)) {
            if(renderedTraffic !== viewportTraffic) {
                renderedTraffic=viewportTraffic
                renderedTrafficIds=viewportTraffic.mapTo(hashSetOf()){it.mmsi}
            }
            state.aisVisibleCount=projectedVisibleCount
        }
    }
    fun update(scene:MapScene,events:(MapEvent)->Unit) {
        if(destroyed)return
        onEvent=events;overlay.onEvent=events;overlay.scene=scene;overlay.unitFormats=com.yokuli.marine.core.design.MarineUnitFormats(maps.unitPreferences);overlay.invalidate()
        renderViewportScene()
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
    fun retry() {styleRevision="";updateStyle()}
    fun destroy() {if(destroyed)return;destroyed=true;lifecycle(Lifecycle.Event.ON_STOP);placeLabels.close();scope.cancel();nativeScene.clear();native?.onDestroy();google?.onDestroy();retire(gateway);gateway=null;camera=null;overlay.camera=null;renderedTraffic=emptyList();renderedTrafficIds=emptySet();state.aisVisibleCount=0}
}

@Composable
fun MarineMap(maps:MapSessionStore,scene:MapScene,state:MapViewState,modifier:Modifier=Modifier,onEvent:(MapEvent)->Unit={},onHost:(ChartHost)->Unit={}) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val structured=rememberStructuredChart(maps,state)
    val combined=scene.copy(points=structured.scene.points+scene.points+state.planningPoints,lines=structured.scene.lines+scene.lines+state.planningLines,areas=structured.scene.areas+scene.areas+state.planningAreas)
    val handleEvent:(MapEvent)->Unit={ event ->
        if(state.interactive) {
            val markerPoint=(event as? MapEvent.ItemSelected)?.id?.takeIf {it.startsWith("enc:")}?.let {id->structured.scene.points.firstOrNull {it.id==id}?.point}
            val queryPoint=markerPoint ?: (event as? MapEvent.CoordinateSelected)?.point
            val canQuery=state.objectPickingEnabled&&state.ruler.isEmpty()&&scene.points.none {it.draggable}
            val objects=when {
                event is MapEvent.ItemSelected&&event.id.startsWith("enc:")&&canQuery&&queryPoint!=null->chartObjectsAt(structured.features,queryPoint,state.zoom)
                event is MapEvent.CoordinateSelected&&canQuery->chartObjectsAt(structured.features,event.point,state.zoom)
                else->emptyList()
            }
            if(objects.isNotEmpty()) {
                state.selectedChartObjects=objects
                state.selectedChartCoordinate=when(event){is MapEvent.ItemSelected->event.hitPoint;is MapEvent.CoordinateSelected->event.point;else->null}
                state.showCrosshair=false
                // 同步宿主的选点展示状态；不派发空白地图点按，不触发相机/准星动作。
                onEvent(MapEvent.ItemSelected("enc:${objects.first().id}"))
            }else {
                if(event is MapEvent.CoordinateSelected)state.showCrosshair=true
                if(event !is MapEvent.CameraChanged){state.selectedChartObjects=emptyList();state.selectedChartCoordinate=null}
                onEvent(event)
            }
        }
    }
    val google=maps.source==MapSource.Satellite && BuildConfig.GOOGLE_MAPS_CONFIGURED
    // 默认地图与用户海图共用离线引擎；API Key 不会将默认来源切回联网地图。
    if(state.interactive&&state.selectedChartObjects.isNotEmpty()) com.yokuli.marine.shell.rebuild.ui.ChartObjectSheet((context.applicationContext as com.yokuli.marine.shell.rebuild.YokuliApplication).os,state)
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
        state.center;state.zoom;state.ruler;state.showCrosshair;state.scaleTopDp;state.follow;state.request;maps.source;maps.library.revision
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
        BoxWithConstraints(modifier) {
            val compactViewport=maxHeight<300.dp
            AndroidView(factory={host},modifier=Modifier.fillMaxSize(),update={it.update(combined,handleEvent)})
            val zh=maps.chinese
            val message=when {
                structured.issue!=null -> structured.issue
                host.error=="online" ->if(zh)"卫星影像暂不可用 · 可切换内置地图" else "satellite imagery unavailable · use the built-in map"
                host.error=="base" ->if(zh)"内置地图未能载入 · 点按重试" else "built-in map could not load · tap to retry"
                host.error=="depthLabels" ->if(zh)"测深标签未能显示 · 点按重试" else "depth labels could not load · tap to retry"
                host.error=="labels" ->if(zh)"地名未能载入 · 点按重试" else "place names could not load · tap to retry"
                host.error=="empty" ->if(zh)"图层没有可用文件 · 在图册检查" else "no available files · check chart library"
                host.error!=null ->if(zh)"图层读取失败 · 在图册检查" else "chart read failed · check chart library"
                host.loading ->if(zh)"正在载入 ${maps.sourceName(true)}…" else "loading ${maps.sourceName(false)}…"
                coverage==false ->if(zh)"当前位置无本地图块" else "no local chart tile here"
                else ->null
            }
            message?.let {
                // 小型地图把状态放到上沿，并为右上角图源入口留位置；主地图仍避开船位和缩放控件。
                val statusModifier=Modifier.align(if(compactViewport)Alignment.TopStart else Alignment.TopCenter)
                    .padding(top=if(compactViewport)8.dp else 166.dp,start=12.dp,end=if(compactViewport)96.dp else 12.dp).widthIn(max=290.dp)
                if(host.loading && host.error==null)Box(statusModifier.background(LocalMetro.current.bg.copy(alpha=.94f)).padding(10.dp)){MetroProgress(it)}
                else Label(it,13,Color(0xFF19252B),statusModifier.background(Color.White.copy(alpha=.95f))
                    .then(if(host.error in setOf("base","labels"))Modifier.clickable {host.retry()}else Modifier).padding(9.dp))
            }
            val credits=maps.selectedLayer()?.files.orEmpty().map {android.text.Html.fromHtml(it.attribution,0).toString()}.filter {it.isNotBlank()}.distinct()+if(!google)listOf("Natural Earth")else emptyList()
            if(credits.isNotEmpty())Column(Modifier.align(Alignment.BottomEnd).padding(bottom=state.bottomOverlayDp.dp).widthIn(max=230.dp).background(Color.White.copy(alpha=.92f)).padding(4.dp)) {
                Label(credits.joinToString(" · "),10,Color(0xFF19252B),maxLines=2)
            }
        }
    }
}

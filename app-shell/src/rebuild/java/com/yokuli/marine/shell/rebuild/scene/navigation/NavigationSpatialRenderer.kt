package com.yokuli.marine.shell.rebuild.scene.navigation

import android.content.Context
import android.graphics.RectF
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.ViewConfiguration
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.yokuli.anchorwatch.location.vessel.DeviceViewOrientationSample
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.scene.ais.*
import com.yokuli.marine.shell.rebuild.scene.MaritimeSceneResource
import com.yokuli.marine.shell.rebuild.scene.MaritimeScenePalette
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

internal data class SpatialRenderInput(
    val snapshot:NavigationSpatialSnapshot=NavigationSpatialSnapshot(null),
    val orientation:DeviceViewOrientationSample=DeviceViewOrientationSample(),
    val conversion:SpatialNorthConversion?=null,
    val screenRotation:Int=0,
    val freeYaw:Double?=null,
    val freePitch:Double=-8.0,
    val chinese:Boolean=false,
    val distanceLabels:Map<String,String> = emptyMap(),
    val fontScale:Float=1f,
    val chartScene:NavigationChartScene?=null,
    val mode:NavigationChartMode=NavigationChartMode.FOLLOW,
    val route:List<GeoPoint> = emptyList(),
    val night:Boolean=false,
    val vesselLengthMeters:Double=12.0,
    val viewOrigin:GeoPoint?=null,
    /** null 跟随真实船位；非 null 是用户明确选择的地图/预览中心。 */
    val focusPoint:GeoPoint?=null,
    val traffic:AisSceneData?=null,
)
internal data class SpatialHit(val id:String,val bounds:RectF)
internal data class SpatialPresentedFrame(val camera:SpatialCamera,val targetDelta:Double?,val hits:List<SpatialHit>)

/**
 * 唯一 Filament 场景持有者。数据/网格在帧外构建；帧时钟只更新相机、变换和材质。
 * 真实地形、船体姿态、导航路线与装饰性海面分层，不能用海面颜色证明水深/可航性。
 */
internal class NavigationSpatialSurface(context:Context):TextureView(context),UiHelper.RendererCallback,Choreographer.FrameCallback,MaritimeSceneResource {
    private val clock=Choreographer.getInstance()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var engine:Engine?=null
    private var renderer:Renderer?=null
    private var scene:Scene?=null
    private var view:View?=null
    private var camera:Camera?=null
    private var cameraEntity=0
    private var sunEntity=0
    private var indirect:IndirectLight?=null
    private var sky:Skybox?=null
    private var environment:Texture?=null
    private var helper:UiHelper?=null
    private var swap:SwapChain?=null
    private var provider:UbershaderProvider?=null
    private var loader:AssetLoader?=null
    private var resourcesLoader:ResourceLoader?=null
    private var terrainLayer:MaritimeTerrainLayer?=null
    private var trafficLayer:MaritimeTrafficLayer?=null
    private var trafficLocal:AisLocalFrame?=null
    private val trafficIndex=AisSceneTargetIndex()
    private var trafficVisibleTargets=emptyList<AisSceneTarget>()
    private var trafficAnimatedIds=emptySet<String>()
    private val trafficMotion=AisObservedMotion()
    private var pendingTraffic:AisSceneFrame?=null
    private var presentedTraffic:AisSceneFrame?=null
    private var markerVersion=-1
    private var markerOrigin:GeoPoint?=null
    private var lastAreaAt=0L
    private var lastAreaPoint:GeoPoint?=null
    private var lastAreaRadius=0.0
    private val loaded=mutableMapOf<String,FilamentAsset>()
    private val loadedKeys=mutableMapOf<String,String>()
    private val desiredKeys=mutableMapOf<String,String>()
    private data class Upload(val slot:String,val key:String,val buffer:ByteBuffer)
    private data class Loading(val upload:Upload,val asset:FilamentAsset)
    private val uploads=ArrayDeque<Upload>()
    private var loading:Loading?=null
    private val builds=mutableMapOf<String,Job>()
    private var input=SpatialRenderInput()
    private var active=false
    private var closed=false
    private var failed=false
    private var scheduled=false
    private var frameTime=0L
    private var latest:SpatialPresentedFrame?=null
    private var pendingFrame:SpatialPresentedFrame?=null
    private var lastOrigin:GeoPoint?=null
    private val projectionValues=DoubleArray(16)
    private val cameraValues=DoubleArray(16)
    private val viewProjection=DoubleArray(16)
    private val transforms=mutableMapOf<String,FloatArray>()
    private var lastSceneKey:String?=null
    private var lastRouteKey:String?=null
    private var appliedNight:Boolean?=null
    private var shownEye:SpatialVector?=null
    private var shownLook:SpatialVector?=null
    private var shownHeading:Double?=null
    private var shownHeadingSource:String?=null
    private val boatMotion=NavigationPositionMotion()
    private var freeCenter:SpatialVector?=null
    private var shownCourse:Double?=null
    private var shownCourseSource:String?=null
    private val presentedProjection=NavigationScreenProjection()
    private var presentedMarkers:List<NavigationChartMarker> = emptyList()
    private val pendingGuides=Array(3){NavigationGuideHit()}
    private val presentedGuides=Array(3){NavigationGuideHit()}
    private var presentedSeabed=false
    private var presentedSceneKey:String?=null
    private var projectionWidth=0
    private var projectionHeight=0
    private var projectionFar=0.0
    private var shownHeel=0.0
    private var shownPitch=0.0
    private var zoom=1.0
    private var lastMode=NavigationChartMode.FOLLOW
    private val deviceMotion=DeviceViewMotion()
    private val cameraMotion=SpatialCameraMotion()
    private var downX=0f;private var downY=0f;private var startYaw=0.0;private var startPitch=-35.0
    private var dragging=false;private var pinching=false
    private var gestureMoved=false;private var panning=false
    private var lastFocusX=0f;private var lastFocusY=0f
    private var initialFocusX=0f;private var initialFocusY=0f
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private val density=resources.displayMetrics.density
    var inputEnabled=true
    var onFailure:()->Unit={}
    var onPresented:(SpatialPresentedFrame)->Unit={}
    private var firstFrameReported=false
    private var firstFrameWatchArmed=false
    private val firstFrameTimeout=Runnable {
        firstFrameWatchArmed=false
        if(canDraw()&&latest==null)fail(IllegalStateException("NAVIGATION_FIRST_FRAME_UNAVAILABLE"))
    }
    private fun watchFirstFrame(){
        if(canDraw()&&latest==null&&!firstFrameWatchArmed){
            firstFrameWatchArmed=true;postDelayed(firstFrameTimeout,15_000)
        }
    }
    var onTarget:(String)->Unit={}
    var onFreeChanged:(Boolean)->Unit={}
    var onViewAreaChanged:(GeoPoint,Double)->Unit={_,_->}
    var onTerrainFailure:(String?)->Unit={}
    private val scaleDetector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(detector:ScaleGestureDetector):Boolean {pinching=true;gestureMoved=true;return true}
        override fun onScale(detector:ScaleGestureDetector):Boolean {zoom=(zoom/detector.scaleFactor).coerceIn(.15,12.0);syncRouteAsset();requestFrame();return true}
    })

    init {isOpaque=true;isClickable=true;contentDescription="Navigation scene"}

    fun update(value:SpatialRenderInput,visible:Boolean) {
        if(closed)return
        val focusChanged=input.focusPoint!=value.focusPoint
        val sample=input.orientation.takeIf {it.generation==value.orientation.generation&&(it.elapsedRealtimeMillis?:0L)>(value.orientation.elapsedRealtimeMillis?:0L)}?:value.orientation
        input=value.copy(orientation=sample,freeYaw=if(value.freeYaw!=null)input.freeYaw?:value.freeYaw else null,freePitch=input.freePitch)
        active=visible
        if(lastMode!=value.mode){lastMode=value.mode;zoom=1.0;freeCenter=null;input=input.copy(freeYaw=null);onFreeChanged(false)}
        if(input.freeYaw==null||focusChanged)freeCenter=null
        if(active&&isAttachedToWindow)initialize()
        if(engine!=null)guarded {syncInputAssets();applyLighting()}
        if(focusChanged&&input.freeYaw!=null)freeCenter=value.focusPoint?.let {local(it)}
        if(canDraw())requestFrame()else cancelFrames()
    }
    fun orientation(value:DeviceViewOrientationSample){if(!closed){input=input.copy(orientation=value);requestFrame()}}
    fun resetView(){input=input.copy(freeYaw=null,freePitch=-35.0);freeCenter=null;zoom=1.0;onFreeChanged(false);syncRouteAsset();requestFrame()}
    fun turnBy(degrees:Double){enterFree();input=input.copy(freeYaw=wrapBearing((input.freeYaw?:0.0)+degrees));requestFrame()}
    fun frame():SpatialPresentedFrame?=latest
    fun retryTerrain(){if(!closed){terrainLayer?.retry();requestFrame()}}
    fun zoomBy(factor:Double){if(factor.isFinite()&&factor>0){zoom=(zoom/factor).coerceIn(.15,12.0);syncRouteAsset();requestFrame()}}

    private fun initialize() {
        if(engine!=null||closed||failed)return
        guarded {
            Gltfio.init()
            val e=Engine.create(Engine.Backend.OPENGL).also {engine=it}
            renderer=e.createRenderer().apply {clearOptions=Renderer.ClearOptions().apply {clear=true;clearColor=doubleArrayOf(.64,.67,.69,1.0)}}
            scene=e.createScene()
            view=e.createView().apply {
                scene=this@NavigationSpatialSurface.scene
                blendMode=View.BlendMode.OPAQUE
                antiAliasing=View.AntiAliasing.FXAA
                dynamicResolutionOptions=View.DynamicResolutionOptions().apply {enabled=true;homogeneousScaling=true;minScale=.8f;maxScale=1f;quality=View.QualityLevel.HIGH}
                ambientOcclusionOptions=View.AmbientOcclusionOptions().apply {enabled=true;quality=View.QualityLevel.MEDIUM;radius=1.5f;intensity=.65f;resolution=.5f}
                bloomOptions=View.BloomOptions().apply {enabled=false}
            }
            cameraEntity=EntityManager.get().create();camera=e.createCamera(cameraEntity).apply {setExposure(16f,1f/125f,100f)};view?.camera=camera
            provider=UbershaderProvider(e);loader=AssetLoader(e,requireNotNull(provider),EntityManager.get());resourcesLoader=ResourceLoader(e)
            terrainLayer=MaritimeTerrainLayer(e,requireNotNull(scene),requireNotNull(loader),::requestFrame){error->
                if(error!=null)Log.w("YokuliNavigation3D","Terrain unavailable; navigation remains active",error)
                post {if(!closed)onTerrainFailure(error?.message)}
            }
            sunEntity=EntityManager.get().create()
            LightManager.Builder(LightManager.Type.SUN).color(1f,1f,1f).intensity(88_000f).direction(-.55f,-.82f,-.35f)
                .castShadows(true).shadowOptions(LightManager.ShadowOptions().apply {mapSize=1024;shadowCascades=2;shadowFar=900f;normalBias=1.2f}).build(e,sunEntity)
            scene?.addEntity(sunEntity)
            createEnvironment()
            helper=UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also {it.isOpaque=true;it.renderCallback=this}
            if(width>0&&height>0)helper?.setDesiredSize(width,height)
            helper?.attachTo(this)
            build("vessel","vessel"){context.assets.open("vessel/yokuli-sloop.glb").use {navigationVesselAsset(it.readBytes())}}
            build("neutral","neutral"){context.assets.open("ais/traffic-neutral.glb").use {it.readBytes()}}
            build("water","water"){waterMesh(false)}
            build("ripples","ripples"){waterMesh(true)}
            build("target","target"){targetMesh()?.let(::navigationGuideAsset)}
            build("next","next"){targetMesh(preview=true)?.let(::navigationGuideAsset)}
            build("steering","steering"){steeringMesh()?.let(::navigationGuideAsset)}
            build("course","course"){courseMesh()?.let(::navigationGuideAsset)}
            syncInputAssets();applyLighting();requestFrame()
        }
    }

    /** 本地解析天空立方体：只影响照明与反射，不表示当地天气。 */
    private fun createEnvironment() {
        val e=requireNotNull(engine);val size=32;val bytes=ByteBuffer.allocateDirect(6*size*size*3*4).order(ByteOrder.nativeOrder());val floats=bytes.asFloatBuffer()
        for(face in 0..5)for(y in 0 until size)for(x in 0 until size) {
            val u=(x+.5)/size*2-1;val v=(y+.5)/size*2-1
            val direction=when(face){0->doubleArrayOf(1.0,-v,-u);1->doubleArrayOf(-1.0,-v,u);2->doubleArrayOf(u,1.0,v);3->doubleArrayOf(u,-1.0,-v);4->doubleArrayOf(u,-v,1.0);else->doubleArrayOf(-u,-v,-1.0)}
            val length=sqrt(direction.sumOf {it*it});val h=(direction[1]/length).coerceIn(-1.0,1.0)
            val skyAmount=h.coerceAtLeast(0.0).pow(.45)
            val bottom=if(h<0) .20 else 1.0
            floats.put(((.67*(1-skyAmount)+.39*skyAmount)*bottom).toFloat())
            floats.put(((.69*(1-skyAmount)+.43*skyAmount)*bottom).toFloat())
            floats.put(((.71*(1-skyAmount)+.46*skyAmount)*bottom).toFloat())
        }
        floats.flip()
        val texture=Texture.Builder().width(size).height(size).levels(6).sampler(Texture.Sampler.SAMPLER_CUBEMAP).format(Texture.InternalFormat.R11F_G11F_B10F).build(e)
        environment=texture
        texture.generatePrefilterMipmap(e,Texture.PixelBufferDescriptor(floats,Texture.Format.RGB,Texture.Type.FLOAT),IntArray(6){it*size*size*3*4},Texture.PrefilterOptions().apply {sampleCount=16;mirror=false})
        indirect=IndirectLight.Builder().reflections(texture).irradiance(1,floatArrayOf(.86f,.88f,.90f)).intensity(26_000f).build(e)
        scene?.indirectLight=indirect
        sky=Skybox.Builder().environment(texture).intensity(26_000f).build(e);scene?.skybox=sky
    }

    private fun applyLighting() {
        if(appliedNight==input.night)return
        val e=engine?:return;appliedNight=input.night
        e.lightManager.setIntensity(e.lightManager.getInstance(sunEntity),if(input.night)7_000f else 88_000f)
        indirect?.intensity=if(input.night)3_000f else 26_000f
        camera?.setExposure(16f,1f/125f,100f)
        val nextSky=if(input.night)Skybox.Builder().color(.018f,.023f,.030f,1f).build(e)
            else Skybox.Builder().environment(requireNotNull(environment)).intensity(26_000f).build(e)
        scene?.skybox=nextSky;sky?.let {e.destroySkybox(it)};sky=nextSky
        val color=MaritimeScenePalette.horizon(!input.night)
        loaded["water"]?.let(::tintWater);loaded["ripples"]?.let(::tintWater)
        view?.fogOptions=View.FogOptions().apply {enabled=true;distance=300f;density=if(input.night).00045f else .00022f;maximumOpacity=.9f;this.color=color;fogColorFromIbl=false;cutOffDistance=24_000f}
    }

    private fun syncInputAssets() {
        val terrain=input.chartScene
        val origin=origin()
        if(lastOrigin!=origin){
            // 船、镜头和路线重基准；共用地形层独立变换旧块，等待新块期间不会移动岸线。
            lastOrigin?.let {old->
                val east=signedBearing(old.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat))
                val south=-(old.lat-origin.lat)*111_320
                boatMotion.rebase(east,south)
                fun moved(p:SpatialVector?)=p?.let {SpatialVector(it.x+east,it.y,it.z+south)}
                if(hypot(east,south)>(terrain?.radiusMeters?:2_000.0)*4){shownEye=null;shownLook=null;freeCenter=null}
                else {shownEye=moved(shownEye);shownLook=moved(shownLook);freeCenter=moved(freeCenter)}
            }
            lastOrigin=origin
            removeAsset("route")
        }
        if(lastSceneKey!=terrain?.sceneKey) {
            lastSceneKey=terrain?.sceneKey
            terrainLayer?.update(terrain)
        }
        syncRouteAsset()
        if(input.traffic!=null){
            build("traffic-vessel","traffic-vessel"){context.assets.open("ais/traffic-vessel.glb").use{it.readBytes()}}
            build("traffic-neutral","traffic-neutral"){context.assets.open("ais/traffic-neutral.glb").use{it.readBytes()}}
        }
    }
    private fun cameraRange():Double {
        // 镜头尺度由用户决定，加载更大资料窗口不能反过来缩放镜头或触发无限扩窗。
        return when(input.mode){NavigationChartMode.FOLLOW->240.0;NavigationChartMode.OVERVIEW->1_800.0;NavigationChartMode.SEABED->1_000.0}*zoom
    }
    private fun syncRouteAsset()=guarded {syncRouteAssetSafely()}
    private fun syncRouteAssetSafely() {
        if(engine==null||closed||failed)return
        val origin=origin();val terrain=input.chartScene
        // 少量离散宽度档保持约 4px 引导线；缩放帧不持续重建几何或影响真实路线坐标。
        val widthStep=round(ln((cameraRange()/height.coerceAtLeast(320)).coerceAtLeast(.01))/ln(1.4)).toInt()
        val halfWidth=(1.4.pow(widthStep)*tan(Math.toRadians(24.0))*2.5).coerceIn(.22,25.0)
        val key="${origin.lat}:${origin.lon}:${terrain?.radiusMeters}:${input.route.hashCode()}:$widthStep"
        if(lastRouteKey!=key) {
            lastRouteKey=key
            val route=input.route.toList();val radius=terrain?.radiusMeters?:2_000.0
            if(route.size<2){
                builds.remove("route")?.cancel();desiredKeys["route"]=key;uploads.removeAll {it.slot=="route"}
                if(loading?.upload?.slot=="route")cancelUpload()
                removeAsset("route")
            }else build("route",key){val job=currentCoroutineContext();routeMesh(route,origin,radius,halfWidth){job.ensureActive()}}
        }
    }
    private fun origin():GeoPoint = input.chartScene?.origin?:input.viewOrigin?:input.snapshot.position?.let {navigationTerrainOrigin(GeoPoint(it.latitude,it.longitude))}?:GeoPoint(0.0,0.0)
    private fun local(point:GeoPoint,origin:GeoPoint=origin()):SpatialVector=SpatialVector(
        signedBearing(point.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat)),0.0,-(point.lat-origin.lat)*111_320)

    private fun build(slot:String,key:String,work:suspend ()->ByteArray?) {
        if(desiredKeys[slot]==key)return
        desiredKeys[slot]=key;builds.remove(slot)?.cancel()
        uploads.removeAll {it.slot==slot}
        if(loading?.upload?.slot==slot&&loading?.upload?.key!=key)cancelUpload()
        builds[slot]=scope.launch {
            try {
                // 连续捏合只合并新的线宽档，不为每个中间档上传/销毁一份网格。
                if(slot=="route")delay(90)
                val result=withContext(Dispatchers.Default){work()?.let {bytes->ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {put(bytes);flip()}}}
                if(!closed&&!failed&&desiredKeys[slot]==key)enqueue(slot,key,result)
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){fail(error)}
        }
    }
    private fun replaceBytes(slot:String,key:String,bytes:ByteArray?)=build(slot,key){bytes}
    private fun enqueue(slot:String,key:String,buffer:ByteBuffer?) {
        uploads.removeAll {it.slot==slot}
        if(buffer==null){removeAsset(slot);return}
        uploads.addLast(Upload(slot,key,buffer));requestFrame()
    }
    private fun pumpUploads() {
        val resource=requireNotNull(resourcesLoader)
        loading?.let {current->
            resource.asyncUpdateLoad()
            if(resource.asyncGetLoadProgress()>=1f) {
                loading=null
                var adopted=false
                try {
                    if(!current.upload.slot.startsWith("traffic-"))current.asset.releaseSourceData()
                    if(desiredKeys[current.upload.slot]==current.upload.key) {
                        removeAsset(current.upload.slot);loaded[current.upload.slot]=current.asset;loadedKeys[current.upload.slot]=current.upload.key;adopted=true
                        configureAsset(current.upload.slot,current.asset)
                        if(!current.upload.slot.startsWith("traffic-"))scene?.addEntities(current.asset.entities)
                    }
                }finally {if(!adopted)loader?.destroyAsset(current.asset)}
            }
        }
        if(!dragging&&!pinching&&!panning&&loading==null&&uploads.isNotEmpty()) {
            val pending=uploads.removeFirst()
            if(desiredKeys[pending.slot]!=pending.key)return
            val asset=requireNotNull(loader?.createAsset(pending.buffer)){"NAVIGATION_MODEL_INVALID"}
            loading=Loading(pending,asset)
            require(asset.resourceUris.isEmpty()){"NAVIGATION_EXTERNAL_RESOURCE"}
            check(resource.asyncBeginLoad(asset)){"NAVIGATION_MODEL_UPLOAD_FAILED"}
        }
    }
    /** 一个 ResourceLoader 同时只上传一份；过时结果立即取消并释放，不阻挡新窗口。 */
    private fun cancelUpload() {
        val current=loading?:return
        loading=null
        try {resourcesLoader?.asyncCancelLoad();resourcesLoader?.evictResourceData()}
        finally {loader?.destroyAsset(current.asset)}
    }
    private fun tintWater(asset:FilamentAsset) {
        val manager=engine?.renderableManager?:return
        val color=MaritimeScenePalette.sea(!input.night)
        for(entity in asset.entities){
            val instance=manager.getInstance(entity);if(instance==0)continue
            for(index in 0 until manager.getPrimitiveCount(instance)){
                val material=manager.getMaterialInstanceAt(instance,index)
                if(material.material.hasParameter("baseColorFactor"))material.setParameter("baseColorFactor",color[0],color[1],color[2],1f)
            }
        }
    }
    private fun configureAsset(slot:String,asset:FilamentAsset) {
        if(slot=="water"||slot=="ripples")tintWater(asset)
        val e=requireNotNull(engine);val manager=e.renderableManager
        for(entity in asset.entities) {
            val instance=manager.getInstance(entity);if(instance==0)continue
            manager.setCastShadows(instance,slot in setOf("vessel","surface"))
            manager.setReceiveShadows(instance,slot !in setOf("route","target","next","steering"))
            manager.setScreenSpaceContactShadows(instance,slot=="vessel")
            if(slot=="route"||slot=="target"||slot=="next"||slot=="steering") {
                // 路线是明确的导航覆盖符号；不能通过抬高/改弯路线制造可航地形。
                manager.setPriority(instance,7)
                for(primitive in 0 until manager.getPrimitiveCount(instance)){
                    manager.getMaterialInstanceAt(instance,primitive).apply {setDepthWrite(false);setDepthCulling(false)}
                    if(slot=="route"){manager.setBlendOrderAt(instance,primitive,primitive);manager.setGlobalBlendOrderEnabledAt(instance,primitive,true)}
                }
            }
        }
    }
    private fun removeAsset(slot:String){loadedKeys.remove(slot);visibility.remove(slot);transforms.remove(slot);loaded.remove(slot)?.let {scene?.removeEntities(it.entities);loader?.destroyAsset(it)}}

    private fun canDraw()=active&&!closed&&!failed&&isAttachedToWindow&&windowVisibility==VISIBLE&&isShown
    private fun requestFrame(){
        watchFirstFrame()
        if(canDraw()&&swap!=null&&!scheduled){scheduled=true;clock.postFrameCallback(this)}
    }
    private fun cancelFrames(){
        clock.removeFrameCallback(this);scheduled=false;frameTime=0L
        removeCallbacks(firstFrameTimeout);firstFrameWatchArmed=false
    }
    override fun doFrame(nanos:Long) {
        scheduled=false
        if(!canDraw())return
        // TextureView 的 surface 回调可早于 UiHelper 的 ready 标志；不能在这一次丢帧后
        // 永久停止 Choreographer，等待一条并不存在的新数据更新来唤醒首帧。
        if(helper?.isReadyToRender!=true||width<=0||height<=0){requestFrame();return}
        guarded {
            val dt=if(frameTime==0L)1.0/60 else ((nanos-frameTime)/1e9).coerceIn(0.0,.05);frameTime=nanos
            pumpUploads()
            val sceneOrigin=origin()
            terrainLayer?.advance(sceneOrigin,!dragging&&!pinching&&!panning){patchOrigin->
                val offset=local(patchOrigin,sceneOrigin)
                val eastScale=cos(Math.toRadians(sceneOrigin.lat))/cos(Math.toRadians(patchOrigin.lat)).coerceAtLeast(.003)
                floatArrayOf(eastScale.toFloat(),0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,offset.x.toFloat(),0f,offset.z.toFloat(),1f)
            }
            drawTransforms(nanos,dt)
            val r=requireNotNull(renderer)
            if(r.beginFrame(requireNotNull(swap),nanos)){try{r.render(requireNotNull(view));latest=pendingFrame;presentedTraffic=pendingTraffic
                viewProjection.copyInto(presentedProjection.matrix);presentedProjection.width=width;presentedProjection.height=height
                for(index in pendingGuides.indices)presentedGuides[index].copyFrom(pendingGuides[index])
                val terrainVersion=terrainLayer?.presentationVersion?:0
                if(markerVersion!=terrainVersion||markerOrigin!=sceneOrigin){
                    markerVersion=terrainVersion;markerOrigin=sceneOrigin
                    presentedMarkers=terrainLayer?.displayedPatches.orEmpty().flatMap {patch->
                        val offset=local(patch.origin,sceneOrigin)
                        val scale=cos(Math.toRadians(sceneOrigin.lat))/cos(Math.toRadians(patch.origin.lat)).coerceAtLeast(.003)
                        patch.markers.map {it.copy(eastMeters=offset.x+it.eastMeters*scale,southMeters=offset.z+it.southMeters)}
                    }
                }
                presentedSceneKey=input.chartScene?.sceneKey
                presentedSeabed=input.mode==NavigationChartMode.SEABED}finally{r.endFrame()}
                if(!firstFrameReported)latest?.let {frame->
                    firstFrameReported=true
                    removeCallbacks(firstFrameTimeout);firstFrameWatchArmed=false
                    post {if(!closed)onPresented(frame)}
                }
            }
            requestFrame()
        }
    }
    private fun drawTransforms(nanos:Long,dt:Double) {
        val snapshot=input.snapshot;val terrain=input.chartScene;val radius=(terrain?.radiusMeters?:2_000.0).coerceIn(250.0,64_000.0)
        val alpha=if(dragging||panning||pinching)1.0 else 1-exp(-dt/.16)
        val elapsed=SystemClock.elapsedRealtime()
        val automaticDirection=input.mode==NavigationChartMode.FOLLOW&&input.focusPoint==null&&input.freeYaw==null
        if(automaticDirection){deviceMotion.update(input.orientation);deviceMotion.advance(nanos)}
        // 概览/海底/明确地图中心默认北向稳定观察，不被残留手机姿态带着旋转。
        val desired=resolveSpatialCamera(snapshot,input.orientation,deviceMotion.shown,input.screenRotation,input.conversion,elapsed,
            input.freeYaw?:if(!automaticDirection)0.0 else null,input.freePitch)
        val directionSource=when{input.freeYaw!=null->"free";!automaticDirection->"overview";else->snapshot.vesselHeading?.source?:input.orientation.sourceName}
        val direction=cameraMotion.present(desired,directionSource,snapshot.vesselHeading?.observedElapsedMillis?:elapsed,nanos)
        val position=snapshot.position
        val vessel=position?.let {local(GeoPoint(it.latitude,it.longitude))}
        if(vessel!=null&&position!=null)boatMotion.update(vessel.x,vessel.z,position.observedUtcMillis,dt,radius*.5)
        else boatMotion.clear()
        val boat=if(vessel==null)SpatialVector(0.0,0.0,0.0)else SpatialVector(boatMotion.x,0.0,boatMotion.z)
        val trueHeading=snapshot.vesselHeading?.takeIf {it.trueDegrees.isFinite()&&(it.observedElapsedMillis?.let {at->elapsed-at}?:it.ageMillis) in 0..10_000}?.trueDegrees
        if(trueHeading!=null) {
            if(shownHeadingSource!=snapshot.vesselHeading?.source)shownHeading=trueHeading
            else shownHeading=shownHeading?.let {wrapBearing(it+signedBearing(trueHeading-it)*alpha)}?:trueHeading
            shownHeadingSource=snapshot.vesselHeading?.source
        }else {shownHeading=null;shownHeadingSource=null}
        // 缺失轴回到中性示意，不能把失联前的倾角永久画成实时姿态；原始值仍为空。
        val heel=snapshot.vesselHeelDegrees?.takeIf(Double::isFinite)?.coerceIn(-80.0,80.0)?:0.0
        val pitch=snapshot.vesselPitchDegrees?.takeIf(Double::isFinite)?.coerceIn(-80.0,80.0)?:0.0
        shownHeel+=(heel-shownHeel)*alpha;shownPitch+=(pitch-shownPitch)*alpha
        val boatInScene=snapshot.position!=null&&hypot(boat.x,boat.z)<=radius*1.5
        visible("vessel",boatInScene&&trueHeading!=null)
        visible("neutral",boatInScene&&trueHeading==null)
        val length=input.vesselLengthMeters.takeIf {it.isFinite()}?.coerceIn(3.0,80.0)?:12.0
        transform("vessel",boat.x,0.0,boat.z,-(shownHeading?:0.0),length/3.9,shownPitch,-shownHeel)
        transform("neutral",boat.x,.4,boat.z,0.0,length*.35)
        val seabed=input.mode==NavigationChartMode.SEABED
        visible("water",!seabed);visible("ripples",!seabed)
        transform("water",0.0,-.34,0.0,0.0,radius*4)
        val time=(nanos/1e9)%10_000
        val waterCenter=freeCenter?:input.focusPoint?.let {local(it)}?:boat
        transform("ripples",waterCenter.x+sin(time*.09)*2.5,-.19,waterCenter.z+cos(time*.07)*2.0,0.0,1.0)
        val course=snapshot.courseOverGround?.takeIf {it.trueDegrees.isFinite()&&(it.observedElapsedMillis?.let {at->elapsed-at}?:it.ageMillis) in 0..10_000L}
        visible("course",course!=null&&boatInScene)
        if(course!=null) {
            shownCourse=if(shownCourseSource!=course.source)course.trueDegrees else shownCourse?.let {wrapBearing(it+signedBearing(course.trueDegrees-it)*alpha)}?:course.trueDegrees
            shownCourseSource=course.source
            transform("course",boat.x,.35,boat.z,-requireNotNull(shownCourse),(length*4/40).coerceIn(.7,4.0))
        }else {shownCourse=null;shownCourseSource=null}
        // 船首向决定船模；COG仅有自己的细箭带，不旋转船模或填补缺失Heading。
        val yaw=input.freeYaw?:direction.trueBearing?:0.0
        val a=Math.toRadians(yaw)
        val range=cameraRange()
        val tilt=if(input.freeYaw!=null)abs(input.freePitch).coerceIn(12.0,82.0)else when(input.mode){NavigationChartMode.FOLLOW->28.0;NavigationChartMode.OVERVIEW->61.0;NavigationChartMode.SEABED->43.0}
        val center=freeCenter?:input.focusPoint?.let {local(it)}?:when(input.mode){NavigationChartMode.FOLLOW->boat+SpatialVector(sin(a)*range*.3,0.0,-cos(a)*range*.3);else->boat}
        val lookY=if(seabed)((terrain?.minElevationMeters?:-50.0)*.26).coerceAtMost(-5.0)else 0.0
        reportViewArea(center,range,elapsed)
        val desiredLook=SpatialVector(center.x,lookY,center.z)
        val elevation=Math.toRadians(tilt)
        val desiredEye=SpatialVector(center.x-sin(a)*range*cos(elevation),lookY+range*sin(elevation),center.z+cos(a)*range*cos(elevation))
        shownEye=shownEye?.let {mix(it,desiredEye,alpha)}?:desiredEye;shownLook=shownLook?.let {mix(it,desiredLook,alpha)}?:desiredLook
        val eye=requireNotNull(shownEye);val look=requireNotNull(shownLook)
        val far=max(12_000.0,radius*12)
        if(projectionWidth!=width||projectionHeight!=height||projectionFar!=far){
            projectionWidth=width;projectionHeight=height;projectionFar=far
            camera?.setProjection(48.0,width.toDouble()/height,.5,far,Camera.Fov.VERTICAL)
            camera?.getProjectionMatrix(projectionValues)
        }
        camera?.lookAt(eye.x,eye.y,eye.z,look.x,look.y,look.z,0.0,1.0,0.0)
        drawTraffic(eye,look,far,dt)
        camera?.getViewMatrix(cameraValues)
        for(column in 0..3)for(row in 0..3){
            val base=column*4
            viewProjection[base+row]=projectionValues[row]*cameraValues[base]+projectionValues[4+row]*cameraValues[base+1]+projectionValues[8+row]*cameraValues[base+2]+projectionValues[12+row]*cameraValues[base+3]
        }
        val current=snapshot.current
        updateGuide(0,"target",current,boat,range,yaw,radius,1.0)
        updateGuide(1,"next",snapshot.next,boat,range,yaw,radius,.72)
        updateGuide(2,"steering",snapshot.steering,boat,range,yaw,radius,.55)
        val guide=snapshot.steering?:current
        val actualBearing=wrapBearing(Math.toDegrees(atan2(look.x-eye.x,eye.z-look.z)))
        pendingFrame=SpatialPresentedFrame(direction.copy(trueBearing=actualBearing),guide?.bearingTrueDegrees?.takeIf{snapshot.live&&direction.issue==null}?.let {signedBearing(it-actualBearing)},emptyList())
    }
    private fun drawTraffic(eye:SpatialVector,look:SpatialVector,far:Double,dt:Double){
        val traffic=input.traffic
        if(traffic==null&&trafficLayer==null)return
        val vesselAsset=loaded["traffic-vessel"]?:return
        val neutralAsset=loaded["traffic-neutral"]?:return
        val e=engine?:return;val s=scene?:return;val loader=loader?:return
        val layer=trafficLayer?:MaritimeTrafficLayer(e,s,loader,
            TrafficModelPool(vesselAsset,listOf(requireNotNull(loader.createInstance(vesselAsset)))),
            TrafficModelPool(neutralAsset,listOf(requireNotNull(loader.createInstance(neutralAsset))))).also{trafficLayer=it}
        val origin=origin()
        val localFrame=trafficLocal?.takeIf{it.origin.latitude==origin.lat&&it.origin.longitude==origin.lon}
            ?:AisLocalFrame(AisScenePosition(origin.lat,origin.lon)).also{trafficLocal=it}
        val aspect=width.toDouble()/height.coerceAtLeast(1)
        val halfHeight=cameraRange()*tan(Math.toRadians(24.0))
        val camera=AisSceneCamera(AisVector3(eye.x,eye.y,eye.z),AisVector3(look.x,look.y,look.z),AisVector3(0.0,1.0,0.0),
            halfHeight*aspect,halfHeight,far,48.0,aspect,.5)
        val facts=traffic?:AisSceneData(null,targets=emptyList())
        trafficIndex.update(facts.targets,localFrame,camera,null)
        if(trafficVisibleTargets!==trafficIndex.targets){
            trafficVisibleTargets=trafficIndex.targets
            trafficAnimatedIds=trafficVisibleTargets.mapTo(mutableSetOf()){it.id}
        }
        val frame=AisSceneFrame(localFrame,camera,trafficVisibleTargets,facts.ownPosition==null,observedPositions=trafficIndex.positions)
        val shown=trafficMotion.advance(frame,facts,dt,trafficAnimatedIds)
        layer.draw(shown,null,width,density)
        pendingTraffic=shown
    }

    private fun reportViewArea(center:SpatialVector,range:Double,elapsed:Long){
        if(elapsed-lastAreaAt<200)return
        val origin=origin()
        val point=GeoPoint((origin.lat-center.z/111_320).coerceIn(-85.0,85.0),
            (origin.lon+center.x/(111_320*cos(Math.toRadians(origin.lat)).coerceAtLeast(.003))+540.0)%360.0-180.0)
        val radius=(range*1.8).coerceIn(500.0,32_000.0)
        val previous=lastAreaPoint
        val movement=previous?.let {local(point,it).let {p->hypot(p.x,p.z)}}?:Double.POSITIVE_INFINITY
        if(movement<max(30.0,radius*.12)&&abs(radius-lastAreaRadius)<radius*.15)return
        lastAreaAt=elapsed;lastAreaPoint=point;lastAreaRadius=radius
        post {if(!closed&&active)onViewAreaChanged(point,radius)}
    }

    private fun updateGuide(index:Int,slot:String,target:SpatialNavigationTarget?,boat:SpatialVector,range:Double,yaw:Double,radius:Double,size:Double){
        val point=target?.let {targetPoint(it,boat)}
        val inRange=point!=null&&hypot(point.x,point.z)<=radius*1.5
        visible(slot,inRange)
        pendingGuides[index].set(null)
        if(point!=null&&target!=null&&inRange){
            val scale=(range*.035).coerceIn(6.0,45.0)*size
            transform(slot,point.x,1.0,point.z,-yaw,scale)
            if(loaded.containsKey(slot))pendingGuides[index].set(target.id,point.x,1.0+scale*.75,point.z)
        }
    }
    private fun targetPoint(target:SpatialNavigationTarget,boat:SpatialVector):SpatialVector? {
        target.point?.takeIf {it.valid()}?.let {return local(it)}
        if(!input.snapshot.live)return null
        val bearing=target.bearingTrueDegrees?.takeIf(Double::isFinite)?:return null
        val distance=target.distanceMeters?.takeIf {it.isFinite()&&it>=0}?:return null
        val p=bearingVector(bearing,distance,0.0);return boat+p
    }
    /** 只在点按时命中实际渲染帧，无每帧 RectF/Pair/投影数组分配。重叠物标选最近中心。 */
    private fun hitTarget(x:Float,y:Float):String? {
        var best:String?=null;var bestDistance=Float.POSITIVE_INFINITY
        fun consider(id:String,east:Double,height:Double,south:Double,radius:Float){
            if(!presentedProjection.project(east,height,south))return
            val dx=x-presentedProjection.x;val dy=y-presentedProjection.y;val distance=dx*dx+dy*dy
            if(distance<=radius*radius&&distance<bestDistance){best=id;bestDistance=distance}
        }
        presentedTraffic?.let {traffic->
            for(id in trafficLayer?.presentedIds.orEmpty())traffic.targetPositions[id]?.let {point->consider("ais:$id",point.x,point.y,point.z,28*density)}
        }
        for(guide in presentedGuides)guide.id?.let {consider(it,guide.east,guide.height,guide.south,30*density)}
        for(marker in presentedMarkers) {
            if(marker.kind==NavigationChartMarkerKind.SOUNDING&&!presentedSeabed)continue
            consider(marker.id,marker.eastMeters,marker.elevationMeters,marker.southMeters,24*density)
        }
        return best
    }
    private val visibility=mutableMapOf<String,Boolean>()
    private fun visible(slot:String,value:Boolean) {
        val asset=loaded[slot]?:return
        if(visibility[slot]==value)return
        visibility[slot]=value
        if(value)scene?.addEntities(asset.entities)else scene?.removeEntities(asset.entities)
    }
    private fun transform(slot:String,x:Double,y:Double,z:Double,yaw:Double,scale:Double,pitch:Double=0.0,heel:Double=0.0) {
        val asset=loaded[slot]?:return;val e=engine?:return;val matrix=transforms.getOrPut(slot){FloatArray(16)}
        Matrix.setIdentityM(matrix,0);Matrix.translateM(matrix,0,x.toFloat(),y.toFloat(),z.toFloat());Matrix.rotateM(matrix,0,yaw.toFloat(),0f,1f,0f)
        Matrix.rotateM(matrix,0,pitch.toFloat(),1f,0f,0f);Matrix.rotateM(matrix,0,heel.toFloat(),0f,0f,1f);Matrix.scaleM(matrix,0,scale.toFloat(),scale.toFloat(),scale.toFloat())
        e.transformManager.setTransform(e.transformManager.getInstance(asset.root),matrix)
    }

    override fun onNativeWindowChanged(surface:Surface){if(!closed)guarded {destroySwap();swap=requireNotNull(engine).createSwapChain(surface,helper?.swapChainFlags?:SwapChainFlags.CONFIG_DEFAULT);onResized(width,height);requestFrame()}}
    override fun onDetachedFromSurface(){cancelFrames();guarded {destroySwap()}}
    override fun onResized(width:Int,height:Int){if(width>0&&height>0&&!closed){view?.viewport=Viewport(0,0,width,height);syncRouteAsset();requestFrame()}}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0){helper?.setDesiredSize(w,h);onResized(w,h)}}
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(active){initialize();requestFrame()}}
    override fun onDetachedFromWindow(){cancelFrames();super.onDetachedFromWindow()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(engine!=null){if(visibility==VISIBLE)requestFrame()else cancelFrames()}}
    override fun onVisibilityAggregated(isVisible:Boolean){
        super.onVisibilityAggregated(isVisible)
        if(engine!=null){if(isVisible)requestFrame()else cancelFrames()}
    }
    private fun destroySwap(){swap?.let {swap=null;engine?.destroySwapChain(it);engine?.flushAndWait()}}
    private inline fun guarded(block:()->Unit){try{block()}catch(error:Exception){fail(error)}catch(error:LinkageError){fail(error)}}
    private fun fail(error:Throwable){if(closed||failed)return;failed=true;Log.w("YokuliNavigation3D","Navigation scene unavailable",error);cancelFrames();post {if(!closed)onFailure()}}
    override val hostView: android.view.View get() = this
    override val reusable: Boolean get() = !closed && !failed
    override fun park() {
        active=false;inputEnabled=false;dragging=false;panning=false;pinching=false;cancelFrames()
        // GPU 资产可以温存；页面上一帧的命中/交通/观测不可成为下一次访问的事实。
        latest=null;pendingFrame=null;presentedMarkers=emptyList();presentedSceneKey=null
        pendingTraffic=null;presentedTraffic=null;trafficMotion.reset();boatMotion.clear();firstFrameReported=false
        trafficIndex.clear();trafficVisibleTargets=emptyList();trafficAnimatedIds=emptySet();trafficLayer?.forgetObservations()
        markerVersion=-1;markerOrigin=null
        presentedGuides.forEach{it.set(null)};pendingGuides.forEach{it.set(null)}
        input=SpatialRenderInput(mode=input.mode,freeYaw=input.freeYaw,freePitch=input.freePitch)
        onTarget={};onFailure={};onPresented={};onFreeChanged={};onViewAreaChanged={_,_->};onTerrainFailure={}
    }
    override fun close() {
        if(closed)return;closed=true;active=false;cancelFrames();scope.cancel();builds.clear();uploads.clear()
        runCatching {helper?.detach()};helper?.renderCallback=null;helper=null;runCatching {destroySwap()}
        runCatching {terrainLayer?.close()};terrainLayer=null
        runCatching {cancelUpload()};runCatching {resourcesLoader?.evictResourceData()};runCatching {resourcesLoader?.destroy()};resourcesLoader=null
        runCatching{trafficLayer?.close()};trafficLayer=null;pendingTraffic=null;presentedTraffic=null
        loaded.values.forEach {asset->runCatching {scene?.removeEntities(asset.entities);loader?.destroyAsset(asset)}};loaded.clear()
        runCatching {loader?.destroy()};loader=null;runCatching {provider?.destroyMaterials()};runCatching {provider?.destroy()};provider=null
        val e=engine
        runCatching {view?.let {e?.destroyView(it)}};view=null
        runCatching {scene?.let {e?.destroyScene(it)}};scene=null
        runCatching {sky?.let {e?.destroySkybox(it)}};sky=null
        runCatching {indirect?.let {e?.destroyIndirectLight(it)}};indirect=null
        runCatching {environment?.let {e?.destroyTexture(it)}};environment=null
        if(sunEntity!=0){runCatching {e?.destroyEntity(sunEntity)};EntityManager.get().destroy(sunEntity);sunEntity=0}
        if(cameraEntity!=0){runCatching {e?.destroyCameraComponent(cameraEntity)};EntityManager.get().destroy(cameraEntity);cameraEntity=0};camera=null
        runCatching {renderer?.let {e?.destroyRenderer(it)}};renderer=null;runCatching {e?.flushAndWait()};runCatching {e?.destroy()};engine=null
        latest=null;pendingFrame=null;presentedMarkers=emptyList();presentedGuides.forEach {it.set(null)};pendingGuides.forEach {it.set(null)};loadedKeys.clear()
        onTarget={};onFailure={};onPresented={};onFreeChanged={};onViewAreaChanged={_,_->};onTerrainFailure={}
    }
    private fun enterFree(){if(input.freeYaw==null){freeCenter=shownLook;input=input.copy(freeYaw=latest?.camera?.trueBearing?:input.snapshot.vesselHeading?.trueDegrees?:0.0,freePitch=when(input.mode){NavigationChartMode.FOLLOW->-28.0;NavigationChartMode.OVERVIEW->-61.0;NavigationChartMode.SEABED->-43.0});onFreeChanged(true)}}
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!inputEnabled||!active||closed)return false
        scaleDetector.onTouchEvent(event)
        fun anchorOne(index:Int=0){
            downX=event.getX(index);downY=event.getY(index);startYaw=input.freeYaw?:latest?.camera?.trueBearing?:0.0
            startPitch=if(input.freeYaw!=null)input.freePitch else when(input.mode){NavigationChartMode.FOLLOW->-28.0;NavigationChartMode.OVERVIEW->-61.0;NavigationChartMode.SEABED->-43.0}
        }
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{anchorOne();dragging=false;pinching=false;panning=false;gestureMoved=false;parent?.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_POINTER_DOWN->{
                if(event.pointerCount>=2){initialFocusX=(event.getX(0)+event.getX(1))*.5f;initialFocusY=(event.getY(0)+event.getY(1))*.5f;lastFocusX=initialFocusX;lastFocusY=initialFocusY}
                gestureMoved=true
            }
            MotionEvent.ACTION_MOVE->{
                if(event.pointerCount>=2){
                    val x=(event.getX(0)+event.getX(1))*.5f;val y=(event.getY(0)+event.getY(1))*.5f
                    if(!panning&&hypot(x-initialFocusX,y-initialFocusY)>slop){panning=true;enterFree()}
                    if(panning){
                        val metersPerPixel=cameraRange()*2*tan(Math.toRadians(24.0))/height.coerceAtLeast(1)
                        val angle=Math.toRadians(input.freeYaw?:0.0);val dx=(x-lastFocusX)*metersPerPixel;val dz=(y-lastFocusY)*metersPerPixel
                        val center=freeCenter?:input.focusPoint?.let {local(it)}?:SpatialVector(boatMotion.x,0.0,boatMotion.z)
                        val east=center.x-dx*cos(angle)-dz*sin(angle)
                        val south=center.z-dx*sin(angle)+dz*cos(angle)
                        freeCenter=SpatialVector(east,center.y,south);requestFrame()
                    }
                    lastFocusX=x;lastFocusY=y
                }else if(!pinching){
                    val dx=event.x-downX;val dy=event.y-downY
                    if(!dragging&&hypot(dx,dy)>slop){dragging=true;gestureMoved=true;enterFree()}
                    if(dragging){input=input.copy(freeYaw=wrapBearing(startYaw-dx/width.coerceAtLeast(1)*100),freePitch=(startPitch+dy/height.coerceAtLeast(1)*70).coerceIn(-82.0,-12.0));requestFrame()}
                }
            }
            MotionEvent.ACTION_POINTER_UP->{
                if(event.pointerCount==2){anchorOne(if(event.actionIndex==0)1 else 0);dragging=false;pinching=false;panning=false}
                gestureMoved=true
            }
            MotionEvent.ACTION_UP->{
                if(!gestureMoved&&!dragging&&!pinching)hitTarget(event.x,event.y)?.let(onTarget)
                performClick();parent?.requestDisallowInterceptTouchEvent(false);pinching=false;panning=false;dragging=false;requestFrame()
            }
            MotionEvent.ACTION_CANCEL->{dragging=false;pinching=false;panning=false;gestureMoved=true;parent?.requestDisallowInterceptTouchEvent(false)}
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    private fun mix(a:SpatialVector,b:SpatialVector,t:Double)=SpatialVector(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t)
}

/** 海面只承担光照/视角参照，不生成海底、地形或可航行证据。 */
private fun waterMesh(ripples:Boolean):ByteArray? {
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=20_000)
    val material=NavigationTerrainMaterial("navigation-water",.34f,.39f,.42f,roughness=if(ripples).62f else .74f,metallic=0f,doubleSided=false)
    if(!ripples) {
        val a=NavigationTerrainVertex(-1f,0f,-1f);val b=NavigationTerrainVertex(-1f,0f,1f);val c=NavigationTerrainVertex(1f,0f,1f);val d=NavigationTerrainVertex(1f,0f,-1f)
        mesh.triangle(material,a,b,c);mesh.triangle(material,a,c,d)
    }else {
        val divisions=40;val size=500f
        fun vertex(x:Int,z:Int):NavigationTerrainVertex {
            val east=(x.toFloat()/divisions-.5f)*size;val south=(z.toFloat()/divisions-.5f)*size
            val phase1=east*.22+south*.08;val phase2=east*.11-south*.18
            val y=(sin(phase1)*.055+sin(phase2)*.028).toFloat()
            val nx=-(cos(phase1)*.055*.22+cos(phase2)*.028*.11).toFloat();val nz=-(cos(phase1)*.055*.08-cos(phase2)*.028*.18).toFloat()
            val length=sqrt(nx*nx+1+nz*nz)
            return NavigationTerrainVertex(east,y,south,nx/length,1/length,nz/length)
        }
        for(z in 0 until divisions)for(x in 0 until divisions){val a=vertex(x,z);val b=vertex(x,z+1);val c=vertex(x+1,z+1);val d=vertex(x+1,z);mesh.triangle(material,a,b,c);mesh.triangle(material,a,c,d)}
    }
    return mesh.glb()
}
private fun targetMesh(preview:Boolean=false):ByteArray? {
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=120)
    val material=NavigationTerrainMaterial("navigation-target",.95f,.98f,1f,alpha=if(preview).42f else 1f,roughness=.3f,metallic=.12f)
    mesh.box(material,-.6f,0f,0f,.085f,1.1f,.085f);mesh.box(material,.6f,0f,0f,.085f,1.1f,.085f);mesh.box(material,0f,1.015f,0f,1.285f,.085f,.085f)
    return mesh.glb()
}
/** 沿线引导用菱形，与业务目标的门形区分；不新增、跳过或推进航点。 */
private fun steeringMesh():ByteArray? {
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=8)
    val material=NavigationTerrainMaterial("route-steering",.12f,.57f,.96f,roughness=1f,doubleSided=true)
    val outer=arrayOf(NavigationTerrainVertex(0f,1.5f,0f),NavigationTerrainVertex(.55f,.75f,0f),NavigationTerrainVertex(0f,0f,0f),NavigationTerrainVertex(-.55f,.75f,0f))
    val inner=arrayOf(NavigationTerrainVertex(0f,1.28f,0f),NavigationTerrainVertex(.35f,.75f,0f),NavigationTerrainVertex(0f,.22f,0f),NavigationTerrainVertex(-.35f,.75f,0f))
    for(i in 0..3){val next=(i+1)%4;mesh.triangle(material,outer[i],outer[next],inner[next]);mesh.triangle(material,outer[i],inner[next],inner[i])}
    return mesh.glb()
}
internal fun routeMesh(route:List<GeoPoint>,origin:GeoPoint,radius:Double,width:Double,checkActive:()->Unit):ByteArray? {
    if(route.size<2)return null
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=16_000)
    val material=NavigationTerrainMaterial("navigation-route",.12f,.57f,.96f,alpha=.93f,roughness=1f,doubleSided=true)
    val outline=NavigationTerrainMaterial("navigation-route-outline",.015f,.045f,.085f,alpha=.72f,roughness=1f,doubleSided=true)
    fun point(p:GeoPoint)=SpatialVector(signedBearing(p.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat)),.5,-(p.lat-origin.lat)*111_320)
    for(i in 1 until route.size) {
        if(i%64==0)checkActive()
        val a=point(route[i-1]);val b=point(route[i]);val dx=b.x-a.x;val dz=b.z-a.z;val length=hypot(dx,dz);if(length<.1)continue
        // 局部世界之外的航段不上传；跨过本窗口的长边先参数裁剪，保留实际折线。
        var lo=0.0;var hi=1.0
        for((p,q) in listOf(-dx to (a.x+radius),dx to (radius-a.x),-dz to (a.z+radius),dz to (radius-a.z))) {
            if(abs(p)<1e-10){if(q<0){lo=1.0;hi=0.0;break}}
            else {val r=q/p;if(p<0)lo=max(lo,r)else hi=min(hi,r)}
        }
        if(lo>hi)continue
        check(!mesh.full){"NAVIGATION_ROUTE_TOO_COMPLEX"}
        val x1=a.x+dx*lo;val z1=a.z+dz*lo;val x2=a.x+dx*hi;val z2=a.z+dz*hi
        val sx=-dz/length*width;val sz=dx/length*width
        val p=NavigationTerrainVertex((x1+sx).toFloat(),.6f,(z1+sz).toFloat());val q=NavigationTerrainVertex((x1-sx).toFloat(),.6f,(z1-sz).toFloat())
        val r=NavigationTerrainVertex((x2-sx).toFloat(),.6f,(z2-sz).toFloat());val s=NavigationTerrainVertex((x2+sx).toFloat(),.6f,(z2+sz).toFloat())
        val p0=NavigationTerrainVertex((x1+sx*1.6).toFloat(),.58f,(z1+sz*1.6).toFloat());val q0=NavigationTerrainVertex((x1-sx*1.6).toFloat(),.58f,(z1-sz*1.6).toFloat())
        val r0=NavigationTerrainVertex((x2-sx*1.6).toFloat(),.58f,(z2-sz*1.6).toFloat());val s0=NavigationTerrainVertex((x2+sx*1.6).toFloat(),.58f,(z2+sz*1.6).toFloat())
        mesh.triangle(outline,p0,q0,r0);mesh.triangle(outline,p0,r0,s0)
        mesh.triangle(material,p,q,r);mesh.triangle(material,p,r,s)
    }
    checkActive()
    return mesh.glb()?.let(::navigationGuideAsset)
}

private fun courseMesh():ByteArray? {
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=6)
    val material=NavigationTerrainMaterial("course-over-ground",.53f,.72f,.77f,roughness=.5f,doubleSided=true)
    val a=NavigationTerrainVertex(-.22f,0f,0f);val b=NavigationTerrainVertex(.22f,0f,0f)
    val c=NavigationTerrainVertex(.22f,0f,-37f);val d=NavigationTerrainVertex(-.22f,0f,-37f)
    mesh.triangle(material,a,c,b);mesh.triangle(material,a,d,c)
    mesh.triangle(material,NavigationTerrainVertex(-1.8f,0f,-36f),NavigationTerrainVertex(0f,0f,-40f),NavigationTerrainVertex(1.8f,0f,-36f))
    return mesh.glb()
}

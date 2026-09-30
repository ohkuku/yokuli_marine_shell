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
)
internal data class SpatialHit(val id:String,val bounds:RectF)
internal data class SpatialPresentedFrame(val camera:SpatialCamera,val targetDelta:Double?,val hits:List<SpatialHit>)

/**
 * 唯一 Filament 场景持有者。数据/网格在帧外构建；帧时钟只更新相机、变换和材质。
 * 真实地形、船体姿态、导航路线与装饰性海面分层，不能用海面颜色证明水深/可航性。
 */
internal class NavigationSpatialSurface(context:Context):TextureView(context),UiHelper.RendererCallback,Choreographer.FrameCallback {
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
    private val loaded=mutableMapOf<String,FilamentAsset>()
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
    private var shownBoat:SpatialVector?=null
    private var shownHeel=0.0
    private var shownPitch=0.0
    private var zoom=1.0
    private var lastMode=NavigationChartMode.FOLLOW
    private val deviceMotion=DeviceViewMotion()
    private val cameraMotion=SpatialCameraMotion()
    private var downX=0f;private var downY=0f;private var startYaw=0.0;private var startPitch=-35.0
    private var dragging=false;private var pinching=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private val density=resources.displayMetrics.density
    var inputEnabled=true
    var onFailure:()->Unit={}
    var onTarget:(String)->Unit={}
    var onFreeChanged:(Boolean)->Unit={}
    private val scaleDetector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(detector:ScaleGestureDetector):Boolean {pinching=true;enterFree();return true}
        override fun onScale(detector:ScaleGestureDetector):Boolean {zoom=(zoom/detector.scaleFactor).coerceIn(.25,3.0);requestFrame();return true}
    })

    init {isOpaque=true;isClickable=true;contentDescription="Navigation scene"}

    fun update(value:SpatialRenderInput,visible:Boolean) {
        if(closed)return
        val sample=input.orientation.takeIf {it.generation==value.orientation.generation&&(it.elapsedRealtimeMillis?:0L)>(value.orientation.elapsedRealtimeMillis?:0L)}?:value.orientation
        input=value.copy(orientation=sample,freeYaw=if(value.freeYaw!=null)input.freeYaw?:value.freeYaw else null,freePitch=input.freePitch)
        active=visible
        if(lastMode!=value.mode){lastMode=value.mode;zoom=1.0;input=input.copy(freeYaw=null);onFreeChanged(false)}
        if(active&&isAttachedToWindow)initialize()
        if(engine!=null){syncInputAssets();applyLighting()}
        if(canDraw())requestFrame()else cancelFrames()
    }
    fun orientation(value:DeviceViewOrientationSample){if(!closed){input=input.copy(orientation=value);requestFrame()}}
    fun resetView(){input=input.copy(freeYaw=null,freePitch=-35.0);zoom=1.0;onFreeChanged(false);requestFrame()}
    fun turnBy(degrees:Double){enterFree();input=input.copy(freeYaw=wrapBearing((input.freeYaw?:0.0)+degrees));requestFrame()}
    fun frame():SpatialPresentedFrame?=latest
    fun zoomBy(factor:Double){if(factor.isFinite()&&factor>0){enterFree();zoom=(zoom/factor).coerceIn(.25,3.0);requestFrame()}}

    private fun initialize() {
        if(engine!=null||closed||failed)return
        guarded {
            Gltfio.init()
            val e=Engine.create(Engine.Backend.OPENGL).also {engine=it}
            renderer=e.createRenderer().apply {clearOptions=Renderer.ClearOptions().apply {clear=true;clearColor=doubleArrayOf(.14,.23,.29,1.0)}}
            scene=e.createScene()
            view=e.createView().apply {
                scene=this@NavigationSpatialSurface.scene
                blendMode=View.BlendMode.OPAQUE
                antiAliasing=View.AntiAliasing.FXAA
                dynamicResolutionOptions=View.DynamicResolutionOptions().apply {enabled=true;homogeneousScaling=true;minScale=.8f;maxScale=1f;quality=View.QualityLevel.HIGH}
                ambientOcclusionOptions=View.AmbientOcclusionOptions().apply {enabled=true;quality=View.QualityLevel.MEDIUM;radius=1.5f;intensity=.65f;resolution=.5f}
                bloomOptions=View.BloomOptions().apply {enabled=true;strength=.045f;threshold=true;resolution=256;levels=5}
            }
            cameraEntity=EntityManager.get().create();camera=e.createCamera(cameraEntity).apply {setExposure(16f,1f/125f,100f)};view?.camera=camera
            provider=UbershaderProvider(e);loader=AssetLoader(e,requireNotNull(provider),EntityManager.get());resourcesLoader=ResourceLoader(e)
            sunEntity=EntityManager.get().create()
            LightManager.Builder(LightManager.Type.SUN).color(1f,.97f,.91f).intensity(88_000f).direction(-.55f,-.82f,-.35f)
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
            build("target","target"){targetMesh()}
            build("course","course"){courseMesh()}
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
            floats.put(((.62*(1-skyAmount)+.16*skyAmount)*bottom).toFloat())
            floats.put(((.76*(1-skyAmount)+.38*skyAmount)*bottom).toFloat())
            floats.put(((.85*(1-skyAmount)+.62*skyAmount)*bottom).toFloat())
        }
        floats.flip()
        val texture=Texture.Builder().width(size).height(size).levels(6).sampler(Texture.Sampler.SAMPLER_CUBEMAP).format(Texture.InternalFormat.R11F_G11F_B10F).build(e)
        environment=texture
        texture.generatePrefilterMipmap(e,Texture.PixelBufferDescriptor(floats,Texture.Format.RGB,Texture.Type.FLOAT),IntArray(6){it*size*size*3*4},Texture.PrefilterOptions().apply {sampleCount=16;mirror=false})
        indirect=IndirectLight.Builder().reflections(texture).irradiance(1,floatArrayOf(.74f,.83f,.94f)).intensity(26_000f).build(e)
        scene?.indirectLight=indirect
        sky=Skybox.Builder().environment(texture).intensity(26_000f).build(e);scene?.skybox=sky
    }

    private fun applyLighting() {
        if(appliedNight==input.night)return
        val e=engine?:return;appliedNight=input.night
        e.lightManager.setIntensity(e.lightManager.getInstance(sunEntity),if(input.night)7_000f else 88_000f)
        indirect?.intensity=if(input.night)3_000f else 26_000f
        camera?.setExposure(16f,1f/125f,100f)
        val nextSky=if(input.night)Skybox.Builder().color(.006f,.012f,.025f,1f).build(e)
            else Skybox.Builder().environment(requireNotNull(environment)).intensity(26_000f).build(e)
        scene?.skybox=nextSky;sky?.let {e.destroySkybox(it)};sky=nextSky
        val color=if(input.night)floatArrayOf(.025f,.045f,.075f)else floatArrayOf(.52f,.66f,.73f)
        view?.fogOptions=View.FogOptions().apply {enabled=true;distance=300f;density=if(input.night).00045f else .00022f;maximumOpacity=.9f;this.color=color;fogColorFromIbl=false;cutOffDistance=24_000f}
    }

    private fun syncInputAssets() {
        val terrain=input.chartScene
        val origin=origin()
        if(lastOrigin!=origin){
            // 已上传网格使用固定原点；窗口改变必须先移除，不能把旧岸线移到新位置。
            lastOrigin=origin;shownBoat=null;shownEye=null;shownLook=null
            listOf("surface","seabed","route").forEach(::removeAsset)
        }
        if(lastSceneKey!=terrain?.sceneKey) {
            lastSceneKey=terrain?.sceneKey
            replaceBytes("surface",terrain?.sceneKey.orEmpty(),terrain?.surfaceGlb)
            replaceBytes("seabed",terrain?.sceneKey.orEmpty(),terrain?.seabedGlb)
        }
        val key="${lastSceneKey}:${origin.lat}:${origin.lon}:${input.route.hashCode()}:${input.night}"
        if(lastRouteKey!=key) {
            lastRouteKey=key
            val route=input.route.toList();val radius=terrain?.radiusMeters?:2_000.0
            build("route",key){routeMesh(route,origin,radius)}
        }
    }
    private fun origin():GeoPoint = input.chartScene?.origin?:input.viewOrigin?:input.snapshot.position?.let {navigationTerrainOrigin(GeoPoint(it.latitude,it.longitude))}?:GeoPoint(0.0,0.0)
    private fun local(point:GeoPoint,origin:GeoPoint=origin()):SpatialVector=SpatialVector(
        signedBearing(point.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat)),0.0,-(point.lat-origin.lat)*111_320)

    private fun build(slot:String,key:String,work:()->ByteArray?) {
        if(desiredKeys[slot]==key)return
        desiredKeys[slot]=key;builds.remove(slot)?.cancel()
        builds[slot]=scope.launch {
            try {
                val result=withContext(Dispatchers.Default){work()?.let {bytes->ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {put(bytes);flip()}}}
                if(!closed&&desiredKeys[slot]==key)enqueue(slot,key,result)
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){if(slot in setOf("surface","seabed","route"))Log.w("YokuliNavigation3D","Scene geometry unavailable: $slot",error)else fail(error)}
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
                current.asset.releaseSourceData()
                if(desiredKeys[current.upload.slot]==current.upload.key) {
                    removeAsset(current.upload.slot);loaded[current.upload.slot]=current.asset
                    configureAsset(current.upload.slot,current.asset);scene?.addEntities(current.asset.entities)
                }else loader?.destroyAsset(current.asset)
                loading=null
            }
        }
        if(loading==null&&uploads.isNotEmpty()) {
            val pending=uploads.removeFirst()
            if(desiredKeys[pending.slot]!=pending.key)return
            val asset=requireNotNull(loader?.createAsset(pending.buffer)){"NAVIGATION_MODEL_INVALID"}
            loading=Loading(pending,asset)
            require(asset.resourceUris.isEmpty()){"NAVIGATION_EXTERNAL_RESOURCE"}
            check(resource.asyncBeginLoad(asset)){"NAVIGATION_MODEL_UPLOAD_FAILED"}
        }
    }
    private fun configureAsset(slot:String,asset:FilamentAsset) {
        val e=requireNotNull(engine);val manager=e.renderableManager
        for(entity in asset.entities) {
            val instance=manager.getInstance(entity);if(instance==0)continue
            manager.setCastShadows(instance,slot in setOf("vessel","surface"))
            manager.setReceiveShadows(instance,slot !in setOf("route","target"))
            manager.setScreenSpaceContactShadows(instance,slot=="vessel")
        }
    }
    private fun removeAsset(slot:String){visibility.remove(slot);transforms.remove(slot);loaded.remove(slot)?.let {scene?.removeEntities(it.entities);loader?.destroyAsset(it)}}

    private fun canDraw()=active&&!closed&&!failed&&isAttachedToWindow&&windowVisibility==VISIBLE&&isShown
    private fun requestFrame(){if(canDraw()&&swap!=null&&!scheduled){scheduled=true;clock.postFrameCallback(this)}}
    private fun cancelFrames(){clock.removeFrameCallback(this);scheduled=false;frameTime=0L}
    override fun doFrame(nanos:Long) {
        scheduled=false
        if(!canDraw()||helper?.isReadyToRender!=true||width<=0||height<=0)return
        guarded {
            val dt=if(frameTime==0L)1.0/60 else ((nanos-frameTime)/1e9).coerceIn(0.0,.05);frameTime=nanos
            pumpUploads();drawTransforms(nanos,dt)
            val r=requireNotNull(renderer)
            if(r.beginFrame(requireNotNull(swap),nanos)){try{r.render(requireNotNull(view));latest=pendingFrame}finally{r.endFrame()}}
            requestFrame()
        }
    }
    private fun drawTransforms(nanos:Long,dt:Double) {
        val snapshot=input.snapshot;val terrain=input.chartScene;val radius=(terrain?.radiusMeters?:2_000.0).coerceIn(250.0,8_000.0)
        val alpha=1-exp(-dt/.16)
        deviceMotion.update(input.orientation);deviceMotion.advance(nanos)
        val desired=resolveSpatialCamera(snapshot,input.orientation,deviceMotion.shown,input.screenRotation,input.conversion,SystemClock.elapsedRealtime(),input.freeYaw,input.freePitch)
        val direction=cameraMotion.present(desired,if(input.freeYaw!=null)"free"else "navigation:${snapshot.vesselHeading?.source}",
            snapshot.vesselHeading?.observedElapsedMillis?:SystemClock.elapsedRealtime(),nanos)
        val vessel=snapshot.position?.let {local(GeoPoint(it.latitude,it.longitude))}?:SpatialVector(0.0,0.0,0.0)
        val boat=if(shownBoat==null||distance(shownBoat!!,vessel)>radius*.5)vessel else mix(shownBoat!!,vessel,alpha)
        shownBoat=boat
        val trueHeading=snapshot.vesselHeading?.takeIf {it.trueDegrees.isFinite()&&(it.observedElapsedMillis?.let {at->SystemClock.elapsedRealtime()-at}?:it.ageMillis) in 0..10_000}?.trueDegrees
        if(trueHeading!=null) {
            if(shownHeadingSource!=snapshot.vesselHeading?.source)shownHeading=trueHeading
            else shownHeading=shownHeading?.let {wrapBearing(it+signedBearing(trueHeading-it)*alpha)}?:trueHeading
            shownHeadingSource=snapshot.vesselHeading?.source
        }
        val heel=snapshot.vesselHeelDegrees?.takeIf(Double::isFinite)?.coerceIn(-80.0,80.0)?:shownHeel
        val pitch=snapshot.vesselPitchDegrees?.takeIf(Double::isFinite)?.coerceIn(-80.0,80.0)?:shownPitch
        shownHeel+=(heel-shownHeel)*alpha;shownPitch+=(pitch-shownPitch)*alpha
        visible("vessel",snapshot.position!=null&&trueHeading!=null)
        visible("neutral",snapshot.position!=null&&trueHeading==null)
        val length=input.vesselLengthMeters.takeIf {it.isFinite()}?.coerceIn(3.0,80.0)?:12.0
        transform("vessel",boat.x,0.0,boat.z,-(shownHeading?:0.0),length/3.9,shownPitch,-shownHeel)
        transform("neutral",boat.x,.4,boat.z,0.0,length*.35)
        val seabed=input.mode==NavigationChartMode.SEABED
        visible("water",!seabed);visible("ripples",!seabed)
        transform("water",boat.x,-.34,boat.z,0.0,radius*4)
        val time=(nanos/1e9)%10_000
        transform("ripples",boat.x+sin(time*.09)*2.5,-.19,boat.z+cos(time*.07)*2.0,0.0,1.0)
        val course=snapshot.courseOverGround?.takeIf {it.trueDegrees.isFinite()&&(it.observedElapsedMillis?.let {at->SystemClock.elapsedRealtime()-at}?:it.ageMillis) in 0..10_000L}
        visible("course",course!=null&&snapshot.position!=null)
        if(course!=null)transform("course",boat.x,.35,boat.z,-course.trueDegrees,(length*4/40).coerceIn(.7,4.0))
        // 船首向决定船模；COG仅有自己的细箭带，不旋转船模或填补缺失Heading。
        val yaw=input.freeYaw?:direction.trueBearing?:0.0
        val a=Math.toRadians(yaw)
        val range=when(input.mode){NavigationChartMode.FOLLOW->(radius*.12).coerceIn(85.0,500.0);NavigationChartMode.OVERVIEW->radius*.95;NavigationChartMode.SEABED->radius*.7}*zoom
        val tilt=if(input.freeYaw!=null)abs(input.freePitch).coerceIn(12.0,82.0)else when(input.mode){NavigationChartMode.FOLLOW->28.0;NavigationChartMode.OVERVIEW->61.0;NavigationChartMode.SEABED->43.0}
        val center=when(input.mode){NavigationChartMode.FOLLOW->boat+SpatialVector(sin(a)*range*.3,0.0,-cos(a)*range*.3);else->boat}
        val lookY=if(seabed)((terrain?.minElevationMeters?:-50.0)*.26).coerceAtMost(-5.0)else 0.0
        val desiredLook=SpatialVector(center.x,lookY,center.z)
        val elevation=Math.toRadians(tilt)
        val desiredEye=SpatialVector(center.x-sin(a)*range*cos(elevation),lookY+range*sin(elevation),center.z+cos(a)*range*cos(elevation))
        shownEye=shownEye?.let {mix(it,desiredEye,alpha)}?:desiredEye;shownLook=shownLook?.let {mix(it,desiredLook,alpha)}?:desiredLook
        val eye=requireNotNull(shownEye);val look=requireNotNull(shownLook)
        camera?.setProjection(48.0,width.toDouble()/height,.5,max(12_000.0,radius*12),Camera.Fov.VERTICAL)
        camera?.lookAt(eye.x,eye.y,eye.z,look.x,look.y,look.z,0.0,1.0,0.0)
        camera?.getProjectionMatrix(projectionValues);camera?.getViewMatrix(cameraValues)
        for(column in 0..3)for(row in 0..3)viewProjection[column*4+row]=(0..3).sumOf {k->projectionValues[k*4+row]*cameraValues[column*4+k]}
        val hits=mutableListOf<SpatialHit>()
        val current=snapshot.current
        val targetPoint=current?.let {targetPoint(it,boat)}
        val inRange=targetPoint!=null&&distance(targetPoint,boat)<=radius*1.5
        visible("target",current!=null&&inRange)
        if(targetPoint!=null&&current!=null) {
            val scale=(range*.035).coerceIn(6.0,45.0)
            transform("target",targetPoint.x,1.0,targetPoint.z,-yaw,scale)
            project(targetPoint+SpatialVector(0.0,scale*.75,0.0))?.let {p->hits+=SpatialHit(current.id,RectF(p.first-26*density,p.second-28*density,p.first+26*density,p.second+28*density))}
        }
        for(marker in terrain?.markers.orEmpty().take(80)) {
            val p=SpatialVector(marker.eastMeters,if(seabed)marker.elevationMeters else marker.elevationMeters.coerceAtLeast(0.0),marker.southMeters)
            project(p)?.let {screen->hits+=SpatialHit(marker.id,RectF(screen.first-20*density,screen.second-20*density,screen.first+20*density,screen.second+20*density))}
        }
        val guide=snapshot.steering?:current
        pendingFrame=SpatialPresentedFrame(direction,guide?.bearingTrueDegrees?.let {bearing->direction.trueBearing?.takeIf {snapshot.live&&direction.issue==null}?.let {signedBearing(bearing-it)}},hits)
    }
    private fun targetPoint(target:SpatialNavigationTarget,boat:SpatialVector):SpatialVector? {
        target.point?.takeIf {it.valid()}?.let {return local(it)}
        if(!input.snapshot.live)return null
        val bearing=target.bearingTrueDegrees?.takeIf(Double::isFinite)?:return null
        val distance=target.distanceMeters?.takeIf {it.isFinite()&&it>=0}?:return null
        val p=bearingVector(bearing,distance,0.0);return boat+p
    }
    private fun project(point:SpatialVector):Pair<Float,Float>? {
        if(camera==null)return null
        val m=viewProjection
        val p=doubleArrayOf(m[0]*point.x+m[4]*point.y+m[8]*point.z+m[12],
            m[1]*point.x+m[5]*point.y+m[9]*point.z+m[13],0.0,
            m[3]*point.x+m[7]*point.y+m[11]*point.z+m[15])
        if(p[3]<=0)return null
        val x=p[0]/p[3];val y=p[1]/p[3]
        if(x !in -1.0..1.0||y !in -1.0..1.0)return null
        return ((x+1)*width/2).toFloat() to ((1-y)*height/2).toFloat()
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
    override fun onResized(width:Int,height:Int){if(width>0&&height>0&&!closed){view?.viewport=Viewport(0,0,width,height);requestFrame()}}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0){helper?.setDesiredSize(w,h);onResized(w,h)}}
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(active){initialize();requestFrame()}}
    override fun onDetachedFromWindow(){cancelFrames();super.onDetachedFromWindow()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(engine!=null){if(visibility==VISIBLE)requestFrame()else cancelFrames()}}
    private fun destroySwap(){swap?.let {swap=null;engine?.destroySwapChain(it);engine?.flushAndWait()}}
    private inline fun guarded(block:()->Unit){try{block()}catch(error:Exception){fail(error)}catch(error:LinkageError){fail(error)}}
    private fun fail(error:Throwable){if(closed||failed)return;failed=true;Log.w("YokuliNavigation3D","Navigation scene unavailable",error);cancelFrames();post {if(!closed)onFailure()}}
    fun close() {
        if(closed)return;closed=true;active=false;cancelFrames();scope.cancel();builds.clear();uploads.clear()
        runCatching {helper?.detach()};helper?.renderCallback=null;helper=null;runCatching {destroySwap()}
        runCatching {resourcesLoader?.asyncCancelLoad()};runCatching {resourcesLoader?.evictResourceData()};runCatching {resourcesLoader?.destroy()};resourcesLoader=null
        loading?.asset?.let {runCatching {loader?.destroyAsset(it)}};loading=null
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
        onTarget={};onFailure={};onFreeChanged={}
    }
    private fun enterFree(){if(input.freeYaw==null){input=input.copy(freeYaw=latest?.camera?.trueBearing?:input.snapshot.vesselHeading?.trueDegrees?:0.0,freePitch=when(input.mode){NavigationChartMode.FOLLOW->-28.0;NavigationChartMode.OVERVIEW->-61.0;NavigationChartMode.SEABED->-43.0});onFreeChanged(true)}}
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!inputEnabled||!active||closed)return false
        scaleDetector.onTouchEvent(event)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=event.x;downY=event.y;dragging=false;pinching=false;startYaw=input.freeYaw?:latest?.camera?.trueBearing?:0.0;startPitch=if(input.freeYaw!=null)input.freePitch else when(input.mode){NavigationChartMode.FOLLOW->-28.0;NavigationChartMode.OVERVIEW->-61.0;NavigationChartMode.SEABED->-43.0};parent?.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_MOVE->if(event.pointerCount==1&&!pinching){val dx=event.x-downX;val dy=event.y-downY
                if(!dragging&&hypot(dx,dy)>slop){dragging=true;enterFree()}
                if(dragging){input=input.copy(freeYaw=wrapBearing(startYaw-dx/width.coerceAtLeast(1)*100),freePitch=(startPitch+dy/height.coerceAtLeast(1)*70).coerceIn(-82.0,-12.0));requestFrame()}}
            MotionEvent.ACTION_UP->{if(!dragging&&!pinching)latest?.hits?.firstOrNull {it.bounds.contains(event.x,event.y)}?.let {onTarget(it.id)};performClick();parent?.requestDisallowInterceptTouchEvent(false)}
            MotionEvent.ACTION_CANCEL->{dragging=false;parent?.requestDisallowInterceptTouchEvent(false)}
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    private fun mix(a:SpatialVector,b:SpatialVector,t:Double)=SpatialVector(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t)
    private fun distance(a:SpatialVector,b:SpatialVector)=sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2)+(a.z-b.z).pow(2))
}

/** 海面只承担光照/视角参照，不生成海底、地形或可航行证据。 */
private fun waterMesh(ripples:Boolean):ByteArray? {
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=20_000)
    val material=NavigationTerrainMaterial("navigation-water",.035f,.17f,.21f,roughness=if(ripples).21f else .25f,metallic=.08f,doubleSided=false)
    if(!ripples) {
        val a=NavigationTerrainVertex(-1f,0f,-1f);val b=NavigationTerrainVertex(-1f,0f,1f);val c=NavigationTerrainVertex(1f,0f,1f);val d=NavigationTerrainVertex(1f,0f,-1f)
        mesh.triangle(material,a,b,c);mesh.triangle(material,a,c,d)
    }else {
        val divisions=80;val size=500f
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
private fun targetMesh():ByteArray? {
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=120)
    val material=NavigationTerrainMaterial("navigation-target",.95f,.98f,1f,roughness=.3f,metallic=.12f)
    mesh.box(material,-.6f,0f,0f,.085f,1.1f,.085f);mesh.box(material,.6f,0f,0f,.085f,1.1f,.085f);mesh.box(material,0f,1.015f,0f,1.285f,.085f,.085f)
    return mesh.glb()
}
private fun routeMesh(route:List<GeoPoint>,origin:GeoPoint,radius:Double):ByteArray? {
    if(route.size<2)return null
    val mesh=NavigationTerrainMeshBuilder(maxTriangles=16_000)
    val material=NavigationTerrainMaterial("navigation-route",.75f,.95f,.98f,roughness=.45f,metallic=.05f,doubleSided=true)
    fun point(p:GeoPoint)=SpatialVector(signedBearing(p.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat)),.5,-(p.lat-origin.lat)*111_320)
    val width=(radius*.0015).coerceIn(2.0,8.0)
    for(i in 1 until route.size) {
        val a=point(route[i-1]);val b=point(route[i]);val dx=b.x-a.x;val dz=b.z-a.z;val length=hypot(dx,dz);if(length<.1)continue
        // 局部世界之外的航段不上传；跨过本窗口的长边先参数裁剪，保留实际折线。
        var lo=0.0;var hi=1.0
        for((p,q) in listOf(-dx to (a.x+radius),dx to (radius-a.x),-dz to (a.z+radius),dz to (radius-a.z))) {
            if(abs(p)<1e-10){if(q<0){lo=1.0;hi=0.0;break}}
            else {val r=q/p;if(p<0)lo=max(lo,r)else hi=min(hi,r)}
        }
        if(lo>hi)continue
        val x1=a.x+dx*lo;val z1=a.z+dz*lo;val x2=a.x+dx*hi;val z2=a.z+dz*hi
        val sx=-dz/length*width;val sz=dx/length*width
        val p=NavigationTerrainVertex((x1+sx).toFloat(),.6f,(z1+sz).toFloat());val q=NavigationTerrainVertex((x1-sx).toFloat(),.6f,(z1-sz).toFloat())
        val r=NavigationTerrainVertex((x2-sx).toFloat(),.6f,(z2-sz).toFloat());val s=NavigationTerrainVertex((x2+sx).toFloat(),.6f,(z2+sz).toFloat())
        mesh.triangle(material,p,q,r);mesh.triangle(material,p,r,s)
        if(mesh.full)break
    }
    return mesh.glb()
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

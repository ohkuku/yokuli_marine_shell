package com.yokuli.marine.shell.rebuild.scene.ais

import android.content.Context
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.View as PlatformView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.FilamentInstance
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.yokuli.marine.shell.rebuild.scene.navigation.NavigationChartScene
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.scene.MaritimeSceneResource
import com.yokuli.marine.shell.rebuild.scene.MaritimeScenePalette
import java.nio.ByteBuffer
import kotlin.math.*

/** 单场景拥有原生资源。渲染回调仅绘制投影，不接收报文、推算船位或计算警报。 */
internal class AisTrafficRenderer3D(
    context: Context,
    private var onFailure: (String) -> Unit,
    private var onReady: () -> Unit,
    private var onPresentedTargets: (Set<String>) -> Unit,
    private var onPresentedFrame: (AisSceneFrame) -> Unit,
    private var onTerrainFailure: (String?) -> Unit = {},
    private var onRouteFailure: (String?) -> Unit = {},
) : UiHelper.RendererCallback, Choreographer.FrameCallback, MaritimeSceneResource {
    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity = 0
    private var lightEntity = 0
    private var indirectLight: IndirectLight? = null
    private var sky:Skybox?=null
    private var materialProvider: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private val assets = mutableListOf<FilamentAsset>()
    private val sourceBuffers = mutableListOf<ByteBuffer>()
    private var trafficLayer:MaritimeTrafficLayer?=null
    private val observedMotion=AisObservedMotion()
    private var gestureActive=false
    private var vesselPool: TrafficModelPool? = null
    private var neutralPool: TrafficModelPool? = null
    private var plane: FilamentAsset? = null
    private var ownAsset:FilamentAsset?=null
    private var ownVisible=false
    private var terrainLayer:AisTerrainLayer?=null
    private var routeLayer:AisRouteLayer?=null
    private var route=emptyList<GeoPoint>()
    private var routeCenter:AisScenePosition?=null
    private var terrainScene:NavigationChartScene?=null
    private var showSeabed=false
    private val ownTransform=FloatArray(16)
    private var shownHeel=0.0
    private var shownPitch=0.0
    private var lastFrameNanos=0L
    private var frameDelta=1.0/60
    private var attitudeMoving=false
    private var loadingAsset = 0
    private var swapChain: SwapChain? = null
    private var helper: UiHelper? = null
    private var closed = false
    private var released = false
    private var failed = false
    private var desiredActive = false
    private var resumed = false
    private var scheduled = false
    private var loading = false
    private var ready = false
    private var frameBudget = 0
    private var frameAttempts = 0
    private var loadFrames = 0
    private var width = 0
    private var height = 0
    private var data: AisSceneData? = null
    private var frame: AisSceneFrame? = null
    private var desiredFrame: AisSceneFrame? = null
    private val cameraMotion = AisSceneCameraMotion()
    private var cameraState = AisSceneCameraState()
    private var selectedId: String? = null
    private var light = false
    private var contentDirty = false
    private var cameraDirty = true
    private var prioritizedTargets = emptyList<AisSceneTarget>()
    private var planeTransform: FloatArray? = null
    private var planeVisible = false
    private var appliedLight: Boolean? = null
    private var failureStage = "initialization"
    private var restoring = false
    private var presentedTargets = emptySet<String>()
    private val displayDensity = context.resources.displayMetrics.density.coerceAtLeast(1f)

    val textureView: TextureView = object : TextureView(context) {
        override fun onAttachedToWindow() { super.onAttachedToWindow(); refreshVisibility() }
        override fun onDetachedFromWindow() { cancelFrames(); handler.removeCallbacks(surfaceTimeout); super.onDetachedFromWindow() }
        override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); handler.post { refreshVisibility() } }
        override fun onVisibilityAggregated(isVisible: Boolean) {
            super.onVisibilityAggregated(isVisible)
            handler.post { refreshVisibility() }
        }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            syncSurfaceSize(w, h)
        }
    }.apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = PlatformView.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val surfaceTimeout = Runnable {
        if (!canDraw()) return@Runnable
        val stage = when {
            width <= 0 || height <= 0 -> "layout"
            swapChain == null -> if (ready) "resume-surface" else "surface"
            loading -> "asset-upload"
            assets.isEmpty() -> "asset-read"
            restoring -> "resume-frame"
            !ready -> "first-frame"
            else -> return@Runnable
        }
        fail(IllegalStateException("Traffic renderer timed out at $stage"), stage)
    }

    override val hostView: PlatformView get() = textureView
    override val reusable: Boolean get() = !closed && !failed
    val isReady: Boolean get() = ready && reusable
    val needsAssetRead: Boolean get() = assets.isEmpty() && reusable

    fun callbacks(failure: (String) -> Unit, ready: () -> Unit, targets: (Set<String>) -> Unit,
        frame: (AisSceneFrame) -> Unit, terrain: (String?) -> Unit, route: (String?) -> Unit) {
        onFailure = failure; onReady = ready; onPresentedTargets = targets
        onPresentedFrame = frame; onTerrainFailure = terrain; onRouteFailure = route
    }

    override fun park() {
        desiredActive = false; resumed = false; gestureActive = false; presentedTargets=emptySet()
        cancelFrames(); handler.removeCallbacks(surfaceTimeout)
        // 保留的只有图形资产；重新借用后必须用新页面事实出帧，旧目标和插值不回放。
        frame=null;desiredFrame=null;data=null;prioritizedTargets=emptyList();observedMotion.reset();trafficLayer?.forgetObservations()
        onFailure = {}; onReady = {}; onPresentedTargets = {}; onPresentedFrame = {}
        onTerrainFailure = {}; onRouteFailure = {}
    }

    fun initialize() {
        if (engine != null || closed) return
        guarded {
            Gltfio.init()
            val e = Engine.create(Engine.Backend.OPENGL).also { engine = it }
            renderer = e.createRenderer().apply { clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = doubleArrayOf(0.018, 0.018, 0.018, 1.0) } }
            scene = e.createScene()
            view = e.createView().apply {
                scene = this@AisTrafficRenderer3D.scene
                blendMode = View.BlendMode.OPAQUE
                antiAliasing = View.AntiAliasing.FXAA
                dynamicResolutionOptions=View.DynamicResolutionOptions().apply{enabled=true;homogeneousScaling=true;minScale=.8f;maxScale=1f;quality=View.QualityLevel.HIGH}
                ambientOcclusionOptions=View.AmbientOcclusionOptions().apply{enabled=true;resolution=.5f;radius=1.5f;intensity=.5f;quality=View.QualityLevel.MEDIUM}
            }
            cameraEntity = EntityManager.get().create()
            camera = e.createCamera(cameraEntity).apply { setExposure(16f, 1f / 125f, 100f) }
            view?.camera = camera
            materialProvider = UbershaderProvider(e)
            assetLoader = AssetLoader(e, requireNotNull(materialProvider), EntityManager.get())
            resourceLoader = ResourceLoader(e)
            terrainLayer=AisTerrainLayer(e,requireNotNull(scene),requireNotNull(assetLoader),::requestDraw){error->
                if(error!=null)Log.w("YokuliAis3D","Terrain unavailable; traffic remains active",error)
                handler.post{if(!closed)onTerrainFailure(error?.message)}
            }
            terrainLayer?.update(terrainScene)
            routeLayer=AisRouteLayer(e,requireNotNull(scene),requireNotNull(assetLoader),::requestDraw){error->
                if(error!=null)Log.w("YokuliAis3D","Route overlay unavailable",error)
                handler.post{if(!closed)onRouteFailure(error?.message)}
            }
            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.SUN)
                .color(1f, 1f, 1f).intensity(90_000f).direction(-0.6f, -1f, -0.5f)
                .castShadows(true).shadowOptions(LightManager.ShadowOptions().apply {mapSize=1024;shadowCascades=2;shadowFar=900f;normalBias=1.2f}).build(e, lightEntity)
            scene?.addEntity(lightEntity)
            indirectLight = IndirectLight.Builder().irradiance(1, floatArrayOf(.9f, .9f, .9f)).intensity(24_000f).build(e)
            scene?.indirectLight = indirectLight
            helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also { it.isOpaque = true; it.renderCallback = this }
            // Pager 可先测量/创建 TextureView，再激活本页、启动引擎。
            // UiHelper 1.75 在 attach 到已有 SurfaceTexture 时用 desiredSize 回调，
            // 默认值为 0，不能指望之后再发生一次 Android 尺寸变化来补救。
            syncSurfaceSize(textureView.width, textureView.height)
            helper?.attachTo(textureView)
            refreshVisibility()
        }
    }

    fun beginAssetRead() { if (!closed) failureStage = "asset-read" }

    fun load(buffers: List<ByteBuffer>) {
        if (closed) return
        guarded {
            failureStage = "asset-upload"
            check(buffers.size in 3..4)
            sourceBuffers.addAll(buffers)
            val loader = requireNotNull(assetLoader)
            // 先装小批，只有进入视野的目标才驱动扩充；全部目标仍有可拾取平面符号。
            val vessels = arrayOfNulls<FilamentInstance>(4)
            val vesselAsset = requireNotNull(loader.createInstancedAsset(buffers[0], vessels))
            assets.add(vesselAsset)
            vesselPool = TrafficModelPool(vesselAsset, vessels.filterNotNull())
            val neutral = arrayOfNulls<FilamentInstance>(4)
            val neutralAsset = requireNotNull(loader.createInstancedAsset(buffers[1], neutral))
            assets.add(neutralAsset)
            neutralPool = TrafficModelPool(neutralAsset, neutral.filterNotNull())
            plane = requireNotNull(loader.createAsset(buffers[2])).also(assets::add)
            if(buffers.size==4)ownAsset=requireNotNull(loader.createAsset(buffers[3])).also(assets::add)
            check(assets.all { it.resourceUris.isEmpty() }) { "Traffic assets must be local and self contained" }
            loadingAsset = 0
            check(resourceLoader?.asyncBeginLoad(assets[loadingAsset]) == true)
            loading = true
            contentDirty = true
            requestDraw()
        }
    }

    fun update(data: AisSceneData, frame: AisSceneFrame, state: AisSceneCameraState, selectedId: String?, light: Boolean, active: Boolean, terrainScene:NavigationChartScene?=null, showSeabed:Boolean=false,gestureActive:Boolean=false,route:List<GeoPoint> = emptyList()) {
        if (closed) return
        guarded {
            // 年龄文字、COG 虚线和轨迹由 Compose 画。它们更新不能让全部原生模型重传材质/变换。
            val geometryChanged = nativeGeometryChanged(this.data, data)
            val terrainChanged=this.terrainScene?.sceneKey!=terrainScene?.sceneKey||this.showSeabed!=showSeabed
            this.terrainScene=terrainScene;this.showSeabed=showSeabed;this.gestureActive=gestureActive
            terrainLayer?.update(terrainScene)
            val cameraChanged = desiredFrame?.camera != frame.camera || desiredFrame?.local?.origin != frame.local.origin
            if (geometryChanged || desiredFrame?.targets !== frame.targets || this.selectedId != selectedId) {
                prioritizedTargets = frame.targets.sortedWith(compareByDescending<AisSceneTarget> { it.id == selectedId }
                    .thenByDescending { it.risk }.thenByDescending { it.followed }.thenBy { it.id })
            }
            val routeChanged=this.route!=route;this.route=route
            routeCenter=if(state.followOwn)data.ownPosition else state.centerLatitude?.let{lat->state.centerLongitude?.let{lon->AisScenePosition(lat,lon)}}
            routeLayer?.update(route,frame,state.rangeMeters,routeCenter)
            val changed = routeChanged || terrainChanged || geometryChanged || cameraChanged || cameraState.rangeMeters != state.rangeMeters || this.selectedId != selectedId || this.light != light
            val visibilityChanged = desiredActive != active
            this.data = data; desiredFrame = frame; if (this.frame == null) this.frame = frame; cameraState = state; this.selectedId = selectedId; this.light = light; desiredActive = active
            if (changed) {
                contentDirty = true
                if (cameraChanged) cameraDirty = true
                requestDraw()
            }
            if (visibilityChanged) {
                if (active && ready) restoring = true
                refreshVisibility()
            }
        }
    }

    fun setResumed(value: Boolean) {
        if (closed || resumed == value) return
        resumed = value
        if (value && ready) restoring = true
        refreshVisibility()
    }

    private fun canDraw() = !closed && desiredActive && resumed && textureView.isAttachedToWindow && textureView.windowVisibility == PlatformView.VISIBLE
    /** 渲染缓冲始终跟随真实宿主尺寸；页面预组装、重入和旋转走同一路径。 */
    private fun syncSurfaceSize(viewWidth: Int, viewHeight: Int) {
        if (closed || viewWidth <= 0 || viewHeight <= 0) return
        val currentHelper = helper ?: return
        guarded {
            if (currentHelper.desiredWidth != viewWidth || currentHelper.desiredHeight != viewHeight) {
                currentHelper.setDesiredSize(viewWidth, viewHeight)
            }
            // attach 前尚无 RenderSurface，因此 setDesiredSize 可能不会发 onResized。
            onResized(viewWidth, viewHeight)
        }
    }
    private fun refreshVisibility() {
        if (closed) return
        if (canDraw()) {
            syncSurfaceSize(textureView.width, textureView.height)
            if (swapChain == null || !ready || restoring) { handler.removeCallbacks(surfaceTimeout); handler.postDelayed(surfaceTimeout, 10_000L) }
            requestDraw()
        } else { cancelFrames(); handler.removeCallbacks(surfaceTimeout) }
    }

    private fun updateCamera() {
        if (width <= 0 || height <= 0) return
        val pose = frame?.camera ?: return
        if (pose.verticalFovDegrees != null) camera?.setProjection(pose.verticalFovDegrees, pose.aspect, pose.clipNear, pose.clipFar, Camera.Fov.VERTICAL)
        else camera?.setProjection(Camera.Projection.ORTHO, -pose.halfWidth, pose.halfWidth, -pose.halfHeight, pose.halfHeight, pose.clipNear, pose.clipFar)
        camera?.lookAt(pose.eye.x, pose.eye.y, pose.eye.z, pose.target.x, pose.target.y, pose.target.z, pose.up.x, pose.up.y, pose.up.z)
        cameraDirty = false
    }

    private fun updateContent() {
        val current = data ?: return
        val f = frame ?: return
        val s = scene ?: return
        val e = engine ?: return
        val tm = e.transformManager
        val own = current.ownPosition?.takeIf { it.valid }?.let {
            AisSceneTarget(OWN_ID, "", it, current.ownHeadingDegrees, current.ownCogDegrees, current.ownSogMetersPerSecond, current.ownDimensions)
        }
        // 视野和模型预算只影响绘制，不修改领域目标、警报或关注状态。
        fun visible(target: AisSceneTarget): Boolean {
            val p = f.targetProjections[target.id] ?: f.camera.project(f.local.position(target.position))
            return p.x in -.25f..1.25f && p.y in -.25f..1.25f
        }
        val loader = requireNotNull(assetLoader)
        val layer=trafficLayer?:MaritimeTrafficLayer(e,s,loader,requireNotNull(vesselPool),requireNotNull(neutralPool)).also{trafficLayer=it}
        tm.openLocalTransformTransaction()
        try {
            layer.draw(f,selectedId,width,displayDensity,own?.takeIf{ownAsset==null||validAisBearing(it.headingDegrees)==null})
            ownAsset?.let{asset->
                val shouldShow=own!=null&&validAisBearing(own.headingDegrees)!=null&&visible(own)
                if(shouldShow&&own!=null){
                    val geometry=aisDisplayGeometry(own,f,width,displayDensity)
                    Matrix.setIdentityM(ownTransform,0);Matrix.translateM(ownTransform,0,geometry.position.x.toFloat(),0f,geometry.position.z.toFloat())
                    Matrix.rotateM(ownTransform,0,-Math.toDegrees(geometry.headingRadians).toFloat(),0f,1f,0f)
                    Matrix.rotateM(ownTransform,0,shownPitch.toFloat(),1f,0f,0f);Matrix.rotateM(ownTransform,0,-shownHeel.toFloat(),0f,0f,1f)
                    Matrix.scaleM(ownTransform,0,(geometry.beam/1.26).toFloat(),(geometry.length/3.9).toFloat(),(geometry.length/3.9).toFloat())
                    tm.setTransform(tm.getInstance(asset.root),ownTransform)
                }
                if(shouldShow!=ownVisible){ownVisible=shouldShow;if(shouldShow)s.addEntities(asset.entities)else s.removeEntities(asset.entities)}
            }
            plane?.let { asset ->
                val size = max(f.camera.halfHeight, f.camera.halfWidth) * 20.0
                val matrix = floatArrayOf(size.toFloat(), 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, size.toFloat(), 0f, f.camera.target.x.toFloat(), -.34f, f.camera.target.z.toFloat(), 1f)
                if (planeTransform?.contentEquals(matrix) != true) {
                    tm.setTransform(tm.getInstance(asset.root), matrix)
                    planeTransform = matrix
                }
                if (showSeabed&&planeVisible){s.removeEntities(asset.entities);planeVisible=false}
                if (!showSeabed&&(!planeVisible||appliedLight!=light)) {
                    val rm = e.renderableManager
                    asset.entities.forEach { entity ->
                        val instance = rm.getInstance(entity)
                        if (instance != 0) for (index in 0 until rm.getPrimitiveCount(instance)) {
                            val material = rm.getMaterialInstanceAt(instance, index)
                            if (material.material.hasParameter("baseColorFactor")) MaritimeScenePalette.sea(light).let{color->material.setParameter("baseColorFactor",color[0],color[1],color[2],1f)}
                            if (material.material.hasParameter("roughnessFactor")) material.setParameter("roughnessFactor", .74f)
                        }
                    }
                    s.addEntities(asset.entities); planeVisible = true
                }
            }
        } finally { tm.commitLocalTransformTransaction() }
        if (lightEntity != 0 && appliedLight != light) {
            e.lightManager.setIntensity(e.lightManager.getInstance(lightEntity), if (light) 88_000f else 7_000f)
            indirectLight?.intensity=if(light)26_000f else 3_000f
            val color=MaritimeScenePalette.horizon(light)
            val background=Skybox.Builder().color(color[0],color[1],color[2],1f).build(e)
            scene?.skybox=background;sky?.let{e.destroySkybox(it)};sky=background
            view?.fogOptions=View.FogOptions().apply {enabled=true;distance=300f;density=if(light).00022f else .00045f;maximumOpacity=.9f;this.color=color;fogColorFromIbl=false;cutOffDistance=24_000f}
            appliedLight = light
        }
        contentDirty = layer.growing
        if (contentDirty) frameBudget = max(frameBudget, 2)
    }

    private fun requestDraw() {
        frameBudget = max(frameBudget, 2)
        if (canDraw() && swapChain != null && !scheduled) { scheduled = true; choreographer.postFrameCallback(this) }
    }
    private fun cancelFrames() { choreographer.removeFrameCallback(this); scheduled = false;lastFrameNanos=0L }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!canDraw()) return
        if (helper?.isReadyToRender != true || width <= 0 || height <= 0) { requestDraw(); return }
        guarded {
            frameDelta=if(lastFrameNanos==0L)1.0/60 else ((frameTimeNanos-lastFrameNanos)/1e9).coerceIn(0.0,.05);lastFrameNanos=frameTimeNanos
            val heel=data?.ownHeelDegrees?.takeIf(Double::isFinite)?.coerceIn(-80.0,80.0)?:0.0
            val pitch=data?.ownPitchDegrees?.takeIf(Double::isFinite)?.coerceIn(-80.0,80.0)?:0.0
            attitudeMoving=abs(heel-shownHeel)>.02||abs(pitch-shownPitch)>.02
            if(attitudeMoving){val t=1-exp(-frameDelta/.16);shownHeel+=(heel-shownHeel)*t;shownPitch+=(pitch-shownPitch)*t;contentDirty=true}
            failureStage = when {
                loading -> "asset-upload"
                restoring -> "resume-frame"
                assets.isEmpty() -> "asset-read"
                !ready -> "first-frame"
                else -> "frame"
            }
            desiredFrame?.let { requested ->
                val previousLocal=frame?.local
                if(previousLocal!=null&&previousLocal!==requested.local)cameraMotion.rebase(requested.local.position(previousLocal.origin))
                val animated=trafficLayer?.presentedIds?.takeIf{it.isNotEmpty()}?:prioritizedTargets.take(64).mapTo(mutableSetOf()){it.id}
                val pose=cameraMotion.advance(requested.camera,frameTimeNanos,gestureActive)
                val presented=data?.let{observedMotion.advance(requested,it,frameDelta,animated,pose)}?:requested.copy(camera=pose)
                if(frame!=presented){frame=presented;cameraDirty=true;contentDirty=true}
            }
            if (cameraDirty) updateCamera()
            if (loading) {
                resourceLoader?.asyncUpdateLoad()
                check(++loadFrames < 1200) { "Traffic resources timed out" }
                if ((resourceLoader?.asyncGetLoadProgress() ?: 0f) >= 1f) {
                    // 池的 glTF 层级保留给后续 createInstance；仅平面可立即释放。
                    if (loadingAsset >= 2) assets[loadingAsset].releaseSourceData()
                    loadingAsset++
                    if (loadingAsset < assets.size) check(resourceLoader?.asyncBeginLoad(assets[loadingAsset]) == true)
                    else { loading = false; failureStage = if (restoring) "resume-frame" else "first-frame"; contentDirty = true; frameBudget = 2 }
                }
            }
            frame?.let{terrainLayer?.advance(it,!gestureActive);routeLayer?.update(route,it,cameraState.rangeMeters,routeCenter);routeLayer?.advance(it,!gestureActive)}
            if (!loading && assets.isNotEmpty() && contentDirty) updateContent()
            if (!loading) failureStage = if (restoring) "resume-frame" else if (!ready) "first-frame" else "frame"
            val r = requireNotNull(renderer)
            if (r.beginFrame(requireNotNull(swapChain), frameTimeNanos)) {
                try { r.render(requireNotNull(view)) } finally { r.endFrame() }
                frameAttempts = 0
                frame?.let(onPresentedFrame)
                if (!loading && assets.isNotEmpty()) {
                    val drawn = trafficLayer?.presentedIds.orEmpty()
                    if (drawn != presentedTargets) {
                        presentedTargets = drawn
                        handler.post { if (!closed) onPresentedTargets(drawn) }
                    }
                }
                frameBudget--
                if (!loading && assets.isNotEmpty() && restoring) { restoring = false; handler.removeCallbacks(surfaceTimeout) }
                if (!loading && assets.isNotEmpty() && !ready) { ready = true; handler.removeCallbacks(surfaceTimeout); handler.post { if (!closed) onReady() } }
            } else check(++frameAttempts < 120) { "Traffic frame unavailable" }
            // 静态观测不需要持续帧循环；新观测、手势与资源上传才申请绘制。
            if ((loading || terrainLayer?.busy==true || routeLayer?.busy==true || frameBudget > 0 || cameraMotion.moving || observedMotion.moving || attitudeMoving) && canDraw()) { scheduled = true; choreographer.postFrameCallback(this) }
        }
    }

    override fun onNativeWindowChanged(surface: Surface) {
        if (closed) return
        guarded {
            restoring = ready
            failureStage = if (restoring) "resume-surface" else "surface"
            destroySwapChain(); swapChain = requireNotNull(engine).createSwapChain(surface, helper?.swapChainFlags ?: SwapChainFlags.CONFIG_DEFAULT)
            syncSurfaceSize(textureView.width, textureView.height)
            refreshVisibility()
        }
    }
    override fun onDetachedFromSurface() {
        if (closed) return
        cancelFrames()
        restoring = ready
        guarded { destroySwapChain() }
        // 可见页面丢失 Surface 后也必须有恢复期限，不能一直留着旧画面。
        if (canDraw()) { handler.removeCallbacks(surfaceTimeout); handler.postDelayed(surfaceTimeout, 10_000L) }
    }
    override fun onResized(width: Int, height: Int) {
        if (closed || width <= 0 || height <= 0 || this.width == width && this.height == height) return
        guarded {
            // viewport 与绘制指令按顺序入队；改变布局尺寸不需要在 UI 线程等 GPU 清空。
            // Surface 真正销毁时的 fence 仍由 destroySwapChain 保留。
            this.width = width; this.height = height
            view?.viewport = Viewport(0, 0, width, height)
            cameraDirty = true; contentDirty = true; requestDraw()
        }
    }

    private fun destroySwapChain() { swapChain?.let { swapChain = null; engine?.destroySwapChain(it); engine?.flushAndWait() } }
    private inline fun guarded(block: () -> Unit) {
        try { block() } catch (error: Exception) { fail(error) } catch (error: LinkageError) { fail(error) }
    }
    fun fail(error: Throwable, stageOverride: String? = null) {
        if (closed || failed) return
        failed = true
        val reason = stageOverride ?: when {
            error is LinkageError -> "native-library"
            engine == null -> "initialization"
            else -> failureStage
        }
        Log.w("YokuliAis3D", "Native traffic scene unavailable at $reason", error)
        releaseResources()
        handler.post { if (!released) onFailure(reason) }
    }
    override fun close() { released = true; park(); releaseResources() }
    private fun releaseResources() {
        if (closed) return
        closed = true
        cancelFrames(); handler.removeCallbacks(surfaceTimeout)
        runCatching { helper?.detach() }; helper?.renderCallback = null; helper = null
        runCatching { destroySwapChain() }
        runCatching{terrainLayer?.close()};terrainLayer=null
        runCatching{routeLayer?.close()};routeLayer=null
        runCatching { resourceLoader?.asyncCancelLoad() }
        runCatching { resourceLoader?.evictResourceData() }
        runCatching { resourceLoader?.destroy() }; resourceLoader = null
        runCatching{trafficLayer?.close()};trafficLayer=null
        assets.forEach { asset -> runCatching { scene?.removeEntities(asset.entities); assetLoader?.destroyAsset(asset) } }
        assets.clear(); sourceBuffers.clear(); vesselPool = null; neutralPool = null; plane = null;ownAsset=null;terrainScene=null
        runCatching { assetLoader?.destroy() }; assetLoader = null
        runCatching { materialProvider?.destroyMaterials() }; runCatching { materialProvider?.destroy() }; materialProvider = null
        val e = engine
        runCatching { view?.let { e?.destroyView(it) } }; view = null
        runCatching { scene?.let { e?.destroyScene(it) } }; scene = null
        runCatching { sky?.let { e?.destroySkybox(it) } }; sky = null
        runCatching { indirectLight?.let { e?.destroyIndirectLight(it) } }; indirectLight = null
        if (lightEntity != 0) { runCatching { e?.destroyEntity(lightEntity) }; runCatching { EntityManager.get().destroy(lightEntity) }; lightEntity = 0 }
        if (cameraEntity != 0) { runCatching { e?.destroyCameraComponent(cameraEntity) }; runCatching { EntityManager.get().destroy(cameraEntity) }; cameraEntity = 0 }
        camera = null
        runCatching { renderer?.let { e?.destroyRenderer(it) } }; renderer = null
        runCatching { e?.flushAndWait() }; runCatching { e?.destroy() }; engine = null
    }

    companion object { const val OWN_ID = "@own-vessel" }
}

/** 只比较原生模型真正使用的事实。时间文案变化仍即时显示，不因此提交额外 GPU 帧。 */
private fun nativeGeometryChanged(previous: AisSceneData?, next: AisSceneData): Boolean {
    if (previous == null) return true
    if (previous === next) return false
    if (previous.ownPosition != next.ownPosition || previous.ownHeelDegrees!=next.ownHeelDegrees || previous.ownPitchDegrees!=next.ownPitchDegrees || previous.ownHeadingDegrees != next.ownHeadingDegrees || previous.ownDimensions != next.ownDimensions || previous.targets.size != next.targets.size) return true
    return previous.targets.indices.any { index ->
        val a = previous.targets[index]
        val b = next.targets[index]
        a.id != b.id || a.position != b.position || a.headingDegrees != b.headingDegrees || a.dimensions != b.dimensions ||
            a.kind != b.kind || a.form != b.form || a.stale != b.stale || a.lost != b.lost || a.risk != b.risk || a.followed != b.followed
    }
}

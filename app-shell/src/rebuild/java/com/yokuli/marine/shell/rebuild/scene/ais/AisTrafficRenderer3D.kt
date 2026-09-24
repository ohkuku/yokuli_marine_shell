package com.yokuli.marine.shell.rebuild.scene.ais

import android.content.Context
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
import java.nio.ByteBuffer
import kotlin.math.*

/** 有界实例池共享网格与材质；每个目标的实体身份在观测更新之间保持不变。 */
private class TrafficModelPool(val asset: FilamentAsset, instances: List<FilamentInstance>) {
    private val available = ArrayDeque(instances)
    val assigned = mutableMapOf<String, FilamentInstance>()
    private val transforms = mutableMapOf<Int, FloatArray>()
    private val paints = mutableMapOf<Int, String>()
    fun acquire(id: String): FilamentInstance? = assigned[id] ?: available.removeFirstOrNull()?.also { assigned[id] = it }
    fun transformChanged(instance: FilamentInstance, transform: FloatArray): Boolean {
        if (transforms[instance.root]?.contentEquals(transform) == true) return false
        transforms[instance.root] = transform
        return true
    }
    fun paintChanged(instance: FilamentInstance, paint: String): Boolean {
        if (paints[instance.root] == paint) return false
        paints[instance.root] = paint
        return true
    }
    fun retain(ids: Set<String>, scene: Scene) {
        assigned.keys.filterNot(ids::contains).forEach { id ->
            assigned.remove(id)?.let { scene.removeEntities(it.entities); available.addLast(it) }
        }
    }
}

/** 单场景拥有原生资源。渲染回调仅绘制投影，不接收报文、推算船位或计算警报。 */
internal class AisTrafficRenderer3D(
    context: Context,
    private val onFailure: (String) -> Unit,
    private val onReady: () -> Unit,
) : UiHelper.RendererCallback, Choreographer.FrameCallback {
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
    private var materialProvider: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private val assets = mutableListOf<FilamentAsset>()
    private val sourceBuffers = mutableListOf<ByteBuffer>()
    private val materials = mutableMapOf<String, MaterialInstance>()
    private var vesselPool: TrafficModelPool? = null
    private var neutralPool: TrafficModelPool? = null
    private var plane: FilamentAsset? = null
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
    private var cameraState = AisSceneCameraState()
    private var selectedId: String? = null
    private var light = false
    private var contentDirty = false
    private var cameraDirty = true
    private var prioritizedTargets = emptyList<AisSceneTarget>()
    private var planeTransform: FloatArray? = null
    private var planeVisible = false
    private var appliedLight: Boolean? = null

    val textureView: TextureView = object : TextureView(context) {
        override fun onAttachedToWindow() { super.onAttachedToWindow(); refreshVisibility() }
        override fun onDetachedFromWindow() { cancelFrames(); handler.removeCallbacks(surfaceTimeout); super.onDetachedFromWindow() }
        override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); handler.post { refreshVisibility() } }
    }.apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = PlatformView.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val surfaceTimeout = Runnable {
        if (canDraw() && (swapChain == null || !ready)) fail(IllegalStateException("Traffic renderer surface timeout"))
    }

    fun initialize() {
        if (engine != null || closed) return
        guarded {
            Gltfio.init()
            val e = Engine.create(Engine.Backend.OPENGL).also { engine = it }
            renderer = e.createRenderer().apply { clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = doubleArrayOf(0.01, 0.025, 0.035, 1.0) } }
            scene = e.createScene()
            view = e.createView().apply {
                scene = this@AisTrafficRenderer3D.scene
                blendMode = View.BlendMode.OPAQUE
                antiAliasing = View.AntiAliasing.FXAA
            }
            cameraEntity = EntityManager.get().create()
            camera = e.createCamera(cameraEntity).apply { setExposure(16f, 1f / 125f, 100f) }
            view?.camera = camera
            materialProvider = UbershaderProvider(e)
            assetLoader = AssetLoader(e, requireNotNull(materialProvider), EntityManager.get())
            resourceLoader = ResourceLoader(e)
            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.95f, 0.98f, 1f).intensity(90_000f).direction(-0.6f, -1f, -0.5f)
                .castShadows(false).build(e, lightEntity)
            scene?.addEntity(lightEntity)
            indirectLight = IndirectLight.Builder().irradiance(1, floatArrayOf(0.8f, 0.88f, 1f)).intensity(24_000f).build(e)
            scene?.indirectLight = indirectLight
            helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also { it.isOpaque = true; it.renderCallback = this }
            helper?.attachTo(textureView)
        }
    }

    fun load(buffers: List<ByteBuffer>) {
        if (closed) return
        guarded {
            check(buffers.size == 3)
            sourceBuffers.addAll(buffers)
            val loader = requireNotNull(assetLoader)
            // 预分配池有明确上限；更多目标仍由同一投影的平面符号保留，不消失。
            val vessels = arrayOfNulls<FilamentInstance>(65)
            val vesselAsset = requireNotNull(loader.createInstancedAsset(buffers[0], vessels))
            assets.add(vesselAsset)
            vesselPool = TrafficModelPool(vesselAsset, vessels.filterNotNull())
            val neutral = arrayOfNulls<FilamentInstance>(65)
            val neutralAsset = requireNotNull(loader.createInstancedAsset(buffers[1], neutral))
            assets.add(neutralAsset)
            neutralPool = TrafficModelPool(neutralAsset, neutral.filterNotNull())
            plane = requireNotNull(loader.createAsset(buffers[2])).also(assets::add)
            check(assets.all { it.resourceUris.isEmpty() }) { "Traffic assets must be local and self contained" }
            loadingAsset = 0
            check(resourceLoader?.asyncBeginLoad(assets[loadingAsset]) == true)
            loading = true
            contentDirty = true
            requestDraw()
        }
    }

    fun update(data: AisSceneData, frame: AisSceneFrame, state: AisSceneCameraState, selectedId: String?, light: Boolean, active: Boolean) {
        if (closed) return
        guarded {
            // 年龄文字、COG 虚线和轨迹由 Compose 画。它们更新不能让全部原生模型重传材质/变换。
            val geometryChanged = nativeGeometryChanged(this.data, data)
            val cameraChanged = this.frame?.camera != frame.camera || this.frame?.local?.origin != frame.local.origin
            if (geometryChanged || this.selectedId != selectedId) {
                prioritizedTargets = frame.targets.sortedWith(compareByDescending<AisSceneTarget> { it.id == selectedId }
                    .thenByDescending { it.risk }.thenByDescending { it.followed }.thenBy { it.id })
            }
            val changed = geometryChanged || cameraChanged || cameraState.rangeMeters != state.rangeMeters || this.selectedId != selectedId || this.light != light
            val visibilityChanged = desiredActive != active
            this.data = data; this.frame = frame; cameraState = state; this.selectedId = selectedId; this.light = light; desiredActive = active
            if (changed) {
                contentDirty = true
                if (cameraChanged) cameraDirty = true
                requestDraw()
            }
            if (visibilityChanged) refreshVisibility()
        }
    }

    fun setResumed(value: Boolean) {
        if (closed || resumed == value) return
        resumed = value
        refreshVisibility()
    }

    private fun canDraw() = !closed && desiredActive && resumed && textureView.isAttachedToWindow && textureView.windowVisibility == PlatformView.VISIBLE
    private fun refreshVisibility() {
        if (closed) return
        if (canDraw()) {
            if (swapChain == null || !ready) { handler.removeCallbacks(surfaceTimeout); handler.postDelayed(surfaceTimeout, 10_000L) }
            requestDraw()
        } else { cancelFrames(); handler.removeCallbacks(surfaceTimeout) }
    }

    private fun updateCamera() {
        if (width <= 0 || height <= 0) return
        val pose = frame?.camera ?: return
        camera?.setProjection(Camera.Projection.ORTHO, -pose.halfWidth, pose.halfWidth, -pose.halfHeight, pose.halfHeight, 0.1, pose.clipFar)
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
        val candidates = (listOfNotNull(own) + prioritizedTargets)
            .filter { val p = f.targetProjections[it.id] ?: f.camera.project(f.local.position(it.position)); p.x in -0.25f..1.25f && p.y in -0.25f..1.25f }
        val ships = candidates.filter { it.kind == AisSceneKind.VESSEL && validAisBearing(it.headingDegrees) != null }.take(65)
        val neutral = candidates.filterNot { it.kind == AisSceneKind.VESSEL && validAisBearing(it.headingDegrees) != null }.take(65)
        vesselPool?.retain(ships.mapTo(mutableSetOf()) { it.id }, s)
        neutralPool?.retain(neutral.mapTo(mutableSetOf()) { it.id }, s)
        tm.openLocalTransformTransaction()
        try {
            for (target in ships + neutral) {
                val isShip = target.kind == AisSceneKind.VESSEL && validAisBearing(target.headingDegrees) != null
                val pool = (if (isShip) vesselPool else neutralPool) ?: continue
                val alreadyShown = pool.assigned.containsKey(target.id)
                val instance = pool.acquire(target.id) ?: continue
                val point = f.targetPositions[target.id] ?: f.local.position(target.position)
                val dims = target.dimensions?.takeIf { it.reliable && isShip }
                val symbolicLength = cameraState.rangeMeters.coerceIn(100.0, 59264.0) * .045
                val length = dims?.let { it.toBow + it.toStern } ?: symbolicLength
                val beam = dims?.let { it.toPort + it.toStarboard } ?: if (isShip) length * .34 else length * .56
                val heading = Math.toRadians(if (isShip) target.headingDegrees!! else 0.0)
                // 原始地理报告点不动，广播偏移只移动船体几何中心。
                val xOffset = dims?.let { (it.toStarboard - it.toPort) * .5 } ?: 0.0
                val zOffset = dims?.let { (it.toBow - it.toStern) * .5 } ?: 0.0
                val position = point + AisVector3(cos(heading) * xOffset + sin(heading) * zOffset, 0.0, sin(heading) * xOffset - cos(heading) * zOffset)
                val heightScale = if (isShip) length.coerceAtMost(140.0) else symbolicLength * .65
                val matrix = floatArrayOf(
                    (cos(heading) * beam).toFloat(), 0f, (sin(heading) * beam).toFloat(), 0f,
                    0f, heightScale.toFloat(), 0f, 0f,
                    (-sin(heading) * length).toFloat(), 0f, (cos(heading) * length).toFloat(), 0f,
                    position.x.toFloat(), position.y.toFloat(), position.z.toFloat(), 1f,
                )
                if (pool.transformChanged(instance, matrix)) tm.setTransform(tm.getInstance(instance.root), matrix)
                val paintKey = when { target.stale || target.lost -> "old"; target.id == OWN_ID -> "own"; target.risk -> "risk"; target.id == selectedId -> "selected"; else -> "target" }
                if (pool.paintChanged(instance, paintKey)) paint(instance, paintKey)
                if (!alreadyShown) s.addEntities(instance.entities)
            }
            plane?.let { asset ->
                val size = max(f.camera.halfHeight, f.camera.halfWidth) * 4.0
                val matrix = floatArrayOf(size.toFloat(), 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, size.toFloat(), 0f, f.camera.target.x.toFloat(), -1f, f.camera.target.z.toFloat(), 1f)
                if (planeTransform?.contentEquals(matrix) != true) {
                    tm.setTransform(tm.getInstance(asset.root), matrix)
                    planeTransform = matrix
                }
                if (!planeVisible) { s.addEntities(asset.entities); planeVisible = true }
            }
        } finally { tm.commitLocalTransformTransaction() }
        if (lightEntity != 0 && appliedLight != light) {
            e.lightManager.setIntensity(e.lightManager.getInstance(lightEntity), if (light) 98_000f else 72_000f)
            appliedLight = light
        }
        contentDirty = false
    }

    private fun paint(instance: FilamentInstance, key: String) {
        val e = engine ?: return
        val rm = e.renderableManager
        for (entity in instance.entities) {
            val renderable = rm.getInstance(entity)
            if (renderable == 0) continue
            for (primitive in 0 until rm.getPrimitiveCount(renderable)) {
                val material = materials.getOrPut(key) {
                    val source = rm.getMaterialInstanceAt(renderable, primitive)
                    MaterialInstance.duplicate(source, "ais-$key").apply {
                        if (getMaterial().hasParameter("baseColorFactor")) {
                            val color = when (key) {
                                "old" -> floatArrayOf(.28f, .31f, .34f, 1f)
                                "own" -> floatArrayOf(.95f, .98f, 1f, 1f)
                                "risk" -> floatArrayOf(1f, .27f, .10f, 1f)
                                "selected" -> floatArrayOf(.16f, .75f, 1f, 1f)
                                else -> floatArrayOf(.56f, .78f, .86f, 1f)
                            }
                            setParameter("baseColorFactor", color[0], color[1], color[2], color[3])
                        }
                    }
                }
                rm.setMaterialInstanceAt(renderable, primitive, material)
            }
        }
    }

    private fun requestDraw() {
        frameBudget = max(frameBudget, 2)
        if (canDraw() && swapChain != null && !scheduled) { scheduled = true; choreographer.postFrameCallback(this) }
    }
    private fun cancelFrames() { choreographer.removeFrameCallback(this); scheduled = false }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!canDraw() || helper?.isReadyToRender != true || width <= 0 || height <= 0) return
        guarded {
            if (cameraDirty) updateCamera()
            if (loading) {
                resourceLoader?.asyncUpdateLoad()
                check(++loadFrames < 1200) { "Traffic resources timed out" }
                if ((resourceLoader?.asyncGetLoadProgress() ?: 0f) >= 1f) {
                    assets[loadingAsset].releaseSourceData()
                    loadingAsset++
                    if (loadingAsset < assets.size) check(resourceLoader?.asyncBeginLoad(assets[loadingAsset]) == true)
                    else { loading = false; sourceBuffers.clear(); contentDirty = true; frameBudget = 2 }
                }
            }
            if (!loading && assets.isNotEmpty() && contentDirty) updateContent()
            val r = requireNotNull(renderer)
            if (r.beginFrame(requireNotNull(swapChain), frameTimeNanos)) {
                try { r.render(requireNotNull(view)) } finally { r.endFrame() }
                frameAttempts = 0
                frameBudget--
                if (!loading && assets.isNotEmpty() && !ready) { ready = true; handler.removeCallbacks(surfaceTimeout); handler.post { if (!closed) onReady() } }
            } else check(++frameAttempts < 120) { "Traffic frame unavailable" }
            // 静态观测不需要持续帧循环；新观测、手势与资源上传才申请绘制。
            if ((loading || frameBudget > 0) && canDraw()) { scheduled = true; choreographer.postFrameCallback(this) }
        }
    }

    override fun onNativeWindowChanged(surface: Surface) {
        if (closed) return
        guarded { destroySwapChain(); swapChain = requireNotNull(engine).createSwapChain(surface, helper?.swapChainFlags ?: SwapChainFlags.CONFIG_DEFAULT); refreshVisibility() }
    }
    override fun onDetachedFromSurface() { cancelFrames(); guarded { destroySwapChain() } }
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
    fun fail(error: Throwable) {
        if (closed || failed) return
        failed = true
        Log.w("YokuliAis3D", "Native traffic scene unavailable", error)
        val reason = when {
            error is LinkageError -> "native-library"
            engine == null -> "graphics-device"
            error.message?.contains("surface", ignoreCase = true) == true -> "surface-timeout"
            loading || assets.isEmpty() -> "local-model"
            else -> "graphics-frame"
        }
        releaseResources()
        handler.post { if (!released) onFailure(reason) }
    }
    fun close() { released = true; releaseResources() }
    private fun releaseResources() {
        if (closed) return
        closed = true
        cancelFrames(); handler.removeCallbacks(surfaceTimeout)
        runCatching { helper?.detach() }; helper?.renderCallback = null; helper = null
        runCatching { destroySwapChain() }
        runCatching { resourceLoader?.asyncCancelLoad() }
        runCatching { resourceLoader?.evictResourceData() }
        runCatching { resourceLoader?.destroy() }; resourceLoader = null
        assets.forEach { asset -> runCatching { scene?.removeEntities(asset.entities); assetLoader?.destroyAsset(asset) } }
        assets.clear(); sourceBuffers.clear(); vesselPool = null; neutralPool = null; plane = null
        materials.values.forEach { material -> runCatching { engine?.destroyMaterialInstance(material) } }; materials.clear()
        runCatching { assetLoader?.destroy() }; assetLoader = null
        runCatching { materialProvider?.destroyMaterials() }; runCatching { materialProvider?.destroy() }; materialProvider = null
        val e = engine
        runCatching { view?.let { e?.destroyView(it) } }; view = null
        runCatching { scene?.let { e?.destroyScene(it) } }; scene = null
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
    if (previous.ownPosition != next.ownPosition || previous.ownHeadingDegrees != next.ownHeadingDegrees || previous.ownDimensions != next.ownDimensions || previous.targets.size != next.targets.size) return true
    return previous.targets.indices.any { index ->
        val a = previous.targets[index]
        val b = next.targets[index]
        a.id != b.id || a.position != b.position || a.headingDegrees != b.headingDegrees || a.dimensions != b.dimensions ||
            a.kind != b.kind || a.stale != b.stale || a.lost != b.lost || a.risk != b.risk || a.followed != b.followed
    }
}

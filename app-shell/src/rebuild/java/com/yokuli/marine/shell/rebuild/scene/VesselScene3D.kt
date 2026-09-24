package com.yokuli.marine.shell.rebuild.scene

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.View as PlatformView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

/** 只描述观察视角；不会改变船舶读数或伪造实时姿态。 */
internal enum class VesselViewPreset { OVERVIEW, TOP, FRONT, SIDE }

/**
 * 本地 GLB 的原生渲染适配器。所有数据、标签、操作和降级界面均由 Compose 宿主管理。
 * TextureView 参与普通窗口合成，不注册触摸监听器，外层 Pivot 仍然拥有横滑手势。
 */
@Composable
internal fun VesselScene3D(
    preset: VesselViewPreset,
    light: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    onFailure: () -> Unit,
    onReady: () -> Unit = {},
    attitude: VesselAttitudePose? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failure = rememberUpdatedState(onFailure)
    val ready = rememberUpdatedState(onReady)
    val renderer = remember(context) {
        VesselSceneRenderer3D(context, { failure.value() }, { ready.value() })
    }
    AndroidView(
        factory = { renderer.textureView.also { renderer.initialize() } },
        modifier = modifier,
        update = { renderer.update(preset, light, active, attitude) },
        onRelease = { renderer.close() },
    )
    DisposableEffect(renderer, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            renderer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        renderer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            renderer.close()
        }
    }
    LaunchedEffect(renderer) {
        try {
            val buffer = withContext(Dispatchers.IO) {
                // 读取放在 IO；Filament 和资源上传只在主线程执行。GLB 不引用外部文件。
                val bytes = context.assets.open("vessel/yokuli-sloop.glb").use { it.readBytes() }
                require(bytes.size in 20..4_194_304) { "Invalid vessel asset size" }
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                    put(bytes)
                    flip()
                }
            }
            renderer.load(buffer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            renderer.fail(error)
        }
    }
}

/** 与 Camera.lookAt / 正交投影共用参数，供水平 Compose 标签连到真实模型部位。 */
internal fun vesselScenePoint(
    preset: VesselViewPreset,
    aspect: Float,
    x: Float,
    y: Float,
    z: Float,
): Offset {
    val pose = vesselCamera(preset, aspect.toDouble())
    val forward = (pose.target - pose.eye).normalized()
    val right = forward.cross(pose.up).normalized()
    val up = right.cross(forward)
    val point = Vector3(x.toDouble(), y.toDouble(), z.toDouble()) - pose.target
    return Offset(
        (0.5 + point.dot(right) / (2.0 * pose.halfWidth)).toFloat(),
        (0.5 - point.dot(up) / (2.0 * pose.halfHeight)).toFloat(),
    )
}

private data class Vector3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3) = Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
    fun normalized(): Vector3 {
        val length = sqrt(dot(this))
        return Vector3(x / length, y / length, z / length)
    }
}

private data class VesselCamera(
    val eye: Vector3,
    val target: Vector3,
    val up: Vector3,
    val halfWidth: Double,
    val halfHeight: Double,
)

private fun vesselCamera(preset: VesselViewPreset, aspect: Double): VesselCamera {
    val safeAspect = aspect.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    val halfHeight = if (preset == VesselViewPreset.TOP) 2.65 else 3.1
    val halfWidth = max(if (preset == VesselViewPreset.TOP) 1.4 else 2.3, halfHeight * safeAspect)
    val height = halfWidth / safeAspect
    return when (preset) {
        VesselViewPreset.OVERVIEW -> VesselCamera(Vector3(7.0, 5.7, 8.0), Vector3(0.0, 1.2, 0.0), Vector3(0.0, 1.0, 0.0), halfWidth, height)
        VesselViewPreset.TOP -> VesselCamera(Vector3(0.0, 10.0, 0.0), Vector3(0.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0), halfWidth, height)
        VesselViewPreset.FRONT -> VesselCamera(Vector3(0.0, 1.15, 10.0), Vector3(0.0, 1.15, 0.0), Vector3(0.0, 1.0, 0.0), halfWidth, height)
        VesselViewPreset.SIDE -> VesselCamera(Vector3(-9.0, 1.3, 0.0), Vector3(0.0, 1.3, 0.0), Vector3(0.0, 1.0, 0.0), halfWidth, height)
    }
}

/** 单个场景拥有全部 GPU 资源，不共享 Engine，也不拥有任何业务会话。 */
private class VesselSceneRenderer3D(
    context: Context,
    private val onFailure: () -> Unit,
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
    private var materials: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var asset: FilamentAsset? = null
    private var sourceBuffer: ByteBuffer? = null
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
    private var preset = VesselViewPreset.OVERVIEW
    private var light = false
    private var attitude: VesselAttitudePose? = null

    val textureView: TextureView = object : TextureView(context) {
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            refreshVisibility()
        }
        override fun onDetachedFromWindow() {
            cancelFrames()
            handler.removeCallbacks(surfaceTimeout)
            super.onDetachedFromWindow()
        }
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            // Android View 构造过程也可能调用此方法；等 textureView 字段完成初始化。
            handler.post { refreshVisibility() }
        }
    }.apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = PlatformView.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val surfaceTimeout = Runnable {
        if (canDraw() && (swapChain == null || !ready)) fail(IllegalStateException("Vessel scene did not become ready"))
    }

    fun initialize() {
        if (engine != null || closed) return
        guarded {
            Gltfio.init()
            val e = Engine.create(Engine.Backend.OPENGL).also { engine = it }
            renderer = e.createRenderer().apply {
                clearOptions = Renderer.ClearOptions().apply { clear = true }
            }
            scene = e.createScene()
            view = e.createView().apply {
                scene = this@VesselSceneRenderer3D.scene
                blendMode = View.BlendMode.TRANSLUCENT
                antiAliasing = View.AntiAliasing.FXAA
            }
            cameraEntity = EntityManager.get().create()
            camera = e.createCamera(cameraEntity).apply { setExposure(16f, 1f / 125f, 100f) }
            view?.camera = camera
            materials = UbershaderProvider(e)
            assetLoader = AssetLoader(e, requireNotNull(materials), EntityManager.get())
            resourceLoader = ResourceLoader(e)
            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 0.97f, 0.92f)
                .intensity(85_000f)
                .direction(-0.6f, -1f, -0.5f)
                .castShadows(false)
                .build(e, lightEntity)
            scene?.addEntity(lightEntity)
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(0.85f, 0.9f, 1f))
                .intensity(25_000f)
                .build(e)
            scene?.indirectLight = indirectLight
            applyLight()
            helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also {
                it.isOpaque = false
                it.renderCallback = this
            }
            helper?.attachTo(textureView)
        }
    }

    fun load(buffer: ByteBuffer) {
        if (closed) return
        guarded {
            sourceBuffer = buffer
            asset = requireNotNull(assetLoader?.createAsset(buffer)) { "Unable to load vessel GLB" }
            applyAttitude()
            require(asset?.resourceUris?.isEmpty() == true) { "Vessel GLB must be self contained" }
            check(resourceLoader?.asyncBeginLoad(requireNotNull(asset)) == true) { "Vessel resources failed to load" }
            loading = true
            requestDraw()
        }
    }

    fun update(preset: VesselViewPreset, light: Boolean, active: Boolean, attitude: VesselAttitudePose?) {
        if (closed) return
        guarded {
            val changed = this.preset != preset || this.light != light
            val visibilityChanged = desiredActive != active
            val poseChanged = this.attitude != attitude
            this.preset = preset
            this.light = light
            this.attitude = attitude
            desiredActive = active
            if (changed) {
                updateCamera()
                applyLight()
                requestDraw()
            }
            if (poseChanged) {
                // 数字与模型使用同一观测：不外推，不跨断源或换源插值，也没有空转动画。
                applyAttitude()
                requestDraw(1)
            }
            if (visibilityChanged) refreshVisibility()
        }
    }

    fun setResumed(value: Boolean) {
        if (closed) return
        if (resumed == value) return
        resumed = value
        refreshVisibility()
    }

    private fun canDraw() = !closed && desiredActive && resumed && textureView.isAttachedToWindow && textureView.windowVisibility == PlatformView.VISIBLE

    private fun refreshVisibility() {
        if (closed) return
        if (canDraw()) {
            if (swapChain == null || !ready) {
                handler.removeCallbacks(surfaceTimeout)
                handler.postDelayed(surfaceTimeout, 8_000L)
            }
            requestDraw()
        } else {
            cancelFrames()
            handler.removeCallbacks(surfaceTimeout)
        }
    }

    private fun applyLight() {
        val e = engine ?: return
        if (lightEntity == 0) return
        val instance = e.lightManager.getInstance(lightEntity)
        e.lightManager.setIntensity(instance, if (light) 85_000f else 62_000f)
        indirectLight?.intensity = if (light) 25_000f else 21_000f
    }

    private fun applyAttitude() {
        val transform = engine?.transformManager ?: return
        val root = asset?.root ?: return
        val instance = transform.getInstance(root)
        if (instance != 0) transform.setTransform(instance, (attitude ?: VesselAttitudePose(0.0, 0.0)).matrix())
    }

    private fun updateCamera() {
        if (width <= 0 || height <= 0) return
        val pose = vesselCamera(preset, width.toDouble() / height)
        camera?.setProjection(Camera.Projection.ORTHO, -pose.halfWidth, pose.halfWidth, -pose.halfHeight, pose.halfHeight, 0.1, 50.0)
        camera?.lookAt(pose.eye.x, pose.eye.y, pose.eye.z, pose.target.x, pose.target.y, pose.target.z, pose.up.x, pose.up.y, pose.up.z)
    }

    private fun requestDraw(frames: Int = 3) {
        frameBudget = max(frameBudget, frames)
        if (canDraw() && swapChain != null && !scheduled) {
            scheduled = true
            choreographer.postFrameCallback(this)
        }
    }

    private fun cancelFrames() {
        choreographer.removeFrameCallback(this)
        scheduled = false
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!canDraw() || helper?.isReadyToRender != true || width <= 0 || height <= 0) return
        guarded {
            if (loading) {
                resourceLoader?.asyncUpdateLoad()
                check(++loadFrames < 600) { "Vessel resources did not finish loading" }
                if ((resourceLoader?.asyncGetLoadProgress() ?: 0f) >= 1f) {
                    scene?.addEntities(requireNotNull(asset).entities)
                    asset?.releaseSourceData()
                    sourceBuffer = null
                    loading = false
                    frameBudget = 3
                }
            }
            val r = requireNotNull(renderer)
            val chain = requireNotNull(swapChain)
            if (r.beginFrame(chain, frameTimeNanos)) {
                try {
                    r.render(requireNotNull(view))
                } finally {
                    r.endFrame()
                }
                frameAttempts = 0
                frameBudget--
                if (!loading && asset != null && !ready) {
                    ready = true
                    handler.removeCallbacks(surfaceTimeout)
                    handler.post { if (!closed) onReady() }
                }
            } else {
                check(++frameAttempts < 120) { "Vessel frame could not be rendered" }
            }
            // 只有资源上传和失效的少量缓冲帧继续运行；静止模型不占用持续帧循环。
            if ((loading || frameBudget > 0) && canDraw()) {
                scheduled = true
                choreographer.postFrameCallback(this)
            }
        }
    }

    override fun onNativeWindowChanged(surface: Surface) {
        if (closed) return
        guarded {
            destroySwapChain()
            swapChain = requireNotNull(engine).createSwapChain(surface, helper?.swapChainFlags ?: SwapChainFlags.CONFIG_TRANSPARENT)
            if(ready) handler.removeCallbacks(surfaceTimeout)
            refreshVisibility()
        }
    }

    override fun onDetachedFromSurface() {
        cancelFrames()
        guarded { destroySwapChain() }
        if (canDraw()) {
            handler.removeCallbacks(surfaceTimeout)
            handler.postDelayed(surfaceTimeout, 8_000L)
        }
    }

    override fun onResized(width: Int, height: Int) {
        if (closed || width <= 0 || height <= 0) return
        guarded {
            // 先清空旧尺寸的待提交绘制，再切换 viewport；不改变源模型坐标。
            engine?.flushAndWait()
            this.width = width
            this.height = height
            view?.viewport = Viewport(0, 0, width, height)
            updateCamera()
            requestDraw()
        }
    }

    private fun destroySwapChain() {
        swapChain?.let { chain ->
            swapChain = null
            engine?.destroySwapChain(chain)
            // UiHelper 在回调后释放 Surface；必须先等原生窗口绘制命令退出。
            engine?.flushAndWait()
        }
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            fail(error)
        } catch (error: LinkageError) {
            fail(error)
        }
    }

    fun fail(error: Throwable) {
        if (failed || closed) return
        failed = true
        Log.w("YokuliVesselScene", "Native vessel renderer unavailable", error)
        releaseResources()
        handler.post { if (!released) onFailure() }
    }

    fun close() {
        released = true
        releaseResources()
    }

    private fun releaseResources() {
        if (closed) return
        closed = true
        cancelFrames()
        handler.removeCallbacks(surfaceTimeout)
        // 逐项释放让初始化中途失败也可回收已创建的对象；先 Surface，再资源，最后 Engine。
        runCatching { helper?.detach() }
        helper?.renderCallback = null
        helper = null
        runCatching { destroySwapChain() }
        runCatching { resourceLoader?.asyncCancelLoad() }
        runCatching { resourceLoader?.evictResourceData() }
        runCatching { resourceLoader?.destroy() }
        resourceLoader = null
        runCatching { asset?.let { scene?.removeEntities(it.entities); assetLoader?.destroyAsset(it) } }
        asset = null
        sourceBuffer = null
        runCatching { assetLoader?.destroy() }
        assetLoader = null
        runCatching { materials?.destroyMaterials() }
        runCatching { materials?.destroy() }
        materials = null
        val e = engine
        runCatching { view?.let { e?.destroyView(it) } }
        view = null
        runCatching { scene?.let { e?.destroyScene(it) } }
        scene = null
        runCatching { indirectLight?.let { e?.destroyIndirectLight(it) } }
        indirectLight = null
        if (lightEntity != 0) {
            runCatching { e?.destroyEntity(lightEntity) }
            runCatching { EntityManager.get().destroy(lightEntity) }
            lightEntity = 0
        }
        if (cameraEntity != 0) {
            runCatching { e?.destroyCameraComponent(cameraEntity) }
            runCatching { EntityManager.get().destroy(cameraEntity) }
            cameraEntity = 0
        }
        camera = null
        runCatching { renderer?.let { e?.destroyRenderer(it) } }
        renderer = null
        runCatching { e?.flushAndWait() }
        runCatching { e?.destroy() }
        engine = null
    }
}

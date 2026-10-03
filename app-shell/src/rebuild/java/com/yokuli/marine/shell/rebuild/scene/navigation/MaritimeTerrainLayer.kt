package com.yokuli.marine.shell.rebuild.scene.navigation

import android.os.SystemClock
import com.yokuli.runtime.contract.chart.ChartBounds
import com.google.android.filament.Engine
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 海图导航与 AIS 共用的地形 GPU 层。来源和编译产物由上游持有，这里只拥有图形资源。
 * 一次只创建一个网格；相邻块复用，旧窗口保留到新窗口上传完成，换来源立即清空。
 * 资料损坏只影响地形层，不能让实时交通、船位和相机一起退出。
 */
internal class MaritimeTerrainLayer(
    private val engine: Engine,
    private val scene: Scene,
    private val loader: AssetLoader,
    private val requestFrame: () -> Unit,
    private val onFailure: (Throwable?) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resources = ResourceLoader(engine)
    // 每层最多一份 GPU 资产。窗口更新/取消可能发生在 surface 完成而 seabed 尚未上传时；
    // 用层身份续传，不能重新累加 surface 后靠 assets.size 猜“加载完成”。
    private data class Patch(val data: NavigationChartScene, val assets: MutableMap<Boolean, FilamentAsset> = linkedMapOf()) {
        fun ready() = (data.surfaceGlb == null || assets.containsKey(true)) &&
            (data.seabedGlb == null || assets.containsKey(false))
    }
    private data class Upload(val key: String, val surface: Boolean, val bytes: ByteBuffer)
    private data class Loading(val upload: Upload, val asset: FilamentAsset,
        var lastFrameMillis:Long=SystemClock.elapsedRealtime(),var stalledMillis:Long=0L,var progress:Float=0f)
    private val resident = linkedMapOf<String, Patch>()
    private val visible = linkedSetOf<String>()
    private val pending = ArrayDeque<Upload>()
    private val prepared = mutableSetOf<String>()
    private val complete = mutableSetOf<String>()
    private var desired = emptyList<NavigationChartScene>()
    private var rootKey: String? = null
    private var requested:NavigationChartScene?=null
    private var sourceKey: String? = null
    private var build: Job? = null
    private var loading: Loading? = null
    private var transformVersion: Any? = null
    private var generation = 0L
    private var failedGeneration = -1L
    private var closed = false
    /** 已实际上传、可被拾取的块，不拿尚未就绪的新版元数据当作屏幕事实。 */
    var presentationVersion = 0; private set
    val displayedPatches: List<NavigationChartScene> get() = visible.mapNotNull { resident[it]?.data }
    val busy get() = build?.isActive == true || loading != null || pending.isNotEmpty()

    fun update(value: NavigationChartScene?) {
        if (closed || rootKey == value?.sceneKey && sourceKey == value?.sourceKey) return
        requested=value
        rootKey = value?.sceneKey
        generation++
        if (sourceKey != value?.sourceKey || value == null) {
            cancelPending()
            removeAll()
        }
        sourceKey = value?.sourceKey
        desired = value?.let { it.patches.ifEmpty { listOf(it) } }.orEmpty().distinctBy { it.sceneKey }
        val keys = desired.mapTo(mutableSetOf()) { it.sceneKey }
        build?.cancel(); build = null
        pending.removeAll { it.key !in keys }
        if (loading?.upload?.key?.let { it !in keys } == true) cancelUpload()
        resident.keys.filter { it !in keys && it !in visible }.toList().forEach(::removePatch)
        prepared.clear()
        prepared.addAll(complete.filter { it in keys })
        prepared.addAll(pending.map { it.key })
        loading?.upload?.key?.let(prepared::add)
        failedGeneration = -1L
        onFailure(null)
        scheduleNext()
        publishIfReady()
        requestFrame()
    }

    fun retry(){
        val value=requested?:return
        rootKey=null
        update(value)
    }

    /** 仅在帧前调用；Filament API 始终留在创建 Engine 的线程。 */
    fun advance(version: Any, allowUpload:Boolean=true, transformFor: (GeoPoint) -> FloatArray) {
        if (closed) return
        try {
            loading?.let { current ->
                resources.asyncUpdateLoad()
                val now=SystemClock.elapsedRealtime()
                val progress=resources.asyncGetLoadProgress()
                // 只累计实际绘制帧的等待；后台停帧不算上传超时。
                if(progress>current.progress){current.progress=progress;current.stalledMillis=0}
                else current.stalledMillis+=(now-current.lastFrameMillis).coerceIn(0,250)
                current.lastFrameMillis=now
                check(current.stalledMillis<15_000){"MARITIME_TERRAIN_UPLOAD_STALLED"}
                if (progress >= 1f) {
                    loading = null
                    current.asset.releaseSourceData()
                    val patch = resident[current.upload.key]
                    if (patch == null) loader.destroyAsset(current.asset)
                    else {
                        patch.assets.put(current.upload.surface, current.asset)?.let { previous ->
                            scene.removeEntities(previous.entities)
                            loader.destroyAsset(previous)
                        }
                        if (patch.ready()) complete += current.upload.key
                    }
                    publishIfReady()
                }
            }
            // 不在一帧里解包/创建多个 glTF。缓冲准备也只排一块，限制 direct buffer 峰值。
            if (allowUpload && loading == null && pending.isNotEmpty()) {
                val item = pending.removeFirst()
                val asset = requireNotNull(loader.createAsset(item.bytes)) { "MARITIME_TERRAIN_MODEL_INVALID" }
                loading = Loading(item, asset)
                require(asset.resourceUris.isEmpty()) { "MARITIME_TERRAIN_EXTERNAL_RESOURCE" }
                val manager = engine.renderableManager
                asset.entities.forEach { entity ->
                    val instance = manager.getInstance(entity)
                    if (instance != 0) {
                        manager.setCastShadows(instance, item.surface)
                        manager.setReceiveShadows(instance, true)
                    }
                }
                check(resources.asyncBeginLoad(asset)) { "MARITIME_TERRAIN_UPLOAD_FAILED" }
            }
            scheduleNext()
            if (transformVersion != version) {
                transformVersion = version
                val manager = engine.transformManager
                visible.forEach { key -> resident[key]?.let { patch ->
                    val matrix = transformFor(patch.data.origin)
                    patch.assets.values.forEach { manager.setTransform(manager.getInstance(it.root), matrix) }
                } }
            }
        } catch (error: Exception) { reject(error) }
    }

    private fun scheduleNext() {
        if (closed || failedGeneration == generation || busy) return
        val next = desired.firstOrNull { it.sceneKey !in complete && it.sceneKey !in prepared } ?: return
        val bytes = (next.surfaceGlb?.size ?: 0).toLong() + (next.seabedGlb?.size ?: 0)
        if (bytes > 16L * 1024 * 1024 || desired.sumOf { (it.surfaceGlb?.size ?: 0).toLong() + (it.seabedGlb?.size ?: 0) } > 96L * 1024 * 1024) {
            reject(IllegalStateException("MARITIME_TERRAIN_GPU_BUDGET")); return
        }
        prepared += next.sceneKey
        val patch = resident.getOrPut(next.sceneKey) { Patch(next) }
        if (bytes == 0L) { complete += next.sceneKey; publishIfReady(); scheduleNext(); return }
        val missing = listOfNotNull(
            next.surfaceGlb?.takeUnless { patch.assets.containsKey(true) }?.let { true to it },
            next.seabedGlb?.takeUnless { patch.assets.containsKey(false) }?.let { false to it },
        )
        if (missing.isEmpty()) { complete += next.sceneKey; publishIfReady(); scheduleNext(); return }
        val epoch = generation
        build = scope.launch {
            try {
                val buffers = withContext(Dispatchers.Default) {
                    missing.map { (surface, content) ->
                        ensureActive()
                        Upload(next.sceneKey, surface, ByteBuffer.allocateDirect(content.size).order(ByteOrder.nativeOrder()).apply { put(content); flip() })
                    }
                }
                if (!closed && generation == epoch) pending.addAll(buffers)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { reject(error) }
            finally { if (generation == epoch) { build = null; requestFrame() } }
        }
    }

    private fun publishIfReady() {
        val keys = desired.mapTo(linkedSetOf()) { it.sceneKey }
        // 上游会逐块发布根场景；“这一批上传完”不等于新窗口已完整。
        val canPublishAll = keys.size >= (requested?.expectedPatches ?: 1) && keys.all { it in complete }
        // 周围已完成块立即出现，不等最慢的边缘块。跨 LOD 时暂留相交旧块，避免同一区域
        // 新旧地形重叠闪烁；当前观察窗口之外的旧块立即退场，不随拖动无限积累 GPU 资产。
        val next = if (canPublishAll) keys else linkedSetOf<String>().apply {
            // 按覆盖块交换基础/详细层。一个边缘块慢，不应阻止中心已经完成的细节出现。
            val retainedOld=visible.filter{key->
                key !in keys&&overlaps(resident[key]?.data?.bounds,requested?.bounds)&&
                    !replacementReady(resident[key]?.data?.bounds)
            }
            addAll(retainedOld)
            addAll(keys.filter{key->key in complete&&retainedOld.none{old->
                resident[old]?.data?.hasGeometry==true&&overlaps(resident[old]?.data?.bounds,resident[key]?.data?.bounds)
            }})
        }
        (visible - next).forEach { key -> resident[key]?.assets?.values?.forEach { scene.removeEntities(it.entities) } }
        (next - visible).forEach { key -> resident[key]?.assets?.values?.forEach { scene.addEntities(it.entities) } }
        if(visible != next)presentationVersion++
        visible.clear(); visible.addAll(next)
        transformVersion = null
        resident.keys.filter { it !in keys && it !in visible }.toList().forEach(::removePatch)
    }

    private fun replacementReady(previous:ChartBounds?):Boolean {
        if(previous==null)return desired.all{it.sceneKey in complete}
        val view=requested?.bounds?:return false
        val needed=previous.split().flatMap{old->view.split().mapNotNull{box->
            val west=maxOf(old.west,box.west);val east=minOf(old.east,box.east)
            val south=maxOf(old.south,box.south);val north=minOf(old.north,box.north)
            if(west<east&&south<north)ChartBounds(west,south,east,north)else null
        }}
        return needed.all{area->
            val covered=desired.filter{it.sceneKey in complete}.sumOf{patch->
                patch.bounds?.split()?.sumOf{box->
                    (minOf(area.east,box.east)-maxOf(area.west,box.west)).coerceAtLeast(0.0)*
                        (minOf(area.north,box.north)-maxOf(area.south,box.south)).coerceAtLeast(0.0)
                }?:0.0
            }
            covered>=(area.east-area.west)*(area.north-area.south)*(1.0-1e-8)
        }
    }

    private fun overlaps(a:ChartBounds?,b:ChartBounds?):Boolean =
        if(a==null||b==null)true else a.split().any {x->b.split().any {y->
            x.west<y.east&&x.east>y.west&&x.south<y.north&&x.north>y.south
        }}

    private fun reject(error: Throwable) {
        failedGeneration = generation
        cancelPending()
        resident.keys.filter { it !in visible }.toList().forEach(::removePatch)
        onFailure(error)
    }
    private fun cancelPending() {
        build?.cancel(); build = null; pending.clear(); prepared.clear()
        cancelUpload()
    }
    private fun cancelUpload() {
        val current = loading ?: return
        loading = null
        try { resources.asyncCancelLoad(); resources.evictResourceData() }
        finally { loader.destroyAsset(current.asset) }
    }
    private fun removePatch(key: String) {
        resident.remove(key)?.assets?.values?.forEach { asset -> scene.removeEntities(asset.entities); loader.destroyAsset(asset) }
        visible.remove(key); prepared.remove(key); complete.remove(key)
    }
    private fun removeAll() { resident.keys.toList().forEach(::removePatch); presentationVersion++; transformVersion = null }
    fun close() {
        if (closed) return
        closed = true; scope.cancel()
        runCatching { cancelPending() }; runCatching { removeAll() }
        runCatching { resources.evictResourceData() }; runCatching { resources.destroy() }
        desired = emptyList()
    }
}

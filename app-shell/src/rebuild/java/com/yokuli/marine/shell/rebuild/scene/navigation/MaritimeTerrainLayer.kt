package com.yokuli.marine.shell.rebuild.scene.navigation

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
    private data class Patch(val data: NavigationChartScene, val assets: MutableList<FilamentAsset> = mutableListOf())
    private data class Upload(val key: String, val surface: Boolean, val bytes: ByteBuffer)
    private data class Loading(val upload: Upload, val asset: FilamentAsset)
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
    fun advance(version: Any, transformFor: (GeoPoint) -> FloatArray) {
        if (closed) return
        try {
            loading?.let { current ->
                resources.asyncUpdateLoad()
                if (resources.asyncGetLoadProgress() >= 1f) {
                    loading = null
                    current.asset.releaseSourceData()
                    resident[current.upload.key]?.assets?.add(current.asset) ?: loader.destroyAsset(current.asset)
                    val patch = resident[current.upload.key]
                    val expected = patch?.data?.let { (if (it.surfaceGlb != null) 1 else 0) + (if (it.seabedGlb != null) 1 else 0) } ?: 0
                    if (patch != null && patch.assets.size == expected) complete += current.upload.key
                    publishIfReady()
                }
            }
            // 不在一帧里解包/创建多个 glTF。缓冲准备也只排一块，限制 direct buffer 峰值。
            if (loading == null && pending.isNotEmpty()) {
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
                    patch.assets.forEach { manager.setTransform(manager.getInstance(it.root), matrix) }
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
        resident.getOrPut(next.sceneKey) { Patch(next) }
        if (bytes == 0L) { complete += next.sceneKey; publishIfReady(); scheduleNext(); return }
        val epoch = generation
        build = scope.launch {
            try {
                val buffers = withContext(Dispatchers.Default) {
                    listOfNotNull(next.surfaceGlb?.let { true to it }, next.seabedGlb?.let { false to it }).map { (surface, content) ->
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
        // 初次进入可逐块出现；有旧地形时保持整片直到新窗口完整就绪，不让海岸闪烁。
        val canPublishAll = keys.all { it in complete }
        if (visible.isNotEmpty() && !canPublishAll) return
        val next = if (canPublishAll) keys else keys.filterTo(linkedSetOf()) { it in complete }
        (visible - next).forEach { key -> resident[key]?.assets?.forEach { scene.removeEntities(it.entities) } }
        (next - visible).forEach { key -> resident[key]?.assets?.forEach { scene.addEntities(it.entities) } }
        if(visible != next)presentationVersion++
        visible.clear(); visible.addAll(next)
        transformVersion = null
        if (canPublishAll) resident.keys.filter { it !in keys }.toList().forEach(::removePatch)
    }

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
        resident.remove(key)?.assets?.forEach { asset -> scene.removeEntities(asset.entities); loader.destroyAsset(asset) }
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

package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 内置地名使用系统字体生成本地 sprite，随后完全由地图引擎定位、缩放和避让。
 * 不声明 text-font / glyphs URL，不需要网络字体，也不在屏幕覆盖层逐帧追赶地图。
 * 文件解析和文字栅格化在后台执行；相机每帧只判断是否已离开预取范围。
 */
internal class OfflineMapLabels(
    context: Context,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = { Log.w("OfflineMapLabels", "Bundled place labels could not be loaded", it) },
    private val onUpdated: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val density = context.resources.displayMetrics.density
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var source: GeoJsonSource? = null
    private var chinese = false
    private var satellite = false
    private var beforeLayerId: String? = null
    private var job: Job? = null
    private var generation = 0L
    private var covered: Window? = null
    private var requested: Window? = null
    private val uploaded = linkedMapOf<String, Int>()
    private var activeImageIds = emptySet<String>()
    private val bitmaps = object : LruCache<String, Bitmap>(IMAGE_BUDGET_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** 在新 style 的加载回调中调用；同一 style 仅在语言或底图明暗变化时重做标签。 */
    fun attach(map: MapLibreMap, style: Style, chinese: Boolean, beforeLayerId: String? = null, satellite: Boolean = false) {
        if (!style.isFullyLoaded || map.style !== style) return
        if (this.map === map && this.style === style && this.chinese == chinese && this.satellite == satellite && this.beforeLayerId == beforeLayerId) return
        clear()
        this.map = map
        this.style = style
        this.chinese = chinese
        this.satellite = satellite
        this.beforeLayerId = beforeLayerId
        val added = GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList<Feature>()))
        style.addSource(added)
        source = added
        val labels = SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("sprite")),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.iconPadding(3f),
            PropertyFactory.iconAnchor(Expression.get("anchor")),
            PropertyFactory.iconOffset(Expression.match(
                Expression.get("anchor"), Expression.literal("top"),
                Expression.literal(arrayOf(0f, -5.5f)), Expression.literal(arrayOf(0f, 0f)),
            )),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
            PropertyFactory.symbolSortKey(Expression.get("priority")),
        )
        // 船位、用户图钉及航线始终在地名之上。
        val foreground = beforeLayerId?.takeIf { style.getLayer(it) != null } ?: style.layers.firstOrNull {
            it.id.startsWith("yokuli-scene-") || it.id.startsWith("yokuli-native-") || it.id == "org.maplibre.annotations.points"
        }?.id
        if (foreground == null) style.addLayer(labels) else style.addLayerBelow(labels, foreground)
        refresh()
    }

    /** 可从原生 camera move 回调调用；在预取窗内不重新投影、上传或构造 GeoJSON。 */
    fun onCameraChanged(center: GeoPoint, zoom: Double) {
        if (style == null || !zoom.isFinite()) return
        val window = requested ?: covered
        if (window == null || !window.containsCamera(center, zoom)) refresh()
    }

    /** 原生 camera idle / 首次布局完成后调用；失败的内置资源加载可在下一次操作时重试。 */
    fun onCameraIdle() {
        // Idle 也覆盖旋转屏幕后的视口尺寸变化；相同地理窗直接复用。
        refresh()
    }

    /** 必须在替换 style、销毁 MapView 之前调用。 */
    fun clear() {
        generation++
        job?.cancel()
        job = null
        val old = style
        if (old != null && map?.style === old && old.isFullyLoaded) {
            old.removeLayer(LAYER_ID)
            old.removeSource(SOURCE_ID)
            uploaded.keys.forEach(old::removeImage)
        }
        source = null
        style = null
        map = null
        beforeLayerId = null
        uploaded.clear()
        activeImageIds = emptySet()
        requested = null
        covered = null
    }

    fun close() {
        clear()
        bitmaps.evictAll()
    }

    private fun refresh() {
        val currentMap = map ?: return
        val currentStyle = style?.takeIf { it.isFullyLoaded && currentMap.style === it } ?: return
        val camera = currentMap.cameraPosition
        val center = camera.target ?: return
        val region = currentMap.projection.visibleRegion
        val corners = listOfNotNull(region.farLeft, region.farRight, region.nearLeft, region.nearRight)
        // Surface 首次布局前可能还没有完整视口；布局完成或相机 idle 会再次请求。
        if (corners.size != 4) return
        val latitudeSpan = corners.maxOf { abs(it.latitude - center.latitude) }.coerceAtLeast(.00001)
        val longitudeSpan = if (region.latLngBounds.longitudeSpan >= 350.0) 180.0 else
            corners.maxOf { abs(longitudeDelta(it.longitude, center.longitude)) }.coerceAtLeast(.00001)
        val window = Window(center.latitude, center.longitude, latitudeSpan, longitudeSpan, floor(camera.zoom * 2) / 2)
        if (requested == window || covered == window) return
        job?.cancel()
        val version = ++generation
        val useChinese = chinese
        val useSatellite = satellite
        requested = window
        job = scope.launch {
            try {
                val labels = BundledLabelIndex.load(appContext)
                val prepared = withContext(Dispatchers.Default) {
                    val candidates = labels.asSequence()
                        .filter { it.minZoom <= window.zoom && it.maxZoom > window.zoom && window.containsLabel(it) }
                        .sortedWith(compareBy<PlaceLabel> { it.minZoom }.thenBy { it.rank }.thenBy { it.id })
                        .take(MAX_LABELS)
                    val result = ArrayList<PreparedLabel>()
                    var bytes = 0
                    for (label in candidates) {
                        currentCoroutineContext().ensureActive()
                        val key = "yokuli-place-${label.id}-$useChinese-$useSatellite-$densityDpi"
                        val bitmap = bitmaps.get(key) ?: makeBitmap(label, useChinese, useSatellite).also { bitmaps.put(key, it) }
                        if (bytes + bitmap.allocationByteCount > IMAGE_BUDGET_BYTES) break
                        bytes += bitmap.allocationByteCount
                        result += PreparedLabel(label, key, bitmap)
                    }
                    result
                }
                if (!isCurrent(version, currentStyle)) return@launch
                val needed = prepared.mapTo(mutableSetOf()) { it.key }
                // 只清理已不再被当前地图使用的 sprite；保留小型邻近区域缓存供反向拖动复用。
                trimUploaded(currentStyle, needed, prepared.sumOf { if (it.key in uploaded) 0 else it.bitmap.allocationByteCount })
                val missing = prepared.filter { it.key !in uploaded }
                // addImage 会复制像素到 native。每帧最多 6 个 / 256KiB（单幅较大文字除外），
                // 避免高 DPI 手机上首屏或跨城市移动时一次复制整个字库。
                var cursor = 0
                while (cursor < missing.size) {
                    // 真正按显示帧分摊 native 上传，不用固定 16ms 定时器与 90/120Hz 屏幕错拍。
                    awaitFrame()
                    if (!isCurrent(version, currentStyle)) return@launch
                    val images = HashMap<String, Bitmap>()
                    var batchBytes = 0
                    while (cursor < missing.size && images.size < 6) {
                        val item = missing[cursor]
                        if (images.isNotEmpty() && batchBytes + item.bitmap.allocationByteCount > 256 * 1024) break
                        images[item.key] = item.bitmap
                        batchBytes += item.bitmap.allocationByteCount
                        cursor++
                    }
                    currentStyle.addImages(images)
                    images.forEach { (key, bitmap) -> uploaded[key] = bitmap.allocationByteCount }
                }
                if (!isCurrent(version, currentStyle)) return@launch
                val data = withContext(Dispatchers.Default) {
                    FeatureCollection.fromFeatures(prepared.map { item ->
                        Feature.fromGeometry(Point.fromLngLat(item.label.lon, item.label.lat)).apply {
                            addStringProperty("sprite", item.key)
                            // 城市圆点精确落在城市坐标；名称排在圆点下方，不把整块 bitmap 的中心冒充城市位置。
                            addStringProperty("anchor", if (item.label.kind == "city") "top" else "center")
                            addNumberProperty("priority", item.label.minZoom * 100 + item.label.rank)
                        }
                    })
                }
                if (!isCurrent(version, currentStyle)) return@launch
                source?.setGeoJson(data)
                activeImageIds = needed
                trimUploaded(currentStyle, needed, 0)
                covered = window
                requested = null
                // setGeoJson 已交给 native；宿主可据此清除错误，并在下一次地图渲染后刷新磁贴。
                onUpdated()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrent(version, currentStyle)) {
                    requested = null
                    onError(failure)
                }
            }
        }
    }

    private fun isCurrent(version: Long, expected: Style) =
        generation == version && style === expected && map?.style === expected && expected.isFullyLoaded

    private fun trimUploaded(current: Style, needed: Set<String>, incomingBytes: Int) {
        var bytes = uploaded.values.sum() + incomingBytes
        val iterator = uploaded.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in needed && entry.key !in activeImageIds &&
                (bytes > IMAGE_BUDGET_BYTES || uploaded.size > MAX_LABELS * 2)) {
                current.removeImage(entry.key)
                bytes -= entry.value
                iterator.remove()
            }
        }
    }

    private fun makeBitmap(label: PlaceLabel, chinese: Boolean, satellite: Boolean): Bitmap {
        val text = if (chinese) label.chinese.ifBlank { label.english } else label.english.ifBlank { label.chinese }
        val water = label.kind == "sea" || label.kind == "lake"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", if (water) Typeface.ITALIC else Typeface.NORMAL)
            textSize = (if (label.kind == "country") 13.5f else if (water) 13f else 12f) * density
            textAlign = Paint.Align.CENTER
            strokeJoin = Paint.Join.ROUND
        }
        // 极长地名适当缩小，保留原始名字，不生成截断后含义不明的伪地名。
        val naturalWidth = paint.measureText(text)
        if (naturalWidth > 260 * density) paint.textSize *= (260 * density / naturalWidth)
        val padding = 4 * density
        val dot = if (label.kind == "city") 3 * density else 0f
        val width = ceil(paint.measureText(text) + padding * 2).toInt().coerceAtLeast(1)
        val height = ceil(paint.descent() - paint.ascent() + padding * 2 + dot * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { this.density = densityDpi }
        val canvas = Canvas(bitmap)
        val x = width / 2f
        val baseline = padding - paint.ascent() + dot * 2
        val fill = if (satellite) Color.WHITE else when (label.kind) {
            "sea", "lake" -> 0xFF316C87.toInt()
            "country" -> 0xFF4F5C57.toInt()
            else -> 0xFF283E45.toInt()
        }
        val halo = if (satellite) 0xDD123044.toInt() else 0xF2F4F4E8.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.8f * density
        paint.color = halo
        canvas.drawText(text, x, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawText(text, x, baseline, paint)
        if (dot > 0f) {
            paint.color = halo
            canvas.drawCircle(x, padding + dot / 2, 2.5f * density, paint)
            paint.color = fill
            canvas.drawCircle(x, padding + dot / 2, 1.4f * density, paint)
        }
        return bitmap
    }

    private data class PreparedLabel(val label: PlaceLabel, val key: String, val bitmap: Bitmap)

    private data class Window(val lat: Double, val lon: Double, val halfLat: Double, val halfLon: Double, val zoom: Double) {
        fun containsCamera(center: GeoPoint, currentZoom: Double) = floor(currentZoom * 2) / 2 == zoom &&
            abs(center.lat - lat) < halfLat * .45 &&
            (halfLon >= 180 || abs(longitudeDelta(center.lon, lon)) < halfLon * .45)

        fun containsLabel(label: PlaceLabel) = abs(label.lat - lat) <= halfLat * 1.8 &&
            (halfLon >= 100 || abs(longitudeDelta(label.lon, lon)) <= halfLon * 1.8)
    }

    private companion object {
        const val SOURCE_ID = "yokuli-offline-places"
        const val LAYER_ID = "yokuli-offline-place-labels"
        const val MAX_LABELS = 180
        const val IMAGE_BUDGET_BYTES = 6 * 1024 * 1024
        fun longitudeDelta(lon: Double, center: Double) = ((lon - center + 540) % 360) - 180
    }
}

/** 字段来自内置 Natural Earth 数据，不以当前 UI 状态伪造地名或坐标。 */
private data class PlaceLabel(
    val id: Int,
    val lat: Double,
    val lon: Double,
    val english: String,
    val chinese: String,
    val kind: String,
    val minZoom: Double,
    val maxZoom: Double,
    val rank: Double,
)

/** 一份进程级只读索引；多个海图/锚警窗口共享，解析不发生在 UI 或相机回调中。 */
private object BundledLabelIndex {
    private val mutex = Mutex()
    @Volatile private var labels: List<PlaceLabel>? = null

    suspend fun load(context: Context): List<PlaceLabel> {
        labels?.let { return it }
        return mutex.withLock {
            labels ?: withContext(Dispatchers.IO) {
                val json = context.assets.open("maps/world_labels.geojson").bufferedReader(Charsets.UTF_8).use { it.readText() }
                val features = JSONObject(json).getJSONArray("features")
                buildList {
                    for (index in 0 until features.length()) {
                        currentCoroutineContext().ensureActive()
                        val feature = features.getJSONObject(index)
                        val geometry = feature.optJSONObject("geometry") ?: continue
                        if (geometry.optString("type") != "Point") continue
                        val coordinates = geometry.optJSONArray("coordinates") ?: continue
                        val lon = coordinates.optDouble(0, Double.NaN)
                        val lat = coordinates.optDouble(1, Double.NaN)
                        if (!lon.isFinite() || !lat.isFinite() || lon !in -180.0..180.0 || lat !in -85.05113..85.05113) continue
                        val properties = feature.optJSONObject("properties") ?: continue
                        fun name(field: String) = if (properties.isNull(field)) "" else properties.optString(field).trim()
                        val english = name("name_en")
                        val chinese = name("name_zh")
                        if (english.isBlank() && chinese.isBlank()) continue
                        val minimum = properties.optDouble("min_zoom", 5.0).takeIf { it.isFinite() } ?: 5.0
                        val rank = properties.optDouble("rank", 10.0).takeIf { it.isFinite() } ?: 10.0
                        val kind = properties.optString("kind", "city")
                        val defaultMaximum = when (kind) { "country" -> 7.0; "sea" -> 12.0; else -> 24.0 }
                        val maximum = properties.optDouble("max_zoom", defaultMaximum).takeIf { it.isFinite() && it > minimum } ?: defaultMaximum
                        add(PlaceLabel(index, lat, lon, english, chinese, kind, minimum.coerceIn(0.0, 22.0), maximum, max(0.0, rank)))
                    }
                }.also { require(it.isNotEmpty()) { "Bundled place label index is empty" } }
            }.also { labels = it }
        }
    }
}

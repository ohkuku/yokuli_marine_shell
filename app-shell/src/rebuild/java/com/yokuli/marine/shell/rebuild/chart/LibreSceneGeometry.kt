package com.yokuli.marine.shell.rebuild.chart

import android.graphics.Color
import com.yokuli.marine.shell.rebuild.GeoPoint
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * MapLibre 的旧 Polyline annotation 不支持虚线；面与线统一使用原生地理样式层。
 * 相同绘制阶段 / 线宽 / 虚实的内容合批，锚泊覆盖网格不会变成几百个 style layer。
 * 固定顺序：覆盖面 → 圆边界 → 航线白边 → 航线 → 测距 → COG → 原生图钉。
 */
internal class LibreSceneGeometry {
    private data class PaintKey(val stage: Int, val width: Float = 0f, val dashed: Boolean = false) {
        val id get() = "yokuli-scene-$stage-${width.toBits()}-$dashed"
    }
    private data class Shape(val points: List<GeoPoint>, val color: Int)
    private data class GeometryState(
        val areas: List<MapArea>, val circles: List<MapCircle>, val lines: List<MapLine>,
        val ruler: List<GeoPoint>, val course: List<GeoPoint>?,
    )
    private var style: Style? = null
    private var previous: GeometryState? = null
    private val sources = linkedMapOf<PaintKey, GeoJsonSource>()
    private val values = mutableMapOf<PaintKey, List<Shape>>()
    private val layerIds = mutableListOf<String>()

    fun clear() {
        // 旧 style 被 SDK 回收后，禁止通过旧句柄操作新 style 中同名的 layer/source。
        style?.takeIf { it.isFullyLoaded }?.let { current ->
            layerIds.asReversed().forEach { current.removeLayer(it) }
            sources.keys.forEach { current.removeSource(it.id) }
        }
        layerIds.clear()
        sources.clear()
        values.clear()
        style = null
        previous = null
    }

    fun render(map: MapLibreMap, scene: MapScene, ruler: List<GeoPoint>) {
        val current = map.style?.takeIf { it.isFullyLoaded } ?: return
        if (style !== current) { clear(); style = current }
        val geometry = GeometryState(scene.areas, scene.circles, scene.lines, ruler.toList(), scene.vessel?.let(::vesselCourseVector))
        if (geometry == previous) return
        val batches = linkedMapOf<PaintKey, MutableList<Shape>>()
        fun add(stage: Int, points: List<GeoPoint>, color: Int, width: Float = 0f, dashed: Boolean = false) {
            if (points.size < (when(stage) {0->3;8->1;else->2}) || !width.isFinite() || width < 0f) return
            batches.getOrPut(PaintKey(stage, width, dashed)) { mutableListOf() }.add(Shape(points, color))
        }
        scene.areas.forEach { add(0, it.boundary, it.color.toInt()) }
        scene.circles.filter { it.radiusMeters.isFinite() && it.radiusMeters > 0 }.forEach { circle ->
            val ring = (0..72).map { destination(circle.center, circle.radiusMeters, it * 5.0) }
            add(0, ring, (circle.color.toInt() and 0xFFFFFF) or 0x10000000)
            add(1, ring, circle.color.toInt(), 1.6f, circle.dashed)
        }
        scene.lines.forEach { path ->
            if(path.points.size==1) {
                add(8,path.points,path.color.toInt(),path.widthDp.coerceAtLeast(2.5f))
                return@forEach
            }
            if (Color.alpha(path.color.toInt()) == 255) add(2, path.points, 0xBBFFFFFF.toInt(), path.widthDp + 1.5f, path.dashed)
            add(3, path.points, path.color.toInt(), path.widthDp, path.dashed)
        }
        if (ruler.size == 2) {
            add(4, ruler, Color.WHITE, 4.5f)
            add(5, ruler, 0xFFD74A29.toInt(), 2.5f, true)
        }
        geometry.course?.let {
            add(6, it, Color.WHITE, 3.5f, true)
            add(7, it, 0xFF007ADC.toInt(), 1.8f, true)
        }
        val ordered = batches.keys.sortedWith(compareBy<PaintKey> { it.stage }.thenBy { it.width }.thenBy { it.dashed })
        val topologyChanged = ordered != sources.keys.toList()
        if (topologyChanged) {
            // 先移除引用 source 的图层，再移除不用的 source；只在绘制批次改变时重建图层。
            layerIds.asReversed().forEach { current.removeLayer(it) }
            layerIds.clear()
            (sources.keys - batches.keys).forEach { key ->
                current.removeSource(key.id); sources.remove(key); values.remove(key)
            }
        }
        ordered.forEach { key ->
            val shapes = batches.getValue(key)
            if (values[key] != shapes) {
                val features = shapes.mapIndexedNotNull { index, shape ->
                    val coordinates = when(key.stage) {
                        0->polygonGeometry(shape.points)
                        8->shape.points.firstOrNull()?.takeIf(::valid)?.let {Point.fromLngLat(it.lon,it.lat)}
                        else->lineGeometry(shape.points)
                    }
                    coordinates?.let { Feature.fromGeometry(it).apply {
                        addStringProperty("color", "rgba(${Color.red(shape.color)},${Color.green(shape.color)},${Color.blue(shape.color)},${Color.alpha(shape.color) / 255.0})")
                        addNumberProperty("order", index)
                    } }
                }
                val data = FeatureCollection.fromFeatures(features)
                val source = sources[key]
                if (source != null) source.setGeoJson(data)
                else {
                    // 虚线使用宽度单位，lineMetrics 必须关闭，避免 SDK 对 dash 比例的特殊处理。
                    val added = GeoJsonSource(key.id, data, GeoJsonOptions().withLineMetrics(false))
                    current.addSource(added); sources[key] = added
                }
                values[key] = shapes.toList()
            }
        }
        if (topologyChanged) {
            val annotationLayer = current.layers.firstOrNull { it.id == "org.maplibre.annotations.points" }?.id
            ordered.forEach { key ->
                val color = Expression.toColor(Expression.get("color"))
                val layer: Layer = if (key.stage == 0) FillLayer(key.id, key.id).withProperties(
                    PropertyFactory.fillColor(color), PropertyFactory.fillOutlineColor(Color.TRANSPARENT),
                    PropertyFactory.fillSortKey(Expression.get("order")),
                ) else if(key.stage==8) CircleLayer(key.id,key.id).withProperties(
                    PropertyFactory.circleColor(color),PropertyFactory.circleRadius(key.width),
                    PropertyFactory.circleStrokeColor(Color.WHITE),PropertyFactory.circleStrokeWidth(1f),
                ) else LineLayer(key.id, key.id).withProperties(
                    PropertyFactory.lineColor(color), PropertyFactory.lineWidth(key.width),
                    PropertyFactory.lineSortKey(Expression.get("order")),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(if (key.dashed) Property.LINE_CAP_BUTT else Property.LINE_CAP_ROUND),
                ).also { line ->
                    // Google 为 9dp 实线 + 6dp 间隔；MapLibre 的数值单位是各层自己的线宽。
                    // 白色描边和细色线分别换算，保证同一条虚线的空隙重合。
                    if (key.dashed) line.setProperties(PropertyFactory.lineDasharray(arrayOf(9f / key.width.coerceAtLeast(.1f), 6f / key.width.coerceAtLeast(.1f))))
                }
                if (annotationLayer != null) current.addLayerBelow(layer, annotationLayer) else current.addLayer(layer)
                layerIds += key.id
            }
            val reordered = ordered.associateWith { sources.getValue(it) }
            sources.clear(); sources.putAll(reordered)
        }
        previous = geometry
    }

    private fun valid(point: GeoPoint) = point.lat.isFinite() && point.lon.isFinite() && point.lat in -90.0..90.0 && point.lon in -180.0..180.0
    private fun mercator(lat: Double) = ln(tan(PI / 4 + Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878)) / 2))
    private fun latitude(y: Double) = Math.toDegrees(2 * atan(exp(y)) - PI / 2)
    private fun crossing(a: GeoPoint, b: GeoPoint, lon: Double): GeoPoint {
        val fraction = (lon - a.lon) / (b.lon - a.lon)
        return GeoPoint(latitude(mercator(a.lat) + fraction * (mercator(b.lat) - mercator(a.lat))), lon)
    }
    private fun point(value: GeoPoint) = Point.fromLngLat(value.lon, value.lat)

    /** 在 ±180° 边界断开原生地理折线，避免 179° → -179° 被画成横跨全世界的线。 */
    private fun lineGeometry(points: List<GeoPoint>): Geometry? {
        val segments = mutableListOf<List<Point>>()
        var segment = mutableListOf<GeoPoint>()
        fun flush() { if (segment.size >= 2) segments += segment.map(::point); segment = mutableListOf() }
        points.forEach { next ->
            if (!valid(next)) { flush(); return@forEach }
            val last = segment.lastOrNull()
            if (last != null && kotlin.math.abs(next.lon - last.lon) > 180) {
                val adjusted = next.copy(lon = next.lon + if (next.lon < last.lon) 360 else -360)
                if (adjusted.lon == last.lon) {
                    // +180 与 -180 是同一条经线，不做分母为零的交点插值。
                    flush(); segment += last.copy(lon = next.lon)
                } else {
                    val seam = if (adjusted.lon > last.lon) 180.0 else -180.0
                    val cut = crossing(last, adjusted, seam)
                    segment += cut; flush(); segment += cut.copy(lon = -seam)
                }
            }
            segment += next
        }
        flush()
        return segments.takeIf { it.isNotEmpty() }?.let(MultiLineString::fromLngLats)
    }

    /** 覆盖面同样按日期变更线裁开；只裁地理几何，不引入屏幕坐标或缩放重投影。 */
    private fun polygonGeometry(points: List<GeoPoint>): Geometry? {
        if (points.size < 3 || points.any { !valid(it) }) return null
        val ring = mutableListOf(points.first())
        points.drop(1).forEach { p ->
            var lon = p.lon
            val previous = ring.last().lon
            while (lon - previous > 180) lon -= 360
            while (lon - previous < -180) lon += 360
            ring += p.copy(lon = lon)
        }
        if (ring.first() == ring.last()) ring.removeAt(ring.lastIndex)
        if (ring.size < 3) return null
        fun clip(input: List<GeoPoint>, boundary: Double, keepEast: Boolean): List<GeoPoint> {
            if (input.isEmpty()) return emptyList()
            val out = mutableListOf<GeoPoint>()
            var a = input.last()
            var aInside = if (keepEast) a.lon >= boundary else a.lon <= boundary
            input.forEach { b ->
                val bInside = if (keepEast) b.lon >= boundary else b.lon <= boundary
                if (aInside != bInside) out += crossing(a, b, boundary)
                if (bInside) out += b
                a = b; aInside = bInside
            }
            return out
        }
        val firstWorld = floor((ring.minOf { it.lon } + 180) / 360).toInt()
        val lastWorld = floor((ring.maxOf { it.lon } + 180) / 360).toInt()
        val polygons = (firstWorld..lastWorld).mapNotNull { world ->
            val clipped = clip(clip(ring, -180.0 + world * 360.0, true), 180.0 + world * 360.0, false)
                .distinct().map { point(it.copy(lon = it.lon - world * 360.0)) }
            if (clipped.size < 3) null else listOf(clipped + clipped.first())
        }
        return polygons.takeIf { it.isNotEmpty() }?.let(MultiPolygon::fromLngLats)
    }
}

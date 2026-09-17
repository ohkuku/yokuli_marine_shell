package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.android.gms.maps.model.MarkerOptions as GoogleMarkerOptions
import com.google.android.gms.maps.model.PolygonOptions as GooglePolygonOptions
import com.google.android.gms.maps.model.PolylineOptions as GooglePolylineOptions
import com.yokuli.marine.shell.rebuild.GeoPoint
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * 地理内容完全交给原生地图渲染。相机缩放时不经过 Compose 重组或第二层 Canvas 投影，
 * 因此航线、图钉、测距和海图始终处于同一帧、同一地理坐标系。
 * 屏幕 Canvas 只保留准星与比例尺；业务状态不从渲染对象反向推断。
 */
internal class NativeSceneRenderer(private val context: Context) {
    private var previous: MapScene? = null
    private var previousRuler: List<GeoPoint> = emptyList()
    private data class Group(val value:Any,val remove:List<()->Unit>)
    private val groups=mutableMapOf<String,Group>()
    private var reset=false
    private val density = context.resources.displayMetrics.density
    private val icons = android.util.LruCache<String, Bitmap>(180)

    fun invalidate() { previous = null;reset=true }

    fun render(google: GoogleMap?, libre: MapLibreMap?, scene: MapScene, ruler: List<GeoPoint>) {
        if (google == null && libre == null) return
        if (scene == previous && ruler == previousRuler) return
        previous = scene; previousRuler = ruler.toList()
        if(reset){groups.values.forEach {group->group.remove.forEach {it()}};groups.clear();reset=false}
        val keys=mutableSetOf<String>()
        var removals=mutableListOf<()->Unit>()
        fun item(key:String,value:Any,draw:()->Unit) {
            keys+=key
            val existing=groups[key]
            if(existing?.value==value)return
            existing?.remove?.forEach {it()}
            removals=mutableListOf()
            draw()
            groups[key]=Group(value,removals.toList())
        }
        fun polygon(points: List<GeoPoint>, fill: Int, stroke: Int = Color.TRANSPARENT, width: Float = 0f) {
            if (points.size < 3) return
            google?.addPolygon(GooglePolygonOptions().addAll(points.map { GoogleLatLng(it.lat, it.lon) })
                .fillColor(fill).strokeColor(stroke).strokeWidth(width * density).geodesic(true).zIndex(1f))?.let { removals.add(it::remove) }
            libre?.addPolygon(PolygonOptions().addAll(points.map { LatLng(it.lat, it.lon) }).fillColor(fill).strokeColor(stroke))?.let {annotation->removals.add {libre?.removeAnnotation(annotation)}}
        }
        fun line(points: List<GeoPoint>, color: Int, width: Float, dashed: Boolean) {
            if (points.size < 2) return
            val option = GooglePolylineOptions().addAll(points.map { GoogleLatLng(it.lat, it.lon) }).color(color)
                .width(width * density).geodesic(true).zIndex(3f)
            if (dashed) option.pattern(listOf(com.google.android.gms.maps.model.Dash(9 * density), com.google.android.gms.maps.model.Gap(6 * density)))
            google?.addPolyline(option)?.let { removals.add(it::remove) }
            libre?.addPolyline(PolylineOptions().addAll(points.map { LatLng(it.lat, it.lon) }).color(color).width(width))?.let {annotation->removals.add {libre?.removeAnnotation(annotation)}}
        }
        scene.areas.forEach {area->item("area:${area.id}",area) {polygon(area.boundary,area.color.toInt())}}
        scene.circles.filter { it.radiusMeters.isFinite() && it.radiusMeters > 0 }.forEach { circle ->
            item("circle:${circle.id}",circle) {
                val ring = (0..72).map { destination(circle.center, circle.radiusMeters, it * 5.0) }
                polygon(ring, (circle.color.toInt() and 0xFFFFFF) or 0x10000000)
                line(ring, circle.color.toInt(), 1.6f, circle.dashed)
            }
        }
        scene.lines.forEach { path ->item("line:${path.id}",path) {
            if (Color.alpha(path.color.toInt()) == 255) line(path.points, 0xBBFFFFFF.toInt(), path.widthDp + 1.5f, path.dashed)
            line(path.points, path.color.toInt(), path.widthDp, path.dashed)
        }}
        val points = scene.points.toMutableList()
        if (ruler.size == 2) {
            item("ruler:line",ruler) {
                line(ruler, Color.WHITE, 4.5f, false)
                line(ruler, 0xFFD74A29.toInt(), 2.5f, true)
            }
            ruler.forEachIndexed { index, point -> points.add(MapPoint("ruler:$index", point, if(index == 0) "A" else "B", 0xFFD74A29, 17f, true)) }
        }
        fun marker(point: GeoPoint, id: String, bitmap: Bitmap) {
            google?.addMarker(GoogleMarkerOptions().position(GoogleLatLng(point.lat, point.lon)).anchor(.5f, .5f)
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)).zIndex(6f))?.let { it.tag = id; removals.add(it::remove) }
            libre?.addMarker(MarkerOptions().position(LatLng(point.lat, point.lon)).title(id)
                .icon(IconFactory.getInstance(context).fromBitmap(bitmap)))?.let {annotation->removals.add {libre?.removeAnnotation(annotation)}}
        }
        points.forEach {point->item("point:${point.id}",point) {marker(point.point,point.id,pointIcon(point))}}
        scene.vessel?.let {vessel->item("vessel",vessel) {marker(vessel.point,"vessel",vesselIcon(vessel))}}
        (groups.keys-keys).forEach {key->groups.remove(key)?.remove?.forEach {it()}}
    }

    private fun pointIcon(point: MapPoint): Bitmap {
        // MapLibre centres the bitmap; transparent margins remain symmetrical at every zoom.
        val label = point.label.takeIf { it.length <= 3 }.orEmpty()
        val key = "${point.color}:${point.radiusDp}:$label"
        icons.get(key)?.let { return it }
        val radius = point.radiusDp * density
        val size = ((radius + 3 * density) * 2).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val center = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE; canvas.drawCircle(center, center, radius + 2 * density, paint)
        paint.color = point.color.toInt(); canvas.drawCircle(center, center, radius, paint)
        if (label.isNotEmpty()) {
            paint.color = Color.WHITE; paint.textSize = 13 * density; paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText(label, center, center - (paint.ascent() + paint.descent()) / 2, paint)
        }
        icons.put(key, bitmap); return bitmap
    }

    private fun vesselIcon(vessel: MapVessel): Bitmap {
        val heading = vessel.courseDegrees?.toFloat() ?: 0f
        val key = "vessel:${vessel.fresh}:${heading.toInt()}"
        icons.get(key)?.let { return it }
        val size = (40 * density).toInt(); val center = size / 2f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(center, center, 12 * density, paint)
        paint.color = if (vessel.fresh) 0xFF007ADC.toInt() else Color.GRAY
        if (vessel.fresh && vessel.courseDegrees != null) {
            canvas.rotate(heading, center, center)
            val shape = Path().apply {
                moveTo(center, center - 17 * density); lineTo(center - 9 * density, center + 11 * density)
                lineTo(center, center + 6 * density); lineTo(center + 9 * density, center + 11 * density); close()
            }
            canvas.drawPath(shape, paint)
        } else {
            paint.style = if (vessel.fresh) Paint.Style.FILL else Paint.Style.STROKE; paint.strokeWidth = 3 * density
            canvas.drawCircle(center, center, 9 * density, paint)
        }
        icons.put(key, bitmap); return bitmap
    }
}

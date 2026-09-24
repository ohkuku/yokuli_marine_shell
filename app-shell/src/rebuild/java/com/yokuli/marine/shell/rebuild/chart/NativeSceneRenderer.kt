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
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * 地理内容完全交给原生地图渲染。相机缩放时不经过 Compose 重组或第二层 Canvas 投影，
 * 因此航线、图钉、测距和海图始终处于同一帧、同一地理坐标系。
 * 屏幕 Canvas 只保留准星与比例尺；业务状态不从渲染对象反向推断。
 */
internal class NativeSceneRenderer(private val context: Context) {
    private val libreGeometry = LibreSceneGeometry()
    private var previous: MapScene? = null
    private var previousInput:MapScene?=null
    private var previousRuler: List<GeoPoint> = emptyList()
    private data class Group(val value:Any,val remove:List<()->Unit>)
    private val groups=mutableMapOf<String,Group>()
    /** 图钉身份与报文元数据分开；移动/改色更新现有原生对象，不删除后重建。 */
    private data class MarkerGroup(
        var point: GeoPoint, var iconKey: String,
        val move: (GeoPoint) -> Unit, val icon: (Bitmap) -> Unit, val remove: () -> Unit,
    )
    private val markers = mutableMapOf<String, MarkerGroup>()
    private fun clearMarkers() { markers.values.forEach { it.remove() }; markers.clear() }
    private var reset=false
    private val density = context.resources.displayMetrics.density
    // 按实际像素字节限制，而不是按图标数量；高密度屏幕长期转向也不会无限积累位图。
    private val icons = object:android.util.LruCache<String,Bitmap>(4*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }

    fun invalidate() { previous = null;previousInput=null;reset=true }

    /** 必须在 setStyle / MapView.onDestroy 之前释放当前 style 的自有资源。 */
    fun clear() {
        groups.values.forEach { group -> group.remove.forEach { it() } }
        groups.clear()
        clearMarkers()
        libreGeometry.clear()
        previous = null
        previousInput=null
        previousRuler = emptyList()
        reset = false
    }

    fun render(google: GoogleMap?, libre: MapLibreMap?, input: MapScene, ruler: List<GeoPoint>): Boolean {
        if (google == null && libre == null) return false
        // style 还在加载时不记录 previous，否则下一帧会误判为已经绘制。
        if (libre != null && libre.style?.isFullyLoaded != true) return false
        // 原生几何随地图相机自行投影；拖动/缩放而业务未变时无需重新生成 AIS 线段与 GeoJSON。
        if(!reset&&input==previousInput&&ruler==previousRuler)return true
        previousInput=input
        val scene=input.trafficGeometry()
        libre?.let { libreGeometry.render(it, scene, ruler) }
        if (scene == previous && ruler == previousRuler) return true
        previous = scene; previousRuler = ruler.toList()
        if(reset){groups.values.forEach {group->group.remove.forEach {it()}};groups.clear();clearMarkers();reset=false}
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
            val map = google ?: return
            if (points.size < 3) return
            map.addPolygon(GooglePolygonOptions().addAll(points.map { GoogleLatLng(it.lat, it.lon) })
                .fillColor(fill).strokeColor(stroke).strokeWidth(width * density).geodesic(true).zIndex(1f))?.let { removals.add(it::remove) }
        }
        fun line(points: List<GeoPoint>, color: Int, width: Float, dashed: Boolean) {
            val map = google ?: return
            if (points.size < 2) return
            val option = GooglePolylineOptions().addAll(points.map { GoogleLatLng(it.lat, it.lon) }).color(color)
                .width(width * density).geodesic(true).zIndex(3f)
            if (dashed) option.pattern(listOf(com.google.android.gms.maps.model.Dash(9 * density), com.google.android.gms.maps.model.Gap(6 * density)))
            map.addPolyline(option).let { removals.add(it::remove) }
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
        val markerKeys = mutableSetOf<String>()
        fun marker(point: GeoPoint, id: String, iconKey: String, bitmap: () -> Bitmap) {
            if (!point.valid()) return
            markerKeys += id
            val existing = markers[id]
            if (existing != null) {
                if (existing.point != point) { existing.move(point); existing.point = point }
                if (existing.iconKey != iconKey) { existing.icon(bitmap()); existing.iconKey = iconKey }
                return
            }
            val image = bitmap()
            val googleMarker = google?.addMarker(GoogleMarkerOptions().position(GoogleLatLng(point.lat, point.lon)).anchor(.5f, .5f)
                .icon(BitmapDescriptorFactory.fromBitmap(image)).zIndex(6f))?.also { it.tag = id }
            val libreMarker = libre?.addMarker(MarkerOptions().position(LatLng(point.lat, point.lon)).title(id)
                .icon(IconFactory.getInstance(context).fromBitmap(image)))
            if (googleMarker == null && libreMarker == null) return
            markers[id] = MarkerGroup(point, iconKey,
                move = { next -> googleMarker?.position = GoogleLatLng(next.lat, next.lon); libreMarker?.position = LatLng(next.lat, next.lon) },
                icon = { next -> googleMarker?.setIcon(BitmapDescriptorFactory.fromBitmap(next)); libreMarker?.icon = IconFactory.getInstance(context).fromBitmap(next) },
                remove = { googleMarker?.remove(); if (libreMarker != null) libre?.removeAnnotation(libreMarker) },
            )
        }
        points.forEach { point -> marker(point.point, point.id, pointIconKey(point)) { pointIcon(point) } }
        scene.aisTargets.forEach { target -> marker(target.point, "ais:${target.mmsi}", aisIconKey(target)) { aisIcon(target) } }
        scene.vessel?.let {vessel ->
            vesselCourseVector(vessel)?.let {vector -> item("vessel:course",vector) {
                line(vector, Color.WHITE, 3.5f, true)
                line(vector, 0xFF007ADC.toInt(), 1.8f, true)
            }}
            marker(vessel.point,"vessel",vesselIconKey(vessel)) { vesselIcon(vessel) }
        }
        (groups.keys-keys).forEach {key->groups.remove(key)?.remove?.forEach {it()}}
        (markers.keys-markerKeys).forEach { key -> markers.remove(key)?.remove?.invoke() }
        return true
    }

    private fun pointIconKey(point: MapPoint) = "point:${point.color}:${point.radiusDp}:${point.label.takeIf { it.length <= 3 }.orEmpty()}"
    private fun aisIconKey(target: MapAisTarget): String {
        val angle = target.heading?.takeIf { it.isFinite() && it in 0.0..360.0 }?.toInt()
        return "ais:${target.kind}:$angle:${target.stale}:${target.lost}:${target.risk}:${target.selected}:${target.distress}"
    }
    private fun vesselIconKey(vessel: MapVessel) = "vessel:${vessel.fresh}:${vessel.headingDegrees?.takeIf { it.isFinite() && it in 0.0..360.0 }?.toInt()}"

    private fun pointIcon(point: MapPoint): Bitmap {
        // MapLibre centres the bitmap; transparent margins remain symmetrical at every zoom.
        val label = point.label.takeIf { it.length <= 3 }.orEmpty()
        val key = pointIconKey(point)
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

    private fun aisIcon(target:MapAisTarget):Bitmap {
        val angle=target.heading?.takeIf {it.isFinite()&&it in 0.0..360.0}?.toInt()
        val key=aisIconKey(target)
        icons.get(key)?.let{return it}
        val size=(48*density).toInt();val center=size/2f
        val image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image)
        val color=when {target.risk||target.distress=="ACTIVE"->0xFFD74A29.toInt();target.selected->0xFFAC4DDD.toInt();target.stale->Color.GRAY;else->0xFF168B76.toInt()}
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color;strokeWidth=2*density;strokeJoin=Paint.Join.ROUND}
        if(target.selected){paint.style=Paint.Style.STROKE;canvas.drawCircle(center,center,19*density,paint)}
        canvas.save()
        val shape=Path()
        when {
            target.kind in setOf("VESSEL_A","VESSEL_B","VESSEL","CLASS_A","CLASS_B") && angle!=null -> {
                canvas.rotate(angle.toFloat(),center,center)
                shape.moveTo(center,center-12*density);shape.lineTo(center-6*density,center+9*density)
                shape.lineTo(center+6*density,center+9*density);shape.close()
            }
            target.kind.contains("ATON") || target.kind.contains("AID") -> {
                shape.moveTo(center,center-10*density);shape.lineTo(center-8*density,center)
                shape.lineTo(center,center+10*density);shape.lineTo(center+8*density,center);shape.close()
            }
            target.kind in setOf("SART","MOB","EPIRB") -> {
                shape.addCircle(center,center,9*density,Path.Direction.CW)
                shape.moveTo(center-12*density,center);shape.lineTo(center+12*density,center)
                shape.moveTo(center,center-12*density);shape.lineTo(center,center+12*density)
            }
            target.kind.contains("BASE") -> shape.addRect(center-7*density,center-7*density,center+7*density,center+7*density,Path.Direction.CW)
            target.kind.contains("SAR") || target.kind.contains("AIRCRAFT") -> {
                shape.moveTo(center-10*density,center);shape.lineTo(center+10*density,center)
                shape.moveTo(center,center-10*density);shape.lineTo(center,center+10*density)
            }
            else -> shape.addCircle(center,center,7*density,Path.Direction.CW)
        }
        paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=4*density;canvas.drawPath(shape,paint)
        paint.color=color;paint.strokeWidth=2*density;paint.style=if(target.stale||target.kind=="VIRTUAL_AID"||target.distress in setOf("TEST","INACTIVE","UNKNOWN"))Paint.Style.STROKE else Paint.Style.FILL_AND_STROKE;canvas.drawPath(shape,paint)
        canvas.restore()
        if(target.lost){paint.color=color;paint.style=Paint.Style.STROKE;canvas.drawLine(center-12*density,center+12*density,center+12*density,center-12*density,paint)}
        icons.put(key,image);return image
    }

    private fun vesselIcon(vessel: MapVessel): Bitmap {
        val heading = vessel.headingDegrees?.takeIf { it.isFinite() && it in 0.0..360.0 }?.toFloat()
        val key = vesselIconKey(vessel)
        icons.get(key)?.let { return it }
        val size = (30 * density).toInt(); val center = size / 2f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val fill = if (vessel.fresh) 0xFF007ADC.toInt() else Color.GRAY
        if (vessel.fresh && heading != null) {
            canvas.rotate(heading, center, center)
            val shape = Path().apply {
                moveTo(center, center - 12 * density)
                lineTo(center - 6 * density, center - 3 * density)
                lineTo(center - 5 * density, center + 10 * density)
                lineTo(center + 5 * density, center + 10 * density)
                lineTo(center + 6 * density, center - 3 * density); close()
            }
            paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;paint.strokeJoin=Paint.Join.ROUND
            canvas.drawPath(shape, paint)
            paint.style=Paint.Style.FILL;paint.color=fill;canvas.drawPath(shape,paint)
            paint.color=Color.WHITE;paint.strokeWidth=1.2f*density
            canvas.drawLine(center,center-6*density,center,center+5*density,paint)
        } else {
            canvas.drawCircle(center, center, 6.5f * density, paint)
            paint.color=fill
            paint.style = if (vessel.fresh) Paint.Style.FILL else Paint.Style.STROKE; paint.strokeWidth = 3 * density
            canvas.drawCircle(center, center, 4.5f * density, paint)
        }
        icons.put(key, bitmap); return bitmap
    }
}

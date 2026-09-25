package com.yokuli.marine.shell.rebuild.chart

import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.android.awaitFrame
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

/** 有界测深标签合并为一个 GeoJSON/SymbolLayer；引擎负责地图投影与避让，不创建逐点 annotation。 */
internal class NativeSoundingLayer(private val scope:CoroutineScope,private val failed:(Boolean)->Unit) {
    private var style:Style?=null
    private var source:GeoJsonSource?=null
    private var previous:List<MapPoint>?=null
    private val images=linkedMapOf<String,String>()
    private var serial=0L
    private var generation=0L
    private var job:Job?=null
    fun clear() {
        generation++;job?.cancel();job=null
        style?.takeIf {it.isFullyLoaded}?.let {old->
            old.removeLayer(LAYER);old.removeSource(SOURCE);images.values.forEach(old::removeImage)
        }
        style=null;source=null;previous=null;images.clear()
    }
    fun render(map:MapLibreMap,points:List<MapPoint>,image:(MapPoint)->Bitmap) {
        val current=map.style?.takeIf {it.isFullyLoaded} ?: return
        if(current!==style) {clear();style=current}
        if(previous==points)return
        previous=points.toList();job?.cancel()
        val version=++generation
        fun isCurrent()=generation==version&&style===current&&map.style===current&&current.isFullyLoaded
        job=scope.launch {
            try {
                val wanted=points.associateBy {"${it.label}/${it.color}"}
                val missing=wanted.filterKeys {it !in images}
                // 系统字体本地栅格化在后台进行；不依赖联网 glyph endpoint。
                val prepared=withContext(Dispatchers.Default) {missing.map {(key,point)->ensureActive();key to image(point)}}
                if(!isCurrent())return@launch
                (images.keys-wanted.keys).forEach {key->images.remove(key)?.let(current::removeImage)}
                var cursor=0
                while(cursor<prepared.size) {
                    awaitFrame()
                    if(!isCurrent())return@launch
                    val batch=linkedMapOf<String,Bitmap>();val keys=linkedMapOf<String,String>();var bytes=0
                    while(cursor<prepared.size&&batch.size<6) {
                        val (key,bitmap)=prepared[cursor]
                        if(batch.isNotEmpty()&&bytes+bitmap.allocationByteCount>256*1024)break
                        val sprite="yokuli-depth-${++serial}";batch[sprite]=bitmap;keys[key]=sprite;bytes+=bitmap.allocationByteCount;cursor++
                    }
                    current.addImages(batch);images.putAll(keys)
                }
                if(!isCurrent())return@launch
                val names=images.toMap()
                val data=withContext(Dispatchers.Default) {FeatureCollection.fromFeatures(points.mapIndexed {index,p->
                    Feature.fromGeometry(Point.fromLngLat(p.point.lon,p.point.lat)).apply {
                        addStringProperty("sprite",names.getValue("${p.label}/${p.color}"));addNumberProperty("priority",index)
                    }
                })}
                if(!isCurrent())return@launch
                if(source==null) {
                    source=GeoJsonSource(SOURCE,data).also {current.addSource(it)}
                    val layer=SymbolLayer(LAYER,SOURCE).withProperties(
                        PropertyFactory.iconImage(Expression.get("sprite")),
                        PropertyFactory.iconAllowOverlap(false),PropertyFactory.iconIgnorePlacement(false),
                        PropertyFactory.iconPadding(3f),PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                        PropertyFactory.symbolSortKey(Expression.get("priority")),
                    )
                    if(current.getLayer("org.maplibre.annotations.points")!=null)current.addLayerBelow(layer,"org.maplibre.annotations.points")else current.addLayer(layer)
                }else source?.setGeoJson(data)
                failed(false)
            }catch(cancel:CancellationException) {throw cancel}
            catch(_:Exception) {if(isCurrent()){source?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()));previous=null;failed(true)}}
        }
    }
    private companion object {const val SOURCE="yokuli-native-depth-source";const val LAYER="yokuli-native-depth-labels"}
}

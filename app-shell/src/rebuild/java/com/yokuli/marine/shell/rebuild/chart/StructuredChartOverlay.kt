package com.yokuli.marine.shell.rebuild.chart

import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.marine.core.design.MarineUnitFormats
import com.yokuli.shell.contract.MarineUnitPreferences
import com.yokuli.runtime.marine.chart.ChartDrawingClipper
import kotlinx.coroutines.*
import kotlin.math.*

internal data class StructuredChartViewport(val features:List<NauticalFeature> = emptyList(),val scene:MapScene=MapScene(),val issue:String?=null)

/** 原生地图只获取当前视口的有界绘制集；全航段分析使用独立快照查询，不复用屏幕裁剪结果。 */
@Composable internal fun rememberStructuredChart(maps:MapSessionStore,view:MapViewState):StructuredChartViewport {
    val data by maps.charts.state.collectAsState()
    val datasets=maps.selectedDatasetIds
    var result by remember(maps,view,datasets,data.revision,maps.unitPreferences){mutableStateOf(StructuredChartViewport())}
    LaunchedEffect(datasets,data.revision) {
        view.selectedChartObjects=view.selectedChartObjects.filter {feature->
            feature.datasetId in datasets&&data.datasets.any {dataset->dataset.id==feature.datasetId&&dataset.offlineReadable&&dataset.cells.any {cell->cell.cellId==feature.cellId&&!cell.cancelled&&cell.edition==feature.source.edition&&cell.update==feature.source.update}}
        }
        if(view.selectedChartObjects.isEmpty())view.selectedChartCoordinate=null
    }
    LaunchedEffect(datasets,data.revision,view.center,view.zoom,view.interactive,maps.unitPreferences,maps.chinese) {
        if(!view.interactive)return@LaunchedEffect
        if(datasets.isEmpty()){result=StructuredChartViewport();return@LaunchedEffect}
        delay(180)
        val center=view.center;val span=(720.0/2.0.pow(view.zoom)).coerceIn(.005,180.0)
        val latSpan=span*cos(Math.toRadians(center.lat)).coerceAtLeast(.05)
        fun norm(v:Double)=((v+540)%360)-180
        val bounds=ChartBounds(if(span>=180)-180.0 else norm(center.lon-span),(center.lat-latSpan).coerceAtLeast(-90.0),if(span>=180)180.0 else norm(center.lon+span),(center.lat+latSpan).coerceAtMost(90.0))
        var lease:ChartDataSnapshot?=null
        try {
            lease=maps.charts.acquireSnapshot(datasets)
            val page=maps.charts.query(lease.id,bounds,4000)
            val snapshot=lease
            result=withContext(Dispatchers.Default){
                val drawing=ChartDrawingClipper.compose(snapshot,page.features,bounds)
                val rendered=structuredScene(drawing.features,center,view.zoom,maps.unitPreferences)
                StructuredChartViewport(drawing.features,rendered.scene,when {snapshot.missingDatasetIds.isNotEmpty()->"所选数据已移除 / Selected data is missing";page.hasMore||page.truncated->"放大以显示更多对象 / Zoom in for more objects";drawing.incompleteGeometry->"部分覆盖或对象未能完整显示 / Some coverage or objects could not be shown";rendered.soundingsSimplified->"测深点已简化 · 放大或点按查询 / Depth labels simplified · Zoom in or tap to inspect";else->null})
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){result=StructuredChartViewport(issue="海图数据未能载入 / Chart data could not load")}
        finally{lease?.let{withContext(NonCancellable){maps.charts.releaseSnapshot(it.id)}}}
    }
    return result
}
private fun ChartPoint.geo()=GeoPoint(latitude,longitude)
internal data class StructuredSceneDrawing(val scene:MapScene,val soundingsSimplified:Boolean)
internal suspend fun structuredScene(features:List<NauticalFeature>,center:GeoPoint,zoom:Double,preferences:MarineUnitPreferences):StructuredSceneDrawing {
    val soundingSelection=selectSoundingLabels(features,center,zoom,MarineUnitFormats(preferences))
    val areas=mutableListOf<MapArea>();val lines=mutableListOf<MapLine>();val points=mutableListOf<MapPoint>()
    features.forEach { f ->
        currentCoroutineContext().ensureActive()
        val color=when(f.kind){NauticalFeatureKind.LAND->0xFFE1D4AC;NauticalFeatureKind.DRYING_AREA->0x997EAB86;NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->when{(f.depth?.lowerMeters?:1000.0)<5->0x5572CCD9;(f.depth?.lowerMeters?:1000.0)<20->0x3372CCD9;else->0x1163B0D0};NauticalFeatureKind.RESTRICTED->0x668C448F;else->0x229A737F}
        val parts=f.geometry.parts
        when(f.geometry.kind){
            ChartGeometryKind.POLYGON->{
                if(f.kind !in setOf(NauticalFeatureKind.COVERAGE,NauticalFeatureKind.QUALITY,NauticalFeatureKind.OTHER))parts.filterNot{it.hole}.forEachIndexed{i,outer->
                    val holes=parts.filter{it.hole&&it.points.firstOrNull()?.let{p->containsRing(outer.points,p)}==true}.map{it.points.map(ChartPoint::geo)}
                    areas.add(MapArea("enc:${f.id}:$i",outer.points.map(ChartPoint::geo),color,holes))
                }
            }
            ChartGeometryKind.LINE->if(f.kind !in setOf(NauticalFeatureKind.OTHER,NauticalFeatureKind.COVERAGE))parts.forEachIndexed{i,part->lines.add(MapLine("enc:${f.id}:$i",part.points.map(ChartPoint::geo),if(f.kind==NauticalFeatureKind.DEPTH_CONTOUR)0x7773979F else 0xFF9C519A,1f,true))}
            ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT->if(f.kind in setOf(NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.BEACON,NauticalFeatureKind.LIGHT))parts.flatMap{it.points}.forEachIndexed{i,p->
                points.add(MapPoint("enc:${f.id}:$i",p.geo(),f.attributes["OBJNAM"].orEmpty(),if(f.kind in setOf(NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION))0xFFAB3645 else 0xFF315A66,4.5f))
            }
            else->Unit
        }
    }
    return StructuredSceneDrawing(MapScene(points=points+soundingSelection.points,lines=lines,areas=areas),soundingSelection.simplified)
}

private data class SoundingLabelSelection(val points:List<MapPoint>,val simplified:Boolean)
/** 绘制抽稀不改 features。稳定世界网格每格保留实际最浅点，并限制一次原生绘制的标签数量。 */
private suspend fun selectSoundingLabels(features:List<NauticalFeature>,center:GeoPoint,zoom:Double,formats:MarineUnitFormats):SoundingLabelSelection {
    data class Candidate(val featureId:String,val index:Int,val point:ChartPoint,val pixelX:Double,val pixelY:Double)
    val level=(floor(zoom*2)/2).coerceIn(1.0,22.0)
    val world=256.0*2.0.pow(level)
    fun pixelX(lon:Double)=(lon+180.0)/360.0*world
    fun pixelY(lat:Double)=(1.0-asinh(tan(Math.toRadians(lat.coerceIn(-85.05112878,85.05112878))))/PI)/2.0*world
    val centerX=pixelX(center.lon);val centerY=pixelY(center.lat)
    val limit=if(level>=14.0)160 else 80
    // 文本含全局单位：fathom 比 m 宽，因此预留更宽的横向避让空间。
    val gridWidth=if(formats.depthUnit.length>2)104.0 else 76.0
    val candidates=linkedMapOf<Pair<Long,Long>,Candidate>()
    var total=0
    for(feature in features) {
        if(feature.kind!=NauticalFeatureKind.SOUNDING)continue
        var index=0
        for(part in feature.geometry.parts)for(point in part.points) {
            if(index%256==0)currentCoroutineContext().ensureActive()
            val pointIndex=index++;total++
            val depth=point.depthMeters?.takeIf(Double::isFinite) ?: continue
            if(level<10.0)continue
            val x=pixelX(point.longitude);val y=pixelY(point.latitude)
            val key=floor(x/gridWidth).toLong() to floor(y/30.0).toLong()
            val old=candidates[key]
            if(old==null||depth<(old.point.depthMeters ?: Double.POSITIVE_INFINITY))candidates[key]=Candidate(feature.id,pointIndex,point,x,y)
        }
    }
    fun distance(c:Candidate):Double {val dx=abs(c.pixelX-centerX).let {min(it,world-it)};val dy=c.pixelY-centerY;return dx*dx+dy*dy}
    val selected=candidates.values.sortedWith(compareBy<Candidate> {distance(it)}.thenBy {it.featureId}.thenBy {it.index}).take(limit)
    return SoundingLabelSelection(selected.map {c->MapPoint("enc:${c.featureId}:sounding:${c.index}",c.point.geo(),formats.depth(c.point.depthMeters),0xFF204957,2f,style=MapPointStyle.SOUNDING)},selected.size<total)
}
/** 经度相对于点解缠，孔洞不命中；图形查询不会移动相机或准星。 */
internal fun containsRing(ring:List<ChartPoint>,p:ChartPoint):Boolean {
    if(ring.size<3)return false
    fun x(v:Double)=((v-p.longitude+540)%360)-180
    var inside=false;var a=ring.last()
    ring.forEach{b->if((a.latitude>p.latitude)!=(b.latitude>p.latitude)&&0.0<(x(b.longitude)-x(a.longitude))*(p.latitude-a.latitude)/(b.latitude-a.latitude)+x(a.longitude))inside=!inside;a=b}
    return inside
}
internal fun chartObjectsAt(features:List<NauticalFeature>,point:GeoPoint,zoom:Double):List<NauticalFeature> {
    val p=ChartPoint(point.lat,point.lon);val radius=30*156543.0*cos(Math.toRadians(point.lat))/2.0.pow(zoom)
    fun xy(v:ChartPoint)=Pair(((v.longitude-point.lon+540)%360-180)*111_320*cos(Math.toRadians(point.lat)),(v.latitude-point.lat)*111_320)
    fun near(a:ChartPoint,b:ChartPoint):Boolean{val (x,y)=xy(a);val (u,v)=xy(b);val dx=u-x;val dy=v-y;val t=(-(x*dx+y*dy)/(dx*dx+dy*dy).coerceAtLeast(.001)).coerceIn(0.0,1.0);return hypot(x+t*dx,y+t*dy)<=radius}
    return features.asReversed().filter{f->when(f.geometry.kind){
        ChartGeometryKind.POLYGON->f.geometry.parts.any{!it.hole&&containsRing(it.points,p)}&&!f.geometry.parts.any{it.hole&&containsRing(it.points,p)}
        ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT->f.geometry.parts.flatMap{it.points}.any{val(x,y)=xy(it);hypot(x,y)<=radius}
        ChartGeometryKind.LINE->f.geometry.parts.any{it.points.zipWithNext().any{(a,b)->near(a,b)}}
        else->false
    }}.filterNot{it.kind==NauticalFeatureKind.COVERAGE}.distinctBy{it.id}.take(30).map {feature->
        if(feature.kind!=NauticalFeatureKind.SOUNDING)feature else {
            val closest=feature.geometry.parts.flatMap {it.points}.minByOrNull {val(x,y)=xy(it);x*x+y*y}
            if(closest==null)feature else feature.copy(geometry=ChartGeometry(ChartGeometryKind.POINT,listOf(ChartGeometryPart(listOf(closest)))),depth=feature.depth?.copy(pointMeters=closest.depthMeters))
        }
    }
}

package com.yokuli.marine.shell.rebuild.chart

import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.marine.core.design.MarineUnitFormats
import com.yokuli.shell.contract.MarineUnitPreferences
import com.yokuli.runtime.marine.chart.ChartDrawingClipper
import kotlinx.coroutines.*
import kotlin.math.*

internal data class StructuredChartViewport(val features:List<NauticalFeature> = emptyList(),val scene:MapScene=MapScene(),val issue:String?=null,val portrayal:ChartPortrayalInfo=ChartPortrayalInfo(),val notices:List<String> = emptyList())

/** 原生地图只获取当前视口的有界绘制集；全航段分析使用独立快照查询，不复用屏幕裁剪结果。 */
@Composable internal fun rememberStructuredChart(maps:MapSessionStore,view:MapViewState,datasetIds:List<String>?=null):StructuredChartViewport {
    val data by maps.charts.state.collectAsState()
    val datasets=datasetIds ?: maps.selectedDatasetIds
    var result by remember(maps,view,datasets,data.revision){mutableStateOf(StructuredChartViewport())}
    LaunchedEffect(datasets,data.revision) {
        view.selectedChartObjects=view.selectedChartObjects.filter {feature->
            feature.datasetId in datasets&&data.datasets.any {dataset->dataset.id==feature.datasetId&&dataset.offlineReadable&&dataset.cells.any {cell->cell.cellId==feature.cellId&&!cell.cancelled&&cell.edition==feature.source.edition&&cell.update==feature.source.update}}
        }
        if(view.selectedChartObjects.isEmpty()&&view.selectedChartCoordinate?.let{hasRasterAt(data.datasets,datasets,it)}!=true)view.selectedChartCoordinate=null
    }
    LaunchedEffect(datasets,data.revision,view.center,view.zoom,view.interactive,maps.unitPreferences,maps.chinese,maps.portrayalPreferences) {
        if(!view.interactive)return@LaunchedEffect
        if(datasets.isEmpty()||!maps.portrayalPreferences.showDataOverlay){result=StructuredChartViewport();return@LaunchedEffect}
        delay(180)
        val center=view.center;val span=(720.0/2.0.pow(view.zoom)).coerceIn(.005,180.0)
        val latSpan=span*cos(Math.toRadians(center.lat)).coerceAtLeast(.05)
        fun norm(v:Double)=((v+540)%360)-180
        val bounds=ChartBounds(if(span>=180)-180.0 else norm(center.lon-span),(center.lat-latSpan).coerceAtLeast(-90.0),if(span>=180)180.0 else norm(center.lon+span),(center.lat+latSpan).coerceAtMost(90.0))
        var lease:ChartDataSnapshot?=null
        try {
            lease=maps.charts.acquireSnapshot(datasets)
            val features=ArrayList<NauticalFeature>()
            var after:String?=null;var clipped=false
            do {
                val page=maps.charts.query(lease.id,bounds,4_000,after)
                features+=page.features
                clipped=page.truncated||page.hasMore
                after=page.nextAfterId
            }while(clipped&&after!=null&&features.size<12_000)
            val options=maps.portrayalPreferences
            val units=maps.unitPreferences
            val chinese=maps.chinese
            val snapshot=lease
            result=withContext(Dispatchers.Default){
                val drawing=ChartDrawingClipper.compose(snapshot,features,bounds)
                val rendered=structuredScene(drawing.features,center,view.zoom,units,options,drawing.boundaries)
                val notices=buildList {
                    if(snapshot.missingDatasetIds.isNotEmpty())add(if(chinese)"所选数据已移除" else "Selected data is missing")
                    if(clipped)add(if(chinese)"当前范围对象过多 · 放大查看" else "Too many objects · Zoom in")
                    if(drawing.incompleteGeometry)add(if(chinese)"部分覆盖或对象未能完整显示" else "Some coverage or objects could not be shown")
                    if(rendered.info.missingSafetyContour)add(if(chinese)"缺少足够深的安全等深线" else "No sufficiently deep safety contour")
                    if(rendered.info.overscale)add(if(chinese)"超过原图精度" else "Overscale")
                    if(rendered.info.symbolsSimplified)add(if(chinese)"符号已抽稀 · 放大查看" else "Symbols reduced · Zoom in")
                    if(rendered.info.unknownObjects>0)add(if(chinese)"部分对象无专用符号 · 可点按查询" else "Some objects have no dedicated symbol · Tap to inspect")
                    if(rendered.info.safetyContoursMeters.any {it>options.safetyDepthMeters+.001})add((if(chinese)"安全等深线 " else "Safety contour ")+rendered.info.safetyContoursMeters.joinToString(" / "){MarineUnitFormats(units).depth(it)})
                }
                StructuredChartViewport(drawing.features,rendered.scene,notices.firstOrNull(),rendered.info,notices)

            }
        }catch(e:CancellationException){throw e}catch(e:Exception){result=StructuredChartViewport(issue="海图数据未能载入 / Chart data could not load")}
        finally{lease?.let{withContext(NonCancellable){runCatching { kotlinx.coroutines.withTimeout(1500) { maps.charts.releaseSnapshot(it.id) } }}}}
    }
    return result
}
/** 经度相对于点解缠，孔洞不命中；图形查询不会移动相机或准星。 */
internal fun containsRing(ring:List<ChartPoint>,p:ChartPoint):Boolean {
    if(ring.size<3)return false
    fun x(v:Double)=((v-p.longitude+540)%360)-180
    var inside=false;var a=ring.last()
    ring.forEach{b->if((a.latitude>p.latitude)!=(b.latitude>p.latitude)&&0.0<(x(b.longitude)-x(a.longitude))*(p.latitude-a.latitude)/(b.latitude-a.latitude)+x(a.longitude))inside=!inside;a=b}
    return inside
}
internal fun chartObjectsAt(features:List<NauticalFeature>,point:GeoPoint,zoom:Double,radiusMeters:Double?=null,limit:Int=30):List<NauticalFeature> {
    val p=ChartPoint(point.lat,point.lon);val radius=radiusMeters ?: (30*156543.0*cos(Math.toRadians(point.lat))/2.0.pow(zoom))
    fun xy(v:ChartPoint)=Pair(((v.longitude-point.lon+540)%360-180)*111_320*cos(Math.toRadians(point.lat)),(v.latitude-point.lat)*111_320)
    fun near(a:ChartPoint,b:ChartPoint):Boolean{val (x,y)=xy(a);val (u,v)=xy(b);val dx=u-x;val dy=v-y;val t=(-(x*dx+y*dy)/(dx*dx+dy*dy).coerceAtLeast(.001)).coerceIn(0.0,1.0);return hypot(x+t*dx,y+t*dy)<=radius}
    return features.asReversed().filter{f->when(f.geometry.kind){
        ChartGeometryKind.POLYGON->{
            var coverage=0
            for(part in f.geometry.parts)if(containsRing(part.points,p))coverage+=if(part.hole)-1 else 1
            coverage>0
        }
        ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT->f.geometry.parts.flatMap{it.points}.any{val(x,y)=xy(it);hypot(x,y)<=radius}
        ChartGeometryKind.LINE->f.geometry.parts.any{it.points.zipWithNext().any{(a,b)->near(a,b)}}
        else->false
    }}.filterNot{it.kind==NauticalFeatureKind.COVERAGE}.distinctBy{it.id}.take(limit).map {feature->
        if(feature.kind!=NauticalFeatureKind.SOUNDING)feature else {
            val closest=feature.geometry.parts.flatMap {it.points}.minByOrNull {val(x,y)=xy(it);x*x+y*y}
            if(closest==null)feature else feature.copy(geometry=ChartGeometry(ChartGeometryKind.POINT,listOf(ChartGeometryPart(listOf(closest)))),depth=feature.depth?.copy(pointMeters=closest.depthMeters))
        }
    }
}

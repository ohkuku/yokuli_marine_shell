package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.core.design.MarineUnitFormats
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.shell.contract.MarineUnitPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale
import kotlin.math.*

/** 由同一快照推导的显示状态；不冒充航线检查或传感器质量。 */
internal data class ChartPortrayalInfo(
    val scaleDenominator:Int=0,
    val safetyContoursMeters:List<Double> = emptyList(),
    val missingSafetyContour:Boolean=false,
    val overscale:Boolean=false,
    val hiddenByScale:Int=0,
    val unknownObjects:Int=0,
    val symbolsSimplified:Boolean=false,
)
internal data class StructuredSceneDrawing(val scene:MapScene,val soundingsSimplified:Boolean,val info:ChartPortrayalInfo=ChartPortrayalInfo())

/** 日/暮/夜采用同一语义调色板；航标红绿和危险洋红保留业务含义，不跟随主题强调色。 */
internal data class ChartPalette(val land:Long,val drying:Long,val shallow:Long,val unsafe:Long,val medium:Long,val deep:Long,val ink:Long,val faint:Long,val contour:Long,val danger:Long,val red:Long,val green:Long,val yellow:Long,val unknown:Long) {
    companion object {
        fun of(mode:ChartColorMode)=when(mode) {
            ChartColorMode.DAY->ChartPalette(0xFFE5DCBB,0xFFA4B795,0xFF8BC5D7,0xFFB6DCE7,0xFFDDEBF0,0xFFF4F4EC,0xFF202C31,0xFF637980,0xFF588C9A,0xFF973D88,0xFFC23B3D,0xFF287849,0xFFC29923,0xFFD8DCDA)
            ChartColorMode.DUSK->ChartPalette(0xFF645E48,0xFF466354,0xFF244E68,0xFF365E71,0xFF3D4F58,0xFF3C4145,0xFFE0DBBE,0xFFA0ABA7,0xFF87AAB4,0xFFD084B9,0xFFDC7770,0xFF6BA783,0xFFD3B85E,0xFF494B4B)
            ChartColorMode.NIGHT->ChartPalette(0xFF27291F,0xFF233B32,0xFF102B43,0xFF183543,0xFF17272C,0xFF11191D,0xFF9DAEAE,0xFF657D84,0xFF50778B,0xFF96567F,0xFFB75957,0xFF4D8E6E,0xFFAD944F,0xFF222C2F)
        }
    }
}
private fun NauticalFeature.number(key:String)=attributes[key]?.trim()?.toDoubleOrNull()?.takeIf(Double::isFinite)
private fun NauticalFeature.codes(key:String)=attributes[key].orEmpty().split(',', ';', ' ').mapNotNull {it.trim().toIntOrNull()}
private fun NauticalFeature.contour()=depth?.pointMeters?.takeIf(Double::isFinite) ?: number("VALDCO") ?: depth?.lowerMeters?.takeIf(Double::isFinite)
private fun ChartPoint.geo()=GeoPoint(latitude,longitude)
private fun NauticalFeature.anchor():ChartPoint?=geometry.parts.firstOrNull {it.points.isNotEmpty()&&!it.hole}?.points?.let {p->
    // 面标注使用一个真实内部候选点；不把跨日期变更线的平均经度移到另一半球。
    if(geometry.kind==ChartGeometryKind.POLYGON){
        val reference=p.first().longitude
        val lat=p.dropLast(1).map{it.latitude}.average()
        val lon=p.dropLast(1).map{((it.longitude-reference+540)%360)-180}.average()+reference
        ChartPoint(lat,((lon+540)%360)-180).takeIf {containsRing(p,it)} ?: p.first()
    }else p[p.size/2]
}
private fun containsFeature(f:NauticalFeature,p:ChartPoint)=f.geometry.parts.any {!it.hole&&containsRing(it.points,p)}&&!f.geometry.parts.any {it.hole&&containsRing(it.points,p)}

/** 规则式、可取消的有界呈现：只使用原始对象与属性，不从底图颜色推断深度。 */
internal suspend fun structuredScene(
    features:List<NauticalFeature>,center:GeoPoint,zoom:Double,preferences:MarineUnitPreferences,
    portrayal:ChartPortrayalPreferences=ChartPortrayalPreferences(),
    boundaries:Map<String,ChartGeometry> = emptyMap(),
):StructuredSceneDrawing {
    val options=portrayal.normalized();val palette=ChartPalette.of(options.colorMode);val formats=MarineUnitFormats(preferences)
    val metersPerPixel=156543.03392*cos(Math.toRadians(center.lat.coerceIn(-85.0,85.0)))/2.0.pow(zoom)
    // SCAMIN 按标准显示像素 0.28 mm 对应的近似图上比例判断，不把 Android 像素密度当地图缩放。
    val scale=(metersPerPixel/.00028).roundToInt().coerceAtLeast(1)
    val depthAreas=features.filter {it.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&it.geometry.kind==ChartGeometryKind.POLYGON}
    val contours=features.filter {it.kind==NauticalFeatureKind.DEPTH_CONTOUR}.groupBy {it.datasetId to it.cellId}
    val depthCells=depthAreas.map {it.datasetId to it.cellId}.toSet()
    val safetyContours=depthCells.associateWith {cell->contours[cell].orEmpty().mapNotNull {it.contour()}.filter {it>=options.safetyDepthMeters}.minOrNull()}
    val topmarks=features.filter {it.acronym=="TOPMAR"}.mapNotNull {f->f.anchor()?.let {Triple(f.cellId,it,f.codes("TOPSHP").firstOrNull())}}
    val areas=mutableListOf<MapArea>();val lines=mutableListOf<MapLine>();val symbols=mutableListOf<MapPoint>();val labels=mutableListOf<MapPoint>()
    var hidden=0;var unknown=0
    fun tint(color:Long,alpha:Int)=(color and 0xFFFFFFL) or (alpha.toLong() shl 24)
    fun geometryLines(f:NauticalFeature,color:Long,width:Float,dashed:Boolean=false) {(boundaries[f.id] ?: f.geometry).parts.forEachIndexed {i,p->if(p.points.size>1)lines+=MapLine("enc:${f.id}:line:$i",p.points.map(ChartPoint::geo),color,width,dashed,casing=false)}}
    fun geometryAreas(f:NauticalFeature,color:Long) {f.geometry.parts.filterNot {it.hole}.forEachIndexed {i,outer->
        val holes=f.geometry.parts.filter {it.hole&&it.points.firstOrNull()?.let {p->containsRing(outer.points,p)}==true}.map {it.points.map(ChartPoint::geo)}
        areas+=MapArea("enc:${f.id}:area:$i",outer.points.map(ChartPoint::geo),color,holes)
    }}
    fun label(f:NauticalFeature,text:String,priority:Int=60,color:Long=palette.ink,bold:Boolean=false,at:ChartPoint?=null) {
        if(text.isBlank()||labels.size>=750)return
        val point=at ?: f.anchor() ?: return
        labels+=MapPoint("enc:${f.id}:text:${labels.size}",point.geo(),text.take(90),color,0f,style=MapPointStyle.CHART_LABEL,bold=bold,haloColor=palette.deep,priority=priority)
    }
    fun name(f:NauticalFeature,priority:Int=60) {if(options.showNames&&zoom>=11)label(f,f.attributes["NOBJNM"].orEmpty().ifBlank {f.attributes["OBJNAM"].orEmpty()},priority)}
    fun colors(f:NauticalFeature):List<Long> = f.codes("COLOUR").map {when(it){1->palette.ink;2->palette.ink;3->palette.red;4->palette.green;5->palette.yellow;6->palette.yellow;7->palette.faint;8->palette.faint;9->palette.faint;10->palette.faint;11->palette.faint;12->palette.danger;13->palette.danger;else->palette.ink}}
    fun pointSymbol(f:NauticalFeature,kind:ChartSymbolKind,dangerous:Boolean=false,priority:Int=20,color:Long=palette.ink,uncertain:Boolean=false) {
        val locations=if(f.geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))f.geometry.parts.flatMap {it.points} else listOfNotNull(f.anchor())
        for((i,point) in locations.withIndex()) {
            val top=f.codes("TOPSHP").firstOrNull() ?: topmarks.firstOrNull {(cell,p,_)->cell==f.cellId&&abs(p.latitude-point.latitude)<.00002&&abs(((p.longitude-point.longitude+540)%360)-180)<.00002}?.third
            symbols+=MapPoint("enc:${f.id}:symbol:$i",point.geo(),color=color,radiusDp=10f,style=MapPointStyle.CHART_SYMBOL,
                symbol=ChartSymbol(kind,colors(f).getOrNull(1),top,f.codes("CATCAM").firstOrNull(),dangerous,uncertain,palette.deep),priority=priority)
        }
    }
    // 按绘制语义分层；相同类别仍保留 ChartDrawingClipper 已确定的图幅次序。
    fun order(f:NauticalFeature)=when {f.kind==NauticalFeatureKind.COVERAGE->0;f.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)->1;f.kind==NauticalFeatureKind.DRYING_AREA->2;f.kind==NauticalFeatureKind.LAND->3;f.kind==NauticalFeatureKind.DEPTH_CONTOUR->4;else->5}
    for(f in features.sortedBy(::order)) {
        currentCoroutineContext().ensureActive()
        if(f.geometry.kind==ChartGeometryKind.NONE)continue
        if(!options.showNavigationAids&&(f.kind in setOf(NauticalFeatureKind.BEACON,NauticalFeatureKind.LIGHT)||f.acronym in setOf("TOPMAR","ACHARE","ACHBRT","BERTHS","DOCARE")))continue
        val cell=f.datasetId to f.cellId
        val effectiveSafety=safetyContours[cell] ?: options.safetyDepthMeters
        val safetyContour=f.kind==NauticalFeatureKind.DEPTH_CONTOUR&&f.contour()?.let {abs(it-effectiveSafety)<.0001}==true
        val hazardous=f.kind in setOf(NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION)
        val essential=f.kind in setOf(NauticalFeatureKind.LAND,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.COVERAGE,NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD,NauticalFeatureKind.RESTRICTED)||f.acronym in setOf("COALNE","SLCONS")||safetyContour||hazardous
        if(options.respectScaleMinimum&&!essential&&f.number("SCAMIN")?.let {scale>it}==true){hidden++;continue}
        if(options.category==ChartDisplayCategory.BASE&&!essential&&f.kind !in setOf(NauticalFeatureKind.BEACON,NauticalFeatureKind.TRAFFIC))continue
        val polygon=f.geometry.kind==ChartGeometryKind.POLYGON
        when(f.kind) {
            NauticalFeatureKind.COVERAGE->if(polygon&&f.codes("CATCOV").firstOrNull()!=2)geometryAreas(f,palette.unknown)
            NauticalFeatureKind.LAND->{if(polygon)geometryAreas(f,palette.land);geometryLines(f,palette.ink,1.1f);name(f,40)}
            NauticalFeatureKind.DRYING_AREA->{if(polygon)geometryAreas(f,palette.drying);geometryLines(f,palette.contour,.7f,true)}
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->{
                val low=f.depth?.lowerMeters?.takeIf(Double::isFinite) ?: f.number("DRVAL1")
                val high=f.depth?.upperMeters?.takeIf(Double::isFinite) ?: f.number("DRVAL2")
                val fill=when {low==null->palette.unknown;high!=null&&high<=0.0->palette.drying;low<0->palette.drying;!options.fourDepthShades->if(low<effectiveSafety)palette.unsafe else palette.deep;low<options.shallowDepthMeters->palette.shallow;low<effectiveSafety->palette.unsafe;low<options.deepDepthMeters->palette.medium;else->palette.deep}
                if(polygon)geometryAreas(f,fill)
                if(f.kind==NauticalFeatureKind.DREDGED_AREA) {geometryLines(f,palette.contour,.8f,true);if(zoom>=13&&low!=null)label(f,formats.depth(low),45,palette.contour)}
            }
            NauticalFeatureKind.DEPTH_CONTOUR->{
                if(options.category!=ChartDisplayCategory.BASE||safetyContour) {
                    geometryLines(f,if(safetyContour)palette.ink else palette.contour,if(safetyContour)2.2f else .65f)
                    if(zoom>=12&&options.showNames)f.contour()?.let {label(f,formats.depth(it),if(safetyContour)5 else 80,if(safetyContour)palette.ink else palette.contour,safetyContour)}
                }
            }
            NauticalFeatureKind.SOUNDING->Unit
            NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION->{
                val value=f.depth?.pointMeters ?: f.number("VALSOU") ?: f.depth?.lowerMeters
                val water=f.codes("WATLEV").firstOrNull()
                val danger=value==null||value<=effectiveSafety||water in setOf(1,2,4,5)
                val anchor=f.anchor()
                val surrounded=anchor!=null&&depthAreas.any {it.cellId==f.cellId&&it.datasetId==f.datasetId&&(it.depth?.lowerMeters ?: it.number("DRVAL1") ?: -1.0)>=effectiveSafety&&containsFeature(it,anchor)}
                val isolated=danger&&surrounded
                if(polygon){geometryAreas(f,tint(if(danger)palette.danger else palette.faint,24));geometryLines(f,if(danger)palette.danger else palette.faint,1f,true)}else if(f.geometry.kind==ChartGeometryKind.LINE)geometryLines(f,palette.danger,1.2f,true)
                val kind=when {isolated->ChartSymbolKind.ISOLATED_DANGER;f.kind==NauticalFeatureKind.ROCK->ChartSymbolKind.ROCK;f.kind==NauticalFeatureKind.WRECK->ChartSymbolKind.WRECK;else->ChartSymbolKind.OBSTRUCTION}
                pointSymbol(f,kind,danger,if(danger)0 else 15,if(danger)palette.danger else palette.faint,f.codes("QUAPOS").any {it in 2..9}||f.codes("QUASOU").any {it in setOf(2,3,4,5,6,7,8,9)})
                if(options.showSoundings&&value!=null&&zoom>=12)label(f,formats.depth(value),1,if(danger)palette.ink else palette.faint,danger)
            }
            NauticalFeatureKind.BEACON->{
                val type=when {f.acronym.endsWith("CAR")->ChartSymbolKind.CARDINAL;f.acronym.endsWith("ISD")->ChartSymbolKind.ISOLATED_DANGER;f.acronym.endsWith("SAW")->ChartSymbolKind.SAFE_WATER;f.acronym.endsWith("SPP")->ChartSymbolKind.SPECIAL_MARK;f.acronym in setOf("LNDMRK","DAYMAR")->ChartSymbolKind.LANDMARK;f.acronym.startsWith("BCN")->ChartSymbolKind.BEACON;else->when(f.codes("BOYSHP").firstOrNull()){1->ChartSymbolKind.BUOY_CONE;2->ChartSymbolKind.BUOY_CAN;3->ChartSymbolKind.BUOY_SPHERE;else->ChartSymbolKind.BUOY_PILLAR}}
                pointSymbol(f,type,color=colors(f).firstOrNull() ?: palette.ink);name(f,15)
            }
            NauticalFeatureKind.LIGHT->{
                val color=colors(f).firstOrNull()?.takeIf {it!=palette.ink} ?: palette.danger
                pointSymbol(f,ChartSymbolKind.LIGHT,color=color,priority=12)
                if(options.showLightSectors&&zoom>=10) {
                    val origin=f.anchor()?.geo();val start=f.number("SECTR1");val end=f.number("SECTR2")
                    if(origin!=null&&start!=null&&end!=null&&start in 0.0..360.0&&end in 0.0..360.0) {
                        val sweep=((end-start+360)%360).takeIf {it>.01} ?: 360.0
                        // SECTR1/2 是从海上望向灯的真方位：从灯绘出必须加 180°。
                        val reach=(metersPerPixel*75).coerceAtLeast(10.0).coerceAtMost((f.number("VALNMR") ?: 30.0).coerceAtLeast(.1)*1852)
                        val steps=ceil(sweep/4).toInt().coerceIn(2,90)
                        val arc=(0..steps).map {destination(origin,reach,start+180+sweep*it/steps)}
                        lines+=MapLine("enc:${f.id}:sector-arc",arc,color,2f,casing=false)
                        if(sweep<359.9){lines+=MapLine("enc:${f.id}:sector-start",listOf(origin,arc.first()),palette.faint,.75f,true,false);lines+=MapLine("enc:${f.id}:sector-end",listOf(origin,arc.last()),palette.faint,.75f,true,false)}
                    }
                }
                if(options.showNames&&zoom>=13) {
                    val character=when(f.codes("LITCHR").firstOrNull()){1->"F";2->"Fl";3->"LFl";4->"Q";5->"VQ";6->"UQ";7->"Iso";8->"Oc";else->""}
                    val group=f.attributes["SIGGRP"].orEmpty();val period=f.number("SIGPER")?.let {" ${number(it)}s"}.orEmpty()
                    label(f,(character+group+period).ifBlank {f.attributes["OBJNAM"].orEmpty()},25,color)
                }
            }
            NauticalFeatureKind.RESTRICTED->{if(polygon)geometryAreas(f,tint(palette.danger,15));geometryLines(f,palette.danger,1.3f,true);if(zoom>=10)pointSymbol(f,ChartSymbolKind.CAUTION,color=palette.danger,priority=30);name(f,35)}
            NauticalFeatureKind.TRAFFIC->{if(polygon)geometryAreas(f,tint(palette.danger,14));geometryLines(f,palette.danger,1f,true)
                val direction=f.number("ORIENT")
                if(direction!=null&&zoom>=10)f.anchor()?.geo()?.let {p->val half=metersPerPixel*24;val tip=destination(p,half,direction);lines+=MapLine("enc:${f.id}:traffic-arrow",listOf(destination(p,half,direction+180),tip,destination(tip,half*.35,direction+150),tip,destination(tip,half*.35,direction+210)),palette.danger,1.6f,casing=false)}
                name(f,50)
            }
            NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD->{if(f.geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))pointSymbol(f,ChartSymbolKind.CAUTION,priority=10);geometryLines(f,palette.ink,2f,true);if(polygon)geometryAreas(f,tint(palette.ink,40));if(options.showNames&&zoom>=12){val clearance=f.number("VERCLR") ?: f.number("VERCCL");if(clearance!=null)label(f,"↕ ${formats.depth(clearance)}",8,palette.ink,true)else name(f,30)}}
            NauticalFeatureKind.QUALITY->{if(options.showQuality||options.category==ChartDisplayCategory.ALL){geometryLines(f,palette.danger,.8f,true);f.attributes["CATZOC"]?.let {label(f,"ZOC $it",90,palette.danger)}}}
            NauticalFeatureKind.OTHER->when(f.acronym) {
                "COALNE","SLCONS","DYKCON","SLOTOP"->{geometryLines(f,palette.ink,1.3f);if(polygon)geometryAreas(f,palette.land)}
                "ACHARE","ACHBRT"->{geometryLines(f,palette.danger,.8f,true);if(zoom>=10)pointSymbol(f,ChartSymbolKind.ANCHORAGE,color=palette.danger,priority=40);name(f,50)}
                "BUAARE","BUISGL"->{if(polygon)geometryAreas(f,tint(palette.ink,35));name(f,80)}
                "SEAARE","HRBARE","BERTHS","DOCARE","RIVERS","CANALS","LAKARE"->{if(options.showNames)name(f,70);if(f.acronym in setOf("BERTHS","DOCARE"))geometryLines(f,palette.faint,.8f)}
                "TOPMAR","M_NSYS","M_CSCL","M_PROD","M_NPUB"->Unit
                else->{unknown++;if(options.category==ChartDisplayCategory.ALL){geometryLines(f,palette.faint,.7f,true);if(f.geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))pointSymbol(f,ChartSymbolKind.UNKNOWN,color=palette.faint,priority=90);if(options.showNames&&zoom>=13)label(f,f.attributes["OBJNAM"].orEmpty().ifBlank {f.acronym},95,palette.faint)}}
            }
        }
    }
    val sounding=selectSoundingLabels(features,center,zoom,formats,options,palette)
    val critical=symbols.filter {it.symbol?.dangerous==true}
    val ordinary=symbols.filterNot {it.symbol?.dangerous==true}.sortedWith(compareBy<MapPoint> {it.priority}.thenBy {abs(it.point.lat-center.lat)+abs(((it.point.lon-center.lon+540)%360)-180)}).take((1600-critical.size).coerceAtLeast(0))
    val finalSymbols=critical+ordinary
    val finalLabels=declutterLabels(labels+sounding.points,center,zoom,finalSymbols)
    val info=ChartPortrayalInfo(scale,safetyContours.values.filterNotNull().distinct().sorted(),safetyContours.any {it.value==null},features.any {it.source.compilationScale?.let {native->scale<native/2}==true},hidden,unknown,finalSymbols.size<symbols.size)
    return StructuredSceneDrawing(MapScene(points=finalSymbols.sortedBy {it.priority}+finalLabels,lines=lines,areas=areas),sounding.simplified||finalLabels.count {it.style==MapPointStyle.SOUNDING}<sounding.points.size,info)
}
private fun number(value:Double)=if(abs(value-round(value))<.00001)value.roundToInt().toString()else String.format(Locale.ROOT,"%.1f",value)
private data class SoundingLabelSelection(val points:List<MapPoint>,val simplified:Boolean)
/** 世界坐标网格保留最浅的实际测深值；不平滑、不平均、不让稀释标签影响数据查询。 */
private suspend fun selectSoundingLabels(features:List<NauticalFeature>,center:GeoPoint,zoom:Double,formats:MarineUnitFormats,options:ChartPortrayalPreferences,palette:ChartPalette):SoundingLabelSelection {
    if(!options.showSoundings||options.category==ChartDisplayCategory.BASE)return SoundingLabelSelection(emptyList(),false)
    data class Candidate(val id:String,val index:Int,val point:ChartPoint,val x:Double,val y:Double)
    val level=(floor(zoom*2)/2).coerceIn(1.0,22.0);val world=256*2.0.pow(level)
    fun x(lon:Double)=(lon+180)/360*world
    fun y(lat:Double)=(1-asinh(tan(Math.toRadians(lat.coerceIn(-85.05,85.05))))/PI)/2*world
    val centerX=x(center.lon);val centerY=y(center.lat);val grid=linkedMapOf<Pair<Long,Long>,Candidate>();var total=0
    for(f in features)if(f.kind==NauticalFeatureKind.SOUNDING) {
        var index=0
        for(p in f.geometry.parts.flatMap {it.points}) {
            if(index%256==0)currentCoroutineContext().ensureActive()
            val i=index++;val depth=p.depthMeters?.takeIf(Double::isFinite) ?: continue;total++
            if(level<10)continue
            val px=x(p.longitude);val py=y(p.latitude);val key=floor(px/64).toLong() to floor(py/28).toLong()
            if(grid[key]?.point?.depthMeters?.let {depth>=it}!=true)grid[key]=Candidate(f.id,i,p,px,py)
        }
    }
    fun distance(c:Candidate):Double {val dx=abs(c.x-centerX).let {min(it,world-it)};return dx*dx+(c.y-centerY).pow(2)}
    val selected=grid.values.sortedWith(compareBy<Candidate> {it.point.depthMeters!!>options.safetyDepthMeters}.thenBy(::distance).thenBy {it.id}).take(if(level>=14)220 else 100)
    return SoundingLabelSelection(selected.map {c->val depth=c.point.depthMeters!!;val dangerous=depth<=options.safetyDepthMeters
        MapPoint("enc:${c.id}:sounding:${c.index}",c.point.geo(),formats.depth(depth).replace('-', '−'),if(dangerous)palette.ink else palette.faint,0f,style=MapPointStyle.SOUNDING,bold=dangerous,haloColor=palette.deep,priority=if(dangerous)35 else 85)
    },selected.size<total)
}
/** 两种地图引擎共用稳定世界网格避让；符号保持真实锚点，文字仅以旁置位图留出符号中心。 */
private fun declutterLabels(labels:List<MapPoint>,center:GeoPoint,zoom:Double,symbols:List<MapPoint>):List<MapPoint> {
    val world=256*2.0.pow((floor(zoom*2)/2).coerceIn(1.0,22.0))
    fun xy(p:GeoPoint):Pair<Double,Double> {val lon=((p.lon-center.lon+540)%360)-180;return lon/360*world to (1-asinh(tan(Math.toRadians(p.lat.coerceIn(-85.05,85.05))))/PI)/2*world}
    val occupied=hashSetOf<Pair<Long,Long>>()
    fun slots(p:MapPoint):List<Pair<Long,Long>> {val(x,y)=xy(p.point);val width=(p.label.length*6.0).coerceIn(20.0,300.0);val offset=if(p.style==MapPointStyle.CHART_LABEL)18 else 0;return ((floor((x-width/2)/20).toLong())..floor((x+width/2)/20).toLong()).flatMap {a->(floor((y+offset-7)/14).toLong()..floor((y+offset+7)/14).toLong()).map {b->a to b}}}
    for(s in symbols){val(x,y)=xy(s.point);occupied+=floor(x/20).toLong() to floor(y/14).toLong()}
    return labels.sortedWith(compareBy<MapPoint> {it.priority}.thenBy {it.id}).filter {p->val cells=slots(p);if(cells.any {it in occupied})false else {occupied.addAll(cells);true}}.take(450)
}

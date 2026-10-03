package com.yokuli.runtime.marine.chart.terrain

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.ChartDrawingResult
import com.yokuli.runtime.marine.chart.ChartGeometryOperations
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import kotlin.math.*

/** 显示专用局部坐标；半径最多 8 km，原始 WGS84 坐标/深度和规划依据不改写。 */
internal class ChartTerrainGeometry(
    private val origin:ChartPoint,
    private val radius:Double,
    private val dataset:ChartDataset,
    private val warnings:MutableSet<ChartTerrainWarning>,
    private val tileBounds:ChartBounds?=null,
    private val lod:Int=1,
) {
    private val factory=GeometryFactory()
    private val eastScale=111_320.0*cos(Math.toRadians(origin.latitude)).coerceAtLeast(.003)
    private val windowShape=tileBounds?.let {box->factory.createPolygon(arrayOf(
        Coordinate(x(box.west),z(box.south)),Coordinate(x(box.east),z(box.south)),
        Coordinate(x(box.east),z(box.north)),Coordinate(x(box.west),z(box.north)),Coordinate(x(box.west),z(box.south))))
    }?:factory.createPoint(Coordinate(0.0,0.0)).buffer(radius,64)
    // 单块预算保证移动补片不会一次上传全国几何；范围由分块总集决定，不缩成小圆。
    private val surface=ChartTerrainMeshBuilder(if(lod==0)4_000 else if(tileBounds==null)28_000 else 10_000)
    private val seabed=ChartTerrainMeshBuilder(if(lod==0)6_000 else if(tileBounds==null)32_000 else 14_000)
    private val sources=linkedMapOf<String,ChartTerrainSource>()
    private val markers=ArrayList<ChartTerrainMarker>()
    private val verticalReferences=linkedSetOf<String>()
    private val featureDistances=HashMap<String,Double>()
    private var rasterSamples=0
    private var missingRasterSamples=0
    private var displayedSoundings=0
    private var displayedFacilities=0
    private var check:()->Unit={}
    private val operations=ChartGeometryOperations{check()}
    var needsSubdivision:Boolean=false;private set
    private val land=ChartTerrainMaterial("chart land",.72f,.75f,.70f)
    private val facility=ChartTerrainMaterial("chart facility symbol",.86f,.89f,.86f,roughness=.62f)
    private val hazard=ChartTerrainMaterial("chart hazard symbol",.74f,.39f,.28f)

    suspend fun build(key:String,drawing:ChartDrawingResult,windows:List<ChartRasterWindow>):ChartTerrainTile {
        val work=currentCoroutineContext();check={work.ensureActive()}
        // 航标和碍航物先分配符号预算，不能被海底面或密集测深耗尽。
        appendMarkers(drawing.features)
        // 栅格的可见面来自同一个优先级裁剪器；NoData 仍占据资料来源，只在网格中留下空洞。
        for(entry in windows) {
            check()
            val mask=drawing.rasterMasks["${entry.grid.datasetId}/${entry.grid.cellId}"] ?: continue
            try {raster(entry,polygon(mask))}
            catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){warnings+=ChartTerrainWarning.GEOMETRY_UNCERTAIN}
        }
        for(feature in drawing.features.sortedBy(::featureDistance)) {
            check()
            try {vector(feature)}
            catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){warnings+=ChartTerrainWarning.GEOMETRY_UNCERTAIN}
        }
        check()
        val surfaceGlb=surface.glb(check);val seabedGlb=seabed.glb(check)
        require((surfaceGlb?.size?:0)+(seabedGlb?.size?:0)<=8*1024*1024) {"CHART_TERRAIN_MODEL_LIMIT"}
        if(surface.full||seabed.full){needsSubdivision=true;warnings+=ChartTerrainWarning.MODEL_BUDGET}
        if(surfaceGlb==null&&seabedGlb==null)warnings+=ChartTerrainWarning.NO_DATA
        if(verticalReferences.size>1)warnings+=ChartTerrainWarning.VERTICAL_DATUM_MIXED
        if(missingRasterSamples>0)warnings+=ChartTerrainWarning.MISSING_ELEVATION
        return ChartTerrainTile(key,origin,radius,surfaceGlb,seabedGlb,sources.values.toList(),markers.toList(),warnings.toSet(),
            surface.triangleCount+seabed.triangleCount,
            min(surface.minY,seabed.minY).takeIf(Float::isFinite)?.toDouble()?:0.0,
            max(surface.maxY,seabed.maxY).takeIf(Float::isFinite)?.toDouble()?:0.0,dataset.revision,
            ChartTerrainCoverage(rasterSamples,missingRasterSamples,displayedSoundings,displayedFacilities))
    }

    private fun raster(entry:ChartRasterWindow,visible:Geometry) {
        val grid=entry.grid;val window=entry.window
        if(window.width<2||window.height<2||visible.isEmpty)return
        val cells=(window.width-1).toLong()*(window.height-1)
        val stride=ceil(sqrt(cells/(if(lod==0)2_000.0 else 6_000.0))).toInt().coerceAtLeast(1)
        if(stride>1)warnings+=ChartTerrainWarning.MODEL_BUDGET
        val spacingX=grid.pixelWidthDegrees*eastScale;val spacingZ=grid.pixelHeightDegrees*111_320.0
        val sourceId="${dataset.id}/${grid.cellId}/elevation/${grid.id}"
        registerSource(ChartTerrainSource(sourceId,dataset.id,grid.cellId,grid.sourceName,ChartTerrainSourceKind.ELEVATION_GRID,
            max(spacingX,spacingZ),grid.verticalReference,true))
        val prepared=PreparedGeometryFactory.prepare(visible)
        val samplePoint=factory.createPoint(Coordinate(0.0,0.0))
        // 前缀和在抽样块内检查每个原像元；四角有效不能掩盖中间的 NaN 缺测孔洞。
        val missing=IntArray((window.width+1)*(window.height+1))
        var containsLand=false;var containsSeabed=false
        for(y in 0 until window.height) {
            check();var rowMissing=0
            for(x in 0 until window.width) {
                if(x%128==0)check()
                val elevation=window.elevationMeters[y*window.width+x]
                if(!elevation.isFinite())rowMissing++
                else if(elevation>0f)containsLand=true else containsSeabed=true
                missing[(y+1)*(window.width+1)+x+1]=missing[y*(window.width+1)+x+1]+rowMissing
                val location=grid.centre(window.column+x,window.row+y)
                samplePoint.coordinateSequence.setOrdinate(0,0,this.x(location.longitude));samplePoint.coordinateSequence.setOrdinate(0,1,z(location.latitude));samplePoint.geometryChanged()
                if(prepared.covers(samplePoint)) {
                    rasterSamples++
                    if(!window.elevationMeters[y*window.width+x].isFinite())missingRasterSamples++
                }
            }
        }
        fun noData(x0:Int,y0:Int,x1:Int,y1:Int):Boolean {
            val width=window.width+1
            return missing[(y1+1)*width+x1+1]-missing[y0*width+x1+1]-missing[(y1+1)*width+x0]+missing[y0*width+x0]>0
        }
        fun vertex(x:Int,y:Int):ChartTerrainVertex {
            val point=grid.centre(window.column+x,window.row+y)
            val height=window.elevationMeters[y*window.width+x]
            fun value(u:Int,v:Int)=if(u in 0 until window.width&&v in 0 until window.height)window.elevationAt(u,v)else null
            val left=value(x-1,y);val right=value(x+1,y);val north=value(x,y-1);val south=value(x,y+1)
            val slopeX=when {left!=null&&right!=null->(right-left)/(2*spacingX);right!=null->(right-height)/spacingX;left!=null->(height-left)/spacingX;else->0.0}
            val slopeZ=when {north!=null&&south!=null->(south-north)/(2*spacingZ);south!=null->(south-height)/spacingZ;north!=null->(height-north)/spacingZ;else->0.0}
            return ChartTerrainVertex(x(point.longitude).toFloat(),height,z(point.latitude).toFloat(),-slopeX.toFloat(),1f,-slopeZ.toFloat())
        }
        fun append(a:ChartTerrainVertex,b:ChartTerrainVertex,c:ChartTerrainVertex) {
            if(surface.full&&seabed.full)return
            val high=max(a.y,max(b.y,c.y));val low=min(a.y,min(b.y,c.y))
            if((high<=0f&&seabed.full)||(low>0f&&surface.full)){warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
            val triangle=factory.createPolygon(arrayOf(Coordinate(a.x.toDouble(),a.z.toDouble()),Coordinate(b.x.toDouble(),b.z.toDouble()),Coordinate(c.x.toDouble(),c.z.toDouble()),Coordinate(a.x.toDouble(),a.z.toDouble())))
            // 整个三角形都须落在有效优先级范围；不能跨孔洞或用低层平面抹掉局部未知。
            if(!prepared.covers(triangle))return
            when {
                high<=0f->seabed.triangle(depthMaterial(-(a.y+b.y+c.y)/3),a,b,c)
                low>=0f->surface.triangle(land,a,b,c)
                else->{planePart(listOf(a,b,c),true).let {fan(surface,land,it)};planePart(listOf(a,b,c),false).let {fan(seabed,depthMaterial(-low/2),it)}}
            }
        }
        fun block(x0:Int,y0:Int,x1:Int,y1:Int) {
            check()
            if(surface.full&&seabed.full){warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
            if((!containsLand&&seabed.full)||(!containsSeabed&&surface.full)){warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
            val smallest=x1-x0==1&&y1-y0==1
            var divide=noData(x0,y0,x1,y1)
            if(divide&&smallest)return
            if(!divide&&!smallest) {
                val a=window.elevationMeters[y0*window.width+x0].toDouble();val b=window.elevationMeters[y0*window.width+x1].toDouble()
                val c=window.elevationMeters[y1*window.width+x0].toDouble();val d=window.elevationMeters[y1*window.width+x1].toDouble()
                sample@for(row in y0..y1)for(col in x0..x1) {
                    if(col%128==0)check()
                    val u=(col-x0).toDouble()/(x1-x0);val v=(row-y0).toDouble()/(y1-y0)
                    val predicted=if(u+v<=1.0)a+(b-a)*u+(c-a)*v else d+(c-d)*(1-u)+(b-d)*(1-v)
                    val actual=window.elevationMeters[row*window.width+col].toDouble()
                    // LOD 不能抹掉中间有限高程的小岛/浅脊。跨海平面必须精确保留符号；
                    // 浅水容差 25 cm、深水最多 1 m，只控制显示误差，不修改任何原始点。
                    val tolerance=(abs(actual)*.005).coerceIn(.25,1.0)
                    if((actual>=0)!=(predicted>=0)||abs(actual-predicted)>tolerance){divide=true;break@sample}
                }
            }
            if(divide) {
                val xs=if(x1-x0>1)listOf(x0,(x0+x1)/2,x1)else listOf(x0,x1)
                val ys=if(y1-y0>1)listOf(y0,(y0+y1)/2,y1)else listOf(y0,y1)
                for(j in 1 until ys.size)for(i in 1 until xs.size)block(xs[i-1],ys[j-1],xs[i],ys[j])
            }else {
                val a=vertex(x0,y0);val b=vertex(x1,y0);val c=vertex(x0,y1);val d=vertex(x1,y1)
                // x 东/z 南：a,c,b 的绕序保证正 y 法线，海底从上方可见。
                append(a,c,b);append(b,c,d)
            }
        }
        // 预算优先保住船位附近，不能固定从图幅左上角开始挤掉当前区域。
        val blocks=ArrayList<IntArray>()
        var y=0
        while(y<window.height-1) {val endY=min(y+stride,window.height-1);var col=0
            while(col<window.width-1){val endX=min(col+stride,window.width-1);blocks+=intArrayOf(col,y,endX,endY);col=endX};y=endY}
        blocks.sortBy {part->grid.centre(window.column+(part[0]+part[2])/2,window.row+(part[1]+part[3])/2).let {hypot(x(it.longitude),z(it.latitude))}}
        for(part in blocks)block(part[0],part[1],part[2],part[3])
    }

    private fun vector(feature:NauticalFeature) {
        val uncertain=feature.attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN"||"GPKG_DATELINE_TOPOLOGY_UNCERTAIN" in feature.issues
        if(uncertain){warnings+=ChartTerrainWarning.GEOMETRY_UNCERTAIN;return}
        val depth=feature.depth
        val isInterval=feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DRYING_AREA)&&depth?.kind==DepthEvidenceKind.INTERVAL
        val source=featureSource(feature,isInterval)
        if(feature.geometry.kind==ChartGeometryKind.LINE&&feature.acronym in setOf("COALNE","SLCONS")) {
            registerSource(source)
            lineRibbon(feature.geometry,0f,(radius*.001).coerceIn(.8,5.0).toFloat(),surface,land.copy(name="measured shoreline",red=.87f,green=.88f,blue=.83f))
        }
        if(feature.kind==NauticalFeatureKind.DEPTH_CONTOUR&&feature.geometry.kind==ChartGeometryKind.LINE) {
            val value=depth?.pointMeters?.takeIf(Double::isFinite) ?: return
            registerSource(source)
            lineRibbon(feature.geometry,-value.toFloat(),(radius*.0006).coerceIn(.3,2.5).toFloat(),if(value<0)surface else seabed,
                depthMaterial(value.toFloat()).copy(name="measured depth contour",red=.61f,green=.76f,blue=.76f,doubleSided=true))
        }
        if(feature.geometry.kind==ChartGeometryKind.POLYGON&&(feature.kind==NauticalFeatureKind.LAND||isInterval||feature.acronym in setOf("SLCONS","DOCARE","HRBFAC"))) {
            val raw=polygon(feature.geometry)
            val shape=if(lod==0&&raw.numPoints>256)TopologyPreservingSimplifier.simplify(raw,(radius/800.0).coerceIn(.5,12.0))else raw
            if(shape.isEmpty)return
            if(shape.numPoints>30_000){needsSubdivision=true;warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
            val elevation=feature.attributes["ELEVAT"]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            val height=if(isInterval)depth?.lowerMeters?.takeIf(Double::isFinite)?.let {-it} ?: return
                else elevation ?: 0.0
            if(isInterval)warnings+=ChartTerrainWarning.DEPTH_INTERVALS
            else if(elevation==null)warnings+=ChartTerrainWarning.LAND_HEIGHT_UNKNOWN
            registerSource(source)
            val destination=if(height>=0)surface else seabed
            val material=if(height>=0)land else depthMaterial(-height.toFloat())
            triangulate(shape,height.toFloat(),destination,material)
            // 深度区间用上下界侧带表达范围；这是“区间阶地”，没有插值出精确海底。
            if(isInterval)depth?.upperMeters?.takeIf {it.isFinite()&&it>=-height&&it+height>.2}?.let {upper->
                intervalEdges(shape,height.toFloat(),-upper.toFloat(),destination,material.copy(name=material.name+" interval",alpha=.24f))
            }
        }
    }

    private fun registerSource(source:ChartTerrainSource) {
        sources[source.id]=source
        source.verticalReference?.takeIf(String::isNotBlank)?.let(verticalReferences::add)
    }

    private fun featureSource(feature:NauticalFeature,interval:Boolean=false):ChartTerrainSource {
        val cell=dataset.cells.firstOrNull {it.cellId==feature.cellId}
        val datum=feature.depth?.datum
        val id="${dataset.id}/${feature.cellId}/${if(interval)"interval" else "objects"}/${datum.orEmpty()}"
        return ChartTerrainSource(id,dataset.id,feature.cellId,cell?.sourceName?:feature.cellId,
            if(interval)ChartTerrainSourceKind.DEPTH_INTERVALS else ChartTerrainSourceKind.CHART_OBJECTS,
            verticalReference=datum,referenceOnly=cell?.referenceOnly==true)
    }

    private fun featureDistance(feature:NauticalFeature):Double=featureDistances.getOrPut(feature.id) {
        var closest=Double.POSITIVE_INFINITY;var count=0
        for(part in feature.geometry.parts)for(point in part.points) {
            if(count++%256==0)check()
            closest=min(closest,hypot(x(point.longitude),z(point.latitude)))
        }
        closest
    }

    private fun markerKind(feature:NauticalFeature):ChartTerrainMarkerKind?=when(feature.kind) {
        NauticalFeatureKind.BEACON->ChartTerrainMarkerKind.BEACON
        NauticalFeatureKind.LIGHT->ChartTerrainMarkerKind.LIGHT
        NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION->ChartTerrainMarkerKind.HAZARD
        NauticalFeatureKind.SOUNDING->ChartTerrainMarkerKind.SOUNDING
        else->if(feature.acronym in setOf("HRBFAC","SMCFAC","BERTHS","MORFAC","DOCARE","PILBOP","RTPBCN"))ChartTerrainMarkerKind.FACILITY else null
    }

    private fun uncertain(feature:NauticalFeature)=feature.attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN"||"GPKG_DATELINE_TOPOLOGY_UNCERTAIN" in feature.issues
    private fun featureName(feature:NauticalFeature)=feature.attributes["NOBJNM"]?.takeIf(String::isNotBlank) ?: feature.attributes["OBJNAM"]?.takeIf(String::isNotBlank) ?: feature.acronym

    private data class Sounding(val feature:NauticalFeature,val point:ChartPoint,val depth:Double,val distance:Double)

    private fun appendMarkers(features:List<NauticalFeature>) {
        val eligible=features.filterNot(::uncertain)
        val groups=eligible.filter {markerKind(it)?.let {kind->kind!=ChartTerrainMarkerKind.SOUNDING}==true}
            .groupBy {requireNotNull(markerKind(it))}.mapValues {(_,values)->values.sortedBy(::featureDistance)}
        // 分类别轮流分配：密集碍航物不挤掉航标，测深另有独立预算。
        val kinds=listOf(ChartTerrainMarkerKind.HAZARD,ChartTerrainMarkerKind.BEACON,ChartTerrainMarkerKind.LIGHT,ChartTerrainMarkerKind.FACILITY)
        val total=groups.values.sumOf {it.size}
        if(total>64)warnings+=ChartTerrainWarning.MODEL_BUDGET
        var round=0
        while(displayedFacilities<64&&round<groups.values.maxOfOrNull {it.size}.orZero()) {
            check()
            for(kind in kinds) {
                val feature=groups[kind]?.getOrNull(round) ?: continue
                if(displayedFacilities>=64)break
                try {appendFacility(feature,kind)}
                catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(_:Exception){warnings+=ChartTerrainWarning.GEOMETRY_UNCERTAIN}
            }
            round++
        }
        // 测深只从真实点中选取空间分散的代表点，绝不平均、插值或移动点。
        val soundings=HashMap<Pair<Int,Int>,Sounding>()
        val cellSize=radius/6.0
        var totalSoundings=0
        for(feature in eligible.filter {it.kind==NauticalFeatureKind.SOUNDING})for(part in feature.geometry.parts)for(p in part.points) {
            if(totalSoundings%128==0)check()
            val depth=p.depthMeters ?: feature.depth?.pointMeters?.takeIf {feature.geometry.kind==ChartGeometryKind.POINT} ?: continue
            if(!depth.isFinite())continue
            val east=x(p.longitude);val south=z(p.latitude);val distance=hypot(east,south)
            if(distance>radius)continue
            totalSoundings++
            val key=floor(east/cellSize).toInt() to floor(south/cellSize).toInt()
            if(soundings[key]?.distance?.let {it<=distance}!=true)soundings[key]=Sounding(feature,p,depth,distance)
        }
        val selected=soundings.values.sortedBy {it.distance}.take(96)
        if(totalSoundings>selected.size)warnings+=ChartTerrainWarning.MODEL_BUDGET
        for(sounding in selected) {
            check()
            val source=featureSource(sounding.feature)
            val east=x(sounding.point.longitude).toFloat();val south=z(sounding.point.latitude).toFloat();val height=-sounding.depth.toFloat()
            val builder=if(height>0f)surface else seabed
            if(builder.remainingTriangles<8){warnings+=ChartTerrainWarning.MODEL_BUDGET;continue}
            val size=(radius*.0009).coerceIn(.5,3.0).toFloat()
            octahedron(builder,facility,east,height,south,size,size)
            registerSource(source)
            markers+=ChartTerrainMarker("${sounding.feature.id}:${sounding.point.latitude}:${sounding.point.longitude}",featureName(sounding.feature),ChartTerrainMarkerKind.SOUNDING,
                ChartPoint(sounding.point.latitude,sounding.point.longitude),east.toDouble(),height.toDouble(),south.toDouble(),source.id,false,sounding.depth,sounding.depth)
            displayedSoundings++
        }
    }

    private fun Int?.orZero()=this ?: 0

    private fun appendFacility(feature:NauticalFeature,kind:ChartTerrainMarkerKind) {
        val points=feature.geometry.parts.flatMap {it.points}
        if(points.isEmpty())return
        val point=if(feature.geometry.kind==ChartGeometryKind.POLYGON) {
            val shape=polygon(feature.geometry)
            if(shape.isEmpty)return
            shape.interiorPoint.coordinate.let {geo(it.x,it.y)}
        }else points.minByOrNull {hypot(x(it.longitude),z(it.latitude))} ?: return
        val east=x(point.longitude).toFloat();val south=z(point.latitude).toFloat()
        if(hypot(east.toDouble(),south.toDouble())>radius)return
        if(surface.remainingTriangles<112){warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
        val color=when(feature.attributes["COLOUR"]?.split(',')?.firstOrNull()?.trim()) {
            "3"->ChartTerrainMaterial("red chart mark",.74f,.28f,.23f)
            "4"->ChartTerrainMaterial("green chart mark",.27f,.58f,.42f)
            "6"->ChartTerrainMaterial("yellow chart mark",.79f,.66f,.3f)
            else->if(kind==ChartTerrainMarkerKind.HAZARD)hazard else facility
        }
        // 设施造型是可识别的海图符号；尺度随视窗限幅，不声称是真实建筑尺寸。
        val height=(radius*.009).coerceIn(6.0,26.0).toFloat()
        val width=height*.25f
        val centreY=when {
            kind==ChartTerrainMarkerKind.HAZARD->{octahedron(surface,color,east,height*.25f,south,width,height*.25f);height*.25f}
            feature.acronym.startsWith("BOY")-> {
                val shape=feature.attributes["BOYSHP"]?.split(',')?.firstOrNull()?.trim()?.toIntOrNull()
                when(shape) {
                    1->frustum(color,east,0f,south,width,0f,height*.7f)
                    2->frustum(color,east,0f,south,width,width,height*.6f)
                    3->octahedron(surface,color,east,height*.35f,south,width,height*.35f)
                    else->{frustum(color,east,0f,south,width,width*.55f,height*.25f);surface.box(color,east,height*.25f,south,width*.5f,height*.5f,width*.5f)}
                }
                height*.45f
            }
            kind==ChartTerrainMarkerKind.FACILITY->{surface.box(color,east,0f,south,width*2f,height*.4f,width*2f);height*.2f}
            else->{frustum(color,east,0f,south,width*.65f,width*.35f,height*.75f);octahedron(surface,color,east,height*.8f,south,width*.8f,height*.16f);height*.8f}
        }
        val source=featureSource(feature);registerSource(source)
        markers+=ChartTerrainMarker(feature.id,featureName(feature),kind,ChartPoint(point.latitude,point.longitude),east.toDouble(),centreY.toDouble(),south.toDouble(),source.id,
            symbolic=true,depthLowerMeters=point.depthMeters ?: feature.depth?.pointMeters ?: feature.depth?.lowerMeters,depthUpperMeters=feature.depth?.upperMeters)
        displayedFacilities++
    }

    private fun octahedron(builder:ChartTerrainMeshBuilder,material:ChartTerrainMaterial,east:Float,height:Float,south:Float,width:Float,halfHeight:Float) {
        val top=ChartTerrainVertex(east,height+halfHeight,south);val bottom=ChartTerrainVertex(east,height-halfHeight,south)
        val ring=listOf(ChartTerrainVertex(east-width,height,south),ChartTerrainVertex(east,height,south+width),ChartTerrainVertex(east+width,height,south),ChartTerrainVertex(east,height,south-width))
        for(i in ring.indices){builder.triangle(material,top,ring[i],ring[(i+1)%4]);builder.triangle(material,bottom,ring[(i+1)%4],ring[i])}
    }

    private fun frustum(material:ChartTerrainMaterial,east:Float,base:Float,south:Float,lowerRadius:Float,upperRadius:Float,height:Float) {
        val count=10
        val bottom=ChartTerrainVertex(east,base,south);val top=ChartTerrainVertex(east,base+height,south)
        for(i in 0 until count) {
            val a=2*Math.PI*i/count;val b=2*Math.PI*(i+1)/count
            val p=ChartTerrainVertex(east+cos(a).toFloat()*lowerRadius,base,south+sin(a).toFloat()*lowerRadius)
            val q=ChartTerrainVertex(east+cos(b).toFloat()*lowerRadius,base,south+sin(b).toFloat()*lowerRadius)
            val r=ChartTerrainVertex(east+cos(a).toFloat()*upperRadius,base+height,south+sin(a).toFloat()*upperRadius)
            val s=ChartTerrainVertex(east+cos(b).toFloat()*upperRadius,base+height,south+sin(b).toFloat()*upperRadius)
            surface.triangle(material,p,r,q);surface.triangle(material,q,r,s)
            surface.triangle(material,bottom,p,q)
            if(upperRadius>0f)surface.triangle(material,top,s,r)
        }
    }

    private fun triangulate(original:Geometry,height:Float,builder:ChartTerrainMeshBuilder,material:ChartTerrainMaterial) {
        check()
        // 基础显示层只简化投影后的显示网格，来源几何和规划不经过此分支。
        val shape=if(lod==0&&original.numPoints>256)TopologyPreservingSimplifier.simplify(original,(radius/800.0).coerceIn(.5,12.0))else original
        check()
        if(shape.numPoints>30_000){needsSubdivision=true;warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
        val triangles=PolygonTriangulator.triangulate(shape)
        for(i in 0 until triangles.numGeometries) {
            if(i%128==0)check()
            if(builder.full){warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
            val p=triangles.getGeometryN(i).coordinates
            if(p.size<3)continue
            val a=ChartTerrainVertex(p[0].x.toFloat(),height,p[0].y.toFloat(),0f,1f,0f)
            val b=ChartTerrainVertex(p[1].x.toFloat(),height,p[1].y.toFloat(),0f,1f,0f)
            val c=ChartTerrainVertex(p[2].x.toFloat(),height,p[2].y.toFloat(),0f,1f,0f)
            if((b.z-a.z)*(c.x-a.x)-(b.x-a.x)*(c.z-a.z)>0)builder.triangle(material,a,b,c)else builder.triangle(material,a,c,b)
        }
    }

    private fun intervalEdges(shape:Geometry,top:Float,bottom:Float,builder:ChartTerrainMeshBuilder,material:ChartTerrainMaterial) {
        if(top<=bottom)return
        for(i in 0 until shape.numGeometries) {
            val polygon=shape.getGeometryN(i) as? Polygon ?: continue
            val rings=listOf(polygon.exteriorRing)+(0 until polygon.numInteriorRing).map(polygon::getInteriorRingN)
            for(ring in rings) {
                val points=ring.coordinates
                for(j in 1 until points.size) {
                    if(j%128==0)check()
                    if(builder.full)return
                    val a=points[j-1];val b=points[j]
                    // 分块裁剪产生的矩形接缝不是海底断崖；不能给每块边缘画一圈垂直幕墙。
                    if(tileBounds!=null) {
                        val edge=windowShape.envelopeInternal
                        fun sameEdge(value:Double,first:Double,second:Double)=abs(first-value)<.02&&abs(second-value)<.02
                        if(sameEdge(edge.minX,a.x,b.x)||sameEdge(edge.maxX,a.x,b.x)||
                            sameEdge(edge.minY,a.y,b.y)||sameEdge(edge.maxY,a.y,b.y))continue
                    }
                    val p=ChartTerrainVertex(a.x.toFloat(),top,a.y.toFloat());val q=ChartTerrainVertex(b.x.toFloat(),top,b.y.toFloat())
                    val r=ChartTerrainVertex(a.x.toFloat(),bottom,a.y.toFloat());val s=ChartTerrainVertex(b.x.toFloat(),bottom,b.y.toFloat())
                    builder.triangle(material.copy(doubleSided=true),p,q,r);builder.triangle(material.copy(doubleSided=true),q,s,r)
                }
            }
        }
    }

    /** 原始岸线仅画窄带，不靠线的包围框补造陆地区域或港池模型。 */
    private fun lineRibbon(geometry:ChartGeometry,height:Float,width:Float,builder:ChartTerrainMeshBuilder,material:ChartTerrainMaterial) {
        for(part in geometry.parts) {
            check()
            if(part.points.size<2)continue
            val line=operations.intersection(factory.createLineString(part.points.map {Coordinate(x(it.longitude),z(it.latitude))}.toTypedArray()),windowShape)
            for(i in 0 until line.numGeometries) {
                val section=line.getGeometryN(i) as? LineString ?: continue
                val points=section.coordinates
                for(j in 1 until points.size) {
                    if(j%128==0)check()
                    if(builder.full){warnings+=ChartTerrainWarning.MODEL_BUDGET;return}
                    val a=points[j-1];val b=points[j];val length=hypot(b.x-a.x,b.y-a.y)
                    if(length<.001)continue
                    val ox=(-(b.y-a.y)/length*width/2).toFloat();val oz=((b.x-a.x)/length*width/2).toFloat()
                    val p=ChartTerrainVertex(a.x.toFloat()+ox,height,a.y.toFloat()+oz,0f,1f,0f)
                    val q=ChartTerrainVertex(b.x.toFloat()+ox,height,b.y.toFloat()+oz,0f,1f,0f)
                    val r=ChartTerrainVertex(a.x.toFloat()-ox,height,a.y.toFloat()-oz,0f,1f,0f)
                    val s=ChartTerrainVertex(b.x.toFloat()-ox,height,b.y.toFloat()-oz,0f,1f,0f)
                    builder.triangle(material,p,q,r);builder.triangle(material,q,s,r)
                }
            }
        }
    }

    /** 保留每个外环与所属孔洞；不能用三角扇把岛屿孔洞填平。 */
    private fun polygon(geometry:ChartGeometry):Geometry {
        if(geometry.kind!=ChartGeometryKind.POLYGON)return factory.createPolygon()
        fun ring(part:ChartGeometryPart):LinearRing {
            require(part.points.size>=4) {"CHART_TERRAIN_RING_INVALID"}
            val coordinates=part.points.mapIndexed {index,p->if(index%256==0)check();Coordinate(x(p.longitude),z(p.latitude))}.toTypedArray()
            require(coordinates.first().equals2D(coordinates.last())) {"CHART_TERRAIN_RING_OPEN"}
            return factory.createLinearRing(coordinates)
        }
        val shells=geometry.parts.filterNot {it.hole}.map {factory.createPolygon(ring(it))}
        val holes=geometry.parts.filter {it.hole}.map(::ring).groupBy {hole->
            check();val area=factory.createPolygon(hole)
            shells.filter {it.covers(area)}.minByOrNull {it.area} ?: error("CHART_TERRAIN_HOLE_UNATTACHED")
        }
        val source=factory.createMultiPolygon(shells.map {shell->factory.createPolygon(shell.exteriorRing as LinearRing,holes[shell].orEmpty().toTypedArray())}.toTypedArray())
            .also {require(it.isValid){"CHART_TERRAIN_GEOMETRY_INVALID"}}
        return operations.area(operations.intersection(source,windowShape))
    }

    private fun planePart(points:List<ChartTerrainVertex>,above:Boolean):List<ChartTerrainVertex> {
        val output=ArrayList<ChartTerrainVertex>();var a=points.last()
        fun inside(p:ChartTerrainVertex)=if(above)p.y>=0 else p.y<=0
        for(b in points) {
            if(inside(a)!=inside(b)) {
                val t=(-a.y/(b.y-a.y)).coerceIn(0f,1f)
                output+=ChartTerrainVertex(a.x+(b.x-a.x)*t,0f,a.z+(b.z-a.z)*t,
                    a.normalX+(b.normalX-a.normalX)*t,a.normalY+(b.normalY-a.normalY)*t,a.normalZ+(b.normalZ-a.normalZ)*t)
            }
            if(inside(b))output+=b
            a=b
        }
        return output
    }
    private fun fan(builder:ChartTerrainMeshBuilder,material:ChartTerrainMaterial,points:List<ChartTerrainVertex>) {
        for(i in 1 until points.size-1)builder.triangle(material,points[0],points[i],points[i+1])
    }
    private fun depthMaterial(depth:Float):ChartTerrainMaterial {
        val band=when {depth<5->0;depth<15->1;depth<30->2;depth<60->3;depth<150->4;else->5}
        val value=band/5f
        return ChartTerrainMaterial("depth band $band",.36f-.13f*value,.60f-.19f*value,.64f-.17f*value,roughness=.92f)
    }
    private fun x(longitude:Double)=(((longitude-origin.longitude+180.0)%360.0+360.0)%360.0-180.0)*eastScale
    private fun z(latitude:Double)=(origin.latitude-latitude)*111_320.0
    private fun geo(east:Double,south:Double)=ChartPoint(origin.latitude-south/111_320.0,((origin.longitude+east/eastScale+180.0)%360.0+360.0)%360.0-180.0)
}

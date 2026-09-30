package com.yokuli.marine.shell.rebuild.scene.navigation

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.ChartDrawingResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator
import kotlin.math.*

/** 显示专用局部坐标；半径最多 8 km，原始 WGS84 坐标/深度和规划依据不改写。 */
internal class NavigationTerrainGeometry(
    private val origin:GeoPoint,
    private val radius:Double,
    private val dataset:ChartDataset,
    private val warnings:MutableSet<NavigationChartWarning>,
) {
    private val factory=GeometryFactory()
    private val windowShape=factory.createPoint(Coordinate(0.0,0.0)).buffer(radius,64)
    private val eastScale=111_320.0*cos(Math.toRadians(origin.lat)).coerceAtLeast(.003)
    private val surface=NavigationTerrainMeshBuilder(28_000)
    private val seabed=NavigationTerrainMeshBuilder(32_000)
    private val sources=linkedMapOf<String,NavigationChartSource>()
    private val markers=ArrayList<NavigationChartMarker>()
    private var check:()->Unit={}
    private val land=NavigationTerrainMaterial("chart land",.72f,.75f,.70f)
    private val facility=NavigationTerrainMaterial("chart facility symbol",.86f,.89f,.86f,roughness=.62f)
    private val hazard=NavigationTerrainMaterial("chart hazard symbol",.74f,.39f,.28f)

    suspend fun build(key:String,drawing:ChartDrawingResult,windows:List<ChartRasterWindow>):NavigationChartScene {
        val work=currentCoroutineContext();check={work.ensureActive()}
        // 栅格的可见面来自同一个优先级裁剪器；NoData 仍占据资料来源，只在网格中留下空洞。
        for(entry in windows) {
            check()
            val mask=drawing.rasterMasks["${entry.grid.datasetId}/${entry.grid.cellId}"] ?: continue
            try {raster(entry,polygon(mask))}
            catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){warnings+=NavigationChartWarning.GEOMETRY_UNCERTAIN}
        }
        for(feature in drawing.features) {
            check()
            try {vector(feature)}
            catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){warnings+=NavigationChartWarning.GEOMETRY_UNCERTAIN}
        }
        check()
        val surfaceGlb=surface.glb();val seabedGlb=seabed.glb()
        require((surfaceGlb?.size?:0)+(seabedGlb?.size?:0)<=8*1024*1024) {"CHART_TERRAIN_MODEL_LIMIT"}
        if(surface.full||seabed.full)warnings+=NavigationChartWarning.MODEL_BUDGET
        if(surfaceGlb==null&&seabedGlb==null)warnings+=NavigationChartWarning.NO_DATA
        val datums=sources.values.mapNotNull {it.verticalReference}.distinct()
        if(datums.size>1)warnings+=NavigationChartWarning.VERTICAL_DATUM_MIXED
        return NavigationChartScene(key,origin,radius,surfaceGlb,seabedGlb,sources.values.toList(),markers.toList(),warnings.toSet(),
            surface.triangleCount+seabed.triangleCount,
            min(surface.minY,seabed.minY).takeIf(Float::isFinite)?.toDouble()?:0.0,
            max(surface.maxY,seabed.maxY).takeIf(Float::isFinite)?.toDouble()?:0.0,dataset.revision)
    }

    private fun raster(entry:ChartRasterWindow,visible:Geometry) {
        val grid=entry.grid;val window=entry.window
        if(window.width<2||window.height<2||visible.isEmpty)return
        val cells=(window.width-1).toLong()*(window.height-1)
        val stride=ceil(sqrt(cells/12_000.0)).toInt().coerceAtLeast(1)
        if(stride>1)warnings+=NavigationChartWarning.MODEL_BUDGET
        val spacingX=grid.pixelWidthDegrees*eastScale;val spacingZ=grid.pixelHeightDegrees*111_320.0
        val sourceId="${dataset.id}/${grid.cellId}/elevation"
        sources[sourceId]=NavigationChartSource(sourceId,dataset.id,grid.cellId,grid.sourceName,NavigationChartSourceKind.ELEVATION_GRID,
            max(spacingX,spacingZ),grid.verticalReference,true)
        val prepared=PreparedGeometryFactory.prepare(visible)
        // 前缀和在抽样块内检查每个原像元；四角有效不能掩盖中间的 NaN 缺测孔洞。
        val missing=IntArray((window.width+1)*(window.height+1))
        for(y in 0 until window.height) {
            check();var rowMissing=0
            for(x in 0 until window.width) {
                if(!window.elevationMeters[y*window.width+x].isFinite())rowMissing++
                missing[(y+1)*(window.width+1)+x+1]=missing[y*(window.width+1)+x+1]+rowMissing
            }
        }
        fun noData(x0:Int,y0:Int,x1:Int,y1:Int):Boolean {
            val width=window.width+1
            return missing[(y1+1)*width+x1+1]-missing[y0*width+x1+1]-missing[(y1+1)*width+x0]+missing[y0*width+x0]>0
        }
        fun vertex(x:Int,y:Int):NavigationTerrainVertex {
            val point=grid.centre(window.column+x,window.row+y)
            val height=window.elevationMeters[y*window.width+x]
            fun value(u:Int,v:Int)=if(u in 0 until window.width&&v in 0 until window.height)window.elevationAt(u,v)else null
            val left=value(x-1,y);val right=value(x+1,y);val north=value(x,y-1);val south=value(x,y+1)
            val slopeX=when {left!=null&&right!=null->(right-left)/(2*spacingX);right!=null->(right-height)/spacingX;left!=null->(height-left)/spacingX;else->0.0}
            val slopeZ=when {north!=null&&south!=null->(south-north)/(2*spacingZ);south!=null->(south-height)/spacingZ;north!=null->(height-north)/spacingZ;else->0.0}
            return NavigationTerrainVertex(x(point.longitude).toFloat(),height,z(point.latitude).toFloat(),-slopeX.toFloat(),1f,-slopeZ.toFloat())
        }
        fun append(a:NavigationTerrainVertex,b:NavigationTerrainVertex,c:NavigationTerrainVertex) {
            if(surface.full&&seabed.full)return
            val triangle=factory.createPolygon(arrayOf(Coordinate(a.x.toDouble(),a.z.toDouble()),Coordinate(b.x.toDouble(),b.z.toDouble()),Coordinate(c.x.toDouble(),c.z.toDouble()),Coordinate(a.x.toDouble(),a.z.toDouble())))
            // 整个三角形都须落在有效优先级范围；不能跨孔洞或用低层平面抹掉局部未知。
            if(!prepared.covers(triangle))return
            val high=max(a.y,max(b.y,c.y));val low=min(a.y,min(b.y,c.y))
            when {
                high<=0f->seabed.triangle(depthMaterial(-(a.y+b.y+c.y)/3),a,b,c)
                low>=0f->surface.triangle(land,a,b,c)
                else->{planePart(listOf(a,b,c),true).let {fan(surface,land,it)};planePart(listOf(a,b,c),false).let {fan(seabed,depthMaterial(-low/2),it)}}
            }
        }
        var y=0
        while(y<window.height-1) {
            check();val endY=min(y+stride,window.height-1);var col=0
            while(col<window.width-1) {
                if(col%32==0)check()
                val endX=min(col+stride,window.width-1)
                if(!noData(col,y,endX,endY)) {
                    val a=vertex(col,y);val b=vertex(endX,y);val c=vertex(col,endY);val d=vertex(endX,endY)
                    // x 东/z 南：a,c,b 的绕序保证正 y 法线，海底从上方可见。
                    append(a,c,b);append(b,c,d)
                }
                col=endX
            }
            y=endY
        }
    }

    private fun vector(feature:NauticalFeature) {
        val uncertain=feature.attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN"||"GPKG_DATELINE_TOPOLOGY_UNCERTAIN" in feature.issues
        if(uncertain){warnings+=NavigationChartWarning.GEOMETRY_UNCERTAIN;return}
        val cell=dataset.cells.firstOrNull {it.cellId==feature.cellId}
        val depth=feature.depth
        val isInterval=feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DRYING_AREA)&&depth?.kind==DepthEvidenceKind.INTERVAL
        val sourceId="${dataset.id}/${feature.cellId}/"+if(isInterval)"interval"else "objects"
        val source=NavigationChartSource(sourceId,dataset.id,feature.cellId,cell?.sourceName?:feature.cellId,
            if(isInterval)NavigationChartSourceKind.DEPTH_INTERVALS else NavigationChartSourceKind.CHART_OBJECTS,
            verticalReference=depth?.datum,referenceOnly=cell?.referenceOnly==true)
        if(feature.geometry.kind==ChartGeometryKind.LINE&&feature.acronym in setOf("COALNE","SLCONS")) {
            sources[sourceId]=source
            lineRibbon(feature.geometry,0f,(radius*.001).coerceIn(.8,5.0).toFloat(),surface,land.copy(name="measured shoreline",red=.87f,green=.88f,blue=.83f))
        }
        if(feature.geometry.kind==ChartGeometryKind.POLYGON&&(feature.kind==NauticalFeatureKind.LAND||isInterval||feature.acronym in setOf("SLCONS","DOCARE","HRBFAC"))) {
            val shape=polygon(feature.geometry)
            if(shape.isEmpty)return
            if(shape.numPoints>30_000){warnings+=NavigationChartWarning.MODEL_BUDGET;return}
            val elevation=feature.attributes["ELEVAT"]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            val height=if(isInterval)depth?.lowerMeters?.takeIf(Double::isFinite)?.let {-it} ?: return
                else elevation ?: 0.0
            if(isInterval)warnings+=NavigationChartWarning.DEPTH_INTERVALS
            else if(elevation==null)warnings+=NavigationChartWarning.LAND_HEIGHT_UNKNOWN
            sources[sourceId]=source
            val destination=if(height>=0)surface else seabed
            val material=if(height>=0)land else depthMaterial(-height.toFloat())
            triangulate(shape,height.toFloat(),destination,material)
            // 深度区间用上下界侧带表达范围；这是“区间阶地”，没有插值出精确海底。
            if(isInterval)depth?.upperMeters?.takeIf {it.isFinite()&&it>=-height&&it+height>.2}?.let {upper->
                intervalEdges(shape,height.toFloat(),-upper.toFloat(),destination,material.copy(name=material.name+" interval",alpha=.24f))
            }
        }
        val kind=when(feature.kind) {
            NauticalFeatureKind.BEACON->NavigationChartMarkerKind.BEACON
            NauticalFeatureKind.LIGHT->NavigationChartMarkerKind.LIGHT
            NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION->NavigationChartMarkerKind.HAZARD
            NauticalFeatureKind.SOUNDING->NavigationChartMarkerKind.SOUNDING
            else->if(feature.acronym in setOf("HRBFAC","SMCFAC","BERTHS","MORFAC","DOCARE","PILBOP","RTPBCN"))NavigationChartMarkerKind.FACILITY else null
        } ?: return
        if(markers.size>=64){warnings+=NavigationChartWarning.MODEL_BUDGET;return}
        val points=feature.geometry.parts.flatMap {it.points}
        if(points.isEmpty())return
        val p=if(feature.geometry.kind==ChartGeometryKind.POLYGON) {
            val shape=polygon(feature.geometry)
            if(shape.isEmpty)return
            shape.interiorPoint.coordinate.let {coordinate->geo(coordinate.x,coordinate.y)}
        }else points.minByOrNull {hypot(x(it.longitude),z(it.latitude))} ?: return
        val height=if(kind==NavigationChartMarkerKind.SOUNDING) {
            (p.depthMeters ?: depth?.pointMeters?.takeIf {feature.geometry.kind==ChartGeometryKind.POINT})?.let {-it} ?: return
        }else 0.0
        if(!height.isFinite())return
        if(hypot(x(p.longitude),z(p.latitude))>radius)return
        sources[sourceId]=source
        val marker=NavigationChartMarker(feature.id,feature.attributes["NOBJNM"]?.takeIf(String::isNotBlank) ?: feature.attributes["OBJNAM"]?.takeIf(String::isNotBlank) ?: feature.acronym,
            kind,GeoPoint(p.latitude,p.longitude),x(p.longitude),height,z(p.latitude),sourceId,
            symbolic=kind!=NavigationChartMarkerKind.SOUNDING,depthLowerMeters=p.depthMeters ?: depth?.pointMeters ?: depth?.lowerMeters,depthUpperMeters=depth?.upperMeters)
        markers+=marker
        if(kind==NavigationChartMarkerKind.SOUNDING) {
            // 孤立实测点只画定位小标，不在点与点之间补一个没有依据的海底面。
            val size=(radius*.0015).coerceIn(.8,5.0).toFloat()
            val cx=marker.eastMeters.toFloat();val cy=marker.elevationMeters.toFloat();val cz=marker.southMeters.toFloat()
            val top=NavigationTerrainVertex(cx,cy+size,cz);val bottom=NavigationTerrainVertex(cx,cy-size,cz)
            val ring=listOf(NavigationTerrainVertex(cx-size,cy,cz),NavigationTerrainVertex(cx,cy,cz+size),NavigationTerrainVertex(cx+size,cy,cz),NavigationTerrainVertex(cx,cy,cz-size))
            for(i in ring.indices) {seabed.triangle(facility,top,ring[i],ring[(i+1)%4]);seabed.triangle(facility,bottom,ring[(i+1)%4],ring[i])}
            return
        }
        val symbolHeight=(radius*.013).coerceIn(10.0,38.0).toFloat()
        val width=symbolHeight*.16f
        val color=when(feature.attributes["COLOUR"]?.split(',')?.firstOrNull()?.trim()) {
            "3"->NavigationTerrainMaterial("red chart mark",.74f,.28f,.23f)
            "4"->NavigationTerrainMaterial("green chart mark",.27f,.58f,.42f)
            "6"->NavigationTerrainMaterial("yellow chart mark",.79f,.66f,.3f)
            else->if(kind==NavigationChartMarkerKind.HAZARD)hazard else facility
        }
        surface.box(color,marker.eastMeters.toFloat(),0f,marker.southMeters.toFloat(),width,symbolHeight,width)
        if(kind in setOf(NavigationChartMarkerKind.BEACON,NavigationChartMarkerKind.LIGHT)) {
            surface.box(color,marker.eastMeters.toFloat(),symbolHeight*.72f,marker.southMeters.toFloat(),width*2.6f,width*1.4f,width*2.6f)
        }
    }

    private fun triangulate(shape:Geometry,height:Float,builder:NavigationTerrainMeshBuilder,material:NavigationTerrainMaterial) {
        check()
        if(shape.numPoints>30_000){warnings+=NavigationChartWarning.MODEL_BUDGET;return}
        val triangles=PolygonTriangulator.triangulate(shape)
        for(i in 0 until triangles.numGeometries) {
            if(i%128==0)check()
            if(builder.full){warnings+=NavigationChartWarning.MODEL_BUDGET;return}
            val p=triangles.getGeometryN(i).coordinates
            if(p.size<3)continue
            val a=NavigationTerrainVertex(p[0].x.toFloat(),height,p[0].y.toFloat(),0f,1f,0f)
            val b=NavigationTerrainVertex(p[1].x.toFloat(),height,p[1].y.toFloat(),0f,1f,0f)
            val c=NavigationTerrainVertex(p[2].x.toFloat(),height,p[2].y.toFloat(),0f,1f,0f)
            if((b.z-a.z)*(c.x-a.x)-(b.x-a.x)*(c.z-a.z)>0)builder.triangle(material,a,b,c)else builder.triangle(material,a,c,b)
        }
    }

    private fun intervalEdges(shape:Geometry,top:Float,bottom:Float,builder:NavigationTerrainMeshBuilder,material:NavigationTerrainMaterial) {
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
                    val p=NavigationTerrainVertex(a.x.toFloat(),top,a.y.toFloat());val q=NavigationTerrainVertex(b.x.toFloat(),top,b.y.toFloat())
                    val r=NavigationTerrainVertex(a.x.toFloat(),bottom,a.y.toFloat());val s=NavigationTerrainVertex(b.x.toFloat(),bottom,b.y.toFloat())
                    builder.triangle(material.copy(doubleSided=true),p,q,r);builder.triangle(material.copy(doubleSided=true),q,s,r)
                }
            }
        }
    }

    /** 原始岸线仅画窄带，不靠线的包围框补造陆地区域或港池模型。 */
    private fun lineRibbon(geometry:ChartGeometry,height:Float,width:Float,builder:NavigationTerrainMeshBuilder,material:NavigationTerrainMaterial) {
        for(part in geometry.parts) {
            check()
            if(part.points.size<2)continue
            val line=factory.createLineString(part.points.map {Coordinate(x(it.longitude),z(it.latitude))}.toTypedArray()).intersection(windowShape)
            for(i in 0 until line.numGeometries) {
                val section=line.getGeometryN(i) as? LineString ?: continue
                val points=section.coordinates
                for(j in 1 until points.size) {
                    if(j%128==0)check()
                    if(builder.full){warnings+=NavigationChartWarning.MODEL_BUDGET;return}
                    val a=points[j-1];val b=points[j];val length=hypot(b.x-a.x,b.y-a.y)
                    if(length<.001)continue
                    val ox=(-(b.y-a.y)/length*width/2).toFloat();val oz=((b.x-a.x)/length*width/2).toFloat()
                    val p=NavigationTerrainVertex(a.x.toFloat()+ox,height,a.y.toFloat()+oz,0f,1f,0f)
                    val q=NavigationTerrainVertex(b.x.toFloat()+ox,height,b.y.toFloat()+oz,0f,1f,0f)
                    val r=NavigationTerrainVertex(a.x.toFloat()-ox,height,a.y.toFloat()-oz,0f,1f,0f)
                    val s=NavigationTerrainVertex(b.x.toFloat()-ox,height,b.y.toFloat()-oz,0f,1f,0f)
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
        return factory.createMultiPolygon(shells.map {shell->factory.createPolygon(shell.exteriorRing as LinearRing,holes[shell].orEmpty().toTypedArray())}.toTypedArray())
            .also {require(it.isValid){"CHART_TERRAIN_GEOMETRY_INVALID"}}.intersection(windowShape)
    }

    private fun planePart(points:List<NavigationTerrainVertex>,above:Boolean):List<NavigationTerrainVertex> {
        val output=ArrayList<NavigationTerrainVertex>();var a=points.last()
        fun inside(p:NavigationTerrainVertex)=if(above)p.y>=0 else p.y<=0
        for(b in points) {
            if(inside(a)!=inside(b)) {
                val t=(-a.y/(b.y-a.y)).coerceIn(0f,1f)
                output+=NavigationTerrainVertex(a.x+(b.x-a.x)*t,0f,a.z+(b.z-a.z)*t,
                    a.normalX+(b.normalX-a.normalX)*t,a.normalY+(b.normalY-a.normalY)*t,a.normalZ+(b.normalZ-a.normalZ)*t)
            }
            if(inside(b))output+=b
            a=b
        }
        return output
    }
    private fun fan(builder:NavigationTerrainMeshBuilder,material:NavigationTerrainMaterial,points:List<NavigationTerrainVertex>) {
        for(i in 1 until points.size-1)builder.triangle(material,points[0],points[i],points[i+1])
    }
    private fun depthMaterial(depth:Float):NavigationTerrainMaterial {
        val band=when {depth<5->0;depth<15->1;depth<30->2;depth<60->3;depth<150->4;else->5}
        val value=band/5f
        return NavigationTerrainMaterial("depth band $band",.36f-.13f*value,.60f-.19f*value,.64f-.17f*value,roughness=.92f)
    }
    private fun x(longitude:Double)=(((longitude-origin.lon+180.0)%360.0+360.0)%360.0-180.0)*eastScale
    private fun z(latitude:Double)=(origin.lat-latitude)*111_320.0
    private fun geo(east:Double,south:Double)=ChartPoint(origin.lat-south/111_320.0,((origin.lon+east/eastScale+180.0)%360.0+360.0)%360.0-180.0)
}

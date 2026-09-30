package com.yokuli.runtime.marine.chart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.*
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.*

/** 一次生成可离线显示的 MBTiles。原始索引/网格是依据；位图不反向成为规划数据。 */
internal object ChartRasterGenerator {
    private const val TILE=256
    private const val GROUP=4
    private const val PAD=24
    private data class Tile(val z:Int,val x:Int,val y:Int)
    private data class Group(val z:Int,val x:Int,val y:Int)

    fun validate(options:ChartRasterization) {
        require(options.bounds.valid&&options.bounds.north>options.bounds.south&&options.bounds.west!=options.bounds.east){"CHART_RENDER_BOUNDS_INVALID"}
        require(options.tileCount() in 1..ChartRasterization.MAX_TILES){"CHART_RENDER_TILE_LIMIT"}
        require(listOf(options.shallowDepthMeters,options.safetyDepthMeters,options.deepDepthMeters).all {it.isFinite()&&it>=0}&&
            options.shallowDepthMeters<=options.safetyDepthMeters&&options.safetyDepthMeters<=options.deepDepthMeters){"CHART_RENDER_DEPTH_INVALID"}
    }

    suspend fun write(target:File,snapshot:ChartDataSnapshot,options:ChartRasterization,service:ChartDataService,
        rasters:Map<String,RasterBathymetryStore>,check:()->Unit,
        progress:suspend(Long,Long)->Unit,
    ) {
        validate(options);require(snapshot.datasets.size==1&&snapshot.missingDatasetIds.isEmpty()){"CHART_DATASET_MISSING"}
        val data=snapshot.datasets.single()
        val tiles=buildList {
            for(z in options.minZoom..options.maxZoom) {
                val n=1 shl z
                fun x(lon:Double)=floor((lon+180)/360*n).toInt().coerceIn(0,n-1)
                fun y(lat:Double)=floor(mercatorY(lat)*n).toInt().coerceIn(0,n-1)
                for(b in options.bounds.split())for(column in x(b.west)..x(b.east))for(row in y(b.north)..y(b.south))add(Tile(z,column,row))
            }
        }.distinct()
        val groups=tiles.groupBy {Group(it.z,it.x/GROUP*GROUP,it.y/GROUP*GROUP)}
        var done=0L;var nonempty=0L
        SQLiteDatabase.openOrCreateDatabase(target,null).use {db->
            db.rawQuery("PRAGMA journal_mode=DELETE",null).use {require(it.moveToFirst()&&it.getString(0).equals("delete",true)){"CHART_SQLITE_JOURNAL_MODE_FAILED"}}
            db.execSQL("CREATE TABLE metadata(name TEXT PRIMARY KEY,value TEXT NOT NULL)")
            db.execSQL("CREATE TABLE tiles(zoom_level INTEGER NOT NULL,tile_column INTEGER NOT NULL,tile_row INTEGER NOT NULL,tile_data BLOB NOT NULL,PRIMARY KEY(zoom_level,tile_column,tile_row))")
            val metadata=mapOf("name" to data.name,"type" to "overlay","version" to "1.3","description" to "Yokuli generated chart · ${data.name}",
                "format" to "png","scheme" to "tms","minzoom" to options.minZoom.toString(),"maxzoom" to options.maxZoom.toString(),
                "bounds" to with(options.bounds){"$west,$south,$east,$north"},"yokuli.dataset.id" to data.id,"yokuli.dataset.revision" to data.revision.toString(),
                "yokuli.generation" to Gson().toJson(options),"yokuli.generatedAt" to System.currentTimeMillis().toString(),
                "attribution" to data.eligibility.provider.ifBlank {data.name})
            metadata.forEach {(name,value)->db.insertOrThrow("metadata",null,ContentValues().apply {put("name",name);put("value",value)})}
            for((group,groupTiles) in groups) {
                check();currentCoroutineContext().ensureActive()
                val n=1 shl group.z
                val columns=minOf(GROUP,n-group.x);val rows=minOf(GROUP,n-group.y)
                val width=columns*TILE+PAD*2;val height=rows*TILE+PAD*2
                val world=n.toDouble()*TILE
                val left=group.x*TILE.toDouble()-PAD;val top=group.y*TILE.toDouble()-PAD
                // 日期线两侧分组单独读取，边缘缓冲只扩展到有效 Web Mercator 世界。
                val bounds=ChartBounds((left/world*360-180).coerceAtLeast(-180.0),inverseY(((top+height)/world).coerceAtMost(1.0)),
                    ((left+width)/world*360-180).coerceAtMost(180.0),inverseY((top/world).coerceAtLeast(0.0)))
                val features=ArrayList<NauticalFeature>();val seen=HashSet<String>();var vertices=0L
                // 低缩放的合批瓦片可能远大于用户范围，只查询真实导出区域，避免顺带读全国。
                val queryBounds=options.bounds.split().mapNotNull {b->
                    val west=maxOf(b.west,bounds.west);val east=minOf(b.east,bounds.east)
                    val south=maxOf(b.south,bounds.south);val north=minOf(b.north,bounds.north)
                    if(west<=east&&south<=north)ChartBounds(west,south,east,north)else null
                }
                for(queryBoundsPart in queryBounds) {
                    var after:String?=null
                    do {
                        check();val page=service.query(snapshot.id,queryBoundsPart,512,after)
                        require(!page.truncated){"CHART_RENDER_SOURCE_INCOMPLETE"}
                        for(feature in page.features)if(seen.add(feature.id)) {
                            features+=feature;vertices+=feature.geometry.parts.sumOf {it.points.size.toLong()}
                        }
                        require(features.size<=24_000&&vertices<=2_000_000){"CHART_RENDER_AREA_TOO_COMPLEX"}
                        if(!page.hasMore)break
                        val next=page.nextAfterId;require(next!=null&&next!=after){"CHART_RENDER_SOURCE_INCOMPLETE"};after=next
                    }while(true)
                }
                // JTS 在 Core 内只对4×4瓦片合批处理；不向 UI 传输大几何，也不逐帧执行。
                val drawing=ChartDrawingClipper.compose(snapshot,features,bounds)
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                try {
                    val canvas=Canvas(bitmap);val painter=Painter(canvas,width,height,left,top,world,options,check)
                    canvas.clipPath(painter.boundsPath(options.bounds))
                    for(dataset in snapshot.datasets) {
                        val store=rasters[dataset.id]?:continue
                        for(grid in store.grids) {
                            val mask=drawing.rasterMasks["${dataset.id}/${grid.cellId}"]?:continue
                            painter.raster(grid,store,mask)
                        }
                    }
                    painter.features(drawing.features,drawing.boundaries,group.z)
                    // 即使有未解释局部语义也不虚填蓝色；已知真实对象照常绘制。
                    db.beginTransaction()
                    try {
                        for(tile in groupTiles) {
                            check();val image=Bitmap.createBitmap(bitmap,(tile.x-group.x)*TILE+PAD,(tile.y-group.y)*TILE+PAD,TILE,TILE)
                            try {
                                val pixels=IntArray(TILE*TILE);image.getPixels(pixels,0,TILE,0,0,TILE,TILE)
                                if(pixels.any {it ushr 24!=0}) {
                                    val bytes=ByteArrayOutputStream().use {output->require(image.compress(Bitmap.CompressFormat.PNG,100,output)){"CHART_RENDER_IMAGE_FAILED"};output.toByteArray()}
                                    db.insertOrThrow("tiles",null,ContentValues().apply {put("zoom_level",tile.z);put("tile_column",tile.x);put("tile_row",(1 shl tile.z)-1-tile.y);put("tile_data",bytes)})
                                    nonempty++
                                }
                            }finally {image.recycle()}
                            done++;progress(done,tiles.size.toLong())
                        }
                        db.setTransactionSuccessful()
                    }finally {db.endTransaction()}
                }finally {bitmap.recycle()}
            }
            require(nonempty>0){"CHART_RENDER_NO_DATA"};db.rawQuery("PRAGMA optimize",null).use {while(it.moveToNext()) Unit}
        }
    }

    private fun mercatorY(latitude:Double)=(1-asinh(tan(Math.toRadians(latitude.coerceIn(-85.05112878,85.05112878))))/Math.PI)/2
    private fun inverseY(y:Double)=Math.toDegrees(atan(sinh(Math.PI*(1-2*y))))

    private class Painter(val canvas:Canvas,val width:Int,val height:Int,val left:Double,val top:Double,val world:Double,
        val options:ChartRasterization,val check:()->Unit,
    ) {
        private val ink=if(options.night)Color.rgb(186,203,204)else Color.rgb(30,48,57)
        private val land=if(options.night)Color.rgb(44,44,38)else Color.rgb(226,219,184)
        private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=ink;style=Paint.Style.STROKE;strokeWidth=1.1f;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND}
        private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.FILL}
        private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=ink;textSize=12f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
        private val halo=Paint(text).apply {color=if(options.night)Color.rgb(17,27,33)else Color.WHITE;style=Paint.Style.STROKE;strokeWidth=2.5f}
        private val labels=mutableListOf<RectF>()
        private fun x(lon:Double):Float {
            val value=(lon+180)/360*world-left
            return (value+round((width/2.0-value)/world)*world).toFloat()
        }
        private fun y(lat:Double)=(mercatorY(lat)*world-top).toFloat()
        private fun path(geometry:ChartGeometry):Path=Path().apply {
            fillType=Path.FillType.EVEN_ODD
            for(part in geometry.parts) {
                check();if(part.points.isEmpty())continue
                moveTo(x(part.points.first().longitude),y(part.points.first().latitude))
                part.points.drop(1).forEachIndexed {index,p->if(index%2048==0)check();lineTo(x(p.longitude),y(p.latitude))}
                if(geometry.kind==ChartGeometryKind.POLYGON)close()
            }
        }
        fun boundsPath(bounds:ChartBounds):Path=Path().apply {for(b in bounds.split())for(shift in -1..1) {
            val west=(b.west+180)/360*world-left+shift*world
            val east=(b.east+180)/360*world-left+shift*world
            if(east>=0&&west<=width)addRect(west.toFloat(),y(b.north),east.toFloat(),y(b.south),Path.Direction.CW)
        }}
        private fun depthColor(depth:Double):Int {
            val colors=if(options.night)intArrayOf(0xff18383f.toInt(),0xff153038.toInt(),0xff102730.toInt(),0xff0b1c25.toInt())
                else intArrayOf(0xff99cedb.toInt(),0xffc0e1e7.toInt(),0xffe1f0f2.toInt(),0xfff7fbfb.toInt())
            return colors[when {depth<options.shallowDepthMeters->0;depth<options.safetyDepthMeters->1;depth<options.deepDepthMeters->2;else->3}]
        }
        fun raster(grid:RasterBathymetryGrid,store:RasterBathymetryStore,mask:ChartGeometry) {
            check();val pixels=IntArray(width*height)
            // 只读取每条实际需要的源像元行；显示允许最近像元抽样，绝不回灌查询/规划。
            val columns=IntArray(width){-1}
            // pixelAt 同时检查纬度，因此列索引使用网格内纬度。
            for(px in columns.indices)columns[px]=grid.pixelAt(ChartPoint(grid.northEdge-grid.pixelHeightDegrees*.5,
                ((left+px+.5)/world*360+180)%360-180))?.first?:-1
            val min=columns.filter {it>=0}.minOrNull()?:return;val max=columns.maxOrNull()?:return
            var lastRow=-1;var values:FloatArray?=null
            for(py in 0 until height) {
                check();val lat=inverseY((top+py+.5)/world)
                val row=grid.pixelAt(ChartPoint(lat,grid.westEdge+grid.pixelWidthDegrees*.5))?.second?:continue
                if(row!=lastRow){values=store.readWindow(grid.id,min,row,max-min+1,1,check).elevationMeters;lastRow=row}
                val data=requireNotNull(values)
                for(px in 0 until width) {
                    val col=columns[px];if(col<0)continue
                    val elevation=data[col-min];if(!elevation.isFinite())continue
                    pixels[py*width+px]=if(elevation>=0)land else depthColor(-elevation.toDouble())
                }
            }
            val image=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
            try {canvas.save();canvas.clipPath(path(mask));canvas.drawBitmap(image,0f,0f,null);canvas.restore()}finally {image.recycle()}
        }
        fun features(features:List<NauticalFeature>,boundaries:Map<String,ChartGeometry>,zoom:Int) {
            fun rank(f:NauticalFeature)=when(f.kind){NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->0;NauticalFeatureKind.DRYING_AREA->1;NauticalFeatureKind.LAND->2;NauticalFeatureKind.DEPTH_CONTOUR->3;else->4}
            for(f in features.sortedWith(compareBy<NauticalFeature>{rank(it)}.thenBy {it.id})) {
                check();if(f.kind in setOf(NauticalFeatureKind.COVERAGE,NauticalFeatureKind.QUALITY)||f.geometry.kind==ChartGeometryKind.NONE)continue
                if(f.hasUncertainChartGeometry())continue
                val shape=path(f.geometry);val edge=boundaries[f.id]?.let(::path)?:shape
                val area=f.geometry.kind==ChartGeometryKind.POLYGON
                when(f.kind) {
                    NauticalFeatureKind.LAND->{fill.color=land;if(area)canvas.drawPath(shape,fill);line.color=ink;line.strokeWidth=1.2f;canvas.drawPath(edge,line)}
                    NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->{val depth=f.depth?.lowerMeters?:f.depth?.pointMeters
                        if(depth!=null&&area){fill.color=depthColor(depth);canvas.drawPath(shape,fill)}}
                    NauticalFeatureKind.DRYING_AREA->{fill.color=if(options.night)0xff344a3d.toInt()else 0xffb9ceb0.toInt();if(area)canvas.drawPath(shape,fill);line.color=ink;line.strokeWidth=.7f;canvas.drawPath(edge,line)}
                    NauticalFeatureKind.DEPTH_CONTOUR->{line.color=if(options.night)0xff527a87.toInt()else 0xff6396a4.toInt();line.strokeWidth=if((f.depth?.pointMeters?:f.depth?.lowerMeters?:-1.0)==options.safetyDepthMeters)1.8f else .65f;canvas.drawPath(shape,line)}
                    NauticalFeatureKind.SOUNDING->if(zoom>=12)for(p in f.geometry.parts.flatMap {it.points}) {
                        val value=p.depthMeters?:f.depth?.pointMeters?:continue
                        label(formatDepth(value),x(p.longitude),y(p.latitude),center=true)
                    }
                    NauticalFeatureKind.BEACON,NauticalFeatureKind.LIGHT,NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK-> {
                        if(area){line.color=ink;line.strokeWidth=.8f;canvas.drawPath(edge,line)}
                        if(zoom>=10)f.geometry.parts.firstOrNull()?.points?.firstOrNull()?.let {p->symbol(f.kind,x(p.longitude),y(p.latitude))}
                    }
                    else->{line.color=ink;line.strokeWidth=.8f
                        if(f.kind==NauticalFeatureKind.RESTRICTED)line.pathEffect=DashPathEffect(floatArrayOf(5f,4f),0f)
                        if(f.geometry.kind==ChartGeometryKind.LINE||area)canvas.drawPath(edge,line)
                        line.pathEffect=null}
                }
                if(zoom>=11&&f.kind !in setOf(NauticalFeatureKind.SOUNDING,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DEPTH_CONTOUR)) {
                    val name=f.attributes["OBJNAM"]?:f.attributes["NOBJNM"]?:f.attributes["name"]?:f.attributes["NAME"]
                    val point=f.geometry.parts.firstOrNull()?.points?.firstOrNull()
                    if(!name.isNullOrBlank()&&point!=null)label(name.take(48),x(point.longitude)+7,y(point.latitude)-7)
                }
            }
        }
        private fun symbol(kind:NauticalFeatureKind,x:Float,y:Float) {
            if(x !in -12f..width+12f||y !in -12f..height+12f)return
            line.color=ink;line.strokeWidth=1.4f;fill.color=ink
            when(kind) {
                NauticalFeatureKind.BEACON->{canvas.drawPath(Path().apply {moveTo(x,y-6);lineTo(x-4,y+3);lineTo(x+4,y+3);close()},line);canvas.drawLine(x-5,y+6,x+5,y+6,line)}
                NauticalFeatureKind.LIGHT->{canvas.drawCircle(x,y,2.5f,fill);for(a in 0 until 8){val r=a*Math.PI/4;canvas.drawLine(x+cos(r).toFloat()*5,y+sin(r).toFloat()*5,x+cos(r).toFloat()*8,y+sin(r).toFloat()*8,line)}}
                else->{canvas.drawCircle(x,y,5f,line);canvas.drawLine(x-3,y-3,x+3,y+3,line);canvas.drawLine(x+3,y-3,x-3,y+3,line)}
            }
        }
        private fun label(value:String,x:Float,y:Float,center:Boolean=false) {
            if(x<0||y<0||x>=width||y>=height)return
            val w=text.measureText(value);val origin=if(center)x-w/2 else x
            val box=RectF(origin-2,y-text.textSize-2,origin+w+2,y+3)
            if(labels.any {RectF.intersects(it,box)}||labels.size>=2000)return
            labels+=box;canvas.drawText(value,origin,y,halo);canvas.drawText(value,origin,y,text)
        }
        private fun formatDepth(value:Double)=if(abs(value-round(value))<.05)round(value).toLong().toString()else String.format(Locale.ROOT,"%.1f",value)
    }
}

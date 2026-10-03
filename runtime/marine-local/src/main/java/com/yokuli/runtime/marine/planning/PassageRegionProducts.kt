package com.yokuli.runtime.marine.planning

import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import com.yokuli.runtime.contract.planning.PASSAGE_RULES_VERSION
import kotlinx.coroutines.*
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter
import java.io.*
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.*

/** 固定地理分区，不依赖地图缩放；日期线的相邻分区仍相连。 */
internal data class PassageRegionId(val x:Int,val y:Int) {
    val west get()=x*STEP-180.0
    val south get()=y*STEP-90.0
    val bounds get()=ChartBounds(west,south,west+STEP,south+STEP)
    val center get()=ChartPoint(south+STEP/2,west+STEP/2)
    fun neighbor(edge:Int):PassageRegionId?=when(edge) {
        0->copy(x=(x+COLUMNS-1)%COLUMNS)
        1->copy(x=(x+1)%COLUMNS)
        2->if(y>0)copy(y=y-1)else null
        else->if(y<ROWS-1)copy(y=y+1)else null
    }
    fun point(edge:Int,value:Double):ChartPoint=when(edge) {
        0->ChartPoint(value,west)
        1->ChartPoint(value,if(west+STEP>=180) -180.0 else west+STEP)
        2->ChartPoint(south,value)
        else->ChartPoint(south+STEP,value)
    }
    companion object {
        const val STEP=.125
        const val COLUMNS=2880
        const val ROWS=1440
        fun at(point:ChartPoint)=PassageRegionId(floor((point.longitude+180)/STEP).toInt().coerceIn(0,COLUMNS-1),floor((point.latitude+90)/STEP).toInt().coerceIn(0,ROWS-1))
        fun intersecting(bounds:ChartBounds):List<PassageRegionId> {
            require(bounds.valid){"NAVIGATION_REGION_INVALID"}
            val result=linkedSetOf<PassageRegionId>()
            for(box in bounds.split()) {
                val a=at(ChartPoint(box.south,box.west));val b=at(ChartPoint(box.north,box.east))
                require((b.x-a.x+1L)*(b.y-a.y+1L)<=32_768){"准备区域过大，请分区准备 / Prepare a smaller region"}
                for(y in a.y..b.y)for(x in a.x..b.x)result+=PassageRegionId(x,y)
            }
            require(result.size<=32_768){"NAVIGATION_REGION_LIMIT"}
            return result.toList()
        }
    }
}
/** 同一分量到边界的真实连续水域区间，不以粗格两侧有水推定可穿越。 */
internal data class PassagePortal(val component:Int,val edge:Int,val lower:Double,val upper:Double)
internal data class PassageRegionEvidence(val featureId:String,val cellId:String,val depth:DepthEvidence?)
internal data class PassageConstraintHeader(val kind:PassageConstraintKind,val featureId:String,val cellId:String,val minimumMeters:Double?,val datumKnown:Boolean)
internal data class PassageRasterHeader(val grid:RasterBathymetryGrid,val column:Int,val row:Int,val width:Int,val height:Int)
internal data class PassageRegionHeader(
    val schema:Int=3,val source:String,val policy:String,val rules:String=PASSAGE_RULES_VERSION,
    val region:PassageRegionId,val portals:List<PassagePortal>,val componentCount:Int,
    val evidence:List<PassageRegionEvidence>,val malformed:List<String>,val margin:Double,
    val constraints:List<PassageConstraintHeader> = emptyList(),val rasters:List<PassageRasterHeader> = emptyList(),
)
internal data class PassageRegionProduct(val header:PassageRegionHeader,val components:List<Geometry>,val unknownDepth:List<Geometry>,val waterWithHalo:Geometry,
    val semantics:PassageSemanticRegion,val mesh:PassageNavigationMesh) {
    private val unknownIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED){org.locationtech.jts.index.strtree.STRtree().apply{
        unknownDepth.forEachIndexed{index,shape->insert(shape.envelopeInternal,index)};build()
    }}
    fun unknownAlong(corridor:Geometry):List<Int> = unknownIndex.query(corridor.envelopeInternal).map{it as Int}
    val preparedWater by lazy(LazyThreadSafetyMode.SYNCHRONIZED){org.locationtech.jts.geom.prep.PreparedGeometryFactory.prepare(waterWithHalo)}
    val estimatedBytes:Long by lazy(LazyThreadSafetyMode.SYNCHRONIZED){(waterWithHalo.numPoints.toLong()+components.sumOf{it.numPoints.toLong()}+unknownDepth.sumOf{it.numPoints.toLong()}+
        semantics.constraints.sumOf{it.geometry.numPoints.toLong()}+semantics.rasters.sumOf{it.mask.numPoints.toLong()})*64L+
        semantics.rasters.sumOf{it.source.window.elevationMeters.size*4L}+mesh.estimatedBytes+header.evidence.size*1024L+4096L}
    fun world(check:()->Unit):PassageWorld {
        val projection=PassageProjection(header.region.center,check)
        val empty=projection.factory.createPolygon()
        val water=waterWithHalo
        return PassageWorld(projection,emptyList(),water,water,header.malformed,header.margin,empty,emptyList(),emptyList(),empty,
            searchBounds=water.envelopeInternal)
    }
}

/**
 * 冻结数据版本目录下的编译产物。基础 policy 与船型无关；船型/避让派生产物独立键。
 * 每个文件含可先读的轻量拓扑头、精确 WKB 分量和未知深度面；原子发布，损坏可重建。
 */
internal class PassageRegionProducts(private val root:File) {
    private val gson=Gson()
    private fun target(key:String)=File(root,"$key.nav")
    fun key(source:String,policy:String,id:PassageRegionId)=passageHash(listOf("navigation-region-3",source,policy,id,PASSAGE_RULES_VERSION))

    suspend fun readHeader(key:String,source:String,policy:String,id:PassageRegionId):PassageRegionHeader?=withContext(Dispatchers.IO) {
        val file=target(key)
        if(!file.isFile)return@withContext null
        try {FileInputStream(file).buffered().use{readHeader(DataInputStream(it),source,policy,id)}}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){null}
    }
    private fun readHeader(input:DataInputStream,source:String,policy:String,id:PassageRegionId):PassageRegionHeader {
        VirtualHostServices.beforeRead()
        require(input.readInt()==MAGIC&&input.readInt()==3){"NAVIGATION_PRODUCT_VERSION"}
        val size=input.readInt();require(size in 1..MAX_HEADER)
        val bytes=ByteArray(size);input.readFully(bytes)
        require(input.readLong()==CRC32().apply{update(bytes)}.value){"NAVIGATION_PRODUCT_HEADER_CHECKSUM"}
        val value=gson.fromJson(String(bytes,Charsets.UTF_8),PassageRegionHeader::class.java)
        require(value.schema==3&&value.source==source&&value.policy==policy&&value.rules==PASSAGE_RULES_VERSION&&value.region==id)
        require(value.componentCount in 0..4096&&value.evidence.size<=8192&&value.portals.size<=16384&&value.constraints.size<=120_000&&value.rasters.size<=256)
        require(value.constraints.all{it.minimumMeters==null||it.minimumMeters.isFinite()&&it.minimumMeters>=0})
        require(value.rasters.all{it.width>0&&it.height>0&&it.width.toLong()*it.height<=262_144&&it.column>=0&&it.row>=0&&it.column.toLong()+it.width<=it.grid.width&&it.row.toLong()+it.height<=it.grid.height})
        require(value.portals.all{it.component in 0 until value.componentCount&&it.edge in 0..3&&it.lower.isFinite()&&it.upper.isFinite()&&it.lower<=it.upper})
        return value
    }
    suspend fun read(key:String,source:String,policy:String,id:PassageRegionId):PassageRegionProduct?=withContext(Dispatchers.IO) {
        val file=target(key)
        if(!file.isFile)return@withContext null
        val work=currentCoroutineContext();work.ensureActive();VirtualHostServices.beforeRead()
        val cacheKey=file.absolutePath+":"+file.length()+":"+file.lastModified()
        synchronized(cache){cache[cacheKey]}?.let{return@withContext it}
        try {
            require(file.length() in 20..MAX_FILE)
            FileInputStream(file).buffered().use {stream->
                val checked=CheckedInputStream(stream,CRC32());val input=DataInputStream(checked)
                val header=readHeader(input,source,policy,id)
                val reader=WKBReader();var total=0L
                val geometries=ArrayList<Geometry>()
                fun geometry():Geometry {
                    work.ensureActive();VirtualHostServices.beforeRead()
                    val size=input.readInt()
                    if(size<0){val index=-(size.toLong()+1);require(index in 0 until geometries.size.toLong()){"NAVIGATION_GEOMETRY_REFERENCE"};return geometries[index.toInt()]}
                    require(size in 1..MAX_GEOMETRY)
                    val rawSize=input.readInt();require(rawSize in 1..MAX_GEOMETRY);total+=rawSize;require(total<=MAX_FILE)
                    val packed=ByteArray(size);input.readFully(packed)
                    val bytes=ByteArray(rawSize)
                    InflaterInputStream(ByteArrayInputStream(packed)).use {source->
                        var offset=0;while(offset<bytes.size){work.ensureActive();val n=source.read(bytes,offset,minOf(64*1024,bytes.size-offset));require(n>0){"NAVIGATION_GEOMETRY_TRUNCATED"};offset+=n}
                        require(source.read()==-1){"NAVIGATION_GEOMETRY_SIZE"}
                    }
                    validateWkb(bytes){work.ensureActive()}
                    return reader.read(bytes).also{require(it.numPoints<=1_000_000&&it.isValid);geometries+=it}
                }
                val water=geometry()
                val components=List(header.componentCount){geometry()}
                val unknown=List(header.evidence.size){geometry()}
                val constraints=header.constraints.map {item->PassageConstraint(item.kind,item.featureId,item.cellId,item.minimumMeters,item.datumKnown,geometry())}
                val rasters=header.rasters.map {item->
                    val mask=geometry();val count=item.width*item.height
                    total+=count*4L;require(total<=MAX_FILE)
                    val samples=FloatArray(count){i->if(i%1024==0)work.ensureActive();input.readFloat().also{require(!it.isInfinite()){"NAVIGATION_RASTER_VALUE"}}}
                    PassageSemanticRaster(ChartRasterWindow(item.grid,RasterBathymetryWindow(item.grid.id,item.column,item.row,item.width,item.height,samples)),mask)
                }
                val mesh=PassageNavigationMesh.read(input){work.ensureActive()}
                val checksum=checked.checksum.value
                require(input.readLong()==checksum&&input.read()==-1){"NAVIGATION_PRODUCT_CHECKSUM"}
                PassageRegionProduct(header,components,unknown,water,PassageSemanticRegion(constraints,rasters),mesh).also{retain(cacheKey,it)}
            }
        }catch(cancel:CancellationException){throw cancel}
        catch(error:IOException){throw error}
        catch(error:IllegalArgumentException){file.delete();null}
        catch(error:org.locationtech.jts.io.ParseException){file.delete();null}
        catch(error:com.google.gson.JsonParseException){file.delete();null}
    }
    suspend fun write(key:String,product:PassageRegionProduct)=withContext(Dispatchers.IO) {
        val work=currentCoroutineContext();work.ensureActive();VirtualHostServices.beforeWrite()
        root.mkdirs();val stage=File(root,".${UUID.randomUUID()}.pending")
        try {
            val header=gson.toJson(product.header).toByteArray(Charsets.UTF_8);require(header.size<=MAX_HEADER)
            FileOutputStream(stage).use {file->
                val buffer=file.buffered();val checked=CheckedOutputStream(buffer,CRC32());val out=DataOutputStream(checked)
                out.writeInt(MAGIC);out.writeInt(3);out.writeInt(header.size);out.write(header)
                out.writeLong(CRC32().apply{update(header)}.value)
                val writer=WKBWriter();var total=header.size.toLong()+28L;var rawTotal=0L
                val geometries=HashMap<String,Int>()
                fun geometry(shape:Geometry) {
                    work.ensureActive();VirtualHostServices.beforeWrite()
                    val bytes=writer.write(shape);require(bytes.size<=MAX_GEOMETRY)
                    val identity=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
                    val existing=geometries[identity]
                    if(existing!=null){out.writeInt(-existing-1);total+=4;return}
                    geometries[identity]=geometries.size
                    rawTotal+=bytes.size;require(rawTotal<=MAX_FILE)
                    val buffer=ByteArrayOutputStream();val compressor=Deflater(Deflater.BEST_SPEED)
                    try{DeflaterOutputStream(buffer,compressor).use{stream->
                        var offset=0;while(offset<bytes.size){work.ensureActive();val count=minOf(64*1024,bytes.size-offset);stream.write(bytes,offset,count);offset+=count}
                    }}finally{compressor.end()}
                    val packed=buffer.toByteArray();require(packed.size<=MAX_GEOMETRY);total+=packed.size+8;require(total<=MAX_FILE)
                    out.writeInt(packed.size);out.writeInt(bytes.size);out.write(packed)
                }
                for(shape in listOf(product.waterWithHalo)+product.components+product.unknownDepth)geometry(shape)
                product.semantics.constraints.forEach{geometry(it.geometry)}
                product.semantics.rasters.forEach {item->
                    geometry(item.mask);total+=item.source.window.elevationMeters.size*4L;require(total<=MAX_FILE)
                    item.source.window.elevationMeters.forEachIndexed{i,value->if(i%1024==0)work.ensureActive();out.writeFloat(value)}
                }
                total+=product.mesh.bytes+8;require(total<=MAX_FILE)
                product.mesh.write(out){work.ensureActive()}
                val checksum=checked.checksum.value;out.writeLong(checksum);out.flush();file.fd.sync()
            }
            work.ensureActive();VirtualHostServices.beforeWrite()
            val destination=target(key)
            require(stage.renameTo(destination)){"NAVIGATION_PRODUCT_PUBLISH_FAILED"}
            retain(destination.absolutePath+":"+destination.length()+":"+destination.lastModified(),product)
        }finally {stage.delete()}
    }
    /** 导入产物在交给 JTS 分配坐标数组前先检查长度/嵌套，损坏计数不能触发无界内存申请。 */
    private fun validateWkb(bytes:ByteArray,check:()->Unit) {
        val input=java.nio.ByteBuffer.wrap(bytes);var points=0L;var shapes=0
        fun count():Int=input.int.also{require(it>=0&&it<=1_000_000){"NAVIGATION_WKB_COUNT"}}
        fun coordinates(n:Int) {
            points+=n;require(points<=1_000_000&&n.toLong()*16<=input.remaining()){"NAVIGATION_WKB_SIZE"}
            repeat(n){if(it%1024==0)check();require(input.double.isFinite()&&input.double.isFinite()){"NAVIGATION_WKB_COORDINATE"}}
        }
        fun shape(depth:Int) {
            check();require(depth<=16&&++shapes<=100_000){"NAVIGATION_WKB_NESTING"}
            input.order(when(input.get().toInt()){0->java.nio.ByteOrder.BIG_ENDIAN;1->java.nio.ByteOrder.LITTLE_ENDIAN;else->error("NAVIGATION_WKB_BYTE_ORDER")})
            when(input.int) {
                1->coordinates(1)
                2->coordinates(count())
                3->{val rings=count();require(rings.toLong()*4<=input.remaining());repeat(rings){coordinates(count())}}
                4,5,6,7->{val children=count();require(children.toLong()*5<=input.remaining());repeat(children){shape(depth+1)}}
                else->error("NAVIGATION_WKB_TYPE")
            }
        }
        shape(0);require(!input.hasRemaining()){"NAVIGATION_WKB_TRAILING_DATA"}
    }
    companion object {
        private val cache=LinkedHashMap<String,PassageRegionProduct>(16,.75f,true)
        private fun retain(key:String,product:PassageRegionProduct)=synchronized(cache) {
            val bytes=product.estimatedBytes
            if(bytes>96L*1024*1024)return@synchronized
            cache.remove(key)
            while(cache.isNotEmpty()&&cache.values.sumOf{it.estimatedBytes}+bytes>96L*1024*1024){val it=cache.entries.iterator();it.next();it.remove()}
            cache[key]=product
        }
        private const val MAGIC=0x594b4e31
        private const val MAX_HEADER=8*1024*1024
        private const val MAX_GEOMETRY=24*1024*1024
        private const val MAX_FILE=64L*1024*1024
    }
}

package com.yokuli.runtime.marine.chart

import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** 只在未发布的版本目录内导入；发布/移除及快照租约仍归 ChartDataService。 */
internal object RasterBathymetryImporter {
    const val MANIFEST="raster-bathymetry.json"
    private val productPattern=Regex("GEBCO[_ -]?(20[0-9]{2})",RegexOption.IGNORE_CASE)
    private val wrongProduct=Regex("(^|[^a-z])(tid|type[ _-]*identifier|colour|color|hillshade|shaded[ _-]*relief)([^a-z]|$)",RegexOption.IGNORE_CASE)
    fun accepts(file:File)=file.extension.lowercase(Locale.ROOT) in setOf("tif","tiff","asc","ascii")

    suspend fun prepare(files:List<File>,stage:File,datasetId:String,declaredProduct:String?=null,check:()->Unit,
        sourceIdentity:(File)->String={it.name},
        preserveSource:Boolean=false,
        declaredProductForSource:(File)->String?={declaredProduct},
        progress:suspend(done:Int,total:Int,detail:String)->Unit={_,_,_->},
    ):List<ChartCellRevision> = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()&&files.size<=256) {"GEBCO_FILE_COUNT_LIMIT"}
        require(datasetId.isNotBlank()&&datasetId.length<=256) {"GEBCO_DATASET_ID_INVALID"}
        require(stage.isDirectory||stage.mkdirs()) {"CHART_STORAGE_FULL"}
        require(!File(stage,MANIFEST).exists()) {"GEBCO_STAGE_ALREADY_INDEXED"}
        val entries=ArrayList<RasterFileEntry>()
        for((index,source) in files.withIndex()) {
            check();require(source.isFile&&source.length() in 16..32_000_000_000L) {"GEBCO_FILE_SIZE_LIMIT"}
            require(accepts(source)) {"GEBCO_FORMAT_UNSUPPORTED_USE_DATA_GEOTIFF_OR_ESRI_ASCII"}
            require(!wrongProduct.containsMatchIn(source.name)) {"GEBCO_TID_OR_IMAGE_IS_NOT_ELEVATION"}
            val identity=sourceIdentity(source)
            val sourceName=identity.removePrefix("files/").replace(Regex("^[0-9A-Fa-f-]{36}_"),"")
            val cell="GEBCO_${UUID.nameUUIDFromBytes(identity.toByteArray(StandardCharsets.UTF_8))}"
            val base="$datasetId/$cell"
            progress(index,files.size,source.name)
            val entry=if(source.extension.lowercase(Locale.ROOT) in setOf("tif","tiff")) {
                val info=GebcoTiff.open(source,check).use {tiff->
                    val product=identifyProduct(source.name+" "+tiff.description,declaredProductForSource(source))
                    // Every encoded block has a checked range. Decode representative blocks before publishing.
                    tiff.readWindow(0,0,minOf(16,tiff.width),minOf(16,tiff.height),check)
                    tiff.readWindow(tiff.width-1,tiff.height-1,1,1,check)
                    tiff.grid(base,datasetId,cell,product,sourceName)
                }
                val target=File(stage,"$cell.tif")
                if(source.canonicalFile!=target.canonicalFile) {
                    require(!target.exists()) {"GEBCO_DUPLICATE_SOURCE_NAME"}
                    // SAF 副本已在未发布的 stage 中；同盘移动避免为大型全球瓦片再占一份空间。
                    val staged=source.canonicalPath.startsWith(stage.canonicalPath+File.separator)
                    if(preserveSource||!staged||!source.renameTo(target))copyChecked(source,target,check)
                    RandomAccessFile(target,"rw").use{it.fd.sync()}
                }
                RasterFileEntry(info,target.name,"TIFF",target.length())
            } else {
                val product=identifyProduct(source.name,declaredProductForSource(source))
                prepareAscii(source,File(stage,"$cell.f32"),base,datasetId,cell,product,check).let {entry->entry.copy(grid=entry.grid.copy(sourceName=sourceName))}
            }
            require(entries.none{it.grid.id==entry.grid.id}) {"GEBCO_DUPLICATE_SOURCE_NAME"}
            entries+=entry
            progress(index+1,files.size,source.name)
        }
        check()
        val encoded=Gson().toJson(RasterManifest(1,entries)).toByteArray(StandardCharsets.UTF_8)
        require(encoded.size<=4_000_000) {"GEBCO_METADATA_SIZE_LIMIT"}
        FileOutputStream(File(stage,MANIFEST)).use {it.write(encoded);it.fd.sync()}
        check()
        entries.map {entry->ChartCellRevision(entry.grid.cellId,edition=1,update=0,intendedUsage=0,compilationScale=null,
            issueDate=Regex("20[0-9]{2}").find(entry.grid.product)?.value,
            bounds=entry.grid.bounds,quality=listOf("GEBCO_REFERENCE_GRID","RESOLUTION_DEGREES=${entry.grid.pixelWidthDegrees}"),
            featureCount=0,referenceOnly=true,sourceName=entry.grid.sourceName,issues=listOf("REFERENCE_ONLY_GEBCO","REFERENCE_ONLY_GEBCO_NOT_FOR_NAVIGATION","REFERENCE_ONLY_NO_OBSTRUCTION_OR_LEGAL_COVERAGE"))}
    }

    private fun identifyProduct(evidence:String,declaration:String?):String {
        require(!wrongProduct.containsMatchIn(evidence)) {"GEBCO_TID_OR_IMAGE_IS_NOT_ELEVATION"}
        val detected=productPattern.find(evidence)?.groupValues?.get(1)
        val declared=declaration?.let{productPattern.find(it)?.groupValues?.get(1)}
        require(detected!=null||declared!=null) {"GEBCO_PRODUCT_DECLARATION_REQUIRED"}
        require(detected==null||declared==null||detected==declared) {"GEBCO_PRODUCT_DECLARATION_CONFLICT"}
        return "GEBCO_${detected?:declared}_Grid"
    }

    private fun copyChecked(source:File,target:File,check:()->Unit) {
        val expected=source.length();var count=0L
        FileInputStream(source).use {input->FileOutputStream(target).use {output->
            val buffer=ByteArray(256*1024)
            while(true){check();val read=input.read(buffer);if(read<0)break;output.write(buffer,0,read);count+=read;require(count<=expected) {"GEBCO_SOURCE_CHANGED"}}
            require(count==expected) {"GEBCO_SOURCE_CHANGED"};output.fd.sync()
        }}
        require(target.length()==expected) {"CHART_STORAGE_FULL"}
    }

    private fun prepareAscii(file:File,target:File,id:String,datasetId:String,cell:String,product:String,check:()->Unit):RasterFileEntry {
        require(!target.exists()) {"GEBCO_STAGE_ALREADY_INDEXED"}
        AsciiTokens(file,check).use {input->
            val fields=linkedMapOf<String,String>();var firstSample:String?=null
            while(fields.size<8) {
                check();val token=input.next()?:error("GEBCO_ASCII_HEADER_MISSING")
                val name=token.lowercase(Locale.ROOT)
                if(name !in setOf("ncols","nrows","xllcorner","yllcorner","xllcenter","yllcenter","cellsize","nodata_value")){firstSample=token;break}
                require(name !in fields) {"GEBCO_ASCII_HEADER_DUPLICATE"}
                fields[name]=input.next()?:error("GEBCO_ASCII_HEADER_MISSING")
            }
            fun number(name:String)=fields[name]?.toDoubleOrNull()?.takeIf(Double::isFinite)?:error("GEBCO_ASCII_HEADER_INVALID:$name")
            val width=number("ncols").let{require(it in 1.0..100_000.0&&it%1.0==0.0){"GEBCO_DIMENSIONS_INVALID"};it.toInt()}
            val height=number("nrows").let{require(it in 1.0..100_000.0&&it%1.0==0.0){"GEBCO_DIMENSIONS_INVALID"};it.toInt()}
            require(width.toLong()*height<=4_000_000_000L) {"GEBCO_DIMENSIONS_INVALID"}
            val spacing=number("cellsize");require(spacing>0&&abs(spacing-1.0/240.0)<1e-8) {"GEBCO_EXPECTED_15_ARCSECOND_GRID"}
            require(("xllcorner" in fields) xor ("xllcenter" in fields)) {"GEBCO_ASCII_REGISTRATION_INVALID"}
            require(("yllcorner" in fields) xor ("yllcenter" in fields)) {"GEBCO_ASCII_REGISTRATION_INVALID"}
            val west=if("xllcorner" in fields)number("xllcorner")else number("xllcenter")-spacing/2
            val south=if("yllcorner" in fields)number("yllcorner")else number("yllcenter")-spacing/2
            val noData=fields["nodata_value"]?.let{parseRasterNumber(it).takeIf(Double::isFinite)}
            val grid=validatedGrid(id,datasetId,cell,width,height,west,south+height*spacing,spacing,spacing,noData,product,"ESRI_PIXEL_CENTRE",file.name.replace(Regex("^[0-9A-Fa-f-]{36}_"),""))
            val row=ByteBuffer.allocate(width*4).order(ByteOrder.LITTLE_ENDIAN)
            FileOutputStream(target).use {output->
                for(y in 0 until height) {
                    check();row.clear()
                    for(x in 0 until width) {
                        if(x%4096==0)check()
                        val token=firstSample?.also{firstSample=null}?:input.next()?:error("GEBCO_ASCII_TRUNCATED")
                        val value=parseRasterNumber(token)
                        row.putFloat(normalizeElevation(value,noData))
                    }
                    output.write(row.array())
                }
                require(input.next()==null) {"GEBCO_ASCII_EXTRA_VALUES"};output.fd.sync()
            }
            require(target.length()==width.toLong()*height*4) {"CHART_STORAGE_FULL"}
            return RasterFileEntry(grid,target.name,"FLOAT32_LE",target.length())
        }
    }
}

internal data class RasterManifest(val version:Int,val files:List<RasterFileEntry>)
internal data class RasterFileEntry(val grid:RasterBathymetryGrid,val file:String,val encoding:String,val size:Long)

/** 每个读取实例都有自己的文件句柄；由快照租约中的 use 释放，不保留指向已替换数据目录的全局缓存。 */
internal class RasterBathymetryStore private constructor(private val directory:File,private val entries:List<RasterFileEntry>):Closeable {
    val grids:List<RasterBathymetryGrid> = entries.map{it.grid}
    // 一个窗口请求可能经过很多文件；只保留当前文件的块缓存，不能每份资料各累积 16 MiB。
    private var readerId:String?=null
    private var reader:Closeable?=null
    private fun readerFor(id:String,open:()->Closeable):Closeable {
        if(readerId==id)return requireNotNull(reader)
        reader?.close();reader=null;readerId=null
        return open().also{reader=it;readerId=id}
    }
    private var closed=false
    @Synchronized fun readWindow(gridId:String,column:Int,row:Int,width:Int,height:Int,check:()->Unit={}):RasterBathymetryWindow {
        check();require(!closed){"GEBCO_READER_CLOSED"}
        val entry=entries.firstOrNull{it.grid.id==gridId}?:error("GEBCO_GRID_NOT_FOUND")
        val grid=entry.grid
        require(column>=0&&row>=0&&width>0&&height>0&&column.toLong()+width<=grid.width&&row.toLong()+height<=grid.height&&width.toLong()*height<=1_048_576L) {"GEBCO_WINDOW_LIMIT"}
        val data=if(entry.encoding=="TIFF") {
            val tiff=readerFor(gridId){GebcoTiff.open(File(directory,entry.file),check)} as GebcoTiff
            tiff.readWindow(column,row,width,height,check)
        } else {
            val file=readerFor(gridId){RandomAccessFile(File(directory,entry.file),"r")} as RandomAccessFile
            val output=FloatArray(width*height);val bytes=ByteArray(width*4)
            for(y in 0 until height){check();file.seek(((row+y).toLong()*grid.width+column)*4);file.readFully(bytes)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(output,y*width,width)}
            output
        }
        return RasterBathymetryWindow(gridId,column,row,width,height,data)
    }
    fun sample(gridId:String,point:ChartPoint,check:()->Unit={}):Float? {
        val grid=grids.firstOrNull{it.id==gridId}?:error("GEBCO_GRID_NOT_FOUND")
        val pixel=grid.pixelAt(point)?:return null
        return readWindow(gridId,pixel.first,pixel.second,1,1,check).elevationAt(0,0)
    }
    @Synchronized override fun close(){if(!closed){closed=true;try{reader?.close()}finally{reader=null;readerId=null}}}
    companion object {
        fun open(directory:File):RasterBathymetryStore {
            val manifest=File(directory,RasterBathymetryImporter.MANIFEST)
            require(manifest.isFile&&manifest.length() in 1..4_000_000) {"GEBCO_METADATA_MISSING"}
            val parsed=manifest.reader().use{Gson().fromJson(it,RasterManifest::class.java)}
            require(parsed.version==1&&parsed.files.size in 1..256) {"GEBCO_METADATA_INVALID"}
            require(parsed.files.map{it.grid.id}.distinct().size==parsed.files.size) {"GEBCO_METADATA_DUPLICATE"}
            parsed.files.forEach {entry->
                require(entry.file.matches(Regex("GEBCO_[a-f0-9-]+\\.(tif|f32)"))&&entry.encoding in setOf("TIFF","FLOAT32_LE")) {"GEBCO_METADATA_INVALID"}
                val file=File(directory,entry.file);require(file.isFile&&file.length()==entry.size) {"GEBCO_FILE_CHANGED"}
                with(entry.grid){validatedGrid(id,datasetId,cellId,width,height,westEdge,northEdge,pixelWidthDegrees,pixelHeightDegrees,noData,product,registration,sourceName)}
                if(entry.encoding=="FLOAT32_LE")require(entry.size==entry.grid.width.toLong()*entry.grid.height*4) {"GEBCO_FILE_CHANGED"}
            }
            return RasterBathymetryStore(directory,parsed.files)
        }
    }
}

internal fun parseRasterNumber(token:String):Double = when(token.lowercase(Locale.ROOT)) {
    "nan","+nan","-nan"->Double.NaN
    else->token.toDoubleOrNull()?:error("GEBCO_NUMBER_INVALID")
}

internal fun normalizeElevation(value:Double,noData:Double?):Float {
    if(!value.isFinite()||noData!=null&&(value==noData||noData.isNaN()&&value.isNaN()))return Float.NaN
    require(value in -12_000.0..10_000.0) {"GEBCO_ELEVATION_OUT_OF_RANGE"}
    return value.toFloat()
}

internal fun validatedGrid(id:String,dataset:String,cell:String,width:Int,height:Int,west:Double,north:Double,dx:Double,dy:Double,noData:Double?,product:String,registration:String,source:String):RasterBathymetryGrid {
    require(width in 1..100_000&&height in 1..100_000&&width.toLong()*height<=4_000_000_000L) {"GEBCO_DIMENSIONS_INVALID"}
    require(listOf(west,north,dx,dy).all(Double::isFinite)&&dx>0&&dy>0&&abs(dx-1.0/240.0)<1e-8&&abs(dy-1.0/240.0)<1e-8) {"GEBCO_EXPECTED_15_ARCSECOND_GRID"}
    val east=west+width*dx;val south=north-height*dy
    require(west>= -180.000001&&west<=360.000001&&east-west<=360.000001&&north<=90.000001&&south>= -90.000001) {"GEBCO_GEOGRAPHIC_EXTENT_INVALID"}
    fun lon(v:Double)=((v+180.0)%360.0+360.0)%360.0-180.0
    val w=lon(west);val span=east-west
    val bounds=if(span>=359.999999)listOf(ChartBounds(-180.0,south.coerceAtLeast(-90.0),180.0,north.coerceAtMost(90.0)))else {
        val unwrapped=w+span
        if(unwrapped<=180.0+1e-8)listOf(ChartBounds(w,south.coerceAtLeast(-90.0),unwrapped.coerceAtMost(180.0),north.coerceAtMost(90.0)))
        else listOf(ChartBounds(w,south.coerceAtLeast(-90.0),180.0,north.coerceAtMost(90.0)),ChartBounds(-180.0,south.coerceAtLeast(-90.0),unwrapped-360.0,north.coerceAtMost(90.0)))
    }
    return RasterBathymetryGrid(id,dataset,cell,width,height,west,north,dx,dy,noData,product,registration,sourceName=source,bounds=bounds)
}

/** 有界词元流，不按超宽行或整幅 ASCII 栅格分配字符串。 */
private class AsciiTokens(file:File,private val check:()->Unit):Closeable {
    private val input=BufferedInputStream(FileInputStream(file),128*1024)
    private var bytes=0
    private fun read():Int {if((bytes++ and 65535)==0)check();return input.read()}
    fun next():String? {
        var c=read();while(c>=0&&c<=32)c=read();if(c<0)return null
        val buffer=StringBuilder(32)
        while(c>32&&c>=0){require(buffer.length<128&&c<128) {"GEBCO_ASCII_TOKEN_INVALID"};buffer.append(c.toChar());c=read()}
        return buffer.toString()
    }
    override fun close()=input.close()
}

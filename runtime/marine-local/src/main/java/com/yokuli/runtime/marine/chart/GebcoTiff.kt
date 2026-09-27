package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.RasterBathymetryGrid
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * GEBCO 单波段 Data GeoTIFF 有界读取器。不会使用 Bitmap 解码数值，也不将像元展开成多边形。
 * 支持 classic TIFF/BigTIFF、北向上 WGS84、strip/tile、无压缩/Deflate/TIFF LZW。
 * 不支持的压缩/配准/投影明确拒绝，不通过猜测继续导航计算。
 */
internal class GebcoTiff private constructor(private val file:RandomAccessFile,private val length:Long):Closeable {
    private var order=ByteOrder.LITTLE_ENDIAN
    private var big=false
    private data class Tag(val type:Int,val count:Long,val bytes:ByteArray)
    private val tags=HashMap<Int,Tag>()
    var width:Int=0;private set
    var height:Int=0;private set
    var description:String="";private set
    private var bits=0
    private var sampleFormat=0
    private var compression=0
    private var predictor=1
    private var blockWidth=0
    private var blockHeight=0
    private var blocksAcross=0
    private var tiled=false
    private var offsets=LongArray(0)
    private var counts=LongArray(0)
    private var west=0.0
    private var north=0.0
    private var dx=0.0
    private var dy=0.0
    private var noData:Double?=null
    private var registration=""
    private var cachedBytes=0L
    private val cache=object:LinkedHashMap<Int,ByteArray>(8,.75f,true){}
    val littleEndian get()=order==ByteOrder.LITTLE_ENDIAN

    private fun header(check:()->Unit) {
        require(length>=16) {"GEBCO_TIFF_HEADER_INVALID"}
        val head=read(0,16)
        order=when(String(head,0,2,Charsets.US_ASCII)){"II"->ByteOrder.LITTLE_ENDIAN;"MM"->ByteOrder.BIG_ENDIAN;else->error("GEBCO_NOT_TIFF")}
        val h=ByteBuffer.wrap(head).order(order);h.position(2)
        val version=h.short.toInt() and 0xffff
        val ifd=when(version){42->h.int.toLong() and 0xffffffffL;43->{big=true;require((h.short.toInt() and 0xffff)==8&&h.short.toInt()==0){"GEBCO_BIGTIFF_HEADER_INVALID"};h.long};else->error("GEBCO_TIFF_VERSION_UNSUPPORTED")}
        val fieldCountBytes=if(big)8 else 2
        val start=ByteBuffer.wrap(read(ifd,fieldCountBytes)).order(order)
        val count=if(big)start.long else (start.short.toInt() and 0xffff).toLong()
        require(count in 1..256) {"GEBCO_TIFF_TAG_LIMIT"}
        val entrySize=if(big)20 else 12
        val directory=ByteBuffer.wrap(read(ifd+fieldCountBytes,count.toInt()*entrySize)).order(order)
        var total=0L
        repeat(count.toInt()) {
            check();val tag=directory.short.toInt() and 0xffff;val type=directory.short.toInt() and 0xffff
            val values=if(big)directory.long else directory.int.toLong() and 0xffffffffL
            val slot=ByteArray(if(big)8 else 4).also{directory.get(it)}
            if(tag !in REQUIRED_TAGS)return@repeat
            require(tag !in tags) {"GEBCO_TIFF_DUPLICATE_TAG"}
            val unit=when(type){1,2,6,7->1;3,8->2;4,9,11->4;5,10,12,16,17,18->8;else->error("GEBCO_TIFF_TAG_TYPE_UNSUPPORTED")}
            require(values in 1..1_000_000L) {"GEBCO_TIFF_TAG_SIZE_LIMIT"}
            val size=values*unit;total+=size;require(size<=8_000_000&&total<=32_000_000) {"GEBCO_TIFF_TAG_SIZE_LIMIT"}
            val bytes=if(size<=slot.size)slot.copyOf(size.toInt())else {
                val pointer=ByteBuffer.wrap(slot).order(order).let{if(big)it.long else it.int.toLong() and 0xffffffffL}
                read(pointer,size.toInt())
            }
            tags[tag]=Tag(type,values,bytes)
        }
        width=scalar(256).checkedInt("GEBCO_DIMENSIONS_INVALID");height=scalar(257).checkedInt("GEBCO_DIMENSIONS_INVALID")
        require(width in 1..100_000&&height in 1..100_000&&width.toLong()*height<=4_000_000_000L) {"GEBCO_DIMENSIONS_INVALID"}
        require(scalar(277,1)==1L&&scalar(284,1)==1L) {"GEBCO_TIFF_SINGLE_NUMERIC_BAND_REQUIRED"}
        require(scalar(262,1)==1L&&338 !in tags&&320 !in tags) {"GEBCO_TID_OR_IMAGE_IS_NOT_ELEVATION"}
        require(scalar(274,1)==1L) {"GEBCO_TIFF_ROW_ORIENTATION_UNSUPPORTED"}
        bits=scalar(258).toInt();sampleFormat=scalar(339,1).toInt()
        require(sampleFormat==2&&bits in setOf(16,32)||sampleFormat==3&&bits in setOf(32,64)) {"GEBCO_TIFF_SIGNED_ELEVATION_REQUIRED"}
        compression=scalar(259,1).toInt();require(compression in setOf(1,5,8,32946)) {"GEBCO_TIFF_COMPRESSION_UNSUPPORTED:$compression"}
        predictor=scalar(317,1).toInt();require(predictor==1||predictor==2&&sampleFormat==2||predictor==3&&sampleFormat==3) {"GEBCO_TIFF_PREDICTOR_UNSUPPORTED"}
        description=listOf(270,305,34737,42112).mapNotNull{text(it)}.joinToString(" ").take(128_000)
        validateNumericMetadata(text(42112))
        noData=text(42113)?.trim()?.let{parseRasterNumber(it).takeIf(Double::isFinite)}
        val keys=ints(34735);require(keys.size>=4&&keys[0]==1L) {"GEBCO_GEOKEYS_MISSING"}
        val keyCount=keys[3].toInt();require(keyCount in 1..256&&keys.size==4+keyCount*4) {"GEBCO_GEOKEYS_INVALID"}
        val keyMap=HashMap<Int,Int>()
        repeat(keyCount){index->val at=4+index*4;if(keys[at+1]==0L&&keys[at+2]==1L){require(keyMap.put(keys[at].toInt(),keys[at+3].toInt())==null){"GEBCO_GEOKEYS_DUPLICATE"}}}
        require(keyMap[1024]==2&&keyMap[2048]==4326&&(keyMap[2054]==null||keyMap[2054]==9102)) {"GEBCO_WGS84_DEGREES_REQUIRED"}
        require(keyMap[4099]==null||keyMap[4099]==9001) {"GEBCO_ELEVATION_METRES_REQUIRED"}
        val rasterType=keyMap[1025]?:1;require(rasterType in 1..2) {"GEBCO_PIXEL_REGISTRATION_INVALID"}
        registration=if(rasterType==2)"TIFF_PIXEL_IS_POINT"else"TIFF_PIXEL_IS_AREA"
        val centreShift=if(rasterType==2).5 else 0.0
        if(34264 in tags) {
            val matrix=doubles(34264);require(matrix.size==16) {"GEBCO_TRANSFORM_INVALID"}
            require(listOf(1,2,4,6,8,9,12,13,14).all{abs(matrix[it])<1e-12}&&abs(matrix[15]-1)<1e-12&&matrix[0]>0&&matrix[5]<0) {"GEBCO_ROTATED_GRID_UNSUPPORTED"}
            dx=matrix[0];dy= -matrix[5];west=matrix[3]-centreShift*dx;north=matrix[7]+centreShift*dy
        } else {
            val scale=doubles(33550);val ties=doubles(33922)
            require(scale.size>=2&&ties.size>=6&&ties.size%6==0&&scale[0]>0&&scale[1]>0) {"GEBCO_PIXEL_REGISTRATION_INVALID"}
            dx=scale[0];dy=scale[1];west=ties[3]-(ties[0]+centreShift)*dx;north=ties[4]+(ties[1]+centreShift)*dy
            for(at in ties.indices step 6)require(abs(ties[at+3]-(west+(ties[at]+centreShift)*dx))<1e-7&&abs(ties[at+4]-(north-(ties[at+1]+centreShift)*dy))<1e-7) {"GEBCO_INCONSISTENT_TIEPOINTS"}
        }
        validatedGrid("","","",width,height,west,north,dx,dy,noData,"GEBCO","","")
        tiled=324 in tags||325 in tags
        if(tiled){blockWidth=scalar(322).checkedInt("GEBCO_BLOCK_SIZE_LIMIT");blockHeight=scalar(323).checkedInt("GEBCO_BLOCK_SIZE_LIMIT");offsets=ints(324);counts=ints(325)}
        else {blockWidth=width;blockHeight=scalar(278,height.toLong()).checkedInt("GEBCO_BLOCK_SIZE_LIMIT");offsets=ints(273);counts=ints(279)}
        require(blockWidth in 1..100_000&&blockHeight in 1..100_000&&blockWidth.toLong()*blockHeight*(bits/8)<=MAX_BLOCK_BYTES) {"GEBCO_BLOCK_SIZE_LIMIT"}
        blocksAcross=(width+blockWidth-1)/blockWidth
        val blocksDown=(height+blockHeight-1)/blockHeight
        require(offsets.size.toLong()==blocksAcross.toLong()*blocksDown&&counts.size==offsets.size) {"GEBCO_TIFF_BLOCK_INDEX_INVALID"}
        offsets.indices.forEach{index->check();require(counts[index] in 1..MAX_BLOCK_BYTES&&offsets[index]>=0&&offsets[index]<=length-counts[index]) {"GEBCO_TIFF_BLOCK_OUTSIDE_FILE"}}
    }

    fun grid(id:String,dataset:String,cell:String,product:String,source:String):RasterBathymetryGrid =
        validatedGrid(id,dataset,cell,width,height,west,north,dx,dy,noData,product,registration,source)

    @Synchronized fun readWindow(column:Int,row:Int,w:Int,h:Int,check:()->Unit):FloatArray {
        require(column>=0&&row>=0&&w>0&&h>0&&column.toLong()+w<=width&&row.toLong()+h<=height&&w.toLong()*h<=1_048_576) {"GEBCO_WINDOW_LIMIT"}
        val output=FloatArray(w*h);val sampleBytes=bits/8
        for(by in row/blockHeight..(row+h-1)/blockHeight)for(bx in column/blockWidth..(column+w-1)/blockWidth) {
            check();val bytes=block(by*blocksAcross+bx,check);val reader=ByteBuffer.wrap(bytes).order(order)
            val x0=maxOf(column,bx*blockWidth);val x1=minOf(column+w,(bx+1)*blockWidth)
            val y0=maxOf(row,by*blockHeight);val y1=minOf(row+h,(by+1)*blockHeight)
            for(y in y0 until y1) {
                check();var offset=((y-by*blockHeight)*blockWidth+x0-bx*blockWidth)*sampleBytes
                for(x in x0 until x1) {
                    val value=when {sampleFormat==2&&bits==16->reader.getShort(offset).toDouble();sampleFormat==2->reader.getInt(offset).toDouble();bits==32->reader.getFloat(offset).toDouble();else->reader.getDouble(offset)}
                    output[(y-row)*w+x-column]=normalizeElevation(value,if(sampleFormat==3&&bits==32)noData?.toFloat()?.toDouble()else noData);offset+=sampleBytes
                }
            }
        }
        return output
    }

    private fun block(index:Int,check:()->Unit):ByteArray {
        cache[index]?.let{return it}
        val heightInBlock=if(tiled)blockHeight else minOf(blockHeight,height-(index/blocksAcross)*blockHeight)
        val expected=blockWidth*heightInBlock*(bits/8)
        val encoded=read(offsets[index],counts[index].toInt())
        val bytes=when(compression){
            1->{require(encoded.size==expected){"GEBCO_TIFF_BLOCK_LENGTH_INVALID"};encoded}
            5->decodeLzw(encoded,expected,check)
            else->inflate(encoded,expected,check)
        }
        if(predictor!=1)restorePredictor(bytes,heightInBlock,check)
        while(cache.isNotEmpty()&&cachedBytes+bytes.size>MAX_CACHE_BYTES){val oldest=cache.entries.iterator();val value=oldest.next();cachedBytes-=value.value.size;oldest.remove()}
        if(bytes.size<=MAX_CACHE_BYTES){cache[index]=bytes;cachedBytes+=bytes.size}
        return bytes
    }
    private fun restorePredictor(bytes:ByteArray,rows:Int,check:()->Unit) {
        val bps=bits/8;val rowBytes=blockWidth*bps
        if(predictor==2) {
            val buffer=ByteBuffer.wrap(bytes).order(order)
            for(y in 0 until rows){check();val start=y*rowBytes
                for(x in 1 until blockWidth){val at=start+x*bps;if(bps==2)buffer.putShort(at,(buffer.getShort(at)+buffer.getShort(at-2)).toShort())else buffer.putInt(at,buffer.getInt(at)+buffer.getInt(at-4))}}
        } else {
            val scratch=ByteArray(rowBytes)
            for(y in 0 until rows){check();val start=y*rowBytes
                for(i in 1 until rowBytes)bytes[start+i]=(bytes[start+i].toInt()+bytes[start+i-1].toInt()).toByte()
                for(x in 0 until blockWidth)for(b in 0 until bps){val plane=if(littleEndian)bps-b-1 else b;scratch[x*bps+b]=bytes[start+plane*blockWidth+x]}
                scratch.copyInto(bytes,start)
            }
        }
    }

    private fun inflate(input:ByteArray,size:Int,check:()->Unit):ByteArray {
        val inflater=Inflater();val result=ByteArray(size)
        try {inflater.setInput(input);var written=0
            while(!inflater.finished()&&written<size){check();val count=inflater.inflate(result,written,minOf(64*1024,size-written));require(count>0){"GEBCO_DEFLATE_TRUNCATED"};written+=count}
            require(written==size&&inflater.finished()) {"GEBCO_DEFLATE_LENGTH_INVALID"}
            return result
        } finally {inflater.end()}
    }

    /** TIFF 6.0 的 MSB-first LZW，包含 clear/end code 与 early-change 码宽规则。 */
    private fun decodeLzw(input:ByteArray,size:Int,check:()->Unit):ByteArray {
        val output=ByteArray(size);val prefix=IntArray(4096);val suffix=ByteArray(4096);val stack=ByteArray(4096)
        for(i in 0..255)suffix[i]=i.toByte()
        var bit=0;var width=9;var next=258;var previous= -1;var count=0;var first=0
        fun code():Int {require(bit.toLong()+width<=input.size.toLong()*8){"GEBCO_LZW_TRUNCATED"};var value=0;repeat(width){value=(value shl 1) or ((input[bit/8].toInt() ushr (7-bit%8)) and 1);bit++};return value}
        var ended=false;var decodedCodes=0
        while(bit.toLong()+width<=input.size.toLong()*8) {
            if((decodedCodes++ and 255)==0)check();val current=code()
            if(current==256){width=9;next=258;previous= -1;continue}
            if(current==257){ended=true;break}
            require(current<=next&&current<4096) {"GEBCO_LZW_CODE_INVALID"}
            if(previous<0){require(current<256&&count<size){"GEBCO_LZW_CODE_INVALID"};output[count++]=current.toByte();first=current;previous=current;continue}
            var top=0;var value=current
            if(value==next){stack[top++]=first.toByte();value=previous}
            var traversed=0
            while(value>=258){require(value<next&&top<4096&&++traversed<4096){"GEBCO_LZW_DICTIONARY_INVALID"};stack[top++]=suffix[value];value=prefix[value]}
            require(value in 0..255&&top<4096){"GEBCO_LZW_DICTIONARY_INVALID"};first=value;stack[top++]=value.toByte()
            require(count.toLong()+top<=size) {"GEBCO_LZW_LENGTH_INVALID"}
            while(top>0)output[count++]=stack[--top]
            if(next<4096){prefix[next]=previous;suffix[next]=first.toByte();next++;if(width<12&&next==(1 shl width)-1)width++}
            previous=current
        }
        require(ended&&count==size) {"GEBCO_LZW_LENGTH_INVALID"};return output
    }
    /** 有缩放/偏移或英尺声明的转换产品不能冒充 GEBCO 原始米制高程。 */
    private fun validateNumericMetadata(metadata:String?) {
        if(metadata==null)return
        val items=Regex("""<Item\b([^>]*)>([^<]*)</Item>""",RegexOption.IGNORE_CASE)
        val attributes=Regex("""(name|role)\s*=\s*["']([^"']+)["']""",RegexOption.IGNORE_CASE)
        for(item in items.findAll(metadata)) {
            val labels=attributes.findAll(item.groupValues[1]).map{it.groupValues[2].lowercase(java.util.Locale.ROOT)}.toSet()
            val value=item.groupValues[2].trim()
            if("scale" in labels)require(value.toDoubleOrNull()==1.0) {"GEBCO_SCALED_PRODUCT_UNSUPPORTED"}
            if("offset" in labels)require(value.toDoubleOrNull()==0.0) {"GEBCO_SCALED_PRODUCT_UNSUPPORTED"}
            if(labels.any{it in setOf("unittype","unit","units")})require(value.lowercase(java.util.Locale.ROOT) in setOf("m","metre","metres","meter","meters")) {"GEBCO_ELEVATION_METRES_REQUIRED"}
        }
    }

    private fun ints(id:Int):LongArray {
        val tag=tags[id]?:error("GEBCO_TIFF_TAG_MISSING:$id");val data=ByteBuffer.wrap(tag.bytes).order(order)
        return LongArray(tag.count.toInt()){when(tag.type){1->data.get().toLong() and 255;3->data.short.toLong() and 65535;4->data.int.toLong() and 0xffffffffL;16,18->data.long.also{require(it>=0){"GEBCO_TIFF_OFFSET_INVALID"}};else->error("GEBCO_TIFF_INTEGER_TAG_REQUIRED:$id")}}
    }
    private fun scalar(id:Int,default:Long?=null):Long {if(id !in tags)return default?:error("GEBCO_TIFF_TAG_MISSING:$id");val values=ints(id);require(values.size==1){"GEBCO_TIFF_SINGLE_VALUE_REQUIRED:$id"};return values[0]}
    private fun doubles(id:Int):DoubleArray {val tag=tags[id]?:error("GEBCO_TIFF_TAG_MISSING:$id");require(tag.type==12){"GEBCO_TIFF_DOUBLE_TAG_REQUIRED"};val data=ByteBuffer.wrap(tag.bytes).order(order);return DoubleArray(tag.count.toInt()){data.double.also{require(it.isFinite()){ "GEBCO_TIFF_COORDINATE_INVALID" }}}}
    private fun text(id:Int):String? {val tag=tags[id]?:return null;require(tag.type==2&&tag.bytes.size<=128_000){"GEBCO_TIFF_TEXT_INVALID"};return String(tag.bytes,Charsets.UTF_8).trimEnd('\u0000')}
    private fun read(offset:Long,count:Int):ByteArray {require(offset>=0&&count>=0&&offset<=length-count){"GEBCO_TIFF_OFFSET_OUTSIDE_FILE"};file.seek(offset);return ByteArray(count).also{file.readFully(it)}}
    private fun Long.checkedInt(error:String)=also{require(it in 1..Int.MAX_VALUE.toLong()){error}}.toInt()
    override fun close(){cache.clear();cachedBytes=0;file.close()}
    companion object {
        private const val MAX_BLOCK_BYTES=64L*1024*1024
        private const val MAX_CACHE_BYTES=16L*1024*1024
        private val REQUIRED_TAGS=setOf(256,257,258,259,262,270,273,274,277,278,279,284,305,317,320,322,323,324,325,338,339,33550,33922,34264,34735,34737,42112,42113)
        fun open(source:File,check:()->Unit={}):GebcoTiff {
            val reader=GebcoTiff(RandomAccessFile(source,"r"),source.length())
            try{reader.header(check);return reader}catch(error:Throwable){reader.close();throw error}
        }
    }
}

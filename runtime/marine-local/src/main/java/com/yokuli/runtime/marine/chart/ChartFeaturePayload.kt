package com.yokuli.runtime.marine.chart

import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * 安装索引的唯一对象编码。属性是压缩的小 JSON，坐标保留原始 double 位，不再逐点写字段名。
 * 每个几何部件有范围和独立数据块；局部查询只读相交部件，不能把局部结果当完整对象缓存。
 * 这是无损的存储编码，不是简化海岸、量化水深或改变来源优先级的显示产物。
 */
internal object ChartFeaturePayload {
    const val MAX_STORED_BYTES=32*1024*1024
    private const val MAGIC=0x594b4637
    private const val VERSION=1
    private const val HEADER_BYTES=36
    private const val PART_BYTES=57
    // 旧 JSON 允许八百万 UTF-16 字符；中文属性不能因新编码突然失去兼容。
    private const val MAX_METADATA_BYTES=24_000_000
    private const val MAX_VERTICES=500_000
    private const val MAX_PARTS=500_000
    private const val READ_CHUNK=128*1024
    private data class Part(val hole:Boolean,val count:Int,val bounds:ChartBounds,val bytes:ByteArray,val rawLength:Int,val crc:Long)

    fun encode(feature:NauticalFeature,gson:Gson,check:()->Unit={}):ByteArray = ChartFeatureEncoder.encode(feature,gson,check)

    /** v2–v6 仍可读取；旧索引后台原子压实期间，前台不等待迁移也不读取半成品。 */
    fun read(db:SQLiteDatabase,rowId:Long,length:Int,gson:Gson,signal:CancellationSignal,check:()->Unit,bounds:ChartBounds?=null,metadataOnly:Boolean=false,identity:NativeIdentity?=null,indexVersion:Int=db.version):NauticalFeature {
        require(length in 1..MAX_STORED_BYTES){"CHART_FEATURE_PAYLOAD_INVALID"}
        if(indexVersion<7)return readLegacy(db,rowId,length,gson,signal,check)
        val source=BlobSource(db,rowId,signal,check)
        if(indexVersion>=8) {
            val metadata=ChartFeatureEncoder.decodeMetadata(source.read(0,source.length),gson,check)
            val geometry=if(metadataOnly)metadata.geometry else ChartGeometrySpanIndex.readGeometry(db,rowId,metadata.geometry.kind,signal,check,bounds)
            val value=metadata.copy(geometry=geometry)
            val rebound=identity?:readIdentity(db)
            return rebound.datasetId?.let{value.copy(datasetId=it,source=value.source.copy(datasetId=it))}?:value
        }
        val header=DataInputStream(ByteArrayInputStream(source.read(0,HEADER_BYTES)))
        require(header.readInt()==MAGIC&&header.readInt()==VERSION){"CHART_FEATURE_PAYLOAD_VERSION"}
        val metadataBytes=header.readInt();val metadataRaw=header.readInt();val count=header.readInt();val metadataCrc=header.readLong();val directoryCrc=header.readLong()
        require(metadataBytes in 1..MAX_STORED_BYTES&&metadataRaw in 1..MAX_METADATA_BYTES&&count in 0..MAX_PARTS){"CHART_FEATURE_PAYLOAD_INVALID"}
        val directoryOffset=HEADER_BYTES+metadataBytes
        val firstPartOffset=directoryOffset.toLong()+count.toLong()*PART_BYTES
        require(firstPartOffset<=source.length){"CHART_FEATURE_PAYLOAD_TRUNCATED"}
        val metadata=inflate(source.read(HEADER_BYTES,metadataBytes),metadataRaw,check)
        require(crc(metadata)==metadataCrc){"CHART_FEATURE_PAYLOAD_CHECKSUM"}
        val feature=requireNotNull(gson.fromJson(String(metadata,Charsets.UTF_8),NauticalFeature::class.java)){"CHART_FEATURE_PAYLOAD_INVALID"}
        val directoryBytes=source.read(directoryOffset,count*PART_BYTES)
        require(crc(directoryBytes)==directoryCrc){"CHART_FEATURE_PAYLOAD_CHECKSUM"}
        val directory=DataInputStream(ByteArrayInputStream(directoryBytes))
        val parts=ArrayList<ChartGeometryPart>(count.coerceAtMost(4096));var vertices=0;var previousEnd=firstPartOffset.toInt()
        for(index in 0 until count) {
            check()
            val input=directory
            val hole=input.readBoolean();val size=input.readInt()
            val partBounds=ChartBounds(input.readDouble(),input.readDouble(),input.readDouble(),input.readDouble())
            val offset=input.readInt();val storedLength=input.readInt();val rawLength=input.readInt();val checksum=input.readLong()
            require(size>=0&&size<=MAX_VERTICES-vertices&&partBounds.valid){"CHART_FEATURE_GEOMETRY_INVALID"};vertices+=size
            require(offset==previousEnd&&storedLength>0&&offset.toLong()+storedLength<=source.length&&rawLength.toLong() in size.toLong()*17..size.toLong()*25){"CHART_FEATURE_PAYLOAD_TRUNCATED"}
            previousEnd=offset+storedLength
            if(bounds!=null&&size>0&&!intersects(bounds,partBounds))continue
            val raw=inflate(source.read(offset,storedLength),rawLength,check)
            require(crc(raw)==checksum){"CHART_FEATURE_PAYLOAD_CHECKSUM"}
            val points=ArrayList<ChartPoint>(size)
            DataInputStream(ByteArrayInputStream(raw)).use {coordinates->
                repeat(size) {pointIndex->
                    if(pointIndex%256==0)check()
                    val latitude=coordinates.readDouble();val longitude=coordinates.readDouble()
                    require(latitude in -90.0..90.0&&longitude in -180.0..180.0){"CHART_FEATURE_COORDINATE_INVALID"}
                    val depth=if(coordinates.readBoolean())coordinates.readDouble().also {require(it.isFinite()){"CHART_FEATURE_DEPTH_INVALID"}}else null
                    points+=ChartPoint(latitude,longitude,depth)
                }
                require(coordinates.read()==-1){"CHART_FEATURE_PAYLOAD_INVALID"}
            }
            parts+=ChartGeometryPart(points,hole)
        }
        require(previousEnd==source.length){"CHART_FEATURE_PAYLOAD_INVALID"}
        val geometry=if(indexVersion>=8&&!metadataOnly)ChartGeometrySpanIndex.readGeometry(db,rowId,feature.geometry.kind,signal,check,bounds)
            else feature.geometry.copy(parts=parts)
        val value=feature.copy(geometry=geometry)
        // 原生库安装只重绑外部资料 ID，不能为此重写全部属性和坐标。
        val datasetId=(identity?:readIdentity(db)).datasetId
        return if(datasetId.isNullOrBlank())value else value.copy(datasetId=datasetId,source=value.source.copy(datasetId=datasetId))
    }

    /** null datasetId 表示此不可变连接没有设备重绑定，不是“尚未读取”。 */
    data class NativeIdentity(val datasetId:String?)
    fun readIdentity(db:SQLiteDatabase):NativeIdentity {
        val present=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='native_identity'",null).use{it.moveToFirst()}
        return NativeIdentity(if(present)db.rawQuery("SELECT dataset_id FROM native_identity LIMIT 1",null).use{if(it.moveToFirst())it.getString(0)else null}else null)
    }

    private fun readLegacy(db:SQLiteDatabase,rowId:Long,length:Int,gson:Gson,signal:CancellationSignal,check:()->Unit):NauticalFeature {
        require(length<=8_000_000){"CHART_FEATURE_PAYLOAD_INVALID"}
        val payload=StringBuilder(length);var offset=1
        while(offset<=length) {
            check();signal.throwIfCanceled()
            db.rawQuery("SELECT substr(payload,?,?) FROM features WHERE rowid=?",arrayOf(offset.toString(),READ_CHUNK.toString(),rowId.toString()),signal).use {part->
                require(part.moveToFirst()){"CHART_FEATURE_ROW_MISSING"}
                val text=part.getString(0);require(!text.isNullOrEmpty()){"CHART_FEATURE_PAYLOAD_TRUNCATED"};payload.append(text)
            }
            offset+=READ_CHUNK
        }
        check();return requireNotNull(gson.fromJson(payload.toString(),NauticalFeature::class.java)){"CHART_FEATURE_PAYLOAD_INVALID"}
    }

    private class BlobSource(val db:SQLiteDatabase,val rowId:Long,val signal:CancellationSignal,val check:()->Unit) {
        val length:Int
        private var pageOffset=0
        private var page:ByteArray
        init {
            check();signal.throwIfCanceled()
            // 候选行和几何读之间可能恰好完成 v6 -> v7 原子替换。行号保持不变，长度必须取
            // 当前连接的 BLOB，不能把旧 JSON 的字符长度套到新对象上导致“截断”误报。
            val first=db.rawQuery("SELECT length(payload),substr(payload,1,?) FROM features WHERE rowid=?",arrayOf(READ_CHUNK.toString(),rowId.toString()),signal).use {row->
                require(row.moveToFirst()){"CHART_FEATURE_ROW_MISSING"}
                row.getInt(0) to row.getBlob(1)
            }
            length=first.first;page=first.second
            require(length in HEADER_BYTES..MAX_STORED_BYTES){"CHART_FEATURE_PAYLOAD_INVALID"}
            require(page.size==minOf(READ_CHUNK,length)){"CHART_FEATURE_PAYLOAD_TRUNCATED"}
        }
        fun read(offset:Int,count:Int):ByteArray {
            require(offset>=0&&count>=0&&offset.toLong()+count<=length){"CHART_FEATURE_PAYLOAD_TRUNCATED"}
            val result=ByteArray(count);var consumed=0
            while(consumed<count) {
                check();signal.throwIfCanceled()
                val position=offset+consumed
                if(position !in pageOffset until pageOffset+page.size) {
                    pageOffset=position/READ_CHUNK*READ_CHUNK
                    db.rawQuery("SELECT substr(payload,?,?) FROM features WHERE rowid=?",arrayOf((pageOffset+1).toString(),READ_CHUNK.toString(),rowId.toString()),signal).use {row->
                        require(row.moveToFirst()){"CHART_FEATURE_ROW_MISSING"};page=row.getBlob(0)
                        require(page.size==minOf(READ_CHUNK,length-pageOffset)){"CHART_FEATURE_PAYLOAD_TRUNCATED"}
                    }
                }
                val size=minOf(count-consumed,pageOffset+page.size-position)
                page.copyInto(result,consumed,position-pageOffset,position-pageOffset+size);consumed+=size
            }
            return result
        }
    }

    private fun intersects(a:ChartBounds,b:ChartBounds)=a.split().any {x->b.split().any {y->x.west<=y.east&&x.east>=y.west&&x.south<=y.north&&x.north>=y.south}}
    private fun crc(bytes:ByteArray)=CRC32().apply{update(bytes)}.value
    private fun inflate(bytes:ByteArray,expected:Int,check:()->Unit):ByteArray {
        require(expected in 0..MAX_STORED_BYTES){"CHART_FEATURE_PAYLOAD_INVALID"}
        val inflater=Inflater(true)
        try {
            val result=ByteArray(expected)
            InflaterInputStream(ByteArrayInputStream(bytes),inflater).use {input->
                var offset=0
                while(offset<expected){check();val read=input.read(result,offset,minOf(READ_CHUNK,expected-offset));require(read>0){"CHART_FEATURE_PAYLOAD_TRUNCATED"};offset+=read}
                require(input.read()==-1&&inflater.finished()&&inflater.remaining==0){"CHART_FEATURE_PAYLOAD_INVALID"}
            }
            return result
        }finally{inflater.end()}
    }
}

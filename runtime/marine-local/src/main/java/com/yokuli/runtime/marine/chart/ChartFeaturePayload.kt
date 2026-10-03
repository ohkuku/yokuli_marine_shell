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

    fun encode(feature:NauticalFeature,gson:Gson,check:()->Unit={}):ByteArray {
        check()
        require(feature.geometry.parts.size<=MAX_PARTS){"CHART_FEATURE_GEOMETRY_LIMIT:${feature.id}"}
        val metadata=encodeMetadata(feature,gson,check)
        val compressedMetadata=metadata.bytes
        var vertices=0
        var bytes=HEADER_BYTES.toLong()+compressedMetadata.size+feature.geometry.parts.size.toLong()*PART_BYTES
        val parts=feature.geometry.parts.map {part->
            check();vertices+=part.points.size
            require(vertices<=MAX_VERTICES){"CHART_FEATURE_GEOMETRY_LIMIT:${feature.id}"}
            var west=180.0;var east=-180.0;var south=90.0;var north=-90.0
            val stream=ByteArrayOutputStream(part.points.size.coerceAtMost(16384)*17)
            DataOutputStream(stream).use {out->
                part.points.forEachIndexed {index,point->
                    if(index%256==0)check()
                    require(point.latitude.isFinite()&&point.longitude.isFinite()&&point.latitude in -90.0..90.0&&point.longitude in -180.0..180.0){"CHART_FEATURE_COORDINATE_INVALID"}
                    west=minOf(west,point.longitude);east=maxOf(east,point.longitude)
                    south=minOf(south,point.latitude);north=maxOf(north,point.latitude)
                    out.writeDouble(point.latitude);out.writeDouble(point.longitude)
                    out.writeBoolean(point.depthMeters!=null)
                    point.depthMeters?.let {require(it.isFinite()){ "CHART_FEATURE_DEPTH_INVALID" };out.writeDouble(it)}
                }
            }
            // 跨日界线的环可能包含 +180 附近而顶点仅到 ±179；普通 min/max 不能排除这段。
            if(east-west>180.0){west=-180.0;east=180.0}
            val raw=stream.toByteArray();val packed=compress(raw,check)
            bytes+=packed.size
            require(bytes<=MAX_STORED_BYTES){"CHART_FEATURE_GEOMETRY_LIMIT:${feature.id}"}
            Part(part.hole,part.points.size,if(part.points.isEmpty())ChartBounds(0.0,0.0,0.0,0.0)else ChartBounds(west,south,east,north),packed,raw.size,crc(raw))
        }
        val directoryStream=ByteArrayOutputStream(parts.size*PART_BYTES)
        DataOutputStream(directoryStream).use {out->
            var offset=HEADER_BYTES+compressedMetadata.size+parts.size*PART_BYTES
            for(part in parts) {
                out.writeBoolean(part.hole);out.writeInt(part.count)
                out.writeDouble(part.bounds.west);out.writeDouble(part.bounds.south)
                out.writeDouble(part.bounds.east);out.writeDouble(part.bounds.north)
                out.writeInt(offset);out.writeInt(part.bytes.size);out.writeInt(part.rawLength);out.writeLong(part.crc)
                offset+=part.bytes.size
            }
        }
        val directory=directoryStream.toByteArray()
        val stream=ByteArrayOutputStream(bytes.toInt())
        DataOutputStream(stream).use {out->
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(compressedMetadata.size)
            out.writeInt(metadata.rawLength);out.writeInt(parts.size);out.writeLong(metadata.crc);out.writeLong(crc(directory))
            out.write(compressedMetadata);out.write(directory)
            for(part in parts){check();out.write(part.bytes)}
        }
        return stream.toByteArray()
    }

    /** v2–v6 仍可读取；旧索引后台原子压实期间，前台不等待迁移也不读取半成品。 */
    fun read(db:SQLiteDatabase,rowId:Long,length:Int,gson:Gson,signal:CancellationSignal,check:()->Unit,bounds:ChartBounds?=null,metadataOnly:Boolean=false):NauticalFeature {
        require(length in 1..MAX_STORED_BYTES){"CHART_FEATURE_PAYLOAD_INVALID"}
        if(db.version<7)return readLegacy(db,rowId,length,gson,signal,check)
        val source=BlobSource(db,rowId,signal,check)
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
        val geometry=if(db.version>=8&&!metadataOnly)ChartGeometrySpanIndex.readGeometry(db,rowId,feature.geometry.kind,signal,check,bounds)
            else feature.geometry.copy(parts=parts)
        val value=feature.copy(geometry=geometry)
        // 原生库安装只重绑外部资料 ID，不能为此重写全部属性和坐标。
        val hasIdentity=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='native_identity'",null).use{it.moveToFirst()}
        val identity=if(hasIdentity)db.rawQuery("SELECT dataset_id FROM native_identity LIMIT 1",null).use{if(it.moveToFirst())it.getString(0)else null}else null
        return if(identity.isNullOrBlank())value else value.copy(datasetId=identity,source=value.source.copy(datasetId=identity))
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
    private data class Metadata(val bytes:ByteArray,val rawLength:Int,val crc:Long)
    /** 流式写属性，避免旧版允许的大文本同时形成 UTF-16 JSON、UTF-8 副本和压缩副本。 */
    private fun encodeMetadata(feature:NauticalFeature,gson:Gson,check:()->Unit):Metadata {
        val compressor=Deflater(Deflater.BEST_SPEED,true)
        try {
            val buffer=ByteArrayOutputStream();val checksum=CRC32();var count=0
            val zip=DeflaterOutputStream(buffer,compressor)
            val output=object:OutputStream() {
                override fun write(value:Int) {write(byteArrayOf(value.toByte()),0,1)}
                override fun write(bytes:ByteArray,offset:Int,length:Int) {
                    check();count+=length
                    require(count<=MAX_METADATA_BYTES){"CHART_FEATURE_METADATA_LIMIT:${feature.id}"}
                    checksum.update(bytes,offset,length);zip.write(bytes,offset,length)
                }
                override fun close(){zip.close()}
                override fun flush(){zip.flush()}
            }
            OutputStreamWriter(output,Charsets.UTF_8).buffered().use {writer->
                gson.toJson(feature.copy(geometry=feature.geometry.copy(parts=emptyList())),writer)
            }
            return Metadata(buffer.toByteArray(),count,checksum.value)
        }finally{compressor.end()}
    }
    private fun compress(bytes:ByteArray,check:()->Unit):ByteArray {
        val compressor=Deflater(Deflater.BEST_SPEED,true)
        try {
            val output=ByteArrayOutputStream()
            DeflaterOutputStream(output,compressor).use {stream->
                var offset=0
                while(offset<bytes.size){check();val size=minOf(READ_CHUNK,bytes.size-offset);stream.write(bytes,offset,size);offset+=size}
            }
            return output.toByteArray()
        }finally{compressor.end()}
    }
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

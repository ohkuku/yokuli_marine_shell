package com.yokuli.runtime.marine.chart

import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import java.io.*
import java.util.zip.*

/** Android 导入与桌面编译共用的唯一二进制属性编码；无平台依赖。 */
internal object ChartFeatureEncoder {
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
    /** v8 属性页不含几何，完整校验后交给同一个事实模型。 */
    fun decodeMetadata(payload:ByteArray,gson:Gson,check:()->Unit={}):NauticalFeature {
        require(payload.size in HEADER_BYTES..MAX_STORED_BYTES){"CHART_FEATURE_PAYLOAD_INVALID"}
        val input=DataInputStream(ByteArrayInputStream(payload))
        require(input.readInt()==MAGIC&&input.readInt()==VERSION){"CHART_FEATURE_PAYLOAD_VERSION"}
        val compressed=input.readInt();val length=input.readInt();val parts=input.readInt();val checksum=input.readLong();val directory=input.readLong()
        require(parts==0&&directory==crc(ByteArray(0))&&length in 1..MAX_METADATA_BYTES&&compressed==payload.size-HEADER_BYTES){"CHART_FEATURE_PAYLOAD_INVALID"}
        val raw=ByteArray(length);val inflater=Inflater(true)
        try {InflaterInputStream(ByteArrayInputStream(payload,HEADER_BYTES,compressed),inflater).use {zip->
            var offset=0
            while(offset<length){check();val count=zip.read(raw,offset,minOf(READ_CHUNK,length-offset));require(count>0){"CHART_FEATURE_PAYLOAD_TRUNCATED"};offset+=count}
            require(zip.read()==-1&&inflater.finished()&&inflater.remaining==0){"CHART_FEATURE_PAYLOAD_INVALID"}
        }}finally{inflater.end()}
        require(crc(raw)==checksum){"CHART_FEATURE_PAYLOAD_CHECKSUM"}
        return requireNotNull(gson.fromJson(String(raw,Charsets.UTF_8),NauticalFeature::class.java)).also{
            require(it.geometry.parts.isEmpty()){"CHART_FEATURE_PAYLOAD_INVALID"}
        }
    }

}

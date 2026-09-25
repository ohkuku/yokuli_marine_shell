package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.WKBReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** GeoPackage 二进制只解释空间坐标。Z/M 原值绝不自动变成水深。 */
internal class GeoPackageGeometryReader(private val check:()->Unit) {
    data class Result(val geometry:ChartGeometry,val vertices:Int,val hasZ:Boolean,val hasM:Boolean)
    private val reader=WKBReader()

    fun read(bytes:ByteArray,srsId:Int,epsg:Int,declaredType:String,z:Int,m:Int):Result {
        require(bytes.size in 13..MAX_BLOB) {"GPKG_GEOMETRY_SIZE_LIMIT"}
        require(bytes[0].toInt()==0x47&&bytes[1].toInt()==0x50&&bytes[2].toInt()==0) {"GPKG_GEOMETRY_HEADER_INVALID"}
        val flags=bytes[3].toInt() and 255
        require(flags and 0xE0==0) {"GPKG_EXTENDED_GEOMETRY_UNSUPPORTED"}
        val envelopeCode=(flags ushr 1) and 7
        require(envelopeCode<=4) {"GPKG_ENVELOPE_INVALID"}
        val empty=flags and 0x10!=0
        val envelopeSize=when(envelopeCode){0->0;1->32;2,3->48;else->64}
        val offset=8+envelopeSize
        require(offset+5<=bytes.size) {"GPKG_GEOMETRY_TRUNCATED"}
        val header=ByteBuffer.wrap(bytes).order(if(flags and 1==1)ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        require(header.getInt(4)==srsId) {"GPKG_GEOMETRY_SRS_MISMATCH"}
        val envelope=if(envelopeSize==0)null else DoubleArray(envelopeSize/8){header.getDouble(8+it*8)}
        if(!empty&&envelope!=null) {
            require(envelope.all{it.isFinite()}&&(envelope.indices step 2).all{envelope[it]<=envelope[it+1]}) {"GPKG_ENVELOPE_INVALID"}
        }
        val buffer=ByteBuffer.wrap(bytes,offset,bytes.size-offset).slice()
        val scan=Scanner(buffer,epsg,check)
        val type=scan.geometry()
        require(!buffer.hasRemaining()) {"GPKG_WKB_TRAILING_BYTES"}
        require((envelopeCode !in setOf(2,4)||scan.hasZ)&&(envelopeCode !in setOf(3,4)||scan.hasM)) {"GPKG_ENVELOPE_DIMENSION_MISMATCH"}
        require(declaredType=="GEOMETRY"||declaredType==typeName(type)) {"GPKG_GEOMETRY_TYPE_MISMATCH"}
        require((z!=0||!scan.hasZ)&&(z!=1||scan.hasZ)&&(m!=0||!scan.hasM)&&(m!=1||scan.hasM)) {"GPKG_GEOMETRY_DIMENSION_MISMATCH"}
        check()
        // 先扫描结构再交给 JTS，避免其自动闭环/补点把坏输入悄悄修成可分析数据。
        val geometry=reader.read(bytes.copyOfRange(offset,bytes.size))
        require(geometry.isEmpty==empty) {"GPKG_EMPTY_FLAG_MISMATCH"}
        if(empty)return Result(ChartGeometry(ChartGeometryKind.NONE,emptyList()),0,scan.hasZ,scan.hasM)
        if(envelope!=null) {
            val actual=geometry.envelopeInternal
            val tolerance=if(epsg==3857)1e-5 else 1e-10
            require(actual.minX>=envelope[0]-tolerance&&actual.maxX<=envelope[1]+tolerance&&actual.minY>=envelope[2]-tolerance&&actual.maxY<=envelope[3]+tolerance) {"GPKG_ENVELOPE_MISMATCH"}
        }
        // 日期变更线附近按同一个经度分支检查拓扑，不把 +180/-180 的短边当成横跨全球。
        val validation=geometry.copy()
        val anchor=position(geometry.coordinate,epsg).longitude
        var checked=0
        validation.apply(object:CoordinateSequenceFilter {
            override fun filter(sequence:CoordinateSequence,index:Int){
                if(++checked%256==0)check()
                val point=position(sequence.getCoordinate(index),epsg)
                var longitude=point.longitude
                while(longitude-anchor>180)longitude-=360
                while(longitude-anchor< -180)longitude+=360
                sequence.setOrdinate(index,0,longitude);sequence.setOrdinate(index,1,point.latitude)
            }
            override fun isDone()=false
            override fun isGeometryChanged()=true
        })
        check();require(validation.isValid) {"GPKG_INVALID_GEOMETRY"};check()
        val parts=ArrayList<ChartGeometryPart>()
        fun line(value:LineString,hole:Boolean=false){parts+=ChartGeometryPart(value.coordinates.mapIndexed {index,c->if(index%256==0)check();position(c,epsg)},hole)}
        fun add(value:Geometry){
            check()
            when(value){
                is Point->if(!value.isEmpty)parts+=ChartGeometryPart(listOf(position(value.coordinate,epsg)))
                is Polygon->{line(value.exteriorRing);repeat(value.numInteriorRing){line(value.getInteriorRingN(it),true)}}
                is LineString->line(value)
                is MultiPoint,is MultiLineString,is MultiPolygon->repeat(value.numGeometries){add(value.getGeometryN(it))}
                else->error("GPKG_GEOMETRY_COLLECTION_UNSUPPORTED")
            }
        }
        add(geometry)
        val kind=when(type){1->ChartGeometryKind.POINT;2,5->ChartGeometryKind.LINE;3,6->ChartGeometryKind.POLYGON;4->ChartGeometryKind.MULTIPOINT;else->error("GPKG_GEOMETRY_TYPE_UNSUPPORTED")}
        return Result(ChartGeometry(kind,parts),scan.vertices,scan.hasZ,scan.hasM)
    }

    private class Scanner(val b:ByteBuffer,val epsg:Int,val check:()->Unit) {
        var vertices=0;var hasZ=false;var hasM=false
        private fun requireBytes(n:Long){require(n>=0&&n<=b.remaining().toLong()) {"GPKG_WKB_TRUNCATED"}}
        private fun integer():Int{requireBytes(4);return b.int}
        private fun number():Double{requireBytes(8);return b.double}
        private fun count(max:Int):Int=integer().also{require(it in 0..max) {"GPKG_GEOMETRY_COUNT_LIMIT"}}
        fun geometry(expected:Int?=null,depth:Int=0,expectedDimension:Int?=null):Int {
            check();require(depth<=8) {"GPKG_GEOMETRY_NESTING_LIMIT"}
            requireBytes(5)
            val byteOrder=b.get().toInt() and 255
            require(byteOrder==0||byteOrder==1) {"GPKG_WKB_BYTE_ORDER_INVALID"}
            b.order(if(byteOrder==1)ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            val raw=integer();val dimension=raw/1000;val type=raw%1000
            require(dimension in 0..3&&type in 1..6) {"GPKG_GEOMETRY_TYPE_UNSUPPORTED"}
            require(expected==null||expected==type) {"GPKG_MULTI_GEOMETRY_TYPE_MISMATCH"}
            require(expectedDimension==null||expectedDimension==dimension) {"GPKG_MULTI_GEOMETRY_DIMENSION_MISMATCH"}
            val z=dimension==1||dimension==3;val m=dimension==2||dimension==3
            hasZ=hasZ||z;hasM=hasM||m
            val ordinates=2+(if(z)1 else 0)+(if(m)1 else 0)
            fun coordinate(emptyAllowed:Boolean=false):Pair<Double,Double> {
                require(++vertices<=MAX_VERTICES) {"GPKG_GEOMETRY_VERTEX_LIMIT"}
                if(vertices%256==0)check()
                val x=number();val y=number()
                repeat(ordinates-2){number().also{value->require(value.isFinite()||value.isNaN()) {"GPKG_COORDINATE_INVALID"}}}
                if(!(emptyAllowed&&x.isNaN()&&y.isNaN()))validatePosition(x,y,epsg)
                return x to y
            }
            fun points(ring:Boolean){
                val n=count(MAX_VERTICES)
                require(n==0||n>=(if(ring)4 else 2)) {"GPKG_GEOMETRY_TOO_FEW_POINTS"}
                require(!ring||n>=4) {"GPKG_RING_EMPTY"}
                require(vertices.toLong()+n<=MAX_VERTICES) {"GPKG_GEOMETRY_VERTEX_LIMIT"}
                requireBytes(n.toLong()*ordinates*8)
                var first:Pair<Double,Double>?=null;var last:Pair<Double,Double>?=null
                repeat(n){val p=coordinate();if(it==0)first=p;last=p}
                if(ring)require(first==last) {"GPKG_RING_NOT_CLOSED"}
            }
            when(type){
                1->coordinate(true)
                2->points(false)
                3->{val rings=count(20_000);repeat(rings){points(true)}}
                4,5,6->{val children=count(100_000);repeat(children){geometry(type-3,depth+1,dimension)}}
            }
            return type
        }
    }
    companion object {
        const val MAX_BLOB=8_000_000
        const val MAX_VERTICES=200_000
        private const val WEB_MERCATOR_LIMIT=20_037_508.342789244
        private const val RADIUS=6_378_137.0
        private fun typeName(type:Int)=when(type){1->"POINT";2->"LINESTRING";3->"POLYGON";4->"MULTIPOINT";5->"MULTILINESTRING";6->"MULTIPOLYGON";else->"UNSUPPORTED"}
        private fun validatePosition(x:Double,y:Double,epsg:Int){
            require(x.isFinite()&&y.isFinite()) {"GPKG_COORDINATE_INVALID"}
            when(epsg){
                4326->require(x in -180.0..180.0&&y in -90.0..90.0) {"GPKG_COORDINATE_OUT_OF_RANGE"}
                3857->require(abs(x)<=WEB_MERCATOR_LIMIT+1e-6&&abs(y)<=WEB_MERCATOR_LIMIT+1e-6) {"GPKG_COORDINATE_OUT_OF_RANGE"}
                else->error("GPKG_SRS_UNSUPPORTED:$epsg")
            }
        }
        private fun position(c:Coordinate,epsg:Int):ChartPoint {
            validatePosition(c.x,c.y,epsg)
            return if(epsg==4326)ChartPoint(c.y,c.x)
            else ChartPoint(Math.toDegrees(2*atan(exp(c.y/RADIUS))-PI/2),Math.toDegrees(c.x/RADIUS).coerceIn(-180.0,180.0))
        }
    }
}

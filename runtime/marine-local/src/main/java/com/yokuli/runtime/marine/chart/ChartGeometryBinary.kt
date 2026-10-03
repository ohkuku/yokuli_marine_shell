package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.ChartPoint
import java.io.*
import java.util.zip.*

/** 手机查询与桌面预编译共用的有界全精度坐标解码。 */
internal object ChartGeometryBinary {
    private const val EDGES=256
    private const val RAW_LIMIT=6425
    private fun crc(bytes:ByteArray)=CRC32().apply{update(bytes)}.value
    fun decode(count:Int,rawSize:Int,checksum:Long,bytes:ByteArray,check:()->Unit):List<ChartPoint> {
        check()
        require(count in 1..EDGES+1&&rawSize in count*17..count*25&&rawSize<=RAW_LIMIT&&bytes.size<=65536){"CHART_GEOMETRY_SPAN_INVALID"}
        val raw=ByteArray(rawSize);val inflater=Inflater(true)
        try {InflaterInputStream(ByteArrayInputStream(bytes),inflater).use {input->
            var offset=0;while(offset<raw.size){check();val n=input.read(raw,offset,raw.size-offset);require(n>0){"CHART_GEOMETRY_SPAN_TRUNCATED"};offset+=n}
            require(input.read()==-1){"CHART_GEOMETRY_SPAN_INVALID"}
        }}finally{inflater.end()}
        require(crc(raw)==checksum){"CHART_GEOMETRY_SPAN_CHECKSUM"}
        return DataInputStream(ByteArrayInputStream(raw)).use {input->List(count){
            val lat=input.readDouble();val lon=input.readDouble();val depth=if(input.readBoolean())input.readDouble()else null
            require(lat in -90.0..90.0&&lon in -180.0..180.0&&depth?.isFinite()!=false){"CHART_FEATURE_COORDINATE_INVALID"};ChartPoint(lat,lon,depth)
        }}
    }
}

package com.yokuli.runtime.contract.chart

import kotlin.math.*

/** 静态模型规则身份；Core、Shell 和桌面制包必须同时失效旧的显示产物。 */
const val CHART_TERRAIN_PRODUCT_RULES="terrain-7"

/** 资料三维准备请求；lod=0 为基础层，1 为详细层，均不作为航线分析证据。 */
data class ChartTerrainRequest(val datasetId:String,val revision:Long,val bounds:ChartBounds,val lod:Int=0)
enum class ChartTerrainPhase { QUEUED, PREPARING, READY, FAILED, STALE, CANCELLED, SUBDIVIDED }
/** 状态是 Core 持久作业的回执，READY 才能读块；离开页面不会取消已接受的准备。 */
data class ChartTerrainStatus(
    val key:String,
    val manifestId:String,
    val phase:ChartTerrainPhase,
    val bounds:ChartBounds,
    val lod:Int,
    val reason:String?=null,
)
/** 只读二进制产品。IPC 对 bytes 使用真实 FD 附件，不把网格编码成 JSON 数值数组。 */
data class ChartProductBlock(val key:String,val schema:Int,val bytes:ByteArray,val sha256:String)

/** Core 已接受的持久准备进度；通知按资料聚合，不把每一个块都作为通知。 */
data class ChartTerrainProgress(val datasetId:String,val queued:Int=0,val preparing:Int=0,val ready:Int=0,val failed:Int=0)

/** 海图册、海图、AIS 共用一个确定性的地理网格；先基础层，再详细层。 */
fun terrainPreparationRequests(datasetId:String,revision:Long,origin:ChartPoint,radiusMeters:Double):List<ChartTerrainRequest> {
    require(origin.latitude in -89.8..89.8&&origin.longitude in -180.0..180.0&&radiusMeters.isFinite()) {"CHART_TERRAIN_POSITION_INVALID"}
    // 对齐三维相机的范围档位；例如海图册准备 5 km 会同时覆盖 8 km 观察档，避免两套网格。
    val requested=radiusMeters.coerceIn(500.0,32_000.0)
    val radius=listOf(1_000.0,2_000.0,4_000.0,8_000.0,16_000.0,32_000.0).first{it>=requested}
    val dy=radius/111_320.0;val dx=dy/cos(Math.toRadians(origin.latitude)).coerceAtLeast(.003)
    fun wrap(x:Double)=((x+180.0)%360.0+360.0)%360.0-180.0
    val region=ChartBounds(wrap(origin.longitude-dx),(origin.latitude-dy).coerceAtLeast(-90.0),wrap(origin.longitude+dx),(origin.latitude+dy).coerceAtMost(90.0))
    val longitudeFactor=2.0.pow(ceil(log2(1.0/cos(Math.toRadians(origin.latitude)).coerceAtLeast(.003))))
    var level=18
    while(true) {
        val step=360.0/(1 shl level);val longitudeStep=(step*longitudeFactor).coerceAtMost(360.0)
        val rows=floor((region.south+90)/step).toInt()..floor((region.north+90-1e-10)/step).toInt()
        val ranges=region.split().map{box->floor((box.west+180)/longitudeStep).toInt()..floor((box.east+180-1e-10)/longitudeStep).toInt()}
        val count=rows.count().toLong()*ranges.sumOf{it.count().toLong()}
        if(count in 1..9) {
            val bounds=ranges.flatMap{cols->rows.flatMap{y->cols.map{x->x to y}}}.distinct().map{(x,y)->
                ChartBounds(-180+x*longitudeStep,(-90+y*step).coerceAtLeast(-90.0),(-180+(x+1)*longitudeStep).coerceAtMost(180.0),(-90+(y+1)*step).coerceAtMost(90.0))
            }.sortedBy{box->
                val x=wrap((box.west+box.east)/2-origin.longitude)*cos(Math.toRadians(origin.latitude))
                val y=(box.south+box.north)/2-origin.latitude;x*x+y*y
            }
            return (0..1).flatMap{lod->bounds.map{ChartTerrainRequest(datasetId,revision,it,lod)}}
        }
        require(level>4){"CHART_TERRAIN_POSITION_INVALID"};level--
    }
}

/** 复杂块可继续细分，所有子块仍由 Core 同一持久队列准备。 */
fun ChartTerrainRequest.splitTerrainRequest():List<ChartTerrainRequest> {
    val x=(bounds.west+bounds.east)/2;val y=(bounds.south+bounds.north)/2
    return listOf(ChartBounds(bounds.west,bounds.south,x,y),ChartBounds(x,bounds.south,bounds.east,y),
        ChartBounds(bounds.west,y,x,bounds.north),ChartBounds(x,y,bounds.east,bounds.north)).map{copy(bounds=it)}
}

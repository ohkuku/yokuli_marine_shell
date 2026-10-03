package com.yokuli.runtime.marine.chart.terrain

import com.yokuli.runtime.contract.chart.ChartBounds
import com.yokuli.runtime.contract.chart.ChartPoint

/** 静态展示产物不含船位、AIS、相机或活动航线；它们只能由实时领域快照提供。 */
enum class ChartTerrainSourceKind { ELEVATION_GRID, DEPTH_INTERVALS, CHART_OBJECTS }
enum class ChartTerrainMarkerKind { BEACON, LIGHT, HAZARD, FACILITY, SOUNDING, UNCERTAIN }
enum class ChartTerrainWarning {
    NO_DATA, PARTIAL_CONTENT, GEOMETRY_UNCERTAIN, RASTER_RESOLUTION_LIMIT,
    DEPTH_INTERVALS, LAND_HEIGHT_UNKNOWN, MODEL_BUDGET, VERTICAL_DATUM_MIXED, MISSING_ELEVATION,
}
data class ChartTerrainSource(val id:String,val datasetId:String,val cellId:String,val name:String,val kind:ChartTerrainSourceKind,
    val resolutionMeters:Double?=null,val verticalReference:String?=null,val referenceOnly:Boolean=false)
data class ChartTerrainMarker(val id:String,val title:String,val kind:ChartTerrainMarkerKind,val point:ChartPoint,
    val eastMeters:Double,val elevationMeters:Double,val southMeters:Double,val sourceId:String,val symbolic:Boolean=true,
    val depthLowerMeters:Double?=null,val depthUpperMeters:Double?=null)
data class ChartTerrainCoverage(val rasterSampleCount:Int=0,val missingRasterSamples:Int=0,val displayedSoundings:Int=0,val displayedFacilities:Int=0)
/** x 东、y 高程、z 南，局部原点固定；无测量数据的地方不补造地形。 */
data class ChartTerrainTile(
    val key:String,val origin:ChartPoint,val radiusMeters:Double,val surfaceGlb:ByteArray?,val seabedGlb:ByteArray?,
    val sources:List<ChartTerrainSource>,val markers:List<ChartTerrainMarker>,val warnings:Set<ChartTerrainWarning>,
    val triangleCount:Int,val minElevationMeters:Double,val maxElevationMeters:Double,val datasetRevision:Long,
    val coverage:ChartTerrainCoverage=ChartTerrainCoverage(),val sourceKey:String="",val bounds:ChartBounds?=null,val lod:Int=0,
)

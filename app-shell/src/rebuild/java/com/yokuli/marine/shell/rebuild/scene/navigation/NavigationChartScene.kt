package com.yokuli.marine.shell.rebuild.scene.navigation

import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlin.math.*

/** 三种视角共用一份真实资料和米制场景；切视角不重读图册，也不改变导航会话。 */
enum class NavigationChartMode { FOLLOW, OVERVIEW, SEABED }
enum class NavigationChartSourceKind { ELEVATION_GRID, DEPTH_INTERVALS, CHART_OBJECTS }
enum class NavigationChartMarkerKind { BEACON, LIGHT, HAZARD, FACILITY, SOUNDING, UNCERTAIN }
enum class NavigationChartWarning {
    NO_DATA, PARTIAL_CONTENT, GEOMETRY_UNCERTAIN, RASTER_RESOLUTION_LIMIT,
    DEPTH_INTERVALS, LAND_HEIGHT_UNKNOWN, MODEL_BUDGET, VERTICAL_DATUM_MIXED, MISSING_ELEVATION,
}

/** 原文件的名称、资料基准和真实分辨率；不把区间阶地声称为实测海底表面。 */
data class NavigationChartSource(
    val id:String,
    val datasetId:String,
    val cellId:String,
    val name:String,
    val kind:NavigationChartSourceKind,
    val resolutionMeters:Double?=null,
    val verticalReference:String?=null,
    val referenceOnly:Boolean=false,
)

/** 设施为海图符号，显示高度/宽度不是实物尺寸；深度点仅证明原测量位置。 */
data class NavigationChartMarker(
    val id:String,
    val title:String,
    val kind:NavigationChartMarkerKind,
    val point:GeoPoint,
    val eastMeters:Double,
    val elevationMeters:Double,
    val southMeters:Double,
    val sourceId:String,
    val symbolic:Boolean=true,
    val depthLowerMeters:Double?=null,
    val depthUpperMeters:Double?=null,
)

/** 当前显示窗口内、经过文件优先级遮盖后的实际计数；不是安全覆盖百分比。 */
data class NavigationTerrainCoverage(
    val rasterSampleCount:Int=0,
    val missingRasterSamples:Int=0,
    val displayedSoundings:Int=0,
    val displayedFacilities:Int=0,
)

/**
 * 已完成的不可变展示投影。GLB 字节由生成器独占构建，发布后不得改写。
 * x 向东、y 向上、z 向南，单位米；origin 是固定 WGS84 原点。
 * surfaceGlb 为有依据的陆地轮廓/设施符号，seabedGlb 为真实网格或明确区间阶地。
 * 无数据处不补海底；灯光、海平面、船模、相机与导航线由渲染器统一持有。
 */
data class NavigationChartScene(
    val sceneKey:String,
    val origin:GeoPoint,
    val radiusMeters:Double,
    val surfaceGlb:ByteArray?,
    val seabedGlb:ByteArray?,
    val sources:List<NavigationChartSource>,
    val markers:List<NavigationChartMarker>,
    val warnings:Set<NavigationChartWarning>,
    val triangleCount:Int,
    val minElevationMeters:Double,
    val maxElevationMeters:Double,
    val datasetRevision:Long,
    val coverage:NavigationTerrainCoverage=NavigationTerrainCoverage(),
) {
    val hasGeometry:Boolean get()=surfaceGlb!=null||seabedGlb!=null
}

/** 场景窗口按半径的 1/3 分桶，船位帧和相机动效不触发网格重建。 */
fun navigationTerrainOrigin(point:GeoPoint,radiusMeters:Double=2_000.0):GeoPoint {
    if(!point.valid()||abs(point.lat)>89.8)return point
    val step=radiusMeters.coerceIn(250.0,8_000.0)/3.0
    val latitudeStep=step/111_320.0
    val latitude=(round(point.lat/latitudeStep)*latitudeStep).coerceIn(-89.8,89.8)
    val longitudeStep=step/(111_320.0*cos(Math.toRadians(latitude)).coerceAtLeast(.003))
    val longitude=round(point.lon/longitudeStep)*longitudeStep
    return GeoPoint(latitude,((longitude+180.0)%360.0+360.0)%360.0-180.0)
}

/** 只预取实际航迹方向上的下一个窗口；静止、未知或过期数据由调用方停止预取。 */
fun navigationTerrainPrefetchOrigin(point:GeoPoint,courseTrueDegrees:Double?,speedMetersPerSecond:Double?,radiusMeters:Double=2_000.0):GeoPoint? {
    if(!point.valid()||abs(point.lat)>89.8||courseTrueDegrees?.isFinite()!=true||speedMetersPerSecond?.isFinite()!=true||speedMetersPerSecond<.5)return null
    val radius=radiusMeters.coerceIn(250.0,8_000.0)
    val ahead=(speedMetersPerSecond*90.0).coerceIn(radius/3.0,radius*.6)
    val bearing=Math.toRadians(courseTrueDegrees)
    val latitude=(point.lat+cos(bearing)*ahead/111_320.0).coerceIn(-89.8,89.8)
    val longitude=point.lon+sin(bearing)*ahead/(111_320.0*cos(Math.toRadians(point.lat)).coerceAtLeast(.003))
    val next=navigationTerrainOrigin(GeoPoint(latitude,((longitude+180.0)%360.0+360.0)%360.0-180.0),radius)
    return next.takeIf {it!=navigationTerrainOrigin(point,radius)}
}

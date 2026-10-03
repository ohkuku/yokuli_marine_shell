package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.yokuli.anchorwatch.domain.vessel.VesselDataSnapshot
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.bearing
import com.yokuli.marine.shell.rebuild.distance
import com.yokuli.marine.shell.rebuild.data.Fix
import com.yokuli.marine.shell.rebuild.scene.navigation.*
import com.yokuli.runtime.contract.chart.ChartColorMode
import com.yokuli.runtime.contract.navigation.NavigationSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 只抽取画面需要的观测；气压、原始报文、候选来源更新不能重组三维入口。 */
private data class SpatialVesselReading(
    val heading:VesselObservation<Double>, val magnetic:VesselObservation<Double>,
    val course:VesselObservation<Double>, val heel:VesselObservation<Double>, val pitch:VesselObservation<Double>,
    val mounted:Boolean, val mountNeedsConfirmation:Boolean, val lengthMeters:Double,
)
private fun VesselDataSnapshot.spatialReading(mounted:Boolean,confirmation:Boolean,length:Double)=SpatialVesselReading(
    headingTrueDegrees,headingMagneticDegrees,cogTrueDegrees,heelDegrees,pitchDegrees,mounted,confirmation,length)

@Composable
internal fun ChartNavigationSpatial(
    os:OsStore,
    fix:Fix?,
    now:Long,
    active:Boolean,
    modifier:Modifier=Modifier,
    onOpenMap:()->Unit,
    onOpenTarget:(String)->Unit,
    onAutomaticSwitchInhibited:(Boolean)->Unit,
    onNavigation:()->Unit,
){
    val marine=os.marine?:return
    val stream=remember(marine){marine.services.state.map{state->state.vesselData.spatialReading(
        state.vesselMountCalibration.mountConfirmed&&state.phoneVesselMountState==PhoneVesselMountState.VESSEL_MOUNTED,
        state.vesselMountCalibration.calibratedAt>0&&state.phoneVesselMountState==PhoneVesselMountState.MOUNT_SUSPECT,
        state.settings.boatLengthMeters)}.distinctUntilChanged()}
    val initial=remember(marine){marine.services.state.value.let{state->state.vesselData.spatialReading(
        state.vesselMountCalibration.mountConfirmed&&state.phoneVesselMountState==PhoneVesselMountState.VESSEL_MOUNTED,
        state.vesselMountCalibration.calibratedAt>0&&state.phoneVesselMountState==PhoneVesselMountState.MOUNT_SUSPECT,state.settings.boatLengthMeters)}}
    val data by stream.collectAsState(initial)
    val traffic=rememberAisSceneData(os,rememberAisTraffic(os),showTracks=true)
    val charts=os.maps.charts
    val datasetIds=os.maps.selectedDatasetIds.toList()
    val nav=os.navigationState
    val shownRoute=chartRoute(os)
    val navigating=chartIsNavigating(os)||nav.session?.let{it.ongoing&&it.source==NavigationSource.EXTERNAL_NMEA&&shownRoute==null}==true
    val preview=!navigating&&shownRoute!=null
    val guidance=nav.guidance.takeIf{navigating}
    // 浏览收藏或地图上的区域不跳回远处船位；“船位”才重新开启随船。
    var followVessel by rememberSaveable{mutableStateOf(navigating||(!preview&&os.follow))}
    val browseLatitude=rememberSaveable{os.center.lat}
    val browseLongitude=rememberSaveable{os.center.lon}
    val browsePoint=GeoPoint(browseLatitude,browseLongitude)
    val vesselPoint=fix?.point?.takeIf(GeoPoint::valid)
    var freeCenter by remember{mutableStateOf<GeoPoint?>(null)}
    val focus=if(followVessel)null else browsePoint
    val sceneCenter=freeCenter?:focus?:vesselPoint?:browsePoint
    // 首个数据窗口与实际相机一致，避免随船首帧把刚启动的 4 km 全国海岸裁剪取消，
    // 再从头启动 1 km 窗口。远近变化随后由原生相机报告。
    var cameraRadius by remember{mutableDoubleStateOf(if(preview||focus!=null)4_000.0 else 1_000.0)}
    val terrain=rememberChartTerrain(charts,datasetIds,sceneCenter,cameraRadius,
        fix?.freshCourse(now),fix?.freshSpeed(now)?.times(.5144444444),active)
    val origin=terrain.scene?.origin?:navigationTerrainOrigin(sceneCenter,terrain.radiusMeters)
    val current=guidance?.let{SpatialNavigationTarget(it.targetId?:"external-current",it.targetName?:os.t("当前目标","Current target"),
        it.bearingTrueDegrees,it.distanceMeters,it.nearTarget,point=it.targetPosition?.let{p->GeoPoint(p.lat,p.lon)})}
    val nextIndex=nav.session?.takeIf{navigating}?.let{session->session.route?.targetIndices?.firstOrNull{it>session.targetIndex}}
    val next=nextIndex?.let{nav.session?.route?.waypoints?.getOrNull(it)}?.let{point->
        val p=GeoPoint(point.point.lat,point.point.lon)
        val observed=fix?.takeIf{it.point.valid()&&guidance?.live==true&&guidance.positionElapsedMillis==it.elapsed}
        SpatialNavigationTarget(point.id,point.name,observed?.let{bearing(it.point,p)},observed?.let{distance(it.point,p)},point=p)
    }
    val steering=guidance?.takeIf{it.geometryIndex!=null&&it.geometryIndex!=nav.session?.targetIndex}?.let{g->
        SpatialNavigationTarget("navigation:steering",os.t("沿线方向","Follow the route"),g.steeringBearingTrueDegrees,
            g.steeringPosition?.let{point->fix?.takeIf{g.live&&it.point.valid()&&it.elapsed==g.positionElapsedMillis}?.point?.let{distance(it,GeoPoint(point.lat,point.lon))}},
            steering=true,point=g.steeringPosition?.let{GeoPoint(it.lat,it.lon)})
    }
    val position=fix?.takeIf{it.point.valid()&&it.utc>0}?.let{SpatialReferencePosition(it.point.lat,it.point.lon,it.utc)}
    val conversion=remember(position,now/60_000){SpatialNorthConversion.from(position,System.currentTimeMillis())}
    fun live(observation:VesselObservation<Double>)=observation.displayIsLive()&&observation.receivedElapsedRealtime?.let{now-it in 0..10_000L}==true
    fun direction(observation:VesselObservation<Double>,declination:Double=0.0):SpatialDirection?{
        val value=observation.value?.takeIf{it.isFinite()&&live(observation)}?:return null
        val at=observation.receivedElapsedRealtime?:return null
        return SpatialDirection(wrapBearing(value+declination),observation.sourceIdentity?.displayName?:observation.provenance?:observation.source.name,now-at,at)
    }
    val heading=direction(data.heading)?:conversion?.let{direction(data.magnetic,it.declinationDegrees)?.let{d->d.copy(source=d.source+os.t(" · 磁北换算"," · converted from magnetic north"))}}
    val route=remember(navigating,nav.session?.route,shownRoute){
        if(navigating)nav.session?.route?.geometry.orEmpty().map{GeoPoint(it.lat,it.lon)} else shownRoute?.points.orEmpty()
    }
    NavigationSpatialView(
        snapshot=NavigationSpatialSnapshot(current,next,heading,direction(data.course),
            if(data.mounted||data.mountNeedsConfirmation)SpatialMountMode.VESSEL_MOUNTED else SpatialMountMode.HANDHELD,position,
            data.heel.takeIf(::live)?.value,data.pitch.takeIf(::live)?.value,fix?.fresh(now)==true,
            steering=steering,mountNeedsConfirmation=data.mountNeedsConfirmation),
        display=marine.services.display,units=os.unitPreferences,chinese=os.chinese,active=active,modifier=modifier,
        onOpenMap=onOpenMap,onOpenTarget=onOpenTarget,onAutomaticSwitchInhibited=onAutomaticSwitchInhibited,
        onOpenSources={os.openLinked(if(data.mountNeedsConfirmation)"data_center:mount"else"data_center:source/HEADING_TRUE")},
        chartScene=terrain.scene,traffic=traffic,terrainLoading=terrain.loading,terrainError=terrain.error?.let{if(it=="CHART_TERRAIN_QUERY_TIMEOUT")os.t("附近资料读取超时，可以重试。","Nearby data took too long to load. Try again.")else chartDataError(os,it)},viewOrigin=origin,focusPoint=focus,route=route,
        navigation=nav.takeIf{navigating},routeTitle=shownRoute?.name,preview=preview,
        speedKnots=fix?.freshSpeed(now),positionAgeMillis=fix?.let{(now-it.elapsed).coerceAtLeast(0)},
        onNavigation=onNavigation,onFollowVessel={followVessel=true;freeCenter=null},
        onExploringChanged={freeCenter=if(it)focus?:vesselPoint?:browsePoint else null},
        onOpenLibrary={os.openLinked(os.maps.activeBundleId?.let{"library:bundle/$it"}?:"library")},
        onOpenPositionSource={os.openLinked("data_center:source/POSITION")},
        night=os.maps.portrayalPreferences.colorMode==ChartColorMode.NIGHT,vesselLengthMeters=data.lengthMeters,
        onViewAreaChanged={point,radius->
            cameraRadius=radius.coerceIn(1_000.0,32_000.0)
            // 跟手相机保持在原生渲染层，仅实际平移时更新资料视野；不改变船位/导航。
            if(freeCenter!=null)freeCenter=point
        },
        onRetryTerrain=terrain.retry,onOpenChartPoint={point->onOpenMap();os.fly(point,os.zoom.coerceAtLeast(14.0))})
}

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
import kotlinx.coroutines.CancellationException
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
    val charts=os.maps.charts
    val chartState by charts.state.collectAsState()
    val datasetIds=os.maps.selectedDatasetIds.toList()
    val datasetRevision=chartState.datasets.filter{it.id in datasetIds}.map{Triple(it.id,it.revision,it.offlineReadable)}
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
    val selectedDataset=chartState.datasets.firstOrNull{it.id in datasetIds}
    val radius=remember(selectedDataset?.id,selectedDataset?.revision,selectedDataset?.rasters,sceneCenter){navigationTerrainRadius(selectedDataset,sceneCenter)}
    var origin by remember(radius){mutableStateOf(navigationTerrainOrigin(sceneCenter,radius))}
    // 越过窗口内圈才迁移；定位在分桶边界来回抖动时不反复建模。
    LaunchedEffect(sceneCenter,radius){if(distance(origin,sceneCenter)>radius/3.0)origin=navigationTerrainOrigin(sceneCenter,radius)}
    val loader=remember(charts){NavigationTerrainLoader(charts)}
    var terrain by remember(loader){mutableStateOf<NavigationChartScene?>(null)}
    var loading by remember(loader){mutableStateOf(false)}
    var error by remember(loader){mutableStateOf<String?>(null)}
    var retry by remember{mutableIntStateOf(0)}
    val resumed=rememberNavigationResumed()
    var loadedKey by remember(loader){mutableStateOf<String?>(null)}
    val sourceKey="$datasetIds:$datasetRevision"
    var loadedSourceKey by remember(loader){mutableStateOf<String?>(null)}
    val loadKey="$sourceKey:$origin:$radius:$retry"
    LaunchedEffect(loader,loadKey,active,resumed){
        if(loadedSourceKey!=sourceKey){loader.clearSource();terrain=null;loadedKey=null;loadedSourceKey=sourceKey}
        if(!active||!resumed)return@LaunchedEffect
        if(loadedKey==loadKey)return@LaunchedEffect
        error=null
        if(datasetIds.isEmpty()){loading=false;loadedKey=loadKey;return@LaunchedEffect}
        loading=true
        try{
            // 同资料移窗保留上一片真实地形，完整新窗口到达才原子替换。
            terrain=loader.load(datasetIds,origin,radius);loadedKey=loadKey
        }catch(cancel:CancellationException){throw cancel}
        catch(failure:Exception){error=if(failure.message=="CHART_TERRAIN_QUERY_TIMEOUT")
            os.t("附近资料读取超时，可以重试。","Nearby data took too long to load. Try again.")
            else chartDataError(os,failure.message?:"CHART_SOURCE_UNAVAILABLE")
        }finally{loading=false}
    }
    val prefetchOrigin=if(followVessel&&freeCenter==null&&fix?.fresh(now)==true)
        navigationTerrainPrefetchOrigin(fix.point,fix.freshCourse(now),fix.freshSpeed(now)?.times(.5144444444),radius)else null
    LaunchedEffect(loader,sourceKey,loadedKey,loadKey,prefetchOrigin,active,resumed){
        if(!active||!resumed||loadedKey!=loadKey||prefetchOrigin==null||prefetchOrigin==origin||datasetIds.isEmpty())return@LaunchedEffect
        // 预取跟随可见页面的生命周期，不能抢前台当前窗口或持有无限任务。
        loader.prefetch(datasetIds,prefetchOrigin,radius)
    }
    DisposableEffect(loader){onDispose{loader.clearSource()}}
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
        chartScene=terrain.takeIf{loadedSourceKey==sourceKey},terrainLoading=loading,terrainError=error,viewOrigin=origin,focusPoint=focus,route=route,
        navigation=nav.takeIf{navigating},routeTitle=shownRoute?.name,preview=preview,
        speedKnots=fix?.freshSpeed(now),positionAgeMillis=fix?.let{(now-it.elapsed).coerceAtLeast(0)},
        onNavigation=onNavigation,onFollowVessel={followVessel=true;freeCenter=null},
        onExploringChanged={freeCenter=if(it)focus?:vesselPoint?:browsePoint else null},
        onOpenLibrary={os.openLinked(os.maps.activeBundleId?.let{"library:bundle/$it"}?:"library")},
        onOpenPositionSource={os.openLinked("data_center:source/POSITION")},
        night=os.maps.portrayalPreferences.colorMode==ChartColorMode.NIGHT,vesselLengthMeters=data.lengthMeters,
        onRetryTerrain={retry++},onOpenChartPoint={point->onOpenMap();os.fly(point,os.zoom.coerceAtLeast(14.0))})
}

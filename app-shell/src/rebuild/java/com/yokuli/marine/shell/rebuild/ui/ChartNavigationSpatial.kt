package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 只抽取此画面需要的事实，船体连续姿态不使整个Chart页反复重组。 */
private data class SpatialVesselReading(val vessel:VesselDataSnapshot,val mounted:Boolean)

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
){
    val marine=os.marine?:return
    val stream=remember(marine){marine.services.state.map{state->SpatialVesselReading(state.vesselData,state.vesselMountCalibration.mountConfirmed&&state.phoneVesselMountState==PhoneVesselMountState.VESSEL_MOUNTED)}.distinctUntilChanged()}
    val data by stream.collectAsState(SpatialVesselReading(marine.services.state.value.vesselData,
        marine.services.state.value.vesselMountCalibration.mountConfirmed&&marine.services.state.value.phoneVesselMountState==PhoneVesselMountState.VESSEL_MOUNTED))
    val nav=os.navigationState
    val guidance=nav.guidance
    val current=guidance?.let{SpatialNavigationTarget(it.targetId?:"external-current",it.targetName?:os.t("当前目标","Current target"),it.bearingTrueDegrees,it.distanceMeters,it.nearTarget)}
    val nextIndex=nav.session?.let{session->session.route?.targetIndices?.firstOrNull{it>session.targetIndex}}
    val next=nextIndex?.let{nav.session?.route?.waypoints?.getOrNull(it)}?.let{point->
        val p=GeoPoint(point.point.lat,point.point.lon)
        SpatialNavigationTarget(point.id,point.name,fix?.takeIf{it.point.valid()&&guidance?.live==true&&guidance.positionElapsedMillis==it.elapsed}?.let{bearing(it.point,p)},fix?.takeIf{it.point.valid()&&guidance?.live==true&&guidance.positionElapsedMillis==it.elapsed}?.let{distance(it.point,p)})
    }
    val steering=guidance?.takeIf{it.geometryIndex!=null&&it.geometryIndex!=nav.session?.targetIndex}?.let{g->
        SpatialNavigationTarget("navigation:steering",os.t("沿线方向","Follow the route"),g.steeringBearingTrueDegrees,
            g.steeringPosition?.let{point->fix?.takeIf{g.live&&it.point.valid()&&it.elapsed==g.positionElapsedMillis}?.point?.let{distance(it,GeoPoint(point.lat,point.lon))}},steering=true)
    }
    val position=fix?.takeIf{it.point.valid()&&it.utc>0}?.let{SpatialReferencePosition(it.point.lat,it.point.lon,it.utc)}
    val conversion=remember(position){SpatialNorthConversion.from(position,System.currentTimeMillis())}
    fun direction(observation:VesselObservation<Double>,declination:Double=0.0):SpatialDirection?{
        val value=observation.value?.takeIf{it.isFinite()&&observation.displayIsLive()}?:return null
        val elapsed=observation.receivedElapsedRealtime?:return null
        return SpatialDirection(wrapBearing(value+declination),observation.sourceIdentity?.displayName?:observation.provenance?:observation.source.name,now-elapsed,elapsed)
    }
    val heading=direction(data.vessel.headingTrueDegrees)?:conversion?.let{direction(data.vessel.headingMagneticDegrees,it.declinationDegrees)?.let{d->d.copy(source=d.source+os.t(" · 磁北换算"," · converted from magnetic north"))}}
    NavigationSpatialView(NavigationSpatialSnapshot(current,next,heading,direction(data.vessel.cogTrueDegrees),
        if(data.mounted)SpatialMountMode.VESSEL_MOUNTED else SpatialMountMode.HANDHELD,position,
        data.vessel.heelDegrees.takeIf{it.displayIsLive()}?.value,data.vessel.pitchDegrees.takeIf{it.displayIsLive()}?.value,
        guidance?.live==true,if(guidance?.issue!=null)os.t("导航保留上次观测，等待新位置","Navigation is holding the last observation while waiting for a position")else null,steering=steering),
        marine.services.display,os.unitPreferences,os.chinese,active,modifier,onOpenMap,onOpenTarget,onAutomaticSwitchInhibited)
}

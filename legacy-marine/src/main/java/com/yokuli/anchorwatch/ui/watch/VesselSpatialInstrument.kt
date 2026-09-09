package com.yokuli.anchorwatch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState
import com.yokuli.anchorwatch.ui.theme.Wp8TextButton as TextButton
import kotlin.math.*

private enum class VesselView { SPACE, STERN, SIDE, ABOVE }
private enum class SpatialReading { HEEL, PITCH, HEADING }

/** A geometric view of the canonical observations, not another sensor owner. */
@Composable
fun VesselSpatialInstrument(
    state: MainUiState,
    locked: Boolean,
    confirmFrame: () -> Unit,
    pauseAttitude: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by rememberSaveable { mutableStateOf(VesselView.SPACE) }
    var selected by remember { mutableStateOf<SpatialReading?>(null) }
    val attitude = state.vesselData.attitude
    val phoneFrameReady = state.vesselMountCalibration.mountConfirmed &&
        state.phoneVesselMountState == PhoneVesselMountState.VESSEL_MOUNTED
    val attitudeLive = attitude.freshness == VesselDataFreshness.FRESH && attitude.value?.let { it.heelDegrees.isFinite() && it.pitchDegrees.isFinite() } == true &&
        (attitude.source != VesselDataSource.PHONE_IMU || phoneFrameReady)
    val trueHeading = state.vesselData.headingTrueDegrees
    val magneticHeading = state.vesselData.headingMagneticDegrees
    val heading = if (trueHeading.freshness == VesselDataFreshness.FRESH && trueHeading.value?.isFinite() == true && trueHeading.reference != VesselReference.MagneticNorth)
        trueHeading else magneticHeading
    val magnetic = heading === magneticHeading
    val headingLive = heading.freshness == VesselDataFreshness.FRESH && heading.value?.isFinite() == true
    val demo = state.settings.demoMode || attitude.source == VesselDataSource.DEMO || heading.source == VesselDataSource.DEMO
    val liveHeel = attitude.value?.heelDegrees?.takeIf { attitudeLive && it.isFinite() }
    val livePitch = attitude.value?.pitchDegrees?.takeIf { attitudeLive && it.isFinite() }
    val heel = smoothVesselAngle(liveHeel ?: 0.0, "heel")
    val pitch = smoothVesselAngle(livePitch ?: 0.0, "pitch")
    val yaw = smoothVesselAngle(heading.value?.takeIf { headingLive } ?: 0.0, "heading")
    val status = when {
        demo -> tr("DEMO · simulated measurements", "演示 · 模拟读数")
        attitudeLive -> tr("Live vessel attitude", "船体姿态 · 实时")
        attitude.value != null && attitude.freshness != VesselDataFreshness.FRESH -> tr("Attitude is stale · outline only", "姿态已过期 · 船形仅示意")
        !state.phoneSensorCapabilities.attitudeAvailable -> tr("Waiting for an attitude source · outline only", "等待姿态来源 · 船形仅示意")
        !phoneFrameReady -> tr("Confirm the phone mounting to see vessel attitude", "确认手机安装后显示船体姿态")
        else -> tr("Waiting for attitude · outline only", "等待姿态 · 船形仅示意")
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(status, style=MaterialTheme.typography.bodySmall,
            color=if(demo)MaterialTheme.colorScheme.tertiary else if(attitudeLive)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            VesselView.entries.forEach { target ->
                TextButton({view=target}, Modifier.weight(1f).testTag("vessel_view_${target.name}"), enabled=!locked,
                    contentPadding=PaddingValues(horizontal=2.dp,vertical=6.dp)) {
                    Text(when(target){VesselView.SPACE->tr("space","空间");VesselView.STERN->tr("stern","船尾");VesselView.SIDE->tr("side","舷侧");VesselView.ABOVE->tr("above","俯视")},
                        fontSize=17.sp, color=if(view==target)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val description = tr("Vessel attitude. $status", "船体姿态。$status")
        Box(Modifier.fillMaxWidth().height(230.dp).testTag("vessel_spatial_view").semantics { contentDescription=description }) {
            VesselMeshCanvas(heel, pitch, yaw, view, attitudeLive, headingLive,
                MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface,
                Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopStart), verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(if(headingLive)tr(if(magnetic)"magnetic north" else "true north",if(magnetic)"磁北参考" else "真北参考") else tr("heading unavailable","船首向不可用"),
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(view==VesselView.STERN || view==VesselView.SIDE) Text(tr("view follows the vessel","视角跟随船体"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(!attitudeLive)Text(tr("OUTLINE · NOT A LEVEL READING","轮廓示意 · 不代表水平"),
                Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.background.copy(alpha=.9f)).padding(4.dp),
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            SpatialValue(tr("heel","横倾"), liveHeel?.let { "%+.1f°".format(it) } ?: "—", Modifier.weight(1f), !locked) { selected=SpatialReading.HEEL }
            SpatialValue(tr("pitch","纵倾"), livePitch?.let { "%+.1f°".format(it) } ?: "—", Modifier.weight(1f), !locked) { selected=SpatialReading.PITCH }
            SpatialValue(tr("heading","船首向"), heading.value?.takeIf { headingLive }?.let { "%03.0f°%s".format(it,if(magnetic)"M" else "T") } ?: "—", Modifier.weight(1f), !locked) { selected=SpatialReading.HEADING }
        }
        Text(tr("Tap a reading for its source. Swipe for the other instrument pages.","点读数查看来源，左右滑动切换仪表页。"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.phoneSensorCapabilities.attitudeAvailable && (attitude.source == VesselDataSource.PHONE_IMU || !attitudeLive)) {
            TextButton(if(phoneFrameReady)pauseAttitude else confirmFrame, enabled=!locked && state.activeTrip?.paused!=true,
                modifier=Modifier.fillMaxWidth().testTag("vessel_attitude_mount_action")) {
                Text(if(phoneFrameReady)tr("Picking up the phone · pause attitude","要拿起手机 · 暂停姿态") else tr("Place the phone · confirm mounting","放好手机 · 确认安装"))
            }
            state.vesselCalibrationFeedback?.takeIf { it.isNotBlank() }?.let { feedback ->
                Text(when(feedback){
                    "Trip attitude frame confirmed."->tr("Mounting confirmed. The vessel keeps its actual tilt.","安装已确认，保留船当前真实倾斜。")
                    "No rotation-vector sample is available on this phone."->tr("Waiting for the phone rotation sensor.","正在等待手机旋转传感器读数。")
                    "Resume the trip before confirming a new attitude segment."->tr("Resume the trip before confirming mounting.","请先恢复航次，再确认安装。")
                    "Trip attitude capture paused. Heading, GPS and pressure continue."->tr("Attitude paused. Other instruments continue.","姿态已暂停，其他仪表继续运行。")
                    else->feedback
                },style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    selected?.let { reading ->
        val value:VesselObservation<*> = if(reading==SpatialReading.HEADING)heading else attitude
        SpatialSourceDialog(reading,value,phoneFrameReady,headingLive,magnetic,demo) { selected=null }
    }
}

@Composable
private fun SpatialValue(label:String,value:String,modifier:Modifier,enabled:Boolean,open:()->Unit) {
    Column(modifier.clickable(enabled=enabled,onClick=open).padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)
        Text(value,fontWeight=FontWeight.Light,fontSize=29.sp,maxLines=1)
    }
}

@Composable
private fun SpatialSourceDialog(reading:SpatialReading,value:VesselObservation<*>,mounted:Boolean,headingLive:Boolean,magnetic:Boolean,demo:Boolean,dismiss:()->Unit) {
    val now by produceState(android.os.SystemClock.elapsedRealtime()) {
        while(true){kotlinx.coroutines.delay(1_000L);this.value=android.os.SystemClock.elapsedRealtime()}
    }
    AlertDialog(onDismissRequest=dismiss,
        title={Text(when(reading){SpatialReading.HEEL->tr("heel source","横倾来源");SpatialReading.PITCH->tr("pitch source","纵倾来源");SpatialReading.HEADING->tr("heading source","船首向来源")})},
        confirmButton={TextButton(dismiss){Text(tr("close","关闭"))}},
        text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val source=value.sourceIdentity?.displayName ?: when(value.source) {
                VesselDataSource.BOAT_NMEA->tr("Boat NMEA","船载 NMEA")
                VesselDataSource.PHONE_IMU->tr("Mounted phone motion sensor","已安装手机运动传感器")
                VesselDataSource.PHONE_MAGNETOMETER->tr("Phone compass","手机罗盘")
                VesselDataSource.DEMO->tr("Demo","演示")
                VesselDataSource.NONE->tr("No source","无来源")
                else->tr("Shared vessel data","共享船舶数据")
            }
            Text(source,style=MaterialTheme.typography.titleMedium)
            Text(when(value.freshness){VesselDataFreshness.FRESH->tr("Fresh measurement","新鲜读数");VesselDataFreshness.HELD->tr("Held measurement · not animating the vessel","保留读数 · 不驱动船体动画");VesselDataFreshness.STALE->tr("Expired measurement · not animating the vessel","读数过期 · 不驱动船体动画");VesselDataFreshness.UNAVAILABLE->tr("No measurement received","尚未收到读数")})
            Text(tr("Received ${value.receivedElapsedRealtime?.let { "%.1f".format((now-it).coerceAtLeast(0L)/1_000.0) } ?: "—"} s ago",
                "距接收 ${value.receivedElapsedRealtime?.let { "%.1f".format((now-it).coerceAtLeast(0L)/1_000.0) } ?: "—"} 秒"))
            Text(when(value.quality){VesselDataQuality.GOOD->tr("Quality: good","质量：良好");VesselDataQuality.DEGRADED->tr("Quality: degraded","质量：降低");VesselDataQuality.UNKNOWN->tr("Quality: unknown","质量：未知")})
            value.provenance?.takeIf { it.isNotBlank() }?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            if(value.conflict?.active==true)Text(tr("The available sources disagree. This view uses the selected source; it does not average them.","可用来源存在冲突。此视图采用已选来源，不对它们取平均。"),color=MaterialTheme.colorScheme.error)
            HorizontalDivider()
            if(reading==SpatialReading.HEADING)Text(if(headingLive)tr(if(magnetic)"M is magnetic heading. It is not relabelled as true north or replaced by GPS course." else "T is true heading. GPS course is a different measurement and does not rotate this vessel.",if(magnetic)"M 代表磁船首向，不会冒充真北或用 GPS 航迹向替代。" else "T 代表真船首向。GPS 航迹向是另一项读数，不用来旋转此船体。") else tr("A missing heading stays unavailable. No phone or GPS fallback is invented here.","船首向缺失时保持不可用，不在此添加手机或 GPS 回退。"))
            else Text(if(mounted || value.source != VesselDataSource.PHONE_IMU)tr("The hull follows the selected signed heel and pitch. Confirming the mounting preserves the boat's current tilt; it is not a zero adjustment.","船体跟随已采用的带正负号横倾和纵倾。确认安装会保留船当前的倾斜，不是归零操作。") else tr("Confirm the phone-to-vessel mounting before using phone attitude. The outline is a shape reference only.","使用手机姿态前须确认手机与船体的安装关系。轮廓只是船形参考。"))
            if(demo)Text(tr("DEMO values never imply a real vessel reading.","演示值不代表真实船体读数。"),color=MaterialTheme.colorScheme.tertiary)
        }})
}

@Composable
private fun smoothVesselAngle(raw:Double,label:String):Float {
    var target by remember { mutableDoubleStateOf(raw) }
    LaunchedEffect(raw) { target += ((raw-target+540.0)%360.0+360.0)%360.0-180.0 }
    return animateFloatAsState(target.toFloat(),tween(180),label=label).value
}

private data class V3(val x:Float,val y:Float,val z:Float)
private data class HullFace(val points:List<V3>,val shade:Float,val accent:Boolean=false)

// Local axes are starboard (+x), bow (+y), up (+z). One convex ring is the deck,
// a second the chine; the lower keel and cabin give the flat projection depth.
private val hullDeck=listOf(V3(0f,1.9f,.18f),V3(.46f,1.15f,.18f),V3(.60f,.1f,.18f),V3(.50f,-1.45f,.18f),V3(-.50f,-1.45f,.18f),V3(-.60f,.1f,.18f),V3(-.46f,1.15f,.18f))
private val hullChine=hullDeck.map { V3(it.x*.82f,it.y*.93f,-.23f) }
private val hullFaces=buildList {
    add(HullFace(hullDeck, .82f))
    hullDeck.indices.forEach { i -> val j=(i+1)%hullDeck.size
        add(HullFace(listOf(hullDeck[i],hullDeck[j],hullChine[j],hullChine[i]), .36f+(i%3)*.12f,true))
        add(HullFace(listOf(hullChine[i],hullChine[j],V3(0f,if(i in 0..1 || i==6).55f else -.55f,-.48f)),.25f+(i%2)*.12f))
    }
    val base=listOf(V3(-.32f,-.65f,.19f),V3(.32f,-.65f,.19f),V3(.28f,.53f,.19f),V3(-.28f,.53f,.19f))
    val top=base.map { V3(it.x*.80f,it.y*.83f,.55f) }
    add(HullFace(top,.92f))
    base.indices.forEach { i -> val j=(i+1)%4;add(HullFace(listOf(base[i],base[j],top[j],top[i]),if(i==2).72f else .56f)) }
}

@Composable
private fun VesselMeshCanvas(heel:Float,pitch:Float,heading:Float,view:VesselView,attitudeLive:Boolean,headingLive:Boolean,accent:Color,foreground:Color,modifier:Modifier) {
    Canvas(modifier) {
        val scale=min(size.width/5.5f,size.height/4.4f)
        val origin=Offset(size.width/2f,size.height*.56f)
        val azimuth=Math.toRadians(when(view){VesselView.SPACE->-28.0;VesselView.SIDE->90.0;else->0.0}).toFloat()
        val elevation=Math.toRadians(when(view){VesselView.SPACE->25.0;VesselView.STERN->8.0;VesselView.SIDE->8.0;VesselView.ABOVE->85.0}).toFloat()
        val roll=Math.toRadians(if(attitudeLive)heel.toDouble()else 0.0).toFloat()
        val trim=Math.toRadians(if(attitudeLive)pitch.toDouble()else 0.0).toFloat()
        val yaw=Math.toRadians(if(headingLive && view in setOf(VesselView.SPACE,VesselView.ABOVE))heading.toDouble()else 0.0).toFloat()
        fun vessel(point:V3):V3 {
            val x=point.x*cos(roll)+point.z*sin(roll);val z=-point.x*sin(roll)+point.z*cos(roll)
            val y=point.y*cos(trim)-z*sin(trim);val up=point.y*sin(trim)+z*cos(trim)
            return V3(x*cos(yaw)+y*sin(yaw),-x*sin(yaw)+y*cos(yaw),up)
        }
        fun camera(p:V3):V3 {val right=p.x*cos(azimuth)+p.y*sin(azimuth);val away=-p.x*sin(azimuth)+p.y*cos(azimuth)
            return V3(right,-away*sin(elevation)-p.z*cos(elevation),away*cos(elevation)-p.z*sin(elevation))}
        fun project(p:V3)=camera(p).let { origin+Offset(it.x*scale,it.y*scale) }
        fun outline(points:List<V3>):Path=Path().apply { points.forEachIndexed { i,p -> val xy=project(p);if(i==0)moveTo(xy.x,xy.y)else lineTo(xy.x,xy.y) };close() }
        val sea=foreground.copy(alpha=.10f)
        for(i in -3..3) {
            drawLine(sea,project(V3(i*.7f,-2.4f,-.13f)),project(V3(i*.7f,2.4f,-.13f)),1.dp.toPx())
            drawLine(sea,project(V3(-2.1f,i*.7f,-.13f)),project(V3(2.1f,i*.7f,-.13f)),1.dp.toPx())
        }
        if(headingLive && view in setOf(VesselView.SPACE,VesselView.ABOVE)) {
            val north=project(V3(0f,2.5f,-.13f));val near=project(V3(0f,1.85f,-.13f))
            drawLine(accent.copy(alpha=.6f),near,north,2.dp.toPx())
            drawCircle(accent,3.dp.toPx(),north)
        }
        if(attitudeLive) {
            hullFaces.map { face->face to face.points.map(::vessel) }.sortedByDescending { (_,p)->p.map { camera(it).z }.average() }.forEach { (face,p) ->
                val fill=if(face.accent)accent.copy(alpha=.4f+face.shade*.45f)else foreground.copy(alpha=face.shade*.55f)
                drawPath(outline(p),fill)
                drawPath(outline(p),foreground.copy(alpha=.40f),style=Stroke(1.dp.toPx()))
            }
        } else {
            hullFaces.forEach { face -> drawPath(outline(face.points.map(::vessel)),foreground.copy(alpha=.22f),style=Stroke(1.dp.toPx())) }
        }
        val bow=project(vessel(V3(0f,1.9f,.20f)))
        val centre=project(vessel(V3(0f,.65f,.20f)))
        drawLine(if(attitudeLive)accent else foreground.copy(alpha=.3f),centre,bow,3.dp.toPx())
        drawCircle(if(attitudeLive)accent else foreground.copy(alpha=.3f),3.dp.toPx(),bow)
    }
}

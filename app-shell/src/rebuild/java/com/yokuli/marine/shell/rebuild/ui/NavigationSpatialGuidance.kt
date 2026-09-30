package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.MarineUnitFormats
import com.yokuli.marine.shell.rebuild.scene.navigation.NavigationSpatialSnapshot
import com.yokuli.runtime.contract.navigation.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

/** 只呈现 Core 已发布的导航结果；三维画面没有第二份推进、ETA 或偏航算法。 */
@Composable
internal fun SpatialNavigationSummary(
    navigation:NavigationState?,routeTitle:String?,preview:Boolean,formats:MarineUnitFormats,
    chinese:Boolean,enabled:Boolean,onNavigation:()->Unit,
){
    fun tr(zh:String,en:String)=if(chinese)zh else en
    val c=LocalMetro.current
    val session=navigation?.session
    val guidance=navigation?.guidance
    if(session==null&&!preview)return
    val phase=when{
        preview->tr("航线预览","Route preview")
        session?.phase==NavigationPhase.PAUSED->tr("导航已暂停","Guidance paused")
        session?.phase==NavigationPhase.RECOVERY_REQUIRED->tr("等待继续导航","Resume your navigation")
        guidance?.live!=true->tr("引导等待新船位","Guidance awaiting position")
        session?.source==NavigationSource.EXTERNAL_NMEA->tr("设备导航","Device navigation")
        guidance.nearTarget->tr("已到目标附近","Near your waypoint")
        else->tr("正在导航","Navigating")
    }
    Label(phase,11,c.muted)
    Label(if(preview)routeTitle.orEmpty()else guidance?.targetName?:routeTitle?:tr("当前目标","Current target"),16,maxLines=1)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        if(!preview)Label(formats.distance(guidance?.distanceAlongRouteToTargetMeters?:guidance?.distanceMeters),22,
            if(guidance?.live==true)c.fg else c.muted,Modifier.weight(1f),maxLines=1)
        SpatialTextAction(if(preview)tr("开始导航","Start navigation")else tr("导航操作","Navigation"),enabled,onNavigation)
    }
    if(guidance!=null){
        val eta=remember(guidance.etaRouteUtcMillis,chinese){guidance.etaRouteUtcMillis?.let{DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}}
        val remaining=guidance.remainingMeters?.let{tr("剩余 ","Remaining ")+formats.distance(it)}
        val estimate=eta?.let{(if(guidance.etaBasis==NavigationEtaBasis.PLAN_SPEED)tr("按计划 ","Planned ")else tr("预计 ","ETA "))+it}
        val line=listOfNotNull(remaining,estimate).joinToString(" · ")
        if(line.isNotEmpty())Label(line,11,c.muted,maxLines=2)
    }
}

/** 航速、艏向与横偏各自使用原始依据；带状图表示相对航线的位置而非舵角指令。 */
@Composable
internal fun SpatialVesselReadout(
    snapshot:NavigationSpatialSnapshot,navigation:NavigationState?,speedKnots:Double?,
    formats:MarineUnitFormats,chinese:Boolean,modifier:Modifier=Modifier,
){
    fun tr(zh:String,en:String)=if(chinese)zh else en
    val c=LocalMetro.current
    val guidance=navigation?.guidance
    val offset=guidance?.crossTrackMeters?.takeIf(Double::isFinite)
    if(snapshot.position==null&&offset==null)return
    Column(modifier.widthIn(max=360.dp).background(c.bg.copy(alpha=.84f)).padding(horizontal=10.dp,vertical=7.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column{
                Label(tr("航速","Speed"),10,c.muted)
                Label(formats.speed(speedKnots),17,maxLines=1)
            }
            snapshot.vesselHeading?.let{
                Column{Label(tr("艏向","Heading"),10,c.muted);Label("${((it.trueDegrees.roundToInt()%360)+360)%360}° T",17,maxLines=1)}
            }
            snapshot.courseOverGround?.let{
                Column{Label(tr("航迹向","Course"),10,c.muted);Label("${((it.trueDegrees.roundToInt()%360)+360)%360}° T",17,maxLines=1)}
            }
        }
        if(offset!=null){
            val range=max(25.0,(navigation?.session?.settings?.arrivalRadiusMeters?:50.0)*2)
            val position=animateFloatAsState((offset/range).toFloat().coerceIn(-1f,1f),tween(250),label="chart-cross-track")
            val color=if(guidance?.live==true)c.fg else c.muted
            val side=when{abs(offset)<1.0->tr("沿航线","On route");offset>0->tr("航线右侧 ","Right of route ")+formats.distance(abs(offset));else->tr("航线左侧 ","Left of route ")+formats.distance(abs(offset))}
            Label((if(guidance?.live==true)""else tr("上次 · ","Last · "))+side,11,c.muted,maxLines=1)
            Canvas(Modifier.fillMaxWidth().height(18.dp)){
                val middle=size.width/2;val y=size.height*.65f
                drawLine(c.muted.copy(alpha=.5f),Offset(5.dp.toPx(),y),Offset(size.width-5.dp.toPx(),y),1.dp.toPx())
                drawLine(color.copy(alpha=.6f),Offset(middle,1.dp.toPx()),Offset(middle,size.height),1.dp.toPx())
                val x=middle+position.value*(middle-7.dp.toPx())
                val marker=Path().apply{moveTo(x,1.dp.toPx());lineTo(x-4.dp.toPx(),y);lineTo(x+4.dp.toPx(),y);close()}
                drawPath(marker,color)
            }
        }
    }
}

internal fun spatialObservationAge(ageMillis:Long?,chinese:Boolean):String {
    if(ageMillis==null)return if(chinese)"尚无观测"else"No observation"
    val seconds=(ageMillis.coerceAtLeast(0)/1000)
    return when{
        seconds<2->if(chinese)"刚刚更新"else"Just updated"
        seconds<60->if(chinese)"${seconds} 秒前"else"${seconds}s ago"
        seconds<3600->if(chinese)"${seconds/60} 分钟前"else"${seconds/60}m ago"
        else->if(chinese)"${seconds/3600} 小时前"else"${seconds/3600}h ago"
    }
}

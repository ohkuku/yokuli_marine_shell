package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TilePresentation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/** 只读图形描述；任何插值只用于画布，数字、来源时间和历史不被改写。 */
internal enum class TileMetricGraphicKind { COMPASS, GAUGE, HEEL, PITCH, WIND, ATTITUDE, DEPTH }
internal data class TileMetricGraphic(
    val kind:TileMetricGraphicKind,
    val value:Double?,
    val minimum:Double=0.0,
    val maximum:Double=100.0,
    val minimumLabel:String="",
    val maximumLabel:String="",
    val relative:Boolean=false,
    val secondary:Double?=null,
    val observation:VesselObservation<*> = VesselObservation<Double>(),
    val secondaryObservation:VesselObservation<*> = VesselObservation<Double>(),
)
internal data class TileOverviewReading(val label:String,val value:String,val status:String,val live:Boolean)
internal data class TileOverviewFrame(
    val readings:List<TileOverviewReading>,
    val graphic:TileMetricGraphic?=null,
    val reference:String="",
    val demo:Boolean=false,
)

/** 每个组合只订阅自己需要的规范观测，不订阅整个传感器快照而跟随无关高频数据重组。 */
private fun overviewObservations(data:VesselDataSnapshot,id:String):List<VesselObservation<Double>> = when(id) {
    "navigationReadings"->listOf(data.sogKnots,data.headingTrueDegrees,data.cogTrueDegrees)
    "windConditions"->listOf(data.trueWind.speedKnots,data.apparentWind.speedKnots,data.trueWind.angleDegrees,data.apparentWind.angleDegrees)
    "environment"->listOf(data.pressureHpa,data.airTemperatureCelsius,data.waterTemperatureCelsius)
    "depthClearance"->listOf(data.depthMeters,data.derived.underKeelClearanceMeters)
    "vesselAttitude"->listOf(data.heelDegrees,data.pitchDegrees)
    else->emptyList()
}

@Composable internal fun navigationOverviewTileFrame(os:OsStore,id:String,config:TilePresentation,active:Boolean):TileFrame {
    val state=os.marine?.services?.state
    val projection=remember(state,id) {state?.map {overviewObservations(it.vesselData,id)}?.distinctUntilChanged()}
    val values=activeTileValue(projection,overviewObservations(state?.value?.vesselData ?: VesselDataSnapshot(),id),active)
    tileInstrumentDisplayDemand(os,active,id in setOf("navigationReadings","windConditions","vesselAttitude"))
    val now=tileElapsed(active)
    val visual=config.style=="detail"
    fun at(index:Int):VesselObservation<Double> = values.getOrNull(index) ?: VesselObservation()
    fun reading(index:Int,zh:String,en:String,metric:String,fresh:Boolean=false):TileOverviewReading {
        val observation=at(index)
        val number=if(fresh)observation.liveNumber()else observation.displayNumber()
        return TileOverviewReading(os.t(zh,en),os.formatMetric(metric,number),observationStatus(os,observation,now),observation.displayIsLive())
    }
    val readings=when(id) {
        "navigationReadings"->listOf(reading(0,"航速","Speed","sog"),reading(1,"船首向","Heading","heading",true),reading(2,"对地航向","Course","cog",true))
        "windConditions"->listOf(reading(0,"真风","True wind","tws"),reading(1,"视风","Apparent","aws"),reading(3,"视风角","Apparent angle","awa",true))
        "environment"->listOf(reading(0,"气压","Pressure","pressure"),reading(1,"气温","Air","air"),reading(2,"水温","Water","water"))
        "depthClearance"->listOf(reading(0,"水深","Depth","depth"),reading(1,"龙骨下","Under keel","ukc"))
        "vesselAttitude"->listOf(reading(0,"横倾","Heel","heel",true),reading(1,"纵倾","Pitch","pitch",true))
        else->emptyList()
    }
    val graphic=if(!visual)null else when(id) {
        "navigationReadings"->TileMetricGraphic(TileMetricGraphicKind.COMPASS,at(1).liveNumber(),secondary=at(2).liveNumber(),observation=at(1),secondaryObservation=at(2))
        "windConditions"->TileMetricGraphic(TileMetricGraphicKind.WIND,at(2).liveNumber(),relative=true,secondary=at(3).liveNumber(),observation=at(2),secondaryObservation=at(3))
        "depthClearance"->TileMetricGraphic(TileMetricGraphicKind.DEPTH,at(0).liveNumber(),secondary=at(1).liveNumber(),observation=at(0),secondaryObservation=at(1))
        "vesselAttitude"->TileMetricGraphic(TileMetricGraphicKind.ATTITUDE,at(0).liveNumber(),secondary=at(1).liveNumber(),observation=at(0),secondaryObservation=at(1))
        else->null
    }
    val reference=when(id) {
        "navigationReadings"->os.t("实线船首 · 虚线航向 · 真北","Heading solid · course dashed · true north")
        "windConditions"->os.t("实线真风 · 虚线视风 · 船艏向上","True solid · apparent dashed · bow up")
        "depthClearance"->when((at(0).reference as? VesselReference.Depth)?.reference?.name) {
            "BELOW_SURFACE"->os.t("水面以下","Below surface")
            "BELOW_KEEL"->os.t("龙骨以下","Below keel")
            "BELOW_TRANSDUCER"->os.t("换能器以下","Below transducer")
            else->os.t("测深参考未提供","Depth reference unspecified")
        }
        "vesselAttitude"->os.t("以已固定的船体零点为准","Relative to the calibrated vessel frame")
        else->""
    }
    val history=if(visual&&id=="environment")rememberTileHistory(os,"pressure",active,now,config.historyMinutes ?: 15)else null
    val historyCaption=remember(history,os.chinese) {history?.takeIf {it.points.isNotEmpty()}?.let {snapshot->
        val last=snapshot.points.last().reading
        val utc=last.observedUtcMillis ?: (System.currentTimeMillis()-(android.os.SystemClock.elapsedRealtime()-last.elapsed))
        os.t("截至 ","Through ")+DateFormat.getTimeInstance(DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(utc))
    }.orEmpty()}
    return TileFrame(id,readings.firstOrNull()?.value ?: "—","",history=history,historyCaption=historyCaption,
        overview=TileOverviewFrame(readings,graphic,reference,values.any {it.source==VesselDataSource.DEMO}))
}

/** 组合磁贴让多个相关观测共用一个视图；保持扁平文字层级，不再为每项套独立小磁贴。 */
@Composable internal fun NavigationTileContent(os:OsStore,title:String,frame:TileFrame,tileSize:MarineTileSize,color:Color) {
    val content=frame.overview ?: return
    val wide=tileSize==MarineTileSize.WIDE_4X2
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val generous=maxHeight>=132.dp
        val primary=content.readings.firstOrNull()
        val hasHistory=frame.history?.points?.isNotEmpty()==true
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            WpText((if(content.demo)os.t("演示 · ","DEMO · ")else "")+title,12,color=color,weight=FontWeight.SemiBold,maxLines=1)
            Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(wide)12.dp else 6.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    if(primary!=null) {
                        val ink=if(primary.live)color else color.copy(alpha=.65f)
                        WpText(primary.label,11,color=color.copy(alpha=.75f),maxLines=1)
                        WpText(primary.value,if(wide&&generous)30 else 22,color=ink,weight=FontWeight.Light,maxLines=1)
                        if(!primary.live)WpText(primary.status,10,color=ink,maxLines=1)
                    }
                    if(wide)content.readings.drop(1).forEach {TileOverviewReadout(it,color,generous)}
                }
                content.graphic?.let {TileMetricDrawing(it,color,if(wide)Modifier.weight(.85f).fillMaxHeight()else Modifier.width(46.dp).fillMaxHeight())}
            }
            if(!wide)content.readings.drop(1).forEach {TileOverviewReadout(it,color,false)}
            val history=frame.history
            if(hasHistory&&history!=null&&generous) {
                val palette=MetroColors(Color.Transparent,color,color.copy(alpha=.5f),Color.Transparent,color)
                Canvas(Modifier.fillMaxWidth().height(if(wide)30.dp else 20.dp).clipToBounds()) {drawInstrumentHistory(history,palette,null,false)}
                if(frame.historyCaption.isNotBlank())WpText(frame.historyCaption,10,color=color.copy(alpha=.7f),maxLines=1)
            }
            if(wide&&generous&&content.reference.isNotBlank())WpText(content.reference,10,color=color.copy(alpha=.75f),maxLines=1)
        }
    }
}

@Composable private fun TileOverviewReadout(item:TileOverviewReading,color:Color,showAge:Boolean) {
    val ink=if(item.live)color else color.copy(alpha=.65f)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        WpText(item.label,11,color=color.copy(alpha=.75f),maxLines=1,modifier=Modifier.weight(1f))
        WpText(item.value,14,color=ink,maxLines=1)
    }
    if(!item.live&&showAge)WpText(item.status,10,color=ink,maxLines=1)
}

/** 指针在绘制阶段读取共享物理缓动；缺测不画零度指针，既有历史绝不做动画。 */
@Composable internal fun TileMetricDrawing(graphic:TileMetricGraphic,color:Color,modifier:Modifier=Modifier) {
    val circular=graphic.kind in setOf(TileMetricGraphicKind.COMPASS,TileMetricGraphicKind.WIND)
    val primary=rememberInstrumentMotion(graphic.observation,graphic.value,circular,scaleKey=graphic.minimumLabel to graphic.maximumLabel)
    val secondary=rememberInstrumentMotion(graphic.secondaryObservation,graphic.secondary,circular)
    Canvas(modifier.clipToBounds()) {
        val faint=color.copy(alpha=.22f)
        val weight=1.5.dp.toPx()
        val mid=Offset(size.width/2,size.height/2)
        val radius=min(size.width,size.height)*.4f
        fun pointer(angle:Float,dashed:Boolean=false,length:Float=radius) {
            val rad=Math.toRadians(angle.toDouble()-90)
            val end=mid+Offset(cos(rad).toFloat()*length,sin(rad).toFloat()*length)
            drawLine(color.copy(alpha=if(dashed).62f else 1f),mid,end,weight,
                pathEffect=if(dashed)PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx()))else null)
            if(!dashed) {
                val back=end-Offset(cos(rad).toFloat(),sin(rad).toFloat())*7.dp.toPx()
                val wing=Offset(-sin(rad).toFloat(),cos(rad).toFloat())*3.dp.toPx()
                drawPath(Path().apply {moveTo(end.x,end.y);lineTo((back+wing).x,(back+wing).y);lineTo((back-wing).x,(back-wing).y);close()},color)
            }
        }
        when(graphic.kind) {
            TileMetricGraphicKind.COMPASS,TileMetricGraphicKind.WIND->{
                drawCircle(faint,radius,mid,style=Stroke(weight))
                repeat(4) {index->val r=Math.toRadians(index*90.0);val direction=Offset(cos(r).toFloat(),sin(r).toFloat());drawLine(color.copy(alpha=.4f),mid+direction*(radius*.87f),mid+direction*radius,weight)}
                if(graphic.kind==TileMetricGraphicKind.WIND||graphic.relative) {
                    val boat=Path().apply {moveTo(mid.x,mid.y-radius*.45f);lineTo(mid.x-radius*.17f,mid.y+radius*.3f);lineTo(mid.x+radius*.17f,mid.y+radius*.3f);close()}
                    drawPath(boat,faint,style=Stroke(weight))
                }
                if(graphic.secondary!=null)pointer(secondary.value,true)
                if(graphic.value!=null)pointer(primary.value)
            }
            TileMetricGraphicKind.HEEL,TileMetricGraphicKind.PITCH,TileMetricGraphicKind.ATTITUDE->{
                drawLine(faint,Offset(0f,mid.y),Offset(size.width,mid.y),weight)
                drawLine(faint,Offset(mid.x,mid.y-radius),Offset(mid.x,mid.y+radius),weight)
                if(graphic.value!=null&&(graphic.kind!=TileMetricGraphicKind.ATTITUDE||graphic.secondary!=null)) {
                    val heel=if(graphic.kind==TileMetricGraphicKind.PITCH)0f else primary.value
                    val pitch=if(graphic.kind==TileMetricGraphicKind.PITCH)primary.value else if(graphic.kind==TileMetricGraphicKind.ATTITUDE)secondary.value else 0f
                    val offset=pitch.coerceIn(-45f,45f)/45f*radius*.6f
                    rotate(-heel,mid) {
                        val horizon=Offset(mid.x,mid.y+offset)
                        drawLine(color,horizon-Offset(radius,0f),horizon+Offset(radius,0f),weight*1.5f)
                        listOf(-1,1).forEach {side->drawLine(color.copy(alpha=.45f),horizon+Offset(-radius*.25f,side*radius*.33f),horizon+Offset(radius*.25f,side*radius*.33f),weight)}
                    }
                }
                drawCircle(color,2.dp.toPx(),mid)
            }
            TileMetricGraphicKind.GAUGE->{
                val start=Offset(size.width*.06f,size.height*.55f);val end=Offset(size.width*.94f,start.y)
                drawLine(faint,start,end,weight*2)
                repeat(5) {index->val x=start.x+(end.x-start.x)*index/4f;drawLine(faint,Offset(x,start.y-4.dp.toPx()),Offset(x,start.y+4.dp.toPx()),weight)}
                if(graphic.value!=null&&graphic.maximum>graphic.minimum) {
                    val ratio=((primary.value-graphic.minimum)/(graphic.maximum-graphic.minimum)).toFloat().coerceIn(0f,1f)
                    val marker=Offset(start.x+(end.x-start.x)*ratio,start.y)
                    drawLine(color,start,marker,weight*2);drawCircle(color,3.dp.toPx(),marker)
                }
            }
            TileMetricGraphicKind.DEPTH->{
                val surface=size.height*.12f;val bed=size.height*.86f
                drawLine(faint,Offset(size.width*.1f,surface),Offset(size.width*.9f,surface),weight)
                if(graphic.value!=null&&graphic.value>0.0) {
                    val x=size.width*.68f
                    drawLine(color,Offset(x,surface),Offset(x,bed),weight)
                    drawLine(color,Offset(x-5.dp.toPx(),bed-5.dp.toPx()),Offset(x,bed),weight)
                    drawLine(color,Offset(x+5.dp.toPx(),bed-5.dp.toPx()),Offset(x,bed),weight)
                    drawLine(faint,Offset(size.width*.1f,bed),Offset(size.width*.9f,bed),weight)
                    // 这里只表达实测水深，不把未知测深基准和 UKC 拼成虚构船体/海床几何。
                }
            }
        }
    }
}

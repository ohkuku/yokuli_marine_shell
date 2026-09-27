package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
    tileInstrumentDisplayDemand(os,active,id in setOf("navigationReadings","windConditions","vesselAttitude"),sensors=id in setOf("environment","vesselAttitude"))
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
        os.t("近 ${config.historyMinutes ?: 15} 分钟 · 截至 ","${config.historyMinutes ?: 15} min · through ")+DateFormat.getTimeInstance(DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(utc))
    }.orEmpty()}
    return TileFrame(id,readings.firstOrNull()?.value ?: "—","",history=history,historyCaption=historyCaption,
        overview=TileOverviewFrame(readings,graphic,reference,values.any {it.source==VesselDataSource.DEMO}))
}

/** 组合磁贴让多个相关观测共用一个视图；保持扁平文字层级，不再为每项套独立小磁贴。 */
@Composable internal fun NavigationTileContent(os:OsStore,title:String,frame:TileFrame,tileSize:MarineTileSize,color:Color,active:Boolean) {
    val content=frame.overview ?: return
    if(tileSize==MarineTileSize.LARGE_4X4) {
        LargeNavigationTileContent(os,title,frame,color,active)
        return
    }
    val wide=tileSize==MarineTileSize.WIDE_4X2
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if(maxHeight<118.dp) {
            CompactNavigationTileContent(os,title,frame,color,active)
            return@BoxWithConstraints
        }
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
                content.graphic?.let {TileMetricGraphicView(it,color,active,if(wide)Modifier.weight(.85f).fillMaxHeight()else Modifier.width(46.dp).fillMaxHeight())}
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

/** 4×4 的额外面积用于关系图和变化，不把同一数字简单放大。 */
@Composable private fun LargeNavigationTileContent(os:OsStore,title:String,frame:TileFrame,color:Color,active:Boolean) {
    val content=frame.overview ?: return
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        WpText((if(content.demo)os.t("演示 · ","DEMO · ")else "")+title,13,color=color,weight=FontWeight.SemiBold,maxLines=1)
        if(frame.key=="environment") {
            val history=frame.history
            if(history!=null&&history.points.isNotEmpty()) {
                val palette=MetroColors(Color.Transparent,color,color.copy(alpha=.45f),Color.Transparent,color)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    WpText(os.t("气压","Pressure"),10,color=color.copy(alpha=.65f),maxLines=1)
                    val scale=String.format(Locale.US,"%.1f–%.1f",history.lower,history.upper)+" "+os.displayMetricUnit("pressure")
                    WpText(scale,10,color=color.copy(alpha=.65f),maxLines=1)
                }
                Canvas(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {drawInstrumentHistory(history,palette,null,false)}
                WpText(frame.historyCaption,10,color=color.copy(alpha=.7f),maxLines=1)
            } else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                WpText(os.t("正在积累气压观测","Gathering pressure observations"),12,color=color.copy(alpha=.6f),maxLines=2)
            }
        } else if(content.graphic?.kind==TileMetricGraphicKind.ATTITUDE) {
            val graphic=content.graphic
            Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TileMetricGraphicView(graphic.copy(kind=TileMetricGraphicKind.HEEL,minimum=-45.0,maximum=45.0),color,active,Modifier.weight(1f).fillMaxHeight())
                TileMetricGraphicView(graphic.copy(kind=TileMetricGraphicKind.PITCH,value=graphic.secondary,observation=graphic.secondaryObservation,minimum=-45.0,maximum=45.0),color,active,Modifier.weight(1f).fillMaxHeight())
            }
        } else content.graphic?.let {TileMetricGraphicView(it,color,active,Modifier.weight(1f).fillMaxWidth())}
            ?: Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            content.readings.forEach {item->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                WpText(item.label,10,color=color.copy(alpha=.7f),maxLines=1)
                WpText(item.value,if(content.readings.size>2)20 else 24,color=if(item.live)color else color.copy(alpha=.6f),weight=FontWeight.Light,maxLines=1)
                WpText(item.status,10,color=color.copy(alpha=.6f),maxLines=2)
            }}
        }
        if(content.reference.isNotBlank())WpText(content.reference,10,color=color.copy(alpha=.65f),maxLines=2)
    }
}

/** 复合磁贴内的场景面板，共享完整磁贴的投影与图形。无第二层卡片背景。 */
@Composable internal fun CompositeOverviewPanel(os:OsStore,id:String,config:TilePresentation,color:Color,active:Boolean,modifier:Modifier) {
    val frame=navigationOverviewTileFrame(os,id,config.copy(style="detail"),active)
    val content=frame.overview ?: return
    val choice=tileCompositePanelChoices(os).firstOrNull {it.binding.contentId==id}
    val title=choice?.title?.let {if(os.chinese)it.chinese else it.english}.orEmpty()
    BoxWithConstraints(modifier.clipToBounds()) {
        val roomy=maxHeight>=118.dp
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            WpText((if(content.demo)os.t("演示 · ","DEMO · ")else "")+title,11,color=color.copy(alpha=.8f),weight=FontWeight.SemiBold,maxLines=1)
            Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                val graphic=content.graphic
                if(graphic!=null)TileMetricGraphicView(graphic,color,active,Modifier.weight(1f).fillMaxHeight())
                else if(frame.history?.points?.isNotEmpty()==true) {
                    val history=frame.history!!
                    val palette=MetroColors(Color.Transparent,color,color.copy(alpha=.45f),Color.Transparent,color)
                    Canvas(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {drawInstrumentHistory(history,palette,null,false)}
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    content.readings.take(if(roomy)3 else 2).forEach {item->
                        if(roomy)WpText(item.label,9,color=color.copy(alpha=.65f),maxLines=1)
                        WpText(item.value,if(roomy)15 else 12,color=if(item.live)color else color.copy(alpha=.6f),maxLines=1)
                    }
                }
            }
            val stale=content.readings.firstOrNull {!it.live}
            if(stale!=null)WpText(stale.label+" · "+stale.status,9,color=color.copy(alpha=.65f),maxLines=1)
            else if(roomy)WpText(content.reference,9,color=color.copy(alpha=.6f),maxLines=1)
        }
    }
}

/** 六列桌面的实际短边更小，用横向小面板保留关系，而不让原字号挤出格位。 */
@Composable private fun CompactNavigationTileContent(os:OsStore,title:String,frame:TileFrame,color:Color,active:Boolean) {
    val content=frame.overview ?: return
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        WpText((if(content.demo)os.t("演示 · ","DEMO · ")else "")+title,10,color=color,weight=FontWeight.SemiBold,maxLines=1)
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            content.graphic?.let {TileMetricGraphicView(it,color,active,Modifier.weight(.8f).fillMaxHeight())}
            Column(Modifier.weight(1.2f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                content.readings.forEach {item->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        WpText(item.label,9,color=color.copy(alpha=.65f),maxLines=1,modifier=Modifier.weight(1f))
                        WpText(item.value,12,color=if(item.live)color else color.copy(alpha=.6f),maxLines=1,modifier=Modifier.weight(1.1f))
                    }
                }
            }
        }
        val stale=content.readings.firstOrNull {!it.live}
        WpText(stale?.let {it.label+" · "+it.status} ?: content.reference,9,color=color.copy(alpha=.6f),maxLines=1)
    }
}

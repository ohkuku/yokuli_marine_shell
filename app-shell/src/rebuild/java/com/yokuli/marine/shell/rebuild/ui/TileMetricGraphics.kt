package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.domain.vessel.VesselReference
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.ShellApp
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.cos
import kotlin.math.sin

internal enum class TileMetricGraphicKind { COMPASS, GAUGE, HEEL, PITCH, WIND, ATTITUDE, DEPTH }
internal data class TileMetricGraphic(
    val kind:TileMetricGraphicKind,
    val value:Double?,
    val minimum:Double=0.0,
    val maximum:Double=100.0,
    val minimumLabel:String="",
    val maximumLabel:String="",
    val relative:Boolean=false,
    val observation:VesselObservation<*> = VesselObservation<Double>(),
    /** 第二字段各自携带来源时效，不能借主字段续命；用于航向、视风或纵倾。 */
    val secondary:Double?=null,
    val secondaryObservation:VesselObservation<*> = VesselObservation<Double>(),
)
/** A typed reading frame; legacy app summaries keep their existing TileFrame contract. */
internal data class ReadingTileContent(val frame:TileFrame,val graphic:TileMetricGraphic?=null,val style:String="simple")
private val ReadingHeadline = Regex("^([−+\\-]?\\d+(?:[.,]\\d+)?)(.*)$")

/** The exact same data, geometry and labels are used on Start and in the editor. */
@Composable internal fun ReadingTileFace(
    os:OsStore,app:ShellApp,title:String,content:ReadingTileContent,size:MarineTileSize,
    context:LauncherTileRenderContext,active:Boolean,
) {
    val frame=content.frame
    if(size==MarineTileSize.ICON_1X1) {
        TileFace(os,app,title,listOf(frame),size,context,false,false,6)
        return
    }
    val color=context.contentColor
    val valueColor=if(frame.live)color else color.copy(alpha=.72f)
    val history=frame.history
    val graphic=content.graphic
    val scale=LocalDensity.current.fontScale*LocalWpTextScale.current
    BoxWithConstraints(context.modifier.fillMaxSize().clipToBounds().semantics(mergeDescendants=true) {
        contentDescription=listOf(title,frame.headline,frame.eyebrow,frame.priorityLine,frame.detail,frame.historyCaption)
            .filter(String::isNotBlank).joinToString(" · ")
    }) {
        val wide=size==MarineTileSize.WIDE_4X2
        val compact=maxHeight<(144f*scale).dp
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            if(frame.eyebrow.isNotBlank())WpText(frame.eyebrow,12,color=color,maxLines=if(wide)2 else 1)
            if(content.style=="trend"&&history!=null) {
                ReadingValue(frame.headline,valueColor,24,Modifier.fillMaxWidth())
                if(history.points.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.CenterStart) {
                        WpText(os.t("等待历史观测","Waiting for history"),12,color=color,maxLines=2)
                    }
                } else {
                    val palette=MetroColors(Color.Transparent,color,color.copy(alpha=.6f),Color.Transparent,color)
                    Canvas(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {drawInstrumentHistory(history,palette,null,false)}
                }
                if(frame.historyCaption.isNotBlank())WpText(frame.historyCaption,11,color=color,maxLines=1)
            } else if(wide&&graphic!=null) {
                Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    ReadingValue(frame.headline,valueColor,if(compact)28 else 38,Modifier.weight(1f))
                    TileMetricGraphicView(graphic,color,active,Modifier.width(88.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.Center) {
                    ReadingValue(frame.headline,valueColor,if(compact)24 else 32,Modifier.fillMaxWidth())
                    if(graphic!=null)TileMetricGraphicView(graphic,color,active,Modifier.fillMaxWidth().weight(1f))
                }
            }
            if(history!=null&&content.style!="trend") {
                if(history.points.isEmpty()) {
                    // Selecting a trend remains visible even before the first observation arrives.
                    WpText(os.t("等待历史观测","Waiting for history"),12,color=color,maxLines=1)
                } else {
                    val palette=MetroColors(Color.Transparent,color,color.copy(alpha=.6f),Color.Transparent,color)
                    Canvas(Modifier.fillMaxWidth().height(if(compact)22.dp else 32.dp).clipToBounds()) {
                        drawInstrumentHistory(history,palette,null,false)
                    }
                }
                if(wide&&!compact&&frame.historyCaption.isNotBlank())WpText(frame.historyCaption,11,color=color,maxLines=1)
            }
            if(frame.priorityLine.isNotBlank())WpText(frame.priorityLine,12,color=color,maxLines=2,weight=FontWeight.SemiBold)
            if(frame.detail.isNotBlank())WpText(frame.detail,12,color=color,maxLines=if(wide&&!compact)2 else 1)
            WpText(title,12,color=color,maxLines=1,weight=FontWeight.SemiBold)
        }
    }
}

@Composable private fun ReadingValue(headline:String,color:Color,numberSize:Int,modifier:Modifier) {
    val match=ReadingHeadline.matchEntire(headline)?.takeIf {it.groupValues[2].trim().length<=12&&'°' !in it.groupValues[2].drop(1)}
    if(match==null) {
        WpText(headline,18,modifier=modifier,color=color,maxLines=2)
    } else {
        Row(modifier,verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            WpText(match.groupValues[1],numberSize,color=color,weight=FontWeight.Light,maxLines=1,modifier=Modifier.weight(1f,fill=false))
            if(match.groupValues[2].isNotBlank())WpText(match.groupValues[2].trim(),12,color=color,maxLines=1,modifier=Modifier.padding(bottom=3.dp))
        }
    }
}

/** Smooth only the display pointer; source changes, invalid values and leaving stop animation. */
@Composable internal fun TileMetricGraphicView(graphic:TileMetricGraphic,color:Color,active:Boolean,modifier:Modifier) {
    val valid=graphic.value?.isFinite()==true&&graphic.observation.displayIsLive()
    val secondaryValid=graphic.secondary?.isFinite()==true&&graphic.secondaryObservation.displayIsLive()
    val allowed=active&&LocalInternalAppInputEnabled.current
    CompositionLocalProvider(LocalInternalAppInputEnabled provides allowed) {
        val circular=graphic.kind in setOf(TileMetricGraphicKind.COMPASS,TileMetricGraphicKind.WIND)
        val motion=rememberInstrumentMotion(graphic.observation,graphic.value.takeIf {valid},
            circular=circular,
            scaleKey=listOf(graphic.kind,graphic.minimum,graphic.maximum,graphic.relative,graphic.minimumLabel,graphic.maximumLabel))
        val secondaryMotion=rememberInstrumentMotion(graphic.secondaryObservation,graphic.secondary.takeIf {secondaryValid},
            circular=circular,scaleKey=graphic.kind)
        val arrow=remember {Path()}
        Column(modifier,verticalArrangement=Arrangement.Center) {
            Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                if(size.width<=0f||size.height<=0f)return@Canvas
                val center=Offset(size.width/2,size.height/2)
                val radius=(size.minDimension*.40f).coerceAtLeast(1f)
                val thin=1.dp.toPx()
                val faint=color.copy(alpha=.25f)
                fun radial(degrees:Float,r:Float):Offset {
                    val a=Math.toRadians((degrees-90).toDouble())
                    return Offset(center.x+cos(a).toFloat()*r,center.y+sin(a).toFloat()*r)
                }
                when(graphic.kind) {
                    TileMetricGraphicKind.COMPASS,TileMetricGraphicKind.WIND->{
                        drawCircle(faint,radius,center,style=Stroke(thin))
                        for(a in 0 until 360 step 30)drawLine(if(a==0)color else faint,
                            radial(a.toFloat(),radius*.83f),radial(a.toFloat(),radius),thin,StrokeCap.Round)
                        if(graphic.relative)drawLine(faint,radial(0f,radius*.65f),radial(180f,radius*.65f),thin)
                        if(secondaryValid)drawLine(color.copy(alpha=.65f),center,radial(secondaryMotion.value,radius*.94f),1.5f*thin,
                            pathEffect=PathEffect.dashPathEffect(floatArrayOf(4*thin,3*thin)))
                        if(graphic.kind==TileMetricGraphicKind.WIND) {
                            arrow.reset();arrow.moveTo(center.x,center.y-radius*.35f)
                            arrow.lineTo(center.x-radius*.12f,center.y+radius*.25f)
                            arrow.lineTo(center.x+radius*.12f,center.y+radius*.25f);arrow.close()
                            drawPath(arrow,faint,style=Stroke(thin))
                            if(valid) {
                                drawLine(color,center,radial(motion.value,radius*.85f),1.5f*thin)
                                rotate(motion.value,center) {
                                    arrow.reset();arrow.moveTo(center.x,center.y-radius*.9f)
                                    arrow.lineTo(center.x-radius*.12f,center.y-radius*.65f)
                                    arrow.lineTo(center.x+radius*.12f,center.y-radius*.65f);arrow.close()
                                    drawPath(arrow,color)
                                }
                            }
                        } else if(valid)rotate(motion.value,center) {
                            arrow.reset();arrow.moveTo(center.x,center.y-radius*.85f)
                            arrow.lineTo(center.x+radius*.24f,center.y+radius*.32f)
                            arrow.lineTo(center.x,center.y+radius*.12f)
                            arrow.lineTo(center.x-radius*.24f,center.y+radius*.32f);arrow.close()
                            drawPath(arrow,color)
                        }
                    }
                    TileMetricGraphicKind.GAUGE->{
                        val r=(size.minDimension*.45f).coerceAtLeast(1f)
                        val top=Offset(center.x-r,center.y-r)
                        val diameter=Size(2*r,2*r)
                        drawArc(faint,150f,240f,false,top,diameter,style=Stroke(2*thin,cap=StrokeCap.Round))
                        if(valid&&graphic.maximum>graphic.minimum) {
                            val fraction=((motion.value-graphic.minimum)/(graphic.maximum-graphic.minimum)).coerceIn(0.0,1.0).toFloat()
                            drawArc(color,150f,240f*fraction,false,top,diameter,style=Stroke(2*thin,cap=StrokeCap.Round))
                            val a=Math.toRadians((150+240*fraction).toDouble())
                            val end=Offset(center.x+cos(a).toFloat()*r*.78f,center.y+sin(a).toFloat()*r*.78f)
                            drawLine(color,center,end,1.5f*thin,StrokeCap.Round)
                        }
                    }
                    TileMetricGraphicKind.HEEL,TileMetricGraphicKind.PITCH->{
                        drawLine(faint,Offset(center.x-radius,center.y),Offset(center.x+radius,center.y),thin)
                        for(limit in listOf(graphic.minimum,graphic.maximum)) {
                            drawLine(faint,radial(limit.toFloat()+90f,radius*.72f),radial(limit.toFloat()+90f,radius),thin)
                        }
                        if(valid)rotate(motion.value,center) {
                            arrow.reset()
                            if(graphic.kind==TileMetricGraphicKind.HEEL) {
                                arrow.moveTo(center.x-radius*.7f,center.y)
                                arrow.lineTo(center.x-radius*.3f,center.y+radius*.35f)
                                arrow.lineTo(center.x+radius*.3f,center.y+radius*.35f)
                                arrow.lineTo(center.x+radius*.7f,center.y);arrow.close()
                                drawPath(arrow,color,style=Stroke(1.5f*thin))
                                drawLine(color,center,Offset(center.x,center.y-radius*.65f),1.5f*thin)
                            } else {
                                arrow.moveTo(center.x-radius*.85f,center.y)
                                arrow.lineTo(center.x+radius*.85f,center.y-radius*.16f)
                                arrow.lineTo(center.x+radius*.5f,center.y+radius*.35f)
                                arrow.lineTo(center.x-radius*.65f,center.y+radius*.35f);arrow.close()
                                drawPath(arrow,color,style=Stroke(1.5f*thin))
                            }
                        }
                    }
                    TileMetricGraphicKind.ATTITUDE->{
                        drawLine(faint,Offset(center.x-radius,center.y),Offset(center.x+radius,center.y),thin)
                        drawLine(faint,Offset(center.x,center.y-radius),Offset(center.x,center.y+radius),thin)
                        if(valid&&secondaryValid)rotate(-motion.value,center) {
                            val horizon=center.copy(y=center.y+secondaryMotion.value.coerceIn(-45f,45f)/45f*radius*.6f)
                            drawLine(color,horizon-Offset(radius,0f),horizon+Offset(radius,0f),1.5f*thin)
                            for(side in listOf(-1,1))drawLine(color.copy(alpha=.45f),horizon+Offset(-radius*.25f,side*radius*.33f),horizon+Offset(radius*.25f,side*radius*.33f),thin)
                        }
                        drawCircle(color,2*thin,center)
                    }
                    TileMetricGraphicKind.DEPTH->{
                        val surface=size.height*.12f;val bottom=size.height*.86f
                        drawLine(faint,Offset(size.width*.1f,surface),Offset(size.width*.9f,surface),thin)
                        // 只有水面基准和规范龙骨余量同时存在，才可把船体放入同一个深度剖面。
                        // 其余情况仅画从实际测深基准到测点的量距，不臆造吃水/换能器偏移。
                        if(valid&&graphic.value!!>0.0) {
                            val x=size.width*.75f
                            drawLine(color,Offset(x,surface),Offset(x,bottom),thin)
                            drawLine(color,Offset(x-4*thin,bottom-4*thin),Offset(x,bottom),thin)
                            drawLine(color,Offset(x+4*thin,bottom-4*thin),Offset(x,bottom),thin)
                            drawLine(faint,Offset(size.width*.1f,bottom),Offset(size.width*.9f,bottom),thin)
                            val surfaceDatum=(graphic.observation.reference as? VesselReference.Depth)?.reference?.name=="BELOW_SURFACE"
                            val clearance=graphic.secondary
                            if(surfaceDatum&&secondaryValid&&clearance!=null&&clearance<=graphic.value) {
                                val fraction=((graphic.value-clearance)/graphic.value).coerceIn(0.0,1.0).toFloat()
                                val keel=surface+(bottom-surface)*fraction
                                val left=size.width*.18f;val right=size.width*.52f
                                arrow.reset();arrow.moveTo(left,surface);arrow.lineTo(right,surface)
                                arrow.lineTo(right-(right-left)*.22f,keel);arrow.lineTo(left+(right-left)*.22f,keel);arrow.close()
                                drawPath(arrow,color.copy(alpha=.14f))
                                drawPath(arrow,color,style=Stroke(1.3f*thin))
                                val clearanceX=size.width*.55f
                                val safeInk=if(clearance<=0.0)Color(0xFFFFB46B)else color
                                drawLine(safeInk,Offset(clearanceX,keel),Offset(clearanceX,bottom),2*thin)
                                drawCircle(safeInk,2*thin,Offset(clearanceX,keel))
                            }
                        }
                    }
                }
                if(!valid)drawLine(faint,Offset(center.x-4*thin,center.y),Offset(center.x+4*thin,center.y),thin)
            }
            if(graphic.kind==TileMetricGraphicKind.GAUGE)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                WpText(graphic.minimumLabel,10,color=color,maxLines=1,modifier=Modifier.weight(1f))
                WpText(graphic.maximumLabel,10,color=color,maxLines=1,modifier=Modifier.weight(1f))
            }
        }
    }
}

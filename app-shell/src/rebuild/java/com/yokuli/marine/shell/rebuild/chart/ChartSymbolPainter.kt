package com.yokuli.marine.shell.rebuild.chart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.*

/** 图标身份包含全部显示变量，避免换日夜色、危险状态或航标类型后复用旧位图。 */
internal fun MapPoint.portrayalIconKey() = "$style:$color:$radiusDp:$label:$symbol:$bold:$haloColor"

/** 原创矢量路径，随 APK 分发；不复制第三方 S-52 符号库，也不依赖联网字体。 */
internal object ChartSymbolPainter {
    fun bitmap(point:MapPoint,density:Float,dpi:Int):Bitmap {
        if(point.style!=MapPointStyle.CHART_SYMBOL)return text(point,density,dpi)
        val side=(44*density).roundToInt().coerceAtLeast(44)
        val bitmap=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888).apply {this.density=dpi}
        val canvas=Canvas(bitmap);canvas.translate(side/2f,side/2f);canvas.scale(density,density)
        val symbol=point.symbol ?: ChartSymbol(ChartSymbolKind.UNKNOWN)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=point.color.toInt();style=Paint.Style.STROKE;strokeWidth=1.5f;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND}
        fun path(vararg xy:Float,closed:Boolean=false,fill:Boolean=false) {
            val shape=Path();xy.asList().chunked(2).forEachIndexed {i,v->if(i==0)shape.moveTo(v[0],v[1])else shape.lineTo(v[0],v[1])};if(closed)shape.close()
            p.color=symbol.haloColor.toInt();p.strokeWidth=3.7f;p.style=Paint.Style.STROKE;canvas.drawPath(shape,p)
            p.color=point.color.toInt();p.strokeWidth=1.5f;p.style=if(fill)Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE;canvas.drawPath(shape,p)
        }
        fun circle(x:Float,y:Float,r:Float,fill:Boolean=false) {p.color=symbol.haloColor.toInt();p.strokeWidth=3.8f;p.style=Paint.Style.STROKE;canvas.drawCircle(x,y,r,p);p.color=point.color.toInt();p.strokeWidth=1.5f;p.style=if(fill)Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE;canvas.drawCircle(x,y,r,p)}
        fun cone(y:Float,up:Boolean,filled:Boolean=true) {val d=if(up)-1f else 1f;path(-3f,y-d*2.6f,0f,y+d*3f,3f,y-d*2.6f,closed=true,fill=filled)}
        fun topmark(code:Int?) {when(code) {
            1->cone(-11f,true);2->cone(-11f,false);3->circle(0f,-11f,2.6f,true)
            4->{circle(0f,-10f,2f,true);circle(0f,-16f,2f,true)}
            5->path(-3f,-14f,3f,-14f,3f,-9f,-3f,-9f,closed=true)
            7->{path(-3f,-14f,3f,-8f);path(3f,-14f,-3f,-8f)}
            8->{path(0f,-15f,0f,-8f);path(-3f,-12f,3f,-12f)}
            10->{cone(-15f,false);cone(-9f,true)}
            11->{cone(-15f,true);cone(-9f,false)}
            12->path(0f,-16f,3f,-12f,0f,-8f,-3f,-12f,closed=true)
            13->{cone(-15f,true);cone(-9f,true)}
            14->{cone(-15f,false);cone(-9f,false)}
        }}
        when(symbol.kind) {
            ChartSymbolKind.BUOY_CAN->path(-5f,5f,-5f,-5f,5f,-5f,5f,5f,closed=true,fill=true)
            ChartSymbolKind.BUOY_CONE->path(-6f,5f,0f,-6f,6f,5f,closed=true,fill=true)
            ChartSymbolKind.BUOY_SPHERE,ChartSymbolKind.SAFE_WATER->circle(0f,0f,5f,true)
            ChartSymbolKind.BUOY_PILLAR->path(-5f,5f,-2f,-6f,2f,-6f,5f,5f,closed=true,fill=true)
            ChartSymbolKind.BEACON->{path(-6f,6f,6f,6f);path(0f,6f,0f,-6f);path(-4f,-6f,4f,-6f)}
            ChartSymbolKind.CARDINAL->{path(-5f,5f,0f,-5f,5f,5f,closed=true,fill=true);when(symbol.cardinal){1->{cone(-15f,true);cone(-9f,true)};2->{cone(-15f,true);cone(-9f,false)};3->{cone(-15f,false);cone(-9f,false)};4->{cone(-15f,false);cone(-9f,true)}}}
            ChartSymbolKind.ISOLATED_DANGER->{path(-6f,5f,6f,5f);circle(0f,-1f,2.5f,true);circle(0f,-8f,2.5f,true);if(symbol.dangerous){circle(0f,0f,12f);path(-8f,-8f,8f,8f);path(-8f,8f,8f,-8f)}}
            ChartSymbolKind.SPECIAL_MARK->{path(-5f,5f,0f,-5f,5f,5f,closed=true);path(-4f,-11f,4f,-3f);path(-4f,-3f,4f,-11f)}
            ChartSymbolKind.LIGHT->{path(2f,-2f,13f,-16f,17f,-10f,closed=true,fill=true);circle(0f,0f,2f)}
            ChartSymbolKind.ROCK->{path(-6f,0f,6f,0f);path(0f,-6f,0f,6f);circle(0f,0f,8f)}
            ChartSymbolKind.WRECK->{path(-8f,4f,-5f,7f,5f,7f,8f,4f,closed=true);path(0f,4f,0f,-7f);path(-5f,-3f,5f,-3f);path(-6f,-6f,6f,5f)}
            ChartSymbolKind.OBSTRUCTION->{circle(0f,0f,7f);path(-4f,-4f,4f,4f);path(-4f,4f,4f,-4f)}
            ChartSymbolKind.ANCHORAGE->{circle(0f,-7f,2f);path(0f,-5f,0f,8f);path(-4f,-1f,4f,-1f);path(-7f,3f,-5f,7f,0f,10f,5f,7f,7f,3f)}
            ChartSymbolKind.CAUTION->{path(0f,-10f,9f,7f,-9f,7f,closed=true);path(0f,-4f,0f,1f);circle(0f,4f,.7f,true)}
            ChartSymbolKind.LANDMARK->{circle(0f,2f,6f);path(0f,-7f,0f,8f);path(-7f,2f,7f,2f)}
            ChartSymbolKind.QUALITY->{path(-8f,-8f,8f,-8f,8f,8f,-8f,8f,closed=true);path(-4f,0f,-1f,3f,5f,-4f)}
            ChartSymbolKind.UNKNOWN->{path(0f,-7f,7f,0f,0f,7f,-7f,0f,closed=true);circle(0f,0f,1f,true)}
        }
        if(symbol.kind in setOf(ChartSymbolKind.BUOY_CAN,ChartSymbolKind.BUOY_CONE,ChartSymbolKind.BUOY_SPHERE,ChartSymbolKind.BUOY_PILLAR,ChartSymbolKind.SAFE_WATER,ChartSymbolKind.BEACON)) {
            symbol.secondaryColor?.let {p.color=it.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=3f;canvas.drawLine(-3f,2f,3f,2f,p)}
            path(-7f,8f,7f,8f);topmark(symbol.topmark)
        }
        if(symbol.uncertain) {p.style=Paint.Style.STROKE;p.color=point.color.toInt();p.strokeWidth=1f;p.pathEffect=android.graphics.DashPathEffect(floatArrayOf(2f,3f),0f);canvas.drawCircle(0f,0f,16f,p)}
        return bitmap
    }
    private fun text(point:MapPoint,density:Float,dpi:Int):Bitmap {
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=(if(point.style==MapPointStyle.SOUNDING)11f else 10.5f)*density;typeface=Typeface.create("sans-serif",if(point.bold)Typeface.BOLD else Typeface.NORMAL);textAlign=Paint.Align.CENTER}
        val text=point.label.take(100)
        val width=(p.measureText(text)+10*density).roundToInt().coerceAtLeast(8)
        val height=((if(point.style==MapPointStyle.CHART_LABEL)60 else 22)*density).roundToInt().coerceAtLeast(8)
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {
            this.density=dpi;val canvas=Canvas(this);val x=width/2f;val y=height/2f-(p.ascent()+p.descent())/2+(if(point.style==MapPointStyle.CHART_LABEL)18*density else 0f)
            p.style=Paint.Style.STROKE;p.strokeWidth=3*density;p.color=point.haloColor.toInt();canvas.drawText(text,x,y,p)
            p.style=Paint.Style.FILL;p.color=point.color.toInt();canvas.drawText(text,x,y,p)
            if(point.style==MapPointStyle.SOUNDING&&text.startsWith("−")){p.strokeWidth=density;canvas.drawLine(4*density,y+2*density,width-4*density,y+2*density,p)}
        }
    }
}

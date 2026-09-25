package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** 仅绘制有界聚合几何；真实样本保留在 frame 中供手势和无障碍逐点查看。 */
internal fun DrawScope.drawInstrumentHistory(frame: InstrumentHistoryFrame, colors: MetroColors, selected: HistoryPoint?, latestFresh: Boolean) {
    if(frame.kind==InstrumentHistoryKind.DIRECTION) {
        drawDirectionRose(frame, colors, selected, latestFresh)
        return
    }
    val span=(frame.upper-frame.lower).coerceAtLeast(.000001)
    fun y(value: Double): Float {
        val fraction=((value-frame.lower)/span).coerceIn(0.0, 1.0)
        return ((if(frame.kind==InstrumentHistoryKind.DEPTH)fraction else 1.0-fraction)*size.height).toFloat()
    }
    fun x(point: HistoryPoint)=point.time*size.width
    val zero=y(0.0)
    val dash=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
    val gridSteps=if(frame.kind==InstrumentHistoryKind.COUNT&&frame.upper<=1.0)1 else 3
    for(index in 0..gridSteps) {
        val yy=size.height*index/gridSteps
        drawLine(colors.muted.copy(alpha=.15f), Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx())
    }
    if(frame.kind==InstrumentHistoryKind.DEVIATION || frame.kind==InstrumentHistoryKind.DEPTH)
        drawLine(colors.fg.copy(alpha=.5f), Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
    // 缺口只作标记，不把其前后的数据填充成连续航行。
    frame.breaks.forEach { time ->
        val xx=time*size.width
        drawLine(colors.muted.copy(alpha=.24f), Offset(xx,0f), Offset(xx,size.height), 1.dp.toPx(), pathEffect=dash)
    }
    val halfWidth=(size.width/frame.bucketCount*.30f).coerceAtLeast(1.dp.toPx())
    frame.buckets.forEach { bucket ->
        val last=bucket.last
        val xx=x(last)
        val alpha=if(bucket.degraded) .50f else .88f
        val color=colors.accent.copy(alpha=alpha)
        if(!bucket.uninterrupted && frame.kind !in setOf(InstrumentHistoryKind.COUNTER,InstrumentHistoryKind.WEATHER,InstrumentHistoryKind.BEARING,InstrumentHistoryKind.COUNT)) {
            // 一个时间桶含多来源或断点时，不产生跨来源均值带；只画实际首末与极值。
            listOf(bucket.first, last, bucket.minimumPoint, bucket.maximumPoint)
                .distinct().forEach { point -> drawCircle(colors.muted, 1.8.dp.toPx(), Offset(x(point),y(point.value))) }
        } else when(frame.kind) {
            InstrumentHistoryKind.RANGE -> {
                val high=y(bucket.maximum); val low=y(bucket.minimum)
                drawLine(color, Offset(xx, high), Offset(xx,low), 3.dp.toPx(), StrokeCap.Butt)
                drawLine(color, Offset(xx-halfWidth,high), Offset(xx+halfWidth,high), 1.dp.toPx())
                drawLine(colors.fg, Offset(xx-halfWidth,y(bucket.mean)), Offset(xx+halfWidth,y(bucket.mean)), 2.dp.toPx())
                if(bucket.maximum==bucket.minimum)drawCircle(color, 2.dp.toPx(), Offset(xx, high))
            }
            InstrumentHistoryKind.DEPTH -> {
                // 从参考面向下的细柱只对应这次实测，不填充不存在的海床或沿途地形。
                val end=y(last.value)
                drawLine(color.copy(alpha=.16f), Offset(xx,zero), Offset(xx,end), 2.dp.toPx())
                val shallow=y(bucket.minimum); val deep=y(bucket.maximum)
                drawLine(color, Offset(xx,shallow), Offset(xx,deep), 4.dp.toPx())
                drawLine(colors.fg.copy(alpha=.7f), Offset(xx-halfWidth,shallow), Offset(xx+halfWidth,shallow), 1.dp.toPx())
                drawCircle(color, 2.4.dp.toPx(), Offset(xx,end))
            }
            InstrumentHistoryKind.DEVIATION -> {
                val high=y(maxOf(0.0,bucket.maximum)); val low=y(minOf(0.0,bucket.minimum))
                if(bucket.maximum>0)drawRect(color, Offset(xx-halfWidth,high), Size(halfWidth*2,(zero-high).coerceAtLeast(1.dp.toPx())))
                if(bucket.minimum<0)drawRect(colors.muted.copy(alpha=alpha), Offset(xx-halfWidth,zero), Size(halfWidth*2,(low-zero).coerceAtLeast(1.dp.toPx())))
                drawLine(colors.fg, Offset(xx-halfWidth,y(bucket.mean)), Offset(xx+halfWidth,y(bucket.mean)), 1.dp.toPx())
            }
            // 天气与方位在下面按真实观测时间逐点绘制，不拿时段末值代替整段样本。
            InstrumentHistoryKind.WEATHER, InstrumentHistoryKind.BEARING, InstrumentHistoryKind.COUNT -> Unit
            InstrumentHistoryKind.COUNTER -> bucket.increment?.let { increment ->
                val top=y(increment)
                val origin=Offset((bucket.index+.18f)/frame.bucketCount*size.width, top)
                val extent=Size(size.width/frame.bucketCount*.64f, (size.height-top).coerceAtLeast(1.5.dp.toPx()))
                // 断点桶仍可保留其内部已知增量，但用空心虚线区分，不能表现为完整连续观测。
                if(!bucket.uninterrupted || bucket.index in frame.breakBuckets)
                    drawRect(color, origin, extent, style=Stroke(1.3.dp.toPx(), pathEffect=dash))
                else drawRect(color, origin, extent)
            }
            else -> Unit
        }
    }
    if(frame.kind in setOf(InstrumentHistoryKind.WEATHER,InstrumentHistoryKind.BEARING,InstrumentHistoryKind.COUNT)) {
        var previous: HistoryPoint?=null
        frame.points.forEach { point ->
            val color=colors.accent.copy(alpha=if(point.reading.quality==com.yokuli.anchorwatch.domain.vessel.VesselDataQuality.DEGRADED).5f else .88f)
            val p=Offset(x(point),y(point.value))
            previous?.takeIf { readingsAreContinuous(it.reading,point.reading) &&
                (frame.kind!=InstrumentHistoryKind.BEARING || abs(point.value-it.value)<=180) }?.let {
                if(frame.kind==InstrumentHistoryKind.COUNT) {
                    // 滚动计数显示采样时的整数状态，不能画分数或由相邻差推算新事件。
                    drawLine(color,Offset(x(it),y(it.value)),Offset(p.x,y(it.value)),1.5.dp.toPx())
                    drawLine(color,Offset(p.x,y(it.value)),p,1.5.dp.toPx())
                } else drawLine(color,Offset(x(it),y(it.value)),p,1.5.dp.toPx())
            }
            drawCircle(color,1.7.dp.toPx(),p)
            previous=point
        }
    }
    if(frame.kind==InstrumentHistoryKind.DEPTH)frame.minimum?.let { minimum ->
        val point=Offset(x(minimum),y(minimum.value))
        drawCircle(colors.fg, 4.dp.toPx(), point, style=Stroke(1.4.dp.toPx()))
    }
    if(frame.kind!=InstrumentHistoryKind.COUNTER)frame.points.lastOrNull()?.let { last ->
        drawCircle(if(latestFresh)colors.accent else colors.muted, 3.dp.toPx(), Offset(x(last),y(last.value)))
    }
    selected?.let { point ->
        val xx=x(point)
        drawLine(colors.fg.copy(alpha=.65f), Offset(xx,0f), Offset(xx,size.height), 1.dp.toPx())
        if(frame.kind==InstrumentHistoryKind.COUNTER) {
            val bucket=frame.buckets.firstOrNull { it.index==(point.time*frame.bucketCount).toInt().coerceIn(0,frame.bucketCount-1) }
            bucket?.increment?.let { drawCircle(colors.fg, 4.dp.toPx(), Offset(xx,y(it))) }
        } else drawCircle(colors.fg, 4.dp.toPx(), Offset(xx,y(point.value)))
    }
}

private fun DrawScope.drawDirectionRose(frame: InstrumentHistoryFrame, colors: MetroColors, selected: HistoryPoint?, latestFresh: Boolean) {
    val center=Offset(size.width/2,size.height/2)
    val radius=min(size.width,size.height)*.39f
    val inner=radius*.20f
    fun radial(angle: Double, length: Float): Offset {
        val radians=Math.toRadians(angle-90)
        return Offset(center.x+cos(radians).toFloat()*length, center.y+sin(radians).toFloat()*length)
    }
    for(fraction in listOf(.35f,.65f,1f))drawCircle(colors.muted.copy(alpha=.18f), radius*fraction, center, style=Stroke(1.dp.toPx()))
    for(angle in 0 until 360 step 90)drawLine(colors.muted.copy(alpha=.24f), radial(angle.toDouble(),inner), radial(angle.toDouble(),radius), 1.dp.toPx())
    val maximum=frame.directionCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
    frame.directionCounts.forEachIndexed { index, count ->
        if(count>0) {
            val end=inner+(radius-inner)*(count.toFloat()/maximum)
            val path=Path().apply {
                val a=radial(index*15.0-5.8,inner)
                val b=radial(index*15.0-5.8,end)
                val d=radial(index*15.0+5.8,end)
                val e=radial(index*15.0+5.8,inner)
                moveTo(a.x,a.y);lineTo(b.x,b.y);lineTo(d.x,d.y);lineTo(e.x,e.y);close()
            }
            drawPath(path, colors.accent.copy(alpha=.50f))
        }
    }
    frame.meanDirection?.let { mean ->
        drawLine(colors.fg.copy(alpha=.55f), radial(mean,inner), radial(mean,radius), 1.2.dp.toPx(),
            pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),4.dp.toPx())))
    }
    frame.points.lastOrNull()?.let { last ->
        val color=if(latestFresh)colors.accent else colors.muted
        val end=radial(last.value,radius)
        drawLine(color, center,end,2.dp.toPx())
        drawCircle(color,3.dp.toPx(),end)
    }
    selected?.let { point ->
        val end=radial(point.value,radius*1.08f)
        drawLine(colors.fg,center,end,1.5.dp.toPx())
        drawCircle(colors.fg,4.dp.toPx(),end,style=Stroke(1.5.dp.toPx()))
    }
    drawCircle(colors.bg,inner*.7f,center)
    drawCircle(colors.fg,2.dp.toPx(),center)
}

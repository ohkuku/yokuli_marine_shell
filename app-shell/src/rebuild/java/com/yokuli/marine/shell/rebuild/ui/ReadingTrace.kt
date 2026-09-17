package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.data.Reading
import kotlin.math.abs

/** 中文：输入保持内部规范单位，所有标签统一调用全局格式器，避免转换两次或显示原始 kn。 */
@Composable internal fun ReadingTrace(os:OsStore,values:List<Reading>,metric:String,now:Long) {
    val c=LocalMetro.current
    val samples=values.filter {now-it.elapsed in 0..900_000 && it.value.isFinite()}.sortedBy { it.elapsed }
    var selectedAt by remember(metric) {mutableStateOf<Long?>(null)}
    var width by remember {mutableIntStateOf(1)}
    val end=now
    val start=minOf(samples.firstOrNull()?.elapsed ?: now,now-60_000).coerceAtLeast(now-900_000)
    val span=(end-start).coerceAtLeast(1)
    val minimum=samples.minOfOrNull {it.value} ?: 0.0
    val maximum=samples.maxOfOrNull {it.value} ?: 1.0
    val padding=((maximum-minimum)*.12).coerceAtLeast(if(samples.firstOrNull()?.unit=="hPa") .1 else .5)
    val lower=minimum-padding;val upper=maximum+padding
    val selected=selectedAt?.let { time -> samples.minByOrNull {abs(it.elapsed-time)} }
    val pick:(Float)->Unit={x->selectedAt=start+(x/width.coerceAtLeast(1)).coerceIn(0f,1f).times(span).toLong()}
    val pickCurrent by rememberUpdatedState(pick)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(metricName(os,metric),13,c.muted)
            Label(os.t("${samples.size} 个样本","${samples.size} samples"),13,c.muted)
        }
        Canvas(Modifier.fillMaxWidth().height(178.dp).clipToBounds().onSizeChanged {width=it.width}
            .semantics { contentDescription = os.t("真实读数趋势，点按或拖动查看样本", "Actual reading history. Tap or drag to inspect a sample") }
            .pointerInput(metric) {detectTapGestures {pickCurrent(it.x)}}
            .pointerInput(metric) {detectDragGestures(onDragStart={pickCurrent(it.x)}) {change,_->change.consume();pickCurrent(change.position.x)}}) {
            for(i in 0..3) {val y=size.height*i/3;drawLine(c.muted.copy(alpha=.18f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())}
            fun position(v:Reading)=Offset(((v.elapsed-start).toDouble()/span*size.width).toFloat(),(size.height-(v.value-lower)/(upper-lower)*size.height).toFloat())
            var previous:Reading?=null
            val path=Path()
            samples.forEach {value->
                val p=position(value);val old=previous
                if(old==null || value.elapsed-old.elapsed>10_000 || value.source!=old.source || (value.unit.startsWith("°") && abs(value.value-old.value)>180))path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
                previous=value
            }
            drawPath(path,c.accent,style=Stroke(2.dp.toPx()))
            samples.lastOrNull()?.let {drawCircle(if(it.fresh(now))c.accent else c.muted,3.dp.toPx(),position(it))}
            selected?.let {val p=position(it);drawLine(c.fg.copy(alpha=.5f),Offset(p.x,0f),Offset(p.x,size.height),1.dp.toPx());drawCircle(c.fg,4.dp.toPx(),p)}
        }
        if(samples.isEmpty()) Label(os.t("等待第一份读数","waiting for the first reading"),15,c.muted)
        else if(selected!=null) Label("${os.formatMetric(metric,selected.value)} · ${readingAge(os,selected.elapsed,now)} · ${selected.source}",15,c.accent)
        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(os.t("低 ${os.formatMetric(metric,minimum)}","low ${os.formatMetric(metric,minimum)}"),13,c.muted)
            Label(os.t("高 ${os.formatMetric(metric,maximum)}","high ${os.formatMetric(metric,maximum)}"),13,c.muted)
        }
        if(samples.isNotEmpty()) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(os.t("${((now-start)/60_000).coerceAtLeast(1)} 分钟前","${((now-start)/60_000).coerceAtLeast(1)} min ago"),12,c.muted)
            Label(os.t("现在","now"),12,c.muted)
        }
    }
}

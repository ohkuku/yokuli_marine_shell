package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.chart.MapViewState
import com.yokuli.marine.shell.rebuild.data.Fix
import com.yokuli.marine.shell.rebuild.decimal
import com.yokuli.marine.core.design.WpTypeScale

/** 海图与锚警共享的地图壳：图源入口、缩放手势与船位读数使用相同位置和尺寸。 */
@Composable internal fun MapPageHeader(os:OsStore,title:String,onSource:()->Unit,hasLocalBack:Boolean=false) {
    val navigation=pageNavigation(os,title,hasLocalBack)
    val c=LocalMetro.current
    val insets=LocalShellHorizontalInsets.current
    Row(Modifier.fillMaxWidth().heightIn(min=58.dp).padding(start=insets.pageStart,end=insets.pageEnd),verticalAlignment=Alignment.CenterVertically) {
        if(navigation.canGoBack)HeaderBackButton(navigation,compact=true)
        Label(title,WpTypeScale.PageTitle,modifier=Modifier.weight(1f),maxLines=1)
        Row(Modifier.widthIn(max=150.dp).heightIn(min=48.dp).clickable(onClick=onSource).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Label(os.maps.sourceName(os.chinese),WpTypeScale.Caption,c.muted,Modifier.weight(1f,fill=false).padding(end=8.dp),maxLines=1)
            Glyph("layers",Modifier.size(24.dp))
        }
    }
}

@Composable internal fun MapCrosshairReadout(os:OsStore,point:GeoPoint,onClose:()->Unit) {
    val c=LocalMetro.current
    Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Label(os.formatCoordinates(point),15,modifier=Modifier.weight(1f))
        Box(Modifier.size(38.dp).clickable(onClick=onClose),contentAlignment=Alignment.Center){Glyph("close",Modifier.size(23.dp))}
    }
}

@Composable internal fun MapZoomControls(view:MapViewState,modifier:Modifier=Modifier,onZoom:((Double)->Unit)?=null) {
    val c=LocalMetro.current
    Column(modifier.background(c.bg.copy(alpha=.94f))) {
        listOf("plus" to 1.0,"minus" to -1.0).forEach {(icon,step)->
            Box(Modifier.size(46.dp).clickable {val zoom=(view.zoom+step).coerceIn(1.0,22.0);if(onZoom!=null)onZoom(zoom)else view.fly(view.center,zoom)},contentAlignment=Alignment.Center) {
                Glyph(icon,Modifier.size(22.dp))
            }
        }
    }
}

@Composable internal fun MapPositionReadout(os:OsStore,fix:Fix?,now:Long,modifier:Modifier=Modifier) {
    val c=LocalMetro.current
    val fresh=fix?.fresh(now)==true
    fun age(elapsed:Long):String {
        val seconds=((now-elapsed).coerceAtLeast(0)/1000)
        return if(seconds<2)os.t("刚刚","now")else if(seconds<60)os.t("${seconds} 秒前","${seconds}s ago")else os.t("${seconds/60} 分钟前","${seconds/60}m ago")
    }
    Column(modifier.background(c.bg.copy(alpha=.94f)).padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(if(fresh)c.accent else c.muted));Spacer(Modifier.width(8.dp))
            Label(fix?.let {os.formatSpeed(it.speed)+it.freshCourse(now)?.let {course->"   COG ${decimal(course,0)}°"}.orEmpty()} ?: os.t("等待首次船位","waiting for first position"),15)
        }
        Label(fix?.let {os.t("船位 ","position ")+age(it.elapsed)} ?: os.t("开启定位后显示船位","position appears when a source is enabled"),11,c.muted)
        fix?.let {
            val held=buildList {
                if(it.speed!=null && it.freshSpeed(now)==null)add(os.t("航速 ","speed ")+age(it.speedElapsed))
                if(it.course!=null && it.freshCourse(now)==null)add("COG ${decimal(it.course,0)}° · "+age(it.courseElapsed))
            }
            if(held.isNotEmpty())Label(held.joinToString(" · "),10,c.muted)
        }
    }
}

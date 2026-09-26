package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*

/** 常驻地图的一条航线状态；不另存起终点。 */
@Composable internal fun RouteDraftSummary(os:OsStore,onInspect:()->Unit) {
    val c=LocalMetro.current
    val system=os.marine?.system
    val workspace=system?.analysis?.state?.collectAsState()?.value
    val data=system?.charts?.state?.collectAsState()?.value
    val tick=rememberMarineClock()
    val now=remember(tick){System.currentTimeMillis()}
    val points=os.draftRoute
    val result=workspace?.analysis?.takeIf {data!=null&&draftMatchesAnalysis(os,it,data,now)}
        ?:workspace?.plan?.candidates?.firstOrNull {data!=null&&draftMatchesAnalysis(os,it.analysis,data,now)}?.analysis
    val name=os.routes.firstOrNull {it.id==os.editingRouteId}?.name ?: os.t("新航线","New route")
    Column(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=14.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Label(name,17,modifier=Modifier.weight(1f),maxLines=1)
            val count=(os.draftNavigationTargetIndices?:points.indices.toList()).size
            Label(os.t("$count 个航点","$count waypoints"),12,c.muted)
        }
        if(points.isEmpty()) {
            Label(os.t("用准星选择起点，再添加终点。手动与自动生成都编辑这条航线。","Aim at a start, then add a destination. Manual changes and automatic suggestions use this same route."),13,c.muted)
            MetroButton(os.t("以当前船位为起点","Start at boat position"),{
                val fix=os.hub.state.value.fix(os.positionSource)?.takeIf {it.fresh(SystemClock.elapsedRealtime())&&it.point.valid()&&os.positionSource!="demo"}
                if(fix!=null){os.draftRoute=listOf(fix.point);os.draftNavigationTargetIndices=null;os.follow=false}
                else os.notify("船位不可用，请在地图上选择起点", "Position unavailable; choose a start on the map")
            })
        } else {
            val ending=if(points.size>1)"  →  "+os.formatCoordinates(points.last())else os.t(" · 继续添加终点"," · Add a destination")
            Label(os.t("起点 ","Start ")+os.formatCoordinates(points.first())+ending,12,c.muted,maxLines=2)
            if(points.size>1)MenuRow(when {
                draftCalculationBusy(os)->os.t("正在检查／生成…","Checking / generating…")
                result!=null->draftVerdict(os,result.severity)
                os.maps.selectedDatasetIds.isEmpty()->os.t("未检查 · 图册尚未启用航行数据","Not checked · No navigation data enabled in Library")
                else->os.t("未检查或条件已变化","Not checked or conditions changed")
            },os.t("查看检查与建议","View checks and suggestions")) {onInspect()}
        }
    }
}

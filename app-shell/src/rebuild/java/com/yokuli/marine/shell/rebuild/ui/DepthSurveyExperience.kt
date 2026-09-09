package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.anchorwatch.data.database.DepthSampleEntity
import com.yokuli.anchorwatch.domain.sonar.TideMode
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import java.text.DateFormat
import java.util.Date

/** A chart workspace for the user's actual measurements, not a separate OS app. */
@Composable fun DepthSurveyScreen(os:OsStore) {
    val marine=os.marine?:return;val vm=marine.vm;val state by vm.ui.collectAsState()
    var start by remember {mutableStateOf(false)}
    var sample by remember {mutableStateOf<DepthSampleEntity?>(null)}
    var corrected by remember {mutableStateOf(false)}
    val all=state.sonarSamples.filter {it.usable&&(!corrected||it.normalizedDepthMeters!=null)}
    val visible=remember(all) {val step=(all.size/1800).coerceAtLeast(1);all.filterIndexed {index,_->index%step==0}.take(1800)}
    val view=remember {MapViewState(os.center,os.zoom)}
    var fitted by remember(state.selectedSonarSurveyId) {mutableStateOf(false)}
    LaunchedEffect(visible,state.selectedSonarSurveyId) {if(!fitted&&visible.isNotEmpty()){view.fit(visible.map{GeoPoint(it.latitude,it.longitude)});fitted=true}}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("实测水深","measured depth"),os.t("海图","CHART"))
        Pivot(listOf(os.t("水深图","depth map"),os.t("调查记录","surveys"))) {page->
            if(page==0)Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=22.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    Label(if(corrected)os.t("改正到基准面","corrected datum")else os.t("测得水深","measured depth"),17)
                    MapSourceButton(os)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MarineMap(os.maps,MapScene(points=visible.map {point->
                        val value=if(corrected)point.normalizedDepthMeters!! else point.measuredDepthMeters
                        MapPoint("depth:${point.id}",GeoPoint(point.latitude,point.longitude),os.formatDepth(value),
                            if(point.depthHeld)0xFF888888 else when{value<3->0xFFD04848;value<10->0xFF007F9B;else->0xFF466CA5},5f)
                    }),view,Modifier.fillMaxSize(),onEvent={event->if(event is MapEvent.ItemSelected)sample=visible.firstOrNull{"depth:${it.id}"==event.id}})
                }
                Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Label(os.t("${all.size} 个可用记录点 · 灰色为沿用深度","${all.size} usable samples · gray indicates held depth"),15,LocalMetro.current.muted)
                    if(all.isEmpty())Label(os.t("连接水深与位置数据后，可沿着自己的航迹记录。","Connect depth and position data to record along your track."),18)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        MetroButton(if(state.activeSonarSurvey==null)os.t("开始水深调查","record depths")else os.t("结束调查","finish survey"),{if(state.activeSonarSurvey==null)start=true else vm.stopSonarSurvey()},primary=true)
                        MetroButton(if(corrected)os.t("看测量值","measured")else os.t("看基准面值","datum"),{corrected=!corrected})
                    }
                }
            } else PageBody {
                if(state.sonarSurveys.isEmpty())Label(os.t("自己的每一次测量","your own measurements"),34)
                state.sonarSurveys.forEach {survey->
                    MenuRow((if(survey.id==state.selectedSonarSurveyId)"✓ " else "")+survey.name,
                        os.t("${survey.sampleCount} 点 · ${if(survey.active)"记录中" else "已保存"}","${survey.sampleCount} samples · ${if(survey.active)"recording" else "saved"}"),"sonar"){vm.selectSonarSurvey(survey.id)}
                    if(survey.id==state.selectedSonarSurveyId)MetroButton(os.t("导出测量 CSV","export measurements CSV"),{vm.exportSonarCsv(survey)})
                }
                MenuRow(os.t("数据来源","data sources"),os.t("查看船位、水深与安装参考","position, depth and installation reference"),"data"){os.open("settings:sources")}
                Label(os.t("每点保留时间、来源和改正信息；未经过的地方不会生成水深。","Each point retains its time, source and corrections. Unvisited areas have no generated depths."),17,LocalMetro.current.muted)
            }
        }
    }
    sample?.let {point->Dialog(onDismissRequest={sample=null}) {Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).padding(22.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        Label(os.formatDepth(if(corrected)point.normalizedDepthMeters else point.measuredDepthMeters),43,LocalMetro.current.accent)
        Label(os.formatCoordinates(GeoPoint(point.latitude,point.longitude)),20)
        Label(DateFormat.getDateTimeInstance().format(Date(point.timestamp)),17)
        Label("${point.sentenceType} · ${point.depthReference}",16,LocalMetro.current.muted)
        if(point.depthHeld)Label(os.t("沿用上次深度，采样时距更新 ${point.depthAgeMillis/1000}s","Held depth; ${point.depthAgeMillis/1000}s since update at sample time"),17)
        MetroButton(os.t("完成","done"),{sample=null})
    }} }
    if(start) {
        var name by remember {mutableStateOf(os.t("水深调查","Depth survey")+" "+DateFormat.getDateInstance().format(Date()))}
        var tide by remember {mutableStateOf("")}
        var correction by remember {mutableStateOf(false)}
        Dialog(onDismissRequest={start=false}) {Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).padding(22.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
            Label(os.t("记录实测水深","record measured depths"),31)
            Field(os.t("名称","name"),name,{name=it.take(100)})
            Toggle(os.t("手动潮高改正","manual tide correction"),correction,os.t("使用你确认的潮高，记录原始值和改正值。","Use a tide height you have established. Keep original and corrected values.")){correction=it}
            if(correction)Field(os.t("潮高 · m","tide height · m"),tide,{tide=it})
            Label(os.t("需要实际收到位置与水深；开始请求不会制造缺失的测量。","Position and depth must actually arrive before samples can be recorded."),17,LocalMetro.current.muted)
            MetroButton(os.t("开始调查","start survey"),{vm.startSonarSurvey(name.trim(),if(correction)TideMode.MANUAL else TideMode.OFF,tide.toDoubleOrNull()?:0.0);start=false},primary=true,
                enabled=name.isNotBlank()&&(!correction||tide.toDoubleOrNull()?.isFinite()==true))
            MetroButton(os.t("取消","cancel"),{start=false})
        }}
    }
}

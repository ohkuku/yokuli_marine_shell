package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos

/** 同一资料包的持久产品准备入口，页面离开只停止呈现，不终止 Core 已接受的任务。 */
@Composable internal fun PrepareChartAreaAction(os:OsStore,dataset:ChartDataset) {
    val system=os.marine?.system
    val chartState by os.maps.charts.state.collectAsState()
    val planning=system?.planning?.state?.collectAsState()?.value
    val navigation=planning?.preparation?.takeIf{it.request.datasetId==dataset.id}
    val terrain=chartState.terrainPreparation.firstOrNull{it.datasetId==dataset.id}
    var open by rememberSaveable(dataset.id){mutableStateOf(false)}
    var radius by rememberSaveable(dataset.id){mutableStateOf(5_000.0)}
    var latitude by rememberSaveable(dataset.id){mutableStateOf(os.center.lat)}
    var longitude by rememberSaveable(dataset.id){mutableStateOf(os.center.lon)}
    var submitting by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val preparing=navigation?.phase==PassagePreparationPhase.PREPARING || terrain?.let{it.queued+it.preparing>0}==true
    ChartPreparedStorageSummary(os,dataset)
    MenuRow(os.t("准备离线区域","Prepare an offline area"),
        os.t("三维和规划提前准备，保存后可随资料包导出","Prepare 3D and routing; include them when exporting"),"download") {
        latitude=os.center.lat;longitude=os.center.lon;error=null;open=true
    }
    terrain?.let{progress->
        if(progress.queued+progress.preparing>0)MetroProgress(os.t("三维 · ${progress.ready} 块已保存","3D · ${progress.ready} blocks saved"))
        else if(progress.ready>0)Label(os.t("三维 · ${progress.ready} 块可离线使用","3D · ${progress.ready} offline blocks"),12,LocalMetro.current.muted)
        if(progress.queued+progress.preparing>0)MetroButton(os.t("停止三维准备","Stop preparing 3D"),{scope.launch{
            try{os.maps.charts.cancelTerrainPreparation(dataset.id)}
            catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){error=chartDataError(os,failure.message?:"CHART_PREPARATION_FAILED")}
        }})
        if(progress.failed>0)Label(os.t("${progress.failed} 块未完成 · 可重新准备","${progress.failed} blocks unfinished · Prepare again to retry"),12,LocalMetro.current.muted)
    }
    navigation?.let{job->
        val text=when(job.phase) {
            PassagePreparationPhase.PREPARING->os.t("规划资料 · ${job.completed} / ${job.total}","Routing data · ${job.completed} / ${job.total}")
            PassagePreparationPhase.COMPLETE->os.t("这个区域的规划资料已保存","Routing data saved for this area")
            PassagePreparationPhase.CANCELLED->os.t("准备已暂停，完成的区域保留","Preparation stopped; completed areas are kept")
            PassagePreparationPhase.INTERRUPTED->os.t("准备中断，继续可复用已完成区域","Preparation interrupted; completed areas can be reused")
            PassagePreparationPhase.FAILED->os.t("这个区域尚未完整准备","This area is not fully prepared")
        }
        if(job.phase==PassagePreparationPhase.PREPARING)MetroProgress(text)else Label(text,12,LocalMetro.current.muted)
        if(job.phase==PassagePreparationPhase.PREPARING)MetroButton(os.t("停止规划资料准备","Stop preparing routing data"),{system?.planning?.cancel(job.request.requestId)})
    }
    if(!open)error?.let{Label(it,13,LocalMetro.current.accentText)}
    if(open)AppDialog(onDismissRequest={if(!submitting)open=false}) {AppDialogSurface {
        AppDialogTitle(os.t("准备离线区域","Prepare offline area"))
        Label(os.t("以海图当前中心为中心","Around the current chart centre"),13,LocalMetro.current.muted)
        Label(os.formatCoordinates(GeoPoint(latitude,longitude)),14)
        listOf(2_000.0,5_000.0,10_000.0).forEach{meters->ChoiceRow(os.unitFormats.distance(meters),radius==meters){radius=meters}}
        Label(os.t("准备在后台继续。现有资料可照常查询；没有覆盖的地方不会补造地形或航道。","Preparation continues in the background. Existing data remains usable; missing terrain and waterways are not invented."),12,LocalMetro.current.muted)
        error?.let{Label(it,13,LocalMetro.current.accentText)}
        MetroButton(os.t("开始准备","Prepare"),{
            val center=ChartPoint(latitude,longitude)
            val dy=radius/111_320.0
            val dx=dy/cos(Math.toRadians(latitude)).coerceAtLeast(.003)
            fun lon(v:Double)=((v+180)%360+360)%360-180
            val bounds=ChartBounds(lon(longitude-dx),(latitude-dy).coerceAtLeast(-89.8),lon(longitude+dx),(latitude+dy).coerceAtMost(89.8))
            submitting=true;error=null
            scope.launch {
                try {
                    requireNotNull(system){"MARINE_CORE_UNAVAILABLE"}
                    val requests=terrainPreparationRequests(dataset.id,dataset.revision,center,radius)
                    os.maps.charts.prepareTerrainRegion(requests)
                    system.planning.prepareRegion(PassagePreparationRequest(UUID.randomUUID().toString(),dataset.id,bounds))
                    open=false
                }catch(cancel:CancellationException){throw cancel}
                catch(failure:Exception){error=chartDataError(os,failure.message?:"CHART_PREPARATION_FAILED")}
                finally{submitting=false}
            }
        },primary=true,enabled=!submitting&&!preparing&&dataset.offlineReadable&&system!=null)
        MetroButton(os.t("取消","Cancel"),{open=false},enabled=!submitting)
    }}
}

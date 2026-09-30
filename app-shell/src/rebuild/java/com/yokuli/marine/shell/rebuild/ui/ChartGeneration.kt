package com.yokuli.marine.shell.rebuild.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos

/** 明确的一次性制图动作；只写 MBTiles，不改变查询资料或当前海图选择。 */
@Composable internal fun GenerateChartAction(os:OsStore,dataset:ChartDataset,bundleId:String) {
    var open by rememberSaveable(dataset.id) {mutableStateOf(false)}
    var radius by rememberSaveable(dataset.id) {mutableStateOf(20_000.0)}
    var zoom by rememberSaveable(dataset.id) {mutableIntStateOf(14)}
    var night by rememberSaveable(dataset.id) {mutableStateOf(false)}
    var request by remember {mutableStateOf<ChartRasterization?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var submitting by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val service=os.marine?.system?.charts
    val state=service?.state?.collectAsState()?.value
    val running=state?.exportJob?.phase in setOf(ChartExportPhase.PREPARING,ChartExportPhase.PACKAGING,ChartExportPhase.COPYING)
    val destination=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->
        val frozen=request
        if(uri!=null&&frozen!=null&&service!=null)scope.launch {
            submitting=true;error=null
            try {
                os.context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                when(val result=service.exportPackage(ChartExportRequest(UUID.randomUUID().toString(),dataset.id,uri.toString(),chart=frozen,collectionId=bundleId))) {
                    is ChartCommandResult.Accepted,is ChartCommandResult.Saved->open=false
                    is ChartCommandResult.Failed->error=chartDataError(os,result.reason)
                    ChartCommandResult.Busy->error=os.t("另一个生成或导出任务正在进行","Another chart or export task is running")
                }
            }catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){error=chartDataError(os,failure.message?:"CHART_SOURCE_UNAVAILABLE")}
            finally {submitting=false;request=null}
        }
    }
    MenuRow(os.t("生成离线海图","Create offline chart"),os.t("生成 MBTiles，加入当前资料包","Generate MBTiles and add to this collection"),"chart") {open=true}
    if(open)AppDialog(onDismissRequest={if(!submitting)open=false}) {AppDialogSurface {
        AppDialogTitle(os.t("生成海图","Create chart"))
        val center=os.center
        val dy=radius/111_320.0
        val dx=dy/cos(Math.toRadians(center.lat.coerceIn(-85.0,85.0))).coerceAtLeast(.001)
        fun longitude(value:Double)=((value+180)%360+360)%360-180
        val bounds=ChartBounds(longitude(center.lon-dx),(center.lat-dy).coerceAtLeast(-85.05112878),longitude(center.lon+dx),(center.lat+dy).coerceAtMost(85.05112878))
        val p=os.maps.portrayalPreferences
        val configuration=ChartRasterization(bounds,minOf(8,zoom),zoom,p.shallowDepthMeters,p.safetyDepthMeters,p.deepDepthMeters,night)
        val tiles=configuration.tileCount()
        Label(os.t("以海图当前中心为中心","Around the current chart centre"),13,LocalMetro.current.muted)
        Label(os.formatCoordinates(center),14)
        Label(os.t("范围半径","Radius"),13,LocalMetro.current.muted)
        listOf(5_000.0,20_000.0,80_000.0).forEach {meters->ChoiceRow(os.unitFormats.distance(meters),radius==meters){radius=meters}}
        Label(os.t("细节","Detail"),13,LocalMetro.current.muted)
        listOf(12 to os.t("沿岸","Coastal"),14 to os.t("港湾","Harbour"),16 to os.t("近岸细节","Close detail")).forEach {(level,name)->ChoiceRow(name,zoom==level){zoom=level}}
        Toggle(os.t("夜间配色","Night palette"),night){night=it}
        Label(if(tiles<=ChartRasterization.MAX_TILES)os.t("$tiles 个图块 · 生成一次，离线直接读取","$tiles tiles · generated once, read offline")else os.t("范围过大，请缩小范围或降低细节","Reduce the radius or detail level"),13,LocalMetro.current.muted)
        Label(os.t("只画资料实际提供的内容；没有资料的区域透明。原数据仍单独供查询和规划。","Only supplied features are drawn; missing areas remain transparent. Data stays separate for queries and planning."),12,LocalMetro.current.muted)
        error?.let {Label(it,13,LocalMetro.current.accentText)}
        MetroButton(os.t("选择保存文件夹","Choose output folder"),{request=configuration;destination.launch(null)},enabled=dataset.offlineReadable&&!running&&!submitting&&tiles in 1..ChartRasterization.MAX_TILES)
        MetroButton(os.t("取消","Cancel"),{open=false},enabled=!submitting)
    }}
}

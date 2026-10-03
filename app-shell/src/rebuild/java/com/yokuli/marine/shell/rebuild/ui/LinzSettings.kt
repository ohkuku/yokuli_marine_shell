package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos

/** 密钥只提交 Core；已保存的密钥不回读、不进入保存实例状态。 */
@Composable internal fun LinzSettingsSection(os:OsStore) {
    val service=os.marine?.system?.charts
    val state=service?.state?.collectAsState()?.value
    val configured=state?.linz?.configured==true
    var key by remember {mutableStateOf("")}
    var saving by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val c=LocalMetro.current
    fun save(value:String) {scope.launch {
        saving=true;message=null
        try {when(val result=service?.configureLinz(value)) {
            is ChartCommandResult.Saved->{key="";message=os.t("已保存","Saved")}
            is ChartCommandResult.Failed->message=chartDataError(os,result.reason)
            else->message=os.t("服务尚未就绪，请重试","Service is not ready; retry")
        }}catch(cancel:CancellationException){throw cancel}
        catch(error:Exception){message=os.t("未能保存，请重试","Could not save; retry")}
        finally{saving=false}
    }}
    PageBody {
        AppSection("LINZ")
        Label(os.t("新西兰水文资料","New Zealand hydrographic data"),18)
        Label(os.t("按航线区域下载，完整保存后可离线规划。LINZ 提供数据，Yokuli 在本机计算水路。","Downloads the route area for offline planning. LINZ provides data; Yokuli calculates the water route on your device."),14,c.muted)
        Label(if(configured)os.t("API 密钥已配置 · 输入可替换","API key configured · enter a replacement")else os.t("API 密钥","API key"),14)
        BasicTextField(key,{key=it.take(256)},Modifier.fillMaxWidth().heightIn(min=48.dp).background(c.subtle).border(2.dp,c.controlStroke).padding(12.dp),
            textStyle=TextStyle(color=c.fg,fontSize=15.sp),cursorBrush=SolidColor(c.accentText),singleLine=true,visualTransformation=PasswordVisualTransformation())
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetroButton(os.t("保存密钥","Save key"),{save(key)},enabled=!saving&&key.isNotBlank()&&service!=null)
            if(configured)MetroButton(os.t("移除密钥","Remove key"),{save("")},enabled=!saving&&service!=null)
        }
        if(saving)MetroProgress(os.t("正在保存","Saving"))
        message?.let{Label(it,14,c.accentText)}
        MenuRow(os.t("获取 LINZ API 密钥","Get a LINZ API key"),"data.linz.govt.nz","next") {
            runCatching {os.context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://data.linz.govt.nz/my/api/")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))}
                .onFailure {message=os.t("没有可打开网页的应用，请在浏览器访问 data.linz.govt.nz/my/api/","No browser is available. Visit data.linz.govt.nz/my/api/ in a browser.")}
        }
        Label(os.t("使用 1:22k–1:90k 水文图层，含水深、岸线及可用障碍资料。它是参考 GIS，未按航海通告更新，不替代正式 ENC。","Uses 1:22k–1:90k hydrographic layers for depths, coasts and available hazards. This reference GIS is not corrected for Notices to Mariners and does not replace official ENCs."),13,c.muted)
        state?.linz?.cachedAtUtc?.let {stamp->Label(os.t("离线副本：","Offline copy: ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(stamp)),13,c.muted)}
        MenuRow(os.t("在海图册选用","Choose in Library"),os.t("在资料包内添加 LINZ 来源","Add a LINZ source inside a collection"),"folder"){os.openLinked("library:data")}
    }
}

@Composable internal fun LinzBundleControls(os:OsStore) {
    val service=os.marine?.system?.charts
    val data=service?.state?.collectAsState()?.value?:ChartDataState()
    val selected=os.maps.selectedDatasetIds.firstOrNull()==LINZ_ONLINE_DATASET_ID
    val configured=data.linz?.configured==true
    val cached=data.datasets.firstOrNull{it.id==LINZ_ONLINE_DATASET_ID}
    val scope=rememberCoroutineScope()
    var message by remember {mutableStateOf<String?>(null)}
        MenuRow(os.t("LINZ 设置","LINZ settings"),null,"settings"){os.openLinked("settings:linz")}
        MetroButton(os.t("更新当前海图区域","Update current chart area"),{
            val center=os.center;val latitudeDelta=15_000/111_320.0
            val longitudeDelta=latitudeDelta/cos(Math.toRadians(center.lat)).coerceAtLeast(.15)
            val bounds=ChartBounds((center.lon-longitudeDelta).coerceAtLeast(-180.0),(center.lat-latitudeDelta).coerceAtLeast(-90.0),
                (center.lon+longitudeDelta).coerceAtMost(180.0),(center.lat+latitudeDelta).coerceAtMost(90.0))
            scope.launch {try {
                val result=service?.refreshLinz(bounds)
                message=if(result is ChartCommandResult.Failed)chartDataError(os,result.reason)else if(result==ChartCommandResult.Busy)os.t("请等当前导入完成","Wait for the current import")else null
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){message=os.t("下载未开始，请重试","Download did not start; retry")}}
        },enabled=configured&&service!=null&&!data.loading&&data.activeJob?.phase !in setOf(ChartImportPhase.COPYING,ChartImportPhase.PARSING,ChartImportPhase.INDEXING,ChartImportPhase.COMMITTING))
        message?.let{Label(it,13,LocalMetro.current.accentText)}
}

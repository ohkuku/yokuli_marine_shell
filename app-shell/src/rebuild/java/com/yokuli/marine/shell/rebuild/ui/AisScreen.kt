package com.yokuli.marine.shell.rebuild.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.marine.shell.rebuild.scene.ais.*
import com.yokuli.runtime.contract.ais.*
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.compose.LocalInternalAppPageKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.*

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.distinctUntilChanged

/** AIS 是“看周围、选一艘、了解关系”的交通应用；地理海图由海图应用处理。 */
@Composable internal fun AisScreen(os:OsStore,initialPage:String="") {
    val s=rememberAisTraffic(os)
    val entry=remember(initialPage){when {initialPage.startsWith("target:")->initialPage;initialPage in listOf("sources","settings")->initialPage;else->"traffic"}}
    var path by rememberSaveable(initialPage){mutableStateOf(listOf(entry))}
    var selected by rememberSaveable(initialPage){mutableStateOf(initialPage.substringAfterLast(':',"").toIntOrNull())}
    val requestedView=remember(initialPage){initialPage.takeIf {it.startsWith("view:")}?.split(':')?.getOrNull(1)?.let {runCatching{AisView.valueOf(it)}.getOrNull()}}
    val initialTab=if(initialPage=="targets")2 else if(requestedView==AisView.THREE_D||requestedView==null&&s.preferences.lastView==AisView.THREE_D)1 else 0
    val pager=rememberPagerState(initialPage=initialTab){3}
    val scope=rememberCoroutineScope()
    var viewRestored by rememberSaveable(initialPage){mutableStateOf(requestedView!=null||initialPage=="targets")}
    var showTracks by rememberSaveable{mutableStateOf(false)}
    var showMenu by rememberSaveable{mutableStateOf(false)}
    var radarHelp by rememberSaveable{mutableStateOf(false)}
    var detailExpanded by rememberSaveable {mutableStateOf(false)}
    var targetFilter by rememberSaveable{mutableStateOf("all")}
    val pageState=rememberSaveableStateHolder()
    val enabled=LocalInternalAppInputEnabled.current
    val current=path.last()
    var rangeMeters by rememberSaveable{mutableDoubleStateOf(s.preferences.rangeNauticalMiles*1852.0)}
    var camera3d by rememberSaveable(stateSaver=AisSceneCameraState.Saver){mutableStateOf(AisSceneCameraState(rangeMeters=rangeMeters))}
    var cameraRestored by rememberSaveable{mutableStateOf(false)}
    var requestedFocusApplied by rememberSaveable(initialPage){mutableStateOf(false)}
    val requestedMmsi=initialPage.takeIf {it.startsWith("view:")}?.substringAfterLast(':')?.toIntOrNull()
    val requestedPosition=requestedMmsi?.let(s::target)?.position
    fun setRange(value:Double) {
        if(!value.isFinite())return
        rangeMeters=value.coerceIn(100.0,59264.0)
        camera3d=camera3d.copy(rangeMeters=rangeMeters)
    }
    fun saveRange(value:Double) {
        setRange(value)
        val requestedRange=rangeMeters
        if(abs(s.preferences.rangeNauticalMiles*1852.0-requestedRange)>.1)
            os.aisPreferences {it.copy(rangeNauticalMiles=requestedRange/1852.0)}
    }
    LaunchedEffect(s.runtime.ready) {
        if(s.runtime.ready&&!viewRestored){pager.scrollToPage(if(s.preferences.lastView==AisView.THREE_D)1 else 0);viewRestored=true}
        if(s.runtime.ready&&!cameraRestored){setRange(s.preferences.rangeNauticalMiles*1852.0);cameraRestored=true}
    }
    LaunchedEffect(pager.settledPage) { if(pager.settledPage==2) detailExpanded=false }
    val latestPreferences by rememberUpdatedState(s.preferences)
    LaunchedEffect(pager,viewRestored) {
        if(viewRestored)snapshotFlow {pager.settledPage}.distinctUntilChanged().collect {page->
            val mode=when(page){0->AisView.RADAR;1->AisView.THREE_D;else->null}
            if(mode!=null&&mode!=latestPreferences.lastView)os.aisPreferences {it.copy(lastView=mode)}
        }
    }
    // 旧海图 tab 深链仍可读；只跳一次，返回后继续在 AIS 看当前对象。
    LaunchedEffect(s.runtime.ready,requestedPosition,enabled) {
        if(enabled&&s.runtime.ready&&!requestedFocusApplied&&requestedMmsi!=null) {
            if(requestedView==AisView.CHART){requestedFocusApplied=true;os.openLinked("chart:ais:$requestedMmsi")}
            else if(requestedPosition!=null) {
                requestedFocusApplied=true
                if(requestedView==AisView.THREE_D)camera3d=camera3d.copy(centerLatitude=requestedPosition.latitude,centerLongitude=requestedPosition.longitude,followOwn=false)
            }
        }
    }
    fun go(page:String){if(enabled&&path.last()!=page)path=(path+page).takeLast(32)}
    fun openChart(mmsi:Int?=selected){if(!enabled)return;showMenu=false;os.openLinked(mmsi?.let {"chart:ais:$it"} ?: "chart")}
    fun viewTarget(mode:AisView,mmsi:Int) {
        if(!enabled)return
        selected=mmsi
        if(mode==AisView.CHART){openChart(mmsi);return}
        detailExpanded=false
        if(mode==AisView.THREE_D)s.target(mmsi)?.position?.let {point->
            camera3d=camera3d.copy(centerLatitude=point.latitude,centerLongitude=point.longitude,followOwn=false)
        }
        if(path.size>1&&path[path.lastIndex-1]=="traffic")path=path.dropLast(1)else go("traffic")
        scope.launch {pager.scrollToPage(if(mode==AisView.THREE_D)1 else 0)}
        viewRestored=true
    }
    fun back(){when {radarHelp->radarHelp=false;showMenu->showMenu=false;path.size>1->path=path.dropLast(1);detailExpanded->detailExpanded=false;selected!=null&&current=="traffic"->selected=null;else->os.shell.popRoute()}}
    AppBackHandler(path.size>1||showMenu||radarHelp||detailExpanded||selected!=null&&current=="traffic"){back()}
    ReportVisibleAppRoute(os,if(current!="traffic")"ais:$current" else if(pager.settledPage==2)"ais:targets" else "ais:view:${if(pager.settledPage==1)AisView.THREE_D.name else AisView.RADAR.name}"+(selected?.let {":$it"} ?: ""))
    AisRetainSelection(os,selected)
    Column(Modifier.fillMaxSize()) {
        if(current=="traffic") AisWorkspaceHeader(os,s,{go("sources")},{openChart()},{showMenu=true})
        else PageHeader(os,when {current=="settings"->os.t("交通提醒","Traffic alerts");current=="sources"->os.t("接收状态","Reception");else->current.substringAfter(':').toIntOrNull()?.let(s::target)?.displayName ?: os.t("船舶详情","Vessel details")},hasLocalBack=true)
        // 原生三维由内部 Pager 唯一持有；不使用双实例的整页 AnimatedContent。
        pageState.SaveableStateProvider(current) {
            when {
                current=="sources"->AisSources(os,s)
                current=="settings"->AisSettings(os,s)
                current.startsWith("target:")->AisTargetDetail(os,s,current.substringAfter(':').toIntOrNull(),::viewTarget,{mmsi->openChart(mmsi)},{go("sources")})
                else->Column(Modifier.fillMaxSize()) {
                    PivotHeaders(listOf(os.t("雷达","Radar"),os.t("三维","3D"),os.t("船舶","Vessels")),pager,compact=true)
                    // 每个场景持有固定的画布与范围尺，横滑到船舶页时不会突然改动三维 Surface 高度。
                    // Android 默认 EdgeEffect 会在翻页边缘画弧光；交通画布不叠加这类无数据装饰。
                    HorizontalPager(pager,Modifier.weight(1f).fillMaxWidth().clipToBounds(),userScrollEnabled=enabled&&!detailExpanded,verticalAlignment=Alignment.Top,overscrollEffect=null) {tab->
                        val active=enabled&&pager.settledPage==tab&&!pager.isScrollInProgress
                        CompositionLocalProvider(LocalInternalAppInputEnabled provides active) {
                            if(tab==2) AisTargetList(os,s,selected,targetFilter,{if(active)targetFilter=it}){mmsi->if(active){selected=mmsi;go("target:$mmsi")}}
                            else Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    CompositionLocalProvider(LocalInternalAppInputEnabled provides (active&&!detailExpanded)) {
                                        if(tab==0) AisRadar(os,s,selected,showTracks,{selected=it},Modifier.fillMaxSize(),rangeMeters=rangeMeters)
                                        else {
                                            val sceneData=remember(s,showTracks,os.chinese){aisSceneData(os,s,showTracks)}
                                            AisTrafficScene3D(sceneData,camera3d,{camera3d=it;rangeMeters=it.rangeMeters},selected?.toString(),{selected=it.toIntOrNull()},
                                                {scope.launch{pager.animateScrollToPage(0)}},active&&!detailExpanded,os.light,os.chinese,Modifier.fillMaxSize(),formatDistance={os.formatDistance(it)},onCameraGestureFinished={saveRange(camera3d.rangeMeters)},
                                                onOpenPositionSources={os.openLinked("data_center:source/POSITION")},onOpenAisSources={go("sources")},onOpenHeadingSources={os.openLinked("data_center:source/HEADING_TRUE")})
                                        }
                                    }
                                    if(pager.settledPage==tab&&selected!=null) AisVesselSheet(os,s,selected!!,detailExpanded,
                                        {detailExpanded=it},{detailExpanded=false;selected=null},::viewTarget,{openChart(it)},{go("sources")},Modifier.fillMaxSize())
                                }
                                AisRangeZoom(os,rangeMeters,::setRange,::saveRange,enabled=active&&s.runtime.ready&&!detailExpanded)
                            }
                        }
                    }
                }
            }
        }
    }
    if(showMenu&&enabled)AppDialog(onDismissRequest={showMenu=false}) {
        AppDialogSurface {
            AppDialogTitle(os.t("周围船舶","Surrounding vessels"))
            val attention=s.targets.count {it.riskLevel!=AisRiskLevel.NONE||it.distress==AisDistressState.ACTIVE}
            if(attention>0)MenuRow(os.t("$attention 个目标需要关注","$attention targets need attention")){showMenu=false;detailExpanded=false;targetFilter="attention";scope.launch{pager.animateScrollToPage(2)}}
            Toggle(os.t("显示观测轨迹","Show observed tracks"),showTracks){showTracks=it}
            MenuRow(os.t("交通提醒","Traffic alerts"),if(s.preferences.monitoringEnabled)os.t("已开启 · 查看提醒规则","On · review alert rules")else os.t("设置会遇与接近提醒","Set approach and proximity alerts")){showMenu=false;go("settings")}
            MenuRow(os.t("接收状态","Reception"),aisInputSummary(os,s)){showMenu=false;go("sources")}
            MenuRow(os.t("雷达图例","Radar guide")){showMenu=false;radarHelp=true}
            MenuRow(os.t("在海图查看","Open Chart")){openChart()}
            PinTileAction(os, aisOverviewTileBinding()) { showMenu = false }
            MetroButton(os.t("完成","Done"),{showMenu=false})
        }
    }
    if(radarHelp&&enabled)AppDialog(onDismissRequest={radarHelp=false}) {RadarInfo(os){radarHelp=false}}
}

@Composable private fun AisHeaderAction(icon:String,label:String,enabled:Boolean=true,onClick:()->Unit) {
    val active=enabled&&LocalInternalAppInputEnabled.current
    Box(Modifier.size(48.dp).semantics {contentDescription=label}.clickable(enabled=active,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Glyph(icon,Modifier.size(24.dp))
    }
}

/** 观察型工作区：48dp 应用栏 + 44dp Pivot，接收状态不再另占数行。 */
@Composable private fun AisWorkspaceHeader(os:OsStore,s:TrafficSnapshot,onSources:()->Unit,onChart:()->Unit,onMore:()->Unit) {
    val c=LocalMetro.current
    val insets=LocalShellHorizontalInsets.current
    val navigation=pageNavigation(os,os.title(AppId.AIS),hasLocalBack=false)
    val risks=s.targets.count {it.riskLevel!=AisRiskLevel.NONE||it.distress==AisDistressState.ACTIVE}
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=insets.pageStart,end=insets.pageEnd),verticalAlignment=Alignment.CenterVertically) {
        // 紧凑工作区也消费同一访问返回关系；不按 AIS 归属猜测调用者。
        if(navigation.canGoBack)AisHeaderAction("back",navigation.backLabel,navigation.enabled,navigation.back)
        Label(os.title(AppId.AIS),20,modifier=Modifier.weight(1f),maxLines=1)
        Box {
            AisHeaderAction("connect",aisInputSummary(os,s),onClick=onSources)
            Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(5.dp).background(if(s.inputs.any {it.state==AisInputState.ONLINE&&it.lastAisElapsed!=null})c.accentText else c.muted))
        }
        AisHeaderAction("chart",os.t("打开海图","Open Chart"),onClick=onChart)
        Box {
            AisHeaderAction("more",os.t("更多 · $risks 个需关注","More · $risks need attention"),onClick=onMore)
            if(risks>0)Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(6.dp).background(Color(0xFFFF795F)))
        }
    }
}

@Composable private fun AisTargetList(os:OsStore,s:TrafficSnapshot,selected:Int?,filter:String,onFilter:(String)->Unit,onSelect:(Int)->Unit) {
    val enabled=LocalInternalAppInputEnabled.current
    var query by rememberSaveable{mutableStateOf("")}
    var searching by rememberSaveable{mutableStateOf(false)}
    var sorting by rememberSaveable{mutableStateOf(false)}
    var sort by rememberSaveable{mutableStateOf("risk")}
    var order by rememberSaveable{mutableStateOf(emptyList<Int>())}
    val c=LocalMetro.current
    val insets=LocalShellHorizontalInsets.current
    fun sorted()=when(sort){"distance"->s.targets.sortedBy {it.relative.distanceMeters ?: Double.MAX_VALUE};"name"->s.targets.sortedBy {it.displayName.lowercase()};else->s.targets.sortedWith(compareByDescending<AisTarget>{it.distress==AisDistressState.ACTIVE}.thenByDescending{it.riskLevel.ordinal}.thenBy {it.relative.distanceMeters ?: Double.MAX_VALUE})}.map {it.mmsi}
    LaunchedEffect(s.targets.map {it.mmsi}.toSet()){order=order.filter {s.target(it)!=null}+sorted().filter {it !in order}}
    val targets=order.mapNotNull(s::target).filter {target->
        (filter!="watched"||target.watched)&&(filter!="attention"||target.riskLevel!=AisRiskLevel.NONE||target.distress==AisDistressState.ACTIVE)&&
            (query.isBlank()||listOf(target.displayName,aisNumber(target.mmsi),target.staticData.callSign?.value.orEmpty()).any {it.contains(query,true)})
    }
    Column(Modifier.fillMaxSize().padding(start=insets.pageStart,end=insets.pageEnd)) {
        Row(Modifier.fillMaxWidth().selectableGroup(),verticalAlignment=Alignment.CenterVertically) {
            listOf("all" to os.t("全部","All"),"attention" to os.t("提醒","Alerts"),"watched" to os.t("关注","Following")).forEach {(key,label)->
                Box(Modifier.weight(1f).heightIn(min=48.dp).selectable(filter==key,enabled=enabled,role=Role.Tab){onFilter(key)},contentAlignment=Alignment.CenterStart) {
                    Label(label,15,if(filter==key)c.accentText else c.muted)
                }
            }
            AisHeaderAction("search",os.t("搜索船舶","Search vessels")){searching=!searching;if(!searching)query=""}
            AisHeaderAction("more",os.t("排列船舶","Sort vessels")){sorting=true}
        }
        if(searching)Field(os.t("船名、MMSI 或呼号","Name, MMSI or call sign"),query,{query=it.take(80)})
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp)) {
            if(targets.isEmpty())item {Column(Modifier.padding(vertical=28.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                AppSection(when {query.isNotBlank()->os.t("没有匹配的船舶","No matching vessels");filter=="watched"->os.t("还没有关注船舶","No vessels followed yet");filter=="attention"->os.t("目前没有交通提醒","No current traffic alerts");else->os.t("等待 AIS 船舶","Waiting for AIS vessels")})
                Label(if(filter=="watched")os.t("打开一艘船的详情，即可关注它。","Open a vessel to follow it.")else aisInputSummary(os,s),15,c.muted)
            }}
            items(targets,key={it.mmsi}) {target->
                Row(Modifier.fillMaxWidth().heightIn(min=80.dp).background(if(target.mmsi==selected)c.subtle else Color.Transparent)
                    .clickable(enabled=enabled,role=Role.Button){onSelect(target.mmsi)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Glyph(if(target.distress==AisDistressState.ACTIVE)"warning" else "chart",Modifier.size(24.dp),if(target.riskLevel!=AisRiskLevel.NONE||target.distress==AisDistressState.ACTIVE)Color(0xFFFF795F)else c.muted)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Label((if(target.watched)"★ " else "")+target.displayName,15,weight=FontWeight.SemiBold,maxLines=1)
                        Label(aisState(os,target),12,c.muted,maxLines=2)
                    }
                    Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Label(target.relative.distanceMeters?.let(os::formatDistance) ?: "—",20)
                        Label(target.dynamic?.sogMetersPerSecond?.let {os.formatSpeed(it/.514444)} ?: aisKind(os,target.kind),12,c.muted,maxLines=1)
                    }
                }
            }
        }
    }
    if(sorting&&enabled)AppDialog(onDismissRequest={sorting=false}) {AppDialogSurface {
        AppDialogTitle(os.t("排列船舶","Sort vessels"))
        listOf("risk" to os.t("需关注的优先","Attention first"),"distance" to os.t("由近到远","Nearest first"),"name" to os.t("按船名","By name")).forEach {(value,label)->
            ChoiceRow(label,sort==value){sort=value;order=sorted();sorting=false}
        }
        Label(os.t("新报告更新读数，列表不会在你操作时跳位。","Reports update readings without moving rows under your finger."),12,c.muted)
    }}
}

@Composable internal fun AisRetainSelection(os:OsStore,mmsi:Int?) {
    val service=os.marine?.system?.ais
    val owner=remember {uid()}
    LaunchedEffect(service,mmsi,owner) {
        if(service==null||mmsi==null)return@LaunchedEffect
        try {
            service.command(AisCommand.RetainTarget(mmsi,true,owner))
            awaitCancellation()
        } finally {
            withContext(NonCancellable){service.command(AisCommand.RetainTarget(mmsi,false,owner))}
        }
    }
}

@Composable private fun AisSources(os:OsStore,s:TrafficSnapshot) {PageBody {
    Label(aisInputSummary(os,s),15)
    Label(os.t("自动识别已启用的 NMEA 输入，不需要另建连接。VDM 是周围目标，VDO 是设备本船报告，不替换系统船位。","AIS is recognised on enabled NMEA inputs. VDM reports surrounding targets; VDO is an own-station report and never replaces system position."),15,LocalMetro.current.muted)
    s.inputs.forEach {input->
        MenuRow(input.name,when(input.state){AisInputState.DISABLED->os.t("已关闭","off");AisInputState.CONNECTING->os.t("正在连接","connecting");AisInputState.ONLINE->os.t("传输在线","transport online");else->os.t("传输中断","transport interrupted")}){os.openLinked("nmea:connection:${Uri.encode(input.connectionId)}")}
        Label(os.t("${input.legalAisMessages} 条合法 AIS · ${input.dynamicTargets} 个动态目标","${input.legalAisMessages} valid AIS · ${input.dynamicTargets} dynamic targets"),15)
        Label(os.t("最近字节：","last bytes: ")+(input.lastByteElapsed?.let {readingAge(os,it,s.generatedElapsed)} ?: os.t("未收到","none received"))+" · "+os.t("合法 NMEA：","valid NMEA: ")+(input.lastNmeaElapsed?.let {readingAge(os,it,s.generatedElapsed)} ?: os.t("未收到","none received")),13,LocalMetro.current.muted)
        input.lastAisElapsed?.let {Label(os.t("AIS 最近接收 · ","AIS last received · ")+readingAge(os,it,s.generatedElapsed),14,LocalMetro.current.muted)}
        if(input.rejectedMessages>0)Label(os.t("已丢弃 ${input.rejectedMessages} 条损坏或歧义报文","${input.rejectedMessages} damaged or ambiguous messages discarded"),14,LocalMetro.current.muted)
        input.lastError?.let {Label(aisInputError(os,it),13,LocalMetro.current.muted)}
    }
    MenuRow(os.t("管理船上连接","manage boat connections")){os.openLinked("nmea")}
    if(s.ownReports.isNotEmpty()) {
        AppSection(os.t("设备本船报告","own-station reports"))
        if(s.ownIdentityConflict)Label(os.t("多台设备的 VDO 身份不一致，请指定本船 MMSI。","VDO identities disagree. Set the own-ship MMSI."),15,LocalMetro.current.accentText)
        s.ownReports.forEach {Label(aisNumber(it.mmsi)+" · "+(s.inputs.firstOrNull {input->input.connectionId==it.source.connectionId}?.name ?: it.source.connectionId),16)}
        Label(os.t("收到 VDO 不代表设备已通过无线电成功发射。","Receiving VDO is not confirmation of successful radio transmission."),14,LocalMetro.current.muted)
    }
}}

@Composable private fun AisSettings(os:OsStore,s:TrafficSnapshot) {
    val p=s.preferences
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val cpa=rememberUnitNumberDraft(p.cpaDistanceMeters,os.distanceUnitLabel,os::distanceValue,os::distanceMeters,p.cpaDistanceMeters)
    val minutes=rememberUnitNumberDraft(p.cpaTimeSeconds,"min",{it/60.0},{it*60.0},p.cpaTimeSeconds)
    val near=rememberUnitNumberDraft(p.proximityMeters,os.distanceUnitLabel,os::distanceValue,os::distanceMeters,p.proximityMeters)
    val anchor=rememberUnitNumberDraft(p.anchorProximityMeters,os.distanceUnitLabel,os::distanceValue,os::distanceMeters,p.anchorProximityMeters)
    var ownMmsi by rememberSaveable(p.ownMmsi){mutableStateOf(p.ownMmsi?.let(::aisNumber).orEmpty())}
    var saved by remember {mutableStateOf(false)}
    fun permissions(enable:Boolean){if(enable&&Build.VERSION.SDK_INT>=33)request.launch(Manifest.permission.POST_NOTIFICATIONS)}
    PageBody {
        AppSection(os.t("全船共用的交通规则","traffic rules shared across the boat"))
        Label(os.t("AIS 无法发现所有船。没有目标不代表周围安全；这些阈值是可调整初值，不是操船建议。","AIS does not detect every vessel. No targets does not mean clear surroundings. Limits are adjustable defaults, not manoeuvring advice."),15,LocalMetro.current.muted)
        Toggle(os.t("近距会遇提醒","closest-approach alerts"),p.cpaEnabled,os.t("CPA ${os.formatDistance(p.cpaDistanceMeters)} / ${decimal(p.cpaTimeSeconds/60,0)} 分钟","CPA ${os.formatDistance(p.cpaDistanceMeters)} / ${decimal(p.cpaTimeSeconds/60,0)} min")){enable->os.aisPreferences {it.copy(cpaEnabled=enable)};permissions(enable)}
        Field(os.t("CPA 距离","CPA distance")+" · ${os.distanceUnitLabel}",cpa.text,{cpa.edit(it);saved=false},number=true)
        Field(os.t("未来时间窗口（分钟）","future window (minutes)"),minutes.text,{minutes.edit(it);saved=false},number=true)
        Toggle(os.t("当前近距离提醒","current-distance alerts"),p.proximityEnabled,os.formatDistance(p.proximityMeters)){enable->os.aisPreferences {it.copy(proximityEnabled=enable)};permissions(enable)}
        Field(os.t("近距离范围","proximity distance")+" · ${os.distanceUnitLabel}",near.text,{near.edit(it);saved=false},number=true)
        Toggle(os.t("锚泊交通警戒","anchor traffic watch"),p.anchorProximityEnabled,os.t("独立于走锚报警，仅在本船锚泊时使用。","Independent from drag alarms; used while your vessel is anchored.")){enable->os.aisPreferences {it.copy(anchorProximityEnabled=enable)};permissions(enable)}
        Field(os.t("锚泊交通范围","anchor traffic distance")+" · ${os.distanceUnitLabel}",anchor.text,{anchor.edit(it);saved=false},number=true)
        ChoiceRow(os.t("相对当前船位","relative to current own position"),!p.anchorUsesAnchorPoint){os.aisPreferences {it.copy(anchorUsesAnchorPoint=false)}}
        ChoiceRow(os.t("锚点区域警戒","anchor-centred area watch"),p.anchorUsesAnchorPoint,os.t("显示锚点到目标的距离，不称为船间距离。","Distance is measured from the anchor, not between vessels.")){os.aisPreferences {it.copy(anchorUsesAnchorPoint=true)}}
        Toggle(os.t("关注目标丢失提醒","alert when a target of concern is lost"),p.lostTargetAlerts){value->os.aisPreferences {it.copy(lostTargetAlerts=value)}}
        Toggle(os.t("提醒声音","alert sound"),p.soundEnabled){value->os.aisPreferences {it.copy(soundEnabled=value)};permissions(value)}
        Field(os.t("本船 MMSI（可留空）","own MMSI (optional)"),ownMmsi,{ownMmsi=it.filter(Char::isDigit).take(9);saved=false},number=true)
        val valid=listOf(cpa,near,anchor).all {it.value?.let {v->v in 10.0..92600.0}==true}&&minutes.value?.let {it in 60.0..7200.0}==true&&(ownMmsi.isBlank()||ownMmsi.length==9&&ownMmsi.toIntOrNull()?.let {it>0}==true)
        MetroButton(os.t("保存阈值与本船身份","save limits and own identity"),{
            val distance=cpa.value ?: return@MetroButton;val window=minutes.value ?: return@MetroButton;val proximity=near.value ?: return@MetroButton;val anchoring=anchor.value ?: return@MetroButton;val identity=ownMmsi.toIntOrNull()
            os.aisPreferences(onSaved={saved=true}) {it.copy(cpaDistanceMeters=distance,cpaTimeSeconds=window,proximityMeters=proximity,anchorProximityMeters=anchoring,ownMmsi=identity)}
        },primary=true,enabled=valid)
        if(!valid)Label(os.t("距离应为 ${os.formatDistance(10.0)}–${os.formatDistance(92600.0)}，时间为 1–120 分钟；MMSI 为 9 位数字或留空。","Distances must be ${os.formatDistance(10.0)}–${os.formatDistance(92600.0)}, time 1–120 minutes; MMSI must contain 9 digits or be empty."),14,LocalMetro.current.muted)
        if(saved)Label(os.t("已保存","saved"),16,LocalMetro.current.accentText)
        AisLayerChoice(os,false);AisLayerChoice(os,true)
        AppSection(os.t("运行状态","runtime status"))
        Label(if(!p.monitoringEnabled)os.t("交通提醒未启用","traffic alerts are off")else if(s.runtime.foregroundActive)os.t("后台交通服务正在运行","background traffic service is running")else os.t("后台运行尚未确认","background execution is not confirmed"),17)
        if(p.monitoringEnabled&&!s.runtime.notificationsAllowed)Label(os.t("系统通知受限，无法保证后台提醒可见。","System notifications are restricted; background alerts may not be visible."),15)
        if(p.soundEnabled&&!s.runtime.soundAllowed)Label(os.t("系统声音设置受限，请检查通知渠道与勿扰模式。","Sound is restricted; check the notification channel and Do Not Disturb."),15)
        if(s.runtime.backgroundRestricted)Label(os.t("Android 限制后台运行，请调整电池设置。","Android restricts background execution; review battery settings."),15)
        if(s.runtime.inputQueueDrops>0)Label(os.t("输入过载，已丢弃 ${s.runtime.inputQueueDrops} 个帧；交通图景可能不完整。","Input overload dropped ${s.runtime.inputQueueDrops} frames; the traffic picture may be incomplete."),15)
        s.runtime.persistenceError?.let {Label(os.t("设置存储失败，请重试。","Settings storage failed; retry."),16)}
        s.runtime.foregroundError?.let {Label(os.t("后台服务未能启动，请保持应用可见并检查系统设置。","The background service could not start. Keep the app visible and check system settings."),15)}
        MenuRow(os.t("Android 通知设置","Android notification settings")){runCatching {os.context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,os.context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {os.notify("无法打开系统通知设置","Could not open system notification settings",app=AppId.AIS)}}
        Label(os.t("强行停止应用或关闭输入连接会中断监控。关闭地图图层不会停止已启用的提醒。","Force-stopping the app or closing input connections interrupts monitoring. Hiding a map layer does not stop enabled alerts."),14,LocalMetro.current.muted)
    }
}

private fun aisSceneData(os:OsStore,s:TrafficSnapshot,showTracks:Boolean):AisSceneData {
    fun AisPoint.scene()=AisScenePosition(latitude,longitude)
    val own=s.ownship?.takeIf {it.positionValid}
    return AisSceneData(ownPosition=own?.position?.scene(),ownHeadingDegrees=own?.headingDegrees,ownCogDegrees=own?.cogDegrees,ownSogMetersPerSecond=own?.sogMetersPerSecond,
        targets=s.targets.mapNotNull {t->t.position?.let {position->
            val fresh=t.state==AisTargetState.CURRENT&&!t.cached&&!t.positionInvalidated
            AisSceneTarget(t.mmsi.toString(),t.displayName,position.scene(),t.dynamic?.headingDegrees?.takeIf {fresh},t.dynamic?.cogDegrees?.takeIf {fresh},t.dynamic?.sogMetersPerSecond?.takeIf {fresh&&"sog_lower_bound" !in t.dynamic?.invalidFields.orEmpty()},
                t.staticData.dimensions?.value?.takeIf {it.usable}?.let {AisSceneDimensions(it.toBowMeters.toDouble(),it.toSternMeters.toDouble(),it.toPortMeters.toDouble(),it.toStarboardMeters.toDouble())},
                kind=when(t.kind){AisEntityKind.CLASS_A,AisEntityKind.CLASS_B,AisEntityKind.LONG_RANGE->AisSceneKind.VESSEL;AisEntityKind.AID_TO_NAVIGATION,AisEntityKind.VIRTUAL_AID->AisSceneKind.AID_TO_NAVIGATION;AisEntityKind.BASE_STATION->AisSceneKind.BASE_STATION;AisEntityKind.SAR_AIRCRAFT->AisSceneKind.AIRCRAFT;AisEntityKind.SART,AisEntityKind.MOB,AisEntityKind.EPIRB->AisSceneKind.DISTRESS;else->AisSceneKind.UNKNOWN},
                ageLabel=t.dynamic?.let {if(t.cached)os.t("历史缓存","cached")else readingAge(os,it.receivedElapsed,s.generatedElapsed)}.orEmpty(),statusLabel=aisState(os,t),stale=!fresh,lost=t.state==AisTargetState.LOST,risk=t.riskLevel!=AisRiskLevel.NONE,followed=t.watched,
                track=t.track.groupBy {it.segment}.values.map {it.map {p->p.position.scene()}})
        }},vectorSeconds=60.0,showTracks=showTracks)
}

private fun aisInputError(os:OsStore,error:String):String=when {
    error.contains("checksum")->os.t("丢弃损坏报文：完整性校验不符","damaged report discarded: integrity check failed")
    error.contains("fragment")||error.contains("group")->os.t("丢弃无法可靠拼接的分片组","fragment group could not be assembled reliably")
    error.contains("length")||error.contains("fields")||error.contains("bounds")->os.t("丢弃长度或字段不合规的报文","report discarded: invalid length or fields")
    error.contains("characters")||error.contains("padding")->os.t("丢弃编码不合规的报文","report discarded: invalid encoding")
    error.contains("identity")->os.t("丢弃身份字段不合规的报文","report discarded: invalid identity")
    else->os.t("输入诊断：","input diagnostic: ")+error.take(160)
}

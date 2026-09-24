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
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.dp
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

/** AIS 应用是交通服务的客户端；视图、筛选、相机与此次访问的返回路径归页面所有。 */
@Composable internal fun AisScreen(os:OsStore,initialPage:String="") {
    val s=rememberAisTraffic(os)
    val entry=remember(initialPage){when {initialPage.startsWith("target:")->initialPage;initialPage in listOf("sources","settings")->initialPage;else->"traffic"}}
    var path by rememberSaveable(initialPage){mutableStateOf(listOf(entry))}
    var selected by rememberSaveable(initialPage){mutableStateOf(initialPage.substringAfterLast(':',"").toIntOrNull())}
    val requestedView=initialPage.takeIf {it.startsWith("view:")}?.split(':')?.getOrNull(1)?.let {runCatching{AisView.valueOf(it)}.getOrNull()}
    var view by rememberSaveable(initialPage){mutableStateOf(requestedView ?: s.preferences.lastView)}
    var viewRestored by rememberSaveable {mutableStateOf(requestedView!=null)}
    LaunchedEffect(s.runtime.ready){if(s.runtime.ready&&!viewRestored){view=s.preferences.lastView;viewRestored=true}}
    var listExpanded by rememberSaveable{mutableStateOf(false)}
    var showTracks by rememberSaveable{mutableStateOf(false)}
    val pageState=rememberSaveableStateHolder()
    val storedPages=remember{mutableSetOf<String>()}
    LaunchedEffect(path){
        storedPages.filter {it !in path}.forEach(pageState::removeState)
        storedPages.clear();storedPages.addAll(path)
    }
    val instance=LocalInternalAppPageKey.current ?: remember {uid()}
    var camera3d by rememberSaveable(stateSaver=AisSceneCameraState.Saver){mutableStateOf(AisSceneCameraState(rangeMeters=s.preferences.rangeNauticalMiles*1852.0))}
    val map=os.maps.view("ais:$instance",s.ownship?.position?.geo() ?: os.center,12.0)
    var cameraRestored by rememberSaveable{mutableStateOf(false)}
    var requestedFocusApplied by rememberSaveable(initialPage){mutableStateOf(false)}
    val requestedMmsi=initialPage.takeIf {it.startsWith("view:")}?.substringAfterLast(':')?.toIntOrNull()
    val requestedPosition=requestedMmsi?.let(s::target)?.position
    LaunchedEffect(s.runtime.ready) {
        if(s.runtime.ready&&!cameraRestored){camera3d=camera3d.copy(rangeMeters=s.preferences.rangeNauticalMiles*1852.0);cameraRestored=true}
    }
    LaunchedEffect(s.runtime.ready,requestedPosition!=null) {
        if(s.runtime.ready&&requestedPosition!=null&&!requestedFocusApplied){
            requestedFocusApplied=true
            if(selected==requestedMmsi){
                if(requestedView==AisView.CHART)map.fly(requestedPosition.geo(),map.zoom)
                if(requestedView==AisView.THREE_D)camera3d=camera3d.copy(centerLatitude=requestedPosition.latitude,centerLongitude=requestedPosition.longitude,followOwn=false)
            }
        }
    }
    val enabled=LocalInternalAppInputEnabled.current
    val current=path.last()

    fun go(page:String){if(enabled&&path.last()!=page)path=(path+page).takeLast(32)}
    fun viewTarget(mode:AisView,mmsi:Int){
        selected=mmsi;view=mode;viewRestored=true;listExpanded=false
        // 只有明确“在此视图查看”才移动相机；单点选中和报文更新均不移动。
        s.target(mmsi)?.position?.let {point->
            if(mode==AisView.CHART)map.fly(point.geo(),map.zoom)
            if(mode==AisView.THREE_D)camera3d=camera3d.copy(centerLatitude=point.latitude,centerLongitude=point.longitude,followOwn=false)
        }
        os.aisPreferences {it.copy(lastView=mode)}
        go("traffic")
    }
    fun back(){when {path.size>1->path=path.dropLast(1);listExpanded->listExpanded=false;selected!=null&&current=="traffic"->selected=null;else->os.shell.popRoute()}}
    val localBack=path.size>1||listExpanded||selected!=null&&current=="traffic"
    AppBackHandler(localBack){back()}
    ReportVisibleAppRoute(os,if(current=="traffic")"ais:view:${view.name}"+(selected?.let {":$it"} ?: "")else "ais:$current")
    AisRetainSelection(os,selected)
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,when {current=="settings"->os.t("交通警戒","traffic watch");current=="sources"->os.t("AIS 输入","AIS inputs");current.startsWith("target:")->current.substringAfter(':').toIntOrNull()?.let(s::target)?.displayName ?: os.t("目标详情","target detail");else->"AIS"},
            hasLocalBack=path.size>1||entry!="traffic",localBackLabel=if(path.size>1)os.t("返回上一页","back to previous page")else null,
            trailing=if(current=="traffic"){{IconAction("more",os.t("警戒","watch"),{go("settings")})}}else null)
        AnimatedContent(current,label="ais-page") { page ->
            pageState.SaveableStateProvider(page) {
              CompositionLocalProvider(LocalInternalAppInputEnabled provides (enabled&&current==page)) {
                Box(Modifier.fillMaxSize().pointerInput(enabled,current==page) {
                    if(!enabled||current!=page)awaitPointerEventScope {
                        while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}
                    }
                }) {
                when {
                    page=="sources"->AisSources(os,s)
                    page=="settings"->AisSettings(os,s)
                    page.startsWith("target:")->AisTargetDetail(os,s,page.substringAfter(':').toIntOrNull(),{mode,mmsi->viewTarget(mode,mmsi)},{mmsi->showTracks=true;viewTarget(AisView.CHART,mmsi)},{go("sources")})
                    else->Column(Modifier.fillMaxSize()) {
                        val inset=LocalShellHorizontalInsets.current
                        Column(Modifier.padding(start=inset.pageStart,end=inset.pageEnd),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Label(aisInputSummary(os,s),14,LocalMetro.current.muted,Modifier.clickable {go("sources")}.padding(vertical=4.dp))
                            val located=s.targets.count {it.position!=null}
                            val risks=s.targets.count {it.riskLevel!=AisRiskLevel.NONE||it.distress==AisDistressState.ACTIVE}
                            Label(os.t("${s.targets.size} 个目标 · $located 个有位置 · $risks 个需关注","${s.targets.size} targets · $located located · $risks need attention"),13,LocalMetro.current.muted)
                            val own=s.ownship
                            Label(if(own?.headingDegrees!=null)os.t("船艏 ${os.formatBearing(own.headingDegrees)} T","heading ${os.formatBearing(own.headingDegrees)} T")else os.t("船首向未提供 · 北向参考","heading not provided · north reference"),12,LocalMetro.current.muted)
                            if(s.capacityLimited)Label(os.t("接收容量受限，未掌握全部目标","capacity limited; the target picture is incomplete"),13,LocalMetro.current.accent)
                            if(!s.ownship.let {it?.positionValid==true&&it.position!=null})Label(os.t("本船无有效定位 · 不计算当前相对关系","own position unavailable · current relative geometry is not calculated"),13,LocalMetro.current.muted)
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                            AisView.entries.forEach {mode->
                                val title=when(mode){AisView.RADAR->os.t("雷达","radar");AisView.THREE_D->os.t("三维","3D");AisView.CHART->os.t("海图","chart")}
                                Label(title,25,if(view==mode)LocalMetro.current.accent else LocalMetro.current.muted,
                                    Modifier.clickable(enabled=enabled){view=mode;viewRestored=true;os.aisPreferences {it.copy(lastView=mode)}}.padding(12.dp))
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when(view) {
                                AisView.RADAR->AisRadar(os,s,selected,showTracks,{selected=it},Modifier.fillMaxSize())
                                AisView.THREE_D->AisTrafficScene3D(aisSceneData(os,s,showTracks),camera3d,{camera3d=it},selected?.toString(),{selected=it.toIntOrNull()},
                                    {view=AisView.RADAR;os.aisPreferences {it.copy(lastView=AisView.RADAR)}},enabled&&current==page,os.light,os.chinese,Modifier.fillMaxSize(),formatDistance={os.formatDistance(it)})
                                AisView.CHART->{
                                    val own=s.ownship?.takeIf {it.positionValid}
                                    map.interactive=enabled&&current==page
                                    MarineMap(os.maps,MapScene(vessel=own?.position?.let {MapVessel(it.geo(),own.cogDegrees,true,own.headingDegrees,own.sogMetersPerSecond?.div(.514444))},
                                        aisTargets=aisMapTargets(s,selected?.toString(),showTracks)),map,Modifier.fillMaxSize(),onEvent={event->
                                        if(event is MapEvent.ItemSelected && event.id.startsWith("ais:"))selected=event.id.substringAfter(':').toIntOrNull()
                                    })
                                    MapSourceButton(os,Modifier.align(Alignment.TopEnd).background(LocalMetro.current.bg))
                                    Label(os.t("视口内 ${map.aisVisibleCount} 个目标 · 虚线 60 秒对地向量","${map.aisVisibleCount} targets in viewport · dashed 60 s ground vectors"),12,LocalMetro.current.fg,Modifier.align(Alignment.BottomStart).background(LocalMetro.current.bg).padding(8.dp))
                                    MapZoomControls(map,Modifier.align(Alignment.BottomEnd).padding(8.dp))
                                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).background(LocalMetro.current.bg)) {
                                        IconAction("locate",os.t("本船","own ship"),{own?.position?.let {map.fly(it.geo(),map.zoom)}})
                                    }
                                }
                            }
                        }
                        selected?.let(s::target)?.let {target->
                            Row(Modifier.fillMaxWidth().background(LocalMetro.current.panel).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable {go("target:${target.mmsi}")}.padding(vertical=9.dp)) {
                                    Label(target.displayName+" ›",20)
                                    Label(aisState(os,target)+" · "+(target.relative.distanceMeters?.let(os::formatDistance) ?: aisCpaReason(os,target.relative.state)),13,LocalMetro.current.muted)
                                }
                                IconAction("close",os.t("收起","close"),{selected=null})
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                            IconAction("data",os.t("目标","targets"),{listExpanded=!listExpanded},active=listExpanded)
                            IconAction("route",os.t("轨迹","tracks"),{showTracks=!showTracks},active=showTracks)
                            IconAction("connect",os.t("来源","inputs"),{go("sources")})
                        }
                        if(listExpanded)Box(Modifier.fillMaxWidth().weight(1f)){AisTargetList(os,s,selected,{selected=it;go("target:$it")})}
                    }
                }
                }
              }
            }
        }
    }
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

@Composable private fun AisTargetList(os:OsStore,s:TrafficSnapshot,selected:Int?,onSelect:(Int)->Unit) {
    var watchedOnly by rememberSaveable{mutableStateOf(false)}
    var query by rememberSaveable{mutableStateOf("")}
    var order by rememberSaveable{mutableStateOf(emptyList<Int>())}
    var sort by rememberSaveable{mutableStateOf("risk")}
    fun sorted()=when(sort){"distance"->s.targets.sortedBy {it.relative.distanceMeters ?: Double.MAX_VALUE};"name"->s.targets.sortedBy {it.displayName.lowercase()};else->s.targets.sortedWith(compareByDescending<AisTarget>{it.riskLevel.ordinal}.thenBy {it.relative.distanceMeters ?: Double.MAX_VALUE})}.map {it.mmsi}
    // 报文刷新只更新行内容。排序由用户明确触发，避免手指落下时目标换位。
    LaunchedEffect(s.targets.map {it.mmsi}.toSet()){order=order.filter {s.target(it)!=null}+sorted().filter {it !in order}}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Field(os.t("船名 / MMSI / 呼号","name / MMSI / call sign"),query,{query=it.take(80)})
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            listOf("risk" to os.t("关注优先","risk first"),"distance" to os.t("距离","distance"),"name" to os.t("名称","name")).forEach {(key,label)->
                Label(label,14,if(sort==key)LocalMetro.current.accent else LocalMetro.current.muted,Modifier.clickable {sort=key;order=sorted()}.padding(vertical=12.dp))
            }
            Label(os.t("重排","refresh order"),14,modifier=Modifier.clickable {order=sorted()}.padding(vertical=12.dp))
        }
        Toggle(os.t("仅显示关注目标","watched targets only"),watchedOnly){watchedOnly=it}
        val targets=order.mapNotNull(s::target).filter {(!watchedOnly||it.watched)&&(query.isBlank()||listOf(it.displayName,aisNumber(it.mmsi),it.staticData.callSign?.value.orEmpty()).any {text->text.contains(query,true)})}
        LazyColumn(Modifier.weight(1f)) {
            if(targets.isEmpty())item {Label(if(query.isBlank())aisInputSummary(os,s)else os.t("没有匹配目标","no matching targets"),16,LocalMetro.current.muted)}
            items(targets,key={it.mmsi}) {target->
                MenuRow((if(target.watched)"★ " else "")+target.displayName,aisState(os,target)+" · "+(target.relative.distanceMeters?.let(os::formatDistance) ?: aisKind(os,target.kind))) {onSelect(target.mmsi)}
            }
        }
    }
}

@Composable private fun AisTargetDetail(os:OsStore,s:TrafficSnapshot,mmsi:Int?,view:(AisView,Int)->Unit,viewTrack:(Int)->Unit,openInputs:()->Unit) {
    val target=mmsi?.let(s::target)
    var aliasEditing by rememberSaveable {mutableStateOf(false)}
    val now=rememberMarineClock()
    PageBody {
        if(target==null){Label(os.t("目标已不在当前缓存中；输入恢复后会按相同 MMSI 重新识别。","The target is no longer retained. It will be recognised by the same MMSI when reports return."),18);MenuRow(os.t("查看输入","inspect inputs")){openInputs()};return@PageBody}
        Label(aisNumber(target.mmsi)+" · "+aisKind(os,target.kind),17,LocalMetro.current.muted)
        Label(aisState(os,target),20,LocalMetro.current.accent)
        if(target.distress==AisDistressState.UNKNOWN&&target.kind in setOf(AisEntityKind.SART,AisEntityKind.MOB,AisEntityKind.EPIRB))Label(os.t("尚不能判断是激活还是自检，请核对报告。","Activation versus self-test is not yet known; inspect the report."),16)
        target.dynamic?.let {dynamic->
            Label(os.t("位置观测 · ","position observed · ")+(if(target.cached)os.t("历史缓存","historical cache")else readingAge(os,dynamic.receivedElapsed,now)),15,LocalMetro.current.muted)
            target.position?.let {Label(os.formatCoordinates(it.geo()),20)}
            AisFact(os,os.t("距离 / 真方位","distance / true bearing"),listOfNotNull(target.relative.distanceMeters?.let(os::formatDistance),target.relative.bearingDegrees?.let {os.formatBearing(it)+" T"}).joinToString(" · ").ifBlank {null})
            target.relative.relativeBearingDegrees?.let {AisFact(os,os.t("相对船艏方位","bearing relative to bow"),os.formatAngle(it))}
            AisFact(os,os.t("对地航速","speed over ground"),dynamic.sogMetersPerSecond?.let {(if("sog_lower_bound" in dynamic.invalidFields)"≥ "else "")+os.formatSpeed(it/.514444)})
            AisFact(os,os.t("对地航向","course over ground"),dynamic.cogDegrees?.let {os.formatBearing(it)+" T"})
            AisFact(os,os.t("船首向","heading"),dynamic.headingDegrees?.let {os.formatBearing(it)+" T"})
            dynamic.navigationStatus?.let {AisFact(os,os.t("广播航行状态","broadcast navigation status"),aisNavigationStatus(os,it))}
            Label(os.t("位置精度标志 · ","position accuracy flag · ")+if(dynamic.positionAccurate)os.t("高精度","high accuracy")else os.t("低精度 / 未声明","low / not declared"),14,LocalMetro.current.muted)
            dynamic.utcSecond?.let {Label(os.t("AIS 秒字段 $it（不是完整 UTC 时间）","AIS second field $it (not a complete UTC timestamp)"),13,LocalMetro.current.muted)}
            dynamic.sourceTimestampText?.let {Label(os.t("源时间标签 · ","source time tag · ")+it,13,LocalMetro.current.muted)}
            dynamic.invalidFields.forEach {Label(aisFieldReason(os,it),13,LocalMetro.current.muted)}
        }
        if(target.relative.approachTrend!=AisApproachTrend.UNKNOWN)AisFact(os,os.t("观测距离趋势","observed range trend"),when(target.relative.approachTrend){
            AisApproachTrend.APPROACHING->os.t("正在接近","approaching")
            AisApproachTrend.RECEDING->os.t("正在远离","receding")
            else->os.t("变化不显著","no significant change")
        })
        Label(os.t("最近会遇","closest approach"),27)
        Label(aisCpaReason(os,target.relative.state),16,LocalMetro.current.muted)
        target.relative.cpaMeters?.let {AisFact(os,"CPA",os.formatDistance(it))}
        target.relative.tcpaSeconds?.let {AisFact(os,"TCPA",os.t("${decimal(it/60,1)} 分钟","${decimal(it/60,1)} min"))}
        if(target.relative.extrapolationMillis>0)Label(os.t("位置对齐推算 ${target.relative.extrapolationMillis/1000} 秒","positions aligned by ${target.relative.extrapolationMillis/1000} seconds"),13,LocalMetro.current.muted)
        Label(os.t("恒速估计不是操船指令，也不保证未来距离。","A constant-velocity estimate is not a manoeuvring instruction or a guaranteed future distance."),14,LocalMetro.current.muted)
        s.events.filter {it.mmsi==target.mmsi&&it.active}.forEach {event->
            Label(aisRiskName(os,event.kind),21,LocalMetro.current.accent)
            if(event.reason=="risk_cannot_be_reconfirmed")Label(os.t("新数据不足，不能确认风险已经解除。","New data is insufficient to confirm that the risk has cleared."),15)
            if(event.kind==AisRiskKind.ANCHOR_PROXIMITY)event.distanceMeters?.let {meters->
                Label((if(s.preferences.anchorUsesAnchorPoint)os.t("锚点到目标 · ","anchor to target · ")else os.t("本船到目标 · ","own vessel to target · "))+os.formatDistance(meters),15)
            }
            if(event.acknowledged)Label(os.t("已确认 · 条件仍存在","acknowledged · condition remains"),14,LocalMetro.current.muted)
            else MetroButton(os.t("确认此提醒","acknowledge this alert"),{os.aisCommand(AisCommand.Acknowledge(event.id))})
            if(event.snoozedUntilElapsed>now)Label(os.t("稍后提醒 · 还剩 ${(event.snoozedUntilElapsed-now)/1000} 秒","snoozed · ${(event.snoozedUntilElapsed-now)/1000} seconds remaining"),14,LocalMetro.current.muted)
            MetroButton(os.t("五分钟后再提醒","snooze for five minutes"),{os.aisCommand(AisCommand.Snooze(event.id))})
        }
        Toggle(os.t("关注此目标","watch this target"),target.watched){os.aisCommand(AisCommand.Watch(target.mmsi,it))}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {AisView.entries.forEach {mode->
            MetroButton(when(mode){AisView.RADAR->os.t("雷达","radar");AisView.THREE_D->os.t("三维","3D");AisView.CHART->os.t("海图","chart")},{view(mode,target.mmsi)},Modifier.weight(1f))
        }}
        if(target.track.isNotEmpty())MenuRow(os.t("查看观测轨迹","view observed track"),os.t("${target.track.size} 个已接收位置 · 中断处不连线","${target.track.size} received positions · gaps stay disconnected")){viewTrack(target.mmsi)}
        MenuRow(os.t("我的备注名","my alias"),target.alias ?: os.t("只保存在本机","stored on this device")){aliasEditing=true}
        MenuRow(os.t("复制 MMSI","copy MMSI"),aisNumber(target.mmsi)) {
            (os.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MMSI",aisNumber(target.mmsi)))
        }
        Label(os.t("船舶广播资料","broadcast particulars"),27)
        Label(os.t("来自 AIS 广播，未经登记核验。每一项保留自己的来源时间。","AIS broadcast information, not registry verification. Each field keeps its own source and time."),14,LocalMetro.current.muted)
        val static=target.staticData
        AisStaticFact(os,s,os.t("名称","name"),static.name,now)
        AisStaticFact(os,s,os.t("呼号","call sign"),static.callSign,now)
        AisStaticFact(os,s,"IMO",static.imo,now)
        AisStaticFact(os,s,os.t("船型编码","ship type code"),static.shipType,now)
        AisStaticFact(os,s,os.t("广播目的地","broadcast destination"),static.destination,now)
        AisStaticFact(os,s,os.t("广播 ETA（与 TCPA 无关）","broadcast ETA (not TCPA)"),static.eta,now)
        static.draughtMeters?.let {AisFact(os,os.t("广播吃水","broadcast draught"),os.formatDepth(it.value))}
        static.dimensions?.let {value->val d=value.value;AisFact(os,os.t("广播长 × 宽","broadcast length × beam"),os.formatDistance(d.lengthMeters.toDouble())+" × "+os.formatDistance(d.beamMeters.toDouble()));Label(os.t("定位点至船艏/艉/左舷/右舷：","antenna to bow / stern / port / starboard: ")+listOf(d.toBowMeters,d.toSternMeters,d.toPortMeters,d.toStarboardMeters).joinToString(" / "){os.formatDistance(it.toDouble())},14,LocalMetro.current.muted)}
        AisStaticFact(os,s,os.t("母船 MMSI","mother ship MMSI"),static.motherShipMmsi,now)
        if(target.safetyMessages.isNotEmpty()) {
            Label(os.t("收到的安全文本","received safety text"),27)
            target.safetyMessages.forEach {message->Label(message.text,18);Label(readingAge(os,message.receivedElapsed,now),13,LocalMetro.current.muted)}
        }
        Label(os.t("数据来自哪里","where the data came from"),27)
        val sources=(listOfNotNull(target.dynamic?.source)+target.candidates.map {it.source}+target.recentMessages.map {it.source}).distinctBy {it.key}
        sources.forEach {source->
            val input=s.inputs.firstOrNull {it.connectionId==source.connectionId}
            MenuRow(input?.name ?: os.t("已移除的输入","removed input"),listOfNotNull(source.peer,source.formatter,source.channel.takeIf(String::isNotBlank)).joinToString(" · ")) {os.openLinked("nmea:connection:${Uri.encode(source.connectionId)}")}
        }
        if(target.state==AisTargetState.CONFLICT) {
            Label(os.t("相同 MMSI 的位置互相矛盾，未平均坐标。请检查网关、来源与设备身份。","Reports with this MMSI disagree; positions were not averaged. Inspect gateways and device identities."),16)
            target.candidates.forEach {report->AisFact(os,s.inputs.firstOrNull {it.connectionId==report.source.connectionId}?.name ?: report.source.connectionId,(report.position?.let {os.formatCoordinates(it.geo())} ?: os.t("未提供位置","position not provided"))+" · "+readingAge(os,report.receivedElapsed,now))}
        }
        var raw by rememberSaveable {mutableStateOf(false)}
        MenuRow(os.t("报文详情","message details"),os.t("最近 ${target.recentMessages.size} 条，未解释类型仍保留","${target.recentMessages.size} recent messages, including uninterpreted types")){raw=!raw}
        if(raw)target.recentMessages.takeLast(12).asReversed().forEach {message->Label("${message.type} · ${readingAge(os,message.receivedElapsed,now)}",14,LocalMetro.current.muted);Label(message.payload,13)}
    }
    if(aliasEditing&&target!=null)TextDialog(os,os.t("本机备注名","local alias"),target.alias.orEmpty(),{aliasEditing=false}) {name->os.aisCommand(AisCommand.Alias(target.mmsi,name.take(80).ifBlank {null})){aliasEditing=false}}
}

@Composable private fun AisFact(os:OsStore,label:String,value:String?) {Column(verticalArrangement=Arrangement.spacedBy(3.dp)){Label(label,14,LocalMetro.current.muted);Label(value?.takeIf {it.isNotBlank()} ?: os.t("未提供","not provided"),21)}}
@Composable private fun <T> AisStaticFact(os:OsStore,s:TrafficSnapshot,label:String,value:AisStaticValue<T>?,now:Long) {
    AisFact(os,label,value?.value?.toString())
    value?.let {Label((s.inputs.firstOrNull {input->input.connectionId==it.source.connectionId}?.name ?: os.t("历史来源","historical source"))+" · "+if(it.receivedElapsed>0)readingAge(os,it.receivedElapsed,now)else os.t("缓存资料","cached information"),12,LocalMetro.current.muted)}
}

@Composable private fun AisSources(os:OsStore,s:TrafficSnapshot) {PageBody {
    Label(aisInputSummary(os,s),21)
    Label(os.t("自动识别已启用的 NMEA 输入，不需要另建连接。VDM 是周围目标，VDO 是设备本船报告，不替换系统船位。","AIS is recognised on enabled NMEA inputs. VDM reports surrounding targets; VDO is an own-station report and never replaces system position."),16,LocalMetro.current.muted)
    s.inputs.forEach {input->
        MenuRow(input.name,when(input.state){AisInputState.DISABLED->os.t("已关闭","off");AisInputState.CONNECTING->os.t("正在连接","connecting");AisInputState.ONLINE->os.t("传输在线","transport online");else->os.t("传输中断","transport interrupted")}){os.openLinked("nmea:connection:${Uri.encode(input.connectionId)}")}
        Label(os.t("${input.legalAisMessages} 条合法 AIS · ${input.dynamicTargets} 个动态目标","${input.legalAisMessages} valid AIS · ${input.dynamicTargets} dynamic targets"),16)
        Label(os.t("最近字节：","last bytes: ")+(input.lastByteElapsed?.let {readingAge(os,it,s.generatedElapsed)} ?: os.t("未收到","none received"))+" · "+os.t("合法 NMEA：","valid NMEA: ")+(input.lastNmeaElapsed?.let {readingAge(os,it,s.generatedElapsed)} ?: os.t("未收到","none received")),13,LocalMetro.current.muted)
        input.lastAisElapsed?.let {Label(os.t("AIS 最近接收 · ","AIS last received · ")+readingAge(os,it,s.generatedElapsed),14,LocalMetro.current.muted)}
        if(input.rejectedMessages>0)Label(os.t("已丢弃 ${input.rejectedMessages} 条损坏或歧义报文","${input.rejectedMessages} damaged or ambiguous messages discarded"),14,LocalMetro.current.muted)
        input.lastError?.let {Label(aisInputError(os,it),13,LocalMetro.current.muted)}
    }
    MenuRow(os.t("管理船上连接","manage boat connections")){os.openLinked("nmea")}
    if(s.ownReports.isNotEmpty()) {
        Label(os.t("设备本船报告","own-station reports"),27)
        if(s.ownIdentityConflict)Label(os.t("多台设备的 VDO 身份不一致，请指定本船 MMSI。","VDO identities disagree. Set the own-ship MMSI."),17,LocalMetro.current.accent)
        s.ownReports.forEach {Label(aisNumber(it.mmsi)+" · "+(s.inputs.firstOrNull {input->input.connectionId==it.source.connectionId}?.name ?: it.source.connectionId),16)}
        Label(os.t("收到 VDO 不代表设备已通过无线电成功发射。","Receiving VDO is not confirmation of successful radio transmission."),14,LocalMetro.current.muted)
    }
}}

@Composable private fun AisSettings(os:OsStore,s:TrafficSnapshot) {
    val p=s.preferences
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    var cpa by rememberSaveable(p.cpaDistanceMeters){mutableStateOf(decimal(p.cpaDistanceMeters,0))}
    var minutes by rememberSaveable(p.cpaTimeSeconds){mutableStateOf(decimal(p.cpaTimeSeconds/60,0))}
    var near by rememberSaveable(p.proximityMeters){mutableStateOf(decimal(p.proximityMeters,0))}
    var anchor by rememberSaveable(p.anchorProximityMeters){mutableStateOf(decimal(p.anchorProximityMeters,0))}
    var ownMmsi by rememberSaveable(p.ownMmsi){mutableStateOf(p.ownMmsi?.let(::aisNumber).orEmpty())}
    var saved by remember {mutableStateOf(false)}
    fun permissions(enable:Boolean){if(enable&&Build.VERSION.SDK_INT>=33)request.launch(Manifest.permission.POST_NOTIFICATIONS)}
    PageBody {
        Label(os.t("全船共用的交通规则","traffic rules shared across the boat"),24)
        Label(os.t("AIS 无法发现所有船。没有目标不代表周围安全；这些阈值是可调整初值，不是操船建议。","AIS does not detect every vessel. No targets does not mean clear surroundings. Limits are adjustable defaults, not manoeuvring advice."),16,LocalMetro.current.muted)
        Toggle(os.t("近距会遇提醒","closest-approach alerts"),p.cpaEnabled,os.t("CPA ${os.formatDistance(p.cpaDistanceMeters)} / ${decimal(p.cpaTimeSeconds/60,0)} 分钟","CPA ${os.formatDistance(p.cpaDistanceMeters)} / ${decimal(p.cpaTimeSeconds/60,0)} min")){enable->os.aisPreferences {it.copy(cpaEnabled=enable)};permissions(enable)}
        Field(os.t("CPA 距离（米）","CPA distance (metres)"),cpa,{cpa=it;saved=false},number=true)
        Field(os.t("未来时间窗口（分钟）","future window (minutes)"),minutes,{minutes=it;saved=false},number=true)
        Toggle(os.t("当前近距离提醒","current-distance alerts"),p.proximityEnabled,os.formatDistance(p.proximityMeters)){enable->os.aisPreferences {it.copy(proximityEnabled=enable)};permissions(enable)}
        Field(os.t("近距离范围（米）","proximity distance (metres)"),near,{near=it;saved=false},number=true)
        Toggle(os.t("锚泊交通警戒","anchor traffic watch"),p.anchorProximityEnabled,os.t("独立于走锚报警，仅在本船锚泊时使用。","Independent from drag alarms; used while your vessel is anchored.")){enable->os.aisPreferences {it.copy(anchorProximityEnabled=enable)};permissions(enable)}
        Field(os.t("锚泊交通范围（米）","anchor traffic distance (metres)"),anchor,{anchor=it;saved=false},number=true)
        ChoiceRow(os.t("相对当前船位","relative to current own position"),!p.anchorUsesAnchorPoint){os.aisPreferences {it.copy(anchorUsesAnchorPoint=false)}}
        ChoiceRow(os.t("锚点区域警戒","anchor-centred area watch"),p.anchorUsesAnchorPoint,os.t("显示锚点到目标的距离，不称为船间距离。","Distance is measured from the anchor, not between vessels.")){os.aisPreferences {it.copy(anchorUsesAnchorPoint=true)}}
        Toggle(os.t("关注目标丢失提醒","alert when a target of concern is lost"),p.lostTargetAlerts){value->os.aisPreferences {it.copy(lostTargetAlerts=value)}}
        Toggle(os.t("提醒声音","alert sound"),p.soundEnabled){value->os.aisPreferences {it.copy(soundEnabled=value)};permissions(value)}
        Field(os.t("本船 MMSI（可留空）","own MMSI (optional)"),ownMmsi,{ownMmsi=it.filter(Char::isDigit).take(9);saved=false},number=true)
        val valid=listOf(cpa,near,anchor).all {it.toDoubleOrNull()?.let {v->v.isFinite()&&v in 10.0..92600.0}==true}&&minutes.toDoubleOrNull()?.let {it.isFinite()&&it in 1.0..120.0}==true&&(ownMmsi.isBlank()||ownMmsi.length==9&&ownMmsi.toIntOrNull()?.let {it>0}==true)
        MetroButton(os.t("保存阈值与本船身份","save limits and own identity"),{
            val distance=cpa.toDouble();val window=minutes.toDouble()*60;val proximity=near.toDouble();val anchoring=anchor.toDouble();val identity=ownMmsi.toIntOrNull()
            os.aisPreferences(onSaved={saved=true}) {it.copy(cpaDistanceMeters=distance,cpaTimeSeconds=window,proximityMeters=proximity,anchorProximityMeters=anchoring,ownMmsi=identity)}
        },primary=true,enabled=valid)
        if(saved)Label(os.t("已保存","saved"),16,LocalMetro.current.accent)
        AisLayerChoice(os,false);AisLayerChoice(os,true)
        Label(os.t("运行状态","runtime status"),27)
        Label(if(!p.monitoringEnabled)os.t("交通提醒未启用","traffic alerts are off")else if(s.runtime.foregroundActive)os.t("后台交通服务正在运行","background traffic service is running")else os.t("后台运行尚未确认","background execution is not confirmed"),17)
        if(p.monitoringEnabled&&!s.runtime.notificationsAllowed)Label(os.t("系统通知受限，无法保证后台提醒可见。","System notifications are restricted; background alerts may not be visible."),16)
        if(p.soundEnabled&&!s.runtime.soundAllowed)Label(os.t("系统声音设置受限，请检查通知渠道与勿扰模式。","Sound is restricted; check the notification channel and Do Not Disturb."),16)
        if(s.runtime.backgroundRestricted)Label(os.t("Android 限制后台运行，请调整电池设置。","Android restricts background execution; review battery settings."),16)
        if(s.runtime.inputQueueDrops>0)Label(os.t("输入过载，已丢弃 ${s.runtime.inputQueueDrops} 个帧；交通图景可能不完整。","Input overload dropped ${s.runtime.inputQueueDrops} frames; the traffic picture may be incomplete."),16)
        s.runtime.persistenceError?.let {Label(os.t("设置存储失败，请重试。","Settings storage failed; retry."),16)}
        s.runtime.foregroundError?.let {Label(os.t("后台服务未能启动，请保持应用可见并检查系统设置。","The background service could not start. Keep the app visible and check system settings."),16)}
        MenuRow(os.t("Android 通知设置","Android notification settings")){runCatching {os.context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,os.context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {os.notify("无法打开系统通知设置","Could not open system notification settings",app=AppId.AIS)}}
        Label(os.t("强行停止应用或关闭输入连接会中断监控。关闭地图图层不会停止已启用的提醒。","Force-stopping the app or closing input connections interrupts monitoring. Hiding a map layer does not stop enabled alerts."),14,LocalMetro.current.muted)
    }
}

private fun aisNavigationStatus(os:OsStore,value:Int)=when(value){0->os.t("机动航行","under way using engine");1->os.t("锚泊","at anchor");2->os.t("失去控制","not under command");3->os.t("操纵受限","restricted manoeuvrability");4->os.t("吃水受限","constrained by draught");5->os.t("系泊","moored");6->os.t("搁浅","aground");7->os.t("从事捕鱼","engaged in fishing");8->os.t("帆航","under way sailing");14->os.t("设备报告状态 14","device report status 14");else->os.t("未声明（$value）","not declared ($value)")}

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

private fun aisFieldReason(os:OsStore,field:String):String=when(field) {
    "position"->os.t("此报文未提供有效位置","this report does not provide a valid position")
    "sog"->os.t("未提供对地航速","speed over ground not provided")
    "cog"->os.t("未提供对地航向","course over ground not provided")
    "heading"->os.t("未提供船首向，不以 COG 代替","heading not provided; COG is not used as heading")
    "sog_lower_bound"->os.t("航速达到编码上限，仅知道下界，不用于预测","speed is at the encoding limit; a lower bound, excluded from prediction")
    "manual_position"->os.t("设备报告手工输入的位置","device reports a manually entered position")
    "estimated_position"->os.t("设备报告推算位置","device reports an estimated position")
    "position_system_inoperative"->os.t("设备报告定位系统失效","device reports an inoperative positioning system")
    "rot_direction_only"->os.t("仅提供转向方向，没有可用转向速率","turn direction only; no usable rate of turn")
    "position_latency_over_five_seconds"->os.t("设备声明位置延迟超过五秒","device declares position latency over five seconds")
    else->os.t("报文含未能采用的字段","report contains a field that cannot be used")
}

private fun aisInputError(os:OsStore,error:String):String=when {
    error.contains("checksum")->os.t("丢弃损坏报文：完整性校验不符","damaged report discarded: integrity check failed")
    error.contains("fragment")||error.contains("group")->os.t("丢弃无法可靠拼接的分片组","fragment group could not be assembled reliably")
    error.contains("length")||error.contains("fields")||error.contains("bounds")->os.t("丢弃长度或字段不合规的报文","report discarded: invalid length or fields")
    error.contains("characters")||error.contains("padding")->os.t("丢弃编码不合规的报文","report discarded: invalid encoding")
    error.contains("identity")->os.t("丢弃身份字段不合规的报文","report discarded: invalid identity")
    else->os.t("输入诊断：","input diagnostic: ")+error.take(160)
}

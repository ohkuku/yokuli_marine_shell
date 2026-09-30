package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.anchorwatch.api.DisplayDemandService
import com.yokuli.marine.core.design.MarineUnitFormats
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.shell.rebuild.scene.navigation.*
import com.yokuli.shell.contract.MarineUnitPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlin.math.*

/** 海图应用内的真实资料场景；只持有显示状态，不另建导航或船位来源。 */
@Composable
fun NavigationSpatialView(
    snapshot: NavigationSpatialSnapshot,
    display: DisplayDemandService,
    units: MarineUnitPreferences,
    chinese: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    onOpenMap: () -> Unit,
    onOpenTarget: (String) -> Unit,
    onAutomaticSwitchInhibited: (Boolean) -> Unit = {},
    onOpenSources: () -> Unit,
    chartScene: NavigationChartScene? = null,
    terrainLoading: Boolean = false,
    terrainError: String? = null,
    viewOrigin: com.yokuli.marine.shell.rebuild.GeoPoint? = null,
    route: List<com.yokuli.marine.shell.rebuild.GeoPoint> = emptyList(),
    night: Boolean = false,
    vesselLengthMeters: Double = 12.0,
    onRetryTerrain: () -> Unit = {},
    onOpenChartPoint: (com.yokuli.marine.shell.rebuild.GeoPoint) -> Unit = {},
) {
    fun tr(zh:String,en:String)=if(chinese)zh else en
    val c=LocalMetro.current
    val resumed=rememberNavigationResumed()
    val enabled=active&&resumed&&LocalInternalAppInputEnabled.current
    val context=LocalContext.current
    val nativeFontScale=LocalWpTextScale.current*context.resources.displayMetrics.scaledDensity/context.resources.displayMetrics.density
    val formats=remember(units){MarineUnitFormats(units)}
    var raw by remember { mutableStateOf(display.deviceViewOrientation.value) }
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var free by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(NavigationChartMode.FOLLOW) }
    var details by rememberSaveable { mutableStateOf(false) }
    var inspectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var renderGeneration by remember { mutableIntStateOf(0) }
    var surface by remember { mutableStateOf<NavigationSpatialSurface?>(null) }
    var shownFrame by remember { mutableStateOf<SpatialPresentedFrame?>(null) }
    val inhibitCallback=rememberUpdatedState(onAutomaticSwitchInhibited)
    LaunchedEffect(free,details,inspectedId){inhibitCallback.value(free||details||inspectedId!=null)}
    AppBackHandler(enabled&&(details||inspectedId!=null)){if(inspectedId!=null)inspectedId=null else details=false}
    DisposableEffect(Unit){onDispose{inhibitCallback.value(false)}}
    DisposableEffect(display,enabled,snapshot.mountMode,free,details,inspectedId){
        val lease=if(enabled&&!details&&inspectedId==null&&snapshot.mountMode==SpatialMountMode.HANDHELD&&!free)display.acquireDeviceViewOrientation()else null
        onDispose{lease?.close()}
    }
    LaunchedEffect(display,enabled,surface){if(enabled)display.deviceViewOrientation.collect {surface?.orientation(it)}}
    LaunchedEffect(display,enabled){if(enabled)while(isActive){raw=display.deviceViewOrientation.value;elapsed=SystemClock.elapsedRealtime();shownFrame=surface?.frame();delay(250)}}
    val conversion=remember(snapshot.position){SpatialNorthConversion.from(snapshot.position,System.currentTimeMillis())}
    val rotation=LocalView.current.display?.rotation?:android.view.Surface.ROTATION_0
    val camera=resolveSpatialCamera(snapshot,raw,raw.deviceToMagneticWorld,rotation,conversion,elapsed,
        if(free)shownFrame?.camera?.trueBearing?:0.0 else null,-28.0)
    val target=snapshot.current
    val inspected=listOfNotNull(snapshot.current,snapshot.next,snapshot.steering).firstOrNull{it.id==inspectedId}
    val marker=chartScene?.markers?.firstOrNull {it.id==inspectedId}
    LaunchedEffect(inspectedId,inspected,marker){if(inspectedId!=null&&inspected==null&&marker==null)inspectedId=null}
    val reason=when(camera.issue){
        "sensor"->tr("无手机方向 · 可拖动观察", "No phone orientation · drag to explore")
        "stale"->tr("方向等待更新", "Waiting for orientation")
        "magnetic"->tr("罗盘受干扰 · 真北参考", "Compass interference · north reference")
        "north"->tr("方向换算等待位置", "Position needed for true north")
        "mount"->tr("固定位置需要重新确认", "Confirm the phone mount")
        "heading"->tr("船首向等待更新", "Waiting for heading")
        else->null
    }
    Column(modifier.clipToBounds().background(c.bg)) {
        Row(Modifier.fillMaxWidth().heightIn(min=42.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            NavigationChartMode.entries.forEach {value->
                val title=when(value){NavigationChartMode.FOLLOW->tr("随船","Follow");NavigationChartMode.OVERVIEW->tr("概览","Overview");NavigationChartMode.SEABED->tr("海底","Seabed")}
                Column(Modifier.weight(1f).clickable(enabled=enabled,role=Role.Tab){mode=value;free=false}.padding(horizontal=8.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Label(title,14,if(mode==value)c.fg else c.muted)
                    Spacer(Modifier.padding(top=5.dp).height(2.dp).fillMaxWidth(.6f).background(if(mode==value)c.fg else Color.Transparent))
                }
            }
            SpatialTextAction(tr("资料","Info"),enabled){details=true}
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()){
            if(failed)Column(Modifier.align(Alignment.Center).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Label(tr("三维显示暂时中断","3D view interrupted"),18)
                Label(tr("海图和导航仍然保留。可以重新载入画面。","Your chart and navigation remain available. Reload this view to continue."),13,c.muted)
                SpatialTextAction(tr("重新载入","Reload"),enabled){failed=false;shownFrame=null;renderGeneration++}
                SpatialTextAction(tr("回到地图","Back to map"),enabled,onOpenMap)
            }else key(renderGeneration){
                val labels=listOfNotNull(snapshot.current,snapshot.next,snapshot.steering).associate{it.id to formats.distance(it.distanceMeters)}
                val input=SpatialRenderInput(snapshot,raw,conversion,rotation,
                    if(free)shownFrame?.camera?.trueBearing?:0.0 else null,chinese=chinese,distanceLabels=labels,fontScale=nativeFontScale,
                    chartScene=chartScene,mode=mode,route=route,night=night,vesselLengthMeters=vesselLengthMeters,viewOrigin=viewOrigin)
                AndroidView(factory={ctx->NavigationSpatialSurface(ctx).also{surface=it}},modifier=Modifier.fillMaxSize(),update={view->
                    view.inputEnabled=enabled&&!details&&inspectedId==null
                    view.onFailure={failed=true}
                    view.onFreeChanged={free=it}
                    view.onTarget={if(enabled)inspectedId=it}
                    view.contentDescription=tr("三维海图。拖动转向，双指缩放，点击物标查看资料。","3D chart. Drag to orbit, pinch to zoom, and tap an object for details.")
                    view.update(input,enabled&&!details&&inspectedId==null)
                },onRelease={view->view.close();if(surface===view)surface=null})
            }
            if(!failed&&!details&&inspectedId==null){
                Column(Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max=250.dp).background(c.bg.copy(alpha=.86f)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    if(target!=null){Label(target.name,15,maxLines=1);Label(formats.distance(target.distanceMeters),13,c.muted)}
                    else Label(tr("自由观察海图","Explore your chart"),14)
                    if(reason!=null)Label(reason,11,c.muted,maxLines=2)
                    if(!snapshot.live&&snapshot.position!=null)Label(snapshot.issueText?:tr("使用上次船位","Using the last position"),11,c.muted,maxLines=2)
                    if(terrainLoading)MetroProgress(tr("载入附近资料","Loading nearby data"))
                    else if(terrainError!=null)SpatialTextAction(tr("资料未载入 · 重试","Data unavailable · retry"),enabled,onRetryTerrain)
                    else if(chartScene?.hasGeometry!=true)Label(tr("此范围没有可显示的资料","No chart geometry in this area"),11,c.muted,maxLines=2)
                    else if(NavigationChartWarning.DEPTH_INTERVALS in chartScene.warnings&&mode==NavigationChartMode.SEABED)Label(tr("按原始深度区间呈现","Showing source depth intervals"),11,c.muted)
                    else if(chartScene.warnings.any {it in setOf(NavigationChartWarning.MODEL_BUDGET,NavigationChartWarning.PARTIAL_CONTENT,NavigationChartWarning.RASTER_RESOLUTION_LIMIT)})Label(tr("部分细节未展开 · 查看资料","Some detail is limited · see Info"),11,c.muted)
                }
                if(shownFrame==null)MetroProgress(tr("展开三维海图","Opening 3D chart"),Modifier.align(Alignment.Center).padding(24.dp))
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp).background(c.bg.copy(alpha=.82f)).padding(horizontal=8.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    snapshot.vesselHeading?.let{Label(tr("艏向 ","HDG ")+"${it.trueDegrees.roundToInt()}°",12)}
                    snapshot.vesselHeelDegrees?.takeIf(Double::isFinite)?.let{Label(tr("横倾 ","Heel ")+"${it.roundToInt()}°",12,c.muted)}
                    snapshot.vesselPitchDegrees?.takeIf(Double::isFinite)?.let{Label(tr("纵倾 ","Pitch ")+"${it.roundToInt()}°",12,c.muted)}
                }
                Column(Modifier.align(Alignment.BottomEnd).padding(10.dp).background(c.bg.copy(alpha=.86f))){
                    SpatialTextAction("+",enabled){surface?.zoomBy(1.4)}
                    SpatialTextAction("−",enabled){surface?.zoomBy(1/1.4)}
                }
            }
            if((inspected!=null||marker!=null)&&!details)Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Label(marker?.title?:inspected?.name.orEmpty(),20,maxLines=2)
                if(marker!=null){
                    if(marker.depthLowerMeters!=null)Label(listOfNotNull(marker.depthLowerMeters,marker.depthUpperMeters?.takeIf{it!=marker.depthLowerMeters}).joinToString(" – "){formats.depth(it)},14)
                    chartScene?.sources?.firstOrNull {it.id==marker.sourceId}?.let{Label(it.name,12,c.muted,maxLines=2)}
                    if(marker.symbolic)Label(tr("设施为海图符号，不代表实际外形尺寸","Chart symbol; shape and size are illustrative"),12,c.muted)
                }else inspected?.let{Label(listOfNotNull(it.distanceMeters?.let(formats::distance),it.bearingTrueDegrees?.let{"${it.roundToInt()}° T"}).joinToString(" · "),14)}
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    SpatialTextAction(tr("地图查看","View on map"),enabled){
                        inspectedId=null
                        if(marker!=null)onOpenChartPoint(marker.point)else if(inspected?.steering==true)onOpenMap()else inspected?.let{onOpenTarget(it.id)}
                    }
                    SpatialTextAction(tr("关闭","Close"),enabled){inspectedId=null}
                }
            }
            if(details)Column(Modifier.fillMaxSize().background(c.bg.copy(alpha=.97f)).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Label(tr("此画面的资料","Scene data"),20)
                Label(tr("当前图包里的真实地形、测深和物标。海面只是视角参照，不表示可航行水域。","Terrain, depths and objects come from your selected package. The water surface is a visual reference, not evidence of safe water."),13,c.muted)
                chartScene?.sources.orEmpty().forEach{source->
                    Label(source.name,15)
                    Label(listOfNotNull(when(source.kind){NavigationChartSourceKind.ELEVATION_GRID->tr("高程网格","Elevation grid");NavigationChartSourceKind.DEPTH_INTERVALS->tr("深度区间","Depth intervals");NavigationChartSourceKind.CHART_OBJECTS->tr("海图物标","Chart objects")},source.resolutionMeters?.let{tr("分辨率 ","Resolution ")+formats.length(it)},source.verticalReference).joinToString(" · "),12,c.muted)
                }
                chartScene?.warnings.orEmpty().forEach{warning->Label(terrainWarning(warning,chinese),12,c.muted)}
                if(terrainError!=null){Label(terrainError,13,c.muted);SpatialTextAction(tr("重新读取资料","Retry data"),enabled,onRetryTerrain)}
                if(chartScene?.sources.isNullOrEmpty()&&!terrainLoading)Label(tr("请在地图选择带有数据的图包。没有资料的地方不生成地形。","Select a package with data on the map. Missing data is left empty."),13,c.muted)
                Label(tr("方向依据","Orientation"),17)
                Label(if(snapshot.mountMode==SpatialMountMode.VESSEL_MOUNTED)tr("跟随已校准的船首向与船体姿态；COG 箭带单独显示实际运动方向。","Uses calibrated vessel heading and attitude. The separate COG ribbon shows movement.")else tr("手持方向只改变观察角度。船模仍使用数据中心选用的船首向；拖动可自由观察。","Phone orientation only changes the camera. The vessel uses heading from Data Center. Drag to explore."),13,c.muted)
                snapshot.vesselHeading?.let{Label(tr("艏向 · ","Heading · ")+it.source,12,c.muted)}
                SpatialTextAction(tr("方向来源","Direction sources"),enabled,onOpenSources)
                SpatialTextAction(tr("返回海图","Back to view"),enabled){details=false}
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            SpatialTextAction(tr("地图","Map"),enabled,onOpenMap)
            SpatialTextAction("−30°",enabled&&!failed){surface?.turnBy(-30.0)}
            SpatialTextAction(if(free)tr("跟随","Follow")else tr("复位","Reset"),enabled&&!failed){surface?.resetView();free=false}
            SpatialTextAction("+30°",enabled&&!failed){surface?.turnBy(30.0)}
            if(target!=null)SpatialTextAction(tr("目标","Target"),enabled){inspectedId=target.id}
        }
    }
}

private fun terrainWarning(warning:NavigationChartWarning,chinese:Boolean):String {
    fun tr(zh:String,en:String)=if(chinese)zh else en
    return when(warning){
        NavigationChartWarning.NO_DATA->tr("当前位置没有地形资料","No terrain data at this position")
        NavigationChartWarning.PARTIAL_CONTENT->tr("部分源资料未能展开，未显示处保留空白","Some source content could not be shown and remains empty")
        NavigationChartWarning.GEOMETRY_UNCERTAIN->tr("有不确定几何，未作为可靠地形显示","Uncertain geometry is not shown as reliable terrain")
        NavigationChartWarning.RASTER_RESOLUTION_LIMIT->tr("网格细节受原始分辨率和显示预算限制","Grid detail is limited by source resolution and display budget")
        NavigationChartWarning.DEPTH_INTERVALS->tr("深度区间以阶地呈现，不是连续实测海底","Depth intervals appear as terraces, not a continuous surveyed seabed")
        NavigationChartWarning.LAND_HEIGHT_UNKNOWN->tr("陆地仅有海岸轮廓，没有真实陆地高程","Land is shown as its coastline only; land elevations are unavailable")
        NavigationChartWarning.MODEL_BUDGET->tr("此范围物标较多，部分细节未展开","This area has many objects; some detail is not expanded")
        NavigationChartWarning.VERTICAL_DATUM_MIXED->tr("资料的高程基准不同，不能直接比较高度","Sources use different vertical references; heights are not directly comparable")
    }
}

@Composable
private fun SpatialTextAction(text:String,enabled:Boolean,onClick:()->Unit){
    Box(Modifier.heightIn(min=48.dp).clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=8.dp,vertical=12.dp),contentAlignment=Alignment.Center){Label(text,14,if(enabled)LocalMetro.current.accentText else LocalMetro.current.disabled)}
}

@Composable
internal fun rememberNavigationResumed():Boolean{
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    return resumed
}

/** 由海图偏好所有者持久化；默认只提示，启用后才允许抬起自动切换。 */
@Composable
fun NavigationLiftPreference(enabled:Boolean,chinese:Boolean,onChange:(Boolean)->Unit){
    val zh=chinese
    Toggle(if(zh)"抬起手机看三维海图"else"Raise phone for 3D chart",enabled,
        if(zh)"抬起时进入三维，放下时回到地图。手动返回地图后暂停自动切换。"else"Lift for 3D, lower for the map. Returning to the map yourself pauses automatic switching.",
        enabled=LocalInternalAppInputEnabled.current,onChange=onChange)
}

/**
 * 姿态手势只控制显示，绝不修改安装身份。进入/退出角度和稳定时间不同；
 * 编辑、拖图、面板、通知、自由查看期间由inhibited暂停计时，不累积隐藏触发。
 */
@Composable
fun NavigationLiftObserver(
    display:DisplayDemandService,
    active:Boolean,
    inhibited:Boolean,
    spatialVisible:Boolean,
    autoEnabled:Boolean,
    mounted:Boolean=false,
    manuallyReturnedToMapEpoch:Long=0,
    onOfferSpatial:()->Unit,
    onRequestSpatial:()->Unit,
    onRequestMap:()->Unit,
){
    val resumed=rememberNavigationResumed()
    val allowed=active&&resumed&&!mounted&&!inhibited&&LocalInternalAppInputEnabled.current
    val currentInhibited=rememberUpdatedState(inhibited)
    val currentSpatial=rememberUpdatedState(spatialVisible)
    val currentAuto=rememberUpdatedState(autoEnabled)
    val offer=rememberUpdatedState(onOfferSpatial);val enter=rememberUpdatedState(onRequestSpatial);val leave=rememberUpdatedState(onRequestMap)
    var suppressed by remember{mutableStateOf(false)}
    var autoOpened by remember{mutableStateOf(false)}
    var manualAt by remember{mutableLongStateOf(0)}
    LaunchedEffect(manuallyReturnedToMapEpoch){if(manuallyReturnedToMapEpoch>0){suppressed=true;manualAt=SystemClock.elapsedRealtime();autoOpened=false}}
    DisposableEffect(display,allowed){val lease=if(allowed)display.acquireDeviceViewOrientation()else null;onDispose{lease?.close()}}
    LaunchedEffect(display,allowed){
        if(!allowed)return@LaunchedEffect
        var enteredAt=0L;var loweredAt=0L;var offered=false
        while(isActive){
            val sample=display.deviceViewOrientation.value;val now=SystemClock.elapsedRealtime()
            val q=sample.deviceToMagneticWorld
            if(currentInhibited.value||q==null||sample.accuracy==android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE||sample.elapsedRealtimeMillis?.let{now-it !in 0..2_000L}!=false){enteredAt=0;loweredAt=0;delay(120);continue}
            val normal=q.rotate(0.0,0.0,-1.0)
            val lift=Math.toDegrees(acos(abs(normal.z).coerceIn(0.0,1.0)))
            when{
                lift>=58.0->{
                    loweredAt=0
                    if(enteredAt==0L)enteredAt=now
                    if(!currentSpatial.value&&!suppressed&&!offered&&now-enteredAt>=900){
                        offered=true
                        if(currentAuto.value){autoOpened=true;enter.value()}else offer.value()
                    }
                }
                lift<=28.0->{
                    enteredAt=0
                    if(loweredAt==0L)loweredAt=now
                    if(now-loweredAt>=1400){
                        offered=false
                        if(suppressed&&now-manualAt>=15_000)suppressed=false
                        if(currentSpatial.value&&autoOpened&&currentAuto.value){autoOpened=false;leave.value()}
                    }
                }
                else->{enteredAt=0;loweredAt=0}
            }
            delay(120)
        }
    }
}

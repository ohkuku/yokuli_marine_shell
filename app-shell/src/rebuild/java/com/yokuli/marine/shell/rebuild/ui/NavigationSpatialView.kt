package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.anchorwatch.api.DisplayDemandService
import com.yokuli.anchorwatch.location.vessel.DeviceViewOrientationSample
import com.yokuli.marine.core.design.MarineUnitFormats
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.shell.rebuild.scene.navigation.*
import com.yokuli.shell.contract.MarineUnitPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlin.math.*

/** 海图应用内的另一观察画面；不另开App、不另建导航会话。 */
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
    var planar by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var inspectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf<NavigationSpatialSurface?>(null) }
    var shownFrame by remember { mutableStateOf<SpatialPresentedFrame?>(null) }
    val inhibitCallback=rememberUpdatedState(onAutomaticSwitchInhibited)
    LaunchedEffect(free,details,inspectedId){inhibitCallback.value(free||details||inspectedId!=null)}
    AppBackHandler(enabled&&(details||inspectedId!=null)){if(inspectedId!=null)inspectedId=null else details=false}
    DisposableEffect(Unit){onDispose{inhibitCallback.value(false)}}
    // 可见手持画面申请独立只读租约；退后台、通知遮挡、自由查看均不维持传感器。
    DisposableEffect(display,enabled,snapshot.mountMode,free){
        val lease=if(enabled&&snapshot.mountMode==SpatialMountMode.HANDHELD&&!free)display.acquireDeviceViewOrientation()else null
        onDispose{lease?.close()}
    }
    LaunchedEffect(display,enabled,surface){
        if(enabled) display.deviceViewOrientation.collect { sample -> surface?.orientation(sample) }
    }
    LaunchedEffect(display,enabled){
        if(enabled)while(isActive){raw=display.deviceViewOrientation.value;elapsed=SystemClock.elapsedRealtime();shownFrame=surface?.frame();delay(250)}
    }
    val conversion=remember(snapshot.position){SpatialNorthConversion.from(snapshot.position,System.currentTimeMillis())}
    val rotation=LocalView.current.display?.rotation?:android.view.Surface.ROTATION_0
    val camera=resolveSpatialCamera(snapshot,raw,raw.deviceToMagneticWorld,rotation,conversion,elapsed,
        if(free)shownFrame?.camera?.trueBearing?:snapshot.current?.bearingTrueDegrees?:0.0 else null,-8.0)
    val target=snapshot.current
    val fallback=planar||failed||camera.issue!=null||target==null||target.nearTarget
    LaunchedEffect(surface,enabled,fallback){
        val view=surface
        if(view!=null&&enabled&&!fallback){delay(4_000);if(view===surface&&view.frame()==null)failed=true}
    }
    val inspected=listOfNotNull(snapshot.current,snapshot.next,snapshot.steering).firstOrNull{it.id==inspectedId}
    LaunchedEffect(inspectedId,inspected){if(inspectedId!=null&&inspected==null)inspectedId=null}
    val distance=target?.distanceMeters?.let(formats::distance).orEmpty()
    val delta=shownFrame?.targetDelta?.takeIf{!fallback&&!free&&snapshot.live}
    val reason=when(camera.issue){
        "sensor"->tr("手机没有可用的方向传感器", "This phone has no usable orientation sensor")
        "stale"->tr("等待新的手机姿态", "Waiting for a phone orientation update")
        "magnetic"->tr("罗盘受干扰，方向暂以真北显示", "Compass interference · directions use true north")
        "north"->tr("需要近期位置才能将罗盘换算为真北", "A recent position is needed to align the compass with true north")
        "vertical"->tr("抬起手机，朝向你想看的方向", "Raise your phone toward the direction you want to see")
        "heading"->tr("等待船首向；对地航向不代替船首向", "Waiting for vessel heading; course does not replace heading")
        else->null
    }
    Column(modifier.clipToBounds().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Label(target?.name?:tr("还没有目的地","No destination yet"),18,maxLines=1)
                Label(listOfNotNull(distance.takeIf{it.isNotBlank()},when{
                    target?.nearTarget==true->tr("已在目标附近","Near your destination")
                    delta!=null->(if(snapshot.steering!=null)tr("沿线 · ","Route · ")else tr("目标 · ","Target · "))+if(abs(delta)<3)tr("前方","Ahead")else tr("${if(delta<0)"向左"else"向右"} ${abs(delta).roundToInt()}°","${if(delta<0)"Left"else"Right"} ${abs(delta).roundToInt()}°")
                    free->tr("自由查看 · 不跟随手机","Free view · not following your phone")
                    snapshot.mountMode==SpatialMountMode.VESSEL_MOUNTED->tr("随船艏观察","Following vessel heading")
                    else->tr("手机视线 · 目标以船位计算","Phone view · bearings from your vessel")
                }).joinToString(" · "),12,c.muted,maxLines=2)
            }
            SpatialTextAction(tr("依据","Details"),enabled){details=!details}
        }
        if(!snapshot.live&&target!=null) Label(snapshot.issueText?:tr("显示上次目标信息，等候位置更新","Showing the last target information while waiting for a position"),12,c.muted,Modifier.padding(horizontal=16.dp,vertical=4.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()){
            if(fallback){
                Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
                    DirectionFallback(snapshot,Modifier.weight(1f).fillMaxWidth(),chinese)
                    Label(when{
                        failed->tr("三维显示暂时中断；方位与距离仍可查看","3D is temporarily interrupted; bearing and distance remain available")
                        target==null->tr("先在地图上选择地点或启用航线","Choose a place or start a route on the map")
                        target.nearTarget->tr("距离很近，方向可能随定位误差变化","At close range, position error can change the bearing")
                        else->reason?:tr("真北方向","True north reference")
                    },13,c.muted,Modifier.padding(horizontal=24.dp,vertical=10.dp))
                    if(target!=null&&target.nearTarget.not()&&(!planar||failed))Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                        SpatialTextAction(tr("自由查看","Explore"),enabled){failed=false;free=true;planar=false}
                        if(failed)SpatialTextAction(tr("重新载入","Retry"),enabled){failed=false}
                    }
                }
            }else{
                val labels=listOfNotNull(snapshot.current,snapshot.next,snapshot.steering).associate{it.id to formats.distance(it.distanceMeters)}
                val input=SpatialRenderInput(snapshot,raw,conversion,rotation,
                    if(free)shownFrame?.camera?.trueBearing?:target?.bearingTrueDegrees?:0.0 else null,chinese=chinese,distanceLabels=labels,fontScale=nativeFontScale)
                AndroidView(factory={ ctx->NavigationSpatialSurface(ctx).also{surface=it} },modifier=Modifier.fillMaxSize(),update={view->
                    view.inputEnabled=enabled&&!details&&inspected==null
                    view.onFailure={failed=true}
                    view.onFreeChanged={free=it}
                    view.onTarget={if(enabled)inspectedId=it}
                    view.contentDescription=tr("目标方向。拖动可自由查看，也可使用底部转向按钮。","Target direction. Drag to explore, or use the turn buttons below.")
                    view.update(input,enabled&&!details&&inspected==null)
                },onRelease={view->view.close();if(surface===view)surface=null})
            }
            if(!fallback&&shownFrame==null)MetroProgress(tr("正在展开方向视图","Opening direction view"),Modifier.align(Alignment.Center).padding(24.dp))
            if(inspected!=null&&!details)Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Label(if(inspected.steering)tr("沿线引导","Route guidance")else if(inspected.id==snapshot.current?.id)tr("当前目标","Current target")else tr("下一站预览","Next target preview"),12,c.muted)
                Label(inspected.name,20,maxLines=2)
                Label(listOfNotNull(formats.distance(inspected.distanceMeters),inspected.bearingTrueDegrees?.let{"${it.roundToInt()}° T"}).joinToString(" · "),15)
                if(inspected.steering)Label(tr("沿规划折线前进；此点随进度移动，不是需要确认的航点","Follow the planned line. This guide moves with progress and is not a waypoint to confirm."),12,c.muted)
                else if(inspected.id!=snapshot.current?.id)Label(tr("查看不会跳过当前目标","Viewing does not skip the current target"),12,c.muted)
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    SpatialTextAction(tr("在地图中查看","View on map"),enabled){val id=inspected.id;inspectedId=null;if(inspected.steering)onOpenMap()else onOpenTarget(id)}
                    SpatialTextAction(tr("关闭","Close"),enabled){inspectedId=null}
                }
            }
            if(details)Column(Modifier.fillMaxSize().background(c.bg.copy(alpha=.96f)).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Label(tr("方向的依据","Direction reference"),20)
                Label(if(snapshot.mountMode==SpatialMountMode.HANDHELD)tr("视线穿过手机屏幕朝向远处，不是手机顶部，也不改变船首向。目标从全船选用的船位计算；这不是相机画面的精确叠加。","The view looks through the screen toward the distance. It is not the phone's top edge and does not change vessel heading. Targets use the selected vessel position; this is not a camera overlay.")else tr("当前视角跟随全船选用的船首向。手机固定位置和安装零点保持不变。","This view follows the selected vessel heading. The confirmed phone mount and zero point stay unchanged."),14,c.muted)
                if(snapshot.mountMode==SpatialMountMode.HANDHELD){
                    Label(tr("手机方向","Phone orientation")+" · "+(raw.sourceName.ifBlank{tr("等待传感器","Waiting for sensor")}),13)
                    raw.elapsedRealtimeMillis?.let{Label(tr("更新于 ${(elapsed-it).coerceAtLeast(0)/1000} 秒前","Updated ${(elapsed-it).coerceAtLeast(0)/1000}s ago"),12,c.muted)}
                    conversion?.let{Label(tr("近似真北：系统地磁模型，磁偏角 ${"%.1f".format(it.declinationDegrees)}°","Approximate true north: system magnetic model, declination ${"%.1f".format(it.declinationDegrees)}°"),13,c.muted)}
                }
                snapshot.vesselHeading?.let{Label(tr("船首向","Heading")+" · ${it.source} · ${it.ageMillis.coerceAtLeast(0)/1000}s",13)}
                snapshot.courseOverGround?.let{Label("COG · ${it.source} · ${it.ageMillis.coerceAtLeast(0)/1000}s",13)}
                Label(tr("环和指示柱表达方向与先后关系，不表示地形、障碍物高度或真实距离比例。","The ring and markers show direction and target order, not terrain, obstacle height or distance scale."),13,c.muted)
                SpatialTextAction(tr("返回观察","Back to view"),enabled){details=false}
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            SpatialTextAction(tr("地图","Map"),enabled,onOpenMap)
            if(!fallback){
                SpatialTextAction("−30°",enabled){surface?.turnBy(-30.0)}
                SpatialTextAction(if(free)tr("跟随","Follow")else tr("二维","2D"),enabled){if(free){surface?.resetView();free=false}else planar=true}
                SpatialTextAction("+30°",enabled){surface?.turnBy(30.0)}
            }else if(planar)SpatialTextAction(tr("三维","3D"),enabled){planar=false}
            if(target!=null)SpatialTextAction(tr("目标","Target"),enabled){inspectedId=target.id}
        }
    }
}

@Composable
private fun SpatialTextAction(text:String,enabled:Boolean,onClick:()->Unit){
    Box(Modifier.heightIn(min=48.dp).clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=8.dp,vertical=12.dp),contentAlignment=Alignment.Center){Label(text,14,if(enabled)LocalMetro.current.accentText else LocalMetro.current.disabled)}
}

/** 缺少姿态时仍表达真实真北方位，不用伪造的船首向旋转箭头。 */
@Composable
private fun DirectionFallback(snapshot:NavigationSpatialSnapshot,modifier:Modifier,chinese:Boolean){
    val colors=LocalMetro.current
    val target=snapshot.steering?:snapshot.current
    Box(modifier,contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize().padding(28.dp).semantics{contentDescription=if(chinese)"真北方向示意"else"True north direction"}){
            val radius=min(size.width,size.height)*.34f;val center=Offset(size.width/2,size.height/2)
            if(radius<12f)return@Canvas
            drawCircle(colors.controlStroke,radius,center,style=Stroke(1.5.dp.toPx()))
            for(d in 0 until 360 step 30){val a=Math.toRadians(d.toDouble());val v=Offset(sin(a).toFloat(),-cos(a).toFloat());drawLine(colors.muted,center+v*(radius*.9f),center+v*radius,1.dp.toPx())}
            target?.bearingTrueDegrees?.takeIf{it.isFinite()&&!target.nearTarget}?.let{degrees->
                val a=Math.toRadians(degrees);val v=Offset(sin(a).toFloat(),-cos(a).toFloat());val n=Offset(-v.y,v.x)
                val tip=center+v*radius*.8f;val base=center-v*radius*.28f
                drawPath(Path().apply{moveTo(tip.x,tip.y);lineTo((base+n*radius*.14f).x,(base+n*radius*.14f).y);lineTo((base-n*radius*.14f).x,(base-n*radius*.14f).y);close()},colors.accentText.copy(alpha=if(snapshot.live)1f else .45f))
            }
        }
        Label("N",14,colors.muted,Modifier.align(Alignment.TopCenter).padding(top=14.dp))
        if(target?.nearTarget==true)Label(if(chinese)"就在附近"else"Nearby",20)
        else target?.bearingTrueDegrees?.takeIf{it.isFinite()}?.let{Label((if(target.steering)if(chinese)"沿线 · "else"Route · "else"")+"${it.roundToInt()}° T",16,colors.muted,Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp))}
    }
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
    Toggle(if(zh)"抬起手机看方向"else"Raise phone for direction",enabled,
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

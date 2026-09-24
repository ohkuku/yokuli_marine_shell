package com.yokuli.marine.shell.rebuild.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.scene.*
import com.yokuli.shell.compose.LocalInternalAppInputEnabled

/** 页面只保存观察方式和热点；唯一数据事实由运行时投影，关闭页面不会停止船舶业务。 */
@Composable internal fun ColumnScope.MyVesselOverview(
    os: OsStore,
    selectedHotspot: VesselHotspot?,
    preset: VesselViewPreset,
    onSelect: (VesselHotspot?) -> Unit,
    onPreset: (VesselViewPreset) -> Unit,
    onDetail: (VesselHotspot) -> Unit,
    openReadings: () -> Unit,
    openMounting: () -> Unit,
    active: Boolean,
) {
    val model = rememberVesselScene(os)
    if (model == null) {
        MetroProgress(os.t("正在读取船舶数据", "reading vessel data"))
        return
    }
    val colors = LocalMetro.current
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("vessel-scene-display", Context.MODE_PRIVATE) }
    // 这只是持久化显示偏好，不增加另一份业务设置或来源选择。
    var prefer2D by rememberSaveable { mutableStateOf(preferences.getBoolean("prefer-2d", false)) }
    val environment = rememberSceneEnvironment()
    var rendererFailed by rememberSaveable { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    val fallback = prefer2D || environment.powerSaver || environment.screenReader || rendererFailed
    LaunchedEffect(fallback) { if(!fallback) ready=false }
    val enabled = LocalInternalAppInputEnabled.current
    val current = model.hotspots.count { model.metric(it.primaryMetric).isCurrent }
    Label(when(current) {
        0 -> os.t("查看船上数据与来源", "explore your vessel's readings")
        4 -> os.t("四项主要读数正在更新", "all four key readings are updating")
        else -> os.t("$current 项主要读数正在更新", "$current key readings are updating")
    }, 16, colors.muted)

    // 标签在模型外排版，放大字体时自然长高，不缩字、遮挡或重叠点击区域。
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VesselHotspotLabel(os, model, VesselHotspot.WIND, selectedHotspot, enabled, onSelect, Modifier.weight(1f))
            VesselHotspotLabel(os, model, VesselHotspot.HEADING, selectedHotspot, enabled, onSelect, Modifier.weight(1f))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(248.dp)) {
            val aspect = maxWidth.value / maxHeight.value
            if (fallback) VesselScene2D(preset, Modifier.fillMaxSize())
            else key(generation) {
                VesselScene3D(preset, os.light, active && enabled, Modifier.fillMaxSize(),
                    onFailure = { rendererFailed = true; ready = false }, onReady = { ready = true })
            }
            // 线的终点使用渲染器相机的同一投影，字体与标牌不会随观察角度旋转。
            Canvas(Modifier.fillMaxSize()) {
                val anchors = listOf(VesselHotspot.WIND,VesselHotspot.HEADING,VesselHotspot.POSITION,VesselHotspot.DEPTH)
                anchors.forEachIndexed { index, id ->
                    val projected = vesselScenePoint(preset, aspect, id.x, id.y, id.z)
                    val target = Offset(projected.x * size.width, projected.y * size.height)
                    val start = Offset(size.width * if(index % 2 == 0) .21f else .79f, if(index < 2) 0f else size.height)
                    val elbow = Offset(start.x, if(index < 2) target.y.coerceAtLeast(12.dp.toPx()) else target.y.coerceAtMost(size.height - 12.dp.toPx()))
                    val color = if(id == selectedHotspot) colors.accent else colors.muted.copy(alpha=.55f)
                    drawLine(color, start, elbow, 1.dp.toPx())
                    drawLine(color, elbow, target, 1.dp.toPx())
                    drawCircle(colors.bg, 4.dp.toPx(), target)
                    drawCircle(color, 3.dp.toPx(), target, style=Stroke(1.5.dp.toPx()))
                }
            }
            if (!fallback && !ready) Box(Modifier.align(Alignment.Center).background(colors.bg).padding(10.dp)) {
                MetroProgress(os.t("载入船体", "loading vessel"))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VesselHotspotLabel(os, model, VesselHotspot.POSITION, selectedHotspot, enabled, onSelect, Modifier.weight(1f))
            VesselHotspotLabel(os, model, VesselHotspot.DEPTH, selectedHotspot, enabled, onSelect, Modifier.weight(1f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        VesselViewPreset.entries.forEach { view ->
            val label = when(view) {
                VesselViewPreset.OVERVIEW -> os.t("概览", "overview")
                VesselViewPreset.TOP -> os.t("俯视", "top")
                VesselViewPreset.SIDE -> os.t("侧视", "side")
            }
            Box(Modifier.weight(1f).heightIn(min=48.dp)
                .selectable(view == preset, enabled=enabled, role=Role.RadioButton, onClick={onPreset(view)})
                .border(if(view==preset)2.dp else 1.dp,if(view==preset)colors.accent else colors.muted)
                .padding(horizontal=8.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                Label(label,16,if(view==preset)colors.accent else colors.fg)
            }
        }
    }
    Label(os.t("通用帆船 · 固定参考视角", "generic sailboat · fixed reference view"),13,colors.muted)
    selectedHotspot?.let { selected ->
        val metric = model.metric(model.hotspot(selected).primaryMetric)
        Column(Modifier.fillMaxWidth().border(1.dp,colors.accent).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Label(vesselHotspotTitle(os, selected),24,modifier=Modifier.weight(1f))
                Box(Modifier.size(48.dp).clickable(enabled=enabled,role=Role.Button,
                    onClickLabel=os.t("收起详情", "close detail"),onClick={onSelect(null)})
                    .semantics { contentDescription=os.t("收起详情", "close detail") },contentAlignment=Alignment.Center) {
                    Glyph("close",Modifier.size(20.dp))
                }
            }
            Label(sourceMetricName(os,metric.id),14,colors.muted)
            Label(vesselMetricValue(os,metric),24)
            Label(vesselStatusText(os,metric),16,colors.muted)
            Label(vesselReferenceText(os,metric.reference,metric.id),14,colors.muted)
            metric.source?.let { source ->
                Label(metric.connection?.name ?: sourceDisplayName(os,source,emptyList()),15,colors.muted)
            }
            metric.receivedElapsedRealtime?.let { Label(os.t("最近更新 · ", "last update · ")+readingAge(os,it,model.generatedElapsedRealtime),14,colors.muted) }
            MenuRow(os.t("查看详情", "view detail"),icon="next") { if(enabled) onDetail(selected) }
        }
    }
    val reason = when {
        environment.screenReader -> os.t("已启用无障碍船体图，四项读数与详情均可访问。", "Accessible vessel view; all four readings and details remain available.")
        environment.powerSaver -> os.t("省电模式使用二维船体，数据照常更新。", "Power saver uses a 2D vessel; readings continue to update.")
        rendererFailed -> os.t("三维船体未能显示，已切换二维；全部数据与操作保留。", "3D could not be displayed. The 2D vessel keeps every reading and action.")
        else -> null
    }
    reason?.let { Label(it,14,colors.muted) }
    if(rendererFailed && !environment.powerSaver && !environment.screenReader && !prefer2D) {
        MetroButton(os.t("重新载入三维船体", "reload 3D vessel"),{generation++;rendererFailed=false;ready=false},enabled=enabled)
    }
    Toggle(os.t("二维船体", "2D vessel"),prefer2D,
        os.t("保留相同的数据入口，减少图形开销。", "The same data entry points with lighter graphics."),enabled=enabled) {
        prefer2D=it
        preferences.edit().putBoolean("prefer-2d",it).apply()
        if(!it){ ready=false; rendererFailed=false; generation++ }
    }
    MenuRow(os.t("全部读数", "all readings"),os.t("查看其他数据与各项来源", "other measurements and their sources"),"data") { if(enabled) openReadings() }
    MenuRow(os.t("手机安装", "phone mounting"),if(model.mount.attitudeConfirmed && model.mount.headingAligned)
        os.t("船首向与姿态安装已确认", "heading and attitude mounting confirmed")
        else os.t("确认固定方式，才能采用手机方向", "confirm mounting before using phone direction")) { if(enabled) openMounting() }
}

@Composable private fun VesselHotspotLabel(
    os: OsStore, model: VesselSceneModel, id: VesselHotspot, selection: VesselHotspot?, enabled:Boolean,
    onSelect:(VesselHotspot?)->Unit,modifier:Modifier,
) {
    val c=LocalMetro.current
    val metric=model.metric(model.hotspot(id).primaryMetric)
    val name=vesselHotspotTitle(os,id)
    val value=vesselMetricValue(os,metric)
    val status=vesselStatusText(os,metric)
    Column(modifier.heightIn(min=72.dp).semantics(mergeDescendants=true) {
        contentDescription="$name, ${sourceMetricName(os,metric.id)}, $value, $status"
        selected=id==selection
    }.clickable(enabled=enabled,role=Role.Button,onClickLabel=os.t("查看$name", "inspect $name"),onClick={onSelect(id)})
        .border(if(id==selection)2.dp else 1.dp,if(id==selection)c.accent else c.muted.copy(alpha=.5f))
        .padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Label(name,18,if(id==selection)c.accent else c.fg)
        Label(sourceMetricName(os,metric.id),12,c.muted)
        Label(value,if(id==VesselHotspot.POSITION)14 else 20)
        Label(status,13,c.muted)
        if(!metric.isCurrent) metric.receivedElapsedRealtime?.let {
            Label(readingAge(os,it,model.generatedElapsedRealtime),12,c.muted)
        }
        if(id==VesselHotspot.POSITION) metric.source?.let { source ->
            Label(metric.connection?.name ?: sourceDisplayName(os,source,emptyList()),12,c.muted)
        }
        if(id==VesselHotspot.DEPTH) Label(vesselReferenceText(os,metric.reference,metric.id),12,c.muted)
    }
}

private data class SceneEnvironment(val powerSaver:Boolean,val screenReader:Boolean)

/** 跟随系统省电与读屏设置；移除监听器时不改变定位、网络或守锚服务。 */
@Composable private fun rememberSceneEnvironment():SceneEnvironment {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val power=remember(context){context.getSystemService(Context.POWER_SERVICE) as PowerManager}
    val accessibility=remember(context){context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager}
    var environment by remember { mutableStateOf(SceneEnvironment(power.isPowerSaveMode,accessibility.isTouchExplorationEnabled)) }
    DisposableEffect(context,lifecycle,power,accessibility) {
        fun refresh(){environment=SceneEnvironment(power.isPowerSaveMode,accessibility.isTouchExplorationEnabled)}
        val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){refresh()}}
        val listener=AccessibilityManager.TouchExplorationStateChangeListener{refresh()}
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()}
        ContextCompat.registerReceiver(context,receiver,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),ContextCompat.RECEIVER_NOT_EXPORTED)
        accessibility.addTouchExplorationStateChangeListener(listener)
        lifecycle.addObserver(observer)
        refresh()
        onDispose {
            context.unregisterReceiver(receiver)
            accessibility.removeTouchExplorationStateChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    return environment
}

/** 同一坐标和语义锚点的二维替代。不绘制海底、浪况或任何不存在的读数。 */
@Composable private fun VesselScene2D(preset:VesselViewPreset,modifier:Modifier) {
    val c=LocalMetro.current
    Canvas(modifier) {
        fun p(x:Float,y:Float,z:Float):Offset {
            val point=vesselScenePoint(preset,size.width/size.height,x,y,z)
            return Offset(point.x*size.width,point.y*size.height)
        }
        fun polygon(points:List<Offset>,fill:Color,outline:Color=c.fg) {
            val path=Path().apply {moveTo(points.first().x,points.first().y);points.drop(1).forEach{lineTo(it.x,it.y)};close()}
            drawPath(path,fill);drawPath(path,outline,style=Stroke(1.3.dp.toPx(),cap=StrokeCap.Round))
        }
        val deck=listOf(p(0f,.3f,2f),p(-.45f,.3f,1f),p(-.65f,.3f,-.3f),p(-.5f,.3f,-2f),p(.5f,.3f,-2f),p(.65f,.3f,-.3f),p(.45f,.3f,1f))
        if(preset!=VesselViewPreset.TOP) {
            polygon(listOf(p(0f,-.1f,.25f),p(0f,-1.2f,0f),p(0f,-1.2f,-.55f),p(0f,-.1f,-.7f)),c.muted.copy(alpha=.35f))
            polygon(listOf(p(-.5f,.3f,-2f),p(-.65f,.3f,-.3f),p(-.45f,.3f,1f),p(0f,.3f,2f),p(0f,-.25f,1.1f),p(-.38f,-.35f,-1.65f)),c.accent.copy(alpha=.55f))
        }
        polygon(deck,c.panel)
        polygon(listOf(p(-.28f,.4f,.3f),p(-.28f,.4f,-.7f),p(.28f,.4f,-.7f),p(.28f,.4f,.3f)),c.muted.copy(alpha=.3f))
        if(preset!=VesselViewPreset.TOP) {
            polygon(listOf(p(0f,3.65f,0f),p(0f,.8f,-1.5f),p(0f,.8f,0f)),c.fg.copy(alpha=.20f))
            polygon(listOf(p(0f,3.4f,.05f),p(0f,.55f,1.75f),p(0f,.65f,.3f)),c.fg.copy(alpha=.13f))
            drawLine(c.fg,p(0f,.3f,0f),p(0f,3.8f,0f),2.dp.toPx())
        }
        drawLine(c.accent,p(0f,.33f,1.1f),p(0f,.33f,1.8f),3.dp.toPx(),StrokeCap.Round)
    }
}

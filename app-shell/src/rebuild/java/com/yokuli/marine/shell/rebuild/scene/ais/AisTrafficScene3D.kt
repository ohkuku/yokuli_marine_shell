package com.yokuli.marine.shell.rebuild.scene.ais

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.shell.rebuild.ui.Label
import com.yokuli.marine.shell.rebuild.ui.LocalMetro
import com.yokuli.marine.shell.rebuild.ui.MetroProgress
import com.yokuli.marine.shell.rebuild.ui.AppBackHandler
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 真正三维交通宿主。平面标签、观测轨迹和方向向量与原生相机共用米制投影。
 * 选船只回传稳定身份；跨应用详情和风险仍由调用方的共享运行时管理。
 */
@Composable
internal fun AisTrafficScene3D(
    data: AisSceneData,
    cameraState: AisSceneCameraState,
    onCameraChanged: (AisSceneCameraState) -> Unit,
    selectedId: String?,
    onSelectTarget: (String) -> Unit,
    onOpen2D: () -> Unit,
    active: Boolean,
    light: Boolean,
    chinese: Boolean,
    modifier: Modifier = Modifier,
    formatDistance: (Double) -> String,
) {
    val colors = LocalMetro.current
    val enabled = LocalInternalAppInputEnabled.current && active
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    val aspect = if (surfaceSize.height > 0) surfaceSize.width.toDouble() / surfaceSize.height else 1.1
    val localFrameCache = remember { arrayOfNulls<AisLocalFrame>(1) }
    val frame = remember(data, cameraState, aspect) {
        aisSceneFrame(data, cameraState, aspect, localFrameCache[0]).also { localFrameCache[0] = it?.local }
    }
    val currentFrame = rememberUpdatedState(frame)
    val currentCamera = rememberUpdatedState(cameraState)
    val changeCamera = rememberUpdatedState(onCameraChanged)
    val selectTarget = rememberUpdatedState(onSelectTarget)
    val selected = data.targets.firstOrNull { it.id == selectedId }
    val hasHeading = validAisBearing(data.ownHeadingDegrees) != null
    var headingFallback by rememberSaveable { mutableStateOf(false) }
    var overlapIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var rendererFailure by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    fun tr(zh: String, en: String) = if (chinese) zh else en
    AppBackHandler(enabled && overlapIds.isNotEmpty()) { overlapIds = emptyList() }

    LaunchedEffect(hasHeading, cameraState.preset) {
        if (!hasHeading && cameraState.preset == AisScenePreset.BOW_FORWARD) {
            headingFallback = true
            onCameraChanged(cameraState.copy(preset = AisScenePreset.NORTH_TOP, bearingDegrees = 0.0, elevationDegrees = 78.0))
        }
    }
    LaunchedEffect(data.ownPosition, frame?.local?.origin, cameraState.centerLatitude) {
        if (data.ownPosition == null && cameraState.centerLatitude == null) {
            frame?.local?.origin?.let { onCameraChanged(cameraState.copy(centerLatitude = it.latitude, centerLongitude = it.longitude, followOwn = false)) }
        }
    }
    LaunchedEffect(active) { if (!active) overlapIds = emptyList() }

    fun reset(preset: AisScenePreset) {
        headingFallback = false
        val own = data.ownPosition?.takeIf { it.valid }
        when (preset) {
            AisScenePreset.ENCOUNTER -> {
                val other = selected?.position ?: return
                val base = own ?: return
                val center = aisSceneMidpoint(base, other)
                val relative = AisLocalFrame(base).position(other)
                val range = max(180.0, sqrt(relative.x * relative.x + relative.z * relative.z) * .75).coerceAtMost(59264.0)
                onCameraChanged(AisSceneCameraState(preset, range, 0.0, 54.0, center.latitude, center.longitude, false))
            }
            AisScenePreset.BOW_FORWARD -> if (hasHeading && own != null) onCameraChanged(AisSceneCameraState(preset, cameraState.rangeMeters, data.ownHeadingDegrees!!, 23.0, own.latitude, own.longitude, true))
            else -> onCameraChanged(AisSceneCameraState(preset, cameraState.rangeMeters, 0.0, 52.0, own?.latitude ?: cameraState.centerLatitude, own?.longitude ?: cameraState.centerLongitude, own != null))
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SceneAction(tr("概览", "overview"), enabled, cameraState.preset == AisScenePreset.OVERVIEW) { reset(AisScenePreset.OVERVIEW) }
            SceneAction(tr("北向俯视", "north up"), enabled, cameraState.preset == AisScenePreset.NORTH_TOP) { reset(AisScenePreset.NORTH_TOP) }
            SceneAction(tr("船艏前视", "bow view"), enabled && hasHeading && data.ownPosition != null, cameraState.preset == AisScenePreset.BOW_FORWARD) { reset(AisScenePreset.BOW_FORWARD) }
            SceneAction(tr("会遇观察", "encounter"), enabled && selected != null && data.ownPosition != null, cameraState.preset == AisScenePreset.ENCOUNTER) { reset(AisScenePreset.ENCOUNTER) }
        }
        if (headingFallback) Label(tr("船首向未更新，已改为北向俯视", "Heading unavailable; returned to north up"), 14, colors.muted)
        if (!hasHeading) Label(tr("船艏前视需要有效船首向；不会用 COG 代替", "Bow view needs heading; COG is a separate motion direction"), 13, colors.muted)
        if (frame?.referenceOnly == true) Label(tr("本船位置未更新 · 地理参考视图，无当前相对距离", "Own position unavailable · geographic reference only"), 14, colors.accent)

        Box(Modifier.weight(1f).fillMaxWidth()
            .background(Color(0xff071720)).onSizeChanged { surfaceSize = it }) {
            when {
                frame == null -> Column(Modifier.align(Alignment.Center).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label(tr("等待有效位置", "waiting for a position"), 24, Color.White)
                    Label(tr("收到船位后才会呈现真实空间关系。目标列表仍可查看已收到的资料。", "Spatial relationships appear only after a valid position. Received details remain in the target list."), 15, Color(0xffb5c9d0))
                    SceneAction(tr("使用二维视图", "use 2D view"), enabled, foreground = Color.White) { onOpen2D() }
                }
                rendererFailure != null -> Column(Modifier.align(Alignment.Center).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(tr("三维图形暂不可用", "3D graphics unavailable"), 24, Color.White)
                    Label(when (rendererFailure) {
                        "native-library" -> tr("此设备无法加载本地三维引擎", "This device could not load the native 3D engine")
                        "graphics-device" -> tr("设备图形驱动未能初始化", "The graphics driver could not initialize")
                        "surface-timeout" -> tr("三维显示窗口未能就绪", "The 3D drawing surface did not become ready")
                        "local-model" -> tr("本地船舶模型未能加载完成", "The local vessel geometry did not finish loading")
                        else -> tr("设备未能完成三维绘制", "The device could not complete the 3D frame")
                    }, 15, Color(0xffb5c9d0))
                    Label(tr("雷达和海图继续使用同一份交通资料", "Radar and chart continue to use the same traffic data"), 14, Color(0xffb5c9d0))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        SceneAction(tr("回到二维", "use 2D"), enabled, foreground = Color.White) { onOpen2D() }
                        SceneAction(tr("重新加载", "retry"), enabled, foreground = Color.White) { rendererFailure = null; ready = false; generation++ }
                    }
                }
                else -> {
                    key(generation) {
                        NativeTrafficScene(data, frame, cameraState, selectedId, light, enabled, Modifier.fillMaxSize(),
                            onFailure = { rendererFailure = it; ready = false }, onReady = { ready = true })
                    }
                    val density = LocalDensity.current
                    val hitRadius = with(density) { 30.dp.toPx() }
                    val inputEnabled = rememberUpdatedState(enabled)
                    val currentSize = rememberUpdatedState(surfaceSize)
                    val targets = frame.targets
                    Box(Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            var pressedIds = emptyList<String>()
                            detectTapGestures(onPress = { point ->
                                val f = currentFrame.value
                                val viewport = currentSize.value
                                pressedIds = if (!inputEnabled.value || f == null) emptyList() else f.targets.map { target ->
                                    val p = f.targetProjections.getValue(target.id)
                                    val distance = hypot(p.x * viewport.width - point.x, p.y * viewport.height - point.y)
                                    target to if (containsReportedHull(target, point, viewport, f)) 0f else distance
                                }.filter { it.second <= hitRadius }.sortedBy { it.second }.map { it.first.id }
                            }, onTap = {
                                if (!inputEnabled.value) return@detectTapGestures
                                val hits = pressedIds
                                if (hits.size == 1) selectTarget.value(hits.first()) else if (hits.isNotEmpty()) overlapIds = hits
                            })
                        }.pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                if (!inputEnabled.value) return@detectTransformGestures
                                val f = currentFrame.value ?: return@detectTransformGestures
                                val state = currentCamera.value
                                val center = if (state.followOwn) f.local.origin else state.centerLatitude?.let { lat -> state.centerLongitude?.let { AisScenePosition(lat, it) } } ?: f.local.origin
                                val currentBearing = if (state.preset == AisScenePreset.BOW_FORWARD && state.followOwn) {
                                    Math.toDegrees(atan2(f.camera.forward.x, -f.camera.forward.z))
                                } else state.bearingDegrees
                                changeCamera.value(state.copy(
                                    preset = AisScenePreset.OVERVIEW,
                                    rangeMeters = (state.rangeMeters / zoom.coerceIn(.7f, 1.4f)).coerceIn(100.0, 59264.0),
                                    bearingDegrees = (currentBearing - pan.x * .22 - rotation + 1080.0) % 360.0,
                                    elevationDegrees = (state.elevationDegrees + pan.y * .12).coerceIn(28.0, 78.0),
                                    centerLatitude = center.latitude, centerLongitude = center.longitude, followOwn = false,
                                ))
                            }
                        }) {
                        SceneReferenceOverlay(data, frame, selectedId, Modifier.fillMaxSize())
                        val prioritizedLabels = remember(frame.targets, selectedId) {
                            frame.targets.sortedWith(compareByDescending<AisSceneTarget> { it.id == selectedId }
                                .thenByDescending { it.risk }.thenByDescending { it.followed }.thenBy { it.id })
                        }
                        val labelLayouts = remember(frame, surfaceSize, prioritizedLabels, density.density, density.fontScale) {
                            arrangeLabels(frame, surfaceSize, prioritizedLabels, density.density, density.fontScale)
                        }
                        labelLayouts.forEach { layout ->
                            key(layout.target.id) {
                                val target = layout.target
                                val tint = when { target.risk -> Color(0xffffa089); target.stale || target.lost -> Color(0xffa8b1b7); target.id == selectedId -> Color(0xff6dd4ff); else -> Color(0xffe0f4ff) }
                                Column(Modifier.offset { IntOffset(layout.rect.left.roundToInt(), layout.rect.top.roundToInt()) }
                                    .width(with(density) { layout.rect.width.toDp() }).heightIn(min = 40.dp)
                                    .background(Color(0xe6091a23)).border(if (target.id == selectedId) 1.dp else 0.dp, if (target.id == selectedId) tint else Color.Transparent)
                                    .clickable(enabled = enabled, role = Role.Button) { onSelectTarget(target.id) }
                                    .semantics { contentDescription = "${target.label}, ${target.ageLabel}, ${target.statusLabel}" }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)) {
                                    Label(target.label, 14, tint, maxLines = 1)
                                    Label(listOf(target.ageLabel, target.statusLabel).filter(String::isNotBlank).joinToString(" · "), 11, Color(0xffabc0ca), maxLines = 1)
                                }
                            }
                        }
                    }
                    SceneCompass(frame, Modifier.align(Alignment.TopEnd).padding(8.dp).size(54.dp))
                    Column(Modifier.align(Alignment.TopStart).background(Color(0xbb071720)).padding(7.dp)) {
                        Label(tr("距圈 ${formatDistance(cameraState.rangeMeters / 4.0)}", "rings ${formatDistance(cameraState.rangeMeters / 4.0)}"), 11, Color(0xffb5c9d0))
                        val visibleCount = frame.targetProjections.values.count { point -> point.x in 0f..1f && point.y in 0f..1f }
                        Label(tr("视口内 $visibleCount / 有位置 ${frame.targets.size}", "in view $visibleCount / located ${frame.targets.size}"), 11, Color(0xffb5c9d0))
                    }
                    if (!ready) Column(Modifier.align(Alignment.Center).background(Color(0xdd071720)).padding(18.dp)) {
                        CompositionLocalProvider(LocalMetro provides colors.copy(fg = Color.White, muted = Color(0xffb5c9d0))) {
                            MetroProgress(tr("载入本地三维交通", "loading local 3D traffic"))
                        }
                    }
                    val outside = targets.filter { it.risk || it.id == selectedId }.filter {
                        val p = frame.targetProjections.getValue(it.id); p.x !in 0f..1f || p.y !in 0f..1f
                    }.sortedWith(compareByDescending<AisSceneTarget> { it.id == selectedId }.thenBy { it.id })
                    if (outside.isNotEmpty()) Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xe6091a23)).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                        outside.take(6).forEach { target -> SceneAction(tr("视野外 · ${target.label}", "off-screen · ${target.label}"), enabled, foreground = Color.White) { onSelectTarget(target.id) } }
                        if (outside.size > 6) SceneAction(tr("全部 ${outside.size} 个", "all ${outside.size}"), enabled, foreground = Color.White) { overlapIds = outside.map { it.id } }
                    }
                    if (overlapIds.isNotEmpty()) {
                        Column(Modifier.align(Alignment.Center).fillMaxWidth(.9f).heightIn(max = 280.dp).background(colors.bg).border(1.dp, colors.muted).padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Label(tr("选择船舶", "choose target"), 22)
                                SceneAction(tr("关闭", "close"), enabled) { overlapIds = emptyList() }
                            }
                            LazyColumn {
                                items(overlapIds, key = { it }) { id ->
                                    val target = data.targets.firstOrNull { it.id == id }
                                    Column(Modifier.fillMaxWidth().clickable(enabled = enabled && target != null) { overlapIds = emptyList(); onSelectTarget(id) }.padding(vertical = 10.dp)) {
                                        Label(target?.label ?: id, 17)
                                        Label(target?.let { listOf(it.ageLabel, it.statusLabel).filter(String::isNotBlank).joinToString(" · ") } ?: tr("目标已退出当前资料", "target no longer present"), 13, colors.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SceneAction("−", enabled && cameraState.rangeMeters < 59264) { onCameraChanged(cameraState.copy(rangeMeters = (cameraState.rangeMeters * 1.5).coerceAtMost(59264.0))) }
            Label(formatDistance(cameraState.rangeMeters), 15)
            SceneAction("+", enabled && cameraState.rangeMeters > 100) { onCameraChanged(cameraState.copy(rangeMeters = (cameraState.rangeMeters / 1.5).coerceAtLeast(100.0))) }
            SceneAction(tr("回本船", "own vessel"), enabled && data.ownPosition != null) { reset(AisScenePreset.OVERVIEW) }
            if (selected != null) SceneAction(tr("聚焦目标", "focus target"), enabled) {
                onCameraChanged(cameraState.copy(preset = AisScenePreset.OVERVIEW, centerLatitude = selected.position.latitude, centerLongitude = selected.position.longitude, followOwn = false))
            }
        }
        Label(tr("拖动旋转 · 双指缩放 · 实线轨迹 / ${data.vectorSeconds.toInt()} 秒对地虚线", "drag to orbit · pinch to zoom · track / ${data.vectorSeconds.toInt()} s ground vector"), 12, colors.muted)
        Label(tr("外观/高度示意 · 无有效尺寸时模型非实尺 · 圆点为实际报告位置", "Illustrative appearance/height · no dimensions: symbolic scale · dot = observed position"), 12, colors.muted)
        if (data.targets.size > 64) Label(tr("目标较多时优先显示选中、关注和风险目标的立体模型，其余保留可点击位置符号", "With many targets, 3D geometry prioritizes selected, followed and risk targets; others retain tappable position symbols"), 12, colors.muted)
        if (selected != null && (selected.stale || selected.lost)) Label(tr("选中目标停留在最后观测位置 · ${selected.ageLabel}", "Selected target remains at its last observation · ${selected.ageLabel}"), 14, colors.muted)
    }
}

@Composable
private fun SceneAction(text: String, enabled: Boolean, selected: Boolean = false, foreground: Color? = null, onClick: () -> Unit) {
    val colors = LocalMetro.current
    Box(Modifier.sizeIn(minHeight = 44.dp, minWidth = 40.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Label(text, 15, when { !enabled -> (foreground ?: colors.muted).copy(alpha = .5f); selected -> colors.accent; else -> foreground ?: colors.fg })
    }
}

@Composable
private fun NativeTrafficScene(
    data: AisSceneData, frame: AisSceneFrame, state: AisSceneCameraState, selectedId: String?, light: Boolean, active: Boolean, modifier: Modifier,
    onFailure: (String) -> Unit, onReady: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failure = rememberUpdatedState(onFailure)
    val ready = rememberUpdatedState(onReady)
    val currentActive = rememberUpdatedState(active)
    val renderer = remember(context) { AisTrafficRenderer3D(context, { failure.value(it) }, { ready.value() }) }
    AndroidView(factory = { renderer.textureView }, modifier = modifier,
        update = { renderer.update(data, frame, state, selectedId, light, active) }, onRelease = { renderer.close() })
    DisposableEffect(renderer, lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> renderer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        lifecycle.addObserver(observer)
        renderer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); renderer.close() }
    }
    LaunchedEffect(renderer) {
        try {
            // 预组装的不可见应用不能在前台手势中创建 Engine 和 130 个实例。
            snapshotFlow { currentActive.value }.first { it }
            renderer.initialize()
            val buffers = withContext(Dispatchers.IO) {
                listOf("traffic-vessel", "traffic-neutral", "traffic-plane").map { name ->
                    val bytes = context.assets.open("ais/$name.glb").use { it.readBytes() }
                    require(bytes.size in 20..1_048_576) { "Invalid traffic model size" }
                    ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); flip() }
                }
            }
            renderer.load(buffers)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { renderer.fail(error) }
    }
}

private data class SceneLabel(val target: AisSceneTarget, val rect: Rect)

/** 大型实尺船也可以点船体，不能只点偏置在艉部的 AIS 天线报告点。 */
private fun containsReportedHull(target: AisSceneTarget, touch: Offset, viewport: IntSize, frame: AisSceneFrame): Boolean {
    val dimensions = target.dimensions?.takeIf { it.reliable } ?: return false
    val heading = validAisBearing(target.headingDegrees)?.let(Math::toRadians) ?: return false
    if (target.kind != AisSceneKind.VESSEL) return false
    val reference = frame.targetPositions.getValue(target.id)
    val vertices = listOf(
        -dimensions.toPort to -dimensions.toStern, dimensions.toStarboard to -dimensions.toStern,
        dimensions.toStarboard to dimensions.toBow, -dimensions.toPort to dimensions.toBow,
    ).map { (x, z) ->
        val world = reference + AisVector3(cos(heading) * x + sin(heading) * z, 0.0, sin(heading) * x - cos(heading) * z)
        val p = frame.camera.project(world)
        Offset(p.x * viewport.width, p.y * viewport.height)
    }
    var inside = false
    var previous = vertices.last()
    for (next in vertices) {
        if ((next.y > touch.y) != (previous.y > touch.y) && touch.x < (previous.x - next.x) * (touch.y - next.y) / (previous.y - next.y) + next.x) inside = !inside
        previous = next
    }
    return inside
}

/** 指北针独立于船首向，手动旋转后仍标记地理真北。 */
@Composable
private fun SceneCompass(frame: AisSceneFrame, modifier: Modifier) {
    val center = frame.camera.project(frame.camera.target)
    val north = frame.camera.project(frame.camera.target + AisVector3(0.0, 0.0, -100.0))
    val direction = (north - center).let { it / it.getDistance().coerceAtLeast(.00001f) }
    Box(modifier.background(Color(0xbb071720)).semantics { contentDescription = "N · true north" }) {
        Canvas(Modifier.fillMaxSize()) {
            val origin = Offset(size.width * .5f, size.height * .5f)
            val tip = origin + direction * (size.minDimension * .30f)
            drawLine(Color(0xffc8e9f2), origin - direction * (size.minDimension * .12f), tip, 1.5.dp.toPx())
            val cross = Offset(-direction.y, direction.x)
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                val left = tip - direction * 6.dp.toPx() + cross * 3.dp.toPx(); lineTo(left.x, left.y)
                val right = tip - direction * 6.dp.toPx() - cross * 3.dp.toPx(); lineTo(right.x, right.y)
                close()
            }
            drawPath(path, Color.White)
        }
        Label("N", 11, Color.White, Modifier.align(Alignment.TopStart).padding(2.dp))
    }
}

private fun arrangeLabels(frame: AisSceneFrame, size: IntSize, prioritizedTargets: List<AisSceneTarget>, density: Float, fontScale: Float): List<SceneLabel> {
    if (size.width <= 0 || size.height <= 0) return emptyList()
    val result = mutableListOf<SceneLabel>()
    val width = min(size.width * .44f, 142f * density * fontScale.coerceAtMost(1.5f))
    val height = (40f * density * fontScale.coerceAtLeast(1f)).coerceAtLeast(40f * density)
    val inset = 5f * density
    val reserved = listOf(Rect(0f, 0f, min(size.width * .60f, 200f * density), 46f * density), Rect(size.width - 72f * density, 0f, size.width.toFloat(), 72f * density))
    for (target in prioritizedTargets) {
        if (result.size >= 12) break
        val projected = frame.targetProjections.getValue(target.id)
        if (projected.x !in .02f.. .98f || projected.y !in .02f.. .95f) continue
        val p = Offset(projected.x * size.width, projected.y * size.height)
        val choices = listOf(Offset(p.x + 9 * density, p.y - height / 2), Offset(p.x - width - 9 * density, p.y - height / 2), Offset(p.x - width / 2, p.y - height - 12 * density), Offset(p.x - width / 2, p.y + 12 * density))
        val candidate = choices.map { Rect(it, androidx.compose.ui.geometry.Size(width, height)) }.firstOrNull {
            it.left >= inset && it.top >= inset && it.right <= size.width - inset && it.bottom <= size.height - inset && reserved.none { occupied -> occupied.overlaps(it) } && result.none { old -> old.rect.inflate(3 * density).overlaps(it) }
        }
        if (candidate != null) result.add(SceneLabel(target, candidate))
    }
    return result
}

@Composable
private fun SceneReferenceOverlay(data: AisSceneData, frame: AisSceneFrame, selectedId: String?, modifier: Modifier) {
    Canvas(modifier) {
        fun project(point: AisVector3): Offset = frame.camera.project(point).let { Offset(it.x * size.width, it.y * size.height) }
        fun path(points: List<AisVector3>): Path = Path().apply { points.forEachIndexed { index, point -> val p = project(point); if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val own = data.ownPosition?.takeIf { it.valid }?.let(frame.local::position)
        val reference = own ?: frame.camera.target
        // 距离环与地平面共同投影，不画雷达扫描或没有来源的海浪。
        val radius = min(frame.camera.halfWidth, frame.camera.halfHeight)
        for (fraction in listOf(.25, .5, .75, 1.0)) {
            val points = (0..96).map { i -> val angle = i * 2.0 * PI / 96; reference + AisVector3(sin(angle) * radius * fraction, 0.0, -cos(angle) * radius * fraction) }
            drawPath(path(points), Color(0xff5e8a9b).copy(alpha = .42f), style = Stroke(1.dp.toPx()))
        }
        for (bearing in listOf(0, 90, 180, 270)) {
            val angle = Math.toRadians(bearing.toDouble())
            drawLine(Color(0xff5e8a9b).copy(alpha = .28f), project(reference), project(reference + AisVector3(sin(angle) * radius, 0.0, -cos(angle) * radius)), 1.dp.toPx())
        }
        fun vector(point: AisVector3, cog: Double?, speed: Double?, color: Color) {
            val bearing = validAisBearing(cog) ?: return
            val velocity = speed?.takeIf { it.isFinite() && it > .05 && it < 100 } ?: return
            val distance = velocity * data.vectorSeconds.coerceIn(1.0, 900.0)
            val angle = Math.toRadians(bearing)
            val end = point + AisVector3(sin(angle) * distance, 0.0, -cos(angle) * distance)
            val from = project(point); val to = project(end)
            drawLine(color, from, to, 1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            val dir = to - from
            val length = dir.getDistance()
            if (length > 10.dp.toPx()) {
                val unit = dir / length
                val side = Offset(-unit.y, unit.x)
                val head = Path().apply { moveTo(to.x, to.y); val left = to - unit * 7.dp.toPx() + side * 3.dp.toPx(); lineTo(left.x, left.y); val right = to - unit * 7.dp.toPx() - side * 3.dp.toPx(); lineTo(right.x, right.y); close() }
                drawPath(head, color)
            }
        }
        frame.targets.forEach { target ->
            val point = frame.targetPositions.getValue(target.id)
            val projected = frame.targetProjections.getValue(target.id).let { Offset(it.x * size.width, it.y * size.height) }
            val color = when { target.risk -> Color(0xffff7758); target.stale || target.lost -> Color(0xff7e949d); target.id == selectedId -> Color(0xff5bd3ff); else -> Color(0xffc8e9f2) }
            if (data.showTracks && (target.id == selectedId || target.followed || target.risk)) target.track.forEach { segment ->
                val valid = segment.takeLast(180).filter { it.valid }.map(frame.local::position)
                if (valid.size > 1) drawPath(path(valid), color.copy(alpha = .65f), style = Stroke(1.5.dp.toPx()))
            }
            if (!target.stale && !target.lost) vector(point, target.cogDegrees, target.sogMetersPerSecond, color.copy(alpha = .75f))
            if (projected.x in -8f..(size.width + 8f) && projected.y in -8f..(size.height + 8f)) {
                drawCircle(Color(0xff061219), 4.dp.toPx(), projected)
                when (target.kind) {
                    AisSceneKind.AID_TO_NAVIGATION, AisSceneKind.UNKNOWN -> {
                        val r = 4.dp.toPx()
                        val diamond = Path().apply { moveTo(projected.x, projected.y - r); lineTo(projected.x + r, projected.y); lineTo(projected.x, projected.y + r); lineTo(projected.x - r, projected.y); close() }
                        drawPath(diamond, color, style = Stroke(1.6.dp.toPx()))
                    }
                    AisSceneKind.BASE_STATION -> drawRect(color, projected - Offset(3.dp.toPx(), 3.dp.toPx()), androidx.compose.ui.geometry.Size(6.dp.toPx(), 6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    AisSceneKind.DISTRESS -> {
                        drawCircle(color, 5.dp.toPx(), projected, style = Stroke(2.dp.toPx()))
                        drawLine(color, projected - Offset(3.dp.toPx(), 0f), projected + Offset(3.dp.toPx(), 0f), 1.5.dp.toPx())
                        drawLine(color, projected - Offset(0f, 3.dp.toPx()), projected + Offset(0f, 3.dp.toPx()), 1.5.dp.toPx())
                    }
                    else -> drawCircle(color, 3.dp.toPx(), projected)
                }
                if (target.id == selectedId) drawCircle(color, 11.dp.toPx(), projected, style = Stroke(1.8.dp.toPx()))
            }
        }
        if (own != null) {
            vector(own, data.ownCogDegrees, data.ownSogMetersPerSecond, Color.White.copy(alpha = .8f))
            val point = project(own)
            drawCircle(Color.White, 4.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
            drawCircle(Color(0xff071720), 2.dp.toPx(), point)
        }
    }
}

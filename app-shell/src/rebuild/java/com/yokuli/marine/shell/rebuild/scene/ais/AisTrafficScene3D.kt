package com.yokuli.marine.shell.rebuild.scene.ais

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.shell.rebuild.ui.Label
import com.yokuli.marine.shell.rebuild.ui.Glyph
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
    onCameraGestureFinished: () -> Unit = {},
    onOpenPositionSources: () -> Unit,
    onOpenAisSources: () -> Unit,
    onOpenHeadingSources: () -> Unit,
) {
    val colors = LocalMetro.current
    val enabled = LocalInternalAppInputEnabled.current && active
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    val aspect = if (surfaceSize.height > 0) surfaceSize.width.toDouble() / surfaceSize.height else 1.1
    val own = data.ownPosition?.takeIf { it.valid }
    val hasHeading = validAisBearing(data.ownHeadingDegrees) != null
    var headingPaused by rememberSaveable { mutableStateOf(false) }
    val headingFallback = cameraState.preset == AisScenePreset.BOW_FORWARD && (headingPaused || !hasHeading || own == null)
    val displayCamera = if (headingFallback) cameraState.copy(preset = AisScenePreset.NORTH_TOP, bearingDegrees = 0.0) else cameraState
    val localFrameCache = remember { arrayOfNulls<AisLocalFrame>(1) }
    val frame = remember(data, displayCamera, aspect) {
        aisSceneFrame(data, displayCamera, aspect, localFrameCache[0]).also { localFrameCache[0] = it?.local }
    }
    val currentFrame = rememberUpdatedState(frame)
    val currentCamera = rememberUpdatedState(cameraState)
    val changeCamera = rememberUpdatedState(onCameraChanged)
    val selectTarget = rememberUpdatedState(onSelectTarget)
    val cameraGestureFinished = rememberUpdatedState(onCameraGestureFinished)
    val selected = data.targets.firstOrNull { it.id == selectedId }
    var helpVisible by rememberSaveable { mutableStateOf(false) }
    var overlapIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var rendererFailure by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var presentedTargets by remember { mutableStateOf(emptySet<String>()) }
    val currentPresentedTargets = rememberUpdatedState(presentedTargets)
    var generation by remember { mutableIntStateOf(0) }
    fun tr(zh: String, en: String) = if (chinese) zh else en
    AppBackHandler(enabled && (overlapIds.isNotEmpty() || helpVisible)) {
        if (overlapIds.isNotEmpty()) overlapIds = emptyList() else helpVisible = false
    }
    LaunchedEffect(hasHeading, own != null, cameraState.preset) {
        if ((!hasHeading || own == null) && cameraState.preset == AisScenePreset.BOW_FORWARD) headingPaused = true
    }
    LaunchedEffect(frame == null) { if (frame == null) { ready = false; presentedTargets = emptySet() } }
    LaunchedEffect(own, frame?.local?.origin, cameraState.centerLatitude) {
        if (own == null && cameraState.centerLatitude == null) {
            frame?.local?.origin?.let { onCameraChanged(cameraState.copy(centerLatitude = it.latitude, centerLongitude = it.longitude, followOwn = false)) }
        }
    }
    LaunchedEffect(active) { if (!active) { overlapIds = emptyList(); helpVisible = false } }

    fun reset(preset: AisScenePreset) {
        headingPaused = false
        when (preset) {
            AisScenePreset.ENCOUNTER -> {
                val other = selected?.position?.takeIf { it.valid } ?: return
                val base = own ?: return
                val center = aisSceneMidpoint(base, other)
                val relative = AisLocalFrame(base).position(other)
                val range = max(180.0, sqrt(relative.x * relative.x + relative.z * relative.z) * .75).coerceAtMost(59264.0)
                onCameraChanged(AisSceneCameraState(preset, range, 0.0, 48.0, center.latitude, center.longitude, false))
            }
            AisScenePreset.BOW_FORWARD -> if (hasHeading && own != null) {
                onCameraChanged(AisSceneCameraState(preset, cameraState.rangeMeters, data.ownHeadingDegrees!!, 23.0, own.latitude, own.longitude, true))
            }
            else -> onCameraChanged(AisSceneCameraState(preset, cameraState.rangeMeters, 0.0, 48.0,
                own?.latitude ?: cameraState.centerLatitude, own?.longitude ?: cameraState.centerLongitude, own != null))
        }
        cameraGestureFinished.value()
    }

    // 工具浮在场景上，显示状态、帮助与相机操作均不能重新测量原生画布。
    Box(modifier.clipToBounds().background(Color(0xff071720)).onSizeChanged { surfaceSize = it }) {
        when {
            frame == null -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(tr("等待船位", "Waiting for a position"), 20, Color.White)
                Label(tr("收到本船或附近船舶的位置后，这里会显示它们的空间关系。", "Nearby traffic appears here when a vessel position arrives."), 15, Color(0xffb5c9d0))
                SceneAction(tr("检查本船定位", "Check own position"), enabled, foreground = Color.White, onClick = onOpenPositionSources)
                SceneAction(tr("连接船舶信号", "Connect AIS input"), enabled, foreground = Color.White, onClick = onOpenAisSources)
            }
            rendererFailure != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(tr("三维暂时无法显示", "3D is temporarily unavailable"), 20, Color.White)
                Label(when (rendererFailure) {
                    "native-library" -> tr("设备无法加载三维引擎。你仍可在雷达中查看同一批船舶。", "The 3D engine could not load. The same vessels remain available on radar.")
                    "initialization" -> tr("三维引擎未能启动。你仍可使用雷达。", "The 3D engine could not start. Radar remains available.")
                    "layout" -> tr("三维画布没有获得可用空间，可以重新载入。", "The 3D canvas did not receive usable space. Try reloading.")
                    "surface" -> tr("绘图窗口未能就绪，可以重新载入。", "The drawing surface did not become ready. Try reloading.")
                    "asset-read" -> tr("本地船模文件未能读入，可以重新载入。", "The local vessel files could not be read. Try reloading.")
                    "asset-upload" -> tr("船模未能载入图形设备，可以重新载入。", "The vessel models could not reach the graphics device. Try reloading.")
                    "first-frame" -> tr("船模已读入，但首帧未能完成。可以重新载入。", "The models loaded, but the first frame did not complete. Try reloading.")
                    "resume-surface", "resume-frame" -> tr("返回应用后，三维显示未能恢复。可以重新载入。", "The 3D view could not resume after returning to the app. Try reloading.")
                    else -> tr("绘图暂时中断，可以重新载入。", "Drawing was interrupted. Try loading it again.")
                }, 15, Color(0xffb5c9d0))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SceneAction(tr("查看雷达", "Open radar"), enabled, foreground = Color.White, onClick = onOpen2D)
                    SceneAction(tr("重新载入", "Retry"), enabled, foreground = Color.White) { rendererFailure = null; ready = false; presentedTargets = emptySet(); generation++ }
                }
            }
            else -> {
                key(generation) {
                    NativeTrafficScene(data, frame, cameraState, selectedId, light, enabled, Modifier.fillMaxSize(),
                        onFailure = { rendererFailure = it; ready = false; presentedTargets = emptySet() }, onReady = { ready = true },
                        onPresentedTargets = { presentedTargets = it })
                }
                val density = LocalDensity.current
                val hitRadius = with(density) { 30.dp.toPx() }
                val inputEnabled = rememberUpdatedState(enabled)
                val currentSize = rememberUpdatedState(surfaceSize)
                Box(Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        var pressedIds = emptyList<String>()
                        detectTapGestures(onPress = { point ->
                            val f = currentFrame.value
                            val viewport = currentSize.value
                            pressedIds = if (!inputEnabled.value || f == null) emptyList() else f.targets.map { target ->
                                val p = f.targetProjections.getValue(target.id)
                                val x = p.x * viewport.width; val y = p.y * viewport.height
                                // 超预算目标保留真实位置标记；只有已出帧模型才拥有完整船体命中。
                                val markerVisible = x in -8f..(viewport.width + 8f) && y in -8f..(viewport.height + 8f)
                                val distance = if (markerVisible) hypot(x - point.x, y - point.y) else Float.POSITIVE_INFINITY
                                target to if (target.id in currentPresentedTargets.value && containsDisplayModel(target, point, viewport, f, density.density)) 0f else distance
                            }.filter { it.second <= hitRadius }.sortedBy { it.second }.map { it.first.id }
                        }, onTap = {
                            if (!inputEnabled.value) return@detectTapGestures
                            val hits = pressedIds
                            if (hits.size == 1) selectTarget.value(hits.first()) else if (hits.isNotEmpty()) overlapIds = hits
                        })
                    }.pointerInput(Unit) {
                        // 单指横滑属于 Pivot；只有两个手指同时按住才接管场景，避免抢走切页。
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var transformed = false
                            var gestureState = currentCamera.value
                            var orbitRequested = gestureState.preset != AisScenePreset.BOW_FORWARD
                            var gesturePan = Offset.Zero
                            var gestureRotation = 0f
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.count { it.pressed }
                                if (!inputEnabled.value) break
                                if (!transformed && pressed == 1 && event.changes.firstOrNull { it.pressed }?.let { (it.position - firstDown.position).getDistance() > viewConfiguration.touchSlop } == true) break
                                if (pressed >= 2) {
                                    val f = currentFrame.value ?: break
                                    if (!transformed) {
                                        gestureState = currentCamera.value
                                        if (gestureState.preset == AisScenePreset.BOW_FORWARD && gestureState.followOwn) {
                                            gestureState = gestureState.copy(bearingDegrees = Math.toDegrees(atan2(f.camera.forward.x, -f.camera.forward.z)))
                                        }
                                    }
                                    transformed = true
                                    val center = if (gestureState.followOwn) f.local.origin else gestureState.centerLatitude?.let { lat -> gestureState.centerLongitude?.let { AisScenePosition(lat, it) } } ?: f.local.origin
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom().takeIf { it.isFinite() && it > 0f } ?: 1f
                                    val rotation = event.calculateRotation()
                                    gesturePan += pan; gestureRotation += rotation
                                    if (gesturePan.getDistance() > viewConfiguration.touchSlop * 2f || abs(gestureRotation) > 5f) orbitRequested = true
                                    gestureState = gestureState.copy(
                                        preset = if (orbitRequested) AisScenePreset.OVERVIEW else gestureState.preset,
                                        rangeMeters = (gestureState.rangeMeters / zoom.coerceIn(.7f, 1.4f)).coerceIn(100.0, 59264.0),
                                        bearingDegrees = if (orbitRequested) (gestureState.bearingDegrees - pan.x * .16 - rotation + 1080.0) % 360.0 else gestureState.bearingDegrees,
                                        elevationDegrees = if (orbitRequested) (gestureState.elevationDegrees + pan.y * .12).coerceIn(20.0, 78.0) else gestureState.elevationDegrees,
                                        centerLatitude = center.latitude, centerLongitude = center.longitude, followOwn = !orbitRequested && gestureState.followOwn,
                                    )
                                    changeCamera.value(gestureState)
                                }
                                if (transformed) event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            if (transformed) cameraGestureFinished.value()
                        }
                    }) {
                    SceneReferenceOverlay(data, frame, selectedId, Modifier.fillMaxSize())
                    val prioritizedLabels = remember(frame.targets, selectedId) {
                        frame.targets.filter { it.id == selectedId || it.risk || it.followed }
                            .sortedWith(compareByDescending<AisSceneTarget> { it.id == selectedId }
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
                                .background(Color(0xe6091a23))
                                .clickable(enabled = enabled, role = Role.Button) { onSelectTarget(target.id) }
                                .semantics { contentDescription = "${target.label}, ${target.ageLabel}, ${target.statusLabel}" }
                                .padding(horizontal = 6.dp, vertical = 3.dp)) {
                                Label(target.label, 14, tint, maxLines = 1)
                                Label(listOf(target.ageLabel, target.statusLabel).filter(String::isNotBlank).joinToString(" · "), 11, Color(0xffabc0ca), maxLines = 1)
                            }
                        }
                    }
                }
                SceneCompass(frame, Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp)
                    .clickable(enabled = enabled, role = Role.Button) { reset(AisScenePreset.NORTH_TOP) })
                if (!ready) Column(Modifier.align(Alignment.Center).background(Color(0xdd071720)).padding(18.dp)) {
                    CompositionLocalProvider(LocalMetro provides colors.copy(fg = Color.White, muted = Color(0xffb5c9d0))) {
                        MetroProgress(tr("载入三维视图", "Loading 3D"))
                    }
                }
                val outside = frame.targets.filter { it.risk || it.id == selectedId }.filter {
                    val p = frame.targetProjections.getValue(it.id); p.x !in 0f..1f || p.y !in 0f..1f
                }.sortedWith(compareByDescending<AisSceneTarget> { it.id == selectedId }.thenBy { it.id })
                if (outside.isNotEmpty()) SceneAction(
                    tr("${outside.size} 艘关注船在视野外", "${outside.size} vessels of interest off screen"), enabled,
                    foreground = Color(0xffb5dbea), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
                        .background(Color(0xc9071720)),
                ) { overlapIds = outside.map { it.id } }
                Column(Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = 220.dp).background(Color(0xb3071720)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Label(tr("每圈 ${formatDistance(cameraState.rangeMeters / 4.0)}", "Rings ${formatDistance(cameraState.rangeMeters / 4.0)}"), 11, Color(0xffb5c9d0))
                    if (frame.referenceOnly) Label(tr("设置本船定位 ›", "Set own position ›"), 11, Color(0xffffc790),
                        Modifier.heightIn(min = 36.dp).clickable(enabled = enabled, role = Role.Button, onClick = onOpenPositionSources).padding(vertical = 8.dp), maxLines = 1)
                    else if (headingFallback) Label(if (hasHeading) tr("前视待恢复 · 轻点箭头", "Tap look ahead to resume") else tr("暂用北向 · 等待船首向", "North up · waiting for heading"), 11, Color(0xffffc790), maxLines = 1)
                }
            }
        }
        if (frame != null && rendererFailure == null) {
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0xd9071720)), verticalAlignment = Alignment.CenterVertically) {
                SceneTool("boat", tr("回本船", "Own vessel"), enabled && own != null, cameraState.followOwn && cameraState.preset == AisScenePreset.OVERVIEW) { reset(AisScenePreset.OVERVIEW) }
                if (selected != null) SceneTool("locate", tr("观察选中船舶", "Observe selected vessel"), enabled, cameraState.preset == AisScenePreset.ENCOUNTER) {
                    if (own != null) reset(AisScenePreset.ENCOUNTER)
                    else onCameraChanged(cameraState.copy(preset = AisScenePreset.OVERVIEW, centerLatitude = selected.position.latitude, centerLongitude = selected.position.longitude, followOwn = false))
                }
                SceneTool("heading", if (!hasHeading) tr("设置船首向", "Set up heading") else if (own == null) tr("设置本船定位", "Set own position") else tr("沿船首向观察", "Look ahead"), enabled,
                    cameraState.preset == AisScenePreset.BOW_FORWARD && !headingFallback) {
                    when { !hasHeading -> onOpenHeadingSources(); own == null -> onOpenPositionSources(); else -> reset(AisScenePreset.BOW_FORWARD) }
                }
            }
        }
        SceneTool("info", tr("三维操作说明", "3D help"), enabled, helpVisible, Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color(0xd9071720))) { helpVisible = !helpVisible }
        if (helpVisible) Column(Modifier.align(Alignment.Center).padding(horizontal = 20.dp).widthIn(max = 420.dp)
            .heightIn(max = 360.dp).background(Color(0xf5081b26)).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(tr("看清附近船舶", "Explore nearby traffic"), 20, Color.White, Modifier.weight(1f))
                SceneTool("close", tr("关闭说明", "Close help"), enabled) { helpVisible = false }
            }
            Label(tr("单指左右滑动切换页面。双指拖动旋转、调整俯仰，张合缩放。轻点船舶查看资料。", "Swipe with one finger to change pages. Use two fingers to orbit, tilt and pinch to zoom. Tap a vessel for details."), 15, Color(0xffd6e7ed))
            Label(tr("船的位置取自已收到的报告。远处的小船会放大为易辨认的示意模型；拉近后，有可靠尺寸的船恢复实际比例。外观和高度仅供辨识。", "Positions come from received reports. Small vessels use a readable symbolic model at a distance; zooming in reveals reported dimensions when known. Appearance and height are illustrative."), 15, Color(0xffb5c9d0))
            Label(tr("前视从本船的示意船桥位置向真实船首向观察。视点高度仅为呈现用途；无有效定位或船首向时暂用北向，恢复数据后轻点前视继续。", "Look ahead uses a perspective view from an illustrative bridge height and the real heading. Without position or heading, north up is used temporarily; tap look ahead to resume once data returns."), 15, Color(0xffb5c9d0))
            Label(tr("实线是已观测轨迹，虚线是 ${data.vectorSeconds.toInt()} 秒对地运动方向。船艏朝向只用船首向，不把对地航向当作船首向。", "Solid lines are observed tracks; dashed lines show ${data.vectorSeconds.toInt()} seconds of ground motion. Vessel orientation uses heading, never course over ground."), 15, Color(0xffb5c9d0))
            if (data.targets.size > 64) Label(tr("繁忙水域优先呈现选中、关注和风险船舶的立体模型，其余目标仍保留可点击的位置标记。", "In busy areas, selected, followed and risk vessels take priority for 3D models. Other targets keep tappable position markers."), 15, Color(0xffb5c9d0))
        }
        if (overlapIds.isNotEmpty()) Column(Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth().heightIn(max = 320.dp)
            .background(colors.panel).border(1.dp, colors.controlStroke).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(tr("选择船舶", "Choose a vessel"), 20, modifier = Modifier.weight(1f))
                SceneAction(tr("关闭", "Close"), enabled) { overlapIds = emptyList() }
            }
            LazyColumn {
                items(overlapIds, key = { it }) { id ->
                    val target = data.targets.firstOrNull { it.id == id }
                    Column(Modifier.fillMaxWidth().clickable(enabled = enabled && target != null) { overlapIds = emptyList(); onSelectTarget(id) }.padding(vertical = 12.dp)) {
                        Label(target?.label ?: id, 15)
                        Label(target?.let { listOf(it.ageLabel, it.statusLabel).filter(String::isNotBlank).joinToString(" · ") } ?: tr("此船已离开当前列表", "No longer in this list"), 12, colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneTool(icon: String, description: String, enabled: Boolean, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(48.dp).semantics { contentDescription = description; this.selected = selected }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        val tint = if (!enabled) Color(0xff6c808a) else if (selected) Color(0xff6dd4ff) else Color(0xffe1f3fa)
        if (icon == "boat" || icon == "heading" || icon == "info") Canvas(Modifier.size(24.dp)) {
            val unit = size.minDimension / 24f
            fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, point(x, y), point(x2, y2), 1.5f * unit)
            when (icon) {
                "boat" -> {
                    val hull = Path().apply { moveTo(12f * unit, 2f * unit); lineTo(20f * unit, 20f * unit); lineTo(12f * unit, 17f * unit); lineTo(4f * unit, 20f * unit); close() }
                    drawPath(hull, tint, style = Stroke(1.5f * unit))
                }
                "heading" -> { line(12f, 21f, 12f, 3f); line(5f, 10f, 12f, 3f); line(12f, 3f, 19f, 10f); line(4f, 21f, 20f, 21f) }
                else -> { drawCircle(tint, 9f * unit, center, style = Stroke(1.5f * unit)); line(12f, 11f, 12f, 17f); drawCircle(tint, 1f * unit, point(12f, 7f)) }
            }
        } else Glyph(icon, Modifier.size(24.dp), tint)
    }
}

@Composable
private fun SceneAction(text: String, enabled: Boolean, selected: Boolean = false, foreground: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalMetro.current
    Box(modifier.sizeIn(minHeight = 48.dp, minWidth = 40.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Label(text, 15, when { !enabled -> (foreground ?: colors.muted).copy(alpha = .5f); selected -> colors.accent; else -> foreground ?: colors.fg })
    }
}

@Composable
private fun NativeTrafficScene(
    data: AisSceneData, frame: AisSceneFrame, state: AisSceneCameraState, selectedId: String?, light: Boolean, active: Boolean, modifier: Modifier,
    onFailure: (String) -> Unit, onReady: () -> Unit, onPresentedTargets: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failure = rememberUpdatedState(onFailure)
    val ready = rememberUpdatedState(onReady)
    val presented = rememberUpdatedState(onPresentedTargets)
    val currentActive = rememberUpdatedState(active)
    val renderer = remember(context) { AisTrafficRenderer3D(context, { failure.value(it) }, { ready.value() }, { presented.value(it) }) }
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
            // 预组装的不可见应用不启动 Engine；可见后才创建小批实例。
            snapshotFlow { currentActive.value }.first { it }
            renderer.initialize()
            renderer.beginAssetRead()
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

/** 船体命中与 GPU 共用放大比例、天线偏移和高度，包括近裁面切过的可见部分。 */
private fun containsDisplayModel(target: AisSceneTarget, touch: Offset, viewport: IntSize, frame: AisSceneFrame, density: Float): Boolean {
    if (viewport.width <= 0 || viewport.height <= 0) return false
    return aisDisplayGeometry(target, frame, viewport.width, density).boundsFaces().any { face ->
        var polygon = face
        for ((boundary, isNear) in listOf((frame.camera.clipNear + .001) to true, (frame.camera.clipFar - .001) to false)) {
            if (polygon.isEmpty()) break
            val clipped = mutableListOf<AisVector3>()
            var last = polygon.last()
            var lastDepth = frame.camera.depth(last)
            for (next in polygon) {
                val nextDepth = frame.camera.depth(next)
                val previousInside = if (isNear) lastDepth >= boundary else lastDepth <= boundary
                val nextInside = if (isNear) nextDepth >= boundary else nextDepth <= boundary
                if (previousInside != nextInside) clipped += last + (next - last) * ((boundary - lastDepth) / (nextDepth - lastDepth))
                if (nextInside) clipped += next
                last = next; lastDepth = nextDepth
            }
            polygon = clipped
        }
        if (polygon.size < 3) return@any false
        val vertices = polygon.map { world -> frame.camera.project(world).let { Offset(it.x * viewport.width, it.y * viewport.height) } }
        var inside = false
        var previous = vertices.last()
        for (next in vertices) {
            if ((next.y > touch.y) != (previous.y > touch.y) && touch.x < (previous.x - next.x) * (touch.y - next.y) / (previous.y - next.y) + next.x) inside = !inside
            previous = next
        }
        inside
    }
}

/** 指北针独立于船首向，手动旋转后仍标记地理真北。 */
@Composable
private fun SceneCompass(frame: AisSceneFrame, modifier: Modifier) {
    val north = AisVector3(0.0, 0.0, -1.0)
    val direction = Offset(north.dot(frame.camera.right).toFloat(), -north.dot(frame.camera.screenUp).toFloat())
        .let { it / it.getDistance().coerceAtLeast(.00001f) }
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
    val reserved = listOf(
        Rect(0f, 0f, size.width.toFloat(), 60f * density),
        Rect(0f, size.height - 116f * density, size.width.toFloat(), size.height.toFloat()),
    )
    for (target in prioritizedTargets) {
        if (result.size >= 5) break
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
        fun path(points: List<AisVector3>): Path = Path().apply {
            points.zipWithNext().forEach { (start, end) ->
                frame.camera.clipSegment(start, end)?.let { (clippedStart, clippedEnd) ->
                    val from = project(clippedStart); val to = project(clippedEnd)
                    moveTo(from.x, from.y); lineTo(to.x, to.y)
                }
            }
        }
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
            val end = reference + AisVector3(sin(angle) * radius, 0.0, -cos(angle) * radius)
            drawPath(path(listOf(reference, end)), Color(0xff5e8a9b).copy(alpha = .28f), style = Stroke(1.dp.toPx()))
        }
        fun vector(point: AisVector3, cog: Double?, speed: Double?, color: Color) {
            val bearing = validAisBearing(cog) ?: return
            val velocity = speed?.takeIf { it.isFinite() && it > .05 && it < 100 } ?: return
            val distance = velocity * data.vectorSeconds.coerceIn(1.0, 900.0)
            val angle = Math.toRadians(bearing)
            val end = point + AisVector3(sin(angle) * distance, 0.0, -cos(angle) * distance)
            val segment = frame.camera.clipSegment(point, end) ?: return
            val from = project(segment.first); val to = project(segment.second)
            drawLine(color, from, to, 1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            val dir = to - from
            val length = dir.getDistance()
            if (length > 10.dp.toPx() && segment.second == end) {
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
            if (point.x.isFinite() && point.y.isFinite()) {
                drawCircle(Color.White, 4.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
                drawCircle(Color(0xff071720), 2.dp.toPx(), point)
            }
        }
    }
}

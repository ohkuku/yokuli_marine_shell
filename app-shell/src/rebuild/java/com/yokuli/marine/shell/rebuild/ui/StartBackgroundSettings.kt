package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.yokuli.marine.core.design.*
import com.yokuli.marine.shell.rebuild.*
import kotlin.math.roundToInt

private data class StartImageLoad(val name:String?,val image:androidx.compose.ui.graphics.ImageBitmap?=null,val finished:Boolean=false)

@Composable
fun rememberStartBackdrop(os: OsStore): StartBackdrop {
    val state by os.shell.persistence.state.collectAsState()
    val preferences = state?.appPreferenceValues.orEmpty()
    val name = preferences[START_IMAGE]
    val loaded by produceState(StartImageLoad(name),os,name) {
        value=StartImageLoad(name)
        if(name!=null) {
            val image=try {os.startBackground.load(name)?.asImageBitmap()} catch(cancelled:CancellationException){throw cancelled} catch(_:Exception){null}
            value=StartImageLoad(name,image,true)
        } else value=StartImageLoad(null,finished=true)
    }
    val image=loaded.takeIf {it.name==name}?.image
    val mode = preferences[START_MODE]?.let { runCatching { StartBackdropMode.valueOf(it) }.getOrNull() } ?: StartBackdropMode.NONE
    fun number(key:String,fallback:Float,range:ClosedFloatingPointRange<Float>)=preferences[key]?.toFloatOrNull()?.takeIf {it.isFinite()}?.coerceIn(range) ?: fallback
    return StartBackdrop(image,mode,number(START_OPACITY,.35f,0f..1f),number(START_CROP_SCALE,1f,1f..3f),
        number(START_FOCUS_X,.5f,0f..1f),number(START_FOCUS_Y,.5f,0f..1f),
        imageLoading=name!=null&&(loaded.name!=name||!loaded.finished),imageFailed=name!=null&&loaded.name==name&&loaded.finished&&image==null)
}

@Composable
fun StartBackgroundSettings(os: OsStore) {
    val current = LocalStartBackdrop.current
    val preferences by os.shell.persistence.state.collectAsState()
    val write by os.startBackground.write.collectAsState()
    var opacity by remember(current.tileOpacity) { mutableFloatStateOf(current.tileOpacity) }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(os.startBackground::choose) }
    val c = LocalMetro.current
    var adjusting by remember {mutableStateOf(false)}
    AppSection(os.t("你的开始屏幕", "make Start yours"))
    // 预览复用真实桌面投影，避免预览透明而桌面不透明的两套实现。
    CompositionLocalProvider(LocalStartBackdrop provides current.copy(tileOpacity = opacity)) {
        StartWallpaperSurface(Modifier.fillMaxWidth().height(210.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BackgroundPreviewTile("chart", os.t("海图", "Chart"), Modifier.weight(1f).fillMaxHeight())
                    BackgroundPreviewTile("anchor", os.t("守锚", "Anchor"), Modifier.weight(1f).fillMaxHeight())
                }
                BackgroundPreviewTile("log", os.t("航行日志", "Logbook"), Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
    MetroButton(os.t("选择背景照片", "choose a photo"), { choose.launch("image/*") }, enabled = !write.busy)
    if (preferences?.appPreferenceValues?.containsKey(START_IMAGE) == true) {
        if(current.imageLoading)MetroProgress(os.t("正在载入照片…","loading photo…"))
        if(current.imageFailed)Label(os.t("照片未能载入，请重新选择。","Your photo could not be loaded. Choose it again."),14,c.muted)
        MetroButton(os.t("调整照片位置","adjust photo position"),{adjusting=true},enabled=current.image!=null&&!write.busy)
        listOf(StartBackdropMode.FULL to os.t("全屏背景", "full-screen picture"),
            StartBackdropMode.TILES to os.t("磁贴内背景", "tile picture"), StartBackdropMode.NONE to os.t("纯色磁贴", "solid tiles")).forEach { (mode, label) ->
            ChoiceRow(label, current.mode == mode) { if (!write.busy) os.startBackground.mode(mode) }
        }
        if (current.mode != StartBackdropMode.NONE) {
            Label(os.t("磁贴透明度", "tile transparency") + " · ${(100 - opacity * 100).roundToInt()}%", 15)
            BackgroundOpacitySlider(1f - opacity, os.t("磁贴透明度", "tile transparency"), !write.busy,
                { opacity = 1f - it }, { os.startBackground.opacity(opacity) })
            Label(os.t("文字与实时内容保持清晰，背景随桌面轻轻移动。照片磁贴保留自己的画面。", "Text and live content stay clear as your background moves with Start. Photo tiles keep their own image."), 14, c.muted)
        }
        MetroButton(os.t("移除背景照片", "remove photo"), {os.startBackground.remove()}, enabled = !write.busy)
    }
    if(adjusting) {
        val imageName=preferences?.appPreferenceValues?.get(START_IMAGE)
        if(imageName!=null&&current.image!=null)StartPhotoFramingDialog(os,current,imageName) {adjusting=false}
    }
    if (write.busy) MetroProgress(os.t("正在保存开始屏幕…", "saving Start…"))
    if (write.failed) Label(os.t("未能保存更改，原背景仍然保留。请检查储存空间后重试。", "Could not save the change. Your previous background is kept. Check storage and try again."), 14, c.muted)
}

@Composable
private fun BackgroundPreviewTile(icon: String, label: String, modifier: Modifier) {
    val backdrop = LocalStartBackdrop.current
    val foreground = if (backdrop.image != null && backdrop.mode != StartBackdropMode.NONE && backdrop.tileOpacity < .7f) Color.White else LocalWpTheme.current.onAccent
    Box(modifier.clipToBounds().startTileBackground().padding(12.dp)) {
        Glyph(icon, Modifier.size(28.dp).align(Alignment.TopStart), foreground)
        Label(label, 15, foreground, Modifier.align(Alignment.BottomStart))
    }
}

/** MDL2 细轨道与矩形滑块，44dp 触控区；只在松手后提交系统偏好。 */
@Composable
private fun BackgroundOpacitySlider(value: Float, label: String, enabled: Boolean, onValue: (Float) -> Unit, onFinish: () -> Unit) {
    val c = LocalMetro.current
    var width by remember { mutableIntStateOf(1) }
    val update by rememberUpdatedState(onValue)
    val finish by rememberUpdatedState(onFinish)
    var dragValue by remember { mutableFloatStateOf(value) }
    var startValue by remember {mutableFloatStateOf(value)}
    val currentValue by rememberUpdatedState(value)
    Canvas(Modifier.fillMaxWidth().height(44.dp).onSizeChanged { width = it.width }
        .semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            if (!enabled) disabled()
            setProgress { requested -> if (enabled) { update(requested.coerceIn(0f, 1f)); finish(); true } else false }
        }
        .pointerInput(enabled, width) { if (enabled) detectTapGestures { update((it.x / width).coerceIn(0f, 1f)); finish() } }
        .pointerInput(enabled, width) { if (enabled) detectHorizontalDragGestures(
            onDragStart = { startValue=currentValue;dragValue = (it.x / width).coerceIn(0f, 1f); update(dragValue) },
            onDragEnd = { finish() }, onDragCancel = { update(startValue) },
        ) { change, delta -> change.consume(); dragValue = (dragValue + delta / width).coerceIn(0f, 1f); update(dragValue) } }) {
        val x = 4.dp.toPx() + (size.width - 8.dp.toPx()) * value
        drawLine(c.controlStroke, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx())
        drawLine(if (enabled) c.accentText else c.disabled, Offset(0f, center.y), Offset(x, center.y), 2.dp.toPx())
        drawRect(if (enabled) c.accentText else c.disabled, Offset(x - 4.dp.toPx(), center.y - 10.dp.toPx()), Size(8.dp.toPx(), 20.dp.toPx()))
    }
}

/** 保存前只改变本次取景草稿；拖动移动照片，捏合围绕双指中点缩放。 */
@Composable private fun StartPhotoFramingDialog(os:OsStore,original:StartBackdrop,imageName:String,dismiss:()->Unit) {
    var draft by remember(imageName) {mutableStateOf(original)}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var submitting by remember {mutableStateOf(false)}
    val write by os.startBackground.write.collectAsState()
    val scope=rememberCoroutineScope()
    val configuration=LocalConfiguration.current
    val ratio=(configuration.screenWidthDp.toFloat()/configuration.screenHeightDp.coerceAtLeast(1)).coerceIn(.4f,2f)
    val enabled=!submitting&&!write.busy
    AppDialog(onDismissRequest={if(!submitting)dismiss()}) {AppDialogSurface {
        AppDialogTitle(os.t("调整照片位置","frame your photo"))
        Label(os.t("拖动照片调整位置，双指缩放。保存后才应用到开始屏幕。","Drag to reposition and pinch to zoom. Changes apply to Start when you save."),14,LocalMetro.current.muted)
        BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            val previewWidth=minOf(maxWidth,360.dp*ratio)
            CompositionLocalProvider(LocalStartBackdrop provides draft.copy(mode=StartBackdropMode.FULL)) {
                StartWallpaperSurface(Modifier.width(previewWidth).height(previewWidth/ratio).onSizeChanged {viewport=it}
                    .semantics {contentDescription=os.t("照片取景，支持拖动和缩放","Photo framing, drag and pinch to zoom")}
                    .pointerInput(imageName,viewport,enabled) {
                        if(enabled)detectTransformGestures {centroid,pan,zoom,_->
                            if(viewport.width<=0||viewport.height<=0)return@detectTransformGestures
                            val old=draft
                            val before=startBackdropPlacement(old,viewport.toSize())
                            val next=old.copy(cropScale=(old.cropScale*zoom).coerceIn(1f,3f))
                            val after=startBackdropPlacement(next,viewport.toSize())
                            val overflow=after.overflow(viewport.toSize())
                            val imageX=(centroid.x-before.origin.x)/before.imageSize.width.coerceAtLeast(1)
                            val imageY=(centroid.y-before.origin.y)/before.imageSize.height.coerceAtLeast(1)
                            val nextX=centroid.x-imageX*after.imageSize.width+pan.x
                            val nextY=centroid.y-imageY*after.imageSize.height+pan.y
                            draft=next.copy(focusX=if(overflow.x>0)(-nextX/overflow.x).coerceIn(0f,1f)else .5f,
                                focusY=if(overflow.y>0)(-nextY/overflow.y).coerceIn(0f,1f)else .5f)
                        }
                    }) {
                    // 仅预览取景和真实透明底色；不拷贝或启动任何应用图表引擎。
                    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.height(80.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            BackgroundPreviewTile("chart",os.t("海图","Chart"),Modifier.weight(1f).fillMaxHeight())
                            BackgroundPreviewTile("anchor",os.t("守锚","Anchor"),Modifier.weight(1f).fillMaxHeight())
                        }
                        BackgroundPreviewTile("log",os.t("航行日志","Logbook"),Modifier.fillMaxWidth().height(80.dp))
                    }
                }
            }
        }
        // 等价按钮使不能捏合/拖动的用户也能完成同一取景。
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MetroButton(os.t("缩小","zoom out"),{draft=draft.copy(cropScale=(draft.cropScale-.1f).coerceAtLeast(1f))},Modifier.weight(1f),enabled=enabled&&draft.cropScale>1f)
            MetroButton(os.t("放大","zoom in"),{draft=draft.copy(cropScale=(draft.cropScale+.1f).coerceAtMost(3f))},Modifier.weight(1f),enabled=enabled&&draft.cropScale<3f)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(os.t("左移","left") to Offset(.05f,0f),os.t("上移","up") to Offset(0f,.05f),os.t("下移","down") to Offset(0f,-.05f),os.t("右移","right") to Offset(-.05f,0f)).forEach {(label,delta)->
                MetroButton(label,{draft=draft.copy(focusX=(draft.focusX+delta.x).coerceIn(0f,1f),focusY=(draft.focusY+delta.y).coerceIn(0f,1f))},Modifier.weight(1f),enabled=enabled)
            }
        }
        MetroButton(os.t("重新居中","centre photo"),{draft=draft.copy(cropScale=1f,focusX=.5f,focusY=.5f)},enabled=enabled)
        if(submitting)MetroProgress(os.t("正在保存取景…","saving photo framing…"))
        if(write.failed&&!submitting)Label(os.t("未能保存取景，调整仍保留在这里。","Could not save. Your framing remains here."),14,LocalMetro.current.muted)
        MetroButton(os.t("保存取景","save framing"),{
            submitting=true
            val submitted=draft
            val job=os.startBackground.crop(imageName,submitted.cropScale,submitted.focusX,submitted.focusY)
            scope.launch {job.join();submitting=false;if(!os.startBackground.write.value.failed)dismiss()}
        },primary=true,enabled=enabled)
        MetroButton(os.t("取消","cancel"),dismiss,enabled=!submitting)
    }}
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.core.design.StartTilePreviewSurface
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.startTileForeground
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.SailingStartPreset
import com.yokuli.marine.shell.rebuild.uid
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.LauncherRecoveryMode
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartLayoutCommitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 确认替换前只看预设；用户现有开始屏幕、背景及磁贴颜色完全不随预览改变。 */
@Composable internal fun SailingStartPresetSettings(os:OsStore) {
    val shell by os.shell.engine.state.collectAsState()
    val loaded by os.shell.persistence.loaded.collectAsState()
    val currentColumns=WpReferenceProfiles.require(shell.start.document.profileId).columnCount
    var columns by rememberSaveable {mutableIntStateOf(currentColumns)}
    var confirming by rememberSaveable {mutableStateOf(false)}
    var confirmedRevision by rememberSaveable {mutableLongStateOf(0L)}
    var requestId by rememberSaveable(columns) {mutableStateOf(uid())}
    var busy by remember {mutableStateOf(false)}
    var message by rememberSaveable {mutableStateOf<String?>(null)}
    var applied by rememberSaveable {mutableStateOf(false)}
    val ready=loaded&&shell.recoveryMode==LauncherRecoveryMode.NORMAL&&!busy
    val preview=remember(columns) {SailingStartPreset.document(columns)}
    PageBody {
        Label(os.t("出航常用内容，一眼看全。","The essentials for time under sail."),18)
        Label(os.t("海图、航向与风况、水深余量、船姿、气压趋势和当前任务。","Chart, course and wind, depth clearance, attitude, pressure history and current tasks."),13,LocalMetro.current.muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(4,6).forEach {count->
                ChoiceRow(os.t("$count 列","$count columns"),columns==count,enabled=!busy,
                    modifier=Modifier.weight(1f)) {columns=count;message=null;applied=false}
            }
        }
        Label(os.t("预览使用真实磁贴；没有数据时保留等待状态。","Real tiles are shown below; missing readings stay empty."),12,LocalMetro.current.muted)
        SailingStartPreview(os,preview)
        Label(os.t("海图册与我的航行放在一起；AIS、数据来源、海图下载和设置保留快捷入口。","Chart Library and My Sailing sit together, with shortcuts for AIS, sources, downloads and settings."),13,LocalMetro.current.muted)
        Label(os.t("只替换磁贴内容和排布，保留背景、颜色、海图、收藏与所有运行任务。","Replaces tile content and arrangement. Your wallpaper, colours, charts, saved places and running tasks stay as they are."),13,LocalMetro.current.muted)
        if(busy)MetroProgress(os.t("正在保存开始屏幕…","Saving Start…"))
        message?.let {Label(it,14)}
        MetroButton(os.t("使用帆船布局","Use sailing layout"),{
            confirmedRevision=shell.start.document.revision
            requestId=uid();confirming=true
        },primary=true,enabled=ready)
        if(applied)MetroButton(os.t("查看开始屏幕","View Start"),os.shell::home,enabled=!busy)
    }
    if(confirming)ConfirmDialog(os,
        os.t("替换现在的 ${shell.start.document.placements.size} 块磁贴？将使用预览中的 $columns 列帆船布局。",
            "Replace your current ${shell.start.document.placements.size} tiles with the $columns-column sailing layout shown above?"),
        onDismiss={confirming=false}) {
        confirming=false;busy=true;message=null;applied=false
        val expected=confirmedRevision;val selectedColumns=columns;val id=requestId
        os.scope.launch {
            try {when(val result=os.shell.applySailingStart(id,expected,selectedColumns)) {
                is StartLayoutCommitResult.Saved->{applied=true;message=os.t("帆船布局已保存。","Sailing layout saved.")}
                StartLayoutCommitResult.Conflict->message=os.t("开始屏幕刚刚有改动，请重新查看并确认替换。","Start changed while you were viewing this preview. Review it and confirm again.")
                is StartLayoutCommitResult.Failed->message=if(result.reason.contains("Finish editing"))
                    os.t("请先结束磁贴编辑，再应用布局。原有桌面保留。","Finish editing your tiles before applying this layout. Your current Start is kept.")
                    else os.t("布局未能保存，原有桌面保留。请稍后重试。","The layout could not be saved. Your current Start is kept. Retry shortly.")
            }}catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){message=os.t("布局未能保存，原有桌面保留。请重试。","The layout could not be saved. Your current Start is kept. Retry.")}
            finally{busy=false}
        }
    }
}

/** 使用真实 Start 的 packer、像素尺寸、6 列内容密度和磁贴 renderer，不绘制另一份示意读数。 */
@Composable private fun SailingStartPreview(os:OsStore,document:StartDocument) {
    val density=LocalDensity.current
    val view=LocalView.current
    val viewport=os.shell.tileWorkshop.viewport ?: remember(view.width,view.height,density) {
        StartViewport(view.width.coerceAtLeast((320*density.density).roundToInt()),
            view.height.coerceAtLeast((480*density.density).roundToInt()),density.density,0,0,density.fontScale)
    }
    val profile=WpReferenceProfiles.require(document.profileId)
    val geometry=remember(viewport,profile) {WpStartGeometryCalculator.calculate(viewport,profile)}
    val contentScale=remember(viewport,profile) {WpStartGeometryCalculator.tileContentScale(viewport,profile)}
    val contentDensity=remember(density,contentScale) {Density(density.density*contentScale,density.fontScale)}
    val packed=remember(document,geometry.columns) {AdaptiveTilePacker.pack(document,geometry.columns)}
    val active=LocalInternalAppInputEnabled.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    val c=LocalMetro.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.bg)) {
        val gridWidth=geometry.tileWidthPx(geometry.columns)
        val gridHeight=geometry.tileHeightPx(packed.documentHeightRows)
        val scale=(with(density) {maxWidth.toPx()}/gridWidth).coerceAtMost(1f)
        Layout(modifier=Modifier.fillMaxWidth().clipToBounds().semantics {contentDescription=os.t("帆船开始屏幕预览，不执行磁贴操作","Sailing Start preview; tile actions are disabled")},content={
            packed.tiles.forEach {placed->key(document.profileId,placed.entry.tileId) {
                val tile=placed.entry
                var visible by remember {mutableStateOf(false)}
                val live=active&&resumed&&visible
                val visual=instanceTilePresentation(os,tile,live)
                val pitch=geometry.smallCellPx+geometry.seamPx
                StartTilePreviewSurface(Size(viewport.widthPx.toFloat(),viewport.heightPx.toFloat()),
                    Offset((geometry.outerInsetsPx.left+placed.cell.column*pitch).toFloat(),(placed.cell.row*pitch).toFloat()),Modifier.fillMaxSize().onGloballyPositioned {
                        val bounds=it.boundsInWindow()
                        visible=bounds.width>0&&bounds.height>0&&bounds.bottom>0&&bounds.top<view.height
                    }) {
                    val foreground=startTileForeground()
                    CompositionLocalProvider(LocalDensity provides contentDensity) {
                        Box(Modifier.fillMaxSize().padding(if(visual.fullBleed&&tile.size!=MarineTileSize.ICON_1X1)0.dp
                            else if(tile.size==MarineTileSize.ICON_1X1)YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset)) {
                            visual.tileRenderers.getValue(tile.size).Render(LauncherTileRenderContext(tile.size,foreground,Modifier.fillMaxSize(),liveContentEnabled=live))
                        }
                    }
                }
            }}
        }) {children,constraints->
            val measured=children.mapIndexed {index,child->val tile=packed.tiles[index].entry
                child.measure(Constraints.fixed(geometry.tileWidthPx(tile.size.columns),geometry.tileHeightPx(tile.size.rows)))}
            layout(constraints.constrainWidth((gridWidth*scale).roundToInt()),(gridHeight*scale).roundToInt().coerceAtLeast(1)) {
                measured.forEachIndexed {index,child->val cell=packed.tiles[index].cell;val pitch=geometry.smallCellPx+geometry.seamPx
                    child.placeWithLayer((cell.column*pitch*scale).roundToInt(),(cell.row*pitch*scale).roundToInt()) {
                        transformOrigin=TransformOrigin(0f,0f);scaleX=scale;scaleY=scale
                    }
                }
            }
        }
    }
}

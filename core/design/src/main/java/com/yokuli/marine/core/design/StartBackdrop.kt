package com.yokuli.marine.core.design

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/** 开始屏幕唯一背景配置；图像已在宿主按内存上限解码，磁贴不各自加载位图。 */
enum class StartBackdropMode { NONE, FULL, TILES }
data class StartBackdrop(
    val image: ImageBitmap? = null,
    val mode: StartBackdropMode = StartBackdropMode.NONE,
    val tileOpacity: Float = .35f,
    /** 取景倍率与中心位置属于已确认的系统偏好；实时拖动草稿只留在设置。 */
    val cropScale: Float = 1f,
    val focusX: Float = .5f,
    val focusY: Float = .5f,
    val imageLoading: Boolean = false,
    val imageFailed: Boolean = false,
    val imageFailureReason:String?=null,
)
val LocalStartBackdrop = staticCompositionLocalOf { StartBackdrop() }
private class BackdropViewport(val sharedLauncher: Boolean = false) {
    var bounds by mutableStateOf(Rect.Zero)
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var scroll by mutableStateOf<() -> Float>({ 0f })
    var page: () -> Float = { 0f }
}
private val LocalBackdropViewport = staticCompositionLocalOf { BackdropViewport() }
/** 应用列表只在共同的 Launcher 宿主内透出背景；独立入口仍有自己的纯色底。 */
val LocalLauncherWallpaperProvided = staticCompositionLocalOf { false }

/** Start 与应用列表共享唯一壁纸。分页进度只在绘制阶段读取，不重组磁贴或列表。 */
@Composable
fun LauncherWallpaperSurface(
    modifier: Modifier = Modifier,
    pageFraction: () -> Float = { 0f },
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    WallpaperSurface(modifier, sharedLauncher = true, pageFraction = pageFraction, content = content)
}

/** 一张裁切后的照片贯穿所有磁贴；滚动只使绘制失效，不把逐帧状态传回 Shell。 */
@Composable
fun StartWallpaperSurface(
    modifier: Modifier = Modifier,
    scrollFraction: () -> Float = { 0f },
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val inherited = LocalBackdropViewport.current
    val reduced = LocalReducedMotion.current
    if (inherited.sharedLauncher) {
        // Start 在共同视口里提供纵向位置，离开此宿主时才释放；横向滑页不重新装载照片。
        val latestScroll = rememberUpdatedState(scrollFraction)
        val scrollSource = remember(reduced) { if (reduced) ({ 0f }) else ({ latestScroll.value() }) }
        DisposableEffect(inherited, scrollSource) {
            inherited.scroll = scrollSource
            onDispose { if (inherited.scroll === scrollSource) inherited.scroll = { 0f } }
        }
        BoxWithConstraints(modifier.clipToBounds(), content = content)
    } else WallpaperSurface(modifier, scrollFraction = scrollFraction, content = content)
}

@Composable
private fun WallpaperSurface(
    modifier: Modifier,
    sharedLauncher: Boolean = false,
    scrollFraction: () -> Float = { 0f },
    pageFraction: () -> Float = { 0f },
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val backdrop = LocalStartBackdrop.current
    val viewport = remember(sharedLauncher) { BackdropViewport(sharedLauncher) }
    val reduced = LocalReducedMotion.current
    val latestScroll = rememberUpdatedState(scrollFraction)
    val latestPage = rememberUpdatedState(pageFraction)
    if (!sharedLauncher) viewport.scroll = remember(reduced) { if (reduced) ({ 0f }) else ({ latestScroll.value() }) }
    viewport.page = remember(reduced) { if (reduced) ({ 0f }) else ({ latestPage.value() }) }
    val colors = LocalWpTheme.current
    val photo = backdrop.image != null && backdrop.mode == StartBackdropMode.FULL
    // 照片上统一使用暗色可读前景，包括应用列表的字母与菜单；不改用户保存的系统主题。
    val startColors = if (photo) WpThemePolicy.resolve(colors.spec.copy(mode = WpThemeMode.DARK), colors.accent)
        .copy(spec = colors.spec, muted = Color.White.copy(alpha = .75f)) else colors
    CompositionLocalProvider(LocalBackdropViewport provides viewport, LocalWpTheme provides startColors,
        LocalLauncherWallpaperProvided provides sharedLauncher) {
        BoxWithConstraints(modifier.clipToBounds().onGloballyPositioned { viewport.coordinates = it; viewport.bounds = Rect(Offset.Zero,it.size.toSize()) }.drawBehind {
            drawRect(colors.background)
            if (photo) drawBackdrop(backdrop, size, viewport.scroll(), Offset.Zero, viewport.page())
        }, content = content)
    }
}

/** 透明度只影响磁贴底色，文字、图标及实时海图保持完整清晰度。 */
@Composable
fun Modifier.startTileBackground(): Modifier {
    val backdrop = LocalStartBackdrop.current
    val viewport = LocalBackdropViewport.current
    val accent = LocalWpTheme.current.accent
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val frame = viewport.coordinates
    // 在 Start 自己的坐标系计算取景，整页 turnstile 不会让背景在磁贴内二次漂移。
    val positioned = if (backdrop.mode == StartBackdropMode.TILES) onGloballyPositioned {
        if (frame?.isAttached == true && it.isAttached) bounds = Rect(frame.localPositionOf(it,Offset.Zero),it.size.toSize())
    } else this
    return positioned.drawBehind {
        val image = backdrop.image
        if (image == null || backdrop.mode == StartBackdropMode.NONE) {
            drawRect(accent)
        } else {
            if (backdrop.mode == StartBackdropMode.TILES && viewport.bounds.width > 0 && viewport.bounds.height > 0) {
                drawBackdrop(backdrop, viewport.bounds.size, viewport.scroll(), bounds.topLeft - viewport.bounds.topLeft, viewport.page())
            }
            drawRect(accent.copy(alpha = backdrop.tileOpacity.coerceIn(0f, 1f)))
        }
    }
}

/** 预览、桌面和取景手势共用的照片几何，不会各算一种裁切。 */
data class StartBackdropPlacement(val origin:Offset,val imageSize:IntSize) {
    fun overflow(viewport:Size)=Offset((imageSize.width-viewport.width).coerceAtLeast(0f),(imageSize.height-viewport.height).coerceAtLeast(0f))
}
fun startBackdropPlacement(backdrop:StartBackdrop,viewport:Size,scroll:Float=0f,page:Float=0f):StartBackdropPlacement {
    val image=backdrop.image ?: return StartBackdropPlacement(Offset.Zero,IntSize.Zero)
    if(viewport.width<=0f||viewport.height<=0f)return StartBackdropPlacement(Offset.Zero,IntSize.Zero)
    val scale=max(viewport.width*1.06f/image.width,viewport.height*1.12f/image.height)*backdrop.cropScale.coerceIn(1f,3f)
    val width=(image.width*scale).roundToInt().coerceAtLeast(1)
    val height=(image.height*scale).roundToInt().coerceAtLeast(1)
    return StartBackdropPlacement(Offset(
        // 稍宽的取景给左右切页留余量。保留已保存焦点；焦点在边缘时减小位移，绝不露出黑边。
        ((viewport.width-width)*backdrop.focusX.coerceIn(0f,1f)-viewport.width*.025f*wallpaperPageProgress(page))
            .coerceIn((viewport.width-width).coerceAtMost(0f),0f),
        // 视差按视口封顶，放大照片或使用长图时也不会滚过数屏背景。
        ((viewport.height-height)*backdrop.focusY.coerceIn(0f,1f)-viewport.height*.08f*scroll.coerceIn(0f,1f))
            .coerceIn((viewport.height-height).coerceAtMost(0f),0f),
    ),IntSize(width,height))
}
private fun wallpaperPageProgress(page: Float): Float = page.coerceIn(0f,1f).let { it*it*(3f-2f*it) }

private fun DrawScope.drawBackdrop(backdrop:StartBackdrop, viewport: Size, scroll: Float, origin: Offset, page:Float=0f) {
    val image=backdrop.image ?: return
    if (viewport.width <= 0 || viewport.height <= 0) return
    val placement=startBackdropPlacement(backdrop,viewport,scroll,page)
    translate(-origin.x, -origin.y) {
        // 平移保留亚像素精度，微小视差不会按整数像素跳动，也不生成加宽的大位图。
        translate(placement.origin.x, placement.origin.y) {
            drawImage(image, dstOffset = IntOffset.Zero, dstSize = placement.imageSize, filterQuality = FilterQuality.Medium)
        }
        drawRect(Color.Black.copy(alpha = .55f+.08f*wallpaperPageProgress(page)), size = viewport)
    }
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.design.WpFontFamily
import com.yokuli.marine.core.design.WpTypeScale
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.contract.ShellSafeBands
import com.yokuli.shell.contract.ShellWindowMetrics
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 保留现有调用名，Shell 与 App 使用同一个 Windows 10 Mobile 语义调色板。 */
data class MetroColors(val bg: Color, val fg: Color, val muted: Color, val panel: Color, val accent: Color,
    val controlFill: Color = panel, val controlStroke: Color = muted, val pressed: Color = panel,
    val subtle: Color = panel, val disabled: Color = muted, val accentText: Color = accent, val onAccent: Color = Color.White)
val LocalMetro = staticCompositionLocalOf { MetroColors(Color.Black,Color.White,Color(0xFFAAAAAA),Color(0xFF191919),Color(0xFF00ABA9)) }
val LocalAppPage = staticCompositionLocalOf<String?> { null }
/** 页面位于状态栏下；通知覆盖位于窗口顶端。只收进左右边缘，不整体下移内容。 */
data class ShellHorizontalInsets(val pageStart: Dp = 22.dp, val pageEnd: Dp = 22.dp,
    val topStart: Dp = 22.dp, val topEnd: Dp = 22.dp)
val LocalShellHorizontalInsets = staticCompositionLocalOf { ShellHorizontalInsets() }
fun shellHorizontalInsets(metrics: ShellWindowMetrics): ShellHorizontalInsets {
    val density = metrics.density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val top = ShellSafeBands.horizontalInsets(metrics,metrics.safeInsets.top,metrics.safeInsets.top+(90f*density).roundToInt())
    val pageTop = metrics.safeInsets.top+(30f*density).roundToInt()
    val page = ShellSafeBands.horizontalInsets(metrics,pageTop,pageTop+(120f*density).roundToInt())
    fun safe(px: Int) = maxOf(22f,px/density+8f).dp
    return ShellHorizontalInsets(safe(page.left),safe(page.right),safe(top.left),safe(top.right))
}
private val LocalChinese = staticCompositionLocalOf { false }
val LightFont=WpFontFamily

/** Android 返回与虚拟返回使用同一任务激活约束，离场画面和通知背后的页面不抢键。 */
@Composable fun AppBackHandler(enabled:Boolean=true,onBack:()->Unit) {
    BindInternalAppInputHandler { input ->
        if(enabled && input == ShellInput.BACK) { onBack(); true } else false
    }
}

@Composable fun MetroTheme(os: OsStore, content: @Composable ()->Unit) {
    val theme = WpThemePolicy.resolve(WpThemeSpec(if(os.light) WpThemeMode.LIGHT else WpThemeMode.DARK), Color(os.accent))
    val colors = MetroColors(theme.background, theme.foreground, theme.muted, theme.chrome, theme.accent,
        theme.controlFill, theme.controlStroke, theme.pressed, theme.subtle, theme.disabled, theme.accentText, theme.onAccent)
    CompositionLocalProvider(LocalMetro provides colors, LocalWpTheme provides theme, LocalChinese provides os.chinese,
        LocalWpTextScale provides when(os.textSize) {"COMPACT" -> .92f;"LARGE" -> 1.12f;else -> 1f}) {
        Box(Modifier.fillMaxSize().background(colors.bg)) { content() }
    }
}
@Composable fun Label(text: String, size: Int=WpTypeScale.Body, color: Color=LocalMetro.current.fg, modifier: Modifier=Modifier,
    maxLines: Int=Int.MAX_VALUE, weight: FontWeight=WpTypeScale.weight(size), colorProducer: ColorProducer? = null) {
    val scale=LocalWpTextScale.current
    BasicText(text,modifier,style=TextStyle(color=color,fontSize=(size*scale).sp,fontFamily=LightFont,fontWeight=weight,
        lineHeight=(WpTypeScale.lineHeight(size)*scale).sp,textMotion=TextMotion.Animated),maxLines=maxLines,
        overflow=TextOverflow.Ellipsis,color=colorProducer)
}
/** 常规内容使用语义层级；仪表主读数可明确指定独立的大字号。 */
@Composable fun AppSection(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(title, WpTypeScale.SectionTitle, modifier = Modifier.semantics { heading() })
        if (!subtitle.isNullOrBlank()) Label(subtitle, WpTypeScale.Caption, LocalMetro.current.muted)
    }
}
@Composable fun AppDialogTitle(title: String, modifier: Modifier = Modifier) {
    Label(title, WpTypeScale.SectionTitle, modifier = modifier.semantics { heading() }, weight = FontWeight.SemiBold)
}
@Composable fun Glyph(name: String, modifier: Modifier=Modifier.size(24.dp), color: Color=LocalMetro.current.fg) {
    Canvas(modifier) {
        val sx=size.width/32; val sy=size.height/32
        fun point(x:Float,y:Float)=Offset(x*sx,y*sy)
        fun line(x:Float,y:Float,a:Float,b:Float,w:Float=2f)=drawLine(color,point(x,y),point(a,b),w*sx,StrokeCap.Square)
        fun circle(x:Float,y:Float,r:Float)=drawCircle(color,r*sx,point(x,y),style=Stroke(1.8f*sx))
        when(name) {
            "chevron_right" -> { line(12f,8f,20f,16f); line(20f,16f,12f,24f) }
            "chevron_down" -> { line(8f,12f,16f,20f); line(16f,20f,24f,12f) }
            "menu" -> { for(y in listOf(8f,16f,24f))line(4f,y,28f,y) }
            "warning" -> {
                line(16f,3f,2f,28f); line(2f,28f,30f,28f); line(30f,28f,16f,3f)
                line(16f,11f,16f,19f); drawCircle(color,1.1f*sx,point(16f,23f))
            }
            "back" -> { line(25f,16f,7f,16f); line(7f,16f,16f,7f); line(7f,16f,16f,25f) }
            "next" -> { line(7f,16f,25f,16f); line(25f,16f,16f,7f); line(25f,16f,16f,25f) }
            "start" -> { for(x in listOf(5f,18f)) for(y in listOf(5f,18f)) drawRect(color,point(x,y),androidx.compose.ui.geometry.Size(9*sx,9*sy)) }
            "search" -> { circle(13f,13f,8f); line(19f,19f,28f,28f,2.4f) }
            "ais" -> {circle(16f,16f,12f);circle(16f,16f,6f);line(16f,2f,16f,30f);line(2f,16f,30f,16f);drawCircle(color,2.5f*sx,point(23f,9f));drawCircle(color,2*sx,point(11f,21f))}
            "chart" -> { line(16f,3f,5f,24f); line(5f,24f,14f,24f); line(14f,24f,14f,5f); line(18f,10f,27f,24f); line(27f,24f,18f,24f); line(18f,24f,18f,10f); line(3f,28f,28f,28f) }
            "layers" -> { for(y in listOf(4f,11f,18f)) { line(3f,y+5,16f,y); line(16f,y,29f,y+5); line(29f,y+5,16f,y+10); line(16f,y+10,3f,y+5) } }
            "route" -> { circle(6f,25f,3f); circle(25f,7f,3f); line(9f,25f,23f,25f); line(23f,25f,12f,10f); line(12f,10f,22f,7f) }
            "data" -> { line(5f,26f,5f,17f,3f); line(12f,26f,12f,7f,3f); line(20f,26f,20f,13f,3f); line(27f,26f,27f,3f,3f) }
            "connect" -> { circle(8f,16f,4f); circle(25f,7f,3f); circle(25f,25f,3f); line(12f,14f,22f,8f); line(12f,18f,22f,24f) }
            "share" -> {line(5f,4f,18f,4f);line(5f,4f,5f,28f);line(5f,28f,18f,28f);line(18f,28f,18f,23f);line(18f,4f,18f,9f);line(12f,16f,29f,16f);line(29f,16f,24f,11f);line(29f,16f,24f,21f)}
            "helm" -> {circle(16f,16f,11f);line(16f,2f,16f,7f);line(16f,25f,16f,30f);line(2f,16f,7f,16f);line(25f,16f,30f,16f);line(16f,9f,12f,22f);line(12f,22f,21f,17f);line(21f,17f,16f,9f)}
            "settings" -> { circle(16f,16f,8f); circle(16f,16f,3f); for(d in 0..7) { val a=d*Math.PI/4; line(16+10*kotlin.math.cos(a).toFloat(),16+10*kotlin.math.sin(a).toFloat(),16+14*kotlin.math.cos(a).toFloat(),16+14*kotlin.math.sin(a).toFloat(),3f) } }
            "locate" -> { circle(16f,16f,8f); drawCircle(color,2*sx,point(16f,16f)); line(16f,2f,16f,7f); line(16f,25f,16f,30f); line(2f,16f,7f,16f); line(25f,16f,30f,16f) }
            "pin" -> { circle(16f,11f,7f); line(16f,18f,16f,29f); drawCircle(color,2*sx,point(16f,11f)) }
            "ruler" -> { line(3f,25f,28f,6f,3f); for(i in 0..4) line(7f+i*4,22f-i*3,4f+i*4,18f-i*3) }
            "plus" -> { line(16f,6f,16f,26f); line(6f,16f,26f,16f) }
            "minus" -> line(6f,16f,26f,16f)
            "check" -> { line(4f,16f,12f,25f,2.5f); line(12f,25f,28f,7f,2.5f) }
            "close" -> { line(7f,7f,25f,25f); line(7f,25f,25f,7f) }
            "more" -> for(x in listOf(7f,16f,25f)) drawCircle(color,2*sx,point(x,16f))
            "folder" -> { line(3f,8f,13f,8f); line(13f,8f,16f,12f); line(16f,12f,29f,12f); line(29f,12f,29f,26f); line(29f,26f,3f,26f); line(3f,26f,3f,8f) }
            "play" -> { val path=Path(); path.moveTo(10*sx,5*sy); path.lineTo(27*sx,16*sy); path.lineTo(10*sx,27*sy); path.close(); drawPath(path,color,style=Stroke(1.8f*sx)) }
            "stop" -> drawRect(color,point(8f,8f),androidx.compose.ui.geometry.Size(16*sx,16*sy),style=Stroke(1.8f*sx))
            "pause" -> {line(11f,7f,11f,25f,3f);line(21f,7f,21f,25f,3f)}
            "record" -> {circle(16f,16f,11f);drawCircle(color,5*sx,point(16f,16f))}
            "anchor" -> {circle(16f,5f,3f);line(16f,8f,16f,28f);line(8f,13f,24f,13f);line(5f,21f,9f,26f);line(9f,26f,16f,29f);line(16f,29f,23f,26f);line(23f,26f,27f,21f);line(5f,21f,5f,26f);line(27f,21f,27f,26f)}
            "sonar" -> {line(3f,7f,29f,7f);line(16f,7f,7f,23f);line(16f,7f,25f,23f);line(7f,23f,25f,23f);line(3f,29f,10f,27f);line(10f,27f,18f,30f);line(18f,30f,29f,26f)}
            "logbook" -> {line(7f,4f,27f,4f);line(27f,4f,27f,28f);line(27f,28f,7f,28f);line(7f,28f,7f,4f);line(11f,4f,11f,28f);line(15f,11f,23f,11f);line(15f,17f,23f,17f);line(15f,23f,21f,23f)}
            "undo" -> { line(4f,13f,25f,13f); line(25f,13f,25f,25f); line(4f,13f,11f,6f); line(4f,13f,11f,20f) }
            "export" -> { line(6f,18f,6f,28f); line(6f,28f,26f,28f); line(26f,28f,26f,18f); line(16f,22f,16f,3f); line(16f,3f,9f,10f); line(16f,3f,23f,10f) }
            else -> circle(16f,16f,10f)
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun IconAction(icon:String,label:String,onClick:()->Unit,modifier:Modifier=Modifier,active:Boolean=false,onLongClick:(()->Unit)?=null) {
    val colors=LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(modifier.widthIn(min=48.dp).heightIn(min=48.dp)
        .background(if(pressed) colors.pressed else Color.Transparent)
        .semantics { if(label.isNotBlank()) contentDescription=label; selected=active }
        .combinedClickable(interactionSource=interaction,indication=null,role=Role.Button,onClick=onClick,onLongClick=onLongClick)
        .padding(horizontal=8.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Glyph(icon,Modifier.size(24.dp),if(active) colors.accentText else colors.fg)
        if(label.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Label(label,12,if(active) colors.accentText else colors.fg,maxLines=2) }
    }
}
@Composable fun MetroButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,primary:Boolean=false,enabled:Boolean=true) {
    val c=LocalMetro.current
    val interaction=remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    val foreground = if(!enabled) c.disabled else if(primary) c.onAccent else c.fg
    val fill = when { !enabled -> c.subtle; pressed -> c.pressed; primary -> c.accent; else -> c.controlFill }
    Box(modifier.fillMaxWidth().heightIn(min=48.dp).background(fill)
        .border(2.dp,if(pressed && enabled) c.controlStroke else Color.Transparent)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,role=Role.Button,onClick=onClick)
        .padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
        Label(label,15,if(pressed && enabled) c.fg else foreground)
    }
}
@Composable fun PageHeader(os:OsStore,title:String,app:String="",trailing:(@Composable ()->Unit)?=null,hasLocalBack:Boolean=false,localBackLabel:String?=null) {
    val navigation=pageNavigation(os,title,hasLocalBack,localBackLabel)
    val caption=app.takeUnless { it.isBlank() || it.equals("YOKULI OS",true) || it.equals("YOKULI",true) } ?: navigation.appIdentity
    val insets=LocalShellHorizontalInsets.current
    val c=LocalMetro.current
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(start=insets.pageStart,end=insets.pageEnd,top=4.dp,bottom=8.dp),
        verticalAlignment=Alignment.CenterVertically) {
        if(navigation.canGoBack) HeaderBackButton(navigation,compact=true)
        Column(Modifier.weight(1f)) {
            // 次级页保留小号应用归属；不为返回和品牌各占一整行。
            if(caption.isNotBlank() && !caption.equals(title,ignoreCase=true))
                Label(caption,WpTypeScale.AppCaption,c.muted,maxLines=1)
            Label(title,WpTypeScale.PageTitle,modifier=Modifier.semantics { heading() },maxLines=2)
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}
@Composable internal fun HeaderBackButton(navigation:PageNavigation,compact:Boolean=false) {
    val c=LocalMetro.current
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(Modifier.heightIn(min=48.dp).widthIn(min=48.dp).background(if(pressed)c.pressed else Color.Transparent)
        .semantics { contentDescription=navigation.backLabel }
        .clickable(interactionSource=interaction,indication=null,enabled=navigation.enabled,role=Role.Button,onClickLabel=navigation.backLabel,onClick=navigation.back)
        .padding(end=if(compact)12.dp else 8.dp),verticalAlignment=Alignment.CenterVertically) {
        Glyph("back",Modifier.size(24.dp))
        if(!compact) Label(navigation.backLabel,15,modifier=Modifier.padding(start=8.dp),maxLines=1)
    }
}
@Composable fun PageBody(scrollState:ScrollState=rememberScrollState(),content:@Composable ColumnScope.()->Unit) {
    val insets=LocalShellHorizontalInsets.current
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable fun MenuRow(title:String,subtitle:String?=null,icon:String?=null,onClick:()->Unit) {
    val c=LocalMetro.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).background(if(pressed)c.pressed else Color.Transparent)
        .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClick=onClick)
        .padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        // 箭头属于导航提示，不再同时画左侧强调色方块和右侧箭头。
        if(icon!=null && icon!="next") { Glyph(icon,Modifier.size(24.dp),c.fg); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Label(title,WpTypeScale.ListTitle)
            if(!subtitle.isNullOrBlank()) Label(subtitle,WpTypeScale.Caption,c.muted,Modifier.padding(top=4.dp))
        }
        Spacer(Modifier.width(12.dp)); Glyph("chevron_right",Modifier.size(16.dp),c.muted)
    }
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit,number:Boolean=false,multiline:Boolean=false) {
    val c=LocalMetro.current
    var focused by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Label(label,15)
        BasicTextField(value,onChange,Modifier.fillMaxWidth().heightIn(min=48.dp).onFocusChanged {focused=it.isFocused}
            .semantics { contentDescription=label }
            .background(if(focused)c.bg else c.subtle).border(2.dp,if(focused)c.accent else c.controlStroke)
            .padding(horizontal=12.dp,vertical=12.dp),
            textStyle=TextStyle(color=c.fg,fontSize=(15*LocalWpTextScale.current).sp,lineHeight=(20*LocalWpTextScale.current).sp,fontFamily=LightFont),
            cursorBrush=SolidColor(c.accentText),singleLine=!multiline,
            keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Decimal else KeyboardType.Text))
    }
}
@Composable fun Toggle(title:String,checked:Boolean,subtitle:String?=null,enabled:Boolean=true,onChange:(Boolean)->Unit) {
    val c=LocalMetro.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).background(if(pressed)c.pressed else Color.Transparent)
        .toggleable(value=checked,enabled=enabled,role=Role.Switch,interactionSource=interaction,indication=null,onValueChange=onChange)
        .padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=16.dp)) {
            Label(title,WpTypeScale.ListTitle,if(enabled)c.fg else c.disabled)
            if(subtitle!=null) Label(subtitle,WpTypeScale.Caption,if(enabled)c.muted else c.disabled,Modifier.padding(top=4.dp))
        }
        Label(if(LocalChinese.current) {if(checked) "开启" else "关闭"} else {if(checked) "On" else "Off"},
            12,if(enabled)c.muted else c.disabled,Modifier.padding(end=12.dp),maxLines=1)
        W10ToggleIndicator(checked,enabled)
    }
}
/** Windows 10 单选：整行拥有唯一单选语义；指标只负责显示真实状态。 */
@Composable fun ChoiceRow(title:String,selected:Boolean,subtitle:String?=null,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=LocalMetro.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    Row(modifier.fillMaxWidth().heightIn(min=48.dp).background(if(pressed)c.pressed else Color.Transparent)
        .selectable(selected=selected,enabled=enabled,role=Role.RadioButton,interactionSource=interaction,indication=null,onClick=onClick)
        .padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        W10RadioIndicator(selected,enabled)
        Column(Modifier.weight(1f)) {
            Label(title,WpTypeScale.ListTitle,if(enabled)c.fg else c.disabled)
            if(subtitle!=null)Label(subtitle,WpTypeScale.Caption,if(enabled)c.muted else c.disabled,Modifier.padding(top=4.dp))
        }
    }
}
@Composable fun Pivot(labels:List<String>,initialPage:Int=0,onPageSelected:((Int)->Unit)?=null,content:@Composable (Int)->Unit) {
    if(labels.isEmpty())return
    val pager=rememberPagerState(initialPage=initialPage.coerceIn(labels.indices)) { labels.size }
    val reportPage=rememberUpdatedState(onPageSelected)
    LaunchedEffect(pager) { snapshotFlow {pager.settledPage}.distinctUntilChanged().collect {reportPage.value?.invoke(it)} }
    Column(Modifier.fillMaxSize()) {
        PivotHeaders(labels,pager)
        HorizontalPager(pager,Modifier.weight(1f),verticalAlignment=Alignment.Top) { page ->
            val parentActive = com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
            CompositionLocalProvider(com.yokuli.shell.compose.LocalInternalAppInputEnabled provides (parentActive && page == pager.settledPage)) { content(page) }
        }
    }
}

/**
 * 标题放得下时保持静止；溢出时保留固定顺序，只滚动到足以完整显示新选中标题。
 * 不把页内拖动进度绑定到标题偏移，不补齐“选中项必须在最左侧”的尾部空白。
 * 标题状态在这里读取，避免一次滑动的每帧都重新执行整个页面的内容组合。
 */
@Composable internal fun PivotHeaders(labels:List<String>,pager:PagerState,compact:Boolean=false) {
    val inputEnabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    val headerScroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    val insets=LocalShellHorizontalInsets.current
    val textScale=LocalWpTextScale.current
    val widths=remember(labels,density.density,density.fontScale,textScale) { mutableStateMapOf<Int,Int>() }
    var viewportWidth by remember { mutableIntStateOf(0) }
    val selectedIndex by remember(pager) { derivedStateOf {pager.currentPage} }
    val measuredWidths=labels.indices.map { widths[it] ?: 0 }
    val gap=with(density) {22.dp.roundToPx()}
    val maxScroll=headerScroll.maxValue
    LaunchedEffect(selectedIndex,measuredWidths,viewportWidth,gap,maxScroll) {
        if(viewportWidth<=0 || measuredWidths.any {it<=0} || selectedIndex !in labels.indices) return@LaunchedEffect
        val selectedStart=measuredWidths.take(selectedIndex).sum()+gap*selectedIndex
        val selectedEnd=selectedStart+measuredWidths[selectedIndex]
        val visibleStart=headerScroll.value
        val visibleEnd=visibleStart+viewportWidth
        val destination=when {
            selectedStart<visibleStart -> selectedStart
            selectedEnd>visibleEnd -> selectedEnd-viewportWidth
            else -> visibleStart
        }.coerceIn(0,maxScroll)
        if(destination!=visibleStart) headerScroll.animateScrollTo(destination,tween(240,easing=FastOutSlowInEasing))
    }
    Row(
        Modifier.fillMaxWidth().padding(start=insets.pageStart,end=insets.pageEnd)
            .clipToBounds().onSizeChanged {viewportWidth=it.width}
            .horizontalScroll(headerScroll,enabled=inputEnabled).selectableGroup().padding(bottom=if(compact)0.dp else 11.dp),
        horizontalArrangement=Arrangement.spacedBy(22.dp),
    ) {
        labels.forEachIndexed {index,name ->
            PivotHeader(name,index==selectedIndex,
                Modifier.widthIn(max=if(viewportWidth>0)with(density){viewportWidth.toDp()}else Dp.Infinity)
                    .onSizeChanged {widths[index]=it.width},
                compact=compact,
            ) {
                scope.launch {pager.animateScrollToPage(index,animationSpec=tween(260,easing=FastOutSlowInEasing))}
            }
        }
    }
}

@Composable private fun PivotHeader(name:String,selected:Boolean,modifier:Modifier,compact:Boolean=false,onClick:()->Unit) {
    val colors=LocalMetro.current
    val inputEnabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    // 仅标题的明暗做短过渡；既保留 Metro 选中反馈，也不驱动页面布局逐帧重排。
    val color by androidx.compose.animation.animateColorAsState(
        if(selected)colors.fg else colors.muted,tween(130),label="pivot-header-color",
    )
    Box(modifier.heightIn(min=if(compact)44.dp else 48.dp).selectable(selected=selected,enabled=inputEnabled,role=Role.Tab,onClick=onClick)
        .padding(vertical=3.dp),contentAlignment=Alignment.CenterStart) {
        // 文本颜色由绘制节点读取；淡入一帧不重建 TextStyle、不重新布局整排标题。
        Label(name,if(compact)20 else WpTypeScale.PivotTitle,maxLines=1,colorProducer={color})
    }
}

/** WP 的不定进度沿水平方向流动，保留上下文，不覆盖用户操作。 */
@Composable fun MetroProgress(label:String,modifier:Modifier=Modifier) {
    val animated=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current && !LocalReducedMotion.current
    val transition=if(animated)rememberInfiniteTransition(label="metro-progress")else null
    val phase=transition?.animateFloat(0f,1f,infiniteRepeatable(tween(2100,easing=LinearEasing)),label="progress-dots")
    val accent=LocalMetro.current.accentText
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(6.dp).semantics { progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate }) {
            repeat(5) {index->
                val t=((phase?.value ?: .55f)-index*.075f+1f)%1f
                val x=(t*t*(3f-2f*t)*1.35f-.175f)*size.width
                if(x in 0f..size.width)drawCircle(accent,2.dp.toPx(),Offset(x,size.height/2))
            }
        }
        Label(label,WpTypeScale.Body,LocalMetro.current.muted)
    }
}
@Composable fun TextDialog(os:OsStore,title:String,initial:String="",onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AppDialog(onDismissRequest=onDismiss) {
        AppDialogSurface {
            AppDialogTitle(title)
            Field(os.t("名称","name"),text,{text=it.take(100)})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MetroButton(os.t("保存","save"),{ onSave(text.trim()); onDismiss() },Modifier.weight(1f),primary=true,enabled=text.isNotBlank())
                MetroButton(os.t("取消","cancel"),onDismiss,Modifier.weight(1f))
            }
        }
    }
}

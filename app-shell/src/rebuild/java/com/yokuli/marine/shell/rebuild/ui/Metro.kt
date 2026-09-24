package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import com.yokuli.marine.core.design.WpFontFamily
import com.yokuli.marine.core.design.WpTypeScale
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.contract.ShellSafeBands
import com.yokuli.shell.contract.ShellWindowMetrics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class MetroColors(val bg: Color, val fg: Color, val muted: Color, val panel: Color, val accent: Color)
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
    val colors=if(os.light) MetroColors(Color(0xFFF7F7F5),Color(0xFF111111),Color(0xFF61615D),Color(0xFFE7E7E2),Color(os.accent))
        else MetroColors(Color.Black,Color.White,Color(0xFFAAAAAA),Color(0xFF191919),Color(os.accent))
    CompositionLocalProvider(LocalMetro provides colors, LocalChinese provides os.chinese, LocalWpTextScale provides when(os.textSize) {"COMPACT" -> .92f;"LARGE" -> 1.12f;else -> 1f}) { Box(Modifier.fillMaxSize().background(colors.bg)) { content() } }
}
@Composable fun Label(text: String, size: Int=WpTypeScale.Body, color: Color=LocalMetro.current.fg, modifier: Modifier=Modifier, maxLines: Int=Int.MAX_VALUE, weight: FontWeight=if(size>=28) FontWeight.Light else FontWeight.Normal) {
    val scale=LocalWpTextScale.current
    BasicText(text,modifier,style=TextStyle(color=color,fontSize=(size*scale).sp,fontFamily=LightFont,fontWeight=weight,lineHeight=(size*scale*1.18).sp,textMotion=TextMotion.Animated),maxLines=maxLines,overflow=TextOverflow.Ellipsis)
}
@Composable fun Glyph(name: String, modifier: Modifier=Modifier.size(28.dp), color: Color=LocalMetro.current.fg) {
    Canvas(modifier) {
        val sx=size.width/32; val sy=size.height/32
        fun point(x:Float,y:Float)=Offset(x*sx,y*sy)
        fun line(x:Float,y:Float,a:Float,b:Float,w:Float=1.8f)=drawLine(color,point(x,y),point(a,b),w*sx,StrokeCap.Square)
        fun circle(x:Float,y:Float,r:Float)=drawCircle(color,r*sx,point(x,y),style=Stroke(1.8f*sx))
        when(name) {
            "back" -> { line(25f,16f,7f,16f); line(7f,16f,16f,7f); line(7f,16f,16f,25f) }
            "next" -> { line(7f,16f,25f,16f); line(25f,16f,16f,7f); line(25f,16f,16f,25f) }
            "start" -> { for(x in listOf(5f,18f)) for(y in listOf(5f,18f)) drawRect(color,point(x,y),androidx.compose.ui.geometry.Size(9*sx,9*sy)) }
            "search" -> { circle(13f,13f,8f); line(19f,19f,28f,28f,2.4f) }
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
    Column(modifier.widthIn(min=52.dp).heightIn(min=52.dp).combinedClickable(onClick=onClick,onLongClick=onLongClick).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Box(Modifier.size(32.dp).border(1.5.dp,if(active) colors.accent else colors.fg,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) { Glyph(icon,Modifier.size(21.dp),if(active) colors.accent else colors.fg) }
        if(label.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Label(label,11,if(active) colors.accent else colors.fg,maxLines=1) }
    }
}
@Composable fun MetroButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,primary:Boolean=false,enabled:Boolean=true) {
    val c=LocalMetro.current; val interaction=remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .97f else 1f,label="button")
    Box(modifier.fillMaxWidth().heightIn(min=48.dp).graphicsLayer { scaleX=scale; scaleY=scale; alpha=if(enabled) 1f else .4f }
        .background(if(primary) c.accent else Color.Transparent).border(2.dp,if(primary) c.accent else c.fg)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,onClick=onClick).padding(horizontal=15.dp,vertical=11.dp),contentAlignment=Alignment.Center) {
        Label(label,18,if(primary) Color.White else c.fg)
    }
}
@Composable fun PageHeader(os:OsStore,title:String,app:String="",trailing:(@Composable ()->Unit)?=null,hasLocalBack:Boolean=false) {
    val navigation=pageNavigation(os,title,hasLocalBack)
    val caption=app.takeUnless { it.isBlank() || it.equals("YOKULI OS",true) || it.equals("YOKULI",true) } ?: navigation.appIdentity
    val insets=LocalShellHorizontalInsets.current
    Column(Modifier.fillMaxWidth().padding(start=insets.pageStart,end=insets.pageEnd,top=6.dp,bottom=10.dp)) {
        if(navigation.canGoBack || trailing!=null) Row(verticalAlignment=Alignment.CenterVertically) {
            if(navigation.canGoBack) HeaderBackButton(navigation)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        // 应用身份独立于返回按钮；首页标题已经说明身份时，不塞系统品牌占位。
        if(caption.isNotBlank() && !caption.equals(title,ignoreCase=true))
            Label(caption.uppercase(),WpTypeScale.AppCaption,weight=FontWeight.SemiBold,maxLines=1)
        Label(title,WpTypeScale.PageTitle,modifier=Modifier.padding(top=4.dp),maxLines=2)
    }
}
@Composable internal fun HeaderBackButton(navigation:PageNavigation,compact:Boolean=false) {
    Row(Modifier.heightIn(min=44.dp).semantics { contentDescription=navigation.backLabel }.clickable(enabled=navigation.enabled,onClickLabel=navigation.backLabel,onClick=navigation.back)
        .padding(end=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Glyph("back",Modifier.size(24.dp))
        if(!compact) Label(navigation.backLabel,16,modifier=Modifier.padding(start=6.dp),maxLines=1)
    }
}
@Composable fun PageBody(scrollState:ScrollState=rememberScrollState(),content:@Composable ColumnScope.()->Unit) {
    val insets=LocalShellHorizontalInsets.current
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
}
@Composable fun MenuRow(title:String,subtitle:String?=null,icon:String?=null,onClick:()->Unit) {
    val interaction=remember {MutableInteractionSource()}
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).wpTilt(interaction).clickable(interactionSource=interaction,indication=null,onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        if(icon!=null) { Box(Modifier.size(36.dp).background(LocalMetro.current.accent),contentAlignment=Alignment.Center) { Glyph(icon,Modifier.size(24.dp),color=Color.White) }; Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) { Label(title,WpTypeScale.ListTitle); if(!subtitle.isNullOrBlank()) Label(subtitle,WpTypeScale.Caption,LocalMetro.current.muted,Modifier.padding(top=4.dp)) }
        Glyph("next",Modifier.size(18.dp),LocalMetro.current.muted)
    }
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit,number:Boolean=false,multiline:Boolean=false) {
    val c=LocalMetro.current
    var focused by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Label(label,14,c.muted)
        BasicTextField(value,onChange,Modifier.fillMaxWidth().heightIn(min=46.dp).onFocusChanged {focused=it.isFocused}
            .background(if(focused)Color.White else Color(0xFFE4E4E4)).border(2.dp,if(focused)c.accent else Color.Transparent).padding(horizontal=12.dp,vertical=9.dp),
            textStyle=TextStyle(color=Color.Black,fontSize=(18*LocalWpTextScale.current).sp,fontFamily=LightFont),cursorBrush=SolidColor(c.accent),singleLine=!multiline,
            keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Decimal else KeyboardType.Text))
    }
}
@Composable fun Toggle(title:String,checked:Boolean,subtitle:String?=null,enabled:Boolean=true,onChange:(Boolean)->Unit) {
    val c=LocalMetro.current
    val thumb by animateFloatAsState(if(checked)1f else 0f,animationSpec=tween(130),label="switch-thumb")
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).graphicsLayer { alpha=if(enabled) 1f else .4f }.toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onChange).padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=16.dp)) {
            Label(title,WpTypeScale.ListTitle)
            Label(if(LocalChinese.current) {if(checked) "开启" else "关闭"} else {if(checked) "on" else "off"},WpTypeScale.Caption,if(checked)c.accent else c.muted,Modifier.padding(top=3.dp))
            if(subtitle!=null) Label(subtitle,WpTypeScale.Caption,c.muted,Modifier.padding(top=5.dp))
        }
        Box(Modifier.size(60.dp,34.dp),contentAlignment=Alignment.CenterStart) {
            Box(Modifier.width(60.dp).height(24.dp).border(2.dp,c.fg).padding(5.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(thumb).background(c.accent))
            }
            Box(Modifier.offset(x=(46*thumb).dp).width(14.dp).height(32.dp).background(c.fg).border(2.dp,c.bg))
        }
    }
}
/** WP 单选行：行本身可点选，状态由真实选中值决定，不用按钮颜色冒充单选。 */
@Composable fun ChoiceRow(title:String,selected:Boolean,subtitle:String?=null,enabled:Boolean=true,onClick:()->Unit) {
    val c=LocalMetro.current
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).graphicsLayer {alpha=if(enabled)1f else .4f}
        .selectable(selected=selected,enabled=enabled,role=Role.RadioButton,onClick=onClick).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).border(2.dp,c.fg,androidx.compose.foundation.shape.CircleShape).padding(6.dp)) {
            if(selected)Box(Modifier.fillMaxSize().background(c.accent,androidx.compose.foundation.shape.CircleShape))
        }
        Column(Modifier.weight(1f)) {Label(title,WpTypeScale.ListTitle);if(subtitle!=null)Label(subtitle,WpTypeScale.Caption,c.muted,Modifier.padding(top=4.dp))}
    }
}
@Composable fun Pivot(labels:List<String>,initialPage:Int=0,onPageSelected:((Int)->Unit)?=null,content:@Composable (Int)->Unit) {
    if(labels.isEmpty())return
    val pager=rememberPagerState(initialPage=initialPage.coerceIn(labels.indices)) { labels.size }
    val scope=rememberCoroutineScope(); val c=LocalMetro.current
    val headerScroll=rememberScrollState()
    val widths=remember(labels) {mutableStateMapOf<Int,Int>()}
    val density=LocalDensity.current
    val insets=LocalShellHorizontalInsets.current
    val reportPage=rememberUpdatedState(onPageSelected)
    LaunchedEffect(pager) { snapshotFlow {pager.currentPage}.distinctUntilChanged().collect {reportPage.value?.invoke(it)} }
    val gap=with(density){22.dp.toPx()}
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 标题条和内容共享同一个连续页位置；拖动到一半，标题也移动到一半。
            // 尾部留白使最后一个标题也能停在左侧，不会被挤在屏幕右缘。
            val tail=with(density){(constraints.maxWidth-insets.pageStart.toPx()-(widths[labels.lastIndex] ?: 0)).coerceAtLeast(0f).toDp()}
            LaunchedEffect(pager,headerScroll,labels,gap) {
                snapshotFlow {
                    val position=(pager.currentPage+pager.currentPageOffsetFraction).coerceIn(0f,labels.lastIndex.toFloat())
                    val index=position.toInt()
                    val prefix=(0 until index).sumOf {widths[it] ?: 0}+gap*index
                    (prefix+((widths[index] ?: 0)+gap)*(position-index)).roundToInt().coerceIn(0,headerScroll.maxValue)
                }.distinctUntilChanged().collectLatest {headerScroll.scrollTo(it)}
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(headerScroll).padding(start=insets.pageStart,end=tail,bottom=14.dp),horizontalArrangement=Arrangement.spacedBy(22.dp)) {
                val position=pager.currentPage+pager.currentPageOffsetFraction
                labels.forEachIndexed { i,name ->
                    Label(name,WpTypeScale.PivotTitle,lerp(c.muted,c.fg,1f-abs(position-i).coerceIn(0f,1f)),
                        Modifier.onSizeChanged {widths[i]=it.width}.clickable {
                            scope.launch {pager.animateScrollToPage(i,animationSpec=tween(260,easing=FastOutSlowInEasing))}
                        },maxLines=1)
                }
            }
        }
        HorizontalPager(pager,Modifier.weight(1f),verticalAlignment=Alignment.Top) { content(it) }
    }
}

/** WP 的不定进度沿水平方向流动，保留上下文，不覆盖用户操作。 */
@Composable fun MetroProgress(label:String,modifier:Modifier=Modifier) {
    val transition=rememberInfiniteTransition(label="metro-progress")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(2100,easing=LinearEasing)),label="progress-dots")
    val accent=LocalMetro.current.accent
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            repeat(5) {index->
                val t=(phase-index*.075f+1f)%1f
                val x=(t*t*(3f-2f*t)*1.35f-.175f)*size.width
                if(x in 0f..size.width)drawCircle(accent,2.dp.toPx(),Offset(x,size.height/2))
            }
        }
        Label(label,WpTypeScale.Body,LocalMetro.current.muted)
    }
}
@Composable fun TextDialog(os:OsStore,title:String,initial:String="",onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).border(1.dp,LocalMetro.current.muted).padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Label(title,31); Field(os.t("名称","name"),text,{text=it.take(100)})
            MetroButton(os.t("保存","save"),{ onSave(text.trim()); onDismiss() },primary=true,enabled=text.isNotBlank())
            MetroButton(os.t("取消","cancel"),onDismiss)
        }
    }
}

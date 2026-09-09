package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.OsStore
import kotlinx.coroutines.launch

data class MetroColors(val bg: Color, val fg: Color, val muted: Color, val panel: Color, val accent: Color)
val LocalMetro = staticCompositionLocalOf { MetroColors(Color.Black,Color.White,Color(0xFFAAAAAA),Color(0xFF191919),Color(0xFF00ABA9)) }
val LightFont=FontFamily(androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL)))

@Composable fun MetroTheme(os: OsStore, content: @Composable ()->Unit) {
    val colors=if(os.light) MetroColors(Color(0xFFF7F7F5),Color(0xFF111111),Color(0xFF61615D),Color(0xFFE7E7E2),Color(os.accent))
        else MetroColors(Color.Black,Color.White,Color(0xFFAAAAAA),Color(0xFF191919),Color(os.accent))
    CompositionLocalProvider(LocalMetro provides colors) { Box(Modifier.fillMaxSize().background(colors.bg)) { content() } }
}
@Composable fun Label(text: String, size: Int=18, color: Color=LocalMetro.current.fg, modifier: Modifier=Modifier, maxLines: Int=Int.MAX_VALUE, weight: FontWeight=FontWeight.Light) {
    BasicText(text,modifier,style=TextStyle(color=color,fontSize=size.sp,fontFamily=LightFont,fontWeight=weight,lineHeight=(size*1.18).sp),maxLines=maxLines,overflow=TextOverflow.Ellipsis)
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
        Label(label,19,if(primary) Color.White else c.fg)
    }
}
@Composable fun PageHeader(os:OsStore,title:String,app:String="YOKULI OS",trailing:(@Composable ()->Unit)?=null,onBack:(()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().padding(start=22.dp,end=16.dp,top=10.dp,bottom=14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { (onBack ?: os::back)() },contentAlignment=Alignment.CenterStart) {
                Box(Modifier.size(30.dp).border(1.5.dp,LocalMetro.current.fg,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) { Glyph("back",Modifier.size(20.dp)) }
            }
            Label(app,12,weight=FontWeight.Medium,modifier=Modifier.weight(1f),maxLines=1)
            trailing?.invoke()
        }
        Label(title,46,modifier=Modifier.padding(top=6.dp),maxLines=2)
    }
}
@Composable fun PageBody(scrollState:ScrollState=rememberScrollState(),content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
@Composable fun MenuRow(title:String,subtitle:String?=null,icon:String?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(icon!=null) { Box(Modifier.size(44.dp).background(LocalMetro.current.accent),contentAlignment=Alignment.Center) { Glyph(icon,color=Color.White) }; Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) { Label(title,25); if(!subtitle.isNullOrBlank()) Label(subtitle,14,LocalMetro.current.muted,Modifier.padding(top=5.dp)) }
        Glyph("next",Modifier.size(18.dp),LocalMetro.current.muted)
    }
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit,number:Boolean=false,multiline:Boolean=false) {
    val c=LocalMetro.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Label(label,14,c.muted)
        BasicTextField(value,onChange,Modifier.fillMaxWidth().background(c.panel).border(1.dp,c.muted).padding(12.dp),
            textStyle=TextStyle(color=c.fg,fontSize=21.sp,fontFamily=LightFont),cursorBrush=SolidColor(c.accent),singleLine=!multiline,
            keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Number else KeyboardType.Text))
    }
}
@Composable fun Toggle(title:String,checked:Boolean,subtitle:String?=null,enabled:Boolean=true,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().graphicsLayer { alpha=if(enabled) 1f else .4f }.clickable(enabled=enabled) { onChange(!checked) }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) { Label(title,23); if(subtitle!=null) Label(subtitle,14,LocalMetro.current.muted,Modifier.padding(top=5.dp)) }
        Box(Modifier.size(52.dp,26.dp).border(2.dp,LocalMetro.current.fg).padding(4.dp)) {
            if(checked) Box(Modifier.fillMaxSize().background(LocalMetro.current.accent))
            Box(Modifier.width(12.dp).fillMaxHeight().align(if(checked) Alignment.CenterEnd else Alignment.CenterStart).background(LocalMetro.current.fg))
        }
    }
}
@Composable fun Pivot(labels:List<String>,content:@Composable (Int)->Unit) {
    val pager=rememberPagerState { labels.size }; val scope=rememberCoroutineScope(); val c=LocalMetro.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start=22.dp,bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(22.dp)) {
            labels.forEachIndexed { i,name -> Label(name,29,if(pager.currentPage==i) c.fg else c.muted,Modifier.clickable { scope.launch { pager.animateScrollToPage(i) } }) }
        }
        HorizontalPager(pager,Modifier.weight(1f),verticalAlignment=Alignment.Top) { content(it) }
    }
}
@Composable fun TextDialog(os:OsStore,title:String,initial:String="",onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).border(1.dp,LocalMetro.current.muted).padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Label(title,31); Field(os.t("名称","name"),text,{text=it.take(100)})
            MetroButton(os.t("保存","save"),{ onSave(text.trim()); onDismiss() },primary=true,enabled=text.isNotBlank())
            MetroButton(os.t("取消","cancel"),onDismiss)
        }
    }
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

private data class TilePosition(val index:Int,val x:Int,val y:Int,val w:Int,val h:Int)
private fun placeTiles(tiles:List<TileSpec>):List<TilePosition> {
    val used=mutableSetOf<Pair<Int,Int>>()
    return tiles.mapIndexed { i,t ->
        val w=t.size; val h=if(t.size==1) 1 else 2
        var y=0; var x=0
        outer@ while(true) {
            for(col in 0..4-w) if((col until col+w).all { cx -> (y until y+h).all { cy -> (cx to cy) !in used } }) { x=col; break@outer }
            y++
        }
        for(cx in x until x+w) for(cy in y until y+h) used.add(cx to cy)
        TilePosition(i,x,y,w,h)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun Desktop(os:OsStore) {
    val pager=rememberPagerState { 2 }; val scope=rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        var time by remember { mutableStateOf(LocalTime.now()) }
        LaunchedEffect(Unit) { while(true) { time=LocalTime.now(); delay(1000) } }
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,top=15.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Label("YOKULI OS",12,LocalMetro.current.muted,Modifier.weight(1f))
            Label(time.format(DateTimeFormatter.ofPattern("HH:mm")),13)
        }
        Row(Modifier.fillMaxWidth().padding(start=22.dp,bottom=18.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Label(if(pager.currentPage==0) os.t("开始","start") else os.t("应用","apps"),52,modifier=Modifier.weight(1f))
            IconAction(if(pager.currentPage==0) "next" else "back","",{ scope.launch { pager.animateScrollToPage(1-pager.currentPage) } })
        }
        HorizontalPager(pager,Modifier.weight(1f),userScrollEnabled=!os.editTiles,verticalAlignment=Alignment.Top) { page ->
            if(page==0) StartTiles(os) else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp)) {
                    Field(os.t("查找应用","find an app"),query,{ query=it })
                    Spacer(Modifier.height(14.dp))
                    AppId.entries.filter { os.title(it).contains(query,true) || it.en.contains(query,true) }.forEach { app ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { MenuRow(os.title(app),icon=app.icon) { os.open(app.name.lowercase()) } }
                            IconAction(if(os.tiles.any { it.app==app.name }) "check" else "pin","",{
                                if(os.tiles.none { it.app==app.name }) { os.tiles=os.tiles+TileSpec(app.name); os.save(); os.notify("已固定到开始屏幕","Pinned to start") }
                            })
                        }
                    }
                    Spacer(Modifier.height(20.dp)); Label(os.t("向右滑动回到开始屏幕","swipe right to return to start"),14,LocalMetro.current.muted)
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun StartTiles(os:OsStore) {
    val positions=placeTiles(os.tiles)
    var selected by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val density=LocalDensity.current
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=22.dp,end=22.dp,bottom=18.dp)) {
            val gap=8.dp; val cell=(maxWidth-gap*3)/4
            val stepPx=with(density) { (cell+gap).toPx() }
            val rows=positions.maxOfOrNull { it.y+it.h } ?: 0
            Box(Modifier.fillMaxWidth().height((cell+gap)*rows)) {
                for(pos in positions) {
                    val tile=os.tiles[pos.index]; val app=AppId.valueOf(tile.app)
                    val source=remember(tile.app) { MutableInteractionSource() }; val pressed by source.collectIsPressedAsState()
                    val scale by animateFloatAsState(if(dragging==tile.app) 1.05f else if(pressed) .965f else if(os.editTiles && selected!=tile.app) .92f else 1f,label="tile")
                    Box(Modifier.offset(x=(cell+gap)*pos.x,y=(cell+gap)*pos.y)
                        .size((cell+gap)*pos.w-gap,(cell+gap)*pos.h-gap)
                        .zIndex(if(dragging==tile.app) 2f else 0f)
                        .graphicsLayer { scaleX=scale; scaleY=scale; rotationY=if(pressed) -2f else 0f; translationX=if(dragging==tile.app) dragOffset.x else 0f; translationY=if(dragging==tile.app) dragOffset.y else 0f }
                        .background(LocalMetro.current.accent)
                        .then(if(os.editTiles && selected==tile.app) Modifier.border(3.dp,LocalMetro.current.fg) else Modifier)
                        .combinedClickable(interactionSource=source,indication=null,onClick={ if(os.editTiles) selected=tile.app else os.open(tile.app.lowercase()) },onLongClick={ os.editTiles=true; selected=tile.app })
                        .pointerInput(tile.app,positions) {
                            detectDragGesturesAfterLongPress(onDragStart={ os.editTiles=true; selected=tile.app; dragging=tile.app; dragOffset=Offset.Zero },
                                onDragCancel={dragging=null; dragOffset=Offset.Zero},onDragEnd={
                                    val centerX=pos.x+pos.w/2f+dragOffset.x/stepPx; val centerY=pos.y+pos.h/2f+dragOffset.y/stepPx
                                    val target=positions.minByOrNull { hypot(it.x+it.w/2f-centerX,it.y+it.h/2f-centerY) }?.index ?: pos.index
                                    val list=os.tiles.toMutableList(); val moved=list.removeAt(pos.index); list.add(target.coerceIn(0,list.size),moved); os.tiles=list; os.save(); dragging=null; dragOffset=Offset.Zero
                                },onDrag={change,amount -> change.consume(); dragOffset+=amount })
                        }.padding(if(tile.size==1) 8.dp else 14.dp)) {
                        Glyph(app.icon,Modifier.size(if(tile.size==1) 30.dp else 57.dp).align(if(tile.size==4) Alignment.CenterStart else Alignment.Center),Color.White)
                        if(tile.size>1) Label(os.title(app),if(tile.size==4) 27 else 18,Color.White,
                            Modifier.align(if(tile.size==4) Alignment.CenterEnd else Alignment.BottomStart),maxLines=1)
                    }
                }
            }
            if(os.tiles.isEmpty()) Label(os.t("向左滑到应用列表，固定你常用的应用。","Swipe left to apps and pin your favourites."),22)
        }
        if(os.editTiles) {
            Row(Modifier.fillMaxWidth().background(LocalMetro.current.panel).padding(5.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                IconAction("layers",os.t("尺寸","size"),{ os.tiles=os.tiles.map { if(it.app==selected) it.copy(size=when(it.size) {1->2;2->4;else->1}) else it }; os.save() })
                IconAction("close",os.t("取消固定","unpin"),{ os.tiles=os.tiles.filter { it.app!=selected }; selected=null; os.save() })
                IconAction("check",os.t("完成","done"),{ os.editTiles=false; selected=null })
            }
        } else Label(os.t("左滑查看应用 · 长按编辑磁贴","swipe for apps · hold a tile to arrange"),12,LocalMetro.current.muted,Modifier.padding(start=22.dp,bottom=14.dp))
    }
}

@Composable fun SearchScreen(os:OsStore) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("搜索","search"))
        PageBody {
            Field(os.t("应用、标记、航线","apps, marks, routes"),query,{query=it})
            for(app in AppId.entries.filter { os.title(it).contains(query,true) || it.en.contains(query,true) }) MenuRow(os.title(app),icon=app.icon) { os.open(app.name.lowercase()) }
            if(query.isNotBlank()) {
                for(place in os.places.filter { it.name.contains(query,true) }) MenuRow(place.name,coordinates(place.point),"pin") { os.open("place:${place.id}") }
                for(route in os.routes.filter { it.name.contains(query,true) }) MenuRow(route.name,nm(route.length),"route") { os.open("route:${route.id}") }
            }
        }
    }
}
@Composable fun SessionsScreen(os:OsStore) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("最近使用","recent"))
        PageBody {
            if(os.recent.isEmpty()) Label(os.t("从开始屏幕打开一个应用","Open an app from start"),22,LocalMetro.current.muted)
            os.recent.forEach { page -> AppId.entries.firstOrNull { it.name.lowercase()==page }?.let { app -> MenuRow(os.title(app),icon=app.icon) { os.open(page) } } }
            Label(os.t("数据连接独立运行，返回桌面不会断开。","Data connections keep running when you return to start."),16,LocalMetro.current.muted)
        }
    }
}

package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import com.yokuli.anchorwatch.data.trip.*
import com.yokuli.anchorwatch.domain.report.TripReport
import com.yokuli.anchorwatch.domain.report.ReportQuality
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.runtime.contract.VoyagePhase
import com.yokuli.runtime.contract.VoyageRequestStatus
import androidx.compose.ui.window.Dialog
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

/** One app owns the active recording and every saved voyage. RecordingDialog owns all controls. */
@Composable fun LogbookScreen(os: OsStore, initialVoyageId: Long? = null) {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
    var selected by rememberSaveable(initialVoyageId) { mutableStateOf(initialVoyageId) }
    ReportVisibleAppRoute(os, selected?.let { "voyage:$it" } ?: "voyages")
    var recording by remember { mutableStateOf(false) }
    var capturedMomentId by rememberSaveable { mutableStateOf<String?>(null) }
    val captures by marine.services.voyages.capturedMoments.collectAsState()
    val pendingCaptures=captures.filter {it.status==com.yokuli.anchorwatch.runtime.trip.TripMomentStatus.FAILED}
    var query by rememberSaveable { mutableStateOf("") }
    val c = LocalMetro.current
    val back:()->Unit = { if(initialVoyageId!=null&&selected==initialVoyageId)os.shell.popRoute() else if(selected!=null)selected=null }
    BindInternalAppInputHandler { input -> if (input == ShellInput.BACK && selected != null) { back(); true } else false }
    AppBackHandler(selected!=null){back()}
    if (selected != null) {
        key(selected) { VoyageDetail(os, selected!!, back, { recording = true }) }
    } else Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.title(AppId.VOYAGES))
        if(pendingCaptures.isNotEmpty())MenuRow(os.t("${pendingCaptures.size} 条随记待保存","${pendingCaptures.size} moments need saving"),os.t("原时刻与备注已保留，点此重试","Original time and notes retained · tap to retry"),"logbook") {
            capturedMomentId=pendingCaptures.first().requestId;recording=true
        }
        Pivot(listOf(os.t("本次航行", "this voyage"), os.t("所有航行", "all voyages"))) { page ->
            if (page == 0) CurrentVoyage(os, { recording = true }, { selected = it }, {state.activeTrip?.let {capturedMomentId=captureVoyageMoment(os,it.id);recording=true}})
            else {
                val sessions = state.tripSessions.sortedByDescending { it.startedAt }.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.fillMaxSize().padding(start = LocalShellHorizontalInsets.current.pageStart, end = LocalShellHorizontalInsets.current.pageEnd), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Field(os.t("查找航行", "find a voyage"), query, { query = it }) }
                    item { Label(os.t("${sessions.size} 次航行", "${sessions.size} voyages"), 20, c.accentText) }
                    if (sessions.isEmpty()) item { Label(if (query.isBlank()) os.t("开始一段记录，航迹、船况和沿途时刻会留在这里。", "Record a voyage to keep its track, conditions and moments here.") else os.t("没有找到对应的航行。", "No voyages match your search."), 15) }
                    items(sessions, key = { it.id }) { session ->
                        MenuRow(session.name, voyageDate(session.startedAt) + " · " + os.formatDistance(session.distanceMeters) + if (session.active) " · " + os.t(if (session.paused) "已暂停" else "记录中", if (session.paused) "paused" else "recording") else "", "logbook") { selected = session.id }
                    }
                }
            }
        }
    }
    if (recording) RecordingDialog(os,initialMomentRequestId=capturedMomentId) { recording = false;capturedMomentId=null }
}

@Composable private fun CurrentVoyage(os: OsStore, controls: () -> Unit, detail: (Long) -> Unit,mark:()->Unit) {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
    val voyage by marine.voyage.collectAsState()
    val commands by marine.system.voyage.commands.collectAsState()
    val currentCommand=commands.lastOrNull { !it.terminal } ?: commands.lastOrNull()
    val data by os.hub.state.collectAsState()
    val now = rememberMarineClock()
    val trip = state.activeTrip
    val c = LocalMetro.current
    val fix = data.fix(os.positionSource)
    PageBody {
        VoyageCommandFeedback(os,currentCommand)
        if (trip == null) {
            Label(os.t("下一次出发", "your next departure"), 24, c.accentText)
            Label(os.t("记录走过的海面，也记住沿途的时刻。", "Keep the waters you travelled and the moments along the way."), 15)
            Label(when {
                voyage.phase == VoyagePhase.STARTING -> os.t("正在准备本次航行…", "preparing this voyage…")
                fix?.fresh(now) == true -> os.t("船位已准备好，可以开始记录。", "Position is ready to record.")
                fix != null -> os.t("保留上次船位，", "Last position retained, ") + readingAge(os, fix.elapsed, now)
                os.positionSource == "none" -> os.t("在“数据中心”选择船位来源或开启手机定位；船上设备在“船联网”中连接。", "Choose a position source or enable phone location in Data Center. Connect boat equipment in Boat Network.")
                else -> os.t("等待所选来源的首次船位。", "Waiting for the first position from your selected source.")
            }, 18, c.muted)
            if (state.active != null) Label(os.t("锚警继续值守，航行记录可以同时运行。", "The anchor watch continues while voyage recording runs."), 15, c.muted)
            MetroButton(if(currentCommand?.status==VoyageRequestStatus.UNKNOWN)os.t("结果未确认","result unconfirmed")else if(voyage.commandPending)os.t("正在开始…","starting…")else os.t("开始记录", "start recording"), controls, primary = true,enabled=!voyage.commandPending)
            state.tripSessions.firstOrNull { !it.active }?.let { last -> MenuRow(os.t("上次航行", "last voyage"), last.name + " · " + voyageDate(last.startedAt), "logbook") { detail(last.id) } }
        } else {
            val wall = System.currentTimeMillis()
            val duration = voyage.elapsedMillis(wall)
            Label(trip.name, 20)
            Label(os.formatDistance(trip.distanceMeters), 50, c.accent)
            Label(durationLabel(duration) + " · " + if (trip.paused) os.t("已暂停", "paused") else os.t("正在记录", "recording"), 20)
            val segments = os.recordedSegments
            val points = segments.flatten()
            val view = remember(trip.id) { MapViewState(points.firstOrNull() ?: fix?.point ?: GeoPoint(0.0, 0.0), if (points.isEmpty() && fix == null) 1.0 else 13.0) }
            var fitted by remember(trip.id) { mutableStateOf(false) }
            LaunchedEffect(points.isNotEmpty()) { if (!fitted && points.isNotEmpty()) { view.fit(points); fitted = true } }
            MarineMap(os.maps, MapScene(vessel = fix?.let { MapVessel(it.point, it.freshCourse(now),it.fresh(now),it.freshHeading(now),it.freshSpeed(now)) }, lines = segments.mapIndexed { index, line -> MapLine("live-$index", line, os.accent) }), view, Modifier.fillMaxWidth().height(240.dp))
            if (!trip.paused && fix?.fresh(now)!=true) Label(fix?.let {os.t("上次船位：","last position: ")+readingAge(os,it.elapsed,now)} ?: os.t("等待首次船位；记录保留定位中断。", "Waiting for the first position; recording preserves gaps."), 17, c.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) { Label(os.formatSpeed(trip.maxSogKnots), 29); Label(os.t("最高航速", "top speed"), 15, c.muted) }
                Column(Modifier.weight(1f)) { Label(trip.waypointCount.toString(), 29); Label(os.t("沿途时刻", "moments"), 15, c.muted) }
            }
            MetroButton(os.t("记一笔","capture a moment"),mark,primary=true,enabled=!voyage.commandPending)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MetroButton(if(trip.paused)os.t("继续记录","resume recording")else os.t("暂停记录","pause recording"),if(trip.paused)({marine.resumeRecording()}) else ({marine.pauseRecording()}),Modifier.weight(1f),enabled=!voyage.commandPending)
                MetroButton(os.t("结束并保存","finish & save"),{marine.finishRecording()},Modifier.weight(1f),enabled=mayFinishVoyage(commands))
            }
            MetroButton(os.t("在海图中查看","view on chart"),{os.openLinked("chart")})
            MetroButton(os.t("查看本次航迹与报告", "view this track & report"), { detail(trip.id) })
        }
        PinTileAction(os, currentTaskTileBinding("recording"))
    }
}

private data class VoyageContent(val map: TripMapData, val report: TripReport?, val replay: TripReplayData, val loadedAt: Long)

@Composable private fun VoyageDetail(os: OsStore, id: Long, back: () -> Unit, controls: () -> Unit) {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
    var loaded by remember(id) { mutableStateOf<VoyageContent?>(null) }
    var error by remember(id) { mutableStateOf(false) }
    var revision by remember(id) { mutableIntStateOf(0) }
    var sources by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingMoment by remember {mutableStateOf<com.yokuli.anchorwatch.data.database.TripWaypointEntity?>(null)}
    var capturedMoment by rememberSaveable {mutableStateOf<String?>(null)}
    val c = LocalMetro.current
    LaunchedEffect(id, revision, state.activeTrip?.takeIf{it.id==id}?.sampleCount?.div(30), state.tripSessions.firstOrNull {it.id==id}?.waypointCount) {
        error = false
        try {
            loaded = withContext(Dispatchers.IO) {
                coroutineScope {
                    val map = async { marine.services.voyages.tripMapData(id, 4_000) }
                    val report = async { marine.services.voyages.tripReport(id) }
                    val replay = async { marine.services.voyages.tripReplay(id) }
                    VoyageContent(map.await(), report.await(), replay.await(), System.currentTimeMillis())
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error = true }
    }
    val session = state.tripSessions.firstOrNull { it.id == id } ?: loaded?.map?.session
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, session?.name ?: os.t("航行详情", "voyage"), hasLocalBack = true)
        val content = loaded
        when {
            error -> PageBody { Label(os.t("暂时无法读取这次航行。", "This voyage could not be loaded."), 15); MetroButton(os.t("重试", "retry"), { revision++ }) }
            content == null -> PageBody { MetroProgress(os.t("正在读取航迹与记录…", "loading track and recordings…")) }
            session == null -> PageBody { Label(os.t("这次航行已经不可用。", "This voyage is no longer available."), 15) }
            else -> Pivot(listOf(os.t("航迹", "track"), os.t("报告", "report"), os.t("时刻", "moments"), os.t("导出", "export"))) { page ->
                PageBody {
                    if (session.active) {
                        MetroButton(os.t("本次记录控制", "recording controls"), controls, primary = true)
                        Label(os.t("自动更新于 ", "updated automatically ") + voyageTime(content.loadedAt), 14, c.muted)
                    }
                    when (page) {
                        0 -> VoyagePlayback(os, id, content) { sources = true }
                        1 -> VoyageReport(os, content.report)
                        2 -> {
                            val moments=content.map.events.filter {it.type=="USER_MOMENT"}
                            if (content.map.waypoints.isEmpty()&&moments.isEmpty()) Label(os.t("这次航行还没有随记。点“记一笔”会立即保存当时的时刻及可用船况。", "No moments yet. Capture a moment to save its time and available readings immediately."), 15)
                            moments.sortedByDescending {it.timestamp}.forEach {event ->
                                val moment=com.yokuli.anchorwatch.runtime.trip.TripMomentContent.from(event)
                                if(moment!=null) {
                                    Label(moment.name,20,c.accentText)
                                    Label(voyageDate(event.timestamp),14,c.muted)
                                    val position=event.latitude?.let {lat->event.longitude?.let {lon->GeoPoint(lat,lon)}}
                                    Label(position?.let {os.formatCoordinates(it)} ?: os.t("位置未记录","position not recorded"),15)
                                    if(moment.note.isNotBlank())Label(moment.note,15)
                                    if(moment.paused)Label(os.t("暂停期间的随记","captured while paused"),12,c.muted)
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        if(position!=null)MetroButton(os.t("在海图中查看","view on chart"),{showVoyageOnChart(os,content,position)},Modifier.weight(1f))
                                        MetroButton(os.t("查看 / 编辑随记","view / edit moment"),{capturedMoment=moment.requestId},Modifier.weight(1f))
                                    }
                                }
                            }
                            content.map.waypoints.forEach { point ->
                                Label(point.name, 20, c.accentText)
                                Label(voyageDate(point.timestamp), 14, c.muted)
                                Label(os.formatCoordinates(GeoPoint(point.latitude, point.longitude)), 15)
                                if (point.note.isNotBlank()) Label(point.note, 15)
                                Label(os.t("航速 ", "speed ") + os.formatSpeed(point.sogKnots) + " · " + os.t("水深 ", "depth ") + os.formatDepth(point.depthMeters), 17, c.muted)
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    MetroButton(os.t("在海图中查看","view on chart"),{showVoyageOnChart(os,content,GeoPoint(point.latitude,point.longitude))},Modifier.weight(1f))
                                    MetroButton(os.t("编辑笔记","edit note"),{editingMoment=point},Modifier.weight(1f))
                                }
                            }
                            AppSection(os.t("记录事件", "recorded events"))
                            if (content.map.events.none {it.type!="USER_MOMENT"}) Label(os.t("没有记录事件。", "No recorded events."), 17, c.muted)
                            content.map.events.filterNot {it.type=="USER_MOMENT"}.sortedBy { it.timestamp }.forEach { event ->
                                Label(eventName(os, event.type), 15)
                                Label(voyageDate(event.timestamp), 14, c.muted)
                            }
                        }
                        else -> {
                            VoyageShareActions(os,session)
                            if (!session.active) {
                                MetroButton(os.t("重命名航行","rename voyage"),{renaming=true})
                                MetroButton(os.t("删除这次航行", "delete voyage"), { delete = true })
                            }
                        }
                    }
                }
            }
        }
    }
    capturedMoment?.let {requestId ->
        AppDialog(onDismissRequest={capturedMoment=null;revision++}) {AppDialogSurface {
            AppDialogTitle(os.t("沿途随记","voyage moment"))
            CapturedMomentContent(os,requestId)
            MetroButton(os.t("完成","done"),{capturedMoment=null;revision++})
        }}
    }
    if (sources) MapSourcePicker(os) { sources = false }
    if (delete && session != null && !session.active) ConfirmDialog(os, os.t("删除这次航行及其记录？此操作无法撤销。", "Delete this voyage and its recordings? This cannot be undone."), { delete = false }) { marine.services.voyages.deleteTrip(session); delete = false; back() }
    if(renaming&&session!=null)VoyageTextEditor(os,os.t("航行名称","voyage name"),session.name,null,{renaming=false}){name,_->marine.services.voyages.renameTrip(id,name);renaming=false}
    editingMoment?.let{moment->VoyageTextEditor(os,os.t("沿途时刻","voyage moment"),moment.name,moment.note,{editingMoment=null}){name,note->os.scope.launch{marine.services.voyages.editTripMoment(moment,name,note.orEmpty()).join();revision++};editingMoment=null}}
}

@Composable private fun VoyageTextEditor(os:OsStore,title:String,initialName:String,initialNote:String?,dismiss:()->Unit,save:(String,String?)->Unit) {
    var name by rememberSaveable(initialName){mutableStateOf(initialName)};var note by rememberSaveable(initialNote){mutableStateOf(initialNote.orEmpty())}
    AppDialog(onDismissRequest=dismiss){AppDialogSurface() {
        AppDialogTitle(title)
        Field(os.t("名称","name"),name,{name=it.take(100)})
        if(initialNote!=null)Field(os.t("笔记","note"),note,{note=it.take(2000)},multiline=true)
        MetroButton(os.t("保存","save"),{save(name.trim(),note.takeIf{initialNote!=null})},primary=true,enabled=name.isNotBlank())
        MetroButton(os.t("取消","cancel"),dismiss)
    }}
}

private fun showVoyageOnChart(os:OsStore,content:VoyageContent,focus:GeoPoint?=null) {
    val segments=content.map.segments.map{segment->segment.points.filter{it.hasPosition}.map{GeoPoint(it.latitude!!,it.longitude!!)}}.filter{it.isNotEmpty()}
    os.maps.view("chart").previewTrack=segments
    os.maps.view("chart").previewTitle=content.map.session?.name?:os.t("历史航行","saved voyage")
    os.fitRequest=if(focus!=null)listOf(focus)else segments.flatten()
    os.openLinked("chart")
}

@Composable private fun VoyagePlayback(os: OsStore, id: Long, content: VoyageContent, sources: () -> Unit) {
    val c = LocalMetro.current
    val track = remember(content.map) { content.map.segments.map { segment -> segment.points.filter { it.hasPosition }.map { GeoPoint(it.latitude!!, it.longitude!!) } } }
    val all = remember(track) { track.flatten() }
    val samples = content.replay.points
    val firstTime = samples.firstOrNull()?.timestamp ?: 0L
    val lastTime = samples.lastOrNull()?.timestamp ?: firstTime
    var time by remember(id, firstTime) { mutableLongStateOf(firstTime) }
    var playing by remember(id) { mutableStateOf(false) }
    var speed by rememberSaveable(id) { mutableIntStateOf(10) }
    val view = remember(id) { MapViewState(all.firstOrNull() ?: GeoPoint(0.0, 0.0), if (all.isEmpty()) 1.0 else 13.0) }
    LaunchedEffect(id, all.isNotEmpty()) { if (all.isNotEmpty()) view.fit(all) }
    LaunchedEffect(playing, speed) {
        if (playing) {
            var tick = SystemClock.elapsedRealtime()
            while (playing && time < lastTime) {
                delay(100)
                val next = SystemClock.elapsedRealtime()
                time = (time + (next - tick) * speed).coerceAtMost(lastTime); tick = next
            }
            playing = false
        }
    }
    val index = remember(samples, time) { var lo = 0; var hi = samples.lastIndex; var result = -1; while (lo <= hi) { val mid = (lo + hi) / 2; if (samples[mid].timestamp <= time) { result = mid; lo = mid + 1 } else hi = mid - 1 }; result }
    val frame = samples.getOrNull(index)?.takeIf { time - it.timestamp <= 10_000 }
    val position = frame?.let { p -> val latitude=p.latitude; val longitude=p.longitude; if (latitude != null && longitude != null) GeoPoint(latitude, longitude).takeIf { it.valid() } else null }
    val capturedPoints=content.map.events.filter {it.type=="USER_MOMENT"}.mapNotNull {event->
        val lat=event.latitude;val lon=event.longitude
        if(lat==null||lon==null)null else MapPoint("captured-${event.id}",GeoPoint(lat,lon),com.yokuli.anchorwatch.runtime.trip.TripMomentContent.from(event)?.name.orEmpty(),os.accent)
    }
    val scene = MapScene(vessel = position?.let { MapVessel(it, frame?.cogDegrees,true,frame?.headingDegrees,frame?.sogKnots) }, lines = track.mapIndexed { i, points -> MapLine("voyage-$id-$i", points, os.accent) }, points = content.map.waypoints.map { MapPoint("moment-${it.id}", GeoPoint(it.latitude, it.longitude), it.name, os.accent) } + capturedPoints)
    MarineMap(os.maps, scene, view, Modifier.fillMaxWidth().height(290.dp), onEvent = { event ->
        if (event is MapEvent.ItemSelected && event.id.startsWith("moment-")) content.map.waypoints.firstOrNull { "moment-${it.id}" == event.id }?.let { time = it.timestamp.coerceIn(firstTime, lastTime); playing = false }
        if (event is MapEvent.ItemSelected && event.id.startsWith("captured-")) content.map.events.firstOrNull { "captured-${it.id}" == event.id }?.let { time=it.timestamp.coerceIn(firstTime,lastTime);playing=false }
    })
    MetroButton(os.t("在海图中查看这段航行","view voyage on chart"),{showVoyageOnChart(os,content)},enabled=all.isNotEmpty())
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroButton(os.t("地图来源", "map source"), sources, Modifier.weight(1f))
        MetroButton(os.t("完整航迹", "fit track"), { view.fit(all) }, Modifier.weight(1f), enabled = all.isNotEmpty())
    }
    if (samples.isEmpty()) Label(os.t("没有可回放的样本。", "No samples are available for replay."), 15, c.muted)
    else {
        AppSection(os.t("历史回放", "historical replay"))
        Label(voyageDate(time), 15)
        ReplayScrubber(os, value = if (lastTime > firstTime) ((time - firstTime).toDouble() / (lastTime - firstTime)).toFloat() else 0f, enabled = lastTime > firstTime) { progress -> playing = false; time = firstTime + ((lastTime - firstTime) * progress.toDouble()).toLong() }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(if (playing) os.t("暂停回放", "pause replay") else os.t("播放", "play"), { if (time >= lastTime) time = firstTime; playing = !playing }, Modifier.weight(1f), primary = true, enabled = lastTime > firstTime)
            MetroButton("${speed}×", { speed = when (speed) { 1 -> 10; 10 -> 60; else -> 1 } }, Modifier.weight(1f))
        }
        if (position == null) Label(os.t("此刻没有有效船位，船标暂不显示。", "No valid position at this time; the vessel marker is hidden."), 15, c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) { Label(os.formatSpeed(frame?.sogKnots), 28); Label(os.t("航速", "speed"), 15, c.muted) }
            Column(Modifier.weight(1f)) { Label(os.formatDepth(frame?.depthMeters), 28); Label(os.t("水深", "depth"), 15, c.muted) }
        }
        Label(os.t("船标与读数来自记录时刻；缺失船位不连成直线，也不会使用现在的船位。", "The marker and readings belong to the recorded time. Missing positions stay as gaps; today's position is never substituted."), 15, c.muted)
        content.replay.markers.forEach { marker -> MenuRow(if (marker.type == "WAYPOINT") marker.title else eventName(os, marker.type), voyageTime(marker.timestamp)) { time = marker.timestamp.coerceIn(firstTime, lastTime); playing = false } }
    }
}

@Composable private fun VoyageReport(os: OsStore, report: TripReport?) {
    val c = LocalMetro.current
    if (report == null) { Label(os.t("还没有可生成报告的记录。", "No recording is available for a report yet."), 15); return }
    Label(os.formatDistance(report.distanceMeters), 49, c.accent)
    Label(voyageDate(report.session.startedAt), 18, c.muted)
    Label(durationLabel(report.durationMillis) + " · " + os.t("移动 ", "moving ") + durationLabel(report.movingMillis), 20)
    fun coords(lat: Double?, lon: Double?) = if (lat != null && lon != null) os.formatCoordinates(GeoPoint(lat, lon)) else "—"
    ReportValue(os.t("出发位置", "start position"), coords(report.startLatitude, report.startLongitude))
    ReportValue(os.t("结束位置", "end position"), coords(report.endLatitude, report.endLongitude))
    AppSection(os.t("航行表现", "under way"))
    ReportValue(os.t("移动时平均 / 最高航速", "moving average / top speed"), os.formatSpeed(report.averageSogKnots) + " / " + os.formatSpeed(report.maxSogKnots))
    ReportValue(os.t("最低水深 / 龙骨下余量", "minimum depth / under-keel clearance"), os.formatDepth(report.minDepthMeters) + " / " + os.formatDepth(report.minUkcMeters))
    ReportValue(os.t("平均 / 最大真风速", "average / maximum true wind"), os.formatSpeed(report.trueWindMeanKnots) + " / " + os.formatSpeed(report.maximumTrueWindKnots))
    ReportValue(os.t("平均横倾 / 横摇周期", "average heel / roll period"), (report.averageAbsHeelDegrees?.let { "${decimal(it)}°" } ?: "—") + " / " + (report.dominantRollPeriodSeconds?.let { "${decimal(it)} s" } ?: "—"))
    ReportValue(os.t("气压变化", "pressure change"), os.formatPressureChange(report.pressureChangeHpa))
    AppSection(os.t("记录完整度", "recording coverage"))
    Label(when (report.quality) { ReportQuality.GOOD -> os.t("主要数据完整", "main observations are well covered"); ReportQuality.PARTIAL -> os.t("部分数据缺失", "some observations are missing"); ReportQuality.LIMITED -> os.t("可用数据有限", "limited observations available") }, 20, c.accentText)
    listOf(os.t("位置", "position") to report.positionCoveragePercent, os.t("水深", "depth") to report.depthCoveragePercent, os.t("风", "wind") to report.windCoveragePercent, os.t("姿态", "attitude") to report.attitudeCoveragePercent).forEach { (label, coverage) -> ReportValue(label, "${decimal(coverage)}%") }
    ReportValue(os.t("保存样本 / 沿途时刻", "saved samples / moments"), "${report.recordedSampleCount} / ${report.waypointCount}")
    ReportValue(os.t("船位缺口 / NMEA 缺口", "position / NMEA gaps"), "${report.positionGapCount} / ${report.nmeaGapCount}")
    Label(os.t("只根据实际记录计算。未连接的风或姿态传感器会显示缺失，不会当成平静天气或零横倾。", "Calculated from actual recordings. Missing wind or attitude sensors never imply calm weather or zero heel."), 15, c.muted)
}
@Composable private fun ReportValue(label: String, value: String) { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Label(label, 15, LocalMetro.current.muted); Label(value, 23) } }
private fun voyageDate(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
private fun voyageTime(time: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(time))
private fun eventName(os: OsStore, type: String): String = when (type) {
    "USER_WAYPOINT", "WAYPOINT" -> os.t("沿途时刻", "moment")
    "IMPACT_CANDIDATE" -> os.t("可能的冲击", "possible impact")
    "HIGH_MOTION" -> os.t("较强船体运动", "high vessel motion")
    "POSITION_GAP_STARTED" -> os.t("船位暂不可用", "position gap started")
    "POSITION_GAP_ENDED" -> os.t("船位恢复", "position restored")
    "NMEA_DATA_GAP" -> os.t("NMEA 数据间断", "NMEA data gap")
    "NMEA_DATA_RESUMED" -> os.t("NMEA 数据恢复", "NMEA data resumed")
    "TRIP_PAUSED" -> os.t("记录已暂停", "recording paused")
    "TRIP_RESUMED" -> os.t("记录已继续", "recording resumed")
    "TRIP_STARTED" -> os.t("航行开始", "voyage started")
    "TRIP_ENDED" -> os.t("航行结束", "voyage finished")
    "POSITION_SOURCE_CHANGED" -> os.t("船位来源改变", "position source changed")
    "HEADING_SOURCE_CHANGED" -> os.t("船首向来源改变", "heading source changed")
    "ATTITUDE_FRAME_CONFIRMED", "PHONE_ATTITUDE_SEGMENT_STARTED" -> os.t("姿态零点已确认", "attitude zero confirmed")
    "PHONE_ATTITUDE_SEGMENT_PAUSED" -> os.t("姿态记录已暂停", "attitude recording paused")
    "PHONE_MOVED_OR_MOUNT_SUSPECT" -> os.t("手机可能已移动", "phone may have moved")
    "RUNTIME_RESTORED" -> os.t("记录已恢复", "recording restored")
    "RUNTIME_RESTORE_PAUSED" -> os.t("恢复记录后暂停", "recording paused after restore")
    "NMEA_DISCONNECTED_BY_USER" -> os.t("手动断开 NMEA", "NMEA disconnected by user")
    "NMEA_RECONNECTED_BY_USER" -> os.t("手动重连 NMEA", "NMEA reconnected by user")
    "INSTRUMENT_GAP_STARTED" -> os.t("仪表数据间断", "instrument data gap")
    "WIND_SOURCE_CHANGED" -> os.t("风数据来源改变", "wind source changed")
    "DATA_WRITE_BACKPRESSURE" -> os.t("记录写入繁忙，部分样本未保存", "recording storage busy; some samples were dropped")
    "ANCHOR_STARTED" -> os.t("开始锚泊值守", "anchor watch started")
    "ANCHOR_STOPPED", "ANCHOR_ENDED" -> os.t("结束锚泊值守", "anchor watch ended")
    "ANCHOR_PAUSED" -> os.t("锚泊值守暂停", "anchor watch paused")
    "ANCHOR_RESUMED" -> os.t("锚泊值守继续", "anchor watch resumed")
    else -> os.t("记录事件", "recorded event") + " · " + type
}


/** WP-style rectangular handle; local synchronous progress keeps dragging independent of replay loading. */
@Composable private fun ReplayScrubber(os: OsStore, value: Float, enabled: Boolean, seek: (Float) -> Unit) {
    val c = LocalMetro.current
    val latestSeek by rememberUpdatedState(seek)
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription = os.t("回放时间", "replay time")
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
        if (enabled) setProgress { latestSeek(it.coerceIn(0f, 1f)); true }
    }.pointerInput(enabled) {
        if (enabled) detectTapGestures { position -> latestSeek((position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) }
    }.pointerInput(enabled) {
        if (enabled) detectDragGestures(onDragStart = { position -> latestSeek((position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) }) { change, _ ->
            change.consume(); latestSeek((change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
        }
    }) {
        val x = value.coerceIn(0f, 1f) * size.width
        drawLine(c.muted, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx())
        drawLine(c.accent, Offset(0f, size.height / 2), Offset(x, size.height / 2), 3.dp.toPx())
        drawRect(if (enabled) c.fg else c.muted, Offset((x - 6.dp.toPx()).coerceIn(0f, (size.width - 12.dp.toPx()).coerceAtLeast(0f)), size.height / 2 - 14.dp.toPx()), Size(12.dp.toPx(), 28.dp.toPx()))
    }
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.runtime.trip.TripMomentContent
import com.yokuli.anchorwatch.runtime.trip.TripMomentStatus
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.GeoPoint
import java.text.DateFormat
import java.util.Date

/** 所有“记一笔”入口立即进入唯一运行时；编辑器不重新采样。 */
internal fun captureVoyageMoment(os:OsStore,sessionId:Long):String? = os.marine?.services?.voyages?.captureMoment(
    sessionId,os.t("随记 ","Moment ")+DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()))

@Composable internal fun CapturedMomentContent(os:OsStore,requestId:String) {
    val voyages=os.marine?.services?.voyages ?: return
    val receipts by voyages.capturedMoments.collectAsState()
    val receipt=receipts.firstOrNull {it.requestId==requestId}
    LaunchedEffect(requestId) { voyages.restoreMoment(requestId) }
    val event=receipt?.event
    val content=event?.let(TripMomentContent::from)
    var editing by rememberSaveable(requestId) { mutableStateOf(false) }
    var name by rememberSaveable(requestId) { mutableStateOf<String?>(null) }
    var note by rememberSaveable(requestId) { mutableStateOf<String?>(null) }
    var kind by rememberSaveable(requestId) { mutableStateOf<String?>(null) }
    var submitting by rememberSaveable(requestId) { mutableStateOf(false) }
    LaunchedEffect(receipt?.status,receipt?.event?.detailJson) {
        if(submitting && content!=null && receipt?.status==TripMomentStatus.SAVED && content.name==name?.trim() && content.note==note?.trim() && content.kind==kind) {
            submitting=false;editing=false
        } else if(receipt?.status==TripMomentStatus.FAILED)submitting=false
    }
    val busy=receipt==null || receipt.status in setOf(TripMomentStatus.SAVING,TripMomentStatus.UPDATING)
    val saved=event?.id?.let {it>0}==true
    when(receipt?.status) {
        null,TripMomentStatus.SAVING -> MetroProgress(os.t("正在记录这一刻…","saving this moment…"))
        TripMomentStatus.UPDATING -> MetroProgress(os.t("正在保存备注…","saving your note…"))
        TripMomentStatus.SAVED -> Label(os.t("已记录","moment saved"),20,LocalMetro.current.accentText)
        TripMomentStatus.FAILED -> {
            Label(if(saved)os.t("备注尚未保存，原时刻已保留。","Your note has not been saved. The original moment is retained.") else os.t("尚未保存，这次点击的时刻和资料已保留用于重试。","Not saved yet. The original time and readings are retained for retry."),15)
            MetroButton(if(saved)os.t("重试保存备注","retry saving note")else os.t("重试保存","retry saving"),{voyages.retryMoment(requestId)},primary=true)
        }
        TripMomentStatus.REJECTED -> Label(os.t("这次航行已经结束，无法将此刻归入它。","This moment falls outside that voyage and cannot be added."),15)
        TripMomentStatus.NOT_RECOVERABLE -> Label(os.t("未找到这次捕获，无法恢复当时的资料。没有重新采样或创建其他时刻。","This capture could not be recovered. No replacement moment or new position was created."),15)
    }
    event?.let {
        Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.MEDIUM).format(Date(it.timestamp)),15)
        val position=it.latitude?.let {lat->it.longitude?.let {lon->GeoPoint(lat,lon)}}
        Label(position?.let {point->os.formatCoordinates(point)} ?: os.t("位置未记录 · 当时没有有效的新船位","Position not recorded · no valid new fix at capture"),12,LocalMetro.current.muted)
        if(content?.paused==true)Label(os.t("暂停期间的随记 · 未恢复轨迹记录","Captured while paused · track recording stayed paused"),12,LocalMetro.current.muted)
    }
    if(saved && !editing) {
        if(content?.name?.isNotBlank()==true)Label(content.name,15)
        if(content?.note?.isNotBlank()==true)Label(content.note,15)
        MetroButton(os.t("添加备注 / 修改类型","add a note / change type"),{
            name=content?.name.orEmpty();note=content?.note.orEmpty();kind=content?.kind?:"GENERAL";editing=true
        },enabled=!busy)
    }
    if(editing) {
        Field(os.t("名称","name"),name.orEmpty(),{name=it.take(100)})
        Field(os.t("想记住什么","what happened"),note.orEmpty(),{note=it.take(2000)},multiline=true)
        listOf("GENERAL" to os.t("随记","moment"),"SAIL_CHANGE" to os.t("换帆","sail change"),"WEATHER" to os.t("天气","weather"),"HAZARD" to os.t("注意点","attention")).forEach {(value,label)->
            ChoiceRow(label,kind==value) {kind=value}
        }
        MetroButton(os.t("保存备注","save note"),{
            submitting=true;voyages.editCapturedMoment(requestId,name.orEmpty(),note.orEmpty(),kind?:"GENERAL")
        },primary=true,enabled=saved&&!busy&&!name.isNullOrBlank())
        MetroButton(os.t("收起","collapse"),{editing=false},enabled=!busy)
    }
}

package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.planning.PassageAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** 人工批注只属于这次分析版本，不修改问题等级，也不能放行缺少资料的水域。 */
@Composable internal fun PassageReviewDialog(os:OsStore,analysis:PassageAnalysis,onDismiss:()->Unit) {
    val service=os.marine?.system?.analysis?:return
    val state by service.state.collectAsState()
    var issueId by rememberSaveable(analysis.key){mutableStateOf(analysis.issues.firstOrNull()?.id)}
    val selected=analysis.issues.firstOrNull{it.id==issueId}
    val saved=state.reviews.firstOrNull{it.analysisKey==analysis.key&&it.issueId==issueId}
    var note by rememberSaveable(analysis.key,issueId){mutableStateOf(saved?.note.orEmpty())}
    var choosing by rememberSaveable {mutableStateOf(false)}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    AppDialog(onDismissRequest=onDismiss){AppDialogSurface{
        AppDialogTitle(os.t("核对记录","Review notes"))
        selected?.let{issue->
            MenuRow(issue.message.split(" / ").let{if(os.chinese)it.first()else it.last()},os.t("第 ${issue.legIndex+1} 段 · ","Leg ${issue.legIndex+1} · ")+os.formatDistance(issue.alongMeters)){choosing=!choosing}
            if(choosing)analysis.issues.forEach{item->MenuRow(item.message.split(" / ").let{if(os.chinese)it.first()else it.last()}){issueId=item.id;choosing=false;error=false}}
            Field(os.t("核对了什么","What did you check?"),note,{if(!saving){note=it.take(1000);error=false}},multiline=true)
            saved?.let {Label(os.t("已保存 ","Saved ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it.reviewedAtUtc)),13,LocalMetro.current.muted)}
            Label(os.t("只记录这次检查的依据，不改变检查结果。资料变化后需重新核对。","Notes belong to this check. They do not change the result and must be reviewed again when data changes."),13,LocalMetro.current.muted)
            MetroButton(if(note.isBlank()&&saved!=null)os.t("移除记录","Remove note")else os.t("保存记录","Save note"),{
                saving=true;error=false
                os.scope.launch {
                    try {service.saveReview(analysis.key,issue.id,note.trim())}
                    catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){error=true}
                    finally{saving=false}
                }
            },primary=true,enabled=!saving&&note.trim()!=saved?.note.orEmpty())
            if(error)Label(os.t("未能保存，请重试","Could not save; retry"),14)
        } ?: Label(os.t("这次检查没有待核对的问题","There are no issues to annotate"),15)
        MetroButton(os.t("返回","Back"),onDismiss)
    }}
}

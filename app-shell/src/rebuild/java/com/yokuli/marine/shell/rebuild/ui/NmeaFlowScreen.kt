package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlinx.coroutines.delay

/** One app, named connections, and local object details. No legacy workspace. */
@Composable fun NmeaScreen(os:OsStore,service:(String,String?)->Unit){
    val vm=os.marine?.vm ?: return
    val connections by vm.nmeaConnections.collectAsState()
    val state by vm.ui.collectAsState()
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var editing by rememberSaveable{mutableStateOf(false)}
    var creating by rememberSaveable{mutableStateOf(false)}
    var frozen by remember{mutableStateOf<List<String>?>(null)}
    var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(Unit){while(true){delay(1000);now=SystemClock.elapsedRealtime()}}
    val current=connections.firstOrNull{it.spec.id==selected}
    val back={if(editing||creating){editing=false;creating=false}else{selected=null;frozen=null}}
    BindInternalAppInputHandler{input->if(input==ShellInput.BACK&&(selected!=null||creating)){back();true}else false}
    BackHandler(selected!=null||creating){back()}
    Column(Modifier.fillMaxSize()){
        PageHeader(os,when{creating->os.t("新连接","new connection");editing->os.t("编辑连接","edit connection");current!=null->current.spec.name;else->"nmea"},onBack=if(selected!=null||creating)back else null)
        when{
            creating||editing->ConnectionEditor(os,if(creating)null else current?.spec,connections.map{it.spec}){spec->vm.saveNmeaConnection(spec){selected=spec.id;editing=false;creating=false}}
            current!=null->Pivot(listOf(os.t("实况","live"),os.t("语句","sentences"),os.t("路由","routing"))){page->
                when(page){
                    0->PageBody{
                        Label(connectionText(os,current),32,LocalMetro.current.accent)
                        Label("${current.spec.protocol}  ${current.spec.host.ifBlank{"*"}}:${if(current.spec.protocol==Protocol.UDP&&current.spec.receive)current.spec.localPort else current.spec.port}",18)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)){
                            Column(Modifier.weight(1f)){Label(current.diagnostics.validSentences.toString(),38);Label(os.t("接收有效语句","valid received"),15,LocalMetro.current.muted)}
                            Column(Modifier.weight(1f)){Label(current.writtenSentences.toString(),38);Label(os.t("实际写出","actually written"),15,LocalMetro.current.muted)}
                        }
                        Label(os.t("最近接收：","Last received: ")+frameAge(os,current.diagnostics.lastPacketElapsed,now),16,LocalMetro.current.muted)
                        Label(os.t("最近写出：","Last written: ")+frameAge(os,current.lastWrittenElapsed,now),16,LocalMetro.current.muted)
                        current.error?.let{Label(it,16,LocalMetro.current.accent)}
                        MetroButton(if(current.requested)os.t("停止这条连接","stop this connection")else os.t("连接","connect"),{if(current.requested)vm.stopNmeaConnection(current.spec.id)else vm.startNmeaConnection(current.spec.id)},primary=!current.requested)
                        Label(os.t("只控制这条连接。其他连接和航程会保留；缺少的数据会显示为中断。","This controls this connection. Other connections and your voyage stay open; missing data is shown as a gap."),15,LocalMetro.current.muted)
                        MetroButton(os.t("编辑连接","edit connection"),{editing=true},enabled=!current.requested)
                        if(current.spec.receive)MetroButton(os.t("使用这条连接的船位","use position from this connection"),{vm.selectNmeaPositionConnection(current.spec.id)},enabled=current.requested)
                        MetroButton(os.t("数据来源","data sources"),{os.open("settings:sources")})
                    }
                    1->PageBody{
                        val live=current.diagnostics.raw.takeLast(60).map{"↓ $it"}+current.recentWritten.takeLast(40).map{"↑ $it"}
                        MetroButton(if(frozen==null)os.t("暂停查看","pause reading")else os.t("继续实时查看","resume reading"),{frozen=if(frozen==null)live.toList()else null})
                        Label(os.t("暂停查看不会暂停收发。↓ 接收，↑ 完成写出。","Pausing this view leaves traffic running. ↓ received, ↑ written."),14,LocalMetro.current.muted)
                        SelectionContainer{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){(frozen?:live).asReversed().forEach{Label(it,14)}}}
                        if(live.isEmpty())Label(os.t("尚无语句","No sentences yet"),26,LocalMetro.current.muted)
                    }
                    else->PageBody{
                        Label(if(current.spec.receive)os.t("接收 → 系统数据","receive → system data")else os.t("不接收数据","input disabled"),24)
                        Label(if(current.spec.send)os.t("输出：","output: ")+feedText(os,current.spec.feed)else os.t("输出已关闭","output disabled"),24)
                        if(current.spec.feed==NmeaFeed.RAW)current.spec.forwardFrom.forEach{id->Label(connections.firstOrNull{it.spec.id==id}?.spec?.name?:id,18)}
                        Label(os.t("句型：","Sentences: ")+current.spec.sentenceTypes.takeIf{it.isNotEmpty()}?.joinToString(", ").orEmpty().ifBlank{os.t("全部可用","all available")},17)
                        Label(os.t("系统编码保留来源；输入报文不会回送原连接。停下后可编辑路由。","System encoding preserves origins; an input is never sent back to its connection. Stop this connection to edit routing."),16,LocalMetro.current.muted)
                        MetroButton(os.t("编辑路由","edit routing"),{editing=true},enabled=!current.requested)
                        MetroButton(os.t("移除这条连接","remove connection"),{vm.removeNmeaConnection(current.spec.id);selected=null},enabled=!current.requested)
                    }
                }
            }
            else->PageBody{
                Label(os.t("船上的连接","connections aboard"),30)
                if(connections.isEmpty())Label(os.t("添加 GPS、风仪或网关。每条连接可以接收、发送，或双向运行。","Add a GPS, wind instrument or gateway. Each connection can receive, send, or do both."),22,LocalMetro.current.muted)
                connections.forEach{connection->MenuRow(connection.spec.name,"${connection.spec.protocol} · ${connectionText(os,connection)} · ↓ ${connection.diagnostics.validSentences}  ↑ ${connection.writtenSentences}","next"){selected=connection.spec.id}}
                MetroButton(os.t("添加连接","add connection"),{creating=true},primary=true)
                MenuRow(os.t("选择数据来源","choose data sources"),os.t("手机和 NMEA 共用一份船位策略","one position policy for phone and NMEA")){os.open("settings:sources")}
            }
        }
        state.connectionAttempt.message.takeIf{state.connectionAttempt.state==com.yokuli.anchorwatch.ConnectionAttemptState.FAILED&&it.isNotBlank()}?.let{Label(it,14,LocalMetro.current.accent,Modifier.padding(horizontal=22.dp,vertical=8.dp))}
    }
}

@Composable private fun ConnectionEditor(os:OsStore,initial:NmeaConnectionSpec?,all:List<NmeaConnectionSpec>,save:(NmeaConnectionSpec)->Unit){
    var name by remember(initial?.id){mutableStateOf(initial?.name.orEmpty())}
    var host by remember(initial?.id){mutableStateOf(initial?.host.orEmpty())}
    var port by remember(initial?.id){mutableStateOf((initial?.port?:10110).toString())}
    var localPort by remember(initial?.id){mutableStateOf((initial?.localPort?:10110).toString())}
    var protocol by remember(initial?.id){mutableStateOf(initial?.protocol?:Protocol.TCP)}
    var rx by remember(initial?.id){mutableStateOf(initial?.receive?:true)}
    var tx by remember(initial?.id){mutableStateOf(initial?.send?:false)}
    var feed by remember(initial?.id){mutableStateOf(initial?.feed?:NmeaFeed.SYSTEM)}
    var forwards by remember(initial?.id){mutableStateOf(initial?.forwardFrom.orEmpty())}
    var filters by remember(initial?.id){mutableStateOf(initial?.sentenceTypes?.joinToString(", ").orEmpty())}
    var checksum by remember(initial?.id){mutableStateOf(initial?.requireChecksum?:true)}
    PageBody{
        Field(os.t("名称","name"),name,{name=it.take(80)})
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Protocol.entries.forEach{value->MetroButton(value.name,{protocol=value},primary=protocol==value)}}
        Toggle(os.t("接收数据","receive data"),rx){rx=it}
        Toggle(os.t("发送数据","send data"),tx){tx=it}
        Field(if(protocol==Protocol.UDP&&rx&&!tx)os.t("指定发件人 IP（留空接收全部）","sender IP (blank accepts all)")else os.t("设备地址","device address"),host,{host=it.trim()})
        Field(if(protocol==Protocol.UDP)os.t("目标端口","destination port")else os.t("端口","port"),port,{port=it},number=true)
        if(protocol==Protocol.UDP&&rx)Field(os.t("本机接收端口","local receive port"),localPort,{localPort=it},number=true)
        Toggle(os.t("要求校验和","require checksum"),checksum){checksum=it}
        if(tx){
            Label(os.t("发送什么","what to send"),27)
            NmeaFeed.entries.forEach{value->MetroButton(feedText(os,value),{feed=value},primary=feed==value)}
            if(feed==NmeaFeed.RAW){
                Label(os.t("转发来源","forward from"),21)
                all.filter{it.id!=initial?.id&&it.receive}.forEach{input->Toggle(input.name,input.id in forwards){enabled->forwards=if(enabled)forwards+input.id else forwards-input.id}}
            }
            Field(os.t("允许的句型，逗号分隔（留空为全部）","sentence filter, comma separated (blank = all)"),filters,{filters=it.uppercase().take(160)})
        }
        val valid=name.isNotBlank()&&(rx||tx)&&port.toIntOrNull() in 1..65535&&(protocol==Protocol.UDP||host.isNotBlank())&&(!tx||host.isNotBlank())&&(!(protocol==Protocol.UDP&&rx)||localPort.toIntOrNull() in 1..65535)&&(!tx||feed!=NmeaFeed.RAW||forwards.isNotEmpty())
        MetroButton(os.t("保存连接","save connection"),{save((initial?:NmeaConnectionSpec()).copy(name=name,host=host,port=port.toInt(),localPort=localPort.toIntOrNull()?:10110,protocol=protocol,receive=rx,send=tx,feed=feed,forwardFrom=forwards,sentenceTypes=filters.split(',',' ').map{it.trim()}.filter{it.length==3}.toSet(),requireChecksum=checksum))},primary=true,enabled=valid)
    }
}
private fun feedText(os:OsStore,feed:NmeaFeed)=when(feed){NmeaFeed.SYSTEM->os.t("系统当前采用的数据","selected system data");NmeaFeed.PHONE->os.t("仅本机传感器","phone sensors only");NmeaFeed.RAW->os.t("指定连接的原始语句","raw sentences from connections")}
private fun connectionText(os:OsStore,c:NmeaConnectionSnapshot)=when{
    !c.requested->os.t("已停止","stopped")
    c.state==NmeaConnectionState.ERROR->os.t("连接失败","connection error")
    c.state==NmeaConnectionState.DISCONNECTED->os.t("等待连接","waiting for connection")
    c.state==NmeaConnectionState.CONNECTING->os.t("正在连接","connecting")
    c.state==NmeaConnectionState.RECONNECTING->os.t("正在重连","reconnecting")
    !c.spec.receive&&c.spec.send&&c.state in setOf(NmeaConnectionState.CONNECTED,NmeaConnectionState.CONNECTED_NO_DATA,NmeaConnectionState.CONNECTED_NO_FIX,NmeaConnectionState.STALE)->os.t("已连接 · 输出就绪","connected · output ready")
    c.state==NmeaConnectionState.STALE->os.t("连接在线 · 数据过期","connected · data expired")
    c.state==NmeaConnectionState.CONNECTED_NO_FIX->os.t("已连接 · 无有效船位","connected · no valid position")
    c.state==NmeaConnectionState.CONNECTED_NO_DATA->os.t("已连接 · 等待数据","connected · waiting for data")
    else->os.t("已连接","connected")
}
private fun frameAge(os:OsStore,time:Long?,now:Long)=if(time==null)os.t("尚无","none yet")else if(now-time<2000)os.t("刚刚","now")else os.t("${(now-time)/1000} 秒前","${(now-time)/1000} s ago")

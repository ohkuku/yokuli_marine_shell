package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlinx.coroutines.delay

/** One app, named connections, and local object details. No legacy workspace. */
@Composable fun NmeaScreen(os:OsStore,initialPage:String=""){
    val services=os.marine?.services ?: return
    val connections by services.network.connections.collectAsState()
    val state by services.state.collectAsState()
    val entryConnection=remember(initialPage){initialPage.takeIf {it.startsWith("connection:")}?.substringAfter("connection:")?.let(android.net.Uri::decode)}
    val directEntry=entryConnection!=null||initialPage=="create"
    var selected by rememberSaveable(initialPage){mutableStateOf(entryConnection)}
    var editing by rememberSaveable{mutableStateOf(false)}
    var creating by rememberSaveable(initialPage){mutableStateOf(initialPage=="create")}
    var frozen by remember{mutableStateOf<List<String>?>(null)}
    val pageStates=rememberSaveableStateHolder()
    var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(Unit){while(true){delay(1000);now=SystemClock.elapsedRealtime()}}
    val current=connections.firstOrNull{it.spec.id==selected}
    val back:()->Unit={when {
        editing->{editing=false}
        creating && !(initialPage=="create"&&selected==null)->{creating=false}
        directEntry->os.shell.popRoute()
        else->{selected=null;creating=false;frozen=null}
    }}
    val localBack=selected!=null||creating||editing||directEntry
    AppBackHandler(localBack){back()}
    ReportVisibleAppRoute(os,when {
        creating->"nmea:create"
        selected!=null->"nmea:connection:${android.net.Uri.encode(selected)}"
        else->"nmea"
    })
    Column(Modifier.fillMaxSize()){
        PageHeader(os,when{creating->os.t("新连接","new connection");editing->os.t("编辑连接","edit connection");current!=null->current.spec.name;else->os.title(AppId.NMEA)},hasLocalBack=localBack,
            localBackLabel=when {
                editing -> os.t("返回 ","back to ") + (current?.spec?.name ?: os.t("连接","connection"))
                creating && !(initialPage=="create"&&selected==null) -> os.t("返回连接","back to connections")
                !directEntry && selected!=null -> os.t("返回连接","back to connections")
                else -> null
            })
        // 编辑连接后回到原来的实况/语句/路由页；列表滚动也独立保留。
        val pageKey=when{creating->"create";editing->"edit:$selected";current!=null->"connection:$selected";else->"connections"}
        pageStates.SaveableStateProvider(pageKey) { when{
            creating||editing->ConnectionEditor(os,if(creating)null else current?.spec,connections.map{it.spec}){spec->services.network.saveNmeaConnection(spec){selected=spec.id;editing=false;creating=false}}
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
                        MetroButton(if(current.requested)os.t("停止这条连接","stop this connection")else os.t("连接","connect"),{if(current.requested)services.network.stopNmeaConnection(current.spec.id)else services.network.startNmeaConnection(current.spec.id)},primary=!current.requested,enabled=current.requested||current.spec.feed!=NmeaFeed.PHONE||!current.spec.send)
                        if(!current.requested&&current.spec.send&&current.spec.feed==NmeaFeed.PHONE)Label(os.t("先编辑这条连接，将旧版手机专用输出改为数据中心的读数。","Edit this connection to replace legacy phone-only output with Data Center readings."),17,LocalMetro.current.muted)
                        Label(os.t("只启停这条连接。数据中心保留你的来源选择，其他连接与航行继续运行。","This starts or stops this connection. Data Center keeps your source choices; other connections and your voyage continue."),15,LocalMetro.current.muted)
                        MetroButton(os.t("编辑连接","edit connection"),{editing=true},enabled=!current.requested)
                    }
                    1->PageBody{
                        val live=current.diagnostics.raw.takeLast(60).map{"↓ $it"}+current.recentWritten.takeLast(40).map{"↑ $it"}
                        MetroButton(if(frozen==null)os.t("暂停查看","pause reading")else os.t("继续实时查看","resume reading"),{frozen=if(frozen==null)live.toList()else null})
                        Label(os.t("暂停查看不会暂停收发。↓ 接收，↑ 完成写出。","Pausing this view leaves traffic running. ↓ received, ↑ written."),14,LocalMetro.current.muted)
                        SelectionContainer{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){(frozen?:live).asReversed().forEach{Label(it,14)}}}
                        if(live.isEmpty())Label(os.t("尚无语句","No sentences yet"),26,LocalMetro.current.muted)
                    }
                    else->PageBody{
                        Label(if(current.spec.receive)os.t("接收 → 数据中心","receive → data center")else os.t("不接收数据","input disabled"),24)
                        Label(if(current.spec.send)os.t("输出：","output: ")+feedText(os,current.spec.feed)else os.t("输出已关闭","output disabled"),24)
                        if(current.spec.send)NmeaPublicationEditor(os,current.spec.feed,{},NmeaPublicationPolicy.selected(current.spec),{},current.spec.forwardFrom,{},connections.map{it.spec},current.spec.id,editable=false,destination=current.spec.name)
                        MetroButton(os.t("编辑路由","edit routing"),{editing=true},enabled=!current.requested)
                        MetroButton(os.t("移除这条连接","remove connection"),{services.network.removeNmeaConnection(current.spec.id);selected=null},enabled=!current.requested)
                    }
                }
            }
            else->PageBody{
                if(selected!=null)Label(os.t("这条连接正在读取，或已被移除。返回会保留原来的数据详情。","This connection is loading or has been removed. Back keeps your original data detail."),17,LocalMetro.current.muted)
                Label(os.t("船上的连接","connections aboard"),30)
                Label(os.t("连接设备、接收与发送。收到的读数汇入数据中心，在那里统一选择每项数据的来源。","Connect devices, receive and transmit. Received measurements arrive in Data Center, where you choose their sources for all apps."),17,LocalMetro.current.muted)
                if(connections.isEmpty())Label(os.t("添加 GPS、风仪或网关。每条连接可以接收、发送，或双向运行。","Add a GPS, wind instrument or gateway. Each connection can receive, send, or do both."),22,LocalMetro.current.muted)
                connections.forEach{connection->MenuRow(connection.spec.name,"${connection.spec.protocol} · ${connectionText(os,connection)} · ↓ ${connection.diagnostics.validSentences}  ↑ ${connection.writtenSentences}","next"){selected=connection.spec.id}}
                MetroButton(os.t("添加连接","add connection"),{creating=true},primary=true)
            }
        }
        }
        state.connectionAttempt.message.takeIf{state.connectionAttempt.state==com.yokuli.anchorwatch.ConnectionAttemptState.FAILED&&it.isNotBlank()}?.let{Label(it,14,LocalMetro.current.accent,Modifier.padding(horizontal=22.dp,vertical=8.dp))}
    }
}

@Composable private fun ConnectionEditor(os:OsStore,initial:NmeaConnectionSpec?,all:List<NmeaConnectionSpec>,save:(NmeaConnectionSpec)->Unit){
    var name by rememberSaveable(initial?.id){mutableStateOf(initial?.name.orEmpty())}
    var host by rememberSaveable(initial?.id){mutableStateOf(initial?.host.orEmpty())}
    var port by rememberSaveable(initial?.id){mutableStateOf((initial?.port?:10110).toString())}
    var localPort by rememberSaveable(initial?.id){mutableStateOf((initial?.localPort?:10110).toString())}
    var protocol by rememberSaveable(initial?.id){mutableStateOf(initial?.protocol?:Protocol.TCP)}
    var rx by rememberSaveable(initial?.id){mutableStateOf(initial?.receive?:true)}
    var tx by rememberSaveable(initial?.id){mutableStateOf(initial?.send?:false)}
    var feed by rememberSaveable(initial?.id){mutableStateOf(initial?.feed?:NmeaFeed.SYSTEM)}
    var forwards by rememberSaveable(initial?.id,stateSaver=NmeaStringSetSaver){mutableStateOf(initial?.forwardFrom.orEmpty())}
    var filters by rememberSaveable(initial?.id){mutableStateOf(initial?.sentenceTypes?.joinToString(", ").orEmpty())}
    var checksum by rememberSaveable(initial?.id){mutableStateOf(initial?.requireChecksum?:true)}
    var advanced by rememberSaveable(initial?.id){mutableStateOf(false)}
    var capabilities by rememberSaveable(initial?.id,stateSaver=NmeaStringSetSaver){mutableStateOf(initial?.let{NmeaPublicationPolicy.selected(it)}?:NmeaCapability.generated)}
    PageBody{
        Field(os.t("名称","name"),name,{name=it.take(80)})
        Protocol.entries.forEach{value->ChoiceRow(value.name,protocol==value,if(value==Protocol.TCP)os.t("持续连接一台设备","maintains a connection to one device")else os.t("接收或发送网络报文","receives or sends network packets")){protocol=value}}
        Toggle(os.t("接收数据","receive data"),rx){rx=it}
        Toggle(os.t("发送数据","send data"),tx){tx=it}
        Field(if(protocol==Protocol.UDP&&rx&&!tx)os.t("指定发件人 IP（留空接收全部）","sender IP (blank accepts all)")else os.t("设备地址","device address"),host,{host=it.trim()})
        Field(if(protocol==Protocol.UDP)os.t("目标端口","destination port")else os.t("端口","port"),port,{port=it},number=true)
        if(protocol==Protocol.UDP&&rx)Field(os.t("本机接收端口","local receive port"),localPort,{localPort=it},number=true)
        if(tx){
            NmeaPublicationEditor(os,feed,{feed=it},capabilities,{capabilities=it},forwards,{forwards=it},all,initial?.id,destination=name.ifBlank{os.t("此连接","this connection")})
        }
        MetroButton(if(advanced)os.t("收起高级选项","hide advanced options")else os.t("高级选项","advanced options"),{advanced=!advanced})
        if(advanced){
            if(rx){Toggle(os.t("忽略传输中损坏的数据","ignore damaged data"),checksum){checksum=it};Label(os.t("设备附带的校验码可以发现传输错误。默认开启；只有老设备不附带校验码时才关闭。","The device's error-checking code detects damaged packets. Leave this on unless an older device omits the code."),16,LocalMetro.current.muted)}
            if(tx)Field(os.t("额外句型限制（可留空）","additional sentence filter (optional)"),filters,{filters=it.uppercase().take(160)})
        }
        val valid=name.isNotBlank()&&(rx||tx)&&port.toIntOrNull() in 1..65535&&(protocol==Protocol.UDP||host.isNotBlank())&&(!tx||host.isNotBlank())&&(!(protocol==Protocol.UDP&&rx)||localPort.toIntOrNull() in 1..65535)&&(!tx||feed!=NmeaFeed.RAW||forwards.isNotEmpty())&&(!tx||feed!=NmeaFeed.PHONE)
        MetroButton(os.t("保存连接","save connection"),{save((initial?:NmeaConnectionSpec()).copy(name=name,host=host,port=port.toInt(),localPort=localPort.toIntOrNull()?:10110,protocol=protocol,receive=rx,send=tx,feed=feed,forwardFrom=forwards,sentenceTypes=filters.split(',',' ').map{it.trim()}.filter{it.length==3}.toSet(),requireChecksum=checksum,capabilities=capabilities))},primary=true,enabled=valid)
    }
}
private fun feedText(os:OsStore,feed:NmeaFeed)=when(feed){NmeaFeed.SYSTEM->os.t("数据中心的读数","Data Center readings");NmeaFeed.PHONE->os.t("手机专用 · 旧配置","phone only · legacy");NmeaFeed.RAW->os.t("指定连接的原始语句","raw sentences from connections")}
private fun connectionText(os:OsStore,c:NmeaConnectionSnapshot)=when{
    !c.requested->os.t("已停止","stopped")
    c.state==NmeaConnectionState.ERROR->os.t("连接失败","connection error")
    c.state==NmeaConnectionState.DISCONNECTED->os.t("等待连接","waiting for connection")
    c.state==NmeaConnectionState.CONNECTING->os.t("正在连接","connecting")
    c.state==NmeaConnectionState.RECONNECTING->os.t("正在重连","reconnecting")
    !c.spec.receive&&c.spec.send&&c.state in setOf(NmeaConnectionState.CONNECTED,NmeaConnectionState.CONNECTED_NO_DATA,NmeaConnectionState.CONNECTED_NO_FIX,NmeaConnectionState.STALE)->os.t("已连接 · 输出就绪","connected · output ready")
    c.state==NmeaConnectionState.STALE->os.t("连接在线 · 等待更新","connected · awaiting update")
    c.state==NmeaConnectionState.CONNECTED_NO_FIX->os.t("已连接 · 无有效船位","connected · no valid position")
    c.state==NmeaConnectionState.CONNECTED_NO_DATA->os.t("已连接 · 等待数据","connected · waiting for data")
    else->os.t("已连接","connected")
}
private fun frameAge(os:OsStore,time:Long?,now:Long)=if(time==null)os.t("尚无","none yet")else if(now-time<2000)os.t("刚刚","now")else os.t("${(now-time)/1000} 秒前","${(now-time)/1000} s ago")

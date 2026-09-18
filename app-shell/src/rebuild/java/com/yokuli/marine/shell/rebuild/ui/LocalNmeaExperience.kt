package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.nmea.NmeaFeed
import com.yokuli.anchorwatch.data.sharing.SharingServerState
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.AppId

/** 本机是服务器，其他设备是客户端；与主动发送连接共用完整的分享策略。 */
@Composable fun LocalNmeaScreen(os:OsStore) {
    val services=os.marine?.services?:return
    val state by services.state.collectAsState()
    val connections by services.network.connections.collectAsState()
    val settings=state.localNmeaServerSettings
    val server=state.nmeaSharing
    var port by rememberSaveable(settings.port){mutableStateOf(settings.port.toString())}
    var feed by rememberSaveable(settings.feed){mutableStateOf(settings.feed)}
    var capabilities by rememberSaveable(settings.capabilities,stateSaver=NmeaStringSetSaver){mutableStateOf(settings.capabilities)}
    var forwards by rememberSaveable(settings.forwardFrom,stateSaver=NmeaStringSetSaver){mutableStateOf(settings.forwardFrom)}
    val dirty=port.toIntOrNull()!=settings.port||feed!=settings.feed||capabilities!=settings.capabilities||forwards!=settings.forwardFrom
    val needsSave=dirty||!settings.configured
    val valid=port.toIntOrNull() in 1024..65535&&(feed!=NmeaFeed.RAW||forwards.isNotEmpty())&&feed!=NmeaFeed.PHONE
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.title(AppId.LOCAL_NMEA))
        Pivot(listOf(os.t("共享","sharing"),os.t("内容","content"),os.t("设备","devices"),os.t("已发送","sent"))) { page ->
            PageBody {
                when(page) {
                    0->{
                        Label(when(server.state){SharingServerState.RUNNING->os.t("等待与提供数据","listening & serving");SharingServerState.STARTING->os.t("正在启动","starting");SharingServerState.ERROR->os.t("服务暂不可用","service unavailable");else->os.t("已停止","stopped")},34,LocalMetro.current.accent)
                        Label(os.t("让同一网络中的平板、电脑或航海软件接收数据。来源由数据中心统一管理，这里选择分享内容与接收设备。","Share data with a tablet, computer or navigation app on the same network. Data Center manages sources; choose shared content and receivers here."),20)
                        Label(publicationFeedName(os,settings.feed),24)
                        Label(os.t("分享 ${settings.capabilities.size} 项数据能力","sharing ${settings.capabilities.size} capabilities"),17,LocalMetro.current.muted)
                        if(settings.serverRequested) {
                            Label("TCP ${settings.port}",28)
                            SelectionContainer { Column(verticalArrangement=Arrangement.spacedBy(8.dp)){server.addresses.forEach{Label(it,20)}} }
                            Label(os.t("${server.clientCount} 个客户端 · 已写出 ${server.sentSentences} 条数据","${server.clientCount} clients · ${server.sentSentences} packets written"),19)
                            MetroButton(os.t("停止本机服务","stop local service"),{services.sharing.stopLocalNmeaServer()})
                        } else {
                            Field(os.t("监听端口","listening port"),port,{port=it},number=true)
                            if(settings.feed==NmeaFeed.PHONE)Label(os.t("先到“内容”确认采用数据中心的读数，保存后再启动。","Choose Data Center readings under Content and save before starting."),17,LocalMetro.current.muted)
                            if(needsSave)MetroButton(if(settings.configured)os.t("保存更改","save changes")else os.t("确认共享内容","confirm sharing choices"),{services.sharing.saveLocalNmeaPublicationPolicy(port.toInt(),feed,capabilities,forwards)},enabled=valid)
                            MetroButton(os.t("启动服务","start service"),{services.sharing.startLocalNmeaServer()},primary=true,enabled=settings.configured&&!dirty&&settings.feed!=NmeaFeed.PHONE)
                        }
                        Label(os.t("在接收设备中选择 TCP 客户端，填写上面的地址与端口。切换到“内容”决定它可以收到什么。","Choose TCP client on the receiving device, then enter the address and port above. Choose what it receives on the content page."),17,LocalMetro.current.muted)
                    }
                    1->{
                        NmeaPublicationEditor(os,feed,{feed=it},capabilities,{capabilities=it},forwards,{forwards=it},connections.map{it.spec},editable=!settings.serverRequested,destination=os.t("所有客户端","all clients"))
                        if(!settings.serverRequested&&needsSave)MetroButton(os.t("保存分享内容","save sharing choices"),{services.sharing.saveLocalNmeaPublicationPolicy(port.toInt(),feed,capabilities,forwards)},primary=true,enabled=valid)
                    }
                    2->{
                        Label(os.t("接入本机的设备","connected devices"),28)
                        if(server.clients.isEmpty())Label(os.t("还没有设备接入。启动服务后，让接收设备连接服务页显示的地址。","No devices connected. Start the service and connect receivers to the address shown on the service page."),21,LocalMetro.current.muted)
                        server.clients.forEach{client->Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Label(client.address,24);Label(os.t("已写出 ${client.sentSentences} 条数据","${client.sentSentences} packets written"),16,LocalMetro.current.muted)}}
                    }
                    else->{
                        Label(os.t("已送到连接的设备","written to connected devices"),25)
                        SelectionContainer{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){server.recentWritten.asReversed().forEach{Label(it,14)}}}
                        if(server.recentWritten.isEmpty())Label(os.t("尚无写出记录","nothing written yet"),22,LocalMetro.current.muted)
                    }
                }
            }
        }
    }
}

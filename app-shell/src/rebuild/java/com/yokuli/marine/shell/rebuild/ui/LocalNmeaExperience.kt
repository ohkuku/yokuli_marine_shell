package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.sharing.SharingServerState
import com.yokuli.marine.shell.rebuild.OsStore

/** The implemented local listener is named explicitly until its app role is confirmed. */
@Composable fun LocalNmeaScreen(os:OsStore,service:(String,String?)->Unit){
    val vm=os.marine?.vm ?: return
    val state by vm.ui.collectAsState()
    val settings=state.localNmeaServerSettings
    val server=state.nmeaSharing
    var port by rememberSaveable(settings.port){mutableStateOf(settings.port.toString())}
    var pressure by rememberSaveable(settings.includePressure){mutableStateOf(settings.includePressure)}
    Column(Modifier.fillMaxSize()){
        PageHeader(os,os.t("本机服务","local service"))
        Pivot(listOf(os.t("服务","service"),os.t("客户端","clients"),os.t("已发送","sent"))){page->
            PageBody{
                when(page){
                    0->{
                        Label(when(server.state){SharingServerState.RUNNING->os.t("正在监听","listening");SharingServerState.STARTING->os.t("正在启动","starting");SharingServerState.ERROR->os.t("服务受阻","service unavailable");else->os.t("已停止","stopped")},34,LocalMetro.current.accent)
                        Label(os.t("把手机与 App 的有效数据提供给其他设备。手机船位必须先在系统来源中开启。","Serve valid phone and app data to other devices. Phone position must be enabled in system sources."),20)
                        if(settings.serverRequested){
                            Label("TCP ${settings.port}",26)
                            SelectionContainer{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){server.addresses.forEach{Label(it,18)}}}
                            Label(os.t("${server.clientCount} 个客户端 · 实际写出 ${server.sentSentences} 句","${server.clientCount} clients · ${server.sentSentences} sentences written"),19)
                            MetroButton(os.t("停止本机服务","stop local service"),{vm.stopLocalNmeaServer()})
                        }else{
                            Field(os.t("监听端口","listening port"),port,{port=it},number=true)
                            Toggle(os.t("包括气压","include pressure"),pressure){pressure=it}
                            MetroButton(os.t("保存服务设置","save service settings"),{vm.saveLocalNmeaServerConfiguration(port.toInt(),pressure,false)},enabled=port.toIntOrNull() in 1024..65535)
                            MetroButton(os.t("启动服务","start service"),{vm.startLocalNmeaServer()},primary=true,enabled=settings.configured&&settings.port.toString()==port&&settings.includePressure==pressure)
                        }
                        MenuRow(os.t("系统数据来源","system data sources")){os.open("settings:sources")}
                        state.localNmeaServerRuntime.suppressedStreams.forEach{(stream,reason)->Label("$stream · $reason",14,LocalMetro.current.muted)}
                    }
                    1->{
                        Label(os.t("接入本机的设备","devices connected here"),28)
                        if(server.clients.isEmpty())Label(os.t("等待接收设备。在同一网络的另一台设备上选择 TCP 客户端，填写服务页地址和端口。","Waiting for a receiver. On another device on this network, choose TCP client and enter the address and port shown on the service page."),21,LocalMetro.current.muted)
                        server.clients.forEach{client->MenuRow(client.address,os.t("实际写出 ${client.sentSentences} 句","${client.sentSentences} sentences written")){}}
                    }
                    else->{Label(os.t("实际完成写出的语句","sentences actually written"),25);SelectionContainer{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){server.recentWritten.asReversed().forEach{Label(it,14)}}};if(server.recentWritten.isEmpty())Label(os.t("尚无写出记录","nothing written yet"),22,LocalMetro.current.muted)}
                }
            }
        }
    }
}

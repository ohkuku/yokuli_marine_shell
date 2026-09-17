package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.saveable.listSaver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.shell.rebuild.*

/** 分享能力与输入 ID 的草稿用字符串列表保存，不依赖集合实现是否 Serializable。 */
internal val NmeaStringSetSaver=listSaver<Set<String>,String>(save={it.toList()},restore={it.toSet()})

/** 两个发布端共用同一可视化编辑器：数据来源 → 能力选择 → 接收设备。 */
@Composable internal fun NmeaPublicationEditor(
    os:OsStore,
    feed:NmeaFeed,
    onFeed:(NmeaFeed)->Unit,
    selected:Set<String>,
    onSelected:(Set<String>)->Unit,
    forwardFrom:Set<String>,
    onForwardFrom:(Set<String>)->Unit,
    connections:List<NmeaConnectionSpec>,
    ownId:String?=null,
    editable:Boolean=true,
    destination:String=os.t("接收设备","receivers"),
) {
    val vm=os.marine?.vm?:return
    val state by vm.ui.collectAsState()
    val c=LocalMetro.current
    val now=rememberMarineClock()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Glyph("connect",Modifier.size(34.dp),c.accent)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Label(os.t("数据来源","data origin"),13,c.muted)
            Label(publicationFeedName(os,feed),22)
        }
        Glyph("next",Modifier.size(20.dp),c.accent)
        Label(destination,17,c.muted,Modifier.padding(start=12.dp).widthIn(max=130.dp),maxLines=2)
    }
    NmeaFeed.entries.forEach { value ->
        ChoiceRow(publicationFeedName(os,value),feed==value,when(value) {
            NmeaFeed.SYSTEM->os.t("使用全系统选定的可信读数；来源改变时同步更新。","Uses the trusted measurements selected for all apps.")
            NmeaFeed.PHONE->os.t("只分享这部手机实际提供的位置与传感器。","Shares only position and sensors supplied by this phone.")
            NmeaFeed.RAW->os.t("保留输入连接的原始报文；按能力筛选后转发。","Forwards input packets after applying your capability choices.")
        },editable) { onFeed(value);onSelected(when(value){NmeaFeed.PHONE->selected.intersect(NmeaCapability.phone);NmeaFeed.SYSTEM->selected-NmeaCapability.OTHER.id;NmeaFeed.RAW->selected}) }
    }
    if(feed==NmeaFeed.RAW) {
        Label(os.t("转发哪些连接","forward these inputs"),28,c.accent)
        val inputs=connections.filter{it.receive&&it.id!=ownId}
        if(inputs.isEmpty())Label(os.t("先添加一条接收连接。","Add a receiving connection first."),18,c.muted)
        inputs.forEach { input -> PublicationChoice(input.name,input.id in forwardFrom,editable,"${input.protocol} · ${input.host.ifBlank{os.t("所有发件人","all senders")}}") { onForwardFrom(if(input.id in forwardFrom)forwardFrom-input.id else forwardFrom+input.id) } }
        Label(os.t("与输入设备相同的 IP 不会收到自己的数据，即使端口不同。","A device never receives its own data back, even on a different port."),15,c.muted)
    }
    Label(os.t("分享什么","what to share"),28,c.accent)
    val choices=NmeaCapability.entries.filter{when(feed){NmeaFeed.PHONE->it.id in NmeaCapability.phone;NmeaFeed.SYSTEM->it!=NmeaCapability.OTHER;NmeaFeed.RAW->true}}
    Label(os.t("已选 ${choices.count{it.id in selected}} / ${choices.size} 项","${choices.count{it.id in selected}} / ${choices.size} selected"),15,c.muted)
    choices.forEach { capability ->
        val detail=if(feed==NmeaFeed.RAW) {
            val names=connections.filter{it.id in forwardFrom}.joinToString(" · "){it.name}
            names.ifBlank{os.t("还未选择输入连接","no input selected")}
        } else publicationReading(os,capability,feed,state.vesselData,now)
        PublicationChoice(os.t(capability.zh,capability.en),capability.id in selected,editable,detail) {
            onSelected(if(capability.id in selected)selected-capability.id else selected+capability.id)
        }
    }
    if(selected.isEmpty())Label(os.t("没有选择任何数据；连接可以保持在线，但不会发送内容。","Nothing selected. The connection may stay online without publishing any data."),16,c.muted)
    if(!editable)Label(os.t("停止服务后可以更改分享内容。","Stop the service to change what is shared."),15,c.muted)
}

@Composable internal fun PublicationChoice(title:String,selected:Boolean,enabled:Boolean=true,detail:String?=null,onClick:()->Unit) {
    val c=LocalMetro.current
    Row(Modifier.fillMaxWidth().selectable(selected=selected,enabled=enabled,role=Role.Checkbox,onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).border(2.dp,if(selected)c.accent else c.fg).background(if(selected)c.accent else c.bg),contentAlignment=Alignment.Center) {
            if(selected)Glyph("check",Modifier.size(22.dp),c.bg)
        }
        Column(Modifier.weight(1f).padding(start=16.dp)) {
            Label(title,23,if(enabled)c.fg else c.muted)
            if(!detail.isNullOrBlank())Label(detail,14,c.muted,Modifier.padding(top=5.dp))
        }
    }
}

internal fun publicationFeedName(os:OsStore,feed:NmeaFeed)=when(feed) {
    NmeaFeed.SYSTEM->os.t("系统当前数据","selected system data")
    NmeaFeed.PHONE->os.t("本机传感器","this phone's sensors")
    NmeaFeed.RAW->os.t("转发输入连接","forward received data")
}

private fun publicationReading(os:OsStore,capability:NmeaCapability,feed:NmeaFeed,snapshot:VesselDataSnapshot,now:Long):String {
    val observation:VesselObservation<*> = when(capability) {
        NmeaCapability.POSITION->snapshot.position
        NmeaCapability.HEADING->snapshot.headingTrueDegrees
        NmeaCapability.ROTATION->snapshot.rateOfTurnDegreesPerMinute
        NmeaCapability.ATTITUDE->snapshot.attitude
        NmeaCapability.PRESSURE->snapshot.pressureHpa
        NmeaCapability.APPARENT_WIND->snapshot.apparentWind.speedKnots
        NmeaCapability.TRUE_WIND->snapshot.trueWind.speedKnots
        NmeaCapability.WATER_SPEED->snapshot.speedThroughWaterKnots
        NmeaCapability.DEPTH->snapshot.depthMeters
        NmeaCapability.TEMPERATURE->snapshot.waterTemperatureCelsius
        NmeaCapability.OTHER->VesselObservation<Any>()
    }
    val metric=when(capability) {
        NmeaCapability.POSITION->VesselMetricId.POSITION
        NmeaCapability.HEADING->VesselMetricId.HEADING_TRUE
        NmeaCapability.ROTATION->VesselMetricId.RATE_OF_TURN
        NmeaCapability.ATTITUDE->VesselMetricId.HEEL
        NmeaCapability.PRESSURE->VesselMetricId.PRESSURE
        else->null
    }
    val phone=if(feed==NmeaFeed.PHONE)snapshot.candidates[metric].orEmpty().filter{it.source.sourceType==VesselSourceType.PHONE_SENSOR}.maxByOrNull{it.receivedElapsedRealtime}else null
    val source=if(feed==NmeaFeed.PHONE)phone?.source?.displayName else observation.sourceIdentity?.displayName?:observation.provenance
    val value=if(feed==NmeaFeed.PHONE)phone?.value else observation.value
    val received=if(feed==NmeaFeed.PHONE)phone?.receivedElapsedRealtime else observation.receivedElapsedRealtime
    val fresh=if(feed==NmeaFeed.PHONE)received!=null&&now-received in 0L..if(capability==NmeaCapability.PRESSURE)60_000L else 10_000L
        else observation.freshness in setOf(VesselDataFreshness.FRESH,VesselDataFreshness.HELD)
    val text=when(value) {
        is VesselPosition->os.formatCoordinates(GeoPoint(value.latitude,value.longitude))
        is VesselAttitude->"${decimal(value.heelDegrees)}° / ${decimal(value.pitchDegrees)}°"
        is Double->when(capability) {
            NmeaCapability.APPARENT_WIND,NmeaCapability.TRUE_WIND,NmeaCapability.WATER_SPEED->os.formatSpeed(value)
            NmeaCapability.DEPTH->os.formatDepth(value)
            NmeaCapability.PRESSURE->"${decimal(value,0)} hPa"
            NmeaCapability.TEMPERATURE->"${decimal(value)} °C"
            NmeaCapability.ROTATION->"${decimal(value)}°/min"
            else->"${decimal(value)}°"
        }
        else->os.t("等待有效数据","waiting for valid data")
    }
    return listOfNotNull(source,text,if(value!=null&&!fresh)os.t("已过期，不会发送","stale; not sent")else null).joinToString(" · ")
}

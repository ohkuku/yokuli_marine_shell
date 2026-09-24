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

/** 两个发布端共用内容编辑器。来源只由数据中心维护；原始转发保留输入身份。 */
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
    val services=os.marine?.services?:return
    val state by services.state.collectAsState()
    val c=LocalMetro.current
    val now=rememberMarineClock()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Glyph("connect",Modifier.size(34.dp),c.accent)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Label(os.t("输出内容","output content"),13,c.muted)
            Label(publicationFeedName(os,feed),22)
        }
        Glyph("next",Modifier.size(20.dp),c.accent)
        Label(destination,17,c.muted,Modifier.padding(start=12.dp).widthIn(max=130.dp),maxLines=2)
    }
    // 保留正在运行的旧 PHONE 发布，不静默改动发送内容；再次保存须明确采用新策略。
    if(feed==NmeaFeed.PHONE) {
        Label(os.t("这是旧版的手机专用输出。全船来源现在统一由数据中心维护。", "This is a legacy phone-only output. Data Center now manages sources for all apps."),18,c.accent)
        if(editable) MetroButton(os.t("改用数据中心的读数", "use Data Center readings"), {onFeed(NmeaFeed.SYSTEM);onSelected(selected-NmeaCapability.OTHER.id)}, primary=true)
        else Label(os.t("当前发送内容保持不变。停止后编辑并保存，才会采用数据中心。", "Current output stays unchanged. Stop, edit and save to use Data Center."),16,c.muted)
    }
    listOf(NmeaFeed.SYSTEM,NmeaFeed.RAW).forEach { value ->
        ChoiceRow(publicationFeedName(os,value),feed==value,when(value) {
            NmeaFeed.SYSTEM->os.t("使用数据中心选定的读数，这里只决定哪些内容可以发出。","Uses readings selected in Data Center; choose only what may be sent here.")
            NmeaFeed.PHONE->os.t("旧版手机输出","legacy phone output")
            NmeaFeed.RAW->os.t("按内容筛选后转发原始报文，不改变数据中心的来源选择。","Forwards selected packet content without changing Data Center source choices.")
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
    NmeaFeed.SYSTEM->os.t("数据中心的读数","Data Center readings")
    NmeaFeed.PHONE->os.t("手机专用 · 旧配置","phone only · legacy")
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
        is VesselAttitude->"${os.formatAngle(value.heelDegrees)} / ${os.formatAngle(value.pitchDegrees)}"
        is Double->when(capability) {
            NmeaCapability.APPARENT_WIND,NmeaCapability.TRUE_WIND,NmeaCapability.WATER_SPEED->os.formatSpeed(value)
            NmeaCapability.DEPTH->os.formatDepth(value)
            NmeaCapability.PRESSURE->os.formatPressure(value)
            NmeaCapability.TEMPERATURE->os.formatTemperature(value)
            NmeaCapability.ROTATION->os.formatMetric("rot", value)
            NmeaCapability.HEADING->os.formatBearing(value)
            else->os.formatAngle(value)
        }
        else->os.t("等待有效数据","waiting for valid data")
    }
    return listOfNotNull(source,text,if(value!=null&&!fresh)os.t("已过期，不会发送","stale; not sent")else null).joinToString(" · ")
}

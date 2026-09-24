package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.runtime.contract.ais.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException

/** 所有客户端订阅系统交通快照；无页面级接收器、选源或避碰计算。 */
@Composable internal fun rememberAisTraffic(os:OsStore):TrafficSnapshot =
    os.marine?.system?.ais?.snapshot?.collectAsState()?.value ?: TrafficSnapshot()

internal fun AisPoint.geo()=GeoPoint(latitude,longitude)
internal fun aisNumber(mmsi:Int)=mmsi.toString().padStart(9,'0')
internal fun aisKind(os:OsStore,kind:AisEntityKind)=when(kind) {
    AisEntityKind.CLASS_A->os.t("A 类船舶","Class A vessel")
    AisEntityKind.CLASS_B->os.t("B 类船舶","Class B vessel")
    AisEntityKind.LONG_RANGE->os.t("远程低分辨率报告","long-range, low-resolution report")
    AisEntityKind.AID_TO_NAVIGATION->os.t("助航标志","aid to navigation")
    AisEntityKind.VIRTUAL_AID->os.t("虚拟助航标志","virtual aid to navigation")
    AisEntityKind.BASE_STATION->os.t("基站","base station")
    AisEntityKind.SAR_AIRCRAFT->os.t("搜救航空器","SAR aircraft")
    AisEntityKind.SART->"AIS-SART"
    AisEntityKind.MOB->"AIS-MOB"
    AisEntityKind.EPIRB->"AIS-EPIRB"
    AisEntityKind.UNKNOWN->os.t("尚未识别类型","type not yet identified")
}
internal fun aisState(os:OsStore,target:AisTarget)=when {
    target.cached->os.t("上次启动的缓存","cached from an earlier session")
    target.distress==AisDistressState.TEST->os.t("设备演练 / 自检","device exercise / self-test")
    target.distress==AisDistressState.INACTIVE->os.t("设备广播停用 / 取消","device reports inactive / cancelled")
    target.distress==AisDistressState.ACTIVE->os.t("收到激活的遇险设备报告","active distress-device report received")
    target.state==AisTargetState.WAITING_POSITION->os.t("等待位置报告","waiting for position")
    target.state==AisTargetState.CURRENT->os.t("观测有效","current observation")
    target.state==AisTargetState.AGING->os.t("上次观测 · 数据变旧","last observation · aging")
    target.state==AisTargetState.LOST->os.t("目标失去更新","target updates lost")
    target.state==AisTargetState.CONFLICT->os.t("来源位置冲突","source positions conflict")
    else->os.t("位置报告无效","invalid position report")
}
internal fun aisCpaReason(os:OsStore,state:AisCpaState)=when(state) {
    AisCpaState.CALCULATED->os.t("恒速直线估计","constant-velocity estimate")
    AisCpaState.ESTIMATED->os.t("对齐观测时刻后的估计","estimate after aligning observation times")
    AisCpaState.PAST->os.t("最近会遇时刻已过去","closest approach is in the past")
    AisCpaState.PARALLEL->os.t("相对速度接近零，时间不确定","near-zero relative speed; time undefined")
    AisCpaState.OWN_POSITION_MISSING->os.t("等待本船有效位置","waiting for valid own position")
    AisCpaState.TARGET_POSITION_MISSING->os.t("等待目标位置","waiting for target position")
    AisCpaState.STALE->os.t("观测太旧，不继续推算","observations too old for prediction")
    AisCpaState.CONFLICT->os.t("位置来源冲突，不推算","conflicting positions; no prediction")
    AisCpaState.MOTION_MISSING->os.t("缺少一致的对地运动数据","coherent ground motion is missing")
    AisCpaState.LOW_SPEED_COURSE->os.t("低速航迹方向不足以预测","low-speed course is insufficient for prediction")
    AisCpaState.NOT_SURFACE_VESSEL->os.t("此实体不参与船舶会遇预测","not a surface-vessel encounter")
    AisCpaState.LOW_RESOLUTION->os.t("远程报告精度不足以预测近距会遇","long-range accuracy cannot support close-quarters prediction")
}
internal fun aisRiskName(os:OsStore,kind:AisRiskKind)=when(kind) {
    AisRiskKind.CPA->os.t("预计近距会遇","predicted close approach")
    AisRiskKind.PROXIMITY->os.t("目标进入近距范围","target within proximity limit")
    AisRiskKind.ANCHOR_PROXIMITY->os.t("锚泊交通接近","anchor traffic proximity")
    AisRiskKind.DISTRESS->os.t("遇险设备报告","distress-device report")
    AisRiskKind.TARGET_LOST->os.t("关注目标失去更新","target of concern lost updates")
}
internal fun aisInputSummary(os:OsStore,s:TrafficSnapshot):String = when {
    s.inputs.isEmpty()->os.t("尚未配置 AIS 输入","no AIS input configured")
    s.inputs.all {it.state==AisInputState.DISABLED}->os.t("输入已关闭","inputs are off")
    s.inputs.any {it.state==AisInputState.CONNECTING}->os.t("正在连接输入","connecting input")
    s.inputs.none {it.state==AisInputState.ONLINE}->os.t("输入中断 · 保留上次观测","input interrupted · last observations retained")
    s.inputs.filter {it.state==AisInputState.ONLINE}.none {it.lastAisElapsed!=null}->os.t("连接在线 · 等待 AIS 报文","connected · waiting for AIS messages")
    s.targets.none {it.position!=null}->os.t("已收到 AIS · 等待目标位置","AIS received · waiting for target positions")
    s.inputs.mapNotNull {it.lastAisElapsed}.maxOrNull()?.let {s.generatedElapsed-it>60_000}==true->os.t("AIS 暂无更新 · 保留上次观测","no recent AIS updates · last observations retained")
    else->os.t("正在接收本地 AIS","receiving local AIS")
}

/** 保留关键目标；普通绘制上限只影响画面，绝不更改运行时的风险规则。 */
internal fun aisMapTargets(s:TrafficSnapshot,selected:String?,showTracks:Boolean=false):List<MapAisTarget> {
    val positioned=s.targets.filter {it.position!=null}
    val critical=positioned.filter {it.mmsi.toString()==selected||it.watched||it.riskLevel!=AisRiskLevel.NONE||it.distress==AisDistressState.ACTIVE}
    val visible=(critical+positioned.filter {it !in critical}.take(512)).distinctBy {it.mmsi}
    return visible.map {target->
        val fresh=target.state==AisTargetState.CURRENT&&!target.cached&&!target.positionInvalidated
        MapAisTarget(target.mmsi.toString(),target.position!!.geo(),target.displayName,target.kind.name,
            heading=target.dynamic?.headingDegrees?.takeIf {fresh},course=target.dynamic?.cogDegrees?.takeIf {fresh},
            speedMetersPerSecond=target.dynamic?.sogMetersPerSecond?.takeIf {fresh&&"sog_lower_bound" !in target.dynamic?.invalidFields.orEmpty()},stale=!fresh,lost=target.state==AisTargetState.LOST,distress=target.distress.name,
            risk=target.riskLevel!=AisRiskLevel.NONE,selected=target.mmsi.toString()==selected,
            tracks=if(showTracks||target.mmsi.toString()==selected)target.track.groupBy {it.segment}.values.map {segment->segment.map {it.position.geo()}} else emptyList())
    }
}

/** 只串行提交 UI 意图；业务状态仍唯一保存在系统服务。避免连续偏好修改用旧快照互相覆盖。 */
private val aisUiCommands=Mutex()
private fun OsStore.dispatchAis(make:(AisPreferences)->AisCommand,onSaved:()->Unit) {
    val service=marine?.system?.ais
    if(service==null){notify("AIS 正在恢复，请稍后重试。","AIS is restoring. Please retry shortly.",app=AppId.AIS);return}
    scope.launch {
        val result=try {
            aisUiCommands.withLock {
                withTimeout(15_000){service.snapshot.first {it.runtime.ready}}
                service.command(make(service.snapshot.value.preferences))
            }
        } catch(error:Exception) {
            if(error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException)throw error
            null
        }
        if(result?.success==true)onSaved()
        else notify("AIS 操作未保存，请重试。","AIS change was not saved. Please retry.",app=AppId.AIS,destination="ais:settings")
    }
}
internal fun OsStore.aisCommand(command:AisCommand,onSaved:()->Unit={})=dispatchAis({command},onSaved)
internal fun OsStore.aisPreferences(onSaved:()->Unit={},change:(AisPreferences)->AisPreferences)=
    dispatchAis({AisCommand.UpdatePreferences(change(it))},onSaved)

@Composable internal fun AisCompactDetail(os:OsStore,snapshot:TrafficSnapshot,mmsi:String,onClose:()->Unit) {
    val target=mmsi.toIntOrNull()?.let(snapshot::target)
    Column(Modifier.fillMaxWidth().background(LocalMetro.current.panel).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row { Label(target?.displayName ?: os.t("目标已离开缓存","target no longer retained"),21,modifier=Modifier.weight(1f));IconAction("close",os.t("收起","close"),onClose) }
        if(target!=null) {
            Label(aisState(os,target),14,LocalMetro.current.muted)
            Label(listOfNotNull(target.relative.distanceMeters?.let {os.formatDistance(it)},target.relative.bearingDegrees?.let {os.formatBearing(it)+" T"}).joinToString(" · ").ifBlank {aisCpaReason(os,target.relative.state)},15)
            MenuRow(os.t("在 AIS 中查看","view in AIS"),aisKind(os,target.kind)) {os.openLinked("ais:target:${target.mmsi}")}
        }
    }
}

@Composable internal fun AisLayerChoice(os:OsStore,anchor:Boolean) {
    val s=rememberAisTraffic(os)
    Toggle(if(anchor)os.t("守锚地图的 AIS","AIS on Anchor Watch")else os.t("海图的 AIS","AIS on Chart"),if(anchor)s.preferences.anchorLayer else s.preferences.chartLayer,
        os.t("只控制此地图显示；连接与已启用警戒继续运行。","Only changes this map; inputs and enabled monitoring keep running.")) { value->
        os.aisPreferences {if(anchor)it.copy(anchorLayer=value)else it.copy(chartLayer=value)}
    }
}

@Composable internal fun AisMonitoringSummary(os:OsStore,s:TrafficSnapshot,hidden:Boolean) {
    if(s.preferences.monitoringEnabled) MenuRow(os.t("AIS 交通警戒已启用","AIS traffic monitoring enabled"),
        if(hidden)os.t("此地图隐藏目标 · 点按查看","targets hidden here · tap to view")
        else if(s.backgroundLimitations.isNotEmpty())os.t("运行条件受限 · 点按检查","monitoring is limited · tap to inspect")
        else os.t("按全局规则监控已接收目标","monitoring received targets under global rules")) {os.openLinked("ais")}
}

/** 保持地图视口不变的小型状态入口。隐藏目标不等于关闭交通警戒。 */
@Composable internal fun AisMapStatus(os:OsStore,s:TrafficSnapshot,hidden:Boolean,modifier:Modifier=Modifier) {
    val concerns=s.targets.count {it.riskLevel!=AisRiskLevel.NONE}
    if(s.preferences.monitoringEnabled || concerns>0) {
        val status=when {
            s.preferences.monitoringEnabled&&(s.backgroundLimitations.isNotEmpty()||s.inputs.none {it.state==AisInputState.ONLINE})->os.t("AIS 警戒受限","AIS watch limited")
            hidden->os.t("AIS 警戒 · 目标隐藏","AIS watch · targets hidden")
            concerns>0->os.t("AIS · $concerns 个需关注","AIS · $concerns need attention")
            else->os.t("AIS 警戒","AIS watch")
        }
        Label(status+" ›",12,if(concerns>0)LocalMetro.current.accent else LocalMetro.current.fg,
            modifier.background(LocalMetro.current.bg).clickable {os.openLinked("ais")}.padding(horizontal=10.dp,vertical=7.dp))
    }
}

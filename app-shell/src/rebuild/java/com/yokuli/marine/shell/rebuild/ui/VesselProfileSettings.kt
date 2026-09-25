package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.marine.shell.rebuild.OsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.yokuli.anchorwatch.data.vessel.PassageGeometry
import com.yokuli.anchorwatch.data.vessel.passageGeometry

/** 船舶资料只在显示和输入边界换单位；各应用共享的几何及吃水仍使用米。 */
@Composable internal fun VesselProfileSettings(os: OsStore) {
    val marine = os.marine ?: return
    val state by marine.services.state.collectAsState()
    var name by rememberSaveable(state.vesselSettings.vesselName) { mutableStateOf(state.vesselSettings.vesselName) }
    val length = rememberUnitNumberDraft(state.settings.boatLengthMeters, os.lengthUnitLabel,
        os::lengthValue, os::lengthMeters, state.settings.boatLengthMeters)
    val draft = rememberUnitNumberDraft(state.vesselSettings.draftMeters, os.depthUnitLabel,
        os::depthValue, os::depthMeters, state.vesselSettings.draftMeters)
    val bow = rememberUnitNumberDraft(state.settings.bowRollerHeightMeters, os.lengthUnitLabel,
        os::lengthValue, os::lengthMeters, state.settings.bowRollerHeightMeters)
    val antenna = rememberUnitNumberDraft(state.settings.nmeaGpsAntennaToBowMeters, os.lengthUnitLabel,
        os::lengthValue, os::lengthMeters, state.settings.nmeaGpsAntennaToBowMeters)
    var passageExpanded by rememberSaveable {mutableStateOf(false)}
    val profile=state.vesselSettings
    val beam=rememberUnitNumberDraft(profile.beamMeters,os.lengthUnitLabel,os::lengthValue,os::lengthMeters,profile.beamMeters)
    val airDraft=rememberUnitNumberDraft(profile.airDraftMeters,os.lengthUnitLabel,os::lengthValue,os::lengthMeters,profile.airDraftMeters)
    val underKeel=rememberUnitNumberDraft(profile.minimumUnderKeelMeters,os.depthUnitLabel,os::depthValue,os::depthMeters,profile.minimumUnderKeelMeters)
    val margin=rememberUnitNumberDraft(profile.clearanceMarginMeters,os.lengthUnitLabel,os::lengthValue,os::lengthMeters,profile.clearanceMarginMeters)
    val corridor=rememberUnitNumberDraft(profile.corridorHalfWidthMeters,os.lengthUnitLabel,os::lengthValue,os::lengthMeters,profile.corridorHalfWidthMeters)
    val turn=rememberUnitNumberDraft(profile.turnRadiusMeters,os.lengthUnitLabel,os::lengthValue,os::lengthMeters,profile.turnRadiusMeters)
    val speed=rememberUnitNumberDraft(profile.plannedSpeedMetersPerSecond,os.speedUnitLabel,
        {os.speedValue(it/.5144444444)},{os.speedKnots(it)*.5144444444},profile.plannedSpeedMetersPerSecond)
    val passage=PassageGeometry(beam.value,airDraft.value,underKeel.value,margin.value,corridor.value,turn.value,speed.value)
    val passageValid=listOf(beam,airDraft,underKeel,margin,corridor,turn,speed).all {it.text.isBlank()||it.value!=null} && runCatching {passage.requireValid()}.isSuccess
    val passageChanged=passage!=profile.passageGeometry()
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val valid = passageValid && length.value?.let { it > 0.0 } == true &&
        (draft.text.isBlank() || draft.value?.let { it >= 0.0 } == true) &&
        listOf(bow, antenna).all { it.value?.let { value -> value >= 0.0 } == true }
    // 仓库把零吃水视为未知；使用同一语义比较，避免零值保存后永远显示未保存。
    val draftMeters = draft.value?.takeIf { it > 0.0 }
    val cleanName = name.trim()
    val geometryChanged = length.value != state.settings.boatLengthMeters ||
        bow.value != state.settings.bowRollerHeightMeters || antenna.value != state.settings.nmeaGpsAntennaToBowMeters
    val identityChanged = cleanName != state.vesselSettings.vesselName || draftMeters != state.vesselSettings.draftMeters
    val changed = geometryChanged || identityChanged || passageChanged

    fun edit(number: UnitNumberDraft, text: String) {
        if (!saving) { number.edit(text); saveFailed = false }
    }

    PageBody {
        Field(os.t("船名", "boat name"), name, { if (!saving) { name = it.take(100); saveFailed = false } })
        Field(os.t("船长", "length") + " · " + os.lengthUnitLabel, length.text, { edit(length, it) }, number = true)
        Field(os.t("吃水", "draft") + " · " + os.depthUnitLabel + os.t("（未知可留空）", " (leave blank if unknown)"),
            draft.text, { edit(draft, it) }, number = true)
        AppSection(os.t("设备位置", "equipment positions"))
        Field(os.t("船艏滚轮距水面高度", "bow roller height above water") + " · " + os.lengthUnitLabel,
            bow.text, { edit(bow, it) }, number = true)
        Field(os.t("固定 GPS 天线到船艏滚轮", "fixed GPS antenna to bow roller") + " · " + os.lengthUnitLabel,
            antenna.text, { edit(antenna, it) }, number = true)
        MenuRow(os.t("航线规划", "passage planning"),
            os.t("船体净空、转弯和计划航速 · 未知留空", "clearance, turning and planned speed · leave unknown values blank")) {passageExpanded=!passageExpanded}
        if(passageExpanded) {
            Field(os.t("船宽","beam")+" · "+os.lengthUnitLabel,beam.text,{edit(beam,it)},number=true)
            Field(os.t("水面以上最高点","air draft")+" · "+os.lengthUnitLabel,airDraft.text,{edit(airDraft,it)},number=true)
            Field(os.t("最小龙骨下余量","minimum under-keel clearance")+" · "+os.depthUnitLabel,underKeel.text,{edit(underKeel,it)},number=true)
            Field(os.t("障碍附加余量","obstacle clearance margin")+" · "+os.lengthUnitLabel,margin.text,{edit(margin,it)},number=true)
            Field(os.t("分析走廊半宽","analysis corridor half-width")+" · "+os.lengthUnitLabel,corridor.text,{edit(corridor,it)},number=true)
            Field(os.t("最小转弯半径","minimum turning radius")+" · "+os.lengthUnitLabel,turn.text,{edit(turn,it)},number=true)
            Field(os.t("计划航速","planned speed")+" · "+os.speedUnitLabel,speed.text,{edit(speed,it)},number=true)
            if(!passageValid)Label(os.t("尺寸需要是有效正数，净空余量可为零。未知项目留空。","Enter positive dimensions; clearance margins may be zero. Leave unknown values blank."),13,LocalMetro.current.accentText)
        }
        Label(os.t("这些资料供相关应用共用。手机定位不会假定手机固定在 GPS 天线位置。",
            "These details are shared by the apps that need them. Phone positioning does not assume a fixed antenna location."), 15, LocalMetro.current.muted)
        if (!valid) Label(os.t("船长须大于零，其余尺寸不能为负；未知吃水可留空。",
            "Length must be positive; other dimensions cannot be negative. Leave unknown draft blank."), 15, LocalMetro.current.muted)
        MetroButton(os.t("保存船舶资料", "save boat details"), {
            if (!valid || saving || !changed) return@MetroButton
            val lengthMeters = length.value ?: return@MetroButton
            val bowMeters = bow.value ?: return@MetroButton
            val antennaMeters = antenna.value ?: return@MetroButton
            saving = true
            saveFailed = false
            // 保存由进程级作用域等待；离开设置页不会中断后一组字段的写入。
            os.scope.launch {
                suspend fun awaitWrite(job: Job) {
                    job.join()
                    check(!job.isCancelled) { "Vessel preference write failed" }
                }
                try {
                    if (geometryChanged) awaitWrite(marine.services.preferences.setVesselGeometry(lengthMeters, bowMeters, antennaMeters))
                    if (identityChanged) awaitWrite(marine.services.preferences.setVesselIdentity(cleanName, draftMeters))
                    if (passageChanged) awaitWrite(marine.services.preferences.setPassageGeometry(passage))
                } catch (cancelled: CancellationException) {
                    saveFailed = true
                    throw cancelled
                } catch (_: Exception) {
                    saveFailed = true
                    os.notify("船舶资料未完全保存，请重试。已保存的字段会保留。",
                        "Boat details were not fully saved. Retry; fields already saved are retained.", app = AppId.SETTINGS, destination = "settings:vessel")
                } finally {
                    saving = false
                }
            }
        }, primary = true, enabled = valid && changed && !saving)
        when {
            saving -> MetroProgress(os.t("正在保存…", "saving…"))
            saveFailed -> Label(os.t("未能完成保存，请重试。", "Could not finish saving. Please retry."), 15, LocalMetro.current.muted)
            !changed -> Label(os.t("资料已保存", "details saved"), 15, LocalMetro.current.muted)
        }
        MenuRow(os.t("传感器与船体安装", "sensors & vessel mounting"),
            os.t("在数据中心确认安装、校准并选择来源", "confirm mounting, calibrate and choose sources in Data Center"), "data") {
            os.openLinked("data_center:phone")
        }
    }
}

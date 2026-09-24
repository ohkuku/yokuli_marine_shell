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
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val valid = length.value?.let { it > 0.0 } == true &&
        (draft.text.isBlank() || draft.value?.let { it >= 0.0 } == true) &&
        listOf(bow, antenna).all { it.value?.let { value -> value >= 0.0 } == true }
    // 仓库把零吃水视为未知；使用同一语义比较，避免零值保存后永远显示未保存。
    val draftMeters = draft.value?.takeIf { it > 0.0 }
    val cleanName = name.trim()
    val geometryChanged = length.value != state.settings.boatLengthMeters ||
        bow.value != state.settings.bowRollerHeightMeters || antenna.value != state.settings.nmeaGpsAntennaToBowMeters
    val identityChanged = cleanName != state.vesselSettings.vesselName || draftMeters != state.vesselSettings.draftMeters
    val changed = geometryChanged || identityChanged

    fun edit(number: UnitNumberDraft, text: String) {
        if (!saving) { number.edit(text); saveFailed = false }
    }

    PageBody {
        Field(os.t("船名", "boat name"), name, { if (!saving) { name = it.take(100); saveFailed = false } })
        Field(os.t("船长", "length") + " · " + os.lengthUnitLabel, length.text, { edit(length, it) }, number = true)
        Field(os.t("吃水", "draft") + " · " + os.depthUnitLabel + os.t("（未知可留空）", " (leave blank if unknown)"),
            draft.text, { edit(draft, it) }, number = true)
        Label(os.t("设备位置", "equipment positions"), 26)
        Field(os.t("船艏滚轮距水面高度", "bow roller height above water") + " · " + os.lengthUnitLabel,
            bow.text, { edit(bow, it) }, number = true)
        Field(os.t("固定 GPS 天线到船艏滚轮", "fixed GPS antenna to bow roller") + " · " + os.lengthUnitLabel,
            antenna.text, { edit(antenna, it) }, number = true)
        Label(os.t("这些资料供相关应用共用。手机定位不会假定手机固定在 GPS 天线位置。",
            "These details are shared by the apps that need them. Phone positioning does not assume a fixed antenna location."), 16, LocalMetro.current.muted)
        if (!valid) Label(os.t("船长须大于零，其余尺寸不能为负；未知吃水可留空。",
            "Length must be positive; other dimensions cannot be negative. Leave unknown draft blank."), 16, LocalMetro.current.muted)
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

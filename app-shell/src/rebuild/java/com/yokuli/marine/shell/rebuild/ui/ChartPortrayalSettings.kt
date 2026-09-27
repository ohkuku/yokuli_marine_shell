package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.chart.ChartColorMode
import com.yokuli.runtime.contract.chart.ChartDisplayCategory
import java.util.Locale

/** 显示偏好直接作用于所有地图宿主；不在此处改变数据文件夹、原始 ENC 或规划吃水条件。 */
@Composable internal fun ChartPortrayalSetting(os: OsStore) {
    var open by rememberSaveable { mutableStateOf(false) }
    val p = os.maps.portrayalPreferences
    val summary = when (p.category) {
        ChartDisplayCategory.BASE -> os.t("基础", "Base")
        ChartDisplayCategory.STANDARD -> os.t("标准", "Standard")
        ChartDisplayCategory.ALL -> os.t("全部", "All")
    }
    MenuRow(os.t("矢量海图显示", "Vector chart display"), summary, "chart") { open = true }
    if (open) AppDialog(onDismissRequest = { open = false }) { AppDialogSurface {
        AppDialogTitle(os.t("矢量海图", "Vector chart"))
        Label(os.t("显示内容", "Detail"), 14, LocalMetro.current.muted)
        ChartDisplayCategory.entries.forEach { category ->
            val name = when (category) { ChartDisplayCategory.BASE -> os.t("基础", "Base"); ChartDisplayCategory.STANDARD -> os.t("标准", "Standard"); ChartDisplayCategory.ALL -> os.t("全部", "All") }
            ChoiceRow(name, p.category == category) { os.maps.updatePortrayal(p.copy(category = category)) }
        }
        Label(os.t("海图配色", "Chart palette"), 14, LocalMetro.current.muted)
        ChartColorMode.entries.forEach { mode ->
            val name = when (mode) { ChartColorMode.DAY -> os.t("白天", "Day"); ChartColorMode.DUSK -> os.t("黄昏", "Dusk"); ChartColorMode.NIGHT -> os.t("夜间", "Night") }
            ChoiceRow(name, p.colorMode == mode) { os.maps.updatePortrayal(p.copy(colorMode = mode)) }
        }
        PortrayalDepths(os)
        Toggle(os.t("四级水深颜色", "Four depth shades"), p.fourDepthShades) { os.maps.updatePortrayal(p.copy(fourDepthShades = it)) }
        Toggle(os.t("水深数字", "Soundings"), p.showSoundings) { os.maps.updatePortrayal(p.copy(showSoundings = it)) }
        Toggle(os.t("地名与航标名称", "Place and aid names"), p.showNames) { os.maps.updatePortrayal(p.copy(showNames = it)) }
        Toggle(os.t("灯光扇区", "Light sectors"), p.showLightSectors) { os.maps.updatePortrayal(p.copy(showLightSectors = it)) }
        Toggle(os.t("测量质量区域", "Survey quality areas"), p.showQuality) { os.maps.updatePortrayal(p.copy(showQuality = it)) }
        Toggle(os.t("按比例尺简化", "Scale-dependent detail"), p.respectScaleMinimum) { os.maps.updatePortrayal(p.copy(respectScaleMinimum = it)) }
        ChartSourceSaveStatus(os)
        MetroButton(os.t("完成", "Done"), { open = false })
    } }
}

@Composable private fun PortrayalDepths(os: OsStore) {
    val p = os.maps.portrayalPreferences
    val f = os.unitFormats
    fun text(meters: Double) = String.format(Locale.US, "%.2f", f.depthValue(meters)).trimEnd('0').trimEnd('.')
    var shallow by rememberSaveable(p.shallowDepthMeters, f.depthUnit) { mutableStateOf(text(p.shallowDepthMeters)) }
    var safety by rememberSaveable(p.safetyDepthMeters, f.depthUnit) { mutableStateOf(text(p.safetyDepthMeters)) }
    var deep by rememberSaveable(p.deepDepthMeters, f.depthUnit) { mutableStateOf(text(p.deepDepthMeters)) }
    var invalid by remember { mutableStateOf(false) }
    Field(os.t("浅水", "Shallow water") + " (${f.depthUnit})", shallow, { shallow = it; invalid = false }, number = true)
    Field(os.t("安全等深线", "Safety contour") + " (${f.depthUnit})", safety, { safety = it; invalid = false }, number = true)
    Field(os.t("深水", "Deep water") + " (${f.depthUnit})", deep, { deep = it; invalid = false }, number = true)
    Label(os.t("实际突出显示不浅于此值的下一条等深线；这里不修改航线规划的吃水条件。", "Highlights the next available contour at or deeper than this depth. Route planning clearance is configured separately."), 12, LocalMetro.current.muted)
    if (invalid) Label(os.t("请输入有效水深：浅水 ≤ 安全水深 ≤ 深水，安全水深必须大于零。", "Enter valid depths: shallow ≤ safety ≤ deep, with safety greater than zero."), 13, LocalMetro.current.accentText)
    MetroButton(os.t("应用水深", "Apply depths"), {
        val values = listOf(shallow, safety, deep).map { it.trim().replace(',', '.').toDoubleOrNull()?.let(f::depthMeters) }
        val a = values[0]; val b = values[1]; val c = values[2]
        if (a == null || b == null || c == null || !a.isFinite() || !b.isFinite() || !c.isFinite() || a < 0 || b < .1 || b > 1000 || c > 12000 || a > b || b > c) invalid = true
        else os.maps.updatePortrayal(p.copy(shallowDepthMeters = a, safetyDepthMeters = b, deepDepthMeters = c))
    })
}

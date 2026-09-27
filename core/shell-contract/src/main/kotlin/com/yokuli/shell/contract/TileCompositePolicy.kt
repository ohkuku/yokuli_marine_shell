package com.yokuli.shell.contract

/** 中文：工坊、存储提交及真实渲染共用的组合约束，不另建数据来源。 */
object TileCompositePolicy {
    val panelIds: Set<String> = linkedSetOf(
        "navigationReadings", "windConditions", "environment", "depthClearance", "vesselAttitude",
        "aisTraffic", "navigation", "anchorWatch", "recording",
    )
    val DEFAULT_PANELS: List<String> = listOf("navigationReadings", "windConditions", "depthClearance", "environment")
    private val targets: Set<String> = setOf(
        "chart", "library", "voyages", "anchor", "places", "instruments", "data_center", "nmea", "ais",
        "local_nmea", "settings", "tiles", "task:navigation", "task:anchorWatch", "task:recording",
        "library:data", "places:routes", "places:anchorages", "instruments:tab:navigation",
        "instruments:tab:sailing", "instruments:tab:attitude", "instruments:tab:weather",
        "settings:units", "settings:start", "settings:exit",
    )

    /** 中文：保存的是内部页面身份，不接受任意 URI/Intent；安全动作仍在目标页确认。 */
    fun isAllowedTarget(target: String?): Boolean {
        if (target == null) return true
        if (target in targets) return true
        val prefix = listOf("tileplace:", "tileroute:").firstOrNull(target::startsWith) ?: return false
        val id = target.removePrefix(prefix)
        return id.isNotBlank() && id.length <= 512 && id.none(Char::isISOControl)
    }

    fun validPresentation(binding: TileBinding, presentation: TilePresentation): Boolean =
        if (binding.kind == TileBindingKind.COMPOSITE) {
            isCanonical(binding, presentation) && isAllowedTarget(presentation.tapTarget) &&
                presentation.title?.let { it.isNotBlank() && it.length <= 48 && it.none(Char::isISOControl) } != false
        } else presentation.compositePanels.isEmpty() && presentation.tapTarget == null && presentation.title == null

    fun validPanels(panels: List<String>): Boolean = panels.size in 2..4 &&
        panels.distinct().size == panels.size && panels.all(panelIds::contains)

    /** 中文：组合成员决定身份；排列、尺寸、名称和点击目标改变时仍是同一内容。 */
    fun canonicalBinding(panels: List<String>): TileBinding {
        require(validPanels(panels)) { "A composite tile needs two to four distinct supported panels" }
        return TileBinding("yokuli", TileBindingKind.COMPOSITE, "panels:" + panels.sorted().joinToString("+"))
    }

    fun isCanonical(binding: TileBinding, presentation: TilePresentation): Boolean =
        validPanels(presentation.compositePanels) && canonicalBinding(presentation.compositePanels) == binding

    fun supportsContentId(id: String): Boolean {
        if (id == "custom") return true // 工坊目录模板，提交前必须规范化。
        if (!id.startsWith("panels:")) return false
        val panels = id.removePrefix("panels:").split('+')
        return validPanels(panels) && canonicalBinding(panels).contentId == id
    }
}

package com.yokuli.runtime.contract.chart

/** 显示配置只改变呈现；不会修改 ENC 原始深度、航线检查阈值或用户船舶资料。 */
enum class ChartDisplayCategory { BASE, STANDARD, ALL }
enum class ChartColorMode { DAY, DUSK, NIGHT }
data class ChartPortrayalPreferences(
    val category: ChartDisplayCategory = ChartDisplayCategory.STANDARD,
    val colorMode: ChartColorMode = ChartColorMode.DAY,
    val shallowDepthMeters: Double = 2.0,
    val safetyDepthMeters: Double = 5.0,
    val deepDepthMeters: Double = 30.0,
    val fourDepthShades: Boolean = true,
    val showSoundings: Boolean = true,
    val showNames: Boolean = true,
    val showLightSectors: Boolean = true,
    val showQuality: Boolean = false,
    val respectScaleMinimum: Boolean = true,
    /** 仅控制所选资料的绘制，不改变查询来源或规划数据。 */
    val showDataOverlay: Boolean = true,
    /** 准星停稳后读取真实对象/像元；不对离散测深点插值。 */
    val showCursorInformation: Boolean = true,
    val showNavigationAids: Boolean = true,
) {
    /** 持久化损坏或旧版本字段不会产生 NaN、颠倒的水深分区。 */
    fun normalized(): ChartPortrayalPreferences {
        val safety = safetyDepthMeters.takeIf { it.isFinite() && it in 0.1..1000.0 } ?: 5.0
        return copy(
            safetyDepthMeters = safety,
            shallowDepthMeters = (shallowDepthMeters.takeIf { it.isFinite() } ?: 2.0).coerceIn(0.0, safety),
            deepDepthMeters = (deepDepthMeters.takeIf { it.isFinite() } ?: 30.0).coerceIn(safety, 12000.0),
        )
    }
}

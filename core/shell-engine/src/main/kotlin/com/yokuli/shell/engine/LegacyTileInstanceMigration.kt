package com.yokuli.shell.engine

import com.yokuli.shell.contract.TileBinding
import com.yokuli.shell.contract.TileBindingKind
import com.yokuli.shell.contract.TilePresentation

/** 中文：在产品别名和布局修复之前捕获每块旧磁贴，之后不再从 App 偏好覆盖。 */
object LegacyTileInstanceMigration {
    private val presetModes = mapOf(
        "tile.chart.cover" to ("chart" to "MAP"), "tile.chart.navigation" to ("chart" to "NAVIGATION"),
        "tile.instruments.speed" to ("instruments" to "SPEED"), "tile.instruments.heading" to ("instruments" to "HEADING"),
        "tile.instruments.depth" to ("instruments" to "DEPTH"), "tile.instruments.wind" to ("instruments" to "WIND"),
        "tile.anchor.distance" to ("anchor" to "DISTANCE"), "tile.voyages.recording" to ("voyages" to "RECORDING"),
        "tile.ais.traffic" to ("ais" to "TRAFFIC"), "tile.nmea.traffic" to ("nmea" to "TRAFFIC"),
    )

    fun migrate(state: LauncherPersistedState): LauncherPersistedState {
        val document = state.document ?: return state
        if (document.placements.all { it.binding != null }) return state
        val migrated = document.copy(
            schemaVersion = maxOf(document.schemaVersion, 2),
            placements = document.placements.map { entry ->
                if (entry.binding != null) entry else {
                    val preset = presetModes[entry.entryId.value]
                    val app = preset?.first ?: entry.entryId.value.removePrefix("app.")
                    val prefix = "$app.tile."
                    entry.copy(
                        binding = TileBinding("yokuli", TileBindingKind.APP, entry.entryId.value),
                        presentation = TilePresentation(
                            style = "summary",
                            legacyMode = preset?.second ?: state.appPreferenceValues[prefix + "mode"]?.removePrefix("c:") ?: "AUTO",
                            rotate = when (state.appPreferenceValues[prefix + "animate"]) { "b:0", "false" -> false; else -> true },
                            intervalSeconds = state.appPreferenceValues[prefix + "interval"]?.removePrefix("c:")?.toIntOrNull()?.takeIf { it in 1..120 } ?: 6,
                        ),
                    )
                }
            },
        )
        // 旧偏好键暂保留供旧应用级设置兼容读取；实例配置已不依赖这些键。
        return state.copy(document = migrated)
    }
}

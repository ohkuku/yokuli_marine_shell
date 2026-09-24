package com.yokuli.marine.shell.rebuild.chart

import org.json.JSONArray
import org.json.JSONObject

/**
 * 内置参考地图只读取 APK 里的真实地理资产。用户海图始终在其上方，
 * 地图底色、岸线和道路都不依赖联网、API Key、首次下载或用户海图文件夹。
 * 数据比例尺为 1:1000 万；不把概略河道、道路或岸线当作航行障碍物。
 */
internal object OfflineWorldStyle {
    fun sources(): JSONObject = JSONObject().apply {
        listOf("land", "lakes", "rivers", "boundaries", "roads").forEach { name ->
            put("world-$name", JSONObject()
                .put("type", "geojson")
                .put("data", "asset://maps/world_$name.geojson")
                .put("maxzoom", 10)
                .put("tolerance", .375))
        }
    }

    fun layers(): JSONArray = JSONArray().apply {
        put(layer("world-water", "background", null, JSONObject().put("background-color", "#dae8e9")))
        put(layer("world-land-fill", "fill", "world-land", JSONObject()
            .put("fill-color", "#ebe7da").put("fill-antialias", true)))
        put(layer("world-coast", "line", "world-land", JSONObject()
            .put("line-color", "#77999b").put("line-width", zoomWidth(.5, .75, 1.0))))
        put(layer("world-lakes-fill", "fill", "world-lakes", JSONObject()
            .put("fill-color", "#dae8e9").put("fill-antialias", true)))
        put(layer("world-lakes-shore", "line", "world-lakes", JSONObject()
            .put("line-color", "#8babad").put("line-width", zoomWidth(.3, .6, .8))))
        put(layer("world-rivers-line", "line", "world-rivers", JSONObject()
            .put("line-color", "#9cbfc3").put("line-width", zoomWidth(.3, .7, 1.2)))
            .put("minzoom", 4))
        put(layer("world-boundaries-line", "line", "world-boundaries", JSONObject()
            .put("line-color", "#b4a899").put("line-width", .7)
            .put("line-dasharray", JSONArray(listOf(3, 3)))).put("minzoom", 3))
        put(layer("world-roads-casing", "line", "world-roads", JSONObject()
            .put("line-color", "#d4c9b7").put("line-width", zoomWidth(.7, 1.5, 2.6)))
            .put("minzoom", 5))
        put(layer("world-roads-line", "line", "world-roads", JSONObject()
            .put("line-color", "#fff9e9").put("line-width", zoomWidth(.35, .8, 1.5)))
            .put("minzoom", 5))
    }

    private fun layer(id: String, type: String, source: String?, paint: JSONObject) = JSONObject()
        .put("id", id).put("type", type).put("paint", paint)
        .apply { source?.let { put("source", it) } }

    private fun zoomWidth(world: Double, region: Double, local: Double) =
        JSONArray(listOf("interpolate", JSONArray(listOf("linear")), JSONArray(listOf("zoom")),
            2, world, 7, region, 12, local))
}

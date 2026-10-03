package com.yokuli.marine.shell.rebuild.scene.ais

import com.google.android.filament.Engine
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.yokuli.marine.shell.rebuild.scene.navigation.MaritimeTerrainLayer
import com.yokuli.marine.shell.rebuild.scene.navigation.NavigationChartScene
import kotlin.math.cos

/** 交通视图只有坐标适配；网格上传、分块复用和资源生命周期与海图导航完全共用。 */
internal class AisTerrainLayer(
    engine: Engine, scene: Scene, loader: AssetLoader,
    requestFrame: () -> Unit, failure: (Throwable?) -> Unit,
) {
    private val shared = MaritimeTerrainLayer(engine, scene, loader, requestFrame, failure)
    val busy get() = shared.busy
    fun update(value: NavigationChartScene?) = shared.update(value)
    fun advance(frame: AisSceneFrame, allowUpload:Boolean=true) = shared.advance(frame.local,allowUpload) { origin ->
        val base = AisScenePosition(origin.lat, origin.lon)
        val point = frame.local.position(base)
        val east = frame.local.position(AisScenePosition(base.latitude, base.longitude + 1.0 / (111_320 * cos(Math.toRadians(base.latitude)).coerceAtLeast(.003))))
        val south = frame.local.position(AisScenePosition(base.latitude - 1.0 / 111_320, base.longitude))
        floatArrayOf(
            (east.x - point.x).toFloat(), 0f, (east.z - point.z).toFloat(), 0f,
            0f, 1f, 0f, 0f,
            (south.x - point.x).toFloat(), 0f, (south.z - point.z).toFloat(), 0f,
            point.x.toFloat(), 0f, point.z.toFloat(), 1f,
        )
    }
    fun close() = shared.close()
}

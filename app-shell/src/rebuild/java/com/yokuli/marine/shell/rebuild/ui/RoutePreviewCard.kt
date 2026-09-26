package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore

/** 预览是临时显示，不是编辑或导航。关闭就在地图上完成，收藏和草稿均保留。 */
@Composable internal fun RoutePreviewCard(os: OsStore) {
    val route = chartRoute(os)?.takeUnless { chartIsNavigating(os) } ?: return
    val colors = LocalMetro.current
    val inputEnabled = com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    var starting by rememberSaveable(route.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(colors.panel).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable(enabled = inputEnabled) { os.openLinked("route:${route.id}") }
            .padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(os.t("预览 · ", "Preview · ") + route.name, 17, maxLines = 1)
            Label(os.formatDistance(route.length) + os.t(" · ${route.targetIndices.size} 个航点", " · ${route.targetIndices.size} waypoints"), 13, colors.muted)
        }
        IconAction("play", os.t("开始这条航线的导航", "Start this route"), { if (inputEnabled) starting = true })
        IconAction("close", os.t("关闭航线预览", "Close route preview"), {
            if (inputEnabled) {
                starting = false
                os.shell.hideChartRoutePreview(route.id)
                os.save()
            }
        })
    }
    if (starting) StartNavigationDialog(os, route) { starting = false }
}

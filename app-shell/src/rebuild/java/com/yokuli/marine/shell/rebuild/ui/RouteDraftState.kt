package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.marine.shell.rebuild.*

/** 退出编辑回到正常地图；草稿仍保留，返回快照不能恢复已结束的编辑状态。 */
internal fun leaveRouteDraft(os:OsStore) {
    os.shell.hideChartRoutePreview()
    os.saveWithFeedback("草稿已保留，可从规划航线继续", "Draft retained; continue from Plan a route")
}
internal fun discardRouteDraft(os:OsStore) {
    os.shell.hideChartRoutePreview()
    os.draftRoute=emptyList();os.draftNavigationTargetIndices=null;os.editingRouteId=null;os.planningDraftUndo=null
    os.saveWithFeedback("已丢弃未保存的航线草稿", "Unsaved route draft discarded")
}
/** 调用者先确认替换现存草稿；不改收藏和运行中的导航。 */
internal fun loadRouteDraft(os:OsStore,route:Route) {
    os.editingRouteId=route.id
    os.draftRoute=route.points.toList();os.draftNavigationTargetIndices=route.navigationTargetIndices?.toList()
    os.planningDraftUndo=null;os.editingRoute=true;os.showCrosshair=true;os.ruler=emptyList();os.follow=false
    os.maps.view("chart",os.center,os.zoom).apply {planningLines=emptyList();planningPoints=emptyList();selectedPlaceId=null;selectedAisMmsi=null}
    os.fitRequest=route.points.takeIf {it.isNotEmpty()};os.save()
}

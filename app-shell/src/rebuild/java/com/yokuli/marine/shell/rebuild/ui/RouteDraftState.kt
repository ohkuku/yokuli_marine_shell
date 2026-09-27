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
    os.shell.hideChartRoutePreview()
    os.editingRouteId=route.id
    os.draftRoute=route.points.toList();os.draftNavigationTargetIndices=route.navigationTargetIndices?.toList()
    os.planningDraftUndo=null;os.editingRoute=true;os.showCrosshair=true;os.ruler=emptyList();os.follow=false
    os.maps.view("chart",os.center,os.zoom).apply {planningLines=emptyList();planningPoints=emptyList();selectedPlaceId=null;selectedAisMmsi=null}
    os.fitRequest=route.points.takeIf {it.isNotEmpty()};os.save()
}


/** 用户控制点（起点/目的地/途经点）与自动规划补出的形状点分开。旧路线没有索引时，每个点都仍是用户点。 */
internal fun routeDraftControlIndices(points:List<GeoPoint>,navigationTargetIndices:List<Int>?):List<Int> {
    if(points.isEmpty())return emptyList()
    if(navigationTargetIndices==null)return points.indices.toList()
    // 兼容旧 bug：自动规划后又追加 C 时，旧 targetIndices 没有同步；物理末点恢复为最新用户目标。
    return (listOf(0)+navigationTargetIndices+points.lastIndex).filter{it in points.indices}.distinct().sorted()
}
internal fun routeDraftControlPoints(points:List<GeoPoint>,navigationTargetIndices:List<Int>?):List<GeoPoint> =
    routeDraftControlIndices(points,navigationTargetIndices).map(points::get)

internal fun appendRouteDraftControlPoint(os:OsStore,point:GeoPoint) {
    val old=os.draftRoute
    if(!point.valid()||old.lastOrNull()?.let{distance(it,point)<1}==true)return
    val next=old+point
    os.draftRoute=next
    os.draftNavigationTargetIndices=os.draftNavigationTargetIndices?.let{targets->
        (targets.filter{it in old.indices&&it>0}+next.lastIndex).distinct().sorted()
    }
    os.planningDraftUndo=null
}
internal fun removeLastRouteDraftControlPoint(os:OsStore) {
    val points=os.draftRoute
    if(points.isEmpty())return
    val explicit=os.draftNavigationTargetIndices
    if(explicit==null) {
        os.draftRoute=points.dropLast(1)
    } else {
        val controls=routeDraftControlIndices(points,explicit)
        if(controls.size<=1) {
            os.draftRoute=emptyList();os.draftNavigationTargetIndices=null
        } else {
            val keep=controls.dropLast(1)
            os.draftRoute=points.take(keep.last()+1)
            os.draftNavigationTargetIndices=keep.drop(1).takeIf{it.isNotEmpty()}
        }
    }
    os.planningDraftUndo=null
}

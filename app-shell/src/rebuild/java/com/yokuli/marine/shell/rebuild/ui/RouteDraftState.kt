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


/** 手工移动任何自动形状点后，它就成为控制点；后续自动规划必须保留它。 */
internal fun moveRouteDraftPoint(os:OsStore,index:Int,point:GeoPoint) {
    val points=os.draftRoute
    if(index !in points.indices||!point.valid())return
    os.draftRoute=points.mapIndexed{i,p->if(i==index)point else p}
    os.draftNavigationTargetIndices=os.draftNavigationTargetIndices?.let{targets->
        (targets.filter{it in points.indices&&it>0}+listOfNotNull(index.takeIf{it>0},points.lastIndex.takeIf{points.size>1}))
            .distinct().sorted()
    }
    os.planningDraftUndo=null
}

/** 在折线的物理序列中插入人工控制点；insertIndex 是新点最终索引。 */
internal fun insertRouteDraftControlPoint(os:OsStore,insertIndex:Int,point:GeoPoint) {
    val old=os.draftRoute
    if(!point.valid()||old.isEmpty())return
    val index=insertIndex.coerceIn(1,old.size)
    if(old.getOrNull(index-1)?.let{distance(it,point)<2}==true||old.getOrNull(index)?.let{distance(it,point)<2}==true)return
    val next=old.toMutableList().also{it.add(index,point)}
    os.draftRoute=next
    os.draftNavigationTargetIndices=os.draftNavigationTargetIndices?.let{targets->
        val shifted=targets.filter{it in old.indices&&it>0}.map{if(it>=index)it+1 else it}
        (shifted+index+next.lastIndex).distinct().sorted()
    }
    os.planningDraftUndo=null
}

/** 删除当前折线中的任意编号点，并保持剩余业务目标映射合法。 */
internal fun deleteRouteDraftPoint(os:OsStore,index:Int) {
    val old=os.draftRoute
    if(index !in old.indices)return
    val next=old.filterIndexed{i,_->i!=index}
    val explicit=os.draftNavigationTargetIndices
    os.draftRoute=next
    os.draftNavigationTargetIndices=when {
        explicit==null||next.size<2->null
        else->{
            val shifted=explicit.filter{it in old.indices&&it>0&&it!=index}.map{if(it>index)it-1 else it}
            (shifted+next.lastIndex).filter{it>0&&it in next.indices}.distinct().sorted().takeIf{it.isNotEmpty()}
        }
    }
    os.planningDraftUndo=null
}

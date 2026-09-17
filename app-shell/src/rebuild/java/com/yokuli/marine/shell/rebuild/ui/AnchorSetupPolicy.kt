package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.anchorwatch.AnchorWatchInput
import com.yokuli.anchorwatch.domain.anchor.AnchorDepthSource
import com.yokuli.anchorwatch.domain.model.*
import com.yokuli.marine.shell.rebuild.GeoPoint

/** 当前船位模式的预览和启动命令都采用同一个可信船位；手动锚点保持用户确认的坐标。 */
internal fun effectiveAnchorPoint(picked:GeoPoint?,accepted:GeoPoint?,origin:AnchorCenterSource,estimate:Boolean):GeoPoint? =
    if(estimate || origin==AnchorCenterSource.CURRENT_POSITION)accepted else picked

/** 两个确认入口共用的草稿转换，避免高级设置返回后丢掉 BACKDOWN、水深和锚链。 */
internal fun anchorWatchInput(estimate:Boolean,origin:AnchorCenterSource,radius:Double,rode:Double?,depth:Double?,bowHeight:Double,boatLength:Double?,source:GpsDataSource,placeId:Long?,spotId:Long?):AnchorWatchInput {
    require(radius.isFinite() && radius>0)
    require(!estimate || rode!=null && rode.isFinite() && depth!=null && depth.isFinite() && depth>=0 && rode>depth+bowHeight)
    return AnchorWatchInput(
        if(estimate)AnchorPlacementMode.BACKDOWN else AnchorPlacementMode.CENTER_DROP,
        AnchorRangeMode.BASIC,AnchorSafetyPreset.BALANCED,
        if(estimate)depth else null,if(estimate)requireNotNull(rode) else 0.0,bowHeight,boatLength,radius,source,
        if(estimate)AnchorCenterSource.UNKNOWN else origin,true,AnchorDepthSource.MANUAL,
        originMode=when {estimate->AnchorOriginMode.BACKDOWN_FROM_ACCEPTED_POSITION;origin==AnchorCenterSource.CURRENT_POSITION->AnchorOriginMode.CURRENT_ACCEPTED_POSITION;origin==AnchorCenterSource.MANUAL_COORDINATES->AnchorOriginMode.MANUAL_COORDINATE;else->AnchorOriginMode.MAP_PICK},
        anchoragePlaceId=placeId,anchorageSpotId=spotId,
    )
}

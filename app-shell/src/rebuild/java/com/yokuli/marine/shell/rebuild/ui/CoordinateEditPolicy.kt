package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.marine.shell.rebuild.GeoPoint

/** 格式化只是显示。未编辑的轴保留原始 Double，不能把显示舍入写回收藏。 */
internal fun preservedCoordinate(original:GeoPoint?,initialLatitude:String,initialLongitude:String,latitude:String,longitude:String):GeoPoint? {
    val lat=if(original!=null && latitude==initialLatitude) original.lat else parseCoordinate(latitude,true)
    val lon=if(original!=null && longitude==initialLongitude) original.lon else parseCoordinate(longitude,false)
    return if(lat!=null && lon!=null)GeoPoint(lat,lon).takeIf(GeoPoint::valid) else null
}

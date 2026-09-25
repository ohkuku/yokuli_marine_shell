package com.yokuli.shell.contract

/** 只描述可表达的读数语义，不持有传感器或仪表的运行状态；目录与提交校验共同使用。 */
object TileReadingPresentationPolicy {
    val directionIds=setOf("HEADING_TRUE","COG","TRUE_WIND_DIRECTION","CURRENT_SET","WAYPOINT_BEARING")
    val attitudeIds=setOf("HEEL","PITCH")
    val windIds=setOf("TRUE_WIND_SPEED","APPARENT_WIND_SPEED")
    val counterIds=setOf("TOTAL_LOG","TRIP_LOG","IMPACT_COUNT")
    val supportedIds=setOf("SOG","HEADING_TRUE","DEPTH","UKC","COG","BOAT_SPEED","TRUE_WIND_SPEED","TRUE_WIND_DIRECTION","TRUE_WIND_ANGLE",
        "APPARENT_WIND_SPEED","APPARENT_WIND_ANGLE","HEEL","PITCH","ROLL_RATE","PITCH_RATE","ROLL_PERIOD","MOTION_SCORE","IMPACT_COUNT",
        "PRESSURE","PRESSURE_TREND_1H","PRESSURE_TREND_3H","PRESSURE_TREND_6H","RATE_OF_TURN","RUDDER_ANGLE","WATER_TEMPERATURE","AIR_TEMPERATURE",
        "CURRENT_SET","CURRENT_DRIFT","CROSS_TRACK_ERROR","WAYPOINT_BEARING","WAYPOINT_DISTANCE","TOTAL_LOG","TRIP_LOG","VMG","VMC","POSITION")

    fun styles(id:String):Set<String> = when {
        id=="POSITION"->setOf("simple","detail")
        id in directionIds||id in windIds->setOf("simple","compass","trend","detail")
        id in attitudeIds->setOf("simple","attitude","trend","detail")
        id in counterIds->setOf("simple","trend","detail")
        id in supportedIds->setOf("simple","gauge","trend","detail")
        else->setOf("simple","detail")
    }
    fun hasValidOptions(value:TilePresentation):Boolean {
        val minimum=value.rangeMinimum;val maximum=value.rangeMaximum
        return (value.historyMinutes==null||value.historyMinutes in 1..15)&&
            ((minimum==null&&maximum==null)||(minimum!=null&&maximum!=null&&minimum.isFinite()&&maximum.isFinite()&&
                kotlin.math.abs(minimum)<=1e12&&kotlin.math.abs(maximum)<=1e12&&minimum<maximum))
    }
}

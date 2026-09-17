package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.anchorwatch.domain.vessel.*

/** 中文：数字、量表和磁贴共用显示规则；历史读数不因此获得实时导航资格。 */
internal object InstrumentReadingPolicy {
    fun requiresFresh(tile: InstrumentTileId): Boolean = tile in setOf(
        InstrumentTileId.HEADING, InstrumentTileId.COG, InstrumentTileId.TRUE_WIND_DIRECTION,
        InstrumentTileId.CURRENT_SET, InstrumentTileId.WAYPOINT_BEARING,
        InstrumentTileId.HEEL, InstrumentTileId.PITCH, InstrumentTileId.ROLL_RATE, InstrumentTileId.PITCH_RATE,
    )

    fun isLive(freshness: VesselDataFreshness, quality: VesselDataQuality): Boolean =
        freshness == VesselDataFreshness.FRESH && quality != VesselDataQuality.UNKNOWN

    fun displayable(tile: InstrumentTileId, freshness: VesselDataFreshness, quality: VesselDataQuality = VesselDataQuality.GOOD): Boolean =
        !requiresFresh(tile) || isLive(freshness, quality)

    /** 风角以船艏为 0，左舷为负、右舷为正；不能先截断到正区间。 */
    fun signedWindFraction(degrees: Double): Float =
        if (degrees.isFinite()) (degrees.coerceIn(-180.0, 180.0) / 180.0).toFloat() else 0f
}

internal fun VesselObservation<*>.displayIsLive(): Boolean = InstrumentReadingPolicy.isLive(freshness, quality)

internal fun instrumentObservation(d: VesselDataSnapshot, tile: InstrumentTileId): VesselObservation<*> = when (tile) {
    InstrumentTileId.SOG -> d.sogKnots
    InstrumentTileId.COG -> d.cogTrueDegrees
    InstrumentTileId.HEADING -> d.headingTrueDegrees
    InstrumentTileId.DEPTH -> d.depthMeters
    InstrumentTileId.UKC -> d.derived.underKeelClearanceMeters
    InstrumentTileId.POSITION -> d.position
    InstrumentTileId.BOAT_SPEED -> d.speedThroughWaterKnots
    InstrumentTileId.TRUE_WIND_SPEED -> d.trueWind.speedKnots
    InstrumentTileId.TRUE_WIND_DIRECTION -> d.trueWind.directionDegrees
    InstrumentTileId.TRUE_WIND_ANGLE -> d.trueWind.angleDegrees
    InstrumentTileId.APPARENT_WIND_SPEED -> d.apparentWind.speedKnots
    InstrumentTileId.APPARENT_WIND_ANGLE -> d.apparentWind.angleDegrees
    InstrumentTileId.HEEL -> d.heelDegrees
    InstrumentTileId.PITCH -> d.pitchDegrees
    InstrumentTileId.ROLL_RATE -> d.rollRateDegreesPerSecond
    InstrumentTileId.PITCH_RATE -> d.pitchRateDegreesPerSecond
    InstrumentTileId.ROLL_PERIOD, InstrumentTileId.MOTION_SCORE, InstrumentTileId.IMPACT_COUNT -> d.motion
    InstrumentTileId.PRESSURE -> d.pressureHpa
    InstrumentTileId.PRESSURE_TREND_1H -> d.derived.pressureTrend1hHpa
    InstrumentTileId.PRESSURE_TREND_3H -> d.derived.pressureTrend3hHpa
    InstrumentTileId.PRESSURE_TREND_6H -> d.derived.pressureTrend6hHpa
    InstrumentTileId.RATE_OF_TURN -> d.rateOfTurnDegreesPerMinute
    InstrumentTileId.RUDDER_ANGLE -> d.rudderAngleDegrees
    InstrumentTileId.WATER_TEMPERATURE -> d.waterTemperatureCelsius
    InstrumentTileId.AIR_TEMPERATURE -> d.airTemperatureCelsius
    InstrumentTileId.CURRENT_SET -> d.currentSetTrueDegrees
    InstrumentTileId.CURRENT_DRIFT -> d.currentDriftKnots
    InstrumentTileId.CROSS_TRACK_ERROR -> d.crossTrackErrorNauticalMiles
    InstrumentTileId.WAYPOINT_BEARING -> d.waypointBearingTrueDegrees
    InstrumentTileId.WAYPOINT_DISTANCE -> d.waypointDistanceNauticalMiles
    InstrumentTileId.TOTAL_LOG -> d.totalLogNauticalMiles
    InstrumentTileId.TRIP_LOG -> d.tripLogNauticalMiles
    InstrumentTileId.VMG -> d.derived.vmgToWindKnots
    InstrumentTileId.VMC -> d.derived.vmcToWaypointKnots
}

/** 稳定的趋势键连接同一观测的详情、趋势和磁贴，不能按显示名称猜测来源或数值。 */
internal fun instrumentTrendKey(tile: InstrumentTileId): String? = when (tile) {
    InstrumentTileId.SOG -> "sog"
    InstrumentTileId.COG -> "cog"
    InstrumentTileId.HEADING -> "heading"
    InstrumentTileId.DEPTH -> "depth"
    InstrumentTileId.UKC -> "ukc"
    InstrumentTileId.POSITION -> null
    InstrumentTileId.BOAT_SPEED -> "bsp"
    InstrumentTileId.TRUE_WIND_SPEED -> "tws"
    InstrumentTileId.TRUE_WIND_DIRECTION -> "twd"
    InstrumentTileId.TRUE_WIND_ANGLE -> "twa"
    InstrumentTileId.APPARENT_WIND_SPEED -> "aws"
    InstrumentTileId.APPARENT_WIND_ANGLE -> "awa"
    InstrumentTileId.HEEL -> "heel"
    InstrumentTileId.PITCH -> "pitch"
    InstrumentTileId.ROLL_RATE -> "roll_rate"
    InstrumentTileId.PITCH_RATE -> "pitch_rate"
    InstrumentTileId.ROLL_PERIOD -> "roll_period"
    InstrumentTileId.MOTION_SCORE -> "motion"
    InstrumentTileId.IMPACT_COUNT -> "impacts"
    InstrumentTileId.PRESSURE -> "pressure"
    InstrumentTileId.PRESSURE_TREND_1H -> "pressure_1h"
    InstrumentTileId.PRESSURE_TREND_3H -> "pressure_3h"
    InstrumentTileId.PRESSURE_TREND_6H -> "pressure_6h"
    InstrumentTileId.RATE_OF_TURN -> "rot"
    InstrumentTileId.RUDDER_ANGLE -> "rudder"
    InstrumentTileId.WATER_TEMPERATURE -> "water"
    InstrumentTileId.AIR_TEMPERATURE -> "air"
    InstrumentTileId.CURRENT_SET -> "current_set"
    InstrumentTileId.CURRENT_DRIFT -> "current_drift"
    InstrumentTileId.CROSS_TRACK_ERROR -> "xte"
    InstrumentTileId.WAYPOINT_BEARING -> "waypoint_bearing"
    InstrumentTileId.WAYPOINT_DISTANCE -> "waypoint_distance"
    InstrumentTileId.TOTAL_LOG -> "total_log"
    InstrumentTileId.TRIP_LOG -> "trip_log"
    InstrumentTileId.VMG -> "vmg"
    InstrumentTileId.VMC -> "vmc"
}

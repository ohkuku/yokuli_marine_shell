package com.yokuli.anchorwatch.data.nmea.input

import com.yokuli.anchorwatch.data.nmea.NmeaMetric
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.sonar.DepthReference
import com.yokuli.anchorwatch.domain.sonar.DepthSentenceType

object NmeaCandidateMapper{
    fun map(envelope:ParsedNmeaEnvelope,profileId:String,generation:Long):List<VesselSourceCandidate<*>>{
        val update=envelope.update;val source=VesselSourceIdentity(
            id="nmea:$profileId:$generation:${envelope.fullSentenceId}",transportProfileId=profileId,connectionGeneration=generation,
            sourceType=VesselSourceType.NMEA_INPUT,talkerId=envelope.talkerId,sentenceType=envelope.sentenceType,fullSentenceId=envelope.fullSentenceId,
            displayName=envelope.fullSentenceId,stableKey="nmea:$profileId:${envelope.fullSentenceId}",
        )
        fun <T> candidate(metric:VesselMetricId,nmeaMetric:NmeaMetric,value:T,reference:VesselReference?=null)=VesselSourceCandidate(metric,value,source,VesselSourceClass.BOAT_NMEA,reference,update.measuredAt(nmeaMetric)?:envelope.receivedElapsedRealtime,update.utcMillis,VesselDataQuality.GOOD,CandidateValidity.ELIGIBLE,VesselProvenance.Nmea(source),update.heartbeatAt(nmeaMetric)?:envelope.receivedElapsedRealtime)
        return buildList{
            update.position?.takeIf{it.valid}?.let{fix->add(candidate(VesselMetricId.POSITION,NmeaMetric.POSITION,VesselPosition(fix.latitude,fix.longitude,fix.altitudeMeters,fix.horizontalAccuracyMeters,fix.satellites,fix.hdop)))}
            update.sog?.let{add(candidate(VesselMetricId.SOG,NmeaMetric.SOG,it))};update.cog?.let{add(candidate(VesselMetricId.COG,NmeaMetric.COG,it,VesselReference.TrueNorth))}
            update.trueHeading?.let{add(candidate(VesselMetricId.HEADING_TRUE,NmeaMetric.TRUE_HEADING,it,VesselReference.TrueNorth))};update.magneticHeading?.let{add(candidate(VesselMetricId.HEADING_MAGNETIC,NmeaMetric.MAGNETIC_HEADING,it,VesselReference.MagneticNorth))}
            update.speedThroughWaterKnots?.let{add(candidate(VesselMetricId.SPEED_THROUGH_WATER,NmeaMetric.SPEED_THROUGH_WATER,it,VesselReference.WaterReferenced))}
            update.depth?.let { rawValue ->
                val observation=update.depthObservation
                val reference=observation?.reference
                // 中文：DPT 保存的是换能器原始测深。只有报文明确给出目标基准和偏移时，
                // 在规范候选边界应用一次偏移；原始 observation 留给独立安全/记录链路，绝不改写。
                val measured=observation?.rawDepthMeters?:rawValue
                val offset=observation?.offsetMeters
                val depth=if(observation?.sentenceType==DepthSentenceType.DPT && offset!=null &&
                    reference in setOf(DepthReference.BELOW_SURFACE,DepthReference.BELOW_KEEL)) measured+offset else measured
                if(depth.isFinite()) add(candidate(VesselMetricId.DEPTH,NmeaMetric.DEPTH,depth,reference?.let{VesselReference.Depth(it)}))
            }
            update.apparentWindAngle?.let{add(candidate(VesselMetricId.APPARENT_WIND_ANGLE,NmeaMetric.APPARENT_WIND_ANGLE,signed(it)))};update.apparentWindSpeedKnots?.let{add(candidate(VesselMetricId.APPARENT_WIND_SPEED,NmeaMetric.APPARENT_WIND_SPEED,it))}
            update.trueWindAngle?.let{add(candidate(VesselMetricId.TRUE_WIND_ANGLE,NmeaMetric.TRUE_WIND_ANGLE,signed(it)))};update.trueWindSpeedKnots?.let{add(candidate(VesselMetricId.TRUE_WIND_SPEED,NmeaMetric.TRUE_WIND_SPEED,it))};update.trueWindDirection?.let{add(candidate(VesselMetricId.TRUE_WIND_DIRECTION,NmeaMetric.TRUE_WIND_DIRECTION,it,VesselReference.TrueNorth))}
        }
    }
    private fun signed(value:Double)=((value+540.0)%360.0)-180.0
}

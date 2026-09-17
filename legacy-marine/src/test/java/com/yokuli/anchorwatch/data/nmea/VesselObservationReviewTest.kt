package com.yokuli.anchorwatch.data.nmea

import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.vessel.source.*
import org.junit.Assert.*
import org.junit.Test

class VesselObservationReviewTest {
    @Test fun phoneAttitudePublicationStillUsesPhoneWhenDisplaySelectsBoat() {
        val phone=VesselSourceIdentity("phone:imu",sourceType=VesselSourceType.PHONE_SENSOR,displayName="Mounted phone")
        fun phoneField(metric:VesselMetricId,value:Double)=VesselSourceCandidate(metric,value,phone,VesselSourceClass.PHONE_IMU,
            receivedElapsedRealtime=1000,provenance=VesselProvenance.PhoneSensor("Mounted phone IMU",3))
        val candidates=mapOf(
            VesselMetricId.HEEL to listOf(phoneField(VesselMetricId.HEEL,2.4)),
            VesselMetricId.PITCH to listOf(phoneField(VesselMetricId.PITCH,1.2)),
            VesselMetricId.YAW_RATE to listOf(phoneField(VesselMetricId.YAW_RATE,0.5)),
        )
        val snapshot=VesselDataSnapshot(candidates=candidates,
            heelDegrees=VesselObservation(-9.0,VesselDataSource.BOAT_NMEA),
            pitchDegrees=VesselObservation(-8.0,VesselDataSource.BOAT_NMEA))
        val encoder=com.yokuli.anchorwatch.runtime.output.AnchorWatchNmeaFeedEncoder(com.yokuli.anchorwatch.data.sharing.NmeaOutputMux())
        val settings=com.yokuli.anchorwatch.data.vessel.NmeaDeviceOutputSettings(phoneAttitudeEnabled=true,phoneRateOfTurnEnabled=true)
        val attitude=encoder.encode(com.yokuli.anchorwatch.runtime.output.AnchorWatchNmeaStream.ATTITUDE,snapshot,settings,1100)
        assertEquals(1,attitude.sentences.size)
        assertTrue(attitude.sentences.single().contains("2.40,D,PHONE_HEEL"))
        assertTrue(attitude.sentences.single().contains("1.20,D,PHONE_PITCH"))
        assertFalse(attitude.sentences.single().contains("-9.00"))
        val rotation=encoder.encode(com.yokuli.anchorwatch.runtime.output.AnchorWatchNmeaStream.RATE_OF_TURN,snapshot,settings,1100)
        assertEquals(1,rotation.sentences.size);assertTrue(rotation.sentences.single().contains("30.00"))
    }
    @Test fun staleDerivedReadingsKeepActualTimeUntilSourceRemoved() {
        val source=VesselSourceIdentity("wind:epoch1",sourceType=VesselSourceType.NMEA_INPUT,displayName="Wind")
        val previous=VesselObservation(4.1,VesselDataSource.DERIVED,receivedElapsedRealtime=1000,freshness=VesselDataFreshness.FRESH,
            provenanceDetail=VesselProvenance.Derived("VMG",listOf(source)))
        val stale=VesselDisplayObservationPolicy.retainDerived(previous,VesselObservation(),setOf(source.id))
        assertEquals(4.1,stale.value!!,0.0);assertEquals(1000L,stale.receivedElapsedRealtime)
        assertEquals(VesselDataFreshness.STALE,stale.freshness)
        assertNull(VesselDisplayObservationPolicy.retainDerived(previous,VesselObservation(),setOf("wind:epoch2")).value)
    }
    private fun candidate(metric:VesselMetricId,value:Double,at:Long=1000,id:String="gps")=VesselSourceCandidate(metric,value,
        VesselSourceIdentity(id,transportProfileId="boat",connectionGeneration=1,sourceType=VesselSourceType.NMEA_INPUT,displayName="Same name"),
        VesselSourceClass.BOAT_NMEA,receivedElapsedRealtime=at)
    @Test fun slowDepthRetainsSixtySecondLeaseThenShowsOriginalAge() {
        val c=candidate(VesselMetricId.DEPTH,12.0)
        val arbiter=VesselSourceArbitrator()
        val fresh=VesselDisplayObservationPolicy.resolve(arbiter.select(c.metric,listOf(c),MetricSourcePreference(),31_000),null,31_000)!!
        assertEquals(VesselDataFreshness.FRESH,fresh.freshness)
        val stale=VesselDisplayObservationPolicy.resolve(arbiter.select(c.metric,listOf(c),MetricSourcePreference(),62_000),fresh,62_000)!!
        assertEquals(12.0,stale.value!!,0.0);assertEquals(VesselDataFreshness.STALE,stale.freshness)
        assertEquals(1000L,stale.receivedElapsedRealtime)
    }
    @Test fun emptyHeartbeatNeverRefreshesHeadingMeasurementTime() {
        val c=candidate(VesselMetricId.HEADING_TRUE,123.0).copy(sourceHeartbeatElapsedRealtime=20_000)
        val selected=VesselSourceArbitrator().select(c.metric,listOf(c),MetricSourcePreference(),21_000)
        val shown=VesselDisplayObservationPolicy.resolve(selected,null,21_000)!!
        assertEquals(VesselDataFreshness.HELD,shown.freshness)
        assertEquals(1000L,shown.receivedElapsedRealtime)
    }
    @Test fun removedDisabledOrDifferentSourceCannotResurrectDisplayValue() {
        val c=candidate(VesselMetricId.PRESSURE,1013.0)
        val old=VesselDisplayObservationPolicy.resolve(VesselSourceSelection(c,listOf(c)),null,1100)!!
        assertNull(VesselDisplayObservationPolicy.resolve(VesselSourceSelection<Double>(),old,2000))
        assertNull(VesselDisplayObservationPolicy.resolve(VesselSourceSelection(null,listOf(c.copy(validity=CandidateValidity.INVALID))),old,2000))
        assertNull(VesselDisplayObservationPolicy.resolve(VesselSourceSelection(null,listOf(c.copy(source=c.source.copy(id="other")))),old,2000))
    }
    @Test fun selectedAttitudePreservesPhoneIdentityAndNmeaPin() {
        val phone=VesselSourceIdentity("phone:imu",sourceType=VesselSourceType.PHONE_SENSOR,displayName="Phone IMU")
        val phoneHeel=VesselSourceCandidate(VesselMetricId.HEEL,4.0,phone,VesselSourceClass.PHONE_IMU,receivedElapsedRealtime=1000,provenance=VesselProvenance.PhoneSensor("Mounted IMU",3))
        val boatHeel=candidate(VesselMetricId.HEEL,-8.0)
        val selection=VesselSourceArbitrator().select(VesselMetricId.HEEL,listOf(phoneHeel,boatHeel),MetricSourcePreference(pinnedSourceId="gps"),1100)
        val selected=VesselDisplayObservationPolicy.resolve(selection,null,1100)!!
        assertEquals(-8.0,selected.value!!,0.0);assertEquals("gps",selected.sourceIdentity?.id)
        val heel=VesselDisplayObservationPolicy.resolve(VesselSourceSelection(phoneHeel,listOf(phoneHeel)),null,1100)!!
        val pitch=heel.copy(value=2.0)
        val attitude=VesselDisplayObservationPolicy.attitude(heel,pitch,VesselObservation(),VesselObservation(),VesselObservation())
        assertEquals(phone,attitude.sourceIdentity);assertTrue(attitude.provenanceDetail is VesselProvenance.PhoneSensor)
        assertTrue(attitude.value!!.rollRateDegreesPerSecond.isNaN())
    }
    @Test fun ggaPositionUsesIndependentVtgMotionWithoutChangingPositionTime() {
        val parser=Nmea0183Parser()
        val gga=parser.parse(NmeaChecksum.append("GPGGA,120000,3648.000,S,17445.000,E,1,08,0.9,0.0,M,0.0,M,,"),elapsed=1000)!!
        val vtg=parser.parse(NmeaChecksum.append("GPVTG,72.0,T,,M,5.0,N,9.26,K,A"),elapsed=1800)!!
        val position=gga.position!!
        assertNull(position.sogKnots)
        val snapshot=VesselDataSnapshot(sogKnots=VesselObservation(vtg.sog,receivedElapsedRealtime=1800),cogTrueDegrees=VesselObservation(vtg.cog,receivedElapsedRealtime=1800))
        val result=VesselFixProjection.compose(position,snapshot)
        assertEquals(5.0,result.sogKnots!!,0.0);assertEquals(72.0,result.cogTrueDegrees!!,0.0)
        assertEquals(position.receivedElapsedRealtime,result.receivedElapsedRealtime)
        assertEquals(1800L,result.sogReceivedElapsedRealtime)
        assertNull(result.headingTrueDegrees)
        val stopped=VesselFixProjection.compose(result,snapshot.copy(sogKnots=VesselObservation()))
        assertNull(stopped.sogKnots)
    }
}

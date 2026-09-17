package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.anchorwatch.data.database.TrackPointEntity
import com.yokuli.anchorwatch.domain.model.*
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.Route
import org.junit.Assert.*
import org.junit.Test

class ChartAnchorPolicyTest {
    private val a=GeoPoint(-36.841234567,174.7654321)
    private val b=GeoPoint(-36.841,174.766)

    @Test fun currentAnchorPreviewFollowsTheAcceptedPositionUsedAtStart() {
        assertEquals(b,effectiveAnchorPoint(a,b,AnchorCenterSource.CURRENT_POSITION,false))
        assertNull(effectiveAnchorPoint(a,null,AnchorCenterSource.CURRENT_POSITION,false))
        assertEquals(a,effectiveAnchorPoint(a,b,AnchorCenterSource.MAP_PICK,false))
    }

    @Test fun estimateDraftKeepsBackdownGeometryAndUnknownCentre() {
        val request=anchorWatchInput(true,AnchorCenterSource.CURRENT_POSITION,50.0,40.0,6.0,1.0,9.0,GpsDataSource.NMEA,12,15)
        assertEquals(AnchorPlacementMode.BACKDOWN,request.placement)
        assertEquals(AnchorOriginMode.BACKDOWN_FROM_ACCEPTED_POSITION,request.originMode)
        assertEquals(AnchorCenterSource.UNKNOWN,request.centerSource)
        assertEquals(40.0,request.rodeMeters,0.0)
        assertEquals(6.0,request.depthMeters!!,0.0)
        assertEquals(12L,request.anchoragePlaceId)
        assertEquals(15L,request.anchorageSpotId)
    }

    @Test(expected=IllegalArgumentException::class) fun impossibleEstimationGeometryCannotStart() {
        anchorWatchInput(true,AnchorCenterSource.CURRENT_POSITION,30.0,5.0,6.0,1.0,9.0,GpsDataSource.SYSTEM,null,null)
    }

    @Test fun manualCoordinatesKeepTheirOriginAcrossBothConfirmEntrypoints() {
        val request=anchorWatchInput(false,AnchorCenterSource.MANUAL_COORDINATES,35.0,null,null,1.0,9.0,GpsDataSource.SYSTEM,null,null)
        assertEquals(AnchorPlacementMode.CENTER_DROP,request.placement)
        assertEquals(AnchorOriginMode.MANUAL_COORDINATE,request.originMode)
        assertNull(request.depthMeters)
    }

    @Test fun editingNameDoesNotRoundStoredCoordinates() {
        val lat="36° 50′ 28.4″ S";val lon="174° 45′ 55.6″ E"
        assertEquals(a,preservedCoordinate(a,lat,lon,lat,lon))
    }

    @Test fun editingOneAxisPreservesTheOtherAxisPrecision() {
        val result=preservedCoordinate(a,"latitude","longitude","-36.842","longitude")!!
        assertEquals(-36.842,result.lat,0.0)
        assertEquals(a.lon,result.lon,0.0)
    }

    @Test fun invalidCoordinateEditsCannotReplaceSavedPoint() {
        assertNull(preservedCoordinate(a,"latitude","longitude","999","longitude"))
    }

    @Test fun editedSavedRouteCannotChangeActiveGuidanceVersion() {
        val frozen=Route("r","departure",listOf(a,b))
        val edited=frozen.copy(name="edited",points=listOf(b,a,b))
        assertSame(frozen,resolveChartRoute("r",frozen,listOf(edited)))
        assertSame(frozen,resolveChartRoute(null,frozen,listOf(edited)))
        assertEquals(2,resolveChartRoute("r",frozen,listOf(edited))!!.points.size)
    }

    @Test fun selectingAnotherRoutePreviewsItsSavedVersion() {
        val frozen=Route("r","departure",listOf(a,b))
        val other=Route("s","other",listOf(b,a))
        assertSame(other,resolveChartRoute("s",frozen,listOf(other)))
    }

    private fun sample(time:Long,source:String="NMEA")=TrackPointEntity(
        sessionId=1,timestamp=time,latitude=a.lat,longitude=a.lon,distanceFromAnchor=1.0,
        sog=null,cog=null,heading=null,hdop=null,positionSource=source,
    )

    @Test fun sameWatchGapIsBrokenInLiveAndReview() {
        assertTrue(anchorSamplesConnected(sample(1_000),sample(10_000),15_000))
        assertFalse(anchorSamplesConnected(sample(1_000),sample(31_000),15_000))
        assertFalse(anchorSamplesConnected(sample(1_000),sample(10_000,"SYSTEM"),15_000))
        assertFalse(anchorSamplesConnected(sample(10_000),sample(1_000),15_000))
    }

    @Test fun quarantinedPositionDoesNotJoinTheTrail() {
        assertFalse(anchorSamplesConnected(sample(1_000),sample(2_000).copy(wasQuarantined=true),15_000))
    }
}

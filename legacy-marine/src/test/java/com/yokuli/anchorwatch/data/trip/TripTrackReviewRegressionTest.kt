package com.yokuli.anchorwatch.data.trip

import org.junit.Assert.*
import org.junit.Test

class TripTrackReviewRegressionTest {
    @Test fun explicitLiveTailGapCannotBeRejoinedWithinFifteenSeconds() {
        val durable=segment(0,3)
        val separator=TripTrackPoint(1,5,5000,null,null)
        val live=segment(3,2).points+separator+segment(6,2).points
        val result=TripTrackSnapshot(1,listOf(durable),live).rendered(3000)
        assertEquals(2,result.size)
        assertEquals(listOf(0L,1L,2L,3L,4L),result.first().points.map{it.recordingSequence})
        assertEquals(listOf(6L,7L),result.last().points.map{it.recordingSequence})
    }
    @Test fun persistedOrLeadingLiveSeparatorPreventsBoundaryMerge() {
        val durable=segment(0,3)
        val separator=TripTrackPoint(1,3,3000,null,null)
        val live=segment(4,2).points
        assertEquals(2,TripTrackSnapshot(1,listOf(durable),live,lastPersistedPoint=separator).rendered(3000).size)
        assertEquals(2,TripTrackSnapshot(1,listOf(durable),listOf(separator)+live).rendered(3000).size)
    }
    private fun segment(start:Int,count:Int)=TripTrackSegment((start until start+count).map{TripTrackPoint(1,it.toLong(),it*1000L,-36.0+it*0.000001,174.0)})
    @Test fun shortRecentTailNeverJoinsPreviousSegment() {
        listOf(4 to 5,3000 to 3000,5000 to 5118).forEach{(budget,oldSize)->
            val old=segment(0,oldSize);val recent=segment(oldSize+30,2)
            val result=TripTrackRenderPolicy.withBudget(listOf(old,recent),budget)
            assertEquals(2,result.size);assertEquals(recent,result.last())
            assertTrue(result.sumOf{it.points.size}<=budget)
            assertTrue(result.first().points.all{it.recordingSequence<oldSize})
        }
    }
    @Test fun partialRecentTailJoinsOnlyItsOwnOriginalSegment() {
        val result=TripTrackRenderPolicy.withBudget(listOf(segment(0,30),segment(100,100)),30)
        assertEquals(2,result.size)
        assertTrue(result.first().points.all{it.recordingSequence<30})
        assertTrue(result.last().points.all{it.recordingSequence>=100})
    }
    @Test fun nullPositionAndTimeGapSurviveCompaction() {
        val points=segment(0,20).points+TripTrackPoint(1,21,21000,null,null)+segment(22,20).points+segment(100,30).points
        val compact=TripTrackRenderPolicy.compact(points,12)
        assertTrue(compact.size<=12)
        assertEquals(3,TripTrackRenderPolicy.segment(compact).size)
    }
}

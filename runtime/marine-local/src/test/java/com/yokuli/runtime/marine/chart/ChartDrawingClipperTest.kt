package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDrawingClipperTest {
    private val center=ChartPoint(-36.84,174.77)

    private fun ring(delta:Double)=listOf(
        ChartPoint(center.latitude-delta,center.longitude-delta),
        ChartPoint(center.latitude-delta,center.longitude+delta),
        ChartPoint(center.latitude+delta,center.longitude+delta),
        ChartPoint(center.latitude+delta,center.longitude-delta),
        ChartPoint(center.latitude-delta,center.longitude-delta),
    )

    private fun source(scale:Int)=ChartFeatureSource(
        datasetId="d",cellId="mixed",edition=1,update=0,producer=0,compilationScale=scale,
        intendedUsage=0,horizontalDatum=null,verticalDatum=1,soundingDatum=1,issueDate=null,
    )

    private fun area(id:String,scale:Int,delta:Double)=NauticalFeature(
        id=id,datasetId="d",cellId="mixed",objectClass=0,acronym="DEPARE",
        kind=NauticalFeatureKind.DEPTH_AREA,
        geometry=ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(ring(delta)))),
        attributes=emptyMap(),depth=DepthEvidence(DepthEvidenceKind.INTERVAL,5.0,10.0,datum="1"),
        source=source(scale),
    )

    private fun contains(geometry:ChartGeometry,point:ChartPoint):Boolean {
        fun inside(ring:List<ChartPoint>):Boolean {
            var inside=false;var previous=ring.last()
            for(current in ring) {
                if((previous.latitude>point.latitude)!=(current.latitude>point.latitude)) {
                    val crossing=(current.longitude-previous.longitude)*(point.latitude-previous.latitude)/
                        (current.latitude-previous.latitude)+previous.longitude
                    if(point.longitude<crossing)inside=!inside
                }
                previous=current
            }
            return inside
        }
        var coverage=0
        for(part in geometry.parts)if(inside(part.points))coverage+=if(part.hole)-1 else 1
        return coverage>0
    }

    @Test fun finerDepthAreaCutsCoarserAreaInsideMixedCell()=runBlocking {
        val coarse=area("coarse",90_000,.01)
        val fine=area("fine",4_000,.003)
        val cell=ChartCellRevision(
            cellId="mixed",edition=1,update=0,intendedUsage=0,compilationScale=null,issueDate=null,
            featureCount=2,bounds=listOf(ChartBounds(174.7,-36.9,174.9,-36.7)),
        )
        val dataset=ChartDataset(
            id="d",name="test",revision=1,installedAtUtc=0,
            eligibility=DataEligibility(automatic=true),cells=listOf(cell)
        )
        val result=ChartDrawingClipper.compose(
            ChartDataSnapshot("s",1,listOf(dataset)),
            listOf(coarse,fine),
            ChartBounds(174.7,-36.9,174.9,-36.7),
        )
        val drawnFine=result.features.firstOrNull{it.id=="fine"}
        val drawnCoarse=result.features.firstOrNull{it.id=="coarse"}
        assertNotNull(drawnFine)
        assertNotNull(drawnCoarse)
        assertTrue(contains(requireNotNull(drawnFine).geometry,center))
        assertFalse(contains(requireNotNull(drawnCoarse).geometry,center))
    }

    @Test fun coarseHazardSurvivesFineDepthOwnership()=runBlocking {
        val fine=area("fine",4_000,.003)
        val rock=NauticalFeature(
            id="rock",datasetId="d",cellId="mixed",objectClass=0,acronym="UWTROC",
            kind=NauticalFeatureKind.ROCK,
            geometry=ChartGeometry(ChartGeometryKind.POINT,listOf(ChartGeometryPart(listOf(center)))),
            attributes=emptyMap(),depth=null,source=source(90_000),
        )
        val cell=ChartCellRevision(
            cellId="mixed",edition=1,update=0,intendedUsage=0,compilationScale=null,issueDate=null,
            featureCount=2,bounds=listOf(ChartBounds(174.7,-36.9,174.9,-36.7)),
        )
        val dataset=ChartDataset(
            id="d",name="test",revision=1,installedAtUtc=0,
            eligibility=DataEligibility(automatic=true),cells=listOf(cell)
        )
        val result=ChartDrawingClipper.compose(
            ChartDataSnapshot("s",1,listOf(dataset)),
            listOf(fine,rock),
            ChartBounds(174.7,-36.9,174.9,-36.7),
        )
        assertTrue(result.features.any{it.id=="rock"})
    }
}

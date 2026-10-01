package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartCursorHitTestTest {
    private val center=GeoPoint(-36.84,174.77)

    private fun source(scale:Int?=4_000)=ChartFeatureSource(
        datasetId="d",cellId="c",edition=1,update=0,producer=0,compilationScale=scale,
        intendedUsage=0,horizontalDatum=null,verticalDatum=1,soundingDatum=1,issueDate=null,
    )

    private fun ring(delta:Double)=listOf(
        ChartPoint(center.lat-delta,center.lon-delta),
        ChartPoint(center.lat-delta,center.lon+delta),
        ChartPoint(center.lat+delta,center.lon+delta),
        ChartPoint(center.lat+delta,center.lon-delta),
        ChartPoint(center.lat-delta,center.lon-delta),
    )

    @Test fun multipolygonIslandInsideHoleStillHits() {
        val outer=ring(.01)
        val hole=ring(.004)
        val island=ring(.001)
        val feature=NauticalFeature(
            id="area",datasetId="d",cellId="c",objectClass=0,acronym="DEPARE",
            kind=NauticalFeatureKind.DEPTH_AREA,
            geometry=ChartGeometry(ChartGeometryKind.POLYGON,listOf(
                ChartGeometryPart(outer),
                ChartGeometryPart(hole,hole=true),
                ChartGeometryPart(island),
            )),
            attributes=emptyMap(),depth=DepthEvidence(DepthEvidenceKind.INTERVAL,5.0,10.0,datum="1"),
            source=source(),
        )
        assertTrue(chartObjectsAt(listOf(feature),center,16.0,limit=10).any{it.id=="area"})
        assertEquals(0.0,chartFeatureDistance(feature,center),.001)
    }

    @Test fun nearestSoundingWinsBeforeHitLimit() {
        val soundings=(0 until 600).map {index->
            val offset=.00001*(index+1)
            NauticalFeature(
                id="n$index",datasetId="d",cellId="c",objectClass=0,acronym="SOUNDG",
                kind=NauticalFeatureKind.SOUNDING,
                geometry=ChartGeometry(ChartGeometryKind.POINT,listOf(ChartGeometryPart(listOf(
                    ChartPoint(center.lat,center.lon+offset,5.0)
                )))),
                attributes=emptyMap(),depth=DepthEvidence(DepthEvidenceKind.POINT,pointMeters=5.0,datum="1"),
                source=source(),
            )
        }.reversed()
        val picked=chartObjectsAt(soundings,center,18.0,radiusMeters=150.0,limit=10)
        assertEquals("n0",picked.first().id)
    }

    @Test fun denseSoundingsCannotEvictContainingDepthArea() {
        val area=NauticalFeature(
            id="area",datasetId="d",cellId="c",objectClass=0,acronym="DEPARE",
            kind=NauticalFeatureKind.DEPTH_AREA,
            geometry=ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(ring(.002)))),
            attributes=emptyMap(),depth=DepthEvidence(DepthEvidenceKind.INTERVAL,5.0,10.0,datum="1"),
            source=source(),
        )
        val soundings=(0 until 700).map {index->
            NauticalFeature(
                id="s$index",datasetId="d",cellId="c",objectClass=0,acronym="SOUNDG",
                kind=NauticalFeatureKind.SOUNDING,
                geometry=ChartGeometry(ChartGeometryKind.POINT,listOf(ChartGeometryPart(listOf(
                    ChartPoint(center.lat,center.lon,6.0+index*.001)
                )))),
                attributes=emptyMap(),depth=DepthEvidence(DepthEvidenceKind.POINT,pointMeters=6.0+index*.001,datum="1"),
                source=source(),
            )
        }
        val features=listOf(area)+soundings
        val cell=ChartCellRevision(
            cellId="c",edition=1,update=0,intendedUsage=0,compilationScale=4_000,issueDate=null,
            featureCount=features.size,bounds=listOf(ChartBounds(174.7,-36.9,174.9,-36.7)),
        )
        val layer=ChartCursorLayer(
            key="test:full",datasetId="d",datasetRevision=1,datasetName="test",
            bounds=ChartBounds(174.7,-36.9,174.9,-36.7),cells=listOf(cell),
            features=features,rasters=emptyList(),incomplete=false,
            residentIndex=CursorResidentIndex(features),
        )
        val probe=layer.probe(center,18.0)
        assertTrue(probe.features.any{it.id=="area"&&it.kind==NauticalFeatureKind.DEPTH_AREA})
    }
}

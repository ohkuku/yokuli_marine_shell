package com.yokuli.runtime.contract.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartDetailScaleTest {
    private fun feature(attributes:Map<String,String> = emptyMap(),compilationScale:Int? = null)=NauticalFeature(
        id="f",datasetId="d",cellId="c",objectClass=0,acronym="DEPARE",
        kind=NauticalFeatureKind.DEPTH_AREA,
        geometry=ChartGeometry(ChartGeometryKind.NONE,emptyList()),
        attributes=attributes,depth=null,
        source=ChartFeatureSource("d","c",1,0,0,compilationScale,0,null,null,null,null),
    )

    @Test fun `explicit compilation scale wins`() {
        assertEquals(12_000,feature(mapOf("YOKULI_DETAIL_SCALE" to "4000"),12_000).detailScaleDenominator())
    }

    @Test fun `new feature detail scale is read`() {
        assertEquals(22_000,feature(mapOf("YOKULI_DETAIL_SCALE" to "22000")).detailScaleDenominator())
    }

    @Test fun `legacy LINZ layer title recovers detail tier`() {
        assertEquals(90_000,feature(mapOf("LINZ_LDS_LAYER" to "Depth area polygon (Hydro, 1:90k - 1:350k)")).detailScaleDenominator())
        assertEquals(350_000,feature(mapOf("LINZ_LDS_LAYER" to "Land area polygon (Hydro, 1:350k - 1:1,500k)")).detailScaleDenominator())
    }


    @Test fun `arbitrary ENC scales map to stable semantic tiers`() {
        assertEquals(0,detailTierForScale(12_000))
        assertEquals(1,detailTierForScale(22_000))
        assertEquals(1,detailTierForScale(50_000))
        assertEquals(2,detailTierForScale(90_000))
        assertEquals(2,detailTierForScale(180_000))
        assertEquals(3,detailTierForScale(350_000))
        assertEquals(3,detailTierForScale(700_000))
        assertEquals(4,detailTierForScale(1_500_000))
        assertEquals(4,detailTierForScale(2_000_000))
        assertEquals(1,feature(compilationScale=50_000).detailTier())
    }

    @Test fun `coverage tier survives independently of compilation scale`() {
        val coverage=CoverageEvidence(
            "cov","cell",ChartGeometry(ChartGeometryKind.NONE,emptyList()),true,null,detailTier=2
        )
        assertEquals(2,coverage.resolvedDetailTier())
    }

    @Test fun `unscaled feature stays unscaled`() {
        assertNull(feature().detailScaleDenominator())
    }
}

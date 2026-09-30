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

    @Test fun `unscaled feature stays unscaled`() {
        assertNull(feature().detailScaleDenominator())
    }
}

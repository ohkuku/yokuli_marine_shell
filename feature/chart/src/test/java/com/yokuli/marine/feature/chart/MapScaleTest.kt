package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapCamera
import org.junit.Assert.assertEquals
import org.junit.Test

class MapScaleTest {
    @Test
    fun scaleUsesViewportPixelsZoomAndLatitudeInsteadOfAStaticLabel() {
        val equator = MapCamera(GeoPoint(0.0, 0.0), zoom = 0.0)
        val zoomed = equator.copy(zoom = 1.0)
        val latitudeSixty = MapCamera(GeoPoint(60.0, 0.0), zoom = 0.0)

        val equatorScale = equator.scaleNauticalMilesForPixels(100.0)
        assertEquals(8_452.6476, equatorScale, 0.001)
        assertEquals(equatorScale / 2.0, zoomed.scaleNauticalMilesForPixels(100.0), 0.001)
        assertEquals(equatorScale / 2.0, latitudeSixty.scaleNauticalMilesForPixels(100.0), 0.001)
        assertEquals(0.0, equator.scaleNauticalMilesForPixels(0.0), 0.0)
    }
}

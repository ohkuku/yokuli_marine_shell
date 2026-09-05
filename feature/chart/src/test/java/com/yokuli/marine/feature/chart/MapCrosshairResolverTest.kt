package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapHitResult
import com.yokuli.marine.map.domain.MapOverlayId
import com.yokuli.marine.map.domain.MapRendererQueryPort
import com.yokuli.marine.map.domain.MapScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapCrosshairResolverTest {
    @Test
    fun `crosshair resolves the actual renderer viewport centre`() {
        var requested: MapScreenPoint? = null
        val expected = GeoPoint(-36.8485, 174.7633)
        val port = object : MapRendererQueryPort {
            override fun project(point: GeoPoint) = null
            override fun unproject(point: MapScreenPoint): GeoPoint {
                requested = point
                return expected
            }
            override fun query(point: MapScreenPoint, overlayIds: Set<MapOverlayId>): List<MapHitResult> = emptyList()
        }

        assertEquals(expected, MapCrosshairResolver.resolve(port, widthPx = 361, heightPx = 359))
        assertEquals(MapScreenPoint(180.5, 179.5), requested)
    }

    @Test
    fun `crosshair remains unavailable until renderer query port and viewport exist`() {
        val port = object : MapRendererQueryPort {
            override fun project(point: GeoPoint) = null
            override fun unproject(point: MapScreenPoint) = GeoPoint(0.0, 0.0)
            override fun query(point: MapScreenPoint, overlayIds: Set<MapOverlayId>): List<MapHitResult> = emptyList()
        }

        assertNull(MapCrosshairResolver.resolve(null, 360, 360))
        assertNull(MapCrosshairResolver.resolve(port, 0, 360))
        assertNull(MapCrosshairResolver.resolve(port, 360, 0))
    }
}

package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.MapEditTarget
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapHitResult
import com.yokuli.marine.map.domain.MapOverlayId
import com.yokuli.marine.map.domain.MapScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointDragContractTest {
    @Test
    fun `touch slop separates handle tap from drag`() {
        val down = MapScreenPoint(10.0, 10.0)
        assertFalse(PointDragMotion.hasMoved(down, MapScreenPoint(12.0, 13.0), 5.0))
        assertTrue(PointDragMotion.hasMoved(down, MapScreenPoint(14.0, 14.0), 5.0))
    }

    @Test
    fun `route handle identity keeps draft tokens containing separators`() {
        val hit = MapHitResult(MapOverlayId.MANUAL_ROUTE_POINTS, "route-point:draft:imported:3")
        assertEquals(MapEditTarget.RoutePoint("draft:imported", 3), hit.toEditTargetOrNull())
    }

    @Test
    fun `GeoJSON conversion preserves longitude latitude axis order`() {
        val source = GeoPoint(-36.8485, 174.7633)
        val rendered = source.toGeoJsonPoint()

        assertEquals(source.longitude, rendered.longitude(), 0.0)
        assertEquals(source.latitude, rendered.latitude(), 0.0)
    }
}

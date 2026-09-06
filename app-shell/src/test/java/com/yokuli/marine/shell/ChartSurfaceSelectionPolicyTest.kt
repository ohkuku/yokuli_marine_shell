package com.yokuli.marine.shell

import com.yokuli.marine.map.domain.MapViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartSurfaceSelectionPolicyTest {
    @Test
    fun `configured Google adapter serves explicit standard and satellite views even with a local library`() {
        assertEquals(ChartSurfaceKind.GOOGLE, chartSurfaceKind(MapViewMode.STANDARD, true))
        assertEquals(ChartSurfaceKind.GOOGLE, chartSurfaceKind(MapViewMode.SATELLITE, true))
    }

    @Test
    fun `marine remains offline and an unconfigured connected adapter falls back safely`() {
        assertEquals(ChartSurfaceKind.OFFLINE, chartSurfaceKind(MapViewMode.MARINE, true))
        assertEquals(ChartSurfaceKind.OFFLINE, chartSurfaceKind(MapViewMode.STANDARD, false))
        assertEquals(ChartSurfaceKind.OFFLINE, chartSurfaceKind(MapViewMode.SATELLITE, false))
    }
}

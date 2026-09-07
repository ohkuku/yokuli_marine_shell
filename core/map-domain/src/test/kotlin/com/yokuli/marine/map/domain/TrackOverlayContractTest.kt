package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrackOverlayContractTest {
    @Test
    fun `actual track segments are runtime overlays independent of planned navigation`() {
        val original = MapState(
            activeNavigationRoute = listOf(GeoPoint(-36.85, 174.76), GeoPoint(-36.80, 174.82)),
            navigationActive = true,
        )
        val actual = listOf(
            listOf(GeoPoint(-36.85, 174.76), GeoPoint(-36.84, 174.77)),
            listOf(GeoPoint(-36.83, 174.78)),
        )

        val changed = MapReducer.reduce(original, MapAction.ActiveTrackChanged(actual)).state

        assertEquals(actual, changed.activeTrackSegments)
        assertEquals(original.activeNavigationRoute, changed.activeNavigationRoute)
        assertFalse(MapOverlayId.ACTIVE_TRACK == MapOverlayId.ACTIVE_NAVIGATION_LEG)
    }
}

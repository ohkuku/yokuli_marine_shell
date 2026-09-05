package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.PlaceCategory
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import com.yokuli.shell.contract.LaunchToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLauncherIntegrationContractTest {
    private val place = SavedPlace("place-1", "Quiet anchorage", GeoPoint(-36.8, 174.7), category = PlaceCategory.ANCHORAGE)
    private val route = SavedRoute("route-1", "Island plan", listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.9, 174.8)))

    @Test
    fun `deep links round trip bounded unicode ids`() {
        listOf("id-1", "锚地 A/1", "route:revision:4").forEach { id ->
            assertEquals(ChartLaunchTarget.Place(id), ChartDestinations.parse(ChartDestinations.place(id)))
            assertEquals(ChartLaunchTarget.Route(id), ChartDestinations.parse(ChartDestinations.route(id)))
        }
        assertEquals(ChartLaunchTarget.Browse, ChartDestinations.parse(ChartDestinations.Browse))
        assertNull(ChartDestinations.parse(LaunchToken("chart.route.not-hex")))
    }

    @Test
    fun `route link previews without starting navigation and missing link becomes operable unavailable state`() {
        assertEquals(MapAction.PreviewRoutePlan(route.id), ChartLaunchProjector.action(ChartLaunchTarget.Route(route.id), MapState(savedRoutes = listOf(route))))
        assertEquals(
            MapAction.OpenSurface(com.yokuli.marine.map.domain.MapSurface.RouteDetail("deleted")),
            ChartLaunchProjector.action(ChartLaunchTarget.Route("deleted"), MapState()),
        )
        assertFalse(MapState(savedRoutes = listOf(route)).navigationActive)
    }

    @Test
    fun `place and route search contributions contain only generic shell facts`() {
        val results = ChartSearchProjection.search(MapState(places = listOf(place), savedRoutes = listOf(route)), "island")
        assertEquals(1, results.size)
        assertEquals(ChartSearchKind.ROUTE, results.single().kind)
        assertEquals(route.name, results.single().title)
        assertEquals(ChartLaunchTarget.Route(route.id), ChartDestinations.parse(results.single().token))

        val chinese = ChartSearchProjection.search(MapState(places = listOf(place), savedRoutes = listOf(route)), "锚地")
        assertEquals(place.id, chinese.single().sourceId)
        assertTrue(ChartDestinations.accepts(chinese.single().token))
        assertTrue(ChartSearchProjection.search(MapState(places = listOf(place)), "").isEmpty())
    }
}

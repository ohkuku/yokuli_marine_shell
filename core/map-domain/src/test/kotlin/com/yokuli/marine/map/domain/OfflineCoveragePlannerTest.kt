package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCoveragePlannerTest {
    private val packageVersion = ChartPackageVersionId("a".repeat(64))

    @Test
    fun `route segment crossing a missing tile is incomplete even when both waypoints are present`() {
        val request = request(points = listOf(GeoPoint(0.0, -30.0), GeoPoint(0.0, 30.0)), zoom = 3)
        val plan = OfflineCoveragePlanner.plan(request)
        val middle = SlippyTileKey(3, 4, 4)
        assertTrue(middle in plan.requiredKeys)

        val result = OfflineCoverageEvaluator.evaluate(
            plan,
            mapOf(packageVersion to (plan.requiredKeys - middle)),
        )

        assertEquals(TileAvailability.MISSING, result.tileAvailability)
        assertTrue(middle in result.missingKeys)
        assertEquals(NavigationSuitability.NOT_ASSESSED, result.navigationSuitability)
    }

    @Test
    fun `only exact requested zoom keys can satisfy a check`() {
        val plan = OfflineCoveragePlanner.plan(request(zoom = 8))
        assertTrue(plan.requiredKeys.all { it.zoom == 8 })

        val lowerZoomKeys = plan.requiredKeys.map { SlippyTileKey(7, it.x / 2, it.y / 2) }.toSet()
        val result = OfflineCoverageEvaluator.evaluate(plan, mapOf(packageVersion to lowerZoomKeys))

        assertEquals(TileAvailability.MISSING, result.tileAvailability)
    }

    @Test
    fun `all opaque tiles keep content footprint unknown and navigation unassessed`() {
        val plan = OfflineCoveragePlanner.plan(request())
        val result = OfflineCoverageEvaluator.evaluate(plan, mapOf(packageVersion to plan.requiredKeys))

        assertEquals(TileAvailability.AVAILABLE, result.tileAvailability)
        assertEquals(ContentFootprint.NOT_VERIFIED, result.contentFootprint)
        assertEquals(NavigationSuitability.NOT_ASSESSED, result.navigationSuitability)
        assertTrue(result.missingKeys.isEmpty())
    }

    @Test
    fun `dateline endpoint caps turns and alternate circles stay bounded without a world scan`() {
        val request = request(
            points = listOf(GeoPoint(10.0, 179.0), GeoPoint(11.0, -179.0), GeoPoint(12.0, -178.0)),
            zoom = 6,
            alternateAreas = listOf(OfflineCoverageArea(GeoPoint(10.5, 179.8), radiusNauticalMiles = 1.0)),
        )

        val keys = OfflineCoveragePlanner.plan(request).requiredKeys

        assertTrue(keys.any { it.x == 0 })
        assertTrue(keys.any { it.x == 63 })
        assertFalse(keys.any { it.x in 20..40 })
        assertTrue(keys.size < 100)
    }

    @Test
    fun `every material input changes the fingerprint`() {
        val original = request()
        val variants = listOf(
            original.copy(routeRevision = 3),
            original.copy(halfWidthNauticalMiles = 3.0),
            original.copy(targetZoom = 9),
            original.copy(packageVersionIds = listOf(ChartPackageVersionId("b".repeat(64)))),
            original.copy(routePoints = original.routePoints + GeoPoint(-36.7, 174.9)),
        )

        variants.forEach { changed ->
            assertNotEquals(OfflineCoverageFingerprint.of(original), OfflineCoverageFingerprint.of(changed))
        }
    }

    @Test
    fun `over budget fails explicitly and never returns a truncated available plan`() {
        val failure = runCatching {
            OfflineCoveragePlanner.plan(
                request(
                    points = listOf(GeoPoint(-80.0, -170.0), GeoPoint(80.0, 170.0)),
                    zoom = 18,
                    maxRequiredKeys = 50,
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is OfflineCoverageTooLargeException)
        assertEquals(50, (failure as OfflineCoverageTooLargeException).maximumKeys)
    }

    @Test
    fun `production sources expose import instead of a fabricated automatic download`() {
        val source = ProductionChartSources.entries.single { it.id == ChartSourceId.NOAA_NCDS }

        assertEquals(ChartAcquisitionMode.IMPORT_ONLY, source.acquisition)
        assertEquals(SourceDeliveryStatus.BLOCKED_EXTERNAL, source.deliveryStatus)
        assertTrue(ChartSourceCapability.OFFLINE_DISPLAY in source.capabilities)
        assertFalse(ChartSourceCapability.AUTOMATED_DOWNLOAD in source.capabilities)
        assertTrue(source.reviewedAtUtc.isNotBlank())
    }

    private fun request(
        points: List<GeoPoint> = listOf(GeoPoint(-36.85, 174.75), GeoPoint(-36.80, 174.85)),
        zoom: Int = 8,
        alternateAreas: List<OfflineCoverageArea> = emptyList(),
        maxRequiredKeys: Int = OfflineCoveragePlanner.MAX_REQUIRED_TILE_KEYS,
    ) = OfflineCoverageRequest(
        routeId = "route-1",
        routeRevision = 2,
        routePoints = points,
        packageVersionIds = listOf(packageVersion),
        targetZoom = zoom,
        halfWidthNauticalMiles = 2.0,
        alternateAreas = alternateAreas,
        maxRequiredKeys = maxRequiredKeys,
    )
}

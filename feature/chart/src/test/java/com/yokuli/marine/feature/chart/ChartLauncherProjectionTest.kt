package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapRendererGeneration
import com.yokuli.marine.map.domain.MapRendererState
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.OfflineCoverageRequest
import com.yokuli.marine.map.domain.OfflineCoverageResult
import com.yokuli.marine.map.domain.SavedRoute
import com.yokuli.marine.map.domain.TileAvailability
import com.yokuli.marine.map.domain.ContentFootprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLauncherProjectionTest {
    private val route = SavedRoute(
        id = "route-a",
        name = "A very long route name that must be ellipsized by the renderer",
        waypoints = listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.9, 174.9)),
        revision = 4,
    )

    @Test
    fun `write failure outranks draft plan and last view`() {
        val state = MapState(
            routeDrafts = listOf(ManualRouteDraft(id = "draft-a", revision = 2, name = "draft", waypoints = route.waypoints)),
            activeRouteDraftId = "draft-a",
            savedRoutes = listOf(route),
            activeRoutePlanId = route.id,
            saveState = MapSaveState.FAILED,
            libraryLoadState = MapLibraryLoadState.READY,
            renderer = MapRendererState(generation = MapRendererGeneration(1)),
        )

        val result = ChartLauncherProjection.project(state, OfflineCoverageUiState.Idle)

        assertEquals(ChartLauncherPriority.WRITE_FAILURE, result.priority)
        assertEquals(ChartLauncherStatus.WRITE_FAILED, result.status)
        assertTrue(result.critical)
    }

    @Test
    fun `pending save outranks current draft`() {
        val state = MapState(
            routeDrafts = listOf(ManualRouteDraft(id = "draft-a", revision = 2, name = "draft", waypoints = route.waypoints)),
            activeRouteDraftId = "draft-a",
            saveState = MapSaveState.PENDING,
            libraryLoadState = MapLibraryLoadState.READY,
        )

        val result = ChartLauncherProjection.project(state, OfflineCoverageUiState.Idle)

        assertEquals(ChartLauncherPriority.UNSAVED, result.priority)
        assertEquals(ChartLauncherStatus.SAVING, result.status)
        assertTrue(result.critical)
    }

    @Test
    fun `active draft contributes its name revision and pure route geometry`() {
        val draft = ManualRouteDraft(id = "draft-a", revision = 7, name = "harbour draft", waypoints = route.waypoints)
        val result = ChartLauncherProjection.project(
            MapState(
                routeDrafts = listOf(draft),
                activeRouteDraftId = draft.id,
                libraryLoadState = MapLibraryLoadState.READY,
            ),
            OfflineCoverageUiState.Idle,
        )

        assertEquals(ChartLauncherPriority.EDITING_DRAFT, result.priority)
        assertEquals("harbour draft", result.subjectName)
        assertEquals(draft.waypoints, result.routePreview?.points)
        assertEquals(7, result.routePreview?.revision)
        assertFalse(result.critical)
    }

    @Test
    fun `current selected plan reports only measured tile availability`() {
        val coverage = OfflineCoverageUiState.Ready(
            request = OfflineCoverageRequest(
                routeId = route.id,
                routeRevision = route.revision,
                routePoints = route.waypoints,
                packageVersionIds = listOf(com.yokuli.marine.map.domain.ChartPackageVersionId("a".repeat(64))),
                targetZoom = 10,
                halfWidthNauticalMiles = 1.0,
            ),
            result = OfflineCoverageResult(
                fingerprint = com.yokuli.marine.map.domain.OfflineCoverageFingerprint("a".repeat(64)),
                tileAvailability = TileAvailability.AVAILABLE,
                contentFootprint = ContentFootprint.NOT_VERIFIED,
                requiredKeyCount = 2,
                missingKeys = emptySet(),
            ),
        )
        val result = ChartLauncherProjection.project(
            MapState(
                savedRoutes = listOf(route),
                activeRoutePlanId = route.id,
                chartPackages = listOf(chartPackage()),
                activeChartPackageId = ChartPackageId("local"),
                libraryLoadState = MapLibraryLoadState.READY,
            ),
            coverage,
        )

        assertEquals(ChartLauncherPriority.SELECTED_PLAN, result.priority)
        assertEquals(ChartLauncherStatus.TILES_AVAILABLE_CONTENT_UNVERIFIED, result.status)
        assertEquals(route.waypoints, result.routePreview?.points)
    }

    @Test
    fun `stale report never claims selected plan tiles available`() {
        val result = ChartLauncherProjection.project(
            MapState(savedRoutes = listOf(route), activeRoutePlanId = route.id, libraryLoadState = MapLibraryLoadState.READY),
            OfflineCoverageUiState.Stale(route.id, previous = null),
        )

        assertEquals(ChartLauncherStatus.COVERAGE_STALE, result.status)
    }

    @Test
    fun `no task falls back to last view only after a renderer existed`() {
        val noVisit = ChartLauncherProjection.project(MapState(), OfflineCoverageUiState.Idle)
        assertEquals(ChartLauncherPriority.ENTRY, noVisit.priority)
        assertNull(noVisit.camera)

        val visited = ChartLauncherProjection.project(
            MapState(
                camera = MapCamera(GeoPoint(-41.2865, 174.7762), 12.0),
                chartPackages = listOf(chartPackage()),
                activeChartPackageId = ChartPackageId("local"),
                renderer = MapRendererState(generation = MapRendererGeneration(2)),
            ),
            OfflineCoverageUiState.Idle,
        )
        assertEquals(ChartLauncherPriority.LAST_VIEW, visited.priority)
        assertEquals(ChartLauncherStatus.LOCAL_CHART_SELECTED, visited.status)
        assertEquals(-41.2865, visited.camera?.center?.latitude ?: 0.0, 0.0)
    }

    private fun chartPackage() = ChartPackage(
        id = ChartPackageId("local"), displayName = "Harbour", source = "user", license = "test",
        attribution = "test", sha256 = "a".repeat(64), localUri = "file:///local.mbtiles",
        coverage = GeoBounds(-42.0, 174.0, -41.0, 175.0), minZoom = 1, maxZoom = 16, version = "1",
    )
}

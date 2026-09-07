package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapRendererGeneration
import com.yokuli.marine.map.domain.MapRendererState
import com.yokuli.marine.map.domain.MapReadFailure
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.SavedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartLauncherProjectionTest {
    private val points = listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.9, 174.9))

    @Test
    fun `route drafts plans and persistence diagnostics never take over the Chart tile`() {
        val draft = ManualRouteDraft(id = "draft-a", revision = 7, name = "unfinished", waypoints = points)
        val plan = SavedRoute(id = "route-a", name = "saved", waypoints = points, revision = 4)

        val result = ChartLauncherProjection.project(
            MapState(
                routeDrafts = listOf(draft),
                activeRouteDraftId = draft.id,
                savedRoutes = listOf(plan),
                activeRoutePlanId = plan.id,
                saveState = MapSaveState.FAILED,
                persistenceFailure = MapReadFailure.IO,
            ),
        )

        assertEquals(ChartLauncherPriority.ENTRY, result.priority)
        assertEquals(ChartLauncherStatus.READY_TO_BROWSE, result.status)
        assertNull(result.camera)
    }

    @Test
    fun `a rendered map contributes only the last camera and no library workflow`() {
        val camera = MapCamera(GeoPoint(-41.2865, 174.7762), 12.0)

        val result = ChartLauncherProjection.project(
            MapState(camera = camera, renderer = MapRendererState(generation = MapRendererGeneration(2))),
        )

        assertEquals(ChartLauncherPriority.LAST_VIEW, result.priority)
        assertEquals(ChartLauncherStatus.LAST_VIEW, result.status)
        assertEquals(camera, result.camera)
    }

    @Test
    fun `Start edit mode freezes decorative camera changes`() {
        val initial = ChartLauncherSnapshot(ChartLauncherPriority.ENTRY, ChartLauncherStatus.READY_TO_BROWSE)
        val slot = ChartLauncherDisplaySlot(initial)
        val later = ChartLauncherSnapshot(
            ChartLauncherPriority.LAST_VIEW,
            ChartLauncherStatus.LAST_VIEW,
            MapCamera(GeoPoint(-36.8, 174.7), 13.0),
        )

        assertEquals(initial, slot.resolve(later, liveContentEnabled = false))
        assertEquals(later, slot.resolve(later, liveContentEnabled = true))
    }
}

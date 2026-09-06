package com.yokuli.marine.map.domain

import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDisplayReducerTest {
    private val reducer = DefaultMapReducer()

    @Test
    fun `older asynchronous display plan cannot replace the current catalog revision`() {
        val current = plan(9L, 5L, '9')
        val old = plan(8L, 4L, '8')

        val result = reducer.reduce(MapState(chartDisplayPlan = current), MapAction.ChartDisplayPlanChanged(old))

        assertEquals(current, result.state.chartDisplayPlan)
        assertTrue((result.effects.single() as MapEffect.LogIncident).incident is MapIncident.StaleChartDisplayPlan)
    }

    @Test
    fun `viewport belongs only to the live renderer generation`() {
        val generation = MapRendererGeneration(7L)
        val initial = reducer.reduce(MapState(), MapAction.RendererHostReady(generation)).state
        val viewport = ChartDisplayViewport(GeoBounds(-40.0, 170.0, -30.0, 179.0), 8)
        val stale = reducer.reduce(
            initial,
            MapAction.ChartDisplayViewportChanged(MapRendererGeneration(6L), viewport),
        ).state
        val accepted = reducer.reduce(initial, MapAction.ChartDisplayViewportChanged(generation, viewport)).state

        assertNull(stale.chartDisplayViewport)
        assertEquals(viewport, accepted.chartDisplayViewport)
    }

    private fun plan(generation: Long, revision: Long, seed: Char) = ChartDisplayPlan(
        generation = generation,
        catalogRevision = revision,
        fingerprint = seed.toString().repeat(64),
        selection = ChartDisplaySelection.None,
    )
}
